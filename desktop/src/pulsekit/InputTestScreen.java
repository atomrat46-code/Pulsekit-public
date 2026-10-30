package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * File > Sound devices > an input > Test (InputLevel), as on Android: a level meter of what the
 * input hears while the window is open, and Record, Stop, Play and Save (a WAV into the program
 * files folder). The recording level set on Sound devices is applied to what is heard and recorded.
 */
final class InputTestScreen {
    private InputTestScreen() {}

    /** For the tests: the window shown last, what it said last, the samples recorded. */
    static JDialog last;
    static volatile String lastSaid = "";
    static volatile int recordedSamples;

    private static volatile boolean open;
    private static volatile boolean recording;
    private static volatile boolean playing;
    private static short[] recorded = new short[0];
    private static int length;

    static void show(final Pulsekit app, java.awt.Window owner, final SoundDevices.Device d) {
        if (last != null) last.dispose();
        final JDialog dialog = new JDialog(owner, InputLevel.TITLE + ": " + d.name, java.awt.Dialog.ModalityType.MODELESS);
        final JLabel level = new JLabel("Listening…");
        level.setName("input-level");
        final JProgressBar meter = new JProgressBar(0, 1000);
        meter.setName("input-meter");
        final JProgressBar peak = new JProgressBar(0, 1000);
        peak.setName("input-peak");
        final JLabel state = new JLabel("Recording level " + AppSettings.recordLevel() + "%. Nothing recorded yet.");
        state.setName("input-state");
        JButton rec = new JButton(InputLevel.RECORD), stop = new JButton(InputLevel.STOP), play = new JButton(InputLevel.PLAY), save = new JButton(InputLevel.SAVE);
        JButton close = new JButton("Close");
        rec.setName("input-record");
        stop.setName("input-stop");
        play.setName("input-play");
        save.setName("input-save");
        rec.addActionListener(e -> {
            synchronized (InputTestScreen.class) {
                length = 0;
                recorded = new short[InputLevel.RATE * 10];
            }
            playing = false;
            recording = true;
            state.setText("Recording… (Stop ends it)");
        });
        stop.addActionListener(e -> {
            boolean was = recording;
            recording = false;
            playing = false;
            recordedSamples = length;
            state.setText(was ? "Recorded " + InputLevel.length(length) + ": Play listens to it, Save keeps it" : "Stopped");
        });
        play.addActionListener(e -> {
            if (length == 0) {
                state.setText("Record something first");
                return;
            }
            recording = false;
            play(state);
        });
        save.addActionListener(e -> {
            if (length == 0) {
                state.setText("Record something first");
                return;
            }
            short[] pcm;
            synchronized (InputTestScreen.class) {
                pcm = java.util.Arrays.copyOf(recorded, length);
            }
            java.io.File f = app.pyJav.saveProgramFile(InputLevel.fileName(), AudioIo.encodeWav(pcm, InputLevel.RATE));
            lastSaid = f == null ? "Could not save the recording" : "Saved " + f.getPath();
            state.setText(lastSaid);
            app.setNow(lastSaid);
        });
        close.addActionListener(e -> dialog.dispose());
        JPanel meters = new JPanel(new GridLayout(0, 1, 0, 4));
        meters.add(level);
        meters.add(meter);
        meters.add(peak);
        meters.add(state);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (JButton b : new JButton[] {rec, stop, play, save, close}) buttons.add(b);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        content.add(meters, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(520, 220);
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                open = false;
                recording = false;
                playing = false;
            }
        });
        open = true;
        recording = false;
        length = 0;
        recordedSamples = 0;
        last = dialog;
        // Listening once the window is up (modeless: it returns at once).
        dialog.setVisible(true);
        listen(dialog, d, level, meter, peak);
    }

    /** What the input hears, read on a thread of its own while the window is open; the meter about ten times a second. */
    static void listen(final JDialog window, final SoundDevices.Device d, final JLabel level, final JProgressBar meter, final JProgressBar peak) {
        new Thread(() -> {
            TargetDataLine line = null;
            try {
                Mixer.Info mi = SoundOut.find(d.name);
                if (mi == null) throw new IllegalStateException(d.name + " is not connected");
                AudioFormat f = new AudioFormat(InputLevel.RATE, 16, 1, true, false);
                line = (TargetDataLine) AudioSystem.getMixer(mi).getLine(new DataLine.Info(TargetDataLine.class, f));
                line.open(f);
                line.start();
                byte[] raw = new byte[InputLevel.RATE / 10 * 2];
                short[] buf = new short[raw.length / 2];
                // Ended with its window (closed, or another Test opened).
                while (open && last == window && window.isDisplayable()) {
                    int got = line.read(raw, 0, raw.length);
                    int n = got / 2;
                    for (int i = 0; i < n; i++) buf[i] = (short) ((raw[2 * i] & 0xff) | (raw[2 * i + 1] << 8));
                    InputLevel.gain(buf, n, AppSettings.recordLevel());
                    if (recording) keep(buf, n);
                    final double[] lv = InputLevel.levels(buf, n);
                    final String text = InputLevel.text(lv) + (recording ? " · recording " + InputLevel.length(length) : "");
                    lastSaid = text;
                    SwingUtilities.invokeLater(() -> {
                        level.setText(text);
                        meter.setValue((int) Math.round(InputLevel.meter(lv[0]) * 1000));
                        peak.setValue((int) Math.round(InputLevel.meter(lv[1]) * 1000));
                    });
                }
            } catch (Throwable ex) {
                final String text = "Could not listen to " + d.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                lastSaid = text;
                SwingUtilities.invokeLater(() -> level.setText(text));
                SoundDevices.log("Input test: " + d.name, text);
            } finally {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            }
        }, "pulsekit-input-test").start();
    }

    private static synchronized void keep(short[] buf, int n) {
        int max = InputLevel.RATE * InputLevel.MAX_SECONDS;
        if (length + n > max) {
            recording = false;
            n = max - length;
        }
        if (length + n > recorded.length) recorded = java.util.Arrays.copyOf(recorded, Math.min(max, Math.max(length + n, recorded.length * 2)));
        System.arraycopy(buf, 0, recorded, length, n);
        length += n;
        recordedSamples = length;
    }

    /** The recording played through Pulsekit's output, at its playback volume. */
    static void play(final JLabel state) {
        final short[] pcm;
        synchronized (InputTestScreen.class) {
            pcm = java.util.Arrays.copyOf(recorded, length);
        }
        playing = true;
        state.setText("Playing " + InputLevel.length(pcm.length) + "…");
        new Thread(() -> {
            String said;
            try {
                SoundOut.apply();
                AudioFormat f = new AudioFormat(InputLevel.RATE, 16, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(f);
                line.open(f);
                SoundOut.volume(line);
                line.start();
                byte[] raw = new byte[8192];
                for (int at = 0; at < pcm.length && playing; ) {
                    int n = Math.min(raw.length / 2, pcm.length - at);
                    for (int i = 0; i < n; i++) {
                        raw[2 * i] = (byte) pcm[at + i];
                        raw[2 * i + 1] = (byte) (pcm[at + i] >> 8);
                    }
                    line.write(raw, 0, n * 2);
                    at += n;
                }
                if (playing) line.drain();
                line.close();
                said = "Played " + InputLevel.length(pcm.length);
            } catch (Throwable ex) {
                said = "Could not play: " + ex.getMessage();
            }
            playing = false;
            final String shown = said;
            SwingUtilities.invokeLater(() -> state.setText(shown));
        }, "pulsekit-input-play").start();
    }
}
