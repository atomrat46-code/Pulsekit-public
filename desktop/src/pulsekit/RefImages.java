package pulsekit;

import java.io.File;
import java.nio.file.Files;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * A picture or a video's first frame as a reference of a Sogni program, from any screen (the Media
 * browser, Downloads, DB/Prompts, Browse DB): a picture to upscale (smaller than the last one upscaled,
 * or not 4:7) is asked about first, Upscale first (ImageUpscaler on PyJav with it as its input) or Use
 * as it is (Process as a reference: the program, its prompt, Reference file 1 or 2).
 */
final class RefImages {
    private RefImages() {}

    /** The tests' answer to the upscale question (UpscaleCheck.UPSCALE_FIRST, AS_IT_IS or Cancel); null asks. */
    static java.util.function.Function<String, String> answer;
    /** The question asked last, the picture it was for and the frame written last (for the tests). */
    static String lastQuestion;
    static File lastPicture;
    static File lastFrame;

    /**
     * The picture as a reference: asked Upscale first / Use as it is / Cancel when it needs upscaling,
     * else Process as a reference straight away. `close`: a window closed before PyJav shows (the
     * browser it came from); `back`: Return in PyJav (and in Process as a reference) opens it again.
     */
    static void offer(Pulsekit app, File picture, java.awt.Window close, Runnable back) {
        lastPicture = picture;
        byte[] head = MediaDir.head(picture);
        String q = UpscaleCheck.refQuestion(PromptDb.dir(), picture.getName(), head);
        lastQuestion = q;
        if (q != null) {
            Object[] options = {UpscaleCheck.UPSCALE_FIRST, UpscaleCheck.AS_IT_IS, "Cancel"};
            String said = answer != null ? answer.apply(q) : null;
            int ans = said != null ? java.util.Arrays.asList(options).indexOf(said)
                : JOptionPane.showOptionDialog(close != null ? close : app, q, MediaDir.PROCESS_REF, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (ans == 0) {
                upscale(app, picture, close, back);
                return;
            }
            if (ans != 1) return;
        }
        ProcessRefDialog.open(app, picture, back);
    }

    /** ImageUpscaler on PyJav with the picture as its input, its Params open; Return opens `back`. */
    static void upscale(Pulsekit app, File picture, java.awt.Window close, Runnable back) {
        if (close != null) close.dispose();
        app.showView("py");
        app.programMenus.selectListedProgram("Java", "ImageUpscaler.java");
        app.pyJav.setInputFile(picture);
        if (back != null) app.pyJav.setReturn("Return", back);
        SwingUtilities.invokeLater(() -> app.pyJav.openParams());
        app.setNow("ImageUpscaler: " + picture.getName());
    }

    /**
     * The video's first frame (full size, a JPEG in ~/.pulsekit/first-frames, read by VLC off the
     * Swing thread), then `then` with it on the Swing thread; nothing when it cannot be read (said why).
     */
    static void firstFrame(final Pulsekit app, final File video, final java.util.function.Consumer<File> then) {
        app.setNow("Reading the first frame of " + video.getName() + "…");
        new Thread(() -> {
            File out = null;
            try {
                byte[] jpeg = MediaBrowser.frameReader.apply(video, 8192);
                if (jpeg != null && jpeg.length > 0) {
                    File dir = new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "first-frames");
                    if (!dir.isDirectory()) dir.mkdirs();
                    out = new File(dir, MediaDir.firstFrameName(video.getName(), "jpg"));
                    Files.write(out.toPath(), jpeg);
                }
            } catch (Exception ex) {
                out = null;
            }
            final File got = out;
            SwingUtilities.invokeLater(() -> {
                if (got == null) {
                    app.setNow("Could not read the first frame of " + video.getName() + " (VLC plays the videos: is it installed?)");
                    return;
                }
                lastFrame = got;
                app.setNow("First frame of " + video.getName() + " · " + got.getName());
                then.accept(got);
            });
        }, "pulsekit-first-frame").start();
    }

    /**
     * Choose file on a picture row (Sogni Params): a frame of a video (C in its player) fills the row;
     * one that needs upscaling is asked Upscale first (ImageUpscaler, the row left as it is), Use as it is
     * or Cancel. `close`: the player (and the browser) closed when the row is filled.
     */
    static void forRow(Pulsekit app, File frame, java.awt.Window close, java.util.function.Consumer<File> row) {
        lastPicture = frame;
        String q = UpscaleCheck.refQuestion(PromptDb.dir(), frame.getName(), MediaDir.head(frame));
        lastQuestion = q;
        if (q != null) {
            Object[] options = {UpscaleCheck.UPSCALE_FIRST, UpscaleCheck.AS_IT_IS, "Cancel"};
            String said = answer != null ? answer.apply(q) : null;
            int ans = said != null ? java.util.Arrays.asList(options).indexOf(said)
                : JOptionPane.showOptionDialog(close != null ? close : app, q, "Reference image", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (ans == 0) {
                upscale(app, frame, close, null);
                return;
            }
            if (ans != 1) return;
        }
        if (close != null) close.dispose();
        row.accept(frame);
    }

    /** First frame as a reference image: the video's first frame, then offer(). */
    static void firstFrameAsRef(Pulsekit app, File video, java.awt.Window close, Runnable back) {
        firstFrame(app, video, frame -> offer(app, frame, close, back));
    }
}
