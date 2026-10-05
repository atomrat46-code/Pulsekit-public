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

    /**
     * Picks DrumMidi_CRT.jar, which takes the audio input, and (when `run`) runs it. The jar carries
     * JLayer's MP3 decoder, so an MP3 input is given as it is.
     */
    static void makeDrumMidi(final MainActivity app, final boolean run) {
        app.programMenus.selectProgram("Java", "DrumMidi_CRT.jar");
        if (run) app.pyJav.pkRunPyJav();
    }

    interface Done {
        void done(java.io.File wav, String error);
    }

    /**
     * Decodes an MP3 to a mono 16-bit WAV at its own sample rate, beside it (song.mp3 → song.wav), off
     * the UI thread; `done` runs on the UI thread with the WAV, or null and why not.
     */
    static void toWav(final MainActivity app, final java.io.File mp3, final Done done) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                java.io.File wav = null;
                String error = null;
                try {
                    java.io.FileInputStream in = new java.io.FileInputStream(mp3);
                    ProjectIo.Decoded d;
                    try {
                        d = ProjectIo.decodeNative(in.getFD());
                    } finally {
                        in.close();
                    }
                    String name = mp3.getName();
                    java.io.File out = new java.io.File(mp3.getParentFile(), name.substring(0, name.length() - 4) + ".wav");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                    try {
                        fos.write(AudioIo.encodeWav(d.mono, d.rate));
                    } finally {
                        fos.close();
                    }
                    wav = out;
                } catch (Throwable ex) {
                    error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }
                final java.io.File made = wav;
                final String why = error;
                app.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        done.done(made, why);
                    }
                });
            }
        }, "pulsekit-mp3-wav").start();
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
