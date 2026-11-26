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
  public static final String HINT = "Cut after: the text after the delimiter (its first one). Cut before: the text before it (its first one).";
  public static final String COPY = "Copy", SAVE = "Save", SEND = "Send as prompt…", CLOSE = "Close";
  /** Document ready's menu holding Copy, Save, Send as prompt, Send to group and the preset send. */
  public static final String FILE_MENU = "File";
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

  /** The text cut at `delim`: after its first appearance, before its first, or all of it (no cut, or the delimiter not in it). */
  public static String cut(String text, String delim, String mode) {
    if (text == null) return "";
    if (delim == null || delim.isEmpty() || NO_CUT.equals(mode)) return text.trim();
    if (CUT_AFTER.equals(mode)) {
      // After its first appearance (all the rest of the text).
      int at = text.indexOf(delim);
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
    return toGroup(vault, app, DownloadMove.nameFor(fileName, choices(null)).replaceAll("\\.[A-Za-z0-9]{1,5}$", ""), text, group);
  }

  /** The text as a section named `name` in the program's group (Custom text's Add into program's group; Send to group). */
  public static String toGroup(PromptVault vault, String app, String name, String text, String group) throws Exception {
    // The same text there already (case, spaces and quotes aside): not added again.
    PromptVault.SogniSection have = sameText(vault, app, text);
    if (have != null) return ALREADY + SogniSections.label(have) + (have.group == null || have.group.isEmpty() ? "" : " (group " + have.group + ")") + ": not added again";
    SectionCollection.Form f = new SectionCollection.Form();
    f.name = name == null || name.trim().isEmpty() ? customName(text) : name.trim();
    f.prompt = text == null ? "" : text.trim();
    f.group = group == null ? SectionCollection.NO_GROUP_PICK : group;
    String bad = SectionCollection.add(vault, app, f);
    return bad != null ? bad : "Sent to " + app + "'s " + (SectionCollection.NO_GROUP_PICK.equals(f.group) ? "sections (no group)" : "group " + f.group) + " as a section";
  }

  /** Custom text: the item in the Select list, its field's hint, and its long press menu. */
  public static final String SELECT_MENU = "Select \u25be", CUSTOM_TEXT = "Custom text", CUSTOM_HINT = "Custom text: added after the answer (long press: Save, Add into program's group)",
      CUSTOM_SAVE = "Save", CUSTOM_GROUP = "Add into program's group", CUSTOM_CLEAR = "Clear", CUT = "Cut", COPY_TEXT = "Copy", PASTE = "Paste", FIELDS_HIDE = "\u2212", FIELDS_SHOW = "+", SECTION_NAME = "Section name", CUSTOM_FILE = "custom-text.txt";

  /** A section name for a text: its first words (at most 40 characters), else "Custom text". */
  public static String customName(String text) {
    String t = text == null ? "" : text.replaceAll("\\s+", " ").trim();
    if (t.length() > 40) {
      int sp = t.lastIndexOf(' ', 40);
      t = t.substring(0, sp > 10 ? sp : 40).trim();
    }
    return t.isEmpty() ? CUSTOM_TEXT : t;
  }

  /** Send to group's word when the text is a section of the program already. */
  public static final String ALREADY = "Already there: ";

  /** The program's section whose prompt is `text` (case, spaces and quotes around it aside), or null. */
  public static PromptVault.SogniSection sameText(PromptVault vault, String app, String text) {
    String want = plain(text);
    if (vault == null || want.isEmpty()) return null;
    for (PromptVault.SogniSection s : vault.sogniSections(app)) {
      String prompt = SectionCollection.formOf(s).prompt;
      if (want.equals(plain(prompt))) return s;
    }
    return null;
  }

  static String plain(String t) {
    String s = t == null ? "" : t.replaceAll("\\s+", " ").trim();
    s = s.replaceAll("^[\"'\u201c\u201d\u2018\u2019]+|[\"'\u201c\u201d\u2018\u2019]+$", "").trim();
    return s.toLowerCase(java.util.Locale.ROOT);
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

  /**
   * The preset's group with its % codes filled in, once for a run (or a Document ready): the date codes, and %n
   * the first number no group of the program has yet (Uudet%n: Uudet1, the next run Uudet2).
   */
  public static String[] resolved(PromptVault vault, String[] preset) {
    if (preset == null || preset[1].indexOf('%') < 0 || vault == null) return preset;
    final java.util.Set<String> have = new java.util.HashSet<String>();
    for (PromptVault.SectionGroup g : vault.sectionGroups(preset[0])) have.add(g.name.toLowerCase(java.util.Locale.ROOT));
    String name = DownloadMove.expand(preset[1], java.util.Calendar.getInstance(), n -> have.contains(n.toLowerCase(java.util.Locale.ROOT)));
    return new String[] {preset[0], name.replaceAll("[/\\\\]", "-").trim()};
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
  /** A program's settings' fields: cut, delimiter, text after, where it came from, Add, Remove note sections, Sogni cut,
   *  Remove section, its B and E, Custom text and whether it is added, whether the two text fields are shown (+/-). */
  public static final int F_MODE = 0, F_DELIM = 1, F_AFTER = 2, F_FROM = 3, F_ADD = 4, F_NOTES = 5, F_SOGNI = 6, F_SECTION = 7, F_B = 8, F_E = 9, F_CUSTOM = 10, F_CUSTOM_ADD = 11, F_SHOWN = 12, FIELDS = 13;

  public static String[] setting(File dir, String program) {
    String[] got = load(dir).get(program);
    if (got != null) return got;
    return "SogniChat.java".equals(program) ? new String[] {CUT_AFTER, SOGNI_REPLY, "", FROM_FILE, ON, OFF, OFF, OFF, "", "", "", ON, ON}
        : new String[] {NO_CUT, "", "", FROM_FILE, ON, OFF, OFF, OFF, "", "", "", ON, ON};
  }

  /** The cut and delimiter remembered; the text after as it was. */
  public static void remember(File dir, String program, String mode, String delim) {
    String[] was = setting(dir, program);
    remember(dir, program, mode, delim, was[2], was[3]);
  }

  public static void remember(File dir, String program, String mode, String delim, String after, String from) {
    remember(dir, program, mode, delim, after, from, setting(dir, program)[F_ADD]);
  }

  /** As above, with Add text after on ("on") or off ("off"). */
  public static void remember(File dir, String program, String mode, String delim, String after, String from, String add) {
    remember(dir, program, mode, delim, after, from, add, setting(dir, program)[F_NOTES]);
  }

  public static void remember(File dir, String program, String mode, String delim, String after, String from, String add, String notes) {
    remember(dir, program, mode, delim, after, from, add, notes, setting(dir, program)[F_SOGNI]);
  }

  /** As above, with Remove note sections and Sogni cut on ("on") or off (Remove section as it was). */
  public static void remember(File dir, String program, String mode, String delim, String after, String from, String add, String notes, String sogni) {
    String[] v = setting(dir, program).clone();
    v[F_MODE] = mode;
    v[F_DELIM] = delim;
    v[F_AFTER] = after;
    v[F_FROM] = from;
    v[F_ADD] = add;
    v[F_NOTES] = notes;
    v[F_SOGNI] = sogni;
    rememberAll(dir, program, v);
  }

  /** All of a program's settings remembered (FIELDS of them, in the F_ order). */
  public static void rememberAll(File dir, String program, String[] in) {
    String[] v = new String[FIELDS];
    for (int i = 0; i < FIELDS; i++) v[i] = in != null && i < in.length && in[i] != null ? in[i] : "";
    v[F_MODE] = v[F_MODE].isEmpty() ? NO_CUT : v[F_MODE];
    v[F_DELIM] = oneLine(v[F_DELIM]);
    v[F_FROM] = v[F_FROM].isEmpty() ? FROM_FILE : v[F_FROM];
    v[F_ADD] = OFF.equals(v[F_ADD]) ? OFF : ON;
    v[F_NOTES] = ON.equals(v[F_NOTES]) ? ON : OFF;
    v[F_SOGNI] = ON.equals(v[F_SOGNI]) ? ON : OFF;
    v[F_SECTION] = ON.equals(v[F_SECTION]) ? ON : OFF;
    v[F_CUSTOM_ADD] = OFF.equals(v[F_CUSTOM_ADD]) ? OFF : ON;
    v[F_SHOWN] = OFF.equals(v[F_SHOWN]) ? OFF : ON;
    v[F_B] = oneLine(v[F_B]);
    v[F_E] = oneLine(v[F_E]);
    Map<String, String[]> all = load(dir);
    all.put(program, v);
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String[]> e : all.entrySet()) {
      String[] w = e.getValue();
      sb.append(e.getKey());
      for (int i = 0; i < FIELDS; i++) sb.append('\t').append(i == F_AFTER || i == F_CUSTOM ? escape(w[i]) : w[i]);
      sb.append('\n');
    }
    try {
      dir.mkdirs();
      java.nio.file.Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // only not remembered
    }
  }

  static String oneLine(String t) {
    return t == null ? "" : t.replace('\n', ' ').replace('\t', ' ').replace("\r", "");
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
        if (p.length < 3) continue;
        String[] v = new String[FIELDS];
        for (int i = 0; i < FIELDS; i++) v[i] = i + 1 < p.length ? p[i + 1] : "";
        v[F_AFTER] = unescape(v[F_AFTER]);
        v[F_CUSTOM] = unescape(v[F_CUSTOM]);
        if (v[F_FROM].isEmpty()) v[F_FROM] = FROM_FILE;
        v[F_ADD] = OFF.equals(v[F_ADD]) ? OFF : ON;
        v[F_NOTES] = ON.equals(v[F_NOTES]) ? ON : OFF;
        v[F_SOGNI] = ON.equals(v[F_SOGNI]) ? ON : OFF;
        v[F_SECTION] = ON.equals(v[F_SECTION]) ? ON : OFF;
        v[F_CUSTOM_ADD] = OFF.equals(v[F_CUSTOM_ADD]) ? OFF : ON;
        v[F_SHOWN] = OFF.equals(v[F_SHOWN]) ? OFF : ON;
        out.put(p[0], v);
      }
    } catch (Exception ignored) {
      // none remembered
    }
    return out;
  }

  /** Sogni cut: the checkbox's words. */
  public static final String SOGNI_CUT = "Sogni cut";

  /** The text without its "=== Sogni ===" blocks: each from that line to the next blank line (both left out too). */
  public static String sogniCut(String text) {
    if (text == null) return "";
    String[] lines = text.replace("\r", "").split("\n", -1);
    StringBuilder out = new StringBuilder();
    boolean in = false;
    for (String l : lines) {
      if (!in && l.trim().equals(SOGNI_REPLY)) {
        in = true;
        continue;
      }
      if (in) {
        if (l.trim().isEmpty()) in = false;
        continue;
      }
      out.append(l).append('\n');
    }
    return out.toString().replaceAll("\n{3,}", "\n\n").trim();
  }

  /** Remove section: the checkbox's words and its two fields'. */
  public static final String REMOVE_SECTION = "Remove section", SECTION_B = "B", SECTION_E = "E";

  /**
   * The text without its sections from `b` to the next `e` (both left out too), each of them; and the blank lines they
   * leave. As it is when `b` or `e` is empty; a `b` with no `e` after it stays.
   */
  public static String withoutSections(String text, String b, String e) {
    if (text == null) return "";
    if (b == null || b.isEmpty() || e == null || e.isEmpty()) return text;
    StringBuilder out = new StringBuilder();
    int from = 0;
    while (true) {
      int at = text.indexOf(b, from);
      if (at < 0) break;
      int end = text.indexOf(e, at + b.length());
      if (end < 0) break;
      out.append(text, from, at);
      from = end + e.length();
    }
    out.append(text.substring(from));
    return out.toString().replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n").trim();
  }

  /** Remove note sections: the checkbox's words. */
  public static final String REMOVE_NOTES = "Remove note sections";

  /**
   * The text without its note sections: each from "*Note:" to the next "*" (the lines between too), and the
   * blank lines they leave.
   */
  public static String withoutNotes(String text) {
    if (text == null) return "";
    String out = text.replaceAll("(?s)\\*Note:.*?\\*", "");
    out = out.replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n");
    return out.trim();
  }

  /** Add text after (the checkbox before Select file): ON adds the text after to the answer, OFF keeps it aside. */
  public static final String ON = "on", OFF = "off", ADD_AFTER = "Add";

  /** The answer as shown and saved: cut at the delimiter, the text after added after it (on a line of its own) when Add is on. */
  public static String result(String text, String[] set) {
    // Sogni cut first: SogniChat's "=== Sogni ===" blocks (the line, the lines after it, to a blank line) out of the text.
    if (set.length > F_SOGNI && ON.equals(set[F_SOGNI])) text = sogniCut(text);
    String cut = cut(text, set[1], set[0]);
    boolean add = set.length <= 4 || !OFF.equals(set[4]);
    String after = add && set.length > 2 && set[2] != null ? set[2].trim() : "";
    String all = after.isEmpty() ? cut : cut.isEmpty() ? after : cut + "\n" + after;
    // Remove note sections, and Remove section (B to E): from the whole processed text (the answer and the text after it).
    if (set.length > F_NOTES && ON.equals(set[F_NOTES])) all = withoutNotes(all);
    if (set.length > F_E && ON.equals(set[F_SECTION])) all = withoutSections(all, set[F_B], set[F_E]);
    // Custom text: the user's own, after the processed text (not cut), when its checkbox is ticked.
    boolean customOn = set.length <= F_CUSTOM_ADD || !OFF.equals(set[F_CUSTOM_ADD]);
    String custom = customOn && set.length > F_CUSTOM && set[F_CUSTOM] != null ? set[F_CUSTOM].trim() : "";
    if (!custom.isEmpty()) all = all.isEmpty() ? custom : all + "\n" + custom;
    return all;
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
