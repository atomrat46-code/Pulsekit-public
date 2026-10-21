package pulsekit;

import android.app.AlertDialog;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * File > General settings: Ask where to save a prompt, Sogni and File types. Use encrypted DB is
 * in Database settings now (DbSettings), its change made here (change): unticked, the prompt library is copied into an
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
        AppSettings.load(app.getFilesDir());
        // Settings master password: everything greyed until Unlock settings is given the password.
        final java.util.List<android.view.View> controls = new java.util.ArrayList<android.view.View>();
        final Runnable lockNow = () -> lock(controls);
        unlockRow(app, col, lockNow);
        // After a run with --saveprompt: ask where the prompt goes (Save prompt), or store it where its sheet says.
        PromptSave.load(app.getFilesDir());
        final CheckBox ask = new CheckBox(app);
        ask.setText("Ask where to save a prompt after a run");
        ask.setTag("settings-ask-save");
        ask.setChecked(PromptSave.ask());
        ask.setOnCheckedChangeListener((b, on) -> PromptSave.setAsk(on));
        col.addView(ask);
        controls.add(ask);
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
        controls.add(sogni);
        controls.add(music);
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
            controls.add(type);
        }
        TextView typesNote = app.text("A type unticked is not shown in the thumbnail screens (Browse DB, the Reference, Result and Prompt files, the Media browser), "
            + "its letter is not there either, and a playlist with files of that type shows the others only.", 12, false);
        typesNote.setPadding(0, app.dp(4), 0, 0);
        col.addView(typesNote);
        // Tabs: the DB/Prompts tab shown or not.
        col.addView(headline(app, "Tabs"));
        final CheckBox promptsTab = new CheckBox(app);
        promptsTab.setText(AppSettings.SHOW_PROMPTS_TAB);
        promptsTab.setTag("settings-show-prompts-tab");
        promptsTab.setChecked(AppSettings.showPromptsTab());
        promptsTab.setOnCheckedChangeListener((b, on) -> {
            AppSettings.setShowPromptsTab(on);
            if (!on && "prompts".equals(app.view)) app.show("py");
            app.refreshTabs();
            app.setNow(on ? "The DB/Prompts tab is shown" : "No DB/Prompts tab (File > Import as Ref file and the galleries still reach the DB)");
        });
        col.addView(promptsTab);
        controls.add(promptsTab);
        // Security: the settings master password.
        col.addView(headline(app, "Security"));
        final CheckBox master = new CheckBox(app);
        master.setText(AppSettings.MASTER);
        master.setTag("settings-master");
        master.setChecked(AppSettings.masterOn());
        master.setOnCheckedChangeListener((b, on) -> {
            if (on == AppSettings.masterOn()) return;
            if (!on) {
                AppSettings.clearMaster();
                app.setNow("Settings master password off: the settings are open");
                return;
            }
            askMaster(app, ok -> {
                if (!ok) {
                    master.setOnCheckedChangeListener(null);
                    master.setChecked(false);
                    show(app);
                    return;
                }
                app.setNow("Settings master password on: the settings are locked when they close");
            });
        });
        col.addView(master);
        controls.add(master);
        TextView masterNote = app.text(AppSettings.MASTER_NOTE, 12, false);
        masterNote.setPadding(0, app.dp(4), 0, 0);
        col.addView(masterNote);
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app)
            .setTitle("General settings")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show();
        // Closed: locked again.
        last.setOnDismissListener(d -> AppSettings.relock());
        lockNow.run();
    }

    /** The settings' controls greyed while the master password locks them (Show only music and chat also needs the Sogni tools). */
    static void lock(java.util.List<android.view.View> controls) {
        boolean open = !AppSettings.locked();
        for (android.view.View v : controls) {
            boolean on = open;
            if ("settings-sogni-music-only".equals(v.getTag())) on = open && AppSettings.showSogni();
            v.setEnabled(on);
        }
    }

    /** The unlock asked for last (for the tests). */
    static AlertDialog lastUnlock;

    /** With a master password: a note and Unlock settings…, which asks it and (given right) runs `unlocked`. */
    static void unlockRow(final MainActivity app, LinearLayout col, final Runnable unlocked) {
        if (!AppSettings.masterOn()) return;
        col.addView(app.text(AppSettings.LOCKED_NOTE, 12, false));
        final TextView unlock = app.pill(AppSettings.UNLOCK, true, null);
        unlock.setTag("settings-unlock");
        unlock.setOnClickListener(v -> askUnlock(app, () -> {
            unlock.setVisibility(android.view.View.GONE);
            unlocked.run();
        }));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = app.dp(6);
        lp.bottomMargin = app.dp(6);
        col.addView(unlock, lp);
    }

    /** The master password asked for; right, `ok` runs. */
    static void askUnlock(final MainActivity app, final Runnable ok) {
        final android.widget.EditText pw = ZipFlow.passwordField(app, "Master password", "settings-master-password");
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(pw);
        lastUnlock = new AlertDialog.Builder(app).setTitle(AppSettings.UNLOCK.replace("\u2026", "")).setView(col)
            .setPositiveButton("OK", (d, w) -> {
                if (AppSettings.unlock(pw.getText().toString().toCharArray())) {
                    app.setNow("Settings unlocked");
                    ok.run();
                } else app.setNow("Wrong master password: the settings stay locked");
            })
            .setNegativeButton("Cancel", null).show();
    }

    interface Done {
        void done(boolean ok);
    }

    /** Settings master password ticked: the password twice; OK with both the same turns the secure mode on. */
    static void askMaster(final MainActivity app, final Done done) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text("The settings are changed only after this password is given. At least " + AppSettings.MASTER_MIN + " characters.", 13, false));
        final android.widget.EditText pw = ZipFlow.passwordField(app, "Master password", "settings-master-new");
        final android.widget.EditText again = ZipFlow.passwordField(app, "The password again", "settings-master-again");
        col.addView(pw);
        col.addView(again);
        lastUnlock = new AlertDialog.Builder(app).setTitle(AppSettings.MASTER).setView(col)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", (d, w) -> done.done(false)).show();
        lastUnlock.setCancelable(false);
        lastUnlock.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = pw.getText().toString();
            if (!a.equals(again.getText().toString())) {
                app.setNow("The passwords are not the same");
                return;
            }
            try {
                AppSettings.setMaster(a.toCharArray());
            } catch (Exception ex) {
                app.setNow(ex.getMessage());
                return;
            }
            lastUnlock.dismiss();
            done.done(true);
        });
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
