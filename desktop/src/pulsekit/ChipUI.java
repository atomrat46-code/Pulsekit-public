package pulsekit;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * The modern look's buttons (chips, tabs, grid cells, Pulsekit's own flat buttons): rounded, a
 * soft gradient, lighter under the mouse and darker while pressed, and a rounded outline for the
 * outlined ones ("pk.outline"). Their colors stay the page's own (setBackground, setForeground).
 */
final class ChipUI extends BasicButtonUI {
    private static final ChipUI SHARED = new ChipUI();
    /** The corners' roundness; "pk.arc" on a button changes it (the grid's cells are less round). */
    static final int ARC = 10;
    /** Pulsekit's gold (FlatLaf's accent too). */
    static final Color ACCENT = new Color(0xD9A441);

    static ChipUI shared() {
        return SHARED;
    }

    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        b.setRolloverEnabled(true);
        b.setContentAreaFilled(false);
    }

    @Override
    public void update(Graphics g, JComponent c) {
        // An opaque button still owns its corners: the colour behind it fills them.
        if (c.isOpaque()) {
            g.setColor(behind(c));
            g.fillRect(0, 0, c.getWidth(), c.getHeight());
        }
        this.paintChip(g, (AbstractButton) c);
        this.paint(g, c);
    }

    void paintChip(Graphics g, AbstractButton b) {
        int w = b.getWidth();
        int h = b.getHeight();
        if (w <= 0 || h <= 0) return;
        ButtonModel m = b.getModel();
        Color bg = b.getBackground();
        if (!b.isEnabled()) bg = mix(bg, behind(b), 0.45f);
        else if (m.isPressed() && m.isArmed()) bg = shade(bg, -0.14f);
        else if (m.isRollover()) bg = shade(bg, 0.12f);
        Object arcProp = b.getClientProperty("pk.arc");
        int arc = arcProp instanceof Integer ? (Integer) arcProp : ARC;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, shade(bg, 0.07f), 0, h, shade(bg, -0.07f)));
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            // A faint light along the top edge: it reads as a raised key.
            g2.setColor(new Color(255, 255, 255, m.isPressed() ? 6 : 22));
            g2.drawLine(arc / 2, 1, w - arc / 2 - 1, 1);
            // The open tab: a gold bar along its foot.
            if (Boolean.TRUE.equals(b.getClientProperty("pk.selected"))) {
                g2.setColor(ACCENT);
                g2.fillRoundRect(arc / 2, h - 3, w - arc, 3, 3, 3);
            }
            Object outline = b.getClientProperty("pk.outline");
            if (outline instanceof Color) {
                g2.setColor(m.isRollover() && b.isEnabled() ? shade((Color) outline, 0.25f) : (Color) outline);
                Object widthProp = b.getClientProperty("pk.outlineWidth");
                int sw = widthProp instanceof Integer ? (Integer) widthProp : 1;
                g2.setStroke(new java.awt.BasicStroke(sw));
                // Drawn inside the button (a wide outline is not cut at its edges).
                float in = sw / 2f;
                g2.draw(new java.awt.geom.RoundRectangle2D.Float(in, in, w - sw, h - sw, arc, arc));
            }
        } finally {
            g2.dispose();
        }
    }

    /** The colour behind a component: its first opaque parent's. */
    static Color behind(Component c) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) if (p.isOpaque() && p.getBackground() != null) return p.getBackground();
        return UiKit.BG;
    }

    /** Lighter (amount > 0) or darker; a light colour (a picked chip) changes less, toward its contrast. */
    static Color shade(Color c, float amount) {
        if (c == null) return null;
        boolean light = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0 > 0.6;
        float a = light ? amount * 0.5f : amount;
        return a >= 0 ? mix(c, Color.WHITE, a) : mix(c, Color.BLACK, -a);
    }

    static Color mix(Color a, Color b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * u), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * u),
            Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * u), a.getAlpha());
    }
}
