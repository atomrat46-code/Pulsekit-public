package pulsekit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A Sogni program's settings from a text file, as Params' Select file reads it:
 *
 * <pre>
 * App: SogniPedit
 * Prompt: a portrait in soft window light,
 *   film grain
 * Loras: skin-detail:0.8, film-look:0.5
 * ----
 * Prompt: the next one
 * </pre>
 *
 * "App:" names the program the definitions after it are for (until another App:). A field runs from
 * its "Name:" line to the next field or "----" (which ends a definition). Prompt and Loras fill
 * --prompt and --loras; another name fills the switch of that name (Aspect: → --aspect) when the
 * program has one.
 */
public final class ParamDefs {
  private ParamDefs() {}

  /** One definition: the program it is for ("" when any) and its fields, switch → value. */
  public static final class Def {
    public String app = "";
    public final Map<String, String> fields = new LinkedHashMap<String, String>();

    /** For a choice list: its program and the start of its prompt. */
    public String label() {
      String p = this.fields.containsKey("--prompt") ? this.fields.get("--prompt") : this.fields.isEmpty() ? "" : this.fields.values().iterator().next();
      p = p.replaceAll("\\s+", " ").trim();
      if (p.length() > 60) p = p.substring(0, 60) + "…";
      return (this.app.length() > 0 ? this.app + ": " : "") + (p.length() == 0 ? "(no prompt)" : p);
    }
  }

  /** Whether a text is such a file: a line starting App:, Prompt: or Loras:. */
  public static boolean looksLike(String text) {
    if (text == null) return false;
    for (String line : text.split("\n")) if (line.trim().matches("(?i)(app|prompt|loras?)\\s*:.*")) return true;
    return false;
  }

  /** The switch a field name stands for ("Loras" → --loras, "Reference file" → --reference_file). */
  static String token(String name) {
    String n = name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    if (n.equals("lora")) n = "loras";
    return "--" + n;
  }

  /**
   * The definitions of the text. `tokens` are the program's switches: a "Name:" line starts a field
   * only for App, Prompt, Loras or one of them (else it is part of the field above, as "Note: soft
   * light" inside a prompt).
   */
  public static List<Def> parse(String text, Set<String> tokens) {
    List<Def> out = new ArrayList<Def>();
    if (text == null) return out;
    String app = "";
    Def cur = null;
    String field = null;
    StringBuilder value = null;
    for (String raw : text.replace("\r", "").split("\n", -1)) {
      String line = raw.trim();
      if (line.startsWith("----")) {
        if (cur != null && field != null) cur.fields.put(field, value.toString().trim());
        if (cur != null && !cur.fields.isEmpty()) out.add(cur);
        cur = null;
        field = null;
        continue;
      }
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z][A-Za-z _-]{0,30}?)\\s*:\\s?(.*)$").matcher(line);
      String name = m.matches() ? m.group(1) : null;
      if (name != null && name.trim().equalsIgnoreCase("app")) {
        if (cur != null && field != null) cur.fields.put(field, value.toString().trim());
        if (cur != null && !cur.fields.isEmpty()) out.add(cur);
        cur = null;
        field = null;
        app = m.group(2).trim().split("\\s+")[0].replaceAll("(?i)\\.(java|py)$", "");
        continue;
      }
      String t = name == null ? null : token(name);
      boolean starts = t != null && (t.equals("--prompt") || t.equals("--loras") || (tokens != null && tokens.contains(t)));
      if (starts) {
        if (cur == null) {
          cur = new Def();
          cur.app = app;
        }
        if (field != null) cur.fields.put(field, value.toString().trim());
        field = t;
        value = new StringBuilder(m.group(2));
        continue;
      }
      if (field != null) value.append('\n').append(raw);
    }
    if (cur != null && field != null) cur.fields.put(field, value.toString().trim());
    if (cur != null && !cur.fields.isEmpty()) out.add(cur);
    return out;
  }

  /** The definitions for a program (SogniPedit.java): those naming it, and those naming none. */
  public static List<Def> forProgram(List<Def> defs, String program) {
    String p = program == null ? "" : program.replaceAll("(?i)\\.(java|py|jar)$", "");
    List<Def> out = new ArrayList<Def>();
    for (Def d : defs) if (d.app.length() == 0 || d.app.equalsIgnoreCase(p)) out.add(d);
    return out;
  }

  /** The programs the file's definitions are for, as a message: "SogniVideo, SogniPadd". */
  public static String apps(List<Def> defs) {
    List<String> names = new ArrayList<String>();
    for (Def d : defs) if (d.app.length() > 0 && !names.contains(d.app)) names.add(d.app);
    return String.join(", ", names);
  }
}
