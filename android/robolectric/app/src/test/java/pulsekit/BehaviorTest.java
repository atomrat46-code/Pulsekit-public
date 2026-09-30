package pulsekit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

/**
 * Characterization tests: each scenario drives the app and writes a snapshot of
 * the view tree plus key state to build/snapshots/NAME.txt. Compare two runs
 * (e.g. before and after a refactor) with robolectric/compare.sh.
 *
 * Members are looked up by name on MainActivity and on any pulsekit object it
 * holds, so the tests keep working when code moves into feature classes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class BehaviorTest {
  private ActivityController<MainActivity> ctl;
  private MainActivity app;

  @Before
  public void boot() {
    this.ctl = Robolectric.buildActivity(MainActivity.class).setup();
    this.app = this.ctl.get();
    idle();
  }

  @After
  public void shutdown() {
    try {
      Object stop = find("stop", 0);
      if (stop != null) call("stop");
    } catch (Throwable ignored) {
      // best effort
    }
  }

  // ---------------------------------------------------------------- scenarios

  @Test
  public void s01_boot() throws Exception {
    snap("s01_boot");
  }

  @Test
  public void s02_views() throws Exception {
    StringBuilder all = new StringBuilder();
    for (String v : new String[] {"pattern", "combo", "fills", "pads", "song", "py", "import", "export", "prompts", "fsetinfo"}) {
      call("show", v);
      idle();
      all.append("### ").append(v).append('\n').append(state()).append(tree(root(), 0));
    }
    write("s02_views", all.toString());
  }

  @Test
  public void s03_grid_taps() throws Exception {
    TextView[][] grid = (TextView[][]) get("grid");
    TextView[] acc = (TextView[]) get("accCells");
    grid[0][0].performClick();
    idle();
    grid[1][4].performClick();
    grid[1][4].performClick();
    idle();
    grid[2][6].performClick();
    grid[2][6].performClick();
    grid[2][6].performClick();
    idle();
    acc[2].performClick();
    acc[5].performClick();
    idle();
    snap("s03_grid_taps");
  }

  @Test
  public void s04_play_stop() throws Exception {
    call("toggle");
    idle();
    Thread.sleep(300);
    idle();
    String playing = "playing=" + get("playing") + "\n";
    call("toggle");
    idle();
    write("s04_play_stop", playing + state());
  }

  @Test
  public void s05_import_pattern_midi() throws Exception {
    byte[] midi = Engine.encodeMidi(Engine.styleCells(Engine.styles().get("funk")), 104);
    call("ingest", midi, "Funky pattern.mid", null);
    idle();
    snap("s05_import_pattern_midi");
  }

  @Test
  public void s06_import_song_midi() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("rock")), 2));
    parts.add(Engine.fill("toms", 110, 1));
    parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get("funk")), 2));
    parts.add(Engine.fill("snare", 110, 1));
    call("ingest", Engine.encodeSongMidi(parts), "Whole song.mid", null);
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    String dialog = d == null ? "no dialog" : "dialog shown";
    if (d != null && d.isShowing()) {
      d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
      idle();
    }
    write("s06_import_song_midi", dialog + "\n" + state() + tree(root(), 0));
  }

  @Test
  public void s07_import_sng() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("Intro", 96, Engine.styleCells(Engine.styles().get("house")), 4));
    parts.add(Engine.rest(96, 2));
    call("ingest", Engine.encodeSng(parts, "My song"), "My song.sng", null);
    idle();
    snap("s07_import_sng");
  }

  @Test
  public void s08_fset_roundtrip_and_info() throws Exception {
    byte[] midi = Engine.encodeMidi(Engine.styleCells(Engine.styles().get("funk")), 104);
    call("ingest", midi, "Set pattern.mid", null);
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    String src = Engine.sourceOf(learned.get(0));
    Engine.FileSet set = Engine.collectFset(src, src, learned, (List<Engine.LearnedFill>) get("learnedFills"), (Map<String, String>) get("fillernPairs"));
    byte[] fset = Engine.encodeFset(set);
    call("ingest", fset, "Exported.fset", null);
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.Learned> after = (List<Engine.Learned>) get("learned");
    String src2 = Engine.sourceOf(after.get(0));
    call("openFileSetInfo", "f:" + src2, src2);
    idle();
    String info = tree(root(), 0);
    call("makeFileSetSong", "f:" + src2, src2);
    idle();
    write("s08_fset", state() + "--- info\n" + info + "--- after make song\n" + tree(root(), 0));
  }

  @Test
  public void s09_programs() throws Exception {
    StringBuilder out = new StringBuilder();
    call("ingest", "public class Hello { public static void main(String[] a) {} }".getBytes(StandardCharsets.UTF_8), "Hello.java", null);
    idle();
    out.append("## java\n").append(state());
    call("ingest", "console.log('hi');".getBytes(StandardCharsets.UTF_8), "hi.mjs", null);
    idle();
    out.append("## js\n").append(state());
    call("ingest", "print('x')".getBytes(StandardCharsets.UTF_8), "script.py", null);
    idle();
    out.append("## py\n").append(state());
    call("ingest", "Make a funk beat".getBytes(StandardCharsets.UTF_8), "beat.prompt", null);
    idle();
    out.append("## prompt\n").append(state()).append(tree(root(), 0));
    write("s09_programs", out.toString());
  }

  @Test
  public void s10_wav_becomes_pyjav_input() throws Exception {
    short[] pcm = new short[22050];
    for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (6000 * Math.sin(2 * Math.PI * 220 * i / 22050.0));
    call("ingest", AudioIo.encodeWav(pcm, 22050), "Take 1.wav", null);
    idle();
    call("ingest", "public class MidiDrumGen { public static void main(String[] a) {} }".getBytes(StandardCharsets.UTF_8), "MidiDrumGen.java", null);
    idle();
    snap("s10_wav_input");
  }

  @Test
  public void s11_project_roundtrip() throws Exception {
    TextView[][] grid = (TextView[][]) get("grid");
    grid[3][3].performClick();
    idle();
    byte[] prj = (byte[]) call("encodePrj");
    grid[0][0].performClick();
    grid[5][9].performClick();
    idle();
    call("loadPrj", prj);
    idle();
    snap("s11_project_roundtrip");
  }

  @Test
  public void s12_song_editing() throws Exception {
    call("show", "song");
    idle();
    for (String label : new String[] {"Pattern ×4", "Fill", "Silent", "Fillern"}) {
      TextView t = findText(root(), label);
      if (t != null) {
        t.performClick();
        idle();
      }
    }
    snap("s12_song_editing");
  }

  @Test
  public void s13_pads_and_styles() throws Exception {
    call("show", "pads");
    idle();
    TextView[] pads = (TextView[]) get("padBtns");
    pads[0].performClick();
    idle();
    call("show", "pattern");
    idle();
    call("loadStyle", "house", false);
    idle();
    snap("s13_pads_and_styles");
  }

  @Test
  public void s14_persistence_restart() throws Exception {
    byte[] midi = Engine.encodeMidi(Engine.styleCells(Engine.styles().get("rock")), 128);
    call("ingest", midi, "Keeper pattern.mid", null);
    idle();
    call("persistLearned");
    this.ctl.pause().stop().destroy();
    this.ctl = Robolectric.buildActivity(MainActivity.class).setup();
    this.app = this.ctl.get();
    idle();
    snap("s14_persistence_restart");
  }

  // ------------------------------------------------------------- snapshotting

  private void snap(String name) throws Exception {
    write(name, state() + tree(root(), 0));
  }

  private String state() throws Exception {
    StringBuilder b = new StringBuilder();
    b.append("view=").append(get("view")).append('\n');
    b.append("style=").append(get("style")).append(" fillId=").append(get("fillId")).append('\n');
    b.append("bpm=").append(call("bpm")).append(" steps=").append(get("steps"))
        .append(" ts=").append(get("tsNum")).append('/').append(get("tsDen")).append('\n');
    b.append("cells=").append(cells((int[][]) get("cells"))).append('\n');
    b.append("lens=").append(cells((int[][]) get("lens"))).append('\n');
    b.append("fillPat=").append(cells((int[][]) get("fillPat"))).append('\n');
    b.append("accents=").append(bools((boolean[]) get("accents"))).append('\n');
    b.append("mutes=").append(bools((boolean[]) get("mutes"))).append('\n');
    for (Object o : (List<?>) get("learned")) {
      Engine.Learned l = (Engine.Learned) o;
      b.append("learned ").append(l.name).append(" src=").append(l.source).append(" bpm=").append(l.bpm).append(' ').append(cells(l.cells)).append('\n');
    }
    for (Object o : (List<?>) get("learnedFills")) {
      Engine.LearnedFill l = (Engine.LearnedFill) o;
      b.append("fill ").append(l.name).append(" kind=").append(l.kind).append(" src=").append(l.source).append(' ').append(cells(l.cells)).append('\n');
    }
    b.append("fillerns=").append(((Map<?, ?>) get("fillernPairs")).size()).append('\n');
    for (Object o : (List<?>) get("song")) b.append("song ").append(part((Engine.Part) o)).append('\n');
    for (Object o : (List<?>) get("importedSongs")) {
      Engine.ImportedSong s = (Engine.ImportedSong) o;
      b.append("importedSong ").append(s.name).append(" parts=").append(s.parts.size()).append('\n');
      for (Engine.Part p : s.parts) b.append("  ").append(part(p)).append('\n');
    }
    b.append("songLane=").append(get("songLane")).append(" songMode=").append(get("songMode")).append('\n');
    b.append("pyName=").append(get("pyName")).append('\n');
    b.append("pyInput=").append(norm(String.valueOf(get("pkPyInputPath")))).append('\n');
    for (Object o : (List<?>) get("drumSets")) b.append("drumSet ").append(((Engine.DrumSet) o).name).append('\n');
    b.append("drumSetIndex=").append(get("drumSetIndex")).append('\n');
    Object now = get("now");
    b.append("now=").append(now instanceof TextView ? norm(((TextView) now).getText().toString()) : "").append('\n');
    return b.toString();
  }

  private static String part(Engine.Part p) {
    return p.kind + ":" + p.name + " x" + p.repeats + " bpm=" + p.bpm + " " + cells(p.cells);
  }

  private static String cells(int[][] c) {
    if (c == null) return "null";
    StringBuilder b = new StringBuilder();
    for (int[] row : c) {
      if (row == null) {
        b.append("_|");
        continue;
      }
      for (int v : row) b.append(v == 0 ? '.' : (v < 90 ? 'o' : (v < 120 ? 'x' : 'X')));
      b.append('|');
    }
    return b.toString();
  }

  private static String bools(boolean[] a) {
    StringBuilder b = new StringBuilder();
    for (boolean v : a) b.append(v ? '1' : '0');
    return b.toString();
  }

  private View root() {
    return this.app.getWindow().getDecorView();
  }

  /** View tree: framework class, visibility, tag and text. Independent of which class built it. */
  private static String tree(View v, int depth) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < depth; i++) b.append("  ");
    Class<?> c = v.getClass();
    while (c != null && !c.getName().startsWith("android.")) c = c.getSuperclass();
    b.append(c == null ? "?" : c.getSimpleName());
    b.append(v.getVisibility() == View.VISIBLE ? "" : v.getVisibility() == View.GONE ? " GONE" : " INVISIBLE");
    if (v.getTag() instanceof String) b.append(" #").append(v.getTag());
    if (v instanceof TextView) b.append(" \"").append(norm(((TextView) v).getText().toString()).replace("\n", "\\n")).append('"');
    b.append('\n');
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) b.append(tree(g.getChildAt(i), depth + 1));
    }
    return b.toString();
  }

  private static String norm(String s) {
    return s.replaceAll("/[^\\s\"]*?/(files|cache)/", "<dir>/");
  }

  private static TextView findText(View v, String text) {
    if (v instanceof TextView && text.equals(((TextView) v).getText().toString())) return (TextView) v;
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) {
        TextView t = findText(g.getChildAt(i), text);
        if (t != null) return t;
      }
    }
    return null;
  }

  /**
   * Learned ids are "c" + time in base 36 + one random digit, so they differ per run
   * (and can even collide within one millisecond). Replace them all with "ID".
   */
  private static String stableIds(String text) {
    return text.replaceAll("\\bc[0-9a-z]{9,10}\\b", "ID");
  }

  private void write(String name, String text) throws Exception {
    text = stableIds(text);
    File dir = new File(System.getProperty("snapshotDir", "build/snapshots"));
    dir.mkdirs();
    Files.write(new File(dir, name + ".txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
  }

  private static void idle() {
    for (int i = 0; i < 5; i++) ShadowLooper.idleMainLooper();
  }

  // --------------------------------------------------------- member lookup

  /** The activity plus every pulsekit object it holds in a field (feature classes). */
  private List<Object> owners() {
    List<Object> out = new ArrayList<>();
    IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
    collect(this.app, out, seen, 0);
    return out;
  }

  private static void collect(Object o, List<Object> out, IdentityHashMap<Object, Boolean> seen, int depth) {
    if (o == null || seen.containsKey(o) || depth > 3) return;
    seen.put(o, true);
    out.add(o);
    for (Class<?> c = o.getClass(); c != null && c.getName().startsWith("pulsekit."); c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers())) continue;
        if (!f.getType().getName().startsWith("pulsekit.") || f.getType().getName().startsWith("pulsekit.Engine")) continue;
        try {
          f.setAccessible(true);
          collect(f.get(o), out, seen, depth + 1);
        } catch (Exception ignored) {
          // skip
        }
      }
    }
  }

  private Object get(String field) throws Exception {
    for (Object o : owners()) {
      for (Class<?> c = o.getClass(); c != null && c.getName().startsWith("pulsekit."); c = c.getSuperclass()) {
        try {
          Field f = c.getDeclaredField(field);
          f.setAccessible(true);
          return f.get(o);
        } catch (NoSuchFieldException ignored) {
          // keep looking
        }
      }
    }
    throw new NoSuchFieldException(field);
  }

  private Object[] find(String name, int argc) {
    for (Object o : owners()) {
      for (Class<?> c = o.getClass(); c != null && c.getName().startsWith("pulsekit."); c = c.getSuperclass()) {
        for (Method m : c.getDeclaredMethods()) {
          if (m.getName().equals(name) && m.getParameterCount() == argc) return new Object[] {o, m};
        }
      }
    }
    return null;
  }

  private Object call(String name, Object... args) throws Exception {
    Object[] hit = find(name, args.length);
    if (hit == null) throw new NoSuchMethodException(name + "/" + args.length);
    Method m = (Method) hit[1];
    m.setAccessible(true);
    try {
      return m.invoke(hit[0], args);
    } catch (java.lang.reflect.InvocationTargetException e) {
      if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
      throw e;
    }
  }

  @SuppressWarnings("unused")
  private static int len(Object arr) {
    return Array.getLength(arr);
  }
}
