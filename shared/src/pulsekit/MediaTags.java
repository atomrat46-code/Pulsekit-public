package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Media browser's tags and categories for files (Tags… and Category… in a file's menu), as the
 * prompt library has them for prompts: any number of tags ("beach", "night") and one category (one
 * of the library's, "Video / Clips"). A file is known by its name and size, so they stay with it
 * when it is moved or copied, and Rename carries them over. Kept in media-tags.txt in the app's
 * folder, the same on the phone and the desktop.
 */
public final class MediaTags {
  private MediaTags() {}

  static final String FILE = "media-tags.txt";

  /** The tag filter of the Media browser ("" all files; "#beach" one tag; "@Video" one category). */
  public static String filter = "";

  private static File dir;
  /** Each file's category and tags (key "name|size"); the tags made so far, also those no file has now. */
  private static final Map<String, String[]> FILES = new LinkedHashMap<String, String[]>();
  private static final List<String> KNOWN = new ArrayList<String>();

  /** Reads the store kept in `folder` (once for each folder; again after reset). */
  public static synchronized void load(File folder) {
    if (folder != null && folder.equals(dir)) return;
    dir = folder;
    FILES.clear();
    KNOWN.clear();
    File f = folder == null ? null : new File(folder, FILE);
    if (f == null || !f.isFile()) return;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.replace("\r", "").split("\t", -1);
        if (p.length >= 2 && p[0].equals("T")) addKnownTag(p[1]);
        else if (p.length >= 4 && p[0].equals("F")) FILES.put(p[1], new String[] {p[2], p[3]});
      }
    } catch (Exception ignored) {
      // none kept
    }
  }

  /** Forgets what was read (the tests start again with another folder). */
  public static synchronized void reset() {
    dir = null;
    FILES.clear();
    KNOWN.clear();
    filter = "";
  }

  private static void save() {
    if (dir == null) return;
    StringBuilder sb = new StringBuilder();
    for (String t : KNOWN) sb.append("T\t").append(t).append('\n');
    for (Map.Entry<String, String[]> e : FILES.entrySet()) sb.append("F\t").append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t').append(e.getValue()[1]).append('\n');
    try {
      dir.mkdirs();
      java.nio.file.Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }

  /** A file's key: its name and size. */
  public static String key(String name, long size) {
    return (name == null ? "" : name.replace('\t', ' ').replace('\n', ' ')) + "|" + size;
  }

  public static String key(MediaDir.Entry e) {
    return key(e.name, e.size);
  }

  /** A tag as typed: no #, tab, ; or line break; "" when nothing is left. */
  public static String cleanTag(String typed) {
    String t = typed == null ? "" : typed.replaceAll("[\\t\\n\\r;]", " ").trim();
    while (t.startsWith("#")) t = t.substring(1).trim();
    t = t.replaceAll("\\s+", " ");
    return t.length() > 40 ? t.substring(0, 40).trim() : t;
  }

  private static void addKnownTag(String typed) {
    String t = cleanTag(typed);
    if (t.length() == 0) return;
    for (String k : KNOWN) if (k.equalsIgnoreCase(t)) return;
    KNOWN.add(t);
  }

  /** A new tag for the lists (Tags… "New tag"), kept even before a file has it. Returns it cleaned. */
  public static synchronized String addTag(String typed) {
    String t = cleanTag(typed);
    addKnownTag(t);
    save();
    return t;
  }

  /** Every tag made so far, by name. */
  public static synchronized List<String> known() {
    List<String> out = new ArrayList<String>(KNOWN);
    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    return out;
  }

  public static synchronized List<String> tags(String key) {
    String[] v = FILES.get(key);
    List<String> out = new ArrayList<String>();
    if (v == null || v[1].length() == 0) return out;
    for (String t : v[1].split(";")) if (t.length() > 0) out.add(t);
    return out;
  }

  public static synchronized String category(String key) {
    String[] v = FILES.get(key);
    return v == null ? "" : v[0];
  }

  /** The file's tags, in place of those it had (none: taken off). */
  public static synchronized void setTags(String key, List<String> tags) {
    StringBuilder sb = new StringBuilder();
    List<String> seen = new ArrayList<String>();
    for (String typed : tags) {
      String t = cleanTag(typed);
      if (t.length() == 0 || seen.contains(t.toLowerCase(Locale.ROOT))) continue;
      seen.add(t.toLowerCase(Locale.ROOT));
      addKnownTag(t);
      sb.append(sb.length() == 0 ? "" : ";").append(t);
    }
    put(key, category(key), sb.toString());
  }

  /** The file's category ("" none). */
  public static synchronized void setCategory(String key, String category) {
    String c = category == null ? "" : category.replaceAll("[\\t\\n\\r]", " ").trim();
    String[] v = FILES.get(key);
    put(key, c, v == null ? "" : v[1]);
  }

  private static void put(String key, String category, String tags) {
    if (category.length() == 0 && tags.length() == 0) FILES.remove(key);
    else FILES.put(key, new String[] {category, tags});
    save();
  }

  /** Rename: the file's tags and category go with its new name. */
  public static synchronized void renamed(String oldKey, String newKey) {
    String[] v = FILES.remove(oldKey);
    if (v == null || oldKey.equals(newKey)) {
      if (v != null) FILES.put(oldKey, v);
      return;
    }
    FILES.put(newKey, v);
    save();
  }

  /** Delete: the file's tags and category go too. */
  public static synchronized void deleted(String key) {
    if (FILES.remove(key) != null) save();
  }

  /** What a card shows under the name: "#beach #night · Video", or "". */
  public static synchronized String line(String key) {
    StringBuilder sb = new StringBuilder();
    for (String t : tags(key)) sb.append(sb.length() == 0 ? "#" : " #").append(t);
    String c = category(key);
    if (c.length() > 0) sb.append(sb.length() == 0 ? "" : " · ").append(c);
    return sb.toString();
  }

  /** The files of `in` that the filter keeps ("#tag", "@category"; "" all of them). */
  public static synchronized List<MediaDir.Entry> filtered(List<MediaDir.Entry> in, String f) {
    if (f == null || f.length() < 2) return in;
    List<MediaDir.Entry> out = new ArrayList<MediaDir.Entry>();
    for (MediaDir.Entry e : in) if (!e.folder && has(key(e), f)) out.add(e);
    return out;
  }

  static boolean has(String key, String f) {
    String want = f.substring(1);
    if (f.startsWith("@")) return category(key).equalsIgnoreCase(want) || category(key).toLowerCase(Locale.ROOT).startsWith(want.toLowerCase(Locale.ROOT) + " / ");
    for (String t : tags(key)) if (t.equalsIgnoreCase(want)) return true;
    return false;
  }

  /**
   * The filter's choices for these files: "All files", then each tag and category they have, with
   * how many ("#beach (3)", "@Video (1)"); [0] what is shown, [1] the filter.
   */
  public static synchronized List<String[]> choices(List<MediaDir.Entry> in) {
    Map<String, Integer> tagCount = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
    Map<String, Integer> catCount = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
    for (MediaDir.Entry e : in) {
      if (e.folder) continue;
      String k = key(e);
      for (String t : tags(k)) tagCount.put(t, (tagCount.containsKey(t) ? tagCount.get(t) : 0) + 1);
      String c = category(k);
      if (c.length() > 0) catCount.put(c, (catCount.containsKey(c) ? catCount.get(c) : 0) + 1);
    }
    List<String[]> out = new ArrayList<String[]>();
    out.add(new String[] {"All files", ""});
    for (Map.Entry<String, Integer> e : tagCount.entrySet()) out.add(new String[] {"#" + e.getKey() + " (" + e.getValue() + ")", "#" + e.getKey()});
    for (Map.Entry<String, Integer> e : catCount.entrySet()) out.add(new String[] {"@" + e.getKey() + " (" + e.getValue() + ")", "@" + e.getKey()});
    return out;
  }

  /** The prompt library's categories as Category… lists them: "Video", "Video / Clips"... in the library's order. */
  public static List<String> categoryNames(PromptVault vault) {
    List<String> out = new ArrayList<String>();
    if (vault == null) return out;
    List<PromptVault.Category> all = vault.categories();
    Map<Long, PromptVault.Category> byId = new java.util.HashMap<Long, PromptVault.Category>();
    for (PromptVault.Category c : all) byId.put(c.id, c);
    for (PromptVault.Category c : all) {
      StringBuilder name = new StringBuilder(c.name);
      PromptVault.Category up = byId.get(c.parentId);
      for (int depth = 0; up != null && depth < 10; depth++) {
        name.insert(0, up.name + " / ");
        up = byId.get(up.parentId);
      }
      out.add(name.toString());
    }
    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    return out;
  }

  /** Category…'s rows after the categories: a new main category, a new subcategory (of one picked), or a new one the file goes in. */
  public static final String NEW_CATEGORY = "+ Add new category\u2026";
  public static final String NEW_SUBCATEGORY = "+ Add new subcategory\u2026";
  public static final String INTO_NEW = "+ Add into new category\u2026";

  /** Category…'s rows: (none), the categories, then the three Add rows. */
  public static List<String> categoryRows(PromptVault vault) {
    List<String> out = new ArrayList<String>();
    out.add("(none)");
    out.addAll(categoryNames(vault));
    out.add(NEW_CATEGORY);
    out.add(NEW_SUBCATEGORY);
    out.add(INTO_NEW);
    return out;
  }

  /** The category of this path ("Video / Clips"), or 0. */
  public static long categoryId(PromptVault vault, String path) {
    if (vault == null || path == null) return 0;
    for (PromptVault.Category c : vault.categories()) if (path.equals(vault.pathName(c.id))) return c.id;
    return 0;
  }

  /** A new category (under `parentPath`, or a main one when it is null or empty): its path. */
  public static String addCategory(PromptVault vault, String parentPath, String name) throws Exception {
    long parent = parentPath == null || parentPath.length() == 0 ? 0 : categoryId(vault, parentPath);
    if (parentPath != null && parentPath.length() > 0 && parent == 0) throw new IllegalArgumentException("No category " + parentPath);
    String path = (parent == 0 ? "" : parentPath + " / ") + (name == null ? "" : name.trim());
    if (categoryId(vault, path) != 0) throw new IllegalArgumentException("There is a category " + path + " already");
    long id = parent == 0 ? vault.addCategory(name) : vault.addSubcategory(parent, name);
    return vault.pathName(id);
  }

  /** Picture ready's and Video ready's: the same, worded for a file just made. */
  public static final String ADD_TAG = "Add tag\u2026";
  public static final String ADD_CATEGORY = "Add to category\u2026";

  /** The menu items Tags… and Category… */
  public static final String TAGS = "Tags…";
  public static final String CATEGORY = "Category…";
}
