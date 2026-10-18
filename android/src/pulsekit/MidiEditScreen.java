package pulsekit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static pulsekit.MainActivity.*;

/**
 * The MIDI editor (long press on a file set's source.mid): the MIDI's hits on a grid like the
 * pattern's, ACC and a row per track, a column for each 16th. A tap adds or erases a hit (on ACC:
 * the step's hits loud, or back); Play, Pause and Stop play it as it is now; Save keeps it as the
 * file set's MIDI, Back leaves without saving.
 */
public final class MidiEditScreen {
  /** The editor open now (one at a time), for the playback clock and the tests. */
  static MidiEditScreen now;

  final MainActivity app;
  final String key;
  final String name;
  final MidiHitEdit edit;
  final Dialog dialog;
  final GridView grid;
  final HorizontalScrollView side;
  final TextView status;
  int[][] cells;
  /** The step under the playhead; -1 none. */
  int playhead = -1;
  /** Edits since the playback was made: Play makes it again from where it was. */
  boolean stale;
  /** Opened by an import (Open in MIDI editor): Back and Save go on to the file set's patterns. */
  boolean fromImport;

  static String playKey(String key) {
    return "edit:" + key;
  }

  /** Opens the editor on the file set's source MIDI; `label` is the set's row (or its source). */
  /** After an import with "Open in MIDI editor": Back and Save then show the Pattern view of its file set. */
  public static void openFromImport(MainActivity app, String label) {
    open(app, label);
    if (now != null) now.fromImport = true;
  }

  /** Save for a MIDI that is not a file set's (the Media browser's): writes it, returns where. */
  public interface Saver {
    String save(byte[] midi) throws Exception;
  }

  /** The editor on a MIDI file's bytes (`name`); Save hands the MIDI to `saver`. */
  public static void openFile(MainActivity app, String name, byte[] midi, Saver saver) {
    MidiHitEdit edit = MidiHitEdit.of(midi);
    if (edit == null) {
      app.setNow(name + " is not a MIDI file");
      return;
    }
    if (now != null) now.close();
    now = new MidiEditScreen(app, "", name, edit, saver);
  }

  public static void open(MainActivity app, String label) {
    String key = Engine.fileSetMidiKeyForLabel(label == null ? "" : label);
    Engine.FileSetAudio au = Engine.fileSetAudioOf(key);
    MidiHitEdit edit = au == null ? null : MidiHitEdit.of(au.sourceMidi);
    if (edit == null) {
      app.setNow("That MIDI file is missing");
      return;
    }
    if (now != null) now.close();
    now = new MidiEditScreen(app, key, Engine.fileSetMidiName(key), edit, null);
  }

  /** Where Save puts a MIDI that is not a file set's; null for a file set's. */
  final Saver saver;

  private MidiEditScreen(final MainActivity app, String key, String name, MidiHitEdit edit, Saver saver) {
    this.saver = saver;
    this.app = app;
    this.key = key;
    this.name = name;
    this.edit = edit;
    this.cells = edit.cells();
    LinearLayout page = app.col();
    page.setTag("midiedit");
    page.setBackgroundColor(BG);
    page.setPadding(app.dp(14), app.dp(12), app.dp(14), app.dp(12));
    TextView title = app.text("MIDI editor", 22, true);
    page.addView(title);
    TextView sub = app.text(saver != null ? name + " \u00b7 Save keeps it as a new file beside it" : (key.isEmpty() ? "Other" : key) + " · " + name, 13, false);
    sub.setTextColor(MUTED);
    sub.setPadding(0, app.dp(4), 0, app.dp(2));
    page.addView(sub);
    this.status = app.text("", 13, false);
    this.status.setTag("midiedit-status");
    this.status.setTextColor(HIT);
    this.status.setPadding(0, app.dp(2), 0, app.dp(6));
    page.addView(this.status);

    LinearLayout transport = app.row();
    TextView clock = app.text("00:00 / 00:00", 11, false);
    clock.setTag("midiclock");
    clock.setTextColor(MUTED);
    clock.setTypeface(android.graphics.Typeface.MONOSPACE);
    transport.addView(clock, app.flex(1));
    TextView play = app.outline("Play", false, v -> this.play());
    play.setTag("midiedit-play");
    TextView pause = app.outline("Pause", false, v -> app.fileSets.pauseSourceMidi());
    pause.setTag("midiedit-pause");
    TextView stop = app.outline("Stop", false, v -> app.fileSets.stopSourceMidi());
    stop.setTag("midiedit-stop");
    transport.addView(play);
    transport.addView(pause);
    transport.addView(stop);
    page.addView(transport);

    // Track names stay put; the steps scroll sideways under them.
    LinearLayout gridRow = app.row();
    gridRow.setPadding(0, app.dp(8), 0, app.dp(8));
    LinearLayout names = app.col();
    names.addView(this.rowName("ACC", ACCENT));
    for (String s : Engine.TRACK_SHORT) names.addView(this.rowName(s, FG));
    gridRow.addView(names);
    this.grid = new GridView(app);
    this.grid.setTag("midiedit-grid");
    this.side = new HorizontalScrollView(app);
    this.side.setTag("midiedit-scroll");
    this.side.addView(this.grid);
    gridRow.addView(this.side, app.flex(1));
    ScrollView tall = new ScrollView(app);
    tall.addView(gridRow);
    page.addView(tall, new LinearLayout.LayoutParams(-1, 0, 1f));

    LinearLayout buttons = app.row();
    buttons.setPadding(0, app.dp(8), 0, 0);
    TextView back = app.action("Back", ELEV, FG, v -> this.back());
    back.setTag("midiedit-back");
    TextView save = app.action("Save", HIT, BG, v -> this.save());
    save.setTag("midiedit-save");
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.dp(48), 1f);
    lp.setMargins(0, 0, app.dp(8), 0);
    buttons.addView(back, lp);
    buttons.addView(save, new LinearLayout.LayoutParams(0, app.dp(48), 1f));
    page.addView(buttons);

    this.dialog = new Dialog(app, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    this.dialog.setContentView(page);
    // The phone's back key is Back: nothing is saved.
    this.dialog.setOnCancelListener(d -> this.back());
    this.showStatus();
    this.dialog.show();
  }

  TextView rowName(String s, int color) {
    TextView t = this.app.labelCell(s, color);
    t.setLayoutParams(new LinearLayout.LayoutParams(this.app.dp(48), this.app.dp(GridView.ROW_DP)));
    return t;
  }

  void showStatus() {
    String s = this.edit.hits() + " hits · " + this.edit.bars() + (this.edit.bars() == 1 ? " bar" : " bars") + " · " + this.edit.bpm + " BPM";
    this.status.setText(this.edit.changed ? s + " · not saved" : s);
  }

  /** A tap on the grid: row 0 is ACC, then the tracks. */
  void tap(int row, int step) {
    if (step < 0 || step >= this.edit.steps()) return;
    if (row == 0) {
      boolean any = false;
      for (int t = 0; t < this.cells.length; t++) if (this.cells[t][step] > 0) any = true;
      if (!any) {
        this.app.setNow("No hits on that step to accent");
        return;
      }
      this.edit.toggleAccent(step);
    } else if (row - 1 < this.edit.tracks()) {
      this.edit.toggle(row - 1, step);
    } else return;
    this.stale = true;
    this.cells = this.edit.cells();
    this.grid.requestLayout();
    this.grid.invalidate();
    this.showStatus();
  }

  void play() {
    FileSets fs = this.app.fileSets;
    String src = playKey(this.key);
    if (!this.stale && src.equals(fs.pkMidiSrc) && fs.pkMidiPlayer != null && fs.pkMidiPaused) {
      fs.playSourceMidi(src);
      return;
    }
    int from = 0;
    if (src.equals(fs.pkMidiSrc) && fs.pkMidiPlayer != null && fs.pkMidiPaused) {
      try {
        from = fs.pkMidiPlayer.getCurrentPosition();
      } catch (Exception ignored) {
        from = 0;
      }
    }
    this.stale = false;
    fs.playMidiBytes(src, this.edit.toMidi(), this.name + " (edited)", from);
  }

  /** The playback clock: the playhead follows the editor's own playback. */
  void clock(String src, int ms, boolean going) {
    int at = src != null && src.equals(playKey(this.key)) ? this.edit.stepAtMillis(ms) : -1;
    if (at == this.playhead) return;
    this.playhead = at;
    this.grid.invalidate();
    if (at >= 0) this.follow(at);
  }

  /** While it plays, the grid scrolls with the beat: the playhead stays in view, a quarter of the way in. */
  void follow(int step) {
    int x = this.grid.stepX(Math.min(step, this.edit.steps() - 1));
    int width = this.side.getWidth();
    if (width <= 0) return;
    int left = this.side.getScrollX();
    if (x >= left && x + this.grid.cellW() <= left + width * 3 / 4) return;
    this.side.smoothScrollTo(Math.max(0, x - width / 4), 0);
  }

  void save() {
    if (this.saver != null && this.edit.changed) {
      try {
        this.app.setNow("Saved \u00b7 " + this.saver.save(this.edit.toMidi()));
      } catch (Exception ex) {
        this.app.setNow("Could not save: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
      }
    } else if (this.edit.changed) {
      MidiHitEdit.saveToFileSet(this.key, this.edit.toMidi());
      Engine.storeFileSetAudioDir(new java.io.File(this.app.getFilesDir(), "fset-audio"));
      this.app.setNow("Saved · " + this.name);
    } else this.app.setNow("No changes to save");
    this.close();
    this.toPatterns();
  }

  void back() {
    this.app.setNow(this.edit.changed ? "Back · changes not saved" : "Back");
    this.close();
    this.toPatterns();
  }

  /** After an import: the Pattern view, its file set open there. */
  void toPatterns() {
    if (!this.fromImport) return;
    this.fromImport = false;
    this.app.importLibrary.openPacks.put("f:" + this.key, true);
    this.app.show("pattern");
    this.app.importLibrary.rebuildImported();
  }

  void close() {
    if (now == this) now = null;
    String src = playKey(this.key);
    if (src.equals(this.app.fileSets.pkMidiSrc)) this.app.fileSets.stopSourceMidi();
    if (this.dialog.isShowing()) this.dialog.dismiss();
  }

  /** The grid, drawn in one view: a song can have hundreds of steps. */
  final class GridView extends View {
    static final int CELL_DP = 34;
    static final int ROW_DP = 42;
    static final int GAP_DP = 4;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    final RectF r = new RectF();

    GridView(Context c) {
      super(c);
      this.stroke.setStyle(Paint.Style.STROKE);
      this.stroke.setStrokeWidth(Math.max(2, app.dp(2)));
      this.stroke.setColor(ACCENT);
      this.label.setTextAlign(Paint.Align.CENTER);
      this.label.setTextSize(app.dp(12));
      this.label.setFakeBoldText(true);
    }

    int cellW() {
      return app.dp(CELL_DP) + app.dp(GAP_DP);
    }

    int rowH() {
      return app.dp(ROW_DP);
    }

    @Override
    protected void onMeasure(int w, int h) {
      // A gap after each bar.
      int width = edit.steps() * this.cellW() + edit.bars() * app.dp(GAP_DP);
      this.setMeasuredDimension(width, (edit.tracks() + 1) * this.rowH());
    }

    int stepX(int step) {
      return step * this.cellW() + (step / edit.barSteps) * app.dp(GAP_DP);
    }

    int stepAt(float x) {
      int bar = edit.barSteps * this.cellW() + app.dp(GAP_DP);
      int b = (int) (x / bar);
      int in = (int) ((x - b * bar) / this.cellW());
      return b * edit.barSteps + Math.min(edit.barSteps - 1, in);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      int cw = app.dp(CELL_DP);
      int pad = app.dp(GAP_DP) / 2;
      float radius = app.dp(6);
      int perBeat = Math.max(1, Engine.stepsPerBeat(edit.tsDen));
      for (int s = 0; s < edit.steps(); s++) {
        int x = this.stepX(s);
        boolean beat = s % perBeat == 0;
        boolean head = s == playhead;
        boolean acc = edit.accent(s);
        for (int row = 0; row <= edit.tracks(); row++) {
          int y = row * this.rowH();
          this.r.set(x, y + pad, x + cw, y + this.rowH() - pad);
          if (row == 0) {
            this.fill.setColor(acc ? ACCENT : (head ? FG : ELEV));
            canvas.drawRoundRect(this.r, radius, radius, this.fill);
            String t = edit.beatLabel(s);
            this.label.setColor(acc || head ? BG : FG);
            canvas.drawText(t.isEmpty() ? (acc ? "▾" : "·") : t, this.r.centerX(), this.r.centerY() + app.dp(4), this.label);
            continue;
          }
          int vel = cells[row - 1][s];
          int color = vel > 0 ? (vel >= MidiHitEdit.ACCENT_FROM ? ACCENT : HIT) : (beat ? SURFACE_BEAT : ELEV);
          this.fill.setColor(color);
          if (vel > 0) this.fill.setAlpha(Math.max(110, Math.min(255, 80 + vel * 175 / 127)));
          canvas.drawRoundRect(this.r, radius, radius, this.fill);
          this.fill.setAlpha(255);
          if (head) canvas.drawRoundRect(this.r, radius, radius, this.stroke);
        }
      }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
      if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
      if (e.getAction() == MotionEvent.ACTION_UP) {
        int row = (int) (e.getY() / this.rowH());
        tap(row, this.stepAt(e.getX()));
        this.performClick();
        return true;
      }
      return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
      return super.performClick();
    }
  }

  /** Empty cells on a beat, a little lighter than the others. */
  static final int SURFACE_BEAT = android.graphics.Color.parseColor("#24272A");
}
