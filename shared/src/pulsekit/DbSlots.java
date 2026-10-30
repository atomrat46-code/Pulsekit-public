package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * File > Database settings: the main DB (the app's own folder, as always) and secondary DBs, each
 * in a folder of its own under dbs/ (dbs/Pulse/prompts.vault and its files). The one in use is
 * named in db-active.txt; PromptVault opens it. Send db to zip writes the DB in use unencrypted into
 * a zip with a password (at least 4 characters); Import db zip reads one back (into Pulsekit's temp
 * folder first), as a new secondary DB or as the main DB, with what it links to that is not there
 * left out.
 */
public final class DbSlots {
  private DbSlots() {}

  public static final String MAIN = "Main";
  static final String FOLDER = "dbs";
  static final String ACTIVE = "db-active.txt";
  /** Inside a DB zip: its files under this folder, with db-info.txt. */
  public static final String ZIP_FOLDER = "pulsekit-db";
  static final String INFO = "db-info.txt";
  public static final int MIN_PASSWORD = 4;

  public static final String TITLE = "Database settings";
  public static final String SEND = "Send db to zip";
  public static final String IMPORT = "Import db zip";
  public static final String USE = "Use";
  public static final String DELETE = "Delete";
  public static final String SHORT_PASSWORD = "The password needs at least " + MIN_PASSWORD + " characters";
  public static final String PASSWORD_NOTE = "The zip gets a password (at least " + MIN_PASSWORD + " characters): the DB is in it unencrypted, "
      + "with its files, and the password encrypts the zip (AES-256). Type the password, or read it from a text file (its first line).";
  /** Import db zip's questions. */
  public static final String[] AS = {"As a new secondary DB", "As the new main DB (the main DB now kept as a secondary DB)"};
  public static final String AS_TITLE = "Import the DB";
  public static final String MAKE_MAIN = "The DB in use is empty. Make the imported DB the main DB? (No keeps it as a secondary DB.)";
  public static final String KEY_NOTE = "This DB is encrypted with another computer's key: choose its key file (prompts.key in that computer's ~/.pulsekit), "
      + "or type the key (64 hexadecimal characters).";

  /** The folder of the DB in use: `dir` for the main DB, else its folder under dbs/. */
  public static File home(File dir) {
    String name = active(dir);
    if (MAIN.equals(name)) return dir;
    File f = new File(new File(dir, FOLDER), name);
    return f.isDirectory() ? f : dir;
  }

  /** The DB in use: MAIN, or a secondary DB's name. */
  public static String active(File dir) {
    if (dir == null) return MAIN;
    File f = new File(dir, ACTIVE);
    if (!f.isFile()) return MAIN;
    try {
      String n = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
      return n.length() == 0 || !new File(new File(dir, FOLDER), n).isDirectory() ? MAIN : n;
    } catch (Exception ex) {
      return MAIN;
    }
  }

  /** The DBs: Main first, then the secondary ones by name. */
  public static List<String> names(File dir) {
    List<String> out = new ArrayList<String>();
    File[] kids = new File(dir, FOLDER).listFiles();
    if (kids != null) for (File k : kids) if (k.isDirectory() && PromptVault.fileAt(k).isFile()) out.add(k.getName());
    java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
    out.add(0, MAIN);
    return out;
  }

  public static File homeOf(File dir, String name) {
    return MAIN.equals(name) ? dir : new File(new File(dir, FOLDER), name);
  }

  /** Use: that DB from now on (the library in memory forgotten, so it is read from there). */
  public static void use(File dir, String name) throws Exception {
    File f = new File(dir, ACTIVE);
    if (MAIN.equals(name)) f.delete();
    else Files.write(f.toPath(), (name + "\n").getBytes(StandardCharsets.UTF_8));
    PromptVault.forget();
  }

  /** A secondary DB deleted (not the main one, not the one in use); false when it is not. */
  public static boolean delete(File dir, String name) {
    if (MAIN.equals(name) || name.equals(active(dir))) return false;
    return MediaDir.deleteAll(homeOf(dir, name));
  }

  /** A DB's size on disk: every file of it in its folder (both forms, when both are there). */
  public static long size(File home) {
    long total = 0;
    File[] kids = home == null ? null : home.listFiles();
    if (kids != null) for (File k : kids) if (k.isFile() && PromptVault.isDbFile(k.getName())) total += k.length();
    return total;
  }

  /** "Main 12 MB (in use, encrypted)" for each DB, and the total last. */
  public static List<String> sizeLines(File dir) {
    List<String> out = new ArrayList<String>();
    long all = 0;
    String active = active(dir);
    for (String n : names(dir)) {
      File home = homeOf(dir, n);
      long s = size(home);
      all += s;
      boolean enc = !new File(home, PromptVault.PLAIN_NAME).isFile();
      out.add(n + ": " + Playlist.sizeText(s) + (n.equals(active) ? " (in use" : " (") + (n.equals(active) ? ", " : "") + (enc ? "encrypted)" : "unencrypted)"));
    }
    out.add("All DBs: " + Playlist.sizeText(all));
    return out;
  }

  /** A name for a new secondary DB: cleaned, and "name (2)" when one of it is there. */
  public static String uniqueName(File dir, String base) {
    String b = base == null ? "" : base.replaceAll("(?i)\\.zip$", "").replaceAll("[\\t\\r\\n/\\\\:*?\"<>|]+", " ").trim();
    if (b.length() == 0 || b.equalsIgnoreCase(MAIN)) b = "Imported";
    String n = b;
    for (int i = 2; new File(new File(dir, FOLDER), n).exists(); i++) n = b + " (" + i + ")";
    return n;
  }

  // ------------------------------------------------------------ Send db to zip

  /** The DB in use, unencrypted, into `zip` with the password; what was done. Works in `tmp` (deleted after). */
  public static String export(File dir, File zip, char[] password, File tmp) throws Exception {
    if (password == null || password.length < MIN_PASSWORD) throw new IllegalArgumentException(SHORT_PASSWORD);
    File work = new File(tmp, "db-export");
    MediaDir.deleteAll(work);
    File inside = new File(work, ZIP_FOLDER);
    try {
      PromptVault.exportPlain(dir, inside);
      PromptVault copy = PromptVault.openAt(inside);
      String name = active(dir);
      String info = "name=" + name + "\nmade=" + System.currentTimeMillis() + "\ncontents=" + copy.summary() + "\n";
      Files.write(new File(inside, INFO).toPath(), info.getBytes(StandardCharsets.UTF_8));
      ZipPack.write(zip, ZipPack.folder(inside), password);
      return "Sent the DB " + name + " (" + copy.summary() + ", " + Playlist.sizeText(zip.length()) + ") to " + zip.getName() + ", with a password";
    } finally {
      MediaDir.deleteAll(work);
    }
  }

  /** A DB zip's name: "pulsekit-db-Main-20261020.zip". */
  public static String zipName(File dir) {
    return "pulsekit-db-" + active(dir).replaceAll("[^A-Za-z0-9._-]+", "_") + "-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new java.util.Date()) + ".zip";
  }

  /** A password read from a file: its first line (at most 4 KB read). */
  public static char[] passwordFrom(File f) throws Exception {
    byte[] b = Files.readAllBytes(f.toPath());
    String t = new String(b, 0, Math.min(b.length, 4096), StandardCharsets.UTF_8);
    int nl = t.indexOf('\n');
    return (nl >= 0 ? t.substring(0, nl) : t).replace("\r", "").trim().toCharArray();
  }

  // ------------------------------------------------------------ Import db zip

  /** A DB zip unpacked into Pulsekit's temp folder, ready to be placed. */
  public static final class Unpacked {
    public File folder;
    public String name = "";
    /** It is encrypted with another computer's key (asked for, then made unencrypted). */
    public boolean needsKey;
  }

  /** The DB files of the zip (wherever in it) into `tmp`/db-import; throws when it holds no Pulsekit DB. */
  public static Unpacked unpack(File zip, String zipName, char[] password, File tmp) throws Exception {
    List<ZipPack.Entry> entries = ZipPack.list(zip);
    File work = new File(tmp, "db-import");
    MediaDir.deleteAll(work);
    work.mkdirs();
    Unpacked u = new Unpacked();
    u.folder = work;
    u.name = zipName == null ? "" : zipName.replaceAll("(?i)\\.zip$", "").replaceFirst("^pulsekit-db-", "").replaceAll("-\\d{8}-\\d{4}$", "");
    for (ZipPack.Entry e : ZipPack.files(entries)) {
      String n = e.fileName();
      if (!PromptVault.isDbFile(n) && !INFO.equals(n)) continue;
      ZipPack.extract(zip, e, password, new File(work, n));
    }
    File info = new File(work, INFO);
    if (info.isFile()) {
      for (String line : new String(Files.readAllBytes(info.toPath()), StandardCharsets.UTF_8).split("\n")) {
        if (line.startsWith("name=") && line.length() > 5 && !MAIN.equals(line.substring(5).trim())) u.name = line.substring(5).trim();
      }
      info.delete();
    }
    boolean plain = new File(work, PromptVault.PLAIN_NAME).isFile();
    boolean enc = new File(work, PromptVault.ENCRYPTED_NAME).isFile();
    // A Pulsekit backup (File > Back up Pulsekit): its prompt library is the DB zip inside it.
    if (!plain && !enc) {
      for (ZipPack.Entry e : ZipPack.files(entries)) {
        if (!AppBackup.DB_ZIP.equals(e.fileName())) continue;
        File inner = new File(tmp, "backup-inner-" + AppBackup.DB_ZIP);
        try {
          ZipPack.extract(zip, e, password, inner);
          Unpacked in = unpack(inner, zipName, password, tmp);
          if (in.name.equals(u.name)) in.name = u.name.replaceFirst("^pulsekit-", "");
          return in;
        } finally {
          inner.delete();
        }
      }
    }
    if (!plain && !enc) {
      MediaDir.deleteAll(work);
      throw new IllegalArgumentException(zipName + " has no Pulsekit DB in it");
    }
    u.needsKey = !plain;
    return u;
  }

  /** A key typed (64 hexadecimal characters) or read from a key file (its 32 bytes, or the hex in it). */
  public static javax.crypto.SecretKey key(String typed, File keyFile) throws Exception {
    byte[] raw = null;
    if (keyFile != null) {
      byte[] b = Files.readAllBytes(keyFile.toPath());
      if (b.length == 32) raw = b;
      else typed = new String(b, StandardCharsets.UTF_8);
    }
    if (raw == null) {
      String h = typed == null ? "" : typed.replaceAll("\\s+", "");
      if (!h.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("A key is 32 bytes: a key file, or 64 hexadecimal characters");
      raw = new byte[32];
      for (int i = 0; i < 32; i++) raw[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
    }
    return new javax.crypto.spec.SecretKeySpec(raw, "AES");
  }

  /** The unpacked DB made unencrypted with the key it was encrypted with. */
  public static void unlock(Unpacked u, javax.crypto.SecretKey key) throws Exception {
    PromptVault.toPlain(u.folder, key);
    u.needsKey = false;
  }

  /** What it links to that is not there left out (saved); how many. */
  public static int prune(Unpacked u) throws Exception {
    return PromptVault.openAt(u.folder).prune();
  }

  /** Whether the DB in use has nothing in it yet. */
  public static boolean activeEmpty(File dir) {
    try {
      return PromptVault.open(dir).isEmpty();
    } catch (Exception ex) {
      return !PromptVault.fileIn(dir).isFile();
    }
  }

  /** The unpacked DB as a new secondary DB `name` (unique); its name. With `encrypt`, encrypted with this app's key. */
  public static String addSecondary(File dir, Unpacked u, String name, boolean encrypt) throws Exception {
    String n = uniqueName(dir, name);
    File home = new File(new File(dir, FOLDER), n);
    move(u.folder, home);
    if (encrypt) encryptAt(dir, n);
    return n;
  }

  /**
   * The unpacked DB as the main DB: the main DB's files moved first into a secondary DB of their
   * own ("Main before 2026-10-20", unless it was empty), then the imported files into the app's
   * folder, and Main in use. With `encrypt`, encrypted with this app's key. The old main DB's name,
   * or "" when it was empty and left out.
   */
  public static String makeMain(File dir, Unpacked u, boolean encrypt) throws Exception {
    PromptVault.forget();
    String kept = "";
    boolean empty;
    try {
      empty = PromptVault.openAt(dir).isEmpty();
    } catch (Exception ex) {
      empty = false;
    }
    File[] kids = dir.listFiles();
    List<File> mine = new ArrayList<File>();
    if (kids != null) for (File k : kids) if (k.isFile() && PromptVault.isDbFile(k.getName())) mine.add(k);
    if (!mine.isEmpty() && !empty) {
      kept = uniqueName(dir, "Main before " + new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date()));
      File home = new File(new File(dir, FOLDER), kept);
      home.mkdirs();
      for (File k : mine) moveFile(k, new File(home, k.getName()));
    } else {
      for (File k : mine) k.delete();
    }
    File[] in = u.folder.listFiles();
    if (in != null) for (File k : in) moveFile(k, new File(dir, k.getName()));
    MediaDir.deleteAll(u.folder);
    use(dir, MAIN);
    if (encrypt) PromptVault.setEncrypted(dir, true);
    PromptVault.forget();
    return kept;
  }

  /** A DB encrypted with this app's key (it is made the one in use for that, then the one in use before again). */
  static void encryptAt(File dir, String name) throws Exception {
    String was = active(dir);
    use(dir, name);
    try {
      PromptVault.setEncrypted(dir, true);
    } finally {
      use(dir, was);
    }
  }

  private static void move(File from, File to) throws Exception {
    to.mkdirs();
    File[] kids = from.listFiles();
    if (kids != null) for (File k : kids) moveFile(k, new File(to, k.getName()));
    MediaDir.deleteAll(from);
  }

  private static void moveFile(File from, File to) throws Exception {
    if (to.exists()) to.delete();
    if (!from.renameTo(to)) {
      Files.copy(from.toPath(), to.toPath());
      from.delete();
    }
  }
}
