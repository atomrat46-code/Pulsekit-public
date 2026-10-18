package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Favourite files of the prompt library (Add to favourite files on a Reference, Result or Prompt
 * file card), in the order they were added: each gallery lists its own in its ★ Favourite files
 * list, and their cards are starred. Kept in file-favourites.txt in the app's folder. Also the
 * galleries' category tabs: the main category of the prompt a file belongs to.
 */
public final class FileFavourites {
  private FileFavourites() {}

  static final String FILE = "file-favourites.txt";
  public static final String ADD = "Add to favourite files";
  public static final String REMOVE = "Remove from favourite files";

  private static File dir;
  private static final List<String> KEYS = new ArrayList<String>();

  public static synchronized void load(File folder) {
    if (folder != null && folder.equals(dir)) return;
    dir = folder;
    KEYS.clear();
    File f = folder == null ? null : new File(folder, FILE);
    if (f == null || !f.isFile()) return;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String t = line.trim();
        // A library file ("3:42"), or a Media browser file ("file:beach.png|1234").
        if ((t.matches("\\d+:\\d+") || t.startsWith("file:")) && !KEYS.contains(t)) KEYS.add(t);
      }
    } catch (Exception ignored) {
      // none kept
    }
  }

  /** Forgets what was read (the tests start again with another folder). */
  public static synchronized void reset() {
    dir = null;
    KEYS.clear();
  }

  private static void save() {
    if (dir == null) return;
    StringBuilder sb = new StringBuilder();
    for (String k : KEYS) sb.append(k).append('\n');
    try {
      java.nio.file.Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }

  static String key(PromptVault.StoredFile f) {
    return f.which + ":" + f.versionId;
  }

  /** The key a file has as a file (its name and size): a run's file marked in Picture ready or Video ready is one by it. */
  static String fileKey(PromptVault.StoredFile f) {
    return MarkFile.mediaKey(f.name, f.size);
  }

  /** A favourite by its place in the library, or as the file it is (marked in Picture ready, Video ready or the Media browser). */
  public static synchronized boolean is(PromptVault.StoredFile f) {
    return f != null && (KEYS.contains(key(f)) || KEYS.contains(fileKey(f)));
  }

  /** Added, or taken off again (by either key); true when it is a favourite now. */
  public static synchronized boolean toggle(PromptVault.StoredFile f) {
    boolean now = !is(f);
    KEYS.remove(key(f));
    KEYS.remove(fileKey(f));
    if (now) KEYS.add(key(f));
    save();
    return now;
  }

  /** F by the A I V S T letters: the favourites only. */
  public static final String LETTER = "F";
  /** A Media browser file's menu item (Remove from favourites when it is one). */
  public static final String ADD_SHORT = "Add to favourites";
  public static final String REMOVE_SHORT = "Remove from favourites";

  /** A file outside the library (the Media browser's, by its MediaDir.groupKey) among the favourites. */
  public static synchronized boolean isKey(String key) {
    return key != null && KEYS.contains(key);
  }

  public static synchronized boolean toggleKey(String key) {
    boolean now = !KEYS.remove(key);
    if (now) KEYS.add(key);
    save();
    return now;
  }

  /** The favourites among `files` (one gallery's), in the order they were added. */
  public static synchronized List<PromptVault.StoredFile> among(List<PromptVault.StoredFile> files) {
    List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
    for (String k : KEYS) for (PromptVault.StoredFile f : files) if ((key(f).equals(k) || fileKey(f).equals(k)) && !out.contains(f)) out.add(f);
    return out;
  }

  /** A card's name, starred when the file is a favourite. */
  public static String shown(PromptVault.StoredFile f, String name) {
    return is(f) ? "★ " + name : name;
  }

  // ------------------------------------------------------------ category tabs

  /** "All" first (0), "No category" (-1) when some files have none. */
  public static final long ALL = 0;
  public static final long NONE = -1;

  /** The main category of the prompt the file belongs to (a reference or result file's version, a prompt file's prompt); NONE when it has none. */
  public static long categoryOf(PromptVault vault, PromptVault.StoredFile f) {
    if (vault == null || f == null) return NONE;
    PromptVault.Version v = vault.version(f.versionId);
    if (v == null) v = vault.versionOf(f);
    PromptVault.Prompt p = v == null ? null : vault.prompt(v.promptId);
    long main = p == null ? 0 : vault.mainOf(p.categoryId);
    return main == 0 ? NONE : main;
  }

  /** The tabs these files call for: the main categories they are in (the library's order), then No category. */
  public static List<long[]> tabs(PromptVault vault, List<PromptVault.StoredFile> files) {
    java.util.Set<Long> used = new java.util.HashSet<Long>();
    for (PromptVault.StoredFile f : files) used.add(categoryOf(vault, f));
    List<long[]> out = new ArrayList<long[]>();
    out.add(new long[] {ALL});
    if (vault != null) for (PromptVault.Category c : vault.mains()) if (used.contains(c.id)) out.add(new long[] {c.id});
    if (used.contains(NONE)) out.add(new long[] {NONE});
    return out;
  }

  public static String tabName(PromptVault vault, long tab) {
    if (tab == ALL) return "All";
    if (tab == NONE) return "No category";
    PromptVault.Category c = vault == null ? null : vault.category(tab);
    return c == null ? "?" : c.name;
  }

  /** The files on a tab (ALL: every one). */
  public static List<PromptVault.StoredFile> onTab(PromptVault vault, List<PromptVault.StoredFile> files, long tab) {
    if (tab == ALL) return files;
    List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
    for (PromptVault.StoredFile f : files) if (categoryOf(vault, f) == tab) out.add(f);
    return out;
  }
}
