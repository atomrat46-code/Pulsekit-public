package pulsekit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A list with a search box above it (StyleDb.search: "rock" shows Rock, Hard Rock, Blues Rock...),
 * for the Style database and Params' Choose. Indices are into the full list.
 */
final class SearchList {
    final String[] items;
    final JPanel panel = new JPanel(new BorderLayout(0, 6));
    final JTextField search = new JTextField();
    final DefaultListModel<String> model = new DefaultListModel<String>();
    final JList<String> list = new JList<String>(this.model);
    private int[] map = new int[0];

    /** `marked` is underlined (the current style), `initial` selected; -1 for neither. */
    SearchList(String[] items, final int marked, int initial) {
        this.items = items;
        this.search.setName("list-search");
        this.search.setToolTipText("Search");
        this.list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        this.list.setFixedCellHeight(24);
        this.list.setVisibleRowCount(16);
        final Font listFont = UIManager.getFont("List.font");
        this.list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                if (c instanceof JLabel) {
                    JLabel lab = (JLabel) c;
                    Font base = listFont != null ? listFont : lab.getFont();
                    if (index >= 0 && index < SearchList.this.map.length && SearchList.this.map[index] == marked) {
                        Map<TextAttribute, Object> attrs = new HashMap<TextAttribute, Object>();
                        attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                        lab.setFont(base.deriveFont(attrs));
                    } else {
                        lab.setFont(base);
                    }
                }
                return c;
            }
        });
        this.filter(initial);
        JScrollPane scroll = new JScrollPane(this.list);
        scroll.setPreferredSize(new Dimension(380, 320));
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(new JLabel("Search"), BorderLayout.WEST);
        top.add(this.search, BorderLayout.CENTER);
        this.panel.add(top, BorderLayout.NORTH);
        this.panel.add(scroll, BorderLayout.CENTER);
        if (initial >= 0) this.list.ensureIndexIsVisible(initial);
        this.search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { SearchList.this.filter(SearchList.this.selected()); }
            public void removeUpdate(DocumentEvent e) { SearchList.this.filter(SearchList.this.selected()); }
            public void changedUpdate(DocumentEvent e) { SearchList.this.filter(SearchList.this.selected()); }
        });
    }

    /** Shows the names that match the search, keeping `keep` selected when it is among them. */
    void filter(int keep) {
        this.map = StyleDb.search(this.items, this.search.getText());
        this.model.clear();
        int at = -1;
        for (int i = 0; i < this.map.length; i++) {
            this.model.addElement(this.items[this.map[i]]);
            if (this.map[i] == keep) at = i;
        }
        if (at >= 0) {
            this.list.setSelectedIndex(at);
            this.list.ensureIndexIsVisible(at);
        } else if (this.map.length > 0 && keep < 0) {
            this.list.clearSelection();
        }
    }

    /** The selected name's index in the full list, or -1. */
    int selected() {
        int i = this.list.getSelectedIndex();
        return i >= 0 && i < this.map.length ? this.map[i] : -1;
    }

    /** A dialog with the list and `button` / Cancel; the chosen index in the full list, or -1. */
    static int choose(Component parent, String title, String[] items, int marked, int initial, String button) {
        SearchList s = new SearchList(items, marked, initial);
        Object[] options = new Object[] {button, "Cancel"};
        int ans = JOptionPane.showOptionDialog(parent, s.panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (ans != 0) return -1;
        int pick = s.selected();
        return pick >= 0 ? pick : initial;
    }
}
