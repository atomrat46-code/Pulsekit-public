package pulsekit;

import java.io.File;
import javax.swing.SwingUtilities;

/**
 * Extract audio (a video thumbnail's item: the Media browser, Downloads, DB/Prompts): the video's
 * sound as <video>-audio.m4a, copied out of the MP4 without re-encoding, beside the video when its
 * folder takes files, else in the program files folder. Off the Swing thread; the status line says
 * where it went (or why not), then asks whether to add it to the AudioMixer as a track.
 */
final class AudioExtract {
    private AudioExtract() {}

    /** The file made last (for the tests). */
    static volatile File last;
    static volatile String lastSaid;

    /** After it: whether to add the sound to the AudioMixer as a track (off the Swing thread when yes). */
    static void offer(final Pulsekit app, final File made) {
        Object[] options = {MixerOut.ADD, MixerOut.NOT_NOW};
        int pick = javax.swing.JOptionPane.showOptionDialog(app, MixerOut.ask(made.getName()), MixerOut.ASK_TITLE, javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (pick != 0) return;
        app.setNow("Adding " + made.getName() + " to the AudioMixer…");
        new Thread(() -> {
            String said;
            try {
                said = MixerOut.addOne(made.getName(), java.nio.file.Files.readAllBytes(made.toPath()));
            } catch (Exception ex) {
                said = "Could not add " + made.getName() + " to the AudioMixer: " + ex.getMessage();
            }
            final String shown = said;
            lastSaid = said;
            SwingUtilities.invokeLater(() -> app.setNow(shown));
        }, "pulsekit-extract-mixer").start();
    }

    /** `video` (a file on disk, named `name`): its sound beside it, or in `fallback`. */
    static void run(Pulsekit app, File video, String name, File fallback) {
        run(app, video, name, fallback, true);
    }

    /** As above; `beside` false (a library file's copy): into `fallback` itself. */
    static void run(final Pulsekit app, final File video, final String name, final File fallback, final boolean beside) {
        app.setNow("Extracting the audio of " + name + "…");
        new Thread(() -> {
            String said;
            File made = null;
            try {
                File dir = video.getAbsoluteFile().getParentFile();
                if (!beside || dir == null || !dir.canWrite()) dir = fallback;
                made = Mp4Media.extractInto(video, name, dir);
                said = "Extracted the audio of " + name + ": " + made.getPath();
            } catch (Exception ex) {
                said = "Could not extract the audio of " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final String shown = said;
            final File done = made;
            last = made;
            lastSaid = said;
            SwingUtilities.invokeLater(() -> {
                app.setNow(shown);
                if (done != null) offer(app, done);
            });
        }, "pulsekit-extract-audio").start();
    }
}
