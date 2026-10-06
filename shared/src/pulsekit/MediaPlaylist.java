package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The Media browser's playlists. For now there is the default one: files added with "Add to
 * default playlist", kept in order in playlist-default.txt beside the prompt library (the app's
 * files folder on the phone, ~/.pulsekit on the desktop), one "address\tname" line each. The
 * address is a path, or on the phone a granted folder's document address. Playing it comes later.
 */
public final class MediaPlaylist {
  public static final String DEFAULT = "default";

  /** One file in a playlist. */
  public static final class Item {
    public String id;
    public String name;
  }

  private MediaPlaylist() {}

  static File file(File dir, String playlist) {
    return new File(dir, "playlist-" + playlist + ".txt");
  }

  /** The playlist's files, in the order added; empty when it has none. */
  public static List<Item> items(File dir, String playlist) {
    List<Item> out = new ArrayList<Item>();
    File f = file(dir, playlist);
    if (!f.isFile()) return out;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int tab = line.indexOf('\t');
        if (tab <= 0) continue;
        Item it = new Item();
        it.id = line.substring(0, tab);
        it.name = line.substring(tab + 1).trim();
        out.add(it);
      }
    } catch (Exception ignored) {
      // an unreadable playlist reads as empty
    }
    return out;
  }

  /** Adds the file at the end (once); returns a line for the status. */
  public static String add(File dir, String playlist, String id, String name) {
    if (id == null || id.trim().length() == 0) return "Nothing to add";
    String shown = name == null || name.trim().length() == 0 ? MediaDir.label(id) : name.trim().replace('\t', ' ').replace('\n', ' ');
    List<Item> now = items(dir, playlist);
    for (Item it : now) if (it.id.equals(id)) return shown + " is already in the " + playlist + " playlist";
    try {
      StringBuilder sb = new StringBuilder();
      for (Item it : now) sb.append(it.id).append('\t').append(it.name).append('\n');
      sb.append(id.replace('\t', ' ').replace('\n', ' ')).append('\t').append(shown).append('\n');
      if (!dir.isDirectory()) dir.mkdirs();
      Files.write(file(dir, playlist).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
      return shown + " added to the " + playlist + " playlist (" + (now.size() + 1) + (now.size() == 0 ? " file)" : " files)");
    } catch (Exception ex) {
      return "Could not add " + shown + " to the playlist" + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
  }
}
