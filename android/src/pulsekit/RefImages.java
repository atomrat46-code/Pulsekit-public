package pulsekit;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.File;

/**
 * A picture or a video's first frame as a reference of a Sogni program, from any screen (the Media
 * browser, Downloads, DB/Prompts, Browse DB): a picture to upscale (smaller than the last one upscaled,
 * or not 4:7) is asked about first, Upscale first (ImageUpscaler on PyJav with it as its input) or Use
 * as it is (Process as a reference: the program, its prompt, Reference file 1 or 2).
 */
final class RefImages {
    private RefImages() {}

    /** The upscale question shown last, and the frame read last (for the tests). */
    static AlertDialog lastAsk;
    static String lastFrameName;
    static byte[] lastFrame;

    /**
     * The picture as a reference: asked Upscale first / Use as it is / Cancel when it needs upscaling.
     * `close`: a dialog closed before PyJav shows (the browser it came from); `back`: Return after ImageUpscaler.
     */
    static void offer(final MainActivity app, final String name, final byte[] png, final android.app.Dialog close, final Runnable back) {
        String q = UpscaleCheck.refQuestion(app.getFilesDir(), name, png);
        if (q == null) {
            ProcessRefDialog.open(app, name, png);
            return;
        }
        lastAsk = new AlertDialog.Builder(app).setTitle(MediaDir.PROCESS_REF).setMessage(q)
            .setPositiveButton(UpscaleCheck.UPSCALE_FIRST, (d, w) -> upscale(app, name, png, close, back))
            .setNeutralButton(UpscaleCheck.AS_IT_IS, (d, w) -> ProcessRefDialog.open(app, name, png))
            .setNegativeButton("Cancel", null).show();
    }

    /**
     * Choose file on a picture row (Sogni Params): a frame of a video (C in its player) fills the row
     * (written into PyJav's input folder); one that needs upscaling is asked Upscale first (ImageUpscaler,
     * the row left as it is), Use as it is or Cancel. `player` and `browser` close when the row is filled.
     */
    static void forRow(final MainActivity app, final String name, final byte[] png, final android.app.Dialog player, final android.app.Dialog browser,
                       final RefBrowser.Picked row) {
        final Runnable fill = () -> {
            try {
                File dir = new File(app.getCacheDir(), "pyjav-in");
                if (!dir.isDirectory()) dir.mkdirs();
                File out = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                try {
                    fos.write(png);
                } finally {
                    fos.close();
                }
                if (player != null && player.isShowing()) player.dismiss();
                if (browser != null && browser.isShowing()) browser.dismiss();
                row.picked(name, out);
            } catch (Exception ex) {
                app.setNow("Could not keep " + name + ": " + ex.getMessage());
            }
        };
        String q = UpscaleCheck.refQuestion(app.getFilesDir(), name, png);
        if (q == null) {
            fill.run();
            return;
        }
        lastAsk = new AlertDialog.Builder(app).setTitle("Reference image").setMessage(q)
            .setPositiveButton(UpscaleCheck.UPSCALE_FIRST, (d, w) -> {
                if (browser != null && browser.isShowing()) browser.dismiss();
                upscale(app, name, png, player, null);
            })
            .setNeutralButton(UpscaleCheck.AS_IT_IS, (d, w) -> fill.run())
            .setNegativeButton("Cancel", null).show();
    }

    /** ImageUpscaler on PyJav with the picture (written into PyJav's input folder) as its input, its Params open. */
    static void upscale(MainActivity app, String name, byte[] png, android.app.Dialog close, Runnable back) {
        if (close != null && close.isShowing()) close.dismiss();
        try {
            File dir = new File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            File in = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
            java.io.FileOutputStream out = new java.io.FileOutputStream(in);
            try {
                out.write(png);
            } finally {
                out.close();
            }
            app.openKitView("py");
            app.programMenus.selectProgram("Java", "ImageUpscaler.java");
            app.pyJav.pkUseInputPath(in.getAbsolutePath());
            if (back != null) app.pyJav.pkSetReturn(back);
            app.pyJav.pkOpenParams();
        } catch (Exception ex) {
            app.setNow("Could not hand " + name + " to ImageUpscaler: " + ex.getMessage());
        }
    }

    /** What the first frame is handed to: its name (<video>-first-frame.png) and its PNG. */
    interface Frame {
        void got(String name, byte[] png);
    }

    /**
     * The video's first frame (full size, a PNG) read off the main thread from its file (`path`) or
     * its document (`uri`), then `then` with it on the main thread; nothing when it cannot be read (said why).
     */
    static void firstFrame(final MainActivity app, final String video, final String path, final Uri uri, final Frame then) {
        app.setNow("Reading the first frame of " + video + "…");
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            byte[] png = null;
            MediaMetadataRetriever media = new MediaMetadataRetriever();
            try {
                if (path != null) media.setDataSource(path);
                else media.setDataSource(app, uri);
                Bitmap frame = media.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) frame = media.getFrameAtTime();
                if (frame != null) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    frame.compress(Bitmap.CompressFormat.PNG, 100, out);
                    png = out.toByteArray();
                }
            } catch (Throwable ex) {
                png = null;
            } finally {
                try {
                    media.release();
                } catch (Throwable ignored) {
                    // released
                }
            }
            final byte[] got = png;
            main.post(() -> {
                if (got == null || got.length == 0) {
                    app.setNow("Could not read the first frame of " + video);
                    return;
                }
                String name = MediaDir.firstFrameName(video, "png");
                lastFrameName = name;
                lastFrame = got;
                app.setNow("First frame of " + video + " · " + name);
                then.got(name, got);
            });
        }, "pulsekit-first-frame").start();
    }

    /** A video kept as bytes (DB/Prompts, Browse DB): written to the cache folder to be read from. */
    static File videoFile(MainActivity app, String name, byte[] bytes) throws java.io.IOException {
        File dir = new File(app.getCacheDir(), "first-frame-in");
        if (!dir.isDirectory()) dir.mkdirs();
        File f = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
        return f;
    }

    /** First frame as a reference image: the video's first frame, then offer(). */
    static void firstFrameAsRef(MainActivity app, String video, String path, Uri uri, android.app.Dialog close, Runnable back) {
        firstFrame(app, video, path, uri, (name, png) -> offer(app, name, png, close, back));
    }
}
