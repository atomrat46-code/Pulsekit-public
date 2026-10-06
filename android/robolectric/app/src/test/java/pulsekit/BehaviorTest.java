package pulsekit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.VideoView;
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

  /** Picking Ballad 6/8 or Slow Blues 12/8 sets the time signature; a 4/4 style after it sets 4/4 back, a hand-set one stays. */
  @Test
  public void s56_style_meter() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "pattern");
    idle();
    String[] picks = {"ballad68", "rock", "blues128", "hardrock", "slipjig98", "folk"};
    for (String id : picks) {
      app.styleLibrary.loadStyle(id, false);
      idle();
      StringBuilder kick = new StringBuilder();
      for (int i = 0; i < app.steps; i++) kick.append(app.cells[Engine.track("kick")][i] > 0 ? 'X' : '-');
      out.append(id).append(": ").append(app.tsNum).append('/').append(app.tsDen).append(", ").append(app.steps).append(" steps, bpm ")
          .append(app.bpm()).append(", kick ").append(kick).append('\n');
      StringBuilder toms = new StringBuilder();
      for (int i = 0; i < app.steps; i++) {
        boolean tom = app.fillPat[Engine.track("htom")][i] > 0 || app.fillPat[Engine.track("mtom")][i] > 0 || app.fillPat[Engine.track("ltom")][i] > 0;
        toms.append(tom ? 'T' : '-');
      }
      out.append("  fill ").append(app.fillId).append(" toms ").append(toms).append('\n');
    }
    // A time signature set by hand stays when a 4/4 style is picked.
    app.tsNumField.setText("3");
    app.tsNumField.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
    idle();
    app.styleLibrary.loadStyle("rock", false);
    idle();
    out.append("hand-set 3/4, then rock: ").append(app.tsNum).append('/').append(app.tsDen).append(", ").append(app.steps).append(" steps\n");
    out.append("style names: ").append(java.util.Arrays.asList(Engine.styles().get("ballad68").label, Engine.styles().get("blues128").label)).append('\n');
    write("s56_style_meter", out.toString());
  }

  /**
   * SogniChat and a JPEG bigger than the 1024 px Sogni shows the chat model: the app makes a smaller
   * copy, turned upright by its EXIF orientation, and passes it as --seen_copy.
   */
  @Test
  @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
  public void s57_picture_copies() throws Exception {
    StringBuilder out = new StringBuilder();
    out.append("shrinker: ").append(PictureCopies.shrinker == null ? "none" : PictureCopies.shrinker.getClass().getSimpleName()).append('\n');
    android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1300, 700, android.graphics.Bitmap.Config.ARGB_8888);
    java.io.ByteArrayOutputStream jpeg = new java.io.ByteArrayOutputStream();
    b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, jpeg);
    byte[] stored = jpeg.toByteArray();
    java.io.File dir = app.getCacheDir();
    java.io.File wide = new java.io.File(dir, "wide.jpg");
    java.nio.file.Files.write(wide.toPath(), stored);
    // The same picture as a camera stores a photo taken on its side (EXIF orientation 6).
    byte[] exif = {(byte) 0xFF, (byte) 0xE1, 0, 34, 'E', 'x', 'i', 'f', 0, 0, 'M', 'M', 0, 42, 0, 0, 0, 8,
      0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0, 0, 0, 0, 0};
    byte[] photo = new byte[stored.length + exif.length];
    System.arraycopy(stored, 0, photo, 0, 2);
    System.arraycopy(exif, 0, photo, 2, exif.length);
    System.arraycopy(stored, 2, photo, 2 + exif.length, stored.length - 2);
    java.io.File sideways = new java.io.File(dir, "sideways.jpg");
    java.nio.file.Files.write(sideways.toPath(), photo);
    java.io.File small = new java.io.File(dir, "small.jpg");
    android.graphics.Bitmap s = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888);
    java.io.ByteArrayOutputStream sj = new java.io.ByteArrayOutputStream();
    s.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, sj);
    java.nio.file.Files.write(small.toPath(), sj.toByteArray());
    out.append("orientation: ").append(PictureCopies.exifOrientation(photo)).append('\n');
    java.util.List<String> argv = new java.util.ArrayList<String>(java.util.Arrays.asList("--prompt", "Describe",
        "--file", wide.getAbsolutePath(), "--file", sideways.getAbsolutePath(), "--file", small.getAbsolutePath()));
    java.util.List<java.io.File> made = new java.util.ArrayList<java.io.File>();
    java.util.List<String> got = PictureCopies.withCopies(argv, made);
    for (int i = 0; i < got.size(); i++) {
      String a = got.get(i);
      if (i > 0 && "--seen_copy".equals(got.get(i - 1))) {
        int[] px = PictureCopies.jpegSize(java.nio.file.Files.readAllBytes(new java.io.File(a).toPath()));
        out.append(new java.io.File(a).getName()).append(" (").append(px[0]).append('x').append(px[1]).append(") ");
      } else {
        out.append(a.startsWith(dir.getAbsolutePath()) ? new java.io.File(a).getName() : a).append(' ');
      }
    }
    out.append('\n');
    PictureCopies.clean(made);
    boolean left = false;
    for (java.io.File f : made) if (f.exists()) left = true;
    out.append("copies left after the run: ").append(left).append('\n');
    write("s57_picture_copies", out.toString());
  }

  /** MidiDrumGen in the Java menu: Params lists its switches, with a style list and on/off checkboxes. */
  @Test
  public void s55_midi_drum_gen() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "MidiDrumGen.java");
    out.append("hint: ").append(((TextView) get("pkPyHint")).getText().toString().replace('\n', '|')).append('\n');
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    out.append("--style choose: ").append(dv.findViewWithTag("params-choose:--style") != null).append('\n');
    for (String t : new String[] {"--no-fills", "--no-crashes", "--no-half-time", "--saveprompt"}) {
      out.append(t).append(" box: ").append(dv.findViewWithTag("params-check:" + t) != null).append('\n');
    }
    ((android.widget.EditText) dv.findViewWithTag("params-field:--style")).setText("Hard Rock");
    ((android.widget.CheckBox) dv.findViewWithTag("params-check:--no-fills")).setChecked(true);
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(((TextView) get("pkPyArgs")).getText()).append('\n');
    write("s55_midi_drum_gen", out.toString());
  }

  /** A run that made a picture (SogniChat's tool result) shows it in a Picture ready dialog. */
  @Test
  public void s54_picture_offer() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniChat.java");
    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888);
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png);
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    files.add(new JavaRun.FileOut("kit-ideas.txt", "SogniChat conversation\n".getBytes("UTF-8")));
    files.add(new JavaRun.FileOut("kit-ideas-1.png", png.toByteArray()));
    app.pyJav.pkShowPyResult(new JavaRun.Result("SogniChat 2026-10-06\nWrote kit-ideas-1.png (1 KB)\nSucceeded: kit-ideas.txt, kit-ideas-1.png", files, 0));
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("dialog: ").append(org.robolectric.Shadows.shadowOf(d).getTitle()).append(", ")
        .append(d.getButton(DialogInterface.BUTTON_POSITIVE).getText()).append('\n');
    out.append("shows picture: ").append(d.getWindow().getDecorView().findViewWithTag("picture-offer:kit-ideas-1.png") != null).append('\n');
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    // A run with no picture shows none.
    files.clear();
    files.add(new JavaRun.FileOut("reply.txt", "hi\n".getBytes("UTF-8")));
    app.pyJav.pkShowPyResult(new JavaRun.Result("SogniChat 2026-10-06\nSucceeded: reply.txt", files, 0));
    idle();
    out.append("after a text-only run: ").append(ShadowDialog.getLatestDialog() == d || !ShadowDialog.getLatestDialog().isShowing() ? "no dialog" : "a dialog").append('\n');
    write("s54_picture_offer", out.toString());
  }

  /** A run that made a video (SogniVideo's clip) shows a Video ready dialog: Play/Pause, Stop, Mute and a volume slider. */
  @Test
  public void s58_video_offer() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniVideo.java");
    // Params: --saveprompt is a checkbox, as for SogniMusic.
    call("pkOpenParams");
    idle();
    AlertDialog params = (AlertDialog) ShadowDialog.getLatestDialog();
    android.widget.CheckBox save = (android.widget.CheckBox) params.getWindow().getDecorView().findViewWithTag("params-check:--saveprompt");
    out.append("saveprompt checkbox: ").append(save == null ? "none" : save.getText()).append('\n');
    // Browse DB next to Choose file: greyed while the prompt library keeps no reference files.
    android.widget.Button db = (android.widget.Button) params.getWindow().getDecorView().findViewWithTag("params-db:--image");
    out.append("Browse DB for --image: ").append(db == null ? "none" : db.isEnabled() ? "enabled" : "greyed").append('\n');
    save.setChecked(true);
    params.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(((TextView) get("pkPyArgs")).getText()).append('\n');
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    files.add(new JavaRun.FileOut("shortsuli.mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'}));
    app.pyJav.pkShowPyResult(new JavaRun.Result("SogniVideo 2026-10-06\nWrote shortsuli.mp4 (0 KB)\nSucceeded: shortsuli.mp4", files, 0));
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    out.append("dialog: ").append(org.robolectric.Shadows.shadowOf(d).getTitle()).append(", ").append(d.getButton(DialogInterface.BUTTON_POSITIVE).getText()).append('\n');
    VideoView view = (VideoView) dv.findViewWithTag("video-offer:view");
    out.append("plays: ").append(view == null ? "no view" : new java.io.File(org.robolectric.Shadows.shadowOf(view).getVideoPath()).getName()).append('\n');
    TextView play = (TextView) dv.findViewWithTag("video-offer:play");
    TextView mute = (TextView) dv.findViewWithTag("video-offer:mute");
    TextView level = (TextView) dv.findViewWithTag("video-offer:level");
    android.widget.SeekBar volume = (android.widget.SeekBar) dv.findViewWithTag("video-offer:volume");
    VideoOffer.Player p = VideoOffer.last;
    out.append("start: ").append(play.getText()).append(", ").append(mute.getText()).append(", volume ").append(level.getText()).append('\n');
    play.performClick();
    int keep = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    out.append("Play: ").append(play.getText()).append(", playing ").append(p.playing).append(", screen kept on ").append((d.getWindow().getAttributes().flags & keep) != 0).append('\n');
    play.performClick();
    out.append("Pause: ").append(play.getText()).append(", playing ").append(p.playing).append(", screen kept on ").append((d.getWindow().getAttributes().flags & keep) != 0).append('\n');
    play.performClick();
    dv.findViewWithTag("video-offer:stop").performClick();
    out.append("Stop: ").append(play.getText()).append(", playing ").append(p.playing).append('\n');
    mute.performClick();
    out.append("Mute: ").append(mute.getText()).append(", ").append(level.getText()).append(", gain ").append(p.gain()).append('\n');
    volume.setProgress(40);
    out.append("volume 40 while muted: ").append(level.getText()).append(", gain ").append(p.gain()).append('\n');
    mute.performClick();
    out.append("Unmute: ").append(mute.getText()).append(", ").append(level.getText()).append(", gain ").append(p.gain()).append('\n');
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("closed: ").append(!d.isShowing()).append(", playing ").append(p.playing).append(", app screen kept on ").append((app.getWindow().getAttributes().flags & keep) != 0).append('\n');
    write("s58_video_offer", out.toString());
  }

  /**
   * SogniVideo --saveprompt: the sheet the run saved goes into the prompt library with its first
   * frame picture as reference file 1, the clip as result file and the result text; opening the
   * sheet loads the picture back.
   */
  @Test
  public void s59_prompt_keep() throws Exception {
    // The prompt library encrypts with the Android keystore, which Robolectric has not: a stand-in in memory.
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniVideo.java");
    java.io.File in = new java.io.File(app.getCacheDir(), "pyjav-in");
    in.mkdirs();
    java.io.File garden = new java.io.File(in, "garden.png");
    java.nio.file.Files.write(garden.toPath(), new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3, 4});
    String sheet = "PKPROMPT1\nsogni-video-she-walks\n\n\ngarden.png\n\nCategory: video\nModel: Sogni minimax-h3-fasth3-i2v-turbo\n"
        + "Reference file 1: garden.png\nReference file 2: \nType: ai\n---\nShe walks slowly through the garden\n\n"
        + "Duration: 5 s. Resolution: 768p. First frame: garden.png.\n\nResult file: sogni-video-she-walks.mp4\nResult text:\nNote: workflow partial_failure\n";
    byte[] clip = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'};
    for (int run = 1; run <= 2; run++) {
      java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
      files.add(new JavaRun.FileOut("sogni-video-she-walks.prompt", sheet.getBytes("UTF-8")));
      files.add(new JavaRun.FileOut("sogni-video-she-walks.mp4", clip));
      app.pyJav.pkLastArgv = new java.util.ArrayList<String>(java.util.Arrays.asList("--prompt", "She walks", "--image", garden.getAbsolutePath(), "--saveprompt"));
      app.pyJav.pkShowPyResult(new JavaRun.Result("SogniVideo 2026-10-06\nSaved prompt sogni-video-she-walks.prompt\nWrote sogni-video-she-walks.mp4 (0 KB)\n"
          + "Succeeded: sogni-video-she-walks.mp4", files, 0));
      idle();
      AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
      if (d != null && d.isShowing()) d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
      idle();
      for (String line : ((TextView) get("pkPyLog")).getText().toString().split("\n")) {
        if (line.startsWith("Prompt library")) out.append("run ").append(run).append(" log: ").append(line).append('\n');
      }
    }
    PromptVault vault = PromptVault.open(app.getFilesDir());
    for (PromptVault.Category c : vault.categories()) {
      for (PromptVault.Prompt p : vault.prompts(c.id)) {
        if (!p.title.startsWith("sogni-video")) continue;
        out.append("library: ").append(c.name).append(" / ").append(p.title).append(", ").append(vault.versions(p.id).size()).append(" versions\n");
        PromptVault.Version v = vault.versions(p.id).get(vault.versions(p.id).size() - 1);
        out.append("  model ").append(v.model).append(", type ").append(v.codeType).append('\n');
        out.append("  body: ").append(v.body.replace("\n", "|")).append('\n');
        out.append("  reference 1: ").append(v.ref1Name).append(" (").append(v.ref1 == null ? 0 : v.ref1.length).append(" bytes), reference 2: \"")
            .append(v.ref2Name).append("\"\n");
        out.append("  result: ").append(v.resultName).append(" (").append(v.result == null ? 0 : v.result.length).append(" bytes)\n");
        out.append("  result text: ").append(v.resultText).append('\n');
      }
    }
    // Opening the sheet loads its picture as reference file 1.
    app.pyJav.pkOpenPromptText("sogni-video-she-walks.prompt", sheet);
    idle();
    String ref = app.pyJav.pkRef1Path;
    out.append("opened: reference file 1 ").append(ref == null || ref.length() == 0 ? "none"
        : new java.io.File(ref).getName() + " (" + new java.io.File(ref).length() + " bytes)").append('\n');
    // Params: Browse DB lists the library's reference files (the picture once, though two versions keep it).
    garden.delete();
    pickFromMenu("Java \u25be", "SogniVideo.java");
    call("pkOpenParams");
    idle();
    AlertDialog params = (AlertDialog) ShadowDialog.getLatestDialog();
    View pv = params.getWindow().getDecorView();
    android.widget.Button db = (android.widget.Button) pv.findViewWithTag("params-db:--image");
    out.append("Browse DB for --image: ").append(db.isEnabled() ? "enabled" : "greyed").append(", for --end_image: ")
        .append(((android.widget.Button) pv.findViewWithTag("params-db:--end_image")).isEnabled() ? "enabled" : "greyed").append('\n');
    db.performClick();
    idle();
    AlertDialog refs = (AlertDialog) ShadowDialog.getLatestDialog();
    View rv = refs.getWindow().getDecorView();
    out.append("browser: ").append(org.robolectric.Shadows.shadowOf(refs).getTitle()).append(", garden.png card ")
        .append(rv.findViewWithTag("refs-pick:garden.png") != null).append('\n');
    rv.findViewWithTag("refs-pick:garden.png").performClick();
    idle();
    out.append("browser closed: ").append(!refs.isShowing()).append(", row shows: ")
        .append(((TextView) pv.findViewWithTag("params-chosen:--image")).getText()).append('\n');
    params.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    String args = ((TextView) get("pkPyArgs")).getText().toString();
    java.io.File picked = new java.io.File(args.replaceAll(".*--image \"?([^\"]+?)\"?( --.*)?$", "$1"));
    out.append("args: ").append(args.replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    out.append("picked file: ").append(picked.getName()).append(", ").append(picked.length()).append(" bytes\n");
    // Prompts page: Result files, beside Ref files, shows the stored results (the clip) as Ref files shows references.
    android.widget.LinearLayout pane = PromptSheet.create(app);
    TextView resultsButton = findText(pane, "Result files");
    out.append("prompts page: Ref files ").append(findText(pane, "Ref files") != null).append(", Result files ").append(resultsButton != null).append('\n');
    resultsButton.performClick();
    idle();
    out.append("result gallery: ").append(findText(pane, "RESULT FILES") != null ? "RESULT FILES" : "?").append(", clip card ")
        .append(findText(pane, "sogni-video-she-walks.mp4") != null).append(", picture card ").append(findText(pane, "garden.png") != null).append('\n');
    findText(pane, "Back").performClick();
    idle();
    findText(pane, "Ref files").performClick();
    idle();
    out.append("ref gallery: ").append(findText(pane, "REFERENCE FILES") != null ? "REFERENCE FILES" : "?").append(", picture card ")
        .append(findText(pane, "garden.png") != null).append(", clip card ").append(findText(pane, "sogni-video-she-walks.mp4") != null).append('\n');
    findText(pane, "Back").performClick();
    idle();
    // Ref files gallery: a sound card shows ▶ and plays in place when tapped; a second tap stops it.
    org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(ds -> new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(1000, 0));
    byte[] loopWav = {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
    byte[] beat = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k', 0, 0, 0, 12,
      0, (byte) 0x99, 36, 100, 96, (byte) 0x89, 36, 0, 0, (byte) 0xFF, 0x2F, 0};
    long cat = vault.categories().get(0).id;
    long sounds = vault.addPrompt(cat, "Drum sounds");
    vault.addVersion(sounds, "Drum sounds", "", "Loops", "", "loop.wav", loopWav, "beat.mid", beat, "", null, "ai", "");
    pane = PromptSheet.create(app);
    findText(pane, "Ref files").performClick();
    idle();
    out.append("ref cards: loop.wav mark ").append(findText(pane, "\u25b6 WAV") != null).append(", beat.mid mark ").append(findText(pane, "\u25b6 MID") != null)
        .append(", garden.png mark ").append(findText(pane, "\u25b6 PNG") != null ? "\u25b6 (wrong)" : "no \u25b6").append('\n');
    java.lang.reflect.Field gp = PromptSheet.class.getDeclaredField("previewFile");
    gp.setAccessible(true);
    ((View) findText(pane, "loop.wav").getParent()).performClick();
    idle();
    out.append("tap loop.wav: ").append(findText(pane, "\u25a0 playing") != null ? "\u25a0 playing" : "not playing")
        .append(", player file ").append(gp.get(null) == null ? "none" : ((java.io.File) gp.get(null)).getName()).append('\n');
    ((View) findText(pane, "beat.mid").getParent()).performClick();
    idle();
    out.append("tap beat.mid: loop.wav back to ").append(findText(pane, "\u25b6 WAV") != null ? "\u25b6 WAV" : "?").append(", beat.mid ")
        .append(findText(pane, "\u25a0 playing") != null ? "\u25a0 playing" : "not playing").append(", player file ")
        .append(gp.get(null) == null ? "none" : ((java.io.File) gp.get(null)).getName()).append(" (the kit's sounds)\n");
    ((View) findText(pane, "beat.mid").getParent()).performClick();
    idle();
    out.append("tap beat.mid again: ").append(findText(pane, "\u25b6 MID") != null ? "stopped, \u25b6 MID" : "?").append(", player file ")
        .append(gp.get(null) == null ? "none" : "still there").append('\n');
    ((View) findText(pane, "garden.png").getParent()).performClick();
    idle();
    out.append("tap garden.png: ").append(findText(pane, "\u25a0 playing") != null ? "playing (wrong)" : "nothing plays").append('\n');
    findText(pane, "Back").performClick();
    idle();
    // Preview: sound files play (WAV, MP3, and MIDI with the phone's General MIDI sounds).
    java.lang.reflect.Method open = PromptSheet.class.getDeclaredMethod("openPreview", android.app.Activity.class, String.class, byte[].class, int.class);
    open.setAccessible(true);
    byte[] midi = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k', 0, 0, 0, 4, 0, (byte) 0xFF, 0x2F, 0};
    byte[] wav = {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
    // Robolectric's player plays nothing; it is told every file is a one-second sound.
    org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(ds -> new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(1000, 0));
    // A bar of kick, snare and hats on the drum channel: played with the kit's sounds (an imported SoundFont).
    byte[] groove = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k', 0, 0, 0, 36,
      0, (byte) 0x99, 36, 100, 0, (byte) 0x99, 42, 90, 48, (byte) 0x89, 42, 0, 0, (byte) 0x99, 38, 100, 0, (byte) 0x99, 42, 90,
      48, (byte) 0x89, 36, 0, 0, (byte) 0x89, 38, 0, (byte) 0x82, 0x40, (byte) 0xFF, 0x2F, 0};
    String[][] cases = {{"groove.mid", "groove"}, {"empty.mid", "midi"}, {"drums", "groove"}, {"loop.wav", "wav"}, {"track.mp3", "mp3"}};
    for (String[] c : cases) {
      byte[] bytes = c[1].equals("groove") ? groove : c[1].equals("midi") ? midi : c[1].equals("wav") ? wav : new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, 0, 0};
      open.invoke(null, app, c[0], bytes, 3);
      idle();
      java.lang.reflect.Field pf = PromptSheet.class.getDeclaredField("previewFile");
      pf.setAccessible(true);
      java.io.File played = (java.io.File) pf.get(null);
      out.append("preview ").append(c[0]).append(": Play ").append(findText(pane, "Play") != null).append(", Stop ").append(findText(pane, "Stop") != null)
          .append(", kit note ").append(findText(pane, "MIDI drums, played with Pulsekit's kit sounds (your imported SoundFont, if any).") != null)
          .append(", GM note ").append(findText(pane, "MIDI, played with the phone's General MIDI sounds (it has no drums for Pulsekit's kit).") != null)
          .append(", player file .").append(played == null ? "?" : played.getName().substring(played.getName().lastIndexOf('.') + 1))
          .append(played != null && played.getName().endsWith(".wav") && c[1].equals("groove") ? " (" + (played.length() > 1000 ? "rendered audio" : "empty") + ")" : "")
          .append(", no-preview text ").append(findText(pane, "No preview for this file. Preview works for text, image, video, and sound (including MIDI).") != null).append('\n');
      findText(pane, "Back").performClick();
      idle();
    }
    write("s59_prompt_keep", out.toString());
  }

  /** An "AndroidKeyStore" for the tests: keys kept in memory, made by a plain AES generator. */
  public static final class FakeKeyStoreProvider extends java.security.Provider {
    static final java.util.Map<String, java.security.Key> KEYS = new java.util.HashMap<String, java.security.Key>();

    public FakeKeyStoreProvider() {
      // Named as the real one: the vault asks KeyGenerator.getInstance("AES", "AndroidKeyStore").
      super("AndroidKeyStore", 1.0, "Android keystore stand-in for the tests");
      put("KeyStore.AndroidKeyStore", FakeStore.class.getName());
      put("KeyGenerator.AES", FakeGenerator.class.getName());
    }
  }

  public static final class FakeGenerator extends javax.crypto.KeyGeneratorSpi {
    private String alias = "key";

    @Override protected void engineInit(java.security.SecureRandom r) {}

    @Override protected void engineInit(java.security.spec.AlgorithmParameterSpec spec, java.security.SecureRandom r) {
      if (spec instanceof android.security.keystore.KeyGenParameterSpec) alias = ((android.security.keystore.KeyGenParameterSpec) spec).getKeystoreAlias();
    }

    @Override protected void engineInit(int size, java.security.SecureRandom r) {}

    @Override protected javax.crypto.SecretKey engineGenerateKey() {
      byte[] k = new byte[32];
      new java.security.SecureRandom().nextBytes(k);
      javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(k, "AES");
      FakeKeyStoreProvider.KEYS.put(alias, key);
      return key;
    }
  }

  public static final class FakeStore extends java.security.KeyStoreSpi {
    @Override public java.security.Key engineGetKey(String a, char[] p) { return FakeKeyStoreProvider.KEYS.get(a); }
    @Override public java.security.cert.Certificate[] engineGetCertificateChain(String a) { return null; }
    @Override public java.security.cert.Certificate engineGetCertificate(String a) { return null; }
    @Override public java.util.Date engineGetCreationDate(String a) { return new java.util.Date(); }
    @Override public void engineSetKeyEntry(String a, java.security.Key k, char[] p, java.security.cert.Certificate[] c) { FakeKeyStoreProvider.KEYS.put(a, k); }
    @Override public void engineSetKeyEntry(String a, byte[] k, java.security.cert.Certificate[] c) {}
    @Override public void engineSetCertificateEntry(String a, java.security.cert.Certificate c) {}
    @Override public void engineDeleteEntry(String a) { FakeKeyStoreProvider.KEYS.remove(a); }
    @Override public java.util.Enumeration<String> engineAliases() { return java.util.Collections.enumeration(FakeKeyStoreProvider.KEYS.keySet()); }
    @Override public boolean engineContainsAlias(String a) { return FakeKeyStoreProvider.KEYS.containsKey(a); }
    @Override public int engineSize() { return FakeKeyStoreProvider.KEYS.size(); }
    @Override public boolean engineIsKeyEntry(String a) { return FakeKeyStoreProvider.KEYS.containsKey(a); }
    @Override public boolean engineIsCertificateEntry(String a) { return false; }
    @Override public String engineGetCertificateAlias(java.security.cert.Certificate c) { return null; }
    @Override public void engineStore(java.io.OutputStream o, char[] p) {}
    @Override public void engineLoad(java.io.InputStream i, char[] p) {}
  }

  /**
   * Drum Midi Settings: "Save MidiDrumGen output file into DB". Off (the default), a MidiDrumGen run
   * stores nothing; on, its MIDI goes into the prompt library (Music / MidiDrumGen) as a result file.
   */
  @Test
  public void s60_midi_drum_gen_db() throws Exception {
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    android.widget.CheckBox box = null;
    View item = root().findViewWithTag("midi-drum-gen-db");
    if (item instanceof android.view.ViewGroup) {
      for (int i = 0; i < ((android.view.ViewGroup) item).getChildCount(); i++) {
        if (((android.view.ViewGroup) item).getChildAt(i) instanceof android.widget.CheckBox) box = (android.widget.CheckBox) ((android.view.ViewGroup) item).getChildAt(i);
      }
    }
    out.append("checkbox: ").append(box == null ? "none" : box.getText() + (box.isChecked() ? " (on)" : " (off)")).append('\n');
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "MidiDrumGen.java");
    byte[] mid = {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k', 0, 0, 0, 12,
      0, (byte) 0x99, 36, 100, 96, (byte) 0x89, 36, 0, 0, (byte) 0xFF, 0x2F, 0};
    for (int run = 1; run <= 3; run++) {
      if (run == 2) box.setChecked(true);
      java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
      files.add(new JavaRun.FileOut("hard_rock_" + run + ".mid", mid));
      app.pyJav.pkLastArgv = new java.util.ArrayList<String>(java.util.Arrays.asList("--style", "Hard Rock", "--bars", "4",
          new java.io.File(app.getCacheDir(), "pyjav-in/hard_rock_" + run + ".mid").getAbsolutePath()));
      org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(ds -> new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(1000, 0));
      app.pyJav.pkShowPyResult(new JavaRun.Result("Wrote hard_rock_" + run + ".mid (style=Hard Rock base=hard_rock tempo=120 timesig=4/4 bars=4)", files, 0));
      idle();
      AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
      if (run == 1) {
        // MIDI ready: the groove with the kit's sounds, Play / Stop and Close.
        out.append("dialog: ").append(org.robolectric.Shadows.shadowOf(d).getTitle()).append(" / ")
            .append(org.robolectric.Shadows.shadowOf(d).getMessage().toString().replace("\n", "|")).append('\n');
        android.widget.Button play = d.getButton(DialogInterface.BUTTON_NEUTRAL);
        play.performClick();
        idle();
        out.append("  Play: button ").append(play.getText()).append(", playing ").append(MidiOffer.playing()).append('\n');
        play.performClick();
        idle();
        out.append("  Stop: button ").append(play.getText()).append(", playing ").append(MidiOffer.playing()).append('\n');
        play.performClick();
        idle();
        d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idle();
        out.append("  Close: shown ").append(d.isShowing()).append(", playing ").append(MidiOffer.playing()).append('\n');
      }
      if (d != null && d.isShowing()) d.dismiss();
      idle();
      String library = "";
      for (String line : ((TextView) get("pkPyLog")).getText().toString().split("\n")) if (line.startsWith("Prompt library")) library = line;
      out.append("run ").append(run).append(" (setting ").append(box.isChecked() ? "on" : "off").append("): ").append(library.length() == 0 ? "not stored" : library).append('\n');
    }
    PromptVault vault = PromptVault.open(app.getFilesDir());
    for (PromptVault.Category c : vault.categories()) {
      for (PromptVault.Prompt p : vault.prompts(c.id)) {
        if (!p.title.equals("MidiDrumGen")) continue;
        out.append("library: ").append(c.name).append(" / ").append(p.title).append(", ").append(vault.versions(p.id).size()).append(" versions\n");
        for (PromptVault.Version v : vault.versions(p.id)) {
          out.append("  text: ").append(v.body).append("\n  result: ").append(v.resultName).append(" (").append(v.result == null ? 0 : v.result.length)
              .append(" bytes), result text: ").append(v.resultText).append('\n');
        }
      }
    }
    out.append("kept: ").append(app.getSharedPreferences(DrumMidiSettingsPage.PREFS, 0).getBoolean(DrumMidiSettingsPage.GEN_TO_DB, false)).append('\n');
    // --saveprompt: the run's sheet goes into the prompt library as its own prompt, with the MIDI as result file;
    // with Save MidiDrumGen output file into DB on too, the MIDI is not stored a second time.
    String sheet = "PKPROMPT1\nhard_rock_4\n\n\n\n\nCategory: Music\nModel: MidiDrumGen\nReference file 1: \nReference file 2: \n---\n"
        + "MidiDrumGen --style hard_rock --tempo 120 --bars 4\n\nStyle: hard_rock (base hard_rock). Tempo: 120 BPM.\n\nResult file: hard_rock_4.mid\n";
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    files.add(new JavaRun.FileOut("hard_rock_4.mid", mid));
    files.add(new JavaRun.FileOut("hard_rock_4.prompt", sheet.getBytes("UTF-8")));
    app.pyJav.pkShowPyResult(new JavaRun.Result("Wrote hard_rock_4.mid (style=hard_rock)\nSaved prompt hard_rock_4.prompt", files, 0));
    idle();
    AlertDialog shown = (AlertDialog) ShadowDialog.getLatestDialog();
    if (shown != null && shown.isShowing()) shown.dismiss();
    for (String line : ((TextView) get("pkPyLog")).getText().toString().split("\n")) if (line.startsWith("Prompt library")) out.append("saveprompt run: ").append(line).append('\n');
    // Opened again: the run stored it through its own handle.
    PromptVault after = PromptVault.open(app.getFilesDir());
    long music = 0;
    for (PromptVault.Category c : after.categories()) if ("Music".equals(c.name)) music = c.id;
    for (PromptVault.Prompt p : after.prompts(music)) {
      if (!p.title.equals("hard_rock_4")) continue;
      PromptVault.Version v = after.versions(p.id).get(0);
      out.append("  sheet in library: ").append(p.title).append(", model ").append(v.model).append(", result ").append(v.resultName).append(" (")
          .append(v.result == null ? 0 : v.result.length).append(" bytes)\n");
    }
    for (PromptVault.Prompt p : after.prompts(music)) {
      if (p.title.equals("MidiDrumGen")) out.append("  MidiDrumGen prompt: ").append(after.versions(p.id).size()).append(" versions (setting ")
          .append(box.isChecked() ? "on" : "off").append(")\n");
    }
    write("s60_midi_drum_gen_db", out.toString());
  }

  /**
   * File > Import as Ref file / Import as Result file: the picked file goes into the prompt library
   * after OK (Cancel adds nothing), and shows in Ref files or Result files. The library is one copy
   * in memory, so the Prompts page sees what was imported, and its own saves keep it.
   */
  @Test
  public void s61_db_import() throws Exception {
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    android.widget.LinearLayout pane = PromptSheet.create(app);
    java.io.File dir = new java.io.File(app.getCacheDir(), "picked");
    dir.mkdirs();
    java.io.File wav = new java.io.File(dir, "loop.wav");
    java.nio.file.Files.write(wav.toPath(), new byte[] {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
    java.io.File clip = new java.io.File(dir, "clip.mp4");
    java.nio.file.Files.write(clip.toPath(), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});
    String[][] runs = {{"Import as Ref file", "loop.wav", "OK"}, {"Import as Result file", "clip.mp4", "Cancel"}, {"Import as Result file", "clip.mp4", "OK"}};
    for (String[] r : runs) {
      fileMenuItem(r[0]);
      org.robolectric.shadows.ShadowActivity.IntentForResult picker = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult();
      out.append(r[0]).append(": picker ").append(picker == null ? "none" : picker.intent.getAction() + " (request " + picker.requestCode + ")").append('\n');
      app.onActivityResult(picker.requestCode, -1, new android.content.Intent().setData(android.net.Uri.fromFile(new java.io.File(dir, r[1]))));
      idle();
      AlertDialog ask = (AlertDialog) ShadowDialog.getLatestDialog();
      out.append("  dialog: ").append(org.robolectric.Shadows.shadowOf(ask).getTitle()).append(" / ")
          .append(org.robolectric.Shadows.shadowOf(ask).getMessage().toString().replace("\n", "|")).append('\n');
      ask.getButton(r[2].equals("OK") ? DialogInterface.BUTTON_POSITIVE : DialogInterface.BUTTON_NEGATIVE).performClick();
      idle();
      PromptVault v = PromptVault.open(app.getFilesDir());
      StringBuilder refs = new StringBuilder(), results = new StringBuilder();
      for (PromptVault.StoredFile f : v.referenceFiles()) refs.append(f.name).append(" (").append(f.promptTitle).append(") ");
      for (PromptVault.StoredFile f : v.resultFiles()) results.append(f.name).append(" (").append(f.promptTitle).append(") ");
      out.append("  ref files: ").append(refs.toString().trim()).append(" | result files: ").append(results.toString().trim()).append('\n');
    }
    // The Prompts page, made before the imports, shares the library: its galleries list them.
    java.lang.reflect.Field pageVault = PromptSheet.class.getDeclaredField("vault");
    pageVault.setAccessible(true);
    out.append("Prompts page library is the shared one: ").append(pageVault.get(null) == PromptVault.open(app.getFilesDir())).append('\n');
    findText(pane, "Result files").performClick();
    idle();
    out.append("Result files gallery: clip.mp4 ").append(findText(pane, "clip.mp4") != null).append('\n');
    findText(pane, "Back").performClick();
    idle();
    findText(pane, "Ref files").performClick();
    idle();
    out.append("Ref files gallery: loop.wav ").append(findText(pane, "loop.wav") != null).append(", clip.mp4 ").append(findText(pane, "clip.mp4") != null).append('\n');
    findText(pane, "Back").performClick();
    idle();
    // A save from the Prompts page keeps the imports (before, it wrote back the copy it read at start).
    ((PromptVault) pageVault.get(null)).addCategory("Drums");
    java.lang.reflect.Field shared = PromptVault.class.getDeclaredField("shared");
    shared.setAccessible(true);
    shared.set(null, null);
    PromptVault fromDisk = PromptVault.open(app.getFilesDir());
    StringBuilder kept = new StringBuilder();
    for (PromptVault.StoredFile f : fromDisk.referenceFiles()) kept.append(f.name).append(' ');
    for (PromptVault.StoredFile f : fromDisk.resultFiles()) kept.append(f.name).append(' ');
    boolean drums = false;
    for (PromptVault.Category c : fromDisk.categories()) if ("Drums".equals(c.name)) drums = true;
    out.append("read from disk after a Prompts page save: ").append(kept.toString().trim()).append(", category Drums ").append(drums).append('\n');
    // Browse DB on SogniVideo's picture rows: Ref files or Result files (a picture a SogniChat tool made, kept as a result).
    DbImport.store(app, "krea.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 9, 9, 9, 9}, true);
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniVideo.java");
    call("pkOpenParams");
    idle();
    AlertDialog params = (AlertDialog) ShadowDialog.getLatestDialog();
    View pv = params.getWindow().getDecorView();
    ((android.widget.Button) pv.findViewWithTag("params-db:--image")).performClick();
    idle();
    AlertDialog refs = (AlertDialog) ShadowDialog.getLatestDialog();
    View rv = refs.getWindow().getDecorView();
    out.append("Browse DB opens: ").append(org.robolectric.Shadows.shadowOf(refs).getTitle()).append(", switch ")
        .append(((TextView) rv.findViewWithTag("refs-kind:refs")).getText()).append(" / ").append(((TextView) rv.findViewWithTag("refs-kind:results")).getText())
        .append(", loop.wav card ").append(rv.findViewWithTag("refs-pick:loop.wav") != null).append('\n');
    rv.findViewWithTag("refs-kind:results").performClick();
    idle();
    AlertDialog results = (AlertDialog) ShadowDialog.getLatestDialog();
    View sv = results.getWindow().getDecorView();
    out.append("switched: ").append(org.robolectric.Shadows.shadowOf(results).getTitle()).append(", first browser closed ").append(!refs.isShowing())
        .append(", krea.png card ").append(sv.findViewWithTag("refs-pick:krea.png") != null).append(", clip.mp4 card ").append(sv.findViewWithTag("refs-pick:clip.mp4") != null).append('\n');
    sv.findViewWithTag("refs-pick:krea.png").performClick();
    idle();
    out.append("picked: ").append(((TextView) pv.findViewWithTag("params-chosen:--image")).getText()).append('\n');
    params.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(((TextView) get("pkPyArgs")).getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    // SogniChat --file: the same Browse DB (a stored picture, sound or text sent to the chat).
    // The Java menu's button names the program loaded from it.
    pickFromMenu("Java \u00b7 SogniVideo.java", "SogniChat.java");
    call("pkOpenParams");
    idle();
    AlertDialog chat = (AlertDialog) ShadowDialog.getLatestDialog();
    View cv = chat.getWindow().getDecorView();
    android.widget.Button chatDb = (android.widget.Button) cv.findViewWithTag("params-db:--file");
    out.append("SogniChat --file Browse DB: ").append(chatDb == null ? "none" : chatDb.isEnabled() ? "enabled" : "greyed").append('\n');
    chatDb.performClick();
    idle();
    View bv = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
    bv.findViewWithTag("refs-pick:loop.wav").performClick();
    idle();
    out.append("SogniChat picked: ").append(((TextView) cv.findViewWithTag("params-chosen:--file")).getText()).append('\n');
    chat.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("SogniChat args: ").append(((TextView) get("pkPyArgs")).getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    // DrumMidi_CRT's audio input: Browse DB lists sound files only (loop.wav; not the picture or the clip).
    pickFromMenu("Java \u00b7 SogniChat.java", "DrumMidi_CRT.java");
    call("pkOpenParams");
    idle();
    AlertDialog dm = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = dm.getWindow().getDecorView();
    android.widget.Button dmDb = (android.widget.Button) dv.findViewWithTag("params-db:input.wav");
    out.append("DrumMidi_CRT input Browse DB: ").append(dmDb == null ? "none" : dmDb.isEnabled() ? "enabled" : "greyed").append('\n');
    dmDb.performClick();
    idle();
    AlertDialog sb = (AlertDialog) ShadowDialog.getLatestDialog();
    View sv2 = sb.getWindow().getDecorView();
    out.append("  sound browser: ").append(((TextView) sv2.findViewWithTag("refs-kind:refs")).getText()).append(" / ")
        .append(((TextView) sv2.findViewWithTag("refs-kind:results")).getText()).append(", loop.wav ").append(sv2.findViewWithTag("refs-pick:loop.wav") != null)
        .append(", krea.png ").append(sv2.findViewWithTag("refs-pick:krea.png") != null).append('\n');
    sv2.findViewWithTag("refs-pick:loop.wav").performClick();
    idle();
    out.append("  picked: ").append(((TextView) dv.findViewWithTag("params-chosen:input.wav")).getText()).append('\n');
    dm.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("  args: ").append(((TextView) get("pkPyArgs")).getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    // CompareHits in PyJav: its input.wav row gets the sound-only Browse DB, its MIDI rows a MIDI-only one.
    DbImport.store(app, "gen_groove.mid", new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k', 0, 0, 0, 4, 0, (byte) 0xFF, 0x2F, 0}, true);
    pickFromMenu("Java \u00b7 DrumMidi_CRT.java", "CompareHits.java");
    call("pkOpenParams");
    idle();
    AlertDialog ch = (AlertDialog) ShadowDialog.getLatestDialog();
    View chv = ch.getWindow().getDecorView();
    android.widget.Button chDb = (android.widget.Button) chv.findViewWithTag("params-db:input.wav");
    out.append("CompareHits input Browse DB: ").append(chDb == null ? "none" : chDb.isEnabled() ? "enabled" : "greyed").append('\n');
    for (String row : new String[] {"drums.mid", "song.mid"}) {
      android.widget.Button mdb = (android.widget.Button) chv.findViewWithTag("params-db:" + row);
      out.append("  ").append(row).append(" Browse DB: ").append(mdb == null ? "none" : mdb.isEnabled() ? "enabled" : "greyed").append('\n');
    }
    ((android.widget.Button) chv.findViewWithTag("params-db:drums.mid")).performClick();
    idle();
    View mb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
    out.append("  MIDI browser: ").append(((TextView) mb.findViewWithTag("refs-kind:refs")).getText()).append(" / ")
        .append(((TextView) mb.findViewWithTag("refs-kind:results")).getText()).append(", gen_groove.mid ").append(mb.findViewWithTag("refs-pick:gen_groove.mid") != null)
        .append(", loop.wav ").append(mb.findViewWithTag("refs-pick:loop.wav") != null).append('\n');
    mb.findViewWithTag("refs-pick:gen_groove.mid").performClick();
    idle();
    out.append("  drums.mid row: ").append(((TextView) chv.findViewWithTag("params-chosen:drums.mid")).getText()).append('\n');
    ch.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    // DrumMidi_CRT's input also takes a MIDI from the library: its drums played with the kit to a WAV.
    DbImport.store(app, "beat_db.mid", new byte[] {'M', 'T', 'h', 'd', 0, 0, 0, 6, 0, 0, 0, 1, 0, 96, 'M', 'T', 'r', 'k', 0, 0, 0, 20,
      0, (byte) 0x99, 36, 100, 48, (byte) 0x89, 36, 0, 0, (byte) 0x99, 38, 100, 48, (byte) 0x89, 38, 0, 0, (byte) 0xFF, 0x2F, 0}, true);
    pickFromMenu("Java \u00b7 CompareHits.java", "DrumMidi_CRT.java");
    for (String pick : new String[] {"gen_groove.mid", "beat_db.mid"}) {
      call("pkOpenParams");
      idle();
      AlertDialog dk = (AlertDialog) ShadowDialog.getLatestDialog();
      View dkv = dk.getWindow().getDecorView();
      ((android.widget.Button) dkv.findViewWithTag("params-db:input.wav")).performClick();
      idle();
      View kb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
      if (pick.equals("gen_groove.mid")) {
        out.append("DrumMidi_CRT browser with MIDI: ").append(((TextView) kb.findViewWithTag("refs-kind:refs")).getText()).append(" / ")
            .append(((TextView) kb.findViewWithTag("refs-kind:results")).getText()).append(", loop.wav ").append(kb.findViewWithTag("refs-pick:loop.wav") != null)
            .append(", beat_db.mid ").append(kb.findViewWithTag("refs-pick:beat_db.mid") != null).append(", krea.png ").append(kb.findViewWithTag("refs-pick:krea.png") != null).append('\n');
        ((TextView) kb.findViewWithTag("refs-kind:results")).performClick();
        idle();
        kb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
      } else {
        ((TextView) kb.findViewWithTag("refs-kind:results")).performClick();
        idle();
        kb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
      }
      kb.findViewWithTag("refs-pick:" + pick).performClick();
      idle();
      out.append("  pick ").append(pick).append(": row ").append(((TextView) dkv.findViewWithTag("params-chosen:input.wav")).getText()).append('\n');
      dk.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
      idle();
    }
    String dmArgs = ((TextView) get("pkPyArgs")).getText().toString();
    out.append("  args: ").append(dmArgs.replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    java.io.File kitWav = new java.io.File(app.getCacheDir(), "pyjav-in/beat_db-kit.wav");
    byte[] kw = kitWav.isFile() ? java.nio.file.Files.readAllBytes(kitWav.toPath()) : new byte[0];
    out.append("  beat_db-kit.wav: ").append(kw.length > 44 && kw[0] == 'R' && kw[8] == 'W' ? "a WAV of " + kw.length + " bytes" : "missing").append('\n');
    // SplitWav's input.wav: the sound-only Browse DB.
    pickFromMenu("Java \u00b7 DrumMidi_CRT.java", "SplitWav.java");
    call("pkOpenParams");
    idle();
    AlertDialog sw = (AlertDialog) ShadowDialog.getLatestDialog();
    View swv = sw.getWindow().getDecorView();
    android.widget.Button swDb = (android.widget.Button) swv.findViewWithTag("params-db:input.wav");
    out.append("SplitWav input Browse DB: ").append(swDb == null ? "none" : swDb.isEnabled() ? "enabled" : "greyed").append('\n');
    swDb.performClick();
    idle();
    View swb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
    out.append("  sound browser: loop.wav ").append(swb.findViewWithTag("refs-pick:loop.wav") != null).append(", gen_groove.mid ")
        .append(swb.findViewWithTag("refs-pick:gen_groove.mid") != null).append('\n');
    swb.findViewWithTag("refs-pick:loop.wav").performClick();
    idle();
    sw.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("  args: ").append(((TextView) get("pkPyArgs")).getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    // CutWav's input.wav: the sound-only Browse DB as well (its output.wav is named by PyJav, no button).
    pickFromMenu("Java \u00b7 SplitWav.java", "CutWav.java");
    call("pkOpenParams");
    idle();
    AlertDialog cw = (AlertDialog) ShadowDialog.getLatestDialog();
    View cwv = cw.getWindow().getDecorView();
    android.widget.Button cwDb = (android.widget.Button) cwv.findViewWithTag("params-db:input.wav");
    out.append("CutWav input Browse DB: ").append(cwDb == null ? "none" : cwDb.isEnabled() ? "enabled" : "greyed")
        .append(", output.wav ").append(cwv.findViewWithTag("params-db:output.wav") == null ? "without" : "with").append(" it\n");
    cwDb.performClick();
    idle();
    View cwb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
    out.append("  sound browser: loop.wav ").append(cwb.findViewWithTag("refs-pick:loop.wav") != null).append(", krea.png ")
        .append(cwb.findViewWithTag("refs-pick:krea.png") != null).append('\n');
    cwb.findViewWithTag("refs-pick:loop.wav").performClick();
    idle();
    cw.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("  args: ").append(((TextView) get("pkPyArgs")).getText().toString().replace(app.getCacheDir().getAbsolutePath(), "<cache>")).append('\n');
    // The Compare Hits page: Browse DB next to Pick WAV.
    app.compareHits.refresh();
    TextView pageDb = (TextView) root().findViewWithTag("compare-browse-db");
    out.append("Compare Hits page Browse DB: ").append(pageDb == null ? "none" : pageDb.isEnabled() ? "enabled" : "greyed").append('\n');
    pageDb.performClick();
    idle();
    View pb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
    out.append("  sound browser: loop.wav ").append(pb.findViewWithTag("refs-pick:loop.wav") != null).append(", krea.png ").append(pb.findViewWithTag("refs-pick:krea.png") != null).append('\n');
    pb.findViewWithTag("refs-pick:loop.wav").performClick();
    idle();
    out.append("  original WAV: ").append(app.compareHits.wavLabel.getText()).append(", ").append(app.compareHits.pickedWav == null ? 0 : app.compareHits.pickedWav.length).append(" bytes\n");
    // A MIDI from the library for the WAV: played with the kit to one first.
    root().findViewWithTag("compare-browse-db").performClick();
    idle();
    View mpb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
    ((TextView) mpb.findViewWithTag("refs-kind:results")).performClick();
    idle();
    mpb = ((AlertDialog) ShadowDialog.getLatestDialog()).getWindow().getDecorView();
    out.append("  result files for the WAV: beat_db.mid ").append(mpb.findViewWithTag("refs-pick:beat_db.mid") != null).append(", krea.png ")
        .append(mpb.findViewWithTag("refs-pick:krea.png") != null).append('\n');
    mpb.findViewWithTag("refs-pick:beat_db.mid").performClick();
    idle();
    byte[] pw = app.compareHits.pickedWav;
    out.append("  original WAV: ").append(app.compareHits.wavLabel.getText()).append(", ")
        .append(pw != null && pw.length > 44 && pw[0] == 'R' && pw[8] == 'W' ? "a WAV of " + pw.length + " bytes" : "not a WAV").append('\n');
    write("s61_db_import", out.toString());
  }

  /**
   * Drum Midi Settings: "Save SogniMusic output file into DB". Off, a SogniMusic run stores nothing;
   * on, its track goes into the prompt library (Music / SogniMusic) with its arguments and workflow
   * id; with --saveprompt too, the track is kept with its sheet only.
   */
  @Test
  public void s62_sogni_music_db() throws Exception {
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    android.widget.CheckBox box = null;
    View item = root().findViewWithTag("sogni-music-db");
    if (item instanceof android.view.ViewGroup) {
      for (int i = 0; i < ((android.view.ViewGroup) item).getChildCount(); i++) {
        if (((android.view.ViewGroup) item).getChildAt(i) instanceof android.widget.CheckBox) box = (android.widget.CheckBox) ((android.view.ViewGroup) item).getChildAt(i);
      }
    }
    out.append("checkbox: ").append(box == null ? "none" : box.getText() + (box.isChecked() ? " (on)" : " (off)")).append('\n');
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniMusic.java");
    byte[] mp3 = {'I', 'D', '3', 3, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6};
    for (int run = 1; run <= 3; run++) {
      if (run == 2) box.setChecked(true);
      String track = "sogni-Funk-run" + run + ".mp3";
      java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
      files.add(new JavaRun.FileOut(track, mp3));
      if (run == 3) {
        String sheet = "PKPROMPT1\nsogni-Funk\n\n\n\n\nCategory: Music\nModel: Sogni turbo\nReference file 1: \nReference file 2: \nType: ai\n---\n"
            + "funk groove\n\nResult file: " + track + "\n";
        files.add(new JavaRun.FileOut("sogni-Funk.prompt", sheet.getBytes("UTF-8")));
      }
      app.pyJav.pkLastArgv = new java.util.ArrayList<String>(java.util.Arrays.asList("--genre", "Funk", "--duration", "30",
          "--key_file", new java.io.File(app.getCacheDir(), "pyjav-in/sogni_credentials").getAbsolutePath()));
      app.pyJav.pkShowPyResult(new JavaRun.Result("SogniMusic 2026-10-05c\nWorkflow: wf_run" + run + "\nWrote " + track + " (0 KB)\nSucceeded: " + track, files, 0));
      idle();
      AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
      if (d != null && d.isShowing()) d.dismiss();
      idle();
      StringBuilder library = new StringBuilder();
      for (String line : ((TextView) get("pkPyLog")).getText().toString().split("\n")) if (line.startsWith("Prompt library")) library.append(library.length() > 0 ? " | " : "").append(line);
      out.append("run ").append(run).append(" (setting ").append(box.isChecked() ? "on" : "off").append(run == 3 ? ", --saveprompt" : "").append("): ")
          .append(library.length() == 0 ? "not stored" : library).append('\n');
    }
    PromptVault vault = PromptVault.open(app.getFilesDir());
    for (PromptVault.Category c : vault.categories()) {
      for (PromptVault.Prompt p : vault.prompts(c.id)) {
        if (!p.title.equals("SogniMusic")) continue;
        out.append("library: ").append(c.name).append(" / ").append(p.title).append(", ").append(vault.versions(p.id).size()).append(" versions\n");
        for (PromptVault.Version v : vault.versions(p.id)) {
          out.append("  text: ").append(v.body).append("\n  result: ").append(v.resultName).append(" (").append(v.result == null ? 0 : v.result.length)
              .append(" bytes), result text: ").append(v.resultText.replace("\n", " | ")).append('\n');
        }
      }
    }
    out.append("kept: ").append(app.getSharedPreferences(DrumMidiSettingsPage.PREFS, 0).getBoolean(DrumMidiSettingsPage.MUSIC_TO_DB, false)).append('\n');
    write("s62_sogni_music_db", out.toString());
  }

  /** Program files folder in Drum Midi Settings: a picked folder is shown by its path, and Use Downloads goes back. */
  @Test
  public void s53_program_folder() throws Exception {
    StringBuilder out = new StringBuilder();
    DrumMidiSettingsPage page = app.drumMidiSettings;
    out.append("before: ").append(page.folderStatus.getText()).append('\n');
    page.takeFolder(android.net.Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic%2FSogni"));
    out.append("after: ").append(page.folderStatus.getText()).append('\n');
    // A folder that cannot be written (here: no such provider) leaves the file in Downloads.
    out.append("published: ").append(ArtJava.publish(app, "kit-ideas-1.png", new byte[] {1, 2, 3}) != null).append('\n');
    ProgramFolder.init(new java.io.File(app.getFilesDir(), "sogni"));
    out.append("kept: ").append(ProgramFolder.label()).append('\n');
    root().findViewWithTag("program-folder-clear").performClick();
    out.append("cleared: ").append(page.folderStatus.getText()).append('\n');
    write("s53_program_folder", out.toString());
  }

  /**
   * The Media browser's Loop videos (kept in the app's preferences): a video opened from it loops.
   * Its player has Mute, a volume slider and Zoom −, Zoom +, Fit (pinch too).
   */
  @Test
  public void s71_media_video() throws Exception {
    StringBuilder out = new StringBuilder();
    File media = new File(app.getCacheDir(), "clips");
    media.mkdirs();
    Files.write(new File(media, "walk.mp4").toPath(), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});
    org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(ds -> new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(3500, 0));
    MediaBrowser b = MediaBrowser.open(app, media.getAbsolutePath());
    idle();
    View bv = b.dialog.getWindow().getDecorView();
    android.widget.CheckBox loop = (android.widget.CheckBox) bv.findViewWithTag("media-loop");
    out.append("Loop videos: ").append(loop.getText()).append(", ticked ").append(loop.isChecked()).append('\n');
    loop.performClick();
    idle();
    out.append("ticked: ").append(loop.isChecked()).append(", kept ").append(app.getSharedPreferences(MediaBrowser.PREFS, 0).getString(MediaBrowser.SETTINGS, "").replace('\n', ' ').trim()).append('\n');
    bv.findViewWithTag("media-card:walk.mp4").performClick();
    idle();
    MediaBrowser.Video v = MediaBrowser.lastVideo;
    View tv = ShadowDialog.getLatestDialog().getWindow().getDecorView();
    // The player ready (3.5 s long), 1.2 s in: the time label follows it.
    android.media.MediaPlayer mp = new android.media.MediaPlayer();
    mp.setDataSource(new File(media, "walk.mp4").getAbsolutePath());
    mp.prepare();
    org.robolectric.Shadows.shadowOf(v.view).getOnPreparedListener().onPrepared(mp);
    mp.seekTo(1200);
    ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
    idle();
    out.append("time: ").append(((TextView) tv.findViewWithTag("media-video-time")).getText()).append('\n');
    // The screen stays on while it plays, not while it is paused.
    mp.start();
    ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
    int keep = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    out.append("playing: screen kept on: player window ").append((v.window.getAttributes().flags & keep) != 0).append(", app window ").append((app.getWindow().getAttributes().flags & keep) != 0);
    mp.pause();
    ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
    out.append("; paused: ").append((v.window.getAttributes().flags & keep) != 0).append(", ").append((app.getWindow().getAttributes().flags & keep) != 0).append('\n');
    out.append("player: loop ").append(v.loop).append(", title ").append(((TextView) tv.findViewWithTag("media-video-title")).getText()).append('\n');
    TextView mute = (TextView) tv.findViewWithTag("media-video-mute");
    mute.performClick();
    out.append("Mute: ").append(mute.getText()).append(", ").append(((TextView) tv.findViewWithTag("media-video-level")).getText()).append(", gain ").append(v.gain()).append('\n');
    mute.performClick();
    ((android.widget.SeekBar) tv.findViewWithTag("media-video-volume")).setProgress(40);
    out.append("volume 40: ").append(((TextView) tv.findViewWithTag("media-video-level")).getText()).append(", gain ").append(v.gain()).append('\n');
    for (String z : new String[] {"media-video-zoom-in", "media-video-zoom-in", "media-video-zoom-in", "media-video-zoom-out", "media-video-fit"}) {
      tv.findViewWithTag(z).performClick();
      out.append("  ").append(z).append(": ").append(((TextView) tv.findViewWithTag("media-video-zoom")).getText()).append(", scale ").append(v.view.getScaleX()).append('\n');
    }
    // Playback speed: a list from 0.25x to 2x.
    android.app.Dialog player = ShadowDialog.getLatestDialog();
    TextView speed = (TextView) tv.findViewWithTag("media-video-speed");
    out.append("speed: ").append(speed.getText()).append('\n');
    speed.performClick();
    idle();
    AlertDialog list = (AlertDialog) ShadowDialog.getLatestDialog();
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < list.getListView().getAdapter().getCount(); i++) items.append(list.getListView().getAdapter().getItem(i)).append(' ');
    out.append("speeds: ").append(items.toString().trim()).append('\n');
    org.robolectric.Shadows.shadowOf(list).clickOnItem(5);
    idle();
    out.append("picked: ").append(speed.getText()).append(", player ").append(v.speed).append('\n');
    tv.findViewWithTag("media-video-zoom-in").performClick();
    player.dismiss();
    idle();
    out.append("kept: ").append(app.getSharedPreferences(MediaBrowser.PREFS, 0).getString(MediaBrowser.SETTINGS, "").replace('\n', ' ').trim()).append('\n');
    b.dialog.dismiss();
    idle();
    // Opened again later: Loop videos stays ticked.
    MediaDir.loopVideos = false;
    b = MediaBrowser.open(app, media.getAbsolutePath());
    idle();
    out.append("reopened: ticked ").append(((android.widget.CheckBox) b.dialog.getWindow().getDecorView().findViewWithTag("media-loop")).isChecked()).append('\n');
    // The next video opens as the last one was left.
    b.dialog.getWindow().getDecorView().findViewWithTag("media-card:walk.mp4").performClick();
    idle();
    MediaBrowser.Video again = MediaBrowser.lastVideo;
    View av = ShadowDialog.getLatestDialog().getWindow().getDecorView();
    out.append("next video: volume ").append(((android.widget.SeekBar) av.findViewWithTag("media-video-volume")).getProgress()).append(" (").append(((TextView) av.findViewWithTag("media-video-level")).getText())
        .append("), zoom ").append(((TextView) av.findViewWithTag("media-video-zoom")).getText()).append(" scale ").append(again.view.getScaleX())
        .append(", ").append(((TextView) av.findViewWithTag("media-video-speed")).getText()).append('\n');
    ShadowDialog.getLatestDialog().dismiss();
    idle();
    b.dialog.dismiss();
    write("s71_media_video", out.toString());
  }

  /**
   * The Media browser remembers the folder picked and the folder it was in under it: opening it on
   * the same folder again goes back there (not when it is gone, nor for another folder), and
   * Params' Directory shows the folder when the arguments have none.
   */
  @Test
  public void s72_media_remember() throws Exception {
    StringBuilder out = new StringBuilder();
    File media = new File(app.getCacheDir(), "my media");
    File deep = new File(new File(media, "trips"), "2024");
    deep.mkdirs();
    Files.write(new File(deep, "beat.wav").toPath(), AudioIo.encodeWav(new short[100], 22050));
    File other = new File(app.getCacheDir(), "other");
    other.mkdirs();
    String base = app.getCacheDir().getAbsolutePath();
    MediaBrowser b = MediaBrowser.open(app, media.getAbsolutePath());
    idle();
    View bv = b.dialog.getWindow().getDecorView();
    out.append("opened: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append('\n');
    bv.findViewWithTag("media-folder:trips").performClick();
    idle();
    bv.findViewWithTag("media-folder:2024").performClick();
    idle();
    out.append("went to: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append('\n');
    b.dialog.dismiss();
    idle();
    out.append("kept: ").append(app.getSharedPreferences(MediaBrowser.PREFS, 0).getString(MediaBrowser.SETTINGS, "")
        .replace(base, "~").replaceAll("(?m)^(loop|volume|zoom|speed)=.*\n", "").trim().replace("\n", "  ").replace("\t", " | ")).append('\n');
    b = MediaBrowser.open(app, media.getAbsolutePath());
    idle();
    bv = b.dialog.getWindow().getDecorView();
    out.append("reopened: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append(", card ").append(bv.findViewWithTag("media-card:beat.wav") != null);
    bv.findViewWithTag("media-up").performClick();
    idle();
    out.append(", Up: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append('\n');
    b.dialog.dismiss();
    idle();
    Files.delete(new File(deep, "beat.wav").toPath());
    Files.delete(deep.toPath());
    Files.delete(deep.getParentFile().toPath());
    b = MediaBrowser.open(app, media.getAbsolutePath());
    idle();
    out.append("trips gone: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append('\n');
    b.dialog.dismiss();
    idle();
    b = MediaBrowser.open(app, other.getAbsolutePath());
    idle();
    out.append("another folder: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append('\n');
    b.dialog.dismiss();
    idle();
    // Params: Directory shows the folder last opened; OK puts it in the arguments.
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "MediaBrowser.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("");
    call("pkOpenParams");
    idle();
    AlertDialog params = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("Params Directory: ").append(((TextView) params.getWindow().getDecorView().findViewWithTag("params-chosen:directory")).getText()).append('\n');
    params.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText().toString().replace(base, "~")).append('\n');
    write("s72_media_remember", out.toString());
  }

  /** An MP4 with only its headers: ftyp, then moov with mvhd (the length) and a trak whose tkhd has the picture size. */
  private static byte[] mp4Header(int lengthMs, int width, int height) throws Exception {
    java.io.ByteArrayOutputStream mvhd = new java.io.ByteArrayOutputStream();
    java.io.DataOutputStream m = new java.io.DataOutputStream(mvhd);
    m.writeInt(108);
    m.writeBytes("mvhd");
    m.writeInt(0);
    m.writeInt(0);
    m.writeInt(0);
    m.writeInt(1000);
    m.writeInt(lengthMs);
    m.write(new byte[80]);
    java.io.ByteArrayOutputStream tkhd = new java.io.ByteArrayOutputStream();
    java.io.DataOutputStream t = new java.io.DataOutputStream(tkhd);
    t.writeInt(92);
    t.writeBytes("tkhd");
    t.write(new byte[76]);
    t.writeInt(width << 16);
    t.writeInt(height << 16);
    java.io.ByteArrayOutputStream file = new java.io.ByteArrayOutputStream();
    java.io.DataOutputStream f = new java.io.DataOutputStream(file);
    f.writeInt(16);
    f.writeBytes("ftypisom");
    f.writeInt(0);
    f.writeInt(8 + 108 + 8 + 92);
    f.writeBytes("moov");
    f.write(mvhd.toByteArray());
    f.writeInt(8 + 92);
    f.writeBytes("trak");
    f.write(tkhd.toByteArray());
    return file.toByteArray();
  }

  /**
   * MediaBrowser (PyJav's Java menu): Params has Directory with Browse, which opens the system's
   * folder picker; the folder picked is kept, shown, and opened in the Media browser: folders first,
   * then pictures, videos and sounds as cards, other files left out. A folder opens in its place and
   * Up goes back; a picture opens full size, a video and a sound play. A run opens it too.
   */
  @Test
  public void s70_media_browser() throws Exception {
    StringBuilder out = new StringBuilder();
    File media = new File(app.getCacheDir(), "my media");
    File more = new File(media, "more");
    more.mkdirs();
    android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888);
    try (java.io.FileOutputStream fo = new java.io.FileOutputStream(new File(media, "sunset.png"))) {
      bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fo);
    }
    try (java.io.FileOutputStream fo = new java.io.FileOutputStream(new File(more, "beach.jpg"))) {
      bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fo);
    }
    // An MP4's headers only (3.46 s, 160x120): the playlist preview reads its length and size from them.
    Files.write(new File(media, "walk.mp4").toPath(), mp4Header(3460, 160, 120));
    Files.write(new File(media, "beat.wav").toPath(), AudioIo.encodeWav(new short[22050], 22050));
    Files.write(new File(media, "groove.mid").toPath(), Engine.encodeMidi(Engine.styleCells(Engine.styles().get("rock")), 110));
    Files.write(new File(media, "notes.txt").toPath(), "not media".getBytes(StandardCharsets.UTF_8));
    org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(ds -> new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(1000, 0));
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "MediaBrowser.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("");
    call("pkOpenParams");
    idle();
    AlertDialog params = (AlertDialog) ShadowDialog.getLatestDialog();
    View pv = params.getWindow().getDecorView();
    TextView browse = (TextView) pv.findViewWithTag("params-dir:directory");
    TextView chosen = (TextView) pv.findViewWithTag("params-chosen:directory");
    out.append("row: ").append(browse == null ? "no Browse" : browse.getText()).append(", ").append(chosen == null ? "no label" : chosen.getText()).append('\n');
    browse.performClick();
    idle();
    org.robolectric.shadows.ShadowActivity.IntentForResult picker = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult();
    out.append("picker: ").append(picker.intent.getAction()).append('\n');
    app.onActivityResult(picker.requestCode, -1, new android.content.Intent().setData(android.net.Uri.fromFile(media)));
    idle();
    MediaBrowser b = MediaBrowser.last;
    out.append("browser: ").append(b == null ? "none" : "open").append('\n');
    out.append("row now: ").append(chosen.getText()).append('\n');
    View bv = b.dialog.getWindow().getDecorView();
    for (int i = 0; i < 40; i++) {
      Thread.sleep(50);
      idle();
    }
    out.append("title: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append('\n');
    out.append("summary: ").append(((TextView) bv.findViewWithTag("media-summary")).getText()).append('\n');
    for (MediaDir.Entry e : b.entries) out.append("  ").append(e.folder ? "folder " : "card ").append(e.name).append(bv.findViewWithTag((e.folder ? "media-folder:" : "media-card:") + e.name) != null ? "" : " (no card)").append('\n');
    out.append("notes.txt card: ").append(bv.findViewWithTag("media-card:notes.txt") != null ? "shown" : "none").append('\n');
    // A long press on a card: its menu. Add to DB as a reference file, as a result file, Add to default playlist.
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    TextView playlistButton = (TextView) bv.findViewWithTag("media-playlist");
    out.append("Playlist button before: ").append(playlistButton.getVisibility() == View.VISIBLE ? playlistButton.getText() : "hidden").append('\n');
    final View browserView = bv;
    java.util.function.Supplier<String> badges = () -> {
      StringBuilder marks = new StringBuilder();
      for (String n : new String[] {"beat.wav", "groove.mid", "sunset.png", "walk.mp4"}) marks.append(n).append(browserView.findViewWithTag("media-in-playlist:" + n) != null ? " \u2630" : " -").append("  ");
      return marks.toString().trim();
    };
    out.append("badges before: ").append(badges.get()).append('\n');
    String[][] picks = {{"sunset.png", "0"}, {"beat.wav", "1"}, {"walk.mp4", "2"}, {"walk.mp4", "2"}, {"sunset.png", "2"}, {"beat.wav", "2"}};
    for (String[] pick : picks) {
      boolean handled = bv.findViewWithTag("media-card:" + pick[0]).performLongClick();
      idle();
      AlertDialog menu = MediaBrowser.lastMenu;
      if (pick == picks[0]) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < menu.getListView().getAdapter().getCount(); i++) items.append('[').append(menu.getListView().getAdapter().getItem(i)).append(']');
        out.append("menu: ").append(items).append(", long press handled ").append(handled).append('\n');
      }
      org.robolectric.Shadows.shadowOf(menu).clickOnItem(Integer.parseInt(pick[1]));
      idle();
      out.append(pick[0]).append(" item ").append(pick[1]).append(": ").append(((TextView) get("now")).getText()).append('\n');
    }
    out.append("folder long press: ").append(bv.findViewWithTag("media-folder:more").isLongClickable()).append('\n');
    for (PromptVault.StoredFile f : PromptVault.open(app.getFilesDir()).referenceFiles()) out.append("ref file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    for (PromptVault.StoredFile f : PromptVault.open(app.getFilesDir()).resultFiles()) out.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    out.append("Playlist button after: ").append(playlistButton.getVisibility() == View.VISIBLE ? playlistButton.getText() : "hidden").append('\n');
    out.append("badges after: ").append(badges.get()).append('\n');
    for (MediaPlaylist.Item it : MediaPlaylist.items(app.getFilesDir(), b.folderKey())) out.append("playlist: ").append(it.name).append(" = ").append(it.id.replace(media.getParent(), "~")).append('\n');
    // The playlist window: its files in order; a tap plays or opens one as its thumbnail does.
    playlistButton.performClick();
    idle();
    View lv = MediaBrowser.lastPlaylist.getWindow().getDecorView();
    out.append("window: ").append(org.robolectric.Shadows.shadowOf(MediaBrowser.lastPlaylist).getTitle()).append('\n');
    for (String n : new String[] {"walk.mp4", "sunset.png", "beat.wav"}) {
      TextView row = (TextView) lv.findViewWithTag("playlist-item:" + n);
      out.append("  ").append(row.getText());
      row.performClick();
      idle();
      out.append(" -> ").append(MediaBrowser.lastOpened).append('\n');
      ShadowDialog.getLatestDialog().dismiss();
      idle();
    }
    out.append("window still open: ").append(MediaBrowser.lastPlaylist.isShowing()).append('\n');
    // Held: the item's thumbnail pops up; letting go closes it and opens nothing.
    for (String n : new String[] {"sunset.png", "walk.mp4", "beat.wav"}) {
      TextView row = (TextView) lv.findViewWithTag("playlist-item:" + n);
      MediaBrowser.lastOpened = null;
      boolean handled = row.performLongClick();
      for (int i = 0; i < 80 && (MediaBrowser.lastPeekInfo.getText().length() == 0 || MediaBrowser.lastPeekImage.getDrawable() == null); i++) {
        Thread.sleep(25);
        idle();
      }
      out.append("held ").append(n).append(": handled ").append(handled).append(", preview shown ").append(MediaBrowser.lastPeek != null && MediaBrowser.lastPeek.isShowing())
          .append(", thumbnail ").append(MediaBrowser.lastPeekImage.getDrawable() != null).append(", info \"").append(MediaBrowser.lastPeekInfo.getText()).append('"');
      long now = android.os.SystemClock.uptimeMillis();
      android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_UP, 5, 5, 0);
      row.dispatchTouchEvent(up);
      up.recycle();
      idle();
      out.append("; released: preview shown ").append(MediaBrowser.lastPeek.isShowing()).append(", opened ").append(MediaBrowser.lastOpened).append('\n');
    }
    MediaBrowser.lastPlaylist.dismiss();
    idle();
    // A folder under it has a playlist of its own.
    bv.findViewWithTag("media-folder:more").performClick();
    idle();
    TextView morePlaylist = (TextView) bv.findViewWithTag("media-playlist");
    out.append("in more, Playlist button: ").append(morePlaylist.getVisibility() == View.VISIBLE ? morePlaylist.getText() : "hidden");
    bv.findViewWithTag("media-card:beach.jpg").performLongClick();
    idle();
    org.robolectric.Shadows.shadowOf(MediaBrowser.lastMenu).clickOnItem(2);
    idle();
    out.append(", after adding beach.jpg: ").append(morePlaylist.getVisibility() == View.VISIBLE ? morePlaylist.getText() : "hidden").append('\n');
    bv.findViewWithTag("media-up").performClick();
    idle();
    out.append("back up, Playlist button: ").append(((TextView) bv.findViewWithTag("media-playlist")).getText()).append(", badges ").append(badges.get()).append('\n');
    bv.findViewWithTag("media-folder:more").performClick();
    idle();
    out.append("in more: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append(", up ").append(bv.findViewWithTag("media-up") != null ? "shown" : "none")
        .append(", cards ").append(b.entries.size()).append('\n');
    bv.findViewWithTag("media-up").performClick();
    idle();
    out.append("up: ").append(org.robolectric.Shadows.shadowOf(b.dialog).getTitle()).append(", up ").append(bv.findViewWithTag("media-up") != null ? "shown" : "none").append('\n');
    // A card's tap: the picture full size, the video and the sound played.
    for (String n : new String[] {"sunset.png", "walk.mp4", "beat.wav", "groove.mid"}) {
      bv.findViewWithTag("media-card:" + n).performClick();
      idle();
      android.app.Dialog top = ShadowDialog.getLatestDialog();
      View tv = top.getWindow().getDecorView();
      String what = tv.findViewWithTag("media-full") != null ? "full size picture"
          : tv.findViewWithTag("media-video") != null ? "video player"
          : tv.findViewWithTag("media-sound-play") != null ? "sound player, " + ((TextView) tv.findViewWithTag("media-sound-play")).getText() : "nothing";
      out.append(n).append(": ").append(MediaBrowser.lastOpened).append(", ").append(what).append('\n');
      top.dismiss();
      idle();
    }
    b.dialog.dismiss();
    idle();
    params.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText().toString().replace(media.getParent(), "~")).append('\n');
    // A run opens the Media browser on its folder.
    app.pyJav.pkShowPyResult(new JavaRun.Result("Succeeded: 1 picture, 1 video, 2 sounds, 1 folder\nMedia browser: " + media.getAbsolutePath(), new java.util.ArrayList<JavaRun.FileOut>(), 0));
    idle();
    out.append("after a run: ").append(MediaBrowser.last != null ? "open, " + MediaBrowser.last.entries.size() + " entries" : "none").append('\n');
    out.append("phone folder: ").append(MediaDir.label("content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera")).append('\n');
    write("s70_media_browser", out.toString());
  }

  /**
   * JoinVideo (PyJav's Java menu): Params has video a and video b (Choose .mp4 and Browse DB,
   * videos only), Video b first, an output name and Add to DB; a run with --addtodb keeps the joined
   * video in the prompt library as a result file.
   */
  @Test
  public void s69_join_video() throws Exception {
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    PromptVault.open(app.getFilesDir()).addLibraryFile("stored.mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p'}, "Imported", 1);
    PromptVault.open(app.getFilesDir()).addLibraryFile("loop.wav", new byte[] {'R', 'I', 'F', 'F'}, "Imported", 1);
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "JoinVideo.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("");
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    for (String t : new String[] {"video_a.mp4", "video_b.mp4", "--video_c", "--video_f"}) {
      TextView pick = (TextView) dv.findViewWithTag("params-file:" + t);
      View db = dv.findViewWithTag("params-db:" + t);
      out.append(t).append(": ").append(pick == null ? "no file row" : pick.getText()).append(", Browse DB ").append(db == null ? "none" : db.isEnabled() ? "on" : "off").append('\n');
    }
    for (String t : new String[] {"--b_first", "--addtodb"}) {
      android.widget.CheckBox c = (android.widget.CheckBox) dv.findViewWithTag("params-check:" + t);
      out.append(t).append(": ").append(c == null ? "none" : c.getText()).append('\n');
    }
    out.append("output: ").append(dv.findViewWithTag("params-field:output.mp4") != null ? "a field" : "none").append('\n');
    d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    // Browse DB on a video row lists the library's videos only.
    out.append("videos in the library: ").append(RefBrowser.allFiles(app, RefBrowser.VIDEOS).size()).append(" of ").append(RefBrowser.allFiles(app, null).size()).append('\n');
    // A run with --addtodb: the joined video goes into the library as a result file.
    app.pyJav.pkLastArgv = java.util.Arrays.asList("/sdcard/a.mp4", "/sdcard/b.mp4", "both.mp4", "--addtodb");
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    files.add(new JavaRun.FileOut("both.mp4", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2', 'i', 's', 'o', 'm'}));
    app.pyJav.pkShowPyResult(new JavaRun.Result("Joined a.mp4 and then b.mp4: wrote both.mp4 (1 KB)\nAdd to DB: both.mp4\nSucceeded: both.mp4", files, 0));
    idle();
    for (PromptVault.StoredFile f : PromptVault.open(app.getFilesDir()).resultFiles()) out.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    write("s69_join_video", out.toString());
  }

  /**
   * SogniVideo: Join with this video. In Params the file buttons work only when it is ticked, and
   * unticking clears the video. After a run, the joined clip (<clip>-merged.mp4) goes into the prompt
   * library as a result file and is the one the Video ready dialog shows; Join is unticked (out of
   * the arguments and the saved Params) after every run.
   */
  @Test
  public void s68_join() throws Exception {
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniVideo.java");
    TextView args = (TextView) get("pkPyArgs");
    java.io.File other = new java.io.File(app.getCacheDir(), "next.mp4");
    java.nio.file.Files.write(other.toPath(), new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});
    args.setText("--prompt Walk --join " + other.getAbsolutePath());
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    android.widget.CheckBox join = (android.widget.CheckBox) dv.findViewWithTag("params-join:--join");
    View pick = dv.findViewWithTag("params-file:--join");
    View db = dv.findViewWithTag("params-db:--join");
    TextView chosen = (TextView) dv.findViewWithTag("params-chosen:--join");
    out.append("Params: ").append(join.getText()).append(' ').append(join.isChecked() ? "ticked" : "unticked").append(", ").append(chosen.getText())
        .append(", Choose ").append(pick.isEnabled() ? "on" : "off").append(", Browse DB ").append(db != null && db.isEnabled() ? "on" : "off (library empty)").append('\n');
    android.widget.CheckBox first = (android.widget.CheckBox) dv.findViewWithTag("params-check:--join_first");
    out.append("order switch: ").append(first == null ? "none" : first.getText() + (first.isChecked() ? " on" : " off")).append('\n');
    join.setChecked(false);
    out.append("unticked: ").append(chosen.getText()).append(", Choose ").append(pick.isEnabled() ? "on" : "off").append('\n');
    join.setChecked(true);
    out.append("ticked again: Choose ").append(pick.isEnabled() ? "on" : "off").append('\n');
    d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    // A run that joined: the merged clip is kept and shown; Join is unticked.
    args.setText("--prompt Walk --join " + other.getAbsolutePath());
    java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
    byte[] mp4 = new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2', 'i', 's', 'o', 'm'};
    files.add(new JavaRun.FileOut("walk.mp4", mp4));
    files.add(new JavaRun.FileOut("walk-merged.mp4", mp4));
    app.pyJav.pkShowPyResult(new JavaRun.Result("Wrote walk.mp4 (1 KB)\nJoined walk.mp4 and next.mp4: wrote walk-merged.mp4 (1 KB)\nSucceeded: walk.mp4", files, 0));
    idle();
    out.append("args after: ").append(args.getText()).append('\n');
    out.append("video shown: ").append(VideoOffer.last != null ? "yes" : "no").append('\n');
    for (PromptVault.StoredFile f : PromptVault.open(app.getFilesDir()).resultFiles()) out.append("result file: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    out.append("saved Params: ").append(PyJavParams.load(app, "SogniVideo.java")).append('\n');
    write("s68_join", out.toString());
  }

  /**
   * SogniVideo Params: "Save the prompt as a prompt sheet" and "Content filter off" start ticked
   * until SogniVideo's Params are saved (then the saved choice wins). The prompt field keeps long-press
   * selection (Select all, Cut, Copy, Paste): its movement method can select, and it is long-clickable.
   */
  @Test
  public void s67_video_defaults() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniVideo.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("");
    for (int round = 0; round < 2; round++) {
      call("pkOpenParams");
      idle();
      AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
      View dv = d.getWindow().getDecorView();
      out.append("round ").append(round + 1).append(":");
      for (String t : new String[] {"--saveprompt", "--no_filter", "--no_audio", "--unlimited"}) {
        android.widget.CheckBox c = (android.widget.CheckBox) dv.findViewWithTag("params-check:" + t);
        out.append(' ').append(t).append(c.isChecked() ? " on" : " off");
      }
      out.append('\n');
      android.widget.EditText prompt = (android.widget.EditText) dv.findViewWithTag("params-field:--prompt");
      if (round == 0) {
        out.append("prompt field: selection ").append(prompt.getMovementMethod() != null && prompt.getMovementMethod().canSelectArbitrarily())
            .append(", long-clickable ").append(prompt.isLongClickable()).append(", editable ").append(prompt.onCheckIsTextEditor()).append('\n');
        prompt.setText("A cat walks");
        prompt.selectAll();
        out.append("select all: \"").append(prompt.getText().subSequence(prompt.getSelectionStart(), prompt.getSelectionEnd())).append("\"\n");
        ((android.widget.CheckBox) dv.findViewWithTag("params-check:--no_filter")).setChecked(false);
      }
      d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
      idle();
      out.append("args: ").append(args.getText()).append('\n');
    }
    // SogniChat runs with the filter off: its Params have "Content filter on", unticked.
    pickFromMenu("Java \u00b7 SogniVideo.java", "SogniChat.java");
    args.setText("");
    call("pkOpenParams");
    idle();
    AlertDialog c = (AlertDialog) ShadowDialog.getLatestDialog();
    View cv = c.getWindow().getDecorView();
    out.append("SogniChat:");
    for (String t : new String[] {"--filter_on", "--no_filter", "--tools", "--run_tools", "--unlimited", "--thinking"}) {
      android.widget.CheckBox box = (android.widget.CheckBox) cv.findViewWithTag("params-check:" + t);
      out.append(' ').append(t).append(box == null ? " none" : box.isChecked() ? " on" : " off");
    }
    out.append('\n');
    c.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
    idle();
    write("s67_video_defaults", out.toString());
  }

  /** The bottom ■ / Play / Gen bar shows on the pages that play (Pattern, Fillern, Fill, Song) and not on the others (PyJav, Pads...). */
  @Test
  public void s66_transport_pages() throws Exception {
    StringBuilder out = new StringBuilder();
    for (String v : new String[] {"pattern", "combo", "fills", "song", "py", "pads", "prompts", "import", "export", "midisettings", "help", "comparehits"}) {
      call("show", v);
      idle();
      out.append(v).append(": ").append(app.transportBar.getVisibility() == View.VISIBLE ? "bar" : "no bar").append('\n');
    }
    // Playing, a page with the bar keeps playing; one without it stops.
    for (String v : new String[] {"song", "import", "py", "pads"}) {
      call("show", "pattern");
      idle();
      if (!app.playing) call("toggle");
      idle();
      boolean before = app.playing;
      call("show", v);
      idle();
      out.append("playing, then ").append(v).append(": ").append(before ? (app.playing ? "still playing" : "stopped") : "did not start")
          .append(", Play reads ").append(app.playBtn.getText()).append('\n');
    }
    if (app.playing) app.playback.stop();
    call("show", "pattern");
    idle();
    write("s66_transport_pages", out.toString());
  }

  /**
   * Import screen: "Import to DB also" keeps a file picked with Choose file in the prompt library
   * too, as a reference file; Browse DB (after Choose file) imports a library file as Choose file
   * would, without storing it again. Kept across starts.
   */
  @Test
  public void s65_import_to_db() throws Exception {
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    call("show", "import");
    idle();
    android.widget.CheckBox box = (android.widget.CheckBox) root().findViewWithTag("import-to-db");
    out.append("checkbox: ").append(box.getText()).append(", ").append(box.isChecked()).append('\n');
    TextView browse = (TextView) root().findViewWithTag("import-browse-db");
    out.append("Browse DB: ").append(browse.getText()).append('\n');
    // An empty library: Browse DB says so.
    browse.performClick();
    idle();
    out.append("empty: ").append(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()).append('\n');
    java.io.File dir = new java.io.File(app.getCacheDir(), "picked");
    dir.mkdirs();
    short[] tone = new short[2205];
    java.io.File off = new java.io.File(dir, "off.wav");
    java.nio.file.Files.write(off.toPath(), AudioIo.encodeWav(tone, 22050));
    java.io.File loop = new java.io.File(dir, "loop.wav");
    java.nio.file.Files.write(loop.toPath(), AudioIo.encodeWav(tone, 22050));
    for (java.io.File f : new java.io.File[] {off, loop}) {
      if (f == loop) box.setChecked(true);
      app.projectIo.openFile();
      org.robolectric.shadows.ShadowActivity.IntentForResult picker = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult();
      app.onActivityResult(picker.requestCode, -1, new android.content.Intent().setData(android.net.Uri.fromFile(f)));
      idle();
      out.append(f.getName()).append(": input ").append(new java.io.File(app.pyJav.pkAudioInputPath).getName())
          .append(", toast ").append(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()).append('\n');
    }
    PromptVault vault = PromptVault.open(app.getFilesDir());
    for (PromptVault.StoredFile f : vault.referenceFiles()) out.append("ref: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    // Browse DB: the library's file, imported as Choose file would.
    app.pyJav.pkAudioInputPath = null;
    browse.performClick();
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    out.append("browse: ").append(org.robolectric.Shadows.shadowOf(d).getTitle()).append('\n');
    d.getWindow().getDecorView().findViewWithTag("refs-pick:loop.wav").performClick();
    idle();
    out.append("from DB: input ").append(app.pyJav.pkAudioInputPath == null ? "none" : new java.io.File(app.pyJav.pkAudioInputPath).getName())
        .append(", library still ").append(vault.referenceFiles().size()).append(" file").append('\n');
    out.append("kept: ").append(app.getSharedPreferences(ProjectIo.EXPORT_PREFS, 0).getBoolean(ProjectIo.IMPORT_TO_DB, false)).append('\n');
    box.setChecked(false);
    write("s65_import_to_db", out.toString());
  }

  /**
   * Export screen: "Export supported media files to DB also". Off, an export only writes the
   * file; on, a MIDI, WAV or MP3 export also goes into the prompt library as a reference file under
   * the name it was saved as (its extension added when it had none); SF2 is left out. Kept across starts.
   */
  @Test
  public void s64_export_to_db() throws Exception {
    if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.insertProviderAt(new FakeKeyStoreProvider(), 1);
    StringBuilder out = new StringBuilder();
    call("show", "export");
    idle();
    android.widget.CheckBox box = (android.widget.CheckBox) root().findViewWithTag("export-to-db");
    out.append("checkbox: ").append(box.getText()).append(", ").append(box.isChecked()).append('\n');
    java.io.File dir = new java.io.File(app.getCacheDir(), "exports");
    dir.mkdirs();
    // {what, file chosen in the picker}
    Object[][] runs = {{"pattern", "off.mid"}, {"pattern", "groove.mid"}, {"fill", "fill no ext"}, {10, "beat.wav"}, {11, "beat.mp3"}, {12, "kit.sf2"}, {17, "song.mid"}};
    for (int i = 0; i < runs.length; i++) {
      if (i == 1) box.setChecked(true);
      if (runs[i][0] instanceof String) app.projectIo.saveMidi((String) runs[i][0]);
      else app.projectIo.saveKind((Integer) runs[i][0]);
      org.robolectric.shadows.ShadowActivity.IntentForResult picker = org.robolectric.Shadows.shadowOf(app).getNextStartedActivityForResult();
      java.io.File file = new java.io.File(dir, (String) runs[i][1]);
      app.onActivityResult(picker.requestCode, -1, new android.content.Intent().setData(android.net.Uri.fromFile(file)));
      idle();
      out.append(runs[i][1]).append(": written ").append(file.length() > 0).append(", toast ").append(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()).append('\n');
    }
    for (PromptVault.StoredFile f : PromptVault.open(app.getFilesDir()).referenceFiles()) {
      out.append("ref: ").append(f.name).append(" (").append(f.promptTitle).append(")\n");
    }
    out.append("kept: ").append(app.getSharedPreferences(ProjectIo.EXPORT_PREFS, 0).getBoolean(ProjectIo.EXPORT_TO_DB, false)).append('\n');
    box.setChecked(false);
    write("s64_export_to_db", out.toString());
  }

  /** SogniChat Params: New chat clears "Continue from saved chat", so the next run starts a new chat; the other arguments stay. */
  @Test
  public void s63_new_chat() throws Exception {
    StringBuilder out = new StringBuilder();
    call("show", "py");
    idle();
    pickFromMenu("Java \u25be", "SogniChat.java");
    TextView args = (TextView) get("pkPyArgs");
    args.setText("--continue /sdcard/Download/sogni-chat-old.txt --prompt Why?");
    call("pkOpenParams");
    idle();
    AlertDialog d = (AlertDialog) ShadowDialog.getLatestDialog();
    View dv = d.getWindow().getDecorView();
    TextView chosen = (TextView) dv.findViewWithTag("params-chosen:--continue");
    android.widget.Button fresh = (android.widget.Button) dv.findViewWithTag("params-newchat:--continue");
    out.append("before: ").append(chosen.getText()).append(", button ").append(fresh == null ? "none" : fresh.getText()).append('\n');
    out.append("only on --continue: ").append(dv.findViewWithTag("params-newchat:--file") == null).append('\n');
    fresh.performClick();
    idle();
    out.append("after New chat: ").append(chosen.getText()).append('\n');
    d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    idle();
    out.append("args: ").append(args.getText()).append('\n');
    write("s63_new_chat", out.toString());
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
        .append(", label ").append(findText(dv, "File (text, MIDI, picture, audio or video)  --file") != null).append('\n');
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
    out.append("output: ").append(findText(dv, "Output name  (optional)") != null).append(", hint ")
        .append(((android.widget.EditText) dv.findViewWithTag("params-field:output_name")).getHint()).append('\n');
    out.append("--prompt hint: ").append(((android.widget.EditText) dv.findViewWithTag("params-field:--prompt")).getHint()).append('\n');
    // Chat model: a list of Sogni's chat models; picking one fills in its id.
    dv.findViewWithTag("params-choose:--model").performClick();
    idle();
    AlertDialog models = (AlertDialog) ShadowDialog.getLatestDialog();
    for (int i = 0; i < models.getListView().getAdapter().getCount(); i++) out.append("model choice: ").append(models.getListView().getAdapter().getItem(i)).append('\n');
    org.robolectric.Shadows.shadowOf(models).clickOnItem(1);
    idle();
    out.append("--model field: ").append(((android.widget.EditText) dv.findViewWithTag("params-field:--model")).getText()).append('\n');
    android.widget.EditText promptField = (android.widget.EditText) dv.findViewWithTag("params-field:--prompt");
    out.append("prompt field: lines up to ").append(promptField.getMaxLines())
        .append(", one line ").append((promptField.getInputType() & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0).append('\n');
    // Typed over two lines, it is still one argument.
    promptField.setText("Suggest\none fill");
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
