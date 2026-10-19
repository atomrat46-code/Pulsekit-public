package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Playlists as .pls files (PlayList.java, File > Playlist viewer/editor, Add to playlist on the
 * thumbnails): each item a file by its path (a content:// address on the phone) or a prompt
 * library file ("pulsekit-db:3:42/beach.png"), with its title. Kept in the playlists folder beside
 * the prompt library (~/.pulsekit/playlists on the desktop), saved at every change; "Default" is
 * the one Add to default playlist fills. The viewer's choices (Repeat, Auto mode, Autoplay video,
 * the seconds a picture shows, volume, mute, speed, zoom) are kept in playlist-settings.txt.
 */
public final class Playlist {
  /** One file of the list. */
  public static final class Item {
    /** A path, a content:// address, or a library file's address (DB_PREFIX). */
    public String file = "";
    public String title = "";
    /** Seconds, as the .pls has it; -1 not known. */
    public long length = -1;

    public Item() {}

    public Item(String file, String title) {
      this.file = file == null ? "" : file.trim();
      this.title = title == null || title.trim().length() == 0 ? shortName(this.file) : title.trim();
    }

    /** What it is: MediaDir.PICTURE, VIDEO or SOUND by the title's (or the address') ending; 0 other. */
    public int kind() {
      int k = MediaDir.kind(this.title);
      return k != 0 ? k : MediaDir.kind(shortName(this.file));
    }
  }

  public static final String FOLDER = "playlists";
  public static final String DEFAULT = "Default";
  public static final String EXT = ".pls";
  /** A prompt library file in a playlist: "pulsekit-db:<which>:<versionId>/<name>". */
  public static final String DB_PREFIX = "pulsekit-db:";
  /** The menu items on the thumbnails. */
  public static final String ADD = "Add to playlist…";
  public static final String ADD_DEFAULT = "Add to default playlist";
  public static final String NEW = "New playlist…";

  public String name = DEFAULT;
  public final List<Item> items = new ArrayList<Item>();

  public Playlist() {}

  public Playlist(String name) {
    this.name = clean(name).length() == 0 ? DEFAULT : clean(name);
  }

  /** A .pls read: File1=, Title1=, Length1= (any order; numbers as given); lines it does not know are left out. */
  public static Playlist parse(String text, String name) {
    Playlist p = new Playlist(name);
    Map<Integer, Item> byNumber = new java.util.TreeMap<Integer, Item>();
    if (text == null) return p;
    for (String raw : text.replace("\r", "").split("\n")) {
      String line = raw.trim();
      int eq = line.indexOf('=');
      if (eq <= 0) continue;
      String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
      String value = line.substring(eq + 1).trim();
      String field = key.replaceAll("\\d+$", "");
      String digits = key.substring(field.length());
      if (digits.length() == 0 || !(field.equals("file") || field.equals("title") || field.equals("length"))) continue;
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
      if (field.equals("file")) it.file = fromUrl(value);
      else if (field.equals("title")) it.title = value;
      else {
        try {
          it.length = Long.parseLong(value);
        } catch (NumberFormatException ex) {
          it.length = -1;
        }
      }
    }
    for (Item it : byNumber.values()) {
      if (it.file.length() == 0) continue;
      if (it.title.length() == 0) it.title = shortName(it.file);
      p.items.add(it);
    }
    return p;
  }

  /** "file:///sdcard/a.png" as a path; anything else as it is. */
  static String fromUrl(String value) {
    if (!value.startsWith("file://")) return value;
    try {
      return java.net.URLDecoder.decode(value.substring(7).replace("+", "%2B"), "UTF-8");
    } catch (Exception ex) {
      return value.substring(7);
    }
  }

  /** The .pls text. */
  public String toPls() {
    StringBuilder sb = new StringBuilder("[playlist]\n");
    for (int i = 0; i < this.items.size(); i++) {
      Item it = this.items.get(i);
      int n = i + 1;
      sb.append("File").append(n).append('=').append(it.file).append('\n');
      sb.append("Title").append(n).append('=').append(it.title).append('\n');
      sb.append("Length").append(n).append('=').append(it.length).append('\n');
    }
    sb.append("NumberOfEntries=").append(this.items.size()).append('\n');
    sb.append("Version=2\n");
    return sb.toString();
  }

  /** Several lists as one, in order; a file in more than one comes once (where it came first). */
  public static Playlist merge(String name, List<Playlist> lists) {
    Playlist out = new Playlist(name);
    Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
    for (Playlist p : lists) {
      if (p == null) continue;
      for (Item it : p.items) {
        if (seen.containsKey(it.file)) continue;
        seen.put(it.file, Boolean.TRUE);
        Item copy = new Item(it.file, it.title);
        copy.length = it.length;
        out.items.add(copy);
      }
    }
    return out;
  }

  /** The item at `from` moved to `to` (its place in the list after); false when either is outside the list. */
  public boolean move(int from, int to) {
    if (from < 0 || from >= this.items.size() || to < 0 || to >= this.items.size()) return false;
    if (from == to) return true;
    Item it = this.items.remove(from);
    this.items.add(to, it);
    return true;
  }

  public boolean remove(int at) {
    if (at < 0 || at >= this.items.size()) return false;
    this.items.remove(at);
    return true;
  }

  /** Added at the end, unless it is in the list already; true when it was added. */
  public boolean add(String file, String title) {
    if (file == null || file.trim().length() == 0) return false;
    for (Item it : this.items) if (it.file.equals(file.trim())) return false;
    this.items.add(new Item(file, title));
    return true;
  }

  /** Whether the file is in the list (Add to playlist asks before adding it a second time). */
  public boolean has(String file) {
    return file != null && this.indexOf(file.trim()) >= 0;
  }

  /** Added at the end even when it is in the list already (asked first: "Add it a second time?"). */
  public void addAgain(String file, String title) {
    if (file == null || file.trim().length() == 0) return;
    this.items.add(new Item(file, title));
  }

  /** The question before adding a file that is in the list already. */
  public static String againQuestion(String title, String list) {
    return title + " is in " + list + " already. Add it a second time?";
  }

  /** The list's index of the file, or -1. */
  public int indexOf(String file) {
    for (int i = 0; i < this.items.size(); i++) if (this.items.get(i).file.equals(file)) return i;
    return -1;
  }

  // The playlists folder.

  public static File folder(File dir) {
    return new File(dir, FOLDER);
  }

  /** A playlist's name as a file name can have it: one line, no / \ : * ? " < > |. */
  public static String clean(String name) {
    if (name == null) return "";
    String n = name.replaceAll("[\\t\\r\\n/\\\\:*?\"<>|]+", " ").trim();
    if (n.toLowerCase(Locale.ROOT).endsWith(EXT)) n = n.substring(0, n.length() - EXT.length()).trim();
    return n;
  }

  public static File file(File dir, String name) {
    return new File(folder(dir), clean(name) + EXT);
  }

  /** The playlists kept, Default first (it is listed even before it has a file), then by name. */
  public static List<String> names(File dir) {
    List<String> out = new ArrayList<String>();
    File[] all = folder(dir).listFiles();
    if (all != null) {
      for (File f : all) {
        String n = f.getName();
        if (f.isFile() && n.toLowerCase(Locale.ROOT).endsWith(EXT)) out.add(n.substring(0, n.length() - EXT.length()));
      }
    }
    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    out.remove(DEFAULT);
    out.add(0, DEFAULT);
    return out;
  }

  /** The playlist kept by that name; empty when there is none (or it cannot be read). */
  public static Playlist load(File dir, String name) {
    File f = file(dir, name);
    try {
      if (f.isFile()) return parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), clean(name));
    } catch (Exception ignored) {
      // read as empty
    }
    return new Playlist(name);
  }

  /** Saved into the playlists folder (as it is after every change). */
  public static void save(File dir, Playlist p) throws Exception {
    File f = file(dir, p.name);
    f.getParentFile().mkdirs();
    File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
    Files.write(tmp.toPath(), p.toPls().getBytes(StandardCharsets.UTF_8));
    if (!tmp.renameTo(f)) {
      Files.write(f.toPath(), Files.readAllBytes(tmp.toPath()));
      tmp.delete();
    }
  }

  /** Add to playlist: the file at the end of the named list (made when new), saved; the status line. */
  public static String addTo(File dir, String name, String file, String title) {
    return addTo(dir, name, file, title, false);
  }

  /** As above; `again`: added even when it is in the list already (asked first). */
  public static String addTo(File dir, String name, String file, String title, boolean again) {
    String n = clean(name).length() == 0 ? DEFAULT : clean(name);
    Playlist p = load(dir, n);
    String shown = title == null || title.trim().length() == 0 ? shortName(file) : title.trim();
    if (again) p.addAgain(file, title);
    else if (!p.add(file, title)) return shown + " is in " + n + " already";
    try {
      save(dir, p);
    } catch (Exception ex) {
      return "Could not add " + shown + " to " + n + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
    return shown + (again ? " added again to playlist " : " added to playlist ") + n + " (" + p.items.size() + (p.items.size() == 1 ? " file)" : " files)");
  }

  /** Whether the file is in the named list already. */
  public static boolean inList(File dir, String name, String file) {
    return load(dir, clean(name).length() == 0 ? DEFAULT : clean(name)).has(file);
  }

  /** The Add to playlist list: the playlists, then New playlist…. */
  public static String[] menu(File dir) {
    List<String> rows = names(dir);
    rows.add(NEW);
    return rows.toArray(new String[0]);
  }

  // Library files in a playlist.

  public static String dbAddress(PromptVault.StoredFile f) {
    return DB_PREFIX + f.which + ":" + f.versionId + "/" + (f.name == null ? "" : f.name);
  }

  public static boolean isDb(String file) {
    return file != null && file.startsWith(DB_PREFIX);
  }

  /** A library address' {which, versionId}; null when it is not one. */
  public static long[] dbPlace(String file) {
    if (!isDb(file)) return null;
    String rest = file.substring(DB_PREFIX.length());
    int slash = rest.indexOf('/');
    String[] p = (slash < 0 ? rest : rest.substring(0, slash)).split(":");
    if (p.length < 2) return null;
    try {
      return new long[] {Long.parseLong(p[0]), Long.parseLong(p[1])};
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /** The file's bytes, for a library file; null for any other address. */
  public static byte[] dbBytes(PromptVault vault, String file) {
    long[] at = dbPlace(file);
    if (at == null || vault == null) return null;
    try {
      return vault.fileBytes(at[1], (int) at[0]);
    } catch (Exception ex) {
      return null;
    }
  }

  /** A library file by its name and size (Browse DB hands on a copy of it): the one of that size, else the first of that name; null when none. */
  public static PromptVault.StoredFile find(PromptVault vault, String name, long size) {
    if (vault == null || name == null) return null;
    List<PromptVault.StoredFile> all = new ArrayList<PromptVault.StoredFile>(vault.referenceFiles());
    all.addAll(vault.resultFiles());
    all.addAll(vault.promptFiles());
    PromptVault.StoredFile byName = null;
    for (PromptVault.StoredFile f : all) {
      if (!name.equals(f.name)) continue;
      if (f.size == size) return f;
      if (byName == null) byName = f;
    }
    return byName;
  }

  /**
   * PlayList.java's run: the playlist it wrote (merged) kept with the playlists by its name (one of
   * that name replaced), and its name; Default when it ran without a playlist; null when it failed.
   */
  public static String keep(File dir, JavaRun.Result r) throws Exception {
    if (r == null) return null;
    for (JavaRun.FileOut f : r.files) {
      if (!f.name.toLowerCase(Locale.ROOT).endsWith(EXT)) continue;
      Playlist p = parse(new String(f.bytes, StandardCharsets.UTF_8), f.name);
      save(dir, p);
      return p.name;
    }
    if (r.code == 0 && r.log != null && r.log.contains("Playlist: " + DEFAULT)) return DEFAULT;
    return null;
  }

  /** The last part of an address: "beach.png" for "/sdcard/Pictures/beach.png" or "pulsekit-db:3:42/beach.png". */
  public static String shortName(String file) {
    if (file == null) return "";
    String f = file;
    if (f.startsWith("content://")) {
      try {
        f = java.net.URLDecoder.decode(f, "UTF-8");
      } catch (Exception ignored) {
        // as it is
      }
    }
    int cut = Math.max(f.lastIndexOf('/'), Math.max(f.lastIndexOf('\\'), f.lastIndexOf(':')));
    return cut >= 0 && cut < f.length() - 1 ? f.substring(cut + 1) : f;
  }

  /** An item's line for the editor: "beach.png · picture · 120 KB · /sdcard/Pictures". */
  public static String info(Item it, long size) {
    int k = it.kind();
    String kind = k == MediaDir.PICTURE ? "picture" : k == MediaDir.VIDEO ? "video" : k == MediaDir.SOUND ? "sound" : "file";
    StringBuilder sb = new StringBuilder(kind);
    if (size > 0) sb.append(" · ").append(sizeText(size));
    if (it.length > 0) sb.append(" · ").append(it.length).append(" s");
    sb.append(" · ").append(isDb(it.file) ? "prompt library" : where(it.file));
    return sb.toString();
  }

  private static String where(String file) {
    String f = file;
    int cut = Math.max(f.lastIndexOf('/'), f.lastIndexOf('\\'));
    return cut > 0 ? MediaDir.label(f.substring(0, cut)) : f;
  }

  public static String sizeText(long bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " KB";
    return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }

  // The viewer's choices, kept.

  /** Repeat, Auto mode, Autoplay video, and the player's own: seconds a picture shows in Auto mode, volume, mute, speed, zoom. */
  public static final class Settings {
    public boolean repeat;
    public boolean auto;
    public boolean autoplay = true;
    /** Loop video: the video shown plays again at its end until an arrow is pressed (Auto mode is off with it). */
    public boolean loop;
    public int seconds = 5;
    public int volume = 100;
    public boolean mute;
    public float speed = 1f;
    /** A picture's zoom (zoom) and a video's (zoomVideo): each kept on its own. */
    public float zoom = 1f;
    public float zoomVideo = 1f;
    /** The playlist open last. */
    public String last = DEFAULT;

    /** Loop video on (Auto mode off then) or off. */
    public void loop(boolean on) {
      this.loop = on;
      if (on) this.auto = false;
    }

    /** Auto mode on (Loop video off then) or off. */
    public void auto(boolean on) {
      this.auto = on;
      if (on) this.loop = false;
    }
  }

  static final String SETTINGS = "playlist-settings.txt";
  public static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

  public static Settings settings(File dir) {
    Settings s = new Settings();
    boolean videoZoomKept = false;
    try {
      File f = new File(dir, SETTINGS);
      if (!f.isFile()) return s;
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int eq = line.indexOf('=');
        if (eq <= 0) continue;
        String k = line.substring(0, eq).trim();
        String v = line.substring(eq + 1).trim();
        try {
          if (k.equals("repeat")) s.repeat = Boolean.parseBoolean(v);
          else if (k.equals("auto")) s.auto = Boolean.parseBoolean(v);
          else if (k.equals("autoplay")) s.autoplay = Boolean.parseBoolean(v);
          else if (k.equals("loop")) s.loop = Boolean.parseBoolean(v);
          else if (k.equals("seconds")) s.seconds = Math.max(1, Math.min(600, Integer.parseInt(v)));
          else if (k.equals("volume")) s.volume = Math.max(0, Math.min(100, Integer.parseInt(v)));
          else if (k.equals("mute")) s.mute = Boolean.parseBoolean(v);
          else if (k.equals("speed")) s.speed = Math.max(0.25f, Math.min(4f, Float.parseFloat(v)));
          else if (k.equals("zoom")) s.zoom = (float) MediaDir.clampZoom(Float.parseFloat(v));
          else if (k.equals("zoomVideo")) {
            s.zoomVideo = (float) MediaDir.clampZoom(Float.parseFloat(v));
            videoZoomKept = true;
          }
          else if (k.equals("last")) s.last = clean(v).length() == 0 ? DEFAULT : clean(v);
        } catch (NumberFormatException ignored) {
          // that one stays as it was
        }
      }
    } catch (Exception ignored) {
      // the defaults
    }
    // Both kept on by hand in the file: Loop video wins (Auto mode is off with it).
    if (s.loop) s.auto = false;
    // From before videos had a zoom of their own: the one zoom for both.
    if (!videoZoomKept) s.zoomVideo = s.zoom;
    return s;
  }

  public static void saveSettings(File dir, Settings s) {
    String text = "repeat=" + s.repeat + "\nauto=" + s.auto + "\nautoplay=" + s.autoplay + "\nloop=" + s.loop + "\nseconds=" + s.seconds + "\nvolume=" + s.volume
        + "\nmute=" + s.mute + "\nspeed=" + s.speed + "\nzoom=" + s.zoom + "\nzoomVideo=" + s.zoomVideo + "\nlast=" + s.last + "\n";
    try {
      if (!dir.isDirectory()) dir.mkdirs();
      Files.write(new File(dir, SETTINGS).toPath(), text.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }

  /** The item after `at` in the viewer (-1: none, the end with Repeat off). */
  public static int next(int at, int size, boolean repeat) {
    if (size <= 0) return -1;
    if (at + 1 < size) return at + 1;
    return repeat ? 0 : -1;
  }

  /** The item before `at` (-1: none, the start with Repeat off). */
  public static int previous(int at, int size, boolean repeat) {
    if (size <= 0) return -1;
    if (at - 1 >= 0) return at - 1;
    return repeat ? size - 1 : -1;
  }

  /** A time as mm:ss ("01:05"); h:mm:ss past an hour. */
  public static String clock(long ms) {
    long s = Math.max(0, ms) / 1000;
    if (s >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
  }

  /** A video's place: "00:12 / 01:30" (the length "--:--" until it is known). */
  public static String time(long at, long length) {
    return clock(at) + " / " + (length > 0 ? clock(length) : "--:--");
  }

  /** "1.25×". */
  public static String speedText(float speed) {
    String s = speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed);
    return s + "×";
  }
}
