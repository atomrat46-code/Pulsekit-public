package pulsekit;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

/**
 * File > Exit (ExitCheck), as on Android: the code editor, playlist editor, MIDI editor and prompt
 * editor asked whether they hold edits not saved yet; Save and exit saves them (Save as for one with
 * no place yet), Exit without saving closes, as the window's close does.
 */
final class ExitScreen {
    private ExitScreen() {}

    /** The tests: the answer (0 save, 1 exit without saving, 2 cancel; null: ask), no closing, and what was asked. */
    static Integer answer;
    static boolean keepRunning;
    static boolean exited;
    static String asked;

    static List<String> unsaved(Pulsekit app) {
        List<String> out = new ArrayList<String>();
        if (app.codeSave.dirty && app.codeSave.canSave()) out.add("Code editor: " + app.codeSave.name());
        for (String p : PlaylistWindow.edited) if (Playlist.savedAt(PromptDb.dir(), p) != null) out.add("Playlist editor: " + p);
        if (MidiEditScreen.now != null && MidiEditScreen.now.edit.changed) out.add("MIDI editor: " + MidiEditScreen.now.name);
        String prompt = app.promptsPage.unsavedWhat();
        if (prompt != null) out.add("Prompt editor: " + prompt);
        return out;
    }

    static void show(Pulsekit app) {
        List<String> open = unsaved(app);
        if (open.isEmpty()) {
            exit(app);
            return;
        }
        asked = ExitCheck.question(open);
        Object[] options = {ExitCheck.SAVE, ExitCheck.DISCARD, "Cancel"};
        int got = answer != null ? answer : JOptionPane.showOptionDialog(app, asked, ExitCheck.TITLE, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (got == 1) {
            exit(app);
            return;
        }
        if (got != 0) return;
        if (app.codeSave.dirty && app.codeSave.canSave()) app.codeSave.save();
        for (String name : new ArrayList<String>(PlaylistWindow.edited)) {
            String at = Playlist.savedAt(PromptDb.dir(), name);
            if (at != null) PlaylistWindow.savePls(app, Playlist.load(PromptDb.dir(), name), new java.io.File(at));
        }
        if (MidiEditScreen.now != null && MidiEditScreen.now.edit.changed) MidiEditScreen.now.save();
        if (app.promptsPage.unsavedWhat() != null) app.promptsPage.saveInPlace();
        // Still not saved (Save as was cancelled): Pulsekit stays open.
        if (!unsaved(app).isEmpty()) {
            app.setNow("Not everything was saved: Pulsekit stays open");
            return;
        }
        exit(app);
    }

    static void exit(Pulsekit app) {
        exited = true;
        if (keepRunning) return;
        // As the window's close: what Pulsekit keeps on its own is saved on the way out.
        app.dispatchEvent(new java.awt.event.WindowEvent(app, java.awt.event.WindowEvent.WINDOW_CLOSING));
    }
}
