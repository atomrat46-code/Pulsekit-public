package pulsekit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

/**
 * A WAV or MP3 played by Java Sound, the way Android plays them: no VLC, no window of its own (the
 * playlist viewer shows the sound's waveform; WAV ready a small player). The file is decoded whole
 * (MP3 by the bundled decoder) into 16-bit stereo PCM, at its own rate.
 */
final class SoundClip implements MediaPlayer {
    /** Longer sounds are not decoded whole (about 20 minutes of 44.1 kHz stereo). */
    static final long MAX_BYTES = 200L * 1024 * 1024;

    private Clip clip;
    private boolean ended;
    private boolean muted;
    private int percent = 100;
    /** Tests only (no sound device in the test machine): a clock stands in for the clip. */
    private long quietLength = -1, quietFrom, quietAt;
    private boolean quietRunning;

    SoundClip(File f) throws Exception {
        byte[] pcm;
        AudioFormat format;
        String low = f.getName().toLowerCase(java.util.Locale.ROOT);
        if (low.endsWith(".mp3")) {
            AudioIo.StereoPcm s = Mp3Decode.parseStereo(Files.readAllBytes(f.toPath()));
            pcm = interleave(s.left, s.right);
            format = new AudioFormat(s.sr, 16, 2, true, false);
        } else {
            AudioInputStream in = AudioSystem.getAudioInputStream(f);
            AudioFormat from = in.getFormat();
            format = new AudioFormat(from.getSampleRate(), 16, Math.min(2, Math.max(1, from.getChannels())), true, false);
            AudioInputStream as16 = AudioSystem.getAudioInputStream(format, in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            for (int n; (n = as16.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new java.io.IOException("too long to play here");
            }
            pcm = out.toByteArray();
        }
        if (System.getProperty("pulsekit.test.dir") != null && AudioSystem.getMixerInfo().length == 0) {
            this.quietLength = pcm.length / format.getFrameSize() * 1000L / Math.max(1, (long) format.getSampleRate());
            return;
        }
        Clip c = AudioSystem.getClip();
        c.open(new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / format.getFrameSize()));
        SoundOut.volume(c);
        c.addLineListener(e -> {
            if (e.getType() == javax.sound.sampled.LineEvent.Type.STOP && c.getFramePosition() >= c.getFrameLength() - 1) this.ended = true;
        });
        this.clip = c;
    }

    /** Whether a file is one this plays (WAV, MP3). */
    static boolean plays(File f) {
        String low = f.getName().toLowerCase(java.util.Locale.ROOT);
        return low.endsWith(".wav") || low.endsWith(".wave") || low.endsWith(".mp3");
    }

    private static byte[] interleave(float[] left, float[] right) {
        int n = left.length;
        byte[] out = new byte[n * 4];
        for (int i = 0; i < n; i++) {
            int l = clamp(left[i]), r = clamp(i < right.length ? right[i] : left[i]);
            out[i * 4] = (byte) l;
            out[i * 4 + 1] = (byte) (l >> 8);
            out[i * 4 + 2] = (byte) r;
            out[i * 4 + 3] = (byte) (r >> 8);
        }
        return out;
    }

    private static int clamp(float v) {
        return Math.max(-32768, Math.min(32767, Math.round(v * 32767f)));
    }

    @Override
    public void play() {
        if (this.quietLength >= 0) {
            if (this.quiet() >= this.quietLength) this.quietAt = 0;
            this.quietFrom = System.currentTimeMillis();
            this.quietRunning = true;
            return;
        }
        Clip c = this.clip;
        if (c == null) return;
        // After the end (or Stop) Play starts again from the beginning.
        if (this.ended || c.getFramePosition() >= c.getFrameLength() - 1) c.setFramePosition(0);
        this.ended = false;
        c.start();
    }

    @Override
    public void pause() {
        if (this.quietLength >= 0) {
            this.quietAt = this.quiet();
            this.quietRunning = false;
        }
        if (this.clip != null) this.clip.stop();
    }

    @Override
    public void stop() {
        this.quietAt = 0;
        this.quietRunning = false;
        Clip c = this.clip;
        if (c == null) return;
        c.stop();
        c.setFramePosition(0);
        this.ended = false;
    }

    @Override
    public boolean playing() {
        if (this.quietLength >= 0) return this.quietRunning && this.quiet() < this.quietLength;
        return this.clip != null && this.clip.isRunning();
    }

    @Override
    public int state() {
        if (this.quietLength >= 0) return this.quietRunning && this.quiet() >= this.quietLength ? VlcPlayer.ENDED : this.playing() ? 3 : 4;
        return this.ended ? VlcPlayer.ENDED : this.playing() ? 3 : 4;
    }

    @Override
    public long timeMs() {
        if (this.quietLength >= 0) return Math.min(this.quietLength, this.quiet());
        return this.clip == null ? 0 : this.clip.getMicrosecondPosition() / 1000;
    }

    @Override
    public long lengthMs() {
        if (this.quietLength >= 0) return this.quietLength;
        return this.clip == null ? 0 : this.clip.getMicrosecondLength() / 1000;
    }

    private long quiet() {
        return this.quietAt + (this.quietRunning ? System.currentTimeMillis() - this.quietFrom : 0);
    }

    /** Moves to a place (0..1 of its length). */
    void seek(double part) {
        part = Math.max(0, Math.min(1, part));
        if (this.quietLength >= 0) {
            this.quietAt = (long) (this.quietLength * part);
            this.quietFrom = System.currentTimeMillis();
            return;
        }
        if (this.clip != null) this.clip.setMicrosecondPosition((long) (this.clip.getMicrosecondLength() * part));
    }

    @Override
    public void volume(int percent) {
        this.percent = Math.max(0, Math.min(100, percent));
        this.gain();
    }

    /** A sound plays at its own speed. */
    @Override
    public void rate(double speed) {
    }

    @Override
    public void mute(boolean on) {
        this.muted = on;
        this.gain();
    }

    private void gain() {
        Clip c = this.clip;
        if (c == null) return;
        if (c.isControlSupported(BooleanControl.Type.MUTE)) ((BooleanControl) c.getControl(BooleanControl.Type.MUTE)).setValue(this.muted);
        if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
            float db = this.muted || this.percent == 0 ? g.getMinimum() : (float) (20 * Math.log10(this.percent / 100.0));
            g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
        }
    }

    @Override
    public void release() {
        Clip c = this.clip;
        this.clip = null;
        if (c == null) return;
        c.stop();
        c.close();
    }

    /** The player shown last (for the tests). */
    static javax.swing.JDialog lastWindow;

    /**
     * A small player for one sound (WAV ready's Play): Play / Pause, Stop, its place "00:04 / 00:12",
     * a slider to move in it, volume. Closing it stops the sound.
     */
    static void window(java.awt.Window owner, File f) {
        // Over a modal window (Downloads, a dialog) it belongs to that one, or the modal one would block it.
        java.awt.Window active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active != null) owner = active;
        final SoundClip s;
        try {
            s = new SoundClip(f);
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(owner, "Could not play " + f.getName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()), f.getName(), javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        final javax.swing.JDialog d = new javax.swing.JDialog(owner, f.getName(), java.awt.Dialog.ModalityType.MODELESS);
        d.setName("sound-player");
        lastWindow = d;
        final javax.swing.JButton play = new javax.swing.JButton("Pause");
        play.setName("sound-play");
        final javax.swing.JButton stop = new javax.swing.JButton("Stop");
        stop.setName("sound-stop");
        final javax.swing.JLabel time = new javax.swing.JLabel(Playlist.time(0, s.lengthMs()));
        time.setName("sound-time");
        final javax.swing.JSlider place = new javax.swing.JSlider(0, 1000, 0);
        place.setName("sound-place");
        final javax.swing.JSlider volume = new javax.swing.JSlider(0, 100, 100);
        volume.setName("sound-volume");
        volume.setPreferredSize(new java.awt.Dimension(110, volume.getPreferredSize().height));
        final boolean[] moving = new boolean[1];
        play.addActionListener(e -> {
            if (s.playing()) s.pause();
            else s.play();
            play.setText(s.playing() ? "Pause" : "Play");
        });
        stop.addActionListener(e -> {
            s.stop();
            play.setText("Play");
        });
        volume.addChangeListener(e -> s.volume(volume.getValue()));
        place.addChangeListener(e -> {
            if (!place.getValueIsAdjusting()) return;
            moving[0] = true;
            s.seek(place.getValue() / 1000.0);
            moving[0] = false;
        });
        final javax.swing.Timer tick = new javax.swing.Timer(200, e -> {
            time.setText(Playlist.time(s.timeMs(), s.lengthMs()));
            if (!place.getValueIsAdjusting() && !moving[0] && s.lengthMs() > 0) place.setValue((int) (s.timeMs() * 1000 / s.lengthMs()));
            if (!s.playing() && "Pause".equals(play.getText())) play.setText("Play");
        });
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        row.add(play);
        row.add(stop);
        row.add(time);
        row.add(new javax.swing.JLabel("  Volume"));
        row.add(volume);
        javax.swing.JPanel box = new javax.swing.JPanel(new java.awt.BorderLayout(0, 4));
        box.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));
        box.add(place, java.awt.BorderLayout.NORTH);
        box.add(row, java.awt.BorderLayout.CENTER);
        d.setContentPane(box);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                tick.stop();
                s.release();
            }
        });
        d.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        tick.start();
        s.play();
    }
}
