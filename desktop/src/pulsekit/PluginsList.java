package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * Params' Plugins list (vst_render's plugin rows): the VST plugins in this computer's VST folders
 * (and where one was picked from), A-Z or Z-A, all of them or the favourites. A plugin's menu (and
 * the buttons): Use it for the row, Add to / Remove from favourites, Info (its file, the kit or patch
 * settings saved for it with Edit), Test plugin (a test beat through it: does it play with these
 * settings? Its WAV opens in WAV ready) and Edit settings (its own window, to load a kit: saved, then
 * tested).
 */
final class PluginsList {
    private PluginsList() {}

    static final String FAVOURITES_KEY = "vstFavourites";
    static final String[] ENDS = {".vst3", ".dll", ".vst", ".component", ".so"};
    /** How deep the VST folders are looked through (makers keep their plugins in folders of their own). */
    static final int DEPTH = 4;

    /** The dialog shown last and the log of its last test (for the tests). */
    static JDialog last;
    static volatile String lastTest;

    /** The plugins in the usual VST folders and in `more` (folders, or plugins whose folder is looked at), by path. */
    static List<File> scan(List<File> more) {
        Set<File> roots = new LinkedHashSet<File>(PyJav.vstFolders());
        for (File f : more) {
            if (f == null) continue;
            File dir = f.isDirectory() && !isPlugin(f) ? f : f.getParentFile();
            if (dir != null && dir.isDirectory()) roots.add(dir);
        }
        Set<File> found = new LinkedHashSet<File>();
        for (File r : roots) walk(r, 0, found);
        return new ArrayList<File>(found);
    }

    private static void walk(File dir, int depth, Set<File> found) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (isPlugin(k)) found.add(k.getAbsoluteFile());
            // A .vst3 (or .vst, .component) bundle is a folder: the plugin, not a folder to look in.
            else if (k.isDirectory() && depth < DEPTH) walk(k, depth + 1, found);
        }
    }

    static boolean isPlugin(File f) {
        String low = f.getName().toLowerCase(java.util.Locale.ROOT);
        for (String e : ENDS) if (low.endsWith(e) && low.length() > e.length()) return true;
        return false;
    }

    /** The plugin's name without its ending: "Anarchy Drums" (vst_render's stem). */
    static String name(File f) {
        String n = f.getName();
        String low = n.toLowerCase(java.util.Locale.ROOT);
        for (String e : ENDS) if (low.endsWith(e)) return n.substring(0, n.length() - e.length());
        return n;
    }

    /** The plugins sorted by name, A-Z or (`down`) Z-A; only the favourites when asked. */
    static List<File> shown(List<File> all, boolean favouritesOnly, boolean down) {
        Set<String> fav = favourites();
        List<File> out = new ArrayList<File>();
        for (File f : all) if (!favouritesOnly || fav.contains(f.getAbsolutePath())) out.add(f);
        Collections.sort(out, (a, b) -> {
            int c = name(a).compareToIgnoreCase(name(b));
            return down ? -c : c;
        });
        return out;
    }

    static Set<String> favourites() {
        String kept = java.util.prefs.Preferences.userNodeForPackage(PyJav.class).get(FAVOURITES_KEY, "");
        Set<String> out = new LinkedHashSet<String>();
        for (String s : kept.split("\n")) if (s.trim().length() > 0) out.add(s.trim());
        return out;
    }

    /** Adds the plugin to the favourites, or takes it off them; true when it is one now. */
    static boolean toggleFavourite(File f) {
        Set<String> fav = favourites();
        String path = f.getAbsolutePath();
        boolean now = !fav.remove(path);
        if (now) fav.add(path);
        java.util.prefs.Preferences node = java.util.prefs.Preferences.userNodeForPackage(PyJav.class);
        node.put(FAVOURITES_KEY, String.join("\n", fav));
        try {
            node.flush();
        } catch (java.util.prefs.BackingStoreException ignored) {
            // kept for this session
        }
        return now;
    }

    /** The settings vst_render saved for the plugin with Edit (~/.pulsekit/vst/<name>.state). */
    static File stateFile(File f) {
        return new File(new File(new File(System.getProperty("user.home"), ".pulsekit"), "vst"), name(f) + ".state");
    }

    /** What Info says: the plugin's kind, file and date, and whether a kit or patch was saved for it. */
    static String info(File f) {
        String low = f.getName().toLowerCase(java.util.Locale.ROOT);
        String kind = low.endsWith(".vst3") ? "VST3" : low.endsWith(".dll") || low.endsWith(".so") || low.endsWith(".vst") ? "VST2" : "Audio Unit";
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringBuilder b = new StringBuilder(name(f)).append(" (").append(kind).append(f.isDirectory() ? ", a bundle folder" : "").append(")\n");
        b.append(f.getAbsolutePath()).append('\n');
        b.append("Changed ").append(day.format(new java.util.Date(f.lastModified())));
        if (favourites().contains(f.getAbsolutePath())) b.append(" · a favourite");
        b.append('\n');
        File state = stateFile(f);
        b.append(state.isFile()
            ? "Settings (kit, patch) saved with Edit on " + day.format(new java.util.Date(state.lastModified())) + ": vst_render plays it with them."
            : "No settings saved with Edit: it plays with its own defaults (a drum plugin may have no kit loaded: Edit settings).");
        return b.toString();
    }

    /** The plugin's line in the list: a star for a favourite. */
    static String line(File f, Set<String> fav) {
        return (fav.contains(f.getAbsolutePath()) ? "★ " : "    ") + name(f);
    }

    /**
     * The list, over `owner`; `current` the row's plugin (picked in the list). Returns the plugin
     * chosen with Use, or null when closed.
     */
    static File show(final PyJav pj, Window owner, String current) {
        final List<File> more = new ArrayList<File>();
        if (current != null && current.length() > 0) more.add(new File(current));
        more.add(PyJav.lastVstFolder());
        final List<File> all = scan(more);
        final JDialog d = new JDialog(owner, "Plugins", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        last = d;
        final File[] chosen = new File[1];
        final DefaultListModel<File> model = new DefaultListModel<File>();
        final JList<File> list = new JList<File>(model);
        list.setName("plugins-list");
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(l, line((File) value, favourites()), index, selected, focus);
            }
        });
        final JToggleButton allTab = new JToggleButton("All", true), favTab = new JToggleButton("Favourites");
        allTab.setName("plugins-all");
        favTab.setName("plugins-favourites");
        ButtonGroup tabs = new ButtonGroup();
        tabs.add(allTab);
        tabs.add(favTab);
        final JComboBox<String> sort = new JComboBox<String>(new String[] {"A–Z", "Z–A"});
        sort.setName("plugins-sort");
        final JTextArea about = new JTextArea(6, 46);
        about.setName("plugins-info");
        about.setEditable(false);
        about.setLineWrap(true);
        about.setWrapStyleWord(true);
        final Runnable fill = () -> {
            File was = list.getSelectedValue();
            model.clear();
            for (File f : shown(all, favTab.isSelected(), sort.getSelectedIndex() == 1)) model.addElement(f);
            File keep = was != null ? was : current != null && current.length() > 0 ? new File(current).getAbsoluteFile() : null;
            if (keep != null) list.setSelectedValue(keep, true);
            if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
            if (model.isEmpty()) about.setText(favTab.isSelected() ? "No favourites yet: Add to favourites on a plugin in All."
                : "No plugins in the VST folders (" + PyJav.vstFolders() + "). Choose plugin finds one anywhere.");
        };
        list.addListSelectionListener(e -> {
            File f = list.getSelectedValue();
            if (f != null && !e.getValueIsAdjusting()) about.setText(info(f));
        });
        allTab.addActionListener(e -> fill.run());
        favTab.addActionListener(e -> fill.run());
        sort.addActionListener(e -> fill.run());
        final JButton use = new JButton("Use"), fav = new JButton("Add to favourites"), test = new JButton("Test plugin"), edit = new JButton("Edit settings"), close = new JButton("Close");
        use.setName("plugins-use");
        fav.setName("plugins-fav");
        test.setName("plugins-test");
        edit.setName("plugins-edit");
        list.addListSelectionListener(e -> {
            File f = list.getSelectedValue();
            fav.setText(f != null && favourites().contains(f.getAbsolutePath()) ? "Remove from favourites" : "Add to favourites");
        });
        final Runnable useIt = () -> {
            chosen[0] = list.getSelectedValue();
            if (chosen[0] != null) d.dispose();
        };
        final Runnable favIt = () -> {
            File f = list.getSelectedValue();
            if (f == null) return;
            boolean now = toggleFavourite(f);
            fav.setText(now ? "Remove from favourites" : "Add to favourites");
            fill.run();
            list.repaint();
        };
        final JButton[] busy = {use, fav, test, edit};
        final java.util.function.Consumer<Boolean> run = editFirst -> {
            File f = list.getSelectedValue();
            if (f == null) return;
            for (JButton b : busy) b.setEnabled(false);
            about.setText((editFirst ? "Edit settings: " + name(f) + "'s window opens (Windows and Mac). Load a kit or patch, then close it: "
                + "the settings are saved and a test beat plays through it.\n" : "Testing " + name(f) + ": a test beat (kick, snare, hats) through it…\n"));
            new Thread(() -> {
                String src = ProgramFiles.bundledSource("vst_render.py");
                List<String> argv = new ArrayList<String>(Arrays.asList("--plugin", f.getAbsolutePath(), "--check"));
                if (editFirst) argv.add("--edit");
                PythonRun.Result r = src == null ? null : PythonRun.run(src, "vst_render.py", argv);
                String log = r == null ? "vst_render.py is not bundled." : r.log;
                lastTest = log;
                PythonRun.FileOut wav = null;
                if (r != null) for (PythonRun.FileOut o : r.files) if (o.name.startsWith("vst-check-") && o.name.endsWith(".wav")) wav = o;
                final PythonRun.FileOut beat = wav;
                SwingUtilities.invokeLater(() -> {
                    for (JButton b : busy) b.setEnabled(true);
                    about.setText(summary(log) + "\n\n" + info(f));
                    // The test beat: WAV ready (Play, favourites, group, tags, category, playlist).
                    if (beat != null && pj != null) {
                        File saved = pj.saveProgramFile(beat.name, beat.bytes);
                        if (saved != null) pj.offerAudio(saved);
                    }
                });
            }, "plugin-test").start();
        };
        use.addActionListener(e -> useIt.run());
        fav.addActionListener(e -> favIt.run());
        test.addActionListener(e -> run.accept(Boolean.FALSE));
        edit.addActionListener(e -> run.accept(Boolean.TRUE));
        close.addActionListener(e -> d.dispose());
        // The same on the plugin's own menu (right click), and Info.
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) useIt.run();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                menu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                menu(e);
            }

            private void menu(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int at = list.locationToIndex(e.getPoint());
                if (at < 0) return;
                list.setSelectedIndex(at);
                File f = list.getSelectedValue();
                JPopupMenu m = new JPopupMenu();
                m.add(item("Use", useIt));
                m.add(item(favourites().contains(f.getAbsolutePath()) ? "Remove from favourites" : "Add to favourites", favIt));
                m.add(item("Info", () -> javax.swing.JOptionPane.showMessageDialog(d, info(f), name(f), javax.swing.JOptionPane.INFORMATION_MESSAGE)));
                m.add(item("Test plugin", () -> run.accept(Boolean.FALSE)));
                m.add(item("Edit settings (kit, patch)", () -> run.accept(Boolean.TRUE)));
                m.show(list, e.getX(), e.getY());
            }
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        top.add(allTab);
        top.add(favTab);
        top.add(new JLabel("  Sort"));
        top.add(sort);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(460, 260));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (JButton b : new JButton[] {use, fav, test, edit, close}) buttons.add(b);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(new JScrollPane(about), BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        box.add(top, BorderLayout.NORTH);
        box.add(listScroll, BorderLayout.CENTER);
        box.add(south, BorderLayout.SOUTH);
        d.setContentPane(box);
        fill.run();
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return chosen[0];
    }

    private static JMenuItem item(String text, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(e -> r.run());
        return i;
    }

    /** What a test said, in short: its check line and how it ended (the lines that matter, not the setup). */
    static String summary(String log) {
        StringBuilder b = new StringBuilder();
        for (String l : log.split("\n")) {
            String t = l.trim();
            if (t.startsWith("Plugin check") || t.startsWith("Failed") || t.startsWith("Succeeded") || t.startsWith("Wrote ") || t.startsWith("Plugin:")
                || t.contains("window is open") || t.contains("settings:") || t.startsWith("Script timed out")) b.append(t).append('\n');
        }
        return b.length() == 0 ? log.trim() : b.toString().trim();
    }
}
