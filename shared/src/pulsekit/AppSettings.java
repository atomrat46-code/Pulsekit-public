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
    unlocked = false;
  }

  private static synchronized boolean flag(String key, boolean otherwise) {
    String v = VALUES.get(key);
    return v == null ? otherwise : Boolean.parseBoolean(v);
  }

  private static synchronized void set(String key, boolean on) {
    // Settings master password on: nothing is changed until it is given (unlock).
    if (locked()) return;
    put(key, String.valueOf(on));
  }

  private static synchronized void put(String key, String value) {
    if (value == null) VALUES.remove(key);
    else VALUES.put(key, value);
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

  // ------------------------------------------------------------ Show DB/Prompts tab

  public static final String SHOW_PROMPTS_TAB = "Show DB/Prompts tab";

  public static boolean showPromptsTab() {
    return flag("promptsTab", true);
  }

  public static void setShowPromptsTab(boolean on) {
    set("promptsTab", on);
  }

  // ------------------------------------------------------------ Settings master password

  public static final String MASTER = "Settings master password";
  public static final String MASTER_NOTE = "Ticked, the settings (General settings, Database settings) are changed only after the master password "
      + "is given (Unlock settings); unticked again with the password known, they are open again.";
  public static final String UNLOCK = "Unlock settings\u2026";
  public static final String LOCKED_NOTE = "The settings are locked with the master password: Unlock settings to change them.";
  public static final int MASTER_MIN = 4;
  /** The master password was given while the settings are open (they lock again when closed). */
  private static volatile boolean unlocked;

  /** Whether a master password is set. */
  public static synchronized boolean masterOn() {
    String h = VALUES.get("masterHash");
    return h != null && h.length() > 0;
  }

  /** A master password is set and was not given yet: the settings are not changed. */
  public static boolean locked() {
    return masterOn() && !unlocked;
  }

  /** The settings closed: locked again. */
  public static void relock() {
    unlocked = false;
  }

  /** Whether `pw` is the master password; given right, the settings are unlocked. */
  public static synchronized boolean unlock(char[] pw) {
    if (!masterOn()) return true;
    try {
      byte[] salt = hex(VALUES.get("masterSalt"));
      boolean ok = java.security.MessageDigest.isEqual(hex(VALUES.get("masterHash")), hash(pw, salt));
      if (ok) unlocked = true;
      return ok;
    } catch (Exception ex) {
      return false;
    }
  }

  /** Settings master password ticked: the password (only its salted hash is kept); unlocked while the settings stay open. */
  public static synchronized void setMaster(char[] pw) throws Exception {
    if (pw == null || pw.length < MASTER_MIN) throw new IllegalArgumentException("The master password needs at least " + MASTER_MIN + " characters");
    if (locked()) throw new IllegalStateException(LOCKED_NOTE);
    byte[] salt = new byte[16];
    new java.security.SecureRandom().nextBytes(salt);
    VALUES.put("masterSalt", hexOf(salt));
    put("masterHash", hexOf(hash(pw, salt)));
    unlocked = true;
  }

  /** Settings master password unticked (the password given): the settings are open again. */
  public static synchronized void clearMaster() {
    if (locked()) return;
    VALUES.remove("masterSalt");
    put("masterHash", null);
  }

  private static byte[] hash(char[] pw, byte[] salt) throws Exception {
    return ZipPack.pbkdf2(new String(pw).getBytes(StandardCharsets.UTF_8), salt, 20000, 32);
  }

  private static String hexOf(byte[] b) {
    StringBuilder sb = new StringBuilder();
    for (byte x : b) sb.append(String.format("%02x", x & 0xff));
    return sb.toString();
  }

  private static byte[] hex(String h) {
    byte[] b = new byte[h.length() / 2];
    for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
    return b;
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
