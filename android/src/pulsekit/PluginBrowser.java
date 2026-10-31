package pulsekit;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PyJav > Plugins on the phone: the drum kits Android plays (SF2, SFZ) in their folders (Folders sets
 * them), with the same favourites, categories, groups, tags, filters, sorts and My order as the
 * desktop's Plugin Browser (plugin-library.txt). Types lists every type and which app uses it: VST,
 * AU, LV2 and CLAP plugins are desktop code, so the phone lists them as such. A kit's page: Use (onto
 * the pads), Test (a beat with its sounds), favourite, Category, Group, Tags, Up and Down.
 */
final class PluginBrowser {
    private PluginBrowser() {}

    static AlertDialog last;
    static AlertDialog page;
    static volatile String lastSaid = "";
    /** The tests' answers (Category, Group, Tags, Folders): null asks. */
    static java.util.function.Function<String, String> answer;

    static String type = PluginLibrary.ANY, sort = PluginLibrary.SORTS[0], category = PluginLibrary.ANY, group = PluginLibrary.ANY, tag = PluginLibrary.ANY, search = "";
    static boolean favourites;
    static final List<File> all = new ArrayList<File>();

    static String home() {
        return android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    static void show(final MainActivity app) {
        PluginLibrary.load(app.getFilesDir());
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app).setTitle("Plugin Browser").setView(scroll).setPositiveButton("Close", null).create();
        rescan();
        fill(app, col);
        last.show();
    }

    static void rescan() {
        all.clear();
        all.addAll(PluginLibrary.scan(true, home(), null));
    }

    static void fill(final MainActivity app, final LinearLayout col) {
        col.removeAllViews();
        final Runnable again = () -> fill(app, col);
        LinearLayout types = app.row();
        for (final String t : new String[] {PluginLibrary.ANY, PluginLibrary.SF2, PluginLibrary.SFZ}) {
            TextView chip = app.pill(t, t.equals(type), v -> {
                type = t;
                again.run();
            });
            chip.setTag("browser-type:" + t);
            types.addView(chip);
        }
        TextView fav = app.pill("\u2605 Favourites", favourites, v -> {
            favourites = !favourites;
            again.run();
        });
        fav.setTag("browser-favourites");
        types.addView(fav);
        col.addView(app.chipStrip(types));
        LinearLayout filters = app.row();
        filters.addView(chooser(app, "Sort: " + sort, "browser-sort", java.util.Arrays.asList(PluginLibrary.SORTS), s -> sort = s, again));
        filters.addView(chooser(app, "Category: " + category, "browser-category", withAny(PluginLibrary.categories()), s -> category = s, again));
        filters.addView(chooser(app, "Group: " + group, "browser-group", withAny(PluginLibrary.groups()), s -> group = s, again));
        filters.addView(chooser(app, "Tag: " + tag, "browser-tag", withAny(PluginLibrary.tags()), s -> tag = s, again));
        col.addView(app.chipStrip(filters));
        final EditText find = new EditText(app);
        find.setTag("browser-search");
        find.setHint("Search: part of a name, category, group or tag (* and ?)");
        find.setText(search);
        find.setSingleLine(true);
        find.setOnEditorActionListener((v, id, e) -> {
            search = find.getText().toString();
            again.run();
            return true;
        });
        col.addView(find);
        LinearLayout tools = app.row();
        TextView folders = app.outline("Folders", false, v -> folders(app, () -> {
            rescan();
            again.run();
        }));
        folders.setTag("browser-folders");
        tools.addView(folders);
        TextView typesInfo = app.outline("Types", false, v -> {
            lastSaid = PluginLibrary.typesTable(true);
            new AlertDialog.Builder(app).setTitle("Plugin and kit types").setMessage(lastSaid).setPositiveButton("Close", null).show();
        });
        typesInfo.setTag("browser-types");
        tools.addView(typesInfo);
        TextView rescan = app.outline("Rescan", false, v -> {
            rescan();
            again.run();
        });
        rescan.setTag("browser-rescan");
        tools.addView(rescan);
        col.addView(app.chipStrip(tools));
        final List<File> shown = PluginLibrary.shown(all, type, favourites, category, group, tag, search, sort);
        TextView count = app.text(shown.size() + " of " + all.size() + (all.isEmpty() ? " \u2014 no kits in the folders (Folders sets them; Pulsekit needs the files permission to look in Download)" : ""), 12, false);
        count.setTag("browser-count");
        count.setPadding(0, app.dp(6), 0, app.dp(4));
        col.addView(count);
        for (final File f : shown) {
            TextView line = app.text(PluginLibrary.line(f), 14, false);
            line.setTag("browser-item:" + PluginLibrary.name(f));
            line.setPadding(0, app.dp(8), 0, app.dp(8));
            line.setOnClickListener(v -> open(app, f, shown, again));
            col.addView(line);
        }
    }

    /** A kit's page: what Info says, and its menu. */
    static void open(final MainActivity app, final File f, final List<File> shown, final Runnable changed) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), app.dp(8));
        TextView info = app.text(PluginLibrary.info(f, true), 13, false);
        info.setTag("browser-info");
        info.setTextIsSelectable(true);
        col.addView(info);
        LinearLayout row = app.row();
        final Runnable reopen = () -> {
            changed.run();
            if (page != null) page.dismiss();
            open(app, f, PluginLibrary.shown(all, type, favourites, category, group, tag, search, sort), changed);
        };
        row.addView(tagged(app.outline(PluginLibrary.entry(f).favourite ? "Remove from favourites" : "Add to favourites", false, v -> {
            say(app, PluginLibrary.name(f) + (PluginLibrary.toggleFavourite(f) ? " is a favourite" : " is not a favourite any more"));
            reopen.run();
        }), "browser-fav"));
        for (final String what : new String[] {"Category", "Group", "Tags"}) {
            row.addView(tagged(app.outline(what, false, v -> ask(app, f, what, reopen)), "browser-set-" + what.toLowerCase(java.util.Locale.ROOT)));
        }
        row.addView(tagged(app.outline("Up", false, v -> {
            PluginLibrary.move(PluginLibrary.shown(all, type, favourites, category, group, tag, search, PluginLibrary.SORTS[6]), f, -1);
            sort = PluginLibrary.SORTS[6];
            reopen.run();
        }), "browser-up"));
        row.addView(tagged(app.outline("Down", false, v -> {
            PluginLibrary.move(PluginLibrary.shown(all, type, favourites, category, group, tag, search, PluginLibrary.SORTS[6]), f, 1);
            sort = PluginLibrary.SORTS[6];
            reopen.run();
        }), "browser-down"));
        col.addView(app.chipStrip(row));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        if (page != null && page.isShowing()) page.dismiss();
        page = new AlertDialog.Builder(app).setTitle(PluginLibrary.name(f)).setView(scroll)
            .setPositiveButton("Use", (d, w) -> use(app, f))
            .setNeutralButton("Test", null)
            .setNegativeButton("Close", null).create();
        page.show();
        android.widget.Button test = page.getButton(AlertDialog.BUTTON_NEUTRAL);
        test.setOnClickListener(v -> test(app, f, s -> app.runOnUiThread(() -> {
            info.append("\n\n" + s);
            say(app, s);
        })));
    }

    /** Use: the kit onto the pads (as File > Import does). */
    static void use(MainActivity app, File f) {
        PluginLibrary.Type t = PluginLibrary.typeOf(f);
        if (t != null && t.name.equals(PluginLibrary.SF2)) {
            try {
                app.projectIo.ingest(java.nio.file.Files.readAllBytes(f.toPath()), f.getName(), null);
                say(app, PluginLibrary.name(f) + " is loading onto the pads (SoundFont)");
            } catch (Exception ex) {
                say(app, "Could not load " + f.getName() + ": " + ex.getMessage());
            }
        } else if (t != null && t.name.equals(PluginLibrary.SFZ)) {
            app.projectIo.loadSfz(f, f.getName());
            say(app, "Loading " + PluginLibrary.name(f) + " onto the pads\u2026");
        } else {
            say(app, PluginLibrary.name(f) + ": " + (t == null ? "not a kit" : t.how));
        }
    }

    /** Test: two bars of a beat with the kit's sounds, through Pulsekit's output (File > Sound devices). */
    static void test(final MainActivity app, final File f, final java.util.function.Consumer<String> said) {
        new Thread(() -> {
            try {
                short[] beat = PluginLibrary.testBeat(PluginLibrary.kitVoices(f));
                String s = "Test beat with " + PluginLibrary.name(f) + ": " + (beat.length / AudioIo.SR) + " s";
                String played = "";
                try {
                    android.media.AudioTrack t = new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC, AudioIo.SR, android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT, Math.max(2, beat.length * 2), android.media.AudioTrack.MODE_STATIC);
                    t.write(beat, 0, beat.length);
                    SoundOut.prefer(t);
                    t.play();
                    Thread.sleep(beat.length * 1000L / AudioIo.SR + 200);
                    t.release();
                } catch (Throwable notPlayed) {
                    played = " (not played: " + notPlayed.getMessage() + ")";
                }
                // Said once, after it played (or could not).
                said.accept(s + played);
            } catch (Throwable ex) {
                said.accept("Could not test " + PluginLibrary.name(f) + ": " + ex.getMessage());
            }
        }, "kit-test").start();
    }

    /** Category, Group or Tags: typed, or one in use tapped (empty clears). */
    static void ask(final MainActivity app, final File f, final String what, final Runnable done) {
        if (answer != null) {
            set(f, what, answer.apply(what));
            say(app, PluginLibrary.name(f) + ": " + what.toLowerCase(java.util.Locale.ROOT) + " set");
            done.run();
            return;
        }
        PluginLibrary.Entry e = PluginLibrary.entry(f);
        final EditText field = new EditText(app);
        field.setText(what.equals("Category") ? e.category : what.equals("Group") ? e.group : String.join(", ", e.tags));
        field.setHint(what.equals("Tags") ? "comma between tags" : what);
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), 0);
        col.addView(field);
        List<String> known = what.equals("Category") ? PluginLibrary.categories() : what.equals("Group") ? PluginLibrary.groups() : PluginLibrary.tags();
        LinearLayout chips = app.row();
        for (final String k : known) {
            chips.addView(app.pill(k, false, v -> {
                String now = field.getText().toString().trim();
                field.setText(what.equals("Tags") && now.length() > 0 ? now + ", " + k : k);
            }));
        }
        if (!known.isEmpty()) col.addView(app.chipStrip(chips));
        new AlertDialog.Builder(app).setTitle(what + " of " + PluginLibrary.name(f)).setView(col)
            .setPositiveButton("OK", (d, w) -> {
                set(f, what, field.getText().toString());
                done.run();
            }).setNegativeButton("Cancel", null).show();
    }

    static void set(File f, String what, String value) {
        if (value == null) return;
        if (what.equals("Category")) PluginLibrary.setCategory(f, value);
        else if (what.equals("Group")) PluginLibrary.setGroup(f, value);
        else PluginLibrary.setTags(f, value);
    }

    /** Folders: SF2's and SFZ's folders (one a line), Defaults, Save. */
    static void folders(final MainActivity app, final Runnable saved) {
        if (answer != null) {
            String got = answer.apply("Folders");
            if (got != null) {
                List<String> paths = lines(got);
                PluginLibrary.setFolders(PluginLibrary.SF2, paths);
                PluginLibrary.setFolders(PluginLibrary.SFZ, paths);
                saved.run();
            }
            return;
        }
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), 0);
        final String[] kinds = {PluginLibrary.SF2, PluginLibrary.SFZ};
        final List<List<String>> lists = new ArrayList<List<String>>();
        for (int i = 0; i < 2; i++) {
            final List<String> paths = new ArrayList<String>(PluginLibrary.folders(kinds[i], true, home()));
            lists.add(paths);
            col.addView(app.sectionLabel(kinds[i] + " FOLDERS (" + paths.size() + ")"));
            // Its folders in a list; Add folder… browses for one, Change… edits or browses the one shown, Delete removes it.
            final android.widget.Spinner shown = new android.widget.Spinner(app);
            shown.setTag("kit-folders:" + kinds[i]);
            final TextView head = (TextView) col.getChildAt(col.getChildCount() - 1);
            final String kind = kinds[i];
            final Runnable refresh = () -> {
                List<String> items = paths.isEmpty() ? java.util.Collections.singletonList(FolderPaths.NONE) : paths;
                int at = Math.max(0, Math.min(shown.getSelectedItemPosition(), items.size() - 1));
                shown.setAdapter(new android.widget.ArrayAdapter<String>(app, android.R.layout.simple_spinner_dropdown_item, new ArrayList<String>(items)));
                shown.setSelection(at);
                head.setText(kind + " FOLDERS (" + paths.size() + ")");
            };
            refresh.run();
            col.addView(shown);
            LinearLayout row = app.row();
            TextView add = app.outline(FolderPaths.ADD, false, v -> pickFolder(app, p -> {
                if (!paths.contains(p)) paths.add(p);
                refresh.run();
                shown.setSelection(paths.indexOf(p));
            }));
            add.setTag("kit-folder-add:" + kind);
            TextView change = app.outline(FolderPaths.CHANGE, false, v -> {
                if (paths.isEmpty()) return;
                final int at = shown.getSelectedItemPosition();
                changeFolder(app, paths.get(at), p -> {
                    if (p.length() == 0) paths.remove(at);
                    else paths.set(at, p);
                    refresh.run();
                });
            });
            change.setTag("kit-folder-change:" + kind);
            TextView delete = app.outline(FolderPaths.DELETE, false, v -> {
                if (paths.isEmpty()) return;
                paths.remove(shown.getSelectedItemPosition());
                refresh.run();
            });
            delete.setTag("kit-folder-delete:" + kind);
            for (TextView b : new TextView[] {add, change, delete}) row.addView(b);
            col.addView(app.chipStrip(row));
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        lastFolders = new AlertDialog.Builder(app).setTitle("Kit folders").setView(scroll)
            .setPositiveButton("Save", (d, w) -> {
                for (int i = 0; i < 2; i++) {
                    List<String> paths = lists.get(i);
                    PluginLibrary.setFolders(kinds[i], paths.equals(PluginLibrary.defaults(kinds[i], true, home())) ? null : paths);
                }
                saved.run();
            })
            .setNeutralButton("Defaults", (d, w) -> {
                for (String k : kinds) PluginLibrary.setFolders(k, null);
                saved.run();
            })
            .setNegativeButton("Cancel", null).show();
    }

    static final int PICK_KIT_FOLDER = 94;
    /** For the tests: the Kit folders window and the Change window shown last. */
    static AlertDialog lastFolders;
    static AlertDialog lastChange;
    /** Where the folder picked goes (Add folder…, Change…'s Browse…). */
    static java.util.function.Consumer<String> pendingFolder;

    /** Android's folder picker; the folder picked (as its path) goes to `got`. */
    static void pickFolder(MainActivity app, java.util.function.Consumer<String> got) {
        pendingFolder = got;
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            app.startActivityForResult(intent, PICK_KIT_FOLDER);
        } catch (Exception ex) {
            say(app, "No folder picker on this phone: " + ex.getMessage());
        }
    }

    /** The picker's folder (MainActivity.onActivityResult): its path to where it was asked for. */
    static void folderPicked(MainActivity app, android.net.Uri tree) {
        java.util.function.Consumer<String> got = pendingFolder;
        pendingFolder = null;
        if (tree == null || got == null) return;
        try {
            app.getContentResolver().takePersistableUriPermission(tree, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // read through its path; the permission only helps
        }
        String path = FolderPaths.fromDocId(android.provider.DocumentsContract.getTreeDocumentId(tree));
        if (path == null) {
            say(app, "That folder is not on the phone's storage: pick one under Internal storage or the memory card");
            return;
        }
        got.accept(path);
        say(app, "Folder " + path);
    }

    /** Change…: the path to edit, or Browse… for another folder in its place; OK keeps it (empty: removed). */
    static void changeFolder(final MainActivity app, String path, final java.util.function.Consumer<String> done) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(18), app.dp(8), app.dp(18), 0);
        final EditText field = new EditText(app);
        field.setText(path);
        field.setTag("kit-folder-path");
        col.addView(field);
        TextView browse = app.outline(FolderPaths.BROWSE, false, v -> pickFolder(app, field::setText));
        browse.setTag("kit-folder-browse");
        col.addView(browse);
        lastChange = new AlertDialog.Builder(app).setTitle("Change folder").setView(col)
            .setPositiveButton("OK", (d, w) -> done.accept(field.getText().toString().trim()))
            .setNegativeButton("Cancel", null).show();
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<String>();
        for (String l : text.split("\n")) if (l.trim().length() > 0) out.add(l.trim());
        return out;
    }

    private static TextView chooser(final MainActivity app, String label, String tag, final List<String> values, final java.util.function.Consumer<String> picked, final Runnable again) {
        TextView chip = app.pill(label, false, v -> new AlertDialog.Builder(app).setItems(values.toArray(new String[0]), (d, w) -> {
            picked.accept(values.get(w));
            again.run();
        }).show());
        chip.setTag(tag);
        return chip;
    }

    private static List<String> withAny(List<String> values) {
        List<String> out = new ArrayList<String>();
        out.add(PluginLibrary.ANY);
        out.addAll(values);
        return out;
    }

    private static TextView tagged(TextView v, String tag) {
        v.setTag(tag);
        return v;
    }

    static void say(MainActivity app, String s) {
        lastSaid = s;
        app.setNow(s);
    }
}
