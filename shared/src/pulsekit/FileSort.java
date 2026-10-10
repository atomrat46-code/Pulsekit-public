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
  public static final String[] CHOICES = {"by type", "by date", "by size"};

  /** The choice shown ("by date" at first, the order the galleries had before). */
  public static volatile String current = "by date";

  private FileSort() {}

  /** `choice` when it is one of CHOICES, else "by date". */
  public static String valid(String choice) {
    for (String c : CHOICES) if (c.equals(choice == null ? null : choice.trim())) return c;
    return "by date";
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
