package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Midi2Wav's test (--test): once Midi2Wav has made its WAV, PyJav runs DrumMidi_CRT on the WAV (its
 * drum hits become a MIDI, imported as a file set and a song, as Make drum MIDI does), then
 * CompareHits on the WAV, DrumMidi's MIDI and the song (the hits heard against the MIDI and the
 * song, with suggestions for DrumMidi's switches), and CompareHits --truth on DrumMidi's MIDI and the
 * song against the MIDI Midi2Wav played (the truth): what DrumMidi missed or added, and what the
 * import then lost.
 * One test at a time; each step is a PyJav run, so its log shows as usual.
 */
public final class Midi2WavTest {
  public static final String FLAG = "--test";
  /** SogniMusic's and MidiDrumGen's --vst: their drum MIDI played through a VST by vst_render.py (on a computer). */
  public static final String VST = "--vst";
  /** vst_render.py's settings kept from its saved Params for such a render (the plugins, their settings, the format). */
  static final List<String> VST_KEPT = Arrays.asList("--plugin", "--state", "--effect", "--effect_state", "--rate", "--tail", "--volume", "--bits");
  /** Waiting for DrumMidi_CRT's run, for the first CompareHits run, for the second. */
  public static final int DRUM_MIDI = 1;
  public static final int COMPARE = 2;
  public static final int AGAINST_PLAYED = 3;
  /** Midi2Wav --dir --test: waiting for DrumMidi_CRT --dir on the folder's WAVs, then for CompareHits --batch. */
  public static final int BATCH_DRUMS = 4;
  public static final int BATCH_COMPARE = 5;

  /** The MIDI Midi2Wav played, the WAV it made, DrumMidi's MIDI, the song's MIDI (paths). */
  public String played = "";
  public String wav = "";
  public String drums = "";
  public String song = "";
  public int stage;
  /** The steps' reports, for the summary at the end. */
  public final StringBuilder report = new StringBuilder();
  /** SogniMusic's test: what the track was asked to be, and the user's saved DrumMidi switches; null for Midi2Wav's. */
  public MusicExpect expect;
  public String drumSaved = "";
  /** What was checked once DrumMidi had run (SogniMusic's test). */
  public String checks = "";
  /** SogniMusic's run: its --test (DrumMidi, CompareHits, the report) and its --vst (DrumMidi's MIDI to vst_render.py). */
  public boolean testing = true;
  public boolean vst;
  /** Midi2Wav --dir's folder (its batch test), and its --recursive. */
  public String batch = "";
  public boolean recursive;

  /** The test in progress, or null. */
  private static Midi2WavTest now;

  private Midi2WavTest() {}

  public static synchronized Midi2WavTest current() {
    return now;
  }

  public static synchronized void stop() {
    now = null;
  }

  /** True while a test waits for DrumMidi's MIDI: its import makes the song without asking "Make a song?". */
  public static synchronized boolean makesSong() {
    return now != null && now.stage == DRUM_MIDI;
  }

  /** True for a MidiDrumGen run whose MIDI goes on to Midi2Wav (--towav), or to its test (--test). */
  public static boolean fromGen(String program, List<String> argv) {
    return "MidiDrumGen.java".equals(program) && argv != null && (argv.contains("--towav") || argv.contains(FLAG) || argv.contains(VST));
  }

  /** True for a MidiDrumGen run whose MIDI goes to vst_render.py (--vst) instead of Midi2Wav. */
  public static boolean genToVst(String program, List<String> argv) {
    return "MidiDrumGen.java".equals(program) && argv != null && argv.contains(VST);
  }

  /**
   * vst_render.py's arguments for a MIDI: the MIDI, then the plugins, their settings and the format
   * from vst_render's saved Params (`saved`), and --test (with --no-toms when saved) when `test`.
   * Null when no plugin was saved: pick one in vst_render.py's Params first.
   */
  public static List<String> vstArgs(String midiPath, String vstSource, String saved, boolean test) {
    String merged = ProgramParams.merge(ProgramParams.parse(vstSource == null ? "" : vstSource), "", saved);
    List<String> rest = JavaRun.split(merged == null ? "" : merged);
    List<String> out = new ArrayList<String>();
    out.add(midiPath);
    boolean plugin = false;
    for (int i = 0; i < rest.size(); i++) {
      String a = rest.get(i);
      if (VST_KEPT.contains(a) && i + 1 < rest.size() && !rest.get(i + 1).startsWith("--")) {
        String v = rest.get(++i);
        if (v.trim().length() == 0) continue;
        if (a.equals("--plugin")) plugin = true;
        out.add(a);
        out.add(v);
      } else if (a.equals("--no-toms") && test) {
        out.add(a);
      }
    }
    if (!plugin) return null;
    if (test) out.add(FLAG);
    return out;
  }

  /**
   * Midi2Wav's arguments for MidiDrumGen's MIDI: the MIDI, Midi2Wav's own saved Params (`saved`:
   * the SoundFont, rate, volume, Add to DB) and --test exactly when MidiDrumGen had it.
   */
  public static List<String> genToWavArgs(String midiPath, List<String> genArgv, String midi2wavSource, String saved) {
    String merged = ProgramParams.merge(ProgramParams.parse(midi2wavSource == null ? "" : midi2wavSource), "", saved);
    List<String> out = new ArrayList<String>();
    out.add(midiPath);
    List<String> rest = JavaRun.split(merged == null ? "" : merged);
    for (int i = 0; i < rest.size(); i++) {
      String a = rest.get(i);
      if (a.equals(FLAG)) continue;
      // An input or output name saved with Midi2Wav's Params is not this run's.
      if (!a.startsWith("--") && (i == 0 || !rest.get(i - 1).startsWith("--") || rest.get(i - 1).equals("--addtodb"))) continue;
      out.add(a);
    }
    if (genArgv != null && genArgv.contains(FLAG)) out.add(FLAG);
    return out;
  }

  /** True for a SogniMusic run with --test. */
  public static boolean musicWanted(String program, List<String> argv) {
    return "SogniMusic.java".equals(program) && argv != null && argv.contains(FLAG);
  }

  /** True for a SogniMusic run whose track goes on to DrumMidi: its --test, or (on a computer) its --vst. */
  public static boolean musicSteps(String program, List<String> argv, boolean vstHere) {
    return musicWanted(program, argv) || vstHere && "SogniMusic.java".equals(program) && argv != null && argv.contains(VST);
  }

  /**
   * SogniMusic's test without drums asked for (no --drums_only, and no drums in --instruments):
   * what to warn about, or null. DrumMidi finds nothing to check in a track without drums.
   */
  public static String drumsWarning(String program, List<String> argv) {
    if (!musicWanted(program, argv)) return null;
    if (argv.contains("--drums_only")) return null;
    int i = argv.indexOf("--instruments");
    if (i >= 0 && i + 1 < argv.size() && argv.get(i + 1).toLowerCase(java.util.Locale.ROOT).matches("(?s).*(drum|kit|percussion).*")) return null;
    return "SogniMusic's test makes drum MIDI from the track and checks it, but this run does not ask for drums.\n"
        + "Tick --drums_only, or name drums in --instruments (for example \"drums, bass\").";
  }

  /** True for a SogniMusic run whose track gets a WAV copy (all of them but --no_wav; --test needs it). */
  public static boolean musicToWav(String program, List<String> argv) {
    return "SogniMusic.java".equals(program) && (argv == null || !argv.contains("--no_wav") || argv.contains(FLAG) || argv.contains(VST));
  }

  /**
   * SogniMusic's test starts with the WAV made from its track: there is no MIDI played to compare
   * with, so the test runs DrumMidi (set from what the track was asked to be) and CompareHits, and
   * its report says what was asked for and checks DrumMidi's MIDI against it.
   */
  public static synchronized Midi2WavTest startMusic(List<String> argv, File wav, String savedDrumMidi) {
    now = null;
    if (wav == null || !wav.isFile()) return null;
    Midi2WavTest t = new Midi2WavTest();
    t.wav = wav.getAbsolutePath();
    t.expect = MusicExpect.of(argv);
    t.drumSaved = savedDrumMidi == null ? "" : savedDrumMidi;
    t.testing = argv != null && argv.contains(FLAG);
    t.vst = argv != null && argv.contains(VST);
    t.stage = DRUM_MIDI;
    now = t;
    return t;
  }

  /** DrumMidi_CRT's arguments in SogniMusic's test: the WAV, its MIDI's name, and the switches the track calls for. */
  public List<String> drumMidiArgs() {
    String stem = new File(this.wav).getName().replaceAll("(?i)\\.wav$", "");
    return this.expect.drumMidiArgs(this.wav, stem + ".mid", this.drumSaved);
  }

  /** The report's title. */
  public String title() {
    if (this.batch.length() > 0) return "Midi2Wav batch test: " + new File(this.batch).getName();
    return (this.expect != null ? "SogniMusic test: " : "Midi2Wav test: ") + new File(this.wav).getName();
  }

  /** True for a Midi2Wav run with --dir and --test: its batch test (DrumMidi --dir, CompareHits --batch). */
  public static boolean batchWanted(String program, List<String> argv) {
    return wanted(program, argv) && valueOf(argv, "--dir") != null;
  }

  static String valueOf(List<String> argv, String flag) {
    int i = argv == null ? -1 : argv.indexOf(flag);
    if (i < 0 || i + 1 >= argv.size()) return null;
    String v = argv.get(i + 1);
    return v == null || v.trim().length() == 0 || v.startsWith("--") ? null : v.trim();
  }

  /**
   * Midi2Wav --dir is over: when it succeeded, the batch test starts on its folder (DrumMidi_CRT
   * --dir makes <wav>-dm.mid of each WAV, then CompareHits --batch sums them per kit and style).
   * Its report is kept as batch-test.txt. Null when the run failed.
   */
  public static synchronized Midi2WavTest startBatch(String status, List<String> argv) {
    now = null;
    String dir = valueOf(argv, "--dir");
    if (status == null || !status.startsWith("Succeeded") || dir == null || !new File(dir).isDirectory()) return null;
    Midi2WavTest t = new Midi2WavTest();
    t.batch = new File(dir).getAbsolutePath();
    t.wav = new File(t.batch, "batch.wav").getPath();
    t.recursive = argv.contains("--recursive");
    t.stage = BATCH_DRUMS;
    now = t;
    return t;
  }

  /** DrumMidi_CRT's arguments in the batch test: the folder (and its subfolders, as Midi2Wav had them). */
  public List<String> batchDrumArgs() {
    List<String> out = new ArrayList<String>();
    out.add("--dir");
    out.add(this.batch);
    if (this.recursive) out.add("--recursive");
    return out;
  }

  /** DrumMidi --dir is over: CompareHits --batch next; false (the test is over) when it made nothing. */
  public synchronized boolean batchDrumsDone(String status) {
    if (status == null || !status.startsWith("Succeeded") || status.contains(" on 0 of ")) {
      now = null;
      return false;
    }
    this.stage = BATCH_COMPARE;
    return true;
  }

  /** CompareHits' arguments in the batch test. */
  public List<String> batchCompareArgs() {
    return new ArrayList<String>(Arrays.asList("--batch", this.batch));
  }

  /** CompareHits --batch is over (its summary, or why it failed): the test is over. */
  public synchronized void batchCompareDone(String log) {
    this.report.append("== CompareHits --batch: each groove against DrumMidi's MIDI of each render, per kit\n").append(clean(log)).append('\n');
    now = null;
  }

  /** The whole report: for SogniMusic, what was asked for, how DrumMidi ran and the checks first; then the comparisons. */
  public String text() {
    StringBuilder sb = new StringBuilder(title()).append("\n\n");
    if (this.expect != null) {
      sb.append(this.expect.asked()).append(this.expect.drumMidiWhy(this.drumSaved)).append("\n\n");
      if (this.checks.length() > 0) sb.append(this.checks).append('\n');
    }
    return sb.append(this.report.toString().trim()).toString();
  }

  /** The WAV's length in seconds, from its header; 0 when it cannot be read. */
  static double wavSeconds(File f) {
    try {
      java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r");
      try {
        byte[] h = new byte[4096];
        int n = r.read(h);
        int rate = 0;
        int block = 0;
        for (int i = 12; i + 8 <= n; ) {
          String id = new String(h, i, 4, java.nio.charset.StandardCharsets.US_ASCII);
          int len = (h[i + 4] & 0xff) | ((h[i + 5] & 0xff) << 8) | ((h[i + 6] & 0xff) << 16) | ((h[i + 7] & 0xff) << 24);
          if (id.equals("fmt ") && i + 22 <= n) {
            rate = (h[i + 12] & 0xff) | ((h[i + 13] & 0xff) << 8) | ((h[i + 14] & 0xff) << 16) | ((h[i + 15] & 0xff) << 24);
            block = (h[i + 20] & 0xff) | ((h[i + 21] & 0xff) << 8);
          }
          if (id.equals("data")) {
            long data = len & 0xffffffffL;
            if (data == 0 || data > r.length()) data = r.length() - i - 8;
            return rate > 0 && block > 0 ? data / (double) block / rate : 0;
          }
          i += 8 + len + (len & 1);
        }
      } finally {
        r.close();
      }
    } catch (Exception ignored) {
      // no length
    }
    return 0;
  }

  /** True for a Midi2Wav run with --test (with --dir: its batch test, batchWanted). */
  public static boolean wanted(String program, List<String> argv) {
    return "Midi2Wav.java".equals(program) && argv != null && argv.contains(FLAG);
  }

  /**
   * Midi2Wav's run is over: when it succeeded, the test starts with the WAV (`wav`, written where
   * DrumMidi can read it) and the MIDI it played (the run's first argument naming a MIDI file).
   * Null (and no test) when the run failed or made no WAV.
   */
  public static synchronized Midi2WavTest start(String status, List<String> argv, File wav) {
    now = null;
    if (status == null || !status.startsWith("Succeeded") || wav == null || !wav.isFile()) return null;
    Midi2WavTest t = new Midi2WavTest();
    t.wav = wav.getAbsolutePath();
    if (argv != null) {
      for (String a : argv) {
        if (a == null) continue;
        String low = a.toLowerCase(java.util.Locale.ROOT);
        if ((low.endsWith(".mid") || low.endsWith(".midi")) && new File(a).isFile()) {
          t.played = a;
          break;
        }
      }
    }
    t.stage = DRUM_MIDI;
    now = t;
    return t;
  }

  /**
   * DrumMidi's run is over: its MIDI (`drumsMidi`, the first .mid among its files) is kept beside
   * the WAV, and the song Pulsekit made from it (found by the file set that keeps that MIDI) as a
   * song MIDI. False (and the test stops) when DrumMidi made no MIDI.
   */
  public synchronized boolean drumMidiDone(byte[] drumsMidi, String drumsName, List<?> learned, List<Engine.ImportedSong> songs) throws java.io.IOException {
    if (drumsMidi == null || drumsMidi.length < 14) {
      now = null;
      return false;
    }
    File dir = new File(this.wav).getParentFile();
    String stem = new File(this.wav).getName().replaceAll("(?i)\\.wav$", "");
    File d = new File(dir, stem + "-drums.mid");
    java.nio.file.Files.write(d.toPath(), drumsMidi);
    this.drums = d.getAbsolutePath();
    Engine.ImportedSong s = songOf(drumsMidi, drumsName, learned, songs);
    if (s != null && !s.parts.isEmpty()) {
      File sm = new File(dir, stem + "-song.mid");
      java.nio.file.Files.write(sm.toPath(), Engine.encodeSongMidi(s.parts));
      this.song = sm.getAbsolutePath();
    }
    if (this.expect != null) this.checks = this.expect.checks(drumsMidi, wavSeconds(new File(this.wav)));
    this.stage = COMPARE;
    // SogniMusic's --vst without --test: DrumMidi's MIDI is all it wanted.
    if (!this.testing) now = null;
    return true;
  }

  /** CompareHits' arguments for the first comparison: the WAV, DrumMidi's MIDI, the song (when one was made). */
  public List<String> compareArgs() {
    List<String> out = new ArrayList<String>();
    out.add(this.wav);
    out.add(this.drums);
    if (this.song.length() > 0) out.add(this.song);
    return out;
  }

  /** CompareHits' arguments for the second: the MIDI Midi2Wav played against DrumMidi's. */
  public List<String> againstPlayedArgs() {
    List<String> out = new ArrayList<String>();
    out.add(this.played);
    out.add(this.drums);
    if (this.song.length() > 0) out.add(this.song);
    out.add("--truth");
    return out;
  }

  /** The first CompareHits run is over: the second runs when the MIDI played is known; false: the test is over. */
  public synchronized boolean compareDone(String log) {
    this.report.append("== CompareHits: the WAV, DrumMidi's MIDI").append(this.song.length() > 0 ? " and the song" : "").append('\n').append(clean(log)).append('\n');
    if (this.played.length() == 0) {
      now = null;
      return false;
    }
    this.stage = AGAINST_PLAYED;
    return true;
  }

  /** The second CompareHits run is over: the test is over. */
  public synchronized void againstPlayedDone(String log) {
    this.report.append("== CompareHits --truth: DrumMidi's MIDI").append(this.song.length() > 0 ? " and the song" : "").append(" against the drums played (")
        .append(new File(this.played).getName()).append(")\n").append(clean(log)).append('\n');
    now = null;
  }

  /** The step's text without the run's own header lines ($ java ..., ART · compiled...). */
  static String clean(String log) {
    StringBuilder sb = new StringBuilder();
    for (String line : (log == null ? "" : log).split("\n")) {
      String t = line.trim();
      if (t.startsWith("$ ") || t.startsWith("ART ") || t.startsWith("Picked up") || t.startsWith("file ") || t.startsWith("saved ")) continue;
      sb.append(line).append('\n');
    }
    return sb.toString().trim();
  }

  /** The song Pulsekit made from file set whose source MIDI is `drumsMidi` (else one named after it). */
  static Engine.ImportedSong songOf(byte[] drumsMidi, String drumsName, List<?> learned, List<Engine.ImportedSong> songs) {
    List<String> sources = new ArrayList<String>();
    if (learned != null) {
      for (Object o : learned) {
        if (!(o instanceof Engine.Learned)) continue;
        String s = Engine.sourceOf((Engine.Learned) o);
        if (s != null && s.length() > 0 && !sources.contains(s)) sources.add(s);
      }
    }
    for (int i = sources.size() - 1; i >= 0; i--) {
      String src = sources.get(i);
      String key = Engine.fileSetMidiKeyForLabel(src);
      byte[] kept = key == null ? null : HitCompare.fileSetMidi(key);
      if (kept != null && Arrays.equals(kept, drumsMidi)) {
        Engine.ImportedSong s = HitCompare.songFor(src, songs);
        if (s != null) return s;
      }
    }
    return drumsName == null ? null : HitCompare.songFor(Engine.stemNameFromMidi(drumsName), songs);
  }
}
