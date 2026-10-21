package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a program's input file in Pulsekit's temp folder came from: a prompt library file picked
 * with Params' Browse DB (input-origins.txt). The phone may empty the temp folder whenever it
 * likes, and Params kept the path: Preview said "The picture could not be read" and SogniPedit
 * "no picture". A file that is gone is written again from the library before Preview and Run.
 */
public final class InputOrigins {
  private InputOrigins() {}

  static final String FILE = "input-origins.txt";
  static final int MAX = 200;

  private static synchronized Map<String, String> read(File dir) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    File f = new File(dir, FILE);
    if (!f.isFile()) return out;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int tab = line.indexOf('\t');
        if (tab > 0) out.put(line.substring(0, tab), line.substring(tab + 1).trim());
      }
    } catch (Exception ignored) {
      // none known
    }
    return out;
  }

  /** Browse DB picked a library file into `path`. */
  public static synchronized void record(File dir, String path, long versionId, int which) {
    if (dir == null || path == null) return;
    Map<String, String> all = read(dir);
    all.remove(path);
    all.put(path, versionId + ":" + which);
    while (all.size() > MAX) all.remove(all.keySet().iterator().next());
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : all.entrySet()) sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
    try {
      Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // only this file is not known again
    }
  }

  /** The file at `path` written again from the library when it is gone; true when it is there now. */
  public static boolean restore(File dir, String path, PromptVault vault) {
    if (path == null || path.length() == 0) return false;
    File f = new File(path);
    if (f.isFile() && f.length() > 0) return true;
    String origin = read(dir).get(path);
    if (origin == null || vault == null) return false;
    try {
      String[] p = origin.split(":");
      byte[] bytes = vault.fileBytes(Long.parseLong(p[0]), Integer.parseInt(p[1]));
      if (bytes == null || bytes.length == 0) return false;
      if (f.getParentFile() != null) f.getParentFile().mkdirs();
      Files.write(f.toPath(), bytes);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Before a run: each argument that is a file in `tempDir` and is gone is written again from the
   * library. The files that could not be (none: all there); the run says so instead of failing.
   */
  public static List<String> missing(File dir, List<String> argv, File tempDir, PromptVault vault) {
    List<String> out = new ArrayList<String>();
    if (argv == null || tempDir == null) return out;
    String temp = tempDir.getAbsolutePath();
    Map<String, String> known = read(dir);
    for (String a : argv) {
      // Only files picked from the library: an output file the run is to make is not there yet either.
      if (a == null || !a.startsWith(temp + File.separator) || !known.containsKey(a)) continue;
      File f = new File(a);
      if (f.isFile() && f.length() > 0) continue;
      if (!restore(dir, a, vault)) out.add(f.getName());
    }
    return out;
  }

  /** Whether any argument picked from the library is gone (the library is opened only then). */
  public static boolean anyGone(File dir, List<String> argv, File tempDir) {
    if (argv == null || tempDir == null) return false;
    String temp = tempDir.getAbsolutePath();
    Map<String, String> known = null;
    for (String a : argv) {
      if (a == null || !a.startsWith(temp + File.separator)) continue;
      File f = new File(a);
      if (f.isFile() && f.length() > 0) continue;
      if (known == null) known = read(dir);
      if (known.containsKey(a)) return true;
    }
    return false;
  }

  /** "beach.png is not there any more: …" for a run that cannot go on. */
  public static String missingNote(List<String> names) {
    return String.join(", ", names) + (names.size() == 1 ? " is" : " are") + " not there any more (the phone emptied Pulsekit's temp folder): "
        + "pick " + (names.size() == 1 ? "it" : "them") + " again in Params (Browse DB or Choose file), then Run.";
  }
}
