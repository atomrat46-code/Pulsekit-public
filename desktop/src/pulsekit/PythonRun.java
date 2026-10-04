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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  private static List<String> join(String[] cmd, String extra) {
    List<String> all = new ArrayList<String>();
    Collections.addAll(all, cmd);
    all.add(extra);
    return all;
  }

  public static List<String> splitArgv(String line) {
    List<String> out = new ArrayList<String>();
    if (line == null || line.trim().isEmpty()) return out;
    Matcher m = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)").matcher(line.trim());
    while (m.find()) {
      String a = m.group(1);
      if (a == null) a = m.group(2);
      if (a == null) a = m.group(3);
      if (a != null && !a.isEmpty()) out.add(a);
    }
    return out;
  }

  /** The kit switches the program mentions (--bpm, --style, --genre, --bars, --swing), as on Android. */
  public static List<String> kitArgv(String source, int bpm, String style, int bars, int swing) {
    return JavaRun.argvFor(source, bpm, style, bars, swing, "");
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
      long deadline = System.currentTimeMillis() + 20_000;
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
        bos.write("\nScript timed out (20s).".getBytes(StandardCharsets.UTF_8));
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
