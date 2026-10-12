package pulsekit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Hydrogen (hydrogen-music.org) drum files as MIDI: a song (.h2song) and a pattern (.h2pattern)
 * are XML. A song's patterns play column by column of its pattern sequence (every pattern in a
 * column at once, the column as long as its longest pattern), each instrument on its MIDI out note.
 * A pattern file names only its drum kit, so its instruments are the GM kit's notes (36 + the
 * instrument's number), or GMRockKit's. The MIDI then imports like any other drum MIDI.
 */
public final class Hydrogen {
  /** Hydrogen's ticks per quarter note: a 4/4 pattern is 192 ticks. */
  public static final int TICKS = 48;
  private static final int NOTE_TICKS = 12;

  /** GMRockKit's MIDI out notes, by instrument number (Hydrogen's own kit list). */
  private static final int[] GM_ROCK_KIT = {36, 37, 38, 39, 38, 41, 42, 43, 44, 45, 46, 56, 51, 49, 59, 57, 82, 81};

  private Hydrogen() {}

  /** A Hydrogen song or pattern, by its XML (whatever the file is called). */
  public static boolean isHydrogen(byte[] data) {
    if (data == null || data.length < 8) return false;
    String head = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.UTF_8);
    return head.contains("<drumkit_pattern") || head.contains("<song") && (head.contains("<instrumentList") || head.contains("<bpm>"));
  }

  /** A song (true) or a pattern file. */
  public static boolean isSong(byte[] data) {
    String head = new String(data, 0, Math.min(data.length, 4096), StandardCharsets.UTF_8);
    return !head.contains("<drumkit_pattern");
  }

  /** "Hydrogen song · <title> · 59 bars, 1331 notes" for the status line. */
  public static String summary(Result r) {
    return "Hydrogen " + (r.bpm > 0 ? "song" : "pattern") + (r.title.isEmpty() ? "" : " \u00b7 " + r.title) + " \u00b7 " + r.bars
        + (r.bars == 1 ? " bar, " : " bars, ") + r.notes + " notes" + (r.bpm > 0 ? " at " + Math.round(r.bpm) + " BPM" : "");
  }

  /**
   * Program arguments with every Hydrogen song or pattern file swapped for its MIDI, written to dir
   * (CompareHits, Midi2Wav and the other programs read MIDI only).
   */
  public static List<String> midiArgs(List<String> argv, java.io.File dir) {
    if (argv == null) return argv;
    List<String> out = new ArrayList<String>(argv);
    for (int i = 0; i < out.size(); i++) {
      String a = out.get(i);
      String low = a.toLowerCase(Locale.ROOT);
      if (!low.endsWith(".h2song") && !low.endsWith(".h2pattern") && !low.endsWith(".xml")) continue;
      java.io.File f = new java.io.File(a);
      if (!f.isFile()) continue;
      try {
        byte[] data = readAll(f);
        if (!isHydrogen(data)) continue;
        Result r = toMidi(data);
        dir.mkdirs();
        java.io.File mid = new java.io.File(dir, midiName(f.getName()));
        java.io.FileOutputStream fos = new java.io.FileOutputStream(mid);
        try {
          fos.write(r.midi);
        } finally {
          fos.close();
        }
        out.set(i, mid.getAbsolutePath());
      } catch (Exception ex) {
        // Left as it is: the program says what it could not read.
      }
    }
    return out;
  }

  private static byte[] readAll(java.io.File f) throws java.io.IOException {
    java.io.FileInputStream in = new java.io.FileInputStream(f);
    try {
      ByteArrayOutputStream o = new ByteArrayOutputStream();
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) > 0) o.write(b, 0, n);
      return o.toByteArray();
    } finally {
      in.close();
    }
  }

  /** The file name with .mid in place of .h2song / .h2pattern (and any .xml after it). */
  public static String midiName(String name) {
    String n = name == null || name.isEmpty() ? "hydrogen" : name;
    n = n.replaceAll("(?i)\\.xml$", "").replaceAll("(?i)\\.h2(song|pattern)$", "");
    return n + ".mid";
  }

  /** What was read, for the status line. */
  public static final class Result {
    public byte[] midi;
    public String title = "";
    public int bars;
    public int notes;
    public int patterns;
    /** The song's tempo; 0 for a pattern file (it has none). */
    public double bpm;
    public int tsNum = 4;
    public int tsDen = 4;
  }

  /** The Hydrogen file as a drum MIDI (format 0, channel 10). */
  public static Result toMidi(byte[] data) throws Exception {
    Element root = parse(data);
    Result r = new Result();
    List<long[]> hits = new ArrayList<long[]>();
    List<long[]> tempos = new ArrayList<long[]>();
    long length;
    if ("drumkit_pattern".equals(root.getTagName())) {
      String kit = text(root, "pattern_for_drumkit");
      if (kit.isEmpty()) kit = text(root, "drumkit_name");
      Element p = child(root, "pattern");
      if (p == null) throw new IllegalArgumentException("No pattern in that Hydrogen file");
      HPattern hp = pattern(p);
      // Patterns imported from MIDI are named by the file's whole path.
      r.title = hp.name.replaceAll("^.*[\\\\/]", "");
      r.patterns = 1;
      int[] den = signature(hp);
      r.tsNum = den[0];
      r.tsDen = den[1];
      Map<Integer, Integer> notes = kitNotes(kit);
      addHits(hits, hp, 0, notes, null);
      length = hp.size;
      // A pattern file has no tempo: the MIDI has none, so the pattern plays at the tempo set.
      r.bpm = 0;
    } else {
      r.title = text(root, "name");
      r.bpm = num(text(root, "bpm"), 120);
      Map<Integer, Integer> notes = new HashMap<Integer, Integer>();
      java.util.Set<Integer> muted = new java.util.HashSet<Integer>();
      Element list = child(root, "instrumentList");
      if (list != null) {
        for (Element in : children(list, "instrument")) {
          int id = (int) num(text(in, "id"), -1);
          int note = (int) num(text(in, "midiOutNote"), 36 + id);
          if (id >= 0) notes.put(id, note);
          if ("true".equalsIgnoreCase(text(in, "isMuted"))) muted.add(id);
        }
      }
      Map<String, HPattern> byName = new LinkedHashMap<String, HPattern>();
      Element plist = child(root, "patternList");
      if (plist != null) for (Element p : children(plist, "pattern")) {
        HPattern hp = pattern(p);
        byName.put(hp.name, hp);
      }
      Map<String, List<String>> virtual = new HashMap<String, List<String>>();
      Element vlist = child(root, "virtualPatternList");
      if (vlist != null) for (Element vp : children(vlist, "pattern")) {
        List<String> with = new ArrayList<String>();
        for (Element v : children(vp, "virtual")) with.add(v.getTextContent().trim());
        virtual.put(text(vp, "name"), with);
      }
      List<List<String>> columns = new ArrayList<List<String>>();
      Element seq = child(root, "patternSequence");
      if (seq != null) for (Element g : children(seq, "group")) {
        List<String> col = new ArrayList<String>();
        for (Element id : children(g, "patternID")) addWithVirtual(col, id.getTextContent().trim(), virtual, 0);
        columns.add(col);
      }
      // No song sequence (pattern mode): every pattern with notes, one after another.
      if (columns.isEmpty()) for (HPattern hp : byName.values()) if (!hp.notes.isEmpty()) {
        List<String> col = new ArrayList<String>();
        col.add(hp.name);
        columns.add(col);
      }
      java.util.Set<String> used = new java.util.HashSet<String>();
      long at = 0;
      HPattern first = null;
      for (List<String> col : columns) {
        long longest = 0;
        for (String name : col) {
          HPattern hp = byName.get(name);
          if (hp == null) continue;
          if (first == null) first = hp;
          used.add(name);
          addHits(hits, hp, at, notes, muted);
          longest = Math.max(longest, hp.size);
        }
        if (longest == 0) longest = 4 * TICKS;
        at += longest;
      }
      length = at;
      r.patterns = used.size();
      if (first != null) {
        int[] sig = signature(first);
        r.tsNum = sig[0];
        r.tsDen = sig[1];
      }
      tempoChanges(root, columns, byName, tempos);
    }
    r.notes = hits.size();
    long bar = (long) TICKS * 4 * r.tsNum / r.tsDen;
    r.bars = (int) Math.max(1, (length + bar - 1) / bar);
    r.midi = encode(hits, tempos, r.bpm, r.tsNum, r.tsDen, length, r.title);
    return r;
  }

  /** One pattern: name, length in ticks, denominator (0 when the file has none) and notes. */
  private static final class HPattern {
    String name = "";
    long size = 4 * TICKS;
    int denominator;
    final List<double[]> notes = new ArrayList<double[]>();
  }

  private static HPattern pattern(Element p) {
    HPattern hp = new HPattern();
    hp.name = text(p, "name");
    if (hp.name.isEmpty()) hp.name = text(p, "pattern_name");
    hp.size = (long) num(text(p, "size"), 4 * TICKS);
    hp.denominator = (int) num(text(p, "denominator"), 0);
    Element list = child(p, "noteList");
    if (list != null) for (Element n : children(list, "note")) {
      double vel = num(text(n, "velocity"), 0.8);
      // Stop notes and the note-offs Hydrogen keeps from imported MIDI sound nothing.
      if (vel <= 0 || "true".equalsIgnoreCase(text(n, "note_off"))) continue;
      double pos = num(text(n, "position"), -1);
      int inst = (int) num(text(n, "instrument"), -1);
      if (pos < 0 || inst < 0) continue;
      hp.notes.add(new double[] {pos, inst, vel});
    }
    return hp;
  }

  /** Beats and beat unit: from the denominator, or a "6-8" style name, else 4/4. */
  private static int[] signature(HPattern hp) {
    if (hp.denominator > 0) {
      long num = hp.size * hp.denominator / (4 * TICKS);
      if (num >= 1 && num <= 16 && num * 4 * TICKS == hp.size * hp.denominator) return new int[] {(int) num, hp.denominator};
    }
    Matcher m = Pattern.compile("(?<![0-9])([2-9]|1[0-6])[-/]([248]|16)(?![0-9])").matcher(hp.name);
    if (m.find()) return new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
    return new int[] {4, 4};
  }

  private static void addWithVirtual(List<String> col, String name, Map<String, List<String>> virtual, int depth) {
    if (!col.contains(name)) col.add(name);
    List<String> with = virtual.get(name);
    if (with != null && depth < 8) for (String w : with) addWithVirtual(col, w, virtual, depth + 1);
  }

  private static void addHits(List<long[]> hits, HPattern hp, long at, Map<Integer, Integer> notes, java.util.Set<Integer> muted) {
    for (double[] n : hp.notes) {
      if (n[0] >= hp.size) continue;
      int inst = (int) n[1];
      if (muted != null && muted.contains(inst)) continue;
      Integer note = notes.get(inst);
      int key = note != null ? note.intValue() : 36 + inst;
      if (key < 0 || key > 127) continue;
      // Rounded down, as Hydrogen's own MIDI export does.
      int vel = (int) Math.max(1, Math.min(127, Math.floor(n[2] * 127 + 1e-9)));
      hits.add(new long[] {at + Math.round(n[0]), key, vel});
    }
  }

  /** A pattern file's instrument notes, by its kit. */
  private static Map<Integer, Integer> kitNotes(String kit) {
    Map<Integer, Integer> m = new HashMap<Integer, Integer>();
    if (kit != null && kit.toLowerCase(Locale.ROOT).replace(" ", "").contains("gmrockkit")) {
      for (int i = 0; i < GM_ROCK_KIT.length; i++) m.put(i, GM_ROCK_KIT[i]);
    }
    return m;
  }

  /** Tempo changes of the song's timeline (Hydrogen 1.x BPMTimeLine and 2.x timeline), at column starts. */
  private static void tempoChanges(Element root, List<List<String>> columns, Map<String, HPattern> byName, List<long[]> out) {
    List<double[]> marks = new ArrayList<double[]>();
    Element old = child(root, "BPMTimeLine");
    if (old != null) for (Element n : children(old, "newBPM")) marks.add(new double[] {num(text(n, "BAR"), -1), num(text(n, "BPM"), 0)});
    Element tl = child(root, "timeline");
    if (tl != null) for (Element n : children(tl, "tempoMarker")) marks.add(new double[] {num(text(n, "bar"), num(text(n, "column"), -1)), num(text(n, "bpm"), 0)});
    if (marks.isEmpty()) return;
    long[] starts = new long[columns.size() + 1];
    for (int c = 0; c < columns.size(); c++) {
      long longest = 0;
      for (String name : columns.get(c)) {
        HPattern hp = byName.get(name);
        if (hp != null) longest = Math.max(longest, hp.size);
      }
      starts[c + 1] = starts[c] + (longest == 0 ? 4 * TICKS : longest);
    }
    for (double[] m : marks) {
      int col = (int) m[0];
      if (col < 0 || col >= starts.length || m[1] < 20 || m[1] > 400) continue;
      out.add(new long[] {starts[col], Math.round(60000000.0 / m[1])});
    }
  }

  /** Format 0 MIDI: tempo, time signature, name, then the hits on channel 10. */
  private static byte[] encode(List<long[]> hits, List<long[]> tempos, double bpm, int tsNum, int tsDen, long length, String title) {
    List<long[]> ev = new ArrayList<long[]>();
    // {tick, order, kind, a, b}: kind 0 tempo (a = microseconds), 1 note off, 2 note on.
    if (bpm > 0) ev.add(new long[] {0, 0, 0, Math.round(60000000.0 / Math.max(20, Math.min(400, bpm))), 0});
    for (long[] t : tempos) if (t[0] > 0) ev.add(new long[] {t[0], 0, 0, t[1], 0});
    for (long[] h : hits) {
      ev.add(new long[] {h[0], 2, 2, h[1], h[2]});
      ev.add(new long[] {h[0] + NOTE_TICKS, 1, 1, h[1], 0});
    }
    java.util.Collections.sort(ev, new java.util.Comparator<long[]>() {
      public int compare(long[] a, long[] b) {
        if (a[0] != b[0]) return a[0] < b[0] ? -1 : 1;
        return a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0;
      }
    });
    ByteArrayOutputStream tr = new ByteArrayOutputStream();
    byte[] name = (title == null || title.isEmpty() ? "Hydrogen" : title).getBytes(StandardCharsets.UTF_8);
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
        tr.write((int) e[3]);
        tr.write((int) e[4]);
      }
    }
    vlq(tr, Math.max(0, length - last));
    tr.write(new byte[] {(byte) 0xFF, 0x2F, 0}, 0, 3);
    byte[] body = tr.toByteArray();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, (byte) TICKS}, 0, 14);
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

  private static Element parse(byte[] data) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(false);
    f.setExpandEntityReferences(false);
    try {
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    } catch (Exception ignored) {
      // Not every parser knows it; Hydrogen files have no doctype anyway.
    }
    DocumentBuilder b = f.newDocumentBuilder();
    Document d = b.parse(new ByteArrayInputStream(data));
    return d.getDocumentElement();
  }

  private static Element child(Element e, String tag) {
    for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element && tag.equals(((Element) n).getTagName())) return (Element) n;
    }
    return null;
  }

  private static List<Element> children(Element e, String tag) {
    List<Element> out = new ArrayList<Element>();
    NodeList nl = e.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) {
      Node n = nl.item(i);
      if (n instanceof Element && tag.equals(((Element) n).getTagName())) out.add((Element) n);
    }
    return out;
  }

  private static String text(Element e, String tag) {
    Element c = child(e, tag);
    return c == null ? "" : c.getTextContent().trim();
  }

  private static double num(String s, double dflt) {
    try {
      return s == null || s.isEmpty() ? dflt : Double.parseDouble(s.trim());
    } catch (NumberFormatException ex) {
      return dflt;
    }
  }
}
