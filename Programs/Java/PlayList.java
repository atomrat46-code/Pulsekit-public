/**
 * PlayList: plays a playlist (.pls) in Pulsekit's playlist viewer / editor. Given two or three
 * playlists it merges them into one (in order; a file in more than one comes once).
 *
 *   java PlayList [list.pls] [list2.pls] [list3.pls] [--name <name>]
 *
 * list.pls   The playlist: File1=, Title1=, Length1= lines as any .pls has them. None: the
 *            Default playlist (Add to default playlist on the thumbnails fills it).
 * list2.pls  A second (and list3.pls a third) playlist: merged into the first, in order.
 * --name     The name the playlist (or the merged one) is kept by; else the file's name, or
 *            "a + b" for a merge.
 *
 * The playlist is written into the run's folder as <name>.pls; Pulsekit keeps it with its
 * playlists and opens it in the Playlist window (Editor and Viewer tabs).
 *
 * Plain Java 8, so PyJav can compile it on a phone.
 */
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class PlayList {
  private PlayList() {}

  public static void main(String[] args) {
    int code = run(args);
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  static void usage() {
    System.out.println("Usage: java PlayList [list.pls] [list2.pls] [list3.pls] [--name <name>]");
  }

  /** One file of a list: its address and title. */
  static final class Item {
    String file = "";
    String title = "";
    long length = -1;
  }

  static int run(String[] args) {
    List<String> lists = new ArrayList<String>();
    String name = null;
    for (int i = 0; i < args.length; i++) {
      String a = args[i] == null ? "" : args[i].trim();
      if (a.length() == 0) continue;
      if (a.equals("-h") || a.equals("--help")) {
        usage();
        return 0;
      }
      if (a.equals("--name") && i + 1 < args.length) {
        name = args[++i].trim();
        continue;
      }
      if (a.startsWith("--")) continue;
      // PyJav's placeholders ("<input>") and anything that is not a playlist are left out.
      if (a.startsWith("<") || !a.toLowerCase().endsWith(".pls")) continue;
      lists.add(a);
    }
    if (lists.isEmpty()) {
      System.out.println("Succeeded: the Default playlist");
      System.out.println("Playlist: Default");
      return 0;
    }
    List<Item> all = new ArrayList<Item>();
    Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
    List<String> names = new ArrayList<String>();
    for (String path : lists) {
      File f = new File(path);
      if (!f.isFile()) {
        System.out.println("Failed: no such playlist " + path);
        return 2;
      }
      List<Item> items;
      try {
        items = parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
      } catch (Exception ex) {
        System.out.println("Failed: could not read " + f.getName() + ": " + ex.getMessage());
        return 2;
      }
      String base = f.getName().substring(0, f.getName().length() - 4);
      names.add(base);
      int added = 0;
      for (Item it : items) {
        if (seen.containsKey(it.file)) continue;
        seen.put(it.file, Boolean.TRUE);
        all.add(it);
        added++;
      }
      System.out.println(f.getName() + ": " + items.size() + " file" + (items.size() == 1 ? "" : "s") + (added < items.size() ? " (" + (items.size() - added) + " in an earlier list already)" : ""));
    }
    if (name == null || name.length() == 0) name = String.join(" + ", names);
    name = name.replaceAll("[\\t\\r\\n/\\\\:*?\"<>|]+", " ").trim();
    if (name.toLowerCase().endsWith(".pls")) name = name.substring(0, name.length() - 4).trim();
    if (name.length() == 0) name = "Playlist";
    for (int i = 0; i < all.size(); i++) System.out.println("  " + (i + 1) + ". " + all.get(i).title);
    File out = new File(workDir(), name + ".pls");
    try {
      Files.write(out.toPath(), pls(all).getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      System.out.println("Failed: could not write " + out.getName() + ": " + ex.getMessage());
      return 2;
    }
    System.out.println("Succeeded: " + (lists.size() > 1 ? lists.size() + " playlists merged into " : "") + name + ", " + all.size() + " file" + (all.size() == 1 ? "" : "s"));
    System.out.println("Playlist: " + name);
    return 0;
  }

  /** The run's folder in the app (pulsekit.work), else user.dir. */
  static String workDir() {
    String w = System.getProperty("pulsekit.work");
    return w != null && w.length() > 0 ? w : System.getProperty("user.dir", ".");
  }

  static List<Item> parse(String text) {
    Map<Integer, Item> byNumber = new TreeMap<Integer, Item>();
    for (String raw : text.replace("\r", "").split("\n")) {
      String line = raw.trim();
      int eq = line.indexOf('=');
      if (eq <= 0) continue;
      String key = line.substring(0, eq).trim().toLowerCase();
      String value = line.substring(eq + 1).trim();
      String field = key.replaceAll("\\d+$", "");
      String digits = key.substring(field.length());
      if (digits.length() == 0) continue;
      int n;
      try {
        n = Integer.parseInt(digits);
      } catch (NumberFormatException ex) {
        continue;
      }
      Item it = byNumber.get(n);
      if (it == null) {
        it = new Item();
        byNumber.put(n, it);
      }
      if (field.equals("file")) it.file = value;
      else if (field.equals("title")) it.title = value;
      else if (field.equals("length")) {
        try {
          it.length = Long.parseLong(value);
        } catch (NumberFormatException ex) {
          it.length = -1;
        }
      }
    }
    List<Item> out = new ArrayList<Item>();
    for (Item it : byNumber.values()) {
      if (it.file.length() == 0) continue;
      if (it.title.length() == 0) it.title = it.file.substring(Math.max(it.file.lastIndexOf('/'), it.file.lastIndexOf('\\')) + 1);
      out.add(it);
    }
    return out;
  }

  static String pls(List<Item> items) {
    StringBuilder sb = new StringBuilder("[playlist]\n");
    for (int i = 0; i < items.size(); i++) {
      Item it = items.get(i);
      sb.append("File").append(i + 1).append('=').append(it.file).append('\n');
      sb.append("Title").append(i + 1).append('=').append(it.title).append('\n');
      sb.append("Length").append(i + 1).append('=').append(it.length).append('\n');
    }
    sb.append("NumberOfEntries=").append(items.size()).append('\n');
    sb.append("Version=2\n");
    return sb.toString();
  }
}
