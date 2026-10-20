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
        // What it is, then its items: favourites, group, tags, category, playlist, Convert into WAV.
        final String shownName = name;
        final long size = data.length;
        android.widget.LinearLayout col = new android.widget.LinearLayout(app);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        android.widget.TextView said = app.text(name + " (" + (data.length / 1024) + " KB) is in Downloads and is now PyJav's input.\n\n"
            + "Make drum MIDI runs DrumMidi_CRT on it and imports the drums as a file set.", 14, false);
        col.addView(said);
        final String[] items = {PictureOffer.isFavourite(app, name, size) ? FileFavourites.REMOVE_SHORT : FileFavourites.ADD_SHORT, FileGroups.ADD,
            MediaTags.ADD_TAG, MediaTags.ADD_CATEGORY, Playlist.ADD, TO_WAV};
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            android.widget.TextView item = app.pill(items[i], false, v -> {
                if (which == 0) PictureOffer.toggleFavourite(app, shownName, size);
                else if (which == 1) GroupDialogs.addTo(app, PictureOffer.groupKeys(app, shownName, size), shownName, null, ReadyRecent.SOUND);
                else if (which == 2) TagDialogs.tags(app, shownName, size, null, ReadyRecent.SOUND);
                else if (which == 3) TagDialogs.category(app, shownName, size, null, ReadyRecent.SOUND);
                else if (which == 4) PlaylistScreen.addReady(app, shownName, null, new java.io.File(path));
                else convert(app, new java.io.File(path));
            });
            item.setTag("audio-item:" + items[i]);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = app.dp(6);
            col.addView(item, lp);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        final AlertDialog dialog = new AlertDialog.Builder(app)
            .setTitle(name.toLowerCase().endsWith(".mp3") ? "MP3 ready" : "Audio ready")
            .setView(scroll)
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
        last = dialog;
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

    static final String TO_WAV = "Convert into WAV";

    /** The offer shown last (for the tests). */
    static AlertDialog last;

    /** Convert into WAV: the MP3 decoded to a WAV, kept in Downloads beside it. */
    static void convert(final MainActivity app, java.io.File audio) {
        if (!audio.getName().toLowerCase().endsWith(".mp3")) {
            app.setNow(audio.getName() + " is a WAV already");
            return;
        }
        app.setNow("Converting " + audio.getName() + " into WAV\u2026");
        toWav(app, audio, (wav, error) -> {
            if (wav == null) {
                app.setNow("Could not convert " + audio.getName() + (error == null ? "" : ": " + error));
                return;
            }
            String where;
            try {
                where = ArtJava.publish(app, wav.getName(), java.nio.file.Files.readAllBytes(wav.toPath()));
            } catch (Exception ex) {
                where = null;
            }
            app.setNow(where == null ? "Made " + wav.getName() + " (not in Downloads)" : "Saved " + where);
        });
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
