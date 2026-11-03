package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Downloads > Finished: Move downloaded media files to. Images, videos and music each have a box
 * and a folder; Move now moves the finished downloads of the ticked kinds out of Download into
 * their folders; Move automatically does so as each run finishes. Kept in download-move.txt beside
 * the Downloads list.
 */
public final class DownloadMove {
  private DownloadMove() {}

  public static final String TITLE = "Move downloaded media files to";
  public static final String MOVE_NOW = "Move now";
  public static final String AUTO = "Move automatically";
  /** The kinds (SogniDownloads.kindOf) and their names. */
  public static final String[] KINDS = {"picture", "video", "audio"};
  public static final String[] NAMES = {"Images", "Videos", "Music"};
  static final String FILE = "download-move.txt";

  public static boolean on;
  public static boolean auto;
  /** Each kind's box, folder (a path, or a granted folder's address) and the folder's name as shown. */
  public static final Map<String, Boolean> ticked = new LinkedHashMap<String, Boolean>();
  public static final Map<String, String> folder = new LinkedHashMap<String, String>();
  public static final Map<String, String> label = new LinkedHashMap<String, String>();
  private static File loadedFrom;

  /** The settings kept beside the Downloads list (read again only for another folder). */
  public static synchronized void load() {
    File dir = SogniDownloads.dir();
    if (dir != null && dir.equals(loadedFrom)) return;
    loadedFrom = dir;
    on = false;
    auto = false;
    ticked.clear();
    folder.clear();
    label.clear();
    if (dir == null) return;
    File f = new File(dir, FILE);
    if (!f.isFile()) return;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.split("\t", -1);
        if (p[0].equals("on")) on = p.length > 1 && "1".equals(p[1]);
        else if (p[0].equals("auto")) auto = p.length > 1 && "1".equals(p[1]);
        else if (p.length >= 4) {
          ticked.put(p[0], "1".equals(p[1]));
          folder.put(p[0], p[2]);
          label.put(p[0], p[3]);
        }
      }
    } catch (Exception ignored) {
      // the defaults
    }
  }

  public static synchronized void save() {
    File dir = SogniDownloads.dir();
    if (dir == null) return;
    StringBuilder sb = new StringBuilder();
    sb.append("on\t").append(on ? "1" : "0").append('\n').append("auto\t").append(auto ? "1" : "0").append('\n');
    for (String k : KINDS) {
      sb.append(k).append('\t').append(isTicked(k) ? "1" : "0").append('\t').append(clean(folder.get(k))).append('\t').append(clean(label.get(k))).append('\n');
    }
    try {
      SafeWrite.write(new File(dir, FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept while Pulsekit runs
    }
  }

  public static synchronized boolean isTicked(String kind) {
    Boolean b = ticked.get(kind);
    return b != null && b.booleanValue();
  }

  /** The kind's folder, or "" when none is picked. */
  public static synchronized String folderOf(String kind) {
    String f = folder.get(kind);
    return f == null ? "" : f;
  }

  /** The kind's folder as shown: its name, or "No folder". */
  public static synchronized String labelOf(String kind) {
    String l = label.get(kind);
    return l == null || l.length() == 0 ? "No folder" : l;
  }

  public static synchronized void setFolder(String kind, String where, String shown) {
    folder.put(kind, where == null ? "" : where);
    label.put(kind, shown == null ? "" : shown);
    save();
  }

  /** The finished downloads to move now: of a ticked kind with a folder, still in Download. {run, item} pairs. */
  public static synchronized List<Object[]> due() {
    List<Object[]> out = new ArrayList<Object[]>();
    for (SogniDownloads.Run r : SogniDownloads.tab(SogniDownloads.FINISHED)) {
      for (SogniDownloads.Item item : r.items) {
        String k = item.kind();
        if (item.away || !isTicked(k) || folderOf(k).length() == 0 || item.where == null || item.where.length() == 0) continue;
        out.add(new Object[] {r, item});
      }
    }
    return out;
  }

  /** Whether runs that finish move their files now (the box ticked, Move automatically ticked). */
  public static synchronized boolean automatic() {
    load();
    return on && auto;
  }

  /** What Move now said: "Moved 3 files (2 images, 1 video)", or why nothing moved. */
  public static String said(int moved, int failed, String firstProblem) {
    if (moved == 0 && failed == 0) return "Nothing to move: no finished downloads of the ticked kinds are left in Download (each kind needs a folder)";
    return "Moved " + moved + (moved == 1 ? " file" : " files") + (failed > 0 ? "; " + failed + " could not be moved" + (firstProblem != null ? " (" + firstProblem + ")" : "") : "");
  }

  /** "12.3 MB" (Move now's progress). */
  public static String mb(long bytes) {
    return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0);
  }

  private static String clean(String s) {
    return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
  }
}
