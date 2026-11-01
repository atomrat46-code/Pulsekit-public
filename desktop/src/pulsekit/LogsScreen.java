package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/**
 * File > Logs: the log entries (Logs) of the type picked, the newest first. A click shows the
 * entry; its text can be selected and copied (Copy: the selected part, or the whole entry), the
 * entries picked (Export) or all of the list (Export all) exported into one text file, or deleted.
 */
final class LogsScreen {
    private LogsScreen() {}

    /** The type shown last. */
    static String type = Logs.ALL;

    static void show(final Pulsekit app) {
        final JDialog dialog = new JDialog(app, "Logs", true);
        dialog.setName("logs");
        final String[] types = new String[Logs.TYPES.length + 1];
        types[0] = Logs.ALL;
        System.arraycopy(Logs.TYPES, 0, types, 1, Logs.TYPES.length);
        final JComboBox<String> pick = new JComboBox<String>(types);
        pick.setName("logs-type");
        pick.setSelectedItem(type);
        // Each type with how many entries it has: "Program runs (12)" (counted when the list opens).
        pick.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, v + " (" + Logs.entries(String.valueOf(v)).size() + ")", i, sel, focus);
            }
        });
        final JLabel state = new JLabel();
        state.setName("logs-state");
        final DefaultListModel<Logs.Entry> model = new DefaultListModel<Logs.Entry>();
        final JList<Logs.Entry> list = new JList<Logs.Entry>(model);
        list.setName("logs-list");
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, v instanceof Logs.Entry ? ((Logs.Entry) v).label() : v, i, sel, focus);
            }
        });
        final JTextArea text = new JTextArea();
        text.setName("logs-text");
        text.setEditable(false);
        text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        final Runnable fill = () -> {
            type = String.valueOf(pick.getSelectedItem());
            model.clear();
            for (Logs.Entry e : Logs.entries(type)) model.addElement(e);
            text.setText(model.isEmpty() ? Logs.EMPTY : "");
            state.setText(model.size() + " entries" + (Logs.on() ? "" : "  \u2014  Logging is off (General settings > Logging on): nothing new is written"));
            if (!model.isEmpty()) list.setSelectedIndex(0);
        };
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Logs.Entry at = list.getSelectedValue();
            if (at == null) return;
            text.setText(Logs.text(at));
            text.setCaretPosition(0);
        });
        pick.addActionListener(e -> fill.run());
        JButton refresh = new JButton("Refresh");
        refresh.setName("logs-refresh");
        refresh.addActionListener(e -> fill.run());
        JButton copy = new JButton("Copy");
        copy.setName("logs-copy");
        copy.setToolTipText("The part of the entry selected, or the whole entry");
        copy.addActionListener(e -> {
            String part = text.getSelectedText();
            String all = part != null && part.length() > 0 ? part : text.getText();
            try {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(all), null);
                app.setNow("Copied " + all.length() + " characters of the log");
            } catch (Exception ex) {
                app.setNow("Cannot copy: " + ex.getMessage());
            }
        });
        JButton export = new JButton("Export\u2026");
        export.setName("logs-export");
        export.setToolTipText("The entries picked into one text file");
        export.addActionListener(e -> exportThem(app, dialog, new ArrayList<Logs.Entry>(list.getSelectedValuesList())));
        JButton exportAll = new JButton("Export all\u2026");
        exportAll.setName("logs-export-all");
        exportAll.setToolTipText("Every entry in the list into one text file");
        exportAll.addActionListener(e -> {
            List<Logs.Entry> all = new ArrayList<Logs.Entry>();
            for (int i = 0; i < model.size(); i++) all.add(model.get(i));
            exportThem(app, dialog, all);
        });
        JButton delete = new JButton("Delete");
        delete.setName("logs-delete");
        delete.addActionListener(e -> {
            List<Logs.Entry> chosen = new ArrayList<Logs.Entry>(list.getSelectedValuesList());
            if (chosen.isEmpty()) return;
            if (askDelete && javax.swing.JOptionPane.showConfirmDialog(dialog, "Delete " + chosen.size() + " log entries?", "Delete logs",
                javax.swing.JOptionPane.OK_CANCEL_OPTION) != javax.swing.JOptionPane.OK_OPTION) return;
            app.setNow("Deleted " + Logs.delete(chosen) + " log entries");
            fill.run();
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        north.add(new JLabel("Type"));
        north.add(pick);
        north.add(refresh);
        north.add(state);
        JScrollPane left = new JScrollPane(list);
        left.setPreferredSize(new Dimension(420, 480));
        JScrollPane right = new JScrollPane(text);
        right.setPreferredSize(new Dimension(620, 480));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        south.add(copy);
        south.add(export);
        south.add(exportAll);
        south.add(delete);
        south.add(close);
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        content.add(north, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        fill.run();
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(app);
        dialog.setVisible(true);
    }

    /** Delete asks first (the tests do not). */
    static boolean askDelete = true;

    /** Where the tests export to (null: ask). */
    static java.util.function.Function<String, File> exportTo;

    static void exportThem(Pulsekit app, java.awt.Component parent, List<Logs.Entry> chosen) {
        if (chosen.isEmpty()) {
            app.setNow("No log entries to export");
            return;
        }
        File to = exportTo != null ? exportTo.apply(Logs.exportName(type)) : ask(parent, Logs.exportName(type));
        if (to == null) return;
        try {
            Logs.export(chosen, to);
            app.setNow("Exported " + chosen.size() + " log entries into " + to);
        } catch (Exception ex) {
            app.setNow("Cannot export: " + ex.getMessage());
        }
    }

    static File ask(java.awt.Component parent, String name) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(new File(System.getProperty("user.home", ".")), name));
        return fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }
}
