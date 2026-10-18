package pulsekit;

import java.awt.Component;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

/**
 * Add to group (a thumbnail's right click) and G's group list (a right click on G), for Browse DB,
 * the Reference and Result file galleries and the Media browser, as on the phone.
 */
final class GroupMenus {
    private GroupMenus() {}

    /** The menu shown last (for the tests). */
    static JPopupMenu last;

    /** The groups (✓ the ones the file is in: picked again, it is taken out), then New group…. */
    static JPopupMenu addTo(final Pulsekit app, final String key, final String name, final Runnable after) {
        FileGroups.load(PromptDb.dir());
        JPopupMenu m = new JPopupMenu();
        String[] rows = FileGroups.menu(key);
        for (int i = 0; i < rows.length; i++) {
            final String group = FileGroups.groupAt(i);
            JMenuItem item = new JMenuItem(rows[i]);
            item.setName(group == null ? "group-new" : "group:" + group);
            item.addActionListener(e -> {
                String to = group;
                if (to == null) {
                    Object typed = JOptionPane.showInputDialog(app, "Group name", FileGroups.NEW, JOptionPane.PLAIN_MESSAGE, null, null, "");
                    to = FileGroups.clean(typed == null ? "" : typed.toString());
                    if (to.isEmpty()) return;
                }
                boolean now = FileGroups.toggle(to, key);
                app.setNow(FileGroups.note(name, to, now));
                if (after != null) after.run();
            });
            m.add(item);
        }
        last = m;
        return m;
    }

    /** "Add to group" for a file's menu: opens the group list where the menu was. */
    static JMenuItem item(final Pulsekit app, final Component at, final String key, final String name, final Runnable after) {
        JMenuItem add = new JMenuItem(FileGroups.ADD + "…");
        add.setName("add-to-group");
        add.addActionListener(e -> {
            JPopupMenu m = addTo(app, key, name, after);
            if (at != null && at.isShowing()) m.show(at, 10, 10);
        });
        return add;
    }

    /** G right-clicked: the groups; the one picked is what G shows, and `picked` runs. */
    static void pick(final Pulsekit app, final Component at, final Runnable picked) {
        FileGroups.load(PromptDb.dir());
        final List<String> names = FileGroups.names();
        if (names.isEmpty()) {
            last = null;
            JOptionPane.showMessageDialog(app, "No groups yet. Right click a thumbnail and pick " + FileGroups.ADD + ".", "Groups", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPopupMenu m = new JPopupMenu();
        for (final String g : names) {
            JMenuItem item = new JMenuItem(g + (g.equals(FileGroups.shown) ? " ✓" : ""));
            item.setName("group-show:" + g);
            item.addActionListener(e -> {
                FileGroups.show(g);
                picked.run();
            });
            m.add(item);
        }
        last = m;
        if (at != null && at.isShowing()) m.show(at, 0, at.getHeight());
    }

    /** A click on G: the group picked before, or the list when none is. True when G shows a group now. */
    static boolean tap(Pulsekit app, Component at, Runnable picked) {
        FileGroups.load(PromptDb.dir());
        if (FileGroups.shown.isEmpty()) {
            pick(app, at, picked);
            return false;
        }
        return true;
    }

    /** Right click on a button: `run`. */
    static void onRightClick(Component c, Runnable run) {
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) run.run();
            }
        });
    }
}
