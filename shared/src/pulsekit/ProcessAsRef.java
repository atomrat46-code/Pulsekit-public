package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Picture ready > Process as a reference: the picture a run made goes into another program as its
 * Reference file 1 or 2 (SogniPedit's picture to edit or second picture, SogniVideo's first or last
 * frame...). The program is one run recently that takes pictures, the prompt one of its recent
 * runs; the program opens in PyJav with that run's settings and the picture in the slot, ready to
 * Run. The choices are kept (process-ref.txt) and picked again next time.
 */
public final class ProcessAsRef {
  private ProcessAsRef() {}

  static final String FILE = "process-ref.txt";

  /** What was picked last: the program, the run's arguments, and the slot (1 or 2). */
  public static final class Choice {
    public String program = "";
    public String extra = "";
    public int slot = 1;
  }

  /** The picture rows of a program, in its usage's order (at most two): "--image", "--image2". */
  public static List<String> slots(String source) {
    List<String> out = new ArrayList<String>();
    for (ProgramParams.Param p : ProgramParams.parse(source == null ? "" : source)) {
      if (out.size() >= 2) break;
      if (p.flag && p.takesValue && ProgramParams.previewsPicture(p) && !out.contains(p.token)) out.add(p.token);
    }
    return out;
  }

  /** The recent programs that take a picture, the one run last first. */
  public static List<String> programs(List<PyJavRecent.Item> recent, Sources sources) {
    List<String> out = new ArrayList<String>();
    for (String name : PyJavRecent.programs(recent)) {
      if (!slots(sources.source(name, recent)).isEmpty()) out.add(name);
    }
    return out;
  }

  /** A program's source: the app's own copy of a bundled program, else the one kept with its runs. */
  public interface Sources {
    String source(String name, List<PyJavRecent.Item> recent);
  }

  /** The kept source of a program's last run ("" when none). */
  public static String keptSource(String name, List<PyJavRecent.Item> recent) {
    for (PyJavRecent.Item item : PyJavRecent.runsOf(recent, name)) if (item.source.length() > 0) return item.source;
    return "";
  }

  /** A run's prompt for the list ("(no prompt)" when it has none), cut to `max` characters. */
  public static String promptLabel(PyJavRecent.Item item, int max) {
    List<String> args = JavaRun.split(item == null ? "" : item.extra);
    for (int i = 0; i + 1 < args.size(); i++) {
      if (!args.get(i).equals("--prompt")) continue;
      String p = args.get(i + 1).replaceAll("\\s+", " ").trim();
      return p.length() > max ? p.substring(0, max - 1) + "…" : p;
    }
    String all = PyJavRecent.argsLabel(item);
    return "(no prompt) " + (all.length() > max ? all.substring(0, max - 1) + "…" : all);
  }

  /** The run's arguments with the picture in the slot's row (its old picture there replaced). */
  public static String withPicture(String extra, String token, String path) {
    List<String> args = JavaRun.split(extra == null ? "" : extra);
    boolean put = false;
    for (int i = 0; i < args.size(); i++) {
      if (!args.get(i).equals(token)) continue;
      if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) args.set(i + 1, path);
      else args.add(i + 1, path);
      put = true;
      break;
    }
    if (!put) {
      args.add(token);
      args.add(path);
    }
    StringBuilder sb = new StringBuilder();
    for (String a : args) sb.append(sb.length() == 0 ? "" : " ").append(JavaRun.quoteArg(a));
    return sb.toString();
  }

  /**
   * The program's saved Params with the picture in the slot's row, when they have that row: saved
   * Params win over the run's arguments (in Params and at Run), so an old picture kept there would
   * stand in for this one. Null when nothing needs changing.
   */
  public static String savedWithPicture(String saved, String token, String path) {
    if (saved == null || !JavaRun.split(saved).contains(token)) return null;
    return withPicture(saved, token, path);
  }

  /** The choice kept last time (an empty one when there is none). */
  public static Choice load(File dir) {
    Choice c = new Choice();
    try {
      File f = new File(dir, FILE);
      if (!f.isFile()) return c;
      String[] lines = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n", -1);
      if (lines.length > 0) c.program = lines[0];
      if (lines.length > 1) c.extra = lines[1].replace("\\n", "\n");
      if (lines.length > 2) c.slot = "2".equals(lines[2].trim()) ? 2 : 1;
    } catch (Exception ignored) {
      // nothing kept
    }
    return c;
  }

  public static void save(File dir, Choice c) {
    try {
      dir.mkdirs();
      String text = c.program + "\n" + c.extra.replace("\n", "\\n") + "\n" + c.slot + "\n";
      java.nio.file.Files.write(new File(dir, FILE).toPath(), text.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // not kept
    }
  }

  /** Where the kept choice is in `programs` (0 when it is not there). */
  public static int programIndex(List<String> programs, Choice c) {
    int i = programs.indexOf(c.program);
    return i < 0 ? 0 : i;
  }

  /** Where the kept run is in a program's runs: the same arguments, else the same prompt; 0 otherwise. */
  public static int runIndex(List<PyJavRecent.Item> runs, Choice c) {
    for (int i = 0; i < runs.size(); i++) if (runs.get(i).extra.equals(c.extra)) return i;
    String want = promptLabel(new PyJavRecent.Item(c.program, c.extra, "", null), 400);
    for (int i = 0; i < runs.size(); i++) if (promptLabel(runs.get(i), 400).equals(want)) return i;
    return 0;
  }
}
