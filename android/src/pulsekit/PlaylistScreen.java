package pulsekit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * File > Playlist viewer/editor (and PlayList.java's run, a .pls run from PyJav, Import .pls): a
 * playlist's files in two tabs. Editor: each file with its thumbnail and what it is; ▲ ▼ move it,
 * ✕ takes it out, a long press (or ⋮) has Start viewer; Add from DB and Add files add more. Viewer:
 * Repeat, Auto mode, Autoplay video, how long a picture shows, and Start viewer. The viewer shows
 * one file full size: ◀ ▶ the one before and after, a long press plays or pauses a video (a
 * picture: holds the Auto mode there), Play / Pause / Stop, Mute, volume, speed and zoom (pinch
 * too); the choices are kept (Playlist.Settings). Every change to the list is saved as its .pls.
 */
final class PlaylistScreen {
    static final int PICK_FILES = 51;
    static final int PICK_PLS = 52;
    static final int PICK_MERGE = 53;
    static final int SAVE_PLS = 54;

    private PlaylistScreen() {}

    /** The window, the viewer, the item menu and the last list asked (for the tests). */
    static AlertDialog last;
    static Dialog lastViewer;
    static AlertDialog lastAsk;
    /** The list the window shows, and its tab (0 Editor, 1 Viewer). */
    static Playlist shown;
    static int tab;
    /** The viewer's item and what it shows ("picture", "video", "sound", "missing", "message"), for the tests. */
    static int viewerAt = -1;
    static String viewerNote = "";

    /** The window on the playlist kept by that name (the one open last when null). */
    static void show(final MainActivity app, String name) {
        File dir = app.getFilesDir();
        Playlist.Settings s = Playlist.settings(dir);
        String n = name == null || name.trim().length() == 0 ? s.last : Playlist.clean(name);
        switchTo(dir, n);
        if (last != null && last.isShowing()) last.dismiss();
        final LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        last = new AlertDialog.Builder(app).setTitle("Playlist · " + shown.name).setView(scroll).setPositiveButton("Close", null).create();
        shownCol = col;
        fill(app, col);
        last.show();
    }

    /** The window's list, filled again after each change. */
    private static LinearLayout shownCol;

    /** The window shown again (after a change). */
    static void again(MainActivity app) {
        if (last == null || !last.isShowing() || shownCol == null) {
            show(app, shown == null ? null : shown.name);
            return;
        }
        fill(app, shownCol);
    }

    private static void switchTo(File dir, String name) {
        shown = Playlist.load(dir, name);
        Playlist.Settings s = Playlist.settings(dir);
        s.last = shown.name;
        Playlist.saveSettings(dir, s);
    }

    static void save(MainActivity app, Playlist p) {
        try {
            Playlist.save(app.getFilesDir(), p);
        } catch (Exception ex) {
            app.setNow("Could not save " + p.name + ": " + ex.getMessage());
        }
    }

    private static void fill(final MainActivity app, final LinearLayout col) {
        col.removeAllViews();
        final File dir = app.getFilesDir();
        final Playlist p = shown;
        if (last != null) last.setTitle("Playlist · " + p.name);
        // The playlist picked (a tap lists them), New, Rename, Delete, Import .pls, Merge .pls, Save, Save as.
        HorizontalScrollView topScroll = new HorizontalScrollView(app);
        LinearLayout top = app.row();
        TextView pick = app.pill(p.name + " ▾", true, v -> {
            final List<String> names = Playlist.names(dir);
            lastAsk = new AlertDialog.Builder(app).setTitle("Playlist").setItems(names.toArray(new String[0]), (d, w) -> {
                switchTo(dir, names.get(w));
                again(app);
            }).setNegativeButton("Cancel", null).show();
        });
        pick.setTag("playlist-pick");
        top.addView(pick);
        TextView make = app.pill("New…", false, v -> named(app, "New playlist", "", typed -> {
            switchTo(dir, typed);
            save(app, shown);
            again(app);
        }));
        make.setTag("playlist-new");
        top.addView(make);
        TextView rename = app.pill("Rename…", false, v -> named(app, "Rename " + p.name, p.name, typed -> {
            if (typed.equals(p.name)) return;
            if (Playlist.file(dir, typed).isFile()) {
                app.setNow("There is a playlist " + typed + " already");
                return;
            }
            File old = Playlist.file(dir, p.name);
            p.name = typed;
            save(app, p);
            old.delete();
            switchTo(dir, typed);
            again(app);
        }));
        rename.setTag("playlist-rename");
        top.addView(rename);
        TextView delete = app.pill("Delete…", false, v -> {
            lastAsk = new AlertDialog.Builder(app).setTitle("Delete " + p.name + "?").setMessage("The playlist goes; its files stay where they are.")
                .setPositiveButton("Delete", (d, w) -> {
                    Playlist.file(dir, p.name).delete();
                    app.setNow("Playlist " + p.name + " deleted");
                    switchTo(dir, Playlist.DEFAULT);
                    again(app);
                }).setNegativeButton("Cancel", null).show();
        });
        delete.setTag("playlist-delete");
        top.addView(delete);
        TextView imp = app.pill("Import .pls…", false, v -> pick(app, PICK_PLS, false));
        imp.setTag("playlist-import");
        top.addView(imp);
        TextView merge = app.pill("Merge .pls…", false, v -> pick(app, PICK_MERGE, true));
        merge.setTag("playlist-merge");
        top.addView(merge);
        // Save: the .pls where it was saved last (Save as… when it has not been); Save as…: a place picked.
        TextView saveIt = app.pill(Playlist.SAVE, false, v -> savePls(app, p));
        saveIt.setTag("playlist-save");
        top.addView(saveIt);
        TextView export = app.pill(Playlist.SAVE_AS, false, v -> saveAs(app, p));
        export.setTag("playlist-export");
        top.addView(export);
        topScroll.addView(top);
        col.addView(topScroll);
        // Editor | Viewer.
        LinearLayout tabs = app.row();
        String[] names = {"Editor", "Viewer"};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            TextView b = app.pill(names[i], tab == i, v -> {
                tab = t;
                again(app);
            });
            b.setTag("playlist-tab:" + names[i]);
            tabs.addView(b);
        }
        col.addView(tabs);
        if (tab == 0) editor(app, col, p);
        else viewerTab(app, col, p);
    }

    /** A name asked for (`now` filled in); `done` gets it cleaned (nothing when empty). */
    private static void named(final MainActivity app, String title, String now, final java.util.function.Consumer<String> done) {
        final EditText field = new EditText(app);
        field.setSingleLine(true);
        field.setText(now);
        field.setHint("Playlist name");
        field.setTag("playlist-name-field");
        lastAsk = new AlertDialog.Builder(app).setTitle(title).setView(field)
            .setPositiveButton("OK", (d, w) -> {
                String n = Playlist.clean(field.getText().toString());
                if (n.length() > 0) done.accept(n);
            })
            .setNegativeButton("Cancel", null).show();
    }

    private static void pick(MainActivity app, int code, boolean several) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        if (code == PICK_FILES) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*", "audio/*"});
        if (several) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            app.startActivityForResult(intent, code);
        } catch (Exception ex) {
            app.setNow("No file picker here: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ Editor

    private static void editor(final MainActivity app, LinearLayout col, final Playlist p) {
        if (p.items.isEmpty()) {
            TextView none = app.text("No files yet: Add from DB or Add files below, or Add to playlist on a thumbnail.", 13, false);
            none.setPadding(0, app.dp(10), 0, app.dp(10));
            col.addView(none);
        }
        final List<ImageView> thumbs = new ArrayList<ImageView>();
        final List<TextView> infos = new ArrayList<TextView>();
        for (int i = 0; i < p.items.size(); i++) {
            final int at = i;
            final Playlist.Item it = p.items.get(i);
            LinearLayout row = app.row();
            row.setTag("playlist-item:" + i);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, app.dp(4), 0, app.dp(4));
            ImageView thumb = new ImageView(app);
            thumb.setTag("playlist-thumb:" + i);
            thumb.setBackgroundColor(Color.parseColor("#1B1D1F"));
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(thumb, new LinearLayout.LayoutParams(app.dp(64), app.dp(48)));
            thumbs.add(thumb);
            TextView text = app.text((i + 1) + ". " + it.title + "\n" + Playlist.info(it, -1), 13, false);
            text.setTag("playlist-info:" + i);
            text.setPadding(app.dp(8), 0, app.dp(4), 0);
            infos.add(text);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView up = app.pill("▲", false, v -> {
                if (p.move(at, at - 1)) save(app, p);
                again(app);
            });
            up.setTag("playlist-up:" + i);
            up.setEnabled(i > 0);
            TextView down = app.pill("▼", false, v -> {
                if (p.move(at, at + 1)) save(app, p);
                again(app);
            });
            down.setTag("playlist-down:" + i);
            down.setEnabled(i < p.items.size() - 1);
            TextView remove = app.pill("✕", false, v -> {
                if (p.remove(at)) save(app, p);
                app.setNow(it.title + " taken out of " + p.name);
                again(app);
            });
            remove.setTag("playlist-remove:" + i);
            TextView more = app.pill("⋮", false, v -> menu(app, p, at));
            more.setTag("playlist-menu:" + i);
            row.addView(up);
            row.addView(down);
            row.addView(remove);
            row.addView(more);
            row.setOnLongClickListener(v -> {
                menu(app, p, at);
                return true;
            });
            row.setOnClickListener(v -> viewer(app, p, at));
            col.addView(row);
        }
        // The thumbnails and sizes, read off the screen's thread.
        final List<Playlist.Item> items = new ArrayList<Playlist.Item>(p.items);
        final Handler main = new Handler(Looper.getMainLooper());
        final int px = app.dp(64);
        new Thread(() -> {
            PromptVault vault = vault(app);
            for (int i = 0; i < items.size(); i++) {
                final Playlist.Item it = items.get(i);
                final long size = size(app, it, vault);
                final Bitmap bmp = thumb(app, it, vault, px);
                final int n = i;
                main.post(() -> {
                    if (bmp != null) thumbs.get(n).setImageBitmap(bmp);
                    infos.get(n).setText((n + 1) + ". " + it.title + "\n" + Playlist.info(it, size));
                });
            }
        }, "pulsekit-playlist-thumbs").start();
        LinearLayout adds = app.row();
        TextView fromDb = app.pill("Add from DB…", false, v -> RefBrowser.browse(app, null, (picked, copy) -> {
            PromptVault.StoredFile f = Playlist.find(vault(app), picked, copy.length());
            if (f == null) {
                app.setNow("Could not find " + picked + " in the prompt library");
                return;
            }
            final String file = Playlist.dbAddress(f);
            final String title = f.name;
            // In the list already: asked before it goes in a second time.
            Runnable put = () -> {
                if (p.has(file)) p.addAgain(file, title);
                else p.add(file, title);
                save(app, p);
                app.setNow(title + " added to " + p.name);
                show(app, p.name);
            };
            if (p.has(file)) askAgain(app, Playlist.againQuestion(title, p.name), put);
            else put.run();
        }));
        fromDb.setTag("playlist-add-db");
        TextView files = app.pill("Add files…", false, v -> pick(app, PICK_FILES, true));
        files.setTag("playlist-add-files");
        adds.addView(fromDb);
        adds.addView(files);
        col.addView(adds);
    }

    /** An item's menu: Start viewer (from it), Move up / down, to the top / bottom, Remove. */
    private static void menu(final MainActivity app, final Playlist p, final int at) {
        final String[] rows = {"Start viewer", "Move up", "Move down", "Move to the top", "Move to the bottom", "Remove from the list"};
        lastAsk = new AlertDialog.Builder(app).setTitle(p.items.get(at).title).setItems(rows, (d, w) -> {
            if (w == 0) {
                viewer(app, p, at);
                return;
            }
            boolean changed;
            if (w == 5) {
                String title = p.items.get(at).title;
                changed = p.remove(at);
                app.setNow(title + " taken out of " + p.name);
            } else {
                int dest = w == 1 ? at - 1 : w == 2 ? at + 1 : w == 3 ? 0 : p.items.size() - 1;
                changed = p.move(at, dest);
            }
            if (changed) save(app, p);
            again(app);
        }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------ Viewer tab

    private static void viewerTab(final MainActivity app, LinearLayout col, final Playlist p) {
        final File dir = app.getFilesDir();
        final Playlist.Settings s = Playlist.settings(dir);
        final CheckBox repeat = box(app, "Repeat: from the end back to the beginning", s.repeat, "playlist-repeat");
        final CheckBox auto = box(app, "Auto mode: the next file shows by itself (else tap \u25b6)", s.auto, "playlist-auto");
        final CheckBox loop = box(app, Playlist.LOOP_CAPTION.replace("pressed", "tapped"), s.loop, "playlist-loop");
        final CheckBox autoplay = box(app, Playlist.AUTOPLAY_CAPTION, s.autoplay, "playlist-autoplay");
        final CheckBox mute = box(app, "Mute", s.mute, "playlist-mute");
        final int[] seconds = {s.seconds};
        final float[] speed = {s.speed};
        final float[] zoom = {s.zoom};
        final float[] zoomVideo = {s.zoomVideo};
        final TextView secs = app.text("", 14, false);
        secs.setTag("playlist-seconds");
        final TextView level = app.text("", 14, false);
        level.setTag("playlist-volume-level");
        final SeekBar volume = new SeekBar(app);
        volume.setMax(100);
        volume.setProgress(s.volume);
        volume.setTag("playlist-volume");
        final TextView speedPill = app.pill("", false, null);
        speedPill.setTag("playlist-speed");
        final TextView zoomLevel = app.text("", 14, false);
        zoomLevel.setTag("playlist-zoom");
        final TextView videoZoomLevel = app.text("", 14, false);
        videoZoomLevel.setTag("playlist-video-zoom");
        final boolean[] filling = {true};
        final Runnable keep = () -> {
            if (filling[0]) return;
            Playlist.Settings now = Playlist.settings(dir);
            now.repeat = repeat.isChecked();
            now.auto = auto.isChecked();
            now.loop = loop.isChecked();
            now.autoplay = autoplay.isChecked();
            now.seconds = seconds[0];
            now.volume = volume.getProgress();
            now.mute = mute.isChecked();
            now.speed = speed[0];
            now.zoom = zoom[0];
            now.zoomVideo = zoomVideo[0];
            Playlist.saveSettings(dir, now);
        };
        final Runnable paint = () -> {
            secs.setText("In Auto mode a picture shows for " + seconds[0] + " s");
            level.setText("Volume " + (mute.isChecked() ? "(muted)" : volume.getProgress() + "%"));
            speedPill.setText("Speed " + Playlist.speedText(speed[0]));
            zoomLevel.setText("Picture zoom " + MediaDir.zoomLabel(zoom[0]));
            videoZoomLevel.setText("Video zoom " + MediaDir.zoomLabel(zoomVideo[0]));
        };
        paint.run();
        // Loop video and Auto mode go one at a time.
        loop.setOnCheckedChangeListener((b, on) -> {
            if (on && auto.isChecked()) auto.setChecked(false);
            keep.run();
        });
        auto.setOnCheckedChangeListener((b, on) -> {
            if (on && loop.isChecked()) loop.setChecked(false);
            keep.run();
        });
        for (CheckBox c : new CheckBox[] {repeat, autoplay, mute}) c.setOnCheckedChangeListener((b, on) -> {
            keep.run();
            paint.run();
        });
        for (CheckBox c : new CheckBox[] {repeat, auto, loop, autoplay}) col.addView(c);
        LinearLayout row = app.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(secs);
        TextView less = app.pill("\u2212", false, v -> {
            seconds[0] = Math.max(1, seconds[0] - 1);
            keep.run();
            paint.run();
        });
        less.setTag("playlist-seconds-less");
        TextView more = app.pill("+", false, v -> {
            seconds[0] = Math.min(600, seconds[0] + 1);
            keep.run();
            paint.run();
        });
        more.setTag("playlist-seconds-more");
        row.addView(less);
        row.addView(more);
        col.addView(row);
        // The player's own, as the viewer starts with them (and keeps what is changed there).
        col.addView(level);
        LinearLayout sound = app.row();
        sound.setGravity(Gravity.CENTER_VERTICAL);
        sound.addView(volume, new LinearLayout.LayoutParams(0, -2, 1f));
        sound.addView(mute);
        col.addView(sound);
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int value, boolean fromUser) {
                paint.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                keep.run();
            }
        });
        LinearLayout look = app.row();
        look.setGravity(Gravity.CENTER_VERTICAL);
        speedPill.setOnClickListener(v -> {
            speed[0] = nextSpeed(speed[0]);
            keep.run();
            paint.run();
        });
        look.addView(speedPill);
        col.addView(look);
        look = app.row();
        look.setGravity(Gravity.CENTER_VERTICAL);
        look.addView(zoomLevel);
        TextView zoomOut = app.pill("\u2212", false, v -> {
            zoom[0] = (float) MediaDir.zoom(zoom[0], false);
            keep.run();
            paint.run();
        });
        zoomOut.setTag("playlist-zoom-out");
        TextView zoomIn = app.pill("+", false, v -> {
            zoom[0] = (float) MediaDir.zoom(zoom[0], true);
            keep.run();
            paint.run();
        });
        zoomIn.setTag("playlist-zoom-in");
        TextView fit = app.pill("Fit", false, v -> {
            zoom[0] = 1f;
            keep.run();
            paint.run();
        });
        fit.setTag("playlist-fit");
        look.addView(zoomOut);
        look.addView(zoomIn);
        look.addView(fit);
        col.addView(look);
        // A video's zoom, kept apart from a picture's.
        LinearLayout lookVideo = app.row();
        lookVideo.setGravity(Gravity.CENTER_VERTICAL);
        lookVideo.addView(videoZoomLevel);
        String[][] vz = {{"\u2212", "playlist-video-zoom-out"}, {"+", "playlist-video-zoom-in"}, {"Fit", "playlist-video-fit"}};
        for (int i = 0; i < vz.length; i++) {
            final int which = i;
            TextView b = app.pill(vz[i][0], false, v -> {
                zoomVideo[0] = which == 2 ? 1f : (float) MediaDir.zoom(zoomVideo[0], which == 1);
                keep.run();
                paint.run();
            });
            b.setTag(vz[i][1]);
            lookVideo.addView(b);
        }
        col.addView(lookVideo);
        filling[0] = false;
        TextView start = app.pill("Start viewer", true, v -> viewer(app, p, 0));
        start.setTag("playlist-start");
        start.setEnabled(!p.items.isEmpty());
        col.addView(start);
    }

    /** The speed after `now` (½× … 2×, then ½× again). */
    static float nextSpeed(float now) {
        int i = 0;
        for (int k = 0; k < Playlist.SPEEDS.length; k++) if (Math.abs(Playlist.SPEEDS[k] - now) < 0.01f) i = k;
        return Playlist.SPEEDS[(i + 1) % Playlist.SPEEDS.length];
    }

    private static CheckBox box(MainActivity app, String text, boolean on, String tag) {
        CheckBox c = new CheckBox(app);
        c.setText(text);
        c.setChecked(on);
        c.setTag(tag);
        c.setTextColor(Color.parseColor("#ECEBE6"));
        return c;
    }

    // ------------------------------------------------------------ Files

    /** Save: the .pls where it was saved last; Save as… when it has not been (or that place is gone). */
    static void savePls(MainActivity app, Playlist p) {
        String at = Playlist.savedAt(app.getFilesDir(), p.name);
        if (at == null || !write(app, p, uri(at))) saveAs(app, p);
    }

    /** Save as…: the system's picker makes the .pls; Save writes there from then on. */
    static void saveAs(MainActivity app, Playlist p) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/x-scpls");
        intent.putExtra(Intent.EXTRA_TITLE, p.name + Playlist.EXT);
        try {
            app.startActivityForResult(intent, SAVE_PLS);
        } catch (Exception ex) {
            app.setNow("No app here saves files: " + ex.getMessage());
        }
    }

    /** The playlist written into `to` (replacing what was there); false when it cannot be. */
    static boolean write(MainActivity app, Playlist p, Uri to) {
        try {
            java.io.OutputStream out = app.getContentResolver().openOutputStream(to, "wt");
            if (out == null) return false;
            try {
                out.write(p.toPls().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            app.setNow("Saved " + p.name + Playlist.EXT);
            return true;
        } catch (Exception ex) {
            app.setNow("Could not save " + p.name + Playlist.EXT + ": " + ex.getMessage());
            return false;
        }
    }

    static PromptVault vault(MainActivity app) {
        try {
            return PromptVault.open(app.getFilesDir());
        } catch (Exception ex) {
            return null;
        }
    }

    /** A content:// or file:// address, or a path, as a Uri. */
    static Uri uri(String file) {
        return file.startsWith("content://") || file.startsWith("file://") ? Uri.parse(file) : Uri.fromFile(new File(file));
    }

    /** The file's bytes (a library file's, or read through the address); null when it cannot be read. */
    static byte[] bytes(MainActivity app, Playlist.Item it, PromptVault vault) {
        if (Playlist.isDb(it.file)) return Playlist.dbBytes(vault, it.file);
        try {
            InputStream in = app.getContentResolver().openInputStream(uri(it.file));
            if (in == null) return null;
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toByteArray();
            } finally {
                in.close();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    static long size(MainActivity app, Playlist.Item it, PromptVault vault) {
        if (Playlist.isDb(it.file)) {
            byte[] b = Playlist.dbBytes(vault, it.file);
            return b == null ? -1 : b.length;
        }
        if (!it.file.startsWith("content://")) {
            File f = new File(Playlist.fromUrl(it.file));
            return f.isFile() ? f.length() : -1;
        }
        Cursor c = null;
        try {
            c = app.getContentResolver().query(Uri.parse(it.file), new String[] {"_size"}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {
            // not known
        } finally {
            if (c != null) c.close();
        }
        return -1;
    }

    /** The item as a file the players can open: its path, or a library file written out (kept for the session). */
    static File local(MainActivity app, Playlist.Item it, PromptVault vault) {
        if (!Playlist.isDb(it.file)) return it.file.startsWith("content://") ? null : new File(Playlist.fromUrl(it.file));
        byte[] b = Playlist.dbBytes(vault, it.file);
        if (b == null) return null;
        try {
            File dir = new File(app.getCacheDir(), "playlist-cache");
            dir.mkdirs();
            File f = new File(dir, Playlist.dbPlace(it.file)[1] + "-" + Playlist.shortName(it.file));
            if (!(f.isFile() && f.length() == b.length)) java.nio.file.Files.write(f.toPath(), b);
            return f;
        } catch (Exception ex) {
            return null;
        }
    }

    /** A thumbnail: a picture scaled down, a video's first frame; null otherwise. */
    static Bitmap thumb(MainActivity app, Playlist.Item it, PromptVault vault, int px) {
        try {
            int k = it.kind();
            if (k == MediaDir.PICTURE) {
                byte[] b = bytes(app, it, vault);
                return b == null ? null : PictureOffer.decode(b, px * 2);
            }
            if (k != MediaDir.VIDEO) return null;
            MediaMetadataRetriever media = new MediaMetadataRetriever();
            try {
                File f = local(app, it, vault);
                if (f != null) media.setDataSource(f.getAbsolutePath());
                else media.setDataSource(app, Uri.parse(it.file));
                Bitmap frame = media.getFrameAtTime(0);
                if (frame == null) return null;
                float s = Math.min(1f, px * 2f / Math.max(frame.getWidth(), frame.getHeight()));
                return s >= 1f ? frame : Bitmap.createScaledBitmap(frame, Math.max(1, Math.round(frame.getWidth() * s)), Math.max(1, Math.round(frame.getHeight() * s)), true);
            } finally {
                try {
                    media.release();
                } catch (Exception ignored) {
                    // released
                }
            }
        } catch (Throwable ex) {
            return null;
        }
    }

    // ------------------------------------------------------------ Picked files (MainActivity's results)

    static void picked(final MainActivity app, int code, Intent intent) {
        List<Uri> uris = new ArrayList<Uri>();
        if (intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) uris.add(intent.getClipData().getItemAt(i).getUri());
        } else if (intent.getData() != null) {
            uris.add(intent.getData());
        }
        if (uris.isEmpty()) return;
        File dir = app.getFilesDir();
        if (code == SAVE_PLS) {
            try {
                app.getContentResolver().takePersistableUriPermission(uris.get(0), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {
                // writable for now
            }
            // Save writes here from then on.
            if (write(app, shown, uris.get(0))) Playlist.setSavedAt(dir, shown.name, uris.get(0).toString());
            return;
        }
        if (code == PICK_FILES) {
            int added = 0;
            final List<String[]> again = new ArrayList<String[]>();
            for (Uri u : uris) {
                try {
                    app.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // readable for now
                }
                if (shown.add(u.toString(), name(app, u))) added++;
                else again.add(new String[] {u.toString(), name(app, u)});
            }
            save(app, shown);
            app.setNow(added + (added == 1 ? " file" : " files") + " added to " + shown.name);
            show(app, shown.name);
            // Those in the list already: asked before they go in a second time.
            if (!again.isEmpty()) {
                String q = again.size() == 1 ? Playlist.againQuestion(again.get(0)[1], shown.name)
                    : again.size() + " of the files are in " + shown.name + " already. Add them a second time?";
                askAgain(app, q, () -> {
                    for (String[] f : again) shown.addAgain(f[0], f[1]);
                    save(app, shown);
                    app.setNow(again.size() + (again.size() == 1 ? " file" : " files") + " added again to " + shown.name);
                    show(app, shown.name);
                });
            }
            return;
        }
        if (code == PICK_PLS) {
            String name = importPls(app, uris.get(0));
            if (name != null) show(app, name);
            return;
        }
        if (code == PICK_MERGE) merge(app, uris);
    }

    static String name(MainActivity app, Uri u) {
        Cursor c = null;
        try {
            c = app.getContentResolver().query(u, new String[] {"_display_name"}, null, null, null);
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (Exception ignored) {
            // the address' end
        } finally {
            if (c != null) c.close();
        }
        return Playlist.shortName(u.toString());
    }

    /** A .pls copied in with the playlists (by its name; " (2)" when that is taken); its name, or null. */
    static String importPls(MainActivity app, Uri u) {
        String file = name(app, u);
        try {
            InputStream in = app.getContentResolver().openInputStream(u);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            in.close();
            Playlist p = Playlist.parse(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8), file);
            String base = p.name;
            for (int n = 2; Playlist.file(app.getFilesDir(), p.name).isFile(); n++) p.name = base + " (" + n + ")";
            Playlist.save(app.getFilesDir(), p);
            app.setNow("Imported " + file + " as " + p.name + " (" + p.items.size() + (p.items.size() == 1 ? " file)" : " files)"));
            return p.name;
        } catch (Exception ex) {
            app.setNow("Could not import " + file + ": " + ex.getMessage());
            return null;
        }
    }

    /** Merge .pls: the picked lists copied to the cache and handed to PlayList.java; the merged list opened. */
    static void merge(final MainActivity app, final List<Uri> uris) {
        app.setNow("PlayList.java: merging " + uris.size() + " playlists…");
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String said;
            String name = null;
            try {
                File work = new File(app.getCacheDir(), "playlist-merge");
                work.mkdirs();
                List<String> argv = new ArrayList<String>();
                for (Uri u : uris) {
                    String n = name(app, u);
                    if (!n.toLowerCase().endsWith(Playlist.EXT)) n = n + Playlist.EXT;
                    File f = new File(work, n);
                    InputStream in = app.getContentResolver().openInputStream(u);
                    java.nio.file.Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    in.close();
                    argv.add(f.getAbsolutePath());
                }
                name = run(app, argv);
                said = name == null ? "PlayList.java could not merge them" : "Merged into " + name;
            } catch (Exception ex) {
                said = "Could not merge: " + ex.getMessage();
            }
            final String to = name;
            final String line = said;
            main.post(() -> {
                app.setNow(line);
                if (to != null) show(app, to);
            });
        }, "pulsekit-playlist-merge").start();
    }

    /** PlayList.java run on the files; what it wrote kept with the playlists; its name, or null. */
    static String run(MainActivity app, List<String> argv) throws Exception {
        String src = app.programMenus.bundledSource("PlayList.java");
        if (src == null) throw new IllegalStateException("PlayList.java is not among the programs");
        return Playlist.keep(app.getFilesDir(), JavaRun.run("PlayList.java", src, null, argv));
    }

    /** PyJav's PlayList.java run: the playlist kept and opened; false when it made none. */
    static boolean afterRun(MainActivity app, JavaRun.Result r) {
        try {
            String name = Playlist.keep(app.getFilesDir(), r);
            if (name == null) return false;
            show(app, name);
            return true;
        } catch (Exception ex) {
            app.setNow("Could not keep the playlist: " + ex.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ Add to playlist (the thumbnails' menus)

    /** The file into the named list; when it is there already, asked first ("Add it a second time?"). */
    static void confirmAdd(final MainActivity app, final String list, final String file, final String title) {
        final File dir = app.getFilesDir();
        if (!Playlist.inList(dir, list, file)) {
            app.setNow(Playlist.addTo(dir, list, file, title));
            return;
        }
        askAgain(app, Playlist.againQuestion(title, Playlist.clean(list).length() == 0 ? Playlist.DEFAULT : Playlist.clean(list)),
            () -> app.setNow(Playlist.addTo(dir, list, file, title, true)));
    }

    /** "… is in … already. Add it a second time?": Add again, or Cancel (nothing added). */
    static void askAgain(MainActivity app, String question, final Runnable again) {
        lastAgain = new AlertDialog.Builder(app).setTitle("In the playlist already").setMessage(question)
            .setPositiveButton("Add again", (d, w) -> again.run())
            .setNegativeButton("Cancel", null).show();
    }

    /** The "Add it a second time?" question shown last (for the tests). */
    static AlertDialog lastAgain;

    /** "Add to playlist…": the playlists (Default first), then New playlist…; the file goes at the end of the one picked. */
    static void addTo(final MainActivity app, final String file, final String title) {
        final File dir = app.getFilesDir();
        final String[] rows = Playlist.menu(dir);
        lastAsk = new AlertDialog.Builder(app).setTitle(Playlist.ADD.replace("…", "") + " · " + title).setItems(rows, (d, w) -> {
            if (w < rows.length - 1) confirmAdd(app, rows[w], file, title);
            else named(app, Playlist.NEW, "", typed -> confirmAdd(app, typed, file, title));
        }).setNegativeButton("Cancel", null).show();
    }

    /**
     * A run's file (Picture ready, Video ready: it may be a temporary copy) kept beside the playlists
     * (playlists/files), so a playlist finds it later; its path, or null when it cannot be kept. The
     * same file (name and size) kept before is used again.
     */
    static String keep(MainActivity app, String name, byte[] bytes, File from) {
        try {
            File dir = new File(Playlist.folder(app.getFilesDir()), "files");
            dir.mkdirs();
            long size = bytes != null ? bytes.length : from == null ? -1 : from.length();
            String base = Playlist.shortName(name);
            File to = new File(dir, base);
            int dot = base.lastIndexOf('.');
            for (int n = 2; to.exists() && to.length() != size; n++) to = new File(dir, (dot > 0 ? base.substring(0, dot) : base) + " (" + n + ")" + (dot > 0 ? base.substring(dot) : ""));
            if (!to.exists()) {
                if (bytes != null) java.nio.file.Files.write(to.toPath(), bytes);
                else java.nio.file.Files.copy(from.toPath(), to.toPath());
            }
            return to.getAbsolutePath();
        } catch (Exception ex) {
            app.setNow("Could not keep " + name + " for a playlist: " + ex.getMessage());
            return null;
        }
    }

    /** Add to playlist for a run's file (Picture ready, Video ready): kept beside the playlists, then the list of playlists. */
    static void addReady(MainActivity app, String name, byte[] bytes, File from) {
        String path = keep(app, name, bytes, from);
        if (path != null) addTo(app, path, name);
    }

    static void addToDefault(MainActivity app, String file, String title) {
        confirmAdd(app, Playlist.DEFAULT, file, title);
    }

    // ------------------------------------------------------------ The viewer

    /** The viewer on the list from item `start`. */
    static void viewer(final MainActivity app, final Playlist p, int start) {
        if (p.items.isEmpty()) {
            app.setNow(p.name + " has no files yet");
            return;
        }
        final File dir = app.getFilesDir();
        final Playlist.Settings s = Playlist.settings(dir);
        final Dialog v = new Dialog(app, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final Handler main = new Handler(Looper.getMainLooper());
        LinearLayout root = new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        // The top: which file, and Close.
        LinearLayout head = app.row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(app.dp(8), app.dp(4), app.dp(8), app.dp(4));
        final TextView where = app.text("", 14, true);
        where.setTag("viewer-where");
        where.setTextColor(Color.parseColor("#ECEBE6"));
        head.addView(where, new LinearLayout.LayoutParams(0, -2, 1f));
        // A video's place: "00:12 / 01:30".
        final TextView timeText = app.text("", 14, true);
        timeText.setTag("viewer-time");
        timeText.setTextColor(Color.parseColor("#ECEBE6"));
        timeText.setPadding(app.dp(8), 0, app.dp(8), 0);
        timeText.setVisibility(View.GONE);
        head.addView(timeText);
        TextView close = app.pill("Close", true, x -> v.dismiss());
        close.setTag("viewer-close");
        head.addView(close);
        root.addView(head);
        // The stage: the picture, the video, or a line; ◀ ▶ over its edges.
        final FrameLayout stage = new FrameLayout(app);
        stage.setTag("viewer-stage");
        final ImageView picture = new ImageView(app);
        picture.setTag("viewer-picture");
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        final VideoView video = new VideoView(app);
        video.setTag("viewer-video");
        final TextView message = app.text("", 15, false);
        message.setTag("viewer-message");
        message.setGravity(Gravity.CENTER);
        message.setTextColor(Color.parseColor("#ECEBE6"));
        stage.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(video, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        stage.addView(message, new FrameLayout.LayoutParams(-1, -1));
        // A sound's place: a line across its picture (waveform, or a MIDI's hits).
        final View playhead = new View(app);
        playhead.setTag("viewer-playhead");
        playhead.setBackgroundColor(Color.parseColor("#E0B04A"));
        playhead.setVisibility(View.GONE);
        stage.addView(playhead, new FrameLayout.LayoutParams(app.dp(2), -1));
        TextView prev = app.pill("◀", false, null);
        prev.setTag("viewer-prev");
        TextView next = app.pill("▶", false, null);
        next.setTag("viewer-next");
        stage.addView(prev, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        stage.addView(next, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        // First and Last: the playlist's first and last file (the stage's top corners).
        TextView first = app.pill("⏮", false, null);
        first.setTag("viewer-first");
        TextView lastOne = app.pill("⏭", false, null);
        lastOne.setTag("viewer-last");
        stage.addView(first, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP));
        stage.addView(lastOne, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        // The controls: Play, Pause, Stop, Mute, volume, speed, zoom.
        // The controls in three rows that fit the screen: playing (and Loop video, Auto mode);
        // Mute and the volume; speed and zoom.
        final TextView play = app.pill("\u25b6 Play", false, null);
        play.setTag("viewer-play");
        final TextView pause = app.pill("\u275a\u275a Pause", false, null);
        pause.setTag("viewer-pause");
        final TextView stop = app.pill("\u25a0 Stop", false, null);
        stop.setTag("viewer-stop");
        final TextView loopPill = app.pill("\u27f3 Loop this video", s.loop, null);
        loopPill.setTag("viewer-loop");
        final TextView autoPill = app.pill("Auto mode", s.auto, null);
        autoPill.setTag("viewer-auto");
        final TextView mute = app.pill(s.mute ? "Unmute" : "Mute", s.mute, null);
        mute.setTag("viewer-mute");
        final SeekBar volume = new SeekBar(app);
        volume.setMax(100);
        volume.setProgress(s.volume);
        volume.setTag("viewer-volume");
        final TextView volumeLevel = app.text(s.volume + "%", 13, false);
        volumeLevel.setTag("viewer-volume-level");
        volumeLevel.setTextColor(Color.parseColor("#ECEBE6"));
        volumeLevel.setMinWidth(app.dp(44));
        final TextView speed = app.pill("Speed " + Playlist.speedText(s.speed), false, null);
        speed.setTag("viewer-speed");
        final TextView zoomOut = app.pill("\u2212", false, null);
        zoomOut.setTag("viewer-zoom-out");
        final TextView zoomIn = app.pill("+", false, null);
        zoomIn.setTag("viewer-zoom-in");
        final TextView fit = app.pill("Fit", false, null);
        fit.setTag("viewer-fit");
        final TextView zoomText = app.text(MediaDir.zoomLabel(s.zoom), 13, false);
        zoomText.setTag("viewer-zoom");
        zoomText.setTextColor(Color.parseColor("#ECEBE6"));
        // Each row shares the screen's width (a row wider than the phone cut Loop video off).
        zoomText.setGravity(Gravity.CENTER);
        LinearLayout rowPlay = shared(app, play, pause, stop);
        rowPlay.setPadding(app.dp(6), app.dp(4), app.dp(6), 0);
        LinearLayout rowLoop = shared(app, loopPill, autoPill);
        rowLoop.setPadding(app.dp(6), 0, app.dp(6), 0);
        LinearLayout rowSound = app.row();
        rowSound.setGravity(Gravity.CENTER_VERTICAL);
        rowSound.setPadding(app.dp(6), 0, app.dp(6), 0);
        rowSound.addView(mute);
        rowSound.addView(volume, new LinearLayout.LayoutParams(0, -2, 1f));
        rowSound.addView(volumeLevel);
        LinearLayout rowLook = shared(app, speed, zoomOut, zoomIn, fit, zoomText);
        rowLook.setPadding(app.dp(6), 0, app.dp(6), app.dp(6));
        root.addView(rowPlay);
        root.addView(rowLoop);
        root.addView(rowSound);
        root.addView(rowLook);
        v.setContentView(root);
        final int[] at = {Math.max(0, Math.min(start, p.items.size() - 1))};
        final float[] zoom = {(float) MediaDir.clampZoom(s.zoom)};
        // Pictures and videos each keep a zoom of their own.
        final boolean[] videoShown = new boolean[1];
        final float[] rate = {s.speed};
        final boolean[] muted = {s.mute};
        final boolean[] held = new boolean[1];
        final boolean[] ended = new boolean[1];
        final long[] shownAt = new long[1];
        final android.media.MediaPlayer[] player = new android.media.MediaPlayer[1];
        final PromptVault vault = vault(app);
        final Runnable keep = () -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.volume = volume.getProgress();
            now.mute = muted[0];
            now.speed = rate[0];
            if (videoShown[0]) now.zoomVideo = zoom[0];
            else now.zoom = zoom[0];
            Playlist.saveSettings(dir, now);
        };
        final Runnable applyZoom = () -> {
            zoomText.setText("Zoom " + MediaDir.zoomLabel(zoom[0]));
            for (View x : new View[] {picture, video}) {
                x.setScaleX(zoom[0]);
                x.setScaleY(zoom[0]);
                if (zoom[0] <= 1f) {
                    x.setTranslationX(0);
                    x.setTranslationY(0);
                }
            }
        };
        final Runnable applySound = () -> {
            android.media.MediaPlayer mp = player[0];
            if (mp == null) return;
            float level = muted[0] ? 0f : volume.getProgress() / 100f;
            try {
                mp.setVolume(level, level);
            } catch (Exception ignored) {
                // released
            }
        };
        // A sound plays at its own speed (MIDI, WAV, MP3): speed and zoom are for videos and pictures.
        final boolean[] soundShown = new boolean[1];
        final Runnable applySpeed = () -> {
            android.media.MediaPlayer mp = player[0];
            if (mp == null || soundShown[0] || android.os.Build.VERSION.SDK_INT < 23) return;
            try {
                boolean was = mp.isPlaying();
                mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(rate[0]));
                if (!was) mp.pause();
            } catch (Exception ignored) {
                // not while it is not ready
            }
        };
        final Runnable[] show = new Runnable[1];
        show[0] = () -> {
            try {
                video.stopPlayback();
            } catch (Exception ignored) {
                // nothing playing
            }
            player[0] = null;
            held[0] = false;
            ended[0] = false;
            shownAt[0] = System.currentTimeMillis();
            Playlist.Item it = p.items.get(at[0]);
            viewerAt = at[0];
            where.setText((at[0] + 1) + " / " + p.items.size() + "  ·  " + it.title);
            // A video (or a sound) at its own zoom, a picture at its own.
            int kindShown = it.kind();
            videoShown[0] = kindShown == MediaDir.VIDEO || kindShown == MediaDir.SOUND;
            soundShown[0] = kindShown == MediaDir.SOUND;
            rowLook.setVisibility(soundShown[0] ? View.GONE : View.VISIBLE);
            loopPill.setText(Playlist.loopLabel(kindShown));
            playhead.setVisibility(View.GONE);
            picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Playlist.Settings kept = Playlist.settings(dir);
            zoom[0] = (float) MediaDir.clampZoom(videoShown[0] ? kept.zoomVideo : kept.zoom);
            zoomText.setText("Zoom " + MediaDir.zoomLabel(zoom[0]));
            timeText.setText(videoShown[0] ? Playlist.time(0, -1) : "");
            timeText.setVisibility(videoShown[0] ? View.VISIBLE : View.GONE);
            picture.setImageBitmap(null);
            picture.setVisibility(View.GONE);
            video.setVisibility(View.GONE);
            message.setVisibility(View.GONE);
            int k = it.kind();
            if (k == MediaDir.PICTURE) {
                byte[] b = bytes(app, it, vault);
                Bitmap bmp = b == null ? null : PictureOffer.decode(b, 2048);
                if (bmp == null) {
                    message.setText(b == null ? it.title + " is not there any more" : "Could not show " + it.title);
                    message.setVisibility(View.VISIBLE);
                    viewerNote = b == null ? "missing" : "message";
                    return;
                }
                picture.setImageBitmap(bmp);
                picture.setVisibility(View.VISIBLE);
                viewerNote = "picture";
                applyZoom.run();
                return;
            }
            if (k == MediaDir.VIDEO || k == MediaDir.SOUND) {
                File f = local(app, it, vault);
                if (f != null && !f.isFile()) {
                    message.setText(it.title + " is not there any more");
                    message.setVisibility(View.VISIBLE);
                    viewerNote = "missing";
                    return;
                }
                video.setVisibility(View.VISIBLE);
                if (f != null) video.setVideoPath(f.getAbsolutePath());
                else video.setVideoURI(Uri.parse(it.file));
                viewerNote = k == MediaDir.VIDEO ? "video" : "sound";
                if (k == MediaDir.SOUND) {
                    // Its picture: a drum MIDI's hits (as the MIDI editor shows them), a WAV's or MP3's waveform.
                    final int shownIndex = at[0];
                    final int pw = Math.max(64, stage.getWidth() > 0 ? stage.getWidth() : app.getResources().getDisplayMetrics().widthPixels);
                    final int ph = Math.max(48, stage.getHeight() > 0 ? stage.getHeight() / 2 : pw / 3);
                    new Thread(() -> {
                        byte[] b = bytes(app, it, vault);
                        int[] px = SoundPicture.pixels(it.file, b, pw, ph);
                        if (px == null) return;
                        Bitmap drawn = Bitmap.createBitmap(px, pw, ph, Bitmap.Config.ARGB_8888);
                        app.runOnUiThread(() -> {
                            if (at[0] != shownIndex || !v.isShowing()) return;
                            picture.setScaleType(ImageView.ScaleType.FIT_XY);
                            picture.setImageBitmap(drawn);
                            picture.setVisibility(View.VISIBLE);
                            playhead.setVisibility(View.VISIBLE);
                            playhead.setTranslationX(0);
                            soundPicture = true;
                        });
                    }, "pulsekit-sound-picture").start();
                }
                applyZoom.run();
                return;
            }
            message.setText(it.title + ": not a picture, video or sound");
            message.setVisibility(View.VISIBLE);
            viewerNote = "message";
        };
        video.setOnPreparedListener(mp -> {
            player[0] = mp;
            applySound.run();
            // Loop video: it plays again at its end until an arrow is tapped.
            try {
                mp.setLooping(Playlist.settings(dir).loop);
            } catch (Exception ignored) {
                // released
            }
            Playlist.Item it = p.items.get(at[0]);
            if (Playlist.settings(dir).autoplay || it.kind() == MediaDir.SOUND) {
                video.start();
                applySpeed.run();
            } else {
                video.seekTo(1);
            }
        });
        video.setOnCompletionListener(mp -> ended[0] = true);
        video.setOnErrorListener((mp, what, extra) -> {
            message.setText("Could not play " + p.items.get(at[0]).title);
            message.setVisibility(View.VISIBLE);
            ended[0] = true;
            return true;
        });
        final Runnable goNext = () -> {
            int n = Playlist.next(at[0], p.items.size(), Playlist.settings(dir).repeat);
            if (n < 0) {
                app.setNow("The end of " + p.name);
                return;
            }
            at[0] = n;
            show[0].run();
        };
        prev.setOnClickListener(x -> {
            int n = Playlist.previous(at[0], p.items.size(), Playlist.settings(dir).repeat);
            if (n < 0) {
                app.setNow("The start of " + p.name);
                return;
            }
            at[0] = n;
            show[0].run();
        });
        next.setOnClickListener(x -> goNext.run());
        first.setOnClickListener(x -> {
            if (p.items.isEmpty()) return;
            at[0] = 0;
            show[0].run();
        });
        lastOne.setOnClickListener(x -> {
            if (p.items.isEmpty()) return;
            at[0] = p.items.size() - 1;
            show[0].run();
        });
        play.setOnClickListener(x -> {
            if (video.getVisibility() != View.VISIBLE) return;
            video.start();
            applySpeed.run();
        });
        pause.setOnClickListener(x -> {
            if (video.getVisibility() != View.VISIBLE) return;
            if (video.isPlaying()) video.pause();
            else video.start();
        });
        stop.setOnClickListener(x -> {
            if (video.getVisibility() != View.VISIBLE) return;
            video.pause();
            video.seekTo(0);
        });
        // Loop video and Auto mode (one at a time), kept as the Viewer tab's.
        loopPill.setOnClickListener(x -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.loop(!now.loop);
            Playlist.saveSettings(dir, now);
            app.paintChip(loopPill, now.loop);
            app.paintChip(autoPill, now.auto);
            shownAt[0] = System.currentTimeMillis();
            android.media.MediaPlayer mp = player[0];
            if (mp != null) {
                try {
                    mp.setLooping(now.loop);
                } catch (Exception ignored) {
                    // released
                }
            }
        });
        autoPill.setOnClickListener(x -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.auto(!now.auto);
            Playlist.saveSettings(dir, now);
            app.paintChip(loopPill, now.loop);
            app.paintChip(autoPill, now.auto);
            shownAt[0] = System.currentTimeMillis();
            android.media.MediaPlayer mp = player[0];
            if (mp != null) {
                try {
                    mp.setLooping(now.loop);
                } catch (Exception ignored) {
                    // released
                }
            }
        });
        mute.setOnClickListener(x -> {
            muted[0] = !muted[0];
            mute.setText(muted[0] ? "Unmute" : "Mute");
            app.paintChip(mute, muted[0]);
            applySound.run();
            keep.run();
        });
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int value, boolean fromUser) {
                volumeLevel.setText(value + "%");
                applySound.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {}

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                keep.run();
            }
        });
        // Speed: each tap the next one (½× … 2×, then ½× again).
        speed.setOnClickListener(x -> {
            int i = 0;
            for (int k = 0; k < Playlist.SPEEDS.length; k++) if (Math.abs(Playlist.SPEEDS[k] - rate[0]) < 0.01f) i = k;
            rate[0] = Playlist.SPEEDS[(i + 1) % Playlist.SPEEDS.length];
            speed.setText("Speed " + Playlist.speedText(rate[0]));
            applySpeed.run();
            keep.run();
        });
        zoomOut.setOnClickListener(x -> {
            zoom[0] = (float) MediaDir.zoom(zoom[0], false);
            applyZoom.run();
            keep.run();
        });
        zoomIn.setOnClickListener(x -> {
            zoom[0] = (float) MediaDir.zoom(zoom[0], true);
            applyZoom.run();
            keep.run();
        });
        fit.setOnClickListener(x -> {
            zoom[0] = 1f;
            applyZoom.run();
            keep.run();
        });
        // A long press: a video plays, or pauses; a picture holds Auto mode there (again: it goes on). Two fingers zoom.
        final ScaleGestureDetector pinch = new ScaleGestureDetector(app, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector g) {
                zoom[0] = (float) MediaDir.clampZoom(zoom[0] * g.getScaleFactor());
                applyZoom.run();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector g) {
                keep.run();
            }
        });
        final int slop = android.view.ViewConfiguration.get(app).getScaledTouchSlop();
        final float[] down = new float[2];
        final Runnable hold = () -> {
            if (video.getVisibility() == View.VISIBLE) {
                if (video.isPlaying()) video.pause();
                else {
                    video.start();
                    applySpeed.run();
                }
            } else {
                held[0] = !held[0];
                shownAt[0] = System.currentTimeMillis();
                app.setNow(held[0] ? "Held: Auto mode waits on this picture (a long press again goes on)" : "Auto mode goes on");
            }
        };
        stage.setOnTouchListener((x, ev) -> {
            pinch.onTouchEvent(ev);
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                down[0] = ev.getRawX();
                down[1] = ev.getRawY();
                main.postDelayed(hold, android.view.ViewConfiguration.getLongPressTimeout());
            } else if (act == MotionEvent.ACTION_MOVE) {
                if (Math.abs(ev.getRawX() - down[0]) > slop || Math.abs(ev.getRawY() - down[1]) > slop) main.removeCallbacks(hold);
            } else {
                main.removeCallbacks(hold);
            }
            return true;
        });
        stageHold = hold;
        // Auto mode: a picture for its seconds, a video or a sound to its end, then the next.
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!v.isShowing()) return;
                // The video's place: mm:ss / mm:ss.
                if (video.getVisibility() == View.VISIBLE) {
                    try {
                        timeText.setText(Playlist.time(video.getCurrentPosition(), player[0] == null ? -1 : video.getDuration()));
                        // A sound's place on its picture.
                        if (playhead.getVisibility() == View.VISIBLE && player[0] != null && video.getDuration() > 0) {
                            playhead.setTranslationX(stage.getWidth() * (float) video.getCurrentPosition() / video.getDuration());
                        }
                    } catch (Exception ignored) {
                        // not ready
                    }
                }
                Playlist.Settings now = Playlist.settings(dir);
                if (now.auto && !held[0]) {
                    boolean playable = video.getVisibility() == View.VISIBLE;
                    if (playable ? ended[0] : System.currentTimeMillis() - shownAt[0] >= now.seconds * 1000L) goNext.run();
                }
                main.postDelayed(this, 250);
            }
        };
        v.setOnDismissListener(d -> {
            main.removeCallbacks(tick);
            try {
                video.stopPlayback();
            } catch (Exception ignored) {
                // stopped
            }
        });
        show[0].run();
        lastViewer = v;
        v.show();
        main.postDelayed(tick, 250);
        autoTick = tick;
    }

    /** A row whose views share its width equally (it never runs past the screen's edge). */
    private static LinearLayout shared(MainActivity app, View... views) {
        LinearLayout row = app.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (View x : views) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(app.dp(2), app.dp(3), app.dp(2), app.dp(3));
            if (x instanceof TextView) {
                ((TextView) x).setSingleLine(true);
                ((TextView) x).setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            row.addView(x, lp);
        }
        return row;
    }

    /** Whether the viewer drew a sound's picture (for the tests). */
    static boolean soundPicture;

    /** The viewer's long press and Auto mode tick, for the tests. */
    static Runnable stageHold;
    static Runnable autoTick;
}
