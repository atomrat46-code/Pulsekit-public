package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * A MIDI file's length and kind for its thumbnail: mm:ss (tempo changes counted), bars and beats
 * (its first time signature), its tempo, and whether it is a drum MIDI (its notes on channel 10).
 */
public final class MidiInfo {
  public double seconds;
  public int bars;
  public int beats;
  public int bpm = 120;
  public int tsNum = 4;
  public int tsDen = 4;
  public int drumNotes;
  public int otherNotes;

  private MidiInfo() {}

  /** A drum MIDI: most of its notes on channel 10. */
  public boolean drums() {
    return drumNotes > 0 && drumNotes >= otherNotes;
  }

  /** "1:32 · 46 bars · 184 beats · 120 BPM 4/4 · drum MIDI". */
  public String line() {
    return Playlist.clock((long) Math.round(seconds * 1000)) + " · " + bars + (bars == 1 ? " bar" : " bars") + " · " + beats + (beats == 1 ? " beat" : " beats")
        + " · " + bpm + " BPM " + tsNum + "/" + tsDen + " · " + (drums() ? "drum MIDI" : drumNotes > 0 ? "MIDI with drums" : "no drums");
  }

  /** The file read; null when it is not a MIDI file (or has no notes). */
  public static MidiInfo of(byte[] d) {
    if (d == null || d.length < 14 || d[0] != 'M' || d[1] != 'T' || d[2] != 'h' || d[3] != 'd') return null;
    try {
      int hlen = be32(d, 4);
      int tpq = ((d[12] & 0xff) << 8) | (d[13] & 0xff);
      if ((tpq & 0x8000) != 0 || tpq == 0) tpq = 480;
      MidiInfo info = new MidiInfo();
      // Tempo changes: {tick, microseconds per quarter}.
      List<long[]> tempos = new ArrayList<long[]>();
      boolean tsSeen = false;
      long lastTick = 0;
      int i = 8 + hlen;
      while (i + 8 <= d.length) {
        int len = be32(d, i + 4);
        boolean track = d[i] == 'M' && d[i + 1] == 'T' && d[i + 2] == 'r' && d[i + 3] == 'k';
        int p = i + 8;
        int end = (int) Math.min((long) d.length, (long) p + Math.max(0, len));
        if (track) {
          long tick = 0;
          int status = 0;
          while (p < end) {
            long[] v = varLen(d, p);
            tick += v[0];
            p = (int) v[1];
            if (p >= end) break;
            int st = d[p] & 0xff;
            if (st >= 0x80) p++;
            else st = status;
            if (st == 0xff) {
              if (p >= end) break;
              int type = d[p++] & 0xff;
              long[] l = varLen(d, p);
              p = (int) l[1];
              int n = (int) l[0];
              if (type == 0x51 && n >= 3 && p + 2 < end) tempos.add(new long[] {tick, ((d[p] & 0xff) << 16) | ((d[p + 1] & 0xff) << 8) | (d[p + 2] & 0xff)});
              if (type == 0x58 && n >= 2 && p + 1 < end && !tsSeen) {
                info.tsNum = Math.max(1, d[p] & 0xff);
                info.tsDen = 1 << Math.min(6, d[p + 1] & 0xff);
                tsSeen = true;
              }
              if (type != 0x2f) lastTick = Math.max(lastTick, tick);
              p += n;
              continue;
            }
            if (st == 0xf0 || st == 0xf7) {
              long[] l = varLen(d, p);
              p = (int) l[1] + (int) l[0];
              continue;
            }
            status = st;
            int cmd = st & 0xf0;
            int ch = st & 0x0f;
            if (cmd == 0xc0 || cmd == 0xd0) {
              p++;
            } else {
              if (p + 1 >= end) break;
              int vel = d[p + 1] & 0xff;
              if (cmd == 0x90 && vel > 0) {
                if (ch == 9) info.drumNotes++;
                else info.otherNotes++;
              }
              p += 2;
            }
            lastTick = Math.max(lastTick, tick);
          }
        }
        i = end;
      }
      if (info.drumNotes + info.otherNotes == 0) return null;
      // The time: tick by tick through the tempo changes.
      java.util.Collections.sort(tempos, (a, b) -> Long.compare(a[0], b[0]));
      double us = 0;
      long at = 0;
      long tempo = 500000;
      for (long[] t : tempos) {
        if (t[0] > lastTick) break;
        us += (t[0] - at) * (double) tempo / tpq;
        at = t[0];
        tempo = t[1];
      }
      us += (lastTick - at) * (double) tempo / tpq;
      info.seconds = us / 1e6;
      long first = tempos.isEmpty() ? 500000 : tempos.get(0)[1];
      info.bpm = (int) Math.round(60000000.0 / Math.max(1, first));
      double beatTicks = tpq * 4.0 / info.tsDen;
      info.beats = (int) Math.ceil(lastTick / beatTicks);
      info.bars = (int) Math.ceil(info.beats / (double) info.tsNum);
      return info;
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static int be32(byte[] d, int at) {
    return ((d[at] & 0xff) << 24) | ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
  }

  /** {value, the index after it}. */
  private static long[] varLen(byte[] d, int p) {
    long v = 0;
    for (int k = 0; k < 4 && p < d.length; k++) {
      int b = d[p++] & 0xff;
      v = (v << 7) | (b & 0x7f);
      if ((b & 0x80) == 0) break;
    }
    return new long[] {v, p};
  }
}
