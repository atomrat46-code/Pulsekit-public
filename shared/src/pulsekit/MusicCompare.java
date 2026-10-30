package pulsekit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * SogniMusic --compare_models: the same request made by several models, each track scored on what
 * can be measured from the sound: how much of it is drums (percussive against held tones, for
 * --drums_only), whether its tempo is the one asked, and its length. Which instruments play
 * cannot be told apart this way, so the report says to listen for that.
 */
public final class MusicCompare {
  private MusicCompare() {}

  public static final String FLAG = "--compare_models";

  /** One model's track and what was measured. */
  public static final class Take {
    public String model = "";
    public String file = "";
    public double seconds;
    public double bpm;
    /** Percussive share of the sound, 0..1. */
    public double drums;
    public double score;
    public String problem;
  }

  /** With --compare_models: each model's track also goes through DrumMidi_CRT into a file set of its own. */
  public static final String TO_FILE_SETS = "--compare_to_file_sets";

  /** The file sets' name: "SogniMusic run N" (N one more than the highest run among the file sets). */
  public static String runName(List<String> fileSets) {
    int n = 0;
    if (fileSets != null) {
      for (String s : fileSets) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("SogniMusic run (\\d+)( .*)?").matcher(s == null ? "" : s);
        if (m.matches()) n = Math.max(n, Integer.parseInt(m.group(1)));
      }
    }
    return "SogniMusic run " + (n + 1);
  }

  /**
   * --compare_to_file_sets: DrumMidi_CRT on each model's WAV in turn, its MIDI named
   * "SogniMusic run N <model>.mid" so the file set it makes has that name. PyJav starts the next run
   * when one ends (next), until all are done.
   */
  public static final class Chain {
    public final String run;
    final MusicExpect expect;
    final String saved;
    /** "model<TAB>wav path", in the ranking's order. */
    final List<String[]> tracks = new ArrayList<String[]>();
    int at = -1;
    public final List<String> made = new ArrayList<String>();
    /** The models' ranking, shown again when the chain ends (each DrumMidi run has its own log). */
    public String report = "";

    Chain(String run, MusicExpect expect, String saved) {
      this.run = run;
      this.expect = expect;
      this.saved = saved;
    }

    /** The file set's name for a model. */
    public String setName(String model) {
      return run + " " + model;
    }

    /** DrumMidi_CRT's arguments for the next track, or null when all are done (the chain ends). */
    public synchronized List<String> next() {
      if (at >= 0 && at < tracks.size()) made.add(setName(tracks.get(at)[0]));
      at++;
      if (at >= tracks.size()) {
        if (chain == this) chain = null;
        return null;
      }
      String[] t = tracks.get(at);
      return expect.drumMidiArgs(t[1], setName(t[0]) + ".mid", saved);
    }

    /** The model now in DrumMidi. */
    public synchronized String model() {
      return at >= 0 && at < tracks.size() ? tracks.get(at)[0] : "";
    }

    public int size() {
      return tracks.size();
    }

    public int index() {
      return at + 1;
    }
  }

  /** The chain going on, or null. */
  static Chain chain;

  /** Starts a chain on the WAVs given ("model", path pairs); null when there are none. `saved` is DrumMidi's saved switches. */
  public static synchronized Chain startChain(List<String> argv, List<String> fileSets, List<String[]> wavs, String saved) {
    if (wavs == null || wavs.isEmpty()) return null;
    Chain c = new Chain(runName(fileSets), MusicExpect.of(argv), saved);
    c.tracks.addAll(wavs);
    chain = c;
    return c;
  }

  public static synchronized Chain chain() {
    return chain;
  }

  /** Whether a run that just ended was the chain's DrumMidi_CRT (PyJav then starts the next). */
  public static synchronized boolean chainStep(String program) {
    return chain != null && chain.at >= 0 && "DrumMidi_CRT.jar".equals(program);
  }

  /** Ends a chain (Stop, or a run that failed). */
  public static synchronized void stopChain() {
    chain = null;
  }

  /** The ranking's file name, beside the tracks: sogni-Rock-6f1262f1-ranking.txt (from the first track's name). */
  public static String reportName(String log) {
    // --random: sogni-random-<seed>-ranking.txt (its tracks each have a style of their own).
    java.util.regex.Matcher seed = java.util.regex.Pattern.compile("Random styles: .*seed (\\d+)").matcher(log == null ? "" : log);
    if (seed.find()) return "sogni-random-" + seed.group(1) + "-ranking.txt";
    List<String[]> t = tracks(log);
    String first = t.isEmpty() ? "sogni-music" : t.get(0)[1].replaceAll("\\.[A-Za-z0-9]{2,4}$", "");
    if (!t.isEmpty() && first.endsWith("-" + t.get(0)[0])) first = first.substring(0, first.length() - t.get(0)[0].length() - 1);
    return first + "-ranking.txt";
  }

  /** SogniMusic --random N: N tracks of random styles, measured (and made into file sets, zipped) as a comparison is. */
  public static final String RANDOM = "--random";

  /** A SogniMusic run that compared models, or made random styles' tracks. */
  public static boolean wanted(String program, List<String> argv) {
    return "SogniMusic.java".equals(program) && argv != null && (argv.contains(FLAG) || argv.contains(RANDOM));
  }

  /** The tracks a comparison wrote, as "model<TAB>file" pairs, from its "Compared: model file" lines. */
  public static List<String[]> tracks(String log) {
    List<String[]> out = new ArrayList<String[]>();
    if (log == null) return out;
    for (String line : log.split("\n")) {
      String t = line.trim();
      if (!t.startsWith("Compared: ")) continue;
      String[] p = t.substring(10).trim().split(" ", 2);
      if (p.length == 2) out.add(new String[] {p[0], p[1].trim()});
    }
    return out;
  }

  /**
   * The comparison of a run's tracks: each "Compared:" file measured (its sound from `soundOf`, which
   * decodes it as the app can: null when it cannot), then ranked. Null when fewer than two.
   */
  public static String of(List<String> argv, String log, java.util.function.Function<String, AudioIo.Pcm> soundOf) {
    List<Take> takes = new ArrayList<Take>();
    for (String[] t : tracks(log)) {
      AudioIo.Pcm sound;
      try {
        sound = soundOf.apply(t[1]);
      } catch (RuntimeException ex) {
        sound = null;
      }
      if (sound == null) {
        Take missing = new Take();
        missing.model = t[0];
        missing.file = t[1];
        missing.problem = "could not be read";
        takes.add(missing);
      } else {
        takes.add(measure(t[0], t[1], sound));
      }
    }
    boolean random = argv != null && argv.contains(RANDOM);
    return takes.size() < (random ? 1 : 2) ? null : report(MusicExpect.of(argv), takes, random ? "Random styles" : "Model comparison");
  }

  /** Measures a track. */
  public static Take measure(String model, String file, AudioIo.Pcm sound) {
    Take t = new Take();
    t.model = model;
    t.file = file;
    measure(t, sound.samples, sound.sr);
    return t;
  }

  static void measure(Take t, float[] x, int sr) {
    t.seconds = x.length / (double) sr;
    // About 11 kHz is enough to tell a drum hit from a held note.
    int k = Math.max(1, Math.round(sr / 11025f));
    int n = x.length / k;
    double rate = sr / (double) k;
    float[] y = new float[n];
    for (int i = 0; i < n; i++) {
      float s = 0;
      for (int j = 0; j < k; j++) s += x[i * k + j];
      y[i] = s / k;
    }
    int size = 512, hop = 256, bins = size / 2 + 1;
    int frames = n < size ? 0 : 1 + (n - size) / hop;
    if (frames < 20) {
      t.problem = "too short to measure";
      return;
    }
    float[][] mag = new float[frames][bins];
    double[] win = new double[size];
    for (int i = 0; i < size; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / size);
    double[] re = new double[size], im = new double[size];
    for (int f = 0; f < frames; f++) {
      for (int i = 0; i < size; i++) {
        re[i] = y[f * hop + i] * win[i];
        im[i] = 0;
      }
      HitCompare.fft(re, im);
      for (int b = 0; b < bins; b++) mag[f][b] = (float) Math.hypot(re[b], im[b]);
    }
    // Harmonic/percussive split: a held tone is steady along time, a hit is spread along frequency.
    int span = 17, half = span / 2;
    float[] buf = new float[span];
    double perc = 0, harm = 0;
    float[] flux = new float[frames];
    for (int f = 0; f < frames; f++) {
      for (int b = 0; b < bins; b++) {
        int c = 0;
        for (int g = Math.max(0, f - half); g <= Math.min(frames - 1, f + half); g++) buf[c++] = mag[g][b];
        float h = median(buf, c);
        c = 0;
        for (int a = Math.max(0, b - half); a <= Math.min(bins - 1, b + half); a++) buf[c++] = mag[f][a];
        float p = median(buf, c);
        double e = (double) mag[f][b] * mag[f][b];
        double hp = (double) h * h, pp = (double) p * p;
        if (hp + pp > 0) {
          perc += e * pp / (hp + pp);
          harm += e * hp / (hp + pp);
        }
        if (f > 0) flux[f] += Math.max(0f, mag[f][b] - mag[f - 1][b]);
      }
    }
    t.drums = perc + harm > 0 ? perc / (perc + harm) : 0;
    t.bpm = tempo(flux, rate / hop);
  }

  /** The tempo of an onset curve (frames per second `fps`), 60 to 200 BPM. */
  static double tempo(float[] flux, double fps) {
    int n = flux.length;
    double mean = 0;
    for (float v : flux) mean += v;
    mean /= n;
    double[] d = new double[n];
    for (int i = 0; i < n; i++) d[i] = flux[i] - mean;
    int lo = (int) Math.floor(fps * 60 / 200), hi = (int) Math.ceil(fps * 60 / 60);
    double best = -1;
    int bestLag = 0;
    for (int lag = Math.max(1, lo); lag <= Math.min(hi, n - 1); lag++) {
      double s = 0;
      for (int i = lag; i < n; i++) s += d[i] * d[i - lag];
      s /= n - lag;
      if (s > best) {
        best = s;
        bestLag = lag;
      }
    }
    if (bestLag == 0) return 0;
    // Between lags: the peak's centre (a parabola through its neighbours).
    double l = bestLag;
    if (bestLag > 1 && bestLag < n - 1) {
      double a = corr(d, bestLag - 1), b = best, c = corr(d, bestLag + 1);
      double den = a - 2 * b + c;
      if (den < 0) l = bestLag + 0.5 * (a - c) / den;
    }
    return 60 * fps / l;
  }

  static double corr(double[] d, int lag) {
    double s = 0;
    for (int i = lag; i < d.length; i++) s += d[i] * d[i - lag];
    return s / (d.length - lag);
  }

  static float median(float[] a, int n) {
    float[] c = Arrays.copyOf(a, n);
    Arrays.sort(c);
    return c[n / 2];
  }

  /** Whether `bpm` is the tempo asked, or its half or double (a beat counted in halves). */
  public static boolean tempoMatches(double bpm, double asked) {
    if (bpm <= 0 || asked <= 0) return false;
    for (double m : new double[] {1, 0.5, 2}) if (Math.abs(bpm - asked * m) <= asked * m * 0.04) return true;
    return false;
  }

  /** The tracks scored and ranked, best first, and the report's text. */
  public static String report(MusicExpect e, List<Take> takes) {
    return report(e, takes, "Model comparison");
  }

  /** The report under its title ("Model comparison", "Random styles"). */
  public static String report(MusicExpect e, List<Take> takes, String title) {
    for (Take t : takes) {
      if (t.problem != null) {
        t.score = -1;
        continue;
      }
      double tempo = e.bpm > 0 ? (tempoMatches(t.bpm, e.bpm) ? 1 : 0) : 1;
      double length = e.duration > 0 ? Math.max(0, 1 - Math.abs(t.seconds - e.duration) / e.duration) : 1;
      t.score = e.drumsOnly ? 0.6 * t.drums + 0.25 * tempo + 0.15 * length : 0.6 * tempo + 0.4 * length;
    }
    List<Take> ranked = new ArrayList<Take>(takes);
    ranked.sort((a, b) -> Double.compare(b.score, a.score));
    List<String> asked = new ArrayList<String>();
    if (e.drumsOnly) asked.add("drums only");
    if (e.bpm > 0) asked.add(MusicExpect.SogniTime.num(e.bpm) + " BPM");
    if (e.duration > 0) asked.add(MusicExpect.SogniTime.num(e.duration) + " s");
    StringBuilder sb = new StringBuilder(title + (asked.isEmpty() ? "" : " (asked: " + MusicExpect.join(asked) + ")") + ":\n");
    int place = 1;
    for (Take t : ranked) {
      sb.append(place++).append(". ").append(t.model).append(": ").append(t.file);
      if (t.problem != null) {
        sb.append(" ").append(t.problem).append('\n');
        continue;
      }
      sb.append(" · ").append(String.format(Locale.ROOT, "%.0f", t.seconds)).append(" s");
      // A beat counted in halves or doubles is the tempo asked: shown as the asked one.
      double shownBpm = t.bpm;
      if (e.bpm > 0) for (double m : new double[] {0.5, 2}) if (tempoMatches(t.bpm, e.bpm) && Math.abs(t.bpm - e.bpm * m) <= e.bpm * m * 0.04) shownBpm = t.bpm / m;
      if (t.bpm > 0) sb.append(" · ").append(String.format(Locale.ROOT, "%.0f", shownBpm)).append(" BPM")
          .append(e.bpm > 0 ? (tempoMatches(t.bpm, e.bpm) ? " (as asked)" : " (asked " + MusicExpect.SogniTime.num(e.bpm) + ")") : "");
      sb.append(" · drums ").append(Math.round(t.drums * 100)).append("% of the sound");
      sb.append(" · score ").append(Math.round(t.score * 100)).append('\n');
    }
    if (!ranked.isEmpty() && ranked.get(0).problem == null) {
      sb.append("Best: ").append(ranked.get(0).model).append(e.drumsOnly ? " (the most drums and the tempo asked)" : " (the tempo and length asked)").append('\n');
    }
    sb.append(e.drumsOnly
        ? "Drums % is the hits' share against held tones (bass, synths, chords): near 100% is drums alone. Listen to the best before using it."
        : "Which instruments play cannot be measured here: listen to each before choosing.");
    return sb.toString();
  }
}
