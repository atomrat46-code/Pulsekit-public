package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.List;

/**
 * File > Drum sound library (DrumLibrary) on the phone: its parts (with how many sounds and how many
 * are ready), a part's sounds with their measures (sorted as asked), each with ▶, Part…, Again and ✕;
 * Analyze waiting. A pad's long-press menu adds sounds: a sound file, a folder (with its subfolders or
 * not; the system's folder picker) or the prompt library's sound files. Links only: a document's
 * address is kept readable, nothing is copied (but for a moment, to measure it).
 */
final class DrumLibraryScreen {
    private DrumLibraryScreen() {}

    static final int PICK_FILE = 141, PICK_FOLDER = 142, PICK_TREE = 143;

    /** The screen shown last, what was said last, the analysis running (for the tests). */
    static AlertDialog last;
    static volatile String lastSaid = "";
    static volatile Thread analyzing;
    static Runnable refill;
    /** The part a picker was opened for. */
    static String pendingPart = "other";

    static File work(MainActivity app) {
        return new File(app.getCacheDir(), "drumlibrary");
    }

    static void open(MainActivity app) {
        DrumLibrary.open(app.getFilesDir());
    }

    /** A pad's long-press item: where the sounds come from. */
    static void addMenu(final MainActivity app, final int pad) {
        open(app);
        final String part = DrumLibrary.partOfPad(pad);
        final String[] kinds = {DrumLibrary.ADD_FILE, DrumLibrary.ADD_FOLDER, DrumLibrary.ADD_TREE, DrumLibrary.ADD_DB};
        new AlertDialog.Builder(app).setTitle(DrumLibrary.ADD + " " + DrumLibrary.label(part)).setItems(kinds, (d, w) -> addFrom(app, part, kinds[w]))
            .setNegativeButton("Cancel", null).show();
    }

    static void addFrom(final MainActivity app, final String part, String kind) {
        pendingPart = part;
        if (kind.equals(DrumLibrary.ADD_DB)) {
            RefBrowser.browse(app, RefBrowser.SOUNDS, (name, file) -> {
                DrumLibrary.Entry e = DrumLibrary.add(DrumLibrary.DB + name, name, part, "db");
                DrumLibrary.save();
                said(app, e == null ? "Already in the library: " + name : "Added " + name + " to " + DrumLibrary.label(e.part));
                analyze(app);
            });
            return;
        }
        Intent intent;
        int code;
        if (kind.equals(DrumLibrary.ADD_FILE)) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            code = PICK_FILE;
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            code = kind.equals(DrumLibrary.ADD_TREE) ? PICK_TREE : PICK_FOLDER;
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        app.startActivityForResult(intent, code);
    }

    /** A picker's answer (MainActivity.onActivityResult): the file or the folder's sound files added. */
    static void picked(final MainActivity app, int code, Uri uri) {
        open(app);
        try {
            app.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // readable for this session still
        }
        final String part = pendingPart;
        if (code == PICK_FILE) {
            String name = DbImport.displayName(app, uri);
            DrumLibrary.Entry e = DrumLibrary.add(uri.toString(), name == null ? uri.getLastPathSegment() : name, part, "file");
            DrumLibrary.save();
            said(app, e == null ? "Already in the library: " + name : "Added " + e.name + " to " + DrumLibrary.label(e.part));
            analyze(app);
            return;
        }
        final boolean tree = code == PICK_TREE;
        said(app, "Looking for sounds in the folder…");
        new Thread(() -> {
            List<String[]> found = new java.util.ArrayList<String[]>();
            try {
                walk(app, uri, android.provider.DocumentsContract.getTreeDocumentId(uri), tree, 0, found);
            } catch (Exception ex) {
                app.handler.post(() -> said(app, "Could not read the folder: " + ex.getMessage()));
                return;
            }
            String s;
            if (found.isEmpty()) s = "No WAV or MP3 in that folder";
            else {
                int added = 0, byName = 0;
                for (String[] f : found) {
                    DrumLibrary.Entry e = DrumLibrary.add(f[1], f[0], part, "folder");
                    if (e == null) continue;
                    added++;
                    if (e.byName) byName++;
                }
                DrumLibrary.save();
                s = added == 0 ? "Already in the library: " + found.size() + " sounds"
                    : "Added " + added + " sound" + (added == 1 ? "" : "s") + " to " + DrumLibrary.label(part) + (byName > 0 ? " (" + byName + " to other parts by their names)" : "")
                        + (found.size() > added ? "; " + (found.size() - added) + " already there" : "");
            }
            final String shown = s;
            app.handler.post(() -> {
                said(app, shown);
                analyze(app);
            });
        }, "pulsekit-library-folder").start();
    }

    /** A folder's sound files {name, document address}, its subfolders' too when `tree`. */
    static void walk(MainActivity app, Uri tree, String doc, boolean deep, int depth, List<String[]> out) {
        android.database.Cursor c = app.getContentResolver().query(android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc),
            new String[] {android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) return;
        List<String> sub = new java.util.ArrayList<String>();
        try {
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (name == null) continue;
                if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (deep && depth < 8 && !name.startsWith(".")) sub.add(id);
                } else if (name.toLowerCase(java.util.Locale.ROOT).matches(DrumLibrary.SOUND_FILES)) {
                    out.add(new String[] {name, android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()});
                }
            }
        } finally {
            c.close();
        }
        for (String id : sub) walk(app, tree, id, true, depth + 1, out);
    }

    static void said(MainActivity app, String s) {
        lastSaid = s;
        app.setNow(s);
        if (refill != null && last != null && last.isShowing()) refill.run();
    }

    /** The phone's way to the sounds: a file as it is, a document or DB file copied into `work` for a moment. */
    static DrumLibrary.Access access(final MainActivity app) {
        return new DrumLibrary.Access() {
            public File local(DrumLibrary.Entry e, File work) throws Exception {
                String safe = e.name.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (e.link.startsWith(DrumLibrary.DB)) {
                    String name = e.link.substring(DrumLibrary.DB.length());
                    PromptVault vault = PromptVault.open(app.getFilesDir());
                    for (PromptVault.StoredFile f : RefBrowser.allFiles(app, RefBrowser.SOUNDS)) {
                        if (!f.name.equals(name)) continue;
                        File copy = new File(work, System.nanoTime() + "-" + safe);
                        AudioMix.write(copy, vault.fileBytes(f.versionId, f.which));
                        return copy;
                    }
                    throw new java.io.FileNotFoundException("not in the DB any more");
                }
                if (e.link.startsWith("content:")) {
                    java.io.InputStream in;
                    try {
                        in = app.getContentResolver().openInputStream(Uri.parse(e.link));
                    } catch (Exception ex) {
                        throw new java.io.FileNotFoundException("the file is gone, or no longer readable");
                    }
                    if (in == null) throw new java.io.FileNotFoundException("the file is gone");
                    File copy = new File(work, System.nanoTime() + "-" + safe);
                    java.io.FileOutputStream out = new java.io.FileOutputStream(copy);
                    try {
                        byte[] buf = new byte[1 << 16];
                        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    } finally {
                        out.close();
                        in.close();
                    }
                    return copy;
                }
                File f = new File(e.link);
                if (!f.isFile()) throw new java.io.FileNotFoundException("the file is gone");
                return f;
            }

            public byte[] jar() {
                try {
                    return app.programMenus.read("Java", "DrumMidi_CRT.jar");
                } catch (Exception ex) {
                    return null;
                }
            }
        };
    }

    /** The waiting sounds measured, off the main thread (one analysis at a time). */
    static synchronized void analyze(final MainActivity app) {
        if (analyzing != null && analyzing.isAlive()) return;
        final List<DrumLibrary.Entry> todo = DrumLibrary.waiting();
        if (todo.isEmpty()) return;
        analyzing = new Thread(() -> {
            String s = DrumLibrary.analyze(todo, access(app), work(app), done -> app.handler.post(() -> said(app, "Analyzing drum sounds: " + done + " of " + todo.size())));
            app.handler.post(() -> {
                said(app, s);
                if (!DrumLibrary.waiting().isEmpty()) analyze(app);
            });
        }, "pulsekit-drum-library");
        analyzing.start();
    }

    static void show(final MainActivity app) {
        open(app);
        if (last != null && last.isShowing()) last.dismiss();
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        final android.widget.Spinner part = new android.widget.Spinner(app);
        part.setTag("library-part");
        final android.widget.Spinner sort = DrumPadScreen.spinner(app, DrumLibrary.SORTS);
        sort.setTag("library-sort");
        final TextView status = app.text(" ", 13, false);
        status.setTag("library-status");
        final LinearLayout rows = app.col();
        rows.setTag("library-rows");
        final int[] chosen = {0};
        final boolean[] filling = {false};
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            filling[0] = true;
            String[] labels = new String[DrumLibrary.PARTS.length + 1];
            labels[0] = DrumLibrary.partLine(null);
            for (int i = 0; i < DrumLibrary.PARTS.length; i++) labels[i + 1] = DrumLibrary.partLine(DrumLibrary.PARTS[i]);
            android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<String>(app, android.R.layout.simple_spinner_item, labels);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            part.setAdapter(a);
            part.setSelection(chosen[0]);
            filling[0] = false;
            String p = chosen[0] == 0 ? null : DrumLibrary.PARTS[chosen[0] - 1];
            rows.removeAllViews();
            List<DrumLibrary.Entry> list = DrumLibrary.of(p, Math.max(0, sort.getSelectedItemPosition()));
            for (DrumLibrary.Entry e : list) rows.addView(row(app, e, status, fill));
            if (list.isEmpty()) rows.addView(app.text("No sounds here yet: a pad's long-press menu has " + DrumLibrary.ADD, 13, false));
            int[] c = DrumLibrary.counts(null);
            status.setText(lastSaid.isEmpty() ? c[0] + " sounds, " + c[1] + " ready, " + c[2] + " waiting" : lastSaid);
        };
        refill = fill[0];
        part.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) {
                if (filling[0] || i == chosen[0]) return;
                chosen[0] = i;
                fill[0].run();
            }

            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        final int[] sorted = {0};
        sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) {
                if (i == sorted[0]) return;
                sorted[0] = i;
                fill[0].run();
            }

            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        TextView analyze = app.pill(DrumLibrary.ANALYZE, true, v -> analyze(app));
        analyze.setTag("library-analyze");
        col.addView(part);
        col.addView(sort);
        col.addView(analyze);
        col.addView(status);
        col.addView(rows);
        fill[0].run();
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        last = new AlertDialog.Builder(app).setTitle(DrumLibrary.TITLE).setView(scroll).setPositiveButton("Close", null).create();
        last.setOnDismissListener(d -> {
            refill = null;
            AudioMixerScreen.stop();
        });
        last.show();
    }

    static LinearLayout row(final MainActivity app, final DrumLibrary.Entry e, final TextView status, final Runnable[] fill) {
        LinearLayout box = app.col();
        box.setTag("library-row:" + e.name);
        box.setPadding(0, app.dp(6), 0, app.dp(4));
        box.addView(app.text((e.ready() ? "✔ " : "• ") + DrumLibrary.label(e.part) + " · " + DrumLibrary.line(e), 13, false));
        LinearLayout r = app.row();
        TextView play = app.pill("▶", false, v -> new Thread(() -> {
            try {
                File work = work(app);
                work.mkdirs();
                File f = access(app).local(e, work);
                byte[] bytes = AudioMix.read(f);
                if (f.getParentFile() != null && f.getParentFile().getAbsoluteFile().equals(work.getAbsoluteFile())) f.delete();
                AudioIo.StereoPcm p = AudioMix.resample(AudioMix.decode(e.name, bytes, null), AudioMix.RATE);
                app.handler.post(() -> AudioMixerScreen.play(p.left, p.right, status, e.name));
            } catch (Exception ex) {
                app.handler.post(() -> status.setText("Could not play " + e.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage())));
            }
        }, "pulsekit-library-play").start());
        play.setTag("library-play:" + e.name);
        TextView move = app.pill("Part…", false, v -> new AlertDialog.Builder(app).setTitle("The part for " + e.name).setItems(DrumLibrary.PART_LABELS, (d, w) -> {
            DrumLibrary.move(e, DrumLibrary.PARTS[w]);
            fill[0].run();
        }).setNegativeButton("Cancel", null).show());
        move.setTag("library-move:" + e.name);
        TextView again = app.pill("Again", false, v -> {
            e.state = DrumLibrary.WAITING;
            DrumLibrary.save();
            fill[0].run();
            analyze(app);
        });
        again.setTag("library-again:" + e.name);
        TextView out = app.pill("✕", false, v -> {
            DrumLibrary.remove(e);
            fill[0].run();
        });
        out.setTag("library-remove:" + e.name);
        r.addView(play);
        r.addView(move, AudioMixerScreen.margin(app));
        r.addView(again, AudioMixerScreen.margin(app));
        r.addView(out, AudioMixerScreen.margin(app));
        box.addView(r);
        return box;
    }
}
