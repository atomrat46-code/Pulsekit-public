package pulsekit;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * A popup list's last item: Cancel (closes it, nothing picked), as the phone's lists have. It is
 * put at the end each time the list opens, so items added after this still come before it.
 */
final class MenuCancel {
    private MenuCancel() {}

    static final String NAME = "menu-cancel";
    private static final String SEPARATOR = "menu-cancel-separator";

    /** Cancel at the end of `m` (from now on, each time it opens). */
    static JPopupMenu add(final JPopupMenu m) {
        if (m == null) return null;
        if (m.getClientProperty(NAME) == null) {
            m.putClientProperty(NAME, Boolean.TRUE);
            m.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    last(m);
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {}
            });
        }
        last(m);
        return m;
    }

    /** Cancel (and its separator) taken out and put back at the end. */
    private static void last(final JPopupMenu m) {
        for (java.awt.Component c : m.getComponents()) if (NAME.equals(c.getName()) || SEPARATOR.equals(c.getName())) m.remove(c);
        if (m.getComponentCount() > 0) {
            JPopupMenu.Separator sep = new JPopupMenu.Separator();
            sep.setName(SEPARATOR);
            m.add(sep);
        }
        JMenuItem cancel = new JMenuItem("Cancel");
        cancel.setName(NAME);
        cancel.addActionListener(e -> m.setVisible(false));
        m.add(cancel);
    }

    /** Whether `c` is a Cancel item or its separator (left out when a list's items are copied into another). */
    static boolean is(java.awt.Component c) {
        return c != null && (NAME.equals(c.getName()) || SEPARATOR.equals(c.getName()));
    }
}
