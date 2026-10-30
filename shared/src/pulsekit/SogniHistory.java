package pulsekit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The Sogni workflows PyJav runs have started, newest first (at most 50), read from each run's log
 * ("Workflow: <id>", "Status: ...", "Music: ..."). Params offers them for SogniMusic's --workflow,
 * which fetches a finished run's result again without paying for a new one.
 */
public final class SogniHistory {
  public static final int MAX = 50;

  /** One run: its id, when it was seen, its last status, and the start of its prompt. */
  public static final class Entry {
    public final String id;
    public final long time;
    public final String status;
    public final String prompt;

    Entry(String id, long time, String status, String prompt) {
      this.id = id;
      this.time = time;
      this.status = status;
      this.prompt = prompt;
    }

    /** "10-05 14:22 · completed · Rock Ballad. Instrumental, only…" */
    public String label() {
      String when = new SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new Date(this.time));
      String p = this.prompt.length() > 60 ? this.prompt.substring(0, 60) + "…" : this.prompt;
      return when + " · " + (this.status.length() == 0 ? "started" : this.status) + (p.length() == 0 ? "" : " · " + p);
    }
  }

  private static File file;

  private SogniHistory() {}

  /** Where the list lives: the app's private folder (Android) or ~/.pulsekit (desktop). */
  public static void init(File dir) {
    file = dir == null ? null : new File(dir, "sogni_workflows.txt");
    List<String> closed;
    synchronized (CLOSED) {
      closed = new ArrayList<String>(CLOSED);
      CLOSED.clear();
    }
    for (String id : closed) closed(id, "");
  }

  /** A download's status while it goes, and after Pulsekit was closed during it. */
  public static final String DOWNLOADING = "running";
  public static final String CLOSED_STATUS = "Pulsekit was closed during the download: Resume download fetches it";
  /** Runs found closed before the list's folder was known. */
  private static final List<String> CLOSED = new ArrayList<String>();

  /**
   * The workflow a run printed, kept the moment it is known (Android may close Pulsekit before the
   * run ends, and its id was lost): "downloading", with the program's name in place of a prompt.
   */
  public static synchronized void started(String id, String program, long now) {
    if (file == null || id == null || id.trim().length() == 0 || id.indexOf('\t') >= 0) return;
    List<Entry> list = entries();
    for (Entry e : list) if (e.id.equals(id.trim())) return;
    list.add(0, new Entry(id.trim(), now, DOWNLOADING, program == null ? "" : program.replaceAll("\\.(java|py|jar|class)$", "")));
    while (list.size() > MAX) list.remove(list.size() - 1);
    write(list);
  }

  /** The run is over: its workflow "completed" when it succeeded (downloaded), else "failed" (Resume can fetch it). */
  public static synchronized void finished(String id, boolean ok) {
    if (file == null || id == null || id.trim().length() == 0) return;
    List<Entry> list = entries();
    for (int i = 0; i < list.size(); i++) {
      Entry e = list.get(i);
      if (!e.id.equals(id.trim())) continue;
      String status = ok ? "completed" : (e.status.length() == 0 || DOWNLOADING.equals(e.status) || "completed".equals(e.status) ? "failed" : e.status);
      if (status.equals(e.status)) return;
      list.set(i, new Entry(e.id, e.time, status, e.prompt));
      write(list);
      return;
    }
  }

  /** A run Pulsekit was closed during: its workflow says so (and is listed, if it was not). */
  public static synchronized void closed(String id, String program) {
    if (id == null || id.trim().length() == 0 || id.indexOf('\t') >= 0) return;
    if (file == null) {
      synchronized (CLOSED) {
        if (!CLOSED.contains(id.trim())) CLOSED.add(id.trim());
      }
      return;
    }
    List<Entry> list = entries();
    for (int i = 0; i < list.size(); i++) {
      Entry e = list.get(i);
      if (!e.id.equals(id.trim())) continue;
      if (e.status.length() > 0 && !DOWNLOADING.equals(e.status)) return;
      list.set(i, new Entry(e.id, e.time, CLOSED_STATUS, e.prompt));
      write(list);
      return;
    }
    list.add(0, new Entry(id.trim(), System.currentTimeMillis(), CLOSED_STATUS, program == null ? "" : program.replaceAll("\\.(java|py|jar|class)$", "")));
    while (list.size() > MAX) list.remove(list.size() - 1);
    write(list);
  }

  /** Remembers the workflow a run's log names, if any. A run of the same id again updates it. */
  public static synchronized void record(String log, long now) {
    if (file == null || log == null) return;
    String id = null;
    String status = "";
    String prompt = "";
    for (String line : log.split("\n")) {
      String t = line.trim();
      if (t.startsWith("Workflow: ")) id = t.substring(10).trim();
      else if (t.startsWith("Status: ")) status = t.substring(8).trim();
      else if (t.startsWith("Music: ") && prompt.length() == 0) prompt = t.substring(7).trim();
    }
    if (id == null || id.length() == 0 || id.indexOf('\t') >= 0) return;
    List<Entry> list = entries();
    Entry old = null;
    for (Entry e : list) if (e.id.equals(id)) old = e;
    if (old != null) {
      list.remove(old);
      if (prompt.length() == 0) prompt = old.prompt;
      if (status.length() == 0) status = old.status;
    }
    list.add(0, new Entry(id, old != null ? old.time : now, status, prompt));
    while (list.size() > MAX) list.remove(list.size() - 1);
    write(list);
  }

  /** Sogni job ids ("Sogni job: <id>" lines, printed after each download) and the workflow that made them. */
  public static final int MAX_JOBS = 500;

  static File jobsFile() {
    return file == null ? null : new File(file.getParentFile(), "sogni_jobs.txt");
  }

  /** Keeps the job ids a run's log names with the workflow it names (newest first). */
  public static synchronized void jobs(String log, String workflow) {
    File f = jobsFile();
    if (f == null || log == null || workflow == null || workflow.trim().length() == 0) return;
    List<String> found = new ArrayList<String>();
    for (String line : log.split("\n")) {
      String t = line.trim();
      if (t.startsWith("Sogni job: ") && t.length() > 11 && !found.contains(t.substring(11).trim())) found.add(t.substring(11).trim());
    }
    if (found.isEmpty()) return;
    List<String> lines = new ArrayList<String>();
    for (String job : found) lines.add(clean(job) + "\t" + clean(workflow.trim()));
    for (String old : readJobs(f)) {
      String job = old.split("\t", -1)[0];
      if (!found.contains(job)) lines.add(old);
    }
    while (lines.size() > MAX_JOBS) lines.remove(lines.size() - 1);
    StringBuilder sb = new StringBuilder();
    for (String l : lines) sb.append(l).append('\n');
    try {
      if (f.getParentFile() != null && !f.getParentFile().isDirectory()) f.getParentFile().mkdirs();
      FileOutputStream out = new FileOutputStream(f);
      try {
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
      } finally {
        out.close();
      }
    } catch (IOException ignored) {
      // the list stays as it was
    }
  }

  /** The workflow that made a Sogni job, or null when Pulsekit did not keep it. */
  public static synchronized String workflowOfJob(String job) {
    File f = jobsFile();
    if (f == null || job == null) return null;
    for (String l : readJobs(f)) {
      String[] p = l.split("\t", -1);
      if (p.length >= 2 && p[0].equals(job.trim()) && p[1].length() > 0) return p[1];
    }
    return null;
  }

  static List<String> readJobs(File f) {
    List<String> out = new ArrayList<String>();
    if (!f.isFile()) return out;
    try {
      for (String l : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) if (l.indexOf('\t') > 0) out.add(l);
    } catch (IOException ignored) {
      // no list yet
    }
    return out;
  }

  /** The text shown for a made file's Sogni job: its id and, if kept, its workflow. */
  public static String jobText(String job) {
    String wf = workflowOfJob(job);
    return "Sogni job: " + job + "\n" + (wf != null ? "Workflow: " + wf
        : "Its workflow is not in Pulsekit's list (made elsewhere, or before Pulsekit kept job ids).");
  }

  public static List<Entry> entries() {
    List<Entry> out = new ArrayList<Entry>();
    if (file == null || !file.isFile()) return out;
    try {
      String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      for (String line : text.split("\n")) {
        String[] p = line.split("\t", -1);
        if (p.length < 4 || p[0].length() == 0) continue;
        long time = 0;
        try {
          time = Long.parseLong(p[1]);
        } catch (NumberFormatException ignored) {
          // keep the entry with no time
        }
        out.add(new Entry(p[0], time, p[2], p[3]));
      }
    } catch (IOException ignored) {
      // no list yet
    }
    return out;
  }

  /** Labels for Params' Choose list, newest first, and the ids they stand for; null when empty. */
  public static String[][] choices() {
    List<Entry> list = entries();
    if (list.isEmpty()) return null;
    String[] labels = new String[list.size()];
    String[] ids = new String[list.size()];
    for (int i = 0; i < list.size(); i++) {
      labels[i] = list.get(i).label();
      ids[i] = list.get(i).id;
    }
    return new String[][] {labels, ids};
  }

  static void write(List<Entry> list) {
    StringBuilder sb = new StringBuilder();
    for (Entry e : list) {
      sb.append(e.id).append('\t').append(e.time).append('\t').append(clean(e.status)).append('\t').append(clean(e.prompt)).append('\n');
    }
    try {
      File dir = file.getParentFile();
      if (dir != null && !dir.isDirectory()) dir.mkdirs();
      FileOutputStream out = new FileOutputStream(file);
      try {
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
      } finally {
        out.close();
      }
    } catch (IOException ignored) {
      // the list stays as it was
    }
  }

  static String clean(String s) {
    String t = s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    return t.length() > 200 ? t.substring(0, 200) : t;
  }
}
