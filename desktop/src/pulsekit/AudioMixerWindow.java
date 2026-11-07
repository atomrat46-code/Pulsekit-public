package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * AudioMixer (File > AudioMixer, or PyJav's Java list): the tracks, each with its volume (0 to 150%),
 * gain (dB), Mute, Solo, ▶ (it alone) and ✕ (taken out), and its input with ● Record, ❚❚ Pause and
 * ■ Stop (TrackRecorder), or a DrumPad track's Drumpad button (DrumPadWindow); + New empty track,
 * + New DrumPad track, ▶ Play mix and ■ Stop under them. File menu:
 * Import new track… (a WAV, an MP3, or a drum MIDI played with the kit), Mixdown (the mix as a WAV in
 * the program files folder, and in the prompt library with Drum Midi Settings' "Write AudioMixer
 * output tracks into db"), New project. The tracks stay in ~/.pulsekit/audiomixer until cleared.
 */
final class AudioMixerWindow {
    private AudioMixerWindow() {}

    /** The window shown last, and the mixdown saved last (for the tests). */
    static JDialog last;
    static volatile File lastMixdown;
    static volatile String lastSaid;
    /** The tests' file to import (instead of the chooser). */
    static File importNext;

    static javax.sound.sampled.Clip playing;

    static File folder() {
        return new File(PromptDb.dir(), "audiomixer");
    }

    static void show(final Pulsekit app) {
        if (last != null && last.isDisplayable()) {
            last.toFront();
            return;
        }
        AudioMix.open(folder());
        final JDialog d = new JDialog(app, AudioMix.TITLE, false);
        d.setName("audiomixer");
        last = d;
        final JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setName("audiomixer-tracks");
        final JLabel status = new JLabel(" ");
        status.setName("audiomixer-status");
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            rows.removeAll();
            if (AudioMix.tracks.isEmpty()) {
                JLabel none = new JLabel("No tracks yet: File > " + AudioMix.IMPORT);
                none.setName("audiomixer-empty");
                rows.add(none);
            }
            for (final AudioMix.Track t : new java.util.ArrayList<AudioMix.Track>(AudioMix.tracks)) rows.add(row(app, t, status, fill));
            rows.revalidate();
            rows.repaint();
        };
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.setName("audiomixer-file");
        JMenuItem imp = new JMenuItem(AudioMix.IMPORT);
        imp.setName("audiomixer-import");
        imp.addActionListener(e -> importTrack(app, d, status, fill[0]));
        JMenuItem empty = new JMenuItem(AudioMix.NEW_EMPTY);
        empty.setName("audiomixer-new-empty-item");
        empty.addActionListener(e -> newEmpty(status, fill[0]));
        JMenuItem padsItem = new JMenuItem(DrumPads.NEW);
        padsItem.setName("audiomixer-new-pads-item");
        padsItem.addActionListener(e -> newPads(status, fill[0]));
        JMenuItem mix = new JMenuItem(AudioMix.MIXDOWN);
        mix.setName("audiomixer-mixdown");
        mix.addActionListener(e -> mixdown(app, status));
        JMenuItem clear = new JMenuItem(AudioMix.CLEAR);
        clear.setName("audiomixer-clear");
        clear.addActionListener(e -> {
            stop();
            TrackRecorder.stop();
            if (DrumPadWindow.last != null) DrumPadWindow.last.dispose();
            if (!AudioMix.tracks.isEmpty() && javax.swing.JOptionPane.showConfirmDialog(d, "Take every track out of the AudioMixer?", AudioMix.CLEAR,
                javax.swing.JOptionPane.OK_CANCEL_OPTION) != javax.swing.JOptionPane.OK_OPTION) return;
            AudioMix.clear();
            fill[0].run();
        });
        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(e -> d.dispose());
        file.add(imp);
        file.add(empty);
        file.add(padsItem);
        file.add(mix);
        file.addSeparator();
        file.add(clear);
        file.add(close);
        bar.add(file);
        d.setJMenuBar(bar);
        JButton newEmpty = new JButton("+ " + AudioMix.NEW_EMPTY);
        newEmpty.setName("audiomixer-new-empty");
        newEmpty.addActionListener(e -> newEmpty(status, fill[0]));
        JButton newPads = new JButton("+ " + DrumPads.NEW);
        newPads.setName("audiomixer-new-pads");
        newPads.addActionListener(e -> newPads(status, fill[0]));
        JButton play = new JButton("▶ Play mix");
        play.setName("audiomixer-play");
        play.addActionListener(e -> playMix(status));
        JButton stopBtn = new JButton("■ Stop");
        stopBtn.setName("audiomixer-stop");
        stopBtn.addActionListener(e -> stop());
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        transport.add(newEmpty);
        transport.add(newPads);
        transport.add(play);
        transport.add(stopBtn);
        transport.add(status);
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(rows);
        scroll.setPreferredSize(new java.awt.Dimension(760, 320));
        body.add(scroll, BorderLayout.CENTER);
        body.add(transport, BorderLayout.SOUTH);
        d.setContentPane(body);
        fill[0].run();
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                stop();
                TrackRecorder.stop();
                if (DrumPadWindow.last != null) DrumPadWindow.last.dispose();
            }
        });
        d.pack();
        d.setLocationRelativeTo(app);
        d.setVisible(true);
    }

    /** One track's row: its name and length, volume, gain, Mute, Solo, ▶ and ✕. */
    static JPanel row(final Pulsekit app, final AudioMix.Track t, final JLabel status, final Runnable[] fill) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setName("audiomixer-track:" + t.name);
        box.setAlignmentX(0.0f);
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setAlignmentX(0.0f);
        final JLabel name = new JLabel(AudioMix.line(t));
        name.setPreferredSize(new java.awt.Dimension(260, name.getPreferredSize().height));
        r.add(name);
        r.add(new JLabel("Volume"));
        final JSlider vol = new JSlider(0, AudioMix.MAX_VOLUME, t.volume);
        vol.setName("audiomixer-volume:" + t.name);
        vol.setPreferredSize(new java.awt.Dimension(120, vol.getPreferredSize().height));
        vol.addChangeListener(e -> {
            t.volume = vol.getValue();
            name.setText(AudioMix.line(t));
            if (!vol.getValueIsAdjusting()) AudioMix.save();
        });
        r.add(vol);
        r.add(new JLabel("Gain dB"));
        final JSpinner gain = new JSpinner(new SpinnerNumberModel(t.gain, AudioMix.MIN_GAIN, AudioMix.MAX_GAIN, 1));
        gain.setName("audiomixer-gain:" + t.name);
        gain.addChangeListener(e -> {
            t.gain = ((Number) gain.getValue()).intValue();
            name.setText(AudioMix.line(t));
            AudioMix.save();
        });
        r.add(gain);
        final JCheckBox mute = new JCheckBox("Mute", t.mute);
        mute.setName("audiomixer-mute:" + t.name);
        mute.addActionListener(e -> {
            t.mute = mute.isSelected();
            name.setText(AudioMix.line(t));
            AudioMix.save();
        });
        r.add(mute);
        final JCheckBox solo = new JCheckBox("Solo", t.solo);
        solo.setName("audiomixer-solo:" + t.name);
        solo.addActionListener(e -> {
            t.solo = solo.isSelected();
            name.setText(AudioMix.line(t));
            AudioMix.save();
        });
        r.add(solo);
        JButton one = new JButton("▶");
        one.setName("audiomixer-play:" + t.name);
        one.setToolTipText("This track alone");
        one.addActionListener(e -> {
            try {
                AudioIo.StereoPcm p = AudioMix.parseStereo(Files.readAllBytes(new File(AudioMix.dir, t.file).toPath()));
                float g = (float) (t.volume / 100.0 * Math.pow(10, t.gain / 20.0));
                float[] l = p.left.clone(), rr = p.right.clone();
                for (int i = 0; i < l.length; i++) {
                    l[i] = Math.max(-1, Math.min(1, l[i] * g));
                    rr[i] = Math.max(-1, Math.min(1, rr[i] * g));
                }
                play(l, rr, status, t.name);
            } catch (Exception ex) {
                status.setText("Could not play " + t.name);
            }
        });
        r.add(one);
        JButton out = new JButton("✕");
        out.setName("audiomixer-remove:" + t.name);
        out.setToolTipText("Take this track out");
        out.addActionListener(e -> {
            stop();
            if (TrackRecorder.recording() == t) TrackRecorder.stop();
            if (DrumPads.current != null && DrumPads.current.track == t && DrumPadWindow.last != null) DrumPadWindow.last.dispose();
            AudioMix.remove(t);
            fill[0].run();
        });
        r.add(out);
        box.add(r);
        box.add(t.pads ? padsRow(app, t, name) : recordRow(t, name, status));
        return box;
    }

    /** A DrumPad track's Drumpad button (its pads, kit, metronome and recording). */
    static JPanel padsRow(final Pulsekit app, final AudioMix.Track t, final JLabel name) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setAlignmentX(0.0f);
        JButton pads = new JButton(DrumPads.BUTTON);
        pads.setName("audiomixer-drumpad:" + t.name);
        pads.addActionListener(e -> {
            stop();
            TrackRecorder.stop();
            DrumPadWindow.show(app, last, t, () -> name.setText(AudioMix.line(t)));
        });
        r.add(pads);
        return r;
    }

    /** The track's input and ● Record, ❚❚ Pause, ■ Stop, with what the input hears. */
    static JPanel recordRow(final AudioMix.Track t, final JLabel name, final JLabel status) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setAlignmentX(0.0f);
        r.add(new JLabel("Input"));
        final String[][] in = TrackRecorder.choices();
        final javax.swing.JComboBox<String> pick = new javax.swing.JComboBox<String>(in[0]);
        pick.setName("audiomixer-input:" + t.name);
        int at = java.util.Arrays.asList(in[1]).indexOf(t.input);
        pick.setSelectedIndex(Math.max(0, at));
        pick.addActionListener(e -> {
            t.input = in[1][Math.max(0, pick.getSelectedIndex())];
            AudioMix.save();
        });
        r.add(pick);
        final JLabel heard = new JLabel(" ");
        heard.setName("audiomixer-heard:" + t.name);
        JButton rec = new JButton(TrackRecorder.RECORD);
        rec.setName("audiomixer-record:" + t.name);
        rec.addActionListener(e -> {
            stop();
            say(status, TrackRecorder.record(t, text -> SwingUtilities.invokeLater(() -> {
                heard.setText(text);
                name.setText(AudioMix.line(t));
            })));
        });
        JButton pause = new JButton(TrackRecorder.PAUSE);
        pause.setName("audiomixer-pause:" + t.name);
        pause.addActionListener(e -> {
            if (TrackRecorder.recording() != t) return;
            say(status, TrackRecorder.pause());
            name.setText(AudioMix.line(t));
        });
        JButton stopRec = new JButton(TrackRecorder.STOP);
        stopRec.setName("audiomixer-record-stop:" + t.name);
        stopRec.addActionListener(e -> {
            stop();
            if (TrackRecorder.recording() != t) return;
            say(status, TrackRecorder.stop());
            heard.setText(" ");
            name.setText(AudioMix.line(t));
        });
        r.add(rec);
        r.add(pause);
        r.add(stopRec);
        r.add(heard);
        return r;
    }

    /** Import new track…: a WAV, an MP3, or a drum MIDI (played with the kit), read off the Swing thread. */
    static void importTrack(final Pulsekit app, JDialog d, final JLabel status, final Runnable fill) {
        File f = importNext;
        importNext = null;
        if (f == null) {
            JFileChooser c = new JFileChooser(MediaBrowser.downloads());
            c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("WAV, MP3, MIDI", "wav", "wave", "mp3", "mid", "midi"));
            if (c.showOpenDialog(d) != JFileChooser.APPROVE_OPTION) return;
            f = c.getSelectedFile();
        }
        add(app, f, status, fill);
    }

    /** `f` added as a track (off the Swing thread); the rows made again. */
    static void add(final Pulsekit app, final File f, final JLabel status, final Runnable fill) {
        if (!AudioMix.importable(f.getName())) {
            say(status, f.getName() + " is not a WAV, an MP3 or a MIDI file");
            return;
        }
        say(status, "Adding " + f.getName() + "…");
        new Thread(() -> {
            String said;
            try {
                short[][] kit = app.playback == null ? null : app.playback.mixVoices();
                AudioMix.Track t = AudioMix.add(f.getName(), Files.readAllBytes(f.toPath()), kit);
                said = "Added " + AudioMix.line(t);
            } catch (Exception ex) {
                said = "Could not add " + f.getName() + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final String shown = said;
            SwingUtilities.invokeLater(() -> {
                say(status, shown);
                if (fill != null) fill.run();
            });
        }, "pulsekit-mixer-add").start();
    }

    /** Mixdown: the mix as a WAV in the program files folder (and in the library when the setting says so). */
    static void mixdown(final Pulsekit app, final JLabel status) {
        if (AudioMix.heard().isEmpty()) {
            say(status, "Nothing to mix: add a track (or unmute one)");
            return;
        }
        say(status, "Mixing down…");
        new Thread(() -> {
            String said;
            File saved = null;
            try {
                StringBuilder scaled = new StringBuilder();
                byte[] wav = AudioMix.mixdown(scaled);
                String name = AudioMix.mixdownName();
                saved = app.pyJav.saveProgramFile(name, wav);
                if (saved == null) throw new java.io.IOException("the program files folder took no file");
                said = "Mixdown: " + saved.getPath() + (scaled.length() > 0 ? " (" + scaled + ")" : "");
                if (AudioMix.toDb) said += "; " + MediaDir.addToDb(PromptDb.dir(), saved.getName(), wav, true);
            } catch (Exception ex) {
                said = "Could not mix down: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            lastMixdown = saved;
            lastSaid = said;
            final String shown = said;
            SwingUtilities.invokeLater(() -> {
                say(status, shown);
                app.setNow(shown);
            });
        }, "pulsekit-mixdown").start();
    }

    /** New empty track: a track with no sound, for ● Record. */
    static void newEmpty(JLabel status, Runnable fill) {
        try {
            AudioMix.Track t = AudioMix.addEmpty();
            say(status, "Added " + t.name + ": pick its input, then " + TrackRecorder.RECORD);
        } catch (Exception ex) {
            say(status, "Could not add a track: " + ex.getMessage());
        }
        if (fill != null) fill.run();
    }

    /** New DrumPad track: a track for the Drumpad window's hits. */
    static void newPads(JLabel status, Runnable fill) {
        try {
            AudioMix.Track t = AudioMix.addPads();
            say(status, "Added " + t.name + ": open its " + DrumPads.BUTTON);
        } catch (Exception ex) {
            say(status, "Could not add a track: " + ex.getMessage());
        }
        if (fill != null) fill.run();
    }

    static void playMix(final JLabel status) {
        new Thread(() -> {
            try {
                float[][] m = AudioMix.mix(null);
                SwingUtilities.invokeLater(() -> play(m[0], m[1], status, "the mix"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> say(status, "Could not mix: " + ex.getMessage()));
            }
        }, "pulsekit-mixer-play").start();
    }

    static void play(float[] l, float[] r, JLabel status, String what) {
        stop();
        if (l.length == 0) {
            say(status, "Nothing to play");
            return;
        }
        try {
            byte[] wav = AudioMix.encodeStereo(l, r, AudioMix.RATE);
            javax.sound.sampled.Clip c = PromptDb.clips.call();
            c.open(new javax.sound.sampled.AudioFormat(AudioMix.RATE, 16, 2, true, false), wav, 44, wav.length - 44);
            SoundOut.volume(c);
            c.start();
            playing = c;
            say(status, "Playing " + what);
        } catch (Exception ex) {
            say(status, "No audio output for " + what);
        }
    }

    static void stop() {
        javax.sound.sampled.Clip c = playing;
        playing = null;
        if (c != null) {
            try {
                c.stop();
                c.close();
            } catch (Exception ignored) {
                // closed
            }
        }
    }

    /** The computer's audio inputs for TrackRecorder (javax.sound: a TargetDataLine, 44.1 kHz mono). */
    static TrackRecorder.Inputs inputs() {
        return new TrackRecorder.Inputs() {
            public java.util.List<SoundDevices.Device> list() {
                return SoundDevices.of(SoundOut.list(), SoundDevices.IN);
            }

            public TrackRecorder.Input open(String key) throws Exception {
                javax.sound.sampled.AudioFormat f = new javax.sound.sampled.AudioFormat(AudioMix.RATE, 16, 1, true, false);
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(javax.sound.sampled.TargetDataLine.class, f);
                javax.sound.sampled.TargetDataLine line = null;
                if (key != null && !key.isEmpty()) {
                    for (SoundDevices.Device d : list()) {
                        if (!d.key().equals(key)) continue;
                        javax.sound.sampled.Mixer.Info mi = SoundOut.find(d.name);
                        if (mi == null) throw new IllegalStateException(d.name + " is not connected");
                        line = (javax.sound.sampled.TargetDataLine) javax.sound.sampled.AudioSystem.getMixer(mi).getLine(info);
                    }
                    if (line == null) throw new IllegalStateException("the input is not connected");
                } else {
                    line = (javax.sound.sampled.TargetDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                }
                line.open(f);
                line.start();
                final javax.sound.sampled.TargetDataLine open = line;
                return new TrackRecorder.Input() {
                    byte[] raw = new byte[0];

                    public int read(short[] buf) {
                        if (raw.length != buf.length * 2) raw = new byte[buf.length * 2];
                        int n = open.read(raw, 0, raw.length) / 2;
                        for (int i = 0; i < n; i++) buf[i] = (short) ((raw[2 * i] & 0xff) | (raw[2 * i + 1] << 8));
                        return n;
                    }

                    public void close() {
                        open.stop();
                        open.close();
                    }
                };
            }
        };
    }

    static void say(JLabel status, String s) {
        lastSaid = s;
        if (status != null) status.setText(s);
    }
}
