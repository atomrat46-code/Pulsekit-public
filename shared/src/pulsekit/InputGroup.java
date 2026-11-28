package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * Input group (the Sogni programs with an Input folder): a group of the program's saved sections, each
 * section one run as a batch (as Run sections with the whole group picked). With an Input folder too, the
 * run asks whether to process the folder, the group, or both (the group first).
 */
public final class InputGroup {
  private InputGroup() {}

  public static final String TOKEN = "--pk-input-group";
  public static final String LABEL = "Input group: each saved section of this group run in turn (its prompt, LoRAs...), one run a section";
  public static final String HINT = "a group's name (Browse groups); \"(no group)\": the sections in none";
  public static final String BROWSE = "Browse groups";
  public static final String ASK = "Both Input folder and Input group are set. Process which?";
  public static final String BOTH = "Both (group first)", FOLDER = "Folder only", GROUP = "Group only";

  /** Its Params row (the programs with an Input folder). */
  public static ProgramParams.Param row() {
    ProgramParams.Param p = new ProgramParams.Param();
    p.token = TOKEN;
    p.flag = true;
    p.takesValue = true;
    p.optional = true;
    p.label = LABEL;
    p.hint = HINT;
    return p;
  }

  /** The group named in the run's arguments, or null. */
  public static String of(List<String> argv) {
    String g = ChatFolder.arg(argv, TOKEN);
    return g == null || g.trim().isEmpty() ? null : g.trim();
  }

  /** The run's arguments without it (Pulsekit's). */
  public static void strip(List<String> argv) {
    if (argv != null) DownloadMove.takeValue(argv, TOKEN);
  }

  /** The program's sections in `group` ("(no group)" or empty: those in none), in their saved order. */
  public static List<PromptVault.SogniSection> sections(PromptVault vault, String program, String group) {
    List<PromptVault.SogniSection> out = new ArrayList<PromptVault.SogniSection>();
    if (vault == null) return out;
    boolean none = group == null || group.trim().isEmpty() || SectionCollection.NO_GROUP_PICK.equals(group.trim());
    for (PromptVault.SogniSection s : vault.sogniSections(SogniSections.app(program))) {
      String g = s.group == null ? "" : s.group.trim();
      if (none ? g.isEmpty() : g.equalsIgnoreCase(group.trim())) out.add(s);
    }
    return out;
  }
}
