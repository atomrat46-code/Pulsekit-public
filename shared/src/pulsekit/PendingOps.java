package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Database operations still to finish: a Sogni run is listed from Run until it has failed, or has
 * succeeded and what it stores in the prompt library is stored. While the list is not empty no
 * other Sogni program runs without asking first, when one of them stores in the library (--saveprompt,
 * Save this run into DB, --addtodb): "Are you sure you want to run a new session while the current
 * task is incomplete?". SogniChat runs (it only reads the library before its run), and Params always
 * open. A run that stores nothing does not hold the next one up. The list is kept
 * unencrypted in pending-db.txt beside the library, one line a run: the program and its files.
 * An app that stopped (closed, crashed) left its runs unfinished: start(), once a process, clears them.
 */
public final class PendingOps {
  private static final List<String> LIST = new ArrayList<String>();
  /** The entries whose runs store in the prompt library. */
  private static final java.util.Set<String> STORING = new java.util.HashSet<String>();
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
    STORING.clear();
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

  /** Lists a run that stores in the prompt library when `storing` (its arguments say so: storesInDb). */
  public static synchronized String add(String program, List<String> files, boolean storing) {
    String entry = add(program, files);
    if (storing) STORING.add(entry);
    return entry;
  }

  /** The run's result is being stored in the prompt library now (whatever its arguments said). */
  public static synchronized void storing(String entry) {
    if (entry != null && LIST.contains(entry)) STORING.add(entry);
  }

  /** The run's database operations are over (stored, or the run failed): it leaves the list. */
  public static synchronized void done(String entry) {
    if (entry == null) return;
    STORING.remove(entry);
    if (LIST.remove(entry)) write();
  }

  public static synchronized int count() {
    return LIST.size();
  }

  /** How many listed runs store in the prompt library. */
  public static synchronized int storingCount() {
    return STORING.size();
  }

  /** True when the run's arguments store in the prompt library: --saveprompt, --addtodb, Save this run into DB. */
  public static boolean storesInDb(List<String> argv) {
    if (argv == null) return false;
    for (String a : argv) {
      if ("--saveprompt".equals(a) || "--addtodb".equals(a) || PromptKeep.SAVE_RUN.equals(a)) return true;
    }
    return false;
  }

  /** True when Run must ask first: a Sogni program (not SogniChat) while a listed run stores in the library. */
  public static boolean mustAsk(String program) {
    return guarded(program, true) && storingCount() > 0;
  }

  /** The question Run asks then. */
  public static String askText(int n) {
    return "Are you sure you want to run a new session while the current task is incomplete?\n("
        + n + " database " + (n == 1 ? "operation is" : "operations are") + " pending)";
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
