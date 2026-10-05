package pulsekit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The Sogni API key file chosen in File > Drum Midi Settings. Pulsekit keeps its own copy (only
 * the key line, readable by Pulsekit alone), SogniApi.findKey reads it, and PyJav passes it as
 * --key_file to programs that take one. The key itself is never shown, only its last four signs.
 */
public final class ApiKeys {
  private static File stored;

  private ApiKeys() {}

  /** Where the copy lives: the app's private folder (Android) or ~/.pulsekit (desktop). */
  public static void init(File dir) {
    stored = dir == null ? null : new File(dir, "sogni_credentials");
    SogniApi.keyFileSetting = path();
  }

  /** The copy's path when a key is set, else null. */
  public static String path() {
    return stored != null && SogniApi.keyIn(stored) != null ? stored.getAbsolutePath() : null;
  }

  /** Keeps the key from a chosen file. Returns the masked key, or throws when the file holds none. */
  public static String store(byte[] data) throws IOException {
    if (stored == null) throw new IOException("No place to keep the key");
    String key = SogniApi.keyInText(data == null ? "" : new String(data, StandardCharsets.UTF_8));
    if (key == null) throw new IOException("No Sogni API key in that file. It needs a line SOGNI_API_KEY=<your key>, or the key alone.");
    File dir = stored.getParentFile();
    if (dir != null && !dir.isDirectory()) dir.mkdirs();
    FileOutputStream out = new FileOutputStream(stored);
    try {
      out.write(("SOGNI_API_KEY=" + key + "\n").getBytes(StandardCharsets.UTF_8));
    } finally {
      out.close();
    }
    stored.setReadable(false, false);
    stored.setReadable(true, true);
    stored.setWritable(false, false);
    stored.setWritable(true, true);
    SogniApi.keyFileSetting = path();
    return mask(key);
  }

  public static void clear() {
    if (stored != null && stored.isFile()) stored.delete();
    SogniApi.keyFileSetting = null;
  }

  /** "Set (key ends 1a2b)" or "Not set". */
  public static String status() {
    String key = stored == null ? null : SogniApi.keyIn(stored);
    return key == null ? "Not set" : "Set (key ends " + mask(key) + ")";
  }

  static String mask(String key) {
    return key.length() <= 4 ? "****" : key.substring(key.length() - 4);
  }
}
