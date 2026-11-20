package pulsekit;

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * vst_render's Kit loaded field: as a name is typed, the plugin's packs that start so are offered under the
 * field (a pack picked puts "Pack: " in it, the cursor after it), then that pack's sets (KitPacks.suggest).
 * Typing on narrows them; the list goes when nothing matches. Only while the field is typed in (a kit
 * put in by Choose or a plugin picked shows none).
 */
final class KitSuggest {
    private KitSuggest() {}

    /** The row's plugin now. */
    interface Plugin {
        String get();
    }

    /** The suggestions shown last (for the tests). */
    static JPopupMenu last;

    static void attach(final JTextField field, final Plugin plugin) {
        final JPopupMenu menu = new JPopupMenu();
        menu.setName("kit-suggest");
        menu.setFocusable(false);
        final boolean[] filling = {false};
        final Runnable show = () -> {
            if (filling[0] || !field.isShowing() || !field.isFocusOwner()) {
                menu.setVisible(false);
                return;
            }
            List<String[]> got = KitPacks.suggest(KitPacks.packs(KitChooser.kits(plugin.get())), field.getText());
            menu.setVisible(false);
            menu.removeAll();
            if (got.isEmpty()) return;
            for (final String[] g : got) {
                JMenuItem item = new JMenuItem(g[0]);
                item.setName("kit-suggest:" + g[1]);
                item.addActionListener(e -> {
                    filling[0] = true;
                    field.setText(g[1]);
                    filling[0] = false;
                    field.setCaretPosition(field.getText().length());
                    field.requestFocusInWindow();
                    // A pack picked: its sets offered at once.
                    if (g[1].endsWith(":")) SwingUtilities.invokeLater(() -> fire(field));
                });
                menu.add(item);
            }
            last = menu;
            menu.show(field, 0, field.getHeight());
            field.requestFocusInWindow();
        };
        field.putClientProperty("kit-suggest", show);
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void now() {
                SwingUtilities.invokeLater(show);
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                now();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                now();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (e.getOppositeComponent() == null || !SwingUtilities.isDescendingFrom(e.getOppositeComponent(), menu)) menu.setVisible(false);
            }
        });
        field.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) menu.setVisible(false);
            }
        });
    }

    /** The suggestions for the field's text now (as typing would show them). */
    static void fire(JTextField field) {
        Object r = field.getClientProperty("kit-suggest");
        if (r instanceof Runnable) ((Runnable) r).run();
    }
}
