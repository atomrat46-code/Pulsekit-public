package pulsekit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

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
    /** Patterns whose Fillern fill was chosen from the list. Only these are underlined. */
    final java.util.Set<String> fillernPicked = new java.util.LinkedHashSet<String>();
    /** Fillern type per pattern: Engine.FILLERN_AFTER (default), FILLERN_END or FILLERN_START. */
    final Map<String, String> fillernModes = new LinkedHashMap<String, String>();

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
    /** The bottom strip: the length bar and ■ / Play / Gen. Hidden on pages that play nothing (afterShow). */
    LinearLayout transportBar;

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
    /** False until a pattern is chosen: the startup House pattern is not shown as selected. */
    boolean styleChosen;
    /** The time signature was set by picking a style in another meter (Ballad 6/8), so a 4/4 style sets 4/4 back. */
    boolean tsFromStyle;

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

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        // The prompt library's key: the Android keystore.
        if (PromptVault.keys == null) PromptVault.keys = new AndroidVaultKey();
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
        this.styleChosen = false;
        this.styleLibrary.refreshStyles();
        this.styleLibrary.refreshFills();
        this.gridEditor.refreshGrid();
        this.gridEditor.applyGridWidth();
        this.refreshTabs();
        this.songEditor.refreshSong();

        ArtJava.pinWorkDir(this);
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
        this.gridEditor.bindNumField(this.tsNumField, 1, 16, n -> {
            if (n != this.tsNum) this.tsFromStyle = false;
            this.gridEditor.applyTimeSig(n, this.tsDen);
        });
        this.gridEditor.bindNumField(this.tsDenField, 2, 16, n -> {
            if (n != this.tsDen) this.tsFromStyle = false;
            this.gridEditor.applyTimeSig(this.tsNum, n);
        });
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
        this.styleLibrary.buildStyleStrips();
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
        this.gridEditor.buildGridPane(frameLayout);
        this.playback.buildPadsPane(frameLayout);
        this.songEditor.buildSongPane(frameLayout);
        this.pyJav.buildPyPane(frameLayout);
        this.projectIo.buildImportPane(frameLayout);
        this.projectIo.buildExportPane(frameLayout);
        this.body.addView((View)frameLayout);
        linearLayout2.addView((View)this.body);
        LinearLayout linearLayout23 = this.col();
        linearLayout23.setBackgroundColor(SURFACE);
        linearLayout23.setPadding(this.dp(12), this.dp(8), this.dp(12), this.dp(16));
        this.gridEditor.buildLenBar(linearLayout23);
        LinearLayout linearLayout24 = this.row();
        linearLayout24.addView((View)this.action("\u25a0", ELEV, FG, view -> this.playback.stop()), (ViewGroup.LayoutParams)this.square());
        this.playBtn = this.action("Play", FG, BG, view -> this.playback.toggle());
        LinearLayout.LayoutParams layoutParams6 = new LinearLayout.LayoutParams(0, this.dp(48), 2.0f);
        layoutParams6.setMargins(this.dp(6), 0, this.dp(6), 0);
        this.playBtn.setLayoutParams((ViewGroup.LayoutParams)layoutParams6);
        linearLayout24.addView((View)this.playBtn);
        linearLayout24.addView((View)this.action("Gen", HIT, BG, view -> this.playback.generate()), (ViewGroup.LayoutParams)this.square());
        linearLayout23.addView((View)linearLayout24);
        this.transportBar = linearLayout23;
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

    void show(String string) {
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
        if ((bl5 || bl6) && !string.equals(this.importedFor)) {
            // The Pattern tab lists a file set's patterns, the Fillern tab its Fillerns.
            this.importLibrary.rebuildImported();
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
        this.afterShow();
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
    protected void onActivityResult(int n, int n2, Intent intent) {
        if (n == 25) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) {
                String before = this.pyName + "\n" + this.codeSave.text();
                this.pyJav.pkTakePickedProgram(intent.getData());
                if (!before.equals(this.pyName + "\n" + this.codeSave.text())) this.codeSave.opened(intent.getData());
            }
            return;
        }
        if (n == 26) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.pyJav.pkTakeInputFile(intent.getData());
            return;
        }
        if (n == 27) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.pyJav.takePromptRef(intent.getData());
            return;
        }
        if (n == DrumMidiSettingsPage.PICK_KEY) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.drumMidiSettings.takeKey(intent.getData());
            return;
        }
        if (n == DrumMidiSettingsPage.PICK_FOLDER) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.drumMidiSettings.takeFolder(intent.getData());
            return;
        }
        if (n == CodeSave.SAVE_AS) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.codeSave.savedAs(intent.getData());
            return;
        }
        if (n == SaveText.SAVE) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) SaveText.write(this, intent.getData());
            return;
        }
        if (n == PyJavParams.PICK_FILE) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.pyJav.pkTakeParamFile(intent.getData());
            return;
        }
        if (n == MediaBrowser.PICK_DIR) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) PyJavParams.dirPicked(this, intent.getData());
            return;
        }
        if (n == DbImport.PICK_REF || n == DbImport.PICK_RESULT) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) DbImport.take(this, intent.getData(), n == DbImport.PICK_RESULT);
            return;
        }
        if (n == CompareHitsPage.PICK_WAV) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.compareHits.takeWav(intent.getData());
            return;
        }
        if (n == 28) {
            super.onActivityResult(n, n2, intent);
            if (n2 == -1 && intent != null && intent.getData() != null) this.pyJav.takePromptExport(intent.getData());
            return;
        }
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
                byte[] picked = this.projectIo.readUri(uri);
                this.projectIo.ingest(picked, string, uri);
                // Import screen: "Import to DB also" keeps the picked file in the prompt library too.
                String toDb = ImportDb.store(this.getFilesDir(), DbImport.displayName(this, uri), picked);
                if (!toDb.isEmpty()) Toast.makeText((Context)this, (CharSequence)toDb, (int)1).show();
                return;
            }
            if (n == 9) {
                List<Engine.Part> songParts = this.songEditor.songPartsForExport();
                String songSet = this.songEditor.songFileSet(songParts);
                byArray = Engine.encodeSng(songParts, songSet != null ? songSet : songParts.get(0).name);
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
            } else if (n == 20) {
                byArray = AudioIo.encodeWav(this.playback.songPcm(), 22050);
            } else if (n == 21) {
                byArray = AudioIo.encodeMp3(this.playback.songPcm(), 22050);
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
            // Export screen: a MIDI, WAV or MP3 also into the prompt library when "Export supported media files to DB also" is on.
            String kind = n == 8 || n == 17 ? "mid" : n == 10 || n == 20 ? "wav" : n == 11 || n == 21 ? "mp3" : null;
            String toDb = "";
            if (kind != null) {
                String dbName = DbImport.displayName(this, uri);
                if (!ExportDb.supported(dbName)) dbName = dbName + "." + kind;
                toDb = ExportDb.store(this.getFilesDir(), dbName, byArray);
            }
            Toast.makeText((Context)this, (CharSequence)(toDb.isEmpty() ? "Saved" : "Saved, " + toDb), (int)(toDb.isEmpty() ? 0 : 1)).show();
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

    android.widget.LinearLayout helpPane;

    android.widget.LinearLayout drumMidiPane;

    android.widget.LinearLayout compareHitsPane;

    /** The groove tab the imported chips were last built for. */
    String importedFor;

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
    final ProgramMenus programMenus = new ProgramMenus(this);
    final HelpPage helpPage = new HelpPage(this);
    final DrumMidiSettingsPage drumMidiSettings = new DrumMidiSettingsPage(this);
    final CompareHitsPage compareHits = new CompareHitsPage(this);
    final CodeSave codeSave = new CodeSave(this);


    void showFileMenu(View anchor) {
        android.widget.LinearLayout menu = this.col();
        android.graphics.drawable.GradientDrawable bg = this.round(SURFACE, 8);
        bg.setStroke(this.dp(1), BORDER);
        menu.setBackground(bg);
        menu.setPadding(this.dp(4), this.dp(4), this.dp(4), this.dp(4));
        android.widget.TextView imp = this.text("Import", 14, true);
        imp.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        imp.setTextColor("import".equals(this.view) ? HIT : FG);
        android.widget.TextView exp = this.text("Export", 14, true);
        exp.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        exp.setTextColor("export".equals(this.view) ? HIT : FG);
        // PyJav's code editor: saved from here, so its own long-press menu stays Select all, Paste...
        final boolean codeSavable = this.codeSave.canSave();
        android.widget.TextView saveCode = this.text("Save code", 14, true);
        saveCode.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        // Every item reads as available; the page shown is in the accent colour. Dimmed: nothing to save.
        saveCode.setTextColor(FG);
        saveCode.setAlpha(codeSavable ? 1f : 0.4f);
        android.widget.TextView saveCodeAs = this.text("Save code as", 14, true);
        saveCodeAs.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        saveCodeAs.setTextColor(FG);
        saveCodeAs.setAlpha(codeSavable ? 1f : 0.4f);
        // A file into the prompt library on its own, as a reference or a result file.
        android.widget.TextView importRef = this.text("Import as Ref file", 14, true);
        importRef.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        importRef.setTextColor(FG);
        importRef.setTag("file-import-ref");
        android.widget.TextView importResult = this.text("Import as Result file", 14, true);
        importResult.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        importResult.setTextColor(FG);
        importResult.setTag("file-import-result");
        android.widget.TextView midi = this.text("Drum Midi Settings", 14, true);
        midi.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        midi.setTextColor("midisettings".equals(this.view) ? HIT : FG);
        android.widget.TextView compare = this.text("Compare Hits", 14, true);
        compare.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        compare.setTextColor("comparehits".equals(this.view) ? HIT : FG);
        android.widget.TextView help = this.text("Help-Android", 14, true);
        help.setPadding(this.dp(18), this.dp(12), this.dp(28), this.dp(12));
        help.setTextColor("help".equals(this.view) ? HIT : FG);
        menu.addView(imp);
        menu.addView(exp);
        menu.addView(saveCode);
        menu.addView(saveCodeAs);
        menu.addView(importRef);
        menu.addView(importResult);
        menu.addView(midi);
        menu.addView(compare);
        menu.addView(help);
        menu.measure(0, 0);
        android.widget.PopupWindow pop = new android.widget.PopupWindow(menu, menu.getMeasuredWidth(), menu.getMeasuredHeight(), true);
        pop.setBackgroundDrawable(new android.graphics.drawable.GradientDrawable());
        pop.setOutsideTouchable(true);
        pop.setElevation((float) this.dp(8));
        imp.setOnClickListener(pulsekit.FileSetClicks.fileItem(this, pop, "import"));
        exp.setOnClickListener(pulsekit.FileSetClicks.fileItem(this, pop, "export"));
        saveCode.setOnClickListener(v -> {
            pop.dismiss();
            if (codeSavable) this.codeSave.save();
            else this.setNow("Binary programs and prompt sheets are not saved from the code editor");
        });
        saveCodeAs.setOnClickListener(v -> {
            pop.dismiss();
            if (codeSavable) this.codeSave.saveAs();
            else this.setNow("Binary programs and prompt sheets are not saved from the code editor");
        });
        importRef.setOnClickListener(v -> {
            pop.dismiss();
            DbImport.pick(this, false);
        });
        importResult.setOnClickListener(v -> {
            pop.dismiss();
            DbImport.pick(this, true);
        });
        midi.setOnClickListener(pulsekit.FileSetClicks.fileItem(this, pop, "midisettings"));
        compare.setOnClickListener(pulsekit.FileSetClicks.fileItem(this, pop, "comparehits"));
        help.setOnClickListener(pulsekit.FileSetClicks.fileItem(this, pop, "help"));
        pop.showAsDropDown(anchor, 0, this.dp(4), 8388613);
    }

    void refreshTabs() {
        this.pyJav.pkWirePyJav();
        java.util.Iterator it = this.tabs.iterator();
        while (it.hasNext()) {
            android.widget.TextView tab = (android.widget.TextView) it.next();
            java.lang.Object tag = tab.getTag();
            boolean on = this.view.equals(tag)
                    || ("file".equals(tag) && ("import".equals(this.view) || "export".equals(this.view) || "help".equals(this.view) || "midisettings".equals(this.view) || "comparehits".equals(this.view) || "fsetinfo".equals(this.view)));
            tab.setBackground(this.round(on ? ELEV : 0, 8));
            tab.setTextColor(on ? FG : MUTED);
        }
    }

    View buildUi() {
        View root = this.buildUiBase();
        this.pyJav.wirePrompts();
        this.helpPage.wire();
        this.drumMidiSettings.wire();
        this.compareHits.wire();
        this.fileSets.wireInfoPane();
        this.fileSets.loadPersistedFsetInfo();
        return root;
    }



    void afterShow() {
        boolean info = "fsetinfo".equals(this.view);
        boolean pr = "prompts".equals(this.view);
        boolean help = "help".equals(this.view);
        boolean midi = "midisettings".equals(this.view);
        boolean compare = "comparehits".equals(this.view);
        // Prompts, Import, Export, Drum Midi Settings, Help, Compare Hits, Pads and PyJav play nothing from the bar.
        boolean noTransport = pr || help || midi || compare || "import".equals(this.view) || "export".equals(this.view) || "pads".equals(this.view)
            || "py".equals(this.view);
        if (this.transportBar != null) this.transportBar.setVisibility(noTransport ? 8 : 0);
        // Without the bar there is no Stop: a beat or song that was playing stops here.
        if (noTransport && this.playing) this.playback.stop();
        if (this.helpPane != null) {
            this.helpPane.setVisibility(help ? 0 : 8);
            if (help) this.helpPane.bringToFront();
        }
        if (this.drumMidiPane != null) {
            this.drumMidiPane.setVisibility(midi ? 0 : 8);
            if (midi) this.drumMidiPane.bringToFront();
        }
        if (this.compareHitsPane != null) {
            this.compareHitsPane.setVisibility(compare ? 0 : 8);
            if (compare) {
                this.compareHitsPane.bringToFront();
                this.compareHits.refresh();
            }
        }
        if (this.infoPane != null) {
            this.infoPane.setVisibility(info ? 0 : 8);
            if (info) this.infoPane.bringToFront();
        }
        if (this.promptsPane != null) {
            this.promptsPane.setVisibility(pr ? 0 : 8);
            if (pr) this.promptsPane.bringToFront();
        }
        if (info || pr || help || midi || compare) {
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
            if (compare) {
                this.setNow("Compare Hits");
            } else if (midi) {
                this.setNow("Drum Midi Settings");
            } else if (help) {
                this.setNow("Help");
            } else if (pr) {
                this.setNow("Prompts");
            } else {
                if (this.infoStatus != null) this.setNow(this.infoStatus.getText().toString());
                else this.setNow("File set");
            }
            this.refreshTabs();
        }
        this.pyJav.pkSyncTransport();
    }

    public void openKitView(java.lang.String view) { this.show(view); }


}
