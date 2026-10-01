package pulsekit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Compare Hits (File menu): how closely drum hits line up. Compares hit times per drum family
 * (kick, snare, cymbals, toms), not sound, because Pulsekit plays its own kit:
 * the song against the file set's source MIDI (what the import changed), and the MIDI or song
 * against onsets heard in the original WAV (what DrumMidi heard). Programs/Java/CompareHits.java
 * and Programs/Python/compare_hits.py do the same from the command line.
 */
public final class HitCompare {
  private HitCompare() {}

  public static final String[] FAMILIES = {"Kick", "Snare", "Cymbals", "Toms"};
  public static final int KICK = 0;
  public static final int SNARE = 1;
  public static final int CYMBAL = 2;
  public static final int TOM = 3;
  /** Two hits match when they are this close. */
  public static final double TOLERANCE = 0.05;

  /** Drum family of a General MIDI drum note, or -1. */
  public static int family(int note) {
    switch (note) {
      case 35: case 36: return KICK;
      case 37: case 38: case 39: case 40: return SNARE;
      case 42: case 44: case 46: case 49: case 51: case 52: case 53: case 55: case 57: case 59: return CYMBAL;
      case 41: case 43: case 45: case 47: case 48: case 50: return TOM;
      default: return -1;
    }
  }

  /** Hit times in seconds, sorted, one array per family. */
  public static double[][] midiHits(byte[] midi) {
    List<long[]> notes = new ArrayList<long[]>();
    List<long[]> tempos = new ArrayList<long[]>();
    if (midi == null || midi.length < 14) return empty();
    int ppq = ((midi[12] & 0xff) << 8) | (midi[13] & 0xff);
    if (ppq <= 0 || (ppq & 0x8000) != 0) ppq = 480;
    int i = 8 + (int) be32(midi, 4);
    while (i + 8 <= midi.length) {
      long len = be32(midi, i + 4);
      boolean track = midi[i] == 'M' && midi[i + 1] == 'T' && midi[i + 2] == 'r' && midi[i + 3] == 'k';
      int p = i + 8;
      int end = (int) Math.min(midi.length, p + len);
      i = end;
      if (!track) continue;
      long tick = 0;
      int status = 0;
      while (p < end) {
        long[] v = varLen(midi, p, end);
        tick += v[0];
        p = (int) v[1];
        if (p >= end) break;
        int b = midi[p] & 0xff;
        if (b == 0xff) {
          if (p + 2 > end) break;
          int type = midi[p + 1] & 0xff;
          long[] l = varLen(midi, p + 2, end);
          int at = (int) l[1];
          if (type == 0x51 && l[0] >= 3 && at + 3 <= end) {
            tempos.add(new long[] {tick, ((midi[at] & 0xff) << 16) | ((midi[at + 1] & 0xff) << 8) | (midi[at + 2] & 0xff)});
          }
          p = at + (int) l[0];
          continue;
        }
        if (b == 0xf0 || b == 0xf7) {
          long[] l = varLen(midi, p + 1, end);
          p = (int) l[1] + (int) l[0];
          continue;
        }
        if ((b & 0x80) != 0) {
          status = b;
          p++;
        }
        int kind = status & 0xf0;
        int data = kind == 0xc0 || kind == 0xd0 ? 1 : 2;
        if (p + data > end) break;
        if (kind == 0x90 && (midi[p + 1] & 0xff) > 0) notes.add(new long[] {tick, midi[p] & 0x7f});
        p += data;
      }
    }
    java.util.Collections.sort(tempos, (a, b) -> Long.compare(a[0], b[0]));
    List<List<Double>> out = lists();
    for (long[] n : notes) {
      int f = family((int) n[1]);
      if (f >= 0) out.get(f).add(Double.valueOf(tickSeconds(n[0], tempos, ppq)));
    }
    return arrays(out);
  }

  private static double tickSeconds(long tick, List<long[]> tempos, int ppq) {
    double sec = 0;
    long at = 0;
    long tempo = 500000;
    for (long[] t : tempos) {
      if (t[0] >= tick) break;
      sec += (t[0] - at) * (double) tempo / 1e6 / ppq;
      at = t[0];
      tempo = t[1];
    }
    return sec + (tick - at) * (double) tempo / 1e6 / ppq;
  }

  /** Hit times of a song as Pulsekit plays it, from 0:00. */
  public static double[][] songHits(List<Engine.Part> parts) {
    List<List<Double>> out = lists();
    double t = 0;
    if (parts != null) {
      for (Engine.Part p : parts) {
        if (p == null) continue;
        int n = Engine.partStepCount(p);
        double step = 60.0 / Math.max(Engine.MIN_BPM, p.bpm) / 4.0;
        for (int r = 0; r < Math.max(1, p.repeats); r++) {
          for (int s = 0; s < n; s++, t += step) {
            for (int k = 0; k < Engine.TRACK_ID.length && p.cells != null && k < p.cells.length; k++) {
              if (p.cells[k] == null || s >= p.cells[k].length || p.cells[k][s] <= 0) continue;
              int f = family(Engine.NOTES[k]);
              if (f >= 0) out.get(f).add(Double.valueOf(t));
            }
          }
        }
      }
    }
    return arrays(out);
  }

  /**
   * Onsets heard in a recording, per family, from three frequency bands: low (kick), mid (snare)
   * and high (cymbals). Toms are not detected. In a full mix the low and mid bands also hear bass
   * and guitar notes, so only cymbals give a fair recall; see Row.recallShown.
   */
  public static double[][] audioOnsets(float[] x, int sr) {
    double[][] out = empty();
    if (x == null || x.length < 4096 || sr < 8000) return out;
    int n = 1024;
    int hop = Math.max(64, sr / 100);
    int frames = (x.length - n) / hop + 1;
    double[] win = new double[n];
    for (int i = 0; i < n; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
    double[][] bands = {{30, 120}, {180, 1200}, {7000, 16000}};
    int[][] bins = new int[3][2];
    for (int b = 0; b < 3; b++) {
      bins[b][0] = Math.max(1, (int) Math.ceil(bands[b][0] * n / sr));
      bins[b][1] = Math.min(n / 2, (int) Math.ceil(Math.min(bands[b][1], sr / 2.0 - 1) * n / sr));
    }
    double[][] flux = new double[3][Math.max(0, frames - 1)];
    double[][] prev = new double[3][];
    double[] re = new double[n];
    double[] im = new double[n];
    for (int f = 0; f < frames; f++) {
      int o = f * hop;
      for (int i = 0; i < n; i++) {
        re[i] = x[o + i] * win[i];
        im[i] = 0;
      }
      fft(re, im);
      for (int b = 0; b < 3; b++) {
        int lo = bins[b][0];
        int hi = bins[b][1];
        double[] cur = new double[Math.max(0, hi - lo)];
        for (int k = lo; k < hi; k++) cur[k - lo] = Math.log1p(100 * Math.hypot(re[k], im[k]));
        if (prev[b] != null && f - 1 < flux[b].length) {
          double sum = 0;
          for (int k = 0; k < cur.length; k++) sum += Math.max(0, cur[k] - prev[b][k]);
          flux[b][f - 1] = sum;
        }
        prev[b] = cur;
      }
    }
    double[] delta = {0.12, 0.12, 0.10};
    for (int b = 0; b < 3; b++) out[b] = peaks(flux[b], delta[b], hop, sr);
    return out;
  }

  private static double[] peaks(double[] flux, double delta, int hop, int sr) {
    int m = flux.length;
    if (m < 3) return new double[0];
    double[] sorted = flux.clone();
    Arrays.sort(sorted);
    double top = sorted[Math.min(m - 1, (int) (0.99 * (m - 1)))];
    if (!(top > 0)) return new double[0];
    double[] v = new double[m];
    for (int i = 0; i < m; i++) v[i] = flux[i] / top;
    int k = (int) (0.15 * sr / hop);
    List<Double> out = new ArrayList<Double>();
    double[] w = new double[2 * k + 1];
    for (int i = 1; i < m - 1; i++) {
      if (!(v[i] > v[i - 1] && v[i] >= v[i + 1])) continue;
      int a = Math.max(0, i - k);
      int z = Math.min(m, i + k);
      int c = z - a;
      System.arraycopy(v, a, w, 0, c);
      Arrays.sort(w, 0, c);
      double med = c % 2 == 1 ? w[c / 2] : (w[c / 2 - 1] + w[c / 2]) / 2;
      if (v[i] <= med + delta) continue;
      double t = (i + 1) * (double) hop / sr;
      if (out.isEmpty() || t - out.get(out.size() - 1).doubleValue() > 0.05) out.add(Double.valueOf(t));
    }
    double[] r = new double[out.size()];
    for (int i = 0; i < r.length; i++) r[i] = out.get(i).doubleValue();
    return r;
  }

  /** In-place radix-2 FFT; the length must be a power of two. */
  static void fft(double[] re, double[] im) {
    int n = re.length;
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double t = re[i]; re[i] = re[j]; re[j] = t;
        t = im[i]; im[i] = im[j]; im[j] = t;
      }
    }
    for (int len = 2; len <= n; len <<= 1) {
      double ang = -2 * Math.PI / len;
      double wr = Math.cos(ang);
      double wi = Math.sin(ang);
      for (int i = 0; i < n; i += len) {
        double cr = 1;
        double ci = 0;
        for (int j = 0; j < len / 2; j++) {
          int a = i + j;
          int b = a + len / 2;
          double xr = re[b] * cr - im[b] * ci;
          double xi = re[b] * ci + im[b] * cr;
          re[b] = re[a] - xr;
          im[b] = im[a] - xi;
          re[a] += xr;
          im[a] += xi;
          double nr = cr * wr - ci * wi;
          ci = cr * wi + ci * wr;
          cr = nr;
        }
      }
    }
  }

  public static final class Row {
    public String family;
    public int ref;
    public int test;
    public int matched;
    public double medianMs;
    /** False when the reference is a full mix heard in a band that also catches other instruments. */
    public boolean recallShown = true;

    public double precision() {
      return test == 0 ? 0 : matched / (double) test;
    }

    public double recall() {
      return ref == 0 ? 0 : matched / (double) ref;
    }
  }

  public static final class Result {
    public String title;
    public String refName;
    public String testName;
    /** Seconds added to the tested hits to line them up with the reference. */
    public double offsetSec;
    public final List<Row> rows = new ArrayList<Row>();
  }

  /**
   * Compares `test` hits against `ref` hits per family, after shifting `test` by the offset
   * (within ±100 ms) that lines up the most hits. With `audioRef`, recall is shown only for cymbals.
   */
  public static Result compare(String title, String refName, double[][] ref, String testName, double[][] test, boolean audioRef) {
    Result r = new Result();
    r.title = title;
    r.refName = refName;
    r.testName = testName;
    r.offsetSec = bestOffset(ref, test);
    for (int f = 0; f < FAMILIES.length; f++) {
      double[] a = f < ref.length && ref[f] != null ? ref[f] : new double[0];
      double[] b = f < test.length && test[f] != null ? test[f] : new double[0];
      if (audioRef && f == TOM) continue;
      if (a.length == 0 && b.length == 0) continue;
      double[] shifted = shift(b, r.offsetSec);
      double[] errs = new double[Math.min(a.length, b.length)];
      int m = match(a, shifted, TOLERANCE, errs);
      Row row = new Row();
      row.family = FAMILIES[f];
      row.ref = a.length;
      row.test = b.length;
      row.matched = m;
      double[] e = Arrays.copyOf(errs, m);
      for (int i = 0; i < m; i++) e[i] = Math.abs(e[i]);
      Arrays.sort(e);
      row.medianMs = m == 0 ? 0 : 1000 * (m % 2 == 1 ? e[m / 2] : (e[m / 2 - 1] + e[m / 2]) / 2);
      row.recallShown = !audioRef || f == CYMBAL;
      r.rows.add(row);
    }
    return r;
  }

  static double bestOffset(double[][] ref, double[][] test) {
    double[] a = all(ref, true);
    double[] b = all(test, true);
    double best = 0;
    int bestN = -1;
    for (int ms = -100; ms <= 100; ms++) {
      int n = match(a, shift(b, ms / 1000.0), 0.02, null);
      if (n > bestN || (n == bestN && Math.abs(ms) < Math.abs(best * 1000))) {
        bestN = n;
        best = ms / 1000.0;
      }
    }
    return best;
  }

  /** Greedy one-to-one matching in time order; `errs` gets test minus ref for each match. */
  static int match(double[] ref, double[] test, double tol, double[] errs) {
    boolean[] used = new boolean[test.length];
    int m = 0;
    for (double r : ref) {
      int k = Arrays.binarySearch(test, r);
      if (k < 0) k = -k - 1;
      int best = -1;
      for (int c = Math.max(0, k - 1); c >= 0 && test[c] >= r - tol; c--) {
        if (!used[c] && (best < 0 || Math.abs(test[c] - r) < Math.abs(test[best] - r))) best = c;
      }
      for (int c = k; c < test.length && test[c] <= r + tol; c++) {
        if (!used[c] && Math.abs(test[c] - r) <= tol && (best < 0 || Math.abs(test[c] - r) < Math.abs(test[best] - r))) best = c;
      }
      if (best >= 0 && Math.abs(test[best] - r) > tol) best = -1;
      if (best >= 0) {
        used[best] = true;
        if (errs != null && m < errs.length) errs[m] = test[best] - r;
        m++;
      }
    }
    return m;
  }

  /** File sets (from `sources`) that keep a source MIDI: shown label → key that holds the MIDI. */
  public static java.util.LinkedHashMap<String, String> fileSetsWithMidi(List<String> sources) {
    java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<String, String>();
    if (sources == null) return out;
    for (String src : sources) {
      if (src == null || src.isEmpty()) continue;
      String key = Engine.fileSetMidiKeyForLabel(src);
      if (key != null && Engine.fileSetMidiName(key).length() > 0 && !out.containsValue(key)) out.put(src, key);
    }
    return out;
  }

  public static byte[] fileSetMidi(String key) {
    Engine.FileSetAudio au = Engine.fileSetAudioOf(key);
    return au == null ? null : au.sourceMidi;
  }

  /** The original WAV kept with the file set, or null. */
  public static byte[] fileSetWav(String key) {
    Engine.FileSetAudio au = Engine.fileSetAudioOf(key);
    return au == null || au.sourceWav == null || au.sourceWav.length < 44 ? null : au.sourceWav;
  }

  /** The song made from or saved in file set `source`, or null. */
  public static Engine.ImportedSong songFor(String source, List<Engine.ImportedSong> songs) {
    if (source == null || songs == null) return null;
    for (Engine.ImportedSong s : songs) if (s != null && source.equals(s.fileSet) && !s.parts.isEmpty()) return s;
    Engine.ImportedSong made = Engine.fileSetSongMade(source, songs);
    return made != null && !made.parts.isEmpty() ? made : null;
  }

  /** The page's report: song against MIDI, then MIDI and song against the WAV when there is one. */
  public static String report(byte[] midi, Engine.ImportedSong song, AudioIo.Pcm wav) {
    StringBuilder sb = new StringBuilder();
    double[][] m = midiHits(midi);
    double[][] s = song == null ? null : songHits(song.parts);
    if (s != null) sb.append(text(compare("", "MIDI", m, "Song", s, false))).append('\n');
    else sb.append("No song made from this file set yet. Make song to compare it with the MIDI.\n\n");
    if (wav != null) {
      double[][] w = audioOnsets(wav.samples, wav.sr);
      sb.append(text(compare("", "WAV", w, "MIDI", m, true))).append('\n');
      if (s != null) sb.append(text(compare("", "WAV", w, "Song", s, true))).append('\n');
    }
    return sb.toString().trim();
  }

  public static String text(Result r) {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format(Locale.ROOT, "%s against %s, shifted %+d ms to line up%n", r.testName, r.refName,
        Math.round(r.offsetSec * 1000)));
    sb.append(String.format(Locale.ROOT, "%-8s %6s %6s %8s %8s %7s %7s%n", "", "ref",
        "test", "matched", "precis.", "recall", "timing"));
    for (Row row : r.rows) {
      sb.append(String.format(Locale.ROOT, "%-8s %6d %6d %8d %7.1f%% %7s %5.1fms%n", row.family, row.ref, row.test, row.matched,
          100 * row.precision(), row.recallShown ? String.format(Locale.ROOT, "%.1f%%", 100 * row.recall()) : "-", row.medianMs));
    }
    return sb.toString();
  }

  /** What the columns mean, for the page and the programs. */
  public static final String LEGEND =
      "matched: hits within " + Math.round(TOLERANCE * 1000) + " ms of each other. precision: tested hits that match. "
      + "recall: reference hits that are found. timing: median distance of matched hits. "
      + "Against a WAV, recall is shown for cymbals only, because the kick and snare bands also hear bass and guitar.";

  private static double[] shift(double[] a, double by) {
    double[] o = new double[a.length];
    for (int i = 0; i < a.length; i++) o[i] = a[i] + by;
    return o;
  }

  private static double[] all(double[][] h, boolean skipToms) {
    int n = 0;
    for (int f = 0; f < h.length; f++) if (h[f] != null && !(skipToms && f == TOM)) n += h[f].length;
    double[] o = new double[n];
    int at = 0;
    for (int f = 0; f < h.length; f++) {
      if (h[f] == null || (skipToms && f == TOM)) continue;
      System.arraycopy(h[f], 0, o, at, h[f].length);
      at += h[f].length;
    }
    Arrays.sort(o);
    return o;
  }

  private static double[][] empty() {
    return new double[][] {new double[0], new double[0], new double[0], new double[0]};
  }

  private static List<List<Double>> lists() {
    List<List<Double>> out = new ArrayList<List<Double>>();
    for (int f = 0; f < FAMILIES.length; f++) out.add(new ArrayList<Double>());
    return out;
  }

  private static double[][] arrays(List<List<Double>> in) {
    double[][] out = new double[in.size()][];
    for (int f = 0; f < out.length; f++) {
      List<Double> l = in.get(f);
      out[f] = new double[l.size()];
      for (int i = 0; i < out[f].length; i++) out[f][i] = l.get(i).doubleValue();
      Arrays.sort(out[f]);
    }
    return out;
  }

  private static long be32(byte[] d, int i) {
    if (i + 4 > d.length) return 0;
    return ((d[i] & 0xffL) << 24) | ((d[i + 1] & 0xffL) << 16) | ((d[i + 2] & 0xffL) << 8) | (d[i + 3] & 0xffL);
  }

  /** {value, next index}. */
  private static long[] varLen(byte[] d, int p, int end) {
    long v = 0;
    while (p < end) {
      int b = d[p++] & 0xff;
      v = (v << 7) | (b & 0x7f);
      if ((b & 0x80) == 0) break;
    }
    return new long[] {v, p};
  }
}
