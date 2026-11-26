package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * BatchRun's plan, run by PyJav: the program's runs one after another (each an ordinary run, with
 * its log, File > Downloads and reconnects), then with --join JoinVideo on the clips in order.
 * What each run makes is kept on disk and not shown (no player or picture window for each); Stop
 * batch ends it after the run in progress. One batch at a time, the same on the phone and the desktop.
 */
public final class BatchQueue {
  public static final String PLANNER = "BatchRun.java";

  /** The program run ("SogniVideo.java"), its runs' arguments, and the next run's place. */
  public String program = "";
  public final List<List<String>> runs = new ArrayList<List<String>>();
  public int next;
  /** --join: JoinVideo on the clips once every run is over; true while JoinVideo runs. */
  public boolean join;
  public boolean joining;
  /** The files the runs made (paths), in order; how many runs succeeded and failed. */
  public final List<String> made = new ArrayList<String>();
  public int ok;
  public int failed;
  /** Picture ready's and Video ready's button while files are made one after another (a batch, a folder run). */
  public static final String STOP_RUN = "Stop run";

  /** The Running line's count of a batch's runs ("Batch 3/20"), or null (no batch, JoinVideo at its end, or one run only). */
  public static String runCount() {
    BatchQueue q = current();
    if (q == null || q.joining || q.runs.size() < 2 || q.next < 1) return null;
    return "Batch " + Math.min(q.next, q.runs.size()) + "/" + q.runs.size() + (q.usageShort == null ? "" : " \u00b7 " + q.usageShort);
  }

  /** A batch going (not stopped yet). */
  public static boolean going() {
    BatchQueue q = current();
    return q != null && !q.stopping;
  }

  /** Stop batch was pressed: no run after the one in progress. */
  public boolean stopping;

  /** Params' Batch folder, its % codes filled in at the batch's first run (every run's files there); null: Download. */
  public String outFolder;

  /** Sogni's usage read before the batch's first run (Params' Usage warning). */
  public boolean usageChecked;
  /** Sogni usage as read before the batch (the log's line), kept for the running line and the summary; null when not read. */
  public String usage;
  /** Its short form for the running line ("Sogni 85.5%"). */
  public String usageShort;

  /** A chain's batch (Chain: SogniPadd's pictures or prompts going on); its runs chain no further. */
  public boolean chained;
  /** What the batch's runs made for a chain to go on with (SogniPadd with Chain ticked). */
  public final List<Chain.Item> chainItems = new ArrayList<Chain.Item>();

  /** Params' Batch group, its % codes filled in at the batch's first run (every run's files put in it); null: none. */
  public String group;

  /** The batch `program`'s run is one of (not JoinVideo at the end), or null. */
  public static BatchQueue runOf(String program) {
    BatchQueue q = current();
    return q != null && !q.joining && q.takes(program) ? q : null;
  }

  /**
   * Run sections (the Sogni image and video programs' Params): each run is the program's Params with
   * one saved section's entries, not BatchRun's arguments. Its Params, label and what it made.
   */
  public final List<String> params = new ArrayList<String>();
  public final List<String> labels = new ArrayList<String>();
  public final List<String> said = new ArrayList<String>();
  /** Run sections: the program's Params rows (the argument line's own switches are left out for each run). */
  public List<ProgramParams.Param> ps = new ArrayList<ProgramParams.Param>();

  /** An input folder's runs (FolderRuns): each file's name, and Skip already processed's list (each done file added). */
  public final List<String> fileNames = new ArrayList<String>();
  public String doneKey;
  /** What starting it said (files left out, Max files, the section): first in its summary. */
  public final List<String> notes = new ArrayList<String>();

  /** An input folder's run: the argument line with %f (in its output name) the file's name, for the run in progress. */
  public String withFile(String line) {
    if (line == null || !this.byFiles() || this.next < 1 || this.next > this.fileNames.size()) return line;
    String stem = this.fileNames.get(this.next - 1).replaceAll("\\.[A-Za-z0-9]{1,7}$", "").replaceAll("[\\\\/:*?\"<>| ]", "_");
    return line.replace("%f", stem);
  }

  /** Whether the runs are an input folder's files (FolderRuns) rather than sections. */
  public boolean byFiles() {
    return !this.fileNames.isEmpty();
  }

  /** An input folder's runs: each its own Params (`params`), labelled with its file. */
  public static synchronized BatchQueue files(String program, List<ProgramParams.Param> ps, List<String> params, List<String> labels, List<String> names, String doneKey) {
    if (program == null || params == null || params.isEmpty()) return null;
    BatchQueue q = new BatchQueue();
    q.program = program;
    if (ps != null) q.ps = ps;
    q.params.addAll(params);
    q.labels.addAll(labels);
    q.fileNames.addAll(names);
    q.doneKey = doneKey;
    for (int i = 0; i < params.size(); i++) q.runs.add(new ArrayList<String>());
    now = q;
    return q;
  }

  private static BatchQueue now;

  private BatchQueue() {}

  public static synchronized BatchQueue current() {
    return now;
  }

  public static synchronized void stop() {
    now = null;
  }

  /**
   * BatchRun's run is over: its plan ("Batch program:", "Batch join:" and "Batch run:" lines) starts
   * the batch, unless it was only planned (--plan) or failed. Null when there is nothing to run.
   */
  public static synchronized BatchQueue start(String program, List<String> argv, String status, String log) {
    if (!PLANNER.equals(program) || status == null || !status.startsWith("Succeeded") || (argv != null && argv.contains("--plan")) || log == null) return null;
    BatchQueue q = new BatchQueue();
    for (String line : log.split("\n")) {
      String t = line.trim();
      if (t.startsWith("Batch program: ")) q.program = t.substring(15).trim();
      else if (t.startsWith("Batch join:")) q.join = true;
      else if (t.startsWith("Batch run: ")) q.runs.add(JavaRun.split(t.substring(11)));
    }
    if (q.program.length() == 0 || q.runs.isEmpty()) return null;
    now = q;
    return q;
  }

  /**
   * Run sections: `program`'s runs one after another, each with the Params `saved` (the rows as
   * they are now) and one section's entries in its rows (the rows a section keeps and it leaves
   * empty, empty). Null when no section is picked.
   */
  public static synchronized BatchQueue sections(String program, String saved, List<ProgramParams.Param> ps, List<PromptVault.SogniSection> picked) {
    if (program == null || picked == null || picked.isEmpty()) return null;
    BatchQueue q = new BatchQueue();
    q.program = program;
    if (ps != null) q.ps = ps;
    for (PromptVault.SogniSection s : picked) {
      q.params.add(paramsFor(ps, saved, s));
      q.labels.add(SogniSections.label(s));
      q.runs.add(new ArrayList<String>());
    }
    now = q;
    return q;
  }

  /** The Params `saved` with a section's entries in the rows a section keeps. */
  public static String paramsFor(List<ProgramParams.Param> ps, String saved, PromptVault.SogniSection s) {
    String[] values = ProgramParams.values(ps, saved == null ? "" : saved, "");
    java.util.Map<String, String> f = SogniSections.read(s.text);
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      if (!SogniSections.kept(ps.get(i))) continue;
      String v = f.get(ps.get(i).token);
      values[i] = v == null ? "" : v;
    }
    return ProgramParams.build(ps, values);
  }

  /** Whether the runs are sections' (Params) rather than BatchRun's arguments. */
  public boolean bySections() {
    return !this.params.isEmpty();
  }

  /** True when this finished run is the batch's (its program, or JoinVideo at the end). */
  public boolean takes(String program) {
    return this.joining ? "JoinVideo.java".equals(program) : this.program.equals(program);
  }

  public boolean hasNext() {
    return !this.stopping && this.next < this.runs.size();
  }

  /** The next run's arguments. */
  public List<String> nextArgs() {
    return new ArrayList<String>(this.runs.get(this.next++));
  }

  /** "Batch: run 2 of 5 (SogniVideo)" for the log and the status line. */
  public String progress() {
    if (this.byFiles()) return "Files: run " + this.next + " of " + this.runs.size() + " (" + this.program.replaceAll("\\.(java|py|jar)$", "") + ") \u00b7 " + this.labels.get(Math.max(0, this.next - 1));
    if (this.bySections()) return "Sections: run " + this.next + " of " + this.runs.size() + " (" + this.program.replaceAll("\\.(java|py|jar)$", "") + ") \u00b7 " + this.labels.get(Math.max(0, this.next - 1));
    return "Batch: run " + this.next + " of " + this.runs.size() + " (" + this.program.replace(".java", "") + ")";
  }

  /** A run of the batch is over: its verdict and the files it saved (paths). */
  public synchronized void done(String status, List<String> files) {
    // JoinVideo at the end is not one of the runs.
    if (this.joining) return;
    if (status != null && status.startsWith("Succeeded")) {
      this.ok++;
      // Skip already processed: the file done kept.
      if (this.doneKey != null && this.next >= 1 && this.next <= this.fileNames.size()) {
        List<String> one = new ArrayList<String>();
        one.add(this.fileNames.get(this.next - 1));
        ChatFolder.markDone(this.doneKey, one);
      }
    } else this.failed++;
    if (files != null) this.made.addAll(files);
    if (this.bySections() && this.said.size() < this.next) {
      StringBuilder sb = new StringBuilder(this.labels.get(this.next - 1)).append(": ");
      String v = status == null || status.isEmpty() ? "no verdict" : status.startsWith("Succeeded") && files != null && !files.isEmpty() ? "Succeeded" : status;
      sb.append(v.length() > 80 ? v.substring(0, 80) + "\u2026" : v);
      if (files != null && !files.isEmpty()) {
        sb.append(" \u2192 ");
        for (int i = 0; i < files.size(); i++) sb.append(i == 0 ? "" : ", ").append(new File(files.get(i)).getName());
      }
      this.said.add(sb.toString());
    }
  }

  /** The clips to join (MP4s the runs made, in order). */
  public List<String> clips() {
    List<String> out = new ArrayList<String>();
    for (String f : this.made) if (f.toLowerCase(java.util.Locale.ROOT).matches(".+\\.(mp4|m4v|mov)") && !f.toLowerCase(java.util.Locale.ROOT).matches(".+-merged(\\(\\d+\\))?\\.mp4")) out.add(f);
    return out;
  }

  /** True when JoinVideo is next: --join, every run over (not stopped), two clips at least. */
  public boolean joinNext() {
    return this.join && !this.joining && !this.stopping && this.next >= this.runs.size() && this.clips().size() >= 2;
  }

  /** JoinVideo's arguments: a list of the clips written into `dir`. */
  public List<String> joinArgs(File dir) throws java.io.IOException {
    dir.mkdirs();
    File list = new File(dir, "batch-clips.txt");
    StringBuilder sb = new StringBuilder();
    for (String c : this.clips()) sb.append(c).append('\n');
    java.nio.file.Files.write(list.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    this.joining = true;
    List<String> out = new ArrayList<String>();
    out.add("--list");
    out.add(list.getAbsolutePath());
    return out;
  }

  /** The batch is over: what it did, for the log. */
  public String summary() {
    StringBuilder sb = new StringBuilder(this.byFiles() ? (this.stopping ? "Files stopped: " : "Files done: ")
        : this.bySections() ? (this.stopping ? "Sections stopped: " : "Sections done: ") : this.stopping ? "Batch stopped: " : "Batch done: ");
    sb.append(this.ok).append(" of ").append(this.runs.size()).append(" runs succeeded");
    if (this.failed > 0) sb.append(", ").append(this.failed).append(" failed");
    if (this.stopping && this.next < this.runs.size()) sb.append(", ").append(this.runs.size() - this.next).append(" not run");
    for (String n : this.notes) sb.append("\n  ").append(n);
    for (String line : this.said) sb.append("\n  ").append(line);
    if (this.usage != null) sb.append("\n  ").append(this.usage);
    return sb.toString();
  }
}
