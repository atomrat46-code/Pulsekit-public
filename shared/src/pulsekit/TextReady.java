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

  /** Text after: where Select text takes it from, and the text added after the answer. */
  public static final String AFTER = "Text after", FROM_FILE = "From file", FROM_SECTIONS = "From sections";
  public static final String[] AFTER_FROM = {FROM_FILE, FROM_SECTIONS};
  public static final String SELECT_TEXT = "Select text", CLEAR_TEXT = "Clear";
  /** Document ready's buttons under the delimiter: the text after the answer from a file or a saved section. */
  public static final String SELECT_FILE = "Select file", BROWSE_SECTIONS = "Browse sections";

  /** Send to group: the text as a section in a Sogni program's group; that group is %g in the saved answer's names. */
  public static final String SEND_GROUP = "Send to group\u2026", SEND_GROUP_PROGRAM = "Send to which program's group?";

  /** %g (the group Send to group used; "" none) as the choice codes have it. */
  public static DownloadMove.Choices choices(String group) {
    DownloadMove.Choices c = new DownloadMove.Choices();
    if (group != null && !group.trim().isEmpty() && !SectionCollection.NO_GROUP_PICK.equals(group)) c.groups.add(group.trim());
    return c;
  }

  /** The answer's name in its output folder: the chat's name, %g (and the other choice codes) filled in. */
  public static String outName(String fileName, String group) {
    return DownloadMove.nameFor(fileName, choices(group));
  }

  /** The output folder's pattern with %g filled in (the date codes and %n stay for the folder's own filling). */
  public static String outDir(String pattern, String group) {
    return pattern == null ? null : DownloadMove.fillChoices(pattern, choices(group)).replace("%%", "%");
  }

  /** Send to group's section: named as the answer (no codes), its prompt the text, in `group` ("(no group)": none). */
  public static String sendToGroup(PromptVault vault, String app, String fileName, String text, String group) throws Exception {
    SectionCollection.Form f = new SectionCollection.Form();
    f.name = DownloadMove.nameFor(fileName, choices(null)).replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    f.prompt = text == null ? "" : text.trim();
    f.group = group == null ? SectionCollection.NO_GROUP_PICK : group;
    String bad = SectionCollection.add(vault, app, f);
    return bad != null ? bad : "Sent to " + app + "'s " + (SectionCollection.NO_GROUP_PICK.equals(f.group) ? "sections (no group)" : "group " + f.group) + " as a section";
  }

  /**
   * SogniChat's Send to program and Send to group (--send_program, --send_group): {program ("SogniPadd"),
   * group}, or null when either is not given. An input folder's answers go there by themselves; Document
   * ready's preset button sends one there with a click.
   */
  public static String[] preset(List<String> argv) {
    String p = ChatFolder.arg(argv, "--send_program"), g = ChatFolder.arg(argv, "--send_group");
    if (p == null || g == null) return null;
    return new String[] {SogniSections.app(p.replaceAll("(?i)\\.java$", "") + ".java"), g.replaceAll("\\s+", " ").trim()};
  }

  /** The preset button's text: "Send to SogniPadd: Moods". */
  public static String presetLabel(String[] p) {
    return "Send to " + p[0] + ": " + p[1];
  }

  /** Whether the line under the delimiter is shown: only when the delimiter is not in the text. */
  public static boolean noteShown(String text, String delim, String mode) {
    return note(text, delim, mode).startsWith("The delimiter is not");
  }

  /**
   * The program's settings: {cut, delimiter, text after, where Select text takes it from}, remembered,
   * else SogniChat's reply line with Cut after (no text after).
   */
  public static String[] setting(File dir, String program) {
    String[] got = load(dir).get(program);
    if (got != null) return got;
    return "SogniChat.java".equals(program) ? new String[] {CUT_AFTER, SOGNI_REPLY, "", FROM_FILE} : new String[] {NO_CUT, "", "", FROM_FILE};
  }

  /** The cut and delimiter remembered; the text after as it was. */
  public static void remember(File dir, String program, String mode, String delim) {
    String[] was = setting(dir, program);
    remember(dir, program, mode, delim, was[2], was[3]);
  }

  public static void remember(File dir, String program, String mode, String delim, String after, String from) {
    Map<String, String[]> all = load(dir);
    all.put(program, new String[] {mode, delim == null ? "" : delim.replace('\n', ' ').replace('\t', ' '), after == null ? "" : after, from == null ? FROM_FILE : from});
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String[]> e : all.entrySet()) {
      String[] v = e.getValue();
      sb.append(e.getKey()).append('\t').append(v[0]).append('\t').append(v[1]).append('\t').append(escape(v[2])).append('\t').append(v[3]).append('\n');
    }
    try {
      dir.mkdirs();
      java.nio.file.Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // only not remembered
    }
  }

  static String escape(String t) {
    return t.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "");
  }

  static String unescape(String t) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (c == '\\' && i + 1 < t.length()) {
        char n = t.charAt(++i);
        sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
      } else sb.append(c);
    }
    return sb.toString();
  }

  static Map<String, String[]> load(File dir) {
    Map<String, String[]> out = new LinkedHashMap<String, String[]>();
    try {
      File f = new File(dir, FILE);
      if (!f.isFile()) return out;
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.split("\t", -1);
        if (p.length >= 3) out.put(p[0], new String[] {p[1], p[2], p.length >= 4 ? unescape(p[3]) : "", p.length >= 5 && !p[4].isEmpty() ? p[4] : FROM_FILE});
      }
    } catch (Exception ignored) {
      // none remembered
    }
    return out;
  }

  /** The answer as shown and saved: cut at the delimiter, the text after added after it (on a line of its own). */
  public static String result(String text, String[] set) {
    String cut = cut(text, set[1], set[0]);
    String after = set.length > 2 && set[2] != null ? set[2].trim() : "";
    return after.isEmpty() ? cut : cut.isEmpty() ? after : cut + "\n" + after;
  }

  /** From sections: the Sogni programs' saved sections, each labelled with its program; their prompts in `texts`. */
  public static List<String> sectionChoices(PromptVault vault, List<String> texts) {
    List<String> labels = new ArrayList<String>();
    for (String app : vault.sogniSectionApps()) {
      for (PromptVault.SogniSection sec : vault.sogniSections(app)) {
        String prompt = SectionCollection.formOf(sec).prompt;
        String t = prompt == null || prompt.trim().isEmpty() ? (sec.text == null ? "" : sec.text.trim()) : prompt.trim();
        if (t.isEmpty()) continue;
        labels.add(app + " \u00b7 " + SogniSections.label(sec));
        texts.add(t);
      }
    }
    return labels;
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
