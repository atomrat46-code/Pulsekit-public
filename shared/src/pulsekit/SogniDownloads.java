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
          if (r.status.length() == 0 || r.status.startsWith("Running")) r.status = "Failed: the app was stopped during the run";
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

  /** The runs in one tab (PENDING, PROGRESS, or FINISHED for finished and failed), by program, the newest first. */
  public static synchronized List<Run> tab(String state) {
    List<Run> out = new ArrayList<Run>();
    for (Run r : RUNS) {
      boolean in = FINISHED.equals(state) ? FINISHED.equals(r.state) || FAILED.equals(r.state) : state.equals(r.state);
      if (in) out.add(r);
    }
    Collections.sort(out, new Comparator<Run>() {
      @Override
      public int compare(Run a, Run b) {
        int p = a.programName().compareToIgnoreCase(b.programName());
        if (p != 0) return p;
        return Long.compare(b.started, a.started);
      }
    });
    return out;
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
    String state = PROGRESS.equals(r.state) ? "In progress" : PENDING.equals(r.state) ? "Pending" : FINISHED.equals(r.state) ? "Finished" : "Failed";
    String s = r.status.length() > 80 ? r.status.substring(0, 80) + "…" : r.status;
    return state + " · " + r.when() + (s.length() == 0 ? "" : " · " + s);
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
    return r;
  }

  private static String clean(String s) {
    return s == null ? "" : s.replace(F, ' ').replace(I, ' ').replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
  }
}
