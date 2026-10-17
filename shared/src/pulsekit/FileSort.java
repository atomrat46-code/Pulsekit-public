package pulsekit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The Prompts page's Ref files and Result files galleries: Sort by type (pictures, videos, sound
 * files, text files, then the rest, each by name), by date (the newest kept first: files are
 * numbered in the order they come into the library) or by size (the largest first). The choice is
 * kept: on the phone in the app's preferences, on the desktop in ~/.pulsekit/file-sort.txt.
 */
public final class FileSort {
  public static final String[] CHOICES = {"by type", "by date", "by size", "by name, version", "by category"};

  /** The choice shown ("by date" at first, the order the galleries had before). */
  public static volatile String current = "by date";

  private FileSort() {}

  /** `choice` when it is one of CHOICES, else "by date". */
  public static String valid(String choice) {
    for (String c : CHOICES) if (c.equals(choice == null ? null : choice.trim())) return c;
    return "by date";
  }

  /** The files in `choice`'s order (a new list); by category needs the library (`vault`). */
  public static List<PromptVault.StoredFile> sorted(List<PromptVault.StoredFile> files, String choice, final PromptVault vault) {
    String c = valid(choice);
    if (!c.equals("by category") && !c.equals("by name, version")) return sorted(files, c);
    List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>(files == null ? new ArrayList<PromptVault.StoredFile>() : files);
    final boolean byCat = c.equals("by category");
    Collections.sort(out, (a, b) -> {
      if (byCat) {
        String ca = category(vault, a), cb = category(vault, b);
        // A file of no prompt (imported on its own) after those that have one.
        if (ca.length() == 0 != (cb.length() == 0)) return ca.length() == 0 ? 1 : -1;
        int k = ca.compareToIgnoreCase(cb);
        if (k != 0) return k;
      }
      int n = name(a).compareToIgnoreCase(name(b));
      // The same name: the older version first (v1, v2...).
      return n != 0 ? n : Long.compare(a.versionId, b.versionId);
    });
    return out;
  }

  /** "Image › Portraits": the category of the prompt the file belongs to; "" for a file kept on its own. */
  public static String category(PromptVault vault, PromptVault.StoredFile f) {
    if (vault == null || f == null) return "";
    PromptVault.Version v = vault.version(f.versionId);
    PromptVault.Prompt p = v == null ? null : vault.prompt(v.promptId);
    PromptVault.Category c = p == null ? null : vault.category(p.categoryId);
    if (c == null) return "";
    PromptVault.Category main = vault.category(vault.mainOf(c.id));
    return main == null || main.id == c.id ? one(c.name) : one(main.name) + " \u203a " + one(c.name);
  }

  /** A card's line under the name: its category (when it has one), its type, and with by type or by date its date. */
  public static String info(PromptVault vault, PromptVault.StoredFile f, String choice) {
    StringBuilder sb = new StringBuilder();
    String cat = category(vault, f);
    if (cat.length() > 0) sb.append(cat).append(" \u00b7 ");
    String e = ext(f.name);
    sb.append(e.length() == 0 ? "file" : e.toUpperCase(java.util.Locale.ROOT));
    PromptVault.Version v = vault == null ? null : vault.version(f.versionId);
    String c = valid(choice);
    if (v != null && v.created > 0 && (c.equals("by type") || c.equals("by date"))) {
      sb.append(" \u00b7 ").append(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date(v.created)));
    }
    return sb.toString();
  }

  private static String one(String s) {
    return s == null ? "" : s.trim();
  }

  /** The files in `choice`'s order (a new list). */
  public static List<PromptVault.StoredFile> sorted(List<PromptVault.StoredFile> files, String choice) {
    List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>(files == null ? new ArrayList<PromptVault.StoredFile>() : files);
    String c = valid(choice);
    Comparator<PromptVault.StoredFile> byName = (a, b) -> name(a).compareToIgnoreCase(name(b));
    if (c.equals("by type")) {
      Collections.sort(out, (a, b) -> {
        int k = rank(a.name) - rank(b.name);
        if (k != 0) return k;
        int e = ext(a.name).compareTo(ext(b.name));
        return e != 0 ? e : byName.compare(a, b);
      });
    } else if (c.equals("by size")) {
      Collections.sort(out, (a, b) -> {
        int s = Integer.compare(b.size, a.size);
        return s != 0 ? s : byName.compare(a, b);
      });
    } else {
      // The library numbers a version and a file kept on its own from one counter: a higher number is newer.
      Collections.sort(out, (a, b) -> Long.compare(b.versionId, a.versionId));
    }
    return out;
  }

  /** Pictures 0, videos 1, sound files (MIDI too) 2, text files 3, anything else 4. */
  static int rank(String name) {
    int kind = MediaDir.kind(name);
    if (kind == MediaDir.PICTURE) return 0;
    if (kind == MediaDir.VIDEO) return 1;
    if (kind == MediaDir.SOUND) return 2;
    if (DbFilter.isText(name)) return 3;
    return 4;
  }

  private static String ext(String name) {
    String low = name == null ? "" : name.toLowerCase();
    int dot = low.lastIndexOf('.');
    return dot >= 0 ? low.substring(dot + 1) : "";
  }

  private static String name(PromptVault.StoredFile f) {
    return f == null || f.name == null ? "" : f.name;
  }
}
