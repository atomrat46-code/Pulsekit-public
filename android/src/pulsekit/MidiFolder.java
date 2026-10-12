package pulsekit;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A folder picked with the system's picker (a content:// tree) given to a program that reads a
 * folder of MIDI files (Midi2HydroPattern's --dir): its .mid and .midi files, and its subfolders'
 * with --recursive, are copied into PyJav's work folder first, as the program cannot read the
 * picker's folder itself. The program's output goes to the work folder, which PyJav saves to
 * Download.
 */
final class MidiFolder {
    private MidiFolder() {}

    /** Copies of the folder's MIDI files under dest (subfolders kept); how many. */
    static int copy(Context ctx, Uri tree, File dest, boolean recursive) {
        String doc = DocumentsContract.getTreeDocumentId(tree);
        return copy(ctx, tree, doc, dest, recursive, 0);
    }

    private static int copy(Context ctx, Uri tree, String doc, File dest, boolean recursive, int depth) {
        int n = 0;
        List<String[]> kids = new ArrayList<String[]>();
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc),
                new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            while (c != null && c.moveToNext()) kids.add(new String[] {c.getString(0), c.getString(1), c.getString(2)});
        } catch (Exception ex) {
            return 0;
        } finally {
            if (c != null) c.close();
        }
        for (String[] k : kids) {
            String name = k[1] == null ? "" : k[1];
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(k[2])) {
                if (recursive && depth < 8 && !name.startsWith(".")) n += copy(ctx, tree, k[0], new File(dest, safe(name)), true, depth + 1);
                continue;
            }
            String low = name.toLowerCase(Locale.ROOT);
            if (!low.endsWith(".mid") && !low.endsWith(".midi")) continue;
            try {
                dest.mkdirs();
                InputStream in = ctx.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, k[0]));
                if (in == null) continue;
                FileOutputStream out = new FileOutputStream(new File(dest, safe(name)));
                try {
                    byte[] b = new byte[8192];
                    for (int r; (r = in.read(b)) > 0; ) out.write(b, 0, r);
                } finally {
                    out.close();
                    in.close();
                }
                n++;
            } catch (Exception ignored) {
                // left out; the others are converted
            }
        }
        return n;
    }

    private static String safe(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.length() == 0 ? "_" : s;
    }

    /**
     * Midi2HydroPattern's arguments with a picked folder (--dir content://...) as a copy in the
     * folder PyJav keeps for inputs, and its output in the run's work folder (a picked --outdir
     * cannot be written by the program).
     */
    static List<String> forProgram(Context ctx, String program, List<String> argv, File work, StringBuilder note) {
        if (!"Midi2HydroPattern.java".equals(program) || argv == null) return argv;
        List<String> out = new ArrayList<String>(argv);
        boolean recursive = out.contains("--recursive");
        boolean staged = false;
        for (int i = 0; i + 1 < out.size(); i++) {
            String a = out.get(i), v = out.get(i + 1);
            if (a.equals("--dir") && v.startsWith("content://")) {
                Uri tree = Uri.parse(v);
                File dest = new File(work, "midi-folder");
                deleteAll(dest);
                int n = copy(ctx, tree, dest, recursive);
                note.append(n).append(" MIDI file").append(n == 1 ? "" : "s").append(" from ").append(MediaDir.label(v));
                out.set(i + 1, dest.getAbsolutePath());
                staged = true;
            }
        }
        // Output: the work folder (saved to Download), in place of a picked folder.
        for (int i = 0; i + 1 < out.size(); i++) {
            if (out.get(i).equals("--outdir") && (out.get(i + 1).startsWith("content://") || staged)) {
                out.remove(i + 1);
                out.remove(i);
                i--;
            }
        }
        // "." is the run's own work folder (user.dir while it runs), whose new files PyJav saves.
        if (staged) {
            out.add("--outdir");
            out.add(".");
        }
        return out;
    }

    private static void deleteAll(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteAll(k);
        f.delete();
    }
}
