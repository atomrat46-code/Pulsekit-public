package pulsekit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Runs a Pulsekit Python script with the computer's Python 3 and bundled midiutil. */
public final class PythonRun {
  public static final class FileOut {
    public final String name;
    public final byte[] bytes;

    FileOut(String name, byte[] bytes) {
      this.name = name;
      this.bytes = bytes;
    }
  }

  public static final class Result {
    public final String log;
    public final List<FileOut> files;
    public final int code;

    Result(String log, List<FileOut> files, int code) {
      this.log = log;
      this.files = files;
      this.code = code;
    }
  }

  private PythonRun() {}

  /**
   * How long a script may go on without printing anything: a batch (vst_render.py --dir on a thousand
   * MIDI files) runs for hours while it prints a line a file, so the time counts from its last output.
   */
  static final int SCRIPT_SECONDS = 600;

  /** The script running now, and the file that asks it to stop (its PULSEKIT_STOP_FILE). */
  static volatile Process current;
  static volatile File stopFile;

  /**
   * Stop script: the first press asks the script to stop (vst_render.py ends after the file in
   * progress, then tests and zips what it made); the next one ends it at once. True when it was asked.
   */
  static boolean stop() {
    Process p = current;
    File f = stopFile;
    if (p == null || !p.isAlive()) return false;
    if (f != null && !f.exists()) {
      try {
        f.createNewFile();
        return true;
      } catch (Exception ignored) {
        // ended below
      }
    }
    p.destroyForcibly();
    return false;
  }

  public static String findPython() {
    String[][] cmds = {{"python3"}, {"python"}, {"py", "-3"}};
    for (String[] cmd : cmds) {
      try {
        Process p = new ProcessBuilder(join(cmd, "--version")).redirectErrorStream(true).start();
        if (!p.waitFor(4, TimeUnit.SECONDS)) {
          p.destroyForcibly();
          continue;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        InputStream in = p.getInputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        String out = bos.toString("UTF-8");
        if (p.exitValue() == 0 && out.toLowerCase().contains("python")) return cmd[0];
        if (out.toLowerCase().contains("python")) return cmd[0];
      } catch (Exception ignored) {
        /* try next */
      }
    }
    return null;
  }

  /** A Python found on this computer: its python.exe and version ("3.10.11"). */
  public static final class Found {
    public final String path;
    public final String version;

    Found(String path, String version) {
      this.path = path;
      this.version = version;
    }

    /** "Python 3.10.11 — C:\\Users\\...\\python.exe" for General settings' list. */
    public String label() {
      return "Python " + this.version + " \u2014 " + this.path;
    }
  }

  /**
   * The Pythons on this computer: those python3, python and py start, those the py launcher lists,
   * and those in the usual folders (Windows: AppData\\Local\\Programs\\Python, AppData\\Local\\Python,
   * Program Files). Each once, by where it is. Slow (it starts each): not on the screen's thread.
   */
  public static List<Found> found() {
    java.util.LinkedHashSet<String> tries = new java.util.LinkedHashSet<String>();
    for (String[] cmd : new String[][] {{"python3"}, {"python"}, {"py", "-3"}}) {
      Found f = describe(java.util.Arrays.asList(cmd));
      if (f != null) tries.add(f.path);
    }
    // The py launcher's own list (Windows): " -V:3.10 *   C:\\...\\python.exe".
    for (String line : runFor(java.util.Arrays.asList("py", "-0p"), 10, null).split("\n")) {
      int at = line.toLowerCase(java.util.Locale.ROOT).indexOf(":\\");
      if (at > 0 && line.toLowerCase(java.util.Locale.ROOT).trim().endsWith(".exe")) tries.add(line.substring(at - 1).trim());
    }
    String local = System.getenv("LOCALAPPDATA");
    List<File> dirs = new ArrayList<File>();
    if (local != null) {
      dirs.add(new File(local, "Programs" + File.separator + "Python"));
      dirs.add(new File(local, "Python"));
    }
    for (String env : new String[] {"ProgramFiles", "ProgramFiles(x86)"}) if (System.getenv(env) != null) dirs.add(new File(System.getenv(env)));
    for (File dir : dirs) {
      File[] kids = dir.listFiles();
      if (kids == null) continue;
      for (File k : kids) {
        File exe = new File(k, "python.exe");
        if (k.getName().toLowerCase(java.util.Locale.ROOT).startsWith("python") && exe.isFile()) tries.add(exe.getAbsolutePath());
      }
    }
    for (String unix : new String[] {"/usr/bin/python3", "/usr/local/bin/python3"}) if (new File(unix).isFile()) tries.add(unix);
    List<Found> out = new ArrayList<Found>();
    java.util.Set<String> seen = new java.util.HashSet<String>();
    for (String t : tries) {
      Found f = describe(java.util.Collections.singletonList(t));
      if (f == null) continue;
      // The same Python by two names (python3 and python, a link to it): once.
      String key = f.path;
      try {
        key = new File(f.path).getCanonicalPath();
      } catch (Exception ignored) {
        // its own name
      }
      if (seen.add(key.toLowerCase(java.util.Locale.ROOT))) out.add(f);
    }
    return out;
  }

  /** The Python a command starts (its sys.executable and version), or null when it is none. */
  static Found describe(List<String> cmd) {
    List<String> all = new ArrayList<String>(cmd);
    all.add("-c");
    all.add("import sys; print(sys.executable); print(sys.version.split()[0])");
    String[] lines = runFor(all, 10, null).trim().split("\r?\n");
    if (lines.length < 2 || !lines[lines.length - 1].matches("\\d+\\.\\d+.*")) return null;
    String exe = lines[lines.length - 2].trim();
    return exe.length() == 0 ? null : new Found(exe, lines[lines.length - 1].trim());
  }

  /** Whether that Python imports these packages (numpy, dawdreamer...). */
  public static boolean hasPackages(String python, List<String> packages) {
    StringBuilder imports = new StringBuilder();
    for (String p : packages) imports.append(imports.length() == 0 ? "import " : ", ").append(PipMissing.moduleOf(p));
    return runCode(java.util.Arrays.asList(python, "-c", imports.toString()), 30, null) == 0;
  }

  /** The command that starts the Python Pulsekit runs ("py -3" as two words), or null when there is none. */
  public static List<String> pythonCommand() {
    // The one picked in General settings, while it is there.
    String chosen = AppSettings.pythonPath();
    if (chosen.length() > 0 && new File(chosen).isFile()) return new ArrayList<String>(java.util.Collections.singletonList(chosen));
    String py = findPython();
    if (py == null) return null;
    List<String> cmd = new ArrayList<String>();
    cmd.add(py);
    if ("py".equals(py)) cmd.add("-3");
    return cmd;
  }

  /** Where that Python is (its sys.executable), or the command's name when it does not say. */
  public static String whichPython() {
    List<String> cmd = pythonCommand();
    if (cmd == null) return "(none)";
    List<String> all = new ArrayList<String>(cmd);
    all.add("-c");
    all.add("import sys; print(sys.executable)");
    String out = runFor(all, 10, null).trim();
    return out.length() > 0 && !out.contains("\n") ? out : String.join(" ", cmd);
  }

  /**
   * pip install for the Python Pulsekit runs (python -m pip install ...), with --user when pip may
   * not write into Python's own folder. Its output line by line to `out`; whether it worked.
   */
  public static boolean pipInstall(List<String> packages, java.util.function.Consumer<String> out) {
    List<String> cmd = pythonCommand();
    if (cmd == null || packages == null || packages.isEmpty()) return false;
    List<String> all = new ArrayList<String>(cmd);
    Collections.addAll(all, "-m", "pip", "install");
    all.addAll(packages);
    StringBuilder log = new StringBuilder();
    int code = runCode(all, 600, line -> {
      log.append(line).append('\n');
      if (out != null) out.accept(line);
    });
    if (code == 0) return true;
    if (!PipMissing.needsUser(log.toString())) return false;
    List<String> user = new ArrayList<String>(cmd);
    Collections.addAll(user, "-m", "pip", "install", "--user");
    user.addAll(packages);
    if (out != null) out.accept("Trying again with --user\u2026");
    return runCode(user, 600, out) == 0;
  }

  /** A command's output (stdout and stderr), waiting at most `seconds`. */
  static String runFor(List<String> cmd, int seconds, java.util.function.Consumer<String> lines) {
    StringBuilder sb = new StringBuilder();
    runCode(cmd, seconds, line -> {
      sb.append(line).append('\n');
      if (lines != null) lines.accept(line);
    });
    return sb.toString();
  }

  /** A command run, its lines to `lines`; its exit code (-1 when it could not start or took too long). */
  static int runCode(List<String> cmd, int seconds, java.util.function.Consumer<String> lines) {
    try {
      ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
      pb.environment().put("PYTHONUNBUFFERED", "1");
      Process p = pb.start();
      Thread reader = new Thread(() -> {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
          for (String line; (line = r.readLine()) != null; ) if (lines != null) lines.accept(line);
        } catch (Exception ignored) {
          // the process ended
        }
      });
      reader.setDaemon(true);
      reader.start();
      if (!p.waitFor(seconds, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        if (lines != null) lines.accept("Stopped after " + seconds + " s.");
        return -1;
      }
      reader.join(2000);
      return p.exitValue();
    } catch (Exception ex) {
      if (lines != null) lines.accept("Could not run " + String.join(" ", cmd) + ": " + ex.getMessage());
      return -1;
    }
  }

  private static List<String> join(String[] cmd, String extra) {
    List<String> all = new ArrayList<String>();
    Collections.addAll(all, cmd);
    all.add(extra);
    return all;
  }

  /** The kit switches the program mentions (--bpm, --style, --genre, --bars, --swing) and the extra args, as on Android. */
  public static List<String> kitArgv(String source, int bpm, String style, int bars, int swing, String extra) {
    return JavaRun.argvFor(source, bpm, style, bars, swing, extra);
  }

  /** As above, with the app's time signature (passed as --timesig unless 4/4). */
  public static List<String> kitArgv(String source, int bpm, String style, int bars, int swing, String extra, int tsNum, int tsDen) {
    return JavaRun.argvFor(source, bpm, style, bars, swing, extra, tsNum, tsDen);
  }

  public static Result run(String source, String scriptName, List<String> argv) {
    List<String> python = pythonCommand();
    if (python == null) {
      return new Result(
          "Python 3 was not found on this computer.\nInstall Python 3, then Run again. midiutil is bundled.",
          Collections.<FileOut>emptyList(),
          127);
    }
    Path dir = null;
    try {
      dir = Files.createTempDirectory("pulsekit-py-");
      String name = scriptName == null || scriptName.trim().isEmpty() ? "script.py" : scriptName.replaceAll("[\\\\/]", "_");
      if (!name.toLowerCase().endsWith(".py")) name = name + ".py";
      Files.write(dir.resolve(name), source.getBytes(StandardCharsets.UTF_8));
      byte[] midiutil = readResource("midiutil.py");
      if (midiutil != null) Files.write(dir.resolve("midiutil.py"), midiutil);
      List<String> cmd = new ArrayList<String>(python);
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(dir.toFile());
      pb.redirectErrorStream(true);
      pb.environment().put("PYTHONPATH", dir.toAbsolutePath().toString());
      pb.environment().put("PYTHONUNBUFFERED", "1");
      File stopAsk = dir.resolve("pulsekit-stop").toFile();
      pb.environment().put("PULSEKIT_STOP_FILE", stopAsk.getAbsolutePath());
      Process proc = pb.start();
      stopFile = stopAsk;
      current = proc;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      InputStream in = proc.getInputStream();
      byte[] buf = new byte[4096];
      long deadline = System.currentTimeMillis() + SCRIPT_SECONDS * 1000L;
      while (System.currentTimeMillis() < deadline) {
        while (in.available() > 0) {
          int n = in.read(buf);
          if (n <= 0) break;
          bos.write(buf, 0, n);
          // Output: the script is working, its time starts again.
          deadline = System.currentTimeMillis() + SCRIPT_SECONDS * 1000L;
        }
        if (!proc.isAlive()) break;
        Thread.sleep(40);
      }
      if (proc.isAlive()) {
        proc.destroyForcibly();
        bos.write(("\nScript timed out (no output for " + SCRIPT_SECONDS + " s).").getBytes(StandardCharsets.UTF_8));
      }
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      int code = proc.isAlive() ? 124 : proc.exitValue();
      String log = bos.toString("UTF-8");
      List<FileOut> files = new ArrayList<FileOut>();
      File[] kids = dir.toFile().listFiles();
      if (kids != null) {
        for (File f : kids) {
          if (!f.isFile()) continue;
          String fn = f.getName();
          if (fn.equals(name) || fn.equals("midiutil.py") || fn.equals("midiutil.pyc")) continue;
          if (fn.endsWith(".pyc") || fn.startsWith(".")) continue;
          byte[] data = Files.readAllBytes(f.toPath());
          files.add(new FileOut(fn, data));
        }
      }
      String header = "$ " + String.join(" ", python) + " " + name + (argv == null || argv.isEmpty() ? "" : " " + String.join(" ", argv)) + "\n";
      return new Result(header + log, files, code);
    } catch (Exception ex) {
      return new Result("Could not run Python: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      current = null;
      stopFile = null;
      if (dir != null) {
        try {
          File[] kids = dir.toFile().listFiles();
          if (kids != null) for (File f : kids) f.delete();
          dir.toFile().delete();
        } catch (Exception ignored) {
          /* leave temp */
        }
      }
    }
  }

  public static String defaultScript() {
    byte[] data = readResource("drum_midi.py");
    if (data != null) return new String(data, StandardCharsets.UTF_8);
    return "#!/usr/bin/env python3\nprint('no script')\n";
  }

  static byte[] readResource(String name) {
    try (InputStream in = PythonRun.class.getResourceAsStream("/pulsekit/" + name)) {
      if (in == null) return null;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      return bos.toByteArray();
    } catch (Exception ex) {
      return null;
    }
  }
}
