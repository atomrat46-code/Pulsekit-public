/**
 * MediaBrowser: the pictures, videos and sounds in a directory, as preview thumbnails in
 * Pulsekit's Media browser screen. A picture opens full size, a video or a sound (MIDI too) plays.
 *
 *   java MediaBrowser <directory>
 *
 * directory  The folder to browse. In Params, Browse picks it and opens the Media browser at once;
 *            a run lists what is in it here and opens the Media browser too. On the phone the
 *            folder is one the system picker granted (a content:// address), read by the app.
 *
 * Plain Java 8, so PyJav can compile it on a phone.
 */
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MediaBrowser {
  private MediaBrowser() {}

  public static void main(String[] args) {
    int code = run(args);
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  static void usage() {
    System.out.println("Usage: java MediaBrowser <directory>");
  }

  /** 2 a picture, 3 a video, 4 a sound (MIDI too), 0 anything else: as Pulsekit's Media browser sorts them. */
  static int kind(String name) {
    String low = name == null ? "" : name.toLowerCase();
    if (low.matches(".+\\.(png|jpe?g|webp|gif|bmp|heic)")) return 2;
    if (low.matches(".+\\.(mp4|m4v|webm|mkv|3gp|mov)")) return 3;
    if (low.matches(".+\\.(wav|wave|mp3|ogg|m4a|aac|flac|mid|midi)")) return 4;
    return 0;
  }

  static String count(int n, String what) {
    return n + " " + what + (n == 1 ? "" : "s");
  }

  static int run(String[] args) {
    // The first argument that is a folder (PyJav may add its input file after it).
    String dir = null;
    String first = null;
    for (String raw : args) {
      String a = raw == null ? "" : raw.trim();
      if (a.length() == 0) continue;
      if (a.equals("-h") || a.equals("--help")) {
        usage();
        return 0;
      }
      if (a.startsWith("--")) continue;
      if (first == null) first = a;
      if (dir == null && (a.startsWith("content://") || new File(a).isDirectory())) dir = a;
    }
    if (dir == null) dir = first;
    if (dir == null || dir.startsWith("<")) {
      System.out.println("Failed: give a directory (Params: Browse)");
      usage();
      return 2;
    }
    // A folder the phone's picker granted: the app lists it.
    if (dir.startsWith("content://")) {
      System.out.println("Succeeded: the app lists that folder");
      System.out.println("Media browser: " + dir);
      return 0;
    }
    File folder = new File(dir);
    if (!folder.isDirectory()) {
      System.out.println("Failed: " + dir + " is not a directory");
      return 2;
    }
    File[] all = folder.listFiles();
    List<File> files = all == null ? new ArrayList<File>() : new ArrayList<File>(Arrays.asList(all));
    Collections.sort(files, new Comparator<File>() {
      @Override
      public int compare(File x, File y) {
        return x.getName().compareToIgnoreCase(y.getName());
      }
    });
    int[] count = new int[5];
    int folders = 0;
    for (File f : files) {
      if (f.isHidden() || f.getName().startsWith(".")) continue;
      if (f.isDirectory()) {
        folders++;
        continue;
      }
      int k = kind(f.getName());
      count[k]++;
      if (k == 0) continue;
      String what = k == 2 ? "picture" : k == 3 ? "video" : "sound";
      System.out.println("  " + what + "  " + f.getName() + "  (" + Math.max(1, f.length() / 1024) + " KB)");
    }
    System.out.println("Succeeded: " + count(count[2], "picture") + ", " + count(count[3], "video") + ", " + count(count[4], "sound")
        + (folders > 0 ? ", " + count(folders, "folder") : "")
        + (count[0] > 0 ? " (" + count[0] + " other files not shown)" : ""));
    System.out.println("Media browser: " + folder.getAbsolutePath());
    return 0;
  }
}
