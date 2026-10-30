package pulsekit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Runs Python, Java source, a .class, or a .jar. Used by the PyJav tab. */
public final class JavaRun {
  /** How long a Java program may run: SogniMusic waits on Sogni's queue for up to 15 minutes. */
  public static final long RUN_MS = 20 * 60 * 1000L;
  /** How long a Sogni program may run: a video waits on Sogni for up to 55 minutes, after its uploads. */
  public static final long SOGNI_RUN_MS = 75 * 60 * 1000L;
  /** How many times a run that timed out with its workflow still going on Sogni is joined again (--workflow). */
  public static final int RECONNECTS = 3;

  /** How long this program may run: longer for the Sogni programs (they carry SogniApi), else RUN_MS. */
  public static long runMs(String name, String source, byte[] bytes) {
    String low = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    if (low.startsWith("sogni") || low.startsWith("imageupscaler")) return SOGNI_RUN_MS;
    if (source != null && source.indexOf("--- SogniApi begin ---") >= 0) return SOGNI_RUN_MS;
    if (bytes != null && indexOf(bytes, "SogniApi".getBytes(StandardCharsets.UTF_8)) >= 0) return SOGNI_RUN_MS;
    return RUN_MS;
  }

  private static int indexOf(byte[] data, byte[] needle) {
    outer:
    for (int i = 0; i + needle.length <= data.length; i++) {
      for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
      return i;
    }
    return -1;
  }

  /**
   * What a run has printed so far, and when it started: PyJav's "Running…" line shows the time
   * and the latest news (queued, reconnecting, progress). Set for the thread that runs a program
   * (and the threads it starts), so two runs at once each keep their own.
   */
  public static final class Live {
    public final long started = System.currentTimeMillis();
    private final StringBuffer text = new StringBuffer();
    /** The same text with each line's time in front ("14:38:05 Wrote ..."), for Output. */
    private final StringBuffer stamped = new StringBuffer();
    private boolean lineStart = true;

    /** Its File > Downloads run (0: none): the workflow id is kept there the moment the program prints it. */
    public volatile long download;
    private volatile boolean workflowKept;

    /** The run's File > Downloads run: its workflow id kept there (now, when it was printed already). */
    public void download(long id) {
      this.download = id;
      if (id != 0 && !this.workflowKept) {
        String wf = SogniDownloads.workflowIn(this.text.toString());
        if (wf != null) {
          this.workflowKept = true;
          SogniDownloads.workflow(id, wf);
        }
      }
    }

    public void add(String s) {
      if (s == null) return;
      this.text.append(s);
      if (this.text.length() > 200000) this.text.delete(0, this.text.length() - 100000);
      synchronized (this.stamped) {
        this.lineStart = stamp(this.stamped, s, this.lineStart, clock(System.currentTimeMillis()));
        if (this.stamped.length() > 240000) this.stamped.delete(0, this.stamped.length() - 120000);
      }
      // "Workflow: <id>": kept with the download at once (Android may close the app before the run ends).
      if (this.download != 0 && !this.workflowKept && s.indexOf("Workflow: ") >= 0) {
        String id = SogniDownloads.workflowIn(this.text.toString());
        if (id != null) {
          this.workflowKept = true;
          SogniDownloads.workflow(this.download, id);
        }
      }
    }

    public String text() {
      return this.text.toString();
    }

    /** What the program printed, each line with the time it came. */
    public String stamped() {
      synchronized (this.stamped) {
        return this.stamped.toString();
      }
    }

    private volatile boolean aborted;
    /** The run is over (its result is in). */
    public volatile boolean done;
    /** When the "no hits yet" question was last asked (0: not yet). */
    public long askedAt;

    /** Abort: the program is stopped (its process, or its thread interrupted on the phone). */
    public void abort() {
      this.aborted = true;
    }

    public boolean aborted() {
      return this.aborted;
    }
  }

  /** How long DrumMidi may look without finding a hit before PyJav asks whether to abort, and again after. */
  public static final long NO_HITS_MS = 90 * 1000L;

  /**
   * DrumMidi_CRT has run NO_HITS_MS (since it started, or since it was last asked) and found no
   * drum hits yet: the question to ask ("…Abort the run?"), else null.
   */
  public static String noHitsQuestion(String program, Live live, long now) {
    String low = program == null ? "" : program.toLowerCase(java.util.Locale.ROOT);
    if (live == null || live.done || live.aborted() || !low.startsWith("drummidi")) return null;
    long since = live.askedAt > 0 ? live.askedAt : live.started;
    if (now - since < NO_HITS_MS) return null;
    String log = live.text();
    if (log.indexOf("\nHits: ") >= 0 || log.startsWith("Hits: ") || log.indexOf("Wrote ") >= 0 || log.indexOf("Succeeded") >= 0 || log.indexOf("Failed") >= 0) return null;
    long s = (now - live.started) / 1000;
    String news = news(log);
    return "DrumMidi has found no drum hits in " + (s / 60) + " min " + (s % 60) + " s"
        + (news == null ? "" : " (" + news + ")") + ".\nA long file takes a while; a file without drums finds none.\nAbort the run?";
  }

  private static final InheritableThreadLocal<Live> LIVE = new InheritableThreadLocal<Live>();

  /** The calling thread's run (and the threads it starts) prints into `live`. */
  public static void track(Live live) {
    LIVE.set(live);
  }

  /** The run the calling thread belongs to, or null. */
  public static Live live() {
    return LIVE.get();
  }

  /** The page's top line while a run goes: "SogniVideo.java: Running… 2:05 · Queued: position 3". */
  public static String runningTop(String program, Live live, long now) {
    return (program == null || program.length() == 0 ? "script.py" : program) + ": " + runningLine(live, now);
  }

  /** "Running… 10:05 elapsed" with the run's latest news (Output's status line on the desktop). */
  public static String runningStatus(Live live, long now) {
    long s = live == null ? 0 : Math.max(0, (now - live.started) / 1000);
    String news = live == null ? null : news(live.text());
    return "Running\u2026 " + duration(s) + " elapsed" + (news == null ? "" : " \u00b7 " + news);
  }

  /** How the run ended, and its time: "Finished in 12:03", "Timed out (no output for 600 s) after 25:10". */
  public static String endedStatus(Live live, int code, long now) {
    String took = duration(live == null ? 0 : Math.max(0, (now - live.started) / 1000));
    if (live != null && live.aborted()) return "Stopped after " + took;
    if (code == 0) return "Finished in " + took;
    if (code == 124) return "Timed out (no output for a long while) after " + took;
    return "Ended with an error (exit " + code + ") after " + took;
  }

  /** "2:05", "1:02:05". */
  static String duration(long s) {
    return s >= 3600 ? (s / 3600) + ":" + two((s / 60) % 60) + ":" + two(s % 60) : (s / 60) + ":" + two(s % 60);
  }

  /** "Running… 2:05" with the run's latest news: "Running… 2:05 · Queued: position 3". */
  public static String runningLine(Live live, long now) {
    long s = live == null ? 0 : Math.max(0, (now - live.started) / 1000);
    String time = s >= 3600 ? (s / 3600) + ":" + two((s / 60) % 60) + ":" + two(s % 60) : (s / 60) + ":" + two(s % 60);
    String news = live == null ? null : news(live.text());
    return "Running\u2026 " + time + (news == null ? "" : " \u00b7 " + news);
  }

  /** Appends `s` to `out` with `time` before each line that starts in it; true when `s` ends a line. */
  static boolean stamp(StringBuffer out, String s, boolean atStart, String time) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (atStart && c != '\n' && c != '\r') {
        out.append(time).append(' ');
        atStart = false;
      }
      out.append(c);
      if (c == '\n') atStart = true;
    }
    return atStart;
  }

  /** "14:38:05": the time of day a line came. */
  public static String clock(long millis) {
    java.util.Calendar c = java.util.Calendar.getInstance();
    c.setTimeInMillis(millis);
    return two(c.get(java.util.Calendar.HOUR_OF_DAY)) + ":" + two(c.get(java.util.Calendar.MINUTE)) + ":" + two(c.get(java.util.Calendar.SECOND));
  }

  /**
   * The run's whole log for Output when it is over, its lines with the times they came: the part the
   * run printed as it went from `live` (the command line before it as it is), what came after it
   * (a timeout note) at `now`. As it is when the live text is not in it (cut, or another log).
   */
  public static String stampedLog(Live live, String log, long now) {
    if (live == null || log == null) return log;
    String raw = live.text();
    if (raw.length() == 0) return log;
    int at = log.indexOf(raw);
    if (at < 0) return log;
    String stamped = live.stamped();
    StringBuffer out = new StringBuffer(log.substring(0, at)).append(stamped);
    stamp(out, log.substring(at + raw.length()), raw.endsWith("\n"), clock(now));
    return out.toString();
  }

  /** The last LIVE_LINES lines the program printed so far, each with its time ("…" first when there were more). */
  public static String runningTail(Live live) {
    String text = live == null ? "" : live.stamped().trim();
    int at = text.length();
    for (int n = 0; n < LIVE_LINES && at > 0; n++) at = text.lastIndexOf('\n', at - 1);
    return at > 0 ? "\u2026\n" + text.substring(at + 1) : text;
  }

  /** Output's text while a run goes: the "Running…" line, then the last lines the program printed so far. */
  public static String runningLog(Live live, long now) {
    String line = runningLine(live, now);
    String tail = runningTail(live);
    return tail.length() == 0 ? line : line + "\n\n" + tail;
  }

  /** How many of the program's last lines Output shows while it runs. */
  static final int LIVE_LINES = 300;

  private static String two(long v) {
    return v < 10 ? "0" + v : String.valueOf(v);
  }

  /** The latest line that says how the run is going: a status, queued, reconnecting, progress. Null when none. */
  public static String news(String log) {
    if (log == null || log.length() == 0) return null;
    String[] lines = log.split("\n");
    String status = null;
    String progress = null;
    for (int i = lines.length - 1; i >= 0 && i >= lines.length - 200; i--) {
      String t = lines[i].trim();
      if (t.length() == 0) continue;
      if (progress == null && t.startsWith("Progress: ")) progress = t.substring(10);
      if (status == null && (t.startsWith("Status: ") || t.startsWith("Queued") || t.startsWith("Reconnecting") || t.startsWith("Uploaded ")
          || t.startsWith("Workflow: ") || t.startsWith("Sogni asks") || t.startsWith("Downloading: ") || t.startsWith("Wrote ") || t.startsWith("Joined "))) status = t;
      if (status != null && progress != null) break;
    }
    if (status == null && progress == null) return null;
    // Done on Sogni, but the run goes on: it is downloading (or saving) the result, so the clock still runs.
    if (status != null && status.matches("Status: (completed|partial_failure)")) return status + ", downloading the result\u2026";
    if (status != null && status.startsWith("Downloading: 100%")) return "Downloaded" + status.substring(17) + ", saving\u2026";
    if (status != null && (status.startsWith("Wrote ") || status.startsWith("Joined "))) return status + ", finishing\u2026";
    if (status == null) return progress;
    if (progress == null || status.startsWith("Reconnecting") || status.startsWith("Queued")) return status;
    return status + " " + progress;
  }

  /**
   * A run that stopped waiting while its workflow still goes on Sogni (it timed out, or the
   * network went away): its arguments for joining that workflow again (--workflow id, the other
   * arguments as they were, so the prompt and its sheet stay the same), or null. Only for the
   * programs that take --workflow, and never for a run that succeeded.
   */
  public static List<String> reconnectArgs(String program, String source, String log, List<String> argv) {
    return workflowArgs(program, source, log, argv, false);
  }

  /**
   * Any failed run whose workflow is known (not only one that timed out): the arguments to join
   * that workflow again (Resume), or null.
   */
  public static List<String> resumeArgs(String program, String source, String log, List<String> argv) {
    return workflowArgs(program, source, log, argv, true);
  }

  /** Resume's question: the run failed (why), its workflow may still finish or have finished on Sogni. */
  public static String resumeQuestion(String log, List<String> again) {
    String why = "";
    for (String line : (log == null ? "" : log).split("\n")) if (line.trim().startsWith("Failed")) why = line.trim();
    return (why.length() > 0 ? why + "\n\n" : "") + "Its workflow " + workflowOf(again) + " is known: Resume waits for it on Sogni (or fetches what it made) "
        + "without starting, or paying for, a new run.";
  }

  private static List<String> workflowArgs(String program, String source, String log, List<String> argv, boolean anyFailure) {
    if (log == null || argv == null) return null;
    String text = source == null ? "" : source;
    String low = program == null ? "" : program.toLowerCase(java.util.Locale.ROOT);
    boolean takes = text.indexOf("--workflow") >= 0 || low.matches("sogni(video|textvideo|music|padd|pedit)\\.(java|jar|class)");
    if (!takes) return null;
    String id = null;
    boolean ok = false;
    for (String line : log.split("\n")) {
      String t = line.trim();
      if (t.startsWith("Workflow: ")) id = t.substring(10).trim();
      if (t.startsWith("Succeeded")) ok = true;
    }
    if (ok || id == null || id.length() == 0 || id.indexOf(' ') >= 0) return null;
    // Not a workflow id ("require"), or Sogni has no such workflow: resuming could only fail again.
    if (!SogniApi.validId(id) || log.matches("(?s).*Failed:[^\n]*(?i:error 404|workflow not found).*")) return null;
    boolean stopped = log.indexOf("Timed out") >= 0
        || log.matches("(?s).*Failed:[^\n]*(?i:unable to resolve host|connection (reset|refused|abort)|software caused connection abort|network is unreachable|socket|timeout|unexpected end of stream|end of stream|premature eof|connection closed).*");
    if (!stopped && !anyFailure) return null;
    List<String> out = new ArrayList<String>();
    for (int i = 0; i < argv.size(); i++) {
      String a = argv.get(i);
      if ("--workflow".equals(a)) {
        i++;
        continue;
      }
      if (a != null && a.startsWith("--workflow ")) continue;
      out.add(a);
    }
    out.add("--workflow");
    out.add(id);
    return out;
  }

  /** The workflow a reconnect joins: the value after --workflow. */
  public static String workflowOf(List<String> argv) {
    if (argv == null) return "";
    for (int i = 0; i + 1 < argv.size(); i++) if ("--workflow".equals(argv.get(i))) return argv.get(i + 1);
    return "";
  }

  /** Files a program writes are kept up to 3 MB; audio (a Sogni track, a cut WAV) up to 50 MB. */
  public static long maxFileBytes(String name) {
    String low = name == null ? "" : name.toLowerCase();
    // Audio, pictures and video a program made (SogniMusic's track, SogniChat's tool results).
    if (low.matches(".*\\.(mp3|wav|wave|flac|m4a|ogg|aac|png|jpe?g|webp|gif|mp4|webm|mov|glb)$")) return 50000000L;
    return 3000000L;
  }

  public static final class FileOut {
    public final String name;
    public final byte[] bytes;

    public FileOut(String name, byte[] bytes) {
      this.name = name;
      this.bytes = bytes;
    }
  }

  public static final class Result {
    public final String log;
    public final List<FileOut> files;
    public final int code;

    public Result(String log, List<FileOut> files, int code) {
      this.log = log;
      this.files = files;
      this.code = code;
    }
  }

  public interface Listener {
    void onDone(Result result);
  }

  private JavaRun() {}

  /** Runs the program on its own thread; `listener` gets its result. Its live log, for the "Running…" line. */
  public static Live start(final String name, final String source, final byte[] bytes, final List<String> argv, final Listener listener) {
    final Live live = new Live();
    new Thread(new Runnable() {
      @Override
      public void run() {
        track(live);
        long started = System.currentTimeMillis();
        Result result = JavaRun.run(name, source, bytes, argv);
        // File > Logs (Logging on): the run's arguments, exit code and output.
        Logs.run(name, argv, result.code, result.log, started);
        live.done = true;
        if (listener != null) listener.onDone(result);
      }
    }, "pulsekit-pyjav").start();
    return live;
  }

  public static List<String> split(String line) {
    List<String> out = new ArrayList<String>();
    if (line == null || line.trim().isEmpty()) return out;
    // "..." (with \" for a quote inside, as quoteArg writes it), '...', or a word.
    Matcher m = Pattern.compile("\"((?:\\\\\"|[^\"])*)\"|'([^']*)'|(\\S+)").matcher(line.trim());
    while (m.find()) {
      String a = m.group(1) != null ? m.group(1).replace("\\\"", "\"") : null;
      if (a == null) a = m.group(2);
      if (a == null) a = m.group(3);
      if (a != null && !a.isEmpty()) out.add(a);
    }
    return out;
  }

  /**
   * Pulsekit's own JAR (or classes folder) after the program's folder on its class path, so a
   * program can use the app's helpers by name (VideoFrames reads frames with VLC through it); its
   * own classes, in the default package, never meet the app's (in pulsekit). "" when not known.
   */
  static String appClassPath() {
    try {
      java.security.CodeSource cs = JavaRun.class.getProtectionDomain().getCodeSource();
      if (cs == null || cs.getLocation() == null) return "";
      File self = new File(cs.getLocation().toURI());
      return self.exists() ? File.pathSeparator + self.getAbsolutePath() : "";
    } catch (Exception ex) {
      return "";
    }
  }

  /**
   * One argument as split reads it back: as it is when it has no space or quote; in double quotes;
   * in single quotes when it has a double quote in it (a prompt with speech: She says "Hello");
   * with both kinds, in double quotes with \" for each double quote.
   */
  public static String quoteArg(String v) {
    if (v == null) return "";
    boolean dq = v.indexOf('"') >= 0, sq = v.indexOf('\'') >= 0;
    if (!dq && !(sq && v.startsWith("'")) && v.indexOf(' ') < 0 && v.indexOf('\t') < 0 && v.length() > 0) return v;
    if (!dq) return "\"" + v + "\"";
    if (!sq) return "'" + v + "'";
    return "\"" + v.replace("\"", "\\\"") + "\"";
  }

  /**
   * The app's kit settings for a program that mentions them (--bpm, --style, --genre, --bars,
   * --swing, and --key_file when a Sogni key file is set), then the extra args. A switch the
   * extra args already give is not added, so the command line shows it once (Params' --genre
   * "Rock Ballad", not also --genre House).
   */
  /** A program that says this takes --bpm only from Params, not Pulsekit's tempo. */
  public static final String NO_KIT_BPM = "Pulsekit does not pass its own tempo as --bpm";

  public static List<String> argvFor(String source, int bpm, String style, int bars, int swing, String extra) {
    return argvFor(source, bpm, style, bars, swing, extra, 4, 4);
  }

  /** As above, with the app's time signature: passed as --timesig N/D to a program that takes it, unless 4/4. */
  public static List<String> argvFor(String source, int bpm, String style, int bars, int swing, String extra, int tsNum, int tsDen) {
    List<String> argv = new ArrayList<String>();
    String src = source == null ? "" : source;
    List<String> given = split(extra);
    // MidiDrumGen's batch (--count) picks a style and its settings for each groove: not the app's.
    if (given.contains("--count")) {
      argv.addAll(given);
      return withKeyFile(src, argv);
    }
    // A program whose --bpm is its own (vst_render's: the MIDI's tempo) says so; Pulsekit's tempo is not passed.
    if (src.contains("--bpm") && !src.contains(NO_KIT_BPM) && !given.contains("--bpm")) {
      argv.add("--bpm");
      argv.add(Integer.toString(bpm));
    }
    // --style itself, not --styles (SogniMusic's and MidiDrumGen's list of them).
    if (src.matches("(?s).*--style(?![A-Za-z_]).*") && !given.contains("--style")) {
      argv.add("--style");
      argv.add(style == null ? "house" : style);
    }
    // SogniMusic --random: the styles it draws from are Pulsekit's (StyleDb), unless --styles names some.
    if (src.contains("--random") && given.contains("--random") && src.contains("--styles") && !given.contains("--styles")) {
      argv.add("--styles");
      argv.add(String.join("|", StyleDb.names()));
    }
    if (src.contains("--genre") && !given.contains("--genre")) {
      // The app's style by name (House, Drum & Bass...).
      Engine.Style st = Engine.styles().get(style == null ? "house" : style);
      argv.add("--genre");
      argv.add(st != null && st.label != null ? st.label : (style == null ? "house" : style));
    }
    if (src.contains("--bars") && !given.contains("--bars")) {
      argv.add("--bars");
      argv.add(Integer.toString(Math.max(1, bars)));
    }
    if (src.contains("--swing") && !given.contains("--swing")) {
      argv.add("--swing");
      argv.add(Integer.toString(Math.max(0, swing)));
    }
    if (src.contains("--timesig") && !given.contains("--timesig") && tsNum > 0 && tsDen > 0 && !(tsNum == 4 && tsDen == 4)) {
      // The app's time signature (MidiDrumGen, SogniMusic); 4/4 is every program's own default.
      argv.add("--timesig");
      argv.add(tsNum + "/" + tsDen);
    }
    argv.addAll(given);
    // The key file from File > Drum Midi Settings (also in place of a given one that holds no key).
    return withKeyFile(src, argv);
  }

  /**
   * The key file from File > Drum Midi Settings added as --key_file when the program takes one and
   * `argv` has none: also for a run with an input file (ImageUpscaler's picture), which argvFor does
   * not build. `argv` itself, changed.
   */
  /**
   * Midi2Wav without a SoundFont (--sf2) or a folder of them (--kits): true, and Pulsekit's own
   * kit (the active drum set, as Export SF2 writes it) is given with withKit.
   */
  public static boolean needsKit(String program, List<String> argv) {
    if (!"Midi2Wav.java".equals(program) || argv == null) return false;
    for (int i = 0; i < argv.size(); i++) {
      if (!"--sf2".equals(argv.get(i)) && !"--kits".equals(argv.get(i))) continue;
      String v = i + 1 < argv.size() ? argv.get(i + 1) : null;
      if (v != null && v.trim().length() > 0 && !v.startsWith("--")) return false;
    }
    return true;
  }

  /** `argv` with --sf2 `path` (an empty --sf2 taken out). */
  public static List<String> withKit(List<String> argv, String path) {
    List<String> out = new ArrayList<String>();
    for (int i = 0; i < argv.size(); i++) {
      String a = argv.get(i);
      if ("--sf2".equals(a)) {
        String v = i + 1 < argv.size() ? argv.get(i + 1) : null;
        if (v != null && !v.startsWith("--")) i++;
        continue;
      }
      out.add(a);
    }
    out.add("--sf2");
    out.add(path);
    return out;
  }

  public static List<String> withKeyFile(String source, List<String> argv) {
    String key = ApiKeys.path();
    if (argv == null || key == null || source == null || !source.contains("--key_file")) return argv;
    int at = argv.indexOf("--key_file");
    if (at >= 0) {
      // A key file given that holds a key stays. One that is empty, gone (a picked copy since
      // cleared away) or without a key gives way to the settings' one: the program cannot see
      // Pulsekit's settings itself, so it would find no key at all.
      String given = at + 1 < argv.size() ? argv.get(at + 1) : "";
      boolean value = given != null && !given.startsWith("--");
      if (value && given.trim().length() > 0 && SogniApi.keyIn(new java.io.File(given.trim())) != null) return argv;
      argv.remove(at);
      if (value && at < argv.size()) argv.remove(at);
    }
    argv.add("--key_file");
    argv.add(key);
    return argv;
  }

  private static String publicClassName(String src) {
    if (src == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("(?m)^\\s*public\\s+(?:final\\s+|abstract\\s+)?class\\s+(\\w+)")
        .matcher(src);
    return m.find() ? m.group(1) : null;
  }

  public static byte[] zipEntry(byte[] zip, String want) {
    if (zip == null || want == null) return null;
    try {
      ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip));
      ZipEntry e;
      byte[] buf = new byte[4096];
      while ((e = zin.getNextEntry()) != null) {
        String n = e.getName();
        if (n != null && (n.equals(want) || n.endsWith("/" + want))) {
          ByteArrayOutputStream bos = new ByteArrayOutputStream();
          int c;
          while ((c = zin.read(buf)) > 0) bos.write(buf, 0, c);
          zin.close();
          return bos.toByteArray();
        }
      }
      zin.close();
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  public static Result run(String name, String source, byte[] bytes, List<String> argv) {
    String n = name == null || name.trim().isEmpty() ? "script.py" : name.replaceAll("[\\\\/]", "_");
    String low = n.toLowerCase();
    if (low.endsWith(".prompt")) {
      PromptRun.Sheet sheet = PromptRun.parse(source);
      String arg = "";
      if (argv != null) {
        for (int i = 0; i < argv.size(); i++) {
          if ("--pk-run".equals(argv.get(i)) && i + 1 < argv.size()) arg = PromptRun.normalizeType(argv.get(i + 1));
        }
      }
      String type = sheet != null ? PromptRun.normalizeType(sheet.type) : "";
      if (type.length() == 0) type = arg;
      if (type.length() == 0) type = PromptRun.defaultMode(sheet != null ? sheet.body : source);
      String body = sheet != null && sheet.body != null ? sheet.body : (source == null ? "" : source);
      body = PromptRun.withoutDescription(sheet == null ? "" : sheet.description, body);
      List<String> args = stripRunFlag(argv);
      if ("python".equals(type)) return runPython(promptFile(n, ".py"), body, args);
      if ("java".equals(type)) {
        String cls = publicClassName(body);
        return runJava((cls == null || cls.length() == 0 ? "Main" : cls) + ".java", body, null, args);
      }
      if ("javascript".equals(type) || "typescript".equals(type)) {
        return runNode(promptFile(n, "typescript".equals(type) ? ".ts" : ".js"), body, args);
      }
      if ("powershell".equals(type)) return runTool(win() ? "powershell" : "pwsh", promptFile(n, ".ps1"), body, args);
      if ("ai".equals(type)) {
        if (androidVm()) {
          return new Result("AI prompt. PyJav cannot run this on Android.\nTest it in the Grok subsystem.", Collections.<FileOut>emptyList(), 0);
        }
        return PromptRun.run(source == null ? "" : source, "ai");
      }
      return PromptRun.run(source == null ? "" : source, type);
    }
    if (low.endsWith(".java") || low.endsWith(".jar") || low.endsWith(".class")) {
      if (PictureCopies.wants(n)) {
        // SogniChat: big JPEG pictures get a smaller copy for the chat model to see.
        List<File> made = new ArrayList<File>();
        try {
          return runJava(n, source, bytes, PictureCopies.withCopies(argv, made));
        } finally {
          PictureCopies.clean(made);
        }
      }
      return runJava(n, source, bytes, argv);
    }
    if (isNode(low)) return runNode(n, source == null ? "" : source, argv);
    return runPython(n, source == null ? "" : source, argv);
  }

  private static boolean isNode(String low) {
    return low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".cjs") || low.endsWith(".jsx")
        || low.endsWith(".ts") || low.endsWith(".mts") || low.endsWith(".tsx");
  }

  /** JavaScript or TypeScript through Node.js. npm installs imported packages, including @sogni-ai/sogni-client. */
  private static Result runNode(String name, String source, List<String> argv) {
    String node = findNode();
    if (node == null) {
      if (androidVm()) return runNodeOnAndroid(name, source, argv);
      return new Result(
          "Node.js was not found.\nInstall Node.js, then Run again. JavaScript, TypeScript, and npm use that Node.\nnodejs-mobile and nodejs-mobile-react-native are React Native hosts. They are not inside this app.",
          Collections.<FileOut>emptyList(),
          127);
    }
    String src = source == null ? "" : source;
    boolean ts = name.toLowerCase().endsWith(".ts") || name.toLowerCase().endsWith(".mts") || name.toLowerCase().endsWith(".tsx");
    java.util.LinkedHashSet<String> pkgs = nodePackages(src);
    File dir = null;
    try {
      dir = tempDir("pulsekit-node-");
      writeFile(new File(dir, name), src.getBytes(StandardCharsets.UTF_8));
      StringBuilder log = new StringBuilder();
      if (src.contains("nodejs-mobile")) {
        log.append("nodejs-mobile and nodejs-mobile-react-native host Node inside React Native. PyJav runs this file with Node.js.\n");
      }
      if (!pkgs.isEmpty()) {
        if (!works("npm", "--version")) {
          return new Result("npm was not found. It ships with Node.js.\nPackages: " + join(pkgs), Collections.<FileOut>emptyList(), 127);
        }
        StringBuilder json = new StringBuilder();
        json.append("{\"name\":\"pulsekit-pyjav\",\"private\":true,\"type\":");
        json.append(src.contains("import ") || src.contains("export ") || name.endsWith(".mjs") ? "\"module\"" : "\"commonjs\"");
        json.append(",\"dependencies\":{");
        boolean first = true;
        for (String pkg : pkgs) {
          if (!first) json.append(',');
          first = false;
          json.append('"').append(pkg.replace("\"", "")).append("\":\"*\"");
        }
        json.append("}}");
        writeFile(new File(dir, "package.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        List<String> npm = new ArrayList<String>();
        npm.add("npm");
        npm.add("install");
        npm.add("--omit=dev");
        npm.add("--no-audit");
        npm.add("--no-fund");
        Result installed = exec(dir, npm, name, "$ npm install", 180000L);
        log.append(installed.log).append('\n');
        if (installed.code != 0) {
          return new Result(log.toString(), Collections.<FileOut>emptyList(), installed.code);
        }
      }
      List<String> cmd = new ArrayList<String>();
      cmd.add(node);
      if (ts) cmd.add("--experimental-strip-types");
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      String shown = "$ " + node + (ts ? " --experimental-strip-types " : " ") + name;
      if (argv != null) for (String a : argv) shown = shown + " " + a;
      java.util.Map<String, Long> stamps = stamps(argv);
      Result ran = exec(dir, cmd, name, shown, 120000L);
      log.append(ran.log);
      List<FileOut> files = new ArrayList<FileOut>();
      for (int i = 0; i < ran.files.size(); i++) {
        String fn = ran.files.get(i).name.toLowerCase();
        if ("package.json".equals(fn) || "package-lock.json".equals(fn)) continue;
        files.add(ran.files.get(i));
      }
      return withOutsideFiles(new Result(log.toString(), files, ran.code), argv, dir, stamps);
    } catch (Exception ex) {
      return new Result("Could not run Node.js: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  private static Result runNodeOnAndroid(String name, String source, List<String> argv) {
    try {
      return (Result) Class.forName("pulsekit.ArtNode")
          .getMethod("run", String.class, String.class, List.class)
          .invoke(null, name, source, argv);
    } catch (java.lang.reflect.InvocationTargetException ex) {
      Throwable c = ex.getCause();
      String m = c == null || c.getMessage() == null ? String.valueOf(c) : c.getMessage();
      return new Result("Could not run Node in Termux: " + m, Collections.<FileOut>emptyList(), 1);
    } catch (ClassNotFoundException ex) {
      return new Result("Termux Node support is not in this build.", Collections.<FileOut>emptyList(), 1);
    } catch (Exception ex) {
      return new Result("Could not run Node in Termux: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    }
  }

  private static String join(java.util.Set<String> pkgs) {
    StringBuilder sb = new StringBuilder();
    for (String pkg : pkgs) {
      if (sb.length() > 0) sb.append(", ");
      sb.append(pkg);
    }
    return sb.toString();
  }

  static java.util.LinkedHashSet<String> nodePackages(String src) {
    java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
    Matcher m = Pattern.compile("(?:import|export)\\s+(?:[^'\"\\n]*?\\s+from\\s+)?['\"]([^'\"]+)['\"]|require\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)|import\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)").matcher(src);
    while (m.find()) {
      String spec = m.group(1);
      if (spec == null) spec = m.group(2);
      if (spec == null) spec = m.group(3);
      String pkg = npmName(spec);
      if (pkg != null) out.add(pkg);
    }
    return out;
  }

  private static String npmName(String spec) {
    if (spec == null) return null;
    if (spec.startsWith(".") || spec.startsWith("/") || spec.startsWith("node:")) return null;
    if ("nodejs-mobile".equals(spec) || "nodejs-mobile-react-native".equals(spec)) return null;
    if (spec.startsWith("@")) {
      int slash = spec.indexOf('/');
      if (slash < 1 || slash == spec.length() - 1) return null;
      int next = spec.indexOf('/', slash + 1);
      return next < 0 ? spec : spec.substring(0, next);
    }
    int slash = spec.indexOf('/');
    String name = slash < 0 ? spec : spec.substring(0, slash);
    if (name.length() == 0 || name.indexOf('.') >= 0 && name.startsWith("node")) return null;
    if ("fs".equals(name) || "path".equals(name) || "os".equals(name) || "url".equals(name) || "crypto".equals(name)
        || "http".equals(name) || "https".equals(name) || "util".equals(name) || "stream".equals(name)
        || "buffer".equals(name) || "events".equals(name) || "child_process".equals(name) || "assert".equals(name)) return null;
    return name;
  }

  private static String findNode() {
    if (works("node", "--version")) return "node";
    if (works("nodejs", "--version")) return "nodejs";
    return null;
  }

  private static boolean win() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private static String promptFile(String name, String ext) {
    String base = name == null ? "prompt" : name;
    int dot = base.lastIndexOf('.');
    if (dot > 0) base = base.substring(0, dot);
    if (base.length() == 0) base = "prompt";
    return base + ext;
  }

  private static List<String> stripRunFlag(List<String> argv) {
    List<String> out = new ArrayList<String>();
    if (argv == null) return out;
    for (int i = 0; i < argv.size(); i++) {
      if ("--pk-run".equals(argv.get(i))) {
        i++;
        continue;
      }
      out.add(argv.get(i));
    }
    return out;
  }

  private static Result runTool(String bin, String name, String source, List<String> argv) {
    if (androidVm()) {
      return new Result(bin + " is not on this phone. Python and Java run inside the app.", Collections.<FileOut>emptyList(), 127);
    }
    if (!works(bin, "--version") && !works(bin, "-Version")) {
      return new Result(bin + " was not found.", Collections.<FileOut>emptyList(), 127);
    }
    File dir = null;
    try {
      dir = tempDir("pulsekit-tool-");
      writeFile(new File(dir, name), (source == null ? "" : source).getBytes(StandardCharsets.UTF_8));
      List<String> cmd = new ArrayList<String>();
      cmd.add(bin);
      if ("powershell".equals(bin) || "pwsh".equals(bin)) {
        cmd.add("-NoProfile");
        cmd.add("-File");
      }
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      return exec(dir, cmd, name, "$ " + bin + " " + name);
    } catch (Exception ex) {
      return new Result("Could not run " + bin + ": " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  /**
   * vst_render.py --test (and --mp3: the jar has Wav2Mp3 too): DrumMidi_CRT.jar (written to `jar`)
   * and this Java for it, unless the arguments give their own. Returns the arguments to run with.
   */
  public static List<String> withDrumMidi(String program, List<String> argv, File jar, byte[] jarBytes) {
    if (!"vst_render.py".equals(program) || argv == null || !(argv.contains("--test") || argv.contains("--mp3")) || argv.contains("--drummidi") || jarBytes == null) return argv;
    List<String> out = new ArrayList<String>(argv);
    try {
      jar.getParentFile().mkdirs();
      writeFile(jar, jarBytes);
    } catch (Exception ex) {
      return argv;
    }
    out.add("--drummidi");
    out.add(jar.getAbsolutePath());
    if (!out.contains("--java")) {
      File java = new File(new File(System.getProperty("java.home", ""), "bin"), File.separatorChar == '\\' ? "java.exe" : "java");
      if (java.isFile()) {
        out.add("--java");
        out.add(java.getAbsolutePath());
      }
    }
    return out;
  }

  private static Result runPython(String name, String source, List<String> argv) {
    if (androidVm()) {
      try {
        return (Result) Class.forName("pulsekit.ArtPy")
            .getMethod("run", String.class, String.class, List.class)
            .invoke(null, name, source, argv);
      } catch (java.lang.reflect.InvocationTargetException ex) {
        Throwable c = ex.getCause();
        String m = c == null || c.getMessage() == null ? String.valueOf(c) : c.getMessage();
        return new Result("Could not run Python on Android: " + m, Collections.<FileOut>emptyList(), 1);
      } catch (ClassNotFoundException ex) {
        return new Result("Android Python runtime is not in this build.", Collections.<FileOut>emptyList(), 1);
      } catch (Exception ex) {
        return new Result("Could not run Python on Android: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
      }
    }
    String py = findPython();
    if (py == null) {
      return new Result(
          "Python 3 was not found.\nInstall Python 3, then Run again. On a phone, use the web or desktop Pulsekit.",
          Collections.<FileOut>emptyList(),
          127);
    }
    if (!name.toLowerCase().endsWith(".py")) name = name + ".py";
    File dir = null;
    try {
      dir = tempDir("pulsekit-py-");
      writeFile(new File(dir, name), source.getBytes(StandardCharsets.UTF_8));
      List<String> cmd = new ArrayList<String>();
      if ("py".equals(py)) {
        cmd.add("py");
        cmd.add("-3");
      } else {
        cmd.add(py);
      }
      cmd.add(name);
      if (argv != null) cmd.addAll(argv);
      String shown = "$ " + py + " " + name;
      if (argv != null) for (String a : argv) shown = shown + " " + a;
      return exec(dir, cmd, name, shown);
    } catch (Exception ex) {
      return new Result("Could not run Python: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  private static Result runJava(String name, String source, byte[] bytes, List<String> argv) {
    if (androidVm()) {
      try {
        return (Result) Class.forName("pulsekit.ArtJava")
            .getMethod("run", String.class, String.class, byte[].class, List.class)
            .invoke(null, name, source, bytes, argv);
      } catch (java.lang.reflect.InvocationTargetException ex) {
        Throwable c = ex.getCause();
        String m = c == null || c.getMessage() == null ? String.valueOf(c) : c.getMessage();
        return new Result("Could not run Java on ART: " + m, Collections.<FileOut>emptyList(), 1);
      } catch (ClassNotFoundException ex) {
        return new Result("Android Java runtime is not in this build.", Collections.<FileOut>emptyList(), 1);
      } catch (Exception ex) {
        return new Result("Could not run Java on ART: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
      }
    }
    String low = name.toLowerCase();
    File dir = null;
    try {
      dir = tempDir("pulsekit-jav-");
      List<String> cmd = new ArrayList<String>();
      String shown;
      if (low.endsWith(".java")) {
        if (!works("javac", "-version")) {
          return new Result("javac was not found.\nInstall a JDK, then Run again.", Collections.<FileOut>emptyList(), 127);
        }
        String src = source == null ? "" : source;
        String declared = publicClassName(src);
        String fileName = declared != null ? declared + ".java" : name;
        writeFile(new File(dir, fileName), src.getBytes(StandardCharsets.UTF_8));
        List<String> jc = new ArrayList<String>();
        jc.add("javac");
        jc.add("-encoding");
        jc.add("UTF-8");
        jc.add(fileName);
        Result compiled = exec(dir, jc, fileName, "$ javac " + fileName);
        if (compiled.code != 0) return compiled;
        if (!works("java", "-version")) {
          return new Result(compiled.log + "\njava was not found after compile.", Collections.<FileOut>emptyList(), 127);
        }
        String cls = declared != null ? declared : name.substring(0, name.length() - 5);
        cmd.add("java");
        cmd.add("-cp");
        cmd.add(dir.getAbsolutePath() + appClassPath());
        cmd.add(cls);
        shown = "$ java " + cls;
      } else if (low.endsWith(".jar")) {
        if (bytes == null || bytes.length == 0) {
          return new Result("No bytes for " + name, Collections.<FileOut>emptyList(), 1);
        }
        if (!works("java", "-version")) {
          return new Result("java was not found.\nInstall a JDK, then Run again.", Collections.<FileOut>emptyList(), 127);
        }
        String main = mainClass(bytes);
        if (main == null || main.isEmpty()) {
          return new Result(
              "No Main-Class in " + name + ".\nAdd Main-Class to META-INF/MANIFEST.MF.",
              Collections.<FileOut>emptyList(),
              1);
        }
        writeFile(new File(dir, name), bytes);
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(name);
        shown = "$ java -jar " + name;
      } else {
        if (bytes == null || bytes.length == 0) {
          return new Result("No bytes for " + name, Collections.<FileOut>emptyList(), 1);
        }
        if (!works("java", "-version")) {
          return new Result("java was not found.\nInstall a JDK, then Run again.", Collections.<FileOut>emptyList(), 127);
        }
        writeFile(new File(dir, name), bytes);
        String cls = name.substring(0, name.length() - 6);
        cmd.add("java");
        cmd.add("-cp");
        cmd.add(dir.getAbsolutePath() + appClassPath());
        cmd.add(cls);
        shown = "$ java " + cls;
      }
      if (argv != null) {
        cmd.addAll(argv);
        for (String a : argv) shown = shown + " " + a;
      }
      java.util.Map<String, Long> stamps = stamps(argv);
      Result ran = withOutsideFiles(exec(dir, cmd, name, shown, runMs(name, source, bytes)), argv, dir, stamps);
      if (low.endsWith(".java")) {
        List<FileOut> kept = new ArrayList<FileOut>();
        for (FileOut f : ran.files) {
          if (f.name.toLowerCase().endsWith(".class")) continue;
          kept.add(f);
        }
        return new Result(ran.log, kept, ran.code);
      }
      return ran;
    } catch (Exception ex) {
      return new Result("Could not run Java: " + ex.getMessage(), Collections.<FileOut>emptyList(), 1);
    } finally {
      deleteDir(dir);
    }
  }

  static String mainClass(byte[] jar) {
    byte[] mf = zipEntry(jar, "META-INF/MANIFEST.MF");
    if (mf == null) return null;
    String text = new String(mf, StandardCharsets.UTF_8);
    StringBuilder cur = new StringBuilder();
    String found = null;
    for (String line : text.split("\n")) {
      String t = line.replace("\r", "");
      if (t.startsWith(" ")) {
        cur.append(t.substring(1).trim());
        continue;
      }
      if (cur.length() > 0 && cur.toString().startsWith("Main-Class:")) {
        found = cur.substring("Main-Class:".length()).trim();
      }
      cur.setLength(0);
      cur.append(t.trim());
    }
    if (found == null && cur.length() > 0 && cur.toString().startsWith("Main-Class:")) {
      found = cur.substring("Main-Class:".length()).trim();
    }
    return found == null || found.isEmpty() ? null : found;
  }

  private static boolean androidVm() {
    try {
      Class.forName("android.os.Build");
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  /** Each argument that names a file, with its modification time before the run (0 when there is none yet). */
  private static java.util.Map<String, Long> stamps(List<String> argv) {
    java.util.Map<String, Long> out = new java.util.HashMap<String, Long>();
    if (argv == null) return out;
    for (String a : argv) {
      File f = new File(a);
      out.put(a, Long.valueOf(f.isFile() ? f.lastModified() : 0L));
    }
    return out;
  }

  /**
   * MIDI written to an absolute extra-arg path, outside the temp folder. Only a file the run made or
   * changed (`before`: stamps from before it): a MIDI given as input (SogniChat --file song.mid) is
   * not the program's output.
   */
  private static Result withOutsideFiles(Result ran, List<String> argv, File dir, java.util.Map<String, Long> before) {
    if (ran == null || argv == null || argv.isEmpty()) return ran;
    List<FileOut> files = new ArrayList<FileOut>(ran.files);
    for (int i = 0; i < argv.size(); i++) {
      File f = new File(argv.get(i));
      if (!f.isFile()) continue;
      if (dir != null && dir.equals(f.getParentFile())) continue;
      Long was = before.get(argv.get(i));
      if (was != null && was.longValue() != 0L && was.longValue() == f.lastModified()) continue;
      String low = f.getName().toLowerCase();
      if (!low.endsWith(".mid") && !low.endsWith(".midi") && !low.endsWith(".sng")) continue;
      boolean seen = false;
      for (int j = 0; j < files.size(); j++) {
        if (f.getName().equals(files.get(j).name)) seen = true;
      }
      if (seen || f.length() > 3000000L) continue;
      try {
        files.add(new FileOut(f.getName(), readFile(f)));
      } catch (Exception ignored) {}
    }
    return new Result(ran.log, files, ran.code);
  }

  private static Result exec(File dir, List<String> cmd, String skipName, String header) throws Exception {
    return exec(dir, cmd, skipName, header, 120000L);
  }

  private static Result exec(File dir, List<String> cmd, String skipName, String header, long waitMs) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.directory(dir);
    pb.redirectErrorStream(true);
    Process proc = pb.start();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    InputStream in = proc.getInputStream();
    byte[] buf = new byte[4096];
    long deadline = System.currentTimeMillis() + waitMs;
    Live live = live();
    while (System.currentTimeMillis() < deadline) {
      if (live != null && live.aborted()) break;
      while (in.available() > 0) {
        int n = in.read(buf);
        if (n <= 0) break;
        bos.write(buf, 0, n);
        if (live != null) live.add(new String(buf, 0, n, StandardCharsets.UTF_8));
      }
      if (!proc.isAlive()) break;
      Thread.sleep(40);
    }
    if (proc.isAlive()) {
      proc.destroyForcibly();
      boolean stopped = live != null && live.aborted();
      bos.write((stopped ? "\nAborted." : "\nTimed out (" + (waitMs / 1000) + "s).").getBytes(StandardCharsets.UTF_8));
    }
    int n;
    try {
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
    } catch (java.io.IOException closed) {
      // a stopped process's output is closed: what it printed is kept
    }
    try {
      proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
    int code = live != null && live.aborted() ? 130 : proc.isAlive() ? 124 : proc.exitValue();
    List<FileOut> files = new ArrayList<FileOut>();
    File[] kids = dir.listFiles();
    if (kids != null) {
      for (File f : kids) {
        if (!f.isFile()) continue;
        String fn = f.getName();
        if (fn.equals(skipName) || fn.startsWith(".")) continue;
        if (f.length() > maxFileBytes(fn)) continue;
        byte[] data = readFile(f);
        files.add(new FileOut(fn, data));
      }
    }
    String log = header + "\n" + bos.toString("UTF-8");
    return new Result(log, files, code);
  }

  private static String findPython() {
    if (works("python3", "--version")) return "python3";
    if (works("python", "--version")) return "python";
    if (works("py", "-3", "--version")) return "py";
    return null;
  }

  private static boolean works(String... cmd) {
    try {
      List<String> all = new ArrayList<String>();
      Collections.addAll(all, cmd);
      Process p = new ProcessBuilder(all).redirectErrorStream(true).start();
      if (!p.waitFor(4, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        return false;
      }
      return p.exitValue() == 0;
    } catch (Exception ex) {
      return false;
    }
  }

  private static void deleteDir(File dir) {
    if (dir == null) return;
    try {
      File[] kids = dir.listFiles();
      if (kids != null) {
        for (File f : kids) {
          if (f.isDirectory()) deleteDir(f);
          else f.delete();
        }
      }
      dir.delete();
    } catch (Exception ignored) {
      /* leave temp */
    }
  }

  private static File tempDir(String prefix) throws Exception {
    File base = new File(System.getProperty("java.io.tmpdir"));
    File dir = new File(base, prefix + System.nanoTime());
    if (!dir.mkdirs()) throw new Exception("Could not create a temp folder");
    return dir;
  }

  private static void writeFile(File file, byte[] data) throws Exception {
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  private static byte[] readFile(File file) throws Exception {
    FileInputStream in = new FileInputStream(file);
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      return bos.toByteArray();
    } finally {
      in.close();
    }
  }
}
