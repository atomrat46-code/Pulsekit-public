package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Browse prompts' favourite categories: a row of them above the main categories, one tap away
 * (a main category or a subcategory). Kept beside the prompt library (favourite-categories.txt),
 * as the categories' ids only, in the order they were added; a category deleted since is left out.
 */
public final class CategoryFavourites {
  private static final List<Long> IDS = new ArrayList<Long>();
  private static File file;

  private CategoryFavourites() {}

  /** The list kept in `dir` (read once a folder). */
  public static synchronized void load(File dir) {
    File at = dir == null ? null : new File(dir, "favourite-categories.txt");
    if (at != null && at.equals(file)) return;
    file = at;
    IDS.clear();
    if (file == null || !file.isFile()) return;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String t = line.trim();
        if (t.length() == 0) continue;
        try {
          Long id = Long.valueOf(t);
          if (!IDS.contains(id)) IDS.add(id);
        } catch (NumberFormatException ignored) {
          // not an id
        }
      }
    } catch (Exception ignored) {
      // an unreadable list starts empty
    }
  }

  public static synchronized boolean is(long id) {
    return IDS.contains(Long.valueOf(id));
  }

  /** Adds or removes the category; true when it is a favourite now. */
  public static synchronized boolean toggle(long id) {
    Long key = Long.valueOf(id);
    boolean now = !IDS.remove(key);
    if (now) IDS.add(key);
    write();
    return now;
  }

  /** The favourites that are still in the library, in the order they were added. */
  public static synchronized List<PromptVault.Category> list(PromptVault vault) {
    List<PromptVault.Category> out = new ArrayList<PromptVault.Category>();
    for (Long id : IDS) {
      PromptVault.Category c = vault.category(id.longValue());
      if (c != null) out.add(c);
    }
    return out;
  }

  /** "Rock / Ballads" for a subcategory, its name for a main category. */
  public static String label(PromptVault vault, PromptVault.Category c) {
    long main = vault.mainOf(c.id);
    if (main == c.id) return c.name;
    PromptVault.Category m = vault.category(main);
    return (m == null ? "" : m.name + " / ") + c.name;
  }

  private static void write() {
    if (file == null) return;
    StringBuilder sb = new StringBuilder();
    for (Long id : IDS) sb.append(id).append('\n');
    try {
      java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }
}
