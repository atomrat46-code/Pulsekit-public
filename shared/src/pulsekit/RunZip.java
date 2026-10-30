package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SogniMusic --zip: everything a run made, gathered as it goes (the tracks, their WAVs, DrumMidi's
 * MIDI and results.txt, the models' ranking, each run's log), then written into one zip in
 * Download when the last step is over, to send in one go; split into parts of at most 25 MB
 * when it is bigger (sending often stops at 30 MB).
 */
public final class RunZip {
  public static final String FLAG = "--zip";

  public final String name;
  final List<ZipPack.Source> sources = new ArrayList<ZipPack.Source>();
  final Set<String> paths = new HashSet<String>();

  RunZip(String name) {
    this.name = name;
  }

  static RunZip current;

  /** A SogniMusic run asked to zip what it makes. */
  public static boolean wanted(String program, List<String> argv) {
    return "SogniMusic.java".equals(program) && argv != null && argv.contains(FLAG);
  }

  /** Starts gathering for a run whose log is `log`: the zip is named for its first track (sogni-Rock-6f1262f1.zip). */
  public static synchronized RunZip start(String log) {
    String track = PyJavHints.madeAudio(log);
    List<String[]> compared = MusicCompare.tracks(log);
    String base;
    if (!compared.isEmpty()) base = MusicCompare.reportName(log).replaceAll("-ranking\\.txt$", "");
    else if (track != null) base = track.replaceAll("\\.[A-Za-z0-9]{2,4}$", "");
    else base = "sogni-music";
    current = new RunZip(base + ".zip");
    return current;
  }

  public static synchronized RunZip current() {
    return current;
  }

  /** Ends the gathering: the zip to write, or null when none was going on. */
  public static synchronized RunZip finish() {
    RunZip z = current;
    current = null;
    return z;
  }

  /** A file made in memory; a second one of the same name gets "(1)" before its extension. */
  public synchronized void add(String path, byte[] data) {
    if (path == null || data == null) return;
    this.sources.add(new ZipPack.Source(unique(path), data));
  }

  /** A file on disk. */
  public synchronized void add(String path, File file) {
    if (path == null || file == null || !file.isFile()) return;
    this.sources.add(new ZipPack.Source(unique(path), file));
  }

  /** A run's log, as "<program> log.txt" ("DrumMidi_CRT SogniMusic run 1 turbo log.txt" with its output named). */
  public void addLog(String program, String about, String log) {
    if (log == null || log.length() == 0) return;
    String p = program == null ? "run" : program.replaceAll("\\.(java|py|jar|class)$", "");
    String a = about == null || about.length() == 0 ? "" : " " + about.replaceAll("\\.[A-Za-z0-9]{2,4}$", "");
    add((p + a + " log.txt").replaceAll("[\\\\/:*?\"<>|]", "_"), log.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public synchronized List<ZipPack.Source> sources() {
    return new ArrayList<ZipPack.Source>(this.sources);
  }

  /** The most a part holds (sending often stops at 30 MB): a bigger run is split into parts of its own. */
  public static final long PART = 25L * 1024 * 1024;

  /**
   * The files split into zips of at most `limit` bytes each (their sizes before zipping: WAVs and
   * MP3s barely shrink), in their order; a file bigger than that alone goes in a part of its own.
   */
  public synchronized List<List<ZipPack.Source>> parts(long limit) {
    List<List<ZipPack.Source>> out = new ArrayList<List<ZipPack.Source>>();
    List<ZipPack.Source> part = new ArrayList<ZipPack.Source>();
    long used = 0;
    for (ZipPack.Source s : this.sources) {
      long size = s.data != null ? s.data.length : s.file != null ? s.file.length() : 0;
      if (!part.isEmpty() && used + size > limit) {
        out.add(part);
        part = new ArrayList<ZipPack.Source>();
        used = 0;
      }
      part.add(s);
      used += size;
    }
    if (!part.isEmpty()) out.add(part);
    return out;
  }

  /** A part's file name: the run's name alone when it is one zip, else "<name>-part1of2.zip". */
  public String partName(int i, int of) {
    return of <= 1 ? this.name : this.name.replaceAll("\\.zip$", "") + "-part" + (i + 1) + "of" + of + ".zip";
  }

  public synchronized int size() {
    return this.sources.size();
  }

  String unique(String path) {
    String p = path.replace('\\', '_').replace('/', '_');
    if (this.paths.add(p)) return p;
    int dot = p.lastIndexOf('.');
    String stem = dot > 0 ? p.substring(0, dot) : p, ext = dot > 0 ? p.substring(dot) : "";
    for (int n = 1; ; n++) {
      String q = stem + "(" + n + ")" + ext;
      if (this.paths.add(q)) return q;
    }
  }
}
