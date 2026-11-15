package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * vst_render's Kit loaded rows: a plugin's kits as packs and their sets. "Total Studio Drums: Smells like
 * Nashville" (or "Total Studio Drums:Smells like Nashville") is the set Smells like Nashville of the pack Total
 * Studio Drums; a name without ":" is a kit
 * of its own (Riot's, Anarchy's). The kits come from the runs (the names given with the plugin) and from an
 * optional list, maps/kits.txt: a [plugin] section a plugin (its file's name in it, as map.txt's), a kit a
 * line, "#" comments.
 */
public final class KitPacks {
  private KitPacks() {}

  public static final String FILE = "kits.txt";
  /** The pack of the kits without one, as listed. */
  public static final String NO_PACK = "(no pack)";

  public static final String TEMPLATE = "# Kit names for vst_render's Kit loaded rows (Choose): a [plugin] section a plugin, a kit a line.\n"
      + "# \"Pack: Set\" lists a set under its pack (Total Studio Drums: Smells like Nashville); a name without\n"
      + "# \":\" is a kit of its own. The kits run with a plugin are listed too, without being written here; a set\n"
      + "# listed here goes under the pack this file gives it (Add, Rename and dragging a set onto a pack write here).\n"
      + "# \"Pack:\" alone is a pack without sets yet; \"-Set\" takes a set off the lists (Delete).\n"
      + "\n"
      + "[Ugritone Drums]\n"
      + "# Total Studio Drums: Smells like Nashville\n";

  /** maps/kits.txt in Pulsekit's folder (~/.pulsekit). */
  public static File file(File home) {
    return new File(new File(new File(home, ".pulsekit"), "maps"), FILE);
  }

  /** The kits the list file has for the plugin: its section's lines (the section's name in the plugin's file name, or the other way round). */
  public static List<String> listed(String text, String plugin) {
    List<String> out = new ArrayList<String>();
    if (text == null) return out;
    String want = PluginFiles.mapKey(new File(plugin == null ? "" : plugin.replace('\\', '/')).getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", ""));
    boolean in = false;
    for (String raw : text.split("\n")) {
      int hash = raw.indexOf('#');
      String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
      if (line.isEmpty()) continue;
      if (line.startsWith("[") && line.endsWith("]")) {
        String k = PluginFiles.mapKey(line.substring(1, line.length() - 1));
        in = want.length() > 0 && k.length() > 0 && (want.contains(k) || k.contains(want));
        continue;
      }
      if (in && !out.contains(line)) out.add(line);
    }
    return out;
  }

  /** A kit's pack ("" for none) and set: split at its first ":" ("Pack: Set" or "Pack:Set"; "Pack:" a pack without a set). */
  public static String[] split(String kit) {
    int at = kit.indexOf(':');
    return at > 0 ? new String[] {kit.substring(0, at).trim(), kit.substring(at + 1).trim()} : new String[] {"", kit.trim()};
  }

  /** A pack's name to compare by (case and spaces aside). */
  public static String packKey(String pack) {
    return (pack == null ? "" : pack).replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
  }

  /** A list file line taking a set off the lists ("-Set"), and the set it takes off; null for another line. */
  static String removed(String line) {
    return line.startsWith("-") && line.length() > 1 ? line.substring(1).trim() : null;
  }

  /** A set's name to compare by (case and spaces aside). */
  public static String setKey(String kit) {
    return split(kit)[1].replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
  }

  /**
   * The kits for a plugin, from the runs (`run`, the last used first) and the list file's (`listed`): a
   * set the file lists is under the file's pack only (the runs' name for it left out).
   */
  public static List<String> merged(List<String> run, List<String> listed) {
    java.util.Set<String> inFile = new java.util.HashSet<String>(), gone = new java.util.HashSet<String>();
    for (String k : listed) {
      String off = removed(k);
      if (off != null) gone.add(setKey(off));
      else if (!split(k)[1].isEmpty()) inFile.add(setKey(k));
    }
    List<String> out = new ArrayList<String>();
    for (String k : run) if (!inFile.contains(setKey(k)) && !gone.contains(setKey(k)) && !out.contains(k)) out.add(k);
    for (String k : listed) if (removed(k) == null && !gone.contains(setKey(k)) && !out.contains(k)) out.add(k);
    return out;
  }

  /**
   * The list file's text with the kit in the plugin's section (made at the end when there is none): the
   * section's lines for the same set (under another pack) taken out first.
   */
  public static String put(String text, String plugin, String kit) {
    return edit(text, plugin, kit, true);
  }

  /** put(), or (add false) the plugin's section's lines for the same set (or pack line) only taken out. */
  static String edit(String text, String plugin, String kit, boolean add) {
    String name = new File(plugin == null ? "" : plugin.replace('\\', '/')).getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    String kitOff = removed(kit);
    String want = PluginFiles.mapKey(name), key = setKey(kitOff != null ? kitOff : kit);
    List<String> lines = new ArrayList<String>(java.util.Arrays.asList((text == null || text.isEmpty() ? TEMPLATE : text).split("\n", -1)));
    int sectionEnd = -1;
    boolean in = false;
    for (int i = 0; i < lines.size(); i++) {
      String raw = lines.get(i);
      int hash = raw.indexOf('#');
      String line = (hash >= 0 ? raw.substring(0, hash) : raw).trim();
      if (line.startsWith("[") && line.endsWith("]")) {
        String k = PluginFiles.mapKey(line.substring(1, line.length() - 1));
        in = want.length() > 0 && k.length() > 0 && (want.contains(k) || k.contains(want));
        if (in) sectionEnd = i + 1;
        continue;
      }
      if (!in) continue;
      String off = removed(line);
      boolean same = !line.isEmpty() && (off != null ? setKey(off).equals(key) : key.isEmpty() ? packKey(split(line)[0]).equals(packKey(split(kit)[0])) && split(line)[1].isEmpty() : setKey(line).equals(key));
      if (same) {
        lines.remove(i--);
        continue;
      }
      if (!raw.trim().isEmpty()) sectionEnd = i + 1;
    }
    if (!add) return String.join("\n", lines);
    if (sectionEnd < 0) {
      while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) lines.remove(lines.size() - 1);
      lines.add("");
      lines.add("[" + name + "]");
      lines.add(kit);
      lines.add("");
    } else lines.add(sectionEnd, kit);
    return String.join("\n", lines);
  }

  /** The kits by pack, in the order first met (the kits without one under NO_PACK, last); each pack's sets A to Z. */
  public static Map<String, List<String>> packs(List<String> kits) {
    Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
    // Packs and sets told apart without case ("A taste of Compression" is "A Taste Of Compression"): the first met is shown.
    Map<String, String> packNames = new java.util.HashMap<String, String>();
    java.util.Set<String> seen = new java.util.HashSet<String>();
    List<String> loose = new ArrayList<String>();
    for (String k : kits) {
      String[] p = split(k);
      if (p[1].isEmpty() && p[0].isEmpty()) continue;
      String pack = p[0].isEmpty() ? null : packNames.computeIfAbsent(packKey(p[0]), x -> p[0]);
      List<String> sets = pack == null ? loose : out.computeIfAbsent(pack, x -> new ArrayList<String>());
      if (p[1].isEmpty()) continue;
      if (seen.add(setKey(p[1]))) sets.add(p[1]);
    }
    for (List<String> sets : out.values()) sets.sort(String.CASE_INSENSITIVE_ORDER);
    if (!loose.isEmpty()) {
      loose.sort(String.CASE_INSENSITIVE_ORDER);
      out.put(NO_PACK, loose);
    }
    return out;
  }

  /** The list file's text with the set taken off the plugin's lists ("-Set"; its lines there taken out). */
  public static String remove(String text, String plugin, String set) {
    return put(text, plugin, "-" + split(set)[1]);
  }

  /**
   * The list file's text with a pack renamed: each of its sets (`sets`) put under the new name, and its
   * "Pack:" line (a pack without sets) as well.
   */
  public static String renamePack(String text, String plugin, String pack, String to, List<String> sets) {
    String t = edit(text, plugin, pack + ":", false);
    for (String set : sets) t = put(t, plugin, join(to, set));
    if (sets.isEmpty()) t = put(t, plugin, to + ":");
    return t;
  }

  /** The kit's full name from a pack and a set ("Pack: Set"; the set alone for NO_PACK). */
  public static String join(String pack, String set) {
    return pack == null || pack.isEmpty() || NO_PACK.equals(pack) ? set : pack + ": " + set;
  }
}
