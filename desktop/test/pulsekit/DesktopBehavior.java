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
  private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<Throwable>());
  private final StringBuilder out = new StringBuilder();

  public static void main(String[] args) throws Exception {
    DesktopBehavior t = new DesktopBehavior();
    int code = 0;
    try {
      t.start();
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
    edt(() -> ((JTextField) get("pyExtra")).setText("in.wav out.mid --sens 2.0"));
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
    edt(() -> ((JTextField) get("pyExtra")).setText("/tmp/a.wav /tmp/d.mid"));
    answers.add("OK");
    call("openParams");
    out.append("CompareHits args: ").append(((JTextField) get("pyExtra")).getText()).append('\n');
    call("selectListedProgram", "Python", "drum_midi.py");
    edt(() -> ((JTextField) get("pyExtra")).setText("--bpm 100"));
    answers.add("OK");
    call("openParams");
    out.append("drum_midi.py args: ").append(((JTextField) get("pyExtra")).getText()).append('\n');
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
    edt(() -> ((JTextField) get("pyExtra")).setText(((JTextField) get("pyExtra")).getText() + " --split_time 0:01"));
    out.append("args: ").append(((JTextField) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (int i = 0; i < 400 && !(log.getText().contains("Succeeded") || log.getText().contains("Failed")); i++) Thread.sleep(50);
    for (String line : log.getText().split("\n")) {
      if (line.startsWith("Wrote") || line.startsWith("Succeeded") || line.startsWith("Failed")) out.append(line.replace(home.getAbsolutePath(), "~")).append('\n');
    }
    File cut = new File(home, "take one_cutted.wav");
    AudioIo.Pcm back = cut.isFile() ? AudioIo.parseWav(Files.readAllBytes(cut.toPath())) : null;
    out.append("cut file: ").append(back == null ? "none" : String.format("%.3f s", back.samples.length / (double) back.sr)).append('\n');
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
    edt(() -> ((JTextField) get("pyExtra")).setText(((JTextField) get("pyExtra")).getText() + " --split_time 0:01"));
    out.append("args: ").append(((JTextField) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (int i = 0; i < 400 && !(log.getText().contains("Succeeded") || log.getText().contains("Failed")); i++) Thread.sleep(50);
    for (String line : log.getText().split("\n")) {
      if (line.startsWith("Split") || line.startsWith("Wrote") || line.startsWith("Succeeded") || line.startsWith("Failed")) out.append(line).append('\n');
    }
    File a = new File(home, "take two_split1.wav");
    File b = new File(home, "take two_split2.wav");
    if (a.isFile() && b.isFile()) {
      AudioIo.Pcm pa = AudioIo.parseWav(Files.readAllBytes(a.toPath()));
      AudioIo.Pcm pb = AudioIo.parseWav(Files.readAllBytes(b.toPath()));
      out.append(String.format("parts: %.3f s + %.3f s%n", pa.samples.length / (double) pa.sr, pb.samples.length / (double) pb.sr));
    } else {
      out.append("parts: missing\n");
    }
  }

  void s28_program_switch_clears_args() throws Exception {
    File home = new File(System.getProperty("user.home"));
    call("showView", "py");
    call("selectListedProgram", "Java", "DrumMidi_CRT.java");
    edt(() -> ((JTextField) get("pyExtra")).setText("<input.wav> " + home.getAbsolutePath() + "/.pulsekit/output.mid"));
    call("selectListedProgram", "Java", "CutWav.java");
    out.append("CutWav args: ").append(((JTextField) get("pyExtra")).getText().replace(home.getAbsolutePath(), "~")).append('\n');
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
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
      edt(() -> ((JTextField) get("pyExtra")).setText("--prompt \"funk groove\" --drums_only --duration 10 --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
      edt(() -> call("runPython"));
      javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
      for (int i = 0; i < 600 && !(log.getText().contains("Saved") || log.getText().contains("Failed") || log.getText().contains("Could not")); i++) Thread.sleep(50);
      for (String line : log.getText().split("\n")) {
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
    edt(() -> ((JTextField) get("pyExtra")).setText("--drums_only --saveprompt --key_file \"" + new File(home, "none.txt").getAbsolutePath() + "\""));
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    // Pulsekit's own "Saved ..." line comes after the program's output, so wait for it.
    for (int i = 0; i < 400 && !(log.getText().contains("Saved " + home.getAbsolutePath()) || log.getText().contains("Could not save")); i++) Thread.sleep(50);
    for (String line : log.getText().split("\n")) {
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
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
        // A chat run started earlier (one that timed out here) is followed again by its id.
        "--run run2",
        "--models",
        "--max_tokens lots",
      };
      for (String extra : runs) {
        edt(() -> log.setText(""));
        edt(() -> ((JTextField) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        // A run that makes a picture shows it; Close the dialog.
        if (extra.contains("--run") || extra.contains("--unlimited")) answers.add("Close");
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(log.getText().contains("Succeeded") || log.getText().contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(300);
        out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
        for (String line : log.getText().split("\n")) {
          if (line.startsWith("$ ") || line.startsWith("Picked up") || line.trim().isEmpty()) continue;
          out.append("  ").append(line.replace(home.getAbsolutePath(), "~")).append('\n');
        }
      }
      synchronized (seen) {
        for (String r : seen) out.append("request: ").append(r.replace(String.valueOf(port), "PORT").replace(home.getAbsolutePath(), "~")).append('\n');
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
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
        edt(() -> ((JTextField) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(log.getText().contains("Succeeded") || log.getText().contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(300);
        out.append("== ").append(extra.replace(home.getAbsolutePath(), "~")).append('\n');
        for (String line : log.getText().split("\n")) {
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
    edt(() -> ((JTextField) get("pyExtra")).setText("--style \"Hard Rock\" --bars 4"));
    edt(() -> call("runPython"));
    javax.swing.JTextArea log = (javax.swing.JTextArea) get("pyLog");
    for (int i = 0; i < 600 && !(log.getText().contains("Wrote") || log.getText().contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : log.getText().split("\n")) {
      if (line.startsWith("Wrote") || line.startsWith("Import") || line.startsWith("$ java") || line.contains("rror")) out.append("log: ").append(line).append('\n');
    }
    // A tempo outside the style's range in the style database is moved into it.
    answers.add("Yes");
    // The MIDI ready dialog that follows (the groove with the kit's sounds): Close.
    answers.add("Close");
    edt(() -> log.setText(""));
    edt(() -> ((JTextField) get("pyExtra")).setText("--style Techno --tempo 90 --bars 4"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(log.getText().contains("Wrote") || log.getText().contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : log.getText().split("\n")) {
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
    edt(() -> ((JTextField) get("pyExtra")).setText("--style Rock --bars 4"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(log.getText().contains("Wrote") || log.getText().contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : log.getText().split("\n")) {
      if (line.startsWith("$ java") || line.startsWith("Wrote")) out.append("log: ").append(line).append('\n');
    }
    set("tsNum", 4);
    // A style in another meter in the style database plays in it.
    answers.add("Yes");
    // The MIDI ready dialog that follows (the groove with the kit's sounds): Close.
    answers.add("Close");
    edt(() -> log.setText(""));
    edt(() -> ((JTextField) get("pyExtra")).setText("--style \"Ballad 6/8\" --bars 4"));
    edt(() -> call("runPython"));
    for (int i = 0; i < 600 && !(log.getText().contains("Wrote") || log.getText().contains("rror")); i++) Thread.sleep(50);
    Thread.sleep(500);
    for (String line : log.getText().split("\n")) {
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
    for (String n : new String[] {"refs-kind:refs", "refs-kind:results"}) {
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
      edt(() -> ((JTextField) get("pyExtra")).setText(extra));
      edt(() -> call("runPython"));
      for (int i = 0; i < 600 && !log.getText().contains("Prompt library") && !log.getText().contains("rror"); i++) Thread.sleep(50);
      Thread.sleep(500);
      for (String line : log.getText().split("\n")) if (line.startsWith("Prompt library") || line.contains("rror")) out.append(extra).append(": ").append(line).append('\n');
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
    String page = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    out.append("page: ").append(c.getResponseCode()).append(", title ").append(page.contains("<title>Frames \u00b7 My clip.mp4</title>")).append('\n');
    c = (java.net.HttpURLConnection) new java.net.URL(base + "video").openConnection();
    c.setRequestProperty("Range", "bytes=100-199");
    byte[] part = c.getInputStream().readAllBytes();
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
    out.append("video 0: ").append(c.getResponseCode()).append(' ').append(c.getContentType()).append(", ").append(c.getInputStream().readAllBytes().length).append(" bytes\n");
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
    edt(() -> b.apply("video-play").doClick());
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
    edt(() -> ((JTextField) get("pyExtra")).setText("--continue \"" + old.getAbsolutePath() + "\" --prompt Why?"));
    answers.add("New chat");
    answers.add("OK");
    call("openParams");
    out.append("args: ").append(((JTextField) get("pyExtra")).getText()).append('\n');
    // Without a saved chat the row reads None; New chat leaves it so.
    answers.add("OK");
    call("openParams");
    out.append("args again: ").append(((JTextField) get("pyExtra")).getText()).append('\n');
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
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
      String[] runs = {"--prompt \"Animate this cartoon\"", "--prompt \"Animate this cartoon\" --no_filter"};
      for (int r = 0; r < runs.length; r++) {
        final String extra = runs[r];
        if (r == 1) answers.add("Close");
        edt(() -> log.setText(""));
        edt(() -> ((JTextField) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        edt(() -> call("runPython"));
        for (int i = 0; i < 600 && !(log.getText().contains("Succeeded") || log.getText().contains("Failed")); i++) Thread.sleep(50);
        Thread.sleep(300);
        out.append("== ").append(extra).append('\n');
        for (String line : log.getText().split("\n")) {
          if (line.startsWith("Content filter") || line.startsWith("Failed") || line.startsWith("Succeeded") || line.startsWith("Status")) out.append("  ").append(line).append('\n');
        }
      }
      synchronized (bodies) {
        for (String b : bodies) out.append("start sends safe_content_filter: ").append(b.contains("\"safe_content_filter\":false") ? "false" : "nothing (Sogni's default: on)").append('\n');
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
    Thread.setDefaultUncaughtExceptionHandler((th, e) -> errors.add(e));
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
          errors.add(t);
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
          StringBuilder text = new StringBuilder("dialog \"" + d.getTitle() + "\":");
          try {
            for (String s : texts(d.getContentPane())) text.append(" [").append(s).append(']');
          } catch (NullPointerException closed) {
            if (d.getRootPane() == null || !d.isDisplayable()) continue;
            throw closed;
          }
          dialogs.add(text.toString());
          List<String> want = new ArrayList<String>();
          synchronized (answers) {
            while (!answers.isEmpty()) {
              String a = answers.peek();
              if (find(d.getContentPane(), a) == null) break;
              answers.poll();
              want.add(a);
              // a reset button (or New chat) keeps the dialog open: the next answer belongs to it too
              if (!a.startsWith("Reset") && !a.equals("New chat")) break;
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
              SwingUtilities.invokeAndWait(b::doClick);
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
