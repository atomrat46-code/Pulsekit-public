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
        last = new AlertDialog.Builder(app)
            .setTitle("General settings")
            .setView(col)
            .setPositiveButton("Close", null)
            .show();
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
