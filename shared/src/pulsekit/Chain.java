package pulsekit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SogniPadd's Chain: once its run (or batch) is over, each picture it made goes on to another Sogni
 * program, one run each as a batch (Stop batch ends it). A program that takes a picture (SogniVideo,
 * SogniPedit: --image) gets the picture, with its own Params' prompt; one
 * that takes only a prompt (SogniTextVideo, SogniMusic) gets the prompt SogniPadd's run had.
 *
 * Batch prompt (the Sogni programs' Params): a text, typed or taken from a file or a saved section,
 * added to the prompt a chain passes on. The target's own Batch prompt, when it has one, is used
 * instead of SogniPadd's.
 */
public final class Chain {
  private Chain() {}

  /** Params' rows (PyJav takes them off; the programs never see them). */
  public static final String CHAIN = "--pk-chain", CHAIN_TO = "--pk-chain-to", BATCH_PROMPT = "--pk-batch-prompt", BATCH_TEXT = "--pk-batch-prompt-text";
  public static final String CHAIN_LABEL = "Chain: when the run or batch is over, its pictures go on to the program below (one run each)";
  public static final String CHAIN_TO_LABEL = "Chain to: a picture program gets each picture (its own prompt); a prompt-only program gets the run's prompt";
  public static final String BATCH_PROMPT_LABEL = "Batch prompt: added to the prompt a chain passes on (the chained program's own is used instead of SogniPadd's)";
  public static final String BATCH_PROMPT_CHAT = "Batch prompt: added to the prompt of each picture, video or music the tools make (Auto render: the rendering program's own Batch prompt)";
  public static final String BATCH_TEXT_LABEL = "Batch prompt text: type it, or Select file / Browse sections";
  public static final String BATCH_TEXT_HINT = "e.g. slow camera pan, cinematic light";
  public static final String BROWSE_SECTIONS = "Browse sections";

  /** The programs a chain goes on to (their .java names), in the list's order. */
  public static final String[] TARGETS = {"SogniTextVideo.java", "SogniVideo.java", "SogniPedit.java", "SogniMusic.java"};

  /** The program with Chain (SogniPadd). */
  public static boolean chains(String program) {
    return "SogniPadd.java".equals(program);
  }

  /** The programs with Batch prompt: the Sogni programs a prompt is given to. */
  public static boolean batchPrompted(String program) {
    return Arrays.asList("SogniPadd.java", "SogniTextVideo.java", "SogniVideo.java", "SogniPedit.java", "SogniMusic.java", "SogniChat.java").contains(program);
  }

  /** The program's rows: Chain and Chain to (SogniPadd), Batch prompt and its text. */
  public static List<ProgramParams.Param> rows(String program) {
    List<ProgramParams.Param> out = new ArrayList<ProgramParams.Param>();
    if (chains(program)) {
      ProgramParams.Param c = flag(CHAIN, CHAIN_LABEL);
      out.add(c);
      ProgramParams.Param to = value(CHAIN_TO, CHAIN_TO_LABEL, "");
      to.dropdown = true;
      to.choices = new String[TARGETS.length];
      to.choiceValues = new String[TARGETS.length];
      for (int i = 0; i < TARGETS.length; i++) {
        to.choices[i] = SogniSections.app(TARGETS[i]) + (takesPicture(TARGETS[i]) ? " (picture)" : " (prompt)");
        to.choiceValues[i] = SogniSections.app(TARGETS[i]);
      }
      to.showWhen = CHAIN;
      out.add(to);
    }
    if (batchPrompted(program)) {
      out.add(flag(BATCH_PROMPT, "SogniChat.java".equals(program) ? BATCH_PROMPT_CHAT : BATCH_PROMPT_LABEL));
      ProgramParams.Param t = value(BATCH_TEXT, BATCH_TEXT_LABEL, BATCH_TEXT_HINT);
      // (Shown always: its Select file and Browse sections are there to fill it before it is ticked.)
      out.add(t);
    }
    return out;
  }

  static ProgramParams.Param flag(String token, String label) {
    ProgramParams.Param p = new ProgramParams.Param();
    p.token = token;
    p.flag = true;
    p.takesValue = false;
    p.optional = true;
    p.label = label;
    return p;
  }

  static ProgramParams.Param value(String token, String label, String hint) {
    ProgramParams.Param p = new ProgramParams.Param();
    p.token = token;
    p.flag = true;
    p.takesValue = true;
    p.optional = true;
    p.label = label;
    p.hint = hint;
    return p;
  }

  /** Batch prompt's text row (Select file and Browse sections beside it). */
  public static boolean batchTextRow(ProgramParams.Param p) {
    return p != null && BATCH_TEXT.equals(p.token);
  }

  /** SogniChat's Batch prompt (ticked, text given): given to it as --batch_prompt (its tools' prompts get it); before strip. */
  public static void forChat(String program, List<String> argv) {
    if (!"SogniChat.java".equals(program) || argv == null || !argv.contains(BATCH_PROMPT)) return;
    String t = ChatFolder.arg(argv, BATCH_TEXT);
    if (t == null || t.trim().isEmpty() || argv.contains("--batch_prompt")) return;
    argv.add("--batch_prompt");
    argv.add(t.trim());
  }

  /** The rows taken off a run's arguments. */
  public static void strip(List<String> argv) {
    if (argv == null) return;
    while (argv.remove(CHAIN)) {}
    while (argv.remove(BATCH_PROMPT)) {}
    DownloadMove.takeValue(argv, CHAIN_TO);
    DownloadMove.takeValue(argv, BATCH_TEXT);
  }

  /** A picture program: SogniVideo, SogniPedit. */
  public static boolean takesPicture(String program) {
    return "SogniVideo.java".equals(program) || "SogniPedit.java".equals(program);
  }

  /** The program the saved Params chain to (".java"), or null (Chain not ticked, none picked, or not SogniPadd's). */
  public static String target(String program, String saved) {
    if (!chains(program) || saved == null) return null;
    List<String> a = JavaRun.split(saved);
    if (!a.contains(CHAIN)) return null;
    String to = ChatFolder.arg(a, CHAIN_TO);
    if (to == null || to.trim().isEmpty()) return null;
    String name = to.trim().replaceAll("(?i)\\.java$", "") + ".java";
    for (String t : TARGETS) if (t.equalsIgnoreCase(name)) return t;
    return null;
  }

  /** The saved Params' Batch prompt: its text when ticked and given, else null. */
  public static String batchPrompt(String saved) {
    if (saved == null) return null;
    List<String> a = JavaRun.split(saved);
    if (!a.contains(BATCH_PROMPT)) return null;
    String t = ChatFolder.arg(a, BATCH_TEXT);
    return t == null || t.trim().isEmpty() ? null : t.trim();
  }

  /** `prompt` with the batch prompt after it (the target's when it has one, else the source's); as it is when neither. */
  public static String withBatch(String prompt, String sourceBatch, String targetBatch) {
    String extra = targetBatch != null ? targetBatch : sourceBatch;
    String p = prompt == null ? "" : prompt.trim();
    if (extra == null || extra.isEmpty()) return p;
    // Already there (the run's prompt had its own batch prompt added): not added twice.
    if (p.toLowerCase(java.util.Locale.ROOT).endsWith(extra.toLowerCase(java.util.Locale.ROOT))) return p;
    return p.isEmpty() ? extra : p + (p.matches(".*[.,;:!?]$") ? " " : ", ") + extra;
  }

  /** One picture a chain goes on with: its label (the picture's name), the prompt its run had, the picture's path. */
  public static final class Item {
    public final String label, prompt, picture;

    public Item(String label, String prompt, String picture) {
      this.label = label;
      this.prompt = prompt;
      this.picture = picture;
    }
  }

  /** The picture row of a picture program: --image. */
  static int pictureRow(String target, List<ProgramParams.Param> ps) {
    for (int i = 0; i < ps.size(); i++) if (ps.get(i).flag && "--image".equals(ps.get(i).token)) return i;
    return -1;
  }

  /** One run's Params for the target: its Params, the prompt (and picture) as above, its input folder's and chain's rows left out. */
  public static String paramsFor(String target, List<ProgramParams.Param> ps, String saved, Item item, String sourceBatch) {
    String[] values = ProgramParams.values(ps, saved == null ? "" : saved, "");
    String targetBatch = batchPrompt(saved);
    int pic = takesPicture(target) ? pictureRow(target, ps) : -1;
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      ProgramParams.Param p = ps.get(i);
      if (Arrays.asList(FolderRuns.OWN).contains(p.token) || CHAIN.equals(p.token) || CHAIN_TO.equals(p.token)) values[i] = "";
      else if (i == pic) values[i] = item.picture;
      else if (p.flag && p.token.equals("--prompt")) {
        // A picture program keeps its own prompt (the batch prompt added); a prompt-only one gets the run's.
        String base = takesPicture(target) ? values[i] : item.prompt;
        values[i] = ProgramParams.oneLine(withBatch(base, sourceBatch, targetBatch));
      }
    }
    return ProgramParams.build(ps, values);
  }

  /** The chain's batch: each item run by `target`; null when there is none (a picture program and no pictures). */
  public static BatchQueue start(String source, String target, List<ProgramParams.Param> ps, String saved, List<Item> items, String sourceBatch) {
    List<String> params = new ArrayList<String>(), labels = new ArrayList<String>();
    for (Item it : items) {
      if (takesPicture(target) ? it.picture == null : it.prompt == null || it.prompt.trim().isEmpty() && sourceBatch == null && batchPrompt(saved) == null) continue;
      params.add(paramsFor(target, ps, saved, it, sourceBatch));
      labels.add(it.label);
    }
    if (params.isEmpty()) return null;
    BatchQueue q = BatchQueue.files(target, ps, params, labels, labels, null);
    if (q == null) return null;
    q.chained = true;
    String batch = batchPrompt(saved) != null ? SogniSections.app(target) + "'s batch prompt added" : sourceBatch != null ? SogniSections.app(source) + "'s batch prompt added" : "no batch prompt";
    q.notes.add("Chain: " + SogniSections.app(source) + "'s " + (takesPicture(target) ? "pictures" : "prompts") + " to " + SogniSections.app(target)
        + (takesPicture(target) ? " (each picture its input)" : " (each run's prompt)") + "; " + batch + "; its Params put back after each run");
    return q;
  }

  /**
   * A batch run's Params with the program's Batch prompt (ticked, text given) added after its prompt (the
   * program's own batches: an input folder, Run sections, Auto render from SogniChat). As they are without one.
   */
  public static String addBatch(List<ProgramParams.Param> ps, String line) {
    String extra = batchPrompt(line);
    if (extra == null || ps == null) return line;
    String[] values = ProgramParams.values(ps, line, "");
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      ProgramParams.Param p = ps.get(i);
      if (p.flag && p.token.equals("--prompt")) {
        values[i] = ProgramParams.oneLine(withBatch(values[i], extra, null));
        return ProgramParams.build(ps, values);
      }
    }
    return line;
  }

  /** The prompt a run had (its --prompt), or "". */
  public static String promptOf(List<String> argv) {
    String p = ChatFolder.arg(argv, "--prompt");
    return p == null ? "" : p;
  }

  /** A picture's name. */
  public static boolean picture(String name) {
    return name != null && name.toLowerCase(java.util.Locale.ROOT).matches(".+\\.(png|jpe?g|webp)");
  }
}
