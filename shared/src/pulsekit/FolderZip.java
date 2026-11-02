package pulsekit;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * The Media browser's folder zips. Send folder contents to zip: the files in the folder shown (not
 * its folders) into "<folder>.zip" there, with a password Pulsekit makes (shown once, to copy; it
 * is not kept). Import files from zip: a zip picked, its password asked when it has one, then its
 * files into the folder shown, or into a folder named for the zip there (made, or added to when it
 * is there already).
 */
public final class FolderZip {
  private FolderZip() {}

  public static final String SEND = "Send folder contents to zip";
  public static final String IMPORT = "Import files from zip…";
  public static final String HERE = "Into this folder";
  public static final String WHERE_TITLE = "Import the files";
  public static final String KEEP = "Keep this password: Pulsekit does not keep it, and the zip does not open without it.";

  /** The choice of a new folder (added to when it is there already): "Into the folder beach (added to)". */
  public static String newFolder(String name, boolean there) {
    return "Into " + (there ? "the folder " : "a new folder ") + name + (there ? " (added to)" : "");
  }

  /** The zip's name for a folder: "<folder>.zip". */
  public static String zipFor(String folder) {
    String n = folder == null ? "" : folder.trim().replaceAll("[\\\\/:*?\"<>|]+", "_");
    return (n.isEmpty() ? "files" : n) + ".zip";
  }

  /** The folder a zip's files go into: its name without ".zip". */
  public static String folderFor(String zip) {
    String n = zip == null ? "" : zip.trim();
    int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
    if (slash >= 0) n = n.substring(slash + 1);
    n = n.replaceAll("(?i)\\.zip$", "").replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
    return n.isEmpty() ? "zip" : n;
  }

  /** Letters and digits that are not mistaken for each other (no 0/O, 1/l/I). */
  static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

  /** A password Pulsekit makes: 16 letters and digits in four groups of four ("Kq7m-…"), about 93 bits. */
  public static String password() {
    SecureRandom r = new SecureRandom();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 16; i++) {
      if (i > 0 && i % 4 == 0) sb.append('-');
      sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  /** What Send folder contents to zip said. */
  public static String sent(String zip, int files, String where) {
    return "Zipped " + files + (files == 1 ? " file" : " files") + " into " + zip + " in " + where + ", with a password";
  }

  /** The files of a folder on disk (not its folders, nor hidden ones, nor the zip being made) as zip sources. */
  public static List<ZipPack.Source> files(File dir, String leaveOut) {
    List<ZipPack.Source> out = new ArrayList<ZipPack.Source>();
    File[] kids = dir.listFiles();
    if (kids == null) return out;
    java.util.Arrays.sort(kids);
    for (File f : kids) {
      if (!f.isFile() || f.getName().startsWith(".") || f.getName().equals(leaveOut)) continue;
      out.add(new ZipPack.Source(f.getName(), f));
    }
    return out;
  }

  /**
   * A zip's files written into the folder `dir` on disk (its folders kept; a name taken gets " (1)").
   * {written, failed}.
   */
  public static int[] extractInto(File zip, List<ZipPack.Entry> entries, char[] password, File dir) throws Exception {
    int[] done = new int[2];
    for (ZipPack.Entry e : ZipPack.files(entries)) {
      String path = ZipPack.safePath(e.name);
      File out = new File(dir, path.replace('/', File.separatorChar));
      File parent = out.getParentFile();
      if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
        done[1]++;
        continue;
      }
      String name = out.getName();
      String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
      String ext = name.substring(stem.length());
      for (int n = 1; out.exists(); n++) out = new File(parent, stem + " (" + n + ")" + ext);
      try {
        ZipPack.extract(zip, e, password, out);
        done[0]++;
      } catch (ZipPack.WrongPassword ex) {
        throw ex;
      } catch (Exception ex) {
        done[1]++;
      }
    }
    return done;
  }

  /** What Import files from zip said. */
  public static String imported(String zip, int[] done, String where) {
    return "Imported " + done[0] + (done[0] == 1 ? " file" : " files") + " from " + zip + " into " + where + (done[1] > 0 ? " (" + done[1] + " could not be extracted)" : "");
  }
}
