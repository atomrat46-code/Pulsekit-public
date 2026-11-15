package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * vst_render's Kit loaded row's Choose: the row's plugin's packs on the left, the sets of the pack picked on
 * the right (KitPacks: the kits run with the plugin and maps/kits.txt's). Use puts "Pack: Set" (a kit of its
 * own: its name) in the row. Add pack… and Add… (a set of the pack picked), a set's Rename… and Delete…,
 * a pack's Rename… (their right click) and a set dragged onto a pack (below them: a pack of its own) are
 * written into kits.txt; Edit list… opens it. Sets and packs differing in case only are one. Without a
 * plugin on the row, every kit named before.
 */
final class KitChooser {
    final JDialog dialog;
    final DefaultListModel<String> packModel = new DefaultListModel<String>(), setModel = new DefaultListModel<String>();
    final JList<String> packList = new FillList(packModel), setList = new FillList(setModel);

    /** A list as high as its box: its empty part below the last row takes a click or a dropped set too. */
    static final class FillList extends JList<String> {
        FillList(DefaultListModel<String> model) {
            super(model);
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return this.getParent() instanceof javax.swing.JViewport && this.getParent().getHeight() > this.getPreferredSize().height;
        }
    }
    final String plugin;
    Map<String, List<String>> packs;
    String chosen;
    /** The chooser shown last (for the tests). */
    static KitChooser last;

    /** The kit chosen ("Pack: Set"), or null when cancelled. */
    static String choose(java.awt.Component at, String plugin, String current) {
        KitChooser c = new KitChooser(at, plugin, current);
        last = c;
        c.dialog.setVisible(true);
        return c.chosen;
    }

    /**
     * The kits for the plugin: the runs' (and the kit names kept before they were kept with a plugin), then
     * the list file's, which puts a set under its pack (every kit named before when no plugin is given).
     */
    static List<String> kits(String plugin) {
        List<String> out = new ArrayList<String>();
        boolean any = plugin != null && plugin.trim().length() > 0;
        if (!any) {
            for (String k : VstLists.kitNames()) if (!out.contains(k)) out.add(k);
            return out;
        }
        List<String> run = new ArrayList<String>(VstLists.kitsFor(plugin));
        for (String k : VstLists.unplacedKits()) if (!run.contains(k)) run.add(k);
        return KitPacks.merged(run, KitPacks.listed(PluginFiles.read(listFile()), plugin));
    }

    static File listFile() {
        return KitPacks.file(new File(System.getProperty("user.home", ".")));
    }

    private KitChooser(java.awt.Component at, String plugin, String current) {
        this.plugin = plugin;
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(at);
        String plug = plugin == null || plugin.trim().isEmpty() ? null : new File(plugin.replace('\\', '/')).getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        this.dialog = new JDialog(owner, "Kit loaded" + (plug != null ? " · " + plug : ""), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        this.dialog.setName("kit-chooser");
        this.packList.setName("kit-packs");
        this.setList.setName("kit-sets");
        this.packList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.setList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.packList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) this.fillSets(null);
        });
        // A set dragged onto a pack: under that pack (written into kits.txt).
        this.setList.setDragEnabled(true);
        this.setList.setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public int getSourceActions(javax.swing.JComponent c) {
                return MOVE;
            }

            @Override
            protected java.awt.datatransfer.Transferable createTransferable(javax.swing.JComponent c) {
                String v = KitChooser.this.setList.getSelectedValue();
                return v == null ? null : new java.awt.datatransfer.StringSelection(v);
            }
        });
        this.packList.setDropMode(javax.swing.DropMode.ON);
        // A pack's name and how many sets it has.
        this.packList.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                List<String> sets = KitChooser.this.packs == null ? null : KitChooser.this.packs.get(String.valueOf(v));
                return super.getListCellRendererComponent(l, v + (sets == null ? "" : " (" + sets.size() + ")"), i, sel, focus);
            }
        });
        this.packList.setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return KitChooser.this.plugin != null && s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport s) {
                try {
                    int at = ((JList.DropLocation) s.getDropLocation()).getIndex();
                    String set = String.valueOf(s.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
                    // Below the packs: the set is a pack of its own (a pack added as a set by mistake).
                    if (at < 0 || at >= KitChooser.this.packModel.size()) return KitChooser.this.makePack(set);
                    return KitChooser.this.moveTo(set, KitChooser.this.packModel.get(at));
                } catch (Exception ex) {
                    return false;
                }
            }
        });
        this.setList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) KitChooser.this.use();
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                this.menu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                this.menu(e);
            }

            void menu(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int at = KitChooser.this.setList.locationToIndex(e.getPoint());
                if (at < 0 || !KitChooser.this.setList.getCellBounds(at, at).contains(e.getPoint())) return;
                KitChooser.this.setList.setSelectedIndex(at);
                KitChooser.this.setMenu().show(KitChooser.this.setList, e.getX(), e.getY());
            }
        });
        this.packList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                this.menu(e);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                this.menu(e);
            }

            void menu(java.awt.event.MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int at = KitChooser.this.packList.locationToIndex(e.getPoint());
                if (at < 0 || !KitChooser.this.packList.getCellBounds(at, at).contains(e.getPoint())) return;
                KitChooser.this.packList.setSelectedIndex(at);
                KitChooser.this.packMenu().show(KitChooser.this.packList, e.getX(), e.getY());
            }
        });
        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
        JPanel left = new JPanel(new BorderLayout(0, 4)), right = new JPanel(new BorderLayout(0, 4));
        left.add(new JLabel("Pack"), BorderLayout.NORTH);
        left.add(new JScrollPane(this.packList), BorderLayout.CENTER);
        right.add(new JLabel("Drum set"), BorderLayout.NORTH);
        right.add(new JScrollPane(this.setList), BorderLayout.CENTER);
        boolean editable = plugin != null && !plugin.trim().isEmpty();
        JButton addPack = new JButton("Add pack…");
        addPack.setName("kit-add-pack");
        addPack.setToolTipText("A new pack of this plugin's drum sets (kits.txt)");
        addPack.setEnabled(editable);
        addPack.addActionListener(e -> this.addPack());
        JPanel leftSouth = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftSouth.add(addPack);
        left.add(leftSouth, BorderLayout.SOUTH);
        this.addSet = new JButton("Add…");
        this.addSet.setName("kit-add-set");
        this.addSet.setToolTipText("A new drum set in the pack picked (kits.txt)");
        this.addSet.addActionListener(e -> this.addSet());
        this.editable = editable;
        JPanel rightSouth = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rightSouth.add(this.addSet);
        right.add(rightSouth, BorderLayout.SOUTH);
        lists.add(left);
        lists.add(right);
        JButton edit = new JButton("Edit list…");
        edit.setName("kit-edit");
        edit.setToolTipText("maps/kits.txt: a [plugin] section a plugin, a kit a line (Pack: Set)");
        edit.addActionListener(e -> this.editList());
        JButton use = new JButton("Use");
        use.setName("kit-use");
        use.addActionListener(e -> this.use());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> this.dialog.dispose());
        JPanel buttons = new JPanel(new BorderLayout());
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)), east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        west.add(edit);
        east.add(cancel);
        east.add(use);
        buttons.add(west, BorderLayout.WEST);
        buttons.add(east, BorderLayout.EAST);
        JPanel page = new JPanel(new BorderLayout(0, 8));
        page.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        page.add(lists, BorderLayout.CENTER);
        page.add(buttons, BorderLayout.SOUTH);
        this.dialog.setContentPane(page);
        this.reload(current);
        this.dialog.setSize(600, 420);
        this.dialog.setLocationRelativeTo(owner);
    }

    /** The lists again (after the file was edited), `current` picked when it is there. */
    void reload(String current) {
        this.packs = KitPacks.packs(kits(this.plugin));
        this.packModel.clear();
        for (String p : this.packs.keySet()) this.packModel.addElement(p);
        String[] cur = current == null || current.trim().isEmpty() ? null : KitPacks.split(current.trim());
        String pack = cur == null ? null : cur[0].isEmpty() ? KitPacks.NO_PACK : cur[0];
        int at = -1;
        for (int i = 0; pack != null && i < this.packModel.size(); i++) if (KitPacks.packKey(this.packModel.get(i)).equals(KitPacks.packKey(pack))) at = i;
        if (at < 0 && this.packModel.size() > 0) at = 0;
        if (at >= 0) this.packList.setSelectedIndex(at);
        this.fillSets(cur == null ? null : cur[1]);
    }

    void fillSets(String pick) {
        this.setModel.clear();
        String pack = this.packList.getSelectedValue();
        if (this.addSet != null) this.addSet.setEnabled(this.editable && pack != null);
        if (pack == null) return;
        for (String s : this.packs.get(pack)) this.setModel.addElement(s);
        int at = -1;
        for (int i = 0; pick != null && !pick.isEmpty() && i < this.setModel.size(); i++) if (KitPacks.setKey(this.setModel.get(i)).equals(KitPacks.setKey(pick))) at = i;
        if (at >= 0) this.setList.setSelectedIndex(at);
    }

    /** Use: the set picked (with its pack) in the row. */
    void use() {
        String pack = this.packList.getSelectedValue(), set = this.setList.getSelectedValue();
        if (set == null) return;
        this.chosen = KitPacks.join(pack, set);
        this.dialog.dispose();
    }

    JButton addSet;
    boolean editable;
    /** The tests: a name asked's answer, and Delete's (null: asked); the right click menu shown last. */
    static String answer;
    static Boolean sure;
    static javax.swing.JPopupMenu lastMenu;

    String ask(String title, String question, String start) {
        if (answer != null) return answer;
        Object got = JOptionPane.showInputDialog(this.dialog, question, title, JOptionPane.PLAIN_MESSAGE, null, null, start);
        return got == null ? null : got.toString().trim();
    }

    /** A pack's or set's name: no ":" (it parts a pack from its set) and not "-" first. */
    static String clean(String name) {
        return name == null ? "" : name.replace(':', ' ').replaceAll("^-+", "").replaceAll("\\s+", " ").trim();
    }

    /** Add pack…: a new pack (without sets yet) of this plugin's; it is picked. */
    void addPack() {
        String name = clean(this.ask("Add pack", "The new pack's name", ""));
        if (name.isEmpty() || KitPacks.NO_PACK.equals(name)) return;
        if (this.write(name + ":")) this.reload(name + ":");
    }

    /** Add…: a new set in the pack picked; it is picked. */
    void addSet() {
        String pack = this.packList.getSelectedValue();
        if (pack == null) return;
        String name = clean(this.ask("Add", "A new drum set in " + pack, ""));
        if (name.isEmpty()) return;
        String kit = KitPacks.join(pack, name);
        if (this.write(kit)) this.reload(kit);
    }

    /** A set's right click: Rename…, Delete…. */
    javax.swing.JPopupMenu setMenu() {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        m.setName("kit-set-menu");
        javax.swing.JMenuItem rename = new javax.swing.JMenuItem("Rename…"), delete = new javax.swing.JMenuItem("Delete…");
        rename.setName("kit-set-rename");
        delete.setName("kit-set-delete");
        rename.setEnabled(this.editable);
        delete.setEnabled(this.editable);
        rename.addActionListener(e -> this.renameSet());
        delete.addActionListener(e -> this.deleteSet());
        m.add(rename);
        m.add(delete);
        lastMenu = m;
        return m;
    }

    /** A pack's right click: Rename… (not "(no pack)"). */
    javax.swing.JPopupMenu packMenu() {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        m.setName("kit-pack-menu");
        javax.swing.JMenuItem rename = new javax.swing.JMenuItem("Rename…");
        rename.setName("kit-pack-rename");
        rename.setEnabled(this.editable && !KitPacks.NO_PACK.equals(this.packList.getSelectedValue()));
        rename.addActionListener(e -> this.renamePack());
        m.add(rename);
        lastMenu = m;
        return m;
    }

    /** A set renamed (in its pack). */
    void renameSet() {
        String pack = this.packList.getSelectedValue(), set = this.setList.getSelectedValue();
        if (pack == null || set == null) return;
        String name = clean(this.ask("Rename", "The drum set's new name", set));
        if (name.isEmpty() || name.equals(set)) return;
        File f = listFile();
        String t = KitPacks.remove(PluginFiles.read(f), this.plugin, set);
        String kit = KitPacks.join(pack, name);
        if (this.save(this.withAlias(KitPacks.put(t, this.plugin, kit), kit, KitPacks.join(pack, set)))) this.reload(kit);
    }

    /** A set taken off this plugin's lists, asked first. */
    void deleteSet() {
        String pack = this.packList.getSelectedValue(), set = this.setList.getSelectedValue();
        if (set == null) return;
        boolean yes = sure != null ? sure.booleanValue()
            : JOptionPane.showConfirmDialog(this.dialog, "Delete the drum set " + set + " from the lists?", "Delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
        if (!yes) return;
        if (this.save(KitPacks.remove(PluginFiles.read(listFile()), this.plugin, set))) this.reload(pack == null || KitPacks.NO_PACK.equals(pack) ? null : pack + ":");
    }

    /** A pack renamed: its sets under the new name. */
    void renamePack() {
        String pack = this.packList.getSelectedValue();
        if (pack == null || KitPacks.NO_PACK.equals(pack)) return;
        String name = clean(this.ask("Rename", "The pack's new name", pack));
        if (name.isEmpty() || name.equals(pack) || KitPacks.NO_PACK.equals(name)) return;
        List<String> sets = this.packs.get(pack) == null ? new ArrayList<String>() : this.packs.get(pack);
        String t = KitPacks.renamePack(PluginFiles.read(listFile()), this.plugin, pack, name, sets);
        for (String set : sets) t = this.withAlias(t, KitPacks.join(name, set), KitPacks.join(pack, set));
        if (this.save(t)) this.reload(name + ":");
    }

    /** A set made a pack of its own (dropped below the packs): the set taken off, a pack of its name made. */
    boolean makePack(String set) {
        String name = clean(set);
        if (name.isEmpty() || this.plugin == null) return false;
        String t = KitPacks.remove(PluginFiles.read(listFile()), this.plugin, set);
        if (!this.save(KitPacks.put(t, this.plugin, name + ":"))) return false;
        this.reload(name + ":");
        return true;
    }

    /** The text with the alias line "now = was" for a kit named anew (none when the names are one). */
    String withAlias(String text, String now, String was) {
        String line = KitPacks.aliasLine(now, was);
        return line == null ? text : KitPacks.put(text, this.plugin, line);
    }

    boolean save(String text) {
        File f = listFile();
        if (PluginFiles.write(f, text)) return true;
        JOptionPane.showMessageDialog(this.dialog, f.getPath() + " could not be written.");
        return false;
    }

    /** A set moved under a pack (dropped on it): written into kits.txt; false when it could not be. */
    boolean moveTo(String set, String pack) {
        if (set == null || set.trim().isEmpty() || pack == null) return false;
        String kit = KitPacks.join(pack, set.trim());
        // The kit's name before (the pack it was shown under): an alias, so the cymbal bench counts its tests as one kit.
        String from = this.packList.getSelectedValue();
        if (this.packs != null) for (java.util.Map.Entry<String, List<String>> e : this.packs.entrySet()) for (String x : e.getValue()) if (KitPacks.setKey(x).equals(KitPacks.setKey(set))) from = e.getKey();
        String was = KitPacks.join(from, set.trim());
        if (!this.save(this.withAlias(KitPacks.put(PluginFiles.read(listFile()), this.plugin, kit), kit, was))) return false;
        this.reload(kit);
        return true;
    }

    boolean write(String kit) {
        return this.save(KitPacks.put(PluginFiles.read(listFile()), this.plugin, kit));
    }

    /** The tests: a pack and a set picked as a click would. */
    void pick(String pack, String set) {
        this.packList.setSelectedValue(pack, true);
        this.fillSets(set);
    }

    /** Edit list…: maps/kits.txt's text (its template when there is none yet); OK saves it. */
    void editList() {
        File f = listFile();
        String text = PluginFiles.read(f);
        javax.swing.JTextArea area = new javax.swing.JTextArea(text != null && text.length() > 0 ? text : KitPacks.TEMPLATE, 18, 60);
        area.setName("kit-list-text");
        int ans = JOptionPane.showConfirmDialog(this.dialog, new JScrollPane(area), "Kit list · " + KitPacks.FILE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ans != JOptionPane.OK_OPTION) return;
        if (!PluginFiles.write(f, area.getText())) {
            JOptionPane.showMessageDialog(this.dialog, f.getPath() + " could not be written.");
            return;
        }
        String now = this.packList.getSelectedValue() == null || this.setList.getSelectedValue() == null ? null : KitPacks.join(this.packList.getSelectedValue(), this.setList.getSelectedValue());
        this.reload(now);
    }
}
