package pulsekit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The Media browser's rules, the same on the phone and the desktop: which files it shows (pictures,
 * videos and sounds, MIDI too), in which order (folders first, then by name), how a folder is
 * named, and where MediaBrowser's run says to open it.
 */
public final class MediaDir {
  public static final int PICTURE = 2;
  public static final int VIDEO = 3;
  public static final int SOUND = 4;

  /** A file or folder in the browser. */
  public static final class Entry {
    public String name;
    /** A file's path, or on the phone its document address. */
    public String id;
    public boolean folder;
    public int kind;
    public long size;
  }

  private MediaDir() {}

  /** PICTURE, VIDEO or SOUND by the name's ending; 0 for a file the browser leaves out. */
  public static int kind(String name) {
    String low = name == null ? "" : name.toLowerCase();
    if (low.matches(".+\\.(png|jpe?g|webp|gif|bmp|heic)")) return PICTURE;
    if (low.matches(".+\\.(mp4|m4v|webm|mkv|3gp|mov)")) return VIDEO;
    if (low.matches(".+\\.(wav|wave|mp3|ogg|m4a|aac|flac|mid|midi)")) return SOUND;
    return 0;
  }

  public static boolean isMidi(String name) {
    return name != null && name.toLowerCase().matches(".+\\.midi?");
  }

  /** The entries shown: folders and media files, no hidden ones; folders first, then by name. */
  public static List<Entry> shown(List<Entry> all) {
    List<Entry> out = new ArrayList<Entry>();
    if (all == null) return out;
    for (Entry e : all) {
      if (e == null || e.name == null || e.name.startsWith(".")) continue;
      if (!e.folder) e.kind = kind(e.name);
      if (e.folder || e.kind != 0) out.add(e);
    }
    Collections.sort(out, new Comparator<Entry>() {
      @Override
      public int compare(Entry a, Entry b) {
        if (a.folder != b.folder) return a.folder ? -1 : 1;
        return a.name.compareToIgnoreCase(b.name);
      }
    });
    return out;
  }

  /** "12 pictures, 3 videos, 4 sounds" (and folders) for the browser's heading. */
  public static String summary(List<Entry> shown) {
    int pictures = 0, videos = 0, sounds = 0, folders = 0;
    for (Entry e : shown) {
      if (e.folder) folders++;
      else if (e.kind == PICTURE) pictures++;
      else if (e.kind == VIDEO) videos++;
      else if (e.kind == SOUND) sounds++;
    }
    String s = count(pictures, "picture") + ", " + count(videos, "video") + ", " + count(sounds, "sound");
    return folders > 0 ? s + ", " + count(folders, "folder") : s;
  }

  private static String count(int n, String what) {
    return n + " " + what + (n == 1 ? "" : "s");
  }

  /**
   * A folder's name to show: a path's last part, or for a folder the phone's picker granted
   * (content://.../tree/primary%3ADCIM%2FCamera) the part after the volume ("DCIM/Camera").
   */
  public static String label(String dir) {
    if (dir == null || dir.trim().length() == 0) return "None";
    String d = dir.trim();
    if (d.startsWith("file://")) {
      try {
        d = java.net.URLDecoder.decode(d.substring(7).replace("+", "%2B"), "UTF-8");
      } catch (Exception ignored) {
        d = d.substring(7);
      }
    }
    if (d.startsWith("content://")) {
      int tree = d.lastIndexOf("/tree/");
      int doc = d.lastIndexOf("/document/");
      String id = doc >= 0 ? d.substring(doc + 10) : tree >= 0 ? d.substring(tree + 6) : d.substring(d.lastIndexOf('/') + 1);
      try {
        id = java.net.URLDecoder.decode(id.replace("+", "%2B"), "UTF-8");
      } catch (Exception ignored) {
        // shown as it is
      }
      int colon = id.indexOf(':');
      String path = colon >= 0 ? id.substring(colon + 1) : id;
      if (path.length() == 0) return colon >= 0 ? id.substring(0, colon) : id;
      return path;
    }
    while (d.length() > 1 && (d.endsWith("/") || d.endsWith("\\"))) d = d.substring(0, d.length() - 1);
    int slash = Math.max(d.lastIndexOf('/'), d.lastIndexOf('\\'));
    return slash >= 0 && slash < d.length() - 1 ? d.substring(slash + 1) : d;
  }

  /** The folder a MediaBrowser run says to open ("Media browser: ..."), or null. */
  public static String opened(String log) {
    if (log == null) return null;
    int at = log.lastIndexOf("Media browser: ");
    if (at < 0) return null;
    int end = log.indexOf('\n', at);
    String d = (end < 0 ? log.substring(at + 15) : log.substring(at + 15, end)).trim();
    return d.length() == 0 ? null : d;
  }
}
