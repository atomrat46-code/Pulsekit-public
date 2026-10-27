package pulsekit;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * One row of buttons that scrolls sideways when it is wider than the window, as Android's row
 * does: the mouse near the row's left or right edge scrolls it that way (the wheel does too).
 * No scroll bar takes room.
 */
final class EdgeScrollRow extends JScrollPane {
    /** How near the edge (pixels) the mouse scrolls the row, and how far each tick goes. */
    static final int EDGE = 36;
    static final int STEP = 8;

    final JPanel items;
    /** -1 left, 1 right, 0 still. */
    int direction;
    private final Timer ticks = new Timer(16, e -> this.scrollBy(this.direction * STEP));

    EdgeScrollRow() {
        this(new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)));
    }

    /** Around a row of its own (a chip strip whose chips change: the mouse over any of them counts). */
    EdgeScrollRow(JPanel items) {
        this.items = items;
        this.items.setOpaque(false);
        this.setViewportView(this.items);
        this.setOpaque(false);
        this.getViewport().setOpaque(false);
        this.setBorder(BorderFactory.createEmptyBorder());
        this.setViewportBorder(null);
        this.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        this.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        this.setAlignmentX(0.0f);
        MouseAdapter edges = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                EdgeScrollRow.this.aim(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), EdgeScrollRow.this).x);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                this.mouseMoved(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), EdgeScrollRow.this);
                if (!new Rectangle(0, 0, EdgeScrollRow.this.getWidth(), EdgeScrollRow.this.getHeight()).contains(p)) EdgeScrollRow.this.aim(-1);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                EdgeScrollRow.this.scrollBy(e.getWheelRotation() * STEP * 3);
            }
        };
        this.edges = edges;
        this.addMouseWheelListener(edges);
        // The mouse over the row or anything in it, chips added later too (a strip is rebuilt when patterns change).
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
            if (!(ev instanceof MouseEvent) || !this.isShowing()) return;
            MouseEvent m = (MouseEvent) ev;
            Component c = m.getComponent();
            boolean inside = c != null && (c == this || SwingUtilities.isDescendingFrom(c, this));
            int id = m.getID();
            if (id == MouseEvent.MOUSE_MOVED || id == MouseEvent.MOUSE_DRAGGED) {
                if (inside) edges.mouseMoved(m);
                else if (this.direction != 0) this.aim(-1);
            } else if (id == MouseEvent.MOUSE_EXITED && inside) {
                edges.mouseExited(m);
            }
        }, java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    private final MouseAdapter edges;

    /** The mouse at x (in the row): near an edge with more items that way, the row scrolls; else it stops. */
    void aim(int x) {
        int d = 0;
        if (x >= 0 && x < EDGE && this.offset() > 0) d = -1;
        else if (x >= 0 && x > this.getWidth() - EDGE && this.offset() < this.maxOffset()) d = 1;
        this.direction = d;
        if (d != 0 && !this.ticks.isRunning()) this.ticks.start();
        if (d == 0) this.ticks.stop();
    }

    int offset() {
        return this.getViewport().getViewPosition().x;
    }

    int maxOffset() {
        return Math.max(0, this.items.getPreferredSize().width - this.getViewport().getExtentSize().width);
    }

    /** Scrolled by dx pixels, within the row's ends. */
    void scrollBy(int dx) {
        JViewport v = this.getViewport();
        int x = Math.max(0, Math.min(this.maxOffset(), this.offset() + dx));
        v.setViewPosition(new Point(x, 0));
        if (x == 0 || x == this.maxOffset()) {
            this.direction = 0;
            this.ticks.stop();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = this.items.getPreferredSize();
        return new Dimension(d.width, d.height);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(40, this.items.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, this.items.getPreferredSize().height);
    }
}
