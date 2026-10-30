package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SogniMusic --zip: everything a run made, gathered as it goes (the tracks, their WAVs, DrumMidi's
 * MIDI and results.txt, the models' ranking, each run's log), then written into one zip in
 * Download when the last step is over, to send in one go.
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
