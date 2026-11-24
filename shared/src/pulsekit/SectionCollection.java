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
  public static final String ADD_GROUP = "Add new", ADD_SECTION = "Add new section", CLOSE = "Close";
  public static final String GROUP_NAME = "Group name", DESCRIPTION = "Description (optional)";
  public static final String SECTION_NAME = "Section name (optional)", SECTION_TEXT = "Section text (\"Prompt: ...\", \"Loras: ...\" a line each; plain text is the prompt)";
  /** A section's menu. */
  public static final String SAVE = "Save", DELETE = "Delete", MOVE = "Move into group", EDIT_NAME = "Edit name", OUT_OF_GROUP = "Take out of the group";
  /** A group's menu. */
  public static final String RENAME = "Rename";
  /** Edit name's dialog. */
  public static final String NAME = "Name", EXIT = "Exit";
  public static final String OPEN = "+", SHUT = "-";
  public static final String NO_SECTIONS = "No sections in no group: Add new section makes one, a group's sections are under it (+).";
  public static final String NO_GROUPS = "No groups yet: Add new makes one.";
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
