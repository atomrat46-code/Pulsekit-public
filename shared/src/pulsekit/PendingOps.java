package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Database operations still to finish: a Sogni run is listed from Run until it has failed, or has
 * succeeded and what it stores in the prompt library is stored. While the list is not empty no
 * other Sogni program runs (SogniChat may, it only reads the library before its run), and the Sogni
 * programs' Params do not open: "Please wait, 2 database operations are pending". The list is kept
 * unencrypted in pending-db.txt beside the library, one line a run: the program and its files.
 * An app that stopped (closed, crashed) left its runs unfinished: start(), once a process, clears them.
 */
public final class PendingOps {
  private static final List<String> LIST = new ArrayList<String>();
  private static File file;
  private static int next = 1;

  private PendingOps() {}

  /** At app start: the list in `dir`; runs a stopped app left in it are cleared. Their number. */
  public static synchronized int start(File dir) {
    File at = new File(dir, "pending-db.txt");
    // The app's window made again (the process still running): its runs are still going.
    if (file != null && file.equals(at)) return 0;
    file = at;
    int left = 0;
    try {
      if (file.isFile()) {
        for (String line : new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
          if (line.trim().length() > 0) left++;
        }
      }
    } catch (Exception ignored) {
      // Unreadable: nothing is pending now anyway.
    }
    LIST.clear();
    write();
    return left;
  }

  /** True for the programs the list is for: SogniVideo, SogniPedit... (not SogniChat when `running`). */
  public static boolean guarded(String program, boolean running) {
    String low = program == null ? "" : program.toLowerCase();
    if (!low.startsWith("sogni")) return false;
    return !(running && low.startsWith("sognichat"));
  }

  /** Lists a run: its program and the files it handles. Returns the entry, for done(). */
  public static synchronized String add(String program, List<String> files) {
    StringBuilder sb = new StringBuilder();
    sb.append(next++).append('\t').append(program == null ? "" : program);
    if (files != null) {
      for (String f : files) {
        if (f == null || f.trim().length() == 0) continue;
        sb.append('\t').append(f.replace('\t', ' ').replace('\n', ' '));
      }
    }
    String entry = sb.toString();
    LIST.add(entry);
    write();
    return entry;
  }

  /** The run's database operations are over (stored, or the run failed): it leaves the list. */
  public static synchronized void done(String entry) {
    if (entry == null) return;
    if (LIST.remove(entry)) write();
  }

  public static synchronized int count() {
    return LIST.size();
  }

  /** "Please wait, 2 database operations are pending" (1: "operation is"). */
  public static String waitText(int n) {
    return "Please wait, " + n + " database " + (n == 1 ? "operation is" : "operations are") + " pending";
  }

  /** The files a run names: its arguments that are paths of existing files. */
  public static List<String> files(List<String> argv) {
    List<String> out = new ArrayList<String>();
    if (argv == null) return out;
    for (String a : argv) {
      if (a == null || a.startsWith("--") || a.indexOf('/') < 0 && a.indexOf('\\') < 0) continue;
      if (new File(a).isFile() && !out.contains(a)) out.add(a);
    }
    return out;
  }

  private static void write() {
    if (file == null) return;
    try {
      StringBuilder sb = new StringBuilder();
      for (String e : LIST) sb.append(e).append('\n');
      if (sb.length() == 0) {
        file.delete();
        return;
      }
      java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // The list in memory still guards the runs.
    }
  }
}
