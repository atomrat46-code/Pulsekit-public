package pulsekit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Runs Python, Java source, a .class, or a .jar. Used by the PyJav tab. */
public final class JavaRun {
  /** How long a Java program may run: SogniMusic waits on Sogni's queue for up to 15 minutes. */
  public static final long RUN_MS = 20 * 60 * 1000L;

  /** Files a program writes are kept up to 3 MB; audio (a Sogni track, a cut WAV) up to 50 MB. */
  public static long maxFileBytes(String name) {
    String low = name == null ? "" : name.toLowerCase();
    // Audio, pictures and video a program made (SogniMusic's track, SogniChat's tool results).
    if (low.matches(".*\\.(mp3|wav|wave|flac|m4a|ogg|aac|png|jpe?g|webp|gif|mp4|webm|mov|glb)$")) return 50000000L;
    return 3000000L;
  }

  public static final class FileOut {
    public final String name;
    public final byte[] bytes;

    public FileOut(String name, byte[] bytes) {
      this.name = name;
      this.bytes = bytes;
    }
  }

  public static final class Result {
    public final String log;
    public final List<FileOut> files;
    public final int code;

    public Result(String log, List<FileOut> files, int code) {
      this.log = log;
      this.files = files;
      this.code = code;
    }
  }

  public interface Listener {
    void onDone(Result result);
  }

  private JavaRun() {}

  public static void start(final String name, final String source, final byte[] bytes, final List<String> argv, final Listener listener) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        Result result = JavaRun.run(name, source, bytes, argv);
        if (listener != null) listener.onDone(result);
      }
    }, "pulsekit-pyjav").start();
  }

  public static List<String> split(String line) {
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

  /**
   * The app's kit settings for a program that mentions them (--bpm, --style, --genre, --bars,
   * --swing, and --key_file when a Sogni key file is set), then the extra args. A switch the
   * extra args already give is not added, so the command line shows it once (Params' --genre
   * "Rock Ballad", not also --genre House).
   */
  public static List<String> argvFor(String source, int bpm, String style, int bars, int swing, String extra) {
    List<String> argv = new ArrayList<String>();
    String src = source == null ? "" : source;
    List<String> given = split(extra);
    if (src.contains("--bpm") && !given.contains("--bpm")) {
      argv.add("--bpm");
      argv.add(Integer.toString(bpm));
    }
    if (src.contains("--style") && !given.contains("--style")) {
      argv.add("--style");
      argv.add(style == null ? "house" : style);
    }
    if (src.contains("--genre") && !given.contains("--genre")) {
      // The app's style by name (House, Drum & Bass...).
      Engine.Style st = Engine.styles().get(style == null ? "house" : style);
      argv.add("--genre");
      argv.add(st != null && st.label != null ? st.label : (style == null ? "house" : style));
    }
    if (src.contains("--bars") && !given.contains("--bars")) {
      argv.add("--bars");
      argv.add(Integer.toString(Math.max(1, bars)));
    }
    if (src.contains("--swing") && !given.contains("--swing")) {
      argv.add("--swing");
      argv.add(Integer.toString(Math.max(0, swing)));
    }
    String key = ApiKeys.path();
    if (key != null && src.contains("--key_file") && !given.contains("--key_file")) {
      // The key file from File > Drum Midi Settings.
      argv.add("--key_file");
      argv.add(key);
    }
    argv.addAll(given);
    return argv;
  }

  private static String publicClassName(String src) {
    if (src == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+)?class\\s+(\\w+)")
        .matcher(src);
    return m.find() ? m.group(1) : null;
  }

  public static byte[] zipEntry(byte[] zip, String want) {
    if (zip == null || want == null) return null;
    try {
      ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip));
      ZipEntry e;
      byte[] buf = new byte[4096];
      while ((e = zin.getNextEntry()) != null) {
        String n = e.getName();
        if (n != null && (n.equals(want) || n.endsWith("/" + want))) {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          int c;
          while ((c = zin.read(buf)) > 0) bos.write(buf, 0, c);
          zin.close();
          return bos.toByteArray();
        }
      }
      zin.close();
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  public static Result run(String name, String source, byte[] bytes, List<String> argv) {
    String n = name == null || name.trim().isEmpty() ? "script.py" : name.replaceAll("[\\\\/]", "_");
    String low = n.toLowerCase();
    if (low.endsWith(".prompt")) {
      PromptRun.Sheet sheet = PromptRun.parse(source);
      String arg = "";
      if (argv != null) {
        for (int i = 0; i < argv.size(); i++) {
          if ("--pk-run".equals(argv.get(i)) && i + 1 < argv.size()) arg = PromptRun.normalizeType(argv.get(i + 1));
        }
      }
      String type = sheet != null ? PromptRun.normalizeType(sheet.type) : "";
      if (type.length() == 0) type = arg;
      if (type.length() == 0) type = PromptRun.defaultMode(sheet != null ? sheet.body : source);
      String body = sheet != null && sheet.body != null ? sheet.body : (source == null ? "" : source);
      body = PromptRun.withoutDescription(sheet == null ? "" : sheet.description, body);
      List<String> args = stripRunFlag(argv);
      if ("python".equals(type)) return runPython(promptFile(n, ".py"), body, args);
      if ("java".equals(type)) {
        String cls = publicClassName(body);
        return runJava((cls == null || cls.length() == 0 ? "Main" : cls) + ".java", body, null, args);
      }
      if ("javascript".equals(type) || "typescript".equals(type)) {
        return runNode(promptFile(n, "typescript".equals(type) ? ".ts" : ".js"), body, args);
      }
      if ("powershell".equals(type)) return runTool(win() ? "powershell" : "pwsh", promptFile(n, ".ps1"), body, args);
      if ("ai".equals(type)) {
        if (androidVm()) {
          return new Result("AI prompt. PyJav cannot run this on Android.\nTest it in the Grok subsystem.", Collections.<FileOut>emptyList(), 0);
        }
        return PromptRun.run(source == null ? "" : source, "ai");
      }
      return PromptRun.run(source == null ? "" : source, type);
    }
    if (low.endsWith(".java") || low.endsWith(".jar") || low.endsWith(".class")) {
      return runJava(n, source, bytes, argv);
    }
    if (isNode(low)) return runNode(n, source == null ? "" : source, argv);
    return runPython(n, source == null ? "" : source, argv);
  }

  private static boolean isNode(String low) {
    return low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".cjs") || low.endsWith(".jsx")
        || low.endsWith(".ts") || low.endsWith(".mts") || low.endsWith(".tsx");
  }

  /** JavaScript or TypeScript through Node.js. npm installs imported packages, including @sogni-ai/sogni-client. */
  private static Result runNode(String name, String source, List<String> argv) {
    String node = findNode();
    if (node == null) {
      if (androidVm()) return runNodeOnAndroid(name, source, argv);
      return new Result(
          "Node.js was not found.\nInstall Node.js, then Run again. JavaScript, TypeScript, and npm use that Node.\nnodejs-mobile and nodejs-mobile-react-native are React Native hosts. They are not inside this app.",
          Collections.<FileOut>emptyList(),
          127);
    }
    String src = source == null ? "" : source;
    boolean ts = name.toLowerCase().endsWith(".ts") || name.toLowerCase().endsWith(".mts") || name.toLowerCase().endsWith(".tsx");
    java.util.LinkedHashSet<String> pkgs = nodePackages(src);
    File dir = null;
    try {
      dir = tempDir("pulsekit-node-");
      writeFile(new File(dir, name), src.getBytes(StandardCharsets.UTF_8));
      StringBuilder log = new StringBuilder();
      if (src.contains("nodejs-mobile")) {
        log.append("nodejs-mobile and nodejs-mobile-react-native host Node inside React Native. PyJav runs this file with Node.js.\n");
      }
      if (!pkgs.isEmpty()) {
        if (!works("npm", "--version")) {
          return new Result("npm was not found. It ships with Node.js.\nPackages: " + join(pkgs), Collections.<FileOut>emptyList(), 127);
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"name\":\"pulsekit-pyjav\",\"private\":true,\"type\":");
        json.append(src.contains("import ") || src.contains("export ") || name.endsWith(".mjs") ? "\"module\"" : "\"commonjs\"");
        json.append(",\"dependencies\":{");
        boolean first = true;
        for (String pkg : pkgs) {
          if (!first) json.append(',');
          first = false;
          json.append('"').append(pkg.replace("\"", "")).append("\":\"*\"");
        }
        json.append("}}");
        writeFile(new File(dir, "package.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        List<String> npm = new ArrayList<String>();
        npm.add("npm");
        npm.add("install");
        npm.add("--omit=dev");
        npm.add("--no-audit");
        npm.add("--no-fund");
        Result installed = exec(dir, npm, name, "$ npm install", 180000L);
        log.append(installed.log).append('\n');
        if (installed.code != 0) {
          return new Result(log.toString(), Collections.<FileOut>emptyList(), installed.code);
        }
      }
      List<String> cmd = new ArrayList<String>();
      cmd.add(node);
      if (ts) cmd.add("--experimental-strip-types");
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      String shown = "$ " + node + (ts ? " --experimental-strip-types " : " ") + name;
      if (argv != null) for (String a : argv) shown = shown + " " + a;
      java.util.Map<String, Long> stamps = stamps(argv);
      Result ran = exec(dir, cmd, name, shown, 120000L);
      log.append(ran.log);
      List<FileOut> files = new ArrayList<FileOut>();
      for (int i = 0; i < ran.files.size(); i++) {
        String fn = ran.files.get(i).name.toLowerCase();
        if ("package.json".equals(fn) || "package-lock.json".equals(fn)) continue;
        files.add(ran.files.get(i));
      }
      return withOutsideFiles(new Result(log.toString(), files, ran.code), argv, dir, stamps);
    } catch (Exception ex) {
      return new Result("Could not run Node.js: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  private static Result runNodeOnAndroid(String name, String source, List<String> argv) {
    try {
      return (Result) Class.forName("pulsekit.ArtNode")
          .getMethod("run", String.class, String.class, List.class)
          .invoke(null, name, source, argv);
    } catch (java.lang.reflect.InvocationTargetException ex) {
      Throwable c = ex.getCause();
      String m = c == null || c.getMessage() == null ? String.valueOf(c) : c.getMessage();
      return new Result("Could not run Node in Termux: " + m, Collections.<FileOut>emptyList(), 1);
    } catch (ClassNotFoundException ex) {
      return new Result("Termux Node support is not in this build.", Collections.<FileOut>emptyList(), 1);
    } catch (Exception ex) {
      return new Result("Could not run Node in Termux: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    }
  }

  private static String join(java.util.Set<String> pkgs) {
    StringBuilder sb = new StringBuilder();
    for (String pkg : pkgs) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(pkg);
    }
    return sb.toString();
  }

  static java.util.LinkedHashSet<String> nodePackages(String src) {
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
    Matcher m = Pattern.compile("(?:import|export)\\s+(?:[^'\"\\n]*?\\s+from\\s+)?['\"]([^'\"]+)['\"]|require\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)|import\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)").matcher(src);
    while (m.find()) {
      String spec = m.group(1);
      if (spec == null) spec = m.group(2);
      if (spec == null) spec = m.group(3);
      String pkg = npmName(spec);
      if (pkg != null) out.add(pkg);
    }
    return out;
  }

  private static String npmName(String spec) {
    if (spec == null) return null;
    if (spec.startsWith(".") || spec.startsWith("/") || spec.startsWith("node:")) return null;
    if ("nodejs-mobile".equals(spec) || "nodejs-mobile-react-native".equals(spec)) return null;
    if (spec.startsWith("@")) {
      int slash = spec.indexOf('/');
      if (slash < 1 || slash == spec.length() - 1) return null;
      int next = spec.indexOf('/', slash + 1);
      return next < 0 ? spec : spec.substring(0, next);
    }
    int slash = spec.indexOf('/');
    String name = slash < 0 ? spec : spec.substring(0, slash);
    if (name.length() == 0 || name.indexOf('.') >= 0 && name.startsWith("node")) return null;
    if ("fs".equals(name) || "path".equals(name) || "os".equals(name) || "url".equals(name) || "crypto".equals(name)
        || "http".equals(name) || "https".equals(name) || "util".equals(name) || "stream".equals(name)
        || "buffer".equals(name) || "events".equals(name) || "child_process".equals(name) || "assert".equals(name)) return null;
    return name;
  }

  private static String findNode() {
    if (works("node", "--version")) return "node";
    if (works("nodejs", "--version")) return "nodejs";
    return null;
  }

  private static boolean win() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private static String promptFile(String name, String ext) {
    String base = name == null ? "prompt" : name;
    int dot = base.lastIndexOf('.');
    if (dot > 0) base = base.substring(0, dot);
    if (base.length() == 0) base = "prompt";
    return base + ext;
  }

  private static List<String> stripRunFlag(List<String> argv) {
    List<String> out = new ArrayList<String>();
    if (argv == null) return out;
    for (int i = 0; i < argv.size(); i++) {
      if ("--pk-run".equals(argv.get(i))) {
        i++;
        continue;
      }
      out.add(argv.get(i));
    }
    return out;
  }

  private static Result runTool(String bin, String name, String source, List<String> argv) {
    if (androidVm()) {
      return new Result(bin + " is not on this phone. Python and Java run inside the app.", Collections.<FileOut>emptyList(), 127);
    }
    if (!works(bin, "--version") && !works(bin, "-Version")) {
      return new Result(bin + " was not found.", Collections.<FileOut>emptyList(), 127);
    }
    File dir = null;
    try {
      dir = tempDir("pulsekit-tool-");
      writeFile(new File(dir, name), (source == null ? "" : source).getBytes(StandardCharsets.UTF_8));
      List<String> cmd = new ArrayList<String>();
      cmd.add(bin);
      if ("powershell".equals(bin) || "pwsh".equals(bin)) {
        cmd.add("-NoProfile");
        cmd.add("-File");
      }
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      return exec(dir, cmd, name, "$ " + bin + " " + name);
    } catch (Exception ex) {
      return new Result("Could not run " + bin + ": " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  private static Result runPython(String name, String source, List<String> argv) {
    if (androidVm()) {
      try {
        return (Result) Class.forName("pulsekit.ArtPy")
            .getMethod("run", String.class, String.class, List.class)
            .invoke(null, name, source, argv);
      } catch (java.lang.reflect.InvocationTargetException ex) {
        Throwable c = ex.getCause();
        String m = c == null || c.getMessage() == null ? String.valueOf(c) : c.getMessage();
        return new Result("Could not run Python on Android: " + m, Collections.<FileOut>emptyList(), 1);
      } catch (ClassNotFoundException ex) {
        return new Result("Android Python runtime is not in this build.", Collections.<FileOut>emptyList(), 1);
      } catch (Exception ex) {
        return new Result("Could not run Python on Android: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
      }
    }
    String py = findPython();
    if (py == null) {
      return new Result(
          "Python 3 was not found.\nInstall Python 3, then Run again. On a phone, use the web or desktop Pulsekit.",
          Collections.<FileOut>emptyList(),
          127);
    }
    if (!name.toLowerCase().endsWith(".py")) name = name + ".py";
    File dir = null;
    try {
      dir = tempDir("pulsekit-py-");
      writeFile(new File(dir, name), source.getBytes(StandardCharsets.UTF_8));
      List<String> cmd = new ArrayList<String>();
      if ("py".equals(py)) {
        cmd.add("py");
        cmd.add("-3");
      } else {
        cmd.add(py);
      }
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      String shown = "$ " + py + " " + name;
      if (argv != null) for (String a : argv) shown = shown + " " + a;
      return exec(dir, cmd, name, shown);
    } catch (Exception ex) {
      return new Result("Could not run Python: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  private static Result runJava(String name, String source, byte[] bytes, List<String> argv) {
    if (androidVm()) {
      try {
        return (Result) Class.forName("pulsekit.ArtJava")
            .getMethod("run", String.class, String.class, byte[].class, List.class)
            .invoke(null, name, source, bytes, argv);
      } catch (java.lang.reflect.InvocationTargetException ex) {
        Throwable c = ex.getCause();
        String m = c == null || c.getMessage() == null ? String.valueOf(c) : c.getMessage();
        return new Result("Could not run Java on ART: " + m, Collections.<FileOut>emptyList(), 1);
      } catch (ClassNotFoundException ex) {
        return new Result("Android Java runtime is not in this build.", Collections.<FileOut>emptyList(), 1);
      } catch (Exception ex) {
        return new Result("Could not run Java on ART: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
      }
    }
    String low = name.toLowerCase();
    File dir = null;
    try {
      dir = tempDir("pulsekit-jav-");
      List<String> cmd = new ArrayList<String>();
      String shown;
      if (low.endsWith(".java")) {
        if (!works("javac", "-version")) {
          return new Result("javac was not found.\nInstall a JDK, then Run again.", Collections.<FileOut>emptyList(), 127);
        }
        String src = source == null ? "" : source;
        String declared = publicClassName(src);
        String fileName = declared != null ? declared + ".java" : name;
        writeFile(new File(dir, fileName), src.getBytes(StandardCharsets.UTF_8));
        List<String> jc = new ArrayList<String>();
        jc.add("javac");
        jc.add("-encoding");
        jc.add("UTF-8");
        jc.add(fileName);
        Result compiled = exec(dir, jc, fileName, "$ javac " + fileName);
        if (compiled.code != 0) return compiled;
        if (!works("java", "-version")) {
          return new Result(compiled.log + "\njava was not found after compile.", Collections.<FileOut>emptyList(), 127);
        }
        String cls = declared != null ? declared : name.substring(0, name.length() - 5);
        cmd.add("java");
        cmd.add("-cp");
        cmd.add(dir.getAbsolutePath());
        cmd.add(cls);
        shown = "$ java " + cls;
      } else if (low.endsWith(".jar")) {
        if (bytes == null || bytes.length == 0) {
          return new Result("No bytes for " + name, Collections.<FileOut>emptyList(), 1);
        }
        if (!works("java", "-version")) {
          return new Result("java was not found.\nInstall a JDK, then Run again.", Collections.<FileOut>emptyList(), 127);
        }
        String main = mainClass(bytes);
        if (main == null || main.isEmpty()) {
          return new Result(
              "No Main-Class in " + name + ".\nAdd Main-Class to META-INF/MANIFEST.MF.",
              Collections.<FileOut>emptyList(),
              1);
        }
        writeFile(new File(dir, name), bytes);
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(name);
        shown = "$ java -jar " + name;
      } else {
        if (bytes == null || bytes.length == 0) {
          return new Result("No bytes for " + name, Collections.<FileOut>emptyList(), 1);
        }
        if (!works("java", "-version")) {
          return new Result("java was not found.\nInstall a JDK, then Run again.", Collections.<FileOut>emptyList(), 127);
        }
        writeFile(new File(dir, name), bytes);
        String cls = name.substring(0, name.length() - 6);
        cmd.add("java");
        cmd.add("-cp");
        cmd.add(dir.getAbsolutePath());
        cmd.add(cls);
        shown = "$ java " + cls;
      }
      if (argv != null) {
        cmd.addAll(argv);
        for (String a : argv) shown = shown + " " + a;
      }
      java.util.Map<String, Long> stamps = stamps(argv);
      Result ran = withOutsideFiles(exec(dir, cmd, name, shown, RUN_MS), argv, dir, stamps);
      if (low.endsWith(".java")) {
        List<FileOut> kept = new ArrayList<FileOut>();
        for (FileOut f : ran.files) {
          if (f.name.toLowerCase().endsWith(".class")) continue;
          kept.add(f);
        }
        return new Result(ran.log, kept, ran.code);
      }
      return ran;
    } catch (Exception ex) {
      return new Result("Could not run Java: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  static String mainClass(byte[] jar) {
    byte[] mf = zipEntry(jar, "META-INF/MANIFEST.MF");
    if (mf == null) return null;
    String text = new String(mf, StandardCharsets.UTF_8);
    StringBuilder cur = new StringBuilder();
    String found = null;
    for (String line : text.split("\n")) {
      String t = line.replace("\r", "");
      if (t.startsWith(" ")) {
        cur.append(t.substring(1).trim());
        continue;
      }
      if (cur.length() > 0 && cur.toString().startsWith("Main-Class:")) {
        found = cur.substring("Main-Class:".length()).trim();
      }
      cur.setLength(0);
      cur.append(t.trim());
    }
    if (found == null && cur.length() > 0 && cur.toString().startsWith("Main-Class:")) {
      found = cur.substring("Main-Class:".length()).trim();
    }
    return found == null || found.isEmpty() ? null : found;
  }

  private static boolean androidVm() {
    try {
      Class.forName("android.os.Build");
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  /** Each argument that names a file, with its modification time before the run (0 when there is none yet). */
  private static java.util.Map<String, Long> stamps(List<String> argv) {
    java.util.Map<String, Long> out = new java.util.HashMap<String, Long>();
    if (argv == null) return out;
    for (String a : argv) {
      File f = new File(a);
      out.put(a, Long.valueOf(f.isFile() ? f.lastModified() : 0L));
    }
    return out;
  }

  /**
   * MIDI written to an absolute extra-arg path, outside the temp folder. Only a file the run made or
   * changed (`before`: stamps from before it): a MIDI given as input (SogniChat --file song.mid) is
   * not the program's output.
   */
  private static Result withOutsideFiles(Result ran, List<String> argv, File dir, java.util.Map<String, Long> before) {
    if (ran == null || argv == null || argv.isEmpty()) return ran;
    List<FileOut> files = new ArrayList<FileOut>(ran.files);
    for (int i = 0; i < argv.size(); i++) {
      File f = new File(argv.get(i));
      if (!f.isFile()) continue;
      if (dir != null && dir.equals(f.getParentFile())) continue;
      Long was = before.get(argv.get(i));
      if (was != null && was.longValue() != 0L && was.longValue() == f.lastModified()) continue;
      String low = f.getName().toLowerCase();
      if (!low.endsWith(".mid") && !low.endsWith(".midi") && !low.endsWith(".sng")) continue;
      boolean seen = false;
      for (int j = 0; j < files.size(); j++) {
        if (f.getName().equals(files.get(j).name)) seen = true;
      }
      if (seen || f.length() > 3000000L) continue;
      try {
        files.add(new FileOut(f.getName(), readFile(f)));
      } catch (Exception ignored) {}
    }
    return new Result(ran.log, files, ran.code);
  }

  private static Result exec(File dir, List<String> cmd, String skipName, String header) throws Exception {
    return exec(dir, cmd, skipName, header, 120000L);
  }

  private static Result exec(File dir, List<String> cmd, String skipName, String header, long waitMs) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(dir);
    pb.redirectErrorStream(true);
    Process proc = pb.start();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    InputStream in = proc.getInputStream();
    byte[] buf = new byte[4096];
    long deadline = System.currentTimeMillis() + waitMs;
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
      bos.write(("\nTimed out (" + (waitMs / 1000) + "s).").getBytes(StandardCharsets.UTF_8));
    }
    int n;
    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
    int code = proc.isAlive() ? 124 : proc.exitValue();
    List<FileOut> files = new ArrayList<FileOut>();
    File[] kids = dir.listFiles();
    if (kids != null) {
      for (File f : kids) {
        if (!f.isFile()) continue;
        String fn = f.getName();
        if (fn.equals(skipName) || fn.startsWith(".")) continue;
        if (f.length() > maxFileBytes(fn)) continue;
        byte[] data = readFile(f);
        files.add(new FileOut(fn, data));
      }
    }
    String log = header + "\n" + bos.toString("UTF-8");
    return new Result(log, files, code);
  }

  private static String findPython() {
    if (works("python3", "--version")) return "python3";
    if (works("python", "--version")) return "python";
    if (works("py", "-3", "--version")) return "py";
    return null;
  }

  private static boolean works(String... cmd) {
    try {
      List<String> all = new ArrayList<String>();
      Collections.addAll(all, cmd);
      Process p = new ProcessBuilder(all).redirectErrorStream(true).start();
      if (!p.waitFor(4, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        return false;
      }
      return p.exitValue() == 0;
    } catch (Exception ex) {
      return false;
    }
  }

  private static void deleteDir(File dir) {
    if (dir == null) return;
    try {
      File[] kids = dir.listFiles();
      if (kids != null) {
        for (File f : kids) {
          if (f.isDirectory()) deleteDir(f);
          else f.delete();
        }
      }
      dir.delete();
    } catch (Exception ignored) {
      /* leave temp */
    }
  }

  private static File tempDir(String prefix) throws Exception {
    File base = new File(System.getProperty("java.io.tmpdir"));
    File dir = new File(base, prefix + System.nanoTime());
    if (!dir.mkdirs()) throw new Exception("Could not create a temp folder");
    return dir;
  }

  private static void writeFile(File file, byte[] data) throws Exception {
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  private static byte[] readFile(File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
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
}
