package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Plugin Browser's model (PyJav > Plugins): the plugin and kit types, which of them each app
 * can use and how, the folders searched for each type (kept in plugin-folders.txt beside the
 * settings), and what the user keeps for each plugin (plugin-library.txt): favourite, category,
 * group, tags and its place in My order. Lists sort by name, type, category, group, date or My order
 * and filter by type, favourites, category, group, tag and a search.
 */
public final class PluginLibrary {
  private PluginLibrary() {}

  /** A type: its name, file endings, whether its plugins are folders (bundles), and what each app does with it. */
  public static final class Type {
    public final String name;
    public final String[] ends;
    public final boolean desktop;
    public final boolean android;
    public final String how;

    Type(String name, String[] ends, boolean desktop, boolean android, String how) {
      this.name = name;
      this.ends = ends;
      this.desktop = desktop;
      this.android = android;
      this.how = how;
    }

    public boolean usable(boolean onAndroid) {
      return onAndroid ? this.android : this.desktop;
    }
  }

  public static final String VST3 = "VST3", VST2 = "VST2", AU = "Audio Unit", CLAP = "CLAP", LV2 = "LV2", SF2 = "SF2", SFZ = "SFZ";

  public static final Type[] TYPES = {
    new Type(VST3, new String[] {".vst3"}, true, false, "vst_render.py plays MIDI through it (Test, Edit settings for its kit); desktop only: Android cannot load VST plugins."),
    new Type(VST2, new String[] {".dll", ".vst", ".so"}, true, false, "vst_render.py plays MIDI through it (Test, Edit settings); desktop only."),
    new Type(AU, new String[] {".component"}, true, false, "vst_render.py plays MIDI through it on macOS (Test, Edit settings)."),
    new Type(LV2, new String[] {".lv2"}, true, false, "Played through Carla: save a Carla project with the plugin in it, then vst_render.py --carla <project>. Listed, not tested here."),
    new Type(CLAP, new String[] {".clap"}, true, true, "Desktop: vst_render.py plays MIDI through it in Carla's Carla-Rack (Carla 2.6 or newer; Test, Edit settings). "
        + "Android: Import .clap takes one built for Android (arm64); Test plays a tune through it, Render MIDI\u2026 plays a MIDI file into a WAV."),
    new Type(SF2, new String[] {".sf2"}, true, true, "A SoundFont: Use puts its drums onto the pads; Test plays a beat with it; Midi2Wav.java --sf2 renders MIDI with it."),
    new Type(SFZ, new String[] {".sfz"}, true, true, "An SFZ kit (its samples beside it): Use puts its drums onto the pads; Test plays a beat with it; Midi2Wav.java --sf2 renders with it.")};

  /** Android: Pulsekit's CLAP folder (files/clap), set when the browser opens. */
  public static String androidClapDir;

  public static Type type(String name) {
    for (Type t : TYPES) if (t.name.equals(name)) return t;
    return null;
  }

  /** The type of a file or bundle by its ending, or null. */
  public static Type typeOf(File f) {
    String low = f.getName().toLowerCase(Locale.ROOT);
    for (Type t : TYPES) for (String e : t.ends) if (low.endsWith(e) && low.length() > e.length()) return t;
    return null;
  }

  /** The supported-types table the browser's Types shows (both apps, this one marked). */
  public static String typesTable(boolean onAndroid) {
    StringBuilder sb = new StringBuilder("Plugin and kit types (" + (onAndroid ? "Android" : "desktop") + " is this app):\n\n");
    for (Type t : TYPES) {
      sb.append(t.name).append(" (").append(String.join(", ", t.ends)).append(")  desktop: ").append(t.desktop ? "yes" : "no").append(", Android: ").append(t.android ? "yes" : "no")
          .append(t.usable(onAndroid) ? "" : "  [not on this app]").append("\n  ").append(t.how).append("\n\n");
    }
    sb.append("Android plays drum kits (SF2, SFZ) on the pads and CLAP plugins built for Android; VST, AU and LV2 plugins are desktop code, which Android apps cannot load.");
    return sb.toString();
  }

  // ------------------------------------------------------------ folders

  static final String FOLDERS = "plugin-folders.txt";
  static final String LIBRARY = "plugin-library.txt";
  private static File dir;
  private static final Map<String, List<String>> folders = new LinkedHashMap<String, List<String>>();
  private static final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();

  /** What is kept for one plugin (by its path). */
  public static final class Entry {
    public boolean favourite;
    public String category = "";
    public String group = "";
    public final Set<String> tags = new LinkedHashSet<String>();
    /** Its place in My order (0: not placed, after the placed ones). */
    public int order;
  }

  /** The folders searched first for each type on this system, as the makers install them. */
  public static List<String> defaults(String type, boolean onAndroid, String home) {
    List<String> out = new ArrayList<String>();
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    boolean win = os.contains("win"), mac = os.contains("mac");
    String pf = System.getenv("ProgramFiles") != null ? System.getenv("ProgramFiles") : "C:\\Program Files";
    String common = System.getenv("CommonProgramFiles") != null ? System.getenv("CommonProgramFiles") : pf + "\\Common Files";
    if (onAndroid) {
      if (type.equals(SF2) || type.equals(SFZ)) {
        out.add(home + "/Download");
        out.add(home + "/Download/Pulsekit/kits");
        out.add(home + "/Music");
      }
      return out;
    }
    if (type.equals(VST3)) {
      if (win) out.add(common + "\\VST3");
      else if (mac) {
        out.add("/Library/Audio/Plug-Ins/VST3");
        out.add(home + "/Library/Audio/Plug-Ins/VST3");
      } else {
        out.add(home + "/.vst3");
        out.add("/usr/lib/vst3");
        out.add("/usr/local/lib/vst3");
      }
    } else if (type.equals(VST2)) {
      if (win) {
        out.add(pf + "\\VSTPlugins");
        out.add(pf + "\\Steinberg\\VSTPlugins");
        out.add(common + "\\VST2");
      } else if (mac) {
        out.add("/Library/Audio/Plug-Ins/VST");
        out.add(home + "/Library/Audio/Plug-Ins/VST");
      } else {
        out.add(home + "/.vst");
        out.add("/usr/lib/vst");
      }
    } else if (type.equals(AU)) {
      if (mac) {
        out.add("/Library/Audio/Plug-Ins/Components");
        out.add(home + "/Library/Audio/Plug-Ins/Components");
      }
    } else if (type.equals(CLAP)) {
      if (win) out.add(common + "\\CLAP");
      else if (mac) out.add("/Library/Audio/Plug-Ins/CLAP");
      else {
        out.add(home + "/.clap");
        out.add("/usr/lib/clap");
      }
    } else if (type.equals(LV2)) {
      if (win) out.add(common + "\\LV2");
      else if (mac) out.add("/Library/Audio/Plug-Ins/LV2");
      else {
        out.add(home + "/.lv2");
        out.add("/usr/lib/lv2");
        out.add("/usr/local/lib/lv2");
      }
    } else if (type.equals(SF2) || type.equals(SFZ)) {
      out.add(home + File.separator + ".pulsekit" + File.separator + "kits");
      out.add(home + File.separator + "Downloads");
      if (type.equals(SF2) && !win && !mac) out.add("/usr/share/sounds/sf2");
    }
    return out;
  }

  /** The settings kept in `d` (read again only for another folder). */
  public static synchronized void load(File d) {
    if (d != null && d.equals(dir)) return;
    dir = d;
    folders.clear();
    entries.clear();
    if (d == null) return;
    for (String line : lines(new File(d, FOLDERS))) {
      int eq = line.indexOf('=');
      if (eq <= 0) continue;
      List<String> paths = new ArrayList<String>();
      for (String p : line.substring(eq + 1).split("\\|")) if (p.trim().length() > 0) paths.add(p.trim());
      folders.put(line.substring(0, eq).trim(), paths);
    }
    for (String line : lines(new File(d, LIBRARY))) {
      String[] f = line.split("\t", -1);
      if (f.length < 6) continue;
      Entry e = new Entry();
      e.favourite = "1".equals(f[1]);
      e.category = f[2];
      e.group = f[3];
      for (String t : f[4].split(",")) if (t.trim().length() > 0) e.tags.add(t.trim());
      try {
        e.order = Integer.parseInt(f[5]);
      } catch (NumberFormatException ignored) {
        e.order = 0;
      }
      entries.put(f[0], e);
    }
  }

  /** Everything as at first (the tests start again). */
  public static synchronized void reset() {
    dir = null;
    folders.clear();
    entries.clear();
  }

  /** The type's own folders, or (none kept) its defaults. */
  public static synchronized List<String> folders(String type, boolean onAndroid, String home) {
    List<String> kept = folders.get(type);
    return kept != null ? new ArrayList<String>(kept) : defaults(type, onAndroid, home);
  }

  /** Whether the type's folders are its own (Settings changed them) rather than the defaults. */
  public static synchronized boolean ownFolders(String type) {
    return folders.containsKey(type);
  }

  /** The type's folders kept (null: back to the defaults). */
  public static synchronized void setFolders(String type, List<String> paths) {
    if (paths == null) folders.remove(type);
    else folders.put(type, new ArrayList<String>(paths));
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> e : folders.entrySet()) sb.append(e.getKey()).append('=').append(String.join("|", e.getValue())).append('\n');
    write(FOLDERS, sb.toString());
  }

  // ------------------------------------------------------------ the plugins

  /** How deep folders are looked through (makers keep their plugins in folders of their own). */
  public static final int DEPTH = 4;

  /** The plugins of the types this app uses (all types on the desktop) in their folders, by path. */
  public static List<File> scan(boolean onAndroid, String home, List<File> more) {
    Set<File> found = new LinkedHashSet<File>();
    for (Type t : TYPES) {
      if (onAndroid && !t.android) continue;
      for (String p : folders(t.name, onAndroid, home)) walk(new File(p), 0, t, found);
      // Android loads a plugin's code only from Pulsekit's own folder: its CLAP folders' plugins are copied there (ClapHost).
      if (onAndroid && t.name.equals(CLAP)) {
        found.removeIf(f -> androidClapDir == null || !f.getAbsolutePath().startsWith(androidClapDir));
        if (androidClapDir != null) walk(new File(androidClapDir), 0, t, found);
      }
    }
    if (more != null) for (File f : more) if (f != null && typeOf(f) != null && f.exists()) found.add(f.getAbsoluteFile());
    return new ArrayList<File>(found);
  }

  private static void walk(File dir, int depth, Type only, Set<File> found) {
    File[] kids = dir.listFiles();
    if (kids == null) return;
    for (File k : kids) {
      Type t = typeOf(k);
      if (t == only) found.add(k.getAbsoluteFile());
      // A bundle (.vst3, .component, .lv2) is the plugin, not a folder to look in.
      else if (k.isDirectory() && t == null && depth < DEPTH && !k.getName().startsWith(".")) walk(k, depth + 1, only, found);
    }
  }

  public static String name(File f) {
    Type t = typeOf(f);
    String n = f.getName();
    if (t != null) for (String e : t.ends) if (n.toLowerCase(Locale.ROOT).endsWith(e)) return n.substring(0, n.length() - e.length());
    return n;
  }

  public static synchronized Entry entry(File f) {
    Entry e = entries.get(f.getAbsolutePath());
    return e != null ? e : new Entry();
  }

  private static synchronized Entry edit(File f) {
    Entry e = entries.get(f.getAbsolutePath());
    if (e == null) {
      e = new Entry();
      entries.put(f.getAbsolutePath(), e);
    }
    return e;
  }

  /** The favourites' paths. */
  public static synchronized Set<String> favouritePaths() {
    Set<String> out = new LinkedHashSet<String>();
    for (Map.Entry<String, Entry> me : entries.entrySet()) if (me.getValue().favourite) out.add(me.getKey());
    return out;
  }

  public static synchronized boolean toggleFavourite(File f) {
    Entry e = edit(f);
    e.favourite = !e.favourite;
    save();
    return e.favourite;
  }

  public static synchronized void setFavourite(File f, boolean on) {
    edit(f).favourite = on;
    save();
  }

  public static synchronized void setCategory(File f, String c) {
    edit(f).category = clean(c);
    save();
  }

  public static synchronized void setGroup(File f, String g) {
    edit(f).group = clean(g);
    save();
  }

  /** The tags (comma between them). */
  public static synchronized void setTags(File f, String tags) {
    Entry e = edit(f);
    e.tags.clear();
    for (String t : (tags == null ? "" : tags).split(",")) if (clean(t).length() > 0) e.tags.add(clean(t));
    save();
  }

  /** My order: the plugin one place up (-1) or down (+1) among `shown` (the list as it is, My order). */
  public static synchronized void move(List<File> shown, File f, int by) {
    int at = shown.indexOf(f);
    int to = at + by;
    if (at < 0 || to < 0 || to >= shown.size()) return;
    List<File> order = new ArrayList<File>(shown);
    Collections.swap(order, at, to);
    for (int i = 0; i < order.size(); i++) edit(order.get(i)).order = i + 1;
    save();
  }

  /** The categories, groups and tags in use, sorted (the filters' lists). */
  public static synchronized List<String> categories() {
    Set<String> out = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (Entry e : entries.values()) if (e.category.length() > 0) out.add(e.category);
    return new ArrayList<String>(out);
  }

  public static synchronized List<String> groups() {
    Set<String> out = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (Entry e : entries.values()) if (e.group.length() > 0) out.add(e.group);
    return new ArrayList<String>(out);
  }

  public static synchronized List<String> tags() {
    Set<String> out = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (Entry e : entries.values()) out.addAll(e.tags);
    return new ArrayList<String>(out);
  }

  public static final String[] SORTS = {"Name A\u2013Z", "Name Z\u2013A", "Type", "Category", "Group", "Newest first", "My order"};
  /** A filter's first choice: no filter. */
  public static final String ANY = "All";

  /** The list as shown: the filters (ANY or "" for none), then the sort. */
  public static List<File> shown(List<File> all, String type, boolean favouritesOnly, String category, String group, String tag, String search, String sort) {
    List<File> out = new ArrayList<File>();
    for (File f : all) {
      Entry e = entry(f);
      Type t = typeOf(f);
      if (on(type) && (t == null || !t.name.equals(type))) continue;
      if (favouritesOnly && !e.favourite) continue;
      if (on(category) && !e.category.equalsIgnoreCase(category)) continue;
      if (on(group) && !e.group.equalsIgnoreCase(group)) continue;
      if (on(tag) && !containsIgnoreCase(e.tags, tag)) continue;
      if (!matches(name(f) + " " + e.category + " " + e.group + " " + String.join(" ", e.tags), search)) continue;
      out.add(f);
    }
    final Comparator<File> byName = (a, b) -> name(a).compareToIgnoreCase(name(b));
    Comparator<File> c;
    String s = sort == null ? SORTS[0] : sort;
    if (s.equals(SORTS[1])) c = byName.reversed();
    else if (s.equals(SORTS[2])) c = Comparator.comparing((File f) -> typeOf(f) == null ? "" : typeOf(f).name).thenComparing(byName);
    else if (s.equals(SORTS[3])) c = Comparator.comparing((File f) -> blankLast(entry(f).category)).thenComparing(byName);
    else if (s.equals(SORTS[4])) c = Comparator.comparing((File f) -> blankLast(entry(f).group)).thenComparing(byName);
    else if (s.equals(SORTS[5])) c = Comparator.comparingLong((File f) -> -f.lastModified()).thenComparing(byName);
    else if (s.equals(SORTS[6])) c = Comparator.comparingInt((File f) -> entry(f).order == 0 ? Integer.MAX_VALUE : entry(f).order).thenComparing(byName);
    else c = byName;
    Collections.sort(out, c);
    return out;
  }

  /** Whether a text matches a search: anywhere in it, case aside; * any letters, ? one. */
  public static boolean matches(String text, String search) {
    String q = search == null ? "" : search.trim();
    if (q.isEmpty()) return true;
    StringBuilder re = new StringBuilder(".*");
    for (char ch : q.toCharArray()) re.append(ch == '*' ? ".*" : ch == '?' ? "." : java.util.regex.Pattern.quote(String.valueOf(ch)));
    return java.util.regex.Pattern.compile(re.append(".*").toString(), java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(text).matches();
  }

  /** A plugin's line in the list: a star for a favourite, its type, its category and group. */
  public static String line(File f) {
    Entry e = entry(f);
    Type t = typeOf(f);
    StringBuilder sb = new StringBuilder(e.favourite ? "\u2605 " : "    ").append(name(f)).append("  [").append(t == null ? "?" : t.name).append(']');
    if (e.category.length() > 0) sb.append("  ").append(e.category);
    if (e.group.length() > 0) sb.append(" / ").append(e.group);
    if (!e.tags.isEmpty()) sb.append("  #").append(String.join(" #", e.tags));
    return sb.toString();
  }

  /** Info: type and what this app does with it, file, size and date, what is kept for it. */
  public static String info(File f, boolean onAndroid) {
    Type t = typeOf(f);
    Entry e = entry(f);
    java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
    StringBuilder b = new StringBuilder(name(f)).append(" (").append(t == null ? "unknown type" : t.name).append(f.isDirectory() ? ", a bundle folder" : "").append(")\n");
    b.append(f.getAbsolutePath()).append('\n');
    b.append(f.isDirectory() ? "Folder" : (f.length() / 1024) + " KB").append(", changed ").append(day.format(new java.util.Date(f.lastModified()))).append('\n');
    if (e.favourite) b.append("A favourite\n");
    if (e.category.length() > 0) b.append("Category: ").append(e.category).append('\n');
    if (e.group.length() > 0) b.append("Group: ").append(e.group).append('\n');
    if (!e.tags.isEmpty()) b.append("Tags: ").append(String.join(", ", e.tags)).append('\n');
    if (t != null) b.append('\n').append(t.usable(onAndroid) ? "" : "Not usable on " + (onAndroid ? "Android" : "this computer") + ". ").append(t.how);
    return b.toString();
  }

  // ------------------------------------------------------------ kits (SF2, SFZ)

  /** A kit's pads (SF2 or SFZ); the pads it has no drum for get Pulsekit's own sounds. */
  public static short[][] kitVoices(File f) throws java.io.IOException {
    Type t = typeOf(f);
    short[][] kit;
    if (t != null && t.name.equals(SF2)) kit = AudioIo.parseSf2(Files.readAllBytes(f.toPath()));
    else if (t != null && t.name.equals(SFZ)) kit = Sfz.load(f, null).voices;
    else throw new java.io.IOException(name(f) + " is not a kit (SF2, SFZ)");
    short[][] own = AudioIo.buildVoices(AudioIo.SR);
    for (int i = 0; i < own.length && i < kit.length; i++) if (kit[i] != null) own[i] = kit[i];
    return own;
  }

  /** Test: two bars of a beat (kick, snare, hats, a crash, a tom fill at the end) with the kit's pads, at 110 BPM. */
  public static short[] testBeat(short[][] voices) {
    int[][] cells = new int[Engine.NOTES.length][16];
    int kick = Engine.track("kick"), snare = Engine.track("snare"), chh = Engine.track("chh"), crash = Engine.track("crash");
    int[] toms = {Engine.track("htom"), Engine.track("mtom"), Engine.track("ltom")};
    for (int i : new int[] {0, 6, 8}) cells[kick][i] = 110;
    cells[snare][4] = 110;
    cells[snare][12] = 110;
    for (int i = 0; i < 12; i += 2) cells[chh][i] = 85;
    cells[crash][0] = 100;
    for (int i = 0; i < 3; i++) if (toms[i] >= 0) cells[toms[i]][13 + i] = 105;
    return AudioIo.mixCells(cells, 16, 110, 2, voices, AudioIo.SR);
  }

  // ------------------------------------------------------------ helpers

  private static boolean on(String filter) {
    return filter != null && filter.length() > 0 && !ANY.equals(filter);
  }

  private static boolean containsIgnoreCase(Set<String> all, String one) {
    for (String s : all) if (s.equalsIgnoreCase(one)) return true;
    return false;
  }

  /** Empty sorts after the rest. */
  private static String blankLast(String s) {
    return s.isEmpty() ? "\uffff" : s.toLowerCase(Locale.ROOT);
  }

  private static String clean(String s) {
    return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replace(',', ' ').trim();
  }

  private static void save() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Entry> me : entries.entrySet()) {
      Entry e = me.getValue();
      if (!e.favourite && e.category.isEmpty() && e.group.isEmpty() && e.tags.isEmpty() && e.order == 0) continue;
      sb.append(me.getKey()).append('\t').append(e.favourite ? "1" : "0").append('\t').append(e.category).append('\t').append(e.group).append('\t')
          .append(String.join(",", e.tags)).append('\t').append(e.order).append('\n');
    }
    write(LIBRARY, sb.toString());
  }

  private static void write(String name, String text) {
    if (dir == null) return;
    try {
      dir.mkdirs();
      Files.write(new File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept while Pulsekit runs
    }
  }

  private static List<String> lines(File f) {
    List<String> out = new ArrayList<String>();
    if (!f.isFile()) return out;
    try {
      for (String l : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) if (l.trim().length() > 0) out.add(l.replace("\r", ""));
    } catch (Exception ignored) {
      // none
    }
    return out;
  }
}
