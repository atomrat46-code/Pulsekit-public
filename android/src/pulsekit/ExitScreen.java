package pulsekit;

import android.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;

/**
 * File > Exit (ExitCheck): the code editor, playlist editor, MIDI editor and prompt editor asked
 * whether they hold edits not saved yet; Save and exit saves them (one that needs a place picked
 * asks for it, and Pulsekit stays open), Exit without saving closes.
 */
final class ExitScreen {
    private ExitScreen() {}

    /** The question shown last, for the tests; and no closing in them. */
    static AlertDialog last;
    static boolean keepRunning;
    static boolean exited;

    /** The edits not saved yet, as they are listed. */
    static List<String> unsaved(MainActivity app) {
        List<String> out = new ArrayList<String>();
        if (app.codeSave.dirty && app.codeSave.canSave()) out.add("Code editor: " + app.codeSave.name());
        for (String p : PlaylistScreen.edited) if (pulsekit.Playlist.savedAt(app.getFilesDir(), p) != null) out.add("Playlist editor: " + p);
        if (MidiEditScreen.now != null && MidiEditScreen.now.edit.changed) out.add("MIDI editor: " + MidiEditScreen.now.name);
        String prompt = PromptSheet.unsavedWhat();
        if (prompt != null) out.add("Prompt editor: " + prompt);
        return out;
    }

    static void show(final MainActivity app) {
        final List<String> open = unsaved(app);
        if (open.isEmpty()) {
            exit(app);
            return;
        }
        last = new AlertDialog.Builder(app).setTitle(ExitCheck.TITLE).setMessage(ExitCheck.question(open))
            .setPositiveButton(ExitCheck.SAVE, (d, w) -> {
                if (saveAll(app)) exit(app);
                else app.setNow("Pick where to save, then File > Exit again");
            })
            .setNeutralButton(ExitCheck.DISCARD, (d, w) -> exit(app))
            .setNegativeButton("Cancel", null).show();
    }

    /** Saves what is open; false when one waits for a place to be picked (Save as). */
    static boolean saveAll(MainActivity app) {
        boolean done = true;
        if (app.codeSave.dirty && app.codeSave.canSave()) {
            boolean placed = app.codeSave.target != null && app.codeSave.name().equals(app.codeSave.targetFor);
            app.codeSave.save();
            if (!placed) done = false;
        }
        for (String name : new ArrayList<String>(PlaylistScreen.edited)) {
            String at = pulsekit.Playlist.savedAt(app.getFilesDir(), name);
            if (at == null) continue;
            try {
                PlaylistScreen.savePls(app, pulsekit.Playlist.load(app.getFilesDir(), name));
            } catch (Exception ex) {
                done = false;
            }
            if (PlaylistScreen.edited.contains(name)) done = false;
        }
        if (MidiEditScreen.now != null && MidiEditScreen.now.edit.changed) MidiEditScreen.now.save();
        if (PromptSheet.unsavedWhat() != null) PromptSheet.saveInPlace(app);
        return done;
    }

    static void exit(MainActivity app) {
        exited = true;
        if (keepRunning) return;
        // Closed as Back would close it: what Pulsekit keeps on its own is saved on the way out.
        app.finishAffinity();
    }
}
