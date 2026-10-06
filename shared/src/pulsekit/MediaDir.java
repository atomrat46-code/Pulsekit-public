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

  /** Media browser: videos start again at their end (the browser's Loop videos; each app keeps it). */
  public static volatile boolean loopVideos;
  /** The Media browser's video player as last left: volume (0..100), zoom (1..4) and playback speed. */
  public static volatile int volume = 100;
  public static volatile double zoom = 1;
  public static volatile double speed = 1;

  /** Playback speeds to pick from. */
  public static final double[] SPEEDS = {0.25, 0.5, 0.75, 1, 1.25, 1.5, 2};

  /** "1x", "0.75x", "1.5x". */
  public static String speedLabel(double v) {
    String t = v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    return t + "x";
  }

  /** The speed `v` as one of SPEEDS (the nearest); 1 for anything unreadable. */
  public static double speed(double v) {
    if (Double.isNaN(v) || v <= 0) return 1;
    double best = 1;
    for (double s : SPEEDS) if (Math.abs(s - v) < Math.abs(best - v)) best = s;
    return best;
  }

  /** The settings above as text, one per line (the desktop's ~/.pulsekit/media-browser.txt, the phone's preferences). */
  public static String encode() {
    return "loop=" + (loopVideos ? 1 : 0) + "\nvolume=" + volume + "\nzoom=" + zoom + "\nspeed=" + speed + "\n";
  }

  /** Reads encode()'s text; a missing or unreadable line keeps its default (Loop off, 100%, fit, 1x). */
  public static void decode(String text) {
    loopVideos = false;
    volume = 100;
    zoom = 1;
    speed = 1;
    if (text == null) return;
    for (String line : text.split("\n")) {
      int eq = line.indexOf('=');
      if (eq < 0) continue;
      String k = line.substring(0, eq).trim();
      String v = line.substring(eq + 1).trim();
      try {
        if (k.equals("loop")) loopVideos = v.equals("1");
        else if (k.equals("volume")) volume = Math.max(0, Math.min(100, Integer.parseInt(v)));
        else if (k.equals("zoom")) zoom = clampZoom(Double.parseDouble(v));
        else if (k.equals("speed")) speed = speed(Double.parseDouble(v));
      } catch (NumberFormatException ignored) {
        // that one keeps its default
      }
    }
  }

  /** A video's zoom steps, from fitting the screen (1) to four times that. */
  private static final double[] ZOOMS = {1, 1.25, 1.5, 2, 2.5, 3, 4};

  /** The next zoom step in (bigger) or out (smaller) from `z`. */
  public static double zoom(double z, boolean in) {
    if (in) {
      for (double s : ZOOMS) if (s > z + 0.001) return s;
      return ZOOMS[ZOOMS.length - 1];
    }
    for (int i = ZOOMS.length - 1; i >= 0; i--) if (ZOOMS[i] < z - 0.001) return ZOOMS[i];
    return 1;
  }

  /** A zoom within 1x..4x (a pinch or the mouse wheel gives any value between). */
  public static double clampZoom(double z) {
    return Math.max(1, Math.min(ZOOMS[ZOOMS.length - 1], z));
  }

  /** "150%". */
  public static String zoomLabel(double z) {
    return Math.round(z * 100) + "%";
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
