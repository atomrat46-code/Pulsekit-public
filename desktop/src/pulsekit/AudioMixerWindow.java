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
 * gain (dB), Mute, Solo, ▶ (it alone) and ✕ (taken out); ▶ Play mix and ■ Stop under them. File menu:
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
        JMenuItem mix = new JMenuItem(AudioMix.MIXDOWN);
        mix.setName("audiomixer-mixdown");
        mix.addActionListener(e -> mixdown(app, status));
        JMenuItem clear = new JMenuItem(AudioMix.CLEAR);
        clear.setName("audiomixer-clear");
        clear.addActionListener(e -> {
            stop();
            if (!AudioMix.tracks.isEmpty() && javax.swing.JOptionPane.showConfirmDialog(d, "Take every track out of the AudioMixer?", AudioMix.CLEAR,
                javax.swing.JOptionPane.OK_CANCEL_OPTION) != javax.swing.JOptionPane.OK_OPTION) return;
            AudioMix.clear();
            fill[0].run();
        });
        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(e -> d.dispose());
        file.add(imp);
        file.add(mix);
        file.addSeparator();
        file.add(clear);
        file.add(close);
        bar.add(file);
        d.setJMenuBar(bar);
        JButton play = new JButton("▶ Play mix");
        play.setName("audiomixer-play");
        play.addActionListener(e -> playMix(status));
        JButton stopBtn = new JButton("■ Stop");
        stopBtn.setName("audiomixer-stop");
        stopBtn.addActionListener(e -> stop());
        JPanel transport = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
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
            }
        });
        d.pack();
        d.setLocationRelativeTo(app);
        d.setVisible(true);
    }

    /** One track's row: its name and length, volume, gain, Mute, Solo, ▶ and ✕. */
    static JPanel row(final Pulsekit app, final AudioMix.Track t, final JLabel status, final Runnable[] fill) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        r.setName("audiomixer-track:" + t.name);
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
            AudioMix.remove(t);
            fill[0].run();
        });
        r.add(out);
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

    static void say(JLabel status, String s) {
        lastSaid = s;
        if (status != null) status.setText(s);
    }
}
