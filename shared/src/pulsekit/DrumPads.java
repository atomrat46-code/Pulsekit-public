package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AudioMixer's DrumPad tracks. + New DrumPad track adds one; its Drumpad button opens the pads of a kit
 * (any of the drum sets), BPM and time signature with a Metronome, the track's volume and gain, and
 * ● Record, ❚❚ Pause, ■ Stop and ▶ Play. Pads sound at once (a small mixer of their own, the
 * metronome's click with them: the app gives it an output); while recording, each hit is kept with its
 * time on the track. Pause and Stop make the track's sound from its hits again (kept beside its WAV in
 * <file>.pads with the kit, BPM and time signature), so they can be edited later.
 */
public final class DrumPads {
  private DrumPads() {}

  public static final String NEW = "New DrumPad track";
  public static final String BUTTON = "Drumpad";
  public static final String TITLE = "DrumPad";
  public static final String METRONOME = "Metronome";
  public static final String[] SIGNATURES = {"4/4", "3/4", "2/4", "5/4", "6/8", "7/8", "12/8"};
  /** The kits' samples are at this rate (the app's voices); the mixer runs at AudioMix.RATE. */
  public static final int VOICE_RATE = 22050;
  /** Frames the mixer makes at a time (about 6 ms). */
  public static final int BLOCK = 256;

  /** One hit: when on the track (frames at AudioMix.RATE), which pad (Engine.TRACK_ID's order), how hard (0..1). */
  public static final class Hit {
    public long frame;
    public int pad;
    public float velocity;

    public Hit(long frame, int pad, float velocity) {
      this.frame = frame;
      this.pad = pad;
      this.velocity = velocity;
    }
  }

  /** A DrumPad track's take: its kit, BPM, time signature, hits and length (frames). */
  public static final class Take {
    public String kit = "Original";
    public int bpm = 120;
    public String ts = "4/4";
    public final List<Hit> hits = new ArrayList<Hit>();
    public long length;

    public int beats() {
      try {
        return Math.max(1, Integer.parseInt(ts.substring(0, ts.indexOf('/'))));
      } catch (Exception ex) {
        return 4;
      }
    }

    public int unit() {
      try {
        return Math.max(1, Integer.parseInt(ts.substring(ts.indexOf('/') + 1)));
      } catch (Exception ex) {
        return 4;
      }
    }

    /** Frames from one click to the next (a beat of the signature's unit). */
    public double clickFrames() {
      return AudioMix.RATE * 60.0 / Math.max(Engine.MIN_BPM, Math.min(Engine.MAX_BPM, bpm)) * 4.0 / unit();
    }
  }

  /** What the app gives: a stereo 16-bit output at AudioMix.RATE. */
  public interface Out {
    /** Interleaved left/right samples, `frames` of them; blocks while the output is full. */
    void write(short[] lr, int frames) throws Exception;

    void close();
  }

  // ---- The track's take, beside its WAV.

  public static File file(AudioMix.Track t) {
    return new File(AudioMix.dir, t.file.replaceAll("\\.wav$", "") + ".pads");
  }

  public static Take load(AudioMix.Track t) {
    Take k = new Take();
    File f = file(t);
    if (!f.isFile()) return k;
    try {
      for (String line : new String(AudioMix.read(f), StandardCharsets.UTF_8).split("\n")) {
        if (line.startsWith("kit=")) k.kit = line.substring(4);
        else if (line.startsWith("bpm=")) k.bpm = AudioMix.clamp(AudioMix.parse(line.substring(4), 120), Engine.MIN_BPM, Engine.MAX_BPM);
        else if (line.startsWith("ts=")) k.ts = line.substring(3).trim();
        else if (line.startsWith("length=")) k.length = Math.max(0, (long) AudioMix.parseDouble(line.substring(7)));
        else if (line.startsWith("hit=")) {
          String[] p = line.substring(4).split(",");
          if (p.length < 3) continue;
          int pad = AudioMix.parse(p[1], -1);
          if (pad < 0 || pad >= Engine.TRACK_ID.length) continue;
          k.hits.add(new Hit((long) AudioMix.parseDouble(p[0]), pad, (float) Math.max(0, Math.min(1, AudioMix.parseDouble(p[2])))));
        }
      }
    } catch (Exception ignored) {
      // what was read
    }
    return k;
  }

  public static void save(AudioMix.Track t, Take k) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("kit=").append(AudioMix.clean(k.kit)).append('\n').append("bpm=").append(k.bpm).append('\n').append("ts=").append(k.ts).append('\n').append("length=").append(k.length).append('\n');
    for (Hit h : k.hits) sb.append("hit=").append(h.frame).append(',').append(h.pad).append(',').append(String.format(Locale.ROOT, "%.3f", h.velocity)).append('\n');
    AudioMix.write(file(t), sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  // ---- Sounds.

  /** The kit's 12 voices (Engine.TRACK_ID's order) from a drum set and the app's own voices (VOICE_RATE). */
  public static short[][] voices(Engine.DrumSet s, short[][] base) {
    short[][] out = new short[Engine.TRACK_ID.length][];
    for (int t = 0; t < out.length; t++) {
      int src = Engine.soundTrack(t);
      short[] v = s == null || s.original || s.matchOrig[src] ? null : s.samples[src];
      out[t] = v != null ? v : base != null && src < base.length ? base[src] : null;
    }
    return out;
  }

  /** The voices at AudioMix.RATE, as floats. */
  public static float[][] atRate(short[][] voices) {
    float[][] out = new float[voices.length][];
    for (int t = 0; t < voices.length; t++) {
      short[] v = voices[t];
      if (v == null) {
        out[t] = new float[0];
        continue;
      }
      float[] f = new float[v.length];
      for (int i = 0; i < f.length; i++) f[i] = v[i] / 32768f;
      out[t] = AudioMix.resample(new AudioIo.StereoPcm(f, f, VOICE_RATE), AudioMix.RATE).left;
    }
    return out;
  }

  /** The metronome's click: high on the bar's first beat, lower on the others (30 ms). */
  public static float[] click(boolean first) {
    int n = AudioMix.RATE * 30 / 1000;
    float[] c = new float[n];
    double hz = first ? 1760 : 1175;
    for (int i = 0; i < n; i++) c[i] = (float) (0.5 * Math.sin(2 * Math.PI * hz * i / AudioMix.RATE) * Math.exp(-i / (n / 5.0)));
    return c;
  }

  /** The take's sound: each hit with its voice, as long as the take (or its last hit's sound). {left, right}. */
  public static float[][] render(Take k, float[][] voices) {
    long n = k.length;
    for (Hit h : k.hits) n = Math.max(n, h.frame + (h.pad < voices.length ? voices[h.pad].length : 0));
    int len = (int) Math.min(Integer.MAX_VALUE - 8, n);
    float[] l = new float[len];
    for (Hit h : k.hits) {
      if (h.pad >= voices.length) continue;
      float[] v = voices[h.pad];
      for (int i = 0; i < v.length && h.frame + i < len; i++) l[(int) (h.frame + i)] += v[i] * h.velocity;
    }
    return new float[][] {l, l.clone()};
  }

  // ---- The live pads: one at a time.

  static final class Sounding {
    final float[] v;
    final float gain;
    int at;

    Sounding(float[] v, float gain) {
      this.v = v;
      this.gain = gain;
    }
  }

  /** The pads' window open now: its track, take, voices and output. */
  public static final class Session {
    public final AudioMix.Track track;
    public final Take take;
    volatile float[][] voices;
    final List<Sounding> sounding = new ArrayList<Sounding>();
    volatile boolean open = true;
    volatile boolean metronome;
    long metroFrame;
    final float[] clickHigh = click(true), clickLow = click(false);
    // Recording: where on the track it goes on from, since when (nanoTime), paused.
    volatile boolean recording, paused;
    long base;
    long since;
    Thread thread;
    /** What the session said last (for the tests and the status). */
    public volatile String lastSaid = "";

    Session(AudioMix.Track track, Take take, float[][] voices) {
      this.track = track;
      this.take = take;
      this.voices = voices;
    }

    public void setVoices(float[][] v, String kit) {
      voices = v;
      take.kit = kit;
    }

    /** A pad pressed: it sounds, and is kept when recording. */
    public void hit(int pad, float velocity) {
      float[][] v = voices;
      if (pad < 0 || pad >= v.length) return;
      synchronized (this) {
        sounding.add(new Sounding(v[pad], velocity * (float) track.factor()));
        if (recording && !paused) take.hits.add(new Hit(now(), pad, velocity));
      }
    }

    /** Where on the track recording is now (frames). */
    synchronized long now() {
      if (!recording || paused) return base;
      return base + (System.nanoTime() - since) * AudioMix.RATE / 1_000_000_000L;
    }

    public void metronome(boolean on) {
      synchronized (this) {
        metronome = on;
        metroFrame = 0;
      }
    }

    public boolean metronomeOn() {
      return metronome;
    }

    /** ● Record: from the end of the take (or on after Pause). */
    public synchronized String record() {
      if (recording && paused) {
        paused = false;
        since = System.nanoTime();
        return say("Recording " + track.name + " (going on)");
      }
      if (recording) return say("Recording " + track.name);
      recording = true;
      paused = false;
      base = take.length;
      since = System.nanoTime();
      return say("Recording " + track.name + " from " + seconds(base));
    }

    /** ❚❚ Pause: the hits so far made into the track's sound; ● Record goes on. */
    public String pause() throws Exception {
      synchronized (this) {
        if (!recording) return say("Not recording");
        if (!paused) {
          base = now();
          paused = true;
        }
        take.length = Math.max(take.length, base);
      }
      write();
      return say("Paused " + track.name + " at " + seconds(take.length) + " · " + take.hits.size() + " hits");
    }

    /** ■ Stop: the hits made into the track's sound, recording ended. */
    public String stop() throws Exception {
      synchronized (this) {
        if (!recording) return say("Not recording");
        if (!paused) base = now();
        recording = false;
        paused = false;
        take.length = Math.max(take.length, base);
      }
      write();
      return say("Recorded " + track.name + ": " + AudioMix.line(track) + " · " + take.hits.size() + " hits");
    }

    public boolean recording() {
      return recording;
    }

    public boolean paused() {
      return recording && paused;
    }

    /** The take kept and made into the track's sound. */
    public synchronized void write() throws Exception {
      save(track, take);
      float[][] s = render(take, voices);
      AudioMix.setSound(track, s[0], s[1], false);
    }

    /** "Recording 3.2 s · 14 hits" (or paused, or not). */
    public String state() {
      return (recording ? paused ? "Paused at " : "Recording " : "Length ") + seconds(recording ? now() : take.length) + " · " + take.hits.size() + " hits"
          + (metronome ? " · " + METRONOME + " " + take.bpm + " " + take.ts : "");
    }

    String say(String s) {
      lastSaid = s;
      return s;
    }

    /** The next `frames` frames of the pads and the metronome, interleaved, clipped. */
    public synchronized void mix(short[] lr, int frames) {
      float[] l = new float[frames];
      for (java.util.Iterator<Sounding> it = sounding.iterator(); it.hasNext(); ) {
        Sounding s = it.next();
        int n = Math.min(frames, s.v.length - s.at);
        for (int i = 0; i < n; i++) l[i] += s.v[s.at + i] * s.gain;
        s.at += n;
        if (s.at >= s.v.length) it.remove();
      }
      if (metronome) {
        double every = take.clickFrames();
        int beats = take.beats();
        for (int i = 0; i < frames; i++, metroFrame++) {
          long beat = (long) Math.floor(metroFrame / every);
          int into = (int) (metroFrame - Math.round(beat * every));
          float[] c = beat % beats == 0 ? clickHigh : clickLow;
          if (into >= 0 && into < c.length) l[i] += c[into];
        }
      }
      for (int i = 0; i < frames; i++) {
        short v = (short) AudioMix.toShort(Math.max(-1, Math.min(1, l[i])));
        lr[2 * i] = v;
        lr[2 * i + 1] = v;
      }
    }

    /** Sound to `out` until closed (a thread of its own). */
    public void play(final Out out) {
      if (out == null) return;
      thread = new Thread(() -> {
        short[] lr = new short[BLOCK * 2];
        long start = System.nanoTime(), made = 0;
        try {
          while (open) {
            mix(lr, BLOCK);
            out.write(lr, BLOCK);
            made += BLOCK;
            // Never more than 100 ms ahead of the clock (an output that does not wait).
            long ahead = made * 1000L / AudioMix.RATE - (System.nanoTime() - start) / 1_000_000L;
            if (ahead > 100) Thread.sleep(ahead - 50);
          }
        } catch (Throwable ex) {
          say("No sound for the pads: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
        } finally {
          out.close();
        }
      }, "pulsekit-drumpad");
      thread.start();
    }

    /** The window closed: recording stopped (written) and the sound ended. */
    public void close() {
      try {
        if (recording) stop();
      } catch (Exception ex) {
        say("Could not write " + track.name + ": " + ex.getMessage());
      }
      open = false;
      if (current == this) current = null;
    }
  }

  /** The session open now (one at a time). */
  public static volatile Session current;

  /** The pads for `t` with these voices (the kit's, at AudioMix.RATE); another one open is closed. */
  public static Session open(AudioMix.Track t, float[][] voices) {
    Session old = current;
    if (old != null) old.close();
    Session s = new Session(t, load(t), voices);
    current = s;
    return s;
  }

  static String seconds(long frames) {
    return String.format(Locale.ROOT, "%.1f s", frames / (double) AudioMix.RATE);
  }
}
