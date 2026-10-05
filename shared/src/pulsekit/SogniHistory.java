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
  }

  /** Remembers the workflow a run's log names, if any. A run of the same id again updates it. */
  public static void record(String log, long now) {
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
