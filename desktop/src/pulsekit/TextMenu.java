package pulsekit;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;

/**
 * A text field's menu, as a long press gives on the phone: Select all, Cut, Copy, Paste. Opened
 * with a right click, or by holding the mouse button down on the field. Cut and Paste are greyed
 * in a field that cannot be edited, Cut and Copy when nothing is selected.
 */
final class TextMenu {
    private TextMenu() {}

    static void attach(final JTextComponent field) {
        MouseAdapter mouse = new MouseAdapter() {
            Timer hold;

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    show(field, e.getX(), e.getY());
                    return;
                }
                final int x = e.getX();
                final int y = e.getY();
                this.hold = new Timer(600, ev -> show(field, x, y));
                this.hold.setRepeats(false);
                this.hold.start();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (this.hold != null) this.hold.stop();
                if (e.isPopupTrigger()) show(field, e.getX(), e.getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // Dragging selects text: not a hold.
                if (this.hold != null) this.hold.stop();
            }
        };
        field.addMouseListener(mouse);
        field.addMouseMotionListener(mouse);
    }

    /** The menu itself, for the field's state now. */
    static JPopupMenu menu(final JTextComponent field) {
        JPopupMenu menu = new JPopupMenu();
        boolean editable = field.isEditable() && field.isEnabled();
        boolean selected = field.getSelectionStart() != field.getSelectionEnd();
        JMenuItem all = new JMenuItem("Select all");
        all.addActionListener(e -> {
            field.requestFocusInWindow();
            field.selectAll();
        });
        JMenuItem cut = new JMenuItem("Cut");
        cut.setEnabled(editable && selected);
        cut.addActionListener(e -> field.cut());
        JMenuItem copy = new JMenuItem("Copy");
        copy.setEnabled(selected);
        copy.addActionListener(e -> field.copy());
        JMenuItem paste = new JMenuItem("Paste");
        paste.setEnabled(editable);
        paste.addActionListener(e -> field.paste());
        menu.add(all);
        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        return menu;
    }

    static void show(JTextComponent field, int x, int y) {
        if (!field.isShowing()) return;
        menu(field).show(field, x, y);
    }
}
