package pulsekit;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a Python file on Android without using the folder it was picked from.
 * The script is copied into app cache, which is writable. Download and "/" are not.
 */
public final class ArtPy {
  private ArtPy() {}

  public static JavaRun.Result run(String name, String source, List<String> argv) {
    File work = null;
    try {
      Context ctx = ArtJava.context();
      String n = name == null || name.trim().isEmpty() ? "script.py" : name.replace('\\', '_').replace('/', '_');
      if (!n.toLowerCase().endsWith(".py")) n = n + ".py";
      String src = source == null ? "" : source;
      if (src.trim().isEmpty()) {
        return fail("That Python file is empty.");
      }
      String py = pythonBin();
      if (py == null) {
        return fail(
            "Python 3 is not installed on this phone.\n"
                + "The script was loaded from anywhere and copied into app storage, same as Java.\n"
                + "Java runs inside the app. Python still needs python3 on the device.");
      }
      work = new File(ctx.getCacheDir(), "pypy-" + System.nanoTime());
      if (!work.mkdirs()) return fail("Could not create a work folder");
      write(new File(work, n), src.getBytes(StandardCharsets.UTF_8));
      copyMidiutil(ctx, work);
      List<String> cmd = new ArrayList<String>();
      cmd.add(py);
      cmd.add(n);
      if (argv != null) cmd.addAll(argv);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(work);
      pb.redirectErrorStream(true);
      pb.environment().put("PYTHONPATH", work.getAbsolutePath());
      pb.environment().put("PYTHONUNBUFFERED", "1");
      Process proc = pb.start();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      InputStream in = proc.getInputStream();
      byte[] buf = new byte[4096];
      long deadline = System.currentTimeMillis() + 20000L;
      while (System.currentTimeMillis() < deadline) {
        while (in.available() > 0) {
          int c = in.read(buf);
          if (c <= 0) break;
          bos.write(buf, 0, c);
        }
        if (!procAlive(proc)) break;
        Thread.sleep(40);
      }
      if (procAlive(proc)) {
        proc.destroy();
        bos.write("\nTimed out (20s).".getBytes(StandardCharsets.UTF_8));
      }
      int c;
      while ((c = in.read(buf)) > 0) bos.write(buf, 0, c);
      int code = procAlive(proc) ? 124 : proc.exitValue();
      List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>();
      StringBuilder saved = new StringBuilder();
      File[] kids = work.listFiles();
      if (kids != null) {
        for (File f : kids) {
          if (!f.isFile()) continue;
          String fn = f.getName();
          if (fn.equals(n) || fn.equals("midiutil.py") || fn.endsWith(".pyc") || fn.startsWith(".")) continue;
          if (f.length() > 3000000L) continue;
          byte[] body = read(f);
          files.add(new JavaRun.FileOut(fn, body));
          String where = ArtJava.publish(ctx, fn, body);
          if (where != null) saved.append("\nsaved ").append(where);
        }
      }
      String shown = "$ " + py + " " + n;
      if (argv != null) for (String a : argv) shown = shown + " " + a;
      String log = shown
          + "\nPython · app storage, not the folder you picked\n"
          + bos.toString("UTF-8")
          + saved;
      return new JavaRun.Result(log, files, code);
    } catch (Throwable ex) {
      String m = ex.getMessage();
      return fail(m == null || m.isEmpty() ? ex.toString() : m);
    } finally {
      delete(work);
    }
  }

  private static String pythonBin() {
    if (works("python3", "--version")) return "python3";
    if (works("python", "--version")) return "python";
    return null;
  }

  private static boolean works(String... cmd) {
    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      if (!procWait(p, 4000L)) {
        p.destroy();
        return false;
      }
      return p.exitValue() == 0;
    } catch (Exception ex) {
      return false;
    }
  }

  private static void copyMidiutil(Context ctx, File work) {
    try {
      File got = extract(ctx, "midiutil.py");
      if (got != null && got.isFile()) {
        write(new File(work, "midiutil.py"), read(got));
      }
    } catch (Exception ignored) {}
  }

  private static File extract(Context ctx, String asset) throws Exception {
    File out = new File(ctx.getDir("art", 0), asset);
    if (out.isFile() && out.length() > 0) return out;
    java.util.zip.ZipFile apk = new java.util.zip.ZipFile(ctx.getApplicationInfo().sourceDir);
    try {
      java.util.zip.ZipEntry entry = apk.getEntry("assets/" + asset);
      if (entry == null) return null;
      InputStream in = apk.getInputStream(entry);
      try {
        write(out, readStream(in));
      } finally {
        in.close();
      }
    } finally {
      apk.close();
    }
    return out.isFile() ? out : null;
  }

  private static void write(File file, byte[] data) throws Exception {
    File parent = file.getParentFile();
    if (parent != null && !parent.isDirectory()) parent.mkdirs();
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  private static byte[] read(File file) throws Exception {
    return readStream(new java.io.FileInputStream(file));
  }

  private static byte[] readStream(InputStream in) throws Exception {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      return bos.toByteArray();
    } finally {
      in.close();
    }
  }

  private static void delete(File dir) {
    if (dir == null) return;
    File[] kids = dir.listFiles();
    if (kids != null) for (File f : kids) f.delete();
    dir.delete();
  }

  private static JavaRun.Result fail(String msg) {
    return new JavaRun.Result(msg, new ArrayList<JavaRun.FileOut>(), 1);
  }

  // API 24-safe process helpers: Process.isAlive(), destroyForcibly() and
  // waitFor(long, TimeUnit) only exist on Android from API 26.
  private static boolean procAlive(Process p) {
    try {
      p.exitValue();
      return false;
    } catch (IllegalThreadStateException ex) {
      return true;
    }
  }

  private static boolean procWait(Process p, long ms) throws InterruptedException {
    long deadline = System.currentTimeMillis() + ms;
    while (procAlive(p)) {
      if (System.currentTimeMillis() >= deadline) return false;
      Thread.sleep(25);
    }
    return true;
  }
}
