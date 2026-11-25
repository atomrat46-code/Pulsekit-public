package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * SogniChat's Auto render (--auto_render): its answer (one, when Document ready closes, as shown there; an
 * input folder's each) as the prompt of its Send to program (--send_program), rendered with that program's
 * Params as they are (aspect, width, height...), one run an answer as a batch (Stop batch ends it). Each
 * run's Params are put back as they were after it, the prompt too. Without a Send to program it is left out.
 */
public final class AutoRender {
  private AutoRender() {}

  /** The program Auto render renders with ("SogniPadd.java"), or null (not ticked, or no Send to program). */
  public static String target(List<String> argv) {
    if (argv == null || !argv.contains("--auto_render")) return null;
    String p = ChatFolder.arg(argv, "--send_program");
    return p == null ? null : SogniSections.app(p.replaceAll("(?i)\\.java$", "") + ".java") + ".java";
  }

  /** One answer's Params for the program: its Params with the answer as the prompt (its input folder's rows left out). */
  public static String paramsFor(List<ProgramParams.Param> ps, String saved, String text) {
    String[] values = ProgramParams.values(ps, saved == null ? "" : saved, "");
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      ProgramParams.Param p = ps.get(i);
      if (java.util.Arrays.asList(FolderRuns.OWN).contains(p.token)) values[i] = "";
      else if (p.flag && p.token.equals("--prompt")) values[i] = ProgramParams.oneLine(text);
    }
    return ProgramParams.build(ps, values);
  }

  /** The batch: each answer ({its file's name, its text}) rendered by `program`; null when there is none. */
  public static BatchQueue start(String program, List<ProgramParams.Param> ps, String saved, List<String[]> answers) {
    List<String> params = new ArrayList<String>(), labels = new ArrayList<String>();
    for (String[] a : answers) {
      if (a[1] == null || a[1].trim().isEmpty()) continue;
      params.add(paramsFor(ps, saved, a[1]));
      labels.add(a[0]);
    }
    if (params.isEmpty()) return null;
    BatchQueue q = BatchQueue.files(program, ps, params, labels, labels, null);
    if (q != null) q.notes.add("Auto render: SogniChat's answer" + (labels.size() == 1 ? "" : "s") + " as " + SogniSections.app(program) + "'s prompt; its Params put back after each run");
    return q;
  }
}
