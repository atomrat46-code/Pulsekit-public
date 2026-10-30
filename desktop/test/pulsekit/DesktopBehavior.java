package pulsekit;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Desktop characterization test: runs one scenario of the real app (under a display, e.g. Xvfb)
 * and writes what it shows and holds to OUT_DIR/NAME.txt. Run every scenario before and after a
 * refactor (desktop/test/run.sh) and compare (desktop/test/compare.sh): any difference is a
 * behavior change.
 *
 * Methods and fields are looked up by name on the window and on every pulsekit object it holds,
 * so the scenarios keep working when code moves between classes.
 *
 *   java -cp Pulsekit.jar:test-classes -Duser.home=FRESH pulsekit.DesktopBehavior SCENARIO OUT_DIR
 */
public final class DesktopBehavior {
  private JFrame frame;
  private final List<String> dialogs = Collections.synchronizedList(new ArrayList<String>());
  private final LinkedList<String> answers = new LinkedList<String>();
  /** The file the next file chooser picks (Open or Save), then cleared; null leaves choosers to the answers. */
  private volatile File chooseNext;
  /** Looks inside the next dialog (on the Swing thread) before it is answered, then cleared. */
  private volatile java.util.function.Consumer<JDialog> inspectNext;
  private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
  private final StringBuilder out = new StringBuilder();

  public static void main(String[] args) throws Exception {
    DesktopBehavior t = new DesktopBehavior();
    int code = 0;
    try {
      t.start();
      // Save prompt (General settings: Ask where to save) is off, as before it was made, but in the scenarios that test it.
      PromptSave.load(PromptDb.dir());
      PromptSave.setAsk(false);
      Method m = DesktopBehavior.class.getDeclaredMethod(args[0]);
      m.invoke(t);
    } catch (Throwable e) {
      Throwable c = e instanceof java.lang.reflect.InvocationTargetException ? e.getCause() : e;
      t.out.append("SCENARIO FAILED: ").append(c).append('\n');
      for (StackTraceElement el : c.getStackTrace()) {
        if (el.getClassName().startsWith("pulsekit")) t.out.append("  at ").append(el).append('\n');
      }
      code = 1;
    }
    for (Throwable e : t.errors) t.out.append("ERROR: ").append(e).append('\n');
    if (!t.errors.isEmpty()) code = 1;
    File dir = new File(args[1]);
    dir.mkdirs();
    Files.write(new File(dir, args[0] + ".txt").toPath(), normalize(t.out.toString()).getBytes(StandardCharsets.UTF_8));
    System.exit(code);
  }

  // ------------------------------------------------------------ scenarios

  void s01_boot() throws Exception {
    snap("boot");
  }

  void s02_views() throws Exception {
    for (String v : new String[] {"pattern", "combo", "fills", "pads", "song", "py", "prompts", "import", "export", "midisettings", "help"}) {
      call("showView", v);
      snap("view " + v);
    }
  }

  void s03_grid_taps() throws Exception {
    call("loadStyle", "rock", false);
    call("showView", "pattern");
    int[][] cells = (int[][]) get("cells");
    edt(() -> {
      cells[1][2] = 100;
      cells[5][3] = 0;
      call("refreshGrid");
    });
    call("toggleSteps");
    state("after taps and steps");
    call("toggleSteps");
    state("steps back");
  }

  void s04_play_stop() throws Exception {
    call("loadStyle", "funk", false);
    call("togglePlay");
    Thread.sleep(300);
    call("stop");
    state("after play and stop");
  }

  void s05_import_pattern_midi() throws Exception {
    call("ingest", Engine.encodeMidi(Engine.styleCells(Engine.styles().get("funk")), 104), "Funky pattern.mid");
    snap("after pattern MIDI");
  }

  void s06_import_song_midi() throws Exception {
    answers.add("Yes");
    call("ingest", Engine.encodeSongMidi(songParts()), "Whole song.mid");
    snap("after song MIDI, Make song yes");
  }

  void s07_import_sng() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("Intro", 96, Engine.styleCells(Engine.styles().get("house")), 4));
    parts.add(Engine.rest(96, 2));
    parts.add(Engine.fill("toms", 96, 1));
    call("ingest", Engine.encodeSng(parts, "Sketch"), "Sketch.sng");
    snap("after .sng");
  }

  void s08_fset_round_trip() throws Exception {
    answers.add("Yes");
    call("ingest", Engine.encodeSongMidi(songParts()), "Set A.mid");
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    @SuppressWarnings("unchecked")
    List<Engine.LearnedFill> fills = (List<Engine.LearnedFill>) get("learnedFills");
    @SuppressWarnings("unchecked")
    Map<String, String> pairs = (Map<String, String>) get("fillernPairs");
    byte[] fset = Engine.encodeFset(Engine.collectFset("Set A", "Set A", learned, fills, pairs));
    call("removeImportSource", "Set A");
    call("loadFset", fset, "Set A copy.fset");
    snap("after fset export and load");
  }

  void s09_programs() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Java", "DrumMidi_CRT.java");
    state("Java program selected");
    call("openCodeFile", "sogni-client.mjs");
    snap("Code file opened");
  }

  void s10_drum_midi_settings() throws Exception {
    call("showView", "midisettings");
    click("Merge hits");
    click("Treat a one-off bar after a repeated groove as a fill");
    click(Engine.FILLERN_MODE_LABELS[1]);
    click("+");
    out.append("settings: ").append(MidiImportSettings.encode().replace('\n', ' ')).append('\n');
    File saved = new File(System.getProperty("user.home"), ".pulsekit/drum-midi-settings.txt");
    out.append("saved: ").append(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8).replace('\n', ' ')).append('\n');
    snap("settings page");
  }

  void s11_fillerns() throws Exception {
    answers.add("No");
    call("ingest", Engine.encodeSongMidi(songParts()), "Fill set.mid");
    call("showView", "combo");
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    String key = "l:" + learned.get(0).id;
    call("pickFillern", key, "roll");
    call("setFillernMode", key, Engine.FILLERN_END);
    snap("Fillern made, type end");
  }

  void s12_song_editing() throws Exception {
    answers.add("Yes");
    call("ingest", Engine.encodeSongMidi(songParts()), "Edit set.mid");
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    String key = "l:" + learned.get(0).id;
    call("showView", "combo");
    call("pickFillern", key, "roll");
    call("setFillernMode", key, Engine.FILLERN_END);
    call("showView", "song");
    call("replaceWithFillern", 2, key);
    call("loadStyle", "rock", false);
    call("addPart", Engine.groove("Rock", 110, Engine.styleCells(Engine.styles().get("rock")), 2));
    answers.add("Copy into set");
    call("saveSongInto", "Edit set", "Edit set", call("importedSong"));
    snap("song edited and saved to set");
    @SuppressWarnings("unchecked")
    List<Engine.Part> parts = (List<Engine.Part>) call("activeSong");
    out.append("export name: ").append(Engine.songFilename(parts, (String) call("songFileSet", parts))).append('\n');
  }

  void s13_fills_in_sets() throws Exception {
    answers.add("No");
    call("ingest", Engine.encodeSongMidi(songParts()), "Fills set.mid");
    call("loadStyle", "rock", false);
    call("copyFillToFileSet", "roll", "Fills set", false);
    call("copyFillToFileSet", "crash", "Fills set", false);
    call("showView", "fills");
    snap("fills copied in");
  }

  void s14_persistence_write() throws Exception {
    answers.add("Yes");
    call("ingest", Engine.encodeSongMidi(songParts()), "Keeper.mid");
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    call("showView", "combo");
    call("pickFillern", "l:" + learned.get(0).id, "crash");
    call("persistLearned");
    state("written");
  }

  void s14_persistence_read() throws Exception {
    snap("after restart");
  }

  void s15_params() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Java", "DrumMidi_CRT.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("in.wav out.mid --sens 2.0"));
    answers.add("Reset to suggested values");
    answers.add("OK");
    call("openParams");
    state("params saved");
  }

  void s16_song_play_follows() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    for (int i = 0; i < 16; i++) parts.add(Engine.groove("P" + i, 120, Engine.styleCells(Engine.styles().get("rock")), 1));
    parts.add(2, Engine.fill("Snare roll", 120, 1));
    call("addImportedSong", "Long song", parts);
    edt(() -> {
      set("songPlay", Boolean.TRUE);
      set("songPart", Integer.valueOf(14));
      call("refreshSong");
    });
    Thread.sleep(400);
    JComponent timeline = (JComponent) get("timeline");
    out.append("timeline scrolled=").append(timeline.getVisibleRect().x > 0).append('\n');
    edt(() -> set("songPlay", Boolean.FALSE));
    snap("long song");
  }

  void s17_song_audio_export() throws Exception {
    answers.add("Yes");
    call("ingest", Engine.encodeSongMidi(songParts()), "Passing Ships.mid");
    call("showView", "export");
    answers.add("Save");
    click("Song WAV");
    answers.add("Save");
    click("Song MP3");
    File home = new File(System.getProperty("user.home"));
    String[] names = home.list();
    java.util.Arrays.sort(names);
    for (String n : names) {
      File f = new File(home, n);
      if (!f.isFile()) continue;
      byte[] b = Files.readAllBytes(f.toPath());
      out.append("saved ").append(n).append(' ').append(b.length > 1000 ? "has audio" : "too small").append(' ')
          .append(new String(b, 0, 4, StandardCharsets.ISO_8859_1).replaceAll("[^A-Za-z]", "?")).append('\n');
    }
    @SuppressWarnings("unchecked")
    List<Engine.Part> parts = (List<Engine.Part>) call("activeSong");
    short[] pcm = (short[]) call("songPcm", parts);
    double sec = 0;
    for (Engine.Part p : parts) sec += p.repeats * p.steps * 15.0 / p.bpm;
    out.append("song ").append(String.format("%.2f", sec)).append(" s, audio as long as the song: ").append(Math.abs(pcm.length / 22050.0 - sec) < 0.001).append('\n');
  }

  void s18_compare_hits() throws Exception {
    answers.add("Yes");
    Engine.stageSourceMidi(Engine.encodeSongMidi(songParts()), "Passing Ships.mid");
    call("ingest", Engine.encodeSongMidi(songParts()), "Passing Ships.mid");
    call("showView", "comparehits");
    idle();
    out.append("set chips: ").append(((java.awt.Container) get("setsBox")).getComponentCount()).append('\n');
    click("Compare");
    javax.swing.JTextArea result = (javax.swing.JTextArea) get("result");
    for (int i = 0; i < 200 && result.getText().startsWith("Comparing"); i++) Thread.sleep(50);
    idle();
    out.append(result.getText()).append('\n');
  }

  void s19_params_from_program() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Java", "CompareHits.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("/tmp/a.wav /tmp/d.mid"));
    answers.add("OK");
    call("openParams");
    out.append("CompareHits args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText()).append('\n');
    call("selectListedProgram", "Python", "drum_midi.py");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--bpm 100"));
    answers.add("OK");
    call("openParams");
    out.append("drum_midi.py args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText()).append('\n');
  }

  void s20_save_program_output() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Java", "CompareHits.java");
    out.append("name: ").append(PyJavHints.resultsFileName((String) get("pyName"))).append('\n');
    answers.add("Save");
    edt(() -> SaveText.save((Pulsekit) frame, PyJavHints.resultsFileName((String) get("pyName")), "Kick 271 261 188\nSucceeded"));
    File saved = new File(System.getProperty("user.home"), "CompareHits_test_results.txt");
    out.append("written: ").append(saved.isFile() ? new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8).replace('\n', '|') : "nothing").append('\n');
  }

  void s21_song_duplicate_delete() throws Exception {
    answers.add("Yes");
    call("ingest", Engine.encodeSongMidi(songParts()), "Passing Ships.mid");
    call("showView", "song");
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    Engine.ImportedSong first = songs.get(0);
    edt(() -> call("duplicateSong", first));
    out.append("after duplicate: ");
    for (Engine.ImportedSong s : songs) out.append(s.name).append(" (").append(s.parts.size()).append(" parts) ");
    out.append('\n');
    Engine.ImportedSong copy = songs.get(1);
    answers.add("Cancel");
    call("confirmDeleteSong", copy);
    out.append("after Cancel: ").append(songs.size()).append(" songs\n");
    answers.add("OK");
    call("confirmDeleteSong", copy);
    out.append("after OK: ").append(songs.size()).append(" songs, left ").append(songs.get(0).name).append('\n');
  }

  void s22_make_song_names_after_file_set() throws Exception {
    for (String file : new String[] {"passing ships v10 121 aaaaaa.mid", "passing ships v10 121 wVqTYx.mid"}) {
      answers.add("Yes");
      call("ingest", Engine.encodeSongMidi(songParts()), file);
    }
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    out.append("made on import: ");
    for (Engine.ImportedSong s : songs) out.append(s.name).append(" | ");
    out.append('\n');
    answers.add("Make another");
    edt(() -> call("makeFileSetSong", "f:passing ships v10 121 wVqTYx", "passing ships v10 121 wVqTYx"));
    Engine.ImportedSong made = songs.get(0);
    out.append("Make song: ").append(made.name).append(", file set ").append(made.fileSet).append('\n');
    Object bar = get("songLaneBar");
    out.append("song bar scrolls sideways: ").append(((java.awt.Component) bar).getParent() instanceof javax.swing.JViewport).append('\n');
  }

  void s23_file_set_compare_hits() throws Exception {
    byte[] midi = Engine.encodeSongMidi(songParts());
    Engine.stageSourceMidi(midi, "Passing Ships.mid");
    answers.add("Yes");
    call("ingest", midi, "Passing Ships.mid");
    call("showView", "combo");
    out.append("has source MIDI: ").append(call("hasSourceMidi", "f:Passing Ships")).append('\n');
    answers.add("Close");
    call("compareFileSetHits", "f:Passing Ships", "Passing Ships");
    for (int i = 0; i < 200 && Engine.fileSetResults("Passing Ships") == null; i++) Thread.sleep(50);
    Thread.sleep(500);
    idle();
    String text = Engine.fileSetResults("Passing Ships");
    out.append("results: ").append(text == null ? "none" : text.substring(0, text.indexOf('\n'))).append(" ... ")
        .append(text == null ? "" : text.substring(text.lastIndexOf("Succeeded")).trim()).append('\n');
    final boolean[] row = {false};
    edt(() -> row[0] = named(frame, "fileset-results"));
    out.append("row under file set: ").append(row[0]).append('\n');
    Engine.fileSetAudio.clear();
    Engine.loadFileSetAudioDir(new File(System.getProperty("user.home"), ".pulsekit/fset-audio"));
    out.append("kept on disk: ").append(text != null && text.equals(Engine.fileSetResults("Passing Ships"))).append('\n');
  }

  void s24_file_set_songs() throws Exception {
    answers.add("No");
    call("ingest", Engine.encodeSongMidi(songParts()), "Passing Ships.mid");
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    edt(() -> call("openFileSetInfo", "f:Passing Ships", "Passing Ships"));
    final boolean[] none = {false};
    edt(() -> none[0] = named(frame, "info-songs"));
    out.append("info has Songs section: ").append(none[0]).append('\n');
    edt(() -> call("makeFileSetSong", "f:Passing Ships", "Passing Ships"));
    out.append("first Make song: ").append(songs.size()).append(" song, file set ").append(songs.get(0).fileSet).append('\n');
    Engine.ImportedSong first = songs.get(0);
    edt(() -> call("openFileSetInfo", "f:Passing Ships", "Passing Ships"));
    final boolean[] row = {false};
    edt(() -> row[0] = named(frame, "info-song:" + first.name));
    out.append("info lists it: ").append(row[0]).append('\n');
    answers.add("Replace");
    edt(() -> call("makeFileSetSong", "f:Passing Ships", "Passing Ships"));
    out.append("Replace: ").append(songs.size()).append(" song, same ").append(songs.get(0) == first).append('\n');
    answers.add("Make another");
    edt(() -> call("makeFileSetSong", "f:Passing Ships", "Passing Ships"));
    out.append("Make another: ").append(songs.size()).append(" songs\n");
    answers.add("Open it");
    edt(() -> call("makeFileSetSong", "f:Passing Ships", "Passing Ships"));
    out.append("Open it: view ").append(get("view")).append('\n');
    edt(() -> call("addImportedSong", "Loose song", songParts()));
    edt(() -> call("refreshSong"));
    StringBuilder rings = new StringBuilder();
    for (Engine.ImportedSong x : songs) {
      final javax.swing.JButton[] b = {null};
      edt(() -> b[0] = button(frame, x.name));
      rings.append(x.name).append(b[0] != null && b[0].getToolTipText() != null ? " (ring: " + b[0].getToolTipText() + ")" : "").append(" | ");
    }
    out.append("chips: ").append(rings).append('\n');
    edt(() -> call("openFileSetInfo", "f:Passing Ships", "Passing Ships"));
    String gone = songs.get(1).name;
    final boolean[] del = {false};
    edt(() -> del[0] = named(frame, "info-delete:" + gone));
    out.append("info has Delete for ").append(gone).append(": ").append(del[0]).append('\n');
    final Engine.ImportedSong goneSong = songs.get(1);
    answers.add("OK");
    call("confirmDeleteSong", goneSong);
    out.append("after Delete: ").append(songs.size()).append(" songs, view ").append(get("view")).append('\n');
  }

  void s25_song_info_and_style() throws Exception {
    answers.add("Yes");
    call("ingest", Engine.encodeSongMidi(songParts()), "Passing Ships.mid");
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    Engine.ImportedSong song = songs.get(0);
    answers.add("OK");
    edt(() -> call("showSongInfo", song));
    out.append("songs before style: ").append(songs.size()).append('\n');
    answers.add("Change style");
    edt(() -> call("promptChangeStyle", "f:Passing Ships", "Passing Ships", song));
    out.append("Change style (first in the list): ").append(songs.size()).append(" song, same song ")
        .append(songs.contains(song)).append(", set style now ").append(call("fileSetStyleLabel", "f:Passing Ships")).append('\n');
  }

  void s26_cutwav_program() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File wav = new File(home, "take one.wav");
    short[] pcm = new short[22050 * 3];
    for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (8000 * Math.sin(i * 2 * Math.PI * 220 / 22050));
    Files.write(wav.toPath(), AudioIo.encodeWav(pcm, 22050));
    call("showView", "py");
    call("selectListedProgram", "Java", "CutWav.java");
    edt(() -> call("setInputFile", wav));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(((javax.swing.text.JTextComponent) get("pyExtra")).getText() + " --split_time 0:01"));
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (int i = 0; i < 400 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Wrote") || line.startsWith("Succeeded") || line.startsWith("Failed")) out.append(line.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    File cut = new File(home, "take one_cutted.wav");
    AudioIo.Pcm back = cut.isFile() ? AudioIo.parseWav(Files.readAllBytes(cut.toPath())) : null;
    out.append("cut file: ").append(back == null ? "none" : String.format("%.3f s", back.samples.length / (double) back.sr)).append('\n');
  }

  /**
   * Wav2Mp3: one WAV as an MP3 (the length and level back, through the app's decoder, gapless),
   * then a folder of them at once (--dir, --outdir) at another bitrate, in mono.
   */
  void s98_wav2mp3_program() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File wav = new File(home, "take three.wav");
    short[] pcm = new short[44100 * 2];
    for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (8000 * Math.sin(i * 2 * Math.PI * 220 / 44100));
    Files.write(wav.toPath(), AudioIo.encodeWav(pcm, 44100));
    call("showView", "py");
    call("selectListedProgram", "Java", "Wav2Mp3.java");
    edt(() -> call("setInputFile", wav));
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Read") || line.startsWith("Wrote") || line.startsWith("Succeeded") || line.startsWith("Failed")) out.append(line.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    File mp3 = new File(home, "take three.mp3");
    if (!mp3.isFile()) throw new AssertionError("no MP3: " + log.getText());
    AudioIo.Pcm back = Mp3Decode.parse(Files.readAllBytes(mp3.toPath()));
    double peak = 0;
    for (float f : back.samples) peak = Math.max(peak, Math.abs(f));
    out.append(String.format(java.util.Locale.ROOT, "MP3 decoded: %d samples at %d Hz (the WAV: %d), peak %.2f (the WAV: %.2f)%n", back.samples.length, back.sr, pcm.length, peak, 8000 / 32768.0));
    if (back.samples.length != pcm.length || Math.abs(peak - 8000 / 32768.0) > 0.03) throw new AssertionError(out.toString());
    // A folder at once: two WAVs, to another folder, 128 kbps mono.
    File folder = new File(home, "takes");
    folder.mkdirs();
    Files.write(new File(folder, "a.wav").toPath(), AudioIo.encodeWav(pcm, 44100));
    Files.write(new File(folder, "b.wav").toPath(), AudioIo.encodeWav(java.util.Arrays.copyOf(pcm, 22050), 22050));
    File mp3s = new File(home, "mp3s");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--dir " + folder.getAbsolutePath() + " --outdir " + mp3s.getAbsolutePath() + " --bitrate 128 --mode mono"));
    edt(() -> log.setText(""));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Wav2Mp3:") || line.startsWith("Wrote") || line.startsWith("Resampled") || line.startsWith("Succeeded") || line.startsWith("Failed")) out.append(line.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    String[] made = mp3s.list();
    java.util.Arrays.sort(made);
    out.append("in mp3s: ").append(String.join(", ", made)).append('\n');
    if (made.length != 2) throw new AssertionError(out.toString());
  }

  void s27_splitwav_program() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File wav = new File(home, "take two.wav");
    short[] pcm = new short[22050 * 3];
    for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (8000 * Math.sin(i * 2 * Math.PI * 220 / 22050));
    byte[] original = AudioIo.encodeWav(pcm, 22050);
    Files.write(wav.toPath(), original);
    call("showView", "py");
    call("selectListedProgram", "Java", "SplitWav.java");
    edt(() -> call("setInputFile", wav));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(((javax.swing.text.JTextComponent) get("pyExtra")).getText() + " --split_time 0:01"));
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (int i = 0; i < 400 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Split") || line.startsWith("Wrote") || line.startsWith("Succeeded") || line.startsWith("Failed")) out.append(line).append('\n');
    }
    // Three seconds in parts of one: three parts (more than two parts' length gives as many as needed).
    StringBuilder parts = new StringBuilder();
    for (int n = 1; n <= 4; n++) {
      File part = new File(home, "take two_split" + n + ".wav");
      if (!part.isFile()) continue;
      AudioIo.Pcm pp = AudioIo.parseWav(Files.readAllBytes(part.toPath()));
      parts.append(parts.length() == 0 ? "" : " + ").append(String.format("%.3f s", pp.samples.length / (double) pp.sr));
    }
    out.append("parts: ").append(parts.length() == 0 ? "missing" : parts.toString()).append('\n');
  }

  void s28_program_switch_clears_args() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Java", "DrumMidi_CRT.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("<input.wav> " + home.getAbsolutePath() + "/.pulsekit/output.mid"));
    call("selectListedProgram", "Java", "CutWav.java");
    out.append("CutWav args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
    out.append("editor shows CutWav: ").append(((javax.swing.text.JTextComponent) get("pyEditor")).getText().contains("class CutWav")).append('\n');
  }

  void s29_code_save() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Java", "CutWav.java");
    CodeSave save = (CodeSave) get("codeSave");
    String[] items = new String[1];
    edt(() -> {
      javax.swing.JPopupMenu menu = this.fileMenu();
      StringBuilder sb = new StringBuilder();
      for (java.awt.Component c : menu.getComponents()) {
        if (c instanceof javax.swing.JMenuItem) sb.append(sb.length() == 0 ? "" : ", ").append(((javax.swing.JMenuItem) c).getText()).append(((javax.swing.JMenuItem) c).isEnabled() ? "" : " (off)");
      }
      menu.setVisible(false);
      items[0] = sb.toString();
    });
    out.append("File menu: ").append(items[0]).append('\n');
    out.append("editor right click left alone: ").append(((javax.swing.JTextArea) get("pyEditor")).getMouseListeners().length).append(" listeners\n");
    out.append("name: ").append(save.name()).append('\n');
    javax.swing.JTextArea ed = (javax.swing.JTextArea) get("pyEditor");
    File file = new File(home, "MyCutWav.java");
    edt(() -> ed.setText(ed.getText() + "\n// mine"));
    edt(() -> save.saveTo(file));
    out.append("written: ").append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).endsWith("// mine")).append('\n');
    edt(() -> ed.setText(ed.getText() + "\n// again"));
    edt(() -> save.save());
    out.append("Save writes there: ").append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).endsWith("// again")).append('\n');
    call("openCodeFile", "sogni-client.mjs");
    out.append("Code file name: ").append(save.name()).append('\n');
  }

  void s30_scripts_menu() throws Exception {
    call("showView", "py");
    out.append("Scripts: ").append(String.join(", ", ProgramFiles.list("Scripts"))).append('\n');
    edt(() -> call("openScript", "sogni.prompt"));
    javax.swing.JTextArea ed = (javax.swing.JTextArea) get("pyEditor");
    out.append("pyName=").append(get("pyName")).append(" editorHasScript=")
        .append(ed.getText().startsWith("npm install -g @sogni-ai/sogni-creative-agent-skill@latest")).append('\n');
    out.append("button: ").append(button(frame, "Scripts \u00b7 sogni.prompt") != null).append('\n');
    call("selectListedProgram", "Java", "CutWav.java");
    out.append("after Java pick, Scripts button reset: ").append(button(frame, "Scripts \u25be") != null).append('\n');
  }

  /** SogniMusic run through PyJav against a stand-in for Sogni's API on this machine. */
  void s31_sogni_music_program() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final byte[] track = new byte[4096];
    for (int i = 0; i < track.length; i++) track[i] = (byte) i;
    final List<String> seen = Collections.synchronizedList(new ArrayList<String>());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      seen.add(ex.getRequestMethod() + " " + path + " key=" + ex.getRequestHeaders().getFirst("api-key") + (body.isEmpty() ? "" : " " + body));
      byte[] reply;
      String type = "application/json";
      if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        reply = "data: {\"status\":\"running\"}\n\ndata: {\"status\":\"completed\"}\n\n".getBytes(StandardCharsets.UTF_8);
      } else if (path.startsWith("/files/")) {
        type = "audio/mpeg";
        reply = track;
      } else if (path.equals("/v1/creative-agent/workflows/wf7")) {
        reply = ("{\"data\":{\"workflow\":{\"workflowId\":\"wf7\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/take.mp3\",\"mimeType\":\"audio/mpeg\"}]}}}").getBytes(StandardCharsets.UTF_8);
      } else {
        reply = "{\"data\":{\"workflow\":{\"workflowId\":\"wf7\",\"status\":\"queued\"}}}".getBytes(StandardCharsets.UTF_8);
      }
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, reply.length);
      ex.getResponseBody().write(reply);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "sogni key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniMusic.java");
      answers.add("Make drum MIDI");
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--prompt \"funk groove\" --drums_only --duration 10 --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
      edt(() -> call("runPython"));
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Saved") || String.valueOf(log.getText()).contains("Failed") || String.valueOf(log.getText()).contains("Could not")); i++) Thread.sleep(50);
      for (String line : String.valueOf(log.getText()).split("\n")) {
        if (line.startsWith("Saved") || line.startsWith("Status") || line.startsWith("Wrote") || line.startsWith("Succeeded") || line.startsWith("Failed")
            || line.startsWith("Model") || line.startsWith("Import") || line.startsWith("Music") || line.startsWith("Workflow")) out.append(line.replace(home.getAbsolutePath(), "~")).append('\n');
      }
      synchronized (seen) {
        for (String r : seen) out.append("request: ").append(r.replace(String.valueOf(port), "PORT")).append('\n');
      }
      File saved = new File(home, ".pulsekit/sogni-House-wf7.mp3");
      out.append("saved file is the track: ").append(saved.isFile() && java.util.Arrays.equals(Files.readAllBytes(saved.toPath()), track)).append('\n');
      for (SogniHistory.Entry e : SogniHistory.entries()) out.append("history: ").append(e.id).append(" ").append(e.status).append(" ").append(e.prompt.substring(0, 20)).append('\n');
      for (int i = 0; i < 100 && !("DrumMidi_CRT.jar".equals(get("pyName")) && get("pyInputPath") != null); i++) Thread.sleep(50);
      out.append("after Make drum MIDI: ").append(get("pyName")).append(", input ")
          .append(String.valueOf(get("pyInputPath")).replace(home.getAbsolutePath(), "~")).append('\n');
    } finally {
      server.stop(0);
    }
  }

  /** The Style database / genre list search: "rock" lists every rock style. */
  void s32_style_search() throws Exception {
    final String[] names = StyleDb.names();
    final String[][] got = new String[3][];
    edt(() -> {
      SearchList s = new SearchList(names, -1, 0);
      String[] qs = {"rock", "hiphop", ""};
      for (int k = 0; k < qs.length; k++) {
        s.search.setText(qs[k]);
        String[] shown = new String[s.model.size()];
        for (int i = 0; i < shown.length; i++) shown[i] = s.model.get(i);
        got[k] = shown;
      }
      s.search.setText("deep h");
      s.list.setSelectedIndex(0);
      out.append("deep h, first picked: ").append(names[s.selected()]).append('\n');
    });
    out.append("rock: ").append(String.join(" | ", got[0])).append('\n');
    out.append("hiphop: ").append(String.join(" | ", got[1])).append('\n');
    out.append("cleared: ").append(got[2].length).append('\n');
  }

  /** SogniMusic --saveprompt through PyJav with no key: the prompt sheet is saved, and the run fails cleanly. */
  void s33_sogni_save_prompt() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniMusic.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--drums_only --saveprompt --key_file \"" + new File(home, "none.txt").getAbsolutePath() + "\""));
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    // Pulsekit's own "Saved ..." line comes after the program's output, so wait for it.
    for (int i = 0; i < 400 && !(String.valueOf(log.getText()).contains("Saved " + home.getAbsolutePath()) || String.valueOf(log.getText()).contains("Could not save")); i++) Thread.sleep(50);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Saved") || line.startsWith("Failed") || line.startsWith("Could not")) out.append(line.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    File sheet = new File(home, ".pulsekit/sogni-House.prompt");
    String text = sheet.isFile() ? new String(Files.readAllBytes(sheet.toPath()), StandardCharsets.UTF_8) : "";
    PromptRun.Sheet parsed = PromptRun.parse(text);
    out.append("sheet: ").append(parsed == null ? "not a prompt sheet" : "name=" + parsed.name + " category=" + parsed.category
        + " model=" + parsed.model + " type=" + parsed.type).append('\n');
    out.append("body: ").append(parsed == null ? "" : parsed.body).append('\n');
    // The run made no track: the sheet ends with why, as its Result text.
    out.append("result file: ").append(parsed == null ? "" : parsed.result).append('\n');
    out.append("result text: ").append(parsed == null ? "" : parsed.resultText.replace("\n", "|")).append('\n');
    out.append("end of the file: ").append(text.substring(Math.max(0, text.lastIndexOf("\n\nResult"))).trim().replace("\n", "|")).append('\n');
  }

  /** The app's kit switches are added only when the extra args do not give them. */
  void s34_kit_args_once() throws Exception {
    String src = "Usage: java SogniMusic [--genre style] [--bpm N]";
    out.append("none given: ").append(JavaRun.argvFor(src, 124, "house", 4, 0, "--prompt x")).append('\n');
    out.append("genre and bpm given: ").append(JavaRun.argvFor(src, 124, "house", 4, 0, "--genre \"Rock Ballad\" --bpm 120")).append('\n');
  }

  /** Drum Midi Settings' Sogni API key file on the desktop: kept in ~/.pulsekit, passed as --key_file. */
  /** SogniChat: the prompt plus a text file go to Sogni's chat with its tools off; the reply is printed and saved. */
  void s36_sogni_chat() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final List<String> seen = Collections.synchronizedList(new ArrayList<String>());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      seen.add(ex.getRequestMethod() + " " + path.replaceAll("jobId=pulsekit-[0-9a-f-]+", "jobId=JOB") + " key=" + ex.getRequestHeaders().getFirst("api-key")
          + (body.isEmpty() ? "" : path.startsWith("/put") ? " (" + body.length() + " bytes, " + ex.getRequestHeaders().getFirst("Content-Type") + ")"
              : " " + body.replaceAll("pulsekit-[0-9]+-[0-9]+-[0-9a-f]+", "JOB").replaceAll("data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+", "data:image/$1;base64,...")));
      String type = "application/json";
      String reply;
      if (path.equals("/v1/models")) {
        reply = "{\"object\":\"list\",\"data\":[{\"id\":\"qwen3.6-35b-a3b-gguf-iq4xs\"},{\"id\":\"other-llm\"}]}";
      } else if (path.startsWith("/v1/chat/") && body.contains("Draw ten kits")) {
        // Past the Unlimited Plan's fair use limit Sogni refuses the task.
        byte[] no = "{\"error\":{\"message\":\"Daily fair use limit reached\"}}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Retry-After", "5400");
        ex.sendResponseHeaders(429, no.length);
        ex.getResponseBody().write(no);
        ex.close();
        return;
      } else if (path.equals("/v1/chat/runs")) {
        // Unlimited Plan: a durable chat run; Sogni runs the tools on its side.
        reply = "{\"data\":{\"run\":{\"runId\":\"" + (body.contains("Animate it") ? "run2" : "run1") + "\",\"status\":\"queued\"}}}";
      } else if (path.matches("/v1/chat/runs/run[12]/events/stream")) {
        type = "text/event-stream";
        reply = "event: run_status\ndata: {\"status\":\"running\"}\n\n"
            + "data: {\"sequence\":1,\"type\":\"tool_call_dispatched\",\"payload\":{\"toolCallId\":\"t1\",\"toolName\":\"generate_image\"}}\n\n"
            + "data: {\"sequence\":2,\"type\":\"tool_call_progress\",\"payload\":{\"toolCallId\":\"t1\",\"progress\":0.5,\"stepLabel\":\"Rendering\",\"etaSeconds\":40}}\n\n"
            + "data: {\"sequence\":3,\"type\":\"tool_call_resolved\",\"payload\":{\"toolCallId\":\"t1\",\"toolName\":\"generate_image\",\"status\":\"ok\"}}\n\n"
            + "data: {\"sequence\":4,\"type\":\"run_completed\",\"payload\":{}}\n\n";
      } else if (path.equals("/v1/chat/runs/run1")) {
        reply = "{\"data\":{\"run\":{\"runId\":\"run1\",\"status\":\"completed\",\"finalResponse\":{\"content\":\"Making your kit picture now.\"},"
            + "\"artifacts\":[{\"id\":\"a1\",\"url\":\"http://127.0.0.1:" + port + "/files/kit.png\",\"mediaType\":\"image\"}],\"childWorkflowIds\":[]}}}";
      } else if (path.equals("/v1/chat/runs/run2")) {
        // A run whose results are in the workflow it started.
        reply = "{\"data\":{\"run\":{\"runId\":\"run2\",\"status\":\"completed\",\"request\":{\"model\":\"deepseek-v4-flash-vision-exp-dspark-1m\","
            + "\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"Animate it\\n\\nPicture wide.jpg is attached as media_ref_1.\"}]}]},"
            + "\"messages\":[{\"role\":\"assistant\",\"content\":\"Animating it now.\"}],"
            + "\"artifacts\":[],\"childWorkflowIds\":[\"wf9\"]}}}";
      } else if (path.equals("/v1/chat/completions") && body.contains("\"sogni_tool_execution\":true")) {
        // Unlimited Plan: Sogni runs the tool in the chat and names the workflow it started.
        reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Making your kit picture now.\"}}],"
            + "\"creative_workflows\":[{\"workflowId\":\"wf9\",\"status\":\"queued\"}]}";
      } else if (path.equals("/v1/chat/completions") && body.contains("\"sogni_tools\":\"creative-tools\"")) {
        // With the tools offered, the model proposes a call instead of answering in text.
        reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Here is a kit picture.\",\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"generate_image\",\"arguments\":\"{\\\"prompt\\\":\\\"a red drum kit\\\"}\"}}]}}]}";
      } else if (path.equals("/v1/chat/completions")) {
        reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"<think>a fill</think>\\nTry a snare roll into the crash.\"}}],"
            + "\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":7}}";
      } else if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        reply = "data: {\"status\":\"running\"}\n\ndata: {\"status\":\"completed\"}\n\n";
      } else if (path.equals("/v1/creative-agent/workflows/wf9")) {
        reply = "{\"data\":{\"workflow\":{\"workflowId\":\"wf9\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/kit.png\",\"mimeType\":\"image/png\"}]}}}";
      } else if (path.startsWith("/v1/image/uploadUrl") || path.startsWith("/v1/media/uploadUrl")) {
        // Uploads for Sogni's tools: a signed URL to PUT the file to, then where it can be read.
        reply = "{\"data\":{\"uploadUrl\":\"http://127.0.0.1:" + port + "/put" + path.substring(path.indexOf('?')) + "\"}}";
      } else if (path.startsWith("/v1/image/downloadUrl") || path.startsWith("/v1/media/downloadUrl")) {
        String q = path.substring(path.indexOf("id=") + 3);
        reply = "{\"data\":{\"downloadUrl\":\"https://store.example/" + q.replaceAll("&.*", "") + "\"}}";
      } else if (path.startsWith("/put")) {
        reply = "";
      } else if (path.startsWith("/files/")) {
        // A real 2x2 picture, so the picture dialog can show it.
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        byte[] picture = png.toByteArray();
        ex.getResponseHeaders().set("Content-Type", "image/png");
        ex.sendResponseHeaders(200, picture.length);
        ex.getResponseBody().write(picture);
        ex.close();
        return;
      } else {
        reply = "{\"data\":{\"workflow\":{\"workflowId\":\"wf9\",\"status\":\"queued\"}}}";
      }
      byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "sogni key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      File notes = new File(home, "notes.txt");
      Files.write(notes.toPath(), "Kick on 1 and 3, snare on 2 and 4.\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniChat.java");
      out.append("hint: ").append(((javax.swing.JLabel) get("pyHint")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      // A MIDI file (a kick and a hat on the drum channel, C4 on channel 1), a picture, and a file of neither kind.
      File song = new File(home, "groove.mid");
      Files.write(song.toPath(), new byte[] {
        'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 1, (byte) 0xE0,
        'M', 'T', 'r', 'k', 0, 0, 0, 44,
        0, (byte) 0xFF, 0x51, 3, 0x07, (byte) 0xA1, 0x20,
        0, (byte) 0xFF, 0x58, 4, 4, 2, 24, 8,
        0, (byte) 0x99, 36, 100, 0, 42, 90,
        (byte) 0x83, 0x60, (byte) 0x89, 36, 0, 0, 42, 0,
        0, (byte) 0x90, 60, 80,
        (byte) 0x87, 0x40, (byte) 0x80, 60, 0,
        0, (byte) 0xFF, 0x2F, 0});
      File picture = new File(home, "kit.png");
      Files.write(picture.toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3});
      File junk = new File(home, "data.bin");
      Files.write(junk.toPath(), new byte[] {1, 0, 2, 0, 3});
      File track = new File(home, "song.mp3");
      Files.write(track.toPath(), new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4});
      File webp = new File(home, "photo.webp");
      Files.write(webp.toPath(), new byte[] {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '});
      // Pictures bigger than the 1024 px Sogni shows the chat model: SogniChat sends a PNG as a smaller
      // copy; the app makes one of a JPEG (--seen_copy), turned upright by its EXIF orientation.
      File tall = new File(home, "tall.png");
      javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(832, 1248, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", tall);
      File wide = new File(home, "wide.jpg");
      javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1300, 700, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", wide);
      // A camera photo stored on its side (EXIF orientation 6: turn 90 degrees to view).
      byte[] stored = Files.readAllBytes(wide.toPath());
      byte[] exif = {(byte) 0xFF, (byte) 0xE1, 0, 34, 'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8,
        0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0};
      byte[] photo = new byte[stored.length + exif.length];
      System.arraycopy(stored, 0, photo, 0, 2);
      System.arraycopy(exif, 0, photo, 2, exif.length);
      System.arraycopy(stored, 2, photo, 2 + exif.length, stored.length - 2);
      File sideways = new File(home, "sideways.jpg");
      Files.write(sideways.toPath(), photo);
      File old = new File(home, "old reply.txt");
      Files.write(old.toPath(), "Play the hats softer.\n".getBytes(StandardCharsets.UTF_8));
      String[] runs = {
        "--prompt \"Suggest one fill\" --system \"Answer briefly.\" --file \"" + notes.getAbsolutePath() + "\"",
        // The saved conversation goes on: its system text and earlier turns are sent again.
        "--continue \"" + new File(home, ".pulsekit/sogni-chat-suggest-one-fill.txt").getAbsolutePath() + "\" --prompt \"And a longer one?\"",
        "--continue \"" + old.getAbsolutePath() + "\" --prompt \"Why?\"",
        "--continue \"" + old.getAbsolutePath() + "\"",
        "--prompt \"What does this groove play?\" --file \"" + song.getAbsolutePath() + "\" --file \"" + picture.getAbsolutePath() + "\"",
        "--prompt Hi --file \"" + junk.getAbsolutePath() + "\"",
        // Tools offered: the proposed call is shown and kept, not run; then run under a cost limit.
        "--prompt \"Draw a drum kit\" --tools",
        "--prompt \"Draw a drum kit\" --run_tools --max_cost 5 --confirm_cost",
        // An output name names the conversation and the results; a given extension is dropped.
        "kit-ideas.png --prompt \"Draw a drum kit\" --unlimited",
        "--prompt \"Draw a drum kit\" --unlimited",
        "--prompt \"Draw ten kits\" --unlimited",
        // Files for the tools: uploaded, then named in the request as media references.
        "--prompt \"Make a video for this song with this kit\" --file \"" + track.getAbsolutePath() + "\" --file \"" + picture.getAbsolutePath() + "\" --unlimited",
        "--prompt \"What is this?\" --file \"" + track.getAbsolutePath() + "\"",
        "--prompt \"Restyle it\" --file \"" + webp.getAbsolutePath() + "\" --tools",
        "--prompt \"Describe it\" --file \"" + tall.getAbsolutePath() + "\"",
        "--prompt \"Describe it\" --file \"" + wide.getAbsolutePath() + "\"",
        "--prompt \"Animate it\" --file \"" + wide.getAbsolutePath() + "\" --unlimited",
        "--prompt \"Describe it\" --file \"" + sideways.getAbsolutePath() + "\"",
        "--prompt \"Filter on\" --filter_on",
        // A chat run started earlier (one that timed out here) is followed again by its id.
        "--run run2",
        "--models",
        "--max_tokens lots",
      };
      for (String extra : runs) {
        edt(() -> log.setText(""));
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        // A run that makes a picture shows it; Close the dialog.
        if (extra.contains("--run") || extra.contains("--unlimited")) answers.add("Close");
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(300);
        out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
        for (String line : String.valueOf(log.getText()).split("\n")) {
          if (line.startsWith("$ ") || line.startsWith("Picked up") || line.trim().isEmpty()) continue;
          out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
        }
      }
      synchronized (seen) {
        for (String r : seen) out.append("request: ").append(r.replace(String.valueOf(port), "PORT").replace(home.getAbsolutePath(), "~")).append('\n');
        // The filter is off for every chat request and run but the --filter_on one.
        int off = 0, on = 0;
        for (String r : seen) {
          if (!r.startsWith("POST /v1/chat/")) continue;
          if (r.contains("\"safe_content_filter\":false") || r.contains("\"safeContentFilter\":false")) off++;
          else on++;
        }
        out.append("chat requests with the filter off: ").append(off).append(", on: ").append(on).append('\n');
      }
      try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(home.toPath())) {
        for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files.filter(x -> x.getFileName().toString().startsWith("sogni-chat")).sorted()::iterator) {
          String shown = f.toString().endsWith(".txt") ? new String(Files.readAllBytes(f), StandardCharsets.UTF_8).trim() : Files.size(f) + " bytes";
          out.append("saved ").append(home.toPath().relativize(f)).append(": ").append(shown).append('\n');
        }
      }
    } finally {
      server.stop(0);
    }
  }

  /** Picking Ballad 6/8 or Slow Blues 12/8 sets the time signature; a 4/4 style after it sets 4/4 back, a hand-set one stays. */
  void s39_style_meter() throws Exception {
    StyleLibrary lib = (StyleLibrary) get("styleLibrary");
    for (String id : new String[] {"ballad68", "rock", "blues128", "hardrock", "slipjig98", "folk"}) {
      edt(() -> lib.loadStyle(id, false));
      int steps = (Integer) get("steps");
      int[][] cells = (int[][]) get("cells");
      StringBuilder kick = new StringBuilder();
      for (int i = 0; i < steps; i++) kick.append(cells[Engine.track("kick")][i] > 0 ? 'X' : '-');
      out.append(id).append(": ").append(get("tsNum")).append('/').append(get("tsDen")).append(", ").append(steps)
          .append(" steps, kick ").append(kick).append('\n');
      int[][] fill = (int[][]) get("fillPat");
      StringBuilder toms = new StringBuilder();
      for (int i = 0; i < steps; i++) {
        boolean tom = fill[Engine.track("htom")][i] > 0 || fill[Engine.track("mtom")][i] > 0 || fill[Engine.track("ltom")][i] > 0;
        toms.append(tom ? 'T' : '-');
      }
      out.append("  fill ").append(get("fillId")).append(" toms ").append(toms).append('\n');
    }
    edt(() -> {
      JTextField num = (JTextField) get("tsNumField");
      num.setText("3");
      num.postActionEvent();
    });
    edt(() -> lib.loadStyle("rock", false));
    out.append("hand-set 3/4, then rock: ").append(get("tsNum")).append('/').append(get("tsDen")).append(", ").append(get("steps")).append(" steps\n");
  }

  /** SogniVideo: a picture animated with MiniMax H3 FastH3 as a Sogni workflow, against a stand-in Sogni server. */
  void s40_sogni_video() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final List<String> seen = Collections.synchronizedList(new ArrayList<String>());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      seen.add(ex.getRequestMethod() + " " + path.replaceAll("jobId=pulsekit-[0-9a-f-]+", "jobId=JOB")
          + (body.isEmpty() ? "" : path.startsWith("/put") ? " (" + body.length() + " bytes, " + ex.getRequestHeaders().getFirst("Content-Type") + ")"
              : " " + body.replaceAll("pulsekit-[0-9]+-[0-9]+-[0-9a-f]+", "JOB")));
      String type = "application/json";
      String reply;
      int code = 200;
      if (path.equals("/v1/creative-agent/workflows")) {
        reply = body.contains("Too much") ? "{\"message\":\"Daily fair use limit reached\"}" : "{\"data\":{\"workflow\":{\"workflowId\":\"wv1\",\"status\":\"queued\"}}}";
        if (body.contains("Too much")) code = 429;
      } else if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        reply = "data: {\"status\":\"running\"}\n\ndata: {\"status\":\"completed\"}\n\n";
      } else if (path.equals("/v1/creative-agent/workflows/wv1")) {
        // The uploaded picture is in the record's input too; the clip is the artifact.
        reply = "{\"data\":{\"workflow\":{\"workflowId\":\"wv1\",\"status\":\"completed\","
            + "\"input\":{\"mediaReferences\":[{\"url\":\"https://store.example/garden.png\",\"mime_type\":\"image/png\"}]},"
            + "\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port + "/files/clip.mp4\",\"mediaType\":\"video\"}]}}}";
      } else if (path.startsWith("/v1/image/uploadUrl")) {
        reply = "{\"data\":{\"uploadUrl\":\"http://127.0.0.1:" + port + "/put" + path.substring(path.indexOf('?')) + "\"}}";
      } else if (path.startsWith("/v1/image/downloadUrl")) {
        String q = path.substring(path.indexOf("imageId=") + 8);
        reply = "{\"data\":{\"downloadUrl\":\"https://store.example/" + q.replaceAll("&.*", "") + "\"}}";
      } else if (path.startsWith("/put")) {
        reply = "";
      } else if (path.equals("/files/clip.mp4")) {
        byte[] clip = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2', 'i', 's', 'o', 'm'};
        ex.getResponseHeaders().set("Content-Type", "video/mp4");
        ex.sendResponseHeaders(200, clip.length);
        ex.getResponseBody().write(clip);
        ex.close();
        return;
      } else {
        reply = "{}";
      }
      byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(code, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "sogni key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      File garden = new File(home, "garden.png");
      javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(832, 1248, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", garden);
      File last = new File(home, "gate.jpg");
      javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(832, 1248, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", last);
      File notes = new File(home, "notes.txt");
      Files.write(notes.toPath(), "not a picture\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniVideo.java");
      out.append("hint: ").append(((javax.swing.JLabel) get("pyHint")).getText().replaceAll("<[^>]+>", "|").replace(home.getAbsolutePath(), "~")).append('\n');
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)")
            .append(p.ext != null ? " file " + p.ext : "").append(p.choices != null ? " choices " + java.util.Arrays.asList(p.choiceValues) : "")
            .append(p.hint.length() > 0 ? " hint: " + p.hint : "").append('\n');
      }
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      String[] runs = {
        "--prompt \"She walks slowly through the garden, the camera follows\" --image \"" + garden.getAbsolutePath() + "\" --duration 5 --saveprompt",
        "walk.mp4 --prompt \"The gate swings open\" --image \"" + garden.getAbsolutePath() + "\" --end_image \"" + last.getAbsolutePath()
            + "\" --resolution 1080p --no_audio --exact_prompt --unlimited --saveprompt",
        "--prompt \"Rain on a tin roof at night, slow push-in\" --aspect 16:9 --max_cost 50 --confirm_cost",
        "--prompt \"Too much\" --unlimited --saveprompt",
        "--workflow wv1",
        "--prompt Walk --image \"" + notes.getAbsolutePath() + "\"",
        "--prompt Walk --end_image \"" + last.getAbsolutePath() + "\"",
        "--prompt Walk --duration 20",
        "--prompt Walk --resolution 4k",
        "--image \"" + garden.getAbsolutePath() + "\"",
      };
      // The runs that make a clip show the Video ready dialog; Close it.
      java.util.Set<Integer> clips = new java.util.HashSet<Integer>(java.util.Arrays.asList(0, 1, 2, 4));
      for (int r = 0; r < runs.length; r++) {
        final String extra = runs[r];
        if (clips.contains(r)) answers.add("Close");
        edt(() -> log.setText(""));
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(300);
        out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
        for (String line : String.valueOf(log.getText()).split("\n")) {
          if (line.startsWith("$ ") || line.startsWith("Picked up") || line.trim().isEmpty()) continue;
          out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
        }
      }
      // Play opens a player page with the phone's controls (Java plays no video itself).
      File clip = new File(home, ".pulsekit/walk.mp4");
      File page = (File) call("videoPage", clip);
      String html = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8);
      out.append("player page ").append(page.getName()).append(": video src ").append(html.contains("src=\"" + clip.toURI()) ? "the saved clip" : "?");
      for (String id : new String[] {"play", "stop", "mute", "volume", "level"}) out.append(", ").append(id).append(html.contains("id=\"" + id + "\"") ? " yes" : " NO");
      out.append('\n');
      synchronized (seen) {
        for (String r : seen) out.append("request: ").append(r.replace(String.valueOf(port), "PORT").replace(home.getAbsolutePath(), "~")).append('\n');
      }
      try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(home.toPath())) {
        for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files.filter(x -> x.getFileName().toString().matches("(sogni-video|walk).*")).sorted()::iterator) {
          out.append("saved ").append(home.toPath().relativize(f)).append(": ").append(Files.size(f)).append(" bytes\n");
          if (f.toString().endsWith(".prompt")) {
            // The sheet opens in PyJav and the Prompts page: read it as they do.
            PromptRun.Sheet sheet = PromptRun.parse(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
            out.append("  sheet: ").append(sheet.name).append(", category ").append(sheet.category).append(", model ").append(sheet.model)
                .append(", type ").append(sheet.type).append("\n  body: ").append(sheet.body.replace("\n", "|")).append('\n');
            out.append("  reference files: 1 \"").append(sheet.ref1).append("\", 2 \"").append(sheet.ref2).append("\"\n");
            out.append("  result file: ").append(sheet.result).append("\n  result text: ").append(sheet.resultText.replace("\n", "|")).append('\n');
            String raw = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            out.append("  end of the file: ").append(raw.substring(Math.max(0, raw.lastIndexOf("\n\nResult"))).trim().replace("\n", "|")).append('\n');
          }
        }
      }
    } finally {
      server.stop(0);
    }
  }

  /** MidiDrumGen in the Java menu: its switches come from its Usage line, and a run's MIDI is imported. */
  void s38_midi_drum_gen() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    out.append("hint: ").append(((javax.swing.JLabel) get("pyHint")).getText().replaceAll("<[^>]+>", "|")).append('\n');
    List<ProgramParams.Param> ps = ProgramParams.parse((String) call("programText"));
    for (ProgramParams.Param p : ps) {
      out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)")
          .append(p.choices != null ? " choices " + p.choices.length : "").append('\n');
    }
    answers.add("Yes");
    // The MIDI ready dialog that follows (the groove with the kit's sounds): Close.
    answers.add("Close");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style \"Hard Rock\" --bars 4"));
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Wrote") || String.valueOf(log.getText()).contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Wrote") || line.startsWith("Import") || line.startsWith("$ java") || line.contains("rror")) out.append("log: ").append(line).append('\n');
    }
    // A tempo outside the style's range in the style database is moved into it.
    answers.add("Yes");
    // The MIDI ready dialog that follows (the groove with the kit's sounds): Close.
    answers.add("Close");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style Techno --tempo 90 --bars 4"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Wrote") || String.valueOf(log.getText()).contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Tempo") || line.startsWith("Wrote")) out.append("log: ").append(line).append('\n');
    }
    // The app's time signature (here 3/4) goes to MidiDrumGen as --timesig; 4/4 is not passed.
    out.append("argv 6/8: ").append(JavaRun.argvFor("[--timesig 3/4]", 120, "house", 4, 0, "", 6, 8)).append('\n');
    out.append("argv 4/4: ").append(JavaRun.argvFor("[--timesig 3/4]", 120, "house", 4, 0, "", 4, 4)).append('\n');
    out.append("argv own: ").append(JavaRun.argvFor("[--timesig 3/4]", 120, "house", 4, 0, "--timesig 7/8", 6, 8)).append('\n');
    set("tsNum", 3);
    answers.add("Yes");
    // The MIDI ready dialog that follows (the groove with the kit's sounds): Close.
    answers.add("Close");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style Rock --bars 4"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Wrote") || String.valueOf(log.getText()).contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("$ java") || line.startsWith("Wrote")) out.append("log: ").append(line).append('\n');
    }
    set("tsNum", 4);
    // A style in another meter in the style database plays in it.
    answers.add("Yes");
    // The MIDI ready dialog that follows (the groove with the kit's sounds): Close.
    answers.add("Close");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style \"Ballad 6/8\" --bars 4"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Wrote") || String.valueOf(log.getText()).contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("Tempo") || line.startsWith("Wrote")) out.append("log: ").append(line).append('\n');
    }
  }

  /** Program files folder: a chosen folder gets the files programs make, kept across starts; "Use Downloads" goes back. */
  void s37_program_folder() throws Exception {
    File home = new File(System.getProperty("user.home"));
    DrumMidiSettingsPage page = (DrumMidiSettingsPage) get("drumMidiSettings");
    out.append("before: ").append(page.folderStatus.getText()).append('\n');
    File chosen = new File(home, "Sogni results");
    edt(() -> page.takeFolder(chosen));
    out.append("after: ").append(page.folderStatus.getText().replace(home.getAbsolutePath(), "~")).append('\n');
    File saved = (File) call("saveProgramFile", "kit-ideas-1.png", "PNG".getBytes(StandardCharsets.UTF_8));
    File again = (File) call("saveProgramFile", "kit-ideas-1.png", "PNG".getBytes(StandardCharsets.UTF_8));
    out.append("saved: ").append(saved.getAbsolutePath().replace(home.getAbsolutePath(), "~")).append(", then ")
        .append(again.getName()).append('\n');
    ProgramFolder.init(new File(home, ".pulsekit"));
    out.append("kept: ").append(ProgramFolder.label().replace(home.getAbsolutePath(), "~")).append('\n');
    edt(() -> ((javax.swing.JButton) component(frame, "program-folder-clear")).doClick());
    out.append("cleared: ").append(page.folderStatus.getText()).append('\n');
    File plain = (File) call("saveProgramFile", "reply.txt", "hi".getBytes(StandardCharsets.UTF_8));
    out.append("then saved: ").append(plain.getAbsolutePath().replace(home.getAbsolutePath(), "~")).append('\n');
  }

  void s35_sogni_key_setting() throws Exception {
    File home = new File(System.getProperty("user.home"));
    DrumMidiSettingsPage page = (DrumMidiSettingsPage) get("drumMidiSettings");
    out.append("before: ").append(page.keyStatus.getText()).append('\n');
    File key = new File(home, "creds.txt");
    Files.write(key.toPath(), "abcd1234efgh5678\n".getBytes(StandardCharsets.UTF_8));
    edt(() -> page.takeKey(key));
    out.append("after: ").append(page.keyStatus.getText()).append('\n');
    out.append("path: ").append(ApiKeys.path().replace(home.getAbsolutePath(), "~")).append('\n');
    out.append("argv: ").append(String.join(" ", JavaRun.argvFor("[--key_file credentials.txt]", 120, "house", 4, 0, "")).replace(home.getAbsolutePath(), "~")).append('\n');
    out.append("own --key_file wins: ").append(JavaRun.argvFor("[--key_file credentials.txt]", 120, "house", 4, 0, "--key_file other.txt")).append('\n');
  }

  /**
   * The prompt library on the desktop: its key file (owner only), File > Import as Ref / Result
   * file, the Ref files and Result files galleries, rename and delete, Browse DB's copies (a MIDI
   * played with the kit to a WAV), Compare Hits' Browse DB, and MidiDrumGen's MIDI and sheet
   * stored after a run (Drum Midi Settings: Save MidiDrumGen output file into DB, --saveprompt).
   */
  void s42_prompt_library() throws Exception {
    File home = new File(System.getProperty("user.home"));
    PromptDb db = (PromptDb) get("promptDb");
    String[] items = new String[1];
    edt(() -> {
      javax.swing.JPopupMenu menu = this.fileMenu();
      StringBuilder sb = new StringBuilder();
      for (java.awt.Component c : menu.getComponents()) {
        if (c instanceof javax.swing.JMenuItem) sb.append(sb.length() == 0 ? "" : ", ").append(((javax.swing.JMenuItem) c).getText());
      }
      menu.setVisible(false);
      items[0] = sb.toString();
    });
    out.append("File menu: ").append(items[0]).append('\n');
    // A WAV and a MIDI to import.
    short[] tone = new short[22050 / 4];
    for (int i = 0; i < tone.length; i++) tone[i] = (short) (8000 * Math.sin(i * 0.05));
    File wav = new File(home, "hit.wav");
    Files.write(wav.toPath(), AudioIo.encodeWav(tone, 22050));
    File mid = new File(home, "beat.mid");
    Files.write(mid.toPath(), Engine.encodeMidi(Engine.styleCells(Engine.styles().get("rock")), 110));
    File big = new File(home, "big.bin");
    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(big, "rw")) {
      raf.setLength(17L * 1024 * 1024);
    }
    answers.add("OK");
    edt(() -> db.importPicked(wav, false));
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    answers.add("Cancel");
    edt(() -> db.importPicked(mid, false));
    answers.add("OK");
    edt(() -> db.importPicked(mid, true));
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    edt(() -> db.importPicked(big, false));
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    File key = new File(home, ".pulsekit/prompts.key");
    out.append("key: ").append(key.length()).append(" bytes, ")
        .append(java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(key.toPath()))).append('\n');
    out.append("vault: ").append(new File(home, ".pulsekit/prompts.vault").isFile()).append('\n');
    for (boolean results : new boolean[] {false, true}) {
      for (PromptVault.StoredFile f : PromptDb.files(results, null)) {
        out.append(results ? "result: " : "ref: ").append(f.name).append(" (").append(f.promptTitle).append(", ").append(f.size).append(" bytes)\n");
      }
    }
    out.append("sounds: ").append(PromptDb.allFiles(PromptDb.SOUNDS).size()).append(", midis: ").append(PromptDb.allFiles(PromptDb.MIDIS).size())
        .append(", both: ").append(PromptDb.allFiles(PromptDb.SOUNDS_OR_MIDIS).size()).append('\n');
    // Read back with a fresh key object (as after a restart).
    PromptVault.keys = new DesktopVaultKey(new File(home, ".pulsekit"));
    out.append("after restart: ").append(PromptDb.allFiles(null).size()).append(" files\n");
    // The galleries: one card per stored file; the other kind's count at the top.
    call("showView", "prompts");
    answers.add("Close");
    edt(() -> ((javax.swing.JButton) component(frame, "prompts-ref-files")).doClick());
    JDialog gallery = (JDialog) get("lastGallery");
    out.append("gallery \"").append(gallery.getTitle()).append("\": ");
    for (String n : new String[] {"gallery-tab:0", "gallery-tab:1", "gallery-tab:2"}) {
      javax.swing.JButton b = (javax.swing.JButton) component(gallery, n);
      out.append('[').append(b.getText()).append(b.isEnabled() ? "" : " (shown)").append("] ");
    }
    out.append(named(gallery, "gallery:hit.wav")).append('\n');
    // Browse DB: the copy in PyJav's input folder; a MIDI where a sound is wanted becomes its kit WAV.
    PromptVault.StoredFile beat = PromptDb.files(true, PromptDb.MIDIS).get(0);
    File copy = db.copyOut(beat);
    out.append("copy: ").append(copy.getAbsolutePath().replace(home.getAbsolutePath(), "~")).append(", ").append(copy.length()).append(" bytes\n");
    String[] values = new String[] {""};
    JLabel label = new JLabel();
    edt(() -> db.useAsAudio(beat.name, copy, values, 0, label));
    out.append("as audio: ").append(label.getText()).append(" -> ").append(new File(values[0]).getName())
        .append(AudioIo.parseWav(Files.readAllBytes(new File(values[0]).toPath())) != null ? " (a WAV)" : "").append('\n');
    CompareHitsPage compare = (CompareHitsPage) get("compareHits");
    call("showView", "comparehits");
    out.append("compare Browse DB: ").append(compare.browseDb.isEnabled() ? "on" : "off").append('\n');
    edt(() -> compare.takeDbWav(beat.name, copy));
    out.append("compare: ").append(compare.wavLabel.getText()).append('\n');
    // Rename and delete from the gallery's menu.
    PromptVault.StoredFile hit = PromptDb.files(false, null).get(0);
    edt(() -> db.rename(hit, "kick one.wav"));
    out.append("renamed: ").append(PromptDb.files(false, null).get(0).name).append('\n');
    edt(() -> db.delete(PromptDb.files(false, null).get(0)));
    out.append("after delete: ").append(PromptDb.files(false, null).size()).append(" ref files\n");
    // Drum Midi Settings: Save MidiDrumGen output file into DB, kept in its own file.
    call("showView", "midisettings");
    javax.swing.JCheckBox gen = (javax.swing.JCheckBox) component(frame, "midi-drum-gen-db");
    javax.swing.JCheckBox music = (javax.swing.JCheckBox) component(frame, "sogni-music-db");
    out.append("checks: ").append(gen.getText()).append(" ").append(gen.isSelected()).append(", ").append(music.getText()).append(" ").append(music.isSelected()).append('\n');
    edt(gen::doClick);
    out.append("db settings: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/db-settings.txt").toPath()), StandardCharsets.UTF_8).replace("\n", " ")).append('\n');
    call("showView", "py");
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (String extra : new String[] {"--style Rock --bars 4", "--style Rock --bars 4 --saveprompt"}) {
      answers.add("Yes");
      answers.add("Close");
      edt(() -> log.setText(""));
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
      edt(() -> call("runPython"));
      for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Prompt library") && !String.valueOf(log.getText()).contains("rror"); i++) Thread.sleep(50);
      Thread.sleep(500);
      for (String line : String.valueOf(log.getText()).split("\n")) if (line.startsWith("Prompt library") || line.contains("rror")) out.append(extra).append(": ").append(line).append('\n');
    }
    PromptVault vault = PromptDb.vault();
    for (PromptVault.Category c : vault.categories()) {
      for (PromptVault.Prompt p : vault.prompts(c.id)) {
        out.append("prompt: ").append(c.name).append(" / ").append(p.title).append(", ").append(vault.versions(p.id).size()).append(" versions\n");
      }
    }
  }

  /**
   * The Prompts page: the old single sheet moved into the library, categories and subcategories,
   * a prompt's editor (type chips under Code, files from disk and from the DB, preview, rename and
   * delete), versions (save, load, mark final), result text, Export .prompt and Open in PyJav
   * with its reference file.
   */
  void s43_prompts_page() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File dir = new File(home, ".pulsekit");
    PromptsPage page = (PromptsPage) get("promptsPage");
    PromptDb db = (PromptDb) get("promptDb");
    // The earlier desktop page's sheet and its file move in once.
    Files.write(new File(dir, "prompts.txt").toPath(), PromptRun.encode("Old idea", "", "", "pic.png", "", "Draw a drum kit").getBytes(StandardCharsets.UTF_8));
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", png);
    Files.write(new File(dir, "prompt-ref-1.png").toPath(), png.toByteArray());
    call("showView", "prompts");
    out.append("old sheet: ").append(new File(dir, "prompts.txt").isFile() ? "still there" : "moved").append(", ")
        .append(new File(dir, "prompts.txt.moved").isFile()).append('\n');
    PromptVault vault = PromptDb.vault();
    StringBuilder cats = new StringBuilder();
    for (PromptVault.Category c : vault.mains()) cats.append(c.name).append(' ');
    out.append("categories: ").append(cats.toString().trim()).append('\n');
    out.append("chips: ").append(named(frame, "category:General")).append(", prompt shown: ").append(named(frame, "prompt:Old idea")).append('\n');
    edt(() -> ((javax.swing.JButton) component(frame, "prompt:Old idea")).doClick());
    out.append("editor: title=").append(page.promptTitle.getText()).append(", body=").append(page.promptBody.getText())
        .append(", ref1=").append(page.promptRef1.getText()).append(", ai box=").append(named(frame, "prompt-ai")).append('\n');
    // A subcategory of Code: its prompts have a type.
    long code = 0;
    for (PromptVault.Category c : vault.mains()) if ("Code".equals(c.name)) code = c.id;
    long py = vault.addSubcategory(code, "python");
    long id = vault.addPrompt(py, "Make hats");
    edt(() -> page.openPrompt(id));
    out.append("type chips: ").append(named(frame, "prompt-type:python")).append(", type ").append(page.codeType).append('\n');
    edt(() -> {
      page.promptDescription.setText("Writes a hats MIDI");
      page.promptBody.setText("print('hats')");
      page.promptModel.setText("none");
      ((javax.swing.JButton) component(frame, "prompt-type:java")).doClick();
      ((javax.swing.JButton) component(frame, "prompt-save")).doClick();
    });
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    // A file from disk goes in at once; the DB button offers what is stored.
    short[] tone = new short[2205];
    File wav = new File(home, "hat.wav");
    Files.write(wav.toPath(), AudioIo.encodeWav(tone, 22050));
    edt(() -> page.takeFile(1, wav));
    out.append("ref1: ").append(page.promptRef1.getText()).append('\n');
    PromptVault.StoredFile pic = null;
    for (PromptVault.StoredFile f : PromptDb.stored(false)) if ("pic.png".equals(f.name)) pic = f;
    final PromptVault.StoredFile picked = pic;
    edt(() -> page.rebuild());
    out.append("DB button: ").append(named(frame, "prompt-db:2")).append('\n');
    edt(() -> page.useStored(picked, 2));
    out.append("ref2: ").append(page.promptRef2.getText()).append('\n');
    // Preview: a picture with zoom.
    answers.add("Close");
    edt(() -> ((javax.swing.JButton) component(frame, "prompt-preview:2")).doClick());
    out.append("preview: ").append(((JDialog) get("lastPreview")).getTitle()).append('\n');
    // Rename the second file, then delete it.
    answers.add("OK");
    edt(() -> {
      javax.swing.JPopupMenu m = page.slotMenu(2);
      ((javax.swing.JMenuItem) m.getComponent(0)).doClick();
    });
    out.append("ref2 after rename (kept name): ").append(page.promptRef2.getText()).append('\n');
    answers.add("Delete");
    edt(() -> ((javax.swing.JMenuItem) page.slotMenu(2).getComponent(1)).doClick());
    out.append("ref2 after delete: ").append(page.promptRef2.getText()).append('\n');
    // Versions: save another (the first stays final), mark the new one final, load the first.
    edt(() -> {
      page.promptBody.setText("print('hats v2')");
      ((javax.swing.JButton) component(frame, "prompt-save")).doClick();
    });
    out.append("versions: ").append(vault.versions(id).size()).append(", buttons ").append(named(frame, "version:v1")).append(" v1 mark ").append(named(frame, "version-final:v1"))
        .append(", v2 mark ").append(named(frame, "version-final:v2")).append('\n');
    out.append("final: ").append(vault.finalVersion(id).body).append('\n');
    edt(() -> ((javax.swing.JButton) component(frame, "version-final:v2")).doClick());
    out.append("final: ").append(vault.finalVersion(id).body).append('\n');
    edt(() -> ((javax.swing.JButton) component(frame, "version:v1")).doClick());
    out.append("loaded v1: ").append(page.promptBody.getText()).append(", type ").append(page.codeType).append('\n');
    // Result text.
    edt(() -> ((javax.swing.JButton) component(frame, "prompt-result-text")).doClick());
    edt(() -> {
      page.resultTextArea.setText("hats.mid made");
      ((javax.swing.JButton) component(frame, "result-text-save")).doClick();
      ((javax.swing.JButton) component(frame, "result-text-back")).doClick();
    });
    out.append("result text: ").append(vault.version(page.loadedVersionId).resultText).append(", button ")
        .append(((javax.swing.JButton) component(frame, "prompt-result-text")).getText()).append('\n');
    // Export and Open in PyJav.
    File exported = new File(home, "Make hats");
    edt(() -> page.exportTo(exported));
    String sheet = new String(Files.readAllBytes(new File(home, "Make hats.prompt").toPath()), StandardCharsets.UTF_8);
    for (String line : sheet.split("\n")) if (!line.startsWith("Version:")) out.append("sheet: ").append(line).append('\n');
    edt(() -> page.openPrompt(vault.prompts(vault.mains().get(0).id).get(0).id));
    edt(() -> ((javax.swing.JButton) component(frame, "prompt-open-pyjav")).doClick());
    out.append("pyjav: ").append(get("pyName")).append(", input ").append(new File((String) get("pyInputPath")).getName()).append('\n');
    out.append("log: ").append(((javax.swing.JTextArea) get("pyLog")).getText().replace(home.getAbsolutePath(), "~").replace("\n", " | ")).append('\n');
    // A category is renamed and deleted from its menu.
    call("showView", "prompts");
    edt(() -> ((javax.swing.JButton) component(frame, "prompts-back")).doClick());
    answers.add("Delete");
    edt(() -> ((javax.swing.JMenuItem) page.categoryMenu(py, "python", true).getComponent(1)).doClick());
    out.append("after deleting python: ").append(vault.category(py) == null ? "gone" : "kept").append(", prompt ").append(vault.prompt(id) == null ? "gone" : "kept").append('\n');
  }

  /**
   * Prompts page video preview: Extract frames serves the video (with byte ranges) and a page to
   * the browser on 127.0.0.1 under a random token; the first and last frame it sends back become
   * reference files 1 and 2 (named for the video). Here the test stands in for the browser.
   */
  void s44_video_frames() throws Exception {
    // The browser's way, as on a computer without VLC (s47 plays it with VLC).
    System.setProperty("pulsekit.novlc", "true");
    File home = new File(System.getProperty("user.home"));
    PromptDb db = (PromptDb) get("promptDb");
    java.util.List<java.net.URI> opened = java.util.Collections.synchronizedList(new ArrayList<java.net.URI>());
    PromptDb.browser = opened::add;
    byte[] video = new byte[5000];
    for (int i = 0; i < video.length; i++) video[i] = (byte) i;
    answers.add("Extract frames");
    edt(() -> db.preview("My clip.mp4", video));
    out.append("opened: ").append(opened.size()).append(" page, ").append(opened.get(0).getHost()).append(", token ")
        .append(opened.get(0).getPath().split("/")[1].length()).append(" chars\n");
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    String base = opened.get(0).toString().replace("/page", "/");
    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(base + "page").openConnection();
    String page = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
    out.append("page: ").append(c.getResponseCode()).append(", title ").append(page.contains("<title>Frames \u00b7 My clip.mp4</title>")).append('\n');
    c = (java.net.HttpURLConnection) new java.net.URL(base + "video").openConnection();
    c.setRequestProperty("Range", "bytes=100-199");
    byte[] part = readAll(c.getInputStream());
    out.append("range: ").append(c.getResponseCode()).append(' ').append(c.getHeaderField("Content-Range")).append(", ").append(part.length)
        .append(" bytes, first ").append(part[0] & 0xff).append(", type ").append(c.getContentType()).append('\n');
    c = (java.net.HttpURLConnection) new java.net.URL(base.replaceAll("/[0-9a-f]{32}/", "/0123/") + "page").openConnection();
    out.append("wrong token: ").append(c.getResponseCode()).append('\n');
    out.append("not a JPEG: ").append(post(base + "first", "hello".getBytes(StandardCharsets.UTF_8))).append('\n');
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(32, 24, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream jpg = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "jpg", jpg);
    out.append("first: ").append(post(base + "first", jpg.toByteArray())).append(", last: ").append(post(base + "last", jpg.toByteArray())).append('\n');
    idle();
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    for (PromptVault.StoredFile f : PromptDb.files(false, null)) out.append("ref: ").append(f.name).append(" (").append(f.promptTitle).append(", file ").append(f.which).append(")\n");
    Thread.sleep(1500);
    try {
      c = (java.net.HttpURLConnection) new java.net.URL(base + "page").openConnection();
      int code = c.getResponseCode();
      out.append("after: ").append(code).append('\n');
    } catch (java.io.IOException stopped) {
      out.append("after: server stopped\n");
    }
  }

  /**
   * Ref files gallery video previews: Make video previews (n) opens one browser tab (the test
   * stands in for it); the first frame it sends back is kept encrypted in ~/.pulsekit/thumbs and
   * shows on the video's card when the gallery opens again, and the button is gone.
   */
  void s45_video_previews() throws Exception {
    // The browser's way, as on a computer without VLC (s48 makes them with VLC).
    System.setProperty("pulsekit.novlc", "true");
    File home = new File(System.getProperty("user.home"));
    PromptDb db = (PromptDb) get("promptDb");
    java.util.List<java.net.URI> opened = java.util.Collections.synchronizedList(new ArrayList<java.net.URI>());
    PromptDb.browser = opened::add;
    byte[] video = new byte[3000];
    video[0] = 0x1a;
    video[1] = 0x45;
    video[2] = (byte) 0xdf;
    video[3] = (byte) 0xa3;
    PromptDb.vault().addLibraryFile("clip.webm", video, "Imported", 1);
    PromptDb.vault().addLibraryFile("again.webm", video, "Imported", 1);
    answers.add("Make video previews (1)");
    SwingUtilities.invokeLater(() -> db.gallery(false));
    for (int i = 0; i < 100 && opened.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append("opened: ").append(opened.size()).append(", status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    String base = opened.get(0).toString().replace("/page", "/");
    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(base + "video/0").openConnection();
    out.append("video 0: ").append(c.getResponseCode()).append(' ').append(c.getContentType()).append(", ").append(readAll(c.getInputStream()).length).append(" bytes\n");
    c = (java.net.HttpURLConnection) new java.net.URL(base + "video/1").openConnection();
    out.append("video 1: ").append(c.getResponseCode()).append('\n');
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 48, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream jpg = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "jpg", jpg);
    out.append("thumb: ").append(post(base + "thumb/0", jpg.toByteArray())).append(", done: ").append(post(base + "done", new byte[0])).append('\n');
    JDialog first = (JDialog) get("lastGallery");
    answers.add("Close");
    for (int i = 0; i < 100 && get("lastGallery") == first; i++) Thread.sleep(100);
    idle();
    JDialog again = (JDialog) get("lastGallery");
    for (int i = 0; i < 100 && again.isShowing(); i++) Thread.sleep(100);
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    javax.swing.JButton card = (javax.swing.JButton) component(again, "gallery:clip.webm");
    out.append("card preview: ").append(card.getIcon() != null ? card.getIcon().getIconWidth() + "x" + card.getIcon().getIconHeight() : "none")
        .append(", second card: ").append(((javax.swing.JButton) component(again, "gallery:again.webm")).getIcon() != null)
        .append(", button: ").append(named(again, "make-video-previews")).append('\n');
    File[] thumbs = new File(home, ".pulsekit/thumbs").listFiles();
    byte[] sealed = Files.readAllBytes(thumbs[0].toPath());
    out.append("cache: ").append(thumbs.length).append(" file, ").append(thumbs[0].getName().length()).append("-char name, encrypted ")
        .append(!(sealed[0] == (byte) 0xff && sealed[1] == (byte) 0xd8)).append(", reads back ").append(java.util.Arrays.equals(ThumbCache.get(video), jpg.toByteArray())).append('\n');
  }

  /**
   * Browse DB plays audio previews: a sound or MIDI card has ▶ Play under it (the MIDI with the
   * kit), which turns to ■ Stop while it plays; the card itself still picks the file, and the
   * sound stops when the dialog closes. Without an audio device it says so.
   */
  void s46_browse_play() throws Exception {
    File home = new File(System.getProperty("user.home"));
    PromptDb db = (PromptDb) get("promptDb");
    short[] tone = new short[22050 * 3];
    for (int i = 0; i < tone.length; i++) tone[i] = (short) (6000 * Math.sin(i * 0.06));
    PromptDb.vault().addLibraryFile("hit.wav", AudioIo.encodeWav(tone, 22050), "Imported", 1);
    PromptDb.vault().addLibraryFile("beat.mid", Engine.encodeMidi(Engine.styleCells(Engine.styles().get("rock")), 110), "Imported", 1);
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", png);
    PromptDb.vault().addLibraryFile("pic.png", png.toByteArray(), "Imported", 1);
    // A stand-in audio output: this machine has none. It plays until stopped and records what it was given.
    List<String> played = Collections.synchronizedList(new ArrayList<String>());
    PromptDb.clips = () -> (javax.sound.sampled.Clip) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {javax.sound.sampled.Clip.class}, new java.lang.reflect.InvocationHandler() {
          boolean running;
          public Object invoke(Object proxy, Method m, Object[] a) {
            switch (m.getName()) {
              case "open": played.add("open " + (int) ((javax.sound.sampled.AudioFormat) a[0]).getSampleRate() + " Hz, " + ((Integer) a[3] / 2) + " samples"); return null;
              case "start": running = true; return null;
              case "stop": running = false; return null;
              case "close": played.add("close"); return null;
              case "isRunning": return running;
              case "hashCode": return System.identityHashCode(proxy);
              case "equals": return proxy == a[0];
              default: return m.getReturnType() == boolean.class ? Boolean.FALSE : m.getReturnType() == int.class || m.getReturnType() == long.class ? (Object) 0 : null;
            }
          }
        });
    String[] picked = new String[1];
    answers.add("\u25b6 Play");
    SwingUtilities.invokeLater(() -> db.browse(null, (name, file) -> picked[0] = name + " -> " + file.getName()));
    for (int i = 0; i < 100 && get("lastBrowse") == null; i++) Thread.sleep(100);
    idle();
    JDialog dialog = (JDialog) get("lastBrowse");
    StringBuilder buttons = new StringBuilder();
    for (String n : new String[] {"hit.wav", "beat.mid", "pic.png"}) buttons.append(n).append(' ').append(named(dialog, "refs-play:" + n)).append(", ");
    out.append("play buttons: ").append(buttons).append('\n');
    javax.swing.JButton wavPlay = (javax.swing.JButton) component(dialog, "refs-play:hit.wav");
    javax.swing.JButton midPlay = (javax.swing.JButton) component(dialog, "refs-play:beat.mid");
    JLabel now = (JLabel) get("nowPlaying");
    java.util.function.Supplier<String> state = () -> "hit.wav " + wavPlay.getText() + ", beat.mid " + midPlay.getText() + ", playing " + db.playing()
        + " (" + now.getText() + ")";
    out.append("watcher pressed the first Play: ").append(state.get()).append('\n');
    edt(midPlay::doClick);
    out.append("beat.mid again (stops): ").append(state.get()).append('\n');
    edt(wavPlay::doClick);
    out.append("hit.wav: ").append(state.get()).append('\n');
    edt(midPlay::doClick);
    out.append("beat.mid while hit.wav plays (switches): ").append(state.get()).append('\n');
    edt(() -> ((javax.swing.JButton) component(dialog, "refs-pick:hit.wav")).doClick());
    for (String p : played) out.append("clip: ").append(p).append('\n');
    out.append("picked: ").append(picked[0]).append(", dialog ").append(dialog.isShowing() ? "open" : "closed").append(", still playing ").append(db.playing()).append('\n');
  }

  /**
   * Prompts page video Preview plays in place with VLC when it is installed: the first frame
   * shows paused, Play / Pause, Stop, Mute and the position follow the player, Play after the end
   * starts again, and Close lets the file go. Without VLC the browser player is offered, with a
   * note to install VLC. desktop/test/clip.webm: 3.5 s, red, then blue from 1.5 s.
   */
  void s47_vlc_preview() throws Exception {
    System.setProperty("pulsekit.vlc.args", "--aout=dummy");
    PromptDb db = (PromptDb) get("promptDb");
    byte[] clip = Files.readAllBytes(new File(System.getProperty("pulsekit.test.dir", "."), "clip.webm").toPath());
    if (!VlcPlayer.available()) {
      answers.add("Close");
      edt(() -> db.preview("clip.webm", clip));
      out.append("no VLC: ").append(VlcPlayer.why()).append('\n');
      return;
    }
    out.append("VLC ").append(VlcPlayer.version().split(" ")[0].replaceAll("\\.\\d+$", ".x")).append('\n');
    answers.add("Mute");
    SwingUtilities.invokeLater(() -> db.preview("clip.webm", clip));
    for (int i = 0; i < 100 && get("lastVideo") == null; i++) Thread.sleep(100);
    Thread.sleep(1200);
    idle();
    JDialog dialog = (JDialog) get("lastVideo");
    VlcPlayer player = (VlcPlayer) get("lastPlayer");
    java.util.function.Function<String, javax.swing.JButton> b = n -> (javax.swing.JButton) component(dialog, n);
    java.util.function.Supplier<String> color = () -> {
      java.awt.image.BufferedImage f = player.screen.frame;
      if (f == null) return "none";
      int rgb = f.getRGB(f.getWidth() / 2, f.getHeight() / 2);
      return ((rgb >> 16) & 255) > 200 ? "red" : (rgb & 255) > 200 ? "blue" : "other";
    };
    out.append("opened: frame ").append(color.get()).append(", visible ").append(player.screen.visibleW).append('x').append(player.screen.visibleH)
        .append(", playing ").append(player.playing()).append(", ").append(b.apply("video-play").getText()).append(", ")
        .append(b.apply("video-mute").getText()).append(", ").append(((JLabel) component(dialog, "video-time")).getText().replaceAll("^\\d:\\d\\d", "0:0x")).append('\n');
    edt(() -> b.apply("video-play").doClick());
    Thread.sleep(2200);
    out.append("after Play 2 s: frame ").append(color.get()).append(", playing ").append(player.playing()).append(", button ").append(b.apply("video-play").getText())
        .append(", slider moved ").append(((javax.swing.JSlider) component(dialog, "video-position")).getValue() > 300).append('\n');
    edt(() -> b.apply("video-pause").doClick());
    Thread.sleep(400);
    out.append("Pause: playing ").append(player.playing()).append(", button ").append(b.apply("video-play").getText()).append('\n');
    edt(() -> b.apply("video-play").doClick());
    for (int i = 0; i < 80 && player.state() != VlcPlayer.ENDED; i++) Thread.sleep(100);
    Thread.sleep(400);
    out.append("at the end: state ended ").append(player.state() == VlcPlayer.ENDED).append(", button ").append(b.apply("video-play").getText())
        .append(", slider ").append(((javax.swing.JSlider) component(dialog, "video-position")).getValue()).append('\n');
    edt(() -> b.apply("video-play").doClick());
    Thread.sleep(500);
    out.append("Play again: frame ").append(color.get()).append(", playing ").append(player.playing()).append('\n');
    edt(() -> b.apply("video-stop").doClick());
    Thread.sleep(300);
    out.append("Stop: playing ").append(player.playing()).append('\n');
    edt(() -> ((javax.swing.JButton) find(dialog.getContentPane(), "Close")).doClick());
    out.append("closed: ").append(!dialog.isShowing()).append(", released ").append(!player.playing() && player.state() == 0).append('\n');
  }

  /**
   * Gallery video previews with VLC: made as soon as the gallery opens, without sound or a
   * browser tab, kept encrypted, and put on the cards in place (both cards of the same video);
   * a file VLC cannot play keeps its type label, and there is no Make video previews button.
   */
  void s48_vlc_previews() throws Exception {
    System.setProperty("pulsekit.vlc.args", "--aout=dummy");
    if (!VlcPlayer.available()) {
      out.append("no VLC: ").append(VlcPlayer.why()).append('\n');
      return;
    }
    PromptDb db = (PromptDb) get("promptDb");
    byte[] clip = Files.readAllBytes(new File(System.getProperty("pulsekit.test.dir", "."), "clip.webm").toPath());
    byte[] junk = new byte[4000];
    new java.util.Random(7).nextBytes(junk);
    PromptDb.vault().addLibraryFile("clip.webm", clip, "Imported", 1);
    PromptDb.vault().addLibraryFile("same clip.webm", clip, "Imported", 1);
    PromptDb.vault().addLibraryFile("broken.mp4", junk, "Imported", 1);
    java.util.List<java.net.URI> opened = java.util.Collections.synchronizedList(new ArrayList<java.net.URI>());
    PromptDb.browser = opened::add;
    // The shown list's own (greyed) button: the watcher leaves the gallery open.
    answers.add("Ref files (3)");
    SwingUtilities.invokeLater(() -> db.gallery(false));
    for (int i = 0; i < 100 && get("lastGallery") == null; i++) Thread.sleep(100);
    Thread.sleep(300);
    for (int i = 0; i < 300 && get("vlcPreviews") != null; i++) Thread.sleep(100);
    idle();
    JDialog gallery = (JDialog) get("lastGallery");
    out.append("button: ").append(named(gallery, "make-video-previews")).append(", browser tabs: ").append(opened.size())
        .append(", status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    for (String n : new String[] {"clip.webm", "same clip.webm", "broken.mp4"}) {
      javax.swing.JButton card = (javax.swing.JButton) component(gallery, "gallery:" + n);
      out.append("card ").append(n).append(": ").append(card.getIcon() == null ? "type label " + card.getText().contains(">MP4<")
          : "preview " + card.getIcon().getIconWidth() + "x" + card.getIcon().getIconHeight()).append('\n');
    }
    byte[] made = ThumbCache.get(clip);
    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(made));
    int rgb = img.getRGB(img.getWidth() / 2, img.getHeight() / 2);
    out.append("kept: ").append(img.getWidth()).append('x').append(img.getHeight()).append(((rgb >> 16) & 255) > 200 && (rgb & 255) < 60 ? " red (the first frame)" : " not red")
        .append(", cache files ").append(new File(System.getProperty("user.home"), ".pulsekit/thumbs").list().length).append('\n');
    edt(() -> ((javax.swing.JButton) find(gallery.getContentPane(), "Close")).doClick());
  }

  /** SogniChat Params: New chat clears "Continue from saved chat", so the next run starts a new chat; the other arguments stay. */
  void s49_new_chat() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniChat.java");
    File old = new File(System.getProperty("user.home"), "sogni-chat-old.txt");
    Files.write(old.toPath(), "Prompt: Hi\n\nHello.\n".getBytes(StandardCharsets.UTF_8));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--continue \"" + old.getAbsolutePath() + "\" --prompt Why?"));
    answers.add("New chat");
    answers.add("OK");
    call("openParams");
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText()).append('\n');
    // Without a saved chat the row reads None; New chat leaves it so.
    answers.add("OK");
    call("openParams");
    out.append("args again: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText()).append('\n');
  }

  /**
   * Export screen: "Export supported media files to DB also". Off, an export only writes the
   * file; on, Pattern / Fill MIDI, WAV, MP3 and Song MIDI exports also go into the prompt library
   * as reference files under the names they were saved as; SF2 is left out. Kept across starts.
   */
  void s50_export_to_db() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("loadStyle", "rock", false);
    call("showView", "export");
    javax.swing.JCheckBox box = (javax.swing.JCheckBox) component(frame, "export-to-db");
    out.append("checkbox: ").append(box.getText()).append(", ").append(box.isSelected()).append('\n');
    String[][] runs = {{"saveMidi", "pattern"}, {"on", ""}, {"saveMidi", "pattern"}, {"saveMidi", "fill"}, {"saveWav", ""}, {"saveMp3", ""}, {"saveSf2", ""}, {"saveSongMidi", ""}};
    for (String[] r : runs) {
      if (r[0].equals("on")) {
        edt(box::doClick);
        continue;
      }
      answers.add("Save");
      if (r[1].isEmpty()) call(r[0]);
      else call(r[0], r[1]);
      out.append(r[0]).append(r[1].isEmpty() ? "" : " " + r[1]).append(": ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
      edt(() -> ((Pulsekit) frame).setNow(null));
    }
    for (PromptVault.StoredFile f : PromptDb.files(false, null)) out.append("ref: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    out.append("kept: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/export-settings.txt").toPath()), StandardCharsets.UTF_8).trim()).append('\n');
  }

  /**
   * Import screen: "Import to DB also" keeps a file picked with Choose file in the prompt library
   * too, as a reference file; Browse DB (after Choose file) imports a library file as Choose file
   * would, without storing it again. Kept across starts.
   */
  void s51_import_to_db() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "import");
    javax.swing.JCheckBox box = (javax.swing.JCheckBox) component(frame, "import-to-db");
    javax.swing.JButton browse = (javax.swing.JButton) component(frame, "import-browse-db");
    out.append("checkbox: ").append(box.getText()).append(", ").append(box.isSelected()).append("; ").append(browse.getText()).append('\n');
    edt(browse::doClick);
    out.append("empty: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    // Pattern MIDIs: picked with Choose file (the watcher types nothing, so the file is preselected).
    for (String n : new String[] {"off", "groove"}) {
      if (n.equals("groove")) edt(box::doClick);
      File f = new File(home, n + ".mid");
      Files.write(f.toPath(), Engine.encodeMidi(Engine.styleCells(Engine.styles().get("rock")), 110));
      // Choose file: the watcher picks the file in the file chooser.
      chooseNext = f;
      call("openFile");
      out.append(n).append(".mid: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
      edt(() -> ((Pulsekit) frame).setNow(null));
    }
    for (PromptVault.StoredFile f : PromptDb.files(false, null)) out.append("ref: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    // Browse DB: the library's MIDI, imported as Choose file would.
    // The shown list's own (greyed) button: the watcher leaves the dialog open.
    answers.add("Ref files (1)");
    SwingUtilities.invokeLater(browse::doClick);
    for (int i = 0; i < 100 && get("lastBrowse") == null; i++) Thread.sleep(100);
    idle();
    JDialog dialog = (JDialog) get("lastBrowse");
    edt(() -> ((javax.swing.JButton) component(dialog, "refs-pick:groove.mid")).doClick());
    idle();
    out.append("from DB: ").append(((JLabel) get("nowPlaying")).getText()).append(", library still ").append(PromptDb.files(false, null).size()).append(" file\n");
    out.append("kept: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/import-settings.txt").toPath()), StandardCharsets.UTF_8).trim()).append('\n');
  }

  /** The bottom ■ / Play / Gen bar shows on the pages that play (Pattern, Fillern, Fill, Song) and not on the others (PyJav, Pads...). */
  void s52_transport_pages() throws Exception {
    for (String v : new String[] {"pattern", "combo", "fills", "song", "py", "pads", "prompts", "import", "export", "midisettings", "help", "comparehits"}) {
      call("showView", v);
      out.append(v).append(": ").append(((javax.swing.JPanel) get("transportBar")).isVisible() ? "bar" : "no bar").append('\n');
    }
    // Playing, a page with the bar keeps playing; one without it stops. This machine has no MIDI
    // synth, so a stand-in sequencer plays: running until stopped.
    final boolean[] running = new boolean[1];
    javax.sound.midi.Sequencer seq = (javax.sound.midi.Sequencer) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {javax.sound.midi.Sequencer.class}, (proxy, m, a) -> {
          switch (m.getName()) {
            case "isRunning": return running[0];
            case "start": running[0] = true; return null;
            case "stop": running[0] = false; return null;
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy == a[0];
            default: return m.getReturnType() == boolean.class ? Boolean.FALSE : m.getReturnType() == int.class ? (Object) 0
                : m.getReturnType() == long.class ? (Object) 0L : m.getReturnType() == float.class ? (Object) 0f : null;
          }
        });
    set("sequencer", seq);
    for (String v : new String[] {"song", "import", "py", "pads"}) {
      call("showView", "pattern");
      running[0] = true;
      edt(() -> ((javax.swing.JButton) get("playBtn")).setText("Pause"));
      call("showView", v);
      out.append("playing, then ").append(v).append(": ").append(running[0] ? "still playing" : "stopped")
          .append(", Play reads ").append(((javax.swing.JButton) get("playBtn")).getText()).append('\n');
    }
    running[0] = false;
    set("sequencer", null);
  }

  /**
   * --no_filter (Content filter off): SogniVideo sends safe_content_filter false and says so; without
   * it a run Sogni pauses for a safety review says what happened and points to --no_filter. Chats and
   * chat runs carry the same setting. A stand-in Sogni server pauses runs that leave the filter on.
   */
  void s53_no_filter() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final List<String> bodies = Collections.synchronizedList(new ArrayList<String>());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    final boolean[] filterOff = new boolean[1];
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      String reply;
      if (path.equals("/v1/creative-agent/workflows")) {
        bodies.add(body);
        filterOff[0] = body.contains("\"safe_content_filter\":false");
        reply = "{\"data\":{\"workflow\":{\"workflowId\":\"ws1\",\"status\":\"queued\"}}}";
      } else if (path.endsWith("/events/stream")) {
        reply = filterOff[0] ? "data: {\"status\":\"completed\"}\n\n" : "data: {\"status\":\"waiting_for_user\"}\n\n";
      } else if (path.equals("/v1/creative-agent/workflows/ws1")) {
        reply = filterOff[0]
            ? "{\"data\":{\"workflow\":{\"workflowId\":\"ws1\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port + "/files/c.mp4\",\"mediaType\":\"video\"}]}}}"
            : "{\"data\":{\"workflow\":{\"workflowId\":\"ws1\",\"status\":\"waiting_for_user\",\"waitingReason\":\"safety_review_required\"}}}";
      } else if (path.equals("/files/c.mp4")) {
        byte[] clip = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2', 'i', 's', 'o', 'm'};
        ex.getResponseHeaders().set("Content-Type", "video/mp4");
        ex.sendResponseHeaders(200, clip.length);
        ex.getResponseBody().write(clip);
        ex.close();
        return;
      } else {
        reply = "{}";
      }
      byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", path.endsWith("/events/stream") ? "text/event-stream" : "application/json");
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniVideo.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        if (p.token.equals("--no_filter")) out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)").append('\n');
      }
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      String[] runs = {"--prompt \"Animate this cartoon\"", "--prompt \"Animate this cartoon\" --no_filter",
          "--prompt \"Animate this cartoon\" --no_filter --loras h3-mystic-xxx-v4:0.8,h3-vbvr-video-reasoning:1", "--prompt \"Animate this cartoon\" --loras h3-mystic-xxx-v4:0.8"};
      for (int r = 0; r < runs.length; r++) {
        final String extra = runs[r];
        if (r == 1 || r == 2) answers.add("Close");
        edt(() -> log.setText(""));
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(300);
        out.append("== ").append(extra).append('\n');
        for (String line : textOf(log).split("\n")) {
          if (line.startsWith("Content filter") || line.startsWith("LoRAs") || line.startsWith("Failed") || line.startsWith("Succeeded") || line.startsWith("Status")) out.append("  ").append(line).append('\n');
        }
      }
      synchronized (bodies) {
        for (String b : bodies) {
          int at = b.indexOf("\"loras\"");
          out.append("start sends safe_content_filter: ").append(b.contains("\"safe_content_filter\":false") ? "false" : "nothing (Sogni's default: on)")
              .append(", ").append(at < 0 ? "no LoRAs" : b.substring(at, b.indexOf(']', b.indexOf("loraStrengths", at)) + 1)).append('\n');
        }
      }
      // The chat request and the chat run carry the setting too (SogniChat; the same SogniApi in each program).
      List<String[]> turns = new ArrayList<String[]>();
      turns.add(new String[] {"user", "hi"});
      SogniApi.noFilter = true;
      String chat = SogniApi.chatInput(null, null, turns, 0, false, "creative-tools", true);
      String run = SogniApi.chatRunInput(null, null, turns, 0, false, null);
      SogniApi.noFilter = false;
      out.append("chat: ").append(chat.contains("\"safe_content_filter\":false")).append(", chat run: ").append(run.contains("\"runtime_config\":{\"safeContentFilter\":false}"))
          .append(", off by default: ").append(!SogniApi.chatInput(null, null, turns, 0, false, null).contains("safe_content_filter")).append('\n');
    } finally {
      server.stop(0);
    }
  }

  /**
   * SogniVideo Params: "Save the prompt as a prompt sheet" and "Content filter off" start ticked
   * until SogniVideo's Params are saved (then the saved choice wins). Params text fields have a
   * menu on a right click or long press: Select all, Cut, Copy, Paste.
   */
  void s54_video_defaults() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniVideo.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    final StringBuilder result = new StringBuilder();
    for (int round = 0; round < 2; round++) {
      final int r = round;
      final StringBuilder seen = new StringBuilder();
      inspectNext = d -> {
        for (String t : new String[] {"--saveprompt", "--no_filter", "--no_audio", "--unlimited"}) {
          javax.swing.JCheckBox c = (javax.swing.JCheckBox) component(d, "params-check:" + t);
          seen.append(t).append(c != null && c.isSelected() ? " on" : " off").append(", ");
        }
        javax.swing.JTextArea prompt = (javax.swing.JTextArea) component(d, "params-field:--prompt");
        if (r == 0) {
          prompt.setText("A cat walks");
          JTextField aspect = (JTextField) component(d, "params-field:--aspect");
          javax.swing.JPopupMenu m = TextMenu.menu(prompt);
          StringBuilder items = new StringBuilder();
          for (java.awt.Component c : m.getComponents()) {
            if (c instanceof javax.swing.JMenuItem) items.append(((javax.swing.JMenuItem) c).getText()).append(((javax.swing.JMenuItem) c).isEnabled() ? "" : " (off)").append(", ");
          }
          seen.append("\nprompt menu: ").append(items);
          ((javax.swing.JMenuItem) m.getComponent(0)).doClick();
          seen.append("\nafter Select all: \"").append(prompt.getSelectedText()).append("\"; menu now ");
          for (java.awt.Component c : TextMenu.menu(prompt).getComponents()) {
            if (!(c instanceof javax.swing.JMenuItem)) continue;
            seen.append(((javax.swing.JMenuItem) c).getText()).append(((javax.swing.JMenuItem) c).isEnabled() ? "" : " (off)").append(", ");
          }
          ((javax.swing.JMenuItem) TextMenu.menu(prompt).getComponent(2)).doClick();
          aspect.setText("");
          ((javax.swing.JMenuItem) TextMenu.menu(aspect).getComponent(3)).doClick();
          seen.append("\nCopy, then Paste in --aspect: \"").append(aspect.getText()).append("\"");
          aspect.setText("16:9");
          // Untick the filter switch: saved, it stays off next time.
          ((javax.swing.JCheckBox) component(d, "params-check:--no_filter")).setSelected(false);
        }
      };
      answers.add("OK");
      call("openParams");
      result.append("round ").append(round + 1).append(": ").append(seen).append('\n');
      result.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText()).append('\n');
    }
    // SogniChat runs with the filter off: its Params have "Content filter on", unticked.
    call("selectListedProgram", "Java", "SogniChat.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    final StringBuilder chat = new StringBuilder("SogniChat:");
    inspectNext = d -> {
      for (String t : new String[] {"--filter_on", "--no_filter", "--tools", "--run_tools", "--unlimited", "--thinking"}) {
        javax.swing.JCheckBox c = (javax.swing.JCheckBox) component(d, "params-check:" + t);
        chat.append(' ').append(t).append(c == null ? " none" : c.isSelected() ? " on" : " off");
      }
    };
    answers.add("Cancel");
    call("openParams");
    result.append(chat).append('\n');
    // Only what the rounds saw (not the long Params dialog text).
    out.setLength(0);
    out.append(result);
  }

  /**
   * SogniVideo: Join with this video. After a run that made a clip, the clip and the chosen video
   * are joined as <clip name>-merged.mp4 (the clip first), saved and kept in the prompt library as a
   * result file; a video in another format is not joined, and a missing one stops the run before it
   * starts. Join is unticked after every run; in Params its buttons work only when it is ticked.
   * The stand-in Sogni server sends desktop/test/clip-a.mp4 as the clip.
   */
  void s55_join() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File dir = new File(System.getProperty("pulsekit.test.dir", "."));
    final byte[] clip = Files.readAllBytes(new File(dir, "clip-a.mp4").toPath());
    File other = new File(home, "next scene.mp4");
    Files.copy(new File(dir, "clip-b.mp4").toPath(), other.toPath());
    File wide = new File(home, "wide.mp4");
    Files.copy(new File(dir, "clip-wide.mp4").toPath(), wide.toPath());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      readAll(ex.getRequestBody());
      byte[] bytes;
      String type = "application/json";
      if (path.equals("/v1/creative-agent/workflows")) {
        bytes = "{\"data\":{\"workflow\":{\"workflowId\":\"wj\",\"status\":\"queued\"}}}".getBytes(StandardCharsets.UTF_8);
      } else if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        bytes = "data: {\"status\":\"completed\"}\n\n".getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/v1/creative-agent/workflows/wj")) {
        bytes = ("{\"data\":{\"workflow\":{\"workflowId\":\"wj\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/c.mp4\",\"mediaType\":\"video\"}]}}}").getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/files/c.mp4")) {
        type = "video/mp4";
        bytes = clip;
      } else {
        bytes = "{}".getBytes(StandardCharsets.UTF_8);
      }
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniVideo.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        if (p.token.equals("--join")) out.append("param ").append(p.token).append(" \"").append(p.label).append("\" join ").append(p.join).append(" Browse DB ").append(p.refs).append('\n');
        if (p.token.equals("--join_first")) out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)").append('\n');
      }
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      String[][] runs = {{"walk", other.getAbsolutePath()}, {"walk2", wide.getAbsolutePath()}, {"walk3", new File(home, "gone.mp4").getAbsolutePath()},
          {"walk4", other.getAbsolutePath(), "--join_first"}};
      for (String[] r : runs) {
        final String extra = r[0] + " --prompt Walk --join \"" + r[1] + "\"" + (r.length > 2 ? " " + r[2] : "");
        if (!r[0].equals("walk3")) answers.add("Close");
        edt(() -> log.setText(""));
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(600);
        out.append("== ").append(r[0]).append(" + ").append(new File(r[1]).getName()).append(r.length > 2 ? " " + r[2] : "").append('\n');
        for (String line : textOf(log).split("\n")) {
          if (line.startsWith("Joined") || line.startsWith("Could not join") || line.startsWith("Note") || line.startsWith("Failed") || line.startsWith("Prompt library") || line.startsWith("Succeeded"))
            out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
        }
        out.append("  args after: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~").replaceAll(" --key_file.*", "")).append('\n');
      }
      try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(home.toPath())) {
        for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files.filter(x -> x.getFileName().toString().matches("walk.*\\.mp4")).sorted()::iterator) {
          out.append("saved ").append(home.toPath().relativize(f)).append(": ").append(Files.size(f) > clip.length + 40000 ? "about both videos' size" : Files.size(f) + " bytes").append('\n');
        }
      }
      for (PromptVault.StoredFile f : PromptDb.files(true, null)) out.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
      // Params: Join is unticked after the runs; its buttons work only when it is ticked.
      final StringBuilder seen = new StringBuilder();
      inspectNext = d -> {
        javax.swing.JCheckBox join = (javax.swing.JCheckBox) component(d, "params-join:--join");
        javax.swing.JButton pick = (javax.swing.JButton) component(d, "params-file:--join");
        javax.swing.JButton db = (javax.swing.JButton) component(d, "params-db:--join");
        seen.append("Params: ").append(join.getText()).append(' ').append(join.isSelected() ? "ticked" : "unticked")
            .append(", Choose ").append(pick.isEnabled() ? "on" : "off").append(", Browse DB ").append(db.isEnabled() ? "on" : "off");
        join.doClick();
        seen.append("; ticked: Choose ").append(pick.isEnabled() ? "on" : "off").append(", Browse DB ").append(db.isEnabled() ? "on" : "off");
      };
      answers.add("Cancel");
      call("openParams");
      out.append(seen).append('\n');
    } finally {
      server.stop(0);
    }
  }

  /**
   * JoinVideo (PyJav's Java menu): video a and video b (also from the prompt library, videos only),
   * Video b first, an optional output name, and Add to DB. Runs join a then b (or b first), refuse
   * videos in different formats, and with --addtodb keep the joined video as a result file.
   */
  void s56_join_video() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File dir = new File(System.getProperty("pulsekit.test.dir", "."));
    File a = new File(home, "scene one.mp4");
    Files.copy(new File(dir, "clip-a.mp4").toPath(), a.toPath());
    File b = new File(home, "scene two.mp4");
    Files.copy(new File(dir, "clip-b.mp4").toPath(), b.toPath());
    File wide = new File(home, "wide.mp4");
    Files.copy(new File(dir, "clip-wide.mp4").toPath(), wide.toPath());
    PromptDb.vault().addLibraryFile("stored.mp4", Files.readAllBytes(b.toPath()), "Imported", 1);
    call("showView", "py");
    call("selectListedProgram", "Java", "JoinVideo.java");
    for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
      out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)")
          .append(p.ext != null && !p.output ? " file " + p.ext : "").append(p.refs ? " Browse DB" : "").append(p.hint.length() > 0 ? " hint: " + p.hint : "").append('\n');
    }
    // Params: Browse DB on each video row lists videos only.
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      for (String t : new String[] {"video_a.mp4", "video_b.mp4"}) {
        javax.swing.JButton db = (javax.swing.JButton) component(d, "params-db:" + t);
        seen.append(t).append(": Browse DB ").append(db == null ? "none" : db.isEnabled() ? "on" : "off").append("; ");
      }
      seen.append("videos in the library: ").append(PromptDb.allFiles(PromptDb.VIDEOS).size()).append(" of ").append(PromptDb.allFiles(null).size());
    };
    answers.add("Cancel");
    call("openParams");
    out.append(seen).append('\n');
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    String[] runs = {
      "\"" + a.getAbsolutePath() + "\" \"" + b.getAbsolutePath() + "\"",
      "\"" + a.getAbsolutePath() + "\" \"" + b.getAbsolutePath() + "\" both.mp4 --b_first --addtodb",
      "\"" + a.getAbsolutePath() + "\" \"" + wide.getAbsolutePath() + "\"",
      "\"" + a.getAbsolutePath() + "\" \"" + b.getAbsolutePath() + "\" three.mp4 --video_c \"" + a.getAbsolutePath() + "\" --addtodb",
    };
    for (int r = 0; r < runs.length; r++) {
      final String extra = runs[r];
      if (r != 2) answers.add("Close");
      edt(() -> log.setText(""));
      set("pyInputPath", null);
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
      edt(() -> call("runPython"));
      for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
      Thread.sleep(600);
      out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
      for (String line : textOf(log).split("\n")) {
        if (line.startsWith("Joined") || line.startsWith("Failed") || line.startsWith("Prompt library") || line.startsWith("Succeeded") || line.startsWith("Add to DB"))
          out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
      }
    }
    try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(home.toPath())) {
      for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files.filter(x -> x.getFileName().toString().matches("(scene one-merged|both|three).*\\.mp4")).sorted()::iterator) {
        long size = Files.size(f);
        out.append("saved ").append(home.toPath().relativize(f)).append(": ").append(size > 120000 ? "about three videos' size" : size > 80000 ? "about two videos' size" : size + " bytes").append('\n');
      }
    }
    for (PromptVault.StoredFile f : PromptDb.files(true, null)) out.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
  }

  /**
   * MediaBrowser (PyJav's Java menu): Params has Directory with Browse, which picks a folder and
   * opens the Media browser on it: its folders first, then pictures, videos and sounds as cards (a
   * picture's thumbnail; a video's needs VLC or the browser, Make video previews), other files left
   * out. A folder opens in its place and Up goes back; a picture opens full size, a video and a
   * sound play. A run lists the folder and opens the Media browser too.
   */
  void s57_media_browser() throws Exception {
    System.setProperty("pulsekit.novlc", "true");
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "my media");
    File more = new File(media, "more");
    more.mkdirs();
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(64, 48, java.awt.image.BufferedImage.TYPE_INT_RGB);
    javax.imageio.ImageIO.write(img, "png", new File(media, "sunset.png"));
    javax.imageio.ImageIO.write(img, "jpg", new File(more, "beach.jpg"));
    Files.copy(new File(System.getProperty("pulsekit.test.dir", "."), "clip-a.mp4").toPath(), new File(media, "walk.mp4").toPath());
    Files.write(new File(media, "beat.wav").toPath(), AudioIo.encodeWav(new short[22050], 22050));
    Files.write(new File(media, "notes.txt").toPath(), "not media".getBytes(StandardCharsets.UTF_8));
    Files.write(new File(media, ".hidden.png").toPath(), new byte[] {1});
    call("showView", "py");
    call("selectListedProgram", "Java", "MediaBrowser.java");
    for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
      out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.dir ? " directory" : "").append(p.isFile() ? " file" : "")
          .append(p.hint.length() > 0 ? " hint: " + p.hint : "").append('\n');
    }
    final Object browser = get("mediaBrowser");
    final java.lang.reflect.Field thumbs = browser.getClass().getDeclaredField("thumbs");
    thumbs.setAccessible(true);
    // The Media browser: the cards, the picture's thumbnail, a folder and Up.
    final StringBuilder seen = new StringBuilder();
    final java.util.function.Consumer<?>[] self = new java.util.function.Consumer<?>[1];
    java.util.function.Consumer<JDialog> look = d -> {
      // The Params dialog and the folder chooser come first: this waits for the Media browser.
      if (!d.getTitle().startsWith("Media browser")) {
        inspectNext = (java.util.function.Consumer<JDialog>) self[0];
        return;
      }
      try {
        for (int i = 0; i < 60 && ((java.util.Map<?, ?>) thumbs.get(browser)).isEmpty(); i++) Thread.sleep(50);
        seen.append("title: ").append(d.getTitle()).append('\n');
        seen.append("summary: ").append(((JLabel) component(d, "media-summary")).getText().replaceAll("<[^>]+>", " ").replace(home.getAbsolutePath(), "~").trim()).append('\n');
        for (String s : names(d.getContentPane())) if (s.startsWith("media-")) seen.append("  ").append(s).append('\n');
        for (String n : new String[] {"sunset.png", "walk.mp4", "beat.wav"}) {
          javax.swing.JButton card = (javax.swing.JButton) component(d, "media-card:" + n);
          seen.append(n).append(": ").append(card.getIcon() != null ? "thumbnail" : "type mark").append(", ").append(card.getToolTipText()).append('\n');
        }
        // A right click on a card: its menu. Add to DB as a reference file, as a result file, Add to default playlist.
        javax.swing.JButton playlist = (javax.swing.JButton) component(d, "media-playlist");
        seen.append("Playlist button before: ").append(playlist.isVisible() ? playlist.getText() : "hidden").append('\n');
        java.util.function.Supplier<String> badges = () -> {
          StringBuilder b = new StringBuilder();
          for (String n : new String[] {"beat.wav", "sunset.png", "walk.mp4"}) {
            b.append(n).append(((javax.swing.JButton) component(d, "media-card:" + n)).getText().contains("\u2630") ? " \u2630" : " -").append("  ");
          }
          return b.toString().trim();
        };
        seen.append("badges before: ").append(badges.get()).append('\n');
        // walk.mp4 is added, taken out again (its menu then says Remove), and added back.
        String[][] picks = {{"sunset.png", "0"}, {"beat.wav", "1"}, {"walk.mp4", "2"}, {"walk.mp4", "2"}, {"walk.mp4", "2"}, {"sunset.png", "2"}, {"beat.wav", "2"}};
        for (String[] pick : picks) {
          javax.swing.JButton card = (javax.swing.JButton) component(d, "media-card:" + pick[0]);
          card.dispatchEvent(new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
              java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3));
          javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) get("lastMenu");
          StringBuilder items = new StringBuilder();
          for (Component c : menu.getComponents()) if (c instanceof javax.swing.JMenuItem) items.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
          if (pick == picks[0]) seen.append("menu: ").append(items).append(", shown ").append(menu.isVisible()).append('\n');
          String label = ((javax.swing.JMenuItem) menu.getComponent(Integer.parseInt(pick[1]))).getText();
          ((javax.swing.JMenuItem) menu.getComponent(Integer.parseInt(pick[1]))).doClick();
          menu.setVisible(false);
          seen.append(pick[0]).append(" \"").append(label).append("\": ").append(((JLabel) get("nowPlaying")).getText())
              .append(", badge ").append(((javax.swing.JButton) component(d, "media-card:" + pick[0])).getText().contains("\u2630") ? "\u2630" : "-")
              .append(", ").append(playlist.isVisible() ? playlist.getText() : "no Playlist button").append('\n');
        }
        for (PromptVault.StoredFile f : PromptDb.files(false, null)) seen.append("ref file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
        for (PromptVault.StoredFile f : PromptDb.files(true, null)) seen.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
        seen.append("Playlist button after: ").append(playlist.isVisible() ? playlist.getText() : "hidden").append('\n');
        seen.append("badges after: ").append(badges.get()).append('\n');
        for (MediaPlaylist.Item it : MediaPlaylist.items(PromptDb.dir(), media.getAbsolutePath())) seen.append("playlist: ").append(it.name).append(" = ").append(it.id.replace(home.getAbsolutePath(), "~")).append('\n');
        ((javax.swing.JButton) component(d, "media-folder:more")).doClick();
        // A folder under it has a playlist of its own.
        javax.swing.JButton morePlaylist = (javax.swing.JButton) component(d, "media-playlist");
        seen.append("in more, Playlist button: ").append(morePlaylist.isVisible() ? morePlaylist.getText() : "hidden");
        javax.swing.JButton beach = (javax.swing.JButton) component(d, "media-card:beach.jpg");
        beach.dispatchEvent(new java.awt.event.MouseEvent(beach, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3));
        javax.swing.JPopupMenu beachMenu = (javax.swing.JPopupMenu) get("lastMenu");
        ((javax.swing.JMenuItem) beachMenu.getComponent(2)).doClick();
        beachMenu.setVisible(false);
        seen.append(", after adding beach.jpg: ").append(morePlaylist.isVisible() ? morePlaylist.getText() : "hidden").append('\n');
        ((javax.swing.JButton) component(d, "media-up")).doClick();
        seen.append("back up, Playlist button: ").append(((javax.swing.JButton) component(d, "media-playlist")).getText()).append(", badges ").append(badges.get()).append('\n');
        ((javax.swing.JButton) component(d, "media-folder:more")).doClick();
        seen.append("in more: ").append(d.getTitle()).append(", up ").append(component(d, "media-up") != null ? "shown" : "none").append('\n');
        for (String s : names(d.getContentPane())) if (s.startsWith("media-card") || s.startsWith("media-folder")) seen.append("  ").append(s).append('\n');
        ((javax.swing.JButton) component(d, "media-up")).doClick();
        seen.append("up: ").append(d.getTitle()).append(", up ").append(component(d, "media-up") != null ? "shown" : "none").append('\n');
      } catch (Exception ex) {
        seen.append("inspect failed: ").append(ex).append('\n');
      }
    };
    self[0] = look;
    inspectNext = look;
    // Params: Browse picks the folder (the watcher chooses it) and the Media browser opens; Close.
    answers.add("Browse");
    chooseNext = media;
    answers.add("Close");
    SwingUtilities.invokeLater(() -> {
      try {
        call("openParams");
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    for (int i = 0; i < 100 && get("shown") == null; i++) Thread.sleep(100);
    Window params = null;
    for (int i = 0; i < 100 && params == null; i++) {
      Thread.sleep(100);
      boolean browsing = false;
      for (Window w : Window.getWindows()) if (w.isShowing() && w instanceof JDialog && ((JDialog) w).getTitle().startsWith("Media browser")) browsing = true;
      for (Window w : Window.getWindows()) if (!browsing && w.isShowing() && w instanceof JDialog && ((JDialog) w).getTitle().startsWith("Parameters")) params = w;
    }
    out.append(seen);
    final Window paramsDialog = params;
    out.append("Params row: ").append(paramsDialog == null ? "no Params dialog" : ((JLabel) component(paramsDialog, "params-chosen:directory")).getText()).append('\n');
    if (paramsDialog != null) edt(() -> find(((JDialog) paramsDialog).getContentPane(), "OK").doClick());
    idle();
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
    // A card's click: the picture full size, the video's player choice (no VLC here), the sound's Play / Stop.
    for (String n : new String[] {"sunset.png", "walk.mp4", "beat.wav"}) {
      answers.add("Close");
      MediaDir.Entry e = new MediaDir.Entry();
      e.name = n;
      e.kind = MediaDir.kind(n);
      File f = new File(media, n);
      SwingUtilities.invokeLater(() -> {
        try {
          java.lang.reflect.Method m = browser.getClass().getDeclaredMethod("openEntry", MediaDir.Entry.class, File.class);
          m.setAccessible(true);
          m.invoke(browser, e, f);
        } catch (Exception ex) {
          errors.add(ex);
        }
      });
      for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
      idle();
    }
    // A run: the folder listed, and the Media browser opened on it.
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    answers.add("Close");
    edt(() -> log.setText(""));
    set("pyInputPath", null);
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
    for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    for (String line : textOf(log).split("\n")) {
      if (line.startsWith("  ") || line.startsWith("Succeeded") || line.startsWith("Failed") || line.startsWith("Media browser"))
        out.append("log: ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    // The playlist window: its files in order; a click opens one as its thumbnail does (the watcher closes the window, then the picture).
    final StringBuilder listed = new StringBuilder();
    inspectNext = d -> {
      listed.append("window: ").append(d.getTitle()).append('\n');
      for (String n : names(d.getContentPane())) {
        if (!n.startsWith("playlist-item:")) continue;
        listed.append("  ").append(((javax.swing.JButton) component(d, n)).getText()).append('\n');
      }
      javax.swing.JButton first = (javax.swing.JButton) component(d, "playlist-item:sunset.png");
      // Held: its thumbnail shows beside the list; the release closes it.
      try {
        java.lang.reflect.Method peek = browser.getClass().getDeclaredMethod("peek", java.awt.Component.class, MediaDir.Entry.class, File.class);
        peek.setAccessible(true);
        MediaDir.Entry beat = new MediaDir.Entry();
        beat.name = "beat.wav";
        beat.kind = MediaDir.SOUND;
        peek.invoke(browser, component(d, "playlist-item:beat.wav"), beat, new File(media, "beat.wav"));
        javax.swing.JWindow w = (javax.swing.JWindow) get("lastPeek");
        listed.append("held beat.wav: preview shown ").append(w.isVisible()).append(", ").append(((JLabel) get("lastPeekImage")).getText().replaceAll("<[^>]+>", " ").trim()).append('\n');
        javax.swing.JButton beatRow = (javax.swing.JButton) component(d, "playlist-item:beat.wav");
        beatRow.dispatchEvent(new java.awt.event.MouseEvent(beatRow, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 5, 5, 1, false, java.awt.event.MouseEvent.BUTTON1));
        listed.append("released: preview shown ").append(w.isVisible()).append('\n');
      } catch (Exception ex) {
        listed.append("peek failed: ").append(ex).append('\n');
      }
      SwingUtilities.invokeLater(first::doClick);
    };
    answers.add("Close");
    answers.add("Close");
    SwingUtilities.invokeLater(() -> {
      try {
        java.lang.reflect.Method m = browser.getClass().getDeclaredMethod("playlist", JDialog.class, File.class);
        m.setAccessible(true);
        m.invoke(browser, null, media);
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    for (int i = 0; i < 80 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append(listed);
    // A picture's preview: its thumbnail, read in the background.
    MediaDir.Entry sunset = new MediaDir.Entry();
    sunset.name = "sunset.png";
    sunset.kind = MediaDir.PICTURE;
    edt(() -> {
      java.lang.reflect.Method peek = browser.getClass().getDeclaredMethod("peek", java.awt.Component.class, MediaDir.Entry.class, File.class);
      peek.setAccessible(true);
      peek.invoke(browser, ((JFrame) frame).getContentPane(), sunset, new File(media, "sunset.png"));
    });
    for (int i = 0; i < 40 && ((JLabel) get("lastPeekImage")).getIcon() == null; i++) Thread.sleep(50);
    for (int i = 0; i < 40 && ((JLabel) get("lastPeekInfo")).getText().trim().isEmpty(); i++) Thread.sleep(50);
    out.append("held sunset.png: ").append(((JLabel) get("lastPeekImage")).getIcon() != null ? "thumbnail" : "no thumbnail").append(", ").append(((JLabel) get("lastPeekInfo")).getText()).append('\n');
    edt(() -> call("unpeek"));
    // A video: its length and resolution (without VLC, from the MP4's own headers).
    MediaDir.Entry walk = new MediaDir.Entry();
    walk.name = "walk.mp4";
    walk.kind = MediaDir.VIDEO;
    edt(() -> {
      java.lang.reflect.Method peek = browser.getClass().getDeclaredMethod("peek", java.awt.Component.class, MediaDir.Entry.class, File.class);
      peek.setAccessible(true);
      peek.invoke(browser, ((JFrame) frame).getContentPane(), walk, new File(media, "walk.mp4"));
    });
    for (int i = 0; i < 60 && ((JLabel) get("lastPeekInfo")).getText().trim().isEmpty(); i++) Thread.sleep(50);
    out.append("held walk.mp4: ").append(((JLabel) get("lastPeekInfo")).getText()).append('\n');
    edt(() -> call("unpeek"));
    out.append("let go: preview shown ").append(((javax.swing.JWindow) get("lastPeek")).isVisible()).append('\n');
    out.append("folder label: ").append(MediaDir.label(media.getAbsolutePath())).append(", phone folder: ")
        .append(MediaDir.label("content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera")).append('\n');
  }

  /**
   * The Media browser's Loop videos (kept in ~/.pulsekit/media-browser.txt): a video opened from it
   * starts with Loop ticked and plays again at its end. The player has Mute, volume, and Zoom −,
   * Zoom +, Fit; without VLC the browser's player page has Loop and zoom too.
   * desktop/test/clip.webm: 3.5 s.
   */
  void s58_media_video() throws Exception {
    System.setProperty("pulsekit.vlc.args", "--aout=dummy");
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "clips");
    media.mkdirs();
    File clip = new File(media, "clip.webm");
    Files.copy(new File(System.getProperty("pulsekit.test.dir", "."), "clip.webm").toPath(), clip.toPath());
    final Object browser = get("mediaBrowser");
    // Loop videos: ticked on the Media browser, and kept.
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      javax.swing.JCheckBox loop = (javax.swing.JCheckBox) component(d, "media-loop");
      seen.append("Loop videos: ").append(loop.getText()).append(", ticked ").append(loop.isSelected());
      loop.doClick();
      seen.append(" -> ").append(loop.isSelected()).append('\n');
    };
    answers.add("Close");
    SwingUtilities.invokeLater(() -> {
      try {
        java.lang.reflect.Method m = browser.getClass().getDeclaredMethod("open", File.class);
        m.setAccessible(true);
        m.invoke(browser, media);
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append(seen);
    out.append("kept: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/media-browser.txt").toPath()), StandardCharsets.UTF_8).trim().replaceAll("place=\\w+", "place=(sealed)")).append('\n');
    final MediaDir.Entry entry = new MediaDir.Entry();
    entry.name = clip.getName();
    entry.kind = MediaDir.VIDEO;
    Runnable open = () -> {
      try {
        java.lang.reflect.Method m = browser.getClass().getDeclaredMethod("openEntry", MediaDir.Entry.class, File.class);
        m.setAccessible(true);
        m.invoke(browser, entry, clip);
      } catch (Exception ex) {
        errors.add(ex);
      }
    };
    if (VlcPlayer.available()) {
      answers.add("Fit");
      SwingUtilities.invokeLater(open);
      for (int i = 0; i < 100 && get("lastVideo") == null; i++) Thread.sleep(100);
      Thread.sleep(1200);
      idle();
      JDialog dialog = (JDialog) get("lastVideo");
      VlcPlayer player = (VlcPlayer) get("lastPlayer");
      java.util.function.Function<String, javax.swing.JButton> b = n -> (javax.swing.JButton) component(dialog, n);
      out.append("VLC player time: ").append(((JLabel) component(dialog, "video-time")).getText().replaceAll("^\\d:\\d\\d", "0:0x")).append('\n');
      out.append("VLC player: Loop ").append(((javax.swing.JCheckBox) component(dialog, "video-loop")).isSelected())
          .append(", ").append(b.apply("video-mute").getText()).append(", volume ").append(component(dialog, "video-volume") != null ? "slider" : "none").append('\n');
      for (String z : new String[] {"video-zoom-in", "video-zoom-in", "video-zoom-in", "video-zoom-out", "video-fit"}) {
        edt(() -> b.apply(z).doClick());
        out.append("  ").append(z).append(": ").append(((JLabel) component(dialog, "video-zoom")).getText()).append(", screen ").append(MediaDir.zoomLabel(player.screen.zoom)).append('\n');
      }
      edt(() -> b.apply("video-zoom-in").doClick());
      edt(() -> b.apply("video-play").doClick());
      // 3.5 s long: after 5 s it is playing again from the start.
      Thread.sleep(5000);
      out.append("after 5 s: playing ").append(player.playing()).append(", ended ").append(player.state() == VlcPlayer.ENDED).append('\n');
      // Speed 2x: a second of playing moves it on about two.
      @SuppressWarnings("unchecked")
      javax.swing.JComboBox<String> speed = (javax.swing.JComboBox<String>) component(dialog, "video-speed");
      StringBuilder items = new StringBuilder();
      for (int i = 0; i < speed.getItemCount(); i++) items.append(speed.getItemAt(i)).append(' ');
      out.append("speeds: ").append(items.toString().trim()).append(", now ").append(speed.getSelectedItem()).append('\n');
      edt(() -> speed.setSelectedItem("2x"));
      Thread.sleep(300);
      long t0 = player.timeMs();
      Thread.sleep(1000);
      long moved = player.timeMs() - t0;
      out.append("2x: one second moves it on ").append(moved > 1500 || moved < -1500 ? "about two" : moved + " ms").append('\n');
      edt(() -> speed.setSelectedItem("1.5x"));
      edt(() -> ((javax.swing.JSlider) component(dialog, "video-volume")).setValue(40));
      edt(() -> ((javax.swing.JCheckBox) component(dialog, "video-loop")).doClick());
      for (int i = 0; i < 80 && player.state() != VlcPlayer.ENDED; i++) Thread.sleep(100);
      out.append("Loop off: ended ").append(player.state() == VlcPlayer.ENDED).append('\n');
      edt(() -> ((javax.swing.JButton) find(dialog.getContentPane(), "Close")).doClick());
      idle();
      out.append("kept: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/media-browser.txt").toPath()), StandardCharsets.UTF_8).trim().replace('\n', ' ').replaceAll("place=\\w+", "place=(sealed)")).append('\n');
      // The next video opens as that one was left.
      set("lastVideo", null);
      answers.add("Fit");
      SwingUtilities.invokeLater(open);
      for (int i = 0; i < 100 && get("lastVideo") == null; i++) Thread.sleep(100);
      Thread.sleep(800);
      idle();
      JDialog again = (JDialog) get("lastVideo");
      out.append("opened again: volume ").append(((javax.swing.JSlider) component(again, "video-volume")).getValue())
          .append(", zoom ").append(((JLabel) component(again, "video-zoom")).getText())
          .append(", speed ").append(((javax.swing.JComboBox<?>) component(again, "video-speed")).getSelectedItem()).append('\n');
      edt(() -> ((javax.swing.JButton) find(again.getContentPane(), "Close")).doClick());
      idle();
      // A preview elsewhere (the Prompts page) keeps its own: 80%, fit, 1x.
      PromptDb db = (PromptDb) get("promptDb");
      set("lastVideo", null);
      answers.add("Mute");
      SwingUtilities.invokeLater(() -> db.vlcPreview("clip.webm", clip));
      for (int i = 0; i < 100 && get("lastVideo") == null; i++) Thread.sleep(100);
      Thread.sleep(800);
      idle();
      JDialog other = (JDialog) get("lastVideo");
      out.append("Prompts preview: volume ").append(((javax.swing.JSlider) component(other, "video-volume")).getValue())
          .append(", zoom ").append(((JLabel) component(other, "video-zoom")).getText())
          .append(", speed ").append(((javax.swing.JComboBox<?>) component(other, "video-speed")).getSelectedItem()).append('\n');
      edt(() -> ((javax.swing.JButton) find(other.getContentPane(), "Close")).doClick());
      idle();
      // The Prompts page's own player (Preview of a stored video): Loop video, volume and zoom kept apart from the Media browser's.
      final byte[] webm = Files.readAllBytes(clip.toPath());
      for (int round = 0; round < 2; round++) {
        set("lastVideo", null);
        answers.add("Mute");
        SwingUtilities.invokeLater(() -> db.preview("clip.webm", webm));
        for (int i = 0; i < 100 && get("lastVideo") == null; i++) Thread.sleep(100);
        Thread.sleep(800);
        idle();
        JDialog pv = (JDialog) get("lastVideo");
        javax.swing.JCheckBox loopBox = (javax.swing.JCheckBox) component(pv, "video-loop");
        out.append(round == 0 ? "Prompts player first: " : "Prompts player again: ").append(loopBox.getText()).append(' ').append(loopBox.isSelected() ? "ticked" : "not ticked")
            .append(", volume ").append(((javax.swing.JSlider) component(pv, "video-volume")).getValue())
            .append(", zoom ").append(((JLabel) component(pv, "video-zoom")).getText()).append('\n');
        if (round == 0) {
          edt(() -> loopBox.doClick());
          edt(() -> ((javax.swing.JSlider) component(pv, "video-volume")).setValue(30));
          edt(() -> ((javax.swing.JButton) component(pv, "video-zoom-in")).doClick());
          edt(() -> ((javax.swing.JButton) component(pv, "video-zoom-in")).doClick());
        }
        edt(() -> ((javax.swing.JButton) find(pv.getContentPane(), "Close")).doClick());
        idle();
        if (round == 0) out.append("Prompts kept: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/prompt-video.txt").toPath()), StandardCharsets.UTF_8).trim().replace('\n', ' ')).append('\n');
      }
      out.append("Media browser still: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/media-browser.txt").toPath()), StandardCharsets.UTF_8).trim().replaceAll("place=\\w+", "place=(sealed)").replace('\n', ' ')).append('\n');
    } else {
      out.append("no VLC: ").append(VlcPlayer.why()).append('\n');
      MediaDir.volume = 40;
      MediaDir.zoom = 1.25;
      MediaDir.speed = 1.5;
    }
    // Without VLC: the browser's player page, looping, with zoom.
    System.setProperty("pulsekit.novlc", "true");
    final java.net.URI[] page = new java.net.URI[1];
    PromptDb.browser = uri -> page[0] = uri;
    answers.add("Play");
    SwingUtilities.invokeLater(open);
    for (int i = 0; i < 50 && page[0] == null; i++) Thread.sleep(100);
    idle();
    String html = page[0] == null ? "" : new String(Files.readAllBytes(new File(page[0]).toPath()), StandardCharsets.UTF_8);
    out.append("player page time: ").append(html.contains("id=\"time\"")).append('\n');
    out.append("player page starts at: volume ").append(html.contains("value=\"40\"")).append(", zoom ").append(html.contains("z=1.25,")).append(", speed ").append(html.contains("value=\"1.5\" selected")).append('\n');
    out.append("player page: loop ").append(html.contains(" loop>")).append(", Loop box ").append(html.contains("id=\"loop\" type=\"checkbox\" checked")).append(", Mute ").append(html.contains("id=\"mute\""))
        .append(", volume ").append(html.contains("id=\"volume\"")).append(", zoom ").append(html.contains("id=\"zin\"") && html.contains("id=\"zout\"") && html.contains("id=\"fit\"")).append('\n');
    out.append("zoom steps: ");
    for (double z = 1, i = 0; i < 8; i++, z = MediaDir.zoom(z, true)) out.append(MediaDir.zoomLabel(z)).append(' ');
    out.append('\n');
  }

  /**
   * The Media browser remembers the folder picked and the folder it was in under it: Params'
   * Directory shows the folder when the arguments have none, and opening the browser on the same
   * folder again goes back to that folder (not when it is gone, nor for another folder).
   */
  void s59_media_remember() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "my media");
    File deep = new File(new File(media, "trips"), "2024");
    deep.mkdirs();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", new File(deep, "lake.png"));
    File other = new File(home, "other");
    other.mkdirs();
    final Object browser = get("mediaBrowser");
    java.util.function.Consumer<File> open = dir -> SwingUtilities.invokeLater(() -> {
      try {
        java.lang.reflect.Method m = browser.getClass().getDeclaredMethod("open", File.class);
        m.setAccessible(true);
        m.invoke(browser, dir);
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    // First time: it opens on the folder itself; go into trips, then 2024.
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      seen.append("opened: ").append(d.getTitle()).append('\n');
      ((javax.swing.JButton) component(d, "media-folder:trips")).doClick();
      ((javax.swing.JButton) component(d, "media-folder:2024")).doClick();
      seen.append("went to: ").append(d.getTitle()).append('\n');
    };
    answers.add("Close");
    open.accept(media);
    for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append(seen);
    String kept = new String(Files.readAllBytes(new File(home, ".pulsekit/media-browser.txt").toPath()), StandardCharsets.UTF_8);
    out.append("kept: folder sealed ").append(kept.contains("place=") && !kept.contains("my media")).append(", reads back ");
    MediaDir.decode(kept);
    out.append(MediaDir.lastRoot.replace(home.getAbsolutePath(), "~")).append(" | ").append(String.join(" | ", MediaDir.lastPath).replace(home.getAbsolutePath(), "~")).append('\n');
    // Again: back in 2024, with Up going to trips.
    final StringBuilder again = new StringBuilder();
    inspectNext = d -> {
      again.append("reopened: ").append(d.getTitle()).append(", cards ").append(names(d.getContentPane()).stream().filter(n -> n.startsWith("media-card")).collect(java.util.stream.Collectors.toList()));
      ((javax.swing.JButton) component(d, "media-up")).doClick();
      again.append(", Up: ").append(d.getTitle()).append('\n');
    };
    answers.add("Close");
    open.accept(media);
    for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append(again);
    // Back to my media after trips is gone: the folder itself.
    Files.delete(new File(deep, "lake.png").toPath());
    Files.delete(deep.toPath());
    Files.delete(deep.getParentFile().toPath());
    final StringBuilder gone = new StringBuilder();
    inspectNext = d -> gone.append("trips gone: ").append(d.getTitle()).append('\n');
    answers.add("Close");
    open.accept(media);
    for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append(gone);
    // Another folder: it opens on itself.
    final StringBuilder elsewhere = new StringBuilder();
    inspectNext = d -> elsewhere.append("another folder: ").append(d.getTitle()).append('\n');
    answers.add("Close");
    open.accept(other);
    for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append(elsewhere);
    // A plain playlist from before (playlist-<hash>.txt): read, sealed, and the plain file removed.
    File pk = new File(home, ".pulsekit");
    byte[] d0 = java.security.MessageDigest.getInstance("SHA-256").digest(other.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
    StringBuilder h0 = new StringBuilder();
    for (int i = 0; i < 8; i++) h0.append(String.format("%02x", d0[i] & 0xff));
    File plain = new File(pk, "playlist-" + h0 + ".txt");
    Files.write(plain.toPath(), ("folder\t" + other.getAbsolutePath() + "\n" + other.getAbsolutePath() + "/song.mp3\tsong.mp3\t1234\n").getBytes(StandardCharsets.UTF_8));
    final StringBuilder migrated = new StringBuilder();
    inspectNext = d -> {
      javax.swing.JButton pl = (javax.swing.JButton) component(d, "media-playlist");
      migrated.append("old plain playlist: ").append(pl.isVisible() ? pl.getText() : "no Playlist button");
    };
    answers.add("Close");
    open.accept(other);
    for (int i = 0; i < 50 && !answers.isEmpty(); i++) Thread.sleep(100);
    idle();
    out.append(migrated).append(", plain file left ").append(plain.isFile());
    File[] sealedLists = pk.listFiles((dir, n) -> n.startsWith("playlist-") && n.endsWith(".dat"));
    boolean readable = false;
    for (File f : sealedLists) if (new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1).contains("song.mp3")) readable = true;
    out.append(", sealed files ").append(sealedLists.length).append(", names readable in them ").append(readable).append('\n');
    // Params: Directory shows the folder last opened when the arguments have none; OK keeps it.
    call("showView", "py");
    call("selectListedProgram", "Java", "MediaBrowser.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    final StringBuilder params = new StringBuilder();
    inspectNext = d -> params.append("Params Directory: ").append(((JLabel) component(d, "params-chosen:directory")).getText()).append('\n');
    answers.add("OK");
    call("openParams");
    out.append(params);
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
  }

  /**
   * SogniPedit (PyJav's Java menu): Krea 2 Identity Edit of a picture with an optional second
   * reference, one picture out. Params: the pictures are file rows with Browse DB, Skin detail and
   * more LoRAs; Unlimited Plan and Save the prompt start ticked. A run uploads the pictures, sends
   * edit_image with model krea-identity-edit, Skin Detail 1.1 first, the LoRAs in order, the filter
   * left on and the Unlimited billing, saves the picture and the prompt sheet (into the library).
   */
  void s60_sogni_pedit() throws Exception {
    File home = new File(System.getProperty("user.home"));
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
    File me = new File(home, "me.png");
    javax.imageio.ImageIO.write(img, "png", me);
    File suit = new File(home, "suit.png");
    javax.imageio.ImageIO.write(img, "png", suit);
    final java.io.ByteArrayOutputStream made = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 24, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", made);
    final List<String> starts = Collections.synchronizedList(new ArrayList<String>());
    final List<String> uploads = Collections.synchronizedList(new ArrayList<String>());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      byte[] bytes;
      String type = "application/json";
      if (path.equals("/v1/creative-agent/workflows")) {
        starts.add(body);
        bytes = "{\"data\":{\"workflow\":{\"workflowId\":\"wp\",\"status\":\"queued\"}}}".getBytes(StandardCharsets.UTF_8);
      } else if (path.startsWith("/v1/image/uploadUrl")) {
        uploads.add(path.replaceAll("jobId=[^&]+", "jobId=..."));
        bytes = ("{\"uploadUrl\":\"http://127.0.0.1:" + port + "/put/" + uploads.size() + "\"}").getBytes(StandardCharsets.UTF_8);
      } else if (path.startsWith("/v1/image/downloadUrl")) {
        bytes = ("{\"downloadUrl\":\"http://127.0.0.1:" + port + "/stored/" + uploads.size() + ".png\"}").getBytes(StandardCharsets.UTF_8);
      } else if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        bytes = "data: {\"status\":\"completed\"}\n\n".getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/v1/creative-agent/workflows/wp")) {
        bytes = ("{\"data\":{\"workflow\":{\"workflowId\":\"wp\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/out.png\",\"mediaType\":\"image\"}]}}}").getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/files/out.png")) {
        type = "image/png";
        bytes = made.toByteArray();
      } else {
        bytes = "{}".getBytes(StandardCharsets.UTF_8);
      }
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniPedit.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)")
            .append(p.isFile() ? " file" : "").append(p.refs ? " Browse DB" : "").append(p.defaultOn ? " ticked" : "")
            .append(p.hint.length() > 0 ? " hint: " + p.hint : "").append('\n');
      }
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      final String extra = "--prompt \"Same person, now in a dark blue suit in a sunlit office\" --image \"" + me.getAbsolutePath() + "\" --image2 \"" + suit.getAbsolutePath()
          + "\" --loras krea2-warm-light:0.6 --saveprompt --unlimited --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port;
      answers.add("Close");
      edt(() -> log.setText(""));
      set("pyInputPath", null);
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
      edt(() -> call("runPython"));
      for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
      Thread.sleep(800);
      idle();
      for (String line : textOf(log).split("\n")) {
        if (line.matches("(SogniPedit|Saved prompt|Edit|Model|Picture|LoRAs|Unlimited|Uploaded|Workflow|Wrote|Succeeded|Failed|Prompt library).*"))
          out.append("log: ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
      }
      synchronized (uploads) {
        for (String u : uploads) out.append("upload: ").append(u).append('\n');
      }
      synchronized (starts) {
        for (String b : starts) {
          Map<?, ?> m = (Map<?, ?>) SogniApi.parseJson(b);
          Map<?, ?> step = (Map<?, ?>) ((List<?>) ((Map<?, ?>) m.get("input")).get("steps")).get(0);
          out.append("step: ").append(step.get("toolName")).append(' ').append(SogniApi.toJson(step.get("arguments"))).append('\n');
          out.append("  references: ").append(((List<?>) m.get("media_references")).size()).append(", billing ").append(m.get("billing_mode"))
              .append(", content filter ").append(b.contains("safe_content_filter") ? "off" : "on (Sogni's default)").append('\n');
        }
      }
      File[] pics = home.listFiles((d, n) -> n.startsWith("sogni-pedit-"));
      java.util.Arrays.sort(pics);
      for (File f : pics) out.append("saved: ").append(f.getName()).append(" (").append(f.length() > 0 ? "has data" : "empty").append(")\n");
      for (PromptVault.StoredFile f : PromptDb.files(true, null)) out.append("library result: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
      for (PromptVault.StoredFile f : PromptDb.files(false, null)) out.append("library reference: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    } finally {
      server.stop(0);
    }
  }

  /**
   * SogniPadd (PyJav's Java menu): a picture from a prompt alone with Dark Beast Krea 2. A run sends
   * generate_image with model dark-beast-krea2, no uploads, the default LoRA set in order (or the
   * given ones, or none), the content filter off and the Unlimited billing; Age below 0 with the
   * sexual fine-tunes is refused before anything is sent.
   */
  void s61_sogni_padd() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final java.io.ByteArrayOutputStream made = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 24, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", made);
    final List<String> starts = Collections.synchronizedList(new ArrayList<String>());
    final List<String> other = Collections.synchronizedList(new ArrayList<String>());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      byte[] bytes;
      String type = "application/json";
      if (path.equals("/v1/creative-agent/workflows")) {
        starts.add(body);
        bytes = "{\"data\":{\"workflow\":{\"workflowId\":\"wa\",\"status\":\"queued\"}}}".getBytes(StandardCharsets.UTF_8);
      } else if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        bytes = "data: {\"status\":\"completed\"}\n\n".getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/v1/creative-agent/workflows/wa")) {
        bytes = ("{\"data\":{\"workflow\":{\"workflowId\":\"wa\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/out.png\",\"mediaType\":\"image\"}]}}}").getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/files/out.png")) {
        type = "image/png";
        bytes = made.toByteArray();
      } else {
        other.add(path);
        bytes = "{}".getBytes(StandardCharsets.UTF_8);
      }
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniPadd.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)")
            .append(p.isFile() ? " file" : "").append(p.defaultOn ? " ticked" : "").append(p.hint.length() > 0 ? " hint: " + p.hint : "").append('\n');
      }
      // The LoRA list is a wrapping area of a few lines in Params.
      final StringBuilder field = new StringBuilder();
      inspectNext = d -> {
        Component c = component(d, "params-field:--loras");
        field.append("LoRA field: ").append(c instanceof javax.swing.JTextArea ? "several lines, wraps " + ((javax.swing.JTextArea) c).getLineWrap() : c == null ? "none" : "one line").append('\n');
      };
      answers.add("Cancel");
      call("openParams");
      out.append(field);
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      String[] runs = {
        "--prompt \"A lighthouse on a cliff at dawn\" --aspect 9:16 --saveprompt --unlimited",
        "--prompt \"A lighthouse on a cliff at dawn\" --loras krea2-warm-light:0.6 --seed 42",
        "--prompt \"A lighthouse on a cliff at dawn\" --loras none",
        "--prompt \"A lighthouse on a cliff at dawn\" --loras \"krea2-warm-light:0.6 krea2-skin-detail:1.1\"",
        "--prompt \"A lighthouse on a cliff at dawn\" --loras krea2-age:-2,krea2-mystic-x:1",
      };
      for (int r = 0; r < runs.length; r++) {
        final String extra = runs[r] + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port;
        if (r < 4) answers.add("Close");
        edt(() -> log.setText(""));
        set("pyInputPath", null);
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(800);
        idle();
        out.append("== ").append(runs[r]).append('\n');
        for (String line : textOf(log).split("\n")) {
          if (line.matches("(Saved prompt|Model|LoRAs|Content filter|Unlimited|Succeeded|Failed|Prompt library).*")) out.append("  ").append(line).append('\n');
        }
      }
      synchronized (starts) {
        for (String b : starts) {
          Map<?, ?> m = (Map<?, ?>) SogniApi.parseJson(b);
          Map<?, ?> step = (Map<?, ?>) ((List<?>) ((Map<?, ?>) m.get("input")).get("steps")).get(0);
          out.append("step: ").append(step.get("toolName")).append(' ').append(SogniApi.toJson(step.get("arguments")).replace("A lighthouse on a cliff at dawn", "..."))
              .append(", uploads ").append(m.get("media_references") == null ? "none" : "some").append(", billing ").append(m.get("billing_mode"))
              .append(", content filter ").append(b.contains("\"safe_content_filter\":false") ? "off" : "on").append('\n');
        }
      }
      out.append("other requests: ").append(other).append('\n');
    } finally {
      server.stop(0);
    }
  }

  /**
   * SogniTextVideo (PyJav's Java menu): a clip from a prompt alone with MiniMax H3 FastH3 text to
   * video. A run sends generate_video with the FastH3 t2v model, no uploads, Mystic X v4 0.5 and
   * VBVR Video Reasoning 1 by default (or the LoRAs given, or none), the content filter off and the
   * Unlimited billing; it saves the clip and the prompt sheet.
   */
  void s62_sogni_textvideo() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final List<String> starts = Collections.synchronizedList(new ArrayList<String>());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    final byte[] clip = Files.readAllBytes(new File(System.getProperty("pulsekit.test.dir", "."), "clip-a.mp4").toPath());
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      byte[] bytes;
      String type = "application/json";
      if (path.equals("/v1/creative-agent/workflows")) {
        starts.add(body);
        bytes = "{\"data\":{\"workflow\":{\"workflowId\":\"wt\",\"status\":\"queued\"}}}".getBytes(StandardCharsets.UTF_8);
      } else if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        bytes = "data: {\"status\":\"completed\"}\n\n".getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/v1/creative-agent/workflows/wt")) {
        bytes = ("{\"data\":{\"workflow\":{\"workflowId\":\"wt\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/c.mp4\",\"mediaType\":\"video\"}]}}}").getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/files/c.mp4")) {
        type = "video/mp4";
        bytes = clip;
      } else {
        bytes = "{}".getBytes(StandardCharsets.UTF_8);
      }
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniTextVideo.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.takesValue ? "" : " (on/off)")
            .append(p.isFile() ? " file" : "").append(p.defaultOn ? " ticked" : "").append(ProgramParams.longText(p) ? " several lines" : "")
            .append(p.hint.length() > 0 ? " hint: " + p.hint : "").append('\n');
      }
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      String[] runs = {
        "--prompt \"Waves roll onto a beach at sunset, the camera drifts along the shore\" --duration 8 --aspect 9:16 --saveprompt --unlimited",
        "--prompt \"Waves roll onto a beach at sunset\" --loras h3-better-motion:0.6 --resolution 1080",
        // The names Sogni's app shows, with their strengths.
        "--prompt \"Waves roll onto a beach at sunset\" --loras \"Mystic X v4 0.5,VBVR Video Reasoning 0.7,Better Motion 0.6\"",
        "--prompt \"Waves roll onto a beach at sunset\" --loras \"better motion; Natural Face & Speech: 0.8\"",
        "--prompt \"Waves roll onto a beach at sunset\" --loras \"Fast Zoom 0.5\"",
        // H3's structured prompt, its lines joined into one by Params: the fields start paragraphs again.
        "--prompt \"integrated_multimodal_description: [Shot 1] Live-action, waves roll onto a beach. overall_soundscape: Surf and gulls.\"",
        "--prompt \"Waves roll onto a beach at sunset\" --loras none --no_audio",
        "--prompt \"Waves roll onto a beach at sunset\" --loras h3-better-motion:0",
      };
      for (int r = 0; r < runs.length; r++) {
        final String extra = runs[r] + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port;
        if (r < 3) answers.add("Close");
        edt(() -> log.setText(""));
        set("pyInputPath", null);
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(800);
        idle();
        out.append("== ").append(runs[r]).append('\n');
        for (String line : textOf(log).split("\n")) {
          if (line.matches("(Saved prompt|Model|LoRAs|Content filter|Unlimited|Succeeded|Failed|Prompt library).*")) out.append("  ").append(line).append('\n');
        }
      }
      synchronized (starts) {
        for (String b : starts) {
          Map<?, ?> m = (Map<?, ?>) SogniApi.parseJson(b);
          Map<?, ?> step = (Map<?, ?>) ((List<?>) ((Map<?, ?>) m.get("input")).get("steps")).get(0);
          Map<?, ?> a = (Map<?, ?>) step.get("arguments");
          out.append("step: ").append(step.get("toolName")).append(", model ").append(a.get("videoModel")).append(", duration ").append(a.get("duration"))
              .append(", loras ").append(a.get("loras")).append(' ').append(a.get("loraStrengths")).append(", audio ").append(!Boolean.FALSE.equals(a.get("generateAudio")))
              .append(", uploads ").append(m.get("media_references") == null ? "none" : "some").append(", billing ").append(m.get("billing_mode"))
              .append(", content filter ").append(b.contains("\"safe_content_filter\":false") ? "off" : "on").append('\n');
          if (String.valueOf(a.get("prompt")).contains("integrated_multimodal_description")) out.append("  prompt sent: ").append(String.valueOf(a.get("prompt")).replace("\n", "|")).append('\n');
        }
      }
    } finally {
      server.stop(0);
    }
  }

  /** The names of the components under `root`, in order. */
  private static List<String> names(Container root) {
    List<String> out = new ArrayList<String>();
    for (Component c : root.getComponents()) {
      if (c.getName() != null) out.add(c.getName());
      if (c instanceof Container) out.addAll(names((Container) c));
    }
    return out;
  }

  /** A text area's text, read on the Swing thread (the app writes the log there; read elsewhere it can be caught mid-change). */
  private static String textOf(javax.swing.JTextArea area) throws Exception {
    final String[] t = new String[1];
    SwingUtilities.invokeAndWait(() -> t[0] = area.getText());
    return t[0] == null ? "" : t[0];
  }

  private static int post(String url, byte[] body) throws Exception {
    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
    c.setRequestMethod("POST");
    c.setDoOutput(true);
    c.getOutputStream().write(body);
    return c.getResponseCode();
  }

  /** Opens the File tab's menu and returns it. */
  private javax.swing.JPopupMenu fileMenu() throws Exception {
    javax.swing.JButton file = button(frame, "File");
    file.doClick();
    for (java.awt.Window w : java.awt.Window.getWindows()) {
      javax.swing.JPopupMenu m = popup(w);
      if (m != null && m.isVisible()) return m;
    }
    throw new AssertionError("no File menu");
  }

  private static javax.swing.JFileChooser chooserIn(java.awt.Component c) {
    if (c instanceof javax.swing.JFileChooser) return (javax.swing.JFileChooser) c;
    if (c instanceof java.awt.Container) {
      for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
        javax.swing.JFileChooser f = chooserIn(k);
        if (f != null) return f;
      }
    }
    return null;
  }

  private static javax.swing.JPopupMenu popup(java.awt.Component c) {
    if (c instanceof javax.swing.JPopupMenu) return (javax.swing.JPopupMenu) c;
    if (c instanceof java.awt.Container) {
      for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
        javax.swing.JPopupMenu m = popup(k);
        if (m != null) return m;
      }
    }
    return null;
  }

  private static javax.swing.JButton button(java.awt.Component c, String text) {
    if (c instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) c).getText())) return (javax.swing.JButton) c;
    if (c instanceof java.awt.Container) {
      for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
        javax.swing.JButton b = button(k, text);
        if (b != null) return b;
      }
    }
    return null;
  }

  /**
   * Browse DB's A / I / V / S / T buttons: all files, images, videos, sound files (MIDI too) or
   * text files; the pick is kept for the next Browse DB and across Ref files / Result files.
   */
  void s63_db_filter() throws Exception {
    PromptDb db = (PromptDb) get("promptDb");
    for (String n : new String[] {"pic.png", "clip.mp4", "loop.wav", "beat.mid", "notes.txt", "data.bin"}) PromptDb.vault().addLibraryFile(n, ("x" + n).getBytes(StandardCharsets.UTF_8), "Imported", 1);
    PromptDb.vault().addLibraryFile("story.md", "# story".getBytes(StandardCharsets.UTF_8), "Imported", 3);
    final String[] picked = new String[1];
    java.util.function.Function<String, JDialog> open = only -> {
      try {
        edt(() -> ((JDialog) get("lastBrowse")).dispose());
      } catch (Exception ignored) {
        // none open yet
      }
      try {
        final JDialog[] before = {(JDialog) get("lastBrowse")};
        SwingUtilities.invokeLater(() -> db.browse(false, only, (name, file) -> picked[0] = name));
        for (int i = 0; i < 100 && get("lastBrowse") == before[0]; i++) Thread.sleep(100);
        idle();
        return (JDialog) get("lastBrowse");
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    java.util.function.Function<JDialog, String> shown = d -> {
      StringBuilder sb = new StringBuilder();
      for (String n : new String[] {"pic.png", "clip.mp4", "loop.wav", "beat.mid", "notes.txt", "data.bin", "story.md"}) if (component(d, "refs-pick:" + n) != null) sb.append(n).append(' ');
      sb.append("| greyed ");
      for (String l : DbFilter.LETTERS) {
        javax.swing.JButton b = (javax.swing.JButton) component(d, "db-filter:" + l);
        if (b == null) sb.append(l).append(" missing ");
        else if (!b.isEnabled()) sb.append(l);
      }
      sb.append(" | ").append(((javax.swing.JButton) component(d, "refs-kind:refs")).getText()).append(" / ").append(((javax.swing.JButton) component(d, "refs-kind:results")).getText());
      return sb.toString();
    };
    java.util.function.BiFunction<JDialog, String, JDialog> press = (d, name) -> {
      try {
        final JDialog[] before = {(JDialog) get("lastBrowse")};
        SwingUtilities.invokeLater(() -> ((javax.swing.JButton) component(d, name)).doClick());
        for (int i = 0; i < 100 && get("lastBrowse") == before[0]; i++) Thread.sleep(100);
        idle();
        return (JDialog) get("lastBrowse");
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    JDialog d = open.apply(null);
    out.append("A: ").append(shown.apply(d)).append('\n');
    for (String l : new String[] {"I", "V", "S", "T"}) {
      d = press.apply(d, "db-filter:" + l);
      out.append(l).append(": ").append(shown.apply(d)).append('\n');
    }
    d = press.apply(d, "refs-kind:results");
    out.append("T, Result files: ").append(shown.apply(d)).append('\n');
    d = open.apply(null);
    out.append("opened again: ").append(shown.apply(d)).append('\n');
    d = press.apply(d, "db-filter:S");
    d = open.apply(PromptDb.SOUNDS);
    out.append("S, a sound-only row: ").append(shown.apply(d)).append('\n');
    final JDialog last = d;
    edt(() -> ((javax.swing.JButton) component(last, "refs-pick:loop.wav")).doClick());
    out.append("picked: ").append(picked[0]).append(", dialog ").append(last.isShowing() ? "open" : "closed").append('\n');
  }

  /**
   * SogniChat --saveprompt: the question goes into the prompt library as a prompt (category
   * Writing) with the saved chat (answer.txt) as its result file and the --file as reference file.
   */
  void s64_chat_saveprompt() throws Exception {
    File home = new File(System.getProperty("user.home"));
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      readAll(ex.getRequestBody());
      byte[] bytes = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Try a snare roll into the crash.\"}}]}".getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().set("Content-Type", "application/json");
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      File notes = new File(home, "notes.txt");
      Files.write(notes.toPath(), "Kick on 1 and 3.\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniChat.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        if (p.token.equals("--saveprompt")) out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.defaultOn ? " ticked" : "").append('\n');
      }
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      String[] runs = {
        "answer --prompt \"Suggest one fill\" --system \"Answer briefly.\" --file \"" + notes.getAbsolutePath() + "\" --saveprompt",
        "answer --prompt \"And another\" --saveprompt",
        "--prompt \"Not kept\"",
      };
      for (String extra : runs) {
        edt(() -> log.setText(""));
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Prompt library") || String.valueOf(log.getText()).contains("Failed") || (!extra.contains("--saveprompt") && String.valueOf(log.getText()).contains("Succeeded"))); i++) Thread.sleep(50);
        Thread.sleep(500);
        out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
        for (String line : String.valueOf(log.getText()).split("\n")) {
          if (line.startsWith("$ ") || line.startsWith("Picked up") || line.trim().isEmpty()) continue;
          out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
        }
      }
      PromptVault vault = PromptDb.vault();
      for (PromptVault.Category c : vault.categories()) {
        for (PromptVault.Prompt p : vault.prompts(c.id)) {
          out.append("prompt ").append(c.name).append(" / ").append(p.title).append(": ").append(vault.versions(p.id).size()).append(" versions\n");
        }
      }
      for (PromptVault.StoredFile f : vault.resultFiles()) out.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append("): ")
          .append(new String(vault.fileBytes(f.versionId, f.which), StandardCharsets.UTF_8).trim().replace("\n", "|")).append('\n');
      for (PromptVault.StoredFile f : vault.referenceFiles()) out.append("reference file: ").append(f.name).append('\n');
    } finally {
      server.stop(0);
    }
  }

  /**
   * Extract prompt in a text file's menu in the Result files gallery: the text after the last
   * **Prompt:** of answer.txt kept as Answer Prompt 1.txt (then 2); a file without one says so.
   */
  void s65_extract_prompt() throws Exception {
    PromptDb db = (PromptDb) get("promptDb");
    String answer = "SogniChat conversation\nModel: qwen\n\n=== You ===\nWrite me a portrait prompt\n\n=== Sogni ===\nHere it is.\n\n---\n\n**Prompt:**\n\n"
        + "> A photorealistic studio portrait of a woman with jet-black hair.\n> Soft key light from the left.\n\n**Negative prompt:** blurry\n";
    PromptDb.vault().addLibraryFile("answer.txt", answer.getBytes(StandardCharsets.UTF_8), "answer", 3);
    PromptDb.vault().addLibraryFile("notes.txt", "Kick on 1.\n".getBytes(StandardCharsets.UTF_8), "Imported", 3);
    PromptDb.vault().addLibraryFile("hit.wav", AudioIo.encodeWav(new short[100], 22050), "Imported", 3);
    for (String n : new String[] {"answer.txt", "answer.txt", "notes.txt", "hit.wav"}) {
      PromptVault.StoredFile file = null;
      for (PromptVault.StoredFile f : PromptDb.stored(true)) if (f.name.equals(n)) file = f;
      final PromptVault.StoredFile at = file;
      final StringBuilder seen = new StringBuilder();
      final boolean[] done = {false};
      // The gallery is checked while it is open (the watcher closes it after).
      inspectNext = gallery -> {
        javax.swing.JButton card = (javax.swing.JButton) component(gallery, "gallery:" + n);
        db.menu(at, card, () -> {});
        javax.swing.JMenuItem extract = null;
        for (java.awt.Component c : db.lastFileMenu.getComponents()) {
          if (!(c instanceof javax.swing.JMenuItem)) continue;
          seen.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
          if (((javax.swing.JMenuItem) c).getText().equals("Extract prompt")) extract = (javax.swing.JMenuItem) c;
        }
        db.lastFileMenu.setVisible(false);
        if (extract != null) extract.doClick();
        done[0] = true;
      };
      SwingUtilities.invokeLater(() -> db.gallery(true));
      for (int i = 0; i < 100 && !done[0]; i++) Thread.sleep(100);
      idle();
      out.append(n).append(" menu: ").append(seen).append('\n');
      if (seen.indexOf("Extract prompt") >= 0) out.append("  now: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    }
    for (PromptVault.StoredFile f : PromptDb.stored(true)) {
      if (!f.name.contains("Prompt")) continue;
      out.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append("): ")
          .append(new String(PromptDb.vault().fileBytes(f.versionId, f.which), StandardCharsets.UTF_8).replace("\n", "|")).append('\n');
    }
  }

  /**
   * A Params prompt field from a text file: Browse DB (the prompt library's text files only) and
   * Select file put the file's text in --prompt; a prompt sheet gives its prompt.
   */
  void s66_prompt_from_file() throws Exception {
    File home = new File(System.getProperty("user.home"));
    DbFilter.current = "A";
    PromptDb.vault().addLibraryFile("Answer Prompt 1.txt", "A photorealistic studio portrait.\nSoft key light from the left.\n".getBytes(StandardCharsets.UTF_8), "answer", 3);
    PromptDb.vault().addLibraryFile("hit.wav", AudioIo.encodeWav(new short[100], 22050), "Imported", 3);
    File sheet = new File(home, "kit.prompt");
    Files.write(sheet.toPath(), "PKPROMPT1\nkit\n\n\n\n\nCategory: image\nType: ai\n---\nA red drum kit on a stage.\n\nResult file: kit.png\n".getBytes(StandardCharsets.UTF_8));
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniPadd.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    // Reset to defaults keeps the Params dialog open for the test to work in.
    final JDialog[] params = new JDialog[1];
    inspectNext = d -> params[0] = d;
    answers.add("Reset to defaults");
    Thread opener = new Thread(() -> {
      try {
        call("openParams");
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    opener.start();
    for (int i = 0; i < 100 && (params[0] == null || !params[0].isShowing()); i++) Thread.sleep(100);
    Thread.sleep(300);
    JDialog d = params[0];
    javax.swing.JTextArea prompt = (javax.swing.JTextArea) component(d, "params-field:--prompt");
    javax.swing.JButton select = (javax.swing.JButton) component(d, "params-prompt-file:--prompt");
    javax.swing.JButton db = (javax.swing.JButton) component(d, "params-prompt-db:--prompt");
    out.append("buttons: ").append(select == null ? "none" : select.getText()).append(", ").append(db == null ? "none" : db.getText() + (db.isEnabled() ? "" : " (greyed)"))
        .append(", --loras has them ").append(component(d, "params-prompt-file:--loras") != null).append('\n');
    // Browse DB: the shown list's own (greyed) button keeps it open for the test to pick from.
    // The last Browse DB showed pictures: the prompt's Browse DB still starts on T.
    DbFilter.current = "I";
    Object before = get("lastBrowse");
    answers.add("Result files (1)");
    SwingUtilities.invokeLater(db::doClick);
    for (int i = 0; i < 100 && get("lastBrowse") == before; i++) Thread.sleep(100);
    Thread.sleep(300);
    JDialog browser = (JDialog) get("lastBrowse");
    out.append("Browse DB: ").append(browser.getTitle()).append(", Answer Prompt 1.txt ").append(component(browser, "refs-pick:Answer Prompt 1.txt") != null)
        .append(", hit.wav ").append(component(browser, "refs-pick:hit.wav") != null).append(", starts on ").append(DbFilter.current).append('\n');
    edt(() -> ((javax.swing.JButton) component(browser, "refs-pick:Answer Prompt 1.txt")).doClick());
    out.append("prompt from DB: ").append(prompt.getText().replace("\n", "|")).append(", browser closed ").append(!browser.isShowing()).append('\n');
    // Select file: the watcher picks the prompt sheet in the file chooser.
    chooseNext = sheet;
    SwingUtilities.invokeLater(select::doClick);
    for (int i = 0; i < 100 && !prompt.getText().startsWith("A red"); i++) Thread.sleep(100);
    out.append("prompt from file: ").append(prompt.getText().replace("\n", "|")).append('\n');
    edt(() -> find(d.getContentPane(), "OK").doClick());
    opener.join(10000);
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText()).append('\n');
  }

  /**
   * The Prompts page's Ref files gallery: Sort by type, date or size opens it again in that order,
   * and the choice is kept (~/.pulsekit/file-sort.txt).
   */
  void s67_file_sort() throws Exception {
    File home = new File(System.getProperty("user.home"));
    PromptDb db = (PromptDb) get("promptDb");
    String[][] files = {{"notes.txt", "300"}, {"walk.mp4", "900"}, {"beat.wav", "100"}, {"sunset.png", "500"}, {"groove.mid", "700"}};
    for (String[] f : files) PromptDb.vault().addLibraryFile(f[0], new byte[Integer.parseInt(f[1])], "Imported", 1);
    java.util.function.Function<JDialog, String> order = g -> {
      StringBuilder sb = new StringBuilder();
      java.util.ArrayDeque<java.awt.Component> stack = new java.util.ArrayDeque<java.awt.Component>();
      stack.push(g.getContentPane());
      while (!stack.isEmpty()) {
        java.awt.Component c = stack.pop();
        if (c.getName() != null && c.getName().startsWith("gallery:") && !(c.getName().startsWith("gallery-"))) sb.append(c.getName().substring(8)).append(' ');
        if (c instanceof java.awt.Container) {
          java.awt.Component[] kids = ((java.awt.Container) c).getComponents();
          for (int i = kids.length - 1; i >= 0; i--) stack.push(kids[i]);
        }
      }
      return sb.toString().trim();
    };
    // The shown list's own (greyed) button keeps each gallery open for the test.
    Object before = get("lastGallery");
    answers.add("Ref files (5)");
    SwingUtilities.invokeLater(() -> db.gallery(false));
    for (int i = 0; i < 100 && get("lastGallery") == before; i++) Thread.sleep(100);
    Thread.sleep(400);
    JDialog g = (JDialog) get("lastGallery");
    javax.swing.JComboBox<?> sort = (javax.swing.JComboBox<?>) component(g, "gallery-sort");
    out.append("Sort by: ").append(sort == null ? "none" : sort.getSelectedItem() + " of " + sort.getItemCount()).append('\n');
    out.append("by date: ").append(order.apply(g)).append('\n');
    for (String choice : new String[] {"by type", "by size"}) {
      before = get("lastGallery");
      answers.add("Ref files (5)");
      final javax.swing.JComboBox<?> box = (javax.swing.JComboBox<?>) component(g, "gallery-sort");
      SwingUtilities.invokeLater(() -> box.setSelectedItem(choice));
      for (int i = 0; i < 100 && get("lastGallery") == before; i++) Thread.sleep(100);
      Thread.sleep(400);
      out.append("first closed ").append(!((JDialog) before).isShowing()).append(", ");
      g = (JDialog) get("lastGallery");
      out.append(choice).append(" (shows ").append(((javax.swing.JComboBox<?>) component(g, "gallery-sort")).getSelectedItem()).append("): ").append(order.apply(g)).append('\n');
    }
    final JDialog last = g;
    edt(last::dispose);
    out.append("kept: ").append(new String(Files.readAllBytes(new File(home, ".pulsekit/file-sort.txt").toPath()), StandardCharsets.UTF_8).trim()).append('\n');
  }

  /**
   * The Prompts page's Prompt field: Select file and Browse DB (text files, starting on T) fill it
   * from a text file; its menu (right click or a long press) has Select all, Cut, Copy, Paste.
   */
  void s68_prompt_page_file() throws Exception {
    File home = new File(System.getProperty("user.home"));
    PromptDb.vault().addLibraryFile("Answer Prompt 1.txt", "A studio portrait.\nSoft key light.\n".getBytes(StandardCharsets.UTF_8), "answer", 3);
    PromptDb.vault().addLibraryFile("hit.wav", AudioIo.encodeWav(new short[100], 22050), "Imported", 3);
    File notes = new File(home, "notes.txt");
    Files.write(notes.toPath(), "A red drum kit.\n".getBytes(StandardCharsets.UTF_8));
    call("showView", "prompts");
    PromptsPage page = (PromptsPage) get("promptsPage");
    PromptVault vault = PromptDb.vault();
    long id = vault.addPrompt(vault.mains().get(0).id, "Portrait");
    edt(() -> page.openPrompt(id));
    javax.swing.JButton select = (javax.swing.JButton) component(frame, "prompt-body-file");
    javax.swing.JButton db = (javax.swing.JButton) component(frame, "prompt-body-db");
    out.append("buttons: ").append(select == null ? "none" : select.getText()).append(", ").append(db == null ? "none" : db.getText() + (db.isEnabled() ? "" : " (greyed)")).append('\n');
    StringBuilder items = new StringBuilder();
    edt(() -> {
      for (java.awt.Component c : TextMenu.menu(page.promptBody).getComponents()) if (c instanceof javax.swing.JMenuItem) items.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
    });
    out.append("text menu: ").append(items).append(", attached ").append(page.promptBody.getMouseListeners().length > 1).append('\n');
    DbFilter.current = "I";
    Object before = get("lastBrowse");
    answers.add("Result files (1)");
    SwingUtilities.invokeLater(db::doClick);
    for (int i = 0; i < 100 && get("lastBrowse") == before; i++) Thread.sleep(100);
    Thread.sleep(300);
    JDialog browser = (JDialog) get("lastBrowse");
    out.append("Browse DB: starts on ").append(DbFilter.current).append(", Answer Prompt 1.txt ").append(component(browser, "refs-pick:Answer Prompt 1.txt") != null)
        .append(", hit.wav ").append(component(browser, "refs-pick:hit.wav") != null).append('\n');
    edt(() -> ((javax.swing.JButton) component(browser, "refs-pick:Answer Prompt 1.txt")).doClick());
    out.append("prompt from DB: ").append(page.promptBody.getText().replace("\n", "|")).append('\n');
    chooseNext = notes;
    SwingUtilities.invokeLater(select::doClick);
    for (int i = 0; i < 100 && !page.promptBody.getText().startsWith("A red"); i++) Thread.sleep(100);
    out.append("prompt from file: ").append(page.promptBody.getText()).append('\n');
    DbFilter.current = "A";
  }

  /**
   * Strip headers in a text file's menu in the Result files gallery (lines that start with
   * **Header:**): the text without them kept as <file> noheaders.txt; other files have no such item.
   */
  void s69_strip_headers() throws Exception {
    PromptDb db = (PromptDb) get("promptDb");
    String looks = "Here is the description:\n\n- **Age/Appearance:** She is a young adult.\n- **Hair:** She has long, straight, dark brown hair.\n**Face** An oval face.\n  * **Pose:** Centered.\n**Notes:**\nPlain line stays.\n";
    PromptDb.vault().addLibraryFile("looks.txt", looks.getBytes(StandardCharsets.UTF_8), "answer", 3);
    String chat = "SogniChat conversation\nModel: qwen\n\n=== You ===\nHow would you describe the woman in the reference file 1? Be precise.\n\nPicture a.webp is attached as media_ref_1.\n\n"
        + "=== Sogni ===\nBased on the reference image provided, the woman is depicted as follows:\n\n- **Age/Appearance:** She appears to be a young adult.\n- **Hair:** Her hair is dark, worn long.\n- **Background:** The background is blurred.\n\n=== You ===\nThanks\n\n=== Sogni ===\nYou are welcome.\n";
    PromptDb.vault().addLibraryFile("chat.txt", chat.getBytes(StandardCharsets.UTF_8), "answer", 3);
    PromptDb.vault().addLibraryFile("notes.txt", "Kick on 1.\n".getBytes(StandardCharsets.UTF_8), "Imported", 3);
    for (String n : new String[] {"looks.txt", "chat.txt", "notes.txt"}) {
      PromptVault.StoredFile file = null;
      for (PromptVault.StoredFile f : PromptDb.stored(true)) if (f.name.equals(n)) file = f;
      final PromptVault.StoredFile at = file;
      final StringBuilder seen = new StringBuilder();
      final boolean[] done = {false};
      inspectNext = gallery -> {
        javax.swing.JButton card = (javax.swing.JButton) component(gallery, "gallery:" + n);
        db.menu(at, card, () -> {});
        javax.swing.JMenuItem strip = null;
        for (java.awt.Component c : db.lastFileMenu.getComponents()) {
          if (!(c instanceof javax.swing.JMenuItem)) continue;
          seen.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
          if (((javax.swing.JMenuItem) c).getText().equals("Strip headers")) strip = (javax.swing.JMenuItem) c;
        }
        db.lastFileMenu.setVisible(false);
        if (strip != null) strip.doClick();
        done[0] = true;
      };
      SwingUtilities.invokeLater(() -> db.gallery(true));
      for (int i = 0; i < 100 && !done[0]; i++) Thread.sleep(100);
      idle();
      out.append(n).append(" menu: ").append(seen).append('\n');
      if (seen.indexOf("Strip headers") >= 0) out.append("  now: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    }
    for (PromptVault.StoredFile f : PromptDb.stored(true)) {
      if (!f.name.contains("noheaders")) continue;
      out.append("result file: ").append(f.name).append(": ").append(new String(PromptDb.vault().fileBytes(f.versionId, f.which), StandardCharsets.UTF_8).replace("\n", "|")).append('\n');
    }
  }

  /** A text file's Preview: its menu (right click or a long press) has only Select all and Copy; the Prompt field keeps Cut and Paste. */
  void s70_preview_text_menu() throws Exception {
    PromptDb db = (PromptDb) get("promptDb");
    final StringBuilder seen = new StringBuilder();
    final boolean[] done = {false};
    inspectNext = d -> {
      javax.swing.JTextArea text = (javax.swing.JTextArea) component(d, "preview-text");
      seen.append("preview menu: ");
      for (java.awt.Component c : TextMenu.menu(text).getComponents()) if (c instanceof javax.swing.JMenuItem) seen.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
      seen.append(", attached ").append(text.getMouseListeners().length > 1);
      done[0] = true;
    };
    answers.add("Close");
    SwingUtilities.invokeLater(() -> db.preview("looks.txt", "She appears to be a young adult.\n".getBytes(StandardCharsets.UTF_8)));
    for (int i = 0; i < 100 && !done[0]; i++) Thread.sleep(100);
    idle();
    out.append(seen).append('\n');
    javax.swing.JTextArea field = new javax.swing.JTextArea("x");
    StringBuilder editable = new StringBuilder();
    for (java.awt.Component c : TextMenu.menu(field).getComponents()) if (c instanceof javax.swing.JMenuItem) editable.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
    out.append("editable field menu: ").append(editable).append('\n');
  }

  /**
   * A Sogni run whose prompt library work is not over: another Sogni run asks first ("Are you
   * sure…"), only when that run stores in the library; Params and SogniChat never wait.
   */
  void s71_pending_db() throws Exception {
    call("selectListedProgram", "Java", "SogniTextVideo.java");
    idle();
    PyJav pj = (PyJav) get("pyJav");
    String plain = PendingOps.add("SogniVideo.java", java.util.Arrays.asList("/tmp/ref1-a.png"));
    out.append("pending: ").append(PendingOps.count()).append(", storing ").append(PendingOps.storingCount()).append('\n');
    out.append("no --saveprompt: Run waits ").append(pj.mustWait("SogniTextVideo.java", true)).append(", Params wait ").append(pj.mustWait("SogniTextVideo.java", false)).append('\n');
    String storing = PendingOps.add("SogniVideo.java", java.util.Arrays.asList("/tmp/ref1-a.png"), PendingOps.storesInDb(java.util.Arrays.asList("--saveprompt")));
    out.append("pending: ").append(PendingOps.count()).append(", storing ").append(PendingOps.storingCount()).append('\n');
    answers.add("No");
    out.append("--saveprompt, answered No: Run waits ").append(pj.mustWait("SogniTextVideo.java", true)).append('\n');
    answers.add("Yes");
    out.append("answered Yes: Run waits ").append(pj.mustWait("SogniTextVideo.java", true)).append('\n');
    out.append("Params wait: ").append(pj.mustWait("SogniTextVideo.java", false)).append('\n');
    out.append("SogniChat run waits: ").append(pj.mustWait("SogniChat.java", true)).append('\n');
    out.append("MidiDrumGen waits: ").append(pj.mustWait("MidiDrumGen.java", true)).append('\n');
    PendingOps.done(storing);
    PendingOps.done(plain);
    out.append("after done: pending ").append(PendingOps.count()).append(", Run waits ").append(pj.mustWait("SogniTextVideo.java", true)).append('\n');
  }

  /** File > General settings: Use encrypted DB unticked copies the library unencrypted; ticked again, the copy goes. */
  void s72_general_settings() throws Exception {
    File dir = PromptDb.dir();
    byte[] video = new byte[300 * 1024];
    new java.util.Random(3).nextBytes(video);
    PromptDb.vault().addLibraryFile("clip.mp4", video, "Imported", 3);
    java.util.function.Supplier<String> files = () -> {
      StringBuilder sb = new StringBuilder();
      File[] kids = dir.listFiles();
      java.util.Arrays.sort(kids);
      for (File k : kids) if (k.getName().startsWith("prompts")) sb.append(k.getName().replaceAll("[0-9a-f]{24}", "<hash>")).append(' ');
      return sb.toString().trim();
    };
    final javax.swing.JCheckBox[] box = new javax.swing.JCheckBox[1];
    inspectNext = d -> box[0] = (javax.swing.JCheckBox) component(d, "settings-encrypted");
    answers.add("Close");
    // Use encrypted DB is in Database settings now.
    SwingUtilities.invokeLater(() -> DbSettings.show((Pulsekit) frame));
    for (int i = 0; i < 100 && box[0] == null; i++) Thread.sleep(100);
    idle();
    out.append("Use encrypted DB: ").append(box[0] != null && box[0].isSelected()).append("; files: ").append(files.get()).append('\n');
    final javax.swing.JCheckBox b = new javax.swing.JCheckBox("Use encrypted DB", true);
    edt(() -> {
      b.setSelected(false);
      GeneralSettings.change((Pulsekit) frame, b, false);
    });
    for (int i = 0; i < 100 && !b.isEnabled(); i++) Thread.sleep(100);
    idle();
    out.append("unticked: encrypted ").append(PromptVault.encrypted(dir)).append("; files: ").append(files.get()).append('\n');
    PromptVault.StoredFile clip = null;
    for (PromptVault.StoredFile f : PromptDb.vault().resultFiles()) if (f.name.equals("clip.mp4")) clip = f;
    out.append("clip.mp4 in the unencrypted library: ").append(clip != null && java.util.Arrays.equals(PromptDb.vault().fileBytes(clip.versionId, clip.which), video)).append('\n');
    PromptDb.vault().addLibraryFile("note.txt", "added unencrypted".getBytes(StandardCharsets.UTF_8), "Imported", 1);
    edt(() -> {
      b.setSelected(true);
      GeneralSettings.change((Pulsekit) frame, b, true);
    });
    for (int i = 0; i < 100 && !b.isEnabled(); i++) Thread.sleep(100);
    Thread.sleep(200);
    idle();
    out.append("ticked: encrypted ").append(PromptVault.encrypted(dir)).append("; files: ").append(files.get()).append('\n');
    StringBuilder names = new StringBuilder();
    for (PromptVault.StoredFile f : PromptDb.vault().resultFiles()) names.append(f.name).append(' ');
    for (PromptVault.StoredFile f : PromptDb.vault().referenceFiles()) names.append(f.name).append(' ');
    out.append("encrypted library: ").append(names.toString().trim()).append('\n');
  }

  /** Choose file: the Media browser with Recent MB folder, Download and favourite tabs; a clicked file fills the row. */
  void s73_choose_file() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "media-choose");
    File sub = new File(media, "shots");
    sub.mkdirs();
    Files.write(new File(media, "a.png").toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
    Files.write(new File(sub, "notes.txt").toPath(), "hi".getBytes(StandardCharsets.UTF_8));
    MediaDir.remember(media.getAbsolutePath(), new ArrayList<String>());
    MediaBrowser.saveLoop();
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    final File[] picked = new File[1];
    final StringBuilder seen = new StringBuilder();
    java.util.function.Function<JDialog, String> tabs = d -> {
      StringBuilder sb = new StringBuilder();
      for (String t : new String[] {"recent", "download", "fav:0", "other"}) {
        AbstractButton b = (AbstractButton) component(d, "media-tab:" + t);
        if (b != null) sb.append('[').append(b.getText()).append(b.isSelected() ? " *" : "").append(']');
      }
      return sb.toString();
    };
    inspectNext = d -> {
      seen.append("opens on: ").append(mb.tab).append(", tabs ").append(tabs.apply(d)).append('\n');
      ((AbstractButton) component(d, "media-folder:shots")).doClick();
      seen.append("a text file is listed: ").append(component(d, "media-card:notes.txt") != null).append('\n');
      ((AbstractButton) component(d, "media-favourite")).doClick();
      seen.append("favourite added: ").append(MediaDir.favourites.size()).append(", tabs now ").append(tabs.apply(d)).append('\n');
      ((AbstractButton) component(d, "media-tab:download")).doClick();
      seen.append("Download tab: ").append(mb.tab).append(", Recent MB folder still ").append(MediaDir.lastRoot.equals(media.getAbsolutePath())).append('\n');
      ((AbstractButton) component(d, "media-tab:fav:0")).doClick();
      seen.append("favourite tab: ").append(mb.tab).append(", shows ").append(mb.shown.getName()).append('\n');
      ((AbstractButton) component(d, "media-card:notes.txt")).doClick();
    };
    SwingUtilities.invokeLater(() -> mb.choose(f -> picked[0] = f, () -> {}));
    for (int i = 0; i < 100 && picked[0] == null; i++) Thread.sleep(100);
    idle();
    out.append(seen);
    out.append("picked: ").append(picked[0] == null ? "none" : home.toPath().relativize(picked[0].toPath())).append('\n');
    MediaBrowser.loadLoop();
    out.append("kept favourites: ").append(MediaDir.favourites.size()).append('\n');
    // Choose file opens on the tab used last: the favourite, not the Recent MB folder.
    final StringBuilder again = new StringBuilder();
    inspectNext = d -> again.append("opens again on: ").append(mb.tab).append(", tabs ").append(tabs.apply(d)).append(", shows ").append(mb.shown.getName()).append('\n');
    SwingUtilities.invokeLater(() -> mb.choose(f -> {}, () -> {}));
    for (int i = 0; i < 50 && again.length() == 0; i++) Thread.sleep(100);
    idle();
    out.append(again);
    MediaDir.removeFavourite(0);
    out.append("favourite taken out, opens on: ").append(MediaDir.openTab(true)).append('\n');
  }

  /** ImageUpscaler from the Java menu: its Params, and runs with method 1 (sizes, shapes) and method 2 (no Real-ESRGAN here). */
  void s74_image_upscaler() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File pic = new File(home, "pic.png");
    java.awt.image.BufferedImage b = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < 30; y++) for (int x = 0; x < 40; x++) b.setRGB(x, y, x < 20 ? 0xff0000 : 0x0000ff);
    javax.imageio.ImageIO.write(b, "png", pic);
    call("selectListedProgram", "Java", "ImageUpscaler.java");
    idle();
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      for (String t : new String[] {"input.png", "--method", "--aspect", "--fit"}) {
        java.awt.Component c = component(d, "params-file:" + t);
        if (c == null) c = component(d, "params-field:" + t);
        seen.append("Params ").append(t).append(": ").append(c == null ? "none" : c.getClass().getSimpleName()).append('\n');
      }
    };
    answers.add("Cancel");
    call("openParams");
    idle();
    out.append(seen);
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    String[] runs = {"\"" + pic.getAbsolutePath() + "\"", "\"" + pic.getAbsolutePath() + "\" sq.jpg --width 100 --aspect 1:1", "\"" + pic.getAbsolutePath() + "\" big.png --method 2"};
    for (String extra : runs) {
      edt(() -> log.setText(""));
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
      edt(() -> call("runPython"));
      for (int i = 0; i < 300 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
      Thread.sleep(300);
      out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
      for (String line : String.valueOf(log.getText()).split("\n")) {
        if (line.startsWith("$ ") || line.startsWith("Picked up") || line.trim().isEmpty()) continue;
        out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
      }
    }
  }

  /** Media browser: a picture's menu has Upscale/resize image, which loads ImageUpscaler with it and opens its Params. */
  void s75_menu_upscale() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "media-up");
    media.mkdirs();
    File pic = new File(media, "a.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 3, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pic);
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    MediaDir.Entry e = null;
    for (MediaDir.Entry x : MediaBrowser.list(media)) if (x.name.equals("a.png")) e = x;
    out.append("picture menu: ").append(java.util.Arrays.toString(MediaDir.menu(false, e.kind))).append('\n');
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> seen.append("Params: ").append(d.getTitle()).append(", picture row ").append(component(d, "params-file:input.png") != null).append('\n');
    answers.add("Cancel");
    final MediaDir.Entry entry = e;
    edt(() -> out.append("menu: ").append(mb.menuPicked(entry, pic, MediaDir.UPSCALE_ITEM)).append('\n'));
    for (int i = 0; i < 50 && seen.length() == 0; i++) Thread.sleep(100);
    idle();
    out.append(seen);
    out.append("program: ").append((String) get("pyName")).append(", input ").append(new File((String) get("pyInputPath")).getName())
        .append(", args ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
  }

  /**
   * ImageUpscaler method 3 (Sogni AI upscale): the picture (cropped to the shape first) is uploaded,
   * upscale_image is started with the longest side wanted (at least 512 px on both sides), and Sogni's
   * picture is fitted to the size asked for. A size no larger than the picture does not use Sogni.
   */
  void s76_sogni_upscale() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File pic = new File(home, "pic.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pic);
    final List<String> starts = Collections.synchronizedList(new ArrayList<String>());
    final List<String> deletes = Collections.synchronizedList(new ArrayList<String>());
    final int[] made = new int[2];
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
      byte[] bytes;
      String type = "application/json";
      if (path.equals("/v1/creative-agent/workflows")) {
        starts.add(body);
        bytes = "{\"data\":{\"workflow\":{\"workflowId\":\"wu\",\"status\":\"queued\"}}}".getBytes(StandardCharsets.UTF_8);
      } else if (path.startsWith("/v1/image/uploadUrl")) {
        bytes = ("{\"uploadUrl\":\"http://127.0.0.1:" + port + "/put/1\"}").getBytes(StandardCharsets.UTF_8);
      } else if (path.startsWith("/v1/image/downloadUrl")) {
        bytes = ("{\"downloadUrl\":\"http://127.0.0.1:" + port + "/stored/1.png\"}").getBytes(StandardCharsets.UTF_8);
      } else if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        bytes = "data: {\"status\":\"completed\"}\n\n".getBytes(StandardCharsets.UTF_8);
      } else if (ex.getRequestMethod().equals("DELETE")) {
        deletes.add("DELETE " + path);
        bytes = "{}".getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/v1/creative-agent/workflows/wu")) {
        // The first run's record names its render (Sogni project); the second's names none.
        String project = starts.size() == 1 ? ",\"steps\":[{\"id\":\"upscale\",\"projectId\":\"2DBC4994-FC95-4840-A5A1-FE264242D6A2\"}]" : "";
        bytes = ("{\"data\":{\"workflow\":{\"workflowId\":\"wu\",\"status\":\"completed\"" + project + ",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/big.png\",\"mediaType\":\"image\"}]}}}").getBytes(StandardCharsets.UTF_8);
      } else if (path.equals("/files/big.png")) {
        // As RTX VSR: the last start's longest side, in the uploaded picture's shape (square for 1:1, else 4:3).
        String last = starts.get(starts.size() - 1);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"targetLongestEdge\":(\\d+)").matcher(last);
        int edge = m.find() ? Integer.parseInt(m.group(1)) : 80;
        made[0] = edge;
        made[1] = starts.size() == 1 ? edge : edge * 3 / 4;
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(made[0], made[1], java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        type = "image/png";
        bytes = png.toByteArray();
      } else {
        bytes = "{}".getBytes(StandardCharsets.UTF_8);
      }
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "ImageUpscaler.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        if (p.token.equals("--method") || p.token.startsWith("--key") || p.token.contains("cost") || p.token.equals("--unlimited"))
          out.append("param ").append(p.token).append(" \"").append(p.label).append("\"")
              .append(p.choices != null ? " " + java.util.Arrays.toString(p.choices) : "").append('\n');
      }
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      String sogni = " --method 3 --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port;
      String[] runs = {"\"" + pic.getAbsolutePath() + "\" sq.png --width 1024 --aspect 1:1 --delete_from_sogni" + sogni, "\"" + pic.getAbsolutePath() + "\" two.png --confirm_cost --max_cost 5 --delete_from_sogni" + sogni,
        "\"" + pic.getAbsolutePath() + "\" small.png --scale 0.5" + sogni};
      for (String extra : runs) {
        edt(() -> log.setText(""));
        edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(500);
        idle();
        out.append("== ").append(extra.replace(home.getAbsolutePath(), "~").replace(String.valueOf(port), "PORT")).append('\n');
        for (String line : textOf(log).split("\n")) {
          if (line.matches("(Read|Note|Uploaded|Sogni|Workflow|Method|Wrote|Succeeded|Failed|Deleted).*")) out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
        }
      }
      synchronized (starts) {
        for (String b : starts) {
          Map<?, ?> m = (Map<?, ?>) SogniApi.parseJson(b);
          Map<?, ?> step = (Map<?, ?>) ((List<?>) ((Map<?, ?>) m.get("input")).get("steps")).get(0);
          out.append("step: ").append(step.get("toolName")).append(' ').append(SogniApi.toJson(step.get("arguments")))
              .append(", references ").append(((List<?>) m.get("media_references")).size()).append('\n');
        }
      }
      synchronized (deletes) {
        for (String d : deletes) out.append(d).append('\n');
      }
      out.append("record saved: ").append(new File(home, ".pulsekit/sogni_workflow_record.txt").isFile()).append('\n');
      for (String n : new String[] {"sq.png", "two.png", "small.png"}) {
        File f = new File(home, ".pulsekit/" + n);
        java.awt.image.BufferedImage got = f.isFile() ? javax.imageio.ImageIO.read(f) : null;
        out.append(n).append(": ").append(got == null ? "missing" : got.getWidth() + " x " + got.getHeight()).append('\n');
      }
    } finally {
      server.stop(0);
    }
  }

  /**
   * ImageUpscaler from the Media browser: a copy in the picture's folder (--output_dir original), the
   * picture in DB (--addtodb), the key file from Drum Midi Settings, and Return back to the Media
   * browser. MediaBrowser.java's Run without a folder opens the one last opened.
   */
  void s77_upscaler_extras() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "shots-up");
    media.mkdirs();
    File pic = new File(media, "a.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pic);
    File key = new File(home, "key.txt");
    Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
    ApiKeys.init(PromptDb.dir());
    ApiKeys.store(Files.readAllBytes(key.toPath()));
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    MediaDir.Entry e = null;
    for (MediaDir.Entry x : MediaBrowser.list(media)) if (x.name.equals("a.png")) e = x;
    // As the browser shows the folder (the menu picks from it).
    set("shown", media);
    set("shownRoot", media);
    answers.add("Cancel");
    final MediaDir.Entry entry = e;
    edt(() -> mb.menuPicked(entry, pic, MediaDir.UPSCALE_ITEM));
    Thread.sleep(500);
    idle();
    javax.swing.JComponent ret = (javax.swing.JComponent) get("pyReturn");
    out.append("Return: ").append(ret.isVisible() ? "shown" : "hidden").append('\n');
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    javax.swing.text.JTextComponent extra = (javax.swing.text.JTextComponent) get("pyExtra");
    edt(() -> extra.setText(extra.getText() + " --output_dir original --addtodb"));
    edt(() -> log.setText(""));
    answers.add("Close");
    edt(() -> call("runPython"));
    for (int i = 0; i < 300 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
    Thread.sleep(800);
    idle();
    for (String line : textOf(log).split("\n")) {
      if (line.matches("(Read|Method|Wrote|Succeeded|Failed|Output folder|Add to DB|Prompt library|\\$ ).*"))
        out.append("log: ").append(line.replace(home.getAbsolutePath(), "~").replaceAll("--key_file \\S+", "--key_file <key>")).append('\n');
    }
    String[] names = media.list();
    java.util.Arrays.sort(names);
    out.append("picture's folder: ").append(String.join(", ", names)).append('\n');
    for (PromptVault.StoredFile f : PromptDb.files(false, null)) out.append("DB reference file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> seen.append("after Return: ").append(d.getTitle()).append(", lists a-upscaled.png ").append(component(d, "media-card:a-upscaled.png") != null).append('\n');
    answers.add("Close");
    SwingUtilities.invokeLater(() -> ((javax.swing.AbstractButton) ret).doClick());
    for (int i = 0; i < 50 && seen.length() == 0; i++) Thread.sleep(100);
    idle();
    out.append(seen).append("Return now: ").append(ret.isVisible() ? "shown" : "hidden").append('\n');
    // MediaBrowser.java: Run with no folder opens the folder the Media browser opened last.
    call("selectListedProgram", "Java", "MediaBrowser.java");
    idle();
    edt(() -> extra.setText(""));
    edt(() -> log.setText(""));
    final StringBuilder opened = new StringBuilder();
    inspectNext = d -> opened.append("MediaBrowser Run: ").append(d.getTitle().replace(home.getAbsolutePath(), "~")).append('\n');
    answers.add("Close");
    edt(() -> call("runPython"));
    for (int i = 0; i < 100 && opened.length() == 0; i++) Thread.sleep(100);
    idle();
    out.append(opened);
    ApiKeys.clear();
  }

  /** The Media browser's video player: Stop, Play and Pause shown, Mute and volume on a click of the picture, C (paused) saves the frame in SC. */
  void s78_video_capture() throws Exception {
    System.setProperty("pulsekit.vlc.args", "--aout=dummy");
    File home = new File(System.getProperty("user.home"));
    File clips = new File(home, "clips");
    clips.mkdirs();
    File clip = new File(clips, "clip.webm");
    Files.copy(new File(System.getProperty("pulsekit.test.dir", "."), "clip.webm").toPath(), clip.toPath());
    if (!VlcPlayer.available()) {
      out.append("no VLC: ").append(VlcPlayer.why()).append('\n');
      return;
    }
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    MediaDir.Entry e = null;
    for (MediaDir.Entry x : MediaBrowser.list(clips)) if (x.name.equals("clip.webm")) e = x;
    final MediaDir.Entry entry = e;
    // Answered (Fit) so the test's watcher leaves the player open.
    answers.add("Fit");
    SwingUtilities.invokeLater(() -> mb.openEntry(entry, clip));
    for (int i = 0; i < 100 && get("lastVideo") == null; i++) Thread.sleep(100);
    Thread.sleep(1500);
    idle();
    JDialog dialog = (JDialog) get("lastVideo");
    VlcPlayer player = (VlcPlayer) get("lastPlayer");
    java.util.function.Function<String, java.awt.Component> c = n -> component(dialog, n);
    out.append("buttons: ").append(c.apply("video-stop") != null).append(' ').append(c.apply("video-play") != null).append(' ').append(c.apply("video-pause") != null)
        .append(", sound row ").append(c.apply("video-sound").isVisible() ? "shown" : "hidden").append('\n');
    edt(() -> {
      java.awt.event.MouseEvent click = new java.awt.event.MouseEvent(player.screen, java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 10, 10, 1, false);
      for (java.awt.event.MouseListener l : player.screen.getMouseListeners()) l.mouseClicked(click);
    });
    out.append("clicked the picture: sound row ").append(c.apply("video-sound").isVisible() ? "shown" : "hidden").append('\n');
    edt(() -> ((javax.swing.AbstractButton) c.apply("video-play")).doClick());
    Thread.sleep(1000);
    out.append("playing: C ").append(c.apply("video-capture").isVisible() ? "shown" : "hidden");
    edt(() -> ((javax.swing.AbstractButton) c.apply("video-pause")).doClick());
    Thread.sleep(600);
    out.append(", paused: C ").append(c.apply("video-capture").isVisible() ? "shown" : "hidden").append('\n');
    edt(() -> ((javax.swing.AbstractButton) c.apply("video-capture")).doClick());
    File sc = new File(clips, MediaDir.FRAMES_DIR);
    String[] got = sc.list();
    out.append("SC folder: ").append(got == null ? "none" : got.length + " file(s), " + (got.length > 0 && got[0].matches("clip-0m0\\ds\\d{3}\\.png") ? "named clip-<time>.png" : String.join(",", got))).append('\n');
    if (got != null && got.length > 0) {
      java.awt.image.BufferedImage frame = javax.imageio.ImageIO.read(new File(sc, got[0]));
      out.append("frame: ").append(frame == null ? "unreadable" : frame.getWidth() + " x " + frame.getHeight()).append('\n');
    }
    edt(() -> ((javax.swing.JButton) find(dialog.getContentPane(), "Close")).doClick());
  }

  /**
   * Prompts: main categories as tabs in one row, "Add subcategory to <main>" puts it under that
   * main category. Media browser: a folder opens where it was last scrolled to. Full size: a picture
   * opens at the zoom the last one had.
   */
  void s79_categories_media_focus() throws Exception {
    File home = new File(System.getProperty("user.home"));
    PromptVault vault = PromptDb.vault();
    vault.addCategory("Alpha");
    vault.addCategory("Beta");
    call("showView", "prompts");
    idle();
    java.util.function.Consumer<String> addSub = name -> {
      inspectNext = d -> {
        JTextField field = textFieldIn(d.getContentPane());
        if (field != null) field.setText(name);
      };
      answers.add("OK");
      SwingUtilities.invokeLater(() -> ((javax.swing.AbstractButton) component(frame, "prompts-add-subcategory")).doClick());
      try {
        Thread.sleep(800);
        idle();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    edt(() -> ((javax.swing.AbstractButton) component(frame, "category:Alpha")).doClick());
    out.append("tabs: ").append(component(frame, "category-tabs") != null).append(", button: ").append(((javax.swing.AbstractButton) component(frame, "prompts-add-subcategory")).getText()).append('\n');
    addSub.accept("A1");
    edt(() -> ((javax.swing.AbstractButton) component(frame, "category:Beta")).doClick());
    out.append("Beta chosen: ").append(((javax.swing.AbstractButton) component(frame, "prompts-add-subcategory")).getText()).append('\n');
    addSub.accept("B1");
    for (PromptVault.Category c : vault.categories()) {
      if (c.parentId != 0) out.append("subcategory ").append(c.name).append(" under ").append(vault.category(c.parentId).name).append('\n');
    }
    out.append("shown: ").append(((JLabel) component(frame, "subcategories-of")).getText()).append(", B1 ").append(component(frame, "category:B1") != null)
        .append(", A1 ").append(component(frame, "category:A1") != null).append('\n');
    // Media browser: scrolled down, closed, opened again where it was.
    File big = new File(home, "big-folder");
    big.mkdirs();
    for (int k = 0; k < 120; k++) javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", new File(big, String.format("p%03d.png", k)));
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    final int[] at = new int[2];
    inspectNext = d -> {
      javax.swing.JScrollPane sp = scrollIn(d.getContentPane());
      sp.getVerticalScrollBar().setValue(900);
      at[0] = sp.getVerticalScrollBar().getValue();
    };
    answers.add("Close");
    SwingUtilities.invokeLater(() -> mb.open(big));
    for (int i = 0; i < 50 && at[0] == 0; i++) Thread.sleep(100);
    Thread.sleep(400);
    idle();
    inspectNext = d -> {
      try {
        Thread.sleep(300);
      } catch (InterruptedException ignored) {}
    };
    final javax.swing.JDialog[] second = new javax.swing.JDialog[1];
    inspectNext = d -> second[0] = d;
    answers.add("Close");
    SwingUtilities.invokeLater(() -> mb.open(big));
    for (int i = 0; i < 50 && second[0] == null; i++) Thread.sleep(100);
    idle();
    out.append("scrolled to ").append(at[0] > 0 ? "a place" : "the top").append(", opened again ").append(MediaDir.place(big.getAbsolutePath()) == at[0] ? "at that place" : "elsewhere (" + MediaDir.place(big.getAbsolutePath()) + ")").append('\n');
    // Full size: 156% kept for the next picture.
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(30, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", png);
    PromptDb db = (PromptDb) get("promptDb");
    inspectNext = d -> {
      javax.swing.AbstractButton plus = find(d.getContentPane(), "+");
      plus.doClick();
      plus.doClick();
    };
    answers.add("Close");
    SwingUtilities.invokeLater(() -> db.preview("one.png", png.toByteArray()));
    Thread.sleep(800);
    idle();
    final String[] shown = new String[1];
    inspectNext = d -> shown[0] = texts(d.getContentPane()).toString();
    answers.add("Close");
    SwingUtilities.invokeLater(() -> db.preview("two.png", png.toByteArray()));
    Thread.sleep(800);
    idle();
    out.append("next picture opens at ").append(shown[0] != null && shown[0].contains("156%") ? "156%" : shown[0]).append('\n');
  }

  /**
   * Browse prompts (Params' Prompt): category tabs, a subcategory, a prompt fills the field.
   * Prompts page Import: a sheet with its files (found beside it); again it is not added twice; a
   * file the library has is linked. Save this run into DB: the switch is taken off and the run stored.
   */
  /**
   * SogniMusic (stand-in Sogni serving a real MP3): its track is also kept as a WAV; with --test,
   * DrumMidi_CRT runs on the WAV with the tempo asked (and the switches for a mix, as bass was asked
   * for), then CompareHits, and the report starts with what the track was asked to be and the checks.
   */
  void s89_sogni_music_test() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final byte[] track = Files.readAllBytes(new File(System.getProperty("pulsekit.test.dir"), "funk.mp3").toPath());
    com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    final int port = server.getAddress().getPort();
    server.createContext("/", ex -> {
      String path = ex.getRequestURI().toString();
      readAll(ex.getRequestBody());
      byte[] reply;
      String type = "application/json";
      if (path.endsWith("/events/stream")) {
        type = "text/event-stream";
        reply = "data: {\"status\":\"running\"}\n\ndata: {\"status\":\"completed\"}\n\n".getBytes(StandardCharsets.UTF_8);
      } else if (path.startsWith("/files/")) {
        type = "audio/mpeg";
        reply = track;
      } else if (path.equals("/v1/creative-agent/workflows/wf9")) {
        reply = ("{\"data\":{\"workflow\":{\"workflowId\":\"wf9\",\"status\":\"completed\",\"artifacts\":[{\"url\":\"http://127.0.0.1:" + port
            + "/files/take.mp3\",\"mimeType\":\"audio/mpeg\"}]}}}").getBytes(StandardCharsets.UTF_8);
      } else {
        reply = "{\"data\":{\"workflow\":{\"workflowId\":\"wf9\",\"status\":\"queued\"}}}".getBytes(StandardCharsets.UTF_8);
      }
      ex.getResponseHeaders().set("Content-Type", type);
      ex.sendResponseHeaders(200, reply.length);
      ex.getResponseBody().write(reply);
      ex.close();
    });
    server.start();
    try {
      File key = new File(home, "sogni key.txt");
      Files.write(key.toPath(), "SOGNI_API_KEY=test-key\n".getBytes(StandardCharsets.UTF_8));
      call("showView", "py");
      call("selectListedProgram", "Java", "SogniMusic.java");
      for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
        if (p.token.equals("--no_wav") || p.token.equals("--test")) out.append("param ").append(p.token).append(" \"").append(p.label).append("\"\n");
      }
      final StringBuilder report = new StringBuilder();
      final Object[] look = new Object[1];
      look[0] = (java.util.function.Consumer<JDialog>) d -> {
        java.awt.Component c = component(d, "midi2wav-test-report");
        if (c != null) report.append(d.getTitle()).append('\n').append(((javax.swing.JTextArea) c).getText());
        else inspectNext = (java.util.function.Consumer<JDialog>) look[0];
      };
      @SuppressWarnings("unchecked")
      java.util.function.Consumer<JDialog> first = (java.util.function.Consumer<JDialog>) look[0];
      inspectNext = first;
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      edt(() -> log.setText(""));
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--genre Funk --prompt \"tight groove\" --instruments \"drums, bass\" --bpm 120 --duration 10 --test --key_file \""
          + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
      edt(() -> call("runPython"));
      for (int i = 0; i < 1500 && report.length() == 0; i++) Thread.sleep(100);
      idle();
      File wav = new File(home, ".pulsekit/sogni-Funk-wf9.wav");
      if (!wav.isFile()) wav = new File(home, "Downloads/sogni-Funk-wf9.wav");
      out.append("WAV kept: ").append(wav.isFile()).append(", ").append(String.format(java.util.Locale.ROOT, "%.1f s", wav.isFile() ? AudioIo.parseWav(Files.readAllBytes(wav.toPath())).samples.length / (double) AudioIo.parseWav(Files.readAllBytes(wav.toPath())).sr : 0)).append('\n');
      out.append("program now: ").append((String) get("pyName")).append('\n');
      for (String line : report.toString().replace(home.getAbsolutePath(), "~").split("\n")) {
        String t = line.trim();
        if (t.startsWith("SogniMusic test") || t.startsWith("==") || t.startsWith("Instruments:") || t.startsWith("Tempo:") || t.startsWith("Length:")
            || t.startsWith("Drum hits:") || t.startsWith("Drums with") || t.startsWith("DrumMidi runs") || t.startsWith("- ") || t.startsWith("Few") || t.startsWith("No kicks"))
          out.append("  ").append(t).append('\n');
      }
    } finally {
      server.stop(0);
    }
  }

  /** A small Hydrogen song: Verse (kick, snare, hats) three times, then Fill (toms), at 100 BPM. */
  static String h2song() {
    StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<song>\n <bpm>100</bpm>\n <name>Test song</name>\n <instrumentList>\n");
    int[][] inst = {{0, 36}, {2, 38}, {5, 41}, {6, 42}, {7, 43}};
    for (int[] i : inst) sb.append("  <instrument><id>").append(i[0]).append("</id><isMuted>false</isMuted><midiOutNote>").append(i[1]).append("</midiOutNote></instrument>\n");
    sb.append(" </instrumentList>\n <patternList>\n  <pattern><name>Verse</name><size>192</size><denominator>4</denominator><noteList>\n");
    for (int pos = 0; pos < 192; pos += 24) sb.append(h2note(pos, 6, 0.6));
    sb.append(h2note(0, 0, 0.9)).append(h2note(96, 0, 0.9)).append(h2note(48, 2, 0.9)).append(h2note(144, 2, 0.9));
    sb.append("  </noteList></pattern>\n  <pattern><name>Fill</name><size>192</size><denominator>4</denominator><noteList>\n");
    for (int pos = 0; pos < 192; pos += 24) sb.append(h2note(pos, pos < 96 ? 7 : 5, 0.8));
    sb.append("  </noteList></pattern>\n </patternList>\n <patternSequence>\n");
    for (String n : new String[] {"Verse", "Verse", "Verse", "Fill"}) sb.append("  <group><patternID>").append(n).append("</patternID></group>\n");
    return sb.append(" </patternSequence>\n</song>\n").toString();
  }

  static String h2note(int pos, int inst, double vel) {
    return "   <note><position>" + pos + "</position><velocity>" + vel + "</velocity><instrument>" + inst + "</instrument><note_off>false</note_off></note>\n";
  }

  /**
   * A small Reaper project at 100 BPM: a muted bass track, and a drum track whose item starts at
   * 2 s and loops its one bar (kick, snare, kick, snare) twice, plus a pooled copy at 10 s.
   */
  static String rpp() {
    StringBuilder ev = new StringBuilder();
    // One bar at 192 per quarter: kick 0, snare 192, kick 384, snare 576; source ends at 768.
    int[][] notes = {{0, 0x24}, {192, 0x26}, {384, 0x24}, {576, 0x26}};
    int at = 0;
    for (int[] n : notes) {
      ev.append("        E ").append(n[0] - at).append(" 99 ").append(Integer.toHexString(n[1])).append(" 64\n");
      ev.append("        E 48 89 ").append(Integer.toHexString(n[1])).append(" 00\n");
      at = n[0] + 48;
    }
    ev.append("        E ").append(768 - at).append(" b0 7b 00\n");
    return "<REAPER_PROJECT 0.1 \"6.46/win64\" 1\n  TEMPO 100 4 4\n  <TEMPOENVEX\n    ACT 0 -1\n  >\n"
        + "  <TRACK\n    NAME \"Bass\"\n    MUTESOLO 1 0 0\n    <ITEM\n      POSITION 0\n      LENGTH 10\n      LOOP 0\n      <SOURCE MIDI\n        HASDATA 1 192 QN\n        E 0 90 28 64\n        E 96 80 28 00\n        IGNTEMPO 0 120 4 4\n      >\n    >\n  >\n"
        + "  <TRACK\n    NAME \"Drums\"\n    <ITEM\n      POSITION 2\n      LENGTH 4.8\n      LOOP 1\n      <SOURCE MIDI\n        HASDATA 1 192 QN\n        POOLEDEVTS {POOL-1}\n"
        + ev + "        IGNTEMPO 0 120 4 4\n      >\n    >\n"
        + "    <ITEM\n      POSITION 10\n      LENGTH 2.4\n      LOOP 1\n      <SOURCE MIDI\n        HASDATA 1 192 QN\n        POOLEDEVTS {POOL-1}\n        IGNTEMPO 0 120 4 4\n      >\n    >\n  >\n>\n";
  }

  void s91_import_reaper() throws Exception {
    byte[] project = rpp().getBytes(StandardCharsets.UTF_8);
    Reaper.Result r = Reaper.toMidi(project);
    out.append(Reaper.summary(r)).append('\n');
    // Two loops of 4 hits from 2 s, one bar from 10 s; the muted bass is left out.
    if (r.notes != 12 || r.bpm != 100 || r.items != 2) throw new AssertionError("rpp read as " + Reaper.summary(r));
    double[][] hits = HitCompare.midiHits(r.midi);
    out.append(String.format(java.util.Locale.ROOT, "first kick %.2f s, kicks %d, snares %d%n", hits[0][0], hits[0].length, hits[1].length));
    if (Math.abs(hits[0][0] - 2.0) > 0.01 || hits[0].length != 6 || hits[1].length != 6 || Math.abs(hits[0][4] - 10.0) > 0.01) throw new AssertionError("rpp hits at the wrong times");
    answers.add("Yes");
    call("ingest", project, "Session.rpp");
    out.append("now ").append(((javax.swing.JLabel) get("nowPlaying")).getText()).append('\n');
    File f = new File(System.getProperty("java.io.tmpdir"), "Session.rpp");
    Files.write(f.toPath(), project);
    List<String> args = DrumFiles.midiArgs(java.util.Arrays.asList(f.getAbsolutePath()), new File(System.getProperty("java.io.tmpdir"), "rppmid"));
    out.append("program args ").append(new File(args.get(0)).getName()).append('\n');
    if (!args.get(0).endsWith("Session.mid")) throw new AssertionError("program got " + args);
    snap("after Reaper project");
  }

  void s90_import_hydrogen() throws Exception {
    byte[] song = h2song().getBytes(StandardCharsets.UTF_8);
    Hydrogen.Result r = Hydrogen.toMidi(song);
    out.append("h2song: ").append(Hydrogen.summary(r)).append('\n');
    if (r.bars != 4 || r.notes != 3 * 12 + 8 || r.bpm != 100) throw new AssertionError("h2song read as " + Hydrogen.summary(r));
    // A pattern file: GM kit notes (36 + instrument), no tempo of its own.
    String pat = "<drumkit_pattern><pattern_for_drumkit>GMkit</pattern_for_drumkit><pattern><pattern_name>Beat</pattern_name><size>192</size><noteList>"
        + h2note(0, 0, 0.9) + h2note(96, 2, 0.9) + h2note(120, 2, 0.0) + "</noteList></pattern></drumkit_pattern>";
    Hydrogen.Result pr = Hydrogen.toMidi(pat.getBytes(StandardCharsets.UTF_8));
    out.append("h2pattern: ").append(Hydrogen.summary(pr)).append('\n');
    if (pr.notes != 2 || pr.bpm != 0) throw new AssertionError("h2pattern read as " + Hydrogen.summary(pr));
    call("ingest", pat.getBytes(StandardCharsets.UTF_8), "Beat.h2pattern");
    int[][] cells = (int[][]) get("cells");
    out.append("pattern kick ").append(cells[0][0]).append(" snare ").append(cells[2][8]).append(" bpm ").append(call("bpm")).append('\n');
    if (cells[0][0] <= 0 || cells[2][8] <= 0) throw new AssertionError("Hydrogen pattern not in the grid");
    answers.add("Yes");
    call("ingest", song, "Test song.h2song");
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> imported = (List<Engine.ImportedSong>) get("importedSongs");
    Engine.ImportedSong made = imported.isEmpty() ? null : imported.get(0);
    out.append("song bpm ").append(call("bpm")).append(", imported song ").append(made == null ? "none" : made.name + " \u00b7 " + made.parts.size() + " parts").append('\n');
    if (made == null || made.parts.isEmpty()) throw new AssertionError("Hydrogen song made no song");
    // Given to a program: its MIDI instead.
    File h2 = new File(System.getProperty("java.io.tmpdir"), "Test song.h2song");
    Files.write(h2.toPath(), song);
    List<String> args = DrumFiles.midiArgs(java.util.Arrays.asList(h2.getAbsolutePath(), "--sf2", "kit.sf2"), new File(System.getProperty("java.io.tmpdir"), "h2mid"));
    out.append("program args ").append(new File(args.get(0)).getName()).append(' ').append(args.subList(1, args.size())).append('\n');
    Engine.MidiBars bars = Engine.parseMidiBars(Files.readAllBytes(new File(args.get(0)).toPath()));
    if (!args.get(0).endsWith("Test song.mid") || bars == null || bars.bars.size() != 4) throw new AssertionError("program got " + args);
    snap("after Hydrogen song");
  }

  /**
   * MidiDrumGen --towav: its MIDI goes on to Midi2Wav (a WAV with Pulsekit's kit); --test: on to
   * Midi2Wav's test, ending with the report (DrumMidi on the WAV, CompareHits against the groove).
   */
  void s88_drumgen_to_wav() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    idle();
    for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
      if (p.token.equals("--towav") || p.token.equals("--test")) out.append("param ").append(p.token).append(" \"").append(p.label).append("\"\n");
    }
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    // --towav: MidiDrumGen, then Midi2Wav (its WAV offered: Close).
    answers.add("Close");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style Rock --bars 2 --towav"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !"Midi2Wav.java".equals(get("pyName")); i++) Thread.sleep(50);
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Succeeded: "); i++) Thread.sleep(50);
    Thread.sleep(800);
    idle();
    out.append("--towav: program now ").append((String) get("pyName")).append('\n');
    for (String line : String.valueOf(log.getText()).split("\n")) {
      String t = line.trim();
      if (t.startsWith("MIDI:") || t.startsWith("SoundFont:") || t.startsWith("Wrote") || t.startsWith("Succeeded") || t.startsWith("Failed")) out.append("  ").append(t.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    answers.clear();
    // --test: MidiDrumGen, Midi2Wav, DrumMidi, CompareHits twice, the report.
    final StringBuilder report = new StringBuilder();
    final Object[] look = new Object[1];
    look[0] = (java.util.function.Consumer<JDialog>) d -> {
      java.awt.Component c = component(d, "midi2wav-test-report");
      if (c != null) report.append(((javax.swing.JTextArea) c).getText());
      else inspectNext = (java.util.function.Consumer<JDialog>) look[0];
    };
    @SuppressWarnings("unchecked")
    java.util.function.Consumer<JDialog> first = (java.util.function.Consumer<JDialog>) look[0];
    inspectNext = first;
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style Rock --bars 2 --test"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 1500 && report.length() == 0; i++) Thread.sleep(100);
    idle();
    out.append("--test: program now ").append((String) get("pyName")).append('\n');
    for (String line : report.toString().replace(home.getAbsolutePath(), "~").split("\n")) {
      String t = line.trim();
      if (t.startsWith("Midi2Wav test") || t.startsWith("==") || t.startsWith("Played:")) out.append("  ").append(t).append('\n');
    }
  }

  /**
   * Midi2Wav --test: the WAV made goes to DrumMidi_CRT (its MIDI imported as a file set and a song),
   * then CompareHits compares the WAV, DrumMidi's MIDI and the song, and the MIDI played against
   * DrumMidi's; the report is shown and kept beside the WAV.
   */
  void s87_midi2wav_test() throws Exception {
    File home = new File(System.getProperty("user.home"));
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    cells[2][4] = 100;
    cells[2][12] = 100;
    for (int i = 0; i < 16; i += 2) cells[5][i] = 100;
    // Four bars, so DrumMidi has a groove to hear.
    int[][] two = new int[12][32];
    for (int t = 0; t < 12; t++) for (int i = 0; i < 32; i++) two[t][i] = cells[t][i % 16];
    File midi = new File(home, "groove.mid");
    Files.write(midi.toPath(), Engine.encodeMidi(two, 120, 32));
    call("showView", "py");
    call("selectListedProgram", "Java", "Midi2Wav.java");
    idle();
    final StringBuilder report = new StringBuilder();
    // Watches every dialog until the report's (the MIDI synth notice and Make a song? come first).
    final Object[] look = new Object[1];
    look[0] = (java.util.function.Consumer<JDialog>) d -> {
      java.awt.Component c = component(d, "midi2wav-test-report");
      if (c != null) report.append(((javax.swing.JTextArea) c).getText());
      else inspectNext = (java.util.function.Consumer<JDialog>) look[0];
    };
    @SuppressWarnings("unchecked")
    java.util.function.Consumer<JDialog> first = (java.util.function.Consumer<JDialog>) look[0];
    inspectNext = first;
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("\"" + midi.getAbsolutePath() + "\" --test"));
    edt(() -> call("runPython"));
    long began = System.currentTimeMillis();
    for (int i = 0; i < 1050 && report.length() == 0; i++) Thread.sleep(100);
    System.err.println("s87 chain took " + (System.currentTimeMillis() - began) + " ms");
    if (report.length() == 0) out.append("NO REPORT; log:\n").append(log.getText()).append('\n');
    idle();
    out.append("program now: ").append((String) get("pyName")).append('\n');
    String text = report.toString().replace(home.getAbsolutePath(), "~");
    // The report's headings and its suggestions.
    for (String line : text.split("\n")) {
      String t = line.trim();
      if (t.startsWith("Midi2Wav test") || t.startsWith("==") || t.startsWith("- ") || t.startsWith("Played:") || t.toLowerCase().contains("match") || t.startsWith("Next run") || t.toLowerCase().startsWith("suggest") || t.startsWith("Kick") || t.startsWith("Snare") || t.startsWith("Cymbals"))
        out.append("  ").append(t).append('\n');
    }
    File saved = new File(home, ".pulsekit/groove2wav-test.txt");
    if (!saved.isFile()) saved = new File(home, "Downloads/groove2wav-test.txt");
    out.append("report kept: ").append(saved.isFile()).append(", report length ").append(text.length() > 200).append('\n');
  }

  /**
   * Midi2Wav (PyJav's Java menu): Params has the drum MIDI, the output name, the SoundFont, the rate
   * and Add to DB. Without a SoundFont it plays the MIDI with Pulsekit's kit, into <drums>2wav.wav;
   * with one and --addtodb the MIDI, the SoundFont and the WAV go into the prompt library.
   */
  void s92_midi2hydro_pattern() throws Exception {
    File home = new File(System.getProperty("user.home"));
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    cells[2][4] = 100;
    cells[2][12] = 100;
    for (int i = 0; i < 16; i += 2) cells[5][i] = 80;
    File one = new File(home, "beat.mid");
    Files.write(one.toPath(), Engine.encodeMidi(cells, 120));
    File folder = new File(home, "Patterns");
    new File(folder, "Funk").mkdirs();
    Files.write(new File(folder, "rock.mid").toPath(), Engine.encodeMidi(cells, 100));
    Files.write(new File(folder, "Funk/funk.mid").toPath(), Engine.encodeMidi(Engine.styleCells(Engine.styles().get("funk")), 96));
    Files.write(new File(folder, "notes.txt").toPath(), "not MIDI".getBytes(StandardCharsets.UTF_8));
    File outDir = new File(home, "h2");
    call("showView", "py");
    call("selectListedProgram", "Java", "Midi2HydroPattern.java");
    idle();
    for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
      out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.dir ? " folder" : "").append(p.choices != null ? " choices" : "").append('\n');
    }
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    String[] runs = {"\"" + one.getAbsolutePath() + "\" --category Tests", "--dir \"" + folder.getAbsolutePath() + "\" --recursive --outdir \"" + outDir.getAbsolutePath() + "\""};
    for (String extra : runs) {
      edt(() -> log.setText(""));
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
      answers.add("Close");
      edt(() -> call("runPython"));
      for (int i = 0; i < 400 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
      Thread.sleep(400);
      idle();
      out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
      for (String line : String.valueOf(log.getText()).split("\n")) {
        String t = line.trim();
        if (t.contains(".h2pattern") || t.startsWith("Succeeded") || t.startsWith("Failed") || t.contains("pattern")) out.append("  ").append(t.replace(home.getAbsolutePath(), "~")).append('\n');
      }
      answers.clear();
    }
    File single = new File(home, "beat.mid.h2pattern");
    File[] made = outDir.listFiles();
    if (!single.isFile() || made == null || made.length != 2) throw new AssertionError("patterns not written: " + single + " " + java.util.Arrays.toString(made));
    java.util.Arrays.sort(made);
    for (File f : made) out.append("made ").append(f.getName()).append('\n');
    // Read back by Pulsekit's own Hydrogen reader: the same hits as the MIDI.
    Hydrogen.Result r = Hydrogen.toMidi(Files.readAllBytes(single.toPath()));
    out.append("read back: ").append(Hydrogen.summary(r)).append('\n');
    if (r.notes != 4 + 2 + 8) throw new AssertionError("pattern has " + r.notes + " notes");
    String funk = new String(Files.readAllBytes(new File(outDir, "Funk - funk.mid.h2pattern").toPath()), StandardCharsets.UTF_8);
    if (!funk.contains("<category>Funk</category>") || !funk.contains("<pattern_name>funk</pattern_name>")) throw new AssertionError("subfolder category or name wrong");
    snap("after Midi2HydroPattern");
  }

  /**
   * The Sogni run fixes and the rest of the October list: a timed-out run joined again with
   * --workflow, the Running line, File > Downloads (Mark as ready, times), SogniMusic's test without
   * drums, one reference file row for one file, favourite categories, Hydrogen export, sizes.
   */
  /** SplitWav and CutWav with --dir: every WAV of a folder at once (no input file), parts to --outdir. */
  /**
   * vst_render.py (Python menu): Params name the MIDI, the plugin and effect (Choose plugin, the
   * system's chooser in the VST folders), their settings files, the rate and bits to pick from, Edit
   * and Scan. Without a plugin the run stops with a Failed line before DawDreamer is needed.
   */
  void s95_vst_render() throws Exception {
    call("showView", "py");
    call("selectListedProgram", "Python", "vst_render.py");
    idle();
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    for (ProgramParams.Param p : ProgramParams.parse(ProgramFiles.read("Python", "vst_render.py") == null ? "" : new String(ProgramFiles.read("Python", "vst_render.py"), StandardCharsets.UTF_8))) {
      out.append(p.token).append(": ").append(p.label).append(p.plugin ? " [plugin]" : "").append(p.isFile() ? " [file " + p.ext + "]" : "")
          .append(p.choices != null ? " " + String.join(" | ", p.choices) : "").append('\n');
    }
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      javax.swing.JButton plugin = (javax.swing.JButton) component(d, "params-file:--plugin");
      javax.swing.JButton effect = (javax.swing.JButton) component(d, "params-file:--effect");
      seen.append("plugin button: ").append(plugin == null ? "none" : plugin.getText()).append(", effect button: ").append(effect == null ? "none" : effect.getText()).append('\n');
      seen.append("edit switch: ").append(component(d, "params-check:--edit") != null).append('\n');
    };
    answers.add("Cancel");
    call("openParams");
    out.append(seen);
    File midi = new File(System.getProperty("user.home"), "song.mid");
    Files.write(midi.toPath(), Engine.encodeMidi(Engine.styleCells(Engine.styles().get("rock")), 110));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("\"" + midi.getPath() + "\""));
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (int i = 0; i < 400 && !String.valueOf(log.getText()).contains("Failed") && !String.valueOf(log.getText()).contains("Succeeded"); i++) Thread.sleep(50);
    for (String line : String.valueOf(log.getText()).split("\n")) if (line.startsWith("Failed")) out.append("run: ").append(line).append('\n');
    // --test: Pulsekit's DrumMidi_CRT.jar and Java are handed on (not twice, not without --test).
    File jar = new File(System.getProperty("user.home"), "tools/DrumMidi_CRT.jar");
    java.util.List<String> args = JavaRun.withDrumMidi("vst_render.py", java.util.Arrays.asList("song.mid", "--test"), jar, ProgramFiles.read("Java", "DrumMidi_CRT.jar"));
    out.append("test args: ").append(String.join(" ", args).replace(System.getProperty("user.home"), "~").replace(System.getProperty("java.home"), "<java.home>")).append(", jar ").append(jar.length() > 10000).append('\n');
    out.append("without --test: ").append(JavaRun.withDrumMidi("vst_render.py", java.util.Arrays.asList("song.mid"), jar, new byte[1])).append('\n');
    // --no-toms: DrumMidi's Params row, and a MIDI stamped toms=off leaves toms out of CompareHits.
    for (ProgramParams.Param p : ProgramParams.parse(new String(ProgramFiles.read("Java", "DrumMidi_CRT.java"), StandardCharsets.UTF_8))) {
      if (p.token.equals("--no-toms")) out.append("DrumMidi --no-toms: ").append(p.label).append(p.takesValue ? " (takes a value)" : " (switch)").append('\n');
    }
    java.util.Map<String, String> stamp = new java.util.HashMap<String, String>();
    stamp.put("toms", "off");
    double[][] hits = {{1.0}, {2.0}, {3.0}, {4.0}};
    out.append("toms=off: ").append(HitCompare.noToms(stamp)).append(", toms left ").append(HitCompare.withoutToms(hits, HitCompare.noToms(stamp))[HitCompare.TOM].length)
        .append(", kicks left ").append(HitCompare.withoutToms(hits, true)[HitCompare.KICK].length).append('\n');
  }

  void s94_wav_folder() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File folder = new File(home, "takes");
    new File(folder, "sub").mkdirs();
    short[] pcm = new short[22050 * 3];
    for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (8000 * Math.sin(i * 2 * Math.PI * 220 / 22050));
    byte[] wav = AudioIo.encodeWav(pcm, 22050);
    Files.write(new File(folder, "one.wav").toPath(), wav);
    Files.write(new File(folder, "two.wav").toPath(), wav);
    Files.write(new File(folder, "sub/three.wav").toPath(), wav);
    Files.write(new File(folder, "notes.txt").toPath(), "x".getBytes(StandardCharsets.UTF_8));
    File parts = new File(home, "parts");
    for (String[] run : new String[][] {{"SplitWav.java", "--dir \"" + folder.getPath() + "\" --split_time 0:01"},
        {"CutWav.java", "--dir \"" + folder.getPath() + "\" --recursive --outdir \"" + parts.getPath() + "\" --split_time 0:02"}}) {
      call("showView", "py");
      call("selectListedProgram", "Java", run[0]);
      idle();
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(run[1]));
      edt(() -> call("runPython"));
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      for (int i = 0; i < 400 && !(String.valueOf(log.getText()).contains(" WAV files done") || log.getText().startsWith("Failed")); i++) Thread.sleep(50);
      for (String line : String.valueOf(log.getText()).split("\n")) {
        if (line.startsWith("SplitWav:") || line.startsWith("CutWav:") || line.startsWith("Succeeded: ") && line.contains("WAV files") || line.startsWith("Failed")) {
          out.append(run[0]).append(": ").append(line.replace(home.getPath(), "~")).append('\n');
        }
      }
    }
    String[] made = folder.list();
    java.util.Arrays.sort(made);
    out.append("folder: ").append(String.join(" ", made)).append('\n');
    String[] cut = parts.isDirectory() ? parts.list() : new String[0];
    java.util.Arrays.sort(cut);
    out.append("outdir: ").append(String.join(" ", cut)).append('\n');
    // Params: Clear by the folder rows unsets them (one file is processed again).
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--dir \"" + folder.getPath() + "\" --outdir \"" + parts.getPath() + "\" song.wav"));
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      javax.swing.JButton clear = (javax.swing.JButton) component(d, "params-clear:--dir");
      seen.append("clear button: ").append(clear == null ? "none" : clear.getText()).append('\n');
      clear.doClick();
      seen.append("--dir row: ").append(((javax.swing.JLabel) component(d, "params-chosen:--dir")).getText()).append('\n');
    };
    answers.add("OK");
    call("openParams");
    out.append(seen).append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getPath(), "~")).append('\n');
  }

  void s93_runs_and_exports() throws Exception {
    java.util.List<String> argv = new java.util.ArrayList<String>(java.util.Arrays.asList("--prompt", "a walk", "/tmp/a.png"));
    String timedOut = "Uploaded a.png\nWorkflow: wf_42\nStatus: processing\nTimed out (4500s).";
    out.append("reconnect: ").append(JavaRun.reconnectArgs("SogniVideo.java", "", timedOut, argv)).append('\n');
    out.append("succeeded: ").append(JavaRun.reconnectArgs("SogniVideo.java", "", timedOut + "\nSucceeded: a.mp4", argv)).append('\n');
    out.append("network: ").append(JavaRun.reconnectArgs("SogniTextVideo.java", "", "Workflow: wf_7\nFailed: Unable to resolve host \"api\"", argv)).append('\n');
    out.append("refused: ").append(JavaRun.reconnectArgs("SogniVideo.java", "", "Workflow: wf_7\nFailed: blocked by the content filter", argv)).append('\n');
    out.append("run limits: SogniVideo ").append(JavaRun.runMs("SogniVideo.java", "", null) / 60000).append(" min, MidiDrumGen ")
        .append(JavaRun.runMs("MidiDrumGen.java", "", null) / 60000).append(" min\n");
    JavaRun.Live live = new JavaRun.Live();
    live.add("Workflow: wf_42\nStatus: queued\nQueued: position 3\n");
    out.append("running: ").append(JavaRun.runningLine(live, live.started + 125000)).append('\n');
    live.add("Status: processing\nProgress: 40%\n");
    out.append("running: ").append(JavaRun.runningLine(live, live.started + 3725000)).append('\n');
    live.add("Reconnecting: Sogni answered 503; again in 20 s\n");
    out.append("running: ").append(JavaRun.runningLine(live, live.started + 3726000)).append('\n');
    java.util.Map<String, Object> ev = new java.util.HashMap<String, Object>();
    java.util.Map<String, Object> data = new java.util.HashMap<String, Object>();
    data.put("progress", Double.valueOf(0.42));
    ev.put("data", data);
    out.append("progress notes: ").append(SogniApi.progressNote(ev, null)).append(" / ").append(SogniApi.progressNote(ev, "Progress: 40%")).append('\n');
    JavaRun.Live dm = new JavaRun.Live();
    dm.add("Loaded 240.0 s at 44100 Hz\nProgress: 25%\n");
    out.append("DrumMidi at 30 s: ").append(JavaRun.noHitsQuestion("DrumMidi_CRT.java", dm, dm.started + 30000)).append('\n');
    out.append("DrumMidi at 95 s: ").append(String.valueOf(JavaRun.noHitsQuestion("DrumMidi_CRT.java", dm, dm.started + 95000)).replace('\n', ' ')).append('\n');
    dm.add("Hits: 120\n");
    out.append("DrumMidi with hits: ").append(JavaRun.noHitsQuestion("DrumMidi_CRT.java", dm, dm.started + 95000)).append('\n');
    // File > Downloads: a run in progress, its time and why; Mark as ready lets it go.
    SogniDownloads.init(PromptDb.dir());
    long id = SogniDownloads.started("SogniTextVideo.java", System.currentTimeMillis() - 185000);
    out.append("in progress: ").append(SogniDownloads.runLabel(SogniDownloads.run(id)).replaceAll("\\d\\d-\\d\\d \\d\\d:\\d\\d", "DATE")).append('\n');
    SogniDownloads.reconnecting(id, "Reconnecting to workflow wf_42 (timed out after 1200 s; try 1 of 3)\u2026");
    out.append("reconnecting: ").append(SogniDownloads.reason(SogniDownloads.run(id))).append(", tab ").append(SogniDownloads.tabLabel(SogniDownloads.PROGRESS)).append('\n');
    SogniDownloads.markReady(id);
    out.append("marked ready: ").append(SogniDownloads.run(id).state).append(" ").append(SogniDownloads.run(id).status).append(", tab ").append(SogniDownloads.tabLabel(SogniDownloads.PROGRESS)).append('\n');
    // SogniMusic's test asks for drums.
    out.append("music test without drums: ").append(Midi2WavTest.drumsWarning("SogniMusic.java", java.util.Arrays.asList("--genre", "funk", "--test")) != null)
        .append(", with --drums_only ").append(Midi2WavTest.drumsWarning("SogniMusic.java", java.util.Arrays.asList("--drums_only", "--test")) != null)
        .append(", with drums in --instruments ").append(Midi2WavTest.drumsWarning("SogniMusic.java", java.util.Arrays.asList("--instruments", "Drums, bass", "--test")) != null).append('\n');
    // One reference file used by two prompts: one row.
    PromptVault vault = PromptDb.vault();
    long cat = vault.addCategory("Walks");
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    vault.addVersion(vault.addPrompt(cat, "Morning"), "Morning", "", "walk", "", "garden.png", png, "", null, "", null, "", "");
    vault.addVersion(vault.addPrompt(cat, "Evening"), "Evening", "", "walk at dusk", "", "garden.png", png, "", null, "", null, "", "");
    StringBuilder refs = new StringBuilder();
    for (PromptVault.StoredFile f : vault.referenceFiles()) refs.append(f.name).append(" (").append(f.shownTitle()).append(") ");
    out.append("reference files: ").append(refs.toString().trim()).append('\n');
    // Favourite categories.
    CategoryFavourites.load(PromptDb.dir());
    CategoryFavourites.toggle(cat);
    StringBuilder favs = new StringBuilder();
    for (PromptVault.Category c : CategoryFavourites.list(vault)) favs.append(CategoryFavourites.label(vault, c)).append(' ');
    out.append("favourites: ").append(favs.toString().trim()).append(", file ").append(new File(PromptDb.dir(), "favourite-categories.txt").isFile()).append('\n');
    CategoryFavourites.toggle(cat);
    out.append("after toggling again: ").append(CategoryFavourites.list(vault).size()).append('\n');
    // Hydrogen export: the beat as a pattern and as a song, read back.
    int[][] cells = new int[Engine.TRACK_ID.length][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    for (int i = 4; i < 16; i += 8) cells[1][i] = 100;
    byte[] midi = Engine.encodeMidi(cells, 120, 16, 4, 4);
    byte[] pattern = HydrogenWrite.pattern(midi, "Groove", "Pulsekit");
    byte[] song = HydrogenWrite.song(Engine.encodeSongMidi(java.util.Arrays.asList(Engine.groove("Groove", 120, cells, 4))), "Groove song");
    out.append("h2pattern: ").append(Hydrogen.summary(Hydrogen.toMidi(pattern))).append('\n');
    out.append("h2song: ").append(Hydrogen.summary(Hydrogen.toMidi(song))).append(", patterns ").append(new String(song, "UTF-8").split("<pattern>").length - 1).append('\n');
    out.append("sizes: ").append(MediaSize.label(1920, 1080)).append(", ").append(MediaSize.label(1080, 1920)).append(", ").append(MediaSize.label(1998, 1080)).append('\n');
  }

  void s86_midi2wav() throws Exception {
    File home = new File(System.getProperty("user.home"));
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    cells[2][4] = 100;
    cells[2][12] = 100;
    for (int i = 0; i < 16; i += 2) cells[5][i] = 100;
    File midi = new File(home, "groove.mid");
    Files.write(midi.toPath(), Engine.encodeMidi(cells, 120));
    File sf2 = new File(home, "test-kit.sf2");
    Files.write(sf2.toPath(), AudioIo.encodeSf2(AudioIo.buildVoices(22050), "Test kit"));
    call("showView", "py");
    call("selectListedProgram", "Java", "Midi2Wav.java");
    idle();
    for (ProgramParams.Param p : ProgramParams.parse((String) call("programText"))) {
      out.append("param ").append(p.token).append(" \"").append(p.label).append("\"").append(p.isFile() ? " file ." + p.ext : "").append(p.refs ? " Browse DB" : "")
          .append(p.choices != null ? " choices" : "").append(p.hint.length() > 0 ? " hint: " + p.hint : "").append('\n');
    }
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    String[] runs = {"\"" + midi.getAbsolutePath() + "\"", "\"" + midi.getAbsolutePath() + "\" kept.wav --sf2 \"" + sf2.getAbsolutePath() + "\" --rate 22050 --addtodb"};
    for (String extra : runs) {
      edt(() -> log.setText(""));
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
      answers.add("Close");
      edt(() -> call("runPython"));
      for (int i = 0; i < 400 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
      Thread.sleep(600);
      idle();
      out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
      for (String line : String.valueOf(log.getText()).split("\n")) {
        String t = line.trim();
        if (t.startsWith("MIDI:") || t.startsWith("SoundFont:") || t.startsWith("Wrote") || t.startsWith("Succeeded") || t.startsWith("Failed") || t.startsWith("Note") || t.startsWith("Saved") || t.startsWith("Prompt library"))
          out.append("  ").append(t.replace(home.getAbsolutePath(), "~")).append('\n');
      }
      answers.clear();
    }
    PromptVault vault = PromptDb.vault();
    for (PromptVault.Category c : vault.categories()) {
      for (PromptVault.Prompt p : vault.prompts(c.id)) {
        PromptVault.Version v = vault.finalVersion(p.id);
        out.append("library ").append(c.name).append(" / ").append(p.title).append(": refs ").append(v.ref1Name).append(", ").append(v.ref2Name)
            .append(", result ").append(v.resultName).append(" (").append(vault.sizeOf(v, 3) > 44 ? "has audio" : "empty").append(")\n");
      }
    }
  }

  /**
   * File > Downloads: "No downloads yet" at first; a Sogni run is listed (failed: no key), and a
   * finished run's files are listed by program in Finished / failed. Rename, Copy to, Move to,
   * Clear and Delete (with its prompt file and prompt library entry) work on a file.
   */
  void s85_downloads() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final StringBuilder seen = new StringBuilder();
    java.util.function.Function<JDialog, String> listing = d -> {
      StringBuilder sb = new StringBuilder();
      java.util.List<Component> all = new java.util.ArrayList<Component>();
      java.util.ArrayDeque<Component> todo = new java.util.ArrayDeque<Component>();
      todo.add(d.getContentPane());
      while (!todo.isEmpty()) {
        Component c = todo.poll();
        if (c.getName() != null && c.getName().startsWith("downloads-")) all.add(c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) todo.add(k);
      }
      for (Component c : all) {
        String t = textOf(c);
        sb.append("  ").append(c.getName().replaceAll("downloads-run:\\d+", "downloads-run")).append(t == null || t.isEmpty() ? "" : " \"" + t.replace(home.getAbsolutePath(), "~").replaceAll("\\d\\d-\\d\\d \\d\\d:\\d\\d", "<when>") + "\"").append('\n');
      }
      return sb.toString();
    };
    inspectNext = d -> seen.append("empty:\n").append(listing.apply(d));
    answers.add("Close");
    SwingUtilities.invokeLater(() -> DownloadsScreen.show((Pulsekit) frame));
    for (int i = 0; i < 50 && seen.length() == 0; i++) Thread.sleep(100);
    idle();
    out.append(seen);
    // A Sogni run through PyJav: SogniMusic with no key fails; its prompt sheet is saved.
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniMusic.java");
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--drums_only --saveprompt --key_file \"" + new File(home, "none.txt").getAbsolutePath() + "\""));
    edt(() -> call("runPython"));
    for (int i = 0; i < 300 && !String.valueOf(log.getText()).contains("Prompt library"); i++) Thread.sleep(50);
    Thread.sleep(300);
    idle();
    // A finished run: its picture and answer in Downloads, its sheet stored in the library.
    File dl = new File(home, "Downloads");
    dl.mkdirs();
    File pic = new File(dl, "sogni-pedit-smile.png");
    File txt = new File(dl, "answer.txt");
    File sheet = new File(dl, "sogni-pedit-smile.prompt");
    Files.write(pic.toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    Files.write(txt.toPath(), "An answer".getBytes(StandardCharsets.UTF_8));
    Files.write(sheet.toPath(), PromptRun.encode("Smile", "", "", "", "", "Image", "1", "Krea", "sogni-pedit-smile.png", "ai", "Smile").getBytes(StandardCharsets.UTF_8));
    PromptVault vault = PromptDb.vault();
    long cat = vault.addCategory("Image");
    long prompt = vault.addPrompt(cat, "Smile");
    long version = vault.addVersion(prompt, "Smile", "", "Smile", "Krea", "", null, "", null, "sogni-pedit-smile.png", null, "ai", "");
    SogniDownloads.init(PromptDb.dir());
    long run = SogniDownloads.started("SogniPedit.java", System.currentTimeMillis());
    SogniDownloads.result(run, "Succeeded: sogni-pedit-smile.png", "Saved " + pic.getAbsolutePath() + "\nSaved " + txt.getAbsolutePath() + "\nSaved " + sheet.getAbsolutePath(), null);
    SogniDownloads.stored(run, version);
    SogniDownloads.done(run);
    seen.setLength(0);
    DownloadsScreen.tab = "";
    inspectNext = d -> seen.append("after runs:\n").append(listing.apply(d));
    answers.add("Close");
    SwingUtilities.invokeLater(() -> DownloadsScreen.show((Pulsekit) frame));
    for (int i = 0; i < 50 && seen.length() == 0; i++) Thread.sleep(100);
    idle();
    out.append(seen);
    SogniDownloads.Run r = SogniDownloads.run(run);
    out.append("menu: ").append(java.util.Arrays.toString(DownloadsScreen.MENU)).append('\n');
    SogniDownloads.Item picItem = r.items.get(0);
    SogniDownloads.Item txtItem = r.items.get(1);
    out.append("rename: ").append(DownloadsScreen.rename(picItem, "smile.png")).append(", now ").append(picItem.where.replace(home.getAbsolutePath(), "~")).append('\n');
    File other = new File(home, "elsewhere");
    other.mkdirs();
    out.append("copy: ").append(DownloadsScreen.put(r, txtItem, other, false).replace(home.getAbsolutePath(), "~")).append(", still at ").append(txtItem.where.replace(home.getAbsolutePath(), "~")).append('\n');
    out.append("move: ").append(DownloadsScreen.put(r, txtItem, other, true).replace(home.getAbsolutePath(), "~")).append(", now ").append(txtItem.where.replace(home.getAbsolutePath(), "~"))
        .append(", gone from Downloads ").append(!txt.exists()).append('\n');
    out.append("clear: ").append(DownloadsScreen.clear(r, txtItem)).append(", file stays ").append(new File(txtItem.where).exists()).append('\n');
    out.append("delete: ").append(DownloadsScreen.delete(r, picItem, true)).append('\n');
    out.append("  picture gone ").append(!new File(home, "Downloads/smile.png").exists()).append(", sheet gone ").append(!sheet.exists())
        .append(", library version gone ").append(PromptDb.vault().version(version) == null).append(", run listed ").append(SogniDownloads.run(run) != null).append('\n');
    // Kept: read back as the app starts again.
    out.append("kept runs: ").append(SogniDownloads.count()).append('\n');
  }

  /**
   * SogniChat: Params' Chat model has a note, image analyze mode for a DeepSeek vision model and
   * tools mode otherwise; a run says the mode, and a DeepSeek run leaves the tools out.
   */
  void s84_chat_modes() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniChat.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--prompt hi"));
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      JTextField model = (JTextField) component(d, "params-field:--model");
      JLabel note = (JLabel) component(d, "params-note:--model");
      seen.append("default model: ").append(note == null ? "no note" : note.getText()).append('\n');
      model.setText("deepseek-v4-flash-vision-exp-dspark-1m");
      seen.append("deepseek: ").append(note == null ? "no note" : note.getText()).append('\n');
    };
    answers.add("Cancel");
    call("openParams");
    idle();
    out.append(seen);
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    String none = "--key_file \"" + new File(home, "none.txt").getAbsolutePath() + "\"";
    for (String extra : new String[] {"--prompt hi --model deepseek-v4-flash-vision-exp-dspark-1m --tools " + none, "--prompt hi " + none, "--prompt hi --tools " + none}) {
      edt(() -> log.setText(""));
      edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(extra));
      edt(() -> call("runPython"));
      for (int i = 0; i < 300 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
      Thread.sleep(300);
      idle();
      out.append("== ").append(extra.replace(none, "")).append('\n');
      for (String line : String.valueOf(log.getText()).split("\n")) {
        String t = line.trim();
        if (t.startsWith("Image analyze") || t.startsWith("Tools mode") || t.startsWith("Note:")) out.append("  ").append(t).append('\n');
      }
    }
  }

  /**
   * --saveprompt with Save prompt (General settings: Ask where to save): a changed sheet asks for the
   * main category, subcategory, name and description and is saved as the prompt's next version with
   * the date (in the library and on disk); the same sheet again is not saved; Cancel saves nothing.
   */
  void s83_save_prompt() throws Exception {
    File home = new File(System.getProperty("user.home"));
    PromptSave.setAsk(true);
    PromptVault vault = PromptDb.vault();
    long image = vault.addCategory("Image");
    vault.addSubcategory(image, "Portraits");
    String today = PromptSave.today();
    String[] prompts = {"Same person, smiling", "Same person, smiling", "Same person, laughing", "Same person, frowning"};
    for (int run = 0; run < prompts.length; run++) {
      String sheet = "PKPROMPT1\nsogni-pedit-same-person\n\n\n\n\nCategory: image\nModel: Sogni Krea 2 Identity Edit\nReference file 1: \nReference file 2: \nType: ai\n---\n"
          + prompts[run] + "\n\nLoRAs: none. Picture: me.png.\n\nResult file: sogni-pedit-same-person.png\n";
      java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
      files.add(new JavaRun.FileOut("sogni-pedit-same-person.prompt", sheet.getBytes(StandardCharsets.UTF_8)));
      files.add(new JavaRun.FileOut("sogni-pedit-same-person.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2}));
      final JavaRun.Result r = new JavaRun.Result("Succeeded: sogni-pedit-same-person.png", files, 0);
      final int at = run;
      final StringBuilder seen = new StringBuilder();
      inspectNext = d -> {
        seen.append("Save prompt: ").append(d.getTitle()).append(", recent ").append(component(d, "save-prompt-recent:Image \u203a Portraits") != null)
            .append(", main ").append(((javax.swing.JComboBox<?>) component(d, "save-prompt-main")).getEditor().getItem())
            .append(", sub ").append(((javax.swing.JComboBox<?>) component(d, "save-prompt-sub")).getEditor().getItem())
            .append(", name ").append(((JTextField) component(d, "save-prompt-name")).getText())
            .append(", ").append(((JLabel) component(d, "save-prompt-version")).getText().replace(today, "<today>")).append('\n');
        if (at == 0) {
          ((javax.swing.JComboBox<?>) component(d, "save-prompt-sub")).getEditor().setItem("Portraits");
          ((javax.swing.JTextArea) component(d, "save-prompt-description")).setText("First try");
        }
        ((JTextField) component(d, "save-prompt-name")).setText("Smile");
        // The third run's version goes into the favourite prompts.
        javax.swing.JCheckBox fav = (javax.swing.JCheckBox) component(d, "save-prompt-favourite");
        seen.append("  Add to favourites box: ").append(fav != null).append('\n');
        if (at == 2) fav.setSelected(true);
      };
      answers.add(run == 3 ? "Cancel" : "OK");
      String kept = (String) call("saveSheets", PromptDb.dir(), r, new java.util.ArrayList<String>());
      idle();
      out.append("== run ").append(run + 1).append(": ").append(prompts[run]).append('\n').append(seen.length() == 0 ? "no Save prompt\n" : seen);
      for (String line : kept.split("\n")) out.append("  log: ").append(line.replace(today, "<today>").replace(home.getAbsolutePath(), "~")).append('\n');
      answers.clear();
      inspectNext = null;
    }
    vault = PromptDb.vault();
    for (PromptVault.Prompt p : vault.prompts(PromptSave.find(vault, "Image", "Portraits"))) {
      java.util.List<PromptVault.Version> rows = vault.versions(p.id);
      out.append("library Image \u203a Portraits: ").append(p.title).append(", ").append(rows.size()).append(" versions\n");
      for (int i = 0; i < rows.size(); i++) out.append("  v").append(rows.size() - i).append(": ").append(rows.get(i).body.split("\n")[0]).append(" | ").append(rows.get(i).description).append('\n');
    }
    File[] disk = new File(home, new File(home, "Downloads").isDirectory() ? "Downloads" : ".pulsekit").listFiles();
    if (disk != null) {
      java.util.Arrays.sort(disk);
      for (File f : disk) {
        if (!f.getName().endsWith(".prompt")) continue;
        PromptRun.Sheet sheet = PromptRun.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        out.append("disk ").append(f.getName()).append(": ").append(sheet.name).append(", ").append(sheet.category).append(" \u203a ").append(sheet.subcategory)
            .append(", version ").append(sheet.version).append(", date ").append(sheet.date.replace(today, "<today>")).append(", description ").append(sheet.description)
            .append(", prompt ").append(sheet.body.split("\n")[0]).append('\n');
      }
    }
    // Favourite prompts: the version saved with Add to favourites, on the Prompts page and in Browse prompts.
    VersionFavourites.load(PromptDb.dir());
    StringBuilder favs = new StringBuilder();
    for (PromptVault.Version v : VersionFavourites.list(vault)) favs.append('[').append(VersionFavourites.label(vault, v)).append(']');
    out.append("favourite prompts: ").append(favs).append('\n');
    call("showView", "prompts");
    idle();
    out.append("Prompts page lists it: ").append(component(frame.getContentPane(), "prompts-favourite:Smile \u00b7 v2") != null).append('\n');
    final String[] handed = new String[1];
    final StringBuilder browse = new StringBuilder();
    inspectNext = d -> {
      javax.swing.JButton item = (javax.swing.JButton) component(d, "prompt-browser-favprompt:Smile \u00b7 v2");
      browse.append("Browse prompts lists it: ").append(item != null).append('\n');
      if (item != null) item.doClick();
    };
    SwingUtilities.invokeLater(() -> PromptBrowser.show((Pulsekit) frame, (title, text) -> handed[0] = title + ": " + text.split("\n")[0]));
    for (int i = 0; i < 50 && handed[0] == null; i++) Thread.sleep(100);
    idle();
    out.append(browse).append("clicked: ").append(handed[0]).append('\n');
    if (favs.indexOf("[Smile \u00b7 v2]") < 0 || handed[0] == null || !handed[0].contains("laughing")) throw new AssertionError(out.toString());
  }

  /**
   * Recent: each program once. A click runs it without parameters when such a run is remembered,
   * else with its last ones; a right click lists its runs, and a click on one runs it with those.
   */
  void s82_recent_programs() throws Exception {
    File dir = new File(System.getProperty("user.home"), ".pulsekit");
    dir.mkdirs();
    PyJavRecent.remember(dir, "CompareHits.java", "a.wav b.mid", "", null);
    PyJavRecent.remember(dir, "ImageUpscaler.java", "pic.png --scale 2", "", null);
    PyJavRecent.remember(dir, "CompareHits.java", "", "", null);
    PyJavRecent.remember(dir, "CompareHits.java", "c.wav d.mid", "", null);
    call("showView", "py");
    javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) call("recentMenu");
    StringBuilder rows = new StringBuilder();
    for (Component c : menu.getComponents()) if (c instanceof javax.swing.JMenuItem) rows.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
    out.append("menu: ").append(rows).append('\n');
    javax.swing.JPopupMenu runs = (javax.swing.JPopupMenu) call("runsMenu", "CompareHits.java");
    StringBuilder labels = new StringBuilder();
    for (Component c : runs.getComponents()) if (c instanceof javax.swing.JMenuItem) labels.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
    out.append("CompareHits runs: ").append(labels).append('\n');
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    String[][] picks = {{"run", "2"}, {"name", "CompareHits.java"}, {"name", "ImageUpscaler.java"}};
    for (String[] pick : picks) {
      edt(() -> log.setText(""));
      if (pick[0].equals("run")) {
        javax.swing.JPopupMenu m = (javax.swing.JPopupMenu) call("runsMenu", "CompareHits.java");
        edt(() -> ((javax.swing.JMenuItem) m.getComponent(Integer.parseInt(pick[1]))).doClick());
      } else {
        javax.swing.JPopupMenu m = (javax.swing.JPopupMenu) call("recentMenu");
        for (Component c : m.getComponents()) {
          if (c instanceof javax.swing.JMenuItem && pick[1].equals(((javax.swing.JMenuItem) c).getText())) edt(() -> ((javax.swing.JMenuItem) c).doClick());
        }
      }
      for (int i = 0; i < 200 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed") || String.valueOf(log.getText()).contains("exit")); i++) Thread.sleep(50);
      Thread.sleep(300);
      idle();
      out.append(pick[0]).append(' ').append(pick[1]).append(": ").append((String) get("pyName")).append(", args [").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText()).append("], ran ").append(!log.getText().equals("")).append('\n');
    }
  }

  /**
   * The Prompts editor's Save: the version shown keeps its number (no new one), with the edited
   * text and its file. Then the favourite prompts' menu: Move down, Remove from favourites.
   */
  void s99_save_in_place_favourite_menu() throws Exception {
    PromptVault vault = PromptDb.vault();
    long cat = vault.categories().get(0).id;
    long id = vault.addPrompt(cat, "Portrait");
    byte[] pic = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};
    long v1 = vault.addVersion(id, "Portrait", "", "Soft light", "", "face.png", pic, "", null, "", null, "ai", "");
    long v2 = vault.addVersion(id, "Portrait", "", "Hard light", "", "", null, "", null, "", null, "ai", "");
    PromptsPage page = (PromptsPage) get("promptsPage");
    call("showView", "prompts");
    edt(() -> {
      page.openPrompt(id);
      page.loadVersion(v1);
    });
    out.append("buttons: ").append(((javax.swing.JButton) component(frame, "prompt-save-here")).getText()).append(", ").append(((javax.swing.JButton) component(frame, "prompt-save")).getText()).append('\n');
    edt(() -> {
      page.promptBody.setText("Soft light, smiling");
      ((javax.swing.JButton) component(frame, "prompt-save-here")).doClick();
    });
    vault = PromptDb.vault();
    out.append("status: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    out.append("after Save: ").append(vault.versions(id).size()).append(" versions, v1 \"").append(vault.version(v1).body).append("\" with ").append(vault.version(v1).ref1Name)
        .append(" (").append(vault.bytesOf(vault.version(v1), 1).length).append(" bytes), v2 \"").append(vault.version(v2).body).append("\"\n");
    if (vault.versions(id).size() != 2 || !vault.version(v1).body.equals("Soft light, smiling") || vault.bytesOf(vault.version(v1), 1).length != pic.length) throw new AssertionError(out.toString());
    VersionFavourites.load(PromptDb.dir());
    VersionFavourites.add(v1);
    VersionFavourites.add(v2);
    final PromptVault vv = vault;
    java.util.function.Supplier<String> order = () -> {
      StringBuilder o = new StringBuilder();
      for (PromptVault.Version v : VersionFavourites.list(vv)) o.append('[').append(VersionFavourites.label(vv, v)).append(']');
      return o.toString();
    };
    edt(() -> {
      page.promptId = 0;
      page.rebuild();
    });
    out.append("favourites: ").append(order.get()).append('\n');
    // Collapsed like Select program: its row opens the list.
    final javax.swing.JButton favToggle = (javax.swing.JButton) component(frame, "prompts-favourites-toggle");
    out.append("toggle: ").append(favToggle.getText()).append(", list shown ").append(component(frame, "prompts-favourite-menu:Portrait \u00b7 v1").isShowing()).append('\n');
    edt(favToggle::doClick);
    out.append("opened: ").append(favToggle.getText()).append(", list shown ").append(component(frame, "prompts-favourite-menu:Portrait \u00b7 v1").isShowing()).append('\n');
    for (String[] step : new String[][] {{"v1", "Move down"}, {"v2", "Remove from favourites"}}) {
      final StringBuilder items = new StringBuilder();
      edt(() -> {
        javax.swing.JButton more = (javax.swing.JButton) component(frame, "prompts-favourite-menu:Portrait \u00b7 " + step[0]);
        more.doClick();
        javax.swing.JMenuItem pick = null;
        for (java.awt.Component c : FavouriteMenu.last.getComponents()) {
          if (!(c instanceof javax.swing.JMenuItem)) continue;
          items.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
          if (((javax.swing.JMenuItem) c).getText().equals(step[1])) pick = (javax.swing.JMenuItem) c;
        }
        FavouriteMenu.last.setVisible(false);
        pick.doClick();
      });
      out.append(step[0]).append(" menu ").append(items).append(" -> ").append(step[1]).append(": ").append(order.get()).append('\n');
    }
    if (!order.get().equals("[Portrait \u00b7 v1]")) throw new AssertionError(out.toString());
    // Change title renames the favourite's prompt.
    FavouriteMenu.Ask was = FavouriteMenu.ask;
    final String[] preset = {null};
    FavouriteMenu.ask = (parent, given) -> {
      preset[0] = given;
      return "Portrait lit";
    };
    try {
      edt(() -> {
        ((javax.swing.JButton) component(frame, "prompts-favourite-menu:Portrait \u00b7 v1")).doClick();
        javax.swing.JMenuItem pick = null;
        for (java.awt.Component c : FavouriteMenu.last.getComponents()) {
          if (c instanceof javax.swing.JMenuItem && ((javax.swing.JMenuItem) c).getText().equals("Change title")) pick = (javax.swing.JMenuItem) c;
        }
        FavouriteMenu.last.setVisible(false);
        pick.doClick();
      });
    } finally {
      FavouriteMenu.ask = was;
    }
    out.append("Change title (was \"").append(preset[0]).append("\"): ").append(order.get()).append(", row ").append(component(frame, "prompts-favourite-menu:Portrait lit \u00b7 v1") != null).append('\n');
    if (!order.get().equals("[Portrait lit \u00b7 v1]") || !"Portrait".equals(preset[0])) throw new AssertionError(out.toString());
    // An audio input carries over to a program taking a sound, not to SogniPedit (its picture took the sound's name).
    for (String program : new String[] {"SogniPedit.java", "CutWav.java", "Wav2Mp3.java"}) {
      java.io.File f = new java.io.File(System.getProperty("pulsekit.test.dir"), "../../Programs/Java/" + program);
      byte[] src = java.nio.file.Files.readAllBytes(f.toPath());
      String hint = PyJavHints.status(program, new String(src, StandardCharsets.UTF_8), null);
      out.append(program).append(" takes the audio: ").append(PyJavHints.takesAudio(program, hint)).append('\n');
      if (PyJavHints.takesAudio(program, hint) != !program.startsWith("Sogni")) throw new AssertionError(out.toString());
    }
  }

  /**
   * The file set's source.mid row (name, clock, Play, Pause, Stop); a right click or long press on
   * its name opens the MIDI editor: the hits on a grid, a click adds or erases one; Back leaves
   * without saving, Save keeps the MIDI.
   */
  void s116_midi_editor() throws Exception {
    int[][] cells = Engine.emptyCells();
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    cells[2][4] = 120;
    cells[2][12] = 90;
    final byte[] midi = Engine.encodeMidi(cells, 110, 16);
    Engine.stageSourceMidi(midi, "Edit Me.mid");
    answers.add("Yes");
    call("ingest", midi, "Edit Me.mid");
    call("showView", "combo");
    final javax.swing.JButton[] name = {null};
    edt(() -> name[0] = (javax.swing.JButton) component(frame, "sourcemidi-name"));
    out.append("source MIDI row: ").append(name[0] != null ? name[0].getText() : "none").append(", play ").append(named(frame, "sourcemidi-play")).append('\n');
    answers.add("(keep)");
    edt(() -> name[0].dispatchEvent(new java.awt.event.MouseEvent(name[0], java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 4, 4, 1, false, java.awt.event.MouseEvent.BUTTON3)));
    idle();
    Thread.sleep(300);
    final MidiEditScreen[] ed = {MidiEditScreen.now};
    final StringBuilder seen = new StringBuilder();
    edt(() -> {
      java.awt.Container page = ed[0].dialog.getContentPane();
      seen.append("editor open: ").append(ed[0].dialog.isShowing()).append(", ").append(((JLabel) component(page, "midiedit-status")).getText()).append('\n');
      seen.append("buttons:");
      for (String b : new String[] {"midiedit-play", "midiedit-pause", "midiedit-stop", "midiedit-back", "midiedit-save"}) seen.append(' ').append(((javax.swing.JButton) component(page, b)).getText());
      seen.append('\n');
      seen.append("grid steps ").append(ed[0].edit.steps()).append(", kick at 0: ").append(ed[0].cells[0][0]).append(", accent at 4: ").append(ed[0].edit.accent(4)).append('\n');
      // A click on the grid: KIK, step 3.
      MidiEditScreen.Grid g = ed[0].grid;
      int x = g.stepX(2) + 5;
      int y = MidiEditScreen.ROW + 5;
      g.dispatchEvent(new java.awt.event.MouseEvent(g, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
      seen.append("click KIK 3: ").append(ed[0].cells[0][2]).append(", ").append(((JLabel) component(page, "midiedit-status")).getText()).append('\n');
      ((javax.swing.JButton) component(page, "midiedit-back")).doClick();
      seen.append("Back: closed ").append(!ed[0].dialog.isShowing()).append(", MIDI kept as it was ").append(java.util.Arrays.equals(midi, Engine.fileSetAudioOf("Edit Me").sourceMidi)).append('\n');
    });
    answers.add("(keep)");
    edt(() -> name[0].dispatchEvent(new java.awt.event.MouseEvent(name[0], java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 4, 4, 1, false, java.awt.event.MouseEvent.BUTTON3)));
    idle();
    Thread.sleep(300);
    ed[0] = MidiEditScreen.now;
    edt(() -> {
      java.awt.Container page = ed[0].dialog.getContentPane();
      ed[0].tap(1, 2);
      ed[0].tap(1, 0);
      ed[0].tap(3, 12);
      ed[0].tap(0, 8);
      // Playing, the grid scrolls with the beat: a hit at bar 4 makes it four bars long.
      ed[0].edit.toggle(0, 63);
      ed[0].cells = ed[0].edit.cells();
      ed[0].grid.revalidate();
      ed[0].scroll.validate();
      javax.swing.JViewport port = ed[0].scroll.getViewport();
      int before = port.getViewPosition().x;
      ed[0].clock(MidiEditScreen.playKey(ed[0].key), (int) Math.round(50 * 60000.0 / 110 / 4), true);
      int x = ed[0].grid.stepX(ed[0].playhead);
      seen.append("follows the playhead: step ").append(ed[0].playhead).append(", scrolled ").append(port.getViewPosition().x > before)
          .append(", playhead in view ").append(x >= port.getViewPosition().x && x < port.getViewPosition().x + port.getExtentSize().width).append('\n');
      ed[0].edit.toggle(0, 63);
      ed[0].cells = ed[0].edit.cells();
      ((javax.swing.JButton) component(page, "midiedit-play")).doClick();
      seen.append("Play: ").append(((JLabel) get("nowPlaying")).getText().replaceAll("No audio output for .*", "no audio device here")).append('\n');
      ((javax.swing.JButton) component(page, "midiedit-save")).doClick();
      seen.append("Save: closed ").append(!ed[0].dialog.isShowing()).append(", editor gone ").append(MidiEditScreen.now == null).append('\n');
    });
    out.append(seen);
    Engine.MidiBars bars = Engine.parseMidiBars(Engine.fileSetAudioOf("Edit Me").sourceMidi);
    StringBuilder grid = new StringBuilder();
    for (int t = 0; t < 3; t++) {
      for (int st = 0; st < 16; st++) grid.append(bars.bars.get(0)[t][st] == 0 ? '.' : (bars.bars.get(0)[t][st] >= MidiHitEdit.ACCENT_FROM ? 'X' : 'x'));
      grid.append(' ');
    }
    out.append("saved: ").append(grid).append(bars.bpm).append(" BPM").append('\n');
    Engine.fileSetAudio.clear();
    Engine.loadFileSetAudioDir(new File(System.getProperty("user.home"), ".pulsekit/fset-audio"));
    Engine.MidiBars again = Engine.parseMidiBars(Engine.fileSetAudioOf("Edit Me").sourceMidi);
    out.append("kept on disk: ").append(again != null && again.bars.get(0)[0][2] > 0).append('\n');
    if (bars.bars.get(0)[0][2] == 0 || bars.bars.get(0)[0][0] != 0 || bars.bars.get(0)[2][12] != 0 || again == null) throw new AssertionError(out.toString());
  }

  /**
   * Import screen, Open in MIDI editor: an imported MIDI keeps its MIDI with its file set and opens
   * in the MIDI editor after the Make a song question; Back there shows the Pattern view with the
   * file set open. Unticked, no editor. The setting is kept.
   */
  void s117_import_opens_midi_editor() throws Exception {
    call("showView", "import");
    final javax.swing.JCheckBox[] box = {null};
    edt(() -> box[0] = (javax.swing.JCheckBox) component(frame, "import-midi-editor"));
    out.append("checkbox: ").append(box[0].getText()).append(", ticked ").append(box[0].isSelected()).append('\n');
    edt(() -> box[0].doClick());
    out.append("kept: ").append(new String(Files.readAllBytes(((ProjectIo) get("projectIo")).importSettingsFile().toPath()), StandardCharsets.UTF_8).contains("midiEditor=1")).append('\n');
    answers.add("No");
    answers.add("(keep)");
    call("ingest", Engine.encodeSongMidi(songParts()), "Night Drive.mid");
    for (int i = 0; i < 100 && MidiEditScreen.now == null; i++) Thread.sleep(50);
    idle();
    final MidiEditScreen ed = MidiEditScreen.now;
    final StringBuilder seen = new StringBuilder();
    edt(() -> {
      seen.append("editor open: ").append(ed != null && ed.dialog.isShowing()).append(", file set ").append(ed == null ? "" : ed.key).append('\n');
      seen.append("MIDI kept with the set: ").append(Engine.fileSetMidiName("Night Drive")).append('\n');
      ((javax.swing.JButton) component(ed.dialog.getContentPane(), "midiedit-back")).doClick();
      seen.append("Back: view ").append(((Pulsekit) frame).view).append(", file set open ").append(((Pulsekit) frame).openPacks.get("f:Night Drive")).append('\n');
    });
    out.append(seen);
    String afterBack = seen.toString().contains("Back: view pattern") ? "pattern" : "";
    call("showView", "import");
    edt(() -> box[0].doClick());
    answers.add("No");
    call("ingest", Engine.encodeSongMidi(songParts()), "Day Drive.mid");
    idle();
    out.append("unticked: editor ").append(MidiEditScreen.now != null).append(", MIDI kept ").append(Engine.fileSetMidiName("Day Drive").length() > 0).append('\n');
    if (ed == null || !"pattern".equals(afterBack) || MidiEditScreen.now != null) throw new AssertionError(out.toString());
  }

  /**
   * VLC: when it closed Pulsekit as it started (vlc-starting.txt left), the next start turns it off
   * and says so; General settings' Use VLC turns it on and off (vlc-off.txt), and off it is not tried.
   */
  void s118_vlc_off() throws Exception {
    java.nio.file.Files.write(VlcPlayer.startingFile().toPath(), new byte[0]);
    answers.add("OK");
    final Pulsekit[] again = new Pulsekit[1];
    SwingUtilities.invokeAndWait(() -> again[0] = new Pulsekit());
    idle();
    Thread.sleep(400);
    out.append("after VLC closed Pulsekit: off ").append(VlcPlayer.turnedOff()).append(", note gone ").append(!VlcPlayer.startingFile().exists())
        .append(", says ").append(again[0].nowPlaying.getText().startsWith("VLC closed Pulsekit")).append('\n');
    SwingUtilities.invokeAndWait(() -> again[0].dispose());
    out.append("off: used ").append(VlcPlayer.available()).append(", why ").append(VlcPlayer.why()).append('\n');
    VlcPlayer.setOff(false);
    out.append("on again: off ").append(VlcPlayer.turnedOff()).append('\n');
    if (VlcPlayer.turnedOff()) throw new AssertionError(out.toString());
  }

  /**
   * Picture ready > Process as a reference: a recent program that takes pictures, one of its recent
   * prompts, Reference file 1 or 2; Open loads it in PyJav with the picture in that slot. Kept.
   */
  void s119_process_as_reference() throws Exception {
    final PyJav pj = (PyJav) get("pyJav");
    String pedit = ProgramFiles.bundledSource("SogniPedit.java");
    PyJavRecent.remember(pj.pyRecentDir(), "SogniPedit.java", "--prompt \"make the coat blue\" --image old.png", pedit, null);
    PyJavRecent.remember(pj.pyRecentDir(), "SogniPedit.java", "--prompt \"a red scarf\"", pedit, null);
    final File pic = new File(System.getProperty("user.home"), "look.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pic);
    // Saved Params with an old picture in the slot: they win over the run's arguments, so the picture goes there too.
    pj.saveParams("SogniPedit.java", "--image2 old2.png --steps 4");
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> ProcessRefDialog.open((Pulsekit) frame, pic));
    for (int i = 0; i < 100 && (ProcessRefDialog.last == null || !ProcessRefDialog.last.dialog.isShowing()); i++) Thread.sleep(50);
    final ProcessRefDialog d = ProcessRefDialog.last;
    final StringBuilder seen = new StringBuilder();
    edt(() -> {
      seen.append("programs: ").append(d.programs).append(", prompt ").append(d.prompt.getSelectedItem()).append(", slots ").append(d.ref1.getText()).append(" | ").append(d.ref2.getText()).append('\n');
      d.prompt.setSelectedIndex(1);
      d.ref2.setSelected(true);
      ((javax.swing.JButton) component(d.dialog.getContentPane(), "process-ref-open")).doClick();
    });
    idle();
    String args = pj.pyExtra.getText().replace(pic.getAbsolutePath(), "~/look.png");
    out.append(seen).append("Open: view ").append(((Pulsekit) frame).view).append(", program ").append(get("pyName")).append(", args ").append(args).append('\n');
    String saved = pj.loadParams("SogniPedit.java");
    List<ProgramParams.Param> ps = ProgramParams.parse(pedit);
    String[] values = ProgramParams.values(ps, saved, pj.pyExtra.getText());
    String shown = "";
    for (int i = 0; i < ps.size(); i++) if (ps.get(i).token.equals("--image2")) shown = values[i].replace(pic.getAbsolutePath(), "~/look.png");
    out.append("Params: --image2 ").append(shown).append(", saved ").append(saved.replace(pic.getAbsolutePath(), "~/look.png")).append('\n');
    ProcessAsRef.Choice kept = ProcessAsRef.load(PromptDb.dir());
    out.append("kept: ").append(kept.program).append(", slot ").append(kept.slot).append('\n');
    if (!args.contains("--image2 ~/look.png") || !args.contains("make the coat blue") || kept.slot != 2 || !shown.equals("~/look.png")) throw new AssertionError(out.toString());
  }

  /**
   * Media browser: a MIDI's right click has Open in MIDI editor (Save writes <name>-edited.mid beside
   * it), Render with SF2, Render with plugin and Play with default SoundFont; a WAV's has Convert
   * drum hits into MIDI. The programs open on PyJav with the file as their input.
   */
  void s120_media_midi_items() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final File media = new File(home, "media-midi");
    media.mkdirs();
    int[][] cells = Engine.emptyCells();
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    Files.write(new File(media, "beat.mid").toPath(), Engine.encodeMidi(cells, 120, 16));
    Files.write(new File(media, "hit.wav").toPath(), AudioIo.encodeWav(new short[4410], 22050));
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    final javax.swing.JDialog[] dlg = new javax.swing.JDialog[1];
    answers.add("(keep)");
    inspectNext = d -> dlg[0] = d;
    SwingUtilities.invokeLater(() -> mb.open(media));
    for (int i = 0; i < 100 && dlg[0] == null; i++) Thread.sleep(50);
    final StringBuilder seen = new StringBuilder();
    for (final String name : new String[] {"beat.mid", "hit.wav"}) {
      for (int i = 0; i < 100; i++) {
        final boolean[] has = {false};
        edt(() -> has[0] = component(dlg[0], "media-card:" + name) != null);
        if (has[0]) break;
        Thread.sleep(50);
      }
      edt(() -> {
        javax.swing.JButton card = (javax.swing.JButton) component(dlg[0], "media-card:" + name);
        card.dispatchEvent(new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3));
        javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) get("lastMenu");
        StringBuilder items = new StringBuilder();
        for (Component c : menu.getComponents()) if (c instanceof javax.swing.JMenuItem) items.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
        seen.append(name).append(" menu: ").append(items).append('\n');
        menu.setVisible(false);
      });
    }
    out.append(seen);
    answers.add("(keep)");
    edt(() -> {
      MediaDir.Entry e = new MediaDir.Entry();
      e.name = "beat.mid";
      e.id = new File(media, "beat.mid").getAbsolutePath();
      mb.kindItem(e, new File(media, "beat.mid"), MediaDir.MIDI_EDIT);
    });
    for (int i = 0; i < 100 && MidiEditScreen.now == null; i++) Thread.sleep(50);
    final MidiEditScreen ed = MidiEditScreen.now;
    edt(() -> {
      ed.tap(3, 4);
      ((javax.swing.JButton) component(ed.dialog.getContentPane(), "midiedit-save")).doClick();
    });
    idle();
    File edited = new File(media, "beat-edited.mid");
    out.append("saved beside it: ").append(edited.isFile()).append(", edit in it ").append(edited.isFile() && Engine.parseMidiBars(Files.readAllBytes(edited.toPath())).bars.get(0)[2][4] > 0).append('\n');
    edt(() -> {
      MediaDir.Entry e = new MediaDir.Entry();
      e.name = "hit.wav";
      e.id = new File(media, "hit.wav").getAbsolutePath();
      mb.kindItem(e, new File(media, "hit.wav"), MediaDir.TO_MIDI);
    });
    idle();
    out.append("Convert drum hits into MIDI: ").append(get("pyName")).append('\n');
    if (!edited.isFile() || !"DrumMidi_CRT.jar".equals(get("pyName")) || !(seen.toString().contains("[Open in MIDI editor]") && seen.toString().contains("[Render with SF2]") && seen.toString().contains("[Render with plugin]")
        && seen.toString().contains("[Play with default SoundFont]"))
        || !seen.toString().contains("[Convert drum hits into MIDI]")) throw new AssertionError(out.toString());
  }

  /**
   * Composer: building blocks from the Song tab's picker, the AABA form making Verse and Bridge,
   * blocks and a subpart in parts, To Song putting the form on the Song tab, the Song tab's From
   * Composer, kept, and a MIDI's Add to composer stack.
   */
  void s121_composer() throws Exception {
    call("showView", "composer");
    final ComposerPage page = (ComposerPage) get("composerPage");
    final StringBuilder seen = new StringBuilder();
    edt(() -> {
      seen.append("tab: ").append(((Pulsekit) frame).view).append(", page ").append(named(frame, "composer")).append('\n');
      ((javax.swing.JButton) component(frame, "composer-add-block")).doClick();
      javax.swing.JPopupMenu pick = ((Pulsekit) frame).songEditor.lastPick;
      for (Component c : pick.getComponents()) if (c instanceof javax.swing.JMenuItem && "Rock".equals(((javax.swing.JMenuItem) c).getText())) {
        ((javax.swing.JMenuItem) c).doClick();
        break;
      }
      pick.setVisible(false);
    });
    idle();
    edt(() -> {
      page.composer().addBlock(new Composer.Block(Composer.FILL, "toms", "Toms"));
      page.changed();
      seen.append("stack: ");
      for (Composer.Block b : page.composer().stack) seen.append(b.label()).append(" | ");
      seen.append('\n');
      ((javax.swing.JButton) component(frame, "composer-form:AABA")).doClick();
      seen.append("AABA: ").append(page.composer().formLabel()).append('\n');
      ((javax.swing.JButton) component(frame, "composer-part-add:Verse")).doClick();
      ((javax.swing.JMenuItem) page.lastComposerMenu.getComponent(0)).doClick();
      ((javax.swing.JButton) component(frame, "composer-part-add:Verse")).doClick();
      ((javax.swing.JMenuItem) page.lastComposerMenu.getComponent(1)).doClick();
      page.composer().part("Verse").entries.get(0).repeats = 2;
      ((javax.swing.JButton) component(frame, "composer-part-sub:Bridge")).doClick();
      ((javax.swing.JMenuItem) page.lastComposerMenu.getComponent(0)).doClick();
      seen.append("Verse: ").append(page.composer().part("Verse").entries.get(0).label()).append(", ").append(page.composer().part("Verse").entries.get(1).label())
          .append("; Bridge: ").append(page.composer().part("Bridge").entries.get(0).label()).append('\n');
      ((javax.swing.JButton) component(frame, "composer-to-song")).doClick();
    });
    idle();
    @SuppressWarnings("unchecked")
    final List<Engine.Part> song = (List<Engine.Part>) get("song");
    edt(() -> {
      StringBuilder names = new StringBuilder();
      for (Engine.Part p : song) names.append(p.name).append(" x").append(p.repeats).append(", ");
      seen.append("To Song: view ").append(((Pulsekit) frame).view).append(", ").append(song.size()).append(" parts: ").append(names).append('\n');
      javax.swing.JPopupMenu from = ((Pulsekit) frame).songEditor.fromComposerMenu();
      StringBuilder items = new StringBuilder();
      for (Component c : from.getComponents()) if (c instanceof javax.swing.JMenuItem) items.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
      seen.append("From Composer: ").append(items).append('\n');
      ((javax.swing.JMenuItem) from.getComponent(1)).doClick();
      seen.append("after adding Verse: ").append(song.size()).append(" parts\n");
    });
    out.append(seen);
    String kept = page.composer().encode();
    out.append("kept: ").append(Composer.load(PromptDb.dir()).encode().equals(kept)).append('\n');
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 118, Engine.styleCells(Engine.styles().get("funk")), 4));
    parts.add(Engine.fill("toms", 118, 1));
    parts.add(Engine.groove("B", 118, Engine.styleCells(Engine.styles().get("house")), 4));
    final File mid = new File(System.getProperty("user.home"), "Night Drive.mid");
    Files.write(mid.toPath(), Engine.encodeSongMidi(parts));
    final int before = page.composer().stack.size();
    edt(() -> MediaBrowser.toComposer((Pulsekit) frame, mid, "Night Drive.mid"));
    idle();
    out.append("Add to composer stack: ").append(page.composer().stack.size() - before).append(" blocks, view ").append(get("view")).append(", ").append(((javax.swing.JLabel) get("nowPlaying")).getText()).append('\n');
    if (song.size() != 10 || !kept.contains("Verse") || page.composer().stack.size() <= before || !"composer".equals(get("view"))) throw new AssertionError(out.toString());
  }

  /**
   * Sunday notes. Browse DB and the galleries: the tabs stay over the thumbnails, and each tab opens
   * where it was left. A long MIDI's Make a song? question offers Composer parts: the song split
   * into parts and a song form. MIDI ready: Add to composer stack. Variate on pattern and fill
   * menus. The video player's clock reads mm:ss / mm:ss.
   */
  void s122_sunday_notes() throws Exception {
    final PromptDb db = (PromptDb) get("promptDb");
    for (int i = 0; i < 30; i++) {
      PromptDb.vault().addLibraryFile("ref" + i + ".txt", ("ref " + i).getBytes(StandardCharsets.UTF_8), "Imported", 1);
      PromptDb.vault().addLibraryFile("res" + i + ".txt", ("res " + i).getBytes(StandardCharsets.UTF_8), "Imported", 3);
    }
    // Browse DB: scrolled down on Ref files, then on Result files; each comes back where it was.
    java.util.function.Function<Action, JDialog> opened = a -> {
      try {
        final Object before = get("lastBrowse");
        answers.add("(keep)");
        SwingUtilities.invokeLater(() -> {
          try {
            a.run();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });
        for (int i = 0; i < 100 && get("lastBrowse") == before; i++) Thread.sleep(100);
        idle();
        return (JDialog) get("lastBrowse");
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    JDialog d = opened.apply(() -> db.browse(false, null, (name, file) -> {}));
    final boolean topOutside = !SwingUtilities.isDescendingFrom(component(d, "refs-kind:results"), db.shownScroll);
    edt(() -> db.shownScroll.getVerticalScrollBar().setValue(300));
    final JDialog refs = d;
    d = opened.apply(() -> ((javax.swing.JButton) component(refs, "refs-kind:results")).doClick());
    out.append("Browse DB: tabs over the scroll ").append(topOutside).append(", Result files at ").append(db.scrolledTo()).append('\n');
    edt(() -> db.shownScroll.getVerticalScrollBar().setValue(120));
    final JDialog res = d;
    d = opened.apply(() -> ((javax.swing.JButton) component(res, "refs-kind:refs")).doClick());
    int backRefs = db.scrolledTo();
    final JDialog refs2 = d;
    d = opened.apply(() -> ((javax.swing.JButton) component(refs2, "refs-kind:results")).doClick());
    int backRes = db.scrolledTo();
    out.append("back on Ref files at ").append(backRefs).append(", on Result files at ").append(backRes).append('\n');
    final JDialog lastB = d;
    edt(() -> lastB.dispose());
    // The galleries: Ref files | Result files | Prompt files over the thumbnails, each where it was left.
    java.util.function.Function<Action, JDialog> gallery = a -> {
      try {
        final Object before = get("lastGallery");
        answers.add("(keep)");
        SwingUtilities.invokeLater(() -> {
          try {
            a.run();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });
        for (int i = 0; i < 100 && get("lastGallery") == before; i++) Thread.sleep(100);
        idle();
        return (JDialog) get("lastGallery");
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    JDialog g = gallery.apply(() -> db.gallery(0));
    out.append("gallery \"").append(g.getTitle()).append("\":");
    for (int k = 0; k < 3; k++) {
      javax.swing.JButton b = (javax.swing.JButton) component(g, "gallery-tab:" + k);
      out.append(" [").append(b.getText()).append(b.isEnabled() ? "" : " (shown)").append(']');
    }
    out.append('\n');
    edt(() -> db.shownScroll.getVerticalScrollBar().setValue(250));
    final JDialog g0 = g;
    g = gallery.apply(() -> ((javax.swing.JButton) component(g0, "gallery-tab:2")).doClick());
    out.append("Prompt files at ").append(db.scrolledTo()).append(", title ").append(g.getTitle()).append('\n');
    final JDialog g2 = g;
    g = gallery.apply(() -> ((javax.swing.JButton) component(g2, "gallery-tab:0")).doClick());
    int galleryBack = db.scrolledTo();
    out.append("back on Ref files at ").append(galleryBack).append('\n');
    final JDialog lastG = g;
    edt(() -> lastG.dispose());
    idle();

    // A long MIDI: Make a song? offers Composer parts.
    List<Engine.Part> parts = new ArrayList<>();
    int[][] rock = Engine.styleCells(Engine.styles().get("rock"));
    int[][] funk = Engine.styleCells(Engine.styles().get("funk"));
    parts.add(Engine.groove("Intro", 110, Engine.styleCells(Engine.styles().get("house")), 2));
    for (int round = 0; round < 2; round++) {
      parts.add(Engine.groove("V", 110, rock, 4));
      parts.add(Engine.fill("toms", 110, 1));
      parts.add(Engine.groove("C", 110, funk, 4));
      parts.add(Engine.fill("snare", 110, 1));
    }
    answers.add("Composer parts");
    call("ingest", Engine.encodeSongMidi(parts), "Long song.mid");
    final ComposerPage page = (ComposerPage) get("composerPage");
    Composer c = page.composer();
    out.append("view ").append(get("view")).append(", ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    for (Composer.Part p : c.parts) {
      out.append("part ").append(p.name).append(':');
      for (Composer.Entry e : p.entries) out.append(' ').append(e.label());
      out.append('\n');
    }
    out.append("form: ").append(c.formLabel()).append(", kept ").append(Composer.load(PromptDb.dir()).encode().equals(c.encode())).append('\n');
    // MIDI ready: Add to composer stack.
    final int before = c.stack.size();
    answers.add("No");
    call("ingest", Engine.encodeMidi(Engine.styleCells(Engine.styles().get("hiphop")), 92), "Gen groove.mid");
    final byte[] gen = Engine.encodeSongMidi(songParts());
    answers.add("No");
    call("ingest", gen, "Gen song.mid");
    answers.add("Add to composer stack");
    call("offerMidi", "Gen song.mid", gen);
    out.append("MIDI ready, Add to composer stack: ").append(c.stack.size() - before).append(" blocks, ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    // Variate: the first item on the pattern and fill menus; it does what Gen does.
    final StyleLibrary lib = (StyleLibrary) get("styleLibrary");
    final StringBuilder seen = new StringBuilder();
    edt(() -> {
      javax.swing.JPopupMenu m = lib.builtinStyleMenu("rock");
      javax.swing.JMenuItem v = (javax.swing.JMenuItem) m.getComponent(0);
      seen.append("Rock menu: ").append(v.getText());
      v.doClick();
      seen.append(" -> ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
      Engine.LearnedFill f = ((Pulsekit) frame).learnedFills.get(0);
      javax.swing.JMenuItem fv = (javax.swing.JMenuItem) lib.learnedFillMenu(f).getComponent(0);
      seen.append("fill menu: ").append(fv.getText());
      int had = ((Pulsekit) frame).variatedFills.size();
      fv.doClick();
      seen.append(" -> ").append(((Pulsekit) frame).variatedFills.size() - had).append(" variated fill, ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    });
    out.append(seen);
    // The video player's clock: mm:ss / mm:ss.
    File clip = new File(System.getProperty("user.home"), "walk.mp4");
    Files.write(clip.toPath(), new byte[16]);
    String html = new String(Files.readAllBytes(((File) call("videoPage", clip)).toPath()), StandardCharsets.UTF_8);
    out.append("video clock: ").append(html.contains(">00:00 / 00:00<")).append(", minutes padded ").append(html.contains("(m<10?'0':'')+m")).append('\n');
    if (!topOutside || backRefs != 300 || backRes != 120 || galleryBack != 250 || c.form.isEmpty() || c.stack.size() <= before || seen.indexOf("Variate") < 0) throw new AssertionError(out.toString());
  }

  /** Video ready: Play plays it here (VLC); a right click (the phone's long press) pauses it and has Extract frame, into SC beside the video. */
  void s123_video_ready_extract_frame() throws Exception {
    System.setProperty("pulsekit.vlc.args", "--aout=dummy");
    File home = new File(System.getProperty("user.home"));
    File runs = new File(home, "runs");
    runs.mkdirs();
    final File clip = new File(runs, "walk.webm");
    Files.copy(new File(System.getProperty("pulsekit.test.dir", "."), "clip.webm").toPath(), clip.toPath());
    if (!VlcPlayer.available()) {
      out.append("no VLC: ").append(VlcPlayer.why()).append('\n');
      return;
    }
    final PyJav pj = (PyJav) get("pyJav");
    answers.add("Play");
    // Answered (Fit) so the test's watcher leaves the player open.
    answers.add("Fit");
    SwingUtilities.invokeLater(() -> pj.offerVideo(clip, 1));
    for (int i = 0; i < 100 && get("lastVideo") == null; i++) Thread.sleep(100);
    Thread.sleep(1500);
    idle();
    final JDialog dialog = (JDialog) get("lastVideo");
    final VlcPlayer player = (VlcPlayer) get("lastPlayer");
    out.append("player: ").append(dialog == null ? "none" : dialog.getTitle()).append('\n');
    edt(() -> ((javax.swing.AbstractButton) component(dialog, "video-play")).doClick());
    Thread.sleep(1000);
    out.append("playing ").append(player.playing());
    edt(() -> {
      java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(player.screen, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
          java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 20, 20, 1, true, java.awt.event.MouseEvent.BUTTON3);
      for (java.awt.event.MouseListener l : player.screen.getMouseListeners()) l.mousePressed(press);
    });
    Thread.sleep(600);
    final PromptDb db = (PromptDb) get("promptDb");
    javax.swing.JMenuItem item = (javax.swing.JMenuItem) db.lastFrameMenu.getComponent(0);
    out.append(", right click: playing ").append(player.playing()).append(", menu ").append(item.getText()).append('\n');
    edt(() -> item.doClick());
    edt(() -> db.lastFrameMenu.setVisible(false));
    File sc = new File(runs, MediaDir.FRAMES_DIR);
    String[] got = sc.list();
    out.append("SC folder: ").append(got == null ? "none" : got.length + " file(s), " + (got.length > 0 && got[0].matches("walk-0m0\\ds\\d{3}\\.png") ? "named walk-<time>.png" : String.join(",", got))).append('\n');
    // Capture frame and process as a reference: the frame into a program, as in Picture ready; the video closes.
    String sv = ProgramFiles.bundledSource("SogniVideo.java");
    PyJavRecent.remember(pj.pyRecentDir(), "SogniVideo.java", "--prompt \"the walk goes on\"", sv, null);
    edt(() -> {
      java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(player.screen, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
          java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 20, 20, 1, true, java.awt.event.MouseEvent.BUTTON3);
      for (java.awt.event.MouseListener l : player.screen.getMouseListeners()) l.mousePressed(press);
    });
    final javax.swing.JMenuItem process = (javax.swing.JMenuItem) db.lastFrameMenu.getComponent(1);
    out.append("second item: ").append(process.getText()).append('\n');
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> {
      process.doClick();
      db.lastFrameMenu.setVisible(false);
    });
    for (int i = 0; i < 100 && (ProcessRefDialog.last == null || !ProcessRefDialog.last.dialog.isShowing()); i++) Thread.sleep(50);
    idle();
    final ProcessRefDialog r = ProcessRefDialog.last;
    String[] now = sc.list();
    out.append("process: video ").append(dialog.isShowing() ? "open" : "closed").append(", dialog ").append(r == null ? "none" : r.picture.getName().replaceAll("\\d", "#") + ", programs " + r.programs)
        .append(", SC files ").append(now == null ? 0 : now.length).append('\n');
    if (r != null) {
      edt(() -> ((javax.swing.JButton) component(r.dialog.getContentPane(), "process-ref-open")).doClick());
      out.append("Open: program ").append(get("pyName")).append(", args ").append(pj.pyExtra.getText().replace(sc.getAbsolutePath(), "~/runs/SC").replaceAll("\\d", "#")).append('\n');
    }
    if (dialog.isShowing()) edt(() -> ((javax.swing.JButton) find(dialog.getContentPane(), "Close")).doClick());
    if (got == null || got.length != 1 || r == null || dialog.isShowing() || !pj.pyExtra.getText().contains("walk-0m0")) throw new AssertionError(out.toString());
  }

  /**
   * Groups: a Browse DB card's right click has Add to favourites (★) and Add to group (New group…);
   * G by A I V S T shows the group picked (a right click on G lists the groups). The galleries and
   * the Media browser have Add to group and G too.
   */
  void s124_file_groups() throws Exception {
    final PromptDb db = (PromptDb) get("promptDb");
    DbFilter.current = "A";
    for (String n : new String[] {"beach.png", "notes.txt", "dune.png"}) PromptDb.vault().addLibraryFile(n, ("x" + n).getBytes(StandardCharsets.UTF_8), "Imported", 1);
    java.util.function.Function<Action, JDialog> opened = a -> {
      try {
        final Object before = get("lastBrowse");
        answers.add("(keep)");
        SwingUtilities.invokeLater(() -> {
          try {
            a.run();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });
        for (int i = 0; i < 100 && get("lastBrowse") == before; i++) Thread.sleep(100);
        idle();
        return (JDialog) get("lastBrowse");
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    final JDialog[] d = {opened.apply(() -> db.browse(false, null, (name, file) -> {}))};
    java.util.function.Consumer<String> rightClick = name -> {
      try {
        edt(() -> {
          java.awt.Component card = component(d[0], "refs-pick:" + name);
          java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
              java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3);
          for (java.awt.event.MouseListener l : card.getMouseListeners()) l.mousePressed(press);
        });
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    java.util.function.Function<javax.swing.JPopupMenu, String> items = m -> {
      StringBuilder sb = new StringBuilder();
      for (Component c : m.getComponents()) if (c instanceof javax.swing.JMenuItem) sb.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
      return sb.toString();
    };
    out.append("G button: ").append(((javax.swing.AbstractButton) component(d[0], "db-filter:G")).getText()).append('\n');
    // beach.png into a new group, Trip.
    rightClick.accept("beach.png");
    out.append("card menu: ").append(items.apply(db.lastCardMenu)).append('\n');
    edt(() -> ((javax.swing.JMenuItem) component(db.lastCardMenu, "add-to-group")).doClick());
    out.append("groups: ").append(items.apply(GroupMenus.last)).append('\n');
    inspectNext = dlg -> {
      javax.swing.JTextField field = null;
      for (javax.swing.JTextField t : fields(dlg.getContentPane())) field = t;
      if (field != null) field.setText("Trip");
    };
    answers.add("OK");
    final javax.swing.JPopupMenu first = GroupMenus.last;
    edt(() -> ((javax.swing.JMenuItem) component(first, "group-new")).doClick());
    idle();
    out.append("after New group: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    rightClick.accept("dune.png");
    edt(() -> ((javax.swing.JMenuItem) component(db.lastCardMenu, "add-to-group")).doClick());
    out.append("groups for dune.png: ").append(items.apply(GroupMenus.last)).append('\n');
    final javax.swing.JPopupMenu second = GroupMenus.last;
    edt(() -> ((javax.swing.JMenuItem) component(second, "group:Trip")).doClick());
    // notes.txt a favourite: the browser opens again with ★ on it.
    rightClick.accept("notes.txt");
    final javax.swing.JPopupMenu fav = db.lastCardMenu;
    d[0] = opened.apply(() -> ((javax.swing.JMenuItem) component(fav, "refs-favourite")).doClick());
    out.append("favourite: ").append(((javax.swing.JButton) component(d[0], "refs-pick:notes.txt")).getText().contains("\u2605") ? "starred" : "no star").append('\n');
    // G: no group picked yet, so the list; Trip shows its two files.
    final JDialog atG = d[0];
    edt(() -> ((javax.swing.AbstractButton) component(atG, "db-filter:G")).doClick());
    out.append("G list: ").append(items.apply(GroupMenus.last)).append('\n');
    final javax.swing.JPopupMenu list = GroupMenus.last;
    d[0] = opened.apply(() -> ((javax.swing.JMenuItem) component(list, "group-show:Trip")).doClick());
    StringBuilder shown = new StringBuilder();
    for (String n : new String[] {"beach.png", "notes.txt", "dune.png"}) if (component(d[0], "refs-pick:" + n) != null) shown.append(n).append(' ');
    out.append("G \u00b7 Trip: ").append(shown).append("| button ").append(((javax.swing.AbstractButton) component(d[0], "db-filter:G")).getText()).append('\n');
    final JDialog last = d[0];
    edt(() -> last.dispose());
    DbFilter.current = "A";
    // The galleries' G, and the Media browser's.
    final JDialog[] gal = new JDialog[1];
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> db.gallery(0));
    for (int i = 0; i < 100 && get("lastGallery") == null; i++) Thread.sleep(100);
    idle();
    gal[0] = (JDialog) get("lastGallery");
    out.append("gallery G: ").append(((javax.swing.AbstractButton) component(gal[0], "gallery-filter:G")).getText()).append('\n');
    edt(() -> gal[0].dispose());
    out.append("media menu has ").append(FileGroups.ADD).append(": ").append(java.util.Arrays.asList(MediaDir.menu(false, MediaDir.PICTURE, true, "sea.png", true)).contains(FileGroups.ADD)).append('\n');
    File pics = new File(System.getProperty("user.home"), "pics");
    pics.mkdirs();
    for (String n : new String[] {"sea.png", "sky.png"}) Files.write(new File(pics, n).toPath(), new byte[] {1, 2, 3});
    List<MediaDir.Entry> all = MediaBrowser.list(pics);
    for (MediaDir.Entry e : all) if (e.name.equals("sea.png")) FileGroups.toggle("Trip", MediaDir.groupKey(e));
    StringBuilder kept = new StringBuilder();
    for (MediaDir.Entry e : MediaDir.filtered(all, "G")) kept.append(e.name).append(' ');
    out.append("media G \u00b7 Trip: ").append(kept).append('\n');
    out.append("kept: ").append(new String(Files.readAllBytes(new File(PromptDb.dir(), "file-groups.txt").toPath()), StandardCharsets.UTF_8).split("\n").length).append(" lines\n");
    if (!"beach.png dune.png ".equals(shown.toString()) || !"sea.png ".equals(kept.toString())) throw new AssertionError(out.toString());
  }

  /** F (favourites) before G: Browse DB and the galleries (Prompt files too) show only the favourites; the Media browser's Add to favourites and F. */
  void s125_favourites_letter() throws Exception {
    final PromptDb db = (PromptDb) get("promptDb");
    for (String n : new String[] {"beach.png", "notes.txt"}) PromptDb.vault().addLibraryFile(n, ("x" + n).getBytes(StandardCharsets.UTF_8), "Imported", 1);
    for (int i = 0; i < 30; i++) PromptDb.vault().addPromptFile("prompt-" + i + ".prompt", ("Prompt " + i + "\n").getBytes(StandardCharsets.UTF_8), "Prompt " + i);
    FileFavourites.load(PromptDb.dir());
    for (PromptVault.StoredFile f : PromptDb.files(false, null)) if (f.name.equals("notes.txt")) FileFavourites.toggle(f);
    DbFilter.current = "F";
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> db.browse(false, null, (name, file) -> {}));
    for (int i = 0; i < 100 && get("lastBrowse") == null; i++) Thread.sleep(100);
    idle();
    final JDialog d = (JDialog) get("lastBrowse");
    out.append("Browse DB F: notes.txt ").append(component(d, "refs-pick:notes.txt") != null).append(", beach.png ").append(component(d, "refs-pick:beach.png") != null)
        .append(", F button ").append(component(d, "db-filter:F") != null).append('\n');
    boolean ok = component(d, "refs-pick:notes.txt") != null && component(d, "refs-pick:beach.png") == null;
    edt(() -> d.dispose());
    DbFilter.current = "A";
    // F is a switch kept while the program runs: off for the gallery.
    FileFilter.reset();
    // Prompt files: its 30 cards, the three tabs and A I V S T F G.
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> db.gallery(2));
    for (int i = 0; i < 100 && get("lastGallery") == null; i++) Thread.sleep(100);
    idle();
    final JDialog g = (JDialog) get("lastGallery");
    out.append("Prompt files: ").append(g.getTitle()).append(", cards ").append(component(g, "gallery:prompt-0.prompt") != null && component(g, "gallery:prompt-29.prompt") != null)
        .append(", tabs ").append(component(g, "gallery-tab:0") != null).append(", F ").append(component(g, "gallery-filter:F") != null).append(", G ").append(component(g, "gallery-filter:G") != null).append('\n');
    ok &= component(g, "gallery:prompt-29.prompt") != null && component(g, "gallery-filter:F") != null;
    edt(() -> g.dispose());
    out.append("media filters: ").append(String.join(" ", MediaDir.FILTERS)).append(", menu has ").append(java.util.Arrays.asList(MediaDir.menu(false, MediaDir.PICTURE, true, "sea.png", true)).contains(FileFavourites.ADD_SHORT)).append('\n');
    if (!ok) throw new AssertionError(out.toString());
  }

  /** Video ready: Add to favourites and Add to group (the question comes back); Picture ready's right click menu has them too. */
  void s126_ready_favourites_groups() throws Exception {
    final PyJav pj = (PyJav) get("pyJav");
    File runs = new File(System.getProperty("user.home"), "runs");
    runs.mkdirs();
    final File clip = new File(runs, "walk.mp4");
    Files.write(clip.toPath(), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});
    FileGroups.load(PromptDb.dir());
    FileGroups.toggle("Run", "file:other.png|1");
    answers.add("Add to favourites");
    answers.add("Add to group");
    answers.add("OK");
    answers.add("Close");
    edt(() -> pj.offerVideo(clip, 1));
    idle();
    out.append("walk.mp4: favourite ").append(MarkFile.isFavourite("walk.mp4", clip.length())).append(", in Run ").append(FileGroups.in("Run", MarkFile.mediaKey("walk.mp4", clip.length()))).append('\n');
    boolean ok = MarkFile.isFavourite("walk.mp4", clip.length()) && FileGroups.in("Run", MarkFile.mediaKey("walk.mp4", clip.length()));
    // Picture ready: the picture's right click.
    final File pic = new File(runs, "look.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pic);
    final javax.swing.JPopupMenu[] m = new javax.swing.JPopupMenu[1];
    edt(() -> m[0] = GroupMenus.fileMenu((Pulsekit) frame, pic));
    StringBuilder items = new StringBuilder();
    for (Component c : m[0].getComponents()) if (c instanceof javax.swing.JMenuItem) items.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    out.append("Picture ready menu: ").append(items).append('\n');
    edt(() -> ((javax.swing.JMenuItem) component(m[0], "file-favourite")).doClick());
    out.append("look.png favourite: ").append(MarkFile.isFavourite("look.png", pic.length())).append('\n');
    ok &= MarkFile.isFavourite("look.png", pic.length());
    if (!ok) throw new AssertionError(out.toString());
  }
  /**
   * F, G and C are switches with the type letter (V with F: the favourite videos); a right click on
   * C lists the categories under the letter's main one (I: Image), a category's submenu holding its
   * subcategories; T1 T2 T3 keep the files with any tag picked. Save prompt: the subcategory
   * Browse… has Add new lower level / same level subcategory, and a Group list. Picture ready's
   * menu: Add tag and Add to category.
   */
  void s127_filter_switches_categories_tags() throws Exception {
    final PromptDb db = (PromptDb) get("promptDb");
    DbFilter.current = "A";
    PromptVault vault = PromptDb.vault();
    long image = 0, video = 0;
    for (PromptVault.Category c : vault.mains()) {
      if (c.name.equalsIgnoreCase("Image")) image = c.id;
      if (c.name.equalsIgnoreCase("video")) video = c.id;
    }
    long portraits = vault.addSubcategory(image, "Portraits");
    vault.addSubcategory(portraits, "Faces");
    long p1 = vault.addPrompt(portraits, "Face");
    vault.addVersion(p1, "Face", "", "a face", "", "face.png", "face".getBytes(StandardCharsets.UTF_8), "", null, "", null, "", "");
    vault.setTags(p1, java.util.Arrays.asList("beach"));
    long p2 = vault.addPrompt(video, "Walk");
    vault.addVersion(p2, "Walk", "", "a walk", "", "walk.mp4", "walk".getBytes(StandardCharsets.UTF_8), "", null, "", null, "", "");
    vault.setTags(p2, java.util.Arrays.asList("night"));
    long p3 = vault.addPrompt(image, "Sky");
    vault.addVersion(p3, "Sky", "", "a sky", "", "sky.png", "sky".getBytes(StandardCharsets.UTF_8), "", null, "", null, "", "");
    FileFavourites.load(PromptDb.dir());
    for (PromptVault.StoredFile f : PromptDb.files(false, null)) if (!f.name.equals("sky.png")) FileFavourites.toggle(f);
    java.util.function.Function<Action, JDialog> opened = a -> {
      try {
        final Object before = get("lastBrowse");
        answers.add("(keep)");
        SwingUtilities.invokeLater(() -> {
          try {
            a.run();
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        });
        for (int i = 0; i < 100 && get("lastBrowse") == before; i++) Thread.sleep(100);
        idle();
        return (JDialog) get("lastBrowse");
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    final JDialog[] d = {opened.apply(() -> db.browse(false, null, (name, file) -> {}))};
    java.util.function.Supplier<String> cards = () -> {
      StringBuilder sb = new StringBuilder();
      for (String n : new String[] {"face.png", "walk.mp4", "sky.png"}) if (component(d[0], "refs-pick:" + n) != null) sb.append(n).append(' ');
      return sb.toString().trim();
    };
    java.util.function.Function<String, javax.swing.AbstractButton> button = n -> (javax.swing.AbstractButton) component(d[0], "db-filter:" + n);
    StringBuilder letters = new StringBuilder();
    for (String l : new String[] {"A", "I", "V", "S", "T", "F", "G", "C", "tag:1", "tag:2", "tag:3"}) letters.append(component(d[0], "db-filter:" + l) != null ? l : "-").append(' ');
    out.append("letters: ").append(letters).append('\n');
    out.append("A: ").append(cards.get()).append('\n');
    d[0] = opened.apply(() -> button.apply("F").doClick());
    String favs = cards.get();
    d[0] = opened.apply(() -> button.apply("V").doClick());
    String favVideos = cards.get();
    out.append("F: ").append(favs).append(" | V with F: ").append(favVideos).append('\n');
    boolean switchOk = "face.png walk.mp4".equals(favs) && "walk.mp4".equals(favVideos);
    d[0] = opened.apply(() -> button.apply("F").doClick());
    d[0] = opened.apply(() -> button.apply("I").doClick());
    // C right-clicked with I: Image's subcategories, Portraits a submenu of its own.
    edt(() -> {
      java.awt.Component c = button.apply("C");
      java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
          java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 5, 5, 1, true, java.awt.event.MouseEvent.BUTTON3);
      for (java.awt.event.MouseListener l : c.getMouseListeners()) l.mousePressed(press);
    });
    idle();
    StringBuilder catMenu = new StringBuilder();
    for (Component c : FilterBar.last.getComponents()) if (c instanceof javax.swing.JMenuItem) catMenu.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    javax.swing.JMenu sub = (javax.swing.JMenu) component(FilterBar.last, "cat-menu:Image / Portraits");
    StringBuilder subMenu = new StringBuilder();
    javax.swing.JMenuItem allPortraits = null;
    for (Component c : sub == null ? new Component[0] : sub.getMenuComponents()) {
      if (!(c instanceof javax.swing.JMenuItem)) continue;
      subMenu.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
      if ("cat-pick:Image / Portraits".equals(c.getName())) allPortraits = (javax.swing.JMenuItem) c;
    }
    out.append("C right click (I): ").append(catMenu).append('\n').append("Portraits: ").append(subMenu).append('\n');
    final javax.swing.JMenuItem pick = allPortraits;
    edt(() -> FilterBar.last.setVisible(false));
    d[0] = opened.apply(() -> pick.doClick());
    String inPortraits = cards.get();
    out.append("I with C (Image / Portraits): ").append(inPortraits).append(", title ").append(d[0].getTitle()).append('\n');
    d[0] = opened.apply(() -> button.apply("C").doClick());
    String cOff = cards.get();
    out.append("C clicked (off): ").append(cOff).append('\n');
    boolean catOk = subMenu.indexOf("Faces") >= 0 && "face.png".equals(inPortraits) && "face.png sky.png".equals(cOff);
    d[0] = opened.apply(() -> button.apply("A").doClick());
    // T1 beach; T2 night: either.
    d[0] = opened.apply(() -> ((javax.swing.JComboBox<?>) component(d[0], "db-filter:tag:1")).setSelectedItem("beach"));
    String t1 = cards.get();
    d[0] = opened.apply(() -> ((javax.swing.JComboBox<?>) component(d[0], "db-filter:tag:2")).setSelectedItem("night"));
    String t12 = cards.get();
    StringBuilder rows = new StringBuilder();
    javax.swing.JComboBox<?> box = (javax.swing.JComboBox<?>) component(d[0], "db-filter:tag:3");
    for (int i = 0; i < box.getItemCount(); i++) rows.append(box.getItemAt(i)).append(' ');
    out.append("T list: ").append(rows).append("| T1 beach: ").append(t1).append(" | T1 or T2 night: ").append(t12).append('\n');
    boolean tagOk = "face.png".equals(t1) && "face.png walk.mp4".equals(t12);
    final JDialog shut = d[0];
    edt(() -> shut.dispose());
    FileFilter.reset();
    // Save prompt: Browse… lists the subcategories, then a lower and a same level one; Group with a group made before.
    FileGroups.load(PromptDb.dir());
    FileGroups.make("Walks");
    final StringBuilder seen = new StringBuilder();
    inspectNext = dlg -> {
      ((javax.swing.JComboBox<?>) component(dlg, "save-prompt-main")).getEditor().setItem("Image");
      ((JTextField) component(dlg, "save-prompt-name")).setText("Walk");
      ((javax.swing.AbstractButton) component(dlg, "save-prompt-sub-browse")).doClick();
      for (Component c : SavePromptDialog.lastBrowse.getComponents()) if (c instanceof javax.swing.JMenuItem) seen.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
      SavePromptDialog.lastBrowse.setVisible(false);
      javax.swing.JComboBox<?> group = (javax.swing.JComboBox<?>) component(dlg, "save-prompt-group");
      seen.append("groups: ");
      for (int i = 0; i < group.getItemCount(); i++) seen.append(group.getItemAt(i)).append(" | ");
      group.setSelectedItem("Walks");
    };
    answers.add("OK");
    final PromptSave.Choice[] got = new PromptSave.Choice[1];
    final boolean[] done = new boolean[1];
    final PromptVault lib = vault;
    SwingUtilities.invokeLater(() -> {
      PromptRun.Sheet sheet = new PromptRun.Sheet();
      sheet.name = "Walk";
      got[0] = SavePromptDialog.ask((Pulsekit) frame, lib, sheet);
      done[0] = true;
    });
    for (int i = 0; i < 100 && !done[0]; i++) Thread.sleep(100);
    idle();
    inspectNext = null;
    out.append("Save prompt Browse: ").append(seen).append('\n').append("chosen: ").append(got[0] == null ? "none" : got[0].main + ", group " + got[0].group).append('\n');
    boolean saveOk = seen.indexOf(PromptSave.ADD_LOWER) >= 0 && seen.indexOf(PromptSave.ADD_SAME) >= 0 && got[0] != null && "Walks".equals(got[0].group);
    // Picture ready's right click menu: Add tag, Add to category.
    File runs = new File(System.getProperty("user.home"), "runs");
    runs.mkdirs();
    final File look = new File(runs, "look.png");
    Files.write(look.toPath(), "look".getBytes(StandardCharsets.UTF_8));
    MediaBrowser.answer = q -> q.startsWith("Tags") ? "sunset" : "Image / Portraits";
    final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];
    edt(() -> menu[0] = GroupMenus.fileMenu((Pulsekit) frame, look));
    StringBuilder items = new StringBuilder();
    for (Component c : menu[0].getComponents()) if (c instanceof javax.swing.JMenuItem) items.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    edt(() -> ((javax.swing.JMenuItem) component(menu[0], "file-tag")).doClick());
    edt(() -> ((javax.swing.JMenuItem) component(menu[0], "file-category")).doClick());
    idle();
    MediaBrowser.answer = null;
    String key = MediaTags.key("look.png", look.length());
    out.append("Picture ready menu: ").append(items).append('\n').append("look.png: tags ").append(MediaTags.tags(key)).append(", category ").append(MediaTags.category(key)).append('\n');
    boolean readyOk = MediaTags.tags(key).contains("sunset") && "Image / Portraits".equals(MediaTags.category(key));
    if (!switchOk || !catOk || !tagOk || !saveOk || !readyOk) throw new AssertionError(out.toString());
  }
  /**
   * Each thumbnail screen has its own F G C switches and T lists: F on in Browse DB left the Media
   * browser's folders "empty". When the switches do keep everything out, the browser says so. A
   * full size video held: it plays, or pauses.
   */
  void s128_switches_per_screen() throws Exception {
    final PromptDb db = (PromptDb) get("promptDb");
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    DbFilter.current = "A";
    PromptDb.vault().addLibraryFile("beach.png", "xbeach".getBytes(StandardCharsets.UTF_8), "Imported", 1);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> db.browse(false, null, (name, file) -> {}));
    for (int i = 0; i < 100 && get("lastBrowse") == null; i++) Thread.sleep(100);
    idle();
    final JDialog first = (JDialog) get("lastBrowse");
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> ((javax.swing.AbstractButton) component(first, "db-filter:F")).doClick());
    for (int i = 0; i < 100 && get("lastBrowse") == first; i++) Thread.sleep(100);
    idle();
    final JDialog second = (JDialog) get("lastBrowse");
    out.append("Browse DB: ").append(second.getTitle()).append('\n');
    edt(() -> second.dispose());
    File media = new File(System.getProperty("user.home"), "pics");
    media.mkdirs();
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
    javax.imageio.ImageIO.write(img, "png", new File(media, "sea.png"));
    final JDialog[] dlg = new JDialog[1];
    answers.add("(keep)");
    inspectNext = d -> dlg[0] = d;
    SwingUtilities.invokeLater(() -> mb.open(media));
    for (int i = 0; i < 100 && dlg[0] == null; i++) Thread.sleep(50);
    idle();
    boolean empty = component(dlg[0], "media-empty") != null;
    out.append("Media browser: empty line ").append(empty).append(", F lit ").append(((javax.swing.AbstractButton) component(dlg[0], "media-filter:F")).isSelected()).append('\n');
    // F on here: nothing in the folder is a favourite, and the line says why.
    final javax.swing.AbstractButton f = (javax.swing.AbstractButton) component(dlg[0], "media-filter:F");
    edt(() -> f.doClick());
    idle();
    javax.swing.JLabel line = (javax.swing.JLabel) component(dlg[0], "media-empty");
    out.append("F on in the Media browser says: ").append(line == null ? "none" : line.getText()).append('\n');
    boolean lineOk = line != null && line.getText().startsWith("Nothing here matches Favourites");
    FileFilter.use(FileFilter.BROWSE);
    boolean browseKept = FileFilter.fav;
    out.append("Browse DB's F kept: ").append(browseKept).append('\n');
    edt(() -> dlg[0].dispose());
    if (empty || !lineOk || !browseKept) throw new AssertionError(out.toString());
  }
  /**
   * Playlists: Add to playlist / Add to default playlist on a Browse DB card; File > Playlist
   * viewer/editor: the Editor (thumbnails, ▲, the item menu's Start viewer) and the viewer (◀ ▶);
   * Merge .pls runs PlayList.java on two lists; a .pls run from PyJav's code area plays it; the
   * Media browser's folder playlist: Save pls.
   */
  void s129_playlists() throws Exception {
    final PromptDb db = (PromptDb) get("promptDb");
    final File dir = PromptDb.dir();
    DbFilter.current = "A";
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(32, 24, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", png);
    PromptDb.vault().addLibraryFile("beach.png", png.toByteArray(), "Imported", 1);
    // Browse DB: a card's right click has Add to playlist and Add to default playlist.
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> db.browse(false, null, (name, file) -> {}));
    for (int i = 0; i < 100 && get("lastBrowse") == null; i++) Thread.sleep(100);
    idle();
    final JDialog browse = (JDialog) get("lastBrowse");
    edt(() -> {
      java.awt.Component card = component(browse, "refs-pick:beach.png");
      java.awt.event.MouseEvent press = new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
          java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3);
      for (java.awt.event.MouseListener l : card.getMouseListeners()) l.mousePressed(press);
    });
    javax.swing.JMenu add = (javax.swing.JMenu) component(db.lastCardMenu, "add-to-playlist");
    StringBuilder rows = new StringBuilder();
    for (java.awt.Component c : add.getMenuComponents()) rows.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    out.append("card menu: Add to playlist ").append(rows).append("Add to default playlist ").append(component(db.lastCardMenu, "add-to-default-playlist") != null).append('\n');
    edt(() -> ((javax.swing.JMenuItem) component(db.lastCardMenu, "add-to-default-playlist")).doClick());
    out.append("added: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    edt(() -> browse.dispose());
    boolean addOk = Playlist.load(dir, Playlist.DEFAULT).items.size() == 1;
    File media = new File(System.getProperty("user.home"), "pics");
    media.mkdirs();
    File sky = new File(media, "sky.png");
    javax.imageio.ImageIO.write(img, "png", sky);
    Playlist.addTo(dir, Playlist.DEFAULT, sky.getAbsolutePath(), "sky.png");
    // File > Playlist viewer/editor.
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.open((Pulsekit) frame, Playlist.DEFAULT));
    for (int i = 0; i < 100 && (PlaylistWindow.last == null || !PlaylistWindow.last.isShowing()); i++) Thread.sleep(100);
    idle();
    Thread.sleep(500);
    idle();
    final JDialog w = PlaylistWindow.last;
    JLabel thumb = (JLabel) component(w, "playlist-thumb:1");
    out.append("editor: ").append(((JLabel) component(w, "playlist-info:0")).getText().replaceAll("<[^>]+>", " ").trim()).append(" | thumbnail ").append(thumb.getIcon() != null).append('\n');
    boolean editorOk = thumb.getIcon() != null;
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-up:1")).doClick());
    out.append("after ▲: ").append(Playlist.load(dir, Playlist.DEFAULT).items.get(0).title).append('\n');
    boolean moveOk = "sky.png".equals(Playlist.load(dir, Playlist.DEFAULT).items.get(0).title);
    // The item's menu: Start viewer; ▶ the next.
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-menu:0")).doClick());
    StringBuilder menu = new StringBuilder();
    for (java.awt.Component c : PlaylistWindow.lastMenu.getComponents()) if (c instanceof javax.swing.JMenuItem) menu.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    out.append("item menu: ").append(menu).append('\n');
    answers.add("(keep)");
    final javax.swing.JMenuItem start = (javax.swing.JMenuItem) component(PlaylistWindow.lastMenu, "playlist-item-start");
    edt(() -> PlaylistWindow.lastMenu.setVisible(false));
    SwingUtilities.invokeLater(() -> start.doClick());
    for (int i = 0; i < 100 && (PlaylistWindow.lastViewer == null || !PlaylistWindow.lastViewer.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog v = PlaylistWindow.lastViewer;
    String first = ((JLabel) component(v, "viewer-where")).getText() + " (" + PlaylistWindow.viewerNote + ")";
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-next")).doClick());
    String second = ((JLabel) component(v, "viewer-where")).getText() + " (" + PlaylistWindow.viewerNote + ")";
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-zoom-in")).doClick());
    edt(() -> v.dispose());
    out.append("viewer: ").append(first).append(" | ▶ ").append(second).append(" | zoom kept ").append(Playlist.settings(dir).zoom).append('\n');
    boolean viewerOk = first.startsWith("1 / 2") && first.endsWith("(picture)") && second.startsWith("2 / 2") && Playlist.settings(dir).zoom > 1f;
    // Merge .pls: PlayList.java on two lists, the merged one shown.
    File a = new File(media, "a.pls");
    File b = new File(media, "b.pls");
    Files.write(a.toPath(), ("[playlist]\nFile1=" + sky.getAbsolutePath() + "\nFile2=/x/y.mp4\nNumberOfEntries=2\n").getBytes(StandardCharsets.UTF_8));
    Files.write(b.toPath(), "[playlist]\nFile1=/x/y.mp4\nFile2=/x/z.mp3\nTitle2=Zed\n".getBytes(StandardCharsets.UTF_8));
    PlaylistWindow.choose = new File[] {a, b};
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-merge")).doClick());
    idle();
    out.append("Merge .pls: ").append(w.getTitle()).append(", ").append(PlaylistWindow.shown.items.size()).append(" files, ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    boolean mergeOk = "a + b".equals(PlaylistWindow.shown.name) && PlaylistWindow.shown.items.size() == 3;
    edt(() -> w.dispose());
    // A .pls in PyJav's code area: Run plays it with PlayList.java.
    final PyJav pj = (PyJav) get("pyJav");
    answers.add("(keep)");
    final JDialog before = PlaylistWindow.last;
    pj.runPls("trip.pls", "[playlist]\nFile1=" + sky.getAbsolutePath() + "\nTitle1=Sky\n");
    for (int i = 0; i < 300 && (PlaylistWindow.last == before || !PlaylistWindow.last.isShowing()); i++) Thread.sleep(100);
    idle();
    out.append("PyJav .pls Run: ").append(PlaylistWindow.last.getTitle()).append(", log ends ").append(pj.lastPlsRun == null ? "-" : pj.lastPlsRun.log.trim().replaceAll("(?s).*\n", "")).append('\n');
    boolean runOk = "Playlist · trip".equals(PlaylistWindow.last.getTitle());
    final JDialog shown = PlaylistWindow.last;
    edt(() -> shown.dispose());
    // The Media browser's folder playlist: Save pls.
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    MediaPlaylist.add(dir, media.getAbsolutePath(), sky.getAbsolutePath(), "sky.png", sky.length());
    String said = mb.savePls(media, "Folder list");
    out.append("Save pls: ").append(said).append('\n');
    boolean savePlsOk = Playlist.load(dir, "Folder list").items.size() == 1;
    out.append("playlists: ").append(Playlist.names(dir)).append('\n');
    if (!addOk || !editorOk || !moveOk || !viewerOk || !mergeOk || !runOk || !savePlsOk) throw new AssertionError(out.toString());
  }
  /**
   * A video's gallery menu has Add to playlist and Add to default playlist, then Cancel; the Media
   * browser's Add to playlist is right under Add to default playlist; Picture ready's right click and
   * Video ready have Add to playlist.
   */
  void s130_playlist_menus_cancel() throws Exception {
    final PromptDb db = (PromptDb) get("promptDb");
    final File dir = PromptDb.dir();
    PromptDb.vault().addLibraryFile("walk.mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'}, "Imported", 1);
    PromptVault.StoredFile walk = null;
    for (PromptVault.StoredFile f : PromptDb.files(false, null)) if (f.name.equals("walk.mp4")) walk = f;
    final PromptVault.StoredFile video = walk;
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> db.gallery(0));
    for (int i = 0; i < 100 && get("lastGallery") == null; i++) Thread.sleep(100);
    idle();
    final JDialog gallery = (JDialog) get("lastGallery");
    final javax.swing.JButton card = (javax.swing.JButton) component(gallery, "gallery:walk.mp4");
    edt(() -> db.menu(video, card, () -> {}));
    edt(() -> db.lastFileMenu.setVisible(false));
    edt(() -> gallery.dispose());
    java.util.function.Function<javax.swing.JPopupMenu, String> items = m -> {
      StringBuilder sb = new StringBuilder();
      for (Component c : m.getComponents()) if (c instanceof javax.swing.JMenuItem) sb.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
      return sb.toString();
    };
    Component[] cs = db.lastFileMenu.getComponents();
    out.append("video's gallery menu: ").append(items.apply(db.lastFileMenu)).append('\n');
    boolean galleryOk = component(db.lastFileMenu, "add-to-playlist") != null && component(db.lastFileMenu, "add-to-default-playlist") != null && MenuCancel.NAME.equals(cs[cs.length - 1].getName());
    // The Media browser's file menu.
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    File pics = new File(System.getProperty("user.home"), "pics");
    pics.mkdirs();
    final File sea = new File(pics, "sea.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", sea);
    final MediaDir.Entry e = new MediaDir.Entry();
    e.name = "sea.png";
    e.id = sea.getAbsolutePath();
    e.kind = MediaDir.PICTURE;
    e.size = sea.length();
    edt(() -> mb.menu(e, sea, new javax.swing.JButton(), 0, 0));
    Component[] mc = mb.lastMenu.getComponents();
    int def = -1;
    for (int i = 0; i < mc.length; i++) if (mc[i] instanceof javax.swing.JMenuItem && MediaDir.MENU[2].equals(((javax.swing.JMenuItem) mc[i]).getText())) def = i;
    out.append("Media browser menu: ").append(items.apply(mb.lastMenu)).append('\n');
    // Add to playlist… in its alphabetical place (the menu is in alphabetical order).
    boolean mediaOk = def >= 0 && component(mb.lastMenu, "add-to-playlist") != null && MenuCancel.NAME.equals(mc[mc.length - 1].getName());
    // Picture ready's right click menu (a run's file).
    final javax.swing.JPopupMenu[] ready = new javax.swing.JPopupMenu[1];
    edt(() -> ready[0] = GroupMenus.fileMenu((Pulsekit) frame, sea));
    out.append("Picture ready menu: ").append(items.apply(ready[0])).append('\n');
    boolean readyOk = component(ready[0], "add-to-playlist") != null;
    // Video ready: Add to playlist, Default (OK), then Close.
    File runs = new File(System.getProperty("user.home"), "runs");
    runs.mkdirs();
    final File clip = new File(runs, "walk.mp4");
    Files.write(clip.toPath(), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});
    final PyJav pj = (PyJav) get("pyJav");
    answers.add(Playlist.ADD);
    answers.add("OK");
    answers.add("Close");
    edt(() -> pj.offerVideo(clip, 1));
    idle();
    Playlist def2 = Playlist.load(dir, Playlist.DEFAULT);
    out.append("Video ready: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    boolean videoOk = def2.items.size() == 1 && clip.getAbsolutePath().equals(def2.items.get(0).file);
    if (!galleryOk || !mediaOk || !readyOk || !videoOk) throw new AssertionError(out.toString());
  }
  /**
   * The Playlist window's Viewer tab: Loop video (it turns Auto mode off), volume, Mute, speed and
   * zoom, kept for the viewer; the viewer's controls all on screen (none cut off), Loop video and
   * Auto there too.
   */
  void s131_playlist_loop_controls() throws Exception {
    final File dir = PromptDb.dir();
    File pics = new File(System.getProperty("user.home"), "pics");
    pics.mkdirs();
    File sky = new File(pics, "sky.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", sky);
    Playlist.addTo(dir, "Loops", sky.getAbsolutePath(), "sky.png");
    Playlist.Settings set = Playlist.settings(dir);
    set.auto(true);
    Playlist.saveSettings(dir, set);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.open((Pulsekit) frame, "Loops"));
    for (int i = 0; i < 100 && (PlaylistWindow.last == null || !PlaylistWindow.last.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog w = PlaylistWindow.last;
    edt(() -> ((javax.swing.JTabbedPane) component(w, "playlist-tabs")).setSelectedIndex(1));
    StringBuilder there = new StringBuilder();
    for (String n : new String[] {"playlist-loop", "playlist-auto", "playlist-volume", "playlist-mute", "playlist-speed", "playlist-zoom-in", "playlist-zoom-out", "playlist-fit"}) there.append(component(w, n) != null ? "" : n + " missing ");
    out.append("Viewer tab controls: ").append(there.length() == 0 ? "all there" : there).append('\n');
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-loop")).doClick());
    boolean loopOk = Playlist.settings(dir).loop && !Playlist.settings(dir).auto && !((javax.swing.AbstractButton) component(w, "playlist-auto")).isSelected();
    out.append("Loop video ticked: loop ").append(Playlist.settings(dir).loop).append(", auto ").append(Playlist.settings(dir).auto).append('\n');
    edt(() -> ((javax.swing.JSlider) component(w, "playlist-volume")).setValue(40));
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-mute")).doClick());
    edt(() -> ((javax.swing.JComboBox<?>) component(w, "playlist-speed")).setSelectedIndex(4));
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-zoom-in")).doClick());
    Playlist.Settings kept = Playlist.settings(dir);
    out.append("kept: volume ").append(kept.volume).append(", mute ").append(kept.mute).append(", speed ").append(Playlist.speedText(kept.speed)).append(", zoom ").append(kept.zoom).append('\n');
    boolean keptOk = kept.volume == 40 && kept.mute && kept.speed == 1.5f && kept.zoom > 1f && kept.loop;
    // The viewer: every control inside the window (none cut off).
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> ((javax.swing.AbstractButton) component(w, "playlist-start")).doClick());
    for (int i = 0; i < 100 && (PlaylistWindow.lastViewer == null || !PlaylistWindow.lastViewer.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog v = PlaylistWindow.lastViewer;
    StringBuilder cut = new StringBuilder();
    for (String n : new String[] {"viewer-play", "viewer-pause", "viewer-stop", "viewer-loop", "viewer-auto", "viewer-mute", "viewer-volume", "viewer-speed", "viewer-zoom-out", "viewer-zoom-in", "viewer-fit", "viewer-close"}) {
      Component c = component(v, n);
      if (c == null) {
        cut.append(n).append(" missing ");
        continue;
      }
      java.awt.Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), v.getContentPane());
      if (!c.isShowing() || r.x + r.width > v.getContentPane().getWidth() || r.y + r.height > v.getContentPane().getHeight() || c.getParent().getHeight() < c.getY() + c.getHeight()) cut.append(n).append(" cut off ");
    }
    out.append("viewer controls: ").append(cut.length() == 0 ? "all on screen" : cut).append(", Loop video ").append(((javax.swing.AbstractButton) component(v, "viewer-loop")).isSelected()).append('\n');
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-auto")).doClick());
    boolean autoOn = Playlist.settings(dir).auto && !Playlist.settings(dir).loop && !((javax.swing.AbstractButton) component(v, "viewer-loop")).isSelected();
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-loop")).doClick());
    boolean loopOn = Playlist.settings(dir).loop && !Playlist.settings(dir).auto;
    out.append("viewer Auto: ").append(autoOn).append(", then Loop video: ").append(loopOn).append('\n');
    edt(() -> v.dispose());
    edt(() -> w.dispose());
    if (there.length() > 0 || !loopOk || !keptOk || cut.length() > 0 || !autoOn || !loopOn) throw new AssertionError(out.toString());
  }
  /**
   * A run's workflow id is kept as soon as it is printed; when Pulsekit was closed during the run,
   * File > Downloads offers Resume download (a button, and in the run's right-click menu) with
   * --workflow. Adding a file a playlist has already asks first. The viewer shows mm:ss / mm:ss for
   * a video, and pictures and videos keep their own zoom.
   */
  void s132_downloads_resume_playlist_again() throws Exception {
    final File dir = PromptDb.dir();
    SogniDownloads.init(dir);
    long id = SogniDownloads.started("SogniVideo.java", System.currentTimeMillis());
    SogniDownloads.argv(id, java.util.Arrays.asList("--prompt", "a walk", "--duration", "5"));
    JavaRun.Live live = new JavaRun.Live();
    live.download(id);
    live.add("Workflow: wf_abc123\n");
    SogniDownloads.init(null);
    SogniDownloads.init(dir);
    final SogniDownloads.Run r = SogniDownloads.run(id);
    out.append("after a restart: ").append(SogniDownloads.runLabel(r).replaceAll("\\d\\d-\\d\\d \\d\\d:\\d\\d", "<when>")).append(", args ").append(SogniDownloads.resumeArgs(r)).append('\n');
    boolean keptOk = SogniDownloads.canResume(r) && SogniDownloads.resumeArgs(r).contains("wf_abc123");
    DownloadsScreen.tab = SogniDownloads.FINISHED;
    final JDialog[] dlg = new JDialog[1];
    answers.add("(keep)");
    inspectNext = d -> dlg[0] = d;
    SwingUtilities.invokeLater(() -> DownloadsScreen.show((Pulsekit) frame));
    for (int i = 0; i < 100 && dlg[0] == null; i++) Thread.sleep(50);
    idle();
    inspectNext = null;
    boolean button = component(dlg[0], "downloads-resume:" + id) != null;
    edt(() -> DownloadsScreen.runMenu((Pulsekit) frame, new javax.swing.JPanel(), r, null, 0, 0));
    StringBuilder menu = new StringBuilder();
    for (Component c : DownloadsScreen.lastRunMenu.getComponents()) if (c instanceof javax.swing.JMenuItem) menu.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    out.append("Downloads: Resume download button ").append(button).append(", the run's menu ").append(menu).append('\n');
    edt(() -> dlg[0].dispose());
    boolean downloadsOk = button && menu.toString().startsWith(SogniDownloads.RESUME);
    // A playlist: the same file again is asked about (Add again).
    File pics = new File(System.getProperty("user.home"), "pics");
    pics.mkdirs();
    File sky = new File(pics, "sky.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16, 12, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", sky);
    File clip = new File(pics, "walk.mp4");
    Files.write(clip.toPath(), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});
    edt(() -> PlaylistWindow.confirmAdd((Pulsekit) frame, "Again", sky.getAbsolutePath(), "sky.png"));
    final StringBuilder asked = new StringBuilder();
    inspectNext = d -> asked.append(d.getTitle());
    answers.add("Add again");
    edt(() -> PlaylistWindow.confirmAdd((Pulsekit) frame, "Again", sky.getAbsolutePath(), "sky.png"));
    idle();
    inspectNext = null;
    out.append("again: asked \"").append(asked).append("\", then ").append(Playlist.load(dir, "Again").items.size()).append(" items, ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    boolean againOk = asked.toString().contains("already") && Playlist.load(dir, "Again").items.size() == 2;
    // The viewer: a picture at its zoom, a video at its own (and its mm:ss / mm:ss).
    Playlist.addTo(dir, "Again", clip.getAbsolutePath(), "walk.mp4");
    Playlist.Settings set = Playlist.settings(dir);
    set.zoom = 1.5f;
    set.zoomVideo = 1f;
    set.auto(false);
    Playlist.saveSettings(dir, set);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.viewer((Pulsekit) frame, frame, Playlist.load(dir, "Again"), 0));
    for (int i = 0; i < 100 && (PlaylistWindow.lastViewer == null || !PlaylistWindow.lastViewer.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog v = PlaylistWindow.lastViewer;
    String pictureZoom = ((JLabel) component(v, "viewer-zoom")).getText();
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-next")).doClick());
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-next")).doClick());
    String videoZoom = ((JLabel) component(v, "viewer-zoom")).getText();
    String time = ((JLabel) component(v, "viewer-time")).getText();
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-zoom-in")).doClick());
    Playlist.Settings kept = Playlist.settings(dir);
    edt(() -> v.dispose());
    out.append("viewer: picture ").append(pictureZoom).append(", video ").append(videoZoom).append(", time ").append(time).append(" | after Zoom + on the video: picture ").append(kept.zoom).append(", video ").append(kept.zoomVideo).append('\n');
    boolean viewerOk = "150%".equals(pictureZoom) && "100%".equals(videoZoom) && time.matches("\\d\\d:\\d\\d / .*") && kept.zoom == 1.5f && kept.zoomVideo > 1f;
    if (!keptOk || !downloadsOk || !againOk || !viewerOk) throw new AssertionError(out.toString());
  }







  /**
   * PlayList.java --nogui writes the Default playlist (or the lists merged) into --outfile, and
   * --batchdir --midi / --midibatch list a folder's MIDI files; none of it opens the window. A
   * playlist is the file list of the batch programs (Midi2HydroPattern --dir, MidiBatch --list),
   * picked in Params with Playlist.
   */
  void s133_playlist_text_mode_and_pls_lists() throws Exception {
    final File dir = PromptDb.dir();
    File home = new File(System.getProperty("user.home"));
    File mids = new File(home, "mids");
    mids.mkdirs();
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    Files.write(new File(mids, "a.mid").toPath(), Engine.encodeMidi(cells, 100));
    Files.write(new File(mids, "b.mid").toPath(), Engine.encodeMidi(cells, 90));
    Files.write(new File(mids, "notes.txt").toPath(), "x".getBytes(StandardCharsets.UTF_8));
    Playlist.addTo(dir, Playlist.DEFAULT, new File(mids, "a.mid").getAbsolutePath(), "a.mid");
    Playlist.addTo(dir, Playlist.DEFAULT, new File(mids, "notes.txt").getAbsolutePath(), "notes.txt");
    String src = new String(ProgramFiles.read("Java", "PlayList.java"), StandardCharsets.UTF_8);
    JavaRun.Result def = JavaRun.run("PlayList.java", src, null, Playlist.withDefault("PlayList.java", java.util.Arrays.asList("--nogui"), dir));
    JavaRun.Result batch = JavaRun.run("PlayList.java", src, null, java.util.Arrays.asList("--batchdir", mids.getAbsolutePath(), "--midi", "--outfile", "mids.pls"));
    JavaRun.Result mb = JavaRun.run("PlayList.java", src, null, java.util.Arrays.asList("--batchdir", mids.getAbsolutePath(), "--midibatch"));
    java.util.function.Function<JavaRun.Result, String> made = r -> {
      StringBuilder b = new StringBuilder();
      for (JavaRun.FileOut f : r.files) b.append(f.name).append("(").append(new String(f.bytes, StandardCharsets.UTF_8).split("\n").length).append(" lines) ");
      return b.toString().trim();
    };
    String keptDef = PlaylistWindow.kept((Pulsekit) frame, def);
    out.append("--nogui: ").append(made.apply(def)).append(", kept ").append(keptDef).append(" | --batchdir --midi: ").append(made.apply(batch)).append(" | --midibatch: ").append(made.apply(mb)).append('\n');
    for (String line : (def.log + batch.log + mb.log).split("\n")) if (line.startsWith("Succeeded") || line.startsWith("Failed")) out.append("  ").append(line).append('\n');
    byte[] midsPls = null, outTxt = null;
    for (JavaRun.FileOut f : batch.files) if (f.name.equals("mids.pls")) midsPls = f.bytes;
    for (JavaRun.FileOut f : mb.files) if (f.name.equals("out.txt")) outTxt = f.bytes;
    boolean runsOk = keptDef == null && def.log.contains("Default playlist: 2 files") && !def.log.contains("Playlist: ") && midsPls != null && outTxt != null
        && Playlist.parse(new String(midsPls, StandardCharsets.UTF_8), "m").items.size() == 2 && new String(outTxt, StandardCharsets.UTF_8).contains("b.mid");
    // The lists given to the batch programs.
    File pls = new File(home, "mids.pls");
    Files.write(pls.toPath(), midsPls);
    File list = new File(home, "out.txt");
    Files.write(list.toPath(), outTxt);
    JavaRun.Result hydro = JavaRun.run("Midi2HydroPattern.java", new String(ProgramFiles.read("Java", "Midi2HydroPattern.java"), StandardCharsets.UTF_8), null,
        java.util.Arrays.asList("--dir", pls.getAbsolutePath(), "--outdir", new File(home, "h2").getAbsolutePath()));
    JavaRun.Result batchList = JavaRun.run("MidiBatch.java", new String(ProgramFiles.read("Java", "MidiBatch.java"), StandardCharsets.UTF_8), null,
        java.util.Arrays.asList("--list", pls.getAbsolutePath(), "--outdir", new File(home, "mb").getAbsolutePath(), "--seed", "3"));
    String[] h2 = new File(home, "h2").list();
    out.append("Midi2HydroPattern --dir mids.pls: ").append(h2 == null ? 0 : h2.length).append(" patterns | MidiBatch --list mids.pls: ");
    for (String line : batchList.log.split("\n")) if (line.startsWith("Listed")) out.append(line);
    out.append('\n');
    boolean programsOk = h2 != null && h2.length == 2 && batchList.log.contains("Listed 2 MIDI files in mids.pls");
    // Params: Playlist on MidiBatch's --dir and --list (not --outdir); it fills the row.
    StringBuilder rows = new StringBuilder();
    ProgramParams.Param listRow = null;
    for (ProgramParams.Param pr : ProgramParams.parse(new String(ProgramFiles.read("Java", "MidiBatch.java"), StandardCharsets.UTF_8))) {
      if (pr.pls) rows.append(pr.token).append(' ');
      if (pr.token.equals("--list")) listRow = pr;
    }
    Playlist.addTo(dir, "Grooves", new File(mids, "b.mid").getAbsolutePath(), "b.mid");
    final String[] values = {""};
    final JLabel chosen = new JLabel();
    final ProgramParams.Param row = listRow;
    final javax.swing.JButton[] b = new javax.swing.JButton[1];
    edt(() -> b[0] = PyJav.playlistButton(row, values, 0, chosen));
    edt(() -> b[0].doClick());
    StringBuilder menu = new StringBuilder();
    for (Component c : PyJav.lastPlaylistMenu.getComponents()) if (c instanceof javax.swing.JMenuItem) menu.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    for (Component c : PyJav.lastPlaylistMenu.getComponents()) if (c instanceof javax.swing.JMenuItem && "params-pls-pick:Grooves".equals(c.getName())) edt(() -> ((javax.swing.JMenuItem) c).doClick());
    out.append("Params rows with Playlist: ").append(rows.toString().trim()).append(" | menu ").append(menu).append("| picked ").append(chosen.getText()).append(" ").append(new File(values[0]).getName()).append('\n');
    boolean paramsOk = rows.toString().trim().equals("--dir --list") && menu.toString().contains("Grooves") && "Grooves.pls".equals(chosen.getText()) && values[0].endsWith("Grooves.pls");
    if (!runsOk || !programsOk || !paramsOk) throw new AssertionError(out.toString() + def.log + batch.log + mb.log + hydro.log + batchList.log);
  }

  /**
   * A run going on is shown again when its program is picked after another. Picture ready's Add
   * tag… has Select all / Clear all and ticks the tags used last (pictures and videos each their
   * own); the group used last is marked. The playlist viewer has First and Last; the editor Save
   * and Save as….
   */
  void s134_running_again_ready_recent_first_last_save() throws Exception {
    final File dir = PromptDb.dir();
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    call("showView", "py");
    JavaRun.Live live = new JavaRun.Live();
    live.add("Status: processing\n");
    PyJav.RUNNING.put("SogniVideo.java", new PyJav.Shown(live, "", new String[1], new String[1]));
    call("selectListedProgram", "Java", "SogniPedit.java");
    edt(() -> log.setText("SogniPedit's Params saved"));
    String other = log.getText();
    call("selectListedProgram", "Java", "SogniVideo.java");
    idle();
    String back = log.getText();
    String top = ((JLabel) get("nowPlaying")).getText();
    // Output has the run's lines (with their times); its state is beside Output's headline.
    String state = ((JLabel) component(frame, "pyjav-status")).getText();
    out.append("SogniPedit shows: ").append(other).append(" | SogniVideo again: ").append(back.replaceAll("\\d\\d:\\d\\d:\\d\\d", "hh:mm:ss")).append(" | status ")
        .append(state.replaceAll("\\d+:\\d\\d", "m:ss")).append(" | top ").append(top).append('\n');
    boolean runningOk = back.matches("\\d\\d:\\d\\d:\\d\\d Status: processing") && state.startsWith("SogniVideo.java: Running") && top.startsWith("SogniVideo") && top.contains("Running");
    live.done = true;
    PyJav.RUNNING.clear();
    // Add tag…: Select all, then Clear all, then two ticked.
    MediaTags.load(dir);
    MediaTags.setTags(MediaTags.key("old.png", 10), java.util.Arrays.asList("beach", "night", "sun"));
    final StringBuilder labels = new StringBuilder();
    inspectNext = d -> {
      javax.swing.AbstractButton all = (javax.swing.AbstractButton) component(d, "media-tag-all");
      labels.append(all.getText());
      all.doClick();
      labels.append(" > ").append(all.getText());
      all.doClick();
      labels.append(" > ").append(all.getText());
      ((javax.swing.AbstractButton) component(d, "media-tag:night")).doClick();
      ((javax.swing.AbstractButton) component(d, "media-tag:sun")).doClick();
    };
    answers.add("OK");
    edt(() -> TagDialogs.tags((Pulsekit) frame, frame, "made.png", 20, null, ReadyRecent.IMAGE));
    idle();
    java.util.List<String> made = MediaTags.tags(MediaTags.key("made.png", 20));
    final StringBuilder ticked = new StringBuilder();
    inspectNext = d -> {
      for (String t : new String[] {"beach", "night", "sun"}) if (((javax.swing.AbstractButton) component(d, "media-tag:" + t)).isSelected()) ticked.append(t).append(' ');
    };
    answers.add("OK");
    edt(() -> TagDialogs.tags((Pulsekit) frame, frame, "next.png", 30, null, ReadyRecent.IMAGE));
    idle();
    final StringBuilder videoTicked = new StringBuilder();
    inspectNext = d -> {
      for (String t : new String[] {"beach", "night", "sun"}) if (((javax.swing.AbstractButton) component(d, "media-tag:" + t)).isSelected()) videoTicked.append(t).append(' ');
    };
    answers.add("Cancel");
    edt(() -> TagDialogs.tags((Pulsekit) frame, frame, "clip.mp4", 40, null, ReadyRecent.VIDEO));
    idle();
    inspectNext = null;
    out.append("Add tag: ").append(labels).append(" | saved ").append(made).append(" | next picture ticked ").append(ticked.toString().trim()).append(" | a video ticked [").append(videoTicked.toString().trim()).append("]\n");
    boolean tagsOk = labels.toString().equals("Select all > Clear all > Select all") && made.toString().equals("[night, sun]") && ticked.toString().trim().equals("night sun") && videoTicked.length() == 0;
    // The group used last: marked.
    FileGroups.load(dir);
    FileGroups.toggle("Trip", new String[] {"k-old"});
    FileGroups.toggle("Work", new String[] {"k-old"});
    ReadyRecent.load(dir);
    ReadyRecent.setGroup(ReadyRecent.IMAGE, "Work");
    String[] marked = ReadyRecent.marked(ReadyRecent.IMAGE, FileGroups.menu(new String[] {"k-new"}), FileGroups.names());
    out.append("groups: ").append(String.join(" | ", marked)).append(" (videos: ").append(String.join(" | ", ReadyRecent.marked(ReadyRecent.VIDEO, FileGroups.menu(new String[] {"k-new"}), FileGroups.names()))).append(")\n");
    boolean groupOk = String.join("|", marked).contains("Work" + ReadyRecent.LAST_USED) && !String.join("|", marked).contains("Trip" + ReadyRecent.LAST_USED);
    // The viewer: First and Last.
    File pics = new File(System.getProperty("user.home"), "first-last");
    pics.mkdirs();
    for (String n : new String[] {"a.png", "b.png", "c.png"}) {
      File f = new File(pics, n);
      javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", f);
      Playlist.addTo(dir, "Three", f.getAbsolutePath(), n);
    }
    Playlist.Settings set = Playlist.settings(dir);
    set.auto(false);
    Playlist.saveSettings(dir, set);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.viewer((Pulsekit) frame, frame, Playlist.load(dir, "Three"), 1));
    for (int i = 0; i < 100 && (PlaylistWindow.lastViewer == null || !PlaylistWindow.lastViewer.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog v = PlaylistWindow.lastViewer;
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-last")).doClick());
    String atLast = ((JLabel) component(v, "viewer-where")).getText();
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-first")).doClick());
    String atFirst = ((JLabel) component(v, "viewer-where")).getText();
    edt(() -> v.dispose());
    out.append("viewer: Last ").append(atLast).append(", First ").append(atFirst).append('\n');
    boolean firstLastOk = atLast.contains("c.png") && atFirst.contains("a.png");
    // The editor: Save asks where first (Save as…); then Save writes there.
    File saved = new File(System.getProperty("user.home"), "saved-three.pls");
    PlaylistWindow.saveAsAnswer = saved;
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.open((Pulsekit) frame, "Three"));
    for (int i = 0; i < 100 && (PlaylistWindow.last == null || !PlaylistWindow.last.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog w = PlaylistWindow.last;
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-save")).doClick());
    int firstSave = saved.isFile() ? Playlist.parse(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8), "x").items.size() : -1;
    PlaylistWindow.saveAsAnswer = new File(System.getProperty("user.home"), "not-this.pls");
    // An edit (as the editor's ✕ does): the same playlist, kept as it goes.
    PlaylistWindow.shown.remove(0);
    Playlist.save(dir, PlaylistWindow.shown);
    edt(() -> ((javax.swing.AbstractButton) component(w, "playlist-save")).doClick());
    int secondSave = Playlist.parse(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8), "x").items.size();
    boolean notAsked = !PlaylistWindow.saveAsAnswer.isFile();
    String saveAs = ((javax.swing.AbstractButton) component(w, "playlist-export")).getText();
    PlaylistWindow.saveAsAnswer = null;
    edt(() -> w.dispose());
    out.append("editor: Save first saved ").append(firstSave).append(" files; Save again (not asked ").append(notAsked).append(") saved ").append(secondSave).append(" | ").append(saveAs).append('\n');
    boolean saveOk = firstSave == 3 && secondSave == 2 && notAsked && Playlist.SAVE_AS.equals(saveAs);
    if (!runningOk || !tagsOk || !groupOk || !firstLastOk || !saveOk) throw new AssertionError(out.toString());
  }

  /**
   * A file of 20 MB or more is imported into separate storage after asking (streamed, never read
   * whole); then Ref files open on S, its card says In separate storage and its groups. A MIDI card
   * shows its length, bars, beats and drum MIDI, and has ▶ ❚❚ ■. Category… adds a category and puts
   * a file in a new one; Add into file set says when there are no file sets.
   */
  void s135_linked_import_cards_categories() throws Exception {
    final File dir = PromptDb.dir();
    final PromptDb db = (PromptDb) get("promptDb");
    File home = new File(System.getProperty("user.home"));
    File big = new File(home, "Passing Ships.wav");
    try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(big, "rw")) {
      raf.setLength(21L * 1024 * 1024);
      raf.write(new byte[] {'R', 'I', 'F', 'F'});
    }
    final StringBuilder asked = new StringBuilder();
    inspectNext = d -> asked.append(texts(d.getContentPane()).get(0).split("\\.")[0]).append(" ... ").append(texts(d.getContentPane()).get(0).contains("separate storage"));
    answers.add("Import");
    answers.add("OK");
    answers.add("(keep)");
    final JDialog before = (JDialog) get("lastGallery");
    SwingUtilities.invokeLater(() -> db.importPicked(big, false));
    for (int i = 0; i < 100 && (get("lastGallery") == before || !((JDialog) get("lastGallery")).isShowing()); i++) Thread.sleep(100);
    idle();
    inspectNext = null;
    JDialog gallery = (JDialog) get("lastGallery");
    javax.swing.JButton card = (javax.swing.JButton) component(gallery, "gallery:Passing Ships.wav");
    PromptVault.StoredFile ships = null;
    for (PromptVault.StoredFile f : PromptDb.vault().referenceFiles()) if (f.name.equals("Passing Ships.wav")) ships = f;
    out.append("asked: ").append(asked).append(" | ").append(gallery.getTitle()).append(" on ").append(((javax.swing.AbstractButton) component(gallery, "gallery-filter:S")).isSelected() ? "S" : "?")
        .append(" | card ").append(card != null && card.getText().contains("In separate storage")).append(" | linked ").append(ships != null && ships.linked).append(", ").append(ships == null ? 0 : ships.size / (1024 * 1024)).append(" MB\n");
    boolean importOk = asked.toString().contains("true") && card != null && card.getText().contains("In separate storage") && ships != null && ships.linked;
    edt(() -> gallery.dispose());
    // Its groups, and a drum MIDI with its length.
    FileGroups.load(dir);
    FileGroups.toggle("Trip", FileGroups.keys(ships));
    FileGroups.toggle("Songs", FileGroups.keys(ships));
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    PromptDb.vault().addLibraryFile("groove.mid", Engine.encodeMidi(cells, 100), "Imported", 1);
    answers.add("(keep)");
    final JDialog was = (JDialog) get("lastGallery");
    SwingUtilities.invokeLater(() -> db.gallery(false));
    for (int i = 0; i < 100 && (get("lastGallery") == was || !((JDialog) get("lastGallery")).isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog g2 = (JDialog) get("lastGallery");
    String shipsText = ((javax.swing.JButton) component(g2, "gallery:Passing Ships.wav")).getText().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    String midiText = ((javax.swing.JButton) component(g2, "gallery:groove.mid")).getText().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    boolean controls = component(g2, "gallery-play:groove.mid") != null && component(g2, "gallery-pause:groove.mid") != null && component(g2, "gallery-stop:groove.mid") != null && component(g2, "gallery-time:Passing Ships.wav") != null;
    edt(() -> g2.dispose());
    out.append("cards: ").append(shipsText.contains("Groups: Trip, Songs \u00b7 In separate storage")).append(" | ").append(midiText.replaceAll(".*(\\d\\d:\\d\\d[^|]*drum MIDI).*", "$1")).append(" | \u25b6 \u275a\u275a \u25a0 ").append(controls).append('\n');
    boolean cardsOk = shipsText.contains("Groups: Trip, Songs \u00b7 In separate storage") && midiText.contains("1 bar \u00b7 4 beats \u00b7 100 BPM 4/4 \u00b7 drum MIDI") && controls;
    // Category…: a new category, then Add into new category.
    final int[] step = {0};
    MediaBrowser.answer = q -> {
      if (q.startsWith("Category for")) return step[0]++ == 0 ? MediaTags.NEW_CATEGORY : MediaTags.INTO_NEW;
      if (q.equals("New category")) return "Beats";
      if (q.equals("Add into new category")) return "Songs";
      return null;
    };
    edt(() -> TagDialogs.category((Pulsekit) frame, frame, "groove.mid", 40, null));
    MediaBrowser.answer = null;
    java.util.List<String> names = MediaTags.categoryNames(PromptDb.vault());
    out.append("Category…: rows end ").append(MediaTags.categoryRows(PromptDb.vault()).subList(names.size() + 1, names.size() + 4)).append(" | Beats made ").append(names.contains("Beats"))
        .append(" | groove.mid in ").append(MediaTags.category(MediaTags.key("groove.mid", 40))).append('\n');
    boolean catOk = names.contains("Beats") && names.contains("Songs") && "Songs".equals(MediaTags.category(MediaTags.key("groove.mid", 40)));
    // Add into file set, with none yet.
    File mid = new File(home, "groove.mid");
    Files.write(mid.toPath(), Engine.encodeMidi(cells, 100));
    final StringBuilder said = new StringBuilder();
    inspectNext = d -> said.append(d.getTitle()).append(": ").append(texts(d.getContentPane()).get(0));
    edt(() -> MediaBrowser.toFileSet((Pulsekit) frame, mid, "groove.mid"));
    idle();
    inspectNext = null;
    out.append("Add into file set: ").append(said).append(" | in the MIDI menu ").append(java.util.Arrays.asList(MediaDir.kindItems("groove.mid", true)).contains(MediaDir.TO_FILE_SET)).append('\n');
    boolean setOk = said.toString().contains("No file sets yet");
    if (!importOk || !cardsOk || !catOk || !setOk) throw new AssertionError(out.toString());
  }

  /**
   * The playlist viewer with a song: "⟳ Loop this song", no speed or zoom, its picture (a drum
   * MIDI's hits); the Viewer tab says media file. SogniMusic's MP3 ready: favourites, group, tags,
   * category, playlist and Convert into WAV (an MP3 decoded beside it).
   */
  void s136_viewer_music_mp3_ready() throws Exception {
    final File dir = PromptDb.dir();
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "music-view");
    media.mkdirs();
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    File mid = new File(media, "beat.mid");
    Files.write(mid.toPath(), Engine.encodeMidi(cells, 100));
    File pic = new File(media, "a.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pic);
    Playlist.addTo(dir, "Mixed", pic.getAbsolutePath(), "a.png");
    Playlist.addTo(dir, "Mixed", mid.getAbsolutePath(), "beat.mid");
    Playlist.Settings set = Playlist.settings(dir);
    set.auto(false);
    Playlist.saveSettings(dir, set);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.viewer((Pulsekit) frame, frame, Playlist.load(dir, "Mixed"), 0));
    for (int i = 0; i < 100 && (PlaylistWindow.lastViewer == null || !PlaylistWindow.lastViewer.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog v = PlaylistWindow.lastViewer;
    String pictureLoop = ((javax.swing.AbstractButton) component(v, "viewer-loop")).getText();
    boolean pictureSpeed = component(v, "viewer-speed").isVisible();
    edt(() -> ((javax.swing.AbstractButton) component(v, "viewer-next")).doClick());
    String songLoop = ((javax.swing.AbstractButton) component(v, "viewer-loop")).getText();
    boolean songLook = component(v, "viewer-speed").isVisible() || component(v, "viewer-zoom-in").isVisible();
    String note = PlaylistWindow.viewerNote;
    edt(() -> v.dispose());
    out.append("viewer: picture ").append(pictureLoop).append(" (speed shown ").append(pictureSpeed).append(") | song ").append(songLoop).append(" (speed or zoom shown ").append(songLook).append("), shows its ").append(note).append('\n');
    boolean viewerOk = pictureLoop.endsWith("file") && pictureSpeed && "\u27f3 Loop this song".equals(songLoop) && !songLook && "sound picture".equals(note);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.open((Pulsekit) frame, "Mixed"));
    for (int i = 0; i < 100 && (PlaylistWindow.last == null || !PlaylistWindow.last.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog w = PlaylistWindow.last;
    String caption = ((javax.swing.AbstractButton) component(w, "playlist-loop")).getText() + " | " + ((javax.swing.AbstractButton) component(w, "playlist-autoplay")).getText();
    edt(() -> w.dispose());
    out.append("Viewer tab: ").append(caption).append('\n');
    boolean captionOk = caption.startsWith("Loop media file") && caption.contains("Autoplay media file");
    // MP3 ready: Add tag (pictures and videos keep their own last tags), Convert into WAV, Close.
    File wav = new File(media, "track.wav");
    short[] tone = new short[22050 / 2];
    for (int i = 0; i < tone.length; i++) tone[i] = (short) (6000 * Math.sin(i * 0.07));
    Files.write(wav.toPath(), AudioIo.encodeWav(tone, 22050));
    final StringBuilder options = new StringBuilder();
    inspectNext = d -> {
      // A WAV is WAV ready now: Convert hits into MIDI in place of Convert into WAV.
      for (String t : new String[] {FileFavourites.ADD_SHORT, FileGroups.ADD, MediaTags.ADD_TAG, MediaTags.ADD_CATEGORY, Playlist.ADD, PyJav.HITS_TO_MIDI}) options.append(find(d.getContentPane(), t) == null ? t + " missing " : "");
      options.insert(0, d.getTitle() + ": ");
    };
    MediaBrowser.answer = q -> q.startsWith("Tags for") ? "drums" : null;
    answers.add(MediaTags.ADD_TAG);
    answers.add("Close");
    final PyJav pj = (PyJav) get("pyJav");
    edt(() -> pj.offerAudio(wav));
    idle();
    MediaBrowser.answer = null;
    inspectNext = null;
    out.append("MP3 ready (a WAV here): ").append(options.toString().trim()).append(" | tags ").append(MediaTags.line(MediaTags.key("track.wav", wav.length()))).append(" | Convert into WAV: ").append(((JLabel) get("nowPlaying")).getText()).append('\n');
    boolean readyOk = options.toString().startsWith("WAV ready:") && !options.toString().contains("missing") && MediaTags.line(MediaTags.key("track.wav", wav.length())).contains("drums");
    if (!viewerOk || !captionOk || !readyOk) throw new AssertionError(out.toString());
  }

  /**
   * The playlist editor's Save and Save as… on a row of their own; DB/Prompts; PlayList.java
   * without a playlist opens the one used last; MidiDrumGen's MIDI ready has the file's items
   * (Render with SF2 and with plugin among them); library cards have Export to disk, an MP3
   * Convert to WAV, which makes a WAV in the same gallery.
   */
  void s137_editor_row_ready_items_export() throws Exception {
    final File dir = PromptDb.dir();
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> PlaylistWindow.open((Pulsekit) frame, "Mp3"));
    for (int i = 0; i < 100 && (PlaylistWindow.last == null || !PlaylistWindow.last.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog w = PlaylistWindow.last;
    boolean ownRow = component(w, "playlist-save").getParent() != component(w, "playlist-pick").getParent() && component(w, "playlist-save").getParent() == component(w, "playlist-export").getParent()
        && component(w, "playlist-save").isShowing();
    edt(() -> w.dispose());
    Playlist.Settings set = Playlist.settings(dir);
    set.last = "Mp3";
    Playlist.saveSettings(dir, set);
    String kept = PlaylistWindow.kept((Pulsekit) frame, new JavaRun.Result("Succeeded: the playlist used last\nPlaylist: Default\n", new java.util.ArrayList<JavaRun.FileOut>(), 0));
    String tab = ((javax.swing.JButton) component(frame, "tab:prompts")).getText();
    out.append("editor: Save and Save as on their own row ").append(ownRow).append(" | run without a playlist opens \"").append(kept).append("\" (the last used) | tab ").append(tab).append('\n');
    boolean quickOk = ownRow && "".equals(kept) && "DB/Prompts".equals(tab);
    // MIDI ready's options.
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    final byte[] groove = Engine.encodeMidi(cells, 100);
    final StringBuilder options = new StringBuilder();
    inspectNext = d -> {
      for (String it : PyJav.midiItems(false)) options.append(find(d.getContentPane(), it) == null ? it + " missing " : "");
      options.insert(0, d.getTitle() + ": ");
    };
    answers.add("Close");
    final PyJav pj = (PyJav) get("pyJav");
    edt(() -> pj.offerMidi("groove.mid", groove));
    idle();
    inspectNext = null;
    out.append("MIDI ready: ").append(options.length() == 0 ? "not shown (no kit sounds here)" : options.toString().trim()).append(" | items ").append(PyJav.midiItems(false)).append('\n');
    boolean midiOk = PyJav.midiItems(false).contains(MediaDir.RENDER_SF2) && PyJav.midiItems(false).contains(MediaDir.RENDER_PLUGIN) && !options.toString().contains("missing");
    // Library cards: Export to disk; an MP3's Convert to WAV.
    short[] tone = new short[22050 * 2];
    for (int i = 0; i < tone.length; i++) tone[i] = (short) (6000 * Math.sin(i * 0.05));
    File wav = new File(System.getProperty("user.home"), "tone.wav");
    Files.write(wav.toPath(), AudioIo.encodeWav(tone, 22050));
    File mp3 = new File(System.getProperty("user.home"), "tone.mp3");
    JavaRun.Result enc = JavaRun.run("Wav2Mp3.java", new String(ProgramFiles.read("Java", "Wav2Mp3.java"), StandardCharsets.UTF_8), null, java.util.Arrays.asList(wav.getAbsolutePath(), mp3.getAbsolutePath()));
    byte[] mp3Bytes = mp3.isFile() ? Files.readAllBytes(mp3.toPath()) : null;
    for (JavaRun.FileOut f : enc.files) if (f.name.endsWith(".mp3")) mp3Bytes = f.bytes;
    PromptDb.vault().addLibraryFile("tone.mp3", mp3Bytes, "Imported", 1);
    final PromptDb db = (PromptDb) get("promptDb");
    PromptVault.StoredFile stored = null;
    for (PromptVault.StoredFile f : PromptDb.vault().referenceFiles()) if (f.name.equals("tone.mp3")) stored = f;
    final PromptVault.StoredFile tone3 = stored;
    edt(() -> db.menu(tone3, new javax.swing.JButton(), () -> {}));
    StringBuilder items = new StringBuilder();
    javax.swing.JMenuItem toWav = null;
    for (Component c : db.lastFileMenu.getComponents()) {
      if (!(c instanceof javax.swing.JMenuItem)) continue;
      String text = ((javax.swing.JMenuItem) c).getText();
      if (text.equals(MediaDir.EXPORT) || text.equals(MediaDir.TO_WAV)) items.append(text).append(" | ");
      if (text.equals(MediaDir.TO_WAV)) toWav = (javax.swing.JMenuItem) c;
    }
    final javax.swing.JMenuItem convert = toWav;
    edt(() -> db.lastFileMenu.setVisible(false));
    if (convert != null) edt(() -> convert.doClick());
    idle();
    boolean made = false;
    for (PromptVault.StoredFile f : PromptDb.vault().referenceFiles()) if (f.name.equals("tone.wav")) made = true;
    out.append("MP3 card menu: ").append(items).append("Convert to WAV made tone.wav in Ref files ").append(made).append(" (").append(((JLabel) get("nowPlaying")).getText()).append(")\n");
    boolean libOk = items.toString().contains(MediaDir.EXPORT) && items.toString().contains(MediaDir.TO_WAV) && made;
    if (!quickOk || !midiOk || !libOk) throw new AssertionError(out.toString() + enc.log);
  }

  /**
   * The Media browser: Add to favourites says what it did (★ In favourites after), a favourite file
   * gets ★; ★ Favourites and ☰ Playlists list folders (a folder's playlist Open); Import file copies
   * a file in; Add to DB asks first about a picture smaller than the last upscaled one or not 4:7.
   */
  void s138_media_favourites_playlists_import_upscale_check() throws Exception {
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    final File pulse = new File(System.getProperty("user.home"), "Pulse");
    pulse.mkdirs();
    final File wide = new File(pulse, "wide.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 48, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", wide);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> mb.open(pulse));
    for (int i = 0; i < 100 && (mb.last == null || !mb.last.isShowing()); i++) Thread.sleep(100);
    idle();
    String before = ((javax.swing.AbstractButton) component(mb.last, "media-favourite")).getText();
    edt(() -> ((javax.swing.AbstractButton) component(mb.last, "media-favourite")).doClick());
    idle();
    String said = MediaBrowser.lastSaid;
    String after = ((javax.swing.AbstractButton) component(mb.last, "media-favourite")).getText();
    edt(() -> ((javax.swing.AbstractButton) component(mb.last, "media-favourite")).doClick());
    idle();
    out.append("Add to favourites: \"").append(before).append("\" -> ").append(said).append(" | button now \"").append(after).append("\" -> ").append(MediaBrowser.lastSaid).append('\n');
    boolean favOk = said.contains("added to favourites") && after.startsWith(MediaDir.STAR) && MediaBrowser.lastSaid.contains("in favourites already");
    // A favourite file, in its folder's playlist.
    FileFavourites.load(PromptDb.dir());
    FileFavourites.toggleKey(MediaDir.groupKey(entryOf(wide)));
    MediaPlaylist.add(PromptDb.dir(), pulse.getAbsolutePath(), wide.getAbsolutePath(), "wide.png", wide.length());
    edt(() -> mb.open(pulse));
    idle();
    String card = ((javax.swing.AbstractButton) component(mb.last, "media-card:wide.png")).getText();
    edt(() -> ((javax.swing.AbstractButton) component(mb.last, "media-favourites-list")).doClick());
    StringBuilder favs = new StringBuilder();
    for (Component c : MediaBrowser.lastFavourites.getComponents()) if (c instanceof javax.swing.JMenuItem) favs.append(((javax.swing.JMenuItem) c).getText()).append(" | ");
    edt(() -> MediaBrowser.lastFavourites.setVisible(false));
    edt(() -> ((javax.swing.AbstractButton) component(mb.last, "media-playlists-list")).doClick());
    StringBuilder lists = new StringBuilder();
    javax.swing.JMenuItem open = null;
    for (Component c : MediaBrowser.lastPlaylists.getComponents()) {
      if (!(c instanceof javax.swing.JMenu)) continue;
      lists.append(((javax.swing.JMenu) c).getText()).append(" | ");
      for (Component k : ((javax.swing.JMenu) c).getMenuComponents()) if ("media-pl-open".equals(k.getName())) open = (javax.swing.JMenuItem) k;
    }
    edt(() -> MediaBrowser.lastPlaylists.setVisible(false));
    final javax.swing.JMenuItem openItem = open;
    answers.add("(keep)");
    if (openItem != null) SwingUtilities.invokeLater(() -> openItem.doClick());
    for (int i = 0; i < 100 && (PlaylistWindow.last == null || !PlaylistWindow.last.isShowing() || !PlaylistWindow.last.getTitle().endsWith("Pulse")); i++) Thread.sleep(100);
    idle();
    String opened = PlaylistWindow.last == null ? "none" : PlaylistWindow.last.getTitle();
    if (PlaylistWindow.last != null) edt(() -> PlaylistWindow.last.dispose());
    out.append("favourite file starred ").append(card.contains(MediaDir.STAR + "wide.png")).append(" | Favourites: ").append(favs).append("Playlists: ").append(lists).append("Open: ").append(opened).append('\n');
    boolean listsOk = card.contains(MediaDir.STAR + "wide.png") && favs.toString().contains("Pulse") && lists.toString().contains("Pulse (1)") && opened.endsWith("Pulse");
    // Import file.
    File outside = new File(System.getProperty("user.home"), "elsewhere");
    outside.mkdirs();
    File song = new File(outside, "song.wav");
    Files.write(song.toPath(), AudioIo.encodeWav(new short[2205], 22050));
    chooseNext = song;
    edt(() -> ((javax.swing.AbstractButton) component(mb.last, "media-import-file")).doClick());
    idle();
    out.append("Import file: ").append(MediaBrowser.lastImport).append(" | there ").append(new File(pulse, "song.wav").isFile()).append('\n');
    boolean importOk = new File(pulse, "song.wav").isFile() && MediaBrowser.lastImport != null && MediaBrowser.lastImport.startsWith("Copied 1 file");
    // Add to DB: asked first.
    final StringBuilder asked = new StringBuilder();
    inspectNext = d -> asked.append(texts(d.getContentPane()).get(0));
    answers.add("Add as it is");
    answers.add("OK");
    edt(() -> mb.menu(entryOf(wide), wide, (java.awt.Component) component(mb.last, "media-card:wide.png"), 0, 0));
    javax.swing.JMenuItem addRef = null;
    for (Component c : mb.lastMenu.getComponents()) if (c instanceof javax.swing.JMenuItem && MediaDir.MENU[0].equals(((javax.swing.JMenuItem) c).getText())) addRef = (javax.swing.JMenuItem) c;
    edt(() -> mb.lastMenu.setVisible(false));
    final javax.swing.JMenuItem add = addRef;
    edt(() -> add.doClick());
    idle();
    inspectNext = null;
    String now = ((JLabel) get("nowPlaying")).getText();
    edt(() -> mb.last.dispose());
    out.append("Add to DB asks: ").append(asked.toString().split("\n")[0]).append(" | Add as it is: ").append(now).append('\n');
    boolean upOk = asked.toString().contains("not 4:7") && now.contains("is in the DB");
    if (!favOk || !listsOk || !importOk || !upOk) throw new AssertionError(out.toString());
  }

  /**
   * Notes 20102026_4 on the desktop: tags by kind (WAV, MP3, MIDI apart) and Use last tags; WAV ready
   * with Convert hits into MIDI; a Sogni workflow in Params' list as soon as it is printed, Copy
   * workflow id; playlists' kinds and the last one per kind; General settings' Sogni tools and file
   * types; Send to zip with a password, and File > Import of the zip (into the DB, or kept as it is).
   */
  void s139_ready_tags_sogni_ids_playlists_settings_zip() throws Exception {
    File dir = PromptDb.dir();
    final File home = new File(System.getProperty("user.home"));
    MediaTags.load(dir);
    ReadyRecent.load(dir);
    ReadyRecent.setTags(ReadyRecent.IMAGE, java.util.Arrays.asList("beach"));
    ReadyRecent.setTags(ReadyRecent.WAV, java.util.Arrays.asList("drums", "loop"));
    ReadyRecent.setTags(ReadyRecent.MIDI, java.util.Arrays.asList("groove"));
    final File pic = new File(home, "sea.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 70, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pic);
    final Pulsekit app = frame instanceof Pulsekit ? (Pulsekit) frame : null;
    final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];
    edt(() -> menu[0] = GroupMenus.fileMenu(app, pic));
    javax.swing.JMenuItem useLast = null;
    for (Component c : menu[0].getComponents()) if ("file-use-last-tags".equals(c.getName())) useLast = (javax.swing.JMenuItem) c;
    final javax.swing.JMenuItem use = useLast;
    if (use != null) edt(() -> use.doClick());
    MediaTags.load(dir);
    String seaTags = MediaTags.line(MediaTags.key("sea.png", pic.length()));
    // WAV ready: its title and Convert hits into MIDI.
    final File wav = new File(home, "beat.wav");
    Files.write(wav.toPath(), AudioIo.encodeWav(new short[4410], 22050));
    final StringBuilder ready = new StringBuilder();
    inspectNext = d -> {
      ready.append(d.getTitle()).append(": ");
      java.util.List<Component> all = new java.util.ArrayList<Component>();
      java.util.LinkedList<Component> todo = new java.util.LinkedList<Component>(java.util.Arrays.asList(d.getContentPane().getComponents()));
      while (!todo.isEmpty()) {
        Component c = todo.removeFirst();
        if (c instanceof javax.swing.AbstractButton) ready.append(((javax.swing.AbstractButton) c).getText()).append(" | ");
        if (c instanceof Container) todo.addAll(java.util.Arrays.asList(((Container) c).getComponents()));
      }
    };
    answers.add("Close");
    final PyJav pj = (PyJav) get("pyJav");
    edt(() -> pj.offerAudio(wav));
    idle();
    inspectNext = null;
    out.append("Use last tags on a picture: ").append(use == null ? "no item" : use.getText()).append(" -> ").append(seaTags).append(" | ").append(ready).append('\n');
    boolean tagsOk = use != null && use.getText().equals("Use last tags: beach") && seaTags.contains("beach") && ready.toString().startsWith("WAV ready:")
        && ready.toString().contains(PyJav.HITS_TO_MIDI) && ready.toString().contains("Use last tags: drums, loop") && !ready.toString().contains(PyJav.TO_WAV);
    // A Sogni run's workflow: in Params' list at once; Copy workflow id.
    SogniHistory.init(new File(home, ".pulsekit"));
    SogniDownloads.init(dir);
    long id = SogniDownloads.started("SogniPedit.java", System.currentTimeMillis());
    JavaRun.Live live = new JavaRun.Live();
    live.download(id);
    live.add("Workflow: wf_desk7\n");
    String during = SogniHistory.entries().isEmpty() ? "none" : SogniHistory.entries().get(0).label();
    edt(() -> {
      pj.showWorkflow("wf_desk7");
      pj.copyWorkflowId();
    });
    String copied = (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
    out.append("Params' list while running: ").append(during).append(" | ").append(pj.copyWorkflow.getText()).append(" -> clipboard ").append(copied).append('\n');
    boolean idsOk = during.contains(SogniHistory.DOWNLOADING) && during.contains("SogniPedit") && "wf_desk7".equals(copied) && pj.copyWorkflow.isVisible();
    // Playlists: kinds and the last one per kind.
    Playlist.addTo(dir, "Pics", "/x/a.png", "a.png");
    Playlist.addTo(dir, "Clips", "/x/b.mp4", "b.mp4");
    final javax.swing.JMenu[] add = new javax.swing.JMenu[1];
    edt(() -> add[0] = PlaylistWindow.addMenu(app, "/x/c.mp4", "c.mp4"));
    String first = add[0].getItem(0).getText();
    out.append("Add to playlist for a video: ").append(first).append('\n');
    boolean plOk = first.equals("Clips (video)" + Playlist.LAST_FOR + "videos");
    // General settings: Sogni tools and file types.
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> GeneralSettings.show(app));
    JDialog settings = null;
    for (int i = 0; i < 100 && settings == null; i++) {
      Thread.sleep(100);
      for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof JDialog && w.isShowing() && "General settings".equals(((JDialog) w).getTitle())) settings = (JDialog) w;
    }
    final JDialog gs = settings;
    boolean boxes = gs != null && component(gs, "settings-show-sogni") != null && component(gs, "settings-show:V") != null;
    edt(() -> ((javax.swing.AbstractButton) component(gs, "settings-sogni-music-only")).doClick());
    java.util.List<String> progs = java.util.Arrays.asList("DrumMidi_CRT.jar", "SogniChat.java", "SogniMusic.java", "SogniVideo.java");
    String musicOnly = AppSettings.programs(progs).toString();
    edt(() -> ((javax.swing.AbstractButton) component(gs, "settings-show-sogni")).doClick());
    String noSogni = AppSettings.programs(progs).toString();
    edt(() -> ((javax.swing.AbstractButton) component(gs, "settings-show-sogni")).doClick());
    edt(() -> ((javax.swing.AbstractButton) component(gs, "settings-show:V")).doClick());
    edt(() -> gs.dispose());
    String letters = String.join(" ", PromptDb.withGroup());
    boolean hidden = !DbFilter.fits("b.mp4", "A");
    AppSettings.setShows("V", true);
    out.append("settings boxes ").append(boxes).append(" | only music and chat ").append(musicOnly).append(" | no Sogni ").append(noSogni).append(" | videos off: ").append(letters).append(", b.mp4 hidden ").append(hidden).append('\n');
    boolean setOk = boxes && musicOnly.equals("[DrumMidi_CRT.jar, SogniChat.java, SogniMusic.java]") && noSogni.equals("[DrumMidi_CRT.jar]") && !letters.contains("V") && hidden;
    // Send to zip: a folder, with a password; then File > Import of it.
    File pulse = new File(home, "Zips/Pulse");
    new File(pulse, "sub").mkdirs();
    Files.copy(wav.toPath(), new File(pulse, "beat.wav").toPath());
    Files.write(new File(pulse, "sub/note.txt").toPath(), "hello".getBytes(StandardCharsets.UTF_8));
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    ZipFlow.answer = q -> "pw42";
    final MediaDir.Entry folder = entryOf(pulse);
    folder.folder = true;
    edt(() -> mb.fileAction(folder, pulse, MediaDir.SEND_ZIP));
    File zip = new File(home, "Zips/Pulse.zip");
    java.util.List<ZipPack.Entry> es = zip.isFile() ? ZipPack.list(zip) : new java.util.ArrayList<ZipPack.Entry>();
    out.append("Send to zip: ").append(ZipFlow.lastSaid).append(" | ").append(es.size()).append(" files, encrypted ").append(ZipPack.encrypted(es)).append('\n');
    boolean sendOk = ZipFlow.lastSaid != null && ZipFlow.lastSaid.startsWith("Zipped Pulse (2 files, with a password)") && es.size() == 2 && ZipPack.encrypted(es);
    answers.add("Yes");
    answers.add(ZipImport.WHERE[ZipImport.DB]);
    answers.add(ZipImport.GALLERY[0]);
    final File z = zip;
    edt(() -> ZipFlow.importZip(app, z));
    idle();
    String imported = ZipFlow.lastSaid;
    String refs = PromptDb.vault().referenceFiles().toString();
    int refCount = PromptDb.vault().referenceFiles().size();
    answers.add("No");
    edt(() -> ZipFlow.importZip(app, z));
    idle();
    String kept = ZipFlow.lastSaid;
    ZipFlow.answer = q -> "wrong";
    edt(() -> ZipFlow.importZip(app, z));
    String wrong = ZipFlow.lastSaid;
    ZipFlow.answer = null;
    out.append("Import: ").append(imported).append(" | refs ").append(refCount).append(" | not extracted: ").append(kept).append(" | wrong password: ").append(wrong).append('\n');
    boolean importOk = imported != null && imported.startsWith("Imported 2 files into Ref files") && refCount >= 2 && kept != null && kept.equals("Pulse.zip is in the DB as a reference file")
        && wrong != null && wrong.startsWith(ZipPack.WRONG_PASSWORD);
    if (!tagsOk || !idsOk || !plOk || !setOk || !sendOk || !importOk) throw new AssertionError(out.toString() + refs);
  }

  /**
   * Notes 20102026_5 on the desktop: File > Database settings (Use encrypted DB, the DBs' space, the
   * list and Use); Send db to zip (a password of at least 4 characters); Import db zip as a
   * secondary DB, and a DB encrypted with another computer's key (its key typed).
   */
  void s140_db_settings_export_import() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    File dir = PromptDb.dir();
    File home = new File(System.getProperty("user.home"));
    PromptVault v = PromptDb.vault();
    long pid = v.addPrompt(v.addCategory("Walks"), "Beach walk");
    byte[] big = new byte[150 * 1024];
    new java.util.Random(5).nextBytes(big);
    v.addVersion(pid, "Beach walk", "", "a walk on the beach", "", "beach.png", big, "", new byte[0], "", new byte[0], "", "");
    v.addLibraryFile("note.txt", "hello".getBytes(StandardCharsets.UTF_8), "Imported", 1);
    String before = v.summary();
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> DbSettings.show(app));
    for (int i = 0; i < 100 && (DbSettings.last == null || !DbSettings.last.isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog d = DbSettings.last;
    boolean parts = component(d, "settings-encrypted") != null && component(d, "db-list") != null && component(d, "db-send-zip") != null && component(d, "db-import-zip") != null;
    String sizes = ((javax.swing.JTextArea) component(d, "db-sizes")).getText();
    out.append("Database settings: parts ").append(parts).append(" | ").append(sizes.replace('\n', ' ')).append('\n');
    boolean dialogOk = parts && sizes.startsWith("Main: ") && sizes.contains("(in use, encrypted)");
    // Send db to zip: a short password refused, then into the file picked.
    DbSettings.answer = q -> "abc";
    edt(() -> ((javax.swing.AbstractButton) component(d, "db-send-zip")).doClick());
    String shortSaid = DbSettings.lastSaid;
    DbSettings.answer = q -> "pw42";
    File zip = new File(home, "pulsekit-db-Pulse-20261020-1400.zip");
    chooseNext = zip;
    edt(() -> ((javax.swing.AbstractButton) component(d, "db-send-zip")).doClick());
    String sent = DbSettings.lastSaid;
    out.append("short password: ").append(shortSaid).append(" | ").append(sent).append('\n');
    boolean sendOk = DbSlots.SHORT_PASSWORD.equals(shortSaid) && sent != null && sent.startsWith("Sent the DB Main (" + before) && ZipPack.encrypted(ZipPack.list(zip));
    // Import db zip: as a new secondary DB; Use it.
    chooseNext = zip;
    answers.add(DbSlots.AS[0]);
    edt(() -> ((javax.swing.AbstractButton) component(d, "db-import-zip")).doClick());
    String imported = DbSettings.lastSaid;
    @SuppressWarnings("unchecked")
    final javax.swing.JComboBox<String> list = (javax.swing.JComboBox<String>) component(d, "db-list");
    edt(() -> list.setSelectedItem("Pulse"));
    edt(() -> ((javax.swing.AbstractButton) component(d, "db-use")).doClick());
    String inUse = DbSlots.active(dir) + ": " + PromptDb.vault().summary() + ", encrypted " + PromptVault.encrypted(dir);
    edt(() -> list.setSelectedItem(DbSlots.MAIN));
    edt(() -> ((javax.swing.AbstractButton) component(d, "db-use")).doClick());
    out.append("Import: ").append(imported).append(" | DBs ").append(DbSlots.names(dir)).append(" | Use Pulse -> ").append(inUse).append('\n');
    boolean importOk = imported != null && imported.startsWith("Imported the DB as Pulse") && inUse.equals("Pulse: " + before + ", encrypted true") && DbSlots.MAIN.equals(DbSlots.active(dir));
    // Another computer's DB: its key typed.
    PromptVault.Keys own = PromptVault.keys;
    final byte[] other = new byte[32];
    other[0] = 9;
    File elsewhere = new File(home, "elsewhere-db");
    elsewhere.mkdirs();
    PromptVault.keys = () -> new javax.crypto.spec.SecretKeySpec(other, "AES");
    PromptVault ev = PromptVault.open(elsewhere);
    ev.addPrompt(ev.addCategory("Far"), "From elsewhere");
    PromptVault.keys = own;
    PromptVault.forget();
    java.util.List<ZipPack.Source> raw = new java.util.ArrayList<ZipPack.Source>();
    for (File f : elsewhere.listFiles()) if (PromptVault.isDbFile(f.getName())) raw.add(new ZipPack.Source(f.getName(), f));
    File rawZip = new File(home, "Laptop.zip");
    ZipPack.write(rawZip, raw, null);
    StringBuilder hex = new StringBuilder();
    for (byte b : other) hex.append(String.format("%02x", b & 0xff));
    DbSettings.answer = q -> q.equals(DbSlots.KEY_NOTE) ? hex.toString() : null;
    chooseNext = rawZip;
    answers.add(DbSlots.AS[0]);
    edt(() -> ((javax.swing.AbstractButton) component(d, "db-import-zip")).doClick());
    String keyed = DbSettings.lastSaid;
    DbSettings.answer = null;
    String sizesAfter = ((javax.swing.JTextArea) component(d, "db-sizes")).getText();
    edt(() -> d.dispose());
    out.append("Another computer's DB: ").append(keyed).append(" | ").append(sizesAfter.replace('\n', ' ')).append('\n');
    boolean keyOk = keyed != null && keyed.startsWith("Imported the DB as Laptop") && sizesAfter.contains("Laptop: ") && sizesAfter.contains("All DBs: ");
    if (!dialogOk || !sendOk || !importOk || !keyOk) throw new AssertionError(out.toString());
  }

  /**
   * Notes 20102026_6 on the desktop: images and videos off keep them out of the galleries and a
   * prompt's thumbnails; Show DB/Prompts tab; the settings master password.
   */
  void s141_hidden_types_prompts_tab_master_password() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    File dir = PromptDb.dir();
    AppSettings.load(dir);
    AppSettings.setShows("I", false);
    AppSettings.setShows("V", false);
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
    PromptVault v = PromptDb.vault();
    v.addLibraryFile("face.png", png.toByteArray(), "Imported", 1);
    v.addLibraryFile("walk.mp4", new byte[2000], "Imported", 1);
    v.addLibraryFile("notes.txt", "hello".getBytes(StandardCharsets.UTF_8), "Imported", 1);
    StringBuilder kept = new StringBuilder();
    for (PromptVault.StoredFile f : FileFilter.keep(v.referenceFiles(), v, "A")) kept.append(f.name).append(' ');
    long id = v.addPrompt(v.categories().get(0).id, "Sky");
    long vid = v.addVersion(id, "Sky", "", "Blue sky", "", "sky.png", png.toByteArray(), "", null, "", null, "ai", "");
    boolean noStrip = PromptDb.slotStrip(v, v.version(vid), 40) == null;
    AppSettings.setShows("I", true);
    boolean stripBack = PromptDb.slotStrip(v, v.version(vid), 40) != null;
    AppSettings.setShows("V", true);
    out.append("Ref files with images and videos off: ").append(kept.toString().trim()).append(" | prompt thumbnails hidden ").append(noStrip).append(", back with images ").append(stripBack).append('\n');
    boolean typesOk = kept.toString().trim().equals("notes.txt") && noStrip && stripBack;
    // Show DB/Prompts tab; the master password.
    GeneralSettings.answer = q -> "open sesame";
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> GeneralSettings.show(app));
    JDialog gs = null;
    for (int i = 0; i < 100 && gs == null; i++) {
      Thread.sleep(100);
      for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof JDialog && w.isShowing() && "General settings".equals(((JDialog) w).getTitle())) gs = (JDialog) w;
    }
    final JDialog d = gs;
    edt(() -> ((javax.swing.AbstractButton) component(d, "settings-show-prompts-tab")).doClick());
    boolean tabGone = !component(app, "tab:prompts").isVisible();
    edt(() -> ((javax.swing.AbstractButton) component(d, "settings-show-prompts-tab")).doClick());
    boolean tabBack = component(app, "tab:prompts").isVisible();
    edt(() -> ((javax.swing.AbstractButton) component(d, "settings-master")).doClick());
    boolean on = AppSettings.masterOn();
    edt(() -> d.dispose());
    boolean locked = AppSettings.locked();
    AppSettings.setShows("V", false);
    boolean refused = AppSettings.shows("V");
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> GeneralSettings.show(app));
    JDialog gs2 = null;
    for (int i = 0; i < 100 && gs2 == null; i++) {
      Thread.sleep(100);
      for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof JDialog && w.isShowing() && w != d && "General settings".equals(((JDialog) w).getTitle())) gs2 = (JDialog) w;
    }
    final JDialog d2 = gs2;
    boolean greyed = !component(d2, "settings-show-sogni").isEnabled() && !component(d2, "settings-master").isEnabled();
    GeneralSettings.answer = q -> "wrong";
    edt(() -> ((javax.swing.AbstractButton) component(d2, "settings-unlock")).doClick());
    boolean stillLocked = AppSettings.locked() && !component(d2, "settings-show-sogni").isEnabled();
    GeneralSettings.answer = q -> "open sesame";
    edt(() -> ((javax.swing.AbstractButton) component(d2, "settings-unlock")).doClick());
    boolean open = !AppSettings.locked() && component(d2, "settings-show-sogni").isEnabled();
    edt(() -> ((javax.swing.AbstractButton) component(d2, "settings-master")).doClick());
    boolean off = !AppSettings.masterOn();
    edt(() -> d2.dispose());
    GeneralSettings.answer = null;
    out.append("DB/Prompts tab: unticked gone ").append(tabGone).append(", ticked shown ").append(tabBack).append(" | master on ").append(on).append(", closed locked ").append(locked)
        .append(", a change refused ").append(refused).append(", greyed ").append(greyed).append(" | wrong password still locked ").append(stillLocked).append(" | unlocked ").append(open)
        .append(" | unticked off ").append(off).append('\n');
    boolean rest = tabGone && tabBack && on && locked && refused && greyed && stillLocked && open && off && !AppSettings.locked();
    if (!typesOk || !rest) throw new AssertionError(out.toString());
  }

  /** Notes 21102026: Drum Tracks' sliders in a window, recent prompts, Delete category, workflow ids, resume. */
  void s142_sliders_recent_prompts_categories_resume() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    File dir = PromptDb.dir();
    edt(() -> call("showView", "pattern"));
    answers.add("(keep)");
    final boolean[] hidden = new boolean[1];
    edt(() -> {
      hidden[0] = !app.knobsRow.isVisible();
      ((javax.swing.JButton) component(app, "drum-sliders")).doClick();
    });
    idle();
    final boolean[] shown = new boolean[1];
    edt(() -> shown[0] = app.slidersWindow != null && app.slidersWindow.isVisible() && app.knobsRow.isVisible() && SwingUtilities.getWindowAncestor(app.knobsRow) == app.slidersWindow);
    edt(() -> app.slidersWindow.dispose());
    idle();
    final boolean[] back = new boolean[1];
    edt(() -> back[0] = app.slidersWindow == null && !app.knobsRow.isVisible() && SwingUtilities.getWindowAncestor(app.knobsRow) == app);
    out.append("sliders: hidden ").append(hidden[0]).append(", in a window ").append(shown[0]).append(", back ").append(back[0]).append('\n');
    boolean slidersOk = hidden[0] && shown[0] && back[0];
    for (int i = 1; i <= 7; i++) PromptHistory.add(dir, "SogniPedit.java", "prompt " + i);
    java.util.List<String> recent = PromptHistory.recent(dir, "SogniPedit.java");
    out.append("recent prompts: ").append(recent).append('\n');
    boolean recentOk = recent.size() == 6 && recent.get(0).equals("prompt 7");
    PromptVault v = PromptDb.vault();
    MediaTags.load(dir);
    MediaTags.addCategory(v, "", "Trips");
    MediaTags.setCategory("V:trip.mp4", "Trips");
    String deleted = MediaTags.deleteCategory(v, "Trips");
    String refused = "";
    try {
      MediaTags.deleteCategory(v, "Video");
    } catch (IllegalArgumentException ex) {
      refused = ex.getMessage();
    }
    out.append("categories: ").append(deleted).append(", file now '").append(MediaTags.category("V:trip.mp4")).append("' | ").append(refused).append('\n');
    boolean categoryOk = MediaTags.categoryId(v, "Trips") == 0 && MediaTags.category("V:trip.mp4").length() == 0 && refused.contains("own");
    SogniHistory.init(new File(dir, "sogni"));
    SogniHistory.started("wf-90", "SogniVideo.java", 1000L);
    String running = SogniHistory.entries().get(0).status;
    SogniHistory.finished("wf-90", true);
    String completed = SogniHistory.entries().get(0).status;
    java.util.List<String> resume = JavaRun.resumeArgs("SogniVideo.java", "", "Workflow: wf-91\nFailed: Unexpected end of stream\n", java.util.Arrays.asList("--prompt", "walk"));
    out.append("workflow: ").append(running).append(" -> ").append(completed).append(" | resume ").append(resume).append('\n');
    boolean wfOk = "running".equals(running) && "completed".equals(completed) && resume != null && resume.contains("wf-91");
    if (!slidersOk || !recentOk || !categoryOk || !wfOk) throw new AssertionError(out.toString());
  }

  /** FlatLaf's modern look at start, in Pulsekit's colors; General settings' Modern look unticked keeps the classic one next time. */
  void s143_modern_look() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    String look = javax.swing.UIManager.getLookAndFeel().getName();
    java.awt.Color accent = javax.swing.UIManager.getColor("Component.accentColor");
    out.append("look: ").append(look).append(", modern ").append(Looks.modern).append(", accent ").append(accent == null ? "none" : String.format("#%06X", accent.getRGB() & 0xFFFFFF)).append('\n');
    boolean modernOk = Looks.modern && look.startsWith("FlatLaf") && accent != null && (accent.getRGB() & 0xFFFFFF) == 0xD9A441;
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> GeneralSettings.show(app));
    idle();
    final javax.swing.JCheckBox[] box = new javax.swing.JCheckBox[1];
    edt(() -> {
      for (java.awt.Window w : java.awt.Window.getWindows()) {
        if (w.isShowing() && w instanceof java.awt.Dialog) {
          java.awt.Component c = component(w, "settings-modern-look");
          if (c != null) box[0] = (javax.swing.JCheckBox) c;
        }
      }
    });
    boolean ticked = box[0] != null && box[0].isSelected();
    final String[] shownLook = new String[1];
    edt(() -> {
      for (java.awt.Window w : java.awt.Window.getWindows()) {
        if (w.isShowing() && w instanceof java.awt.Dialog && component(w, "settings-look-in-use") != null) shownLook[0] = ((JLabel) component(w, "settings-look-in-use")).getText();
      }
    });
    out.append("General settings: ").append(shownLook[0]).append(" | title: ").append(app.getTitle()).append('\n');
    boolean inUseOk = "In use: modern (FlatLaf Dark)".equals(shownLook[0]) && app.getTitle().matches("Pulsekit \\d+");
    edt(() -> box[0].doClick());
    idle();
    boolean off = !AppSettings.modernLook();
    String now = app.nowPlaying.getText();
    edt(() -> {
      for (java.awt.Window w : java.awt.Window.getWindows()) if (w.isShowing() && w instanceof java.awt.Dialog) w.dispose();
    });
    idle();
    out.append("Modern look ticked at first ").append(ticked).append(", unticked off ").append(off).append(" -> ").append(now).append('\n');
    if (!modernOk || !ticked || !off || !inUseOk) throw new AssertionError(out.toString());
  }

  /** Next_todo: Drum Tracks' one row (scrolling at its edges), no bar there; the Sogni boxes; Install for a missing Python library. */
  void s144_drum_row_sogni_shown_pip() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    edt(() -> call("showView", "pattern"));
    idle();
    final StringBuilder labels = new StringBuilder();
    final EdgeScrollRow[] row = new EdgeScrollRow[1];
    final boolean[] state = new boolean[3];
    edt(() -> {
      java.awt.Component sliders = component(app, "drum-sliders");
      row[0] = (EdgeScrollRow) SwingUtilities.getAncestorOfClass(EdgeScrollRow.class, sliders);
      for (java.awt.Component c : row[0].items.getComponents()) labels.append(((javax.swing.AbstractButton) c).getText()).append(' ');
      state[0] = !app.knobsRow.isVisible();
      state[1] = !app.transportBar.isVisible();
    });
    edt(() -> call("showView", "song"));
    idle();
    edt(() -> state[2] = app.transportBar.isVisible());
    out.append("row: ").append(labels.toString().trim()).append(" | sliders hidden ").append(state[0]).append(", bar hidden ").append(state[1]).append(", bar on Song ").append(state[2]).append('\n');
    boolean rowOk = labels.toString().trim().equals("Pattern Fillern Fill CTL ▶ ■ GEN") && state[0] && state[1] && state[2];
    // A narrow row: the mouse at its right edge scrolls it, at its left edge back.
    final int[] at = new int[4];
    edt(() -> {
      EdgeScrollRow r = new EdgeScrollRow();
      for (int i = 0; i < 12; i++) r.items.add(new javax.swing.JButton("Item " + i));
      r.setSize(200, r.getPreferredSize().height);
      r.doLayout();
      r.getViewport().setSize(200, r.getPreferredSize().height);
      r.getViewport().setViewSize(r.items.getPreferredSize());
      at[0] = r.maxOffset();
      r.aim(195);
      for (int i = 0; i < 5; i++) r.scrollBy(r.direction * EdgeScrollRow.STEP);
      at[1] = r.offset();
      r.aim(5);
      for (int i = 0; i < 5; i++) r.scrollBy(r.direction * EdgeScrollRow.STEP);
      at[2] = r.offset();
      r.aim(100);
      at[3] = r.direction;
    });
    out.append("edge scrolling: can scroll ").append(at[0]).append(" px, right edge -> ").append(at[1]).append(", left edge -> ").append(at[2]).append(", middle still ").append(at[3] == 0).append('\n');
    boolean scrollOk = at[0] > 0 && at[1] == 5 * EdgeScrollRow.STEP && at[2] == 0 && at[3] == 0;
    // A small laptop window: Built-in on one row, scrolled by the mouse at its right edge; the grid's rows stay readable.
    edt(() -> {
      app.setExtendedState(java.awt.Frame.NORMAL);
      app.setSize(1000, 560);
      app.validate();
      call("showView", "pattern");
    });
    idle();
    final EdgeScrollRow[] styles = new EdgeScrollRow[1];
    final int[] grid = new int[3];
    edt(() -> {
      styles[0] = (EdgeScrollRow) SwingUtilities.getAncestorOfClass(EdgeScrollRow.class, app.styleBar);
      java.awt.Component first = app.styleBar.getComponent(0);
      grid[0] = app.styleBar.getComponentCount() > 0 && first.getY() == app.styleBar.getComponent(app.styleBar.getComponentCount() - 1).getY() ? 1 : 0;
      javax.swing.JScrollPane gs = (javax.swing.JScrollPane) component(app, "grid-scroll");
      javax.swing.JPanel cells = (javax.swing.JPanel) ((javax.swing.JPanel) gs.getViewport().getView()).getComponent(0);
      grid[1] = cells.getComponent(cells.getComponentCount() - 1).getHeight();
      grid[2] = gs.getHeight();
      // The mouse moves onto the row's right edge (over a chip there): the row scrolls.
      java.awt.Point p = new java.awt.Point(styles[0].getWidth() - 4, styles[0].getHeight() / 2);
      java.awt.Component under = SwingUtilities.getDeepestComponentAt(styles[0], p.x, p.y);
      if (under == null) under = styles[0];
      java.awt.Point q = SwingUtilities.convertPoint(styles[0], p, under);
      java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new java.awt.event.MouseEvent(under, java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, q.x, q.y, 0, false));
    });
    Thread.sleep(600);
    idle();
    final int[] moved = new int[1];
    edt(() -> {
      moved[0] = styles[0].offset();
      styles[0].aim(-1);
    });
    out.append("small window: Built-in one row ").append(grid[0] == 1).append(", mouse at its right edge scrolled it ").append(moved[0]).append(" px | grid rows ")
        .append(grid[1]).append(" px high (least ").append(GridEditor.MIN_ROW).append(") in ").append(grid[2]).append(" px\n");
    boolean smallOk = grid[0] == 1 && moved[0] > 0 && grid[1] >= GridEditor.MIN_ROW;
    // Started on Pattern (the tab kept from last time): the sliders are not on the page.
    final boolean[] restart = new boolean[1];
    SwingUtilities.invokeAndWait(() -> {
      Pulsekit again = new Pulsekit();
      restart[0] = !again.knobsRow.isVisible() && "pattern".equals(again.view);
      again.dispose();
    });
    out.append("started again on Pattern: sliders hidden ").append(restart[0]).append('\n');
    // Fill: three imported sets are all shown (the Fill page was cut at 220 px); the chips are the modern look's.
    edt(() -> {
      for (int i = 1; i <= 3; i++) {
        javax.swing.JButton set = app.chip("\u25b8 Escape from the island " + i + " - 11", false);
        set.setAlignmentX(0f);
        app.importedFillBar.add(set);
      }
      call("showView", "fills");
    });
    idle();
    final int[] fill = new int[3];
    edt(() -> {
      fill[0] = app.fillHost.getHeight();
      fill[1] = app.fillHost.getPreferredSize().height;
      javax.swing.JScrollPane sets = (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, app.importedFillBar);
      fill[2] = sets.getViewport().getHeight() >= app.importedFillBar.getPreferredSize().height ? 1 : 0;
    });
    boolean chipUi = app.styleBar.getComponent(0) instanceof javax.swing.JButton && ((javax.swing.JButton) app.styleBar.getComponent(0)).getUI() instanceof ChipUI;
    out.append("Fill: ").append(fill[0]).append(" of ").append(fill[1]).append(" px shown, all three imported sets in view ").append(fill[2] == 1)
        .append(" | chips rounded (ChipUI) ").append(chipUi).append('\n');
    scrollOk &= fill[0] >= fill[1] && fill[2] == 1 && chipUi;
    // LEN, as on Android: Half picked, a click paints a half-note hit; accented steps are gold.
    edt(() -> call("showView", "pattern"));
    idle();
    final String[] len = new String[1];
    edt(() -> {
      ((javax.swing.JButton) component(app, "len:Half")).doClick();
      int t = 1;
      int step = 2;
      app.gridEditor.buttons[t][step].doClick();
      boolean painted = app.cells[t][step] > 0 && Engine.lenAt(app.lens, t, step) == 8;
      ((javax.swing.JButton) component(app, "len:Half")).doClick();
      app.gridEditor.buttons[t][3].doClick();
      boolean toggled = app.cells[t][3] > 0 && Engine.lenAt(app.lens, t, 3) <= 1;
      boolean gold = app.accents[0] && UiKit.ACCENT.equals(app.gridEditor.accentBtns[0].getBackground());
      len[0] = "half painted " + painted + ", LEN off: a plain hit " + toggled + ", accented step gold " + gold + ", window least " + app.getMinimumSize().width + "x" + app.getMinimumSize().height;
      app.cells[t][step] = 0;
      app.lens[t][step] = 0;
      app.cells[t][3] = 0;
      app.gridEditor.refreshGrid();
    });
    out.append("LEN: ").append(len[0]).append('\n');
    scrollOk &= len[0].startsWith("half painted true, LEN off: a plain hit true, accented step gold true");
    scrollOk &= smallOk && restart[0];
    // The Sogni boxes: a Sogni video's name is left out with the tools off, kept with music and chat only for sound.
    File dir = PromptDb.dir();
    AppSettings.load(dir);
    AppSettings.setShowSogni(false);
    boolean off = !AppSettings.showsFile("sogni-video-walk.mp4") && !AppSettings.showsFile("sogni-Deep-House-1a2b.mp3") && AppSettings.showsFile("beach.png");
    AppSettings.setMusicOnly(true);
    boolean music = !AppSettings.showsFile("sogni-video-walk.mp4") && AppSettings.showsFile("sogni-Deep-House-1a2b.mp3") && AppSettings.showsFile("sogni-chat-cat.png");
    AppSettings.setMusicOnly(false);
    out.append("Sogni tools off: hidden ").append(off).append(" | music and chat only ").append(music).append('\n');
    boolean sogniOk = off && music && AppSettings.showSogni();
    // Install's question names the Python Pulsekit runs (No: nothing is installed).
    answers.add("No");
    edt(() -> app.pyJav.offerPip(java.util.Arrays.asList("numpy")));
    for (int i = 0; i < 100 && PyJav.lastPipQuestion == null; i++) Thread.sleep(50);
    idle();
    String q = PyJav.lastPipQuestion == null ? "" : PyJav.lastPipQuestion;
    String now = app.nowPlaying.getText();
    out.append("Install: ").append(q.split("\n")[0]).append(" -> No: ").append(now).append('\n');
    boolean pipOk = q.startsWith("numpy is not installed for the Python Pulsekit runs") && now.startsWith("Not installed: numpy");
    if (!rowOk || !scrollOk || !sogniOk || !pipOk) throw new AssertionError(out.toString());
  }

  /** Params keep a file argument (vst_render's song.mid); General settings' Python for scripts; another Python that has the package. */
  void s145_params_file_python_choice() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    String src = ProgramFiles.bundledSource("vst_render.py");
    java.util.List<ProgramParams.Param> ps = ProgramParams.parse(src);
    String[] v = ProgramParams.values(ps, "C:\\midi\\song.mid --plugin \"C:\\VST3\\Anarchy Drums.vst3\"", "");
    out.append("vst_render Params from the saved ones, no arguments: ").append(v[0]).append(" | ").append(v[2]).append('\n');
    boolean paramsOk = "C:\\midi\\song.mid".equals(v[0]) && v[2].endsWith("Anarchy Drums.vst3");
    // Two Pythons on the computer (the tests' list): General settings lists them, picking one is kept.
    final String here = PythonRun.whichPython();
    final PythonRun.Found other = new PythonRun.Found("/usr/bin/python3", "3.10.11");
    PyJav.pythonsFound = () -> java.util.Arrays.asList(new PythonRun.Found(here, "3.15.0"), other);
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> GeneralSettings.show(app));
    for (int i = 0; i < 40; i++) {
      Thread.sleep(100);
      idle();
    }
    final String[] items = new String[1];
    edt(() -> {
      for (java.awt.Window w : java.awt.Window.getWindows()) {
        if (!w.isShowing() || !(w instanceof java.awt.Dialog)) continue;
        java.awt.Component c = component(w, "settings-python");
        if (c == null) continue;
        @SuppressWarnings("unchecked")
        javax.swing.JComboBox<String> box = (javax.swing.JComboBox<String>) c;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < box.getItemCount(); i++) sb.append(box.getItemAt(i)).append(" | ");
        items[0] = sb.toString();
        box.setSelectedIndex(box.getItemCount() - 1);
      }
    });
    String picked = AppSettings.pythonPath();
    java.util.List<String> cmd = PythonRun.pythonCommand();
    edt(() -> {
      for (java.awt.Window w : java.awt.Window.getWindows()) if (w.isShowing() && w instanceof java.awt.Dialog) w.dispose();
    });
    idle();
    out.append("Python for scripts: ").append(items[0]).append("\npicked ").append(picked).append(", runs ").append(cmd).append('\n');
    boolean listOk = items[0] != null && items[0].startsWith(GeneralSettings.AUTO_PYTHON) && items[0].contains("Python 3.10.11") && "/usr/bin/python3".equals(picked)
        && cmd.equals(java.util.Collections.singletonList("/usr/bin/python3"));
    AppSettings.setPythonPath("");
    // A missing package another Python has: Use that Python (and the script runs again with it).
    answers.add("Use that Python");
    PyJav.lastPipQuestion = null;
    edt(() -> app.pyJav.offerPip(java.util.Arrays.asList("json")));
    for (int i = 0; i < 100 && PyJav.lastPipQuestion == null; i++) Thread.sleep(50);
    for (int i = 0; i < 20; i++) {
      Thread.sleep(50);
      idle();
    }
    String q = PyJav.lastPipQuestion == null ? "" : PyJav.lastPipQuestion;
    String now = AppSettings.pythonPath();
    out.append("other Python has it: ").append(q.replace('\n', ' ')).append("\n-> scripts run with ").append(now).append('\n');
    boolean otherOk = q.contains("but this Python on the computer has it") && q.contains("3.10.11") && "/usr/bin/python3".equals(now);
    AppSettings.setPythonPath("");
    PyJav.pythonsFound = null;
    // pip's "no matching distribution": the note says pick an older Python.
    String note = PipMissing.noPackageNote(java.util.Arrays.asList("dawdreamer"), "C:\\Python\\pythoncore-3.15\\python.exe", "3.15.0");
    boolean noteOk = PipMissing.noPackageFor("ERROR: No matching distribution found for dawdreamer") && note.contains("Python 3.15.0") && note.contains(AppSettings.PYTHON);
    out.append("no package: ").append(note).append('\n');
    if (!paramsOk || !listOk || !otherOk || !noteOk) throw new AssertionError(out.toString());
  }

  /** PyJav's Output and Code menus: Open in editor window (Save, Save as, Undo, Redo, Find, Back to Code), Save text as. */
  void s146_output_code_editor_window() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    edt(() -> call("showView", "py"));
    idle();
    final String[] menus = new String[2];
    edt(() -> {
      app.pyLog.setText("line one\nline two\nSucceeded");
      menus[0] = items(TextMenu.menu(app.pyLog));
      menus[1] = items(TextMenu.menu(app.pyEditor));
    });
    out.append("Output menu: ").append(menus[0]).append("\nCode menu: ").append(menus[1]).append('\n');
    boolean menusOk = menus[0].contains("Open in editor window") && menus[0].contains("Save text as") && !menus[0].contains("Paste")
        && menus[1].contains("Paste") && menus[1].contains("Open in editor window") && menus[1].contains("Save text as");
    // Output in the editor window: edit, undo, redo, find, Save as, Save.
    answers.add("(keep)");
    edt(() -> app.pyJav.openInEditor(false));
    idle();
    final TextEditorWindow ed = TextEditorWindow.last;
    final String[] got = new String[1];
    File saved = new File(System.getProperty("user.home"), "edited-output.txt");
    TextEditorWindow.saveAsFor = saved;
    edt(() -> {
      boolean full = ed.window.isShowing() && ed.window.getWidth() > 600 && ed.backTo == null;
      ed.text.append("\nmore");
      boolean changed = ed.changed && ed.window.getTitle().startsWith("* ");
      ed.undo();
      boolean undone = ed.text.getText().endsWith("Succeeded");
      ed.redo();
      boolean redone = ed.text.getText().endsWith("more");
      boolean found = ed.findNext("TWO") && ed.text.getSelectionStart() == 14;
      boolean savedAs = ed.saveAs() && !ed.changed;
      ed.text.append("!");
      boolean savedAgain = ed.save();
      got[0] = "window " + full + ", changed " + changed + ", undo " + undone + ", redo " + redone + ", find " + found + ", Save as " + savedAs + ", Save " + savedAgain + ", title " + ed.window.getTitle();
    });
    TextEditorWindow.saveAsFor = null;
    String written = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
    out.append("editor: ").append(got[0]).append(" | file ").append(written.replace('\n', '|')).append('\n');
    boolean editorOk = got[0].startsWith("window true, changed true, undo true, redo true, find true, Save as true, Save true") && written.endsWith("more!");
    // Close with changes asks: Don't save closes.
    answers.add("Don't save");
    edt(() -> ed.text.append("?"));
    edt(() -> ed.close());
    idle();
    boolean closed = !ed.window.isShowing();
    // Code in the editor window: Back to Code puts the edited text in the Code area.
    answers.add("(keep)");
    edt(() -> {
      app.pyEditor.setText("print('hi')\n");
      app.pyJav.openInEditor(true);
    });
    idle();
    final TextEditorWindow code = TextEditorWindow.last;
    final boolean[] back = new boolean[1];
    edt(() -> {
      code.text.setText("print('edited')\n");
      ((javax.swing.JButton) component(code.window, "editor-back")).doClick();
      back[0] = "print('edited')\n".equals(app.pyEditor.getText());
      code.changed = false;
      code.close();
    });
    idle();
    out.append("close with changes: Don't save closed ").append(closed).append(" | Back to Code ").append(back[0]).append('\n');
    if (!menusOk || !editorOk || !closed || !back[0] || code.window.isShowing()) throw new AssertionError(out.toString());
  }

  /** vst_render --bpm: a MIDI with no tempo (120) played at 80 from a copy; PyJav does not pass Pulsekit's tempo to it. */
  void s147_vst_render_bpm() throws Exception {
    File dir = new File(System.getProperty("user.home"), "bpm-test");
    dir.mkdirs();
    // A MIDI with no tempo: a kick at beat 0 and at beat 2 (1.0 s at the default 120).
    byte[] track = {0x00, (byte) 0x99, 36, 100, (byte) 0x87, 0x40, (byte) 0x89, 36, 0, 0x00, (byte) 0x99, 36, 100, 0x00, (byte) 0x89, 36, 0, 0x00, (byte) 0xFF, 0x2F, 0x00};
    java.io.ByteArrayOutputStream mid = new java.io.ByteArrayOutputStream();
    mid.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0});
    mid.write(new byte[] {'M', 'T', 'r', 'k', 0, 0, 0, (byte) track.length});
    mid.write(track);
    File song = new File(dir, "song.mid");
    Files.write(song.toPath(), mid.toByteArray());
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    String check = "import importlib.util, sys\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "a = vr.midi_notes(sys.argv[2])[1]\n"
        + "b = vr.midi_notes(vr.retempo(sys.argv[2], 80, sys.argv[3]))[1]\n"
        + "print('at 120: %.3f s, at 80: %.3f s, notes %d %d' % (a[-1][0], b[-1][0], len(a), len(b)))\n";
    File work = new File(dir, "out");
    work.mkdirs();
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), song.getAbsolutePath(), work.getAbsolutePath()));
    String said = PythonRun.runFor(cmd, 60, null).trim();
    // A bad --bpm is refused before anything is rendered.
    java.util.List<String> bad = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    bad.addAll(java.util.Arrays.asList(script.getAbsolutePath(), song.getAbsolutePath(), "--plugin", "x.vst3", "--bpm", "5"));
    String refused = PythonRun.runFor(bad, 60, null).trim();
    java.util.List<String> argv = JavaRun.argvFor(ProgramFiles.bundledSource("vst_render.py"), 124, "house", 4, 0, song.getAbsolutePath());
    this.out.append("--bpm 80: ").append(said).append("\n--bpm 5: ").append(refused.replace('\n', ' ')).append("\nPyJav's arguments: ").append(argv).append('\n');
    boolean ok = said.startsWith("at 120: 1.000 s, at 80: 1.500 s, notes 2 2") && refused.contains("--bpm is 20 to 400") && !argv.contains("--bpm");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** vst_render --zip: the MIDI played, its --bpm copy, the WAV, DrumMidi's MIDI, the report and the run in one zip. */
  void s148_vst_render_zip() throws Exception {
    File dir = new File(System.getProperty("user.home"), "zip-test");
    File sub = new File(dir, "beats");
    sub.mkdirs();
    File out = new File(dir, "out");
    out.mkdirs();
    byte[] track = {0x00, (byte) 0x99, 36, 100, (byte) 0x83, 0x60, (byte) 0x89, 36, 0, 0x00, (byte) 0xFF, 0x2F, 0x00};
    java.io.ByteArrayOutputStream mid = new java.io.ByteArrayOutputStream();
    mid.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0});
    mid.write(new byte[] {'M', 'T', 'r', 'k', 0, 0, 0, (byte) track.length});
    mid.write(track);
    File song = new File(sub, "song.mid");
    Files.write(song.toPath(), mid.toByteArray());
    File wav = new File(out, "beats/song-Kit.wav");
    wav.getParentFile().mkdirs();
    Files.write(wav.toPath(), new byte[] {1, 2, 3});
    File dm = new File(out, "beats/song-Kit-dm.mid");
    Files.write(dm.toPath(), mid.toByteArray());
    File report = new File(out, "vst-test-Kit.txt");
    Files.write(report.toPath(), "All: Kick 1/1\n".getBytes(StandardCharsets.UTF_8));
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    String check = "import importlib.util, sys, zipfile\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "d, song, out = sys.argv[2], sys.argv[3], sys.argv[4]\n"
        + "copy = vr.retempo(song, 80, out)\n"
        + "opts = {'--outdir': out, '--dir': d, '--state': None, '--effect_state': None, '--carla': None, '--midimap': None}\n"
        + "z = vr.make_zip(out, 'Kit', ['--dir', d, '--test', '--zip', '--bpm', '80'], d, [(song, copy)], [sys.argv[5]], [sys.argv[6]], sys.argv[7], opts, 80.0)\n"
        + "print('names ' + ' '.join(sorted(zipfile.ZipFile(z[0]).namelist())))\n"
        + "print('asks %s' % vr.ask_zip())\n"
        // --zip_mb 1: three MIDIs with 0.6 MB WAVs that do not shrink, a zip each, each with the report and the run.
        + "import os, shutil\n"
        + "played, wavs = [], []\n"
        + "for k in range(3):\n"
        + "    m = os.path.join(d, 'beats', 's%d.mid' % k); shutil.copy(song, m); w = os.path.join(out, 'beats', 's%d-Kit.wav' % k)\n"
        + "    open(w, 'wb').write(os.urandom(600000)); played.append((m, None)); wavs.append(w)\n"
        + "parts = vr.make_zip(out, 'Kit', ['--zip_mb', '1'], d, played, wavs, [], sys.argv[7], opts, None, 1)\n"
        + "print('parts %d: ' % len(parts) + ' | '.join(os.path.basename(p)[-13:] + ' ' + ','.join(sorted(zipfile.ZipFile(p).namelist())) for p in parts))\n"
        + "print('sizes ok %s' % all(os.path.getsize(p) <= 1000000 for p in parts))\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), dir.getAbsolutePath(), song.getAbsolutePath(), out.getAbsolutePath(),
        wav.getAbsolutePath(), dm.getAbsolutePath(), report.getAbsolutePath()));
    String said = PythonRun.runFor(cmd, 60, null).trim();
    // The switch is known to the program and labelled in Params.
    java.util.List<String> bad = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    bad.addAll(java.util.Arrays.asList(script.getAbsolutePath(), "--zip"));
    String plain = PythonRun.runFor(bad, 60, null).trim();
    String label = "";
    for (ProgramParams.Param p : ProgramParams.parse(ProgramFiles.bundledSource("vst_render.py"))) if (p.token.equals("--zip")) label = p.label;
    this.out.append(said).append("\nvst_render.py --zip: ").append(plain).append("\nParams: ").append(label).append('\n');
    boolean ok = said.contains("names dm/beats/song-Kit-dm.mid midi-80bpm/beats/song.mid midi/beats/song.mid vst-render-run.txt vst-test-Kit.txt wav/beats/song-Kit.wav")
        && said.contains("Zipped 5 files") && said.contains("asks None")
        && said.contains("parts 3: -part1of3.zip midi/beats/s0.mid,vst-render-run.txt,vst-test-Kit.txt,wav/beats/s0-Kit.wav | -part2of3.zip midi/beats/s1.mid,")
        && said.contains("sizes ok True") && !plain.contains("unknown switch") && label.startsWith("Zip:");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** Stop script: vst_render ends rendering after the file in progress, then zips (without WAVs) what it made; PythonRun's stop file. */
  void s149_vst_render_stop() throws Exception {
    File dir = new File(System.getProperty("user.home"), "stop-test");
    File in = new File(dir, "in");
    in.mkdirs();
    byte[] track = {0x00, (byte) 0x99, 36, 100, (byte) 0x83, 0x60, (byte) 0x89, 36, 0, 0x00, (byte) 0xFF, 0x2F, 0x00};
    java.io.ByteArrayOutputStream mid = new java.io.ByteArrayOutputStream();
    mid.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0});
    mid.write(new byte[] {'M', 'T', 'r', 'k', 0, 0, 0, (byte) track.length});
    mid.write(track);
    for (int k = 0; k < 4; k++) Files.write(new File(in, "s" + k + ".mid").toPath(), mid.toByteArray());
    Files.write(new File(dir, "kit.xml").toPath(), new byte[0]);
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    File stop = new File(dir, "stop-now");
    // A stand-in for the kit: the second render asks to stop (as Stop script does).
    String check = "import importlib.util, os, sys, glob, zipfile\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "import numpy as np\n"
        + "class G:\n"
        + "    n = 0\n"
        + "    def __init__(self, rate, opts): pass\n"
        + "    def latency(self): return 0\n"
        + "    def render(self, np, midi=None, wav=None, tail=2.0):\n"
        + "        G.n += 0 if str(midi).endswith('vst-check.mid') else 1\n"
        + "        if G.n == 2: open(os.environ['PULSEKIT_STOP_FILE'], 'w').close()\n"
        + "        return np.zeros((2, 4410)) + 0.1, 0.1, 0\n"
        + "vr.Gizmo = G\n"
        + "d = sys.argv[2]\n"
        + "vr.main(['--drumgizmo', os.path.join(d, 'kit.xml'), '--dir', os.path.join(d, 'in'), '--outdir', os.path.join(d, 'out'), '--zip_nowav'])\n"
        + "z = glob.glob(os.path.join(d, 'out', '*.zip'))[0]\n"
        + "print('zip ' + ' '.join(sorted(zipfile.ZipFile(z).namelist())))\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), dir.getAbsolutePath()));
    ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
    pb.environment().put("PULSEKIT_STOP_FILE", stop.getAbsolutePath());
    Process p = pb.start();
    String said = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8).trim();
    p.waitFor();
    // PythonRun: Stop script makes the stop file the script watches; the script ends on it.
    final String[] log = new String[1];
    final String loop = "import os, time\nf = os.environ['PULSEKIT_STOP_FILE']\nfor i in range(600):\n    if os.path.exists(f):\n        print('stopped by the file'); break\n    print('working', i); time.sleep(0.1)\n";
    Thread t = new Thread(() -> log[0] = PythonRun.run(loop, "loop.py", new java.util.ArrayList<String>()).log);
    t.start();
    long until = System.currentTimeMillis() + 20000;
    while (PythonRun.current == null && System.currentTimeMillis() < until) Thread.sleep(50);
    Thread.sleep(500);
    boolean asked = PythonRun.stop();
    t.join(30000);
    String[] lines = log[0] == null ? new String[0] : log[0].trim().split("\n");
    this.out.append(said).append("\nStop script asked: ").append(asked).append(", last line: ").append(lines.length == 0 ? "" : lines[lines.length - 1]).append('\n');
    boolean ok = said.contains("Stopped rendering: 2 of 4 done; the test and the zip use the WAVs made")
        && said.contains("zip midi/s0.mid midi/s1.mid vst-render-run.txt") && !said.contains("wav/")
        && asked && lines.length > 0 && lines[lines.length - 1].equals("stopped by the file") && PythonRun.current == null;
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** vst_render --skip_existing: a whole WAV from before is kept (and its newer DrumMidi MIDI), a cut-off one is made again. */
  void s150_vst_render_skip_existing() throws Exception {
    File dir = new File(System.getProperty("user.home"), "skip-test");
    File in = new File(dir, "in");
    in.mkdirs();
    byte[] track = {0x00, (byte) 0x99, 36, 100, (byte) 0x83, 0x60, (byte) 0x89, 36, 0, 0x00, (byte) 0xFF, 0x2F, 0x00};
    java.io.ByteArrayOutputStream mid = new java.io.ByteArrayOutputStream();
    mid.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0});
    mid.write(new byte[] {'M', 'T', 'r', 'k', 0, 0, 0, (byte) track.length});
    mid.write(track);
    for (int k = 0; k < 3; k++) Files.write(new File(in, "s" + k + ".mid").toPath(), mid.toByteArray());
    Files.write(new File(dir, "kit.xml").toPath(), new byte[0]);
    Files.write(new File(dir, "DrumMidi_CRT.jar").toPath(), new byte[] {1});
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    String check = "import importlib.util, os, sys, shutil, time\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "import numpy as np\n"
        + "d = sys.argv[2]; out = os.path.join(d, 'out'); os.makedirs(out)\n"
        + "vr.write_wav(os.path.join(out, 's0-kit.wav'), np.zeros((2, 4410)) + 0.1, 44100, 16)\n"
        + "vr.write_wav(os.path.join(out, 's1-kit.wav'), np.zeros((2, 4410)) + 0.1, 44100, 16)\n"
        + "b = open(os.path.join(out, 's1-kit.wav'), 'rb').read(); open(os.path.join(out, 's1-kit.wav'), 'wb').write(b[:len(b) // 2])\n"
        + "time.sleep(0.05); shutil.copy(os.path.join(d, 'in', 's0.mid'), os.path.join(out, 's0-kit-dm.mid'))\n"
        + "class G:\n"
        + "    n = 0\n"
        + "    def __init__(self, rate, opts): pass\n"
        + "    def latency(self): return 0\n"
        + "    def render(self, np, midi=None, wav=None, tail=2.0):\n"
        + "        G.n += 0 if str(midi).endswith('vst-check.mid') else 1\n"
        + "        return np.zeros((2, 4410)) + 0.1, 0.1, 0\n"
        + "vr.Gizmo = G\n"
        + "vr.main(['--drumgizmo', os.path.join(d, 'kit.xml'), '--dir', os.path.join(d, 'in'), '--outdir', out, '--skip_existing', '--test', '--drummidi', os.path.join(d, 'DrumMidi_CRT.jar')])\n"
        + "print('renders %d' % G.n)\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), dir.getAbsolutePath()));
    String said = PythonRun.runFor(cmd, 120, null).trim();
    this.out.append(said).append('\n');
    boolean ok = said.contains("Kept s0-kit.wav (already made)") && !said.contains("Kept s1-kit.wav") && said.contains("Wrote s1-kit.wav")
        && said.contains("Kept s0-kit-dm.mid (already made)") && said.contains("renders 2")
        && said.contains("Succeeded: 2 of 3 MIDI files rendered, 1 kept from before");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** vst_render --mp3: each WAV made an MP3 by the jar's Wav2Mp3 and deleted; the zip has the MP3s; --skip_existing keeps them. */
  void s151_vst_render_mp3() throws Exception {
    File dir = new File(System.getProperty("user.home"), "mp3-test");
    File in = new File(dir, "in");
    in.mkdirs();
    byte[] track = {0x00, (byte) 0x99, 36, 100, (byte) 0x83, 0x60, (byte) 0x89, 36, 0, 0x00, (byte) 0xFF, 0x2F, 0x00};
    java.io.ByteArrayOutputStream mid = new java.io.ByteArrayOutputStream();
    mid.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0});
    mid.write(new byte[] {'M', 'T', 'r', 'k', 0, 0, 0, (byte) track.length});
    mid.write(track);
    for (int k = 0; k < 2; k++) Files.write(new File(in, "s" + k + ".mid").toPath(), mid.toByteArray());
    Files.write(new File(dir, "kit.xml").toPath(), new byte[0]);
    File jar = new File(dir, "DrumMidi_CRT.jar");
    Files.write(jar.toPath(), ProgramFiles.read("Java", "DrumMidi_CRT.jar"));
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    String javaBin = new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath();
    String check = "import importlib.util, os, sys, glob, zipfile\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "import numpy as np\n"
        + "class G:\n"
        + "    n = 0\n"
        + "    def __init__(self, rate, opts): pass\n"
        + "    def latency(self): return 0\n"
        + "    def render(self, np, midi=None, wav=None, tail=2.0):\n"
        + "        G.n += 0 if str(midi).endswith('vst-check.mid') else 1\n"
        + "        t = np.arange(44100 * 2) / 44100.0\n"
        + "        return np.vstack([np.sin(2 * np.pi * 220 * t), np.sin(2 * np.pi * 330 * t)]) * 0.3, 2.0, 0\n"
        + "vr.Gizmo = G\n"
        + "d = sys.argv[2]; out = os.path.join(d, 'out')\n"
        + "args = ['--drumgizmo', os.path.join(d, 'kit.xml'), '--dir', os.path.join(d, 'in'), '--outdir', out, '--mp3', '192', '--drummidi', sys.argv[3], '--java', sys.argv[4]]\n"
        + "vr.main(args + ['--zip_nowav'])\n"
        + "print('files ' + ' '.join(sorted(os.listdir(out))[:3]))\n"
        + "z = glob.glob(os.path.join(out, '*.zip'))[0]\n"
        + "print('zip ' + ' '.join(sorted(zipfile.ZipFile(z).namelist())))\n"
        + "vr.main(args + ['--skip_existing'])\n"
        + "print('renders %d' % G.n)\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), dir.getAbsolutePath(), jar.getAbsolutePath(), javaBin));
    String said = PythonRun.runFor(cmd, 180, null).trim();
    // PyJav hands the jar over for --mp3 alone (no --test).
    java.util.List<String> argv = JavaRun.withDrumMidi("vst_render.py", java.util.Arrays.asList("--mp3", "192"), new File(dir, "given.jar"), new byte[] {1});
    this.out.append(said).append("\nPyJav's arguments: ").append(argv).append('\n');
    boolean ok = said.contains("MP3s: 2 at 192 kbps") && said.contains("files s0-kit.mp3 s1-kit.mp3 vst-render-")
        && said.contains("zip midi/s0.mid midi/s1.mid mp3/s0-kit.mp3 mp3/s1-kit.mp3 vst-render-run.txt")
        && said.contains("Kept s0-kit.mp3 (already made)") && said.contains("renders 2") && argv.contains("--drummidi");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** PyJav: the Arguments field as wide as the page, Code and Output parted by a divider, a script's lines in Output as it runs. */
  void s152_pyjav_layout_live() throws Exception {
    call("showView", "py");
    idle();
    edt(() -> ((javax.swing.JButton) component(frame, "pyjav-args-head")).doClick());
    idle();
    final int[] w = new int[3];
    edt(() -> {
      javax.swing.text.JTextComponent args = (javax.swing.text.JTextComponent) get("pyExtra");
      javax.swing.JSplitPane split = (javax.swing.JSplitPane) component(frame, "pyjav-split");
      w[0] = args.getWidth();
      w[1] = split.getWidth();
      int before = split.getTopComponent().getHeight();
      split.setDividerLocation(before - 80);
      split.validate();
      w[2] = before - split.getTopComponent().getHeight();
    });
    this.out.append("arguments ").append(w[0] * 100 / Math.max(1, w[1])).append("% of the page's width, Code smaller by ").append(w[2]).append(" px after dragging\n");
    // A script that prints, waits, then prints again: its first line is in Output while it still runs.
    final JavaRun.Live live = new JavaRun.Live();
    final String script = "import time\nprint('rendered 1 of 2', flush=True)\ntime.sleep(3)\nprint('rendered 2 of 2')\n";
    Thread t = new Thread(() -> {
      JavaRun.track(live);
      PythonRun.run(script, "live.py", new java.util.ArrayList<String>());
    });
    t.start();
    long until = System.currentTimeMillis() + 20000;
    while (!live.text().contains("rendered 1 of 2") && System.currentTimeMillis() < until) Thread.sleep(50);
    String during = JavaRun.runningLog(live, System.currentTimeMillis());
    boolean alive = t.isAlive();
    t.join(30000);
    this.out.append("while running (" + alive + "): ").append(during.replaceAll("Running… \\d+:\\d+", "Running… m:ss").replace('\n', '|')).append('\n');
    boolean ok = w[0] * 100 / Math.max(1, w[1]) >= 90 && w[2] >= 60 && alive && during.startsWith("Running…") && during.endsWith("rendered 1 of 2")
        && live.text().contains("rendered 2 of 2");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** vst_render --map: the test's played notes as the plugin plays them (moved or left out), from pairs or a file; bad maps refused. */
  void s153_vst_render_map() throws Exception {
    File dir = new File(System.getProperty("user.home"), "map-test");
    dir.mkdirs();
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    File file = new File(dir, "anarchy-map.txt");
    Files.write(file.toPath(), "# Anarchy Drums\n45=46\n41=none  57=49\n".getBytes(StandardCharsets.UTF_8));
    String check = "import importlib.util, sys\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "m = vr.note_map('45=46, 41=none'); f = vr.note_map(sys.argv[2])\n"
        + "print('pairs', sorted(m.items(), key=lambda x: x[0]))\n"
        + "print('file', sorted(f.items(), key=lambda x: x[0]))\n"
        + "played = [(0.0, 36), (0.5, 45), (1.0, 41), (1.5, 43)]\n"
        + "print('mapped', vr.mapped(played, m))\n"
        + "rows, shift = vr.score(vr.mapped(played, m), [(0.0, 36), (0.5, 46), (1.0, 42), (1.5, 43)], 2.0)\n"
        + "print('score', vr.score_line(rows))\n"
        + "for bad in ('45', '45=x', '200=46'):\n"
        + "    try: vr.note_map(bad); print('kept', bad)\n"
        + "    except ValueError as e: print('refused', e)\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), file.getAbsolutePath()));
    String said = PythonRun.runFor(cmd, 60, null).trim();
    java.util.List<String> noTest = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    noTest.addAll(java.util.Arrays.asList(script.getAbsolutePath(), "x.mid", "--plugin", "x.vst3", "--map", "45=46"));
    String refused = PythonRun.runFor(noTest, 60, null).trim();
    String label = "";
    for (ProgramParams.Param p : ProgramParams.parse(ProgramFiles.bundledSource("vst_render.py"))) if (p.token.equals("--map")) label = p.label;
    this.out.append(said).append("\nwithout --test: ").append(refused).append("\nParams: ").append(label).append('\n');
    boolean ok = said.contains("pairs [(41, None), (45, 46)]") && said.contains("file [(41, None), (45, 46), (57, 49)]")
        && said.contains("mapped [(0.0, 36), (0.5, 46), (1.5, 43)]")
        && said.contains("score Kick 1/1 P100 R100  Hats 1/1 P 50 R100  Toms 1/1 P100 R100")
        && said.contains("refused 45 is not note=note") && said.contains("refused 45=x is not note=note") && said.contains("refused 200=46: MIDI notes are 0 to 127")
        && refused.contains("--map is for --test") && label.startsWith("Test: notes the plugin plays");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** DrumMidi --snare-leak: a Params switch (on/off), passed on by vst_render --test, and DrumMidi says what it learned or why not. */
  void s154_snare_leak_option() throws Exception {
    File dir = new File(System.getProperty("user.home"), "leak-test");
    dir.mkdirs();
    int at = DrumMidiArgs.index("--snare-leak");
    this.out.append("Params switch: ").append(at >= 0 ? DrumMidiArgs.LABELS[at] : "none").append(", on/off ").append(DrumMidiArgs.onOff("--snare-leak")).append('\n');
    // A short groove: kicks (a low thump), snares (noise with a body) and hats (bright noise), 120 BPM.
    File wav = new File(dir, "groove.wav");
    String make = "import sys, wave, numpy as np\n"
        + "sr = 44100; x = np.zeros(sr * 8); rng = np.random.default_rng(3)\n"
        + "def put(t, s):\n    i = int(t * sr); x[i:i + len(s)] += s[:len(x) - i]\n"
        + "n = np.arange(int(0.3 * sr)) / sr\n"
        + "kick = np.sin(2 * np.pi * 60 * n) * np.exp(-n * 18)\n"
        + "snare = (rng.standard_normal(len(n)) * 0.6 + np.sin(2 * np.pi * 200 * n)) * np.exp(-n * 25)\n"
        + "hat = np.diff(rng.standard_normal(len(n) + 1)) * np.exp(-n * 60) * 0.5\n"
        + "for b in range(16):\n"
        + "    t = 0.25 + b * 0.5\n"
        + "    put(t, kick if b % 2 == 0 else snare * 0.8)\n"
        + "    if b % 4 != 3: put(t, hat)\n"
        + "    put(t + 0.25, hat * 0.7)\n"
        + "y = (x / np.max(np.abs(x)) * 0.8 * 32767).astype('<i2')\n"
        + "w = wave.open(sys.argv[1], 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(y.tobytes()); w.close()\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", make, wav.getAbsolutePath()));
    PythonRun.runFor(cmd, 60, null);
    File jar = new File(dir, "DrumMidi_CRT.jar");
    Files.write(jar.toPath(), ProgramFiles.read("Java", "DrumMidi_CRT.jar"));
    String javaBin = new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath();
    Process p = new ProcessBuilder(javaBin, "-cp", jar.getAbsolutePath(), "DrumMidi_CRT", wav.getAbsolutePath(), new File(dir, "groove-dm.mid").getAbsolutePath(), "--snare-leak")
        .directory(dir).redirectErrorStream(true).start();
    String said = new String(readAll(p.getInputStream()), StandardCharsets.UTF_8);
    p.waitFor();
    String leak = "", settings = "";
    for (String line : said.split("\n")) {
      if (line.startsWith("Snare leak:")) leak = line.trim();
      if (line.startsWith("Settings:")) settings = line.trim();
    }
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    java.util.List<String> vr = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    vr.addAll(java.util.Arrays.asList(script.getAbsolutePath(), "x.mid", "--plugin", "x.vst3", "--snare-leak"));
    String vrSaid = PythonRun.runFor(vr, 60, null).trim();
    String label = "";
    for (ProgramParams.Param q : ProgramParams.parse(ProgramFiles.bundledSource("vst_render.py"))) if (q.token.equals("--snare-leak")) label = q.label;
    this.out.append(settings).append('\n').append(leak.replaceAll("[0-9.]+", "N")).append("\nvst_render: ").append(vrSaid).append("\nvst_render Params: ").append(label).append('\n');
    boolean ok = at >= 0 && DrumMidiArgs.onOff("--snare-leak") && settings.endsWith("--snare-leak") && leak.startsWith("Snare leak:")
        && !vrSaid.contains("unknown switch") && label.startsWith("Snare leak in the test");
    if (!ok) throw new AssertionError(this.out.toString() + said);
  }

  /** vst_render: two source folders, tempo from the name where a MIDI has none, and a second plugin (stand-in renderers). */
  void s155_vst_render_folders_tempo_plugins() throws Exception {
    File dir = new File(System.getProperty("user.home"), "multi-test");
    File fills = new File(dir, "fills/100 rock"), pats = new File(dir, "patterns");
    fills.mkdirs();
    pats.mkdirs();
    byte[] track = {0x00, (byte) 0x99, 36, 100, (byte) 0x87, 0x40, (byte) 0x89, 36, 0, 0x00, (byte) 0x99, 36, 100, 0x00, (byte) 0x89, 36, 0, 0x00, (byte) 0xFF, 0x2F, 0x00};
    byte[] tempo90 = {0x00, (byte) 0xFF, 0x51, 0x03, 0x0A, 0x2C, 0x2B, 0x00, (byte) 0x99, 36, 100, (byte) 0x87, 0x40, (byte) 0x89, 36, 0, 0x00, (byte) 0x99, 36, 100, 0x00, (byte) 0x89, 36, 0, 0x00, (byte) 0xFF, 0x2F, 0x00};
    Files.write(new File(fills, "Fill 1.mid").toPath(), midiFile(track));
    Files.write(new File(pats, "80bpm Groove.mid").toPath(), midiFile(track));
    Files.write(new File(pats, "120 Own Tempo.mid").toPath(), midiFile(tempo90));
    Files.write(new File(dir, "a.vst3").toPath(), new byte[] {1});
    Files.write(new File(dir, "b.vst3").toPath(), new byte[] {1});
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    String check = "import importlib.util, os, sys, types\n"
        + "sys.modules['dawdreamer'] = types.ModuleType('dawdreamer')\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "import numpy as np\n"
        + "class C:\n"
        + "    def __init__(self, daw, rate, opts, switches): pass\n"
        + "    def latency(self): return 0\n"
        + "    def render(self, np, midi=None, wav=None, tail=2.0):\n"
        + "        length, notes = vr.midi_notes(midi)\n"
        + "        return np.zeros((2, int(44100 * (length + 0.1)))) + 0.1, length, 0\n"
        + "vr.Chain = C\n"
        + "d = sys.argv[2]\n"
        + "vr.main(['--plugin', os.path.join(d, 'a.vst3'), '--plugin2', os.path.join(d, 'b.vst3'), '--dir', os.path.join(d, 'fills'), '--dir2', os.path.join(d, 'patterns'),\n"
        + "         '--recursive', '--outdir', os.path.join(d, 'out'), '--bpm_from_name'])\n"
        + "print('made ' + ' '.join(sorted(os.path.relpath(os.path.join(r, f), os.path.join(d, 'out')).replace(os.sep, '/') for r, _, fs in os.walk(os.path.join(d, 'out')) for f in fs)))\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), dir.getAbsolutePath()));
    String said = PythonRun.runFor(cmd, 120, null).trim();
    String labels = "";
    for (ProgramParams.Param q : ProgramParams.parse(ProgramFiles.bundledSource("vst_render.py")))
      if (q.token.equals("--plugin2") || q.token.equals("--dir2") || q.token.equals("--bpm_from_name")) labels += q.token + (q.plugin ? " (plugin)" : q.dir ? " (folder)" : "") + ", ";
    this.out.append(said).append("\nParams: ").append(labels).append('\n');
    boolean ok = said.contains("== Plugin 1 of 2: a.vst3") && said.contains("== Plugin 2 of 2: b.vst3") && said.contains("Plugins: a.vst3 done, b.vst3 done")
        && said.contains("fills/100 rock/Fill 1-a.wav: 1.3 s at 100 BPM (name)") && said.contains("patterns/80bpm Groove-a.wav: 1.6 s at 80 BPM (name)")
        && said.contains("patterns/120 Own Tempo-b.wav: 1.4 s at its own 90 BPM (its name says 120)")
        && said.contains("made fills/100 rock/Fill 1-a.wav fills/100 rock/Fill 1-b.wav patterns/120 Own Tempo-a.wav patterns/120 Own Tempo-b.wav patterns/80bpm Groove-a.wav patterns/80bpm Groove-b.wav")
        && labels.contains("--plugin2 (plugin)") && labels.contains("--dir2 (folder)") && labels.contains("--bpm_from_name");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** A one-track MIDI file (480 ticks a beat) around the track's bytes. */
  private static byte[] midiFile(byte[] track) throws java.io.IOException {
    java.io.ByteArrayOutputStream mid = new java.io.ByteArrayOutputStream();
    mid.write(new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0x01, (byte) 0xE0});
    mid.write(new byte[] {'M', 'T', 'r', 'k', 0, 0, 0, (byte) track.length});
    mid.write(track);
    return mid.toByteArray();
  }

  /** vst_render --map with several plugins: a map file's [plugin] sections, --map2 for the second plugin, the first plugin's --state only. */
  void s156_vst_render_maps_per_plugin() throws Exception {
    File dir = new File(System.getProperty("user.home"), "maps-test");
    dir.mkdirs();
    File script = new File(dir, "vst_render.py");
    Files.write(script.toPath(), ProgramFiles.bundledSource("vst_render.py").getBytes(StandardCharsets.UTF_8));
    File maps = new File(dir, "maps.txt");
    Files.write(maps.toPath(), "# every plugin\n35=none\n[Anarchy Drums]\n45=46\n41=none\n[Artillery]\n57=49\n".getBytes(StandardCharsets.UTF_8));
    String check = "import importlib.util, sys\n"
        + "spec = importlib.util.spec_from_file_location('vr', sys.argv[1]); vr = importlib.util.module_from_spec(spec); spec.loader.exec_module(vr)\n"
        + "m = sys.argv[2]\n"
        + "for p in ('/vst/Anarchy Drums.vst3', '/vst/Artillery2.vst3', '/vst/Other.vst3'):\n"
        + "    notes, part = vr.map_for(m, p)\n"
        + "    print('%s: %s %s' % (p.split('/')[-1], sorted(notes.items(), key=lambda x: x[0]), part))\n"
        + "opts = {'--plugin': 'a.vst3', '--plugin2': 'b.vst3', '--plugin3': 'c.vst3', '--map2': '45=46', '--map3': None}\n"
        + "for plug, args in vr.plugin_runs(['--plugin', 'a.vst3', '--plugin2', 'b.vst3', '--plugin3', 'c.vst3', '--map', 'M', '--map2', '45=46', '--state', 's.fxp', '--test'], opts):\n"
        + "    print('run %s: %s' % (plug, ' '.join(args)))\n";
    java.util.List<String> cmd = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    cmd.addAll(java.util.Arrays.asList("-c", check, script.getAbsolutePath(), maps.getAbsolutePath()));
    String said = PythonRun.runFor(cmd, 60, null).trim();
    java.util.List<String> bad = new java.util.ArrayList<String>(PythonRun.pythonCommand());
    bad.addAll(java.util.Arrays.asList(script.getAbsolutePath(), "x.mid", "--plugin", "x.vst3", "--map2", "45=46"));
    String refused = PythonRun.runFor(bad, 60, null).trim();
    String label = "";
    for (ProgramParams.Param q : ProgramParams.parse(ProgramFiles.bundledSource("vst_render.py"))) if (q.token.equals("--map2")) label = q.label;
    this.out.append(said).append("\n--map2 alone: ").append(refused).append("\nParams: ").append(label).append('\n');
    boolean ok = said.contains("Anarchy Drums.vst3: [(35, None), (41, None), (45, 46)] Anarchy Drums") && said.contains("Artillery2.vst3: [(35, None), (57, 49)] Artillery")
        && said.contains("Other.vst3: [(35, None)] ")
        && said.contains("run a.vst3: --plugin a.vst3 --map M --state s.fxp --test") && said.contains("run b.vst3: --plugin b.vst3 --test --map 45=46")
        && said.contains("run c.vst3: --plugin c.vst3 --map M --test") && refused.contains("--map2 is the map for --plugin2") && label.startsWith("Test: the second plugin");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * vst_render's Params: the form scrolls within the screen (the dialog no taller than it); closed
   * with the window's X, the second and third plugins and the second folder are kept as with OK;
   * Cancel leaves them as they were.
   */
  void s157_params_scroll_x_keeps() throws Exception {
    File dir = new File(System.getProperty("user.home"), "params-test");
    File patterns = new File(dir, "patterns");
    patterns.mkdirs();
    File b = new File(dir, "Artillery.vst3"), c = new File(dir, "Anarchy Drums.vst3");
    Files.write(b.toPath(), new byte[] {1});
    Files.write(c.toPath(), new byte[] {1});
    call("showView", "py");
    call("selectListedProgram", "Python", "vst_render.py");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    call("saveParams", "vst_render.py", "");
    final JDialog[] params = new JDialog[1];
    inspectNext = d -> params[0] = d;
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> {
      try {
        call("openParams");
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    for (int i = 0; i < 100 && (params[0] == null || !params[0].isShowing()); i++) Thread.sleep(100);
    Thread.sleep(300);
    final JDialog d = params[0];
    javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) component(d, "params-scroll");
    int screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
    int screenWide = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
    int formHeight = scroll == null ? 0 : scroll.getViewport().getView().getPreferredSize().height;
    this.out.append("form ").append(formHeight).append(" px, shown ").append(scroll == null ? 0 : scroll.getViewport().getHeight()).append(" px, dialog ")
        .append(d.getHeight()).append(" px high, ").append(d.getWidth() <= screenWide ? "within" : "wider than").append(" the screen's width, screen ").append(screen).append(" px, scrolls ").append(scroll != null && scroll.getVerticalScrollBar().isVisible()).append('\n');
    boolean scrollOk = scroll != null && d.getHeight() <= screen && d.getWidth() <= screenWide && (formHeight <= scroll.getViewport().getHeight() || scroll.getVerticalScrollBar().isVisible());
    // Choose plugin (the second and the third) and Browse (the second folder): the watcher picks them.
    chooseNext = b;
    edt(() -> ((javax.swing.JButton) component(d, "params-file:--plugin2")).doClick());
    idle();
    // The third plugin's chooser (its row empty) starts where the second was picked from.
    String startsIn = PyJav.lastVstFolder().getAbsolutePath().replace(dir.getAbsolutePath(), "~");
    chooseNext = c;
    edt(() -> ((javax.swing.JButton) component(d, "params-file:--plugin3")).doClick());
    idle();
    chooseNext = patterns;
    edt(() -> ((javax.swing.JButton) component(d, "params-dir:--dir2")).doClick());
    idle();
    // The window's X.
    edt(() -> d.dispatchEvent(new java.awt.event.WindowEvent(d, java.awt.event.WindowEvent.WINDOW_CLOSING)));
    idle();
    String kept = String.valueOf(call("loadParams", "vst_render.py")).replace(dir.getAbsolutePath(), "~");
    this.out.append("an empty plugin row's chooser starts in ").append(startsIn).append('\n').append("after X: ").append(kept).append('\n');
    boolean keptOk = kept.contains("--plugin2 ~/Artillery.vst3") && kept.contains("--plugin3") && kept.contains("Anarchy Drums.vst3") && kept.contains("--dir2 ~/patterns");
    // Cancel: the third plugin cleared meanwhile comes back.
    final JDialog[] again = new JDialog[1];
    inspectNext = w -> again[0] = w;
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> {
      try {
        call("openParams");
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    for (int i = 0; i < 100 && (again[0] == null || !again[0].isShowing()); i++) Thread.sleep(100);
    Thread.sleep(300);
    final JDialog e = again[0];
    String shown2 = ((JLabel) component(e, "params-chosen:--plugin2")).getText();
    final javax.swing.JButton clearPlugin3 = (javax.swing.JButton) component(e, "params-clear:--plugin3");
    if (clearPlugin3 != null) edt(clearPlugin3::doClick);
    edt(() -> find(e.getContentPane(), "Cancel").doClick());
    idle();
    String after = String.valueOf(call("loadParams", "vst_render.py")).replace(dir.getAbsolutePath(), "~");
    this.out.append("opened again: --plugin2 shows ").append(shown2).append(", after Cancel: ").append(after).append('\n');
    call("saveParams", "vst_render.py", "");
    boolean cancelOk = "Artillery.vst3".equals(shown2) && clearPlugin3 != null && after.equals(kept) && "~".equals(startsIn);
    if (!scrollOk || !keptOk || !cancelOk) throw new AssertionError(this.out.toString());
  }

  /**
   * PyJav's Code and Output: Home and End take the caret to the start and the end of the line in
   * Code (Ctrl+Home and Ctrl+End to the text's), and to the start and the end of the whole text in
   * Output (it is read, not edited: its newest lines are at the end). The arguments wrap over three
   * lines and stay one line for the program (Enter adds none, a pasted line break is a space).
   */
  void s158_home_end_args_lines() throws Exception {
    call("showView", "py");
    final javax.swing.JTextArea code = (javax.swing.JTextArea) get("pyEditor");
    final javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    final StringBuilder text = new StringBuilder();
    for (int i = 0; i < 60; i++) text.append("line ").append(i).append(" of the text\n");
    edt(() -> {
      code.setText("first line\nsecond line here\nthird");
      log.setText(text.toString());
    });
    final int[] at = new int[6];
    final java.awt.Robot robot = new java.awt.Robot();
    edt(() -> {
      code.requestFocusInWindow();
      code.setCaretPosition(15);
    });
    idle();
    Thread.sleep(300);
    press(robot, java.awt.event.KeyEvent.VK_HOME, false);
    edt(() -> at[0] = code.getCaretPosition());
    press(robot, java.awt.event.KeyEvent.VK_END, false);
    edt(() -> at[1] = code.getCaretPosition());
    press(robot, java.awt.event.KeyEvent.VK_HOME, true);
    edt(() -> at[2] = code.getCaretPosition());
    edt(() -> {
      log.requestFocusInWindow();
      log.setCaretPosition(400);
    });
    idle();
    Thread.sleep(300);
    press(robot, java.awt.event.KeyEvent.VK_HOME, false);
    edt(() -> at[3] = log.getCaretPosition());
    press(robot, java.awt.event.KeyEvent.VK_END, false);
    edt(() -> at[4] = log.getCaretPosition());
    final int logEnd = text.length();
    this.out.append("Code: Home ").append(at[0]).append(", End ").append(at[1]).append(", Ctrl+Home ").append(at[2]).append(" | Output: Home ").append(at[3]).append(", End ").append(at[4])
        .append(" of ").append(logEnd).append('\n');
    boolean keys = at[0] == 11 && at[1] == 27 && at[2] == 0 && at[3] == 0 && at[4] == logEnd;
    // The arguments: three lines high, wrapped; a pasted line break is a space.
    final javax.swing.text.JTextComponent args = (javax.swing.text.JTextComponent) get("pyExtra");
    edt(() -> args.setText("--plugin \"C:\\VST2\\Anarchy Drums.vst3\"\n--plugin2 \"C:\\VST2\\Riot Drums.vst3\""));
    javax.swing.JScrollPane box = (javax.swing.JScrollPane) component(frame, "pyjav-args-scroll");
    int lineHeight = args.getFontMetrics(args.getFont()).getHeight();
    this.out.append("Arguments: ").append(args.getText()).append(" | ").append(args.getClass().getSimpleName()).append(", ").append(box == null ? 0 : box.getPreferredSize().height / lineHeight).append(" lines high\n");
    boolean argsOk = args instanceof javax.swing.JTextArea && ((javax.swing.JTextArea) args).getLineWrap() && args.getText().indexOf('\n') < 0
        && args.getText().contains(".vst3\" --plugin2") && box != null && box.getPreferredSize().height >= 3 * lineHeight;
    if (!keys || !argsOk) throw new AssertionError(this.out.toString());
  }

  private void press(java.awt.Robot robot, int key, boolean ctrl) throws Exception {
    if (ctrl) robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL);
    robot.keyPress(key);
    robot.keyRelease(key);
    if (ctrl) robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL);
    robot.waitForIdle();
    Thread.sleep(150);
    idle();
  }

  /**
   * A run's Output: each line with the time it came ("14:38:05 rendered 1 of 2"), the newest last;
   * beside the headline the run's state ("times.py: Running… 0:02 elapsed"), then "Finished in 0:03".
   */
  void s159_output_times_status() throws Exception {
    final Pulsekit app = (Pulsekit) frame;
    call("showView", "py");
    final String script = "import time\nprint('rendered 1 of 2', flush=True)\ntime.sleep(3)\nprint('rendered 2 of 2')\n";
    edt(() -> {
      app.pyName = "times.py";
      app.pyEditor.setText(script);
      ((javax.swing.text.JTextComponent) get("pyExtra")).setText("");
    });
    edt(() -> call("runPython"));
    final javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    final JLabel status = (JLabel) component(frame, "pyjav-status");
    long until = System.currentTimeMillis() + 20000;
    while (!String.valueOf(log.getText()).contains("rendered 1 of 2") && System.currentTimeMillis() < until) Thread.sleep(50);
    Thread.sleep(1200);
    final String[] during = new String[2];
    edt(() -> {
      during[0] = log.getText();
      during[1] = status.getText();
    });
    until = System.currentTimeMillis() + 30000;
    while (!String.valueOf(status.getText()).contains("Finished") && System.currentTimeMillis() < until) Thread.sleep(100);
    idle();
    final String[] after = new String[2];
    edt(() -> {
      after[0] = log.getText();
      after[1] = status.getText();
    });
    String clock = "\\d\\d:\\d\\d:\\d\\d";
    this.out.append("while running: ").append(during[1].replaceAll("\\d+:\\d\\d", "m:ss")).append(" | ").append(during[0].replaceAll(clock, "hh:mm:ss").replace('\n', '|')).append('\n');
    this.out.append("after: ").append(after[1].replaceAll("\\d+:\\d\\d", "m:ss")).append(" | ").append(after[0].replaceAll(clock, "hh:mm:ss").replace('\n', '|')).append('\n');
    boolean ok = during[1].matches("times\\.py: Running\u2026 0:0\\d elapsed.*") && during[0].matches("(?s)" + clock + " rendered 1 of 2\\s*")
        && after[1].matches("times\\.py: Finished in 0:0\\d") && after[0].matches("(?s).*\\n" + clock + " rendered 1 of 2\\n" + clock + " rendered 2 of 2\\s*");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * Params' Plugins list (vst_render's plugin rows): the plugins in the VST folders (in a maker's
   * folder too), A-Z and Z-A; Info says whether a kit was saved for one with Edit; a favourite has a
   * star and is in Favourites; Test plugin says what vst_render's check found; Use fills the row.
   */
  void s160_plugins_list() throws Exception {
    File home = new File(System.getProperty("user.home"));
    new File(home, ".vst3/Zeta Drums.vst3").mkdirs();
    new File(home, ".vst3/Anarchy Drums.vst3").mkdirs();
    new File(home, ".vst3/Ugritone/Ugritone Drums.vst3").mkdirs();
    new File(home, ".vst").mkdirs();
    Files.write(new File(home, ".vst/Riot.so").toPath(), new byte[] {1});
    new File(home, ".pulsekit/vst").mkdirs();
    Files.write(new File(home, ".pulsekit/vst/Anarchy Drums.state").toPath(), new byte[] {1});
    call("showView", "py");
    call("selectListedProgram", "Python", "vst_render.py");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    call("saveParams", "vst_render.py", "");
    final JDialog[] params = new JDialog[1];
    inspectNext = w -> params[0] = w;
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> {
      try {
        call("openParams");
      } catch (Exception ex) {
        errors.add(ex);
      }
    });
    for (int i = 0; i < 100 && (params[0] == null || !params[0].isShowing()); i++) Thread.sleep(100);
    Thread.sleep(300);
    final JDialog d = params[0];
    PluginsList.last = null;
    answers.add("(keep)");
    SwingUtilities.invokeLater(() -> ((javax.swing.JButton) component(d, "params-plugins:--plugin2")).doClick());
    for (int i = 0; i < 100 && (PluginsList.last == null || !PluginsList.last.isShowing()); i++) Thread.sleep(100);
    Thread.sleep(300);
    final JDialog list = PluginsList.last;
    @SuppressWarnings("unchecked")
    final javax.swing.JList<File> rows = (javax.swing.JList<File>) component(list, "plugins-list");
    final javax.swing.JTextArea info = (javax.swing.JTextArea) component(list, "plugins-info");
    java.util.function.Supplier<String> names = () -> {
      StringBuilder b = new StringBuilder();
      // The test's own plugins (the computer's VST folders may have others).
      for (int i = 0; i < rows.getModel().getSize(); i++) {
        File f = rows.getModel().getElementAt(i);
        if (f.getAbsolutePath().startsWith(home.getAbsolutePath())) b.append(b.length() == 0 ? "" : ", ").append(PluginsList.line(f, PluginsList.favourites()).trim());
      }
      return b.toString();
    };
    final String[] seen = new String[9];
    edt(() -> seen[0] = names.get());
    // A new order starts from the top, wherever the list was.
    edt(() -> rows.setSelectedIndex(rows.getModel().getSize() - 1));
    edt(() -> ((javax.swing.JComboBox<?>) component(list, "plugins-sort")).setSelectedIndex(1));
    edt(() -> seen[1] = names.get());
    edt(() -> seen[6] = rows.getSelectedIndex() + " " + rows.getFirstVisibleIndex());
    final javax.swing.JTextField search = (javax.swing.JTextField) component(list, "plugins-search");
    edt(() -> search.setText("u?r*DRUM"));
    edt(() -> seen[7] = names.get());
    edt(() -> search.setText("zz*q"));
    edt(() -> seen[8] = rows.getModel().getSize() + " " + info.getText());
    edt(() -> search.setText(""));
    edt(() -> {
      for (int i = 0; i < rows.getModel().getSize(); i++) if (rows.getModel().getElementAt(i).getName().startsWith("Anarchy")) rows.setSelectedIndex(i);
    });
    edt(() -> seen[2] = info.getText().replace(home.getAbsolutePath(), "~").replaceAll("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d", "date"));
    edt(() -> ((javax.swing.JButton) component(list, "plugins-fav")).doClick());
    edt(() -> ((javax.swing.JToggleButton) component(list, "plugins-favourites")).doClick());
    edt(() -> seen[3] = names.get());
    PluginsList.lastTest = null;
    edt(() -> ((javax.swing.JButton) component(list, "plugins-test")).doClick());
    for (int i = 0; i < 300 && PluginsList.lastTest == null; i++) Thread.sleep(100);
    idle();
    edt(() -> seen[4] = info.getText().split("\n")[0]);
    edt(() -> ((javax.swing.JButton) component(list, "plugins-use")).doClick());
    idle();
    edt(() -> seen[5] = ((JLabel) component(d, "params-chosen:--plugin2")).getText());
    edt(() -> find(d.getContentPane(), "Cancel").doClick());
    idle();
    this.out.append("A-Z: ").append(seen[0]).append("\nZ-A: ").append(seen[1]).append("\nInfo: ").append(seen[2].replace('\n', '|')).append("\nFavourites: ").append(seen[3])
        .append("\nTest plugin: ").append(seen[4]).append("\nUse: --plugin2 ").append(seen[5]).append("\nZ-A selected, first shown: ").append(seen[6])
        .append("\nSearch u?r*DRUM: ").append(seen[7]).append("\nSearch zz*q: ").append(seen[8]).append('\n');
    boolean ok = seen[0].equals("Anarchy Drums, Riot, Ugritone Drums, Zeta Drums") && seen[1].equals("Zeta Drums, Ugritone Drums, Riot, Anarchy Drums")
        && seen[2].startsWith("Anarchy Drums (VST3, a bundle folder)") && seen[2].contains("Settings (kit, patch) saved with Edit on date")
        && seen[3].equals("\u2605 Anarchy Drums") && seen[4].length() > 0 && "Anarchy Drums.vst3".equals(seen[5]) && !list.isShowing()
        && seen[6].equals("0 0") && seen[7].equals("Ugritone Drums") && seen[8].equals("0 No plugin name matches zz*q.");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /** WAV ready's Add to DB…: the render into the prompt library as a result file, then its group asked (No group). */
  void s161_wav_ready_add_to_db() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File wav = new File(home, "vst-check-Kars.wav");
    short[] tone = new short[22050 / 2];
    for (int i = 0; i < tone.length; i++) tone[i] = (short) (6000 * Math.sin(i * 0.07));
    Files.write(wav.toPath(), AudioIo.encodeWav(tone, 22050));
    final boolean[] listed = new boolean[1];
    inspectNext = d -> listed[0] = find(d.getContentPane(), MediaDir.ADD_TO_DB) != null;
    answers.add(MediaDir.ADD_TO_DB);
    answers.add(MediaDir.DB_KINDS[0]);
    answers.add("OK");
    answers.add("Close");
    final PyJav pj = (PyJav) get("pyJav");
    edt(() -> pj.offerAudio(wav));
    idle();
    inspectNext = null;
    java.util.List<String> results = new java.util.ArrayList<String>();
    for (PromptVault.StoredFile f : PromptDb.vault().resultFiles()) results.add(f.name);
    this.out.append("WAV ready lists Add to DB: ").append(listed[0]).append(" | said: ").append(pj.lastDbAdd).append(" | result files: ").append(results).append('\n');
    boolean ok = listed[0] && "vst-check-Kars.wav is in the DB as a result file".equals(pj.lastDbAdd) && results.contains("vst-check-Kars.wav");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * vst_render's Params: a file or folder row's Use box unticked leaves it out of the run (its
   * switch not in the arguments or the saved Params) and keeps it: Params shows it again, unticked;
   * ticked again, the run has it.
   */
  void s162_params_use_box() throws Exception {
    File dir = new File(System.getProperty("user.home"), "use-test");
    dir.mkdirs();
    File riot = new File(dir, "Riot.vst3");
    Files.write(riot.toPath(), new byte[] {1});
    call("showView", "py");
    call("selectListedProgram", "Python", "vst_render.py");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText(""));
    call("saveParams", "vst_render.py", "");
    call("saveParams", "vst_render.py" + ProgramParams.OFF_KEY, "");
    java.util.function.Function<java.util.function.Consumer<JDialog>, String> params = work -> {
      try {
        final JDialog[] d = new JDialog[1];
        inspectNext = w -> d[0] = w;
        answers.add("(keep)");
        SwingUtilities.invokeLater(() -> {
          try {
            call("openParams");
          } catch (Exception ex) {
            errors.add(ex);
          }
        });
        for (int i = 0; i < 100 && (d[0] == null || !d[0].isShowing()); i++) Thread.sleep(100);
        Thread.sleep(300);
        final String[] seen = new String[1];
        edt(() -> seen[0] = ((JLabel) component(d[0], "params-chosen:--plugin2")).getText() + " use "
            + ((javax.swing.JCheckBox) component(d[0], "params-use:--plugin2")).isSelected());
        work.accept(d[0]);
        edt(() -> find(d[0].getContentPane(), "OK").doClick());
        idle();
        return seen[0];
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    // Pick the second plugin and untick its Use.
    params.apply(d -> {
      try {
        chooseNext = riot;
        edt(() -> ((javax.swing.JButton) component(d, "params-file:--plugin2")).doClick());
        idle();
        edt(() -> ((javax.swing.JCheckBox) component(d, "params-use:--plugin2")).doClick());
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    String args1 = ((javax.swing.text.JTextComponent) get("pyExtra")).getText();
    String saved1 = String.valueOf(call("loadParams", "vst_render.py"));
    String kept1 = String.valueOf(call("loadParams", "vst_render.py" + ProgramParams.OFF_KEY)).replace(dir.getAbsolutePath(), "~");
    // Opened again: there, unticked; ticked, the run has it.
    String shown = params.apply(d -> {
      try {
        edt(() -> ((javax.swing.JCheckBox) component(d, "params-use:--plugin2")).doClick());
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
    String args2 = ((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(dir.getAbsolutePath(), "~");
    String kept2 = String.valueOf(call("loadParams", "vst_render.py" + ProgramParams.OFF_KEY));
    call("saveParams", "vst_render.py", "");
    call("saveParams", "vst_render.py" + ProgramParams.OFF_KEY, "");
    this.out.append("unticked: args [").append(args1).append("] saved [").append(saved1).append("] kept [").append(kept1).append("]\n")
        .append("opened again: --plugin2 ").append(shown).append(" | ticked: args [").append(args2).append("] kept [").append(kept2).append("]\n");
    boolean ok = !args1.contains("--plugin2") && !saved1.contains("--plugin2") && kept1.equals("--plugin2 ~/Riot.vst3")
        && shown.equals("Riot.vst3 use false") && args2.contains("--plugin2 ~/Riot.vst3") && kept2.length() == 0;
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * WAV ready's Play: the built-in player (no VLC, no system app) plays the render: Pause / Play,
   * its place "00:00 / 00:01", Stop back to the start; closing it ends the sound. (The playlist viewer
   * plays a sound the same way, under its waveform.)
   */
  void s163_builtin_sound_player() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File wav = new File(home, "render-play.wav");
    short[] tone = new short[22050 * 3 / 2];
    for (int i = 0; i < tone.length; i++) tone[i] = (short) (6000 * Math.sin(i * 0.07));
    Files.write(wav.toPath(), AudioIo.encodeWav(tone, 22050));
    answers.add("Play");
    answers.add("(keep)");
    SoundClip.lastWindow = null;
    final PyJav pj = (PyJav) get("pyJav");
    edt(() -> pj.offerAudio(wav));
    for (int i = 0; i < 50 && (SoundClip.lastWindow == null || !SoundClip.lastWindow.isShowing()); i++) Thread.sleep(100);
    final JDialog d = SoundClip.lastWindow;
    Thread.sleep(500);
    final String[] seen = new String[5];
    edt(() -> seen[0] = d.getTitle() + " | " + ((javax.swing.JButton) component(d, "sound-play")).getText() + " | " + ((JLabel) component(d, "sound-time")).getText());
    edt(() -> ((javax.swing.JButton) component(d, "sound-play")).doClick());
    edt(() -> seen[1] = ((javax.swing.JButton) component(d, "sound-play")).getText());
    edt(() -> ((javax.swing.JButton) component(d, "sound-stop")).doClick());
    Thread.sleep(400);
    edt(() -> seen[2] = ((javax.swing.JButton) component(d, "sound-play")).getText() + " " + ((JLabel) component(d, "sound-time")).getText());
    edt(() -> ((javax.swing.JButton) component(d, "sound-play")).doClick());
    Thread.sleep(2200);
    edt(() -> seen[3] = ((javax.swing.JButton) component(d, "sound-play")).getText() + " " + ((JLabel) component(d, "sound-time")).getText());
    edt(() -> d.dispose());
    idle();
    this.out.append("Player: ").append(seen[0]).append("\nPause pressed: ").append(seen[1]).append("\nStopped: ").append(seen[2])
        .append("\nPlayed to the end: ").append(seen[3]).append("\nShowing after close: ").append(d.isShowing()).append('\n');
    boolean ok = seen[0].startsWith("render-play.wav | Pause | 00:0") && seen[0].endsWith(" / 00:01") && "Play".equals(seen[1])
        && "Play 00:00 / 00:01".equals(seen[2]) && "Play 00:01 / 00:01".equals(seen[3]) && !d.isShowing();
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * vst_render's arguments: a --plugin typed in the Arguments field is the run's (the saved Params
   * fill in the rest, their own --plugin left out); with nothing typed the saved Params are used.
   */
  void s164_typed_switches_win() throws Exception {
    File vr = new File(System.getProperty("pulsekit.test.dir"), "../../Programs/Python/vst_render.py");
    java.util.List<ProgramParams.Param> ps = ProgramParams.parse(new String(Files.readAllBytes(vr.toPath()), StandardCharsets.UTF_8));
    String saved = "--plugin old.vst3 --dir in --skip_existing";
    String typed = ProgramParams.merge(ps, "--plugin \"new kit.vst3\" --mp3 128", saved);
    String none = ProgramParams.merge(ps, "", saved);
    String kept = ProgramParams.typed(ps, typed);
    this.out.append("typed: ").append(typed).append("\nnothing typed: ").append(none).append("\nsaved next: ").append(kept).append('\n');
    boolean ok = typed.contains("--plugin \"new kit.vst3\"") && !typed.contains("old.vst3") && typed.contains("--dir in") && typed.contains("--mp3 128")
        && none.contains("--plugin old.vst3") && kept.contains("new kit.vst3") && kept.contains("--skip_existing");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * vst_render's Params: --map has Pulsekit's map files to pick (drum-kits.txt, put in
   * ~/.pulsekit/maps) and Browse; a kit name typed in the run's arguments (--kit_name) is kept,
   * and the next Params has it to pick.
   */
  void s165_vst_map_and_kit_lists() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Python", "vst_render.py");
    call("saveParams", "vst_render.py", "");
    java.util.function.Function<String, String[]> rows = token -> {
      try {
        final JDialog[] d = new JDialog[1];
        inspectNext = w -> d[0] = w;
        answers.add("(keep)");
        SwingUtilities.invokeLater(() -> {
          try {
            call("openParams");
          } catch (Exception ex) {
            errors.add(ex);
          }
        });
        for (int i = 0; i < 100 && (d[0] == null || !d[0].isShowing()); i++) Thread.sleep(100);
        Thread.sleep(300);
        final String[] seen = new String[2];
        edt(() -> {
          seen[0] = (component(d[0], "params-choose:" + token) != null ? "Choose" : "-") + (component(d[0], "params-map-browse:" + token) != null ? " Browse" : "");
        });
        edt(() -> find(d[0].getContentPane(), "Cancel").doClick());
        idle();
        return seen;
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    String[] map = rows.apply("--map");
    String[][] choices = VstLists.mapChoices();
    boolean unpacked = new File(home, ".pulsekit/maps/drum-kits.txt").isFile();
    String[] kitBefore = rows.apply("--kit_name");
    VstLists.rememberKits("--plugin k.vst3 --kit_name \"Finnish Metal 2000\" --test");
    String[] kitAfter = rows.apply("--kit_name");
    this.out.append("--map row: ").append(map[0]).append(" | choices: ").append(java.util.Arrays.toString(choices[0])).append(" -> ").append(java.util.Arrays.toString(choices[1]))
        .append(" | unpacked: ").append(unpacked).append("\n--kit_name row before: ").append(kitBefore[0]).append(" | after a run: ").append(kitAfter[0])
        .append(" ").append(java.util.Arrays.toString(VstLists.kitNames())).append('\n');
    boolean ok = map[0].equals("Choose Browse") && java.util.Arrays.asList(choices[0]).contains("drum-kits.txt (Pulsekit's)")
        && java.util.Arrays.asList(choices[1]).contains("drum-kits.txt") && unpacked && kitBefore[0].equals("-") && kitAfter[0].equals("Choose")
        && java.util.Arrays.asList(VstLists.kitNames()).equals(java.util.Arrays.asList("Finnish Metal 2000"));
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * File > Logs: nothing is written while General settings > Logging on is unticked; ticked, a
   * program run (arguments, exit code, output) and the start are logged, a password argument
   * hidden; the Logs window lists them by type, the newest first, shows, exports and deletes them.
   */
  void s166_logs() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File wav = new File(home, "take.wav");
    short[] pcm = new short[22050];
    for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (8000 * Math.sin(i * 2 * Math.PI * 220 / 44100));
    Files.write(wav.toPath(), AudioIo.encodeWav(pcm, 44100));
    call("showView", "py");
    call("selectListedProgram", "Java", "Wav2Mp3.java");
    final javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    Runnable runIt = () -> {
      try {
        edt(() -> log.setText(""));
        edt(() -> call("setInputFile", wav));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(300);
        idle();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    };
    runIt.run();
    int whileOff = Logs.entries(Logs.ALL).size();
    // General settings > Logging on (another dialog first, the MIDI synth's message, is let go).
    final boolean[] box = new boolean[2];
    final boolean[] shown = new boolean[2];
    final java.util.function.Consumer<JDialog>[] hook = newHook();
    hook[0] = w -> {
      if (!"General settings".equals(w.getTitle())) {
        inspectNext = hook[0];
        return;
      }
      shown[0] = true;
      javax.swing.JCheckBox c = (javax.swing.JCheckBox) component(w, "settings-logging");
      box[0] = c != null && !c.isSelected() && c.getText().equals(Logs.SETTING);
      if (c != null) c.doClick();
      box[1] = AppSettings.logging();
    };
    idle();
    inspectNext = hook[0];
    answers.add("Close");
    SwingUtilities.invokeLater(() -> GeneralSettings.show((Pulsekit) frame));
    for (int i = 0; i < 100 && !shown[0]; i++) Thread.sleep(100);
    Thread.sleep(300);
    idle();
    runIt.run();
    Logs.run("Secret.java", java.util.Arrays.asList("--password", "hunter2", "--out", "a b.txt"), 1, "Failed: no", System.currentTimeMillis());
    java.util.List<Logs.Entry> runs = Logs.entries(Logs.RUNS);
    java.util.List<Logs.Entry> starts = Logs.entries(Logs.STARTS);
    String runText = runs.size() > 1 ? Logs.text(runs.get(1)) : "";
    String secretText = runs.size() > 0 ? Logs.text(runs.get(0)) : "";
    this.out.append("while off: ").append(whileOff).append(" entries | box unticked first: ").append(box[0]).append(", on after a click: ").append(box[1])
        .append("\nruns: ").append(runs.size()).append(" (newest: ").append(runs.isEmpty() ? "-" : runs.get(0).title).append("), starts: ").append(starts.size())
        .append("\nsecret run args: ").append(secretText.replaceAll("(?s).*?(Arguments:[^\n]*).*", "$1")).append('\n');
    // The Logs window: Program runs, the Wav2Mp3 run shown, exported, then deleted.
    final File exported = new File(home, "logs-out.txt");
    LogsScreen.exportTo = name -> exported;
    LogsScreen.askDelete = false;
    final String[] seen = new String[4];
    hook[0] = w -> {
      if (!"Logs".equals(w.getTitle())) {
        inspectNext = hook[0];
        return;
      }
      shown[1] = true;
      @SuppressWarnings("unchecked")
      javax.swing.JComboBox<String> pick = (javax.swing.JComboBox<String>) component(w, "logs-type");
      @SuppressWarnings("unchecked")
      javax.swing.JList<Logs.Entry> list = (javax.swing.JList<Logs.Entry>) component(w, "logs-list");
      seen[0] = "all: " + list.getModel().getSize();
      pick.setSelectedItem(Logs.RUNS);
      seen[0] += ", runs: " + list.getModel().getSize();
      list.setSelectedIndex(1);
      seen[1] = ((javax.swing.JTextArea) component(w, "logs-text")).getText();
      ((javax.swing.JButton) component(w, "logs-export")).doClick();
      ((javax.swing.JButton) component(w, "logs-delete")).doClick();
      seen[2] = String.valueOf(list.getModel().getSize());
      seen[3] = ((javax.swing.JLabel) component(w, "logs-state")).getText();
    };
    idle();
    inspectNext = hook[0];
    answers.add("Close");
    SwingUtilities.invokeLater(() -> LogsScreen.show((Pulsekit) frame));
    for (int i = 0; i < 100 && !shown[1]; i++) Thread.sleep(100);
    Thread.sleep(300);
    idle();
    LogsScreen.exportTo = null;
    LogsScreen.askDelete = true;
    String exportedText = exported.isFile() ? new String(Files.readAllBytes(exported.toPath()), StandardCharsets.UTF_8) : "";
    this.out.append("window: ").append(seen[0]).append(" | after Delete: ").append(seen[2]).append(" | ").append(seen[3])
        .append("\nshown: ").append(seen[1].split("\n")[0]).append(" / ").append(seen[1].contains("Exit code: 0")).append(" | exported: ").append(exportedText.contains("Program: Wav2Mp3.java")).append('\n');
    boolean ok = whileOff == 0 && box[0] && box[1] && runs.size() == 2 && runs.get(0).title.startsWith("Secret.java failed") && runs.get(1).title.equals("Wav2Mp3.java finished")
        && runText.contains("Exit code: 0") && runText.contains("Output:") && starts.size() >= 1 && secretText.contains("--password (hidden)") && !secretText.contains("hunter2")
        && secretText.contains("\"a b.txt\"") && seen[1].startsWith("Type: Program runs") && seen[1].contains("Program: Wav2Mp3.java") && exportedText.contains("Program: Wav2Mp3.java")
        && "1".equals(seen[2]) && seen[3].startsWith("1 entries");
    if (!ok) throw new AssertionError(this.out.toString() + "\n" + runText);
  }

  /**
   * File > Sound devices: every group listed (Pulsekit's own synth among the MIDI outputs), a driver
   * file picked with Browse listed under Drivers, an output kept that is not connected said and
   * left for the default (Java Sound's default lines untouched), Use the default clearing it, the
   * report and the logs (Logging on) holding what was found.
   */
  void s167_sound_devices() throws Exception {
    File home = new File(System.getProperty("user.home"));
    AppSettings.load(PromptDb.dir());
    AppSettings.setLogging(true);
    AppSettings.setSoundOut("Gone Interface | DirectSound");
    SoundOut.apply();
    String lineProp = String.valueOf(System.getProperty(SoundOut.LINE_KEY));
    final File dll = new File(home, "asio-test.dll");
    Files.write(dll.toPath(), new byte[] {'M', 'Z', 0, 0});
    SoundDevicesScreen.browseTo = what -> dll;
    final String[] seen = new String[6];
    final boolean[] shown = new boolean[1];
    final java.util.function.Consumer<JDialog>[] hook = newHook();
    hook[0] = w -> {
      if (!"Sound devices".equals(w.getTitle())) {
        inspectNext = hook[0];
        return;
      }
      shown[0] = true;
      @SuppressWarnings("unchecked")
      javax.swing.JList<Object> list = (javax.swing.JList<Object>) component(w, "sound-list");
      StringBuilder heads = new StringBuilder();
      int synthAt = -1;
      for (int i = 0; i < list.getModel().getSize(); i++) {
        Object o = list.getModel().getElementAt(i);
        if (o instanceof String) heads.append(heads.length() == 0 ? "" : ", ").append(((String) o).replaceAll(" \\(\\d+\\)", ""));
        else if (SoundDevices.MIDI_OUT.equals(((SoundDevices.Device) o).kind) && SoundDevices.SYNTH.equals(((SoundDevices.Device) o).driver)) synthAt = i;
      }
      seen[0] = heads.toString();
      seen[1] = ((javax.swing.JLabel) component(w, "sound-now")).getText();
      ((javax.swing.JButton) component(w, "sound-browse")).doClick();
      StringBuilder drivers = new StringBuilder();
      for (int i = 0; i < list.getModel().getSize(); i++) {
        Object o = list.getModel().getElementAt(i);
        if (o instanceof SoundDevices.Device && SoundDevices.DRIVERS.equals(((SoundDevices.Device) o).kind)) drivers.append(((SoundDevices.Device) o).name).append(' ');
      }
      seen[2] = drivers.toString().trim();
      if (synthAt >= 0) {
        list.setSelectedIndex(synthAt);
        seen[3] = ((javax.swing.JTextArea) component(w, "sound-info")).getText().split("\n")[1] + " | use " + component(w, "sound-use").isEnabled()
            + ", test " + component(w, "sound-test").isEnabled();
      } else {
        seen[3] = "no synth listed";
      }
      ((javax.swing.JButton) component(w, "sound-default")).doClick();
      seen[4] = ((javax.swing.JLabel) component(w, "sound-now")).getText();
      seen[5] = AppSettings.soundOut();
    };
    idle();
    inspectNext = hook[0];
    answers.add("Close");
    SwingUtilities.invokeLater(() -> SoundDevicesScreen.show((Pulsekit) frame));
    for (int i = 0; i < 100 && !shown[0]; i++) Thread.sleep(100);
    Thread.sleep(300);
    idle();
    SoundDevicesScreen.browseTo = null;
    String report = SoundDevices.report("", SoundOut.list());
    java.util.List<Logs.Entry> logged = Logs.entries(Logs.SOUND);
    StringBuilder titles = new StringBuilder();
    for (Logs.Entry e : logged) titles.append(titles.length() == 0 ? "" : " / ").append(e.title.replaceAll("\\(\\d+\\)", "(n)"));
    this.out.append("missing output, default line: ").append(lineProp).append("\ngroups: ").append(seen[0]).append("\nnow: ").append(seen[1].replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", " "))
        .append("\ndrivers after Browse: ").append(seen[2]).append("\nown synth: ").append(seen[3]).append("\nafter Use the default: ").append(seen[4].replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", " "))
        .append(" (kept: \"").append(seen[5]).append("\")\nlogged: ").append(titles).append('\n');
    boolean ok = "null".equals(lineProp) && seen[0].equals(String.join(", ", SoundDevices.KINDS)) && seen[1].contains("Gone Interface | DirectSound") && seen[1].contains("is not connected")
        && seen[2].contains("asio-test.dll") && seen[3].endsWith("| use false, test true") && "".equals(seen[5]) && seen[4].contains(SoundDevices.DEFAULT_OUT) && seen[4].contains(SoundDevices.OWN_SYNTH)
        && report.contains("== " + SoundDevices.MIDI_OUT) && report.contains("asio-test.dll") && titles.toString().contains("Sound devices found")
        && titles.toString().contains("Driver file picked") && titles.toString().contains("Back to the defaults");
    if (!ok) throw new AssertionError(this.out.toString() + "\n" + report);
  }

  /**
   * SFZ kits: a .zip with two kits, the one picked onto the pads (a FLAC kick, a 24-bit snare, a hat
   * from an #include), kept as kit.sf2 and in the logs; Midi2Wav plays the other
   * kit of the package (--sf2 package.zip#kit.sfz).
   */
  void s168_sfz_kit() throws Exception {
    File home = new File(System.getProperty("user.home"));
    AppSettings.load(PromptDb.dir());
    AppSettings.setLogging(true);
    File zip = sfzPackage(home, new File(System.getProperty("pulsekit.test.dir", "."), "kick24.flac"));
    final Pulsekit app = (Pulsekit) frame;
    final short[] kickBefore = app.voices[0], snareBefore = app.voices[2], hatBefore = app.voices[5], clapBefore = app.voices[3];
    final java.util.List<String> offered = new java.util.ArrayList<String>();
    ProjectIo.sfzPick = kits -> {
      offered.addAll(kits);
      return "Kits/Rock.sfz";
    };
    boolean isKit = Sfz.isKit(zip);
    edt(() -> app.projectIo.importSfz(zip));
    for (int i = 0; i < 200 && !String.valueOf(app.nowPlaying.getText()).contains("SFZ kit \u00b7"); i++) Thread.sleep(50);
    idle();
    ProjectIo.sfzPick = null;
    String said = String.valueOf(app.nowPlaying.getText()).trim();
    boolean kick = app.voices[0] != kickBefore, snare = app.voices[2] != snareBefore, hat = app.voices[5] != hatBefore, clapKept = app.voices[3] == clapBefore;
    File kept = new File(home, ".pulsekit/kit.sf2");
    int keptPads = 0;
    if (kept.isFile()) for (short[] v : AudioIo.parseSf2(Files.readAllBytes(kept.toPath()))) if (v != null) keptPads++;
    java.util.List<Logs.Entry> logged = Logs.entries(Logs.KITS);
    String report = logged.isEmpty() ? "" : Logs.text(logged.get(0));
    this.out.append("kit: ").append(isKit).append(", offered ").append(offered).append("\nsaid: ").append(said).append("\npads changed: kick ").append(kick).append(", snare ").append(snare)
        .append(", hat ").append(hat).append(", clap kept ").append(clapKept).append(" | kit.sf2 pads: ").append(keptPads).append("\nlog: ").append(logged.isEmpty() ? "-" : logged.get(0).title).append('\n');
    for (String line : report.split("\n")) if (line.startsWith("Kick") || line.startsWith("Snare") || line.startsWith("Closed")) this.out.append("  ").append(line).append('\n');
    // Midi2Wav with the package's other kit.
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    File midi = new File(home, "groove.mid");
    Files.write(midi.toPath(), Engine.encodeMidi(cells, 120));
    call("showView", "py");
    call("selectListedProgram", "Java", "Midi2Wav.java");
    idle();
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("\"" + midi.getAbsolutePath() + "\" jazz.wav --sf2 \"" + zip.getAbsolutePath() + "#Kits/Jazz.sfz\" --rate 22050"));
    answers.add("Close");
    edt(() -> call("runPython"));
    for (int i = 0; i < 400 && !(String.valueOf(log.getText()).contains("Succeeded") || String.valueOf(log.getText()).contains("Failed")); i++) Thread.sleep(50);
    Thread.sleep(600);
    idle();
    answers.clear();
    StringBuilder ran = new StringBuilder();
    for (String line : String.valueOf(log.getText()).split("\n")) {
      String t = line.trim().replaceAll("^\\d\\d:\\d\\d:\\d\\d ", "");
      if (t.startsWith("SFZ kit") || t.startsWith("Succeeded") || t.startsWith("Failed")) ran.append("  ").append(t.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    this.out.append("Midi2Wav:\n").append(ran);
    boolean ok = isKit && offered.equals(java.util.Arrays.asList("Kits/Jazz.sfz", "Kits/Rock.sfz")) && said.contains("SFZ kit \u00b7 Rock \u00b7 4 drums") && kick && snare && hat && clapKept
        && keptPads >= 4 && report.contains("Kick (36): Kits/Samples/Kick/kick.flac") && report.contains("Snare (38): Kits/Samples/snare 24.wav")
        && report.contains("Closed hat (42): Kits/Samples/snare 24.wav") && ran.toString().contains("SFZ kit Jazz: played as a SoundFont") && ran.toString().contains("Succeeded");
    if (!ok) throw new AssertionError(this.out.toString() + "\n" + report + "\n" + log.getText());
  }

  /** A test SFZ package: Kits/Rock.sfz (FLAC kick, 24-bit snare, an #include'd hat) and Kits/Jazz.sfz, zipped. */
  private static File sfzPackage(File home, File flac) throws Exception {
    File zip = new File(home, "Test Kits.zip");
    short[] snare = new short[11025];
    for (int i = 0; i < snare.length; i++) snare[i] = (short) (20000 * Math.exp(-i / 1500.0) * Math.sin(i * 0.31 + Math.sin(i * 1.7)));
    // A 24-bit snare: the 16-bit one with a low byte under it.
    byte[] wav16 = AudioIo.encodeWav(snare, 44100);
    int n = snare.length;
    java.io.ByteArrayOutputStream w = new java.io.ByteArrayOutputStream();
    java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + n * 3).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + n * 3).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
        .putInt(44100).putInt(44100 * 3).putShort((short) 3).putShort((short) 24).put("data".getBytes(StandardCharsets.US_ASCII)).putInt(n * 3);
    for (short v : snare) b.put((byte) 0x40).putShort(v);
    w.write(b.array());
    try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
      String[][] files = {
        {"Kits/Rock.sfz", "// Rock kit\n#define $DIR Samples\n<control> default_path=$DIR/\n<group> key=c2\n<region> sample=Kick\\kick.flac lovel=1 hivel=127\n"
            + "<group> key=38 seq_length=2\n<region> sample=snare 24.wav seq_position=1\n<region> sample=missing.wav seq_position=2\n#include \"hats.sfzh\"\n"},
        {"Kits/hats.sfzh", "<region> sample=snare 24.wav key=f#2 locc4=0 hicc4=63 volume=-6\n"},
        {"Kits/Jazz.sfz", "<region> sample=Samples/snare 24.wav key=36\n"}};
      for (String[] f : files) {
        z.putNextEntry(new java.util.zip.ZipEntry(f[0]));
        z.write(f[1].getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
      }
      z.putNextEntry(new java.util.zip.ZipEntry("Kits/Samples/kick/KICK.FLAC"));
      z.write(Files.readAllBytes(flac.toPath()));
      z.closeEntry();
      z.putNextEntry(new java.util.zip.ZipEntry("Kits/Samples/snare 24.wav"));
      z.write(w.toByteArray());
      z.closeEntry();
    }
    return zip;
  }

  /**
   * PyJav > Plugins, the Plugin Browser: folders per type (Folders\u2026), the plugins and kits found there
   * (a VST3 bundle, a VST2, a CLAP, an SF2, an SFZ), favourite, category, group and tags (and the
   * filters on them, kept in plugin-library.txt and shared with Params' Plugins list), My order,
   * Types, a kit's test beat, Use (a VST3 into vst_render's --plugin, a CLAP said not to play).
   */
  void s169_plugin_browser() throws Exception {
    File home = new File(System.getProperty("user.home"));
    final File vst = new File(home, "plugins"), kits = new File(home, "my kits");
    new File(vst, "Anarchy Drums.vst3/Contents").mkdirs();
    new File(vst, "Maker/Deep").mkdirs();
    Files.write(new File(vst, "Maker/Deep/Old Drums.dll").toPath(), new byte[] {'M', 'Z'});
    Files.write(new File(vst, "Surge.clap").toPath(), new byte[] {0});
    new File(kits, "Test").mkdirs();
    Files.write(new File(kits, "Rock.sf2").toPath(), AudioIo.encodeSf2(AudioIo.buildVoices(22050), "Rock"));
    short[] hit = new short[8000];
    for (int i = 0; i < hit.length; i++) hit[i] = (short) (18000 * Math.exp(-i / 900.0) * Math.sin(i * 0.05));
    Files.write(new File(kits, "Test/kick.wav").toPath(), AudioIo.encodeWav(hit, 44100));
    Files.write(new File(kits, "Test/Jazz.sfz").toPath(), "<region> sample=kick.wav key=36\n".getBytes(StandardCharsets.UTF_8));
    final Pulsekit app = (Pulsekit) frame;
    PluginBrowser.testsQuiet = Boolean.TRUE;
    final java.util.Map<String, String> answersFor = new java.util.HashMap<String, String>();
    PluginBrowser.answer = what -> answersFor.get(what);
    final JDialog[] browser = new JDialog[1];
    final java.util.function.Consumer<JDialog>[] hook = newHook();
    hook[0] = w -> {
      if ("Plugin Browser".equals(w.getTitle())) browser[0] = w;
      else inspectNext = hook[0];
    };
    idle();
    inspectNext = hook[0];
    answers.add("(keep)");
    // PyJav's programs row: Plugins.
    edt(() -> ((javax.swing.JButton) component(frame, "program-plugins")).doClick());
    for (int i = 0; i < 100 && (browser[0] == null || !browser[0].isShowing()); i++) Thread.sleep(100);
    idle();
    final JDialog d = browser[0];
    @SuppressWarnings("unchecked")
    final javax.swing.JList<File> list = (javax.swing.JList<File>) component(d, "browser-list");
    // Folders\u2026: the VST3 and VST2 types in "plugins", CLAP there too, the kits in "my kits".
    final boolean[] saved = new boolean[1];
    final java.util.function.Consumer<JDialog>[] folderHook = newHook();
    folderHook[0] = w -> {
      if (!"Plugin folders".equals(w.getTitle())) {
        inspectNext = folderHook[0];
        return;
      }
      @SuppressWarnings("unchecked")
      javax.swing.JComboBox<String> type = (javax.swing.JComboBox<String>) component(w, "folders-type");
      javax.swing.JTextArea paths = (javax.swing.JTextArea) component(w, "folders-paths");
      for (String t : new String[] {PluginLibrary.VST3, PluginLibrary.VST2, PluginLibrary.LV2, PluginLibrary.CLAP, PluginLibrary.SF2}) {
        type.setSelectedItem(t);
        paths.setText(t.equals(PluginLibrary.SF2) || t.equals(PluginLibrary.LV2) ? "" : vst.getAbsolutePath());
        if (t.equals(PluginLibrary.SF2)) {
          answersFor.put("Folder", kits.getAbsolutePath());
          ((javax.swing.JButton) component(w, "folders-add")).doClick();
        }
      }
      type.setSelectedItem(PluginLibrary.SFZ);
      paths.setText(kits.getAbsolutePath());
      ((javax.swing.JButton) component(w, "folders-save")).doClick();
      saved[0] = true;
    };
    inspectNext = folderHook[0];
    SwingUtilities.invokeLater(() -> ((javax.swing.JButton) component(d, "browser-folders")).doClick());
    for (int i = 0; i < 100 && !saved[0]; i++) Thread.sleep(100);
    idle();
    final StringBuilder seen = new StringBuilder();
    final java.util.function.Function<String, File> pick = name -> {
      for (int i = 0; i < list.getModel().getSize(); i++) if (PluginLibrary.name(list.getModel().getElementAt(i)).equals(name)) {
        list.setSelectedIndex(i);
        return list.getModel().getElementAt(i);
      }
      return null;
    };
    final java.util.function.Supplier<String> names = () -> {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < list.getModel().getSize(); i++) b.append(i == 0 ? "" : ", ").append(PluginLibrary.name(list.getModel().getElementAt(i)));
      return b.toString();
    };
    edt(() -> {
      seen.append("found: ").append(names.get()).append(" (").append(((javax.swing.JLabel) component(d, "browser-count")).getText()).append(")\n");
      pick.apply("Rock");
      ((javax.swing.JButton) component(d, "browser-fav")).doClick();
      answersFor.put("Category", "Drum kits");
      ((javax.swing.JButton) component(d, "browser-set-category")).doClick();
      answersFor.put("Group", "Live");
      ((javax.swing.JButton) component(d, "browser-set-group")).doClick();
      answersFor.put("Tags", "metal, fast");
      ((javax.swing.JButton) component(d, "browser-set-tags")).doClick();
      pick.apply("Anarchy Drums");
      ((javax.swing.JButton) component(d, "browser-set-category")).doClick();
      seen.append("Rock line: ").append(PluginLibrary.line(pick.apply("Rock"))).append('\n');
      @SuppressWarnings("unchecked")
      javax.swing.JComboBox<String> type = (javax.swing.JComboBox<String>) component(d, "browser-type");
      type.setSelectedItem(PluginLibrary.SFZ);
      seen.append("type SFZ: ").append(names.get()).append('\n');
      type.setSelectedItem(PluginLibrary.ANY);
      @SuppressWarnings("unchecked")
      javax.swing.JComboBox<String> cat = (javax.swing.JComboBox<String>) component(d, "browser-category");
      cat.setSelectedItem("Drum kits");
      seen.append("category Drum kits: ").append(names.get()).append('\n');
      cat.setSelectedItem(PluginLibrary.ANY);
      @SuppressWarnings("unchecked")
      javax.swing.JComboBox<String> tag = (javax.swing.JComboBox<String>) component(d, "browser-tag");
      tag.setSelectedItem("fast");
      seen.append("tag fast: ").append(names.get()).append('\n');
      tag.setSelectedItem(PluginLibrary.ANY);
      ((javax.swing.JToggleButton) component(d, "browser-favourites")).doClick();
      seen.append("favourites: ").append(names.get()).append('\n');
      ((javax.swing.JToggleButton) component(d, "browser-favourites")).doClick();
      ((javax.swing.JTextField) component(d, "browser-search")).setText("live");
      seen.append("search live: ").append(names.get()).append('\n');
      ((javax.swing.JTextField) component(d, "browser-search")).setText("");
      // My order: Surge down to the end, then Old Drums up.
      pick.apply("Old Drums");
      ((javax.swing.JButton) component(d, "browser-up")).doClick();
      seen.append("My order after Up on Old Drums: ").append(names.get()).append('\n');
      ((javax.swing.JButton) component(d, "browser-types")).doClick();
      seen.append("types: ").append(PluginBrowser.lastSaid.split("\n\n")[1].split("\n")[0]).append(" | ").append(PluginBrowser.lastSaid.contains("SFZ (.sfz)  desktop: yes, Android: yes")).append('\n');
      pick.apply("Surge");
      ((javax.swing.JButton) component(d, "browser-use")).doClick();
      seen.append("Use Surge: ").append(PluginBrowser.lastSaid).append('\n');
      pick.apply("Jazz");
      ((javax.swing.JButton) component(d, "browser-test")).doClick();
    });
    for (int i = 0; i < 100 && !PluginBrowser.lastSaid.startsWith("Test beat"); i++) Thread.sleep(100);
    seen.append("Test Jazz: ").append(PluginBrowser.lastSaid.replaceAll(", kit-test.*", "")).append('\n');
    final String[] args = new String[1];
    edt(() -> {
      pick.apply("Anarchy Drums");
      ((javax.swing.JButton) component(d, "browser-use")).doClick();
      args[0] = app.pyJav.pyExtra.getText();
      seen.append("Use Anarchy Drums: ").append(PluginBrowser.lastSaid).append(" | args ").append(args[0].replace(home.getAbsolutePath(), "~")).append('\n');
      d.dispose();
    });
    idle();
    PluginBrowser.answer = null;
    PluginBrowser.testsQuiet = null;
    String library = new String(Files.readAllBytes(new File(PromptDb.dir(), "plugin-library.txt").toPath()), StandardCharsets.UTF_8);
    boolean shared = PluginsList.favourites().contains(new File(kits, "Rock.sf2").getAbsolutePath());
    this.out.append(seen.toString().replace(home.getAbsolutePath(), "~")).append("library kept: ").append(library.split("\n").length).append(" plugins, favourite shared with Params' list: ").append(shared).append('\n');
    String all = seen.toString();
    boolean ok = all.contains("found: Anarchy Drums, Jazz, Old Drums, Rock, Surge (5 of 5)") && all.contains("Rock line: \u2605 Rock  [SF2]  Drum kits / Live  #metal #fast")
        && all.contains("type SFZ: Jazz\n") && all.contains("category Drum kits: Anarchy Drums, Rock\n") && all.contains("tag fast: Rock\n") && all.contains("favourites: Rock\n")
        && all.contains("search live: Rock\n") && all.contains("My order after Up on Old Drums: Anarchy Drums, Old Drums, Jazz, Rock, Surge") && all.contains("| true")
        && all.contains("Use Surge: Surge: Listed only") && all.contains("Test Jazz: Test beat with Jazz") && args[0] != null && args[0].startsWith("--plugin \"" + new File(vst, "Anarchy Drums.vst3").getAbsolutePath())
        && shared && library.contains("Drum kits\tLive\tmetal,fast");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  /**
   * Process as a Sogni reference on a picture's menu: a 4:7 picture has it, a 4:3 one (to upscale
   * first) has not, nor any with only Sogni's music tools shown; the menu is in alphabetical order;
   * the item opens the reference dialog. ImageUpscaler's --aspect suggestion for the input picture.
   */
  void s170_process_ref_menus() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File pics = new File(home, "pics");
    pics.mkdirs();
    final File tall = new File(pics, "tall.png"), wide = new File(pics, "wide.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(400, 700, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", tall);
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(640, 480, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", wide);
    AppSettings.load(PromptDb.dir());
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    java.util.function.Function<File, java.util.List<String>> menuOf = f -> {
      final MediaDir.Entry e = new MediaDir.Entry();
      e.name = f.getName();
      e.id = f.getAbsolutePath();
      e.kind = MediaDir.PICTURE;
      e.size = f.length();
      try {
        edt(() -> mb.menu(e, f, new javax.swing.JButton(), 0, 0));
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      java.util.List<String> out = new java.util.ArrayList<String>();
      for (Component c : mb.lastMenu.getComponents()) if (c instanceof javax.swing.JMenuItem && !MenuCancel.is(c)) out.add(((javax.swing.JMenuItem) c).getText());
      try {
        edt(() -> mb.lastMenu.setVisible(false));
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      return out;
    };
    java.util.List<String> tallMenu = menuOf.apply(tall), wideMenu = menuOf.apply(wide);
    AppSettings.setMusicOnly(true);
    java.util.List<String> musicMenu = menuOf.apply(tall);
    AppSettings.setMusicOnly(false);
    java.util.List<String> sortedTall = new java.util.ArrayList<String>(tallMenu);
    sortedTall.sort((a, b) -> MediaDir.sortKey(a).compareTo(MediaDir.sortKey(b)));
    this.out.append("4:7 menu: ").append(String.join(" | ", tallMenu)).append("\n4:3 has it: ").append(wideMenu.contains(MediaDir.PROCESS_REF))
        .append(" | music tools only: ").append(musicMenu.contains(MediaDir.PROCESS_REF)).append('\n');
    // The item: the reference dialog (no program run yet, so it says which to run first).
    final MediaDir.Entry e = new MediaDir.Entry();
    e.name = tall.getName();
    e.id = tall.getAbsolutePath();
    e.kind = MediaDir.PICTURE;
    e.size = tall.length();
    edt(() -> mb.menu(e, tall, new javax.swing.JButton(), 0, 0));
    javax.swing.JMenuItem process = null;
    for (Component c : mb.lastMenu.getComponents()) if (c instanceof javax.swing.JMenuItem && MediaDir.PROCESS_REF.equals(((javax.swing.JMenuItem) c).getText())) process = (javax.swing.JMenuItem) c;
    final javax.swing.JMenuItem item = process;
    if (item != null) SwingUtilities.invokeLater(item::doClick);
    for (int i = 0; i < 30 && !this.out.toString().contains("No program that takes pictures"); i++) {
      Thread.sleep(100);
      idle();
    }
    boolean asked = this.out.toString().contains("No program that takes pictures");
    // ImageUpscaler: the --aspect suggested for its input.
    call("showView", "py");
    call("selectListedProgram", "Java", "ImageUpscaler.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("\"" + wide.getAbsolutePath() + "\""));
    String[] tip = ((Pulsekit) frame).pyJav.aspectTip();
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("\"" + tall.getAbsolutePath() + "\""));
    String[] tipTall = ((Pulsekit) frame).pyJav.aspectTip();
    this.out.append("item opened the dialog: ").append(asked).append("\naspect for 640x480: ").append(tip == null ? "-" : tip[0] + " (" + tip[1] + ")")
        .append("\naspect for 400x700: ").append(tipTall == null ? "-" : tipTall[0] + " (" + tipTall[1] + ")").append('\n');
    boolean ok = tallMenu.contains(MediaDir.PROCESS_REF) && !wideMenu.contains(MediaDir.PROCESS_REF) && !musicMenu.contains(MediaDir.PROCESS_REF)
        && tallMenu.equals(sortedTall) && asked && tip != null && tip[0].equals("4:3") && tipTall != null && tipTall[0].equals("4:7");
    if (!ok) throw new AssertionError(this.out.toString());
  }

  @SuppressWarnings("unchecked")
  private static java.util.function.Consumer<JDialog>[] newHook() {
    return new java.util.function.Consumer[1];
  }

  /** A popup menu's items: "Select all | Copy | Open in editor window". */
  private static String items(javax.swing.JPopupMenu menu) {
    StringBuilder sb = new StringBuilder();
    for (java.awt.Component c : menu.getComponents()) if (c instanceof javax.swing.JMenuItem) sb.append(sb.length() == 0 ? "" : " | ").append(((javax.swing.JMenuItem) c).getText());
    return sb.toString();
  }

  /** The NullPointerException Java 8 throws in EventQueue.getCurrentEventImpl when an EventQueue is pushed. */
  private static boolean jdk8PushedQueue(Throwable t) {
    StackTraceElement[] st = t.getStackTrace();
    return t instanceof NullPointerException && st.length > 0 && "java.awt.EventQueue".equals(st[0].getClassName()) && "getCurrentEventImpl".equals(st[0].getMethodName());
  }

  /** A stream read whole (InputStream.readAllBytes is newer than Java 8, which the suite also runs on). */
  private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    for (int k; (k = in.read(buf)) > 0; ) out.write(buf, 0, k);
    return out.toByteArray();
  }

  private static MediaDir.Entry entryOf(File f) {
    MediaDir.Entry e = new MediaDir.Entry();
    e.name = f.getName();
    e.id = f.getAbsolutePath();
    e.kind = MediaDir.kind(f.getName());
    e.size = f.length();
    return e;
  }

  /** The tab Pulsekit was on is kept (~/.pulsekit/last-tab.txt), and Pulsekit opens on it again. */
  void s115_last_tab_kept() throws Exception {
    edt(() -> call("showView", "fills"));
    out.append("kept: ").append(new String(Files.readAllBytes(Pulsekit.lastTabFile().toPath()), StandardCharsets.UTF_8).trim()).append('\n');
    final Pulsekit[] again = new Pulsekit[1];
    SwingUtilities.invokeAndWait(() -> again[0] = new Pulsekit());
    out.append("started again on: ").append(again[0].view).append(", subtabs ").append(again[0].drumSubtabs.isVisible() ? "shown" : "hidden").append('\n');
    SwingUtilities.invokeAndWait(() -> again[0].dispose());
    edt(() -> call("showView", "pattern"));
    if (!"fills".equals(again[0].view)) throw new AssertionError(out.toString());
  }

  /**
   * The shared VST batch folder: SogniMusic --batchdir puts DrumMidi's MIDI of its track in it (a
   * batch.txt row, base SogniMusic); MidiDrumGen --count --outdir the same folder adds its grooves
   * after it, numbered on; one vst_render --dir run then has them all.
   */
  void s114_shared_batch_folder() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File dir = new File(home, "shared-batch");
    final PyJav pj = (PyJav) get("pyJav");
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    call("showView", "py");
    java.util.List<String> argv = java.util.Arrays.asList("--genre", "Neo Soul", "--bpm", "92", "--drums_only", "--batchdir", dir.getAbsolutePath());
    File wav = new File(home, "track.wav");
    Files.write(wav.toPath(), AudioIo.encodeWav(new short[22050], 22050));
    Midi2WavTest.startMusic(argv, wav, "");
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    final PythonRun.FileOut drums = new PythonRun.FileOut("track.mid", Engine.encodeMidi(cells, 92));
    edt(() -> log.setText(""));
    edt(() -> pj.testStep("DrumMidi_CRT.jar", new java.util.ArrayList<String>(), "Succeeded: track.mid", drums, null, ""));
    idle();
    out.append("after DrumMidi: ").append(new File(dir, "sogni-neo_soul-92bpm.mid").isFile()).append(", test over ").append(Midi2WavTest.current() == null).append('\n');
    for (String line : String.valueOf(log.getText()).split("\n")) if (line.startsWith("Batch folder:")) out.append("log: ").append(line).append('\n');
    // MidiDrumGen into the same folder: its grooves follow, numbered on.
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--count 2 --outdir \"" + dir.getAbsolutePath() + "\" --seed 4"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Succeeded") && !String.valueOf(log.getText()).contains("Failed"); i++) Thread.sleep(50);
    String list = new String(Files.readAllBytes(new File(dir, "batch.txt").toPath()), StandardCharsets.UTF_8);
    StringBuilder files = new StringBuilder();
    for (String line : list.split("\n")) if (!line.startsWith("#") && !line.startsWith("file\t") && line.trim().length() > 0) files.append('[').append(line.split("\t")[0]).append(" ").append(line.split("\t")[2]).append(']');
    out.append("batch.txt: ").append(files).append('\n');
    if (!files.toString().startsWith("[sogni-neo_soul-92bpm.mid SogniMusic][gen-002-") || !files.toString().contains("[gen-003-")) throw new AssertionError(out.toString() + log.getText());
  }

  /**
   * Drum Tracks: one main tab, with Pattern, Fillern and Fills as its subtabs under the main tabs
   * (shown with them only); the tab opens the subtab used last.
   */
  void s113_drum_tracks_tab() throws Exception {
    final StringBuilder seen = new StringBuilder();
    final Container root = frame.getContentPane();
    java.util.function.Supplier<String> state = () -> "view " + ((Pulsekit) frame).view + ", subtabs " + (component(root, "drum-subtabs").isVisible() ? "shown" : "hidden")
        + ", Drum Tracks lit " + ((javax.swing.JButton) component(root, "tab:drums")).getForeground().equals(((javax.swing.JButton) component(root, "tab:" + ((Pulsekit) frame).view)).getForeground());
    edt(() -> ((javax.swing.JButton) component(root, "tab:drums")).doClick());
    edt(() -> seen.append("Drum Tracks: ").append(state.get()).append('\n'));
    edt(() -> ((javax.swing.JButton) component(root, "tab:fills")).doClick());
    edt(() -> seen.append("Fills: ").append(state.get()).append('\n'));
    edt(() -> ((javax.swing.JButton) component(root, "tab:song")).doClick());
    edt(() -> seen.append("Song: view ").append(((Pulsekit) frame).view).append(", subtabs ").append(component(root, "drum-subtabs").isVisible() ? "shown" : "hidden").append('\n'));
    edt(() -> ((javax.swing.JButton) component(root, "tab:drums")).doClick());
    edt(() -> seen.append("Drum Tracks again: ").append(state.get()).append('\n'));
    edt(() -> ((javax.swing.JButton) component(root, "tab:pattern")).doClick());
    out.append(seen);
    if (!seen.toString().contains("Fills: view fills, subtabs shown, Drum Tracks lit true") || !seen.toString().contains("Song: view song, subtabs hidden")
        || !seen.toString().contains("Drum Tracks again: view fills, subtabs shown")) throw new AssertionError(out.toString());
  }

  /**
   * Favourite files and category tabs in the galleries: tabs for the main categories the files'
   * prompts are in, a favourite file starred and in the ★ Favourite files list.
   */
  void s112_favourite_files_tabs() throws Exception {
    FileFavourites.reset();
    PromptVault vault = PromptDb.vault();
    long video = vault.addCategory("Videos");
    long image = vault.addCategory("Images");
    java.io.ByteArrayOutputStream pngOut = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", pngOut);
    long pv = vault.addPrompt(video, "Sea");
    vault.addVersion(pv, "Sea", "", "Waves", "", "sea.png", pngOut.toByteArray(), "", null, "dusk.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 4}, "ai", "");
    long pi = vault.addPrompt(image, "Face");
    vault.addVersion(pi, "Face", "", "A face", "", "face.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 5}, "", null, "", null, "ai", "");
    PromptVault.StoredFile sea = null;
    for (PromptVault.StoredFile f : vault.referenceFiles()) if ("sea.png".equals(f.name)) sea = f;
    FileFavourites.load(PromptDb.dir());
    FileFavourites.toggle(sea);
    final PromptDb db = (PromptDb) get("promptDb");
    final StringBuilder seen = new StringBuilder();
    for (final long tab : new long[] {FileFavourites.ALL, image}) {
      PromptDb.galleryCategory = tab;
      inspectNext = d -> {
        seen.append(tab == FileFavourites.ALL ? "All" : "Images").append(": tabs ").append(component(d, "gallery-cat:Videos") != null).append(' ').append(component(d, "gallery-cat:Images") != null)
            .append(", sea.png ").append(component(d, "gallery:sea.png") != null).append(", face.png ").append(component(d, "gallery:face.png") != null);
        javax.swing.JButton card = (javax.swing.JButton) component(d, "gallery:sea.png");
        if (card != null) seen.append(", starred ").append(card.getText().contains("\u2605 sea.png"));
        javax.swing.JComboBox<?> favs = (javax.swing.JComboBox<?>) component(d, "gallery-favs");
        seen.append(", list ").append(favs == null ? "none" : favs.getItemCount() + " (" + favs.getItemAt(1) + ")").append('\n');
        d.dispose();
      };
      SwingUtilities.invokeLater(() -> db.gallery(false));
      int before = seen.length();
      for (int i = 0; i < 100 && seen.length() == before; i++) Thread.sleep(100);
      idle();
    }
    // Browse prompts: a prompt's pictures as small thumbnails on its row.
    PromptBrowser.lastCategory = video;
    PromptSearch.query = "";
    inspectNext = d -> {
      javax.swing.JButton item = (javax.swing.JButton) component(d, "prompt-browser-item:Sea");
      seen.append("Browse prompts: Sea's thumbnails ").append(item != null && item.getIcon() != null).append('\n');
      d.dispose();
    };
    int before = seen.length();
    SwingUtilities.invokeLater(() -> PromptBrowser.show((Pulsekit) frame, (title, text) -> {}));
    for (int i = 0; i < 100 && seen.length() == before; i++) Thread.sleep(100);
    inspectNext = null;
    PromptDb.galleryCategory = FileFavourites.ALL;
    FileFavourites.reset();
    out.append(seen);
    if (!seen.toString().contains("Sea's thumbnails true")) throw new AssertionError(out.toString());
    if (!seen.toString().contains("All: tabs true true, sea.png true, face.png true, starred true, list 2 (sea.png") || !seen.toString().contains("Images: tabs true true, sea.png false, face.png true"))
      throw new AssertionError(out.toString());
  }

  /**
   * Nested subcategories: a subcategory of a subcategory, the Prompts page's +/− tree, a category's
   * ★ Favourites item, Save prompt's "Clips / Night", Browse prompts' rows for each level.
   */
  void s111_category_tree() throws Exception {
    PromptVault vault = PromptDb.vault();
    long video = vault.addCategory("Video");
    long clips = vault.addSubcategory(video, "Clips");
    long night = vault.addSubcategory(clips, "Night");
    long walk = vault.addPrompt(night, "Night walk");
    long v1 = vault.addVersion(walk, "Night walk", "", "A walk under the moon", "", "", null, "", null, "", null, "ai", "");
    VersionFavourites.load(PromptDb.dir());
    VersionFavourites.add(v1);
    out.append("Night: ").append(vault.pathName(night)).append(", Save prompt finds Clips / Night ").append(PromptSave.find(vault, "Video", "Clips / Night") == night)
        .append(", offers ").append(PromptSave.subPaths(vault, video)).append('\n');
    call("showView", "prompts");
    idle();
    edt(() -> ((javax.swing.AbstractButton) component(frame.getContentPane(), "category:Video")).doClick());
    idle();
    final StringBuilder seen = new StringBuilder();
    edt(() -> {
      javax.swing.AbstractButton toggle = (javax.swing.AbstractButton) component(frame.getContentPane(), "category-toggle:Clips");
      seen.append("tree: Clips [").append(toggle.getText()).append("], Night shown ").append(component(frame.getContentPane(), "category:Night") != null).append('\n');
      toggle.doClick();
    });
    idle();
    edt(() -> {
      seen.append("+ on Clips: Night shown ").append(component(frame.getContentPane(), "category:Night") != null).append('\n');
      ((javax.swing.AbstractButton) component(frame.getContentPane(), "category:Night")).doClick();
    });
    idle();
    edt(() -> {
      seen.append("Night chosen, Night still shown ").append(component(frame.getContentPane(), "category:Night") != null).append('\n');
      javax.swing.AbstractButton more = (javax.swing.AbstractButton) component(frame.getContentPane(), "category-menu:Clips");
      seen.append("Clips menu has Favourites ").append(more != null).append('\n');
    });
    final StringBuilder browse = new StringBuilder();
    PromptBrowser.lastCategory = night;
    PromptSearch.query = "";
    inspectNext = d -> {
      java.util.List<Component> all = new java.util.ArrayList<Component>();
      java.util.ArrayDeque<Component> todo = new java.util.ArrayDeque<Component>();
      todo.add(d);
      while (!todo.isEmpty()) {
        Component c = todo.poll();
        if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().startsWith("Subcategories of ")) browse.append('[').append(((JLabel) c).getText()).append(']');
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) todo.add(k);
      }
      d.dispose();
    };
    SwingUtilities.invokeLater(() -> PromptBrowser.show((Pulsekit) frame, (title, text) -> {}));
    for (int i = 0; i < 50 && browse.length() == 0; i++) Thread.sleep(100);
    inspectNext = null;
    out.append(seen).append("Browse prompts rows: ").append(browse).append('\n');
    out.append("Clips favourites: ").append(VersionFavourites.inCategory(vault, clips).size()).append('\n');
    vault.deleteCategory(clips);
    out.append("after deleting Clips: Night ").append(vault.category(night) == null ? "gone" : "kept").append(", Night walk ").append(vault.prompt(walk) == null ? "gone" : "kept").append('\n');
    if (!seen.toString().contains("+ on Clips: Night shown true") || !browse.toString().contains("[Subcategories of Video][Subcategories of Clips]") || vault.category(night) != null
        || VersionFavourites.inCategory(vault, video).size() != 0)
      throw new AssertionError(out.toString());
  }

  /**
   * Prompt tags: Save prompt's Tag 1–3 (one from the tag list, one added new), kept with the prompt
   * and offered again on its next save; Browse prompts' search by #tag, text and category.
   */
  void s110_prompt_tags_search() throws Exception {
    PromptSave.setAsk(true);
    MediaTags.reset();
    MediaTags.load(PromptDb.dir());
    MediaTags.addTag("night");
    PromptVault vault = PromptDb.vault();
    vault.addCategory("Video");
    String[] bodies = {"Camera drifts over the night garden", "Camera drifts slowly over the night garden"};
    for (int run = 0; run < 2; run++) {
      String sheet = "PKPROMPT1\nsogni-video-garden\n\n\n\n\nCategory: video\nModel: Sogni H3\nReference file 1: \nReference file 2: \nType: ai\n---\n" + bodies[run] + "\n\nResult file: garden.mp4\n";
      java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
      files.add(new JavaRun.FileOut("sogni-video-garden.prompt", sheet.getBytes(StandardCharsets.UTF_8)));
      files.add(new JavaRun.FileOut("garden.mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'}));
      final JavaRun.Result r = new JavaRun.Result("Succeeded: garden.mp4", files, 0);
      final int at = run;
      final StringBuilder seen = new StringBuilder();
      inspectNext = d -> {
        javax.swing.JComboBox<?> t1 = (javax.swing.JComboBox<?>) component(d, "save-prompt-tag1");
        javax.swing.JComboBox<?> t2 = (javax.swing.JComboBox<?>) component(d, "save-prompt-tag2");
        seen.append("run ").append(at + 1).append(": Tag 1 ").append(t1.getSelectedItem()).append(", Tag 2 ").append(t2.getSelectedItem()).append(", Add new tag ")
            .append(component(d, "save-prompt-tag-new1") != null).append('\n');
        if (at == 0) {
          // Added new (as Add new tag does), then picked; Tag 2 from the list.
          String t = PromptSave.newTag(PromptDb.dir(), "garden");
          ((javax.swing.JComboBox<String>) t1).addItem(t);
          t1.setSelectedItem(t);
          t2.setSelectedItem("night");
          ((JTextField) component(d, "save-prompt-name")).setText("Garden walk");
        }
      };
      answers.add(run == 1 ? "Cancel" : "OK");
      String kept = (String) call("saveSheets", PromptDb.dir(), r, new java.util.ArrayList<String>());
      idle();
      out.append(seen);
      for (String line : kept.split("\n")) if (line.contains("tags ")) out.append("  log: ").append(line.replaceAll("\\d{4}-\\d\\d-\\d\\d", "<today>")).append('\n');
      answers.clear();
      inspectNext = null;
    }
    vault = PromptDb.vault();
    PromptVault.Prompt garden = null;
    for (PromptVault.Prompt p : vault.allPrompts()) if ("Garden walk".equals(p.title)) garden = p;
    out.append("library: Garden walk tags ").append(garden == null ? "none" : vault.tags(garden.id)).append('\n');
    final StringBuilder browse = new StringBuilder();
    PromptSearch.query = "";
    inspectNext = d -> {
      JTextField search = (JTextField) component(d, "prompt-browser-search");
      for (String q : new String[] {"#night", "drifts", "video garden", "#beach", "zzz"}) {
        search.setText(q);
        JLabel found = (JLabel) component(d, "prompt-browser-found");
        javax.swing.JButton item = (javax.swing.JButton) component(d, "prompt-browser-item:Garden walk");
        browse.append("search ").append(q).append(": ").append(found == null ? "none" : found.getText()).append(item == null ? "" : " | " + item.getText().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim()).append('\n');
      }
      search.setText("");
      d.dispose();
    };
    SwingUtilities.invokeLater(() -> PromptBrowser.show((Pulsekit) frame, (title, text) -> {}));
    for (int i = 0; i < 50 && browse.length() == 0; i++) Thread.sleep(100);
    idle();
    inspectNext = null;
    out.append(browse);
    PromptSearch.query = "";
    MediaTags.reset();
    if (garden == null || !vault.tags(garden.id).equals(java.util.Arrays.asList("garden", "night")) || !out.toString().contains("run 2: Tag 1 garden, Tag 2 night")
        || !browse.toString().contains("search #night: 1 prompt found | Garden walk Video \u00b7 #garden #night") || !browse.toString().contains("search zzz: 0 prompts found"))
      throw new AssertionError(out.toString());
  }

  /**
   * Media browser tags and categories: Tags… and Category… in a file's menu, the line on its card,
   * the Tagged filter, Rename keeping them; file cards drag onto folder cards (moved there).
   */
  void s109_media_tags() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "media-tags");
    new File(media, "keep").mkdirs();
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
    javax.imageio.ImageIO.write(img, "png", new File(media, "a.png"));
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(50, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", new File(media, "b.png"));
    MediaTags.reset();
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    final javax.swing.JDialog[] dlg = new javax.swing.JDialog[1];
    answers.add("(keep)");
    inspectNext = d -> dlg[0] = d;
    SwingUtilities.invokeLater(() -> mb.open(media));
    for (int i = 0; i < 100 && dlg[0] == null; i++) Thread.sleep(50);
    final String cat = MediaTags.categoryNames(PromptDb.vault()).get(0);
    MediaBrowser.answer = q -> q.startsWith("Tags for") ? "beach, #night" : q.startsWith("Category for") ? cat : q.startsWith("New name") ? "sea" : null;
    final StringBuilder seen = new StringBuilder();
    try {
      edt(() -> {
        javax.swing.JButton card = (javax.swing.JButton) component(dlg[0], "media-card:a.png");
        card.dispatchEvent(new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3));
        javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) get("lastMenu");
        StringBuilder items = new StringBuilder();
        for (Component c : menu.getComponents()) if (c instanceof javax.swing.JMenuItem) items.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
        seen.append("menu: ").append(items).append('\n');
        ((javax.swing.JMenuItem) component(menu, "media-menu:" + MediaTags.TAGS)).doClick();
        menu.setVisible(false);
      });
      edt(() -> {
        javax.swing.JButton card = (javax.swing.JButton) component(dlg[0], "media-card:a.png");
        card.dispatchEvent(new java.awt.event.MouseEvent(card, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            java.awt.event.InputEvent.BUTTON3_DOWN_MASK, 10, 10, 1, true, java.awt.event.MouseEvent.BUTTON3));
        javax.swing.JPopupMenu menu = (javax.swing.JPopupMenu) get("lastMenu");
        ((javax.swing.JMenuItem) component(menu, "media-menu:" + MediaTags.CATEGORY)).doClick();
        menu.setVisible(false);
      });
      edt(() -> {
        javax.swing.JButton card = (javax.swing.JButton) component(dlg[0], "media-card:a.png");
        seen.append("card: ").append(card.getText().contains("#beach #night \u00b7 " + cat)).append('\n');
        seen.append("a.png: ").append(MediaTags.line(MediaTags.key("a.png", new File(media, "a.png").length()))).append('\n');
        // Drag: file cards and folder cards (and Up) take part.
        seen.append("drag: file card ").append(card.getTransferHandler() != null).append(", folder card ")
            .append(((javax.swing.JComponent) component(dlg[0], "media-folder:keep")).getTransferHandler() != null).append('\n');
        javax.swing.JComboBox<?> tagged = (javax.swing.JComboBox<?>) component(dlg[0], "media-tag-filter");
        StringBuilder choices = new StringBuilder();
        for (int i = 0; i < tagged.getItemCount(); i++) choices.append('[').append(tagged.getItemAt(i)).append(']');
        seen.append("Tagged: ").append(choices).append('\n');
        for (int i = 0; i < tagged.getItemCount(); i++) if (String.valueOf(tagged.getItemAt(i)).equals("#night (1)")) tagged.setSelectedIndex(i);
      });
      edt(() -> {
        seen.append("tagged #night: a.png ").append(component(dlg[0], "media-card:a.png") != null).append(", b.png ").append(component(dlg[0], "media-card:b.png") != null).append('\n');
        MediaTags.filter = "";
        mb.fileAction(null, new File(media, "a.png"), MediaDir.RENAME);
        seen.append("renamed sea.png: ").append(MediaTags.line(MediaTags.key("sea.png", new File(media, "sea.png").length()))).append('\n');
        seen.append("dropped: ").append(mb.dropped(new File(media, "sea.png"), new File(media, "keep"))).append('\n');
      });
    } finally {
      MediaBrowser.answer = null;
      MediaTags.reset();
      edt(() -> dlg[0].dispose());
    }
    out.append(seen);
    // (The menu is in alphabetical order: Category… and Tags… are not side by side.)
    if (!seen.toString().contains("[Tags\u2026]") || !seen.toString().contains("[Category\u2026]") || !seen.toString().contains("card: true") || !seen.toString().contains("tagged #night: a.png true, b.png false")
        || !seen.toString().contains("renamed sea.png: #beach #night") || !seen.toString().contains("drag: file card true, folder card true") || !new File(media, "keep/sea.png").isFile())
      throw new AssertionError(out.toString());
  }

  /**
   * BatchRun: its plan runs one run after another (VideoFrames on each clip of a list here; Sogni
   * programs alike), what each makes kept and not shown, then the summary; Stop batch ends it after
   * the run in progress; JoinVideo --list joins a list of clips.
   */
  void s108_batch_run() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File dir = new File(System.getProperty("pulsekit.test.dir"));
    File list = new File(home, "clips.txt");
    Files.write(list.toPath(), (new File(dir, "clip-a.mp4").getAbsolutePath() + "\n" + new File(dir, "clip-b.mp4").getAbsolutePath() + "\n").getBytes(StandardCharsets.UTF_8));
    for (ProgramParams.Param p : ProgramParams.parse(ProgramFiles.bundledSource("BatchRun.java"))) out.append("BatchRun ").append(p.token).append(": ").append(p.label).append(p.dir ? " (folder)" : p.isFile() ? " (file)" : "").append('\n');
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    final PyJav pj = (PyJav) get("pyJav");
    call("showView", "py");
    call("selectListedProgram", "Java", "BatchRun.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--program VideoFrames --list \"" + list.getAbsolutePath() + "\" --slot input --args --first"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 1200 && !String.valueOf(log.getText()).contains("Batch done") && !String.valueOf(log.getText()).contains("Batch stopped"); i++) Thread.sleep(50);
    Thread.sleep(300);
    java.util.List<String> pics = new java.util.ArrayList<String>();
    for (String d : new String[] {"Downloads", ".pulsekit"}) {
      String[] in = new File(home, d).list();
      if (in != null) for (String n : in) if (n.endsWith("-first.png")) pics.add(n);
    }
    java.util.Collections.sort(pics);
    String summary = "";
    for (String line : String.valueOf(log.getText()).split("\n")) if (line.startsWith("Batch")) summary = line;
    out.append("batch: ").append(summary).append(", program now ").append(get("pyName")).append(", pictures ").append(pics).append(", Stop batch shown ").append(pj.pyBatchStop.isVisible()).append('\n');
    if (!summary.equals("Batch done: 2 of 2 runs succeeded") || !pics.contains("clip-a-first.png") || !pics.contains("clip-b-first.png") || pj.pyBatchStop.isVisible() || BatchQueue.current() != null)
      throw new AssertionError(out.toString() + log.getText());
    // Stop batch during the first run: the second is not run.
    call("selectListedProgram", "Java", "BatchRun.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--program VideoFrames --list \"" + list.getAbsolutePath() + "\" --slot input --args --last"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && BatchQueue.current() == null; i++) Thread.sleep(20);
    out.append("Stop batch shown while it runs: ").append(pj.pyBatchStop.isVisible()).append('\n');
    edt(() -> pj.stopBatch());
    for (int i = 0; i < 1200 && BatchQueue.current() != null; i++) Thread.sleep(50);
    Thread.sleep(300);
    summary = "";
    for (String line : String.valueOf(log.getText()).split("\n")) if (line.startsWith("Batch")) summary = line;
    out.append("stopped: ").append(summary).append('\n');
    if (!summary.equals("Batch stopped: 1 of 2 runs succeeded, 1 not run")) throw new AssertionError(out.toString() + log.getText());
    // JoinVideo --list: the clips of a list, in order.
    call("selectListedProgram", "Java", "JoinVideo.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--list \"" + list.getAbsolutePath() + "\""));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Succeeded") && !String.valueOf(log.getText()).contains("Failed"); i++) Thread.sleep(50);
    String joined = "";
    for (String line : String.valueOf(log.getText()).split("\n")) if (line.startsWith("Joined")) joined = line;
    out.append("JoinVideo --list: ").append(joined).append('\n');
    if (!joined.startsWith("Joined clip-a.mp4 and then clip-b.mp4")) throw new AssertionError(out.toString() + log.getText());
  }

  /**
   * MidiBatch: a folder of MIDI files (and its subfolders) into a batch folder with list.txt and
   * batch.txt; --combine joins them at random. Then Midi2Wav --dir --test renders it with Pulsekit's
   * kit, DrumMidi_CRT --dir reads the WAVs and CompareHits --batch sums them, in the test report.
   */
  void s107_midi_batch() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File src = new File(home, "my-midis");
    new File(src, "sub").mkdirs();
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    for (int i = 4; i < 16; i += 8) cells[2][i] = 100;
    for (int i = 0; i < 16; i += 2) cells[4][i] = 80;
    Files.write(new File(src, "a.mid").toPath(), Engine.encodeMidi(cells, 100));
    Files.write(new File(src, "sub/b.mid").toPath(), Engine.encodeMidi(cells, 140));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    call("showView", "py");
    for (ProgramParams.Param p : ProgramParams.parse(ProgramFiles.bundledSource("MidiBatch.java"))) out.append("MidiBatch ").append(p.token).append(": ").append(p.label).append(p.dir ? " (folder)" : "").append('\n');
    // Copied as they are.
    File mix = new File(home, "mix-a");
    call("selectListedProgram", "Java", "MidiBatch.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--dir \"" + src.getAbsolutePath() + "\" --recursive --outdir \"" + mix.getAbsolutePath() + "\" --seed 7"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Succeeded") && !String.valueOf(log.getText()).contains("Failed"); i++) Thread.sleep(50);
    Thread.sleep(300);
    String[] made = mix.list();
    java.util.Arrays.sort(made == null ? new String[0] : made);
    out.append("copied: ").append(java.util.Arrays.toString(made)).append('\n');
    if (!new File(mix, "batch.txt").isFile()) throw new AssertionError(out.toString() + log.getText());
    String batch = new String(Files.readAllBytes(new File(mix, "batch.txt").toPath()), StandardCharsets.UTF_8);
    out.append("batch.txt:\n").append(batch);
    if (!batch.contains("mix-002-b.mid\tb\tsub\t140\t4/4")) throw new AssertionError(out.toString() + log.getText());
    // Joined: 3 files of 2 parts each.
    File joined = new File(home, "mix-b");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--list \"" + new File(mix, "list.txt").getAbsolutePath() + "\" --combine 2 --count 3 --cut 1 --outdir \"" + joined.getAbsolutePath() + "\" --seed 7"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Succeeded") && !String.valueOf(log.getText()).contains("Failed"); i++) Thread.sleep(50);
    Thread.sleep(300);
    javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(new File(joined, "mix-001-2-parts.mid"));
    out.append("joined: ").append(String.valueOf(log.getText()).contains("Succeeded: 3 MIDI files")).append(", first file ").append(seq.getTickLength()).append(" ticks (2 bars of 480)\n");
    if (seq.getTickLength() != 3840) throw new AssertionError(out.toString() + log.getText());
    // Midi2Wav --dir --test with Pulsekit's kit: the WAVs, DrumMidi on each, CompareHits --batch, the report.
    final StringBuilder report = new StringBuilder();
    final Object[] look = new Object[1];
    look[0] = (java.util.function.Consumer<JDialog>) d -> {
      java.awt.Component c = component(d, "midi2wav-test-report");
      if (c != null) report.append(((javax.swing.JTextArea) c).getText());
      else inspectNext = (java.util.function.Consumer<JDialog>) look[0];
    };
    @SuppressWarnings("unchecked")
    java.util.function.Consumer<JDialog> first = (java.util.function.Consumer<JDialog>) look[0];
    inspectNext = first;
    call("selectListedProgram", "Java", "Midi2Wav.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--dir \"" + mix.getAbsolutePath() + "\" --test"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 2400 && report.length() == 0; i++) Thread.sleep(50);
    inspectNext = null;
    String[] after = mix.list();
    java.util.Arrays.sort(after);
    out.append("batch folder after: ").append(java.util.Arrays.toString(after)).append('\n');
    out.append("report:\n").append(report.toString().replace(home.getAbsolutePath(), "~")).append('\n');
    if (!report.toString().startsWith("Midi2Wav batch test: mix-a") || !report.toString().contains("== Pulsekit-kit: 2 grooves") || !new File(mix, "mix-001-a-Pulsekit-kit-dm.mid").isFile())
      throw new AssertionError(out.toString() + log.getText());
  }

  /**
   * --vst: MidiDrumGen's MIDI goes to vst_render.py (not Midi2Wav) with the plugin and settings saved
   * in vst_render's Params (and its --test); SogniMusic's DrumMidi MIDI of the track does too. Without
   * a saved plugin the log says to pick one. (vst_render itself needs DawDreamer and a plugin: only
   * its arguments are checked.)
   */
  void s106_vst_render_option() throws Exception {
    File home = new File(System.getProperty("user.home"));
    for (String prog : new String[] {"MidiDrumGen.java", "SogniMusic.java"}) {
      for (ProgramParams.Param p : ProgramParams.parse(ProgramFiles.bundledSource(prog))) if (p.token.equals("--vst")) out.append(prog).append(" --vst: ").append(p.label).append('\n');
    }
    final PyJav pj = (PyJav) get("pyJav");
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    call("showView", "py");
    // No plugin saved: the log says so, and nothing else runs.
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style Rock --bars 2 --vst"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("No VST render"); i++) Thread.sleep(50);
    out.append("no plugin saved: ").append(String.valueOf(log.getText()).contains("No VST render: pick the plugin")).append(", program ").append(get("pyName")).append('\n');
    if (!String.valueOf(log.getText()).contains("No VST render") || !"MidiDrumGen.java".equals(get("pyName"))) throw new AssertionError(out.toString() + log.getText());
    // vst_render.py's Params saved: its plugin, settings and format are used, not its folder or Edit.
    File plugin = new File(home, "Anarchy.vst3");
    edt(() -> pj.saveParams("vst_render.py", "--plugin \"" + plugin.getAbsolutePath() + "\" --state \"" + new File(home, "kit 1.state").getAbsolutePath() + "\" --rate 48000 --dir beats --edit --no-toms"));
    java.util.List<String> args = Midi2WavTest.vstArgs("/x/a.mid", ProgramFiles.bundledSource("vst_render.py"), pj.loadParams("vst_render.py"), true);
    out.append("vst_render args: ").append(String.valueOf(args).replace(home.getAbsolutePath(), "~")).append('\n');
    if (args == null || !args.contains("--plugin") || !args.contains("--state") || !args.contains("48000") || args.contains("--dir") || args.contains("--edit") || !args.contains("--test")) throw new AssertionError(out.toString());
    // A Carla project alone (Linux: its LV2 or bridged plugins, in Carla-Rack) is a plugin for --vst too.
    String vstSaved = pj.loadParams("vst_render.py");
    edt(() -> pj.saveParams("vst_render.py", "--carla \"" + new File(home, "Anarchy kit.carxp").getAbsolutePath() + "\" --rate 48000"));
    java.util.List<String> carla = Midi2WavTest.vstArgs("/x/a.mid", ProgramFiles.bundledSource("vst_render.py"), pj.loadParams("vst_render.py"), false);
    out.append("Carla project args: ").append(String.valueOf(carla).replace(home.getAbsolutePath(), "~")).append('\n');
    if (carla == null || !carla.contains("--carla") || carla.contains("--plugin")) throw new AssertionError(out.toString());
    for (ProgramParams.Param p : ProgramParams.parse(ProgramFiles.bundledSource("vst_render.py"))) if (p.token.equals("--carla") || p.token.equals("--drumgizmo") || p.token.equals("--midimap")) out.append(p.token).append(" row: ").append(p.label).append('\n');
    // A DrumGizmo kit alone (DrumGizmo's own program plays it) too.
    edt(() -> pj.saveParams("vst_render.py", "--drumgizmo \"" + new File(home, "DRSKit/DRSKit_full.xml").getAbsolutePath() + "\" --rate 48000"));
    java.util.List<String> gizmo = Midi2WavTest.vstArgs("/x/a.mid", ProgramFiles.bundledSource("vst_render.py"), pj.loadParams("vst_render.py"), false);
    out.append("DrumGizmo kit args: ").append(String.valueOf(gizmo).replace(home.getAbsolutePath(), "~")).append('\n');
    if (gizmo == null || !gizmo.contains("--drumgizmo") || gizmo.contains("--plugin")) throw new AssertionError(out.toString());
    edt(() -> pj.saveParams("vst_render.py", vstSaved));
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--style Rock --bars 2 --vst --test"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !"vst_render.py".equals(get("pyName")); i++) Thread.sleep(50);
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Failed") && !String.valueOf(log.getText()).contains("Succeeded"); i++) Thread.sleep(50);
    Thread.sleep(300);
    String ran = "";
    for (String line : String.valueOf(log.getText()).split("\n")) if (line.trim().startsWith("$ ")) ran = line.trim();
    out.append("MidiDrumGen --vst --test: program ").append(get("pyName")).append(", ran ").append(ran.contains("--plugin") && ran.contains("Anarchy.vst3") && ran.contains("--test") && ran.contains("--drummidi") && !ran.contains("--dir")).append('\n');
    if (!"vst_render.py".equals(get("pyName")) || !ran.contains("Anarchy.vst3") || !ran.contains("--drummidi")) throw new AssertionError(out.toString() + log.getText());
    // SogniMusic --vst (no --test): after DrumMidi_CRT's MIDI of the track, vst_render.py without its test.
    File wav = new File(home, "track.wav");
    Files.write(wav.toPath(), new byte[] {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'A', 'V', 'E'});
    Midi2WavTest t = Midi2WavTest.startMusic(java.util.Arrays.asList("--drums_only", "--vst"), wav, "");
    out.append("SogniMusic --vst: test ").append(t.testing).append(", vst ").append(t.vst).append('\n');
    int[][] cells = new int[12][16];
    for (int i = 0; i < 16; i += 4) cells[0][i] = 100;
    final PythonRun.FileOut drums = new PythonRun.FileOut("track.mid", Engine.encodeMidi(cells, 120));
    edt(() -> log.setText(""));
    edt(() -> pj.testStep("DrumMidi_CRT.jar", new java.util.ArrayList<String>(), "Succeeded: track.mid", drums, null, ""));
    for (int i = 0; i < 600 && !"vst_render.py".equals(get("pyName")); i++) Thread.sleep(50);
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Failed") && !String.valueOf(log.getText()).contains("Succeeded"); i++) Thread.sleep(50);
    Thread.sleep(300);
    ran = "";
    for (String line : String.valueOf(log.getText()).split("\n")) if (line.trim().startsWith("$ ")) ran = line.trim();
    out.append("then: ").append(get("pyName")).append(" on track-drums.mid ").append(ran.contains("track-drums.mid")).append(", its test ").append(ran.contains("--test")).append('\n');
    if (t.testing || !t.vst || !ran.contains("track-drums.mid") || ran.contains("--test") || Midi2WavTest.current() != null) throw new AssertionError(out.toString() + log.getText());
  }

  /**
   * A video picked for a picture row of Params (SogniVideo's --image): its first frame goes there and
   * its last frame to --end_image, still empty; read with VLC.
   */
  void s105_video_in_picture_slot() throws Exception {
    File clip = new File(System.getProperty("pulsekit.test.dir"), "clip-a.mp4");
    File src = new File(System.getProperty("pulsekit.test.dir"), "../../Programs/Java/SogniVideo.java");
    java.util.List<ProgramParams.Param> ps = ProgramParams.parse(new String(java.nio.file.Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8));
    int image = -1, end = -1;
    for (int i = 0; i < ps.size(); i++) {
      if ("--image".equals(ps.get(i).token)) image = i;
      if ("--end_image".equals(ps.get(i).token)) end = i;
    }
    final String[] values = new String[ps.size()];
    final javax.swing.JLabel[] labels = new javax.swing.JLabel[ps.size()];
    labels[image] = new javax.swing.JLabel();
    labels[end] = new javax.swing.JLabel();
    final PyJav pj = (PyJav) get("pyJav");
    pj.lastFramesNote = null;
    final int row = image;
    edt(() -> pj.assignFile(ps, values, row, labels, clip.getAbsolutePath(), null));
    for (int i = 0; i < 600 && pj.lastFramesNote == null; i++) Thread.sleep(50);
    Thread.sleep(200);
    out.append("frames: ").append(pj.lastFramesNote).append('\n');
    out.append("--image ").append(values[image] == null ? "none" : new File(values[image]).getName()).append(" (").append(labels[image].getText()).append(")\n");
    out.append("--end_image ").append(values[end] == null ? "none" : new File(values[end]).getName()).append(" (").append(labels[end].getText()).append(")\n");
    if (!"first and last frame".equals(pj.lastFramesNote) || !String.valueOf(values[image]).endsWith("clip-a-first.png") || !String.valueOf(values[end]).endsWith("clip-a-last.png")
        || !new File(values[end]).isFile()) throw new AssertionError(out.toString());
  }

  /**
   * VideoFrames (PyJav's Java menu): the first and last frame and one at a time, read with VLC through
   * Pulsekit's own JAR; --upscale then opens ImageUpscaler with the first picture as its input.
   */
  void s104_video_frames() throws Exception {
    File clip = new File(System.getProperty("pulsekit.test.dir"), "clip-a.mp4");
    call("showView", "py");
    call("selectListedProgram", "Java", "VideoFrames.java");
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("\"" + clip.getAbsolutePath() + "\" --first --last --at 0:01.5 --upscale"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 1200 && !"ImageUpscaler.java".equals(get("pyName")) && !String.valueOf(log.getText()).contains("Failed"); i++) Thread.sleep(50);
    Thread.sleep(500);
    // The pictures, kept in the program files folder (~/Downloads, or ~/.pulsekit without one).
    File home = new File(System.getProperty("user.home"));
    java.util.List<String> pics = new java.util.ArrayList<String>();
    for (String d : new String[] {"Downloads", ".pulsekit"}) {
      String[] in = new File(home, d).list();
      if (in != null) for (String n : in) if (n.startsWith("clip-a-") && n.endsWith(".png")) pics.add(n);
    }
    java.util.Collections.sort(pics);
    out.append("pictures: ").append(pics).append('\n');
    out.append("then: ").append(get("pyName")).append(", input ").append(get("pyInputPath") == null ? "none" : new File(String.valueOf(get("pyInputPath"))).getName()).append('\n');
    if (!"ImageUpscaler.java".equals(get("pyName")) || get("pyInputPath") == null || !String.valueOf(get("pyInputPath")).contains("clip-a-first")
        || !pics.contains("clip-a-last.png") || !pics.contains("clip-a-0m01s500.png")) throw new AssertionError(out.toString());
  }

  /**
   * Media browser: a folder shows its first picture; Up comes back to the folder left (outlined);
   * a file's menu has Rename, Copy to folder, Move to folder and Delete, a folder's Rename and Delete.
   */
  void s103_media_files() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File media = new File(home, "media-files");
    File shots = new File(media, "shots");
    shots.mkdirs();
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
    javax.imageio.ImageIO.write(img, "png", new File(shots, "b.png"));
    javax.imageio.ImageIO.write(img, "png", new File(shots, "a.png"));
    javax.imageio.ImageIO.write(img, "png", new File(media, "top.png"));
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    final StringBuilder seen = new StringBuilder();
    final javax.swing.JDialog[] dlg = new javax.swing.JDialog[1];
    answers.add("(keep)");
    inspectNext = d -> dlg[0] = d;
    SwingUtilities.invokeLater(() -> mb.open(media));
    for (int i = 0; i < 100 && dlg[0] == null; i++) Thread.sleep(50);
    for (int i = 0; i < 60 && ((AbstractButton) component(dlg[0], "media-folder:shots")).getIcon() == null; i++) Thread.sleep(50);
    edt(() -> {
      AbstractButton folder = (AbstractButton) component(dlg[0], "media-folder:shots");
      seen.append("folder thumbnail: ").append(folder.getIcon() != null).append('\n');
      folder.doClick();
    });
    edt(() -> ((AbstractButton) component(dlg[0], "media-up")).doClick());
    edt(() -> {
      AbstractButton folder = (AbstractButton) component(dlg[0], "media-folder:shots");
      seen.append("back up: shots outlined ").append(folder.getBorder() instanceof javax.swing.border.LineBorder).append('\n');
    });
    // File actions, answered without dialogs.
    MediaBrowser.answer = q -> q.startsWith("New name") ? "beach" : q.startsWith("Copy") || q.startsWith("Move") ? new File(media, "kept").getAbsolutePath() : "yes";
    try {
      edt(() -> {
        String[] items = MediaDir.menu(false, MediaDir.PICTURE, true);
        seen.append("file menu: ").append(java.util.Arrays.toString(items)).append('\n');
        MediaDir.Entry e = new MediaDir.Entry();
        e.name = "top.png";
        e.id = new File(media, "top.png").getAbsolutePath();
        e.kind = MediaDir.PICTURE;
        mb.fileAction(e, new File(media, "top.png"), MediaDir.RENAME);
        mb.fileAction(e, new File(media, "beach.png"), MediaDir.COPY_TO);
        mb.fileAction(e, new File(shots, "b.png"), MediaDir.MOVE_TO);
        mb.fileAction(e, new File(shots, "a.png"), MediaDir.DELETE);
      });
    } finally {
      MediaBrowser.answer = null;
    }
    String[] top = media.list();
    java.util.Arrays.sort(top);
    String[] kept = new File(media, "kept").list();
    java.util.Arrays.sort(kept);
    seen.append("after: ").append(java.util.Arrays.toString(top)).append(", kept ").append(java.util.Arrays.toString(kept)).append(", shots ").append(java.util.Arrays.toString(shots.list())).append('\n');
    edt(() -> dlg[0].dispose());
    out.append(seen);
    if (seen.indexOf("folder thumbnail: true") < 0 || seen.indexOf("outlined true") < 0 || !new File(media, "beach.png").isFile()
        || !java.util.Arrays.asList(kept).equals(java.util.Arrays.asList("b.png", "beach.png")) || shots.list().length != 0) throw new AssertionError(out.toString());
  }

  /** A prompt's reference picture shows as a small thumbnail on its name; a slot without one shows none. */
  void s102_slot_thumbnails() throws Exception {
    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img, "png", png);
    PromptVault vault = PromptDb.vault();
    long cat = vault.categories().get(0).id;
    long id = vault.addPrompt(cat, "Sky");
    long v1 = vault.addVersion(id, "Sky", "", "Blue sky", "", "sky.png", png.toByteArray(), "", null, "", null, "ai", "");
    PromptsPage page = (PromptsPage) get("promptsPage");
    call("showView", "prompts");
    edt(() -> {
      page.openPrompt(id);
      page.loadVersion(v1);
    });
    for (int i = 0; i < 100 && page.promptRef1.getIcon() == null; i++) Thread.sleep(30);
    idle();
    out.append("reference 1 thumbnail: ").append(page.promptRef1.getIcon() == null ? "none" : page.promptRef1.getIcon().getIconWidth() + "x" + page.promptRef1.getIcon().getIconHeight())
        .append(", reference 2 (none): ").append(page.promptRef2.getIcon() != null).append('\n');
    if (page.promptRef1.getIcon() == null || page.promptRef2.getIcon() != null) throw new AssertionError(out.toString());
  }

  /**
   * MidiDrumGen --count: a batch of grooves in random styles, into a folder named plainly (it goes in
   * the program files folder, not the run's temporary one), with batch.txt; the app's style is not
   * passed to it. Then CompareHits --batch on the folder sums DrumMidi's results per style (here
   * the grooves themselves stand for DrumMidi's MIDI: every hit found).
   */
  void s101_drumgen_batch() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Java", "MidiDrumGen.java");
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--count 3 --seed 5 --styles rock,funk --outdir gen3"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Succeeded") && !String.valueOf(log.getText()).contains("Failed"); i++) Thread.sleep(50);
    Thread.sleep(300);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("MidiDrumGen batch") || line.startsWith("Wrote") || line.startsWith("Succeeded") || line.startsWith("Failed") || line.startsWith("$ java"))
        out.append("run: ").append(line.replace(home.getAbsolutePath(), "~").replaceAll("--seed \\d+", "--seed N")).append('\n');
    }
    java.io.File dir = null;
    for (String d : new String[] {"Downloads", ".pulsekit"}) if (new File(new File(home, d), "gen3").isDirectory()) dir = new File(new File(home, d), "gen3");
    out.append("folder: ").append(dir == null ? "none" : home.toPath().relativize(dir.toPath())).append('\n');
    if (dir == null) throw new AssertionError(out.toString());
    String[] names = dir.list();
    java.util.Arrays.sort(names);
    out.append("files: ").append(java.util.Arrays.toString(names)).append('\n');
    String list = new String(Files.readAllBytes(new File(dir, "batch.txt").toPath()), StandardCharsets.UTF_8);
    int grooves = 0;
    for (String line : list.split("\n")) if (line.startsWith("gen-")) grooves++;
    if (grooves != 3 || list.contains("\thouse\t")) throw new AssertionError(out.toString());
    // vst_render.py --dir --test leaves <groove>-<plugin>-dm.mid beside each render.
    for (String n : names) {
      if (n.endsWith(".mid")) Files.copy(new File(dir, n).toPath(), new File(dir, n.replace(".mid", "-Kit-dm.mid")).toPath());
    }
    call("selectListedProgram", "Java", "CompareHits.java");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--batch gen3"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !String.valueOf(log.getText()).contains("Succeeded") && !String.valueOf(log.getText()).contains("Failed"); i++) Thread.sleep(50);
    Thread.sleep(300);
    for (String line : String.valueOf(log.getText()).split("\n")) {
      if (line.startsWith("CompareHits --batch") || line.startsWith("== ") || line.startsWith("All:") || line.startsWith("Succeeded") || line.startsWith("Failed"))
        out.append("summary: ").append(line).append('\n');
    }
    out.append("batch-summary.txt: ").append(new File(dir, "batch-summary.txt").isFile()).append('\n');
    if (!String.valueOf(log.getText()).contains("Succeeded: 3 renders of 3 grooves") || !new File(dir, "batch-summary.txt").isFile()) throw new AssertionError(out.toString());
  }

  /**
   * --saveprompt: the run's sheet is kept in the library under Prompt files, not written to disk.
   * Prompts page: Prompt files lists it and its text shows. Media browser: under T with a star
   * (not under A), its text on a click, and Choose file gets a copy.
   */
  void s100_prompt_files_in_db() throws Exception {
    File home = new File(System.getProperty("user.home"));
    String sheet = "PKPROMPT1\nsogni-pedit-smile\n\n\n\n\nCategory: image\nModel: Sogni Krea 2 Identity Edit\nReference file 1: \nReference file 2: \nType: ai\n---\n"
        + "Same person, smiling\n\nResult file: sogni-pedit-smile.png\n";
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    files.add(new JavaRun.FileOut("sogni-pedit-smile.prompt", sheet.getBytes(StandardCharsets.UTF_8)));
    files.add(new JavaRun.FileOut("sogni-pedit-smile.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2}));
    JavaRun.Result r = new JavaRun.Result("Succeeded: sogni-pedit-smile.png", files, 0);
    String kept = (String) call("saveSheets", PromptDb.dir(), r, new java.util.ArrayList<String>());
    for (String line : kept.split("\n")) out.append("log: ").append(line.replace(PromptSave.today(), "<today>").replace(home.getAbsolutePath(), "~")).append('\n');
    PromptVault vault = PromptDb.vault();
    java.util.List<PromptVault.StoredFile> pf = vault.promptFiles();
    out.append("prompt files: ").append(pf.size());
    for (PromptVault.StoredFile f : pf) out.append(" [").append(f.name).append(" \u00b7 ").append(f.promptTitle).append(']');
    out.append(", in Ref files ").append(vault.referenceFiles().size()).append(", in Result files ").append(vault.resultFiles().size()).append('\n');
    boolean onDisk = false;
    for (File d : new File[] {new File(home, "Downloads"), new File(home, ".pulsekit")}) {
      File[] in = d.listFiles();
      if (in != null) for (File f : in) if (f.getName().endsWith(".prompt")) onDisk = true;
    }
    out.append("sheet on disk: ").append(onDisk).append('\n');
    if (pf.size() != 1 || onDisk || !kept.contains("Prompt files")) throw new AssertionError(out.toString());
    // Prompts page: Prompt files.
    call("showView", "prompts");
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      seen.append("gallery: ").append(d.getTitle()).append(", card ").append(component(d, "gallery:sogni-pedit-smile.prompt") != null).append('\n');
      inspectNext = preview -> seen.append("preview: ").append(preview.getTitle()).append(", shows the prompt ")
          .append(texts(preview.getContentPane()).toString().contains("Same person, smiling")).append('\n');
      final AbstractButton card = (AbstractButton) component(d, "gallery:sogni-pedit-smile.prompt");
      SwingUtilities.invokeLater(card::doClick);
    };
    SwingUtilities.invokeLater(() -> ((AbstractButton) component(frame, "prompts-prompt-files")).doClick());
    for (int i = 0; i < 100 && seen.indexOf("preview") < 0; i++) Thread.sleep(100);
    idle();
    out.append(seen);
    if (seen.indexOf("shows the prompt true") < 0) throw new AssertionError(out.toString());
    // Media browser: T lists it with a star, A does not.
    File media = new File(home, "media-prompts");
    media.mkdirs();
    Files.write(new File(media, "a.png").toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
    MediaDir.remember(media.getAbsolutePath(), new ArrayList<String>());
    MediaBrowser.saveLoop();
    final MediaBrowser mb = (MediaBrowser) get("mediaBrowser");
    final StringBuilder browsed = new StringBuilder();
    final File[] picked = new File[1];
    MediaDir.filter = "A";
    inspectNext = d -> {
      AbstractButton t = (AbstractButton) component(d, "media-filter:T");
      browsed.append("A: card ").append(component(d, "media-card:sogni-pedit-smile.prompt") != null).append(", T ").append(t.getToolTipText()).append('\n');
      t.doClick();
      AbstractButton card = (AbstractButton) component(d, "media-card:sogni-pedit-smile.prompt");
      browsed.append("T: card ").append(card != null).append(", shows ").append(card == null ? "" : card.getText().contains("\u2605 sogni-pedit-smile.prompt")).append('\n');
      SwingUtilities.invokeLater(card::doClick);
    };
    SwingUtilities.invokeLater(() -> mb.choose(f -> picked[0] = f, () -> {}));
    for (int i = 0; i < 100 && picked[0] == null; i++) Thread.sleep(100);
    idle();
    out.append(browsed);
    out.append("chosen: ").append(picked[0] == null ? "none" : picked[0].getName() + ", " + new String(Files.readAllBytes(picked[0].toPath()), StandardCharsets.UTF_8).contains("Same person, smiling")).append('\n');
    MediaDir.filter = "A";
    if (picked[0] == null || browsed.indexOf("A: card false") < 0 || browsed.indexOf("shows true") < 0) throw new AssertionError(out.toString());
  }

  /**
   * The Result files gallery open while a file is added to the library: it opens again with it.
   * A picture's Preview from the gallery has Previous / Next, which pass a text file between pictures.
   */
  void s97_gallery_refresh_prevnext() throws Exception {
    PromptDb db = (PromptDb) get("promptDb");
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(30, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
    PromptDb.vault().addLibraryFile("a.png", png.toByteArray(), "Imported", 3);
    PromptDb.vault().addLibraryFile("note.txt", "text".getBytes(StandardCharsets.UTF_8), "Imported", 3);
    PromptDb.vault().addLibraryFile("b.png", png.toByteArray(), "Imported", 3);
    final StringBuilder seen = new StringBuilder();
    final boolean[] done = {false};
    inspectNext = gallery -> {
      seen.append("c.png listed: ").append(component(gallery, "gallery:c.png") != null).append('\n');
      try {
        PromptDb.vault().addLibraryFile("c.png", png.toByteArray(), "Imported", 3);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      inspectNext = again -> {
        seen.append("opened again: c.png listed ").append(component(again, "gallery:c.png") != null).append('\n');
        StringBuilder order = new StringBuilder();
        for (PromptVault.StoredFile f : db.galleryList) order.append(f.name).append(' ');
        seen.append("order: ").append(order.toString().trim()).append('\n');
        PromptVault.StoredFile first = db.galleryList.get(0);
        StringBuilder steps = new StringBuilder(first.name);
        PromptVault.StoredFile at = first;
        for (int i = 0; i < 3; i++) {
          Object[] next = db.stepPicture(at, 1);
          if (next == null) {
            steps.append(" > (last)");
            break;
          }
          at = (PromptVault.StoredFile) next[0];
          steps.append(" > ").append(at.name);
        }
        Object[] prev = db.stepPicture(at, -1);
        steps.append(" < ").append(prev == null ? "(first)" : ((PromptVault.StoredFile) prev[0]).name);
        seen.append("next/previous: ").append(steps).append('\n');
        // Preview from the gallery shows Previous and Next.
        final PromptVault.StoredFile shown = first;
        inspectNext = preview -> {
          seen.append("preview ").append(preview.getTitle()).append(": Previous ").append(component(preview, "preview-prev") != null)
              .append(", Next ").append(component(preview, "preview-next") != null).append('\n');
          done[0] = true;
        };
        SwingUtilities.invokeLater(() -> db.preview(shown, (javax.swing.JButton) component(again, "gallery:" + shown.name)));
      };
      if (!db.refreshGallery(gallery, true)) seen.append("not opened again\n");
    };
    SwingUtilities.invokeLater(() -> db.gallery(true));
    for (int i = 0; i < 150 && !done[0]; i++) Thread.sleep(100);
    idle();
    out.append(seen);
    if (seen.indexOf("opened again: c.png listed true") < 0 || seen.indexOf("note.txt", seen.indexOf("next/previous")) >= 0 || seen.indexOf("Next true") < 0)
      throw new AssertionError(seen.toString());
  }

  /**
   * PyJav's headlines (Code or Prompt above the editor, Output), Params' Preview of a picked
   * picture, and Upscale/Resize image in the Ref/Result files menu, also for a picture whose
   * name has no picture extension.
   */
  void s96_headlines_preview_upscale() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File me = new File(home, "me");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(640, 480, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", me);
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniPedit.java");
    idle();
    for (String t : new String[] {"pyjav-editor-caption", "pyjav-output-head"}) {
      JLabel head = (JLabel) component(frame.getContentPane(), t);
      if (head == null) throw new AssertionError("no " + t);
      out.append(t).append(": ").append(head.getText()).append('\n');
    }
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--prompt Hi --image \"" + me.getAbsolutePath() + "\""));
    final StringBuilder seen = new StringBuilder();
    final Object pyJav = get("pyJav");
    inspectNext = d -> {
      javax.swing.JButton preview = (javax.swing.JButton) component(d, "params-preview:--image");
      javax.swing.JButton preview2 = (javax.swing.JButton) component(d, "params-preview:--image2");
      if (preview == null || preview2 == null) throw new AssertionError("no Preview button");
      seen.append("Preview --image enabled ").append(preview.isEnabled()).append(", --image2 (none picked) ").append(preview2.isEnabled()).append('\n');
      seen.append("no Preview on --prompt: ").append(component(d, "params-preview:--prompt") == null).append('\n');
      preview.doClick();
      try {
        javax.swing.JDialog shown = (javax.swing.JDialog) pyJav.getClass().getDeclaredField("lastParamsPreview").get(pyJav);
        seen.append("preview: ").append(shown == null ? "none" : shown.getTitle() + ", image " + (component(shown.getContentPane(), "params-preview-image") != null)).append('\n');
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      ((javax.swing.JButton) component(d, "params-clear:--image")).doClick();
      seen.append("after Clear, Preview enabled ").append(preview.isEnabled()).append('\n');
    };
    answers.add("OK");
    call("openParams");
    idle();
    Thread.sleep(500);
    idle();
    out.append(seen);
    if (seen.indexOf("Preview --image enabled true, --image2 (none picked) false") < 0 || seen.indexOf("image true") < 0 || seen.indexOf("after Clear, Preview enabled false") < 0)
      throw new AssertionError("Preview: " + seen);
    PromptDb db = (PromptDb) get("promptDb");
    byte[] png = java.nio.file.Files.readAllBytes(me.toPath());
    PromptDb.vault().addLibraryFile("suit.png", png, "Imported", 1);
    PromptDb.vault().addLibraryFile("picture", png, "Imported", 3);
    PromptDb.vault().addLibraryFile("notes.txt", "Kick on 1.\n".getBytes(StandardCharsets.UTF_8), "Imported", 3);
    for (String n : new String[] {"suit.png", "picture", "notes.txt"}) {
      final boolean results = !"suit.png".equals(n);
      PromptVault.StoredFile file = null;
      for (PromptVault.StoredFile f : PromptDb.stored(results)) if (f.name.equals(n)) file = f;
      if (file == null) throw new AssertionError("not stored: " + n);
      final PromptVault.StoredFile at = file;
      final StringBuilder items = new StringBuilder();
      final boolean[] done = {false};
      inspectNext = gallery -> {
        javax.swing.JButton card = (javax.swing.JButton) component(gallery, "gallery:" + n);
        db.menu(at, card, () -> {});
        for (java.awt.Component c : db.lastFileMenu.getComponents()) {
          if (c instanceof javax.swing.JMenuItem) items.append('[').append(((javax.swing.JMenuItem) c).getText()).append(']');
        }
        db.lastFileMenu.setVisible(false);
        done[0] = true;
      };
      SwingUtilities.invokeLater(() -> db.gallery(results));
      for (int i = 0; i < 100 && !done[0]; i++) Thread.sleep(100);
      idle();
      out.append(n).append(results ? " (result)" : " (reference)").append(" menu: ").append(items).append('\n');
      boolean upscale = items.indexOf("[Upscale/Resize image]") >= 0;
      if (upscale == "notes.txt".equals(n)) throw new AssertionError("Upscale item on " + n + ": " + items);
    }
  }

  /** Params file rows have Clear: SogniPedit's second reference picked by mistake leaves the run, the first stays. */
  void s81_params_clear_file() throws Exception {
    File home = new File(System.getProperty("user.home"));
    File me = new File(home, "me.png");
    File suit = new File(home, "suit.png");
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", me);
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", suit);
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniPedit.java");
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("--prompt Hi --image \"" + me.getAbsolutePath() + "\" --image2 \"" + suit.getAbsolutePath() + "\""));
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> {
      for (String t : new String[] {"--image", "--image2"}) {
        javax.swing.JButton clear = (javax.swing.JButton) component(d, "params-clear:" + t);
        seen.append(t).append(": ").append(((JLabel) component(d, "params-chosen:" + t)).getText()).append(", button ").append(clear == null ? "none" : clear.getText()).append('\n');
      }
      ((javax.swing.JButton) component(d, "params-clear:--image2")).doClick();
      seen.append("after Clear: ").append(((JLabel) component(d, "params-chosen:--image2")).getText()).append('\n');
    };
    answers.add("OK");
    call("openParams");
    idle();
    out.append(seen);
    out.append("args: ").append(((javax.swing.text.JTextComponent) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
  }

  void s80_browse_import_saverun() throws Exception {
    File home = new File(System.getProperty("user.home"));
    PromptVault vault = PromptDb.vault();
    long image = 0;
    for (PromptVault.Category c : vault.mains()) if (c.name.equals("Image")) image = c.id;
    long faces = vault.addSubcategory(image, "Faces");
    long smile = vault.addPrompt(faces, "Smile");
    vault.addVersion(smile, "Smile", "", "A smiling face, soft light", "", "", null, "", null, "", null, "ai", "");
    call("showView", "py");
    call("selectListedProgram", "Java", "SogniTextVideo.java");
    idle();
    final StringBuilder seen = new StringBuilder();
    inspectNext = d -> seen.append("Params: Browse prompts ").append(component(d, "params-prompt-browse:--prompt") != null ? "shown" : "missing").append('\n');
    answers.add("Cancel");
    call("openParams");
    for (int i = 0; i < 50 && seen.length() == 0; i++) Thread.sleep(100);
    idle();
    // The browser itself, as Browse prompts opens it.
    final String[] got = new String[1];
    inspectNext = b -> {
      ((javax.swing.AbstractButton) component(b, "prompt-browser-tab:Image")).doClick();
      seen.append("Image tab: ").append(component(b, "prompt-browser-sub:Faces") != null ? "subcategory Faces" : "no Faces").append('\n');
      ((javax.swing.AbstractButton) component(b, "prompt-browser-sub:Faces")).doClick();
      ((javax.swing.AbstractButton) component(b, "prompt-browser-item:Smile")).doClick();
    };
    answers.add("Cancel");
    SwingUtilities.invokeLater(() -> PromptBrowser.show((Pulsekit) frame, (title, text) -> got[0] = title + ": " + text));
    for (int i = 0; i < 50 && got[0] == null; i++) Thread.sleep(100);
    idle();
    seen.append("picked: ").append(got[0]).append('\n');
    out.append(seen);
    // Import: the files a sheet names are found beside it.
    File dir = new File(home, "to-import");
    dir.mkdirs();
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(30, 20, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
    Files.write(new File(dir, "face.png").toPath(), png.toByteArray());
    Files.write(new File(dir, "out.png").toPath(), png.toByteArray());
    Files.write(new File(dir, "Portrait.prompt").toPath(), PromptRun.encode("Portrait", "", "", "face.png", "", "Image", "v2 \u00b7 FINAL", "Sogni Krea", "out.png", "ai", "Draw a portrait").getBytes(StandardCharsets.UTF_8));
    Files.write(new File(dir, "Portrait 2.prompt").toPath(), PromptRun.encode("Portrait 2", "", "", "face.png", "", "Image", "", "", "", "ai", "Another portrait").getBytes(StandardCharsets.UTF_8));
    call("showView", "prompts");
    idle();
    PromptsPage page = (PromptsPage) get("promptsPage");
    for (String n : new String[] {"Portrait.prompt", "Portrait.prompt", "Portrait 2.prompt"}) {
      page.importNext = new File[] {new File(dir, n)};
      answers.add("OK");
      edt(() -> ((javax.swing.AbstractButton) component(frame, "prompts-import")).doClick());
      Thread.sleep(300);
      idle();
      out.append("import ").append(n).append(": ").append(page.lastImport).append('\n');
    }
    // Save this run into DB: a real ImageUpscaler run.
    call("showView", "py");
    call("selectListedProgram", "Java", "ImageUpscaler.java");
    idle();
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    edt(() -> log.setText(""));
    edt(() -> ((javax.swing.text.JTextComponent) get("pyExtra")).setText("\"" + new File(dir, "face.png").getAbsolutePath() + "\" --scale 3 " + PromptKeep.SAVE_RUN));
    answers.add("Close");
    edt(() -> call("runPython"));
    for (int i = 0; i < 300 && !(textOf(log).contains("Succeeded") || textOf(log).contains("Failed")); i++) Thread.sleep(50);
    Thread.sleep(800);
    idle();
    for (String line : textOf(log).split("\n")) {
      if (line.matches("(\\$ |Succeeded|Failed|Prompt library|Unknown).*")) out.append("log: ").append(line.replace(dir.getAbsolutePath(), "<dir>")).append('\n');
    }
    for (PromptVault.Category c : vault.categories()) {
      if (!c.name.equals("Programs")) continue;
      for (PromptVault.Prompt p : vault.prompts(c.id)) {
        PromptVault.Version v = vault.finalVersion(p.id);
        out.append("DB: Programs / ").append(p.title).append(": text \"").append(v.body).append("\", reference ").append(v.ref1Name)
            .append(", result ").append(v.resultName).append(" (").append(vault.sizeOf(v, 3) > 0 ? "stored" : "none").append(")\n");
      }
    }
  }

  private static JTextField textFieldIn(java.awt.Component c) {
    if (c instanceof JTextField) return (JTextField) c;
    if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
      JTextField f = textFieldIn(k);
      if (f != null) return f;
    }
    return null;
  }

  private static javax.swing.JScrollPane scrollIn(java.awt.Component c) {
    if (c instanceof javax.swing.JScrollPane) return (javax.swing.JScrollPane) c;
    if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
      javax.swing.JScrollPane f = scrollIn(k);
      if (f != null) return f;
    }
    return null;
  }

  private static java.awt.Component component(java.awt.Component c, String name) {
    if (name.equals(c.getName())) return c;
    if (c instanceof java.awt.Container) {
      for (java.awt.Component k : ((java.awt.Container) c).getComponents()) {
        java.awt.Component found = component(k, name);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static boolean named(java.awt.Component c, String name) {
    if (name.equals(c.getName())) return true;
    if (c instanceof java.awt.Container) {
      for (java.awt.Component k : ((java.awt.Container) c).getComponents()) if (named(k, name)) return true;
    }
    return false;
  }

  /** The text fields in a container (an input dialog's). */
  private static List<javax.swing.JTextField> fields(java.awt.Container c) {
    List<javax.swing.JTextField> out = new ArrayList<javax.swing.JTextField>();
    for (Component k : c.getComponents()) {
      if (k instanceof javax.swing.JTextField) out.add((javax.swing.JTextField) k);
      else if (k instanceof java.awt.Container) out.addAll(fields((java.awt.Container) k));
    }
    return out;
  }

  private static List<Engine.Part> songParts() {
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 110, 1));
    parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get("funk")), 4));
    parts.add(Engine.fill("snare", 110, 1));
    return parts;
  }

  // ------------------------------------------------------------ app driving

  private void start() throws Exception {
    Thread.setDefaultUncaughtExceptionHandler((th, e) -> {
      e.printStackTrace();
      if (!jdk8PushedQueue(e)) errors.add(e);
    });
    Thread watcher = new Thread(this::watchDialogs, "dialog-watcher");
    watcher.setDaemon(true);
    watcher.start();
    Pulsekit.main(new String[0]);
    for (int i = 0; i < 100 && frame == null; i++) {
      Thread.sleep(100);
      for (Window w : Window.getWindows()) {
        if (w instanceof JFrame && w.isShowing()) frame = (JFrame) w;
      }
    }
    if (frame == null) throw new IllegalStateException("no window");
    Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
      @Override
      protected void dispatchEvent(AWTEvent e) {
        try {
          super.dispatchEvent(e);
        } catch (Throwable t) {
          // Java 8's own EventQueue fails this way on a focus change while a queue is pushed (this
          // one, the tests' own): not Pulsekit's, so not counted.
          if (jdk8PushedQueue(t)) return;
          errors.add(t);
          t.printStackTrace();
        }
      }
    });
    idle();
    synchronized (dialogs) {
      for (String d : dialogs) out.append("startup ").append(d).append('\n');
      dialogs.clear();
    }
  }

  /** Answers dialogs: the next queued answer, or OK / Yes. Records what each dialog said. */
  private void watchDialogs() {
    java.util.Set<Window> seen = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Window, Boolean>());
    while (true) {
      try {
        Thread.sleep(80);
        for (Window w : Window.getWindows()) {
          if (!(w instanceof JDialog) || !w.isShowing() || seen.contains(w)) continue;
          seen.add(w);
          JDialog d = (JDialog) w;
          // A dialog that closes by itself (a progress note) can be gone before it is read: it is left alone.
          if (d.getRootPane() == null || !d.isDisplayable()) continue;
          final javax.swing.JFileChooser chooser = chooserIn(d.getContentPane());
          if (chooser != null && chooseNext != null) {
            final File pick = chooseNext;
            chooseNext = null;
            dialogs.add("dialog \"" + d.getTitle() + "\": chose " + pick.getName());
            SwingUtilities.invokeAndWait(() -> {
              chooser.setSelectedFile(pick);
              chooser.approveSelection();
            });
            continue;
          }
          final java.util.function.Consumer<JDialog> inspect = inspectNext;
          if (inspect != null) {
            inspectNext = null;
            SwingUtilities.invokeAndWait(() -> inspect.accept(d));
          }
          StringBuilder text = new StringBuilder("dialog \"" + d.getTitle() + "\":");
          try {
            for (String s : texts(d.getContentPane())) text.append(" [").append(s).append(']');
          } catch (NullPointerException closed) {
            if (d.getRootPane() == null || !d.isDisplayable()) continue;
            throw closed;
          }
          dialogs.add(text.toString());
          List<String> want = new ArrayList<String>();
          // "(keep)": the dialog stays open for the scenario to work in.
          synchronized (answers) {
            if ("(keep)".equals(answers.peek())) {
              answers.poll();
              continue;
            }
          }
          synchronized (answers) {
            while (!answers.isEmpty()) {
              String a = answers.peek();
              if (find(d.getContentPane(), a) == null) break;
              answers.poll();
              want.add(a);
              // a reset button (or New chat) keeps the dialog open: the next answer belongs to it too
              if (!a.startsWith("Reset") && !a.equals("New chat") && !a.equals("Browse")) break;
            }
          }
          if (want.isEmpty()) want.add(find(d.getContentPane(), "OK") != null ? "OK" : "Yes");
          for (String a : want) {
            AbstractButton b = find(d.getContentPane(), a);
            if (b == null) {
              dialogs.add("  no button " + a);
              SwingUtilities.invokeLater(d::dispose);
            } else {
              dialogs.add("  pressed " + a);
              // Browse opens a folder chooser of its own: pressed without waiting for it.
              if (a.equals("Browse")) SwingUtilities.invokeLater(b::doClick);
              else SwingUtilities.invokeAndWait(b::doClick);
            }
          }
        }
      } catch (Throwable e) {
        errors.add(e);
      }
    }
  }

  interface Action {
    void run() throws Exception;
  }

  private void edt(Action a) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) {
      a.run();
      return;
    }
    Throwable[] err = new Throwable[1];
    SwingUtilities.invokeAndWait(() -> {
      try {
        a.run();
      } catch (Throwable t) {
        err[0] = t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t;
      }
    });
    if (err[0] instanceof Exception) throw (Exception) err[0];
    if (err[0] != null) throw new RuntimeException(err[0]);
    idle();
  }

  private void idle() throws Exception {
    for (int i = 0; i < 3; i++) {
      Thread.sleep(120);
      SwingUtilities.invokeAndWait(() -> {});
    }
    synchronized (dialogs) {
      for (String d : dialogs) out.append(d).append('\n');
      dialogs.clear();
    }
  }

  private List<Object> owners() {
    List<Object> list = new ArrayList<Object>();
    list.add(frame);
    for (Class<?> c = frame.getClass(); c != null && c.getName().startsWith("pulsekit."); c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers())) continue;
        if (!f.getType().getName().startsWith("pulsekit.") || f.getType().getName().startsWith("pulsekit.Engine")) continue;
        try {
          f.setAccessible(true);
          Object v = f.get(frame);
          if (v != null && !list.contains(v)) list.add(v);
        } catch (Exception ignored) {
          // not a feature object
        }
      }
    }
    return list;
  }

  private Object call(String name, Object... args) throws Exception {
    Object[] result = new Object[1];
    edt(() -> {
      for (Object o : owners()) {
        for (Class<?> c = o.getClass(); c != null && c.getName().startsWith("pulsekit."); c = c.getSuperclass()) {
          for (Method m : c.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length || !fits(m, args)) continue;
            m.setAccessible(true);
            result[0] = m.invoke(o, args);
            return;
          }
        }
      }
      throw new NoSuchMethodException(name + "/" + args.length);
    });
    return result[0];
  }

  private static boolean fits(Method m, Object[] args) {
    Class<?>[] p = m.getParameterTypes();
    for (int i = 0; i < p.length; i++) {
      if (args[i] == null) continue;
      Class<?> want = p[i].isPrimitive() ? box(p[i]) : p[i];
      if (!want.isInstance(args[i])) return false;
    }
    return true;
  }

  private static Class<?> box(Class<?> c) {
    if (c == int.class) return Integer.class;
    if (c == boolean.class) return Boolean.class;
    if (c == long.class) return Long.class;
    if (c == double.class) return Double.class;
    if (c == float.class) return Float.class;
    return c;
  }

  private Field field(String name) throws Exception {
    for (Object o : owners()) {
      for (Class<?> c = o.getClass(); c != null && c.getName().startsWith("pulsekit."); c = c.getSuperclass()) {
        try {
          Field f = c.getDeclaredField(name);
          f.setAccessible(true);
          return f;
        } catch (NoSuchFieldException ignored) {
          // look further
        }
      }
    }
    throw new NoSuchFieldException(name);
  }

  private Object ownerOf(Field f) {
    for (Object o : owners()) if (f.getDeclaringClass().isInstance(o)) return o;
    return null;
  }

  private Object get(String name) throws Exception {
    Field f = field(name);
    return f.get(ownerOf(f));
  }

  private void set(String name, Object value) throws Exception {
    Field f = field(name);
    f.set(ownerOf(f), value);
  }

  private void click(String text) throws Exception {
    edt(() -> {
      AbstractButton b = find(frame.getContentPane(), text);
      if (b == null) throw new IllegalStateException("no button " + text);
      b.doClick();
    });
  }

  private static AbstractButton find(Container root, String text) {
    for (Component c : root.getComponents()) {
      if (c instanceof AbstractButton && text.equals(((AbstractButton) c).getText()) && c.isShowing()) return (AbstractButton) c;
      if (c instanceof Container) {
        AbstractButton b = find((Container) c, text);
        if (b != null) return b;
      }
    }
    return null;
  }

  private static List<String> texts(Container root) {
    List<String> list = new ArrayList<String>();
    for (Component c : root.getComponents()) {
      String t = textOf(c);
      if (t != null && !t.isEmpty() && !(c instanceof AbstractButton)) list.add(t);
      if (c instanceof Container) list.addAll(texts((Container) c));
    }
    return list;
  }

  // ------------------------------------------------------------ snapshots

  private void snap(String title) throws Exception {
    state(title);
    StringBuilder tree = new StringBuilder();
    SwingUtilities.invokeAndWait(() -> tree(frame.getContentPane(), 0, tree));
    out.append(tree);
  }

  private void state(String title) throws Exception {
    idle();
    out.append("### ").append(title).append('\n');
    TreeMap<String, String> lines = new TreeMap<String, String>();
    SwingUtilities.invokeAndWait(() -> {
      for (Object o : owners()) {
        for (Class<?> c = o.getClass(); c != null && c.getName().startsWith("pulsekit."); c = c.getSuperclass()) {
          for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
              f.setAccessible(true);
              String v = describe(f.get(o), 0);
              if (v != null) lines.put(f.getName(), v);
            } catch (Exception ignored) {
              // unreadable
            }
          }
        }
      }
    });
    for (Map.Entry<String, String> e : lines.entrySet()) out.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
    out.append("settings ").append(MidiImportSettings.encode().replace('\n', ' ')).append('\n');
    out.append("fileSetSongs ").append(Engine.fileSetSongs.keySet()).append('\n');
  }

  private static String describe(Object v, int depth) {
    if (v == null) return "null";
    if (v instanceof String || v instanceof Number || v instanceof Boolean || v instanceof Character) return String.valueOf(v);
    if (v instanceof JTextComponent) return "text:" + clip(((JTextComponent) v).getText());
    if (v instanceof int[][]) return sig((int[][]) v);
    if (v instanceof boolean[] || v instanceof int[]) {
      StringBuilder sb = new StringBuilder();
      int n = Array.getLength(v);
      for (int i = 0; i < n; i++) sb.append(String.valueOf(Array.get(v, i)).charAt(0));
      return sb.toString();
    }
    if (v instanceof Engine.Learned) {
      Engine.Learned l = (Engine.Learned) v;
      return "learned " + l.name + " src=" + l.source + " bpm=" + l.bpm + " closest=" + l.closest + " " + sig(l.cells);
    }
    if (v instanceof Engine.LearnedFill) {
      Engine.LearnedFill f = (Engine.LearnedFill) v;
      return "fill " + f.name + " kind=" + f.kind + " src=" + f.source + " " + sig(f.cells);
    }
    if (v instanceof Engine.Part) {
      Engine.Part p = (Engine.Part) v;
      return p.kind + ":" + p.name + " x" + p.repeats + " bpm=" + p.bpm + " steps=" + p.steps + " " + sig(p.cells);
    }
    if (v instanceof Engine.ImportedSong) {
      Engine.ImportedSong s = (Engine.ImportedSong) v;
      return "song " + s.name + " set=" + s.fileSet + "/" + s.fileSetSong + " " + describe(s.parts, depth + 1);
    }
    if (depth > 2) return null;
    if (v instanceof Collection) {
      List<String> items = new ArrayList<String>();
      for (Object o : (Collection<?>) v) {
        String d = describe(o, depth + 1);
        if (d == null) return null;
        items.add(d);
      }
      return items.toString();
    }
    if (v instanceof Map) {
      List<String> items = new ArrayList<String>();
      for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
        String d = describe(e.getValue(), depth + 1);
        if (d == null) return null;
        items.add(e.getKey() + "=" + d);
      }
      return items.toString();
    }
    return null;
  }

  private static String sig(int[][] cells) {
    if (cells == null) return "-";
    StringBuilder sb = new StringBuilder();
    for (int[] row : cells) {
      for (int x : row) sb.append(x == 0 ? '.' : (x >= 110 ? 'X' : (x >= 70 ? 'x' : 'o')));
      sb.append('|');
    }
    return sb.toString();
  }

  private static String clip(String s) {
    if (s == null) return "";
    s = s.replace('\n', '/');
    return s.length() > 160 ? s.substring(0, 160) + "…" : s;
  }

  private static String textOf(Component c) {
    if (c instanceof AbstractButton) return ((AbstractButton) c).getText();
    if (c instanceof JLabel) return ((JLabel) c).getText();
    if (c instanceof JTextComponent) return ((JTextComponent) c).getText();
    if (c instanceof JOptionPane) return String.valueOf(((JOptionPane) c).getMessage());
    return null;
  }

  private static void tree(Component c, int depth, StringBuilder sb) {
    if (c instanceof javax.swing.JScrollBar || c instanceof javax.swing.CellRendererPane) return;
    Class<?> k = c.getClass();
    while (k.isAnonymousClass() || k.getSimpleName().isEmpty()) k = k.getSuperclass();
    for (int i = 0; i < depth; i++) sb.append("  ");
    sb.append(k.getSimpleName());
    if (!c.isVisible()) sb.append(" HIDDEN");
    if (c instanceof JComponent) {
      for (String key : new String[] {"role", "lane", "style", "fill", "tab", "learned", "variated", "plugin"}) {
        Object p = ((JComponent) c).getClientProperty(key);
        if (p != null) sb.append(" ").append(key).append('=').append(p);
      }
    }
    String t = textOf(c);
    if (t != null && !t.isEmpty()) sb.append(" \"").append(clip(t)).append('"');
    sb.append('\n');
    if (c instanceof Container && !(c instanceof javax.swing.JComboBox)) {
      for (Component child : ((Container) c).getComponents()) tree(child, depth + 1, sb);
    }
  }

  /** Learned ids are times: number them in order of appearance. */
  private static String normalize(String s) {
    Matcher m = Pattern.compile("\\bc[0-9a-z]{8,10}\\b").matcher(s);
    Map<String, String> ids = new LinkedHashMap<String, String>();
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String id = ids.get(m.group());
      if (id == null) {
        id = "ID" + (ids.size() + 1);
        ids.put(m.group(), id);
      }
      m.appendReplacement(sb, id);
    }
    m.appendTail(sb);
    return sb.toString().replaceAll("\\d+:\\d\\d / \\d+:\\d\\d", "MM:SS / MM:SS");
  }
}
