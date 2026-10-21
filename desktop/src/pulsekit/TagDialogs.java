package pulsekit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Tags… and Category… for a file by its name and size (MediaTags), as the Media browser has them:
 * also on the Reference, Result and Prompt files' menus and Browse DB's. MediaBrowser.answer, when
 * set (the tests), answers in place of the dialog.
 */
final class TagDialogs {
    private TagDialogs() {}

    /** Tags…: the file's tags, picked from those made so far, and new ones typed (a comma between). `after` runs once saved. */
    static void tags(Pulsekit app, Component parent, String name, long size, Runnable after) {
        tags(app, parent, name, size, after, null);
    }

    /**
     * As above; for Picture ready and Video ready (`ready`: ReadyRecent.IMAGE or VIDEO) Select all
     * (Clear all when every tag is ticked) above the tags, and a file without tags has the tags used
     * last for that kind ticked.
     */
    static void tags(Pulsekit app, Component parent, String name, long size, Runnable after, String ready) {
        MediaTags.load(PromptDb.dir());
        ReadyRecent.load(PromptDb.dir());
        String key = MediaTags.key(name, size);
        List<String> chosen;
        if (MediaBrowser.answer != null) {
            String a = MediaBrowser.answer.apply("Tags for " + name);
            if (a == null) return;
            chosen = new ArrayList<String>(java.util.Arrays.asList(a.split(",")));
        } else {
            List<String> known = MediaTags.known();
            List<String> has = ReadyRecent.ticked(ready, MediaTags.tags(key), known);
            JPanel boxes = new JPanel(new GridLayout(0, 3, 6, 2));
            List<javax.swing.JCheckBox> checks = new ArrayList<javax.swing.JCheckBox>();
            for (String t : known) {
                javax.swing.JCheckBox c = new javax.swing.JCheckBox(t, has.contains(t));
                c.setName("media-tag:" + t);
                checks.add(c);
                boxes.add(c);
            }
            javax.swing.JTextField more = new javax.swing.JTextField(24);
            more.setName("media-new-tags");
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            if (!known.isEmpty()) panel.add(boxes, BorderLayout.CENTER);
            if (ready != null && known.size() > 1) panel.add(allButton(checks), BorderLayout.NORTH);
            JPanel add = new JPanel(new BorderLayout(6, 0));
            add.add(new JLabel("New tags (a comma between):"), BorderLayout.WEST);
            add.add(more, BorderLayout.CENTER);
            panel.add(add, BorderLayout.SOUTH);
            if (javax.swing.JOptionPane.showConfirmDialog(parent, panel, "Tags for " + name, javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE) != javax.swing.JOptionPane.OK_OPTION) return;
            chosen = new ArrayList<String>();
            for (javax.swing.JCheckBox c : checks) if (c.isSelected()) chosen.add(c.getText());
            chosen.addAll(java.util.Arrays.asList(more.getText().split(",")));
        }
        MediaTags.setTags(key, chosen);
        ReadyRecent.setTags(ready, MediaTags.tags(key));
        String line = MediaTags.line(key);
        app.setNow(line.length() == 0 ? name + ": no tags" : name + ": " + line);
        if (after != null) after.run();
    }

    /** Use last tags: the tags used last for `ready`'s kind put on the file at once. */
    static void useLast(Pulsekit app, String name, long size, String ready) {
        MediaTags.load(PromptDb.dir());
        ReadyRecent.load(PromptDb.dir());
        List<String> now = ReadyRecent.useLast(ready, MediaTags.key(name, size));
        app.setNow(now == null ? "No tags used yet for this kind of file" : name + ": " + String.join(", ", now));
    }

    /** Use all recent choices on a run's saved file. */
    static void useAll(Pulsekit app, java.io.File file, String ready) {
        MediaTags.load(PromptDb.dir());
        ReadyRecent.load(PromptDb.dir());
        app.setNow(ReadyRecent.useAll(PromptDb.dir(), ready, MediaTags.key(file.getName(), file.length()), GroupMenus.keys(file), file.getAbsolutePath(), file.getName()));
    }

    /** The ready screens' items with Use last tags: … after Add tag… when tags were used for `ready`'s kind. */
    static List<String> withUseLast(List<String> items, String ready) {
        ReadyRecent.load(PromptDb.dir());
        String label = ReadyRecent.useLastLabel(ready);
        List<String> out = new ArrayList<String>(items);
        int at = out.indexOf(MediaTags.ADD_TAG);
        if (label != null) out.add(at < 0 ? out.size() : ++at, label);
        // Use all recent choices: tags, category, group and playlist as chosen last for this kind.
        FileGroups.load(PromptDb.dir());
        String all = ReadyRecent.useAllLabel(PromptDb.dir(), ready);
        if (all != null) out.add(at < 0 ? out.size() : at + 1, all);
        return out;
    }

    /** Category…: one of the prompt library's categories for the file, or none. `after` runs once picked. */
    static void category(Pulsekit app, Component parent, String name, long size, Runnable after) {
        category(app, parent, name, size, after, null);
    }

    /** As above; for Picture ready and Video ready (`ready`) the category used last for that kind is picked first. */
    static void category(Pulsekit app, Component parent, String name, long size, Runnable after, String ready) {
        MediaTags.load(PromptDb.dir());
        ReadyRecent.load(PromptDb.dir());
        String key = MediaTags.key(name, size);
        PromptVault vault;
        List<String> names;
        try {
            vault = PromptDb.vault();
            names = MediaTags.categoryRows(vault);
        } catch (Exception ex) {
            // no library: none only
            vault = null;
            names = new ArrayList<String>();
            names.add("(none)");
        }
        String picked;
        final String[] held = new String[1];
        if (MediaBrowser.answer != null) {
            picked = MediaBrowser.answer.apply("Category for " + name);
        } else {
            String now = ReadyRecent.picked(ready, MediaTags.category(key));
            // A list: a right click on a category adds a subcategory under it.
            final javax.swing.JList<String> list = new javax.swing.JList<String>(names.toArray(new String[0]));
            list.setName("category-list");
            list.setSelectedValue(now.length() == 0 ? names.get(0) : now, true);
            list.setVisibleRowCount(Math.min(14, names.size()));
            final List<String> rows = names;
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (!javax.swing.SwingUtilities.isRightMouseButton(e)) return;
                    int at = list.locationToIndex(e.getPoint());
                    if (at <= 0 || rows.get(at).startsWith("+ ")) return;
                    held[0] = rows.get(at);
                    javax.swing.SwingUtilities.getWindowAncestor(list).dispose();
                }
            });
            JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.add(new javax.swing.JScrollPane(list), BorderLayout.CENTER);
            panel.add(new JLabel("Right click a category: a new subcategory under it, or Delete category"), BorderLayout.SOUTH);
            int ans = javax.swing.JOptionPane.showConfirmDialog(parent, panel, "Category for " + name, javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
            picked = held[0] == null && ans == javax.swing.JOptionPane.OK_OPTION ? list.getSelectedValue() : null;
        }
        if (vault != null && held[0] != null) {
            // Right click on a category: a new subcategory under it, or Delete category (not the library's own).
            Object[] rows = MediaTags.isOriginal(held[0]) ? new Object[] {MediaTags.HELD_NEW_SUB, "Cancel"} : new Object[] {MediaTags.HELD_NEW_SUB, MediaTags.DELETE_CATEGORY, "Cancel"};
            int what = javax.swing.JOptionPane.showOptionDialog(parent, held[0], "Category", javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE, null, rows, rows[0]);
            if (what == 0) subcategory(app, parent, vault, held[0]);
            else if (what == 1 && rows.length == 3) {
                Object[] yes = {"Delete", "Cancel"};
                if (javax.swing.JOptionPane.showOptionDialog(parent, MediaTags.deleteQuestion(vault, held[0]), MediaTags.DELETE_CATEGORY, javax.swing.JOptionPane.DEFAULT_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE, null, yes, yes[1]) == 0) {
                    try {
                        app.setNow(MediaTags.deleteCategory(vault, held[0]));
                    } catch (Exception ex) {
                        app.setNow(ex.getMessage());
                    }
                }
            }
            category(app, parent, name, size, after, ready);
            return;
        }
        if (picked == null) return;
        try {
            if (vault != null && MediaTags.NEW_CATEGORY.equals(picked)) {
                String n = named(parent, "New category");
                if (n != null) MediaTags.addCategory(vault, null, n);
                category(app, parent, name, size, after, ready);
                return;
            }
            if (vault != null && MediaTags.NEW_SUBCATEGORY.equals(picked)) {
                List<String> parents = MediaTags.categoryNames(vault);
                if (parents.isEmpty()) {
                    app.setNow("Make a category first (" + MediaTags.NEW_CATEGORY + ")");
                } else {
                    Object under = MediaBrowser.answer != null ? MediaBrowser.answer.apply("New subcategory under")
                        : javax.swing.JOptionPane.showInputDialog(parent, "New subcategory under", "Category", javax.swing.JOptionPane.PLAIN_MESSAGE, null,
                            parents.toArray(new String[0]), parents.get(0));
                    if (under != null) subcategory(app, parent, vault, under.toString());
                }
                category(app, parent, name, size, after, ready);
                return;
            }
            if (vault != null && MediaTags.INTO_NEW.equals(picked)) {
                String n = named(parent, "Add into new category");
                if (n == null) return;
                picked = MediaTags.addCategory(vault, null, n);
            }
        } catch (Exception ex) {
            app.setNow(ex.getMessage() == null ? "Could not add the category" : ex.getMessage());
            return;
        }
        MediaTags.setCategory(key, picked.equals("(none)") ? "" : picked);
        if (!picked.equals("(none)")) ReadyRecent.setCategory(ready, picked);
        app.setNow(name + (picked.equals("(none)") ? ": no category" : " is in " + picked));
        if (after != null) after.run();
    }

    /** A new subcategory under `parentPath` (its name asked); true when one was made. */
    private static boolean subcategory(Pulsekit app, Component parent, PromptVault vault, String parentPath) {
        String n = named(parent, "New subcategory under " + parentPath);
        if (n == null) return true;
        try {
            MediaTags.addCategory(vault, parentPath, n);
        } catch (Exception ex) {
            app.setNow(ex.getMessage() == null ? "Could not add " + n : ex.getMessage());
        }
        return true;
    }

    /** A name asked for; null when none was given. */
    private static String named(Component parent, String title) {
        Object typed = MediaBrowser.answer != null ? MediaBrowser.answer.apply(title)
            : javax.swing.JOptionPane.showInputDialog(parent, "Name", title, javax.swing.JOptionPane.PLAIN_MESSAGE, null, null, "");
        String n = typed == null ? "" : typed.toString().trim();
        return n.length() == 0 ? null : n;
    }

    /** Select all, Clear all when every tag is ticked; the label follows the boxes. */
    static javax.swing.JButton allButton(final List<javax.swing.JCheckBox> checks) {
        final javax.swing.JButton all = new javax.swing.JButton();
        all.setName("media-tag-all");
        final Runnable label = () -> {
            boolean[] on = new boolean[checks.size()];
            for (int i = 0; i < on.length; i++) on[i] = checks.get(i).isSelected();
            all.setText(ReadyRecent.allLabel(on));
        };
        label.run();
        all.addActionListener(e -> {
            boolean to = !ReadyRecent.CLEAR_ALL.equals(all.getText());
            for (javax.swing.JCheckBox c : checks) c.setSelected(to);
            label.run();
        });
        for (javax.swing.JCheckBox c : checks) c.addActionListener(e -> label.run());
        return all;
    }
}
