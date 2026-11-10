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
    StringBuilder b = new StringBuilder("Pulsekit's pads (Pads tab) and their MIDI notes:\n");
    for (int t = 0; t < Engine.TRACK_ID.length; t++) b.append("  ").append(Engine.NOTES[t]).append("  ").append(Engine.TRACK_LABEL[t]).append('\n');
    b.append("\nMore GM drum notes in songs' MIDI:\n");
    String[][] more = {{"43", "Low floor tom"}, {"44", "Pedal hat"}, {"45", "Low tom"}, {"48", "High-mid tom"}, {"52", "China"}, {"53", "Ride bell"},
      {"55", "Splash"}, {"56", "Cowbell"}, {"57", "Crash 2"}, {"59", "Ride 2"}};
    for (String[] m : more) b.append("  ").append(m[0]).append("  ").append(m[1]).append('\n');
    b.append("\nA line: the MIDI's note=the plugin's pad note (36=48), or 41=none.");
    return b.toString();
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
