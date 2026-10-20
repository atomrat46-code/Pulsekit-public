package pulsekit;

import android.app.AlertDialog;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * File > General settings: "Use encrypted DB". Unticked, the prompt library is copied into an
 * unencrypted one, which is used from then on (quicker on a slow phone); ticked again, the
 * encrypted one is brought up to date and the unencrypted one deleted. The copy runs in the
 * background ("Processing DB, please wait"); the setting is kept by which library is there.
 */
final class GeneralSettings {
    /** The dialog shown last, for the tests. */
    static AlertDialog last;

    private GeneralSettings() {}

    static void show(final MainActivity app) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(12), app.dp(20), app.dp(4));
        final CheckBox box = new CheckBox(app);
        box.setText("Use encrypted DB");
        box.setTag("settings-encrypted");
        box.setChecked(PromptVault.encrypted(app.getFilesDir()));
        col.addView(box);
        TextView note = app.text("The prompt library is kept encrypted with the phone's keystore. Unticked, it is copied into an unencrypted library "
            + "in the app's own storage and used from then on: opening and saving are quicker. Ticked again, the encrypted library is brought up to date "
            + "and the unencrypted one is deleted.", 12, false);
        note.setPadding(0, app.dp(8), 0, 0);
        col.addView(note);
        box.setOnCheckedChangeListener((b, on) -> change(app, box, on));
        // After a run with --saveprompt: ask where the prompt goes (Save prompt), or store it where its sheet says.
        PromptSave.load(app.getFilesDir());
        final CheckBox ask = new CheckBox(app);
        ask.setText("Ask where to save a prompt after a run");
        ask.setTag("settings-ask-save");
        ask.setChecked(PromptSave.ask());
        ask.setOnCheckedChangeListener((b, on) -> PromptSave.setAsk(on));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = app.dp(12);
        col.addView(ask, alp);
        TextView askNote = app.text("A run with --saveprompt (Save the prompt into DB) shows Save prompt: main category, subcategory, name and description; "
            + "the prompt is saved as its next version, with the date. A prompt the same as one in the library already is not saved again. "
            + "Unticked, it goes into the category its sheet names.", 12, false);
        askNote.setPadding(0, app.dp(4), 0, 0);
        col.addView(askNote);
        // Sogni: which Sogni tools PyJav lists.
        AppSettings.load(app.getFilesDir());
        col.addView(headline(app, "Sogni"));
        final CheckBox sogni = new CheckBox(app);
        final CheckBox music = new CheckBox(app);
        sogni.setText(AppSettings.SHOW_SOGNI);
        sogni.setTag("settings-show-sogni");
        sogni.setChecked(AppSettings.showSogni());
        music.setText(AppSettings.MUSIC_ONLY);
        music.setTag("settings-sogni-music-only");
        music.setChecked(AppSettings.musicOnly());
        music.setEnabled(AppSettings.showSogni());
        sogni.setOnCheckedChangeListener((b, on) -> {
            AppSettings.setShowSogni(on);
            if (!on && music.isChecked()) {
                music.setChecked(false);
                AppSettings.setMusicOnly(false);
            }
            music.setEnabled(on);
            app.setNow(on ? "Sogni tools are listed in PyJav" : "No Sogni tools in PyJav's list");
        });
        music.setOnCheckedChangeListener((b, on) -> {
            AppSettings.setMusicOnly(on);
            app.setNow(on ? "Only SogniMusic and SogniChat are listed of the Sogni tools" : "All Sogni tools are listed in PyJav");
        });
        col.addView(sogni);
        col.addView(music);
        TextView sogniNote = app.text("Unticked, no Sogni program is in PyJav's list (Select program). Only music tools and chat: SogniMusic and SogniChat "
            + "are listed, the image and video tools are not.", 12, false);
        sogniNote.setPadding(0, app.dp(4), 0, 0);
        col.addView(sogniNote);
        // File types: a type unticked is left out of the thumbnails (its letter too) and of the playlists shown.
        col.addView(headline(app, "File types"));
        for (int i = 0; i < AppSettings.TYPE_LETTERS.length; i++) {
            final String letter = AppSettings.TYPE_LETTERS[i];
            final CheckBox type = new CheckBox(app);
            type.setText(AppSettings.TYPE_LABELS[i] + " (" + letter + ")");
            type.setTag("settings-show:" + letter);
            type.setChecked(AppSettings.shows(letter));
            type.setOnCheckedChangeListener((b, on) -> {
                AppSettings.setShows(letter, on);
                app.setNow(DbFilter.label(letter) + (on ? " are shown again" : " are left out of the thumbnails and playlists"));
            });
            col.addView(type);
        }
        TextView typesNote = app.text("A type unticked is not shown in the thumbnail screens (Browse DB, the Reference, Result and Prompt files, the Media browser), "
            + "its letter is not there either, and a playlist with files of that type shows the others only.", 12, false);
        typesNote.setPadding(0, app.dp(4), 0, 0);
        col.addView(typesNote);
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        last = new AlertDialog.Builder(app)
            .setTitle("General settings")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show();
    }

    /** A section's headline ("Sogni", "File types"). */
    static TextView headline(MainActivity app, String text) {
        TextView t = app.text(text, 15, true);
        t.setPadding(0, app.dp(16), 0, app.dp(2));
        return t;
    }

    /** Copies the library over in the background; the box is greyed until it is done, and set back if it fails. */
    static void change(final MainActivity app, final CheckBox box, final boolean on) {
        if (PromptVault.encrypted(app.getFilesDir()) == on) return;
        box.setEnabled(false);
        app.setNow(on ? "Encrypting the prompt library…" : "Copying the prompt library unencrypted…");
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            String said;
            boolean ok;
            try {
                PromptVault.setEncrypted(app.getFilesDir(), on);
                said = on ? "The prompt library is encrypted again; the unencrypted copy is deleted" : "The prompt library is now kept unencrypted";
                ok = true;
            } catch (Throwable ex) {
                said = "Could not change the prompt library: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                ok = false;
            }
            final String shown = said;
            final boolean done = ok;
            app.handler.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                box.setOnCheckedChangeListener(null);
                if (!done) box.setChecked(!on);
                box.setOnCheckedChangeListener((b, now) -> change(app, box, now));
                box.setEnabled(true);
                app.setNow(shown);
                android.widget.Toast.makeText(app, shown, android.widget.Toast.LENGTH_LONG).show();
            });
        }, "pulsekit-db-settings").start();
    }
}
