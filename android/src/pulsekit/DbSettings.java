package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.List;

/**
 * File > Database settings (DbSlots): Use encrypted DB, how much room each DB takes, the DBs with
 * Use (and Delete for a secondary one), Send db to zip and Import db zip. The zip's password is
 * typed or read from a file; a DB encrypted with another computer's key asks for that key (typed,
 * or its key file). The work runs off the main thread.
 */
final class DbSettings {
    private DbSettings() {}

    static final int PASSWORD_FILE = 86;
    static final int SAVE_ZIP = 87;
    static final int OPEN_ZIP = 88;
    static final int KEY_FILE = 89;

    /** The dialog and the question shown last, and what the last DB work said (for the tests). */
    static AlertDialog last, lastAsk;
    static volatile String lastSaid;
    /** The DB picked in the list (Use, Delete). */
    static String picked;

    static void said(MainActivity app, String text) {
        lastSaid = text;
        app.setNow(text);
        android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_LONG).show();
    }

    static void show(final MainActivity app) {
        final File dir = app.getFilesDir();
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(12), app.dp(20), app.dp(4));
        // Use encrypted DB (moved here from General settings).
        final CheckBox box = new CheckBox(app);
        box.setText("Use encrypted DB");
        box.setTag("settings-encrypted");
        box.setChecked(PromptVault.encrypted(dir));
        box.setOnCheckedChangeListener((b, on) -> GeneralSettings.change(app, box, on));
        col.addView(box);
        TextView note = app.text("The DB in use is kept encrypted with the phone's keystore. Unticked, it is copied into an unencrypted DB "
            + "and used from then on: opening and saving are quicker. Ticked again, the encrypted DB is brought up to date and the unencrypted one is deleted.", 12, false);
        note.setPadding(0, app.dp(4), 0, 0);
        col.addView(note);
        // The room each DB takes, and all of them.
        col.addView(GeneralSettings.headline(app, "Space on disk"));
        TextView sizes = app.text(String.join("\n", DbSlots.sizeLines(dir)), 13, false);
        sizes.setTag("db-sizes");
        col.addView(sizes);
        // The DBs: pick one, then Use (or Delete a secondary one).
        col.addView(GeneralSettings.headline(app, "Databases"));
        final List<String> names = DbSlots.names(dir);
        picked = DbSlots.active(dir);
        final android.widget.RadioGroup group = new android.widget.RadioGroup(app);
        for (int i = 0; i < names.size(); i++) {
            android.widget.RadioButton r = new android.widget.RadioButton(app);
            final String n = names.get(i);
            r.setId(1000 + i);
            r.setText(n.equals(DbSlots.active(dir)) ? n + " (in use)" : n);
            r.setTag("db-pick:" + n);
            r.setChecked(n.equals(picked));
            r.setOnClickListener(v -> picked = n);
            group.addView(r);
        }
        col.addView(group);
        LinearLayout row = app.row();
        TextView use = app.pill(DbSlots.USE, true, v -> use(app, picked));
        use.setTag("db-use");
        TextView delete = app.pill(DbSlots.DELETE, false, v -> delete(app, picked));
        delete.setTag("db-delete");
        row.addView(use);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.leftMargin = app.dp(8);
        row.addView(delete, gap);
        col.addView(row);
        // Send db to zip, Import db zip.
        col.addView(GeneralSettings.headline(app, "Export and import"));
        TextView send = app.pill(DbSlots.SEND, false, v -> askExportPassword(app));
        send.setTag("db-send-zip");
        TextView imp = app.pill(DbSlots.IMPORT, false, v -> pickZip(app));
        imp.setTag("db-import-zip");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = app.dp(6);
        col.addView(send, lp);
        col.addView(imp, new LinearLayout.LayoutParams(lp));
        TextView how = app.text("Send db to zip: the DB in use, unencrypted, into a zip with a password (at least 4 characters). "
            + "Import db zip: a DB zip as a new secondary DB, or as the main DB; what it links to that is not on this phone is left out.", 12, false);
        how.setPadding(0, app.dp(6), 0, 0);
        col.addView(how);
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app).setTitle(DbSlots.TITLE).setView(scroll).setPositiveButton("Close", null).show();
    }

    /** The dialog again (sizes and the list as they are now). */
    static void again(MainActivity app) {
        if (last != null && last.isShowing()) show(app);
    }

    static void use(MainActivity app, String name) {
        if (name == null) return;
        try {
            DbSlots.use(app.getFilesDir(), name);
            PromptSheet.reopen(app);
            said(app, "Using the DB " + name);
        } catch (Exception ex) {
            said(app, "Could not use " + name + ": " + ex.getMessage());
        }
        again(app);
    }

    static void delete(final MainActivity app, final String name) {
        File dir = app.getFilesDir();
        if (name == null || DbSlots.MAIN.equals(name) || name.equals(DbSlots.active(dir))) {
            said(app, "Only a secondary DB that is not in use can be deleted");
            return;
        }
        lastAsk = new AlertDialog.Builder(app).setTitle("Delete DB").setMessage("Delete the DB " + name + " and all its files?")
            .setPositiveButton("Delete", (d, w) -> {
                said(app, DbSlots.delete(app.getFilesDir(), name) ? "Deleted the DB " + name : "Could not delete " + name);
                again(app);
            })
            .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------ Passwords and keys

    interface Got {
        void got(char[] password);
    }

    /** The password asked for next; the field it goes in when read from a file. */
    static Got pending;
    static EditText pendingField;

    /** A password: typed, or read from a text file (its first line); `min` characters at least. */
    static void askPassword(final MainActivity app, String title, String message, final int min, final Got got) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(message, 13, false));
        final EditText pw = ZipFlow.passwordField(app, "Password", "db-password");
        col.addView(pw);
        TextView fromFile = app.pill("Read from a file…", false, v -> {
            pendingField = pw;
            pick(app, Intent.ACTION_OPEN_DOCUMENT, "*/*", PASSWORD_FILE, null);
        });
        fromFile.setTag("db-password-file");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = app.dp(6);
        col.addView(fromFile, lp);
        lastAsk = new AlertDialog.Builder(app).setTitle(title).setView(col)
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null).show();
        lastAsk.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String typed = pw.getText().toString();
            if (typed.length() < min) {
                said(app, min > 0 ? DbSlots.SHORT_PASSWORD : "Type the password");
                return;
            }
            lastAsk.dismiss();
            got.got(typed.toCharArray());
        });
    }

    static void pick(MainActivity app, String action, String type, int code, String name) {
        Intent intent = new Intent(action);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        if (name != null) intent.putExtra(Intent.EXTRA_TITLE, name);
        try {
            app.startActivityForResult(intent, code);
        } catch (Exception ex) {
            said(app, "No file picker on this phone: " + ex.getMessage());
        }
    }

    /** The pickers' answers (MainActivity.onActivityResult). */
    static void picked(final MainActivity app, int code, Uri uri) {
        if (uri == null) return;
        if (code == PASSWORD_FILE || code == KEY_FILE) {
            try {
                File tmp = copyIn(app, uri, code == KEY_FILE ? "db-key.tmp" : "db-password.tmp");
                if (code == PASSWORD_FILE && pendingField != null) pendingField.setText(new String(DbSlots.passwordFrom(tmp)));
                if (code == KEY_FILE) keyFrom(app, null, tmp);
                tmp.delete();
            } catch (Exception ex) {
                said(app, "Could not read it: " + ex.getMessage());
            }
            return;
        }
        if (code == SAVE_ZIP) export(app, uri);
        if (code == OPEN_ZIP) importZip(app, uri);
    }

    static File copyIn(MainActivity app, Uri uri, String name) throws Exception {
        File tmp = new File(app.getCacheDir(), name);
        java.io.InputStream in = app.getContentResolver().openInputStream(uri);
        if (in == null) throw new java.io.IOException("no data");
        try {
            java.nio.file.Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            in.close();
        }
        return tmp;
    }

    // ------------------------------------------------------------ Send db to zip

    static char[] exportPassword;

    static void askExportPassword(final MainActivity app) {
        askPassword(app, DbSlots.SEND, DbSlots.PASSWORD_NOTE, DbSlots.MIN_PASSWORD, pw -> {
            exportPassword = pw;
            pick(app, Intent.ACTION_CREATE_DOCUMENT, "application/zip", SAVE_ZIP, DbSlots.zipName(app.getFilesDir()));
        });
    }

    /** The DB written into Pulsekit's temp folder as a zip, then copied where the picker said. */
    static void export(final MainActivity app, final Uri to) {
        final char[] pw = exportPassword;
        exportPassword = null;
        if (pw == null) return;
        app.setNow("Sending the DB to a zip…");
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            String text;
            File zip = new File(app.getCacheDir(), "db-send.zip");
            try {
                text = DbSlots.export(app.getFilesDir(), zip, pw, app.getCacheDir());
                java.io.OutputStream os = app.getContentResolver().openOutputStream(to);
                if (os == null) throw new java.io.IOException("could not write the zip there");
                try {
                    java.nio.file.Files.copy(zip.toPath(), os);
                } finally {
                    os.close();
                }
                // Said by the name it has where the picker put it.
                text = text.replace(" to " + zip.getName(), " to " + DbImport.displayName(app, to));
            } catch (Throwable ex) {
                text = "Could not send the DB to a zip: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                zip.delete();
            }
            final String shown = text;
            app.handler.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                said(app, shown);
            });
        }, "pulsekit-db-export").start();
    }

    // ------------------------------------------------------------ Import db zip

    static File importZip;
    static String importName;
    static DbSlots.Unpacked unpacked;

    static void pickZip(MainActivity app) {
        pick(app, Intent.ACTION_OPEN_DOCUMENT, "*/*", OPEN_ZIP, null);
    }

    /** The zip copied into Pulsekit's temp folder; its password asked when it has one. */
    static void importZip(final MainActivity app, final Uri uri) {
        try {
            importName = DbImport.displayName(app, uri);
            importZip = copyIn(app, uri, "db-import.zip");
            opened(app, importZip, importName);
        } catch (Exception ex) {
            said(app, "Could not read the zip: " + ex.getMessage());
        }
    }

    /** A DB zip in Pulsekit's temp folder: its password (when it has one), then it is unpacked. */
    static void opened(final MainActivity app, final File zip, final String name) {
        importZip = zip;
        importName = name;
        List<ZipPack.Entry> entries;
        try {
            entries = ZipPack.list(zip);
        } catch (Exception ex) {
            said(app, "Could not read " + name + ": " + ex.getMessage());
            return;
        }
        if (!ZipPack.encrypted(entries)) {
            unpack(app, null);
            return;
        }
        final List<ZipPack.Entry> es = entries;
        askPassword(app, name, ZipPack.ASK_PASSWORD, 1, pw -> {
            if (!ZipPack.passwordOk(zip, es, pw)) {
                said(app, ZipPack.WRONG_PASSWORD + " " + name);
                opened(app, zip, name);
                return;
            }
            unpack(app, pw);
        });
    }

    static void unpack(final MainActivity app, final char[] pw) {
        app.setNow("Unpacking " + importName + "…");
        new Thread(() -> {
            String failed = null;
            try {
                unpacked = DbSlots.unpack(importZip, importName, pw, app.getCacheDir());
            } catch (Throwable ex) {
                failed = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            final String why = failed;
            app.handler.post(() -> {
                if (why != null) {
                    said(app, "Could not import " + importName + ": " + why);
                    return;
                }
                if (unpacked.needsKey) askKey(app);
                else place(app);
            });
        }, "pulsekit-db-unpack").start();
    }

    /** Another computer's key: typed, or its key file. */
    static void askKey(final MainActivity app) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(DbSlots.KEY_NOTE, 13, false));
        final EditText hex = new EditText(app);
        hex.setSingleLine(true);
        hex.setHint("64 hexadecimal characters");
        hex.setTag("db-key");
        col.addView(hex);
        TextView file = app.pill("Key file…", false, v -> {
            if (lastAsk != null) lastAsk.dismiss();
            pick(app, Intent.ACTION_OPEN_DOCUMENT, "*/*", KEY_FILE, null);
        });
        file.setTag("db-key-file");
        col.addView(file);
        lastAsk = new AlertDialog.Builder(app).setTitle(importName).setView(col)
            .setPositiveButton("OK", (d, w) -> keyFrom(app, hex.getText().toString(), null))
            .setNegativeButton("Cancel", (d, w) -> said(app, "Not imported: " + importName)).show();
    }

    static void keyFrom(MainActivity app, String typed, File keyFile) {
        try {
            DbSlots.unlock(unpacked, DbSlots.key(typed, keyFile));
            place(app);
        } catch (Exception ex) {
            said(app, ex.getMessage() == null ? ex.toString() : ex.getMessage());
            askKey(app);
        }
    }

    /** Where the unpacked DB goes: the DB in use empty, asked whether it becomes the main DB; else secondary (first) or main. */
    static void place(final MainActivity app) {
        final File dir = app.getFilesDir();
        if (DbSlots.activeEmpty(dir)) {
            lastAsk = new AlertDialog.Builder(app).setTitle(DbSlots.AS_TITLE).setMessage(DbSlots.MAKE_MAIN)
                .setPositiveButton("Yes", (d, w) -> placeAs(app, true))
                .setNegativeButton("No", (d, w) -> placeAs(app, false)).show();
            return;
        }
        final int[] row = {0};
        lastAsk = new AlertDialog.Builder(app).setTitle(DbSlots.AS_TITLE)
            .setSingleChoiceItems(DbSlots.AS, 0, (d, w) -> row[0] = w)
            .setPositiveButton("OK", (d, w) -> placeAs(app, row[0] == 1))
            .setNegativeButton("Cancel", (d, w) -> said(app, "Not imported: " + importName)).show();
    }

    static void placeAs(final MainActivity app, final boolean main) {
        final File dir = app.getFilesDir();
        final boolean encrypt = PromptVault.encrypted(dir);
        app.setNow("Importing the DB…");
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            String text;
            try {
                int gone = DbSlots.prune(unpacked);
                String links = gone > 0 ? "; " + gone + (gone == 1 ? " link" : " links") + " to files that are not here left out" : "";
                if (main) {
                    String kept = DbSlots.makeMain(dir, unpacked, encrypt);
                    text = "The imported DB is the main DB now" + (kept.length() > 0 ? " (the one before is kept as " + kept + ")" : "") + links;
                } else {
                    String n = DbSlots.addSecondary(dir, unpacked, unpacked.name, encrypt);
                    text = "Imported the DB as " + n + " (Use picks it)" + links;
                }
            } catch (Throwable ex) {
                text = "Could not import the DB: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                if (importZip != null) importZip.delete();
            }
            final String shown = text;
            app.handler.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                PromptSheet.reopen(app);
                said(app, shown);
                again(app);
            });
        }, "pulsekit-db-import").start();
    }
}
