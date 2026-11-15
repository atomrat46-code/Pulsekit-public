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
 * own: its name) in the row; Add new… names a set (under the pack picked) and a set dragged onto a pack goes
 * under it, both written into kits.txt; Edit list… opens kits.txt. Without a plugin on the row, every kit
 * named before.
 */
final class KitChooser {
    final JDialog dialog;
    final DefaultListModel<String> packModel = new DefaultListModel<String>(), setModel = new DefaultListModel<String>();
    final JList<String> packList = new JList<String>(packModel), setList = new JList<String>(setModel);
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
        this.packList.setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return KitChooser.this.plugin != null && s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport s) {
                try {
                    int at = ((JList.DropLocation) s.getDropLocation()).getIndex();
                    if (at < 0 || at >= KitChooser.this.packModel.size()) return false;
                    String set = String.valueOf(s.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
                    return KitChooser.this.moveTo(set, KitChooser.this.packModel.get(at));
                } catch (Exception ex) {
                    return false;
                }
            }
        });
        this.setList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) KitChooser.this.use();
            }
        });
        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
        JPanel left = new JPanel(new BorderLayout(0, 4)), right = new JPanel(new BorderLayout(0, 4));
        left.add(new JLabel("Pack"), BorderLayout.NORTH);
        left.add(new JScrollPane(this.packList), BorderLayout.CENTER);
        right.add(new JLabel("Drum set"), BorderLayout.NORTH);
        right.add(new JScrollPane(this.setList), BorderLayout.CENTER);
        lists.add(left);
        lists.add(right);
        JButton edit = new JButton("Edit list…");
        edit.setName("kit-edit");
        edit.setToolTipText("maps/kits.txt: a [plugin] section a plugin, a kit a line (Pack: Set)");
        edit.addActionListener(e -> this.editList());
        JButton add = new JButton("Add new…");
        add.setName("kit-add");
        add.setToolTipText("A drum set of this plugin's: its name, under the pack picked (Pack: Set), into kits.txt");
        add.setEnabled(plugin != null && !plugin.trim().isEmpty());
        add.addActionListener(e -> this.addNew());
        JButton use = new JButton("Use");
        use.setName("kit-use");
        use.addActionListener(e -> this.use());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> this.dialog.dispose());
        JPanel buttons = new JPanel(new BorderLayout());
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)), east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        west.add(add);
        west.add(javax.swing.Box.createHorizontalStrut(6));
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
        this.dialog.setSize(560, 380);
        this.dialog.setLocationRelativeTo(owner);
    }

    /** The lists again (after the file was edited), `current` picked when it is there. */
    void reload(String current) {
        this.packs = KitPacks.packs(kits(this.plugin));
        this.packModel.clear();
        for (String p : this.packs.keySet()) this.packModel.addElement(p);
        String[] cur = current == null || current.trim().isEmpty() ? null : KitPacks.split(current.trim());
        String pack = cur == null ? null : cur[0].isEmpty() ? KitPacks.NO_PACK : cur[0];
        int at = pack == null ? -1 : this.packModel.indexOf(pack);
        if (at < 0 && this.packModel.size() > 0) at = 0;
        if (at >= 0) this.packList.setSelectedIndex(at);
        this.fillSets(cur == null ? null : cur[1]);
    }

    void fillSets(String pick) {
        this.setModel.clear();
        String pack = this.packList.getSelectedValue();
        if (pack == null) return;
        for (String s : this.packs.get(pack)) this.setModel.addElement(s);
        int at = pick == null ? -1 : this.setModel.indexOf(pick);
        if (at >= 0) this.setList.setSelectedIndex(at);
    }

    /** Use: the set picked (with its pack) in the row. */
    void use() {
        String pack = this.packList.getSelectedValue(), set = this.setList.getSelectedValue();
        if (set == null) return;
        this.chosen = KitPacks.join(pack, set);
        this.dialog.dispose();
    }

    /** Add new…'s answer for the tests (null: asked). */
    static String addAnswer;

    /** Add new…: a set's name ("Pack: Set", the pack picked filled in) into kits.txt; it is picked. */
    void addNew() {
        String pack = this.packList.getSelectedValue();
        String start = pack == null || KitPacks.NO_PACK.equals(pack) ? "" : pack + ": ";
        String kit = addAnswer != null ? addAnswer
            : (String) JOptionPane.showInputDialog(this.dialog, "The drum set: Pack: Set (a kit without a pack: its name alone)", "Add new", JOptionPane.PLAIN_MESSAGE, null, null, start);
        if (kit == null) return;
        kit = kit.trim();
        String[] p = KitPacks.split(kit);
        if (p[1].isEmpty()) return;
        kit = KitPacks.join(p[0], p[1]);
        if (this.write(kit)) this.reload(kit);
    }

    /** A set moved under a pack (dropped on it): written into kits.txt; false when it could not be. */
    boolean moveTo(String set, String pack) {
        if (set == null || set.trim().isEmpty() || pack == null) return false;
        String kit = KitPacks.join(pack, set.trim());
        if (!this.write(kit)) return false;
        this.reload(kit);
        return true;
    }

    boolean write(String kit) {
        File f = listFile();
        if (PluginFiles.write(f, KitPacks.put(PluginFiles.read(f), this.plugin, kit))) return true;
        JOptionPane.showMessageDialog(this.dialog, f.getPath() + " could not be written.");
        return false;
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
