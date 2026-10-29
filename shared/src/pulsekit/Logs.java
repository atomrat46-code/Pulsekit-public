package pulsekit;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * File > Logs: one file per entry in the logs folder (~/.pulsekit/logs on the desktop, the app's
 * files/logs on Android), written only while General settings > Logging on is ticked. Its types:
 * the program runs (arguments, exit code, output), errors (what closed Pulsekit or went wrong),
 * the sound devices (what File > Sound devices found and did) and Pulsekit's starts (version,
 * system, Java). The newest are kept, KEEP of each type.
 */
public final class Logs {
  private Logs() {}

  public static final String RUNS = "Program runs";
  public static final String ERRORS = "Errors";
  public static final String SOUND = "Sound devices";
  public static final String STARTS = "Pulsekit starts";
  /** SFZ and SF2 kits loaded onto the pads: which sample each pad took. */
  public static final String KITS = "Kits";
  public static final String[] TYPES = {RUNS, ERRORS, SOUND, KITS, STARTS};
  /** The type list's first choice: the entries of every type. */
  public static final String ALL = "All logs";
  public static final String SETTING = "Logging on (File > Logs)";
  public static final String SETTING_NOTE = "Ticked, Pulsekit writes logs (program runs, errors, sound devices, kits, its starts) that File > Logs "
      + "shows, exports and copies; unticked, no log files are written.";
  public static final String EMPTY = "No logs yet. Tick General settings > Logging on, then run what you want logged.";
  static final int KEEP = 200;
  /** A run's output longer than this is cut in the middle. */
  static final int MAX_CHARS = 400000;
  static final String EXT = ".log";

  private static volatile File dir;
  private static int count;

  /** Where the logs go (set when Pulsekit starts). */
  public static void setDir(File d) {
    dir = d;
  }

  public static File dir() {
    File d = dir;
    return d != null ? d : new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "logs");
  }

  public static boolean on() {
    return AppSettings.logging();
  }

  /** One entry of the logs folder. */
  public static final class Entry {
    public final File file;
    public final String type;
    public final String title;
    public final long when;

    Entry(File file, String type, String title, long when) {
      this.file = file;
      this.type = type;
      this.title = title;
      this.when = when;
    }

    /** The list's line: time, type and title. */
    public String label() {
      return time(this.when) + "  " + this.type + ": " + this.title;
    }
  }

  /** Writes an entry when logging is on; its file, or null (off, or it could not be written). */
  public static File write(String type, String title, String text) {
    if (!on()) return null;
    return put(type, title, text);
  }

  static synchronized File put(String type, String title, String text) {
    try {
      File d = dir();
      d.mkdirs();
      long now = System.currentTimeMillis();
      String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date(now));
      File f = new File(d, stamp + "-" + String.format(Locale.ROOT, "%03d", count++ % 1000) + "_" + slug(type) + EXT);
      String body = "Type: " + type + "\nTitle: " + oneLine(title) + "\nTime: " + time(now) + "\n\n" + cut(text == null ? "" : text) + "\n";
      Files.write(f.toPath(), body.getBytes(StandardCharsets.UTF_8));
      prune(type);
      return f;
    } catch (Exception ex) {
      return null;
    }
  }

  /** The entries of `type` (ALL or null: every type), the newest first. */
  public static List<Entry> entries(String type) {
    List<Entry> out = new ArrayList<Entry>();
    File[] files = dir().listFiles();
    if (files == null) return out;
    for (File f : files) {
      if (!f.isFile() || !f.getName().endsWith(EXT)) continue;
      Entry e = read(f);
      if (e == null) continue;
      if (type == null || ALL.equals(type) || type.equals(e.type)) out.add(e);
    }
    // The names start with the time (to the millisecond), so the newest come first by name.
    Collections.sort(out, (a, b) -> b.file.getName().compareTo(a.file.getName()));
    return out;
  }

  static Entry read(File f) {
    String type = "", title = f.getName();
    try (java.io.BufferedReader in = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
      for (int i = 0; i < 3; i++) {
        String line = in.readLine();
        if (line == null) break;
        if (line.startsWith("Type: ")) type = line.substring(6);
        else if (line.startsWith("Title: ")) title = line.substring(7);
      }
    } catch (Exception ex) {
      return null;
    }
    long when = f.lastModified();
    try {
      when = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).parse(f.getName().substring(0, 19)).getTime();
    } catch (Exception ex) {
      // the file's time
    }
    return type.isEmpty() ? null : new Entry(f, type, title, when);
  }

  /** The entry's whole text. */
  public static String text(Entry e) {
    try {
      return new String(Files.readAllBytes(e.file.toPath()), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return "(cannot read " + e.file + ": " + ex.getMessage() + ")";
    }
  }

  /** The entries' texts one after another into `to` (Export). */
  public static void export(List<Entry> entries, File to) throws Exception {
    if (to.getParentFile() != null) to.getParentFile().mkdirs();
    Files.write(to.toPath(), joined(entries).getBytes(StandardCharsets.UTF_8));
  }

  /** The entries' texts one after another, a line between them. */
  public static String joined(List<Entry> entries) {
    StringBuilder sb = new StringBuilder();
    for (Entry e : entries) sb.append(text(e)).append("\n----------------------------------------\n\n");
    return sb.toString();
  }

  /** The name Export suggests: pulsekit-logs-<type>-<time>.txt. */
  public static String exportName(String type) {
    return "pulsekit-logs-" + slug(type == null ? ALL : type) + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()) + ".txt";
  }

  /** Deletes the entries (Delete); how many went. */
  public static int delete(List<Entry> entries) {
    int n = 0;
    for (Entry e : entries) if (e.file.delete()) n++;
    return n;
  }

  /** A program run: its arguments, exit code, how long it took and its output. */
  public static File run(String name, List<String> argv, int code, String log, long started) {
    if (!on()) return null;
    long took = System.currentTimeMillis() - started;
    StringBuilder sb = new StringBuilder();
    sb.append("Program: ").append(name).append('\n');
    sb.append("Arguments:");
    if (argv != null) {
      for (int i = 0; i < argv.size(); i++) {
        // A password or key given as an argument is not written into the log.
        boolean secret = i > 0 && argv.get(i - 1).toLowerCase(Locale.ROOT).matches("--?(.*password.*|api_?key|token|secret)");
        sb.append(' ').append(secret ? "(hidden)" : JavaRun.quoteArg(argv.get(i)));
      }
    }
    sb.append('\n');
    sb.append("Exit code: ").append(code).append('\n');
    sb.append("Took: ").append(took / 1000.0).append(" s\n");
    sb.append("Started: ").append(time(started)).append("\n\nOutput:\n").append(log == null ? "" : log);
    return write(RUNS, name + (code == 0 ? " finished" : " failed (exit code " + code + ")"), sb.toString());
  }

  /** An error with its stack trace. */
  public static File error(String what, Throwable error) {
    StringWriter trace = new StringWriter();
    if (error != null) error.printStackTrace(new PrintWriter(trace));
    return write(ERRORS, what + (error == null ? "" : ": " + error), trace.toString());
  }

  /** Pulsekit started: `about` (version, system) and Java's own properties. */
  public static File started(String about) {
    StringBuilder sb = new StringBuilder(about == null ? "" : about.trim() + "\n");
    String[] keys = {"java.version", "java.vendor", "java.vm.name", "os.name", "os.version", "os.arch", "user.dir", "file.encoding"};
    for (String k : keys) if (System.getProperty(k) != null) sb.append(k).append(": ").append(System.getProperty(k)).append('\n');
    Runtime rt = Runtime.getRuntime();
    sb.append("Memory: ").append(rt.maxMemory() / (1024 * 1024)).append(" MB at most, processors: ").append(rt.availableProcessors()).append('\n');
    return write(STARTS, "Pulsekit started", sb.toString());
  }

  /** Errors nothing else caught go into the log too (then to what handled them before). */
  public static void catchErrors() {
    final Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
    if (before instanceof Catcher) return;
    Thread.setDefaultUncaughtExceptionHandler(new Catcher(before));
  }

  static final class Catcher implements Thread.UncaughtExceptionHandler {
    final Thread.UncaughtExceptionHandler before;

    Catcher(Thread.UncaughtExceptionHandler before) {
      this.before = before;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable error) {
      try {
        error("Uncaught on thread " + thread.getName(), error);
      } catch (Throwable ignored) {
        // no log
      }
      if (this.before != null) this.before.uncaughtException(thread, error);
      else error.printStackTrace();
    }
  }

  static String time(long ms) {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(ms));
  }

  static String slug(String type) {
    return type.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  private static String oneLine(String s) {
    return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
  }

  private static String cut(String s) {
    if (s.length() <= MAX_CHARS) return s;
    int half = MAX_CHARS / 2;
    return s.substring(0, half) + "\n\n[... " + (s.length() - MAX_CHARS) + " characters left out ...]\n\n" + s.substring(s.length() - half);
  }

  /** The newest KEEP of the type stay. */
  private static void prune(String type) {
    List<Entry> all = entries(type);
    for (int i = KEEP; i < all.size(); i++) all.get(i).file.delete();
  }
}
