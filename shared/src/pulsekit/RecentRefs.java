package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The reference pictures used last in the Params (a picture row's Choose file, Browse DB, Use as a
 * reference): Recent beside the row lists them as small thumbnails, a tap puts one in the row. Each
 * is kept as a copy in recent-refs/ beside the Params (the temporary copy a run is given is cleared
 * later), the newest first, at most MAX.
 */
public final class RecentRefs {
  private RecentRefs() {}

  public static final String RECENT = "Recent";
  public static final int MAX = 16;
  static final String DIR = "recent-refs", ORDER = "order.txt";

  /** The kept pictures, the newest first (those still there). */
  public static synchronized List<File> list(File base) {
    List<File> out = new ArrayList<File>();
    File dir = new File(base, DIR);
    File order = new File(dir, ORDER);
    if (!order.isFile()) return out;
    try {
      for (String line : new String(Files.readAllBytes(order.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String n = line.trim();
        if (n.length() == 0) continue;
        File f = new File(dir, n);
        if (f.isFile()) out.add(f);
      }
    } catch (Exception ignored) {
      // none kept
    }
    return out;
  }

  /** A picture used: kept (a copy) as the newest; the same picture (name and size) once. Its copy, or null. */
  public static synchronized File add(File base, File picture) {
    if (base == null || picture == null || !picture.isFile() || MediaDir.kind(picture.getName()) != MediaDir.PICTURE) return null;
    File dir = new File(base, DIR);
    if (!dir.isDirectory() && !dir.mkdirs()) return null;
    List<File> now = list(base);
    File kept = null;
    for (File f : now) if (f.getName().equalsIgnoreCase(picture.getName()) && f.length() == picture.length()) kept = f;
    try {
      if (kept == null) {
        String stem = picture.getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        String ext = picture.getName().substring(stem.length());
        kept = new File(dir, picture.getName());
        for (int n = 1; kept.exists(); n++) kept = new File(dir, stem + " (" + n + ")" + ext);
        if (!picture.getAbsoluteFile().equals(kept.getAbsoluteFile())) Files.copy(picture.toPath(), kept.toPath());
      }
      now.remove(kept);
      now.add(0, kept);
      while (now.size() > MAX) {
        File old = now.remove(now.size() - 1);
        old.delete();
      }
      StringBuilder sb = new StringBuilder();
      for (File f : now) sb.append(f.getName()).append('\n');
      SafeWrite.write(new File(dir, ORDER), sb.toString().getBytes(StandardCharsets.UTF_8));
      return kept;
    } catch (Exception ex) {
      return null;
    }
  }
}
