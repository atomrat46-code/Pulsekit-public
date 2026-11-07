package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AudioMixer: tracks added one by one (a WAV, an MP3, or a drum MIDI played with the kit), each with
 * its volume (0 to 150%), gain (-24 to +12 dB), mute and solo, mixed into one (Mixdown). The tracks
 * are kept as 44.1 kHz stereo WAVs in the mixer's own folder (they stay there between sessions until
 * the project is cleared); the mixdown is a 16-bit stereo WAV, scaled down when it would clip. The
 * folder's mixer.txt lists them. Drum Midi Settings' "Write AudioMixer output tracks into db" puts
 * the mixdown into the prompt library too.
 */
public final class AudioMix {
  private AudioMix() {}

  public static final String TITLE = "AudioMixer";
  /** Its entry in PyJav's Java list (Programs/Java/AudioMixer.java): picking it opens the mixer. */
  public static final String PROGRAM = "AudioMixer.java";
  public static final String IMPORT = "Import new track…";
  public static final String MIXDOWN = "Mixdown";
  public static final String CLEAR = "New project (clear the tracks)";
  public static final String NEW_EMPTY = "New empty track";
  /** A track's input when none is picked: the system's (the phone's microphone). */
  public static final String DEFAULT_INPUT = "Default input";
  public static final String TO_DB = "Write AudioMixer output tracks into db";
  public static final int RATE = 44100;
  public static final int MAX_VOLUME = 150;
  public static final int MIN_GAIN = -24, MAX_GAIN = 12;
  static final String LIST = "mixer.txt";
  static final String SETTINGS = "mixer-settings.txt";

  /** One track: its name, its WAV in the folder, volume (%), gain (dB), mute and solo. */
  public static final class Track {
    public String name = "";
    public String file = "";
    public int volume = 100;
    public int gain = 0;
    public boolean mute;
    public boolean solo;
    /** Its length in seconds (read when it is added). */
    public double seconds;
    /** The audio input its Record uses (a Sound devices key); empty: the default input. */
    public String input = "";

    /** What it is multiplied by: the volume and the gain together (0 when muted). */
    public double factor() {
      return mute ? 0 : volume / 100.0 * Math.pow(10, gain / 20.0);
    }
  }

  /** The mixer's folder and its tracks (as last read or changed). */
  public static File dir;
  public static final List<Track> tracks = new ArrayList<Track>();
  /** Write AudioMixer output tracks into db (Drum Midi Settings). */
  public static boolean toDb;

  /** How the app reads an MP3 (the desktop's decoder, the phone's own); set as the app starts. */
  public interface Mp3 {
    AudioIo.StereoPcm decode(byte[] mp3) throws Exception;
  }

  public static volatile Mp3 mp3;

  /** The mixer's folder (made when missing), its tracks and its setting read. */
  public static synchronized void open(File folder) {
    dir = folder;
    if (!dir.isDirectory()) dir.mkdirs();
    tracks.clear();
    toDb = false;
    try {
      File s = new File(dir, SETTINGS);
      if (s.isFile()) for (String line : new String(read(s), StandardCharsets.UTF_8).split("\n")) if (line.trim().equals("toDb=1")) toDb = true;
      File f = new File(dir, LIST);
      if (!f.isFile()) return;
      for (String line : new String(read(f), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.split("\t", -1);
        if (p.length < 6 || !new File(dir, p[1]).isFile()) continue;
        Track t = new Track();
        t.name = p[0];
        t.file = p[1];
        t.volume = clamp(parse(p[2], 100), 0, MAX_VOLUME);
        t.gain = clamp(parse(p[3], 0), MIN_GAIN, MAX_GAIN);
        t.mute = "1".equals(p[4]);
        t.solo = "1".equals(p[5]);
        t.seconds = p.length > 6 ? parseDouble(p[6]) : 0;
        t.input = p.length > 7 ? p[7] : "";
        tracks.add(t);
      }
    } catch (Exception ignored) {
      // an unreadable list starts empty
    }
  }

  /** The list and the setting written back. */
  public static synchronized void save() {
    if (dir == null) return;
    StringBuilder sb = new StringBuilder();
    for (Track t : tracks) {
      sb.append(clean(t.name)).append('\t').append(t.file).append('\t').append(t.volume).append('\t').append(t.gain).append('\t').append(t.mute ? 1 : 0).append('\t')
          .append(t.solo ? 1 : 0).append('\t').append(String.format(Locale.ROOT, "%.3f", t.seconds)).append('\t').append(clean(t.input)).append('\n');
    }
    try {
      write(new File(dir, LIST), sb.toString().getBytes(StandardCharsets.UTF_8));
      write(new File(dir, SETTINGS), ("toDb=" + (toDb ? 1 : 0) + "\n").getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }

  /** A file of these kinds can be a track. */
  public static boolean importable(String name) {
    return name != null && name.toLowerCase(Locale.ROOT).matches(".+\\.(wav|wave|mp3|mid|midi)");
  }

  /**
   * A track from a file (`name`, its bytes): a WAV, an MP3, or a drum MIDI played with `kit` (the
   * kit's voices; MIDI needs one). Kept in the folder as a 44.1 kHz stereo WAV; the track added.
   */
  public static synchronized Track add(String name, byte[] bytes, short[][] kit) throws Exception {
    AudioIo.StereoPcm pcm = decode(name, bytes, kit);
    pcm = resample(pcm, RATE);
    Track t = new Track();
    t.name = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    String stem = t.name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    File f = new File(dir, stem + ".wav");
    for (int n = 2; f.exists(); n++) f = new File(dir, stem + "-" + n + ".wav");
    write(f, encodeStereo(pcm.left, pcm.right, RATE));
    t.file = f.getName();
    t.seconds = pcm.left.length / (double) RATE;
    tracks.add(t);
    save();
    return t;
  }

  /** New empty track: "Track 1" (the first number not taken), no sound yet, for Record. */
  public static synchronized Track addEmpty() throws Exception {
    int n = 1;
    for (boolean taken = true; taken; n++) {
      taken = false;
      for (Track o : tracks) taken |= o.name.equals("Track " + n);
      if (!taken) break;
    }
    Track t = new Track();
    t.name = "Track " + n;
    File f = new File(dir, "Track_" + n + ".wav");
    for (int k = 2; f.exists(); k++) f = new File(dir, "Track_" + n + "-" + k + ".wav");
    write(f, encodeStereo(new float[0], new float[0], RATE));
    t.file = f.getName();
    tracks.add(t);
    save();
    return t;
  }

  /** The track's sound replaced by `l`/`r` (at RATE), or added after its end (`append`); its length read again. */
  public static synchronized void setSound(Track t, float[] l, float[] r, boolean append) throws Exception {
    File f = new File(dir, t.file);
    if (append && f.isFile()) {
      AudioIo.StereoPcm old = parseStereo(read(f));
      float[] nl = java.util.Arrays.copyOf(old.left, old.left.length + l.length), nr = java.util.Arrays.copyOf(old.right, old.right.length + r.length);
      System.arraycopy(l, 0, nl, old.left.length, l.length);
      System.arraycopy(r, 0, nr, old.right.length, r.length);
      l = nl;
      r = nr;
    }
    write(f, encodeStereo(l, r, RATE));
    t.seconds = l.length / (double) RATE;
    save();
  }

  /** A track taken out (its WAV deleted). */
  public static synchronized void remove(Track t) {
    tracks.remove(t);
    if (dir != null) new File(dir, t.file).delete();
    save();
  }

  /** New project: every track taken out. */
  public static synchronized void clear() {
    for (Track t : new ArrayList<Track>(tracks)) remove(t);
  }

  /** The file's sound, in stereo. */
  static AudioIo.StereoPcm decode(String name, byte[] bytes, short[][] kit) throws Exception {
    String low = name.toLowerCase(Locale.ROOT);
    if (low.endsWith(".mp3")) {
      Mp3 m = mp3;
      if (m == null) throw new IllegalArgumentException("MP3 cannot be read here");
      return m.decode(bytes);
    }
    if (low.endsWith(".mid") || low.endsWith(".midi")) {
      if (kit == null) throw new IllegalArgumentException("a MIDI file needs a kit to be played with");
      short[] mono = AudioIo.renderMidiDrums(bytes, kit, RATE);
      if (mono == null || mono.length == 0) throw new IllegalArgumentException(name + " has no drums for the kit to play");
      float[] f = new float[mono.length];
      for (int i = 0; i < f.length; i++) f[i] = mono[i] / 32768f;
      return new AudioIo.StereoPcm(f, f.clone(), RATE);
    }
    return parseStereo(bytes);
  }

  /** A WAV's two sides (a mono one on both); 8, 16, 24 or 32-bit PCM. */
  public static AudioIo.StereoPcm parseStereo(byte[] d) {
    if (d.length < 44) throw new IllegalArgumentException("Not a WAV file");
    int i = 12, sr = 44100, ch = 1, bits = 16, dataAt = -1, dataLen = 0;
    while (i + 8 <= d.length) {
      String id = new String(d, i, 4, StandardCharsets.ISO_8859_1);
      int size = u32(d, i + 4);
      int ds = i + 8;
      int de = Math.min(d.length, ds + Math.max(0, size));
      if ("fmt ".equals(id) && size >= 16) {
        ch = Math.max(1, u16(d, ds + 2));
        sr = Math.max(8000, u32(d, ds + 4));
        bits = u16(d, ds + 14);
      } else if ("data".equals(id)) {
        dataAt = ds;
        dataLen = Math.max(0, de - ds);
        break;
      }
      i = de + (size & 1);
    }
    if (dataAt < 0) throw new IllegalArgumentException("WAV has no audio data");
    int bps = Math.max(1, bits / 8);
    int frame = Math.max(1, ch * bps);
    int n = dataLen / frame;
    float[] l = new float[n], r = new float[n];
    for (int s = 0; s < n; s++) {
      int o = dataAt + s * frame;
      l[s] = sample(d, o, bits);
      r[s] = ch > 1 ? sample(d, o + bps, bits) : l[s];
    }
    return new AudioIo.StereoPcm(l, r, sr);
  }

  static float sample(byte[] d, int p, int bits) {
    if (bits <= 8) return ((d[p] & 0xff) - 128) / 128f;
    if (bits == 24) return ((d[p] & 0xff) | ((d[p + 1] & 0xff) << 8) | (d[p + 2] << 16)) / 8388608f;
    if (bits == 32) return ((d[p] & 0xff) | ((d[p + 1] & 0xff) << 8) | ((d[p + 2] & 0xff) << 16) | (d[p + 3] << 24)) / 2147483648f;
    return (short) ((d[p] & 0xff) | (d[p + 1] << 8)) / 32768f;
  }

  /** The sound at `rate` (straight lines between the samples). */
  static AudioIo.StereoPcm resample(AudioIo.StereoPcm p, int rate) {
    if (p.sr == rate) return p;
    int n = (int) ((long) p.left.length * rate / p.sr);
    float[] l = new float[n], r = new float[n];
    double step = p.sr / (double) rate;
    for (int i = 0; i < n; i++) {
      double at = i * step;
      int a = (int) at;
      double f = at - a;
      int b = Math.min(p.left.length - 1, a + 1);
      l[i] = (float) (p.left[a] * (1 - f) + p.left[b] * f);
      r[i] = (float) (p.right[a] * (1 - f) + p.right[b] * f);
    }
    return new AudioIo.StereoPcm(l, r, rate);
  }

  /** The tracks heard: the soloed ones when any is, else every track not muted. */
  public static synchronized List<Track> heard() {
    boolean anySolo = false;
    for (Track t : tracks) anySolo |= t.solo;
    List<Track> out = new ArrayList<Track>();
    for (Track t : tracks) if (anySolo ? t.solo : !t.mute) out.add(t);
    return out;
  }

  /**
   * The mix of the tracks heard (each with its volume and gain), as long as the longest; scaled down
   * so it does not clip. {left, right}, and what the scaling was in `said` (may be null).
   */
  public static synchronized float[][] mix(StringBuilder said) throws Exception {
    List<Track> on = heard();
    int n = 0;
    List<AudioIo.StereoPcm> got = new ArrayList<AudioIo.StereoPcm>();
    for (Track t : on) {
      AudioIo.StereoPcm p = parseStereo(read(new File(dir, t.file)));
      got.add(p);
      n = Math.max(n, p.left.length);
    }
    float[] l = new float[n], r = new float[n];
    for (int k = 0; k < on.size(); k++) {
      float g = (float) (on.get(k).mute && !on.get(k).solo ? 0 : on.get(k).volume / 100.0 * Math.pow(10, on.get(k).gain / 20.0));
      AudioIo.StereoPcm p = got.get(k);
      for (int i = 0; i < p.left.length; i++) {
        l[i] += p.left[i] * g;
        r[i] += p.right[i] * g;
      }
    }
    float peak = 0;
    for (int i = 0; i < n; i++) peak = Math.max(peak, Math.max(Math.abs(l[i]), Math.abs(r[i])));
    if (peak > 0.98f) {
      float s = 0.98f / peak;
      for (int i = 0; i < n; i++) {
        l[i] *= s;
        r[i] *= s;
      }
      if (said != null) said.append(String.format(Locale.ROOT, "scaled down %.1f dB so it does not clip", 20 * Math.log10(s)));
    }
    return new float[][] {l, r};
  }

  /** Mixdown's name: AudioMixer mixdown 071126-1530.wav. */
  public static String mixdownName() {
    return "AudioMixer mixdown " + new java.text.SimpleDateFormat("ddMMyy-HHmm", Locale.ROOT).format(new java.util.Date()) + ".wav";
  }

  /** Mixdown: the mix as a 16-bit stereo WAV's bytes. */
  public static byte[] mixdown(StringBuilder said) throws Exception {
    float[][] m = mix(said);
    return encodeStereo(m[0], m[1], RATE);
  }

  /** A 16-bit stereo WAV. */
  public static byte[] encodeStereo(float[] l, float[] r, int sr) {
    int n = l.length;
    byte[] out = new byte[44 + n * 4];
    put(out, 0, "RIFF");
    le32(out, 4, 36 + n * 4);
    put(out, 8, "WAVE");
    put(out, 12, "fmt ");
    le32(out, 16, 16);
    le16(out, 20, 1);
    le16(out, 22, 2);
    le32(out, 24, sr);
    le32(out, 28, sr * 4);
    le16(out, 32, 4);
    le16(out, 34, 16);
    put(out, 36, "data");
    le32(out, 40, n * 4);
    for (int i = 0; i < n; i++) {
      le16(out, 44 + i * 4, toShort(l[i]));
      le16(out, 46 + i * 4, toShort(r[i]));
    }
    return out;
  }

  /** A short track's line: "drums · 0:32 · 100% · +0 dB". */
  public static String line(Track t) {
    int s = (int) Math.round(t.seconds);
    return t.name + " · " + (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60) + " · " + t.volume + "% · " + (t.gain >= 0 ? "+" : "") + t.gain + " dB"
        + (t.mute ? " · muted" : "") + (t.solo ? " · solo" : "");
  }

  static int toShort(float v) {
    return Math.max(-32768, Math.min(32767, Math.round(v * 32767f)));
  }

  static void put(byte[] d, int at, String s) {
    for (int i = 0; i < 4; i++) d[at + i] = (byte) s.charAt(i);
  }

  static void le32(byte[] d, int at, int v) {
    d[at] = (byte) v;
    d[at + 1] = (byte) (v >> 8);
    d[at + 2] = (byte) (v >> 16);
    d[at + 3] = (byte) (v >> 24);
  }

  static void le16(byte[] d, int at, int v) {
    d[at] = (byte) v;
    d[at + 1] = (byte) (v >> 8);
  }

  static int u32(byte[] d, int at) {
    return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8) | ((d[at + 2] & 0xff) << 16) | ((d[at + 3] & 0xff) << 24);
  }

  static int u16(byte[] d, int at) {
    return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8);
  }

  static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  static int parse(String s, int dflt) {
    try {
      return Integer.parseInt(s.trim());
    } catch (Exception ex) {
      return dflt;
    }
  }

  static double parseDouble(String s) {
    try {
      return Double.parseDouble(s.trim());
    } catch (Exception ex) {
      return 0;
    }
  }

  static String clean(String s) {
    return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
  }

  static byte[] read(File f) throws java.io.IOException {
    java.io.FileInputStream in = new java.io.FileInputStream(f);
    try {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[1 << 16];
      for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
      return out.toByteArray();
    } finally {
      in.close();
    }
  }

  static void write(File f, byte[] data) throws java.io.IOException {
    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }
}
