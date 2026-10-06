package pulsekit;

import java.io.File;

/**
 * Export screen: "Export supported media files to DB also". When on, a MIDI, WAV or MP3 the
 * Export screen saves (Pattern, Fillern, Fill, WAV, MP3, Song MIDI, Song WAV, Song MP3) also
 * goes into the prompt library on its own, as a reference file under the name it was saved as:
 * it shows in Ref files and in Browse DB. Other exports (PRJ, SF2, JAR...) are left out. Up to
 * the library's 16 MB.
 */
public final class ExportDb {
  /** The setting: kept by each app (preferences on the phone, a file in ~/.pulsekit on the desktop). */
  public static volatile boolean on;

  private static final int MAX_BYTES = 16 * 1024 * 1024;

  private ExportDb() {}

  /** A MIDI, WAV or MP3 by its name: the kinds that go into the library. */
  public static boolean supported(String name) {
    String low = name == null ? "" : name.toLowerCase(java.util.Locale.US);
    return low.endsWith(".mid") || low.endsWith(".midi") || low.endsWith(".wav") || low.endsWith(".mp3");
  }

  /**
   * After an export: stores the file in the library kept in `dir` when the setting is on and it
   * is a supported kind. Returns a line for the status (empty when nothing was to be stored).
   */
  public static String store(File dir, String name, byte[] bytes) {
    if (!on || !supported(name) || bytes == null || bytes.length == 0) return "";
    if (bytes.length > MAX_BYTES) return name + " is over the library's 16 MB, so it is not in the DB";
    try {
      PromptVault.open(dir).addLibraryFile(name, bytes, "Exported", 1);
      return "also in the DB as a reference file";
    } catch (Exception ex) {
      return "not in the DB" + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
    }
  }
}
