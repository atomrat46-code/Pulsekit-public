package pulsekit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SFZ drum kits onto Pulsekit's pads, as SF2 ones are: the .sfz text (its #define and #include,
 * the control, global, master, group and region headers, each level's opcodes passed down) says
 * which sample plays which note at which velocity; each pad takes the region of its note (General
 * MIDI) at velocity 100, its first round robin, the hi-hat pedal at rest, else a sample named like
 * it. Samples are WAV (8, 16, 24 or 32 bits, float too) or FLAC. A kit is a .sfz beside its sample
 * folders, or a .zip / .tar / .tar.gz holding one or more kits (AVL Drumkits' packages).
 */
public final class Sfz {
  private Sfz() {}

  /** A kit loaded: its name, the pads' voices (null: the pad keeps its sound), and which sample each pad took. */
  public static final class Kit {
    public final String name;
    public final short[][] voices;
    public final String report;
    public final int pads;

    Kit(String name, short[][] voices, String report) {
      this.name = name;
      this.voices = voices;
      this.report = report;
      int n = 0;
      for (short[] v : voices) if (v != null) n++;
      this.pads = n;
    }

    /** The kit as an SF2 (Pulsekit keeps its kit as kit.sf2). */
    public byte[] sf2() {
      return AudioIo.encodeSf2(this.voices, this.name);
    }
  }

  /** Where the kit's files are read from (paths relative to the package or the .sfz's folder, "/" between). */
  public interface Source {
    byte[] read(String path) throws IOException;

    /** The .sfz files in it. */
    List<String> kits();
  }

  /** A pad's note when its own is not in the kit (as parseSf2 tries them). */
  static final int[] ALIASES = {36, 36, 40, 39, 37, 44, 46, 57, 59, 43, 45, 48};

  public static boolean isKitName(String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    return n.endsWith(".sfz") || n.endsWith(".zip") || n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz");
  }

  /** The source for a .sfz (its folder), a .zip, or a .tar / .tar.gz. */
  public static Source open(File f) throws IOException {
    String n = f.getName().toLowerCase(Locale.ROOT);
    if (n.endsWith(".sfz")) return new Folder(f.getParentFile(), f.getName());
    if (n.endsWith(".zip")) return new Zip(f);
    if (n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz")) return new Tar(f);
    throw new IOException("Not an SFZ kit or package: " + f.getName());
  }

  /** Whether the file is a kit: a .sfz, or a package with one or more .sfz in it. */
  public static boolean isKit(File f) {
    if (!isKitName(f.getName()) || !f.isFile()) return false;
    try {
      return !open(f).kits().isEmpty();
    } catch (Exception ex) {
      return false;
    }
  }

  /** The kit of `sfz` in the source (the first one when null). */
  public static Kit load(Source src, String sfz) throws IOException {
    List<String> kits = src.kits();
    if (kits.isEmpty()) throw new IOException("No .sfz kit in it");
    String path = sfz == null ? kits.get(0) : sfz;
    String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
    String text = preprocess(src, dir, new String(src.read(path), StandardCharsets.UTF_8), new HashMap<String, String>(), 0);
    List<Map<String, String>> regions = regions(text);
    String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("(?i)\\.sfz$", "");
    return build(src, dir, name, regions);
  }

  public static Kit load(File f, String sfz) throws IOException {
    return loadFast(f, sfz);
  }

  /** Midi2Wav's --sf2 with a kit: the kit as SF2 bytes ("path" or "package.zip#kit.sfz"). */
  public static byte[] toSf2(String spec) throws IOException {
    int hash = spec.indexOf(".zip#") >= 0 || spec.indexOf(".tar#") >= 0 || spec.indexOf(".gz#") >= 0 || spec.indexOf(".tgz#") >= 0 ? spec.lastIndexOf('#') : -1;
    File f = new File(hash > 0 ? spec.substring(0, hash) : spec);
    return load(f, hash > 0 ? spec.substring(hash + 1) : null).sf2();
  }

  // ------------------------------------------------------------ the text

  private static final Pattern DEFINE = Pattern.compile("#define\\s+(\\$\\w+)\\s+([^\\r\\n]*)");
  private static final Pattern INCLUDE = Pattern.compile("#include\\s+\"([^\"]+)\"");

  /** Comments off, #define'd names put in, #include'd files put in their place. */
  static String preprocess(Source src, String dir, String text, Map<String, String> defines, int depth) throws IOException {
    String t = text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", "");
    StringBuilder out = new StringBuilder();
    for (String line : t.split("\r?\n")) {
      Matcher d = DEFINE.matcher(line);
      if (d.find()) {
        defines.put(d.group(1), d.group(2).trim());
        continue;
      }
      String l = line;
      for (Map.Entry<String, String> e : defines.entrySet()) l = l.replace(e.getKey(), e.getValue());
      Matcher inc = INCLUDE.matcher(l);
      if (inc.find()) {
        if (depth < 8) {
          try {
            out.append(preprocess(src, dir, new String(src.read(join(dir, inc.group(1))), StandardCharsets.UTF_8), defines, depth + 1)).append('\n');
          } catch (IOException missing) {
            // a missing include: what is there
          }
        }
        continue;
      }
      out.append(l).append('\n');
    }
    return out.toString();
  }

  private static final Pattern TOKEN = Pattern.compile("<(\\w+)>|([A-Za-z0-9_]+)=");

  /** The regions with every level's opcodes in them (control's default_path, note_offset, set_cc too). */
  static List<Map<String, String>> regions(String text) {
    List<Map<String, String>> out = new ArrayList<Map<String, String>>();
    Map<String, String> control = new HashMap<String, String>(), global = new HashMap<String, String>(), master = new HashMap<String, String>(),
        group = new HashMap<String, String>();
    Map<String, String> region = null;
    Map<String, String> into = null;
    Matcher m = TOKEN.matcher(text);
    List<int[]> at = new ArrayList<int[]>();
    while (m.find()) at.add(new int[] {m.start(), m.end(), m.group(1) != null ? 1 : 0});
    for (int i = 0; i < at.size(); i++) {
      int[] a = at.get(i);
      String tok = text.substring(a[0], a[1]);
      if (a[2] == 1) {
        if (region != null) out.add(region);
        region = null;
        String h = tok.substring(1, tok.length() - 1).toLowerCase(Locale.ROOT);
        if (h.equals("control")) into = control;
        else if (h.equals("global")) {
          global = new HashMap<String, String>();
          master = new HashMap<String, String>();
          group = new HashMap<String, String>();
          into = global;
        } else if (h.equals("master")) {
          master = new HashMap<String, String>();
          group = new HashMap<String, String>();
          into = master;
        } else if (h.equals("group")) {
          group = new HashMap<String, String>();
          into = group;
        } else if (h.equals("region")) {
          region = new LinkedHashMap<String, String>(control);
          region.putAll(global);
          region.putAll(master);
          region.putAll(group);
          into = region;
        } else {
          into = null;
        }
        continue;
      }
      int end = i + 1 < at.size() ? at.get(i + 1)[0] : text.length();
      String value = text.substring(a[1], end).trim();
      // Only sample= may hold spaces; another opcode's value ends at its line.
      String key = tok.substring(0, tok.length() - 1).toLowerCase(Locale.ROOT);
      if (!key.equals("sample") && !key.equals("default_path")) value = value.split("\\s+")[0];
      else value = value.split("[\\r\\n]")[0].trim();
      if (into == null) continue;
      into.put(key, value);
      // key= is lokey, hikey and pitch_keycenter at once (a later one wins over the group's).
      if (key.equals("key")) {
        into.put("lokey", value);
        into.put("hikey", value);
        into.put("pitch_keycenter", value);
      }
    }
    if (region != null) out.add(region);
    return out;
  }

  /** A key: a number, or a note name (c4 = 60, c#4, db4). */
  static int key(String v) {
    if (v == null || v.isEmpty()) return -1;
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException notNumber) {
      Matcher m = Pattern.compile("(?i)([a-g])([#b]?)(-?\\d+)").matcher(v.trim());
      if (!m.matches()) return -1;
      int[] base = {9, 11, 0, 2, 4, 5, 7};
      int n = base[Character.toLowerCase(m.group(1).charAt(0)) - 'a'] + ("#".equals(m.group(2)) ? 1 : "b".equalsIgnoreCase(m.group(2)) ? -1 : 0);
      return (Integer.parseInt(m.group(3)) + 1) * 12 + n;
    }
  }

  private static int num(Map<String, String> r, String k, int otherwise) {
    String v = r.get(k);
    if (v == null) return otherwise;
    try {
      return (int) Math.round(Double.parseDouble(v));
    } catch (NumberFormatException ex) {
      return otherwise;
    }
  }

  private static double real(Map<String, String> r, String k, double otherwise) {
    String v = r.get(k);
    if (v == null) return otherwise;
    try {
      return Double.parseDouble(v);
    } catch (NumberFormatException ex) {
      return otherwise;
    }
  }

  /** The region's key range (key= sets both ends and the centre), with control's note and octave offsets. */
  static int[] keys(Map<String, String> r) {
    int shift = num(r, "note_offset", 0) + 12 * num(r, "octave_offset", 0);
    int k = key(r.get("key"));
    int lo = r.containsKey("lokey") ? key(r.get("lokey")) : k >= 0 ? k : 0;
    int hi = r.containsKey("hikey") ? key(r.get("hikey")) : k >= 0 ? k : 127;
    int centre = r.containsKey("pitch_keycenter") ? key(r.get("pitch_keycenter")) : k >= 0 ? k : lo;
    return new int[] {lo + shift, hi + shift, centre + shift};
  }

  /** Whether the region plays a plain note-on: attack trigger, CC conditions met by the controllers at rest (set_ccN). */
  static boolean plays(Map<String, String> r) {
    String trig = r.getOrDefault("trigger", "attack");
    if (!trig.equals("attack") && !trig.equals("first")) return false;
    for (Map.Entry<String, String> e : r.entrySet()) {
      Matcher m = Pattern.compile("(lo|hi)cc(\\d+)").matcher(e.getKey());
      if (!m.matches()) continue;
      int cc = Integer.parseInt(m.group(2));
      int value = num(r, "set_cc" + cc, 0);
      int lo = num(r, "locc" + cc, 0), hi = num(r, "hicc" + cc, 127);
      if (value < lo || value > hi) return false;
    }
    return true;
  }

  /** The region for a note: its velocity layer holding 100 (else the nearest), the first round robin. */
  static Map<String, String> pick(List<Map<String, String>> regions, int note) {
    Map<String, String> best = null;
    int bestScore = Integer.MAX_VALUE;
    for (Map<String, String> r : regions) {
      if (!r.containsKey("sample") || r.get("sample").startsWith("*")) continue;
      int[] k = keys(r);
      if (note < k[0] || note > k[1] || !plays(r)) continue;
      int lo = num(r, "lovel", 1), hi = num(r, "hivel", 127);
      int vel = 100 < lo ? lo - 100 : 100 > hi ? 100 - hi : 0;
      int seq = Math.max(0, num(r, "seq_position", 1) - 1);
      int rand = (int) Math.round(real(r, "lorand", 0) * 10);
      int score = vel * 1000 + seq * 10 + rand;
      if (score < bestScore) {
        best = r;
        bestScore = score;
      }
    }
    return best;
  }

  // ------------------------------------------------------------ the samples

  static Kit build(Source src, String dir, String name, List<Map<String, String>> regions) throws IOException {
    short[][] voices = new short[Engine.NOTES.length][];
    StringBuilder report = new StringBuilder("SFZ kit " + name + ": " + regions.size() + " regions\n");
    Map<String, AudioIo.Pcm> read = new HashMap<String, AudioIo.Pcm>();
    for (int t = 0; t < Engine.NOTES.length; t++) {
      int note = Engine.NOTES[t];
      Map<String, String> r = pick(regions, note);
      int used = note;
      if (r == null) {
        r = pick(regions, ALIASES[t]);
        used = ALIASES[t];
      }
      if (r == null) {
        // A sample named like the pad (kick, snare, hat...).
        for (Map<String, String> x : regions) {
          if (x.containsKey("sample") && plays(x) && AudioIo.nameToTrack(x.get("sample").replaceAll(".*[/\\\\]", "")) == t) {
            r = x;
            used = keys(x)[2];
            break;
          }
        }
      }
      String label = Engine.TRACK_LABEL[t] + " (" + note + ")";
      if (r == null) {
        report.append(label).append(": none in the kit (the pad keeps its sound)\n");
        continue;
      }
      String path = join(dir, r.getOrDefault("default_path", "").replace('\\', '/') + r.get("sample").replace('\\', '/'));
      try {
        AudioIo.Pcm pcm = read.get(path);
        if (pcm == null) {
          pcm = decode(src.read(path), path);
          read.put(path, pcm);
        }
        voices[t] = voice(pcm, r, used, t);
        report.append(label).append(": ").append(path).append(used != note ? " (note " + used + ")" : "")
            .append(voices[t] == null ? " is silent: the pad keeps its sound" : "").append('\n');
      } catch (IOException | RuntimeException ex) {
        report.append(label).append(": ").append(path).append(" not read: ").append(ex.getMessage()).append('\n');
      }
    }
    Kit kit = new Kit(name, voices, report.toString());
    if (kit.pads == 0) throw new IOException("No drum samples could be read from " + name + "\n" + report);
    return kit;
  }

  /** A sample's sound: WAV or FLAC, its channels mixed, at most 3 seconds of it. */
  static AudioIo.Pcm decode(byte[] d, String path) throws IOException {
    if (FlacDecode.isFlac(d)) return FlacDecode.decode(d, 3);
    if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W') return wav(d, 3);
    if (d.length >= 4 && d[0] == 'O' && d[1] == 'g' && d[2] == 'g') throw new IOException("Ogg samples are not read (WAV and FLAC are)");
    throw new IOException("not a WAV or FLAC sample");
  }

  /** A WAV of any common kind: 8, 16, 24, 32-bit integers or 32/64-bit floats (WAVE_FORMAT_EXTENSIBLE too). */
  static AudioIo.Pcm wav(byte[] d, double maxSeconds) throws IOException {
    int i = 12, fmt = 1, ch = 1, rate = 44100, bits = 16, at = -1, len = 0;
    while (i + 8 <= d.length) {
      String id = new String(d, i, 4, StandardCharsets.ISO_8859_1);
      int size = (d[i + 4] & 0xff) | ((d[i + 5] & 0xff) << 8) | ((d[i + 6] & 0xff) << 16) | ((d[i + 7] & 0xff) << 24);
      int body = i + 8;
      if (id.equals("fmt ") && size >= 16) {
        fmt = u16(d, body);
        ch = Math.max(1, u16(d, body + 2));
        rate = (d[body + 4] & 0xff) | ((d[body + 5] & 0xff) << 8) | ((d[body + 6] & 0xff) << 16) | ((d[body + 7] & 0xff) << 24);
        bits = u16(d, body + 14);
        if (fmt == 0xFFFE && size >= 26) fmt = u16(d, body + 24);
      } else if (id.equals("data")) {
        at = body;
        len = size < 0 ? d.length - body : Math.min(size, d.length - body);
        break;
      }
      if (size < 0) break;
      i = body + size + (size & 1);
    }
    if (at < 0) throw new IOException("WAV has no audio data");
    int bps = Math.max(1, bits / 8), frame = bps * ch;
    int n = Math.min(len / frame, (int) (Math.max(8000, rate) * maxSeconds));
    float[] out = new float[n];
    for (int s = 0; s < n; s++) {
      float acc = 0;
      for (int c = 0; c < ch; c++) {
        int p = at + s * frame + c * bps;
        float v;
        if (fmt == 3 && bits == 32) v = Float.intBitsToFloat((d[p] & 0xff) | ((d[p + 1] & 0xff) << 8) | ((d[p + 2] & 0xff) << 16) | (d[p + 3] << 24));
        else if (fmt == 3 && bits == 64) {
          long l = 0;
          for (int b = 7; b >= 0; b--) l = (l << 8) | (d[p + b] & 0xff);
          v = (float) Double.longBitsToDouble(l);
        } else if (bits == 8) v = ((d[p] & 0xff) - 128) / 128f;
        else if (bits == 16) v = (short) ((d[p] & 0xff) | (d[p + 1] << 8)) / 32768f;
        else if (bits == 24) v = ((d[p] & 0xff) | ((d[p + 1] & 0xff) << 8) | (d[p + 2] << 16)) / 8388608f;
        else v = ((d[p] & 0xff) | ((d[p + 1] & 0xff) << 8) | ((d[p + 2] & 0xff) << 16) | (d[p + 3] << 24)) / 2147483648f;
        acc += v;
      }
      out[s] = acc / ch;
    }
    return new AudioIo.Pcm(out, rate);
  }

  /** The pad's voice: offset to end, the region's volume, at Pulsekit's rate (tuned when the region follows the key), shaped as SF2 hits are. */
  static short[] voice(AudioIo.Pcm pcm, Map<String, String> r, int note, int track) {
    int from = Math.max(0, Math.min(pcm.samples.length, num(r, "offset", 0)));
    int to = r.containsKey("end") ? Math.min(pcm.samples.length, num(r, "end", pcm.samples.length) + 1) : pcm.samples.length;
    if (to - from < 8) return null;
    double gain = Math.pow(10, real(r, "volume", 0) / 20) * real(r, "amplitude", 100) / 100;
    double keytrack = real(r, "pitch_keytrack", 100) / 100;
    double cents = keytrack * (note - keys(r)[2]) * 100 + real(r, "tune", 0) + 100 * real(r, "transpose", 0);
    double step = (double) pcm.sr / AudioIo.SR * Math.pow(2, cents / 1200);
    int cap = AudioIo.drumCap(track) + AudioIo.SR;
    int n = (int) Math.min(cap, (to - from) / step);
    short[] raw = new short[Math.max(0, n)];
    // Averaged over the step when it skips samples (no aliasing from 44.1 or 48 kHz down to 22.05).
    int width = Math.max(1, (int) Math.floor(step));
    for (int i = 0; i < n; i++) {
      double pos = from + i * step;
      int a = (int) pos;
      double acc = 0;
      int got = 0;
      for (int w = 0; w < width && a + w < to; w++, got++) {
        int x = a + w;
        double frac = pos - a;
        double v = x + 1 < to ? pcm.samples[x] * (1 - frac) + pcm.samples[x + 1] * frac : pcm.samples[x];
        acc += v;
      }
      double v = got == 0 ? 0 : acc / got * gain * 32767;
      raw[i] = (short) Math.max(-32767, Math.min(32767, Math.round(v)));
    }
    return AudioIo.shapeDrum(raw, AudioIo.drumCap(track), track >= 9);
  }

  // ------------------------------------------------------------ the sources

  /** A path inside the kit: dir + p, with "./" and "../" worked out. */
  static String join(String dir, String p) {
    String all = (p.startsWith("/") ? p.substring(1) : dir + p).replace('\\', '/');
    List<String> parts = new ArrayList<String>();
    for (String s : all.split("/")) {
      if (s.isEmpty() || s.equals(".")) continue;
      if (s.equals("..")) {
        if (!parts.isEmpty()) parts.remove(parts.size() - 1);
      } else {
        parts.add(s);
      }
    }
    return String.join("/", parts);
  }

  private static int u16(byte[] d, int at) {
    return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8);
  }

  /** Paths matched as they are, else without caring for case (kits made on Windows). */
  private static String match(java.util.Collection<String> names, String path) {
    if (names.contains(path)) return path;
    for (String n : names) if (n.equalsIgnoreCase(path)) return n;
    return null;
  }

  static final class Folder implements Source {
    final File base;
    final String sfz;

    Folder(File base, String sfz) {
      this.base = base == null ? new File(".") : base;
      this.sfz = sfz;
    }

    @Override
    public byte[] read(String path) throws IOException {
      File f = new File(this.base, path);
      if (!f.isFile()) {
        // Each folder on the way, without caring for case.
        File at = this.base;
        for (String part : path.split("/")) {
          String[] here = at.list();
          String found = here == null ? null : match(java.util.Arrays.asList(here), part);
          if (found == null) throw new IOException("missing " + path);
          at = new File(at, found);
        }
        f = at;
      }
      return Files.readAllBytes(f.toPath());
    }

    @Override
    public List<String> kits() {
      List<String> out = new ArrayList<String>();
      out.add(this.sfz);
      return out;
    }
  }

  static final class Zip implements Source {
    final java.util.zip.ZipFile zip;
    final List<String> names = new ArrayList<String>();

    Zip(File f) throws IOException {
      this.zip = new java.util.zip.ZipFile(f);
      java.util.Enumeration<? extends java.util.zip.ZipEntry> all = this.zip.entries();
      while (all.hasMoreElements()) {
        java.util.zip.ZipEntry e = all.nextElement();
        if (!e.isDirectory()) this.names.add(e.getName().replace('\\', '/'));
      }
    }

    @Override
    public byte[] read(String path) throws IOException {
      String n = match(this.names, path);
      if (n == null) throw new IOException("missing " + path);
      java.util.zip.ZipEntry e = this.zip.getEntry(n);
      if (e == null) e = this.zip.getEntry(n.replace('/', '\\'));
      try (InputStream in = this.zip.getInputStream(e)) {
        return all(in);
      }
    }

    @Override
    public List<String> kits() {
      return sfzIn(this.names);
    }
  }

  /** A tar (gzipped or not): read through for the kits' list and again for the samples asked (kept once read). */
  static final class Tar implements Source {
    final File file;
    final List<String> names = new ArrayList<String>();
    final Map<String, byte[]> small = new HashMap<String, byte[]>();

    Tar(File f) throws IOException {
      this.file = f;
      // The .sfz texts are kept on the first pass (they are small); samples are read when asked.
      this.walk(null);
    }

    private InputStream stream() throws IOException {
      InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(this.file), 1 << 16);
      in.mark(2);
      int a = in.read(), b = in.read();
      in.reset();
      return a == 0x1f && b == 0x8b ? new java.util.zip.GZIPInputStream(in, 1 << 16) : in;
    }

    /** Through the tar: the names (want null), or the entries of `want` kept. */
    private void walk(java.util.Set<String> want) throws IOException {
      try (InputStream in = this.stream()) {
        byte[] head = new byte[512];
        String longName = null;
        while (readFully(in, head)) {
          if (head[0] == 0) break;
          String name = longName != null ? longName : cstr(head, 0, 100);
          longName = null;
          String prefix = cstr(head, 345, 155);
          if (!prefix.isEmpty() && head[257] == 'u') name = prefix + "/" + name;
          long size = octal(head, 124, 12);
          char type = (char) head[156];
          long padded = (size + 511) / 512 * 512;
          if (type == 'L') {
            byte[] b = new byte[(int) size];
            readFully(in, b);
            skip(in, padded - size);
            longName = cstr(b, 0, b.length);
            continue;
          }
          String n = name.replace('\\', '/');
          if (n.startsWith("./")) n = n.substring(2);
          boolean file = type == '0' || type == 0;
          if (file && want == null) this.names.add(n);
          boolean keep = file && (want == null ? n.toLowerCase(Locale.ROOT).endsWith(".sfz") || n.toLowerCase(Locale.ROOT).endsWith(".txt") && size < 200000 : want.contains(n));
          if (keep && size < Integer.MAX_VALUE) {
            byte[] b = new byte[(int) size];
            readFully(in, b);
            this.small.put(n, b);
            skip(in, padded - size);
          } else {
            skip(in, padded);
          }
        }
      }
    }

    @Override
    public byte[] read(String path) throws IOException {
      String n = match(this.names, path);
      if (n == null) throw new IOException("missing " + path);
      byte[] b = this.small.get(n);
      if (b != null) return b;
      // Read through once more for it (Kit loading asks sample by sample; a pass costs the tar's size).
      java.util.Set<String> one = new java.util.HashSet<String>();
      one.add(n);
      this.walk(one);
      b = this.small.get(n);
      if (b == null) throw new IOException("missing " + path);
      return b;
    }

    @Override
    public List<String> kits() {
      return sfzIn(this.names);
    }
  }

  /** Every sample the kit's pads take, read in one pass of a tar (load reads them one by one otherwise). */
  public static Kit loadFast(File f, String sfz) throws IOException {
    Source src = open(f);
    if (!(src instanceof Tar)) return load(src, sfz);
    Tar tar = (Tar) src;
    List<String> kits = tar.kits();
    if (kits.isEmpty()) throw new IOException("No .sfz kit in it");
    String path = sfz == null ? kits.get(0) : sfz;
    String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
    String text = preprocess(tar, dir, new String(tar.read(path), StandardCharsets.UTF_8), new HashMap<String, String>(), 0);
    List<Map<String, String>> regions = regions(text);
    java.util.Set<String> want = new java.util.HashSet<String>();
    for (int t = 0; t < Engine.NOTES.length; t++) {
      for (int note : new int[] {Engine.NOTES[t], ALIASES[t]}) {
        Map<String, String> r = pick(regions, note);
        if (r == null) continue;
        String p = match(tar.names, join(dir, r.getOrDefault("default_path", "").replace('\\', '/') + r.get("sample").replace('\\', '/')));
        if (p != null) want.add(p);
      }
    }
    if (!want.isEmpty()) tar.walk(want);
    return build(tar, dir, path.substring(path.lastIndexOf('/') + 1).replaceAll("(?i)\\.sfz$", ""), regions);
  }

  static List<String> sfzIn(List<String> names) {
    List<String> out = new ArrayList<String>();
    for (String n : names) if (n.toLowerCase(Locale.ROOT).endsWith(".sfz") && !n.contains("__MACOSX")) out.add(n);
    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    return out;
  }

  private static boolean readFully(InputStream in, byte[] b) throws IOException {
    int got = 0;
    while (got < b.length) {
      int n = in.read(b, got, b.length - got);
      if (n < 0) return false;
      got += n;
    }
    return true;
  }

  private static void skip(InputStream in, long n) throws IOException {
    byte[] buf = new byte[8192];
    while (n > 0) {
      int r = in.read(buf, 0, (int) Math.min(buf.length, n));
      if (r < 0) return;
      n -= r;
    }
  }

  private static String cstr(byte[] b, int at, int len) {
    int end = at;
    while (end < at + len && end < b.length && b[end] != 0) end++;
    return new String(b, at, end - at, StandardCharsets.UTF_8);
  }

  private static long octal(byte[] b, int at, int len) {
    long v = 0;
    for (int i = at; i < at + len; i++) {
      int c = b[i];
      if (c >= '0' && c <= '7') v = v * 8 + (c - '0');
      else if (c != ' ' && c != 0) break;
      else if (v > 0) break;
    }
    return v;
  }

  static byte[] all(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[1 << 16];
    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    return out.toByteArray();
  }
}
