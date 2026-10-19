package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picture ready's and Video ready's Add tag…, Add to category… and Add to group…: the tags, the
 * category and the group used last, one set for pictures and one for videos (ready-recent.txt).
 * A file without tags gets the last tags ticked, the last category is the one picked first, and
 * the last group is marked (last used).
 */
public final class ReadyRecent {
  private ReadyRecent() {}

  public static final String IMAGE = "image";
  public static final String VIDEO = "video";
  static final String FILE = "ready-recent.txt";

  /** Add tag…'s first row: every tag ticked, or (when they all are) none. */
  public static final String SELECT_ALL = "Select all";
  public static final String CLEAR_ALL = "Clear all";
  /** After the last group's name in Add to group…. */
  public static final String LAST_USED = " (last used)";

  private static final Map<String, String> VALUES = new LinkedHashMap<String, String>();
  private static File at;

  public static synchronized void load(File dir) {
    File f = new File(dir, FILE);
    VALUES.clear();
    at = f;
    if (!f.isFile()) return;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int tab = line.indexOf('\t');
        if (tab > 0) VALUES.put(line.substring(0, tab), line.substring(tab + 1).replace("\r", ""));
      }
    } catch (Exception ignored) {
      // none remembered
    }
  }

  private static synchronized void save() {
    if (at == null) return;
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : VALUES.entrySet()) sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
    try {
      if (at.getParentFile() != null) at.getParentFile().mkdirs();
      Files.write(at.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this run only
    }
  }

  /** VIDEO for a video's name, else IMAGE. */
  public static String kind(String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    return n.matches(".+\\.(mp4|m4v|webm|mkv|3gp|mov)") ? VIDEO : IMAGE;
  }

  private static synchronized String get(String kind, String what) {
    String v = VALUES.get(kind + "." + what);
    return v == null ? "" : v;
  }

  private static synchronized void put(String kind, String what, String value) {
    if (kind == null) return;
    VALUES.put(kind + "." + what, value == null ? "" : value.replace("\t", " ").replace("\n", " "));
    save();
  }

  /** The tags used last for this kind of file. */
  public static List<String> tags(String kind) {
    List<String> out = new ArrayList<String>();
    for (String t : get(kind, "tags").split(",")) if (t.trim().length() > 0) out.add(t.trim());
    return out;
  }

  public static void setTags(String kind, List<String> tags) {
    List<String> kept = new ArrayList<String>();
    for (String t : tags) if (t != null && t.trim().length() > 0 && !kept.contains(t.trim())) kept.add(t.trim());
    if (!kept.isEmpty()) put(kind, "tags", String.join(",", kept));
  }

  /** The tags ticked first: the file's own, or (when it has none) those used last that are still known. */
  public static List<String> ticked(String kind, List<String> has, List<String> known) {
    if (kind == null || !has.isEmpty()) return has;
    List<String> out = new ArrayList<String>();
    for (String t : tags(kind)) if (known.contains(t)) out.add(t);
    return out;
  }

  public static String category(String kind) {
    return get(kind, "category");
  }

  public static void setCategory(String kind, String category) {
    if (category != null && category.length() > 0) put(kind, "category", category);
  }

  /** The category picked first: the file's own, or the one used last. */
  public static String picked(String kind, String now) {
    return kind == null || (now != null && now.length() > 0) ? now : category(kind);
  }

  public static String group(String kind) {
    return get(kind, "group");
  }

  public static void setGroup(String kind, String group) {
    if (group != null && group.length() > 0) put(kind, "group", group);
  }

  /** Add to group…'s rows with the group used last marked; `groups` are the rows' groups in order. */
  public static String[] marked(String kind, String[] rows, List<String> groups) {
    String last = kind == null ? "" : group(kind);
    String[] out = Arrays.copyOf(rows, rows.length);
    if (last.length() == 0) return out;
    for (int i = 0; i < out.length && i < groups.size(); i++) if (last.equals(groups.get(i))) out[i] = out[i] + LAST_USED;
    return out;
  }

  /** Select all, or Clear all when every tag is ticked. */
  public static String allLabel(boolean[] on) {
    if (on.length == 0) return SELECT_ALL;
    for (boolean b : on) if (!b) return SELECT_ALL;
    return CLEAR_ALL;
  }
}
