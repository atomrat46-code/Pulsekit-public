package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import java.util.List;

/**
 * Add to group (a thumbnail's long press) and G's group list (a long press on G), for Browse DB,
 * the Reference and Result file galleries and the Media browser.
 */
final class GroupDialogs {
    private GroupDialogs() {}

    /** The dialog shown last (for the tests). */
    static AlertDialog last;

    /** The groups (✓ the ones the file is in: picked again, it is taken out), then New group…. */
    static void addTo(final Activity activity, final String key, final String name, final Runnable after) {
        addTo(activity, new String[] {key}, name, after);
    }

    /** As above, for a file known by several keys (a run's file and its copies in the library): all go in, or all out. */
    static void addTo(final Activity activity, final String[] keys, final String name, final Runnable after) {
        FileGroups.load(activity.getFilesDir());
        final String[] rows = FileGroups.menu(keys);
        last = new AlertDialog.Builder(activity)
            .setTitle(FileGroups.ADD + " · " + name)
            .setItems(rows, (d, w) -> {
                String group = FileGroups.groupAt(w);
                if (group != null) {
                    done(activity, name, group, FileGroups.toggle(group, keys), after);
                    return;
                }
                final EditText field = new EditText(activity);
                field.setSingleLine(true);
                field.setHint("Group name");
                field.setTag("group-new-name");
                last = new AlertDialog.Builder(activity)
                    .setTitle(FileGroups.NEW)
                    .setView(field)
                    .setPositiveButton("Add", (d2, w2) -> {
                        String made = FileGroups.clean(field.getText().toString());
                        if (made.isEmpty()) return;
                        done(activity, name, made, FileGroups.toggle(made, keys), after);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static void done(Activity activity, String name, String group, boolean now, Runnable after) {
        if (activity instanceof MainActivity) ((MainActivity) activity).setNow(FileGroups.note(name, group, now));
        if (after != null) after.run();
    }

    /**
     * G held: the groups with how many files each holds ("Trip (5)"); the one picked is what G
     * shows, and `picked` runs. Then New group…, Rename a group… and Delete a group….
     */
    static void pick(final Activity activity, final Runnable picked) {
        FileGroups.load(activity.getFilesDir());
        final List<String> names = FileGroups.names();
        PromptVault vault = null;
        try {
            vault = PromptVault.open(activity.getFilesDir());
        } catch (Exception ignored) {
            // counted without the library's files
        }
        final java.util.List<String> rows = new java.util.ArrayList<String>();
        for (String g : names) rows.add(FileGroups.pickRow(g, vault));
        rows.add(FileGroups.NEW);
        if (!names.isEmpty()) {
            rows.add(FileGroups.RENAME);
            rows.add(FileGroups.DELETE);
        }
        last = new AlertDialog.Builder(activity)
            .setTitle(names.isEmpty() ? "Groups (none yet)" : "Show group")
            .setItems(rows.toArray(new String[0]), (d, w) -> {
                if (w < names.size()) {
                    FileGroups.show(names.get(w));
                    picked.run();
                    return;
                }
                String row = rows.get(w);
                if (FileGroups.NEW.equals(row)) {
                    named(activity, FileGroups.NEW, "", typed -> {
                        if (!FileGroups.make(typed)) say(activity, "There is a group " + FileGroups.clean(typed) + " already");
                        else say(activity, "New group " + FileGroups.clean(typed) + ": add files to it with " + FileGroups.ADD);
                        pick(activity, picked);
                    });
                } else if (FileGroups.RENAME.equals(row)) {
                    last = new AlertDialog.Builder(activity).setTitle(FileGroups.RENAME)
                        .setItems(names.toArray(new String[0]), (d2, w2) -> named(activity, "Rename " + names.get(w2), names.get(w2), typed -> {
                            if (FileGroups.rename(names.get(w2), typed)) say(activity, names.get(w2) + " is " + FileGroups.clean(typed) + " now");
                            else say(activity, "Could not rename " + names.get(w2) + " (the name is empty or taken)");
                            pick(activity, picked);
                        }))
                        .setNegativeButton("Cancel", null).show();
                } else if (FileGroups.DELETE.equals(row)) {
                    last = new AlertDialog.Builder(activity).setTitle(FileGroups.DELETE)
                        .setItems(names.toArray(new String[0]), (d2, w2) -> {
                            last = new AlertDialog.Builder(activity).setTitle("Delete " + names.get(w2) + "?")
                                .setMessage("The group goes; its files stay where they are.")
                                .setPositiveButton("Delete", (d3, w3) -> {
                                    FileGroups.delete(names.get(w2));
                                    say(activity, "Group " + names.get(w2) + " deleted");
                                    picked.run();
                                })
                                .setNegativeButton("Cancel", null).show();
                        })
                        .setNegativeButton("Cancel", null).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** A name asked for (`now` filled in); `done` gets what was typed. */
    private static void named(final Activity activity, String title, String now, final java.util.function.Consumer<String> done) {
        final EditText field = new EditText(activity);
        field.setSingleLine(true);
        field.setText(now);
        field.setHint("Group name");
        field.setTag("group-name-field");
        last = new AlertDialog.Builder(activity).setTitle(title).setView(field)
            .setPositiveButton("OK", (d, w) -> done.accept(field.getText().toString()))
            .setNegativeButton("Cancel", null).show();
    }

    private static void say(Activity activity, String line) {
        if (activity instanceof MainActivity) ((MainActivity) activity).setNow(line);
    }

    /**
     * After Import as Ref / Result file or the Media browser's Add to DB: which group the file goes
     * in (No group, a group, or New group…); nothing picked, no group.
     */
    static void afterAdd(final Activity activity, final String name, final long size) {
        FileGroups.load(activity.getFilesDir());
        final String[] rows = FileGroups.afterAdd();
        final String key = MarkFile.mediaKey(name, size);
        last = new AlertDialog.Builder(activity)
            .setTitle("Group for " + name + "?")
            .setItems(rows, (d, w) -> {
                if (w == 0) return;
                if (w < rows.length - 1) {
                    FileGroups.set(rows[w], key, true);
                    say(activity, FileGroups.note(name, rows[w], true));
                    return;
                }
                named(activity, FileGroups.NEW, "", typed -> {
                    String g = FileGroups.clean(typed);
                    if (g.isEmpty()) return;
                    FileGroups.set(g, key, true);
                    say(activity, FileGroups.note(name, g, true));
                });
            })
            .setNegativeButton("No group", null)
            .show();
    }

    /** A tap on G: the group picked before, or the list when none is. True when G shows a group now. */
    static boolean tap(Activity activity, Runnable picked) {
        return tap(activity, picked, false);
    }

    /** As above; `on`: G shows a group already, so a tap lists the groups to pick another. */
    static boolean tap(Activity activity, Runnable picked, boolean on) {
        FileGroups.load(activity.getFilesDir());
        if (on || FileGroups.shown.isEmpty()) {
            pick(activity, picked);
            return false;
        }
        return true;
    }
}
