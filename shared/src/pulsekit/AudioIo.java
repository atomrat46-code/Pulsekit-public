package pulsekit;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared WAV / MP3 / SF2 / song-analysis for desktop and Android.
 * Matches the web import-export set: MIDI, SNG, WAV, MP3, SF2, pad samples.
 */
public final class AudioIo {
  public static final int SR = 22050;

  public static final class Pcm {
    public final float[] samples;
    public final int sr;

    public Pcm(float[] samples, int sr) {
      this.samples = samples;
      this.sr = sr;
    }
  }

  /** First two channels of a file. `right` is null when the file is mono. */
  public static final class StereoPcm {
    public final float[] left;
    public final float[] right;
    public final int sr;

    public StereoPcm(float[] left, float[] right, int sr) {
      this.left = left;
      this.right = right;
      this.sr = sr;
    }

    public float[] mix() {
      if (this.right == null || this.left == null) return this.left == null ? new float[0] : this.left;
      int n = Math.min(this.left.length, this.right.length);
      float[] m = new float[n];
      for (int i = 0; i < n; i++) m[i] = (this.left[i] + this.right[i]) * 0.5f;
      return m;
    }
  }

  public static final class TrackPart {
    public int index;
    public String name = "Part";
    public String kind = "groove";
    public float startSec;
    public float endSec;
    public int bars = 1;
    public int bpm = 120;
    public int tsNum = 4;
    public int tsDen = 4;
    public String styleId = "pop";
    public String styleLabel = "Pop";
    public int hits;
    public int swing;
    public float density;
    public float confidence;
    public int[][] cells;
    /** Style-database name. Empty for a normal compose. */
    public String salt = "";
  }

  public static final class PartAnalysis {
    public float durationSec;
    public int bpm = 120;
    public String styleId = "pop";
    public final List<TrackPart> parts = new ArrayList<TrackPart>();
  }

  private static final String[] ISO_TRACK = {
    "kick", "snare", "ltom", "mtom", "htom", "chh", "ohh", "ride", "crash", "rim"
  };
  private static final int[] ISO_LO = { 30, 180, 50, 95, 160, 5000, 3500, 1100, 2000, 180 };
  private static final int[] ISO_HI = { 120, 480, 100, 175, 280, 11000, 10000, 4500, 9000, 2500 };
  private static final int[] ISO_CENTER = { 55, 220, 75, 130, 210, 8000, 6500, 2400, 4500, 800 };
  private static final float[] ISO_PRE = { 0.012f, 0.008f, 0.01f, 0.01f, 0.008f, 0.002f, 0.002f, 0.003f, 0.004f, 0.004f };
  private static final float[] ISO_POST = { 0.28f, 0.16f, 0.24f, 0.22f, 0.2f, 0.07f, 0.22f, 0.26f, 0.55f, 0.09f };
  private static final float[] ISO_RATIO = { 1.3f, 1.15f, 1.18f, 1.15f, 1.15f, 1.08f, 1.08f, 1.35f, 1.05f, 1.05f };
  private static final float[] ISO_RMS = { 0.018f, 0.016f, 0.014f, 0.014f, 0.014f, 0.01f, 0.01f, 0.008f, 0.012f, 0.012f };

  public static String sniff(byte[] d) {
    if (d == null || d.length < 4) return "unknown";
    if (d.length >= 6 && d[0] == 'P' && d[1] == 'K' && d[2] == 'S' && d[3] == 'N' && d[4] == 'G') return "sng";
    if (d[0] == 'M' && d[1] == 'T' && d[2] == 'h' && d[3] == 'd') return "midi";
    if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F') {
      if (d.length >= 12 && d[8] == 'W' && d[9] == 'A' && d[10] == 'V' && d[11] == 'E') return "wav";
      if (d.length >= 12 && d[8] == 's' && d[9] == 'f' && d[10] == 'b' && d[11] == 'k') return "sf2";
    }
    if (d[0] == 'I' && d[1] == 'D' && d[2] == '3') return "mp3";
    if ((d[0] & 0xff) == 0xff && (d[1] & 0xe0) == 0xe0) return "mp3";
    return "unknown";
  }

  public static String fileName(String label, int bpm, String ext, boolean variated) {
    String style = label == null || label.isEmpty() ? "pulsekit" : label.toLowerCase();
    String v = variated ? " variated" : "";
    return style + " " + bpm + " bpm" + v + "." + ext;
  }

  public static short[][] buildVoices(int sr) {
    short[][] voices = new short[Engine.NOTES.length][];
    for (int t = 0; t < Engine.NOTES.length; t++) {
      int n = Engine.NOTES[t];
      int len = (int) (sr * (n == 36 || n == 35 ? 0.28 : n >= 41 && n <= 50 && n != 42 && n != 46 ? 0.22 : 0.12));
      short[] buf = new short[Math.max(64, len)];
      for (int i = 0; i < buf.length; i++) {
        double env = Math.exp(-i * (n == 36 || n == 35 ? 12.0 : 22.0) / sr);
        double s;
        if (n == 36 || n == 35) s = Math.sin(2 * Math.PI * (58 - i * 30.0 / buf.length) * i / sr);
        else if (n == 37) {
          s = Math.sin(2 * Math.PI * 1040 * i / sr) * 0.9 + Math.sin(2 * Math.PI * 1560 * i / sr) * 0.2;
          env = Math.exp(-i * 70.0 / sr);
        } else if (n == 38 || n == 39) s = (Math.random() * 2 - 1) * 0.7 + Math.sin(2 * Math.PI * 180 * i / sr) * 0.3;
        else if (n == 42 || n == 46 || n == 49 || n == 51) {
          s = Math.random() * 2 - 1;
          env = Math.exp(-i * (n == 46 || n == 49 ? 10.0 : 40.0) / sr);
        } else s = Math.sin(2 * Math.PI * (n == 41 ? 90 : n == 47 ? 130 : 180) * i / sr);
        buf[i] = (short) Math.max(-32767, Math.min(32767, s * env * 22000));
      }
      voices[t] = buf;
    }
    return voices;
  }

  public static short[] mix(
      int[][] cells,
      int[][] fillPat,
      boolean fillLast,
      boolean[] mutes,
      short[][] voices,
      int bpm,
      int bars,
      int sr) {
    return mix(cells, fillPat, fillLast, mutes, voices, bpm, bars, sr, Engine.STEPS);
  }

  public static short[] mix(
      int[][] cells,
      int[][] fillPat,
      boolean fillLast,
      boolean[] mutes,
      short[][] voices,
      int bpm,
      int bars,
      int sr,
      int steps) {
    int nSteps = steps == Engine.MAX_STEPS ? Engine.MAX_STEPS : Engine.STEPS;
    int stepN = Math.max(200, (int) Math.round(sr * 60.0 / Math.max(40, bpm) / 4.0));
    int nBars = Math.max(1, bars);
    int total = stepN * nSteps * nBars;
    int[] acc = new int[total];
    for (int bar = 0; bar < nBars; bar++) {
      boolean last = fillLast && bar == nBars - 1 && fillPat != null;
      for (int s = 0; s < nSteps; s++) {
        int at = (bar * nSteps + s) * stepN;
        int[][] src = cells;
        int idx = s;
        if (last) {
          if (nSteps == Engine.STEPS) {
            src = fillPat;
          } else if (s >= nSteps - Engine.STEPS) {
            src = fillPat;
            idx = s - (nSteps - Engine.STEPS);
          }
        }
        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
          if (mutes != null && mutes[t]) continue;
          int vel = idx < src[t].length ? src[t][idx] : 0;
          if (vel <= 0) continue;
          short[] srcv = voices[t];
          if (srcv == null) continue;
          double g = vel / 127.0;
          for (int i = 0; i < srcv.length && at + i < total; i++) acc[at + i] += (int) (srcv[i] * g);
        }
      }
    }
    short[] pcm = new short[total];
    for (int i = 0; i < total; i++) pcm[i] = (short) Math.max(-32767, Math.min(32767, acc[i]));
    return pcm;
  }

  public static byte[] encodeWav(short[] pcm, int sr) {
    byte[] data = new byte[pcm.length * 2];
    for (int i = 0; i < pcm.length; i++) {
      int v = pcm[i];
      data[i * 2] = (byte) (v & 0xff);
      data[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream(44 + data.length);
    write(bos, "RIFF");
    writeLe(bos, 36 + data.length, 4);
    write(bos, "WAVEfmt ");
    writeLe(bos, 16, 4);
    writeLe(bos, 1, 2);
    writeLe(bos, 1, 2);
    writeLe(bos, sr, 4);
    writeLe(bos, sr * 2, 4);
    writeLe(bos, 2, 2);
    writeLe(bos, 16, 2);
    write(bos, "data");
    writeLe(bos, data.length, 4);
    bos.write(data, 0, data.length);
    return bos.toByteArray();
  }

  public static Pcm parseWav(byte[] d) {
    if (d.length < 44) throw new IllegalArgumentException("Not a WAV file");
    int i = 12;
    int sr = 44100;
    int ch = 1;
    int bits = 16;
    int dataAt = -1;
    int dataLen = 0;
    while (i + 8 <= d.length) {
      String id = four(d, i);
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
    int cap = Math.max(sr, 8000) * 60 * 15;
    if (n > cap) n = cap;
    float[] samples = new float[n];
    for (int s = 0; s < n; s++) {
      int o = dataAt + s * frame;
      float acc = 0;
      for (int c = 0; c < ch; c++) {
        int p = o + c * bps;
        int v;
        if (bits <= 8) v = ((d[p] & 0xff) - 128) << 8;
        else v = (short) ((d[p] & 0xff) | (d[p + 1] << 8));
        acc += v / 32768f;
      }
      samples[s] = acc / ch;
    }
    return new Pcm(samples, sr);
  }

  public static short[] floatsToShorts(float[] x) {
    if (x == null) return new short[0];
    short[] o = new short[x.length];
    for (int i = 0; i < x.length; i++) {
      o[i] = (short) Math.max(-32767, Math.min(32767, Math.round(x[i] * 32767)));
    }
    return o;
  }

  /** One pattern, repeated, at its own step count. */
  public static short[] mixCells(int[][] cells, int steps, int bpm, int repeats, short[][] voices, int sr) {
    int nSteps = Math.max(1, Math.min(Engine.MAX_STEPS, steps));
    int stepN = Math.max(200, (int) Math.round(sr * 60.0 / Math.max(40, bpm) / 4.0));
    int times = Math.max(1, repeats);
    int total = stepN * nSteps * times;
    int[] acc = new int[total];
    for (int bar = 0; bar < times; bar++) {
      for (int s = 0; s < nSteps; s++) {
        int at = (bar * nSteps + s) * stepN;
        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
          int vel = cells != null && t < cells.length && cells[t] != null && s < cells[t].length ? cells[t][s] : 0;
          if (vel <= 0) continue;
          short[] srcv = voices != null && t < voices.length ? voices[t] : null;
          if (srcv == null) continue;
          double g = vel / 127.0;
          for (int i = 0; i < srcv.length && at + i < total; i++) acc[at + i] += (int) (srcv[i] * g);
        }
      }
    }
    short[] pcm = new short[total];
    for (int i = 0; i < total; i++) pcm[i] = (short) Math.max(-32767, Math.min(32767, acc[i]));
    return pcm;
  }

  public static short[] mixSong(java.util.List<Engine.Part> parts, short[][] voices, int sr) {
    if (parts == null || parts.isEmpty()) return new short[0];
    java.util.ArrayList<short[]> chunks = new java.util.ArrayList<short[]>();
    int total = 0;
    for (Engine.Part p : parts) {
      if (p == null) continue;
      int steps = p.steps > 0 ? p.steps : Engine.STEPS;
      short[] chunk = mixCells(p.cells, steps, p.bpm > 0 ? p.bpm : 120, Math.max(1, p.repeats), voices, sr);
      chunks.add(chunk);
      total += chunk.length;
    }
    short[] out = new short[total];
    int at = 0;
    for (short[] c : chunks) {
      System.arraycopy(c, 0, out, at, c.length);
      at += c.length;
    }
    return out;
  }

  /** Drum hits in a MIDI file, one bar at a time, rendered with the kit voices. */
  public static short[] renderMidiDrums(byte[] midi, short[][] voices, int sr) {
    Engine.MidiBars bars = Engine.parseMidiBars(midi);
    if (bars == null || bars.bars.isEmpty()) return new short[0];
    java.util.ArrayList<Engine.Part> parts = new java.util.ArrayList<Engine.Part>();
    for (int i = 0; i < bars.bars.size(); i++) {
      parts.add(Engine.groove("bar", bars.bpm, bars.bars.get(i), 1));
    }
    return mixSong(parts, voices, sr);
  }

  private static int[] peaks(float[] onset, int minGap, float floor) {
    float max = 0;
    for (float v : onset) if (v > max) max = v;
    if (max <= 1e-8f) return new int[0];
    float thresh = max * floor;
    int[] tmp = new int[Math.min(onset.length, 64)];
    int n = 0;
    for (int i = 1; i < onset.length - 1 && n < tmp.length; i++) {
      if (onset[i] < thresh) continue;
      if (onset[i] < onset[i - 1] || onset[i] < onset[i + 1]) continue;
      if (n > 0 && i - tmp[n - 1] < minGap) {
        if (onset[i] > onset[tmp[n - 1]]) tmp[n - 1] = i;
        continue;
      }
      tmp[n++] = i;
    }
    int[] out = new int[n];
    System.arraycopy(tmp, 0, out, 0, n);
    return out;
  }

  private static String titleStyle(String id) {
    Engine.Style st = id == null ? null : Engine.styles().get(id);
    if (st != null && st.label != null && !st.label.isEmpty()) return st.label;
    if (id == null || id.isEmpty()) return "Pop";
    if ("hiphop".equals(id)) return "Hip-Hop";
    if ("hardrock".equals(id)) return "Hard Rock";
    if ("progmetal".equals(id)) return "Prog Metal";
    if ("ukg".equals(id)) return "UKG";
    if ("dnb".equals(id)) return "DnB";
    if ("boombap".equals(id)) return "Boom Bap";
    if ("popballad".equals(id)) return "Pop Ballad";
    if ("rockballad".equals(id)) return "Rock Ballad";
    if ("metalballad".equals(id)) return "Metal Ballad";
    return Character.toUpperCase(id.charAt(0)) + id.substring(1);
  }

  private static int swingFor(String id) {
    if ("house".equals(id)) return 12;
    if ("techno".equals(id) || "hardrock".equals(id) || "metalballad".equals(id)) return 6;
    if ("hiphop".equals(id)) return 22;
    if ("trap".equals(id) || "ukg".equals(id)) return 18;
    if ("rock".equals(id) || "dnb".equals(id) || "popballad".equals(id)) return 8;
    if ("metal".equals(id)) return 4;
    if ("progmetal".equals(id)) return 2;
    if ("rockballad".equals(id) || "pop".equals(id)) return 10;
    if ("funk".equals(id)) return 28;
    if ("breakbeat".equals(id)) return 16;
    if ("latin".equals(id)) return 20;
    if ("boombap".equals(id)) return 24;
    return 10;
  }

  private static String uniqueSetName(String base, Set<String> used) {
    String stem = base == null || base.trim().isEmpty() ? "Part" : base.trim();
    if (stem.length() > 28) stem = stem.substring(0, 28);
    String n = stem;
    int i = 2;
    while (used.contains(n)) {
      n = (stem.length() > 24 ? stem.substring(0, 24) : stem) + " " + (i++);
      if (n.length() > 28) n = n.substring(0, 28);
    }
    used.add(n);
    return n;
  }

  private static int composeDensity(TrackPart p) {
    float d = p == null || p.density <= 0 ? 0.45f : p.density;
    int base = Math.max(1, Math.min(10, Math.round(d <= 1f ? d * 10f : d)));
    String kind = p == null ? "" : p.kind;
    if ("chorus".equals(kind)) return Math.min(10, base + 2);
    if ("intro".equals(kind) || "outro".equals(kind)) return Math.max(1, base - 2);
    if ("break".equals(kind)) return Math.max(1, Math.round(base * 0.45f));
    if ("fill".equals(kind)) return Math.min(10, base + 1);
    return base;
  }

  /** Drums written for a guitar part — not the transcribed hits. */
  private static int[][] composedGroove(TrackPart p, int index) {
    String style = p != null && p.styleId != null ? p.styleId : "pop";
    Engine.Style st = Engine.styles().get(style);
    if (st == null) st = Engine.styles().get("pop");
    int[][] cells = st != null ? Engine.styleCells(st) : Engine.emptyCells();
    int tsNum = p != null && p.tsNum > 0 ? p.tsNum : 4;
    int tsDen = p != null && p.tsDen > 0 ? p.tsDen : 4;
    long seed = ((p != null && p.salt != null ? p.salt.hashCode() : 0) * 131L)
        + (p != null && p.name != null ? p.name.hashCode() : index) * 31L
        + index * 997L;
    Engine.nudgePattern(cells, new java.util.Random(seed), composeDensity(p), Engine.barSteps(tsNum, tsDen));
    return cells;
  }

  private static int[][] grooveCells(TrackPart p) {
    if (p.cells != null && Engine.hitCount(p.cells) >= 4) return Engine.copyCells(p.cells);
    Engine.Style st = Engine.styles().get(p.styleId);
    if (st == null) st = Engine.styles().get("pop");
    if (st == null) return Engine.emptyCells();
    return Engine.styleCells(st);
  }

  private static int[][] fillCellsFor(TrackPart p, int[][] groove) {
    if (p.cells != null && Engine.hitCount(p.cells) >= 1) return Engine.lastBar(p.cells);
    String style = p.styleId == null ? "house" : p.styleId;
    return Engine.buildFill("toms", groove, style);
  }

  /** Build a file set of patterns, fills, and Fillerns from analyzed parts. */
  public static Engine.FileSet fileSetFromParts(PartAnalysis a, String name) {
    return fileSetFromParts(a, name, "");
  }

  public static Engine.FileSet fileSetFromParts(PartAnalysis a, String name, String origin) {
    boolean source = sourceOrigin(origin);
    boolean restyle = "restyle".equals(origin);
    if (a != null && !source) unifyPartStyles(a);
    if (a != null && source) sourceLock(a);
    Engine.FileSet set = new Engine.FileSet();
    String setName = name == null || name.trim().isEmpty() ? "Analyze" : name.trim();
    if (setName.length() > 40) setName = setName.substring(0, 40);
    set.name = setName;
    if (Engine.isFileSetOrigin(origin)) set.origin = origin;
    if (a == null || a.parts == null || a.parts.isEmpty()) {
      int[][] gp;
      String closest;
      if (source) {
        gp = Engine.emptyCells();
        closest = "";
      } else {
        Engine.Style st = Engine.styles().get(a != null ? a.styleId : "pop");
        if (st == null) st = Engine.styles().get("pop");
        gp = st != null ? Engine.styleCells(st) : Engine.emptyCells();
        closest = st != null ? st.id : "pop";
      }
      Engine.Learned item = new Engine.Learned();
      item.name = setName.length() > 28 ? setName.substring(0, 28) : setName;
      item.bpm = a != null ? a.bpm : 120;
      item.closest = closest;
      item.cells = gp;
      set.patterns.add(item);
      if (!source) {
        Engine.LearnedFill fill = new Engine.LearnedFill();
        fill.name = (item.name + " fill");
        if (fill.name.length() > 28) fill.name = fill.name.substring(0, 28);
        fill.cells = Engine.buildFill("toms", gp, item.closest);
        fill.kind = Engine.classifyFill(fill.cells);
        set.fills.add(fill);
        set.fillerns.add(new String[] { item.name, fill.name });
      }
      if (a != null && a.durationSec > 0) set.durationSec = a.durationSec;
      return set;
    }
    Set<String> usedP = new LinkedHashSet<String>();
    Set<String> usedF = new LinkedHashSet<String>();
    String lastName = null;
    int[][] lastGroove = null;
    Map<String, String> patBySig = new HashMap<String, String>();
    Map<String, String> fillBySig = new HashMap<String, String>();
    for (int i = 0; i < a.parts.size(); i++) {
      TrackPart p = a.parts.get(i);
      TrackPart next = i + 1 < a.parts.size() ? a.parts.get(i + 1) : null;
      // A silent bar from a file is a rest in the song, not an empty pattern.
      if (source && "rest".equals(p.kind) && (p.cells == null || Engine.hitCount(p.cells) < 1)) continue;
      int[][] gp = restyle ? composedGroove(p, i) : (source ? sourceCells(p) : grooveCells(p));
      if (source && MidiImportSettings.reuseBars) {
        // A bar that comes back is the same pattern or fill, not a new one each time.
        boolean isFill = "fill".equals(p.kind);
        String seen = (isFill ? fillBySig : patBySig).get(Engine.patternSignature(gp));
        if (seen != null) {
          p.name = seen;
          if (isFill) {
            if (lastName != null) set.fillerns.add(new String[] { lastName, seen });
          } else {
            lastName = seen;
            lastGroove = gp;
          }
          continue;
        }
        if (isFill) {
          String fname = uniqueSetName(p.name == null || p.name.isEmpty() ? "Fill " + (p.index + 1) : p.name, usedF);
          p.name = fname;
          Engine.LearnedFill fill = new Engine.LearnedFill();
          fill.name = fname;
          fill.kind = Engine.classifyFill(gp);
          fill.cells = gp;
          set.fills.add(fill);
          fillBySig.put(Engine.patternSignature(gp), fname);
          if (lastName != null) set.fillerns.add(new String[] { lastName, fname });
          continue;
        }
      }
      String pname = uniqueSetName(p.name == null || p.name.isEmpty() ? ("Part " + (p.index + 1)) : p.name, usedP);
      Engine.Learned item = new Engine.Learned();
      item.name = pname;
      item.bpm = Math.max(40, Math.min(240, p.bpm));
      item.closest = source ? "" : p.styleId;
      item.cells = gp;
      item.tsNum = p.tsNum;
      item.tsDen = p.tsDen;
      set.patterns.add(item);
      if (source) {
        p.name = pname;
        patBySig.put(Engine.patternSignature(gp), pname);
      }
      if ("fill".equals(p.kind)) {
        int[][] groove = lastGroove != null ? lastGroove : gp;
        int[][] fp = restyle ? Engine.buildFill("toms", groove, p.styleId) : (source ? sourceCells(p) : fillCellsFor(p, groove));
        boolean fillNamed = p.name != null && p.name.toLowerCase().contains("fill");
        String fname = uniqueSetName(fillNamed ? p.name : (pname + " fill"), usedF);
        Engine.LearnedFill fill = new Engine.LearnedFill();
        fill.name = fname;
        fill.kind = Engine.classifyFill(fp);
        fill.cells = fp;
        set.fills.add(fill);
        if (lastName != null) set.fillerns.add(new String[] { lastName, fname });
        set.fillerns.add(new String[] { pname, fname });
        lastName = pname;
        lastGroove = gp;
        continue;
      }
      lastName = pname;
      lastGroove = gp;
      if (next != null && "fill".equals(next.kind)) continue;
      if (source) continue;
      int[][] fp = Engine.buildFill("toms", gp, p.styleId);
      String fname = uniqueSetName(pname + " fill", usedF);
      Engine.LearnedFill fill = new Engine.LearnedFill();
      fill.name = fname;
      fill.kind = Engine.classifyFill(fp);
      fill.cells = fp;
      set.fills.add(fill);
      set.fillerns.add(new String[] { pname, fname });
    }
    if (set.patterns.isEmpty() && !source) {
      Engine.Style st = Engine.styles().get(a.styleId);
      if (st == null) st = Engine.styles().get("pop");
      int[][] gp = st != null ? Engine.styleCells(st) : Engine.emptyCells();
      String pname = uniqueSetName(setName, usedP);
      Engine.Learned item = new Engine.Learned();
      item.name = pname;
      item.bpm = a.bpm;
      item.closest = st != null ? st.id : "pop";
      item.cells = gp;
      set.patterns.add(item);
      int[][] fp = Engine.buildFill("toms", gp, item.closest);
      String fname = uniqueSetName(pname + " fill", usedF);
      Engine.LearnedFill fill = new Engine.LearnedFill();
      fill.name = fname;
      fill.kind = Engine.classifyFill(fp);
      fill.cells = fp;
      set.fills.add(fill);
      set.fillerns.add(new String[] { pname, fname });
    }
    if (a != null && a.parts != null) {
      for (TrackPart p : a.parts) set.parts.add(infoOf(p));
    }
    if (a != null && a.durationSec > 0) set.durationSec = a.durationSec;
    Engine.unifyFileSetParts(set.parts, set);
    if (Engine.isFileSetOrigin(origin)) set.origin = origin;
    return set;
  }

  /** New drums in one style-database voice. Part clocks stay. Tempo nests with the track. */
  public static Engine.FileSet restyleFileSet(Engine.FileSet set, String kit, String styleName, int hats, float four, float dkick, int styleBpm, float back) {
    Engine.FileSet src = set == null ? new Engine.FileSet() : set;
    String style = kit == null || kit.isEmpty() ? "pop" : kit;
    if (Engine.styles().get(style) == null) style = "pop";
    String label = styleName == null || styleName.trim().isEmpty() ? titleStyle(style) : styleName.trim();
    int track = 120;
    if (!src.parts.isEmpty() && src.parts.get(0).bpm > 0) track = src.parts.get(0).bpm;
    else if (!src.patterns.isEmpty() && src.patterns.get(0).bpm > 0) track = src.patterns.get(0).bpm;
    int synced = StyleDb.syncBpm(track, styleBpm);
    PartAnalysis a = new PartAnalysis();
    a.styleId = style;
    a.durationSec = src.durationSec;
    a.bpm = synced;
    float dens = 0.25f + Math.max(0, Math.min(16, hats)) / 16f * 0.55f;
    if (dkick > 0.2f) dens += 0.1f;
    if (four < 0.4f) dens -= 0.08f;
    dens = Math.max(0.1f, Math.min(1f, dens));
    if (src.parts.isEmpty()) {
      TrackPart p = new TrackPart();
      p.name = src.name;
      p.kind = "groove";
      p.styleId = style;
      p.styleLabel = label;
      p.salt = label;
      p.density = dens;
      p.bpm = synced;
      a.parts.add(p);
    } else {
      for (int i = 0; i < src.parts.size(); i++) {
        Engine.FileSetPart info = src.parts.get(i);
        TrackPart p = new TrackPart();
        p.index = i;
        p.name = info.name;
        p.kind = info.kind == null ? "groove" : info.kind;
        p.startSec = info.startSec;
        p.endSec = info.endSec;
        p.bars = info.bars;
        p.bpm = synced;
        p.tsNum = info.tsNum > 0 ? info.tsNum : 4;
        p.tsDen = info.tsDen > 0 ? info.tsDen : 4;
        p.styleId = style;
        p.styleLabel = label;
        p.salt = label;
        p.density = dens;
        p.cells = null;
        a.parts.add(p);
      }
    }
    float[] starts = new float[a.parts.size()];
    float[] ends = new float[a.parts.size()];
    int[] bars = new int[a.parts.size()];
    for (int i = 0; i < a.parts.size(); i++) {
      starts[i] = a.parts.get(i).startSec;
      ends[i] = a.parts.get(i).endSec;
      bars[i] = a.parts.get(i).bars;
    }
    Engine.FileSet next = fileSetFromParts(a, src.name, "restyle");
    next.origin = src.origin == null ? "" : src.origin;
    if (src.durationSec > 0) next.durationSec = src.durationSec;
    for (int i = 0; i < next.parts.size() && i < starts.length; i++) {
      next.parts.get(i).startSec = starts[i];
      next.parts.get(i).endSec = ends[i];
      if (bars[i] > 0) next.parts.get(i).bars = bars[i];
      next.parts.get(i).styleLabel = label;
      next.parts.get(i).bpm = synced;
      if ("compose".equals(next.origin)) next.parts.get(i).swing = 0;
    }
    for (int i = 0; i < next.patterns.size(); i++) {
      Engine.Learned item = next.patterns.get(i);
      item.bpm = synced;
      String kind = i < next.parts.size() && next.parts.get(i).kind != null ? next.parts.get(i).kind : "";
      if (!"fill".equalsIgnoreCase(kind)) stampStyleHits(item.cells, four, back, hats, dkick);
      if (i < next.parts.size() && item.cells != null) next.parts.get(i).hits = Engine.hitCount(item.cells);
    }
    next.sourceWav = src.sourceWav;
    return next;
  }

  /** Kicks, backbeat, hats, and double-kicks on the same grid as the guitar. */
  private static void stampStyleHits(int[][] cells, float four, float back, int hats, float dkick) {
    if (cells == null || cells.length < 7 || cells[0] == null) return;
    int n = cells[0].length;
    if (n < 4) return;
    int kick = Engine.track("kick");
    int dkickT = Engine.track("dkick");
    int snare = Engine.track("snare");
    int chh = Engine.track("chh");
    int ohh = Engine.track("ohh");
    int beat = Math.max(1, n / 4);
    if (four >= 0.7f) {
      for (int b = 0; b < 4; b++) {
        int i = Math.min(n - 1, b * beat);
        if (kick < cells.length && cells[kick] != null && i < cells[kick].length && cells[kick][i] < 108) cells[kick][i] = 110;
      }
    }
    if (back >= 0.55f && snare < cells.length && cells[snare] != null) {
      int i2 = Math.min(cells[snare].length - 1, beat);
      int i4 = Math.min(cells[snare].length - 1, beat * 3);
      if (cells[snare][i2] < 108) cells[snare][i2] = 112;
      if (cells[snare][i4] < 108) cells[snare][i4] = 112;
    }
    int want = Math.max(0, Math.min(n, Math.round(Math.max(0, hats) / 16f * n)));
    boolean[] used = new boolean[n];
    if (chh < cells.length && cells[chh] != null) {
      int hn = Math.min(n, cells[chh].length);
      for (int i = 0; i < hn; i++) cells[chh][i] = 0;
      for (int h = 0; h < want; h++) {
        int i = (int) Math.round((h * (double) hn) / Math.max(1, want)) % hn;
        if (used[i]) {
          for (int d = 1; d < hn; d++) {
            int at = (i + d) % hn;
            if (!used[at]) { i = at; break; }
          }
        }
        used[i] = true;
        if (ohh < cells.length && cells[ohh] != null && i < cells[ohh].length && cells[ohh][i] > 0) cells[ohh][i] = 0;
        cells[chh][i] = (i % 2 == 0) ? 100 : 84;
      }
    }
    if (dkickT < cells.length && cells[dkickT] != null) {
      int dn = cells[dkickT].length;
      if (dkick > 0.15f) {
        int vel = dkick > 0.5f ? 110 : 96;
        for (int b = 0; b < 4; b++) {
          int i = Math.min(n - 1, b * beat);
          int prev = (i - 1 + dn) % dn;
          if (cells[dkickT][prev] < vel) cells[dkickT][prev] = vel;
        }
      } else {
        for (int i = 0; i < dn; i++) cells[dkickT][i] = 0;
      }
    }
  }

  /** Turn a drum MIDI into analyzed parts so Info matches Analyze / Isolation. */
  public static PartAnalysis partAnalysisFromMidi(Engine.MidiBars bars) {
    PartAnalysis a = new PartAnalysis();
    if (bars == null || bars.bars == null || bars.bars.isEmpty()) return a;
    a.bpm = Engine.clampBpm(bars.bpm);
    int tsNum = bars.tsNum > 0 ? bars.tsNum : 4;
    int tsDen = bars.tsDen > 0 ? bars.tsDen : 4;
    java.util.List<Engine.MidiSeg> segs = Engine.segmentMidiBars(bars.bars);
    float barSec = tsNum * (4f / tsDen) * 60f / Math.max(40, a.bpm);
    java.util.ArrayList<Object[]> raw = new java.util.ArrayList<Object[]>();
    for (Engine.MidiSeg s : segs) {
      String style = StyleDb.suggest(s.groove, a.bpm);
      if (style == null || style.isEmpty()) style = "pop";
      raw.add(new Object[] { Boolean.FALSE, Integer.valueOf(Math.max(1, s.grooveRepeats)), s.groove, style });
      if (s.fill != null) {
        raw.add(new Object[] { Boolean.TRUE, Integer.valueOf(1), s.fill, style });
      }
    }
    int n = raw.size();
    float t = 0;
    String firstStyle = "pop";
    for (int i = 0; i < n; i++) {
      Object[] row = raw.get(i);
      boolean fillish = ((Boolean) row[0]).booleanValue();
      int nBars = ((Integer) row[1]).intValue();
      int[][] cells = (int[][]) row[2];
      String style = (String) row[3];
      if (i == 0) firstStyle = style;
      int hits = Engine.hitCount(cells);
      float density = Math.min(1f, hits / 32f);
      String kind = hits == 0 ? "rest" : midiPartKind(i, n, nBars, density, fillish);
      TrackPart p = new TrackPart();
      p.index = i;
      p.name = midiPartName(kind, i, n);
      p.kind = kind;
      p.startSec = t;
      t += nBars * barSec;
      p.endSec = t;
      p.bars = nBars;
      p.bpm = a.bpm;
      p.tsNum = tsNum;
      p.tsDen = tsDen;
      p.styleId = style;
      p.styleLabel = titleStyle(style);
      p.hits = hits;
      p.swing = 12;
      p.density = density;
      p.confidence = 0.85f;
      p.cells = Engine.copyCells(cells);
      a.parts.add(p);
    }
    a.durationSec = t;
    a.styleId = firstStyle;
    unifyPartStyles(a);
    return a;
  }

  public static Engine.FileSet fileSetFromMidi(Engine.MidiBars bars, String name) {
    return fileSetFromMidi(bars, name, "midi");
  }

  public static Engine.FileSet fileSetFromMidi(Engine.MidiBars bars, String name, String origin) {
    return fileSetFromParts(partAnalysisFromMidi(bars), name, origin);
  }

  private static String midiPartName(String kind, int index, int total) {
    if ("rest".equals(kind)) return "Silent " + (index + 1);
    if ("intro".equals(kind)) return "Intro";
    if ("outro".equals(kind)) return "Outro";
    if ("fill".equals(kind)) return "Fill " + (index + 1);
    if ("break".equals(kind)) return "Break " + (index + 1);
    if ("chorus".equals(kind)) return "Chorus " + (index + 1);
    if (total <= 1) return "Groove";
    return "Part " + (index + 1);
  }

  private static String midiPartKind(int i, int n, int bars, float density, boolean fill) {
    if (fill && bars <= 2) return "fill";
    if (density < 0.18f && bars <= 4 && i > 0 && i < n - 1) return "break";
    if (i == 0 && density < 0.18f) return "intro";
    if (i == n - 1 && density < 0.18f) return "outro";
    if (density >= 0.42f && n > 1) return "chorus";
    return "groove";
  }

  private static Engine.FileSetPart infoOf(TrackPart p) {
    Engine.FileSetPart x = new Engine.FileSetPart();
    x.name = p.name;
    x.kind = p.kind;
    x.bpm = p.bpm;
    x.tsNum = p.tsNum;
    x.tsDen = p.tsDen;
    x.styleLabel = p.styleLabel;
    x.bars = p.bars;
    x.startSec = p.startSec;
    x.endSec = p.endSec;
    x.hits = p.hits;
    x.swing = p.swing;
    x.density = p.density;
    return x;
  }

  /** MIDI, program output, Isolation, Analyze, and Compose: no style name, no style swing. The notes and timing stay as in the file. */
  static void sourceLock(PartAnalysis a) {
    if (a == null) return;
    a.styleId = "";
    if (a.parts != null) {
      for (TrackPart p : a.parts) {
        if (p == null) continue;
        p.styleId = "";
        p.styleLabel = "";
        p.swing = 0;
      }
    }
    fitTrackParts(a);
  }

  private static boolean sourceOrigin(String origin) {
    if ("midi".equals(origin) || "program".equals(origin)) return MidiImportSettings.asWritten;
    return "analyze".equals(origin) || "isolate".equals(origin) || "compose".equals(origin);
  }

  private static int[][] sourceCells(TrackPart p) {
    if (p != null && p.cells != null) return Engine.copyCells(p.cells);
    return Engine.emptyCells();
  }

  static void unifyPartStyles(PartAnalysis a) {
    if (a == null || a.parts == null || a.parts.isEmpty()) return;
    java.util.ArrayList<String> kits = new java.util.ArrayList<String>();
    java.util.ArrayList<Double> weights = new java.util.ArrayList<Double>();
    for (TrackPart p : a.parts) {
      String kit = p.styleId == null || p.styleId.isEmpty() ? a.styleId : p.styleId;
      double w = Math.max(1, p.bars);
      String kind = p.kind == null ? "" : p.kind.toLowerCase();
      if ("fill".equals(kind) || "break".equals(kind) || "rest".equals(kind)) w *= 0.12;
      else if ("chorus".equals(kind)) w *= 1.2;
      kits.add(kit);
      weights.add(w);
    }
    kits.add(a.styleId == null || a.styleId.isEmpty() ? "pop" : a.styleId);
    weights.add(2.0);
    String kit = StyleDb.unify(kits, weights, a.styleId);
    a.styleId = kit;
    String label = titleStyle(kit);
    int swing = swingFor(kit);
    int bpm = a.bpm > 0 ? Engine.clampBpm(a.bpm) : 120;
    a.bpm = bpm;
    for (TrackPart p : a.parts) {
      p.styleId = kit;
      p.styleLabel = label;
      p.swing = swing;
      p.bpm = bpm;
    }
    fitTrackParts(a);
  }

  static void fitTrackParts(PartAnalysis a) {
    if (a == null || a.parts == null || a.parts.isEmpty()) return;
    float dur = a.durationSec > 0 ? a.durationSec : 0;
    if (!(dur > 0)) {
      for (TrackPart p : a.parts) if (p.endSec > dur) dur = p.endSec;
    }
    if (!(dur > 0)) return;
    dur = Engine.round1(dur);
    a.durationSec = dur;
    float span = 0;
    for (TrackPart p : a.parts) if (p.endSec > span) span = p.endSec;
    if (span <= 0) {
      int n = a.parts.size();
      for (int i = 0; i < n; i++) {
        TrackPart p = a.parts.get(i);
        p.startSec = Engine.round1((i / (float) n) * dur);
        p.endSec = Engine.round1(((i + 1f) / n) * dur);
      }
    } else {
      float s = dur / span;
      for (TrackPart p : a.parts) {
        p.startSec = Math.max(0, Engine.round1(p.startSec * s));
        p.endSec = Math.max(0, Engine.round1(p.endSec * s));
      }
    }
    a.parts.get(0).startSec = 0;
    a.parts.get(a.parts.size() - 1).endSec = dur;
  }

  public static byte[] encodeSf2(short[][] voices, String name) {
    int n = Engine.NOTES.length;
    int pad = 46;
    int frames = 0;
    int[] starts = new int[n];
    int[] ends = new int[n];
    for (int t = 0; t < n; t++) {
      starts[t] = frames;
      int len = voices[t] == null ? 64 : voices[t].length;
      ends[t] = frames + len;
      frames += len + pad;
    }
    byte[] smpl = new byte[frames * 2];
    int o = 0;
    for (int t = 0; t < n; t++) {
      short[] src = voices[t] == null ? new short[64] : voices[t];
      for (short v : src) {
        smpl[o++] = (byte) (v & 0xff);
        smpl[o++] = (byte) ((v >> 8) & 0xff);
      }
      o += pad * 2;
    }
    byte[] shdr = new byte[46 * (n + 1)];
    for (int t = 0; t < n; t++) {
      int b = t * 46;
      putStr(shdr, b, Engine.TRACK_SHORT[t], 20);
      put32(shdr, b + 20, starts[t]);
      put32(shdr, b + 24, ends[t]);
      put32(shdr, b + 28, starts[t]);
      put32(shdr, b + 32, ends[t]);
      put32(shdr, b + 36, SR);
      shdr[b + 40] = (byte) Engine.NOTES[t];
      put16(shdr, b + 44, 1);
    }
    putStr(shdr, n * 46, "EOS", 20);
    int gensPer = 3;
    byte[] igen = new byte[4 * (n * gensPer + 1)];
    for (int t = 0; t < n; t++) {
      int b = t * gensPer * 4;
      int note = Engine.NOTES[t];
      put16(igen, b, 43);
      put16(igen, b + 2, note | (note << 8));
      put16(igen, b + 4, 58);
      put16(igen, b + 6, note);
      put16(igen, b + 8, 53);
      put16(igen, b + 10, t);
    }
    byte[] ibag = new byte[4 * (n + 1)];
    for (int i = 0; i <= n; i++) put16(ibag, i * 4, i * gensPer);
    byte[] inst = new byte[44];
    putStr(inst, 0, "Drumkit", 20);
    put16(inst, 20, 0);
    putStr(inst, 22, "EOI", 20);
    put16(inst, 42, n);
    byte[] pgen = new byte[4];
    put16(pgen, 0, 41);
    put16(pgen, 2, 0);
    byte[] pbag = new byte[8];
    put16(pbag, 4, 1);
    byte[] phdr = new byte[76];
    putStr(phdr, 0, (name == null ? "Pulsekit" : name), 20);
    put16(phdr, 22, 128);
    putStr(phdr, 38, "EOP", 20);
    put16(phdr, 38 + 24, 1);
    byte[] pmod = new byte[10];
    byte[] imod = new byte[10];
    byte[] ifil = new byte[4];
    put16(ifil, 0, 2);
    put16(ifil, 2, 1);
    byte[] inam = (name == null ? "Pulsekit" : name).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    byte[] isng = "EMU8000".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    byte[] body = cat(new byte[][] {
      list("INFO", new byte[][] { ck("ifil", ifil), ck("isng", isng), ck("INAM", inam) }),
      list("sdta", new byte[][] { ck("smpl", smpl) }),
      list("pdta", new byte[][] {
        ck("phdr", phdr), ck("pbag", pbag), ck("pmod", pmod), ck("pgen", pgen),
        ck("inst", inst), ck("ibag", ibag), ck("imod", imod), ck("igen", igen), ck("shdr", shdr),
      }),
    });
    byte[] riff = new byte[12 + body.length];
    putStr(riff, 0, "RIFF", 4);
    put32(riff, 4, 4 + body.length);
    putStr(riff, 8, "sfbk", 4);
    System.arraycopy(body, 0, riff, 12, body.length);
    return riff;
  }

  public static short[][] parseSf2(byte[] d) {
    short[][] voices = new short[Engine.NOTES.length][];
    int[] peaks = new int[Engine.NOTES.length];
    if (d == null || d.length < 16 || !"RIFF".equals(four(d, 0)) || !"sfbk".equals(four(d, 8))) {
      throw new IllegalArgumentException("Not a SoundFont (.sf2) file");
    }
    int smplOff = -1, smplLen = 0, shdrOff = -1, shdrLen = 0;
    int ibagOff = -1, ibagLen = 0, igenOff = -1, igenLen = 0;
    List<int[]> chunks = new ArrayList<>();
    int riffEnd = Math.min(d.length, 8 + Math.max(0, u32(d, 4)));
    walk(d, 12, riffEnd, chunks);
    for (int[] c : chunks) {
      String id = four(d, c[0]);
      if ("smpl".equals(id)) { smplOff = c[0] + 8; smplLen = c[1]; }
      else if ("shdr".equals(id)) { shdrOff = c[0] + 8; shdrLen = c[1]; }
      else if ("ibag".equals(id)) { ibagOff = c[0] + 8; ibagLen = c[1]; }
      else if ("igen".equals(id)) { igenOff = c[0] + 8; igenLen = c[1]; }
    }
    if (smplOff < 0 || shdrOff < 0 || smplLen < 16 || shdrLen < 46) {
      throw new IllegalArgumentException("SoundFont is missing samples");
    }
    int rec = 46;
    int count = Math.min(shdrLen / rec, 4000);
    int[] exact = new int[128];
    int[] range = new int[128];
    Arrays.fill(exact, -1);
    Arrays.fill(range, -1);
    if (ibagOff >= 0 && igenOff >= 0 && ibagLen >= 8) {
      int bags = Math.min(ibagLen / 4, 8000);
      for (int b = 0; b < bags - 1; b++) {
        int g0 = u16(d, ibagOff + b * 4);
        int g1 = u16(d, ibagOff + (b + 1) * 4);
        if (g1 < g0) continue;
        int lo = 0, hi = 127, sid = -1;
        int gMax = Math.min(g1, igenLen / 4);
        for (int g = g0; g < gMax; g++) {
          int off = igenOff + g * 4;
          if (off + 4 > d.length) break;
          int op = u16(d, off);
          int amt = u16(d, off + 2);
          if (op == 43) {
            lo = amt & 0xff;
            hi = (amt >> 8) & 0xff;
          } else if (op == 53) sid = amt;
        }
        if (sid < 0 || sid >= count || hi < lo) continue;
        if (lo == hi && lo < 128) exact[lo] = sid;
        else {
          int n1 = Math.min(hi, 127);
          for (int n = Math.max(0, lo); n <= n1; n++) {
            if (range[n] < 0) range[n] = sid;
          }
        }
      }
    }
    int[] aliases = { 36, 36, 40, 39, 37, 44, 46, 57, 59, 43, 45, 48 };
    for (int t = 0; t < Engine.NOTES.length; t++) {
      int sid = -1;
      int note = Engine.NOTES[t];
      if (note < 128 && exact[note] >= 0) sid = exact[note];
      else if (note < 128 && range[note] >= 0) sid = range[note];
      else if (aliases[t] < 128 && exact[aliases[t]] >= 0) sid = exact[aliases[t]];
      else if (aliases[t] < 128 && range[aliases[t]] >= 0) sid = range[aliases[t]];
      if (sid < 0) continue;
      takeSample(d, smplOff, smplLen, shdrOff, sid, t, voices, peaks);
    }
    for (int i = 0; i < count; i++) {
      int b = shdrOff + i * rec;
      if (b + rec > d.length) break;
      String nm = cstr(d, b, 20);
      if ("EOS".equals(nm) || nm.isEmpty()) continue;
      int type = u16(d, b + 44);
      if ((type & 0x8000) != 0) continue;
      int pitch = d[b + 40] & 0xff;
      int noteTrack = Engine.trackForNote(pitch);
      if (noteTrack < 0) noteTrack = nameToTrack(nm);
      if (noteTrack < 0) continue;
      takeSample(d, smplOff, smplLen, shdrOff, i, noteTrack, voices, peaks);
    }
    int got = 0;
    for (short[] v : voices) if (v != null) got++;
    if (got == 0) throw new IllegalArgumentException("No drum samples mapped for this SoundFont");
    return voices;
  }

  private static int nameToTrack(String nm) {
    String low = nm.toLowerCase();
    if (low.contains("double") || low.contains("dkick") || low.contains("pedal")) return Engine.track("dkick");
    if (low.contains("kick") || low.contains("bd") || low.contains("bass")) return Engine.track("kick");
    if (low.contains("snare") || low.contains("sd")) return Engine.track("snare");
    if (low.contains("clap")) return Engine.track("clap");
    if (low.contains("rim")) return Engine.track("rim");
    if (low.contains("ohh") || (low.contains("hat") && low.contains("open")) || low.contains("open hat")) return Engine.track("ohh");
    if (low.contains("hat") || low.contains("hh") || low.contains("chh") || low.contains("hihat")) return Engine.track("chh");
    if (low.contains("crash")) return Engine.track("crash");
    if (low.contains("ride")) return Engine.track("ride");
    if (low.contains("floor") || low.contains("ltom") || low.contains("flr") || low.equals("flo")
        || low.contains("low tom") || low.contains("lo tom") || low.contains("tom 1")
        || low.contains("tom1") || low.contains("lowtom")) return Engine.track("ltom");
    if (low.contains("mtom") || low.contains("mid tom") || low.contains("middle tom")
        || low.contains("tom 2") || low.contains("tom2")) return Engine.track("mtom");
    if (low.contains("htom") || low.contains("high tom") || low.contains("hi tom")
        || low.contains("rack") || low.contains("tom 3") || low.contains("tom3") || low.contains("tom")) return Engine.track("htom");
    return -1;
  }

  private static int drumCap(int t) {
    String id = t >= 0 && t < Engine.TRACK_ID.length ? Engine.TRACK_ID[t] : "";
    if ("chh".equals(id)) return SR / 6;
    if ("rim".equals(id)) return SR / 5;
    if ("ohh".equals(id)) return SR / 2;
    if ("crash".equals(id) || "ride".equals(id)) return (int) (SR * 0.85);
    if ("ltom".equals(id) || "mtom".equals(id) || "htom".equals(id)) return (int) (SR * 0.55);
    return (int) (SR * 0.45);
  }

  private static void takeSample(
      byte[] d, int smplOff, int smplLen, int shdrOff, int sid, int track,
      short[][] voices, int[] peaks) {
    int b = shdrOff + sid * 46;
    if (b + 46 > d.length) return;
    int type = u16(d, b + 44);
    if ((type & 0x8000) != 0) return;
    int start = u32(d, b + 20);
    int end = u32(d, b + 24);
    int srcRate = u32(d, b + 36);
    if (start < 0 || end <= start) return;
    if (srcRate < 8000 || srcRate > 96000) srcRate = 44100;
    short[] buf = extractDrum(d, smplOff, smplLen, start, end, srcRate, drumCap(track), track >= 8);
    if (buf == null) return;
    int pk = samplePeak(buf);
    if (voices[track] != null && pk <= peaks[track]) return;
    voices[track] = buf;
    peaks[track] = pk;
  }

  private static short[] extractDrum(
      byte[] d, int smplOff, int smplLen, int start, int end, int srcRate, int maxFrames, boolean tom) {
    int ratio = Math.max(1, (srcRate + SR / 2) / SR);
    int avail = smplLen / 2 - start;
    if (avail < 8) return null;
    int frames = Math.min(end - start, avail);
    frames = Math.min(frames, maxFrames * ratio + SR);
    int rawLen = Math.max(8, frames / ratio);
    short[] raw = new short[rawLen];
    for (int s = 0; s < rawLen; s++) {
      int o = smplOff + (start + s * ratio) * 2;
      if (o + 1 >= d.length || o + 1 >= smplOff + smplLen) break;
      raw[s] = (short) ((d[o] & 0xff) | (d[o + 1] << 8));
    }
    int peak = samplePeak(raw);
    if (peak < 400) return null;
    int thresh = Math.max(80, peak / 40);
    int a = 0;
    int b = raw.length - 1;
    while (a < b && (raw[a] < thresh && raw[a] > -thresh)) a++;
    while (b > a && (raw[b] < thresh && raw[b] > -thresh)) b--;
    int tail = Math.min(raw.length - 1, b + Math.max(24, SR / 50));
    int len = Math.min(maxFrames, tail - a + 1);
    if (len < 8) return null;
    float g = 26000f / peak;
    if (tom) g *= 1.35f;
    if (g > 3.5f) g = 3.5f;
    if (g < 0.55f) g = 0.55f;
    short[] out = new short[len];
    for (int i = 0; i < len; i++) {
      int v = Math.round(raw[a + i] * g);
      if (v > 32767) v = 32767;
      else if (v < -32767) v = -32767;
      out[i] = (short) v;
    }
    return out;
  }

  private static int samplePeak(short[] buf) {
    int p = 0;
    for (short s : buf) {
      int a = s < 0 ? -s : s;
      if (a > p) p = a;
    }
    return p;
  }

  /**
   * Tiny MPEG-1 Layer III encoder (mono, 44100 Hz, 64 kbps).
   * Rhythm-accurate enough for drum loops; not a studio encoder.
   */
  public static byte[] encodeMp3(short[] pcmIn, int sr) {
    short[] pcm = resampleShort(pcmIn, sr, 44100);
    final int br = 64000;
    final int outSr = 44100;
    final int frameSamples = 1152;
    ByteArrayOutputStream bos = new ByteArrayOutputStream(pcm.length);
    int off = 0;
    while (off < pcm.length) {
      float[] xr = new float[576];
      for (int i = 0; i < 576; i++) {
        int a = off + i * 2;
        float s0 = a < pcm.length ? pcm[a] / 32768f : 0;
        float s1 = a + 1 < pcm.length ? pcm[a + 1] / 32768f : 0;
        xr[i] = (s0 + s1) * 0.5f;
      }
      int[] is = new int[576];
      for (int i = 0; i < 576; i++) {
        int q = Math.round(xr[i] * 24);
        is[i] = q < -1 ? -1 : q > 1 ? 1 : q;
      }
      int frame = (int) (144L * br / outSr);
      byte[] f = new byte[frame];
      f[0] = (byte) 0xff;
      f[1] = (byte) 0xfb;
      f[2] = (byte) 0x50;
      f[3] = (byte) 0xc0;
      BitPack bp = new BitPack(f, 4);
      bp.bits(0, 9);
      bp.bits(0, 5);
      bp.bits(0, 4);
      int partLen = 0;
      BitPack main = new BitPack(new byte[256], 0);
      for (int i = 0; i + 3 < 576; i += 4) {
        int h = huffA(is[i], is[i + 1], is[i + 2], is[i + 3]);
        int n = huffAn(is[i], is[i + 1], is[i + 2], is[i + 3]);
        main.bits(h, n);
        partLen += n;
      }
      for (int g = 0; g < 2; g++) {
        bp.bits(g == 0 ? partLen : 0, 12);
        bp.bits(0, 9);
        bp.bits(160, 8);
        bp.bits(0, 4);
        bp.bits(0, 1);
        bp.bits(0, 5);
        bp.bits(0, 5);
        bp.bits(0, 5);
        bp.bits(0, 4);
        bp.bits(0, 3);
        bp.bits(0, 1);
        bp.bits(0, 1);
        bp.bits(0, 1);
      }
      byte[] md = main.data;
      int siEnd = 4 + 17;
      int copy = Math.min(md.length, f.length - siEnd);
      System.arraycopy(md, 0, f, siEnd, copy);
      bos.write(f, 0, f.length);
      off += frameSamples;
    }
    return bos.toByteArray();
  }

  private static int huffA(int a, int b, int c, int d) {
    int s = (sign1(a) << 3) | (sign1(b) << 2) | (sign1(c) << 1) | sign1(d);
    int mag = (nz(a) << 3) | (nz(b) << 2) | (nz(c) << 1) | nz(d);
    return (mag << 4) | s;
  }

  private static int huffAn(int a, int b, int c, int d) {
    return 4 + nz(a) + nz(b) + nz(c) + nz(d);
  }

  private static int nz(int v) {
    return v == 0 ? 0 : 1;
  }

  private static int sign1(int v) {
    return v < 0 ? 1 : 0;
  }

  private static final class BitPack {
    final byte[] data;
    int bit;

    BitPack(byte[] data, int startByte) {
      this.data = data;
      this.bit = startByte * 8;
    }

    void bits(int v, int n) {
      for (int i = n - 1; i >= 0; i--) {
        int b = bit / 8;
        if (b >= data.length) return;
        if (((v >> i) & 1) != 0) data[b] |= (byte) (1 << (7 - (bit & 7)));
        bit++;
      }
    }
  }

  private static short[] resampleShort(short[] src, int srcSr, int dstSr) {
    if (srcSr == dstSr) return src;
    double ratio = srcSr / (double) dstSr;
    int n = Math.max(1, (int) Math.floor(src.length / ratio));
    short[] o = new short[n];
    for (int i = 0; i < n; i++) {
      int si = Math.min(src.length - 1, (int) Math.floor(i * ratio));
      o[i] = src[si];
    }
    return o;
  }

  private static float[] lp(float[] x, int sr, float hz) {
    float a = (float) (1 - Math.exp(-2 * Math.PI * hz / sr));
    float[] y = new float[x.length];
    float z = 0;
    for (int i = 0; i < x.length; i++) {
      z += a * (x[i] - z);
      y[i] = z;
    }
    return y;
  }

  private static void walk(byte[] d, int start, int end, List<int[]> out) {
    int i = start;
    int guard = 0;
    if (end > d.length) end = d.length;
    while (i + 8 <= end && guard++ < 4096) {
      long sizeL = u32(d, i + 4) & 0xffffffffL;
      int ds = i + 8;
      int remain = end - ds;
      if (remain < 0) break;
      if (sizeL > remain) sizeL = remain;
      int size = (int) sizeL;
      int de = ds + size;
      String id = four(d, i);
      out.add(new int[] { i, size });
      if ("LIST".equals(id) && de - ds >= 4) walk(d, ds + 4, de, out);
      int next = de + (size & 1);
      if (next <= i) break;
      i = next;
    }
  }

  private static byte[] ck(String id, byte[] data) {
    int pad = data.length & 1;
    byte[] out = new byte[8 + data.length + pad];
    putStr(out, 0, id, 4);
    put32(out, 4, data.length);
    System.arraycopy(data, 0, out, 8, data.length);
    return out;
  }

  private static byte[] list(String type, byte[][] parts) {
    byte[] t = new byte[4];
    putStr(t, 0, type, 4);
    byte[][] all = new byte[parts.length + 1][];
    all[0] = t;
    System.arraycopy(parts, 0, all, 1, parts.length);
    return ck("LIST", cat(all));
  }

  private static byte[] cat(byte[][] parts) {
    int n = 0;
    for (byte[] p : parts) n += p.length;
    byte[] out = new byte[n];
    int o = 0;
    for (byte[] p : parts) {
      System.arraycopy(p, 0, out, o, p.length);
      o += p.length;
    }
    return out;
  }

  private static String four(byte[] d, int i) {
    if (i + 4 > d.length) return "";
    return new String(d, i, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
  }

  private static String cstr(byte[] d, int i, int n) {
    StringBuilder sb = new StringBuilder();
    for (int k = 0; k < n && i + k < d.length; k++) {
      int c = d[i + k] & 0xff;
      if (c == 0) break;
      sb.append((char) c);
    }
    return sb.toString().trim();
  }

  private static int u16(byte[] d, int i) {
    return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8);
  }

  private static int u32(byte[] d, int i) {
    return (d[i] & 0xff) | ((d[i + 1] & 0xff) << 8) | ((d[i + 2] & 0xff) << 16) | ((d[i + 3] & 0xff) << 24);
  }

  private static void putStr(byte[] o, int i, String s, int n) {
    for (int k = 0; k < n; k++) o[i + k] = (byte) (k < s.length() ? s.charAt(k) : 0);
  }

  private static void put16(byte[] o, int i, int v) {
    o[i] = (byte) (v & 0xff);
    o[i + 1] = (byte) ((v >> 8) & 0xff);
  }

  private static void put32(byte[] o, int i, int v) {
    o[i] = (byte) (v & 0xff);
    o[i + 1] = (byte) ((v >> 8) & 0xff);
    o[i + 2] = (byte) ((v >> 16) & 0xff);
    o[i + 3] = (byte) ((v >> 24) & 0xff);
  }

  private static void write(ByteArrayOutputStream bos, String s) {
    byte[] b = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    bos.write(b, 0, b.length);
  }

  private static void writeLe(ByteArrayOutputStream bos, int v, int n) {
    for (int i = 0; i < n; i++) bos.write((v >> (8 * i)) & 0xff);
  }

  private AudioIo() {}
}
