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
        return this.tree != null ? this.list(under) != null : new File(under).isDirectory() && under.startsWith(this.base + File.separator);
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

    /**
     * Choose file: the Media browser, with tabs for the Recent MB folder (the folder the Media browser
     * opened last), Download and the favourite folders; a file tapped is copied into PyJav's input
     * folder and handed to `picked`. "Other…" opens the system's picker (`other`).
     */
    static MediaBrowser choose(MainActivity app, RefBrowser.Picked picked, Runnable other) {
        load(app);
        return chooseTab(app, MediaDir.lastRoot.length() > 0 ? "recent" : "download", picked, other);
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
                new String[] {android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME, android.provider.MediaStore.MediaColumns.SIZE},
                null, null, android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (c == null) return all;
            while (c.moveToNext()) {
                MediaDir.Entry e = new MediaDir.Entry();
                e.id = DL + c.getLong(0);
                e.name = c.getString(1);
                e.size = c.isNull(2) ? 0 : c.getLong(2);
                if (e.name != null) all.add(e);
            }
        } catch (Exception ex) {
            return null;
        } finally {
            if (c != null) c.close();
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
        return this.tree == null && !e.id.startsWith(DL);
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
    static String saveInto(MainActivity app, String[] origin, String sub, String name, String mime, byte[] data) throws Exception {
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
                os.write(data);
            } finally {
                os.close();
            }
            return rel + "/" + name;
        }
        if (tree == null || tree.length() == 0) {
            File folder = sub == null ? new File(dir) : new File(dir, sub);
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("could not make " + folder.getPath());
            String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
            String ext = name.substring(stem.length());
            File f = new File(folder, name);
            for (int n = 1; f.exists(); n++) f = new File(folder, stem + " (" + n + ")" + ext);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            try {
                fos.write(data);
            } finally {
                fos.close();
            }
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
                            if (sub.equals(c.getString(1)) && DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2))) found = DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0));
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
            Uri file = DocumentsContract.createDocument(app.getContentResolver(), folder, mime, name);
            if (file == null) throw new java.io.IOException("could not write " + name);
            java.io.OutputStream os = app.getContentResolver().openOutputStream(file);
            try {
                os.write(data);
            } finally {
                os.close();
            }
            return label + "/" + name;
        } catch (SecurityException ex) {
            throw new java.io.IOException("Pulsekit may not write in this folder: pick it again with Params, Browse (it now asks to write there too)");
        }
    }

    /** Choose file: the file tapped, copied into PyJav's input folder off the main thread, then handed on. */
    void chosen(final MediaDir.Entry e) {
        this.chosen(e, this.chooser);
    }

    /** The file copied into PyJav's input folder off the main thread; then the browser closes and `picked` gets it. */
    void chosen(final MediaDir.Entry e, final RefBrowser.Picked picked) {
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
            this.main.post(() -> {
                if (got == null) {
                    this.app.setNow("Could not read " + e.name);
                    return;
                }
                if (this.dialog != null) this.dialog.dismiss();
                this.app.setNow("Chose " + e.name);
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
        for (int i = 0; i < MediaDir.favourites.size(); i++) all.add(new String[] {"fav:" + i, MediaDir.favouriteLabel(MediaDir.favourites.get(i))});
        for (final String[] t : all) {
            TextView pill = this.app.pill(t[1], t[0].equals(this.tab), v -> {
                if (t[0].equals(this.tab)) return;
                if (this.dialog != null) this.dialog.dismiss();
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
        scroll.setPadding(0, 0, 0, this.app.dp(8));
        this.body.addView(scroll);
    }

    /** The folder picked, as Params gives it: what the last folder opened is remembered under. */
    String root;

    private String here() {
        return this.path.get(this.path.size() - 1);
    }

    /** The folder shown, as its default playlist knows it: the path, or the granted folder and the document id. */
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
        lastPlaylist = new AlertDialog.Builder(this.app)
            .setTitle("Playlist \u00b7 " + this.label())
            .setView(scroll)
            .setPositiveButton("Close", null)
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
                all.add(e);
            }
            return MediaDir.shown(all, this.chooser != null);
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(this.tree, at);
        String[] cols = new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE};
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
    Uri uri(MediaDir.Entry e) {
        if (e.id.startsWith(DL)) return android.content.ContentUris.withAppendedId(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, Long.parseLong(e.id.substring(DL.length())));
        return this.tree == null ? Uri.fromFile(new File(e.id)) : DocumentsContract.buildDocumentUriUsingTree(this.tree, e.id);
    }

    private String label() {
        if (DOWNLOADS.equals(here())) return "Download";
        if (this.tree == null) return MediaDir.label(here());
        return MediaDir.label(DocumentsContract.buildDocumentUriUsingTree(this.tree, here()).toString());
    }

    private void show() {
        LinearLayout col = this.app.col();
        int pad = this.app.dp(10);
        col.setPadding(pad, pad, pad, pad);
        this.body = this.app.col();
        ScrollView scroll = new ScrollView(this.app);
        scroll.addView(this.body);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        this.dialog = new AlertDialog.Builder(this.app)
            .setTitle("Media browser")
            .setView(col)
            .setPositiveButton("Close", null)
            .create();
        this.dialog.setOnDismissListener(d -> {
            this.token++;
            if (last == this) last = null;
        });
        this.dialog.show();
        this.load(here());
    }

    /** Opens folder `at` (already on the path) at its first page. */
    private void load(String at) {
        // Remembered: this folder, for the next time the Media browser opens on the same one.
        if (this.root != null) {
            MediaDir.remember(this.root, this.path.subList(1, this.path.size()));
            save(this.app);
        }
        List<MediaDir.Entry> got = this.list(at);
        this.entries = got == null ? new ArrayList<MediaDir.Entry>() : got;
        this.page = 0;
        if (got == null) this.app.setNow("Could not read " + this.label() + ": pick it again with Browse");
        this.paint();
    }

    void openFolder(MediaDir.Entry e) {
        this.path.add(e.id);
        this.load(e.id);
    }

    void up() {
        if (this.path.size() <= 1) return;
        this.path.remove(this.path.size() - 1);
        this.load(here());
    }

    void showPage(int p) {
        int pages = Math.max(1, (this.entries.size() + PAGE - 1) / PAGE);
        this.page = Math.max(0, Math.min(pages - 1, p));
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
        summary.setPadding(0, 0, 0, this.app.dp(6));
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
        LinearLayout tools = this.app.row();
        if (this.path.size() > 1) {
            TextView up = this.app.pill("Up", false, v -> this.up());
            up.setTag("media-up");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = this.app.dp(6);
            tools.addView(up, lp);
        }
        // Add to favourites: this folder, as a tab in Choose file.
        if (this.base != null && !DOWNLOADS.equals(here())) {
            TextView fav = this.app.pill("Add to favourites", false, v -> {
                boolean added = MediaDir.addFavourite(this.base, this.path.subList(1, this.path.size()));
                save(this.app);
                this.app.setNow(added ? this.label() + " is a favourite: a tab in Choose file" : this.label() + " is a favourite already");
                if (added && this.chooser != null) this.paint();
            });
            fav.setTag("media-favourite");
            tools.addView(fav);
        }
        if (tools.getChildCount() > 0) this.body.addView(tools);
        if (this.entries.isEmpty()) {
            this.body.addView(this.app.text("No pictures, videos or sounds here", 14, false));
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
            TextView mark = this.app.text(e.folder ? "FOLDER" : ext(e.name), 13, true);
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
            card.addView(box, new LinearLayout.LayoutParams(-1, Math.max(this.app.dp(60), cell)));
            TextView name = this.app.text(e.name, 11, false);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            name.setPadding(0, this.app.dp(3), 0, 0);
            card.addView(name);
            card.setOnClickListener(v -> {
                if (e.folder) this.openFolder(e);
                else if (this.chooser != null) this.chosen(e);
                else this.openEntry(e);
            });
            // A long press on a file: Add to DB (reference or result file), Add to default playlist.
            if (!e.folder) {
                card.setOnLongClickListener(v -> {
                    this.menu(e);
                    return true;
                });
            }
            row.addView(card, new LinearLayout.LayoutParams(0, -2, 1f));
            if (e.kind == MediaDir.PICTURE || e.kind == MediaDir.VIDEO) {
                Bitmap have = THUMBS.get(key(e));
                if (have != null) image.setImageBitmap(have);
                else {
                    views.add(image);
                    wanted.add(e);
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
            nav.addView(prev);
            nav.addView(at, new LinearLayout.LayoutParams(0, -2, 1f));
            nav.addView(next);
            this.body.addView(nav);
        }
        final int px = Math.max(64, cell);
        for (int k = 0; k < wanted.size(); k++) {
            final MediaDir.Entry e = wanted.get(k);
            final ImageView view = views.get(k);
            READER.execute(() -> {
                if (mine != this.token) return;
                final Bitmap b = this.thumb(e, px);
                if (b == null) return;
                THUMBS.put(key(e), b);
                this.main.post(() -> {
                    if (mine == this.token) view.setImageBitmap(b);
                });
            });
        }
    }

    private String key(MediaDir.Entry e) {
        return (this.tree == null ? "" : this.tree.toString()) + "|" + e.id + "|" + e.size;
    }

    /** The menu shown last, for the tests. */
    static AlertDialog lastMenu;

    /** A file's long-press menu (MediaDir.MENU). */
    void menu(final MediaDir.Entry e) {
        lastMenu = new AlertDialog.Builder(this.app)
            .setTitle(e.name)
            // A file already in the playlist: its third item takes it out.
            .setItems(MediaDir.menu(MediaPlaylist.has(this.app.getFilesDir(), this.folderKey(), e.id), e.kind),
                (d, which) -> this.pick(e, which))
            .setNegativeButton("Cancel", null)
            .show();
    }

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
        this.app.setNow("Adding " + e.name + " to the prompt library\u2026");
        PyJav.KEEPING.incrementAndGet();
        new Thread(() -> {
            String said;
            try {
                said = this.menuPicked(e, which);
            } catch (Throwable ex) {
                said = "Could not add " + e.name + " (" + ex.getClass().getSimpleName() + ")";
            }
            final String shown = said;
            this.main.post(() -> {
                PyJav.KEEPING.decrementAndGet();
                this.app.setNow(shown);
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
        if (MediaDir.tooBig(e.size)) return e.name + " is over the library's 16 MB, so it is not in the DB";
        byte[] bytes;
        try {
            bytes = this.read(e);
        } catch (Exception ex) {
            return "Could not read " + e.name;
        }
        return MediaDir.addToDb(this.app.getFilesDir(), e.name, bytes, which == 1);
    }

    /** The file's bytes (up to the library's 16 MB, plus one to tell). */
    private byte[] read(MediaDir.Entry e) throws Exception {
        InputStream in = this.app.getContentResolver().openInputStream(this.uri(e));
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 16 * 1024 * 1024) break;
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
                    Bitmap frame = media.getFrameAtTime(0);
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
        if (e.kind == MediaDir.PICTURE) this.picture(e, among);
        else if (e.kind == MediaDir.VIDEO) this.video(e);
        else if (e.kind == MediaDir.SOUND) this.sound(e);
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
        title.setBackgroundColor(0x88000000);
        frame.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout bar = this.app.row();
        bar.setBackgroundColor(0x88000000);
        bar.setPadding(this.app.dp(6), this.app.dp(4), this.app.dp(6), this.app.dp(4));
        final int side = Math.max(this.app.getResources().getDisplayMetrics().widthPixels, this.app.getResources().getDisplayMetrics().heightPixels);
        final Runnable show = () -> {
            MediaDir.Entry e = pictures.get(at[0]);
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
        image.setOnTouchListener((v, ev) -> {
            pinch.onTouchEvent(ev);
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
        SeekBar position;
        /** The last frame C saved, for the tests. */
        String captured;

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
            if (position != null && len > 0) position.setProgress((int) Math.min(1000, (long) at * 1000 / len));
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

        /** Times the controls were brought back on top (a long press), for the tests. */
        int raised;

        /**
         * The system's play / pause and position controls: for 3 seconds after a tap, or with `pinned`
         * (a long press) put back on top of the player and kept until the next tap.
         */
        void showControls(MediaController controls, boolean pinned) {
            try {
                if (pinned) {
                    // Taken down and shown again: a controller that ended up behind the player comes back in front.
                    controls.hide();
                    controls.show(0);
                    raised++;
                } else {
                    controls.show(3000);
                }
            } catch (RuntimeException ex) {
                // a controller not tied to its player yet: it shows on the next tap
                if (pinned) raised++;
            }
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
        lastOpened = "video:" + e.name;
        if (this.app.playing) this.app.playback.stop();
        final Dialog d = this.fullScreen();
        final Video p = new Video();
        // As the last video was left: volume, zoom and speed.
        p.loop = MediaDir.loopVideos;
        p.volume = MediaDir.volume;
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
        clock[0] = () -> {
            if (!d.isShowing()) return;
            p.showTime();
            p.keepAwake();
            p.paintTransport();
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
        col.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        // Mute and volume.
        LinearLayout sound = this.app.row();
        sound.setPadding(this.app.dp(8), this.app.dp(4), this.app.dp(8), 0);
        p.mute = this.app.pill("Mute", false, v -> {
            p.muted = !p.muted;
            p.mute.setText(p.muted ? "Unmute" : "Mute");
            this.app.paintChip(p.mute, p.muted);
            p.applyVolume();
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
        // Mute and volume at the top, shown by a tap on the video (and hidden by the next).
        sound.setTag("media-video-sound");
        sound.setVisibility(View.GONE);
        p.sound = sound;
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
        TextView playBtn = this.app.pill("\u25b6 Play", false, v -> {
            p.view.start();
            p.paintTransport();
        });
        playBtn.setTag("media-video-play");
        TextView pauseBtn = this.app.pill("\u275a\u275a Pause", false, v -> {
            p.view.pause();
            p.paintTransport();
        });
        pauseBtn.setTag("media-video-pause");
        p.capture = this.app.pill("C", false, v -> this.capture(e, p));
        p.capture.setTag("media-video-capture");
        p.capture.setContentDescription("Capture this frame into the SC folder");
        p.capture.setVisibility(View.GONE);
        for (TextView t : new TextView[] {stopBtn, playBtn, pauseBtn, p.capture}) {
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
                    int len = p.view.getDuration();
                    if (len > 0) p.view.seekTo((int) ((long) len * value / 1000));
                } catch (RuntimeException ignored) {
                    // not ready
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
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
        // The last video's zoom.
        p.zoomTo(MediaDir.zoom);
        // The play / pause and position controls show over the picture on a tap.
        final MediaController controls = new MediaController(this.app);
        controls.setAnchorView(frame);
        p.view.setMediaController(controls);
        // Pinch to zoom; one finger moves a zoomed video, a tap shows the controls.
        final ScaleGestureDetector pinch = new ScaleGestureDetector(this.app, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector g) {
                p.zoomTo(p.zoom * g.getScaleFactor());
                return true;
            }
        });
        final float[] down = new float[3];
        // Holding a finger on the video brings the play / pause and position controls back on top, until the next tap.
        final int slop = android.view.ViewConfiguration.get(this.app).getScaledTouchSlop();
        final float[] start = new float[2];
        final Runnable hold = () -> {
            down[2] = 1;
            p.showControls(controls, true);
        };
        final View.OnTouchListener touch = (v, ev) -> {
            pinch.onTouchEvent(ev);
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
                // A tap shows Mute and the volume at the top (the next tap hides them); held, the position controls come on top.
                if (act == MotionEvent.ACTION_UP && down[2] == 0) p.sound.setVisibility(p.sound.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
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
            try {
                controls.show(3000);
            } catch (RuntimeException ex) {
                // a controller not tied to its player yet: it shows on the next tap
            }
        });
        p.view.setOnErrorListener((mp, what, extra) -> {
            this.app.setNow("This phone cannot play " + e.name);
            return true;
        });
        p.view.setVideoURI(this.uri(e));
        p.window = d.getWindow();
        p.appWindow = this.app.getWindow();
        d.setOnShowListener(x -> this.main.post(clock[0]));
        d.setOnDismissListener(x -> {
            this.main.removeCallbacks(clock[0]);
            p.setAwake(false);
            // The next video opens as this one was left.
            MediaDir.volume = p.volume;
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

    /**
     * C: the paused video's frame saved as a PNG in the SC folder under the video's folder (made
     * when it is not there): clip-0m12s345.png. Read and written off the main thread.
     */
    void capture(final MediaDir.Entry e, final Video p) {
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
            });
        }, "pulsekit-capture").start();
    }

    /** A sound (a MIDI too, with the phone's own instruments): Play / Pause, Stop and a position bar. */
    void sound(final MediaDir.Entry e) {
        lastOpened = "sound:" + e.name;
        if (this.app.playing) this.app.playback.stop();
        final MediaPlayer player = new MediaPlayer();
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
        col.addView(this.app.text(MediaDir.isMidi(e.name) ? "MIDI, played with the phone's own instruments" : (e.size / 1024) + " KB", 12, false));
        col.addView(where, new LinearLayout.LayoutParams(-1, -2));
        col.addView(time);
        col.addView(buttons);
        player.setOnPreparedListener(mp -> {
            ready[0] = true;
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
            player.setDataSource(this.app, this.uri(e));
            player.prepareAsync();
        } catch (Exception ex) {
            this.app.setNow("Could not play " + e.name);
        }
    }

    static String clock(int ms) {
        int s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60);
    }
}
