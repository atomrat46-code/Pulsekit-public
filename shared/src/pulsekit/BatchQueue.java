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
    return "Batch: run " + this.next + " of " + this.runs.size() + " (" + this.program.replace(".java", "") + ")";
  }

  /** A run of the batch is over: its verdict and the files it saved (paths). */
  public synchronized void done(String status, List<String> files) {
    // JoinVideo at the end is not one of the runs.
    if (this.joining) return;
    if (status != null && status.startsWith("Succeeded")) this.ok++;
    else this.failed++;
    if (files != null) this.made.addAll(files);
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
    StringBuilder sb = new StringBuilder(this.stopping ? "Batch stopped: " : "Batch done: ");
    sb.append(this.ok).append(" of ").append(this.runs.size()).append(" runs succeeded");
    if (this.failed > 0) sb.append(", ").append(this.failed).append(" failed");
    if (this.stopping && this.next < this.runs.size()) sb.append(", ").append(this.runs.size() - this.next).append(" not run");
    return sb.toString();
  }
}
