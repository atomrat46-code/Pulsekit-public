package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Save prompt, after a run whose prompt sheet (--saveprompt) changed: the main category and the
 * subcategory (picked from the library's, typed new, or one of the places used recently; a new
 * name is made on OK), the prompt's name and description, Tag 1–3 (from the tag list, or Add new
 * tag), and the version it is saved as (the prompt's versions there + 1) with today's date. Cancel
 * saves nothing.
 */
final class SavePromptDialog {
    private SavePromptDialog() {}

    /** The subcategory Browse… menu shown last (for the tests). */
    static javax.swing.JPopupMenu lastBrowse;

    /** The place and names chosen; null when cancelled. */
    static PromptSave.Choice ask(Pulsekit app, final PromptVault vault, PromptRun.Sheet sheet) {
        PromptSave.Choice first = PromptSave.suggest(vault, sheet);
        final JComboBox<String> main = new JComboBox<String>();
        main.setName("save-prompt-main");
        main.setEditable(true);
        if (vault != null) for (PromptVault.Category c : vault.mains()) main.addItem(c.name);
        main.setSelectedItem(first.main);
        final JComboBox<String> sub = new JComboBox<String>();
        sub.setName("save-prompt-sub");
        sub.setEditable(true);
        final JTextField name = new JTextField(first.name, 28);
        name.setName("save-prompt-name");
        final JTextArea description = new JTextArea(first.description, 3, 28);
        description.setName("save-prompt-description");
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        final JLabel version = new JLabel();
        version.setName("save-prompt-version");
        final Runnable paintVersion = () -> {
            int v = vault == null ? 1 : PromptSave.nextVersion(vault, text(main), text(sub), name.getText());
            version.setText("Saved as v" + v + " · " + PromptSave.today());
        };
        // The subcategories offered are the main category's.
        final Runnable fillSubs = () -> {
            String keep = text(sub);
            sub.removeAllItems();
            sub.addItem("");
            long mainId = vault == null ? 0 : PromptSave.find(vault, text(main), "");
            // Subcategories nest: each as its place under the main one ("Clips / Night").
            for (String p : PromptSave.subPaths(vault, mainId)) sub.addItem(p);
            sub.setSelectedItem(keep);
        };
        fillSubs.run();
        sub.setSelectedItem(first.sub);
        main.addActionListener(e -> {
            fillSubs.run();
            paintVersion.run();
        });
        sub.addActionListener(e -> paintVersion.run());
        name.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                paintVersion.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                paintVersion.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                paintVersion.run();
            }
        });
        paintVersion.run();
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        List<String[]> recent = PromptSave.recent();
        if (!recent.isEmpty()) {
            JPanel places = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            for (final String[] place : recent) {
                String label = PromptSave.placeLabel(place[0], place[1]);
                JButton b = new JButton(label);
                b.setName("save-prompt-recent:" + label);
                b.addActionListener(e -> {
                    main.setSelectedItem(place[0]);
                    fillSubs.run();
                    sub.setSelectedItem(place[1]);
                    paintVersion.run();
                });
                places.add(b);
            }
            add(form, g, row++, "Recent", places);
        }
        add(form, g, row++, "Main category", main);
        // Browse…: the subcategories, then a new one inside the one typed or beside it (made on OK).
        JButton browse = new JButton("Browse\u2026");
        browse.setName("save-prompt-sub-browse");
        browse.addActionListener(e -> {
            javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
            long mainId = vault == null ? 0 : PromptSave.find(vault, text(main), "");
            java.util.List<String> paths = new java.util.ArrayList<String>();
            paths.add("");
            paths.addAll(PromptSave.subPaths(vault, mainId));
            for (final String p : paths) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(p.length() == 0 ? "(none)" : p);
                item.addActionListener(ev -> {
                    sub.setSelectedItem(p);
                    paintVersion.run();
                });
                m.add(item);
            }
            m.addSeparator();
            for (final boolean lower : new boolean[] {true, false}) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(lower ? PromptSave.ADD_LOWER : PromptSave.ADD_SAME);
                item.setName(lower ? "save-prompt-add-lower" : "save-prompt-add-same");
                item.addActionListener(ev -> {
                    String now = text(sub);
                    Object typed = JOptionPane.showInputDialog(browse, lower ? "Inside " + (now.length() == 0 ? text(main) : now) : "Beside " + now,
                        lower ? PromptSave.ADD_LOWER : PromptSave.ADD_SAME, JOptionPane.PLAIN_MESSAGE, null, null, "");
                    if (typed == null) return;
                    String made = lower ? PromptSave.lowerLevel(now, typed.toString()) : PromptSave.sameLevel(now, typed.toString());
                    sub.addItem(made);
                    sub.setSelectedItem(made);
                    paintVersion.run();
                });
                m.add(item);
            }
            lastBrowse = m;
            m.show(browse, 0, browse.getHeight());
        });
        JPanel subRow = new JPanel(new BorderLayout(6, 0));
        subRow.add(sub, BorderLayout.CENTER);
        subRow.add(browse, BorderLayout.EAST);
        add(form, g, row++, "Subcategory (optional; Clips / Night nests)", subRow);
        add(form, g, row++, "Name", name);
        add(form, g, row++, "Description (optional)", new JScrollPane(description));
        // Tag 1–3: each from the tag list (the prompts' and the Media browser's), or Add new tag.
        final List<String> tagList = PromptSave.tagChoices(vault, PromptDb.dir());
        final java.util.List<JComboBox<String>> tagBoxes = new java.util.ArrayList<JComboBox<String>>();
        for (int i = 0; i < 3; i++) {
            final JComboBox<String> box = new JComboBox<String>();
            box.setName("save-prompt-tag" + (i + 1));
            box.addItem("(none)");
            for (String t : tagList) box.addItem(t);
            if (i < first.tags.size()) box.setSelectedItem(first.tags.get(i));
            tagBoxes.add(box);
        }
        for (int i = 0; i < 3; i++) {
            final JComboBox<String> box = tagBoxes.get(i);
            JButton add = new JButton("Add new tag");
            add.setName("save-prompt-tag-new" + (i + 1));
            add.addActionListener(e -> {
                Object typed = JOptionPane.showInputDialog(box, "New tag", "Add new tag", JOptionPane.PLAIN_MESSAGE, null, null, "");
                String t = typed == null ? "" : PromptSave.newTag(PromptDb.dir(), typed.toString());
                if (t.length() == 0) return;
                // Offered in all three, and picked in this one.
                for (JComboBox<String> b : tagBoxes) {
                    boolean have = false;
                    for (int k = 0; k < b.getItemCount(); k++) have |= b.getItemAt(k).equalsIgnoreCase(t);
                    if (!have) b.addItem(t);
                }
                box.setSelectedItem(t);
            });
            JPanel tagRow = new JPanel(new BorderLayout(6, 0));
            tagRow.add(box, BorderLayout.CENTER);
            tagRow.add(add, BorderLayout.EAST);
            add(form, g, row++, "Tag " + (i + 1), tagRow);
        }
        // Group: the run's result file and the prompt file go in it ((none), a group, New group…).
        FileGroups.load(PromptDb.dir());
        final JComboBox<String> group = new JComboBox<String>();
        group.setName("save-prompt-group");
        group.addItem("(none)");
        for (String gr : FileGroups.names()) group.addItem(gr);
        group.addItem(FileGroups.NEW);
        group.addActionListener(e -> {
            if (!FileGroups.NEW.equals(group.getSelectedItem())) return;
            Object typed = JOptionPane.showInputDialog(group, "Group name", FileGroups.NEW, JOptionPane.PLAIN_MESSAGE, null, null, "");
            String made = FileGroups.clean(typed == null ? "" : typed.toString());
            if (made.isEmpty()) {
                group.setSelectedIndex(0);
                return;
            }
            FileGroups.make(made);
            group.insertItemAt(made, group.getItemCount() - 1);
            group.setSelectedItem(made);
        });
        add(form, g, row++, "Group (optional)", group);
        add(form, g, row++, "", version);
        // Add to favourites: the version stored goes into the favourite prompts (Browse prompts, Prompts page).
        javax.swing.JCheckBox favourite = new javax.swing.JCheckBox("Add to favourites");
        favourite.setName("save-prompt-favourite");
        add(form, g, row++, "", favourite);
        JLabel hint = new JLabel("A new category or subcategory name is made on OK.");
        add(form, g, row, "", hint);
        JPanel box = new JPanel(new BorderLayout());
        box.add(form, BorderLayout.CENTER);
        Object[] options = {"OK", "Cancel"};
        int ans = JOptionPane.showOptionDialog(app, box, "Save prompt", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (ans != 0) return null;
        PromptSave.Choice c = new PromptSave.Choice();
        c.main = text(main).length() == 0 ? "General" : text(main);
        c.sub = text(sub);
        c.name = name.getText().trim().length() == 0 ? first.name : name.getText().trim();
        c.description = description.getText().trim();
        c.favourite = favourite.isSelected();
        Object gr = group.getSelectedItem();
        c.group = gr == null || "(none)".equals(gr) || FileGroups.NEW.equals(gr) ? "" : gr.toString();
        for (JComboBox<String> b : tagBoxes) {
            Object t = b.getSelectedItem();
            if (t != null && !"(none)".equals(t.toString())) c.tags.add(t.toString());
        }
        return c;
    }

    private static void add(JPanel form, GridBagConstraints g, int row, String label, java.awt.Component c) {
        g.gridy = row;
        g.gridx = 0;
        g.weightx = 0;
        form.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        form.add(c, g);
    }

    private static String text(JComboBox<String> box) {
        Object v = box.isEditable() ? box.getEditor().getItem() : box.getSelectedItem();
        return v == null ? "" : v.toString().trim();
    }
}
