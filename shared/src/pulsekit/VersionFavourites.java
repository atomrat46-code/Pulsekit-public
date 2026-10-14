package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Favourite prompts: prompt library versions kept one tap away (Browse prompts, the Prompts page),
 * added with Save prompt's "Add to favourites" or the star of the version shown. Kept beside the
 * prompt library (favourite-versions.txt), as the versions' ids only, in the order they were
 * added; a version deleted since is left out.
 */
public final class VersionFavourites {
  private static final List<Long> IDS = new ArrayList<Long>();
  private static File file;

  private VersionFavourites() {}

  /** The list kept in `dir` (read once a folder). */
  public static synchronized void load(File dir) {
    File at = dir == null ? null : new File(dir, "favourite-versions.txt");
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

  /** Adds the version (once). */
  public static synchronized void add(long id) {
    if (id == 0 || IDS.contains(Long.valueOf(id))) return;
    IDS.add(Long.valueOf(id));
    write();
  }

  /** Adds or removes the version; true when it is a favourite now. */
  public static synchronized boolean toggle(long id) {
    Long key = Long.valueOf(id);
    boolean now = !IDS.remove(key);
    if (now) IDS.add(key);
    write();
    return now;
  }

  /** The favourites that are still in the library, in the order they were added. */
  public static synchronized List<PromptVault.Version> list(PromptVault vault) {
    List<PromptVault.Version> out = new ArrayList<PromptVault.Version>();
    for (Long id : IDS) {
      PromptVault.Version v = vault.version(id.longValue());
      if (v != null && vault.prompt(v.promptId) != null) out.add(v);
    }
    return out;
  }

  /** "Rock ballad · v3": the prompt's title and the version's number. */
  public static String label(PromptVault vault, PromptVault.Version v) {
    PromptVault.Prompt p = vault.prompt(v.promptId);
    List<PromptVault.Version> rows = vault.versions(v.promptId);
    int n = rows.size() - rows.indexOf(v);
    return (p == null || p.title == null ? "Prompt" : p.title) + " · v" + n;
  }

  /** The version's prompt text (without its description), as Browse prompts hands it on. */
  public static String words(PromptVault.Version v) {
    return v == null ? "" : PromptRun.withoutDescription(v.description, v.body == null ? "" : v.body).trim();
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
