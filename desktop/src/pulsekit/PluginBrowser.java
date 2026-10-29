package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * PyJav > Plugins: the Plugin Browser. Every plugin and kit in the folders of its type (VST3, VST2,
 * Audio Unit, LV2, CLAP, SF2, SFZ; Folders\u2026 sets them), filtered by type, favourites, category,
 * group, tag and a search, sorted by name, type, category, group, date or My order (Up, Down). A
 * plugin's menu and buttons: Use (vst_render.py with it; a kit onto the pads), Test (a test beat
 * through it, or with the kit's sounds), Edit settings (the plugin's window, its kit or patch kept),
 * Info, favourite, Category\u2026, Group\u2026, Tags\u2026. Types shows which types each app uses and how.
 */
final class PluginBrowser {
    private PluginBrowser() {}

    static JDialog last;
    /** What the last Test or Use said (the tests read it). */
    static volatile String lastSaid = "";
    /** The tests' answers to the questions (Category, Group, Tags, a folder): null asks. */
    static java.util.function.Function<String, String> answer;

    static String home() {
        return System.getProperty("user.home", ".");
    }

    static void show(final Pulsekit app) {
        PluginsList.library();
        final JDialog d = new JDialog(app, "Plugin Browser", false);
        d.setName("plugin-browser");
        last = d;
        final List<File> all = new ArrayList<File>();
        final DefaultListModel<File> model = new DefaultListModel<File>();
        final JList<File> list = new JList<File>(model);
        list.setName("browser-list");
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                java.awt.Component c = super.getListCellRendererComponent(l, PluginLibrary.line((File) v), i, sel, focus);
                PluginLibrary.Type t = PluginLibrary.typeOf((File) v);
                // A type this app cannot play is greyed (listed, Info says why).
                if (t != null && !t.usable(false) && !sel) c.setForeground(java.awt.Color.GRAY);
                return c;
            }
        });
        final String[] typeNames = new String[PluginLibrary.TYPES.length + 1];
        typeNames[0] = PluginLibrary.ANY;
        for (int i = 0; i < PluginLibrary.TYPES.length; i++) typeNames[i + 1] = PluginLibrary.TYPES[i].name;
        final JComboBox<String> type = new JComboBox<String>(typeNames);
        type.setName("browser-type");
        final JComboBox<String> sort = new JComboBox<String>(PluginLibrary.SORTS);
        sort.setName("browser-sort");
        final JToggleButton favs = new JToggleButton("\u2605 Favourites");
        favs.setName("browser-favourites");
        final JTextField search = new JTextField(12);
        search.setName("browser-search");
        search.setToolTipText("Part of a name, category, group or tag; * for any letters, ? for one");
        final JComboBox<String> category = new JComboBox<String>(), group = new JComboBox<String>(), tag = new JComboBox<String>();
        category.setName("browser-category");
        group.setName("browser-group");
        tag.setName("browser-tag");
        final JTextArea about = new JTextArea(8, 50);
        about.setName("browser-info");
        about.setEditable(false);
        about.setLineWrap(true);
        about.setWrapStyleWord(true);
        final JLabel count = new JLabel();
        count.setName("browser-count");
        final boolean[] filling = {false};
        final Runnable filters = () -> {
            filling[0] = true;
            refill(category, PluginLibrary.categories());
            refill(group, PluginLibrary.groups());
            refill(tag, PluginLibrary.tags());
            filling[0] = false;
        };
        final Runnable fill = () -> {
            if (filling[0]) return;
            File was = list.getSelectedValue();
            model.clear();
            for (File f : PluginLibrary.shown(all, (String) type.getSelectedItem(), favs.isSelected(), (String) category.getSelectedItem(), (String) group.getSelectedItem(),
                (String) tag.getSelectedItem(), search.getText(), (String) sort.getSelectedItem())) model.addElement(f);
            if (was != null) list.setSelectedValue(was, true);
            if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
            count.setText(model.size() + " of " + all.size());
            if (model.isEmpty()) about.setText(all.isEmpty() ? "No plugins or kits in the folders (Folders\u2026 sets where each type is looked for)." : "None match the filters.");
        };
        final Runnable rescan = () -> {
            all.clear();
            // PyJav's VST folders (Params' Plugins list) too, until Folders\u2026 sets the VST types' own.
            boolean own = PluginLibrary.ownFolders(PluginLibrary.VST3) && PluginLibrary.ownFolders(PluginLibrary.VST2);
            List<File> more = own ? new ArrayList<File>() : new ArrayList<File>(PluginsList.scan(new ArrayList<File>()));
            all.addAll(PluginLibrary.scan(false, home(), more));
            filters.run();
            fill.run();
        };
        list.addListSelectionListener(e -> {
            File f = list.getSelectedValue();
            if (f != null && !e.getValueIsAdjusting()) about.setText(PluginLibrary.info(f, false) + stateNote(f));
        });
        for (JComboBox<String> c : java.util.Arrays.asList(type, sort, category, group, tag)) c.addActionListener(e -> fill.run());
        favs.addActionListener(e -> fill.run());
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                fill.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                fill.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                fill.run();
            }
        });
        final JButton use = button("Use", "browser-use"), test = button("Test", "browser-test"), edit = button("Edit settings", "browser-edit"),
            fav = button("Favourite", "browser-fav"), cat = button("Category\u2026", "browser-set-category"), grp = button("Group\u2026", "browser-set-group"),
            tags = button("Tags\u2026", "browser-set-tags"), up = button("Up", "browser-up"), down = button("Down", "browser-down"),
            folders = button("Folders\u2026", "browser-folders"), types = button("Types", "browser-types"), again = button("Rescan", "browser-rescan"), close = button("Close", "browser-close");
        use.setToolTipText("A VST or AU: vst_render.py with it as --plugin. A kit (SF2, SFZ): its drums onto the pads.");
        test.setToolTipText("A test beat through the plugin, or with the kit's sounds: it opens in WAV ready");
        edit.setToolTipText("The plugin's own window: load a kit or patch, close it, and vst_render plays with it from then on");
        final Runnable after = () -> {
            filters.run();
            fill.run();
            list.repaint();
        };
        final java.util.function.Consumer<String> say = s -> {
            lastSaid = s;
            about.setText(s + "\n\n" + about.getText());
            app.setNow(s.split("\n")[0]);
        };
        final Runnable useIt = () -> {
            File f = list.getSelectedValue();
            if (f != null) say.accept(use(app, f));
        };
        final java.util.function.Consumer<Boolean> testIt = editFirst -> {
            final File f = list.getSelectedValue();
            if (f == null) return;
            PluginLibrary.Type t = PluginLibrary.typeOf(f);
            if (t == null || !t.desktop || t.name.equals(PluginLibrary.LV2) || t.name.equals(PluginLibrary.CLAP)) {
                say.accept("Nothing to test for " + PluginLibrary.name(f) + ": " + (t == null ? "" : t.how));
                return;
            }
            if (t.name.equals(PluginLibrary.SF2) || t.name.equals(PluginLibrary.SFZ)) {
                if (editFirst) {
                    say.accept("A kit has no settings window: Use puts it onto the pads, where each pad's sound can be changed.");
                    return;
                }
                test.setEnabled(false);
                new Thread(() -> {
                    String said;
                    File wav = null;
                    try {
                        short[] beat = PluginLibrary.testBeat(PluginLibrary.kitVoices(f));
                        wav = app.pyJav.saveProgramFile("kit-test-" + PluginLibrary.name(f).replaceAll("[^A-Za-z0-9._ -]+", "_") + ".wav", AudioIo.encodeWav(beat, AudioIo.SR));
                        said = "Test beat with " + PluginLibrary.name(f) + ": " + (beat.length / AudioIo.SR) + " s" + (wav == null ? "" : ", " + wav.getName());
                    } catch (Exception ex) {
                        said = "Could not test " + PluginLibrary.name(f) + ": " + ex.getMessage();
                    }
                    final String s = said;
                    final File w = wav;
                    SwingUtilities.invokeLater(() -> {
                        test.setEnabled(true);
                        say.accept(s);
                        if (w != null && testsQuiet == null) app.pyJav.offerAudio(w);
                    });
                }, "kit-test").start();
                return;
            }
            for (JButton b : new JButton[] {use, test, edit}) b.setEnabled(false);
            about.setText(PluginsList.testing(f, editFirst));
            PluginsList.test(testsQuiet != null ? null : app.pyJav, f, editFirst, log -> {
                for (JButton b : new JButton[] {use, test, edit}) b.setEnabled(true);
                say.accept(PluginsList.summary(log));
            });
        };
        final Runnable favIt = () -> {
            File f = list.getSelectedValue();
            if (f == null) return;
            say.accept(PluginLibrary.name(f) + (PluginLibrary.toggleFavourite(f) ? " is a favourite" : " is not a favourite any more"));
            after.run();
        };
        final java.util.function.BiConsumer<String, java.util.List<String>> ask = (what, known) -> {
            File f = list.getSelectedValue();
            if (f == null) return;
            PluginLibrary.Entry e = PluginLibrary.entry(f);
            String now = what.equals("Category") ? e.category : what.equals("Group") ? e.group : String.join(", ", e.tags);
            String got = answer != null ? answer.apply(what) : askText(d, what, PluginLibrary.name(f), now, known);
            if (got == null) return;
            if (what.equals("Category")) PluginLibrary.setCategory(f, got);
            else if (what.equals("Group")) PluginLibrary.setGroup(f, got);
            else PluginLibrary.setTags(f, got);
            say.accept(PluginLibrary.name(f) + ": " + what.toLowerCase(java.util.Locale.ROOT) + (got.trim().isEmpty() ? " cleared" : " " + got.trim()));
            after.run();
        };
        final java.util.function.IntConsumer move = by -> {
            File f = list.getSelectedValue();
            if (f == null) return;
            if (!PluginLibrary.SORTS[6].equals(sort.getSelectedItem())) sort.setSelectedItem(PluginLibrary.SORTS[6]);
            List<File> shown = new ArrayList<File>();
            for (int i = 0; i < model.size(); i++) shown.add(model.get(i));
            PluginLibrary.move(shown, f, by);
            fill.run();
            list.setSelectedValue(f, true);
        };
        use.addActionListener(e -> useIt.run());
        test.addActionListener(e -> testIt.accept(Boolean.FALSE));
        edit.addActionListener(e -> testIt.accept(Boolean.TRUE));
        fav.addActionListener(e -> favIt.run());
        cat.addActionListener(e -> ask.accept("Category", PluginLibrary.categories()));
        grp.addActionListener(e -> ask.accept("Group", PluginLibrary.groups()));
        tags.addActionListener(e -> ask.accept("Tags", PluginLibrary.tags()));
        up.addActionListener(e -> move.accept(-1));
        down.addActionListener(e -> move.accept(1));
        folders.addActionListener(e -> {
            if (foldersDialog(d)) rescan.run();
        });
        types.addActionListener(e -> {
            lastSaid = PluginLibrary.typesTable(false);
            about.setText(lastSaid);
            about.setCaretPosition(0);
        });
        again.addActionListener(e -> rescan.run());
        close.addActionListener(e -> d.dispose());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) useIt.run();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                this.menu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                this.menu(e);
            }

            private void menu(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int at = list.locationToIndex(e.getPoint());
                if (at < 0) return;
                list.setSelectedIndex(at);
                File f = list.getSelectedValue();
                JPopupMenu m = new JPopupMenu();
                m.add(item("Use", useIt));
                m.add(item(PluginLibrary.entry(f).favourite ? "Remove from favourites" : "Add to favourites", favIt));
                m.add(item("Category\u2026", () -> ask.accept("Category", PluginLibrary.categories())));
                m.add(item("Group\u2026", () -> ask.accept("Group", PluginLibrary.groups())));
                m.add(item("Tags\u2026", () -> ask.accept("Tags", PluginLibrary.tags())));
                m.addSeparator();
                m.add(item("Info", () -> JOptionPane.showMessageDialog(d, PluginLibrary.info(f, false) + stateNote(f), PluginLibrary.name(f), JOptionPane.INFORMATION_MESSAGE)));
                m.add(item("Test", () -> testIt.accept(Boolean.FALSE)));
                m.add(item("Edit settings (kit, patch)", () -> testIt.accept(Boolean.TRUE)));
                m.addSeparator();
                m.add(item("Move up (My order)", () -> move.accept(-1)));
                m.add(item("Move down (My order)", () -> move.accept(1)));
                m.show(list, e.getX(), e.getY());
            }
        });
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row1.add(new JLabel("Type"));
        row1.add(type);
        row1.add(new JLabel(" Sort"));
        row1.add(sort);
        row1.add(favs);
        row1.add(new JLabel(" Search"));
        row1.add(search);
        row1.add(count);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row2.add(new JLabel("Category"));
        row2.add(category);
        row2.add(new JLabel(" Group"));
        row2.add(group);
        row2.add(new JLabel(" Tag"));
        row2.add(tag);
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(row1);
        top.add(row2);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(620, 300));
        JPanel b1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2)), b2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (JButton b : new JButton[] {use, test, edit, fav, cat, grp, tags}) b1.add(b);
        for (JButton b : new JButton[] {up, down, folders, types, again, close}) b2.add(b);
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.Y_AXIS));
        buttons.add(b1);
        buttons.add(b2);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(new JScrollPane(about), BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        box.add(top, BorderLayout.NORTH);
        box.add(listScroll, BorderLayout.CENTER);
        box.add(south, BorderLayout.SOUTH);
        d.setContentPane(box);
        rescan.run();
        d.pack();
        d.setLocationRelativeTo(app);
        d.setVisible(true);
    }

    /** Set by the tests: no WAV ready over the browser after a test. */
    static Object testsQuiet;

    /** Use: a VST or AU into vst_render.py's --plugin (PyJav shows it, ready to run); a kit onto the pads. What it did. */
    static String use(Pulsekit app, File f) {
        PluginLibrary.Type t = PluginLibrary.typeOf(f);
        if (t == null) return "Not a plugin or kit: " + f.getName();
        if (t.name.equals(PluginLibrary.SF2)) {
            try {
                app.projectIo.ingest(java.nio.file.Files.readAllBytes(f.toPath()), f.getName());
                return PluginLibrary.name(f) + " is on the pads (SoundFont)";
            } catch (Exception ex) {
                return "Could not load " + f.getName() + ": " + ex.getMessage();
            }
        }
        if (t.name.equals(PluginLibrary.SFZ)) {
            app.projectIo.importSfz(f);
            return "Loading " + PluginLibrary.name(f) + " onto the pads\u2026";
        }
        if (t.name.equals(PluginLibrary.LV2) || t.name.equals(PluginLibrary.CLAP)) return PluginLibrary.name(f) + ": " + t.how;
        app.programMenus.selectListedProgram("Python", "vst_render.py");
        if (app.pyJav.pyExtra != null) app.pyJav.pyExtra.setText(withPlugin(app.pyJav.pyExtra.getText(), f.getAbsolutePath()));
        app.showView("py");
        return "vst_render.py plays through " + PluginLibrary.name(f) + " (--plugin): give the MIDI, then Run";
    }

    /** The arguments with --plugin set to `path` (the one there replaced). */
    static String withPlugin(String args, String path) {
        List<String> w = JavaRun.split(args == null ? "" : args);
        StringBuilder out = new StringBuilder("--plugin " + JavaRun.quoteArg(path));
        for (int i = 0; i < w.size(); i++) {
            if (w.get(i).equals("--plugin") && i + 1 < w.size()) {
                i++;
                continue;
            }
            out.append(' ').append(JavaRun.quoteArg(w.get(i)));
        }
        return out.toString();
    }

    /** For VSTs: whether Edit settings saved a kit or patch for it. */
    static String stateNote(File f) {
        PluginLibrary.Type t = PluginLibrary.typeOf(f);
        if (t == null || !(t.name.equals(PluginLibrary.VST3) || t.name.equals(PluginLibrary.VST2) || t.name.equals(PluginLibrary.AU))) return "";
        File state = PluginsList.stateFile(f);
        return state.isFile() ? "\nSettings (kit, patch) saved with Edit settings: vst_render plays with them." : "\nNo settings saved with Edit settings: it plays with its own defaults.";
    }

    /** Folders\u2026: each type's folders (one a line), Add folder\u2026, Defaults; true when saved. */
    static boolean foldersDialog(java.awt.Window owner) {
        final JDialog d = new JDialog(owner, "Plugin folders", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.setName("plugin-folders");
        final String[] names = new String[PluginLibrary.TYPES.length];
        for (int i = 0; i < names.length; i++) names[i] = PluginLibrary.TYPES[i].name;
        final JComboBox<String> type = new JComboBox<String>(names);
        type.setName("folders-type");
        final JTextArea paths = new JTextArea(8, 50);
        paths.setName("folders-paths");
        final java.util.Map<String, String> edited = new java.util.LinkedHashMap<String, String>();
        final String[] shownType = {names[0]};
        final Runnable show = () -> {
            String t = (String) type.getSelectedItem();
            shownType[0] = t;
            paths.setText(edited.containsKey(t) ? edited.get(t) : String.join("\n", PluginLibrary.folders(t, false, home())));
        };
        type.addActionListener(e -> {
            edited.put(shownType[0], paths.getText());
            show.run();
        });
        JButton add = button("Add folder\u2026", "folders-add"), defaults = button("Defaults", "folders-defaults"), save = button("Save", "folders-save"), cancel = button("Cancel", "folders-cancel");
        add.addActionListener(e -> {
            String got;
            if (answer != null) got = answer.apply("Folder");
            else {
                javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
                fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
                got = fc.showOpenDialog(d) == javax.swing.JFileChooser.APPROVE_OPTION ? fc.getSelectedFile().getAbsolutePath() : null;
            }
            if (got != null) paths.setText((paths.getText().trim() + "\n" + got).trim());
        });
        defaults.addActionListener(e -> paths.setText(String.join("\n", PluginLibrary.defaults((String) type.getSelectedItem(), false, home()))));
        final boolean[] saved = {false};
        save.addActionListener(e -> {
            edited.put(shownType[0], paths.getText());
            for (java.util.Map.Entry<String, String> me : edited.entrySet()) {
                List<String> list = new ArrayList<String>();
                for (String l : me.getValue().split("\n")) if (l.trim().length() > 0) list.add(l.trim());
                boolean same = list.equals(PluginLibrary.defaults(me.getKey(), false, home()));
                PluginLibrary.setFolders(me.getKey(), same ? null : list);
            }
            saved[0] = true;
            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        north.add(new JLabel("Type"));
        north.add(type);
        north.add(new JLabel("  one folder a line; its subfolders are looked through too (" + PluginLibrary.DEPTH + " deep)"));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        for (JButton b : new JButton[] {add, defaults, save, cancel}) south.add(b);
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        box.add(north, BorderLayout.NORTH);
        box.add(new JScrollPane(paths), BorderLayout.CENTER);
        box.add(south, BorderLayout.SOUTH);
        d.setContentPane(box);
        show.run();
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return saved[0];
    }

    /** A category, group or tags asked: the ones in use to pick from, or a new one typed (empty clears). */
    static String askText(java.awt.Component parent, String what, String name, String now, java.util.List<String> known) {
        JComboBox<String> box = new JComboBox<String>(known.toArray(new String[0]));
        box.setEditable(true);
        box.setSelectedItem(now);
        int ok = JOptionPane.showConfirmDialog(parent, new Object[] {what + " of " + name + (what.equals("Tags") ? " (comma between them)" : "") + ":", box}, what,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return ok == JOptionPane.OK_OPTION ? String.valueOf(box.getEditor().getItem()) : null;
    }

    private static void refill(JComboBox<String> box, List<String> values) {
        Object was = box.getSelectedItem();
        box.removeAllItems();
        box.addItem(PluginLibrary.ANY);
        for (String v : values) box.addItem(v);
        box.setSelectedItem(was != null && values.contains(was) ? was : PluginLibrary.ANY);
    }

    private static JButton button(String text, String name) {
        JButton b = new JButton(text);
        b.setName(name);
        return b;
    }

    private static JMenuItem item(String text, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(e -> r.run());
        return i;
    }
}
