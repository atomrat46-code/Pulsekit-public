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
 * name is made on OK), the prompt's name and description, and the version it is saved as (the
 * prompt's versions there + 1) with today's date. Cancel saves nothing.
 */
final class SavePromptDialog {
    private SavePromptDialog() {}

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
            if (mainId != 0) for (PromptVault.Category c : vault.children(mainId)) sub.addItem(c.name);
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
        add(form, g, row++, "Subcategory (optional)", sub);
        add(form, g, row++, "Name", name);
        add(form, g, row++, "Description (optional)", new JScrollPane(description));
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
