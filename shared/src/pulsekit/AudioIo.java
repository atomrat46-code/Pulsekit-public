package pulsekit;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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

  public static final class Analysis {
    public int bpm = 120;
    public String styleId = "pop";
    public int[][] cells = Engine.emptyCells();
    public boolean fromGroove;
    public boolean isolated;
    public short[] kickSample;
    public short[] snareSample;
    public float[] pcm;
    public final List<PadIso> pads = new ArrayList<>();
    public final List<int[][]> bars = new ArrayList<>();
    public int[][] fillCells;
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

  public static final class PadIso {
    public String track;
    public String name;
    public int hz;
    public int lo;
    public int hi;
    public boolean found;
    public short[] sample;
    public boolean fromSample;
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

  public static int wavChannelCount(byte[] d) {
    if (d == null || d.length < 44) return 1;
    return Math.max(1, readWavHead(d).ch);
  }

  /** Like parseWav, but keeps left and right so a center vocal can be cancelled. */
  public static StereoPcm parseWavStereo(byte[] d) {
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
    float[] left = new float[n];
    float[] right = ch >= 2 ? new float[n] : null;
    for (int s = 0; s < n; s++) {
      int o = dataAt + s * frame;
      left[s] = wavSample(d, o, bits);
      if (right != null) right[s] = wavSample(d, o + bps, bits);
    }
    return new StereoPcm(left, right, sr);
  }

  private static float wavSample(byte[] d, int p, int bits) {
    int v;
    if (bits <= 8) v = ((d[p] & 0xff) - 128) << 8;
    else v = (short) ((d[p] & 0xff) | (d[p + 1] << 8));
    return v / 32768f;
  }

  /**
   * Best-effort guitar / vocal split. Stereo cancels a center vocal and keeps
   * wide guitar. Mono ducks the vocal band and keeps low body plus pick attacks.
   * `right` may be null. Result length matches `left`.
   * One output buffer — the phone path was dying with OutOfMemoryError when
   * every filter pole kept its own copy of a full-rate take.
   */
  public static float[] removeVocals(float[] left, float[] right, int sr) {
    int rate = sr > 0 ? sr : SR;
    if (left == null || left.length == 0) return new float[0];
    final float[] l = left;
    final float[] r = right;
    final int n = left.length;
    Reader mono = new Reader() {
      public int length() { return n; }
      public float at(int i) { return l[i]; }
    };
    boolean stereo = right != null && right.length >= n;
    if (!stereo) return removeMonoRead(mono, rate);
    Reader side = new Reader() {
      public int length() { return n; }
      public float at(int i) { return r[i]; }
    };
    return removeStereoOrMono(mono, side, rate);
  }

  /**
   * Strip a WAV without holding full-rate float channels. Works at 22050
   * so a long stereo take fits in a phone heap. Returned PCM is that rate.
   */
  public static Pcm stripVocalsWav(byte[] d) {
    if (d == null || d.length < 44) throw new IllegalArgumentException("Not a WAV file");
    final WavHead h = readWavHead(d);
    if (h.dataAt < 0 || h.frames <= 0) throw new IllegalArgumentException("WAV has no audio data");
    final int workSr = h.sr > SR ? SR : h.sr;
    final int n = workSr == h.sr ? h.frames : Math.max(1, (int) Math.round(h.frames * (workSr / (double) h.sr)));
    final byte[] bytes = d;
    Reader left = new Reader() {
      public int length() { return n; }
      public float at(int i) { return wavAt(h, bytes, 0, i, workSr); }
    };
    if (h.ch < 2) return new Pcm(removeMonoRead(left, workSr), workSr);
    Reader right = new Reader() {
      public int length() { return n; }
      public float at(int i) { return wavAt(h, bytes, 1, i, workSr); }
    };
    return new Pcm(removeStereoOrMono(left, right, workSr), workSr);
  }

  public static short[] floatsToShorts(float[] x) {
    if (x == null) return new short[0];
    short[] o = new short[x.length];
    for (int i = 0; i < x.length; i++) {
      o[i] = (short) Math.max(-32767, Math.min(32767, Math.round(x[i] * 32767)));
    }
    return o;
  }

  public static float[] shortsToFloat(short[] x) {
    if (x == null) return new float[0];
    float[] o = new float[x.length];
    for (int i = 0; i < x.length; i++) o[i] = x[i] / 32768f;
    return o;
  }

  public static float[] resample(float[] in, int fromSr, int toSr) {
    if (in == null || in.length == 0) return new float[0];
    if (fromSr <= 0 || toSr <= 0 || fromSr == toSr) return in;
    int n = Math.max(1, (int) Math.round(in.length * ((double) toSr / fromSr)));
    float[] out = new float[n];
    double ratio = (double) fromSr / toSr;
    int last = in.length - 1;
    int span = ratio > 1.25 ? Math.max(2, (int) Math.round(ratio)) : 1;
    for (int i = 0; i < n; i++) {
      double x = i * ratio;
      int i0 = (int) Math.floor(x);
      if (i0 > last) i0 = last;
      if (span == 1) {
        int i1 = Math.min(last, i0 + 1);
        float f = (float) (x - i0);
        out[i] = in[i0] + (in[i1] - in[i0]) * f;
      } else {
        int a0 = Math.max(0, i0 - span / 2);
        int a1 = Math.min(last, a0 + span - 1);
        float s = 0f;
        int cnt = 0;
        for (int k = a0; k <= a1; k++) {
          s += in[k];
          cnt++;
        }
        out[i] = cnt > 0 ? s / cnt : in[i0];
      }
    }
    return out;
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

  /**
   * Drums locked to the guitar. Steady 16ths inside each guitar part, from its
   * start to its end, so the drums stay with that stretch of the file.
   */
  public static short[] mixSongOnFile(java.util.List<Engine.Part> parts, short[][] voices, int sr, float fileSec) {
    return mixSongOnFile(parts, null, null, voices, sr, fileSec);
  }

  public static short[] mixSongOnFile(
      java.util.List<Engine.Part> parts,
      java.util.List<Float> sectionSecs,
      java.util.List<Engine.FileSetPart> fileParts,
      short[][] voices,
      int sr,
      float fileSec) {
    float natural = Engine.songDurationSec(parts);
    float target = fileSec > 0.05f ? fileSec : natural;
    int rate = Math.max(8000, sr);
    int total = Math.max(1, Math.round(target * rate));
    int[] acc = new int[total];
    if (parts == null || parts.isEmpty() || !(natural > 0f)) return new short[total];
    boolean split = fileParts != null && sectionSecs != null
        && !fileParts.isEmpty() && fileParts.size() == sectionSecs.size();
    if (!split) {
      placeSteady(parts, 0, parts.size(), 0f, target, acc, voices, rate, total);
    } else {
      int songAt = 0;
      for (int i = 0; i < fileParts.size(); i++) {
        float need = Math.max(0f, sectionSecs.get(i).floatValue());
        Engine.FileSetPart info = fileParts.get(i);
        float start = i == 0 ? 0f : Math.max(0f, info.startSec);
        float end = i == fileParts.size() - 1 ? target : info.endSec;
        if (end < start) end = start;
        int from = songAt;
        float got = 0f;
        while (songAt < parts.size() && got < need - 0.0005f) {
          float d = partSpan(parts.get(songAt));
          if (got > 0f && got + d > need + 0.05f) break;
          got += d;
          songAt++;
        }
        if (i == fileParts.size() - 1) songAt = parts.size();
        if (end > start + 0.001f && songAt > from) {
          placeSteady(parts, from, songAt, start, end, acc, voices, rate, total);
        }
      }
    }
    short[] pcm = new short[total];
    for (int i = 0; i < total; i++) pcm[i] = (short) Math.max(-32767, Math.min(32767, acc[i]));
    return pcm;
  }

  private static float partSpan(Engine.Part p) {
    if (p == null) return 0f;
    int steps = Engine.partStepCount(p);
    int bpm = p.bpm > 0 ? p.bpm : 120;
    return Math.max(1, p.repeats) * steps * (60f / Math.max(40, bpm) / 4f);
  }

  private static void placeSteady(
      java.util.List<Engine.Part> parts,
      int from,
      int to,
      float start,
      float end,
      int[] acc,
      short[][] voices,
      int sr,
      int total) {
    if (end <= start || from >= to) return;
    int nSteps = 0;
    for (int i = from; i < to && i < parts.size(); i++) {
      Engine.Part p = parts.get(i);
      if (p == null) continue;
      int steps = Math.max(1, Math.min(Engine.MAX_STEPS, p.steps > 0 ? p.steps : Engine.STEPS));
      nSteps += Math.max(1, p.repeats) * steps;
    }
    if (nSteps <= 0) return;
    float stepSec = (end - start) / nSteps;
    float cursor = start;
    for (int i = from; i < to && i < parts.size(); i++) {
      Engine.Part p = parts.get(i);
      if (p == null) continue;
      int steps = Math.max(1, Math.min(Engine.MAX_STEPS, p.steps > 0 ? p.steps : Engine.STEPS));
      int times = Math.max(1, p.repeats);
      int[][] cells = p.cells;
      for (int r = 0; r < times; r++) {
        for (int s = 0; s < steps; s++) {
          int at = Math.round(cursor * sr);
          if (at < 0) at = 0;
          if (at < total) {
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
              int vel = cells != null && t < cells.length && cells[t] != null && s < cells[t].length ? cells[t][s] : 0;
              if (vel <= 0) continue;
              short[] srcv = voices != null && t < voices.length ? voices[t] : null;
              if (srcv == null) continue;
              double g = vel / 127.0;
              for (int k = 0; k < srcv.length && at + k < total; k++) acc[at + k] += (int) (srcv[k] * g);
            }
          }
          cursor += stepSec;
        }
      }
    }
  }

  public static final class MixOut {
    public short[] mix = new short[0];
    public short[] drums = new short[0];
    public short[] guitar = new short[0];
  }

  public static short[] combineTracks(short[] drums, int drumSr, float[] guitar, int guitarSr) {
    return combineMix(drums, drumSr, guitar, guitarSr, null, 1f, 1f).mix;
  }

  /** Guitar stays in front of the drums. Length is the guitar file. Vocals are stripped here. */
  public static short[] combineTracks(short[] drums, int drumSr, float[] guitar, int guitarSr, double[] beats) {
    return combineMix(drums, drumSr, guitar, guitarSr, beats, 1f, 1f).mix;
  }

  /**
   * Mix, plus the drum and guitar stems at the automatic level.
   * {@code drumLevel} and {@code guitarLevel} scale those stems (1 = as combined).
   */
  public static MixOut combineMix(short[] drums, int drumSr, float[] guitar, int guitarSr, double[] beats, float drumLevel, float guitarLevel) {
    int sr = 22050;
    float[] d = resample(shortsToFloat(drums), drumSr > 0 ? drumSr : sr, sr);
    float[] raw = resample(guitar == null ? new float[0] : guitar, guitarSr > 0 ? guitarSr : sr, sr);
    float[] g = mixGuitarBed(raw, sr, beats);
    addPresence(g, sr);
    compressGuitar(g, sr);
    peakNormalize(g, 0.94f);
    float dPeak = 1e-6f;
    for (int i = 0; i < d.length; i++) dPeak = Math.max(dPeak, Math.abs(d[i]));
    float dR = pcmRms(d);
    float gR = pcmRms(g);
    float dGain = dR > 1e-8f ? Math.min((gR * 0.22f) / dR, 0.28f / dPeak) : 0f;
    for (int i = 0; i < d.length; i++) d[i] *= dGain;
    limitPeaks(d, sr, 0.28f);
    int n = Math.max(g.length, 1);
    float gMul = Float.isFinite(guitarLevel) ? Math.max(0f, Math.min(1.5f, guitarLevel)) : 1f;
    float dMul = Float.isFinite(drumLevel) ? Math.max(0f, Math.min(1.5f, drumLevel)) : 1f;
    short[] gStem = floatsToShorts(g);
    short[] dStem = new short[Math.max(d.length, 1)];
    for (int i = 0; i < dStem.length; i++) {
      float dv = i < d.length ? d[i] : 0f;
      dStem[i] = (short) Math.max(-32767, Math.min(32767, Math.round(dv * 32767f)));
    }
    short[] shaped = shapeGuitar(gStem, gMul * 3.2f, 0.08f, 0.4f, 0.12f);
    float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      float gv = i < shaped.length ? shaped[i] / 32768f : 0f;
      float dv = i < dStem.length ? dStem[i] / 32768f : 0f;
      out[i] = gv + dv * dMul;
    }
    limitPeaks(out, sr, 0.9f);
    MixOut mix = new MixOut();
    mix.mix = floatsToShorts(out);
    mix.drums = dStem;
    mix.guitar = shaped;
    return mix;
  }

  /**
   * Guitar dynamics. {@code volume} is 1 at the stored stem.
   * {@code gate}, {@code comp} and {@code limit} are 0–1. The limiter always
   * holds the peaks, so a hot strum is turned down instead of clipped.
   */
  public static short[] shapeGuitar(short[] x, float volume, float gate, float comp, float limit) {
    float[] s = shortsToFloat(x);
    float vol = Float.isFinite(volume) ? Math.max(0f, Math.min(5f, volume)) : 1f;
    for (int i = 0; i < s.length; i++) s[i] *= vol;
    guitarGate(s, 22050, clamp01(gate));
    guitarComp(s, 22050, clamp01(comp));
    float amount = clamp01(limit);
    float peakTarget = (0.96f - amount * 0.2f) * Math.min(1.2f, Math.max(0.02f, vol / 2.6f));
    if (peakTarget > 0.97f) peakTarget = 0.97f;
    scaleToPeak(s, peakTarget);
    limitPeaks(s, 22050, 0.97f);
    return floatsToShorts(s);
  }

  /** Turn a stem up, then hold the peak so it does not flat-top. */
  public static short[] boostLimited(short[] x, float gain, float ceil) {
    float[] s = shortsToFloat(x);
    float g = Float.isFinite(gain) ? Math.max(0f, Math.min(5f, gain)) : 1f;
    for (int i = 0; i < s.length; i++) s[i] *= g;
    float c = Float.isFinite(ceil) ? Math.max(0.3f, Math.min(0.98f, ceil)) : 0.9f;
    float peakTarget = Math.min(c, 0.5f * Math.min(1.8f, Math.max(0.05f, g)));
    scaleToPeak(s, peakTarget);
    limitPeaks(s, 22050, c);
    return floatsToShorts(s);
  }

  /** Scale so the loudest sample sits at {@code peak}. Does not clip the wave flat. */
  private static void scaleToPeak(float[] x, float peak) {
    if (x == null || x.length == 0 || !(peak > 0f)) return;
    float m = 0f;
    for (int i = 0; i < x.length; i++) m = Math.max(m, Math.abs(x[i]));
    if (!(m > 1e-8f)) return;
    float g = peak / m;
    if (g > 24f) g = 24f;
    for (int i = 0; i < x.length; i++) x[i] *= g;
  }

  private static float clamp01(float v) {
    if (!Float.isFinite(v)) return 0f;
    if (v < 0f) return 0f;
    if (v > 1f) return 1f;
    return v;
  }

  private static void peakNormalize(float[] x, float peak) {
    if (x == null || x.length == 0) return;
    float m = 0f;
    for (int i = 0; i < x.length; i++) m = Math.max(m, Math.abs(x[i]));
    if (!(m > 1e-8f)) return;
    float g = peak / m;
    for (int i = 0; i < x.length; i++) x[i] *= g;
  }

  private static void guitarGate(float[] x, int sr, float amount) {
    if (x == null || x.length < 32 || amount < 0.02f) return;
    int step = Math.max(1, x.length / 5000);
    int n = (x.length + step - 1) / step;
    float[] probe = new float[n];
    float e = 0f;
    float atk = (float) Math.exp(-1.0 / (Math.max(1, sr) * 0.004));
    float rel = (float) Math.exp(-1.0 / (Math.max(1, sr) * (0.08 + 0.22 * (1f - amount))));
    int k = 0;
    for (int i = 0; i < x.length; i++) {
      float a = Math.abs(x[i]);
      e = a > e ? a + (e - a) * atk : a + (e - a) * rel;
      if (i % step == 0 && k < n) probe[k++] = e;
    }
    if (k < 4) return;
    java.util.Arrays.sort(probe, 0, k);
    int idx = Math.round((0.2f + amount * 0.62f) * (k - 1));
    if (idx < 0) idx = 0;
    if (idx >= k) idx = k - 1;
    float thresh = probe[idx];
    if (!(thresh > 1e-6f)) return;
    float closed = (1f - amount) * (1f - amount);
    e = 0f;
    float g = 1f;
    float gSmooth = (float) Math.exp(-1.0 / (Math.max(1, sr) * 0.008));
    for (int i = 0; i < x.length; i++) {
      float a = Math.abs(x[i]);
      e = a > e ? a + (e - a) * atk : a + (e - a) * rel;
      float want = e >= thresh ? 1f : closed + (1f - closed) * (e / thresh);
      g = want > g ? want + (g - want) * gSmooth : want;
      x[i] *= g;
    }
  }

  private static void guitarComp(float[] x, int sr, float amount) {
    if (x == null || x.length < 64 || amount < 0.02f) return;
    int rate = Math.max(1, sr);
    float atk = (float) Math.exp(-1.0 / (rate * 0.012));
    float rel = (float) Math.exp(-1.0 / (rate * (0.08 + 0.2 * (1f - amount))));
    int step = Math.max(1, x.length / 5000);
    int n = (x.length + step - 1) / step;
    float[] probe = new float[n];
    float e = 0f;
    int k = 0;
    for (int i = 0; i < x.length; i++) {
      float a = Math.abs(x[i]);
      e = a > e ? a + (e - a) * atk : a + (e - a) * rel;
      if (i % step == 0 && k < n) probe[k++] = e;
    }
    if (k < 4) return;
    java.util.Arrays.sort(probe, 0, k);
    int idx = Math.round((0.42f - amount * 0.22f) * (k - 1));
    if (idx < 0) idx = 0;
    if (idx >= k) idx = k - 1;
    float steer = probe[idx];
    if (!(steer > 1e-6f)) return;
    float gr = 1f - 1f / (1f + amount * 6f);
    e = 0f;
    float g = 1f;
    float gSmooth = (float) Math.exp(-1.0 / (rate * 0.02));
    for (int i = 0; i < x.length; i++) {
      float a = Math.abs(x[i]);
      e = a > e ? a + (e - a) * atk : a + (e - a) * rel;
      float level = e < steer * 0.15f ? steer * 0.15f : e;
      float want = (float) Math.pow(steer / level, gr);
      if (want > 2.2f) want = 2.2f;
      if (want < 0.45f) want = 0.45f;
      g = want + (g - want) * gSmooth;
      x[i] *= g;
    }
  }

  /** Playback copy louder than the stem so a slider can go above the saved mix. */
  public static short[] gainShorts(short[] x, float g) {
    if (x == null) return new short[0];
    short[] o = new short[x.length];
    float mul = Float.isFinite(g) ? g : 1f;
    for (int i = 0; i < x.length; i++) {
      float v = (x[i] / 32768f) * mul;
      o[i] = (short) Math.max(-32767, Math.min(32767, Math.round(v * 32767f)));
    }
    return o;
  }

  /** Rebalance stored drum and guitar stems. Levels are 1 at the automatic mix. */
  public static short[] remixStems(short[] drums, short[] guitar, float drumLevel, float guitarLevel) {
    int n = Math.max(drums == null ? 0 : drums.length, guitar == null ? 0 : guitar.length);
    float[] out = new float[Math.max(1, n)];
    float dMul = Float.isFinite(drumLevel) ? Math.max(0f, Math.min(1.5f, drumLevel)) : 1f;
    float gMul = Float.isFinite(guitarLevel) ? Math.max(0f, Math.min(1.5f, guitarLevel)) : 1f;
    for (int i = 0; i < out.length; i++) {
      float dv = drums != null && i < drums.length ? drums[i] / 32768f : 0f;
      float gv = guitar != null && i < guitar.length ? guitar[i] / 32768f : 0f;
      out[i] = dv * dMul + gv * gMul;
    }
    limitPeaks(out, 22050, 0.86f);
    return floatsToShorts(out);
  }

  public static float[] mixGuitarBed(float[] x, int sr) {
    return mixGuitarBed(x, sr, null);
  }

  public static float[] mixGuitarBed(float[] x, int sr, double[] beats) {
    if (x == null || x.length < 32) return x == null ? new float[0] : x;
    final int rate = sr > 0 ? sr : SR;
    float[] body = lpN(x, rate, 130f, 4);
    float[] pick = hpN(x, rate, 3800f, 2);
    float[] tick = shortTick(pick, rate);
    float[] pulse = hp(lpN(x, rate, 320f, 3), rate, 50f);
    strumGateInto(pulse, rate);
    float[] warm = hpN(lpN(x, rate, 920f, 2), rate, 500f, 2);
    float[] air = hpN(x, rate, 3600f, 2);
    float[] out = new float[x.length];
    for (int i = 0; i < x.length; i++) out[i] = body[i] * 3.4f + pick[i] * 0.55f * tick[i];
    float[] sung = hpN(lpN(out, rate, 3200f, 2), rate, 230f, 2);
    for (int i = 0; i < out.length; i++) {
      float g = pulse[i];
      out[i] = out[i] - sung[i] + warm[i] * 2.2f * g + air[i] * 0.22f * g;
    }
    return out;
  }

  /** 1 after a fast decaying attack, then a short ring. A held vowel never opens it. */
  private static float[] sharpStrumGate(float[] x, int sr) {
    int rate = Math.max(1, sr);
    int hop = Math.max(1, rate / 200);
    int n = x.length;
    float[] gate = new float[n];
    float release = (float) Math.exp(-1.0 / (rate * 0.55));
    float held = 0f;
    int win = Math.max(8, (int) (rate * 0.008f));
    int prev = 0;
    double prevE = 0;
    for (int i = 0; i < n; i++) {
      if (i % hop == 0) {
        int a = Math.max(0, i - win);
        double e = 0;
        int c = 0;
        for (int k = a; k < i && k < n; k++) {
          e += (double) x[k] * x[k];
          c++;
        }
        e = c > 0 ? Math.sqrt(e / c) : 0;
        double later = 0;
        int b = Math.min(n, i + win * 5);
        int c2 = 0;
        for (int k = i; k < b; k += Math.max(1, win / 2)) {
          later += (double) x[k] * x[k];
          c2++;
        }
        later = c2 > 0 ? Math.sqrt(later / c2) : 0;
        boolean sharp = prev > 0 && e > prevE * 2.2 && e > 0.015 && e > later * 1.02;
        if (sharp) held = 1f;
        prevE = e;
        prev++;
      }
      gate[i] = held;
      held *= release;
    }
    int head = Math.min(n, (int) (rate * 0.03f));
    int tail = Math.min(n, head + (int) (rate * 0.05f));
    double h = 0;
    double t = 0;
    for (int i = 0; i < head; i++) h += (double) x[i] * x[i];
    for (int i = head; i < tail; i++) t += (double) x[i] * x[i];
    h = Math.sqrt(h / Math.max(1, head));
    t = Math.sqrt(t / Math.max(1, tail - head));
    if (h > 0.04 && h > t * 1.35) {
      for (int i = 0; i < n && gate[i] < 0.99f; i++) {
        float fade = (float) Math.exp(-i / (rate * 0.55));
        if (fade > gate[i]) gate[i] = fade;
        else break;
      }
    }
    return gate;
  }

  /** Add the string band back so a phone hears the guitar, not only the sub peak. */
  private static void addPresence(float[] x, int sr) {
    if (x == null || x.length < 32) return;
    int rate = sr > 0 ? sr : SR;
    float[] aud = hpN(lpN(x, rate, 2400f, 2), rate, 280f, 2);
    for (int i = 0; i < x.length; i++) x[i] += aud[i] * 1.8f;
  }

  /** 1 on a guitar beat, falling off before the next vocal tail. No beats means fully open. */
  private static float beatGainFrom(double[] beats, int start, float t) {
    if (beats == null || beats.length < 3) return 1f;
    float g = 0f;
    for (int i = Math.max(0, start); i < beats.length; i++) {
      float d = t - (float) beats[i];
      if (d < -0.05f) break;
      float w = 0f;
      if (d >= -0.04f && d <= 0.28f) w = 1f;
      else if (d > 0.28f && d < 0.42f) w = 1f - (d - 0.28f) / 0.14f;
      if (w > g) g = w;
    }
    return g;
  }

  /** A few milliseconds of the pick. A sung note has no tick, so it stays out. */
  private static float[] shortTick(float[] x, int sr) {
    int hop = 128;
    float[] env = frameRms(x, hop, 512);
    float[] fl = flux(env);
    float[] mask = new float[x.length];
    float release = (float) Math.exp(-1.0 / (Math.max(1, sr) * 0.008));
    float held = 0f;
    int fi = 0;
    for (int i = 0; i < x.length; i++) {
      if (i % hop == 0 && fi < fl.length) {
        float level = env[fi];
        if (fi > 0 && env[fi - 1] > level) level = env[fi - 1];
        if (level < 1e-4f) level = 1e-4f;
        float rel = fl[fi] / level;
        float kick = rel > 1.05f ? Math.min(1f, (rel - 1.05f) / 0.45f) : 0f;
        if (kick > held) held = kick;
        fi++;
      }
      mask[i] = held;
      held *= release;
    }
    smoothRises(mask, sr, 0.004f);
    return mask;
  }

  /** Opens on a low-body strum, including one that starts the file. Writes the gate over {@code x}. */
  private static void strumGateInto(float[] x, int sr) {
    int hop = 128;
    float[] env = frameRms(x, hop, 512);
    float[] fl = flux(env);
    float release = (float) Math.exp(-1.0 / (Math.max(1, sr) * 0.28));
    float held = 0f;
    int headN = Math.min(x.length, (int) (sr * 0.06f));
    double headE = 0;
    for (int i = 0; i < headN; i++) headE += (double) x[i] * x[i];
    headE = Math.sqrt(headE / Math.max(1, headN));
    if (headE > 0.02) held = 1f;
    int fi = 0;
    for (int i = 0; i < x.length; i++) {
      if (i % hop == 0 && fi < fl.length) {
        float level = env[fi];
        if (fi > 0 && env[fi - 1] > level) level = env[fi - 1];
        if (level < 1e-4f) level = 1e-4f;
        float rel = fl[fi] / level;
        float kick = rel > 0.18f ? Math.min(1f, (rel - 0.18f) / 0.32f) : 0f;
        if (kick > held) held = kick;
        fi++;
      }
      x[i] = held;
      held *= release;
    }
    int lead = (int) (sr * 0.03f);
    if (lead > 0) {
      float[] src = java.util.Arrays.copyOf(x, x.length);
      for (int i = 0; i < x.length; i++) {
        float ahead = i + lead < src.length ? src[i + lead] : 0f;
        x[i] = Math.max(src[i], ahead);
      }
    }
    smoothRises(x, sr, 0.006f);
  }

  /** A hard gate step clicks. Rises ease in; the release envelope is left alone. */
  private static void smoothRises(float[] mask, int sr, float seconds) {
    float c = (float) Math.exp(-1.0 / (Math.max(1, sr) * Math.max(0.001f, seconds)));
    float sm = 0f;
    for (int i = 0; i < mask.length; i++) {
      float t = mask[i];
      sm = t > sm ? t + (sm - t) * c : t;
      mask[i] = sm;
    }
  }

  /** Hold peaks at {@code thresh}. Gain drops on the crest and returns slowly, so the wave is not bent or flat-topped. */
  private static void limitPeaks(float[] x, int sr, float thresh) {
    if (x == null || x.length == 0) return;
    float rel = (float) Math.exp(-1.0 / (Math.max(1, sr) * 0.4));
    float env = 0f;
    float g = 1f;
    for (int i = 0; i < x.length; i++) {
      float v = x[i];
      float a = Math.abs(v);
      env = a > env ? a : env * rel;
      float want = env > thresh ? thresh / env : 1f;
      g = want < g ? want : want + (g - want) * rel;
      x[i] = v * g;
    }
  }

  /** Pull loud strums down and lift quiet ones. The envelope is slow, so the wave is not bent. */
  private static void compressGuitar(float[] x, int sr) {
    if (x == null || x.length < 64) return;
    int rate = Math.max(1, sr);
    float atk = (float) Math.exp(-1.0 / (rate * 0.055));
    float rel = (float) Math.exp(-1.0 / (rate * 0.1));
    int step = Math.max(1, x.length / 6000);
    int n = (x.length + step - 1) / step;
    float[] probe = new float[n];
    float e = 0f;
    int k = 0;
    for (int i = 0; i < x.length; i++) {
      float a = Math.abs(x[i]);
      float c = a > e ? atk : rel;
      e = a + (e - a) * c;
      if (i % step == 0 && k < n) probe[k++] = e;
    }
    if (k <= 0) return;
    java.util.Arrays.sort(probe, 0, k);
    int idx = Math.round(0.32f * (k - 1));
    if (idx < 0) idx = 0;
    if (idx >= k) idx = k - 1;
    float steer = probe[idx];
    if (!(steer > 1e-5f)) return;
    float floor = steer * 0.06f;
    e = 0f;
    float g = 1f;
    float gSmooth = (float) Math.exp(-1.0 / (rate * 0.05));
    for (int i = 0; i < x.length; i++) {
      float a = Math.abs(x[i]);
      float c = a > e ? atk : rel;
      e = a + (e - a) * c;
      float level = e < floor ? floor : e;
      float want = steer / level;
      if (want > 2.2f) want = 2.2f;
      if (want < 0.62f) want = 0.62f;
      g = want + (g - want) * gSmooth;
      x[i] *= g;
    }
  }

  private static float absPercentile(float[] x, float p) {
    if (x == null || x.length == 0) return 0f;
    int step = Math.max(1, x.length / 8000);
    int n = (x.length + step - 1) / step;
    float[] v = new float[n];
    int k = 0;
    for (int i = 0; i < x.length && k < n; i += step) v[k++] = Math.abs(x[i]);
    if (k <= 0) return 0f;
    java.util.Arrays.sort(v, 0, k);
    int idx = Math.round(p * (k - 1));
    if (idx < 0) idx = 0;
    if (idx >= k) idx = k - 1;
    return v[idx];
  }

  public static final class GuitarSource {
    public final float[] samples;
    public final boolean cleaned;
    public GuitarSource(float[] samples, boolean cleaned) {
      this.samples = samples;
      this.cleaned = cleaned;
    }
  }

  /** Always hand back a vocal-reduced guitar. `cleaned` is true when the vocal band actually dropped. */
  public static GuitarSource guitarMixSource(float[] samples, int sr) {
    int rate = sr > 0 ? sr : SR;
    if (samples == null || samples.length < 32) return new GuitarSource(samples, false);
    float[] cleaned = removeVocals(samples, null, rate);
    if (cleaned == null || cleaned.length != samples.length) return new GuitarSource(samples, false);
    float vocO = bandRms(samples, rate, 400f, 3400f);
    float vocC = bandRms(cleaned, rate, 400f, 3400f);
    boolean dropped = vocO > 1e-4f && vocC < vocO * 0.75f;
    return new GuitarSource(cleaned, dropped);
  }

  private static float bandRms(float[] x, int sr, float lo, float hi) {
    float[] low = lp(x, sr, hi);
    float[] body = lp(low, sr, lo);
    double s = 0;
    for (int i = 0; i < low.length; i++) {
      float v = low[i] - body[i];
      s += (double) v * v;
    }
    return (float) Math.sqrt(s / Math.max(1, low.length));
  }

  private static double[] toDoubles(java.util.ArrayList<Double> xs) {
    double[] out = new double[xs.size()];
    for (int i = 0; i < xs.size(); i++) out[i] = xs.get(i).doubleValue();
    return out;
  }

  /** Guitar clock: BPM, meter, swing, and quarter-note times locked to the guitar. */
  public static final class GuitarClock {
    public int bpm = 120;
    public int tsNum = 4;
    public int tsDen = 4;
    public int swing;
    public double[] beats = new double[0];
    public double[] onsets = new double[0];
    /** Beat index of the bar downbeat. */
    public int bar0;
    public double fileSec;
    /** Guitar the mix should use. May be vocal-reduced. */
    public float[] mix;
    public boolean cleaned;
  }

  public static double tempoLock(int styleBpm, int guitarBpm) {
    double g = Math.max(40, guitarBpm > 0 ? guitarBpm : 120);
    double s = Math.max(40, styleBpm > 0 ? styleBpm : g);
    double best = 1;
    double bestD = Double.POSITIVE_INFINITY;
    for (double m : new double[] { 0.5, 1, 2 }) {
      double d = Math.abs(Math.log((g * m) / s));
      if (d < bestD - 1e-9) {
        bestD = d;
        best = m;
      }
    }
    return best;
  }

  /** Write the guitar's BPM, meter, and swing onto the file set. Timing humanize is off. */
  public static GuitarClock stampGuitarFeel(Engine.FileSet set, java.util.List<Engine.FileSetPart> parts, float[] guitar, int guitarSr) {
    GuitarClock clock = readGuitarClock(guitar, guitarSr);
    int styleBpm = 120;
    if (parts != null && !parts.isEmpty() && parts.get(0) != null && parts.get(0).bpm > 0) styleBpm = parts.get(0).bpm;
    else if (set != null && set.patterns != null && !set.patterns.isEmpty()) styleBpm = set.patterns.get(0).bpm;
    double rate = tempoLock(styleBpm, clock.bpm);
    int bpm = Engine.clampBpm((int) Math.round(clock.bpm * rate));
    if (parts != null) {
      for (Engine.FileSetPart p : parts) {
        if (p == null) continue;
        p.bpm = bpm;
        p.tsNum = clock.tsNum;
        p.tsDen = clock.tsDen;
        p.swing = clock.swing;
      }
    }
    if (set != null && set.patterns != null) {
      for (Engine.Learned p : set.patterns) {
        if (p == null) continue;
        p.bpm = bpm;
        p.tsNum = clock.tsNum;
        p.tsDen = clock.tsDen;
        p.swing = clock.swing;
        p.human = 0;
      }
    }
    return clock;
  }

  /** Place the file-set drums on the guitar's beat grid. No stretch, no random timing. */
  public static short[] mixFileSetOnGuitar(
      Engine.FileSet set,
      java.util.List<Engine.FileSetPart> parts,
      float[] guitar,
      int guitarSr,
      short[][] voices) {
    voices = voicesOrBuilt(voices);
    GuitarClock clock = readGuitarClock(guitar, guitarSr);
    int styleBpm = clock.bpm;
    if (parts != null && !parts.isEmpty() && parts.get(0) != null && parts.get(0).bpm > 0) styleBpm = parts.get(0).bpm;
    double rate = tempoLock(styleBpm, clock.bpm);
    int rateSr = 22050;
    int total = Math.max(1, (int) Math.round(Math.max(0.01, clock.fileSec) * rateSr));
    int[] acc = new int[total];
    if (set == null || parts == null || parts.isEmpty()) return new short[total];
    double[] times = clockSteps(clock, rate);
    int down = clockDownIndex(clock, times);
    int loop = Math.max(4, (int) Math.round(Math.max(1, clock.tsNum) * (16.0 / Math.max(1, clock.tsDen)) * rate));
    for (int pi = 0; pi < parts.size(); pi++) {
      Engine.FileSetPart part = parts.get(pi);
      if (part == null) continue;
      double end = pi == parts.size() - 1 ? clock.fileSec + 1 : part.endSec;
      java.util.ArrayList<Integer> idxs = new java.util.ArrayList<Integer>();
      for (int i = 0; i < times.length; i++) {
        if (times[i] >= part.startSec - 0.0005 && times[i] < end - 0.0005) idxs.add(Integer.valueOf(i));
      }
      if (idxs.isEmpty()) continue;
      String kind = part.kind == null ? "groove" : part.kind.toLowerCase();
      boolean silent = ("break".equals(kind) || "rest".equals(kind)) && part.hits < 2;
      Engine.Learned learned = namedPattern(set, part.name);
      if (learned == null && pi < set.patterns.size()) learned = set.patterns.get(pi);
      if (learned == null && !set.patterns.isEmpty()) learned = set.patterns.get(0);
      int[][] groove = learned == null ? null : learned.cells;
      String nextKind = pi + 1 < parts.size() && parts.get(pi + 1) != null && parts.get(pi + 1).kind != null
          ? parts.get(pi + 1).kind.toLowerCase() : "";
      int[][] fill = null;
      if (!"fill".equals(nextKind) && learned != null) fill = namedFill(set, fillernName(set, learned.name));
      if ("fill".equals(kind)) {
        int[][] asFill = namedFill(set, part.name);
        if (asFill != null) groove = asFill;
        fill = null;
      }
      int fillFrom = Integer.MAX_VALUE;
      if (!silent && fill != null && !"fill".equals(kind)) {
        for (int k = idxs.size() - 1; k >= 0; k--) {
          int ix = idxs.get(k).intValue();
          if (mod(ix - down, loop) == 0) {
            fillFrom = ix;
            break;
          }
        }
      }
      for (int n = 0; n < idxs.size(); n++) {
        int i = idxs.get(n).intValue();
        if (silent) continue;
        int step = mod(i - down, loop);
        int[][] use = i >= fillFrom && fill != null ? fill : groove;
        if (use == null) continue;
        int at = (int) Math.round(times[i] * rateSr);
        if (at < 0 || at >= total) continue;
        for (int t = 0; t < Engine.TRACK_ID.length && t < use.length; t++) {
          int[] row = use[t];
          if (row == null || row.length == 0) continue;
          int cell = step < row.length ? step : step % row.length;
          int vel = row[cell];
          if (vel <= 0) continue;
          if (step % 4 == 0) vel = Math.max(vel, 110);
          else vel = Math.max(1, (int) Math.round(vel * 0.58));
          short[] srcv = voices != null && t < voices.length ? voices[t] : null;
          if (srcv == null) continue;
          double g = vel / 127.0;
          for (int k = 0; k < srcv.length && at + k < total; k++) acc[at + k] += (int) (srcv[k] * g);
        }
      }
    }
    short[] pcm = new short[total];
    for (int i = 0; i < total; i++) pcm[i] = (short) Math.max(-32767, Math.min(32767, acc[i]));
    return pcm;
  }

  public static final class GuitarFit {
    public float[] mix;
    public boolean cleaned;
    public int bpm = 120;
    public int tsNum = 4;
    public int tsDen = 4;
    public int swing;
    public double fileSec;
    public double[] beats = new double[0];
    public int[][] cells;
    public double[] time = new double[0];
    public int[] track = new int[0];
    public int[] vel = new int[0];
  }

  private static boolean onSubdivision(double onset, double[] beats, double period) {
    if (beats == null || beats.length == 0) return false;
    double beat = beats[0];
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i < beats.length; i++) {
      double d = Math.abs(beats[i] - onset);
      if (d < best) {
        best = d;
        beat = beats[i];
      }
    }
    double rel = onset - beat;
    double[] spots = new double[] { 0.5, 1.0 / 3.0, 2.0 / 3.0, -0.5, -1.0 / 3.0, -2.0 / 3.0 };
    for (int i = 0; i < spots.length; i++) {
      if (Math.abs(rel - period * spots[i]) < 0.04) return true;
    }
    return false;
  }

  private static boolean isBackbeat(int beat, int ts) {
    if (ts == 3) return beat == 2;
    if (ts == 2) return beat == 1;
    return beat % 2 == 1;
  }

  private static boolean silentPart(java.util.List<Engine.FileSetPart> parts, double t) {
    if (parts == null) return false;
    for (int i = 0; i < parts.size(); i++) {
      Engine.FileSetPart p = parts.get(i);
      if (p == null) continue;
      String kind = p.kind == null ? "" : p.kind.toLowerCase();
      if (("break".equals(kind) || "rest".equals(kind)) && p.hits < 2 && t >= p.startSec - 0.0005 && t < p.endSec - 0.0005) return true;
    }
    return false;
  }

  private static void addHit(
      java.util.ArrayList<Double> times,
      java.util.ArrayList<Integer> tracks,
      java.util.ArrayList<Integer> vels,
      double t,
      int track,
      int vel) {
    times.add(Double.valueOf(Math.max(0, t)));
    tracks.add(Integer.valueOf(track));
    vels.add(Integer.valueOf(vel));
  }

  /**
   * Strums that have both a low guitar body and a bright pick.
   * A sung note has no pick, so it does not move the drums.
   * The time is the start of the attack, not the middle of the analysis window.
   */
  private static double[] guitarStrums(float[] x, int sr) {
    if (x == null || x.length < 128) return new double[0];
    int rate = sr > 0 ? sr : SR;
    float[] low = hp(lpN(x, rate, 280f, 2), rate, 55f);
    float[] high = hpN(x, rate, 2200f, 2);
    int hop = 64;
    float[] le = frameRms(low, hop, 128);
    float[] he = frameRms(high, hop, 128);
    int n = Math.min(le.length, he.length);
    if (n < 4) return new double[0];
    float fps = rate / (float) hop;
    float hMax = 0f;
    float lSum = 0f;
    for (int i = 0; i < n; i++) {
      if (he[i] > hMax) hMax = he[i];
      lSum += le[i];
    }
    float lMean = lSum / n;
    float hFloor = Math.max(1e-4f, hMax * 0.07f);
    float lFloor = Math.max(0.012f, lMean * 1.2f);
    int minGap = Math.max(1, Math.round(0.12f * fps));
    java.util.ArrayList<Double> out = new java.util.ArrayList<Double>();
    int prev = -minGap * 2;
    for (int i = 2; i < n - 1; i++) {
      float rise = he[i] - he[i - 1];
      if (rise < hFloor || he[i] < he[i + 1]) continue;
      float body = Math.max(le[i], Math.max(le[i - 1], le[Math.max(0, i - 2)]));
      if (body < lFloor) continue;
      if (body < he[i] * 0.45f) continue;
      if (i - prev < minGap) continue;
      out.add(Double.valueOf(Math.max(0, (i - 1) / (double) fps)));
      prev = i;
    }
    double[] d = new double[out.size()];
    for (int i = 0; i < out.size(); i++) d[i] = out.get(i).doubleValue();
    return d;
  }

  /** Guitar pick attacks. Drums fire on these, not on a metronome. */
  private static double[] pickStrums(float[] x, int sr) {
    if (x == null || x.length < 64) return new double[0];
    int rate = sr > 0 ? sr : SR;
    float[] y = hpN(x, rate, 1600f, 2);
    double[] found = peakTimes(y, rate);
    if (found.length >= 4) return found;
    float[] low = hp(lpN(x, rate, 420f, 2), rate, 70f);
    return peakTimes(low, rate);
  }

  private static double[] peakTimes(float[] x, int sr) {
    int hop = 128;
    float[] env = frameRms(x, hop, 256);
    float fps = sr / (float) hop;
    float max = 0f;
    float sum = 0f;
    for (int i = 0; i < env.length; i++) {
      if (env[i] > max) max = env[i];
      sum += env[i];
    }
    float mean = sum / Math.max(1, env.length);
    float floor = Math.max(mean * 1.7f, max * 0.14f);
    int minGap = Math.max(1, Math.round(0.13f * fps));
    java.util.ArrayList<Double> out = new java.util.ArrayList<Double>();
    int prev = -minGap * 2;
    for (int i = 1; i < env.length - 1; i++) {
      float v = env[i];
      if (v < floor || v < env[i - 1] || v < env[i + 1]) continue;
      if (i - prev < minGap) {
        if (!out.isEmpty() && v > env[prev]) {
          out.set(out.size() - 1, Double.valueOf(i / (double) fps));
          prev = i;
        }
        continue;
      }
      out.add(Double.valueOf(i / (double) fps));
      prev = i;
    }
    double[] d = new double[out.size()];
    for (int i = 0; i < out.size(); i++) d[i] = out.get(i).doubleValue();
    return d;
  }

  private static double strumLow(float[] x, int sr, double t) {
    if (x == null || x.length == 0) return 0;
    int rate = sr > 0 ? sr : SR;
    int i0 = (int) Math.round(t * rate);
    int a = Math.max(0, i0);
    int b = Math.min(x.length, a + (int) (rate * 0.04));
    double s = 0;
    int c = 0;
    for (int i = a; i < b; i++) {
      s += (double) x[i] * x[i];
      c++;
    }
    return c == 0 ? 0 : Math.sqrt(s / c);
  }

  /** Compose a guitar take that had no drum hits. Leaves a real drum transcription alone. */
  public static void stampGuitarIfEmpty(Engine.FileSet set, float[] samples, int sr) {
    if (set == null || samples == null || sr < 1000 || samples.length < sr) return;
    int heard = 0;
    for (Engine.Learned p : set.patterns) {
      if (p != null && p.cells != null && p.cells.length > 0 && p.cells[0] != null) heard += Engine.hitCount(p.cells);
    }
    for (Engine.LearnedFill f : set.fills) {
      if (f != null && f.cells != null && f.cells.length > 0 && f.cells[0] != null) heard += Engine.hitCount(f.cells);
    }
    if (heard >= 1) return;
    GuitarFit fit = fitGuitar(samples, sr, null);
    if (fit == null || fit.cells == null || fit.cells.length == 0 || fit.cells[0] == null) return;
    int n = Engine.hitCount(fit.cells);
    if (n < 1) return;
    for (int i = 0; i < set.patterns.size(); i++) {
      Engine.Learned item = set.patterns.get(i);
      if (item == null) continue;
      String kind = "";
      Engine.FileSetPart row = set.parts != null && i < set.parts.size() ? set.parts.get(i) : null;
      if (row != null && row.kind != null) kind = row.kind.toLowerCase();
      if ("intro".equals(kind) || "outro".equals(kind) || "break".equals(kind)) continue;
      item.cells = Engine.copyCells(fit.cells);
      item.tsNum = fit.tsNum;
      item.tsDen = fit.tsDen;
      item.closest = "";
      if (row != null) {
        row.hits = n;
        row.tsNum = fit.tsNum;
        row.tsDen = fit.tsDen;
        row.styleLabel = "";
        row.swing = 0;
        row.density = Math.min(1f, n / 32f);
      }
    }
  }

  public static void stampGuitarIfEmpty(Engine.FileSet set, byte[] wav) {
    if (wav == null || wav.length < 44) return;
    try {
      Pcm pcm = parseWav(wav);
      stampGuitarIfEmpty(set, pcm.samples, pcm.sr);
    } catch (RuntimeException ignored) {
    }
  }

  /** Custom Guitar style. Hits are on the guitar pulse, not a drifting bar grid. */
  public static GuitarFit fitGuitar(float[] samples, int sr, java.util.List<Engine.FileSetPart> parts) {
    GuitarClock clock = readGuitarClock(samples, sr);
    GuitarFit fit = new GuitarFit();
    fit.mix = clock.mix != null ? clock.mix : samples;
    fit.cleaned = clock.cleaned;
    fit.bpm = clock.bpm;
    int ts = Math.max(2, Math.min(7, clock.tsNum > 0 ? clock.tsNum : 4));
    fit.tsNum = ts;
    fit.tsDen = clock.tsDen > 0 ? clock.tsDen : 4;
    fit.swing = clock.swing;
    fit.fileSec = clock.fileSec;
    fit.beats = clock.beats == null ? new double[0] : clock.beats;
    int steps = Math.max(4, ts * 4);
    int[][] cells = new int[Engine.TRACK_ID.length][steps];
    int kick = trackIndex("kick");
    int snare = trackIndex("snare");
    int rim = trackIndex("rim");
    int hat = trackIndex("chh");
    double per = steps / (double) ts;
    int back = Math.max(1, ts / 2);
    int backStep = (int) Math.round(back * per);
    if (backStep >= steps) backStep = steps / 2;
    cells[kick][0] = 110;
    if (snare >= 0 && backStep >= 0 && backStep < steps) cells[snare][backStep] = 8;
    if (rim >= 0 && backStep >= 0 && backStep < steps) cells[rim][backStep] = 100;
    if (hat >= 0) cells[hat][0] = 28;
    double[] strums = clock.beats == null ? new double[0] : clock.beats;
    if (strums.length < 4 && clock.onsets != null) strums = clock.onsets;
    fit.beats = strums;
    java.util.ArrayList<Double> times = new java.util.ArrayList<Double>();
    java.util.ArrayList<Integer> tracks = new java.util.ArrayList<Integer>();
    java.util.ArrayList<Integer> vels = new java.util.ArrayList<Integer>();
    int kickAt = 0;
    double loud = -1;
    for (int i = 0; i < strums.length; i++) {
      double e = strumLow(samples, sr, strums[i]);
      if (e > loud) {
        loud = e;
        kickAt = i;
      }
    }
    int kickParity = kickAt % 2;
    for (int i = 0; i < strums.length; i++) {
      double t = strums[i];
      if (t < -0.0005 || t >= fit.fileSec - 0.0005) continue;
      if (silentPart(parts, t)) continue;
      if ((i % 2) == kickParity) addHit(times, tracks, vels, t, kick, 100);
      else if (rim >= 0) addHit(times, tracks, vels, t, rim, 92);
    }
    fit.cells = cells;
    fit.time = new double[times.size()];
    fit.track = new int[times.size()];
    fit.vel = new int[times.size()];
    for (int i = 0; i < times.size(); i++) {
      fit.time[i] = times.get(i).doubleValue();
      fit.track[i] = tracks.get(i).intValue();
      fit.vel[i] = vels.get(i).intValue();
    }
    return fit;
  }

  /** Sit each beat on the guitar strum. If the grid missed the strums, the strums become the beat. */
  private static double[] lockPulseToStrums(double[] beats, double[] onsets, double period, double fileSec) {
    if (onsets == null || onsets.length < 6) return beats == null ? new double[0] : beats;
    double window = Math.min(0.16, Math.max(0.045, period * 0.34));
    if (beats != null && beats.length >= 4) {
      int near = 0;
      for (int i = 0; i < beats.length; i++) if (nearestDist(onsets, beats[i]) <= window) near++;
      if (near * 2 >= beats.length) {
        double[] out = new double[beats.length];
        for (int i = 0; i < beats.length; i++) {
          double d = nearestDist(onsets, beats[i]);
          out[i] = d <= window ? nearestTime(onsets, beats[i]) : beats[i];
        }
        return out;
      }
    }
    java.util.ArrayList<Double> chain = new java.util.ArrayList<Double>();
    double expect = onsets[0];
    int i = 0;
    while (expect < fileSec - 0.02 && chain.size() < 20000) {
      double best = -1;
      double bestD = window;
      while (i < onsets.length && onsets[i] < expect - window) i++;
      for (int k = i; k < onsets.length; k++) {
        double d = Math.abs(onsets[k] - expect);
        if (onsets[k] > expect + window) break;
        if (d < bestD) {
          bestD = d;
          best = onsets[k];
        }
      }
      double use = best > 0 ? best : expect;
      if (chain.isEmpty() || use > chain.get(chain.size() - 1).doubleValue() + 0.04) chain.add(Double.valueOf(use));
      expect = use + period;
    }
    if (chain.size() < 4) return beats == null ? new double[0] : beats;
    double[] out = new double[chain.size()];
    for (int n = 0; n < chain.size(); n++) out[n] = chain.get(n).doubleValue();
    return out;
  }

  private static double nearestDist(double[] xs, double t) {
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i < xs.length; i++) best = Math.min(best, Math.abs(xs[i] - t));
    return best;
  }

  private static double nearestTime(double[] xs, double t) {
    double best = t;
    double near = Double.POSITIVE_INFINITY;
    for (int i = 0; i < xs.length; i++) {
      double d = Math.abs(xs[i] - t);
      if (d < near) {
        near = d;
        best = xs[i];
      }
    }
    return best;
  }

  /** Missing kit slots still play, so a style is never silent on a partial drum set. */
  private static short[][] voicesOrBuilt(short[][] voices) {
    short[][] built = null;
    int n = voices == null ? 0 : voices.length;
    if (n == 0) return buildVoices(SR);
    boolean hole = false;
    for (int i = 0; i < n; i++) {
      if (voices[i] == null || voices[i].length == 0) hole = true;
    }
    if (!hole) return voices;
    built = buildVoices(SR);
    for (int i = 0; i < n; i++) {
      if ((voices[i] == null || voices[i].length == 0) && i < built.length) voices[i] = built[i];
    }
    return voices;
  }

  public static short[] renderGuitarFit(GuitarFit fit, short[][] voices) {
    int sr = 22050;
    int total = fit == null ? 1 : Math.max(1, (int) Math.round(Math.max(0.01, fit.fileSec) * sr));
    int[] acc = new int[total];
    if (fit == null || fit.time == null) return new short[total];
    int kick = trackIndex("kick");
    int rim = trackIndex("rim");
    short[] kickV = folkKick(sr);
    short[] rimV = folkClick(sr);
    for (int i = 0; i < fit.time.length; i++) {
      int tr = fit.track[i];
      int vel = fit.vel[i];
      if (vel <= 0 || tr < 0) continue;
      short[] srcv = null;
      if (tr == kick) srcv = kickV;
      else if (tr == rim) srcv = rimV;
      if (srcv == null) continue;
      int at = (int) Math.round(fit.time[i] * sr);
      if (at < 0 || at >= total) continue;
      double g = vel / 127.0;
      for (int k = 0; k < srcv.length && at + k < total; k++) acc[at + k] += (int) (srcv[k] * g);
    }
    short[] pcm = new short[total];
    for (int i = 0; i < total; i++) pcm[i] = (short) Math.max(-32767, Math.min(32767, acc[i]));
    return pcm;
  }

  /** Soft foot, not the pop kit kick. */
  private static short[] folkKick(int sr) {
    int n = (int) (sr * 0.16);
    short[] buf = new short[Math.max(32, n)];
    for (int i = 0; i < buf.length; i++) {
      double env = Math.exp(-i * 18.0 / sr);
      double f = 70 - i * 40.0 / buf.length;
      double s = Math.sin(2 * Math.PI * f * i / sr);
      buf[i] = (short) Math.max(-32767, Math.min(32767, s * env * 20000));
    }
    return buf;
  }

  /** Short wood click. Not a snare. */
  private static short[] folkClick(int sr) {
    int n = (int) (sr * 0.04);
    short[] buf = new short[Math.max(16, n)];
    for (int i = 0; i < buf.length; i++) {
      double env = Math.exp(-i * 90.0 / sr);
      double s = Math.sin(2 * Math.PI * 1180 * i / sr) * 0.85 + Math.sin(2 * Math.PI * 1860 * i / sr) * 0.3;
      buf[i] = (short) Math.max(-32767, Math.min(32767, s * env * 22000));
    }
    return buf;
  }

  /** Guitar body under 320 Hz. Sung syllables sit above this and must not clock the drums. */
  private static float[] guitarPulse(float[] pcm) {
    int n = pcm == null ? 0 : pcm.length;
    float[] y = new float[n];
    if (n == 0) return y;
    Cascade lp = new Cascade(SR, 320f, 3, false);
    Cascade hp = new Cascade(SR, 50f, 1, true);
    for (int i = 0; i < n; i++) y[i] = hp.next(lp.next(pcm[i]));
    return y;
  }

  private static int mod(int n, int m) {
    if (m <= 0) return 0;
    int r = n % m;
    return r < 0 ? r + m : r;
  }

  private static Engine.Learned namedPattern(Engine.FileSet set, String name) {
    if (set == null || name == null) return null;
    for (Engine.Learned p : set.patterns) {
      if (p != null && p.name != null && p.name.trim().equalsIgnoreCase(name.trim())) return p;
    }
    return null;
  }

  private static String fillernName(Engine.FileSet set, String patternName) {
    if (set == null || patternName == null) return null;
    for (String[] row : set.fillerns) {
      if (row != null && row.length >= 2 && row[0] != null && row[0].trim().equalsIgnoreCase(patternName.trim())) return row[1];
    }
    return null;
  }

  private static int[][] namedFill(Engine.FileSet set, String name) {
    if (set == null || name == null) return null;
    for (Engine.LearnedFill f : set.fills) {
      if (f != null && f.name != null && f.name.trim().equalsIgnoreCase(name.trim()) && f.cells != null) return Engine.lastBar(f.cells);
    }
    return null;
  }

  public static GuitarClock readGuitarClock(float[] samples, int sr) {
    double fileSec = samples != null && sr > 0 ? samples.length / (double) sr : 0;
    GuitarClock clock = new GuitarClock();
    clock.fileSec = fileSec;
    GuitarSource source = guitarMixSource(samples, sr);
    clock.mix = source.samples != null ? source.samples : samples;
    clock.cleaned = source.cleaned;
    if (!(fileSec >= 1.2) || samples == null || samples.length == 0) {
      clock.beats = metronome(120, Math.max(fileSec, 0.5));
      return clock;
    }
    float[] raw = sr == SR ? samples : resample(samples, sr, SR, samples.length);
    float[] env = flux(smooth(frameRms(guitarPulse(raw), 128, 512)));
    float fps = SR / 128f;
    float energy = 0f;
    for (int i = 0; i < env.length; i++) energy += env[i];
    if (!(energy > 0f)) {
      clock.beats = metronome(120, fileSec);
      return clock;
    }
    int bpm = 120;
    double phase = 0;
    double score = -1;
    for (int b = 60; b <= 180; b++) {
      double[] hit = bestPhase(env, fps, b);
      double bias = b >= 168 ? 0.9 : 1;
      double adj = hit[0] * bias;
      if (adj > score) {
        score = adj;
        bpm = b;
        phase = hit[1];
      }
    }
    for (int b = Math.max(60, bpm - 4); b <= Math.min(180, bpm + 4); b++) {
      double[] hit = bestPhase(env, fps, b);
      if (hit[0] > score) {
        score = hit[0];
        bpm = b;
        phase = hit[1];
      }
    }
    java.util.ArrayList<Double> heardOn = new java.util.ArrayList<Double>();
    pickOnsets(env, fps, heardOn);
    int ioi = bpmFromOnsets(heardOn);
    if (ioi > 0 && Math.abs(ioi - bpm) > 4 && onBeatMean(env, fps, ioi) >= onBeatMean(env, fps, bpm) * 0.72) {
      bpm = ioi;
      phase = bestPhase(env, fps, bpm)[1];
    }
    java.util.ArrayList<Double> beats = new java.util.ArrayList<Double>();
    java.util.ArrayList<Double> onsets = new java.util.ArrayList<Double>();
    double gridPhase = fileSec >= 10 ? windowPhase(env, fps, bpm) : phase;
    int[] bpmBox = new int[] { bpm };
    trackBeats(env, fps, bpm, gridPhase, fileSec, beats, onsets, bpmBox);
    bpm = bpmBox[0];
    clock.bpm = bpm;
    if (beats.size() < 3) {
      clock.beats = metronome(bpm, fileSec);
      clock.onsets = toDoubles(onsets);
      return clock;
    }
    clock.beats = toDoubles(beats);
    clock.onsets = toDoubles(onsets);
    int meter = chooseMeter(env, fps, clock.beats);
    clock.tsNum = meter;
    clock.bar0 = barOrigin(env, fps, clock.beats, meter);
    clock.swing = measureSwing(env, fps, clock.beats);
    return clock;
  }

  private static double[] metronome(int bpm, double fileSec) {
    double period = 60.0 / Math.max(40, bpm);
    java.util.ArrayList<Double> beats = new java.util.ArrayList<Double>();
    for (double t = 0; t < fileSec - 0.02 && beats.size() < 20000; t += period) beats.add(Double.valueOf(t));
    double[] out = new double[beats.size()];
    for (int i = 0; i < beats.size(); i++) out[i] = beats.get(i).doubleValue();
    return out;
  }

  private static double envAt(float[] env, float fps, double t) {
    int i = (int) Math.round(t * fps);
    float m = 0f;
    for (int k = -2; k <= 2; k++) {
      int j = i + k;
      if (j >= 0 && j < env.length) m = Math.max(m, env[j]);
    }
    return m;
  }

  private static double phaseScore(float[] env, float fps, int bpm, double phase) {
    double period = 60.0 / bpm;
    double end = env.length / (double) fps - 0.04;
    double s = 0;
    int n = 0;
    for (double t = phase; t < end; t += period) {
      s += envAt(env, fps, t);
      n++;
    }
    return n >= 3 ? s : 0;
  }

  private static int bpmFromOnsets(java.util.ArrayList<Double> onsets) {
    if (onsets == null || onsets.size() < 8) return 0;
    int[] score = new int[221];
    for (int i = 0; i < onsets.size(); i++) {
      for (int k = 1; k <= 4 && i + k < onsets.size(); k++) {
        double dt = onsets.get(i + k).doubleValue() - onsets.get(i).doubleValue();
        if (dt < 0.28 || dt > 1.2) continue;
        double bpm = 60.0 / dt;
        while (bpm < 70) bpm *= 2;
        while (bpm > 160) bpm /= 2;
        int b = (int) Math.round(bpm);
        if (b >= 70 && b <= 160) score[b] += k == 1 ? 2 : 1;
      }
    }
    int bestB = 0;
    int best = 0;
    for (int b = 70; b <= 160; b++) {
      int s = score[b] * 2 + score[b - 1] + score[b + 1];
      if (s > best) {
        best = s;
        bestB = b;
      }
    }
    if (bestB < 70 || best < 12) return 0;
    int alt = 0;
    int altB = 0;
    for (int b = 70; b <= 160; b++) {
      if (Math.abs(b - bestB) <= 4) continue;
      int s = score[b] * 2 + score[b - 1] + score[b + 1];
      if (s > alt) {
        alt = s;
        altB = b;
      }
    }
    if (altB > 0 && Math.abs(altB - bestB) <= 12 && alt >= best * 0.9) {
      return (int) Math.round((bestB * (double) best + altB * (double) alt) / (best + alt));
    }
    if (best < alt * 1.25) return 0;
    return bestB;
  }

  private static double onBeatMean(float[] env, float fps, int bpm) {
    double phase = bestPhase(env, fps, bpm)[1];
    double period = 60.0 / Math.max(40, bpm);
    double end = env.length / (double) fps - 0.04;
    double s = 0;
    int n = 0;
    for (double t = phase; t < end; t += period) {
      s += envAt(env, fps, t);
      n++;
    }
    return n >= 3 ? s / n : 0;
  }

  private static double[] bestPhase(float[] env, float fps, int bpm) {
    double period = 60.0 / bpm;
    double score = 0;
    double phase = 0;
    for (int i = 0; i < 48; i++) {
      double p = (i / 48.0) * period;
      double s = phaseScore(env, fps, bpm, p);
      if (s > score) {
        score = s;
        phase = p;
      }
    }
    return new double[] { score, phase };
  }

  private static double median(java.util.ArrayList<Double> xs) {
    if (xs.isEmpty()) return 0;
    double[] s = new double[xs.size()];
    for (int i = 0; i < xs.size(); i++) s[i] = xs.get(i).doubleValue();
    java.util.Arrays.sort(s);
    return s[s.length / 2];
  }

  private static void pickOnsets(float[] env, float fps, java.util.ArrayList<Double> out) {
    float max = 0f;
    float sum = 0f;
    for (int i = 0; i < env.length; i++) {
      if (env[i] > max) max = env[i];
      sum += env[i];
    }
    float mean = sum / Math.max(1, env.length);
    float floor = Math.max(mean * 1.35f, max * 0.16f);
    int minGap = Math.max(1, Math.round(0.07f * fps));
    int prev = -minGap * 2;
    for (int i = 1; i < env.length - 1; i++) {
      float v = env[i];
      if (v < floor || v < env[i - 1] || v < env[i + 1]) continue;
      if (i - prev < minGap) {
        if (v > env[prev]) {
          out.remove(out.size() - 1);
          out.add(Double.valueOf(i / (double) fps));
          prev = i;
        }
        continue;
      }
      out.add(Double.valueOf(i / (double) fps));
      prev = i;
    }
  }

  private static void trackBeats(
      float[] env,
      float fps,
      int bpm,
      double phase,
      double fileSec,
      java.util.ArrayList<Double> beats,
      java.util.ArrayList<Double> onsets,
      int[] bpmBox) {
    pickOnsets(env, fps, onsets);
    double period = 60.0 / Math.max(40, bpm);
    if (onsets.size() < 4) {
      double expect = phase;
      while (expect < fileSec - 0.02 && beats.size() < 20000) {
        beats.add(Double.valueOf(expect));
        expect += period;
      }
      if (bpmBox != null && bpmBox.length > 0) bpmBox[0] = lockToLateAudio(env, fps, beats, bpm, fileSec);
      snapBeatsToOnsets(beats, onsets, 60.0 / Math.max(40, bpmBox != null && bpmBox.length > 0 ? bpmBox[0] : bpm));
      return;
    }
    double base = period;
    double snap = Math.min(0.22, period * 0.36);
    double prev = onsets.get(0).doubleValue();
    double nearPhase = snap;
    for (int i = 0; i < onsets.size(); i++) {
      double o = onsets.get(i).doubleValue();
      if (o > phase + snap) break;
      double d = Math.abs(o - phase);
      if (d < nearPhase) {
        nearPhase = d;
        prev = o;
      }
    }
    beats.add(Double.valueOf(prev));
    while (prev < fileSec - 0.05 && beats.size() < 20000) {
      double expect = prev + period;
      if (expect > fileSec + period * 0.2) break;
      double reach = Math.min(0.22, period * 0.36);
      double best = -1;
      double near = reach;
      for (int i = 0; i < onsets.size(); i++) {
        double o = onsets.get(i).doubleValue();
        if (o <= prev + period * 0.5) continue;
        if (o > expect + reach) break;
        double d = Math.abs(o - expect);
        if (d < near) {
          near = d;
          best = o;
        }
      }
      double use = best > 0 ? best : expect;
      if (use <= prev + 0.04) break;
      if (best > 0) {
        double iv = use - prev;
        if (iv > period * 0.72 && iv < period * 1.35) period = period * 0.7 + iv * 0.3;
      }
      beats.add(Double.valueOf(use));
      prev = use;
    }
    if (pulledOff(onsets, beats, phase, base, fileSec)) {
      beats.clear();
      double t = phase;
      while (t < fileSec - 0.02 && beats.size() < 20000) {
        beats.add(Double.valueOf(t));
        t += base;
      }
    }
    imposeLatePhase(env, fps, beats, bpm, fileSec);
    spliceOpening(beats, fileSec);
    if (bpmBox != null && bpmBox.length > 0) bpmBox[0] = lockToLateAudio(env, fps, beats, bpm, fileSec);
    snapBeatsToOnsets(beats, onsets, 60.0 / Math.max(40, bpmBox != null && bpmBox.length > 0 ? bpmBox[0] : bpm));
  }

  /** The later guitar, not the opening pulse, owns the beat when they disagree. */
  private static void imposeLatePhase(float[] env, float fps, java.util.ArrayList<Double> beats, int bpm, double fileSec) {
    if (beats.size() < 8 || fileSec < 36) return;
    double period = 60.0 / Math.max(40, bpm);
    double phase = lateOnlyPhase(env, fps, bpm, fileSec);
    double earlyEnd = Math.min(55, fileSec * 0.4);
    double err = 0;
    int n = 0;
    for (int i = 0; i < beats.size(); i++) {
      double b = beats.get(i).doubleValue();
      if (b > earlyEnd) break;
      double d = Math.abs(b - phase) % period;
      if (d > period / 2) d = period - d;
      err += d;
      n++;
    }
    if (n < 4 || err / n < 0.07) return;
    double late0 = Math.max(fileSec * 0.55, Math.min(fileSec * 0.8, 70));
    double openPhase = beats.get(0).doubleValue() % period;
    if (openPhase < 0) openPhase += period;
    if (phaseScoreFrom(env, fps, phase, period, late0, fileSec) < phaseScoreFrom(env, fps, openPhase, period, late0, fileSec) * 1.03) return;
    beats.clear();
    double t = phase;
    while (t < fileSec - 0.02 && beats.size() < 20000) {
      beats.add(Double.valueOf(t));
      t += period;
    }
  }

  private static double phaseScoreFrom(float[] env, float fps, double phase, double period, double start, double fileSec) {
    double s = 0;
    int c = 0;
    double t = phase < start ? phase + Math.ceil((start - phase) / period) * period : phase;
    for (; t < fileSec - 0.05; t += period) {
      s += envAt(env, fps, t);
      c++;
    }
    return c > 0 ? s / c : 0;
  }

  private static double lateOnlyPhase(float[] env, float fps, int bpm, double fileSec) {
    double period = 60.0 / Math.max(40, bpm);
    double start = Math.max(fileSec * 0.55, Math.min(fileSec * 0.8, 70));
    double bestP = 0;
    double best = -1;
    for (int i = 0; i < 64; i++) {
      double p = (i / 64.0) * period;
      double t = p < start ? p + Math.ceil((start - p) / period) * period : p;
      double s = 0;
      int n = 0;
      for (; t < fileSec - 0.05; t += period) {
        s += envAt(env, fps, t);
        n++;
      }
      double mean = n >= 4 ? s / n : 0;
      if (mean > best) {
        best = mean;
        bestP = p;
      }
    }
    if (best > 0) return bestP;
    return bestPhase(env, fps, bpm)[1];
  }

  /** Opening on a steady wrong pulse is moved onto the beat the rest of the file locked to. */
  private static void spliceOpening(java.util.ArrayList<Double> beats, double fileSec) {
    if (beats.size() < 12 || fileSec < 36) return;
    double late0 = Math.max(fileSec * 0.72, Math.min(fileSec * 0.85, 80));
    if (!(late0 > 8 && late0 < fileSec - 4)) return;
    double anchor = -1;
    for (int i = 0; i < beats.size(); i++) {
      double t = beats.get(i).doubleValue();
      if (t >= late0) {
        anchor = t;
        break;
      }
    }
    double period = beatIoi(beats, late0, fileSec);
    if (!(anchor > 0 && period > 0.3)) return;
    double earlyEnd = Math.min(50, late0 * 0.55);
    double err = 0;
    int n = 0;
    for (int i = 0; i < beats.size(); i++) {
      double t = beats.get(i).doubleValue();
      if (t > earlyEnd) break;
      double d = Math.abs(t - anchor) % period;
      if (d > period / 2) d = period - d;
      err += d;
      n++;
    }
    if (n < 6 || err / n < 0.07) return;
    java.util.ArrayList<Double> fixed = new java.util.ArrayList<Double>();
    double t = anchor;
    while (t - period > -0.03) t -= period;
    if (t < -0.02) t += period;
    while (t < anchor - period * 0.35 && fixed.size() < 20000) {
      if (t >= -0.01) fixed.add(Double.valueOf(t));
      t += period;
    }
    for (int i = 0; i < beats.size(); i++) {
      double b = beats.get(i).doubleValue();
      if (b >= anchor - 0.02) fixed.add(Double.valueOf(b));
    }
    if (fixed.size() < 8) return;
    beats.clear();
    beats.addAll(fixed);
  }

  /**
   * The later guitar owns the whole song. When that part is a clear pulse,
   * the opening is rewritten onto it even if the early tracker walked off.
   */
  private static int lockToLateAudio(float[] env, float fps, java.util.ArrayList<Double> beats, int bpm, double fileSec) {
    if (beats == null || fileSec < 20) return bpm;
    int late = bestLateBpm(env, fps, fileSec, bpm);
    double period = 60.0 / Math.max(40, late);
    double phase = lateOnlyPhase(env, fps, late, fileSec);
    double late0 = Math.max(fileSec * 0.55, Math.min(fileSec * 0.8, 70));
    double on = phaseScoreFrom(env, fps, phase, period, late0, fileSec);
    double off = phaseScoreFrom(env, fps, phase + period * 0.5, period, late0, fileSec);
    if (!(on > 1e-8) || on < off * 1.05) return bpm;
    beats.clear();
    double t = phase;
    while (t - period > -0.03) t -= period;
    if (t < -0.02) t += period;
    while (t < fileSec - 0.02 && beats.size() < 20000) {
      if (t >= -0.01) beats.add(Double.valueOf(t));
      t += period;
    }
    return beats.size() >= 8 ? late : bpm;
  }

  /** Pull each grid beat onto the guitar strum when one is already close. */
  private static void snapBeatsToOnsets(java.util.ArrayList<Double> beats, java.util.ArrayList<Double> onsets, double period) {
    if (beats == null || onsets == null || beats.size() < 4 || onsets.size() < 4) return;
    double window = Math.min(0.09, Math.max(0.028, period * 0.16));
    for (int i = 0; i < beats.size(); i++) {
      double b = beats.get(i).doubleValue();
      double best = b;
      double near = window;
      for (int k = 0; k < onsets.size(); k++) {
        double o = onsets.get(k).doubleValue();
        double d = Math.abs(o - b);
        if (d < near) {
          near = d;
          best = o;
        }
      }
      if (best != b) beats.set(i, Double.valueOf(best));
    }
  }

  private static int bestLateBpm(float[] env, float fps, double fileSec, int hint) {
    double start = Math.max(fileSec * 0.55, Math.min(fileSec * 0.8, 70));
    int bestB = hint > 40 ? hint : 120;
    double best = -1;
    int loBpm = hint > 40 ? Math.max(60, hint - 8) : 60;
    int hiBpm = hint > 40 ? Math.min(180, hint + 8) : 180;
    for (int b = loBpm; b <= hiBpm; b++) {
      double phase = lateOnlyPhase(env, fps, b, fileSec);
      double period = 60.0 / b;
      double s = phaseScoreFrom(env, fps, phase, period, start, fileSec);
      double half = phaseScoreFrom(env, fps, phase + period * 0.5, period, start, fileSec);
      double contrast = s - half;
      if (hint > 40 && Math.abs(b - hint) <= 2) contrast *= 1.04;
      if (b >= 148) contrast *= 0.86;
      if (contrast > best) {
        best = contrast;
        bestB = b;
      }
    }
    return bestB;
  }

  private static double beatIoi(java.util.ArrayList<Double> beats, double t0, double t1) {
    java.util.ArrayList<Double> iv = new java.util.ArrayList<Double>();
    double prev = -1;
    for (int i = 0; i < beats.size(); i++) {
      double t = beats.get(i).doubleValue();
      if (t < t0 || t > t1) continue;
      if (prev >= 0) {
        double d = t - prev;
        if (d > 0.28 && d < 1.15) iv.add(Double.valueOf(d));
      }
      prev = t;
    }
    if (iv.size() < 4) return 0;
    return median(iv);
  }

  private static boolean pulledOff(
      java.util.ArrayList<Double> onsets,
      java.util.ArrayList<Double> beats,
      double phase,
      double period,
      double fileSec) {
    double late0 = fileSec * 0.45;
    double earlyEnd = Math.min(fileSec * 0.35, 80);
    double lateOn = gridErr(onsets, phase, period, late0, fileSec);
    double earlyBeat = gridErr(beats, phase, period, 0, earlyEnd);
    double earlyIoi = medianIoi(beats, 0, earlyEnd, period);
    double lateIoi = medianIoi(onsets, late0, fileSec, period);
    if (!(lateOn < 0.045 && earlyBeat > 0.08)) return false;
    if (!(earlyIoi > 0 && lateIoi > 0)) return false;
    return Math.abs(earlyIoi - period) / period < 0.12 && Math.abs(lateIoi - period) / period < 0.1;
  }

  private static double medianIoi(java.util.ArrayList<Double> xs, double t0, double t1, double period) {
    java.util.ArrayList<Double> iv = new java.util.ArrayList<Double>();
    double prev = -1;
    for (int i = 0; i < xs.size(); i++) {
      double t = xs.get(i).doubleValue();
      if (t < t0 || t > t1) continue;
      if (prev >= 0) {
        double d = t - prev;
        if (d > period * 0.45 && d < period * 1.7) iv.add(Double.valueOf(d));
      }
      prev = t;
    }
    if (iv.size() < 4) return 0;
    double[] s = new double[iv.size()];
    for (int i = 0; i < s.length; i++) s[i] = iv.get(i).doubleValue();
    java.util.Arrays.sort(s);
    return s[s.length / 2];
  }

  private static double gridErr(java.util.ArrayList<Double> beats, double phase, double period, double t0, double t1) {
    java.util.ArrayList<Double> xs = new java.util.ArrayList<Double>();
    for (int i = 0; i < beats.size(); i++) {
      double b = beats.get(i).doubleValue();
      if (b < t0 || b > t1) continue;
      double d = Math.abs(b - phase) % period;
      if (d > period / 2) d = period - d;
      xs.add(Double.valueOf(d));
    }
    if (xs.size() < 6) return 1;
    double[] s = new double[xs.size()];
    for (int i = 0; i < xs.size(); i++) s[i] = xs.get(i).doubleValue();
    java.util.Arrays.sort(s);
    return s[s.length / 2];
  }

  private static double windowPhase(float[] env, float fps, int bpm) {
    double whole = bestPhase(env, fps, bpm)[1];
    double period = 60.0 / bpm;
    double fileSec = env.length / (double) fps;
    if (fileSec < 10) return whole;
    double win = 12;
    double step = 6;
    java.util.ArrayList<double[]> hits = new java.util.ArrayList<double[]>();
    for (double t0 = 0; t0 + win <= fileSec + 0.2; t0 += step) {
      double score = 0;
      double phase = 0;
      double end = Math.min(fileSec, t0 + win);
      for (int i = 0; i < 48; i++) {
        double p = (i / 48.0) * period;
        double t = p < t0 ? p + Math.ceil((t0 - p) / period) * period : p;
        double s = 0;
        int n = 0;
        for (; t < end; t += period) {
          s += envAt(env, fps, t);
          n++;
        }
        double mean = n >= 4 ? s / n : 0;
        if (mean > score) {
          score = mean;
          phase = p;
        }
      }
      if (score > 0) hits.add(new double[] { phase, score * (0.35 + (1.15 * t0) / Math.max(1, fileSec)) });
    }
    if (hits.size() < 2) return whole;
    double bestP = hits.get(0)[0];
    double bestW = -1;
    for (int c = 0; c < hits.size(); c++) {
      double w = 0;
      double cp = hits.get(c)[0];
      for (int o = 0; o < hits.size(); o++) {
        double d = Math.abs(cp - hits.get(o)[0]);
        if (d > period / 2) d = period - d;
        if (d <= period * 0.08) w += hits.get(o)[1];
      }
      if (w > bestW) {
        bestW = w;
        bestP = cp;
      }
    }
    return bestP;
  }

  private static int barOrigin(float[] env, float fps, double[] beats, int ts) {
    int n = Math.max(2, Math.min(7, ts));
    int bestR = 0;
    double best = -1;
    for (int r = 0; r < n; r++) {
      double s = 0;
      int c = 0;
      for (int i = r; i < beats.length; i += n) {
        s += envAt(env, fps, beats[i]);
        c++;
      }
      double mean = c > 0 ? s / c : 0;
      if (mean > best) {
        best = mean;
        bestR = r;
      }
    }
    return bestR;
  }

  private static int chooseMeter(float[] env, float fps, double[] beats) {
    double ratio4 = meterScore(env, fps, beats, 4);
    int bestN = 4;
    double best = ratio4;
    for (int n : new int[] { 2, 3, 5, 6, 7 }) {
      double ratio = meterScore(env, fps, beats, n);
      if (ratio > best) {
        best = ratio;
        bestN = n;
      }
    }
    if (bestN != 4 && (beats.length < bestN * 4 || best < ratio4 * (bestN == 5 || bestN == 7 ? 1.85 : 1.45))) bestN = 4;
    return bestN;
  }

  private static double meterScore(float[] env, float fps, double[] beats, int n) {
    if (beats.length < n * 2) return 0;
    double down = 0;
    double all = 0;
    int nd = 0;
    for (int i = 0; i < beats.length; i++) {
      double e = envAt(env, fps, beats[i]);
      all += e;
      if (i % n == 0) {
        down += e;
        nd++;
      }
    }
    double mean = all / beats.length;
    if (!(mean > 0)) mean = 1;
    return nd > 0 ? (down / nd) / mean : 0;
  }

  private static int measureSwing(float[] env, float fps, double[] beats) {
    if (beats.length < 4) return 0;
    double straight = 0;
    double swung = 0;
    int n = 0;
    for (int i = 0; i < beats.length - 1; i++) {
      double a = beats[i];
      double dur = beats[i + 1] - a;
      if (dur < 0.12 || dur > 1.6) continue;
      straight += envAt(env, fps, a + dur * 0.5);
      swung += envAt(env, fps, a + dur * (2.0 / 3.0));
      n++;
    }
    if (n < 3 || swung < straight * 1.2) return 0;
    double bestPos = 2.0 / 3.0;
    double bestS = -1;
    for (int p = 52; p <= 70; p++) {
      double pos = p / 100.0;
      double s = 0;
      for (int i = 0; i < beats.length - 1; i++) {
        double a = beats[i];
        double dur = beats[i + 1] - a;
        if (dur < 0.12 || dur > 1.6) continue;
        s += envAt(env, fps, a + dur * pos);
      }
      if (s > bestS) {
        bestS = s;
        bestPos = pos;
      }
    }
    int swing = (int) Math.round(((bestPos - 0.5) / (1.0 / 6.0)) * 75);
    return Math.max(0, Math.min(75, swing));
  }

  private static void pushBeat(java.util.ArrayList<Double> times, double start, double dur, int swing, double rate) {
    double and = 0.5 + (Math.max(0, Math.min(75, swing)) / 75.0) * (1.0 / 6.0);
    double[] g = new double[] { 0, and / 2, and, and + (1 - and) / 2 };
    if (rate < 0.75) {
      times.add(Double.valueOf(start));
      times.add(Double.valueOf(start + dur * and));
      return;
    }
    if (rate < 1.5) {
      for (double p : g) times.add(Double.valueOf(start + dur * p));
      return;
    }
    for (int i = 0; i < 4; i++) {
      double a = g[i];
      double c = i < 3 ? g[i + 1] : 1;
      times.add(Double.valueOf(start + dur * a));
      times.add(Double.valueOf(start + dur * ((a + c) / 2)));
    }
  }

  private static double[] clockSteps(GuitarClock clock, double rate) {
    double fileSec = Math.max(0, clock.fileSec);
    int bpm = Math.max(40, clock.bpm > 0 ? clock.bpm : 120);
    double nom = 60.0 / bpm;
    java.util.ArrayList<Double> src = new java.util.ArrayList<Double>();
    if (clock.beats != null) for (double b : clock.beats) src.add(Double.valueOf(b));
    if (src.isEmpty()) {
      double[] m = metronome(bpm, fileSec);
      for (double b : m) src.add(Double.valueOf(b));
    }
    double downbeat = src.get(0).doubleValue();
    double dur0 = src.size() > 1 ? Math.max(0.15, src.get(1).doubleValue() - src.get(0).doubleValue()) : nom;
    double t = downbeat;
    while (t - dur0 > -0.02 && src.size() < 20000) {
      t -= dur0;
      src.add(0, Double.valueOf(t));
    }
    double last = src.get(src.size() - 1).doubleValue();
    double lastDur = src.size() > 1
        ? Math.max(0.15, src.get(src.size() - 1).doubleValue() - src.get(src.size() - 2).doubleValue())
        : nom;
    while (last < fileSec && src.size() < 20000) {
      last += lastDur;
      src.add(Double.valueOf(last));
    }
    java.util.ArrayList<Double> times = new java.util.ArrayList<Double>();
    for (int i = 0; i < src.size() - 1; i++) {
      double start = src.get(i).doubleValue();
      double dur = src.get(i + 1).doubleValue() - start;
      if (!(dur > 0.08) || dur > 2) continue;
      pushBeat(times, start, dur, clock.swing, rate);
    }
    java.util.ArrayList<Double> kept = new java.util.ArrayList<Double>();
    for (Double time : times) {
      double v = time.doubleValue();
      if (v >= -0.0005 && v < fileSec - 0.0005) kept.add(Double.valueOf(Math.max(0, v)));
    }
    double[] out = new double[kept.size()];
    for (int i = 0; i < kept.size(); i++) out[i] = kept.get(i).doubleValue();
    return out;
  }

  private static int clockDownIndex(GuitarClock clock, double[] times) {
    double downbeat = clock.beats != null && clock.beats.length > 0 ? clock.beats[0] : 0;
    int down = 0;
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i < times.length; i++) {
      double d = Math.abs(times[i] - downbeat);
      if (d < best) {
        best = d;
        down = i;
      }
    }
    return down;
  }

  private static final class PickedHit {
    final double time;
    final String kind;
    final int vel;
    final float rms;
    PickedHit(double time, String kind, int vel, float rms) {
      this.time = time;
      this.kind = kind;
      this.vel = vel;
      this.rms = rms;
    }
  }

  private static int[] pickAttackFrames(float[] env, int rate, int hop) {
    int n = env.length;
    if (n < 5 || rate < 1000 || hop < 1) return new int[0];
    float mx = 0;
    for (int i = 0; i < n; i++) if (env[i] > mx) mx = env[i];
    float floor = Math.max(0.02f, mx * 0.08f);
    int minI = Math.max(1, (int) (0.11 * rate / hop));
    int last = -minI;
    int[] tmp = new int[n];
    int c = 0;
    for (int i = 2; i < n - 2; i++) {
      float v = env[i];
      if (v < floor) continue;
      if (v < env[i - 1] || v < env[i + 1]) continue;
      int prevN = Math.min(5, i);
      float prev = 0;
      for (int k = i - prevN; k < i; k++) prev += env[k];
      prev /= prevN;
      if (v < prev * 1.35f && v < prev + 0.04f) continue;
      if (i - last < minI) continue;
      tmp[c++] = i;
      last = i;
    }
    int[] out = new int[c];
    System.arraycopy(tmp, 0, out, 0, c);
    return out;
  }

  private static float sustainRatio(float[] x, int sr) {
    int n = x == null ? 0 : x.length;
    if (n < 32 || sr < 1000) return 1f;
    int attackN = Math.min(n, Math.max(16, sr / 40));
    int s0 = Math.min(n, (int) (sr * 0.05f));
    int s1 = Math.min(n, (int) (sr * 0.13f));
    float attack = rmsRange(x, 0, attackN);
    float sustain = rmsRange(x, s0, s1);
    return attack > 1e-6f ? sustain / attack : 1f;
  }

  private static String classifyAttack(float[] x, int sr) {
    int n = x == null ? 0 : x.length;
    if (n < 32 || sr < 1000) return "snare";
    int attackN = Math.min(n, Math.max(16, sr / 40));
    int s0 = Math.min(n, (int) (sr * 0.05f));
    int s1 = Math.min(n, (int) (sr * 0.13f));
    float attack = rmsRange(x, 0, attackN);
    float sustain = rmsRange(x, s0, s1);
    float ratio = attack > 1e-6f ? sustain / attack : 1f;
    if (ratio >= 0.5f) return "rim";
    int m = Math.min(n, (int) (sr * 0.04f));
    if (m < 16) return "snare";
    float energy = 0;
    int zc = 0;
    float prev = x[0];
    float low = 0;
    float z = 0;
    float a = (float) (1.0 - Math.exp((-2.0 * Math.PI * 150.0) / sr));
    for (int i = 0; i < m; i++) {
      float v = x[i];
      energy += v * v;
      if ((prev >= 0f) != (v >= 0f)) zc++;
      prev = v;
      z += a * (v - z);
      low += z * z;
    }
    if (energy < 1e-8f) return "snare";
    float zcHz = (zc / 2f) * (sr / (float) m);
    float lowShare = low / energy;
    if (lowShare > 0.55f && zcHz < 400f) return "kick";
    if (zcHz > 3000f || lowShare < 0.12f) return "hat";
    return "snare";
  }

  private static int velocityOf(float rms) {
    float x = (rms - 0.02f) / 0.35f;
    if (x < 0f) x = 0f;
    if (x > 1f) x = 1f;
    return 40 + Math.round(x * 87f);
  }

  private static java.util.ArrayList<PickedHit> collectAttacks(float[] x, int sr) {
    java.util.ArrayList<PickedHit> out = new java.util.ArrayList<PickedHit>();
    if (x == null || sr < 1000 || x.length < sr) return out;
    int hop = 256;
    int win = 512;
    float[] env = frameRms(x, hop, win);
    int[] frames = pickAttackFrames(env, sr, hop);
    int shotN = Math.max(64, Math.round(sr * 0.14f));
    for (int f = 0; f < frames.length; f++) {
      double time = (frames[f] * (double) hop) / sr;
      int at = Math.min(x.length - 1, Math.round((float) time * sr));
      int end = Math.min(x.length, at + shotN);
      if (end - at < 32) continue;
      float[] shot = new float[end - at];
      System.arraycopy(x, at, shot, 0, shot.length);
      String kind = classifyAttack(shot, sr);
      int attackN = Math.min(shot.length, Math.max(16, sr / 40));
      float rms = rmsRange(shot, 0, attackN);
      out.add(new PickedHit(time, kind, velocityOf(rms), rms));
    }
    return out;
  }

  private static boolean ringingTake(java.util.List<PickedHit> attacks) {
    if (attacks == null || attacks.size() < 8) return false;
    int rim = 0;
    int drum = 0;
    for (int i = 0; i < attacks.size(); i++) {
      if ("rim".equals(attacks.get(i).kind)) rim++;
      else drum++;
    }
    return rim >= 8 && rim >= drum;
  }

  private static int bpmFromAttacks(java.util.List<PickedHit> attacks) {
    if (attacks == null || attacks.size() < 6) return 0;
    int[] score = new int[221];
    for (int i = 0; i < attacks.size(); i++) {
      for (int k = 1; k <= 6 && i + k < attacks.size(); k++) {
        double dt = attacks.get(i + k).time - attacks.get(i).time;
        if (dt < 0.22 || dt > 1.3) continue;
        double bpm = 60.0 / dt;
        while (bpm < 80) bpm *= 2;
        while (bpm > 180) bpm /= 2;
        int b = (int) Math.round(bpm);
        if (b < 80 || b > 180) continue;
        score[b] += k == 1 ? 2 : 1;
      }
    }
    int bestB = 120;
    int best = 0;
    for (int b = 80; b <= 180; b++) {
      int prev = b > 80 ? score[b - 1] : 0;
      int next = b < 180 ? score[b + 1] : 0;
      int s = score[b] * 2 + prev + next;
      if (s > best) {
        best = s;
        bestB = b;
      }
    }
    return best > 0 ? bestB : 0;
  }

  private static float phaseFromAttacks(java.util.List<PickedHit> attacks, int bpm) {
    float step = (60f / bpm) / 4f;
    float best = 0;
    float bestScore = -1;
    for (int p = 0; p < 16; p++) {
      float phase = (p / 16f) * step;
      float score = 0;
      for (int i = 0; i < attacks.size(); i++) {
        float rel = (float) attacks.get(i).time - phase;
        if (rel < 0) continue;
        float dist = Math.abs(rel / step - Math.round(rel / step));
        score += 1f - dist * 2f;
      }
      if (score > bestScore) {
        bestScore = score;
        best = phase;
      }
    }
    return best;
  }

  private static int trackForKind(String kind) {
    if ("kick".equals(kind)) return Engine.track("kick");
    if ("snare".equals(kind)) return Engine.track("snare");
    if ("hat".equals(kind)) return Engine.track("chh");
    return Engine.track("rim");
  }

  private static java.util.ArrayList<int[][]> stampAttacks(java.util.List<PickedHit> attacks, int bpm, int nBars, float phaseSec) {
    java.util.ArrayList<int[][]> bars = new java.util.ArrayList<int[][]>();
    float step = (60f / bpm) / 4f;
    for (int b = 0; b < nBars; b++) bars.add(Engine.emptyCells());
    for (int i = 0; i < attacks.size(); i++) {
      PickedHit a = attacks.get(i);
      float rel = (float) a.time - phaseSec;
      if (rel < -1e-4f) continue;
      int stepIndex = Math.round(Math.max(0f, rel) / step);
      int bar = stepIndex / 16;
      int s = stepIndex - bar * 16;
      if (bar < 0 || bar >= nBars || s < 0 || s > 15) continue;
      int tr = trackForKind(a.kind);
      int[][] cells = bars.get(bar);
      if (tr < 0 || tr >= cells.length || cells[tr] == null || s >= cells[tr].length) continue;
      if (a.vel > cells[tr][s]) cells[tr][s] = a.vel;
    }
    return bars;
  }

  private static int rowHits(int[][] bar, int t) {
    if (bar == null || t < 0 || t >= bar.length || bar[t] == null) return 0;
    int n = 0;
    for (int v : bar[t]) if (v > 0) n++;
    return n;
  }

  private static boolean rimMajority(java.util.List<int[][]> bars) {
    if (bars == null || bars.isEmpty()) return false;
    int rim = Engine.track("rim");
    int kick = Engine.track("kick");
    int snare = Engine.track("snare");
    int hat = Engine.track("chh");
    int r = 0;
    int d = 0;
    for (int i = 0; i < bars.size(); i++) {
      int[][] bar = bars.get(i);
      r += rowHits(bar, rim);
      d += rowHits(bar, kick) + rowHits(bar, snare) + rowHits(bar, hat);
    }
    return r >= 8 && r >= d;
  }

  private static int[][] foldBars(java.util.List<int[][]> bars) {
    int[][] out = Engine.emptyCells();
    int tracks = Engine.TRACK_ID.length;
    for (int t = 0; t < tracks; t++) {
      int[] acc = new int[16];
      int max = 0;
      for (int i = 0; i < bars.size(); i++) {
        int[][] bar = bars.get(i);
        if (bar == null || t >= bar.length || bar[t] == null) continue;
        for (int s = 0; s < 16 && s < bar[t].length; s++) {
          acc[s] += bar[t][s];
          if (acc[s] > max) max = acc[s];
        }
      }
      if (max <= 0 || out[t] == null) continue;
      for (int s = 0; s < 16 && s < out[t].length; s++) {
        if (acc[s] >= max * 0.35f) {
          out[t][s] = Math.max(64, Math.min(127, Math.round(70 + (acc[s] / (float) max) * 57)));
        }
      }
    }
    return out;
  }

  private static int[][] representativeBar(java.util.List<int[][]> bars) {
    int count = 0;
    for (int i = 0; i < bars.size(); i++) if (Engine.hitCount(bars.get(i)) > 0) count++;
    if (count == 0) return bars.isEmpty() ? Engine.emptyCells() : Engine.copyCells(bars.get(0));
    int[] ns = new int[count];
    int[][][] kept = new int[count][][];
    int w = 0;
    for (int i = 0; i < bars.size(); i++) {
      int h = Engine.hitCount(bars.get(i));
      if (h <= 0) continue;
      ns[w] = h;
      kept[w] = bars.get(i);
      w++;
    }
    for (int a = 1; a < count; a++) {
      int v = ns[a];
      int[][] b = kept[a];
      int j = a - 1;
      while (j >= 0 && ns[j] > v) {
        ns[j + 1] = ns[j];
        kept[j + 1] = kept[j];
        j--;
      }
      ns[j + 1] = v;
      kept[j + 1] = b;
    }
    return Engine.copyCells(kept[count / 2]);
  }

  private static PadIso rimPad(float[] raw, java.util.List<PickedHit> attacks) {
    int spec = 0;
    for (int i = 0; i < ISO_TRACK.length; i++) if ("rim".equals(ISO_TRACK[i])) spec = i;
    PadIso miss = emptyPad(spec, ISO_CENTER[spec]);
    PickedHit best = null;
    if (attacks != null) {
      for (int i = 0; i < attacks.size(); i++) {
        PickedHit a = attacks.get(i);
        if (!"rim".equals(a.kind)) continue;
        if (best == null || a.rms > best.rms) best = a;
      }
    }
    if (best == null || raw == null) return miss;
    int at = Math.max(0, Math.round((float) best.time * SR));
    int n = Math.max(64, Math.round(SR * 0.09f));
    float[] y = new float[n];
    float peak = 0;
    for (int i = 0; i < n; i++) {
      int s = at + i;
      float v = s >= 0 && s < raw.length ? raw[s] : 0;
      float t = Math.max(0, (i - SR * 0.012f) / SR);
      v *= (float) Math.exp(-t / 0.03f);
      y[i] = v;
      if (Math.abs(v) > peak) peak = Math.abs(v);
    }
    if (peak < 0.01f) return miss;
    float g = 0.9f / peak;
    int fadeIn = Math.max(2, Math.round(SR * 0.002f));
    int fadeOut = Math.max(8, Math.round(n * 0.28f));
    for (int i = 0; i < n; i++) y[i] *= g;
    for (int i = 0; i < fadeIn && i < n; i++) y[i] *= i / (float) fadeIn;
    for (int i = 0; i < fadeOut; i++) y[n - 1 - i] *= i / (float) fadeOut;
    PadIso p = emptyPad(spec, ISO_CENTER[spec]);
    p.found = true;
    p.sample = floatsToShorts(y);
    return p;
  }

  /** Point-downsample to the analyzer rate. Same samples analyze() would keep. */
  public static Pcm forAnalyze(float[] samples, int sr) {
    if (samples == null || samples.length == 0) return new Pcm(new float[0], SR);
    int useSr = sr <= 0 ? SR : sr;
    int srcN = Math.min(samples.length, useSr * 15 * 60);
    if (useSr == SR) {
      if (srcN == samples.length) return new Pcm(samples, SR);
      float[] cut = new float[srcN];
      System.arraycopy(samples, 0, cut, 0, srcN);
      return new Pcm(cut, SR);
    }
    double ratio = useSr / (double) SR;
    int n = Math.max(1, (int) Math.floor(srcN / ratio));
    float[] o = new float[n];
    for (int i = 0; i < n; i++) {
      int si = Math.min(srcN - 1, (int) Math.floor(i * ratio));
      o[i] = samples[si];
    }
    return new Pcm(o, SR);
  }

  public static Analysis analyze(float[] samples, int sr) {
    return analyze(samples, sr, true);
  }

  public static Analysis analyze(float[] samples, int sr, boolean isolate) {
    try {
      return analyzeWork(samples, sr, isolate);
    } catch (OutOfMemoryError oom) {
      throw new IllegalArgumentException("That song is too large for this device");
    }
  }

  /** Many unique bars (a guitar take) would build dozens of styles and crash the phone UI. */
  public static void compactImportBars(Analysis a) {
    if (a == null || a.isolated || a.bars == null || a.bars.size() < 12) return;
    java.util.HashSet<String> sigs = new java.util.HashSet<String>();
    for (int i = 0; i < a.bars.size() && sigs.size() <= 8; i++) {
      sigs.add(Engine.patternSignature(a.bars.get(i)));
    }
    if (sigs.size() <= 8) return;
    int[][] rep = representativeBar(a.bars);
    a.bars.clear();
    if (rep != null) a.bars.add(rep);
  }

  private static Analysis analyzeWork(float[] samples, int sr, boolean isolate) {
    Analysis a = new Analysis();
    if (samples.length < sr * 3) throw new IllegalArgumentException("Need a longer song (3 seconds+)");
    int maxN = Math.min(samples.length, sr * 15 * 60);
    float[] x = resample(samples, sr, SR, maxN);
    int hop = 128;
    int win = 512;
    float[] kickB = hp(lp(x, SR, 140), SR, 30);
    float[] snareB = hp(lp(x, SR, 2800), SR, 180);
    float[] hatB = hp(x, SR, 5500);
    float[] kick = flux(smooth(frameRms(kickB, hop, win)));
    float[] snare = flux(smooth(frameRms(snareB, hop, win)));
    float[] hat = flux(smooth(frameRms(hatB, hop, win)));
    int n = Math.min(kick.length, Math.min(snare.length, hat.length));
    float[] mix = new float[n];
    float hatSum = 0;
    float mixSum = 0;
    for (int i = 0; i < n; i++) {
      mix[i] = kick[i] * 1.4f + snare[i] + hat[i] * 0.7f;
      hatSum += hat[i];
      mixSum += mix[i];
    }
    float fps = SR / (float) hop;
    float raw = autocorrBpm(mix, fps, 70, 185);
    float hatDensity = mixSum > 0 ? hatSum / mixSum : 0;
    int bpm = pickBpm(raw, hatDensity);
    a.bpm = bpm;
    kick = keepPercussive(kick, kickB, false);
    snare = keepPercussive(snare, snareB, false);
    hat = keepPercussive(hat, hatB, false);
    float stepFrames = ((60f / bpm) / 4f) * fps;
    int phaseSteps = Math.max(8, Math.min(48, Math.round(stepFrames)));
    int bestPhase = 0;
    float bestScore = -1;
    for (int p = 0; p < phaseSteps; p++) {
      float[] k = fold(kick, stepFrames, p);
      float[] s = fold(snare, stepFrames, p);
      float score = k[0] + k[8] + s[4] * 1.2f + s[12] * 1.2f;
      if (score > bestScore) {
        bestScore = score;
        bestPhase = p;
      }
    }
    int[] kickHits = toHits(fold(kick, stepFrames, bestPhase), 0.42f);
    int[] snareHits = toHits(fold(snare, stepFrames, bestPhase), 0.46f);
    int[] hatHits = toHits(fold(hat, stepFrames, bestPhase), 0.4f);
    int[][] folded = fromHits(kickHits, snareHits, hatHits);
    int kicks = 0;
    int snares = 0;
    for (int s = 0; s < Engine.STEPS; s++) {
      if (kickHits[s] > 0) kicks++;
      if (snareHits[s] > 0) snares++;
    }
    boolean grooveOk = kicks >= 2 && snares >= 1 && kicks + snares >= 4
        && tonalDrum(kickB, kick, true) && tonalDrum(snareB, snare, false);
    java.util.ArrayList<PickedHit> attacks = collectAttacks(x, SR);
    boolean ringing = ringingTake(attacks);
    boolean drumTake = grooveOk && !ringing;
    if (ringing) {
      int ioi = bpmFromAttacks(attacks);
      if (ioi > 0) {
        bpm = ioi;
        a.bpm = bpm;
      }
    }
    float barLen = stepFrames * 16;
    int nBars = (int) Math.max(0, Math.floor((n - bestPhase) / barLen));
    if ((n - bestPhase) / barLen - nBars >= 0.25f) nBars++;
    nBars = Math.min(1024, nBars);
    float[] tomL = null;
    float[] tomM = null;
    float[] tomH = null;
    float[] ohh = null;
    float[] ride = null;
    float[] crash = null;
    if (drumTake) {
      tomL = drumOnset(x, 50, 100, false);
      tomM = drumOnset(x, 95, 175, false);
      tomH = drumOnset(x, 160, 280, false);
      ohh = drumOnset(x, 3500, 10000, true);
      ride = drumOnset(x, 250, 900, true);
      crash = drumOnset(x, 2000, 9000, true);
    }
    if (ringing) {
      float step = (60f / bpm) / 4f;
      float phase = phaseFromAttacks(attacks, bpm);
      float barSec = step * 16f;
      float spanSec = Math.max(0f, x.length / (float) SR - phase);
      int nb = (int) Math.floor(spanSec / barSec);
      if (spanSec / barSec - nb >= 0.25f) nb++;
      nb = Math.max(1, Math.min(1024, nb));
      a.bars.addAll(stampAttacks(attacks, bpm, nb, phase));
    } else if (drumTake && nBars >= 1) {
      for (int b = 0; b < nBars; b++) {
        float start = bestPhase + b * barLen;
        int[][] bar = fromHits(
            barHits(kick, start, stepFrames, 0.48f),
            barHits(snare, start, stepFrames, 0.48f),
            barHits(hat, start, stepFrames, 0.48f));
        if (peaky(tomL)) putHits(bar, "ltom", barHits(tomL, start, stepFrames, 0.48f));
        if (peaky(tomM)) putHits(bar, "mtom", barHits(tomM, start, stepFrames, 0.48f));
        if (peaky(tomH)) putHits(bar, "htom", barHits(tomH, start, stepFrames, 0.48f));
        if (peaky(ohh)) putHits(bar, "ohh", barHits(ohh, start, stepFrames, 0.48f));
        if (peaky(ride)) putHits(bar, "ride", barHits(ride, start, stepFrames, 0.48f));
        if (peaky(crash)) putHits(bar, "crash", barHits(crash, start, stepFrames, 0.48f));
        a.bars.add(bar);
      }
    }
    a.styleId = drumTake ? closestStyle(folded, bpm) : closestStyleByBpm(bpm);
    a.pcm = x;
    kickB = null;
    snareB = null;
    hatB = null;
    kick = null;
    snare = null;
    hat = null;
    tomL = null;
    tomM = null;
    tomH = null;
    ohh = null;
    ride = null;
    crash = null;
    if (isolate) {
      a.pads.addAll(isolateAll(x, drumTake, attacks));
      boolean kickFound = false;
      boolean snareFound = false;
      for (PadIso p : a.pads) {
        if (!p.found || p.sample == null) continue;
        if ("kick".equals(p.track)) { a.kickSample = p.sample; kickFound = true; }
        if ("snare".equals(p.track)) { a.snareSample = p.sample; snareFound = true; }
      }
      a.isolated = drumTake && kickFound && snareFound;
      a.fromGroove = a.isolated;
      if (!a.isolated) {
        a.kickSample = null;
        a.snareSample = null;
      }
    } else {
      a.pads.addAll(emptyPads());
      a.isolated = false;
      a.fromGroove = grooveOk;
    }
    int[][] groove;
    int[][] fill;
    if (ringing && !a.bars.isEmpty()) {
      groove = representativeBar(a.bars);
      fill = Engine.emptyCells();
    } else if (!a.bars.isEmpty()) {
      List<Engine.MidiSeg> segs = Engine.segmentMidiBars(a.bars);
      Engine.MidiSeg withFill = null;
      for (Engine.MidiSeg s : segs) {
        if (s.fill != null) { withFill = s; break; }
      }
      if (withFill != null) {
        groove = Engine.copyCells(withFill.groove);
        fill = Engine.copyCells(withFill.fill);
      } else {
        groove = Engine.copyCells(a.bars.get(0));
        int[][] last = a.bars.get(a.bars.size() - 1);
        if (a.bars.size() >= 2 && Engine.hitCount(last) >= 3
            && !Engine.patternSignature(groove).equals(Engine.patternSignature(last))) {
          fill = Engine.copyCells(last);
        } else {
          fill = Engine.emptyCells();
        }
      }
    } else {
      groove = folded;
      fill = Engine.emptyCells();
    }
    a.cells = stitch(groove, fill);
    a.fillCells = fill;
    return a;
  }

  private static String closestStyleByBpm(int bpm) {
    return StyleDb.kitFromBpm(bpm);
  }

  /** A guitar track with no drum kit. Mid tempo was landing on funk or pop. */
  private static String acousticStyle(int bpm, float hatDensity) {
    if (bpm >= 80 && bpm <= 130) return "folk";
    return StyleDb.kitFromBpm(bpm);
  }

  private static int[][] fromHits(int[] kick, int[] snare, int[] hat) {
    int[][] c = Engine.emptyCells();
    System.arraycopy(kick, 0, c[Engine.track("kick")], 0, Engine.STEPS);
    System.arraycopy(snare, 0, c[Engine.track("snare")], 0, Engine.STEPS);
    System.arraycopy(hat, 0, c[Engine.track("chh")], 0, Engine.STEPS);
    return c;
  }

  private static void putHits(int[][] cells, String id, int[] hits) {
    int t = Engine.track(id);
    if (t < 0 || hits == null) return;
    System.arraycopy(hits, 0, cells[t], 0, Math.min(Engine.STEPS, hits.length));
  }

  private static int[][] stitch(int[][] groove, int[][] fill) {
    int[][] o = Engine.emptyCells();
    for (int t = 0; t < Engine.TRACK_ID.length; t++) {
      System.arraycopy(groove[t], 0, o[t], 0, Engine.STEPS);
      if (fill != null) System.arraycopy(fill[t], 0, o[t], Engine.STEPS, Engine.STEPS);
    }
    return o;
  }

  private static int[] barHits(float[] onset, float start, float stepFrames, float floor) {
    float[] acc = new float[16];
    float end = start + stepFrames * 16;
    int i0 = Math.max(0, (int) Math.floor(start));
    for (int i = i0; i < onset.length && i < end; i++) {
      int step = Math.round((i - start) / stepFrames);
      if (step < 0 || step > 15) continue;
      acc[step] += onset[i];
    }
    return toHits(acc, floor);
  }

  private static boolean cymbal(String t) {
    return "chh".equals(t) || "ohh".equals(t) || "ride".equals(t) || "crash".equals(t);
  }

  private static float[] keepPercussive(float[] onset, float[] band, boolean loose) {
    float[] y = new float[onset.length];
    float peak = 0;
    for (int i = 0; i < onset.length; i++) if (onset[i] > peak) peak = onset[i];
    float floor = peak * 0.12f;
    int hop = 128;
    for (int i = 1; i < onset.length - 1; i++) {
      float v = onset[i];
      if (v < floor || v < onset[i - 1] || v <= onset[i + 1]) continue;
      int at = Math.min(Math.max(0, band.length - 1), i * hop);
      if (!percussive(band, at, loose)) continue;
      y[i] = v;
    }
    return y;
  }

  private static boolean tonalDrum(float[] band, float[] onset, boolean kick) {
    int[] top = new int[6];
    float[] topV = new float[6];
    for (int i = 1; i < onset.length - 1; i++) {
      float v = onset[i];
      if (v <= 1e-8f) continue;
      int slot = 0;
      for (int k = 1; k < 6; k++) if (topV[k] < topV[slot]) slot = k;
      if (v > topV[slot]) {
        topV[slot] = v;
        top[slot] = i;
      }
    }
    for (int k = 0; k < 6; k++) {
      if (topV[k] <= 1e-8f) continue;
      int at = Math.min(Math.max(0, band.length - 1), top[k] * 128);
      int n = Math.min(band.length - at, Math.round(SR * 0.12f));
      if (n < 64) continue;
      float[] shot = new float[n];
      System.arraycopy(band, at, shot, 0, n);
      if (toneRatio(shot, SR) < 0.72f) continue;
      if (kick) {
        int hz = estimateHz(shot, SR, 30, 200);
        if (hz > 0 && hz < 45) continue;
      }
      return true;
    }
    return false;
  }

  private static float[] drumOnset(float[] x, int lo, int hi, boolean loose) {
    float[] band = bandpass(x, SR, lo, hi);
    return keepPercussive(flux(smooth(frameRms(band, 128, 512))), band, loose);
  }

  private static boolean percussive(float[] band, int at, boolean loose) {
    float attack = rmsRange(band, at, at + Math.round(SR * 0.04f));
    float tail = rmsRange(band, at + Math.round(SR * 0.12f), at + Math.round(SR * 0.26f));
    if (attack < 1e-5f) return false;
    return tail / attack < (loose ? 0.82f : 0.55f);
  }

  private static boolean peaky(float[] onset) {
    if (onset == null || onset.length < 8) return false;
    float max = 0;
    float sum = 0;
    for (int i = 0; i < onset.length; i++) {
      if (onset[i] > max) max = onset[i];
      sum += onset[i];
    }
    float mean = sum / onset.length;
    return mean > 1e-8f && max > mean * 8f;
  }

  private static boolean needsBassStrip(String t) {
    return "kick".equals(t) || "snare".equals(t) || "ltom".equals(t) || "mtom".equals(t)
        || "htom".equals(t) || "ride".equals(t);
  }

  private static float[] drumGate(float[] y, int sr, String track) {
    if ("ride".equals(track) && y != null) return hp(y, sr, 1200);
    if (!needsBassStrip(track) || y == null) return y;
    float[] src = y;
    if ("snare".equals(track)) src = hp(src, sr, 180);
    int hold = Math.round(sr * 0.012f);
    float tau = "kick".equals(track) ? 0.055f : "snare".equals(track) ? 0.065f : 0.08f;
    float[] out = new float[src.length];
    for (int i = 0; i < src.length; i++) {
      float t = Math.max(0, (i - hold) / (float) sr);
      out[i] = src[i] * (float) Math.exp(-t / tau);
    }
    return out;
  }

  private static float[] extractShot(float[] band, float[] raw, int at, float preSec, float postSec, float rawMix) {
    int pre = Math.round(SR * preSec);
    int post = Math.round(SR * postSec);
    int a0 = Math.max(0, at - pre);
    int b0 = Math.min(band.length, at + post);
    int len = Math.max(32, b0 - a0);
    float[] y = new float[len];
    float peak = 0;
    float mix = Math.max(0, Math.min(0.4f, rawMix));
    float keep = 1 - mix;
    for (int i = 0; i < len && a0 + i < band.length; i++) {
      float v = band[a0 + i] * keep + raw[a0 + i] * mix;
      y[i] = v;
      if (Math.abs(v) > peak) peak = Math.abs(v);
    }
    int fadeIn = Math.max(2, Math.round(SR * 0.003f));
    int fadeOut = Math.max(8, Math.round(len * (mix < 0.12f ? 0.5f : 0.35f)));
    for (int i = 0; i < fadeIn && i < len; i++) y[i] *= i / (float) fadeIn;
    for (int i = 0; i < fadeOut && i < len; i++) y[len - 1 - i] *= i / (float) fadeOut;
    if (peak > 1e-6f) {
      float g = 0.92f / peak;
      for (int i = 0; i < len; i++) y[i] *= g;
    }
    return y;
  }

  private static float rmsRange(float[] x, int a, int b) {
    double s = 0;
    int n = 0;
    int lo = Math.max(0, a);
    int hi = Math.min(x.length, b);
    for (int i = lo; i < hi; i++) {
      s += x[i] * x[i];
      n++;
    }
    return n > 0 ? (float) Math.sqrt(s / n) : 0;
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

  private static short[] isolateShot(float[] raw, float[] band, float[] other, float[] onset, int hop, boolean kick) {
    int minGap = Math.max(4, Math.round(0.18f * SR / hop));
    int[] pk = peaks(onset, minGap, kick ? 0.36f : 0.4f);
    int bestAt = -1;
    float bestR = -1;
    int win = Math.round(SR * (kick ? 0.08f : 0.06f));
    int lim = Math.min(pk.length, 24);
    for (int i = 0; i < lim; i++) {
      int at = pk[i] * hop;
      float b = rmsRange(band, at, at + win);
      float o = rmsRange(other, at, at + win);
      float r = o > 1e-6f ? b / o : b * 10;
      if (b > (kick ? 0.012f : 0.01f) && r > bestR) {
        bestR = r;
        bestAt = at;
      }
    }
    if (bestAt < 0 || bestR < (kick ? 1.35f : 1.15f)) return null;
    int pre = Math.round(SR * (kick ? 0.012f : 0.008f));
    int post = Math.round(SR * (kick ? 0.28f : 0.2f));
    int a0 = Math.max(0, bestAt - pre);
    int b0 = Math.min(band.length, bestAt + post);
    int len = Math.max(32, b0 - a0);
    float[] y = new float[len];
    float peak = 0;
    for (int i = 0; i < len && a0 + i < band.length; i++) {
      float v = band[a0 + i] * 0.78f + raw[a0 + i] * 0.22f;
      y[i] = v;
      if (Math.abs(v) > peak) peak = Math.abs(v);
    }
    int fadeIn = Math.max(2, Math.round(SR * 0.003f));
    int fadeOut = Math.max(8, Math.round(len * 0.35f));
    for (int i = 0; i < fadeIn && i < len; i++) y[i] *= i / (float) fadeIn;
    for (int i = 0; i < fadeOut && i < len; i++) y[len - 1 - i] *= i / (float) fadeOut;
    if (peak > 1e-6f) {
      float g = 0.92f / peak;
      for (int i = 0; i < len; i++) y[i] *= g;
    }
    if (rmsRange(y, 0, len) < (kick ? 0.02f : 0.018f)) return null;
    return floatsToShorts(y);
  }

  private static float[] bandpass(float[] x, int sr, float lo, float hi) {
    float ny = sr * 0.45f;
    float hiC = Math.min(ny, Math.max(lo + 8, hi));
    float loC = Math.max(18, Math.min(lo, hiC - 8));
    return hp(lp(x, sr, hiC), sr, loC);
  }

  private static float toneRatio(float[] x, int sr) {
    if (x == null || x.length < 32) return 0;
    float energy = 0;
    for (int i = 0; i < x.length; i++) energy += x[i] * x[i];
    if (energy < 1e-8f) return 0;
    int minLag = Math.max(2, Math.round(sr / 500f));
    int maxLag = Math.min(x.length / 2, Math.round(sr / 70f));
    if (maxLag <= minLag) return 0;
    float best = 0;
    for (int lag = minLag; lag <= maxLag; lag++) {
      float s = 0;
      int n = x.length - lag;
      for (int i = 0; i < n; i++) s += x[i] * x[i + lag];
      if (s > best) best = s;
    }
    return best / energy;
  }

  private static int estimateHz(float[] x, int sr, float lo, float hi) {
    if (x == null || x.length < 16) return 0;
    if (lo >= 1800) {
      int zc = 0;
      for (int i = 1; i < x.length; i++) {
        if ((x[i - 1] >= 0) != (x[i] >= 0)) zc++;
      }
      float hz = (zc / 2f) * (sr / (float) x.length);
      if (hz >= lo * 0.45f && hz <= hi * 1.45f) return Math.round(hz);
      return 0;
    }
    int minLag = Math.max(2, Math.round(sr / hi));
    int maxLag = Math.min(x.length / 3, Math.round(sr / lo));
    if (maxLag <= minLag) return 0;
    float best = 0;
    int bestLag = 0;
    for (int lag = minLag; lag <= maxLag; lag++) {
      float s = 0;
      int n = x.length - lag;
      for (int i = 0; i < n; i++) s += x[i] * x[i + lag];
      if (s > best) {
        best = s;
        bestLag = lag;
      }
    }
    if (bestLag < 1) return 0;
    float hz = sr / (float) bestLag;
    if (hz < lo * 0.65f || hz > hi * 1.4f) return 0;
    return Math.round(hz);
  }

  private static int trackIndex(String id) {
    for (int i = 0; i < Engine.TRACK_ID.length; i++) if (Engine.TRACK_ID[i].equals(id)) return i;
    return -1;
  }

  private static String padName(String track) {
    int i = trackIndex(track);
    return i >= 0 ? Engine.TRACK_LABEL[i] : track;
  }

  private static PadIso emptyPad(int spec, int hz) {
    PadIso p = new PadIso();
    p.track = ISO_TRACK[spec];
    p.name = padName(p.track);
    p.hz = hz > 0 ? hz : ISO_CENTER[spec];
    p.lo = ISO_LO[spec];
    p.hi = ISO_HI[spec];
    return p;
  }

  public static List<PadIso> emptyPads() {
    List<PadIso> pads = new ArrayList<PadIso>();
    for (int i = 0; i < ISO_TRACK.length; i++) pads.add(emptyPad(i, 0));
    return pads;
  }

  private static float[] competing(float[] raw, int spec) {
    String t = ISO_TRACK[spec];
    if ("kick".equals(t) || "ltom".equals(t)) return bandpass(raw, SR, 160, 3200);
    if ("snare".equals(t) || "htom".equals(t)) return bandpass(raw, SR, 30, 120);
    if ("ride".equals(t)) return bandpass(raw, SR, 40, 500);
    if ("chh".equals(t) || "ohh".equals(t) || "crash".equals(t)) return bandpass(raw, SR, 30, 400);
    return bandpass(raw, SR, 30, 140);
  }

  private static PadIso isolateSpec(float[] raw, int spec, float loIn, float hiIn) {
    return isolateSpec(raw, spec, loIn, hiIn, false);
  }

  private static PadIso isolateSpec(float[] raw, int spec, float loIn, float hiIn, boolean ringing) {
    if ("rim".equals(ISO_TRACK[spec]) && loIn <= 0 && hiIn <= 0) return rimPad(raw, collectAttacks(raw, SR));
    float lo = loIn > 0 ? Math.max(18, loIn) : ISO_LO[spec];
    float hi = hiIn > 0 ? Math.max(lo + 8, hiIn) : ISO_HI[spec];
    float[] band = bandpass(raw, SR, lo, hi);
    float[] other = competing(raw, spec);
    int hop = 128;
    int win = 512;
    float[] onset = flux(smooth(frameRms(band, hop, win)));
    int minGap = Math.max(3, Math.round(("chh".equals(ISO_TRACK[spec]) ? 0.08f : 0.16f) * SR / hop));
    int[] pk = peaks(onset, minGap, "kick".equals(ISO_TRACK[spec]) ? 0.36f : 0.4f);
    String track = ISO_TRACK[spec];
    boolean shell = "kick".equals(track) || "snare".equals(track);
    int bestAt = -1;
    float bestR = -1;
    int w = Math.round(SR * ISO_POST[spec] * 0.35f);
    int lim = Math.min(pk.length, 36);
    float mix = "ride".equals(track) ? 0f : needsBassStrip(track) ? ("snare".equals(track) ? 0.03f : 0.05f) : 0.18f;
    float postSec = "snare".equals(track) ? Math.min(ISO_POST[spec], 0.14f)
        : "ride".equals(track) ? Math.min(ISO_POST[spec], 0.2f)
        : needsBassStrip(track) ? Math.min(ISO_POST[spec], 0.16f) : ISO_POST[spec];
    for (int i = 0; i < lim; i++) {
      int at = pk[i] * hop;
      float b = rmsRange(band, at, at + w);
      float o = rmsRange(other, at, at + w);
      float r = o > 1e-6f ? b / o : b * 10;
      if (!(b > ISO_RMS[spec] * 0.55f && r > bestR && percussive(band, at, cymbal(track)))) continue;
      if (shell) {
        float[] trial = extractShot(band, raw, at, ISO_PRE[spec], postSec, mix);
        if (sustainRatio(trial, SR) >= 1.08f && "rim".equals(classifyAttack(drumGate(trial, SR, track), SR))) continue;
      }
      bestR = r;
      bestAt = at;
    }
    PadIso miss = emptyPad(spec, Math.round((lo + hi) / 2f));
    miss.lo = Math.round(lo);
    miss.hi = Math.round(hi);
    if (bestAt < 0 || bestR < ISO_RATIO[spec]) return miss;
    float[] rawShot = extractShot(band, raw, bestAt, ISO_PRE[spec], postSec, mix);
    int measured = estimateHz(rawShot, SR, lo, hi);
    if (!cymbal(track) && toneRatio(rawShot, SR) < 0.72f) return miss;
    float[] y = drumGate(rawShot, SR, track);
    if (shell && sustainRatio(rawShot, SR) >= 1.08f && "rim".equals(classifyAttack(y, SR))) return miss;
    if (ringing && shell && "rim".equals(classifyAttack(rawShot, SR)) && !"kick".equals(classifyAttack(y, SR))) return miss;
    if (("kick".equals(track) || "ltom".equals(track)) && measured > 0 && measured < 45) return miss;
    boolean extra = spec >= 2 && spec <= 4;
    if (loIn <= 0 && hiIn <= 0 && measured == 0 && extra) return miss;
    if (rmsRange(y, 0, y.length) < ISO_RMS[spec]) return miss;
    if ("ride".equals(track)) {
      float leak = rmsRange(lp(y, SR, 500), 0, y.length);
      float all = rmsRange(y, 0, y.length);
      if (all > 1e-6f && leak / all > 0.22f) return miss;
    }
    int hz = measured > 0 ? measured : Math.round((lo + hi) / 2f);
    PadIso p = emptyPad(spec, hz);
    p.lo = Math.round(lo);
    p.hi = Math.round(hi);
    p.found = true;
    p.sample = floatsToShorts(y);
    return p;
  }

  public static PadIso isolateAtRange(float[] pcm, String track, int lo, int hi) {
    int spec = -1;
    for (int i = 0; i < ISO_TRACK.length; i++) if (ISO_TRACK[i].equals(track)) spec = i;
    if (spec < 0) {
      PadIso p = new PadIso();
      p.track = track;
      p.name = padName(track);
      p.lo = lo;
      p.hi = hi;
      p.hz = Math.round((lo + hi) / 2f);
      return p;
    }
    return isolateSpec(pcm, spec, lo, hi);
  }

  public static int isolateLo(String track) {
    for (int i = 0; i < ISO_TRACK.length; i++) if (ISO_TRACK[i].equals(track)) return ISO_LO[i];
    return 20;
  }

  public static int isolateHi(String track) {
    for (int i = 0; i < ISO_TRACK.length; i++) if (ISO_TRACK[i].equals(track)) return ISO_HI[i];
    return 200;
  }

  public static PadIso isolateAtHz(float[] pcm, String track, int hz) {
    int spec = -1;
    for (int i = 0; i < ISO_TRACK.length; i++) if (ISO_TRACK[i].equals(track)) spec = i;
    if (spec < 0) {
      PadIso p = new PadIso();
      p.track = track;
      p.name = padName(track);
      p.hz = hz;
      return p;
    }
    int use = hz > 0 ? Math.max(20, Math.min(12000, hz)) : ISO_CENTER[spec];
    if (hz <= 0) return isolateSpec(pcm, spec, 0, 0);
    return isolateSpec(pcm, spec, use * 0.55f, use * 1.85f);
  }

  private static float[] rmsEnv(float[] x, int hop) {
    int n = Math.max(1, x.length / hop);
    float[] e = new float[n];
    for (int i = 0; i < n; i++) e[i] = rmsRange(x, i * hop, (i + 1) * hop);
    return e;
  }

  private static float envCorr(float[] a, float[] b) {
    if (a.length < 4 || b.length < 4) return 0;
    int n = Math.min(48, Math.min(a.length, b.length));
    double sa = 0, sb = 0, ab = 0;
    for (int i = 0; i < n; i++) {
      float va = a[(int) ((i * (long) a.length) / n)];
      float vb = b[(int) ((i * (long) b.length) / n)];
      sa += va * va;
      sb += vb * vb;
      ab += va * vb;
    }
    double d = Math.sqrt(sa * sb);
    return d > 1e-12 ? (float) (ab / d) : 0;
  }

  public static PadIso isolateFromSample(float[] song, String track, float[] sample) {
    return isolateFromSample(song, track, sample, SR);
  }

  public static PadIso isolateFromSample(float[] song, String track, float[] sample, int sampleSr) {
    int spec = -1;
    for (int i = 0; i < ISO_TRACK.length; i++) if (ISO_TRACK[i].equals(track)) spec = i;
    if (spec < 0) {
      PadIso p = new PadIso();
      p.track = track;
      p.name = padName(track);
      return p;
    }
    float[] src = sampleSr == SR ? sample : resample(sample, sampleSr, SR, Math.min(sample.length, Math.max(1, sampleSr * 2)));
    int maxN = Math.min(src.length, SR * 2);
    float[] ref = src;
    if (maxN < src.length) {
      ref = new float[maxN];
      System.arraycopy(src, 0, ref, 0, maxN);
    }
    float lo = ISO_LO[spec];
    float hi = ISO_HI[spec];
    float[] bandRef = bandpass(ref, SR, lo, hi);
    int hz = estimateHz(bandRef, SR, lo, hi);
    if (hz <= 0) hz = ISO_CENTER[spec];
    PadIso best = isolateSpec(song, spec, hz > 0 ? hz * 0.55f : 0, hz > 0 ? hz * 1.85f : 0);
    String t = ISO_TRACK[spec];
    float[] songBand = bandpass(song, SR, lo, hi);
    float[] other = competing(song, spec);
    int hop = 128;
    float[] onset = flux(smooth(frameRms(songBand, hop, 512)));
    int minGap = Math.max(3, Math.round(("chh".equals(t) ? 0.08f : 0.16f) * SR / hop));
    int[] pk = peaks(onset, minGap, "kick".equals(t) ? 0.36f : 0.4f);
    float[] refEnv = rmsEnv(bandRef, hop);
    float bestCorr = best.found ? 0.34f : 0.24f;
    int bestAt = -1;
    int win = Math.round(SR * ISO_POST[spec] * 0.35f);
    float mix = "ride".equals(t) ? 0f : 0.04f;
    int lim = Math.min(pk.length, 40);
    for (int i = 0; i < lim; i++) {
      int at = pk[i] * hop;
      float[] shot = extractShot(songBand, song, at, ISO_PRE[spec], ISO_POST[spec], mix);
      float c = envCorr(rmsEnv(shot, hop), refEnv);
      float b = rmsRange(songBand, at, at + win);
      float o = rmsRange(other, at, at + win);
      float r = o > 1e-6f ? b / o : b * 10;
      if (c > bestCorr && r > ISO_RATIO[spec] * 0.65f) {
        bestCorr = c;
        bestAt = at;
      }
    }
    if (bestAt >= 0) {
      float[] y = drumGate(extractShot(songBand, song, bestAt, ISO_PRE[spec], ISO_POST[spec], mix), SR, t);
      if (rmsRange(y, 0, y.length) >= ISO_RMS[spec] * 0.65f) {
        PadIso p = emptyPad(spec, hz);
        p.found = true;
        p.sample = floatsToShorts(y);
        return p;
      }
    }
    if (best.found) {
      best.hz = hz;
      return best;
    }
    float[] band = bandpass(ref, SR, lo, hi);
    float[] on2 = flux(smooth(frameRms(band, hop, 512)));
    int[] pk2 = peaks(on2, Math.max(3, Math.round(0.12f * SR / hop)), 0.28f);
    int at = pk2.length > 0 ? pk2[0] * hop : 0;
    float[] fb = drumGate(extractShot(band, ref, at, ISO_PRE[spec], ISO_POST[spec], "ride".equals(t) ? 0f : 0.05f), SR, t);
    if (rmsRange(fb, 0, fb.length) < ISO_RMS[spec] * 0.35f) return emptyPad(spec, hz);
    PadIso p = emptyPad(spec, hz);
    p.found = true;
    p.fromSample = true;
    p.sample = floatsToShorts(fb);
    return p;
  }

  public static int isolateCenter(String track) {
    for (int i = 0; i < ISO_TRACK.length; i++) if (ISO_TRACK[i].equals(track)) return ISO_CENTER[i];
    return 100;
  }

  public static String padShort(String track) {
    int i = trackIndex(track);
    return i >= 0 ? Engine.TRACK_SHORT[i] : track;
  }

  public static String isolationStatus(List<PadIso> pads) {
    int found = 0;
    boolean kick = false;
    boolean snare = false;
    StringBuilder missing = new StringBuilder();
    boolean rim = false;
    for (PadIso p : pads) {
      if (p.found) {
        found++;
        if ("kick".equals(p.track)) kick = true;
        if ("snare".equals(p.track)) snare = true;
        if ("rim".equals(p.track)) rim = true;
      } else {
        if (missing.length() > 0) missing.append(", ");
        missing.append(p.name);
      }
    }
    if (kick && snare) {
      if (missing.length() == 0) return "Kick and snare found. Toms, hats, ride, and crash are ready for the pads.";
      return "Kick and snare found. " + found + " of " + pads.size() + " pads loaded. Type a Hz, or load a sample WAV, for " + missing + ".";
    }
    if (rim) return "Kick and snare were not isolated. Rim attacks from the recording are on the rim pad.";
    return "Kick and snare were not isolated. Type a Hz or load a sample WAV, then tap Try.";
  }

  public static List<PadIso> isolateAll(float[] raw, boolean isolated) {
    return isolateAll(raw, isolated, null);
  }

  public static List<PadIso> isolateAll(float[] raw, boolean isolated, java.util.List<PickedHit> attacks) {
    List<PadIso> pads = new ArrayList<PadIso>();
    java.util.List<PickedHit> hits = attacks != null ? attacks : collectAttacks(raw, SR);
    boolean ring = ringingTake(hits);
    PadIso kick = isolateSpec(raw, 0, 0, 0, ring);
    PadIso snare = isolateSpec(raw, 1, 0, 0, ring);
    pads.add(kick);
    pads.add(snare);
    boolean ks = isolated && kick.found && snare.found;
    for (int i = 2; i < ISO_TRACK.length; i++) {
      if ("rim".equals(ISO_TRACK[i])) {
        pads.add(rimPad(raw, hits));
        continue;
      }
      pads.add(ks ? isolateSpec(raw, i, 0, 0) : emptyPad(i, 0));
    }
    return pads;
  }

  public static void continueIsolation(float[] pcm, List<PadIso> pads) {
    boolean kick = false;
    boolean snare = false;
    for (PadIso p : pads) {
      if (p.found && "kick".equals(p.track)) kick = true;
      if (p.found && "snare".equals(p.track)) snare = true;
    }
    if (!kick || !snare) return;
    for (int i = 0; i < pads.size(); i++) {
      PadIso p = pads.get(i);
      if (p.found) continue;
      int spec = -1;
      for (int s = 0; s < ISO_TRACK.length; s++) if (ISO_TRACK[s].equals(p.track)) spec = s;
      if (spec < 2) continue;
      if ("rim".equals(p.track)) {
        pads.set(i, rimPad(pcm, collectAttacks(pcm, SR)));
        continue;
      }
      pads.set(i, isolateSpec(pcm, spec, 0, 0));
    }
  }

  public static int estimateTempo(float[] pcm, int sr) {
    if (pcm == null || pcm.length < sr * 2.5f) return 120;
    float[] x = sr == SR ? pcm : resample(pcm, sr, SR, Math.min(pcm.length, sr * 15));
    int hop = 128;
    int win = 512;
    float[] kickB = hp(lp(x, SR, 140), SR, 30);
    float[] snareB = hp(lp(x, SR, 2800), SR, 180);
    float[] hatB = hp(x, SR, 5500);
    float[] kick = flux(smooth(frameRms(kickB, hop, win)));
    float[] snare = flux(smooth(frameRms(snareB, hop, win)));
    float[] hat = flux(smooth(frameRms(hatB, hop, win)));
    int n = Math.min(kick.length, Math.min(snare.length, hat.length));
    float[] mix = new float[n];
    float hatSum = 0;
    float mixSum = 0;
    for (int i = 0; i < n; i++) {
      mix[i] = kick[i] * 1.4f + snare[i] + hat[i] * 0.7f;
      hatSum += hat[i];
      mixSum += mix[i];
    }
    float fps = SR / (float) hop;
    float raw = autocorrBpm(mix, fps, 70, 185);
    float hatDensity = mixSum > 0 ? hatSum / mixSum : 0;
    return pickBpm(raw, hatDensity);
  }

  private static int barHit(int[][] bar, String track, int step) {
    int t = trackIndex(track);
    if (t < 0 || bar == null || t >= bar.length || step < 0 || step >= bar[t].length) return 0;
    return bar[t][step];
  }

  private static int[] guessTs(int[][] bar) {
    int k0 = barHit(bar, "kick", 0);
    int k4 = barHit(bar, "kick", 4);
    int k6 = barHit(bar, "kick", 6);
    int k8 = barHit(bar, "kick", 8);
    int s4 = barHit(bar, "snare", 4);
    int s12 = barHit(bar, "snare", 12);
    if (k0 > 0 && k6 > 0 && k8 == 0 && k4 == 0 && s4 == 0 && s12 == 0) return new int[] { 6, 8 };
    if (k0 > 0 && k4 > 0 && k8 > 0 && barHit(bar, "kick", 12) == 0 && s12 == 0 && (s4 > 0 || barHit(bar, "snare", 8) > 0))
      return new int[] { 3, 4 };
    return new int[] { 4, 4 };
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

  private static int medianInt(List<Integer> xs) {
    if (xs.isEmpty()) return 0;
    Integer[] a = xs.toArray(new Integer[0]);
    Arrays.sort(a);
    return a[a.length / 2];
  }

  private static String majorityStr(List<String> xs) {
    if (xs.isEmpty()) return "";
    String best = xs.get(0);
    int n = 0;
    for (int i = 0; i < xs.size(); i++) {
      int c = 0;
      String v = xs.get(i);
      for (String x : xs) if (x.equals(v)) c++;
      if (c > n) { n = c; best = v; }
    }
    return best;
  }

  private static String densBand(float d) {
    if (d < 0.18f) return "low";
    if (d < 0.42f) return "mid";
    return "high";
  }

  private static int tomVoices(int[][] bar) {
    int n = 0;
    String[] ids = new String[] { "ltom", "mtom", "htom" };
    for (int i = 0; i < ids.length; i++) {
      int tr = Engine.track(ids[i]);
      if (bar == null || tr < 0 || tr >= bar.length || bar[tr] == null) continue;
      for (int v : bar[tr]) {
        if (v > 0) { n++; break; }
      }
    }
    return n;
  }

  private static void loudParts(PartAnalysis out, float[] pcm, int sr, int bpm) {
    int rate = sr > 0 ? sr : SR;
    int hop = Math.max(1, (int) (rate * 0.5f));
    int frames = Math.max(1, (pcm.length + hop - 1) / hop);
    float[] env = new float[frames];
    float max = 0;
    for (int f = 0; f < frames; f++) {
      int a = f * hop;
      int b = Math.min(pcm.length, a + hop);
      double s = 0;
      int c = 0;
      for (int i = a; i < b; i++) {
        s += (double) pcm[i] * pcm[i];
        c++;
      }
      float e = c == 0 ? 0 : (float) Math.sqrt(s / c);
      env[f] = e;
      if (e > max) max = e;
    }
    float dur = pcm.length / (float) rate;
    int bpmUse = Math.max(40, Math.min(240, bpm > 0 ? bpm : 120));
    float barSec = (60f / bpmUse) * 4f;
    java.util.ArrayList<String> kinds = new java.util.ArrayList<String>();
    java.util.ArrayList<float[]> spans = new java.util.ArrayList<float[]>();
    if (max < 1e-5f || frames < 4) {
      kinds.add("groove");
      spans.add(new float[] { 0f, dur });
    } else {
      float[] sm = new float[frames];
      for (int i = 0; i < frames; i++) {
        float sum = 0;
        int n = 0;
        for (int k = -2; k <= 2; k++) {
          int j = i + k;
          if (j < 0 || j >= frames) continue;
          sum += env[j];
          n++;
        }
        sm[i] = n == 0 ? 0 : sum / n;
      }
      float[] sorted = sm.clone();
      java.util.Arrays.sort(sorted);
      float body = Math.max(sorted[Math.round((sorted.length - 1) * 0.62f)], max * 0.2f);
      float floor = Math.max(body * 0.48f, max * 0.08f);
      int introFrames = 0;
      while (introFrames < frames && sm[introFrames] < floor) introFrames++;
      float introSec = (introFrames * (float) hop) / rate;
      if (introSec < 4f || dur - introSec < 6f) introSec = 0f;
      int outroFrames = frames;
      while (outroFrames > introFrames && sm[outroFrames - 1] < floor) outroFrames--;
      float outroSec = (outroFrames * (float) hop) / rate;
      if (dur - outroSec < 4f || outroSec - introSec < 6f) outroSec = dur;
      if (introSec >= 4f) {
        kinds.add("intro");
        spans.add(new float[] { 0f, introSec });
      }
      float bodyStart = introSec;
      float bodyEnd = outroSec;
      if (bodyEnd - bodyStart >= 0.4f) {
        int a = Math.min(frames - 1, (int) Math.floor((bodyStart * rate) / hop));
        int b = Math.max(a + 1, Math.min(frames, (int) Math.ceil((bodyEnd * rate) / hop)));
        String band = loudBand(sm[a], body);
        int from = a;
        java.util.ArrayList<String> runBand = new java.util.ArrayList<String>();
        java.util.ArrayList<float[]> runSpan = new java.util.ArrayList<float[]>();
        for (int i = a + 1; i <= b; i++) {
          String next = i < b ? loudBand(sm[i], body) : band;
          if (i == b || !next.equals(band)) {
            runBand.add(band);
            runSpan.add(new float[] { (from * (float) hop) / rate, (i * (float) hop) / rate });
            band = next;
            from = i;
          }
        }
        java.util.ArrayList<String> mergedB = new java.util.ArrayList<String>();
        java.util.ArrayList<float[]> mergedS = new java.util.ArrayList<float[]>();
        for (int i = 0; i < runBand.size(); i++) {
          float start = runSpan.get(i)[0];
          float end = runSpan.get(i)[1];
          if (!mergedS.isEmpty() && end - start < 8f) {
            mergedS.get(mergedS.size() - 1)[1] = end;
            continue;
          }
          if (!mergedB.isEmpty() && mergedB.get(mergedB.size() - 1).equals(runBand.get(i))) {
            mergedS.get(mergedS.size() - 1)[1] = end;
            continue;
          }
          mergedB.add(runBand.get(i));
          mergedS.add(new float[] { start, end });
        }
        if (mergedS.size() > 1 && mergedS.get(0)[1] - mergedS.get(0)[0] < 8f) {
          mergedS.get(1)[0] = mergedS.get(0)[0];
          mergedB.remove(0);
          mergedS.remove(0);
        }
        int last = mergedS.size() - 1;
        if (mergedS.size() > 1 && mergedS.get(last)[1] - mergedS.get(last)[0] < 8f) {
          mergedS.get(last - 1)[1] = mergedS.get(last)[1];
          mergedB.remove(last);
          mergedS.remove(last);
        }
        for (int i = 0; i < mergedB.size(); i++) {
          boolean opening = kinds.isEmpty() && mergedS.get(i)[0] < 1f;
          String rb = mergedB.get(i);
          String kind = "high".equals(rb) ? "chorus" : "low".equals(rb) ? (opening ? "intro" : "break") : "groove";
          kinds.add(kind);
          spans.add(new float[] { Math.max(bodyStart, mergedS.get(i)[0]), Math.min(bodyEnd, mergedS.get(i)[1]) });
        }
      }
      if (dur - outroSec >= 4f) {
        kinds.add("outro");
        spans.add(new float[] { outroSec, dur });
      }
      if (kinds.isEmpty()) {
        kinds.add("groove");
        spans.add(new float[] { 0f, dur });
      }
    }
    int grooves = 0;
    for (int i = 0; i < kinds.size(); i++) if ("groove".equals(kinds.get(i))) grooves++;
    for (int i = 0; i < kinds.size(); i++) {
      String kind = kinds.get(i);
      float start = spans.get(i)[0];
      float end = spans.get(i)[1];
      TrackPart p = new TrackPart();
      p.index = i;
      p.kind = kind;
      if ("groove".equals(kind) && grooves == 1) p.name = "Groove";
      else if ("intro".equals(kind)) p.name = "Intro";
      else if ("outro".equals(kind)) p.name = "Outro";
      else if ("chorus".equals(kind)) p.name = "Chorus " + (i + 1);
      else if ("break".equals(kind)) p.name = "Break " + (i + 1);
      else p.name = "Part " + (i + 1);
      p.bpm = bpmUse;
      p.tsNum = 4;
      p.tsDen = 4;
      p.styleId = "";
      p.styleLabel = "";
      p.startSec = Math.round(Math.max(0f, start) * 10f) / 10f;
      p.endSec = Math.round(Math.min(dur, Math.max(start, end)) * 10f) / 10f;
      p.bars = Math.max(1, Math.round(Math.max(0.1f, p.endSec - p.startSec) / barSec));
      p.density = 0;
      p.hits = 0;
      p.swing = 0;
      p.confidence = 0.45f;
      out.parts.add(p);
    }
  }

  private static String loudBand(float v, float body) {
    if (v < body * 0.55f) return "low";
    if (v > body * 1.32f) return "high";
    return "mid";
  }

  public static PartAnalysis analyzeParts(float[] samples, int sr) {
    Analysis a = analyze(samples, sr, false);
    PartAnalysis out = new PartAnalysis();
    float[] pcm = a.pcm != null ? a.pcm : samples;
    int useSr = a.pcm != null ? SR : sr;
    out.durationSec = pcm.length / (float) useSr;
    out.bpm = a.bpm;
    out.styleId = a.styleId;
    if (rimMajority(a.bars)) {
      loudParts(out, pcm, useSr, a.bpm);
      int[][] rep = representativeBar(a.bars);
      int per = rep == null ? 0 : Engine.hitCount(rep);
      for (int i = 0; i < out.parts.size(); i++) {
        TrackPart p = out.parts.get(i);
        String kind = p.kind == null ? "" : p.kind;
        if ("intro".equals(kind) || "outro".equals(kind) || "break".equals(kind) || per < 1) continue;
        p.cells = Engine.copyCells(rep);
        p.hits = per;
        p.density = Math.min(1f, per / 32f);
      }
      sourceLock(out);
      return out;
    }
    int drumHits = 0;
    if (!a.bars.isEmpty()) {
      for (int[][] bar : a.bars) {
        if (bar != null && bar.length > 0 && bar[0] != null) drumHits += Engine.hitCount(bar);
      }
    }
    if (a.bars.isEmpty() || drumHits < 8) {
      loudParts(out, pcm, useSr, a.bpm);
      sourceLock(out);
      return out;
    }
    int win = useSr * 8;
    int hop = useSr * 4;
    List<int[]> windows = new ArrayList<int[]>();
    for (int start = 0; start + useSr * 3 < pcm.length; start += hop) {
      int end = Math.min(pcm.length, start + win);
      float[] slice = Arrays.copyOfRange(pcm, start, end);
      int bpm = estimateTempo(slice, useSr);
      windows.add(new int[] { start, bpm });
    }
    if (a.bars.isEmpty()) {
      List<List<int[]>> chunks = new ArrayList<List<int[]>>();
      List<int[]> group = new ArrayList<int[]>();
      for (int i = 0; i < windows.size(); i++) {
        int[] w = windows.get(i);
        float mean = 0;
        for (int[] g : group) mean += g[1];
        if (!group.isEmpty()) mean /= group.size();
        boolean close = group.isEmpty() || Math.abs(w[1] - mean) < 10;
        int[] next = i + 1 < windows.size() ? windows.get(i + 1) : null;
        boolean blip = !close && next != null && Math.abs(next[1] - mean) < 10;
        if (close || blip) group.add(w);
        else {
          chunks.add(group);
          group = new ArrayList<int[]>();
          group.add(w);
        }
      }
      if (!group.isEmpty()) chunks.add(group);
      for (int i = 0; i < chunks.size(); i++) {
        List<int[]> chunk = chunks.get(i);
        List<Integer> bpms = new ArrayList<Integer>();
        for (int[] c : chunk) bpms.add(c[1]);
        int bpm = a.bpm;
        float startSec = Math.max(0, chunk.get(0)[0] / (float) useSr);
        float endSec = Math.min(out.durationSec, chunk.get(chunk.size() - 1)[0] / (float) useSr + 8);
        TrackPart p = new TrackPart();
        p.index = i;
        p.kind = chunks.size() <= 1 ? "groove" : (i == 0 ? "groove" : "groove");
        p.name = chunks.size() <= 1 ? "Groove" : ("Part " + (i + 1));
        p.bpm = bpm;
        p.tsNum = 4;
        p.tsDen = 4;
        p.styleId = out.styleId != null && out.styleId.length() > 0 ? out.styleId : closestStyleByBpm(bpm);
        p.styleLabel = titleStyle(p.styleId);
        p.startSec = Math.round(startSec * 10f) / 10f;
        p.endSec = Math.round(endSec * 10f) / 10f;
        p.bars = Math.max(1, Math.round((endSec - startSec) / ((60f / Math.max(40, bpm)) * 4f)));
        p.density = 0.2f;
        p.swing = swingFor(p.styleId);
        p.confidence = 0.4f;
        out.parts.add(p);
      }
      if (out.parts.isEmpty()) {
        TrackPart p = new TrackPart();
        p.name = "Groove";
        p.kind = "groove";
        p.bpm = a.bpm;
        p.tsNum = 4;
        p.tsDen = 4;
        p.styleId = a.styleId;
        p.styleLabel = titleStyle(a.styleId);
        p.endSec = out.durationSec;
        p.bars = Math.max(1, Math.round(out.durationSec / ((60f / Math.max(40, a.bpm)) * 4f)));
        p.swing = swingFor(a.styleId);
        p.confidence = 0.4f;
        out.parts.add(p);
      }
      sourceLock(out);
      return out;
    }
    class BarRow {
      int i, bpm, tsNum, tsDen, hits;
      String style, sig;
      float density;
      boolean fillish;
      int[][] cells;
    }
    List<BarRow> rows = new ArrayList<BarRow>();
    float barSecSong = (60f / Math.max(40, a.bpm)) * 4f;
    for (int i = 0; i < a.bars.size(); i++) {
      int[][] bar = a.bars.get(i);
      int[] ts = guessTs(bar);
      BarRow r = new BarRow();
      r.i = i;
      r.bpm = a.bpm;
      if (!windows.isEmpty()) {
        float t = i * barSecSong + barSecSong * 0.5f;
        float bestD = 1e9f;
        for (int[] w : windows) {
          float wt = w[0] / (float) useSr + 4f;
          float d = Math.abs(wt - t);
          if (d < bestD) { bestD = d; r.bpm = w[1]; }
        }
      }
      r.tsNum = ts[0];
      r.tsDen = ts[1];
      r.hits = Engine.hitCount(bar);
      r.density = Math.min(1f, r.hits / 32f);
      r.style = closestStyle(bar, a.bpm);
      r.sig = Engine.patternSignature(bar);
      r.fillish = tomVoices(bar) >= 2;
      r.cells = bar;
      rows.add(r);
    }
    List<List<BarRow>> runs = new ArrayList<List<BarRow>>();
    List<BarRow> cur = new ArrayList<BarRow>();
    cur.add(rows.get(0));
    for (int i = 1; i < rows.size(); i++) {
      BarRow next = rows.get(i);
      float mean = 0;
      for (BarRow r : cur) mean += r.bpm;
      mean /= cur.size();
      boolean bpmClose = Math.abs(next.bpm - mean) < 10;
      String bestTs = cur.get(0).tsNum + "/" + cur.get(0).tsDen;
      int bestC = 0;
      for (BarRow r : cur) {
        String k = r.tsNum + "/" + r.tsDen;
        int c = 0;
        for (BarRow x : cur) if ((x.tsNum + "/" + x.tsDen).equals(k)) c++;
        if (c > bestC) { bestC = c; bestTs = k; }
      }
      boolean tsSame = (next.tsNum + "/" + next.tsDen).equals(bestTs);
      BarRow next2 = i + 1 < rows.size() ? rows.get(i + 1) : null;
      boolean bpmBlip = !bpmClose && next2 != null && Math.abs(next2.bpm - mean) < 10;
      boolean tsBlip = !tsSame && (next2 == null || !(next2.tsNum + "/" + next2.tsDen).equals(next.tsNum + "/" + next.tsDen));
      if ((bpmClose || bpmBlip) && (tsSame || tsBlip || bpmBlip)) cur.add(next);
      else { runs.add(cur); cur = new ArrayList<BarRow>(); cur.add(next); }
    }
    runs.add(cur);
    List<List<BarRow>> peeled = new ArrayList<List<BarRow>>();
    for (List<BarRow> run : runs) {
      if (run.size() < 3) { peeled.add(run); continue; }
      List<BarRow> chunk = new ArrayList<BarRow>();
      chunk.add(run.get(0));
      for (int i = 1; i < run.size(); i++) {
        BarRow next = run.get(i);
        boolean chunkFill = true;
        for (BarRow r : chunk) if (!r.fillish) { chunkFill = false; break; }
        if (chunkFill && chunk.size() <= 2 && !next.fillish) {
          peeled.add(chunk);
          chunk = new ArrayList<BarRow>();
          chunk.add(next);
          continue;
        }
        if (!chunkFill && next.fillish && chunk.size() >= 2) {
          int j = i;
          while (j < run.size() && run.get(j).fillish && j - i < 2) j++;
          boolean shortFill = (j - i) <= 2 && (j >= run.size() || !run.get(j).fillish);
          if (shortFill) {
            peeled.add(chunk);
            chunk = new ArrayList<BarRow>();
            chunk.add(next);
            continue;
          }
        }
        chunk.add(next);
      }
      peeled.add(chunk);
    }
    List<List<BarRow>> signed = new ArrayList<List<BarRow>>();
    for (List<BarRow> run : peeled) {
      if (run.size() < 6) { signed.add(run); continue; }
      List<BarRow> chunk = new ArrayList<BarRow>();
      chunk.add(run.get(0));
      for (int i = 1; i < run.size(); i++) {
        List<String> sigs = new ArrayList<String>();
        for (BarRow r : chunk) sigs.add(r.sig);
        String prev = majorityStr(sigs);
        BarRow next = run.get(i);
        if (!next.sig.equals(prev) && chunk.size() >= 4 && run.size() - i >= 3) {
          boolean hold = true;
          for (int k = i; k < Math.min(run.size(), i + 3); k++) {
            if (!run.get(k).sig.equals(next.sig)) { hold = false; break; }
          }
          if (hold) {
            signed.add(chunk);
            chunk = new ArrayList<BarRow>();
            chunk.add(next);
            continue;
          }
        }
        chunk.add(next);
      }
      signed.add(chunk);
    }
    List<List<BarRow>> split = new ArrayList<List<BarRow>>();
    for (List<BarRow> run : signed) {
      if (run.size() < 6) { split.add(run); continue; }
      List<BarRow> chunk = new ArrayList<BarRow>();
      chunk.add(run.get(0));
      for (int i = 1; i < run.size(); i++) {
        String band = densBand(run.get(i).density);
        String prev = densBand(chunk.get(chunk.size() - 1).density);
        if (!band.equals(prev) && chunk.size() >= 3 && run.size() - i >= 3) {
          boolean hold = true;
          for (int k = i; k < Math.min(run.size(), i + 3); k++) {
            if (!densBand(run.get(k).density).equals(band)) { hold = false; break; }
          }
          if (hold) {
            split.add(chunk);
            chunk = new ArrayList<BarRow>();
            chunk.add(run.get(i));
            continue;
          }
        }
        chunk.add(run.get(i));
      }
      split.add(chunk);
    }
    float t = 0;
    for (int ri = 0; ri < split.size(); ri++) {
      List<BarRow> run = split.get(ri);
      int hits = 0;
      float dens = 0;
      List<Integer> bpms = new ArrayList<Integer>();
      List<String> styles = new ArrayList<String>();
      List<String> tss = new ArrayList<String>();
      for (BarRow r : run) {
        hits += r.hits;
        dens += r.density;
        bpms.add(r.bpm);
        styles.add(r.style);
        tss.add(r.tsNum + "/" + r.tsDen);
      }
      dens = Math.min(1f, dens / run.size());
      int bpm = a.bpm;
      String ts = majorityStr(tss);
      int tsNum = 4, tsDen = 4;
      int slash = ts.indexOf('/');
      if (slash > 0) {
        try {
          tsNum = Integer.parseInt(ts.substring(0, slash));
          tsDen = Integer.parseInt(ts.substring(slash + 1));
        } catch (Exception ignored) {}
      }
      String style = majorityStr(styles);
      String band = densBand(dens);
      boolean fillish = run.size() <= 2;
      for (BarRow r : run) if (!r.fillish) fillish = false;
      String kind = "groove";
      if (fillish && run.size() <= 2) kind = "fill";
      else if (ri == 0 && "low".equals(band)) kind = "intro";
      else if (ri == split.size() - 1 && "low".equals(band)) kind = "outro";
      else if ("high".equals(band) && split.size() > 1) kind = "chorus";
      else if ("low".equals(band) && ri > 0 && ri < split.size() - 1) kind = "break";
      String name = "Part " + (ri + 1);
      if ("intro".equals(kind)) name = "Intro";
      else if ("outro".equals(kind)) name = "Outro";
      else if ("fill".equals(kind)) name = "Fill " + (ri + 1);
      else if ("chorus".equals(kind)) name = "Chorus " + (ri + 1);
      else if ("break".equals(kind)) name = "Break " + (ri + 1);
      else if (split.size() <= 1) name = "Groove";
      float dur = 0;
      for (BarRow r : run) dur += (r.tsNum * (16f / r.tsDen)) * (60f / Math.max(40, a.bpm) / 4f);
      TrackPart p = new TrackPart();
      p.index = ri;
      p.name = name;
      p.kind = kind;
      p.startSec = Math.round(t * 10f) / 10f;
      p.endSec = Math.round((t + dur) * 10f) / 10f;
      p.bars = run.size();
      p.bpm = bpm;
      p.tsNum = tsNum;
      p.tsDen = tsDen;
      p.styleId = style;
      p.styleLabel = titleStyle(style);
      p.hits = hits;
      p.density = Math.round(dens * 100f) / 100f;
      p.swing = swingFor(style);
      p.confidence = 0.55f;
      int[][] cells = null;
      if (fillish) {
        int bestHits = -1;
        for (BarRow r : run) {
          if (r.hits > bestHits) { bestHits = r.hits; cells = r.cells; }
        }
      } else {
        List<String> sigs = new ArrayList<String>();
        for (BarRow r : run) sigs.add(r.sig);
        String bestSig = majorityStr(sigs);
        for (BarRow r : run) {
          if (bestSig.equals(r.sig)) { cells = r.cells; break; }
        }
      }
      p.cells = cells != null ? Engine.copyCells(cells) : null;
      out.parts.add(p);
      t += dur;
    }
    sourceLock(out);
    return out;
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
    for (int i = 0; i < a.parts.size(); i++) {
      TrackPart p = a.parts.get(i);
      TrackPart next = i + 1 < a.parts.size() ? a.parts.get(i + 1) : null;
      int[][] gp = restyle ? composedGroove(p, i) : (source ? sourceCells(p) : grooveCells(p));
      String pname = uniqueSetName(p.name == null || p.name.isEmpty() ? ("Part " + (p.index + 1)) : p.name, usedP);
      Engine.Learned item = new Engine.Learned();
      item.name = pname;
      item.bpm = Math.max(40, Math.min(240, p.bpm));
      item.closest = source ? "" : p.styleId;
      item.cells = gp;
      item.tsNum = p.tsNum;
      item.tsDen = p.tsDen;
      set.patterns.add(item);
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
      String kind = midiPartKind(i, n, nBars, density, fillish);
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

  public static int voiceIndex(String track) {
    return trackIndex(track);
  }

  /** Isolation, Analyze, and Compose: no style name, no style swing. Timing stays on the file. */
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
    return "analyze".equals(origin) || "isolate".equals(origin) || "compose".equals(origin);
  }

  private static int[][] sourceCells(TrackPart p) {
    if (p != null && p.cells != null) return Engine.copyCells(p.cells);
    return Engine.emptyCells();
  }

  public static String closestStyle(int[][] cells, int bpm) {
    return StyleDb.suggest(cells, bpm);
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

  private static float[] resample(float[] src, int srcSr, int dstSr, int srcN) {
    double ratio = srcSr / (double) dstSr;
    int n = Math.max(1, (int) Math.floor(srcN / ratio));
    float[] o = new float[n];
    for (int i = 0; i < n; i++) {
      int si = Math.min(srcN - 1, (int) Math.floor(i * ratio));
      o[i] = src[si];
    }
    return o;
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

  private static float[] hp(float[] x, int sr, float hz) {
    float[] l = lp(x, sr, hz);
    float[] y = new float[x.length];
    for (int i = 0; i < x.length; i++) y[i] = x[i] - l[i];
    return y;
  }

  private static float[] lpN(float[] x, int sr, float hz, int poles) {
    float[] y = x;
    for (int i = 0; i < poles; i++) y = lp(y, sr, hz);
    return y;
  }

  private static float[] hpN(float[] x, int sr, float hz, int poles) {
    float[] y = x;
    for (int i = 0; i < poles; i++) y = hp(y, sr, hz);
    return y;
  }

  private static float pcmRms(float[] x) {
    if (x == null || x.length == 0) return 0f;
    double s = 0;
    for (int i = 0; i < x.length; i++) s += (double) x[i] * x[i];
    return (float) Math.sqrt(s / x.length);
  }

  private static float[] limitPeak(float[] x) {
    float p = 0f;
    for (int i = 0; i < x.length; i++) p = Math.max(p, Math.abs(x[i]));
    if (p <= 0.98f || p < 1e-8f) return x;
    float g = 0.98f / p;
    for (int i = 0; i < x.length; i++) x[i] *= g;
    return x;
  }

  private interface Reader {
    int length();
    float at(int i);
  }

  /** Cascaded one-pole. Sample-by-sample matches a full-buffer lpN / hpN pass. */
  private static final class Cascade {
    final float a;
    final float[] z;
    final boolean high;

    Cascade(int sr, float hz, int poles, boolean high) {
      this.a = (float) (1 - Math.exp(-2 * Math.PI * hz / Math.max(1, sr)));
      this.z = new float[Math.max(1, poles)];
      this.high = high;
    }

    float next(float x) {
      float y = x;
      for (int p = 0; p < z.length; p++) {
        z[p] += a * (y - z[p]);
        y = high ? y - z[p] : z[p];
      }
      return y;
    }

    void reset() {
      for (int i = 0; i < z.length; i++) z[i] = 0f;
    }
  }

  private static final class Attack {
    final float[] env;
    final float release;
    float held;
    int fi;

    Attack(float[] env, int sr) {
      this.env = env == null ? new float[0] : env;
      this.release = (float) Math.exp(-1.0 / (Math.max(1, sr) * 0.045));
    }

    void reset() {
      held = 0f;
      fi = 0;
    }

    float next(int i) {
      if (i % 128 == 0 && fi < env.length) {
        float level = Math.max(env[fi], fi > 0 ? env[fi - 1] : 0f);
        if (level < 1e-4f) level = 1e-4f;
        float flux = fi == 0 ? 0f : Math.max(0f, env[fi] - env[fi - 1]);
        float kick = Math.max(0f, Math.min(1f, (flux / level - 0.15f) / 0.45f));
        if (kick > held) held = kick;
        fi++;
      }
      float a = held;
      held *= release;
      return a;
    }
  }

  private static final class WavHead {
    int sr = 44100;
    int ch = 1;
    int bits = 16;
    int dataAt = -1;
    int dataLen;
    int bps = 2;
    int frame = 2;
    int frames;
  }

  private static WavHead readWavHead(byte[] d) {
    WavHead h = new WavHead();
    int i = 12;
    while (i + 8 <= d.length) {
      String id = four(d, i);
      int size = u32(d, i + 4);
      int ds = i + 8;
      int de = Math.min(d.length, ds + Math.max(0, size));
      if ("fmt ".equals(id) && size >= 16) {
        h.ch = Math.max(1, u16(d, ds + 2));
        h.sr = Math.max(8000, u32(d, ds + 4));
        h.bits = u16(d, ds + 14);
      } else if ("data".equals(id)) {
        h.dataAt = ds;
        h.dataLen = Math.max(0, de - ds);
        break;
      }
      i = de + (size & 1);
    }
    h.bps = Math.max(1, h.bits / 8);
    h.frame = Math.max(1, h.ch * h.bps);
    h.frames = h.dataLen / h.frame;
    return h;
  }

  private static float wavAt(WavHead h, byte[] d, int ch, int i, int workSr) {
    if (h.frames <= 0) return 0f;
    if (h.sr == workSr) return frameSample(h, d, ch, i);
    double src = i * (h.sr / (double) workSr);
    int i0 = (int) Math.floor(src);
    float frac = (float) (src - i0);
    if (i0 >= h.frames - 1) return frameSample(h, d, ch, h.frames - 1);
    float a = frameSample(h, d, ch, Math.max(0, i0));
    float b = frameSample(h, d, ch, i0 + 1);
    return a + (b - a) * frac;
  }

  private static float frameSample(WavHead h, byte[] d, int ch, int frame) {
    if (frame < 0) frame = 0;
    if (frame >= h.frames) frame = h.frames - 1;
    int p = h.dataAt + frame * h.frame + Math.max(0, ch) * h.bps;
    if (p < 0 || p >= d.length) return 0f;
    if (h.bits <= 8) return ((d[p] & 0xff) - 128) / 128f;
    if (p + 1 >= d.length) return 0f;
    int v = (short) ((d[p] & 0xff) | (d[p + 1] << 8));
    return v / 32768f;
  }

  private static float[] attackEnv(Reader x, int sr) {
    int n = x.length();
    int hop = 128;
    int win = 512;
    int frames = Math.max(1, (n - win) / hop);
    float[] env = new float[Math.max(1, frames)];
    if (n < win) {
      Cascade hp = new Cascade(sr, 180f, 1, true);
      double s = 0;
      for (int i = 0; i < n; i++) {
        float v = hp.next(x.at(i));
        s += (double) v * v;
      }
      env[0] = (float) Math.sqrt(s / win);
      return env;
    }
    Cascade hp = new Cascade(sr, 180f, 1, true);
    float[] ring = new float[win];
    int fi = 0;
    for (int i = 0; i < n && fi < env.length; i++) {
      ring[i % win] = hp.next(x.at(i));
      if (i >= win - 1 && (i - (win - 1)) % hop == 0) {
        double s = 0;
        for (int j = 0; j < win; j++) s += (double) ring[j] * ring[j];
        env[fi++] = (float) Math.sqrt(s / win);
      }
    }
    return env;
  }

  private static float[] removeMonoRead(Reader x, int sr) {
    int n = x.length();
    if (n <= 0) return new float[0];
    Attack atk = new Attack(attackEnv(x, sr), sr);
    float[] out = new float[n];
    Cascade body = new Cascade(sr, 240f, 2, false);
    Cascade air = new Cascade(sr, 7000f, 2, true);
    Cascade vLp = new Cascade(sr, 3800f, 2, false);
    Cascade vHp = new Cascade(sr, 280f, 2, true);
    for (int i = 0; i < n; i++) {
      float s = x.at(i);
      float a = atk.next(i);
      float voc = vHp.next(vLp.next(s));
      out[i] = body.next(s) * 1.4f + air.next(s) * 0.18f + voc * (0.03f + 0.62f * a);
    }
    tameVocalInPlace(out, sr, atk);
    return limitPeak(out);
  }

  private static float[] removeStereoOrMono(Reader left, Reader right, int sr) {
    int n = left.length();
    double se = 0;
    double me = 0;
    for (int i = 0; i < n; i++) {
      float mid = (left.at(i) + right.at(i)) * 0.5f;
      float side = (left.at(i) - right.at(i)) * 0.5f;
      me += (double) mid * mid;
      se += (double) side * side;
    }
    float sideR = (float) Math.sqrt(se / Math.max(1, n));
    float midR = (float) Math.sqrt(me / Math.max(1, n));
    if (sideR < midR * 0.045f) {
      Reader mid = new Reader() {
        public int length() { return left.length(); }
        public float at(int i) { return (left.at(i) + right.at(i)) * 0.5f; }
      };
      return removeMonoRead(mid, sr);
    }
    float[] out = new float[n];
    Cascade low = new Cascade(sr, 200f, 3, false);
    Cascade air = new Cascade(sr, 7000f, 3, true);
    Cascade vLp = new Cascade(sr, 4000f, 2, false);
    Cascade vHp = new Cascade(sr, 280f, 2, true);
    for (int i = 0; i < n; i++) {
      float mid = (left.at(i) + right.at(i)) * 0.5f;
      out[i] = low.next(mid) + air.next(mid) * 0.4f + vHp.next(vLp.next(mid)) * 0.04f;
    }
    tameVocalInPlace(out, sr, null);
    for (int i = 0; i < n; i++) {
      float side = (left.at(i) - right.at(i)) * 0.5f;
      out[i] = side * 1.25f + out[i];
    }
    return limitPeak(out);
  }

  /** Pull a sustained vocal out of the formant band. `atk` keeps pick attacks. */
  private static void tameVocalInPlace(float[] y, int sr, Attack atk) {
    if (y == null || y.length == 0) return;
    for (int pass = 0; pass < 3; pass++) {
      Cascade body = new Cascade(sr, 240f, 2, false);
      Cascade vLp = new Cascade(sr, 3800f, 2, false);
      Cascade vHp = new Cascade(sr, 280f, 2, true);
      double bs = 0;
      double vs = 0;
      for (int i = 0; i < y.length; i++) {
        float b = body.next(y[i]);
        float v = vHp.next(vLp.next(y[i]));
        bs += (double) b * b;
        vs += (double) v * v;
      }
      float br = (float) Math.sqrt(bs / y.length);
      float vr = (float) Math.sqrt(vs / y.length);
      if (!(vr > Math.max(br * 0.35f, 1e-5f))) break;
      float g = Math.min(0.22f, (Math.max(br, 1e-6f) * 0.18f) / vr);
      vLp.reset();
      vHp.reset();
      if (atk != null) atk.reset();
      for (int i = 0; i < y.length; i++) {
        float keep = atk == null ? 0f : atk.next(i);
        float cut = (1f - g) * (1f - keep);
        float v = vHp.next(vLp.next(y[i]));
        y[i] = y[i] - v * cut;
      }
    }
  }

  private static float[] removeVocalsMono(float[] x, int sr) {
    final float[] src = x;
    return removeMonoRead(new Reader() {
      public int length() { return src.length; }
      public float at(int i) { return src[i]; }
    }, sr);
  }

  private static float[] frameRms(float[] x, int hop, int win) {
    int n = Math.max(1, (x.length - win) / hop);
    float[] e = new float[n];
    for (int i = 0; i < n; i++) {
      int o = i * hop;
      double s = 0;
      for (int j = 0; j < win && o + j < x.length; j++) s += x[o + j] * x[o + j];
      e[i] = (float) Math.sqrt(s / win);
    }
    return e;
  }

  private static float[] flux(float[] e) {
    float[] o = new float[e.length];
    for (int i = 1; i < e.length; i++) o[i] = Math.max(0, e[i] - e[i - 1]);
    return o;
  }

  private static float[] smooth(float[] e) {
    float[] y = new float[e.length];
    for (int i = 0; i < e.length; i++) {
      float s = 0;
      int n = 0;
      for (int k = -3; k <= 3; k++) {
        int j = i + k;
        if (j < 0 || j >= e.length) continue;
        s += e[j];
        n++;
      }
      y[i] = n > 0 ? s / n : 0;
    }
    return y;
  }

  private static float autocorrBpm(float[] onset, float fps, int minBpm, int maxBpm) {
    int minLag = Math.max(2, Math.round((fps * 60) / maxBpm));
    int maxLag = Math.min(onset.length - 2, Math.round((fps * 60) / minBpm));
    int bestLag = minLag;
    float best = -1;
    for (int lag = minLag; lag <= maxLag; lag++) {
      float s = 0;
      for (int i = 0; i < onset.length - lag; i++) s += onset[i] * onset[i + lag];
      if (s > best) {
        best = s;
        bestLag = lag;
      }
    }
    return (60f * fps) / bestLag;
  }

  private static int pickBpm(float raw, float hatDensity) {
    float bpm = raw;
    if (bpm < 58) bpm *= 2;
    if (bpm > 200) bpm /= 2;
    if (bpm >= 150 && bpm <= 200 && hatDensity < 0.4f) bpm /= 2;
    if (bpm >= 58 && bpm <= 78 && hatDensity > 0.55f) bpm *= 2;
    return Engine.clamp(Math.round(bpm), 60, 200);
  }

  private static float[] fold(float[] onset, float stepFrames, int phase) {
    float[] acc = new float[16];
    for (int i = 0; i < onset.length; i++) {
      int step = Math.round((i - phase) / stepFrames);
      if (step < 0) continue;
      acc[((step % 16) + 16) % 16] += onset[i];
    }
    return acc;
  }

  private static int[] toHits(float[] acc, float floor) {
    float max = 0;
    float sum = 0;
    for (int i = 0; i < 16; i++) {
      max = Math.max(max, acc[i]);
      sum += acc[i];
    }
    float mean = sum / 16f;
    float thresh = mean + (max - mean) * floor;
    int[] hits = new int[16];
    if (max <= 1e-8f || max < mean * 1.35f) return hits;
    for (int i = 0; i < 16; i++) {
      float left = acc[(i + 15) % 16];
      float right = acc[(i + 1) % 16];
      if (acc[i] < thresh || acc[i] < left || acc[i] < right) continue;
      hits[i] = Engine.clamp(Math.round(70 + (acc[i] / max) * 57), 64, 127);
    }
    return hits;
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

  private static byte[] slice(byte[] d, int a, int size) {
    int n = Math.max(0, Math.min(size, d.length - a));
    byte[] o = new byte[n];
    System.arraycopy(d, a, o, 0, n);
    return o;
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
