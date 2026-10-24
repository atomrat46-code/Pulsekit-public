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

  /** How long a script may run: rendering a song through a plugin (vst_render.py) takes minutes, not seconds. */
  static final int SCRIPT_SECONDS = 600;

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

  /** The command that starts the Python Pulsekit runs ("py -3" as two words), or null when there is none. */
  public static List<String> pythonCommand() {
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
    String py = findPython();
    if (py == null) {
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
      List<String> cmd = new ArrayList<String>();
      if ("py".equals(py)) {
        cmd.add("py");
        cmd.add("-3");
      } else {
        cmd.add(py);
      }
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(dir.toFile());
      pb.redirectErrorStream(true);
      pb.environment().put("PYTHONPATH", dir.toAbsolutePath().toString());
      pb.environment().put("PYTHONUNBUFFERED", "1");
      Process proc = pb.start();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      InputStream in = proc.getInputStream();
      byte[] buf = new byte[4096];
      long deadline = System.currentTimeMillis() + SCRIPT_SECONDS * 1000L;
      while (System.currentTimeMillis() < deadline) {
        while (in.available() > 0) {
          int n = in.read(buf);
          if (n <= 0) break;
          bos.write(buf, 0, n);
        }
        if (!proc.isAlive()) break;
        Thread.sleep(40);
      }
      if (proc.isAlive()) {
        proc.destroyForcibly();
        bos.write(("\nScript timed out (" + SCRIPT_SECONDS + " s).").getBytes(StandardCharsets.UTF_8));
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
      String header = "$ " + py + " " + name + (argv == null || argv.isEmpty() ? "" : " " + String.join(" ", argv)) + "\n";
      return new Result(header + log, files, code);
    } catch (Exception ex) {
      return new Result("Could not run Python: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
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
