package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

/**
 * File > Import as Ref file / Import as Result file: a file picked with the system picker goes
 * into the prompt library on its own (not with a prompt), after OK, as a reference file (Ref files,
 * Browse DB) or a result file (Result files). Up to the library's 16 MB.
 */
final class DbImport {
    static final int PICK_REF = 35;
    static final int PICK_RESULT = 36;
    private static final long MAX_BYTES = 16L * 1024 * 1024;

    private DbImport() {}

    static void pick(MainActivity app, boolean result) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        app.startActivityForResult(intent, result ? PICK_RESULT : PICK_REF);
    }

    /** The picked file: asks to add it, then stores it. */
    static void take(final MainActivity app, Uri uri, final boolean result) {
        final String kind = result ? "result file" : "reference file";
        final String name = displayName(app, uri);
        final byte[] bytes;
        try {
            bytes = read(app, uri);
        } catch (Exception ex) {
            app.setNow("Could not read " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return;
        }
        if (bytes == null) {
            app.setNow(name + " is over the library's 16 MB");
            return;
        }
        new AlertDialog.Builder(app)
            .setTitle(result ? "Import as Result file" : "Import as Ref file")
            .setMessage("Add " + name + " (" + Math.max(1, bytes.length / 1024) + " KB) to the prompt library as a " + kind + "?\n\n"
                + (result ? "It shows in Result files on the Prompts page." : "It shows in Ref files on the Prompts page and in Browse DB."))
            .setPositiveButton("OK", (d, w) -> store(app, name, bytes, result))
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Adds the file; says what happened on the status line. */
    static void store(MainActivity app, String name, byte[] bytes, boolean result) {
        try {
            PromptVault.open(app).addLibraryFile(name, bytes, "Imported", result ? 3 : 1);
            app.setNow("Imported " + name + " as a " + (result ? "result file" : "reference file"));
        } catch (Exception ex) {
            app.setNow("Could not import " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
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

    /** The file's bytes, or null when it is over 16 MB. */
    static byte[] read(MainActivity app, Uri uri) throws Exception {
        java.io.InputStream in = app.getContentResolver().openInputStream(uri);
        if (in == null) throw new java.io.IOException("no data");
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) return null;
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
