/**
 * Wav2Mp3: turns a WAV file into an MP3 (MPEG-1 Layer III, constant bitrate).
 *
 *   java Wav2Mp3 <input.wav> [output.mp3] [--bitrate KBPS] [--mode joint|stereo|mono] [--title T] [--artist A] [--album A] [--dir FOLDER] [--recursive] [--outdir FOLDER]
 *
 * output.mp3   Default: <input>.mp3 next to the input.
 * --bitrate    32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192 (default), 224, 256 or 320 kbps.
 * --mode       joint (default: mid/side stereo, better at a given bitrate), stereo (left and
 *              right apart) or mono (a stereo input is mixed down). A mono input is mono.
 * --title, --artist, --album  Written as an ID3v2 tag at the start of the file.
 * --dir FOLDER Every WAV in the folder, each to <its name>.mp3; output.mp3 is not used.
 * --recursive  With --dir: its subfolders' WAVs too.
 * --outdir FOLDER  With --dir: the MP3s go there (made when missing), not beside each WAV.
 *
 * 32, 44.1 and 48 kHz are kept; other rates are resampled to the nearest of those (22.05 and
 * 11.025 kHz to 44.1, 24 and 96 to 48, 16 and 8 to 32, 88.2 to 44.1). Works with 8, 16, 24 and
 * 32-bit PCM and 32-bit float WAV, mono or stereo (more channels: the first two). The encoder is
 * a plain one (no psychoacoustic model, long blocks only), so higher bitrates sound best.
 * Plain Java 8, so PyJav can compile it on a phone.
 */
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Wav2Mp3 {
  private Wav2Mp3() {}

  public static void main(String[] args) {
    try {
      run(args);
    } catch (Throwable ex) {
      String m = ex.getMessage();
      System.out.println("Failed: " + (m == null || m.length() == 0 ? ex.toString() : m));
    }
  }

  static void usage() {
    System.out.println("Usage: java Wav2Mp3 <input.wav> [output.mp3] [--bitrate KBPS] [--mode joint|stereo|mono] [--title T] [--artist A] [--album A] [--dir FOLDER] [--recursive] [--outdir FOLDER]");
  }

  static final java.util.Set<String> TAKES_VALUE = new java.util.HashSet<String>(java.util.Arrays.asList("--bitrate", "--mode", "--title", "--artist", "--album"));

  static void run(String[] args) throws Exception {
    if (!batch(args)) runOne(args);
  }

  /** --dir: the folder's WAV files, each processed as one input; --outdir: where the outputs go (default: beside each). */
  static File outDir;

  static boolean batch(String[] args) throws Exception {
    String dir = null, outdir = null;
    boolean recursive = false;
    List<String> rest = new ArrayList<String>();
    for (int i = 0; i < args.length; i++) {
      String a = args[i] == null ? "" : args[i].trim();
      if ("--dir".equals(a)) dir = value(args, ++i, a);
      else if ("--outdir".equals(a)) outdir = value(args, ++i, a);
      else if ("--recursive".equals(a)) recursive = true;
      else if (a.startsWith("--")) {
        rest.add(a);
        if (TAKES_VALUE.contains(a) && i + 1 < args.length) rest.add(args[++i]);
      }
      // Positional files are left out: each WAV of the folder is the input.
    }
    if (dir == null) return false;
    File folder = new File(dir);
    if (!folder.isDirectory() && !isPls(folder)) throw new IllegalArgumentException("No such folder: " + dir);
    if (outdir != null) {
      File o = new File(outdir);
      // In the app the run's work folder (pulsekit.work: Android keeps user.dir at "/", read-only).
      if (!o.isAbsolute()) o = new File(workDir(), outdir);
      if (!o.isDirectory() && !o.mkdirs()) throw new IllegalArgumentException("Could not make the folder " + o.getPath());
      outDir = o;
    }
    List<File> wavs = new ArrayList<File>();
    collect(folder, recursive, wavs);
    if (wavs.isEmpty()) {
      System.out.println("Failed: no WAV files in " + folder.getName() + (recursive ? " or its subfolders" : ""));
      return true;
    }
    System.out.println("Wav2Mp3: " + wavs.size() + " WAV file" + (wavs.size() == 1 ? "" : "s") + " in " + folder.getName() + (outDir != null ? ", to " + outDir.getPath() : ""));
    int ok = 0;
    List<String> failed = new ArrayList<String>();
    for (File f : wavs) {
      System.out.println("");
      System.out.println("== " + f.getName());
      List<String> one = new ArrayList<String>();
      one.add(f.getPath());
      one.addAll(rest);
      try {
        if (runOne(one.toArray(new String[0]))) ok++;
        else failed.add(f.getName());
      } catch (Throwable ex) {
        String m = ex.getMessage();
        System.out.println("Failed: " + f.getName() + ": " + (m == null || m.length() == 0 ? ex.toString() : m));
        failed.add(f.getName());
      }
    }
    System.out.println("");
    String sum = ok + " of " + wavs.size() + " WAV files done" + (failed.isEmpty() ? "" : "; not done: " + String.join(", ", failed));
    System.out.println(ok > 0 ? "Succeeded: " + sum : "Failed: " + sum);
    return true;
  }

  /** Where a relative --outdir is: the app's work folder for the run (pulsekit.work), else user.dir. */
  static String workDir() {
    String w = System.getProperty("pulsekit.work");
    return w != null && w.length() > 0 ? w : System.getProperty("user.dir", ".");
  }

  /** The folder's .wav files (its subfolders' too with --recursive), by name. */
  static void collect(File folder, boolean recursive, List<File> out) {
    File[] kids = listed(folder);
    if (kids == null) return;
    if (!isPls(folder)) java.util.Arrays.sort(kids);
    for (File k : kids) {
      String low = k.getName().toLowerCase(Locale.ROOT);
      if (k.isDirectory()) {
        if (recursive && !k.getName().startsWith(".")) collect(k, true, out);
      } else if ((low.endsWith(".wav") || low.endsWith(".wave")) && !low.startsWith(".")) {
        out.add(k);
      }
    }
  }

  static String value(String[] args, int i, String opt) {
    if (i >= args.length || args[i] == null || args[i].trim().length() == 0) throw new IllegalArgumentException(opt + " needs a value");
    return args[i].trim();
  }

  static final int[] BITRATES = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};

  /** One WAV; true when it was written. */
  static boolean runOne(String[] args) throws Exception {
    List<String> files = new ArrayList<String>();
    int kbps = 192;
    String mode = "joint";
    String title = null, artist = null, album = null;
    for (int i = 0; i < args.length; i++) {
      String a = args[i] == null ? "" : args[i].trim();
      if (a.length() == 0) continue;
      if ("--bitrate".equals(a)) {
        String v = value(args, ++i, a).toLowerCase(Locale.ROOT).replace("kbps", "").replace("k", "").trim();
        try {
          kbps = Integer.parseInt(v);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("--bitrate needs a number of kbps, not " + args[i]);
        }
      } else if ("--mode".equals(a)) {
        mode = value(args, ++i, a).toLowerCase(Locale.ROOT);
        if (mode.startsWith("j")) mode = "joint";
        else if (mode.startsWith("s")) mode = "stereo";
        else if (mode.startsWith("m")) mode = "mono";
        else throw new IllegalArgumentException("--mode is joint, stereo or mono, not " + args[i]);
      } else if ("--title".equals(a)) title = value(args, ++i, a);
      else if ("--artist".equals(a)) artist = value(args, ++i, a);
      else if ("--album".equals(a)) album = value(args, ++i, a);
      else if (a.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + a);
      else files.add(a);
    }
    if (files.isEmpty()) {
      usage();
      System.out.println("Failed: need an input WAV");
      return false;
    }
    int brIndex = -1;
    for (int b = 1; b < BITRATES.length; b++) if (BITRATES[b] == kbps) brIndex = b;
    if (brIndex < 0) throw new IllegalArgumentException("--bitrate " + kbps + " is not an MP3 bitrate (32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)");
    File in = new File(files.get(0));
    if (!in.isFile()) throw new IllegalArgumentException("No such file: " + in.getName());
    File out = files.size() > 1 ? new File(files.get(1)) : defaultOutput(in);
    if (out.getAbsoluteFile().equals(in.getAbsoluteFile())) throw new IllegalArgumentException("The output would replace the input: " + out.getName());

    Wav wav = Wav.read(in);
    double total = wav.frames / (double) wav.sampleRate;
    System.out.println(String.format(Locale.ROOT, "Read %s: %s, %d Hz, %d ch, %d-bit%s, %.1f MB",
        in.getName(), clock(total), wav.sampleRate, wav.channels, wav.bits, wav.isFloat ? " float" : "", in.length() / 1e6));
    int rate = mp3Rate(wav.sampleRate);
    int channels = wav.channels == 1 || "mono".equals(mode) ? 1 : 2;
    if (channels == 1) mode = "mono";
    if (rate != wav.sampleRate) System.out.println("Resampled " + wav.sampleRate + " Hz to " + rate + " Hz (MP3 keeps 32, 44.1 and 48 kHz)");

    File parent = out.getAbsoluteFile().getParentFile();
    if (parent != null && !parent.isDirectory()) parent.mkdirs();
    Source src = new Source(in, wav, rate, channels);
    OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 16);
    long frames;
    byte[] tag = id3(title, artist, album);
    Stream stream = new Stream(os);
    try {
      if (tag != null) os.write(tag);
      Encoder enc = new Encoder(rate, channels, brIndex, "joint".equals(mode));
      frames = enc.encode(src, stream);
    } finally {
      os.close();
      src.close();
    }
    // The Info frame's numbers, now that the audio is written.
    RandomAccessFile patch = new RandomAccessFile(out, "rw");
    try {
      patch.seek(tag == null ? 0 : tag.length);
      patch.write(stream.info());
    } finally {
      patch.close();
    }
    String label = "joint".equals(mode) ? "joint stereo" : mode;
    System.out.println(String.format(Locale.ROOT, "Wrote %s: %s, %d kbps %s, %d Hz, %d frames, %.2f MB",
        out.getName(), clock(total), kbps, label, rate, frames, out.length() / 1e6));
    System.out.println("Succeeded: " + out.getName());
    return true;
  }

  static int mp3Rate(int sr) {
    if (sr == 32000 || sr == 44100 || sr == 48000) return sr;
    if (sr % 11025 == 0) return 44100;
    if (sr == 16000 || sr == 8000 || sr == 32000) return 32000;
    if (sr % 12000 == 0) return 48000;
    // Any other rate: the nearest of the three.
    int best = 44100;
    for (int r : new int[] {32000, 44100, 48000}) if (Math.abs(r - sr) < Math.abs(best - sr)) best = r;
    return best;
  }

  static File defaultOutput(File in) {
    String name = in.getName();
    int dot = name.lastIndexOf('.');
    String stem = dot > 0 ? name.substring(0, dot) : name;
    File dir = outDir != null ? outDir : in.getAbsoluteFile().getParentFile();
    return new File(dir, stem + ".mp3");
  }

  static String clock(double sec) {
    if (sec < 0) sec = 0;
    long ms = Math.round(sec * 1000);
    return String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000);
  }

  // ------------------------------------------------------------------ ID3v2.3 tag

  /** An ID3v2.3 tag with the title, artist and album given (UTF-16 text), or null when none is. */
  static byte[] id3(String title, String artist, String album) throws Exception {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    frame(body, "TIT2", title);
    frame(body, "TPE1", artist);
    frame(body, "TALB", album);
    if (body.size() == 0) return null;
    ByteArrayOutputStream tag = new ByteArrayOutputStream();
    tag.write(new byte[] {'I', 'D', '3', 3, 0, 0});
    int n = body.size();
    tag.write((n >> 21) & 0x7f);
    tag.write((n >> 14) & 0x7f);
    tag.write((n >> 7) & 0x7f);
    tag.write(n & 0x7f);
    body.writeTo(tag);
    return tag.toByteArray();
  }

  static void frame(ByteArrayOutputStream out, String id, String text) throws Exception {
    if (text == null || text.length() == 0) return;
    byte[] t = text.getBytes("UTF-16");
    int n = t.length + 1;
    out.write(id.getBytes("US-ASCII"));
    out.write(new byte[] {(byte) (n >>> 24), (byte) (n >>> 16), (byte) (n >>> 8), (byte) n, 0, 0, 1});
    out.write(t);
  }

  // ------------------------------------------------------------------ WAV input

  static final class Wav {
    int channels;
    int sampleRate;
    int bits;
    int blockAlign;
    boolean isFloat;
    long dataAt;
    long frames;

    static Wav read(File f) throws Exception {
      RandomAccessFile raf = new RandomAccessFile(f, "r");
      try {
        byte[] head = new byte[12];
        raf.readFully(head);
        if (head[0] != 'R' || head[1] != 'I' || head[2] != 'F' || head[3] != 'F' || head[8] != 'W' || head[9] != 'A') {
          throw new IllegalArgumentException(f.getName() + " is not a WAV file");
        }
        Wav w = new Wav();
        boolean fmt = false;
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
            byte[] b = new byte[(int) Math.min(size, 1024)];
            raf.readFully(b);
            int tag = le16(b, 0);
            w.channels = le16(b, 2);
            w.sampleRate = (int) le32(b, 4);
            w.blockAlign = le16(b, 12);
            w.bits = le16(b, 14);
            if (tag == 0xFFFE && b.length >= 26) tag = le16(b, 24);
            w.isFloat = tag == 3;
            if (tag != 1 && tag != 3) throw new IllegalArgumentException("Only PCM or float WAV files can be read (format " + tag + ")");
            if (w.channels < 1 || w.sampleRate < 1000 || w.blockAlign < 1) throw new IllegalArgumentException("The WAV's format is damaged");
            fmt = true;
          } else if ("data".equals(id)) {
            if (!fmt) throw new IllegalArgumentException("The WAV has its audio before its format");
            w.dataAt = body;
            long dataLen = Math.min(size, len - body);
            w.frames = dataLen / w.blockAlign;
            return w;
          }
          pos = body + size + (size & 1);
        }
        throw new IllegalArgumentException("The WAV has no audio data");
      } finally {
        raf.close();
      }
    }
  }

  static int le16(byte[] b, int at) {
    return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
  }

  static long le32(byte[] b, int at) {
    return (b[at] & 0xffL) | (b[at + 1] & 0xffL) << 8 | (b[at + 2] & 0xffL) << 16 | (b[at + 3] & 0xffL) << 24;
  }

  /**
   * The WAV's audio as frames of `channels` floats (-1..1) at `rate`: read in blocks, mixed down
   * to mono when asked, and resampled (linear) when the WAV's rate is another one.
   */
  static final class Source {
    final Wav wav;
    final InputStream in;
    final int channels;
    final double step;
    long left;
    final byte[] buf;
    // The two input frames around the position (resampling), and where between them it is.
    final float[] a = new float[2], b = new float[2];
    double pos = 0;
    boolean started = false, ended = false;

    Source(File f, Wav wav, int rate, int channels) throws Exception {
      this.wav = wav;
      this.channels = channels;
      this.step = wav.sampleRate / (double) rate;
      FileInputStream fis = new FileInputStream(f);
      long skip = wav.dataAt;
      while (skip > 0) {
        long s = fis.skip(skip);
        if (s <= 0) break;
        skip -= s;
      }
      this.in = new BufferedInputStream(fis, 1 << 16);
      this.left = wav.frames;
      this.buf = new byte[wav.blockAlign];
    }

    /** The next input frame into `to` (2 floats); false at the end. */
    boolean readFrame(float[] to) throws Exception {
      if (left <= 0) return false;
      int got = 0;
      while (got < buf.length) {
        int n = in.read(buf, got, buf.length - got);
        if (n < 0) {
          left = 0;
          return false;
        }
        got += n;
      }
      left--;
      int bytes = wav.bits / 8;
      float l = sample(buf, 0, bytes);
      float r = wav.channels > 1 ? sample(buf, bytes, bytes) : l;
      if (channels == 1) {
        to[0] = wav.channels > 1 ? (l + r) * 0.5f : l;
        to[1] = to[0];
      } else {
        to[0] = l;
        to[1] = r;
      }
      return true;
    }

    float sample(byte[] b, int at, int bytes) {
      if (wav.isFloat && bytes == 4) {
        return Float.intBitsToFloat((b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | (b[at + 2] & 0xff) << 16 | (b[at + 3] & 0xff) << 24);
      }
      if (wav.isFloat && bytes == 8) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = v << 8 | (b[at + i] & 0xff);
        return (float) Double.longBitsToDouble(v);
      }
      switch (bytes) {
        case 1:
          return ((b[at] & 0xff) - 128) / 128f;
        case 2:
          return (short) ((b[at] & 0xff) | b[at + 1] << 8) / 32768f;
        case 3:
          return ((b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | b[at + 2] << 16) / 8388608f;
        default:
          return ((b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | (b[at + 2] & 0xff) << 16 | b[at + 3] << 24) / 2147483648f;
      }
    }

    /** Fills `l` and `r` with up to n samples; how many were filled (fewer at the end). */
    int read(float[] l, float[] r, int n) throws Exception {
      float[] f = new float[2];
      int i = 0;
      if (step == 1.0) {
        for (; i < n; i++) {
          if (!readFrame(f)) break;
          l[i] = f[0];
          r[i] = f[1];
        }
        return i;
      }
      if (!started) {
        started = true;
        if (!readFrame(a)) ended = true;
        if (!readFrame(b)) {
          b[0] = a[0];
          b[1] = a[1];
        }
      }
      for (; i < n && !ended; i++) {
        while (pos >= 1.0) {
          pos -= 1.0;
          a[0] = b[0];
          a[1] = b[1];
          if (!readFrame(b)) {
            ended = true;
            break;
          }
        }
        if (ended) break;
        float t = (float) pos;
        l[i] = a[0] + (b[0] - a[0]) * t;
        r[i] = a[1] + (b[1] - a[1]) * t;
        pos += step;
      }
      return i;
    }

    void close() {
      try {
        in.close();
      } catch (Exception ignored) {
        // read already
      }
    }
  }

  // ------------------------------------------------------------------ MP3 encoder

  /**
   * MPEG-1 Layer III, constant bitrate: the 32-band polyphase filter bank, an 18-point MDCT per
   * band (long blocks), alias reduction, a quantizer whose global gain is searched so each
   * granule fits its share of the frame, Huffman coding (big values in three regions with their
   * best tables, then the count1 quadruples), mid/side stereo for joint (frame by frame), a
   * low-pass by bitrate, and the bit reservoir: each granule is coded no finer than a quality
   * target, so quiet or simple frames leave bits for hard ones; the target follows the reservoir.
   * No scalefactors (no psychoacoustic model to shape the noise).
   */
  static final class Encoder {
    final int rate, channels, brIndex;
    final boolean ms;
    final int[] sfb;
    final double[] C = new double[512];
    final double[][] M = new double[32][64];
    final double[][] mdctCos = new double[18][36];
    final double[] cs = new double[8], ca = new double[8];
    // Per channel: the filter bank's input history, and the last granule's subband samples.
    final double[][] X;
    final int[] xAt;
    final double[][][] prev;

    Encoder(int rate, int channels, int brIndex, boolean ms) {
      this.rate = rate;
      this.channels = channels;
      this.brIndex = brIndex;
      this.ms = ms && channels == 2;
      this.sfb = rate == 48000 ? SFB_48 : rate == 32000 ? SFB_32 : SFB_44;
      String[] w = WINDOW.split(" ");
      for (int i = 0; i < 512; i++) C[i] = Double.parseDouble(w[i]) / 32.0;
      for (int k = 0; k < 32; k++) for (int i = 0; i < 64; i++) M[k][i] = Math.cos((2 * k + 1) * (i - 16) * Math.PI / 64);
      for (int m = 0; m < 18; m++) {
        for (int k = 0; k < 36; k++) mdctCos[m][k] = Math.sin(Math.PI / 36 * (k + 0.5)) * Math.cos(Math.PI / 72 * (2 * k + 19) * (2 * m + 1));
      }
      double[] ci = {-0.6, -0.535, -0.33, -0.185, -0.095, -0.041, -0.0142, -0.0037};
      for (int i = 0; i < 8; i++) {
        double sq = Math.sqrt(1 + ci[i] * ci[i]);
        cs[i] = 1 / sq;
        ca[i] = ci[i] / sq;
      }
      X = new double[channels][1024];
      xAt = new int[channels];
      prev = new double[channels][32][18];
      loadTables();
    }

    /** Encodes all of `src` after an Info frame (written at the start, filled in at the end); the audio frames written. */
    long encode(Source src, Stream out) throws Exception {
      int bitrate = BITRATES[brIndex] * 1000;
      long num = 144L * bitrate, rem = 0;
      int sideBytes = channels == 1 ? 17 : 32;
      float[] l = new float[1152], r = new float[1152];
      double[][][] xr = new double[2][channels][576];
      int[][][] ix = new int[2][channels][576];
      Granule[][] g = new Granule[2][channels];
      for (int gr = 0; gr < 2; gr++) for (int ch = 0; ch < channels; ch++) g[gr][ch] = new Granule();
      // Above the low-pass (by bitrate per channel) nothing is coded: the bits go to what is heard.
      int cutBin = Math.min(576, (int) Math.ceil(lowpass(BITRATES[brIndex] / channels) / (rate / 2.0) * 576));
      // The filter bank and MDCT delay the audio by 528 samples (the decoder adds 529): the
      // encoder goes on past the end with silence until all of it is out.
      long tail = 1152 * 2;
      long frames = 0;
      Bits side = new Bits(), main = new Bits();
      out.writeInfoPlaceholder(this, sideBytes);
      long slotStart = 0;
      // Quality aimed at (dB of signal to quantizing noise): raised while the reservoir fills, lowered while it empties.
      double target = 20 + BITRATES[brIndex] / channels / 4.0;
      while (true) {
        int n = src.read(l, r, 1152);
        if (n < 1152) {
          for (int i = n; i < 1152; i++) {
            l[i] = 0;
            r[i] = 0;
          }
          if (n == 0) {
            if (tail <= 0) break;
            tail -= 1152;
          } else {
            tail -= 1152 - n;
          }
        }
        out.samples += n;
        int whole = (int) (num / rate);
        rem += num % rate;
        int padding = 0;
        if (rem >= rate) {
          rem -= rate;
          padding = 1;
        }
        int frameBytes = whole + padding;
        int slotBytes = frameBytes - 4 - sideBytes;
        for (int gr = 0; gr < 2; gr++) {
          for (int ch = 0; ch < channels; ch++) {
            analyse(ch, ch == 0 ? l : r, gr * 576, xr[gr][ch]);
            for (int i = cutBin; i < 576; i++) xr[gr][ch][i] = 0;
          }
        }
        // Joint: mid/side for this frame when the channels are alike (one of mid and side is small).
        msFrame = false;
        if (ms) {
          double em = 0, es = 0;
          for (int gr = 0; gr < 2; gr++) {
            for (int i = 0; i < 576; i++) {
              double m = xr[gr][0][i] + xr[gr][1][i], s = xr[gr][0][i] - xr[gr][1][i];
              em += m * m;
              es += s * s;
            }
          }
          msFrame = es < MS_RATIO * em || em < MS_RATIO * es;
        }
        if (msFrame) {
          for (int gr = 0; gr < 2; gr++) {
            for (int i = 0; i < 576; i++) {
              double m = (xr[gr][0][i] + xr[gr][1][i]) * SQRT_HALF;
              double s = (xr[gr][0][i] - xr[gr][1][i]) * SQRT_HALF;
              xr[gr][0][i] = m;
              xr[gr][1][i] = s;
            }
          }
        }
        // The bit reservoir: this frame's main data may start up to 511 bytes back, in what the
        // frames before left unused.
        int resv = (int) Math.min(511, slotStart - out.mainEnd);
        if (slotStart - out.mainEnd > 511) out.mainEnd = slotStart - 511;
        int budget = (resv + slotBytes) * 8;
        int share = slotBytes * 8 / (2 * channels);
        int units = 2 * channels;
        for (int gr = 0; gr < 2; gr++) {
          for (int ch = 0; ch < channels; ch++) {
            // As much as is left, keeping half a share for each granule still to come.
            int avail = Math.min(4095, budget - (units - 1) * (share / 2));
            quantize(xr[gr][ch], ix[gr][ch], g[gr][ch], Math.max(0, avail), target);
            budget -= g[gr][ch].part23;
            units--;
          }
        }
        main.reset(slotBytes + 512);
        for (int gr = 0; gr < 2; gr++) for (int ch = 0; ch < channels; ch++) huffman(main, ix[gr][ch], g[gr][ch]);
        side.reset(4 + sideBytes);
        header(side, padding);
        sideInfo(side, g, resv);
        out.frame(side.buf, 4 + sideBytes, slotStart, slotBytes, main.buf, (main.bit + 7) >>> 3);
        slotStart += slotBytes;
        frames++;
        long left = slotStart - out.mainEnd;
        if (left > 511 * 0.6) target = Math.min(90, target + 0.5);
        else if (left < 511 * 0.25) target = Math.max(0, target - 0.5);
      }
      out.finish(slotStart);
      out.frames = frames;
      return frames;
    }

    /** The low-pass for a bitrate per channel (kbps), in Hz. */
    static double lowpass(int perChannel) {
      if (perChannel >= 160) return 20000;
      if (perChannel >= 128) return 19500;
      if (perChannel >= 112) return 19000;
      if (perChannel >= 96) return 18500;
      if (perChannel >= 80) return 17500;
      if (perChannel >= 64) return 16500;
      if (perChannel >= 56) return 15500;
      if (perChannel >= 48) return 14500;
      if (perChannel >= 40) return 12500;
      if (perChannel >= 32) return 11000;
      return 8000;
    }

    static final double SQRT_HALF = Math.sqrt(0.5);
    static final double MS_RATIO = 0.3;
    /** Mid/side in the frame being written (joint stereo). */
    boolean msFrame;

    /** One granule (576 samples from `at`) of a channel: filter bank, MDCT and alias reduction into xr. */
    void analyse(int ch, float[] pcm, int at, double[] xr) {
      double[][] sb = new double[18][32];
      double[] x = X[ch];
      double[] y = new double[64];
      for (int t = 0; t < 18; t++) {
        // 32 new samples: the newest at the lowest index (ISO 11172-3 analysis filter).
        int p = xAt[ch];
        for (int i = 31; i >= 0; i--) {
          p = (p - 1) & 511;
          x[p] = pcm[at + t * 32 + (31 - i)];
          x[p + 512] = x[p];
        }
        // After the loop x[p + i] is X[i] (X[0] the newest).
        xAt[ch] = p;
        for (int i = 0; i < 64; i++) {
          double s = 0;
          for (int j = 0; j < 8; j++) s += C[i + 64 * j] * x[p + i + 64 * j];
          y[i] = s;
        }
        for (int k = 0; k < 32; k++) {
          double s = 0;
          double[] mk = M[k];
          for (int i = 0; i < 64; i++) s += mk[i] * y[i];
          sb[t][k] = s;
        }
      }
      double[][] last = prev[ch];
      double[] in = new double[36];
      for (int band = 0; band < 32; band++) {
        // The decoder inverts every other sample of the odd bands: done here first.
        if ((band & 1) == 1) for (int t = 1; t < 18; t += 2) sb[t][band] = -sb[t][band];
        for (int t = 0; t < 18; t++) {
          in[t] = last[band][t];
          in[t + 18] = sb[t][band];
          last[band][t] = sb[t][band];
        }
        for (int m = 0; m < 18; m++) {
          double s = 0;
          double[] c = mdctCos[m];
          for (int k = 0; k < 36; k++) s += c[k] * in[k];
          xr[band * 18 + m] = s * MDCT_GAIN;
        }
      }
      // Alias reduction (the decoder's butterflies undone).
      for (int band = 1; band < 32; band++) {
        for (int i = 0; i < 8; i++) {
          int lo = band * 18 - 1 - i, hi = band * 18 + i;
          double bu = xr[lo], bd = xr[hi];
          xr[lo] = bu * cs[i] + bd * ca[i];
          xr[hi] = bd * cs[i] - bu * ca[i];
        }
      }
    }

    // ---------------------------------------------------------------- quantizing

    static final class Granule {
      int part23, bigValues, globalGain, count1, count1Table;
      int region0, region1;
      final int[] table = new int[3];
      int[] address = new int[3];
    }

    final double[] xr34 = new double[576];

    /**
     * The quantization of xr into ix (its side info in g): the finest that fits in `avail` bits,
     * but no finer than needed for `target` dB of signal to quantizing noise (the bits saved go
     * to the reservoir).
     */
    void quantize(double[] xr, int[] ix, Granule g, int avail, double target) {
      double peak = 0;
      for (int i = 0; i < 576; i++) {
        double a = Math.abs(xr[i]);
        xr34[i] = Math.pow(a, 0.75);
        if (a > peak) peak = a;
      }
      if (peak < 1e-12) {
        for (int i = 0; i < 576; i++) ix[i] = 0;
        g.globalGain = 0;
        layout(ix, g);
        return;
      }
      // Lowest gain that fits: binary search (more gain, coarser steps, fewer bits).
      int lo = 0, hi = 255, best = 255;
      while (lo <= hi) {
        int mid = (lo + hi) >>> 1;
        int used = quantizeAt(xr, ix, mid, g);
        if (used >= 0 && used <= avail) {
          best = mid;
          hi = mid - 1;
        } else {
          lo = mid + 1;
        }
      }
      // Coarser while the noise stays below the target (the highest such gain, at least `best`).
      double energy = 0;
      for (int i = 0; i < 576; i++) energy += xr[i] * xr[i];
      double allowed = energy * Math.pow(10, -target / 10);
      lo = best;
      hi = 255;
      int coarse = best;
      while (lo <= hi) {
        int mid = (lo + hi) >>> 1;
        if (noise(xr, mid) <= allowed) {
          coarse = mid;
          lo = mid + 1;
        } else {
          hi = mid - 1;
        }
      }
      best = coarse;
      if (quantizeAt(xr, ix, best, g) < 0 || g.part23 > avail) {
        // Even the coarsest steps do not fit (not at MP3 bitrates): silence for this granule.
        for (int i = 0; i < 576; i++) ix[i] = 0;
        g.globalGain = 0;
        layout(ix, g);
      }
    }

    /** The quantizing noise energy at global gain `gain` (what the decoder's values miss of xr). */
    double noise(double[] xr, int gain) {
      double f = Math.pow(2.0, -0.1875 * (gain - 210));
      double step = Math.pow(2.0, 0.25 * (gain - 210));
      double sum = 0;
      for (int i = 0; i < 576; i++) {
        int q = (int) (xr34[i] * f + 0.4054);
        double back = POW43[Math.min(q, 8206)] * step;
        double d = Math.abs(xr[i]) - back;
        sum += d * d;
      }
      return sum;
    }

    static final double[] POW43 = new double[8207];
    static {
      for (int i = 0; i < POW43.length; i++) POW43[i] = Math.pow(i, 4.0 / 3.0);
    }

    /** Quantizes with global gain `gain`; the bits it takes, or -1 when a value is too large to code. */
    int quantizeAt(double[] xr, int[] ix, int gain, Granule g) {
      double f = Math.pow(2.0, -0.1875 * (gain - 210));
      for (int i = 0; i < 576; i++) {
        int q = (int) (xr34[i] * f + 0.4054);
        if (q > 8206) return -1;
        ix[i] = xr[i] < 0 ? -q : q;
      }
      g.globalGain = gain;
      return layout(ix, g);
    }

    static final int[][] SUBDV = {{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 1}, {1, 1}, {1, 1}, {1, 2}, {2, 2}, {2, 3}, {2, 3}, {3, 4}, {3, 4}, {3, 4}, {4, 5}, {4, 5}, {4, 6}, {5, 6}, {5, 6}, {5, 7}, {6, 7}, {6, 7}};

    /** The regions, tables and count1 table for ix; the bits they take (part2_3_length). */
    int layout(int[] ix, Granule g) {
      int i = 576;
      while (i > 1 && ix[i - 1] == 0 && ix[i - 2] == 0) i -= 2;
      int count1 = 0;
      while (i > 3 && Math.abs(ix[i - 1]) <= 1 && Math.abs(ix[i - 2]) <= 1 && Math.abs(ix[i - 3]) <= 1 && Math.abs(ix[i - 4]) <= 1) {
        count1++;
        i -= 4;
      }
      g.bigValues = i / 2;
      g.count1 = count1;
      int big = i;
      if (big == 0) {
        g.region0 = 0;
        g.region1 = 0;
        g.address[0] = 0;
        g.address[1] = 0;
        g.address[2] = 0;
      } else {
        int n = 0;
        while (sfb[n] < big) n++;
        int c = SUBDV[n][0];
        while (c > 0 && sfb[c + 1] > big) c--;
        g.region0 = c;
        g.address[0] = sfb[c + 1];
        int index = c + 1;
        c = SUBDV[n][1];
        while (c > 0 && sfb[c + index + 1] > big) c--;
        g.region1 = c;
        g.address[1] = sfb[index + c + 1];
        g.address[2] = big;
        // The regions' ends never pass the big values' end.
        if (g.address[0] > big) g.address[0] = big;
        if (g.address[1] > big) g.address[1] = big;
      }
      int total = 0;
      int start = 0;
      for (int r = 0; r < 3; r++) {
        int end = g.address[r];
        int t = 0, best = 0;
        if (end > start) {
          int max = 0;
          for (int k = start; k < end; k++) max = Math.max(max, Math.abs(ix[k]));
          int[] pick = choose(ix, start, end, max);
          t = pick[0];
          best = pick[1];
        }
        g.table[r] = t;
        total += best;
        start = Math.max(start, end);
      }
      // count1: table A or B, the smaller.
      int a = 0, b = 0;
      for (int q = 0, k = big; q < count1; q++, k += 4) {
        int v = Math.abs(ix[k]), w = Math.abs(ix[k + 1]), x = Math.abs(ix[k + 2]), y = Math.abs(ix[k + 3]);
        int signs = v + w + x + y;
        a += LEN[32][v * 8 + w * 4 + x * 2 + y] + signs;
        b += 4 + signs;
      }
      g.count1Table = b < a ? 1 : 0;
      total += Math.min(a, b);
      g.part23 = total;
      return total;
    }

    static final int[] SMALL = {1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15};

    /** The table that codes ix[start, end) in the fewest bits, and that number of bits. */
    int[] choose(int[] ix, int start, int end, int max) {
      if (max == 0) return new int[] {0, 0};
      int bestT = -1, bestBits = Integer.MAX_VALUE;
      if (max <= 15) {
        for (int t : SMALL) {
          if (XLEN[t] <= max) continue;
          int b = count(ix, start, end, t);
          if (b < bestBits) {
            bestBits = b;
            bestT = t;
          }
        }
        return new int[] {bestT, bestBits};
      }
      int need = max - 15;
      for (int base = 16; base <= 24; base += 8) {
        for (int t = base; t < base + 8; t++) {
          if ((1 << LINBITS[t]) - 1 >= need) {
            int b = count(ix, start, end, t);
            if (b < bestBits) {
              bestBits = b;
              bestT = t;
            }
            break;
          }
        }
      }
      return new int[] {bestT, bestBits};
    }

    /** The bits table t takes for the pairs of ix[start, end). */
    int count(int[] ix, int start, int end, int t) {
      int[] len = LEN[t < 16 ? t : t < 24 ? 16 : 24];
      int ylen = t < 16 ? XLEN[t] : 16;
      int lin = LINBITS[t];
      int sum = 0;
      for (int k = start; k < end; k += 2) {
        int x = Math.abs(ix[k]), y = Math.abs(ix[k + 1]);
        if (lin > 0) {
          if (x >= 15) {
            sum += lin;
            x = 15;
          }
          if (y >= 15) {
            sum += lin;
            y = 15;
          }
        }
        sum += len[x * ylen + y];
        if (x != 0) sum++;
        if (y != 0) sum++;
      }
      return sum;
    }

    // ---------------------------------------------------------------- bitstream

    static final class Bits {
      byte[] buf = new byte[2048];
      int bit;

      void reset(int bytes) {
        if (buf.length < bytes) buf = new byte[bytes];
        java.util.Arrays.fill(buf, 0, bytes, (byte) 0);
        bit = 0;
      }

      void put(int value, int n) {
        for (int i = n - 1; i >= 0; i--) {
          if (((value >>> i) & 1) != 0) buf[bit >>> 3] |= (byte) (0x80 >>> (bit & 7));
          bit++;
        }
      }
    }

    void header(Bits b, int padding) {
      header(b, padding, brIndex);
    }

    void header(Bits b, int padding, int bitrateIndex) {
      b.put(0xFFF, 12);
      b.put(1, 1); // MPEG-1
      b.put(1, 2); // layer III
      b.put(1, 1); // no CRC
      b.put(bitrateIndex, 4);
      b.put(rate == 44100 ? 0 : rate == 48000 ? 1 : 2, 2);
      b.put(padding, 1);
      b.put(0, 1);
      b.put(channels == 1 ? 3 : ms ? 1 : 0, 2);
      b.put(msFrame ? 2 : 0, 2); // joint: mid/side on (this frame), intensity off
      b.put(0, 1); // copyright
      b.put(1, 1); // original
      b.put(0, 2); // emphasis
    }

    void sideInfo(Bits b, Granule[][] g, int mainDataBegin) {
      b.put(mainDataBegin, 9); // where this frame's main data starts: that many bytes back
      b.put(0, channels == 1 ? 5 : 3);
      for (int ch = 0; ch < channels; ch++) b.put(0, 4); // scfsi
      for (int gr = 0; gr < 2; gr++) {
        for (int ch = 0; ch < channels; ch++) {
          Granule x = g[gr][ch];
          b.put(x.part23, 12);
          b.put(x.bigValues, 9);
          b.put(x.globalGain, 8);
          b.put(0, 4); // scalefac_compress: no scalefactors
          b.put(0, 1); // window_switching_flag: long blocks
          for (int r = 0; r < 3; r++) b.put(x.table[r], 5);
          b.put(x.region0, 4);
          b.put(x.region1, 3);
          b.put(0, 1); // preflag
          b.put(0, 1); // scalefac_scale
          b.put(x.count1Table, 1);
        }
      }
    }

    void huffman(Bits b, int[] ix, Granule g) {
      int start = 0;
      for (int r = 0; r < 3; r++) {
        int end = g.address[r];
        int t = g.table[r];
        if (t != 0) {
          int base = t < 16 ? t : t < 24 ? 16 : 24;
          int[] len = LEN[base], code = CODE[base];
          int ylen = t < 16 ? XLEN[t] : 16;
          int lin = LINBITS[t];
          for (int k = start; k < end; k += 2) {
            int x = ix[k], y = ix[k + 1];
            int ax = Math.abs(x), ay = Math.abs(y);
            int cx = lin > 0 && ax >= 15 ? 15 : ax, cy = lin > 0 && ay >= 15 ? 15 : ay;
            b.put(code[cx * ylen + cy], len[cx * ylen + cy]);
            if (lin > 0 && ax >= 15) b.put(ax - 15, lin);
            if (ax != 0) b.put(x < 0 ? 1 : 0, 1);
            if (lin > 0 && ay >= 15) b.put(ay - 15, lin);
            if (ay != 0) b.put(y < 0 ? 1 : 0, 1);
          }
        }
        start = Math.max(start, end);
      }
      int k = g.bigValues * 2;
      int tab = g.count1Table == 0 ? 32 : 33;
      for (int q = 0; q < g.count1; q++, k += 4) {
        int v = Math.abs(ix[k]), w = Math.abs(ix[k + 1]), x = Math.abs(ix[k + 2]), y = Math.abs(ix[k + 3]);
        int idx = v * 8 + w * 4 + x * 2 + y;
        b.put(CODE[tab][idx], LEN[tab][idx]);
        if (v != 0) b.put(ix[k] < 0 ? 1 : 0, 1);
        if (w != 0) b.put(ix[k + 1] < 0 ? 1 : 0, 1);
        if (x != 0) b.put(ix[k + 2] < 0 ? 1 : 0, 1);
        if (y != 0) b.put(ix[k + 3] < 0 ? 1 : 0, 1);
      }
    }
  }

  /**
   * The frames as they go out: each frame's header and side info, then its slot of main data,
   * which holds the end of the frames before (the bit reservoir) and its own. A frame goes to the
   * file once nothing later can still write into its slot. The first frame is an Info frame
   * (LAME's gapless tag: the encoder delay and padding, so players and ffmpeg trim the silence
   * the filter bank adds); its numbers are filled in when the audio is written (info()).
   */
  static final class Stream {
    final OutputStream os;
    long samples, frames, mainEnd, audioBytes;
    int infoLen, crc;
    byte[] infoHead;
    byte[] slots = new byte[1 << 15];
    long base;
    final List<byte[]> heads = new ArrayList<byte[]>();
    final List<long[]> places = new ArrayList<long[]>();
    Encoder enc;
    int sideBytes;

    Stream(OutputStream os) {
      this.os = os;
    }

    /** The Info frame's place: a frame of silence at the bitrate (no padding). */
    void writeInfoPlaceholder(Encoder e, int side) throws Exception {
      enc = e;
      sideBytes = side;
      // At a low bitrate the frame is too small for the tag: the Info frame has a bitrate it fits in (as LAME does).
      int index = e.brIndex;
      while (index < BITRATES.length - 1 && 144L * BITRATES[index] * 1000 / e.rate < 4 + side + 156) index++;
      infoLen = (int) (144L * BITRATES[index] * 1000 / e.rate);
      Encoder.Bits b = new Encoder.Bits();
      b.reset(4 + side);
      boolean ms = e.msFrame;
      e.msFrame = false;
      e.header(b, 0, index);
      e.msFrame = ms;
      infoHead = java.util.Arrays.copyOf(b.buf, 4 + side);
      os.write(new byte[infoLen]);
    }

    void frame(byte[] head, int headLen, long slotStart, int slotLen, byte[] main, int mainLen) throws Exception {
      long end = slotStart + slotLen;
      if (end - base > slots.length) slots = java.util.Arrays.copyOf(slots, (int) Math.max(slots.length * 2, end - base + 4096));
      System.arraycopy(main, 0, slots, (int) (mainEnd - base), mainLen);
      mainEnd += mainLen;
      heads.add(java.util.Arrays.copyOf(head, headLen));
      places.add(new long[] {slotStart, slotLen});
      // Final: what is written already, and what the next frame cannot reach (more than 511 bytes back).
      long done = Math.max(mainEnd, end - 511);
      while (!places.isEmpty() && places.get(0)[0] + places.get(0)[1] <= done) flushOne();
    }

    void finish(long slotEnd) throws Exception {
      while (!places.isEmpty()) flushOne();
    }

    void flushOne() throws Exception {
      byte[] head = heads.remove(0);
      long[] at = places.remove(0);
      os.write(head);
      crc = crc16(crc, head, 0, head.length);
      int from = (int) (at[0] - base);
      os.write(slots, from, (int) at[1]);
      crc = crc16(crc, slots, from, (int) at[1]);
      audioBytes += head.length + at[1];
      // The slot is out: what follows moves to the start of the buffer.
      long next = at[0] + at[1];
      int shift = (int) (next - base);
      System.arraycopy(slots, shift, slots, 0, slots.length - shift);
      java.util.Arrays.fill(slots, slots.length - shift, slots.length, (byte) 0);
      base = next;
    }

    /** The Info frame with its numbers: frames, bytes, the encoder delay and padding, the CRCs. */
    byte[] info() {
      byte[] f = new byte[infoLen];
      System.arraycopy(infoHead, 0, f, 0, infoHead.length);
      int at = 4 + sideBytes;
      put(f, at, "Info");
      be32(f, at + 4, 0x0F); // frames, bytes, TOC and quality fields
      be32(f, at + 8, (int) frames);
      be32(f, at + 12, (int) (audioBytes + infoLen));
      for (int i = 0; i < 100; i++) f[at + 16 + i] = (byte) (i * 256 / 100);
      be32(f, at + 116, 0);
      int lame = at + 120;
      put(f, lame, "LAME3.100");
      f[lame + 9] = 0x01; // tag revision 0, CBR
      f[lame + 10] = (byte) Math.min(255, Encoder.lowpass(BITRATES[enc.brIndex] / enc.channels) / 100);
      f[lame + 20] = (byte) Math.min(255, BITRATES[enc.brIndex]);
      long padding = Math.max(0, Math.min(4095, frames * 1152 - ENCODER_DELAY - samples));
      f[lame + 21] = (byte) (ENCODER_DELAY >> 4);
      f[lame + 22] = (byte) ((ENCODER_DELAY & 15) << 4 | (int) (padding >> 8));
      f[lame + 23] = (byte) padding;
      be32(f, lame + 28, (int) (audioBytes + infoLen));
      f[lame + 32] = (byte) (crc >> 8);
      f[lame + 33] = (byte) crc;
      int tagCrc = crc16(0, f, 0, lame + 34);
      f[lame + 34] = (byte) (tagCrc >> 8);
      f[lame + 35] = (byte) tagCrc;
      return f;
    }

    static void put(byte[] f, int at, String s) {
      for (int i = 0; i < s.length(); i++) f[at + i] = (byte) s.charAt(i);
    }

    static void be32(byte[] f, int at, int v) {
      f[at] = (byte) (v >>> 24);
      f[at + 1] = (byte) (v >>> 16);
      f[at + 2] = (byte) (v >>> 8);
      f[at + 3] = (byte) v;
    }
  }

  /** The samples the encoder delays the audio by (the decoder adds 529 more): in the Info frame. */
  static final int ENCODER_DELAY = 528;

  /** CRC-16 as LAME's tag uses it (polynomial 0x8005, reflected). */
  static int crc16(int crc, byte[] b, int from, int n) {
    for (int i = from; i < from + n; i++) {
      crc ^= b[i] & 0xff;
      for (int k = 0; k < 8; k++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
    }
    return crc & 0xffff;
  }

  /** The MDCT's scale, so the decoder gives the input's level back. */
  static final double MDCT_GAIN = 1.0 / 9;

  static final int[] SFB_44 = {0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 52, 62, 74, 90, 110, 134, 162, 196, 238, 288, 342, 418, 576};
  static final int[] SFB_48 = {0, 4, 8, 12, 16, 20, 24, 30, 36, 42, 50, 60, 72, 88, 106, 128, 156, 190, 230, 276, 330, 384, 576};
  static final int[] SFB_32 = {0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 54, 66, 82, 102, 126, 156, 194, 240, 296, 364, 448, 550, 576};

  /** Each table's size (values 0..XLEN-1) and its linbits; 16-23 use table 16's codes, 24-31 table 24's. */
  static final int[] XLEN = {0, 2, 3, 3, 0, 4, 4, 6, 6, 6, 8, 8, 8, 16, 0, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16};
  static final int[] LINBITS = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 6, 8, 10, 13, 4, 5, 6, 7, 8, 9, 11, 13};
  static final int[][] LEN = new int[34][], CODE = new int[34][];

  static synchronized void loadTables() {
    if (LEN[1] != null) return;
    for (int t = 0; t < 34; t++) {
      String s = HUFF[t];
      if (s == null) continue;
      List<int[]> entries = new ArrayList<int[]>();
      int i = 0;
      while (i < s.length()) {
        int len = s.charAt(i++) - 'a';
        int digits = (len + 3) / 4;
        int code = Integer.parseInt(s.substring(i, i + digits), 16);
        i += digits;
        entries.add(new int[] {len, code});
      }
      LEN[t] = new int[entries.size()];
      CODE[t] = new int[entries.size()];
      for (int k = 0; k < entries.size(); k++) {
        LEN[t][k] = entries.get(k)[0];
        CODE[t][k] = entries.get(k)[1];
      }
    }
  }

  static final String WINDOW = "0.0 -1.5259e-05 -1.5259e-05 -1.5259e-05 -1.5259e-05 -1.5259e-05 -1.5259e-05 -3.0518e-05 -3.0518e-05 -3.0518e-05 -3.0518e-05 -4.5776e-05 -4.5776e-05 -6.1035e-05 -6.1035e-05 -7.6294e-05 -7.6294e-05 -9.1553e-05 -0.000106812 -0.000106812 -0.00012207 -0.000137329 -0.000152588 -0.000167847 -0.000198364 -0.000213623 -0.000244141 -0.000259399 -0.000289917 -0.000320435 -0.000366211 -0.000396729 -0.000442505 -0.000473022 -0.000534058 -0.000579834 -0.00062561 -0.000686646 -0.000747681 -0.000808716 -0.00088501 -0.000961304 -0.001037598 -0.001113892 -0.001205444 -0.001296997 -0.00138855 -0.001480103 -0.001586914 -0.001693726 -0.001785278 -0.001907349 -0.00201416 -0.002120972 -0.002243042 -0.002349854 -0.002456665 -0.002578735 -0.002685547 -0.002792358 -0.00289917 -0.002990723 -0.003082275 -0.003173828 0.003250122 0.003326416 0.003387451 0.003433228 0.003463745 0.003479004 0.003479004 0.003463745 0.003417969 0.003372192 0.00328064 0.003173828 0.003051758 0.002883911 0.002700806 0.002487183 0.002227783 0.001937866 0.001617432 0.001266479 0.000869751 0.000442505 -3.0518e-05 -0.000549316 -0.001098633 -0.001693726 -0.002334595 -0.003005981 -0.003723145 -0.004486084 -0.0052948 -0.006118774 -0.007003784 -0.007919312 -0.008865356 -0.009841919 -0.010848999 -0.011886597 -0.012939453 -0.014022827 -0.01512146 -0.016235352 -0.017349243 -0.018463135 -0.019577026 -0.020690918 -0.02178955 -0.022857666 -0.023910522 -0.024932861 -0.025909424 -0.02684021 -0.02772522 -0.028533936 -0.029281616 -0.029937744 -0.030532837 -0.03100586 -0.03138733 -0.031661987 -0.031814575 -0.031845093 -0.03173828 -0.03147888 0.031082153 0.030517578 0.029785156 0.028884888 0.027801514 0.026535034 0.02508545 0.023422241 0.021575928 0.01953125 0.01725769 0.014801025 0.012115479 0.009231567 0.006134033 0.002822876 -0.000686646 -0.004394531 -0.00831604 -0.012420654 -0.016708374 -0.0211792 -0.025817871 -0.03060913 -0.03555298 -0.040634155 -0.045837402 -0.051132202 -0.056533813 -0.06199646 -0.06752014 -0.07305908 -0.07862854 -0.08418274 -0.08970642 -0.09516907 -0.10054016 -0.1058197 -0.110946655 -0.11592102 -0.12069702 -0.1252594 -0.12956238 -0.1335907 -0.13729858 -0.14067078 -0.14367676 -0.1462555 -0.14842224 -0.15011597 -0.15130615 -0.15196228 -0.15206909 -0.15159607 -0.15049744 -0.1487732 -0.1463623 -0.14326477 -0.13945007 -0.1348877 -0.12957764 -0.12347412 -0.11657715 -0.1088562 0.10031128 0.090927124 0.08068848 0.06959534 0.057617188 0.044784546 0.031082153 0.01651001 0.001068115 -0.015228271 -0.03237915 -0.050354004 -0.06916809 -0.088775635 -0.10916138 -0.13031006 -0.15220642 -0.17478943 -0.19805908 -0.22198486 -0.24650574 -0.2715912 -0.2972107 -0.32331848 -0.34986877 -0.37680054 -0.40408325 -0.43165588 -0.45947266 -0.48747253 -0.51560974 -0.54382324 -0.57203674 -0.6002197 -0.6282959 -0.6562195 -0.6839142 -0.71131897 -0.7383728 -0.7650299 -0.791214 -0.816864 -0.84194946 -0.8663635 -0.89009094 -0.9130554 -0.9351959 -0.95648193 -0.9768524 -0.99624634 -1.0146179 -1.0319366 -1.0481567 -1.0632172 -1.0771179 -1.0897827 -1.1012115 -1.1113739 -1.120224 -1.1277466 -1.1339264 -1.1387634 -1.1422119 -1.1442871 1.144989 1.1442871 1.1422119 1.1387634 1.1339264 1.1277466 1.120224 1.1113739 1.1012115 1.0897827 1.0771179 1.0632172 1.0481567 1.0319366 1.0146179 0.99624634 0.9768524 0.95648193 0.9351959 0.9130554 0.89009094 0.8663635 0.84194946 0.816864 0.791214 0.7650299 0.7383728 0.71131897 0.6839142 0.6562195 0.6282959 0.6002197 0.57203674 0.54382324 0.51560974 0.48747253 0.45947266 0.43165588 0.40408325 0.37680054 0.34986877 0.32331848 0.2972107 0.2715912 0.24650574 0.22198486 0.19805908 0.17478943 0.15220642 0.13031006 0.10916138 0.088775635 0.06916809 0.050354004 0.03237915 0.015228271 -0.001068115 -0.01651001 -0.031082153 -0.044784546 -0.057617188 -0.06959534 -0.08068848 -0.090927124 0.10031128 0.1088562 0.11657715 0.12347412 0.12957764 0.1348877 0.13945007 0.14326477 0.1463623 0.1487732 0.15049744 0.15159607 0.15206909 0.15196228 0.15130615 0.15011597 0.14842224 0.1462555 0.14367676 0.14067078 0.13729858 0.1335907 0.12956238 0.1252594 0.12069702 0.11592102 0.110946655 0.1058197 0.10054016 0.09516907 0.08970642 0.08418274 0.07862854 0.07305908 0.06752014 0.06199646 0.056533813 0.051132202 0.045837402 0.040634155 0.03555298 0.03060913 0.025817871 0.0211792 0.016708374 0.012420654 0.00831604 0.004394531 0.000686646 -0.002822876 -0.006134033 -0.009231567 -0.012115479 -0.014801025 -0.01725769 -0.01953125 -0.021575928 -0.023422241 -0.02508545 -0.026535034 -0.027801514 -0.028884888 -0.029785156 -0.030517578 0.031082153 0.03147888 0.03173828 0.031845093 0.031814575 0.031661987 0.03138733 0.03100586 0.030532837 0.029937744 0.029281616 0.028533936 0.02772522 0.02684021 0.025909424 0.024932861 0.023910522 0.022857666 0.02178955 0.020690918 0.019577026 0.018463135 0.017349243 0.016235352 0.01512146 0.014022827 0.012939453 0.011886597 0.010848999 0.009841919 0.008865356 0.007919312 0.007003784 0.006118774 0.0052948 0.004486084 0.003723145 0.003005981 0.002334595 0.001693726 0.001098633 0.000549316 3.0518e-05 -0.000442505 -0.000869751 -0.001266479 -0.001617432 -0.001937866 -0.002227783 -0.002487183 -0.002700806 -0.002883911 -0.003051758 -0.003173828 -0.00328064 -0.003372192 -0.003417969 -0.003463745 -0.003479004 -0.003479004 -0.003463745 -0.003433228 -0.003387451 -0.003326416 0.003250122 0.003173828 0.003082275 0.002990723 0.00289917 0.002792358 0.002685547 0.002578735 0.002456665 0.002349854 0.002243042 0.002120972 0.00201416 0.001907349 0.001785278 0.001693726 0.001586914 0.001480103 0.00138855 0.001296997 0.001205444 0.001113892 0.001037598 0.000961304 0.00088501 0.000808716 0.000747681 0.000686646 0.00062561 0.000579834 0.000534058 0.000473022 0.000442505 0.000396729 0.000366211 0.000320435 0.000289917 0.000259399 0.000244141 0.000213623 0.000198364 0.000167847 0.000152588 0.000137329 0.00012207 0.000106812 0.000106812 9.1553e-05 7.6294e-05 7.6294e-05 6.1035e-05 6.1035e-05 4.5776e-05 4.5776e-05 3.0518e-05 3.0518e-05 3.0518e-05 3.0518e-05 1.5259e-05 1.5259e-05 1.5259e-05 1.5259e-05 1.5259e-05 1.5259e-05";
  /** Huffman codes (ISO 11172-3 tables, as JLayer decodes them): per entry a letter (a + length) and the code in hex. */
  static final String[] HUFF = new String[34];
  static {
    HUFF[1] = "b1d1c1d0";
    HUFF[2] = "b1d2g01d3d1f01f03f02g00";
    HUFF[3] = "c3c2g01d1c1f01f03f02g00";
    HUFF[5] = "b1d2g06h05d3d1g04h04g07g05h07i01h06g01h01i00";
    HUFF[6] = "d7d3f05h01d6c2e3f02e5e4f04g01g03f03g02h00";
    HUFF[7] = "b1d2g0ai13i10j00ad3e3g07h0ah05i03g0bf04h0di11i08j004h0ch0bi12j00fj00bj002h07h06i09j00ej003k001i06i04j005k003k002k000";
    HUFF[8] = "c3d4g06i12i0cj005d5c1e2i10i09i03g07e3g05i0ei07j003i13i11i0fj00dj00ak004i0dh05i08j00bk005k001j00ci04j004j001l001l000";
    HUFF[9] = "d7d5f09g0ei0fj007d6d4e5f05g06i07e7e6f08g08h08i05g0ff06g09h0ah05i01h0bg07h09h06i04j001i0eh04i06i02j006j000";
    HUFF[10] = "b1d2g0ai17j023j01ej00ck011d3e3g08h0ci12j015i0ci07g0bg09h0fi15j020k028j013j006h0eh0di16j022k02ek017j012k007i14i13j021k02fk01bk016k009k003j01fj016k029k01al015l014k005l003i0ei0dj00ak00bk010k006l005l001j009i08j007k008k004l004l002l000";
    HUFF[11] = "c3d4f0ah18i22j021i15j00fd5d3e4g0ai20i11h0bi0af0bf07g0dh12i1ej01fi14i05h19g0bh13j03bi1bk012i0cj005i23i21i1fj03aj01ek010j007k005i1ci1aj020k013k011l00fk008l00ei0eh0ch09i0dj00ek009k004k001i0bh04i06j006k006k003k002k000";
    HUFF[12] = "e9d6f10h21i29j027j026j01ad7d5e6f09h17h10i1ai0bf11e7f0bg0eh15i1eh0ai07g11f0ag0fg0ch12i1ci0ei05h20g0dh16h13i12i10i09j005i28h11i1fi1di11j00di04j002i1bh0ch0bi0fi0aj007j004k001j01bi0ci08j00cj006j003j001k000";
    HUFF[13] = "b1e5g0eh15i22j033j02ek047j02ak034l044l034m043m02cn002bn0013d3e4g0ch13i1fi1aj02cj021j01fj018k020k018l01fm023m016m00eg0fg0dh17i24j03bj031k04dk041j01dk028k01el028l01bm021n002an0010h16h14i25j03dj038k04fk049k040k02bl04cl038l025l01am01fn0019n000ei23h10j03cj039k061k04bl072l05bk036l049l037m029m030n0035n0017o0018j03ai1bj032k060k04ck046l05dl054l04dl03am04fl01dn004an0031o0029o0011j02fj02dk04ek04al073l05el05al04fl045m053m047m032n003bn0026o0024o000fk048j022k038l05fl05cl055m05bm05am056m049n004dn0041n0033o002cq002bq002aj02bi14j01ek02ck037l04el048m057m04em03dm02en0036n0025o001ep0014p0010k035j019k029k025l02cl03bl036n0051m042n004cn0039o0036o0025o0012q0027p000bk023k021k01fl039l02am052m048n0050m02fn003ao0037n0015o0016p001aq0026r00016l035k019k017l026m046m03cm033m024n0037n001an0022o0017p001bp000ep0009q0007l022l020l01cm027m031n004bm01en0034o0030o0028p0034p001cp0012q0011q0009q0005m02dl015m022n0040n0038n0032o0031o002do001fo0013o000cp000fq000ap0007q0006q0003n0030m017m014n0027n0024n0023p0035o0015o0010r00017p000dp000ap0006r00001q0004q0002m010m00fn0011o001bo0019o0014p001do000bp0011p000cq0010q0008t00001s00001t00000q0001";
    HUFF[15] = "d7ecf12h35h2fi4cj07cj06cj059k07bk06cl077l06bl051m07an003fedd5f10g1bh2eh24i3di33i2aj046j034k053k041k029l03bl024f13f11f0fg18h29h22i3bi30i28j040j032k04ek03el050l038l021g1dg1cg19h2bh27i3fi37j05dj04cj03bk05dk048k036l04bl032l01dh34g16h2ah28i43i39j05fj04fj048j039k059k045k031l042l02el01bi4dh25h23i42i3ai34j05bj04aj03ej030k04fk03fl05al03el028m026j07dh20i3ci38i32j05cj04ej041j037k057k047k033l049l033m046m01ej06di35i31j05ej058j04bj042k07ak05bk049k038k02al040l02cl015m019j05ai2bi29j04dj049j03fj038k05ck04dk042k02fl043l030m035m024m014j047i22j043j03cj03aj031k058k04ck043l06al047l036l026m027m017m00fk06dj035j033j02fk05ak052k03ak039k030l048l039l029l017m01bn003em009k056j02aj028j025k046k040k034k02bl046l037l02al019m01dm012m00bn000bl076k044j01ek037k032k02el04al041l031l027l018l010m016m00dn000en0007l05bk02ck027k026k022l03fl034l02dl01fm034m01cm013m00em008n0009n0003m07bl03cl03al035l02fl02bl020l016m025m018m011m00cn000fn000am002n0001m047l025l022l01el01cl014l011m01am015m010m00am006n0008n0006n0002n0000";
    HUFF[16] = "b1e5g0ei2cj04aj03fk06ek05dl0acl095l08am0f2m0e1m0c3n0178j011d3e4g0ch14i23j03ej035j02fk053k04bk044l077m0c9l06bm0cfi09g0fg0dh17i26j043j03ak067k05al0a1k048l07fl075l06em0d1m0cej010i2dh15i27j045j040k072k063k057l09el08cm0fcm0d4m0c7n0183n016dk01aj04bi24j044j041k073k065l0b3l0a4l09bm108m0f6m0e2n018bn017en016aj009j042i1ej03bj038k066l0b9l0adm109l08em0fdm0e8n0190n0184n017ao01bdk010k06fj036j034k064l0b8l0b2l0a0l085m101m0f4m0e4m0d9n0181n016eo02cbk00ak062j030k05bk058l0a5l09dl094m105m0f8n0197n018dn0174n017cp0379p0374k008k055k054k051l09fl09cl08fm104m0f9n01abn0191n0188n017fo02d7o02c9o02c4k007l09ak04ck049l08dl083m100m0f5n01aan0196n018an0180o02dfn0167o02c6n0160l00bl08bl081k043l07dm0f7m0e9m0e5m0dbn0189o02e7o02e1o02d0p0375p0372o01b7k004m0f3l078l076l073m0e3m0dfn018co02eao02e6o02e0o02d1o02c8o02c2n00dfo01b4l006m0cam0e0m0dem0dam0d8n0185n0182n017dn016cp0378o01bbo02c3o01b8o01b5q06c0l004o02ebm0d3m0d2m0d0n0172n017bo02deo02d3o02caq06c7p0373p036dp036cr00d83p0361l002n0179n0171l066m0bbo02d6o02d2n0166o02c7o02c5p0362q06c6p0367r00d82p0366o01b2l000j00ci0ai07j00bj00ak011k00bk009l00dl00cl00al007l005l003l001i03";
    HUFF[24] = "efedg2eh50i92j106j0f8k1b2k1aal29dl28dl289l26dl205m408j058eeecf15g26h47i82i7aj0d8j0d1j0c6k147k159k13fk129k117i2ag2ff16g29h4ah44i80i78j0ddj0cfj0c2j0b6k154k13bk127l21dh12h51g27h4bh46i86i7di74j0dcj0ccj0bej0b2k145k137k125k10fh10i93h48h45i87i7fi76i70j0d2j0c8j0bck160k143k132k11dl21ch0ej107h42i81i7ei77i72j0d6j0caj0c0j0b4k155k13dk12dk119k106h0cj0f9i7bi79i75i71j0d7j0cej0c3j0b9k15bk14ak134k123k110l208h0ak1b3i73i6fi6dj0d3j0cbj0c4j0bbk161k14ck139k12ak11bl213l17di11k1abj0d4j0d0j0cdj0c9j0c1j0baj0b1j0a9k140k12fk11ek10cl202l179i10k14fj0c7j0c5j0bfj0bdj0b5j0aek14dk141k131k121k113l209l17bl173i0bl29cj0b8j0b7j0b3j0afk158k14bk13ak130k122k115l212l17fl175l16ei0al28ck15aj0abj0a8j0a4k13ek135k12bk11fk114k107l201l177l170l16ai06l288k142k13ck138k133k12ek124k11ck10dk105l200l178l172l16cl167i04l26ck12ck128k126k120k11ak111k10al203l17cl176l171l16dl169l165i02m409k118k116k112k10bk108k103l17el17al174l16fl16bl168l166l164i00i2bh14h13h11h0fh0dh0bh09h07h06h04i07i05i03i01e3";
    HUFF[32] = "b1e5e4f05e6g05f04g04e7f03f06g00f07g02g03g01";
    HUFF[33] = "efeeedecebeae9e8e7e6e5e4e3e2e1e0";
  }

  /** A playlist (.pls) given instead of a folder (or list): its files are the ones used. */
  static boolean isPls(File f) {
    return f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pls") && f.isFile();
  }

  /** The folder's files; for a playlist (.pls) its File= entries in order (a name without a folder is beside it). */
  static File[] listed(File dir) {
    if (!isPls(dir)) return dir.listFiles();
    java.util.List<File> out = new java.util.ArrayList<File>();
    try {
      File beside = dir.getAbsoluteFile().getParentFile();
      for (String line : new String(java.nio.file.Files.readAllBytes(dir.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        String t = line.replace("\r", "").trim();
        if (!t.toLowerCase(java.util.Locale.ROOT).matches("file\\d+=.+")) continue;
        String name = t.substring(t.indexOf('=') + 1).trim();
        File f = new File(name);
        if (!f.isAbsolute()) f = new File(beside, name);
        if (f.isFile()) out.add(f);
        else System.out.println("Not found: " + name);
      }
    } catch (java.io.IOException ex) {
      System.out.println("Could not read " + dir.getName() + ": " + ex.getMessage());
    }
    return out.toArray(new File[0]);
  }
}
