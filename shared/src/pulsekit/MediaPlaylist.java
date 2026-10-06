package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * The Media browser's playlists. Each folder it shows (the one picked in Params and every folder
 * under it) has a default playlist: the files added there with "Add to default playlist", in the
 * order added. Each is a file beside the prompt library (the app's files folder on the phone,
 * ~/.pulsekit on the desktop), playlist-&lt;folder hash&gt;.txt: a "folder\t..." line, then one
 * "address\tname\tsize" line per file. The address is a path, or on the phone the file's document
 * id in the folder the picker granted.
 */
public final class MediaPlaylist {
  /** One file in a playlist. */
  public static final class Item {
    public String id;
    public String name;
    public long size;
  }

  private MediaPlaylist() {}

  /** The default playlist's file for `folder` (a path, or a granted folder with its document id). */
  static File file(File dir, String folder) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-256").digest(folder.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i] & 0xff));
      return new File(dir, "playlist-" + sb + ".txt");
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** The folder's default playlist, in the order added; empty when it has none. */
  public static List<Item> items(File dir, String folder) {
    List<Item> out = new ArrayList<Item>();
    if (folder == null) return out;
    File f = file(dir, folder);
    if (!f.isFile()) return out;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String[] part = line.split("\t");
        if (part.length < 2 || part[0].equals("folder")) continue;
        Item it = new Item();
        it.id = part[0];
        it.name = part[1].trim();
        try {
          it.size = part.length > 2 ? Long.parseLong(part[2].trim()) : 0;
        } catch (NumberFormatException ex) {
          it.size = 0;
        }
        out.add(it);
      }
    } catch (Exception ignored) {
      // an unreadable playlist reads as empty
    }
    return out;
  }

  /** Adds the file at the end of the folder's default playlist (once); returns a line for the status. */
  public static String add(File dir, String folder, String id, String name, long size) {
    if (folder == null || id == null || id.trim().length() == 0) return "Nothing to add";
    String shown = clean(name == null || name.trim().length() == 0 ? MediaDir.label(id) : name.trim());
    List<Item> now = items(dir, folder);
    for (Item it : now) if (it.id.equals(id)) return shown + " is already in this folder's playlist";
    try {
      StringBuilder sb = new StringBuilder("folder\t").append(clean(folder)).append('\n');
      for (Item it : now) sb.append(it.id).append('\t').append(it.name).append('\t').append(it.size).append('\n');
      sb.append(clean(id)).append('\t').append(shown).append('\t').append(Math.max(0, size)).append('\n');
      if (!dir.isDirectory()) dir.mkdirs();
      Files.write(file(dir, folder).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
      int n = now.size() + 1;
      return shown + " added to this folder's playlist (" + n + (n == 1 ? " file)" : " files)");
    } catch (Exception ex) {
      return "Could not add " + shown + " to the playlist" + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
  }

  /** True when the file is in the folder's default playlist. */
  public static boolean has(File dir, String folder, String id) {
    for (Item it : items(dir, folder)) if (it.id.equals(id)) return true;
    return false;
  }

  /** Takes the file out of the folder's default playlist; returns a line for the status. */
  public static String remove(File dir, String folder, String id, String name) {
    String shown = name == null || name.trim().length() == 0 ? MediaDir.label(id) : name.trim();
    List<Item> now = items(dir, folder);
    StringBuilder sb = new StringBuilder("folder\t").append(clean(folder)).append('\n');
    int left = 0;
    boolean found = false;
    for (Item it : now) {
      if (it.id.equals(id)) {
        found = true;
        continue;
      }
      sb.append(it.id).append('\t').append(it.name).append('\t').append(it.size).append('\n');
      left++;
    }
    if (!found) return shown + " is not in this folder's playlist";
    try {
      File f = file(dir, folder);
      if (left == 0) f.delete();
      else Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
      return shown + " removed from this folder's playlist (" + left + (left == 1 ? " file left)" : " files left)");
    } catch (Exception ex) {
      return "Could not remove " + shown + " from the playlist" + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
  }

  /** "Playlist (3)". */
  public static String button(int count) {
    return "Playlist (" + count + ")";
  }

  private static String clean(String s) {
    return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
  }
}
