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
        MediaTags.load(app.getFilesDir());
        final String key = MediaTags.key(name, size);
        final List<String> known = MediaTags.known();
        final List<String> has = MediaTags.tags(key);
        final String[] names = known.toArray(new String[0]);
        final boolean[] on = new boolean[names.length];
        for (int i = 0; i < names.length; i++) on[i] = has.contains(names[i]);
        AlertDialog.Builder b = new AlertDialog.Builder(app).setTitle("Tags for " + name);
        if (names.length > 0) b.setMultiChoiceItems(names, on, (d, which, checked) -> on[which] = checked);
        else b.setMessage("No tags yet: add one with New tag.");
        MediaBrowser.lastAsk = b.setPositiveButton("Save", (d, w) -> {
                List<String> chosen = new ArrayList<String>();
                for (int i = 0; i < names.length; i++) if (on[i]) chosen.add(names[i]);
                tagged(app, name, key, chosen, after);
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
                        tagged(app, name, key, chosen, after);
                        // The list again, the new tag ticked.
                        tags(app, name, size, after);
                    })
                    .setNegativeButton("Cancel", null).show();
            })
            .setNegativeButton("Cancel", null).show();
    }

    private static void tagged(MainActivity app, String name, String key, List<String> chosen, Runnable after) {
        MediaTags.setTags(key, chosen);
        String line = MediaTags.line(key);
        app.setNow(line.length() == 0 ? name + ": no tags" : name + ": " + line);
        if (after != null) after.run();
    }

    /** Category…: one of the prompt library's categories for the file, or none (read off the main thread). */
    static void category(final MainActivity app, final String name, final long size, final Runnable after) {
        MediaTags.load(app.getFilesDir());
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
                int now = Math.max(0, names.indexOf(MediaTags.category(key)));
                MediaBrowser.lastAsk = new AlertDialog.Builder(app).setTitle("Category for " + name)
                    .setSingleChoiceItems(names.toArray(new String[0]), now, (d, which) -> {
                        String picked = names.get(which);
                        MediaTags.setCategory(key, which == 0 ? "" : picked);
                        app.setNow(name + (which == 0 ? ": no category" : " is in " + picked));
                        d.dismiss();
                        if (after != null) after.run();
                    })
                    .setNegativeButton("Cancel", null).show();
            });
        }, "pulsekit-file-categories").start();
    }
}
