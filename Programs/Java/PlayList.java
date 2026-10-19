/**
 * PlayList: plays a playlist (.pls) in Pulsekit's playlist viewer / editor. Given two or three
 * playlists it merges them into one (in order; a file in more than one comes once).
 *
 *   java PlayList [list.pls] [list2.pls] [list3.pls] [--name <name>] [--nogui] [--outfile <file>] [--batchdir <folder>] [--midi] [--midibatch]
 *
 * list.pls   The playlist: File1=, Title1=, Length1= lines as any .pls has them. None: the
 *            Default playlist (Add to default playlist on the thumbnails fills it).
 * list2.pls  A second (and list3.pls a third) playlist: merged into the first, in order.
 * --name     The name the playlist (or the merged one) is kept by; else the file's name, or
 *            "a + b" for a merge.
 *
 * --nogui    text mode: no Playlist window; the playlist (the lists merged, or the Default
 *            playlist when none is given) is written into --outfile only.
 * --outfile  the file --nogui writes (default out.pls; out.txt with --midibatch).
 * --batchdir every file in the folder listed into --outfile (--nogui goes with it).
 * --midi     only the MIDI files (*.mid, *.midi), of --batchdir or the lists. The list can be given
 *            to Midi2Wav --dir (SoundFont), vst_render.py --dir (VST, or Carla / DrumGizmo on Linux).
 * --midibatch --outfile written as a MidiBatch list (one file a line) for MidiBatch --list;
 *            --midi goes with it.
 *
 * Without --nogui the playlist is written into the run's folder as <name>.pls; Pulsekit keeps it
 * with its playlists and opens it in the Playlist window (Editor and Viewer tabs). Pulsekit gives
 * --default_pls <file> (the Default playlist's file) for --nogui without a list.
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
    System.out.println("Usage: java PlayList [list.pls] [list2.pls] [list3.pls] [--name <name>] [--nogui] [--outfile <file>] [--batchdir <folder>] [--midi] [--midibatch]");
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
    boolean nogui = false;
    boolean midi = false;
    boolean midibatch = false;
    String outfile = null;
    String batchdir = null;
    String defaultPls = null;
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
      if (a.equals("--nogui")) nogui = true;
      else if (a.equals("--midi")) midi = true;
      else if (a.equals("--midibatch")) midibatch = true;
      else if (a.equals("--outfile") && i + 1 < args.length) outfile = args[++i].trim();
      else if (a.equals("--batchdir") && i + 1 < args.length) batchdir = args[++i].trim();
      else if (a.equals("--default_pls") && i + 1 < args.length) defaultPls = args[++i].trim();
      if (a.startsWith("--")) continue;
      // PyJav's placeholders ("<input>") and anything that is not a playlist are left out.
      if (a.startsWith("<") || !a.toLowerCase().endsWith(".pls")) continue;
      lists.add(a);
    }
    if (batchdir != null && (batchdir.length() == 0 || batchdir.startsWith("<"))) batchdir = null;
    if (outfile != null && (outfile.length() == 0 || outfile.startsWith("<"))) outfile = null;
    if (midibatch) midi = true;
    if (batchdir != null) nogui = true;
    if (nogui) return text(lists, batchdir, outfile, defaultPls, midi, midibatch);
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

  /**
   * --nogui: the lists merged (or --batchdir's files, or the Default playlist) written into the
   * outfile, as a playlist or (--midibatch) a MidiBatch list; no "Playlist:" line, so Pulsekit opens
   * no window.
   */
  static int text(List<String> lists, String batchdir, String outfile, String defaultPls, boolean midi, boolean midibatch) {
    List<Item> all = new ArrayList<Item>();
    Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
    List<String> from = new ArrayList<String>(lists);
    if (batchdir == null && from.isEmpty()) {
      if (defaultPls == null || defaultPls.length() == 0) {
        System.out.println("Failed: no playlist given (and no Default playlist: run it in Pulsekit)");
        return 2;
      }
      from.add(defaultPls);
    }
    if (batchdir != null) {
      File d = new File(batchdir);
      if (!d.isDirectory()) {
        System.out.println("Failed: no folder " + batchdir);
        return 2;
      }
      File[] files = d.listFiles();
      List<File> sorted = new ArrayList<File>();
      if (files != null) for (File f : files) if (f.isFile() && !f.getName().startsWith(".")) sorted.add(f);
      java.util.Collections.sort(sorted, new java.util.Comparator<File>() {
        public int compare(File x, File y) {
          return x.getName().compareToIgnoreCase(y.getName());
        }
      });
      int added = 0;
      for (File f : sorted) {
        if (midi && !isMidi(f.getName())) continue;
        Item it = new Item();
        it.file = f.getAbsolutePath();
        it.title = f.getName();
        if (seen.containsKey(it.file)) continue;
        seen.put(it.file, Boolean.TRUE);
        all.add(it);
        added++;
      }
      System.out.println(d.getName() + ": " + added + (midi ? " MIDI" : "") + " file" + (added == 1 ? "" : "s"));
    }
    for (String path : from) {
      File f = new File(path);
      if (path.equals(defaultPls) && !f.isFile()) {
        System.out.println("Default playlist: 0 files (Add to default playlist on the thumbnails fills it)");
        continue;
      }
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
      int added = 0;
      for (Item it : items) {
        // A name without a folder is beside the list.
        File beside = new File(f.getAbsoluteFile().getParentFile(), it.file);
        if (!new File(it.file).isAbsolute() && it.file.indexOf(':') < 0 && beside.isFile()) it.file = beside.getPath();
        if (midi && !isMidi(it.file)) continue;
        if (seen.containsKey(it.file)) continue;
        seen.put(it.file, Boolean.TRUE);
        all.add(it);
        added++;
      }
      String label = path.equals(defaultPls) ? "Default playlist" : f.getName();
      System.out.println(label + ": " + added + (midi ? " MIDI" : "") + " file" + (added == 1 ? "" : "s"));
    }
    String name = outfile != null ? outfile : midibatch ? "out.txt" : "out.pls";
    File out = new File(name);
    if (!out.isAbsolute()) out = new File(workDir(), name);
    StringBuilder text = new StringBuilder();
    if (midibatch) {
      text.append("# PlayList: the MIDI files (give to MidiBatch --list)\n");
      for (Item it : all) text.append(it.file).append('\n');
    }
    try {
      if (out.getParentFile() != null) out.getParentFile().mkdirs();
      Files.write(out.toPath(), (midibatch ? text.toString() : pls(all)).getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      System.out.println("Failed: could not write " + out.getName() + ": " + ex.getMessage());
      return 2;
    }
    for (int i = 0; i < all.size(); i++) System.out.println("  " + (i + 1) + ". " + all.get(i).title);
    System.out.println("Succeeded: " + all.size() + (midi ? " MIDI" : "") + " file" + (all.size() == 1 ? "" : "s") + " into " + out.getName());
    if (midibatch) System.out.println("Next: MidiBatch --list \"" + out.getPath() + "\"");
    else if (midi) System.out.println("Next: Midi2Wav --dir \"" + out.getPath() + "\" (SoundFont) or vst_render.py --dir \"" + out.getPath() + "\" (VST, Carla, DrumGizmo)");
    return 0;
  }

  static boolean isMidi(String name) {
    String n = name.toLowerCase();
    return n.endsWith(".mid") || n.endsWith(".midi");
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
