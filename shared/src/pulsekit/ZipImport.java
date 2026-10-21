package pulsekit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * File > Import of a zip package: its password asked when it has one, then whether to extract it.
 * Extracted: into a folder, into the DB (Ref files at first, or Result files), or both. Not
 * extracted: the zip itself goes into the DB as a reference file when it is under 20 MB; a larger
 * one is linked into the DB (separate storage) after asking, or not imported.
 */
public final class ZipImport {
  private ZipImport() {}

  public static final String EXTRACT_TITLE = "Extract the zip?";
  public static final String[] WHERE = {"Into a folder", "Into the DB", "Into a folder and the DB"};
  public static final int FOLDER = 0;
  public static final int DB = 1;
  public static final int BOTH = 2;
  public static final String WHERE_TITLE = "Extract where?";
  /** Into the DB: which gallery (Ref files first). */
  public static final String[] GALLERY = {"Ref files", "Result files"};
  public static final String GALLERY_TITLE = "Into which files?";
  public static final String LINK = "Link into DB";
  public static final String ABORT = "Abort";

  public static boolean isZip(String name) {
    return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip");
  }

  /** "Pack.zip has 4 files, 12 MB. Extract its files? (No: the zip itself goes into the DB.)" */
  public static String extractQuestion(String name, List<ZipPack.Entry> entries) {
    return name + " has " + ZipPack.summary(entries) + ".\n\nExtract its files? No keeps the zip itself in the DB (Ref files).";
  }

  /** A zip of 20 MB or more kept as it is: linked into the DB, or not imported. */
  public static String tooBig(String name, long size) {
    return name + " is " + Math.max(1, size / (1024 * 1024)) + " MB: a file of " + (PromptVault.LINK_MIN / (1024 * 1024))
        + " MB or more does not fit into the DB itself. Link it into the DB (separate storage: kept as it is beside the library), or abort?";
  }

  /** 1 for Ref files, 3 for Result files (GALLERY's row). */
  public static int which(int galleryRow) {
    return galleryRow == 1 ? 3 : 1;
  }

  public static String galleryName(int which) {
    return which == 3 ? "Result files" : "Ref files";
  }

  /**
   * The zip's files into the library (`which`: 1 Ref files, 3 Result files), each by its own name; one
   * of 20 MB or more into separate storage. Each goes through a temp file in `tmpDir`. What was done.
   */
  public static String intoDb(PromptVault vault, File zip, List<ZipPack.Entry> entries, char[] password, int which, File tmpDir) throws IOException {
    int done = 0;
    int linked = 0;
    int failed = 0;
    File tmp = new File(tmpDir, "zip-entry.tmp");
    try {
      for (ZipPack.Entry e : ZipPack.files(entries)) {
        try {
          ZipPack.extract(zip, e, password, tmp);
          String name = e.fileName();
          if (tmp.length() >= PromptVault.LINK_MIN) {
            try (InputStream in = new FileInputStream(tmp)) {
              vault.addLinkedFile(name, in, "Imported", which);
            }
            linked++;
          } else {
            vault.addLibraryFile(name, Files.readAllBytes(tmp.toPath()), "Imported", which);
          }
          done++;
        } catch (ZipPack.WrongPassword ex) {
          throw ex;
        } catch (Exception ex) {
          failed++;
        }
      }
    } finally {
      tmp.delete();
    }
    return done + (done == 1 ? " file" : " files") + " into " + galleryName(which) + (linked > 0 ? " (" + linked + " in separate storage)" : "")
        + (failed > 0 ? "; " + failed + " could not be imported" : "");
  }

  /** The zip's files into `folder`, with its folders. What was done. */
  public static String intoFolder(File zip, List<ZipPack.Entry> entries, char[] password, File folder) throws IOException {
    int done = 0;
    int failed = 0;
    for (ZipPack.Entry e : ZipPack.files(entries)) {
      File to = new File(folder, ZipPack.safePath(e.name));
      // Never over a file that is there: "name (2).ext".
      String stem = to.getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
      String ext = to.getName().substring(stem.length());
      for (int n = 2; to.exists(); n++) to = new File(to.getParentFile(), stem + " (" + n + ")" + ext);
      try {
        ZipPack.extract(zip, e, password, to);
        done++;
      } catch (ZipPack.WrongPassword ex) {
        throw ex;
      } catch (Exception ex) {
        failed++;
        to.delete();
      }
    }
    return done + (done == 1 ? " file" : " files") + " into " + folder.getName() + (failed > 0 ? "; " + failed + " could not be extracted" : "");
  }

  /** The zip itself into the DB as a reference file (linked, when `link`). What was done. */
  public static String zipIntoDb(PromptVault vault, File zip, String name, boolean link) throws Exception {
    if (link) {
      try (InputStream in = new FileInputStream(zip)) {
        vault.addLinkedFile(name, in, "Imported", 1);
      }
      return name + " is in the DB as a reference file (in separate storage)";
    }
    vault.addLibraryFile(name, Files.readAllBytes(zip.toPath()), "Imported", 1);
    return name + " is in the DB as a reference file";
  }
}
