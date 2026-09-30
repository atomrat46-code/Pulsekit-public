package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.text.Html;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import pulsekit.AudioIo;
import pulsekit.Engine;
import pulsekit.FlowLayout;

public class MainActivity extends UiKit {
    static final int OPEN = 7;

    static final int SAVE_MIDI = 8;

    static final int SAVE_SNG = 9;

    static final int SAVE_WAV = 10;

    static final int SAVE_MP3 = 11;

    static final int SAVE_SF2 = 12;

    static final int OPEN_PAD = 13;

    static final int SAVE_PY = 14;

    static final int SAVE_PRJ = 15;

    static final int SAVE_PKP = 16;

    static final int SAVE_SONG_MIDI = 17;

    static final int SAVE_JAR = 18;

    static final int SAVE_FSET = 19;

    String midiSaveRole = "pattern";

    String fsetSaveSource = null;

    final Map<String, Engine.Style> styles = Engine.styles();

    final int[][] cells = Engine.emptyCells();

    final int[][] fillPat = Engine.fillCells("toms");

    final int[][] lens = Engine.emptyCells();

    final int[][] fillLens = Engine.emptyCells();

    final boolean[] accents = new boolean[32];

    final boolean[] mutes = new boolean[Engine.TRACK_ID.length];

    final TextView[][] grid = new TextView[Engine.TRACK_ID.length][32];

    TextView accLab;

    final TextView[] accCells = new TextView[32];

    final TextView[] padBtns = new TextView[Engine.TRACK_ID.length];

    final List<Engine.Part> song = new ArrayList<Engine.Part>();

    final List<Engine.ImportedSong> importedSongs = new ArrayList<Engine.ImportedSong>();

    String songLane = "original";

    String importedSongId;

    final List<Engine.Learned> learned = new ArrayList<Engine.Learned>();

    final List<Engine.LearnedFill> learnedFills = new ArrayList<Engine.LearnedFill>();

    final List<Engine.LearnedFill> variatedFills = new ArrayList<Engine.LearnedFill>();

    final List<Engine.Learned> variatedPatterns = new ArrayList<Engine.Learned>();

    final Map<String, String> fillernPairs = new LinkedHashMap<String, String>();

    final Handler handler = new Handler(Looper.getMainLooper());

    int paintLen;

    LinearLayout lenBar;

    final TextView[] lenChips = new TextView[Engine.LEN_STEPS.length];

    final short[][] voices = new short[Engine.NOTES.length][];

    final List<Engine.DrumSet> drumSets = new ArrayList<Engine.DrumSet>();

    FlowLayout drumSetBar;

    TextView padMatchAll;

    final List<TextView> tabs = new ArrayList<TextView>();

    TextView now;

    TextView swingVal;

    TextView densVal;

    TextView humanVal;

    FlowLayout styleBar;

    LinearLayout importedHost;

    LinearLayout importedFillHost;

    LinearLayout importedFileList;

    FlowLayout variatedPatternBar;

    LinearLayout styleWrap;

    LinearLayout fillWrap;

    FlowLayout variatedFillBar;

    FlowLayout fillBar;

    LinearLayout chrome;

    LinearLayout body;

    LinearLayout gridPane;

    LinearLayout songCards;

    LinearLayout knobsRow;

    LinearLayout toolsRow;

    HorizontalScrollView toolsScroll;

    ScrollView gridScroll;

    LinearLayout padsPane;

    LinearLayout songPane;

    LinearLayout pyPane;

    EditText pyEditor;

    String pyName = "drum_midi.py";

    LinearLayout importPane;

    LinearLayout exportPane;

    LinearLayout songAdds;

    LinearLayout timeline;

    TextView playBtn;

    TextView muteBtn;

    TextView stepsBtn;

    EditText bpmLabel;

    EditText tsNumField;

    EditText tsDenField;

    RangeBar bpmBar;

    RangeBar swingBar;

    RangeBar densBar;

    RangeBar humanBar;

    String style = "house";

    String view = "pattern";

    String fillId = "toms";

    String songMode = "edit";

    volatile boolean playing;

    volatile boolean songPlay;

    boolean muted;

    boolean livePads;

    boolean pluginGhostHats;

    boolean fillLast;

    int playhead = -1;

    int songIndex;

    int songLoop;

    int barLoop;

    int bars = 4;

    int steps = 16;

    int tsNum = 4;

    int tsDen = 4;

    int padTarget = -1;

    AudioTrack track;

    boolean fillVariated;

    final Object mixLock = new Object();

    final int[] mixPos = new int[Engine.NOTES.length];

    Thread mixThread;

    volatile boolean mixRun;

    int shownHead = -1;

    void onCreateBase(Bundle bundle) {
        super.onCreate(bundle);
        if (Build.VERSION.SDK_INT >= 21) {
            this.getWindow().setStatusBarColor(BG);
            this.getWindow().setNavigationBarColor(SURFACE);
        }
        Arrays.fill(this.mixPos, -1);
        this.drumSets.add(Engine.DrumSet.originalSet());
        Engine.defaultAccents(this.accents, this.steps, Engine.stepsPerBeat(this.tsDen));
        this.styleLibrary.loadStyle("house", false);
        this.setContentView(this.buildUi());
        this.persistence.restoreSession();
        this.styleLibrary.refreshStyles();
        this.styleLibrary.refreshFills();
        this.gridEditor.refreshGrid();
        this.gridEditor.applyGridWidth();
        this.refreshTabs();
        this.songEditor.refreshSong();
    }

    View buildUiBase() {
        LinearLayout.LayoutParams layoutParams;
        TextView textView;
        LinearLayout linearLayout;
        TextView textView2;
        TextView textView3;
        int n2;
        TextView textView4;
        LinearLayout linearLayout2 = this.col();
        linearLayout2.setBackgroundColor(BG);
        int n3 = this.dp(12);
        linearLayout2.setPadding(n3, n3, n3, 0);
        LinearLayout linearLayout3 = this.row();
        linearLayout3.setGravity(16);
        LinearLayout linearLayout4 = this.col();
        TextView textView5 = this.text("DRUM MIDI", 8, true);
        textView5.setTextColor(SUBTLE);
        textView5.setLetterSpacing(0.2f);
        textView5.setIncludeFontPadding(false);
        TextView textView6 = this.text("PULSEKIT", 16, true);
        textView6.setLetterSpacing(0.08f);
        textView6.setIncludeFontPadding(false);
        linearLayout4.addView((View)textView5);
        linearLayout4.addView((View)textView6);
        linearLayout3.addView((View)linearLayout4, (ViewGroup.LayoutParams)this.flex(1));
        LinearLayout linearLayout5 = this.col();
        linearLayout5.setGravity(0x800005);
        this.bpmLabel = this.gridEditor.numField("124", 18, 48);
        TextView textView7 = this.text("BPM", 8, true);
        textView7.setTextColor(SUBTLE);
        textView7.setGravity(0x800005);
        textView7.setLetterSpacing(0.12f);
        linearLayout5.addView((View)this.bpmLabel);
        linearLayout5.addView((View)textView7);
        linearLayout3.addView((View)linearLayout5, (ViewGroup.LayoutParams)this.wrap());
        LinearLayout linearLayout6 = this.col();
        linearLayout6.setGravity(0x800005);
        LinearLayout linearLayout7 = this.row();
        linearLayout7.setGravity(0x800005);
        this.tsNumField = this.gridEditor.numField("4", 14, 24);
        this.tsDenField = this.gridEditor.numField("4", 14, 24);
        TextView textView8 = this.text("/", 14, true);
        textView8.setTextColor(MUTED);
        linearLayout7.addView((View)this.tsNumField);
        linearLayout7.addView((View)textView8);
        linearLayout7.addView((View)this.tsDenField);
        TextView textView9 = this.text("TS", 8, true);
        textView9.setTextColor(SUBTLE);
        textView9.setGravity(0x800005);
        textView9.setLetterSpacing(0.12f);
        linearLayout6.addView((View)linearLayout7);
        linearLayout6.addView((View)textView9);
        LinearLayout.LayoutParams layoutParams2 = this.wrap();
        layoutParams2.setMargins(this.dp(10), 0, 0, 0);
        linearLayout6.setLayoutParams((ViewGroup.LayoutParams)layoutParams2);
        linearLayout3.addView((View)linearLayout6);
        this.gridEditor.bindNumField(this.bpmLabel, 40, 240, n -> {
            if (this.bpmBar != null) {
                this.bpmBar.setVal(n);
            }
        });
        this.gridEditor.bindNumField(this.tsNumField, 1, 16, n -> this.gridEditor.applyTimeSig(n, this.tsDen));
        this.gridEditor.bindNumField(this.tsDenField, 2, 16, n -> this.gridEditor.applyTimeSig(this.tsNum, n));
        this.muteBtn = this.outline("Mute", false, view -> {
            this.muted = !this.muted;
            this.muteBtn.setText((CharSequence)(this.muted ? "Unmute" : "Mute"));
            this.paintOutline(this.muteBtn, this.muted);
        });
        LinearLayout.LayoutParams layoutParams3 = this.wrap();
        layoutParams3.setMargins(this.dp(8), 0, 0, 0);
        this.muteBtn.setLayoutParams((ViewGroup.LayoutParams)layoutParams3);
        linearLayout3.addView((View)this.muteBtn);
        linearLayout2.addView((View)linearLayout3);
        LinearLayout linearLayout8 = this.col();
        linearLayout8.setPadding(0, this.dp(8), 0, this.dp(2));
        LinearLayout linearLayout9 = this.row();
        this.addTab(linearLayout9, "pattern", "Pattern");
        this.addTab(linearLayout9, "combo", "Fillern");
        this.addTab(linearLayout9, "fills", "Fills");
        this.addTab(linearLayout9, "pads", "Pads");
        LinearLayout linearLayout10 = this.row();
        linearLayout10.setPadding(0, this.dp(4), 0, 0);
        this.addTab(linearLayout10, "song", "Song");
        this.addTab(linearLayout10, "py", "PyJav");
        this.addFileTab(linearLayout10);
        linearLayout8.addView((View)linearLayout9);
        linearLayout8.addView((View)linearLayout10);
        linearLayout2.addView((View)linearLayout8);
        this.now = this.text(" ", 11, true);
        this.now.setTextColor(HIT);
        this.now.setPadding(0, this.dp(4), 0, this.dp(2));
        this.now.setVisibility(8);
        linearLayout2.addView((View)this.now);
        this.chrome = this.col();
        this.styleWrap = this.col();
        this.styleWrap.addView((View)this.sectionLabel("Built-in"));
        this.styleBar = new FlowLayout((Context)this, this.dp(6), this.dp(6));
        this.styleBar.setSingleLine(true);
        for (Engine.Style style2 : this.styles.values()) {
            TextView textView10 = this.pill(style2.label, false, view -> this.styleLibrary.loadStyle(style2.id, false));
            textView10.setTag((Object)style2.id);
            this.styleLibrary.attachBuiltinStyleMenu(textView10, style2.id);
            this.styleBar.addView((View)textView10);
        }
        this.styleWrap.addView((View)this.chipStrip((View)this.styleBar));
        this.styleWrap.addView((View)this.sectionLabel("Variated"));
        this.variatedPatternBar = new FlowLayout((Context)this, this.dp(6), this.dp(6));
        this.variatedPatternBar.setSingleLine(true);
        TextView textView11 = this.text("Variate a groove or add a fill", 12, false);
        textView11.setTextColor(MUTED);
        textView11.setTag((Object)"imported-empty");
        this.variatedPatternBar.addView((View)textView11);
        this.styleWrap.addView((View)this.chipStrip((View)this.variatedPatternBar));
        this.styleWrap.addView((View)this.sectionLabel("Imported"));
        this.importedHost = this.col();
        TextView emptyStyles = this.text("MIDI, song or plugin pack", 12, false);
        emptyStyles.setTextColor(MUTED);
        emptyStyles.setTag("imported-empty");
        this.importedHost.addView((View)emptyStyles);
        this.styleWrap.addView((View)this.importedHost);
        this.chrome.addView((View)this.styleWrap);
        this.fillWrap = this.col();
        this.fillWrap.addView((View)this.sectionLabel("Built-in"));
        this.fillBar = new FlowLayout((Context)this, this.dp(6), this.dp(6));
        this.fillBar.setSingleLine(true);
        for (int i = 0; i < Engine.FILL_ID.length; ++i) {
            String string = Engine.FILL_ID[i];
            textView4 = this.pill(Engine.FILL_LABEL[i], false, view -> this.styleLibrary.applyFill(string));
            textView4.setTag((Object)string);
            this.fillBar.addView((View)textView4);
        }
        this.fillBar.addView((View)this.pill("Variate", false, view -> this.styleLibrary.variateFill()));
        this.fillBar.addView((View)this.pill("Apply", false, view -> this.show("combo")));
        this.fillWrap.addView((View)this.chipStrip((View)this.fillBar));
        this.fillWrap.addView((View)this.sectionLabel("Variated"));
        this.variatedFillBar = new FlowLayout((Context)this, this.dp(6), this.dp(6));
        this.variatedFillBar.setSingleLine(true);
        TextView textView12 = this.text("Variate to keep a copy here", 12, false);
        textView12.setTextColor(MUTED);
        textView12.setTag((Object)"imported-empty");
        this.variatedFillBar.addView((View)textView12);
        this.fillWrap.addView((View)this.chipStrip((View)this.variatedFillBar));
        this.fillWrap.addView((View)this.sectionLabel("Imported"));
        this.importedFillHost = this.col();
        TextView emptyFills = this.text("Last bar of an imported MIDI", 12, false);
        emptyFills.setTextColor(MUTED);
        emptyFills.setTag((Object)"imported-empty");
        this.importedFillHost.addView((View)emptyFills);
        this.fillWrap.addView((View)this.importedFillHost);
        this.fillWrap.setVisibility(8);
        this.chrome.addView((View)this.fillWrap);
        this.knobsRow = this.row();
        this.knobsRow.setPadding(0, this.dp(8), 0, 0);
        this.bpmBar = this.knob(this.knobsRow, "TEMPO", 40, 240, 124, n -> this.bpmLabel.setText((CharSequence)Integer.toString(n)));
        this.swingBar = this.knob(this.knobsRow, "SWING", 0, 75, 12, n -> this.swingVal.setText((CharSequence)(n + "%")));
        this.densBar = this.knob(this.knobsRow, "DENSITY", 1, 10, 5, n -> this.densVal.setText((CharSequence)Integer.toString(n)));
        this.humanBar = this.knob(this.knobsRow, "HUMAN", 0, 100, 18, n -> this.humanVal.setText((CharSequence)(n + "%")));
        this.chrome.addView((View)this.knobsRow);
        this.toolsRow = this.row();
        this.toolsRow.setPadding(0, this.dp(4), 0, this.dp(4));
        this.stepsBtn = this.outline("16 steps", false, view -> this.gridEditor.toggleSteps());
        this.toolsRow.addView((View)this.stepsBtn);
        textView4 = this.outline("4 bars out", false, view -> {
            this.bars = this.bars == 4 ? 8 : (this.bars == 8 ? 2 : 4);
            ((TextView)view).setText((CharSequence)(this.bars + " bars out"));
        });
        this.toolsRow.addView((View)textView4);
        TextView textView13 = this.outline("Variate", false, view -> this.styleLibrary.variatePattern("random"));
        textView13.setOnLongClickListener(view -> {
            new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Variate pattern").setItems(new CharSequence[]{"Random groove", "With this fill", "With random fill"}, (dialogInterface, n) -> {
                if (n == 0) {
                    this.styleLibrary.variatePattern("random");
                } else if (n == 1) {
                    this.styleLibrary.variatePattern("fill");
                } else {
                    this.styleLibrary.variatePattern("random-fill");
                }
            }).show();
            return true;
        });
        this.toolsRow.addView((View)textView13);
        this.toolsRow.addView((View)this.outline("Silent", false, view -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < 16; ++j) {
                    this.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(this.lens);
            this.styleLibrary.syncBuiltinFill();
            this.gridEditor.refreshGrid();
            this.setNow("Silent pattern");
        }));
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView((Context)this);
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        horizontalScrollView.addView((View)this.toolsRow);
        this.chrome.addView((View)horizontalScrollView);
        this.body = this.col();
        this.body.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.chrome.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        this.body.addView((View)this.chrome);
        FrameLayout frameLayout = new FrameLayout((Context)this);
        frameLayout.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, 0, 1.0f));
        this.gridPane = this.col();
        this.gridPane.setClipChildren(true);
        LinearLayout linearLayout11 = this.row();
        linearLayout11.setClipChildren(true);
        linearLayout11.setPadding(0, 0, 0, this.dp(2));
        if (Build.VERSION.SDK_INT >= 21) {
            linearLayout11.setElevation((float)this.dp(6));
        }
        this.accLab = this.labelCell("ACC", FG);
        this.accLab.setClickable(true);
        this.gridEditor.bindAccCell(this.accLab);
        this.accLab.setOnClickListener(view -> {
            int n;
            boolean bl = false;
            for (n = 0; n < this.steps; ++n) {
                if (!this.accents[n]) continue;
                bl = true;
                break;
            }
            if (bl) {
                for (n = 0; n < 32; ++n) {
                    this.accents[n] = false;
                }
            } else {
                Engine.defaultAccents(this.accents, this.steps, Engine.stepsPerBeat(this.tsDen));
            }
            view.performHapticFeedback(3);
            this.gridEditor.refreshGrid();
        });
        linearLayout11.addView((View)this.accLab);
        for (n2 = 0; n2 < 32; ++n2) {
            int n4 = n2;
            TextView textView14 = this.cell(n2 % 4 == 0 ? Integer.toString(n2 / 4 + 1) : "\u00b7", view -> {
                this.accents[n4] = !this.accents[n4];
                view.performHapticFeedback(3);
                this.gridEditor.refreshGrid();
            });
            textView14.setTag((Object)("acc-" + n2));
            this.gridEditor.bindAccCell(textView14);
            this.accCells[n2] = textView14;
            linearLayout11.addView((View)textView14, (ViewGroup.LayoutParams)this.accLp());
        }
        this.gridPane.addView((View)linearLayout11);
        for (n2 = 0; n2 < Engine.TRACK_ID.length; ++n2) {
            LinearLayout linearLayout12 = this.row();
            linearLayout12.setClipChildren(true);
            linearLayout12.setMinimumHeight(this.dp(40));
            int n5 = n2;
            textView3 = this.labelCell(Engine.TRACK_SHORT[n2], MUTED);
            textView3.setOnClickListener(view -> {
                if (this.mutes[n5]) {
                    this.mutes[n5] = false;
                    this.gridEditor.refreshGrid();
                    return;
                }
                this.playback.bang(n5, 110);
            });
            textView3.setOnLongClickListener(view -> {
                this.mutes[n5] = !this.mutes[n5];
                this.gridEditor.refreshGrid();
                return true;
            });
            linearLayout12.addView((View)textView3);
            for (int i = 0; i < 32; ++i) {
                int n6 = i;
                textView2 = this.cell("", view -> {
                    if (this.paintLen > 0) {
                        this.gridEditor.setCellLen(n5, n6, this.paintLen);
                        return;
                    }
                    int[][] nArray = this.gridEditor.editCells();
                    int[][] nArray2 = this.gridEditor.editLens();
                    int v = nArray[n5][n6];
                    nArray[n5][n6] = v <= 0 ? 100 : (v < 90 ? 0 : (v < 120 ? 127 : 64));
                    if (nArray[n5][n6] <= 0) {
                        nArray2[n5][n6] = 0;
                    }
                    this.gridEditor.refreshGrid();
                    if (!"fills".equals(this.view)) {
                        this.styleLibrary.syncBuiltinFill();
                    }
                });
                this.gridEditor.bindCellLength(textView2, n5, n6);
                this.grid[n2][i] = textView2;
                linearLayout12.addView((View)textView2, (ViewGroup.LayoutParams)this.cellLp());
            }
            this.gridPane.addView((View)linearLayout12);
        }
        HorizontalScrollView horizontalScrollView2 = new HorizontalScrollView((Context)this);
        horizontalScrollView2.setHorizontalScrollBarEnabled(false);
        horizontalScrollView2.setFillViewport(true);
        horizontalScrollView2.setOverScrollMode(2);
        horizontalScrollView2.addView((View)this.gridPane);
        this.gridScroll = new ScrollView((Context)this);
        this.gridScroll.setFillViewport(true);
        this.gridScroll.setOverScrollMode(2);
        this.gridScroll.setPadding(0, this.dp(4), 0, this.dp(8));
        this.gridScroll.setClipToPadding(false);
        this.gridScroll.addView((View)horizontalScrollView2);
        frameLayout.addView((View)this.gridScroll);
        this.padsPane = this.col();
        this.padsPane.setVisibility(8);
        this.padsPane.addView((View)this.text("Drum sets", 11, true));
        HorizontalScrollView horizontalScrollView3 = new HorizontalScrollView((Context)this);
        horizontalScrollView3.setHorizontalScrollBarEnabled(false);
        this.drumSetBar = new FlowLayout((Context)this, this.dp(6), this.dp(6));
        this.drumSetBar.setSingleLine(true);
        horizontalScrollView3.addView((View)this.drumSetBar);
        this.padsPane.addView((View)horizontalScrollView3);
        LinearLayout linearLayout13 = this.row();
        textView3 = this.outline("Live", false, view -> {
            this.livePads = !this.livePads;
            this.paintOutline((TextView)view, this.livePads);
            this.setNow(this.livePads ? "Pads write the pattern" : "Hold a pad");
        });
        linearLayout13.addView((View)textView3);
        linearLayout13.addView((View)this.outline("Silent", false, view -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < 16; ++j) {
                    this.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(this.lens);
            this.styleLibrary.syncBuiltinFill();
            this.gridEditor.refreshGrid();
        }));
        this.padMatchAll = this.outline("Match Original", true, view -> this.playback.matchWholeOriginal());
        this.padMatchAll.setVisibility(8);
        linearLayout13.addView((View)this.padMatchAll);
        TextView textView15 = this.text("  Tap Match Original for the whole kit", 11, false);
        textView15.setTextColor(SUBTLE);
        linearLayout13.addView((View)textView15);
        this.padsPane.addView((View)linearLayout13);
        ScrollView scrollView = new ScrollView((Context)this);
        LinearLayout padGrid = this.col();
        for (int i = 0; i < 3; ++i) {
            linearLayout = this.row();
            for (int j = 0; j < 4; ++j) {
                int n7 = i * 4 + j;
                if (n7 >= Engine.TRACK_ID.length) {
                    linearLayout.addView(new View((Context)this), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, this.dp(96), 1.0f));
                    continue;
                }
                int n8 = n7;
                this.padBtns[n7] = textView = this.pad(Engine.TRACK_SHORT[n7], Engine.TRACK_LABEL[n7], view -> {
                    this.playback.bang(n8, 110);
                    this.playback.flashPad(n8);
                    if (this.livePads) {
                        int step = this.playhead >= 0 ? this.playhead : 0;
                        this.cells[n8][step] = this.cells[n8][step] > 0 ? 0 : 100;
                        if (this.cells[n8][step] <= 0) {
                            this.lens[n8][step] = 0;
                        }
                        this.styleLibrary.syncBuiltinFill();
                        this.gridEditor.refreshGrid();
                    }
                });
                textView.setOnLongClickListener(view -> {
                    this.padTarget = n8;
                    this.playback.showPadMenu(n8);
                    return true;
                });
                layoutParams = new LinearLayout.LayoutParams(0, this.dp(96), 1.0f);
                layoutParams.setMargins(this.dp(4), this.dp(4), this.dp(4), this.dp(4));
                textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
                linearLayout.addView((View)textView);
            }
            padGrid.addView((View)linearLayout);
        }
        scrollView.addView((View)padGrid);
        this.padsPane.addView((View)scrollView, (ViewGroup.LayoutParams)this.flexFill());
        frameLayout.addView((View)this.padsPane);
        this.playback.refreshDrumSets();
        this.songPane = this.col();
        this.songPane.setVisibility(8);
        LinearLayout linearLayout14 = this.row();
        TextView editPill = this.pill("Edit", true, view -> {
            this.songMode = "edit";
            this.chrome.setVisibility(0);
            this.styleWrap.setVisibility(8);
            this.fillWrap.setVisibility(8);
            this.knobsRow.setVisibility(8);
            this.songEditor.refreshSong();
        });
        editPill.setTag((Object)"edit");
        TextView textView16 = this.pill("Play", false, view -> {
            this.songMode = "play";
            this.chrome.setVisibility(8);
            this.songEditor.refreshSong();
        });
        textView16.setTag((Object)"play");
        linearLayout14.addView((View)editPill);
        linearLayout14.addView((View)textView16);
        this.songPane.addView((View)linearLayout14);
        LinearLayout linearLayout15 = this.row();
        TextView textView17 = this.pill("Original", true, view -> {
            this.songLane = "original";
            this.songEditor.refreshSong();
        });
        textView17.setTag((Object)"original");
        textView = this.pill("Imported", false, view -> {
            this.songLane = "imported";
            if (this.importedSongId == null && !this.importedSongs.isEmpty()) {
                this.importedSongId = this.importedSongs.get((int)0).id;
            }
            this.songEditor.refreshSong();
        });
        textView.setTag((Object)"imported");
        linearLayout15.addView((View)textView17);
        linearLayout15.addView((View)textView);
        this.songPane.addView((View)linearLayout15);
        this.songAdds = this.row();
        TextView patternPill = this.pill("Pattern \u00d74", false, view -> {
            Engine.Part part = Engine.groove(this.styles.get((Object)this.style).label, this.bpm(), this.cells, 4);
            part.lens = Engine.copyCells(this.lens);
            this.songEditor.add(part);
        });
        this.songEditor.attachSongPick(patternPill, -1, "pattern");
        TextView textView18 = this.pill("Fillern", false, view -> this.songEditor.addCurrentFillern());
        this.songEditor.attachSongPick(textView18, -1, "fillern");
        TextView textView19 = this.pill("Fill", false, view -> {
            Engine.Part part = Engine.fill(this.styleLibrary.fillLabel(this.fillId), this.bpm(), this.fillPat, 1);
            part.lens = Engine.copyCells(this.fillLens);
            this.songEditor.add(part);
        });
        this.songEditor.attachSongPick(textView19, -1, "fill");
        this.songAdds.addView((View)patternPill);
        this.songAdds.addView((View)textView18);
        this.songAdds.addView((View)textView19);
        this.songAdds.addView((View)this.pill("Silent", false, view -> this.songEditor.add(Engine.rest(this.bpm(), 2))));
        this.songAdds.addView((View)this.pill("Clear", false, view -> {
            if ("imported".equals(this.songLane) && this.importedSongId != null) {
                String string = this.importedSongId;
                this.importedSongs.removeIf(importedSong -> string.equals(importedSong.id));
                this.importedSongId = this.importedSongs.isEmpty() ? null : this.importedSongs.get((int)0).id;
                this.persistence.persistLearned();
            } else {
                this.song.clear();
            }
            this.songEditor.refreshSong();
        }));
        HorizontalScrollView horizontalScrollView4 = new HorizontalScrollView((Context)this);
        horizontalScrollView4.setHorizontalScrollBarEnabled(false);
        horizontalScrollView4.addView((View)this.songAdds);
        this.songPane.addView((View)horizontalScrollView4);
        HorizontalScrollView horizontalScrollView5 = new HorizontalScrollView((Context)this);
        horizontalScrollView5.setHorizontalScrollBarEnabled(false);
        this.timeline = this.row();
        horizontalScrollView5.addView((View)this.timeline);
        this.songPane.addView((View)horizontalScrollView5);
        ScrollView scrollView2 = new ScrollView((Context)this);
        this.songCards = this.col();
        scrollView2.addView((View)this.songCards);
        this.songPane.addView((View)scrollView2, (ViewGroup.LayoutParams)this.flexFill());
        frameLayout.addView((View)this.songPane);
        this.pyPane = this.col();
        this.pyPane.setVisibility(8);
        this.pyPane.addView((View)this.text("PyJav", 18, true));
        TextView textView20 = this.text("Python, or Java .java / .jar / .class. Run uses java or python3 on this device when it is installed.", 14, false);
        textView20.setTextColor(MUTED);
        textView20.setPadding(0, this.dp(8), 0, this.dp(8));
        this.pyPane.addView((View)textView20);
        this.pyEditor = new EditText((Context)this);
        this.pyEditor.setText((CharSequence)"#!/usr/bin/env python3\n\"\"\"Pulsekit drum script.\"\"\"\nprint(\"edit me\")\n");
        this.pyEditor.setTypeface(Typeface.MONOSPACE);
        this.pyEditor.setTextColor(FG);
        this.pyEditor.setTextSize(2, 12.0f);
        this.pyEditor.setBackground((Drawable)this.round(ELEV, 10));
        this.pyEditor.setPadding(this.dp(10), this.dp(10), this.dp(10), this.dp(10));
        this.pyEditor.setGravity(0x800033);
        this.pyEditor.setMinLines(8);
        this.pyPane.addView((View)this.pyEditor, (ViewGroup.LayoutParams)this.flexFill());
        frameLayout.addView((View)this.pyPane);
        this.importPane = this.col();
        this.importPane.setVisibility(8);
        this.importPane.addView((View)this.text("Import", 18, true));
        TextView textView21 = this.text("PRJ \u00b7 full project\nPKP \u00b7 plugin pack\nFSET \u00b7 patterns, Fillerns, and fills from one imported file\nMIDI \u00b7 pattern, Fillern or fill (named in the file)\nSNG \u00b7 song\nWAV / MP3 \u00b7 input file for a PyJav program such as MidiDrumGen.java\nSF2 \u00b7 drum samples onto pads\nPY \u00b7 Python script to edit", 14, false);
        textView21.setTextColor(MUTED);
        textView21.setPadding(0, this.dp(8), 0, this.dp(16));
        this.importPane.addView((View)textView21);
        this.importPane.addView((View)this.action("Choose file", FG, BG, view -> this.projectIo.openFile()));
        this.importedFileList = this.col();
        this.importedFileList.setPadding(0, this.dp(12), 0, 0);
        this.importPane.addView((View)this.importedFileList);
        TextView textView22 = this.action("Try Dub pack", ELEV, FG, view -> this.projectIo.tryDub());
        LinearLayout.LayoutParams layoutParams4 = this.wrap();
        layoutParams4.setMargins(0, this.dp(8), 0, 0);
        textView22.setLayoutParams((ViewGroup.LayoutParams)layoutParams4);
        this.importPane.addView((View)textView22);
        frameLayout.addView((View)this.importPane);
        this.exportPane = this.col();
        this.exportPane.setVisibility(8);
        this.exportPane.addView((View)this.text("Export", 18, true));
        this.exportPane.addView((View)this.hint("Project"));
        LinearLayout linearLayout17 = this.row();
        linearLayout17.addView((View)this.action("PRJ", HIT, BG, view -> this.projectIo.saveKind(15)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout17.addView((View)this.action("PKP", ELEV, FG, view -> this.projectIo.saveKind(16)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout17.addView((View)this.action("FSET", ELEV, FG, view -> this.importLibrary.saveFset(null)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout17);
        this.exportPane.addView((View)this.hint("MIDI"));
        LinearLayout linearLayout18 = this.row();
        linearLayout18.addView((View)this.action("Pattern", ELEV, FG, view -> this.projectIo.saveMidi("pattern")), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout18.addView((View)this.action("Fillern", ELEV, FG, view -> this.projectIo.saveMidi("fillern")), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout18.addView((View)this.action("Fill", ELEV, FG, view -> this.projectIo.saveMidi("fill")), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout18);
        this.exportPane.addView((View)this.hint("This beat"));
        LinearLayout linearLayout19 = this.row();
        linearLayout19.addView((View)this.action("WAV", ELEV, FG, view -> this.projectIo.saveKind(10)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout19.addView((View)this.action("MP3", ELEV, FG, view -> this.projectIo.saveKind(11)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout19);
        LinearLayout linearLayout20 = this.row();
        linearLayout20.setPadding(0, this.dp(8), 0, 0);
        linearLayout20.addView((View)this.action("SF2", ELEV, FG, view -> this.projectIo.saveKind(12)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout20.addView((View)this.action("JAR", ELEV, FG, view -> this.projectIo.saveKind(18)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout20);
        this.exportPane.addView((View)this.hint("Song"));
        LinearLayout linearLayout21 = this.row();
        linearLayout21.addView((View)this.action("SNG", ELEV, FG, view -> this.projectIo.saveKind(9)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout21.addView((View)this.action("Song MIDI", ELEV, FG, view -> this.projectIo.saveKind(17)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout21);
        this.exportPane.addView((View)this.hint("Python"));
        LinearLayout linearLayout22 = this.row();
        linearLayout22.addView((View)this.action("PY", ELEV, FG, view -> this.projectIo.saveKind(14)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout22);
        frameLayout.addView((View)this.exportPane);
        this.body.addView((View)frameLayout);
        linearLayout2.addView((View)this.body);
        LinearLayout linearLayout23 = this.col();
        linearLayout23.setBackgroundColor(SURFACE);
        linearLayout23.setPadding(this.dp(12), this.dp(8), this.dp(12), this.dp(16));
        this.lenBar = this.row();
        this.lenBar.setPadding(0, 0, 0, this.dp(8));
        this.lenBar.setGravity(16);
        TextView textView23 = this.text("LEN", 10, true);
        textView23.setTextColor(SUBTLE);
        textView23.setPadding(0, 0, this.dp(6), 0);
        this.lenBar.addView((View)textView23);
        for (int i = 0; i < Engine.LEN_STEPS.length; ++i) {
            int n9 = Engine.LEN_STEPS[i];
            TextView textView24 = this.text(Engine.LEN_LABEL[i], 11, true);
            textView24.setGravity(17);
            textView24.setTextColor(BG);
            textView24.setPadding(this.dp(4), this.dp(12), this.dp(4), this.dp(12));
            LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams5.setMargins(this.dp(3), 0, this.dp(3), 0);
            textView24.setOnClickListener(view -> {
                this.paintLen = this.paintLen == n9 ? 0 : n9;
                this.gridEditor.paintLenChips();
            });
            this.lenChips[i] = textView24;
            this.lenBar.addView((View)textView24, (ViewGroup.LayoutParams)layoutParams5);
        }
        linearLayout23.addView((View)this.lenBar);
        this.gridEditor.paintLenChips();
        LinearLayout linearLayout24 = this.row();
        linearLayout24.addView((View)this.action("\u25a0", ELEV, FG, view -> this.playback.stop()), (ViewGroup.LayoutParams)this.square());
        this.playBtn = this.action("Play", FG, BG, view -> this.playback.toggle());
        LinearLayout.LayoutParams layoutParams6 = new LinearLayout.LayoutParams(0, this.dp(48), 2.0f);
        layoutParams6.setMargins(this.dp(6), 0, this.dp(6), 0);
        this.playBtn.setLayoutParams((ViewGroup.LayoutParams)layoutParams6);
        linearLayout24.addView((View)this.playBtn);
        linearLayout24.addView((View)this.action("Gen", HIT, BG, view -> this.playback.generate()), (ViewGroup.LayoutParams)this.square());
        linearLayout23.addView((View)linearLayout24);
        linearLayout2.addView((View)linearLayout23);
        return linearLayout2;
    }

    RangeBar knob(LinearLayout linearLayout, String string, int n2, int n3, int n4, IntFn intFn) {
        LinearLayout linearLayout2 = this.col();
        LinearLayout linearLayout3 = this.row();
        TextView textView = this.text(string, 10, true);
        textView.setTextColor(SUBTLE);
        textView.setLetterSpacing(0.12f);
        TextView textView2 = this.text(string.equals("TEMPO") ? Integer.toString(n4) : (string.equals("DENSITY") ? Integer.toString(n4) : n4 + "%"), 11, true);
        textView2.setTextColor(MUTED);
        textView2.setGravity(0x800005);
        if ("SWING".equals(string)) {
            this.swingVal = textView2;
        }
        if ("DENSITY".equals(string)) {
            this.densVal = textView2;
        }
        if ("HUMAN".equals(string)) {
            this.humanVal = textView2;
        }
        linearLayout3.addView((View)textView, (ViewGroup.LayoutParams)this.flex(1));
        linearLayout3.addView((View)textView2, (ViewGroup.LayoutParams)this.wrap());
        RangeBar rangeBar = new RangeBar(n2, n3, n4, n -> {
            if ("TEMPO".equals(string)) {
                textView2.setText((CharSequence)Integer.toString(n));
            } else if ("DENSITY".equals(string)) {
                textView2.setText((CharSequence)Integer.toString(n));
            } else {
                textView2.setText((CharSequence)(n + "%"));
            }
            intFn.apply(n);
        });
        linearLayout2.addView((View)linearLayout3);
        linearLayout2.addView((View)rangeBar);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.setMargins(this.dp(4), 0, this.dp(4), 0);
        linearLayout.addView((View)linearLayout2, (ViewGroup.LayoutParams)layoutParams);
        return rangeBar;
    }

    void addTab(LinearLayout linearLayout, String string, String string2) {
        TextView textView = this.text(string2, 13, true);
        textView.setGravity(17);
        textView.setTag((Object)string);
        textView.setOnClickListener(view -> this.show(string));
        textView.setPadding(0, this.dp(8), 0, this.dp(8));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, this.dp(36), 1.0f);
        layoutParams.setMargins(this.dp(2), 0, this.dp(2), 0);
        textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        linearLayout.addView((View)textView);
        this.tabs.add(textView);
    }

    void addFileTab(LinearLayout linearLayout) {
        TextView textView = this.text("File", 13, true);
        textView.setGravity(17);
        textView.setTag((Object)"file");
        textView.setOnClickListener(this::showFileMenu);
        textView.setPadding(0, this.dp(8), 0, this.dp(8));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, this.dp(36), 1.0f);
        layoutParams.setMargins(this.dp(2), 0, this.dp(2), 0);
        textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        linearLayout.addView((View)textView);
        this.tabs.add(textView);
    }

    boolean grooveView() {
        return "pattern".equals(this.view) || "combo".equals(this.view);
    }

    void showBase(String string) {
        this.gridEditor.hideLenMenu();
        this.view = string;
        boolean bl = "song".equals(string);
        boolean bl2 = "py".equals(string);
        boolean bl3 = "pads".equals(string);
        boolean bl4 = "fills".equals(string);
        boolean bl5 = "combo".equals(string);
        boolean bl6 = "pattern".equals(string);
        boolean bl7 = "import".equals(string);
        boolean bl8 = "export".equals(string);
        boolean bl10 = bl || bl2 || bl3 || bl7 || bl8;
        this.gridScroll.setVisibility(bl10 ? 8 : 0);
        if (this.lenBar != null) {
            this.lenBar.setVisibility(bl10 ? 8 : 0);
        }
        this.padsPane.setVisibility(bl3 ? 0 : 8);
        this.songPane.setVisibility(bl ? 0 : 8);
        this.pyPane.setVisibility(bl2 ? 0 : 8);
        this.importPane.setVisibility(bl7 ? 0 : 8);
        this.exportPane.setVisibility(bl8 ? 0 : 8);
        this.chrome.setVisibility(bl2 || bl7 || bl8 || bl && "play".equals(this.songMode) ? 8 : 0);
        if (!(bl2 || bl7 || bl8)) {
            this.styleWrap.setVisibility(this.grooveView() ? 0 : 8);
        }
        this.fillWrap.setVisibility(bl4 ? 0 : 8);
        this.knobsRow.setVisibility(bl || bl2 || bl7 || bl8 ? 8 : 0);
        this.toolsRow.setVisibility(this.grooveView() ? 0 : 8);
        this.refreshTabs();
        this.styleLibrary.refreshFills();
        if (bl) {
            this.songEditor.refreshSong();
        }
        if (bl6) {
            this.fillLast = false;
            this.setNow(null);
            this.styleLibrary.refreshStyles();
        }
        if (bl5) {
            this.fillLast = true;
            String string2 = this.fillernPairs.get(this.styleLibrary.patternKeyFor(this.style));
            this.styleLibrary.applyFill(string2 != null ? string2 : this.fillId);
            if (this.playing && !this.songPlay) {
                this.barLoop = Math.max(0, this.bars - 1);
            }
            this.setNow("Last bar \u00b7 " + this.styleLibrary.fillLabel(this.fillId));
            this.styleLibrary.refreshStyles();
        }
        if (bl4) {
            if (this.playing && !this.songPlay) {
                this.fillLast = true;
                this.barLoop = Math.max(0, this.bars - 1);
            }
            this.setNow(this.styleLibrary.fillLabel(this.fillId));
        }
        if (this.grooveView() || bl4) {
            this.gridEditor.refreshGrid();
        }
        if (bl3) {
            this.setNow(this.livePads ? "Pads write the pattern" : "Tap a pad");
        }
        if (bl2) {
            this.setNow("Scripts on web");
        }
        if (bl7) {
            this.setNow("Choose a MIDI, song, WAV or SoundFont");
        }
        if (bl8) {
            this.setNow(this.projectIo.exportName("mid").replace(".mid", ""));
        }
    }

    int bpm() {
        return this.bpmBar != null ? this.bpmBar.getVal() : 124;
    }

    int swing() {
        return this.swingBar != null ? this.swingBar.getVal() : 12;
    }

    int human() {
        return this.humanBar != null ? this.humanBar.getVal() : 18;
    }

    int density() {
        return this.densBar != null ? this.densBar.getVal() : 5;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onActivityResultBase(int n, int n2, Intent intent) {
        super.onActivityResult(n, n2, intent);
        if (n2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        Uri uri = intent.getData();
        try {
            Object object;
            byte[] byArray;
            if (n == 13) {
                byte[] byArray3 = this.projectIo.readUri(uri);
                int n3 = this.padTarget;
                if (n3 < 0 || n3 >= this.voices.length) {
                    return;
                }
                short[] sArray = this.projectIo.decodeToShorts(uri, byArray3);
                if (sArray.length > 44100) {
                    short[] trimmed = new short[44100];
                    System.arraycopy(sArray, 0, trimmed, 0, trimmed.length);
                    sArray = trimmed;
                }
                synchronized (this.mixLock) {
                    Engine.DrumSet drumSet = this.playback.activeDrumSet();
                    if (drumSet.original) {
                        this.voices[n3] = sArray;
                    } else {
                        drumSet.samples[n3] = sArray;
                        drumSet.matchOrig[n3] = false;
                    }
                    this.mixPos[n3] = -1;
                }
                this.playback.refreshPadLabels();
                Toast.makeText((Context)this, (CharSequence)(Engine.TRACK_LABEL[n3] + " sample loaded"), (int)0).show();
                return;
            }
            if (n == 7) {
                String string = uri.getLastPathSegment();
                if (string == null) {
                    string = uri.toString();
                }
                this.projectIo.ingest(this.projectIo.readUri(uri), string, uri);
                return;
            }
            if (n == 9) {
                byArray = Engine.encodeSng(this.songEditor.songPartsForExport(), this.songEditor.songPartsForExport().get((int)0).name);
            } else if (n == 14) {
                byArray = (this.pyEditor != null ? this.pyEditor.getText().toString() : "").getBytes(StandardCharsets.UTF_8);
            } else if (n == 15) {
                byArray = this.projectIo.encodePrj();
            } else if (n == 16) {
                object = this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : "Pulsekit";
                String string = this.pyEditor != null ? this.pyEditor.getText().toString() : "";
                byArray = Engine.encodePkp("pulsekit." + this.style, (String)object, this.bpm(), this.style, this.cells, this.fillPat, this.pyName, string);
            } else if (n == 19) {
                Object object4 = object = this.fsetSaveSource != null ? this.fsetSaveSource : this.importLibrary.selectedFileSource();
                if (object == null) {
                    throw new IllegalArgumentException("Pick an imported file set first");
                }
                Object object5 = ((String)object).isEmpty() ? "Other" : object;
                Engine.FileSet fileSet = Engine.collectFset((String)object, (String)object5, this.learned, this.learnedFills, this.fillernPairs);
                if (fileSet == null) {
                    throw new IllegalArgumentException("That file set is empty");
                }
                byArray = Engine.encodeFset(fileSet);
            } else if (n == 18) {
                byArray = Engine.encodeKitJar(Engine.encodeMidi(this.cells, this.bpm(), this.steps, this.tsNum, this.tsDen), this.cells, this.bpm(), this.style);
            } else if (n == 17) {
                byArray = Engine.encodeSongMidi(this.songEditor.songPartsForExport());
            } else if (n == 10) {
                byArray = this.playback.encodeWav();
            } else if (n == 11) {
                byArray = AudioIo.encodeMp3(this.playback.mixPcm(), 22050);
            } else if (n == 12) {
                this.playback.fillMissingVoices();
                byArray = AudioIo.encodeSf2(this.voices, this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : "Pulsekit");
            } else if (n == 8) {
                object = Engine.cellsForMidiRole(this.cells, this.fillPat, this.midiSaveRole);
                int[][] nArray = Engine.cellsForMidiRole(this.lens, this.fillLens, this.midiSaveRole);
                byArray = Engine.encodeMidi((int[][])object, nArray, this.bpm(), Engine.usedSteps((int[][])object), this.tsNum, this.tsDen);
            } else {
                byArray = "song".equals(this.view) && !this.songEditor.activeSong().isEmpty() ? Engine.encodeSongMidi(this.songEditor.activeSong()) : Engine.encodeMidi(this.cells, this.lens, this.bpm(), this.steps, this.tsNum, this.tsDen);
            }
            object = this.getContentResolver().openOutputStream(uri);
            ((OutputStream)object).write(byArray);
            ((OutputStream)object).close();
            Toast.makeText((Context)this, (CharSequence)"Saved", (int)0).show();
        }
        catch (Exception exception) {
            Toast.makeText((Context)this, (CharSequence)exception.getMessage(), (int)1).show();
        }
    }

    protected void onDestroy() {
        this.gridEditor.hideLenMenu();
        this.playback.stop();
        this.mixRun = false;
        if (this.mixThread != null) {
            this.mixThread.interrupt();
        }
        if (this.track != null) {
            try {
                this.track.stop();
            }
            catch (Exception exception) {
                // empty catch block
            }
            try {
                this.track.release();
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.track = null;
        }
        super.onDestroy();
    }

    void setNow(String string) {
        if (this.now == null) {
            return;
        }
        boolean bl = string != null && !string.trim().isEmpty();
        this.now.setText((CharSequence)(bl ? string : " "));
        this.now.setVisibility(bl ? 0 : 8);
    }

    static interface IntFn {
        public void apply(int var1);
    }

    final class RangeBar
    extends View {
        private final int min;
        private final int max;
        private int value;
        private final IntFn on;

        RangeBar(int n, int n2, int n3, IntFn intFn) {
            super((Context)MainActivity.this);
            this.min = n;
            this.max = n2;
            this.value = n3;
            this.on = intFn;
        }

        int getVal() {
            return this.value;
        }

        void setVal(int n) {
            this.value = Engine.clamp(n, this.min, this.max);
            if (this.on != null) {
                this.on.apply(this.value);
            }
            this.invalidate();
        }

        protected void onMeasure(int n, int n2) {
            this.setMeasuredDimension(View.MeasureSpec.getSize((int)n), MainActivity.this.dp(28));
        }

        protected void onDraw(Canvas canvas) {
            float f = MainActivity.this.dp(8);
            float f2 = this.getWidth() - MainActivity.this.dp(8);
            float f3 = (float)this.getHeight() / 2.0f;
            float f4 = this.max == this.min ? 0.0f : (float)(this.value - this.min) / (float)(this.max - this.min);
            float f5 = f + f4 * (f2 - f);
            Paint paint = new Paint(1);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth((float)MainActivity.this.dp(3));
            paint.setColor(BORDER);
            canvas.drawLine(f, f3, f2, f3, paint);
            paint.setColor(HIT);
            canvas.drawLine(f, f3, f5, f3, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(FG);
            canvas.drawCircle(f5, f3, (float)MainActivity.this.dp(8), paint);
        }

        public boolean onTouchEvent(MotionEvent motionEvent) {
            float f = MainActivity.this.dp(8);
            float f2 = Math.max(f + 1.0f, (float)(this.getWidth() - MainActivity.this.dp(8)));
            float f3 = (motionEvent.getX() - f) / (f2 - f);
            int n = Math.round((float)this.min + (f3 = Math.max(0.0f, Math.min(1.0f, f3))) * (float)(this.max - this.min));
            if (n != this.value) {
                this.value = n;
                this.on.apply(n);
                this.invalidate();
            }
            return true;
        }
    }

    static interface NameFn {
        public void apply(String var1);
    }

    android.widget.LinearLayout promptsPane;

    android.widget.LinearLayout infoPane;

    android.widget.TextView infoStatus;

    java.util.LinkedHashMap fsetInfoMap;

    android.widget.TextView pkParamsBtn;

    byte[] pkPyBytes;

    String pkPromptCategory;

    String pkPromptSource;

    // Feature classes. Created after the fields above, which they read.
    final GridEditor gridEditor = new GridEditor(this);
    final Playback playback = new Playback(this);
    final SongEditor songEditor = new SongEditor(this);
    final StyleLibrary styleLibrary = new StyleLibrary(this);
    final ImportLibrary importLibrary = new ImportLibrary(this);
    final FileSets fileSets = new FileSets(this);
    final ProjectIo projectIo = new ProjectIo(this);
    final Persistence persistence = new Persistence(this);
    final PyJav pyJav = new PyJav(this);

    void show(String string) {
        this.showBase(string);
        this.afterShow();
    }

    void showFileMenu(View anchor) {
        android.widget.LinearLayout menu = this.col();
        android.graphics.drawable.GradientDrawable bg = this.round(SURFACE, 8);
        bg.setStroke(this.dp(1), BORDER);
        menu.setBackground(bg);
        menu.setPadding(this.dp(4), this.dp(4), this.dp(4), this.dp(4));
        android.widget.TextView imp = this.text("Import", 14, true);
        imp.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        imp.setTextColor("import".equals(this.view) ? FG : MUTED);
        android.widget.TextView exp = this.text("Export", 14, true);
        exp.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        exp.setTextColor("export".equals(this.view) ? FG : MUTED);
        menu.addView(imp);
        menu.addView(exp);
        menu.measure(0, 0);
        android.widget.PopupWindow pop = new android.widget.PopupWindow(menu, menu.getMeasuredWidth(), menu.getMeasuredHeight(), true);
        pop.setBackgroundDrawable(new android.graphics.drawable.GradientDrawable());
        pop.setOutsideTouchable(true);
        pop.setElevation((float) this.dp(8));
        imp.setOnClickListener(pulsekit.AnalyzeClicks.fileItem(this, pop, "import"));
        exp.setOnClickListener(pulsekit.AnalyzeClicks.fileItem(this, pop, "export"));
        pop.showAsDropDown(anchor, 0, this.dp(4), 8388613);
    }

    void refreshTabs() {
        this.pyJav.pkWirePyJav();
        java.util.Iterator it = this.tabs.iterator();
        while (it.hasNext()) {
            android.widget.TextView tab = (android.widget.TextView) it.next();
            java.lang.Object tag = tab.getTag();
            boolean on = this.view.equals(tag)
                    || ("file".equals(tag) && ("import".equals(this.view) || "export".equals(this.view) || "fsetinfo".equals(this.view)));
            tab.setBackground(this.round(on ? ELEV : 0, 8));
            tab.setTextColor(on ? FG : MUTED);
        }
    }

    View buildUi() {
        View root = this.buildUiBase();
        this.pyJav.wirePrompts();
        this.fileSets.wireInfoPane();
        this.fileSets.loadPersistedFsetInfo();
        return root;
    }

    protected void onCreate(Bundle bundle) {
        this.onCreateBase(bundle);
        ArtJava.pinWorkDir(this);
    }

    void afterShow() {
        this.afterShowCore();
        this.pyJav.pkSyncTransport();
    }

    void afterShowCore() {
        boolean info = "fsetinfo".equals(this.view);
        boolean pr = "prompts".equals(this.view);
        if (this.infoPane != null) {
            this.infoPane.setVisibility(info ? 0 : 8);
            if (info) this.infoPane.bringToFront();
        }
        if (this.promptsPane != null) {
            this.promptsPane.setVisibility(pr ? 0 : 8);
            if (pr) this.promptsPane.bringToFront();
        }
        if (info || pr) {
            this.gridScroll.setVisibility(8);
            if (this.lenBar != null) this.lenBar.setVisibility(8);
            if (this.importPane != null) this.importPane.setVisibility(8);
            if (this.exportPane != null) this.exportPane.setVisibility(8);
            if (this.padsPane != null) this.padsPane.setVisibility(8);
            if (this.songPane != null) this.songPane.setVisibility(8);
            if (this.pyPane != null) this.pyPane.setVisibility(8);
            if (this.chrome != null) this.chrome.setVisibility(8);
            if (this.knobsRow != null) this.knobsRow.setVisibility(8);
            if (this.toolsRow != null) this.toolsRow.setVisibility(8);
            if (this.styleWrap != null) this.styleWrap.setVisibility(8);
            if (this.fillWrap != null) this.fillWrap.setVisibility(8);
            if (pr) {
                this.setNow("Prompts");
            } else {
                if (this.infoStatus != null) this.setNow(this.infoStatus.getText().toString());
                else this.setNow("File set");
            }
            this.refreshTabs();
        }
    }

    public void openKitView(java.lang.String view) { this.show(view); }

    protected void onActivityResult(int req, int res, android.content.Intent data) {
        if (req == 25) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.pyJav.pkTakePickedProgram(data.getData());
            return;
        }
        if (req == 26) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.pyJav.pkTakeInputFile(data.getData());
            return;
        }
        if (req == 27) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.pyJav.takePromptRef(data.getData());
            return;
        }
        if (req == 28) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.pyJav.takePromptExport(data.getData());
            return;
        }
        this.onActivityResultBase(req, res, data);
    }
}
