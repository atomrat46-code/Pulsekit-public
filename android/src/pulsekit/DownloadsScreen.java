package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * File > Downloads: the Sogni runs and the files they downloaded (SogniDownloads), in three tabs:
 * Pending, In progress, Finished / failed; by program, the newest first. A tap on a file previews
 * it; a long press on a finished one offers Preview, Clear (off the list; the file stays), Rename,
 * Delete (asked first, with its prompt file and prompt library entry too when ticked), Move to and
 * Copy to (Download, the Recent MB folder, a favourite folder, or another folder picked).
 */
final class DownloadsScreen {
    static final int PICK_FOLDER = 39;

    /** The screen shown last, for the tests, and the tab it shows. */
    static AlertDialog last;
    static String tab = "";

    /** Move to / Copy to waiting for the folder picked with Other…: {run, item, "move" or "copy"}. */
    private static SogniDownloads.Run waitingRun;
    private static SogniDownloads.Item waitingItem;
    private static boolean waitingMove;

    private DownloadsScreen() {}

    static void show(final MainActivity app) {
        SogniDownloads.init(app.getFilesDir());
        // The tab shown last, unless it is empty now (a run in progress is shown then).
        if (tab.length() == 0 || SogniDownloads.tab(tab).isEmpty()) tab = SogniDownloads.firstTab();
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app).setTitle("Downloads").setView(scroll).setPositiveButton("Close", null).create();
        fill(app, col);
        last.show();
        // Every second while it is shown: the tabs' counts and the runs' times; the list again when a run moved on.
        final AlertDialog shown = last;
        final String[] seen = new String[] {signature()};
        col.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!shown.isShowing()) return;
                String now = signature();
                if (!now.equals(seen[0])) {
                    seen[0] = now;
                    fill(app, col);
                } else {
                    refreshTimes(col);
                }
                col.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /** The runs and their states: when it changes, the list is made again. */
    static String signature() {
        StringBuilder sb = new StringBuilder();
        for (String t : new String[] {SogniDownloads.PENDING, SogniDownloads.PROGRESS, SogniDownloads.FINISHED}) {
            for (SogniDownloads.Run r : SogniDownloads.tab(t)) sb.append(r.id).append(r.state).append(r.status).append(r.items.size()).append(',');
            sb.append('|');
        }
        return sb.toString();
    }

    /** The In progress and Pending runs' lines again (their time). */
    private static void refreshTimes(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            android.view.View k = g.getChildAt(i);
            Object tag = k.getTag();
            if (k instanceof TextView && tag instanceof String && ((String) tag).startsWith("downloads-run:")) {
                SogniDownloads.Run r = SogniDownloads.run(Long.parseLong(((String) tag).substring(14)));
                if (r != null) ((TextView) k).setText(SogniDownloads.runLabel(r));
            } else if (k instanceof android.view.ViewGroup) {
                refreshTimes((android.view.ViewGroup) k);
            }
        }
    }

    private static void fill(final MainActivity app, final LinearLayout col) {
        col.removeAllViews();
        if (SogniDownloads.count() == 0) {
            TextView none = app.text("No downloads yet", 15, false);
            none.setTag("downloads-none");
            none.setPadding(0, app.dp(12), 0, app.dp(12));
            col.addView(none);
            return;
        }
        LinearLayout tabs = app.row();
        for (final String t : new String[] {SogniDownloads.PENDING, SogniDownloads.PROGRESS, SogniDownloads.FINISHED}) {
            TextView pill = app.pill(SogniDownloads.tabLabel(t), t.equals(tab), v -> {
                tab = t;
                fill(app, col);
            });
            pill.setTag("downloads-tab:" + t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = app.dp(6);
            tabs.addView(pill, lp);
        }
        android.widget.HorizontalScrollView tabScroll = new android.widget.HorizontalScrollView(app);
        tabScroll.addView(tabs);
        col.addView(tabScroll);
        List<SogniDownloads.Run> runs = SogniDownloads.tab(tab);
        if (SogniDownloads.FINISHED.equals(tab) && !runs.isEmpty()) {
            // Sort by (newest first at first), and Clear all: the list, not the files.
            LinearLayout sorts = app.row();
            sorts.addView(app.text("Sort by", 13, false));
            for (final String how : SogniDownloads.SORTS) {
                TextView pill = app.pill(how, how.equals(SogniDownloads.sort), v -> {
                    SogniDownloads.sort = how;
                    fill(app, col);
                });
                pill.setTag("downloads-sort:" + how);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = app.dp(6);
                sorts.addView(pill, lp);
            }
            android.widget.HorizontalScrollView sortScroll = new android.widget.HorizontalScrollView(app);
            sortScroll.addView(sorts);
            sortScroll.setPadding(0, app.dp(8), 0, 0);
            col.addView(sortScroll);
            TextView clearAll = app.pill("Clear all", false, v -> lastClearAll = new AlertDialog.Builder(app).setTitle("Clear all")
                .setMessage("Take every finished download off this list? The files stay where they are.")
                .setPositiveButton("Clear all", (d, w) -> {
                    app.setNow(SogniDownloads.clearFinished() + " off the list (the files stay)");
                    fill(app, col);
                })
                .setNegativeButton("Cancel", null).show());
            clearAll.setTag("downloads-clear-all");
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.topMargin = app.dp(6);
            col.addView(clearAll, clp);
        }
        if (runs.isEmpty()) {
            TextView none = app.text(SogniDownloads.PROGRESS.equals(tab) ? "No run in progress" : SogniDownloads.PENDING.equals(tab) ? "Nothing pending" : "No finished downloads", 14, false);
            none.setTag("downloads-empty");
            none.setPadding(0, app.dp(12), 0, 0);
            col.addView(none);
            return;
        }
        String program = null;
        boolean headings = SogniDownloads.byProgram(tab);
        for (final SogniDownloads.Run r : runs) {
            if (headings && !r.programName().equals(program)) {
                program = r.programName();
                TextView head = app.text(program, 15, true);
                head.setTypeface(Typeface.DEFAULT_BOLD);
                head.setTag("downloads-program:" + program);
                head.setPadding(0, app.dp(14), 0, app.dp(4));
                col.addView(head);
            }
            // Without program headings (Sort by newest, oldest, type) each run says its program.
            TextView line = app.text((headings ? "" : r.programName() + " \u00b7 ") + SogniDownloads.runLabel(r), 12, false);
            line.setTextColor(Color.parseColor("#8A8B86"));
            line.setTag("downloads-run:" + r.id);
            line.setPadding(app.dp(8), app.dp(4), 0, app.dp(2));
            if (SogniDownloads.FAILED.equals(r.state) || r.items.isEmpty()) {
                line.setOnLongClickListener(v -> {
                    SogniDownloads.clear(r.id, null);
                    fill(app, col);
                    return true;
                });
            }
            col.addView(line);
            if (SogniDownloads.PROGRESS.equals(r.state) || SogniDownloads.PENDING.equals(r.state)) {
                // Mark as ready: a run the app lost track of (it never says it is over) is let go.
                TextView ready = app.pill("Mark as ready", false, v -> {
                    SogniDownloads.markReady(r.id);
                    fill(app, col);
                });
                ready.setTag("downloads-ready:" + r.id);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
                rp.leftMargin = app.dp(8);
                rp.bottomMargin = app.dp(4);
                col.addView(ready, rp);
            }
            if (r.items.isEmpty() && SogniDownloads.FINISHED.equals(tab)) {
                TextView none = app.text("No files", 13, false);
                none.setPadding(app.dp(16), 0, 0, app.dp(4));
                col.addView(none);
            }
            for (final SogniDownloads.Item item : r.items) {
                TextView row = app.text(item.name + "\n" + item.shown, 14, false);
                row.setTag("downloads-item:" + item.name);
                row.setPadding(app.dp(16), app.dp(8), app.dp(8), app.dp(8));
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setCornerRadius(app.dp(8));
                bg.setColor(Color.parseColor("#1B1D1F"));
                row.setBackground(bg);
                row.setOnClickListener(v -> preview(app, item));
                // A finished picture or video: its thumbnail before the name, read in the background.
                if (SogniDownloads.FINISHED.equals(r.state) && ("picture".equals(item.kind()) || "video".equals(item.kind()))) thumb(app, row, item);
                if (SogniDownloads.FINISHED.equals(tab)) row.setOnLongClickListener(v -> {
                    menu(app, col, r, item);
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = app.dp(4);
                col.addView(row, lp);
            }
        }
    }

    /** Thumbnails made this session, by where the file is. */
    private static final java.util.Map<String, android.graphics.Bitmap> THUMBS = new java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>();

    private static void thumb(final MainActivity app, final TextView row, final SogniDownloads.Item item) {
        final int px = app.dp(56);
        android.graphics.Bitmap have = THUMBS.get(item.where);
        if (have != null) {
            put(app, row, have, px);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.graphics.Bitmap made = null;
                try {
                    byte[] data = bytes(app, item);
                    if (data != null) made = PromptSheet.refThumb(app, item.name, data, px);
                } catch (Throwable ex) {
                    made = null;
                }
                if (made == null) return;
                THUMBS.put(item.where, made);
                final android.graphics.Bitmap shown = made;
                app.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        put(app, row, shown, px);
                    }
                });
            }
        }, "pulsekit-download-thumb").start();
    }

    private static void put(MainActivity app, TextView row, android.graphics.Bitmap b, int px) {
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(app.getResources(), b);
        float sc = Math.min(px / (float) Math.max(1, b.getWidth()), px / (float) Math.max(1, b.getHeight()));
        d.setBounds(0, 0, Math.max(1, Math.round(b.getWidth() * sc)), Math.max(1, Math.round(b.getHeight() * sc)));
        row.setCompoundDrawables(d, null, null, null);
        row.setCompoundDrawablePadding(app.dp(10));
    }

    /** The long press menu shown last, for the tests. */
    static AlertDialog lastMenu;

    static final String[] MENU = {"Preview", "Clear", "Rename", "Delete", "Move to…", "Copy to…", "Create again", "Add to favourites"};

    /** Clear all's question, shown last (for the tests). */
    static AlertDialog lastClearAll;

    private static void menu(final MainActivity app, final LinearLayout col, final SogniDownloads.Run r, final SogniDownloads.Item item) {
        lastMenu = new AlertDialog.Builder(app).setTitle(item.name).setItems(MENU, (d, which) -> {
            if (which == 6) createAgain(app, r);
            else if (which == 7) app.setNow(favourite(app, r));
            else if (which == 0) preview(app, item);
            else if (which == 1) {
                SogniDownloads.clear(r.id, item);
                app.setNow(item.name + " is off the list (the file stays)");
                fill(app, col);
            } else if (which == 2) rename(app, col, item);
            else if (which == 3) delete(app, col, r, item);
            else places(app, col, r, item, which == 4);
        }).setNegativeButton("Cancel", null).show();
    }

    /** Create again: the program that made the download on PyJav, with the arguments it ran with, its Params open. */
    static void createAgain(MainActivity app, SogniDownloads.Run r) {
        String kind = r.program.toLowerCase(java.util.Locale.ROOT).endsWith(".py") ? "Python" : "Java";
        app.openKitView("py");
        app.programMenus.selectProgram(kind, r.program);
        if (!r.program.equals(app.pyName)) {
            app.setNow("Could not open " + r.program);
            return;
        }
        if (r.args.length() > 0 && app.pyJav.pkPyArgs != null) app.pyJav.pkPyArgs.setText(r.args);
        app.setNow(r.args.length() > 0 ? r.programName() + " with the arguments it ran with" : r.programName() + " (this download kept no arguments)");
        app.pyJav.pkOpenParams();
    }

    /** Add to favourites: the prompt library version the run stored (its result file with it) among the favourite prompts. */
    static String favourite(MainActivity app, SogniDownloads.Run r) {
        try {
            PromptVault vault = PromptVault.open(app.getFilesDir());
            PromptVault.Version v = r.versionId == 0 ? null : vault.version(r.versionId);
            if (v == null) return "Not in the prompt library: a run with Save the prompt into DB is (its result file with it)";
            VersionFavourites.load(app.getFilesDir());
            if (VersionFavourites.is(v.id)) return VersionFavourites.label(vault, v) + " is a favourite already";
            VersionFavourites.add(v.id);
            return "Added " + VersionFavourites.label(vault, v) + " to favourites";
        } catch (Exception ex) {
            return "Could not open the prompt library";
        }
    }

    /** The file's bytes; null after saying why not. */
    static byte[] bytes(MainActivity app, SogniDownloads.Item item) {
        try {
            InputStream in;
            Uri uri = uriFor(app, item.where);
            if (uri != null) in = app.getContentResolver().openInputStream(uri);
            else in = new java.io.FileInputStream(fileFor(item.where));
            if (in == null) throw new java.io.IOException("cannot open it");
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            } finally {
                in.close();
            }
        } catch (Exception ex) {
            app.setNow("Could not read " + item.name + " (" + (ex.getMessage() == null ? "it is gone" : ex.getMessage()) + ")");
            return null;
        }
    }

    /** A content:// address for `where` (as it is, or Download's file of that name); null for a path. */
    static Uri uriFor(MainActivity app, String where) {
        if (where == null) return null;
        if (where.startsWith("content://")) return Uri.parse(where);
        if (where.startsWith("Download/") && android.os.Build.VERSION.SDK_INT >= 29) {
            String name = where.substring(where.lastIndexOf('/') + 1);
            Cursor c = null;
            try {
                c = app.getContentResolver().query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[] {android.provider.MediaStore.MediaColumns._ID}, android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[] {name}, null);
                if (c != null && c.moveToFirst()) return android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0));
            } catch (Exception ignored) {
                // read as a path below
            } finally {
                if (c != null) c.close();
            }
        }
        return null;
    }

    private static File fileFor(String where) {
        if (where.startsWith("Download/")) {
            return new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), where.substring(9));
        }
        return new File(where);
    }

    static void preview(MainActivity app, SogniDownloads.Item item) {
        byte[] data = bytes(app, item);
        if (data == null) return;
        List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>();
        files.add(new JavaRun.FileOut(item.name, data));
        JavaRun.Result r = new JavaRun.Result("", files, 0);
        String kind = item.kind();
        boolean shown;
        if ("picture".equals(kind)) shown = PictureOffer.offer(app, r);
        else if ("video".equals(kind)) shown = VideoOffer.offer(app, r);
        else if ("audio".equals(kind)) shown = AudioOffer.offer(app, item.name, r);
        else if ("text".equals(kind)) {
            TextView t = app.text(new String(data, java.nio.charset.StandardCharsets.UTF_8), 13, false);
            t.setTextIsSelectable(true);
            t.setPadding(app.dp(16), app.dp(8), app.dp(16), app.dp(8));
            ScrollView s = new ScrollView(app);
            s.addView(t);
            new AlertDialog.Builder(app).setTitle(item.name).setView(s).setPositiveButton("Close", null).show();
            shown = true;
        } else shown = false;
        if (!shown) app.setNow("No preview for " + item.name);
    }

    private static void rename(final MainActivity app, final LinearLayout col, final SogniDownloads.Item item) {
        final EditText field = new EditText(app);
        field.setSingleLine(true);
        field.setText(item.name);
        field.setTag("downloads-rename");
        new AlertDialog.Builder(app).setTitle("Rename").setView(field).setPositiveButton("Rename", (d, w) -> {
            String next = field.getText().toString().trim().replace('/', '_').replace('\\', '_');
            if (next.length() == 0 || next.equals(item.name)) return;
            try {
                Uri uri = uriFor(app, item.where);
                String shown = item.shown.substring(0, item.shown.length() - item.name.length()) + next;
                if (uri != null && DocumentsContract.isDocumentUri(app, uri)) {
                    Uri renamed = DocumentsContract.renameDocument(app.getContentResolver(), uri, next);
                    SogniDownloads.moved(item, next, renamed == null ? uri.toString() : renamed.toString(), shown);
                } else if (uri != null) {
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, next);
                    if (app.getContentResolver().update(uri, v, null, null) < 1) throw new java.io.IOException("Download did not rename it");
                    SogniDownloads.moved(item, next, uri.toString(), shown);
                } else {
                    File f = fileFor(item.where);
                    File to = new File(f.getParentFile(), next);
                    if (to.exists()) throw new java.io.IOException(next + " is there already");
                    if (!f.renameTo(to)) throw new java.io.IOException("could not rename it");
                    SogniDownloads.moved(item, next, to.getAbsolutePath(), shown);
                }
                app.setNow("Renamed · " + next);
            } catch (Exception ex) {
                app.setNow("Could not rename " + item.name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            }
            fill(app, col);
        }).setNegativeButton("Cancel", null).show();
    }

    /** The Delete question shown last, for the tests. */
    static AlertDialog lastDelete;

    private static void delete(final MainActivity app, final LinearLayout col, final SogniDownloads.Run r, final SogniDownloads.Item item) {
        LinearLayout box = app.col();
        box.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        box.addView(app.text("Delete " + item.name + " from " + item.shown + "?", 14, false));
        final CheckBox also = new CheckBox(app);
        also.setTag("downloads-delete-also");
        boolean linked = r.sheet.length() > 0 || r.versionId != 0;
        if (linked) {
            also.setText("Also delete its prompt file" + (r.sheetName.length() > 0 ? " (" + r.sheetName + ")" : "") + " and its prompt library entry");
            box.addView(also);
        }
        lastDelete = new AlertDialog.Builder(app).setTitle("Delete").setView(box).setPositiveButton("Delete", (d, w) -> {
            boolean ok = remove(app, item.where);
            if (!ok) {
                app.setNow("Could not delete " + item.name);
                return;
            }
            StringBuilder said = new StringBuilder("Deleted · " + item.name);
            if (also.isChecked()) {
                if (r.sheet.length() > 0 && remove(app, r.sheet)) said.append(", ").append(r.sheetName);
                if (r.versionId != 0) {
                    try {
                        PromptVault.open(app.getFilesDir()).deleteVersion(r.versionId);
                        said.append(", its prompt library entry");
                    } catch (Exception ex) {
                        said.append(" (its prompt library entry stays: ").append(ex.getMessage()).append(')');
                    }
                }
                SogniDownloads.unlinked(r);
            }
            SogniDownloads.clear(r.id, item);
            app.setNow(said.toString());
            fill(app, col);
        }).setNegativeButton("Cancel", null).show();
    }

    private static boolean remove(MainActivity app, String where) {
        try {
            Uri uri = uriFor(app, where);
            if (uri != null) {
                if (DocumentsContract.isDocumentUri(app, uri)) return DocumentsContract.deleteDocument(app.getContentResolver(), uri);
                return app.getContentResolver().delete(uri, null, null) > 0;
            }
            File f = fileFor(where);
            return f.delete() || !f.exists();
        } catch (Exception ex) {
            return false;
        }
    }

    /** Move to / Copy to: Download, the Recent MB folder, a favourite, or another folder (Other…). */
    private static void places(final MainActivity app, final LinearLayout col, final SogniDownloads.Run r, final SogniDownloads.Item item, final boolean move) {
        MediaBrowser.load(app);
        final List<String[]> origins = new ArrayList<String[]>();
        final List<String> names = new ArrayList<String>();
        origins.add(new String[] {"", MediaBrowser.DOWNLOADS});
        names.add("Download");
        if (MediaDir.lastRoot.length() > 0) {
            origins.add(origin(MediaDir.lastRoot, MediaDir.lastPath));
            names.add("Recent MB folder");
        }
        for (String f : MediaDir.favourites) {
            String[] part = f.split("\t");
            List<String> under = new ArrayList<String>();
            for (int k = 1; k < part.length; k++) under.add(part[k]);
            origins.add(origin(part[0], under));
            names.add(MediaDir.favouriteLabel(f));
        }
        names.add("Other…");
        new AlertDialog.Builder(app).setTitle((move ? "Move " : "Copy ") + item.name + " to").setItems(names.toArray(new CharSequence[0]), (d, which) -> {
            if (which == origins.size()) {
                waitingRun = r;
                waitingItem = item;
                waitingMove = move;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                app.startActivityForResult(intent, PICK_FOLDER);
                return;
            }
            put(app, r, item, origins.get(which), move);
            if (col != null) fill(app, col);
        }).setNegativeButton("Cancel", null).show();
    }

    /** The folder for MediaBrowser.saveInto: a granted folder (and the folder under it), or a path. */
    static String[] origin(String root, List<String> under) {
        String last = under == null || under.isEmpty() ? null : under.get(under.size() - 1);
        if (root.startsWith("content://")) {
            Uri tree = Uri.parse(root);
            return new String[] {root, last != null ? last : DocumentsContract.getTreeDocumentId(tree)};
        }
        return new String[] {"", last != null ? last : root};
    }

    /** The folder picked with Other…: the move or copy waiting goes there. */
    static void folderPicked(MainActivity app, Uri tree) {
        if (waitingItem == null) return;
        MediaBrowser.keep(app, tree);
        put(app, waitingRun, waitingItem, new String[] {tree.toString(), DocumentsContract.getTreeDocumentId(tree)}, waitingMove);
        waitingItem = null;
        waitingRun = null;
        if (last != null && last.isShowing()) show(app);
    }

    private static void put(MainActivity app, SogniDownloads.Run r, SogniDownloads.Item item, String[] origin, boolean move) {
        byte[] data = bytes(app, item);
        if (data == null) return;
        try {
            String at = MediaBrowser.saveInto(app, origin, null, item.name, ArtJava.mimeOf(item.name), data);
            if (move) {
                if (!remove(app, item.where)) {
                    app.setNow("Copied to " + at + ", but could not delete " + item.name + " where it was");
                    return;
                }
                SogniDownloads.moved(item, item.name, at, at);
                app.setNow("Moved to " + at);
            } else {
                app.setNow("Copied to " + at);
            }
        } catch (Exception ex) {
            app.setNow("Could not " + (move ? "move " : "copy ") + item.name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
    }
}
