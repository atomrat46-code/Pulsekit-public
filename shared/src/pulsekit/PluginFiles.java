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
