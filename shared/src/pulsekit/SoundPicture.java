package pulsekit;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * What the playlist viewer shows while a sound plays: a drum MIDI's hits on one row per drum (as
 * the MIDI editor shows them), another MIDI's notes by pitch, a WAV's or MP3's waveform. Pixels
 * (ARGB, w × h) for a Bitmap on the phone and a BufferedImage on the computer.
 */
public final class SoundPicture {
  private SoundPicture() {}

  /** An MP3 decoder where the app has one (the computer's Mp3Decode); none: an MP3 is not drawn. */
  public interface Decoder {
    AudioIo.Pcm decode(byte[] bytes) throws Exception;
  }

  public static volatile Decoder mp3;

  /** Larger sounds are not drawn (the whole file would be read into memory). */
  public static final int MAX_BYTES = 30 * 1024 * 1024;

  private static final int BACK = 0xff0e0f11;
  private static final int GRID = 0xff24262a;
  private static final int BAR = 0xff3a3d42;
  private static final int WAVE = 0xff8fb8de;
  private static final int[] LANES = {0xffe0b04a, 0xffd9734a, 0xff8fb8de, 0xff9fcf8a, 0xffc79be0, 0xffe07a9a, 0xff7ad6c8, 0xffecebe6};

  /** The picture, or null when the file is not a MIDI, WAV or MP3 it can draw. */
  public static int[] pixels(String name, byte[] bytes, int w, int h) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES || w < 8 || h < 8) return null;
    try {
      if (MediaDir.isMidi(name) || bytes.length > 4 && bytes[0] == 'M' && bytes[1] == 'T' && bytes[2] == 'h' && bytes[3] == 'd') return midi(bytes, w, h);
      String low = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
      AudioIo.Pcm pcm = low.endsWith(".mp3") ? (mp3 == null ? null : mp3.decode(bytes)) : AudioIo.parseWav(bytes);
      return pcm == null ? null : wave(pcm.samples, w, h);
    } catch (Throwable ex) {
      return null;
    }
  }

  static int[] wave(float[] s, int w, int h) {
    if (s == null || s.length == 0) return null;
    int[] px = fill(w, h);
    int mid = h / 2;
    for (int x = 0; x < w; x++) px[mid * w + x] = GRID;
    long from = 0;
    for (int x = 0; x < w; x++) {
      long to = (long) s.length * (x + 1) / w;
      float lo = 0, hi = 0;
      for (long i = from; i < to; i++) {
        float v = s[(int) i];
        if (v < lo) lo = v;
        if (v > hi) hi = v;
      }
      from = to;
      int top = clamp(mid - Math.round(hi * (h / 2 - 2)), 0, h - 1);
      int bottom = clamp(mid - Math.round(lo * (h / 2 - 2)), 0, h - 1);
      for (int y = top; y <= bottom; y++) px[y * w + x] = WAVE;
    }
    return px;
  }

  /** {tick, note, channel} of every note, and the MIDI's ticks a quarter and last tick. */
  static int[] midi(byte[] d, int w, int h) {
    List<int[]> notes = new ArrayList<int[]>();
    int tpq = ((d[12] & 0xff) << 8) | (d[13] & 0xff);
    if ((tpq & 0x8000) != 0 || tpq == 0) tpq = 480;
    int tsNum = 4, tsDen = 4;
    long last = 0;
    int i = 8 + be32(d, 4);
    while (i + 8 <= d.length) {
      int len = be32(d, i + 4);
      boolean track = d[i] == 'M' && d[i + 1] == 'T' && d[i + 2] == 'r' && d[i + 3] == 'k';
      int p = i + 8;
      int end = (int) Math.min((long) d.length, (long) p + Math.max(0, len));
      if (track) {
        long tick = 0;
        int status = 0;
        while (p < end) {
          long v = 0;
          for (int k = 0; k < 4 && p < end; k++) {
            int b = d[p++] & 0xff;
            v = (v << 7) | (b & 0x7f);
            if ((b & 0x80) == 0) break;
          }
          tick += v;
          if (p >= end) break;
          int st = d[p] & 0xff;
          if (st >= 0x80) p++;
          else st = status;
          if (st == 0xff || st == 0xf0 || st == 0xf7) {
            int type = st == 0xff && p < end ? d[p++] & 0xff : -1;
            long n = 0;
            for (int k = 0; k < 4 && p < end; k++) {
              int b = d[p++] & 0xff;
              n = (n << 7) | (b & 0x7f);
              if ((b & 0x80) == 0) break;
            }
            if (type == 0x58 && n >= 2 && p + 1 < end) {
              tsNum = Math.max(1, d[p] & 0xff);
              tsDen = 1 << Math.min(6, d[p + 1] & 0xff);
            }
            p += (int) n;
            continue;
          }
          status = st;
          int cmd = st & 0xf0;
          if (cmd == 0xc0 || cmd == 0xd0) {
            p++;
            continue;
          }
          if (p + 1 >= end) break;
          int note = d[p] & 0xff;
          int vel = d[p + 1] & 0xff;
          p += 2;
          if (cmd == 0x90 && vel > 0) {
            notes.add(new int[] {(int) tick, note, st & 0x0f});
            last = Math.max(last, tick);
          }
        }
      }
      i = end;
    }
    if (notes.isEmpty()) return null;
    int drums = 0;
    for (int[] n : notes) if (n[2] == 9) drums++;
    boolean drumMidi = drums * 2 >= notes.size();
    // Rows: a drum MIDI's drums (one row each, low notes at the bottom), else its pitches.
    TreeSet<Integer> rows = new TreeSet<Integer>();
    for (int[] n : notes) if (!drumMidi || n[2] == 9) rows.add(n[1]);
    List<Integer> order = new ArrayList<Integer>(rows);
    int lo = order.get(0), hi = order.get(order.size() - 1);
    int count = drumMidi ? order.size() : hi - lo + 1;
    long span = Math.max(1, last + tpq / 2);
    int[] px = fill(w, h);
    // Bars and beats.
    double beat = tpq * 4.0 / tsDen;
    for (int b = 0; b * beat <= span; b++) {
      int x = (int) (b * beat * w / span);
      if (x >= w) break;
      int c = b % tsNum == 0 ? BAR : GRID;
      for (int y = 0; y < h; y++) px[y * w + x] = c;
    }
    double rowH = h / (double) Math.max(1, count);
    int bw = Math.max(2, (int) Math.round(w * (tpq / 4.0) / span));
    for (int[] n : notes) {
      if (drumMidi && n[2] != 9) continue;
      int row = drumMidi ? order.indexOf(n[1]) : n[1] - lo;
      int y0 = (int) (h - (row + 1) * rowH) + 1;
      int y1 = (int) (h - row * rowH) - 1;
      int x0 = (int) ((long) n[0] * w / span);
      int color = LANES[(drumMidi ? row : n[2]) % LANES.length];
      for (int y = Math.max(0, y0); y <= Math.min(h - 1, Math.max(y0, y1)); y++) {
        for (int x = x0; x < Math.min(w, x0 + bw); x++) px[y * w + x] = color;
      }
    }
    return px;
  }

  private static int[] fill(int w, int h) {
    int[] px = new int[w * h];
    java.util.Arrays.fill(px, BACK);
    return px;
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static int be32(byte[] d, int at) {
    return ((d[at] & 0xff) << 24) | ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
  }
}
