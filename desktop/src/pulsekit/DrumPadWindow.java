package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * A DrumPad track's Drumpad window (DrumPads): the kit (any drum set), BPM, time signature and
 * Metronome, the track's volume and gain, the 12 pads (a mouse press, or the keys 1 to 0, - and =), and
 * ● Record, ❚❚ Pause, ■ Stop and ▶ Play. The pads sound through Pulsekit's output.
 */
final class DrumPadWindow {
    private DrumPadWindow() {}

    /** The window shown last (for the tests). */
    static JDialog last;
    static final String KEYS = "1234567890-=";

    static void show(final Pulsekit app, java.awt.Window owner, final AudioMix.Track t, final Runnable changed) {
        if (last != null && last.isDisplayable()) last.dispose();
        if (DrumGridWindow.last != null && DrumGridWindow.last.isDisplayable()) DrumGridWindow.last.dispose();
        app.playback.fillMissingVoices();
        final DrumPads.Session s = DrumPads.open(t, null);
        int kitAt = app.playback.drumSetIndex;
        for (int i = 0; i < app.drumSets.size(); i++) if (app.drumSets.get(i).name.equals(s.take.kit)) kitAt = i;
        kitAt = Math.max(0, Math.min(app.drumSets.size() - 1, kitAt));
        Engine.DrumSet set = app.drumSets.get(kitAt);
        s.setVoices(DrumPads.atRate(DrumPads.voices(set, app.voices)), set.name);
        final JDialog d = new JDialog(owner, DrumPads.TITLE + ": " + t.name, java.awt.Dialog.ModalityType.MODELESS);
        d.setName("drumpad");
        last = d;
        final JLabel status = new JLabel(s.state());
        status.setName("drumpad-status");
        // The kit, BPM, time signature, Metronome.
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        top.add(new JLabel("Kit"));
        String[] kits = new String[app.drumSets.size()];
        for (int i = 0; i < kits.length; i++) kits[i] = app.drumSets.get(i).name;
        final JComboBox<String> kit = new JComboBox<String>(kits);
        kit.setName("drumpad-kit");
        kit.setSelectedIndex(kitAt);
        kit.addActionListener(e -> {
            Engine.DrumSet k = app.drumSets.get(Math.max(0, kit.getSelectedIndex()));
            s.setVoices(DrumPads.atRate(DrumPads.voices(k, app.voices)), k.name);
            write(s, status, changed);
        });
        top.add(kit);
        top.add(new JLabel("BPM"));
        final JSpinner bpm = new JSpinner(new SpinnerNumberModel(s.take.bpm, Engine.MIN_BPM, Engine.MAX_BPM, 1));
        bpm.setName("drumpad-bpm");
        bpm.addChangeListener(e -> {
            s.take.bpm = ((Number) bpm.getValue()).intValue();
            keep(s);
        });
        top.add(bpm);
        top.add(new JLabel("TS"));
        final JComboBox<String> ts = new JComboBox<String>(DrumPads.SIGNATURES);
        ts.setName("drumpad-ts");
        ts.setSelectedItem(s.take.ts);
        ts.addActionListener(e -> {
            s.take.ts = String.valueOf(ts.getSelectedItem());
            keep(s);
        });
        top.add(ts);
        final JToggleButton metro = new JToggleButton(DrumPads.METRONOME);
        metro.setName("drumpad-metronome");
        metro.addActionListener(e -> s.metronome(metro.isSelected()));
        top.add(metro);
        // The track's volume and gain.
        JPanel levels = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        levels.add(new JLabel("Volume"));
        final JSlider vol = new JSlider(0, AudioMix.MAX_VOLUME, t.volume);
        vol.setName("drumpad-volume");
        vol.addChangeListener(e -> {
            t.volume = vol.getValue();
            if (!vol.getValueIsAdjusting()) {
                AudioMix.save();
                if (changed != null) changed.run();
            }
        });
        levels.add(vol);
        levels.add(new JLabel("Gain dB"));
        final JSpinner gain = new JSpinner(new SpinnerNumberModel(t.gain, AudioMix.MIN_GAIN, AudioMix.MAX_GAIN, 1));
        gain.setName("drumpad-gain");
        gain.addChangeListener(e -> {
            t.gain = ((Number) gain.getValue()).intValue();
            AudioMix.save();
            if (changed != null) changed.run();
        });
        levels.add(gain);
        // The pads.
        JPanel pads = new JPanel(new GridLayout(3, 4, 6, 6));
        pads.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        for (int i = 0; i < Engine.TRACK_ID.length; i++) {
            final int pad = i;
            final JButton b = new JButton("<html><center>" + Engine.TRACK_LABEL[i] + "<br><small>" + KEYS.charAt(i) + "</small></center></html>");
            b.setName("drumpad-pad:" + Engine.TRACK_ID[i]);
            b.setFocusable(false);
            b.setPreferredSize(new java.awt.Dimension(110, 64));
            b.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    s.hit(pad, 1f);
                }
            });
            pads.add(b);
            String key = "pad" + i;
            d.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(KEYS.charAt(i)), key);
            d.getRootPane().getActionMap().put(key, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    s.hit(pad, 1f);
                }
            });
        }
        // ● Record, ❚❚ Pause, ■ Stop, ▶ Play.
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton rec = new JButton(TrackRecorder.RECORD);
        rec.setName("drumpad-record");
        rec.setFocusable(false);
        rec.addActionListener(e -> {
            AudioMixerWindow.stop();
            status.setText(s.record());
        });
        JButton pause = new JButton(TrackRecorder.PAUSE);
        pause.setName("drumpad-pause");
        pause.setFocusable(false);
        pause.addActionListener(e -> {
            try {
                status.setText(s.pause());
            } catch (Exception ex) {
                status.setText("Could not write " + t.name + ": " + ex.getMessage());
            }
            if (changed != null) changed.run();
        });
        JButton stop = new JButton(TrackRecorder.STOP);
        stop.setName("drumpad-stop");
        stop.setFocusable(false);
        stop.addActionListener(e -> {
            AudioMixerWindow.stop();
            if (!s.recording()) return;
            try {
                status.setText(s.stop());
            } catch (Exception ex) {
                status.setText("Could not write " + t.name + ": " + ex.getMessage());
            }
            if (changed != null) changed.run();
        });
        JButton play = new JButton("▶ Play");
        play.setName("drumpad-play");
        play.setFocusable(false);
        play.addActionListener(e -> {
            float[][] m = DrumPads.render(s.take, s.voices);
            float g = (float) t.factor();
            for (int i = 0; i < m[0].length; i++) {
                m[0][i] *= g;
                m[1][i] *= g;
            }
            AudioMixerWindow.play(m[0], m[1], status, t.name);
        });
        transport.add(rec);
        transport.add(pause);
        transport.add(stop);
        transport.add(play);
        JPanel north = new JPanel();
        north.setLayout(new javax.swing.BoxLayout(north, javax.swing.BoxLayout.Y_AXIS));
        north.add(top);
        north.add(levels);
        JPanel south = new JPanel(new BorderLayout());
        south.add(transport, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        body.add(north, BorderLayout.NORTH);
        body.add(pads, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        d.setContentPane(body);
        // The state while open (how long, how many hits).
        final javax.swing.Timer tick = new javax.swing.Timer(200, e -> {
            if (s.recording()) status.setText(s.state());
        });
        tick.start();
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                tick.stop();
                s.close();
                if (changed != null) changed.run();
            }
        });
        s.play(out());
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    static void keep(DrumPads.Session s) {
        try {
            DrumPads.save(s.track, s.take);
        } catch (Exception ignored) {
            // kept for this session
        }
    }

    /** The track's sound made again (another kit). */
    static void write(DrumPads.Session s, JLabel status, Runnable changed) {
        try {
            s.write();
            status.setText(s.state());
        } catch (Exception ex) {
            status.setText("Could not write " + s.track.name + ": " + ex.getMessage());
        }
        if (changed != null) SwingUtilities.invokeLater(changed);
    }

    /** Pulsekit's output for the pads (a short buffer, so a press sounds at once); null when there is none. */
    static DrumPads.Out out() {
        try {
            SoundOut.apply();
            javax.sound.sampled.AudioFormat f = new javax.sound.sampled.AudioFormat(AudioMix.RATE, 16, 2, true, false);
            final javax.sound.sampled.SourceDataLine line = javax.sound.sampled.AudioSystem.getSourceDataLine(f);
            line.open(f, DrumPads.BLOCK * 4 * 4);
            SoundOut.volume(line);
            line.start();
            return new DrumPads.Out() {
                final byte[] raw = new byte[DrumPads.BLOCK * 4];

                public void write(short[] lr, int frames) {
                    for (int i = 0; i < frames * 2; i++) {
                        raw[2 * i] = (byte) lr[i];
                        raw[2 * i + 1] = (byte) (lr[i] >> 8);
                    }
                    line.write(raw, 0, frames * 4);
                }

                public void close() {
                    line.stop();
                    line.close();
                }
            };
        } catch (Throwable ex) {
            return null;
        }
    }
}
