package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups of files (Add to group, on a thumbnail's long press in Browse DB, the Reference and Result
 * file galleries and the Media browser): named groups, each a list of files: a prompt library file
 * by its place in the library ("db:3:42"), a file elsewhere by its path or address ("file:/sdcard/..").
 * G, by the A I V S T letters, shows one group's files; a long press on G picks the group. Kept in
 * file-groups.txt in the app's folder.
 */
public final class FileGroups {
  private FileGroups() {}

  static final String FILE = "file-groups.txt";
  public static final String LETTER = "G";
  public static final String ADD = "Add to group";
  public static final String NEW = "New group…";

  private static File dir;
  private static final Map<String, List<String>> GROUPS = new LinkedHashMap<String, List<String>>();

  /** The group G shows ("" until one is picked). */
  public static volatile String shown = "";

  public static synchronized void load(File folder) {
    if (folder != null && folder.equals(dir)) return;
    dir = folder;
    GROUPS.clear();
    File f = folder == null ? null : new File(folder, FILE);
    if (f == null || !f.isFile()) return;
    try {
      List<String> at = null;
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.replace("\r", "").split("\t", 2);
        if (p.length < 2) continue;
        if (p[0].equals("G")) {
          at = new ArrayList<String>();
          GROUPS.put(p[1], at);
        } else if (p[0].equals("F") && at != null && !at.contains(p[1])) {
          at.add(p[1]);
        } else if (p[0].equals("S")) {
          shown = p[1];
        }
      }
    } catch (Exception ignored) {
      // none kept
    }
    if (!GROUPS.containsKey(shown)) shown = "";
  }

  /** Forgets what was read (the tests start again with another folder). */
  public static synchronized void reset() {
    dir = null;
    GROUPS.clear();
    shown = "";
  }

  private static void save() {
    if (dir == null) return;
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) {
      sb.append("G\t").append(g.getKey()).append('\n');
      for (String k : g.getValue()) sb.append("F\t").append(k).append('\n');
    }
    if (shown.length() > 0) sb.append("S\t").append(shown).append('\n');
    try {
      java.nio.file.Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }

  /** A prompt library file's key. */
  public static String key(PromptVault.StoredFile f) {
    return f == null ? "" : "db:" + f.which + ":" + f.versionId;
  }

  /** A file's key by its path or address. */
  public static String fileKey(String path) {
    return "file:" + (path == null ? "" : path);
  }

  /** The groups, in the order they were made. */
  public static synchronized List<String> names() {
    return new ArrayList<String>(GROUPS.keySet());
  }

  /** A group's name as a new group gets it: trimmed, one line; "" when there is none. */
  public static String clean(String name) {
    return name == null ? "" : name.replaceAll("[\\t\\r\\n]+", " ").trim();
  }

  public static synchronized boolean in(String group, String key) {
    List<String> g = GROUPS.get(group);
    return g != null && g.contains(key);
  }

  /** The groups a file is in. */
  public static synchronized List<String> groupsOf(String key) {
    List<String> out = new ArrayList<String>();
    for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) if (g.getValue().contains(key)) out.add(g.getKey());
    return out;
  }

  /** The file put in the group (made when it is new), or taken out when it is there; true when it is in it now. */
  public static synchronized boolean toggle(String group, String key) {
    String name = clean(group);
    if (name.isEmpty() || key == null || key.isEmpty()) return false;
    List<String> g = GROUPS.get(name);
    if (g == null) {
      g = new ArrayList<String>();
      GROUPS.put(name, g);
    }
    boolean now = !g.remove(key);
    if (now) g.add(key);
    save();
    return now;
  }

  /** The file in the group (made when it is new), or out of it. */
  public static synchronized void set(String group, String key, boolean in) {
    String name = clean(group);
    if (name.isEmpty() || key == null || key.isEmpty()) return;
    List<String> g = GROUPS.get(name);
    if (g == null) {
      if (!in) return;
      g = new ArrayList<String>();
      GROUPS.put(name, g);
    }
    if (in && !g.contains(key)) g.add(key);
    if (!in) g.remove(key);
    save();
  }

  /** As toggle, for a file known by several keys (a run's file and its copies in the library): all go in, or all out. */
  public static synchronized boolean toggle(String group, String[] keys) {
    boolean now = toggle(group, keys[0]);
    for (int i = 1; i < keys.length; i++) set(group, keys[i], now);
    return now;
  }

  /** The Add to group list: each group (✓ when the file is in it), then New group…. */
  public static synchronized String[] menu(String key) {
    List<String> out = new ArrayList<String>();
    for (Map.Entry<String, List<String>> g : GROUPS.entrySet()) out.add(g.getKey() + (g.getValue().contains(key) ? " ✓" : ""));
    out.add(NEW);
    return out.toArray(new String[0]);
  }

  /** The group a menu row stands for (null for New group…). */
  public static synchronized String groupAt(int row) {
    List<String> n = names();
    return row >= 0 && row < n.size() ? n.get(row) : null;
  }

  /** The status line after Add to group. */
  public static String note(String file, String group, boolean now) {
    return file + (now ? " added to " : " taken out of ") + group;
  }

  /** G picked: the group it shows (kept). */
  public static synchronized void show(String group) {
    shown = GROUPS.containsKey(group) ? group : "";
    save();
  }

  /** G's label: "G", or "G · Trip" when a group is picked. */
  public static String label() {
    return shown.isEmpty() ? LETTER : LETTER + " · " + shown;
  }

  /** Whether a file shows under G: it is in the group G shows. */
  public static boolean fits(String key) {
    return !shown.isEmpty() && in(shown, key);
  }
}
