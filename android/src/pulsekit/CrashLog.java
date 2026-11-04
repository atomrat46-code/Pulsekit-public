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
    // Why Android ended Pulsekit last time (Android 11 and later keep it), read once: a Java crash says it itself.
    final String why = lastExit(app);
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
    // Android's reason: into the note, crash-log.txt and Logs.
    if (why != null) {
      try {
        File f = new File(dir, FILE);
        String old = f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        String all = why + "\n\n" + old;
        java.nio.file.Files.write(f.toPath(), (all.length() > KEEP ? all.substring(0, KEEP) : all).getBytes(StandardCharsets.UTF_8));
        java.nio.file.Files.write(seen.toPath(), why.split("\n")[0].getBytes(StandardCharsets.UTF_8));
      } catch (Exception ignored) {
        // shown only
      }
      Logs.write(Logs.ERRORS, "Android closed Pulsekit", why);
    }
    if (!run.isEmpty() || why != null) {
      final String text = (run.isEmpty() ? "Android closed Pulsekit." : "Android closed Pulsekit while " + run + " ran (no crash was written).")
          + (why != null ? "\n\n" + why : "") + (run.isEmpty() ? "" : "\n\nA Sogni run goes on on Sogni: File > Downloads has it, and Create again or the run's reconnect gets its result.");
      return new AlertDialog.Builder(app).setTitle("Pulsekit was closed")
          .setMessage(text)
          .setPositiveButton("OK", null)
          .setNeutralButton("Save crash-log.txt", (d, w) -> SaveText.save(app, FILE, text(app.getFilesDir())))
          .show();
    }
    return null;
  }

  /** The last exit reason looked at (its time), so each is told once. */
  static final String EXIT_SEEN = "exit-seen.txt";

  /**
   * Why Android ended Pulsekit's last process, when it was not Pulsekit closing itself or the user
   * swiping it away: "Android's reason: low memory (...)", with what Android says of it, the memory
   * Pulsekit used and how it was shown then; null when there is nothing new (or before Android 11).
   */
  static String lastExit(android.content.Context ctx) {
    if (android.os.Build.VERSION.SDK_INT < 30) return null;
    try {
      android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE);
      java.util.List<android.app.ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(ctx.getPackageName(), 0, 1);
      if (list == null || list.isEmpty()) return null;
      android.app.ApplicationExitInfo e = list.get(0);
      File seen = new File(ctx.getFilesDir(), EXIT_SEEN);
      String at = String.valueOf(e.getTimestamp());
      if (seen.isFile() && new String(java.nio.file.Files.readAllBytes(seen.toPath()), StandardCharsets.UTF_8).trim().equals(at)) return null;
      java.nio.file.Files.write(seen.toPath(), at.getBytes(StandardCharsets.UTF_8));
      int r = e.getReason();
      if (r == android.app.ApplicationExitInfo.REASON_EXIT_SELF || r == android.app.ApplicationExitInfo.REASON_USER_REQUESTED
          || r == android.app.ApplicationExitInfo.REASON_USER_STOPPED || r == android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED) return null;
      String when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date(e.getTimestamp()));
      String text = exitText(r, e.getDescription(), e.getPss() / 1024, e.getRss() / 1024, e.getImportance(), when, e.getStatus());
      // Not responding: where the main thread was (Android's thread dump), and the hang the watchdog saw.
      if (r == android.app.ApplicationExitInfo.REASON_ANR) {
        String main = null;
        try {
          java.io.InputStream in = e.getTraceInputStream();
          if (in != null) {
            try {
              main = mainThread(new String(readAll(in), StandardCharsets.UTF_8));
            } finally {
              in.close();
            }
          }
        } catch (Throwable ignored) {
          // no dump kept
        }
        if (main != null) text += "\n\nThe main thread then (Android's dump):\n" + main;
        String hang = lastHang(ctx.getFilesDir());
        if (hang != null) text += "\n\n" + hang;
      }
      return text;
    } catch (Throwable ex) {
      return null;
    }
  }

  static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[16384];
    int n;
    while ((n = in.read(buf)) > 0 && out.size() < 4 << 20) out.write(buf, 0, n);
    return out.toByteArray();
  }

  /** The "main" thread's part of an ANR thread dump (its first 60 lines), or null. */
  static String mainThread(String dump) {
    int at = dump.indexOf("\n\"main\"");
    if (at < 0) at = dump.startsWith("\"main\"") ? -1 : -2;
    if (at == -2) return null;
    String rest = dump.substring(at + 1);
    StringBuilder out = new StringBuilder();
    int lines = 0;
    for (String line : rest.split("\n")) {
      if (lines > 0 && line.trim().length() == 0) break;
      out.append(line).append('\n');
      if (++lines >= 60) break;
    }
    return out.toString().trim();
  }

  // ------------------------------------------------------------ The watchdog

  /** The screen's (main thread's) hangs seen: the newest first, a few kept. */
  static final String HANGS = "hang-log.txt";
  static final long HUNG_MS = 2500;
  private static volatile long beat;
  private static Thread watchdog;

  /**
   * A watchdog: the main thread is asked every half second to say it is there; busy for 2.5 s (Android
   * says "not responding" at 5 s), where it is then is written to hang-log.txt and File > Logs (Errors),
   * again at 4.5 s if still busy, so a not responding has its cause.
   */
  static synchronized void watch(final MainActivity app) {
    if (watchdog != null || "robolectric".equals(android.os.Build.FINGERPRINT)) return;
    final File dir = app.getFilesDir();
    final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    final Runnable alive = () -> beat = android.os.SystemClock.uptimeMillis();
    beat = android.os.SystemClock.uptimeMillis();
    watchdog = new Thread(() -> {
      int said = 0;
      while (true) {
        try {
          main.post(alive);
          Thread.sleep(500);
          long late = android.os.SystemClock.uptimeMillis() - beat;
          if (late < HUNG_MS) {
            said = 0;
            continue;
          }
          if (said == 0 || (said == 1 && late >= 4500)) {
            said++;
            hung(dir, late, android.os.Looper.getMainLooper().getThread().getStackTrace());
          }
        } catch (InterruptedException stop) {
          return;
        } catch (Throwable ignored) {
          // goes on
        }
      }
    }, "pulsekit-watchdog");
    watchdog.setDaemon(true);
    watchdog.start();
  }

  /** A hang: when, how long so far, what ran, and the main thread's stack. */
  static void hung(File dir, long ms, StackTraceElement[] stack) {
    String when = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date());
    String run = "";
    try {
      File running = new File(dir, RunKeeper.MARK);
      if (running.isFile()) run = new String(java.nio.file.Files.readAllBytes(running.toPath()), StandardCharsets.UTF_8).split("\n")[0];
    } catch (Exception ignored) {
      // none
    }
    StringBuilder sb = new StringBuilder("The screen was busy " + (ms / 100) / 10.0 + " s (" + when + (run.isEmpty() ? "" : ", while " + run + " ran") + "); the main thread was at:\n");
    int n = 0;
    for (StackTraceElement e : stack) {
      sb.append("    at ").append(e).append('\n');
      if (++n >= 40) break;
    }
    String entry = sb.toString();
    try {
      File f = new File(dir, HANGS);
      String old = f.isFile() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
      String all = entry + "\n" + old;
      java.nio.file.Files.write(f.toPath(), (all.length() > KEEP ? all.substring(0, KEEP) : all).getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // Logs only
    }
    try {
      Logs.write(Logs.ERRORS, "Pulsekit's screen was busy", entry);
    } catch (Throwable ignored) {
      // none
    }
  }

  /** The newest hang written (for a not responding's note), or null. */
  static String lastHang(File dir) {
    try {
      File f = new File(dir, HANGS);
      if (!f.isFile()) return null;
      String all = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
      int end = all.indexOf("\n\n");
      String top = (end > 0 ? all.substring(0, end) : all).trim();
      return top.isEmpty() ? null : top;
    } catch (Exception ex) {
      return null;
    }
  }

  /** The words for an exit (ApplicationExitInfo's reason and importance numbers). */
  static String exitText(int reason, String description, long pssMb, long rssMb, int importance, String when, int status) {
    String[] names = {"unknown", "exit by itself", "killed by a signal", "low memory (Android needed memory for other apps)", "a crash (Java)",
        "a crash in native code", "not responding (ANR)", "an error starting", "a permission change", "using too much (CPU or memory)",
        "the user asked", "the user stopped it", "a dependency died", "other", "frozen", "package changed", "package updated"};
    String name = reason >= 0 && reason < names.length ? names[reason] : "reason " + reason;
    String shown = importance <= 100 ? "on screen" : importance <= 125 ? "running a foreground service (the run's notification)" : importance <= 230 ? "visible or perceptible"
        : importance <= 300 ? "a service" : "in the background, cached";
    return "Android's reason (" + when + "): " + name + (reason == 2 ? " " + status : "")
        + (description != null && description.length() > 0 ? "\nAndroid says: " + description : "")
        + "\nPulsekit used " + pssMb + " MB (" + rssMb + " MB resident), " + shown + ".";
  }
}
