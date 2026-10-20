package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Send to zip (the Media browser's files and folders, the Ref / Result / Prompt files) and File >
 * Import of a zip (ZipImport): the questions, and the work off the main thread.
 */
final class ZipFlow {
    private ZipFlow() {}

    /** The folder picker for Extract into a folder. */
    static final int PICK_FOLDER = 84;
    /** The question shown last, and what the last zip work said (for the tests). */
    static AlertDialog last;
    static volatile String lastSaid;

    interface Go {
        /** The password (null: none). */
        void go(char[] password);
    }

    /** Send to zip's question: a password, typed twice (recommended), or none. */
    static void askPassword(final MainActivity app, String what, final Go go) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(ZipPack.PASSWORD_NOTE, 13, false));
        final EditText pw = passwordField(app, "Password", "zip-password");
        final EditText again = passwordField(app, "The password again", "zip-password-again");
        col.addView(pw);
        col.addView(again);
        last = new AlertDialog.Builder(app).setTitle(ZipPack.PASSWORD_TITLE + " · " + what).setView(col)
            .setPositiveButton(ZipPack.WITH_PASSWORD, null)
            .setNeutralButton(ZipPack.NO_PASSWORD, (d, w) -> go.go(null))
            .setNegativeButton("Cancel", null).show();
        last.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = pw.getText().toString();
            if (a.length() == 0) {
                app.setNow("Type a password, or press " + ZipPack.NO_PASSWORD);
                return;
            }
            if (!a.equals(again.getText().toString())) {
                app.setNow("The passwords are not the same");
                return;
            }
            last.dismiss();
            go.go(a.toCharArray());
        });
    }

    static EditText passwordField(MainActivity app, String hint, String tag) {
        EditText f = new EditText(app);
        f.setSingleLine(true);
        f.setHint(hint);
        f.setTag(tag);
        f.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return f;
    }

    /** What a zip's work said: on the status line, as a toast, and kept for the tests. */
    static void said(MainActivity app, String text) {
        lastSaid = text;
        app.setNow(text);
        android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ Send to zip: a library file

    /** A Ref, Result or Prompt file zipped (with a password when one is given) into Download. */
    static void sendLibraryFile(final Activity activity, final PromptVault vault, final long versionId, final int which, final String name) {
        if (!(activity instanceof MainActivity) || vault == null) return;
        final MainActivity app = (MainActivity) activity;
        askPassword(app, name, password -> {
            app.setNow("Zipping " + name + "…");
            new Thread(() -> {
                String text;
                File tmp = new File(app.getCacheDir(), "send-to-zip.zip");
                try {
                    String title = PromptVault.fileTitle(name);
                    File linked = vault.linkedFile(versionId, which);
                    ZipPack.Source s = linked != null && linked.isFile() ? new ZipPack.Source(title, linked) : new ZipPack.Source(title, vault.fileBytes(versionId, which));
                    ZipPack.write(tmp, java.util.Collections.singletonList(s), password);
                    String where = ArtJava.publish(app, ZipPack.zipName(title, false), java.nio.file.Files.readAllBytes(tmp.toPath()));
                    text = where == null ? "Could not save the zip of " + name : "Zipped " + name + (password != null ? " with a password" : "") + " into " + where;
                } catch (Throwable ex) {
                    text = "Could not zip " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                } finally {
                    tmp.delete();
                }
                final String shown = text;
                app.runOnUiThread(() -> said(app, shown));
            }, "pulsekit-send-zip").start();
        });
    }

    // ------------------------------------------------------------ File > Import: a zip

    /** The zip being imported, its files, its password and where it goes (one at a time). */
    static File zip;
    static String zipName;
    static List<ZipPack.Entry> entries;
    static char[] password;
    static int which = 1;
    static boolean alsoDb;

    /** File > Import picked a zip: copied into Pulsekit's temp folder, then its password (when it has one) and Extract the zip?. */
    static void importZip(final MainActivity app, final Uri uri, final String name) {
        app.setNow("Reading " + name + "…");
        new Thread(() -> {
            String failed = null;
            File tmp = new File(app.getCacheDir(), "zip-import.zip");
            List<ZipPack.Entry> list = null;
            try {
                java.io.InputStream in = app.getContentResolver().openInputStream(uri);
                if (in == null) throw new java.io.IOException("no data");
                try {
                    java.nio.file.Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    in.close();
                }
                list = ZipPack.list(tmp);
            } catch (Throwable ex) {
                failed = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            final String why = failed;
            final List<ZipPack.Entry> got = list;
            app.runOnUiThread(() -> {
                if (why != null) {
                    said(app, "Could not read " + name + ": " + why);
                    return;
                }
                opened(app, tmp, name, got);
            });
        }, "pulsekit-zip-read").start();
    }

    /** The zip read: its password asked when it has one, then Extract the zip?. */
    static void opened(MainActivity app, File file, String name, List<ZipPack.Entry> list) {
        zip = file;
        zipName = name;
        entries = list;
        password = null;
        which = 1;
        alsoDb = false;
        if (ZipPack.encrypted(list)) askZipPassword(app);
        else askExtract(app);
    }

    static void askZipPassword(final MainActivity app) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(ZipPack.ASK_PASSWORD, 13, false));
        final EditText pw = passwordField(app, "Password", "zip-open-password");
        col.addView(pw);
        last = new AlertDialog.Builder(app).setTitle(zipName).setView(col)
            .setPositiveButton("Open", (d, w) -> {
                char[] typed = pw.getText().toString().toCharArray();
                if (!ZipPack.passwordOk(zip, entries, typed)) {
                    said(app, ZipPack.WRONG_PASSWORD + " " + zipName);
                    askZipPassword(app);
                    return;
                }
                password = typed;
                askExtract(app);
            })
            .setNegativeButton("Cancel", (d, w) -> app.setNow("Not imported: " + zipName)).show();
    }

    static void askExtract(final MainActivity app) {
        last = new AlertDialog.Builder(app).setTitle(ZipImport.EXTRACT_TITLE).setMessage(ZipImport.extractQuestion(zipName, entries))
            .setPositiveButton("Yes", (d, w) -> askWhere(app))
            .setNeutralButton("No", (d, w) -> keepZip(app))
            .setNegativeButton("Cancel", (d, w) -> app.setNow("Not imported: " + zipName)).show();
    }

    static void askWhere(final MainActivity app) {
        last = new AlertDialog.Builder(app).setTitle(ZipImport.WHERE_TITLE).setItems(ZipImport.WHERE, (d, w) -> {
            if (w == ZipImport.FOLDER) {
                alsoDb = false;
                pickFolder(app);
            } else askGallery(app, w == ZipImport.BOTH);
        }).setNegativeButton("Cancel", (d, w) -> app.setNow("Not imported: " + zipName)).show();
    }

    /** Into the DB: Ref files (picked first) or Result files; with `folderToo`, then the folder. */
    static void askGallery(final MainActivity app, final boolean folderToo) {
        final int[] row = {0};
        last = new AlertDialog.Builder(app).setTitle(ZipImport.GALLERY_TITLE)
            .setSingleChoiceItems(ZipImport.GALLERY, 0, (d, w) -> row[0] = w)
            .setPositiveButton("OK", (d, w) -> {
                which = ZipImport.which(row[0]);
                if (folderToo) {
                    alsoDb = true;
                    pickFolder(app);
                } else intoDb(app, null);
            })
            .setNegativeButton("Cancel", (d, w) -> app.setNow("Not imported: " + zipName)).show();
    }

    static void pickFolder(MainActivity app) {
        try {
            app.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
        } catch (Exception ex) {
            said(app, "No folder picker on this phone: " + ex.getMessage());
        }
    }

    /** The folder picked: the files extracted into it (and into the DB too, when that was asked). */
    static void folderPicked(final MainActivity app, final Uri tree) {
        if (zip == null || tree == null) return;
        final boolean db = alsoDb;
        app.setNow("Extracting " + zipName + "…");
        new Thread(() -> {
            String text;
            try {
                text = "Extracted " + intoTree(app, tree);
                if (db) text += "; " + ZipImport.intoDb(PromptVault.open(app.getFilesDir()), zip, entries, password, which, app.getCacheDir());
            } catch (Throwable ex) {
                text = "Could not extract " + zipName + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final String shown = text;
            app.runOnUiThread(() -> {
                said(app, shown);
                if (db) PromptSheet.openFiles(app, which == 3, null);
            });
        }, "pulsekit-zip-extract").start();
    }

    /** The zip's files written into the granted folder, with its folders; what was done. */
    static String intoTree(MainActivity app, Uri tree) throws Exception {
        String root = DocumentsContract.getTreeDocumentId(tree);
        Map<String, String> folders = new HashMap<String, String>();
        int done = 0;
        int failed = 0;
        for (ZipPack.Entry e : ZipPack.files(entries)) {
            String[] parts = ZipPack.safePath(e.name).split("/");
            String parent = root;
            String key = "";
            for (int k = 0; k < parts.length - 1 && parent != null; k++) {
                key = key + "/" + parts[k];
                String known = folders.get(key);
                if (known == null) {
                    known = folderIn(app, tree, parent, parts[k]);
                    if (known != null) folders.put(key, known);
                }
                parent = known;
            }
            if (parent == null) {
                failed++;
                continue;
            }
            try {
                String name = parts[parts.length - 1];
                Uri out = DocumentsContract.createDocument(app.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(tree, parent), ArtJava.mimeOf(name), name);
                if (out == null) throw new java.io.IOException("could not write " + name);
                java.io.OutputStream os = app.getContentResolver().openOutputStream(out);
                try {
                    ZipPack.extract(zip, e, password, os);
                } finally {
                    os.close();
                }
                done++;
            } catch (ZipPack.WrongPassword ex) {
                throw ex;
            } catch (Exception ex) {
                failed++;
            }
        }
        return done + (done == 1 ? " file" : " files") + (failed > 0 ? " (" + failed + " could not be extracted)" : "");
    }

    /** The folder `name` in the granted folder's `parent` (made when it is not there): its id, or null. */
    static String folderIn(MainActivity app, Uri tree, String parent, String name) {
        android.database.Cursor c = null;
        try {
            c = app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent),
                new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            while (c != null && c.moveToNext()) {
                if (name.equals(c.getString(1)) && DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) return c.getString(0);
            }
        } catch (Exception ignored) {
            // made below
        } finally {
            if (c != null) c.close();
        }
        try {
            Uri made = DocumentsContract.createDocument(app.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(tree, parent), DocumentsContract.Document.MIME_TYPE_DIR, name);
            return made == null ? null : DocumentsContract.getDocumentId(made);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Into the DB only (no folder). */
    static void intoDb(final MainActivity app, final Runnable after) {
        app.setNow("Importing " + zipName + " into " + ZipImport.galleryName(which) + "…");
        new Thread(() -> {
            String text;
            try {
                text = "Imported " + ZipImport.intoDb(PromptVault.open(app.getFilesDir()), zip, entries, password, which, app.getCacheDir());
            } catch (Throwable ex) {
                text = "Could not import " + zipName + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final String shown = text;
            app.runOnUiThread(() -> {
                said(app, shown);
                PromptSheet.openFiles(app, which == 3, null);
                if (after != null) after.run();
            });
        }, "pulsekit-zip-db").start();
    }

    /** Not extracted: the zip itself into the DB (Ref files); one of 20 MB or more linked after asking, or not imported. */
    static void keepZip(final MainActivity app) {
        if (zip.length() >= PromptVault.LINK_MIN) {
            last = new AlertDialog.Builder(app).setTitle(zipName).setMessage(ZipImport.tooBig(zipName, zip.length()))
                .setPositiveButton(ZipImport.LINK, (d, w) -> storeZip(app, true))
                .setNegativeButton(ZipImport.ABORT, (d, w) -> said(app, "Not imported: " + zipName)).show();
            return;
        }
        storeZip(app, false);
    }

    static void storeZip(final MainActivity app, final boolean link) {
        new Thread(() -> {
            String text;
            try {
                text = ZipImport.zipIntoDb(PromptVault.open(app.getFilesDir()), zip, zipName, link);
            } catch (Throwable ex) {
                text = "Could not import " + zipName + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final String shown = text;
            app.runOnUiThread(() -> {
                said(app, shown);
                PromptSheet.openFiles(app, false, null);
            });
        }, "pulsekit-zip-keep").start();
    }
}
