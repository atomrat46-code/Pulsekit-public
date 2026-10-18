package pulsekit;

import java.io.File;

/**
 * Import screen: "Import to DB also". When on, a file picked with Choose file is imported as
 * before and also goes into the prompt library on its own, as a reference file under its name
 * (it shows in Ref files and in Browse DB). A file brought in with the Import screen's Browse DB
 * is in the library already and is not stored again. Up to the library's 128 MB.
 */
public final class ImportDb {
  /** The setting: kept by each app (preferences on the phone, a file in ~/.pulsekit on the desktop). */
  public static volatile boolean on;

  /**
   * Import screen: "Open in MIDI editor". When on, a MIDI imported from the Import screen keeps its
   * MIDI with its file set (source.mid) and opens in the MIDI editor; Back or Save there goes on to
   * the Pattern view of its file set. Kept as `on` is.
   */
  public static volatile boolean midiEditor;

  private static final int MAX_BYTES = 128 * 1024 * 1024;

  private ImportDb() {}

  /**
   * After an import from Choose file: stores the file in the library kept in `dir` when the
   * setting is on. Returns a line for the status (empty when it is off).
   */
  public static String store(File dir, String name, byte[] bytes) {
    if (!on || bytes == null || bytes.length == 0) return "";
    String shown = name == null || name.trim().isEmpty() ? "file" : name.trim();
    if (bytes.length > MAX_BYTES) return shown + " is over the library's 128 MB, so it is not in the DB";
    try {
      PromptVault.open(dir).addLibraryFile(shown, bytes, "Imported", 1);
      return shown + " is also in the DB as a reference file";
    } catch (Exception ex) {
      return shown + " is not in the DB" + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
  }
}
