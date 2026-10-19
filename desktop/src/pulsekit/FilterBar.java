package pulsekit;

import java.awt.Container;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * F, G and C after the type letters (switches that go with the letter: V with F, the favourite
 * videos), and the T1 T2 T3 tag lists, for Browse DB, the Reference / Result / Prompt file
 * galleries and the Media browser (FileFilter), as on the phone. A right click on G: the groups;
 * on C: the categories under the main category of the type letter (I Image, V Video, S Music),
 * a category's own submenu holding its subcategories.
 */
final class FilterBar {
    private FilterBar() {}

    /** The category menu shown last (for the tests). */
    static JPopupMenu last;

    /** F, G and C into `into`, named prefix + letter; `again` shows the screen anew; `type` is its type letter. */
    static void toggles(final Pulsekit app, Container into, String prefix, final String type, final Runnable again) {
        FileGroups.load(PromptDb.dir());
        FileFavourites.load(PromptDb.dir());
        MediaTags.load(PromptDb.dir());
        // This screen's switches (each screen has its own): in use again whatever was shown since.
        final String screen = FileFilter.using();
        for (final String t : new String[] {FileFilter.FAV, FileFilter.GROUP, FileFilter.CAT}) {
            boolean on = FileFilter.on(t) && (!FileFilter.GROUP.equals(t) || !FileGroups.shown.isEmpty())
                && (!FileFilter.CAT.equals(t) || FileFilter.category.length() > 0);
            final JToggleButton b = new JToggleButton(t, on);
            b.setName(prefix + t);
            b.setToolTipText(FileFilter.label(t).replace("held", "right click"));
            final Runnable groupOn = () -> {
                FileFilter.use(screen);
                FileFilter.group = true;
                again.run();
            };
            b.addActionListener(e -> {
                FileFilter.use(screen);
                if (FileFilter.FAV.equals(t)) {
                    FileFilter.fav = !FileFilter.fav;
                } else if (FileFilter.GROUP.equals(t)) {
                    // Off: the group picked before (the list when there is none).
                    if (FileFilter.group && !FileGroups.shown.isEmpty()) FileFilter.group = false;
                    else if (!GroupMenus.tap(app, b, groupOn)) {
                        b.setSelected(false);
                        return;
                    } else FileFilter.group = true;
                } else {
                    if (FileFilter.cat && FileFilter.category.length() > 0) FileFilter.cat = false;
                    else if (FileFilter.category.length() == 0) {
                        b.setSelected(false);
                        categories(b, type, again);
                        return;
                    } else FileFilter.cat = true;
                }
                again.run();
            });
            if (FileFilter.GROUP.equals(t)) GroupMenus.onRightClick(b, () -> GroupMenus.pick(app, b, groupOn));
            if (FileFilter.CAT.equals(t)) GroupMenus.onRightClick(b, () -> {
                FileFilter.use(screen);
                categories(b, type, again);
            });
            into.add(b);
        }
    }

    /** C right-clicked: No category filter, the type letter's main category (All of it, its subcategories), the other main ones. */
    static JPopupMenu categories(AbstractButton at, String type, final Runnable again) {
        PromptVault vault;
        try {
            vault = PromptDb.vault();
        } catch (Exception ex) {
            vault = null;
        }
        JPopupMenu m = new JPopupMenu();
        final String screen = FileFilter.using();
        JMenuItem none = new JMenuItem(FileFilter.NO_CATEGORY);
        none.setName("cat-none");
        none.addActionListener(e -> {
            FileFilter.use(screen);
            FileFilter.pickCategory("");
            again.run();
        });
        m.add(none);
        PromptVault.Category main = FileFilter.main(vault, type);
        if (main != null) {
            String path = vault.pathName(main.id);
            m.add(pick(FileFilter.allOf(path), path, again));
            for (PromptVault.Category c : FileFilter.level(vault, main.id)) m.add(node(vault, c, again));
            JMenu others = new JMenu("Other main categories");
            others.setName("cat-mains");
            for (PromptVault.Category c : FileFilter.level(vault, 0)) others.add(node(vault, c, again));
            m.add(others);
        } else {
            for (PromptVault.Category c : FileFilter.level(vault, 0)) m.add(node(vault, c, again));
        }
        last = m;
        if (at != null && at.isShowing()) m.show(at, 0, at.getHeight());
        return m;
    }

    /** A category: one without subcategories is picked; one with them opens them, All of it first. */
    private static JMenuItem node(PromptVault vault, PromptVault.Category c, Runnable again) {
        String path = vault.pathName(c.id);
        List<PromptVault.Category> under = vault.children(c.id);
        if (under.isEmpty()) return pick(c.name, path, again);
        JMenu sub = new JMenu(c.name + mark(path));
        sub.setName("cat-menu:" + path);
        sub.add(pick(FileFilter.allOf(path), path, again));
        for (PromptVault.Category u : under) sub.add(node(vault, u, again));
        return sub;
    }

    private static JMenuItem pick(String text, final String path, final Runnable again) {
        JMenuItem item = new JMenuItem(text + mark(path));
        item.setName("cat-pick:" + path);
        final String screen = FileFilter.using();
        item.addActionListener(e -> {
            FileFilter.use(screen);
            FileFilter.pickCategory(path);
            again.run();
        });
        return item;
    }

    private static String mark(String path) {
        return FileFilter.cat && path.equalsIgnoreCase(FileFilter.category) ? " ✓" : "";
    }

    /** T1 T2 T3 into `into`: each a list, All and every tag; a file shows when it has any tag picked. */
    static void tags(Container into, String prefix, final Runnable again) {
        PromptVault vault;
        try {
            vault = PromptDb.vault();
        } catch (Exception ex) {
            vault = null;
        }
        final String[] rows = FileFilter.tagRows(PromptSave.tagChoices(vault, PromptDb.dir()));
        final String screen = FileFilter.using();
        for (int i = 0; i < FileFilter.tags.length; i++) {
            final int at = i;
            into.add(new javax.swing.JLabel("T" + (i + 1)));
            final JComboBox<String> box = new JComboBox<String>(rows);
            box.setName(prefix + "tag:" + (i + 1));
            if (FileFilter.tags[i].length() > 0 && !java.util.Arrays.asList(rows).contains(FileFilter.tags[i])) box.addItem(FileFilter.tags[i]);
            box.setSelectedItem(FileFilter.tags[i].length() == 0 ? FileFilter.ALL : FileFilter.tags[i]);
            box.setToolTipText("Files with this tag (or the other lists' tags)");
            box.addActionListener(e -> {
                String picked = (String) box.getSelectedItem();
                FileFilter.use(screen);
                String now = FileFilter.tags[at].length() == 0 ? FileFilter.ALL : FileFilter.tags[at];
                if (picked == null || picked.equals(now)) return;
                FileFilter.pickTag(at, picked);
                SwingUtilities.invokeLater(again);
            });
            into.add(box);
        }
    }
}
