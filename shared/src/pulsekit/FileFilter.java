package pulsekit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The thumbnail screens' filters past the type letter (A I V S T, O in the Media browser): F, G
 * and C are switches that go with it (V with F on: the favourite videos), and T1, T2, T3 pick tags.
 * F: favourites. G: the group G shows (FileGroups.shown). C: a category, or under it (a prompt
 * library file's own category, or its prompt's; a file elsewhere by Category…). T1–T3: files with
 * any of the tags picked (a file's own tags, and its prompt's). Kept while the program runs.
 */
public final class FileFilter {
  private FileFilter() {}

  public static final String FAV = FileFavourites.LETTER;
  public static final String GROUP = FileGroups.LETTER;
  public static final String CAT = "C";
  /** The tag lists' first row: no tag picked. */
  public static final String ALL = "All";
  public static final String NO_CATEGORY = "No category filter";
  public static final String UP = "\u2191 Up a level";

  public static volatile boolean fav;
  public static volatile boolean group;
  public static volatile boolean cat;
  /** The category C shows ("Image / Portraits"), "" until one is picked. */
  public static volatile String category = "";
  /** T1, T2, T3: "" is All. */
  public static final String[] tags = {"", "", ""};

  /** Everything off, on every screen (the tests start again). */
  public static synchronized void reset() {
    clear();
    KEPT.clear();
    using = "";
  }

  private static void clear() {
    fav = false;
    group = false;
    cat = false;
    category = "";
    for (int i = 0; i < tags.length; i++) tags[i] = "";
  }

  /** The screens with switches of their own: Browse DB, the Ref / Result / Prompt file galleries, the Media browser. */
  public static final String BROWSE = "browse";
  public static final String GALLERY = "gallery";
  public static final String MEDIA = "media";

  /**
   * Each screen's switches (F on in Browse DB left the Media browser showing only favourites: its
   * folders looked empty). The switches above are the screen's in use; the others are kept here.
   */
  private static final Map<String, Object[]> KEPT = new HashMap<String, Object[]>();
  private static String using = "";

  /** The screen whose switches are in use. */
  public static synchronized String using() {
    return using;
  }

  /** `screen`'s switches in use (those in use till now kept for their screen). */
  public static synchronized void use(String screen) {
    if (screen == null || screen.equals(using)) return;
    KEPT.put(using, new Object[] {fav, group, cat, category, tags.clone()});
    Object[] s = KEPT.get(screen);
    clear();
    if (s != null) {
      fav = (Boolean) s[0];
      group = (Boolean) s[1];
      cat = (Boolean) s[2];
      category = (String) s[3];
      System.arraycopy((String[]) s[4], 0, tags, 0, tags.length);
    }
    using = screen;
  }

  /** The empty screen's line when the switches keep everything out: "Nothing here matches Favourites, Tags: beach: turn …". */
  public static String nothing() {
    return "Nothing here matches" + title().replace(" · ", ", ").replaceFirst("^,", "") + ": turn the lit F G C or the T lists off to see all";
  }

  /** F, G and C, by the type letters. */
  public static boolean isToggle(String letter) {
    return FAV.equals(letter) || GROUP.equals(letter) || CAT.equals(letter);
  }

  /** A letter kept from before F and G were switches: F or G turns its switch on, and the type is A. */
  public static String normal(String letter) {
    if (FAV.equals(letter)) {
      fav = true;
      return "A";
    }
    if (GROUP.equals(letter)) {
      group = true;
      return "A";
    }
    return letter == null || CAT.equals(letter) ? "A" : letter;
  }

  public static boolean on(String toggle) {
    if (FAV.equals(toggle)) return fav;
    if (GROUP.equals(toggle)) return group;
    if (CAT.equals(toggle)) return cat;
    return false;
  }

  public static void set(String toggle, boolean on) {
    if (FAV.equals(toggle)) fav = on;
    else if (GROUP.equals(toggle)) group = on;
    else if (CAT.equals(toggle)) cat = on;
  }

  /** G with a group to show. */
  static boolean groupOn() {
    return group && !FileGroups.shown.isEmpty();
  }

  /** C with a category to show. */
  static boolean catOn() {
    return cat && category.length() > 0;
  }

  public static boolean tagsOn() {
    for (String t : tags) if (t.length() > 0) return true;
    return false;
  }

  /** Any of F, G, C or a tag keeps files out. */
  public static boolean active() {
    return fav || groupOn() || catOn() || tagsOn();
  }

  /** The tags picked, in order. */
  public static List<String> picked() {
    List<String> out = new ArrayList<String>();
    for (String t : tags) if (t.length() > 0 && !out.contains(t)) out.add(t);
    return out;
  }

  /** A tag list's label: "T1: All", "T2: beach". */
  public static String tagLabel(int i) {
    return "T" + (i + 1) + ": " + (tags[i].length() == 0 ? ALL : tags[i]);
  }

  /** A tag list's rows: All, then every tag. */
  public static String[] tagRows(List<String> choices) {
    List<String> out = new ArrayList<String>();
    out.add(ALL);
    if (choices != null) out.addAll(choices);
    return out.toArray(new String[0]);
  }

  /** A tag list's row picked (All: none). */
  public static void pickTag(int i, String row) {
    tags[i] = row == null || ALL.equals(row) ? "" : row;
  }

  /** The category C picked (cleared by "" or null): C is on with it. */
  public static void pickCategory(String path) {
    category = path == null ? "" : path;
    cat = category.length() > 0;
  }

  /** For the screen's title: " · Favourites · Group: Trip · Category: Image / Portraits · Tags: beach, night"; "" when nothing is on. */
  public static String title() {
    StringBuilder sb = new StringBuilder();
    if (fav) sb.append(" · Favourites");
    if (groupOn()) sb.append(" · Group: ").append(FileGroups.shown);
    if (catOn()) sb.append(" · Category: ").append(category);
    if (tagsOn()) sb.append(" · Tags: ").append(String.join(", ", picked()));
    return sb.toString();
  }

  /** The switch's description. */
  public static String label(String toggle) {
    if (FAV.equals(toggle)) return "Favourites (with the type picked)";
    if (GROUP.equals(toggle)) return "A group (held: the groups)";
    if (CAT.equals(toggle)) return "A category (held: the categories)";
    return toggle;
  }

  /** Whether `path` is the category picked or under it ("Image / Portraits" is under "Image"). */
  public static boolean under(String path, String picked) {
    if (path == null || path.length() == 0) return false;
    String p = path.toLowerCase(Locale.ROOT);
    String c = picked.toLowerCase(Locale.ROOT);
    return p.equals(c) || p.startsWith(c + " / ");
  }

  private static boolean anyTag(List<String> has) {
    for (String t : has) for (String want : tags) if (want.length() > 0 && want.equalsIgnoreCase(t)) return true;
    return false;
  }

  /** The library's files kept by the type letter, F, G, C and the tags. */
  public static List<PromptVault.StoredFile> keep(List<PromptVault.StoredFile> all, PromptVault vault, String letter) {
    List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
    if (all == null) return out;
    String type = isToggle(letter) ? null : letter;
    Prompts prompts = catOn() || tagsOn() ? new Prompts(vault) : null;
    for (PromptVault.StoredFile f : all) {
      // An old F or G as the letter still means its marks.
      if (isToggle(letter) && !DbFilter.fits(f, letter)) continue;
      if (!DbFilter.fits(f.name, type) || !marks(f) || !SogniShown.fileShown(f)) continue;
      if (prompts != null && !fitsPrompt(f, prompts)) continue;
      out.add(f);
    }
    return out;
  }

  /** One library file: as keep. */
  public static boolean fits(PromptVault.StoredFile f, PromptVault vault, String letter) {
    List<PromptVault.StoredFile> one = new ArrayList<PromptVault.StoredFile>();
    one.add(f);
    return !keep(one, vault, letter).isEmpty();
  }

  private static boolean marks(PromptVault.StoredFile f) {
    if (fav && !FileFavourites.is(f)) return false;
    if (groupOn() && !FileGroups.fits(f)) return false;
    return true;
  }

  private static boolean fitsPrompt(PromptVault.StoredFile f, Prompts prompts) {
    String key = MediaTags.key(f.name, f.size);
    PromptVault.Prompt p = prompts.of(f);
    if (catOn()) {
      String own = MediaTags.category(key);
      String path = own.length() > 0 ? own : p == null ? "" : prompts.path(p.categoryId);
      if (!under(path, category)) return false;
    }
    if (tagsOn()) {
      List<String> has = new ArrayList<String>(MediaTags.tags(key));
      if (p != null && p.tags != null) for (String t : p.tags.split(";")) if (t.trim().length() > 0) has.add(t.trim());
      if (!anyTag(has)) return false;
    }
    return true;
  }

  /** A library file's prompt (and category paths), looked up once for a whole list. */
  private static final class Prompts {
    final PromptVault vault;
    final Map<Long, PromptVault.Prompt> byId = new HashMap<Long, PromptVault.Prompt>();
    final Map<Long, String> paths = new HashMap<Long, String>();

    Prompts(PromptVault vault) {
      this.vault = vault;
      if (vault != null) for (PromptVault.Prompt p : vault.allPrompts()) byId.put(p.id, p);
    }

    PromptVault.Prompt of(PromptVault.StoredFile f) {
      if (vault == null) return null;
      PromptVault.Version v = vault.version(f.versionId);
      return v == null ? null : byId.get(v.promptId);
    }

    String path(long categoryId) {
      String p = paths.get(categoryId);
      if (p == null) {
        p = vault.pathName(categoryId);
        paths.put(categoryId, p);
      }
      return p;
    }
  }

  /** The Media browser's entries kept by F, G, C and the tags (folders stay, to go on looking; the library's prompt files go by T). */
  public static List<MediaDir.Entry> entries(List<MediaDir.Entry> all) {
    if (all == null || !active()) return all;
    List<MediaDir.Entry> out = new ArrayList<MediaDir.Entry>();
    for (MediaDir.Entry e : all) if (e.folder || fits(e)) out.add(e);
    return out;
  }

  public static boolean fits(MediaDir.Entry e) {
    String key = MediaTags.key(e);
    String marked = MediaDir.groupKey(e);
    if (fav && !FileFavourites.isKey(marked)) return false;
    if (groupOn() && !FileGroups.fits(marked)) return false;
    if (catOn() && !under(MediaTags.category(key), category)) return false;
    if (tagsOn() && !anyTag(MediaTags.tags(key))) return false;
    return true;
  }

  /** The main category a type letter stands for: I Image, V Video, S Music (or Sound, Audio), T Writing (or Text); null for A and O. */
  public static PromptVault.Category main(PromptVault vault, String letter) {
    if (vault == null || letter == null) return null;
    String[] names = "I".equals(letter) ? new String[] {"Image", "Images", "Picture", "Pictures"}
        : "V".equals(letter) ? new String[] {"Video", "Videos"}
        : "S".equals(letter) ? new String[] {"Music", "Sound", "Sounds", "Audio"}
        : "T".equals(letter) ? new String[] {"Writing", "Text", "Texts"} : new String[0];
    for (String n : names) for (PromptVault.Category c : vault.mains()) if (c.name.equalsIgnoreCase(n)) return c;
    return null;
  }

  /** A level of the category list: the main categories (parent 0), or a category's subcategories. */
  public static List<PromptVault.Category> level(PromptVault vault, long parentId) {
    if (vault == null) return new ArrayList<PromptVault.Category>();
    return parentId == 0 ? vault.mains() : vault.children(parentId);
  }

  /** "All of Image": the level's own category picked. */
  public static String allOf(String path) {
    return "All of " + path;
  }
}
