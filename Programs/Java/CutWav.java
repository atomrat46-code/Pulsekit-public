/**
 * CutWav: keeps the start of a WAV file and writes it as a new WAV.
 *
 *   java CutWav <input.wav> [output.wav] [--split_time mm:ss.ms] [--split_lenght MB] [--trim] [--bpm N]
 *
 * output.wav   Default: <input>_cutted.wav next to the input.
 * --split_time Keep 00:00 up to this time: mm:ss, or mm:ss.ms with milliseconds (02:45.250).
 *              mm.ss works too (02.45 = 2 min 45 s), and a plain number is seconds.
 * --split_lenght  Keep the start so the whole output file is at most this many MB
 *              (1 MB = 1,000,000 bytes), e.g. 29.5. --split_length works too.
 *              With both, the shorter cut wins.
 * --trim       Move the cut to the end of the nearest bar (4 beats). The tempo and the first
 *              beat are found from the audio, or the tempo comes from --bpm. With --split_lenght
 *              the cut moves back to a bar end, so the file stays within the size.
 *              --trim alone cuts after the last whole bar.
 * --bpm N      The tempo for --trim, instead of finding it.
 *
 * The last 10 ms fade out, so the cut does not click. Works with 8, 16, 24 and 32-bit PCM and
 * 32-bit float WAV, mono or stereo. Plain Java 8, so PyJav can compile it on a phone.
 */
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CutWav {
  private CutWav() {}

  static final int BEATS_PER_BAR = 4;
  static final double FADE_SEC = 0.010;

  public static void main(String[] args) {
    try {
      run(args);
    } catch (Throwable ex) {
      String m = ex.getMessage();
      System.out.println("Failed: " + (m == null || m.length() == 0 ? ex.toString() : m));
    }
  }

  static void usage() {
    System.out.println("Usage: java CutWav <input.wav> [output.wav] [--split_time mm:ss.ms] [--split_lenght MB] [--trim] [--bpm N]");
  }

  static void run(String[] args) throws Exception {
    List<String> files = new ArrayList<String>();
    double splitTime = -1;
    double splitMb = -1;
    double bpm = 0;
    boolean trim = false;
    for (int i = 0; i < args.length; i++) {
      String a = args[i] == null ? "" : args[i].trim();
      if (a.length() == 0) continue;
      if ("--split_time".equals(a)) splitTime = parseTime(value(args, ++i, a));
      else if ("--split_lenght".equals(a) || "--split_length".equals(a)) splitMb = parseNumber(value(args, ++i, a), a);
      else if ("--bpm".equals(a)) bpm = parseNumber(value(args, ++i, a), a);
      else if ("--trim".equals(a)) trim = true;
      else if (a.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + a);
      else files.add(a);
    }
    if (files.isEmpty()) {
      usage();
      System.out.println("Failed: need an input WAV");
      return;
    }
    if (splitTime < 0 && splitMb < 0 && !trim) {
      usage();
      System.out.println("Failed: give --split_time, --split_lenght or --trim");
      return;
    }
    File in = new File(files.get(0));
    if (!in.isFile()) throw new IllegalArgumentException("No such file: " + in.getName());
    File out = files.size() > 1 ? new File(files.get(1)) : defaultOutput(in);
    if (out.getAbsoluteFile().equals(in.getAbsoluteFile())) throw new IllegalArgumentException("The output would replace the input: " + out.getName());

    Wav wav = Wav.read(in);
    double total = wav.frames / (double) wav.sampleRate;
    System.out.println(String.format(Locale.ROOT, "Read %s: %s, %d Hz, %d ch, %d-bit%s, %.1f MB",
        in.getName(), clock(total), wav.sampleRate, wav.channels, wav.bits, wav.isFloat ? " float" : "", in.length() / 1e6));

    long endFrame = wav.frames;
    String why = "the whole file";
    if (splitTime >= 0) {
      endFrame = Math.min(endFrame, Math.round(splitTime * wav.sampleRate));
      why = "--split_time " + clock(splitTime);
    }
    long sizeLimitFrames = Long.MAX_VALUE;
    if (splitMb >= 0) {
      long bytes = (long) Math.floor(splitMb * 1e6) - wav.headerSize();
      sizeLimitFrames = Math.max(0, bytes / wav.blockAlign);
      if (sizeLimitFrames < endFrame) {
        endFrame = sizeLimitFrames;
        why = "--split_lenght " + number(splitMb) + " MB";
      }
    }
    if (trim) {
      Beat beat = bpm > 0 ? Beat.withTempo(wav, in, bpm) : Beat.find(wav, in);
      double barSec = BEATS_PER_BAR * 60.0 / beat.bpm;
      System.out.println(String.format(Locale.ROOT, "Tempo: %.2f BPM%s, first bar at %.3f s, bar %.3f s",
          beat.bpm, bpm > 0 ? " (--bpm)" : "", beat.firstBar, barSec));
      double target = endFrame / (double) wav.sampleRate;
      double limit = Math.min(total, sizeLimitFrames == Long.MAX_VALUE ? total : sizeLimitFrames / (double) wav.sampleRate);
      double n = (target - beat.firstBar) / barSec;
      long k = splitTime < 0 && splitMb < 0 ? (long) Math.floor(n) : Math.round(n);
      double cut = beat.firstBar + k * barSec;
      // Never past the size limit or the end of the file; then the bar end before it.
      while (cut > limit + 1e-6 && k > 0) cut = beat.firstBar + (--k) * barSec;
      if (cut <= 0) throw new IllegalArgumentException("No whole bar fits: the cut would be at " + clock(cut));
      System.out.println(String.format(Locale.ROOT, "--trim: %s -> %s, the end of bar %d", clock(target), clock(cut), k));
      endFrame = Math.round(cut * wav.sampleRate);
      why = why + ", trimmed to a bar end";
    }
    endFrame = Math.max(1, Math.min(endFrame, wav.frames));

    wav.write(in, out, endFrame, FADE_SEC);
    double kept = endFrame / (double) wav.sampleRate;
    System.out.println(String.format(Locale.ROOT, "Wrote %s: 00:00-%s (%s), %.2f MB, cut at %s",
        out.getName(), clock(kept), why, out.length() / 1e6, clock(kept)));
    System.out.println("Succeeded: " + out.getName());
  }

  static String value(String[] args, int i, String opt) {
    if (i >= args.length || args[i] == null || args[i].trim().length() == 0) throw new IllegalArgumentException(opt + " needs a value");
    return args[i].trim();
  }

  static double parseNumber(String v, String opt) {
    try {
      double d = Double.parseDouble(v.replace(',', '.'));
      if (d < 0) throw new NumberFormatException();
      return d;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(opt + " needs a number, not " + v);
    }
  }

  /** mm:ss, mm:ss.ms, hh:mm:ss, mm.ss (02.45 = 2:45) or plain seconds. */
  static double parseTime(String v) {
    String t = v.trim().replace(',', '.');
    try {
      if (t.indexOf(':') >= 0) {
        String[] p = t.split(":");
        double sec = Double.parseDouble(p[p.length - 1]);
        double min = Double.parseDouble(p[p.length - 2]);
        double hours = p.length > 2 ? Double.parseDouble(p[p.length - 3]) : 0;
        if (sec >= 60 || sec < 0 || min < 0) throw new NumberFormatException();
        return hours * 3600 + min * 60 + sec;
      }
      int dot = t.indexOf('.');
      if (dot > 0 && t.indexOf('.', dot + 1) < 0 && t.length() - dot - 1 == 2) {
        double min = Double.parseDouble(t.substring(0, dot));
        double sec = Double.parseDouble(t.substring(dot + 1));
        if (sec >= 60) throw new NumberFormatException();
        return min * 60 + sec;
      }
      return Double.parseDouble(t);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("--split_time needs mm:ss or mm:ss.ms, such as 02:45 or 02:45.250, not " + v);
    }
  }

  static File defaultOutput(File in) {
    String name = in.getName();
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    File dir = in.getAbsoluteFile().getParentFile();
    return new File(dir, stem + "_cutted.wav");
  }

  static String clock(double sec) {
    if (sec < 0) sec = 0;
    long ms = Math.round(sec * 1000);
    return String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
  }

  static String number(double v) {
    String t = String.format(Locale.ROOT, "%.2f", v);
    while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
    if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
    return t;
  }

  /** A WAV file's format and where its audio is. The audio is read and copied in pieces. */
  static final class Wav {
    int channels;
    int sampleRate;
    int bits;
    int blockAlign;
    boolean isFloat;
    byte[] fmt;
    long dataAt;
    long frames;

    long headerSize() {
      return 12 + 8 + fmt.length + (fmt.length & 1) + 8;
    }

    static Wav read(File f) throws Exception {
      RandomAccessFile raf = new RandomAccessFile(f, "r");
      try {
        byte[] head = new byte[12];
        raf.readFully(head);
        if (head[0] != 'R' || head[1] != 'I' || head[2] != 'F' || head[3] != 'F' || head[8] != 'W' || head[9] != 'A') {
          throw new IllegalArgumentException(f.getName() + " is not a WAV file");
        }
        Wav w = new Wav();
        long pos = 12;
        long len = raf.length();
        while (pos + 8 <= len) {
          raf.seek(pos);
          byte[] h = new byte[8];
          raf.readFully(h);
          String id = new String(h, 0, 4, "US-ASCII");
          long size = le32(h, 4);
          long body = pos + 8;
          if ("fmt ".equals(id)) {
            w.fmt = new byte[(int) Math.min(size, 1024)];
            raf.readFully(w.fmt);
            int tag = le16(w.fmt, 0);
            w.channels = le16(w.fmt, 2);
            w.sampleRate = (int) le32(w.fmt, 4);
            w.blockAlign = le16(w.fmt, 12);
            w.bits = le16(w.fmt, 14);
            if (tag == 0xFFFE && w.fmt.length >= 26) tag = le16(w.fmt, 24);
            w.isFloat = tag == 3;
            if (tag != 1 && tag != 3) throw new IllegalArgumentException("Only PCM or float WAV files can be cut (format " + tag + ")");
          } else if ("data".equals(id)) {
            if (w.fmt == null) throw new IllegalArgumentException("The WAV has its audio before its format");
            w.dataAt = body;
            long dataLen = Math.min(size, len - body);
            w.frames = dataLen / Math.max(1, w.blockAlign);
            return w;
          }
          pos = body + size + (size & 1);
        }
        throw new IllegalArgumentException("The WAV has no audio data");
      } finally {
        raf.close();
      }
    }

    /** Writes frames [0, endFrame) as a new WAV, fading out the last `fadeSec`. */
    void write(File in, File out, long endFrame, double fadeSec) throws Exception {
      File parent = out.getAbsoluteFile().getParentFile();
      if (parent != null && !parent.isDirectory()) parent.mkdirs();
      long dataLen = endFrame * blockAlign;
      long fadeFrames = Math.min(endFrame, Math.round(fadeSec * sampleRate));
      RandomAccessFile src = new RandomAccessFile(in, "r");
      FileOutputStream dst = new FileOutputStream(out);
      try {
        byte[] h = new byte[(int) headerSize()];
        put(h, 0, "RIFF");
        put32(h, 4, h.length - 8 + dataLen + (dataLen & 1));
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        put32(h, 16, fmt.length);
        System.arraycopy(fmt, 0, h, 20, fmt.length);
        int at = 20 + fmt.length + (fmt.length & 1);
        put(h, at, "data");
        put32(h, at + 4, dataLen);
        dst.write(h);
        src.seek(dataAt);
        byte[] buf = new byte[blockAlign * 16384];
        long frame = 0;
        while (frame < endFrame) {
          int n = (int) Math.min(buf.length / blockAlign, endFrame - frame);
          src.readFully(buf, 0, n * blockAlign);
          for (int i = 0; i < n; i++) {
            long left = endFrame - (frame + i);
            if (left <= fadeFrames) scale(buf, i * blockAlign, (left - 1) / (double) Math.max(1, fadeFrames));
          }
          dst.write(buf, 0, n * blockAlign);
          frame += n;
        }
        if ((dataLen & 1) == 1) dst.write(0);
      } finally {
        dst.close();
        src.close();
      }
    }

    /** Scales every sample of one frame by g. */
    void scale(byte[] b, int at, double g) {
      int bytes = bits / 8;
      for (int c = 0; c < channels; c++) {
        int p = at + c * bytes;
        if (isFloat && bytes == 4) {
          int raw = (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | ((b[p + 2] & 0xff) << 16) | (b[p + 3] << 24);
          int v = Float.floatToIntBits((float) (Float.intBitsToFloat(raw) * g));
          b[p] = (byte) v; b[p + 1] = (byte) (v >> 8); b[p + 2] = (byte) (v >> 16); b[p + 3] = (byte) (v >> 24);
        } else if (bytes == 1) {
          int v = (int) Math.round(((b[p] & 0xff) - 128) * g) + 128;
          b[p] = (byte) v;
        } else if (bytes == 2) {
          int v = (int) Math.round((short) ((b[p] & 0xff) | (b[p + 1] << 8)) * g);
          b[p] = (byte) v; b[p + 1] = (byte) (v >> 8);
        } else if (bytes == 3) {
          int v = (int) Math.round(((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | (b[p + 2] << 16)) * g);
          b[p] = (byte) v; b[p + 1] = (byte) (v >> 8); b[p + 2] = (byte) (v >> 16);
        } else if (bytes == 4) {
          long v = Math.round(((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | ((b[p + 2] & 0xff) << 16) | (b[p + 3] << 24)) * g);
          b[p] = (byte) v; b[p + 1] = (byte) (v >> 8); b[p + 2] = (byte) (v >> 16); b[p + 3] = (byte) (v >> 24);
        }
      }
    }

    /** One mono sample in [-1, 1] at frame-relative byte offset `at`. */
    double mono(byte[] b, int at) {
      int bytes = bits / 8;
      double sum = 0;
      for (int c = 0; c < channels; c++) {
        int p = at + c * bytes;
        double v;
        if (isFloat && bytes == 4) v = Float.intBitsToFloat((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | ((b[p + 2] & 0xff) << 16) | (b[p + 3] << 24));
        else if (bytes == 1) v = ((b[p] & 0xff) - 128) / 128.0;
        else if (bytes == 2) v = (short) ((b[p] & 0xff) | (b[p + 1] << 8)) / 32768.0;
        else if (bytes == 3) v = ((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | (b[p + 2] << 16)) / 8388608.0;
        else v = ((b[p] & 0xff) | ((b[p + 1] & 0xff) << 8) | ((b[p + 2] & 0xff) << 16) | (b[p + 3] << 24)) / 2147483648.0;
        sum += v;
      }
      return sum / channels;
    }
  }

  /** Tempo and the first downbeat, for --trim. */
  static final class Beat {
    double bpm;
    double firstBar;

    /** The last onsets() split into the low band (kick) and the rest (snare, hats). */
    static double[] lowOn;
    static double[] highOn;

    /** Onset strength per hop: rises in low (kick) and high (snare, hats) energy, from the whole file. */
    static double[] onsets(Wav w, File f, int hop) throws Exception {
      RandomAccessFile raf = new RandomAccessFile(f, "r");
      try {
        int frames = (int) Math.min(Integer.MAX_VALUE - 8, w.frames / hop);
        double[] low = new double[frames];
        double[] high = new double[frames];
        raf.seek(w.dataAt);
        byte[] buf = new byte[w.blockAlign * hop];
        double lp = 0;
        double a = Math.exp(-2 * Math.PI * 150.0 / w.sampleRate);
        for (int i = 0; i < frames; i++) {
          raf.readFully(buf);
          double el = 0;
          double eh = 0;
          for (int s = 0; s < hop; s++) {
            double x = w.mono(buf, s * w.blockAlign);
            lp = a * lp + (1 - a) * x;
            double hp = x - lp;
            el += lp * lp;
            eh += hp * hp;
          }
          low[i] = Math.log1p(1000 * el / hop);
          high[i] = Math.log1p(1000 * eh / hop);
        }
        double[] onset = new double[frames];
        lowOn = new double[frames];
        highOn = new double[frames];
        for (int i = 1; i < frames; i++) {
          lowOn[i] = Math.max(0, low[i] - low[i - 1]);
          highOn[i] = Math.max(0, high[i] - high[i - 1]);
          onset[i] = lowOn[i] + highOn[i];
        }
        return onset;
      } finally {
        raf.close();
      }
    }

    static Beat find(Wav w, File f) throws Exception {
      int hop = Math.max(64, w.sampleRate / 200);
      double fps = w.sampleRate / (double) hop;
      double[] on = onsets(w, f, hop);
      // Rough tempo: the strongest autocorrelation between 70 and 180 BPM, leaning toward 120.
      double bestScore = -1;
      double bestBpm = 120;
      for (double b = 70; b <= 180.0001; b += 0.05) {
        double lag = 60.0 * fps / b;
        double sc = 0;
        int n = 0;
        for (int i = 0; i + lag * 4 + 1 < on.length; i += 2) {
          sc += on[i] * (interp(on, i + lag) + 0.5 * interp(on, i + 2 * lag) + 0.25 * interp(on, i + 4 * lag));
          n++;
        }
        sc = n == 0 ? 0 : sc / n;
        sc *= 0.8 + 0.2 * Math.exp(-0.5 * Math.pow(Math.log(b / 120.0) / 0.5, 2));
        if (sc > bestScore) {
          bestScore = sc;
          bestBpm = b;
        }
      }
      // Exact tempo: within 3% of that, the beat grid over the whole file whose beats land on the
      // most onset energy. Over a whole song a grid only fits at the right tempo.
      double coarse = bestBpm;
      bestScore = -1;
      for (double b = coarse * 0.97; b <= coarse * 1.03; b += 0.01) {
        double sc = gridScore(on, fps, b, 48);
        if (sc > bestScore) {
          bestScore = sc;
          bestBpm = b;
        }
      }
      return withOnsets(on, fps, bestBpm);
    }

    /** Mean onset energy on the beats of the best of `phases` grids at this tempo. */
    static double gridScore(double[] on, double fps, double bpm, int phases) {
      double beat = 60.0 * fps / bpm;
      double best = 0;
      for (int k = 0; k < phases; k++) {
        double sc = 0;
        int n = 0;
        for (double t = beat * k / phases; t < on.length - 1; t += beat) {
          sc += interp(on, t);
          n++;
        }
        if (n > 0 && sc / n > best) best = sc / n;
      }
      return best;
    }

    static Beat withTempo(Wav w, File f, double bpm) throws Exception {
      int hop = Math.max(64, w.sampleRate / 200);
      return withOnsets(onsets(w, f, hop), w.sampleRate / (double) hop, bpm);
    }

    /** The beat phase with the most onset energy on the beats, then the downbeat of four. */
    static Beat withOnsets(double[] on, double fps, double bpm) {
      double beat = 60.0 * fps / bpm;
      int steps = 64;
      double bestPhase = 0;
      double best = -1;
      for (int k = 0; k < steps; k++) {
        double ph = beat * k / steps;
        double sc = 0;
        for (double t = ph; t < on.length - 1; t += beat) sc += interp(on, t);
        if (sc > best) {
          best = sc;
          bestPhase = ph;
        }
      }
      // Downbeat. Snares (the upper band) sit on beats 2 and 4, kicks on 1 and 3, so the pair of
      // beats with the most kick and the least snare is 1 and 3; of those two, beat 1 has more kick.
      double bar = beat * BEATS_PER_BAR;
      double[] lowAt = new double[BEATS_PER_BAR];
      double[] highAt = new double[BEATS_PER_BAR];
      for (int d = 0; d < BEATS_PER_BAR; d++) {
        for (double t = bestPhase + d * beat; t < on.length - 1; t += bar) {
          lowAt[d] += lowOn == null ? 0 : interp(lowOn, t);
          highAt[d] += highOn == null ? interp(on, t) : interp(highOn, t);
        }
      }
      int down = 0;
      double strongest = -Double.MAX_VALUE;
      for (int d = 0; d < 2; d++) {
        double sc = lowAt[d] + lowAt[d + 2] - highAt[d] - highAt[d + 2] + highAt[d + 1] + highAt[(d + 3) % 4];
        if (sc > strongest) {
          strongest = sc;
          down = d;
        }
      }
      if (lowAt[down + 2] > lowAt[down]) down += 2;
      Beat r = new Beat();
      r.bpm = bpm;
      double first = (bestPhase + down * beat) / fps;
      double barSec = BEATS_PER_BAR * 60.0 / bpm;
      while (first - barSec >= -1e-9) first -= barSec;
      r.firstBar = first;
      return r;
    }

    static double interp(double[] a, double x) {
      int i = (int) x;
      if (i < 0 || i + 1 >= a.length) return 0;
      double f = x - i;
      return a[i] * (1 - f) + a[i + 1] * f;
    }
  }

  static long le32(byte[] d, int i) {
    return (d[i] & 0xffL) | ((d[i + 1] & 0xffL) << 8) | ((d[i + 2] & 0xffL) << 16) | ((d[i + 3] & 0xffL) << 24);
  }

  static int le16(byte[] d, int i) {
    return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8);
  }

  static void put(byte[] d, int i, String s) {
    for (int k = 0; k < 4; k++) d[i + k] = (byte) s.charAt(k);
  }

  static void put32(byte[] d, int i, long v) {
    d[i] = (byte) v;
    d[i + 1] = (byte) (v >> 8);
    d[i + 2] = (byte) (v >> 16);
    d[i + 3] = (byte) (v >> 24);
  }
}
