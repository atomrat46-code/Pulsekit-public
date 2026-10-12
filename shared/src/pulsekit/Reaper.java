package pulsekit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reaper projects (.rpp) as drum MIDI: the notes of the project's MIDI items where Reaper plays
 * them (item position, length and loop, the project tempo or the item's own), so the MIDI lines up
 * with the project's render. Muted items and tracks are left out. When some MIDI is on channel 10,
 * only that is taken (the drums); otherwise all notes are. The MIDI is at the project's tempo, with
 * its tempo changes, and imports like any other drum MIDI.
 */
public final class Reaper {
  /** Ticks per quarter note of the MIDI written. */
  public static final int PPQ = 960;
  private static final int NOTE_TICKS = 120;

  private Reaper() {}

  /** A Reaper project, by its text (whatever the file is called). */
  public static boolean isReaper(byte[] data) {
    if (data == null || data.length < 16) return false;
    String head = new String(data, 0, Math.min(data.length, 64), StandardCharsets.UTF_8).trim();
    return head.startsWith("<REAPER_PROJECT");
  }

  /** What was read, for the status line. */
  public static final class Result {
    public byte[] midi;
    public String title = "";
    public int items;
    public int notes;
    public int bars;
    public double bpm = 120;
    public int tsNum = 4;
    public int tsDen = 4;
    public double seconds;
  }

  /** "Reaper project · Untitled Song · 58 bars, 1307 notes at 120 BPM" for the status line. */
  public static String summary(Result r) {
    return "Reaper project" + (r.title.isEmpty() ? "" : " · " + r.title) + " · " + r.bars + (r.bars == 1 ? " bar, " : " bars, ")
        + r.notes + " notes at " + Math.round(r.bpm) + " BPM" + (r.items > 1 ? " (" + r.items + " MIDI items)" : "");
  }

  /** The file name with .mid in place of .rpp. */
  public static String midiName(String name) {
    String n = name == null || name.isEmpty() ? "reaper" : name;
    return n.replaceAll("(?i)\\.rpp(-bak)?$", "") + ".mid";
  }

  /** One node of the project: its first line's words and its children. */
  private static final class Node {
    final List<String> head;
    final List<Node> kids = new ArrayList<Node>();
    final List<List<String>> lines = new ArrayList<List<String>>();

    Node(List<String> head) {
      this.head = head;
    }

    String tag() {
      return head.isEmpty() ? "" : head.get(0);
    }

    Node kid(String tag) {
      for (Node k : kids) if (k.tag().equals(tag)) return k;
      return null;
    }

    List<String> line(String key) {
      for (List<String> l : lines) if (!l.isEmpty() && l.get(0).equals(key)) return l;
      return null;
    }

    double num(String key, int index, double dflt) {
      List<String> l = line(key);
      return l == null || l.size() <= index ? dflt : Reaper.num(l.get(index), dflt);
    }
  }

  private static Node parse(String text) {
    Node root = new Node(new ArrayList<String>());
    List<Node> stack = new ArrayList<Node>();
    stack.add(root);
    for (String raw : text.split("\r?\n")) {
      String s = raw.trim();
      if (s.isEmpty()) continue;
      if (s.startsWith("<")) {
        Node n = new Node(words(s.substring(1)));
        stack.get(stack.size() - 1).kids.add(n);
        stack.add(n);
      } else if (s.equals(">")) {
        if (stack.size() > 1) stack.remove(stack.size() - 1);
      } else {
        stack.get(stack.size() - 1).lines.add(words(s));
      }
    }
    return root.kids.isEmpty() ? root : root.kids.get(0);
  }

  /** Words of a line; "quoted words" stay one. */
  private static List<String> words(String s) {
    List<String> out = new ArrayList<String>();
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == ' ' || c == '\t') {
        i++;
        continue;
      }
      if (c == '"' || c == '\'' || c == '`') {
        int j = s.indexOf(c, i + 1);
        if (j < 0) j = s.length();
        out.add(s.substring(i + 1, j));
        i = j + 1;
        continue;
      }
      int j = i;
      while (j < s.length() && s.charAt(j) != ' ' && s.charAt(j) != '\t') j++;
      out.add(s.substring(i, j));
      i = j;
    }
    return out;
  }

  /** The project's tempo map: square steps at its tempo points (a ramp is taken as a step). */
  private static final class Tempo {
    final List<double[]> points = new ArrayList<double[]>();

    Tempo(Node project) {
      double bpm = project.num("TEMPO", 1, 120);
      points.add(new double[] {0, bpm});
      Node env = project.kid("TEMPOENVEX");
      if (env != null) {
        List<double[]> pts = new ArrayList<double[]>();
        for (List<String> l : env.lines) {
          if (l.size() >= 3 && l.get(0).equals("PT")) pts.add(new double[] {num(l.get(1), 0), num(l.get(2), bpm)});
        }
        Collections.sort(pts, new Comparator<double[]>() {
          public int compare(double[] a, double[] b) {
            return Double.compare(a[0], b[0]);
          }
        });
        // ACT 0: the tempo envelope is off, the project tempo holds.
        if (!pts.isEmpty() && env.num("ACT", 1, 1) != 0) {
          points.clear();
          if (pts.get(0)[0] > 0) points.add(new double[] {0, bpm});
          points.addAll(pts);
        }
      }
    }

    /** Quarter notes from 0:00 to a time (seconds). */
    double beats(double sec) {
      double b = 0;
      for (int i = 0; i < points.size(); i++) {
        double start = points.get(i)[0];
        double end = i + 1 < points.size() ? points.get(i + 1)[0] : Double.MAX_VALUE;
        if (sec <= start) break;
        b += (Math.min(sec, end) - start) * points.get(i)[1] / 60.0;
      }
      return b;
    }

    /** The time (seconds) of a number of quarter notes from 0:00. */
    double seconds(double beats) {
      double b = 0;
      for (int i = 0; i < points.size(); i++) {
        double start = points.get(i)[0];
        double end = i + 1 < points.size() ? points.get(i + 1)[0] : Double.MAX_VALUE;
        double span = (end - start) * points.get(i)[1] / 60.0;
        if (b + span >= beats || i + 1 == points.size()) return start + (beats - b) * 60.0 / points.get(i)[1];
        b += span;
      }
      return beats * 0.5;
    }
  }

  /** The project's MIDI items' drum notes as a MIDI file. */
  public static Result toMidi(byte[] data) throws Exception {
    Node project = parse(new String(data, StandardCharsets.UTF_8));
    if (!project.tag().equals("REAPER_PROJECT")) throw new IllegalArgumentException("Not a Reaper project");
    Result r = new Result();
    Tempo tempo = new Tempo(project);
    r.bpm = tempo.points.get(0)[1];
    r.tsNum = (int) project.num("TEMPO", 2, 4);
    r.tsDen = (int) project.num("TEMPO", 3, 4);
    if (r.tsNum < 1 || r.tsNum > 32) r.tsNum = 4;
    if (r.tsDen != 2 && r.tsDen != 4 && r.tsDen != 8 && r.tsDen != 16) r.tsDen = 4;

    // Pooled MIDI: the events are written once, with their pool's GUID.
    Map<String, Node> pool = new HashMap<String, Node>();
    List<Node> tracks = new ArrayList<Node>();
    collect(project, "TRACK", tracks);
    for (Node t : tracks) {
      List<Node> items = new ArrayList<Node>();
      collect(t, "ITEM", items);
      for (Node it : items) {
        Node src = midiSource(it);
        if (src == null || src.line("POOLEDEVTS") == null || !hasEvents(src)) continue;
        pool.put(src.line("POOLEDEVTS").size() > 1 ? src.line("POOLEDEVTS").get(1) : "", src);
      }
    }

    List<double[]> all = new ArrayList<double[]>(); // {seconds, channel, key, velocity}
    double end = 0;
    for (Node t : tracks) {
      if (t.num("MUTESOLO", 1, 0) == 1) continue;
      if (r.title.isEmpty()) {
        List<String> nm = t.line("NAME");
        if (nm != null && nm.size() > 1) r.title = nm.get(1);
      }
      List<Node> items = new ArrayList<Node>();
      collect(t, "ITEM", items);
      for (Node it : items) {
        if (it.num("MUTE", 1, 0) == 1) continue;
        Node src = midiSource(it);
        if (src == null) continue;
        if (!hasEvents(src) && src.line("POOLEDEVTS") != null && src.line("POOLEDEVTS").size() > 1) {
          Node shared = pool.get(src.line("POOLEDEVTS").get(1));
          if (shared != null) src = shared;
        }
        int before = all.size();
        double itemEnd = itemNotes(it, src, tempo, all);
        if (all.size() > before) r.items++;
        end = Math.max(end, itemEnd);
      }
    }
    boolean drums = false;
    for (double[] n : all) if ((int) n[1] == 9) drums = true;
    List<long[]> hits = new ArrayList<long[]>();
    for (double[] n : all) {
      if (drums && (int) n[1] != 9) continue;
      hits.add(new long[] {Math.round(tempo.beats(n[0]) * PPQ), (long) n[2], (long) n[3]});
    }
    r.notes = hits.size();
    r.seconds = end;
    long length = Math.round(tempo.beats(end) * PPQ);
    long bar = (long) PPQ * 4 * r.tsNum / r.tsDen;
    r.bars = (int) Math.max(1, (length + bar - 1) / bar);
    List<long[]> tempos = new ArrayList<long[]>();
    for (double[] p : tempo.points) tempos.add(new long[] {Math.round(tempo.beats(p[0]) * PPQ), Math.round(60000000.0 / Math.max(20, Math.min(400, p[1])))});
    r.midi = encode(hits, tempos, r.tsNum, r.tsDen, length, r.title);
    return r;
  }

  private static void collect(Node n, String tag, List<Node> out) {
    for (Node k : n.kids) {
      if (k.tag().equals(tag)) out.add(k);
      else if (!tag.equals("ITEM") || !k.tag().equals("TRACK")) collect(k, tag, out);
    }
  }

  /** The item's active take's MIDI source (the first one, if no take is marked). */
  private static Node midiSource(Node item) {
    for (Node k : item.kids) if (k.tag().equals("SOURCE") && k.head.size() > 1 && k.head.get(1).equals("MIDI")) return k;
    return null;
  }

  private static boolean hasEvents(Node src) {
    for (List<String> l : src.lines) if (!l.isEmpty() && (l.get(0).equals("E") || l.get(0).equals("e"))) return true;
    return false;
  }

  /**
   * The item's notes in project seconds, added to out; the item's end. Ticks are quarter notes of
   * the project tempo, or of the item's own (IGNTEMPO 1 bpm); a looped item repeats its source up
   * to its length; SOFFS skips the source's start.
   */
  private static double itemNotes(Node item, Node src, Tempo tempo, List<double[]> out) {
    double pos = item.num("POSITION", 1, 0);
    double len = item.num("LENGTH", 1, 0);
    double offs = item.num("SOFFS", 1, 0);
    double rate = item.num("PLAYRATE", 1, 1);
    if (rate <= 0) rate = 1;
    boolean loop = item.num("LOOP", 1, 0) == 1;
    double ppq = src.num("HASDATA", 2, 960);
    if (ppq <= 0) ppq = 960;
    boolean own = src.num("IGNTEMPO", 1, 0) == 1;
    double ownBpm = src.num("IGNTEMPO", 2, 120);
    List<double[]> ev = new ArrayList<double[]>(); // {tick, channel, key, velocity}
    long tick = 0;
    for (List<String> l : src.lines) {
      if (l.isEmpty()) continue;
      String k = l.get(0);
      if (!k.equals("E") && !k.equals("e") && !k.equals("X") && !k.equals("x")) continue;
      if (l.size() < 2) continue;
      tick += (long) num(l.get(1), 0);
      if ((k.equals("E") || k.equals("e")) && l.size() >= 5) {
        int status = hex(l.get(2)), key = hex(l.get(3)), vel = hex(l.get(4));
        if ((status & 0xF0) == 0x90 && vel > 0) ev.add(new double[] {tick, status & 0x0F, key, vel});
      }
    }
    double srcBeats = tick / ppq;
    double startBeats = tempo.beats(pos);
    double end = pos + len;
    double offBeats = own ? offs * ownBpm / 60.0 : tempo.beats(pos + offs) - startBeats;
    for (int rep = 0; rep < 10000; rep++) {
      boolean any = false;
      for (double[] e : ev) {
        double b = (rep * srcBeats + e[0] / ppq - offBeats) / rate;
        if (b < 0) continue;
        double sec = own ? pos + b * 60.0 / ownBpm : tempo.seconds(startBeats + b);
        if (sec >= end - 1e-6) continue;
        out.add(new double[] {sec, e[1], e[2], e[3]});
        any = true;
      }
      double nextStart = own ? pos + ((rep + 1) * srcBeats - offBeats) / rate * 60.0 / ownBpm : tempo.seconds(startBeats + ((rep + 1) * srcBeats - offBeats) / rate);
      if (!loop || srcBeats <= 0 || nextStart >= end) break;
      if (!any && rep > 0) break;
    }
    return end;
  }

  /** Format 0 MIDI on channel 10: tempo map, time signature, name, notes. */
  private static byte[] encode(List<long[]> hits, List<long[]> tempos, int tsNum, int tsDen, long length, String title) {
    List<long[]> ev = new ArrayList<long[]>(); // {tick, order, kind, a, b}: 0 tempo, 1 off, 2 on
    for (long[] t : tempos) ev.add(new long[] {t[0], 0, 0, t[1], 0});
    for (long[] h : hits) {
      ev.add(new long[] {h[0], 2, 2, h[1], h[2]});
      ev.add(new long[] {h[0] + NOTE_TICKS, 1, 1, h[1], 0});
    }
    Collections.sort(ev, new Comparator<long[]>() {
      public int compare(long[] a, long[] b) {
        if (a[0] != b[0]) return a[0] < b[0] ? -1 : 1;
        return a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0;
      }
    });
    ByteArrayOutputStream tr = new ByteArrayOutputStream();
    byte[] name = (title == null || title.isEmpty() ? "Reaper" : title).getBytes(StandardCharsets.UTF_8);
    vlq(tr, 0);
    tr.write(0xFF);
    tr.write(0x03);
    vlq(tr, name.length);
    tr.write(name, 0, name.length);
    int dd = 0;
    while ((1 << dd) < tsDen) dd++;
    vlq(tr, 0);
    tr.write(new byte[] {(byte) 0xFF, 0x58, 4, (byte) tsNum, (byte) dd, 24, 8}, 0, 7);
    long last = 0;
    for (long[] e : ev) {
      vlq(tr, e[0] - last);
      last = e[0];
      if (e[2] == 0) {
        long us = e[3];
        tr.write(new byte[] {(byte) 0xFF, 0x51, 3, (byte) (us >> 16), (byte) (us >> 8), (byte) us}, 0, 6);
      } else {
        tr.write(e[2] == 2 ? 0x99 : 0x89);
        tr.write((int) (e[3] & 0x7F));
        tr.write((int) (e[4] & 0x7F));
      }
    }
    vlq(tr, Math.max(0, length - last));
    tr.write(new byte[] {(byte) 0xFF, 0x2F, 0}, 0, 3);
    byte[] body = tr.toByteArray();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, (byte) (PPQ >> 8), (byte) PPQ}, 0, 14);
    out.write(new byte[] {'M', 'T', 'r', 'k', (byte) (body.length >> 24), (byte) (body.length >> 16), (byte) (body.length >> 8), (byte) body.length}, 0, 8);
    out.write(body, 0, body.length);
    return out.toByteArray();
  }

  private static void vlq(ByteArrayOutputStream o, long v) {
    long buf = v & 0x7F;
    while ((v >>= 7) > 0) {
      buf <<= 8;
      buf |= (v & 0x7F) | 0x80;
    }
    while (true) {
      o.write((int) (buf & 0xFF));
      if ((buf & 0x80) != 0) buf >>= 8;
      else break;
    }
  }

  private static int hex(String s) {
    try {
      return Integer.parseInt(s, 16) & 0xFF;
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  static double num(String s, double dflt) {
    try {
      return s == null || s.isEmpty() ? dflt : Double.parseDouble(s.trim());
    } catch (NumberFormatException ex) {
      return dflt;
    }
  }
}
