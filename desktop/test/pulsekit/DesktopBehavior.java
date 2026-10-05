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
              : " " + body.replaceAll("pulsekit-[0-9]+-[0-9]+-[0-9a-f]+", "JOB").replaceAll("data:image/png;base64,[A-Za-z0-9+/=]+", "data:image/png;base64,...")));
      String type = "application/json";
      String reply;
      if (path.equals("/v1/models")) {
        reply = "{\"object\":\"list\",\"data\":[{\"id\":\"qwen3.6-35b-a3b-gguf-iq4xs\"},{\"id\":\"other-llm\"}]}";
      } else if (path.equals("/v1/chat/completions") && body.contains("Draw ten kits")) {
        // Past the Unlimited Plan's fair use limit Sogni refuses the task.
        byte[] no = "{\"error\":{\"message\":\"Daily fair use limit reached\"}}".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Retry-After", "5400");
        ex.sendResponseHeaders(429, no.length);
        ex.getResponseBody().write(no);
        ex.close();
        return;
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
        "--models",
        "--max_tokens lots",
      };
      for (String extra : runs) {
        edt(() -> log.setText(""));
        edt(() -> ((JTextField) get("pyExtra")).setText(extra + " --key_file \"" + key.getAbsolutePath() + "\" --api_base http://127.0.0.1:" + port));
        // A run that makes a picture shows it; Close the dialog.
        if (extra.contains("--run_tools") || extra.contains("--unlimited")) answers.add("Close");
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
    for (String id : new String[] {"ballad68", "rock", "blues128", "hardrock"}) {
      edt(() -> lib.loadStyle(id, false));
      int steps = (Integer) get("steps");
      int[][] cells = (int[][]) get("cells");
      StringBuilder kick = new StringBuilder();
      for (int i = 0; i < steps; i++) kick.append(cells[Engine.track("kick")][i] > 0 ? 'X' : '-');
      out.append(id).append(": ").append(get("tsNum")).append('/').append(get("tsDen")).append(", ").append(steps)
          .append(" steps, kick ").append(kick).append('\n');
    }
    edt(() -> {
      JTextField num = (JTextField) get("tsNumField");
      num.setText("3");
      num.postActionEvent();
    });
    edt(() -> lib.loadStyle("rock", false));
    out.append("hand-set 3/4, then rock: ").append(get("tsNum")).append('/').append(get("tsDen")).append(", ").append(get("steps")).append(" steps\n");
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
          StringBuilder text = new StringBuilder("dialog \"" + d.getTitle() + "\":");
          for (String s : texts(d.getContentPane())) text.append(" [").append(s).append(']');
          dialogs.add(text.toString());
          List<String> want = new ArrayList<String>();
          synchronized (answers) {
            while (!answers.isEmpty()) {
              String a = answers.peek();
              if (find(d.getContentPane(), a) == null) break;
              answers.poll();
              want.add(a);
              // a reset button keeps the dialog open: the next answer belongs to it too
              if (!a.startsWith("Reset")) break;
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
