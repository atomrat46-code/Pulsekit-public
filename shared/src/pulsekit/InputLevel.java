package pulsekit;

import java.util.Locale;

/**
 * File > Sound devices > an input's Test: what the input hears, as a level meter (RMS and peak in
 * dBFS), with Record, Stop, Play and Save; and Pulsekit's own levels (Sound devices, on top): the
 * playback volume of its players and the recording level applied to what it records.
 */
public final class InputLevel {
  private InputLevel() {}

  public static final String TITLE = "Test input";
  public static final String RECORD = "Record";
  public static final String STOP = "Stop";
  public static final String PLAY = "Play";
  public static final String SAVE = "Save";
  public static final String PLAYBACK = "Playback volume";
  public static final String RECORDING = "Recording level";
  public static final String LEVELS_NOTE = "Playback volume: Pulsekit's own players (drums, previews, clips; not the phone's or computer's own "
      + "volume). Recording level: the gain on what Pulsekit records (an input's Test).";
  public static final int RATE = 44100;
  /** At most this long a recording (s). */
  public static final int MAX_SECONDS = 120;
  /** Below this the meter says no signal (dBFS). */
  public static final double SILENT_DB = -60;

  /** The samples with the recording level (percent) applied, clipped at full scale. */
  public static void gain(short[] pcm, int n, int percent) {
    if (percent == 100) return;
    double g = Math.max(0, percent) / 100.0;
    for (int i = 0; i < n; i++) {
      long v = Math.round(pcm[i] * g);
      pcm[i] = (short) Math.max(-32768, Math.min(32767, v));
    }
  }

  /** {rms dBFS, peak dBFS} of n samples (-120 for silence). */
  public static double[] levels(short[] pcm, int n) {
    double sum = 0;
    int peak = 0;
    for (int i = 0; i < n; i++) {
      int v = pcm[i];
      sum += (double) v * v;
      peak = Math.max(peak, Math.abs(v));
    }
    double rms = n == 0 ? 0 : Math.sqrt(sum / n);
    return new double[] {db(rms), db(peak)};
  }

  static double db(double v) {
    return v <= 0 ? -120 : Math.max(-120, 20 * Math.log10(v / 32768.0));
  }

  /** The meter's fill, 0..1, from a level in dBFS (-60 empty, 0 full). */
  public static double meter(double db) {
    return Math.max(0, Math.min(1, (db - SILENT_DB) / -SILENT_DB));
  }

  /** "Level -23.4 dBFS, peak -12.0 dBFS: signal" (or no signal, or clipping). */
  public static String text(double[] lv) {
    String state = lv[1] >= -0.5 ? "clipping: lower the recording level" : lv[0] < SILENT_DB ? "no signal" : "signal";
    return String.format(Locale.ROOT, "Level %s, peak %s: %s", dbText(lv[0]), dbText(lv[1]), state);
  }

  static String dbText(double db) {
    return db <= -119 ? "-∞ dBFS" : String.format(Locale.ROOT, "%.1f dBFS", db);
  }

  /** "Recorded 3.2 s". */
  public static String length(int samples) {
    return String.format(Locale.ROOT, "%.1f s", samples / (double) RATE);
  }

  /** A recording's file name: "Pulsekit input test 20261030-2136.wav". */
  public static String fileName() {
    return "Pulsekit input test " + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new java.util.Date()) + ".wav";
  }

  /** A playback volume (percent) as a player's gain, 0..1. */
  public static float volume(int percent) {
    return Math.max(0, Math.min(100, percent)) / 100f;
  }
}
