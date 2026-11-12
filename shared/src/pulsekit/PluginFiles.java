package pulsekit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * vst_render's Files &amp; locations tab: where Carla's program (Carla.exe) and rack plugin (CarlaVstShell.dll,
 * Carla-Rack) are, kept in ~/.pulsekit (carla-app.txt, carla-rack.txt: vst_render tries them first), and the
 * note map, ~/.pulsekit/maps/map.txt: the notes vst_render sends a plugin (36=48: the MIDI's kick on the
 * plugin's pad 48), edited here with the pads' notes at hand.
 */
public final class PluginFiles {
  private PluginFiles() {}

  public static final String TAB = "Files & locations";
  public static final String CARLA_APP = "Carla program (Carla.exe): opens a 32-bit plugin's window (Edit settings)";
  public static final String CARLA_RACK = "Carla rack plugin (CarlaVstShell.dll or Carla-Rack): plays 32-bit and CLAP plugins";
  public static final String NOTE_MAP = "Note map (map.txt): the notes sent to a plugin whose pads are on other notes";
  public static final String PAD_NOTES = "Pads & notes";

  public static File dir(File home) {
    return new File(home, ".pulsekit");
  }

  /** The note naming chosen (Engine.noteOctaveAt0), kept for Pulsekit and vst_render: ~/.pulsekit/note-names.txt. */
  public static File noteNamesFile(File home) {
    return new File(dir(home), "note-names.txt");
  }

  /** The note naming kept (Engine.noteOctaveAt0 set); -1 (C4 = 60) when none. */
  public static int loadNoteNames(File home) {
    int v = -1;
    try {
      v = Integer.parseInt(kept(noteNamesFile(home)));
    } catch (Exception ex) {
      v = -1;
    }
    boolean known = false;
    for (int k : Engine.NOTE_NAMINGS) known |= k == v;
    Engine.noteOctaveAt0 = known ? v : -1;
    return Engine.noteOctaveAt0;
  }

  public static void saveNoteNames(File home, int octaveAt0) {
    Engine.noteOctaveAt0 = octaveAt0;
    write(noteNamesFile(home), String.valueOf(octaveAt0));
  }

  public static final String NOTE_NAMES = "Note names: how the pads, Test and the note map name notes (plugins differ: DigiDrum's C-4 is note 48)";

  /** The Params page's tab open last (vst_render's: Input, Plugins, Files &amp; locations...), kept across starts. */
  public static File paramsTabFile(File home) {
    return new File(dir(home), "params-tab.txt");
  }

  public static File carlaAppFile(File home) {
    return new File(dir(home), "carla-app.txt");
  }

  public static File carlaRackFile(File home) {
    return new File(dir(home), "carla-rack.txt");
  }

  /** Pulsekit's note map: ~/.pulsekit/maps/map.txt (vst_render's --note_map when the row is empty). */
  public static File noteMap(File home) {
    return new File(new File(dir(home), "maps"), "map.txt");
  }

  /** The path kept in a .txt (carla-app.txt, carla-rack.txt); "" when none. */
  public static String kept(File f) {
    String t = read(f);
    return t == null ? "" : t.trim();
  }

  /** A path kept (empty: the file removed, vst_render looks for itself). */
  public static void keep(File f, String path) {
    if (path == null || path.trim().length() == 0) {
      f.delete();
      return;
    }
    write(f, path.trim());
  }

  /** Whether a file is Carla's program: Carla.exe, or carla (Linux, Mac). */
  public static boolean isCarlaApp(File f) {
    String n = f.getName().toLowerCase(java.util.Locale.ROOT);
    return n.equals("carla.exe") || n.equals("carla");
  }

  /** What a location row shows: the path kept, else what was found by itself, else "Not set". */
  public static String shown(String kept, File found) {
    if (kept.length() > 0) return kept;
    return found != null ? "Not set (found: " + found.getPath() + ")" : "Not set: vst_render looks for it itself";
  }

  /** A new map.txt's text: how its lines go, with an example section. */
  public static final String TEMPLATE = "# Note map: the notes vst_render sends to a plugin (Files & locations: Edit).\n"
      + "# A line is the MIDI's note=the plugin's note: 36=48 plays the MIDI's kick on the plugin's pad 48.\n"
      + "# 41=none leaves a note out. A [section] is for the plugin whose file name has its name in it;\n"
      + "# lines before any section are for every plugin. The MIDI files are not changed.\n"
      + "# A note can be a number or a name in the Note names chosen (36=C-4 for DigiDrum's naming).\n"
      + "# Test (Plugins tab) says which notes a plugin plays.\n"
      + "\n"
      + "[DigiDrum Pro]\n"
      + "# 36=48\n";

  /** The map's text to edit: the file's, else the template. */
  public static String mapText(File f) {
    String t = read(f);
    return t != null && t.length() > 0 ? t : TEMPLATE;
  }

  /** Pulsekit's pads with their MIDI notes, and the other GM drum notes: for writing a note map. */
  public static String padNotes() {
    StringBuilder b = new StringBuilder("Pulsekit's pads (Pads tab) and their MIDI notes (names: " + label() + "):\n");
    for (int t = 0; t < Engine.TRACK_ID.length; t++) b.append("  ").append(row(Engine.NOTES[t], Engine.TRACK_LABEL[t])).append('\n');
    b.append("\nMore GM drum notes in songs' MIDI:\n");
    String[][] more = {{"43", "Low floor tom"}, {"44", "Pedal hat"}, {"45", "Low tom"}, {"48", "High-mid tom"}, {"52", "China"}, {"53", "Ride bell"},
      {"55", "Splash"}, {"56", "Cowbell"}, {"57", "Crash 2"}, {"59", "Ride 2"}};
    for (String[] m : more) b.append("  ").append(row(Integer.parseInt(m[0]), m[1])).append('\n');
    b.append("\nA line: the MIDI's note=the plugin's pad note (36=48, or by name: 36=" + Engine.noteName(48) + "), or 41=none.");
    return b.toString();
  }

  /** "36  C2    Kick": a note, its name, what plays it. */
  static String row(int n, String what) {
    String name = Engine.noteName(n);
    StringBuilder pad = new StringBuilder(name);
    while (pad.length() < 6) pad.append(' ');
    return n + "  " + pad + what;
  }

  /** The naming chosen, in words. */
  public static String label() {
    for (int i = 0; i < Engine.NOTE_NAMINGS.length; i++) if (Engine.NOTE_NAMINGS[i] == Engine.noteOctaveAt0) return Engine.NOTE_NAMING_LABELS[i];
    return Engine.NOTE_NAMING_LABELS[0];
  }

  /** Test's suggested note map (vst_render's "Map suggestion: [Plugin] 36=48 # why" lines): its section and lines. */
  public static final class Suggestion {
    public String section = "";
    public final java.util.List<String> lines = new java.util.ArrayList<String>();
  }

  public static final String SUGGESTION = "Map suggestion: ";

  /** The suggestion in a Test's log; null when it has none. */
  public static Suggestion suggestion(String log) {
    Suggestion s = null;
    for (String l : log.split("\n")) {
      String t = l.trim();
      if (!t.startsWith(SUGGESTION)) continue;
      t = t.substring(SUGGESTION.length());
      int close = t.indexOf(']');
      if (!t.startsWith("[") || close < 0) continue;
      if (s == null) s = new Suggestion();
      s.section = t.substring(1, close);
      s.lines.add(t.substring(close + 1).trim());
    }
    return s;
  }

  /** The suggestion as Test shows it: "[DigiDrum Pro]" and its lines. */
  public static String suggestionText(Suggestion s) {
    StringBuilder b = new StringBuilder("[" + s.section + "]");
    for (String l : s.lines) b.append('\n').append(l);
    return b.toString();
  }

  /**
   * A map's text with a plugin's section set to these lines: the section of that name (case aside) replaced,
   * up to the next section; else added at the end. Lines before any section are kept.
   */
  public static String withSection(String text, String section, java.util.List<String> lines) {
    StringBuilder out = new StringBuilder();
    boolean in = false, done = false;
    String body = "[" + section + "]\n# Suggested by Test from the pads' sounds; edit as needed.\n" + String.join("\n", lines) + "\n";
    for (String l : text.split("\n", -1)) {
      String t = l.trim();
      if (t.startsWith("[") && t.endsWith("]")) {
        if (in) in = false;
        if (!done && t.substring(1, t.length() - 1).trim().equalsIgnoreCase(section.trim())) {
          out.append(body).append('\n');
          in = true;
          done = true;
          continue;
        }
      }
      if (in) continue;
      out.append(l).append('\n');
    }
    String s = out.toString().replaceAll("\n+$", "\n");
    if (!done) s = s + (s.endsWith("\n\n") || s.length() == 0 ? "" : "\n") + body;
    return s;
  }

  /** The suggestion put in map.txt (its plugin's section replaced, or added). */
  public static boolean addSuggestion(File home, Suggestion s) {
    File map = noteMap(home);
    return write(map, withSection(mapText(map), s.section, s.lines));
  }

  private static final String[] LETTERS = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

  /** A note given as a number (48) or a name in the naming chosen (C-4, C#3, Db3); -1 when it is neither. */
  public static int noteNumber(String text) {
    String t = text.trim();
    if (t.matches("\\d+")) {
      int n = Integer.parseInt(t);
      return n <= 127 ? n : -1;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Ga-g])([#b]?)(-?\\d+)$").matcher(t);
    if (!m.matches()) return -1;
    int o = Engine.noteOctaveAt0;
    int letter = java.util.Arrays.asList(LETTERS).indexOf(m.group(1).toUpperCase(java.util.Locale.ROOT));
    // DigiDrum's naming writes its octave after a dash (C-4): a dash there is no minus.
    int octave = o >= 0 ? Integer.parseInt(m.group(3).replace("-", "")) : Integer.parseInt(m.group(3));
    int n = letter + ("#".equals(m.group(2)) ? 1 : "b".equals(m.group(2)) ? -1 : 0) + 12 * (octave - o);
    return n >= 0 && n <= 127 ? n : -1;
  }

  /** A section's name to match on (as vst_render's map_key): letters and digits, lower case. */
  static String mapKey(String name) {
    return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  private static final int[] TOM_NOTES = {41, 43, 45, 47, 48, 50};

  private static String kickSnareName(int n) {
    return n == 35 || n == 36 ? "kick" : n == 37 ? "side stick" : n == 39 ? "clap" : "snare";
  }

  /**
   * Clear errors in a note map's text (map.txt), the ones vst_render prints at a run (map_problems): a line
   * that is no note=note; a note that is no note; the kick or snare left out (36=none, 38=none); a song's
   * tom sent to the kick's or snare's notes (50=38, 48=37); a note given twice in a section with different
   * notes; a section twice (its lines are read as one).
   */
  public static java.util.List<String> mapProblems(String text) {
    java.util.List<String> out = new java.util.ArrayList<String>();
    java.util.Map<String, Integer> sections = new java.util.HashMap<String, Integer>();
    java.util.Map<Integer, Integer> given = new java.util.HashMap<Integer, Integer>();
    String section = null;
    String[] lines = text.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String raw = lines[i];
      int hash = raw.indexOf('#');
      String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
      if (line.length() == 0) continue;
      if (line.startsWith("[") && line.endsWith("]") && line.length() > 2) {
        section = line.substring(1, line.length() - 1).trim();
        given.clear();
        String key = mapKey(section);
        if (sections.containsKey(key)) out.add("[" + section + "] is there twice (lines " + sections.get(key) + " and " + (i + 1) + "): their lines are read as one section");
        else sections.put(key, Integer.valueOf(i + 1));
        continue;
      }
      String where = section != null ? "[" + section + "]" : "(every plugin)";
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([^=\\s]+)\\s*=\\s*([^=\\s]+)$").matcher(line);
      if (!m.matches()) {
        out.add("line " + (i + 1) + ": " + line + " is not note=note (36=48, or 41=none)");
        continue;
      }
      int a = noteNumber(m.group(1));
      boolean none = m.group(2).equalsIgnoreCase("none");
      int b = none ? -2 : noteNumber(m.group(2));
      if (a < 0 || b == -1) {
        out.add("line " + (i + 1) + ": " + line + ": no such note (a number 0-127, or a name such as " + Engine.noteName(36) + ")");
        continue;
      }
      Integer before = given.get(Integer.valueOf(a));
      if (before != null && before.intValue() != b)
        out.add(where + " " + a + " is given twice (" + a + "=" + (before.intValue() == -2 ? "none" : before.toString()) + ", " + a + "=" + (none ? "none" : String.valueOf(b)) + "): the last is used");
      given.put(Integer.valueOf(a), Integer.valueOf(b));
      boolean tom = false;
      for (int t : TOM_NOTES) tom |= t == a;
      if (none && (a == 36 || a == 38)) out.add(where + " " + a + "=none: every " + (a == 36 ? "kick" : "snare") + " of the songs is left out");
      else if (tom && b >= 35 && b <= 40) out.add(where + " " + a + "=" + b + ": the songs' tom " + a + " plays on " + b + ", the " + kickSnareName(b) + "'s note");
    }
    return out;
  }

  /**
   * The section (its [name] line to the line before the next section, or the end) the offset is in:
   * {start, end} offsets in the text; null for the lines before any section.
   */
  public static int[] sectionAt(String text, int offset) {
    int start = -1;
    int pos = 0;
    for (String line : text.split("\n", -1)) {
      int end = pos + line.length() + 1;
      String t = line.trim();
      boolean head = t.startsWith("[") && t.endsWith("]") && t.length() > 2;
      if (head) {
        if (pos > offset && start >= 0) return new int[] {start, pos};
        if (pos > offset) return null;
        start = pos;
      }
      pos = end;
    }
    return start >= 0 ? new int[] {start, text.length()} : null;
  }

  /** The section's name at the start of {start, end} ("[Ugritone Drums]" without its brackets). */
  public static String sectionName(String text, int[] at) {
    String first = text.substring(at[0], Math.min(text.length(), at[1])).split("\n", 2)[0].trim();
    return first.substring(1, first.length() - 1).trim();
  }

  /** Where `find` is next in the text from `from` on (case aside), wrapping round to the start; -1 when nowhere. */
  public static int findNext(String text, String find, int from) {
    if (find == null || find.length() == 0) return -1;
    String low = text.toLowerCase(java.util.Locale.ROOT), f = find.toLowerCase(java.util.Locale.ROOT);
    int at = low.indexOf(f, Math.max(0, Math.min(from, text.length())));
    return at >= 0 ? at : low.indexOf(f);
  }

  /** The text with every `find` (case aside) replaced; and how many, in count[0]. */
  public static String replaceAll(String text, String find, String with, int[] count) {
    count[0] = 0;
    if (find == null || find.length() == 0) return text;
    StringBuilder b = new StringBuilder();
    String low = text.toLowerCase(java.util.Locale.ROOT), f = find.toLowerCase(java.util.Locale.ROOT);
    int from = 0;
    for (int at; (at = low.indexOf(f, from)) >= 0; from = at + f.length()) {
      b.append(text, from, at).append(with);
      count[0]++;
    }
    return b.append(text.substring(from)).toString();
  }

  /** The text without the section at {start, end}, the blank lines around it kept to one. */
  public static String withoutSection(String text, int[] at) {
    String before = text.substring(0, at[0]).replaceAll("\n+$", "\n"), after = text.substring(at[1]).replaceAll("^\n+", "");
    String out = before + (before.endsWith("\n\n") || before.length() == 0 || after.length() == 0 ? "" : "\n") + after;
    return out.replaceAll("\n{3,}", "\n\n");
  }

  /**
   * A section pasted (text copied with Copy section: its [name] line and lines) after the section at
   * {start, end}, or at the end when `at` is null; a blank line between sections.
   */
  public static String pasteSection(String text, int[] at, String section) {
    String clip = section.replaceAll("^\n+", "").replaceAll("\n+$", "") + "\n";
    int where = at == null ? text.length() : at[1];
    String before = text.substring(0, where).replaceAll("\n*$", ""), after = text.substring(where).replaceAll("^\n+", "");
    return (before.length() == 0 ? "" : before + "\n\n") + clip + (after.length() == 0 ? "" : "\n" + after);
  }

  /**
   * Whether a file's text is a note map to import: some note=note line, and none that is not one (or names
   * no note). Null when it is; else why not.
   */
  public static String notAMap(String text) {
    if (text == null) return "it could not be read";
    boolean pair = false;
    for (String raw : text.split("\n", -1)) {
      int hash = raw.indexOf('#');
      String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
      if (line.length() == 0 || line.startsWith("[") && line.endsWith("]")) continue;
      pair = true;
    }
    if (!pair) return "it has no note=note line";
    for (String p : mapProblems(text)) if (p.contains(" is not note=note") || p.contains(": no such note")) return p;
    return null;
  }

  /** A backup of map.txt beside it: map-backup-<yyyyMMdd-HHmmss>.txt; null when there is no map.txt. */
  public static File backupMap(File home) {
    File map = noteMap(home);
    String text = read(map);
    if (text == null) return null;
    String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(new java.util.Date());
    File to = new File(map.getParentFile(), "map-backup-" + stamp + ".txt");
    for (int k = 2; to.exists(); k++) to = new File(map.getParentFile(), "map-backup-" + stamp + "-" + k + ".txt");
    return write(to, text) ? to : null;
  }

  /** map.txt replaced with the text imported, a backup of it made first; the backup (null: there was no map.txt). */
  public static File importMap(File home, String text) {
    File backup = backupMap(home);
    write(noteMap(home), text);
    return backup;
  }

  public static String read(File f) {
    if (f == null || !f.isFile()) return null;
    try (InputStream in = new FileInputStream(f)) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
      return new String(out.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return null;
    }
  }

  public static boolean write(File f, String text) {
    try {
      if (f.getParentFile() != null) f.getParentFile().mkdirs();
      try (OutputStream out = new FileOutputStream(f)) {
        out.write(text.getBytes(StandardCharsets.UTF_8));
      }
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}
