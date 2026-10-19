package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * F, G and C after the type letters (switches that go with the letter: V with F, the favourite
 * videos), and the T1 T2 T3 tag lists, for Browse DB, the Reference / Result / Prompt file
 * galleries and the Media browser (FileFilter). G held: the groups; C held: the categories under
 * the main category of the type letter (I Image, V Video, S Music), an item held opens its
 * subcategories.
 */
final class FilterRow {
    private FilterRow() {}

    /** A screen's own pill: its text, lit when `on`. */
    interface Pill {
        TextView make(String text, boolean on);
    }

    /** The dialog shown last (for the tests). */
    static AlertDialog last;

    /** F, G and C into `row`, tagged prefix + letter; `again` shows the screen anew; `type` is its type letter. */
    static void toggles(final Activity activity, LinearLayout row, String prefix, final String type, Pill pill,
                        java.util.function.Supplier<LinearLayout.LayoutParams> lp, final Runnable again) {
        FileGroups.load(activity.getFilesDir());
        FileFavourites.load(activity.getFilesDir());
        MediaTags.load(activity.getFilesDir());
        // This screen's switches (each screen has its own): in use again whatever was shown since.
        final String screen = FileFilter.using();
        for (final String t : new String[] {FileFilter.FAV, FileFilter.GROUP, FileFilter.CAT}) {
            TextView b = pill.make(t, FileFilter.on(t) && (!FileFilter.GROUP.equals(t) || !FileGroups.shown.isEmpty())
                && (!FileFilter.CAT.equals(t) || FileFilter.category.length() > 0));
            b.setTag(prefix + t);
            b.setContentDescription(FileFilter.label(t));
            final Runnable groupOn = () -> {
                FileFilter.use(screen);
                FileFilter.group = true;
                again.run();
            };
            b.setOnClickListener(v -> {
                FileFilter.use(screen);
                if (FileFilter.FAV.equals(t)) {
                    FileFilter.fav = !FileFilter.fav;
                } else if (FileFilter.GROUP.equals(t)) {
                    // Off: the group picked before (the list when there is none).
                    if (FileFilter.group && !FileGroups.shown.isEmpty()) FileFilter.group = false;
                    else if (!GroupDialogs.tap(activity, groupOn)) return;
                    else FileFilter.group = true;
                } else {
                    if (FileFilter.cat && FileFilter.category.length() > 0) FileFilter.cat = false;
                    else if (FileFilter.category.length() == 0) {
                        categories(activity, type, again);
                        return;
                    } else FileFilter.cat = true;
                }
                again.run();
            });
            if (FileFilter.GROUP.equals(t)) b.setOnLongClickListener(v -> {
                GroupDialogs.pick(activity, groupOn);
                return true;
            });
            if (FileFilter.CAT.equals(t)) b.setOnLongClickListener(v -> {
                FileFilter.use(screen);
                categories(activity, type, again);
                return true;
            });
            row.addView(b, lp.get());
        }
    }

    /** C held: the categories under the type letter's main category (all the main ones for A). */
    static void categories(Activity activity, String type, Runnable again) {
        PromptVault vault;
        try {
            vault = PromptVault.open(activity.getFilesDir());
        } catch (Exception ex) {
            vault = null;
        }
        PromptVault.Category main = FileFilter.main(vault, type);
        level(activity, vault, main == null ? 0 : main.id, again);
    }

    /** One level of the categories: tapped, the one shown; held, its subcategories. */
    static void level(final Activity activity, final PromptVault vault, final long parentId, final Runnable again) {
        final List<PromptVault.Category> under = FileFilter.level(vault, parentId);
        final List<String> rows = new ArrayList<String>();
        final String here = parentId == 0 || vault == null ? "" : vault.pathName(parentId);
        rows.add(FileFilter.NO_CATEGORY);
        if (here.length() > 0) rows.add(FileFilter.allOf(here) + mark(here));
        for (PromptVault.Category c : under) {
            String path = vault.pathName(c.id);
            rows.add(c.name + (vault.children(c.id).isEmpty() ? "" : " ▸") + mark(path));
        }
        if (parentId != 0) rows.add(FileFilter.UP);
        final int first = here.length() > 0 ? 2 : 1;
        final String screen = FileFilter.using();
        last = new AlertDialog.Builder(activity)
            .setTitle(here.length() == 0 ? "Show category" : here)
            .setItems(rows.toArray(new String[0]), (d, w) -> {
                FileFilter.use(screen);
                if (w == 0) {
                    FileFilter.pickCategory("");
                } else if (w < first) {
                    FileFilter.pickCategory(here);
                } else if (w - first < under.size()) {
                    FileFilter.pickCategory(vault.pathName(under.get(w - first).id));
                } else {
                    PromptVault.Category self = null;
                    for (PromptVault.Category c : vault.categories()) if (c.id == parentId) self = c;
                    level(activity, vault, self == null ? 0 : self.parentId, again);
                    return;
                }
                again.run();
            })
            .setNegativeButton("Cancel", null)
            .show();
        final AlertDialog shown = last;
        // Held: a category's subcategories (one without any is picked).
        shown.getListView().setOnItemLongClickListener((parent, view, w, id) -> {
            if (w < first || w - first >= under.size()) return false;
            PromptVault.Category c = under.get(w - first);
            shown.dismiss();
            FileFilter.use(screen);
            if (vault.children(c.id).isEmpty()) {
                FileFilter.pickCategory(vault.pathName(c.id));
                again.run();
            } else {
                level(activity, vault, c.id, again);
            }
            return true;
        });
    }

    private static String mark(String path) {
        return FileFilter.cat && path.equalsIgnoreCase(FileFilter.category) ? " ✓" : "";
    }

    /** T1 T2 T3: each a list, All and every tag; a file shows when it has any tag picked. */
    static LinearLayout tags(final Activity activity, String prefix, Pill pill, java.util.function.Supplier<LinearLayout.LayoutParams> lp, final Runnable again) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final String screen = FileFilter.using();
        for (int i = 0; i < FileFilter.tags.length; i++) {
            final int at = i;
            TextView b = pill.make(FileFilter.tagLabel(i) + " ▾", FileFilter.tags[i].length() > 0);
            b.setTag(prefix + "tag:" + (i + 1));
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setOnClickListener(v -> {
                PromptVault vault;
                try {
                    vault = PromptVault.open(activity.getFilesDir());
                } catch (Exception ex) {
                    vault = null;
                }
                final String[] rows = FileFilter.tagRows(PromptSave.tagChoices(vault, activity.getFilesDir()));
                last = new AlertDialog.Builder(activity).setTitle("Tag " + (at + 1))
                    .setItems(rows, (d, w) -> {
                        FileFilter.use(screen);
                        FileFilter.pickTag(at, rows[w]);
                        again.run();
                    })
                    .setNegativeButton("Cancel", null).show();
            });
            row.addView(b, lp.get());
        }
        return row;
    }
}
