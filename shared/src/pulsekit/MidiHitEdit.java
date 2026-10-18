package pulsekit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The hits of a file set's source MIDI on a grid (long press on source.mid): an ACC row and one row
 * per track, a column for each 16th of the song. A tap adds or erases a hit; everything else in the
 * file (tempo, time signature, other channels, the timing of the hits not touched) stays as it was.
 * Save writes it back as the file set's MIDI.
 */
public final class MidiHitEdit {
  /** Velocity of a hit added, and of an accented step's hits. */
  public static final int HIT = 100;
  public static final int ACCENTED = 127;
  public static final int UNACCENTED = 96;
  /** A step is accented when a hit in it is this loud. */
  public static final int ACCENT_FROM = 112;

  private static final class Ev {
    int tick;
    int order;
    /** Note events: status (0x80/0x90 | channel), note, velocity. Others: their bytes after the delta time. */
    int status;
    int note;
    int vel;
    byte[] raw;

    boolean on() {
      return (this.status & 0xf0) == 0x90 && this.vel > 0;
    }

    boolean off() {
      return (this.status & 0xf0) == 0x80 || ((this.status & 0xf0) == 0x90 && this.vel == 0);
    }
  }

  public final int tpq;
  public final int bpm;
  public final int tsNum;
  public final int tsDen;
  public final int barSteps;
  private final List<Ev> evs = new ArrayList<Ev>();
  private int nextOrder;
  private int steps;
  public boolean changed;

  private MidiHitEdit(int tpq, int bpm, int tsNum, int tsDen) {
    this.tpq = tpq;
    this.bpm = bpm;
    this.tsNum = tsNum;
    this.tsDen = tsDen;
    this.barSteps = Engine.barSteps(tsNum, tsDen);
  }

  /** The MIDI on a grid; null when it is not a MIDI file. */
  public static MidiHitEdit of(byte[] midi) {
    if (midi == null || midi.length < 14 || !"MThd".equals(new String(midi, 0, 4, StandardCharsets.ISO_8859_1))) return null;
    int tpq = ((midi[12] & 0xff) << 8) | (midi[13] & 0xff);
    if ((tpq & 0x8000) != 0 || tpq <= 0) tpq = Engine.TPQ;
    int tsNum = 4;
    int tsDen = 4;
    List<Ev> all = new ArrayList<Ev>();
    int i = 8 + (((midi[4] & 0xff) << 24) | ((midi[5] & 0xff) << 16) | ((midi[6] & 0xff) << 8) | (midi[7] & 0xff));
    if (i < 14) i = 14;
    int order = 0;
    boolean sigSeen = false;
    while (i + 8 <= midi.length) {
      int len = ((midi[i + 4] & 0xff) << 24) | ((midi[i + 5] & 0xff) << 16) | ((midi[i + 6] & 0xff) << 8) | (midi[i + 7] & 0xff);
      boolean track = midi[i] == 'M' && midi[i + 1] == 'T' && midi[i + 2] == 'r' && midi[i + 3] == 'k';
      if (len < 0) break;
      int p = i + 8;
      int end = Math.min(midi.length, p + len);
      i = end;
      if (!track) continue;
      int tick = 0;
      int running = 0;
      while (p < end) {
        int[] v = vlq(midi, p, end);
        tick += v[0];
        p = v[1];
        if (p >= end) break;
        int st = midi[p] & 0xff;
        if (st < 0x80) {
          if (running == 0) break;
          st = running;
        } else {
          p++;
          if (st < 0xf0) running = st;
        }
        Ev e = new Ev();
        e.tick = tick;
        e.order = order++;
        e.status = st;
        if (st == 0xff) {
          if (p >= end) break;
          int type = midi[p] & 0xff;
          int[] ml = vlq(midi, p + 1, end);
          int stop = Math.min(end, ml[1] + ml[0]);
          // End of track goes once, at the end of the file written.
          if (type == 0x2f) {
            p = stop;
            continue;
          }
          if (type == 0x58 && ml[0] >= 2 && ml[1] + 1 < end && !sigSeen) {
            tsNum = Engine.clampTsNum(midi[ml[1]] & 0xff);
            tsDen = Engine.clampTsDen(1 << Math.min(5, midi[ml[1] + 1] & 0xff));
            sigSeen = true;
          }
          e.raw = slice(midi, p - 1, stop);
          p = stop;
        } else if (st == 0xf0 || st == 0xf7) {
          int[] sl = vlq(midi, p, end);
          int stop = Math.min(end, sl[1] + sl[0]);
          e.raw = slice(midi, p - 1, stop);
          p = stop;
        } else {
          int cmd = st & 0xf0;
          int n = cmd == 0xc0 || cmd == 0xd0 ? 1 : 2;
          if (p + n > end) break;
          if (cmd == 0x80 || cmd == 0x90) {
            e.note = midi[p] & 0xff;
            e.vel = midi[p + 1] & 0xff;
          } else {
            e.raw = new byte[n + 1];
            e.raw[0] = (byte) st;
            for (int k = 0; k < n; k++) e.raw[k + 1] = midi[p + k];
          }
          p += n;
        }
        all.add(e);
      }
    }
    MidiHitEdit out = new MidiHitEdit(tpq, Engine.parseMidiBpm(midi, 120), tsNum, tsDen);
    out.evs.addAll(all);
    out.nextOrder = order;
    out.fitSteps(0);
    return out;
  }

  private static byte[] slice(byte[] b, int from, int to) {
    byte[] out = new byte[Math.max(0, to - from)];
    System.arraycopy(b, from, out, 0, out.length);
    return out;
  }

  private static int[] vlq(byte[] b, int p, int end) {
    int v = 0;
    for (int k = 0; k < 4 && p < end; k++) {
      int c = b[p++] & 0xff;
      v = (v << 7) | (c & 0x7f);
      if ((c & 0x80) == 0) break;
    }
    return new int[] {v, p};
  }

  /** Ticks in a 16th. */
  public int stepTicks() {
    return Math.max(1, this.tpq / 4);
  }

  int stepOf(int tick) {
    return (int) Math.round(tick / (double) this.stepTicks());
  }

  /** Whole bars, enough for the last hit and one more step at least; never fewer than `min` steps. */
  private void fitSteps(int min) {
    int last = 0;
    for (Ev e : this.evs) if (this.drumHit(e) >= 0) last = Math.max(last, this.stepOf(e.tick));
    int need = Math.max(min, last + 1);
    int bars = Math.max(1, (need + this.barSteps - 1) / this.barSteps);
    bars = Math.min(Engine.MAX_MIDI_BARS, bars);
    this.steps = Math.max(this.steps, bars * this.barSteps);
  }

  /** The track a drum hit plays (-1: not a drum hit on a track). */
  private int drumHit(Ev e) {
    if (!e.on() || (e.status & 0x0f) != Engine.DRUM_CH) return -1;
    return Engine.trackForNote(e.note);
  }

  public int steps() {
    return this.steps;
  }

  public int bars() {
    return this.steps / this.barSteps;
  }

  public int tracks() {
    return Engine.TRACK_ID.length;
  }

  /** The loudest hit of the track at the step; 0 none. */
  public int vel(int track, int step) {
    int best = 0;
    for (Ev e : this.evs) if (this.drumHit(e) == track && this.stepOf(e.tick) == step) best = Math.max(best, e.vel);
    return best;
  }

  /** Every track's hits by step, [track][step] the loudest; for drawing the grid in one go. */
  public int[][] cells() {
    int[][] out = new int[this.tracks()][this.steps];
    for (Ev e : this.evs) {
      int t = this.drumHit(e);
      if (t < 0) continue;
      int s = this.stepOf(e.tick);
      if (s >= 0 && s < this.steps && e.vel > out[t][s]) out[t][s] = e.vel;
    }
    return out;
  }

  public boolean accent(int step) {
    for (Ev e : this.evs) if (this.drumHit(e) >= 0 && this.stepOf(e.tick) == step && e.vel >= ACCENT_FROM) return true;
    return false;
  }

  public int hits() {
    int n = 0;
    for (Ev e : this.evs) if (this.drumHit(e) >= 0) n++;
    return n;
  }

  /** Adds a hit, or erases the track's hits at the step (with their note offs). True: it has a hit now. */
  public boolean toggle(int track, int step) {
    if (track < 0 || track >= this.tracks() || step < 0) return false;
    this.changed = true;
    List<Ev> gone = new ArrayList<Ev>();
    for (Ev e : this.evs) if (this.drumHit(e) == track && this.stepOf(e.tick) == step) gone.add(e);
    if (!gone.isEmpty()) {
      for (Ev on : gone) {
        Ev off = this.offFor(on);
        this.evs.remove(on);
        if (off != null) this.evs.remove(off);
      }
      return false;
    }
    int tick = step * this.stepTicks();
    Ev on = new Ev();
    on.tick = tick;
    on.order = this.nextOrder++;
    on.status = 0x90 | Engine.DRUM_CH;
    on.note = Engine.NOTES[track];
    on.vel = this.accent(step) ? ACCENTED : HIT;
    Ev off = new Ev();
    off.tick = tick + Math.max(1, this.stepTicks() / 2);
    off.order = this.nextOrder++;
    off.status = 0x80 | Engine.DRUM_CH;
    off.note = on.note;
    this.evs.add(on);
    this.evs.add(off);
    this.fitSteps(step + 1);
    return true;
  }

  /** ACC: the step's hits all loud, or back to normal. True: accented now. */
  public boolean toggleAccent(int step) {
    boolean now = !this.accent(step);
    boolean any = false;
    for (Ev e : this.evs) if (this.drumHit(e) >= 0 && this.stepOf(e.tick) == step) {
      e.vel = now ? ACCENTED : Math.min(e.vel, UNACCENTED);
      any = true;
    }
    if (any) this.changed = true;
    return any && now;
  }

  /** The note off that ends this note on: the first one of its note and channel after it. */
  private Ev offFor(Ev on) {
    Ev best = null;
    for (Ev e : this.evs) {
      if (!e.off() || e.note != on.note || (e.status & 0x0f) != (on.status & 0x0f)) continue;
      if (e.tick < on.tick || (e.tick == on.tick && e.order < on.order)) continue;
      if (best == null || e.tick < best.tick || (e.tick == best.tick && e.order < best.order)) best = e;
    }
    return best;
  }

  /** The step on the playhead at a time in the playback (one tempo: the file's first). */
  public int stepAtMillis(long ms) {
    double stepMs = 60000.0 / Math.max(1, this.bpm) / 4.0;
    return (int) (ms / stepMs);
  }

  /** The MIDI as it is now: one track, the file's events in time order. */
  public byte[] toMidi() {
    List<Ev> sorted = new ArrayList<Ev>(this.evs);
    Collections.sort(sorted, new Comparator<Ev>() {
      @Override
      public int compare(Ev a, Ev b) {
        if (a.tick != b.tick) return a.tick < b.tick ? -1 : 1;
        int ka = rank(a);
        int kb = rank(b);
        if (ka != kb) return ka < kb ? -1 : 1;
        return a.order < b.order ? -1 : (a.order == b.order ? 0 : 1);
      }
    });
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    int last = 0;
    for (Ev e : sorted) {
      writeVlq(body, e.tick - last);
      last = e.tick;
      if (e.raw != null) body.write(e.raw, 0, e.raw.length);
      else {
        body.write(e.off() && (e.status & 0xf0) == 0x80 ? (0x80 | (e.status & 0x0f)) : e.status);
        body.write(e.note & 0x7f);
        body.write(e.vel & 0x7f);
      }
    }
    int end = Math.max(last, this.steps * this.stepTicks());
    writeVlq(body, end - last);
    body.write(0xff);
    body.write(0x2f);
    body.write(0x00);
    byte[] b = body.toByteArray();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write('M');
    out.write('T');
    out.write('h');
    out.write('d');
    int[] head = {0, 0, 0, 6, 0, 0, 0, 1, (this.tpq >> 8) & 0xff, this.tpq & 0xff};
    for (int h : head) out.write(h);
    out.write('M');
    out.write('T');
    out.write('r');
    out.write('k');
    out.write((b.length >> 24) & 0xff);
    out.write((b.length >> 16) & 0xff);
    out.write((b.length >> 8) & 0xff);
    out.write(b.length & 0xff);
    out.write(b, 0, b.length);
    return out.toByteArray();
  }

  /** At one tick: meta events first, then note offs, then the rest, then note ons. */
  static int rank(Ev e) {
    if (e.status == 0xff) return 0;
    if (e.raw == null && e.off()) return 1;
    if (e.raw == null && e.on()) return 3;
    return 2;
  }

  private static void writeVlq(ByteArrayOutputStream out, int value) {
    int v = Math.max(0, value);
    int[] tmp = new int[5];
    int n = 0;
    tmp[n++] = v & 0x7f;
    v >>= 7;
    while (v > 0) {
      tmp[n++] = (v & 0x7f) | 0x80;
      v >>= 7;
    }
    for (int k = n - 1; k >= 0; k--) out.write(tmp[k]);
  }

  /** "1.1" for the first step: bar and beat, for the step under the ACC row. */
  public String beatLabel(int step) {
    int perBeat = Math.max(1, Engine.stepsPerBeat(this.tsDen));
    int inBar = step % this.barSteps;
    if (inBar % perBeat != 0) return "";
    int bar = step / this.barSteps + 1;
    int beat = inBar / perBeat + 1;
    return beat == 1 ? Integer.toString(bar) : Integer.toString(beat);
  }

  /** Save: the file set's MIDI becomes this one (kept under its name). */
  public static void saveToFileSet(String key, byte[] midi) {
    Engine.FileSetAudio au = Engine.fileSetAudioOf(key);
    String name = au == null || au.sourceMidiName == null || au.sourceMidiName.isEmpty() ? "source.mid" : au.sourceMidiName;
    Engine.rememberFileSetMidi(key, midi, name);
  }
}
