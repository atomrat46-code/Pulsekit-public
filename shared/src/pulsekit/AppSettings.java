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
 * File > General settings' Sogni and File types (app-settings.txt beside the prompt library).
 * Sogni: "Show Sogni tools" (unticked, no Sogni* program is in PyJav's list), "Show only Sogni music
 * tools and chat" (SogniMusic and SogniChat only: the image and video tools are left out). File
 * types: "Show images", "Show videos", "Show sound files", "Show text files": a type unticked is
 * left out of the thumbnail screens (its letter, I V S or T, too) and out of the playlists shown.
 */
public final class AppSettings {
  private AppSettings() {}

  static final String FILE = "app-settings.txt";
  public static final String SHOW_SOGNI = "Show Sogni tools";
  public static final String MUSIC_ONLY = "Show only Sogni music tools and chat";
  /** The file types' letters that can be left out, and their boxes' labels. */
  public static final String[] TYPE_LETTERS = {"I", "V", "S", "T"};
  public static final String[] TYPE_LABELS = {"Show images", "Show videos", "Show sound files (MIDI too)", "Show text files"};

  private static final Map<String, String> VALUES = new LinkedHashMap<String, String>();
  private static File at;

  /** The settings kept in `dir` (read again only when it is another folder). */
  public static synchronized void load(File dir) {
    File f = dir == null ? null : new File(dir, FILE);
    if (f != null && f.equals(at)) return;
    at = f;
    VALUES.clear();
    if (f == null || !f.isFile()) return;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int eq = line.indexOf('=');
        if (eq > 0) VALUES.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
      }
    } catch (Exception ignored) {
      // the defaults
    }
  }

  /** Everything as it is at first (the tests start again). */
  public static synchronized void reset() {
    VALUES.clear();
    at = null;
  }

  private static synchronized boolean flag(String key, boolean otherwise) {
    String v = VALUES.get(key);
    return v == null ? otherwise : Boolean.parseBoolean(v);
  }

  private static synchronized void set(String key, boolean on) {
    VALUES.put(key, String.valueOf(on));
    if (at == null) return;
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : VALUES.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
    try {
      if (at.getParentFile() != null) at.getParentFile().mkdirs();
      Files.write(at.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept while the program runs
    }
  }

  public static boolean showSogni() {
    return flag("sogni", true);
  }

  public static void setShowSogni(boolean on) {
    set("sogni", on);
  }

  public static boolean musicOnly() {
    return flag("sogniMusicOnly", false);
  }

  /** Only the music tools and chat: the Sogni tools are shown then. */
  public static void setMusicOnly(boolean on) {
    set("sogniMusicOnly", on);
    if (on && !showSogni()) set("sogni", true);
  }

  /** Whether PyJav lists the program: a Sogni* one only when the Sogni settings let it. */
  public static boolean listsProgram(String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (!n.startsWith("sogni")) return true;
    if (!showSogni()) return false;
    if (!musicOnly()) return true;
    return n.startsWith("sognimusic") || n.startsWith("sognichat");
  }

  /** The programs PyJav lists, in their order. */
  public static List<String> programs(List<String> names) {
    List<String> out = new ArrayList<String>();
    for (String n : names) if (listsProgram(n)) out.add(n);
    return out;
  }

  /** Whether a file type letter (I V S T) is shown; any other letter is. */
  public static boolean shows(String letter) {
    for (String l : TYPE_LETTERS) if (l.equals(letter)) return flag("show." + l, true);
    return true;
  }

  public static void setShows(String letter, boolean on) {
    set("show." + letter, on);
  }

  /** Every type shown: nothing is left out. */
  public static boolean showsAll() {
    for (String l : TYPE_LETTERS) if (!shows(l)) return false;
    return true;
  }

  /** The thumbnail screens' letters without those of the types left out. */
  public static String[] letters(String[] all) {
    List<String> out = new ArrayList<String>();
    for (String l : all) if (shows(l)) out.add(l);
    return out.toArray(new String[0]);
  }

  /** The letter a screen shows: A when its own is left out. */
  public static String letter(String now) {
    return now == null || !shows(now) ? "A" : now;
  }

  /** The type letter a file of this name comes under, for leaving it out: I, V, S, T, or "" (always shown). */
  public static String typeOf(String name) {
    int k = MediaDir.kind(name);
    if (k == MediaDir.PICTURE) return "I";
    if (k == MediaDir.VIDEO) return "V";
    if (k == MediaDir.SOUND) return "S";
    if (DbFilter.isText(name) || "T".equals(MediaDir.filterOf(name))) return "T";
    return "";
  }

  /** Whether a file of this name is shown at all (its type ticked). */
  public static boolean showsFile(String name) {
    String t = typeOf(name);
    return t.length() == 0 || shows(t);
  }

  /** The playlist as the viewer shows it: a copy without the types left out (the list itself stays whole). */
  public static Playlist shownPlaylist(Playlist p) {
    Playlist out = new Playlist(p.name);
    out.items.addAll(shownItems(p.items));
    return out;
  }

  /** Where the viewer starts in shownPlaylist(p) for item `start` of p: that item, or the next one shown. */
  public static int shownStart(Playlist p, int start) {
    int at = 0;
    for (int i = 0; i < p.items.size(); i++) {
      boolean shown = shownItems(java.util.Collections.singletonList(p.items.get(i))).size() == 1;
      if (i >= start && shown) return at;
      if (shown) at++;
    }
    return 0;
  }

  /** The viewer's line when every file of the list is of a type left out. */
  public static String nothingShown(String list) {
    return "Nothing to show in " + list + ": its file types are left out in General settings (File types)";
  }

  /** A playlist's items without those of the types left out ("If Show videos is not checked, the playlist shows no videos"). */
  public static List<Playlist.Item> shownItems(List<Playlist.Item> items) {
    List<Playlist.Item> out = new ArrayList<Playlist.Item>();
    for (Playlist.Item it : items) {
      String name = it.title.length() > 0 && MediaDir.kind(it.title) != 0 ? it.title : Playlist.shortName(it.file);
      if (showsFile(name)) out.add(it);
    }
    return out;
  }
}
