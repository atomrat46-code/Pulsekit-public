package pulsekit;

import java.awt.GridLayout;
import java.io.File;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * ReRender (a video thumbnail's item: the Media browser, Downloads, DB/Prompts): the video made again
 * at another size and shape with VLC, as <video>-WxH.mp4 beside it (else in the program files folder).
 * Its shape is reached by cropping (the part kept: centre, top or left, bottom or right) or by black
 * bars, then it is scaled, in one pass; the sound is the original's, copied as it is (VLC renders the
 * picture alone: it drops sound it makes faster than it plays). Off the Swing thread; the status line says how far.
 */
final class VideoRender {
    private VideoRender() {}

    /** The tests' answer to the dialog: {size ("1080×1920" or a SIZES label), CROP or PAD, an AREAS name}; null asks. */
    static String[] answer;
    /** The file made last, and what was said (for the tests). */
    static volatile File last;
    static volatile String lastSaid;

    /** The dialog: size, Crop to fill or Fit with black bars, the part kept; then the render. */
    static void ask(final Pulsekit app, java.awt.Component at, final File video, final String name, final File fallback, final boolean beside) {
        if (!VlcPlayer.available()) {
            app.setNow("ReRender needs VLC on this computer (videolan.org): " + VlcPlayer.why());
            return;
        }
        String[] got = answer;
        if (got == null) {
            String[] labels = new String[VideoFit.SIZES.length + 1];
            System.arraycopy(VideoFit.SIZES, 0, labels, 0, VideoFit.SIZES.length);
            labels[labels.length - 1] = VideoFit.CUSTOM;
            JComboBox<String> size = new JComboBox<String>(labels);
            size.setName("rerender-size");
            JTextField custom = new JTextField("1080x1920", 9);
            custom.setName("rerender-custom");
            custom.setEnabled(false);
            size.addActionListener(e -> custom.setEnabled(VideoFit.CUSTOM.equals(size.getSelectedItem())));
            JRadioButton crop = new JRadioButton(VideoFit.CROP, true);
            JRadioButton pad = new JRadioButton(VideoFit.PAD);
            javax.swing.ButtonGroup g = new javax.swing.ButtonGroup();
            g.add(crop);
            g.add(pad);
            JComboBox<String> area = new JComboBox<String>(VideoFit.AREAS);
            area.setName("rerender-area");
            crop.addActionListener(e -> area.setEnabled(true));
            pad.addActionListener(e -> area.setEnabled(false));
            JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
            p.add(new JLabel("Size"));
            p.add(size);
            p.add(new JLabel("Custom size (W x H)"));
            p.add(custom);
            p.add(crop);
            p.add(pad);
            p.add(new JLabel("Part kept (crop)"));
            p.add(area);
            if (JOptionPane.showConfirmDialog(at != null ? at : app, p, "ReRender · " + name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            got = new String[] {VideoFit.CUSTOM.equals(size.getSelectedItem()) ? custom.getText() : String.valueOf(size.getSelectedItem()),
                crop.isSelected() ? VideoFit.CROP : VideoFit.PAD, String.valueOf(area.getSelectedItem())};
        }
        int[] wh = VideoFit.parseSize(got[0]);
        if (wh == null) {
            app.setNow("ReRender: " + got[0] + " is no size (W x H, each 16 to 4096)");
            return;
        }
        int a = java.util.Arrays.asList(VideoFit.AREAS).indexOf(got[2]);
        File dir = video.getAbsoluteFile().getParentFile();
        if (!beside || dir == null || !dir.canWrite()) dir = fallback;
        run(app, video, name, dir, wh[0], wh[1], VideoFit.PAD.equals(got[1]), VideoFit.ANCHORS[Math.max(0, a)]);
    }

    /** The render, off the Swing thread: the shape (crop or bars) at the video's own size, then scaled, then its sound. */
    static void run(final Pulsekit app, final File video, final String name, final File dir, final int w, final int h, final boolean pad, final double anchor) {
        app.setNow("ReRendering " + name + " at " + w + "×" + h + "…");
        new Thread(() -> {
            String said;
            File made = null;
            File work = null;
            try {
                VlcPlayer.Probe probe = VlcPlayer.probe(video, 64);
                if (probe == null || probe.width <= 0 || probe.height <= 0) throw new java.io.IOException("VLC could not read its size");
                int[] p = VideoFit.plan(probe.width, probe.height, w, h, pad, anchor);
                work = java.nio.file.Files.createTempDirectory("pulsekit-rerender").toFile();
                // One pass: the shape (crop or bars, in the video's own pixels), then scaled (canvas), the picture alone;
                // no B-frames or look-ahead, so VLC's encoder holds back no frames at the end.
                File two = new File(work, "sized.mp4");
                String sout = "#transcode{vcodec=h264,venc=x264{crf=18,preset=medium,bframes=0,lookahead=0,threads=1},vfilter='croppadd{cropleft=" + p[0] + ",croptop=" + p[1]
                    + ",cropright=" + p[2] + ",cropbottom=" + p[3] + ",paddleft=" + p[4] + ",paddtop=" + p[5] + ",paddright=" + p[6] + ",paddbottom=" + p[7] + "}:canvas{width=" + w
                    + ",height=" + h + "}'}:std{access=file,mux=mp4,dst=" + two.getAbsolutePath() + "}";
                String why = VlcPlayer.transcode(video, sout, f -> say(app, name, f), 30 * 60 * 1000L, null, ":no-sout-audio");
                if (why != null || !two.isFile() || two.length() < 64) throw new java.io.IOException(why == null ? "VLC made no video" : why);
                if (!dir.isDirectory()) dir.mkdirs();
                String base = VideoFit.name(name, w, h).replaceAll("\\.mp4$", "");
                made = new File(dir, base + ".mp4");
                for (int n = 1; made.exists(); n++) made = new File(dir, base + "(" + n + ").mp4");
                // The original's sound, copied as it is.
                Mp4Media.withSound(two, video, made, w, h);
                said = "ReRendered " + name + " at " + w + "×" + h + " (" + VideoFit.said(p) + "): " + made.getPath();
            } catch (Exception ex) {
                if (made != null) made.delete();
                made = null;
                said = "Could not ReRender " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                if (work != null) {
                    File[] left = work.listFiles();
                    if (left != null) for (File f : left) f.delete();
                    work.delete();
                }
            }
            final String shown = said;
            last = made;
            lastSaid = said;
            SwingUtilities.invokeLater(() -> app.setNow(shown));
        }, "pulsekit-rerender").start();
    }

    private static long lastSay;

    static void say(Pulsekit app, String name, double f) {
        long now = System.currentTimeMillis();
        if (now - lastSay < 500) return;
        lastSay = now;
        final String text = "ReRendering " + name + "… " + Math.round(f * 100) + "%";
        SwingUtilities.invokeLater(() -> app.setNow(text));
    }
}
