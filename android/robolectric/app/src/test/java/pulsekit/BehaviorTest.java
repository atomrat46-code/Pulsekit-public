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
    // File-set state is static; start each scenario without what an earlier one imported.
    Engine.fileSetOrigins.clear();
    Engine.fileSetAudio.clear();
    MidiImportSettings.reset();
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
    for (String v : new String[] {"pattern", "combo", "fills", "pads", "song", "py", "import", "export", "prompts", "fsetinfo", "help", "midisettings"}) {
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

  @Test
  public void s15_program_menus() throws Exception {
    call("show", "py");
    idle();
    StringBuilder out = new StringBuilder();
    for (String kind : new String[] {"Java", "Python", "Code"}) {
      out.append(kind).append(": ").append(String.join(", ", (String[]) call("list", kind))).append('\n');
    }
    String editorBefore = ((TextView) get("pyEditor")).getText().toString();
    pickFromMenu("Java \u25be", "DrumMidi_CRT.java");
    out.append("after Java pick: pyName=").append(get("pyName"))
        .append(" editorUnchanged=").append(editorBefore.equals(((TextView) get("pyEditor")).getText().toString())).append('\n');
    pickFromMenu("Code \u25be", "sogni-client.mjs");
    String editor = ((TextView) get("pyEditor")).getText().toString();
    String run = (String) call("sourceToRun");
    out.append("after Code pick: pyName=").append(get("pyName"))
        .append(" editorHasSogni=").append(editor.contains("@sogni-ai/sogni-client"))
        .append(" runsDrumMidi=").append(run != null && run.contains("public class DrumMidi_CRT")).append('\n');
    short[] pcm = new short[22050];
    call("ingest", AudioIo.encodeWav(pcm, 22050), "Song take.wav", null);
    idle();
    out.append("after WAV: pyName=").append(get("pyName")).append(" args=")
        .append(norm(((TextView) get("pkPyArgs")).getText().toString())).append('\n');
    write("s15_program_menus", out + state() + tree(root(), 0));
  }

  @Test
  public void s16_help_from_file_menu() throws Exception {
    TextView file = findText(root(), "File");
    if (file == null) throw new AssertionError("no File tab");
    file.performClick();
    idle();
    android.widget.PopupWindow pop = org.robolectric.shadows.ShadowApplication.getInstance().getLatestPopupWindow();
    TextView help = findText(pop.getContentView(), "Help-Android");
    if (help == null) throw new AssertionError("no Help-Android item");
    help.performClick();
    idle();
    TextView pyText = findText(root(), "Python, Java, JavaScript, or TypeScript. On Android, Node.js runs in Termux: "
        + "pkg install nodejs. npm installs @sogni-ai/sogni-client. Java runs in the app.");
    write("s16_help", "helpTextShown=" + (pyText != null && pyText.isShown()) + "\n" + state() + tree(root(), 0));
  }

  @Test
  public void s17_recent_after_run() throws Exception {
    call("show", "py");
    idle();
    short[] pcm = new short[22050];
    call("ingest", AudioIo.encodeWav(pcm, 22050), "Passing Ships.wav", null);
    idle();
    pickFromMenu("Java \u25be", "DrumMidi_CRT.java");
    TextView args = (TextView) get("pkPyArgs");
    String used = args.getText().toString() + " --sens 0.4 --hat 0.4";
    args.setText(used);
    TextView run = findText(root(), "Run");
    run.performClick();
    idle();
    StringBuilder out = new StringBuilder();
    @SuppressWarnings("unchecked")
    List<Object> items = (List<Object>) get("pkPyRecentItems");
    out.append("recent items=").append(items == null ? 0 : items.size()).append('\n');
    View box = root().findViewWithTag("pk-recent");
    String label = "DrumMidi_CRT.java  " + used;
    TextView entry = box == null ? null : findText(box, label);
    out.append("recent row shown=").append(entry != null).append('\n');
    // open something else, then pick the recent entry
    call("ingest", "print('x')".getBytes(StandardCharsets.UTF_8), "other.py", null);
    idle();
    if (entry != null) {
      entry.performClick();
      idle();
    }
    out.append("after tap: pyName=").append(get("pyName")).append('\n');
    out.append("after tap: args restored=").append(used.equals(((TextView) get("pkPyArgs")).getText().toString())).append('\n');
    out.append("after tap: editor has DrumMidi=").append(((TextView) get("pyEditor")).getText().toString().contains("class DrumMidi_CRT")).append('\n');
    write("s17_recent", norm(out.toString()));
  }

  @Test
  public void s18_program_midi_kept_as_written_merged() throws Exception {
    write("s18_program_midi", importStats(detectedMidi(), "Passing Ships"));
  }

  @Test
  public void s19_drum_midi_settings() throws Exception {
    TextView file = findText(root(), "File");
    file.performClick();
    idle();
    android.widget.PopupWindow pop = org.robolectric.shadows.ShadowApplication.getInstance().getLatestPopupWindow();
    TextView item = findText(pop.getContentView(), "Drum Midi Settings");
    if (item == null) throw new AssertionError("no Drum Midi Settings item");
    item.performClick();
    idle();
    StringBuilder out = new StringBuilder();
    out.append("view=").append(get("view")).append('\n');
    View page = (View) get("drumMidiPane");
    out.append("page shown=").append(page != null && page.isShown()).append('\n');
    // Merge more, and no one-off fills.
    ((android.widget.CheckBox) findText(page, "Treat a one-off bar after a repeated groove as a fill")).performClick();
    TextView plus = findText(page, "+");
    plus.performClick();
    plus.performClick();
    idle();
    out.append("settings:\n").append(MidiImportSettings.encode());
    out.append(importStats(detectedMidi(), "Detected"));
    // Merging off.
    call("show", "midisettings");
    idle();
    ((android.widget.CheckBox) findText((View) get("drumMidiPane"), "Merge hits")).performClick();
    idle();
    out.append("merge off:\n").append(importStats(detectedMidi(), "Unmerged"));
    String stored = app.getSharedPreferences("pulsekit-drum-midi", 0).getString("settings", "");
    out.append("stored=").append(stored.replace('\n', ' ')).append('\n');
    write("s19_drum_midi_settings", out.toString() + tree(root(), 0));
  }

  @Test
  public void s20_fillern_underline_only_when_picked() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("rock")), 2));
    parts.add(Engine.fill("toms", 110, 1));
    parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get("funk")), 2));
    call("ingest", Engine.encodeSongMidi(parts), "Old set.mid", null);
    idle();
    AlertDialog song = (AlertDialog) ShadowDialog.getLatestDialog();
    if (song != null && song.isShowing()) {
      song.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
      idle();
    }
    // Like an older import: every pattern already has a fill paired.
    @SuppressWarnings("unchecked")
    Map<String, String> pairs = (Map<String, String>) get("fillernPairs");
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    for (Engine.Learned l : learned) pairs.put("l:" + l.id, "toms");
    call("show", "combo");
    idle();
    StringBuilder out = new StringBuilder();
    out.append("underlined after import=").append(underlinedChips()).append('\n');
    out.append("fillern tab chips=").append(packChips()).append('\n');
    // Make a Fillern from the file set: first pattern, then a fill.
    root().findViewWithTag("fillern-add").performClick();
    idle();
    org.robolectric.Shadows.shadowOf(((AlertDialog) ShadowDialog.getLatestDialog()).getListView()).performItemClick(0);
    idle();
    out.append("list underlined before pick=").append(underlinedItems()).append('\n');
    android.widget.ListView list = ((AlertDialog) ShadowDialog.getLatestDialog()).getListView();
    int at = -1;
    for (int i = 0; i < list.getAdapter().getCount(); i++) {
      if (String.valueOf(list.getAdapter().getItem(i)).trim().equals(Engine.FILL_LABEL[1])) at = i;
    }
    if (at < 0) throw new AssertionError("no " + Engine.FILL_LABEL[1] + " in the list");
    org.robolectric.Shadows.shadowOf(list).performItemClick(at);
    idle();
    call("show", "combo");
    idle();
    TextView chip = null;
    for (Engine.Learned l : learned) {
      View v = root().findViewWithTag(l.id);
      if (v instanceof TextView && v.isShown()) { chip = (TextView) v; break; }
    }
    if (chip == null) throw new AssertionError("no Fillern chip");
    out.append("chip=").append(chip.getText()).append('\n');
    out.append("underlined after pick=").append(underlinedChips()).append('\n');
    chip = (TextView) root().findViewWithTag(chip.getTag());
    chip.performLongClick();
    idle();
    out.append("list underlined after pick=").append(underlinedItems()).append('\n');
    ((AlertDialog) ShadowDialog.getLatestDialog()).dismiss();
    call("persistLearned");
    this.ctl.pause().stop().destroy();
    this.ctl = Robolectric.buildActivity(MainActivity.class).setup();
    this.app = this.ctl.get();
    idle();
    call("show", "combo");
    idle();
    out.append("underlined after restart=").append(underlinedChips()).append(" (the set's chips start folded)\n");
    out.append("picked after restart=").append(call("fillernUnderlined", "l:" + chip.getTag())).append('\n');
    write("s20_fillern_underline", out.toString());
  }

  @Test
  public void s21_create_fillern_in_file_set() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("rock")), 2));
    parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get("funk")), 2));
    parts.add(Engine.groove("C", 110, Engine.styleCells(Engine.styles().get("house")), 2));
    call("ingest", Engine.encodeSongMidi(parts), "Three parts.mid", null);
    idle();
    AlertDialog song = (AlertDialog) ShadowDialog.getLatestDialog();
    if (song != null && song.isShowing()) {
      song.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
      idle();
    }
    StringBuilder out = new StringBuilder();
    call("show", "pattern");
    idle();
    out.append("pattern tab chips=").append(packChips()).append('\n');
    call("show", "combo");
    idle();
    out.append("fillern tab chips=").append(packChips()).append('\n');
    View none = root().findViewWithTag("fillern-none");
    out.append("no-fillerns note shown=").append(none != null && none.isShown()).append('\n');
    none.performClick();
    idle();
    AlertDialog pick = (AlertDialog) ShadowDialog.getLatestDialog();
    android.widget.ListView patterns = pick.getListView();
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < patterns.getAdapter().getCount(); i++) names.append(i == 0 ? "" : ", ").append(patterns.getAdapter().getItem(i));
    out.append("pattern list=").append(names).append('\n');
    org.robolectric.Shadows.shadowOf(patterns).performItemClick(0);
    idle();
    android.widget.ListView fills = ((AlertDialog) ShadowDialog.getLatestDialog()).getListView();
    int at = -1;
    for (int i = 0; i < fills.getAdapter().getCount(); i++) {
      if (String.valueOf(fills.getAdapter().getItem(i)).trim().equals(Engine.FILL_LABEL[1])) at = i;
    }
    org.robolectric.Shadows.shadowOf(fills).performItemClick(at);
    idle();
    out.append("after create: fillern tab chips=").append(packChips()).append('\n');
    View gone = root().findViewWithTag("fillern-none");
    out.append("no-fillerns note shown=").append(gone != null && gone.isShown()).append('\n');
    out.append("underlined=").append(underlinedChips()).append('\n');
    call("show", "pattern");
    idle();
    out.append("pattern tab chips=").append(packChips()).append('\n');
    write("s21_create_fillern", out.toString());
  }

  @Test
  public void s22_imported_list_scrolls() throws Exception {
    String[] styles = {"rock", "funk", "house", "techno", "trap", "hiphop"};
    for (int k = 0; k < styles.length; k++) {
      List<Engine.Part> parts = new ArrayList<>();
      parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get(styles[k])), 2));
      parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get(styles[(k + 1) % styles.length])), 2));
      call("ingest", Engine.encodeSongMidi(parts), "Set " + (k + 1) + ".mid", null);
      idle();
      AlertDialog song = (AlertDialog) ShadowDialog.getLatestDialog();
      if (song != null && song.isShowing()) {
        song.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        idle();
      }
    }
    call("show", "combo");
    idle();
    // Open every folded file set.
    for (int round = 0; round < 10; round++) {
      TextView folded = null;
      for (View v : allViews(root())) {
        if (v instanceof TextView && v.isShown() && "pack".equals(v.getTag()) && ((TextView) v).getText().toString().startsWith("\u25b8 ")) {
          folded = (TextView) v;
          break;
        }
      }
      if (folded == null) break;
      folded.performClick();
      idle();
    }
    StringBuilder out = new StringBuilder();
    int notes = 0;
    for (View v : allViews(root())) if ("fillern-none".equals(v.getTag())) notes++;
    out.append("file sets with the no-fillerns note=").append(notes).append('\n');
    View scroll = root().findViewWithTag("imported-scroll");
    View inner = ((ViewGroup) scroll).getChildAt(0);
    int screen = app.getResources().getDisplayMetrics().heightPixels;
    out.append("list taller than its box=").append(inner.getHeight() > scroll.getHeight()).append('\n');
    out.append("box at most a third of the screen=").append(scroll.getHeight() <= screen / 3 + 1).append('\n');
    View knobs = (View) get("knobsRow");
    int[] at = new int[2];
    knobs.getLocationInWindow(at);
    out.append("knobs on screen=").append(knobs.isShown() && at[1] + knobs.getHeight() <= screen).append('\n');
    write("s22_imported_scroll", out.toString());
  }

  private List<View> allViews(View v) {
    List<View> out = new ArrayList<>();
    out.add(v);
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) out.addAll(allViews(g.getChildAt(i)));
    }
    return out;
  }

  /** Text of the shown chips in the imported pattern packs. */
  private String packChips() throws Exception {
    List<String> names = new ArrayList<>();
    collectChips((View) get("importedHost"), names);
    return names.toString();
  }

  private void collectChips(View v, List<String> out) {
    if (v instanceof TextView && v.isShown() && !"pack".equals(v.getTag())) out.add(((TextView) v).getText().toString());
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) collectChips(g.getChildAt(i), out);
    }
  }

  /** Names of shown pattern chips drawn underlined. */
  private String underlinedChips() {
    List<String> names = new ArrayList<>();
    collectUnderlined(root(), names);
    return names.toString();
  }

  private void collectUnderlined(View v, List<String> out) {
    if (v instanceof TextView && v.isShown() && v.getTag() != null
        && (((TextView) v).getPaintFlags() & android.graphics.Paint.UNDERLINE_TEXT_FLAG) != 0) {
      out.add(((TextView) v).getText().toString());
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) collectUnderlined(g.getChildAt(i), out);
    }
  }

  /** Entries of the open list dialog that are underlined. */
  private String underlinedItems() {
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    List<String> names = new ArrayList<>();
    android.widget.ListAdapter a = d.getListView().getAdapter();
    for (int i = 0; i < a.getCount(); i++) {
      Object item = a.getItem(i);
      if (item instanceof android.text.Spanned
          && ((android.text.Spanned) item).getSpans(0, ((android.text.Spanned) item).length(), android.text.style.UnderlineSpan.class).length > 0) {
        names.add(item.toString().trim());
      }
    }
    return names.toString();
  }

  /** A detected drum track: 64 bars that each differ a little, with two silent bars. */
  private byte[] detectedMidi() {
    java.util.Random rng = new java.util.Random(7);
    int[][] base = Engine.styleCells(Engine.styles().get("rock"));
    List<Engine.Part> parts = new ArrayList<>();
    for (int b = 0; b < 64; b++) {
      int[][] cells = Engine.copyCells(base);
      if (b == 30 || b == 31) cells = Engine.emptyCells();
      else for (int k = 0; k < 2; k++) cells[rng.nextInt(3)][rng.nextInt(16)] = 90;
      parts.add(Engine.groove("b" + b, 121, cells, 1));
    }
    byte[] midi = Engine.encodeSongMidi(parts);
    return midi;
  }

  /** Import a MIDI as program output, decline the song, and count what the file set holds. */
  private String importStats(byte[] midi, String name) throws Exception {
    call("pkImportProgramMidi", midi, name + ".mid");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    if (d != null && d.isShowing()) {
      d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
      idle();
    }
    Engine.MidiBars parsed = Engine.parseMidiBars(midi);
    // Bars a hit or two apart are merged on import; the song must follow the merged bars.
    List<int[][]> merged = Engine.mergeNearBars(parsed.bars, MidiImportSettings.mergeLimit());
    java.util.Set<String> barSigs = new java.util.HashSet<>();
    int changedHits = 0;
    for (int i = 0; i < merged.size(); i++) {
      barSigs.add(Engine.patternSignature(merged.get(i)));
      changedHits += Engine.hitDiff(merged.get(i), parsed.bars.get(i));
    }
    StringBuilder out = new StringBuilder();
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    @SuppressWarnings("unchecked")
    List<Engine.LearnedFill> fills = (List<Engine.LearnedFill>) get("learnedFills");
    int pats = 0, patsFromFile = 0, styled = 0;
    for (Engine.Learned l : learned) {
      if (!name.equals(l.source)) continue;
      pats++;
      if (barSigs.contains(Engine.patternSignature(l.cells))) patsFromFile++;
      if (l.closest != null && !l.closest.isEmpty()) styled++;
    }
    int fillCount = 0, fillsFromFile = 0;
    for (Engine.LearnedFill f : fills) {
      if (!name.equals(f.source)) continue;
      fillCount++;
      if (barSigs.contains(Engine.patternSignature(f.cells))) fillsFromFile++;
    }
    out.append("bars=").append(parsed.bars.size()).append(" distinct after merge=").append(barSigs.size()).append(" hits changed=").append(changedHits).append('\n');
    out.append("patterns=").append(pats).append(" from the file=").append(patsFromFile).append(" styled=").append(styled).append('\n');
    out.append("fills=").append(fillCount).append(" from the file=").append(fillsFromFile).append('\n');
    out.append("swing=").append(call("swing")).append(" human=").append(call("human")).append('\n');
    @SuppressWarnings("unchecked")
    Map<String, String> fillerns = (Map<String, String>) get("fillernPairs");
    Engine.FileSet set = Engine.collectFset(name, name, learned, fills, fillerns);
    call("ensureFsetInfoMap");
    @SuppressWarnings("unchecked")
    List<Engine.FileSetPart> stored = (List<Engine.FileSetPart>) ((Map<String, Object>) get("fsetInfoMap")).get(name);
    if (stored != null && !stored.isEmpty()) {
      set.parts.clear();
      set.parts.addAll(stored);
    }
    List<String> song = new ArrayList<>();
    for (Engine.Part p : Engine.songFromFileSet(set)) {
      for (int r = 0; r < p.repeats; r++) song.add(Engine.patternSignature(p.cells));
    }
    int inPlace = 0;
    for (int i = 0; i < Math.min(song.size(), merged.size()); i++) {
      if (song.get(i).equals(Engine.patternSignature(merged.get(i)))) inPlace++;
    }
    out.append("song bars=").append(song.size()).append(" in place=").append(inPlace).append('\n');
    return out.toString();
  }

  /** Tap a PyJav menu button and choose an entry in the list dialog it opens. */
  private void pickFromMenu(String button, String entry) throws Exception {
    TextView b = findText(root(), button);
    if (b == null) throw new AssertionError("no button " + button);
    b.performClick();
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    android.widget.ListView list = d.getListView();
    for (int i = 0; i < list.getAdapter().getCount(); i++) {
      if (entry.equals(String.valueOf(list.getAdapter().getItem(i)))) {
        org.robolectric.Shadows.shadowOf(d).clickOnItem(i);
        idle();
        return;
      }
    }
    throw new AssertionError("no entry " + entry);
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

  /** Draw the whole window, as the phone does. Catches crashes that only happen while drawing. */
  private void drawAll() {
    View r = root();
    int w = Math.max(1, r.getWidth()), h = Math.max(1, r.getHeight());
    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
    r.draw(new android.graphics.Canvas(bmp));
  }

  private void write(String name, String text) throws Exception {
    drawAll();
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
