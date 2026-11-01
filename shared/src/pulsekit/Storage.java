package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * General settings > Storage: what Pulsekit's folders take (the prompt library, settings and songs,
 * logs, the temporary files), Clear temporary files, and the temporary files older than some days
 * deleted when Pulsekit starts. Temporary: the files picked for programs and what the programs
 * wrote next to them (PyJav's input folder), the playlist cache, the work left by a stopped run;
 * what was kept went into Download (or the program files folder) already.
 */
public final class Storage {
  private Storage() {}

  public static final String TITLE = "Storage";
  public static final String CLEAR = "Clear temporary files";
  public static final String AUTO = "When Pulsekit starts, delete temporary files older than";
  public static final String NOTE = "Temporary: the files picked for programs, the files the programs wrote next to them (their results are "
      + "saved into Download as well), the playlist cache and what a stopped run left. A file picked again is copied in again.";
  public static final String ASK = "Delete all temporary files now? Results already saved into Download stay; a program's input picked "
      + "earlier has to be picked again.";
  /** The choices of days (0: never). */
  public static final int[] DAYS = {0, 3, 7, 14, 30};
  public static final int DEFAULT_DAYS = 14;

  public static String daysLabel(int days) {
    return days <= 0 ? "Never" : days + (days == 1 ? " day" : " days");
  }

  /** One of Pulsekit's places: a folder (its children starting with `prefix` only, when given; `skip` left out). */
  public static final class Place {
    public final String label;
    public final File dir;
    public final boolean temporary;
    final Set<String> skip = new HashSet<String>();
    String prefix;
    /** Files of the DB only (PromptVault.isDbFile), and its secondary DBs' folder. */
    boolean dbOnly;
    boolean noDb;

    public Place(String label, File dir, boolean temporary) {
      this.label = label;
      this.dir = dir;
      this.temporary = temporary;
    }

    public Place skip(String... names) {
      this.skip.addAll(Arrays.asList(names));
      return this;
    }

    public Place prefix(String p) {
      this.prefix = p;
      return this;
    }

    public Place dbOnly() {
      this.dbOnly = true;
      return this;
    }

    /** All but the DB's files. */
    public Place noDb() {
      this.noDb = true;
      return this;
    }

    boolean takes(File child) {
      String n = child.getName();
      if (this.skip.contains(n)) return false;
      if (this.prefix != null && !n.startsWith(this.prefix)) return false;
      boolean db = child.isDirectory() ? n.equals(DbSlots.FOLDER) : PromptVault.isDbFile(n) || n.equals(DbSlots.ACTIVE);
      if (this.dbOnly) return db;
      if (this.noDb) return !db;
      return true;
    }

    /** The bytes it takes. */
    public long bytes() {
      long total = 0;
      File[] kids = this.dir == null ? null : this.dir.listFiles();
      if (kids != null) for (File k : kids) if (this.takes(k)) total += Storage.bytes(k);
      return total;
    }
  }

  /** A file's bytes, or a folder's (all of it). */
  public static long bytes(File f) {
    if (f == null) return 0;
    if (!f.isDirectory()) return f.length();
    long total = 0;
    File[] kids = f.listFiles();
    if (kids != null) for (File k : kids) total += bytes(k);
    return total;
  }

  /** "Program inputs and outputs: 3.2 GB (temporary)" for each place, and the total last. */
  public static List<String> lines(List<Place> places) {
    List<String> out = new ArrayList<String>();
    long all = 0;
    for (Place p : places) {
      long b = p.bytes();
      all += b;
      out.add(p.label + ": " + Playlist.sizeText(b) + (p.temporary ? " (temporary)" : ""));
    }
    out.add("All: " + Playlist.sizeText(all));
    return out;
  }

  /**
   * The temporary places' files last changed before `cutoff` (ms; Long.MAX_VALUE: all) deleted, and
   * the folders they leave empty. {files deleted, bytes freed}.
   */
  public static long[] clear(List<Place> places, long cutoff) {
    long[] done = new long[2];
    for (Place p : places) {
      if (!p.temporary || p.dir == null) continue;
      File[] kids = p.dir.listFiles();
      if (kids != null) for (File k : kids) if (p.takes(k)) clearIn(k, cutoff, done);
    }
    return done;
  }

  private static void clearIn(File f, long cutoff, long[] done) {
    if (f.isDirectory()) {
      File[] kids = f.listFiles();
      if (kids != null) for (File k : kids) clearIn(k, cutoff, done);
      String[] left = f.list();
      if (left != null && left.length == 0) f.delete();
      return;
    }
    if (f.lastModified() >= cutoff) return;
    long size = f.length();
    if (f.delete()) {
      done[0]++;
      done[1] += size;
    }
  }

  /** Temporary files older than `days` deleted (0: none); what was done, or null when nothing was. */
  public static String clearOld(List<Place> places, int days) {
    if (days <= 0) return null;
    long[] done = clear(places, System.currentTimeMillis() - days * 86400000L);
    return done[0] == 0 ? null : said(done, " older than " + daysLabel(days));
  }

  /** "Deleted 12 temporary files (1.4 GB)". */
  public static String said(long[] done, String more) {
    return "Deleted " + done[0] + " temporary " + (done[0] == 1 ? "file" : "files") + (more == null ? "" : more) + " (" + Playlist.sizeText(done[1]) + ")";
  }
}
