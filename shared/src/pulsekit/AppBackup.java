package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * File > Back up Pulsekit / Restore from backup: what Pulsekit keeps in its own folder (lost when
 * the app is uninstalled), in parts that can be picked: settings and lists, patterns, file sets and
 * songs, playlists, the Sogni run history, the prompt library (the DB in use, unencrypted inside the
 * zip, the zip with a password) and the logs. Keys never go in: the Sogni API key, the DB's key
 * (on Android held by the phone itself, gone with the app), and nothing temporary.
 */
public final class AppBackup {
  private AppBackup() {}

  public static final String BACKUP = "Back up Pulsekit";
  public static final String RESTORE = "Restore from backup";

  public static final String SETTINGS = "settings";
  public static final String SETS = "sets";
  public static final String PLAYLISTS = "playlists";
  public static final String SOGNI = "sogni";
  public static final String DB = "db";
  public static final String LOGS = "logs";
  /** The parts in their order, and what each holds. */
  public static final String[] PARTS = {SETTINGS, SETS, PLAYLISTS, SOGNI, DB, LOGS};
  public static final String[] LABELS = {
    "Settings, favourites, groups, tags and recent lists",
    "Patterns, file sets, songs and the Composer",
    "Playlists",
    "Sogni run history (workflows and job ids; no API key)",
    "Prompt library (the DB in use, unencrypted inside the zip)",
    "Logs",
  };
  /** Ticked when the dialog opens. */
  public static final boolean[] ON = {true, true, true, true, true, false};

  public static final String NOTE = "Pulsekit's own folder is deleted when the app is uninstalled: this zip keeps what you tick, to restore "
      + "after a new install (or on another phone). The zip gets a password when the prompt library is in it.";
  public static final String RESTORE_NOTE = "What you tick replaces what Pulsekit has now. Pulsekit closes when it is done: open it again.";

  static final String MANIFEST = "pulsekit-backup.txt";
  static final String DB_ZIP = "pulsekit-db.zip";
  static final String PREFS = "prefs/";

  public static String label(String part) {
    for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(part)) return LABELS[i];
    return part;
  }

  /** The backup's name: "pulsekit-backup-20261030-1500.zip". */
  public static String zipName() {
    return "pulsekit-backup-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new java.util.Date()) + ".zip";
  }

  /**
   * The part a file of Pulsekit's folder belongs to (`rel` its path in it, with /), or null for one
   * never backed up: the DB files themselves (the DB goes in as an export), keys, temporary files.
   */
  public static String partOf(String rel) {
    String r = rel.replace('\\', '/');
    String name = r.substring(r.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    String low = r.toLowerCase(Locale.ROOT);
    if (low.startsWith(DbSlots.FOLDER + "/") || PromptVault.isDbFile(name) || name.equals(DbSlots.ACTIVE)) return null;
    if (name.endsWith(".key") || name.contains("credentials") || name.equals("sealed.salt") || name.endsWith(".tmp")) return null;
    if (low.startsWith("cache/") || low.startsWith("tmp/") || low.contains("/cache/")) return null;
    if (low.startsWith("logs/") || name.equals("crash-log.txt")) return LOGS;
    if (low.startsWith(Playlist.FOLDER + "/")) return PLAYLISTS;
    if (low.startsWith("sogni/") || name.startsWith("sogni")) return SOGNI;
    if (name.equals("learned.json") || name.equals(Composer.FILE) || name.equals("kit.sf2") || low.startsWith("fset") || name.startsWith("fset")) return SETS;
    return SETTINGS;
  }

  /**
   * Writes the backup: the files of `root` (Pulsekit's folder) and `prefs` (Android's settings
   * folder, or null) in the parts picked, and the DB as an export, into `zip`. The password (null:
   * none) is needed when the DB is in. What was done.
   */
  public static String backup(File root, File prefs, Set<String> parts, char[] password, File zip, File tmp, String from) throws Exception {
    if (parts.contains(DB) && (password == null || password.length < DbSlots.MIN_PASSWORD)) throw new IllegalArgumentException(DbSlots.SHORT_PASSWORD);
    List<ZipPack.Source> sources = new ArrayList<ZipPack.Source>();
    int[] counts = new int[PARTS.length];
    collect(root, root, parts, sources, counts, "");
    if (prefs != null && prefs.isDirectory() && parts.contains(SETTINGS)) {
      File[] list = prefs.listFiles();
      if (list != null) {
        for (File f : list) {
          if (!f.isFile()) continue;
          sources.add(new ZipPack.Source(PREFS + f.getName(), f));
          counts[0]++;
        }
      }
    }
    File dbZip = new File(tmp, "backup-" + DB_ZIP);
    try {
      if (parts.contains(DB)) {
        DbSlots.export(root, dbZip, password, tmp);
        sources.add(new ZipPack.Source(DB_ZIP, Files.readAllBytes(dbZip.toPath())));
        counts[4]++;
      }
      StringBuilder m = new StringBuilder("made=" + System.currentTimeMillis() + "\nfrom=" + (from == null ? "" : from) + "\nparts=");
      List<String> inside = new ArrayList<String>();
      for (int i = 0; i < PARTS.length; i++) if (parts.contains(PARTS[i]) && counts[i] > 0) inside.add(PARTS[i]);
      m.append(String.join(",", inside)).append('\n');
      sources.add(new ZipPack.Source(MANIFEST, m.toString().getBytes(StandardCharsets.UTF_8)));
      ZipPack.write(zip, sources, password);
    } finally {
      dbZip.delete();
    }
    StringBuilder said = new StringBuilder("Backed up ");
    List<String> what = new ArrayList<String>();
    for (int i = 0; i < PARTS.length; i++) if (parts.contains(PARTS[i]) && counts[i] > 0) what.add(PARTS[i].equals(DB) ? "the prompt library" : counts[i] + " " + shortName(PARTS[i]));
    said.append(what.isEmpty() ? "nothing" : String.join(", ", what)).append(" (").append(Playlist.sizeText(zip.length())).append(")")
        .append(password != null ? ", with a password" : "");
    return said.toString();
  }

  static String shortName(String part) {
    if (SETTINGS.equals(part)) return "settings files";
    if (SETS.equals(part)) return "pattern and file set files";
    if (PLAYLISTS.equals(part)) return "playlist files";
    if (SOGNI.equals(part)) return "Sogni history files";
    return "log files";
  }

  static void collect(File root, File dir, Set<String> parts, List<ZipPack.Source> out, int[] counts, String rel) {
    File[] list = dir.listFiles();
    if (list == null) return;
    java.util.Arrays.sort(list);
    for (File f : list) {
      String r = rel + f.getName();
      if (f.isDirectory()) {
        collect(root, f, parts, out, counts, r + "/");
        continue;
      }
      String part = partOf(r);
      if (part == null || !parts.contains(part)) continue;
      out.add(new ZipPack.Source("files/" + r, f));
      for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(part)) counts[i]++;
    }
  }

  /** A backup's parts (from its list of files, readable without the password); empty when it is not a Pulsekit backup. */
  public static Set<String> partsIn(File zip) throws Exception {
    Set<String> out = new LinkedHashSet<String>();
    boolean ours = false;
    for (ZipPack.Entry e : ZipPack.files(ZipPack.list(zip))) {
      String n = e.name;
      if (n.equals(MANIFEST)) ours = true;
      else if (n.equals(DB_ZIP)) out.add(DB);
      else if (n.startsWith(PREFS)) out.add(SETTINGS);
      else if (n.startsWith("files/")) {
        String p = partOf(n.substring(6));
        if (p != null) out.add(p);
      }
    }
    if (!ours) return new LinkedHashSet<String>();
    Set<String> ordered = new LinkedHashSet<String>();
    for (String p : PARTS) if (out.contains(p)) ordered.add(p);
    return ordered;
  }

  /** Whether the backup needs its password (the DB is in it, or it was written with one). */
  public static boolean needsPassword(File zip) throws Exception {
    return ZipPack.encrypted(ZipPack.list(zip));
  }

  /**
   * Restores the parts picked from `zip` into `root` and `prefs` (their files replace the ones there;
   * the DB becomes the main DB, the one before kept as a secondary DB). What was done.
   */
  public static String restore(File zip, char[] password, Set<String> parts, File root, File prefs, File tmp) throws Exception {
    List<ZipPack.Entry> entries = ZipPack.files(ZipPack.list(zip));
    if (ZipPack.encrypted(entries) && !ZipPack.passwordOk(zip, entries, password)) throw new ZipPack.WrongPassword();
    int files = 0;
    boolean db = false;
    for (ZipPack.Entry e : entries) {
      String n = e.name;
      File to = null;
      if (n.startsWith("files/")) {
        String rel = n.substring(6);
        String p = partOf(rel);
        if (p == null || !parts.contains(p) || rel.contains("..")) continue;
        to = new File(root, rel);
      } else if (n.startsWith(PREFS) && prefs != null && parts.contains(SETTINGS)) {
        String rel = n.substring(PREFS.length());
        if (rel.contains("/") || rel.contains("..")) continue;
        to = new File(prefs, rel);
      } else if (n.equals(DB_ZIP) && parts.contains(DB)) {
        File inner = new File(tmp, "restore-" + DB_ZIP);
        try {
          ZipPack.extract(zip, e, password, inner);
          DbSlots.Unpacked u = DbSlots.unpack(inner, "pulsekit-db-Main", password, tmp);
          DbSlots.prune(u);
          DbSlots.makeMain(root, u, PromptVault.encrypted(root));
          db = true;
        } finally {
          inner.delete();
        }
        continue;
      }
      if (to == null) continue;
      File parent = to.getParentFile();
      if (parent != null && !parent.isDirectory()) parent.mkdirs();
      ZipPack.extract(zip, e, password, to);
      files++;
    }
    return "Restored " + files + " files" + (db ? " and the prompt library" : "");
  }
}
