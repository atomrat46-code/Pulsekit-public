package pulsekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AudioMixer's Record: one track at a time takes sound from its audio input (the track's input
 * picker; the app opens it). ● Record starts (or goes on after Pause); ❚❚ Pause keeps the input open
 * and writes what was taken onto the end of the track; ■ Stop writes it and closes the input. What is
 * recorded is added after the track's sound, so nothing on it is lost. 44.1 kHz mono, at General
 * settings' recording level, kept on both sides.
 */
public final class TrackRecorder {
  private TrackRecorder() {}

  public static final String RECORD = "● Record";
  public static final String PAUSE = "❚❚ Pause";
  public static final String STOP = "■ Stop";
  /** Taken in parts of this length at most (written onto the track when full). */
  public static final int PART_SECONDS = 60;

  /** An open audio input: 16-bit mono samples at AudioMix.RATE. */
  public interface Input {
    /** Samples read into `buf` (blocks until some are there); 0: none yet; negative: the input failed. */
    int read(short[] buf) throws Exception;

    void close();
  }

  /** What the app gives: its audio inputs (Sound devices' "Audio inputs"), and one opened by its key. */
  public interface Inputs {
    List<SoundDevices.Device> list();

    /** The input with this key; empty key: the default input. */
    Input open(String key) throws Exception;
  }

  /** What is heard while recording (the level, and how long), from the recording's thread. */
  public interface Listener {
    void heard(String text);
  }

  /** Set by the app as it starts. */
  public static volatile Inputs inputs;

  static volatile AudioMix.Track track;
  static volatile boolean running, paused;
  static Thread thread;
  static short[] take = new short[0];
  static int length;
  /** The take's samples written so far (this Record to Stop), for the status. */
  static long total;
  /** The last thing said (for the tests and the status). */
  public static volatile String lastSaid = "";

  /** The inputs' names and keys: the default first. {names, keys}. */
  public static String[][] choices() {
    List<String> names = new ArrayList<String>(), keys = new ArrayList<String>();
    names.add(AudioMix.DEFAULT_INPUT);
    keys.add("");
    Inputs in = inputs;
    if (in != null) {
      try {
        for (SoundDevices.Device d : in.list()) {
          if (!SoundDevices.IN.equals(d.kind) || keys.contains(d.key())) continue;
          names.add(d.name + (d.driver.isEmpty() ? "" : " (" + d.driver + ")"));
          keys.add(d.key());
        }
      } catch (Throwable ignored) {
        // the default only
      }
    }
    return new String[][] {names.toArray(new String[0]), keys.toArray(new String[0])};
  }

  /** The track being recorded (paused too), or null. */
  public static synchronized AudioMix.Track recording() {
    return running ? track : null;
  }

  public static boolean paused() {
    return running && paused;
  }

  /**
   * ● Record on `t`: starts taking from its input, or goes on after Pause. Another track being
   * recorded is stopped first. What it says.
   */
  public static String record(final AudioMix.Track t, final Listener listener) {
    synchronized (TrackRecorder.class) {
      if (running && track == t) {
        paused = false;
        return say("Recording " + t.name + " (going on)");
      }
    }
    if (running) stop();
    final Inputs in = inputs;
    if (in == null) return say("No audio input here");
    final Input input;
    try {
      input = in.open(t.input);
    } catch (Throwable ex) {
      return say("Could not open the input for " + t.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
    }
    synchronized (TrackRecorder.class) {
      track = t;
      running = true;
      paused = false;
      length = 0;
      total = 0;
      thread = new Thread(() -> loop(t, input, listener), "pulsekit-mixer-record");
      thread.start();
    }
    return say("Recording " + t.name);
  }

  static void loop(AudioMix.Track t, Input input, Listener listener) {
    short[] buf = new short[AudioMix.RATE / 10];
    String failed = null;
    try {
      while (running && track == t) {
        int n = input.read(buf);
        if (n < 0) throw new IllegalStateException("the input gave error " + n);
        if (n == 0) {
          Thread.sleep(20);
          continue;
        }
        InputLevel.gain(buf, n, AppSettings.recordLevel());
        boolean on = !paused;
        if (on) keep(t, buf, n);
        String text = InputLevel.text(InputLevel.levels(buf, n)) + " · " + (on ? "recording " : "paused at ") + seconds();
        if (listener != null) listener.heard(text);
      }
    } catch (Throwable ex) {
      failed = "Recording stopped: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
    } finally {
      input.close();
    }
    if (failed != null) {
      synchronized (TrackRecorder.class) {
        if (track == t) running = false;
      }
      flush(t);
      say(failed);
      if (listener != null) listener.heard(failed);
    }
  }

  static synchronized void keep(AudioMix.Track t, short[] buf, int n) {
    if (length + n > AudioMix.RATE * PART_SECONDS) flush(t);
    if (length + n > take.length) take = java.util.Arrays.copyOf(take, Math.max(length + n, Math.min(AudioMix.RATE * PART_SECONDS, Math.max(AudioMix.RATE, take.length * 2))));
    System.arraycopy(buf, 0, take, length, n);
    length += n;
    total += n;
  }

  /** What was taken so far written onto the end of the track. */
  static synchronized void flush(AudioMix.Track t) {
    if (length == 0) return;
    float[] f = new float[length];
    for (int i = 0; i < length; i++) f[i] = take[i] / 32768f;
    length = 0;
    try {
      AudioMix.setSound(t, f, f.clone(), true);
    } catch (Exception ex) {
      say("Could not write " + t.name + ": " + ex.getMessage());
    }
  }

  /** ❚❚ Pause: the take so far written onto the track; ● Record goes on. What it says. */
  public static String pause() {
    AudioMix.Track t;
    synchronized (TrackRecorder.class) {
      if (!running) return say("Not recording");
      paused = true;
      t = track;
      flush(t);
    }
    return say("Paused " + t.name + " at " + seconds() + " (● Record goes on)");
  }

  /** ■ Stop: the take written onto the track and the input closed. What it says (nothing when not recording). */
  public static String stop() {
    AudioMix.Track t;
    Thread th;
    synchronized (TrackRecorder.class) {
      if (!running) return "";
      running = false;
      t = track;
      th = thread;
    }
    if (th != null && th != Thread.currentThread()) {
      try {
        th.join(3000);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
    String took = seconds();
    flush(t);
    synchronized (TrackRecorder.class) {
      if (track == t) track = null;
    }
    return say("Recorded " + took + " onto " + t.name + ": " + AudioMix.line(t));
  }

  static String seconds() {
    long s = total;
    return String.format(Locale.ROOT, "%.1f s", s / (double) AudioMix.RATE);
  }

  static String say(String s) {
    lastSaid = s;
    return s;
  }
}
