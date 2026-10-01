package pulsekit;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.Timer;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** The pattern grid: cells and note lengths, steps, time signature and tempo fields. */
final class GridEditor {
    final Pulsekit app;

    GridEditor(Pulsekit app) {
        this.app = app;
    }

    boolean lenHeld;

    final JButton[][] buttons = new JButton[Engine.TRACK_ID.length][Engine.MAX_STEPS];

    final JButton[] accentBtns = new JButton[Engine.MAX_STEPS];

    final JLabel[] trackLabs = new JLabel[Engine.TRACK_ID.length];

    void styleNumField(JTextField field, int size) {
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setBackground(BG);
        field.setFont(new Font("Monospaced", 1, size));
        field.setColumns(Math.max(2, field.getColumns()));
    }

    void bindNumField(JTextField field, int min, int max, IntConsumer on) {
        Runnable commit = () -> {
            try {
                int n = Integer.parseInt(field.getText().trim().replaceAll("[^0-9-]", ""));
                n = Engine.clamp(n, min, max);
                if (field == app.tsDenField) n = Engine.clampTsDen(n);
                field.setText(Integer.toString(n));
                on.accept(n);
            } catch (Exception e) {
                if (field == app.bpmField) field.setText(Integer.toString(app.bpm()));
                else if (field == app.tsNumField) field.setText(Integer.toString(app.tsNum));
                else if (field == app.tsDenField) field.setText(Integer.toString(app.tsDen));
            }
        };
        field.addActionListener(e -> commit.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.selectAll();
            }
            @Override
            public void focusLost(FocusEvent e) {
                commit.run();
            }
        });
    }

    void applyTimeSig(int num, int den) {
        boolean two = Engine.isDoubled(app.steps, app.tsNum, app.tsDen);
        app.tsNum = Engine.clampTsNum(num);
        app.tsDen = Engine.clampTsDen(den);
        app.tsNumField.setText(Integer.toString(app.tsNum));
        app.tsDenField.setText(Integer.toString(app.tsDen));
        int next = Engine.patternSteps(app.tsNum, app.tsDen, two);
        Engine.defaultAccents(app.accents, next, Engine.stepsPerBeat(app.tsDen));
        this.applySteps(next, true);
    }

    void setBpmUi(int n) {
        int bpm = Engine.clampBpm(n);
        app.tempoBar.setVal(bpm);
        app.bpmField.setText(Integer.toString(bpm));
    }

    void onTempo(int n) {
        if (app.sequencer != null && !app.songPlay) {
            app.sequencer.setTempoInBPM(n);
        }
    }

    JPanel buildGrid() {
        int n;
        int cols = app.steps;
        JPanel jPanel = new JPanel(new GridLayout(Engine.TRACK_ID.length + 1, cols + 1, 3, 3));
        jPanel.setOpaque(false);
        Font font = new Font("SansSerif", 1, 11);
        JLabel jLabel = new JLabel("ACC");
        jLabel.setForeground(SUBTLE);
        jLabel.setFont(font);
        jPanel.add(jLabel);
        int pulse = Engine.stepsPerBeat(app.tsDen);
        int cellW = cols > 16 ? 22 : 28;
        for (n = 0; n < cols; ++n) {
            int n2 = n;
            JButton jButton = app.cellBtn(n % pulse == 0 ? Integer.toString(n / pulse + 1) : "\u00b7", cellW);
            jButton.addActionListener(actionEvent -> {
                app.accents[n2] = !app.accents[n2];
                this.refreshGrid();
            });
            this.accentBtns[n] = jButton;
            jPanel.add(jButton);
        }
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            JLabel jLabel2 = new JLabel(Engine.TRACK_SHORT[n]);
            jLabel2.setForeground(MUTED);
            jLabel2.setFont(font);
            final int n3 = n;
            jLabel2.addMouseListener(new MouseAdapter(){

                @Override
                public void mousePressed(MouseEvent mouseEvent) {
                    if (mouseEvent.getButton() == 3) {
                        app.mutes[n3] = !app.mutes[n3];
                        GridEditor.this.refreshGrid();
                    } else {
                        app.playback.preview(n3);
                    }
                }
            });
            this.trackLabs[n] = jLabel2;
            jPanel.add(jLabel2);
            for (int i = 0; i < cols; ++i) {
                JButton jButton = app.cellBtn("", cellW);
                int n4 = i;
                jButton.addActionListener(actionEvent -> {
                    if (this.lenHeld) {
                        this.lenHeld = false;
                        return;
                    }
                    int[][] vel = this.editCells();
                    int[][] row = this.editLens();
                    int cur = vel[n3][n4];
                    vel[n3][n4] = cur <= 0 ? 100 : (cur < 90 ? 0 : (cur < 120 ? 127 : 64));
                    if (vel[n3][n4] <= 0) row[n3][n4] = 0;
                    this.refreshGrid();
                    if (!"fills".equals(app.view)) app.styleLibrary.syncBuiltinFill();
                    if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) {
                        app.playback.rebuildAndPlay(true);
                    }
                });
                jButton.addMouseListener(new MouseAdapter() {
                    private Timer hold;

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.getButton() == 3) {
                            GridEditor.this.showLenMenu(jButton, n3, n4);
                            return;
                        }
                        if (e.getButton() != 1) return;
                        GridEditor.this.lenHeld = false;
                        if (this.hold != null) this.hold.stop();
                        this.hold = new Timer(420, ev -> {
                            GridEditor.this.lenHeld = true;
                            GridEditor.this.showLenMenu(jButton, n3, n4);
                        });
                        this.hold.setRepeats(false);
                        this.hold.start();
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (this.hold != null) this.hold.stop();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        /* keep the hold; leaving the tiny cell is common */
                    }
                });
                this.buttons[n][i] = jButton;
                jPanel.add(jButton);
            }
        }
        this.refreshGrid();
        return jPanel;
    }

    void toggleSteps() {
        int bar = Engine.barSteps(app.tsNum, app.tsDen);
        if (bar * 2 <= Engine.MAX_STEPS && app.steps == bar) this.applySteps(bar * 2, true);
        else this.applySteps(bar, false);
    }

    void applySteps(int n, boolean tile) {
        int bar = Engine.barSteps(app.tsNum, app.tsDen);
        int next = Engine.clampSteps(n);
        if (next != bar && !(next == bar * 2 && bar * 2 <= Engine.MAX_STEPS)) next = bar;
        if (next == app.steps && app.gridHost != null && app.gridHost.getComponentCount() > 0) {
            if (app.stepsBtn != null) {
                app.stepsBtn.setText(app.steps + " steps");
                app.paintOutline(app.stepsBtn, app.steps > bar);
            }
            return;
        }
        if (next > app.steps && tile) {
            int from = Math.max(1, Math.min(app.steps, bar));
            Engine.tileSteps(app.cells, from, next);
            Engine.tileSteps(app.lens, from, next);
            for (int s = from; s < next; s++) {
                app.accents[s] = app.accents[s % from];
            }
        }
        app.steps = next;
        if (app.stepsBtn != null) {
            app.stepsBtn.setText(app.steps + " steps");
            app.paintOutline(app.stepsBtn, app.steps > bar);
        }
        this.rebuildGrid();
        app.styleLibrary.syncBuiltinFill();
        if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) {
            app.playback.rebuildAndPlay(true);
        }
    }

    void rebuildGrid() {
        if (app.gridHost == null) return;
        app.gridHost.removeAll();
        app.gridHost.add((Component)this.buildGrid(), "Center");
        app.gridHost.revalidate();
        app.gridHost.repaint();
    }

    void refreshGrid() {
        int n;
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            if (this.trackLabs[n] != null) {
                this.trackLabs[n].setForeground(app.mutes[n] ? SUBTLE : MUTED);
            }
            for (int i = 0; i < app.steps; ++i) {
                if (this.buttons[n][i] == null) continue;
                boolean covered = Engine.lengthCoveredAt(this.editLens()[n], this.editCells()[n], i);
                int vel = this.editCells()[n][i];
                int len = vel > 0 ? Engine.lenAt(this.editLens(), n, i) : Engine.coverLen(this.editLens(), this.editCells(), n, i);
                this.paintCell(this.buttons[n][i], vel, len, i == app.playhead, app.accents[i], covered);
            }
        }
        for (n = 0; n < app.steps; ++n) {
            JButton jButton = this.accentBtns[n];
            if (jButton == null) continue;
            boolean bl = app.accents[n];
            jButton.setBackground(bl ? HIT : (n == app.playhead ? FG : ELEV));
            jButton.setForeground(bl || n == app.playhead ? BG : SUBTLE);
        }
    }

    int[][] editCells() {
        return "fills".equals(app.view) ? app.fillPat : app.cells;
    }

    int[][] editLens() {
        return "fills".equals(app.view) ? app.fillLens : app.lens;
    }

    void setCellLen(int t, int s, int n) {
        int[][] vel = this.editCells();
        int[][] row = this.editLens();
        int len = Engine.clampLen(n);
        if (vel[t][s] <= 0) vel[t][s] = 100;
        row[t][s] = len <= 1 ? 0 : len;
        this.refreshGrid();
        if (!"fills".equals(app.view)) app.styleLibrary.syncBuiltinFill();
        if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) {
            app.playback.rebuildAndPlay(true);
        }
    }

    void showLenMenu(JButton btn, int t, int s) {
        JPopupMenu menu = new JPopupMenu();
        int cur = Engine.lenAt(this.editLens(), t, s);
        JLabel head = new JLabel("Length \u00b7 " + Engine.TRACK_SHORT[t] + " \u00b7 " + (s + 1) + " \u00b7 " + Engine.noteLengthLabel(cur));
        head.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        head.setForeground(SUBTLE);
        menu.add(head);
        for (int i = 0; i < Engine.LEN_STEPS.length; i++) {
            final int n = Engine.LEN_STEPS[i];
            JMenuItem it = new JMenuItem(Engine.LEN_LABEL[i]);
            if (n == cur) it.setFont(it.getFont().deriveFont(Font.BOLD));
            it.addActionListener(e -> this.setCellLen(t, s, n));
            menu.add(it);
        }
        JMenuItem custom = new JMenuItem("Steps 1\u201316\u2026");
        custom.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(app, "Length in 16th notes (1\u201316)", Integer.toString(cur));
            if (raw == null) return;
            try {
                this.setCellLen(t, s, Integer.parseInt(raw.trim()));
            } catch (Exception ignored) { /* */ }
        });
        menu.add(custom);
        menu.show(btn, 0, btn.getHeight());
    }

    void paintCell(JButton jButton, int n, int len, boolean bl, boolean bl2, boolean covered) {
        if (jButton == null) {
            return;
        }
        Color color = bl
            ? HIT
            : (n > 0 || covered
                ? new Color(Engine.lenColor(len, n), true)
                : (bl2 ? new Color(0x222422) : ELEV));
        jButton.setBackground(color);
        String mark = n > 0 ? (Engine.lenMark(len).isEmpty() ? "16" : Engine.lenMark(len)) : (covered ? "—" : "");
        jButton.setText(mark);
        jButton.setForeground(n >= 90 ? BG : (n > 0 || covered ? FG : SUBTLE));
    }
}
