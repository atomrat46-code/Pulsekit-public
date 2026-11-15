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
 * own: its name) in the row; Edit list… opens kits.txt. Without a plugin on the row, every kit named before.
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

    /** The kits for the plugin: the runs' and the list file's (every kit named before when no plugin is given). */
    static List<String> kits(String plugin) {
        List<String> out = new ArrayList<String>();
        boolean any = plugin != null && plugin.trim().length() > 0;
        if (any) out.addAll(VstLists.kitsFor(plugin));
        else for (String k : VstLists.kitNames()) if (!out.contains(k)) out.add(k);
        if (any) for (String k : KitPacks.listed(PluginFiles.read(KitPacks.file(new File(System.getProperty("user.home", ".")))), plugin)) if (!out.contains(k)) out.add(k);
        return out;
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

    /** The tests: a pack and a set picked as a click would. */
    void pick(String pack, String set) {
        this.packList.setSelectedValue(pack, true);
        this.fillSets(set);
    }

    /** Edit list…: maps/kits.txt's text (its template when there is none yet); OK saves it. */
    void editList() {
        File f = KitPacks.file(new File(System.getProperty("user.home", ".")));
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
