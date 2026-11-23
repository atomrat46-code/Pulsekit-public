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
  /** Stop batch was pressed: no run after the one in progress. */
  public boolean stopping;

  /**
   * Run sections (the Sogni image and video programs' Params): each run is the program's Params with
   * one saved section's entries, not BatchRun's arguments. Its Params, label and what it made.
   */
  public final List<String> params = new ArrayList<String>();
  public final List<String> labels = new ArrayList<String>();
  public final List<String> said = new ArrayList<String>();
  /** Run sections: the program's Params rows (the argument line's own switches are left out for each run). */
  public List<ProgramParams.Param> ps = new ArrayList<ProgramParams.Param>();

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

  /** The next section's Params (Run sections). */
  public String nextParams() {
    return this.params.get(this.next++);
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
    if (this.bySections()) return "Sections: run " + this.next + " of " + this.runs.size() + " (" + this.program.replaceAll("\\.(java|py|jar)$", "") + ") \u00b7 " + this.labels.get(Math.max(0, this.next - 1));
    return "Batch: run " + this.next + " of " + this.runs.size() + " (" + this.program.replace(".java", "") + ")";
  }

  /** A run of the batch is over: its verdict and the files it saved (paths). */
  public synchronized void done(String status, List<String> files) {
    // JoinVideo at the end is not one of the runs.
    if (this.joining) return;
    if (status != null && status.startsWith("Succeeded")) this.ok++;
    else this.failed++;
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
    StringBuilder sb = new StringBuilder(this.bySections() ? (this.stopping ? "Sections stopped: " : "Sections done: ") : this.stopping ? "Batch stopped: " : "Batch done: ");
    sb.append(this.ok).append(" of ").append(this.runs.size()).append(" runs succeeded");
    if (this.failed > 0) sb.append(", ").append(this.failed).append(" failed");
    if (this.stopping && this.next < this.runs.size()) sb.append(", ").append(this.runs.size() - this.next).append(" not run");
    for (String line : this.said) sb.append("\n  ").append(line);
    return sb.toString();
  }
}
