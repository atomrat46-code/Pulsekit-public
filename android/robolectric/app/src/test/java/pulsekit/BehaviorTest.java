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
    Engine.fileSetSongs.clear();
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
    pickFromMenu("Java \u25be", "DrumMidi_CRT.java");
    TextView ed = (TextView) get("pyEditor");
    out.append("after Java pick: pyName=").append(get("pyName"))
        .append(" editorShowsSource=").append(ed.getText().toString().contains("public class DrumMidi_CRT")).append('\n');
    ed.setText(ed.getText() + "\n// edited");
    String edited = (String) call("sourceToRun");
    out.append("edit runs: ").append(edited != null && edited.endsWith("// edited")).append('\n');
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
    View list = box == null ? null : box.findViewWithTag("pk-recent-list");
    out.append("list starts closed=").append(list != null && list.getVisibility() == View.GONE).append('\n');
    box.findViewWithTag("pk-recent-toggle").performClick();
    out.append("list opens=").append(list.getVisibility() == View.VISIBLE).append('\n');
    if (entry != null) {
      entry.performClick();
      idle();
    }
    list = root().findViewWithTag("pk-recent-list");
    out.append("list closes after pick=").append(list != null && list.getVisibility() == View.GONE).append('\n');
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

  @Test
  public void s23_no_style_selected_at_start() throws Exception {
    StringBuilder out = new StringBuilder();
    out.append("selected at start=").append(selectedChips()).append('\n');
    findText(root(), "Rock").performClick();
    idle();
    out.append("selected after tapping Rock=").append(selectedChips()).append('\n');
    write("s23_no_style_selected", out.toString());
  }

  @Test
  public void s24_fills_in_file_sets() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("rock")), 2));
    parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get("funk")), 2));
    call("ingest", Engine.encodeSongMidi(parts), "No fills.mid", null);
    idle();
    AlertDialog song = (AlertDialog) ShadowDialog.getLatestDialog();
    if (song != null && song.isShowing()) {
      song.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
      idle();
    }
    call("show", "fills");
    idle();
    StringBuilder out = new StringBuilder();
    for (View v : allViews((View) get("importedFillHost"))) {
      if (v instanceof TextView && "pack".equals(v.getTag()) && ((TextView) v).getText().toString().startsWith("\u25b8 ")) {
        out.append("set=").append(((TextView) v).getText()).append('\n');
        v.performClick();
        idle();
        break;
      }
    }
    out.append("fills tab=").append(fillPackChips()).append('\n');
    // "No fills yet, add one" -> Snare roll.
    root().findViewWithTag("fills-none").performClick();
    idle();
    pickItem(Engine.FILL_LABEL[1]);
    out.append("after add=").append(fillPackChips()).append('\n');
    // Long press Crash -> Variated fill into file set -> this set.
    TextView crash = null;
    for (View v : allViews((View) get("fillBar"))) {
      if (v instanceof TextView && "crash".equals(v.getTag())) crash = (TextView) v;
    }
    if (crash == null) throw new AssertionError("no Crash chip");
    crash.performLongClick();
    idle();
    pickItem("Variated fill into file set");
    pickItem("No fills");
    out.append("after variated=").append(fillPackChips()).append('\n');
    crash.performLongClick();
    idle();
    pickItem("Copy fill to file set");
    pickItem("No fills");
    out.append("after copy=").append(fillPackChips()).append('\n');
    @SuppressWarnings("unchecked")
    List<Engine.LearnedFill> fills = (List<Engine.LearnedFill>) get("learnedFills");
    for (Engine.LearnedFill f : fills) {
      // A variation is random: only that it has hits is stable.
      String hits = f.name.endsWith(" var") ? (Engine.hitCount(f.cells) > 0 ? "some" : "0") : Integer.toString(Engine.hitCount(f.cells));
      out.append("fill ").append(f.name).append(" src=").append(f.source).append(" hits=").append(hits).append('\n');
    }
    write("s24_fills_in_file_sets", out.toString());
  }

  @Test
  public void s25_fillern_types() throws Exception {
    StringBuilder out = new StringBuilder();
    String key = (String) call("patternKeyFor", "rock");
    for (String type : Engine.FILLERN_MODE_LABELS) {
      findText(root(), "Rock").performClick();
      idle();
      call("show", "combo");
      idle();
      TextView rock = null;
      for (View v : allViews((View) get("styleBar"))) {
        if (v instanceof TextView && "rock".equals(v.getTag())) rock = (TextView) v;
      }
      rock.performLongClick();
      idle();
      out.append(type).append(": list shows ");
      List<String> rows = new ArrayList<>();
      android.widget.ListAdapter a = ((AlertDialog) ShadowDialog.getLatestDialog()).getListView().getAdapter();
      for (int i = 0; i < 5 && i < a.getCount(); i++) rows.add(String.valueOf(a.getItem(i)).trim());
      out.append(rows).append('\n');
      pickItem(type);
      rock.performLongClick();
      idle();
      pickItem(Engine.FILL_LABEL[1]);
      out.append("  underlined type=").append(underlinedItemsAfterLongPress(rock)).append('\n');
      @SuppressWarnings("unchecked")
      List<Engine.Part> song = (List<Engine.Part>) call("activeSong");
      song.clear();
      call("applySongPick", "fillern", key, -1);
      idle();
      int bars = 0;
      StringBuilder parts = new StringBuilder();
      for (Engine.Part p : song) {
        bars += p.repeats;
        parts.append(p.kind).append(' ').append(p.name).append(" x").append(p.repeats).append(" steps=").append(p.steps).append("; ");
      }
      out.append("  song: ").append(parts).append("bars=").append(bars).append('\n');
      for (Engine.Part p : song) {
        if (p.name.contains(" + ")) {
          int[][] rockCells = Engine.styleCells(Engine.styles().get("rock"));
          int first = Engine.hitDiff(cut(p.cells, 0, 8), cut(rockCells, 0, 8));
          int last = Engine.hitDiff(cut(p.cells, 8, 16), cut(rockCells, 8, 16));
          out.append("  mixed bar differs from Rock: first half ").append(first).append(" hits, second half ").append(last).append(" hits\n");
        }
      }
    }
    write("s25_fillern_types", out.toString());
  }

  @Test
  public void s26_replace_song_part_with_fillern() throws Exception {
    StringBuilder out = new StringBuilder();
    // Fillern type default: replaces end.
    call("show", "midisettings");
    idle();
    findText((View) get("drumMidiPane"), Engine.FILLERN_MODE_LABELS[1]).performClick();
    idle();
    out.append("default=").append(MidiImportSettings.fillernDefault).append('\n');
    // A song MIDI, and the song made from it.
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get("funk")), 4));
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("rock")), 4));
    call("ingest", Engine.encodeSongMidi(parts), "Song.mid", null);
    idle();
    AlertDialog make = (AlertDialog) ShadowDialog.getLatestDialog();
    make.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("song cells=").append(timelineCells()).append(" bars=").append(songBars()).append('\n');
    // A Fillern for Pattern 1 with Snare roll; it has no type of its own.
    call("show", "combo");
    idle();
    root().findViewWithTag("fillern-add").performClick();
    idle();
    pickItem("Pattern 1");
    pickItem(Engine.FILL_LABEL[1]);
    out.append("fillern chips=").append(packChips()).append('\n');
    // Song tab, imported lane: long press the second part, replace it with the Fillern.
    call("show", "song");
    idle();
    View second = findText((View) get("songCards"), "Pattern 2");
    while (second != null && !second.isLongClickable()) second = (View) second.getParent();
    second.performLongClick();
    idle();
    pickItem("Replace with fillern");
    pickItem("Pattern 1 \u00b7 Snare roll (replaces end)");
    out.append("after replace=").append(timelineCells()).append(" bars=").append(songBars()).append('\n');
    write("s26_replace_with_fillern", out.toString());
  }

  @Test
  public void s27_song_follows_playback() throws Exception {
    List<Engine.Part> parts = new ArrayList<>();
    for (int i = 0; i < 16; i++) parts.add(Engine.groove("P" + i, 120, Engine.styleCells(Engine.styles().get("rock")), 1));
    parts.add(2, Engine.fill("Snare roll", 120, 1));
    parts.get(6).name = "P5 + Snare roll";  // a pattern with a Fillern's fill in it
    call("addImportedArrangement", "Long song", parts);
    idle();
    StringBuilder out = new StringBuilder();
    ViewGroup line = (ViewGroup) get("timeline");
    int fillColor = Engine.FILL_CELL_COLOR;
    List<String> colored = new ArrayList<>();
    for (int i = 0; i < line.getChildCount(); i++) {
      View top = ((ViewGroup) line.getChildAt(i)).getChildAt(0);
      android.graphics.drawable.Drawable bg = top.getBackground();
      if (bg instanceof android.graphics.drawable.GradientDrawable
          && ((android.graphics.drawable.GradientDrawable) bg).getColor() != null
          && ((android.graphics.drawable.GradientDrawable) bg).getColor().getDefaultColor() == fillColor) {
        colored.add(((TextView) ((ViewGroup) line.getChildAt(i)).getChildAt(1)).getText().toString());
      }
    }
    out.append("fill-colored cells=").append(colored).append('\n');
    android.widget.HorizontalScrollView strip = (android.widget.HorizontalScrollView) line.getParent();
    out.append("strip scroll at start=").append(strip.getScrollX()).append('\n');
    // Playing part 14 of 17.
    setField("songPlay", true);
    setField("songIndex", 14);
    call("refreshSong");
    idle();
    ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
    line = (ViewGroup) get("timeline");
    strip = (android.widget.HorizontalScrollView) line.getParent();
    View now = line.getChildAt(14);
    boolean visible = now.getLeft() >= strip.getScrollX() && now.getRight() <= strip.getScrollX() + strip.getWidth();
    out.append("strip scrolled=").append(strip.getScrollX() > 0).append(" playing cell in view=").append(visible).append('\n');
    setField("songPlay", false);
    write("s27_song_follows_playback", out.toString());
  }

  @Test
  public void s28_song_saved_in_file_set() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 110, Engine.styleCells(Engine.styles().get("funk")), 2));
    parts.add(Engine.groove("B", 110, Engine.styleCells(Engine.styles().get("house")), 2));
    call("ingest", Engine.encodeSongMidi(parts), "Set song.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    // Add a built-in pattern to the imported song: a part from outside the set.
    findText(root(), "Rock").performClick();
    idle();
    call("show", "song");
    idle();
    findText(root(), "Pattern \u00d74").performClick();
    idle();
    out.append("song=").append(timelineCells()).append('\n');
    root().findViewWithTag("song-save-set").performClick();
    idle();
    pickItem("Set song");
    AlertDialog ask = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("asked=").append(ask.isShowing()).append('\n');
    ask.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    List<Engine.FileSetSong> saved = Engine.fileSetSongs.get("Set song");
    Engine.FileSetSong song = saved.get(0);
    StringBuilder refs = new StringBuilder();
    for (Engine.SongRef r : song.parts) refs.append(r.part.name).append("->").append(r.usePattern != null ? r.usePattern : (r.useFill != null ? r.useFill : "-")).append("; ");
    out.append("saved ").append(song.name).append(": ").append(refs).append('\n');
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    List<String> setPatterns = new ArrayList<>();
    for (Engine.Learned l : Engine.learnedFrom(learned, "Set song")) setPatterns.add(l.name);
    out.append("set patterns=").append(setPatterns).append('\n');
    // Edit Pattern 1 in the set; reopening the song shows the edit.
    Engine.Learned p1 = null;
    for (Engine.Learned l : Engine.learnedFrom(learned, "Set song")) if ("Pattern 1".equals(l.name)) p1 = l;
    p1.cells[0][3] = 100;
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    call("refreshFromFileSet", songs.get(0));
    out.append("song follows the edit=").append(songs.get(0).parts.get(0).cells[0][3] == 100).append('\n');
    // Export the set and load it back: the song comes with it.
    @SuppressWarnings("unchecked")
    List<Engine.LearnedFill> fills = (List<Engine.LearnedFill>) get("learnedFills");
    @SuppressWarnings("unchecked")
    Map<String, String> pairs = (Map<String, String>) get("fillernPairs");
    byte[] fset = Engine.encodeFset(Engine.collectFset("Set song", "Set song", learned, fills, pairs));
    Engine.FileSet back = Engine.decodeFset(fset);
    out.append("fset songs=").append(back.songs.size()).append(" parts=").append(back.songs.isEmpty() ? 0 : back.songs.get(0).parts.size()).append('\n');
    call("removeImportSource", "Set song");
    idle();
    songs.clear();
    call("loadFset", fset, "Set song.fset");
    idle();
    int bars = 0;
    for (Engine.Part p : songs.get(0).parts) bars += p.repeats;
    out.append("after reload: song=").append(songs.get(0).name).append(" in ").append(songs.get(0).fileSet).append(" bars=").append(bars)
        .append(" edit kept=").append(songs.get(0).parts.get(0).cells[0][3] == 100).append('\n');
    write("s28_song_in_file_set", out.toString());
  }

  @Test
  public void s29_song_export_named_after_file_set() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 2));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 2));
    call("ingest", Engine.encodeSongMidi(parts), "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.Part> song = (List<Engine.Part>) call("songPartsForExport");
    String set = (String) call("songFileSet", song);
    out.append("imported song: ").append(Engine.songFilename(song, set)).append(" / ").append(Engine.songFilename(song, set).replace(".sng", ".mid")).append('\n');
    // A song of built-in patterns only keeps the old name.
    call("loadStyle", "rock", false);
    idle();
    ((View) get("songPane")).findViewWithTag("original").performClick();
    idle();
    findText(root(), "Pattern \u00d74").performClick();
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.Part> own = (List<Engine.Part>) call("songPartsForExport");
    StringBuilder names = new StringBuilder();
    for (Engine.Part p : own) names.append(p.name).append(' ');
    out.append("lane=").append(get("songLane")).append(" parts=").append(names).append('\n');
    out.append("built-in song: ").append(Engine.songFilename(own, (String) call("songFileSet", own))).append('\n');
    write("s29_song_export_names", out.toString());
  }

  @Test
  public void s30_drummidi_params_resets() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "DrumMidi_CRT.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("in.wav out.mid --sens 2.0 --bpm 99");
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    d.getWindow().getDecorView().findViewWithTag("params-suggested").performClick();
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("suggested: ").append(args.getText()).append('\n');
    call("pkOpenParams");
    idle();
    d = (AlertDialog) ShadowDialog.getLatestDialog();
    d.getWindow().getDecorView().findViewWithTag("params-defaults").performClick();
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("defaults: ").append(args.getText()).append('\n');
    // Kick, snare and toms only: a checkbox; the switch reaches the arguments and Params reopens ticked.
    call("pkOpenParams");
    idle();
    d = (AlertDialog) ShadowDialog.getLatestDialog();
    android.widget.CheckBox noCymbals = (android.widget.CheckBox) d.getWindow().getDecorView().findViewWithTag("params-check:--no-cymbals");
    out.append("no-cymbals box: ").append(noCymbals.getText()).append(", ticked ").append(noCymbals.isChecked()).append('\n');
    noCymbals.setChecked(true);
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("no cymbals: ").append(args.getText()).append('\n');
    call("pkOpenParams");
    idle();
    d = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("reopened ticked: ").append(((android.widget.CheckBox) d.getWindow().getDecorView().findViewWithTag("params-check:--no-cymbals")).isChecked()).append('\n');
    d.getWindow().getDecorView().findViewWithTag("params-defaults").performClick();
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("defaults again: ").append(args.getText()).append('\n');
    write("s30_drummidi_params_resets", out.toString());
  }

  @Test
  public void s31_song_audio_export() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    call("ingest", Engine.encodeSongMidi(parts), "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    call("show", "export");
    idle();
    for (String label : new String[] {"Song WAV", "Song MP3"}) {
      findText(root(), label).performClick();
      idle();
      android.content.Intent save = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult().intent;
      out.append(label).append(": ").append(save.getType()).append(' ').append(save.getStringExtra("android.intent.extra.TITLE")).append('\n');
    }
    @SuppressWarnings("unchecked")
    List<Engine.Part> song = (List<Engine.Part>) call("songPartsForExport");
    double sec = 0;
    for (Engine.Part p : song) sec += p.repeats * p.steps * 15.0 / p.bpm;
    short[] pcm = (short[]) call("songPcm");
    out.append("song ").append(String.format(java.util.Locale.ROOT, "%.2f", sec)).append(" s, audio as long as the song: ").append(Math.abs(pcm.length / 22050.0 - sec) < 0.001).append('\n');
    write("s31_song_audio_export", out.toString());
  }

  /** A long imported song (more than 24 parts) keeps every part through a restart and exports at full length. */
  @Test
  public void s32_long_song_survives_restart() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
      parts.add(Engine.fill("toms", 121, 1));
      parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
      parts.add(Engine.fill("snare", 121, 1));
    }
    call("ingest", Engine.encodeSongMidi(parts), "Long Song.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.Part> made = (List<Engine.Part>) call("songPartsForExport");
    int madeBars = 0;
    for (Engine.Part p : made) madeBars += p.repeats;
    out.append("made: ").append(madeBars).append(" bars, more than 24 parts: ").append(made.size() > 24).append('\n');
    call("persistLearned");
    this.ctl.pause().stop().destroy();
    this.ctl = Robolectric.buildActivity(MainActivity.class).setup();
    this.app = this.ctl.get();
    idle();
    call("show", "combo");
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> kept = (List<Engine.ImportedSong>) get("importedSongs");
    int keptBars = 0;
    for (Engine.Part p : kept.get(0).parts) keptBars += p.repeats;
    out.append("after restart: ").append(keptBars).append(" bars, same parts: ").append(kept.get(0).parts.size() == made.size()).append('\n');
    double sec = 0;
    for (Engine.Part p : kept.get(0).parts) sec += p.repeats * p.steps * 15.0 / p.bpm;
    short[] pcm = AudioIo.renderSong(kept.get(0).parts, null, AudioIo.buildVoices(22050), 22050);
    out.append("audio as long as the song: ").append(Math.abs(pcm.length / 22050.0 - sec) < 0.001).append('\n');
    write("s32_long_song_survives_restart", out.toString());
  }

  /** File > Compare Hits: the song against the file set's MIDI, and both against a WAV of the song. */
  @Test
  public void s33_compare_hits() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    Engine.stageSourceMidi(Engine.encodeSongMidi(parts), "Passing Ships.mid");
    call("ingest", Engine.encodeSongMidi(parts), "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    call("show", "comparehits");
    idle();
    out.append("set chip: ").append(root().findViewWithTag("compare-set:Passing Ships") != null).append('\n');
    CompareHitsPage page = (CompareHitsPage) get("compareHits");
    page.pickedWav = AudioIo.encodeWav(AudioIo.renderSong(parts, null, AudioIo.buildVoices(44100), 44100), 44100);
    page.pickedWavName = "song.wav";
    page.paintWav();
    out.append("wav: ").append(page.wavLabel.getText()).append('\n');
    ((View) root().findViewWithTag("compare-run")).performClick();
    TextView result = (TextView) root().findViewWithTag("compare-result");
    for (int i = 0; i < 400 && result.getText().toString().startsWith("Comparing"); i++) {
      Thread.sleep(25);
      idle();
    }
    // The test WAV's drums are noise-based, so only the exact song-against-MIDI table is recorded.
    String text = result.getText().toString();
    int wavAt = text.indexOf("MIDI against WAV");
    out.append(wavAt > 0 ? text.substring(0, wavAt).trim() : text).append('\n');
    out.append("WAV tables: ").append(wavAt > 0 && text.contains("Song against WAV")).append('\n');
    write("s33_compare_hits", out.toString());
  }

  /** Params reads the loaded program's Usage line: file buttons for .wav and .mid, text fields for the rest. */
  @Test
  public void s34_params_from_program() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "DrumMidi_CRT.java");
    pickFromMenu("Java \u00b7 DrumMidi_CRT.java", "CompareHits.java");
    out.append("hint: ").append(((TextView) get("pkPyHint")).getText()).append('\n');
    out.append("page scrolls: ").append(root().findViewWithTag("py-scroll") instanceof android.widget.ScrollView).append('\n');
    TextView args = (TextView) get("pkPyArgs");
    args.setText("/x/in.wav");
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    for (String t : new String[] {"input.wav", "drums.mid", "song.mid"}) {
      out.append(t).append(": button ").append(dv.findViewWithTag("params-file:" + t) != null)
          .append(", chosen ").append(((TextView) dv.findViewWithTag("params-chosen:" + t)).getText()).append('\n');
    }
    out.append("suggested button: ").append(dv.findViewWithTag("params-suggested") != null).append('\n');
    android.widget.EditText log = (android.widget.EditText) dv.findViewWithTag("params-field:--log");
    out.append("--log field: ").append(log != null ? log.getHint() : "none").append('\n');
    log.setText("CompareHits_test_results.txt");
    dv.findViewWithTag("params-file:drums.mid").performClick();
    android.content.Intent pick = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult().intent;
    out.append("picker: ").append(pick.getAction()).append('\n');
    PyJavParams.filePicked("/x/drums.mid");
    out.append("drums.mid chosen: ").append(((TextView) dv.findViewWithTag("params-chosen:drums.mid")).getText()).append('\n');
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText()).append('\n');
    write("s34_params_from_program", out.toString());
  }

  /** A Recent entry for a bundled program loads the program as the app ships it, not the saved copy. */
  @Test
  public void s35_recent_uses_bundled_program() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    PyJavRecent.remember(app.getFilesDir(), "CompareHits.java", "a.wav d.mid", "// old copy with a lambda\n", null);
    call("pkRefreshRecent", 0);
    idle();
    call("pkApplyRecent", 1);
    idle();
    String editor = ((TextView) get("pyEditor")).getText().toString();
    out.append("editor has old copy=").append(editor.contains("old copy")).append('\n');
    out.append("editor has bundled program=").append(editor.contains("public final class CompareHits")).append('\n');
    out.append("args kept=").append(((TextView) get("pkPyArgs")).getText()).append('\n');
    write("s35_recent_uses_bundled_program", out.toString());
  }

  /** Params: a .mid can come from a file set's source MIDI, copied into PyJav's input folder. */
  @Test
  public void s36_params_midi_from_file_set() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    byte[] midi = Engine.encodeSongMidi(parts);
    Engine.stageSourceMidi(midi, "Passing Ships.mid");
    call("ingest", midi, "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "CompareHits.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("/x/in.wav");
    call("pkOpenParams");
    idle();
    AlertDialog params = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = params.getWindow().getDecorView();
    out.append("button for drums.mid: ").append(dv.findViewWithTag("params-fileset:drums.mid") != null).append('\n');
    out.append("button for input.wav: ").append(dv.findViewWithTag("params-fileset:input.wav") != null).append('\n');
    dv.findViewWithTag("params-fileset:drums.mid").performClick();
    idle();
    AlertDialog list = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("sets: ").append(list.getListView().getAdapter().getCount()).append(' ').append(list.getListView().getAdapter().getItem(0)).append('\n');
    org.robolectric.Shadows.shadowOf(list).clickOnItem(0);
    idle();
    out.append("chosen: ").append(((TextView) dv.findViewWithTag("params-chosen:drums.mid")).getText()).append('\n');
    params.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    String line = args.getText().toString();
    File copied = new File(app.getCacheDir(), "pyjav-in/Passing_Ships_source.mid");
    out.append("args: ").append(line.replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    out.append("copied file is the source MIDI: ").append(copied.isFile() && java.util.Arrays.equals(Files.readAllBytes(copied.toPath()), midi)).append('\n');
    write("s36_params_midi_from_file_set", out.toString());
  }

  /** Long press on PyJav's output saves it as <program>_test_results.txt. */
  @Test
  public void s37_save_program_output() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "CompareHits.java");
    TextView log = (TextView) get("pkPyLog");
    log.setText("Song against MIDI\nKick 271 261 188\nSucceeded: compared 3 files");
    app.pyJav.pkRunLog = log.getText().toString(); // as a run leaves it
    log.performLongClick();
    idle();
    AlertDialog menu = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("menu: ").append(menu.getListView().getAdapter().getItem(0)).append('\n');
    org.robolectric.Shadows.shadowOf(menu).clickOnItem(0);
    idle();
    android.content.Intent save = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult().intent;
    out.append("save: ").append(save.getAction()).append(' ').append(save.getType()).append(' ')
        .append(save.getStringExtra(android.content.Intent.EXTRA_TITLE)).append('\n');
    File file = new File(app.getCacheDir(), "saved_results.txt");
    android.content.Intent result = new android.content.Intent().setData(android.net.Uri.fromFile(file));
    org.robolectric.Shadows.shadowOf(app).receiveResult(save, android.app.Activity.RESULT_OK, result);
    idle();
    out.append("written: ").append(file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace('\n', '|') : "nothing").append('\n');
    write("s37_save_program_output", out.toString());
  }

  /** Long press on a song: Duplicate song, and Delete song after asking. */
  @Test
  public void s38_song_duplicate_delete() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    call("ingest", Engine.encodeSongMidi(parts), "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    call("show", "song");
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    String name = songs.get(0).name;
    root().findViewWithTag("song-chip:" + name).performLongClick();
    idle();
    AlertDialog menu = (AlertDialog) ShadowDialog.getLatestDialog();
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < menu.getListView().getAdapter().getCount(); i++) items.append(i == 0 ? "" : ", ").append(menu.getListView().getAdapter().getItem(i));
    out.append("menu: ").append(items).append('\n');
    org.robolectric.Shadows.shadowOf(menu).clickOnItem(itemIndex(menu, "Duplicate song"));
    idle();
    out.append("after duplicate: ");
    for (Engine.ImportedSong s : songs) out.append(s.name).append(" (").append(s.parts.size()).append(" parts) ");
    out.append('\n');
    out.append("copy selected: ").append(songs.get(1).id.equals(get("importedSongId"))).append('\n');
    out.append("parts copied, not shared: ").append(songs.get(1).parts.get(0) != songs.get(0).parts.get(0)
        && Engine.patternSignature(songs.get(1).parts.get(0).cells).equals(Engine.patternSignature(songs.get(0).parts.get(0).cells))).append('\n');
    String copyName = songs.get(1).name;
    for (String answer : new String[] {"Cancel", "Delete"}) {
      root().findViewWithTag("song-chip:" + copyName).performLongClick();
      idle();
      AlertDialog songMenu = (AlertDialog) ShadowDialog.getLatestDialog();
      org.robolectric.Shadows.shadowOf(songMenu).clickOnItem(itemIndex(songMenu, "Delete song"));
      idle();
      AlertDialog confirm = (AlertDialog) ShadowDialog.getLatestDialog();
      if ("Cancel".equals(answer)) {
        out.append("confirm: ").append(org.robolectric.Shadows.shadowOf(confirm).getMessage()).append('\n');
        confirm.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
      } else {
        confirm.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
      }
      idle();
      out.append("after ").append(answer).append(": ").append(songs.size()).append(" songs\n");
    }
    out.append("left: ").append(songs.get(0).name).append(", selected: ").append(songs.get(0).id.equals(get("importedSongId"))).append('\n');
    write("s38_song_duplicate_delete", out.toString());
  }

  /** Make song from a file set takes that set's whole name, even when another set starts the same way. */
  @Test
  public void s39_make_song_names_after_file_set() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    for (String file : new String[] {"passing ships v10 121 aaaaaa.mid", "passing ships v10 121 wVqTYx.mid"}) {
      call("ingest", Engine.encodeSongMidi(parts), file, null);
      idle();
      ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
      idle();
    }
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    @SuppressWarnings("unchecked")
    List<Engine.Learned> learned = (List<Engine.Learned>) get("learned");
    @SuppressWarnings("unchecked")
    List<Engine.LearnedFill> fills = (List<Engine.LearnedFill>) get("learnedFills");
    out.append("sets: ").append(Engine.fileSetSources(learned, fills)).append('\n');
    out.append("made on import: ");
    for (Engine.ImportedSong s : songs) out.append(s.name).append(" | ");
    out.append('\n');
    FileSets fileSets = (FileSets) get("fileSets");
    fileSets.makeFileSetSong("f:passing ships v10 121 wVqTYx", "passing ships v10 121 wVqTYx");
    idle();
    // The set already has its import's song: Make song asks; Make another adds one.
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
    idle();
    Engine.ImportedSong made = songs.get(0);
    out.append("Make song: ").append(made.name).append(", file set ").append(made.fileSet).append(", selected ")
        .append(made.id.equals(get("importedSongId"))).append('\n');
    call("show", "song");
    idle();
    View strip = root().findViewWithTag("song-strip");
    out.append("song list scrolls sideways: ").append(strip instanceof android.widget.HorizontalScrollView).append('\n');
    write("s39_make_song_names_after_file_set", out.toString());
  }

  /** File set menu > Compare hits: results shown, kept under the file set, and still there after a restart. */
  @Test
  public void s40_file_set_compare_hits() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    // As DrumMidi_CRT writes it: its settings in a text event.
    byte[] midi = withText(Engine.encodeSongMidi(parts), "pulsekit-drummidi; input=Passing Ships.wav; output=Passing Ships.mid; "
        + "args=--sens 0.4 --hat 0.4; sens=0.4; hat=0.4; tom=0.4; ride=0.4; crash=0.4; bpm=auto; quantize=off; hpss=on; tempo=121");
    Engine.stageSourceMidi(midi, "Passing Ships.mid");
    call("ingest", midi, "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    call("show", "combo");
    idle();
    TextView pack = null;
    for (View v : allViews(root())) {
      if (v instanceof TextView && "pack".equals(v.getTag()) && ((TextView) v).getText().toString().contains("Passing Ships")) pack = (TextView) v;
    }
    pack.performLongClick();
    idle();
    AlertDialog menu = (AlertDialog) ShadowDialog.getLatestDialog();
    int at = -1;
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < menu.getListView().getAdapter().getCount(); i++) {
      String item = String.valueOf(menu.getListView().getAdapter().getItem(i));
      items.append(item).append(" | ");
      if ("Compare hits".equals(item)) at = i;
    }
    out.append("menu: ").append(items).append('\n');
    org.robolectric.Shadows.shadowOf(menu).clickOnItem(at);
    AlertDialog shown = null;
    for (int i = 0; i < 400; i++) {
      idle();
      Thread.sleep(25);
      android.app.Dialog d = ShadowDialog.getLatestDialog();
      if (d != menu && d instanceof AlertDialog && d.isShowing()) {
        shown = (AlertDialog) d;
        break;
      }
    }
    TextView text = (TextView) shown.getWindow().getDecorView().findViewWithTag("fileset-results-text");
    String body = text.getText().toString();
    out.append("results start: ").append(body.substring(0, body.indexOf("Song against MIDI"))).append('\n');
    out.append("suggestions: ").append(body.substring(body.indexOf("Suggestions"), body.indexOf("matched:")).trim().replace('\n', '|')).append('\n');
    out.append("results end: ").append(body.substring(body.lastIndexOf("Succeeded"))).append('\n');
    View dv = shown.getWindow().getDecorView();
    out.append("buttons: ").append(((TextView) dv.findViewWithTag("results-save")).getText()).append(" | ")
        .append(((TextView) dv.findViewWithTag("results-close")).getText()).append('\n');
    out.append("text box at most 60% of the screen: ").append(((View) text.getParent()).getLayoutParams().height
        <= Math.max(Math.round(120 * app.getResources().getDisplayMetrics().density), app.getResources().getDisplayMetrics().heightPixels * 0.6f)).append('\n');
    out.append("wraps, no sideways scroll: ").append(text.getParent() instanceof android.widget.ScrollView).append(", text ")
        .append(Math.round(text.getTextSize() / app.getResources().getDisplayMetrics().scaledDensity * 2) / 2.0).append(" sp, window width ")
        .append(shown.getWindow().getAttributes().width == app.getResources().getDisplayMetrics().widthPixels - Math.round(16 * app.getResources().getDisplayMetrics().density)).append('\n');
    dv.findViewWithTag("results-close").performClick();
    idle();
    out.append("closed: ").append(!shown.isShowing()).append('\n');
    out.append("row under file set: ").append(root().findViewWithTag("fileset-results") != null).append('\n');
    call("persistLearned");
    this.ctl.pause().stop().destroy();
    Engine.fileSetAudio.clear();
    this.ctl = Robolectric.buildActivity(MainActivity.class).setup();
    this.app = this.ctl.get();
    idle();
    call("show", "combo");
    idle();
    out.append("after restart: kept ").append(body.equals(Engine.fileSetResults("Passing Ships")))
        .append(", row ").append(root().findViewWithTag("fileset-results") != null).append('\n');
    write("s40_file_set_compare_hits", out.toString());
  }

  /** Info shows the file set's songs; Make song asks when the set has one: Replace, Make another, Open it. */
  @Test
  public void s41_file_set_songs() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    call("ingest", Engine.encodeSongMidi(parts), "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    FileSets fileSets = (FileSets) get("fileSets");
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    fileSets.openFileSetInfo("f:Passing Ships", "Passing Ships");
    idle();
    out.append("info, no song: ").append(texts((ViewGroup) root().findViewWithTag("info-songs"))).append('\n');
    fileSets.makeFileSetSong("f:Passing Ships", "Passing Ships");
    idle();
    out.append("first Make song, no question: ").append(songs.size()).append(" song ").append(songs.get(0).name)
        .append(", file set ").append(songs.get(0).fileSet).append('\n');
    Engine.ImportedSong first = songs.get(0);
    fileSets.openFileSetInfo("f:Passing Ships", "Passing Ships");
    idle();
    out.append("info: ").append(texts((ViewGroup) root().findViewWithTag("info-songs"))).append('\n');
    first.parts.remove(first.parts.size() - 1);
    int edited = first.parts.size();
    fileSets.makeFileSetSong("f:Passing Ships", "Passing Ships");
    idle();
    AlertDialog ask = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("asks: ").append(org.robolectric.Shadows.shadowOf(ask).getTitle()).append(" | ")
        .append(org.robolectric.Shadows.shadowOf(ask).getMessage().toString().replace('\n', ' ')).append(" | ")
        .append(ask.getButton(DialogInterface.BUTTON_POSITIVE).getText()).append(", ")
        .append(ask.getButton(DialogInterface.BUTTON_NEUTRAL).getText()).append(", ")
        .append(ask.getButton(DialogInterface.BUTTON_NEGATIVE).getText()).append('\n');
    ask.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("Replace: ").append(songs.size()).append(" song, same song ").append(songs.get(0) == first)
        .append(", parts rebuilt ").append(edited).append(" -> ").append(first.parts.size()).append(", view ").append(get("view")).append('\n');
    fileSets.makeFileSetSong("f:Passing Ships", "Passing Ships");
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
    idle();
    out.append("Make another: ");
    for (Engine.ImportedSong x : songs) out.append(x.name).append(" | ");
    out.append('\n');
    call("show", "pattern");
    idle();
    fileSets.makeFileSetSong("f:Passing Ships", "Passing Ships");
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    out.append("Open it: view ").append(get("view")).append(", selected ").append(songs.get(0).id.equals(get("importedSongId"))).append('\n');
    // A song that is not from a file set has no ring, in Edit and in Play.
    call("addImportedArrangement", "Loose song", new ArrayList<Engine.Part>(parts));
    idle();
    for (String mode : new String[] {"edit", "play"}) {
      setField("songMode", mode);
      call("refreshSong");
      idle();
      StringBuilder rings = new StringBuilder();
      for (Engine.ImportedSong x : songs) {
        View chip = root().findViewWithTag("song-chip:" + x.name);
        rings.append(x.name).append(chip != null && "File set song".contentEquals(String.valueOf(chip.getContentDescription())) ? " (ring)" : "").append(" | ");
      }
      out.append(mode).append(" chips: ").append(rings).append('\n');
    }
    setField("songMode", "edit");
    // Info: Delete after each song, after asking; Info shows the rest.
    fileSets.openFileSetInfo("f:Passing Ships", "Passing Ships");
    idle();
    out.append("info: ").append(texts((ViewGroup) root().findViewWithTag("info-songs"))).append('\n');
    String gone = songs.get(1).name;
    for (String answer : new String[] {"Cancel", "Delete"}) {
      root().findViewWithTag("info-delete:" + gone).performClick();
      idle();
      AlertDialog confirm = (AlertDialog) ShadowDialog.getLatestDialog();
      confirm.getButton("Cancel".equals(answer) ? DialogInterface.BUTTON_NEGATIVE : DialogInterface.BUTTON_POSITIVE).performClick();
      idle();
      out.append(answer).append(": ").append(songs.size()).append(" songs, view ").append(get("view")).append('\n');
    }
    out.append("info after delete: ").append(texts((ViewGroup) root().findViewWithTag("info-songs"))).append('\n');
    write("s41_file_set_songs", out.toString());
  }

  /** Song menu: Info (length, counts, file set, style) and Change style (that song, rebuilt in the new style). */
  @Test
  public void s42_song_info_and_style() throws Exception {
    StringBuilder out = new StringBuilder();
    List<Engine.Part> parts = new ArrayList<>();
    parts.add(Engine.groove("A", 121, Engine.styleCells(Engine.styles().get("rock")), 4));
    parts.add(Engine.fill("toms", 121, 1));
    parts.add(Engine.groove("B", 121, Engine.styleCells(Engine.styles().get("funk")), 4));
    parts.add(Engine.fill("snare", 121, 1));
    call("ingest", Engine.encodeSongMidi(parts), "Passing Ships.mid", null);
    idle();
    ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    call("show", "song");
    idle();
    @SuppressWarnings("unchecked")
    List<Engine.ImportedSong> songs = (List<Engine.ImportedSong>) get("importedSongs");
    Engine.ImportedSong song = songs.get(0);
    root().findViewWithTag("song-chip:" + song.name).performLongClick();
    idle();
    AlertDialog menu = (AlertDialog) ShadowDialog.getLatestDialog();
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < menu.getListView().getAdapter().getCount(); i++) items.append(menu.getListView().getAdapter().getItem(i)).append(" | ");
    out.append("menu: ").append(items).append('\n');
    org.robolectric.Shadows.shadowOf(menu).clickOnItem(itemIndex(menu, "Info"));
    idle();
    AlertDialog info = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("info:\n").append(((TextView) info.getWindow().getDecorView().findViewWithTag("song-info-text")).getText()).append('\n');
    info.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    root().findViewWithTag("song-chip:" + song.name).performLongClick();
    idle();
    AlertDialog again = (AlertDialog) ShadowDialog.getLatestDialog();
    org.robolectric.Shadows.shadowOf(again).clickOnItem(itemIndex(again, "Change style"));
    idle();
    AlertDialog styles = (AlertDialog) ShadowDialog.getLatestDialog();
    int pick = 2;
    org.robolectric.Shadows.shadowOf(styles).clickOnItem(pick);
    String chosen = StyleDb.rows().get(pick).name;
    styles.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    FileSets fileSets = (FileSets) get("fileSets");
    out.append("Change style to ").append(chosen).append(": ").append(songs.size()).append(" song, same song ")
        .append(songs.contains(song)).append(", set style now ").append(fileSets.fileSetStyleLabel("f:Passing Ships")).append('\n');
    write("s42_song_info_and_style", out.toString());
  }

  /** CutWav in PyJav's Java menu; Params: an input file, an optional output name, the switches. */
  @Test
  public void s43_cutwav_params() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "CutWav.java");
    out.append("hint: ").append(((TextView) get("pkPyHint")).getText().toString().replace('\n', '|')).append('\n');
    TextView args = (TextView) get("pkPyArgs");
    args.setText("/x/song.wav");
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    out.append("input button: ").append(dv.findViewWithTag("params-file:input.wav") != null).append('\n');
    android.widget.EditText outName = (android.widget.EditText) dv.findViewWithTag("params-field:output.wav");
    out.append("output field: ").append(outName == null ? "none" : outName.getHint()).append('\n');
    for (String flag : new String[] {"--split_time", "--split_lenght", "--trim", "--bpm"}) {
      android.widget.EditText f = (android.widget.EditText) dv.findViewWithTag("params-field:" + flag);
      out.append(flag).append(": ").append(f == null ? "none" : f.getHint()).append('\n');
    }
    ((android.widget.EditText) dv.findViewWithTag("params-field:--split_time")).setText("02:45");
    out.append("--trim checkbox: ").append(dv.findViewWithTag("params-check:--trim") != null).append('\n');
    ((android.widget.CheckBox) dv.findViewWithTag("params-check:--trim")).setChecked(true);
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText()).append('\n');
    write("s43_cutwav_params", out.toString());
  }

  /** SplitWav in PyJav's Java menu; Params: an input file, two optional part names, the switches. */
  @Test
  public void s44_splitwav_params() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SplitWav.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("/x/song.wav");
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    out.append("input button: ").append(dv.findViewWithTag("params-file:input.wav") != null).append('\n');
    for (String flag : new String[] {"--output_file1", "--output_file2", "--split_time", "--split_lenght", "--trim", "--bpm"}) {
      android.widget.EditText f = (android.widget.EditText) dv.findViewWithTag("params-field:" + flag);
      out.append(flag).append(": ").append(f == null ? "none" : f.getHint()).append('\n');
    }
    ((android.widget.EditText) dv.findViewWithTag("params-field:--output_file1")).setText("first.wav");
    ((android.widget.EditText) dv.findViewWithTag("params-field:--split_lenght")).setText("29.5");
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText()).append('\n');
    write("s44_splitwav_params", out.toString());
  }

  /** Picking CutWav after DrumMidi_CRT: the args field drops DrumMidi's output.mid, and Params does not take it as the input. */
  @Test
  public void s45_program_switch_clears_args() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "DrumMidi_CRT.java");
    TextView args = (TextView) get("pkPyArgs");
    out.append("DrumMidi_CRT args: ").append(args.getText()).append('\n');
    pickFromMenu("Java \u00b7 DrumMidi_CRT.java", "CutWav.java");
    out.append("CutWav args: ").append(args.getText()).append('\n');
    TextView log = (TextView) get("pkPyLog");
    out.append("below Run: ").append(log.getText()).append('\n');
    out.append("long press offers save: ").append(log.performLongClick()).append('\n');
    String stale = app.getCacheDir().getAbsolutePath() + "/pyjav-in/output.mid";
    args.setText(stale);
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    TextView chosen = (TextView) dv.findViewWithTag("params-chosen:input.wav");
    out.append("input chosen: ").append(chosen == null ? "none" : chosen.getText()).append('\n');
    ((android.widget.EditText) dv.findViewWithTag("params-field:--split_time")).setText("01:00");
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("after OK: ").append(args.getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    write("s45_program_switch_clears_args", out.toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>"));
  }

  /** File > Save code and Save code as, for PyJav's code editor; the editor's own long-press menu is left alone. */
  @Test
  public void s46_code_save() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "CutWav.java");
    android.widget.EditText ed = (android.widget.EditText) get("pyEditor");
    TextView fileTab = findText(root(), "File");
    fileTab.performClick();
    idle();
    android.widget.PopupWindow pop = org.robolectric.shadows.ShadowApplication.getInstance().getLatestPopupWindow();
    ViewGroup items = (ViewGroup) pop.getContentView();
    StringBuilder looks = new StringBuilder();
    for (int i = 0; i < items.getChildCount(); i++) {
      TextView t = (TextView) items.getChildAt(i);
      looks.append(i == 0 ? "" : ", ").append(t.getText()).append(t.getCurrentTextColor() == UiKit.FG && t.getAlpha() == 1f ? "" : " (dim)");
    }
    pop.dismiss();
    out.append("File menu: ").append(looks).append('\n');
    out.append("editor menu left alone: ").append(ed.getCustomSelectionActionModeCallback() == null && ed.getCustomInsertionActionModeCallback() == null).append('\n');
    // A bundled program has no file of its own: Save code asks where.
    ed.setText(ed.getText() + "\n// mine");
    fileMenuItem("Save code");
    android.content.Intent ask = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult().intent;
    out.append("Save code: ").append(ask.getAction()).append(' ').append(ask.getType()).append(' ')
        .append(ask.getStringExtra(android.content.Intent.EXTRA_TITLE)).append('\n');
    File file = new File(app.getCacheDir(), "MyCutWav.java");
    org.robolectric.Shadows.shadowOf(app).receiveResult(ask, android.app.Activity.RESULT_OK, new android.content.Intent().setData(android.net.Uri.fromFile(file)));
    idle();
    out.append("written: ").append(file.isFile() && new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).endsWith("// mine")).append('\n');
    // Save code again: straight to that file.
    ed.setText(ed.getText() + "\n// again");
    fileMenuItem("Save code");
    out.append("second Save asks: ").append(org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult() != null).append('\n');
    out.append("second Save written: ").append(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).endsWith("// again")).append('\n');
    // Save code as always asks.
    fileMenuItem("Save code as");
    android.content.Intent again = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult().intent;
    out.append("Save code as: ").append(again.getStringExtra(android.content.Intent.EXTRA_TITLE)).append('\n');
    // A Code menu file is saved under its own name.
    pickFromMenu("Code \u25be", "sogni-client.mjs");
    fileMenuItem("Save code");
    android.content.Intent code = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult().intent;
    out.append("Code file Save: ").append(code.getStringExtra(android.content.Intent.EXTRA_TITLE)).append('\n');
    write("s46_code_save", out.toString());
  }

  /** PyJav's Scripts menu lists the repo's Prompts folder; a script opens like a .prompt file. */
  @Test
  public void s47_scripts_menu() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    out.append("Scripts: ").append(String.join(", ", (String[]) call("list", "Scripts"))).append('\n');
    pickFromMenu("Scripts \u25be", "sogni.prompt");
    TextView ed = (TextView) get("pyEditor");
    out.append("pyName=").append(get("pyName")).append(" run=").append(app.pyJav.pkPromptRun)
        .append(" editorHasScript=").append(ed.getText().toString().startsWith("npm install -g @sogni-ai/sogni-creative-agent-skill@latest")).append('\n');
    out.append("button: ").append(findText(root(), "Scripts \u00b7 sogni.prompt") != null).append('\n');
    pickFromMenu("Java \u25be", "CutWav.java");
    out.append("after Java pick, Scripts button reset: ").append(findText(root(), "Scripts \u25be") != null).append('\n');
    write("s47_scripts_menu", out.toString());
  }

  /** SogniMusic in PyJav: Params lists its switches, the key file gets a file button, and an imported WAV is not passed in. */
  @Test
  public void s48_sogni_music_params() throws Exception {
    StringBuilder out = new StringBuilder();
    short[] pcm = new short[22050];
    call("ingest", AudioIo.encodeWav(pcm, 22050), "Song take.wav", null);
    idle();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniMusic.java");
    TextView args = (TextView) get("pkPyArgs");
    out.append("args after pick: '").append(args.getText()).append("'\n");
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    for (String flag : new String[] {"--prompt", "--genre", "--saveprompt", "--drums_only", "--instruments", "--bpm", "--duration", "--keyscale", "--timesig", "--model", "--lyrics", "--confirm_cost", "--max_cost"}) {
      android.widget.EditText f = (android.widget.EditText) dv.findViewWithTag("params-field:" + flag);
      out.append(flag).append(": ").append(f == null ? "none" : f.getHint()).append('\n');
    }
    TextView keyFile = (TextView) dv.findViewWithTag("params-file:--key_file");
    out.append("--key_file button: ").append(keyFile == null ? "none" : keyFile.getText()).append('\n');
    out.append("input button: ").append(dv.findViewWithTag("params-file:input.wav") != null).append('\n');
    // --genre lists Pulsekit's style database.
    dv.findViewWithTag("params-choose:--genre").performClick();
    idle();
    AlertDialog genres = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("genres: ").append(genres.getListView().getAdapter().getCount()).append(", first ").append(genres.getListView().getAdapter().getItem(0)).append('\n');
    ((android.widget.EditText) genres.getWindow().getDecorView().findViewWithTag("list-search")).setText("deep");
    idle();
    out.append("search deep: ").append(listItems(genres)).append('\n');
    org.robolectric.Shadows.shadowOf(genres).clickOnItem(itemIndex(genres, "Deep House"));
    idle();
    ((android.widget.EditText) dv.findViewWithTag("params-field:--prompt")).setText("funk groove");
    ((android.widget.EditText) dv.findViewWithTag("params-field:--instruments")).setText("slap bass, drums");
    ((android.widget.EditText) dv.findViewWithTag("params-field:--bpm")).setText("124");
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText()).append('\n');
    write("s48_sogni_music_params", out.toString());
  }

  /** The Style database's search box: "rock" lists every rock style; clearing it shows all again. */
  @Test
  public void s49_style_database_search() throws Exception {
    StringBuilder out = new StringBuilder();
    FileSetClicks.promptChangeStyle(app, "f:none", "none");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    android.widget.EditText search = (android.widget.EditText) d.getWindow().getDecorView().findViewWithTag("list-search");
    out.append("all: ").append(d.getListView().getAdapter().getCount()).append('\n');
    int flags = d.getWindow().getAttributes().flags;
    out.append("keyboard allowed: ").append((flags & (android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        | android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)) == 0).append('\n');
    search.setText("rock");
    idle();
    out.append("rock: ").append(listItems(d)).append('\n');
    search.setText("hiphop");
    idle();
    out.append("hiphop: ").append(listItems(d)).append('\n');
    search.setText("");
    idle();
    out.append("cleared: ").append(d.getListView().getAdapter().getCount()).append('\n');
    d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    write("s49_style_database_search", out.toString());
  }

  private static String listItems(AlertDialog d) {
    StringBuilder sb = new StringBuilder();
    android.widget.ListAdapter a = d.getListView().getAdapter();
    for (int i = 0; i < a.getCount(); i++) sb.append(i == 0 ? "" : " | ").append(a.getItem(i));
    return sb.toString();
  }

  /** Drum Midi Settings' Sogni API key file: kept privately, shown masked, passed to SogniMusic as --key_file. */
  @Test
  public void s50_sogni_key_setting() throws Exception {
    StringBuilder out = new StringBuilder();
    DrumMidiSettingsPage page = (DrumMidiSettingsPage) get("drumMidiSettings");
    TextView status = (TextView) root().findViewWithTag("sogni-key-status");
    out.append("before: ").append(status.getText()).append('\n');
    File bad = new File(app.getCacheDir(), "notes.txt");
    Files.write(bad.toPath(), "hello".getBytes(StandardCharsets.UTF_8));
    page.takeKey(android.net.Uri.fromFile(bad));
    out.append("no key in file, still: ").append(status.getText()).append('\n');
    File key = new File(app.getCacheDir(), "my key.txt");
    Files.write(key.toPath(), "# Sogni\nSOGNI_API_KEY=\"abcd1234efgh5678\"\n".getBytes(StandardCharsets.UTF_8));
    page.takeKey(android.net.Uri.fromFile(key));
    out.append("after: ").append(status.getText()).append('\n');
    String path = ApiKeys.path();
    out.append("kept privately: ").append(path != null && path.startsWith(app.getFilesDir().getAbsolutePath())).append('\n');
    out.append("kept text: ").append(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8).trim().replace("abcd1234efgh", "…")).append('\n');
    out.append("findKey: ").append("abcd1234efgh5678".equals(SogniApi.findKey(null))).append('\n');
    java.util.List<String> argv = JavaRun.argvFor("Usage: java SogniMusic [--key_file credentials.txt]", 120, "house", 4, 0, "--prompt x");
    out.append("argv: ").append(String.join(" ", argv).replace(app.getFilesDir().getAbsolutePath(), "<files>")).append('\n');
    root().findViewWithTag("sogni-key-clear").performClick();
    idle();
    out.append("cleared: ").append(status.getText()).append(", argv ").append(JavaRun.argvFor("[--key_file x.txt]", 120, "house", 4, 0, "")).append('\n');
    write("s50_sogni_key_setting", out.toString());
  }

  /** SogniChat: Params has a text-file picker for --file and checkboxes for --thinking and --models; a run's reply is its verdict. */
  @Test
  public void s52_sogni_chat() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniChat.java");
    TextView args = (TextView) get("pkPyArgs");
    out.append("hint: ").append(((TextView) get("pkPyHint")).getText().toString().replace('\n', '|')).append('\n');
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    out.append("--file picker: ").append(((TextView) dv.findViewWithTag("params-file:--file")).getText())
        .append(", label ").append(findText(dv, "File (text, MIDI or picture)  --file") != null).append('\n');
    out.append("--continue picker: ").append(dv.findViewWithTag("params-file:--continue") != null)
        .append(", label ").append(findText(dv, "Continue from saved chat  --continue") != null).append('\n');
    out.append("--thinking box: ").append(dv.findViewWithTag("params-check:--thinking") != null).append('\n');
    out.append("--models box: ").append(dv.findViewWithTag("params-check:--models") != null).append('\n');
    for (String t : new String[] {"--tools", "--run_tools", "--unlimited", "--confirm_cost"}) {
      out.append(t).append(" box: ").append(((android.widget.CheckBox) dv.findViewWithTag("params-check:" + t)).getText()).append('\n');
    }
    out.append("--max_cost hint: ").append(((android.widget.EditText) dv.findViewWithTag("params-field:--max_cost")).getHint()).append('\n');
    out.append("--system label: ").append(findText(dv, "System role/answer  --system") != null)
        .append(", hint: ").append(((android.widget.EditText) dv.findViewWithTag("params-field:--system")).getHint()).append('\n');
    out.append("--prompt hint: ").append(((android.widget.EditText) dv.findViewWithTag("params-field:--prompt")).getHint()).append('\n');
    // Chat model: a list of Sogni's chat models; picking one fills in its id.
    dv.findViewWithTag("params-choose:--model").performClick();
    idle();
    AlertDialog models = (AlertDialog) ShadowDialog.getLatestDialog();
    for (int i = 0; i < models.getListView().getAdapter().getCount(); i++) out.append("model choice: ").append(models.getListView().getAdapter().getItem(i)).append('\n');
    org.robolectric.Shadows.shadowOf(models).clickOnItem(1);
    idle();
    out.append("--model field: ").append(((android.widget.EditText) dv.findViewWithTag("params-field:--model")).getText()).append('\n');
    ((android.widget.EditText) dv.findViewWithTag("params-field:--prompt")).setText("Suggest one fill");
    ((android.widget.EditText) dv.findViewWithTag("params-field:--system")).setText("Answer briefly.");
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText()).append('\n');
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    files.add(new JavaRun.FileOut("sogni-chat-suggest-one-fill.txt", "Try a snare roll.\n".getBytes("UTF-8")));
    app.pyJav.pkShowPyResult(new JavaRun.Result("SogniChat 2026-10-05\nModel qwen3.6-35b-a3b-gguf-iq4xs\nPrompt: Suggest one fill\n\nTry a snare roll.\n\n"
        + "Tokens: 42 in, 7 out\nWrote sogni-chat-suggest-one-fill.txt\nSucceeded: sogni-chat-suggest-one-fill.txt", files, 0));
    idle();
    out.append("verdict: ").append(((TextView) get("pkPyHint")).getText()).append('\n');
    out.append("audio offer: ").append(ShadowDialog.getLatestDialog() != d && ShadowDialog.getLatestDialog() != null && ShadowDialog.getLatestDialog().isShowing()).append('\n');
    write("s52_sogni_chat", out.toString());
  }

  /** A run that made an audio file (SogniMusic's track) offers Play and Make drum MIDI; the track becomes the input. */
  @Test
  public void s51_audio_offer() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniMusic.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("--genre House --drums_only");
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    files.add(new JavaRun.FileOut("sogni_music.mp3", new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0}));
    app.pyJav.pkShowPyResult(new JavaRun.Result("SogniMusic 2026\nMusic: Rock Ballad. Instrumental, only drums\nWorkflow: wf_durable_workflow_abc123\n"
        + "Status: running\nStatus: completed\nWrote sogni_music.mp3 (1 KB)\nSucceeded: sogni_music.mp3", files, 0));
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("dialog: ").append(org.robolectric.Shadows.shadowOf(d).getTitle()).append(" / ")
        .append(d.getButton(DialogInterface.BUTTON_NEUTRAL).getText()).append(", ")
        .append(d.getButton(DialogInterface.BUTTON_POSITIVE).getText()).append(", ")
        .append(d.getButton(DialogInterface.BUTTON_NEGATIVE).getText()).append('\n');
    out.append("input: ").append(app.pyJav.pkAudioInputPath.replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    out.append("SogniMusic args kept: ").append(args.getText()).append('\n');
    d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    // The run's workflow is remembered: Params' --workflow lists it, and picking it fills in the id.
    call("pkOpenParams");
    idle();
    AlertDialog params = (AlertDialog) ShadowDialog.getLatestDialog();
    View pv = params.getWindow().getDecorView();
    out.append("--workflow hint: ").append(((android.widget.EditText) pv.findViewWithTag("params-field:--workflow")).getHint()).append('\n');
    pv.findViewWithTag("params-choose:--workflow").performClick();
    idle();
    AlertDialog runs = (AlertDialog) ShadowDialog.getLatestDialog();
    String label = String.valueOf(runs.getListView().getAdapter().getItem(0));
    out.append("runs: ").append(runs.getListView().getAdapter().getCount()).append(", first ").append(label.substring(label.indexOf(" \u00b7 ") + 3)).append('\n');
    org.robolectric.Shadows.shadowOf(runs).clickOnItem(0);
    idle();
    out.append("--workflow field: ").append(((android.widget.EditText) pv.findViewWithTag("params-field:--workflow")).getText()).append('\n');
    params.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    // DrumMidi_CRT.jar reads the MP3 itself.
    out.append("Java menu: ").append(java.util.Arrays.toString(app.programMenus.list("Java"))).append('\n');
    AudioOffer.makeDrumMidi(app, false);
    idle();
    out.append("Make drum MIDI: ").append(get("pyName")).append(", args ")
        .append(args.getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    out.append("editor: ").append(((TextView) get("pyEditor")).getText().toString().replace("\n", " | ")).append('\n');
    out.append("runs the jar: ").append(get("pkPyBytes") != null && app.programMenus.current() && "".equals(app.programMenus.sourceToRun())).append('\n');
    out.append("hint: ").append(((TextView) get("pkPyHint")).getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>").replace("\n", " | ")).append('\n');
    write("s51_audio_offer", out.toString());
  }

  /** Tap File, then an item in its menu. */
  private void fileMenuItem(String label) throws Exception {
    TextView file = findText(root(), "File");
    if (file == null) throw new AssertionError("no File tab");
    file.performClick();
    idle();
    android.widget.PopupWindow pop = org.robolectric.shadows.ShadowApplication.getInstance().getLatestPopupWindow();
    TextView item = findText(pop.getContentView(), label);
    if (item == null) throw new AssertionError("no File item " + label);
    item.performClick();
    idle();
  }

  private static int itemIndex(AlertDialog menu, String label) {
    for (int i = 0; i < menu.getListView().getAdapter().getCount(); i++) {
      if (label.equals(String.valueOf(menu.getListView().getAdapter().getItem(i)))) return i;
    }
    throw new AssertionError("no menu item " + label);
  }

  private static String texts(ViewGroup g) {
    StringBuilder sb = new StringBuilder();
    if (g == null) return "none";
    for (View v : allViewsStatic(g)) {
      if (v instanceof TextView && ((TextView) v).getText().length() > 0) sb.append(((TextView) v).getText()).append(" / ");
    }
    return sb.toString();
  }

  private static List<View> allViewsStatic(View v) {
    List<View> out = new ArrayList<>();
    out.add(v);
    if (v instanceof ViewGroup) {
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) out.addAll(allViewsStatic(((ViewGroup) v).getChildAt(i)));
    }
    return out;
  }

  /** The MIDI with a text event added at the start of its first track. */
  private static byte[] withText(byte[] midi, String text) {
    byte[] t = text.getBytes(StandardCharsets.UTF_8);
    java.io.ByteArrayOutputStream ev = new java.io.ByteArrayOutputStream();
    ev.write(0);
    ev.write(0xff);
    ev.write(0x01);
    int n = t.length;
    if (n >= 128) ev.write(0x80 | (n >> 7));
    ev.write(n & 0x7f);
    ev.write(t, 0, t.length);
    byte[] e = ev.toByteArray();
    int at = 14;
    int len = ((midi[at + 4] & 0xff) << 24) | ((midi[at + 5] & 0xff) << 16) | ((midi[at + 6] & 0xff) << 8) | (midi[at + 7] & 0xff);
    byte[] out = new byte[midi.length + e.length];
    System.arraycopy(midi, 0, out, 0, at + 8);
    System.arraycopy(e, 0, out, at + 8, e.length);
    System.arraycopy(midi, at + 8, out, at + 8 + e.length, midi.length - at - 8);
    len += e.length;
    out[at + 4] = (byte) (len >> 24);
    out[at + 5] = (byte) (len >> 16);
    out[at + 6] = (byte) (len >> 8);
    out[at + 7] = (byte) len;
    return out;
  }

  private void setField(String name, Object value) throws Exception {
    for (Class<?> c = app.getClass(); c != null; c = c.getSuperclass()) {
      try {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(app, value);
        return;
      } catch (NoSuchFieldException ignored) {
        // look further up
      }
    }
    throw new NoSuchFieldException(name);
  }

  private String timelineCells() throws Exception {
    List<String> cells = new ArrayList<>();
    ViewGroup line = (ViewGroup) get("timeline");
    for (int i = 0; i < line.getChildCount(); i++) {
      ViewGroup cell = (ViewGroup) line.getChildAt(i);
      cells.add(((TextView) cell.getChildAt(0)).getText() + " " + ((TextView) cell.getChildAt(1)).getText());
    }
    return cells.toString();
  }

  private int songBars() throws Exception {
    int bars = 0;
    @SuppressWarnings("unchecked")
    List<Engine.Part> song = (List<Engine.Part>) call("activeSong");
    for (Engine.Part p : song) bars += p.repeats;
    return bars;
  }

  private static int[][] cut(int[][] cells, int from, int to) {
    int[][] out = Engine.emptyCells();
    for (int t = 0; t < cells.length && t < out.length; t++) {
      for (int s = from; s < to && s < cells[t].length; s++) out[t][s] = cells[t][s];
    }
    return out;
  }

  private String underlinedItemsAfterLongPress(TextView chip) {
    chip.performLongClick();
    idle();
    String u = underlinedItems();
    ((AlertDialog) ShadowDialog.getLatestDialog()).dismiss();
    idle();
    return u;
  }

  /** Taps the entry of the open list dialog whose text is label. */
  private void pickItem(String label) {
    android.widget.ListView list = ((AlertDialog) ShadowDialog.getLatestDialog()).getListView();
    for (int i = 0; i < list.getAdapter().getCount(); i++) {
      if (String.valueOf(list.getAdapter().getItem(i)).trim().equals(label)) {
        org.robolectric.Shadows.shadowOf(list).performItemClick(i);
        idle();
        return;
      }
    }
    throw new AssertionError("no " + label + " in the list");
  }

  private String fillPackChips() throws Exception {
    List<String> names = new ArrayList<>();
    collectChips((View) get("importedFillHost"), names);
    return names.toString();
  }

  /** Pattern chips drawn as selected (dark text on the light chip). */
  private String selectedChips() throws Exception {
    int bg = UiKit.BG;
    List<String> names = new ArrayList<>();
    for (View v : allViews((View) get("styleBar"))) {
      if (v instanceof TextView && v.getTag() != null && ((TextView) v).getCurrentTextColor() == bg) names.add(((TextView) v).getText().toString());
    }
    return names.toString();
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
