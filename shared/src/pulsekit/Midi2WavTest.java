package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Midi2Wav's test (--test): once Midi2Wav has made its WAV, PyJav runs DrumMidi_CRT on the WAV (its
 * drum hits become a MIDI, imported as a file set and a song, as Make drum MIDI does), then
 * CompareHits on the WAV, DrumMidi's MIDI and the song (the hits heard against the MIDI and the
 * song, with suggestions for DrumMidi's switches), and CompareHits once more on the MIDI Midi2Wav
 * played against DrumMidi's (what the transcription missed or added: the MIDI played is the truth).
 * One test at a time; each step is a PyJav run, so its log shows as usual.
 */
public final class Midi2WavTest {
  public static final String FLAG = "--test";
  /** Waiting for DrumMidi_CRT's run, for the first CompareHits run, for the second. */
  public static final int DRUM_MIDI = 1;
  public static final int COMPARE = 2;
  public static final int AGAINST_PLAYED = 3;

  /** The MIDI Midi2Wav played, the WAV it made, DrumMidi's MIDI, the song's MIDI (paths). */
  public String played = "";
  public String wav = "";
  public String drums = "";
  public String song = "";
  public int stage;
  /** The steps' reports, for the summary at the end. */
  public final StringBuilder report = new StringBuilder();

  /** The test in progress, or null. */
  private static Midi2WavTest now;

  private Midi2WavTest() {}

  public static synchronized Midi2WavTest current() {
    return now;
  }

  public static synchronized void stop() {
    now = null;
  }

  /** True for a Midi2Wav run with --test. */
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
    this.stage = COMPARE;
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
    this.report.append("== CompareHits --truth: DrumMidi's MIDI against the drums played (").append(new File(this.played).getName()).append(")\n").append(clean(log)).append('\n');
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
