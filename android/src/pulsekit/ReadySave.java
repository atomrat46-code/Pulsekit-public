package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;

/**
 * The ready screens' saves (Picture, Video, MP3 and WAV ready): Save as a Reference file, Save as a
 * Result file (into the prompt library, then which group it goes in) and Save into disk… (Android's
 * save dialog, opened where the last file was saved into).
 */
final class ReadySave {
    private ReadySave() {}

    // Not 97: Lv2Host.IMPORT_LV2 has it, and Save into disk's answer went to the LV2 import ("Not imported...").
    static final int SAVE_DISK = 102;
    static final String PREFS = "pulsekit-ready-save";
    /** The file saved into disk last: the save dialog opens beside it. */
    static final String LAST = "last";

    /** What waits for the save dialog: the name and the bytes, or the file to copy. */
    private static String pendingName;
    private static byte[] pendingBytes;
    private static File pendingFile;
    /** What a save said last (for the tests). */
    static String lastSaid;

    /**
     * The three as one short row (Reference, Result, Save to file) under the file's name: Picture and
     * Video ready keep the picture and the player's controls in view. `bytes` or `file` is the media.
     */
    static android.view.View row(final MainActivity app, final String name, final byte[] bytes, final File file) {
        LinearLayout row = app.row();
        row.setTag("ready-save:" + name);
        for (int i = 0; i < MediaDir.READY_SAVES.length; i++) {
            final String item = MediaDir.READY_SAVES[i];
            TextView b = app.outline(MediaDir.READY_SAVES_SHORT[i], false, v -> act(app, item, name, bytes, file));
            b.setTag("ready-save:" + item + ":" + name);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = app.dp(6);
            row.addView(b, lp);
        }
        android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(app);
        strip.setTag("ready-saves:" + name);
        strip.addView(row);
        strip.setPadding(0, app.dp(2), 0, app.dp(6));
        return strip;
    }

    static void act(MainActivity app, String item, String name, byte[] bytes, File file) {
        if (MediaDir.SAVE_DISK.equals(item)) toDisk(app, name, bytes, file);
        else toDb(app, name, bytes, file, MediaDir.SAVE_RESULT.equals(item));
    }

    /** Into the prompt library as a reference or result file, then which group it goes in. */
    static void toDb(MainActivity app, String name, byte[] bytes, File file, boolean result) {
        byte[] data = bytes;
        long size = bytes != null ? bytes.length : file != null ? file.length() : 0;
        try {
            if (data == null && file != null && !MediaDir.tooBig(file.length())) data = java.nio.file.Files.readAllBytes(file.toPath());
        } catch (Exception ignored) {
            // said below
        }
        String said = data == null && MediaDir.tooBig(size) ? name + " is over the library's 128 MB, so it is not in the DB" : MediaDir.addToDb(app.getFilesDir(), name, data, result);
        lastSaid = said;
        app.setNow(said);
        if (said.contains(" is in the DB as a ")) GroupDialogs.afterAdd(app, name, size);
    }

    /** Android's save dialog for the file, opened where the last one was saved. */
    static void toDisk(MainActivity app, String name, byte[] bytes, File file) {
        pendingName = name;
        pendingBytes = bytes;
        pendingFile = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type(name));
        intent.putExtra(Intent.EXTRA_TITLE, name);
        String last = app.getSharedPreferences(PREFS, 0).getString(LAST, null);
        if (last != null && android.os.Build.VERSION.SDK_INT >= 26) intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(last));
        try {
            app.startActivityForResult(intent, SAVE_DISK);
        } catch (Exception ex) {
            app.setNow("No save dialog on this phone");
        }
    }

    static String type(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".wav") || n.endsWith(".wave")) return "audio/wav";
        return "application/octet-stream";
    }

    /** The place the save dialog returned: the file written there, and the place kept for next time. */
    static void write(MainActivity app, Uri uri) {
        String name = pendingName;
        byte[] bytes = pendingBytes;
        File file = pendingFile;
        pendingName = null;
        pendingBytes = null;
        pendingFile = null;
        if (name == null) return;
        String said;
        try {
            java.io.OutputStream out = app.getContentResolver().openOutputStream(uri);
            if (out == null) throw new java.io.IOException("no output");
            try {
                if (bytes != null) out.write(bytes);
                else java.nio.file.Files.copy(file.toPath(), out);
            } finally {
                out.close();
            }
            app.getSharedPreferences(PREFS, 0).edit().putString(LAST, uri.toString()).apply();
            said = "Saved " + name;
        } catch (Exception ex) {
            said = "Could not save " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
        lastSaid = said;
        app.setNow(said);
    }
}
