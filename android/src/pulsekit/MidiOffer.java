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
        return offer(app, midi);
    }

    /** WAV ready's Convert hits into MIDI asked: MIDI ready follows the DrumMidi_CRT run. */
    static boolean hitsAsked;

    /** Convert hits into MIDI's run: MIDI ready on the MIDI DrumMidi_CRT made (in its files, or at `outputPath`). */
    static boolean offerHits(final MainActivity app, JavaRun.Result result, String outputPath) {
        hitsAsked = false;
        if (offer(app, result)) return true;
        if (outputPath == null || app.playback == null) return false;
        java.io.File out = new java.io.File(outputPath);
        try {
            byte[] b = out.isFile() ? java.nio.file.Files.readAllBytes(out.toPath()) : null;
            if (b == null || b.length < 4 || b[0] != 'M' || b[1] != 'T') return false;
            return offer(app, new JavaRun.FileOut(out.getName(), b));
        } catch (Exception ex) {
            return false;
        }
    }

    /** MIDI ready on one MIDI; false when it has no drums for the kit. */
    static boolean offer(final MainActivity app, JavaRun.FileOut midi) {
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
        // The MIDI as a file for its items (the MIDI editor, Midi2Wav, the default SoundFont).
        final java.io.File file = new java.io.File(new java.io.File(app.getCacheDir(), "pyjav-in"), midi.name.replace('/', '_').replace(' ', '_'));
        try {
            file.getParentFile().mkdirs();
            java.nio.file.Files.write(file.toPath(), midi.bytes);
        } catch (Exception ex) {
            return false;
        }
        final String midiName = midi.name;
        final long size = midi.bytes.length;
        android.widget.LinearLayout col = new android.widget.LinearLayout(app);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(midi.name + " (" + (seconds / 60) + ":" + String.format(java.util.Locale.US, "%02d", seconds % 60)
            + ") is imported as a file set.\n\nPlay sounds it with Pulsekit's kit (your imported SoundFont, if any).", 14, false));
        for (final String item : TagDialogs.withUseLast(app, items(PictureOffer.isFavourite(app, midi.name, size), false).toArray(new String[0]), ReadyRecent.MIDI)) {
            android.widget.TextView b = app.pill(item, false, v -> run(app, file, midiName, size, item));
            b.setTag("midi-offer-item:" + item);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = app.dp(6);
            col.addView(b, lp);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        final AlertDialog dialog = new AlertDialog.Builder(app)
            .setTitle("MIDI ready")
            .setView(scroll)
            .setNeutralButton("Play", null)
            .setNegativeButton("Add to composer stack", null)
            .setPositiveButton("Close", null)
            .create();
        // The file set the run's MIDI became: its patterns and fills go on the Composer's stack.
        final String set = app.importLibrary.lastPackedSet;
        final String shown = midi.name;
        dialog.setOnDismissListener(d -> stop());
        dialog.setOnShowListener(d -> {
            Button stack = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            stack.setTag("midi-offer:composer");
            stack.setOnClickListener(v -> {
                if (set == null) {
                    app.setNow("No file set of " + shown + " to put on the stack");
                    return;
                }
                Composer c = app.composerPage.composer();
                int added = c.addFileSet(set, app.learned, app.learnedFills);
                c.save(app.getFilesDir());
                app.setNow(Composer.stackedNote(shown, added));
                dialog.dismiss();
            });
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

    /** MIDI ready's items: favourites, group, tags, category, playlist, then the MIDI's own (MediaDir.kindItems; Add to composer stack is its button). */
    static java.util.List<String> items(boolean favourite, boolean plugins) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add(favourite ? FileFavourites.REMOVE_SHORT : FileFavourites.ADD_SHORT);
        out.add(FileGroups.ADD);
        out.add(MediaTags.ADD_TAG);
        out.add(MediaTags.ADD_CATEGORY);
        out.add(Playlist.ADD);
        for (String k : MediaDir.kindItems("x.mid", plugins)) if (!MediaDir.TO_COMPOSER.equals(k)) out.add(k);
        return out;
    }

    /** One of MIDI ready's items on the run's MIDI. */
    static void run(MainActivity app, java.io.File file, String name, long size, String item) {
        String kind = ReadyRecent.MIDI;
        if (item.equals(FileFavourites.ADD_SHORT) || item.equals(FileFavourites.REMOVE_SHORT)) PictureOffer.toggleFavourite(app, name, size);
        else if (item.equals(FileGroups.ADD)) GroupDialogs.addTo(app, PictureOffer.groupKeys(app, name, size), name, null, kind);
        else if (item.equals(MediaTags.ADD_TAG)) TagDialogs.tags(app, name, size, null, kind);
        else if (item.startsWith(ReadyRecent.USE_LAST)) TagDialogs.useLast(app, name, size, kind);
        else if (item.equals(ReadyRecent.USE_ALL)) TagDialogs.useAll(app, name, size, kind, null, file);
        else if (item.equals(MediaTags.ADD_CATEGORY)) TagDialogs.category(app, name, size, null, kind);
        else if (item.equals(Playlist.ADD)) PlaylistScreen.addReady(app, name, null, file);
        else if (item.equals(MediaDir.PLAY_SF)) MediaBrowser.playDefault(app, file, name);
        else if (item.equals(MediaDir.TO_FILE_SET)) MediaBrowser.toFileSet(app, file, name);
        else if (item.equals(MediaDir.MIDI_EDIT)) {
            // Save keeps the edited MIDI in Download as <name>-edited.mid.
            final String edited = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-edited.mid";
            try {
                MidiEditScreen.openFile(app, name, java.nio.file.Files.readAllBytes(file.toPath()), midi -> {
                    String where = ArtJava.publish(app, edited, midi);
                    return where == null ? edited : where;
                });
            } catch (Exception ex) {
                app.setNow("Could not read " + name);
            }
        } else if (item.equals(MediaDir.RENDER_SF2)) {
            if (last != null) last.dismiss();
            app.openKitView("py");
            app.programMenus.selectProgram("Java", "Midi2Wav.java");
            app.pyJav.pkUseInputPath(file.getAbsolutePath());
            app.pyJav.pkOpenParams();
        }
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
