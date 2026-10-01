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
    edt(() -> call("makeFileSetSong", "f:passing ships v10 121 wVqTYx", "passing ships v10 121 wVqTYx"));
    Engine.ImportedSong made = songs.get(0);
    out.append("Make song: ").append(made.name).append(", file set ").append(made.fileSet).append('\n');
    Object bar = get("songLaneBar");
    out.append("song bar scrolls sideways: ").append(((java.awt.Component) bar).getParent() instanceof javax.swing.JViewport).append('\n');
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
