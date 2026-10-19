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
            final List<String> names = new ArrayList<String>();
            names.add("(none)");
            try {
                names.addAll(MediaTags.categoryNames(PromptVault.open(app.getFilesDir())));
            } catch (Throwable ex) {
                // no library: none only
            }
            main.post(() -> {
                int now = Math.max(0, names.indexOf(ReadyRecent.picked(ready, MediaTags.category(key))));
                MediaBrowser.lastAsk = new AlertDialog.Builder(app).setTitle("Category for " + name)
                    .setSingleChoiceItems(names.toArray(new String[0]), now, (d, which) -> {
                        String picked = names.get(which);
                        MediaTags.setCategory(key, which == 0 ? "" : picked);
                        if (which > 0) ReadyRecent.setCategory(ready, picked);
                        app.setNow(name + (which == 0 ? ": no category" : " is in " + picked));
                        d.dismiss();
                        if (after != null) after.run();
                    })
                    .setNegativeButton("Cancel", null).show();
            });
        }, "pulsekit-file-categories").start();
    }
}
