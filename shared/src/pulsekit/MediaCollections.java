package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * Collections: named groups of things kept together, each a tree of containers (a container in a
 * container, as folders), each container holding links: a file on disk (its path or content://
 * address), a library file (Playlist.DB_PREFIX), a playlist (its name) or a file set (its key). A
 * linked file not found is shown missing until its folder is relocated; a library file gone is shown
 * deleted. Kept in the prompt library (PromptVault.collectionsText), so DB export and import carry it.
 */
public final class MediaCollections {
  private MediaCollections() {}

  public static final String TITLE = "Collections";
  public static final String ADD = "Add to Collection";
  public static final String KIND_FILE = "file", KIND_DB = "db", KIND_PLAYLIST = "playlist", KIND_FILESET = "fileset";

  /** What Collections.java says for Pulsekit to open: "Collections: <name>" (the collection), or "Collections:" (all). */
  public static final String OPEN_LINE = "Collections:";

  /** The collection Collections.java's run names ("" for all of them), or null when it said none. */
  public static String opened(String log) {
    if (log == null) return null;
    int at = log.lastIndexOf(OPEN_LINE);
    if (at < 0) return null;
    int end = log.indexOf('\n', at);
    String n = (end < 0 ? log.substring(at + OPEN_LINE.length()) : log.substring(at + OPEN_LINE.length(), end)).trim();
    return n.length() == 0 ? null : n;
  }

  /** A link in a container. */
  public static final class Entry {
    public String kind = KIND_FILE;
    /** Path, content:// address, library address, playlist name or file set key. */
    public String ref = "";
    /** As shown ("sunset.png", "Beach trip"). */
    public String name = "";
    public long size;
    public long added;

    /** "Picture", "Video"... for a file, else "Playlist", "File set", "DB file". */
    public String kindLabel() {
      if (KIND_PLAYLIST.equals(kind)) return "Playlist";
      if (KIND_FILESET.equals(kind)) return "File set";
      int k = MediaDir.kind(name);
      String t = k == MediaDir.PICTURE ? "Picture" : k == MediaDir.VIDEO ? "Video" : k == MediaDir.SOUND ? "Sound" : "File";
      return KIND_DB.equals(kind) ? t + " in DB" : t;
    }
  }

  /** A container: its subcontainers and its links. A collection's top is one too. */
  public static final class Container {
    public long id;
    public String name = "";
    public final List<Container> kids = new ArrayList<Container>();
    public final List<Entry> entries = new ArrayList<Entry>();

    /** Every link in it and under it. */
    public int total() {
      int n = entries.size();
      for (Container k : kids) n += k.total();
      return n;
    }
  }

  /** A collection: its name is its top container's. */
  public static final class Collection {
    public final Container top = new Container();

    public String name() {
      return top.name;
    }
  }

  /** What is there, asked of the app (a file's address, a library file, a playlist, a file set). */
  public interface Checker {
    boolean exists(Entry e);
  }

  /** "missing" (a file or a playlist or file set not found), "deleted" (a library file gone), or "". */
  public static String status(Entry e, Checker checker) {
    if (checker == null || checker.exists(e)) return "";
    return KIND_DB.equals(e.kind) ? "deleted" : "missing";
  }

  // ------------------------------------------------------------ the collections kept

  private static final List<Collection> ALL = new ArrayList<Collection>();
  private static long next = 1;

  /** The collections, read from the library's text. */
  public static synchronized void load(PromptVault vault) {
    ALL.clear();
    next = 1;
    if (vault == null) return;
    read(vault.collectionsText());
  }

  /** The collections written into the library. */
  public static synchronized void save(PromptVault vault) throws Exception {
    if (vault != null) vault.setCollectionsText(text());
  }

  public static synchronized List<Collection> all() {
    return new ArrayList<Collection>(ALL);
  }

  public static synchronized Collection named(String name) {
    for (Collection c : ALL) if (c.name().equalsIgnoreCase(name == null ? "" : name.trim())) return c;
    return null;
  }

  /** A new collection (a name taken gets " 2"). */
  public static synchronized Collection add(String name) {
    Collection c = new Collection();
    c.top.id = next++;
    c.top.name = free(name, ALL);
    ALL.add(c);
    return c;
  }

  public static synchronized boolean remove(Collection c) {
    return ALL.remove(c);
  }

  public static synchronized void rename(Collection c, String name) {
    List<Collection> others = new ArrayList<Collection>(ALL);
    others.remove(c);
    c.top.name = free(name, others);
  }

  /** A new container in `parent` (a name taken there gets " 2"). */
  public static synchronized Container addContainer(Container parent, String name) {
    Container k = new Container();
    k.id = next++;
    String n = clean(name);
    String base = n.length() == 0 ? "Container" : n;
    String tryName = base;
    for (int i = 2; hasKid(parent, tryName); i++) tryName = base + " " + i;
    k.name = tryName;
    parent.kids.add(k);
    return k;
  }

  private static boolean hasKid(Container parent, String name) {
    for (Container k : parent.kids) if (k.name.equalsIgnoreCase(name)) return true;
    return false;
  }

  /** The container `child` is in, under `top` (null when it is the top or not there). */
  public static Container parentOf(Container top, Container child) {
    for (Container k : top.kids) {
      if (k == child) return top;
      Container p = parentOf(k, child);
      if (p != null) return p;
    }
    return null;
  }

  public static synchronized boolean removeContainer(Container top, Container k) {
    Container p = parentOf(top, k);
    return p != null && p.kids.remove(k);
  }

  /** A link into `in`; false when the same is there already. */
  public static synchronized boolean addEntry(Container in, String kind, String ref, String name, long size) {
    for (Entry e : in.entries) if (e.kind.equals(kind) && e.ref.equals(ref)) return false;
    Entry e = new Entry();
    e.kind = kind;
    e.ref = ref == null ? "" : ref;
    e.name = name == null || name.length() == 0 ? shortName(e.ref) : name;
    e.size = size;
    e.added = System.currentTimeMillis();
    in.entries.add(e);
    return true;
  }

  /**
   * Relocate: the missing files under `top` (a file not found) pointed at the files of the same name
   * in the folder picked (`found`: name → its address there); how many.
   */
  public static synchronized int relocate(Container top, java.util.Map<String, String> found, Checker checker) {
    int n = 0;
    for (Entry e : top.entries) {
      if (!KIND_FILE.equals(e.kind) || checker != null && checker.exists(e)) continue;
      String there = found.get(e.name.toLowerCase(java.util.Locale.ROOT));
      if (there == null) there = found.get(shortName(e.ref).toLowerCase(java.util.Locale.ROOT));
      if (there == null) continue;
      e.ref = there;
      n++;
    }
    for (Container k : top.kids) n += relocate(k, found, checker);
    return n;
  }

  /** How many links under `top` are missing or deleted. */
  public static int lost(Container top, Checker checker) {
    int n = 0;
    for (Entry e : top.entries) if (status(e, checker).length() > 0) n++;
    for (Container k : top.kids) n += lost(k, checker);
    return n;
  }

  /** Every container of the collection, the top first, each after its parent ("Beach › Day 1"). */
  public static List<Container> containers(Collection c) {
    List<Container> out = new ArrayList<Container>();
    walk(c.top, out);
    return out;
  }

  private static void walk(Container k, List<Container> out) {
    out.add(k);
    for (Container kid : k.kids) walk(kid, out);
  }

  /** A file's folder: its address or path up to the last "/" ("" when it has none). */
  public static String folderOf(String ref) {
    int at = ref == null ? -1 : ref.lastIndexOf('/');
    return at < 0 ? "" : ref.substring(0, at);
  }

  static String join(String folder, String name) {
    return folder.endsWith("/") ? folder + name : folder + "/" + name;
  }

  static String shortName(String ref) {
    String r = ref == null ? "" : ref;
    if (Playlist.isDb(r)) {
      int slash = r.indexOf('/');
      return slash >= 0 ? r.substring(slash + 1) : r;
    }
    int at = r.lastIndexOf('/');
    String n = at >= 0 ? r.substring(at + 1) : r;
    try {
      return java.net.URLDecoder.decode(n.replace("+", "%2B"), "UTF-8");
    } catch (Exception ex) {
      return n;
    }
  }

  private static String clean(String name) {
    return name == null ? "" : name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
  }

  private static String free(String name, List<Collection> others) {
    String n = clean(name);
    String base = n.length() == 0 ? "Collection" : n;
    String tryName = base;
    for (int i = 2; ; i++) {
      boolean taken = false;
      for (Collection c : others) if (c.name().equalsIgnoreCase(tryName)) taken = true;
      if (!taken) return tryName;
      tryName = base + " " + i;
    }
  }

  /** "Holiday › Beach › Day 1": the containers from the top to `k`. */
  public static String path(Collection c, Container k) {
    List<String> names = new ArrayList<String>();
    for (Container at = k; at != null; at = parentOf(c.top, at)) {
      names.add(0, at.name);
      if (at == c.top) break;
    }
    if (names.isEmpty() || !names.get(0).equals(c.top.name)) names.add(0, c.top.name);
    return String.join(" \u203a ", names);
  }

  // ------------------------------------------------------------ text: a line a collection, container or link

  /**
   * C id name / K id parent name / E container kind size added name ref, tab between, a tab, a new
   * line or a backslash written \t \n \\.
   */
  static synchronized String text() {
    StringBuilder sb = new StringBuilder("PKCOLL1\n");
    for (Collection c : ALL) {
      sb.append("C\t").append(c.top.id).append('\t').append(esc(c.top.name)).append('\n');
      write(sb, c.top);
    }
    return sb.toString();
  }

  private static void write(StringBuilder sb, Container k) {
    for (Entry e : k.entries) {
      sb.append("E\t").append(k.id).append('\t').append(esc(e.kind)).append('\t').append(e.size).append('\t').append(e.added).append('\t')
          .append(esc(e.name)).append('\t').append(esc(e.ref)).append('\n');
    }
    for (Container kid : k.kids) {
      sb.append("K\t").append(kid.id).append('\t').append(k.id).append('\t').append(esc(kid.name)).append('\n');
      write(sb, kid);
    }
  }

  static synchronized void read(String text) {
    if (text == null || !text.startsWith("PKCOLL1")) return;
    java.util.Map<Long, Container> byId = new java.util.HashMap<Long, Container>();
    for (String line : text.split("\n")) {
      String[] p = line.split("\t", -1);
      try {
        if (p[0].equals("C") && p.length >= 3) {
          Collection c = new Collection();
          c.top.id = Long.parseLong(p[1]);
          c.top.name = unesc(p[2]);
          ALL.add(c);
          byId.put(c.top.id, c.top);
          next = Math.max(next, c.top.id + 1);
        } else if (p[0].equals("K") && p.length >= 4) {
          Container parent = byId.get(Long.parseLong(p[2]));
          if (parent == null) continue;
          Container k = new Container();
          k.id = Long.parseLong(p[1]);
          k.name = unesc(p[3]);
          parent.kids.add(k);
          byId.put(k.id, k);
          next = Math.max(next, k.id + 1);
        } else if (p[0].equals("E") && p.length >= 7) {
          Container in = byId.get(Long.parseLong(p[1]));
          if (in == null) continue;
          Entry e = new Entry();
          e.kind = unesc(p[2]);
          e.size = Long.parseLong(p[3]);
          e.added = Long.parseLong(p[4]);
          e.name = unesc(p[5]);
          e.ref = unesc(p[6]);
          in.entries.add(e);
        }
      } catch (RuntimeException ignored) {
        // a damaged line is left out
      }
    }
  }

  static String esc(String s) {
    return (s == null ? "" : s).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
  }

  static String unesc(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char n = s.charAt(++i);
        sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
      } else sb.append(c);
    }
    return sb.toString();
  }
}
