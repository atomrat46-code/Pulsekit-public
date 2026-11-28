package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A batch's place kept on disk (batch-resume.txt) before each of its runs: the program, the runs not done
 * yet (the one starting included) and what they need. When the batch ends the file goes; when it does not
 * (no connection to Sogni, Android stopped Pulsekit, a crash) the next start offers to go on from there.
 */
public final class BatchSave {
  private BatchSave() {}

  public static final String FILE = "batch-resume.txt";

  /** Words of the connection failures that pause a batch (kept, to go on later) instead of running on. */
  static final String[] OFFLINE = {"unable to resolve host", "unknownhostexception", "no address associated", "network is unreachable", "failed to connect",
      "connection refused", "software caused connection abort", "connection reset", "sockettimeoutexception", "timeout while connecting"};

  /** A run's verdict or log that says the connection was lost (not a fault of the run itself). */
  public static boolean offline(String text) {
    if (text == null) return false;
    String t = text.toLowerCase(java.util.Locale.ROOT);
    for (String w : OFFLINE) if (t.contains(w)) return true;
    return false;
  }

  /** The batch kept from run `from` (its index) on; nothing when the batch has no such run. */
  public static void save(File dir, BatchQueue q, int from) {
    if (dir == null || q == null || q.joining || from < 0 || from >= q.runs.size()) return;
    StringBuilder sb = new StringBuilder();
    line(sb, "program", q.program);
    line(sb, "join", q.join ? "1" : "");
    line(sb, "chained", q.chained ? "1" : "");
    line(sb, "doneKey", q.doneKey);
    line(sb, "outFolder", q.outFolder);
    line(sb, "group", q.group);
    line(sb, "folder", q.folder);
    line(sb, "total", String.valueOf(q.runs.size()));
    line(sb, "from", String.valueOf(from));
    line(sb, "saved", String.valueOf(System.currentTimeMillis()));
    for (int i = from; i < q.runs.size(); i++) {
      line(sb, "run", String.join("\u001f", q.runs.get(i)));
      line(sb, "params", i < q.params.size() ? q.params.get(i) : "");
      line(sb, "label", i < q.labels.size() ? q.labels.get(i) : "");
      line(sb, "file", i < q.fileNames.size() ? q.fileNames.get(i) : "");
    }
    for (String n : q.notes) line(sb, "note", n);
    try {
      dir.mkdirs();
      java.nio.file.Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // not kept: the batch only cannot be gone on with later
    }
  }

  static void line(StringBuilder sb, String key, String value) {
    sb.append(key).append('\t').append(value == null ? "" : value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")).append('\n');
  }

  static String unescape(String t) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < t.length(); i++) {
      char c = t.charAt(i);
      if (c == '\\' && i + 1 < t.length()) {
        char n = t.charAt(++i);
        sb.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
      } else sb.append(c);
    }
    return sb.toString();
  }

  /** The batch ended (done, stopped): nothing to go on with. */
  public static void clear(File dir) {
    if (dir != null) new File(dir, FILE).delete();
  }

  /** A kept batch: its program, how many runs are left of how many, and when it was kept. */
  public static final class Kept {
    public String program = "";
    public int total, from;
    public long saved;
    public boolean join, chained;
    public String doneKey, outFolder, group, folder;
    public final List<List<String>> runs = new ArrayList<List<String>>();
    public final List<String> params = new ArrayList<String>(), labels = new ArrayList<String>(), files = new ArrayList<String>(), notes = new ArrayList<String>();

    public int left() {
      return this.runs.size();
    }

    /** "SogniPadd: 23 of 62 runs left (from 40, a.txt)". */
    public String said() {
      return SogniSections.app(this.program) + ": " + this.left() + " of " + this.total + " runs left" + (this.labels.isEmpty() || this.labels.get(0).isEmpty() ? "" : " (from " + this.labels.get(0) + ")");
    }
  }

  /** The kept batch, or null. */
  public static Kept load(File dir) {
    try {
      File f = new File(dir, FILE);
      if (!f.isFile()) return null;
      Kept k = new Kept();
      for (String raw : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int tab = raw.indexOf('\t');
        if (tab < 0) continue;
        String key = raw.substring(0, tab), v = unescape(raw.substring(tab + 1));
        if (key.equals("program")) k.program = v;
        else if (key.equals("join")) k.join = v.equals("1");
        else if (key.equals("chained")) k.chained = v.equals("1");
        else if (key.equals("doneKey")) k.doneKey = v.isEmpty() ? null : v;
        else if (key.equals("outFolder")) k.outFolder = v.isEmpty() ? null : v;
        else if (key.equals("group")) k.group = v.isEmpty() ? null : v;
        else if (key.equals("folder")) k.folder = v.isEmpty() ? null : v;
        else if (key.equals("total")) k.total = Integer.parseInt(v.trim());
        else if (key.equals("from")) k.from = Integer.parseInt(v.trim());
        else if (key.equals("saved")) k.saved = Long.parseLong(v.trim());
        else if (key.equals("run")) {
          List<String> args = new ArrayList<String>();
          if (!v.isEmpty()) for (String a : v.split("\u001f", -1)) args.add(a);
          k.runs.add(args);
        }
        else if (key.equals("params")) k.params.add(v);
        else if (key.equals("label")) k.labels.add(v);
        else if (key.equals("file")) k.files.add(v);
        else if (key.equals("note")) k.notes.add(v);
      }
      return k.program.isEmpty() || k.runs.isEmpty() ? null : k;
    } catch (Exception ex) {
      return null;
    }
  }

  /** The kept batch as a batch again (its Params rows `ps`), ready to go on with its first run left. */
  public static BatchQueue resume(Kept k, List<ProgramParams.Param> ps) {
    boolean byParams = false;
    for (String p : k.params) if (p != null && !p.isEmpty()) byParams = true;
    BatchQueue q = BatchQueue.restore(k.program, ps, k.runs, byParams ? k.params : null, k.labels, k.files, k.doneKey);
    q.join = k.join;
    q.chained = k.chained;
    q.outFolder = k.outFolder;
    q.group = k.group;
    q.folder = k.folder;
    q.notes.addAll(k.notes);
    q.notes.add("Gone on with a kept batch: " + k.left() + " of " + k.total + " runs");
    return q;
  }
}
