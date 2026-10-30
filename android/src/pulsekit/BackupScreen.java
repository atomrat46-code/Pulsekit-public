package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * File > Back up Pulsekit / Restore from backup (AppBackup): the parts ticked into a zip in
 * Download (with a password when the prompt library is in it), and back from one picked; Pulsekit
 * closes after a restore, so nothing it still holds in memory writes over what came back.
 */
final class BackupScreen {
    private BackupScreen() {}

    static final int OPEN_BACKUP = 91;

    /** The dialogs shown last, for the tests. */
    static AlertDialog last;
    /** What the last backup or restore said. */
    static volatile String lastSaid;
    /** The tests: no closing after a restore. */
    static boolean keepRunning;

    static File prefsDir(MainActivity app) {
        return new File(app.getFilesDir().getParentFile(), "shared_prefs");
    }

    static void show(final MainActivity app) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(AppBackup.NOTE, 13, false));
        final CheckBox[] boxes = new CheckBox[AppBackup.PARTS.length];
        for (int i = 0; i < AppBackup.PARTS.length; i++) {
            CheckBox b = new CheckBox(app);
            b.setText(AppBackup.LABELS[i]);
            b.setChecked(AppBackup.ON[i]);
            b.setTag("backup-part:" + AppBackup.PARTS[i]);
            boxes[i] = b;
            col.addView(b);
        }
        final EditText pw = ZipFlow.passwordField(app, "Password (at least " + DbSlots.MIN_PASSWORD + " characters)", "backup-password");
        final EditText again = ZipFlow.passwordField(app, "The password again", "backup-password-again");
        col.addView(pw);
        col.addView(again);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        last = new AlertDialog.Builder(app).setTitle(AppBackup.BACKUP).setView(scroll)
            .setPositiveButton("Back up", null)
            .setNeutralButton(AppBackup.RESTORE, (d, w) -> pickBackup(app))
            .setNegativeButton("Cancel", null).create();
        last.setOnShowListener(d -> last.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Set<String> parts = new LinkedHashSet<String>();
            for (int i = 0; i < boxes.length; i++) if (boxes[i].isChecked()) parts.add(AppBackup.PARTS[i]);
            String a = pw.getText().toString(), b = again.getText().toString();
            if (parts.isEmpty()) {
                app.setNow("Tick what to back up");
                return;
            }
            if (parts.contains(AppBackup.DB) || a.length() > 0) {
                if (a.length() < DbSlots.MIN_PASSWORD) {
                    app.setNow(DbSlots.SHORT_PASSWORD);
                    return;
                }
                if (!a.equals(b)) {
                    app.setNow("The passwords are not the same");
                    return;
                }
            }
            last.dismiss();
            backup(app, parts, a.length() == 0 ? null : a.toCharArray());
        }));
        last.show();
    }

    /** The backup written in Pulsekit's temp folder, then saved into Download. */
    static void backup(final MainActivity app, final Set<String> parts, final char[] password) {
        app.setNow("Backing up Pulsekit…");
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            String text;
            File zip = new File(app.getCacheDir(), "pulsekit-backup.zip");
            try {
                String name = AppBackup.zipName();
                text = AppBackup.backup(app.getFilesDir(), prefsDir(app), parts, password, zip, app.getCacheDir(), "Android " + app.appVersion());
                String where = ArtJava.publish(app, name, java.nio.file.Files.readAllBytes(zip.toPath()));
                text = where == null ? "Could not save " + name : text + ": saved " + where;
            } catch (Throwable ex) {
                text = "Could not back up Pulsekit: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                zip.delete();
            }
            final String shown = text;
            app.handler.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                said(app, shown);
            });
        }, "pulsekit-backup").start();
    }

    static void said(MainActivity app, String text) {
        lastSaid = text;
        app.setNow(text);
        android.widget.Toast.makeText(app, text, 1).show();
    }

    static void pickBackup(MainActivity app) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            app.startActivityForResult(intent, OPEN_BACKUP);
        } catch (Exception ex) {
            said(app, "No file picker on this phone: " + ex.getMessage());
        }
    }

    /** A backup picked (MainActivity.onActivityResult): copied in, then its parts to tick. */
    static void picked(final MainActivity app, Uri uri) {
        try {
            opened(app, DbSettings.copyIn(app, uri, "pulsekit-restore.zip"), DbImport.displayName(app, uri));
        } catch (Exception ex) {
            said(app, "Could not read the backup: " + ex.getMessage());
        }
    }

    /** A backup in Pulsekit's temp folder: its parts to tick and its password, then Restore. */
    static void opened(final MainActivity app, final File zip, final String name) {
        final Set<String> inside;
        final boolean locked;
        try {
            inside = AppBackup.partsIn(zip);
            locked = AppBackup.needsPassword(zip);
        } catch (Exception ex) {
            said(app, "Could not read " + name + ": " + ex.getMessage());
            return;
        }
        if (inside.isEmpty()) {
            said(app, name + " is not a Pulsekit backup");
            return;
        }
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(AppBackup.RESTORE_NOTE, 13, false));
        final java.util.List<CheckBox> boxes = new java.util.ArrayList<CheckBox>();
        for (String p : inside) {
            CheckBox b = new CheckBox(app);
            b.setText(AppBackup.label(p));
            b.setChecked(true);
            b.setTag("restore-part:" + p);
            b.setContentDescription(p);
            boxes.add(b);
            col.addView(b);
        }
        final EditText pw = locked ? ZipFlow.passwordField(app, "The backup's password", "restore-password") : null;
        if (pw != null) col.addView(pw);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        last = new AlertDialog.Builder(app).setTitle(AppBackup.RESTORE + ": " + name).setView(scroll)
            .setPositiveButton("Restore", (d, w) -> {
                Set<String> parts = new LinkedHashSet<String>();
                for (CheckBox b : boxes) if (b.isChecked()) parts.add(String.valueOf(b.getContentDescription()));
                restore(app, zip, pw == null ? null : pw.getText().toString().toCharArray(), parts);
            })
            .setNegativeButton("Cancel", (d, w) -> zip.delete()).show();
    }

    /** The parts restored; then Pulsekit closes (what it holds in memory would write over them). */
    static void restore(final MainActivity app, final File zip, final char[] password, final Set<String> parts) {
        app.setNow("Restoring Pulsekit…");
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            String text;
            boolean done = false;
            try {
                text = AppBackup.restore(zip, password, parts, app.getFilesDir(), prefsDir(app), app.getCacheDir());
                done = true;
            } catch (ZipPack.WrongPassword ex) {
                text = ZipPack.WRONG_PASSWORD;
            } catch (Throwable ex) {
                text = "Could not restore: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                zip.delete();
            }
            final String shown = text;
            final boolean close = done && !keepRunning;
            app.handler.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                said(app, shown);
                if (!close) return;
                last = new AlertDialog.Builder(app).setTitle(AppBackup.RESTORE).setMessage(shown + ". Pulsekit closes now: open it again.")
                    .setCancelable(false)
                    .setPositiveButton("Close Pulsekit", (d, w) -> {
                        // Ended at once, not finished: closing would save what Pulsekit holds in memory over the restored files.
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }).show();
            });
        }, "pulsekit-restore").start();
    }
}
