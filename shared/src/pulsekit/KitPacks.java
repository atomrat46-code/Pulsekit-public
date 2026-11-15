package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * vst_render's Kit loaded rows: a plugin's kits as packs and their sets. "Total Studio Drums: Smells like
 * Nashville" is the set Smells like Nashville of the pack Total Studio Drums; a name without ": " is a kit
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
      + "# \": \" is a kit of its own. The kits run with a plugin are listed too, without being written here.\n"
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

  /** A kit's pack ("" for none) and set. */
  public static String[] split(String kit) {
    int at = kit.indexOf(": ");
    return at > 0 ? new String[] {kit.substring(0, at).trim(), kit.substring(at + 2).trim()} : new String[] {"", kit.trim()};
  }

  /** The kits by pack, in the order first met (the kits without one under NO_PACK, last); each pack's sets A to Z. */
  public static Map<String, List<String>> packs(List<String> kits) {
    Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
    List<String> loose = new ArrayList<String>();
    for (String k : kits) {
      String[] p = split(k);
      if (p[1].isEmpty()) continue;
      List<String> sets = p[0].isEmpty() ? loose : out.computeIfAbsent(p[0], x -> new ArrayList<String>());
      if (!sets.contains(p[1])) sets.add(p[1]);
    }
    for (List<String> sets : out.values()) sets.sort(String.CASE_INSENSITIVE_ORDER);
    if (!loose.isEmpty()) {
      loose.sort(String.CASE_INSENSITIVE_ORDER);
      out.put(NO_PACK, loose);
    }
    return out;
  }

  /** The kit's full name from a pack and a set ("Pack: Set"; the set alone for NO_PACK). */
  public static String join(String pack, String set) {
    return pack == null || pack.isEmpty() || NO_PACK.equals(pack) ? set : pack + ": " + set;
  }
}
