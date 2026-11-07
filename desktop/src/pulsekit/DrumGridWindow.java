package pulsekit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;

/**
 * A DrumPad track's MIDI editor (DrumGrid): a bar at a time, its timeline of 16th-note steps over a
 * grid of the 12 pads, and the pads under them. Select a step on the timeline (or ← and →), then press
 * a pad (or its key, 1 to 0, - and =): it plays in that step, or comes out of it when it was there. A
 * cell of the grid does the same. ◀ ▶ go bar to bar; + Bar, Quantize, BPM and time signature, ▶ Play,
 * Export MIDI (into the program files folder). Each change makes the track's sound again.
 */
final class DrumGridWindow {
    private DrumGridWindow() {}

    /** The window shown last, the MIDI file exported last (for the tests). */
    static JDialog last;
    static volatile File lastExport;
    static final Color ON = new Color(0xE0, 0x6C, 0x3C), BEAT = new Color(0x3A, 0x3F, 0x4A), OFF = new Color(0x2A, 0x2E, 0x36), PICKED = new Color(0x4C, 0x8B, 0xF5);

    /** Where the editor is: the bar shown and the step selected (in the bar). */
    static final class Where {
        int bar;
        int step;
    }

    static void show(final Pulsekit app, java.awt.Window owner, final AudioMix.Track t, final Runnable changed) {
        if (last != null && last.isDisplayable()) last.dispose();
        if (DrumPadWindow.last != null && DrumPadWindow.last.isDisplayable()) DrumPadWindow.last.dispose();
        app.playback.fillMissingVoices();
        final DrumPads.Session s = DrumPads.open(t, null);
        Engine.DrumSet set = app.drumSets.get(Math.max(0, Math.min(app.drumSets.size() - 1, app.playback.drumSetIndex)));
        for (Engine.DrumSet k : app.drumSets) if (k.name.equals(s.take.kit)) set = k;
        s.setVoices(DrumPads.atRate(DrumPads.voices(set, app.voices)), set.name);
        final Where w = new Where();
        final JDialog d = new JDialog(owner, DrumGrid.TITLE + ": " + t.name, java.awt.Dialog.ModalityType.MODELESS);
        d.setName("drumgrid");
        last = d;
        final JLabel state = new JLabel();
        state.setName("editor-state");
        final JPanel grid = new JPanel();
        final Runnable[] fill = new Runnable[1];
        // A change: the track's sound made again, the grid shown again.
        final Runnable edited = () -> {
            try {
                s.write();
            } catch (Exception ex) {
                state.setText("Could not write " + t.name + ": " + ex.getMessage());
            }
            fill[0].run();
            if (changed != null) changed.run();
        };
        fill[0] = () -> {
            int per = DrumGrid.stepsPerBar(s.take);
            w.bar = Math.max(0, Math.min(DrumGrid.bars(s.take) - 1, w.bar));
            w.step = Math.max(0, Math.min(per - 1, w.step));
            grid.removeAll();
            grid.setLayout(new GridLayout(Engine.TRACK_ID.length + 1, per + 1, 2, 2));
            grid.add(new JLabel("Step"));
            int beat = Math.max(1, 16 / s.take.unit());
            for (int i = 0; i < per; i++) {
                final int at = i;
                JToggleButton b = new JToggleButton(String.valueOf(i + 1));
                b.setName("editor-step:" + i);
                b.setSelected(i == w.step);
                b.setMargin(new java.awt.Insets(0, 0, 0, 0));
                b.setFocusable(false);
                b.setBackground(i == w.step ? PICKED : i % beat == 0 ? BEAT : OFF);
                b.addActionListener(e -> {
                    w.step = at;
                    fill[0].run();
                });
                grid.add(b);
            }
            for (int p = 0; p < Engine.TRACK_ID.length; p++) {
                final int pad = p;
                grid.add(new JLabel(Engine.TRACK_LABEL[p]));
                for (int i = 0; i < per; i++) {
                    final int at = i;
                    final int step = w.bar * per + i;
                    JToggleButton c = new JToggleButton();
                    c.setName("editor-cell:" + Engine.TRACK_ID[p] + ":" + i);
                    c.setFocusable(false);
                    c.setSelected(DrumGrid.has(s.take, p, step));
                    c.setOpaque(true);
                    c.setBackground(c.isSelected() ? ON : i == w.step ? PICKED.darker() : i % beat == 0 ? BEAT : OFF);
                    c.setPreferredSize(new java.awt.Dimension(26, 20));
                    c.addActionListener(e -> {
                        w.step = at;
                        DrumGrid.toggle(s.take, pad, step);
                        edited.run();
                    });
                    grid.add(c);
                }
            }
            state.setText(DrumGrid.state(s.take, w.bar, w.step));
            grid.revalidate();
            grid.repaint();
        };
        // The bar, + Bar, Quantize, BPM, time signature, ▶ Play, Export MIDI.
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton prev = new JButton("◀");
        prev.setName("editor-bar-prev");
        prev.addActionListener(e -> {
            w.bar = Math.max(0, w.bar - 1);
            fill[0].run();
        });
        JButton next = new JButton("▶");
        next.setName("editor-bar-next");
        next.addActionListener(e -> {
            w.bar = Math.min(DrumGrid.bars(s.take) - 1, w.bar + 1);
            fill[0].run();
        });
        JButton bar = new JButton(DrumGrid.ADD_BAR);
        bar.setName("editor-add-bar");
        bar.addActionListener(e -> {
            DrumGrid.addBar(s.take);
            w.bar = DrumGrid.bars(s.take) - 1;
            edited.run();
        });
        JButton quantize = new JButton(DrumGrid.QUANTIZE);
        quantize.setName("editor-quantize");
        quantize.addActionListener(e -> {
            int moved = DrumGrid.quantize(s.take);
            edited.run();
            state.setText(DrumGrid.state(s.take, w.bar, w.step) + " · " + moved + " moved onto the steps");
        });
        final JSpinner bpm = new JSpinner(new SpinnerNumberModel(s.take.bpm, Engine.MIN_BPM, Engine.MAX_BPM, 1));
        bpm.setName("editor-bpm");
        bpm.addChangeListener(e -> {
            s.take.bpm = ((Number) bpm.getValue()).intValue();
            DrumPadWindow.keep(s);
            fill[0].run();
        });
        final JComboBox<String> ts = new JComboBox<String>(DrumPads.SIGNATURES);
        ts.setName("editor-ts");
        ts.setSelectedItem(s.take.ts);
        ts.addActionListener(e -> {
            s.take.ts = String.valueOf(ts.getSelectedItem());
            DrumPadWindow.keep(s);
            fill[0].run();
        });
        JButton play = new JButton("▶ Play");
        play.setName("editor-play");
        play.addActionListener(e -> {
            float[][] m = DrumPads.render(s.take, s.voices);
            float g = (float) t.factor();
            for (int i = 0; i < m[0].length; i++) {
                m[0][i] *= g;
                m[1][i] *= g;
            }
            AudioMixerWindow.play(m[0], m[1], state, t.name);
        });
        JButton export = new JButton(DrumGrid.EXPORT);
        export.setName("editor-export");
        export.addActionListener(e -> {
            File f = app.pyJav.saveProgramFile(DrumGrid.midiName(t), DrumGrid.toMidi(s.take));
            lastExport = f;
            state.setText(f == null ? "Could not save " + DrumGrid.midiName(t) : "Exported " + f.getPath());
            if (f != null) app.setNow(state.getText());
        });
        top.add(prev);
        top.add(next);
        top.add(bar);
        top.add(quantize);
        top.add(new JLabel("BPM"));
        top.add(bpm);
        top.add(new JLabel("TS"));
        top.add(ts);
        top.add(play);
        top.add(export);
        // The pads: each puts itself in the step selected (and sounds).
        JPanel pads = new JPanel(new GridLayout(2, 6, 4, 4));
        pads.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        for (int i = 0; i < Engine.TRACK_ID.length; i++) {
            final int pad = i;
            JButton b = new JButton("<html><center>" + Engine.TRACK_LABEL[i] + "<br><small>" + DrumPadWindow.KEYS.charAt(i) + "</small></center></html>");
            b.setName("editor-pad:" + Engine.TRACK_ID[i]);
            b.setFocusable(false);
            final Runnable press = () -> {
                s.hit(pad, 1f);
                DrumGrid.toggle(s.take, pad, w.bar * DrumGrid.stepsPerBar(s.take) + w.step);
                edited.run();
            };
            b.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    press.run();
                }
            });
            pads.add(b);
            key(d, String.valueOf(DrumPadWindow.KEYS.charAt(i)), "pad" + i, press);
        }
        key(d, "LEFT", "step-left", () -> {
            w.step--;
            if (w.step < 0 && w.bar > 0) {
                w.bar--;
                w.step = DrumGrid.stepsPerBar(s.take) - 1;
            }
            fill[0].run();
        });
        key(d, "RIGHT", "step-right", () -> {
            w.step++;
            if (w.step >= DrumGrid.stepsPerBar(s.take) && w.bar < DrumGrid.bars(s.take) - 1) {
                w.bar++;
                w.step = 0;
            }
            fill[0].run();
        });
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(state, BorderLayout.SOUTH);
        body.add(north, BorderLayout.NORTH);
        body.add(new javax.swing.JScrollPane(grid), BorderLayout.CENTER);
        body.add(pads, BorderLayout.SOUTH);
        d.setContentPane(body);
        fill[0].run();
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                s.close();
                if (changed != null) changed.run();
            }
        });
        s.play(DrumPadWindow.out());
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    static void key(JDialog d, String stroke, String name, final Runnable run) {
        javax.swing.KeyStroke k = stroke.length() == 1 ? javax.swing.KeyStroke.getKeyStroke(stroke.charAt(0)) : javax.swing.KeyStroke.getKeyStroke(stroke);
        d.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, name);
        d.getRootPane().getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                run.run();
            }
        });
    }
}
