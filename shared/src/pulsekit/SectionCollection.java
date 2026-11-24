package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * Section Collection (Browse sections' SC): a Sogni program's sections in groups. The groups on the
 * left, A to Z, each with how many sections it has and a + that opens its list; the sections in no
 * group on the right, each in a text box (its name above it). A section dragged onto a group (or its
 * menu's Move into group) goes into that group; on the left, sections are dragged from group to group.
 * The texts and actions here; the phone's and the desktop's windows show them.
 */
public final class SectionCollection {
  private SectionCollection() {}

  public static final String TITLE = "Section Collection";
  /** Browse sections' button that opens it. */
  public static final String BUTTON = "SC";
  public static final String BUTTON_TIP = "Section Collection: the sections in groups (add, name, move between groups)";
  /** A section's full view (a tap on one under its group). */
  public static final String FULL_TIP = "Click: the section shown fully";
  public static final String ADD_GROUP = "Add new group", ADD_SECTION = "Add new section", CLOSE = "Close";
  public static final String GROUP_NAME = "Group name", DESCRIPTION = "Description (optional)";
  public static final String SECTION_NAME = "Section name (optional)", SECTION_TEXT = "Section text (\"Prompt: ...\", \"Loras: ...\" a line each; plain text is the prompt)";
  /** A section's menu. */
  public static final String SAVE = "Save", DELETE = "Delete", MOVE = "Move into group", EDIT_NAME = "Edit data", OUT_OF_GROUP = "Take out of the group";
  /** A group's menu. */
  public static final String RENAME = "Rename";
  /** Edit name's dialog. */
  public static final String NAME = "Name", EXIT = "Exit";
  public static final String OPEN = "+", SHUT = "-";
  public static final String NO_SECTIONS = "No sections in no group: Add new section makes one, a group's sections are under it (+).";
  public static final String NO_GROUPS = "No groups yet: Add new group makes one.";
  public static final String DROP_TIP = "Drag a section onto a group to put it there (its menu: Move into group)";

  /** A group as the left side shows it: its name, description and sections (the newest first). */
  public static final class Group {
    public String name = "", note = "";
    public final List<PromptVault.SogniSection> sections = new ArrayList<PromptVault.SogniSection>();
  }

  /** The program's groups, A to Z, with their sections. */
  public static List<Group> groups(PromptVault vault, String app) {
    List<Group> out = new ArrayList<Group>();
    List<PromptVault.SogniSection> all = vault.sogniSections(app);
    for (PromptVault.SectionGroup g : vault.sectionGroups(app)) {
      Group row = new Group();
      row.name = g.name;
      row.note = g.note == null ? "" : g.note;
      for (PromptVault.SogniSection s : all) if (same(s.group, g.name)) row.sections.add(s);
      out.add(row);
    }
    return out;
  }

  /** The sections in no group (the right side), the newest first. */
  public static List<PromptVault.SogniSection> loose(PromptVault vault, String app) {
    List<PromptVault.SogniSection> out = new ArrayList<PromptVault.SogniSection>();
    for (PromptVault.SogniSection s : vault.sogniSections(app)) if (s.group == null || s.group.trim().isEmpty()) out.add(s);
    return out;
  }

  static boolean same(String a, String b) {
    return tidy(a).equalsIgnoreCase(tidy(b)) && tidy(a).length() > 0;
  }

  static String tidy(String s) {
    return s == null ? "" : s.replaceAll("\\s+", " ").trim();
  }

  /** A group's row: its name, and "(n)" when it has sections. */
  public static String groupLabel(Group g) {
    return g.name + (g.sections.isEmpty() ? "" : " (" + g.sections.size() + ")");
  }

  /** A section's row under its group on the left: its name, else its label. */
  public static String shortLabel(PromptVault.SogniSection s) {
    return SogniSections.label(s);
  }

  /** The name printed above a section's text box ("" when it has none). */
  public static String heading(PromptVault.SogniSection s) {
    return s.name == null ? "" : s.name;
  }

  /** A section's text as typed: entry lines as they are; plain text (no "Prompt:" line) is the prompt. */
  public static String entries(String typed) {
    String t = typed == null ? "" : typed.trim();
    if (t.isEmpty()) return "";
    if (!SogniSections.read(t).isEmpty()) return t.endsWith("\n") ? t : t + "\n";
    return "Prompt: " + t.replaceAll("\\s*\n\\s*", " ") + "\n";
  }

  /** Add new: a group (null when it was made, else what is wrong). */
  public static String addGroup(PromptVault vault, String app, String name, String note) throws Exception {
    if (tidy(name).isEmpty()) return "A group needs a name";
    vault.addSectionGroup(app, name, note);
    return null;
  }

  /** Add new section's own rows: the prompt, the LoRAs (recent ones to pick), the group. */
  public static final String PROMPT = "Prompt", LORAS = "LoRAs", GROUP = "Group";
  public static final String NO_LORAS = "(no LoRAs)", NO_GROUP_PICK = "(no group)";

  /**
   * Add new section's LoRA list: "(no LoRAs)", then the LoRA values used last (the Params' now first,
   * then the program's sections', the newest first), each once.
   */
  public static List<String> recentLoras(PromptVault vault, String app, String paramsNow) {
    java.util.LinkedHashSet<String> got = new java.util.LinkedHashSet<String>();
    String now = paramsNow == null ? "" : paramsNow.trim();
    if (now.length() > 0) got.add(now);
    for (PromptVault.SogniSection s : vault.sogniSections(app)) {
      String l = SogniSections.read(s.text).get("--loras");
      if (l != null && l.trim().length() > 0) got.add(l.trim());
    }
    List<String> out = new ArrayList<String>();
    out.add(NO_LORAS);
    out.addAll(got);
    return out;
  }

  /**
   * A section's data as Add new section and Edit data show it: its name and description, the prompt, the
   * LoRAs (a pick), its group (a pick) and the program's other rows a section keeps (aspect, width, height...).
   */
  public static final class Form {
    public String name = "", note = "", prompt = "", loras = NO_LORAS, group = NO_GROUP_PICK;
    public final java.util.LinkedHashMap<String, String> extra = new java.util.LinkedHashMap<String, String>();
    /** Edit data's Save as: kept as a new section (the one edited stays as it was). */
    public boolean asNew;
  }

  /** Edit data's third button. */
  public static final String SAVE_AS = "Save as";

  /** The rows the form shows besides the prompt and the LoRAs: the program's Aspect, Width and Height. */
  public static final java.util.Set<String> FORM_ROWS = new java.util.HashSet<String>(java.util.Arrays.asList("--aspect", "--width", "--height"));

  /** The program's rows the form shows besides the prompt and the LoRAs (Aspect, Width, Height). */
  public static List<ProgramParams.Param> extraRows(List<ProgramParams.Param> ps) {
    List<ProgramParams.Param> out = new ArrayList<ProgramParams.Param>();
    if (ps != null) for (ProgramParams.Param p : ps) if (SogniSections.kept(p) && FORM_ROWS.contains(p.token)) out.add(p);
    return out;
  }

  /** A row's label in the form: its Params label without the hint after a colon or bracket. */
  public static String rowLabel(ProgramParams.Param p) {
    String l = p.label == null || p.label.isEmpty() ? p.token.substring(2) : p.label;
    return l.replaceAll("\\s*[(:].*$", "").trim();
  }

  /** The form of entries as the Params have them (switch → value). */
  public static Form formOf(java.util.Map<String, String> entries) {
    Form f = new Form();
    for (java.util.Map.Entry<String, String> e : entries.entrySet()) {
      String v = e.getValue() == null ? "" : e.getValue().trim();
      if (e.getKey().equals("--prompt")) f.prompt = v;
      else if (e.getKey().equals("--loras")) f.loras = v.isEmpty() ? NO_LORAS : v;
      else if (v.length() > 0) f.extra.put(e.getKey(), v);
    }
    return f;
  }

  /** A saved section's form. */
  public static Form formOf(PromptVault.SogniSection s) {
    Form f = formOf(SogniSections.read(s.text));
    f.name = s.name == null ? "" : s.name;
    f.note = s.note == null ? "" : s.note;
    f.group = s.group == null || s.group.trim().isEmpty() ? NO_GROUP_PICK : s.group.trim();
    return f;
  }

  /** The form's entries as a section's text ("Prompt: ...", "Loras: ...", "aspect: 4:7"). */
  public static String textOf(Form f) {
    java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<String, String>();
    String p = f.prompt == null ? "" : f.prompt.trim().replaceAll("\\s*\n\\s*", " ");
    if (p.length() > 0) m.put("--prompt", p);
    String l = f.loras == null || NO_LORAS.equals(f.loras) ? "" : f.loras.trim();
    if (l.length() > 0) m.put("--loras", l);
    for (java.util.Map.Entry<String, String> e : f.extra.entrySet()) if (e.getValue() != null && e.getValue().trim().length() > 0) m.put(e.getKey(), e.getValue().trim());
    return SogniSections.text(m);
  }

  /** The LoRAs list for a form: the form's own LoRAs first (when it has some), then the recent ones. */
  public static List<String> lorasFor(PromptVault vault, String app, String lorasNow, Form f) {
    List<String> all = recentLoras(vault, app, lorasNow);
    if (f != null && f.loras != null && !NO_LORAS.equals(f.loras) && !all.contains(f.loras)) all.add(1, f.loras);
    return all;
  }

  /** Add new section (or Save's new one): the form kept as a section. Null when it was made, else what is wrong. */
  public static String add(PromptVault vault, String app, Form f) throws Exception {
    return add(vault, app, f, null);
  }

  /**
   * Edit data's Save as: the form kept as a new section beside `from` (it stays). The same entries are
   * kept again only under another name.
   */
  public static String saveAs(PromptVault vault, PromptVault.SogniSection from, Form f) throws Exception {
    return add(vault, from.app, f, from);
  }

  static String add(PromptVault vault, String app, Form f, PromptVault.SogniSection from) throws Exception {
    if (f.prompt == null || f.prompt.trim().isEmpty()) return "A section needs its prompt";
    String t = textOf(f);
    boolean copy = false;
    if (from != null) {
      PromptVault.SogniSection same = vault.findSogniSection(app, t);
      if (same != null) {
        if (tidy(f.name).isEmpty() || tidy(f.name).equalsIgnoreCase(tidy(same.name))) return "Save as: give the copy another name (or change an entry)";
        copy = true;
      }
    }
    PromptVault.SogniSection s = vault.addSogniSection(app, t, copy);
    if (s == null) return "The same section is saved already";
    if (tidy(f.name).length() > 0 || (f.note != null && f.note.trim().length() > 0)) vault.nameSogniSection(s.id, f.name, f.note);
    String g = f.group == null || NO_GROUP_PICK.equals(f.group) ? "" : tidy(f.group);
    if (g.length() > 0) vault.groupSogniSection(s.id, g);
    lastAdded = s;
    return null;
  }

  /** The section add() made last. */
  public static PromptVault.SogniSection lastAdded;

  /** Edit data: the section's entries, name, description and group as the form has them. Null when saved, else what is wrong. */
  public static String update(PromptVault vault, PromptVault.SogniSection s, Form f) throws Exception {
    if (f.prompt == null || f.prompt.trim().isEmpty()) return "A section needs its prompt";
    String t = textOf(f);
    PromptVault.SogniSection same = vault.findSogniSection(s.app, t);
    if (same != null && same.id != s.id) return "Another section has the same entries: " + SogniSections.label(same);
    if (!SogniSections.same(t, s.text)) vault.updateSogniSection(s.id, t);
    vault.nameSogniSection(s.id, f.name, f.note);
    vault.groupSogniSection(s.id, f.group == null || NO_GROUP_PICK.equals(f.group) ? "" : tidy(f.group));
    return null;
  }

  /** The --loras value in a Params' saved switches ("" when it has none). */
  public static String lorasIn(String params) {
    List<String> w = JavaRun.split(params == null ? "" : params);
    for (int i = 0; i + 1 < w.size(); i++) if (w.get(i).equals("--loras") && !w.get(i + 1).startsWith("--")) return w.get(i + 1);
    return "";
  }

  /** Add new section's group list: "(no group)", then the program's groups A to Z. */
  public static List<String> groupPicks(PromptVault vault, String app) {
    List<String> out = new ArrayList<String>();
    out.add(NO_GROUP_PICK);
    for (PromptVault.SectionGroup g : vault.sectionGroups(app)) out.add(g.name);
    return out;
  }

  /**
   * Add new section with its own rows: a name and description, the prompt, the LoRAs picked ("(no
   * LoRAs)": none) and the group picked ("(no group)": none). Null when it was made, else what is wrong.
   */
  public static String addSection(PromptVault vault, String app, String name, String note, String prompt, String loras, String group) throws Exception {
    Form f = new Form();
    f.name = name == null ? "" : name;
    f.note = note == null ? "" : note;
    f.prompt = prompt == null ? "" : prompt;
    f.loras = loras == null ? NO_LORAS : loras;
    f.group = group == null ? NO_GROUP_PICK : group;
    return add(vault, app, f);
  }

  /** Add new section: a section with a name (null when it was made, else what is wrong). */
  public static String addSection(PromptVault vault, String app, String name, String text) throws Exception {
    String t = entries(text);
    if (t.isEmpty()) return "A section needs its text";
    PromptVault.SogniSection s = vault.addSogniSection(app, t);
    if (s == null) return "The same section is saved already";
    if (tidy(name).length() > 0) vault.nameSogniSection(s.id, name, "");
    return null;
  }

  /** Save: the text box's text as the section's (null when saved or unchanged, else what is wrong). */
  public static String save(PromptVault vault, PromptVault.SogniSection s, String text) throws Exception {
    String t = entries(text);
    if (t.isEmpty()) return "A section needs its text";
    if (t.trim().equals(s.text == null ? "" : s.text.trim())) return null;
    vault.updateSogniSection(s.id, t);
    return null;
  }

  /** Move into group (or dragged onto one): the section in that group; "" takes it out. */
  public static void move(PromptVault vault, long id, String group) throws Exception {
    vault.groupSogniSection(id, group);
  }

  public static String deleteAsk(PromptVault.SogniSection s) {
    return "Delete this section? It cannot be brought back.\n\n" + SogniSections.label(s);
  }

  public static String deleteGroupAsk(Group g) {
    return "Delete the group " + g.name + "?" + (g.sections.isEmpty() ? "" : "\n\nIts " + (g.sections.size() == 1 ? "section stays, in" : g.sections.size() + " sections stay, in") + " no group (on the right).");
  }

  /** A section's menu: Cut, Copy, Paste, and Send to another Sogni program (or one of its groups). */
  public static final String CUT = "Cut", COPY = "Copy", PASTE = "Paste";
  public static final String SEND = "Send to other program\u2026", SEND_GROUP = "Send to other program's group\u2026";
  public static final String PICK_PROGRAM = "Send to which program?", PICK_GROUP = "Into which group?";

  /** The section cut or copied (Paste puts it in), or null. */
  public static final class Clip {
    public String app = "", text = "", name = "", note = "";
    public long id;
    public boolean cut;
  }

  public static Clip clip;

  /** Cut or Copy: the section held for Paste (its text is on the system clipboard too, where there is one). */
  public static String hold(PromptVault.SogniSection s, boolean cut) {
    Clip c = new Clip();
    c.app = s.app;
    c.id = s.id;
    c.text = s.text == null ? "" : s.text;
    c.name = s.name == null ? "" : s.name;
    c.note = s.note == null ? "" : s.note;
    c.cut = cut;
    clip = c;
    return (cut ? "Cut: " : "Copied: ") + SogniSections.label(s) + (cut ? " (Paste puts it where you paste)" : "");
  }

  /**
   * Paste: the section cut or copied into `app`'s `group` ("" none). The same program's section is moved
   * there (a program keeps a section once); another program's is added there (Cut: and taken out of its
   * own program). What it did.
   */
  public static String paste(PromptVault vault, String app, String group) throws Exception {
    Clip c = clip;
    if (c == null) return "Nothing to paste: Cut or Copy a section first";
    String g = group == null ? "" : tidy(group);
    String done = put(vault, c, app, g);
    if (c.cut) clip = null;
    return done;
  }

  /** The section held put into a program's group: moved (the same program) or added (another one). */
  static String put(PromptVault vault, Clip c, String app, String g) throws Exception {
    PromptVault.SogniSection same = vault.findSogniSection(app, c.text);
    if (same != null) {
      vault.groupSogniSection(same.id, g);
      if (c.cut && same.id != c.id) vault.deleteSogniSection(c.id);
      return "In " + (g.isEmpty() ? "no group" : g) + (app.equalsIgnoreCase(c.app) ? "" : " of " + app) + ": " + SogniSections.label(same);
    }
    PromptVault.SogniSection n = vault.addSogniSection(app, c.text);
    if (n == null) return "Could not add it";
    if (c.name.length() > 0 || c.note.length() > 0) vault.nameSogniSection(n.id, c.name, c.note);
    if (!g.isEmpty()) vault.groupSogniSection(n.id, g);
    if (c.cut) vault.deleteSogniSection(c.id);
    return "Added to " + app + (g.isEmpty() ? "" : " \u203a " + g) + ": " + SogniSections.label(n);
  }

  /** Send to other program (its group, or none): the section copied there. What it did. */
  public static String send(PromptVault vault, PromptVault.SogniSection s, String app, String group) throws Exception {
    Clip c = new Clip();
    c.app = s.app;
    c.id = s.id;
    c.text = s.text == null ? "" : s.text;
    c.name = s.name == null ? "" : s.name;
    c.note = s.note == null ? "" : s.note;
    return put(vault, c, app, group == null ? "" : tidy(group));
  }

  /** The other Sogni programs a section can go to: Pulsekit's and those with sections (not `app`), A to Z. */
  public static List<String> programs(PromptVault vault, String app) {
    java.util.TreeMap<String, String> by = new java.util.TreeMap<String, String>();
    for (String p : ParamsClip.SOGNI) {
      String a = SogniSections.app(p);
      if (!a.equals("SogniChat") && !a.equals("SogniMusic")) by.put(a.toLowerCase(java.util.Locale.ROOT), a);
    }
    for (String a : vault.sogniSectionApps()) by.put(a.toLowerCase(java.util.Locale.ROOT), a);
    by.remove(app == null ? "" : app.toLowerCase(java.util.Locale.ROOT));
    return new ArrayList<String>(by.values());
  }

  /** What a section's drag carries (and a drop reads back): "pk-section:<id>". */
  public static String dragText(PromptVault.SogniSection s) {
    return "pk-section:" + s.id;
  }

  public static long dragged(String text) {
    if (text == null || !text.startsWith("pk-section:")) return -1;
    try {
      return Long.parseLong(text.substring(11).trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
