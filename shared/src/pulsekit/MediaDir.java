package pulsekit;

import java.io.File;
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
  /** The folder last picked (Params: Browse), and the folders opened under it then, in order; "" and empty when none. */
  public static volatile String lastRoot = "";
  public static volatile List<String> lastPath = new ArrayList<String>();

  /** The folders under `root` to open again: the remembered ones when `root` is the folder last picked, else none. */
  public static List<String> pathFor(String root) {
    if (root == null || !root.equals(lastRoot)) return new ArrayList<String>();
    return new ArrayList<String>(lastPath);
  }

  /** Remembers the folder picked and the folders opened under it (encode() keeps them). */
  public static void remember(String root, List<String> under) {
    lastRoot = root == null ? "" : root;
    lastPath = under == null ? new ArrayList<String>() : new ArrayList<String>(under);
  }

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
    StringBuilder path = new StringBuilder();
    for (String p : lastPath) path.append(path.length() > 0 ? "\t" : "").append(p.replace('\t', ' ').replace('\n', ' '));
    return "loop=" + (loopVideos ? 1 : 0) + "\nvolume=" + volume + "\nzoom=" + zoom + "\nspeed=" + speed
        + "\nroot=" + lastRoot.replace('\n', ' ') + "\npath=" + path + "\n";
  }

  /** Reads encode()'s text; a missing or unreadable line keeps its default (Loop off, 100%, fit, 1x). */
  public static void decode(String text) {
    loopVideos = false;
    volume = 100;
    zoom = 1;
    speed = 1;
    lastRoot = "";
    lastPath = new ArrayList<String>();
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
        else if (k.equals("root")) lastRoot = v;
        else if (k.equals("path")) {
          List<String> got = new ArrayList<String>();
          for (String part : line.substring(eq + 1).split("\t")) if (part.length() > 0) got.add(part);
          lastPath = got;
        }
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

  private static final int DB_MAX = 16 * 1024 * 1024;

  /** The menu a file's card has (a long press, or a right click on the desktop). */
  public static final String[] MENU = {"Add to DB Reference files", "Add to DB result files", "Add to default playlist"};

  /** The menu for a file already in the playlist: its third item takes it out. */
  public static final String[] MENU_LISTED = {"Add to DB Reference files", "Add to DB result files", "Remove from default playlist"};

  /**
   * Add to DB: the file into the prompt library kept in `dir`, on its own, as a reference file or
   * (with `result`) a result file; up to the library's 16 MB. Returns a line for the status.
   */
  public static String addToDb(File dir, String name, byte[] bytes, boolean result) {
    String shown = name == null || name.trim().length() == 0 ? "file" : name.trim();
    if (bytes == null || bytes.length == 0) return "Could not read " + shown;
    if (bytes.length > DB_MAX) return shown + " is over the library's 16 MB, so it is not in the DB";
    try {
      PromptVault.open(dir).addLibraryFile(shown, bytes, "Media browser", result ? 3 : 1);
      return shown + " is in the DB as a " + (result ? "result" : "reference") + " file";
    } catch (Exception ex) {
      return shown + " is not in the DB" + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
  }

  /** Over the library's 16 MB: not read in whole for Add to DB. */
  public static boolean tooBig(long size) {
    return size > DB_MAX;
  }

  /**
   * A preview's info line: a video's length and resolution ("0:42 · 1280×720"), a picture's
   * dimensions and size in MB ("1920×1080 · 2.4 MB"). Unknown parts are left out.
   */
  public static String info(int kind, int width, int height, long lengthMs, long bytes) {
    StringBuilder sb = new StringBuilder();
    if (kind == VIDEO) {
      if (lengthMs > 0) sb.append(clock(lengthMs));
      if (width > 0 && height > 0) sb.append(sb.length() > 0 ? " \u00b7 " : "").append(width).append('\u00d7').append(height);
    } else if (kind == PICTURE) {
      if (width > 0 && height > 0) sb.append(width).append('\u00d7').append(height);
      if (bytes > 0) sb.append(sb.length() > 0 ? " \u00b7 " : "").append(megabytes(bytes));
    }
    return sb.toString();
  }

  /**
   * An MP4's (or MOV's) length and picture size from its headers: {lengthMs, width, height}, each 0
   * when not found; null when it is not an MP4 that can be read.
   */
  public static long[] mp4Info(File f) {
    try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(f, "r")) {
      long[] out = new long[3];
      long moov = -1, moovEnd = -1;
      long at = 0, end = in.length();
      while (at + 8 <= end) {
        in.seek(at);
        long size = in.readInt() & 0xffffffffL;
        int type = in.readInt();
        long head = 8;
        if (size == 1) {
          size = in.readLong();
          head = 16;
        } else if (size == 0) {
          size = end - at;
        }
        if (size < head) return null;
        if (type == 0x6d6f6f76) { // moov
          moov = at + head;
          moovEnd = at + size;
          break;
        }
        at += size;
      }
      if (moov < 0) return null;
      boxes(in, moov, Math.min(moovEnd, end), out, 0);
      return out;
    } catch (Exception ex) {
      return null;
    }
  }

  /** Reads mvhd (length) and each trak's tkhd (the first with a picture size) between `from` and `to`. */
  private static void boxes(java.io.RandomAccessFile in, long from, long to, long[] out, int depth) throws java.io.IOException {
    long at = from;
    while (at + 8 <= to && depth < 4) {
      in.seek(at);
      long size = in.readInt() & 0xffffffffL;
      int type = in.readInt();
      if (size < 8 || at + size > to) return;
      if (type == 0x6d766864) { // mvhd
        int version = in.readUnsignedByte();
        in.skipBytes(3);
        long scale;
        long duration;
        if (version == 1) {
          in.skipBytes(16);
          scale = in.readInt() & 0xffffffffL;
          duration = in.readLong();
        } else {
          in.skipBytes(8);
          scale = in.readInt() & 0xffffffffL;
          duration = in.readInt() & 0xffffffffL;
        }
        if (scale > 0) out[0] = duration * 1000 / scale;
      } else if (type == 0x746b6864 && out[1] == 0) { // tkhd: width and height (16.16) are its last 8 bytes
        in.seek(at + size - 8);
        long w = (in.readInt() & 0xffffffffL) >> 16;
        long h = (in.readInt() & 0xffffffffL) >> 16;
        if (w > 0 && h > 0) {
          out[1] = w;
          out[2] = h;
        }
      } else if (type == 0x7472616b) { // trak
        boxes(in, at + 8, at + size, out, depth + 1);
      }
      at += size;
    }
  }

  /** "2.4 MB" (0.1 MB at least). */
  public static String megabytes(long bytes) {
    double mb = Math.max(0.1, bytes / (1024.0 * 1024.0));
    return String.format(java.util.Locale.US, mb < 10 ? "%.1f MB" : "%.0f MB", mb);
  }

  /** "0:42", "1:02:05". */
  public static String clock(long ms) {
    long s = Math.max(0, (ms + 500) / 1000);
    long h = s / 3600;
    long m = (s / 60) % 60;
    String sec = String.format(java.util.Locale.US, "%02d", s % 60);
    return h > 0 ? h + ":" + String.format(java.util.Locale.US, "%02d", m) + ":" + sec : m + ":" + sec;
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
