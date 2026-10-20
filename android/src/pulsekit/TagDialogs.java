package pulsekit;

import android.app.AlertDialog;
import java.util.ArrayList;
import java.util.List;

/**
 * Tags… and Category… for a file by its name and size (MediaTags), as the Media browser has them:
 * also on the Reference, Result and Prompt files' menus and Browse DB's. The dialog shown last is
 * MediaBrowser.lastAsk (for the tests).
 */
final class TagDialogs {
    private TagDialogs() {}

    /** Tags…: the file's tags, ticked from those made so far; New tag… adds one. `after` runs once saved. */
    static void tags(final MainActivity app, final String name, final long size, final Runnable after) {
        tags(app, name, size, after, null);
    }

    /**
     * As above; for Picture ready and Video ready (`ready`: ReadyRecent.IMAGE or VIDEO) the first row
     * is Select all (Clear all when every tag is ticked), and a file without tags has the tags used
     * last for that kind ticked.
     */
    static void tags(final MainActivity app, final String name, final long size, final Runnable after, final String ready) {
        MediaTags.load(app.getFilesDir());
        ReadyRecent.load(app.getFilesDir());
        final String key = MediaTags.key(name, size);
        final List<String> known = MediaTags.known();
        final List<String> has = ReadyRecent.ticked(ready, MediaTags.tags(key), known);
        final String[] names = known.toArray(new String[0]);
        final boolean[] on = new boolean[names.length];
        for (int i = 0; i < names.length; i++) on[i] = has.contains(names[i]);
        // Ready: Select all / Clear all as the first row (never ticked itself).
        final boolean withAll = ready != null && names.length > 1;
        final String[] rows = new String[names.length + (withAll ? 1 : 0)];
        final boolean[] shownOn = new boolean[rows.length];
        if (withAll) rows[0] = ReadyRecent.allLabel(on);
        for (int i = 0; i < names.length; i++) {
            rows[i + (withAll ? 1 : 0)] = names[i];
            shownOn[i + (withAll ? 1 : 0)] = on[i];
        }
        AlertDialog.Builder b = new AlertDialog.Builder(app).setTitle("Tags for " + name);
        if (names.length > 0) b.setMultiChoiceItems(rows, shownOn, (d, which, checked) -> {
            if (!withAll) {
                on[which] = checked;
                return;
            }
            android.widget.ListView list = ((AlertDialog) d).getListView();
            if (which == 0) {
                boolean to = !ReadyRecent.CLEAR_ALL.equals(rows[0]);
                for (int i = 0; i < on.length; i++) {
                    on[i] = to;
                    shownOn[i + 1] = to;
                    list.setItemChecked(i + 1, to);
                }
            } else on[which - 1] = checked;
            shownOn[0] = false;
            list.setItemChecked(0, false);
            rows[0] = ReadyRecent.allLabel(on);
            if (list.getAdapter() instanceof android.widget.BaseAdapter) ((android.widget.BaseAdapter) list.getAdapter()).notifyDataSetChanged();
        });
        else b.setMessage("No tags yet: add one with New tag.");
        MediaBrowser.lastAsk = b.setPositiveButton("Save", (d, w) -> {
                List<String> chosen = new ArrayList<String>();
                for (int i = 0; i < names.length; i++) if (on[i]) chosen.add(names[i]);
                tagged(app, name, key, chosen, after, ready);
            })
            .setNeutralButton("New tag…", (d, w) -> {
                final android.widget.EditText field = new android.widget.EditText(app);
                field.setSingleLine(true);
                field.setHint("beach, night (a comma between)");
                field.setTag("media-new-tag-field");
                MediaBrowser.lastAsk = new AlertDialog.Builder(app).setTitle("New tag").setView(field)
                    .setPositiveButton("Add new tag", (d2, w2) -> {
                        List<String> chosen = new ArrayList<String>();
                        for (int i = 0; i < names.length; i++) if (on[i]) chosen.add(names[i]);
                        for (String t : field.getText().toString().split(",")) chosen.add(t);
                        tagged(app, name, key, chosen, after, ready);
                        // The list again, the new tag ticked.
                        tags(app, name, size, after, ready);
                    })
                    .setNegativeButton("Cancel", null).show();
            })
            .setNegativeButton("Cancel", null).show();
    }

    /** Use last tags: the tags used last for `ready`'s kind put on the file at once. */
    static void useLast(MainActivity app, String name, long size, String ready) {
        MediaTags.load(app.getFilesDir());
        ReadyRecent.load(app.getFilesDir());
        List<String> now = ReadyRecent.useLast(ready, MediaTags.key(name, size));
        app.setNow(now == null ? "No tags used yet for this kind of file" : name + ": " + String.join(", ", now));
    }

    /** The ready screens' items with Use last tags: … after Add tag… when tags were used for `ready`'s kind. */
    static String[] withUseLast(MainActivity app, String[] items, String ready) {
        ReadyRecent.load(app.getFilesDir());
        String label = ReadyRecent.useLastLabel(ready);
        if (label == null) return items;
        java.util.List<String> out = new ArrayList<String>(java.util.Arrays.asList(items));
        int at = out.indexOf(MediaTags.ADD_TAG);
        out.add(at < 0 ? out.size() : at + 1, label);
        return out.toArray(new String[0]);
    }

    private static void tagged(MainActivity app, String name, String key, List<String> chosen, Runnable after, String ready) {
        MediaTags.setTags(key, chosen);
        ReadyRecent.setTags(ready, MediaTags.tags(key));
        String line = MediaTags.line(key);
        app.setNow(line.length() == 0 ? name + ": no tags" : name + ": " + line);
        if (after != null) after.run();
    }

    /** Category…: one of the prompt library's categories for the file, or none (read off the main thread). */
    static void category(final MainActivity app, final String name, final long size, final Runnable after) {
        category(app, name, size, after, null);
    }

    /** As above; for Picture ready and Video ready (`ready`) the category used last for that kind is picked first. */
    static void category(final MainActivity app, final String name, final long size, final Runnable after, final String ready) {
        MediaTags.load(app.getFilesDir());
        ReadyRecent.load(app.getFilesDir());
        final String key = MediaTags.key(name, size);
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            PromptVault opened = null;
            final List<String> names = new ArrayList<String>();
            try {
                opened = PromptVault.open(app.getFilesDir());
                names.addAll(MediaTags.categoryRows(opened));
            } catch (Throwable ex) {
                // no library: none only
                names.add("(none)");
            }
            final PromptVault vault = opened;
            main.post(() -> {
                int now = Math.max(0, names.indexOf(ReadyRecent.picked(ready, MediaTags.category(key))));
                final Runnable again = () -> category(app, name, size, after, ready);
                AlertDialog shown = new AlertDialog.Builder(app).setTitle("Category for " + name)
                    .setSingleChoiceItems(names.toArray(new String[0]), now, (d, which) -> {
                        String picked = names.get(which);
                        if (vault != null && MediaTags.NEW_CATEGORY.equals(picked)) {
                            // A new main category: the list again, with it.
                            d.dismiss();
                            named(app, "New category", null, n -> {
                                MediaTags.addCategory(vault, null, n);
                                again.run();
                            });
                            return;
                        }
                        if (vault != null && MediaTags.NEW_SUBCATEGORY.equals(picked)) {
                            // Which category it goes under, then its name; the list again, with it.
                            d.dismiss();
                            final List<String> parents = MediaTags.categoryNames(vault);
                            if (parents.isEmpty()) {
                                app.setNow("Make a category first (" + MediaTags.NEW_CATEGORY + ")");
                                again.run();
                                return;
                            }
                            MediaBrowser.lastAsk = new AlertDialog.Builder(app).setTitle("New subcategory under")
                                .setItems(parents.toArray(new String[0]), (d2, w2) -> subcategory(app, vault, parents.get(w2), again))
                                .setNegativeButton("Cancel", (d2, w2) -> again.run()).show();
                            return;
                        }
                        if (vault != null && MediaTags.INTO_NEW.equals(picked)) {
                            // A new main category, and the file in it.
                            d.dismiss();
                            named(app, "Add into new category", null, n -> {
                                String path = MediaTags.addCategory(vault, null, n);
                                placed(app, name, key, path, ready, after);
                            });
                            return;
                        }
                        d.dismiss();
                        placed(app, name, key, which == 0 ? "" : picked, ready, after);
                    })
                    .setNegativeButton("Cancel", null).show();
                // Held on a category: a new subcategory under it.
                shown.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                    String held = names.get(position);
                    if (vault == null || position == 0 || held.startsWith("+ ")) return false;
                    shown.dismiss();
                    subcategory(app, vault, held, again);
                    return true;
                });
                MediaBrowser.lastAsk = shown;
            });
        }, "pulsekit-file-categories").start();
    }

    /** The file in the category (none for ""), said, and `after` run. */
    private static void placed(MainActivity app, String name, String key, String path, String ready, Runnable after) {
        MediaTags.setCategory(key, path);
        if (path.length() > 0) ReadyRecent.setCategory(ready, path);
        app.setNow(name + (path.length() == 0 ? ": no category" : " is in " + path));
        if (after != null) after.run();
    }

    /** A new subcategory under `parentPath` (its name asked), then `again` (the list with it). */
    private static void subcategory(MainActivity app, PromptVault vault, String parentPath, Runnable again) {
        named(app, "New subcategory under " + parentPath, null, n -> {
            MediaTags.addCategory(vault, parentPath, n);
            again.run();
        });
    }

    interface Named {
        void take(String name) throws Exception;
    }

    /** A name asked for (Add / Cancel); `done` gets it; a failure is said on the status line. */
    static void named(final MainActivity app, String title, String start, final Named done) {
        final android.widget.EditText field = new android.widget.EditText(app);
        field.setSingleLine(true);
        field.setTag("category-name-field");
        if (start != null) field.setText(start);
        MediaBrowser.lastAsk = new AlertDialog.Builder(app).setTitle(title).setView(field)
            .setPositiveButton("Add", (d, w) -> {
                String n = field.getText().toString().trim();
                if (n.length() == 0) return;
                try {
                    done.take(n);
                } catch (Exception ex) {
                    app.setNow(ex.getMessage() == null ? "Could not add " + n : ex.getMessage());
                }
            })
            .setNegativeButton("Cancel", null).show();
    }
}
