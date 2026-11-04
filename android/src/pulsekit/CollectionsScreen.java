package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * File > Collections (and Collections.java on PyJav's Java menu): the collections, each a tree of
 * containers holding links to files (Media browser), DB files (Ref and Result files), playlists and
 * file sets. A file not found shows missing (Relocate folder… finds it by name in the folder
 * picked); a DB file gone shows deleted. Add to Collection on those menus puts a link in.
 */
final class CollectionsScreen {
    private CollectionsScreen() {}

    /** The folder picker's request (Relocate folder…). */
    static final int RELOCATE = 113;

    /** The window shown last, its menu, and the question asked last (for the tests). */
    static AlertDialog last, lastMenu, lastAsk;
    /** The collection and container shown last. */
    static MediaCollections.Collection shownCollection;
    static MediaCollections.Container shownContainer;

    static PromptVault vault(MainActivity app) {
        try {
            return PromptVault.open(app.getFilesDir());
        } catch (Exception ex) {
            app.setNow("Could not open the DB: " + ex.getMessage());
            return null;
        }
    }

    static boolean load(MainActivity app) {
        PromptVault v = vault(app);
        if (v == null) return false;
        MediaCollections.load(v);
        return true;
    }

    static void save(MainActivity app) {
        try {
            MediaCollections.save(vault(app));
        } catch (Exception ex) {
            app.setNow("Could not keep the collections: " + ex.getMessage());
        }
    }

    /** Whether a link's file, DB file, playlist or file set is there. */
    static MediaCollections.Checker checker(final MainActivity app) {
        final PromptVault v = vault(app);
        return e -> {
            if (MediaCollections.KIND_PLAYLIST.equals(e.kind)) return Playlist.names(app.getFilesDir()).contains(e.ref);
            if (MediaCollections.KIND_FILESET.equals(e.kind)) return true;
            if (MediaCollections.KIND_DB.equals(e.kind) || Playlist.isDb(e.ref)) {
                long[] at = Playlist.dbPlace(e.ref);
                return at != null && v != null && v.hasFile(at[1], (int) at[0]);
            }
            return fileThere(app, e.ref);
        };
    }

    /** A path or a content:// address that can still be read. */
    static boolean fileThere(MainActivity app, String ref) {
        if (ref == null || ref.length() == 0) return false;
        if (!ref.startsWith("content://")) return new File(ref.startsWith("file://") ? Uri.parse(ref).getPath() : ref).isFile();
        Cursor c = null;
        try {
            c = app.getContentResolver().query(Uri.parse(ref), new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            return c != null && c.moveToFirst();
        } catch (Exception ex) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    // ------------------------------------------------------------ the collections

    static void show(final MainActivity app) {
        show(app, null);
    }

    /** The collections; with `name`, that collection open. */
    static void show(final MainActivity app, String name) {
        if (!load(app)) return;
        MediaCollections.Collection named = name == null ? null : MediaCollections.named(name);
        if (named != null) {
            open(app, named, named.top);
            return;
        }
        if (last != null && last.isShowing()) last.dismiss();
        final MediaCollections.Checker check = checker(app);
        LinearLayout col = app.col();
        col.setPadding(app.dp(16), app.dp(8), app.dp(16), app.dp(8));
        TextView fresh = app.pill("New collection", true, v -> ask(app, "New collection", "", n -> {
            MediaCollections.Collection c = MediaCollections.add(n);
            save(app);
            open(app, c, c.top);
        }));
        fresh.setTag("collections-new");
        col.addView(fresh, new LinearLayout.LayoutParams(-2, -2));
        List<MediaCollections.Collection> all = MediaCollections.all();
        if (all.isEmpty()) {
            TextView none = app.text("No collections yet: New collection, or Add to Collection on a thumbnail's menu, a playlist or a file set.", 13, false);
            none.setTag("collections-empty");
            none.setPadding(0, app.dp(10), 0, 0);
            col.addView(none);
        }
        for (final MediaCollections.Collection c : all) {
            int lost = MediaCollections.lost(c.top, check);
            TextView row = app.text(c.name() + "\n" + c.top.total() + (c.top.total() == 1 ? " link" : " links") + (c.top.kids.isEmpty() ? "" : " · " + c.top.kids.size() + (c.top.kids.size() == 1 ? " container" : " containers"))
                + (lost > 0 ? " · " + lost + " missing or deleted" : ""), 14, false);
            row.setTag("collections-row:" + c.name());
            row.setPadding(0, app.dp(10), 0, app.dp(4));
            row.setOnClickListener(v -> open(app, c, c.top));
            row.setOnLongClickListener(v -> {
                collectionMenu(app, c);
                return true;
            });
            col.addView(row);
        }
        last = window(app, MediaCollections.TITLE, col);
    }

    static AlertDialog window(MainActivity app, String title, LinearLayout col) {
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        return new AlertDialog.Builder(app).setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }

    static void collectionMenu(final MainActivity app, final MediaCollections.Collection c) {
        lastMenu = new AlertDialog.Builder(app).setTitle(c.name()).setItems(new String[] {"Open", "Rename", "Delete"}, (d, w) -> {
            if (w == 0) open(app, c, c.top);
            else if (w == 1) ask(app, "Rename", c.name(), n -> {
                MediaCollections.rename(c, n);
                save(app);
                show(app);
            });
            else lastAsk = new AlertDialog.Builder(app).setTitle("Delete").setMessage("Delete the collection " + c.name() + "? Its links go; the files, DB files and playlists stay.")
                .setPositiveButton("Delete", (d2, w2) -> {
                    MediaCollections.remove(c);
                    save(app);
                    show(app);
                }).setNegativeButton("Cancel", null).show();
        }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------ a container

    static void open(final MainActivity app, final MediaCollections.Collection c, final MediaCollections.Container k) {
        if (last != null && last.isShowing()) last.dismiss();
        shownCollection = c;
        shownContainer = k;
        final MediaCollections.Checker check = checker(app);
        LinearLayout col = app.col();
        col.setPadding(app.dp(16), app.dp(8), app.dp(16), app.dp(8));
        LinearLayout tools = app.row();
        TextView up = app.pill(k == c.top ? "All collections" : "Up", false, v -> {
            MediaCollections.Container p = MediaCollections.parentOf(c.top, k);
            if (p == null) show(app);
            else open(app, c, p);
        });
        up.setTag("collections-up");
        tools.addView(up);
        TextView add = app.pill("New container", false, v -> ask(app, "New container", "", n -> {
            MediaCollections.Container made = MediaCollections.addContainer(k, n);
            save(app);
            open(app, c, made);
        }));
        add.setTag("collections-new-container");
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.leftMargin = app.dp(6);
        tools.addView(add, alp);
        col.addView(tools);
        for (final MediaCollections.Container kid : k.kids) {
            TextView row = app.text("▤ " + kid.name + "  (" + kid.total() + ")", 15, true);
            row.setTag("collections-container:" + kid.name);
            row.setPadding(0, app.dp(10), 0, app.dp(4));
            row.setOnClickListener(v -> open(app, c, kid));
            row.setOnLongClickListener(v -> {
                containerMenu(app, c, k, kid);
                return true;
            });
            col.addView(row);
        }
        for (final MediaCollections.Entry e : k.entries) {
            String state = MediaCollections.status(e, check);
            TextView row = app.text(e.name + "\n" + e.kindLabel() + (state.length() > 0 ? " · " + state : ""), 14, false);
            row.setTag("collections-entry:" + e.name);
            if (state.length() > 0) row.setAlpha(0.55f);
            row.setPadding(0, app.dp(8), 0, app.dp(4));
            row.setOnClickListener(v -> entryMenu(app, c, k, e, state));
            col.addView(row);
        }
        if (k.kids.isEmpty() && k.entries.isEmpty()) {
            TextView none = app.text("Empty: New container, or Add to Collection on a thumbnail's menu, a playlist or a file set.", 13, false);
            none.setPadding(0, app.dp(10), 0, 0);
            col.addView(none);
        }
        last = window(app, MediaCollections.path(c, k), col);
    }

    static void containerMenu(final MainActivity app, final MediaCollections.Collection c, final MediaCollections.Container in, final MediaCollections.Container kid) {
        lastMenu = new AlertDialog.Builder(app).setTitle(kid.name).setItems(new String[] {"Open", "Rename", "Delete"}, (d, w) -> {
            if (w == 0) open(app, c, kid);
            else if (w == 1) ask(app, "Rename", kid.name, n -> {
                kid.name = n.trim().length() == 0 ? kid.name : n.trim();
                save(app);
                open(app, c, in);
            });
            else lastAsk = new AlertDialog.Builder(app).setTitle("Delete").setMessage("Delete the container " + kid.name + " and what is in it (" + kid.total() + (kid.total() == 1 ? " link" : " links") + ")? The files stay.")
                .setPositiveButton("Delete", (d2, w2) -> {
                    MediaCollections.removeContainer(c.top, kid);
                    save(app);
                    open(app, c, in);
                }).setNegativeButton("Cancel", null).show();
        }).setNegativeButton("Cancel", null).show();
    }

    static final String OPEN = "Open", REMOVE = "Remove from collection", RELOCATE_ITEM = "Relocate folder…";

    static void entryMenu(final MainActivity app, final MediaCollections.Collection c, final MediaCollections.Container k, final MediaCollections.Entry e, String state) {
        final List<String> items = new ArrayList<String>();
        if (state.length() == 0) items.add(OPEN);
        if ("missing".equals(state) && MediaCollections.KIND_FILE.equals(e.kind)) items.add(RELOCATE_ITEM);
        items.add(REMOVE);
        lastMenu = new AlertDialog.Builder(app).setTitle(e.name).setItems(items.toArray(new String[0]), (d, w) -> {
            String it = items.get(w);
            if (it.equals(OPEN)) openEntry(app, e);
            else if (it.equals(RELOCATE_ITEM)) {
                waiting = c;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                app.startActivityForResult(intent, RELOCATE);
            } else {
                k.entries.remove(e);
                save(app);
                open(app, c, k);
            }
        }).setNegativeButton("Cancel", null).show();
    }

    /** The collection Relocate folder… is for. */
    static MediaCollections.Collection waiting;

    /** The folder picked for Relocate folder…: its files found by name. */
    static void relocatePicked(MainActivity app, Uri tree) {
        MediaCollections.Collection c = waiting;
        waiting = null;
        if (c == null) return;
        try {
            app.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // read for now
        }
        java.util.Map<String, String> found = new java.util.HashMap<String, String>();
        Cursor cur = null;
        try {
            String doc = DocumentsContract.getTreeDocumentId(tree);
            cur = app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc),
                new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            while (cur != null && cur.moveToNext()) {
                if (cur.getString(1) != null) found.put(cur.getString(1).toLowerCase(java.util.Locale.ROOT), DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0)).toString());
            }
        } catch (Exception ex) {
            app.setNow("Could not read the folder: " + ex.getMessage());
        } finally {
            if (cur != null) cur.close();
        }
        relocated(app, c, found);
    }

    /** Relocate folder… with a folder by its path (the files in it found by name). */
    static int relocateFolder(MainActivity app, MediaCollections.Collection c, File dir) {
        java.util.Map<String, String> found = new java.util.HashMap<String, String>();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) found.put(f.getName().toLowerCase(java.util.Locale.ROOT), f.getAbsolutePath());
        return relocated(app, c, found);
    }

    static int relocated(MainActivity app, MediaCollections.Collection c, java.util.Map<String, String> found) {
        int n = MediaCollections.relocate(c.top, found, checker(app));
        save(app);
        app.setNow(n == 0 ? "None of the missing files is in that folder" : "Found " + n + (n == 1 ? " missing file" : " missing files") + " there");
        if (shownCollection == c && shownContainer != null) open(app, c, shownContainer);
        return n;
    }

    /** Open: a playlist in the Playlist window, a file set's Info, a file (or DB file) in its viewer. */
    static void openEntry(final MainActivity app, final MediaCollections.Entry e) {
        if (MediaCollections.KIND_PLAYLIST.equals(e.kind)) {
            PlaylistScreen.show(app, e.ref);
            return;
        }
        if (MediaCollections.KIND_FILESET.equals(e.kind)) {
            if (last != null) last.dismiss();
            app.fileSets.openFileSetInfo(e.ref, e.name);
            return;
        }
        app.setNow("Opening " + e.name + "…");
        new Thread(() -> {
            byte[] data = null;
            try {
                if (Playlist.isDb(e.ref)) data = Playlist.dbBytes(vault(app), e.ref);
                else if (e.ref.startsWith("content://")) {
                    try (java.io.InputStream in = app.getContentResolver().openInputStream(Uri.parse(e.ref))) {
                        data = readAll(in);
                    }
                } else {
                    try (java.io.InputStream in = new java.io.FileInputStream(e.ref)) {
                        data = readAll(in);
                    }
                }
            } catch (Throwable ex) {
                data = null;
            }
            final byte[] got = data;
            app.runOnUiThread(() -> {
                if (got == null || got.length == 0) {
                    app.setNow("Could not read " + e.name);
                    return;
                }
                app.setNow(e.name);
                List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>();
                files.add(new JavaRun.FileOut(e.name, got));
                JavaRun.Result r = new JavaRun.Result("", files, 0);
                int kind = MediaDir.kind(e.name);
                if (kind == MediaDir.PICTURE) PictureOffer.offer(app, r);
                else if (kind == MediaDir.VIDEO) VideoOffer.offer(app, r);
                else if (kind == MediaDir.SOUND) AudioOffer.offer(app, e.name, r);
                else app.setNow(e.name + ": no viewer for this kind of file");
            });
        }, "pulsekit-collection-open").start();
    }

    static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ------------------------------------------------------------ Add to Collection

    /** Add to Collection: which collection (or a new one), then which container in it; the link kept there. */
    static void addTo(final MainActivity app, final String kind, final String ref, final String name, final long size) {
        if (!load(app)) return;
        final List<MediaCollections.Collection> all = MediaCollections.all();
        final List<String> rows = new ArrayList<String>();
        for (MediaCollections.Collection c : all) rows.add(c.name());
        rows.add("New collection…");
        lastAsk = new AlertDialog.Builder(app).setTitle(MediaCollections.ADD + " · " + name).setItems(rows.toArray(new String[0]), (d, w) -> {
            if (w == all.size()) ask(app, "New collection", "", n -> {
                MediaCollections.Collection c = MediaCollections.add(n);
                pickContainer(app, c, kind, ref, name, size);
            });
            else pickContainer(app, all.get(w), kind, ref, name, size);
        }).setNegativeButton("Cancel", null).show();
    }

    static void pickContainer(final MainActivity app, final MediaCollections.Collection c, final String kind, final String ref, final String name, final long size) {
        final List<MediaCollections.Container> where = MediaCollections.containers(c);
        if (where.size() == 1) {
            put(app, c, c.top, kind, ref, name, size);
            return;
        }
        final List<String> rows = new ArrayList<String>();
        for (MediaCollections.Container k : where) rows.add(MediaCollections.path(c, k));
        rows.add("New container…");
        lastAsk = new AlertDialog.Builder(app).setTitle("Into which container?").setItems(rows.toArray(new String[0]), (d, w) -> {
            if (w == where.size()) ask(app, "New container in " + c.name(), "", n -> put(app, c, MediaCollections.addContainer(c.top, n), kind, ref, name, size));
            else put(app, c, where.get(w), kind, ref, name, size);
        }).setNegativeButton("Cancel", null).show();
    }

    /** What Add to Collection said last (for the tests). */
    static String lastSaid;

    static void put(MainActivity app, MediaCollections.Collection c, MediaCollections.Container k, String kind, String ref, String name, long size) {
        boolean added = MediaCollections.addEntry(k, kind, ref, name, size);
        save(app);
        lastSaid = name + (added ? " added to " : " is in ") + MediaCollections.path(c, k) + (added ? "" : " already");
        app.setNow(lastSaid);
        android.widget.Toast.makeText(app, lastSaid, android.widget.Toast.LENGTH_SHORT).show();
    }

    interface Named {
        void picked(String name);
    }

    /** A name asked (New collection, New container, Rename). */
    static void ask(MainActivity app, String title, String now, final Named then) {
        final EditText field = new EditText(app);
        field.setTag("collections-name");
        field.setSingleLine(true);
        field.setText(now);
        LinearLayout box = app.col();
        box.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        box.addView(field);
        lastAsk = new AlertDialog.Builder(app).setTitle(title).setView(box)
            .setPositiveButton("OK", (d, w) -> then.picked(field.getText().toString()))
            .setNegativeButton("Cancel", null).show();
    }
}
