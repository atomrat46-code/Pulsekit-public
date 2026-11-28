package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * File > Logs' Batches: one entry a batch (Run sections, an input folder, Auto render, a chain,
 * SogniChat's folder), written again as it goes: the program, where its files come from, its state
 * (running, finished, stopped, paused, interrupted) and each run's state (done, failed, running,
 * waiting) with its time and result. Written whether or not Logging on is ticked. An entry still
 * "running" when Pulsekit starts again was interrupted (Android closed Pulsekit): said so, and the
 * kept batch (BatchSave) or Skip already processed goes on from there.
 */
public final class BatchLog {
  private BatchLog() {}

  public static final String TYPE = "Batches";
  public static final String RUNNING = "running", DONE = "done", FAILED = "failed", WAITING = "waiting", NOT_RUN = "not run";

  /** One batch's entry. */
  public static final class Entry {
    File file;
    String title;
    String state = RUNNING;
    final long started = System.currentTimeMillis();
    final List<String> about = new ArrayList<String>();
    final List<String> labels = new ArrayList<String>(), states = new ArrayList<String>(), notes = new ArrayList<String>();
    final List<Long> times = new ArrayList<Long>();

    synchronized String text() {
      int done = 0, failed = 0, running = 0, waiting = 0;
      for (String s : this.states) {
        if (s.equals(DONE)) done++;
        else if (s.equals(FAILED)) failed++;
        else if (s.equals(RUNNING)) running++;
        else waiting++;
      }
      StringBuilder sb = new StringBuilder();
      sb.append("State: ").append(this.state).append('\n');
      sb.append("Started: ").append(Logs.time(this.started)).append('\n');
      sb.append("Runs: ").append(this.labels.size()).append(" (").append(done).append(" done, ").append(failed).append(" failed, ")
          .append(running).append(" running, ").append(waiting).append(" not run yet)\n");
      for (String a : this.about) sb.append(a).append('\n');
      sb.append('\n');
      int w = String.valueOf(this.labels.size()).length();
      for (int i = 0; i < this.labels.size(); i++) {
        String n = String.valueOf(i + 1);
        while (n.length() < w) n = " " + n;
        String st = this.states.get(i);
        String pad = st;
        while (pad.length() < 8) pad += " ";
        long t = this.times.get(i).longValue();
        sb.append(n).append("  ").append(pad).append(t > 0 ? Logs.time(t).substring(11) : "        ").append("  ").append(this.labels.get(i));
        String note = this.notes.get(i);
        if (note != null && !note.isEmpty()) sb.append("  → ").append(note);
        sb.append('\n');
      }
      return sb.toString();
    }

    /** Written again (its file kept: the same entry). */
    synchronized void write() {
      String body = this.text();
      if (this.file == null) {
        this.file = Logs.put(TYPE, this.title, body);
        return;
      }
      try {
        String old = new String(java.nio.file.Files.readAllBytes(this.file.toPath()), StandardCharsets.UTF_8);
        int head = old.indexOf("\n\n");
        java.nio.file.Files.write(this.file.toPath(), ((head < 0 ? "Type: " + TYPE + "\nTitle: " + this.title : old.substring(0, head)) + "\n\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
      } catch (Exception ex) {
        // a new entry instead
        this.file = Logs.put(TYPE, this.title, body);
      }
    }

    /** Run `i`'s state (and its result or why), written at once. */
    public synchronized void run(int i, String state, String note) {
      if (i < 0 || i >= this.labels.size()) return;
      this.states.set(i, state);
      this.times.set(i, Long.valueOf(System.currentTimeMillis()));
      if (note != null) this.notes.set(i, note.length() > 160 ? note.substring(0, 160) + "…" : note);
      this.write();
    }

    /** The batch's state: finished, stopped, paused...; the runs still running then "not run" when it ended. */
    public synchronized void state(String s, boolean over) {
      this.state = s;
      if (over) for (int i = 0; i < this.states.size(); i++) if (this.states.get(i).equals(RUNNING)) this.states.set(i, NOT_RUN);
      this.write();
    }

    public synchronized void add(String line) {
      this.about.add(line);
      this.write();
    }

    /** Index of the run labelled `label`, or -1. */
    public synchronized int indexOf(String label) {
      return this.labels.indexOf(label);
    }
  }

  /** A batch begun: its title ("SogniPadd: input folder glamourize"), its runs' labels, more lines about it. */
  public static Entry begin(String title, List<String> labels, List<String> about) {
    Entry e = new Entry();
    e.title = title;
    if (about != null) e.about.addAll(about);
    if (labels != null) for (String l : labels) {
      e.labels.add(l == null || l.isEmpty() ? "(run " + (e.labels.size() + 1) + ")" : l);
      e.states.add(WAITING);
      e.notes.add("");
      e.times.add(Long.valueOf(0));
    }
    e.write();
    return e;
  }

  /** A BatchQueue's entry (its program, where its runs come from, the runs' labels). */
  public static Entry begin(BatchQueue q) {
    String app = SogniSections.app(q.program);
    String kind = q.chained ? "chain" : q.byFiles() ? "input folder" + (q.folder != null ? " " + q.folder : "") : q.bySections() ? "sections" : "batch";
    List<String> labels = new ArrayList<String>();
    for (int i = 0; i < q.runs.size(); i++) labels.add(i < q.labels.size() ? q.labels.get(i) : "");
    List<String> about = new ArrayList<String>();
    about.add("Program: " + q.program);
    if (q.outFolder != null) about.add("Batch folder: " + q.outFolder);
    if (q.group != null && !q.group.isEmpty()) about.add("Batch group: " + q.group);
    for (String n : q.notes) about.add(n);
    return begin(app + ": " + kind + " (" + q.runs.size() + (q.runs.size() == 1 ? " run)" : " runs)"), labels, about);
  }

  /** Entries left "running" by a process that is gone (Android closed Pulsekit): said to be interrupted. Called once at start. */
  public static int interrupted() {
    int n = 0;
    for (Logs.Entry e : Logs.entries(TYPE)) {
      String t = Logs.text(e);
      if (!t.contains("\n\nState: " + RUNNING + "\n")) continue;
      String now = t.replace("\n\nState: " + RUNNING + "\n", "\n\nState: interrupted (Pulsekit was closed while it ran; a kept batch is offered to go on, a folder's answered files are left out with Skip already processed)\n")
          .replaceAll("(?m)^(\\s*\\d+  )running ", "$1stopped ").replaceAll("(?m)^(Runs: .*), (\\d+) running, ", "$1, $2 stopped, ");
      try {
        java.nio.file.Files.write(e.file.toPath(), now.getBytes(StandardCharsets.UTF_8));
        n++;
      } catch (Exception ignored) {
        // left as it is
      }
    }
    return n;
  }
}
