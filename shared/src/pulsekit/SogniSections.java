package pulsekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Params > Save and Browse sections for the Sogni programs: the Params' text rows (the prompt, the
 * LoRAs, the other switches typed; not files, folders, the output name or on/off switches) kept in
 * the prompt library as the program's "section" (PromptVault.SogniSection), in the definitions
 * file format (ParamDefs: "Prompt: ...", "Loras: ..."), and put back into the Params.
 */
public final class SogniSections {
  private SogniSections() {}

  public static final String SAVE = "Save";
  public static final String BROWSE = "Browse sections";
  public static final String ASK = "The entries now are not saved as a section. Save them before the section is loaded?";
  public static final String SAVE_FIRST = "Save, then load";
  public static final String DONT_SAVE = "Load without saving";
  public static final String LOAD = "Load", EDIT = "Edit section", REMOVE = "Remove section";

  /** Remove section's question. */
  public static String removeAsk(PromptVault.SogniSection s) {
    return "Remove this section? It cannot be brought back.\n\n" + label(s);
  }

  /** Edit section's text checked: null when it is fine, else what is wrong. */
  public static String editProblem(String text) {
    return read(text).isEmpty() ? "No entries: each line is a row and its value (\"Prompt: ...\", \"Loras: ...\")" : null;
  }

  /** Whether a program's Params have Save and Browse sections (the Sogni programs). */
  public static boolean shown(String program) {
    return program != null && program.startsWith("Sogni");
  }

  /** The program a section is for: its name without .java or .py ("SogniPedit"). */
  public static String app(String program) {
    return program == null ? "" : program.replaceAll("(?i)\\.(java|py|jar)$", "");
  }

  /** Whether a row is one a section keeps. */
  public static boolean kept(ProgramParams.Param p) {
    // A switch with a value (an on/off one takes none), not a file, folder, plugin or the output name.
    return p.flag && p.takesValue && !p.output && !p.dir && !p.plugin && !p.isFile();
  }

  /** The rows kept: switch → value (the non-empty ones). */
  public static Map<String, String> fields(List<ProgramParams.Param> ps, String[] values) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      ProgramParams.Param p = ps.get(i);
      String v = values[i] == null ? "" : values[i].trim();
      if (kept(p) && v.length() > 0) out.put(p.token, v);
    }
    return out;
  }

  /** The rows as a section's text: "Prompt: ...", "Loras: ...", "skin_detail: 1.1". */
  public static String text(Map<String, String> fields) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : fields.entrySet()) {
      String t = e.getKey();
      String name = t.equals("--prompt") ? "Prompt" : t.equals("--loras") ? "Loras" : t.substring(2);
      // One line a field: the Params join a field's lines for the run anyway.
      sb.append(name).append(": ").append(e.getValue().replaceAll("\\s*\n\\s*", " ").trim()).append('\n');
    }
    return sb.toString();
  }

  /** A section's text read back: switch → value (a line a field, as text() writes them). */
  public static Map<String, String> read(String text) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    for (String line : (text == null ? "" : text).split("\n")) {
      int c = line.indexOf(':');
      if (c <= 0 || !line.substring(0, c).trim().matches("[A-Za-z][A-Za-z _-]{0,30}")) continue;
      out.put(ParamDefs.token(line.substring(0, c)), line.substring(c + 1).trim());
    }
    return out;
  }

  /** Whether two sections' texts hold the same entries (order and spacing aside). */
  public static boolean same(String a, String b) {
    return normal(read(a)).equals(normal(read(b)));
  }

  private static Map<String, String> normal(Map<String, String> m) {
    Map<String, String> out = new java.util.TreeMap<String, String>();
    for (Map.Entry<String, String> e : m.entrySet()) out.put(e.getKey(), e.getValue().replaceAll("\\s+", " ").trim());
    return out;
  }

  /** Browse sections' groups: the sections whose prompt starts with the same sentences. */
  public static final class Group {
    /** The first sentences of their prompt, as shown. */
    public String lead = "";
    /** Its sections, the newest first. */
    public final java.util.List<PromptVault.SogniSection> sections = new java.util.ArrayList<PromptVault.SogniSection>();
  }

  /** How many of the prompt's first sentences make a group. */
  public static final int LEAD_SENTENCES = 2;

  /** A prompt's first `n` sentences (each ending with . ! or ?, or the prompt's end), spaces made one; "(no prompt)" when there is none. */
  public static String lead(String prompt, int n) {
    String p = prompt == null ? "" : prompt.replaceAll("\\s+", " ").trim();
    if (p.isEmpty()) return "(no prompt)";
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("[.!?]+(\\s|$)").matcher(p);
    int end = p.length();
    for (int i = 0; i < n && m.find(); i++) end = m.end();
    return p.substring(0, Math.min(end, p.length())).trim();
  }

  /** A section's group lead: its prompt's first LEAD_SENTENCES sentences. */
  public static String lead(PromptVault.SogniSection s) {
    return lead(read(s.text).get("--prompt"), LEAD_SENTENCES);
  }

  /** The sections grouped by their lead (case aside), the groups in A to Z order, each group's newest first. */
  public static java.util.List<Group> groups(java.util.List<PromptVault.SogniSection> all) {
    Map<String, Group> by = new LinkedHashMap<String, Group>();
    for (PromptVault.SogniSection s : all) {
      String lead = lead(s);
      String key = lead.toLowerCase(java.util.Locale.ROOT);
      Group g = by.get(key);
      if (g == null) {
        g = new Group();
        g.lead = lead;
        by.put(key, g);
      }
      g.sections.add(s);
    }
    java.util.List<Group> out = new java.util.ArrayList<Group>(by.values());
    for (Group g : out) java.util.Collections.sort(g.sections, (a, b) -> Long.compare(b.created, a.created));
    java.util.Collections.sort(out, (a, b) -> {
      boolean na = a.lead.equals("(no prompt)"), nb = b.lead.equals("(no prompt)");
      if (na != nb) return na ? 1 : -1;
      return a.lead.compareToIgnoreCase(b.lead);
    });
    return out;
  }

  /** A group's row: one section as its own label; several as their lead (cut at 80) and how many ("… (3)"). */
  public static String groupLabel(Group g) {
    if (g.sections.size() == 1) return label(g.sections.get(0));
    String lead = g.lead.length() > 80 ? g.lead.substring(0, 80) + "\u2026" : g.lead;
    return "\u25b8 " + lead + " (" + g.sections.size() + ")";
  }

  /** A section shown fully: when it was saved, then its entries, a line each. */
  public static String full(PromptVault.SogniSection s) {
    String when = new java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale.ROOT).format(new java.util.Date(s.created));
    return "Saved " + when + "\n\n" + s.text.trim();
  }

  /** A section in a list: its prompt's start and what else it sets ("a portrait in soft light… · loras, skin_detail"). */
  public static String label(PromptVault.SogniSection s) {
    Map<String, String> f = read(s.text);
    String prompt = f.containsKey("--prompt") ? f.get("--prompt").replaceAll("\\s+", " ").trim() : "(no prompt)";
    if (prompt.length() > 50) prompt = prompt.substring(0, 50) + "…";
    StringBuilder more = new StringBuilder();
    for (String line : s.text.split("\n")) {
      int c = line.indexOf(':');
      if (c <= 0) continue;
      String n = line.substring(0, c).trim();
      if (n.equalsIgnoreCase("prompt") || !n.matches("[A-Za-z][A-Za-z _-]{0,30}")) continue;
      more.append(more.length() == 0 ? "" : ", ").append(n.toLowerCase(java.util.Locale.ROOT));
    }
    String when = new java.text.SimpleDateFormat("d.M. HH:mm", java.util.Locale.ROOT).format(new java.util.Date(s.created));
    return prompt + (more.length() == 0 ? "" : " · " + more) + " · " + when;
  }
}
