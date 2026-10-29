package pulsekit;

import android.app.AlertDialog;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * What went wrong last time: a crash is written to crash-log.txt (the newest first, the last few
 * kept) before Pulsekit closes, and the next start shows it with Save. When Android closed
 * Pulsekit while a program ran (RunKeeper's running.txt is still there), the next start says so.
 */
public final class CrashLog {
  private CrashLog() {}

  static final String FILE = "crash-log.txt";
  /** The crash shown already (its first line), so it is shown once. */
  static final String SEEN = "crash-seen.txt";
  static final int KEEP = 20000;

  /** Writes any crash to crash-log.txt, then lets it close Pulsekit as before. */
  public static void install(final MainActivity app) {
    final File dir = app.getFilesDir();
    final Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
    if (before instanceof Writer) return;
    Thread.setDefaultUncaughtExceptionHandler(new Writer(dir, before));
  }

  static final class Writer implements Thread.UncaughtExceptionHandler {
    final File dir;
    final Thread.UncaughtExceptionHandler before;

    Writer(File dir, Thread.UncaughtExceptionHandler before) {
      this.dir = dir;
      this.before = before;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
      try {
        write(this.dir, thread, error);
      } catch (Throwable ignored) {
        // no log
      }
      try {
        // File > Logs too (Logging on).
        Logs.error("Pulsekit stopped (thread " + thread.getName() + ")", error);
      } catch (Throwable ignored) {
        // no log
      }
      if (this.before != null) this.before.uncaughtException(thread, error);
    }
  }

  static void write(File dir, Thread thread, Throwable error) throws Exception {
    StringWriter trace = new StringWriter();
    error.printStackTrace(new PrintWriter(trace));
    String when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date());
    File running = new File(dir, RunKeeper.MARK);
    String run = running.isFile() ? new String(java.nio.file.Files.readAllBytes(running.toPath()), StandardCharsets.UTF_8).split("\n")[0] : "";
    String entry = "Pulsekit stopped " + when + " (thread " + thread.getName() + (run.isEmpty() ? "" : ", while " + run + " ran") + ")\n"
        + "Android " + android.os.Build.VERSION.RELEASE + ", " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n" + trace + "\n";
    File f = new File(dir, FILE);
    String old = f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
    String all = entry + old;
    if (all.length() > KEEP) all = all.substring(0, KEEP);
    java.nio.file.Files.write(f.toPath(), all.getBytes(StandardCharsets.UTF_8));
  }

  /** The log, newest first; "" when there is none. */
  public static String text(File dir) {
    try {
      File f = new File(dir, FILE);
      return f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
    } catch (Exception ex) {
      return "";
    }
  }

  /** At start: the crash of last time (once), or the run Android closed Pulsekit during. Returns the dialog shown, or null. */
  public static AlertDialog showLast(final MainActivity app) {
    File dir = app.getFilesDir();
    final String log = text(dir);
    String first = log.isEmpty() ? "" : log.substring(0, Math.max(0, log.indexOf('\n')));
    File seen = new File(dir, SEEN);
    String shown = "";
    try {
      if (seen.isFile()) shown = new String(java.nio.file.Files.readAllBytes(seen.toPath()), StandardCharsets.UTF_8).trim();
    } catch (Exception ignored) {
      // shown again
    }
    // running.txt from a run of this same process (a second window) is not one Android ended.
    File running = new File(dir, RunKeeper.MARK);
    String run = "";
    if (!RunKeeper.markedHere) {
      try {
        if (running.isFile()) run = new String(java.nio.file.Files.readAllBytes(running.toPath()), StandardCharsets.UTF_8).split("\n")[0];
      } catch (Exception ignored) {
        // none
      }
      running.delete();
    }
    if (!first.isEmpty() && !first.equals(shown)) {
      try {
        java.nio.file.Files.write(seen.toPath(), first.getBytes(StandardCharsets.UTF_8));
      } catch (Exception ignored) {
        // shown again next time
      }
      String top = log.length() > 4000 ? log.substring(0, 4000) + "…" : log;
      return new AlertDialog.Builder(app).setTitle("Pulsekit stopped last time")
          .setMessage(top)
          .setPositiveButton("Save crash-log.txt", (d, w) -> SaveText.save(app, FILE, log))
          .setNegativeButton("Close", null).show();
    }
    if (!run.isEmpty()) {
      return new AlertDialog.Builder(app).setTitle("Pulsekit was closed")
          .setMessage("Android closed Pulsekit while " + run + " ran (no crash was written). A Sogni run goes on on Sogni: "
              + "File > Downloads has it, and Create again or the run's reconnect gets its result.")
          .setPositiveButton("OK", null).show();
    }
    return null;
  }
}
