package pulsekit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Media browser (MediaBrowser in PyJav's Java menu): a folder's pictures, videos and sounds as
 * preview thumbnails, its folders first, a page of 48 at a time. A picture opens full size (pinch
 * to zoom, Previous / Next), a video plays full screen with its controls, a sound or a MIDI plays
 * with Play / Stop and a position bar; a folder opens in its place, and Up goes back as far as the
 * folder picked. The folder is one the system's picker granted (Params: Browse), or a path.
 */
final class MediaBrowser {
    static final int PICK_DIR = 37;
    static final int PAGE = 48;
    /** Where Loop videos is kept. */
    static final String PREFS = "pulsekit-media";
    static final String SETTINGS = "settings";

    /** The browser shown last, for the tests. */
    static MediaBrowser last;
    /** What the last card opened ("picture", "video", "sound") and with what, for the tests. */
    static String lastOpened;
    static MediaPlayer lastSound;

    /** Thumbnails by file, kept while there is room (an eighth of the memory the app may use). */
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<String, Bitmap>((int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 8)) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    final MainActivity app;
    /** A granted folder (content://.../tree/...), or null for a folder given by path. */
    final Uri tree;
    /** The folders opened, the picked one first: document ids, or paths. */
    final List<String> path = new ArrayList<String>();
    List<MediaDir.Entry> entries = new ArrayList<MediaDir.Entry>();
    int page;
    AlertDialog dialog;
    LinearLayout body;
    /** Bumped on every page shown: thumbnails for an earlier one are dropped. */
    private int token;
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaBrowser(MainActivity app, Uri tree, String start) {
        this.app = app;
        this.tree = tree;
        this.path.add(start);
    }

    /** Params' Browse: the system's folder picker, for the waiting Directory row. */
    static void pick(MainActivity app) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // Writing too: captured frames (SC) and ImageUpscaler's copy go into the folder.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        app.startActivityForResult(intent, PICK_DIR);
    }

    /** The folder picked: kept readable after a restart too (a run opens it again). */
    static void keep(MainActivity app, Uri tree) {
        try {
            app.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ex) {
            try {
                app.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // readable for this session still
            }
        }
    }

    /** Loop videos and the player's last volume, zoom and speed (MediaDir.encode), from the app's preferences. */
    static void load(MainActivity app) {
        MediaDir.decode(app.getSharedPreferences(PREFS, 0).getString(SETTINGS, null));
    }

    static void save(MainActivity app) {
        app.getSharedPreferences(PREFS, 0).edit().putString(SETTINGS, MediaDir.encode()).apply();
    }

    /** Opens the Media browser on `dir`: a granted folder's content:// address, or a path. */
    static MediaBrowser open(MainActivity app, String dir) {
        rowPictures = false;
        MediaBrowser b = make(app, dir, true);
        if (b == null) return null;
        last = b;
        b.show();
        return b;
    }

    /**
     * The browser on `dir`, not shown yet; null after saying why it cannot be. `recent`: the folder
     * becomes the Recent MB folder, and opens where it was last left.
     */
    static MediaBrowser make(MainActivity app, String dir, boolean recent) {
        if (dir == null || dir.trim().length() == 0) {
            app.setNow("No directory: Params, Browse");
            return null;
        }
        String d = dir.trim();
        // A folder by its file:// address is a path.
        if (d.startsWith("file://")) d = Uri.parse(d).getPath();
        MediaBrowser b;
        if (DOWNLOADS.equals(d)) {
            // Download (Android lets no app pick it as a folder): read through Android's own list of it.
            b = new MediaBrowser(app, null, DOWNLOADS);
            load(app);
            b.base = null;
            return b;
        }
        if (d.startsWith("content://")) {
            Uri tree = Uri.parse(d);
            String doc;
            try {
                doc = DocumentsContract.getTreeDocumentId(tree);
            } catch (Exception ex) {
                app.setNow("Not a folder: pick it again with Browse");
                return null;
            }
            b = new MediaBrowser(app, tree, doc);
        } else {
            if (!new File(d).isDirectory()) {
                app.setNow(d + " is not a directory");
                return null;
            }
            b = new MediaBrowser(app, null, new File(d).getAbsolutePath());
        }
        load(app);
        b.base = b.tree != null ? d : b.path.get(0);
        if (!recent) return b;
        // The folder picked, and the folder it was last in under it (when it is still there).
        b.root = b.base;
        for (String under : MediaDir.pathFor(b.root)) {
            if (!b.there(under)) break;
            b.path.add(under);
        }
        return b;
    }

    /** Whether `under` is a folder that can still be opened in this browser. */
    boolean there(String under) {
        if (this.tree == null) return new File(under).isDirectory() && under.startsWith(this.base + File.separator);
        // The folder itself is asked about, not listed: listing a folder of thousands of files on the
        // main thread here stopped the app (Choose file on a large Recent MB folder).
        Cursor c = null;
        try {
            c = this.app.getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(this.tree, under),
                new String[] {DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            return c != null && c.moveToFirst() && DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(0));
        } catch (Exception ex) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    /** The folder this browser was made on, as Params gives it (Add to favourites keeps it). */
    String base;

    /** Choose file: what to do with the file picked, or null for the Media browser itself. */
    RefBrowser.Picked chooser;
    /** Choose file's tab: "recent", "download" or "fav:<n>". */
    String tab = "";
    /** For Choose file's "Other…": the system's file picker. */
    Runnable other;

    /** Download, listed from Android's Downloads (the files Pulsekit and its programs saved there). */
    static final String DOWNLOADS = "pulsekit:downloads";
    /** A Downloads file's id, as the entry's id. */
    static final String DL = "pulsekit-dl:";
    /** Another app's picture, video or sound in Download: MEDIA + 0 audio / 1 image / 2 video + ":" + its id. */
    static final String MEDIA = "pulsekit-media:";

    /**
     * Choose file: the Media browser, with tabs for the Recent MB folder (the folder the Media browser
     * opened last), Download and the favourite folders, open on the tab used last; a file tapped is copied into PyJav's input
     * folder and handed to `picked`. "Other…" opens the system's picker (`other`).
     */
    static MediaBrowser choose(MainActivity app, RefBrowser.Picked picked, Runnable other) {
        rowPictures = false;
        load(app);
        // The tab used last (a favourite, say), else the Recent MB folder.
        return chooseTab(app, MediaDir.openTab(MediaDir.lastRoot.length() > 0), picked, other);
    }

    /**
     * Choose file for a Params row (`key`, its switch): it opens where that row's file was picked
     * last (each row its own folder: Reference image 1 and 2 apart), else as Choose file does.
     */
    static MediaBrowser chooseFor(final MainActivity app, final String key, final RefBrowser.Picked picked, Runnable other) {
        return chooseFor(app, key, picked, other, false);
    }

    /**
     * Choose file for a picture row (`picture`: a Sogni program's reference image): a video tapped plays
     * instead, and C in its player fills the row with the frame there (asked about upscaling first when
     * it needs it).
     */
    static boolean rowPictures;

    static MediaBrowser chooseFor(final MainActivity app, final String key, final RefBrowser.Picked picked, Runnable other, boolean picture) {
        MediaBrowser b = chooseRow(app, key, picked, other);
        rowPictures = picture;
        return b;
    }

    private static MediaBrowser chooseRow(final MainActivity app, final String key, final RefBrowser.Picked picked, Runnable other) {
        load(app);
        RefBrowser.Picked keep = (name, file) -> {
            // The folder it came from: this row's next Choose file opens there.
            if (chosenPlace != null && chosenPlace.length() > 0) {
                MediaDir.rowPlace(key, chosenPlace);
                save(app);
            }
            picked.picked(name, file);
        };
        String place = MediaDir.rowPlace(key);
        if (place.equals("download")) return chooseTab(app, "download", keep, other);
        if (place.length() > 0) {
            String[] part = place.split("\t");
            MediaBrowser b = make(app, part[0], false);
            if (b != null) {
                for (int k = 1; k < part.length; k++) {
                    if (!b.there(part[k])) break;
                    b.path.add(part[k]);
                }
                b.root = b.base;
                b.chooser = keep;
                b.tab = "row";
                b.other = other;
                last = b;
                b.show();
                return b;
            }
        }
        return choose(app, keep, other);
    }

    /** The folder the file Choose file handed on last came from ("root\tfolder..." or "download"). */
    static String chosenPlace;

    /** Where this browser is, as a row's place: "download", or its folder and the folders opened under it. */
    String placeHere() {
        if (this.tree == null && !this.path.isEmpty() && DOWNLOADS.equals(this.path.get(0))) return "download";
        if (this.root == null) return "";
        return MediaDir.placeOf(this.root, this.path.subList(1, this.path.size()));
    }

    static MediaBrowser chooseTab(MainActivity app, String tab, RefBrowser.Picked picked, Runnable other) {
        MediaBrowser b = null;
        if (tab.equals("recent")) b = make(app, MediaDir.lastRoot, true);
        else if (tab.startsWith("fav:")) {
            int i = Integer.parseInt(tab.substring(4));
            if (i < MediaDir.favourites.size()) {
                String[] part = MediaDir.favourites.get(i).split("\t");
                b = make(app, part[0], false);
                for (int k = 1; b != null && k < part.length; k++) {
                    if (!b.there(part[k])) break;
                    b.path.add(part[k]);
                }
                // The favourite opened is the Recent MB folder now (and where it is in it).
                if (b != null) b.root = b.base;
            }
        }
        if (b == null) {
            tab = "download";
            b = new MediaBrowser(app, null, DOWNLOADS);
            b.base = null;
        }
        b.chooser = picked;
        b.tab = tab;
        b.other = other;
        last = b;
        b.show();
        return b;
    }

    static final int MEDIA_PERMISSION = 79;

    /** Whether Pulsekit may read the pictures, videos and sounds other apps saved (Download included). */
    static boolean mediaAllowed(MainActivity app) {
        if (android.os.Build.VERSION.SDK_INT < 23) return true;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return app.checkSelfPermission("android.permission.READ_MEDIA_AUDIO") == 0
                && app.checkSelfPermission("android.permission.READ_MEDIA_IMAGES") == 0
                && app.checkSelfPermission("android.permission.READ_MEDIA_VIDEO") == 0;
        }
        return app.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == 0;
    }

    /** Asks for that permission; the browser shown lists Download again when it is answered. */
    static void askMedia(MainActivity app) {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        String[] want = android.os.Build.VERSION.SDK_INT >= 33
            ? new String[] {"android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"}
            : new String[] {"android.permission.READ_EXTERNAL_STORAGE"};
        app.requestPermissions(want, MEDIA_PERMISSION);
    }

    /** The permission was answered: the browser shown reads its folder again. */
    static void mediaAnswered(MainActivity app) {
        MediaBrowser b = last;
        if (b != null && b.dialog != null && b.dialog.isShowing()) b.load(b.here());
        if (!mediaAllowed(app)) app.setNow("Without the permission Download shows only the files Pulsekit saved");
    }

    /** Asked once a session when Download opens without the permission. */
    private static boolean askedMedia;

    /** The files in Download (newest first); null when they cannot be listed. */
    List<MediaDir.Entry> downloads() {
        List<MediaDir.Entry> all = new ArrayList<MediaDir.Entry>();
        if (android.os.Build.VERSION.SDK_INT < 29) {
            File[] files = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).listFiles();
            if (files == null) return null;
            for (File f : files) {
                if (f.isHidden() || f.isDirectory()) continue;
                MediaDir.Entry e = new MediaDir.Entry();
                e.name = f.getName();
                e.id = f.getAbsolutePath();
                e.size = f.length();
                all.add(e);
            }
            return MediaDir.shown(all, this.chooser != null);
        }
        Cursor c = null;
        try {
            c = this.app.getContentResolver().query(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[] {android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.SIZE,
                    android.provider.MediaStore.MediaColumns.DATE_ADDED},
                null, null, android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (c == null) return all;
            while (c.moveToNext()) {
                MediaDir.Entry e = new MediaDir.Entry();
                e.id = DL + c.getLong(0);
                e.name = c.getString(1);
                e.size = c.isNull(2) ? 0 : c.getLong(2);
                e.date = c.isNull(3) ? 0 : c.getLong(3);
                if (e.name != null) all.add(e);
            }
        } catch (Exception ex) {
            return null;
        } finally {
            if (c != null) c.close();
        }
        // Download lists only Pulsekit's own files there; with the media permission the pictures,
        // videos and sounds other apps saved in Download are listed too (a WAV from the browser).
        if (mediaAllowed(this.app)) {
            java.util.Set<String> have = new java.util.HashSet<String>();
            for (MediaDir.Entry e : all) have.add(e.name + "\u0000" + e.size);
            Uri[] kinds = {android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI};
            for (int k = 0; k < kinds.length; k++) {
                Cursor m = null;
                try {
                    m = this.app.getContentResolver().query(kinds[k],
                        new String[] {android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.SIZE,
                            android.provider.MediaStore.MediaColumns.DATE_ADDED},
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?", new String[] {android.os.Environment.DIRECTORY_DOWNLOADS + "/%"}, null);
                    if (m == null) continue;
                    while (m.moveToNext()) {
                        MediaDir.Entry e = new MediaDir.Entry();
                        e.id = MEDIA + k + ":" + m.getLong(0);
                        e.name = m.getString(1);
                        e.size = m.isNull(2) ? 0 : m.getLong(2);
                        e.date = m.isNull(3) ? 0 : m.getLong(3);
                        if (e.name == null || !have.add(e.name + "\u0000" + e.size)) continue;
                        all.add(e);
                    }
                } catch (Exception ignored) {
                    // that kind cannot be listed
                } finally {
                    if (m != null) m.close();
                }
            }
            java.util.Collections.sort(all, new java.util.Comparator<MediaDir.Entry>() {
                @Override
                public int compare(MediaDir.Entry a, MediaDir.Entry b) {
                    return Long.compare(b.date, a.date);
                }
            });
        } else if (!askedMedia) {
            askedMedia = true;
            this.main.post(() -> askMedia(this.app));
        }
        // Newest first, as Downloads gives them.
        List<MediaDir.Entry> out = new ArrayList<MediaDir.Entry>();
        for (MediaDir.Entry e : all) {
            e.kind = MediaDir.kind(e.name);
            if (e.kind != 0 || this.chooser != null) out.add(e);
        }
        return out;
    }

    /** Whether the entry is a file on disk by its path (not in a granted folder, not in Downloads). */
    boolean onDisk(MediaDir.Entry e) {
        return this.tree == null && !e.id.startsWith(DL) && !e.id.startsWith(MEDIA);
    }

    /** Upscale/resize image: ImageUpscaler on PyJav, with the picture (copied into PyJav's input folder) as its input; its Params open. */
    void upscale(final MediaDir.Entry e) {
        final Uri tree = this.tree;
        final String base = this.base;
        final String root = this.root;
        final List<String> at = new ArrayList<String>(this.path);
        final MainActivity host = this.app;
        this.chosen(e, (name, file) -> {
            host.openKitView("py");
            host.programMenus.selectProgram("Java", "ImageUpscaler.java");
            host.pyJav.pkUseInputPath(file.getAbsolutePath());
            // Return: the Media browser again, in this folder (the new picture listed when it was saved here).
            host.pyJav.pkSetReturn(() -> reopen(host, tree, base, root, at));
            host.pyJav.pkOpenParams();
        });
    }

    /** ImageUpscaler on PyJav with `in` (a frame C captured) as its input; Return opens this browser again here. */
    void upscaleFile(File in, android.app.Dialog player) {
        final Uri tree = this.tree;
        final String base = this.base;
        final String root = this.root;
        final List<String> at = new ArrayList<String>(this.path);
        final MainActivity host = this.app;
        if (player != null && player.isShowing()) player.dismiss();
        if (this.dialog != null) this.dialog.dismiss();
        host.openKitView("py");
        host.programMenus.selectProgram("Java", "ImageUpscaler.java");
        host.pyJav.pkUseInputPath(in.getAbsolutePath());
        host.pyJav.pkSetReturn(() -> reopen(host, tree, base, root, at));
        host.pyJav.pkOpenParams();
    }

    /** What opens this browser again where it is now (Return in PyJav). */
    Runnable returner() {
        final Uri tree = this.tree;
        final String base = this.base;
        final String root = this.root;
        final List<String> at = new ArrayList<String>(this.path);
        final MainActivity host = this.app;
        return () -> reopen(host, tree, base, root, at);
    }

    /** The Media browser again on the folders `at` (the first the folder picked), as Return from ImageUpscaler opens it. */
    static MediaBrowser reopen(MainActivity app, Uri tree, String base, String root, List<String> at) {
        MediaBrowser b = at.size() > 0 && DOWNLOADS.equals(at.get(0)) ? new MediaBrowser(app, null, DOWNLOADS) : make(app, base, false);
        if (b == null) return null;
        b.path.clear();
        b.path.addAll(at);
        b.root = root;
        load(app);
        last = b;
        b.show();
        return b;
    }

    /**
     * Where each file copied into PyJav's input folder came from: {granted folder or "", the folder's
     * document id, path or DOWNLOADS}. ImageUpscaler's --output_dir original puts its copy there.
     */
    static final java.util.Map<String, String[]> ORIGIN = new java.util.concurrent.ConcurrentHashMap<String, String[]>();

    /**
     * Writes `data` as `name` in a folder (`origin` as ORIGIN keeps it), or in its subfolder `sub`
     * (made when it is not there); a name taken gets " (1)". Returns where, for the log. Throws with
     * what to do when the folder cannot be written.
     */
    static String saveInto(MainActivity app, String[] origin, String sub, String name, String mime, final byte[] data) throws Exception {
        return saveInto(app, origin, sub, name, mime, out -> out.write(data));
    }

    /** What saveInto writes: the bytes, or a file streamed in (a large video is not held in memory). */
    interface Body {
        void write(java.io.OutputStream out) throws Exception;
    }

    /**
     * Where saveInto wrote last on this thread, as the file can be opened again: its content:// address
     * or its path (the label it returns is for people). Downloads' moves keep it, so Preview and the
     * playlists still find a moved file.
     */
    static final ThreadLocal<String> savedAddress = new ThreadLocal<String>();

    /** As above, with the file's content written by `data` (Downloads' Move: streamed from where it is). */
    static String saveInto(MainActivity app, String[] origin, String sub, String name, String mime, Body data) throws Exception {
        String tree = origin[0];
        String dir = origin[1];
        if (DOWNLOADS.equals(dir)) {
            if (android.os.Build.VERSION.SDK_INT < 29) {
                origin = new String[] {"", android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()};
                return saveInto(app, origin, sub, name, mime, data);
            }
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
            String rel = android.os.Environment.DIRECTORY_DOWNLOADS + (sub == null ? "" : "/" + sub);
            v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel);
            Uri uri = app.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new java.io.IOException("Download takes no more files named " + name);
            java.io.OutputStream os = app.getContentResolver().openOutputStream(uri);
            try {
                data.write(os);
            } finally {
                os.close();
            }
            savedAddress.set(uri.toString());
            return rel + "/" + name;
        }
        // A subfolder path (a move's %ddmmyyyy/pics): each part found (any case) or made in turn.
        if (sub != null && sub.indexOf('/') >= 0) {
            String[] at = origin;
            String[] parts = sub.split("/");
            for (int i = 0; i < parts.length - 1; i++) at = folderIn(app, at, parts[i]);
            return saveInto(app, at, parts[parts.length - 1], name, mime, data);
        }
        if (sub != null && sub.length() == 0) sub = null;
        if (tree == null || tree.length() == 0) {
            File folder = sub == null ? new File(dir) : new File(dir, sub);
            // Any case: an "sc" folder already there is the SC folder.
            if (sub != null && !folder.isDirectory()) {
                File[] kids = new File(dir).listFiles();
                if (kids != null) for (File k : kids) if (k.isDirectory() && k.getName().equalsIgnoreCase(sub)) folder = k;
            }
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("could not make " + folder.getPath());
            String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
            String ext = name.substring(stem.length());
            File f = new File(folder, name);
            for (int n = 1; f.exists(); n++) f = new File(folder, stem + " (" + n + ")" + ext);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            try {
                data.write(fos);
            } finally {
                fos.close();
            }
            savedAddress.set(f.getPath());
            return f.getPath();
        }
        Uri treeUri = Uri.parse(tree);
        try {
            Uri folder = DocumentsContract.buildDocumentUriUsingTree(treeUri, dir);
            String label = MediaDir.label(folder.toString());
            if (sub != null) {
                Uri found = null;
                Cursor c = app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dir),
                    new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
                if (c != null) {
                    try {
                        while (c.moveToNext()) {
                            // Any case: an "sc" folder already there is the SC folder (an exact match made "SC (1)"
                            // ... "SC (32)", one per capture, until the phone made no more).
                            if (found == null && sub.equalsIgnoreCase(c.getString(1)) && DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) found = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0));
                        }
                    } finally {
                        c.close();
                    }
                }
                if (found == null) found = DocumentsContract.createDocument(app.getContentResolver(), folder, DocumentsContract.Document.MIME_TYPE_DIR, sub);
                if (found == null) throw new java.io.IOException("could not make the " + sub + " folder");
                folder = found;
                label = label + "/" + sub;
            }
            // A name taken there: the next free "name (n)" picked here (Android tries up to (31) of
            // them, then gives up with "Failed to create unique file": Sogni names its results alike).
            String picked = freeName(childNames(app, treeUri, DocumentsContract.getDocumentId(folder)), name);
            Uri file = DocumentsContract.createDocument(app.getContentResolver(), folder, mime, picked);
            if (file == null) throw new java.io.IOException("could not write " + name);
            java.io.OutputStream os = app.getContentResolver().openOutputStream(file);
            try {
                data.write(os);
            } finally {
                os.close();
            }
            savedAddress.set(file.toString());
            return label + "/" + picked;
        } catch (SecurityException ex) {
            throw new java.io.IOException("Pulsekit may not write in this folder: pick it again with Params, Browse (it now asks to write there too)");
        }
    }

    /** The folder `name` in `origin` (found in any case, else made), as an origin for saveInto. */
    static String[] folderIn(MainActivity app, String[] origin, String name) throws Exception {
        String tree = origin[0];
        String dir = origin[1];
        if (DOWNLOADS.equals(dir)) {
            File base = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (android.os.Build.VERSION.SDK_INT < 29) return folderIn(app, new String[] {"", base.getAbsolutePath()}, name);
            throw new java.io.IOException("Download takes one subfolder only");
        }
        if (tree == null || tree.length() == 0) {
            File folder = new File(dir, name);
            File[] kids = new File(dir).listFiles();
            if (!folder.isDirectory() && kids != null) for (File k : kids) if (k.isDirectory() && k.getName().equalsIgnoreCase(name)) folder = k;
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("could not make " + folder.getPath());
            return new String[] {"", folder.getPath()};
        }
        Uri treeUri = Uri.parse(tree);
        String found = childDir(app, treeUri, dir, name);
        if (found == null) {
            Uri made = DocumentsContract.createDocument(app.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(treeUri, dir), DocumentsContract.Document.MIME_TYPE_DIR, name);
            if (made == null) throw new java.io.IOException("could not make the " + name + " folder");
            found = DocumentsContract.getDocumentId(made);
        }
        return new String[] {tree, found};
    }

    /** A folder's subfolder by name (any case) in a granted folder: its document id, or null. */
    static String childDir(MainActivity app, Uri treeUri, String docId, String name) {
        Cursor c = null;
        try {
            c = app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId),
                new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            while (c != null && c.moveToNext()) {
                if (name.equalsIgnoreCase(c.getString(1)) && DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) return c.getString(0);
            }
        } catch (Exception ignored) {
            // not readable
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    /** Whether the subfolder path is in `origin` already (a move's %n picks the first that is not). */
    static boolean hasPath(MainActivity app, String[] origin, String path) {
        String tree = origin[0];
        String dir = origin[1];
        if (DOWNLOADS.equals(dir)) {
            return new File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), path).exists();
        }
        if (tree == null || tree.length() == 0) return new File(dir, path).exists();
        Uri treeUri = Uri.parse(tree);
        String at = dir;
        for (String part : path.split("/")) {
            if (part.length() == 0) continue;
            at = childDir(app, treeUri, at, part);
            if (at == null) return false;
        }
        return true;
    }

    /** A kind's folder as saveInto's origin (a granted folder's address, or a path). */
    static String[] originOf(String to) {
        return to.startsWith("content://") ? new String[] {to, DocumentsContract.getTreeDocumentId(Uri.parse(to))} : new String[] {"", to};
    }

    /** The kind's subfolder for a move now (its % codes filled in; "" or null: the folder itself). */
    static String subNow(final MainActivity app, String kind, final String[] origin) {
        String got = DownloadMove.subNow(kind, p -> hasPath(app, origin, p));
        return got.length() == 0 ? null : got;
    }

    /** The names in a granted folder (empty when it cannot be read). */
    static java.util.Set<String> childNames(MainActivity app, Uri treeUri, String docId) {
        java.util.Set<String> names = new java.util.HashSet<String>();
        Cursor c = null;
        try {
            c = app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId),
                new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            while (c != null && c.moveToNext()) {
                String n = c.getString(0);
                if (n != null) names.add(n.toLowerCase(java.util.Locale.ROOT));
            }
        } catch (Exception ignored) {
            // the provider picks
        } finally {
            if (c != null) c.close();
        }
        return names;
    }

    /** `name`, or the first "stem (n).ext" not among `taken` (lower case). */
    static String freeName(java.util.Set<String> taken, String name) {
        if (!taken.contains(name.toLowerCase(java.util.Locale.ROOT))) return name;
        String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        String ext = name.substring(stem.length());
        for (int n = 1; ; n++) {
            String next = stem + " (" + n + ")" + ext;
            if (!taken.contains(next.toLowerCase(java.util.Locale.ROOT))) return next;
        }
    }

    /** The picture Extract first frame wrote last (for the tests). */
    static File lastFirstFrame;

    /** A picture row's Choose file: a video played from it hands C's frame here (null otherwise). */
    RefBrowser.Picked frameRow;

    /**
     * Choose file's Extract first frame as reference img: the video's first frame (full size) written as a
     * PNG in PyJav's input folder, off the main thread, and that picture handed to the row as if picked.
     */
    void firstFrameChosen(final MediaDir.Entry e) {
        final RefBrowser.Picked picked = this.chooser;
        if (picked == null) return;
        this.app.setNow("Reading the first frame of " + e.name + "\u2026");
        new Thread(() -> {
            File out = null;
            MediaMetadataRetriever media = new MediaMetadataRetriever();
            try {
                if (this.onDisk(e)) media.setDataSource(e.id);
                else media.setDataSource(this.app, this.uri(e));
                Bitmap frame = media.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) frame = media.getFrameAtTime();
                if (frame != null) {
                    File dir = new File(this.app.getCacheDir(), "pyjav-in");
                    if (!dir.isDirectory()) dir.mkdirs();
                    out = new File(dir, MediaDir.firstFrameName(e.name, "png"));
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                    try {
                        frame.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    } finally {
                        fos.close();
                    }
                }
            } catch (Throwable ex) {
                out = null;
            } finally {
                try {
                    media.release();
                } catch (Throwable ignored) {
                    // released
                }
            }
            final File got = out;
            this.main.post(() -> {
                if (got == null) {
                    this.app.setNow("Could not read the first frame of " + e.name);
                    return;
                }
                lastFirstFrame = got;
                if (this.dialog != null) this.dialog.dismiss();
                this.app.setNow("First frame of " + e.name + " \u00b7 " + got.getName());
                chosenPlace = this.placeHere();
                picked.picked(got.getName(), got);
            });
        }, "pulsekit-first-frame").start();
    }

    /** Choose file: the file tapped, copied into PyJav's input folder off the main thread, then handed on. */
    void chosen(final MediaDir.Entry e) {
        this.chosen(e, this.chooser);
    }

    /** The file copied into PyJav's input folder off the main thread; then the browser closes and `picked` gets it. */
    void chosen(final MediaDir.Entry e, final RefBrowser.Picked picked) {
        if (e.dbId != 0) {
            this.chosenPromptFile(e, picked);
            return;
        }
        this.app.setNow("Reading " + e.name + "\u2026");
        new Thread(() -> {
            File out = null;
            try {
                File dir = new File(this.app.getCacheDir(), "pyjav-in");
                if (!dir.isDirectory()) dir.mkdirs();
                out = new File(dir, e.name.replace('/', '_'));
                InputStream in = this.app.getContentResolver().openInputStream(this.uri(e));
                java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                try {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) fos.write(buf, 0, n);
                } finally {
                    fos.close();
                    in.close();
                }
            } catch (Throwable ex) {
                out = null;
            }
            final File got = out;
            if (got != null) ORIGIN.put(got.getAbsolutePath(), new String[] {this.tree == null ? "" : this.tree.toString(), here()});
            // Read again from the folder when the phone empties the temp folder (InputOrigins).
            if (got != null) InputOrigins.recordUri(this.app.getFilesDir(), got.getAbsolutePath(), this.uri(e).toString());
            this.main.post(() -> {
                if (got == null) {
                    this.app.setNow("Could not read " + e.name);
                    return;
                }
                if (this.dialog != null) this.dialog.dismiss();
                this.app.setNow("Chose " + e.name);
                chosenPlace = this.placeHere();
                picked.picked(e.name, got);
            });
        }, "pulsekit-choose-file").start();
    }

    /** Choose file's tabs: Recent MB folder, Download, the favourites (a long press takes one out), Other…. */
    void tabs() {
        LinearLayout row = this.app.row();
        java.util.List<String[]> all = new ArrayList<String[]>();
        if (MediaDir.lastRoot.length() > 0) all.add(new String[] {"recent", "Recent MB folder"});
        all.add(new String[] {"download", "Download"});
        for (int i = 0; i < MediaDir.favourites.size(); i++) all.add(new String[] {"fav:" + i, this.favLabel(MediaDir.favourites.get(i))});
        for (final String[] t : all) {
            TextView pill = this.app.pill(t[1], t[0].equals(this.tab), v -> {
                if (t[0].equals(this.tab)) return;
                if (this.dialog != null) this.dialog.dismiss();
                // Choose file opens on this tab next time.
                MediaDir.usedTab(t[0]);
                save(this.app);
                chooseTab(this.app, t[0], this.chooser, this.other);
            });
            pill.setTag("media-tab:" + t[0]);
            if (t[0].startsWith("fav:")) {
                pill.setOnLongClickListener(v -> {
                    MediaDir.removeFavourite(Integer.parseInt(t[0].substring(4)));
                    save(this.app);
                    this.app.setNow(t[1] + " is no longer a favourite");
                    if (this.dialog != null) this.dialog.dismiss();
                    chooseTab(this.app, "download", this.chooser, this.other);
                    return true;
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(6);
            row.addView(pill, lp);
        }
        if (this.other != null) {
            TextView other = this.app.pill("Other\u2026", false, v -> {
                if (this.dialog != null) this.dialog.dismiss();
                this.other.run();
            });
            other.setTag("media-tab:other");
            row.addView(other);
        }
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this.app);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        scroll.setPadding(0, 0, 0, this.app.dp(4));
        this.body.addView(scroll);
    }

    /** The folder picked, as Params gives it: what the last folder opened is remembered under. */
    String root;

    private String here() {
        return this.path.get(this.path.size() - 1);
    }

    /** The folder shown, as its default playlist knows it: the path, or the granted folder and the document id. */
    /** Where in the page this folder was scrolled to, kept with its page (MediaDir.place). */
    String scrollKey() {
        return this.folderKey() + "\u0001y";
    }

    void rememberScroll() {
        if (this.scroll == null || this.loading) return;
        MediaDir.place(this.scrollKey(), this.scroll.getScrollY());
        save(this.app);
    }

    String folderKey() {
        return this.tree == null ? here() : this.tree.toString() + "|" + here();
    }

    TextView playlistButton;

    /** The Playlist (n) button: shown once this folder's playlist has a file. */
    void paintPlaylist() {
        if (this.playlistButton == null) return;
        int n = MediaPlaylist.items(this.app.getFilesDir(), this.folderKey()).size();
        this.playlistButton.setText(MediaPlaylist.button(n));
        this.playlistButton.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
    }

    /** The thumbnail shown while a playlist item is held, for the tests. */
    static android.widget.PopupWindow lastPeek;
    static ImageView lastPeekImage;

    static TextView lastPeekInfo;

    /**
     * A playlist item's thumbnail over the list, while it is held (a sound shows its type), with a
     * video's length and resolution or a picture's dimensions and size under it.
     */
    void peek(View anchor, final MediaDir.Entry e) {
        this.unpeek();
        int side = Math.min(this.app.getResources().getDisplayMetrics().widthPixels, this.app.getResources().getDisplayMetrics().heightPixels) * 6 / 10;
        LinearLayout col = this.app.col();
        col.setBackgroundColor(0xee000000);
        col.setPadding(this.app.dp(6), this.app.dp(6), this.app.dp(6), this.app.dp(6));
        FrameLayout box = new FrameLayout(this.app);
        TextView mark = this.app.text(ext(e.name) + "\n" + e.name, 15, true);
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(UiKit.MUTED);
        box.addView(mark, new FrameLayout.LayoutParams(-1, -1));
        final ImageView image = new ImageView(this.app);
        image.setTag("playlist-peek");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(image, new FrameLayout.LayoutParams(-1, -1));
        col.addView(box, new LinearLayout.LayoutParams(-1, side));
        final TextView info = this.app.text("", 14, false);
        info.setTag("playlist-peek-info");
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, this.app.dp(6), 0, this.app.dp(2));
        col.addView(info, new LinearLayout.LayoutParams(-1, -2));
        // Not touchable: the finger's release still reaches the list, which closes it.
        final android.widget.PopupWindow pop = new android.widget.PopupWindow(col, side, -2, false);
        pop.setTouchable(false);
        pop.setOutsideTouchable(false);
        lastPeek = pop;
        lastPeekImage = image;
        lastPeekInfo = info;
        pop.showAtLocation(anchor.getRootView(), Gravity.CENTER, 0, 0);
        if (e.kind != MediaDir.PICTURE && e.kind != MediaDir.VIDEO) return;
        final Bitmap have = THUMBS.get(key(e));
        if (have != null) image.setImageBitmap(have);
        final int px = side;
        READER.execute(() -> {
            final Bitmap b = have != null ? null : this.thumb(e, px);
            final String line = this.info(e);
            this.main.post(() -> {
                if (lastPeek != pop || !pop.isShowing()) return;
                if (b != null) image.setImageBitmap(b);
                info.setText(line);
            });
        });
    }

    /** A video's length and resolution, a picture's dimensions and size in MB (MediaDir.info); empty when unknown. */
    String info(MediaDir.Entry e) {
        Uri u = this.uri(e);
        long bytes = e.size;
        try {
            if (e.kind == MediaDir.PICTURE) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                InputStream in = this.app.getContentResolver().openInputStream(u);
                try {
                    BitmapFactory.decodeStream(in, null, bounds);
                } finally {
                    if (in != null) in.close();
                }
                if (bytes <= 0 && this.onDisk(e)) bytes = new File(e.id).length();
                return MediaDir.info(MediaDir.PICTURE, bounds.outWidth, bounds.outHeight, 0, bytes);
            }
            if (e.kind == MediaDir.VIDEO) {
                long len = 0;
                int w = 0, h = 0;
                MediaMetadataRetriever media = new MediaMetadataRetriever();
                try {
                    if (this.onDisk(e)) media.setDataSource(e.id);
                    else media.setDataSource(this.app, u);
                    len = number(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    w = (int) number(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    h = (int) number(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                    long turn = number(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                    // A phone video filmed upright is stored on its side.
                    if (turn == 90 || turn == 270) {
                        int t = w;
                        w = h;
                        h = t;
                    }
                } catch (Exception ignored) {
                    // the file's own headers, below
                } finally {
                    try {
                        media.release();
                    } catch (Exception ignored) {}
                }
                if ((len <= 0 || w <= 0) && this.onDisk(e)) {
                    long[] head = MediaDir.mp4Info(new File(e.id));
                    if (head != null) {
                        if (len <= 0) len = head[0];
                        if (w <= 0) {
                            w = (int) head[1];
                            h = (int) head[2];
                        }
                    }
                }
                return MediaDir.info(MediaDir.VIDEO, w, h, len, bytes);
            }
        } catch (Throwable ignored) {
            // unknown
        }
        return "";
    }

    private static long number(String s) {
        try {
            return s == null ? 0 : Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    void unpeek() {
        if (lastPeek != null && lastPeek.isShowing()) lastPeek.dismiss();
    }

    /** The playlist window shown last, for the tests. */
    static AlertDialog lastPlaylist;

    /** This folder's default playlist: its files, in order; a tap plays or opens one as its thumbnail does. */
    void playlist() {
        final List<MediaDir.Entry> items = new ArrayList<MediaDir.Entry>();
        for (MediaPlaylist.Item it : MediaPlaylist.items(this.app.getFilesDir(), this.folderKey())) {
            MediaDir.Entry e = new MediaDir.Entry();
            e.id = it.id;
            e.name = it.name;
            e.size = it.size;
            e.kind = MediaDir.kind(it.name);
            items.add(e);
        }
        LinearLayout list = this.app.col();
        list.setPadding(this.app.dp(16), this.app.dp(6), this.app.dp(16), this.app.dp(6));
        for (int i = 0; i < items.size(); i++) {
            final MediaDir.Entry e = items.get(i);
            String what = e.kind == MediaDir.PICTURE ? "picture" : e.kind == MediaDir.VIDEO ? "video" : "sound";
            TextView row = this.app.text((i + 1) + ".  " + e.name + "   \u00b7 " + what, 15, false);
            row.setTag("playlist-item:" + e.name);
            row.setPadding(0, this.app.dp(10), 0, this.app.dp(10));
            row.setOnClickListener(v -> this.openEntry(e, items));
            // Holding it shows its thumbnail; letting go closes it (and opens nothing).
            row.setOnLongClickListener(v -> {
                this.peek(v, e);
                return true;
            });
            row.setOnTouchListener((v, ev) -> {
                int act = ev.getActionMasked();
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) this.unpeek();
                return false;
            });
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this.app);
        scroll.addView(list);
        // Save pls: this folder's playlist kept as a .pls with the playlists; Open in viewer: that, in the Playlist window.
        final String listName = Playlist.clean(this.label());
        lastPlaylist = new AlertDialog.Builder(this.app)
            .setTitle("Playlist \u00b7 " + this.label())
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("Save pls", (d, w) -> {
                final android.widget.EditText field = new android.widget.EditText(this.app);
                field.setSingleLine(true);
                field.setText(listName);
                field.setTag("media-playlist-pls-name");
                lastAsk = new AlertDialog.Builder(this.app).setTitle("Save pls").setView(field)
                    .setPositiveButton("Save", (d2, w2) -> {
                        String n = Playlist.clean(field.getText().toString());
                        if (n.length() > 0) this.app.setNow(this.savePls(n));
                    })
                    .setNegativeButton("Cancel", null).show();
            })
            .setNegativeButton("Open in viewer", (d, w) -> {
                this.app.setNow(this.savePls(listName));
                PlaylistScreen.show(this.app, listName);
            })
            .show();
    }

    /** The folder's entries, shown in order; null when it cannot be read. */
    List<MediaDir.Entry> list(String at) {
        if (DOWNLOADS.equals(at)) return this.downloads();
        List<MediaDir.Entry> all = new ArrayList<MediaDir.Entry>();
        if (this.tree == null) {
            File[] files = new File(at).listFiles();
            if (files == null) return null;
            for (File f : files) {
                if (f.isHidden()) continue;
                MediaDir.Entry e = new MediaDir.Entry();
                e.name = f.getName();
                e.id = f.getAbsolutePath();
                e.folder = f.isDirectory();
                e.size = e.folder ? 0 : f.length();
                // When it was last changed (seconds, as Download's dates): Sort by date.
                e.date = f.lastModified() / 1000;
                all.add(e);
            }
            return MediaDir.shown(all, this.chooser != null);
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(this.tree, at);
        String[] cols = new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        Cursor c = null;
        try {
            c = this.app.getContentResolver().query(children, cols, null, null, null);
            if (c == null) return null;
            while (c.moveToNext()) {
                MediaDir.Entry e = new MediaDir.Entry();
                e.id = c.getString(0);
                e.name = c.getString(1);
                e.folder = DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2));
                e.size = c.isNull(3) ? 0 : c.getLong(3);
                e.date = c.isNull(4) ? 0 : c.getLong(4) / 1000;
                all.add(e);
            }
        } catch (Exception ex) {
            return null;
        } finally {
            if (c != null) c.close();
        }
        return MediaDir.shown(all, this.chooser != null);
    }

    /** A file's address for the players and decoders. */
    /** A file's address in a playlist (.pls): its path, or its content:// address. */
    String playlistAddress(MediaDir.Entry e) {
        if (this.tree == null && !e.id.startsWith(MEDIA) && !e.id.startsWith(DL)) return e.id;
        return this.uri(e).toString();
    }

    /** Save pls: this folder's playlist as the named .pls with the playlists (replacing one of that name); the status line. */
    static final int IMPORT_FILES = 81;
    static final int IMPORT_FOLDER = 82;
    static final int COPY_FOLDER = 83;
    /** Import files from zip…: the zip picked. */
    static final int IMPORT_ZIP = 85;
    /** The browser an import goes into (the folder it shows). */
    static MediaBrowser importInto;

    /** The system's picker: files (Import file…) or a folder (Import folder…, Copy folder from…). */
    void pickImport(int code) {
        importInto = this;
        Intent intent;
        if (code == IMPORT_FILES || code == IMPORT_ZIP) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            if (code == IMPORT_FILES) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        }
        try {
            this.app.startActivityForResult(intent, code);
        } catch (Exception ex) {
            this.app.setNow("No picker on this phone: " + ex.getMessage());
        }
    }

    /** The picker's answer: the files (or the folder's files, or the whole folder) copied here, off the main thread. */
    static void imported(final MainActivity app, final int code, Intent intent) {
        final MediaBrowser to = importInto;
        importInto = null;
        if (to == null || intent == null) return;
        final List<Uri> files = new ArrayList<Uri>();
        if (code == IMPORT_FILES) {
            if (intent.getClipData() != null) for (int i = 0; i < intent.getClipData().getItemCount(); i++) files.add(intent.getClipData().getItemAt(i).getUri());
            else if (intent.getData() != null) files.add(intent.getData());
        }
        final Uri tree = code == IMPORT_FILES ? null : intent.getData();
        if (files.isEmpty() && tree == null) return;
        app.setNow("Copying into " + to.label() + "\u2026");
        new Thread(() -> {
            int[] done = new int[2];
            String failed = null;
            try {
                if (code == IMPORT_FILES) {
                    for (Uri u : files) to.importOne(u, DbImport.displayName(app, u), to.here(), done);
                } else {
                    String top = DocumentsContract.getTreeDocumentId(tree);
                    String into = to.here();
                    if (code == COPY_FOLDER) {
                        // The folder itself (and its folders) under this one.
                        into = to.folderIn(into, MediaDir.label(DocumentsContract.buildDocumentUriUsingTree(tree, top).toString()));
                        if (into == null) throw new java.io.IOException("could not make the folder here");
                    }
                    to.importTree(tree, top, into, code == COPY_FOLDER, done, 0);
                }
            } catch (Exception ex) {
                failed = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            final String why = failed;
            app.runOnUiThread(() -> {
                String said = "Copied " + done[0] + (done[0] == 1 ? " file" : " files") + " into " + to.label() + (done[1] > 0 ? " (" + done[1] + " could not be copied)" : "") + (why == null ? "" : ": " + why);
                lastImport = said;
                app.setNow(said);
                if (to.dialog != null && to.dialog.isShowing()) to.load(to.here());
            });
        }, "pulsekit-media-import").start();
    }

    /**
     * Send to zip: the file, or the folder with its folders, into "<name>.zip" here, with a password
     * when one is given (made in Pulsekit's temp folder first, then copied here).
     */
    void sendToZip(final MediaDir.Entry e) {
        ZipFlow.askPassword(this.app, e.name, password -> {
            this.app.setNow("Zipping " + e.name + "\u2026");
            new Thread(() -> {
                String text;
                File tmp = new File(this.app.getCacheDir(), "media-send.zip");
                try {
                    List<ZipPack.Source> sources = new ArrayList<ZipPack.Source>();
                    if (this.tree == null && !e.id.startsWith(MEDIA) && !e.id.startsWith(DL)) {
                        File f = new File(e.id);
                        if (f.isDirectory()) sources.addAll(ZipPack.folder(f));
                        else sources.add(new ZipPack.Source(e.name, f));
                    } else if (e.folder) {
                        this.treeSources(e.id, e.name, sources, 0);
                    } else {
                        final Uri u = this.uri(e);
                        sources.add(new ZipPack.Source(e.name, () -> this.app.getContentResolver().openInputStream(u), 0));
                    }
                    if (sources.isEmpty()) throw new java.io.IOException(e.name + " has no files");
                    ZipPack.write(tmp, sources, password);
                    int[] done = new int[2];
                    String name = ZipPack.zipName(e.name, e.folder);
                    this.importOne(Uri.fromFile(tmp), name, this.here(), done);
                    text = done[0] == 1 ? "Zipped " + e.name + " (" + sources.size() + (sources.size() == 1 ? " file" : " files") + (password != null ? ", with a password" : "")
                        + ") into " + name + " in " + this.label() : "Could not put " + name + " in " + this.label();
                } catch (Throwable ex) {
                    text = "Could not zip " + e.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                } finally {
                    tmp.delete();
                }
                final String shown = text;
                this.app.runOnUiThread(() -> {
                    ZipFlow.said(this.app, shown);
                    if (this.dialog != null && this.dialog.isShowing()) this.load(this.here());
                });
            }, "pulsekit-media-zip").start();
        });
    }

    /** A granted folder's files (and its folders') as zip sources under `prefix`. */
    private void treeSources(String doc, String prefix, List<ZipPack.Source> out, int depth) throws Exception {
        if (depth > 16) return;
        Cursor c = this.app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(this.tree, doc),
            new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null);
        List<String[]> kids = new ArrayList<String[]>();
        try {
            while (c != null && c.moveToNext()) kids.add(new String[] {c.getString(0), c.getString(1), c.getString(2), c.isNull(3) ? "0" : String.valueOf(c.getLong(3))});
        } finally {
            if (c != null) c.close();
        }
        for (String[] k : kids) {
            String name = k[1] == null ? "file" : k[1];
            if (name.startsWith(".")) continue;
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(k[2])) {
                this.treeSources(k[0], prefix + "/" + name, out, depth + 1);
                continue;
            }
            final Uri u = DocumentsContract.buildDocumentUriUsingTree(this.tree, k[0]);
            out.add(new ZipPack.Source(prefix + "/" + name, () -> this.app.getContentResolver().openInputStream(u), Long.parseLong(k[3])));
        }
    }

    /** What the last import said (for the tests). */
    static volatile String lastImport;

    /** The password Send folder contents to zip made last, and the folder zips' question shown last (for the tests). */
    static volatile String lastZipPassword;
    static AlertDialog lastZipAsk;

    /** This folder's name, for its zip. */
    String folderName() {
        String l = this.label();
        int slash = Math.max(l.lastIndexOf('/'), l.lastIndexOf(':'));
        return slash >= 0 && slash < l.length() - 1 ? l.substring(slash + 1) : l;
    }

    /** Send folder contents to zip: this folder's files (not its folders) into "<folder>.zip" here, with a password made and shown once. */
    void folderToZip() {
        final String zipName = FolderZip.zipFor(this.folderName());
        final String pw = FolderZip.password();
        this.app.setNow("Zipping the files of " + this.label() + "\u2026");
        new Thread(() -> {
            String text;
            boolean ok = false;
            File tmp = new File(this.app.getCacheDir(), "media-folder.zip");
            try {
                // Every file here (whatever the type letters show), not its folders.
                List<ZipPack.Source> sources = new ArrayList<ZipPack.Source>();
                if (this.tree != null) {
                    List<ZipPack.Source> all = new ArrayList<ZipPack.Source>();
                    this.treeSources(this.here(), "", all, 16);
                    for (ZipPack.Source src : all) if (!src.path.equals("/" + zipName)) sources.add(new ZipPack.Source(src.path.substring(1), src::open, 0));
                } else if (!this.here().startsWith(MEDIA) && !this.here().startsWith(DL)) {
                    sources.addAll(FolderZip.files(new File(this.here()), zipName));
                } else {
                    for (MediaDir.Entry e : this.allEntries) {
                        if (e.folder || e.dbId != 0 || e.name.startsWith(".") || e.name.equals(zipName)) continue;
                        final Uri u = this.uri(e);
                        sources.add(new ZipPack.Source(e.name, () -> this.app.getContentResolver().openInputStream(u), 0));
                    }
                }
                if (sources.isEmpty()) throw new java.io.IOException(this.label() + " has no files to zip");
                ZipPack.write(tmp, sources, pw.toCharArray());
                int[] done = new int[2];
                this.importOne(Uri.fromFile(tmp), zipName, this.here(), done);
                ok = done[0] == 1;
                text = ok ? FolderZip.sent(zipName, sources.size(), this.label()) : "Could not put " + zipName + " in " + this.label();
            } catch (Throwable ex) {
                text = "Could not zip " + this.label() + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                tmp.delete();
            }
            final String shown = text;
            final boolean made = ok;
            this.app.runOnUiThread(() -> {
                this.app.setNow(shown);
                lastImport = shown;
                if (this.dialog != null && this.dialog.isShowing()) this.load(this.here());
                if (!made) {
                    ZipFlow.said(this.app, shown);
                    return;
                }
                lastZipPassword = pw;
                this.showPassword(zipName, pw, shown);
            });
        }, "pulsekit-folder-zip").start();
    }

    /** The password made for a zip, to copy: Pulsekit does not keep it. */
    void showPassword(String zipName, final String pw, String said) {
        LinearLayout col = this.app.col();
        col.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
        col.addView(this.app.text(said + ".", 13, false));
        TextView field = this.app.text(pw, 20, true);
        field.setTextIsSelectable(true);
        field.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        field.setTag("media-zip-password");
        field.setPadding(0, this.app.dp(10), 0, this.app.dp(10));
        col.addView(field);
        col.addView(this.app.text(FolderZip.KEEP, 13, false));
        lastZipAsk = new AlertDialog.Builder(this.app).setTitle("Password for " + zipName).setView(col)
            .setPositiveButton("Copy password", (d, w) -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) this.app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Zip password", pw));
                this.app.setNow("The password is copied");
            })
            .setNegativeButton("Close", null).show();
    }

    /** Import files from zip…: the zip picked, copied into the temp folder, then its password and where its files go. */
    static void zipPicked(final MainActivity app, final Uri uri) {
        final MediaBrowser to = importInto;
        importInto = null;
        if (to == null) return;
        final String name = DbImport.displayName(app, uri);
        app.setNow("Reading " + name + "\u2026");
        new Thread(() -> {
            String failed = null;
            File tmp = new File(app.getCacheDir(), "media-zip-in.zip");
            List<ZipPack.Entry> list = null;
            try {
                InputStream in = app.getContentResolver().openInputStream(uri);
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
                    ZipFlow.said(app, "Could not read " + name + " (is it a zip?): " + why);
                    return;
                }
                to.zipOpened(tmp, name, got);
            });
        }, "pulsekit-folder-zip-read").start();
    }

    /** The zip read: its password asked when it has one, then where its files go. */
    void zipOpened(final File zip, final String name, final List<ZipPack.Entry> entries) {
        if (!ZipPack.encrypted(entries)) {
            this.zipWhere(zip, name, entries, null);
            return;
        }
        LinearLayout col = this.app.col();
        col.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
        col.addView(this.app.text(ZipPack.ASK_PASSWORD, 13, false));
        final android.widget.EditText pw = ZipFlow.passwordField(this.app, "Password", "media-zip-open-password");
        col.addView(pw);
        lastZipAsk = new AlertDialog.Builder(this.app).setTitle(name).setView(col)
            .setPositiveButton("Open", (d, w) -> {
                char[] typed = pw.getText().toString().trim().toCharArray();
                if (!ZipPack.passwordOk(zip, entries, typed)) {
                    ZipFlow.said(this.app, ZipPack.WRONG_PASSWORD + " " + name);
                    this.zipOpened(zip, name, entries);
                    return;
                }
                this.zipWhere(zip, name, entries, typed);
            })
            .setNegativeButton("Cancel", (d, w) -> this.app.setNow("Not imported: " + name)).show();
    }

    /** Into this folder, or into a folder named for the zip here (made, or added to). */
    void zipWhere(final File zip, final String name, final List<ZipPack.Entry> entries, final char[] password) {
        final String folder = FolderZip.folderFor(name);
        boolean there = false;
        for (MediaDir.Entry e : this.allEntries) if (e.folder && e.name.equals(folder)) there = true;
        final String[] where = {FolderZip.HERE, FolderZip.newFolder(folder, there)};
        lastZipAsk = new AlertDialog.Builder(this.app).setTitle(FolderZip.WHERE_TITLE + " \u00b7 " + ZipImport.extractQuestion(name, entries).split("\n")[0])
            .setItems(where, (d, w) -> this.zipExtract(zip, name, entries, password, w == 1 ? folder : null))
            .setNegativeButton("Cancel", (d, w) -> this.app.setNow("Not imported: " + name)).show();
    }

    /** The zip's files written here (or into `folder` here), its folders kept, off the main thread. */
    void zipExtract(final File zip, final String name, final List<ZipPack.Entry> entries, final char[] password, final String folder) {
        this.app.setNow("Extracting " + name + "\u2026");
        new Thread(() -> {
            String text;
            int[] done = new int[2];
            String into = this.here();
            String whereName = this.label();
            try {
                if (folder != null) {
                    into = this.folderFound(into, folder);
                    if (into == null) throw new java.io.IOException("could not make the folder " + folder);
                    whereName = folder;
                }
                java.util.Map<String, String> made = new java.util.HashMap<String, String>();
                File part = new File(this.app.getCacheDir(), "media-zip-part");
                for (ZipPack.Entry e : ZipPack.files(entries)) {
                    String[] parts = ZipPack.safePath(e.name).split("/");
                    String parent = into;
                    String key = "";
                    for (int k = 0; k < parts.length - 1 && parent != null; k++) {
                        key = key + "/" + parts[k];
                        String known = made.get(key);
                        if (known == null) {
                            known = this.folderFound(parent, parts[k]);
                            if (known != null) made.put(key, known);
                        }
                        parent = known;
                    }
                    if (parent == null) {
                        done[1]++;
                        continue;
                    }
                    ZipPack.extract(zip, e, password, part);
                    this.importOne(Uri.fromFile(part), parts[parts.length - 1], parent, done);
                    part.delete();
                }
                text = FolderZip.imported(name, done, whereName);
            } catch (Throwable ex) {
                text = "Could not import " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                zip.delete();
            }
            final String shown = text;
            this.app.runOnUiThread(() -> {
                ZipFlow.said(this.app, shown);
                lastImport = shown;
                if (this.dialog != null && this.dialog.isShowing()) this.load(this.here());
            });
        }, "pulsekit-folder-zip-extract").start();
    }

    /** The folder `name` in folder `in`: the one there, or made; its id, or null. */
    String folderFound(String in, String name) {
        if (this.tree == null) return this.folderIn(in, name);
        return ZipFlow.folderIn(this.app, this.tree, in, name);
    }

    /** A folder's files (and with `deep`, its folders, each made here) copied into `into`. */
    private void importTree(Uri tree, String doc, String into, boolean deep, int[] done, int depth) throws Exception {
        Cursor c = this.app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc),
            new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        List<String[]> kids = new ArrayList<String[]>();
        try {
            while (c != null && c.moveToNext()) kids.add(new String[] {c.getString(0), c.getString(1), c.getString(2)});
        } finally {
            if (c != null) c.close();
        }
        for (String[] k : kids) {
            String name = k[1] == null ? "file" : k[1];
            if (name.startsWith(".")) continue;
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(k[2])) {
                if (!deep || depth >= 8) continue;
                String sub = this.folderIn(into, name);
                if (sub != null) this.importTree(tree, k[0], sub, true, done, depth + 1);
                continue;
            }
            this.importOne(DocumentsContract.buildDocumentUriUsingTree(tree, k[0]), name, into, done);
        }
    }

    /** One file copied into folder `into`: first into Pulsekit's own temp folder, then to its place. */
    private void importOne(Uri from, String name, String into, int[] done) {
        File tmp = new File(this.app.getCacheDir(), "media-import.tmp");
        try {
            InputStream in = this.app.getContentResolver().openInputStream(from);
            if (in == null) throw new java.io.IOException("no data");
            try {
                java.nio.file.Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                in.close();
            }
            String clean = MediaDir.cleanName(name);
            if (clean == null) clean = "file";
            if (this.tree == null) {
                File dir = new File(into);
                File out = new File(dir, clean);
                String stem = clean.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
                String ext = clean.substring(stem.length());
                for (int n = 1; out.exists(); n++) out = new File(dir, stem + " (" + n + ")" + ext);
                java.nio.file.Files.copy(tmp.toPath(), out.toPath());
            } else {
                String mime = this.app.getContentResolver().getType(from);
                Uri out = DocumentsContract.createDocument(this.app.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(this.tree, into),
                    mime == null ? "application/octet-stream" : mime, clean);
                if (out == null) throw new java.io.IOException("could not write " + clean);
                java.io.OutputStream os = this.app.getContentResolver().openOutputStream(out);
                try {
                    java.nio.file.Files.copy(tmp.toPath(), os);
                } finally {
                    os.close();
                }
            }
            done[0]++;
        } catch (Exception ex) {
            done[1]++;
        } finally {
            tmp.delete();
        }
    }

    /** The folder `name` in folder `in` (made when it is not there): its id, or null. */
    private String folderIn(String in, String name) {
        String clean = MediaDir.cleanName(name);
        if (clean == null) clean = "folder";
        try {
            if (this.tree == null) {
                File f = new File(in, clean);
                return f.isDirectory() || f.mkdirs() ? f.getAbsolutePath() : null;
            }
            Uri made = DocumentsContract.createDocument(this.app.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(this.tree, in), DocumentsContract.Document.MIME_TYPE_DIR, clean);
            return made == null ? null : DocumentsContract.getDocumentId(made);
        } catch (Exception ex) {
            return null;
        }
    }

    /** The lists shown last (for the tests). */
    static AlertDialog lastFavourites, lastPlaylists, lastPlaylistMenu;

    /** ★ Favourites: the favourite folders; a tap opens one, held: its favourite files (F on). */
    void favouritesList() {
        final List<String> favs = new ArrayList<String>(MediaDir.favourites);
        String[] rows = new String[favs.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = this.favLabel(favs.get(i));
        AlertDialog.Builder b = new AlertDialog.Builder(this.app).setTitle(MediaDir.STAR + "Favourite folders").setNegativeButton("Cancel", null);
        if (rows.length == 0) b.setMessage("No favourite folders yet: Add to favourites in a folder");
        else b.setItems(rows, (d, w) -> this.openFavourite(favs.get(w), false));
        final AlertDialog shown = b.show();
        if (rows.length > 0) shown.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            shown.dismiss();
            this.openFavourite(favs.get(position), true);
            return true;
        });
        lastFavourites = shown;
    }

    /** A favourite folder opened in a browser of its own; with `onlyFavourites`, F on: its favourite files. */
    void openFavourite(String fav, boolean onlyFavourites) {
        String[] part = fav.split("\t");
        MediaBrowser b = make(this.app, part[0], false);
        if (b == null) return;
        for (int k = 1; k < part.length; k++) {
            if (!b.there(part[k])) break;
            b.path.add(part[k]);
        }
        b.root = b.base;
        FileFilter.use(FileFilter.MEDIA);
        if (onlyFavourites) FileFilter.fav = true;
        if (this.dialog != null) this.dialog.dismiss();
        last = b;
        b.show();
        if (onlyFavourites) this.app.setNow(MediaDir.STAR + "The favourite files in " + this.favLabel(fav) + " (F off: all of them)");
    }

    /** ☰ Playlists: the folders with files in their default playlist; a tap opens its playlist, held: Open, Save as. */
    void playlistsList() {
        final List<String[]> folders = MediaPlaylist.folders(this.app.getFilesDir());
        String[] rows = new String[folders.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = MediaPlaylist.folderLabel(folders.get(i)[0]) + " (" + folders.get(i)[1] + ")";
        AlertDialog.Builder b = new AlertDialog.Builder(this.app).setTitle("\u2630 Folder playlists").setNegativeButton("Cancel", null);
        if (rows.length == 0) b.setMessage("No folder has a playlist yet: Add to default playlist on a file");
        else b.setItems(rows, (d, w) -> {
            MediaBrowser at = this.openKey(folders.get(w)[0]);
            if (at != null) at.playlist();
        });
        final AlertDialog shown = b.show();
        if (rows.length > 0) shown.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
            final String key = folders.get(position)[0];
            final String name = Playlist.clean(MediaPlaylist.folderLabel(key));
            lastPlaylistMenu = new AlertDialog.Builder(this.app).setTitle(name)
                .setItems(new CharSequence[] {"Open", "Save as\u2026"}, (d, w) -> {
                    MediaBrowser there = this.browserAt(key);
                    if (there == null) return;
                    this.app.setNow(there.savePls(name));
                    shown.dismiss();
                    // Open: in the Playlist window (Editor, Viewer); Save as: a .pls on disk.
                    if (w == 0) PlaylistScreen.show(this.app, name);
                    else {
                        // (The picker's answer writes the playlist shown.)
                        PlaylistScreen.shown = Playlist.load(this.app.getFilesDir(), name);
                        PlaylistScreen.saveAs(this.app, PlaylistScreen.shown);
                    }
                })
                .setNegativeButton("Cancel", null).show();
            return true;
        });
        lastPlaylists = shown;
    }

    /** A browser (not shown) on the folder a playlist key names: a path, or a granted folder "tree|document". */
    MediaBrowser browserAt(String key) {
        int bar = key.indexOf('|');
        if (bar < 0) return make(this.app, key, false);
        MediaBrowser b = make(this.app, key.substring(0, bar), false);
        if (b == null) return null;
        String doc = key.substring(bar + 1);
        String top = b.path.get(0);
        if (!doc.equals(top) && doc.startsWith(top)) {
            String rest = doc.substring(top.length());
            if (rest.startsWith("/")) rest = rest.substring(1);
            String at = top;
            for (String part : rest.split("/")) {
                if (part.length() == 0) continue;
                at = at.endsWith(":") || at.endsWith("/") ? at + part : at + "/" + part;
                b.path.add(at);
            }
        }
        b.root = b.base;
        return b;
    }

    /** The folder a playlist key names, opened in place of this browser. */
    MediaBrowser openKey(String key) {
        MediaBrowser b = this.browserAt(key);
        if (b == null) return null;
        if (this.dialog != null) this.dialog.dismiss();
        last = b;
        b.show();
        return b;
    }

    String savePls(String name) {
        Playlist p = new Playlist(name);
        for (MediaPlaylist.Item it : MediaPlaylist.items(this.app.getFilesDir(), this.folderKey())) {
            MediaDir.Entry e = new MediaDir.Entry();
            e.id = it.id;
            e.name = it.name;
            p.add(this.playlistAddress(e), it.name);
        }
        try {
            Playlist.save(this.app.getFilesDir(), p);
            return "Saved " + p.name + Playlist.EXT + " (" + p.items.size() + (p.items.size() == 1 ? " file)" : " files)") + ": File > Playlist viewer/editor";
        } catch (Exception ex) {
            return "Could not save " + p.name + Playlist.EXT + ": " + ex.getMessage();
        }
    }

    Uri uri(MediaDir.Entry e) {
        if (e.id.startsWith(MEDIA)) {
            String[] part = e.id.substring(MEDIA.length()).split(":");
            Uri kind = "0".equals(part[0]) ? android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                : "1".equals(part[0]) ? android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI : android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            return android.content.ContentUris.withAppendedId(kind, Long.parseLong(part[1]));
        }
        if (e.id.startsWith(DL)) return android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, Long.parseLong(e.id.substring(DL.length())));
        return this.tree == null ? Uri.fromFile(new File(e.id)) : DocumentsContract.buildDocumentUriUsingTree(this.tree, e.id);
    }

    private String label() {
        if (DOWNLOADS.equals(here())) return "Download";
        if (this.tree == null) return MediaDir.label(here());
        Uri doc = DocumentsContract.buildDocumentUriUsingTree(this.tree, here());
        String shown = MediaDir.label(doc.toString());
        // A folder known by a number (Download's own folders: "1000180279"): its name instead.
        if (!shown.matches(".*[^0-9].*")) {
            String name = this.docName(doc);
            if (name != null && name.length() > 0) return name;
        }
        return shown;
    }

    /** A favourite's name: its folder's, also for one known by a number (Download's own folders). */
    String favLabel(String fav) {
        String shown = MediaDir.favouriteLabel(fav);
        String[] part = fav.split("\t");
        if (shown.matches(".*[^0-9].*") || part.length < 2 || !part[0].startsWith("content://")) return shown;
        try {
            String name = this.docName(DocumentsContract.buildDocumentUriUsingTree(Uri.parse(part[0]), part[part.length - 1]));
            return name == null || name.length() == 0 ? shown : name;
        } catch (Exception ex) {
            return shown;
        }
    }

    /** The folders' names asked of Android, by address (a folder known by a number). */
    private static final java.util.Map<String, String> DOC_NAMES = new java.util.concurrent.ConcurrentHashMap<String, String>();

    /** A document's own name (DISPLAY_NAME), or null. */
    String docName(Uri doc) {
        String kept = DOC_NAMES.get(doc.toString());
        if (kept != null) return kept;
        Cursor c = null;
        try {
            c = this.app.getContentResolver().query(doc, new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst() && c.getString(0) != null) {
                DOC_NAMES.put(doc.toString(), c.getString(0));
                return c.getString(0);
            }
        } catch (Exception ignored) {
            // the address as it is
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private void show() {
        LinearLayout col = this.app.col();
        int pad = this.app.dp(10);
        col.setPadding(pad, pad, pad, pad);
        this.body = this.app.col();
        ScrollView scroll = new ScrollView(this.app);
        scroll.addView(this.body);
        this.scroll = scroll;
        col.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        this.dialog = new AlertDialog.Builder(this.app)
            .setTitle("Media browser")
            .setView(col)
            .setPositiveButton("Close", null)
            .create();
        this.dialog.setOnDismissListener(d -> {
            this.rememberScroll();
            this.token++;
            if (last == this) last = null;
        });
        this.dialog.show();
        this.load(here());
    }

    /** Opens folder `at` (already on the path) at its first page. */
    void load(String at) {
        // Remembered: this folder, for the next time the Media browser opens on the same one.
        if (this.root != null) {
            MediaDir.remember(this.root, this.path.subList(1, this.path.size()));
            save(this.app);
        }
        // Read off the main thread: a folder of thousands of files took long enough for the phone to
        // stop the app ("not responding"). The page it was last on comes back.
        final int mine = ++this.listing;
        this.entries = new ArrayList<MediaDir.Entry>();
        this.allEntries = this.entries;
        this.loading = true;
        this.paint();
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            List<MediaDir.Entry> read;
            try {
                read = this.list(at);
            } catch (Throwable ex) {
                read = null;
            }
            // The library's prompt files (Prompt files), under T with a star.
            if (read != null) {
                try {
                    read = MediaDir.withPromptFiles(read, PromptVault.open(this.app.getFilesDir()));
                } catch (Throwable ex) {
                    // No library: the folder only.
                }
            }
            final List<MediaDir.Entry> got = read;
            this.main.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                if (mine != this.listing) return;
                this.loading = false;
                this.allEntries = got == null ? new ArrayList<MediaDir.Entry>() : got;
                this.entries = this.shownEntries();
                int pages = Math.max(1, (this.entries.size() + PAGE - 1) / PAGE);
                this.page = Math.max(0, Math.min(pages - 1, MediaDir.place(this.folderKey())));
                // Up from a folder: the page with its card.
                this.focusId = CAME_FROM.get(this.folderKey());
                for (int i = 0; this.focusId != null && i < this.entries.size(); i++) {
                    if (this.focusId.equals(this.entries.get(i).id)) this.page = i / PAGE;
                }
                if (got == null) this.app.setNow("Could not read " + this.label() + ": pick it again with Browse");
                this.paint();
                // Where in the page it was left (kept on leaving it for a folder in it, and on closing).
                final int y = MediaDir.place(this.scrollKey());
                if (y > 0) this.main.post(() -> {
                    if (this.scroll != null) this.scroll.scrollTo(0, y);
                });
            });
        }, "pulsekit-media-list").start();
    }

    /** Everything in the folder; `entries` is what the type selector (A I V S T O) keeps of it. */
    List<MediaDir.Entry> allEntries = new ArrayList<MediaDir.Entry>();

    /** The type selectors: A all, I pictures, V videos, S sounds, T text, O other files. */
    private void filters() {
        FileGroups.load(this.app.getFilesDir());
        FileFavourites.load(this.app.getFilesDir());
        LinearLayout row = this.app.row();
        FileFilter.use(FileFilter.MEDIA);
        MediaDir.filter = AppSettings.letter(FileFilter.normal(MediaDir.filter));
        for (int i = 0; i < MediaDir.FILTERS.length; i++) {
            final String f = MediaDir.FILTERS[i];
            // F G C come after; a type General settings leaves out has no letter.
            if (FileFilter.isToggle(f) || !AppSettings.shows(f)) continue;
            int n = MediaDir.count(this.allEntries, f);
            TextView pill = this.app.pill(f, f.equals(MediaDir.filter), v -> {
                MediaDir.filter = f;
                this.refilter();
            });
            pill.setTag("media-filter:" + f);
            pill.setContentDescription(MediaDir.FILTER_NAMES[i] + " (" + n + ")");
            if (n == 0 && !f.equals("A")) pill.setAlpha(0.45f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(6);
            row.addView(pill, lp);
        }
        // F G C: switches with the letter (held: G the groups, C the categories).
        FilterRow.toggles(this.app, row, "media-filter:", MediaDir.filter, (text, on) -> this.app.pill(text, on, null), () -> {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(6);
            return lp;
        }, this::refilter);
        TextView legend = this.app.text(MediaDir.FILTER_NAMES[java.util.Arrays.asList(MediaDir.FILTERS).indexOf(MediaDir.filter)]
            + FileFilter.title().replace(" \u00b7 Group: ", " \u00b7 group ").replace(" \u00b7 ", ", "), 12, false);
        legend.setTextColor(UiKit.MUTED);
        legend.setTag("media-filter-name");
        // One line: wrapped in the row, it made the row tall (large gaps above and below the letters).
        legend.setSingleLine(true);
        row.addView(legend);
        // Tagged: only the files with a tag or a category (Tags… and Category… in a file's menu).
        TextView tagged = this.app.pill(MediaTags.filter.length() == 0 ? "Tagged \u25be" : MediaTags.filter + " \u25be", MediaTags.filter.length() > 0, v -> this.tagFilter());
        tagged.setTag("media-tag-filter");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = this.app.dp(8);
        row.addView(tagged, tlp);
        // The letters, the line saying what is shown and Tagged: one row, scrolled sideways when it is wider.
        android.widget.HorizontalScrollView letters = new android.widget.HorizontalScrollView(this.app);
        letters.setHorizontalScrollBarEnabled(false);
        letters.addView(row);
        letters.setPadding(0, 0, 0, this.app.dp(4));
        this.body.addView(letters);
        // T1 T2 T3: the files with any of the tags picked.
        this.body.addView(FilterRow.tags(this.app, "media-filter:", (text, on) -> this.app.pill(text, on, null), () -> {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(6);
            return lp;
        }, this::refilter));
        // Sort by (name, date, type), Order, and ⌕ Search (a name with * and ?, and a type).
        LinearLayout sorts = this.app.row();
        TextView sortBy = this.app.pill("Sort: " + MediaDir.sortBy + " \u25be", false, v -> {
            lastAsk = new AlertDialog.Builder(this.app).setTitle("Sort by").setItems(MediaDir.SORTS, (d, w) -> {
                MediaDir.sortBy = MediaDir.SORTS[w];
                // By date the newest first at first.
                MediaDir.sortDesc = MediaDir.SORT_DATE.equals(MediaDir.sortBy);
                save(this.app);
                this.refilter();
            }).setNegativeButton("Cancel", null).show();
        });
        sortBy.setTag("media-sort");
        sorts.addView(sortBy);
        TextView order = this.app.pill(MediaDir.orderLabel(MediaDir.sortBy, MediaDir.sortDesc), false, v -> {
            MediaDir.sortDesc = !MediaDir.sortDesc;
            save(this.app);
            this.refilter();
        });
        order.setTag("media-order");
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.leftMargin = this.app.dp(6);
        sorts.addView(order, olp);
        boolean searching = this.search.length() > 0 || !"A".equals(this.searchType);
        TextView find = this.app.pill(searching ? "\uD83D\uDD0D " + (this.search.length() > 0 ? this.search : "") + ("A".equals(this.searchType) ? "" : " (" + this.searchType + ")") : "\uD83D\uDD0D", searching, v -> this.searchDialog());
        find.setTag("media-search");
        sorts.addView(find, olp);
        if (searching) {
            TextView clear = this.app.pill("\u2715", false, v -> {
                this.search = "";
                this.searchType = "A";
                this.refilter();
            });
            clear.setTag("media-search-clear");
            sorts.addView(clear, olp);
        }
        android.widget.HorizontalScrollView sortScroll = new android.widget.HorizontalScrollView(this.app);
        sortScroll.setHorizontalScrollBarEnabled(false);
        sortScroll.addView(sorts);
        sortScroll.setPadding(0, this.app.dp(4), 0, this.app.dp(2));
        this.body.addView(sortScroll);
        // Select multiple: how many are ticked, Select all, None, Done.
        if (this.selecting) {
            LinearLayout sel = this.app.row();
            TextView count = this.app.text(this.selected.size() + " selected \u00b7 hold a ticked one for its menu", 12, false);
            count.setTag("media-selection-count");
            sel.addView(count, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView all = this.app.pill("All", false, v -> {
                for (MediaDir.Entry e : this.entries) if (!e.folder && e.dbId == 0) this.selected.add(e.id);
                this.paint();
            });
            all.setTag("media-select-all");
            sel.addView(all);
            TextView none = this.app.pill("None", false, v -> {
                this.selected.clear();
                this.paint();
            });
            none.setTag("media-select-none");
            sel.addView(none, olp);
            TextView done = this.app.pill("Done", true, v -> {
                this.selecting = false;
                this.selected.clear();
                this.paint();
            });
            done.setTag("media-select-done");
            sel.addView(done, olp);
            this.body.addView(sel);
        }
    }

    /** Search (⌕): a name (* any letters, ? one), and a type. */
    String search = "";
    String searchType = "A";

    /** Select multiple: on, and the files ticked (their ids). */
    boolean selecting;
    final java.util.Set<String> selected = new java.util.LinkedHashSet<String>();

    void searchDialog() {
        LinearLayout box = this.app.col();
        box.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
        final android.widget.EditText field = new android.widget.EditText(this.app);
        field.setSingleLine(true);
        field.setHint("Name: sunset, img_2026*, ?.png");
        field.setText(this.search);
        field.setTag("media-search-field");
        box.addView(field);
        TextView note = this.app.text("* is any letters, ? one letter; without them, names holding the text. Case aside.", 12, false);
        note.setTextColor(UiKit.MUTED);
        box.addView(note);
        LinearLayout types = this.app.row();
        final String[] picked = {this.searchType};
        final String[][] kinds = {{"A", "All"}, {"I", "Pictures"}, {"V", "Videos"}, {"S", "Sounds"}, {"T", "Text"}, {"O", "Other"}};
        final List<TextView> pills = new ArrayList<TextView>();
        for (final String[] k : kinds) {
            TextView pill = this.app.pill(k[1], k[0].equals(picked[0]), null);
            pill.setTag("media-search-type:" + k[0]);
            pill.setOnClickListener(v -> {
                picked[0] = k[0];
                for (int i = 0; i < pills.size(); i++) this.app.paintChip(pills.get(i), kinds[i][0].equals(picked[0]));
            });
            pills.add(pill);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(4);
            types.addView(pill, lp);
        }
        android.widget.HorizontalScrollView ts = new android.widget.HorizontalScrollView(this.app);
        ts.addView(types);
        ts.setPadding(0, this.app.dp(6), 0, 0);
        box.addView(ts);
        lastAsk = new AlertDialog.Builder(this.app).setTitle("Search").setView(box)
            .setPositiveButton("Search", (d, w) -> {
                this.search = field.getText().toString().trim();
                this.searchType = picked[0];
                this.refilter();
                this.app.setNow(this.entries.size() + (this.entries.size() == 1 ? " found" : " found") + (this.search.length() > 0 ? " for " + this.search : ""));
            })
            .setNeutralButton("Clear", (d, w) -> {
                this.search = "";
                this.searchType = "A";
                this.refilter();
            })
            .setNegativeButton("Cancel", null).show();
    }

    /** The files ticked, as entries (those still listed). */
    List<MediaDir.Entry> selectedEntries() {
        List<MediaDir.Entry> out = new ArrayList<MediaDir.Entry>();
        for (MediaDir.Entry e : this.allEntries) if (this.selected.contains(e.id)) out.add(e);
        return out;
    }

    static final String REMOVE_SELECTED = "Remove selected", MOVE_SELECTED = "Move selected to\u2026";

    /** A ticked file held: Remove selected (asked first), Move selected to…, Clear selection. */
    void selectionMenu() {
        final List<MediaDir.Entry> picked = this.selectedEntries();
        final String[] items = {REMOVE_SELECTED + " (" + picked.size() + ")", MOVE_SELECTED, "Clear selection"};
        lastMenu = new AlertDialog.Builder(this.app).setTitle(picked.size() + (picked.size() == 1 ? " file selected" : " files selected")).setItems(items, (d, w) -> {
            if (w == 0) this.removeSelected(picked);
            else if (w == 1) this.moveSelected(picked);
            else {
                this.selected.clear();
                this.paint();
            }
        }).setNegativeButton("Cancel", null).show();
    }

    void removeSelected(final List<MediaDir.Entry> picked) {
        lastAsk = new AlertDialog.Builder(this.app).setTitle(REMOVE_SELECTED)
            .setMessage("Delete these " + picked.size() + (picked.size() == 1 ? " file" : " files") + "? They cannot be brought back.")
            .setPositiveButton("Delete", (d, w) -> this.eachSelected(picked, e -> {
                String said = this.delete(e);
                if (said.startsWith("Deleted")) MediaTags.deleted(MediaTags.key(e));
                return said.startsWith("Deleted");
            }, "Deleted"))
            .setNegativeButton("Cancel", null).show();
    }

    void moveSelected(final List<MediaDir.Entry> picked) {
        final List<String> ids = new ArrayList<String>();
        final List<String> names = new ArrayList<String>();
        if (this.path.size() > 1) {
            ids.add(this.path.get(this.path.size() - 2));
            names.add("\u2191 Up one folder");
        }
        for (MediaDir.Entry f : this.allEntries) {
            if (f.folder) {
                ids.add(f.id);
                names.add(f.name);
            }
        }
        names.add("New folder\u2026");
        lastAsk = new AlertDialog.Builder(this.app).setTitle("Move " + picked.size() + (picked.size() == 1 ? " file" : " files") + " to")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                if (which < ids.size()) {
                    final String to = ids.get(which);
                    this.eachSelected(picked, e -> this.copyTo(e, to, true).startsWith("Moved"), "Moved");
                    return;
                }
                final android.widget.EditText field = new android.widget.EditText(this.app);
                field.setSingleLine(true);
                field.setTag("media-newfolder-field");
                lastAsk = new AlertDialog.Builder(this.app).setTitle("New folder").setView(field)
                    .setPositiveButton("Move", (d2, w2) -> {
                        final String made = this.makeFolder(field.getText().toString());
                        if (made == null) this.done("Could not make the folder");
                        else this.eachSelected(picked, e -> this.copyTo(e, made, true).startsWith("Moved"), "Moved");
                    })
                    .setNegativeButton("Cancel", null).show();
            })
            .setNegativeButton("Cancel", null).show();
    }

    interface EachFile {
        boolean run(MediaDir.Entry e);
    }

    /** What Remove selected / Move selected said last (for the tests). */
    static volatile String lastSelectionSaid;

    /** Each ticked file done in turn (off the main thread); the selection cleared, the folder read again. */
    void eachSelected(final List<MediaDir.Entry> picked, final EachFile doing, final String verb) {
        this.app.setNow(verb.equals("Deleted") ? "Deleting\u2026" : "Moving\u2026");
        new Thread(() -> {
            int ok = 0;
            for (MediaDir.Entry e : picked) {
                try {
                    if (doing.run(e)) ok++;
                } catch (Throwable ignored) {
                    // counted as not done
                }
            }
            final String said = verb + " " + ok + (ok == 1 ? " file" : " files") + (ok < picked.size() ? "; " + (picked.size() - ok) + " could not be" : "");
            lastSelectionSaid = said;
            this.main.post(() -> {
                this.selected.clear();
                this.done(said);
            });
        }, "pulsekit-selected").start();
    }

    /** The type selector (or G's group) picked: the folder shown again with what it keeps. */
    void refilter() {
        this.entries = this.shownEntries();
        this.page = 0;
        this.paint();
    }

    /** The folder's files the type selector and the tag filter keep. */
    List<MediaDir.Entry> shownEntries() {
        MediaTags.load(this.app.getFilesDir());
        FileGroups.load(this.app.getFilesDir());
        FileFavourites.load(this.app.getFilesDir());
        FileFilter.use(FileFilter.MEDIA);
        List<MediaDir.Entry> kept = FileFilter.entries(MediaTags.filtered(MediaDir.filtered(this.allEntries, MediaDir.filter), MediaTags.filter));
        // Search (⌕), then Sort by and Order.
        if (this.search.length() > 0 || !"A".equals(this.searchType)) kept = MediaDir.searched(kept, this.search, this.searchType);
        return MediaDir.sortedBy(kept, MediaDir.sortBy, MediaDir.sortDesc);
    }

    /** The tag filter's choices for this folder: All files, each tag and category its files have. */
    void tagFilter() {
        MediaTags.load(this.app.getFilesDir());
        final List<String[]> choices = MediaTags.choices(this.allEntries);
        String[] names = new String[choices.size()];
        for (int i = 0; i < names.length; i++) names[i] = choices.get(i)[0];
        lastAsk = new AlertDialog.Builder(this.app).setTitle("Show the files tagged")
            .setItems(names, (d, which) -> {
                MediaTags.filter = choices.get(which)[1];
                this.entries = this.shownEntries();
                this.page = 0;
                this.paint();
            })
            .setNegativeButton("Cancel", null).show();
    }

    /** Tags…: the file's tags, ticked from those made so far; New tag… adds one (TagDialogs). */
    void tags(final MediaDir.Entry e) {
        TagDialogs.tags(this.app, e.name, e.size, () -> {
            this.entries = this.shownEntries();
            this.paint();
        });
    }

    /** Category…: one of the prompt library's categories for the file, or none (TagDialogs). */
    void category(final MediaDir.Entry e) {
        TagDialogs.category(this.app, e.name, e.size, () -> {
            this.entries = this.shownEntries();
            this.paint();
        });
    }

    /** The file being dragged onto a folder card (press, hold and move), or null. */
    static MediaDir.Entry dragging;

    /** False: a long press only opens the menu (the tests, where a drag never ends). */
    static boolean drags = true;

    /** Starts dragging a file card; false when the phone does not drag (its menu shows instead). */
    boolean startDrag(View card, MediaDir.Entry e) {
        if (!drags) return false;
        try {
            dragging = e;
            boolean started = card.startDragAndDrop(android.content.ClipData.newPlainText("media", e.name), new View.DragShadowBuilder(card), e, 0);
            if (!started) dragging = null;
            return started;
        } catch (Throwable ex) {
            dragging = null;
            return false;
        }
    }

    /** A folder card (or Up) a file can be dropped on: moved into `folderId` (an id as `path` holds). */
    void dropTarget(final View target, final String folderId) {
        target.setOnDragListener((v, ev) -> {
            switch (ev.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return ev.getLocalState() instanceof MediaDir.Entry;
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.6f);
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f);
                    return true;
                case android.view.DragEvent.ACTION_DROP:
                    v.setAlpha(1f);
                    MediaDir.Entry moved = (MediaDir.Entry) ev.getLocalState();
                    dragging = null;
                    this.main.post(() -> this.done(this.dropped(moved, folderId)));
                    return true;
                default:
                    return true;
            }
        });
    }

    /** A file dropped on a folder: moved into it. The status line. */
    String dropped(MediaDir.Entry e, String folderId) {
        if (e == null || e.folder || folderId == null) return "Not moved";
        return this.copyTo(e, folderId, true);
    }

    /** True while the folder is being read ("Reading the folder…"); bumped for each folder read, so an older read is dropped. */
    boolean loading;
    private int listing;

    void openFolder(MediaDir.Entry e) {
        // Back (Up) comes to this folder's card again: its page, scrolled to and outlined.
        CAME_FROM.put(this.folderKey(), e.id);
        this.rememberScroll();
        this.path.add(e.id);
        this.load(e.id);
    }

    /** The folder opened last from each folder (by folderKey): Up shows its card again. */
    static final java.util.Map<String, String> CAME_FROM = new java.util.HashMap<String, String>();
    /** The card to show again (a folder Up came from), or null. */
    String focusId;
    ScrollView scroll;

    void up() {
        if (this.path.size() <= 1) {
            this.above();
            return;
        }
        this.path.remove(this.path.size() - 1);
        this.load(here());
    }

    /** The folder above the one this browser started in: a folder by path, or a granted folder another grant holds. */
    private Object[] aboveRoot() {
        if (this.chooser != null || DOWNLOADS.equals(this.path.get(0))) return null;
        if (this.tree == null) {
            File parent = new File(this.path.get(0)).getParentFile();
            return parent != null && parent.isDirectory() && parent.canRead() ? new Object[] {parent.getAbsolutePath()} : null;
        }
        // A granted folder: Android lets the app read above it only through a grant of a higher folder.
        String doc = this.path.get(0);
        int cut = Math.max(doc.lastIndexOf('/'), doc.indexOf(':'));
        if (cut < 0 || cut == doc.length() - 1) return null;
        String parentDoc = doc.lastIndexOf('/') > doc.indexOf(':') ? doc.substring(0, doc.lastIndexOf('/')) : doc.substring(0, doc.indexOf(':') + 1);
        try {
            for (android.content.UriPermission g : this.app.getContentResolver().getPersistedUriPermissions()) {
                if (!g.isReadPermission()) continue;
                Uri u = g.getUri();
                String top;
                try {
                    top = DocumentsContract.getTreeDocumentId(u);
                } catch (Exception ex) {
                    continue;
                }
                if (!u.getAuthority().equals(this.tree.getAuthority())) continue;
                boolean holds = parentDoc.equals(top) || parentDoc.startsWith(top.endsWith(":") || top.endsWith("/") ? top : top + "/");
                if (holds) return new Object[] {u.toString(), top, parentDoc};
            }
        } catch (Exception ignored) {
            // no grants to look at
        }
        return null;
    }

    /** Up's question at a folder Android allows no higher (for the tests). */
    static android.app.AlertDialog lastAbove;

    /** Up at the first folder: the browser again on the folder above it (the folder it came from shown under it). */
    private void above() {
        Object[] to = this.aboveRoot();
        if (to == null) {
            // Android lets Pulsekit read only the folders picked: another one, picked with the system's picker.
            final MainActivity host = this.app;
            lastAbove = new android.app.AlertDialog.Builder(host).setTitle("Up")
                .setMessage("Android lets Pulsekit read only the folders picked. Pick a folder (a higher one, or any other) to browse there.")
                .setPositiveButton("Pick a folder", (d, w) -> {
                    PyJavParams.pendingValues = null;
                    PyJavParams.pendingOpensMedia = true;
                    if (this.dialog != null) this.dialog.dismiss();
                    pick(host);
                })
                .setNegativeButton("Cancel", null).show();
            return;
        }
        MediaBrowser b = make(this.app, (String) to[0], false);
        if (b == null) return;
        if (to.length == 3) {
            // From the granted top down to the folder above.
            String top = (String) to[1];
            String parentDoc = (String) to[2];
            if (!parentDoc.equals(top)) {
                String rest = parentDoc.substring(top.length());
                if (rest.startsWith("/")) rest = rest.substring(1);
                String at = top;
                for (String part : rest.split("/")) {
                    if (part.length() == 0) continue;
                    at = at.endsWith(":") || at.endsWith("/") ? at + part : at + "/" + part;
                    b.path.add(at);
                }
            }
        }
        b.root = b.base;
        if (this.dialog != null) this.dialog.dismiss();
        last = b;
        b.show();
    }

    void showPage(int p) {
        int pages = Math.max(1, (this.entries.size() + PAGE - 1) / PAGE);
        this.page = Math.max(0, Math.min(pages - 1, p));
        // Remembered: this folder's page, for the next time it opens.
        MediaDir.place(this.folderKey(), this.page);
        MediaDir.place(this.scrollKey(), 0);
        save(this.app);
        this.paint();
    }

    /** The heading, Up, the page's cards in rows of three, and the page buttons. */
    private void paint() {
        final int mine = ++this.token;
        this.body.removeAllViews();
        if (this.dialog != null) this.dialog.setTitle("Media browser · " + this.label());
        TextView summary = this.app.text(MediaDir.summary(this.entries), 13, false);
        summary.setTag("media-summary");
        summary.setTextColor(UiKit.MUTED);
        summary.setPadding(0, 0, 0, this.app.dp(2));
        this.body.addView(summary);
        android.widget.CheckBox loop = new android.widget.CheckBox(this.app);
        loop.setText("Loop videos");
        loop.setTag("media-loop");
        loop.setTextColor(UiKit.FG);
        loop.setChecked(MediaDir.loopVideos);
        loop.setOnCheckedChangeListener((x, on) -> {
            MediaDir.loopVideos = on;
            save(this.app);
        });
        // Playlist (n) beside it, once this folder's playlist has a file.
        LinearLayout loopRow = this.app.row();
        loopRow.addView(loop, new LinearLayout.LayoutParams(0, -2, 1f));
        this.playlistButton = this.app.pill("Playlist", false, v -> this.playlist());
        this.playlistButton.setTag("media-playlist");
        loopRow.addView(this.playlistButton);
        this.body.addView(loopRow);
        this.paintPlaylist();
        if (this.chooser != null) this.tabs();
        if (!this.loading) this.filters();
        // Download (or a folder by its path) without the phone's permission to read other apps' files:
        // only Pulsekit's own show (on some phones a folder then shows only its folders).
        if ((DOWNLOADS.equals(here()) || this.tree == null) && !mediaAllowed(this.app)) {
            TextView ask = this.app.pill("Show files other apps saved here", false, v -> askMedia(this.app));
            ask.setTag("media-ask-permission");
            this.body.addView(ask);
        }
        LinearLayout tools = this.app.row();
        // Up stays at the folder Params gave too (.bck, say): it goes on above it where the phone allows,
        // else it picks another folder with the system's picker.
        if (this.path.size() > 1 || this.chooser == null) {
            TextView up = this.app.pill("Up", false, v -> this.up());
            up.setTag("media-up");
            if (this.path.size() > 1 && this.changeable()) this.dropTarget(up, this.path.get(this.path.size() - 2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(6);
            tools.addView(up, lp);
        }
        // Add to favourites: this folder, as a tab in Choose file (★ In favourites when it is one).
        if (this.base != null && !DOWNLOADS.equals(here())) {
            final boolean already = MediaDir.isFavourite(this.base, this.path.subList(1, this.path.size()));
            TextView fav = this.app.pill(already ? MediaDir.STAR + "In favourites" : "Add to favourites", already, v -> {
                boolean added = MediaDir.addFavourite(this.base, this.path.subList(1, this.path.size()));
                save(this.app);
                String said = added ? this.label() + " added to favourites: a tab in Choose file" : this.label() + " is in favourites already";
                this.app.setNow(said);
                android.widget.Toast.makeText(this.app, said, android.widget.Toast.LENGTH_SHORT).show();
                if (added) this.paint();
            });
            fav.setTag("media-favourite");
            tools.addView(fav);
        }
        // File: Import file…, Import folder…, Import files from zip…, Send folder contents to zip, Copy
        // folder from… (a folder that can be changed), and Move defined media types.
        if (this.chooser == null) {
            TextView file = this.app.pill(FILE_MENU + " \u25be", false, v -> this.fileMenu());
            file.setTag("media-file-menu");
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
            mlp.leftMargin = this.app.dp(6);
            tools.addView(file, mlp);
        }
        if (tools.getChildCount() > 0) this.body.addView(tools);
        tools = this.app.row();
        // The favourite folders, and the folders with a playlist: each opens here.
        if (this.chooser == null) {
            TextView favs = this.app.pill(MediaDir.STAR + "Favourites \u25be", false, v -> this.favouritesList());
            favs.setTag("media-favourites-list");
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-2, -2);
            flp.rightMargin = this.app.dp(6);
            tools.addView(favs, flp);
            TextView lists = this.app.pill("\u2630 Playlists \u25be", false, v -> this.playlistsList());
            lists.setTag("media-playlists-list");
            tools.addView(lists);
        }
        if (tools.getChildCount() > 0) this.body.addView(tools);
        if (this.loading) {
            TextView reading = this.app.text("Reading the folder\u2026", 14, false);
            reading.setTag("media-reading");
            this.body.addView(reading);
            return;
        }
        if (this.entries.isEmpty()) {
            // The F G C switches or the T lists may be what keeps the files out: say so.
            FileFilter.use(FileFilter.MEDIA);
            TextView none = this.app.text(FileFilter.active() ? FileFilter.nothing() : "No pictures, videos or sounds here", 14, false);
            none.setTag("media-empty");
            this.body.addView(none);
            return;
        }
        int from = this.page * PAGE;
        int to = Math.min(this.entries.size(), from + PAGE);
        final int cell = (this.app.getResources().getDisplayMetrics().widthPixels - this.app.dp(96)) / 3;
        LinearLayout row = null;
        final List<ImageView> views = new ArrayList<ImageView>();
        // The files already in this folder's playlist get a ☰ badge.
        final java.util.Set<String> listed = new java.util.HashSet<String>();
        for (MediaPlaylist.Item it : MediaPlaylist.items(this.app.getFilesDir(), this.folderKey())) listed.add(it.id);
        final List<MediaDir.Entry> wanted = new ArrayList<MediaDir.Entry>();
        final List<TextView> labels = new ArrayList<TextView>();
        final List<ImageView> folderViews = new ArrayList<ImageView>();
        final List<MediaDir.Entry> folderEntries = new ArrayList<MediaDir.Entry>();
        for (int i = from; i < to; i++) {
            final MediaDir.Entry e = this.entries.get(i);
            if ((i - from) % 3 == 0) {
                row = this.app.row();
                row.setGravity(Gravity.TOP);
                this.body.addView(row);
            }
            LinearLayout card = this.app.col();
            card.setTag((e.folder ? "media-folder:" : "media-card:") + e.name);
            card.setPadding(this.app.dp(3), this.app.dp(3), this.app.dp(3), this.app.dp(6));
            FrameLayout box = new FrameLayout(this.app);
            box.setBackgroundColor(UiKit.ELEV);
            ImageView image = new ImageView(this.app);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            TextView mark = this.app.text(e.folder ? "FOLDER" : (e.dbId != 0 ? MediaDir.STAR : "") + ext(e.name), 13, true);
            mark.setTextColor(UiKit.MUTED);
            mark.setGravity(Gravity.CENTER);
            box.addView(mark, new FrameLayout.LayoutParams(-1, -1));
            box.addView(image, new FrameLayout.LayoutParams(-1, -1));
            if (e.kind == MediaDir.VIDEO || e.kind == MediaDir.SOUND) {
                TextView play = this.app.text("▶", 16, true);
                play.setGravity(Gravity.CENTER);
                box.addView(play, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END));
                play.setPadding(this.app.dp(4), 0, this.app.dp(6), this.app.dp(2));
            }
            if (!e.folder && listed.contains(e.id)) this.badge(box, e);
            // Select multiple: a box on each file to tick.
            final boolean pickable = this.selecting && !e.folder && e.dbId == 0;
            if (pickable) {
                final android.widget.CheckBox tick = new android.widget.CheckBox(this.app);
                tick.setTag("media-select:" + e.name);
                tick.setChecked(this.selected.contains(e.id));
                tick.setOnCheckedChangeListener((b, on) -> {
                    if (on) this.selected.add(e.id);
                    else this.selected.remove(e.id);
                    View count = this.body.findViewWithTag("media-selection-count");
                    if (count instanceof TextView) ((TextView) count).setText(this.selected.size() + " selected \u00b7 hold a ticked one for its menu");
                });
                box.addView(tick, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
            }
            card.addView(box, new LinearLayout.LayoutParams(-1, Math.max(this.app.dp(60), cell)));
            // A favourite file: ★ before its name.
            boolean favFile = !e.folder && e.dbId == 0 && FileFavourites.isKey(MediaDir.groupKey(e));
            TextView name = this.app.text((favFile ? MediaDir.STAR : "") + MediaDir.shownName(e), 11, false);
            name.setMaxLines(3);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            name.setPadding(0, this.app.dp(3), 0, 0);
            card.addView(name);
            // Its tags and category.
            String tagLine = e.folder || e.dbId != 0 ? "" : MediaTags.line(MediaTags.key(e));
            if (tagLine.length() > 0) {
                TextView tags = this.app.text(tagLine, 10, false);
                tags.setTextColor(0xFFD9A441);
                tags.setMaxLines(2);
                tags.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tags.setTag("media-tags:" + e.name);
                card.addView(tags);
            }
            card.setOnClickListener(v -> {
                // Selecting: a tap ticks it (or not).
                if (pickable) {
                    View tick = v.findViewWithTag("media-select:" + e.name);
                    if (tick instanceof android.widget.CheckBox) ((android.widget.CheckBox) tick).toggle();
                    return;
                }
                if (e.folder) this.openFolder(e);
                else if (this.chooser != null && rowPictures && e.kind == MediaDir.VIDEO && AppSettings.showSogni() && !AppSettings.musicOnly()) {
                    // A picture row's Choose file: the video plays; C there fills the row with its frame.
                    this.frameRow = this.chooser;
                    this.video(e);
                } else if (this.chooser != null) this.chosen(e);
                else this.openEntry(e);
            });
            // A long press on a file: Add to DB (reference or result file), Add to default playlist,
            // Rename, Copy / Move to folder, Delete; on a folder: Rename, Delete.
            // Held and moved onto a folder card (or Up), a file is moved there; let go on itself, its menu shows.
            if (pickable) {
                // A ticked file held: Remove selected, Move selected to…
                card.setOnLongClickListener(v -> {
                    if (this.selected.isEmpty()) this.selected.add(e.id);
                    this.selectionMenu();
                    return true;
                });
            } else if (!e.folder && e.dbId == 0) {
                card.setOnLongClickListener(v -> {
                    if (!this.changeable() || !this.startDrag(v, e)) this.menu(e);
                    return true;
                });
                card.setOnDragListener((v, ev) -> {
                    if (ev.getAction() == android.view.DragEvent.ACTION_DRAG_STARTED) return ev.getLocalState() == e;
                    // Let go anywhere but on a folder (on the card itself, most often): its menu.
                    if (ev.getAction() == android.view.DragEvent.ACTION_DROP && ev.getLocalState() == e) {
                        dragging = null;
                        this.main.post(() -> this.menu(e));
                    } else if (ev.getAction() == android.view.DragEvent.ACTION_DRAG_ENDED && !ev.getResult() && dragging == e) {
                        dragging = null;
                        this.main.post(() -> this.menu(e));
                    }
                    return true;
                });
            } else if (e.folder && this.changeable()) {
                card.setOnLongClickListener(v -> {
                    this.folderMenu(e);
                    return true;
                });
                this.dropTarget(card, e.id);
            } else if (this.chooser == null && (e.dbId != 0 || e.folder && this.renamable() && e.id.startsWith("/"))) {
                // A library prompt file (★) or a Download folder: Rename.
                card.setOnLongClickListener(v -> {
                    this.renameMenu(e);
                    return true;
                });
            }
            if (e.id.equals(this.focusId)) {
                // The folder Up came from: outlined, and scrolled to.
                card.setBackgroundColor(0xFF2A2D31);
                card.setTag(R_FOCUS, "media-focus");
                final View focusRow = row;
                this.main.post(() -> {
                    if (this.scroll != null) this.scroll.scrollTo(0, Math.max(0, focusRow.getTop() - this.app.dp(8)));
                });
            }
            if (e.folder) {
                // A folder shows the first picture in it.
                folderViews.add(image);
                folderEntries.add(e);
            }
            row.addView(card, new LinearLayout.LayoutParams(0, -2, 1f));
            if (e.kind == MediaDir.PICTURE || e.kind == MediaDir.VIDEO) {
                Bitmap have = THUMBS.get(key(e));
                String size = SIZES.get(key(e));
                if (size != null && size.length() > 0) name.setText(name.getText().toString().split("\n")[0] + "\n" + size);
                if (have != null && size != null) image.setImageBitmap(have);
                else {
                    views.add(image);
                    wanted.add(e);
                    labels.add(name);
                }
            }
        }
        // The last row keeps its cells' width.
        if (row != null) for (int k = row.getChildCount(); k < 3; k++) row.addView(new View(this.app), new LinearLayout.LayoutParams(0, 1, 1f));
        int pages = (this.entries.size() + PAGE - 1) / PAGE;
        if (pages > 1) {
            LinearLayout nav = this.app.row();
            nav.setPadding(0, this.app.dp(8), 0, 0);
            TextView prev = this.app.pill("Previous", false, v -> this.showPage(this.page - 1));
            prev.setTag("media-prev");
            prev.setEnabled(this.page > 0);
            prev.setAlpha(this.page > 0 ? 1f : 0.4f);
            TextView at = this.app.text("Page " + (this.page + 1) + " of " + pages, 13, false);
            at.setTag("media-page");
            at.setGravity(Gravity.CENTER);
            TextView next = this.app.pill("Next", false, v -> this.showPage(this.page + 1));
            next.setTag("media-next");
            next.setEnabled(this.page < pages - 1);
            next.setAlpha(this.page < pages - 1 ? 1f : 0.4f);
            // First and Last: the first and the last page at once.
            TextView first = this.app.pill("First", false, v -> this.showPage(0));
            first.setTag("media-first");
            first.setEnabled(this.page > 0);
            first.setAlpha(this.page > 0 ? 1f : 0.4f);
            final int lastPage = pages - 1;
            TextView lastBtn = this.app.pill("Last", false, v -> this.showPage(lastPage));
            lastBtn.setTag("media-last");
            lastBtn.setEnabled(this.page < pages - 1);
            lastBtn.setAlpha(this.page < pages - 1 ? 1f : 0.4f);
            LinearLayout.LayoutParams gapLp = new LinearLayout.LayoutParams(-2, -2);
            gapLp.rightMargin = this.app.dp(4);
            nav.addView(first, gapLp);
            nav.addView(prev);
            nav.addView(at, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(-2, -2);
            nextLp.rightMargin = this.app.dp(4);
            nav.addView(next, nextLp);
            nav.addView(lastBtn);
            this.body.addView(nav);
        }
        final int px = Math.max(64, cell);
        for (int k = 0; k < folderEntries.size(); k++) {
            final MediaDir.Entry f = folderEntries.get(k);
            final ImageView view = folderViews.get(k);
            READER.execute(() -> {
                if (mine != this.token) return;
                String fk = "folder|" + key(f);
                Bitmap b = THUMBS.get(fk);
                if (b == null) {
                    MediaDir.Entry pic = this.firstPicture(f);
                    b = pic == null ? null : this.thumb(pic, px);
                    if (b != null) THUMBS.put(fk, b);
                }
                final Bitmap shown = b;
                if (shown != null) this.main.post(() -> {
                    if (mine == this.token) view.setImageBitmap(shown);
                });
            });
        }
        for (int k = 0; k < wanted.size(); k++) {
            final MediaDir.Entry e = wanted.get(k);
            final ImageView view = views.get(k);
            final TextView label = labels.get(k);
            READER.execute(() -> {
                if (mine != this.token) return;
                final Bitmap b = THUMBS.get(key(e)) != null ? THUMBS.get(key(e)) : this.thumb(e, px);
                // Its resolution and aspect ratio under the name: "1920×1080 · 16:9".
                String sized = SIZES.get(key(e));
                if (sized == null) {
                    sized = this.sizeOf(e);
                    SIZES.put(key(e), sized);
                }
                final String size = sized;
                if (b != null) THUMBS.put(key(e), b);
                this.main.post(() -> {
                    if (mine != this.token) return;
                    if (b != null) view.setImageBitmap(b);
                    if (size.length() > 0) {
                        label.setText(label.getText().toString().split("\n")[0] + "\n" + size);
                        label.setTag("media-size:" + size);
                    }
                });
            });
        }
    }

    private String key(MediaDir.Entry e) {
        return (this.tree == null ? "" : this.tree.toString()) + "|" + e.id + "|" + e.size;
    }

    /** The menu shown last, for the tests. */
    static AlertDialog lastMenu;

    /** A file's first bytes (MediaDir.HEAD; a picture's size is in them): a path, or a document address. */
    byte[] head(MediaDir.Entry e) {
        java.io.File f = new java.io.File(e.id == null ? "" : e.id);
        if (f.isFile()) return MediaDir.head(f);
        try (java.io.InputStream in = this.app.getContentResolver().openInputStream(Uri.parse(e.id))) {
            if (in == null) return null;
            byte[] b = new byte[MediaDir.HEAD];
            int got = 0;
            for (int n; got < b.length && (n = in.read(b, got, b.length - got)) > 0; ) got += n;
            return java.util.Arrays.copyOf(b, got);
        } catch (Exception ex) {
            return null;
        }
    }

    /** A file's long-press menu (MediaDir.MENU). */
    void menu(final MediaDir.Entry e) {
        // A file already in the playlist: its third item takes it out.
        String[] baseItems = MediaDir.menu(MediaPlaylist.has(this.app.getFilesDir(), this.folderKey(), e.id), e.kind, this.changeable(), e.name, false);
        // Download (not moved or deleted from here): Rename still.
        if (this.renamable()) {
            baseItems = java.util.Arrays.copyOf(baseItems, baseItems.length + 1);
            baseItems[baseItems.length - 1] = MediaDir.RENAME;
        }
        final String[] menuItems = baseItems;
        // Add to playlist: a named one (.pls, File > Playlist viewer/editor), not only this folder's.
        boolean playable = e.kind == MediaDir.PICTURE || e.kind == MediaDir.VIDEO || e.kind == MediaDir.SOUND;
        String[] withPlaylist = playable ? MediaDir.withPlaylistItem(menuItems, Playlist.ADD) : menuItems;
        // A picture: Use as a reference (the Sogni picture tools shown); one to upscale is asked about first.
        final byte[] refHead = e.kind == MediaDir.PICTURE && e.dbId == 0 ? this.head(e) : null;
        if (refHead != null && MediaDir.useRef(e.name, refHead)) {
            withPlaylist = java.util.Arrays.copyOf(withPlaylist, withPlaylist.length + 1);
            withPlaylist[withPlaylist.length - 1] = MediaDir.PROCESS_REF;
        }
        // Add to Collection: a link to the file (a file on disk, or the folder's document).
        if (e.dbId == 0) {
            withPlaylist = java.util.Arrays.copyOf(withPlaylist, withPlaylist.length + 1);
            withPlaylist[withPlaylist.length - 1] = MediaCollections.ADD;
        }
        // A made file carrying its Sogni job id: Sogni job id (and the workflow that made it).
        final String job = e.dbId == 0 ? SogniJobDialog.of(this.app, e.id, e.name) : null;
        if (job != null) {
            withPlaylist = java.util.Arrays.copyOf(withPlaylist, withPlaylist.length + 1);
            withPlaylist[withPlaylist.length - 1] = MediaDir.SOGNI_JOB;
        }
        // Choose file (a Params row), a video: its first frame fills the row as a picture.
        if (this.chooser != null && e.kind == MediaDir.VIDEO) {
            withPlaylist = java.util.Arrays.copyOf(withPlaylist, withPlaylist.length + 1);
            withPlaylist[withPlaylist.length - 1] = MediaDir.FIRST_FRAME_REF;
        } else if (this.chooser == null && MediaDir.firstFrameRef(e.name)) {
            // The Media browser, a video: its first frame as a reference (upscaled first when asked).
            withPlaylist = java.util.Arrays.copyOf(withPlaylist, withPlaylist.length + 1);
            withPlaylist[withPlaylist.length - 1] = MediaDir.FIRST_FRAME_AS_REF;
        }
        // A favourite already: Remove from favourites.
        FileFavourites.load(this.app.getFilesDir());
        final boolean fav = FileFavourites.isKey(MediaDir.groupKey(e));
        for (int i = 0; i < withPlaylist.length; i++) if (fav && FileFavourites.ADD_SHORT.equals(withPlaylist[i])) withPlaylist[i] = FileFavourites.REMOVE_SHORT;
        // In alphabetical order; the first items are picked by their old place (baseIndex).
        final String[] items = MediaDir.sorted(withPlaylist);
        final boolean listed = MediaPlaylist.has(this.app.getFilesDir(), this.folderKey(), e.id);
        lastMenu = new AlertDialog.Builder(this.app)
            .setTitle(e.name)
            .setItems(items, (d, which) -> {
                if (Playlist.ADD.equals(items[which])) PlaylistScreen.addTo(this.app, this.playlistAddress(e), e.name);
                else if (MediaTags.TAGS.equals(items[which])) this.tags(e);
                else if (FileFavourites.ADD_SHORT.equals(items[which]) || FileFavourites.REMOVE_SHORT.equals(items[which])) {
                    boolean now = FileFavourites.toggleKey(MediaDir.groupKey(e));
                    this.app.setNow(e.name + (now ? " added to favourites" : " taken out of favourites"));
                    this.refilter();
                }
                else if (FileGroups.ADD.equals(items[which])) GroupDialogs.addTo(this.app, MediaDir.groupKey(e), e.name, () -> {
                    FileFilter.use(FileFilter.MEDIA);
                    if (FileFilter.active()) this.refilter();
                });
                else if (MediaTags.CATEGORY.equals(items[which])) this.category(e);
                else if (java.util.Arrays.asList(MediaDir.FILE_ACTIONS).contains(items[which])) this.fileAction(e, items[which]);
                else if (MediaDir.isKindItem(items[which])) this.kindItem(e, items[which]);
                else if (MediaDir.PROCESS_REF.equals(items[which])) this.useAsRef(e, refHead);
                else if (MediaDir.SOGNI_JOB.equals(items[which])) SogniJobDialog.show(this.app, job);
                else if (MediaDir.FIRST_FRAME_REF.equals(items[which])) this.firstFrameChosen(e);
                else if (MediaDir.FIRST_FRAME_AS_REF.equals(items[which]))
                    RefImages.firstFrameAsRef(this.app, e.name, this.onDisk(e) ? e.id : null, this.onDisk(e) ? null : this.uri(e), this.dialog, this.returner());
                else if (MediaCollections.ADD.equals(items[which])) CollectionsScreen.addTo(this.app, MediaCollections.KIND_FILE, this.playlistAddress(e), e.name, e.size);
                else this.pick(e, MediaDir.baseIndex(listed, e.kind, items[which]));
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * A MIDI's Open in MIDI editor (Save writes <name>-edited.mid beside it), Render with SF2
     * (Midi2Wav), Play with default SoundFont (the phone's own MIDI player); a sound's Convert drum
     * hits into MIDI (DrumMidi_CRT). Programs open in PyJav with the file as their input, Params
     * open, and Return comes back to this folder.
     */
    void kindItem(final MediaDir.Entry e, final String item) {
        final Uri tree = this.tree;
        final String base = this.base;
        final String root = this.root;
        final List<String> at = new ArrayList<String>(this.path);
        final MainActivity host = this.app;
        this.chosen(e, (name, file) -> {
            if (MediaDir.MIDI_EDIT.equals(item)) {
                final String[] origin = ORIGIN.get(file.getAbsolutePath());
                byte[] bytes;
                try {
                    bytes = java.nio.file.Files.readAllBytes(file.toPath());
                } catch (Exception ex) {
                    host.setNow("Could not read " + name);
                    return;
                }
                final String edited = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-edited.mid";
                MidiEditScreen.openFile(host, name, bytes, midi -> origin != null
                    ? saveInto(host, origin, null, edited, "audio/midi", midi)
                    : writeBeside(file, edited, midi));
            } else if (MediaDir.PLAY_SF.equals(item)) {
                playDefault(host, file, name);
            } else if (MediaDir.TO_COMPOSER.equals(item)) {
                toComposer(host, file, name);
            } else if (MediaDir.TO_FILE_SET.equals(item)) {
                toFileSet(host, file, name);
            } else if (MediaDir.TO_WAV.equals(item)) {
                AudioOffer.convert(host, file);
            } else {
                host.openKitView("py");
                host.programMenus.selectProgram("Java", MediaDir.TO_MIDI.equals(item) ? "DrumMidi_CRT.jar" : "Midi2Wav.java");
                host.pyJav.pkUseInputPath(file.getAbsolutePath());
                host.pyJav.pkSetReturn(() -> reopen(host, tree, base, root, at));
                host.pyJav.pkOpenParams();
            }
        });
    }

    /** Add to composer stack: the MIDI as a file set (no song asked about), its patterns and fills on the Composer's stack, the Composer shown. */
    /** The file set picked last list (for the tests). */
    static AlertDialog lastFileSets;

    /** Add into file set: the MIDI kept as the source MIDI of the file set picked (From file set finds it there). */
    static void toFileSet(final MainActivity app, File file, final String name) {
        final byte[] midi;
        try {
            midi = java.nio.file.Files.readAllBytes(file.toPath());
        } catch (Exception ex) {
            app.setNow("Could not read " + name);
            return;
        }
        final java.util.List<String> sources = Engine.fileSetSources(app.learned, app.learnedFills);
        AlertDialog.Builder b = new AlertDialog.Builder(app).setTitle(MediaDir.TO_FILE_SET + " \u00b7 " + name).setNegativeButton("Cancel", null);
        if (sources.isEmpty()) b.setMessage("No file sets yet: import a pattern library, or run DrumMidi_CRT in PyJav (it makes one).");
        else b.setItems(sources.toArray(new String[0]), (d, w) -> {
            Engine.rememberFileSetMidi(sources.get(w), midi, name);
            Engine.storeFileSetAudioDir(new File(app.getFilesDir(), "fset-audio"));
            app.setNow(name + " is the MIDI of the file set " + sources.get(w));
        });
        lastFileSets = b.show();
    }

    static void toComposer(MainActivity app, File file, String name) {
        try {
            Engine.MidiBars bars = Engine.parseMidiBars(java.nio.file.Files.readAllBytes(file.toPath()));
            if (bars == null) {
                app.setNow(name + " has no drums for the stack");
                return;
            }
            app.importLibrary.quietSet = true;
            app.importLibrary.lastPackedSet = null;
            boolean made;
            try {
                made = app.importLibrary.tryPackMidiFileSet(name, bars, true);
            } finally {
                app.importLibrary.quietSet = false;
            }
            String set = app.importLibrary.lastPackedSet;
            if (!made || set == null) {
                app.setNow("No patterns in " + name + " for the stack");
                return;
            }
            Composer c = app.composerPage.composer();
            int added = c.addFileSet(set, app.learned, app.learnedFills);
            c.save(app.getFilesDir());
            app.show("composer");
            app.setNow(name + " \u00b7 " + added + (added == 1 ? " block" : " blocks") + " on the Composer's stack");
        } catch (Exception ex) {
            app.setNow("Could not read " + name);
        }
    }

    /** `data` as `name` in the file's own folder (a name taken gets " (1)"); returns its path. */
    static String writeBeside(File file, String name, byte[] data) throws Exception {
        String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        String ext = name.substring(stem.length());
        File f = new File(file.getParentFile(), name);
        for (int n = 1; f.exists(); n++) f = new File(file.getParentFile(), stem + " (" + n + ")" + ext);
        java.nio.file.Files.write(f.toPath(), data);
        return f.getPath();
    }

    /** Play with default SoundFont: the phone's own MIDI player (all its instruments), Stop in a small dialog. */
    static android.media.MediaPlayer playing;
    static AlertDialog lastPlay;

    static void playDefault(final MainActivity app, File file, String name) {
        stopDefault();
        try {
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            SoundOut.prefer(mp);
            mp.setDataSource(file.getAbsolutePath());
            mp.prepare();
            mp.start();
            playing = mp;
            mp.setOnCompletionListener(p -> {
                if (lastPlay != null && lastPlay.isShowing()) lastPlay.dismiss();
            });
        } catch (Exception ex) {
            app.setNow("The phone could not play " + name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
            return;
        }
        app.setNow("Playing " + name + " with the default SoundFont");
        lastPlay = new AlertDialog.Builder(app).setTitle(name).setMessage("Playing with the phone's default SoundFont")
            .setPositiveButton("Stop", null).setOnDismissListener(d -> stopDefault()).show();
    }

    static void stopDefault() {
        android.media.MediaPlayer mp = playing;
        playing = null;
        if (mp == null) return;
        try {
            mp.stop();
        } catch (Exception ignored) {
            // not started
        }
        mp.release();
    }

    /** A folder's long-press menu: Rename, Delete. */
    void folderMenu(final MediaDir.Entry e) {
        lastMenu = new AlertDialog.Builder(this.app)
            .setTitle(e.name)
            .setItems(MediaDir.FOLDER_MENU, (d, which) -> this.fileAction(e, MediaDir.FOLDER_MENU[which]))
            .setNegativeButton("Cancel", null)
            .show();
    }

    static final String FILE_MENU = "File";
    static final String IMPORT_FILE_ITEM = "Import file\u2026", IMPORT_FOLDER_ITEM = "Import folder\u2026", COPY_FOLDER_ITEM = "Copy folder from\u2026";

    /** The File menu shown last, and the folders question (for the tests). */
    static AlertDialog lastFileMenu, lastFoldersAsk;

    static final String SELECT_MULTIPLE = "Select multiple", STOP_SELECTING = "Stop selecting";

    /** The File menu's items here. */
    List<String> fileItems() {
        List<String> items = new ArrayList<String>();
        // Select multiple: boxes on the files, then Remove selected or Move selected to… (a folder that can be changed).
        if (this.changeable()) items.add(this.selecting ? STOP_SELECTING : SELECT_MULTIPLE);
        if (this.changeable()) {
            items.add(IMPORT_FILE_ITEM);
            items.add(IMPORT_FOLDER_ITEM);
            items.add(FolderZip.IMPORT);
            items.add(FolderZip.SEND);
            items.add(COPY_FOLDER_ITEM);
        }
        items.add(DownloadMove.DEFAULTS);
        // The folder's pictures, videos and music (those ticked) into a playlist.
        items.add(Playlist.ADD_FOLDER);
        return items;
    }

    void fileMenu() {
        final List<String> items = this.fileItems();
        lastFileMenu = new AlertDialog.Builder(this.app).setTitle(FILE_MENU).setItems(items.toArray(new String[0]), (d, which) -> this.fileItem(items.get(which)))
            .setNegativeButton("Cancel", null).show();
    }

    void fileItem(String item) {
        if (IMPORT_FILE_ITEM.equals(item)) this.pickImport(IMPORT_FILES);
        else if (IMPORT_FOLDER_ITEM.equals(item)) this.pickImport(IMPORT_FOLDER);
        else if (COPY_FOLDER_ITEM.equals(item)) this.pickImport(COPY_FOLDER);
        else if (FolderZip.IMPORT.equals(item)) this.pickImport(IMPORT_ZIP);
        else if (FolderZip.SEND.equals(item)) this.folderToZip();
        else if (DownloadMove.DEFAULTS.equals(item)) this.moveDefined();
        else if (Playlist.ADD_FOLDER.equals(item)) this.addFolderToPlaylist();
        else if (SELECT_MULTIPLE.equals(item) || STOP_SELECTING.equals(item)) {
            this.selecting = SELECT_MULTIPLE.equals(item);
            this.selected.clear();
            this.paint();
        }
    }

    /** Add folder's media files to playlist's dialog shown last, and what it said (for the tests). */
    static AlertDialog lastAddFolder;
    static String lastAddFolderSaid;

    /**
     * Add folder's media files to playlist: Images, Videos and Music to tick, then the playlist (Add new
     * first: its name asked); the folder's files of the kinds ticked go into it.
     */
    void addFolderToPlaylist() {
        LinearLayout box = this.app.col();
        box.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
        int[] counts = new int[3];
        for (MediaDir.Entry e : this.allEntries) {
            if (e.folder || e.dbId != 0) continue;
            int k = MediaDir.kind(e.name);
            if (k == MediaDir.PICTURE) counts[0]++;
            else if (k == MediaDir.VIDEO) counts[1]++;
            else if (k == MediaDir.SOUND) counts[2]++;
        }
        String[][] kinds = {{"media-pl-pictures", "Images"}, {"media-pl-videos", "Videos"}, {"media-pl-music", "Music"}};
        final android.widget.CheckBox[] boxes = new android.widget.CheckBox[3];
        for (int i = 0; i < 3; i++) {
            boxes[i] = new android.widget.CheckBox(this.app);
            boxes[i].setText(kinds[i][1] + " (" + counts[i] + ")");
            boxes[i].setTag(kinds[i][0]);
            boxes[i].setChecked(counts[i] > 0);
            box.addView(boxes[i]);
        }
        TextView into = this.app.text("Into the playlist", 13, true);
        into.setPadding(0, this.app.dp(8), 0, 0);
        box.addView(into);
        final List<String> names = new ArrayList<String>();
        names.add(Playlist.ADD_NEW);
        names.addAll(Playlist.names(this.app.getFilesDir()));
        final android.widget.Spinner pick = new android.widget.Spinner(this.app);
        pick.setTag("media-pl-pick");
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this.app, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pick.setAdapter(adapter);
        box.addView(pick);
        lastAddFolder = new AlertDialog.Builder(this.app).setTitle(Playlist.ADD_FOLDER).setView(box)
            .setPositiveButton("OK", (d, w) -> {
                final boolean pics = boxes[0].isChecked(), vids = boxes[1].isChecked(), music = boxes[2].isChecked();
                if (!pics && !vids && !music) {
                    this.app.setNow("Nothing added: no type is ticked");
                    return;
                }
                int at = pick.getSelectedItemPosition();
                if (at <= 0) {
                    final android.widget.EditText field = new android.widget.EditText(this.app);
                    field.setSingleLine(true);
                    field.setTag("media-pl-name");
                    field.setText(this.label());
                    field.setSelection(field.getText().length());
                    LinearLayout nb = this.app.col();
                    nb.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
                    nb.addView(field);
                    lastAsk = new AlertDialog.Builder(this.app).setTitle("New playlist").setView(nb)
                        .setPositiveButton("OK", (d2, w2) -> this.addFolderInto(field.getText().toString(), pics, vids, music))
                        .setNegativeButton("Cancel", null).show();
                } else this.addFolderInto(names.get(at), pics, vids, music);
            })
            .setNegativeButton("Cancel", null).show();
    }

    void addFolderInto(String playlist, boolean pics, boolean vids, boolean music) {
        String n = playlist == null ? "" : playlist.trim();
        if (n.length() == 0) {
            this.app.setNow("Nothing added: the playlist has no name");
            return;
        }
        List<String[]> files = Playlist.mediaOf(MediaDir.sortedBy(this.allEntries, MediaDir.sortBy, MediaDir.sortDesc), pics, vids, music, this::playlistAddress);
        String said = files.isEmpty() ? "No files of the kinds ticked here" : Playlist.addAll(this.app.getFilesDir(), n, files);
        lastAddFolderSaid = said;
        this.app.setNow(said);
        android.widget.Toast.makeText(this.app, said, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Move defined media types: with no type ticked with a folder, the folders asked for first. */
    void moveDefined() {
        SogniDownloads.init(this.app.getFilesDir());
        DownloadMove.load();
        if (DownloadMove.anyDefault()) {
            this.moveToDefaults();
            return;
        }
        LinearLayout box = this.app.col();
        box.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
        box.addView(this.app.text(DownloadMove.FOLDERS_ASK, 14, false));
        DownloadsScreen.folderRows(this.app, box, null);
        lastFoldersAsk = new AlertDialog.Builder(this.app).setTitle(DownloadMove.SETTINGS_TITLE).setView(box)
            .setPositiveButton("Move", (d, w) -> {
                if (DownloadMove.anyDefault()) this.moveToDefaults();
                else this.app.setNow("Not moved: no type is ticked with a folder");
            })
            .setNegativeButton("Cancel", null).show();
    }

    /** Whether a kind's folder (a path, or a granted folder's address) is the folder shown. */
    boolean isHere(String to) {
        if (to.startsWith("content://")) {
            return this.tree != null && this.tree.toString().equals(to) && here().equals(DocumentsContract.getTreeDocumentId(this.tree));
        }
        return this.tree == null && !DOWNLOADS.equals(this.path.get(0)) && new File(here()).getAbsoluteFile().equals(new File(to).getAbsoluteFile());
    }

    /** What Move to default locations said last (for the tests). */
    static volatile String lastDefaultsSaid;

    /**
     * Move to default locations: Download's pictures, videos and music whose kind has a folder picked in
     * Downloads > Finished moved there, the oldest first, each streamed (off the main thread, with
     * Pulsekit's notification, as Move now). The Downloads list and the playlists follow them.
     */
    void moveToDefaults() {
        final List<MediaDir.Entry> files = new ArrayList<MediaDir.Entry>();
        for (MediaDir.Entry e : this.allEntries) {
            String to = e.folder || e.dbId != 0 ? "" : DownloadMove.defaultFor(e.name);
            if (to.length() > 0 && !this.isHere(to)) files.add(e);
        }
        java.util.Collections.sort(files, (a, b) -> Long.compare(a.date, b.date));
        if (!DownloadsScreen.moving.compareAndSet(false, true)) {
            this.app.setNow("Moving already");
            return;
        }
        final java.util.Map<MediaDir.Entry, Uri> from = new java.util.HashMap<MediaDir.Entry, Uri>();
        final java.util.Map<MediaDir.Entry, String> address = new java.util.HashMap<MediaDir.Entry, String>();
        for (MediaDir.Entry e : files) {
            from.put(e, this.uri(e));
            address.put(e, this.playlistAddress(e));
        }
        final MainActivity app = this.app;
        final boolean inDownload = DOWNLOADS.equals(this.path.get(0));
        // Each kind's subfolder (its % codes filled in) once for the whole move, off the main thread.
        final java.util.Map<String, String> subs = new java.util.HashMap<String, String>();
        final boolean keeper = !RunKeeper.foreground;
        if (keeper) RunKeeper.start(app, "Move downloads");
        // What is moving: the file, how many of how many, the MB done, and a bar (Hide: it goes on).
        LinearLayout box = this.app.col();
        box.setPadding(this.app.dp(20), this.app.dp(12), this.app.dp(20), 0);
        final TextView status = this.app.text(files.isEmpty() ? "Looking for files\u2026" : "Moving " + files.size() + (files.size() == 1 ? " file" : " files") + "\u2026", 14, false);
        status.setTag("media-move-status");
        box.addView(status);
        final android.widget.ProgressBar bar = new android.widget.ProgressBar(this.app, null, android.R.attr.progressBarStyleHorizontal);
        bar.setTag("media-move-progress");
        bar.setMax(1000);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = this.app.dp(8);
        box.addView(bar, blp);
        lastMoveProgress = new AlertDialog.Builder(this.app).setTitle(DownloadMove.DEFAULTS).setView(box).setPositiveButton("Hide", null).show();
        final int count = files.size();
        new Thread(() -> {
            int moved = 0, failed = 0;
            String problem = null;
            try {
                for (int i = 0; i < files.size(); i++) {
                    MediaDir.Entry e = files.get(i);
                    final String head = "Moving " + (i + 1) + " of " + count + ": " + e.name;
                    final int index = i;
                    final long size = e.size;
                    this.main.post(() -> {
                        status.setText(head);
                        bar.setProgress(index * 1000 / count);
                    });
                    String kind = DownloadMove.kindOf(e.name);
                    if (!subs.containsKey(kind)) subs.put(kind, subNow(app, kind, originOf(DownloadMove.defaultFor(e.name))));
                    String why = moveToDefault(app, e, from.get(e), address.get(e), inDownload, subs.get(kind), done -> this.main.post(() -> {
                        // At each MB: how far into this file.
                        status.setText(head + " \u00b7 " + DownloadMove.mb(done) + (size > 0 ? " of " + DownloadMove.mb(size) : ""));
                        bar.setProgress((int) ((index + (size > 0 ? Math.min(1.0, done / (double) size) : 0.5)) * 1000 / count));
                    }));
                    if (why == null) moved++;
                    else {
                        failed++;
                        if (problem == null) problem = why;
                    }
                }
            } finally {
                DownloadsScreen.moving.set(false);
                if (keeper) RunKeeper.stop(app);
            }
            DownloadMove.afterMove(moved);
            final String said = DownloadMove.defaultsSaid(moved, failed, problem);
            lastDefaultsSaid = said;
            this.main.post(() -> {
                app.setNow(said);
                status.setText(said);
                bar.setProgress(1000);
                if (lastMoveProgress != null && lastMoveProgress.isShowing()) lastMoveProgress.getButton(AlertDialog.BUTTON_POSITIVE).setText("Close");
                if (this.dialog != null && this.dialog.isShowing()) this.load(this.here());
            });
        }).start();
    }

    /** One file into its kind's folder and out of the folder shown; null when moved, else why not. */
    /** How far a file's move is: the bytes written so far (said at each MB). */
    interface Progress {
        void at(long done);
    }

    /** Move defined media types' window: the status and the bar (for the tests). */
    static AlertDialog lastMoveProgress;

    static String moveToDefault(MainActivity app, MediaDir.Entry e, Uri src, String address, boolean inDownload, final Progress progress) {
        return moveToDefault(app, e, src, address, inDownload, null, progress);
    }

    /** As above, into `sub` (a subfolder path, made when missing) of the kind's folder. */
    static String moveToDefault(MainActivity app, MediaDir.Entry e, Uri src, String address, boolean inDownload, String sub, final Progress progress) {
        try {
            String to = DownloadMove.defaultFor(e.name);
            String[] origin = to.startsWith("content://") ? new String[] {to, DocumentsContract.getTreeDocumentId(Uri.parse(to))} : new String[] {"", to};
            // In Download, the ways the Downloads list knows it too.
            java.util.Set<String> olds = inDownload ? DownloadsScreen.olds(app, "Download/" + e.name) : new java.util.HashSet<String>();
            olds.add(e.id);
            olds.add(address);
            olds.add(src.toString());
            boolean file = "file".equals(src.getScheme());
            final java.io.InputStream in = file ? new java.io.FileInputStream(src.getPath()) : app.getContentResolver().openInputStream(src);
            if (in == null) return e.name + " could not be read";
            String at;
            try {
                at = saveInto(app, origin, sub, e.name, ArtJava.mimeOf(e.name), out -> {
                    byte[] buf = new byte[65536];
                    long done = 0, said = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        if (progress != null && done - said >= (1 << 20)) {
                            said = done;
                            progress.at(done);
                        }
                    }
                });
            } finally {
                in.close();
            }
            String now = savedAddress.get();
            savedAddress.remove();
            if (now == null || now.length() == 0) now = at;
            boolean gone = file ? new File(src.getPath()).delete()
                : DocumentsContract.isDocumentUri(app, src) ? DocumentsContract.deleteDocument(app.getContentResolver(), src) : app.getContentResolver().delete(src, null, null) > 0;
            if (!gone) return "copied " + e.name + " to " + at + ", but could not delete it from Download";
            SogniDownloads.Item item = SogniDownloads.inDownload(olds);
            if (item != null) SogniDownloads.movedAway(item, at.lastIndexOf('/') >= 0 && at.lastIndexOf('/') < at.length() - 1 ? at.substring(at.lastIndexOf('/') + 1) : item.name, now, at);
            Playlist.repoint(app.getFilesDir(), olds, now);
            return null;
        } catch (Exception ex) {
            return e.name + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
        }
    }

    /** Download's files and folders (not moved or deleted from here): Rename only. */
    boolean renamable() {
        return this.chooser == null && !this.changeable() && DOWNLOADS.equals(this.path.get(0));
    }

    /** The menu Rename alone shows (a library prompt file, a Download folder). */
    void renameMenu(final MediaDir.Entry e) {
        lastMenu = new AlertDialog.Builder(this.app)
            .setTitle(MediaDir.shownName(e))
            .setItems(new String[] {MediaDir.RENAME}, (d, which) -> this.fileAction(e, MediaDir.RENAME))
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Whether files here can be renamed, moved and deleted: a folder by path or a granted one (not Download, not Choose file). */
    boolean changeable() {
        return this.chooser == null && !DOWNLOADS.equals(this.path.get(0));
    }

    /** The dialog Rename, Delete or the folder list showed last, for the tests. */
    static AlertDialog lastAsk;

    /** Rename, Copy to folder, Move to folder or Delete, asked about first. */
    void fileAction(final MediaDir.Entry e, String action) {
        if (MediaDir.SEND_ZIP.equals(action)) {
            this.sendToZip(e);
            return;
        }
        if (MediaDir.RENAME.equals(action)) {
            // Two fields: the name (the cursor at its end) and the ending.
            final String[] parts = MediaDir.splitName(e.name, e.folder);
            LinearLayout box = this.app.col();
            box.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
            final android.widget.EditText field = new android.widget.EditText(this.app);
            field.setSingleLine(true);
            field.setText(parts[0]);
            field.setTag("media-rename-field");
            field.setSelection(parts[0].length());
            box.addView(field);
            final android.widget.EditText extField = new android.widget.EditText(this.app);
            extField.setSingleLine(true);
            extField.setText(parts[1]);
            extField.setTag("media-rename-ext");
            extField.setHint("Ending (png, mp4...)");
            if (!e.folder && parts[1].length() > 0) {
                LinearLayout extRow = this.app.row();
                extRow.addView(this.app.text(".", 16, true));
                extRow.addView(extField, new LinearLayout.LayoutParams(0, -2, 1f));
                box.addView(extRow);
            }
            field.requestFocus();
            lastAsk = new AlertDialog.Builder(this.app).setTitle("Rename " + e.name).setView(box)
                .setPositiveButton("Rename", (d, w) -> {
                    String typed = e.folder || parts[1].length() == 0 ? field.getText().toString() : MediaDir.joinName(field.getText().toString(), extField.getText().toString());
                    String said = this.rename(e, typed);
                    // Its tags and category go with the new name.
                    if (said.startsWith("Renamed") && !e.folder && e.dbId == 0) MediaTags.renamed(MediaTags.key(e), MediaTags.key(MediaDir.keepEnding(e.name, typed), e.size));
                    this.done(said);
                })
                .setNegativeButton("Cancel", null).show();
            // The keyboard up, on the name.
            if (lastAsk.getWindow() != null) lastAsk.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else if (MediaDir.DELETE.equals(action)) {
            lastAsk = new AlertDialog.Builder(this.app).setTitle(e.folder ? "Delete folder" : "Delete file")
                .setMessage(e.folder ? "Delete " + e.name + " and everything in it?" : "Delete " + e.name + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    String said = this.delete(e);
                    if (said.startsWith("Deleted") && !e.folder) MediaTags.deleted(MediaTags.key(e));
                    this.done(said);
                })
                .setNegativeButton("Cancel", null).show();
        } else {
            final boolean move = MediaDir.MOVE_TO.equals(action);
            // The folders here, and the one above (when there is one).
            final List<String> ids = new ArrayList<String>();
            final List<String> names = new ArrayList<String>();
            if (this.path.size() > 1) {
                ids.add(this.path.get(this.path.size() - 2));
                names.add("\u2191 Up one folder");
            }
            for (MediaDir.Entry f : this.allEntries) {
                if (f.folder && !f.id.equals(e.id)) {
                    ids.add(f.id);
                    names.add(f.name);
                }
            }
            names.add("New folder\u2026");
            lastAsk = new AlertDialog.Builder(this.app).setTitle((move ? "Move " : "Copy ") + e.name + " to")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    if (which < ids.size()) {
                        this.done(this.copyTo(e, ids.get(which), move));
                        return;
                    }
                    final android.widget.EditText field = new android.widget.EditText(this.app);
                    field.setSingleLine(true);
                    field.setTag("media-newfolder-field");
                    lastAsk = new AlertDialog.Builder(this.app).setTitle("New folder").setView(field)
                        .setPositiveButton(move ? "Move" : "Copy", (d2, w2) -> {
                            String made = this.makeFolder(field.getText().toString());
                            this.done(made == null ? "Could not make the folder" : this.copyTo(e, made, move));
                        })
                        .setNegativeButton("Cancel", null).show();
                })
                .setNegativeButton("Cancel", null).show();
        }
    }

    /** After a change: the status line, and the folder read again. */
    private void done(String said) {
        this.app.setNow(said);
        android.widget.Toast.makeText(this.app, said, android.widget.Toast.LENGTH_SHORT).show();
        this.load(here());
    }

    String rename(MediaDir.Entry e, String typed) {
        String name = e.folder ? MediaDir.cleanName(typed) : MediaDir.keepEnding(e.name, typed);
        if (name == null) return "Not a name: " + typed;
        if (name.equals(e.name)) return "Not renamed";
        try {
            // A library prompt file (★): its name in the DB.
            if (e.dbId != 0) return "Renamed " + e.name + " to " + PromptVault.open(this.app.getFilesDir()).renameFile(e.dbId, PromptVault.PROMPT_FILE, name);
            // A Download file Android lists (MediaStore): its display name (Pulsekit's own files; another app's are refused).
            if (e.id.startsWith(MEDIA) || e.id.startsWith(DL)) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                return this.app.getContentResolver().update(this.uri(e), v, null, null) > 0 ? "Renamed " + e.name + " to " + name : "Could not rename " + e.name;
            }
            if (this.tree == null) {
                File f = new File(e.id);
                File to = new File(f.getParentFile(), name);
                if (to.exists()) return name + " is there already";
                return f.renameTo(to) ? "Renamed " + e.name + " to " + name : "Could not rename " + e.name;
            }
            Uri done = DocumentsContract.renameDocument(this.app.getContentResolver(), this.uri(e), name);
            return done != null ? "Renamed " + e.name + " to " + name : "Could not rename " + e.name;
        } catch (Exception ex) {
            return "Could not rename " + e.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    String delete(MediaDir.Entry e) {
        try {
            if (this.tree == null) return MediaDir.deleteAll(new File(e.id)) ? "Deleted " + e.name : "Could not delete all of " + e.name;
            return DocumentsContract.deleteDocument(this.app.getContentResolver(), this.uri(e)) ? "Deleted " + e.name : "Could not delete " + e.name;
        } catch (Exception ex) {
            return "Could not delete " + e.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    /** A folder made here; its id (a path, or a document id), or null. */
    String makeFolder(String typed) {
        String name = MediaDir.cleanName(typed);
        if (name == null) return null;
        try {
            if (this.tree == null) {
                File f = new File(here(), name);
                return f.isDirectory() || f.mkdirs() ? f.getAbsolutePath() : null;
            }
            Uri made = DocumentsContract.createDocument(this.app.getContentResolver(), DocumentsContract.buildDocumentUriUsingTree(this.tree, here()),
                DocumentsContract.Document.MIME_TYPE_DIR, name);
            return made == null ? null : DocumentsContract.getDocumentId(made);
        } catch (Exception ex) {
            return null;
        }
    }

    /** The file (or folder) copied, or moved, into the folder `to` (an id as `path` holds). */
    String copyTo(MediaDir.Entry e, String to, boolean move) {
        String verb = move ? "Moved " : "Copied ";
        try {
            if (this.tree == null) {
                File done = move ? MediaDir.moveInto(new File(e.id), new File(to)) : MediaDir.copyInto(new File(e.id), new File(to));
                return verb + e.name + " to " + done.getParentFile().getName();
            }
            android.content.ContentResolver cr = this.app.getContentResolver();
            Uri src = this.uri(e);
            Uri target = DocumentsContract.buildDocumentUriUsingTree(this.tree, to);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(this.tree, here());
            try {
                Uri done = move ? DocumentsContract.moveDocument(cr, src, parent, target) : DocumentsContract.copyDocument(cr, src, target);
                if (done != null) return verb + e.name;
            } catch (Exception unsupported) {
                // The folder's app has no copy or move of its own: the bytes, then (moving) the original goes.
            }
            if (e.folder) return "This folder's app copies or moves only files, not folders";
            Uri out = DocumentsContract.createDocument(cr, target, cr.getType(src) == null ? "application/octet-stream" : cr.getType(src), e.name);
            if (out == null) return "Could not " + (move ? "move " : "copy ") + e.name;
            InputStream in = cr.openInputStream(src);
            java.io.OutputStream os = cr.openOutputStream(out);
            try {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
            } finally {
                in.close();
                os.close();
            }
            if (move) DocumentsContract.deleteDocument(cr, src);
            return verb + e.name;
        } catch (Exception ex) {
            return "Could not " + (move ? "move " : "copy ") + e.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    /** The first picture in folder `f` (by name), or null: the folder's thumbnail. */
    MediaDir.Entry firstPicture(MediaDir.Entry f) {
        String best = null, bestId = null;
        if (this.tree == null) {
            File[] kids = new File(f.id).listFiles();
            if (kids == null) return null;
            for (File k : kids) {
                if (!k.isFile() || k.getName().startsWith(".") || MediaDir.kind(k.getName()) != MediaDir.PICTURE) continue;
                if (best == null || k.getName().compareToIgnoreCase(best) < 0) {
                    best = k.getName();
                    bestId = k.getAbsolutePath();
                }
            }
        } else {
            Cursor c = null;
            try {
                c = this.app.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(this.tree, f.id),
                    new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
                while (c != null && c.moveToNext()) {
                    String n = c.getString(1);
                    if (n == null || n.startsWith(".") || MediaDir.kind(n) != MediaDir.PICTURE) continue;
                    if (best == null || n.compareToIgnoreCase(best) < 0) {
                        best = n;
                        bestId = c.getString(0);
                    }
                }
            } catch (Exception ex) {
                return null;
            } finally {
                if (c != null) c.close();
            }
        }
        if (best == null) return null;
        MediaDir.Entry pic = new MediaDir.Entry();
        pic.name = best;
        pic.id = bestId;
        pic.kind = MediaDir.PICTURE;
        return pic;
    }

    /** The tag the card Up came from carries (for the tests). */
    static final int R_FOCUS = 0x7f0b1002;

    /** A menu item picked: adding to the prompt library reads and stores the file off the main thread (a large one takes a while). */
    void pick(final MediaDir.Entry e, final int which) {
        if (which == MediaDir.UPSCALE_ITEM) {
            this.upscale(e);
            return;
        }
        if (which == 2) {
            this.app.setNow(this.menuPicked(e, which));
            return;
        }
        this.pick(e, which, false);
    }

    /** The question shown last before Add to DB (for the tests). */
    static AlertDialog lastUpscaleAsk;

    /** Add to DB; a picture smaller than the last upscaled one, or not 4:7, is asked about first (unless `asked`). */
    void pick(final MediaDir.Entry e, final int which, final boolean asked) {
        this.app.setNow("Adding " + e.name + " to the prompt library\u2026");
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            String said;
            if (!asked && e.kind == MediaDir.PICTURE && !MediaDir.tooBig(e.size)) {
                String q;
                try {
                    q = UpscaleCheck.question(this.app.getFilesDir(), e.name, this.read(e));
                } catch (Exception ex) {
                    q = null;
                }
                if (q != null) {
                    final String question = q;
                    this.main.post(() -> {
                        PyJav.KEEPING.decrementAndGet();
                        this.app.setNow("");
                        lastUpscaleAsk = new AlertDialog.Builder(this.app).setTitle(which == 1 ? "Add to DB result files" : "Add to DB Reference files").setMessage(question)
                            .setPositiveButton("Upscale first", (d, w) -> this.upscale(e))
                            .setNeutralButton("Add as it is", (d, w) -> this.pick(e, which, true))
                            .setNegativeButton("Cancel", null).show();
                    });
                    return;
                }
            }
            try {
                said = this.menuPicked(e, which);
            } catch (Throwable ex) {
                said = "Could not add " + e.name + " (" + ex.getClass().getSimpleName() + ")";
            }
            final String shown = said;
            this.main.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                this.app.setNow(shown);
                // In the DB: then which group it goes in (No group: none).
                if ((which == 0 || which == 1) && shown.contains(" is in the DB as a ")) GroupDialogs.afterAdd(this.app, e.name, e.size);
            });
        }, "pulsekit-add-to-db").start();
    }

    /** What a menu item does: 0 and 1 add the file to the prompt library, 2 adds it to the default playlist (or takes it out when it is in). Returns the status line. */
    String menuPicked(MediaDir.Entry e, int which) {
        if (which == 2) {
            View card = this.body.findViewWithTag("media-card:" + e.name);
            FrameLayout box = card instanceof LinearLayout && ((LinearLayout) card).getChildAt(0) instanceof FrameLayout ? (FrameLayout) ((LinearLayout) card).getChildAt(0) : null;
            if (MediaPlaylist.has(this.app.getFilesDir(), this.folderKey(), e.id)) {
                // Remove from default playlist: the badge goes at once.
                String said = MediaPlaylist.remove(this.app.getFilesDir(), this.folderKey(), e.id, e.name);
                this.paintPlaylist();
                View badge = box == null ? null : box.findViewWithTag("media-in-playlist:" + e.name);
                if (badge != null) box.removeView(badge);
                return said;
            }
            String said = MediaPlaylist.add(this.app.getFilesDir(), this.folderKey(), e.id, e.name, e.size);
            this.paintPlaylist();
            // Its card gets the badge at once.
            if (box != null) this.badge(box, e);
            return said;
        }
        if (MediaDir.tooBig(e.size)) return e.name + " is over the library's 128 MB, so it is not in the DB";
        byte[] bytes;
        try {
            bytes = this.read(e);
        } catch (Exception ex) {
            return "Could not read " + e.name;
        }
        return MediaDir.addToDb(this.app.getFilesDir(), e.name, bytes, which == 1);
    }

    /** The file's bytes (up to the library's 128 MB, plus one to tell). */
    private byte[] read(MediaDir.Entry e) throws Exception {
        InputStream in = this.app.getContentResolver().openInputStream(this.uri(e));
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 128 * 1024 * 1024) break;
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The ☰ badge in a card's top left corner: the file is in this folder's playlist. */
    void badge(FrameLayout box, MediaDir.Entry e) {
        if (box.findViewWithTag("media-in-playlist:" + e.name) != null) return;
        TextView mark = this.app.text("\u2630", 14, true);
        mark.setTag("media-in-playlist:" + e.name);
        mark.setTextColor(UiKit.BG);
        mark.setBackgroundColor(UiKit.ACCENT);
        mark.setPadding(this.app.dp(5), this.app.dp(2), this.app.dp(5), this.app.dp(3));
        mark.setContentDescription("In this folder's playlist");
        box.addView(mark, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
    }

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toUpperCase() : "FILE";
    }

    /** A picture's or a video's thumbnail about `px` across; null when there is none. */
    /** Each picture's and video's "1920×1080 · 16:9" ("" when it could not be read), by file. */
    private static final java.util.Map<String, String> SIZES = new java.util.concurrent.ConcurrentHashMap<String, String>();

    /** A picture's or a video's resolution and aspect ratio, read without decoding it: "1920×1080 · 16:9", or "". */
    String sizeOf(MediaDir.Entry e) {
        try {
            if (e.kind == MediaDir.PICTURE) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                InputStream in = this.app.getContentResolver().openInputStream(this.uri(e));
                try {
                    BitmapFactory.decodeStream(in, null, bounds);
                } finally {
                    if (in != null) in.close();
                }
                return MediaSize.label(bounds.outWidth, bounds.outHeight);
            }
            if (e.kind == MediaDir.VIDEO) {
                MediaMetadataRetriever media = new MediaMetadataRetriever();
                try {
                    if (this.onDisk(e)) media.setDataSource(e.id);
                    else media.setDataSource(this.app, this.uri(e));
                    int[] wh = PromptSheet.videoSize(media);
                    return MediaSize.label(wh[0], wh[1]);
                } finally {
                    try {
                        media.release();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {
            // not known
        }
        return "";
    }

    Bitmap thumb(MediaDir.Entry e, int px) {
        Uri u = this.uri(e);
        if (this.tree != null) {
            try {
                Bitmap b = DocumentsContract.getDocumentThumbnail(this.app.getContentResolver(), u, new Point(px, px), null);
                if (b != null) return b;
            } catch (Throwable ignored) {
                // read it ourselves
            }
        }
        try {
            if (e.kind == MediaDir.PICTURE) return this.decode(u, px);
            if (e.kind == MediaDir.VIDEO) {
                MediaMetadataRetriever media = new MediaMetadataRetriever();
                try {
                    if (this.onDisk(e)) media.setDataSource(e.id);
                    else media.setDataSource(this.app, u);
                    // A small frame straight away (a 4K video's full frame took 33 MB per card).
                    Bitmap frame = null;
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        try {
                            frame = media.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, px, px);
                        } catch (Throwable ignored) {
                            // the full frame below
                        }
                    }
                    if (frame == null) frame = media.getFrameAtTime(0);
                    if (frame == null) frame = media.getFrameAtTime();
                    return frame == null ? null : scaleDown(frame, px);
                } finally {
                    try {
                        media.release();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable ignored) {
            // the card keeps its type
        }
        return null;
    }

    /** A picture decoded at about `px` on its longer side (a power of two smaller than itself). */
    Bitmap decode(Uri u, int px) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in = this.app.getContentResolver().openInputStream(u);
        try {
            BitmapFactory.decodeStream(in, null, bounds);
        } finally {
            if (in != null) in.close();
        }
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / (sample * 2) >= px && sample < 128) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        in = this.app.getContentResolver().openInputStream(u);
        try {
            return BitmapFactory.decodeStream(in, null, opts);
        } finally {
            if (in != null) in.close();
        }
    }

    private static Bitmap scaleDown(Bitmap b, int px) {
        int max = Math.max(b.getWidth(), b.getHeight());
        if (max <= px || max < 1) return b;
        float s = px / (float) max;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true);
    }

    /** A card's tap: a picture full size, a video or a sound played. */
    void openEntry(MediaDir.Entry e) {
        this.openEntry(e, this.entries);
    }

    /** As above; a picture's Previous / Next go through the pictures in `among` (the folder, or the playlist). */
    void openEntry(MediaDir.Entry e, List<MediaDir.Entry> among) {
        if (e.dbId != 0) this.promptFile(e);
        else if (e.kind == MediaDir.PICTURE) this.picture(e, among);
        else if (e.kind == MediaDir.VIDEO) this.video(e, among);
        else if (e.kind == MediaDir.SOUND) this.sound(e);
    }

    /** The text of a library prompt file (a tap on a ★ card). */
    static AlertDialog lastPromptFile;

    void promptFile(MediaDir.Entry e) {
        String text;
        try {
            text = new String(PromptVault.open(this.app.getFilesDir()).fileBytes(e.dbId, PromptVault.PROMPT_FILE), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            this.app.setNow("Could not read " + e.name);
            return;
        }
        lastOpened = "prompt " + e.name;
        TextView body = this.app.text(text, 13, false);
        body.setTag("media-prompt-text");
        body.setTextIsSelectable(true);
        body.setPadding(this.app.dp(16), this.app.dp(8), this.app.dp(16), this.app.dp(8));
        ScrollView scroll = new ScrollView(this.app);
        scroll.addView(body);
        lastPromptFile = new AlertDialog.Builder(this.app).setTitle(MediaDir.shownName(e)).setView(scroll).setPositiveButton("Close", null).show();
    }

    /** Choose file on a library prompt file: written into PyJav's input folder, then handed on. */
    void chosenPromptFile(final MediaDir.Entry e, final RefBrowser.Picked picked) {
        File got;
        try {
            byte[] bytes = PromptVault.open(this.app.getFilesDir()).fileBytes(e.dbId, PromptVault.PROMPT_FILE);
            File dir = new File(this.app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            got = new File(dir, e.name.replace('/', '_'));
            java.nio.file.Files.write(got.toPath(), bytes);
            InputOrigins.record(this.app.getFilesDir(), got.getAbsolutePath(), e.dbId, PromptVault.PROMPT_FILE);
        } catch (Exception ex) {
            this.app.setNow("Could not read " + e.name);
            return;
        }
        if (this.dialog != null) this.dialog.dismiss();
        this.app.setNow("Chose " + e.name);
        picked.picked(e.name, got);
    }

    private Dialog fullScreen() {
        Dialog d = new Dialog(this.app, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        return d;
    }

    /** A picture full size: pinch to zoom, drag to look around; Previous / Next go through the folder's pictures. */
    void picture(MediaDir.Entry first, List<MediaDir.Entry> among) {
        lastOpened = "picture:" + first.name;
        final List<MediaDir.Entry> pictures = new ArrayList<MediaDir.Entry>();
        int found = -1;
        for (MediaDir.Entry x : among) {
            if (x.kind != MediaDir.PICTURE) continue;
            if (x == first || x.id.equals(first.id)) found = pictures.size();
            pictures.add(x);
        }
        if (found < 0) {
            pictures.clear();
            pictures.add(first);
            found = 0;
        }
        final int[] at = new int[] {found};
        final Dialog d = this.fullScreen();
        FrameLayout frame = new FrameLayout(this.app);
        frame.setBackgroundColor(0xff000000);
        final ImageView image = new ImageView(this.app);
        image.setTag("media-full");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
        final TextView title = this.app.text("", 13, false);
        title.setPadding(this.app.dp(12), this.app.dp(10), this.app.dp(12), this.app.dp(6));
        title.setTag("media-full-title");
        title.setBackgroundColor(0x88000000);
        frame.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout bar = this.app.row();
        bar.setBackgroundColor(0x88000000);
        bar.setPadding(this.app.dp(6), this.app.dp(4), this.app.dp(6), this.app.dp(4));
        final int side = Math.max(this.app.getResources().getDisplayMetrics().widthPixels, this.app.getResources().getDisplayMetrics().heightPixels);
        // Crop image (the picture's long press): a frame over it (CropLayer); its long press saves or upscales the crop.
        final CropLayer crop = new CropLayer(this.app, image, first.name, new CropLayer.Actions() {
            public Bitmap cut(CropBox box) throws Exception {
                return MediaBrowser.this.cut(MediaBrowser.this.uri(pictures.get(at[0])), box);
            }

            public String save(String name, byte[] png) throws Exception {
                String[] where = new String[] {MediaBrowser.this.tree == null ? "" : MediaBrowser.this.tree.toString(), here()};
                String said = "Saved the crop as " + saveInto(MediaBrowser.this.app, where, null, name, "image/png", png);
                lastCrop = name;
                // The folder lists it (behind the picture).
                MediaBrowser.this.main.post(() -> {
                    if (MediaBrowser.this.dialog != null && MediaBrowser.this.dialog.isShowing()) MediaBrowser.this.load(here());
                });
                return said;
            }

            public void upscale(String name, byte[] png) throws Exception {
                File dir = new File(MediaBrowser.this.app.getCacheDir(), "pyjav-in");
                if (!dir.isDirectory()) dir.mkdirs();
                File in = new File(dir, name);
                java.nio.file.Files.write(in.toPath(), png);
                ORIGIN.put(in.getAbsolutePath(), new String[] {MediaBrowser.this.tree == null ? "" : MediaBrowser.this.tree.toString(), here()});
                MediaBrowser.this.upscaleFile(in, d);
            }
        });
        crop.setTag("media-full-crop-box");
        frame.addView(crop, new FrameLayout.LayoutParams(-1, -1));
        final Runnable cropOff = crop::stop;
        final Runnable show = () -> {
            cropOff.run();
            MediaDir.Entry e = pictures.get(at[0]);
            crop.name = e.name;
            lastOpened = "picture:" + e.name;
            title.setText(e.name + "  (" + (at[0] + 1) + " of " + pictures.size() + ")");
            image.setScaleX(1f);
            image.setScaleY(1f);
            image.setTranslationX(0f);
            image.setTranslationY(0f);
            image.setImageBitmap(null);
            READER.execute(() -> {
                Bitmap b;
                try {
                    b = this.decode(this.uri(e), side);
                } catch (Throwable ex) {
                    b = null;
                }
                final Bitmap got = b;
                this.main.post(() -> {
                    if (got != null && pictures.get(at[0]) == e) image.setImageBitmap(got);
                    else if (got == null) this.app.setNow("Could not read " + e.name);
                });
            });
        };
        TextView prev = this.app.pill("Previous", false, v -> {
            if (at[0] > 0) {
                at[0]--;
                show.run();
            }
        });
        prev.setTag("media-full-prev");
        TextView next = this.app.pill("Next", false, v -> {
            if (at[0] < pictures.size() - 1) {
                at[0]++;
                show.run();
            }
        });
        next.setTag("media-full-next");
        TextView close = this.app.pill("Close", true, v -> d.dismiss());
        close.setTag("media-full-close");
        bar.addView(prev);
        bar.addView(next);
        bar.addView(new View(this.app), new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(close);
        frame.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        // Small arrows at the picture's left and right edges: the previous and the next picture.
        for (int edge = 0; edge < 2; edge++) {
            final boolean back = edge == 0;
            TextView arrow = this.app.text(back ? "\u2039" : "\u203a", 30, true);
            arrow.setTag(back ? "media-full-left" : "media-full-right");
            arrow.setGravity(Gravity.CENTER);
            arrow.setBackgroundColor(0x66000000);
            arrow.setOnClickListener(v -> (back ? prev : next).performClick());
            FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(this.app.dp(40), this.app.dp(64), Gravity.CENTER_VERTICAL | (back ? Gravity.START : Gravity.END));
            frame.addView(arrow, alp);
        }
        // Pinch to zoom (1x to 6x); one finger moves a zoomed picture.
        final ScaleGestureDetector pinch = new ScaleGestureDetector(this.app, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector g) {
                float s = Math.max(1f, Math.min(6f, image.getScaleX() * g.getScaleFactor()));
                image.setScaleX(s);
                image.setScaleY(s);
                if (s == 1f) {
                    image.setTranslationX(0f);
                    image.setTranslationY(0f);
                }
                return true;
            }
        });
        final float[] down = new float[2];
        // A long press: the picture's menu (Crop image).
        final android.view.GestureDetector press = new android.view.GestureDetector(this.app, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent ev) {
                if (pinch.isInProgress() || crop.cropping()) return;
                MediaBrowser.this.pictureMenu(pictures.get(at[0]), () -> {
                    image.setScaleX(1f);
                    image.setScaleY(1f);
                    image.setTranslationX(0f);
                    image.setTranslationY(0f);
                    crop.start();
                });
            }
        });
        // A swipe (not zoomed in, not cropping): the previous or the next picture, as the arrows.
        final Swipe swipe = new Swipe(this.app);
        image.setOnTouchListener((v, ev) -> {
            press.onTouchEvent(ev);
            pinch.onTouchEvent(ev);
            int step = swipe.feed(ev);
            if (step != 0 && image.getScaleX() <= 1f && !crop.cropping()) {
                (step < 0 ? prev : next).performClick();
                return true;
            }
            if (ev.getPointerCount() == 1 && image.getScaleX() > 1f) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    down[0] = ev.getRawX() - image.getTranslationX();
                    down[1] = ev.getRawY() - image.getTranslationY();
                } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && !pinch.isInProgress()) {
                    image.setTranslationX(ev.getRawX() - down[0]);
                    image.setTranslationY(ev.getRawY() - down[1]);
                }
            } else if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = ev.getRawX() - image.getTranslationX();
                down[1] = ev.getRawY() - image.getTranslationY();
            }
            return true;
        });
        d.setContentView(frame);
        d.show();
        show.run();
    }

    /** The picture viewer's menu shown last (for the tests). */
    static AlertDialog lastPictureMenu;

    /** A picture's long press in the viewer: Crop image. */
    void pictureMenu(MediaDir.Entry e, Runnable cropImage) {
        lastPictureMenu = new AlertDialog.Builder(this.app).setTitle(e.name)
            .setItems(new String[] {CropBox.CROP}, (dlg, w) -> cropImage.run())
            .setNegativeButton("Cancel", null).show();
    }

    /** Where an ImageView draws its picture (FIT_CENTER, unzoomed): x, y, width, height; null before it has one. */
    static float[] drawnRect(ImageView image) {
        android.graphics.drawable.Drawable dr = image.getDrawable();
        if (dr == null || dr.getIntrinsicWidth() <= 0 || dr.getIntrinsicHeight() <= 0 || image.getWidth() <= 0 || image.getHeight() <= 0) return null;
        float s = Math.min(image.getWidth() / (float) dr.getIntrinsicWidth(), image.getHeight() / (float) dr.getIntrinsicHeight());
        float w = dr.getIntrinsicWidth() * s, h = dr.getIntrinsicHeight() * s;
        return new float[] {(image.getWidth() - w) / 2f, (image.getHeight() - h) / 2f, w, h};
    }

    /** The last crop saved (its name), for the tests. */
    static String lastCrop;

    /** The box cut from the picture at its full size (at most 8192 pixels a side, halved until it fits). */
    Bitmap cut(Uri u, CropBox box) throws Exception {
        try {
            InputStream in = this.app.getContentResolver().openInputStream(u);
            try {
                @SuppressWarnings("deprecation")
                android.graphics.BitmapRegionDecoder dec = android.graphics.BitmapRegionDecoder.newInstance(in, false);
                int[] p = box.pixels(dec.getWidth(), dec.getHeight());
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 1;
                while (Math.max(p[2], p[3]) / o.inSampleSize > 8192) o.inSampleSize *= 2;
                Bitmap got = dec.decodeRegion(new android.graphics.Rect(p[0], p[1], p[0] + p[2], p[1] + p[3]), o);
                dec.recycle();
                if (got != null) return got;
            } finally {
                if (in != null) in.close();
            }
        } catch (Throwable ex) {
            // A picture the region reader takes not: read whole below.
        }
        Bitmap whole = this.decode(u, 8192);
        if (whole == null) throw new java.io.IOException("the picture could not be read");
        int[] p = box.pixels(whole.getWidth(), whole.getHeight());
        return Bitmap.createBitmap(whole, p[0], p[1], p[2], p[3]);
    }

    /** The video player's state: the last one's, for the tests too. */
    static final class Video {
        VideoView view;
        MediaPlayer media;
        boolean muted;
        int volume = 100;
        double zoom = 1;
        double speed = 1;
        boolean loop;
        TextView mute;
        TextView speedLabel;
        TextView time;
        /** Mute and volume (shown by a tap), C, and the position bar. */
        View sound;
        TextView capture;
        /** R: this frame as a reference of a Sogni program (shown with C while paused). */
        TextView ref;
        /** R pressed: the frame C's capture reads goes on as a reference. */
        boolean refNext;
        SeekBar position;
        /** The last frame C saved, for the tests. */
        String captured;

        /** The position bar is being dragged: it is not moved by the video meanwhile. */
        boolean dragging;
        /** The player's dialog (C held: closed for ImageUpscaler). */
        android.app.Dialog dialog;

        boolean playing() {
            try {
                return media != null ? media.isPlaying() : view.isPlaying();
            } catch (Exception ex) {
                return false;
            }
        }

        /** Pause, or (paused) play on: the Pause button, a tap on the video. */
        void pauseOrResume() {
            try {
                if (playing()) view.pause();
                else view.start();
            } catch (RuntimeException ignored) {
                // not ready
            }
            paintTransport();
        }

        /** To `ms`, showing that frame even while paused (the closest frame, not only a key frame). */
        void seekFrame(int ms) {
            try {
                if (media != null && android.os.Build.VERSION.SDK_INT >= 26) media.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
                else view.seekTo(ms);
            } catch (RuntimeException ignored) {
                // not ready
            }
            showTime();
        }

        /** C shows while paused; the position bar follows the video. */
        void paintTransport() {
            boolean on = false;
            int at = 0;
            int len = 0;
            try {
                on = media != null ? media.isPlaying() : view.isPlaying();
                at = media != null ? media.getCurrentPosition() : view.getCurrentPosition();
                len = media != null ? media.getDuration() : view.getDuration();
            } catch (Exception ignored) {
                // not ready, or closing
            }
            if (capture != null) capture.setVisibility(!on && media != null ? View.VISIBLE : View.GONE);
            if (ref != null) ref.setVisibility(!on && media != null ? View.VISIBLE : View.GONE);
            if (position != null && len > 0 && !dragging) position.setProgress((int) Math.min(1000, (long) at * 1000 / len));
        }

        /** The player's window and the app's: kept on while it plays. */
        android.view.Window window;
        android.view.Window appWindow;
        boolean awake;

        /** The screen stays on while the video plays; paused, stopped or at its end the phone's own timeout applies. */
        void keepAwake() {
            boolean on = false;
            try {
                on = media != null ? media.isPlaying() : view.isPlaying();
            } catch (Exception ignored) {
                // released: closing
            }
            setAwake(on);
        }

        void setAwake(boolean on) {
            if (awake == on) return;
            awake = on;
            screenOn(window, on);
            screenOn(appWindow, on);
        }

        /** Times a long press brought Mute and the volume to the top, for the tests. */
        int raised;

        /** The Sound dialog (Mute and the volume), opened by a long press. */
        Runnable soundDialog;

        /** A long press: the video pauses, and a small Sound dialog shows Mute and the volume (OK closes it). */
        void hold() {
            try {
                if (playing()) view.pause();
            } catch (RuntimeException ignored) {
                // not ready
            }
            raised++;
            paintTransport();
            if (soundDialog != null) soundDialog.run();
        }

        /** The time label: where it is / how long it is. */
        void showTime() {
            if (time == null || view == null) return;
            int at = 0;
            int len = 0;
            try {
                // The player once it is ready, else the view's own idea.
                at = media != null ? media.getCurrentPosition() : view.getCurrentPosition();
                len = media != null ? media.getDuration() : view.getDuration();
            } catch (Exception ignored) {
                // not ready
            }
            time.setText(clock(Math.max(0, at)) + " / " + clock(Math.max(0, len)));
        }
        TextView level;
        TextView zoomLabel;

        float gain() {
            return muted ? 0f : volume / 100f;
        }

        void applyVolume() {
            if (media != null) {
                try {
                    media.setVolume(gain(), gain());
                } catch (IllegalStateException ignored) {
                    // released: closing
                }
            }
            if (level != null) level.setText(muted ? "muted" : volume + "%");
        }

        void zoomTo(double z) {
            zoom = MediaDir.clampZoom(z);
            view.setScaleX((float) zoom);
            view.setScaleY((float) zoom);
            if (zoom == 1) {
                view.setTranslationX(0f);
                view.setTranslationY(0f);
            }
            if (zoomLabel != null) zoomLabel.setText(MediaDir.zoomLabel(zoom));
        }

        /** The playback speed; a paused video stays paused (setting the speed would start it). */
        void speedTo(double v) {
            speed = MediaDir.speed(v);
            if (speedLabel != null) speedLabel.setText("Speed " + MediaDir.speedLabel(speed));
            if (media == null) return;
            try {
                boolean was = media.isPlaying();
                media.setPlaybackParams(media.getPlaybackParams().setSpeed((float) speed));
                if (!was) media.pause();
            } catch (Exception ignored) {
                // this phone's player cannot change speed (or it is closing)
            }
        }
    }

    /** Keeps the screen on (or lets it time out again) while `window` shows: the window flag, which every phone honours. */
    static void screenOn(android.view.Window window, boolean on) {
        if (window == null) return;
        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    static Video lastVideo;

    /**
     * A video full screen, playing: the system's controls (play / pause, position) on a tap, and
     * below Mute, a volume slider, and Zoom − / + (pinch too; drag to look around a zoomed video).
     * With Loop videos ticked it starts again at its end.
     */
    void video(MediaDir.Entry e) {
        this.video(e, this.entries);
    }

    /** As above; the small arrows at the video's left and right edges open the previous and the next video of `among`. */
    void video(final MediaDir.Entry e, final List<MediaDir.Entry> among) {
        lastOpened = "video:" + e.name;
        // The folder's videos, in its order: the arrows go through them.
        final List<MediaDir.Entry> videos = new ArrayList<MediaDir.Entry>();
        int found = -1;
        if (among != null) for (MediaDir.Entry x : among) {
            if (x.kind != MediaDir.VIDEO) continue;
            if (x == e || (x.id != null && x.id.equals(e.id))) found = videos.size();
            videos.add(x);
        }
        final int at = found;
        if (this.app.playing) this.app.playback.stop();
        final Dialog d = this.fullScreen();
        final Video p = new Video();
        // As the last video was left: volume, zoom and speed.
        p.loop = MediaDir.loopVideos;
        p.volume = MediaDir.volume;
        p.muted = MediaDir.muted;
        p.speed = MediaDir.speed;
        lastVideo = p;
        LinearLayout col = this.app.col();
        col.setBackgroundColor(0xff000000);
        LinearLayout top = this.app.row();
        top.setPadding(this.app.dp(12), this.app.dp(6), this.app.dp(6), this.app.dp(6));
        TextView title = this.app.text(e.name + (p.loop ? "  \u00b7 looping" : ""), 13, false);
        title.setTag("media-video-title");
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        // Where it is and how long it is: 0:12 / 1:30.
        p.time = this.app.text("0:00 / 0:00", 13, false);
        p.time.setTag("media-video-time");
        p.time.setPadding(this.app.dp(8), 0, this.app.dp(8), 0);
        top.addView(p.time);
        final Runnable[] clock = new Runnable[1];
        // The bars (the top, the transport, zoom and speed): hidden while it plays turned sideways.
        final View[] chrome = new View[3];
        clock[0] = () -> {
            if (!d.isShowing()) return;
            p.showTime();
            p.keepAwake();
            p.paintTransport();
            barsFor(this.app, p.playing(), chrome);
            this.main.postDelayed(clock[0], 250);
        };
        TextView close = this.app.pill("Close", true, v -> d.dismiss());
        close.setTag("media-video-close");
        top.addView(close);
        col.addView(top);
        final FrameLayout frame = new FrameLayout(this.app);
        frame.setClipChildren(true);
        p.view = new VideoView(this.app);
        p.view.setTag("media-video");
        frame.addView(p.view, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        // Small arrows at the left and right edges: the folder's previous and next video (none at its first or last).
        // A swipe across the video works as them.
        final View[] arrows = new View[2];
        for (int edge = 0; edge < 2; edge++) {
            final boolean back = edge == 0;
            final int to = at < 0 ? -1 : back ? at - 1 : at + 1;
            if (to < 0 || to >= videos.size()) continue;
            TextView arrow = this.app.text(back ? "\u2039" : "\u203a", 30, true);
            arrow.setTag(back ? "media-video-left" : "media-video-right");
            arrows[edge] = arrow;
            arrow.setGravity(Gravity.CENTER);
            arrow.setBackgroundColor(0x66000000);
            arrow.setOnClickListener(v -> {
                // Kept before the next opens (the dismiss saves it too, later).
                MediaDir.volume = p.volume;
                MediaDir.muted = p.muted;
                MediaDir.zoom = p.zoom;
                MediaDir.speed = p.speed;
                // The next opens over this one, then this closes under it: the folder is not shown between.
                try {
                    if (p.playing()) p.view.pause();
                } catch (RuntimeException ignored) {
                    // not ready
                }
                this.video(videos.get(to), among);
                this.main.postDelayed(d::dismiss, 400);
            });
            frame.addView(arrow, new FrameLayout.LayoutParams(this.app.dp(40), this.app.dp(64), Gravity.CENTER_VERTICAL | (back ? Gravity.START : Gravity.END)));
        }
        col.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        // Mute and volume.
        LinearLayout sound = this.app.row();
        sound.setPadding(this.app.dp(8), this.app.dp(4), this.app.dp(8), 0);
        p.mute = this.app.pill(p.muted ? "Unmute" : "Mute", p.muted, v -> {
            p.muted = !p.muted;
            p.mute.setText(p.muted ? "Unmute" : "Mute");
            this.app.paintChip(p.mute, p.muted);
            p.applyVolume();
            MediaDir.muted = p.muted;
            save(this.app);
        });
        p.mute.setTag("media-video-mute");
        sound.addView(p.mute);
        sound.addView(this.app.text("Volume", 13, false));
        SeekBar volume = new SeekBar(this.app);
        volume.setMax(100);
        volume.setProgress(p.volume);
        volume.setTag("media-video-volume");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                p.volume = value;
                p.applyVolume();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        sound.addView(volume, new LinearLayout.LayoutParams(0, -2, 1f));
        p.level = this.app.text(p.volume + "%", 13, false);
        p.level.setTag("media-video-level");
        p.level.setMinWidth(this.app.dp(52));
        sound.addView(p.level);
        // Mute and volume at the top, kept hidden: a long press on the video shows them in the Sound dialog.
        sound.setTag("media-video-sound");
        sound.setVisibility(View.GONE);
        p.sound = sound;
        final SeekBar rowVolume = volume;
        p.soundDialog = () -> this.soundDialog(p, rowVolume);
        col.addView(sound, 1);
        // Stop, Play, Pause and the position: always shown; C (capture the frame) while paused.
        LinearLayout transport = this.app.row();
        transport.setPadding(this.app.dp(8), this.app.dp(4), this.app.dp(8), 0);
        TextView stopBtn = this.app.pill("\u25a0 Stop", false, v -> {
            try {
                p.view.pause();
                p.view.seekTo(0);
            } catch (RuntimeException ignored) {
                // not ready
            }
            p.paintTransport();
        });
        stopBtn.setTag("media-video-stop");
        // Play while it plays pauses it; Pause while paused plays on.
        TextView playBtn = this.app.pill("\u25b6 Play", false, v -> {
            if (p.playing()) p.view.pause();
            else p.view.start();
            p.paintTransport();
        });
        playBtn.setTag("media-video-play");
        TextView pauseBtn = this.app.pill("\u275a\u275a Pause", false, v -> p.pauseOrResume());
        pauseBtn.setTag("media-video-pause");
        p.capture = this.app.pill("C", false, v -> this.capture(e, p, false));
        // C held: the frame captured, then ImageUpscaler with it as the input.
        p.capture.setOnLongClickListener(v -> {
            this.capture(e, p, true);
            return true;
        });
        p.capture.setTag("media-video-capture");
        p.capture.setContentDescription("Capture this frame into the SC folder");
        p.capture.setVisibility(View.GONE);
        // R: this frame straight on as a reference of a Sogni program (not on a Choose file row: C fills the row there).
        TextView[] shownRow = new TextView[] {stopBtn, playBtn, pauseBtn, p.capture};
        if (this.frameRow == null && AppSettings.showSogni() && !AppSettings.musicOnly()) {
            p.ref = this.app.pill("R", false, v -> {
                p.refNext = true;
                this.capture(e, p, false);
            });
            p.ref.setTag("media-video-ref");
            p.ref.setContentDescription("This frame as a reference of a Sogni program");
            p.ref.setVisibility(View.GONE);
            shownRow = new TextView[] {stopBtn, playBtn, pauseBtn, p.capture, p.ref};
        } else if (this.frameRow != null) {
            p.capture.setContentDescription("This frame into the row");
        }
        for (TextView t : shownRow) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(6);
            transport.addView(t, lp);
        }
        p.position = new SeekBar(this.app);
        p.position.setMax(1000);
        p.position.setTag("media-video-position");
        p.position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (!fromUser) return;
                try {
                    // Paused too: the frame there is shown.
                    int len = p.media != null ? p.media.getDuration() : p.view.getDuration();
                    if (len > 0) p.seekFrame((int) ((long) len * value / 1000));
                } catch (RuntimeException ignored) {
                    // not ready
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                p.dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                p.dragging = false;
            }
        });
        transport.addView(p.position, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(transport);
        // Zoom.
        LinearLayout zoom = this.app.row();
        zoom.setPadding(this.app.dp(8), 0, this.app.dp(8), this.app.dp(6));
        TextView out = this.app.pill("Zoom \u2212", false, v -> p.zoomTo(MediaDir.zoom(p.zoom, false)));
        out.setTag("media-video-zoom-out");
        TextView in = this.app.pill("Zoom +", false, v -> p.zoomTo(MediaDir.zoom(p.zoom, true)));
        in.setTag("media-video-zoom-in");
        TextView fit = this.app.pill("Fit", false, v -> p.zoomTo(1));
        fit.setTag("media-video-fit");
        p.zoomLabel = this.app.text("100%", 13, false);
        p.zoomLabel.setTag("media-video-zoom");
        p.zoomLabel.setPadding(this.app.dp(8), 0, 0, 0);
        zoom.addView(out);
        zoom.addView(in);
        zoom.addView(fit);
        zoom.addView(p.zoomLabel);
        // Playback speed: a list from 0.25x to 2x.
        zoom.addView(new View(this.app), new LinearLayout.LayoutParams(0, 1, 1f));
        p.speedLabel = this.app.pill("Speed " + MediaDir.speedLabel(p.speed), false, v -> {
            final String[] labels = new String[MediaDir.SPEEDS.length];
            int now = 0;
            for (int i = 0; i < labels.length; i++) {
                labels[i] = MediaDir.speedLabel(MediaDir.SPEEDS[i]);
                if (MediaDir.SPEEDS[i] == p.speed) now = i;
            }
            new AlertDialog.Builder(this.app)
                .setTitle("Playback speed")
                .setSingleChoiceItems(labels, now, (dd, which) -> {
                    p.speedTo(MediaDir.SPEEDS[which]);
                    dd.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        p.speedLabel.setTag("media-video-speed");
        zoom.addView(p.speedLabel);
        col.addView(zoom);
        chrome[0] = top;
        chrome[1] = transport;
        chrome[2] = zoom;
        // The last video's zoom.
        p.zoomTo(MediaDir.zoom);
        // No system controls over the picture: Stop, Play, Pause, C and the position are the row below,
        // shown from the start. Pinch to zoom; one finger moves a zoomed video; a tap pauses or plays on.
        final ScaleGestureDetector pinch = new ScaleGestureDetector(this.app, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector g) {
                p.zoomTo(p.zoom * g.getScaleFactor());
                return true;
            }
        });
        final float[] down = new float[3];
        // Holding a finger on the video pauses it and brings Mute and the volume to the top; held again, it plays.
        final int slop = android.view.ViewConfiguration.get(this.app).getScaledTouchSlop();
        final float[] start = new float[2];
        final Runnable hold = () -> {
            down[2] = 1;
            p.hold();
        };
        final Swipe swipe = new Swipe(this.app);
        final View.OnTouchListener touch = (v, ev) -> {
            pinch.onTouchEvent(ev);
            // A swipe (not zoomed in): the previous or the next video, as the arrows.
            int step = swipe.feed(ev);
            if (step != 0 && p.zoom <= 1.01f && arrows[step < 0 ? 0 : 1] != null) {
                this.main.removeCallbacks(hold);
                arrows[step < 0 ? 0 : 1].performClick();
                return true;
            }
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                down[0] = ev.getRawX() - p.view.getTranslationX();
                down[1] = ev.getRawY() - p.view.getTranslationY();
                down[2] = 0;
                start[0] = ev.getRawX();
                start[1] = ev.getRawY();
                this.main.postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout());
            } else if (act == MotionEvent.ACTION_MOVE) {
                if (Math.abs(ev.getRawX() - start[0]) > slop || Math.abs(ev.getRawY() - start[1]) > slop) this.main.removeCallbacks(hold);
                if (ev.getPointerCount() == 1 && !pinch.isInProgress() && p.zoom > 1) {
                    p.view.setTranslationX(ev.getRawX() - down[0]);
                    p.view.setTranslationY(ev.getRawY() - down[1]);
                    down[2] = 1;
                }
            } else if (act == MotionEvent.ACTION_POINTER_DOWN) {
                this.main.removeCallbacks(hold);
                down[2] = 1;
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                this.main.removeCallbacks(hold);
                // A tap works as Pause: it pauses, or (paused) plays on.
                if (act == MotionEvent.ACTION_UP && down[2] == 0) p.pauseOrResume();
            }
            return true;
        };
        // On the video and around it alike (the video's own tap would only toggle the controls).
        frame.setOnTouchListener(touch);
        p.view.setOnTouchListener(touch);
        p.view.setOnPreparedListener(mp -> {
            p.media = mp;
            mp.setLooping(p.loop);
            p.applyVolume();
            if (p.speed != 1) p.speedTo(p.speed);
            p.showTime();
            p.paintTransport();
        });
        p.view.setOnErrorListener((mp, what, extra) -> {
            this.app.setNow("This phone cannot play " + e.name);
            return true;
        });
        p.view.setVideoURI(this.uri(e));
        p.window = d.getWindow();
        p.appWindow = this.app.getWindow();
        p.dialog = d;
        d.setOnShowListener(x -> this.main.post(clock[0]));
        mayTurn(this.app);
        d.setOnDismissListener(x -> {
            this.main.removeCallbacks(clock[0]);
            portraitAgain(this.app);
            p.setAwake(false);
            // The next video opens as this one was left.
            MediaDir.volume = p.volume;
            MediaDir.muted = p.muted;
            MediaDir.zoom = p.zoom;
            MediaDir.speed = p.speed;
            save(this.app);
            p.media = null;
            p.view.stopPlayback();
        });
        d.setContentView(col);
        d.show();
        p.view.start();
    }

    /** Full screen players open now that let the phone turn (the app is otherwise upright). */
    static int turning;

    /**
     * A full screen player opened: the screen may turn with the phone (as its auto-rotate says), so a
     * wide video plays across the whole screen.
     */
    static void mayTurn(android.app.Activity app) {
        turning++;
        try {
            app.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        } catch (RuntimeException ignored) {
            // the phone keeps it as it is
        }
    }

    /** That player closed: upright again once no other is open. */
    static void portraitAgain(android.app.Activity app) {
        turning = Math.max(0, turning - 1);
        if (turning > 0) return;
        try {
            app.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } catch (RuntimeException ignored) {
            // the phone keeps it as it is
        }
    }

    /** The screen turned sideways. */
    static boolean sideways(android.content.Context c) {
        return c.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    /** The bars a player hid (to show again just those). */
    private static final java.util.Map<View, Boolean> hiddenBars = new java.util.WeakHashMap<View, Boolean>();

    /**
     * Sideways and playing: the bars hidden, the video on the whole screen; paused (or upright) they
     * show again, those that were shown.
     */
    static void barsFor(android.content.Context c, boolean playing, View... bars) {
        boolean hide = playing && sideways(c);
        for (View b : bars) {
            if (b == null) continue;
            if (hide && b.getVisibility() == View.VISIBLE) {
                b.setVisibility(View.GONE);
                hiddenBars.put(b, Boolean.TRUE);
            } else if (!hide && hiddenBars.remove(b) != null) {
                b.setVisibility(View.VISIBLE);
            }
        }
    }

    /** The Sound dialog shown last (for the tests). */
    static AlertDialog lastSoundDialog;

    /** A long press on a video: Mute and a volume slider in a small dialog; kept for the next video; OK closes it. */
    void soundDialog(final Video p, final SeekBar rowVolume) {
        LinearLayout box = this.app.col();
        box.setPadding(this.app.dp(20), this.app.dp(10), this.app.dp(20), 0);
        LinearLayout row = this.app.row();
        final TextView level = this.app.text(p.muted ? "muted" : p.volume + "%", 14, false);
        level.setTag("media-sound-level");
        level.setMinWidth(this.app.dp(60));
        final TextView mute = this.app.pill(p.muted ? "Unmute" : "Mute", p.muted, null);
        mute.setTag("media-sound-mute");
        mute.setOnClickListener(v -> {
            p.muted = !p.muted;
            mute.setText(p.muted ? "Unmute" : "Mute");
            this.app.paintChip(mute, p.muted);
            if (p.mute != null) {
                p.mute.setText(p.muted ? "Unmute" : "Mute");
                this.app.paintChip(p.mute, p.muted);
            }
            p.applyVolume();
            level.setText(p.muted ? "muted" : p.volume + "%");
        });
        row.addView(mute);
        SeekBar slider = new SeekBar(this.app);
        slider.setMax(100);
        slider.setProgress(p.volume);
        slider.setTag("media-sound-volume");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                p.volume = value;
                if (rowVolume != null) rowVolume.setProgress(value);
                p.applyVolume();
                level.setText(p.muted ? "muted" : value + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1f);
        slp.leftMargin = this.app.dp(8);
        row.addView(slider, slp);
        row.addView(level);
        box.addView(row);
        lastSoundDialog = new AlertDialog.Builder(this.app).setTitle("Sound").setView(box)
            .setPositiveButton("OK", null).create();
        // Closed (OK, Back or a tap outside): kept for the next video.
        lastSoundDialog.setOnDismissListener(x -> {
            MediaDir.volume = p.volume;
            MediaDir.muted = p.muted;
            save(this.app);
        });
        lastSoundDialog.show();
    }

    /**
     * C: the paused video's frame saved as a PNG in the SC folder under the video's folder (made
     * when it is not there): clip-0m12s345.png. Read and written off the main thread.
     */
    void capture(final MediaDir.Entry e, final Video p, final boolean upscale) {
        final long ms;
        try {
            ms = p.media != null ? p.media.getCurrentPosition() : p.view.getCurrentPosition();
        } catch (RuntimeException ex) {
            return;
        }
        final Uri u = this.uri(e);
        final boolean disk = this.onDisk(e);
        final String[] where = new String[] {this.tree == null ? "" : this.tree.toString(), here()};
        this.app.setNow("Capturing the frame at " + clock((int) ms) + "\u2026");
        new Thread(() -> {
            String said;
            // The frame saved, for Process as a Sogni reference (C pressed shortly).
            final byte[][] saved = new byte[1][];
            final String[] savedName = new String[1];
            MediaMetadataRetriever media = new MediaMetadataRetriever();
            try {
                if (disk) media.setDataSource(e.id);
                else media.setDataSource(this.app, u);
                Bitmap frame = media.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame == null) throw new java.io.IOException("the phone gave no picture at " + clock((int) ms));
                java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
                frame.compress(Bitmap.CompressFormat.PNG, 100, png);
                String name = MediaDir.frameName(e.name, ms);
                said = "Saved the frame as " + saveInto(this.app, where, MediaDir.FRAMES_DIR, name, "image/png", png.toByteArray());
                p.captured = name;
                saved[0] = png.toByteArray();
                savedName[0] = name;
                if (upscale) {
                    // C held: the frame goes on to ImageUpscaler as its input.
                    File dir = new File(this.app.getCacheDir(), "pyjav-in");
                    if (!dir.isDirectory()) dir.mkdirs();
                    final File in = new File(dir, name);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(in);
                    try {
                        fos.write(png.toByteArray());
                    } finally {
                        fos.close();
                    }
                    ORIGIN.put(in.getAbsolutePath(), where);
                    this.main.post(() -> this.upscaleFile(in, p.dialog));
                }
            } catch (Throwable ex) {
                said = "Could not capture the frame: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            } finally {
                try {
                    media.release();
                } catch (Exception ignored) {}
            }
            final String shown = said;
            this.main.post(() -> {
                this.app.setNow(shown);
                android.widget.Toast.makeText(this.app, shown, android.widget.Toast.LENGTH_SHORT).show();
                // The frame: asked whether it goes on as a Sogni reference (one to upscale: Upscale first, Use as it is, No).
                if (upscale || saved[0] == null) return;
                if (this.frameRow != null) {
                    // A picture row's Choose file: the frame fills the row (asked about upscaling first when it needs it).
                    RefImages.forRow(this.app, savedName[0], saved[0], p.dialog, this.dialog, this.frameRow);
                    return;
                }
                if (p.refNext) {
                    // R: straight on as a reference (asked about upscaling first when it needs it).
                    p.refNext = false;
                    RefImages.offer(this.app, savedName[0], saved[0], p.dialog, this.returner());
                    return;
                }
                if (MediaDir.useRef(savedName[0], saved[0])) this.askProcessRef(savedName[0], saved[0], p.dialog, where);
            });
        }, "pulsekit-capture").start();
    }

    /** Use as a reference's question (upscale first?), shown last (for the tests). */
    static AlertDialog lastRefAsk;

    /**
     * Use as a reference: into a recent Sogni program as Reference file 1 or 2. A picture smaller than
     * the last upscaled one, or not 4:7, is asked about first: Upscale first (ImageUpscaler), or Use as it is.
     */
    void useAsRef(final MediaDir.Entry e, byte[] head) {
        String q = UpscaleCheck.refQuestion(this.app.getFilesDir(), e.name, head);
        if (q == null) {
            this.chosen(e, (name, file) -> ProcessRefDialog.open(this.app, file, this, null));
            return;
        }
        lastRefAsk = new AlertDialog.Builder(this.app).setTitle(MediaDir.PROCESS_REF).setMessage(q)
            .setPositiveButton(UpscaleCheck.UPSCALE_FIRST, (d, w) -> this.upscale(e))
            .setNeutralButton(UpscaleCheck.AS_IT_IS, (d, w) -> this.chosen(e, (name, file) -> ProcessRefDialog.open(this.app, file, this, null)))
            .setNegativeButton("Cancel", null).show();
    }

    /** The question shown last after C (for the tests). */
    static AlertDialog lastProcessAsk;

    void askProcessRef(final String name, final byte[] png, final android.app.Dialog player) {
        this.askProcessRef(name, png, player, null);
    }

    /**
     * C's frame: Use it as a Sogni reference? Yes opens the program and prompt list. A frame smaller
     * than the last upscaled picture, or not 4:7, is asked Upscale first (ImageUpscaler, `where` its
     * folder for the result), Use as it is, or No; No: Picture ready.
     */
    void askProcessRef(final String name, final byte[] png, final android.app.Dialog player, final String[] where) {
        String q = UpscaleCheck.refQuestion(this.app.getFilesDir(), name, png);
        if (q != null) {
            lastProcessAsk = new AlertDialog.Builder(this.app).setTitle(MediaDir.PROCESS_REF).setMessage(q)
                .setPositiveButton(UpscaleCheck.UPSCALE_FIRST, (d, w) -> {
                    try {
                        File dir = new File(this.app.getCacheDir(), "pyjav-in");
                        if (!dir.isDirectory()) dir.mkdirs();
                        File in = new File(dir, name);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(in);
                        try {
                            fos.write(png);
                        } finally {
                            fos.close();
                        }
                        if (where != null) ORIGIN.put(in.getAbsolutePath(), where);
                        this.upscaleFile(in, player);
                    } catch (Exception ex) {
                        this.app.setNow("Could not hand " + name + " to ImageUpscaler: " + ex.getMessage());
                    }
                })
                .setNeutralButton(UpscaleCheck.AS_IT_IS, (d, w) -> ProcessRefDialog.open(this.app, name, png, this, player))
                .setNegativeButton("No", (d, w) -> {
                    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
                    files.add(new JavaRun.FileOut(name, png));
                    PictureOffer.offer(this.app, new JavaRun.Result("Succeeded: " + name, files, 0));
                }).show();
            return;
        }
        lastProcessAsk = new AlertDialog.Builder(this.app).setTitle(MediaDir.PROCESS_REF)
            .setMessage(name + " needs no upscaling. Use it as a reference of a Sogni program (the program and prompt, then Reference file 1 or 2)?")
            .setPositiveButton("Yes", (d, w) -> ProcessRefDialog.open(this.app, name, png, this, player))
            // Not as a reference: Picture ready, with the frame's items (favourites, group, tags, playlist...).
            .setNegativeButton("No", (d, w) -> {
                java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
                files.add(new JavaRun.FileOut(name, png));
                PictureOffer.offer(this.app, new JavaRun.Result("Succeeded: " + name, files, 0));
            }).show();
    }

    /** A sound (a MIDI too, with the phone's own instruments): Play / Pause, Stop and a position bar. */
    void sound(final MediaDir.Entry e) {
        lastOpened = "sound:" + e.name;
        if (this.app.playing) this.app.playback.stop();
        final MediaPlayer player = new MediaPlayer();
        SoundOut.prefer(player);
        lastSound = player;
        LinearLayout col = this.app.col();
        col.setPadding(this.app.dp(16), this.app.dp(8), this.app.dp(16), this.app.dp(8));
        final TextView time = this.app.text("0:00", 13, false);
        time.setTag("media-sound-time");
        final SeekBar where = new SeekBar(this.app);
        where.setTag("media-sound-position");
        final boolean[] ready = new boolean[1];
        final TextView play = this.app.pill("Play", true, null);
        play.setTag("media-sound-play");
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            try {
                if (ready[0]) {
                    where.setProgress(player.getCurrentPosition());
                    time.setText(clock(player.getCurrentPosition()) + " / " + clock(player.getDuration()));
                }
                if (ready[0] && player.isPlaying()) this.main.postDelayed(tick[0], 250);
            } catch (IllegalStateException ignored) {
                // released
            }
        };
        play.setOnClickListener(v -> {
            if (!ready[0]) return;
            if (player.isPlaying()) {
                player.pause();
                play.setText("Play");
            } else {
                player.start();
                play.setText("Pause");
                this.main.post(tick[0]);
            }
        });
        TextView stop = this.app.pill("Stop", false, v -> {
            if (!ready[0]) return;
            player.pause();
            player.seekTo(0);
            play.setText("Play");
            tick[0].run();
        });
        stop.setTag("media-sound-stop");
        where.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser && ready[0]) {
                    player.seekTo(value);
                    time.setText(clock(value) + " / " + clock(player.getDuration()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout buttons = this.app.row();
        buttons.addView(play);
        buttons.addView(stop);
        // Volume and Mute (the Media browser's, kept for next time).
        final boolean[] muted = new boolean[1];
        final SeekBar volume = new SeekBar(this.app);
        volume.setTag("media-sound-volume");
        volume.setMax(100);
        volume.setProgress(MediaDir.volume);
        final TextView level = this.app.text(MediaDir.volume + "%", 12, false);
        level.setTag("media-sound-level");
        final Runnable applyVolume = () -> {
            float v = muted[0] ? 0f : volume.getProgress() / 100f;
            try {
                player.setVolume(v, v);
            } catch (Exception ignored) {
                // released
            }
            level.setText(muted[0] ? "muted" : volume.getProgress() + "%");
        };
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                MediaDir.volume = value;
                applyVolume.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                save(MediaBrowser.this.app);
            }
        });
        final TextView mute = this.app.pill("Mute", false, null);
        mute.setTag("media-sound-mute");
        mute.setOnClickListener(v -> {
            muted[0] = !muted[0];
            mute.setText(muted[0] ? "Unmute" : "Mute");
            applyVolume.run();
        });
        buttons.addView(mute);
        // A drum MIDI with Pulsekit's kit (the imported SoundFont); another MIDI with the phone's own instruments.
        File kitWav = MediaDir.isMidi(e.name) ? this.kitWav(e) : null;
        col.addView(this.app.text(MediaDir.isMidi(e.name) ? (kitWav != null ? "MIDI, played with Pulsekit's kit (the imported SoundFont)" : "MIDI, played with the phone's own instruments") : (e.size / 1024) + " KB", 12, false));
        col.addView(where, new LinearLayout.LayoutParams(-1, -2));
        col.addView(time);
        col.addView(buttons);
        LinearLayout volRow = this.app.row();
        volRow.addView(this.app.text("Volume", 12, false));
        volRow.addView(volume, new LinearLayout.LayoutParams(0, -2, 1f));
        volRow.addView(level);
        col.addView(volRow);
        player.setOnPreparedListener(mp -> {
            ready[0] = true;
            applyVolume.run();
            where.setMax(Math.max(1, mp.getDuration()));
            time.setText("0:00 / " + clock(mp.getDuration()));
            mp.start();
            play.setText("Pause");
            this.main.post(tick[0]);
        });
        player.setOnCompletionListener(mp -> {
            play.setText("Play");
            tick[0].run();
        });
        player.setOnErrorListener((mp, what, extra) -> {
            this.app.setNow("This phone cannot play " + e.name);
            return true;
        });
        AlertDialog d = new AlertDialog.Builder(this.app)
            .setTitle(e.name)
            .setView(col)
            .setPositiveButton("Close", null)
            .create();
        d.setOnDismissListener(x -> {
            ready[0] = false;
            this.main.removeCallbacks(tick[0]);
            try {
                player.release();
            } catch (Exception ignored) {}
            if (lastSound == player) lastSound = null;
        });
        d.show();
        try {
            if (kitWav != null) player.setDataSource(kitWav.getAbsolutePath());
            else player.setDataSource(this.app, this.uri(e));
            player.prepareAsync();
        } catch (Exception ex) {
            this.app.setNow("Could not play " + e.name);
        }
    }

    /** A drum MIDI rendered with Pulsekit's kit to a WAV in the cache; null for a MIDI without drums (or no kit). */
    private File kitWav(MediaDir.Entry e) {
        if (this.app.playback == null) return null;
        try {
            short[] pcm = AudioIo.renderMidiDrums(this.read(e), this.app.playback.mixVoices(), 22050);
            if (pcm == null || pcm.length == 0) return null;
            File wav = new File(this.app.getCacheDir(), "media-midi.wav");
            java.nio.file.Files.write(wav.toPath(), AudioIo.encodeWav(pcm, 22050));
            return wav;
        } catch (Throwable ex) {
            return null;
        }
    }

    static String clock(int ms) {
        int s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60);
    }
}
