package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * File > Collections (and Collections.java on PyJav's Java menu), as on the phone: the collections,
 * each a tree of containers holding links to files, DB files, playlists and file sets. A file not
 * found shows missing (Relocate folder… finds it by name in a folder picked); a DB file gone shows
 * deleted. Add to Collection on the menus puts a link in.
 */
final class CollectionsWindow {
    private CollectionsWindow() {}

    /** The window shown last (for the tests). */
    static JDialog last;
    static MediaCollections.Collection shownCollection;
    static MediaCollections.Container shownContainer;
    /** The tests: names typed (New collection, New container, Rename), the collection and container picked by Add to Collection, the folder Relocate picks. */
    static final java.util.ArrayDeque<String> typed = new java.util.ArrayDeque<String>();
    static Integer pickCollection, pickContainer;
    static File relocateTo;
    /** What Add to Collection said last. */
    static String lastSaid;

    static boolean load() {
        try {
            MediaCollections.load(PromptDb.vault());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    static void save(Pulsekit app) {
        try {
            MediaCollections.save(PromptDb.vault());
        } catch (Exception ex) {
            app.setNow("Could not keep the collections: " + ex.getMessage());
        }
    }

    static MediaCollections.Checker checker() {
        PromptVault v;
        try {
            v = PromptDb.vault();
        } catch (Exception ex) {
            v = null;
        }
        final PromptVault vault = v;
        return e -> {
            if (MediaCollections.KIND_PLAYLIST.equals(e.kind)) return Playlist.names(PromptDb.dir()).contains(e.ref);
            if (MediaCollections.KIND_FILESET.equals(e.kind)) return true;
            if (MediaCollections.KIND_DB.equals(e.kind) || Playlist.isDb(e.ref)) {
                long[] at = Playlist.dbPlace(e.ref);
                return at != null && vault != null && vault.hasFile(at[1], (int) at[0]);
            }
            return e.ref.length() > 0 && new File(e.ref).isFile();
        };
    }

    static String ask(java.awt.Component at, String title, String now) {
        if (!typed.isEmpty()) return typed.poll();
        return (String) JOptionPane.showInputDialog(at, "Name", title, JOptionPane.PLAIN_MESSAGE, null, null, now);
    }

    static void show(Pulsekit app) {
        show(app, null);
    }

    /** The collections; with `name`, that collection open. */
    static void show(final Pulsekit app, String name) {
        if (!load()) {
            app.setNow("Could not open the DB");
            return;
        }
        MediaCollections.Collection named = name == null ? null : MediaCollections.named(name);
        if (named != null) {
            open(app, named, named.top);
            return;
        }
        final List<MediaCollections.Collection> all = MediaCollections.all();
        final MediaCollections.Checker check = checker();
        DefaultListModel<String> model = new DefaultListModel<String>();
        for (MediaCollections.Collection c : all) {
            int lost = MediaCollections.lost(c.top, check);
            model.addElement(c.name() + "  ·  " + c.top.total() + (c.top.total() == 1 ? " link" : " links") + (c.top.kids.isEmpty() ? "" : ", " + c.top.kids.size() + (c.top.kids.size() == 1 ? " container" : " containers"))
                + (lost > 0 ? ", " + lost + " missing or deleted" : ""));
        }
        final JList<String> list = new JList<String>(model);
        list.setName("collections-list");
        if (!all.isEmpty()) list.setSelectedIndex(0);
        final JDialog d = dialog(app, MediaCollections.TITLE);
        Runnable openIt = () -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            d.dispose();
            open(app, all.get(i), all.get(i).top);
        };
        list.addMouseListener(doubleClick(openIt));
        JButton open = button("Open", "collections-open", e -> openIt.run());
        JButton fresh = button("New collection…", "collections-new", e -> {
            String n = ask(d, "New collection", "");
            if (n == null) return;
            MediaCollections.Collection c = MediaCollections.add(n);
            save(app);
            d.dispose();
            open(app, c, c.top);
        });
        JButton rename = button("Rename…", "collections-rename", e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            String n = ask(d, "Rename", all.get(i).name());
            if (n == null) return;
            MediaCollections.rename(all.get(i), n);
            save(app);
            d.dispose();
            show(app);
        });
        JButton delete = button("Delete…", "collections-delete", e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            if (JOptionPane.showConfirmDialog(d, "Delete the collection " + all.get(i).name() + "? Its links go; the files, DB files and playlists stay.", "Delete",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            MediaCollections.remove(all.get(i));
            save(app);
            d.dispose();
            show(app);
        });
        finish(app, d, all.isEmpty() ? new javax.swing.JLabel("No collections yet: New collection, or Add to Collection on a thumbnail's menu, a playlist or a file set.") : new JScrollPane(list),
            open, fresh, rename, delete);
    }

    /** A container: its subcontainers, then its links. */
    static void open(final Pulsekit app, final MediaCollections.Collection c, final MediaCollections.Container k) {
        shownCollection = c;
        shownContainer = k;
        final MediaCollections.Checker check = checker();
        final List<Object> rows = new ArrayList<Object>();
        DefaultListModel<String> model = new DefaultListModel<String>();
        for (MediaCollections.Container kid : k.kids) {
            rows.add(kid);
            model.addElement("▤ " + kid.name + "  (" + kid.total() + ")");
        }
        for (MediaCollections.Entry e : k.entries) {
            rows.add(e);
            String state = MediaCollections.status(e, check);
            model.addElement(e.name + "  ·  " + e.kindLabel() + (state.length() > 0 ? "  ·  " + state : ""));
        }
        final JList<String> list = new JList<String>(model);
        list.setName("collections-items");
        if (!rows.isEmpty()) list.setSelectedIndex(0);
        final JDialog d = dialog(app, MediaCollections.path(c, k));
        final Runnable openIt = () -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            Object row = rows.get(i);
            if (row instanceof MediaCollections.Container) {
                d.dispose();
                open(app, c, (MediaCollections.Container) row);
            } else openEntry(app, d, (MediaCollections.Entry) row);
        };
        list.addMouseListener(doubleClick(openIt));
        JButton up = button(k == c.top ? "All collections" : "Up", "collections-up", e -> {
            d.dispose();
            MediaCollections.Container p = MediaCollections.parentOf(c.top, k);
            if (p == null) show(app);
            else open(app, c, p);
        });
        JButton open = button("Open", "collections-open", e -> openIt.run());
        JButton add = button("New container…", "collections-new-container", e -> {
            String n = ask(d, "New container", "");
            if (n == null) return;
            MediaCollections.Container made = MediaCollections.addContainer(k, n);
            save(app);
            d.dispose();
            open(app, c, made);
        });
        JButton rename = button("Rename…", "collections-rename", e -> {
            int i = list.getSelectedIndex();
            if (i < 0 || !(rows.get(i) instanceof MediaCollections.Container)) return;
            MediaCollections.Container kid = (MediaCollections.Container) rows.get(i);
            String n = ask(d, "Rename", kid.name);
            if (n == null || n.trim().length() == 0) return;
            kid.name = n.trim();
            save(app);
            d.dispose();
            open(app, c, k);
        });
        JButton remove = button("Remove…", "collections-remove", e -> {
            int i = list.getSelectedIndex();
            if (i < 0) return;
            Object row = rows.get(i);
            String what = row instanceof MediaCollections.Container ? "the container " + ((MediaCollections.Container) row).name + " and what is in it" : ((MediaCollections.Entry) row).name;
            if (JOptionPane.showConfirmDialog(d, "Take " + what + " out of the collection? The files stay.", "Remove", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            if (row instanceof MediaCollections.Container) MediaCollections.removeContainer(c.top, (MediaCollections.Container) row);
            else k.entries.remove(row);
            save(app);
            d.dispose();
            open(app, c, k);
        });
        JButton relocate = button("Relocate folder…", "collections-relocate", e -> {
            File dir = relocateTo;
            if (dir == null) {
                javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("The folder the missing files are in now");
                if (chooser.showOpenDialog(d) != javax.swing.JFileChooser.APPROVE_OPTION) return;
                dir = chooser.getSelectedFile();
            }
            int n = relocateFolder(app, c, dir);
            d.dispose();
            open(app, c, k);
            app.setNow(n == 0 ? "None of the missing files is in that folder" : "Found " + n + (n == 1 ? " missing file" : " missing files") + " there");
        });
        finish(app, d, rows.isEmpty() ? new javax.swing.JLabel("Empty: New container, or Add to Collection on a thumbnail's menu, a playlist or a file set.") : new JScrollPane(list),
            up, open, add, rename, remove, relocate);
    }

    static int relocateFolder(Pulsekit app, MediaCollections.Collection c, File dir) {
        java.util.Map<String, String> found = new java.util.HashMap<String, String>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) found.put(f.getName().toLowerCase(java.util.Locale.ROOT), f.getAbsolutePath());
        int n = MediaCollections.relocate(c.top, found, checker());
        save(app);
        return n;
    }

    /** Open: a playlist in the Playlist window, a file set's Info, a picture or video in its ready window, else the system's viewer. */
    static void openEntry(Pulsekit app, JDialog from, MediaCollections.Entry e) {
        if (MediaCollections.status(e, checker()).length() > 0) {
            app.setNow(e.name + " is " + MediaCollections.status(e, checker()));
            return;
        }
        if (MediaCollections.KIND_PLAYLIST.equals(e.kind)) {
            from.dispose();
            PlaylistWindow.open(app, e.ref);
            return;
        }
        if (MediaCollections.KIND_FILESET.equals(e.kind)) {
            from.dispose();
            app.fileSets.openFileSetInfo(e.ref, e.name);
            return;
        }
        try {
            File f;
            if (Playlist.isDb(e.ref)) {
                byte[] data = Playlist.dbBytes(PromptDb.vault(), e.ref);
                File dir = new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-collection");
                dir.mkdirs();
                f = new File(dir, e.name.replaceAll("[\\\\/:*?\"<>|]", "_"));
                java.nio.file.Files.write(f.toPath(), data);
            } else f = new File(e.ref);
            int kind = MediaDir.kind(e.name);
            if (kind == MediaDir.PICTURE) app.pyJav.offerPictures(java.util.Collections.singletonList(f));
            else if (kind == MediaDir.VIDEO) app.pyJav.offerVideo(f, 1);
            else java.awt.Desktop.getDesktop().open(f);
            app.setNow(e.name);
        } catch (Exception ex) {
            app.setNow("Could not open " + e.name + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
    }

    // ------------------------------------------------------------ Add to Collection

    /** A menu item: Add to Collection for this file, DB file, playlist or file set. */
    static javax.swing.JMenuItem addItem(final Pulsekit app, final java.awt.Component at, final String kind, final String ref, final String name, final long size) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(MediaCollections.ADD);
        item.setName("add-to-collection");
        item.addActionListener(e -> addTo(app, at, kind, ref, name, size));
        return item;
    }

    /** Add to Collection: which collection (or a new one), then which container; the link kept there. */
    static void addTo(Pulsekit app, java.awt.Component at, String kind, String ref, String name, long size) {
        if (!load()) {
            app.setNow("Could not open the DB");
            return;
        }
        List<MediaCollections.Collection> all = MediaCollections.all();
        String[] choices = new String[all.size() + 1];
        for (int i = 0; i < all.size(); i++) choices[i] = all.get(i).name();
        choices[all.size()] = "New collection…";
        int w = pickCollection != null ? pickCollection : indexOf(choices, JOptionPane.showInputDialog(at, "Into which collection?", MediaCollections.ADD + " · " + name,
            JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]));
        if (w < 0) return;
        MediaCollections.Collection c;
        if (w == all.size()) {
            String n = ask(at, "New collection", "");
            if (n == null) return;
            c = MediaCollections.add(n);
        } else c = all.get(w);
        List<MediaCollections.Container> where = MediaCollections.containers(c);
        MediaCollections.Container k = c.top;
        if (where.size() > 1) {
            String[] rows = new String[where.size() + 1];
            for (int i = 0; i < where.size(); i++) rows[i] = MediaCollections.path(c, where.get(i));
            rows[where.size()] = "New container…";
            int kw = pickContainer != null ? pickContainer : indexOf(rows, JOptionPane.showInputDialog(at, "Into which container?", MediaCollections.ADD,
                JOptionPane.PLAIN_MESSAGE, null, rows, rows[0]));
            if (kw < 0) return;
            if (kw == where.size()) {
                String n = ask(at, "New container in " + c.name(), "");
                if (n == null) return;
                k = MediaCollections.addContainer(c.top, n);
            } else k = where.get(kw);
        }
        boolean added = MediaCollections.addEntry(k, kind, ref, name, size);
        save(app);
        lastSaid = name + (added ? " added to " : " is in ") + MediaCollections.path(c, k) + (added ? "" : " already");
        app.setNow(lastSaid);
    }

    static int indexOf(String[] rows, Object picked) {
        for (int i = 0; i < rows.length; i++) if (rows[i].equals(picked)) return i;
        return -1;
    }

    // ------------------------------------------------------------ the window

    static JDialog dialog(Pulsekit app, String title) {
        if (last != null) last.dispose();
        JDialog d = new JDialog(app, title, true);
        d.setName("collections-window");
        last = d;
        return d;
    }

    static JButton button(String text, String name, java.awt.event.ActionListener does) {
        JButton b = new JButton(text);
        b.setName(name);
        b.addActionListener(does);
        return b;
    }

    static java.awt.event.MouseAdapter doubleClick(final Runnable then) {
        return new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) then.run();
            }
        };
    }

    static void finish(Pulsekit app, final JDialog d, java.awt.Component body, JButton... buttons) {
        JPanel page = new JPanel(new BorderLayout(0, 8));
        page.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        page.add(body, BorderLayout.CENTER);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JButton b : buttons) row.add(b);
        JButton close = button("Close", "collections-close", e -> d.dispose());
        row.add(close);
        page.add(row, BorderLayout.SOUTH);
        d.setContentPane(page);
        d.setSize(720, 460);
        d.setLocationRelativeTo(app);
        d.setVisible(true);
    }
}
