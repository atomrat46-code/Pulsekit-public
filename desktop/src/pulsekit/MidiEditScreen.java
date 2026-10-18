package pulsekit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import static pulsekit.Pulsekit.*;

/**
 * The MIDI editor (long press or right click on a file set's source.mid): the MIDI's hits on a
 * grid like the pattern's, ACC and a row per track, a column for each 16th. A click adds or erases
 * a hit (on ACC: the step's hits loud, or back); Play, Pause and Stop play it as it is now; Save
 * keeps it as the file set's MIDI, Back leaves without saving.
 */
final class MidiEditScreen {
    /** The editor open now (one at a time), for the playback clock and the tests. */
    static MidiEditScreen now;

    static final Color ACCENT = new Color(0xD9A441);
    static final Color BEAT = new Color(0x24272A);
    static final int CELL = 30;
    static final int GAP = 4;
    static final int ROW = 34;

    final Pulsekit app;
    final String key;
    final String name;
    final MidiHitEdit edit;
    final JDialog dialog;
    final Grid grid;
    final JScrollPane scroll;
    final JLabel status;
    int[][] cells;
    int playhead = -1;
    /** Edits since the playback was made: Play makes it again from where it was. */
    boolean stale;
    /** Opened by an import (Open in MIDI editor): Back and Save go on to the file set's patterns. */
    boolean fromImport;

    static String playKey(String key) {
        return "edit:" + key;
    }

    /** After an import with "Open in MIDI editor": Back and Save then show the Pattern view of its file set. */
    static void openFromImport(Pulsekit app, String label) {
        open(app, label);
        if (now != null) now.fromImport = true;
    }

    /** Save for a MIDI that is not a file set's (the Media browser's): writes it, returns where. */
    interface Saver {
        String save(byte[] midi) throws Exception;
    }

    /** The editor on a MIDI file's bytes (`name`); Save hands the MIDI to `saver`. */
    static void openFile(Pulsekit app, String name, byte[] midi, Saver saver) {
        MidiHitEdit edit = MidiHitEdit.of(midi);
        if (edit == null) {
            app.setNow(name + " is not a MIDI file");
            return;
        }
        if (now != null) now.close();
        now = new MidiEditScreen(app, "", name, edit, saver);
    }

    /** Opens the editor on the file set's source MIDI; `label` is the set's row (or its source). */
    static void open(Pulsekit app, String label) {
        String key = Engine.fileSetMidiKeyForLabel(label == null ? "" : label);
        Engine.FileSetAudio au = Engine.fileSetAudioOf(key);
        MidiHitEdit edit = au == null ? null : MidiHitEdit.of(au.sourceMidi);
        if (edit == null) {
            app.setNow("That MIDI file is missing");
            return;
        }
        if (now != null) now.close();
        now = new MidiEditScreen(app, key, Engine.fileSetMidiName(key), edit, null);
    }

    /** Where Save puts a MIDI that is not a file set's; null for a file set's. */
    final Saver saver;

    private MidiEditScreen(Pulsekit app, String key, String name, MidiHitEdit edit, Saver saver) {
        this.saver = saver;
        this.app = app;
        this.key = key;
        this.name = name;
        this.edit = edit;
        this.cells = edit.cells();
        JPanel page = new JPanel(new BorderLayout(0, 8));
        page.setName("midiedit");
        page.setBackground(BG);
        page.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("MIDI editor");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(FG);
        top.add(title);
        JLabel sub = new JLabel(saver != null ? name + " \u00b7 Save keeps it as a new file beside it" : (key.isEmpty() ? "Other" : key) + " · " + name);
        sub.setForeground(MUTED);
        top.add(sub);
        this.status = new JLabel(" ");
        this.status.setName("midiedit-status");
        this.status.setForeground(HIT);
        top.add(this.status);
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        transport.setOpaque(false);
        transport.setAlignmentX(0f);
        JLabel clock = new JLabel("00:00 / 00:00");
        clock.setName("midiclock");
        clock.setForeground(MUTED);
        clock.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        transport.add(clock);
        JButton play = app.outline("Play", false);
        play.setName("midiedit-play");
        play.addActionListener(e -> this.play());
        JButton pause = app.outline("Pause", false);
        pause.setName("midiedit-pause");
        pause.addActionListener(e -> app.fileSets.pauseSourceMidi());
        JButton stop = app.outline("Stop", false);
        stop.setName("midiedit-stop");
        stop.addActionListener(e -> app.fileSets.stopSourceMidi());
        transport.add(play);
        transport.add(pause);
        transport.add(stop);
        top.add(transport);
        for (java.awt.Component c : top.getComponents()) ((JComponent) c).setAlignmentX(0f);
        page.add(top, BorderLayout.NORTH);

        // Track names stay put; the steps scroll sideways beside them.
        JPanel names = new JPanel(new GridLayout(0, 1));
        names.setBackground(BG);
        names.add(this.rowName("ACC", ACCENT));
        for (String s : Engine.TRACK_SHORT) names.add(this.rowName(s, FG));
        this.grid = new Grid();
        this.grid.setName("midiedit-grid");
        JScrollPane scroll = this.scroll = new JScrollPane(this.grid, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        // At the top of its own panel: each name as tall as its grid row, however tall the window is.
        JPanel nameColumn = new JPanel(new BorderLayout());
        nameColumn.setBackground(BG);
        nameColumn.add(names, BorderLayout.NORTH);
        scroll.setRowHeaderView(nameColumn);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        scroll.getHorizontalScrollBar().setUnitIncrement(CELL + GAP);
        page.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 8, 0));
        buttons.setOpaque(false);
        JButton back = app.action("Back", ELEV, FG, () -> this.back());
        back.setName("midiedit-back");
        JButton save = app.action("Save", HIT, BG, () -> this.save());
        save.setName("midiedit-save");
        back.setPreferredSize(new Dimension(160, 44));
        buttons.add(back);
        buttons.add(save);
        page.add(buttons, BorderLayout.SOUTH);

        this.dialog = new JDialog(app, "MIDI editor · " + name, false);
        this.dialog.setName("midiedit-window");
        this.dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        // Closing the window is Back: nothing is saved.
        this.dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                MidiEditScreen.this.back();
            }
        });
        this.dialog.setContentPane(page);
        this.showStatus();
        this.dialog.setSize(Math.min(1100, Math.max(640, app.getWidth() - 80)), 40 + ROW * 13 + 260);
        this.dialog.setLocationRelativeTo(app);
        this.dialog.setVisible(true);
    }

    JLabel rowName(String s, Color color) {
        JLabel l = new JLabel(s);
        l.setForeground(color);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        l.setPreferredSize(new Dimension(52, ROW));
        l.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 6));
        return l;
    }

    void showStatus() {
        String s = this.edit.hits() + " hits · " + this.edit.bars() + (this.edit.bars() == 1 ? " bar" : " bars") + " · " + this.edit.bpm + " BPM";
        this.status.setText(this.edit.changed ? s + " · not saved" : s);
    }

    /** A click on the grid: row 0 is ACC, then the tracks. */
    void tap(int row, int step) {
        if (step < 0 || step >= this.edit.steps()) return;
        if (row == 0) {
            boolean any = false;
            for (int t = 0; t < this.cells.length; t++) if (this.cells[t][step] > 0) any = true;
            if (!any) {
                this.app.setNow("No hits on that step to accent");
                return;
            }
            this.edit.toggleAccent(step);
        } else if (row - 1 < this.edit.tracks()) {
            this.edit.toggle(row - 1, step);
        } else return;
        this.stale = true;
        this.cells = this.edit.cells();
        this.grid.revalidate();
        this.grid.repaint();
        this.showStatus();
    }

    void play() {
        FileSets fs = this.app.fileSets;
        String src = playKey(this.key);
        boolean paused = src.equals(fs.midiSrc) && fs.midiClip != null && fs.midiPaused;
        if (!this.stale && paused) {
            fs.resumeSourceMidi();
            return;
        }
        int from = paused ? (int) (fs.midiClip.getMicrosecondPosition() / 1000) : 0;
        this.stale = false;
        fs.playMidiBytes(src, this.edit.toMidi(), this.name + " (edited)", from);
    }

    /** The playback clock: the playhead follows the editor's own playback. */
    void clock(String src, int ms, boolean going) {
        int at = src != null && src.equals(playKey(this.key)) ? this.edit.stepAtMillis(ms) : -1;
        if (at == this.playhead) return;
        this.playhead = at;
        this.grid.repaint();
        if (at >= 0) this.follow(at);
    }

    /** While it plays, the grid scrolls with the beat: the playhead stays in view, a quarter of the way in. */
    void follow(int step) {
        javax.swing.JViewport port = this.scroll.getViewport();
        int x = this.grid.stepX(Math.min(step, this.edit.steps() - 1));
        int width = port.getExtentSize().width;
        if (width <= 0) return;
        java.awt.Point at = port.getViewPosition();
        if (x >= at.x && x + CELL <= at.x + width * 3 / 4) return;
        int most = Math.max(0, this.grid.getPreferredSize().width - width);
        port.setViewPosition(new java.awt.Point(Math.min(most, Math.max(0, x - width / 4)), at.y));
    }

    void save() {
        if (this.saver != null && this.edit.changed) {
            try {
                this.app.setNow("Saved \u00b7 " + this.saver.save(this.edit.toMidi()));
            } catch (Exception ex) {
                this.app.setNow("Could not save: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            }
        } else if (this.edit.changed) {
            MidiHitEdit.saveToFileSet(this.key, this.edit.toMidi());
            this.app.fileSets.storeAudioDir();
            this.app.setNow("Saved · " + this.name);
        } else this.app.setNow("No changes to save");
        this.close();
        this.toPatterns();
    }

    void back() {
        this.app.setNow(this.edit.changed ? "Back · changes not saved" : "Back");
        this.close();
        this.toPatterns();
    }

    /** After an import: the Pattern view, its file set open there. */
    void toPatterns() {
        if (!this.fromImport) return;
        this.fromImport = false;
        this.app.openPacks.put("f:" + this.key, true);
        this.app.showView("pattern");
        this.app.styleLibrary.refreshLearnedChips();
    }

    void close() {
        if (now == this) now = null;
        if (playKey(this.key).equals(this.app.fileSets.midiSrc)) this.app.fileSets.stopSourceMidi();
        this.dialog.dispose();
    }

    /** The grid, drawn in one component: a song can have hundreds of steps. */
    final class Grid extends JComponent {
        Grid() {
            this.setOpaque(true);
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!Grid.this.contains(e.getPoint())) return;
                    tap(e.getY() / ROW, Grid.this.stepAt(e.getX()));
                }
            });
        }

        int stepX(int step) {
            return step * (CELL + GAP) + (step / edit.barSteps) * GAP;
        }

        int stepAt(int x) {
            int bar = edit.barSteps * (CELL + GAP) + GAP;
            int b = x / bar;
            int in = (x - b * bar) / (CELL + GAP);
            return b * edit.barSteps + Math.min(edit.barSteps - 1, in);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(this.stepX(edit.steps()) + GAP, (edit.tracks() + 1) * ROW);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillRect(0, 0, this.getWidth(), this.getHeight());
            java.awt.Rectangle clip = g.getClipBounds();
            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int perBeat = Math.max(1, Engine.stepsPerBeat(edit.tsDen));
            for (int s = 0; s < edit.steps(); s++) {
                int x = this.stepX(s);
                if (clip != null && (x + CELL < clip.x || x > clip.x + clip.width)) continue;
                boolean beat = s % perBeat == 0;
                boolean head = s == playhead;
                boolean acc = edit.accent(s);
                for (int row = 0; row <= edit.tracks(); row++) {
                    int y = row * ROW + 2;
                    int h = ROW - 4;
                    if (row == 0) {
                        g.setColor(acc ? ACCENT : (head ? FG : ELEV));
                        g.fillRoundRect(x, y, CELL, h, 10, 10);
                        String t = edit.beatLabel(s);
                        if (t.isEmpty()) t = acc ? "▾" : "·";
                        g.setColor(acc || head ? BG : FG);
                        g.drawString(t, x + (CELL - fm.stringWidth(t)) / 2, y + (h + fm.getAscent()) / 2 - 2);
                        continue;
                    }
                    int vel = cells[row - 1][s];
                    Color c = vel > 0 ? (vel >= MidiHitEdit.ACCENT_FROM ? ACCENT : HIT) : (beat ? BEAT : ELEV);
                    if (vel > 0 && vel < MidiHitEdit.ACCENT_FROM) {
                        int a = Math.max(110, Math.min(255, 80 + vel * 175 / 127));
                        c = new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
                    }
                    g.setColor(c);
                    g.fillRoundRect(x, y, CELL, h, 10, 10);
                    if (head) {
                        g.setColor(ACCENT);
                        g.setStroke(new java.awt.BasicStroke(2f));
                        g.drawRoundRect(x + 1, y + 1, CELL - 2, h - 2, 10, 10);
                    }
                }
            }
        }
    }
}
