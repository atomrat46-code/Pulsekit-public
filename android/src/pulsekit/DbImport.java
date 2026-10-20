package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

/**
 * File > Import as Ref file / Import as Result file: a file picked with the system picker goes
 * into the prompt library on its own (not with a prompt), after OK, as a reference file (Ref files,
 * Browse DB) or a result file (Result files). One of 20 MB or more goes into separate storage
 * (PromptVault.addLinkedFile), after asking.
 */
final class DbImport {
    static final int PICK_REF = 35;
    static final int PICK_RESULT = 36;

    private DbImport() {}

    static void pick(MainActivity app, boolean result) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        app.startActivityForResult(intent, result ? PICK_RESULT : PICK_REF);
    }

    /** The picked file: asks to add it, then stores it (one of 20 MB or more into separate storage, after asking). */
    static void take(final MainActivity app, final Uri uri, final boolean result) {
        final String kind = result ? "result file" : "reference file";
        final String name = displayName(app, uri);
        long size = sizeOf(app, uri);
        if (size >= PromptVault.LINK_MIN) {
            askLinked(app, uri, name, size, result);
            return;
        }
        final byte[] bytes;
        try {
            bytes = read(app, uri);
        } catch (Exception ex) {
            app.setNow("Could not read " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return;
        }
        if (bytes == null) {
            // Larger than it said (or it did not say): separate storage.
            askLinked(app, uri, name, -1, result);
            return;
        }
        last = new AlertDialog.Builder(app)
            .setTitle(result ? "Import as Result file" : "Import as Ref file")
            .setMessage("Add " + name + " (" + Math.max(1, bytes.length / 1024) + " KB) to the prompt library as a " + kind + "?\n\n"
                + (result ? "It shows in Result files on the Prompts page." : "It shows in Ref files on the Prompts page and in Browse DB."))
            .setPositiveButton("OK", (d, w) -> {
                if (store(app, name, bytes, result)) imported(app, name, bytes.length, result, false);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** The import's question shown last (for the tests). */
    static AlertDialog last;

    /** A file of 20 MB or more: asked first; then copied into separate storage off the main thread. */
    static void askLinked(final MainActivity app, final Uri uri, final String name, long size, final boolean result) {
        last = new AlertDialog.Builder(app)
            .setTitle(result ? "Import as Result file" : "Import as Ref file")
            .setMessage(DbFilter.linkQuestion(name, size))
            .setPositiveButton("Import", (d, w) -> {
                app.setNow("Importing " + name + " into separate storage\u2026");
                new Thread(() -> {
                    String failed = null;
                    long kept = 0;
                    try {
                        java.io.InputStream in = app.getContentResolver().openInputStream(uri);
                        if (in == null) throw new java.io.IOException("no data");
                        try {
                            PromptVault vault = PromptVault.open(app.getFilesDir());
                            long id = vault.addLinkedFile(name, in, "Imported", result ? 3 : 1);
                            java.io.File at = vault.linkedFile(id, result ? 3 : 1);
                            kept = at == null ? 0 : at.length();
                        } finally {
                            in.close();
                        }
                    } catch (Throwable ex) {
                        failed = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                    }
                    final String why = failed;
                    final long bytes = kept;
                    app.runOnUiThread(() -> {
                        if (why != null) app.setNow("Could not import " + name + ": " + why);
                        else imported(app, name, bytes, result, true);
                    });
                }, "pulsekit-import-linked").start();
            })
            .setNegativeButton("Cancel", (d, w) -> app.setNow("Not imported: " + name))
            .show();
    }

    /**
     * Imported: said, the Prompts page's Ref files (or Result files) opened on it (S for a sound or
     * MIDI file), then which group it goes in.
     */
    static void imported(MainActivity app, String name, long size, boolean result, boolean linked) {
        String said = "Imported " + name + " as a " + (result ? "result file" : "reference file") + (linked ? " (in separate storage)" : "");
        PromptSheet.openFiles(app, result, DbFilter.afterImport(name));
        app.setNow(said);
        android.widget.Toast.makeText(app, said, android.widget.Toast.LENGTH_LONG).show();
        GroupDialogs.afterAdd(app, name, size);
    }

    /** Adds the file; says what happened on the status line. */
    static boolean store(MainActivity app, String name, byte[] bytes, boolean result) {
        try {
            PromptVault.open(app.getFilesDir()).addLibraryFile(name, bytes, "Imported", result ? 3 : 1);
            app.setNow("Imported " + name + " as a " + (result ? "result file" : "reference file"));
            return true;
        } catch (Exception ex) {
            app.setNow("Could not import " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return false;
        }
    }

    /** The picked file's size as the picker gives it; -1 when it does not. */
    static long sizeOf(MainActivity app, Uri uri) {
        try {
            android.database.Cursor c = app.getContentResolver().query(uri, new String[] {android.provider.OpenableColumns.SIZE}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
            // not known
        }
        if ("file".equals(uri.getScheme()) && uri.getPath() != null) return new java.io.File(uri.getPath()).length();
        return -1;
    }

    static String displayName(MainActivity app, Uri uri) {
        String name = null;
        try {
            android.database.Cursor c = app.getContentResolver().query(uri, new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) name = c.getString(0);
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
            // Named from the address below.
        }
        if (name == null || name.trim().length() == 0) {
            String path = uri.getLastPathSegment();
            name = path == null ? "file" : path.substring(path.lastIndexOf('/') + 1);
        }
        return name.trim();
    }

    /** The file's bytes, or null when it is 20 MB or more (separate storage). */
    static byte[] read(MainActivity app, Uri uri) throws Exception {
        java.io.InputStream in = app.getContentResolver().openInputStream(uri);
        if (in == null) throw new java.io.IOException("no data");
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() >= PromptVault.LINK_MIN) return null;
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
