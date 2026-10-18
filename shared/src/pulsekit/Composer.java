package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Composer tab: a song built from parts, the same on the phone and the desktop.
 *
 * Building blocks: patterns, Fillerns, fills and silence put on a stack to compose from. Parts:
 * named sections (Intro, Verse, Pre-chorus, Chorus, Bridge, Outro, A1, B2...), each a row of
 * entries: a block, or another part (a subpart), with how many times it repeats. Song form: a row of
 * letters (A A B A), each played as the part it is given; AABA, ABABCB, Verse–Pre-chorus–Chorus
 * and 12-bar blues fill it in. The form (or one part) becomes song parts (Song tab, From Composer).
 * Kept in composer.txt in the app's folder.
 */
public final class Composer {
  public static final String FILE = "composer.txt";
  public static final String PATTERN = "pattern";
  public static final String FILLERN = "fillern";
  public static final String FILL = "fill";
  public static final String SILENT = "silent";
  /** How deep parts may sit in parts (a subpart's subpart...). */
  public static final int MAX_DEPTH = 4;

  /** A building block: what it is (PATTERN, FILLERN, FILL, SILENT), its key (as the Song tab's picker gives it) and name. */
  public static final class Block {
    public String kind;
    public String key;
    public String name;

    public Block(String kind, String key, String name) {
      this.kind = kind == null ? PATTERN : kind;
      this.key = key == null ? "" : key;
      this.name = name == null || name.trim().isEmpty() ? this.key : name.trim();
    }

    public boolean same(Block o) {
      return o != null && this.kind.equals(o.kind) && this.key.equals(o.key);
    }

    /** "Rock (pattern)", "Rock · Toms (Fillern)". */
    public String label() {
      String what = PATTERN.equals(this.kind) ? "pattern" : FILLERN.equals(this.kind) ? "Fillern" : FILL.equals(this.kind) ? "fill" : "silence";
      return SILENT.equals(this.kind) ? "Silent" : this.name + " (" + what + ")";
    }
  }

  /** An entry of a part: a block, or a part played inside it (a subpart); repeated `repeats` times. */
  public static final class Entry {
    public Block block;
    public String part;
    public int repeats;

    public static Entry of(Block b) {
      Entry e = new Entry();
      e.block = b;
      e.repeats = defaultRepeats(b.kind);
      return e;
    }

    public static Entry sub(String part) {
      Entry e = new Entry();
      e.part = part;
      e.repeats = 1;
      return e;
    }

    /** "Rock ×4", "Rock · Toms Fillern ×4", "[Verse] ×2". */
    public String label() {
      String what = this.block == null ? "[" + this.part + "]" : SILENT.equals(this.block.kind) ? "Silent" : FILLERN.equals(this.block.kind) ? this.block.name + " Fillern" : this.block.name;
      return what + " ×" + this.repeats;
    }
  }

  /** A part: its name and entries. */
  public static final class Part {
    public String name;
    public final List<Entry> entries = new ArrayList<Entry>();

    public Part(String name) {
      this.name = name;
    }
  }

  /** The song form's letters and the part each letter plays. */
  public final List<Block> stack = new ArrayList<Block>();
  public final List<Part> parts = new ArrayList<Part>();
  public final List<String> form = new ArrayList<String>();
  public final Map<String, String> letters = new LinkedHashMap<String, String>();
  public String formName = "";

  /** Names offered for a new part. */
  public static final String[] PART_NAMES = {"Intro", "Verse", "Pre-chorus", "Chorus", "Bridge", "Outro", "A1", "A2", "B1", "B2"};

  /** The forms: name, letters, and each letter's part. */
  public static final String[][] FORMS = {
    {"AABA", "A A B A", "A=Verse", "B=Bridge"},
    {"ABABCB", "A B A B C B", "A=Verse", "B=Chorus", "C=Bridge"},
    {"Verse–Pre-chorus–Chorus", "I V P C V P C O", "I=Intro", "V=Verse", "P=Pre-chorus", "C=Chorus", "O=Outro"},
    {"12-bar blues", "A A A", "A=12-bar blues"},
  };

  /** How many times a new entry of this kind repeats: a pattern's bars, a fill's, silence's. */
  public static int defaultRepeats(String kind) {
    if (FILL.equals(kind)) return 1;
    if (SILENT.equals(kind)) return 2;
    return 4;
  }

  /** What a form says about itself under its button. */
  public static String formNote(String name) {
    if ("12-bar blues".equals(name)) return "12 bars each time round: a pattern ×11 and a Fillern or fill makes one";
    if ("AABA".equals(name)) return "32-bar song form: verse, verse, bridge, verse";
    if ("ABABCB".equals(name)) return "verse, chorus, verse, chorus, bridge, chorus";
    return "intro, verse, pre-chorus, chorus twice, outro";
  }

  // ------------------------------------------------------------ editing

  public Part part(String name) {
    for (Part p : this.parts) if (p.name.equals(name)) return p;
    return null;
  }

  /** A block on the stack (not twice); returns the one on the stack. */
  public Block addBlock(Block b) {
    for (Block have : this.stack) if (have.same(b)) return have;
    this.stack.add(b);
    return b;
  }

  /** A file set's patterns and fills as blocks on the stack (Add to composer stack); returns how many are new. */
  public int addFileSet(String source, List<Engine.Learned> learned, List<Engine.LearnedFill> fills) {
    int before = this.stack.size();
    for (Engine.Learned l : learned) if (source.equals(Engine.sourceOf(l))) this.addBlock(new Block(PATTERN, "l:" + l.id, l.name));
    for (Engine.LearnedFill f : fills) if (source.equals(Engine.sourceOf(f))) this.addBlock(new Block(FILL, "l:" + f.id, f.name));
    return this.stack.size() - before;
  }

  /**
   * A long MIDI's song (as the file set `source` made it) split into parts: its patterns, fills and
   * silences in order, a section ending after each fill (or after 8 bars of one pattern turning into
   * another). Sections played alike become one part, named by how the song uses it: the one heard
   * most is the Verse, the next the Chorus, a short one at the start the Intro, a lone one at the
   * end the Outro, the others Bridge... The song form becomes the sections' letters when it is empty;
   * otherwise a part named after the file plays them in order. The blocks go on the stack. Returns
   * the status line.
   */
  public String addSong(String source, List<Engine.Part> song, List<Engine.Learned> learned, List<Engine.LearnedFill> fills) {
    // The song's blocks, the same block one after another as one entry.
    List<Entry> run = new ArrayList<Entry>();
    for (Engine.Part sp : song) {
      Block b = this.blockFor(source, sp, learned, fills);
      if (b == null) continue;
      Entry last = run.isEmpty() ? null : run.get(run.size() - 1);
      int times = Math.max(1, sp.repeats);
      if (last != null && last.block.same(b)) last.repeats += times;
      else {
        Entry e = Entry.of(this.addBlock(b));
        e.repeats = times;
        run.add(e);
      }
    }
    if (run.isEmpty()) return "No patterns in " + source + " for the Composer";
    // Sections: closed after a fill (or a one-bar pattern after a longer one: a fill the file
    // played as a pattern), before a new pattern once 8 bars are in, and after an opening pattern
    // the song never comes back to (an intro).
    List<List<Entry>> sections = new ArrayList<List<Entry>>();
    List<Entry> at = new ArrayList<Entry>();
    int bars = 0;
    for (int i = 0; i < run.size(); i++) {
      Entry e = run.get(i);
      Entry prev = at.isEmpty() ? null : at.get(at.size() - 1);
      boolean split = prev != null && PATTERN.equals(e.block.kind) && e.repeats >= 2 && prev.repeats >= 2 && !FILL.equals(prev.block.kind)
          && (bars >= 8 || !playedFrom(run, i, prev.block));
      if (split) {
        sections.add(at);
        at = new ArrayList<Entry>();
        bars = 0;
      }
      at.add(e);
      bars += e.repeats;
      boolean fillLike = FILL.equals(e.block.kind) || (e.repeats == 1 && prev != null && prev.repeats >= 2 && !prev.block.same(e.block));
      if (fillLike) {
        sections.add(at);
        at = new ArrayList<Entry>();
        bars = 0;
      }
    }
    if (!at.isEmpty()) sections.add(at);
    // Sections alike: one letter each, in the order first heard.
    List<String> keys = new ArrayList<String>();
    List<String> order = new ArrayList<String>();
    Map<String, Integer> heard = new LinkedHashMap<String, Integer>();
    Map<String, List<Entry>> body = new LinkedHashMap<String, List<Entry>>();
    for (List<Entry> sec : sections) {
      StringBuilder k = new StringBuilder();
      for (Entry e : sec) k.append(e.block.kind).append(':').append(e.block.key).append('x').append(e.repeats).append(' ');
      String key = k.toString();
      keys.add(key);
      if (!heard.containsKey(key)) {
        order.add(key);
        body.put(key, sec);
        heard.put(key, 0);
      }
      heard.put(key, heard.get(key) + 1);
    }
    // Names: Intro (short, once, first), Outro (once, last), then Verse, Chorus by how often heard, Bridge...
    Map<String, String> named = new LinkedHashMap<String, String>();
    String first = keys.get(0);
    String lastKey = keys.get(keys.size() - 1);
    if (order.size() > 1 && heard.get(first) == 1 && barsOf(body.get(first)) <= 8) named.put(first, "Intro");
    if (order.size() > 2 && heard.get(lastKey) == 1 && !named.containsKey(lastKey)) named.put(lastKey, "Outro");
    List<String> byHeard = new ArrayList<String>();
    for (String k : order) if (!named.containsKey(k)) byHeard.add(k);
    byHeard.sort((a, b) -> heard.get(b) - heard.get(a));
    String[] next = {"Verse", "Chorus", "Bridge", "B1", "B2", "A1", "A2"};
    for (int i = 0; i < byHeard.size(); i++) named.put(byHeard.get(i), i < next.length ? next[i] : "Part " + (i + 1));
    Map<String, String> partOf = new LinkedHashMap<String, String>();
    for (String k : order) {
      Part p = this.addPart(named.get(k));
      for (Entry e : body.get(k)) {
        Entry copy = Entry.of(e.block);
        copy.repeats = Math.max(1, Math.min(64, e.repeats));
        p.entries.add(copy);
      }
      partOf.put(k, p.name);
    }
    if (this.form.isEmpty()) {
      Map<String, String> letterOf = new LinkedHashMap<String, String>();
      this.letters.clear();
      for (String k : order) {
        String l = String.valueOf((char) ('A' + Math.min(25, letterOf.size())));
        letterOf.put(k, l);
        this.letters.put(l, partOf.get(k));
      }
      for (String k : keys) this.form.add(letterOf.get(k));
      this.formName = source;
      return source + " · " + order.size() + (order.size() == 1 ? " part" : " parts") + " · song form " + this.formLabel();
    }
    Part whole = this.addPart(source);
    for (String k : keys) whole.entries.add(Entry.sub(partOf.get(k)));
    return source + " · " + order.size() + (order.size() == 1 ? " part" : " parts") + ", played in order by the part " + whole.name;
  }

  /** True when `b` is played at `from` or later in the song. */
  private static boolean playedFrom(List<Entry> run, int from, Block b) {
    for (int i = from; i < run.size(); i++) if (run.get(i).block.same(b)) return true;
    return false;
  }

  private static int barsOf(List<Entry> sec) {
    int n = 0;
    for (Entry e : sec) n += e.repeats;
    return n;
  }

  /** The stack block a song part of the file set `source` plays: its pattern, fill, or silence. */
  private Block blockFor(String source, Engine.Part sp, List<Engine.Learned> learned, List<Engine.LearnedFill> fills) {
    String kind = sp.kind == null ? "groove" : sp.kind.toLowerCase();
    if ("fill".equals(kind)) {
      for (Engine.LearnedFill f : fills) if (source.equals(Engine.sourceOf(f)) && f.name.equals(sp.name)) return new Block(FILL, "l:" + f.id, f.name);
      return null;
    }
    if ("rest".equals(kind) || "break".equals(kind)) return new Block(SILENT, "silent", "Silent");
    for (Engine.Learned l : learned) if (source.equals(Engine.sourceOf(l)) && l.name.equals(sp.name)) return new Block(PATTERN, "l:" + l.id, l.name);
    return null;
  }

  /** The status line after a file set's blocks went on the stack. */
  public static String stackedNote(String what, int added) {
    return what + " \u00b7 " + added + (added == 1 ? " block" : " blocks") + " on the Composer's stack";
  }

  public void removeBlock(int i) {
    if (i >= 0 && i < this.stack.size()) this.stack.remove(i);
  }

  /** A new part named `name` ("Verse", or "Verse 2" when there is one); returns it. */
  public Part addPart(String name) {
    String base = name == null || name.trim().isEmpty() ? "Part" : name.trim().replaceAll("[\\t\\n\\r]", " ");
    String n = base;
    for (int i = 2; this.part(n) != null; i++) n = base + " " + i;
    Part p = new Part(n);
    this.parts.add(p);
    return p;
  }

  /** Renames a part, and every subpart entry and form letter that plays it. False when the name is taken. */
  public boolean renamePart(Part p, String name) {
    String n = name == null ? "" : name.trim().replaceAll("[\\t\\n\\r]", " ");
    if (n.isEmpty() || (this.part(n) != null && this.part(n) != p)) return false;
    for (Part q : this.parts) for (Entry e : q.entries) if (p.name.equals(e.part)) e.part = n;
    for (Map.Entry<String, String> l : this.letters.entrySet()) if (p.name.equals(l.getValue())) l.setValue(n);
    p.name = n;
    return true;
  }

  /** Deletes a part, and the subpart entries that play it; form letters that played it play nothing. */
  public void deletePart(Part p) {
    this.parts.remove(p);
    for (Part q : this.parts) q.entries.removeIf(e -> p.name.equals(e.part));
    for (Map.Entry<String, String> l : this.letters.entrySet()) if (p.name.equals(l.getValue())) l.setValue("");
  }

  /** True when `child` may go inside `parent`: not itself, not a part that holds `parent`, not too deep. */
  public boolean canNest(Part parent, Part child) {
    if (parent == null || child == null || parent == child) return false;
    if (this.holds(child, parent.name, 0)) return false;
    return this.depth(child, 0) + this.depthAbove(parent) + 1 <= MAX_DEPTH;
  }

  private boolean holds(Part p, String name, int depth) {
    if (depth > MAX_DEPTH + 2) return true;
    for (Entry e : p.entries) {
      if (e.part == null) continue;
      if (e.part.equals(name)) return true;
      Part q = this.part(e.part);
      if (q != null && this.holds(q, name, depth + 1)) return true;
    }
    return false;
  }

  private int depth(Part p, int at) {
    if (at > MAX_DEPTH + 2) return at;
    int most = 0;
    for (Entry e : p.entries) {
      Part q = e.part == null ? null : this.part(e.part);
      if (q != null) most = Math.max(most, 1 + this.depth(q, at + 1));
    }
    return most;
  }

  private int depthAbove(Part p) {
    int most = 0;
    for (Part q : this.parts) {
      for (Entry e : q.entries) if (p.name.equals(e.part)) most = Math.max(most, 1 + this.depthAbove(q));
    }
    return Math.min(most, MAX_DEPTH + 1);
  }

  /** Fills the song form in from a preset: its letters, and the parts they play (made when not there). */
  public void applyForm(String name) {
    for (String[] f : FORMS) {
      if (!f[0].equals(name)) continue;
      this.formName = name;
      this.form.clear();
      for (String l : f[1].split(" ")) this.form.add(l);
      this.letters.clear();
      for (int i = 2; i < f.length; i++) {
        String[] lp = f[i].split("=", 2);
        if (this.part(lp[1]) == null) this.addPart(lp[1]);
        this.letters.put(lp[0], lp[1]);
      }
      return;
    }
  }

  /** The next free letter (A, B, ... Z) for a letter added by hand. */
  public String nextLetter() {
    for (char c = 'A'; c <= 'Z'; c++) if (!this.letters.containsKey(String.valueOf(c))) return String.valueOf(c);
    return "Z";
  }

  /** "A Verse · A Verse · B Bridge · A Verse". */
  public String formLabel() {
    StringBuilder sb = new StringBuilder();
    for (String l : this.form) {
      String p = this.letters.get(l);
      sb.append(sb.length() == 0 ? "" : " · ").append(l).append(' ').append(p == null || p.isEmpty() ? "(no part)" : p);
    }
    return sb.toString();
  }

  // ------------------------------------------------------------ to song parts

  /** A block's song parts, `repeats` times (the Song tab's own way of making them). */
  public interface Resolver {
    List<Engine.Part> parts(Block block, int repeats);
  }

  /** A part's song parts: its entries in order, a subpart played as its own entries, repeated. */
  public List<Engine.Part> renderPart(String name, Resolver r) {
    List<Engine.Part> out = new ArrayList<Engine.Part>();
    this.render(name, r, out, 0);
    return out;
  }

  private void render(String name, Resolver r, List<Engine.Part> out, int depth) {
    Part p = this.part(name);
    if (p == null || depth > MAX_DEPTH) return;
    for (Entry e : p.entries) {
      int times = Math.max(1, e.repeats);
      if (e.block != null) {
        for (Engine.Part sp : r.parts(e.block, times)) if (out.size() < Engine.MAX_SONG) out.add(sp);
      } else {
        for (int i = 0; i < times && out.size() < Engine.MAX_SONG; i++) this.render(e.part, r, out, depth + 1);
      }
    }
  }

  /** The whole song form's song parts, letter by letter. */
  public List<Engine.Part> renderForm(Resolver r) {
    List<Engine.Part> out = new ArrayList<Engine.Part>();
    for (String l : this.form) {
      String p = this.letters.get(l);
      if (p != null && !p.isEmpty()) this.render(p, r, out, 0);
    }
    return out;
  }

  // ------------------------------------------------------------ kept

  private static String clean(String s) {
    return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
  }

  public String encode() {
    StringBuilder sb = new StringBuilder("PK-COMPOSER\t1\n");
    for (Block b : this.stack) sb.append("S\t").append(clean(b.kind)).append('\t').append(clean(b.key)).append('\t').append(clean(b.name)).append('\n');
    for (Part p : this.parts) {
      sb.append("P\t").append(clean(p.name)).append('\n');
      for (Entry e : p.entries) {
        if (e.block != null) sb.append("E\tb\t").append(clean(e.block.kind)).append('\t').append(clean(e.block.key)).append('\t').append(clean(e.block.name));
        else sb.append("E\tp\t\t").append(clean(e.part)).append('\t');
        sb.append('\t').append(e.repeats).append('\n');
      }
    }
    StringBuilder letters = new StringBuilder();
    for (String l : this.form) letters.append(letters.length() == 0 ? "" : " ").append(l);
    sb.append("F\t").append(clean(this.formName)).append('\t').append(clean(letters.toString())).append('\n');
    for (Map.Entry<String, String> l : this.letters.entrySet()) sb.append("L\t").append(clean(l.getKey())).append('\t').append(clean(l.getValue())).append('\n');
    return sb.toString();
  }

  public static Composer decode(String text) {
    Composer c = new Composer();
    Part at = null;
    for (String line : (text == null ? "" : text).split("\n")) {
      String[] f = line.replace("\r", "").split("\t", -1);
      if (f.length >= 4 && f[0].equals("S")) c.stack.add(new Block(f[1], f[2], f[3]));
      else if (f.length >= 2 && f[0].equals("P")) {
        at = new Part(f[1]);
        c.parts.add(at);
      } else if (f.length >= 6 && f[0].equals("E") && at != null) {
        // E, b or p, the block's kind, key (a subpart's name) and name, then the repeats.
        Entry e = f[1].equals("p") ? Entry.sub(f[3]) : Entry.of(new Block(f[2], f[3], f[4]));
        try {
          e.repeats = Math.max(1, Math.min(64, Integer.parseInt(f[5].trim())));
        } catch (NumberFormatException ignored) {
          // the kind's own
        }
        at.entries.add(e);
      } else if (f.length >= 3 && f[0].equals("F")) {
        c.formName = f[1];
        for (String l : f[2].trim().split(" ")) if (!l.isEmpty()) c.form.add(l);
      } else if (f.length >= 3 && f[0].equals("L")) {
        c.letters.put(f[1], f[2]);
      }
    }
    return c;
  }

  public static Composer load(File dir) {
    try {
      File f = new File(dir, FILE);
      if (f.isFile()) return decode(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // a new one
    }
    return new Composer();
  }

  public void save(File dir) {
    try {
      dir.mkdirs();
      java.nio.file.Files.write(new File(dir, FILE).toPath(), this.encode().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }
}
