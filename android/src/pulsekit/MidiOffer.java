package pulsekit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.widget.Button;

/**
 * After a MidiDrumGen run: a MIDI ready dialog plays the groove it made with Pulsekit's kit sounds
 * (the imported SoundFont, through the active drum set), as a file set plays its source MIDI.
 * Play / Stop and Close. The MIDI is already imported as a file set.
 */
final class MidiOffer {
    private static MediaPlayer player;
    /** The dialog shown last, for the tests. */
    static AlertDialog last;

    private MidiOffer() {}

    /** Shows the offer for the run's MIDI; false when it made none, or none with drums for the kit. */
    static boolean offer(final MainActivity app, JavaRun.Result result) {
        if (result == null || result.files == null || app.playback == null) return false;
        JavaRun.FileOut midi = null;
        for (JavaRun.FileOut f : result.files) {
            String low = f.name == null ? "" : f.name.toLowerCase();
            if ((low.endsWith(".mid") || low.endsWith(".midi")) && f.bytes != null && f.bytes.length >= 4 && f.bytes[0] == 'M' && f.bytes[1] == 'T') midi = f;
        }
        if (midi == null) return false;
        short[] pcm;
        try {
            pcm = AudioIo.renderMidiDrums(midi.bytes, app.playback.mixVoices(), 22050);
        } catch (Exception ex) {
            pcm = null;
        }
        if (pcm == null || pcm.length == 0) return false;
        final java.io.File wav = new java.io.File(app.getCacheDir(), "midi-offer.wav");
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(wav);
            try {
                out.write(AudioIo.encodeWav(pcm, 22050));
            } finally {
                out.close();
            }
        } catch (Exception ex) {
            return false;
        }
        int seconds = Math.round(pcm.length / 22050f);
        final AlertDialog dialog = new AlertDialog.Builder(app)
            .setTitle("MIDI ready")
            .setMessage(midi.name + " (" + (seconds / 60) + ":" + String.format(java.util.Locale.US, "%02d", seconds % 60)
                + ") is imported as a file set.\n\nPlay sounds it with Pulsekit's kit (your imported SoundFont, if any).")
            .setNeutralButton("Play", null)
            .setPositiveButton("Close", null)
            .create();
        dialog.setOnDismissListener(d -> stop());
        dialog.setOnShowListener(d -> {
            final Button play = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            play.setTag("midi-offer:play");
            play.setOnClickListener(v -> {
                if (player != null) {
                    stop();
                    play.setText("Play");
                    return;
                }
                try {
                    player = new MediaPlayer();
                    player.setDataSource(wav.getAbsolutePath());
                    player.setOnCompletionListener(mp -> {
                        stop();
                        play.setText("Play");
                    });
                    player.prepare();
                    player.start();
                    play.setText("Stop");
                } catch (Exception ex) {
                    stop();
                    app.setNow("Could not play the MIDI");
                }
            });
        });
        last = dialog;
        dialog.show();
        return true;
    }

    static boolean playing() {
        return player != null;
    }

    static void stop() {
        if (player == null) return;
        try {
            player.release();
        } catch (Exception ignored) {
            // released already
        }
        player = null;
    }
}
