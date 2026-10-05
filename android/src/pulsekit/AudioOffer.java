package pulsekit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.widget.Button;

/**
 * After a run that made an audio file (SogniMusic's track): the file becomes PyJav's audio input,
 * and a dialog offers Play / Stop, Make drum MIDI (DrumMidi_CRT on it, which imports the drums as a
 * file set), and Close. The PyJav arguments of the program that made it are left alone.
 */
final class AudioOffer {
    private static MediaPlayer player;

    private AudioOffer() {}

    /** Shows the offer for `name` from the run's files; false when the run made no such file. */
    static boolean offer(final MainActivity app, String name, JavaRun.Result result) {
        if (name == null || result == null || result.files == null) return false;
        byte[] data = null;
        for (JavaRun.FileOut f : result.files) if (name.equals(f.name)) data = f.bytes;
        if (data == null) return false;
        final String path = useAsInput(app, name, data);
        if (path == null) return false;
        final AlertDialog dialog = new AlertDialog.Builder(app)
            .setTitle("Audio ready")
            .setMessage(name + " (" + (data.length / 1024) + " KB) is in Downloads and is now PyJav's input.\n\n"
                + "Make drum MIDI runs DrumMidi_CRT on it and imports the drums as a file set.")
            .setNeutralButton("Play", null)
            .setPositiveButton("Make drum MIDI", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    stop();
                    makeDrumMidi(app, true);
                }
            })
            .setNegativeButton("Close", null)
            .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                stop();
            }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                final Button play = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                play.setOnClickListener(v -> {
                    if (player != null) {
                        stop();
                        play.setText("Play");
                        return;
                    }
                    try {
                        player = new MediaPlayer();
                        player.setDataSource(path);
                        player.setOnCompletionListener(mp -> {
                            stop();
                            play.setText("Play");
                        });
                        player.prepare();
                        player.start();
                        play.setText("Stop");
                    } catch (Exception ex) {
                        stop();
                        app.setNow("Could not play " + new java.io.File(path).getName());
                    }
                });
            }
        });
        dialog.show();
        return true;
    }

    /** Copies the audio into PyJav's input folder and makes it the audio input. Returns its path, or null. */
    static String useAsInput(MainActivity app, String name, byte[] data) {
        try {
            java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, name.replace(' ', '_'));
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                fos.write(data);
            } finally {
                fos.close();
            }
            app.pyJav.pkAudioInputPath = out.getAbsolutePath();
            return out.getAbsolutePath();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Picks DrumMidi_CRT, which takes the audio input, and (when `run`) runs it. */
    static void makeDrumMidi(MainActivity app, boolean run) {
        app.programMenus.selectProgram("Java", "DrumMidi_CRT.java");
        if (run) app.pyJav.pkRunPyJav();
    }

    static void stop() {
        if (player == null) return;
        try {
            player.stop();
        } catch (Exception ignored) {
            // already stopped
        }
        player.release();
        player = null;
    }
}
