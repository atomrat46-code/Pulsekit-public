package pulsekit;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Timer;

/** Colors and the small Swing factories every page uses. */
abstract class UiKit extends JFrame {
    UiKit(String title) {
        super(title);
    }

    static final Color BG = new Color(658188);

    static final Color SURFACE = new Color(1250326);

    static final Color FG = new Color(15526886);

    static final Color MUTED = new Color(9079686);

    static final Color SUBTLE = new Color(6053209);

    static final Color ELEV = new Color(1776927);

    static final Color BORDER = new Color(2763564);

    static final Color HIT = new Color(10136476);

    JButton action(String string, Color color, Color color2, Runnable runnable) {
        JButton jButton = this.action(string, color, color2);
        jButton.addActionListener(actionEvent -> runnable.run());
        return jButton;
    }

    void onChipMenu(JButton btn, Supplier<JPopupMenu> menus) {
        btn.addMouseListener(new MouseAdapter() {
            Timer hold;
            void showAt(MouseEvent e) {
                JPopupMenu menu = menus.get();
                if (menu == null || menu.getComponentCount() == 0) return;
                menu.show(btn, e != null ? e.getX() : 8, e != null ? e.getY() : btn.getHeight());
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == 3) {
                    showAt(e);
                    return;
                }
                hold = new Timer(450, ev -> {
                    JPopupMenu menu = menus.get();
                    if (menu != null && menu.getComponentCount() > 0) menu.show(btn, 8, btn.getHeight());
                });
                hold.setRepeats(false);
                hold.start();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (hold != null) hold.stop();
                if (e.isPopupTrigger()) showAt(e);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (hold != null) hold.stop();
            }
        });
    }

    void addMenuHeading(JPopupMenu m, String title) {
        JMenuItem h = new JMenuItem(title);
        h.setEnabled(false);
        m.add(h);
    }

    void onRightClick(JButton btn, Runnable action) {
        btn.addMouseListener(new MouseAdapter() {
            Timer hold;
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == 3) {
                    action.run();
                    return;
                }
                hold = new Timer(450, ev -> action.run());
                hold.setRepeats(false);
                hold.start();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (hold != null) hold.stop();
                if (e.isPopupTrigger()) action.run();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (hold != null) hold.stop();
            }
        });
    }

    void walkChips(JComponent host, java.util.function.Consumer<JButton> fn) {
        for (Component c : host.getComponents()) {
            if (c instanceof JButton) fn.accept((JButton) c);
            else if (c instanceof JComponent) this.walkChips((JComponent) c, fn);
        }
    }

    JLabel sectionLab(String s) {
        JLabel l = new JLabel(s.toUpperCase());
        l.setForeground(SUBTLE);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        l.setAlignmentX(0.0f);
        return l;
    }

    /** The imported file sets scroll inside at most maxHeight pixels, so a long list leaves room for the grid. */
    JScrollPane cappedScroll(JComponent content, int maxHeight) {
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = content.getPreferredSize();
                return new Dimension(d.width, Math.min(maxHeight, d.height + 4));
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, this.getPreferredSize().height);
            }
        };
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setAlignmentX(0.0f);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    void flatten(JButton jButton) {
        jButton.setFocusPainted(false);
        jButton.setBorderPainted(false);
        jButton.setOpaque(true);
        jButton.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
    }

    JButton chip(String string, boolean bl) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        this.paintChip(jButton, bl);
        return jButton;
    }

    JButton outline(String string, boolean bl) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        this.paintOutline(jButton, bl);
        return jButton;
    }

    void paintChip(JButton jButton, boolean bl) {
        jButton.setBackground(bl ? FG : ELEV);
        jButton.setForeground(bl ? BG : FG);
        jButton.setBorderPainted(false);
    }

    void underlineChip(JButton b, boolean on) {
        Font f = b.getFont();
        Map<TextAttribute, Object> attrs = new HashMap<TextAttribute, Object>(f.getAttributes());
        attrs.put(TextAttribute.UNDERLINE, on ? TextAttribute.UNDERLINE_ON : Integer.valueOf(-1));
        b.setFont(f.deriveFont(attrs));
    }

    void paintOutline(JButton jButton, boolean bl) {
        jButton.setOpaque(true);
        jButton.setBackground(bl ? new Color(2765356) : BG);
        jButton.setForeground(bl ? FG : MUTED);
        jButton.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(bl ? HIT : BORDER), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
    }

    JButton cellBtn(String string) {
        return this.cellBtn(string, 28);
    }

    JButton cellBtn(String string, int w) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        jButton.setFont(new Font("SansSerif", 1, 9));
        jButton.setPreferredSize(new Dimension(w, 22));
        jButton.setMinimumSize(new Dimension(w, 22));
        jButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        jButton.setBackground(ELEV);
        jButton.setForeground(SUBTLE);
        return jButton;
    }

    JButton action(String string, Color color, Color color2) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        jButton.setBackground(color);
        jButton.setForeground(color2);
        jButton.setFont(new Font("SansSerif", 1, 14));
        jButton.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        return jButton;
    }
}
