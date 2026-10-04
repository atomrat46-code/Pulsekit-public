package pulsekit;

import android.content.Context;
import dalvik.system.DexClassLoader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Compiles Java on the phone and runs it on ART.
 * ECJ and dx ship as assets. ART is the system runtime, loaded through DexClassLoader.
 */
public final class ArtJava {
  private static DexClassLoader ecjLoader;
  private static DexClassLoader dxLoader;
  private static File rtJar;
  private static File resDir;

  private ArtJava() {}

  public static JavaRun.Result run(String name, String source, byte[] bytes, List<String> argv) {
    String n = name == null || name.trim().isEmpty() ? "Main.java" : name.replaceAll("[\\\\/]", "_");
    String low = n.toLowerCase();
    File work = null;
    try {
      Context ctx = context();
      ensure(ctx);
      work = new File(ctx.getCacheDir(), "pyjav-" + System.nanoTime());
      if (!work.mkdirs()) return fail("Could not create a work folder");
      pinPath(work);
      File clsDir = new File(work, "cls");
      if (!clsDir.mkdirs()) return fail("Could not create a class folder");
      String main;
      String logHead;
      if (low.endsWith(".java")) {
        String src = source == null ? "" : source;
        if (src.trim().isEmpty()) return fail("That Java file is empty.");
        String fromName = n.substring(0, n.length() - 5);
        String declared = publicClassName(src);
        String simple = declared != null ? declared : fromName;
        File srcFile = new File(work, simple + ".java");
        write(srcFile, src.getBytes("UTF-8"));
        String compiled = compile(srcFile, clsDir);
        if (compiled != null) return new JavaRun.Result(compiled, empty(), 1);
        main = qualified(simple, src);
        logHead = "$ art " + main;
      } else if (low.endsWith(".jar")) {
        if (bytes == null || bytes.length == 0) return fail("No bytes for " + n);
        main = JavaRun.mainClass(bytes);
        if (main == null || main.isEmpty()) {
          return fail("No Main-Class in " + n + ".\nAdd Main-Class to META-INF/MANIFEST.MF.");
        }
        File jar = new File(work, n);
        write(jar, bytes);
        File program = prepareJar(ctx, jar);
        String dexed = dexInto(new File(work, "pgm.dex"), program);
        if (dexed != null) return new JavaRun.Result(dexed, empty(), 1);
        return invoke(ctx, work, new File(work, "pgm.dex"), main, argv, "$ art -jar " + n, n);
      } else if (low.endsWith(".class")) {
        if (bytes == null || bytes.length == 0) return fail("No bytes for " + n);
        main = thisClass(bytes);
        File cf = new File(clsDir, main.replace('.', '/') + ".class");
        File parent = cf.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return fail("Could not place " + main);
        write(cf, bytes);
        logHead = "$ art " + main;
      } else {
        return fail("Pick a .py, .java, .class, or .jar file.");
      }
      if (!low.endsWith(".jar")) {
        String dexed = dexInto(new File(work, "pgm.dex"), clsDir);
        if (dexed != null) return new JavaRun.Result(dexed, empty(), 1);
        return invoke(ctx, work, new File(work, "pgm.dex"), main, argv, logHead, n);
      }
      return fail("Could not run " + n);
    } catch (Throwable ex) {
      String m = ex.getMessage();
      return fail(m == null || m.isEmpty() ? ex.toString() : m);
    } finally {
      delete(work);
    }
  }

  private static JavaRun.Result invoke(Context ctx, File work, File dex, String main, List<String> argv, String head, String skip) throws Exception {
    File opt = ctx.getDir("artopt", 0);
    DexClassLoader loader = new DexClassLoader(dex.getAbsolutePath(), opt.getAbsolutePath(), null, ArtJava.class.getClassLoader());
    Class<?> cls;
    try {
      cls = loader.loadClass(main);
    } catch (ClassNotFoundException ex) {
      return fail(head + "\nCould not find class " + main);
    }
    Method method;
    try {
      method = cls.getMethod("main", String[].class);
    } catch (NoSuchMethodException ex) {
      return fail(head + "\n" + main + " has no public static void main(String[]).");
    }
    final String[] args = placeInputs(work, argv);
    final int[] code = new int[] {0};
    final StringBuilder err = new StringBuilder();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(bos, true, "UTF-8");
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    String oldDir = System.getProperty("user.dir");
    String shown = head;
    for (int i = 0; i < args.length; i++) shown = shown + " " + args[i];
    final String logHead = shown;
    java.util.HashSet<String> before = new java.util.HashSet<String>();
    File[] prior = work.listFiles();
    if (prior != null) {
      for (int i = 0; i < prior.length; i++) {
        if (prior[i].isFile()) before.add(prior[i].getName());
      }
    }
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          method.invoke(null, (Object) args);
        } catch (Throwable ex) {
          Throwable c = ex.getCause() == null ? ex : ex.getCause();
          code[0] = 1;
          c.printStackTrace(ps);
          err.append(c.getMessage() == null ? c.toString() : c.getMessage());
        }
      }
    }, "pulsekit-art-main");
    t.setContextClassLoader(loader);
    exemptHidden();
    pinPath(work);
    String oldCwd = cwd();
    boolean moved = chdir(work.getAbsolutePath());
    try {
      System.setProperty("user.dir", work.getAbsolutePath());
      System.setOut(ps);
      System.setErr(ps);
      t.start();
      t.join(JavaRun.RUN_MS);
      if (t.isAlive()) {
        t.interrupt();
        code[0] = 124;
        ps.println("Timed out (" + (JavaRun.RUN_MS / 1000) + "s).");
      }
      ps.flush();
    } finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      if (oldDir != null) System.setProperty("user.dir", oldDir);
      if (moved) chdir(oldCwd == null ? "/" : oldCwd);
    }
    List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>();
    File[] kids = work.listFiles();
    if (kids != null) {
      for (File f : kids) {
        if (!f.isFile()) continue;
        String fn = f.getName();
        if (before.contains(fn)) continue;
        if (fn.equals(skip) || fn.endsWith(".class") || fn.endsWith(".dex") || fn.endsWith(".java")) continue;
        if (f.length() > JavaRun.maxFileBytes(fn)) continue;
        byte[] body = read(f);
        files.add(new JavaRun.FileOut(fn, body));
        String saved = publish(ctx, fn, body);
        if (saved != null) ps.println("saved " + saved);
      }
    }
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        File extra = new File(args[i]);
        if (!extra.isFile()) continue;
        File parent = extra.getParentFile();
        if (parent != null && work.equals(parent)) continue;
        boolean seen = false;
        for (int j = 0; j < files.size(); j++) {
          if (extra.getName().equals(files.get(j).name)) seen = true;
        }
        if (seen || extra.length() > 3000000L) continue;
        byte[] body = read(extra);
        files.add(new JavaRun.FileOut(extra.getName(), body));
        String saved = publish(ctx, extra.getName(), body);
        if (saved != null) ps.println("saved " + saved);
      }
    }
    addLoggedMidi(bos.toString("UTF-8"), files, ctx);
    String log = logHead + "\nART · compiled on this phone\n" + bos.toString("UTF-8");
    if (err.length() > 0 && bos.size() == 0) log = log + err;
    return new JavaRun.Result(log, files, code[0]);
  }

  private static void addLoggedMidi(String log, List<JavaRun.FileOut> files, Context ctx) {
    if (log == null || files == null) return;
    int at = 0;
    String mark = "MIDI Extracted to:";
    while (at >= 0) {
      at = log.indexOf(mark, at);
      if (at < 0) break;
      int start = at + mark.length();
      int end = log.indexOf('\n', start);
      String path = (end < 0 ? log.substring(start) : log.substring(start, end)).trim();
      at = end < 0 ? -1 : end + 1;
      if (path.length() == 0) continue;
      File extra = new File(path);
      if (!extra.isFile() || extra.length() > 3000000L) continue;
      boolean seen = false;
      for (int j = 0; j < files.size(); j++) {
        if (extra.getName().equals(files.get(j).name)) seen = true;
      }
      if (seen) continue;
      try {
        byte[] body = read(extra);
        files.add(new JavaRun.FileOut(extra.getName(), body));
        publish(ctx, extra.getName(), body);
      } catch (Exception ignored) {}
    }
  }

  private static String publicClassName(String src) {
    if (src == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+)?class\\s+(\\w+)")
        .matcher(src);
    return m.find() ? m.group(1) : null;
  }

  private static String qualified(String simple, String src) {
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(src == null ? "" : src);
    if (m.find()) return m.group(1) + "." + simple;
    return simple;
  }

  private static String compile(File source, File dest) throws Exception {
    Class<?> em = Class.forName("org.eclipse.jdt.internal.compiler.batch.Main", true, ecjLoader);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);
    Object compiler = em.getConstructor(PrintWriter.class, PrintWriter.class, boolean.class, java.util.Map.class)
        .newInstance(pw, pw, Boolean.FALSE, null);
    em.getField("systemExitWhenFinished").setBoolean(compiler, false);
    String rt = rtJar.getAbsolutePath();
    boolean ok = ((Boolean) em.getMethod("compile", String[].class).invoke(compiler, (Object) new String[] {
        "-bootclasspath", rt,
        "-classpath", rt,
        "-1.8",
        "-encoding", "UTF-8",
        "-proc:none",
        "-d", dest.getAbsolutePath(),
        source.getAbsolutePath()
    })).booleanValue();
    if (ok) return null;
    String msg = sw.toString().trim();
    if (msg.isEmpty()) msg = "Java compile failed.";
    return "$ art javac " + source.getName() + "\n" + msg
        + "\nThe in-app compiler sees java.* and javax.* (not Android SDK classes).";
  }

  /** Android resolves a relative File against "/", which is read-only (EROFS). */
  private static String cwd() {
    try {
      Object v = Class.forName("android.system.Os").getMethod("getcwd").invoke(null);
      return v == null ? null : String.valueOf(v);
    } catch (Throwable ex) {
      return null;
    }
  }

  private static boolean chdir(String path) {
    try {
      Class.forName("android.system.Os").getMethod("chdir", String.class).invoke(null, path);
      return true;
    } catch (Throwable ex) {
      return false;
    }
  }

  /** Keep relative files off "/". Called from startup and again before a program runs. */
  public static void pinWorkDir(Context ctx) {
    if (ctx == null) return;
    File dir = ctx.getFilesDir();
    if (dir != null) pinPath(dir);
  }

  private static void pinPath(File dir) {
    if (dir == null) return;
    String path = dir.getAbsolutePath();
    System.setProperty("pulsekit.work", path);
    System.setProperty("user.dir", path);
    System.setProperty("java.io.tmpdir", path);
    chdir(path);
  }

  /** Android 9+ hides Os.chdir. Ask the runtime to allow it before changing folder. */
  private static void exemptHidden() {
    try {
      Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
      Method forName = Class.class.getDeclaredMethod("forName", String.class);
      Class<?> vm = (Class<?>) forName.invoke(null, "dalvik.system.VMRuntime");
      Method getRuntime = (Method) getDeclaredMethod.invoke(vm, "getRuntime", null);
      Method setEx = (Method) getDeclaredMethod.invoke(vm, "setHiddenApiExemptions", new Class[] {String[].class});
      Object runtime = getRuntime.invoke(null);
      setEx.invoke(runtime, (Object) new String[] {"L"});
    } catch (Throwable ignored) {}
  }

  /**
   * Replace an old MIDI writer that opens a relative path. That path lands on "/"
   * and Android returns EROFS. A failed rewrite is reported instead of ignored.
   */
  private static File prepareJar(Context ctx, File jar) throws Exception {
    byte[] fixed = assetBytes(ctx, "MidiSystem.class");
    boolean javaxRef = jarMentions(jar, "javax/sound/midi");
    java.util.Map<String, byte[]> extra = new java.util.HashMap<String, byte[]>();
    if (javaxRef) {
      ZipFile lib = new ZipFile(assetFile(ctx, "javax-midi.jar"));
      try {
        java.util.Enumeration<? extends ZipEntry> entries = lib.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (entry.isDirectory()) continue;
          extra.put(entry.getName(), readStream(lib.getInputStream(entry)));
        }
      } finally {
        lib.close();
      }
    }
    File out = new File(jar.getParentFile(), "run.jar");
    boolean replaced = false;
    boolean hadMidi = false;
    ZipFile inZip = new ZipFile(jar);
    try {
      java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(out));
      try {
        java.util.Enumeration<? extends ZipEntry> entries = inZip.entries();
        byte[] buf = new byte[8192];
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          String name = entry.getName();
          if (extra.containsKey(name)) continue;
          if ("midi/MidiSystem.class".equals(name)) hadMidi = true;
          zos.putNextEntry(new ZipEntry(name));
          if ("midi/MidiSystem.class".equals(name)) {
            zos.write(fixed);
            replaced = true;
          } else if (!entry.isDirectory()) {
            InputStream in = inZip.getInputStream(entry);
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            in.close();
          }
          zos.closeEntry();
        }
        for (java.util.Map.Entry<String, byte[]> e : extra.entrySet()) {
          zos.putNextEntry(new ZipEntry(e.getKey()));
          zos.write(e.getValue());
          zos.closeEntry();
        }
      } finally {
        zos.close();
      }
    } finally {
      inZip.close();
    }
    if (hadMidi && !replaced) throw new Exception("Could not replace the MIDI writer");
    if (hadMidi) {
      ZipFile check = new ZipFile(out);
      try {
        ZipEntry got = check.getEntry("midi/MidiSystem.class");
        if (got == null) throw new Exception("MIDI writer missing after rewrite");
        if (!same(readStream(check.getInputStream(got)), fixed)) throw new Exception("MIDI writer was not updated");
      } finally {
        check.close();
      }
    }
    return out;
  }

  private static boolean jarMentions(File jar, String needle) throws Exception {
    byte[] want = needle.getBytes("UTF-8");
    ZipFile zip = new ZipFile(jar);
    try {
      java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (entry.isDirectory()) continue;
        String name = entry.getName();
        if (name.indexOf(needle) >= 0) return true;
        if (!name.endsWith(".class")) continue;
        if (indexOf(readStream(zip.getInputStream(entry)), want) >= 0) return true;
      }
    } finally {
      zip.close();
    }
    return false;
  }

  private static boolean same(byte[] a, byte[] b) {
    if (a == null || b == null || a.length != b.length) return false;
    for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
    return true;
  }

  private static int indexOf(byte[] data, byte[] needle) {
    if (data == null || needle == null || needle.length == 0 || data.length < needle.length) return -1;
    outer:
    for (int i = 0; i <= data.length - needle.length; i++) {
      for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
      return i;
    }
    return -1;
  }

  private static byte[] assetBytes(Context ctx, String name) throws Exception {
    return readAsset(ctx, name);
  }

  private static File assetFile(Context ctx, String name) throws Exception {
    File out = new File(ctx.getCacheDir(), "asset-" + name.replace('/', '_'));
    write(out, readAsset(ctx, name));
    return out;
  }

  /** Copy a browsed input into the work folder and pass that path to main. */
  private static String[] placeInputs(File work, List<String> argv) throws Exception {
    if (argv == null || argv.isEmpty()) return new String[0];
    String[] args = argv.toArray(new String[argv.size()]);
    for (int i = 0; i < args.length; i++) {
      if (args[i] == null || args[i].length() == 0) continue;
      File src = new File(args[i]);
      if (!src.isFile()) {
        String lowArg = args[i].toLowerCase();
        if (lowArg.endsWith(".mid") || lowArg.endsWith(".midi")) {
          String base = src.getName();
          if (base == null || base.length() == 0) base = "output.mid";
          args[i] = new File(work, base).getAbsolutePath();
        }
        continue;
      }
      File dest = new File(work, src.getName());
      if (!src.getAbsolutePath().equals(dest.getAbsolutePath())) {
        streamCopy(src, dest);
      }
      args[i] = dest.getAbsolutePath();
    }
    return args;
  }

  private static void streamCopy(File src, File dest) throws Exception {
    File parent = dest.getParentFile();
    if (parent != null && !parent.isDirectory()) parent.mkdirs();
    FileInputStream in = new FileInputStream(src);
    try {
      FileOutputStream out = new FileOutputStream(dest);
      try {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
      } finally {
        out.close();
      }
    } finally {
      in.close();
    }
  }

  private static byte[] readAsset(Context ctx, String name) throws Exception {
    ZipFile apk = new ZipFile(ctx.getApplicationInfo().sourceDir);
    try {
      ZipEntry entry = apk.getEntry("assets/" + name);
      if (entry == null) throw new Exception("Missing asset " + name);
      return readStream(apk.getInputStream(entry));
    } finally {
      apk.close();
    }
  }

  private static byte[] readStream(InputStream in) throws Exception {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      return bos.toByteArray();
    } finally {
      in.close();
    }
  }

  private static String dexInto(File outDex, File input) throws Exception {
    Class<?> dxMain = Class.forName("com.android.dx.command.dexer.Main", true, dxLoader);
    Class<?> argsCls = Class.forName("com.android.dx.command.dexer.Main$Arguments", true, dxLoader);
    Object arguments = argsCls.getConstructor().newInstance();
    Method parse = argsCls.getDeclaredMethod("parse", String[].class);
    parse.setAccessible(true);
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream old = System.err;
    PrintStream ps = new PrintStream(err, true, "UTF-8");
    int code;
    try {
      System.setErr(ps);
      parse.invoke(arguments, (Object) new String[] {
          "--core-library",
          "--min-sdk-version=24",
          "--output=" + outDex.getAbsolutePath(),
          input.getAbsolutePath()
      });
      code = ((Integer) dxMain.getMethod("run", argsCls).invoke(null, arguments)).intValue();
      try {
        dxMain.getMethod("clearInternTables").invoke(null);
      } catch (Throwable ignored) {}
    } finally {
      System.setErr(old);
    }
    if (code == 0 && outDex.isFile()) return null;
    String msg = err.toString("UTF-8").trim();
    if (msg.isEmpty()) msg = "Could not convert that program to DEX (code " + code + ").";
    return msg;
  }

  private static void ensure(Context ctx) throws Exception {
    if (ecjLoader != null && dxLoader != null && rtJar != null && rtJar.isFile()) return;
    File dir = ctx.getDir("art", 0);
    rtJar = extract(ctx, dir, "rt.jar");
    File ecjDex = extract(ctx, dir, "ecj.dex");
    File dxDex = extract(ctx, dir, "dx.dex");
    File resZip = extract(ctx, dir, "ecj-res.zip");
    resDir = new File(dir, "res");
    if (!new File(resDir, "org/eclipse/jdt/internal/compiler/batch/messages.properties").isFile()) {
      unzip(resZip, resDir);
    }
    File opt = ctx.getDir("artopt", 0);
    ClassLoader parent = ArtJava.class.getClassLoader();
    ecjLoader = new ResDexLoader(ecjDex.getAbsolutePath(), opt.getAbsolutePath(), parent, resDir);
    dxLoader = new DexClassLoader(dxDex.getAbsolutePath(), opt.getAbsolutePath(), null, parent);
  }

  private static File extract(Context ctx, File dir, String name) throws Exception {
    File out = new File(dir, name);
    if (out.isFile() && out.length() > 0) return out;
    ZipFile apk = new ZipFile(ctx.getApplicationInfo().sourceDir);
    try {
      ZipEntry e = apk.getEntry("assets/" + name);
      if (e == null) throw new Exception("Missing assets/" + name + " in the APK.");
      InputStream in = apk.getInputStream(e);
      try {
        writeStream(out, in);
      } finally {
        in.close();
      }
    } finally {
      apk.close();
    }
    return out;
  }

  private static void unzip(File zip, File dest) throws Exception {
    ZipFile z = new ZipFile(zip);
    try {
      java.util.Enumeration<? extends ZipEntry> en = z.entries();
      byte[] buf = new byte[8192];
      while (en.hasMoreElements()) {
        ZipEntry e = en.nextElement();
        if (e.isDirectory()) continue;
        String name = e.getName();
        if (name.contains("..")) continue;
        File f = new File(dest, name);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        InputStream in = z.getInputStream(e);
        try {
          writeStream(f, in);
        } finally {
          in.close();
        }
      }
    } finally {
      z.close();
    }
  }

  /** Dex loader that also serves ECJ's properties from an unpacked resource tree. */
  public static final class ResDexLoader extends DexClassLoader {
    private final File res;

    ResDexLoader(String dex, String opt, ClassLoader parent, File res) {
      super(dex, opt, null, parent);
      this.res = res;
    }

    @Override
    public java.net.URL getResource(String name) {
      try {
        File f = new File(res, name);
        if (f.isFile()) return f.toURI().toURL();
      } catch (Exception ignored) {}
      return super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      try {
        File f = new File(res, name);
        if (f.isFile()) return new java.io.FileInputStream(f);
      } catch (Exception ignored) {}
      return super.getResourceAsStream(name);
    }
  }

  static Context context() throws Exception {
    Class<?> at = Class.forName("android.app.ActivityThread");
    Object app = at.getMethod("currentApplication").invoke(null);
    if (!(app instanceof Context)) throw new Exception("Android runtime is not ready yet.");
    return (Context) app;
  }

  static String thisClass(byte[] b) throws Exception {
    if (b == null || b.length < 10 || (b[0] & 255) != 0xCA || (b[1] & 255) != 0xFE || (b[2] & 255) != 0xBA || (b[3] & 255) != 0xBE) {
      throw new Exception("That file is not a Java class.");
    }
    int count = u2(b, 8);
    String[] utf = new String[count];
    int[] klass = new int[count];
    int i = 10;
    for (int idx = 1; idx < count; idx++) {
      int tag = b[i++] & 255;
      if (tag == 1) {
        int len = u2(b, i);
        i += 2;
        utf[idx] = new String(b, i, len, "UTF-8");
        i += len;
      } else if (tag == 7 || tag == 8 || tag == 16 || tag == 19 || tag == 20) {
        if (tag == 7) klass[idx] = u2(b, i);
        i += 2;
      } else if (tag == 15) {
        i += 3;
      } else if (tag == 5 || tag == 6) {
        i += 8;
        idx++;
      } else if (tag == 3 || tag == 4 || tag == 9 || tag == 10 || tag == 11 || tag == 12 || tag == 17 || tag == 18) {
        i += 4;
      } else {
        throw new Exception("Unsupported class-file tag " + tag);
      }
    }
    int thisIndex = u2(b, i + 2);
    String internal = utf[klass[thisIndex]];
    if (internal == null) throw new Exception("Could not read the class name.");
    return internal.replace('/', '.');
  }

  private static int u2(byte[] b, int i) {
    return ((b[i] & 255) << 8) | (b[i + 1] & 255);
  }

  private static List<JavaRun.FileOut> empty() {
    return new ArrayList<JavaRun.FileOut>();
  }

  private static JavaRun.Result fail(String msg) {
    return new JavaRun.Result(msg, empty(), 1);
  }

  /** Copy a program output file to Download. Android opens relative paths on "/", which is read-only. */
  static String publish(Context ctx, String name, byte[] data) {
    if (ctx == null || name == null || data == null) return null;
    String safe = name.replace('\\', '_').replace('/', '_');
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      try {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safe);
        v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeOf(safe));
        v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
        android.net.Uri uri = ctx.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri != null) {
          java.io.OutputStream os = ctx.getContentResolver().openOutputStream(uri);
          if (os != null) {
            try {
              os.write(data);
            } finally {
              os.close();
            }
            return "Download/" + safe;
          }
        }
      } catch (Throwable ignored) {}
    }
    try {
      File dir = ctx.getExternalFilesDir(null);
      if (dir == null) dir = ctx.getFilesDir();
      write(new File(dir, safe), data);
      return dir.getAbsolutePath() + "/" + safe;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static String mimeOf(String name) {
    String n = name.toLowerCase();
    if (n.endsWith(".mid") || n.endsWith(".midi")) return "audio/midi";
    if (n.endsWith(".wav")) return "audio/wav";
    if (n.endsWith(".txt")) return "text/plain";
    return "application/octet-stream";
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

  private static void writeStream(File file, InputStream in) throws Exception {
    File parent = file.getParentFile();
    if (parent != null && !parent.isDirectory()) parent.mkdirs();
    File tmp = new File(file.getAbsolutePath() + ".part");
    FileOutputStream out = new FileOutputStream(tmp);
    try {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    } finally {
      out.close();
    }
    if (file.exists() && !file.delete()) {
      /* replace below */
    }
    if (!tmp.renameTo(file)) {
      write(file, read(tmp));
      tmp.delete();
    }
  }

  private static byte[] read(File file) throws Exception {
    java.io.FileInputStream in = new java.io.FileInputStream(file);
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
    if (dir == null || !dir.exists()) return;
    File[] kids = dir.listFiles();
    if (kids != null) {
      for (File f : kids) {
        if (f.isDirectory()) delete(f);
        else f.delete();
      }
    }
    dir.delete();
  }
}
