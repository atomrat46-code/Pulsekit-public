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
  /** The favourite button (☆ not one, ★ one) and the final mark's menu items. */
  public static final String STAR_OFF = "\u2606", STAR_ON = "\u2605";
  public static final String FINAL = "Mark as final", UNFINAL = "Remove the final mark";
  public static final String FAVOURITE = "Mark as favourite \u2605", UNFAVOURITE = "Remove from favourites";
  /** Add to group…: an existing group of the program's or a new one by name; Take out of the group. */
  public static final String ADD_GROUP = "Add to group\u2026", NEW_GROUP = "New group\u2026", UNGROUP = "Take out of the group";
  public static final String GROUP_NAME = "The group's name";
  /** Run sections…: several sections picked (or a group), the program run once with each, one after another. */
  public static final String RUN = "Run sections\u2026";
  public static final String RUN_TIP = "Run the program once for each section picked, one after another (the other Params as they are now)";
  public static final String RUN_ASK = "Pick the sections to run, one after another. Each run has the Params as they are now, with the section's entries.";
  public static final String RUN_ALL = "Pick: none", RUN_FAVOURITES = "Pick: favourites \u2605";
  public static final String RUN_NONE = "No sections saved for this program yet: Save keeps the entries as one.";

  /** Run sections' pick list: "none", "favourites", then "group: <name>" for each group. */
  public static java.util.List<String> runPicks(java.util.List<PromptVault.SogniSection> all) {
    java.util.List<String> out = new java.util.ArrayList<String>();
    out.add(RUN_ALL);
    out.add(RUN_FAVOURITES);
    for (String g : groupNames(all)) out.add("Pick group: " + g);
    return out;
  }

  /** Which sections a pick ticks (by index in `all`). */
  public static boolean[] runPicked(java.util.List<PromptVault.SogniSection> all, String pick) {
    boolean[] out = new boolean[all.size()];
    for (int i = 0; i < out.length; i++) {
      PromptVault.SogniSection s = all.get(i);
      if (RUN_FAVOURITES.equals(pick)) out[i] = s.favourite;
      else if (pick != null && pick.startsWith("Pick group: ")) out[i] = s.group != null && s.group.trim().equalsIgnoreCase(pick.substring(12).trim());
    }
    return out;
  }

  /** Browse sections' search: the button, its two ways and the term's hint. */
  public static final String SEARCH = "Search", CONTAINS = "Contains", NOT_CONTAINS = "Doesn't contain";
  public static final String SEARCH_HINT = "Search term: * any characters, ? one (kite*sky)";

  /**
   * A search term as a pattern: * any characters (none too), ? any one, the rest as it is; case
   * aside. Null for an empty term (everything matches).
   */
  public static java.util.regex.Pattern searchPattern(String term) {
    String t = term == null ? "" : term.trim();
    if (t.isEmpty()) return null;
    StringBuilder re = new StringBuilder();
    StringBuilder plain = new StringBuilder();
    for (char c : t.toCharArray()) {
      if (c == '*' || c == '?') {
        if (plain.length() > 0) re.append(java.util.regex.Pattern.quote(plain.toString()));
        plain.setLength(0);
        re.append(c == '*' ? ".*?" : ".");
      } else plain.append(c);
    }
    if (plain.length() > 0) re.append(java.util.regex.Pattern.quote(plain.toString()));
    return java.util.regex.Pattern.compile(re.toString(), java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.DOTALL);
  }

  /** Whether a section has the term (anywhere in its entries or its group's name): `contains` false turns it round. */
  public static boolean matches(PromptVault.SogniSection s, String term, boolean contains) {
    java.util.regex.Pattern p = searchPattern(term);
    if (p == null) return true;
    boolean hit = p.matcher((s.text == null ? "" : s.text) + "\n" + (s.group == null ? "" : s.group)).find();
    return contains ? hit : !hit;
  }

  /** The sections the search keeps (all of them for an empty term), in their order. */
  public static java.util.List<PromptVault.SogniSection> search(java.util.List<PromptVault.SogniSection> all, String term, boolean contains) {
    java.util.List<PromptVault.SogniSection> out = new java.util.ArrayList<PromptVault.SogniSection>();
    for (PromptVault.SogniSection s : all) if (matches(s, term, contains)) out.add(s);
    return out;
  }

  /** What the search found, for the line under it. */
  /** Said after the count when none of the sections is in a group yet (Add to group… makes one). */
  public static String groupsNote(java.util.List<PromptVault.SogniSection> all) {
    for (Group g : groups(all)) if (g.named) return "";
    return " \u00b7 No groups yet: a section's menu has Add to group\u2026";
  }

  public static String searchSaid(int found, int all, String term, boolean contains) {
    if (searchPattern(term) == null) return all + " section" + (all == 1 ? "" : "s");
    return found + " of " + all + " section" + (all == 1 ? "" : "s") + (contains ? " contain " : " don't contain ") + "\"" + term.trim() + "\"";
  }

  /** The groups sections were added to (Add to group…), A to Z. */
  public static java.util.List<String> groupNames(java.util.List<PromptVault.SogniSection> all) {
    java.util.TreeMap<String, String> names = new java.util.TreeMap<String, String>();
    for (PromptVault.SogniSection s : all) {
      String g = s.group == null ? "" : s.group.trim();
      if (g.length() > 0) names.put(g.toLowerCase(java.util.Locale.ROOT), g);
    }
    return new java.util.ArrayList<String>(names.values());
  }

  /** Whether a group row opens a list (several sections, or a group added to by name) rather than one section. */
  public static boolean opens(Group g) {
    return g.named || g.none || g.sections.size() != 1;
  }

  /** A section's marks before its label: "★ " for a favourite, "F " for a final one. */
  public static String marks(PromptVault.SogniSection s) {
    return (s.favourite ? STAR_ON + " " : "") + (s.fin ? "F " : "");
  }

  /** The prompt row's ★ and G: the entries now (the prompt and the other rows) as a favourite section, or in a group. */
  public static final String GROUP_BUTTON = "G";
  public static final String STAR_TIP = "Favourite: the prompt (with the other rows) kept as a favourite section; again, not a favourite";
  public static final String GROUP_TIP = "Group: the prompt (with the other rows) kept as a section in a group";

  /** The program's section with these entries: the one saved, or saved now. */
  public static PromptVault.SogniSection sectionOf(PromptVault vault, String app, Map<String, String> now) throws Exception {
    String t = text(now);
    PromptVault.SogniSection s = vault.findSogniSection(app, t);
    return s != null ? s : vault.addSogniSection(app, t);
  }

  /** The prompt row's star now: ★ when the entries are a favourite section already. */
  public static String starNow(PromptVault vault, String app, Map<String, String> now) {
    if (now == null || now.isEmpty()) return STAR_OFF;
    PromptVault.SogniSection s = vault.findSogniSection(app, text(now));
    return s != null && s.favourite ? STAR_ON : STAR_OFF;
  }

  /** The favourite button's text for a section. */
  public static String star(PromptVault.SogniSection s) {
    return s.favourite ? STAR_ON : STAR_OFF;
  }

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
    /** A group sections were added to by name (Add to group…), not one their prompt makes. */
    public boolean named;
    /** "No group": the sections in no group, listed last. */
    public boolean none;
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
  public static final String NO_GROUP = "No group";

  /** Browse sections' list as Section Collection has it: the groups A to Z, each with its sections; then "No group". */
  public static java.util.List<Group> groups(java.util.List<PromptVault.SogniSection> all) {
    Map<String, Group> by = new LinkedHashMap<String, Group>();
    for (PromptVault.SogniSection s : all) {
      String name = s.group == null ? "" : s.group.replaceAll("\\s+", " ").trim();
      String key = name.length() > 0 ? "g:" + name.toLowerCase(java.util.Locale.ROOT) : "none";
      Group g = by.get(key);
      if (g == null) {
        g = new Group();
        g.lead = name.length() > 0 ? name : NO_GROUP;
        g.named = name.length() > 0;
        g.none = name.isEmpty();
        by.put(key, g);
      }
      g.sections.add(s);
    }
    java.util.List<Group> out = new java.util.ArrayList<Group>(by.values());
    for (Group g : out) java.util.Collections.sort(g.sections, (a, b) -> Long.compare(b.created, a.created));
    java.util.Collections.sort(out, (a, b) -> {
      if (a.none != b.none) return a.none ? 1 : -1;
      return a.lead.compareToIgnoreCase(b.lead);
    });
    return out;
  }

  /** A group's marks: ★ when a section in it is a favourite, F when one is final. */
  public static String groupMarks(Group g) {
    boolean fav = false, fin = false;
    for (PromptVault.SogniSection s : g.sections) {
      fav |= s.favourite;
      fin |= s.fin;
    }
    return (fav ? STAR_ON + " " : "") + (fin ? "F " : "");
  }

  /** A group's row: one section as its own label; several as their lead (cut at 80) and how many ("… (3)"). */
  public static String groupLabel(Group g) {
    if (!opens(g)) return label(g.sections.get(0));
    String lead = g.lead.length() > 80 ? g.lead.substring(0, 80) + "\u2026" : g.lead;
    return "\u25b8 " + groupMarks(g) + lead + " (" + g.sections.size() + ")";
  }

  /** A section shown fully: when it was saved, then its entries, a line each. */
  public static String full(PromptVault.SogniSection s) {
    String when = new java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale.ROOT).format(new java.util.Date(s.created));
    String named = (s.name != null && s.name.length() > 0 ? s.name + "\n" : "") + (s.note != null && s.note.length() > 0 ? s.note + "\n" : "");
    return (named.length() > 0 ? named + "\n" : "") + "Saved " + when + "\n\n" + s.text.trim();
  }

  /** A section in a list: its prompt's start and what else it sets ("a portrait in soft light… · loras, skin_detail"). */
  public static String label(PromptVault.SogniSection s) {
    // A section with a name (Section Collection's Edit name): the name first.
    if (s.name != null && s.name.length() > 0) return s.name + ": " + unnamedLabel(s);
    return unnamedLabel(s);
  }

  private static String unnamedLabel(PromptVault.SogniSection s) {
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
    return marks(s) + prompt + (more.length() == 0 ? "" : " · " + more) + " · " + when;
  }
}
