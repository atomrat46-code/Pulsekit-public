package pulsekit;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A DrumPad track's MIDI editor (with its pads): the take's hits as a grid of 16th-note steps, a bar of
 * the time signature at a time. A step is selected on the timeline, then a pad pressed puts that pad
 * in it (or takes it out); a cell of the grid does the same. + Bar makes the take a bar longer,
 * Quantize moves every hit onto its nearest step, Export MIDI writes the take as a drum MIDI file
 * (General MIDI notes on channel 10, its tempo and time signature).
 */
public final class DrumGrid {
  private DrumGrid() {}

  public static final String BUTTON = "MIDI editor";
  public static final String TITLE = "MIDI editor";
  public static final String ADD_BAR = "+ Bar";
  public static final String QUANTIZE = "Quantize";
  public static final String EXPORT = "Export MIDI";

  /** Frames from one 16th note to the next (the BPM counts quarter notes). */
  public static double stepFrames(DrumPads.Take k) {
    return AudioMix.RATE * 15.0 / Math.max(Engine.MIN_BPM, Math.min(Engine.MAX_BPM, k.bpm));
  }

  /** Steps in a bar: 16 in 4/4, 12 in 3/4 and in 6/8, 14 in 7/8. */
  public static int stepsPerBar(DrumPads.Take k) {
    return Math.max(1, k.beats() * 16 / k.unit());
  }

  public static double barFrames(DrumPads.Take k) {
    return stepsPerBar(k) * stepFrames(k);
  }

  /** The bars the take covers (one at least): its length, or its last hit. */
  public static int bars(DrumPads.Take k) {
    long end = k.length;
    for (DrumPads.Hit h : k.hits) end = Math.max(end, h.frame + 1);
    return Math.max(1, (int) Math.ceil(end / barFrames(k) - 1e-9));
  }

  /** The step a frame is nearest to. */
  public static int stepOf(DrumPads.Take k, long frame) {
    return (int) Math.round(frame / stepFrames(k));
  }

  public static long frameOf(DrumPads.Take k, int step) {
    return Math.round(step * stepFrames(k));
  }

  /** The hits of `pad` at `step` (within half a step of it). */
  public static List<DrumPads.Hit> at(DrumPads.Take k, int pad, int step) {
    List<DrumPads.Hit> out = new ArrayList<DrumPads.Hit>();
    for (DrumPads.Hit h : k.hits) if (h.pad == pad && stepOf(k, h.frame) == step) out.add(h);
    return out;
  }

  public static boolean has(DrumPads.Take k, int pad, int step) {
    for (DrumPads.Hit h : k.hits) if (h.pad == pad && stepOf(k, h.frame) == step) return true;
    return false;
  }

  /** `pad` put in `step`, or taken out when it is there; whether it is there now. The take covers the step's bar. */
  public static boolean toggle(DrumPads.Take k, int pad, int step) {
    List<DrumPads.Hit> there = at(k, pad, step);
    if (!there.isEmpty()) {
      k.hits.removeAll(there);
      return false;
    }
    DrumPads.Hit h = new DrumPads.Hit(frameOf(k, step), pad, 1f);
    int i = 0;
    while (i < k.hits.size() && k.hits.get(i).frame <= h.frame) i++;
    k.hits.add(i, h);
    int bar = step / stepsPerBar(k);
    k.length = Math.max(k.length, Math.round((bar + 1) * barFrames(k)));
    return true;
  }

  /** + Bar: the take a bar longer (from the end of its last whole bar). */
  public static void addBar(DrumPads.Take k) {
    k.length = Math.round((bars(k) + 1) * barFrames(k));
  }

  /** Quantize: each hit on its nearest step (two of one pad on one step become one); how many moved. */
  public static int quantize(DrumPads.Take k) {
    int moved = 0;
    List<DrumPads.Hit> kept = new ArrayList<DrumPads.Hit>();
    for (DrumPads.Hit h : k.hits) {
      long f = frameOf(k, stepOf(k, h.frame));
      if (f != h.frame) moved++;
      h.frame = f;
      boolean twice = false;
      for (DrumPads.Hit o : kept) twice |= o.pad == h.pad && o.frame == h.frame;
      if (!twice) kept.add(h);
    }
    k.hits.clear();
    k.hits.addAll(kept);
    k.hits.sort((a, b) -> Long.compare(a.frame, b.frame));
    return moved;
  }

  /** "Bar 2 of 4 · step 5 · 12 hits". */
  public static String state(DrumPads.Take k, int bar, int step) {
    return "Bar " + (bar + 1) + " of " + bars(k) + " · step " + (step + 1) + " · " + k.hits.size() + " hits";
  }

  /** The take as a drum MIDI file: its tempo, time signature, each hit a 16th note long on channel 10. */
  public static byte[] toMidi(DrumPads.Take k) {
    int bpm = Math.max(Engine.MIN_BPM, Math.min(Engine.MAX_BPM, k.bpm));
    double ticksPerFrame = Engine.TPQ * bpm / 60.0 / AudioMix.RATE;
    List<long[]> ev = new ArrayList<long[]>();
    int gate = Engine.TPQ / 4;
    for (DrumPads.Hit h : k.hits) {
      long at = Math.round(h.frame * ticksPerFrame);
      int vel = Math.max(1, Math.min(127, Math.round(h.velocity * 110)));
      ev.add(new long[] {at, 1, 0x90 | Engine.DRUM_CH, Engine.NOTES[h.pad], vel});
      ev.add(new long[] {at + gate, 0, 0x80 | Engine.DRUM_CH, Engine.NOTES[h.pad], 0});
    }
    ev.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
    ByteArrayOutputStream t = new ByteArrayOutputStream();
    int us = (int) Math.round(60_000_000.0 / bpm);
    t.write(0);
    t.write(new byte[] {(byte) 0xff, 0x51, 0x03, (byte) (us >> 16), (byte) (us >> 8), (byte) us}, 0, 6);
    t.write(0);
    int den = 0;
    for (int u = k.unit(); u > 1; u >>= 1) den++;
    t.write(new byte[] {(byte) 0xff, 0x58, 0x04, (byte) k.beats(), (byte) den, 0x18, 0x08}, 0, 7);
    long last = 0;
    for (long[] e : ev) {
      vlq(t, e[0] - last);
      last = e[0];
      t.write((int) e[2]);
      t.write((int) e[3]);
      t.write((int) e[4]);
    }
    long end = Math.max(last, Math.round(bars(k) * barFrames(k) * ticksPerFrame));
    vlq(t, end - last);
    t.write(new byte[] {(byte) 0xff, 0x2f, 0x00}, 0, 3);
    byte[] body = t.toByteArray();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, (byte) (Engine.TPQ >> 8), (byte) Engine.TPQ}, 0, 14);
    out.write(new byte[] {'M', 'T', 'r', 'k', (byte) (body.length >> 24), (byte) (body.length >> 16), (byte) (body.length >> 8), (byte) body.length}, 0, 8);
    out.write(body, 0, body.length);
    return out.toByteArray();
  }

  static void vlq(ByteArrayOutputStream o, long value) {
    long v = Math.max(0, value);
    int[] tmp = new int[5];
    int n = 0;
    tmp[n++] = (int) (v & 0x7f);
    v >>= 7;
    while (v > 0) {
      tmp[n++] = (int) ((v & 0x7f) | 0x80);
      v >>= 7;
    }
    for (int i = n - 1; i >= 0; i--) o.write(tmp[i]);
  }

  /** The export's name: "DrumPad 1.mid". */
  public static String midiName(AudioMix.Track t) {
    return t.name.replaceAll("[\\\\/:*?\"<>|]+", "_") + ".mid";
  }
}
