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
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        app.startActivityForResult(intent, PICK_DIR);
    }

    /** The folder picked: kept readable after a restart too (a run opens it again). */
    static void keep(MainActivity app, Uri tree) {
        try {
            app.getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // readable for this session still
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
        last = b;
        b.show();
        return b;
    }

    private String here() {
        return this.path.get(this.path.size() - 1);
    }

    /** The folder's entries, shown in order; null when it cannot be read. */
    List<MediaDir.Entry> list(String at) {
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
            return MediaDir.shown(all);
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
        return MediaDir.shown(all);
    }

    /** A file's address for the players and decoders. */
    Uri uri(MediaDir.Entry e) {
        return this.tree == null ? Uri.fromFile(new File(e.id)) : DocumentsContract.buildDocumentUriUsingTree(this.tree, e.id);
    }

    private String label() {
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
        this.body.addView(loop);
        if (this.path.size() > 1) {
            TextView up = this.app.pill("Up", false, v -> this.up());
            up.setTag("media-up");
            this.body.addView(up);
        }
        if (this.entries.isEmpty()) {
            this.body.addView(this.app.text("No pictures, videos or sounds here", 14, false));
            return;
        }
        int from = this.page * PAGE;
        int to = Math.min(this.entries.size(), from + PAGE);
        final int cell = (this.app.getResources().getDisplayMetrics().widthPixels - this.app.dp(96)) / 3;
        LinearLayout row = null;
        final List<ImageView> views = new ArrayList<ImageView>();
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
            card.addView(box, new LinearLayout.LayoutParams(-1, Math.max(this.app.dp(60), cell)));
            TextView name = this.app.text(e.name, 11, false);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            name.setPadding(0, this.app.dp(3), 0, 0);
            card.addView(name);
            card.setOnClickListener(v -> {
                if (e.folder) this.openFolder(e);
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
            .setItems(MediaDir.MENU, (d, which) -> this.app.setNow(this.menuPicked(e, which)))
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** What a menu item does: 0 and 1 add the file to the prompt library, 2 to the default playlist. Returns the status line. */
    String menuPicked(MediaDir.Entry e, int which) {
        if (which == 2) return MediaPlaylist.add(this.app.getFilesDir(), MediaPlaylist.DEFAULT, this.uri(e).toString().startsWith("file:") ? e.id : this.uri(e).toString(), e.name);
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
                    if (this.tree == null) media.setDataSource(e.id);
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
        if (e.kind == MediaDir.PICTURE) this.picture(e);
        else if (e.kind == MediaDir.VIDEO) this.video(e);
        else if (e.kind == MediaDir.SOUND) this.sound(e);
    }

    private Dialog fullScreen() {
        Dialog d = new Dialog(this.app, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        return d;
    }

    /** A picture full size: pinch to zoom, drag to look around; Previous / Next go through the folder's pictures. */
    void picture(MediaDir.Entry first) {
        lastOpened = "picture:" + first.name;
        final List<MediaDir.Entry> pictures = new ArrayList<MediaDir.Entry>();
        for (MediaDir.Entry x : this.entries) if (x.kind == MediaDir.PICTURE) pictures.add(x);
        final int[] at = new int[] {Math.max(0, pictures.indexOf(first))};
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
        col.addView(sound);
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
        final View.OnTouchListener touch = (v, ev) -> {
            pinch.onTouchEvent(ev);
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                down[0] = ev.getRawX() - p.view.getTranslationX();
                down[1] = ev.getRawY() - p.view.getTranslationY();
                down[2] = 0;
            } else if (act == MotionEvent.ACTION_MOVE && ev.getPointerCount() == 1 && !pinch.isInProgress() && p.zoom > 1) {
                p.view.setTranslationX(ev.getRawX() - down[0]);
                p.view.setTranslationY(ev.getRawY() - down[1]);
                down[2] = 1;
            } else if (act == MotionEvent.ACTION_POINTER_DOWN) {
                down[2] = 1;
            } else if (act == MotionEvent.ACTION_UP && down[2] == 0) {
                if (controls.isShowing()) controls.hide();
                else controls.show(3000);
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
            controls.show(3000);
        });
        p.view.setOnErrorListener((mp, what, extra) -> {
            this.app.setNow("This phone cannot play " + e.name);
            return true;
        });
        p.view.setVideoURI(this.uri(e));
        d.setOnDismissListener(x -> {
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
