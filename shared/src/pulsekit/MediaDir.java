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
    /** When it was added (seconds), where known: Download lists the newest first. */
    public long date;
    /** A prompt file kept in the library (Prompt files), listed under T with a star: its library id; 0 for a file in the folder. */
    public long dbId;
  }

  /** The id a library prompt file is listed by. */
  public static final String DB_PROMPT = "pulsekit-db-prompt:";

  /** What a library prompt file's name shows with: a star. */
  public static final String STAR = "\u2605 ";

  /** The name a card shows: a library prompt file's with its star. */
  public static String shownName(Entry e) {
    return e.dbId != 0 ? STAR + e.name : e.name;
  }

  /**
   * The folder's entries and, after them, the library's prompt files (the sheets runs saved with
   * --saveprompt): those show only under T, with a star. `vault` null leaves them out.
   */
  public static List<Entry> withPromptFiles(List<Entry> all, PromptVault vault) {
    List<Entry> out = new ArrayList<Entry>();
    if (all != null) out.addAll(all);
    if (vault == null) return out;
    for (PromptVault.StoredFile f : vault.promptFiles()) {
      Entry e = new Entry();
      e.name = f.name;
      e.id = DB_PROMPT + f.versionId;
      e.dbId = f.versionId;
      e.size = f.size;
      out.add(e);
    }
    return out;
  }

  /** How many entries selector `f` shows (folders left out); A leaves the library's prompt files out, as it lists the folder. */
  public static int count(List<Entry> all, String f) {
    int n = 0;
    if (all == null) return 0;
    for (Entry e : all) {
      if ("A".equals(f)) {
        if (e.dbId == 0) n++;
      } else if (FileFavourites.LETTER.equals(f) || FileGroups.LETTER.equals(f)) {
        if (!e.folder && e.dbId == 0 && marked(e, f)) n++;
      } else if (!e.folder && f.equals(filterOf(e.name))) n++;
    }
    return n;
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
    return shown(all, false);
  }

  /** As above; `every` (Choose file) keeps every file, not only pictures, videos and sounds. */
  public static List<Entry> shown(List<Entry> all, boolean every) {
    List<Entry> out = new ArrayList<Entry>();
    if (all == null) return out;
    for (Entry e : all) {
      if (e == null || e.name == null || e.name.startsWith(".")) continue;
      if (!e.folder) e.kind = kind(e.name);
      if (e.folder || e.kind != 0 || every) out.add(e);
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

  /**
   * The browser's type selectors: A all, I pictures, V videos, S sounds (MIDI too), T text, O other
   * files. Folders are always shown.
   */
  public static final String[] FILTERS = {"A", "I", "V", "S", "T", "O", FileFavourites.LETTER, FileGroups.LETTER};
  public static final String[] FILTER_NAMES = {"all", "pictures", "videos", "sounds", "text", "other files", "favourites", "a group"};

  /** F: a favourite; G: in the group G shows. */
  static boolean marked(Entry e, String f) {
    return FileFavourites.LETTER.equals(f) ? FileFavourites.isKey(groupKey(e)) : FileGroups.fits(groupKey(e));
  }

  /** A file's key in the groups (Add to group) and favourites: its name and size, as its tags have it. */
  public static String groupKey(Entry e) {
    return FileGroups.fileKey(MediaTags.key(e));
  }

  /** The selector a file comes under: "I", "V", "S", "T" or "O". */
  public static String filterOf(String name) {
    int k = kind(name);
    if (k == PICTURE) return "I";
    if (k == VIDEO) return "V";
    if (k == SOUND) return "S";
    String low = name == null ? "" : name.toLowerCase();
    if (low.matches(".+\\.(txt|md|json|prompt|csv|xml|h2song|h2pattern|rpp|py|java|log|srt|lrc)")) return "T";
    return "O";
  }

  /** The entries under selector `f` ("A" keeps the folder's all; folders stay; the library's prompt files only under T). */
  public static List<Entry> filtered(List<Entry> all, String f) {
    if (all == null) return new ArrayList<Entry>();
    List<Entry> out = new ArrayList<Entry>();
    if (f == null || "A".equals(f)) {
      // The library's prompt files show under T only.
      for (Entry e : all) if (e.dbId == 0) out.add(e);
      return out;
    }
    // F: the favourites; G: the files in the group G shows (folders stay, to go on looking).
    if (FileFavourites.LETTER.equals(f) || FileGroups.LETTER.equals(f)) {
      for (Entry e : all) if (e.folder || (e.dbId == 0 && marked(e, f))) out.add(e);
      return out;
    }
    for (Entry e : all) if (e.folder || f.equals(filterOf(e.name))) out.add(e);
    return out;
  }

  /** The selector the browser shows: kept for the session (Choose file opens on it again). */
  public static String filter = "A";

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
    if (d.equals("pulsekit:downloads")) return "Download";
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

  /**
   * Media browser: Add to favourites. Each is a folder as "root\tfolder under it\t...", the root as
   * Params gives it (a granted folder's content:// address, or a path); kept sealed like the folder
   * last opened. Choose file shows them as tabs after Recent MB folder and Download, and opens on
   * the tab used last (lastTab).
   */
  public static volatile List<String> favourites = new ArrayList<String>();

  /**
   * The Media browser's place in each folder, so it opens there again: the page on the phone, the
   * scroll position on the desktop. Kept by a hash of the folder (no folder names in the settings),
   * the 60 folders seen last.
   */
  private static final java.util.LinkedHashMap<String, Integer> PLACES = new java.util.LinkedHashMap<String, Integer>(16, 0.75f, true);

  public static synchronized int place(String folder) {
    Integer at = PLACES.get(placeKey(folder));
    return at == null ? 0 : at.intValue();
  }

  public static synchronized void place(String folder, int at) {
    String k = placeKey(folder);
    if (at <= 0) PLACES.remove(k);
    else PLACES.put(k, Integer.valueOf(at));
    while (PLACES.size() > 60) PLACES.remove(PLACES.keySet().iterator().next());
  }

  private static String placeKey(String folder) {
    return Integer.toHexString((folder == null ? "" : folder).hashCode());
  }

  /** Whether the folder (`root`, then the folders opened under it) is a favourite. */
  public static synchronized boolean isFavourite(String root, List<String> under) {
    StringBuilder sb = new StringBuilder(root == null ? "" : root.replace('\t', ' ').replace('\n', ' '));
    if (under != null) for (String u : under) sb.append('\t').append(u.replace('\t', ' ').replace('\n', ' '));
    return favourites.contains(sb.toString());
  }

  /** Adds the folder (`root`, then the folders opened under it); false when it is a favourite already. */
  public static synchronized boolean addFavourite(String root, List<String> under) {
    StringBuilder sb = new StringBuilder(root == null ? "" : root.replace('\t', ' ').replace('\n', ' '));
    if (under != null) for (String u : under) sb.append('\t').append(u.replace('\t', ' ').replace('\n', ' '));
    String f = sb.toString();
    if (f.trim().length() == 0 || favourites.contains(f)) return false;
    List<String> next = new ArrayList<String>(favourites);
    next.add(f);
    favourites = next;
    return true;
  }

  /**
   * Choose file's tab used last, so Choose file opens on it again: "recent", "download", or a
   * favourite as its folder text (kept sealed); "" when none.
   */
  public static volatile String lastTab = "";

  /** Choose file opened `tab` ("recent", "download", "fav:<its place in favourites>"). */
  public static synchronized void usedTab(String tab) {
    if (tab == null) return;
    if (tab.startsWith("fav:")) {
      try {
        int i = Integer.parseInt(tab.substring(4));
        if (i >= 0 && i < favourites.size()) lastTab = "fav\t" + favourites.get(i);
      } catch (NumberFormatException ignored) {
        // not a favourite tab
      }
    } else if (tab.equals("recent") || tab.equals("download")) {
      lastTab = tab;
    }
  }

  /**
   * The tab Choose file opens on: the one used last (a favourite still among the favourites, the
   * Recent MB folder when `recentThere`, Download), else the Recent MB folder, else Download.
   */
  public static synchronized String openTab(boolean recentThere) {
    String t = lastTab == null ? "" : lastTab;
    if (t.startsWith("fav\t")) {
      int i = favourites.indexOf(t.substring(4));
      if (i >= 0) return "fav:" + i;
    } else if (t.equals("download")) {
      return "download";
    }
    return recentThere ? "recent" : "download";
  }

  /** Takes a favourite out (its place in favourites). */
  public static synchronized void removeFavourite(int i) {
    if (i < 0 || i >= favourites.size()) return;
    List<String> next = new ArrayList<String>(favourites);
    next.remove(i);
    favourites = next;
  }

  /** A favourite's tab: its folder's name. */
  public static String favouriteLabel(String f) {
    String[] part = f.split("\t");
    return label(part[part.length - 1]);
  }

  /**
   * MediaBrowser.java run without a folder (Run pressed, Params not opened this time): the folder the
   * Media browser opened last, so Run opens it again. `argv` itself when it names a folder or none is known.
   */
  public static List<String> withLastRoot(List<String> argv) {
    if (argv == null) return argv;
    for (String a : argv) {
      if (a == null) continue;
      String t = a.trim();
      if (t.startsWith("content://") || (t.length() > 0 && !t.startsWith("<") && !t.startsWith("-") && new java.io.File(t).isDirectory())) return argv;
    }
    if (lastRoot == null || lastRoot.length() == 0) return argv;
    List<String> out = new ArrayList<String>();
    for (String a : argv) if (a != null && !a.trim().startsWith("<") && a.trim().length() > 0) out.add(a);
    out.add(lastRoot);
    return out;
  }

  /** The folder captured frames go in, under the video's folder (C in the video player). */
  public static final String FRAMES_DIR = "SC";

  /** A captured frame's name: "clip-0m12s345.png" for clip.mp4 at 0:12.345. */
  public static String frameName(String video, long ms) {
    String stem = (video == null ? "video" : video).replaceAll("\\.[A-Za-z0-9]{1,5}$", "").replace('/', '_').replace('\\', '_');
    long t = Math.max(0, ms);
    return stem + "-" + (t / 60000) + "m" + String.format(java.util.Locale.ROOT, "%02d", (t / 1000) % 60) + "s" + String.format(java.util.Locale.ROOT, "%03d", t % 1000) + ".png";
  }

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

  /** The settings above as text, one per line; the folder (place) sealed (the desktop's ~/.pulsekit/media-browser.txt, the phone's preferences). */
  public static String encode() {
    StringBuilder place = new StringBuilder(lastRoot.replace('\n', ' ').replace('\t', ' '));
    for (String p : lastPath) place.append('\t').append(p.replace('\t', ' ').replace('\n', ' '));
    String sealed = "";
    // The folder and the folders under it are sealed with the library's key (Sealed); without a key they are not kept.
    if (lastRoot.length() > 0) {
      try {
        sealed = Sealed.sealText(place.toString());
      } catch (Exception ex) {
        sealed = "";
      }
    }
    StringBuilder favs = new StringBuilder();
    for (String f : favourites) {
      try {
        favs.append("\nfav=").append(Sealed.sealText(f));
      } catch (Exception ex) {
        // without a key, favourites are not kept
      }
    }
    String tab = lastTab == null ? "" : lastTab;
    if (tab.startsWith("fav\t")) {
      try {
        tab = "fav:" + Sealed.sealText(tab.substring(4));
      } catch (Exception ex) {
        // without a key, favourites are not kept
        tab = "";
      }
    }
    StringBuilder places = new StringBuilder();
    synchronized (MediaDir.class) {
      for (java.util.Map.Entry<String, Integer> e : PLACES.entrySet()) places.append("\nat=").append(e.getKey()).append(':').append(e.getValue());
    }
    return "loop=" + (loopVideos ? 1 : 0) + "\nvolume=" + volume + "\nzoom=" + zoom + "\nspeed=" + speed
        + (sealed.length() > 0 ? "\nplace=" + sealed : "") + favs + (tab.length() > 0 ? "\ntab=" + tab : "") + places + "\n";
  }

  /** Reads encode()'s text; a missing or unreadable line keeps its default (Loop off, 100%, fit, 1x). */
  public static void decode(String text) {
    loopVideos = false;
    volume = 100;
    zoom = 1;
    speed = 1;
    lastRoot = "";
    lastPath = new ArrayList<String>();
    List<String> favs = new ArrayList<String>();
    favourites = favs;
    lastTab = "";
    synchronized (MediaDir.class) {
      PLACES.clear();
    }
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
        else if (k.equals("at")) {
          int colon = v.lastIndexOf(':');
          if (colon > 0) {
            synchronized (MediaDir.class) {
              PLACES.put(v.substring(0, colon), Integer.valueOf(Integer.parseInt(v.substring(colon + 1).trim())));
            }
          }
        }
        else if (k.equals("tab")) {
          if (v.startsWith("fav:")) {
            try {
              lastTab = "fav\t" + Sealed.openText(v.substring(4));
            } catch (Exception ex) {
              // unreadable: opens as before
            }
          } else {
            lastTab = v;
          }
        }
        else if (k.equals("fav")) {
          try {
            favs.add(Sealed.openText(v));
          } catch (Exception ex) {
            // unreadable: left out
          }
        }
        else if (k.equals("place")) {
          String[] part;
          try {
            part = Sealed.openText(v).split("\t");
          } catch (Exception ex) {
            continue;
          }
          lastRoot = part[0];
          List<String> got = new ArrayList<String>();
          for (int i = 1; i < part.length; i++) if (part[i].length() > 0) got.add(part[i]);
          lastPath = got;
        }
        // Plain lines from before the folder was sealed (sealed with the next save).
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

  private static final int DB_MAX = 128 * 1024 * 1024;

  /** The menu a file's card has (a long press, or a right click on the desktop). */
  /** A file's menu with `item` (Add to playlist…) right under Add to (or Remove from) default playlist; at the end when that is not there. */
  public static String[] withPlaylistItem(String[] menu, String item) {
    List<String> out = new ArrayList<String>(java.util.Arrays.asList(menu));
    int at = out.indexOf(MENU[2]);
    if (at < 0) at = out.indexOf(MENU_LISTED[2]);
    out.add(at < 0 ? out.size() : at + 1, item);
    return out.toArray(new String[0]);
  }

  public static final String[] MENU = {"Add to DB Reference files", "Add to DB result files", "Add to default playlist"};

  /** The menu for a file already in the playlist: its third item takes it out. */
  public static final String[] MENU_LISTED = {"Add to DB Reference files", "Add to DB result files", "Remove from default playlist"};

  /** A picture's fourth item: ImageUpscaler loaded with the picture as its input, and its Params open. */
  public static final String UPSCALE = "Upscale/resize image";
  public static final int UPSCALE_ITEM = 3;

  /** The file items after those: on the file itself (not in Download, where files have no folders). */
  public static final String RENAME = "Rename";
  public static final String COPY_TO = "Copy to folder\u2026";
  public static final String MOVE_TO = "Move to folder\u2026";
  public static final String DELETE = "Delete";
  public static final String[] FILE_ACTIONS = {RENAME, COPY_TO, MOVE_TO, DELETE};
  /** A folder's long-press menu. */
  public static final String[] FOLDER_MENU = {RENAME, DELETE};

  /** The menu for a file: MENU or MENU_LISTED, and Upscale/resize image for a picture. */
  public static String[] menu(boolean listed, int kind) {
    String[] base = listed ? MENU_LISTED : MENU;
    if (kind != PICTURE) return base;
    String[] out = java.util.Arrays.copyOf(base, base.length + 1);
    out[UPSCALE_ITEM] = UPSCALE;
    return out;
  }

  /** A sound, MIDI or text file by its name: no picture to make for it, so its bytes need not be read (a large WAV ran the phone out of memory). */
  public static boolean noThumb(String name) {
    String low = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    return low.matches(".+\\.(wav|wave|mp3|ogg|m4a|aac|flac|mid|midi|txt|md|json|prompt|csv|xml|pls)");
  }

  /** A library file's (Ref, Result and Prompt files) copy on disk, to work on with another program. */
  public static final String EXPORT = "Export to disk\u2026";

  /** A MIDI file's items: the MIDI editor, Midi2Wav (a SoundFont), vst_render (a plugin; computer only), played with the system's own SoundFont. */
  public static final String MIDI_EDIT = "Open in MIDI editor";
  public static final String RENDER_SF2 = "Render with SF2";
  public static final String RENDER_PLUGIN = "Render with plugin";
  public static final String PLAY_SF = "Play with default SoundFont";
  /** A MIDI's: its patterns and fills (a file set made of it) onto the Composer's stack. */
  public static final String TO_COMPOSER = "Add to composer stack";
  /** A MIDI's: kept as the source MIDI of a file set picked from the list (From file set finds it there). */
  public static final String TO_FILE_SET = "Add into file set";
  /** A WAV or MP3's item: DrumMidi_CRT on it, its drum hits as a MIDI. */
  public static final String TO_MIDI = "Convert drum hits into MIDI";
  /** An MP3's item: decoded to a WAV (beside it; in the library, a new file of the same gallery). */
  public static final String TO_WAV = "Convert to WAV";

  /** The items `name` has for what it is: a MIDI's (with Render with plugin when `plugins`), a sound's. */
  public static String[] kindItems(String name, boolean plugins) {
    if (isMidi(name)) return plugins ? new String[] {MIDI_EDIT, RENDER_SF2, RENDER_PLUGIN, PLAY_SF, TO_COMPOSER, TO_FILE_SET} : new String[] {MIDI_EDIT, RENDER_SF2, PLAY_SF, TO_COMPOSER, TO_FILE_SET};
    if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) return new String[] {TO_MIDI, TO_WAV};
    if (name != null && kind(name) == SOUND) return new String[] {TO_MIDI};
    return new String[0];
  }

  /** True for the items kindItems gives. */
  public static boolean isKindItem(String item) {
    return MIDI_EDIT.equals(item) || RENDER_SF2.equals(item) || RENDER_PLUGIN.equals(item) || PLAY_SF.equals(item) || TO_MIDI.equals(item) || TO_COMPOSER.equals(item) || TO_FILE_SET.equals(item) || TO_WAV.equals(item);
  }

  /** As menu(listed, kind, files), with `name`'s own items (kindItems) before Tags…. */
  public static String[] menu(boolean listed, int kind, boolean files, String name, boolean plugins) {
    String[] all = menu(listed, kind, files);
    String[] extra = kindItems(name, plugins);
    if (extra.length == 0) return all;
    int at = menu(listed, kind).length;
    String[] out = new String[all.length + extra.length];
    System.arraycopy(all, 0, out, 0, at);
    System.arraycopy(extra, 0, out, at, extra.length);
    System.arraycopy(all, at, out, at + extra.length, all.length - at);
    return out;
  }

  /**
   * As above, then Tags…, Category… (MediaTags), Add to group (FileGroups) and Add to favourites, then Rename, Copy to folder, Move to folder and
   * Delete (`files`: the file can be changed there).
   */
  public static String[] menu(boolean listed, int kind, boolean files) {
    String[] first = menu(listed, kind);
    String[] base = java.util.Arrays.copyOf(first, first.length + 4);
    base[first.length] = MediaTags.TAGS;
    base[first.length + 1] = MediaTags.CATEGORY;
    base[first.length + 2] = FileGroups.ADD;
    base[first.length + 3] = FileFavourites.ADD_SHORT;
    if (!files) return base;
    String[] out = java.util.Arrays.copyOf(base, base.length + FILE_ACTIONS.length);
    System.arraycopy(FILE_ACTIONS, 0, out, base.length, FILE_ACTIONS.length);
    return out;
  }

  /** A new name for a file or folder: one name, no folder parts; null when it is not one. */
  public static String cleanName(String typed) {
    String n = typed == null ? "" : typed.replace('\n', ' ').replace('\r', ' ').trim();
    if (n.length() == 0 || n.equals(".") || n.equals("..") || n.indexOf('/') >= 0 || n.indexOf('\\') >= 0 || n.indexOf(':') >= 0) return null;
    return n;
  }

  /** A file name keeping the old one's ending when the new one has none ("beach" for beach.png gives beach.png). */
  public static String keepEnding(String old, String typed) {
    String n = cleanName(typed);
    if (n == null || old == null) return n;
    int dot = old.lastIndexOf('.');
    if (dot <= 0 || n.lastIndexOf('.') > 0) return n;
    return n + old.substring(dot);
  }

  /** The folder (and the folders under it) as a whole: deleted, true when all of it went. */
  public static boolean deleteAll(File f) {
    if (f == null || !f.exists()) return false;
    File[] kids = f.isDirectory() ? f.listFiles() : null;
    boolean ok = true;
    if (kids != null) for (File k : kids) ok &= deleteAll(k);
    return f.delete() && ok;
  }

  /** `f` copied into folder `to` (a free name there: "a (1).png"); the copy, or an exception. */
  public static File copyInto(File f, File to) throws java.io.IOException {
    File out = freeName(to, f.getName());
    if (f.isDirectory()) {
      if (!out.mkdirs()) throw new java.io.IOException("could not make " + out.getName());
      File[] kids = f.listFiles();
      if (kids != null) for (File k : kids) copyInto(k, out);
      return out;
    }
    java.nio.file.Files.copy(f.toPath(), out.toPath());
    return out;
  }

  /** `f` moved into folder `to` (a free name there); the moved file, or an exception. */
  public static File moveInto(File f, File to) throws java.io.IOException {
    if (to.getAbsolutePath().startsWith(f.getAbsolutePath() + File.separator) || to.equals(f)) throw new java.io.IOException("a folder cannot go inside itself");
    File out = freeName(to, f.getName());
    if (f.renameTo(out)) return out;
    File copy = copyInto(f, to);
    deleteAll(f);
    return copy;
  }

  /** `name` in folder `dir`, or "name (1).ext"... when it is taken. */
  public static File freeName(File dir, String name) {
    String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    String ext = name.substring(stem.length());
    File f = new File(dir, name);
    for (int n = 1; f.exists(); n++) f = new File(dir, stem + " (" + n + ")" + ext);
    return f;
  }

  /**
   * Add to DB: the file into the prompt library kept in `dir`, on its own, as a reference file or
   * (with `result`) a result file; up to the library's 128 MB. Returns a line for the status.
   */
  public static String addToDb(File dir, String name, byte[] bytes, boolean result) {
    String shown = name == null || name.trim().length() == 0 ? "file" : name.trim();
    if (bytes == null || bytes.length == 0) return "Could not read " + shown;
    if (bytes.length > DB_MAX) return shown + " is over the library's 128 MB, so it is not in the DB";
    try {
      PromptVault.open(dir).addLibraryFile(shown, bytes, "Media browser", result ? 3 : 1);
      return shown + " is in the DB as a " + (result ? "result" : "reference") + " file";
    } catch (Exception ex) {
      return shown + " is not in the DB" + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
  }

  /** Over the library's 128 MB: not read in whole for Add to DB. */
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
