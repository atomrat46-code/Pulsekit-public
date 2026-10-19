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
        // The playlist picked (a tap lists them), New, Rename, Delete, Import .pls, Merge .pls, Save pls as.
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
        TextView export = app.pill("Save pls as…", false, v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/x-scpls");
            intent.putExtra(Intent.EXTRA_TITLE, p.name + Playlist.EXT);
            try {
                app.startActivityForResult(intent, SAVE_PLS);
            } catch (Exception ex) {
                app.setNow("No app here saves files: " + ex.getMessage());
            }
        });
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
            app.setNow(p.add(Playlist.dbAddress(f), f.name) ? f.name + " added to " + p.name : f.name + " is in " + p.name + " already");
            save(app, p);
            show(app, p.name);
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
        final CheckBox auto = box(app, "Auto mode: the next file shows by itself (else tap ▶)", s.auto, "playlist-auto");
        final CheckBox autoplay = box(app, "Autoplay video (else a long press, or Play, starts it)", s.autoplay, "playlist-autoplay");
        final int[] seconds = {s.seconds};
        final TextView secs = app.text("", 14, false);
        secs.setTag("playlist-seconds");
        final Runnable keep = () -> {
            Playlist.Settings now = Playlist.settings(dir);
            now.repeat = repeat.isChecked();
            now.auto = auto.isChecked();
            now.autoplay = autoplay.isChecked();
            now.seconds = seconds[0];
            Playlist.saveSettings(dir, now);
            secs.setText("In Auto mode a picture shows for " + seconds[0] + " s");
        };
        keep.run();
        for (CheckBox c : new CheckBox[] {repeat, auto, autoplay}) {
            c.setOnCheckedChangeListener((b, on) -> keep.run());
            col.addView(c);
        }
        LinearLayout row = app.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(secs);
        TextView less = app.pill("−", false, v -> {
            seconds[0] = Math.max(1, seconds[0] - 1);
            keep.run();
        });
        less.setTag("playlist-seconds-less");
        TextView more = app.pill("+", false, v -> {
            seconds[0] = Math.min(600, seconds[0] + 1);
            keep.run();
        });
        more.setTag("playlist-seconds-more");
        row.addView(less);
        row.addView(more);
        col.addView(row);
        TextView start = app.pill("Start viewer", true, v -> viewer(app, p, 0));
        start.setTag("playlist-start");
        start.setEnabled(!p.items.isEmpty());
        col.addView(start);
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
                java.io.OutputStream out = app.getContentResolver().openOutputStream(uris.get(0));
                try {
                    out.write(shown.toPls().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } finally {
                    out.close();
                }
                app.setNow("Saved " + shown.name + Playlist.EXT);
            } catch (Exception ex) {
                app.setNow("Could not save " + shown.name + Playlist.EXT + ": " + ex.getMessage());
            }
            return;
        }
        if (code == PICK_FILES) {
            int added = 0;
            for (Uri u : uris) {
                try {
                    app.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                    // readable for now
                }
                if (shown.add(u.toString(), name(app, u))) added++;
            }
            save(app, shown);
            app.setNow(added + (added == 1 ? " file" : " files") + " added to " + shown.name);
            show(app, shown.name);
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

    /** "Add to playlist…": the playlists (Default first), then New playlist…; the file goes at the end of the one picked. */
    static void addTo(final MainActivity app, final String file, final String title) {
        final File dir = app.getFilesDir();
        final String[] rows = Playlist.menu(dir);
        lastAsk = new AlertDialog.Builder(app).setTitle(Playlist.ADD.replace("…", "") + " · " + title).setItems(rows, (d, w) -> {
            if (w < rows.length - 1) app.setNow(Playlist.addTo(dir, rows[w], file, title));
            else named(app, Playlist.NEW, "", typed -> app.setNow(Playlist.addTo(dir, typed, file, title)));
        }).setNegativeButton("Cancel", null).show();
    }

    static void addToDefault(MainActivity app, String file, String title) {
        app.setNow(Playlist.addTo(app.getFilesDir(), Playlist.DEFAULT, file, title));
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
        TextView prev = app.pill("◀", false, null);
        prev.setTag("viewer-prev");
        TextView next = app.pill("▶", false, null);
        next.setTag("viewer-next");
        stage.addView(prev, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        stage.addView(next, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        // The controls: Play, Pause, Stop, Mute, volume, speed, zoom.
        HorizontalScrollView barScroll = new HorizontalScrollView(app);
        LinearLayout bar = app.row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(app.dp(6), app.dp(4), app.dp(6), app.dp(6));
        final TextView play = app.pill("▶ Play", false, null);
        play.setTag("viewer-play");
        final TextView pause = app.pill("❚❚ Pause", false, null);
        pause.setTag("viewer-pause");
        final TextView stop = app.pill("■ Stop", false, null);
        stop.setTag("viewer-stop");
        final TextView mute = app.pill(s.mute ? "Unmute" : "Mute", s.mute, null);
        mute.setTag("viewer-mute");
        final SeekBar volume = new SeekBar(app);
        volume.setMax(100);
        volume.setProgress(s.volume);
        volume.setTag("viewer-volume");
        final TextView speed = app.pill(Playlist.speedText(s.speed), false, null);
        speed.setTag("viewer-speed");
        final TextView zoomOut = app.pill("−", false, null);
        zoomOut.setTag("viewer-zoom-out");
        final TextView zoomIn = app.pill("+", false, null);
        zoomIn.setTag("viewer-zoom-in");
        final TextView fit = app.pill("Fit", false, null);
        fit.setTag("viewer-fit");
        bar.addView(play);
        bar.addView(pause);
        bar.addView(stop);
        bar.addView(mute);
        bar.addView(volume, new LinearLayout.LayoutParams(app.dp(120), -2));
        bar.addView(speed);
        bar.addView(zoomOut);
        bar.addView(zoomIn);
        bar.addView(fit);
        barScroll.addView(bar);
        root.addView(barScroll);
        v.setContentView(root);
        final int[] at = {Math.max(0, Math.min(start, p.items.size() - 1))};
        final float[] zoom = {(float) MediaDir.clampZoom(s.zoom)};
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
            now.zoom = zoom[0];
            Playlist.saveSettings(dir, now);
        };
        final Runnable applyZoom = () -> {
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
        final Runnable applySpeed = () -> {
            android.media.MediaPlayer mp = player[0];
            if (mp == null || android.os.Build.VERSION.SDK_INT < 23) return;
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
            speed.setText(Playlist.speedText(rate[0]));
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

    /** The viewer's long press and Auto mode tick, for the tests. */
    static Runnable stageHold;
    static Runnable autoTick;
}
