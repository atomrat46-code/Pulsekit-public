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
        final String key = keys[0];
        FileGroups.load(activity.getFilesDir());
        final String[] rows = FileGroups.menu(key);
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

    /** G held: the groups; the one picked is what G shows, and `picked` runs. */
    static void pick(final Activity activity, final Runnable picked) {
        FileGroups.load(activity.getFilesDir());
        final List<String> names = FileGroups.names();
        if (names.isEmpty()) {
            last = new AlertDialog.Builder(activity).setTitle("Groups")
                .setMessage("No groups yet. Long press a thumbnail and pick " + FileGroups.ADD + ".")
                .setPositiveButton("OK", null).show();
            return;
        }
        String[] rows = new String[names.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = names.get(i) + (names.get(i).equals(FileGroups.shown) ? " ✓" : "");
        last = new AlertDialog.Builder(activity)
            .setTitle("Show group")
            .setItems(rows, (d, w) -> {
                FileGroups.show(names.get(w));
                picked.run();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** A tap on G: the group picked before, or the list when none is. True when G shows a group now. */
    static boolean tap(Activity activity, Runnable picked) {
        FileGroups.load(activity.getFilesDir());
        if (FileGroups.shown.isEmpty()) {
            pick(activity, picked);
            return false;
        }
        return true;
    }
}
