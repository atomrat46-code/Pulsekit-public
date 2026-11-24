package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Document ready: after a SogniChat run, the text it wrote (answer.txt...) shown, cut at a delimiter
 * so only the answer is left (the chat before it dropped), with Copy and Send as prompt (into another
 * Sogni program's Params as its prompt). The delimiter and the cut are remembered for each program.
 */
public final class TextReady {
  private TextReady() {}

  public static final String TITLE = "Document ready";
  public static final String NO_CUT = "No cut", CUT_AFTER = "Cut after", CUT_BEFORE = "Cut before";
  public static final String[] MODES = {CUT_AFTER, CUT_BEFORE, NO_CUT};
  public static final String DELIMITER = "Delimiter";
  public static final String HINT = "Cut after: the text after the delimiter (its last one). Cut before: the text before it (its first one).";
  public static final String COPY = "Copy", SAVE = "Save", SEND = "Send as prompt…", CLOSE = "Close";
  public static final String SEND_TITLE = "Send as prompt to";
  /** SogniChat's reply follows this line in the file it writes. */
  public static final String SOGNI_REPLY = "=== Sogni ===";
  static final String FILE = "text-ready.txt";

  /** Whether a run of `program` gets Document ready on the text it wrote. */
  public static boolean wanted(String program) {
    return "SogniChat.java".equals(program);
  }

  /** The text file a run wrote (its last .txt), or null. */
  public static JavaRun.FileOut textOf(List<JavaRun.FileOut> files) {
    JavaRun.FileOut out = null;
    if (files != null) for (JavaRun.FileOut f : files) if (f != null && f.name != null && f.bytes != null && f.name.toLowerCase().endsWith(".txt")) out = f;
    return out;
  }

  public static String text(byte[] bytes) {
    return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
  }

  /** The text cut at `delim`: after its last appearance, before its first, or all of it (no cut, or the delimiter not in it). */
  public static String cut(String text, String delim, String mode) {
    if (text == null) return "";
    if (delim == null || delim.isEmpty() || NO_CUT.equals(mode)) return text.trim();
    if (CUT_AFTER.equals(mode)) {
      int at = text.lastIndexOf(delim);
      return at < 0 ? text.trim() : text.substring(at + delim.length()).trim();
    }
    if (CUT_BEFORE.equals(mode)) {
      int at = text.indexOf(delim);
      return at < 0 ? text.trim() : text.substring(0, at).trim();
    }
    return text.trim();
  }

  /** Under the text: what was cut, or that the delimiter is not in it. */
  public static String note(String text, String delim, String mode) {
    if (delim == null || delim.isEmpty() || NO_CUT.equals(mode)) return "The whole text";
    if (text == null || text.indexOf(delim) < 0) return "The delimiter is not in the text: the whole text is shown";
    return (CUT_AFTER.equals(mode) ? "The text after " : "The text before ") + "“" + delim + "”";
  }

  /** The program's cut and delimiter: remembered, else SogniChat's reply line with Cut after. */
  public static String[] setting(File dir, String program) {
    String[] got = load(dir).get(program);
    if (got != null) return got;
    return "SogniChat.java".equals(program) ? new String[] {CUT_AFTER, SOGNI_REPLY} : new String[] {NO_CUT, ""};
  }

  public static void remember(File dir, String program, String mode, String delim) {
    Map<String, String[]> all = load(dir);
    all.put(program, new String[] {mode, delim == null ? "" : delim.replace('\n', ' ').replace('\t', ' ')});
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String[]> e : all.entrySet()) sb.append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t').append(e.getValue()[1]).append('\n');
    try {
      dir.mkdirs();
      java.nio.file.Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // only not remembered
    }
  }

  static Map<String, String[]> load(File dir) {
    Map<String, String[]> out = new LinkedHashMap<String, String[]>();
    try {
      File f = new File(dir, FILE);
      if (!f.isFile()) return out;
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.split("\t", -1);
        if (p.length >= 3) out.put(p[0], new String[] {p[1], p[2]});
      }
    } catch (Exception ignored) {
      // none remembered
    }
    return out;
  }

  /** Save's file name: answer.txt's text as shown, answer-trimmed.txt. */
  public static String savedName(String fileName) {
    String stem = fileName == null ? "" : fileName.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    return (stem.isEmpty() ? "text" : stem) + "-trimmed.txt";
  }

  public static String copied(String text) {
    return text.trim().isEmpty() ? "No text to copy" : "Copied the text (" + text.trim().length() + " characters)";
  }

  /** The Sogni programs whose Params have a prompt (SogniChat too). */
  public static List<String> targets(java.util.function.Function<String, String> textOf) {
    List<String> out = new ArrayList<String>();
    for (String s : ParamsClip.SOGNI) {
      String t = textOf.apply(s);
      if (t == null) continue;
      for (ProgramParams.Param p : ProgramParams.parse(t)) {
        if (p.flag && p.token.equals("--prompt")) {
          out.add(s);
          break;
        }
      }
    }
    return out;
  }
}
