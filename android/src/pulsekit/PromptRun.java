package pulsekit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Read, write, and run a .prompt file (PKPROMPT1). */
public final class PromptRun {
  private PromptRun() {}

  public static final class Sheet {
    public String name = "";
    public String out1 = "";
    public String out2 = "";
    public String ref1 = "";
    public String ref2 = "";
    public String category = "";
    public String version = "";
    public String model = "";
    public String result = "";
    public String type = "";
    public String description = "";
    public String body = "";
  }

  private static final String CMD =
      " dir del erase copy move ren rename cls ipconfig ver xcopy robocopy attrib vol md rd ";

  private static final String COMMANDS =
      " echo printf ls dir cd pwd cat mkdir md rm rmdir rd cp mv touch git npm node python python3 py java javac "
          + "bash sh cmd powershell pwsh cls clear chmod chown kill ps sleep tar zip unzip ping hostname sudo make gcc "
          + "awk sed docker ssh scp curl wget grep find head tail wc sort uniq whoami uname env which where df du xargs "
          + "tee basename dirname ln pushd popd export alias seq expr bc umask cal tree base64 md5sum sha256sum "
          + "ipconfig ifconfig copy move del erase ren rename xcopy robocopy attrib ver ";

  public static String fileName(String name) {
    if (name == null) return "";
    String base = name.trim().replaceAll("[\\\\/:*?\"<>|]", " ").replaceAll("\\s+", " ").trim();
    if (base.toLowerCase().endsWith(".prompt")) base = base.substring(0, base.length() - 7).trim();
    if (base.isEmpty()) return "";
    return base + ".prompt";
  }

  public static String encode(String name, String out1, String out2, String ref1, String ref2, String body) {
    return encode(name, out1, out2, ref1, ref2, "", "", "", "", "", body);
  }

  public static String encode(String name, String out1, String out2, String ref1, String ref2, String category, String version, String model, String result, String body) {
    return encode(name, out1, out2, ref1, ref2, category, version, model, result, "", body);
  }

  public static String encode(String name, String out1, String out2, String ref1, String ref2, String category, String version, String model, String result, String type, String body) {
    StringBuilder sb = new StringBuilder();
    sb.append("PKPROMPT1\n");
    sb.append(one(name)).append('\n');
    sb.append(one(out1)).append('\n');
    sb.append(one(out2)).append('\n');
    sb.append(one(ref1)).append('\n');
    sb.append(one(ref2)).append('\n');
    if (category != null && category.trim().length() > 0) sb.append("Category: ").append(one(category)).append('\n');
    if (version != null && version.trim().length() > 0) sb.append("Version: ").append(one(version)).append('\n');
    if (model != null && model.trim().length() > 0) sb.append("Model: ").append(one(model)).append('\n');
    sb.append("Reference file 1: ").append(one(ref1)).append('\n');
    sb.append("Reference file 2: ").append(one(ref2)).append('\n');
    if (result != null && result.trim().length() > 0) sb.append("Result file: ").append(one(result)).append('\n');
    String kind = normalizeType(type);
    if (kind.length() > 0) sb.append("Type: ").append(kind).append('\n');
    sb.append("---\n");
    sb.append(body == null ? "" : body.replace("\r\n", "\n").replace("\r", "\n"));
    return sb.toString();
  }

  public static Sheet parse(String text) {
    if (text == null) return null;
    String norm = text.replace("\r\n", "\n").replace("\r", "\n");
    if (!norm.startsWith("PKPROMPT1\n") && !"PKPROMPT1".equals(norm)) return null;
    String[] lines = norm.split("\n", -1);
    int dash = -1;
    for (int i = 1; i < lines.length; i++) {
      if ("---".equals(lines[i])) {
        dash = i;
        break;
      }
    }
    if (dash < 0) return null;
    Sheet sheet = new Sheet();
    StringBuilder body = new StringBuilder();
    for (int i = dash + 1; i < lines.length; i++) {
      if (i > dash + 1) body.append('\n');
      body.append(lines[i]);
    }
    sheet.body = body.toString();
    int fields = dash - 1;
    if (fields >= 5) {
      sheet.name = lines[1];
      sheet.out1 = lines[2];
      sheet.out2 = lines[3];
      sheet.ref1 = lines[4];
      sheet.ref2 = lines[5];
      readLabels(sheet, lines, 6, dash);
      return sheet;
    }
    if (fields >= 4) {
      sheet.out1 = lines[1];
      sheet.out2 = lines[2];
      sheet.ref1 = lines[3];
      sheet.ref2 = lines[4];
      return sheet;
    }
    return null;
  }

  /** Run as bash, cmd, or AI. "auto" picks from the text. */
  public static JavaRun.Result run(String text) {
    return run(text, "auto");
  }

  public static JavaRun.Result run(String text, String mode) {
    Sheet sheet = parse(text);
    String body = sheet != null ? sheet.body : (text == null ? "" : text);
    String title = sheet == null ? "prompt.prompt" : fileName(sheet.name);
    if (title.isEmpty()) title = "prompt.prompt";
    if (body == null) body = "";
    body = withoutDescription(sheet == null ? "" : sheet.description, body);
    if (body.trim().isEmpty()) {
      return new JavaRun.Result("The prompt is empty.", new ArrayList<JavaRun.FileOut>(), 0);
    }
    String chosen = mode == null ? "" : mode.trim().toLowerCase();
    if (!"bash".equals(chosen) && !"cmd".equals(chosen) && !"ai".equals(chosen)) chosen = defaultMode(body);
    if ("ai".equals(chosen)) return runAi(body, title);
    return runShell(body, title, chosen);
  }

  public static String normalizeType(String raw) {
    if (raw == null) return "";
    String t = raw.trim().toLowerCase();
    if ("py".equals(t) || "python3".equals(t)) return "python";
    if ("js".equals(t) || "node".equals(t) || "nodejs".equals(t) || "node.js".equals(t) || "npm".equals(t)) return "javascript";
    if ("ts".equals(t) || "tsx".equals(t) || "typescript".equals(t)) return "typescript";
    if ("pwsh".equals(t) || "ps1".equals(t)) return "powershell";
    if ("sh".equals(t)) return "bash";
    if ("bat".equals(t) || "batch".equals(t)) return "cmd";
    if ("bash".equals(t) || "cmd".equals(t) || "python".equals(t) || "java".equals(t) || "javascript".equals(t) || "typescript".equals(t) || "powershell".equals(t) || "ai".equals(t)) return t;
    return "";
  }

  public static boolean codeType(String type) {
    String t = normalizeType(type);
    return "bash".equals(t) || "cmd".equals(t) || "python".equals(t) || "java".equals(t) || "javascript".equals(t) || "typescript".equals(t) || "powershell".equals(t);
  }

  /** Category, type, and whether PyJav can run it. grok/sogni/claude are installed subsystems. */
  public static String report(String category, String type, boolean grok, boolean sogni, boolean claude) {
    String cat = category == null ? "" : category.trim();
    if (cat.length() == 0) cat = "none";
    String kind = normalizeType(type);
    if (kind.length() == 0 && type != null && "ai".equals(type.trim().toLowerCase())) kind = "ai";
    if (kind.length() == 0) kind = "none";
    StringBuilder sb = new StringBuilder();
    sb.append("Category: ").append(cat).append('\n');
    sb.append("Type: ").append(kind).append('\n');
    if ("ai".equals(kind)) {
      sb.append("Grok subsystem: ").append(grok ? "present" : "not present").append('\n');
      sb.append("Sogni Chat subsystem: ").append(sogni ? "present" : "not present").append('\n');
      sb.append("Claude subsystem: ").append(claude ? "present" : "not present");
      if (!grok && !sogni && !claude) sb.append("\nAI prompt. PyJav cannot run this. Grok, Sogni, and Claude are not present.");
    } else if (codeType(kind) || "code".equalsIgnoreCase(cat)) {
      sb.append("Runnable");
    }
    return sb.toString();
  }

  /** Type stored on the prompt, otherwise a guess from the text. */
  public static String runMode(String full, Sheet sheet) {
    if (sheet == null) sheet = parse(full);
    String typed = sheet == null ? "" : normalizeType(sheet.type);
    if (typed.length() > 0) return typed;
    String body = sheet != null && sheet.body != null ? sheet.body : full;
    return defaultMode(body);
  }

  public static String defaultMode(String body) {
    if (body == null || !isShell(body)) return "ai";
    String text = body.replace("\r\n", "\n").replace("\r", "\n");
    String[] lines = text.split("\n", -1);
    String first = "";
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      first = line;
      break;
    }
    String token = first.isEmpty() ? "" : first.split("\\s+", 2)[0].replace("\"", "").replace("'", "");
    int slash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
    String base = (slash >= 0 ? token.substring(slash + 1) : token).toLowerCase();
    if (base.endsWith(".exe")) base = base.substring(0, base.length() - 4);
    if (CMD.indexOf(" " + base + " ") >= 0) return "cmd";
    return "bash";
  }

  private static boolean isShell(String body) {
    String text = body.replace("\r\n", "\n").replace("\r", "\n").trim();
    if (text.isEmpty()) return false;
    if (text.startsWith("#!")) return true;
    String[] lines = text.split("\n", -1);
    String first = "";
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      first = line;
      break;
    }
    if (first.isEmpty()) return false;
    if (first.matches("[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*\\S.*") && !sentence(first)) return true;
    if (sentence(first)) return false;
    if (first.indexOf('|') >= 0 || first.indexOf('&') >= 0 || first.indexOf(';') >= 0
        || first.indexOf('<') >= 0 || first.indexOf('>') >= 0 || first.indexOf('`') >= 0
        || first.indexOf("$(") >= 0) return true;
    String token = first.split("\\s+", 2)[0].replace("\"", "").replace("'", "");
    if (token.startsWith("./") || token.startsWith("../")) return true;
    if (token.startsWith("/") && token.indexOf(' ') < 0) return true;
    if (token.length() > 2 && token.charAt(1) == ':' && (token.charAt(2) == '\\' || token.charAt(2) == '/')) return true;
    int slash = Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\'));
    String base = (slash >= 0 ? token.substring(slash + 1) : token).toLowerCase();
    if (base.endsWith(".exe")) base = base.substring(0, base.length() - 4);
    return COMMANDS.indexOf(" " + base + " ") >= 0;
  }

  private static boolean sentence(String line) {
    String t = line.trim();
    String[] words = t.split("\\s+");
    if (words.length < 3) return false;
    if (t.matches(".*(\\s|^)-{1,2}[A-Za-z].*")) return false;
    if (t.indexOf('|') >= 0 || t.indexOf('&') >= 0 || t.indexOf(';') >= 0
        || t.indexOf('<') >= 0 || t.indexOf('>') >= 0 || t.indexOf('`') >= 0 || t.indexOf("$(") >= 0) return false;
    if (t.endsWith(".") || t.endsWith("?") || t.endsWith("!")) return true;
    if (words.length < 4) return false;
    String padded = " " + t.toLowerCase().replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ") + " ";
    String[] marks = {
      " a ", " an ", " the ", " to ", " of ", " for ", " and ", " please ", " write ", " create ",
      " make ", " explain ", " about ", " with ", " from ", " this ", " that ", " into ", " on ", " in "
    };
    for (int i = 0; i < marks.length; i++) {
      if (padded.indexOf(marks[i]) >= 0) return true;
    }
    return false;
  }

  private static JavaRun.Result runShell(String script, String title, String which) {
    boolean cmd = "cmd".equals(which);
    boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
    String bin;
    if (cmd) {
      if (!win) {
        return new JavaRun.Result("cmd is not on this system.", new ArrayList<JavaRun.FileOut>(), 127);
      }
      String com = System.getenv("ComSpec");
      bin = com != null && com.length() > 0 ? com : "cmd.exe";
    } else if (new File("/bin/bash").isFile()) {
      bin = "/bin/bash";
    } else {
      return new JavaRun.Result("bash is not on this system.", new ArrayList<JavaRun.FileOut>(), 127);
    }
    String shown = cmd ? "cmd" : "bash";
    String fileName = cmd ? "prompt.cmd" : "prompt.sh";
    File dir = new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-sh-" + System.nanoTime());
    if (!dir.mkdirs()) {
      String missing = cmd ? "cmd is not on this system." : "bash is not on this system.";
      return new JavaRun.Result(missing, new ArrayList<JavaRun.FileOut>(), 127);
    }
    try {
      File scriptFile = new File(dir, fileName);
      FileOutputStream out = new FileOutputStream(scriptFile);
      try {
        String body = script.endsWith("\n") ? script : script + "\n";
        out.write(body.getBytes(StandardCharsets.UTF_8));
      } finally {
        out.close();
      }
      List<String> command = new ArrayList<String>();
      if (cmd) {
        command.add(bin);
        command.add("/d");
        command.add("/c");
        command.add(fileName);
      } else {
        command.add(bin);
        command.add(fileName);
      }
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(dir);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      String output = readProcess(p);
      int code;
      try {
        code = p.exitValue();
      } catch (IllegalThreadStateException ex) {
        code = 1;
      }
      List<JavaRun.FileOut> files = collect(dir, fileName);
      String log = "$ " + shown + "\n" + output;
      if (code == 0) log = "Executed · " + title + "\n" + log;
      return new JavaRun.Result(log.replaceAll("\\s+$", ""), files, code);
    } catch (Exception ex) {
      String missing = cmd ? "cmd is not on this system." : "bash is not on this system.";
      return new JavaRun.Result(missing, new ArrayList<JavaRun.FileOut>(), 127);
    } finally {
      deleteAll(dir);
    }
  }

  private static JavaRun.Result runAi(String prompt, String title) {
    String key = System.getenv("XAI_API_KEY");
    if (key == null || key.trim().length() == 0) {
      return new JavaRun.Result("AI prompt. PyJav cannot run this. Grok, Sogni, and Claude are not present.", new ArrayList<JavaRun.FileOut>(), 0);
    }
    String clipped = prompt.length() > 8000 ? prompt.substring(0, 8000) : prompt;
    HttpURLConnection conn = null;
    try {
      URL url = new URL("https://api.x.ai/v1/chat/completions");
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("POST");
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(40000);
      conn.setDoOutput(true);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("Authorization", "Bearer " + key.trim());
      byte[] raw = ("{\"model\":\"grok-4.5\",\"max_tokens\":400,\"messages\":[{\"role\":\"user\",\"content\":"
          + jsonString(clipped) + "}]}").getBytes(StandardCharsets.UTF_8);
      conn.setFixedLengthStreamingMode(raw.length);
      OutputStream os = conn.getOutputStream();
      try {
        os.write(raw);
      } finally {
        os.close();
      }
      int status = conn.getResponseCode();
      InputStream in = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
      String resp = readStream(in);
      if (status < 200 || status >= 300) {
        return new JavaRun.Result("AI prompt. This system cannot execute it.", new ArrayList<JavaRun.FileOut>(), 1);
      }
      String text = extractContent(resp);
      if (text.length() > 8000) text = text.substring(0, 8000);
      String log = ("Executed · " + title + "\n$ AI\n" + text).replaceAll("\\s+$", "");
      return new JavaRun.Result(log, new ArrayList<JavaRun.FileOut>(), 0);
    } catch (Exception ex) {
      return new JavaRun.Result("AI prompt. This system cannot execute it.", new ArrayList<JavaRun.FileOut>(), 1);
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private static String jsonString(String s) {
    StringBuilder sb = new StringBuilder();
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"' || c == '\\') sb.append('\\').append(c);
      else if (c == '\n') sb.append("\\n");
      else if (c == '\r') sb.append("\\r");
      else if (c == '\t') sb.append("\\t");
      else if (c < 32) sb.append(String.format("\\u%04x", Integer.valueOf(c)));
      else sb.append(c);
    }
    sb.append('"');
    return sb.toString();
  }

  private static String extractContent(String json) {
    if (json == null) return "";
    int at = json.indexOf("\"content\"");
    if (at < 0) return "";
    int colon = json.indexOf(':', at);
    if (colon < 0) return "";
    int i = colon + 1;
    while (i < json.length() && json.charAt(i) <= ' ') i++;
    if (i >= json.length() || json.charAt(i) != '"') return "";
    i++;
    StringBuilder sb = new StringBuilder();
    while (i < json.length()) {
      char c = json.charAt(i);
      if (c == '"') break;
      if (c == '\\' && i + 1 < json.length()) {
        char n = json.charAt(i + 1);
        if (n == 'n') sb.append('\n');
        else if (n == 'r') sb.append('\r');
        else if (n == 't') sb.append('\t');
        else if (n == 'u' && i + 5 < json.length()) {
          try {
            sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
            i += 6;
            continue;
          } catch (Exception ignored) {
            sb.append(n);
          }
        } else sb.append(n);
        i += 2;
        continue;
      }
      sb.append(c);
      i++;
    }
    return sb.toString();
  }

  private static String readStream(InputStream in) throws Exception {
    if (in == null) return "";
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0 && bos.size() < 65536) {
        int room = 65536 - bos.size();
        bos.write(buf, 0, n < room ? n : room);
      }
      return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    } finally {
      in.close();
    }
  }

  private static String readProcess(final Process p) throws Exception {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    final InputStream in = p.getInputStream();
    Thread reader = new Thread(new Runnable() {
      public void run() {
        byte[] buf = new byte[4096];
        try {
          int n;
          while ((n = in.read(buf)) > 0) {
            synchronized (bos) {
              if (bos.size() < 65536) {
                int room = 65536 - bos.size();
                bos.write(buf, 0, n < room ? n : room);
              }
            }
          }
        } catch (Exception ignored) {}
      }
    });
    reader.setDaemon(true);
    reader.start();
    boolean finished = procWait(p, 20000L);
    if (!finished) {
      p.destroy();
      try {
        procWait(p, 2000L);
      } catch (Exception ignored) {}
      p.destroy();
    }
    reader.join(1500);
    String text;
    synchronized (bos) {
      text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
    if (!finished) text = text + (text.endsWith("\n") || text.isEmpty() ? "" : "\n") + "Timed out.";
    return text;
  }

  private static List<JavaRun.FileOut> collect(File dir, String skip) {
    List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>();
    File[] list = dir.listFiles();
    if (list == null) return files;
    for (int i = 0; i < list.length; i++) {
      File f = list[i];
      if (!f.isFile() || f.getName().equals(skip) || f.getName().startsWith(".")) continue;
      if (f.length() <= 0 || f.length() > 3000000L) continue;
      try {
        files.add(new JavaRun.FileOut(f.getName(), readAll(f)));
      } catch (Exception ignored) {}
    }
    return files;
  }

  private static byte[] readAll(File f) throws Exception {
    FileInputStream in = new FileInputStream(f);
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

  private static void deleteAll(File f) {
    if (f == null || !f.exists()) return;
    if (f.isDirectory()) {
      File[] kids = f.listFiles();
      if (kids != null) {
        for (int i = 0; i < kids.length; i++) deleteAll(kids[i]);
      }
    }
    f.delete();
  }

  /** File name for a run. A defined result name is kept. Otherwise the category, model, or prompt picks the extension, and an unclear format becomes a log. */
  public static String chooseOutputName(String title, String category, String type, String model, String body, String defined) {
    String given = defined == null ? "" : defined.trim();
    if (hasExt(given)) return safeFile(given);
    String ext = guessExt(category, type, model, body);
    String base = given.length() > 0 ? stripExt(given) : stem(title);
    if (base.length() == 0) base = "output";
    return safeFile(base + ext);
  }

  public static boolean logOutput(String fileName) {
    return fileName != null && fileName.toLowerCase().endsWith(".log");
  }

  private static boolean hasExt(String name) {
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    String file = slash >= 0 ? name.substring(slash + 1) : name;
    int dot = file.lastIndexOf('.');
    return dot > 0 && dot < file.length() - 1;
  }

  private static String stripExt(String name) {
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    String file = slash >= 0 ? name.substring(slash + 1) : name;
    int dot = file.lastIndexOf('.');
    return dot > 0 ? file.substring(0, dot) : file;
  }

  private static String stem(String title) {
    String name = title == null ? "" : title.trim();
    if (name.toLowerCase().endsWith(".prompt")) name = name.substring(0, name.length() - 7);
    return name;
  }

  private static String guessExt(String category, String type, String model, String body) {
    String cat = lower(category);
    String kind = lower(type);
    String blob = lower(model) + "\n" + lower(body);
    if (cat.contains("image")) return firstExt(blob, new String[] {".jpg", ".jpeg", ".webp", ".gif", ".png"}, ".png");
    if (cat.contains("video")) return firstExt(blob, new String[] {".webm", ".mov", ".mp4"}, ".mp4");
    if (cat.contains("music") || cat.contains("sound") || cat.contains("audio")) return firstExt(blob, new String[] {".mp3", ".mid", ".midi", ".wav"}, ".wav");
    if (cat.contains("code") || codeType(kind)) {
      String hinted = outputHint(body);
      return hinted == null ? ".log" : hinted;
    }
    String hinted = outputHint(body);
    if (hinted != null) return hinted;
    String modelLow = lower(model);
    if (modelLow.contains("krea") || modelLow.contains("image")) return ".png";
    if (modelLow.contains("minimax") || modelLow.contains("video")) return ".mp4";
    if (modelLow.contains("music") || modelLow.contains("audio")) return ".wav";
    hinted = firstExt(blob, new String[] {".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".webm", ".mov", ".mp3", ".wav", ".mid", ".midi"}, null);
    return hinted == null ? ".log" : hinted;
  }

  private static String outputHint(String body) {
    if (body == null) return null;
    int i = 0;
    while (i < body.length()) {
      int a = body.indexOf('<', i);
      if (a < 0) return null;
      int b = body.indexOf('>', a + 1);
      if (b < 0) return null;
      String name = body.substring(a + 1, b).trim().toLowerCase();
      int dot = name.lastIndexOf('.');
      if (dot > 0 && name.indexOf("input") < 0) {
        String ext = name.substring(dot);
        if (ext.indexOf('|') < 0 && ext.indexOf(' ') < 0 && ext.length() <= 8) return ext;
      }
      i = b + 1;
    }
    return null;
  }

  private static String firstExt(String blob, String[] options, String fallback) {
    for (int i = 0; i < options.length; i++) {
      if (blob.indexOf(options[i]) >= 0) return ".jpeg".equals(options[i]) ? ".jpg" : ".midi".equals(options[i]) ? ".mid" : options[i];
    }
    return fallback;
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase();
  }

  private static String safeFile(String name) {
    String raw = name == null ? "" : name.trim();
    int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
    if (slash >= 0) raw = raw.substring(slash + 1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length() && sb.length() < 80; i++) {
      char c = raw.charAt(i);
      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_') sb.append(c);
      else sb.append('_');
    }
    String file = sb.toString();
    while (file.startsWith(".")) file = file.substring(1);
    return file.length() == 0 ? "output.log" : file;
  }

  private static void readLabels(Sheet sheet, String[] lines, int from, int dash) {
    for (int i = from; i < dash; i++) {
      String line = lines[i];
      if (line.startsWith("Category: ")) sheet.category = line.substring(10).trim();
      else if (line.startsWith("Version: ")) sheet.version = line.substring(9).trim();
      else if (line.startsWith("Model: ")) sheet.model = line.substring(7).trim();
      else if (line.startsWith("Reference file 1: ")) sheet.ref1 = line.substring(18).trim();
      else if (line.startsWith("Reference file 2: ")) sheet.ref2 = line.substring(18).trim();
      else if (line.startsWith("Result file: ")) sheet.result = line.substring(13).trim();
      else if (line.startsWith("Type: ")) sheet.type = normalizeType(line.substring(6));
      else if (line.startsWith("Description: ")) sheet.description = line.substring(13).trim();
    }
  }

  /** The prompt itself. A description stored in front of it is not part of the run. */
  public static String withoutDescription(String description, String text) {
    if (text == null) return "";
    String body = text.replace("\r\n", "\n").replace('\r', '\n');
    String desc = description == null ? "" : description.replace("\r\n", "\n").replace('\r', '\n').trim();
    if (desc.length() == 0) return body;
    int i = 0;
    while (i < body.length() && body.charAt(i) == '\n') i++;
    String rest = body.substring(i);
    if (rest.startsWith(desc) && (rest.length() == desc.length() || rest.charAt(desc.length()) == '\n')) {
      rest = rest.substring(desc.length());
      while (rest.startsWith("\n")) rest = rest.substring(1);
      return rest;
    }
    String flat = desc.replace('\n', ' ').trim();
    if (!flat.equals(desc) && rest.startsWith(flat) && (rest.length() == flat.length() || rest.charAt(flat.length()) == '\n')) {
      rest = rest.substring(flat.length());
      while (rest.startsWith("\n")) rest = rest.substring(1);
      return rest;
    }
    return body;
  }

  private static String one(String value) {
    if (value == null) return "";
    return value.replace('\n', ' ').replace('\r', ' ');
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
