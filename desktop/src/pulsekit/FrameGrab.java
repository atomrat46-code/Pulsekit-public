package pulsekit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Extract frames for a video on the desktop, as on the phone: Java has no video decoder, so the
 * browser reads them. A small server on this computer only (127.0.0.1, a random token in every
 * address) serves the video and a page that plays it. The page draws the first frame and the
 * last (a second before the end) as JPEGs and sends them back; they reach `done`, which keeps
 * them as reference files. The server stops when both arrived, on Cancel, or after 5 minutes.
 */
final class FrameGrab {
    interface Done {
        /** On the Swing thread: the first and last frame (either may be null), or why there are none. */
        void frames(byte[] first, byte[] last, String error);
    }

    private static final int MAX_FRAME = 16 * 1024 * 1024;
    private static final long LIFETIME_MS = 5 * 60 * 1000L;

    /** Video previews (the Ref files gallery): a picture of each video's first frame, by its place in the list. */
    interface Thumbs {
        /** On the Swing thread: the previews read (a video the browser could not play has none). */
        void thumbs(java.util.Map<Integer, byte[]> made);
    }

    private final File video;
    private final String name;
    private final Done done;
    /** For previews: the videos, and what to call with them; null when reading one video's frames. */
    private final java.util.List<File> videos;
    private final Thumbs thumbsDone;
    private final java.util.Map<Integer, byte[]> thumbs = new java.util.HashMap<Integer, byte[]>();
    private final String token;
    private HttpServer server;
    private byte[] first;
    private byte[] last;
    private boolean finished;

    FrameGrab(File video, String name, Done done) {
        this(video, name, done, null, null);
    }

    /** Previews for `videos`: the page reads each one's first frame, smaller, and sends it back. */
    FrameGrab(java.util.List<File> videos, Thumbs thumbs) {
        this(videos.get(0), "Video previews", null, videos, thumbs);
    }

    private FrameGrab(File video, String name, Done done, java.util.List<File> videos, Thumbs thumbsDone) {
        this.video = video;
        this.name = name == null || name.isEmpty() ? video.getName() : name;
        this.done = done;
        this.videos = videos;
        this.thumbsDone = thumbsDone;
        byte[] raw = new byte[16];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
        this.token = sb.toString();
    }

    /** Starts the server; the page's address, for the browser. */
    synchronized URI start() throws Exception {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/" + this.token + "/", this::handle);
        this.server.start();
        Thread timer = new Thread(() -> {
            try {
                Thread.sleep(LIFETIME_MS);
            } catch (InterruptedException ignored) {
                return;
            }
            this.finish("Frames were not read in time: try again with the page open");
        }, "pulsekit-frames-timeout");
        timer.setDaemon(true);
        timer.start();
        return URI.create("http://127.0.0.1:" + this.server.getAddress().getPort() + "/" + this.token + "/page");
    }

    /** Stops without frames (Cancel). */
    void cancel() {
        this.finish(null);
    }

    private void handle(HttpExchange ex) throws java.io.IOException {
        try {
            String path = ex.getRequestURI().getPath().substring(this.token.length() + 2);
            String method = ex.getRequestMethod();
            if ("GET".equals(method) && "page".equals(path)) {
                this.send(ex, 200, "text/html; charset=utf-8", this.page().getBytes(StandardCharsets.UTF_8));
            } else if ("GET".equals(method) && "video".equals(path)) {
                this.sendVideo(ex, this.video);
            } else if ("GET".equals(method) && path.startsWith("video/") && this.videos != null) {
                int at = index(path.substring(6));
                if (at < 0) this.send(ex, 404, "text/plain", "not here".getBytes(StandardCharsets.UTF_8));
                else this.sendVideo(ex, this.videos.get(at));
            } else if ("POST".equals(method) && path.startsWith("thumb/") && this.videos != null) {
                int at = index(path.substring(6));
                byte[] body = read(ex.getRequestBody());
                if (at < 0 || body == null || body.length < 3 || (body[0] & 0xff) != 0xff || (body[1] & 0xff) != 0xd8) {
                    this.send(ex, 400, "text/plain", "not a JPEG".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                synchronized (this) {
                    this.thumbs.put(at, body);
                }
                this.send(ex, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
            } else if ("POST".equals(method) && "done".equals(path) && this.videos != null) {
                read(ex.getRequestBody());
                this.send(ex, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                this.finish(null);
            } else if ("POST".equals(method) && this.videos == null && ("first".equals(path) || "last".equals(path))) {
                byte[] body = read(ex.getRequestBody());
                if (body == null || body.length < 3 || (body[0] & 0xff) != 0xff || (body[1] & 0xff) != 0xd8) {
                    this.send(ex, 400, "text/plain", "not a JPEG".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                synchronized (this) {
                    if ("first".equals(path)) this.first = body;
                    else this.last = body;
                }
                this.send(ex, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                boolean both;
                synchronized (this) {
                    both = this.first != null && this.last != null;
                }
                if (both) this.finish(null);
            } else if ("POST".equals(method) && "error".equals(path) && this.videos == null) {
                byte[] body = read(ex.getRequestBody());
                this.send(ex, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                String why = body == null ? "" : new String(body, StandardCharsets.UTF_8).trim();
                this.finish(why.isEmpty() ? "The browser could not read this video" : why);
            } else {
                this.send(ex, 404, "text/plain", "not here".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            ex.close();
        }
    }

    /** The video's place in the list, or -1. */
    private int index(String raw) {
        try {
            int at = Integer.parseInt(raw);
            return at >= 0 && at < this.videos.size() ? at : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Hands the frames (or previews) on once, on the Swing thread, and stops the server. */
    private void finish(String error) {
        final byte[] a;
        final byte[] b;
        final java.util.Map<Integer, byte[]> made;
        synchronized (this) {
            if (this.finished) return;
            this.finished = true;
            a = this.first;
            b = this.last;
            made = new java.util.HashMap<Integer, byte[]>(this.thumbs);
        }
        final HttpServer s = this.server;
        // Stopped a moment later, so the page's last reply goes out first.
        Thread stopper = new Thread(() -> {
            if (s != null) s.stop(1);
        }, "pulsekit-frames-stop");
        stopper.setDaemon(true);
        stopper.start();
        if (this.videos != null) {
            // Previews: what arrived is kept, even when the page stopped early.
            if (!made.isEmpty()) javax.swing.SwingUtilities.invokeLater(() -> this.thumbsDone.thumbs(made));
            return;
        }
        if (a == null && b == null && error == null) return;
        javax.swing.SwingUtilities.invokeLater(() -> this.done.frames(a, b, a == null && b == null ? error : null));
    }

    private static byte[] read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
            if (out.size() > MAX_FRAME) return null;
        }
        return out.toByteArray();
    }

    private void send(HttpExchange ex, int code, String type, byte[] body) throws java.io.IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            OutputStream out = ex.getResponseBody();
            out.write(body);
        }
    }

    /** The video, with byte ranges: the browser seeks to the last frame with them. */
    private void sendVideo(HttpExchange ex, File video) throws java.io.IOException {
        long size = video.length();
        long from = 0;
        long to = size - 1;
        String range = ex.getRequestHeaders().getFirst("Range");
        boolean partial = false;
        if (range != null && range.startsWith("bytes=")) {
            String[] ends = range.substring(6).split(",")[0].trim().split("-", 2);
            try {
                if (ends[0].isEmpty()) {
                    from = Math.max(0, size - Long.parseLong(ends[1]));
                } else {
                    from = Long.parseLong(ends[0]);
                    if (ends.length > 1 && !ends[1].isEmpty()) to = Math.min(size - 1, Long.parseLong(ends[1]));
                }
                partial = true;
            } catch (NumberFormatException ignored) {
                from = 0;
                to = size - 1;
            }
        }
        if (from > to || from >= size) {
            ex.getResponseHeaders().set("Content-Range", "bytes */" + size);
            ex.sendResponseHeaders(416, -1);
            return;
        }
        String low = video.getName().toLowerCase();
        String type = low.endsWith(".webm") ? "video/webm" : low.endsWith(".mov") ? "video/quicktime" : low.endsWith(".mkv") ? "video/x-matroska"
            : low.endsWith(".3gp") ? "video/3gpp" : "video/mp4";
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Accept-Ranges", "bytes");
        long length = to - from + 1;
        if (partial) ex.getResponseHeaders().set("Content-Range", "bytes " + from + "-" + to + "/" + size);
        ex.sendResponseHeaders(partial ? 206 : 200, length);
        try (RandomAccessFile in = new RandomAccessFile(video, "r")) {
            in.seek(from);
            OutputStream out = ex.getResponseBody();
            byte[] buf = new byte[65536];
            long left = length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n <= 0) break;
                out.write(buf, 0, n);
                left -= n;
            }
        } catch (java.io.IOException closed) {
            // the browser stopped reading (it seeks elsewhere)
        }
    }

    /** The page: the video, then the first and last frame drawn and sent back (or, for previews, each video's first frame). */
    String page() {
        if (this.videos != null) return this.thumbPage();
        String shown = this.name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<!doctype html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "<title>Frames · " + shown + "</title>\n<style>\n"
            + "body{margin:0;background:#111;color:#eee;font:15px sans-serif;display:flex;flex-direction:column;align-items:center;padding:16px;gap:12px}\n"
            + "video{max-width:100%;max-height:60vh;background:#000;border-radius:8px}\n#note{color:#c7f04b}\n</style></head>\n<body>\n"
            + "<div>" + shown + "</div>\n"
            + "<video id=\"v\" src=\"video\" preload=\"auto\" muted playsinline></video>\n"
            + "<div id=\"note\">Reading the first and last frame…</div>\n"
            + "<script>\n"
            + "var v=document.getElementById('v'),note=document.getElementById('note');\n"
            + "function say(t){note.textContent=t;}\n"
            + "function fail(t){say(t);fetch('error',{method:'POST',body:t});}\n"
            + "function seek(t){return new Promise(function(ok){v.addEventListener('seeked',function h(){v.removeEventListener('seeked',h);ok();});v.currentTime=t;});}\n"
            // A file without its length in its header (a recorded WebM) has none until the browser seeks past its end.
            + "function length(){return new Promise(function(ok){if(isFinite(v.duration)&&v.duration>0){ok(v.duration);return;}\n"
            + "  var done=false,finish=function(){if(done)return;done=true;ok(isFinite(v.duration)?v.duration:0);};\n"
            + "  v.addEventListener('durationchange',function h(){if(isFinite(v.duration)){v.removeEventListener('durationchange',h);finish();}});\n"
            + "  setTimeout(finish,4000);v.currentTime=1e101;});}\n"
            + "function grab(){return new Promise(function(ok,no){var c=document.createElement('canvas');c.width=v.videoWidth;c.height=v.videoHeight;\n"
            + "  c.getContext('2d').drawImage(v,0,0,c.width,c.height);c.toBlob(function(b){b?ok(b):no(new Error('no frame'));},'image/jpeg',0.9);});}\n"
            + "function post(which,b){return fetch(which,{method:'POST',body:b}).then(function(r){if(!r.ok)throw new Error('Pulsekit did not take the '+which+' frame');});}\n"
            + "v.onerror=function(){fail('The browser could not play this video, so it has no frames to read.');};\n"
            + "v.addEventListener('loadeddata',function once(){v.removeEventListener('loadeddata',once);\n"
            + "  if(!v.videoWidth){fail('This video has no picture.');return;}\n"
            + "  seek(0).then(grab).then(function(b){return post('first',b);})\n"
            + "  .then(length).then(function(d){return seek(d>0?Math.max(0,d-1):0);}).then(grab).then(function(b){return post('last',b);})\n"
            + "  .then(function(){say('Saved the first and last frame in Pulsekit as reference files. You can close this tab.');})\n"
            + "  .catch(function(e){fail(e&&e.message?e.message:'Could not read frames.');});\n"
            + "});\n"
            + "</script>\n</body></html>\n";
    }

    /** Previews: each video in turn, its first frame drawn at most 320 pixels across and sent back; then done. */
    String thumbPage() {
        return "<!doctype html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "<title>Pulsekit \u00b7 Video previews</title>\n<style>\n"
            + "body{margin:0;background:#111;color:#eee;font:15px sans-serif;display:flex;flex-direction:column;align-items:center;padding:16px;gap:12px}\n"
            + "video{max-width:320px;max-height:240px;background:#000;border-radius:8px}\n#note{color:#c7f04b}\n</style></head>\n<body>\n"
            + "<div>Pulsekit is making previews of the videos in its prompt library.</div>\n"
            + "<video id=\"v\" preload=\"auto\" muted playsinline></video>\n"
            + "<div id=\"note\">Reading\u2026</div>\n"
            + "<script>\n"
            + "var v=document.getElementById('v'),note=document.getElementById('note'),count=" + this.videos.size() + ",made=0;\n"
            + "function load(i){return new Promise(function(ok,no){v.onloadeddata=function(){v.onloadeddata=null;v.onerror=null;ok();};\n"
            + "  v.onerror=function(){v.onloadeddata=null;v.onerror=null;no(new Error('cannot play'));};v.src='video/'+i;v.load();});}\n"
            + "function seek(t){return new Promise(function(ok){v.addEventListener('seeked',function h(){v.removeEventListener('seeked',h);ok();});v.currentTime=t;});}\n"
            + "function grab(){return new Promise(function(ok,no){if(!v.videoWidth){no(new Error('no picture'));return;}\n"
            + "  var s=Math.min(1,320/Math.max(v.videoWidth,v.videoHeight)),c=document.createElement('canvas');\n"
            + "  c.width=Math.max(1,Math.round(v.videoWidth*s));c.height=Math.max(1,Math.round(v.videoHeight*s));\n"
            + "  c.getContext('2d').drawImage(v,0,0,c.width,c.height);c.toBlob(function(b){b?ok(b):no(new Error('no frame'));},'image/jpeg',0.85);});}\n"
            + "function one(i){note.textContent='Reading video '+(i+1)+' of '+count+'\u2026';\n"
            + "  return load(i).then(function(){return seek(0);}).then(grab).then(function(b){return fetch('thumb/'+i,{method:'POST',body:b});})\n"
            + "  .then(function(r){if(r.ok)made++;}).catch(function(){});}\n"
            + "var chain=Promise.resolve();for(var i=0;i<count;i++)(function(i){chain=chain.then(function(){return one(i);});})(i);\n"
            + "chain.then(function(){return fetch('done',{method:'POST',body:''});}).then(function(){\n"
            + "  note.textContent='Made '+made+' of '+count+' previews. They show in Pulsekit now. You can close this tab.';v.removeAttribute('src');v.load();});\n"
            + "</script>\n</body></html>\n";
    }
}
