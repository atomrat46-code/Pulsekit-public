package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * File > Downloads: the Sogni runs PyJav made and the files they downloaded (pictures, videos,
 * music, SogniChat's tool results and its answer as a text file), with the prompt sheet each saved
 * and the prompt library version it was stored as. A run is in progress from Run until its result
 * is in, pending while what it stores (the prompt library, Save prompt) is not done yet, then
 * finished or failed. Kept beside the prompt library (sogni-downloads.txt), each run sealed with the
 * library's key (file names stay private); a run the app was stopped in is failed when it starts
 * again. At most MAX runs, the oldest go first.
 */
public final class SogniDownloads {
  public static final String PENDING = "pending";
  public static final String PROGRESS = "progress";
  public static final String FINISHED = "finished";
  public static final String FAILED = "failed";
  public static final int MAX = 200;

  /** A file a run downloaded: its name, where it is (a path, or a content:// address), its kind. */
  public static final class Item {
    public String name = "";
    public String where = "";
    /** Download/name, a folder's label: where it is, said for people. */
    public String shown = "";

    public String kind() {
      return kindOf(name);
    }
  }

  /** One run. */
  public static final class Run {
    public long id;
    public String program = "";
    public long started;
    public long ended;
    public String state = PROGRESS;
    /** Its verdict ("Succeeded: x.png", "Failed: ..."). */
    public String status = "";
    public final List<Item> items = new ArrayList<Item>();
    /** The prompt sheet it saved (where), and the prompt library version it was stored as (0: none). */
    public String sheet = "";
    public String sheetName = "";
    public long versionId;
    /** The arguments it ran with (Create again loads the program with them). */
    public String args = "";
    /** Its Sogni workflow ("Workflow: <id>" in the run's log), kept as soon as it is known: Resume download joins it again. */
    public String workflow = "";
    /** The exact arguments it ran with (Resume download runs it with them and --workflow). */
    public final List<String> argv = new ArrayList<String>();

    /** "SogniPedit" for SogniPedit.java. */
    public String programName() {
      return program.replaceAll("\\.(java|py|jar|class)$", "");
    }

    /** "10-12 14:22". */
    public String when() {
      return new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(new java.util.Date(started));
    }
  }

  private static File file;
  private static final List<Run> RUNS = new ArrayList<Run>();
  private static long next = 1;

  private SogniDownloads() {}

  /** The list kept in `dir`; a run left in progress or pending (the app stopped) is failed. */
  public static synchronized void init(File dir) {
    File at = dir == null ? null : new File(dir, "sogni-downloads.txt");
    if (at != null && at.equals(file)) return;
    file = at;
    RUNS.clear();
    if (file == null || !file.isFile()) return;
    boolean stopped = false;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
        if (!line.startsWith("run=")) continue;
        Run r;
        try {
          r = decode(Sealed.openText(line.substring(4).trim()));
        } catch (Exception ex) {
          continue;
        }
        if (r == null) continue;
        if (PROGRESS.equals(r.state) || PENDING.equals(r.state)) {
          r.state = FAILED;
          if (r.status.length() == 0 || r.status.startsWith("Running") || r.status.startsWith("Reconnecting") || r.status.startsWith("Resuming") || r.status.startsWith("Queued")) {
            r.status = "Failed: Pulsekit was closed during the run" + (r.workflow.length() > 0 ? " (Resume download fetches it)" : "");
          }
          // Params' --workflow list says so for its workflow.
          if (r.workflow.length() > 0) SogniHistory.closed(r.workflow, r.program);
          stopped = true;
        }
        RUNS.add(r);
        next = Math.max(next, r.id + 1);
      }
    } catch (Exception ignored) {
      // an unreadable list starts empty
    }
    if (stopped) write();
  }

  /** True for the programs whose downloads are listed: the Sogni programs. */
  public static boolean listed(String program) {
    return program != null && program.toLowerCase(Locale.ROOT).startsWith("sogni");
  }

  /** Run pressed: the run is in progress. Its id. */
  public static synchronized long started(String program, long now) {
    Run r = new Run();
    r.id = Math.max(next, now);
    next = r.id + 1;
    r.program = program == null ? "" : program;
    r.started = now;
    r.state = PROGRESS;
    RUNS.add(r);
    while (RUNS.size() > MAX) RUNS.remove(0);
    write();
    return r.id;
  }

  /** The run timed out with its workflow still going on Sogni and is joined again: in progress, saying so. */
  public static synchronized void reconnecting(long id, String note) {
    Run r = run(id);
    if (r == null) return;
    r.state = PROGRESS;
    r.status = note == null ? "" : note;
    write();
  }

  /** The run's Sogni workflow, from its log as it runs ("Workflow: <id>"); kept at once, so a run the app lost can be resumed. */
  public static synchronized void workflow(long id, String workflow) {
    Run r = run(id);
    if (r == null || workflow == null || workflow.trim().length() == 0 || workflow.trim().equals(r.workflow)) return;
    r.workflow = workflow.trim();
    write();
    // Params' --workflow list has it at once too (not only when the run ends).
    SogniHistory.started(r.workflow, r.program, System.currentTimeMillis());
  }

  /** The exact arguments the run started with (Resume download). */
  public static synchronized void argv(long id, List<String> argv) {
    Run r = run(id);
    if (r == null || argv == null) return;
    r.argv.clear();
    for (String a : argv) r.argv.add(a == null ? "" : a);
    write();
  }

  /** The workflow id in a run's log so far ("Workflow: <id>"), or null. */
  public static String workflowIn(String log) {
    if (log == null) return null;
    int at = log.lastIndexOf("Workflow: ");
    if (at < 0) return null;
    int end = log.indexOf('\n', at);
    String id = (end < 0 ? log.substring(at + 10) : log.substring(at + 10, end)).trim();
    return id.length() == 0 || id.indexOf(' ') >= 0 ? null : id;
  }

  /** Resume download is offered: a failed run with its workflow known and nothing downloaded. */
  public static boolean canResume(Run r) {
    return r != null && FAILED.equals(r.state) && r.workflow.length() > 0 && r.items.isEmpty();
  }

  /** Resume download's arguments: the run's own, with --workflow <its workflow> (so the prompt and its sheet stay the same). */
  public static List<String> resumeArgs(Run r) {
    List<String> out = new ArrayList<String>();
    for (int i = 0; i < r.argv.size(); i++) {
      String a = r.argv.get(i);
      if ("--workflow".equals(a)) {
        i++;
        continue;
      }
      out.add(a);
    }
    out.add("--workflow");
    out.add(r.workflow);
    return out;
  }

  public static final String RESUME = "Resume download";

  /** Resume download pressed: the run is in progress again, saying so; the note. */
  public static synchronized String resuming(long id) {
    Run r = run(id);
    if (r == null) return "";
    String note = "Resuming workflow " + r.workflow + "\u2026";
    r.state = PROGRESS;
    r.status = note;
    r.started = r.started <= 0 ? System.currentTimeMillis() : r.started;
    write();
    return note;
  }

  /** Mark as ready: a pending or in-progress run the app lost track of is let go (finished, or failed with nothing saved). */
  public static synchronized void markReady(long id) {
    Run r = run(id);
    if (r == null) return;
    if (!PROGRESS.equals(r.state) && !PENDING.equals(r.state)) return;
    if (r.status.length() == 0 || r.status.startsWith("Reconnecting") || r.status.startsWith("Running")) r.status = r.items.isEmpty() ? "Failed: marked as ready" : "Marked as ready";
    r.state = r.status.startsWith("Succeeded") || (!r.status.startsWith("Failed") && !r.items.isEmpty()) ? FINISHED : FAILED;
    r.ended = System.currentTimeMillis();
    write();
  }

  /** "12 min 5 s" since the run started (for In progress and Pending). */
  public static String elapsed(Run r, long now) {
    long s = Math.max(0, (now - r.started) / 1000);
    if (s < 60) return s + " s";
    if (s < 3600) return (s / 60) + " min " + (s % 60) + " s";
    return (s / 3600) + " h " + ((s / 60) % 60) + " min";
  }

  /** Why a run is where it is, for Pending and In progress: "queued", "reconnecting", "storing in the prompt library". */
  public static String reason(Run r) {
    String st = r.status == null ? "" : r.status;
    if (PENDING.equals(r.state)) return st.startsWith("Failed") ? "failed, finishing up" : "storing in the prompt library";
    if (st.startsWith("Reconnecting")) return "reconnecting";
    if (st.startsWith("Queued")) return "queued";
    return "running";
  }

  /**
   * The run's result is in: its verdict, and the files it says it saved ("saved Download/x.png",
   * "Saved /home/me/Downloads/x.png" lines in `log`); `where` gives a file's content:// address
   * when one is known (by what the line says). Pending until done().
   */
  public static synchronized void result(long id, String status, String log, java.util.Map<String, String> where) {
    Run r = run(id);
    if (r == null) return;
    r.status = status == null ? "" : status;
    r.state = PENDING;
    addSaved(r, log, where);
    write();
  }

  /** More files the run saved (a prompt sheet, after Save prompt), from "saved ..." lines. */
  public static synchronized void saved(long id, String log, java.util.Map<String, String> where) {
    Run r = run(id);
    if (r == null) return;
    addSaved(r, log, where);
    write();
  }

  /** The prompt library version the run's sheet was stored as. */
  public static synchronized void stored(long id, long versionId) {
    Run r = run(id);
    if (r == null || versionId == 0) return;
    r.versionId = versionId;
    write();
  }

  /** What the run stores is stored: finished, or failed when its verdict is not "Succeeded". */
  public static synchronized void done(long id) {
    Run r = run(id);
    if (r == null) return;
    r.state = r.status.startsWith("Succeeded") || (!r.status.startsWith("Failed") && !r.items.isEmpty()) ? FINISHED : FAILED;
    r.ended = System.currentTimeMillis();
    write();
  }

  private static void addSaved(Run r, String log, java.util.Map<String, String> where) {
    if (log == null) return;
    for (String line : log.split("\n")) {
      String t = line.trim();
      String at = t.startsWith("saved ") ? t.substring(6).trim() : t.startsWith("Saved ") ? t.substring(6).trim() : null;
      if (at == null || at.length() == 0 || at.startsWith("prompt ")) continue;
      int slash = Math.max(at.lastIndexOf('/'), at.lastIndexOf('\\'));
      String name = slash >= 0 ? at.substring(slash + 1) : at;
      // The workflow's own records are not downloads.
      if (name.startsWith("sogni_workflow")) continue;
      String uri = where == null ? null : where.get(at);
      if (name.toLowerCase(Locale.ROOT).endsWith(".prompt")) {
        r.sheet = uri != null ? uri : at;
        r.sheetName = name;
        continue;
      }
      boolean seen = false;
      for (Item i : r.items) if (i.shown.equals(at)) seen = true;
      if (seen) continue;
      Item item = new Item();
      item.name = name;
      item.shown = at;
      item.where = uri != null ? uri : at;
      r.items.add(item);
    }
  }

  /** Finished's Sort by: newest first (the default), oldest first, by program, by type (pictures, videos, sounds...). */
  public static final String[] SORTS = {"Newest first", "Oldest first", "Program", "Type"};
  public static String sort = SORTS[0];

  /** Whether the tab shows its runs under program headings (Sort by Program, and the Pending and In progress tabs). */
  public static boolean byProgram(String state) {
    return !FINISHED.equals(state) || "Program".equals(sort);
  }

  /** The runs in one tab (PENDING, PROGRESS, or FINISHED for finished and failed); Finished in the order Sort by says, the others by program, the newest first. */
  public static synchronized List<Run> tab(String state) {
    List<Run> out = new ArrayList<Run>();
    for (Run r : RUNS) {
      boolean in = FINISHED.equals(state) ? FINISHED.equals(r.state) || FAILED.equals(r.state) : state.equals(r.state);
      if (in) out.add(r);
    }
    final String how = FINISHED.equals(state) ? sort : "Program";
    Collections.sort(out, new Comparator<Run>() {
      @Override
      public int compare(Run a, Run b) {
        long ta = a.ended > 0 ? a.ended : a.started, tb = b.ended > 0 ? b.ended : b.started;
        if ("Oldest first".equals(how)) return Long.compare(ta, tb);
        if ("Type".equals(how)) {
          int k = typeOf(a).compareTo(typeOf(b));
          if (k != 0) return k;
        } else if ("Program".equals(how)) {
          int p = a.programName().compareToIgnoreCase(b.programName());
          if (p != 0) return p;
        }
        return Long.compare(tb, ta);
      }
    });
    return out;
  }

  /** A run's type for Sort by Type: its first file's kind ("picture", "video"...), "~" for none (last). */
  static String typeOf(Run r) {
    return r.items.isEmpty() ? "~" : r.items.get(0).kind();
  }

  /** Clear all (Finished): the finished and failed runs leave the list (their files stay); how many went. */
  public static synchronized int clearFinished() {
    int n = 0;
    for (int i = RUNS.size() - 1; i >= 0; i--) {
      if (FINISHED.equals(RUNS.get(i).state) || FAILED.equals(RUNS.get(i).state)) {
        RUNS.remove(i);
        n++;
      }
    }
    write();
    return n;
  }

  /** The arguments a run started with (Create again). */
  public static synchronized void args(long id, String line) {
    Run r = run(id);
    if (r == null) return;
    r.args = line == null ? "" : line;
    write();
  }

  public static synchronized int count() {
    return RUNS.size();
  }

  public static synchronized Run run(long id) {
    for (Run r : RUNS) if (r.id == id) return r;
    return null;
  }

  /** Clear: the item leaves the list (the file stays); a run with no items left, and a failed one, goes. */
  public static synchronized void clear(long id, Item item) {
    Run r = run(id);
    if (r == null) return;
    if (item != null) r.items.remove(item);
    if (item == null || r.items.isEmpty()) RUNS.remove(r);
    write();
  }

  /** The item is now `name` at `where` (renamed or moved). */
  public static synchronized void moved(Item item, String name, String where, String shown) {
    item.name = name;
    item.where = where;
    item.shown = shown;
    write();
  }

  /** The run's prompt sheet and library version are gone (deleted with a file). */
  public static synchronized void unlinked(Run r) {
    r.sheet = "";
    r.sheetName = "";
    r.versionId = 0;
    write();
  }

  /** "picture", "video", "audio", "text" or "file". */
  public static String kindOf(String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (n.matches(".*\\.(png|jpe?g|webp|gif|bmp)$")) return "picture";
    if (n.matches(".*\\.(mp4|m4v|webm|mov|mkv)$")) return "video";
    if (n.matches(".*\\.(mp3|wav|flac|m4a|ogg|aac)$")) return "audio";
    if (n.matches(".*\\.(txt|md|json|prompt)$")) return "text";
    return "file";
  }

  /** "Finished · 10-12 14:22 · Succeeded: x.png". */
  public static String runLabel(Run r) {
    return runLabel(r, System.currentTimeMillis());
  }

  /** In progress and Pending also say how long since Run and why: "In progress \u00b7 10-12 14:22 \u00b7 3 min 5 s, queued". */
  public static String runLabel(Run r, long now) {
    boolean going = PROGRESS.equals(r.state) || PENDING.equals(r.state);
    String state = PROGRESS.equals(r.state) ? "In progress" : PENDING.equals(r.state) ? "Pending" : FINISHED.equals(r.state) ? "Finished" : "Failed";
    String s = r.status.length() > 80 ? r.status.substring(0, 80) + "\u2026" : r.status;
    return state + " \u00b7 " + r.when() + (going ? " \u00b7 " + elapsed(r, now) + ", " + reason(r) : "") + (s.length() == 0 ? "" : " \u00b7 " + s);
  }

  /** The tab's title: "In progress (1)". */
  public static String tabLabel(String state) {
    String name = PROGRESS.equals(state) ? "In progress" : PENDING.equals(state) ? "Pending" : "Finished / failed";
    return name + " (" + tab(state).size() + ")";
  }

  /** The tab shown first: In progress when a run is going, else Pending when one waits, else Finished / failed. */
  public static String firstTab() {
    if (!tab(PROGRESS).isEmpty()) return PROGRESS;
    if (!tab(PENDING).isEmpty()) return PENDING;
    return FINISHED;
  }

  private static void write() {
    if (file == null) return;
    StringBuilder sb = new StringBuilder();
    for (Run r : RUNS) {
      try {
        sb.append("run=").append(Sealed.sealText(encode(r))).append('\n');
      } catch (Exception ex) {
        // without the library's key the list is kept for this session only
      }
    }
    try {
      if (!file.getParentFile().isDirectory()) file.getParentFile().mkdirs();
      java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept in memory
    }
  }

  private static final char F = '\u001f';
  private static final char I = '\u001e';

  static String encode(Run r) {
    StringBuilder sb = new StringBuilder();
    sb.append(r.id).append(F).append(clean(r.program)).append(F).append(r.started).append(F).append(r.ended).append(F).append(r.state).append(F)
        .append(clean(r.status)).append(F).append(clean(r.sheet)).append(F).append(clean(r.sheetName)).append(F).append(r.versionId).append(F);
    for (int i = 0; i < r.items.size(); i++) {
      Item item = r.items.get(i);
      if (i > 0) sb.append(I);
      sb.append(clean(item.name)).append('\t').append(clean(item.where)).append('\t').append(clean(item.shown));
    }
    // After the items (a list from before has none).
    sb.append(F).append(clean(r.args));
    sb.append(F).append(clean(r.workflow)).append(F);
    for (int i = 0; i < r.argv.size(); i++) sb.append(i == 0 ? "" : String.valueOf(I)).append(clean(r.argv.get(i)));
    return sb.toString();
  }

  static Run decode(String s) {
    String[] f = s.split(String.valueOf(F), -1);
    if (f.length < 10) return null;
    Run r = new Run();
    try {
      r.id = Long.parseLong(f[0]);
      r.program = f[1];
      r.started = Long.parseLong(f[2]);
      r.ended = Long.parseLong(f[3]);
      r.state = f[4];
      r.status = f[5];
      r.sheet = f[6];
      r.sheetName = f[7];
      r.versionId = Long.parseLong(f[8]);
    } catch (NumberFormatException ex) {
      return null;
    }
    if (f[9].length() > 0) {
      for (String one : f[9].split(String.valueOf(I))) {
        String[] p = one.split("\t", -1);
        if (p.length < 3) continue;
        Item item = new Item();
        item.name = p[0];
        item.where = p[1];
        item.shown = p[2];
        r.items.add(item);
      }
    }
    if (f.length > 10) r.args = f[10];
    if (f.length > 11) r.workflow = f[11];
    if (f.length > 12 && f[12].length() > 0) for (String a : f[12].split(String.valueOf(I), -1)) r.argv.add(a);
    return r;
  }

  private static String clean(String s) {
    return s == null ? "" : s.replace(F, ' ').replace(I, ' ').replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
  }
}
