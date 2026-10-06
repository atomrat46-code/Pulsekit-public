package pulsekit;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import javax.swing.JPanel;

/**
 * Video played inside Pulsekit with the VLC installed on the computer (libvlc 3), when there is
 * one. VLC decodes each frame into memory (RV32) and Pulsekit draws it in a panel, the same on
 * Windows, Mac and Linux; VLC plays the sound itself. Without VLC, available() is false and
 * Preview keeps the browser player.
 *
 * Only libvlc's C functions are called, through JNA; VLC itself is not bundled.
 */
final class VlcPlayer {
    /** The libvlc functions Pulsekit uses. */
    interface LibVlc extends Library {
        Pointer libvlc_new(int argc, String[] argv);
        void libvlc_release(Pointer instance);
        String libvlc_get_version();
        Pointer libvlc_media_new_path(Pointer instance, String path);
        void libvlc_media_release(Pointer media);
        void libvlc_media_add_option(Pointer media, String option);
        Pointer libvlc_media_player_new_from_media(Pointer media);
        void libvlc_media_player_release(Pointer player);
        int libvlc_media_player_play(Pointer player);
        void libvlc_media_player_set_pause(Pointer player, int pause);
        void libvlc_media_player_stop(Pointer player);
        int libvlc_media_player_is_playing(Pointer player);
        long libvlc_media_player_get_length(Pointer player);
        long libvlc_media_player_get_time(Pointer player);
        float libvlc_media_player_get_position(Pointer player);
        void libvlc_media_player_set_position(Pointer player, float position);
        int libvlc_media_player_get_state(Pointer player);
        int libvlc_audio_set_volume(Pointer player, int volume);
        int libvlc_video_get_size(Pointer player, int num, com.sun.jna.ptr.IntByReference width, com.sun.jna.ptr.IntByReference height);
        void libvlc_audio_set_mute(Pointer player, int mute);
        int libvlc_media_player_set_rate(Pointer player, float rate);
        void libvlc_video_set_callbacks(Pointer player, Lock lock, Unlock unlock, Display display, Pointer opaque);
        void libvlc_video_set_format_callbacks(Pointer player, Setup setup, Cleanup cleanup);
    }

    interface Lock extends Callback {
        Pointer invoke(Pointer opaque, Pointer planes);
    }

    interface Unlock extends Callback {
        void invoke(Pointer opaque, Pointer picture, Pointer planes);
    }

    interface Display extends Callback {
        void invoke(Pointer opaque, Pointer picture);
    }

    interface Setup extends Callback {
        int invoke(PointerByReference opaque, Pointer chroma, Pointer width, Pointer height, Pointer pitches, Pointer lines);
    }

    interface Cleanup extends Callback {
        void invoke(Pointer opaque);
    }

    /** setenv, for VLC_PLUGIN_PATH on a Mac (VLC.app keeps its plugins beside the library). */
    interface LibC extends Library {
        int setenv(String name, String value, int overwrite);
    }

    /** Frames are drawn at most this wide or tall (smaller is decoded as is). */
    private static final int MAX_SIDE = 1280;
    /** libvlc_state_t: 6 is Ended, 7 is Error. */
    static final int ENDED = 6;
    static final int ERROR = 7;

    private static LibVlc lib;
    private static Pointer instance;
    private static String why;
    private static boolean tried;

    /** Extra VLC options (the tests run without a sound card: -Dpulsekit.vlc.args=--aout=dummy). */
    static String extraArgs() {
        return System.getProperty("pulsekit.vlc.args", "");
    }

    /** True when VLC was found and started (tried once), unless it is turned off. */
    static synchronized boolean available() {
        // -Dpulsekit.novlc=true keeps the browser player even with VLC installed.
        if (Boolean.getBoolean("pulsekit.novlc")) {
            why = "VLC is turned off (pulsekit.novlc)";
            return false;
        }
        if (!tried) {
            tried = true;
            try {
                load();
            } catch (Throwable t) {
                lib = null;
                instance = null;
                why = t.getMessage() == null ? t.toString() : t.getMessage();
            }
        }
        return instance != null;
    }

    /** Why VLC is not used (not installed, or it would not start), for the status line. */
    static String why() {
        return why == null ? "" : why;
    }

    static String version() {
        return available() ? lib.libvlc_get_version() : "";
    }

    /** The folders VLC installs its library in, by system. */
    static String[] folders() {
        if (Platform.isWindows()) {
            String pf = System.getenv("ProgramFiles");
            String pf86 = System.getenv("ProgramFiles(x86)");
            return new String[] {
                (pf == null ? "C:\\Program Files" : pf) + "\\VideoLAN\\VLC",
                (pf86 == null ? "C:\\Program Files (x86)" : pf86) + "\\VideoLAN\\VLC",
            };
        }
        if (Platform.isMac()) {
            String home = System.getProperty("user.home", "");
            return new String[] {"/Applications/VLC.app/Contents/MacOS/lib", home + "/Applications/VLC.app/Contents/MacOS/lib"};
        }
        return new String[] {"/usr/lib/x86_64-linux-gnu", "/usr/lib/aarch64-linux-gnu", "/usr/lib64", "/usr/lib", "/usr/local/lib",
            "/snap/vlc/current/usr/lib", "/app/lib"};
    }

    private static void load() {
        for (String dir : folders()) {
            if (!new File(dir).isDirectory()) continue;
            NativeLibrary.addSearchPath("vlc", dir);
            NativeLibrary.addSearchPath("libvlc", dir);
            NativeLibrary.addSearchPath("vlccore", dir);
            NativeLibrary.addSearchPath("libvlccore", dir);
            if (Platform.isMac()) {
                File plugins = new File(new File(dir).getParentFile(), "plugins");
                if (plugins.isDirectory()) {
                    try {
                        Native.load("c", LibC.class).setenv("VLC_PLUGIN_PATH", plugins.getAbsolutePath(), 1);
                    } catch (Throwable ignored) {
                        // VLC looks for its plugins itself
                    }
                }
            }
        }
        String name = Platform.isWindows() ? "libvlc" : "vlc";
        // libvlccore first: on Windows libvlc.dll needs it and is not looked for beside libvlc.dll.
        try {
            NativeLibrary.getInstance(Platform.isWindows() ? "libvlccore" : "vlccore");
        } catch (Throwable ignored) {
            // found with libvlc below, or not at all
        }
        try {
            lib = Native.load(name, LibVlc.class);
        } catch (UnsatisfiedLinkError ex) {
            throw new IllegalStateException("VLC is not installed (videolan.org); the browser plays videos instead");
        }
        java.util.List<String> args = new java.util.ArrayList<String>();
        args.add("--no-video-title-show");
        args.add("--no-snapshot-preview");
        args.add("--quiet");
        args.add("--no-xlib");
        for (String a : extraArgs().trim().split("\\s+")) if (!a.isEmpty()) args.add(a);
        instance = lib.libvlc_new(args.size(), args.toArray(new String[0]));
        if (instance == null) throw new IllegalStateException("VLC is installed but would not start; the browser plays videos instead");
    }

    // ------------------------------------------------------------ one video

    /** Where the frames are drawn: the latest one, scaled to fit, on black. */
    static final class Screen extends JPanel {
        volatile BufferedImage frame;
        /** Frames drawn so far, for the tests. */
        volatile int frames;
        /** The picture's visible size, smaller than the frame when VLC pads it (to 16 rows); 0 until known. */
        volatile int visibleW;
        volatile int visibleH;

        /** Zoom: 1 fits the window, up to 4 (MediaDir's steps); the shift of a zoomed picture, in pixels. */
        volatile double zoom = 1;
        volatile int panX;
        volatile int panY;
        /** Called when the wheel changes the zoom (the window's zoom label). */
        Runnable zoomed;

        Screen() {
            this.setBackground(Color.BLACK);
            this.setPreferredSize(new Dimension(720, 405));
            // The wheel zooms; a drag moves a zoomed picture.
            this.addMouseWheelListener(e -> {
                this.zoomTo(MediaDir.zoom(this.zoom, e.getWheelRotation() < 0));
                if (this.zoomed != null) this.zoomed.run();
            });
            java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
                int x;
                int y;

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    this.x = e.getX() - Screen.this.panX;
                    this.y = e.getY() - Screen.this.panY;
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (Screen.this.zoom <= 1) return;
                    Screen.this.panX = e.getX() - this.x;
                    Screen.this.panY = e.getY() - this.y;
                    Screen.this.repaint();
                }
            };
            this.addMouseListener(drag);
            this.addMouseMotionListener(drag);
        }

        void zoomTo(double z) {
            this.zoom = MediaDir.clampZoom(z);
            if (this.zoom == 1) {
                this.panX = 0;
                this.panY = 0;
            }
            this.repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage img = this.frame;
            if (img == null) return;
            int vw = this.visibleW;
            int vh = this.visibleH;
            if (vw > 0 && vh > 0 && (vw < img.getWidth() || vh < img.getHeight())) img = img.getSubimage(0, 0, Math.min(vw, img.getWidth()), Math.min(vh, img.getHeight()));
            double s = Math.min(this.getWidth() / (double) img.getWidth(), this.getHeight() / (double) img.getHeight()) * this.zoom;
            int w = Math.max(1, (int) (img.getWidth() * s));
            int h = Math.max(1, (int) (img.getHeight() * s));
            // A zoomed picture moves no further than its edges.
            int maxX = Math.max(0, (w - this.getWidth()) / 2);
            int maxY = Math.max(0, (h - this.getHeight()) / 2);
            this.panX = Math.max(-maxX, Math.min(maxX, this.panX));
            this.panY = Math.max(-maxY, Math.min(maxY, this.panY));
            ((java.awt.Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, (this.getWidth() - w) / 2 + this.panX, (this.getHeight() - h) / 2 + this.panY, w, h, null);
        }
    }

    final Screen screen = new Screen();
    private Pointer player;
    private Memory buffer;
    private int width;
    private int height;
    private BufferedImage back;
    // Held here so the garbage collector keeps them while VLC calls them.
    private final Setup setup;
    private final Cleanup cleanup;
    private final Lock lock;
    private final Unlock unlock;
    private final Display display;

    /** A player for the file; call available() first. */
    VlcPlayer(File video) {
        this(video, false);
    }

    /** With `silent`, VLC leaves the sound out (a preview made in the background). */
    VlcPlayer(File video, boolean silent) {
        if (!available()) throw new IllegalStateException(why());
        Pointer media = lib.libvlc_media_new_path(instance, video.getAbsolutePath());
        if (media == null) throw new IllegalStateException("VLC could not open " + video.getName());
        if (silent) lib.libvlc_media_add_option(media, ":no-audio");
        this.player = lib.libvlc_media_player_new_from_media(media);
        lib.libvlc_media_release(media);
        if (this.player == null) throw new IllegalStateException("VLC could not play " + video.getName());
        this.setup = (opaque, chroma, w, h, pitches, lines) -> {
            int vw = Math.max(1, w.getInt(0));
            int vh = Math.max(1, h.getInt(0));
            double s = Math.min(1.0, MAX_SIDE / (double) Math.max(vw, vh));
            vw = Math.max(2, (int) (vw * s) & ~1);
            vh = Math.max(2, (int) (vh * s) & ~1);
            // RV32: 4 bytes a pixel, B G R X in memory, read as one int: 0xXXRRGGBB.
            chroma.write(0, new byte[] {'R', 'V', '3', '2'}, 0, 4);
            w.setInt(0, vw);
            h.setInt(0, vh);
            pitches.setInt(0, vw * 4);
            lines.setInt(0, vh);
            synchronized (this) {
                this.width = vw;
                this.height = vh;
                this.buffer = new Memory((long) vw * vh * 4);
                this.back = new BufferedImage(vw, vh, BufferedImage.TYPE_INT_RGB);
            }
            return 1;
        };
        this.cleanup = opaque -> { };
        this.lock = (opaque, planes) -> {
            planes.setPointer(0, this.buffer);
            return null;
        };
        this.unlock = (opaque, picture, planes) -> { };
        this.display = (opaque, picture) -> {
            BufferedImage img;
            synchronized (this) {
                img = this.back;
                if (img == null || this.buffer == null) return;
                int[] pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                this.buffer.read(0, pixels, 0, this.width * this.height);
                // The next frame goes into a fresh image, so the one on screen is never half written.
                this.back = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
            }
            this.screen.frame = img;
            this.screen.frames++;
            this.screen.repaint();
        };
        lib.libvlc_video_set_format_callbacks(this.player, this.setup, this.cleanup);
        lib.libvlc_video_set_callbacks(this.player, this.lock, this.unlock, this.display, null);
    }

    /**
     * Reads the picture's visible size from VLC (called from the Swing thread, not from VLC's
     * own callbacks), scaled as the frames are, so the padding VLC adds below is not drawn.
     */
    void refreshSize() {
        if (this.player == null) return;
        com.sun.jna.ptr.IntByReference w = new com.sun.jna.ptr.IntByReference();
        com.sun.jna.ptr.IntByReference h = new com.sun.jna.ptr.IntByReference();
        if (lib.libvlc_video_get_size(this.player, 0, w, h) != 0 || w.getValue() <= 0 || h.getValue() <= 0) return;
        double s = Math.min(1.0, MAX_SIDE / (double) Math.max(w.getValue(), h.getValue()));
        this.screen.visibleW = Math.max(1, (int) Math.round(w.getValue() * s));
        this.screen.visibleH = Math.max(1, (int) Math.round(h.getValue() * s));
    }

    void play() {
        if (this.player == null) return;
        int state = lib.libvlc_media_player_get_state(this.player);
        // After the end (or Stop) Play starts again from the beginning.
        if (state == ENDED || state == ERROR) lib.libvlc_media_player_stop(this.player);
        lib.libvlc_media_player_play(this.player);
    }

    void pause() {
        if (this.player != null) lib.libvlc_media_player_set_pause(this.player, 1);
    }

    void stop() {
        if (this.player != null) lib.libvlc_media_player_stop(this.player);
    }

    boolean playing() {
        return this.player != null && lib.libvlc_media_player_is_playing(this.player) != 0;
    }

    int state() {
        return this.player == null ? 0 : lib.libvlc_media_player_get_state(this.player);
    }

    /** 0..1 through the video. */
    float position() {
        return this.player == null ? 0f : Math.max(0f, lib.libvlc_media_player_get_position(this.player));
    }

    void seek(float at) {
        if (this.player != null) lib.libvlc_media_player_set_position(this.player, Math.max(0f, Math.min(1f, at)));
    }

    long timeMs() {
        return this.player == null ? 0 : Math.max(0, lib.libvlc_media_player_get_time(this.player));
    }

    long lengthMs() {
        return this.player == null ? 0 : Math.max(0, lib.libvlc_media_player_get_length(this.player));
    }

    /** 0..100. */
    void volume(int percent) {
        if (this.player != null) lib.libvlc_audio_set_volume(this.player, Math.max(0, Math.min(100, percent)));
    }

    /** Playback speed: 1 as recorded, 0.5 half, 2 double. */
    void rate(double speed) {
        if (this.player != null) lib.libvlc_media_player_set_rate(this.player, (float) speed);
    }

    void mute(boolean on) {
        if (this.player != null) lib.libvlc_audio_set_mute(this.player, on ? 1 : 0);
    }

    /** Stops and lets VLC go of the file (the dialog closed). */
    void release() {
        Pointer p = this.player;
        this.player = null;
        if (p == null) return;
        lib.libvlc_media_player_stop(p);
        lib.libvlc_media_player_release(p);
    }

    /**
     * The video's first frame as a JPEG at most `maxSide` pixels across, read by VLC without
     * sound and without showing anything; null when VLC cannot play it (within 8 seconds).
     */
    static byte[] firstFrame(File video, int maxSide) {
        VlcPlayer p = null;
        try {
            p = new VlcPlayer(video, true);
            p.play();
            for (int i = 0; i < 160 && p.screen.frames == 0; i++) {
                int state = p.state();
                if (state == ERROR || state == ENDED && p.screen.frames == 0 && i > 10) return null;
                Thread.sleep(50);
            }
            BufferedImage frame = p.screen.frame;
            if (frame == null) return null;
            p.refreshSize();
            int vw = p.screen.visibleW > 0 ? Math.min(p.screen.visibleW, frame.getWidth()) : frame.getWidth();
            int vh = p.screen.visibleH > 0 ? Math.min(p.screen.visibleH, frame.getHeight()) : frame.getHeight();
            double s = Math.min(1.0, maxSide / (double) Math.max(vw, vh));
            int w = Math.max(1, (int) Math.round(vw * s));
            int h = Math.max(1, (int) Math.round(vh * s));
            BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = small.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(frame, 0, 0, w, h, 0, 0, vw, vh, null);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(small, "jpg", out);
            return out.size() > 0 ? out.toByteArray() : null;
        } catch (Exception ex) {
            return null;
        } finally {
            if (p != null) p.release();
        }
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60);
    }
}
