/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.Activity
 *  android.app.AlertDialog$Builder
 *  android.content.Context
 *  android.content.DialogInterface
 *  android.content.Intent
 *  android.graphics.Canvas
 *  android.graphics.Color
 *  android.graphics.Paint
 *  android.graphics.Paint$Cap
 *  android.graphics.Paint$Style
 *  android.graphics.Typeface
 *  android.graphics.drawable.Drawable
 *  android.graphics.drawable.GradientDrawable
 *  android.media.AudioTrack
 *  android.media.MediaCodec
 *  android.media.MediaCodec$BufferInfo
 *  android.media.MediaExtractor
 *  android.media.MediaFormat
 *  android.net.Uri
 *  android.os.Build$VERSION
 *  android.os.Bundle
 *  android.os.Handler
 *  android.os.Looper
 *  android.os.ParcelFileDescriptor
 *  android.os.Process
 *  android.os.SystemClock
 *  android.text.Html
 *  android.view.MotionEvent
 *  android.view.View
 *  android.view.View$MeasureSpec
 *  android.view.View$OnClickListener
 *  android.view.View$OnTouchListener
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.view.ViewParent
 *  android.widget.EditText
 *  android.widget.FrameLayout
 *  android.widget.HorizontalScrollView
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.PopupWindow
 *  android.widget.ScrollView
 *  android.widget.TextView
 *  android.widget.Toast
 *  org.json.JSONObject
 */
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

public class MainActivity
extends Activity {
    private static final int BG = Color.parseColor((String)"#0A0B0C");
    private static final int SURFACE = Color.parseColor((String)"#131416");
    private static final int ELEV = Color.parseColor((String)"#1B1D1F");
    private static final int FG = Color.parseColor((String)"#ECEBE6");
    private static final int MUTED = Color.parseColor((String)"#8A8B86");
    private static final int SUBTLE = Color.parseColor((String)"#5C5D59");
    private static final int HIT = Color.parseColor((String)"#9AAB9C");
    private static final int BORDER = Color.parseColor((String)"#2A2B2C");
    private static final int VEL_HI = Color.parseColor((String)"#6F7D71");
    private static final int VEL_LO = Color.parseColor((String)"#3A3C3A");
    private static final int ACCENT = Color.parseColor((String)"#D9A441");
    private static final int ACC_CELL = Color.parseColor((String)"#3A3220");
    private static final int OPEN = 7;
    private static final int SAVE_MIDI = 8;
    private static final int SAVE_SNG = 9;
    private static final int SAVE_WAV = 10;
    private static final int SAVE_MP3 = 11;
    private static final int SAVE_SF2 = 12;
    private static final int OPEN_PAD = 13;
    private static final int OPEN_ISO_SAMPLE = 21;
    private static final int OPEN_ANALYZE = 22;
    private static final int SAVE_PY = 14;
    private static final int SAVE_PRJ = 15;
    private static final int SAVE_PKP = 16;
    private static final int SAVE_SONG_MIDI = 17;
    private static final int SAVE_JAR = 18;
    private static final int SAVE_FSET = 19;
    private String midiSaveRole = "pattern";
    private String fsetSaveSource = null;
    private final Map<String, Engine.Style> styles = Engine.styles();
    private final int[][] cells = Engine.emptyCells();
    private final int[][] fillPat = Engine.fillCells("toms");
    private final int[][] lens = Engine.emptyCells();
    private final int[][] fillLens = Engine.emptyCells();
    private final boolean[] accents = new boolean[32];
    private final boolean[] mutes = new boolean[Engine.TRACK_ID.length];
    private final TextView[][] grid = new TextView[Engine.TRACK_ID.length][32];
    private TextView accLab;
    private final TextView[] accCells = new TextView[32];
    private final TextView[] padBtns = new TextView[Engine.TRACK_ID.length];
    private final List<Engine.Part> song = new ArrayList<Engine.Part>();
    private final List<Engine.ImportedSong> importedSongs = new ArrayList<Engine.ImportedSong>();
    private String songLane = "original";
    private String importedSongId;
    private final List<Engine.Learned> learned = new ArrayList<Engine.Learned>();
    private final List<Engine.LearnedFill> learnedFills = new ArrayList<Engine.LearnedFill>();
    private final List<Engine.LearnedFill> variatedFills = new ArrayList<Engine.LearnedFill>();
    private final List<Engine.Learned> variatedPatterns = new ArrayList<Engine.Learned>();
    private final Map<String, String> fillernPairs = new LinkedHashMap<String, String>();
    private final List<String> hiddenStyles = new ArrayList<String>();
    private final List<String> hiddenFills = new ArrayList<String>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int paintLen;
    private LinearLayout lenBar;
    private final TextView[] lenChips = new TextView[Engine.LEN_STEPS.length];
    private long cellTapAt;
    private int cellTapT = -1;
    private int cellTapS = -1;
    private float cellTapX;
    private float cellTapY;
    private View cellTapView;
    private Runnable cellTapClick;
    private Runnable cellTapUnlock;
    private final short[][] voices = new short[Engine.NOTES.length][];
    private final List<Engine.DrumSet> drumSets = new ArrayList<Engine.DrumSet>();
    private int drumSetIndex = 0;
    private FlowLayout drumSetBar;
    private TextView padMatchAll;
    private short[] oneShot;
    private int oneShotPos = -1;
    private float oneShotGain;
    private final List<TextView> tabs = new ArrayList<TextView>();
    private TextView now;
    private TextView swingVal;
    private TextView densVal;
    private TextView humanVal;
    private FlowLayout styleBar;
    private LinearLayout importedHost;
    private LinearLayout importedFillHost;
    private LinearLayout importedFileList;
    private final Map<String, Boolean> openPacks = new LinkedHashMap<String, Boolean>();
    private FlowLayout variatedPatternBar;
    private LinearLayout styleWrap;
    private LinearLayout fillWrap;
    private FlowLayout variatedFillBar;
    private FlowLayout fillBar;
    private LinearLayout chrome;
    private LinearLayout body;
    private LinearLayout gridPane;
    private LinearLayout songCards;
    private LinearLayout knobsRow;
    private LinearLayout toolsRow;
    private HorizontalScrollView toolsScroll;
    private ScrollView gridScroll;
    private LinearLayout padsPane;
    private LinearLayout songPane;
    private LinearLayout pyPane;
    private EditText pyEditor;
    private String pyName = "drum_midi.py";
    private LinearLayout importPane;
    private LinearLayout exportPane;
    private LinearLayout isolatePane;
    private LinearLayout isolateRows;
    private TextView isolateStatus;
    private TextView isolateUse;
    private TextView isolateSkip;
    private AudioIo.Analysis isolateAnalysis;
    private String isolateFile;
    private String isolateSampleTrack;
    private LinearLayout analyzePane;
    private LinearLayout analyzeRows;
    private TextView analyzeStatus;
    private TextView analyzeFileLab;
    private byte[] analyzeBytes;
    private String analyzeName;
    private Uri analyzeUri;
    private LinearLayout songAdds;
    private LinearLayout timeline;
    private TextView playBtn;
    private TextView muteBtn;
    private TextView stepsBtn;
    private EditText bpmLabel;
    private EditText tsNumField;
    private EditText tsDenField;
    private RangeBar bpmBar;
    private RangeBar swingBar;
    private RangeBar densBar;
    private RangeBar humanBar;
    private String style = "house";
    private String variatedPatternId;
    private String view = "pattern";
    private String fillId = "toms";
    private String songMode = "edit";
    private volatile boolean playing;
    private volatile boolean songPlay;
    private boolean muted;
    private boolean livePads;
    private boolean pluginGhostHats;
    private boolean fillLast;
    private int playhead = -1;
    private int step;
    private int songIndex;
    private int songLoop;
    private int barLoop;
    private int bars = 4;
    private int steps = 16;
    private int tsNum = 4;
    private int tsDen = 4;
    private int padTarget = -1;
    private AudioTrack track;
    private boolean fillVariated;
    private static final int MIX_CHUNK = 256;
    private final Object mixLock = new Object();
    private final int[] mixPos = new int[Engine.NOTES.length];
    private final int[] mixGain = new int[Engine.NOTES.length];
    private Runnable pendingDouble;
    private PopupWindow lenPop;
    private boolean lenPopReady;
    private Thread mixThread;
    private volatile boolean mixRun;
    private volatile boolean mixNeedHit;
    private volatile boolean mixReset;
    private int mixAcc;
    private int shownHead = -1;
    private final Random mixRng = new Random();

    private void onCreateBase(Bundle bundle) {
        super.onCreate(bundle);
        if (Build.VERSION.SDK_INT >= 21) {
            this.getWindow().setStatusBarColor(BG);
            this.getWindow().setNavigationBarColor(SURFACE);
        }
        Arrays.fill(this.mixPos, -1);
        this.drumSets.add(Engine.DrumSet.originalSet());
        Engine.defaultAccents(this.accents, this.steps, Engine.stepsPerBeat(this.tsDen));
        this.loadStyle("house", false);
        this.setContentView(this.buildUi());
        this.restoreSession();
        this.refreshStyles();
        this.refreshFills();
        this.refreshGrid();
        this.applyGridWidth();
        this.refreshTabs();
        this.refreshSong();
    }

    private View buildUiBase() {
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
        this.bpmLabel = this.numField("124", 18, 48);
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
        this.tsNumField = this.numField("4", 14, 24);
        this.tsDenField = this.numField("4", 14, 24);
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
        this.bindNumField(this.bpmLabel, 40, 240, n -> {
            if (this.bpmBar != null) {
                this.bpmBar.setVal(n);
            }
        });
        this.bindNumField(this.tsNumField, 1, 16, n -> this.applyTimeSig(n, this.tsDen));
        this.bindNumField(this.tsDenField, 2, 16, n -> this.applyTimeSig(this.tsNum, n));
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
            TextView textView10 = this.pill(style2.label, false, view -> this.loadStyle(style2.id, false));
            textView10.setTag((Object)style2.id);
            this.attachBuiltinStyleMenu(textView10, style2.id);
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
            textView4 = this.pill(Engine.FILL_LABEL[i], false, view -> this.applyFill(string));
            textView4.setTag((Object)string);
            this.fillBar.addView((View)textView4);
        }
        this.fillBar.addView((View)this.pill("Variate", false, view -> this.variateFill()));
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
        this.stepsBtn = this.outline("16 steps", false, view -> this.toggleSteps());
        this.toolsRow.addView((View)this.stepsBtn);
        textView4 = this.outline("4 bars out", false, view -> {
            this.bars = this.bars == 4 ? 8 : (this.bars == 8 ? 2 : 4);
            ((TextView)view).setText((CharSequence)(this.bars + " bars out"));
        });
        this.toolsRow.addView((View)textView4);
        TextView textView13 = this.outline("Variate", false, view -> this.variatePattern("random"));
        textView13.setOnLongClickListener(view -> {
            new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Variate pattern").setItems(new CharSequence[]{"Random groove", "With this fill", "With random fill"}, (dialogInterface, n) -> {
                if (n == 0) {
                    this.variatePattern("random");
                } else if (n == 1) {
                    this.variatePattern("fill");
                } else {
                    this.variatePattern("random-fill");
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
            this.syncBuiltinFill();
            this.refreshGrid();
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
        this.bindAccCell(this.accLab);
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
            this.refreshGrid();
        });
        linearLayout11.addView((View)this.accLab);
        for (n2 = 0; n2 < 32; ++n2) {
            int n4 = n2;
            TextView textView14 = this.cell(n2 % 4 == 0 ? Integer.toString(n2 / 4 + 1) : "\u00b7", view -> {
                this.accents[n4] = !this.accents[n4];
                view.performHapticFeedback(3);
                this.refreshGrid();
            });
            textView14.setTag((Object)("acc-" + n2));
            this.bindAccCell(textView14);
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
                    this.refreshGrid();
                    return;
                }
                this.bang(n5, 110);
            });
            textView3.setOnLongClickListener(view -> {
                this.mutes[n5] = !this.mutes[n5];
                this.refreshGrid();
                return true;
            });
            linearLayout12.addView((View)textView3);
            for (int i = 0; i < 32; ++i) {
                int n6 = i;
                textView2 = this.cell("", view -> {
                    if (this.paintLen > 0) {
                        this.setCellLen(n5, n6, this.paintLen);
                        return;
                    }
                    int[][] nArray = this.editCells();
                    int[][] nArray2 = this.editLens();
                    int v = nArray[n5][n6];
                    nArray[n5][n6] = v <= 0 ? 100 : (v < 90 ? 0 : (v < 120 ? 127 : 64));
                    if (nArray[n5][n6] <= 0) {
                        nArray2[n5][n6] = 0;
                    }
                    this.refreshGrid();
                    if (!"fills".equals(this.view)) {
                        this.syncBuiltinFill();
                    }
                });
                this.bindCellLength(textView2, n5, n6);
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
            this.syncBuiltinFill();
            this.refreshGrid();
        }));
        this.padMatchAll = this.outline("Match Original", true, view -> this.matchWholeOriginal());
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
                    this.bang(n8, 110);
                    this.flashPad(n8);
                    if (this.livePads) {
                        int step = this.playhead >= 0 ? this.playhead : 0;
                        this.cells[n8][step] = this.cells[n8][step] > 0 ? 0 : 100;
                        if (this.cells[n8][step] <= 0) {
                            this.lens[n8][step] = 0;
                        }
                        this.syncBuiltinFill();
                        this.refreshGrid();
                    }
                });
                textView.setOnLongClickListener(view -> {
                    this.padTarget = n8;
                    this.showPadMenu(n8);
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
        this.refreshDrumSets();
        this.songPane = this.col();
        this.songPane.setVisibility(8);
        LinearLayout linearLayout14 = this.row();
        TextView editPill = this.pill("Edit", true, view -> {
            this.songMode = "edit";
            this.chrome.setVisibility(0);
            this.styleWrap.setVisibility(8);
            this.fillWrap.setVisibility(8);
            this.knobsRow.setVisibility(8);
            this.refreshSong();
        });
        editPill.setTag((Object)"edit");
        TextView textView16 = this.pill("Play", false, view -> {
            this.songMode = "play";
            this.chrome.setVisibility(8);
            this.refreshSong();
        });
        textView16.setTag((Object)"play");
        linearLayout14.addView((View)editPill);
        linearLayout14.addView((View)textView16);
        this.songPane.addView((View)linearLayout14);
        LinearLayout linearLayout15 = this.row();
        TextView textView17 = this.pill("Original", true, view -> {
            this.songLane = "original";
            this.refreshSong();
        });
        textView17.setTag((Object)"original");
        textView = this.pill("Imported", false, view -> {
            this.songLane = "imported";
            if (this.importedSongId == null && !this.importedSongs.isEmpty()) {
                this.importedSongId = this.importedSongs.get((int)0).id;
            }
            this.refreshSong();
        });
        textView.setTag((Object)"imported");
        linearLayout15.addView((View)textView17);
        linearLayout15.addView((View)textView);
        this.songPane.addView((View)linearLayout15);
        this.songAdds = this.row();
        TextView patternPill = this.pill("Pattern \u00d74", false, view -> {
            Engine.Part part = Engine.groove(this.styles.get((Object)this.style).label, this.bpm(), this.cells, 4);
            part.lens = Engine.copyCells(this.lens);
            this.add(part);
        });
        this.attachSongPick(patternPill, -1, "pattern");
        TextView textView18 = this.pill("Fillern", false, view -> this.addCurrentFillern());
        this.attachSongPick(textView18, -1, "fillern");
        TextView textView19 = this.pill("Fill", false, view -> {
            Engine.Part part = Engine.fill(this.fillLabel(this.fillId), this.bpm(), this.fillPat, 1);
            part.lens = Engine.copyCells(this.fillLens);
            this.add(part);
        });
        this.attachSongPick(textView19, -1, "fill");
        this.songAdds.addView((View)patternPill);
        this.songAdds.addView((View)textView18);
        this.songAdds.addView((View)textView19);
        this.songAdds.addView((View)this.pill("Silent", false, view -> this.add(Engine.rest(this.bpm(), 2))));
        this.songAdds.addView((View)this.pill("Clear", false, view -> {
            if ("imported".equals(this.songLane) && this.importedSongId != null) {
                String string = this.importedSongId;
                this.importedSongs.removeIf(importedSong -> string.equals(importedSong.id));
                this.importedSongId = this.importedSongs.isEmpty() ? null : this.importedSongs.get((int)0).id;
                this.persistLearned();
            } else {
                this.song.clear();
            }
            this.refreshSong();
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
        TextView textView21 = this.text("PRJ \u00b7 full project\nPKP \u00b7 plugin pack\nFSET \u00b7 patterns, Fillerns, and fills from one imported file\nMIDI \u00b7 pattern, Fillern or fill (named in the file)\nSNG \u00b7 song\nWAV / MP3 \u00b7 isolate kick, snare, toms, hats, ride, crash\nSF2 \u00b7 drum samples onto pads\nPY \u00b7 Python script to edit", 14, false);
        textView21.setTextColor(MUTED);
        textView21.setPadding(0, this.dp(8), 0, this.dp(16));
        this.importPane.addView((View)textView21);
        this.importPane.addView((View)this.action("Choose file", FG, BG, view -> this.openFile()));
        this.importedFileList = this.col();
        this.importedFileList.setPadding(0, this.dp(12), 0, 0);
        this.importPane.addView((View)this.importedFileList);
        TextView textView22 = this.action("Try Dub pack", ELEV, FG, view -> this.tryDub());
        LinearLayout.LayoutParams layoutParams4 = this.wrap();
        layoutParams4.setMargins(0, this.dp(8), 0, 0);
        textView22.setLayoutParams((ViewGroup.LayoutParams)layoutParams4);
        this.importPane.addView((View)textView22);
        frameLayout.addView((View)this.importPane);
        this.isolatePane = this.col();
        this.isolatePane.setVisibility(8);
        this.isolatePane.addView((View)this.text("Isolation", 18, true));
        this.isolateStatus = this.text("Import a WAV or MP3 to isolate pads.", 14, false);
        this.isolateStatus.setTextColor(MUTED);
        this.isolateStatus.setPadding(0, this.dp(8), 0, this.dp(12));
        this.isolatePane.addView((View)this.isolateStatus);
        ScrollView scrollView3 = new ScrollView((Context)this);
        this.isolateRows = this.col();
        scrollView3.addView((View)this.isolateRows);
        this.isolatePane.addView((View)scrollView3, (ViewGroup.LayoutParams)this.flexFill());
        LinearLayout linearLayout16 = this.row();
        linearLayout16.setPadding(0, this.dp(8), 0, 0);
        this.isolateUse = this.action("Use pads", FG, BG, view -> this.finishIsolation(ISO_PADS_USE));
        this.isolateSkip = this.action("Skip", ELEV, FG, view -> this.finishIsolation(ISO_PADS_SKIP));
        linearLayout16.addView((View)this.isolateUse, (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout16.addView((View)this.isolateSkip, (ViewGroup.LayoutParams)this.flexBtn());
        this.isolatePane.addView((View)linearLayout16);
        frameLayout.addView((View)this.isolatePane);
        this.exportPane = this.col();
        this.exportPane.setVisibility(8);
        this.exportPane.addView((View)this.text("Export", 18, true));
        this.exportPane.addView((View)this.hint("Project"));
        LinearLayout linearLayout17 = this.row();
        linearLayout17.addView((View)this.action("PRJ", HIT, BG, view -> this.saveKind(15)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout17.addView((View)this.action("PKP", ELEV, FG, view -> this.saveKind(16)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout17.addView((View)this.action("FSET", ELEV, FG, view -> this.saveFset(null)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout17);
        this.exportPane.addView((View)this.hint("MIDI"));
        LinearLayout linearLayout18 = this.row();
        linearLayout18.addView((View)this.action("Pattern", ELEV, FG, view -> this.saveMidi("pattern")), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout18.addView((View)this.action("Fillern", ELEV, FG, view -> this.saveMidi("fillern")), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout18.addView((View)this.action("Fill", ELEV, FG, view -> this.saveMidi("fill")), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout18);
        this.exportPane.addView((View)this.hint("This beat"));
        LinearLayout linearLayout19 = this.row();
        linearLayout19.addView((View)this.action("WAV", ELEV, FG, view -> this.saveKind(10)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout19.addView((View)this.action("MP3", ELEV, FG, view -> this.saveKind(11)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout19);
        LinearLayout linearLayout20 = this.row();
        linearLayout20.setPadding(0, this.dp(8), 0, 0);
        linearLayout20.addView((View)this.action("SF2", ELEV, FG, view -> this.saveKind(12)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout20.addView((View)this.action("JAR", ELEV, FG, view -> this.saveKind(18)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout20);
        this.exportPane.addView((View)this.hint("Song"));
        LinearLayout linearLayout21 = this.row();
        linearLayout21.addView((View)this.action("SNG", ELEV, FG, view -> this.saveKind(9)), (ViewGroup.LayoutParams)this.flexBtn());
        linearLayout21.addView((View)this.action("Song MIDI", ELEV, FG, view -> this.saveKind(17)), (ViewGroup.LayoutParams)this.flexBtn());
        this.exportPane.addView((View)linearLayout21);
        this.exportPane.addView((View)this.hint("Python"));
        LinearLayout linearLayout22 = this.row();
        linearLayout22.addView((View)this.action("PY", ELEV, FG, view -> this.saveKind(14)), (ViewGroup.LayoutParams)this.flexBtn());
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
                this.paintLenChips();
            });
            this.lenChips[i] = textView24;
            this.lenBar.addView((View)textView24, (ViewGroup.LayoutParams)layoutParams5);
        }
        linearLayout23.addView((View)this.lenBar);
        this.paintLenChips();
        LinearLayout linearLayout24 = this.row();
        linearLayout24.addView((View)this.action("\u25a0", ELEV, FG, view -> this.stop()), (ViewGroup.LayoutParams)this.square());
        this.playBtn = this.action("Play", FG, BG, view -> this.toggle());
        LinearLayout.LayoutParams layoutParams6 = new LinearLayout.LayoutParams(0, this.dp(48), 2.0f);
        layoutParams6.setMargins(this.dp(6), 0, this.dp(6), 0);
        this.playBtn.setLayoutParams((ViewGroup.LayoutParams)layoutParams6);
        linearLayout24.addView((View)this.playBtn);
        linearLayout24.addView((View)this.action("Gen", HIT, BG, view -> this.generate()), (ViewGroup.LayoutParams)this.square());
        linearLayout23.addView((View)linearLayout24);
        linearLayout2.addView((View)linearLayout23);
        return linearLayout2;
    }

    private RangeBar knob(LinearLayout linearLayout, String string, int n2, int n3, int n4, IntFn intFn) {
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

    private void addTab(LinearLayout linearLayout, String string, String string2) {
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

    private void addFileTab(LinearLayout linearLayout) {
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

    private boolean grooveView() {
        return "pattern".equals(this.view) || "combo".equals(this.view);
    }

    private void showBase(String string) {
        this.hideLenMenu();
        this.view = string;
        boolean bl = "song".equals(string);
        boolean bl2 = "py".equals(string);
        boolean bl3 = "pads".equals(string);
        boolean bl4 = "fills".equals(string);
        boolean bl5 = "combo".equals(string);
        boolean bl6 = "pattern".equals(string);
        boolean bl7 = "import".equals(string);
        boolean bl8 = "export".equals(string);
        boolean bl9 = "isolate".equals(string);
        boolean bl11 = "analyze".equals(string);
        boolean bl10 = bl || bl2 || bl3 || bl7 || bl8 || bl9 || bl11;
        this.gridScroll.setVisibility(bl10 ? 8 : 0);
        if (this.lenBar != null) {
            this.lenBar.setVisibility(bl10 ? 8 : 0);
        }
        this.padsPane.setVisibility(bl3 ? 0 : 8);
        this.songPane.setVisibility(bl ? 0 : 8);
        this.pyPane.setVisibility(bl2 ? 0 : 8);
        this.importPane.setVisibility(bl7 ? 0 : 8);
        this.exportPane.setVisibility(bl8 ? 0 : 8);
        this.isolatePane.setVisibility(bl9 ? 0 : 8);
        if (this.analyzePane != null) {
            this.analyzePane.setVisibility(bl11 ? 0 : 8);
        }
        this.chrome.setVisibility(bl2 || bl7 || bl8 || bl9 || bl11 || bl && "play".equals(this.songMode) ? 8 : 0);
        if (!(bl2 || bl7 || bl8 || bl9 || bl11)) {
            this.styleWrap.setVisibility(this.grooveView() ? 0 : 8);
        }
        this.fillWrap.setVisibility(bl4 ? 0 : 8);
        this.knobsRow.setVisibility(bl || bl2 || bl7 || bl8 || bl9 || bl11 ? 8 : 0);
        this.toolsRow.setVisibility(this.grooveView() ? 0 : 8);
        this.refreshTabs();
        this.refreshFills();
        if (bl) {
            this.refreshSong();
        }
        if (bl6) {
            this.fillLast = false;
            this.setNow(null);
            this.refreshStyles();
        }
        if (bl5) {
            this.fillLast = true;
            String string2 = this.fillernPairs.get(this.patternKeyFor(this.style));
            this.applyFill(string2 != null ? string2 : this.fillId);
            if (this.playing && !this.songPlay) {
                this.barLoop = Math.max(0, this.bars - 1);
            }
            this.setNow("Last bar \u00b7 " + this.fillLabel(this.fillId));
            this.refreshStyles();
        }
        if (bl4) {
            if (this.playing && !this.songPlay) {
                this.fillLast = true;
                this.barLoop = Math.max(0, this.bars - 1);
            }
            this.setNow(this.fillLabel(this.fillId));
        }
        if (this.grooveView() || bl4) {
            this.refreshGrid();
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
        if (bl9) {
            this.refreshIsolate();
            this.setNow(this.isolateStatus != null ? this.isolateStatus.getText().toString() : "Isolation");
        }
        if (bl11) {
            this.setNow(this.analyzeStatus != null ? this.analyzeStatus.getText().toString() : "Analyze");
        }
        if (bl8) {
            this.setNow(this.exportName("mid").replace(".mid", ""));
        }
    }

    private void applyFill(String string) {
        this.fillId = string;
        int[][] nArray = this.fillCellsFor(string);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, this.fillPat[i], 0, 16);
        }
        Engine.zeroCells(this.fillLens);
        boolean bl = this.fillVariated = string != null && string.startsWith("v:");
        if (this.playing && !this.songPlay) {
            this.fillLast = true;
            this.barLoop = Math.max(0, this.bars - 1);
        }
        this.refreshFills();
        this.setNow(this.fillLabel(string));
    }

    private boolean customFill() {
        return this.fillId != null && (this.fillId.startsWith("l:") || this.fillId.startsWith("v:") || this.fillId.startsWith("p:"));
    }

    private void syncBuiltinFill() {
        if (this.customFill()) {
            return;
        }
        int[][] nArray = Engine.buildFill(this.fillId, this.cells, this.style);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, this.fillPat[i], 0, 16);
        }
    }

    private int[][] fillCellsFor(String string) {
        String string2;
        if (string != null && string.startsWith("l:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : this.learnedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return Engine.copyCells(learnedFill.cells);
            }
        }
        if (string != null && string.startsWith("v:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : this.variatedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return Engine.copyCells(learnedFill.cells);
            }
        }
        return Engine.buildFill(string, this.cells, this.style);
    }

    private String fillLabel(String string) {
        String string2;
        if (string != null && string.startsWith("l:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : this.learnedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return learnedFill.name;
            }
        }
        if (string != null && string.startsWith("v:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : this.variatedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return learnedFill.name;
            }
        }
        return Engine.fillLabel(string);
    }

    private void variateFill() {
        Random random = new Random();
        for (int i = 8; i < Engine.TRACK_ID.length; ++i) {
            for (int j = 8; j < 16; ++j) {
                if (!(random.nextDouble() < 0.22)) continue;
                this.fillPat[i][j] = this.fillPat[i][j] > 0 ? 0 : (random.nextBoolean() ? 100 : 127);
            }
        }
        Engine.LearnedFill learnedFill = new Engine.LearnedFill();
        learnedFill.id = Engine.newLearnedId();
        learnedFill.kind = this.fillId.startsWith("l:") || this.fillId.startsWith("v:") ? "toms" : this.fillId;
        learnedFill.name = Engine.uniqueFillName(Engine.fillLabel(learnedFill.kind) + " var", this.variatedFills);
        learnedFill.cells = Engine.copyCells(this.fillPat);
        this.variatedFills.add(0, learnedFill);
        while (this.variatedFills.size() > 8) {
            this.variatedFills.remove(this.variatedFills.size() - 1);
        }
        this.addVariatedFillChip(learnedFill);
        this.persistLearned();
        this.fillId = "v:" + learnedFill.id;
        this.fillVariated = true;
        this.refreshFills();
        this.setNow(learnedFill.name);
    }

    private void refreshFills() {
        this.paintFillBar(this.fillBar);
        this.paintFillBar(this.variatedFillBar);
        if (this.importedFillHost != null) {
            this.paintFillGroup((ViewGroup)this.importedFillHost);
        }
    }

    private void paintFillGroup(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); ++i) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof TextView && view.getTag() != null && !"imported-empty".equals(view.getTag()) && !"pack".equals(view.getTag())) {
                this.paintChip((TextView)view, this.fillId.equals(view.getTag()));
                continue;
            }
            if (!(view instanceof ViewGroup)) continue;
            this.paintFillGroup((ViewGroup)view);
        }
    }

    private void paintFillBar(FlowLayout flowLayout) {
        if (flowLayout == null) {
            return;
        }
        for (int i = 0; i < flowLayout.getChildCount(); ++i) {
            View view = flowLayout.getChildAt(i);
            if (!(view instanceof TextView) || view.getTag() == null || "imported-empty".equals(view.getTag())) continue;
            this.paintChip((TextView)view, this.fillId.equals(view.getTag()));
        }
    }

    private List<Engine.Part> activeSong() {
        if ("imported".equals(this.songLane)) {
            Engine.ImportedSong importedSong = this.importedSong();
            if (importedSong != null) {
                return importedSong.parts;
            }
            return new ArrayList<Engine.Part>();
        }
        return this.song;
    }

    private Engine.ImportedSong importedSong() {
        if (this.importedSongId != null) {
            for (Engine.ImportedSong importedSong : this.importedSongs) {
                if (!this.importedSongId.equals(importedSong.id)) continue;
                return importedSong;
            }
        }
        return this.importedSongs.isEmpty() ? null : this.importedSongs.get(0);
    }

    private void addImportedArrangement(String string, List<Engine.Part> list) {
        Engine.ImportedSong importedSong = new Engine.ImportedSong();
        importedSong.id = Engine.newLearnedId();
        importedSong.name = Engine.uniqueImportedName(string, this.importedSongs);
        importedSong.parts.addAll(list);
        this.importedSongs.add(0, importedSong);
        while (this.importedSongs.size() > 8) {
            this.importedSongs.remove(this.importedSongs.size() - 1);
        }
        this.importedSongId = importedSong.id;
        this.songLane = "imported";
        this.persistLearned();
        this.show("song");
        if (!importedSong.parts.isEmpty()) {
            this.applyPart(importedSong.parts.get(0));
        }
        this.refreshSong();
        this.setNow("Imported song \u00b7 " + importedSong.name);
    }

    private void attachSongPick(TextView textView, int n) {
        this.attachSongPick(textView, n, null);
    }

    private void attachSongPick(TextView textView, int n, String string) {
        textView.setOnLongClickListener(view -> {
            if (!"original".equals(this.songLane)) {
                return true;
            }
            this.showSongPick(n, string);
            return true;
        });
    }

    private void addCurrentFillern() {
        Engine.Part part = Engine.groove(this.styles.get((Object)this.style).label, this.bpm(), this.cells, 4);
        part.lens = Engine.copyCells(this.lens);
        Engine.Part part2 = Engine.fill(this.fillLabel(this.fillId), this.bpm(), this.fillPat, 1);
        part2.lens = Engine.copyCells(this.fillLens);
        this.add(part);
        this.add(part2);
    }

    private void showSongPick(int n) {
        this.showSongPick(n, null);
    }

    private void showSongPick(int n2, String string) {
        if (!"original".equals(this.songLane)) {
            return;
        }
        boolean bl = string == null || string.isEmpty();
        ArrayList<String> arrayList = new ArrayList<String>();
        ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
        if (bl || "pattern".equals(string)) {
            if (bl) {
                arrayList.add("— Pattern —");
                arrayList2.add(null);
            } else {
                arrayList.add("— Built-in —");
                arrayList2.add(null);
            }
            boolean bl2 = bl;
            boolean bl3 = bl;
            for (String string2 : Engine.styles().keySet()) {
                Engine.Style style = this.styles.get(string2);
                if (style == null) continue;
                String key = "s:" + string2;
                arrayList.add(style.label);
                arrayList2.add(() -> this.applySongPick("pattern", key, n2));
            }
            for (Engine.Learned learned : this.variatedPatterns) {
                if (!bl2 && !bl) {
                    arrayList.add("— Variated —");
                    arrayList2.add(null);
                    bl2 = true;
                }
                arrayList.add(learned.name);
                arrayList2.add(() -> this.applySongPick("pattern", "v:" + learned.id, n2));
            }
            if (!this.learned.isEmpty()) {
                if (!bl3 && !bl) {
                    arrayList.add("— Imported —");
                    arrayList2.add(null);
                    bl3 = true;
                }
                LinkedHashMap<String, ArrayList<Engine.Learned>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.Learned>>();
                for (Engine.Learned learned : this.learned) {
                    String src = Engine.sourceOf(learned);
                    if (src.isEmpty()) {
                        src = "Other";
                    }
                    ArrayList<Engine.Learned> group = linkedHashMap.get(src);
                    if (group == null) {
                        group = new ArrayList<Engine.Learned>();
                        linkedHashMap.put(src, group);
                    }
                    group.add(learned);
                }
                for (Map.Entry<String, ArrayList<Engine.Learned>> entry : linkedHashMap.entrySet()) {
                    arrayList.add("— " + entry.getKey() + " —");
                    arrayList2.add(null);
                    for (Engine.Learned learned : entry.getValue()) {
                        arrayList.add("  " + learned.name);
                        arrayList2.add(() -> this.applySongPick("pattern", "l:" + learned.id, n2));
                    }
                }
            }
        }
        if (bl || "fillern".equals(string)) {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
            linkedHashSet.add(this.currentPatternKey());
            linkedHashSet.addAll(this.fillernPairs.keySet());
            ArrayList<String[]> arrayList3 = new ArrayList<String[]>();
            LinkedHashSet<String> linkedHashSet2 = new LinkedHashSet<String>();
            for (String string3 : linkedHashSet) {
                if (string3 == null || !linkedHashSet2.add(string3) || !this.fillernUnderlined(string3)) continue;
                String fillKey = this.fillernFillKeyOf(string3);
                arrayList3.add(new String[]{this.songPickSection(string3), string3, this.patternName(string3) + " · " + this.fillLabel(fillKey)});
            }
            if (arrayList3.isEmpty()) {
                if ("fillern".equals(string)) {
                    Toast.makeText((Context)this, (CharSequence)"No underlined Fillerns yet", (int)0).show();
                    return;
                }
            } else if (bl) {
                arrayList.add("— Fillern —");
                arrayList2.add(null);
                ArrayList<String[]> arrayList5 = new ArrayList<String[]>();
                for (String[] row : arrayList3) {
                    if ("Imported".equals(row[0])) {
                        arrayList5.add(row);
                        continue;
                    }
                    String string4 = row[1];
                    arrayList.add(row[2]);
                    arrayList2.add(() -> this.applySongPick("fillern", string4, n2));
                }
                this.addImportedSongRows(arrayList, arrayList2, arrayList5, "fillern", n2);
            } else {
                for (String string5 : new String[]{"Built-in", "Variated"}) {
                    boolean bl2 = false;
                    for (String[] stringArray : arrayList3) {
                        if (!string5.equals(stringArray[0])) continue;
                        if (!bl2) {
                            arrayList.add("— " + string5 + " —");
                            arrayList2.add(null);
                            bl2 = true;
                        }
                        String string6 = stringArray[1];
                        arrayList.add(stringArray[2]);
                        arrayList2.add(() -> this.applySongPick("fillern", string6, n2));
                    }
                }
                ArrayList<String[]> arrayList6 = new ArrayList<String[]>();
                for (String[] stringArray : arrayList3) {
                    if (!"Imported".equals(stringArray[0])) continue;
                    arrayList6.add(stringArray);
                }
                if (!arrayList6.isEmpty()) {
                    arrayList.add("— Imported —");
                    arrayList2.add(null);
                    this.addImportedSongRows(arrayList, arrayList2, arrayList6, "fillern", n2);
                }
            }
        }
        if (bl || "fill".equals(string)) {
            ArrayList<String[]> arrayList4 = new ArrayList<String[]>();
            for (int i = 0; i < Engine.FILL_ID.length; ++i) {
                arrayList4.add(new String[]{"Built-in", Engine.FILL_ID[i], Engine.FILL_LABEL[i]});
            }
            for (Engine.LearnedFill learnedFill : this.variatedFills) {
                arrayList4.add(new String[]{"Variated", "v:" + learnedFill.id, learnedFill.name});
            }
            for (Engine.LearnedFill learnedFill : this.learnedFills) {
                arrayList4.add(new String[]{"Imported", "l:" + learnedFill.id, learnedFill.name});
            }
            if (arrayList4.isEmpty()) {
                if ("fill".equals(string)) {
                    Toast.makeText((Context)this, (CharSequence)"No fills on the Fills tab", (int)0).show();
                    return;
                }
            } else if (bl) {
                arrayList.add("— Fill —");
                arrayList2.add(null);
                ArrayList<String[]> imported = new ArrayList<String[]>();
                for (String[] stringArray : arrayList4) {
                    if ("Imported".equals(stringArray[0])) {
                        imported.add(stringArray);
                        continue;
                    }
                    String string7 = stringArray[1];
                    arrayList.add(stringArray[2]);
                    arrayList2.add(() -> this.applySongPick("fill", string7, n2));
                }
                this.addImportedSongRows(arrayList, arrayList2, imported, "fill", n2);
            } else {
                for (String string8 : new String[]{"Built-in", "Variated"}) {
                    boolean bl4 = false;
                    for (String[] stringArray : arrayList4) {
                        if (!string8.equals(stringArray[0])) continue;
                        if (!bl4) {
                            arrayList.add("— " + string8 + " —");
                            arrayList2.add(null);
                            bl4 = true;
                        }
                        String string9 = stringArray[1];
                        arrayList.add(stringArray[2]);
                        arrayList2.add(() -> this.applySongPick("fill", string9, n2));
                    }
                }
                ArrayList<String[]> imported = new ArrayList<String[]>();
                for (String[] stringArray : arrayList4) {
                    if (!"Imported".equals(stringArray[0])) continue;
                    imported.add(stringArray);
                }
                if (!imported.isEmpty()) {
                    arrayList.add("— Imported —");
                    arrayList2.add(null);
                    this.addImportedSongRows(arrayList, arrayList2, imported, "fill", n2);
                }
            }
        }
        if (arrayList.isEmpty()) {
            return;
        }
        String string4 = n2 >= 0 ? "Replace part" : ("fillern".equals(string) ? "Fillern" : ("fill".equals(string) ? "Fill" : ("pattern".equals(string) ? "Pattern" : "Add to song")));
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)string4).setItems(arrayList.toArray(new CharSequence[0]), (dialogInterface, n) -> {
            if (n < 0 || n >= arrayList2.size()) {
                return;
            }
            Runnable runnable = (Runnable)arrayList2.get(n);
            if (runnable != null) {
                runnable.run();
            }
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    private String songFileLabel(String string, boolean bl) {
        block5: {
            if (string == null) {
                return "Other";
            }
            if (!string.startsWith("l:")) break block5;
            String string2 = string.substring(2);
            if (bl) {
                for (Engine.LearnedFill learnedFill : this.learnedFills) {
                    if (!string2.equals(learnedFill.id)) continue;
                    String string3 = Engine.sourceOf(learnedFill);
                    return string3.isEmpty() ? "Other" : string3;
                }
            } else {
                for (Engine.Learned learned : this.learned) {
                    if (!string2.equals(learned.id)) continue;
                    String string4 = Engine.sourceOf(learned);
                    return string4.isEmpty() ? "Other" : string4;
                }
            }
        }
        return "Other";
    }

    private void addImportedSongRows(List<String> list, List<Runnable> list2, List<String[]> list3, String string, int n) {
        LinkedHashMap<String, ArrayList<String[]>> linkedHashMap = new LinkedHashMap<String, ArrayList<String[]>>();
        for (String[] object : list3) {
            String string2 = this.songFileLabel(object[1], "fill".equals(string));
            ArrayList<String[]> group = linkedHashMap.get(string2);
            if (group == null) {
                group = new ArrayList<String[]>();
                linkedHashMap.put(string2, group);
            }
            group.add(object);
        }
        for (Map.Entry<String, ArrayList<String[]>> entry : linkedHashMap.entrySet()) {
            list.add("— " + entry.getKey() + " —");
            list2.add(null);
            for (String[] stringArray : entry.getValue()) {
                String string3 = stringArray[1];
                list.add("  " + stringArray[2]);
                list2.add(() -> this.applySongPick(string, string3, n));
            }
        }
    }

    private String currentPatternKey() {
        return this.patternKeyFor(this.style);
    }

    private void addFillernPickRow(List<String> list, List<Runnable> list2, String string, int n, Set<String> set) {
        if (string == null || !set.add(string)) {
            return;
        }
        if (!this.fillernUnderlined(string)) {
            return;
        }
        String string2 = this.fillernFillKeyOf(string);
        list.add(this.patternName(string) + " \u00b7 " + this.fillLabel(string2));
        String string3 = string;
        list2.add(() -> this.applySongPick("fillern", string3, n));
    }

    private String patternName(String string) {
        if (string == null) {
            return "Pattern";
        }
        if (string.startsWith("s:")) {
            Engine.Style style = this.styles.get(string.substring(2));
            return style != null ? style.label : string.substring(2);
        }
        if (string.startsWith("l:")) {
            for (Engine.Learned learned : this.learned) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.name;
            }
        }
        if (string.startsWith("v:")) {
            for (Engine.Learned learned : this.variatedPatterns) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.name;
            }
        }
        Engine.Style style = this.styles.get(this.style);
        return style != null ? style.label : "Pattern";
    }

    private int patternBpm(String string) {
        Engine.Style iterator;
        if (string != null && string.startsWith("s:") && (iterator = this.styles.get(string.substring(2))) != null) {
            return iterator.bpm;
        }
        if (string != null && string.startsWith("l:")) {
            for (Engine.Learned learned : this.learned) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.bpm;
            }
        }
        if (string != null && string.startsWith("v:")) {
            for (Engine.Learned learned : this.variatedPatterns) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.bpm;
            }
        }
        return this.bpm();
    }

    private int[][] patternCellsFor(String string) {
        Engine.Style iterator;
        if (string != null && string.startsWith("s:") && (iterator = this.styles.get(string.substring(2))) != null) {
            return Engine.styleCells(iterator);
        }
        if (string != null && string.startsWith("l:")) {
            for (Engine.Learned learned : this.learned) {
                if (!learned.id.equals(string.substring(2))) continue;
                return Engine.copyCells(learned.cells);
            }
        }
        if (string != null && string.startsWith("v:")) {
            for (Engine.Learned learned : this.variatedPatterns) {
                if (!learned.id.equals(string.substring(2))) continue;
                return Engine.copyCells(learned.cells);
            }
        }
        return Engine.copyCells(this.cells);
    }

    private int[][] songFillCells(String string, int[][] nArray) {
        if (string != null && (string.startsWith("l:") || string.startsWith("v:"))) {
            return this.fillCellsFor(string);
        }
        return Engine.buildFill(string, nArray != null ? nArray : this.cells, this.style);
    }

    private void applySongPick(String string, String string2, int n) {
        if (!"original".equals(this.songLane)) {
            return;
        }
        List<Engine.Part> list = this.activeSong();
        int n2 = n;
        if (n2 < 0 || n2 >= list.size()) {
            n2 = -1;
        }
        if ("pattern".equals(string)) {
            int n3 = n2 >= 0 && "groove".equals(list.get((int)n2).kind) ? list.get((int)n2).repeats : 4;
            Engine.Part part = Engine.groove(this.patternName(string2), this.patternBpm(string2), this.patternCellsFor(string2), n3);
            if (n2 >= 0) {
                list.set(n2, part);
            } else {
                this.add(part);
            }
        } else if ("fill".equals(string)) {
            int[][] nArray = n2 >= 0 ? list.get((int)n2).cells : this.cells;
            int n4 = n2 >= 0 && "fill".equals(list.get((int)n2).kind) ? list.get((int)n2).repeats : 1;
            int n5 = n2 >= 0 ? list.get((int)n2).bpm : this.bpm();
            Engine.Part part = Engine.fill(this.fillLabel(string2), n5, this.songFillCells(string2, nArray), n4);
            if (n2 >= 0) {
                list.set(n2, part);
            } else {
                this.add(part);
            }
        } else {
            int[][] nArray = this.patternCellsFor(string2);
            String string3 = this.fillernPairs.get(string2);
            if (string3 == null) {
                string3 = string2.equals(this.currentPatternKey()) ? this.fillId : "toms";
            }
            int n6 = n2 >= 0 && "groove".equals(list.get((int)n2).kind) ? list.get((int)n2).repeats : 4;
            Engine.Part part = Engine.groove(this.patternName(string2), this.patternBpm(string2), nArray, n6);
            Engine.Part part2 = Engine.fill(this.fillLabel(string3), this.patternBpm(string2), this.songFillCells(string3, nArray), 1);
            if (n2 >= 0) {
                list.set(n2, part);
                if (list.size() < 24) {
                    list.add(n2 + 1, part2);
                }
            } else {
                this.add(part);
                this.add(part2);
            }
        }
        this.refreshSong();
        this.setNow("fillern".equals(string) ? this.patternName(string2) + " Fillern" : ("fill".equals(string) ? this.fillLabel(string2) : this.patternName(string2)));
    }

    private void add(Engine.Part part) {
        List<Engine.Part> list = this.activeSong();
        if ("imported".equals(this.songLane) && this.importedSong() == null) {
            Toast.makeText((Context)this, (CharSequence)"No imported song yet", (int)0).show();
            return;
        }
        if (list.size() >= 24) {
            Toast.makeText((Context)this, (CharSequence)"Song is full", (int)0).show();
            return;
        }
        list.add(part);
        part.tsNum = this.tsNum;
        part.tsDen = this.tsDen;
        part.steps = "groove".equals(part.kind) ? this.steps : Engine.barSteps(this.tsNum, this.tsDen);
        if ("imported".equals(this.songLane)) {
            this.persistLearned();
        }
        this.refreshSong();
    }

    private void refreshSongBase() {
        List<Engine.Part> list;
        if (this.songAdds != null) {
            boolean bl = "edit".equals(this.songMode) && (!"imported".equals(this.songLane) || !this.importedSongs.isEmpty());
            this.songAdds.setVisibility(bl ? 0 : 8);
        }
        if (this.songPane != null) {
            LinearLayout linearLayout = (LinearLayout)this.songPane.getChildAt(0);
            for (int i = 0; i < linearLayout.getChildCount(); ++i) {
                View view = linearLayout.getChildAt(i);
                if (!(view instanceof TextView) || view.getTag() == null) continue;
                this.paintChip((TextView)view, this.songMode.equals(view.getTag()));
            }
            if (this.songPane.getChildCount() > 1 && this.songPane.getChildAt(1) instanceof LinearLayout) {
                LinearLayout linearLayout2 = (LinearLayout)this.songPane.getChildAt(1);
                for (int i = 0; i < linearLayout2.getChildCount(); ++i) {
                    View object2 = linearLayout2.getChildAt(i);
                    if (!(object2 instanceof TextView) || object2.getTag() == null) continue;
                    this.paintChip((TextView)object2, this.songLane.equals(object2.getTag()));
                }
            }
        }
        this.timeline.removeAllViews();
        this.songCards.removeAllViews();
        if ("imported".equals(this.songLane) && !this.importedSongs.isEmpty()) {
            LinearLayout linearLayout = this.row();
            for (Engine.ImportedSong importedSong : this.importedSongs) {
                TextView object = this.pill(importedSong.name, importedSong.id.equals(this.importedSongId), arg_0 -> this.refreshSongAction75(importedSong, arg_0));
                linearLayout.addView((View)object);
            }
            this.songCards.addView((View)linearLayout);
        }
        if ((list = this.activeSong()).isEmpty()) {
            TextView textView = this.text("imported".equals(this.songLane) ? "No imported song yet. After a song MIDI, choose Make song." : "Empty song \u2014 add the current pattern, a fill, or silence.", 13, false);
            textView.setTextColor(MUTED);
            textView.setPadding(0, this.dp(8), 0, 0);
            this.songCards.addView((View)textView);
            this.setNow("Song");
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        for (int i = 0; i < list.size(); ++i) {
            Engine.Part object2 = list.get(i);
            String object = object2.kind + ":" + object2.name;
            if (!arrayList.contains(object)) {
                arrayList.add(object);
            }
            int n = arrayList.indexOf(object) + 1;
            boolean bl = this.songPlay && i == this.songIndex;
            LinearLayout linearLayout = this.col();
            TextView textView = this.text(Integer.toString(n), 15, true);
            textView.setGravity(17);
            textView.setMinWidth(this.dp(44));
            textView.setPadding(this.dp(10), this.dp(8), this.dp(10), this.dp(8));
            textView.setBackground((Drawable)this.round(bl ? HIT : ELEV, 0));
            textView.setTextColor(bl ? BG : FG);
            TextView textView2 = this.text(((Engine.Part)object2).name, 10, false);
            textView2.setGravity(17);
            textView2.setPadding(this.dp(4), this.dp(6), this.dp(4), this.dp(6));
            textView2.setTextColor(bl ? FG : MUTED);
            textView2.setBackground((Drawable)this.round(bl ? Color.parseColor((String)"#2A322C") : 0, 0));
            linearLayout.addView((View)textView);
            linearLayout.addView((View)textView2);
            LinearLayout.LayoutParams layoutParams = this.wrap();
            layoutParams.setMargins(this.dp(1), 0, this.dp(1), 0);
            this.timeline.addView((View)linearLayout, (ViewGroup.LayoutParams)layoutParams);
            this.songCards.addView((View)this.partCard(i, n, (Engine.Part)object2, bl));
        }
        this.setNow("play".equals(this.songMode) ? (this.songPlay && this.songIndex < list.size() ? list.get((int)this.songIndex).name : "Play mode") : list.size() + " parts");
    }

    private LinearLayout partCardBase(int n, int n2, Engine.Part part, boolean bl) {
        LinearLayout linearLayout = this.row();
        linearLayout.setBackground((Drawable)this.round(bl ? Color.parseColor((String)"#2A322C") : ELEV, 12));
        linearLayout.setPadding(this.dp(10), this.dp(10), this.dp(10), this.dp(10));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = this.dp(6);
        linearLayout.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        TextView textView = this.text(Integer.toString(n2), 16, true);
        textView.setTextColor(bl ? HIT : FG);
        textView.setWidth(this.dp(28));
        LinearLayout linearLayout2 = this.col();
        linearLayout2.addView((View)this.text(part.name, 14, true));
        TextView textView2 = this.text("\u00d7" + part.repeats + "  \u00b7  " + part.bpm + " BPM", 11, false);
        textView2.setTextColor(MUTED);
        linearLayout2.addView((View)textView2);
        linearLayout.addView((View)textView);
        linearLayout.addView((View)linearLayout2, (ViewGroup.LayoutParams)this.flex(1));
        if ("original".equals(this.songLane) && "edit".equals(this.songMode)) {
            linearLayout.setOnLongClickListener(view -> {
                this.showSongPick(n);
                return true;
            });
        }
        if ("edit".equals(this.songMode)) {
            TextView textView3 = this.outline("\u2212", false, view -> {
                part.repeats = Engine.clamp(part.repeats - 1, 1, 32);
                if ("imported".equals(this.songLane)) {
                    this.persistLearned();
                }
                this.refreshSong();
            });
            TextView textView4 = this.outline("+", false, view -> {
                part.repeats = Engine.clamp(part.repeats + 1, 1, 32);
                if ("imported".equals(this.songLane)) {
                    this.persistLearned();
                }
                this.refreshSong();
            });
            TextView textView5 = this.outline("\u2715", false, view -> {
                List<Engine.Part> list = this.activeSong();
                if (n >= 0 && n < list.size()) {
                    list.remove(n);
                }
                if ("imported".equals(this.songLane)) {
                    this.persistLearned();
                }
                this.refreshSong();
            });
            linearLayout.addView((View)textView3);
            linearLayout.addView((View)textView4);
            linearLayout.addView((View)textView5);
        }
        return linearLayout;
    }

    private void loadStyle(String string, boolean bl) {
        Engine.Style style = this.styles.get(string);
        if (style == null) {
            return;
        }
        this.style = string;
        int[][] nArray = Engine.rowsToCells(style.rows);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, this.cells[i], 0, 32);
        }
        Engine.zeroCells(this.lens);
        if (!bl && this.bpmBar != null) {
            this.bpmBar.setVal(Engine.clampBpm(style.bpm));
            this.bpmLabel.setText((CharSequence)Integer.toString(style.bpm));
            this.applyFeel(string, null);
        }
        this.variatedPatternId = null;
        for (Engine.Learned learned : this.variatedPatterns) {
            if (!learned.id.equals(string)) continue;
            this.variatedPatternId = string;
            break;
        }
        this.syncBuiltinFill();
        this.refreshStyles();
        this.refreshGrid();
        if ("combo".equals(this.view)) {
            this.applyStoredFillern(this.patternKeyFor(string));
        }
    }

    private void variatePattern(String string) {
        Object object;
        int[][] nArray = Engine.copyCells(this.cells);
        Random random = new Random();
        String string2 = "var";
        if ("fill".equals(string)) {
            Engine.stampFillLastBar(nArray, this.fillPat, this.steps, Engine.barSteps(this.tsNum, this.tsDen));
            string2 = this.fillLabel(this.fillId).toLowerCase();
            this.fillLast = true;
        } else if ("random-fill".equals(string)) {
            object = Engine.randomFillId(random);
            Engine.stampFillLastBar(nArray, Engine.buildFill((String)object, this.cells, this.style), this.steps, Engine.barSteps(this.tsNum, this.tsDen));
            string2 = Engine.fillLabel((String)object).toLowerCase();
            this.fillLast = true;
        } else {
            Engine.nudgePattern(nArray, random, this.density(), 16);
            if (this.pluginGhostHats) {
                Engine.ghostHats(nArray, random);
            }
        }
        object = this.styles.get(this.style);
        String string3 = object != null ? ((Engine.Style)object).label : "Groove";
        Engine.Learned learned = new Engine.Learned();
        learned.id = Engine.newLearnedId();
        learned.name = Engine.uniqueLearnedName(string3 + " " + string2, this.variatedPatterns);
        learned.bpm = this.bpm();
        learned.closest = this.style;
        learned.cells = nArray;
        learned.swing = this.swing();
        learned.density = this.density();
        learned.human = this.human();
        this.variatedPatterns.add(0, learned);
        while (this.variatedPatterns.size() > 8) {
            this.variatedPatterns.remove(this.variatedPatterns.size() - 1);
        }
        this.styles.put(learned.id, new Engine.Style(learned.id, learned.name, learned.bpm, Engine.rowsFromCells(learned.cells)));
        this.addVariatedPatternChip(learned);
        this.persistLearned();
        this.loadStyle(learned.id, false);
        if (this.fillLast && "pattern".equals(this.view)) {
            this.show("combo");
        }
        this.setNow("Var \u00b7 " + learned.name);
    }

    private void addVariatedPatternChip(Engine.Learned learned) {
        if (this.variatedPatternBar == null) {
            return;
        }
        for (int i = 0; i < this.variatedPatternBar.getChildCount(); ++i) {
            if (!learned.id.equals(this.variatedPatternBar.getChildAt(i).getTag())) continue;
            return;
        }
        TextView textView = this.pill(learned.name, false, view -> this.loadStyle(learned.id, false));
        textView.setTag((Object)learned.id);
        textView.setOnLongClickListener(view -> {
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            arrayList.add("Duplicate");
            arrayList2.add(() -> this.replicateStyle(learned.id));
            arrayList.add("Rename");
            arrayList2.add(() -> this.promptRename(learned.name, string -> {
                learned.name = string;
                textView.setText((CharSequence)string);
                this.persistLearned();
            }));
            arrayList.add("Delete");
            arrayList2.add(() -> {
                this.variatedPatterns.remove(learned);
                this.variatedPatternBar.removeView((View)textView);
                this.persistLearned();
                this.syncVariatedPatternEmpty();
                if (learned.id.equals(this.variatedPatternId)) {
                    this.variatedPatternId = null;
                }
                this.setNow("Variation deleted");
            });
            this.showEntryMenu(learned.name, this.patternKeyFor(learned.id), () -> this.loadStyle(learned.id, false), arrayList, arrayList2);
            return true;
        });
        this.variatedPatternBar.addView((View)textView);
        this.syncVariatedPatternEmpty();
        this.refreshStyles();
    }

    private void syncVariatedPatternEmpty() {
        if (this.variatedPatternBar == null) {
            return;
        }
        boolean bl = false;
        View view = null;
        for (int i = 0; i < this.variatedPatternBar.getChildCount(); ++i) {
            View view2 = this.variatedPatternBar.getChildAt(i);
            if ("imported-empty".equals(view2.getTag())) {
                view = view2;
                continue;
            }
            bl = true;
        }
        if (view != null) {
            view.setVisibility(bl ? 8 : 0);
        }
    }

    private void applyFeel(String string, Engine.Learned learned) {
        int n;
        if (learned == null) {
            for (Engine.Learned learned2 : this.learned) {
                if (!learned2.id.equals(string)) continue;
                learned = learned2;
                break;
            }
        }
        String string2 = learned != null && learned.closest != null ? learned.closest : string;
        int n2 = learned != null && learned.swing >= 0 ? learned.swing : Engine.styleSwing(string2);
        int n3 = learned != null && learned.density >= 0 ? learned.density : Engine.styleDensity(string2);
        int n4 = n = learned != null && learned.human >= 0 ? learned.human : Engine.styleHuman(string2);
        if (this.swingBar != null) {
            this.swingBar.setVal(Engine.clamp(n2, 0, 75));
        }
        if (this.densBar != null) {
            this.densBar.setVal(Engine.clamp(n3, 1, 10));
        }
        if (this.humanBar != null) {
            this.humanBar.setVal(Engine.clamp(n, 0, 100));
        }
    }

    private void replicateStyle(String string) {
        Engine.Learned learned3 = null;
        for (Engine.Learned learned : this.learned) {
            if (!learned.id.equals(string)) continue;
            learned3 = learned;
            break;
        }
        if (learned3 == null) {
            for (Engine.Learned learned : this.variatedPatterns) {
                if (!learned.id.equals(string)) continue;
                learned3 = learned;
                break;
            }
        }
        Engine.Style style = this.styles.get(string);
        Engine.Learned learned22 = new Engine.Learned();
        learned22.id = Engine.newLearnedId();
        if (learned3 != null) {
            learned22.name = Engine.uniqueLearnedName(learned3.name + " copy", this.learned);
            learned22.bpm = learned3.bpm;
            learned22.cells = Engine.copyCells(learned3.cells);
            learned22.closest = learned3.closest;
            learned22.swing = learned3.swing >= 0 ? learned3.swing : this.swing();
            learned22.density = learned3.density >= 0 ? learned3.density : this.density();
            learned22.human = learned3.human >= 0 ? learned3.human : this.human();
            learned22.source = learned3.source;
        } else if (style != null) {
            learned22.name = Engine.uniqueLearnedName(style.label + " copy", this.learned);
            learned22.bpm = style.bpm;
            learned22.cells = Engine.rowsToCells(style.rows);
            learned22.closest = string;
            learned22.swing = Engine.styleSwing(string);
            learned22.density = Engine.styleDensity(string);
            learned22.human = Engine.styleHuman(string);
        } else {
            return;
        }
        this.learned.add(0, learned22);
        while (this.learned.size() > 48) {
            this.learned.remove(this.learned.size() - 1);
        }
        this.styles.put(learned22.id, new Engine.Style(learned22.id, learned22.name, learned22.bpm, Engine.rowsFromCells(learned22.cells)));
        String string2 = null;
        for (Engine.Learned learned : this.learned) {
            if (!learned.id.equals(string)) continue;
            string2 = "l:" + string;
            break;
        }
        if (string2 == null) {
            for (Engine.Learned learned : this.variatedPatterns) {
                if (!learned.id.equals(string)) continue;
                string2 = "v:" + string;
                break;
            }
        }
        if (string2 == null) {
            string2 = this.patternKeyFor(string);
        }
        String object = this.fillernPairs.get(string2);
        if (object != null) {
            this.fillernPairs.put("l:" + learned22.id, object);
        }
        this.addLearnedChip(learned22);
        this.persistLearned();
        this.loadStyle(learned22.id, false);
        this.setNow("Copy \u00b7 " + learned22.name);
    }

    private void refreshStyles() {
        View view;
        int n;
        if (this.styleBar != null) {
            for (n = 0; n < this.styleBar.getChildCount(); ++n) {
                view = this.styleBar.getChildAt(n);
                if (!(view instanceof TextView)) continue;
                this.paintChip((TextView)view, this.style.equals(view.getTag()), this.fillernUnder(String.valueOf(view.getTag())));
            }
        }
        if (this.importedHost != null) {
            this.paintStyleGroup((ViewGroup)this.importedHost);
        }
        if (this.variatedPatternBar != null) {
            for (n = 0; n < this.variatedPatternBar.getChildCount(); ++n) {
                view = this.variatedPatternBar.getChildAt(n);
                if (!(view instanceof TextView) || view.getTag() == null || "imported-empty".equals(view.getTag())) continue;
                this.paintChip((TextView)view, this.style.equals(view.getTag()), this.fillernUnder(String.valueOf(view.getTag())));
            }
        }
    }

    private void paintStyleGroup(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); ++i) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof TextView && view.getTag() != null && !"imported-empty".equals(view.getTag()) && !"pack".equals(view.getTag())) {
                this.paintChip((TextView)view, this.style.equals(view.getTag()), this.fillernUnder(String.valueOf(view.getTag())));
                continue;
            }
            if (!(view instanceof ViewGroup)) continue;
            this.paintStyleGroup((ViewGroup)view);
        }
    }

    private void syncImportedEmpty() {
        this.rebuildImported();
    }

    private void refreshGrid() {
        this.shownHead = this.playhead;
        this.applyGridWidth();
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            if (this.gridPane != null && this.gridPane.getChildCount() > i + 1) {
                LinearLayout linearLayout = (LinearLayout)this.gridPane.getChildAt(i + 1);
                TextView textView = (TextView)linearLayout.getChildAt(0);
                textView.setTextColor(this.mutes[i] ? SUBTLE : MUTED);
            }
            for (int j = 0; j < 32; ++j) {
                boolean bl = Engine.lengthCoveredAt(this.editLens()[i], this.editCells()[i], j);
                int n = this.editCells()[i][j];
                int n2 = n > 0 ? Engine.lenAt(this.editLens(), i, j) : Engine.coverLen(this.editLens(), this.editCells(), i, j);
                this.paintCell(this.grid[i][j], n, n2, j == this.playhead, this.accents[j], bl);
            }
        }
        this.paintAccentRow();
    }

    private void refreshPlayheadBase() {
        int n = this.shownHead;
        int n2 = this.playhead;
        if (n == n2) {
            return;
        }
        if (n >= 0 && n < 32) {
            this.paintColumn(n, false);
        }
        if (n2 >= 0 && n2 < 32) {
            this.paintColumn(n2, true);
        }
        this.shownHead = n2;
    }

    private void paintColumn(int n, boolean bl) {
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            boolean bl2 = Engine.lengthCoveredAt(this.editLens()[i], this.editCells()[i], n);
            int n2 = this.editCells()[i][n];
            int n3 = n2 > 0 ? Engine.lenAt(this.editLens(), i, n) : Engine.coverLen(this.editLens(), this.editCells(), i, n);
            this.paintCell(this.grid[i][n], n2, n3, bl, this.accents[n], bl2);
        }
        if (this.gridPane == null || this.gridPane.getChildCount() == 0) {
            return;
        }
        LinearLayout linearLayout = (LinearLayout)this.gridPane.getChildAt(0);
        View view = linearLayout.getChildAt(n + 1);
        if (view instanceof TextView) {
            TextView textView = (TextView)view;
            this.fillRound(textView, this.accents[n] ? ACCENT : (bl ? FG : ELEV), 6);
            textView.setText((CharSequence)(n % 4 == 0 ? Integer.toString(n / 4 + 1) : (this.accents[n] ? "\u25be" : "\u00b7")));
            textView.setTextColor(this.accents[n] || bl ? BG : FG);
        }
    }

    private void paintAccentRow() {
        if (this.gridPane == null || this.gridPane.getChildCount() == 0) {
            return;
        }
        boolean bl = false;
        for (int i = 0; i < this.steps; ++i) {
            if (!this.accents[i]) continue;
            bl = true;
            break;
        }
        if (this.accLab != null) {
            this.accLab.setTextColor(bl ? ACCENT : FG);
        }
        LinearLayout linearLayout = (LinearLayout)this.gridPane.getChildAt(0);
        for (int i = 0; i < 32; ++i) {
            View view = linearLayout.getChildAt(i + 1);
            if (!(view instanceof TextView)) continue;
            TextView textView = (TextView)view;
            this.fillRound(textView, this.accents[i] ? ACCENT : (i == this.playhead ? FG : ELEV), 6);
            textView.setText((CharSequence)(i % 4 == 0 ? Integer.toString(i / 4 + 1) : (this.accents[i] ? "\u25be" : "\u00b7")));
            textView.setTextColor(this.accents[i] || i == this.playhead ? BG : FG);
        }
    }

    private int[][] editCells() {
        return "fills".equals(this.view) ? this.fillPat : this.cells;
    }

    private int[][] editLens() {
        return "fills".equals(this.view) ? this.fillLens : this.lens;
    }

    private void setCellLen(int n, int n2, int n3) {
        int[][] nArray = this.editCells();
        int[][] nArray2 = this.editLens();
        int n4 = Engine.clampLen(n3);
        if (nArray[n][n2] <= 0) {
            nArray[n][n2] = 100;
        }
        nArray2[n][n2] = n4 <= 1 ? 0 : n4;
        this.refreshGrid();
        if (!"fills".equals(this.view)) {
            this.syncBuiltinFill();
        }
    }

    private void bindAccCell(TextView textView) {
        textView.setClickable(true);
        textView.setLongClickable(false);
        textView.setTextIsSelectable(false);
        textView.setOnTouchListener((view, motionEvent) -> {
            int n = motionEvent.getActionMasked();
            if (n == 0) {
                view.setPressed(true);
                this.disallowScroll(view, true);
                return true;
            }
            if (n == 2) {
                return true;
            }
            if (n == 1 || n == 3) {
                view.setPressed(false);
                this.disallowScroll(view, false);
                if (n == 1) {
                    view.performClick();
                }
                return true;
            }
            return true;
        });
    }

    private void bindCellLength(final TextView textView, final int n, final int n2) {
        textView.setClickable(true);
        textView.setLongClickable(false);
        textView.setTextIsSelectable(false);
        textView.setHapticFeedbackEnabled(true);
        textView.setOnLongClickListener(null);
        textView.setOnTouchListener(new View.OnTouchListener(){
            float downX;
            float downY;
            long downAt;
            boolean held;
            boolean moved;
            final Runnable fire = () -> {
                if (this.held) {
                    return;
                }
                this.held = true;
                MainActivity.this.cancelPendingCellTap();
                textView.performHapticFeedback(0);
                MainActivity.this.showLenMenu(n, n2);
            };

            public boolean onTouch(View view, MotionEvent motionEvent) {
                int n3 = motionEvent.getActionMasked();
                if (n3 == 0) {
                    boolean bl;
                    long l = SystemClock.uptimeMillis();
                    this.held = false;
                    this.moved = false;
                    this.downX = motionEvent.getRawX();
                    this.downY = motionEvent.getRawY();
                    this.downAt = l;
                    MainActivity.this.handler.removeCallbacks(this.fire);
                    view.setPressed(true);
                    MainActivity.this.disallowScroll(view, true);
                    float f = MainActivity.this.dp(22);
                    boolean bl2 = bl = l - MainActivity.this.cellTapAt < 400L && MainActivity.this.cellTapT == n && MainActivity.this.cellTapS == n2 && Math.abs(this.downX - MainActivity.this.cellTapX) <= f && Math.abs(this.downY - MainActivity.this.cellTapY) <= f;
                    if (bl) {
                        MainActivity.this.cancelPendingCellTap();
                        this.fire.run();
                        return true;
                    }
                    MainActivity.this.handler.postDelayed(this.fire, 400L);
                    return true;
                }
                if (n3 == 2) {
                    if (!this.moved && (Math.abs(motionEvent.getRawX() - this.downX) > (float)MainActivity.this.dp(28) || Math.abs(motionEvent.getRawY() - this.downY) > (float)MainActivity.this.dp(28))) {
                        this.moved = true;
                        MainActivity.this.handler.removeCallbacks(this.fire);
                        view.setPressed(false);
                        MainActivity.this.disallowScroll(view, false);
                    }
                    return true;
                }
                if (n3 == 1 || n3 == 3) {
                    long l = SystemClock.uptimeMillis() - this.downAt;
                    MainActivity.this.handler.removeCallbacks(this.fire);
                    view.setPressed(false);
                    if (this.moved || this.held) {
                        MainActivity.this.disallowScroll(view, false);
                        return true;
                    }
                    if (n3 == 3) {
                        MainActivity.this.disallowScroll(view, false);
                        return true;
                    }
                    if (l >= 280L) {
                        this.fire.run();
                        return true;
                    }
                    if (MainActivity.this.paintLen > 0) {
                        textView.performClick();
                        return true;
                    }
                    MainActivity.this.cellTapT = n;
                    MainActivity.this.cellTapS = n2;
                    MainActivity.this.cellTapAt = SystemClock.uptimeMillis();
                    MainActivity.this.cellTapX = this.downX;
                    MainActivity.this.cellTapY = this.downY;
                    MainActivity.this.cellTapView = view;
                    if (MainActivity.this.cellTapUnlock != null) {
                        MainActivity.this.handler.removeCallbacks(MainActivity.this.cellTapUnlock);
                    }
                    MainActivity.this.cellTapUnlock = () -> {
                        MainActivity.this.disallowScroll(view, false);
                        MainActivity.this.cellTapUnlock = null;
                    };
                    MainActivity.this.handler.postDelayed(MainActivity.this.cellTapUnlock, 120L);
                    if (MainActivity.this.cellTapClick != null) {
                        MainActivity.this.handler.removeCallbacks(MainActivity.this.cellTapClick);
                        MainActivity.this.cellTapClick = null;
                    }
                    textView.performClick();
                    return true;
                }
                return true;
            }
        });
    }

    private void cancelPendingCellTap() {
        this.cellTapT = -1;
        this.cellTapAt = 0L;
        if (this.cellTapClick != null) {
            this.handler.removeCallbacks(this.cellTapClick);
            this.cellTapClick = null;
        }
        if (this.cellTapUnlock != null) {
            this.handler.removeCallbacks(this.cellTapUnlock);
            this.cellTapUnlock = null;
        }
        if (this.cellTapView != null) {
            this.disallowScroll(this.cellTapView, false);
        }
        this.cellTapView = null;
    }

    private void paintLenChips() {
        if (this.lenChips[0] == null) {
            return;
        }
        for (int i = 0; i < Engine.LEN_STEPS.length; ++i) {
            int n = Engine.LEN_STEPS[i];
            GradientDrawable gradientDrawable = this.round(Engine.lenHue(n) | 0xFF000000, 8);
            if (this.paintLen == n) {
                gradientDrawable.setStroke(this.dp(3), FG);
            }
            this.lenChips[i].setBackground((Drawable)gradientDrawable);
            this.lenChips[i].setAlpha(this.paintLen == 0 || this.paintLen == n ? 1.0f : 0.45f);
        }
    }

    private void disallowScroll(View view, boolean bl) {
        for (ViewParent viewParent = view.getParent(); viewParent != null; viewParent = viewParent.getParent()) {
            viewParent.requestDisallowInterceptTouchEvent(bl);
        }
    }

    private void showLenMenu(int n, int n2) {
        int n3;
        this.hideLenMenu();
        int n4 = Engine.lenAt(this.editLens(), n, n2);
        LinearLayout linearLayout = this.col();
        GradientDrawable gradientDrawable = this.round(SURFACE, 0);
        gradientDrawable.setStroke(this.dp(1), BORDER);
        linearLayout.setBackground((Drawable)gradientDrawable);
        linearLayout.setPadding(this.dp(12), this.dp(10), this.dp(12), this.dp(16));
        LinearLayout linearLayout2 = this.row();
        linearLayout2.setGravity(16);
        LinearLayout linearLayout3 = this.col();
        TextView textView = this.text("NOTE LENGTH", 10, true);
        textView.setTextColor(SUBTLE);
        linearLayout3.addView((View)textView);
        TextView textView2 = this.text(Engine.TRACK_LABEL[n] + " \u00b7 step " + (n2 + 1) + " \u00b7 " + Engine.noteLengthLabel(n4), 13, true);
        textView2.setTextColor(FG);
        linearLayout3.addView((View)textView2);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        linearLayout2.addView((View)linearLayout3, (ViewGroup.LayoutParams)layoutParams);
        TextView textView3 = this.text("Done", 13, true);
        textView3.setTextColor(MUTED);
        textView3.setPadding(this.dp(12), this.dp(8), this.dp(4), this.dp(8));
        textView3.setOnClickListener(view -> this.hideLenMenu());
        linearLayout2.addView((View)textView3);
        linearLayout.addView((View)linearLayout2);
        LinearLayout linearLayout4 = this.row();
        linearLayout4.setPadding(0, this.dp(8), 0, 0);
        for (n3 = 0; n3 < Engine.LEN_STEPS.length; ++n3) {
            int n5 = Engine.LEN_STEPS[n3];
            TextView textView4 = this.text(Engine.LEN_LABEL[n3], 12, n5 == n4);
            textView4.setGravity(17);
            textView4.setTextColor(BG);
            GradientDrawable gradientDrawable2 = this.round(Engine.lenHue(n5) | 0xFF000000, 8);
            if (n5 == n4) {
                gradientDrawable2.setStroke(this.dp(2), FG);
            }
            textView4.setBackground((Drawable)gradientDrawable2);
            textView4.setPadding(this.dp(4), this.dp(12), this.dp(4), this.dp(12));
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams2.setMargins(this.dp(3), 0, this.dp(3), 0);
            textView4.setOnClickListener(view -> {
                if (!this.lenPopReady) {
                    return;
                }
                this.setCellLen(n, n2, n5);
                this.hideLenMenu();
            });
            linearLayout4.addView((View)textView4, (ViewGroup.LayoutParams)layoutParams2);
        }
        linearLayout.addView((View)linearLayout4);
        linearLayout.measure(View.MeasureSpec.makeMeasureSpec((int)this.getResources().getDisplayMetrics().widthPixels, (int)0x40000000), 0);
        n3 = linearLayout.getMeasuredHeight();
        this.lenPop = new PopupWindow((View)linearLayout, -1, n3, false);
        this.lenPop.setBackgroundDrawable((Drawable)new GradientDrawable());
        this.lenPop.setOutsideTouchable(false);
        this.lenPop.setElevation((float)this.dp(12));
        View linearLayout5 = this.gridPane != null ? (View)this.gridPane : this.getWindow().getDecorView();
        try {
            this.lenPop.showAtLocation((View)linearLayout5, 80, 0, 0);
        }
        catch (Exception exception) {
            Toast.makeText((Context)this, (CharSequence)(Engine.TRACK_LABEL[n] + " \u00b7 " + Engine.noteLengthLabel(n4)), (int)0).show();
            return;
        }
        this.lenPopReady = false;
        this.handler.postDelayed(() -> {
            this.lenPopReady = true;
        }, 280L);
    }

    private void hideLenMenu() {
        this.lenPopReady = false;
        if (this.lenPop == null) {
            return;
        }
        try {
            if (this.lenPop.isShowing()) {
                this.lenPop.dismiss();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        this.lenPop = null;
    }

    private void paintCell(TextView textView, int n, int n2, boolean bl, boolean bl2, boolean bl3) {
        String string;
        if (textView == null) {
            return;
        }
        int n3 = bl ? HIT : (n > 0 ? Engine.lenColor(n2, n) : (bl3 ? Engine.lenColor(n2, 0) : (bl2 ? ACC_CELL : ELEV)));
        this.fillRound(textView, n3, 6);
        Drawable drawable = textView.getBackground();
        if (drawable instanceof GradientDrawable) {
            GradientDrawable gradientDrawable = (GradientDrawable)drawable;
            if (bl2 && !bl) {
                gradientDrawable.setStroke(Math.max(2, this.dp(2)), ACCENT);
            } else {
                gradientDrawable.setStroke(0, 0);
            }
        }
        string = "";
        if (n > 0) {
            string = Engine.lenMark(n2);
            if (string.isEmpty()) {
                string = "16";
            }
        } else if (bl3) {
            string = "\u2014";
        }
        textView.setText((CharSequence)string);
        textView.setTextColor(n >= 90 ? BG : (n > 0 || bl3 ? FG : SUBTLE));
        textView.setTextSize(2, 8.0f);
    }

    private void fillRound(TextView textView, int n, int n2) {
        Drawable drawable = textView.getBackground();
        if (drawable instanceof GradientDrawable) {
            ((GradientDrawable)drawable).setColor(n);
        } else {
            textView.setBackground((Drawable)this.round(n, n2));
        }
    }

    private void paintChip(TextView textView, boolean bl) {
        this.paintChip(textView, bl, false);
    }

    private void paintChip(TextView textView, boolean bl, boolean bl2) {
        textView.setBackground((Drawable)this.round(bl ? FG : ELEV, 16));
        textView.setTextColor(bl ? BG : FG);
        int n = textView.getPaintFlags();
        if (bl2) {
            textView.setPaintFlags(n | 8);
        } else {
            textView.setPaintFlags(n & 0xFFFFFFF7);
        }
    }

    private void paintOutline(TextView textView, boolean bl) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(bl ? Color.parseColor((String)"#2A322C") : 0);
        gradientDrawable.setCornerRadius((float)this.dp(16));
        gradientDrawable.setStroke(Math.max(1, this.dp(1)), bl ? HIT : BORDER);
        textView.setBackground((Drawable)gradientDrawable);
        textView.setTextColor(bl ? FG : MUTED);
    }

    private int bpm() {
        return this.bpmBar != null ? this.bpmBar.getVal() : 124;
    }

    private EditText numField(String string, int n, int n2) {
        EditText editText = new EditText((Context)this);
        editText.setText((CharSequence)string);
        editText.setTextColor(FG);
        editText.setTextSize(2, (float)n);
        editText.setTypeface(Typeface.MONOSPACE, 1);
        editText.setBackgroundColor(0);
        editText.setInputType(2);
        editText.setGravity(0x800005);
        editText.setPadding(0, 0, 0, 0);
        editText.setMinWidth(this.dp(n2));
        editText.setSingleLine(true);
        editText.setImeOptions(6);
        editText.setIncludeFontPadding(false);
        return editText;
    }

    private void bindNumField(EditText editText, int n2, int n3, IntFn intFn) {
        Runnable runnable = () -> {
            block6: {
                try {
                    String string;
                    int v = Integer.parseInt(editText.getText().toString().trim().replaceAll("[^0-9-]", ""));
                    v = Engine.clamp(v, n2, n3);
                    if (editText == this.tsDenField) {
                        v = Engine.clampTsDen(v);
                    }
                    if (!(string = Integer.toString(v)).contentEquals((CharSequence)editText.getText())) {
                        editText.setText((CharSequence)string);
                    }
                    intFn.apply(v);
                }
                catch (Exception exception) {
                    if (editText == this.bpmLabel) {
                        editText.setText((CharSequence)Integer.toString(this.bpm()));
                    }
                    if (editText == this.tsNumField) {
                        editText.setText((CharSequence)Integer.toString(this.tsNum));
                    }
                    if (editText != this.tsDenField) break block6;
                    editText.setText((CharSequence)Integer.toString(this.tsDen));
                }
            }
        };
        editText.setOnEditorActionListener((textView, n, keyEvent) -> {
            if (n == 6 || n == 2) {
                runnable.run();
                return true;
            }
            return false;
        });
        editText.setOnFocusChangeListener((view, bl) -> {
            if (bl) {
                editText.post(() -> ((EditText)editText).selectAll());
            } else {
                runnable.run();
            }
        });
    }

    private void applyTimeSig(int n, int n2) {
        boolean bl = Engine.isDoubled(this.steps, this.tsNum, this.tsDen);
        this.tsNum = Engine.clampTsNum(n);
        this.tsDen = Engine.clampTsDen(n2);
        if (this.tsNumField != null) {
            this.tsNumField.setText((CharSequence)Integer.toString(this.tsNum));
        }
        if (this.tsDenField != null) {
            this.tsDenField.setText((CharSequence)Integer.toString(this.tsDen));
        }
        int n3 = Engine.patternSteps(this.tsNum, this.tsDen, bl);
        Engine.defaultAccents(this.accents, n3, Engine.stepsPerBeat(this.tsDen));
        this.applySteps(n3, true);
    }

    private void toggleSteps() {
        int n = Engine.barSteps(this.tsNum, this.tsDen);
        if (n * 2 <= 32 && this.steps == n) {
            this.applySteps(n * 2, true);
        } else {
            this.applySteps(n, false);
        }
    }

    private void applySteps(int n, boolean bl) {
        int n2 = Engine.barSteps(this.tsNum, this.tsDen);
        int n3 = Engine.clampSteps(n);
        if (n3 != n2 && (n3 != n2 * 2 || n2 * 2 > 32)) {
            n3 = n2;
        }
        if (n3 > this.steps && bl) {
            int n4 = Math.max(1, Math.min(this.steps, n2));
            Engine.tileSteps(this.cells, n4, n3);
            Engine.tileSteps(this.lens, n4, n3);
            for (int i = n4; i < n3; ++i) {
                this.accents[i] = this.accents[i % n4];
            }
        }
        this.steps = n3;
        if (this.stepsBtn != null) {
            this.stepsBtn.setText((CharSequence)(this.steps + " steps"));
            this.paintOutline(this.stepsBtn, this.steps > n2);
        }
        this.applyGridWidth();
        this.syncBuiltinFill();
        this.refreshGrid();
    }

    private void applyGridWidth() {
        for (int i = 0; i < 32; ++i) {
            int n;
            int n2 = n = i < this.steps ? 0 : 8;
            if (this.accCells[i] != null) {
                this.accCells[i].setVisibility(n);
            }
            for (int j = 0; j < Engine.TRACK_ID.length; ++j) {
                if (this.grid[j][i] == null) continue;
                this.grid[j][i].setVisibility(n);
            }
        }
    }

    private int currentSteps() {
        if (this.songPlay) {
            List<Engine.Part> list = this.activeSong();
            if (this.songIndex >= 0 && this.songIndex < list.size()) {
                return Engine.clampSteps(list.get((int)this.songIndex).steps);
            }
        }
        return this.steps;
    }

    private int swing() {
        return this.swingBar != null ? this.swingBar.getVal() : 12;
    }

    private int human() {
        return this.humanBar != null ? this.humanBar.getVal() : 18;
    }

    private int density() {
        return this.densBar != null ? this.densBar.getVal() : 5;
    }

    private void toggle() {
        if (this.playing) {
            this.stop();
            return;
        }
        this.ensureAudio();
        this.songPlay = "song".equals(this.view) && !this.activeSong().isEmpty();
        this.step = 0;
        this.songIndex = 0;
        this.songLoop = 0;
        this.barLoop = 0;
        this.playhead = 0;
        this.shownHead = -1;
        if (this.songPlay) {
            this.applyPart(this.activeSong().get(0));
        }
        this.playBtn.setText((CharSequence)"Pause");
        this.mixReset = true;
        this.mixNeedHit = true;
        this.playing = true;
        this.refreshGrid();
    }

    private void stop() {
        this.playing = false;
        this.mixNeedHit = false;
        this.playhead = -1;
        this.songPlay = false;
        if (this.playBtn != null) {
            this.playBtn.setText((CharSequence)"Play");
        }
        this.refreshGrid();
        this.refreshSong();
    }

    private int stepFrames(int n, int n2) {
        int n3 = (int)Math.round(1323000.0 / (double)Math.max(40, n2) / 4.0);
        if ((n & 1) == 1) {
            n3 = (int)((long)n3 + Math.round((double)(n3 * this.swing()) / 100.0));
        }
        return Math.max(48, n3);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void audioTick() {
        int n;
        List<Engine.Part> list;
        boolean bl;
        if (!this.playing) {
            return;
        }
        int n2 = this.step;
        int[][] nArray = this.cells;
        boolean bl2 = bl = !this.songPlay && this.fillLast && this.barLoop == this.bars - 1;
        if (bl) {
            nArray = this.fillPat;
        }
        Engine.Part part = null;
        List<Engine.Part> list2 = list = this.songPlay ? this.activeSong() : null;
        if (this.songPlay && list != null && this.songIndex < list.size()) {
            part = list.get(this.songIndex);
            nArray = part.cells;
        }
        int n3 = this.human();
        Object object = this.mixLock;
        synchronized (object) {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                if (this.muted || this.mutes[i]) continue;
                int n4 = n = n2 < nArray[i].length ? nArray[i][n2] : 0;
                if (n <= 0) continue;
                n = Engine.accentVel(n, n2 < this.accents.length && this.accents[n2]);
                if (n3 > 0) {
                    n = Engine.clamp(n + this.mixRng.nextInt(n3 + 1) - n3 / 2, 1, 127);
                }
                this.mixPos[i] = 0;
                this.mixGain[i] = n;
            }
        }
        int n5 = n2;
        Engine.Part part2 = part;
        this.step = n2 + 1;
        if (this.step >= this.currentSteps()) {
            this.step = 0;
            if (this.songPlay) {
                Engine.Part part3;
                List<Engine.Part> list3 = this.activeSong();
                Engine.Part part4 = part3 = this.songIndex < list3.size() ? list3.get(this.songIndex) : null;
                if (part3 == null) {
                    this.playing = false;
                    this.handler.post(this::stop);
                    return;
                }
                ++this.songLoop;
                if (this.songLoop >= Math.max(1, part3.repeats)) {
                    this.songLoop = 0;
                    ++this.songIndex;
                    if (this.songIndex >= list3.size()) {
                        this.playing = false;
                        this.handler.post(this::stop);
                        return;
                    }
                    n = 1;
                } else {
                    n = 0;
                }
            } else {
                this.barLoop = (this.barLoop + 1) % Math.max(1, this.bars);
                n = 0;
            }
        } else {
            n = 0;
        }
        boolean advanced = n != 0;
        this.handler.post(() -> this.audioTickAction94(n5, part2, advanced));
    }

    private void applyPart(Engine.Part part) {
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(part.cells[i], 0, this.cells[i], 0, 32);
        }
        this.tsNum = Engine.clampTsNum(part.tsNum);
        this.tsDen = Engine.clampTsDen(part.tsDen);
        if (this.tsNumField != null) {
            this.tsNumField.setText((CharSequence)Integer.toString(this.tsNum));
        }
        if (this.tsDenField != null) {
            this.tsDenField.setText((CharSequence)Integer.toString(this.tsDen));
        }
        this.applySteps(Engine.clampSteps(part.steps), false);
        if (this.bpmBar != null) {
            this.bpmBar.setVal(Engine.clampBpm(part.bpm));
        }
        this.bpmLabel.setText((CharSequence)Integer.toString(part.bpm));
        this.refreshGrid();
    }

    private void generate() {
        this.loadStyle(this.style, true);
        Random random = new Random();
        double d = 0.04 + (double)this.density() * 0.02;
        for (int i = 0; i < 6; ++i) {
            for (int j = 0; j < this.steps; ++j) {
                if (!(random.nextDouble() < d)) continue;
                if (this.cells[i][j] > 0 && random.nextBoolean()) {
                    this.cells[i][j] = 0;
                    continue;
                }
                if (this.cells[i][j] != 0) continue;
                this.cells[i][j] = random.nextBoolean() ? 64 : 100;
            }
        }
        if (this.pluginGhostHats) {
            Engine.ghostHats(this.cells, random);
        }
        this.syncBuiltinFill();
        this.refreshGrid();
    }

    private void ensureAudio() {
        if (this.track != null && this.mixRun) {
            return;
        }
        this.buildVoices();
        this.startMixer();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void buildVoices() {
        int n;
        short[][] sArray = AudioIo.buildVoices(22050);
        Object object = this.mixLock;
        synchronized (object) {
            for (n = 0; n < this.voices.length; ++n) {
                if (this.voices[n] != null) continue;
                this.voices[n] = sArray[n];
            }
        }
        if (this.track == null) {
            int n2 = AudioTrack.getMinBufferSize((int)22050, (int)4, (int)2);
            n = Math.max(n2, 512);
            this.track = new AudioTrack(3, 22050, 4, 2, n, 1);
            this.track.play();
        }
    }

    private void startMixer() {
        if (this.mixThread != null && this.mixThread.isAlive()) {
            return;
        }
        this.mixRun = true;
        this.mixThread = new Thread(this::mixLoop, "pulsekit-mix");
        this.mixThread.start();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void mixLoop() {
        Process.setThreadPriority((int)-16);
        short[] sArray = new short[256];
        while (this.mixRun) {
            int n;
            block29: {
                if (this.mixReset) {
                    this.mixReset = false;
                    this.mixAcc = 0;
                    this.mixNeedHit = true;
                }
                if (this.playing && this.mixNeedHit) {
                    this.mixNeedHit = false;
                    this.audioTick();
                }
                Arrays.fill(sArray, (short)0);
                Object object = this.mixLock;
                synchronized (object) {
                    int n2;
                    for (n = 0; n < this.voices.length; ++n) {
                        int n3 = this.mixPos[n];
                        if (n3 < 0) continue;
                        short[] sArray2 = this.voiceAtLocked(n);
                        if (sArray2 == null || n3 >= sArray2.length) {
                            this.mixPos[n] = -1;
                            continue;
                        }
                        float f = (float)this.mixGain[n] / 127.0f;
                        if (n >= 8) {
                            f *= 1.4f;
                        }
                        n2 = Math.min(256, sArray2.length - n3);
                        for (int i = 0; i < n2; ++i) {
                            int n4 = sArray[i] + (int)((float)sArray2[n3 + i] * f);
                            if (n4 > Short.MAX_VALUE) {
                                n4 = Short.MAX_VALUE;
                            } else if (n4 < Short.MIN_VALUE) {
                                n4 = Short.MIN_VALUE;
                            }
                            sArray[i] = (short)n4;
                        }
                        this.mixPos[n] = (n3 += n2) >= sArray2.length ? -1 : n3;
                    }
                    if (this.oneShot != null && this.oneShotPos >= 0) {
                        n = this.oneShotPos;
                        if (n >= this.oneShot.length) {
                            this.oneShotPos = -1;
                        } else {
                            float f = this.oneShotGain;
                            int n5 = Math.min(256, this.oneShot.length - n);
                            for (int i = 0; i < n5; ++i) {
                                n2 = sArray[i] + (int)((float)this.oneShot[n + i] * f);
                                if (n2 > Short.MAX_VALUE) {
                                    n2 = Short.MAX_VALUE;
                                } else if (n2 < Short.MIN_VALUE) {
                                    n2 = Short.MIN_VALUE;
                                }
                                sArray[i] = (short)n2;
                            }
                            this.oneShotPos = (n += n5) >= this.oneShot.length ? -1 : n;
                        }
                    }
                }
                AudioTrack audioTrack = this.track;
                if (audioTrack == null) {
                    try {
                        Thread.sleep(10L);
                        continue;
                    }
                    catch (InterruptedException interruptedException) {
                        break;
                    }
                }
                try {
                    n = audioTrack.write(sArray, 0, 256);
                    if (n > 0) break block29;
                    Thread.sleep(4L);
                }
                catch (Exception exception) {
                    break;
                }
            }
            if (this.playing) {
                this.mixAcc += 256;
                n = this.bpm();
                List<Engine.Part> list = this.activeSong();
                if (this.songPlay && this.songIndex < list.size()) {
                    n = list.get((int)this.songIndex).bpm;
                }
                int n6 = this.stepFrames(Math.max(0, this.step == 0 ? this.currentSteps() - 1 : this.step - 1), n);
                while (this.playing && this.mixAcc >= n6) {
                    this.mixAcc -= n6;
                    this.audioTick();
                    n = this.bpm();
                    list = this.activeSong();
                    if (this.songPlay && this.songIndex < list.size()) {
                        n = list.get((int)this.songIndex).bpm;
                    }
                    n6 = this.stepFrames(Math.max(0, this.step == 0 ? 15 : this.step - 1), n);
                }
                continue;
            }
            this.mixAcc = 0;
        }
    }

    private void bang(int n, int n2) {
        if (this.muted || this.mutes[n] || n < 0 || n >= this.mixPos.length) {
            return;
        }
        this.ensureAudio();
        int n3 = Engine.track("dkick");
        int n4 = Engine.track("kick");
        if (n == n3) {
            this.triggerMix(n3, n2);
            if (this.pendingDouble != null) {
                this.handler.removeCallbacks(this.pendingDouble);
            }
            int n5 = n2;
            this.pendingDouble = () -> {
                if (this.muted || this.mutes[n3]) {
                    return;
                }
                this.triggerMix(n4, n5);
            };
            this.handler.postDelayed(this.pendingDouble, (long)Engine.doubleKickDelayMs(this.bpm(), this.tsDen));
            return;
        }
        this.triggerMix(n, n2);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void triggerMix(int n, int n2) {
        Object object = this.mixLock;
        synchronized (object) {
            this.mixPos[n] = 0;
            this.mixGain[n] = Engine.clamp(n2, 1, 127);
        }
    }

    private void flashPad(int n) {
        if (this.padBtns[n] == null) {
            return;
        }
        this.padBtns[n].setBackground((Drawable)this.round(HIT, 12));
        this.handler.postDelayed(() -> {
            if (this.padBtns[n] != null) {
                this.padBtns[n].setBackground((Drawable)this.round(ELEV, 12));
            }
        }, 90L);
    }

    private void saveKind(int n) {
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        if (n == 9) {
            intent.setType("application/octet-stream");
            List<Engine.Part> list = this.songPartsForExport();
            intent.putExtra("android.intent.extra.TITLE", Engine.sngFilename(list));
        } else if (n == 14) {
            intent.setType("text/x-python");
            intent.putExtra("android.intent.extra.TITLE", this.pyName);
        } else if (n == 15) {
            intent.setType("application/octet-stream");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("prj"));
        } else if (n == 16) {
            intent.setType("application/octet-stream");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("pkp"));
        } else if (n == 19) {
            intent.setType("application/octet-stream");
            String string = this.fsetSaveSource != null ? this.fsetSaveSource : "";
            intent.putExtra("android.intent.extra.TITLE", Engine.fsetFilename(string.isEmpty() ? "Other" : string));
        } else if (n == 18) {
            intent.setType("application/java-archive");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("jar"));
        } else if (n == 17) {
            intent.setType("audio/midi");
            intent.putExtra("android.intent.extra.TITLE", Engine.sngFilename(this.songPartsForExport()).replace(".sng", ".mid"));
        } else if (n == 10) {
            intent.setType("audio/wav");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("wav"));
        } else if (n == 11) {
            intent.setType("audio/mpeg");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("mp3"));
        } else if (n == 12) {
            intent.setType("application/octet-stream");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("sf2"));
        } else {
            intent.setType("audio/midi");
            String string = this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : this.style;
            intent.putExtra("android.intent.extra.TITLE", Engine.midiFileName(string, this.bpm(), this.midiSaveRole, this.fillLabel(this.fillId)));
        }
        this.startActivityForResult(intent, n);
    }

    private List<Engine.Part> songPartsForExport() {
        List<Engine.Part> list = this.activeSong();
        if (!list.isEmpty()) {
            return list;
        }
        String string = this.exportName("mid").replace(".mid", "");
        return Collections.singletonList(Engine.groove(string, this.bpm(), this.cells, 1));
    }

    private byte[] encodePrj() throws Exception {
        LinkedHashMap<String, byte[]> linkedHashMap = new LinkedHashMap<String, byte[]>();
        String string = this.pyEditor != null ? this.pyEditor.getText().toString() : "";
        linkedHashMap.put("scripts/0-script.py", string.getBytes(StandardCharsets.UTF_8));
        this.fillMissingVoices();
        String string2 = this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : "Pulsekit";
        linkedHashMap.put("kit.sf2", AudioIo.encodeSf2(this.voices, string2));
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('[');
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            if (i > 0) {
                stringBuilder.append(',');
            }
            String string3 = "pads/" + i + "-" + Engine.TRACK_ID[i] + ".wav";
            linkedHashMap.put(string3, AudioIo.encodeWav(this.voices[i], 22050));
            stringBuilder.append("{\"id\":").append(Engine.quote(Engine.TRACK_ID[i]));
            stringBuilder.append(",\"note\":").append(Engine.NOTES[i]);
            stringBuilder.append(",\"name\":").append(Engine.quote(Engine.TRACK_LABEL[i]));
            stringBuilder.append(",\"file\":").append(Engine.quote(string3)).append('}');
        }
        stringBuilder.append(']');
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append('[');
        for (int i = 0; i < this.song.size(); ++i) {
            if (i > 0) {
                stringBuilder2.append(',');
            }
            Engine.Part part = this.song.get(i);
            stringBuilder2.append("{\"kind\":").append(Engine.quote(part.kind));
            stringBuilder2.append(",\"name\":").append(Engine.quote(part.name));
            stringBuilder2.append(",\"repeats\":").append(part.repeats);
            stringBuilder2.append(",\"bpm\":").append(part.bpm);
            stringBuilder2.append(",\"steps\":").append(part.steps);
            stringBuilder2.append(",\"tsNum\":").append(part.tsNum);
            stringBuilder2.append(",\"tsDen\":").append(part.tsDen);
            stringBuilder2.append(",\"pattern\":").append(Engine.cellsJson(part.cells)).append('}');
        }
        stringBuilder2.append(']');
        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append('{');
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            if (i > 0) {
                stringBuilder3.append(',');
            }
            stringBuilder3.append(Engine.quote(Engine.TRACK_ID[i])).append(':').append(this.mutes[i] ? "true" : "false");
        }
        stringBuilder3.append('}');
        String string4 = "{\"format\":\"pulsekit-prj\",\"v\":1,\"name\":" + Engine.quote(string2) + ",\"kit\":{\"pattern\":" + Engine.cellsJson(this.cells) + ",\"bpm\":" + this.bpm() + ",\"swing\":" + (this.swingBar != null ? this.swingBar.getVal() : 12) + ",\"humanize\":" + (this.humanBar != null ? (double)this.humanBar.getVal() / 100.0 : 0.18) + ",\"density\":" + (this.densBar != null ? this.densBar.getVal() : 5) + ",\"steps\":" + this.steps + ",\"tsNum\":" + this.tsNum + ",\"tsDen\":" + this.tsDen + ",\"style\":" + Engine.quote(this.style) + ",\"bars\":" + this.bars + ",\"mutes\":" + stringBuilder3 + ",\"fillLastBar\":" + this.fillLast + ",\"fillVariated\":" + this.fillVariated + ",\"accents\":" + Engine.boolJson(this.accents) + ",\"lengths\":" + Engine.cellsJson(this.lens) + "},\"fillPattern\":" + Engine.cellsJson(this.fillPat) + ",\"fillLengths\":" + Engine.cellsJson(this.fillLens) + ",\"fillId\":" + Engine.quote(this.fillId) + ",\"learned\":[],\"learnedFills\":[],\"hiddenStyles\":[],\"hiddenFills\":[],\"song\":" + stringBuilder2 + ",\"live\":" + this.livePads + ",\"songMode\":" + Engine.quote(this.songMode) + ",\"scriptName\":" + Engine.quote(this.pyName) + ",\"scripts\":[{\"name\":" + Engine.quote(this.pyName) + ",\"file\":\"scripts/0-script.py\"}],\"sf2\":\"kit.sf2\",\"sf2Name\":" + Engine.quote(string2) + ",\"pads\":" + stringBuilder + "}";
        linkedHashMap.put("project.json", string4.getBytes(StandardCharsets.UTF_8));
        return Engine.zipStored(linkedHashMap);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void loadPrj(byte[] byArray) throws Exception {
        Map<String, byte[]> map = Engine.unzip(byArray);
        byte[] byArray2 = map.get("project.json");
        if (byArray2 == null) {
            throw new IllegalArgumentException("Could not read that .prj");
        }
        String string = new String(byArray2, StandardCharsets.UTF_8);
        int n = Engine.clampBpm(this.jsonInt(string, "\"bpm\"", this.bpm()));
        if (this.bpmBar != null) {
            this.bpmBar.setVal(n);
        }
        this.bpmLabel.setText((CharSequence)Integer.toString(n));
        if (this.swingBar != null) {
            this.swingBar.setVal(Engine.clamp(this.jsonInt(string, "\"swing\"", this.swingBar.getVal()), 0, 75));
        }
        if (this.densBar != null) {
            this.densBar.setVal(Engine.clamp(this.jsonInt(string, "\"density\"", this.densBar.getVal()), 1, 10));
        }
        if (this.humanBar != null) {
            int n2 = (int)Math.round(this.jsonDouble(string, "\"humanize\"", (double)this.humanBar.getVal() / 100.0) * 100.0);
            this.humanBar.setVal(Engine.clamp(n2, 0, 100));
        }
        this.copyPattern(string, "\"pattern\"", this.cells);
        this.copyPattern(string, "\"fillPattern\"", this.fillPat);
        this.copyPattern(string, "\"lengths\"", this.lens);
        this.copyPattern(string, "\"fillLengths\"", this.fillLens);
        this.tsNum = Engine.clampTsNum(this.jsonInt(string, "\"tsNum\"", 4));
        this.tsDen = Engine.clampTsDen(this.jsonInt(string, "\"tsDen\"", 4));
        if (this.tsNumField != null) {
            this.tsNumField.setText((CharSequence)Integer.toString(this.tsNum));
        }
        if (this.tsDenField != null) {
            this.tsDenField.setText((CharSequence)Integer.toString(this.tsDen));
        }
        this.applySteps(Engine.clampSteps(this.jsonInt(string, "\"steps\"", this.steps)), false);
        this.song.clear();
        this.song.addAll(Engine.decodeSng(byArray2));
        this.songLane = "original";
        byte[] byArray3 = map.get("kit.sf2");
        if (byArray3 != null) {
            short[][] object = AudioIo.parseSf2(byArray3);
            Object object2 = this.mixLock;
            synchronized (object2) {
                for (int i = 0; i < this.voices.length && i < object.length; ++i) {
                    if (object[i] == null) continue;
                    this.voices[i] = object[i];
                    this.mixPos[i] = -1;
                }
            }
        }
        byte[] object = null;
        for (String string2 : map.keySet()) {
            if (!string2.startsWith("scripts/") || !string2.endsWith(".py")) continue;
            object = map.get(string2);
            this.pyName = string2.substring(string2.lastIndexOf(47) + 1);
            break;
        }
        if (object != null && this.pyEditor != null) {
            this.pyEditor.setText((CharSequence)new String((byte[])object, StandardCharsets.UTF_8));
        }
        this.fillLast = string.contains("\"fillLastBar\":true");
        this.show(this.song.isEmpty() ? (this.fillLast ? "combo" : "pattern") : "song");
        this.refreshGrid();
        this.refreshFills();
        this.refreshSong();
        Toast.makeText((Context)this, (CharSequence)"Project loaded", (int)0).show();
    }

    private void loadPlugin(byte[] byArray) throws Exception {
        String string;
        Map<String, byte[]> map;
        try {
            map = Engine.unzip(byArray);
        }
        catch (Exception exception) {
            map = new LinkedHashMap<String, byte[]>();
        }
        byte[] byArray2 = map.get("plugin.json");
        if (byArray2 == null) {
            byArray2 = byArray;
        }
        if (!(string = new String(byArray2, StandardCharsets.UTF_8)).contains("pulsekit-plugin")) {
            throw new IllegalArgumentException("Could not read that plugin");
        }
        this.copyPattern(string, "\"pattern\"", this.cells);
        int n = Engine.clampBpm(this.jsonInt(string, "\"bpm\"", this.bpm()));
        if (this.bpmBar != null) {
            this.bpmBar.setVal(n);
        }
        this.bpmLabel.setText((CharSequence)Integer.toString(n));
        String string2 = this.jsonStr(string, "\"name\"");
        String string3 = this.jsonStr(string, "\"id\"");
        if (string3 == null || string3.contains(".")) {
            string3 = "plug";
        }
        this.addPluginStyle(new Engine.Style(string3, string2 != null ? string2 : "Plugin", n, Engine.rowsFromCells(this.cells)));
        this.loadStyle(string3, false);
        this.pluginGhostHats = string.contains("ghostHats");
        byte[] byArray3 = null;
        for (String string4 : map.keySet()) {
            if (!string4.startsWith("scripts/") || !string4.endsWith(".py")) continue;
            byArray3 = map.get(string4);
            this.pyName = string4.substring(string4.lastIndexOf(47) + 1);
            break;
        }
        if (byArray3 != null && this.pyEditor != null) {
            this.pyEditor.setText((CharSequence)new String(byArray3, StandardCharsets.UTF_8));
        }
        this.show("pattern");
        this.refreshGrid();
        this.refreshFills();
        Toast.makeText((Context)this, (CharSequence)("Plugin \u00b7 " + (string2 != null ? string2 : "pack")), (int)0).show();
    }

    private void tryDub() {
        Engine.Style style = Engine.dubStyle();
        this.addPluginStyle(style);
        int[][] nArray = Engine.dubFill();
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, this.fillPat[i], 0, 16);
        }
        this.pluginGhostHats = true;
        this.loadStyle("dub", false);
        this.show("pattern");
        Toast.makeText((Context)this, (CharSequence)"Plugin \u00b7 Dub pack", (int)0).show();
    }

    private void addPluginStyle(Engine.Style style) {
        this.styles.put(style.id, style);
        this.rebuildImported();
    }

    private void saveMidi(String string) {
        this.midiSaveRole = string == null ? "pattern" : string;
        this.saveKind(8);
    }

    private void refreshIsolate() {
        if (this.isolateRows == null) {
            return;
        }
        this.isolateRows.removeAllViews();
        boolean bl = this.isolateAnalysis != null;
        List<AudioIo.PadIso> list = bl ? this.isolateAnalysis.pads : AudioIo.emptyPads();
        this.isolateStatus.setText((CharSequence)(bl ? AudioIo.isolationStatus(list) : "Import a WAV or MP3 to isolate pads."));
        if (this.isolateUse != null) {
            this.isolateUse.setText((CharSequence)(bl ? "Use pads" : "Import song"));
        }
        if (this.isolateSkip != null) {
            this.isolateSkip.setVisibility(bl ? 0 : 8);
        }
        for (AudioIo.PadIso padIso : list) {
            LinearLayout linearLayout = this.col();
            linearLayout.setBackground((Drawable)this.round(ELEV, 10));
            linearLayout.setPadding(this.dp(10), this.dp(8), this.dp(10), this.dp(8));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
            layoutParams.setMargins(0, 0, 0, this.dp(8));
            linearLayout.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
            LinearLayout linearLayout2 = this.row();
            LinearLayout linearLayout3 = this.col();
            linearLayout3.addView((View)this.text(padIso.name, 14, true));
            TextView textView = this.text(AudioIo.padShort(padIso.track), 10, true);
            textView.setTextColor(SUBTLE);
            if (Build.VERSION.SDK_INT >= 21) {
                textView.setLetterSpacing(0.12f);
            }
            linearLayout3.addView((View)textView);
            linearLayout2.addView((View)linearLayout3, (ViewGroup.LayoutParams)this.flex(1));
            TextView textView2 = this.text(padIso.found ? "Found" : "Missing", 11, true);
            textView2.setTextColor(padIso.found ? HIT : SUBTLE);
            linearLayout2.addView((View)textView2);
            linearLayout.addView((View)linearLayout2);
            LinearLayout linearLayout4 = this.row();
            linearLayout4.setPadding(0, this.dp(6), 0, 0);
            TextView textView3 = this.text("Hz", 10, true);
            textView3.setTextColor(SUBTLE);
            textView3.setPadding(0, 0, this.dp(8), 0);
            linearLayout4.addView((View)textView3);
            int n = padIso.lo > 0 ? padIso.lo : AudioIo.isolateLo(padIso.track);
            int n2 = padIso.hi > 0 ? padIso.hi : AudioIo.isolateHi(padIso.track);
            EditText editText = this.isoHzField(Integer.toString(n), Integer.toString(AudioIo.isolateLo(padIso.track)));
            EditText editText2 = this.isoHzField(Integer.toString(n2), Integer.toString(AudioIo.isolateHi(padIso.track)));
            editText.setEnabled(bl);
            editText2.setEnabled(bl);
            linearLayout4.addView((View)editText);
            TextView textView4 = this.text("\u2013", 14, false);
            textView4.setPadding(this.dp(4), 0, this.dp(4), 0);
            linearLayout4.addView((View)textView4);
            linearLayout4.addView((View)editText2);
            String string = padIso.track;
            TextView textView5 = this.action("Try", SURFACE, FG, view -> {
                int lo = 0;
                int hi = 0;
                try {
                    lo = Integer.parseInt(editText.getText().toString().trim());
                }
                catch (Exception exception) {
                    // empty catch block
                }
                try {
                    hi = Integer.parseInt(editText2.getText().toString().trim());
                }
                catch (Exception exception) {
                    // empty catch block
                }
                this.tryIsolatePad(string, lo, hi);
            });
            textView5.setEnabled(bl);
            textView5.setAlpha(bl ? 1.0f : 0.4f);
            linearLayout4.addView((View)textView5, (ViewGroup.LayoutParams)this.flexBtn());
            TextView textView6 = this.action("Sample", SURFACE, FG, view -> {
                this.isolateSampleTrack = string;
                Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
                intent.addCategory("android.intent.category.OPENABLE");
                intent.setType("audio/*");
                this.startActivityForResult(intent, 21);
            });
            textView6.setEnabled(bl);
            textView6.setAlpha(bl ? 1.0f : 0.4f);
            linearLayout4.addView((View)textView6, (ViewGroup.LayoutParams)this.flexBtn());
            if (padIso.found) {
                linearLayout4.addView((View)this.action("Play", SURFACE, FG, view -> this.previewIsoPad(string)), (ViewGroup.LayoutParams)this.flexBtn());
            }
            linearLayout.addView((View)linearLayout4);
            this.isolateRows.addView((View)linearLayout);
        }
        this.isolateRows.requestLayout();
    }

    private EditText isoHzField(String string, String string2) {
        EditText editText = new EditText((Context)this);
        editText.setText((CharSequence)string);
        editText.setHint((CharSequence)string2);
        editText.setHintTextColor(SUBTLE);
        editText.setInputType(2);
        editText.setTextColor(FG);
        editText.setTextSize(2, 14.0f);
        editText.setTypeface(Typeface.MONOSPACE);
        editText.setBackground((Drawable)this.round(SURFACE, 8));
        editText.setPadding(this.dp(8), this.dp(8), this.dp(8), this.dp(8));
        editText.setSingleLine(true);
        editText.setGravity(17);
        editText.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dp(64), this.dp(40)));
        return editText;
    }

    private String isoFileKey(String string) {
        String string2 = string == null ? "song" : string;
        int n = Math.max(string2.lastIndexOf(47), string2.lastIndexOf(92));
        if (n >= 0 && n + 1 < string2.length()) {
            string2 = string2.substring(n + 1);
        }
        return (string2 = string2.replaceAll("(?i)\\.(wav|wave|mp3)$", "").trim().toLowerCase()).isEmpty() ? "song" : string2;
    }

    private void rememberIsoPads() {
        if (this.isolateFile == null || this.isolateAnalysis == null) {
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject(this.getSharedPreferences("iso_hz", 0).getString("map", "{}"));
            String string = this.isoFileKey(this.isolateFile);
            JSONObject jSONObject2 = jSONObject.has(string) ? jSONObject.getJSONObject(string) : new JSONObject();
            for (AudioIo.PadIso padIso : this.isolateAnalysis.pads) {
                if (padIso.lo < 18 || padIso.hi <= padIso.lo) continue;
                JSONObject jSONObject3 = new JSONObject();
                jSONObject3.put("lo", padIso.lo);
                jSONObject3.put("hi", padIso.hi);
                jSONObject3.put("hz", padIso.hz);
                jSONObject2.put(padIso.track, (Object)jSONObject3);
            }
            jSONObject.put(string, (Object)jSONObject2);
            this.getSharedPreferences("iso_hz", 0).edit().putString("map", jSONObject.toString()).apply();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void applyIsoMemory(AudioIo.Analysis analysis, String string) {
        if (analysis == null || analysis.pcm == null) {
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject(this.getSharedPreferences("iso_hz", 0).getString("map", "{}"));
            String string2 = this.isoFileKey(string);
            if (!jSONObject.has(string2)) {
                return;
            }
            JSONObject jSONObject2 = jSONObject.getJSONObject(string2);
            for (int i = 0; i < analysis.pads.size(); ++i) {
                AudioIo.PadIso padIso = analysis.pads.get(i);
                if (!jSONObject2.has(padIso.track)) continue;
                JSONObject jSONObject3 = jSONObject2.getJSONObject(padIso.track);
                int n = jSONObject3.optInt("lo", 0);
                int n2 = jSONObject3.optInt("hi", 0);
                if (n < 18 || n2 <= n) continue;
                AudioIo.PadIso padIso2 = AudioIo.isolateAtRange(analysis.pcm, padIso.track, n, n2);
                if (padIso2.found) {
                    analysis.pads.set(i, padIso2);
                    continue;
                }
                padIso.lo = n;
                padIso.hi = n2;
                if (!jSONObject3.has("hz")) continue;
                padIso.hz = jSONObject3.optInt("hz", padIso.hz);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void tryIsolatePad(String string, int n, int n2) {
        if (this.isolateAnalysis == null || this.isolateAnalysis.pcm == null) {
            return;
        }
        int n3 = n > 0 ? n : AudioIo.isolateLo(string);
        int n4 = n2 > n3 ? n2 : AudioIo.isolateHi(string);
        AudioIo.PadIso padIso = AudioIo.isolateAtRange(this.isolateAnalysis.pcm, string, n3, n4);
        for (int i = 0; i < this.isolateAnalysis.pads.size(); ++i) {
            if (!string.equals(this.isolateAnalysis.pads.get((int)i).track)) continue;
            this.isolateAnalysis.pads.set(i, padIso);
            break;
        }
        AudioIo.continueIsolation(this.isolateAnalysis.pcm, this.isolateAnalysis.pads);
        this.rememberIsoPads();
        String string2 = padIso.found ? padIso.name + " \u00b7 " + padIso.lo + "\u2013" + padIso.hi + " Hz." : "No " + padIso.name + " in " + n3 + "\u2013" + n4 + " Hz.";
        this.isolateStatus.setText((CharSequence)(AudioIo.isolationStatus(this.isolateAnalysis.pads) + " " + string2));
        this.setNow(string2);
        this.refreshIsolate();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void previewIsoPad(String string) {
        if (this.isolateAnalysis == null) {
            return;
        }
        for (AudioIo.PadIso padIso : this.isolateAnalysis.pads) {
            if (!string.equals(padIso.track) || !padIso.found || padIso.sample == null) continue;
            Object object = this.mixLock;
            synchronized (object) {
                this.oneShot = padIso.sample;
                this.oneShotPos = 0;
                this.oneShotGain = 0.86f;
            }
            return;
        }
    }

    private void finishIsolationBase() {
        if (this.isolateAnalysis == null) {
            this.show("import");
            return;
        }
        AudioIo.Analysis analysis = this.isolateAnalysis;
        this.rememberIsoPads();
        String string = this.isolateFile != null ? this.isolateFile : "song";
        this.isolateAnalysis = null;
        this.isolateFile = null;
        this.applyAnalysis(analysis, string);
    }

    private void applyAnalysis(AudioIo.Analysis analysis, String string) {
        int n;
        int n2 = 0;
        for (AudioIo.PadIso padIso : analysis.pads) {
            if (!padIso.found || padIso.sample == null) continue;
            ++n2;
        }
        if (n2 == 0 && (analysis.kickSample != null || analysis.snareSample != null)) {
            n2 = 1;
        }
        if (n2 > 0) {
            this.addIsolatedSet(Engine.stemNameFromMidi(string), analysis.pads);
        }
        if (analysis.bars.size() >= 1) {
            Engine.MidiBars midiBars = new Engine.MidiBars();
            midiBars.bpm = analysis.bpm;
            midiBars.bars.addAll(analysis.bars);
            if (this.learnFromSongImport(string, midiBars, true)) {
                if (n2 > 0) {
                    Toast.makeText((Context)this, (CharSequence)(n2 + " pads from the song"), (int)0).show();
                }
                return;
            }
        }
        int n3 = Math.min(analysis.cells[0].length, this.cells[0].length);
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            System.arraycopy(analysis.cells[n], 0, this.cells[n], 0, n3);
        }
        if (this.bpmBar != null) {
            this.bpmBar.setVal(Engine.clampBpm(analysis.bpm));
        }
        this.bpmLabel.setText((CharSequence)Integer.toString(analysis.bpm));
        if (this.styles.containsKey(analysis.styleId)) {
            this.loadStyle(analysis.styleId, true);
        }
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            System.arraycopy(analysis.cells[n], 0, this.cells[n], 0, n3);
        }
        this.refreshGrid();
        this.learnFromImport(string, analysis.cells, analysis.bpm);
        Engine.Style style = this.styles.get(analysis.styleId);
        String string2 = style != null ? style.label : analysis.styleId;
        String string3 = n2 > 0 ? " \u00b7 " + n2 + " pads" : "";
        String string4 = analysis.isolated ? string2 + " \u00b7 " + analysis.bpm + " BPM \u00b7 drums from the song" + string3 : string2 + " \u00b7 " + analysis.bpm + " BPM \u00b7 built a fill" + string3;
        Toast.makeText((Context)this, (CharSequence)string4, (int)1).show();
        this.setNow(string4);
    }

    /*
     * WARNING - void declaration
     */
    private void learnFromImport(String string, int[][] nArray, int n) {
        Engine.LearnedFill learnedFill = null;
        boolean bl;
        if (Engine.hitCount(nArray) < 1) {
            return;
        }
        String string2 = Engine.midiRoleFromFile(string);
        boolean bl2 = "pattern".equals(string2);
        boolean bl3 = "fill".equals(string2);
        boolean bl4 = "fillern".equals(string2) || !bl2 && !bl3 && Engine.importedFillern(nArray);
        boolean bl5 = bl = bl3 || bl4 || !bl2 && (Engine.shouldImportFill(nArray) || Engine.hitCount(Engine.lastBar(nArray)) >= 2);
        if (bl3) {
            Engine.LearnedFill learnedFill2 = this.addImportedFill(string, nArray);
            if (learnedFill2 != null) {
                this.applyFill("l:" + learnedFill2.id);
                this.show("fills");
                this.persistLearned();
                this.setNow(this.fillLabel(learnedFill2.kind) + " \u00b7 " + learnedFill2.name);
            }
            return;
        }
        int[][] nArray2 = bl4 ? Engine.firstBar(nArray) : nArray;
        String string3 = Engine.patternSignature(nArray2);
        Engine.Learned object3 = null;
        for (Engine.Learned object22 : this.learned) {
            if (!Engine.patternSignature(object22.cells).equals(string3)) continue;
            object3 = object22;
            this.loadStyle(object22.id, false);
            break;
        }
        if (object3 == null) {
            Engine.Learned object = new Engine.Learned();
            object.id = Engine.newLearnedId();
            object.name = Engine.stemNameFromMidi(string);
            object.bpm = n;
            object.closest = Engine.matchStyle(nArray2);
            object.cells = Engine.copyCells(nArray2);
            object.swing = this.swing();
            object.density = this.density();
            object.human = this.human();
            object.source = Engine.importSource(string);
            this.learned.add(0, object);
            while (this.learned.size() > 48) {
                this.learned.remove(this.learned.size() - 1);
            }
            this.styles.put(object.id, new Engine.Style(object.id, object.name, object.bpm, Engine.rowsFromCells(object.cells)));
            this.addLearnedChip(object);
            object3 = object;
            this.loadStyle(object.id, false);
        }
        String object = "Style \u00b7 " + object3.name;
        if (bl && (learnedFill = this.addImportedFill(string, nArray)) != null) {
            object = object + " \u00b7 fill \u00b7 " + learnedFill.name;
        }
        if (bl4 && learnedFill != null) {
            this.applyFill("l:" + learnedFill.id);
            this.rememberFillern(this.patternKeyFor(object3.id), "l:" + learnedFill.id);
            this.show("combo");
            object = "Fillern \u00b7 " + object3.name + " \u00b7 " + this.fillLabel(learnedFill.kind);
        } else if (bl2) {
            this.show("pattern");
        }
        this.persistLearned();
        this.setNow(object);
    }

    private boolean learnFromSongImportBase(String string, Engine.MidiBars midiBars) {
        return this.learnFromSongImport(string, midiBars, false);
    }

    /*
     * WARNING - void declaration
     */
    private boolean learnFromSongImportBase(String string, Engine.MidiBars midiBars, boolean bl) {
        boolean bl2;
        List<Engine.MidiSeg> list = Engine.segmentMidiBars(midiBars.bars);
        if (list.isEmpty()) {
            return false;
        }
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        LinkedHashSet<String> linkedHashSet2 = new LinkedHashSet<String>();
        int n = 0;
        for (Engine.MidiSeg object52 : list) {
            linkedHashSet.add(Engine.patternSignature(object52.groove));
            if (object52.fill != null) {
                linkedHashSet2.add(Engine.patternSignature(object52.fill));
            }
            if (!"fillern".equals(object52.kind)) continue;
            ++n;
        }
        boolean bl3 = bl2 = midiBars.bars.size() >= 3 && (list.size() > 1 || linkedHashSet.size() > 1 || linkedHashSet2.size() > 1 || n > 0 && midiBars.bars.size() >= 4);
        if (!bl && !bl2) {
            return false;
        }
        String string2 = Engine.stemNameFromMidi(string);
        int[][] nArray = Engine.copyCells(list.get((int)0).groove);
        for (int linkedHashMap = 0; linkedHashMap < Engine.TRACK_ID.length; ++linkedHashMap) {
            System.arraycopy(nArray[linkedHashMap], 0, this.cells[linkedHashMap], 0, 32);
        }
        if (this.bpmBar != null) {
            this.bpmBar.setVal(Engine.clampBpm(midiBars.bpm));
        }
        this.bpmLabel.setText((CharSequence)Integer.toString(this.bpm()));
        this.applyTimeSig(midiBars.tsNum, midiBars.tsDen);
        this.refreshGrid();
        LinkedHashMap<String, Engine.Learned> linkedHashMap = new LinkedHashMap<String, Engine.Learned>();
        LinkedHashMap<String, Engine.LearnedFill> linkedHashMap2 = new LinkedHashMap<String, Engine.LearnedFill>();
        String string3 = null;
        String string4 = null;
        for (Engine.MidiSeg midiSeg : list) {
            String string5 = Engine.patternSignature(midiSeg.groove);
            Engine.Learned object2 = linkedHashMap.get(string5);
            if (object2 == null) {
                for (Engine.Learned learned : this.learned) {
                    if (!Engine.patternSignature(learned.cells).equals(string5)) continue;
                    object2 = learned;
                    break;
                }
                if (object2 == null && Engine.hitCount(midiSeg.groove) >= 1) {
                    Engine.Learned object = new Engine.Learned();
                    object.id = Engine.newLearnedId();
                    object.name = Engine.uniqueLearnedName(string2, this.learned);
                    object.bpm = midiBars.bpm;
                    object.closest = Engine.matchStyle(midiSeg.groove);
                    object.cells = Engine.copyCells(midiSeg.groove);
                    object.swing = this.swing();
                    object.density = this.density();
                    object.human = this.human();
                    object.source = string2;
                    this.learned.add(0, object);
                    while (this.learned.size() > 48) {
                        this.learned.remove(this.learned.size() - 1);
                    }
                    this.styles.put(object.id, new Engine.Style(object.id, object.name, object.bpm, Engine.rowsFromCells(object.cells)));
                    this.addLearnedChip(object);
                    object2 = object;
                }
                if (object2 != null) {
                    linkedHashMap.put(string5, object2);
                }
            }
            if (object2 != null && string3 == null) {
                string3 = object2.id;
            }
            if (midiSeg.fill == null || object2 == null) continue;
            String fillSig = Engine.patternSignature(midiSeg.fill);
            Engine.LearnedFill learnedFill2 = linkedHashMap2.get(fillSig);
            if (learnedFill2 == null && (learnedFill2 = this.addImportedFillBar(string, midiSeg.fill, string2 + " " + Engine.fillLabel(Engine.classifyFill(midiSeg.fill)))) != null) {
                linkedHashMap2.put(fillSig, learnedFill2);
            }
            if (learnedFill2 == null) continue;
            this.rememberFillern(this.patternKeyFor(object2.id), "l:" + learnedFill2.id);
            if (string4 != null) continue;
            string4 = object2.id;
        }
        ArrayList<Engine.Part> arrayList = new ArrayList<Engine.Part>();
        for (Engine.MidiSeg midiSeg : list) {
            if (arrayList.size() >= 24) break;
            Engine.Learned object2 = linkedHashMap.get(Engine.patternSignature(midiSeg.groove));
            arrayList.add(Engine.groove(object2 != null ? object2.name : string2, midiBars.bpm, midiSeg.groove, midiSeg.grooveRepeats));
            if (midiSeg.fill == null || arrayList.size() >= 24) continue;
            Engine.LearnedFill object = linkedHashMap2.get(Engine.patternSignature(midiSeg.fill));
            arrayList.add(Engine.fill(object != null ? object.name : "fill", midiBars.bpm, midiSeg.fill, 1));
        }
        if (string4 != null) {
            this.loadStyle(string4, false);
            this.show("combo");
            this.applyStoredFillern(this.patternKeyFor(string4));
        } else {
            if (string3 != null) {
                this.loadStyle(string3, false);
            }
            this.show("pattern");
        }
        this.persistLearned();
        StringBuilder stringBuilder = new StringBuilder("Imported \u00b7 ").append(linkedHashMap.size()).append(linkedHashMap.size() == 1 ? " pattern" : " patterns");
        if (!linkedHashMap2.isEmpty()) {
            stringBuilder.append(" \u00b7 ").append(linkedHashMap2.size()).append(linkedHashMap2.size() == 1 ? " fill" : " fills");
        }
        if (n > 0) {
            stringBuilder.append(" \u00b7 ").append(n).append(n == 1 ? " Fillern" : " Fillerns");
        }
        this.setNow(stringBuilder.toString());
        Toast.makeText((Context)this, (CharSequence)stringBuilder.toString(), (int)1).show();
        if (!arrayList.isEmpty()) {
            String string6 = string2;
            ArrayList<Engine.Part> object2 = new ArrayList<Engine.Part>(arrayList);
            new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Make a song?").setMessage((CharSequence)(stringBuilder + "\n\nIt goes under Imported. Your original song stays.")).setPositiveButton((CharSequence)"Make song", (arg_0, arg_1) -> this.learnFromSongImportAction100(string6, object2, arg_0, arg_1)).setNegativeButton((CharSequence)"Not now", null).show();
        }
        return true;
    }

    private Engine.LearnedFill addImportedFillBar(String string, int[][] nArray, String string2) {
        if (Engine.hitCount(nArray) < 1 && !"rest".equals(Engine.classifyFill(nArray))) {
            return null;
        }
        String string3 = Engine.patternSignature(nArray);
        for (Engine.LearnedFill learnedFill : this.learnedFills) {
            if (!Engine.patternSignature(learnedFill.cells).equals(string3)) continue;
            return learnedFill;
        }
        Engine.LearnedFill learnedFill = new Engine.LearnedFill();
        learnedFill.id = Engine.newLearnedId();
        learnedFill.kind = Engine.classifyFill(nArray);
        learnedFill.name = Engine.uniqueFillName(string2 != null ? string2 : Engine.fillNameFromFile(string, learnedFill.kind), this.learnedFills);
        learnedFill.cells = Engine.copyCells(nArray);
        learnedFill.source = Engine.importSource(string);
        this.learnedFills.add(0, learnedFill);
        while (this.learnedFills.size() > 48) {
            this.learnedFills.remove(this.learnedFills.size() - 1);
        }
        this.addLearnedFillChip(learnedFill);
        return learnedFill;
    }

    private Engine.LearnedFill addImportedFill(String string, int[][] nArray) {
        return this.addImportedFillBar(string, Engine.lastBar(nArray), null);
    }

    private void addLearnedChip(Engine.Learned learned) {
        this.rebuildImported();
    }

    private void addLearnedFillChip(Engine.LearnedFill learnedFill) {
        this.rebuildImportedFills();
    }

    private boolean packOpen(String string, boolean bl) {
        Boolean bl2 = this.openPacks.get(string);
        return bl2 != null ? bl2 : bl;
    }

    private void addImportPackBase(LinearLayout linearLayout, String string, String string2, int n, boolean bl, Runnable runnable, Runnable runnable2, Consumer<FlowLayout> consumer) {
        boolean bl2 = this.packOpen(string, bl);
        TextView textView = this.pill((bl2 ? "\u25be " : "\u25b8 ") + string2 + " \u00b7 " + n, bl, view -> {
            this.openPacks.put(string, !this.packOpen(string, bl));
            this.rebuildImported();
            this.rebuildImportedFills();
            this.refreshImportedFiles();
        });
        textView.setTag((Object)"pack");
        textView.setOnLongClickListener(view -> {
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            if (runnable2 != null) {
                arrayList.add("Export .fset");
                arrayList2.add(runnable2);
            }
            arrayList.add("Delete file set");
            arrayList2.add(runnable);
            new AlertDialog.Builder((Context)this).setTitle((CharSequence)string2).setItems(arrayList.toArray(new CharSequence[0]), (dialogInterface, which) -> {
                if (which >= 0 && which < arrayList2.size()) {
                    ((Runnable)arrayList2.get(which)).run();
                }
            }).show();
            return true;
        });
        linearLayout.addView((View)textView);
        if (bl2) {
            FlowLayout flowLayout = new FlowLayout((Context)this, this.dp(6), this.dp(6));
            flowLayout.setSingleLine(true);
            consumer.accept(flowLayout);
            linearLayout.addView((View)this.chipStrip((View)flowLayout));
        }
    }

    private void rebuildImported() {
        if (this.importedHost == null) {
            return;
        }
        this.importedHost.removeAllViews();
        if (this.learned.isEmpty()) {
            TextView textView = this.text("MIDI, song or plugin pack", 12, false);
            textView.setTextColor(MUTED);
            textView.setTag((Object)"imported-empty");
            this.importedHost.addView((View)textView);
            this.refreshImportedFiles();
            return;
        }
        LinkedHashMap<String, ArrayList<Engine.Learned>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.Learned>>();
        for (Engine.Learned object : this.learned) {
            String string = Engine.sourceOf(object);
            ArrayList<Engine.Learned> arrayList = linkedHashMap.get(string);
            if (arrayList == null) {
                arrayList = new ArrayList<Engine.Learned>();
                linkedHashMap.put(string, arrayList);
            }
            arrayList.add(object);
        }
        for (Map.Entry<String, ArrayList<Engine.Learned>> entry : linkedHashMap.entrySet()) {
            String string = entry.getKey();
            ArrayList<Engine.Learned> arrayList = entry.getValue();
            boolean bl = false;
            for (Engine.Learned object2 : arrayList) {
                if (!this.style.equals(object2.id)) continue;
                bl = true;
            }
            String string2 = string.isEmpty() ? "Other" : string;
            String packKey = string.isEmpty() ? "o:other" : "f:" + string;
            this.addImportPack(this.importedHost, packKey, string2, arrayList.size(), bl, () -> this.removeImportSource(string), () -> this.saveFset(string), flowLayout -> {
                for (Engine.Learned learned : arrayList) {
                    TextView textView = this.pill(learned.name, false, view -> this.loadStyle(learned.id, false));
                    textView.setTag((Object)learned.id);
                    this.attachLearnedStyleMenu(textView, learned);
                    flowLayout.addView((View)textView);
                }
            });
        }
        this.refreshStyles();
        this.refreshImportedFiles();
    }

    private void rebuildImportedFills() {
        if (this.importedFillHost == null) {
            return;
        }
        this.importedFillHost.removeAllViews();
        if (this.learnedFills.isEmpty()) {
            TextView textView = this.text("Last bar of an imported MIDI", 12, false);
            textView.setTextColor(MUTED);
            textView.setTag((Object)"imported-empty");
            this.importedFillHost.addView((View)textView);
            this.refreshImportedFiles();
            return;
        }
        LinkedHashMap<String, ArrayList<Engine.LearnedFill>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.LearnedFill>>();
        for (Engine.LearnedFill object : this.learnedFills) {
            String string = Engine.sourceOf(object);
            ArrayList<Engine.LearnedFill> arrayList = linkedHashMap.get(string);
            if (arrayList == null) {
                arrayList = new ArrayList<Engine.LearnedFill>();
                linkedHashMap.put(string, arrayList);
            }
            arrayList.add(object);
        }
        for (Map.Entry<String, ArrayList<Engine.LearnedFill>> entry : linkedHashMap.entrySet()) {
            String string = entry.getKey();
            ArrayList<Engine.LearnedFill> arrayList = entry.getValue();
            boolean bl = false;
            for (Engine.LearnedFill object2 : arrayList) {
                if (!("l:" + object2.id).equals(this.fillId)) continue;
                bl = true;
            }
            String string2 = string.isEmpty() ? "Other" : string;
            String packKey = string.isEmpty() ? "o:other" : "f:" + string;
            this.addImportPack(this.importedFillHost, packKey, string2, arrayList.size(), bl, () -> this.removeImportSource(string), () -> this.saveFset(string), flowLayout -> {
                for (Engine.LearnedFill learnedFill : arrayList) {
                    String key = "l:" + learnedFill.id;
                    TextView textView = this.pill(learnedFill.name, false, view -> this.applyFill(key));
                    textView.setTag((Object)key);
                    this.attachLearnedFillMenu(textView, learnedFill);
                    flowLayout.addView((View)textView);
                }
            });
        }
        this.refreshFills();
        this.refreshImportedFiles();
    }

    private void removeImportSourceBase(String string) {
        String string2 = string == null ? "" : string;
        this.learned.removeIf(learned -> string2.equals(Engine.sourceOf(learned)));
        this.learnedFills.removeIf(learnedFill -> string2.equals(Engine.sourceOf(learnedFill)));
        this.fillernPairs.entrySet().removeIf(entry -> {
            String id;
            String key = (String)entry.getKey();
            String string3 = (String)entry.getValue();
            boolean bl = false;
            boolean bl2 = false;
            if (key != null && key.startsWith("l:")) {
                id = key.substring(2);
                bl = true;
                for (Engine.Learned object : this.learned) {
                    if (!id.equals(object.id)) continue;
                    bl = false;
                }
            }
            if (string3 != null && string3.startsWith("l:")) {
                id = string3.substring(2);
                bl2 = true;
                for (Engine.LearnedFill learnedFill : this.learnedFills) {
                    if (!id.equals(learnedFill.id)) continue;
                    bl2 = false;
                }
            }
            return bl || bl2;
        });
        this.persistLearned();
        this.rebuildImported();
        this.rebuildImportedFills();
        this.setNow("Removed \u00b7 " + (string2.isEmpty() ? "Other" : string2));
    }

    private String selectedFileSource() {
        Object object;
        for (Engine.Learned iterator : this.learned) {
            if (!iterator.id.equals(this.style)) continue;
            return Engine.sourceOf(iterator);
        }
        if (this.fillId != null && this.fillId.startsWith("l:")) {
            object = this.fillId.substring(2);
            for (Engine.LearnedFill learnedFill : this.learnedFills) {
                if (!((String)object).equals(learnedFill.id)) continue;
                return Engine.sourceOf(learnedFill);
            }
        }
        object = new LinkedHashMap();
        for (Engine.Learned learned : this.learned) {
            ((HashMap)object).put(Engine.sourceOf(learned), Boolean.TRUE);
        }
        for (Engine.LearnedFill learnedFill : this.learnedFills) {
            ((HashMap)object).put(Engine.sourceOf(learnedFill), Boolean.TRUE);
        }
        if (((HashMap)object).isEmpty()) {
            return null;
        }
        return (String)((LinkedHashMap)object).keySet().iterator().next();
    }

    private void saveFset(String string) {
        String string2 = string;
        if (string2 == null) {
            string2 = this.selectedFileSource();
        }
        if (string2 == null) {
            Toast.makeText((Context)this, (CharSequence)"Pick an imported file set first", (int)0).show();
            return;
        }
        String string3 = string2.isEmpty() ? "Other" : string2;
        Engine.FileSet fileSet = Engine.collectFset(string2, string3, this.learned, this.learnedFills, this.fillernPairs);
        if (fileSet == null) {
            Toast.makeText((Context)this, (CharSequence)"That file set is empty", (int)0).show();
            return;
        }
        this.fsetSaveSource = string2;
        this.saveKind(19);
    }

    private void loadFsetBase(byte[] byArray, String string) throws Exception {
        Engine.FileSet fileSet = Engine.decodeFset(byArray);
        String string2 = Engine.importSource(string);
        if (string2 == null || string2.isEmpty() || "Import".equals(string2)) {
            string2 = fileSet.name;
        }
        string2 = Engine.uniqueImportSource(string2, this.learned, this.learnedFills);
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
        ArrayList<Engine.LearnedFill> arrayList = new ArrayList<Engine.LearnedFill>();
        String string3 = null;
        for (Engine.Learned object2 : fileSet.patterns) {
            Engine.Learned copy = new Engine.Learned();
            copy.id = Engine.newLearnedId();
            copy.name = Engine.uniqueLearnedName(object2.name, this.learned);
            copy.bpm = object2.bpm;
            copy.closest = object2.closest;
            copy.cells = Engine.copyCells(object2.cells);
            copy.source = string2;
            this.learned.add(0, copy);
            while (this.learned.size() > 48) {
                this.learned.remove(this.learned.size() - 1);
            }
            this.styles.put(copy.id, new Engine.Style(copy.id, copy.name, copy.bpm, Engine.rowsFromCells(copy.cells)));
            linkedHashMap.put(object2.name, copy.id);
            if (string3 != null) continue;
            string3 = copy.id;
        }
        for (Engine.LearnedFill learnedFill : fileSet.fills) {
            Engine.LearnedFill copy = new Engine.LearnedFill();
            copy.id = Engine.newLearnedId();
            copy.name = Engine.uniqueFillName(learnedFill.name == null || learnedFill.name.isEmpty() ? "fill" : learnedFill.name, this.learnedFills);
            copy.kind = learnedFill.kind == null || learnedFill.kind.isEmpty() ? "toms" : learnedFill.kind;
            copy.cells = Engine.copyCells(learnedFill.cells);
            copy.source = string2;
            this.learnedFills.add(0, copy);
            while (this.learnedFills.size() > 48) {
                this.learnedFills.remove(this.learnedFills.size() - 1);
            }
            arrayList.add(copy);
        }
        int n = 0;
        for (String[] stringArray2 : fileSet.fillerns) {
            String string4 = linkedHashMap.get(stringArray2[0]);
            if (string4 == null) continue;
            String string5 = Engine.resolveFsetFillKey(stringArray2[1], arrayList);
            this.fillernPairs.put("l:" + string4, string5);
            ++n;
        }
        if (string3 != null) {
            this.loadStyle(string3, false);
        } else if (!arrayList.isEmpty()) {
            this.applyFill("l:" + arrayList.get(0).id);
        }
        this.persistLearned();
        this.rebuildImported();
        this.rebuildImportedFills();
        this.show(n > 0 ? "combo" : (string3 != null ? "pattern" : "fills"));
        String string6 = "File set · " + string2 + " · " + fileSet.patterns.size() + " patterns · " + n + " Fillerns · " + fileSet.fills.size() + " fills";
        this.setNow(string6);
        Toast.makeText((Context)this, (CharSequence)string6, (int)0).show();
    }

    private void refreshImportedFilesBase() {
        if (this.importedFileList == null) {
            return;
        }
        this.importedFileList.removeAllViews();
        this.importedFileList.addView((View)this.sectionLabel("Imported files"));
        LinkedHashMap<String, int[]> linkedHashMap = new LinkedHashMap<String, int[]>();
        for (Engine.Learned object2 : this.learned) {
            String string = Engine.sourceOf(object2);
            int[] counts = linkedHashMap.get(string);
            if (counts == null) {
                counts = new int[]{0, 0};
                linkedHashMap.put(string, counts);
            }
            counts[0] = counts[0] + 1;
        }
        for (Engine.LearnedFill learnedFill : this.learnedFills) {
            String string = Engine.sourceOf(learnedFill);
            int[] counts = linkedHashMap.get(string);
            if (counts == null) {
                counts = new int[]{0, 0};
                linkedHashMap.put(string, counts);
            }
            counts[1] = counts[1] + 1;
        }
        if (linkedHashMap.isEmpty()) {
            TextView empty = this.text("No imported files yet.", 12, false);
            empty.setTextColor(MUTED);
            this.importedFileList.addView((View)empty);
            return;
        }
        for (Map.Entry<String, int[]> entry : linkedHashMap.entrySet()) {
            String string = entry.getKey();
            String label = string.isEmpty() ? "Other" : string;
            String string2 = string.isEmpty() ? "o:other" : "f:" + string;
            int n = entry.getValue()[0] + entry.getValue()[1];
            boolean bl = this.packOpen(string2, false);
            LinearLayout linearLayout = this.row();
            TextView textView2 = this.pill((bl ? "▾ " : "▸ ") + label + " · " + n, false, view -> {
                this.openPacks.put(string2, !this.packOpen(string2, false));
                this.refreshImportedFiles();
            });
            linearLayout.addView((View)textView2, (ViewGroup.LayoutParams)this.flex(1));
            TextView textView3 = this.outline("Export", false, view -> this.saveFset(string));
            linearLayout.addView((View)textView3);
            TextView textView4 = this.outline("Delete", false, view -> this.removeImportSource(string));
            linearLayout.addView((View)textView4);
            this.importedFileList.addView((View)linearLayout);
            if (!bl) continue;
            LinearLayout linearLayout2 = this.col();
            linearLayout2.setPadding(this.dp(8), 0, 0, this.dp(6));
            for (Engine.Learned learned : this.learned) {
                if (!string.equals(Engine.sourceOf(learned))) continue;
                TextView textView = this.text("Pattern · " + learned.name, 13, false);
                textView.setTextColor(MUTED);
                textView.setPadding(0, this.dp(4), 0, this.dp(4));
                textView.setOnClickListener(arg_0 -> this.refreshImportedFilesAction118(learned, arg_0));
                linearLayout2.addView((View)textView);
            }
            for (Engine.LearnedFill learnedFill : this.learnedFills) {
                if (!string.equals(Engine.sourceOf(learnedFill))) continue;
                TextView textView = this.text("Fill · " + learnedFill.name, 13, false);
                textView.setTextColor(MUTED);
                textView.setPadding(0, this.dp(4), 0, this.dp(4));
                textView.setOnClickListener(arg_0 -> this.refreshImportedFilesAction119(learnedFill, arg_0));
                linearLayout2.addView((View)textView);
            }
            this.importedFileList.addView((View)linearLayout2);
        }
    }

    private void addVariatedFillChip(Engine.LearnedFill learnedFill) {
        if (this.variatedFillBar == null) {
            return;
        }
        String string = "v:" + learnedFill.id;
        for (int i = 0; i < this.variatedFillBar.getChildCount(); ++i) {
            if (!string.equals(this.variatedFillBar.getChildAt(i).getTag())) continue;
            return;
        }
        TextView textView = this.pill(learnedFill.name, false, view -> this.applyFill(string));
        textView.setTag((Object)string);
        textView.setOnLongClickListener(view -> {
            new AlertDialog.Builder((Context)this).setTitle((CharSequence)learnedFill.name).setItems(new CharSequence[]{"Delete"}, (dialogInterface, n) -> {
                this.variatedFills.remove(learnedFill);
                this.variatedFillBar.removeView((View)textView);
                this.persistLearned();
                this.syncVariatedEmpty();
                this.setNow("Variation deleted");
            }).show();
            return true;
        });
        this.variatedFillBar.addView((View)textView);
        this.syncVariatedEmpty();
        this.refreshFills();
    }

    private void syncVariatedEmpty() {
        if (this.variatedFillBar == null) {
            return;
        }
        boolean bl = false;
        View view = null;
        for (int i = 0; i < this.variatedFillBar.getChildCount(); ++i) {
            View view2 = this.variatedFillBar.getChildAt(i);
            if ("imported-empty".equals(view2.getTag())) {
                view = view2;
                continue;
            }
            bl = true;
        }
        if (view != null) {
            view.setVisibility(bl ? 8 : 0);
        }
    }

    private void syncImportedFillEmpty() {
        this.rebuildImportedFills();
    }

    private String patternKeyFor(String string) {
        if (string == null) {
            return "s:house";
        }
        for (Engine.Learned learned : this.variatedPatterns) {
            if (!learned.id.equals(string)) continue;
            return "v:" + string;
        }
        for (Engine.Learned learned : this.learned) {
            if (!learned.id.equals(string)) continue;
            return "l:" + string;
        }
        return "s:" + string;
    }

    private boolean fillernUnder(String string) {
        if (!"combo".equals(this.view) || string == null || "imported-empty".equals(string) || "null".equals(string)) {
            return false;
        }
        return this.fillernUnderlined(this.patternKeyFor(string));
    }

    private boolean fillernUnderlined(String string) {
        if (string == null) {
            return false;
        }
        String string2 = this.fillernPairs.get(string);
        if (string2 != null && !string2.isEmpty()) {
            return true;
        }
        return string.equals(this.currentPatternKey());
    }

    private String fillernFillKeyOf(String string) {
        String string2 = this.fillernPairs.get(string);
        if (string2 != null && !string2.isEmpty()) {
            return string2;
        }
        if (string.equals(this.currentPatternKey())) {
            return this.fillId;
        }
        return "toms";
    }

    private String songPickSection(String string) {
        if (string != null && string.startsWith("v:")) {
            return "Variated";
        }
        if (string != null && (string.startsWith("l:") || string.startsWith("p:"))) {
            return "Imported";
        }
        return "Built-in";
    }

    private String selectedFillFor(String string) {
        String string2 = this.fillernPairs.get(string);
        if (string2 != null) {
            return string2;
        }
        if (string.equals(this.patternKeyFor(this.style))) {
            return this.fillId;
        }
        return null;
    }

    private void rememberFillern(String string, String string2) {
        if (string == null || string2 == null) {
            return;
        }
        this.fillernPairs.remove(string);
        this.fillernPairs.put(string, string2);
        this.persistLearned();
        this.refreshStyles();
    }

    private void applyStoredFillern(String string) {
        if (!"combo".equals(this.view) || string == null) {
            return;
        }
        String string2 = this.fillernPairs.get(string);
        if (string2 != null) {
            this.applyFill(string2);
        }
    }

    private void hideStyle(String string) {
        if (string == null) {
            return;
        }
        if (!this.hiddenStyles.contains(string)) {
            this.hiddenStyles.add(string);
        }
        if (this.styleBar != null) {
            for (int i = 0; i < this.styleBar.getChildCount(); ++i) {
                View view = this.styleBar.getChildAt(i);
                if (!string.equals(String.valueOf(view.getTag()))) continue;
                view.setVisibility(8);
            }
        }
        this.setNow("Hidden style");
    }

    private CharSequence fillernItem(String string, String string2, String string3) {
        String string4 = "  " + string2;
        if (string.equals(this.selectedFillFor(string3))) {
            String string5 = string4.replace("&", "&amp;").replace("<", "&lt;");
            return Html.fromHtml((String)("<u>" + string5 + "</u>"), (int)0);
        }
        return string4;
    }

    private void addFillernFillRows(List<CharSequence> list, List<Runnable> list2, String string, Runnable runnable) {
        if (!"combo".equals(this.view)) {
            return;
        }
        list.add("— Last-bar fill —");
        list2.add(null);
        list.add("— Built-in —");
        list2.add(null);
        for (int i = 0; i < Engine.FILL_ID.length; ++i) {
            String object3 = Engine.FILL_ID[i];
            if (this.hiddenFills.contains(object3)) continue;
            list.add(this.fillernItem(object3, Engine.FILL_LABEL[i], string));
            list2.add(() -> {
                runnable.run();
                this.applyFill(object3);
                this.rememberFillern(string, object3);
                this.show("combo");
            });
        }
        if (!this.variatedFills.isEmpty()) {
            list.add("— Variated —");
            list2.add(null);
            for (Engine.LearnedFill object4 : this.variatedFills) {
                String object2 = "v:" + object4.id;
                list.add(this.fillernItem(object2, object4.name, string));
                list2.add(() -> this.addFillernFillRowsAction124(runnable, object2, string));
            }
        }
        LinkedHashMap<String, ArrayList<Engine.LearnedFill>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.LearnedFill>>();
        for (Engine.LearnedFill learnedFill : this.learnedFills) {
            String src = Engine.sourceOf(learnedFill);
            if (src.isEmpty()) {
                src = "Other";
            }
            ArrayList<Engine.LearnedFill> group = linkedHashMap.get(src);
            if (group == null) {
                group = new ArrayList<Engine.LearnedFill>();
                linkedHashMap.put(src, group);
            }
            group.add(learnedFill);
        }
        for (Map.Entry<String, ArrayList<Engine.LearnedFill>> entry : linkedHashMap.entrySet()) {
            list.add("— " + entry.getKey() + " —");
            list2.add(null);
            for (Engine.LearnedFill object4 : entry.getValue()) {
                String string2 = "l:" + object4.id;
                list.add(this.fillernItem(string2, object4.name, string));
                list2.add(() -> {
                    runnable.run();
                    this.applyFill(string2);
                    this.rememberFillern(string, string2);
                    this.show("combo");
                });
            }
        }
    }

    private void showEntryMenu(String string, String string2, Runnable runnable, List<String> list, List<Runnable> list2) {
        ArrayList<CharSequence> arrayList = new ArrayList<CharSequence>(list);
        ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>(list2);
        this.addFillernFillRows(arrayList, arrayList2, string2, runnable);
        if (arrayList.isEmpty()) {
            return;
        }
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)string).setItems(arrayList.toArray(new CharSequence[0]), (dialogInterface, n) -> {
            if (n < 0 || n >= arrayList2.size()) {
                return;
            }
            Runnable picked = (Runnable)arrayList2.get(n);
            if (picked != null) {
                picked.run();
            }
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    private void attachLearnedStyleMenu(TextView textView, Engine.Learned learned) {
        textView.setOnLongClickListener(view -> {
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            arrayList.add("Duplicate");
            arrayList2.add(() -> this.replicateStyle(learned.id));
            arrayList.add("Rename");
            arrayList2.add(() -> this.promptRename(learned.name, string -> {
                learned.name = string;
                textView.setText((CharSequence)string);
                this.styles.put(learned.id, new Engine.Style(learned.id, string, learned.bpm, Engine.rowsFromCells(learned.cells)));
                this.persistLearned();
            }));
            arrayList.add("Delete");
            arrayList2.add(() -> {
                this.learned.remove(learned);
                this.persistLearned();
                this.rebuildImported();
                this.setNow("Style deleted");
            });
            this.showEntryMenu(learned.name, this.patternKeyFor(learned.id), () -> this.loadStyle(learned.id, false), arrayList, arrayList2);
            return true;
        });
    }

    private void attachBuiltinStyleMenu(TextView textView, String string) {
        textView.setOnLongClickListener(view -> {
            Engine.Style style = this.styles.get(string);
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            arrayList.add("Duplicate");
            arrayList2.add(() -> this.replicateStyle(string));
            arrayList.add("Hide");
            arrayList2.add(() -> this.hideStyle(string));
            this.showEntryMenu(style != null ? style.label : string, this.patternKeyFor(string), () -> this.loadStyle(string, false), arrayList, arrayList2);
            return true;
        });
    }

    private void attachLearnedFillMenu(TextView textView, Engine.LearnedFill learnedFill) {
        textView.setOnLongClickListener(view -> {
            new AlertDialog.Builder((Context)this).setTitle((CharSequence)learnedFill.name).setItems(new CharSequence[]{"Rename", "Delete"}, (dialogInterface, n) -> {
                if (n == 0) {
                    this.promptRename(learnedFill.name, string -> {
                        learnedFill.name = string;
                        textView.setText((CharSequence)string);
                        this.persistLearned();
                    });
                } else {
                    this.learnedFills.remove(learnedFill);
                    this.persistLearned();
                    this.rebuildImportedFills();
                    this.setNow("Fill deleted");
                }
            }).show();
            return true;
        });
    }

    private void promptRename(String string, NameFn nameFn) {
        EditText editText = new EditText((Context)this);
        editText.setText((CharSequence)string);
        editText.setSingleLine();
        editText.setSelectAllOnFocus(true);
        new AlertDialog.Builder((Context)this).setTitle((CharSequence)"Rename").setView((View)editText).setPositiveButton((CharSequence)"Save", (dialogInterface, n) -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                return;
            }
            if (name.length() > 28) {
                name = name.substring(0, 28);
            }
            nameFn.apply(name);
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    private File learnedFile() {
        return new File(this.getFilesDir(), "learned.json");
    }

    private File kitFile() {
        return new File(this.getFilesDir(), "kit.sf2");
    }

    private void persistLearnedBase() {
        try {
            String string = "{\"learned\":" + Engine.learnedJson(this.learned) + ",\"learnedFills\":" + Engine.learnedFillsJson(this.learnedFills) + ",\"variatedFills\":" + Engine.learnedFillsJson(this.variatedFills) + ",\"variatedPatterns\":" + Engine.learnedJson(this.variatedPatterns) + ",\"fillernPairs\":" + this.fillernPairJson() + ",\"importedSongs\":" + Engine.importedSongsJson(this.importedSongs) + "}";
            FileOutputStream fileOutputStream = new FileOutputStream(this.learnedFile());
            fileOutputStream.write(string.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private String fillernPairJson() {
        StringBuilder stringBuilder = new StringBuilder("[");
        int n = 0;
        for (Map.Entry<String, String> entry : this.fillernPairs.entrySet()) {
            if (n++ > 0) {
                stringBuilder.append(',');
            }
            stringBuilder.append(Engine.quote(entry.getKey() + "=" + entry.getValue()));
        }
        stringBuilder.append(']');
        return stringBuilder.toString();
    }

    private void loadFillernPairs(String string) {
        this.fillernPairs.clear();
        int n = string.indexOf("\"fillernPairs\"");
        if (n < 0) {
            return;
        }
        int n2 = string.indexOf(91, n);
        int n3 = string.indexOf(93, n2);
        if (n2 < 0 || n3 < 0) {
            return;
        }
        String string2 = string.substring(n2 + 1, n3).trim();
        if (string2.isEmpty()) {
            return;
        }
        Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(string2);
        while (matcher.find()) {
            String string3 = matcher.group(1).replace("\\\"", "\"");
            int n4 = string3.indexOf(61);
            if (n4 <= 0) continue;
            this.fillernPairs.put(string3.substring(0, n4), string3.substring(n4 + 1));
        }
    }

    private void persistSf2(byte[] byArray) {
        if (byArray == null || byArray.length < 16) {
            return;
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(this.kitFile());
            fileOutputStream.write(byArray);
            fileOutputStream.close();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void restoreSession() {
        int n;
        FileInputStream fileInputStream;
        byte[] byArray;
        File file;
        try {
            file = this.learnedFile();
            if (file.isFile() && file.length() > 8L) {
                byArray = new byte[(int)file.length()];
                fileInputStream = new FileInputStream(file);
                n = fileInputStream.read(byArray);
                fileInputStream.close();
                if (n > 0) {
                    this.learned.clear();
                    this.learned.addAll(Engine.parseLearnedJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.Learned object : this.learned) {
                        this.styles.put(object.id, new Engine.Style(object.id, object.name, object.bpm, Engine.rowsFromCells(object.cells)));
                        this.addLearnedChip(object);
                    }
                    this.learnedFills.clear();
                    this.learnedFills.addAll(Engine.parseLearnedFillsJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.LearnedFill learnedFill : this.learnedFills) {
                        this.addLearnedFillChip(learnedFill);
                    }
                    this.variatedFills.clear();
                    this.variatedFills.addAll(Engine.parseVariatedFillsJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.LearnedFill learnedFill : this.variatedFills) {
                        this.addVariatedFillChip(learnedFill);
                    }
                    this.variatedPatterns.clear();
                    this.variatedPatterns.addAll(Engine.parseVariatedPatternsJson(new String(byArray, 0, n, StandardCharsets.UTF_8)));
                    for (Engine.Learned learned : this.variatedPatterns) {
                        this.styles.put(learned.id, new Engine.Style(learned.id, learned.name, learned.bpm, Engine.rowsFromCells(learned.cells)));
                        this.addVariatedPatternChip(learned);
                    }
                    this.loadFillernPairs(new String(byArray, 0, n, StandardCharsets.UTF_8));
                    List<Engine.ImportedSong> list = Engine.decodeImportedSongs(new String(byArray, 0, n, StandardCharsets.UTF_8));
                    if (!list.isEmpty()) {
                        this.importedSongs.clear();
                        this.importedSongs.addAll((Collection<Engine.ImportedSong>)list);
                        this.importedSongId = ((Engine.ImportedSong)list.get((int)0)).id;
                    }
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        try {
            file = this.kitFile();
            if (file.isFile() && file.length() > 64L) {
                byArray = new byte[(int)file.length()];
                fileInputStream = new FileInputStream(file);
                n = fileInputStream.read(byArray);
                fileInputStream.close();
                if (n > 0) {
                    this.applySf2(AudioIo.parseSf2(n == byArray.length ? byArray : Arrays.copyOf(byArray, n)));
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private String jsonStr(String string, String string2) {
        int n = string.indexOf(string2);
        if (n < 0) {
            return null;
        }
        int n2 = string.indexOf(58, n);
        if (n2 < 0) {
            return null;
        }
        int n3 = string.indexOf(34, n2 + 1);
        if (n3 < 0) {
            return null;
        }
        int n4 = string.indexOf(34, n3 + 1);
        if (n4 < 0) {
            return null;
        }
        return string.substring(n3 + 1, n4);
    }

    private int jsonInt(String string, String string2, int n) {
        int n2;
        int n3;
        int n4 = string.indexOf(string2);
        if (n4 < 0) {
            return n;
        }
        int n5 = string.indexOf(58, n4);
        if (n5 < 0) {
            return n;
        }
        for (n3 = n5 + 1; n3 < string.length() && string.charAt(n3) == ' '; ++n3) {
        }
        for (n2 = n3; n2 < string.length() && "-0123456789".indexOf(string.charAt(n2)) >= 0; ++n2) {
        }
        try {
            return Integer.parseInt(string.substring(n3, n2));
        }
        catch (Exception exception) {
            return n;
        }
    }

    private double jsonDouble(String string, String string2, double d) {
        int n;
        int n2;
        int n3 = string.indexOf(string2);
        if (n3 < 0) {
            return d;
        }
        int n4 = string.indexOf(58, n3);
        if (n4 < 0) {
            return d;
        }
        for (n2 = n4 + 1; n2 < string.length() && string.charAt(n2) == ' '; ++n2) {
        }
        for (n = n2; n < string.length() && "-0123456789.eE".indexOf(string.charAt(n)) >= 0; ++n) {
        }
        try {
            return Double.parseDouble(string.substring(n2, n));
        }
        catch (Exception exception) {
            return d;
        }
    }

    private void copyPattern(String string, String string2, int[][] nArray) {
        int n = string.indexOf(string2);
        if (n < 0) {
            return;
        }
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            String string3 = "\"" + Engine.TRACK_ID[i] + "\"";
            int n2 = string.indexOf(string3, n);
            if (n2 < 0) continue;
            int n3 = string.indexOf(91, n2);
            int n4 = string.indexOf(93, n3);
            if (n3 < 0 || n4 < 0) continue;
            String[] stringArray = string.substring(n3 + 1, n4).split(",");
            for (int j = 0; j < 32 && j < stringArray.length; ++j) {
                try {
                    nArray[i][j] = Integer.parseInt(stringArray[j].trim());
                    continue;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
    }

    private String exportName(String string) {
        String string2 = this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : this.style;
        boolean bl = true;
        for (int i = 0; i < Engine.TRACK_ID.length && bl; ++i) {
            for (int j = 0; j < 16; ++j) {
                if (this.cells[i][j] <= 0) continue;
                bl = false;
            }
        }
        return AudioIo.fileName(bl ? "silent" : string2, this.bpm(), string, this.fillVariated);
    }

    private void openFile() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        this.startActivityForResult(intent, 7);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void onActivityResultBase(int n, int n2, Intent intent) {
        super.onActivityResult(n, n2, intent);
        if (n2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        Uri uri = intent.getData();
        try {
            Object object;
            byte[] byArray;
            if (n == 22) {
                this.analyzeUri = uri;
                this.analyzeBytes = this.readUri(uri);
                String string = uri.getLastPathSegment();
                this.analyzeName = string != null ? string : "song";
                if (this.analyzeFileLab != null) {
                    this.analyzeFileLab.setText((CharSequence)this.analyzeName);
                }
                if (this.analyzeStatus != null) {
                    this.analyzeStatus.setText((CharSequence)"Tap Process to split the track.");
                }
                this.show("analyze");
                return;
            }
            if (n == 21) {
                byte[] byArray2 = this.readUri(uri);
                if (this.isolateAnalysis == null || this.isolateAnalysis.pcm == null || this.isolateSampleTrack == null) {
                    return;
                }
                short[] sArray = this.decodeToShorts(uri, byArray2);
                float[] fArray = new float[sArray.length];
                for (int i = 0; i < sArray.length; ++i) {
                    fArray[i] = (float)sArray[i] / 32768.0f;
                }
                AudioIo.PadIso padIso = AudioIo.isolateFromSample(this.isolateAnalysis.pcm, this.isolateSampleTrack, fArray);
                for (int i = 0; i < this.isolateAnalysis.pads.size(); ++i) {
                    if (!this.isolateSampleTrack.equals(this.isolateAnalysis.pads.get((int)i).track)) continue;
                    this.isolateAnalysis.pads.set(i, padIso);
                    break;
                }
                AudioIo.continueIsolation(this.isolateAnalysis.pcm, this.isolateAnalysis.pads);
                this.rememberIsoPads();
                String string = padIso.fromSample ? padIso.name + " loaded from sample \u00b7 " + padIso.hz + " Hz." : (padIso.found ? padIso.name + " matched the sample at " + padIso.hz + " Hz." : "Could not match " + padIso.name + " from that sample.");
                this.isolateStatus.setText((CharSequence)(AudioIo.isolationStatus(this.isolateAnalysis.pads) + " " + string));
                Toast.makeText((Context)this, (CharSequence)string, (int)0).show();
                this.refreshIsolate();
                return;
            }
            if (n == 13) {
                byte[] byArray3 = this.readUri(uri);
                int n3 = this.padTarget;
                if (n3 < 0 || n3 >= this.voices.length) {
                    return;
                }
                short[] sArray = this.decodeToShorts(uri, byArray3);
                if (sArray.length > 44100) {
                    short[] trimmed = new short[44100];
                    System.arraycopy(sArray, 0, trimmed, 0, trimmed.length);
                    sArray = trimmed;
                }
                synchronized (this.mixLock) {
                    Engine.DrumSet drumSet = this.activeDrumSet();
                    if (drumSet.original) {
                        this.voices[n3] = sArray;
                    } else {
                        drumSet.samples[n3] = sArray;
                        drumSet.matchOrig[n3] = false;
                    }
                    this.mixPos[n3] = -1;
                }
                this.refreshPadLabels();
                Toast.makeText((Context)this, (CharSequence)(Engine.TRACK_LABEL[n3] + " sample loaded"), (int)0).show();
                return;
            }
            if (n == 7) {
                String string = uri.getLastPathSegment();
                if (string == null) {
                    string = uri.toString();
                }
                this.ingest(this.readUri(uri), string, uri);
                return;
            }
            if (n == 9) {
                byArray = Engine.encodeSng(this.songPartsForExport(), this.songPartsForExport().get((int)0).name);
            } else if (n == 14) {
                byArray = (this.pyEditor != null ? this.pyEditor.getText().toString() : "").getBytes(StandardCharsets.UTF_8);
            } else if (n == 15) {
                byArray = this.encodePrj();
            } else if (n == 16) {
                object = this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : "Pulsekit";
                String string = this.pyEditor != null ? this.pyEditor.getText().toString() : "";
                byArray = Engine.encodePkp("pulsekit." + this.style, (String)object, this.bpm(), this.style, this.cells, this.fillPat, this.pyName, string);
            } else if (n == 19) {
                Object object4 = object = this.fsetSaveSource != null ? this.fsetSaveSource : this.selectedFileSource();
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
                byArray = Engine.encodeSongMidi(this.songPartsForExport());
            } else if (n == 10) {
                byArray = this.encodeWav();
            } else if (n == 11) {
                byArray = AudioIo.encodeMp3(this.mixPcm(), 22050);
            } else if (n == 12) {
                this.fillMissingVoices();
                byArray = AudioIo.encodeSf2(this.voices, this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : "Pulsekit");
            } else if (n == 8) {
                object = Engine.cellsForMidiRole(this.cells, this.fillPat, this.midiSaveRole);
                int[][] nArray = Engine.cellsForMidiRole(this.lens, this.fillLens, this.midiSaveRole);
                byArray = Engine.encodeMidi((int[][])object, nArray, this.bpm(), Engine.usedSteps((int[][])object), this.tsNum, this.tsDen);
            } else {
                byArray = "song".equals(this.view) && !this.activeSong().isEmpty() ? Engine.encodeSongMidi(this.activeSong()) : Engine.encodeMidi(this.cells, this.lens, this.bpm(), this.steps, this.tsNum, this.tsDen);
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

    private byte[] readUri(Uri uri) throws Exception {
        int n;
        InputStream inputStream = this.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] byArray = new byte[4096];
        while ((n = inputStream.read(byArray)) > 0) {
            byteArrayOutputStream.write(byArray, 0, n);
        }
        inputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    private void ingestBase(byte[] byArray, String string, Uri uri) throws Exception {
        int n;
        int[][] object;
        String string2 = AudioIo.sniff(byArray);
        String string3 = string.toLowerCase();
        if ("unknown".equals(string2)) {
            if (string3.contains(".sng")) {
                string2 = "sng";
            } else if (string3.contains(".sf2")) {
                string2 = "sf2";
            } else if (string3.contains(".wav")) {
                string2 = "wav";
            } else if (string3.contains(".mp3")) {
                string2 = "mp3";
            } else if (string3.contains(".mid")) {
                string2 = "midi";
            } else if (string3.contains(".py")) {
                string2 = "py";
            } else if (string3.contains(".prj") || Engine.isPrj(byArray)) {
                string2 = "prj";
            } else if (string3.contains(".pkp") || Engine.isPlugin(byArray)) {
                string2 = "pkp";
            } else if (string3.contains(".fset") || Engine.isFset(byArray)) {
                string2 = "fset";
            }
        }
        if ("prj".equals(string2) || Engine.isPrj(byArray)) {
            this.loadPrj(byArray);
            return;
        }
        if ("pkp".equals(string2) || Engine.isPlugin(byArray)) {
            this.loadPlugin(byArray);
            return;
        }
        if ("fset".equals(string2) || Engine.isFset(byArray)) {
            this.loadFset(byArray, string);
            return;
        }
        if ("py".equals(string2)) {
            String string4 = string;
            int n2 = Math.max(string4.lastIndexOf(47), string4.lastIndexOf(58));
            String string5 = this.pyName = n2 >= 0 ? string4.substring(n2 + 1) : string4;
            if (!this.pyName.toLowerCase().endsWith(".py")) {
                this.pyName = "script.py";
            }
            if (this.pyEditor != null) {
                this.pyEditor.setText((CharSequence)new String(byArray, StandardCharsets.UTF_8));
            }
            this.show("py");
            Toast.makeText((Context)this, (CharSequence)("Python \u00b7 " + this.pyName), (int)0).show();
            return;
        }
        if ("sng".equals(string2)) {
            List<Engine.Part> list = Engine.decodeSng(byArray);
            if (list.isEmpty()) {
                Toast.makeText((Context)this, (CharSequence)"Could not read that .sng", (int)0).show();
                return;
            }
            this.song.clear();
            this.song.addAll(list);
            this.songLane = "original";
            this.show("song");
            this.applyPart(list.get(0));
            Toast.makeText((Context)this, (CharSequence)(list.size() + " parts loaded"), (int)0).show();
            return;
        }
        if ("wav".equals(string2) || "mp3".equals(string2)) {
            AudioIo.Pcm pcm;
            if ("wav".equals(string2)) {
                pcm = AudioIo.parseWav(byArray);
            } else {
                short[] shorts = this.decodeToShorts(uri, byArray);
                float[] fArray = new float[shorts.length];
                for (int i = 0; i < shorts.length; ++i) {
                    fArray[i] = (float)shorts[i] / 32768.0f;
                }
                pcm = new AudioIo.Pcm(fArray, 22050);
            }
            AudioIo.Analysis object2 = AudioIo.analyze(pcm.samples, pcm.sr);
            if (object2.isolated) {
                this.applyIsoMemory((AudioIo.Analysis)object2, string);
                this.isolateAnalysis = object2;
                this.isolateFile = string;
                this.refreshIsolate();
                this.show("isolate");
                Toast.makeText((Context)this, (CharSequence)"Kick and snare found", (int)0).show();
                return;
            }
            this.applyAnalysis((AudioIo.Analysis)object2, string);
            return;
        }
        if ("sf2".equals(string2)) {
            if ((long)byArray.length > 0x3000000L) {
                Toast.makeText((Context)this, (CharSequence)"SoundFont is too large", (int)0).show();
                return;
            }
            this.setNow("Loading SoundFont\u2026");
            Toast.makeText((Context)this, (CharSequence)"Loading SoundFont\u2026", (int)0).show();
            new Thread(() -> {
                try {
                    short[][] sArray = AudioIo.parseSf2(byArray);
                    this.handler.post(() -> {
                        this.applySf2(sArray);
                        this.persistSf2(byArray);
                    });
                }
                catch (Exception exception) {
                    this.handler.post(() -> {
                        this.setNow(null);
                        String msg = exception.getMessage() != null ? exception.getMessage() : "Could not read SoundFont";
                        Toast.makeText((Context)this, (CharSequence)msg, (int)1).show();
                    });
                }
            }, "pulsekit-sf2").start();
            return;
        }
        String string6 = Engine.midiRoleFromFile(string);
        Engine.MidiBars songBars;
        if (string6 == null && (songBars = Engine.parseMidiBars(byArray)) != null && this.learnFromSongImport(string, songBars)) {
            return;
        }
        object = Engine.parseMidi(byArray);
        if (Engine.hitCount((int[][])object) < 1) {
            Toast.makeText((Context)this, (CharSequence)"No drum track in that MIDI", (int)0).show();
            return;
        }
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            System.arraycopy(object[n], 0, this.cells[n], 0, 32);
        }
        n = Engine.parseMidiBpm(byArray, this.bpm());
        if (this.bpmBar != null) {
            this.bpmBar.setVal(Engine.clampBpm(n));
        }
        this.bpmLabel.setText((CharSequence)Integer.toString(this.bpm()));
        Engine.MidiBars midiBars = Engine.parseMidiBars(byArray);
        int n3 = midiBars != null ? midiBars.tsNum : 4;
        int n4 = midiBars != null ? midiBars.tsDen : 4;
        this.tsNum = Engine.clampTsNum(n3);
        this.tsDen = Engine.clampTsDen(n4);
        if (this.tsNumField != null) {
            this.tsNumField.setText((CharSequence)Integer.toString(this.tsNum));
        }
        if (this.tsDenField != null) {
            this.tsDenField.setText((CharSequence)Integer.toString(this.tsDen));
        }
        this.applySteps(Engine.patternLenFromCells((int[][])object, this.tsNum, this.tsDen), false);
        Engine.defaultAccents(this.accents, this.steps, Engine.stepsPerBeat(this.tsDen));
        this.show("pattern");
        this.refreshGrid();
        this.learnFromImport(string, (int[][])object, n);
        CharSequence charSequence = this.now != null ? this.now.getText() : null;
        Toast.makeText((Context)this, (CharSequence)(charSequence != null && charSequence.length() > 0 ? charSequence : "MIDI loaded"), (int)0).show();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void applySf2(short[][] sArray) {
        this.ensureAudio();
        int n = 0;
        Object object = this.mixLock;
        synchronized (object) {
            for (int i = 0; i < this.voices.length; ++i) {
                if (sArray[i] == null) continue;
                this.voices[i] = sArray[i];
                this.mixPos[i] = -1;
                ++n;
            }
        }
        this.fillMissingVoices();
        this.setNow("SoundFont \u00b7 " + n + " drums");
        Toast.makeText((Context)this, (CharSequence)("SoundFont \u00b7 " + n + " drums"), (int)0).show();
    }

    private short[] decodeToShorts(Uri uri, byte[] byArray) throws Exception {
        if (uri != null) {
            try {
                return this.decodeWithExtractor(uri);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (AudioIo.sniff(byArray).equals("wav")) {
            return AudioIo.floatsToShorts(AudioIo.parseWav((byte[])byArray).samples);
        }
        throw new IllegalArgumentException("Could not decode that audio file");
    }

    private short[] decodeWithExtractor(Uri uri) throws Exception {
        int n;
        MediaExtractor mediaExtractor = new MediaExtractor();
        ParcelFileDescriptor parcelFileDescriptor = this.getContentResolver().openFileDescriptor(uri, "r");
        mediaExtractor.setDataSource(parcelFileDescriptor.getFileDescriptor());
        int n2 = 0;
        MediaFormat mediaFormat = null;
        for (int i = 0; i < mediaExtractor.getTrackCount(); ++i) {
            MediaFormat mediaFormat2 = mediaExtractor.getTrackFormat(i);
            String string = mediaFormat2.getString("mime");
            if (string == null || !string.startsWith("audio/")) continue;
            n2 = i;
            mediaFormat = mediaFormat2;
            break;
        }
        if (mediaFormat == null) {
            mediaExtractor.release();
            parcelFileDescriptor.close();
            throw new IllegalArgumentException("No audio track");
        }
        mediaExtractor.selectTrack(n2);
        String string = mediaFormat.getString("mime");
        int n3 = mediaFormat.containsKey("sample-rate") ? mediaFormat.getInteger("sample-rate") : 44100;
        int n4 = mediaFormat.containsKey("channel-count") ? mediaFormat.getInteger("channel-count") : 1;
        MediaCodec mediaCodec = MediaCodec.createDecoderByType((String)string);
        mediaCodec.configure(mediaFormat, null, null, 0);
        mediaCodec.start();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean bl = false;
        boolean bl2 = false;
        int spins = 0;
        while (!bl2) {
            ByteBuffer byteBuffer;
            int n5;
            if (!bl && (n5 = mediaCodec.dequeueInputBuffer(10000L)) >= 0) {
                byteBuffer = mediaCodec.getInputBuffer(n5);
                int n6 = byteBuffer == null ? -1 : mediaExtractor.readSampleData(byteBuffer, 0);
                if (n6 < 0) {
                    mediaCodec.queueInputBuffer(n5, 0, 0, 0L, 4);
                    bl = true;
                } else {
                    mediaCodec.queueInputBuffer(n5, 0, n6, mediaExtractor.getSampleTime(), 0);
                    mediaExtractor.advance();
                }
            }
            if ((n5 = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000L)) < 0) {
                if (bl && ++spins > 80) break;
                continue;
            }
            spins = 0;
            byteBuffer = mediaCodec.getOutputBuffer(n5);
            int size = bufferInfo.size;
            int off = bufferInfo.offset;
            if (byteBuffer != null && size > 0 && off >= 0 && off + size <= byteBuffer.capacity()) {
                byteBuffer.position(off);
                byteBuffer.limit(off + size);
                byte[] byArray = new byte[size];
                byteBuffer.get(byArray);
                byteArrayOutputStream.write(byArray);
            }
            mediaCodec.releaseOutputBuffer(n5, false);
            if ((bufferInfo.flags & 4) == 0) continue;
            bl2 = true;
        }
        mediaCodec.stop();
        mediaCodec.release();
        mediaExtractor.release();
        parcelFileDescriptor.close();
        byte[] byArray = byteArrayOutputStream.toByteArray();
        int n7 = byArray.length / 2 / Math.max(1, n4);
        short[] sArray = new short[n7];
        for (int i = 0; i < n7; ++i) {
            int n8 = 0;
            for (n = 0; n < n4; ++n) {
                int n9 = (i * n4 + n) * 2;
                n8 += (short)(byArray[n9] & 0xFF | byArray[n9 + 1] << 8);
            }
            sArray[i] = (short)(n8 / n4);
        }
        if (n3 != 22050) {
            double d = (double)n3 / 22050.0;
            n = Math.max(1, (int)Math.floor((double)sArray.length / d));
            short[] sArray2 = new short[n];
            for (int i = 0; i < n; ++i) {
                sArray2[i] = sArray[Math.min(sArray.length - 1, (int)Math.floor((double)i * d))];
            }
            return sArray2;
        }
        return sArray;
    }

    protected void onDestroy() {
        this.hideLenMenu();
        this.stop();
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

    private LinearLayout col() {
        LinearLayout linearLayout = new LinearLayout((Context)this);
        linearLayout.setOrientation(1);
        return linearLayout;
    }

    private HorizontalScrollView chipStrip(View view) {
        HorizontalScrollView horizontalScrollView = new HorizontalScrollView((Context)this);
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        horizontalScrollView.setOverScrollMode(1);
        view.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-2, -2));
        horizontalScrollView.addView(view);
        horizontalScrollView.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(-1, -2));
        horizontalScrollView.setFillViewport(false);
        return horizontalScrollView;
    }

    private LinearLayout row() {
        LinearLayout linearLayout = new LinearLayout((Context)this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        return linearLayout;
    }

    private TextView hint(String string) {
        TextView textView = this.text(string, 13, false);
        textView.setTextColor(MUTED);
        textView.setPadding(0, this.dp(10), 0, this.dp(6));
        return textView;
    }

    private TextView sectionLabel(String string) {
        TextView textView = this.text(string, 11, true);
        textView.setTextColor(SUBTLE);
        textView.setPadding(0, this.dp(6), 0, this.dp(2));
        if (Build.VERSION.SDK_INT >= 21) {
            textView.setLetterSpacing(0.12f);
        }
        return textView;
    }

    private TextView text(String string, int n, boolean bl) {
        TextView textView = new TextView((Context)this);
        textView.setText((CharSequence)string);
        textView.setTextColor(FG);
        textView.setTextSize(2, (float)n);
        textView.setTypeface(Typeface.SANS_SERIF, bl ? 1 : 0);
        textView.setIncludeFontPadding(false);
        return textView;
    }

    private TextView pill(String string, boolean bl, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 13, true);
        textView.setGravity(17);
        textView.setPadding(this.dp(12), this.dp(7), this.dp(12), this.dp(7));
        this.paintChip(textView, bl);
        textView.setOnClickListener(onClickListener);
        LinearLayout.LayoutParams layoutParams = this.wrap();
        layoutParams.setMargins(this.dp(3), this.dp(4), this.dp(3), this.dp(4));
        textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        return textView;
    }

    private TextView outline(String string, boolean bl, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 12, true);
        textView.setGravity(17);
        textView.setPadding(this.dp(12), this.dp(6), this.dp(12), this.dp(6));
        this.paintOutline(textView, bl);
        textView.setOnClickListener(onClickListener);
        LinearLayout.LayoutParams layoutParams = this.wrap();
        layoutParams.setMargins(this.dp(3), this.dp(4), this.dp(3), this.dp(4));
        textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        return textView;
    }

    private TextView cell(String string, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 9, true);
        textView.setGravity(17);
        textView.setBackground((Drawable)this.round(ELEV, 6));
        textView.setTextColor(SUBTLE);
        textView.setMinHeight(0);
        textView.setMinimumHeight(0);
        textView.setIncludeFontPadding(false);
        textView.setOnClickListener(onClickListener);
        return textView;
    }

    private TextView labelCell(String string, int n) {
        TextView textView = this.text(string, 11, true);
        textView.setTextColor(n);
        textView.setWidth(this.dp(40));
        textView.setTextSize(2, 14.0f);
        textView.setGravity(16);
        textView.setPadding(0, this.dp(4), this.dp(4), this.dp(4));
        return textView;
    }

    private TextView pad(String string, String string2, View.OnClickListener onClickListener) {
        TextView textView = this.text(string + "\n" + string2, 12, true);
        textView.setGravity(17);
        textView.setBackground((Drawable)this.round(ELEV, 12));
        textView.setTextColor(FG);
        textView.setOnClickListener(onClickListener);
        return textView;
    }

    private TextView action(String string, int n, int n2, View.OnClickListener onClickListener) {
        TextView textView = this.text(string, 15, true);
        textView.setGravity(17);
        textView.setBackground((Drawable)this.round(n, 12));
        textView.setTextColor(n2);
        textView.setOnClickListener(onClickListener);
        textView.setMinHeight(this.dp(44));
        return textView;
    }

    private GradientDrawable round(int n, int n2) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(n);
        gradientDrawable.setCornerRadius((float)this.dp(n2));
        return gradientDrawable;
    }

    private LinearLayout.LayoutParams accLp() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(this.dp(26), this.dp(40));
        layoutParams.setMargins(this.dp(1), this.dp(2), this.dp(1), this.dp(2));
        return layoutParams;
    }

    private LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(this.dp(26), this.dp(40));
        layoutParams.setMargins(this.dp(1), this.dp(2), this.dp(1), this.dp(2));
        return layoutParams;
    }

    private LinearLayout.LayoutParams flex(int n) {
        return new LinearLayout.LayoutParams(0, -2, (float)n);
    }

    private LinearLayout.LayoutParams flexFill() {
        return new LinearLayout.LayoutParams(-1, 0, 1.0f);
    }

    private LinearLayout.LayoutParams flexBtn() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, this.dp(44), 1.0f);
        layoutParams.setMargins(this.dp(4), 0, this.dp(4), 0);
        return layoutParams;
    }

    private LinearLayout.LayoutParams square() {
        return new LinearLayout.LayoutParams(this.dp(48), this.dp(48));
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private void setNow(String string) {
        if (this.now == null) {
            return;
        }
        boolean bl = string != null && !string.trim().isEmpty();
        this.now.setText((CharSequence)(bl ? string : " "));
        this.now.setVisibility(bl ? 0 : 8);
    }

    private Engine.DrumSet activeDrumSet() {
        if (this.drumSets.isEmpty()) {
            this.drumSets.add(Engine.DrumSet.originalSet());
        }
        if (this.drumSetIndex < 0 || this.drumSetIndex >= this.drumSets.size()) {
            this.drumSetIndex = 0;
        }
        return this.drumSets.get(this.drumSetIndex);
    }

    private short[] voiceAtLocked(int n) {
        int n2 = Engine.soundTrack(n);
        Engine.DrumSet drumSet = this.activeDrumSet();
        if (drumSet.original || drumSet.matchOrig[n2]) {
            return this.voices[n2];
        }
        return n2 >= 0 && n2 < drumSet.samples.length ? drumSet.samples[n2] : null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private short[][] mixVoices() {
        this.fillMissingVoices();
        Engine.DrumSet drumSet = this.activeDrumSet();
        short[][] sArrayArray = new short[this.voices.length][];
        Object object = this.mixLock;
        synchronized (object) {
            for (int i = 0; i < Engine.TRACK_ID.length && i < sArrayArray.length; ++i) {
                int n = Engine.soundTrack(i);
                sArrayArray[i] = drumSet.original || drumSet.matchOrig[n] ? this.voices[n] : drumSet.samples[n];
            }
        }
        return sArrayArray;
    }

    private void refreshDrumSets() {
        if (this.drumSetBar == null) {
            return;
        }
        this.drumSetBar.removeAllViews();
        for (int i = 0; i < this.drumSets.size(); ++i) {
            int n = i;
            Engine.DrumSet drumSet = this.drumSets.get(i);
            TextView textView = this.pill(drumSet.name, n == this.drumSetIndex, view -> {
                this.drumSetIndex = n;
                this.refreshDrumSets();
                this.refreshPadLabels();
                this.setNow(drumSet.original ? "Original kit" : drumSet.name + " \u00b7 " + drumSet.foundCount() + " pads");
            });
            if (!drumSet.original) {
                textView.setOnLongClickListener(view -> {
                    new AlertDialog.Builder((Context)this).setItems(new CharSequence[]{"Match Original", "Match empty from Original", "Delete set"}, (dialogInterface, n2) -> {
                        if (n2 == 0) {
                            this.drumSetIndex = n;
                            this.matchWholeOriginal();
                        } else if (n2 == 1) {
                            this.drumSetIndex = n;
                            for (int t = 0; t < Engine.TRACK_ID.length; ++t) {
                                if (drumSet.samples[t] != null) continue;
                                drumSet.matchOrig[t] = true;
                            }
                            this.refreshDrumSets();
                            this.refreshPadLabels();
                            Toast.makeText((Context)this, (CharSequence)"Empty pads match Original", (int)0).show();
                        } else if (n > 0 && n < this.drumSets.size()) {
                            this.drumSets.remove(n);
                            if (this.drumSetIndex >= this.drumSets.size()) {
                                this.drumSetIndex = 0;
                            }
                            this.refreshDrumSets();
                            this.refreshPadLabels();
                            Toast.makeText((Context)this, (CharSequence)"Drum set removed", (int)0).show();
                        }
                    }).show();
                    return true;
                });
            }
            this.drumSetBar.addView((View)textView);
        }
        this.drumSetBar.requestLayout();
        this.refreshPadLabels();
        Engine.DrumSet drumSet = this.activeDrumSet();
        if (this.padMatchAll != null) {
            int n = !drumSet.original ? 1 : 0;
            this.padMatchAll.setVisibility(n != 0 ? 0 : 8);
            if (n != 0) {
                boolean bl = true;
                for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                    if (drumSet.matchOrig[i]) continue;
                    bl = false;
                    break;
                }
                this.padMatchAll.setText((CharSequence)(bl ? "This set" : "Match Original"));
                this.paintOutline(this.padMatchAll, !bl);
            }
        }
    }

    private void matchWholeOriginal() {
        int n;
        Engine.DrumSet drumSet = this.activeDrumSet();
        if (drumSet.original) {
            return;
        }
        boolean bl = true;
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            if (drumSet.matchOrig[n]) continue;
            bl = false;
            break;
        }
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            drumSet.matchOrig[n] = !bl;
        }
        this.refreshDrumSets();
        this.refreshPadLabels();
        Toast.makeText((Context)this, (CharSequence)(!bl ? "Whole set matches Original" : "Using this set's sounds"), (int)0).show();
    }

    private void refreshPadLabels() {
        Engine.DrumSet drumSet = this.activeDrumSet();
        for (int i = 0; i < this.padBtns.length; ++i) {
            if (this.padBtns[i] == null) continue;
            String string = Engine.TRACK_LABEL[i];
            if (!drumSet.original) {
                string = drumSet.matchOrig[i] ? "Original" : (drumSet.samples[i] != null ? "From song" : "Empty");
            }
            this.padBtns[i].setText((CharSequence)(Engine.TRACK_SHORT[i] + "\n" + string));
        }
    }

    private void showPadMenu(int n) {
        Engine.DrumSet drumSet = this.activeDrumSet();
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("Load WAV / MP3");
        if (!drumSet.original) {
            arrayList.add(drumSet.matchOrig[n] ? "Use this set's sound" : "Match Original");
            if (drumSet.samples[n] != null) {
                arrayList.add("Remove sample");
            }
        }
        CharSequence[] charSequenceArray = arrayList.toArray(new String[0]);
        new AlertDialog.Builder((Context)this).setItems(charSequenceArray, (arg_0, arg_1) -> this.showPadMenuAction147((String[])charSequenceArray, n, drumSet, arg_0, arg_1)).show();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    /**
     * Adds a drum set made from the pads isolated out of a song. Pads the song
     * did not yield play the Original kit's sound instead of silence. The set
     * becomes the active kit only when the user chose "Use pads"; "Skip" adds
     * nothing.
     */
    private void addIsolatedSet(String string, List<AudioIo.PadIso> list) {
        if (this.isoPadMode == ISO_PADS_SKIP) {
            return;
        }
        Engine.DrumSet drumSet = Engine.DrumSet.isolated("s" + Integer.toHexString((int)(Math.random() * 1.0E9)), Engine.uniqueSetName(string, this.drumSets));
        int n = 0;
        Object object = this.mixLock;
        synchronized (object) {
            for (AudioIo.PadIso padIso : list) {
                int n2 = AudioIo.voiceIndex(padIso.track);
                if (n2 < 0 || !padIso.found || padIso.sample == null) continue;
                drumSet.samples[n2] = padIso.sample;
                ++n;
            }
        }
        if (n == 0) {
            return;
        }
        for (int t = 0; t < drumSet.samples.length; ++t) {
            if (drumSet.samples[t] == null) {
                drumSet.matchOrig[t] = true;
            }
        }
        Engine.DrumSet active = this.activeDrumSet();
        this.drumSets.add(drumSet);
        while (this.drumSets.size() > 9) {
            this.drumSets.remove(1);
        }
        boolean select = this.isoPadMode == ISO_PADS_USE;
        int keep = this.drumSets.indexOf(active);
        this.drumSetIndex = select || keep < 0 ? this.drumSets.size() - 1 : keep;
        this.refreshDrumSets();
        this.refreshPadLabels();
        Toast.makeText((Context)this, (CharSequence)("Drum set \u00b7 " + drumSet.name + " \u00b7 " + n + " pads" + (select ? "" : " \u00b7 pick it under Pads")), (int)0).show();
        if (select) {
            this.show("pads");
        }
    }

    private byte[] encodeWav() {
        return AudioIo.encodeWav(this.mixPcm(), 22050);
    }

    private short[] mixPcm() {
        this.ensureAudio();
        this.fillMissingVoices();
        return AudioIo.mix(this.cells, this.fillPat, this.fillLast, this.mutes, this.mixVoices(), this.bpm(), this.bars, 22050);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void fillMissingVoices() {
        short[][] sArray = AudioIo.buildVoices(22050);
        Object object = this.mixLock;
        synchronized (object) {
            for (int i = 0; i < this.voices.length; ++i) {
                if (this.voices[i] != null) continue;
                this.voices[i] = sArray[i];
            }
        }
    }

    private int dp(int n) {
        return Math.round((float)n * this.getResources().getDisplayMetrics().density);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private /* synthetic */ void showPadMenuAction147(String[] stringArray, int n, Engine.DrumSet drumSet, DialogInterface dialogInterface, int n2) {
        String string = stringArray[n2];
        if ("Load WAV / MP3".equals(string)) {
            this.padTarget = n;
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("audio/*");
            this.startActivityForResult(intent, 13);
        } else if ("Match Original".equals(string)) {
            drumSet.matchOrig[n] = true;
            this.refreshPadLabels();
            Toast.makeText((Context)this, (CharSequence)(Engine.TRACK_LABEL[n] + " \u00b7 Original"), (int)0).show();
        } else if ("Use this set's sound".equals(string)) {
            drumSet.matchOrig[n] = false;
            this.refreshPadLabels();
            Toast.makeText((Context)this, (CharSequence)(Engine.TRACK_LABEL[n] + " \u00b7 this set"), (int)0).show();
        } else if ("Remove sample".equals(string)) {
            Object object = this.mixLock;
            synchronized (object) {
                drumSet.samples[n] = null;
            }
            this.refreshPadLabels();
        }
    }

    private /* synthetic */ void addFillernFillRowsAction124(Runnable runnable, String string, String string2) {
        runnable.run();
        this.applyFill(string);
        this.rememberFillern(string2, string);
        this.show("combo");
    }

    private /* synthetic */ void refreshImportedFilesAction119(Engine.LearnedFill learnedFill, View view) {
        this.applyFill("l:" + learnedFill.id);
        this.show("fills");
    }

    private /* synthetic */ void refreshImportedFilesAction118(Engine.Learned learned, View view) {
        this.loadStyle(learned.id, false);
        this.show("pattern");
    }

    private /* synthetic */ void learnFromSongImportAction100(String string, List list, DialogInterface dialogInterface, int n) {
        this.addImportedArrangement(string, list);
    }

    private /* synthetic */ void audioTickAction94Base(int n, Engine.Part part, boolean bl) {
        if (!this.playing) {
            return;
        }
        this.playhead = n;
        this.refreshPlayhead();
        if (part != null && bl) {
            Engine.Part part2;
            List<Engine.Part> list = this.activeSong();
            Engine.Part part3 = part2 = list.isEmpty() ? null : list.get(Math.min(this.songIndex, list.size() - 1));
            if (part2 != null) {
                this.applyPart(part2);
                this.setNow(part2.name + "  " + part2.bpm + " BPM");
            }
            this.refreshSong();
        } else if (part != null && n == 0) {
            this.setNow(part.name + "  " + part.bpm + " BPM");
        }
    }

    private /* synthetic */ void refreshSongAction75(Engine.ImportedSong importedSong, View view) {
        this.importedSongId = importedSong.id;
        this.songLane = "imported";
        this.refreshSong();
    }

    private /* synthetic */ void showSongPickAction65(String string, int n) {
        this.applySongPick("pattern", string, n);
    }

    private static interface IntFn {
        public void apply(int var1);
    }

    private final class RangeBar
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

    private static interface NameFn {
        public void apply(String var1);
    }

    // ------------------------------------------------------------------
    // Features that used to be injected with Javassist (android/patch).
    // Analyze, Compose, Prompts, file-set Info, stem/MIDI playback, PyJav.
    // ------------------------------------------------------------------

    private android.media.MediaPlayer pkMidiPlayer;
    private java.lang.String pkMidiSrc;
    private boolean pkMidiPaused;
    private pulsekit.AudioIo.PartAnalysis analyzeResult;
    private boolean analyzePacked;
    private boolean pkAudioBusy;
    private java.lang.String pkAnErr;
    private pulsekit.AudioIo.PartAnalysis pkAnPending;
    private byte[] pkIsoBytes;
    private android.net.Uri pkIsoUri;
    private java.lang.String pkIsoName;
    private java.lang.String pkIsoErr;
    private pulsekit.AudioIo.Analysis pkIsoPending;
    private android.widget.LinearLayout composePane;
    private android.widget.LinearLayout composeRows;
    private android.widget.TextView composeStatus;
    private android.widget.TextView composeFileLab;
    private byte[] composeBytes;
    private java.lang.String composeName;
    private android.net.Uri composeUri;
    private pulsekit.AudioIo.PartAnalysis composeResult;
    private boolean composePacked;
    private byte[] composeSourceWav;
    private boolean pkSkipGuitarFit;
    private android.widget.CheckBox composeRemoveVocals;
    private boolean composeVocalsApplied;
    private android.widget.LinearLayout promptsPane;
    private android.widget.LinearLayout infoPane;
    private android.widget.LinearLayout infoRows;
    private android.widget.TextView infoTitle;
    private android.widget.TextView infoStatus;
    private java.util.LinkedHashMap fsetInfoMap;
    private android.widget.LinearLayout pkPackHost;
    private java.lang.String pkPackKey;
    private java.lang.String pkPackLabel;
    private java.lang.Runnable pkPackDel;
    private java.lang.Runnable pkPackExp;
    private byte[] pkFsetBytes;
    private java.lang.String pkFsetName;
    private java.lang.String pkRemovedSource;
    private java.lang.String pkInfoBack;
    private android.media.MediaPlayer pkMixPlayer;
    private android.media.MediaPlayer pkDrumPlayer;
    private android.media.MediaPlayer pkGuitarPlayer;
    private short[] pkDrumPcm;
    private short[] pkGuitarPcm;
    private java.lang.String pkStemSrc;
    private java.lang.Runnable pkMixRun;
    private int pkDrumLevel = 100;
    private int pkGuitarLevel = 100;
    private int pkMain = 100;
    private int pkBakeDrum = 100;
    private int pkBakeGuitar = 100;
    private int pkBakeMain = 100;
    private int pkGate = 20;
    private int pkComp = 50;
    private int pkLimit = 25;
    private boolean pkComposeBusy;
    private boolean pkComposeStrip;
    private java.lang.String pkComposeErr;
    private byte[] pkComposeWav;
    private pulsekit.AudioIo.PartAnalysis pkComposePending;
    private byte[] pkCombinedExport;
    private java.lang.String pkCombinedName;
    private boolean pkPartNow;
    private boolean pkPyWired;
    private boolean pkPyRecentMute;
    private android.widget.TextView pkPyLog;
    private android.widget.TextView pkPyHint;
    private android.widget.EditText pkPyArgs;
    private android.widget.TextView pkParamsBtn;
    private android.widget.TextView pkPyInput;
    private String pkPyInputToken;
    private String pkPyInputPath;
    private String pkPyOutputPath;
    private android.widget.Spinner pkPyRecent;
    private java.util.List pkPyRecentItems;
    private byte[] pkPyBytes;
    private String pkPromptRun;
    private String pkPromptCategory;
    private String pkPromptReport;
    private String pkPromptDescription;
    private String pkPromptSource;
    private String pkRef1Path;
    private String pkRef2Path;
    private String pkDefinedResult;
    private String pkOutputName;
    private boolean pkOutputInvented;
    private boolean pkOutputSaved;
    private boolean pkOutputResume;
    private android.widget.LinearLayout pkPromptModes;
    private android.widget.TextView pkRunBash;
    private android.widget.TextView pkRunCmd;
    private android.widget.TextView pkRunAi;

    private void loadFset(byte[] byArray, String string) throws Exception {
        this.pkFsetBytes = byArray;
        java.lang.String src = pulsekit.Engine.importSource(string);
        if (src == null || src.length() == 0 || "Import".equals(src)) src = string;
        java.lang.String unique = pulsekit.Engine.uniqueImportSource(src, this.learned, this.learnedFills);
        if (unique != null && src != null && !unique.equals(src)) string = unique + ".fset";
        this.pkFsetName = string;
        this.loadFsetBase(byArray, string);
        this.afterLoadFset();
    }

    private void afterLoadFset() {
        try {
            if (this.pkFsetBytes == null) return;
            pulsekit.Engine.FileSet set = pulsekit.Engine.decodeFset(this.pkFsetBytes);
            java.lang.String src = pulsekit.Engine.importSource(this.pkFsetName);
            if (src == null || src.length() == 0 || "Import".equals(src)) src = set.name;
            java.util.List parts = set.parts;
            if (parts == null || parts.isEmpty()) parts = pulsekit.Engine.partsForDisplay(set);
            if (pulsekit.Engine.isFileSetOrigin(set.origin)) pulsekit.Engine.rememberFileSetOrigin(src, set.origin);
            if (set.sourceWav != null || set.combinedWav != null) {
                pulsekit.Engine.rememberFileSetAudio(src, set.sourceWav, set.combinedWav, set.combinedName);
                pulsekit.Engine.storeFileSetAudioDir(new java.io.File(this.getFilesDir(), "fset-audio"));
            }
            if (set.sourceMidi != null && set.sourceMidi.length >= 14) {
                pulsekit.Engine.rememberFileSetMidi(src, set.sourceMidi, set.sourceMidiName);
                if (set.name != null && !set.name.equals(src)) pulsekit.Engine.rememberFileSetMidi(set.name, set.sourceMidi, set.sourceMidiName);
                for (int i = 0; i < this.learned.size() && i < 12; i++) {
                    pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) this.learned.get(i);
                    if (item == null) continue;
                    java.lang.String s = pulsekit.Engine.sourceOf(item);
                    if (s == null || s.length() == 0) continue;
                    if (s.equals(src) || (set.name != null && (s.equals(set.name) || s.startsWith(set.name + " ")))) pulsekit.Engine.rememberFileSetMidi(s, set.sourceMidi, set.sourceMidiName);
                }
            }
            this.storeFsetParts(src, parts);
            this.rebuildImported();
            this.rebuildImportedFills();
        } catch (java.lang.Exception ignored) {}
    }
    private void show(String string) {
        this.showBase(string);
        this.afterShow();
    }
    private void showFileMenu(View anchor) {
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
    private void refreshTabs() {
        this.pkWirePyJav();
        java.util.Iterator it = this.tabs.iterator();
        while (it.hasNext()) {
            android.widget.TextView tab = (android.widget.TextView) it.next();
            java.lang.Object tag = tab.getTag();
            boolean on = this.view.equals(tag)
                    || ("file".equals(tag) && ("import".equals(this.view) || "export".equals(this.view) || "isolate".equals(this.view) || "analyze".equals(this.view) || "compose".equals(this.view) || "fsetinfo".equals(this.view)));
            tab.setBackground(this.round(on ? ELEV : 0, 8));
            tab.setTextColor(on ? FG : MUTED);
        }
    }
    private View buildUi() {
        View root = this.buildUiBase();
        this.wireAnalyzePane();
        this.wireComposePane();
        this.wirePrompts();
        this.wireInfoPane();
        this.loadPersistedFsetInfo();
        return root;
    }
    protected void onCreate(Bundle bundle) {
        this.onCreateBase(bundle);
        ArtJava.pinWorkDir(this);
    }
    /** How the next isolation result treats the pads it found in the song. */
    private static final int ISO_PADS_AUTO = 0;
    private static final int ISO_PADS_USE = 1;
    private static final int ISO_PADS_SKIP = 2;
    private int isoPadMode = ISO_PADS_AUTO;

    /** Called by the isolate pane's "Use pads" and "Skip" buttons. */
    private void finishIsolation(int padMode) {
        this.isoPadMode = padMode;
        try {
            if (this.tryPackIsolation()) {
                return;
            }
            this.finishIsolationBase();
        }
        finally {
            this.isoPadMode = ISO_PADS_AUTO;
        }
    }
    private boolean learnFromSongImport(String string, Engine.MidiBars midiBars) {
        if (this.tryPackMidiFileSet(string, midiBars, true)) {
            return true;
        }
        return this.learnFromSongImportBase(string, midiBars);
    }

    private boolean learnFromSongImport(String string, Engine.MidiBars midiBars, boolean bl) {
        if (this.tryPackMidiFileSet(string, midiBars, bl)) {
            return true;
        }
        return this.learnFromSongImportBase(string, midiBars, bl);
    }
    private void persistLearned() {
        this.persistLearnedBase();
        this.persistFsetInfo();
    }
    private void removeImportSource(String string) {
        this.pkRemovedSource = string;
        this.removeImportSourceBase(string);
        this.ensureFsetInfoMap();
        if (this.pkRemovedSource != null) {
            this.fsetInfoMap.remove(this.pkRemovedSource);
            pulsekit.Engine.forgetFileSetOrigin(this.pkRemovedSource);
            pulsekit.Engine.forgetFileSetAudio(this.pkRemovedSource);
            pulsekit.Engine.storeFileSetAudioDir(new java.io.File(this.getFilesDir(), "fset-audio"));
        }
        this.persistFsetInfo();
    }
    private void refreshSong() {
        this.refreshSongBase();
        this.afterRefreshSong();
    }

    private void refreshPlayhead() {
        this.refreshPlayheadBase();
        this.afterRefreshSong();
    }

    private void audioTickAction94(int n, Engine.Part part, boolean bl) {
        this.audioTickAction94Base(n, part, bl);
        this.afterRefreshSong();
    }
    private LinearLayout partCard(int n, int n2, Engine.Part part, boolean bl) {
        this.pkPartNow = bl;
        LinearLayout card = this.partCardBase(n, n2, part, bl);
        this.afterPartCard(card, this.pkPartNow);
        return card;
    }
    private void addImportPack(LinearLayout linearLayout, String string, String string2, int n, boolean bl, Runnable runnable, Runnable runnable2, Consumer<FlowLayout> consumer) {
        this.pkPackHost = linearLayout;
        this.pkPackKey = string;
        this.pkPackLabel = string2;
        this.pkPackDel = runnable;
        this.pkPackExp = runnable2;
        this.addImportPackBase(linearLayout, string, string2, n, bl, runnable, runnable2, consumer);
        this.attachFileSetInfoMenu(this.pkPackHost, this.pkPackKey, this.pkPackLabel, this.pkPackDel, this.pkPackExp);
    }
    private void refreshImportedFiles() {
        this.refreshImportedFilesBase();
        this.wireFileSetListActions();
    }
    private void ingest(byte[] byArray, String string, Uri uri) throws Exception {
        // Audio goes to the background isolation job.
        if (byArray != null && byArray.length >= 4) {
            java.lang.String k = pulsekit.AudioIo.sniff(byArray);
            java.lang.String n = string == null ? "" : string.toLowerCase();
            boolean audio = "wav".equals(k) || "mp3".equals(k) || n.endsWith(".wav") || n.endsWith(".wave") || n.endsWith(".mp3");
            if (audio) {
                if (this.pkAudioBusy) {
                    android.widget.Toast.makeText(this, "Still working on the song", 0).show();
                    return;
                }
                this.pkAudioBusy = true;
                this.pkIsoBytes = byArray;
                this.pkIsoUri = uri;
                this.pkIsoName = string;
                this.pkIsoErr = null;
                this.pkIsoPending = null;
                android.widget.Toast.makeText(this, "Isolating…", 0).show();
                new java.lang.Thread(new pulsekit.IsolateJob(this), "pulsekit-isolate").start();
                return;
            }
        }
        // Programs (.java, .class, .jar, .js, .ts) open in PyJav.
        if (this.pkTakeProgram(byArray, string)) {
            return;
        }
        // .prompt sheets open in PyJav with their run mode and references.
        if (string != null && (string.toLowerCase().endsWith(".prompt") || (byArray != null && byArray.length >= 9 && new String(byArray, 0, Math.min(byArray.length, 12), java.nio.charset.StandardCharsets.UTF_8).startsWith("PKPROMPT1")))) {
            String base = string;
            int slash = Math.max(base.lastIndexOf(47), base.lastIndexOf(58));
            if (slash >= 0) base = base.substring(slash + 1);
            if (!base.toLowerCase().endsWith(".prompt")) base = base + ".prompt";
            this.pyName = base;
            this.pkPyBytes = null;
            String text = new String(byArray, java.nio.charset.StandardCharsets.UTF_8);
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
            this.pkPromptSource = text;
            this.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
            if (sheet != null && sheet.body != null) text = sheet.body;
            if (this.pyEditor != null) this.pyEditor.setText(text);
            this.pkSetPromptRun(pulsekit.PromptRun.runMode(new String(byArray, java.nio.charset.StandardCharsets.UTF_8), null));
            this.pkLoadRefs(this.pkPromptSource);
            this.pkShowPromptModes();
            this.show("py");
            this.setNow("PyJav · " + base);
            return;
        }
        this.ingestBase(byArray, string, uri);
    }
    private void afterShow() {
        this.afterShowCore();
        this.pkSyncTransport();
    }
    public void tickMixClock() {
        this.tickMixClockCore();
        this.tickMidiClock();
    }
    public void stopCombinedFile() {
        this.stopSourceMidi();
        this.stopCombinedFileCore();
    }
    private void wireFileSetListActions() {
        this.wireFileSetListActionsCore();
        this.pkRebindFileSetClicks();
    }
    private void appendCombinedFileRows() {
        this.appendCombinedFileRowsCore();
        this.appendMidiFileRows();
    }
    public void openFileSetInfo(String key, String label) {
        this.openFileSetInfoCore(key, label);
        this.pkShowInfoMidi(key != null && key.startsWith("f:") ? key.substring(2) : "");
    }
    private boolean pkTakeProgram(byte[] data, String name) {
        if (data != null && name != null && isNodeScript(name)) {
            int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
            String base = slash >= 0 ? name.substring(slash + 1) : name;
            this.pyName = base;
            this.pkPyBytes = null;
            this.pkPyInputPath = null;
            if (this.pyEditor != null) {
                this.pyEditor.setText(new String(data, StandardCharsets.UTF_8));
            }
            this.show("py");
            return true;
        }
        return this.pkTakeProgramCore(data, name);
    }
    public void pkTakePickedProgram(Uri uri) {
        try {
            byte[] data = this.readUri(uri);
            String name = this.displayName(uri, "program.js");
            if (data != null && isNodeScript(name)) {
                this.pyName = name;
                this.pkPyBytes = null;
                this.pkPyInputPath = null;
                if (this.pyEditor != null) {
                    this.pyEditor.setText(new String(data, StandardCharsets.UTF_8));
                }
                this.show("py");
                return;
            }
        }
        catch (Exception exception) {
            // Fall through: the general loader reports the error.
        }
        this.pkTakePickedProgramCore(uri);
    }

    private String displayName(Uri uri, String fallback) {
        String name = null;
        android.database.Cursor cursor = this.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex("_display_name");
                    if (col >= 0) {
                        name = cursor.getString(col);
                    }
                }
            }
            finally {
                cursor.close();
            }
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        if (name == null) {
            name = fallback;
        }
        int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }
    private void pkWirePyJav() {
        this.pkWirePyJavCore();
        if (this.pkPyRecent != null && this.pyPane != null && this.pkPyRecent.getParent() == this.pyPane) {
            this.pyPane.removeView(this.pkPyRecent);
            int at = this.pyPane.getChildCount() > 0 ? 1 : 0;
            this.pyPane.addView(this.pkPyRecent, at);
            this.pkPyRecent.setBackgroundColor(0xFF1B1D1F);
        }
        this.pkRefreshNodeUi();
    }

    private void afterShowCore() {
        boolean an = "analyze".equals(this.view);
        boolean gt = "compose".equals(this.view);
        boolean info = "fsetinfo".equals(this.view);
        boolean pr = "prompts".equals(this.view);
        if (this.analyzePane != null) {
            this.analyzePane.setVisibility(an ? 0 : 8);
            if (an) this.analyzePane.bringToFront();
        }
        if (this.composePane != null) {
            this.composePane.setVisibility(gt ? 0 : 8);
            if (gt) this.composePane.bringToFront();
        }
        if (this.infoPane != null) {
            this.infoPane.setVisibility(info ? 0 : 8);
            if (info) this.infoPane.bringToFront();
        }
        if (this.promptsPane != null) {
            this.promptsPane.setVisibility(pr ? 0 : 8);
            if (pr) this.promptsPane.bringToFront();
        }
        if (an || gt || info || pr) {
            this.gridScroll.setVisibility(8);
            if (this.lenBar != null) this.lenBar.setVisibility(8);
            if (this.importPane != null) this.importPane.setVisibility(8);
            if (this.exportPane != null) this.exportPane.setVisibility(8);
            if (this.isolatePane != null) this.isolatePane.setVisibility(8);
            if (this.padsPane != null) this.padsPane.setVisibility(8);
            if (this.songPane != null) this.songPane.setVisibility(8);
            if (this.pyPane != null) this.pyPane.setVisibility(8);
            if (this.chrome != null) this.chrome.setVisibility(8);
            if (this.knobsRow != null) this.knobsRow.setVisibility(8);
            if (this.toolsRow != null) this.toolsRow.setVisibility(8);
            if (this.styleWrap != null) this.styleWrap.setVisibility(8);
            if (this.fillWrap != null) this.fillWrap.setVisibility(8);
            if (an) {
                if (this.analyzeStatus != null) this.setNow(this.analyzeStatus.getText().toString());
                else this.setNow("Analyze");
            } else if (gt) {
                if (this.composeStatus != null) this.setNow(this.composeStatus.getText().toString());
                else this.setNow("Compose from GT");
            } else if (pr) {
                this.setNow("Prompts");
            } else {
                if (this.infoStatus != null) this.setNow(this.infoStatus.getText().toString());
                else this.setNow("File set");
            }
            this.refreshTabs();
        }
    }

    public void openKitView(java.lang.String view) { this.show(view); }

    public void pickAnalyzeFile() {
        android.content.Intent intent = new android.content.Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("audio/*");
        this.startActivityForResult(intent, 22);
    }

    private java.lang.String fmtAnalyzeTime(float sec) {
        int s = Math.max(0, Math.round(sec));
        return (s / 60) + ":" + java.lang.String.format("%02d", new java.lang.Object[] { java.lang.Integer.valueOf(s % 60) });
    }

    private void showAnalyze(pulsekit.AudioIo.PartAnalysis result) {
        if (this.analyzeRows == null) return;
        this.analyzeRows.removeAllViews();
        if (this.analyzeStatus != null) {
            java.lang.String styleBit = "";
            if (!result.parts.isEmpty()) {
                java.lang.String lab = ((pulsekit.AudioIo.TrackPart) result.parts.get(0)).styleLabel;
                if (lab != null && lab.length() > 0) styleBit = lab + " · ";
            }
            this.analyzeStatus.setText(result.parts.size() + " parts · " + styleBit + this.fmtAnalyzeTime(result.durationSec) + " · " + result.bpm + " BPM overall");
        }
        java.util.List parts = result.parts;
        for (int i = 0; i < parts.size(); i++) {
            pulsekit.AudioIo.TrackPart p = (pulsekit.AudioIo.TrackPart) parts.get(i);
            android.widget.LinearLayout card = this.col();
            card.setBackground(this.round(ELEV, 10));
            card.setPadding(this.dp(10), this.dp(8), this.dp(10), this.dp(8));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, this.dp(8));
            card.setLayoutParams(lp);
            android.widget.LinearLayout head = this.row();
            head.addView(this.text(p.name, 14, true), this.flex(1));
            android.widget.TextView kind = this.text(p.kind, 11, true);
            kind.setTextColor(SUBTLE);
            head.addView(kind);
            card.addView(head);
            card.addView(this.text("BPM " + p.bpm + " · TS " + p.tsNum + "/" + p.tsDen + " · " + p.styleLabel, 12, false));
            android.widget.TextView more = this.text(p.bars + " bars · " + this.fmtAnalyzeTime(p.startSec) + "–" + this.fmtAnalyzeTime(p.endSec) + " · " + p.hits + " hits · swing " + p.swing, 11, false);
            more.setTextColor(SUBTLE);
            card.addView(more);
            this.analyzeRows.addView(card);
        }
        this.analyzeRows.requestLayout();
        this.setNow(result.parts.size() + " parts");
        if (this.analyzeStatus != null && !this.analyzePacked) {
            this.analyzeStatus.setText(this.analyzeStatus.getText().toString() + " Tap Process again to build a file set.");
        }
    }

    public void runAnalyze() {
        if (this.analyzeBytes == null && this.analyzeUri == null) {
            if (this.analyzeStatus != null) this.analyzeStatus.setText("Choose a WAV or MP3 first.");
            return;
        }
        if (this.analyzeResult != null && !this.analyzePacked) {
            if (this.analyzeStatus != null) this.analyzeStatus.setText("Processing…");
            try {
                java.lang.String stem = pulsekit.Engine.uniqueImportSource(pulsekit.Engine.stemNameFromMidi(this.analyzeName), this.learned, this.learnedFills);
                pulsekit.Engine.FileSet set = pulsekit.AudioIo.fileSetFromParts(this.analyzeResult, stem, "analyze");
                if (set.patterns.isEmpty() && set.fills.isEmpty()) {
                    if (this.analyzeStatus != null) this.analyzeStatus.setText("Could not build a file set from those parts");
                    return;
                }
                byte[] packed = pulsekit.Engine.encodeFset(set);
                this.loadFset(packed, pulsekit.Engine.fsetFilename(set.name));
                this.analyzePacked = true;
                if (this.analyzeStatus != null) {
                    this.analyzeStatus.setText("File set · " + set.name + " · " + set.patterns.size() + " patterns · " + set.fillerns.size() + " Fillerns · " + set.fills.size() + " fills. Tap Process to analyze again.");
                }
            } catch (java.lang.Exception ex) {
                if (this.analyzeStatus != null) {
                    java.lang.String m = ex.getMessage();
                    this.analyzeStatus.setText(m != null ? m : "Could not build that file set");
                }
            }
            return;
        }
        if (this.pkAudioBusy) {
            if (this.analyzeStatus != null) this.analyzeStatus.setText("Still working…");
            return;
        }
        this.pkAudioBusy = true;
        this.pkAnErr = null;
        this.pkAnPending = null;
        if (this.analyzeStatus != null) this.analyzeStatus.setText("Processing…");
        new java.lang.Thread(new pulsekit.AnalyzeJob(this), "pulsekit-analyze").start();
    }

    private pulsekit.AudioIo.Pcm songPcm(byte[] bytes, boolean mp3, android.net.Uri uri) throws Exception {
        if (mp3) {
            try {
                pulsekit.JlAssets.install(this);
                pulsekit.AudioIo.Pcm dec = pulsekit.Mp3Decode.parse(bytes);
                return pulsekit.AudioIo.forAnalyze(dec.samples, dec.sr);
            } catch (java.lang.Throwable ex) {
                if (ex instanceof java.lang.Error) throw (java.lang.Error) ex;
            }
            short[] s = this.decodeToShorts(uri, bytes);
            if (s.length > 22050 * 60 * 15) {
                short[] cut = new short[22050 * 60 * 15];
                System.arraycopy(s, 0, cut, 0, cut.length);
                s = cut;
            }
            float[] f = new float[s.length];
            for (int i = 0; i < s.length; i++) f[i] = (float) s[i] / 32768.0f;
            return new pulsekit.AudioIo.Pcm(f, 22050);
        }
        pulsekit.AudioIo.Pcm wav = pulsekit.AudioIo.parseWav(bytes);
        return pulsekit.AudioIo.forAnalyze(wav.samples, wav.sr);
    }

    public void runAnalyzeJob() {
        java.lang.String err = null;
        pulsekit.AudioIo.PartAnalysis analysis = null;
        try {
            byte[] bytes = this.analyzeBytes != null ? this.analyzeBytes : this.readUri(this.analyzeUri);
            java.lang.String kind = pulsekit.AudioIo.sniff(bytes);
            java.lang.String name = this.analyzeName;
            boolean mp3 = "mp3".equals(kind) || (name != null && name.toLowerCase().endsWith(".mp3"));
            pulsekit.AudioIo.Pcm pcm = this.songPcm(bytes, mp3, this.analyzeUri);
            bytes = null;
            this.analyzeBytes = null;
            analysis = pulsekit.AudioIo.analyzeParts(pcm.samples, pcm.sr);
        } catch (java.lang.Throwable ex) {
            err = ex instanceof java.lang.OutOfMemoryError
                ? "That song is too large for this device"
                : ex.getMessage();
            if (err == null || err.length() == 0) err = "Could not analyze that file";
        }
        this.pkAnErr = err;
        this.pkAnPending = analysis;
        this.handler.post(new pulsekit.AnalyzeDone(this));
    }

    public void finishAnalyzeJob() {
        this.pkAudioBusy = false;
        java.lang.String err = this.pkAnErr;
        pulsekit.AudioIo.PartAnalysis analysis = this.pkAnPending;
        this.pkAnErr = null;
        this.pkAnPending = null;
        if (err != null) {
            if (this.analyzeStatus != null) this.analyzeStatus.setText(err);
            return;
        }
        if (analysis == null) {
            if (this.analyzeStatus != null) this.analyzeStatus.setText("Could not analyze that file");
            return;
        }
        this.analyzeResult = analysis;
        this.analyzePacked = false;
        this.showAnalyze(analysis);
    }

    public void runIsolateJob() {
        java.lang.String err = null;
        pulsekit.AudioIo.Analysis analysis = null;
        try {
            byte[] bytes = this.pkIsoBytes;
            android.net.Uri uri = this.pkIsoUri;
            java.lang.String name = this.pkIsoName;
            java.lang.String kind = pulsekit.AudioIo.sniff(bytes);
            java.lang.String low = name == null ? "" : name.toLowerCase();
            boolean mp3 = "mp3".equals(kind) || low.endsWith(".mp3");
            pulsekit.AudioIo.Pcm pcm = this.songPcm(bytes, mp3, uri);
            bytes = null;
            this.pkIsoBytes = null;
            analysis = pulsekit.AudioIo.analyze(pcm.samples, pcm.sr);
            pulsekit.AudioIo.compactImportBars(analysis);
        } catch (java.lang.Throwable ex) {
            err = ex instanceof java.lang.OutOfMemoryError
                ? "That song is too large for this device"
                : ex.getMessage();
            if (err == null || err.length() == 0) err = "Could not isolate that song";
        }
        this.pkIsoErr = err;
        this.pkIsoPending = analysis;
        this.handler.post(new pulsekit.IsolateDone(this));
    }

    public void finishIsolateJob() {
        this.pkAudioBusy = false;
        this.pkIsoBytes = null;
        java.lang.String err = this.pkIsoErr;
        pulsekit.AudioIo.Analysis analysis = this.pkIsoPending;
        java.lang.String name = this.pkIsoName != null ? this.pkIsoName : "song";
        this.pkIsoErr = null;
        this.pkIsoPending = null;
        if (err != null || analysis == null) {
            java.lang.String m = err != null ? err : "Could not isolate that song";
            android.widget.Toast.makeText(this, m, 1).show();
            return;
        }
        try {
            if (analysis.isolated) {
                this.applyIsoMemory(analysis, name);
                this.isolateAnalysis = analysis;
                this.isolateFile = name;
                this.refreshIsolate();
                this.show("isolate");
                android.widget.Toast.makeText(this, "Kick and snare found", 0).show();
            } else {
                this.applyAnalysis(analysis, name);
            }
        } catch (java.lang.Throwable ex) {
            java.lang.String m = ex.getMessage();
            android.widget.Toast.makeText(this, m != null && m.length() > 0 ? m : "Could not open that song", 1).show();
        }
    }

    private void takeAnalyzeFile(android.net.Uri uri) {
        try {
            this.analyzeUri = uri;
            this.analyzeBytes = this.readUri(uri);
            this.analyzeResult = null;
            this.analyzePacked = false;
            java.lang.String name = uri.getLastPathSegment();
            if (name == null || name.length() == 0) name = "song";
            int slash = name.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
            this.analyzeName = name;
            if (this.analyzeFileLab != null) this.analyzeFileLab.setText(name);
            if (this.analyzeStatus != null) this.analyzeStatus.setText("Tap Process to split the track.");
            if (this.analyzeRows != null) this.analyzeRows.removeAllViews();
            this.show("analyze");
        } catch (java.lang.Exception ex) {
            if (this.analyzeStatus != null) this.analyzeStatus.setText("Could not read that file");
        }
    }

    private void wireAnalyzePane() {
        this.analyzePane = this.col();
        this.analyzePane.setVisibility(8);
        this.analyzePane.setBackgroundColor(BG);
        this.analyzePane.setClickable(true);
        this.analyzePane.setPadding(this.dp(16), this.dp(12), this.dp(16), this.dp(12));
        this.analyzePane.addView(this.text("Analyze", 18, true));
        this.analyzeStatus = this.text("Choose a WAV or MP3, then tap Process.", 14, false);
        this.analyzeStatus.setTextColor(MUTED);
        this.analyzeStatus.setPadding(0, this.dp(8), 0, this.dp(8));
        this.analyzePane.addView(this.analyzeStatus);
        this.analyzeFileLab = this.text("No file selected", 13, false);
        this.analyzeFileLab.setTextColor(FG);
        this.analyzeFileLab.setBackground(this.round(SURFACE, 8));
        this.analyzeFileLab.setPadding(this.dp(12), this.dp(12), this.dp(12), this.dp(12));
        android.widget.LinearLayout.LayoutParams pathLp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        pathLp.setMargins(0, this.dp(4), 0, this.dp(8));
        this.analyzePane.addView(this.analyzeFileLab, pathLp);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(-1, this.dp(48));
        btnLp.setMargins(0, 0, 0, this.dp(8));
        android.widget.TextView pick = this.action("Choose file", SURFACE, FG, pulsekit.AnalyzeClicks.pick(this));
        this.analyzePane.addView(pick, btnLp);
        android.widget.LinearLayout.LayoutParams procLp = new android.widget.LinearLayout.LayoutParams(-1, this.dp(48));
        procLp.setMargins(0, 0, 0, this.dp(8));
        android.widget.TextView process = this.action("Process", FG, BG, pulsekit.AnalyzeClicks.process(this));
        this.analyzePane.addView(process, procLp);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        this.analyzeRows = this.col();
        scroll.addView(this.analyzeRows);
        android.widget.LinearLayout.LayoutParams lp = this.flexFill();
        lp.setMargins(0, this.dp(8), 0, 0);
        this.analyzePane.addView(scroll, lp);
        android.view.ViewGroup host = null;
        if (this.isolatePane != null && this.isolatePane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.isolatePane.getParent();
        } else if (this.importPane != null && this.importPane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.importPane.getParent();
        } else if (this.exportPane != null && this.exportPane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.exportPane.getParent();
        }
        if (host != null) {
            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(-1, -1);
            host.addView((android.view.View) this.analyzePane, (android.view.ViewGroup.LayoutParams) flp);
        }
    }

    public void pickComposeFile() {
        android.content.Intent intent = new android.content.Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("audio/*");
        this.startActivityForResult(intent, 23);
    }

    public void showCompose(pulsekit.AudioIo.PartAnalysis result) {
        if (this.composeRows == null) return;
        this.composeRows.removeAllViews();
        if (this.composeStatus != null) {
            java.lang.String styleBit = "";
            if (!result.parts.isEmpty()) {
                java.lang.String lab = ((pulsekit.AudioIo.TrackPart) result.parts.get(0)).styleLabel;
                if (lab != null && lab.length() > 0) styleBit = lab + " · ";
            }
            this.composeStatus.setText((this.composeVocalsApplied ? "Vocals reduced · " : "") + result.parts.size() + " parts · " + styleBit + this.fmtAnalyzeTime(result.durationSec) + " · " + result.bpm + " BPM overall");
        }
        java.util.List parts = result.parts;
        for (int i = 0; i < parts.size(); i++) {
            pulsekit.AudioIo.TrackPart p = (pulsekit.AudioIo.TrackPart) parts.get(i);
            android.widget.LinearLayout card = this.col();
            card.setBackground(this.round(ELEV, 10));
            card.setPadding(this.dp(10), this.dp(8), this.dp(10), this.dp(8));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, this.dp(8));
            card.setLayoutParams(lp);
            android.widget.LinearLayout head = this.row();
            head.addView(this.text(p.name, 14, true), this.flex(1));
            android.widget.TextView kind = this.text(p.kind, 11, true);
            kind.setTextColor(SUBTLE);
            head.addView(kind);
            card.addView(head);
            card.addView(this.text("BPM " + p.bpm + " · TS " + p.tsNum + "/" + p.tsDen + " · " + p.styleLabel, 12, false));
            android.widget.TextView more = this.text(p.bars + " bars · " + this.fmtAnalyzeTime(p.startSec) + "–" + this.fmtAnalyzeTime(p.endSec) + " · " + p.hits + " hits · swing " + p.swing, 11, false);
            more.setTextColor(SUBTLE);
            card.addView(more);
            this.composeRows.addView(card);
        }
        this.composeRows.requestLayout();
        this.setNow(result.parts.size() + " parts");
        if (this.composeStatus != null && !this.composePacked) {
            this.composeStatus.setText(this.composeStatus.getText().toString() + " Tap Process again to compose drums.");
        }
    }

    public pulsekit.AudioIo.StereoPcm pkDecodeStereo(android.net.Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("No audio file");
        android.media.MediaExtractor ex = new android.media.MediaExtractor();
        android.os.ParcelFileDescriptor pfd = this.getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new IllegalArgumentException("Could not open that file");
        android.media.MediaCodec codec = null;
        try {
            ex.setDataSource(pfd.getFileDescriptor());
            int track = -1;
            android.media.MediaFormat fmt = null;
            int nTracks = ex.getTrackCount();
            for (int i = 0; i < nTracks; i++) {
                android.media.MediaFormat f = ex.getTrackFormat(i);
                java.lang.String mime = f.getString("mime");
                if (mime != null && mime.startsWith("audio/")) { track = i; fmt = f; break; }
            }
            if (fmt == null || track < 0) throw new IllegalArgumentException("No audio track");
            ex.selectTrack(track);
            int inSr = 44100;
            if (fmt.containsKey("sample-rate")) inSr = fmt.getInteger("sample-rate");
            if (inSr < 8000) inSr = 44100;
            int chIn = 1;
            if (fmt.containsKey("channel-count")) chIn = fmt.getInteger("channel-count");
            if (chIn < 1) chIn = 1;
            int outSr = 22050;
            long durUs = 0L;
            if (fmt.containsKey("durationUs")) durUs = fmt.getLong("durationUs");
            int cap = outSr * 30;
            if (durUs > 0L) cap = (int) (durUs * outSr / 1000000L) + outSr;
            if (cap < 1) cap = 1;
            if (cap > outSr * 480) throw new IllegalArgumentException("That take is too long to strip on this phone");
            float[] left = new float[cap];
            float[] right = null;
            if (chIn > 1) right = new float[cap];
            int pcmFloat = 2;
            if (fmt.containsKey("pcm-encoding")) pcmFloat = fmt.getInteger("pcm-encoding");
            java.lang.String mime = fmt.getString("mime");
            codec = android.media.MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();
            android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            boolean inDone = false;
            boolean outDone = false;
            int spins = 0;
            long seen = 0L;
            int w = 0;
            double ratio = inSr / (double) outSr;
            if (!(ratio > 0.01)) ratio = 1.0;
            int limit = outSr * 480;
            while (!outDone) {
                if (!inDone) {
                    int inIx = codec.dequeueInputBuffer(8000L);
                    if (inIx >= 0) {
                        java.nio.ByteBuffer inBuf = codec.getInputBuffer(inIx);
                        int nread = -1;
                        if (inBuf != null) nread = ex.readSampleData(inBuf, 0);
                        if (nread < 0) {
                            codec.queueInputBuffer(inIx, 0, 0, 0L, 4);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(inIx, 0, nread, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int outIx = codec.dequeueOutputBuffer(info, 8000L);
                if (outIx == -2) {
                    android.media.MediaFormat of = codec.getOutputFormat();
                    if (of != null && of.containsKey("channel-count")) chIn = of.getInteger("channel-count");
                    if (chIn < 1) chIn = 1;
                    if (of != null && of.containsKey("sample-rate")) inSr = of.getInteger("sample-rate");
                    if (inSr < 8000) inSr = 44100;
                    ratio = inSr / (double) outSr;
                    if (of != null && of.containsKey("pcm-encoding")) pcmFloat = of.getInteger("pcm-encoding");
                    if (chIn > 1 && right == null) right = new float[left.length];
                    continue;
                }
                if (outIx == -1) {
                    if (inDone && ++spins > 40) outDone = true;
                    continue;
                }
                if (outIx < 0) continue;
                spins = 0;
                java.nio.ByteBuffer buf = codec.getOutputBuffer(outIx);
                int bps = pcmFloat == 4 ? 4 : 2;
                int frameBytes = bps * chIn;
                int start = info.offset;
                int size = info.size;
                if (buf != null && frameBytes > 0 && start >= 0 && size > 0 && start + size <= buf.capacity()) {
                    int frames = size / frameBytes;
                    for (int i = 0; i < frames; i++) {
                        int p = start + i * frameBytes;
                        if (p + frameBytes > buf.capacity()) break;
                        float lf;
                        float rf;
                        if (pcmFloat == 4) {
                            lf = buf.getFloat(p);
                            rf = chIn > 1 ? buf.getFloat(p + 4) : lf;
                        } else {
                            int ls = (short) ((buf.get(p) & 0xff) | (buf.get(p + 1) << 8));
                            lf = ls / 32768f;
                            if (chIn > 1) {
                                int rs = (short) ((buf.get(p + 2) & 0xff) | (buf.get(p + 3) << 8));
                                rf = rs / 32768f;
                            } else rf = lf;
                        }
                        long abs = seen + i;
                        int guard = 0;
                        while ((long) Math.floor(w * ratio) <= abs && guard++ < 8) {
                            if (w >= left.length) {
                                int ncap = left.length * 2;
                                if (ncap > limit) throw new IllegalArgumentException("That take is too long to strip on this phone");
                                float[] nl = new float[ncap];
                                java.lang.System.arraycopy(left, 0, nl, 0, left.length);
                                left = nl;
                                if (right != null) {
                                    float[] nr = new float[ncap];
                                    java.lang.System.arraycopy(right, 0, nr, 0, right.length);
                                    right = nr;
                                }
                            }
                            left[w] = lf;
                            if (right != null) right[w] = rf;
                            w++;
                        }
                    }
                    seen += frames;
                }
                codec.releaseOutputBuffer(outIx, false);
                if ((info.flags & 4) != 0) outDone = true;
            }
            int n = w;
            if (n < 1) n = 1;
            if (n < left.length) {
                float[] lf = new float[n];
                java.lang.System.arraycopy(left, 0, lf, 0, Math.min(n, left.length));
                left = lf;
                if (right != null) {
                    float[] rf = new float[n];
                    java.lang.System.arraycopy(right, 0, rf, 0, Math.min(n, right.length));
                    right = rf;
                }
            }
            return new pulsekit.AudioIo.StereoPcm(left, right, outSr);
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (java.lang.Throwable ignored) {}
                try { codec.release(); } catch (java.lang.Throwable ignored) {}
            }
            try { ex.release(); } catch (java.lang.Throwable ignored) {}
            try { pfd.close(); } catch (java.lang.Throwable ignored) {}
        }
    }

    public byte[] pkReadUri(android.net.Uri uri) throws Exception {
        return this.readUri(uri);
    }

    public short[] pkDecodeToShorts(android.net.Uri uri, byte[] bytes) throws Exception {
        return this.decodeToShorts(uri, bytes);
    }

    public void runCompose() {
        boolean strip = this.composeRemoveVocals != null && this.composeRemoveVocals.isChecked();
        if (this.composeBytes == null && this.composeUri == null) {
            if (this.composeStatus != null) this.composeStatus.setText("Choose a WAV or MP3 first.");
            return;
        }
        if (this.composeResult != null && !this.composePacked && strip == this.composeVocalsApplied) {
            if (this.composeStatus != null) this.composeStatus.setText("Processing…");
            try {
                java.lang.String stem = pulsekit.Engine.uniqueImportSource(pulsekit.Engine.stemNameFromMidi(this.composeName), this.learned, this.learnedFills);
                pulsekit.Engine.FileSet set = pulsekit.AudioIo.fileSetFromParts(this.composeResult, stem, "compose");
                if (this.composeSourceWav != null) pulsekit.AudioIo.stampGuitarIfEmpty(set, this.composeSourceWav);
                if (set.patterns.isEmpty() && set.fills.isEmpty()) {
                    if (this.composeStatus != null) this.composeStatus.setText("Could not compose drums from those parts");
                    return;
                }
                if (this.composeSourceWav != null) pulsekit.Engine.rememberFileSetAudio(set.name, this.composeSourceWav, null, null);
                pulsekit.Engine.storeFileSetAudioDir(new java.io.File(this.getFilesDir(), "fset-audio"));
                byte[] packed = pulsekit.Engine.encodeFset(set);
                this.loadFset(packed, pulsekit.Engine.fsetFilename(set.name));
                this.composePacked = true;
                if (this.composeStatus != null) {
                    this.composeStatus.setText("File set · C " + set.name + " · " + set.patterns.size() + " patterns · " + set.fillerns.size() + " Fillerns · " + set.fills.size() + " fills. Tap Process to split again.");
                }
            } catch (java.lang.Exception ex) {
                if (this.composeStatus != null) {
                    java.lang.String m = ex.getMessage();
                    this.composeStatus.setText(m != null ? m : "Could not compose that file set");
                }
            }
            return;
        }
        if (this.pkComposeBusy) return;
        this.pkComposeBusy = true;
        if (this.composeStatus != null) this.composeStatus.setText(strip ? "Removing vocals…" : "Processing…");
        new java.lang.Thread(new pulsekit.ComposeJob(this, strip)).start();
    }

    public void runComposeJob(boolean doStrip) {
        java.lang.String err = null;
        pulsekit.AudioIo.PartAnalysis analysis = null;
        byte[] sourceWav = null;
        try {
            byte[] bytes = this.composeBytes != null ? this.composeBytes : this.pkReadUri(this.composeUri);
            java.lang.String kind = pulsekit.AudioIo.sniff(bytes);
            java.lang.String name = this.composeName;
            pulsekit.AudioIo.Pcm pcm;
            boolean mp3 = "mp3".equals(kind) || (name != null && name.toLowerCase().endsWith(".mp3"));
            if (mp3) {
                float[] f = null;
                if (this.composeUri != null) {
                    try {
                        pulsekit.AudioIo.StereoPcm st = this.pkDecodeStereo(this.composeUri);
                        if (st != null && st.left != null) {
                            boolean wide = st.right != null;
                            int sr = st.sr;
                            if (sr <= 0) sr = 22050;
                            if (doStrip || wide) {
                                float[] right = null;
                                if (wide) right = st.right;
                                f = pulsekit.AudioIo.removeVocals(st.left, right, sr);
                            } else f = st.left;
                        }
                    } catch (java.lang.Throwable ignored) {}
                }
                if (f == null) {
                    short[] s = this.pkDecodeToShorts(this.composeUri, bytes);
                    f = new float[s.length];
                    for (int i = 0; i < s.length; i++) f[i] = (float) s[i] / 32768.0f;
                    s = null;
                    if (doStrip) f = pulsekit.AudioIo.removeVocals(f, null, 22050);
                }
                pcm = new pulsekit.AudioIo.Pcm(f, 22050);
            } else if (doStrip || pulsekit.AudioIo.wavChannelCount(bytes) >= 2) {
                pcm = pulsekit.AudioIo.stripVocalsWav(bytes);
            } else {
                pcm = pulsekit.AudioIo.parseWav(bytes);
            }
            analysis = pulsekit.AudioIo.analyzeParts(pcm.samples, pcm.sr);
            float[] mono = pulsekit.AudioIo.resample(pcm.samples, pcm.sr, 22050);
            sourceWav = pulsekit.AudioIo.encodeWav(pulsekit.AudioIo.floatsToShorts(mono), 22050);
        } catch (java.lang.Throwable ex) {
            err = ex instanceof java.lang.OutOfMemoryError
                ? "That take is too long to strip on this phone"
                : ex.getMessage();
            if (err == null) err = "Could not split that guitar track";
        }
        this.pkComposeErr = err;
        this.pkComposePending = analysis;
        this.pkComposeWav = sourceWav;
        this.pkComposeStrip = doStrip;
        this.handler.post(new pulsekit.ComposeDone(this));
    }

    public void finishComposeJob() {
        this.pkComposeBusy = false;
        java.lang.String err = this.pkComposeErr;
        pulsekit.AudioIo.PartAnalysis analysis = this.pkComposePending;
        byte[] wav = this.pkComposeWav;
        this.pkComposeErr = null;
        this.pkComposePending = null;
        this.pkComposeWav = null;
        if (err != null) {
            if (this.composeStatus != null) this.composeStatus.setText(err);
            return;
        }
        if (analysis == null) {
            if (this.composeStatus != null) this.composeStatus.setText("Could not split that guitar track");
            return;
        }
        this.composeVocalsApplied = this.pkComposeStrip;
        this.composeSourceWav = wav;
        this.composeResult = analysis;
        this.composePacked = false;
        this.showCompose(analysis);
    }

    private void takeComposeFile(android.net.Uri uri) {
        try {
            this.composeUri = uri;
            this.composeBytes = this.readUri(uri);
            this.composeResult = null;
            this.composePacked = false;
            this.composeSourceWav = null;
            this.composeVocalsApplied = false;
            java.lang.String name = uri.getLastPathSegment();
            if (name == null || name.length() == 0) name = "song";
            int slash = name.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
            this.composeName = name;
            if (this.composeFileLab != null) this.composeFileLab.setText(name);
            if (this.composeStatus != null) this.composeStatus.setText("Tap Process to split the guitar track.");
            if (this.composeRows != null) this.composeRows.removeAllViews();
            this.show("compose");
        } catch (java.lang.Exception ex) {
            if (this.composeStatus != null) this.composeStatus.setText("Could not read that file");
        }
    }

    private void wireComposePane() {
        this.composePane = this.col();
        this.composePane.setVisibility(8);
        this.composePane.setBackgroundColor(BG);
        this.composePane.setClickable(true);
        this.composePane.setPadding(this.dp(16), this.dp(12), this.dp(16), this.dp(12));
        this.composePane.addView(this.text("Compose from GT", 18, true));
        this.composeStatus = this.text("Choose a guitar WAV or MP3, then tap Process.", 14, false);
        this.composeStatus.setTextColor(MUTED);
        this.composeStatus.setPadding(0, this.dp(8), 0, this.dp(8));
        this.composePane.addView(this.composeStatus);
        this.composeFileLab = this.text("No file selected", 13, false);
        this.composeFileLab.setTextColor(FG);
        this.composeFileLab.setBackground(this.round(SURFACE, 8));
        this.composeFileLab.setPadding(this.dp(12), this.dp(12), this.dp(12), this.dp(12));
        android.widget.LinearLayout.LayoutParams pathLp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        pathLp.setMargins(0, this.dp(4), 0, this.dp(8));
        this.composePane.addView(this.composeFileLab, pathLp);
        this.composeRemoveVocals = new android.widget.CheckBox(this);
        this.composeRemoveVocals.setText("Remove vocals");
        this.composeRemoveVocals.setTextColor(FG);
        this.composeRemoveVocals.setTextSize(14f);
        this.composeRemoveVocals.setButtonTintList(android.content.res.ColorStateList.valueOf(FG));
        this.composeRemoveVocals.setPadding(0, this.dp(4), 0, this.dp(8));
        this.composePane.addView(this.composeRemoveVocals);
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(-1, this.dp(48));
        btnLp.setMargins(0, 0, 0, this.dp(8));
        android.widget.TextView pick = this.action("Choose file", SURFACE, FG, pulsekit.AnalyzeClicks.pickCompose(this));
        this.composePane.addView(pick, btnLp);
        android.widget.LinearLayout.LayoutParams procLp = new android.widget.LinearLayout.LayoutParams(-1, this.dp(48));
        procLp.setMargins(0, 0, 0, this.dp(8));
        android.widget.TextView process = this.action("Process", FG, BG, pulsekit.AnalyzeClicks.processCompose(this));
        this.composePane.addView(process, procLp);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        this.composeRows = this.col();
        scroll.addView(this.composeRows);
        android.widget.LinearLayout.LayoutParams lp = this.flexFill();
        lp.setMargins(0, this.dp(8), 0, 0);
        this.composePane.addView(scroll, lp);
        android.view.ViewGroup host = null;
        if (this.analyzePane != null && this.analyzePane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.analyzePane.getParent();
        } else if (this.isolatePane != null && this.isolatePane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.isolatePane.getParent();
        } else if (this.importPane != null && this.importPane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.importPane.getParent();
        }
        if (host != null) {
            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(-1, -1);
            host.addView((android.view.View) this.composePane, (android.view.ViewGroup.LayoutParams) flp);
        }
    }

    public void openPrompts() { this.show("prompts"); }

    public void takePromptRef(android.net.Uri uri) { pulsekit.PromptSheet.take(this, uri); }

    private void wirePrompts() {
        this.promptsPane = pulsekit.PromptSheet.create(this);
        this.promptsPane.setVisibility(8);
        android.view.ViewGroup host = null;
        if (this.isolatePane != null && this.isolatePane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.isolatePane.getParent();
        } else if (this.importPane != null && this.importPane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.importPane.getParent();
        }
        if (host != null) {
            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(-1, -1);
            host.addView((android.view.View) this.promptsPane, (android.view.ViewGroup.LayoutParams) flp);
        }
        android.widget.TextView tab = this.text("Prompts", 13, true);
        tab.setGravity(17);
        tab.setTag("prompts");
        tab.setOnClickListener(pulsekit.AnalyzeClicks.prompts(this));
        tab.setPadding(0, this.dp(8), 0, this.dp(8));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, this.dp(36), 1.0f);
        lp.setMargins(this.dp(2), 0, this.dp(2), 0);
        tab.setLayoutParams(lp);
        android.view.View py = null;
        java.util.Iterator it = this.tabs.iterator();
        while (it.hasNext()) {
            android.widget.TextView t = (android.widget.TextView) it.next();
            if ("py".equals(t.getTag())) { py = t; break; }
        }
        if (py != null && py.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup row = (android.view.ViewGroup) py.getParent();
            row.addView(tab, row.indexOfChild(py) + 1);
        }
        this.tabs.add(tab);
    }

    private void writeCombinedExport(android.net.Uri uri) {
        if (uri == null || this.pkCombinedExport == null) return;
        try {
            java.io.OutputStream out = this.getContentResolver().openOutputStream(uri);
            if (out == null) {
                this.setNow("Could not save that mix");
                return;
            }
            out.write(this.pkCombinedExport);
            out.close();
            java.lang.String name = this.pkCombinedName != null ? this.pkCombinedName : "combined_track.wav";
            this.setNow("Saved \u00b7 " + name);
        } catch (java.lang.Exception ex) {
            java.lang.String m = ex.getMessage();
            this.setNow(m != null ? m : "Could not save that mix");
        }
    }

    public void takePromptExport(android.net.Uri uri) { pulsekit.PromptSheet.writeExport(this, uri); }

    protected void onActivityResult(int req, int res, android.content.Intent data) {
        if (req == 22) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.takeAnalyzeFile(data.getData());
            return;
        }
        if (req == 23) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.takeComposeFile(data.getData());
            return;
        }
        if (req == 24) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.writeCombinedExport(data.getData());
            return;
        }
        if (req == 27) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.takePromptRef(data.getData());
            return;
        }
        if (req == 28) {
            super.onActivityResult(req, res, data);
            if (res == -1 && data != null && data.getData() != null) this.takePromptExport(data.getData());
            return;
        }
        this.onActivityResultBase(req, res, data);
    }

    private void ensureFsetInfoMap() {
        if (this.fsetInfoMap == null) this.fsetInfoMap = new java.util.LinkedHashMap();
    }

    private void persistFsetInfo() {
        try {
            this.ensureFsetInfoMap();
            java.io.File f = new java.io.File(this.getFilesDir(), "fset-info.json");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            out.write(pulsekit.Engine.encodeFsetInfo(this.fsetInfoMap).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.close();
        } catch (java.lang.Exception ignored) {}
    }

    private void loadPersistedFsetInfo() {
        try {
            this.ensureFsetInfoMap();
            java.io.File f = new java.io.File(this.getFilesDir(), "fset-info.json");
            if (!f.isFile() || f.length() < 8) return;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            in.close();
            if (n <= 0) return;
            java.lang.String json = new java.lang.String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
            this.fsetInfoMap.clear();
            this.fsetInfoMap.putAll(pulsekit.Engine.decodeFsetInfo(json));
            pulsekit.Engine.fileSetOrigins.clear();
            pulsekit.Engine.fileSetOrigins.putAll(pulsekit.Engine.decodeFsetOrigins(json));
            pulsekit.Engine.loadFileSetAudioDir(new java.io.File(this.getFilesDir(), "fset-audio"));
        } catch (java.lang.Exception ignored) {}
    }

    private void storeFsetParts(java.lang.String source, java.util.List parts) {
        this.ensureFsetInfoMap();
        if (source == null) return;
        if (parts == null || parts.isEmpty()) this.fsetInfoMap.remove(source);
        else this.fsetInfoMap.put(source, new java.util.ArrayList(parts));
        this.persistFsetInfo();
    }

    private void applyPlayerVolumes() {
        float bd = this.pkBakeDrum > 0 ? this.pkBakeDrum : 100f;
        float bg = this.pkBakeGuitar > 0 ? this.pkBakeGuitar : 100f;
        float bm = this.pkBakeMain > 0 ? this.pkBakeMain : 100f;
        float drum = (this.pkDrumLevel / bd) * (this.pkMain / bm);
        float guitar = (this.pkGuitarLevel / bg) * (this.pkMain / bm);
        if (drum < 0f) drum = 0f;
        if (guitar < 0f) guitar = 0f;
        if (drum > 1f) drum = 1f;
        if (guitar > 1f) guitar = 1f;
        if (this.pkDrumPlayer != null) this.pkDrumPlayer.setVolume(drum, drum);
        if (this.pkGuitarPlayer != null) this.pkGuitarPlayer.setVolume(guitar, guitar);
        if (this.pkDrumPlayer == null && this.pkGuitarPlayer == null && this.pkMixPlayer != null) {
            float m = this.pkMain / 100f;
            if (m < 0f) m = 0f;
            if (m > 1f) m = 1f;
            this.pkMixPlayer.setVolume(m, m);
        }
    }

    public void setMixLevel(int kind, int value) {
        int cap = kind == 5 ? 200 : 100;
        if (value < 0) value = 0;
        if (value > cap) value = cap;
        if (kind == 0) this.pkDrumLevel = value;
        else if (kind == 1) this.pkGuitarLevel = value;
        else if (kind == 2) this.pkGate = value;
        else if (kind == 3) this.pkComp = value;
        else if (kind == 4) this.pkLimit = value;
        else this.pkMain = value;
        if (kind < 2 || kind == 5) {
            this.applyPlayerVolumes();
            this.setNow("Main " + this.pkMain + "  Drum " + this.pkDrumLevel + "  Guitar " + this.pkGuitarLevel);
        }
    }

    private android.widget.LinearLayout mixSlider(java.lang.String label, int kind, java.lang.String src) {
        android.widget.LinearLayout row = this.row();
        android.widget.TextView lab = this.text(label, 11, false);
        lab.setTextColor(MUTED);
        android.widget.SeekBar bar = new android.widget.SeekBar(this);
        bar.setMax(kind == 5 ? 200 : 100);
        int progress = this.pkLimit;
        if (kind == 0) progress = this.pkDrumLevel;
        else if (kind == 1) progress = this.pkGuitarLevel;
        else if (kind == 2) progress = this.pkGate;
        else if (kind == 3) progress = this.pkComp;
        else if (kind == 5) progress = this.pkMain;
        bar.setProgress(progress);
        pulsekit.MixLevels levels = new pulsekit.MixLevels(this, kind, src);
        bar.setOnSeekBarChangeListener(levels);
        bar.setOnTouchListener(levels);
        row.addView(lab);
        row.addView(bar, this.flex(1));
        return row;
    }

    private void addMixLevels(android.widget.LinearLayout parent, java.lang.String src) {
        if (parent == null) return;
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setTag("mixlevels");
        box.addView(this.mixSlider("Main", 5, src));
        box.addView(this.mixSlider("Drum", 0, src));
        box.addView(this.mixSlider("Guitar", 1, src));
        box.addView(this.mixSlider("Gate", 2, src));
        box.addView(this.mixSlider("Comp", 3, src));
        box.addView(this.mixSlider("Limit", 4, src));
        parent.addView(box);
    }

    private static java.lang.String mixTime(int ms) {
        if (ms < 0) ms = 0;
        int s = ms / 1000;
        int m = s / 60;
        s = s % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    private void paintMixClockOn(android.view.View v, java.lang.String label) {
        if (v instanceof android.widget.TextView && "mixclock".equals(v.getTag())) {
            ((android.widget.TextView) v).setText(label);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) this.paintMixClockOn(g.getChildAt(i), label);
        }
    }

    private void paintMixClock(java.lang.String label) {
        android.view.View root = this.getWindow() == null ? null : this.getWindow().getDecorView();
        if (root != null) this.paintMixClockOn(root, label);
    }

    private void stopMixClock() {
        if (this.pkMixRun != null) this.handler.removeCallbacks(this.pkMixRun);
    }

    private void tickMixClockCore() {
        android.media.MediaPlayer mp = this.pkMixPlayer;
        if (mp == null) return;
        int pos = 0;
        int dur = 0;
        boolean going = false;
        try {
            dur = mp.getDuration();
            pos = mp.getCurrentPosition();
            going = mp.isPlaying();
        } catch (java.lang.Exception ex) { return; }
        java.lang.String label = mixTime(pos) + " / " + mixTime(dur);
        this.setNow(label);
        this.paintMixClock(label);
        if (going && this.pkMixRun != null) this.handler.postDelayed(this.pkMixRun, 200L);
    }

    private void startMixClock() {
        this.stopMixClock();
        this.pkMixRun = new pulsekit.MixClock(this);
        this.handler.post(this.pkMixRun);
    }

    private void writePcmFile(java.io.File f, short[] pcm) throws java.lang.Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        out.write(pulsekit.AudioIo.encodeWav(pcm, 22050));
        out.close();
    }

    private void releasePlayer(android.media.MediaPlayer p) {
        if (p == null) return;
        try { p.stop(); } catch (java.lang.Exception ignored) {}
        try { p.release(); } catch (java.lang.Exception ignored) {}
    }

    private void releaseStemPlayers() {
        android.media.MediaPlayer mix = this.pkMixPlayer;
        android.media.MediaPlayer drum = this.pkDrumPlayer;
        android.media.MediaPlayer guitar = this.pkGuitarPlayer;
        this.pkMixPlayer = null;
        this.pkDrumPlayer = null;
        this.pkGuitarPlayer = null;
        if (mix != null && mix != drum && mix != guitar) this.releasePlayer(mix);
        this.releasePlayer(drum);
        this.releasePlayer(guitar);
    }

    private void stopCombinedFileCore() {
        this.stopMixClock();
        this.releaseStemPlayers();
        this.setNow("Stopped");
    }

    private void paintMidiClockOn(android.view.View v, java.lang.String label) {
        if (v instanceof android.widget.TextView && "midiclock".equals(v.getTag())) {
            ((android.widget.TextView) v).setText(label);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) this.paintMidiClockOn(g.getChildAt(i), label);
        }
    }

    private void paintMidiClock(java.lang.String label) {
        android.view.View root = this.getWindow() == null ? null : this.getWindow().getDecorView();
        if (root != null) this.paintMidiClockOn(root, label);
    }

    public void tickMidiClock() {
        android.media.MediaPlayer mp = this.pkMidiPlayer;
        if (mp == null) return;
        int pos = 0;
        int dur = 0;
        boolean going = false;
        try {
            dur = mp.getDuration();
            pos = mp.getCurrentPosition();
            going = mp.isPlaying();
        } catch (java.lang.Exception ex) { return; }
        this.paintMidiClock(mixTime(pos) + " / " + mixTime(dur));
        if (going && this.pkMixRun != null) this.handler.postDelayed(this.pkMixRun, 200L);
    }

    public void stopSourceMidi() {
        android.media.MediaPlayer mp = this.pkMidiPlayer;
        this.pkMidiPlayer = null;
        this.pkMidiPaused = false;
        this.pkMidiSrc = null;
        if (mp != null) this.releasePlayer(mp);
        this.paintMidiClock("00:00 / 00:00");
        this.setNow("Stopped");
    }

    public void pauseSourceMidi() {
        android.media.MediaPlayer mp = this.pkMidiPlayer;
        if (mp == null) return;
        try {
            if (mp.isPlaying()) mp.pause();
            this.pkMidiPaused = true;
            this.paintMidiClock(mixTime(mp.getCurrentPosition()) + " / " + mixTime(mp.getDuration()));
            this.setNow("Paused");
        } catch (java.lang.Exception ex) {
            java.lang.String m = ex.getMessage();
            this.setNow(m != null ? m : "Could not pause");
        }
    }

    public void playSourceMidi(java.lang.String src) {
        if (src != null && src.equals(this.pkMidiSrc) && this.pkMidiPlayer != null && this.pkMidiPaused) {
            try {
                this.pkMidiPlayer.start();
                this.pkMidiPaused = false;
                this.startMixClock();
                this.setNow("Play");
                return;
            } catch (java.lang.Exception ignored) {}
        }
        this.stopCombinedFile();
        this.stopSourceMidi();
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if (au == null || au.sourceMidi == null || au.sourceMidi.length < 14) {
            this.setNow("That MIDI file is missing");
            return;
        }
        try {
            short[] pcm = pulsekit.AudioIo.renderMidiDrums(au.sourceMidi, this.mixVoices(), 22050);
            if (pcm == null || pcm.length < 8) {
                this.setNow("No drum notes in that MIDI");
                return;
            }
            java.io.File f = new java.io.File(this.getCacheDir(), "pk-midi.wav");
            this.writePcmFile(f, pcm);
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            float vol = this.pkMain / 100f;
            if (vol < 0f) vol = 0f;
            if (vol > 1f) vol = 1f;
            mp.setVolume(vol, vol);
            mp.start();
            this.pkMidiPlayer = mp;
            this.pkMidiSrc = src;
            this.pkMidiPaused = false;
            this.startMixClock();
            this.setNow("Play \u00b7 " + pulsekit.Engine.fileSetMidiName(src));
        } catch (java.lang.Throwable ex) {
            java.lang.String m = ex.getMessage();
            this.setNow(m != null ? m : "Could not play that MIDI");
        }
    }

    private void attachMidiChip(android.widget.LinearLayout host, java.lang.String key) {
        if (host == null || key == null || !key.startsWith("f:")) return;
        java.lang.String src = key.substring(2);
        java.lang.String name = pulsekit.Engine.fileSetMidiName(src);
        if (name.length() == 0) return;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            android.view.View v = host.getChildAt(i);
            if ("sourcemidi".equals(v.getTag())) return;
            if (v instanceof android.widget.TextView && "pack".equals(v.getTag())) break;
        }
        android.widget.LinearLayout line = this.row();
        line.setTag("sourcemidi");
        line.setPadding(this.dp(8), 0, 0, this.dp(4));
        android.widget.TextView label = this.text(name, 12, false);
        label.setTextColor(MUTED);
        line.addView(label, this.flex(1));
        android.widget.TextView clock = this.text("00:00 / 00:00", 11, false);
        clock.setTag("midiclock");
        clock.setTextColor(MUTED);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.addView(clock);
        line.addView(this.outline("Play", false, pulsekit.AnalyzeClicks.filePlayMidi(this, src)));
        line.addView(this.outline("Pause", false, pulsekit.AnalyzeClicks.filePauseMidi(this)));
        line.addView(this.outline("Stop", false, pulsekit.AnalyzeClicks.fileStopMidi(this)));
        host.addView(line);
    }

    public void playCombinedFile(java.lang.String src) {
        this.stopCombinedFile();
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if (au == null || au.combinedWav == null || au.combinedWav.length < 44) {
            this.setNow("That mix is missing");
            return;
        }
        try {
            java.io.File f = new java.io.File(this.getCacheDir(), "pk-mix.wav");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            out.write(au.combinedWav);
            out.close();
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            float vol = this.pkMain / 100f;
            if (vol < 0f) vol = 0f;
            if (vol > 1f) vol = 1f;
            mp.setVolume(vol, vol);
            mp.start();
            this.pkMixPlayer = mp;
            this.startMixClock();
            this.setNow("Main " + this.pkMain + " / Guitar " + this.pkGuitarLevel);
        } catch (java.lang.Exception ex) {
            java.lang.String m = ex.getMessage();
            this.setNow(m != null ? m : "Could not play that mix");
        }
    }

    public void applyMixLevels(java.lang.String src) {
        int pos = 0;
        boolean going = false;
        if (this.pkMixPlayer != null) {
            try {
                pos = this.pkMixPlayer.getCurrentPosition();
                going = this.pkMixPlayer.isPlaying();
            } catch (java.lang.Exception ignored) {}
        }
        short[] drums = this.pkDrumPcm;
        short[] guitar = this.pkGuitarPcm;
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if ((drums == null || guitar == null || src == null || !src.equals(this.pkStemSrc)) && au != null && au.drumWav != null && au.bedWav != null && au.drumWav.length > 44 && au.bedWav.length > 44) {
            try {
                drums = pulsekit.AudioIo.floatsToShorts(pulsekit.AudioIo.parseWav(au.drumWav).samples);
                guitar = pulsekit.AudioIo.floatsToShorts(pulsekit.AudioIo.parseWav(au.bedWav).samples);
                this.pkDrumPcm = drums;
                this.pkGuitarPcm = guitar;
                this.pkStemSrc = src;
            } catch (java.lang.Throwable ignored) {}
        }
        if (drums == null || guitar == null) {
            this.setNow("Combine tracks again, then move Gate, Comp or Limit");
            return;
        }
        try {
            float gMul = this.pkGuitarLevel / 100f * this.pkMain / 100f * 2.6f;
            float dMul = this.pkDrumLevel / 100f * this.pkMain / 100f * 1.15f;
            short[] shaped = pulsekit.AudioIo.shapeGuitar(guitar, gMul, this.pkGate / 100f, this.pkComp / 100f, this.pkLimit / 100f);
            short[] drumsOut = pulsekit.AudioIo.boostLimited(drums, dMul, 0.88f);
            short[] mix = pulsekit.AudioIo.remixStems(drumsOut, shaped, 1f, 1f);
            if (au != null) au.combinedWav = pulsekit.AudioIo.encodeWav(mix, 22050);
            pulsekit.Engine.storeFileSetAudioDir(new java.io.File(this.getFilesDir(), "fset-audio"));
            if (going) {
                this.playCombinedFile(src);
                if (this.pkMixPlayer != null) this.pkMixPlayer.seekTo(pos);
            }
            this.setNow("Main " + this.pkMain + " / Gate " + this.pkGate + " / Comp " + this.pkComp + " / Limit " + this.pkLimit);
        } catch (java.lang.Throwable ex) {
            java.lang.String m = ex.getMessage();
            this.setNow(m != null ? m : "Could not shape the guitar");
        }
    }

    public void saveCombinedFile(java.lang.String src) {
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if (au == null || au.combinedWav == null || au.combinedWav.length < 44) {
            this.setNow("That mix is missing");
            return;
        }
        java.lang.String name = au.combinedName != null && au.combinedName.length() > 0 ? au.combinedName : "combined_track.wav";
        this.pkCombinedExport = au.combinedWav;
        this.pkCombinedName = name;
        android.content.Intent intent = new android.content.Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("audio/wav");
        intent.putExtra("android.intent.extra.TITLE", name);
        this.startActivityForResult(intent, 24);
    }

    private void attachCombinedChip(android.widget.LinearLayout host, java.lang.String key) {
        if (host == null || host.getChildCount() < 2 || key == null || !key.startsWith("f:")) return;
        java.lang.String src = key.substring(2);
        java.lang.String name = pulsekit.Engine.fileSetCombinedName(src);
        if (name.length() == 0) return;
        android.view.View strip = host.getChildAt(host.getChildCount() - 1);
        if (!(strip instanceof android.widget.HorizontalScrollView)) return;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            android.view.View v = host.getChildAt(i);
            if ("combined".equals(v.getTag())) return;
            if (v instanceof android.widget.TextView && "pack".equals(v.getTag())) break;
        }
        android.widget.LinearLayout line = this.row();
        line.setTag("combined");
        line.setPadding(this.dp(8), 0, 0, this.dp(4));
        android.widget.TextView label = this.text(name, 12, false);
        label.setTextColor(MUTED);
        line.addView(label, this.flex(1));
        android.widget.TextView clock = this.text("00:00 / 00:00", 11, false);
        clock.setTag("mixclock");
        clock.setTextColor(MUTED);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.addView(clock);
        line.addView(this.outline("Play", false, pulsekit.AnalyzeClicks.filePlayCombined(this, src)));
        line.addView(this.outline("Stop", false, pulsekit.AnalyzeClicks.fileStopCombined(this)));
        line.addView(this.outline("Save", false, pulsekit.AnalyzeClicks.fileSaveCombined(this, src)));
        android.view.View head = host.getChildAt(host.getChildCount() - 2);
        host.addView(line);
        this.addMixLevels(host, src);
        if (head instanceof android.widget.TextView) {
            android.widget.TextView h = (android.widget.TextView) head;
            java.lang.String t = h.getText() == null ? "" : h.getText().toString();
            int cut = t.lastIndexOf(" \u00b7 ");
            if (cut > 0) {
                try {
                    int n = Integer.parseInt(t.substring(cut + 3).trim());
                    h.setText(t.substring(0, cut) + " \u00b7 " + (n + 1));
                } catch (java.lang.Exception ignored) {}
            }
        }
    }

    private void appendCombinedFileRowsCore() {
        if (this.importedFileList == null) return;
        for (int i = 0; i < this.importedFileList.getChildCount(); i++) {
            android.view.View child = this.importedFileList.getChildAt(i);
            if (!(child instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout row = (android.widget.LinearLayout) child;
            android.widget.TextView packHead = null;
            boolean header = false;
            for (int j = 0; j < row.getChildCount(); j++) {
                android.view.View v = row.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                android.widget.TextView t = (android.widget.TextView) v;
                java.lang.String s = t.getText() == null ? "" : t.getText().toString();
                if ("Export".equals(s)) header = true;
                else if (packHead == null && s.indexOf(" \u00b7 ") >= 0) packHead = t;
            }
            if (!header || packHead == null) continue;
            if (i + 1 >= this.importedFileList.getChildCount()) continue;
            android.view.View next = this.importedFileList.getChildAt(i + 1);
            if (!(next instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout kids = (android.widget.LinearLayout) next;
            boolean nextHeader = false;
            boolean already = false;
            for (int k = 0; k < kids.getChildCount(); k++) {
                android.view.View v = kids.getChildAt(k);
                if ("combined".equals(v.getTag())) already = true;
                if (v instanceof android.widget.TextView && "Export".equals(((android.widget.TextView) v).getText().toString())) nextHeader = true;
            }
            if (nextHeader || already) continue;
            java.lang.String label = packHead.getText() == null ? "" : packHead.getText().toString();
            if (label.startsWith("\u25be ") || label.startsWith("\u25b8 ")) label = label.substring(2);
            int cut = label.lastIndexOf(" \u00b7 ");
            java.lang.String name = cut > 0 ? label.substring(0, cut) : label;
            java.lang.String src = "Other".equals(name) ? "" : name;
            java.lang.String mix = pulsekit.Engine.fileSetCombinedName(src);
            if (mix.length() == 0 && name.length() > 2 && name.charAt(1) == ' ') {
                java.lang.String mark = name.substring(0, 1);
                if ("M".equals(mark) || "A".equals(mark) || "I".equals(mark) || "C".equals(mark)) {
                    src = name.substring(2);
                    if ("Other".equals(src)) src = "";
                    mix = pulsekit.Engine.fileSetCombinedName(src);
                }
            }
            if (mix.length() == 0) continue;
            android.widget.LinearLayout line = this.row();
            line.setTag("combined");
            android.widget.TextView nameView = this.text(mix, 12, false);
            nameView.setTextColor(MUTED);
            line.addView(nameView, this.flex(1));
            android.widget.TextView clock = this.text("00:00 / 00:00", 11, false);
            clock.setTag("mixclock");
            clock.setTextColor(MUTED);
            clock.setTypeface(android.graphics.Typeface.MONOSPACE);
            line.addView(clock);
            line.addView(this.outline("Play", false, pulsekit.AnalyzeClicks.filePlayCombined(this, src)));
            line.addView(this.outline("Stop", false, pulsekit.AnalyzeClicks.fileStopCombined(this)));
            line.addView(this.outline("Save", false, pulsekit.AnalyzeClicks.fileSaveCombined(this, src)));
            kids.addView(line);
            this.addMixLevels(kids, src);
            java.lang.String head = packHead.getText() == null ? "" : packHead.getText().toString();
            int hc = head.lastIndexOf(" \u00b7 ");
            if (hc > 0) {
                try {
                    int n = Integer.parseInt(head.substring(hc + 3).trim());
                    packHead.setText(head.substring(0, hc) + " \u00b7 " + (n + 1));
                } catch (java.lang.Exception ignored) {}
            }
        }
    }

    private void stampPackMark(android.widget.TextView pack, java.lang.String source) {
        if (pack == null || source == null) return;
        java.lang.String mark = pulsekit.Engine.fileSetOriginMark(pulsekit.Engine.fileSetOriginOf(source));
        if (mark.length() == 0) return;
        java.lang.CharSequence cs = pack.getText();
        java.lang.String raw = cs == null ? "" : cs.toString();
        boolean arrow = raw.startsWith("\u25be ") || raw.startsWith("\u25b8 ");
        java.lang.String rest = arrow ? raw.substring(2) : raw;
        if (rest.startsWith(mark + " ")) return;
        pack.setText((arrow ? raw.substring(0, 2) : "") + mark + " " + rest);
    }

    private void attachFileSetInfoMenu(android.widget.LinearLayout host, java.lang.String key, java.lang.String label, java.lang.Runnable onDelete, java.lang.Runnable onExport) {
        if (host == null) return;
        android.widget.TextView pack = null;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            android.view.View v = host.getChildAt(i);
            if (v instanceof android.widget.TextView && "pack".equals(v.getTag())) {
                pack = (android.widget.TextView) v;
                break;
            }
        }
        if (pack == null) return;
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        this.stampPackMark(pack, src);
        pack.setOnLongClickListener(pulsekit.AnalyzeClicks.packMenu(this, key, label, onDelete, onExport));
        this.attachCombinedChip(host, key);
        this.attachMidiChip(host, key);
    }

    private void wireFileSetListActionsCore() {
        if (this.importedFileList == null) return;
        for (int i = 0; i < this.importedFileList.getChildCount(); i++) {
            android.view.View child = this.importedFileList.getChildAt(i);
            if (!(child instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout row = (android.widget.LinearLayout) child;
            android.widget.TextView exportBtn = null;
            android.widget.TextView packHead = null;
            boolean hasMake = false;
            for (int j = 0; j < row.getChildCount(); j++) {
                android.view.View v = row.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                android.widget.TextView t = (android.widget.TextView) v;
                java.lang.CharSequence cs = t.getText();
                java.lang.String s = cs == null ? "" : cs.toString();
                if ("Export".equals(s)) exportBtn = t;
                else if ("Make song".equals(s)) hasMake = true;
                else if (s.indexOf(" \u00b7 ") >= 0) packHead = t;
            }
            if (exportBtn == null || packHead == null || hasMake) continue;
            java.lang.String head = packHead.getText().toString();
            if (head.startsWith("\u25be ") || head.startsWith("\u25b8 ")) head = head.substring(2);
            int cut = head.lastIndexOf(" \u00b7 ");
            java.lang.String label = cut > 0 ? head.substring(0, cut) : head;
            java.lang.String src = "Other".equals(label) ? "" : label;
            java.lang.String key = src.length() == 0 ? "o:other" : "f:" + src;
            this.stampPackMark(packHead, src);
            android.widget.TextView make = this.outline("Make song", false, pulsekit.AnalyzeClicks.fileMakeSong(this, key, label));
            row.addView(make, row.indexOfChild(exportBtn));
            if (pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src))) {
                android.widget.TextView style = this.outline("Change style", false, pulsekit.AnalyzeClicks.fileChangeStyle(this, key, label));
                row.addView(style, row.indexOfChild(exportBtn));
            }
            if ("compose".equals(pulsekit.Engine.fileSetOriginOf(src))) {
                android.widget.TextView combine = this.outline("Combine tracks", false, pulsekit.AnalyzeClicks.fileCombine(this, key, label));
                row.addView(combine, row.indexOfChild(exportBtn));
            }
        }
        this.appendCombinedFileRows();
    }

    private void pkRebindFileSetClicks() {
        if (this.importedFileList == null) return;
        android.view.View.OnClickListener click = pulsekit.AnalyzeClicks.fileEntry(this);
        java.lang.String src = "";
        for (int i = 0; i < this.importedFileList.getChildCount(); i++) {
            android.view.View child = this.importedFileList.getChildAt(i);
            if (!(child instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout box = (android.widget.LinearLayout) child;
            boolean header = false;
            android.widget.TextView packHead = null;
            for (int j = 0; j < box.getChildCount(); j++) {
                android.view.View v = box.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                if ("Export".equals(s)) header = true;
                if (s.startsWith("\u25be ") || s.startsWith("\u25b8 ")) packHead = (android.widget.TextView) v;
            }
            if (header) {
                src = "";
                if (packHead != null && packHead.getText() != null) {
                    java.lang.String head = packHead.getText().toString();
                    if (head.startsWith("\u25be ") || head.startsWith("\u25b8 ")) head = head.substring(2);
                    int cut = head.lastIndexOf(" \u00b7 ");
                    java.lang.String label = cut > 0 ? head.substring(0, cut) : head;
                    if (label.startsWith("EP ")) label = label.substring(3);
                    else if (label.startsWith("A ") || label.startsWith("M ") || label.startsWith("I ") || label.startsWith("C ")) label = label.substring(2);
                    src = "Other".equals(label) ? "" : label;
                }
                continue;
            }
            int pi = 0;
            int fi = 0;
            for (int j = 0; j < box.getChildCount(); j++) {
                android.view.View v = box.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                if (s.startsWith("Pattern")) {
                    while (pi < this.learned.size()) {
                        pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) this.learned.get(pi);
                        pi = pi + 1;
                        if (item != null && src.equals(pulsekit.Engine.sourceOf(item))) {
                            v.setTag("fp:" + item.id);
                            v.setOnClickListener(click);
                            v.setClickable(true);
                            break;
                        }
                    }
                } else if (s.startsWith("Fill ") || s.startsWith("Fill\u00b7")) {
                    while (fi < this.learnedFills.size()) {
                        pulsekit.Engine.LearnedFill item = (pulsekit.Engine.LearnedFill) this.learnedFills.get(fi);
                        fi = fi + 1;
                        if (item != null && src.equals(pulsekit.Engine.sourceOf(item))) {
                            v.setTag("ff:" + item.id);
                            v.setOnClickListener(click);
                            v.setClickable(true);
                            break;
                        }
                    }
                }
            }
            for (int n = 0; n < this.learned.size(); n++) {
                pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) this.learned.get(n);
                if (item == null || !src.equals(pulsekit.Engine.sourceOf(item))) continue;
                java.lang.String pair = (java.lang.String) this.fillernPairs.get("l:" + item.id);
                if (pair == null || pair.length() == 0) continue;
                android.widget.TextView t = this.text("Fillern \u00b7 " + item.name, 13, false);
                t.setTextColor(MUTED);
                t.setPadding(0, this.dp(4), 0, this.dp(4));
                t.setTag("fr:" + item.id);
                t.setOnClickListener(click);
                t.setClickable(true);
                box.addView(t);
            }
        }
    }

    public void pkLoadFilePattern(java.lang.String id) {
        if (id == null) return;
        for (int i = 0; i < this.learned.size(); i++) {
            pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) this.learned.get(i);
            if (item == null || !id.equals(item.id)) continue;
            this.styles.put(item.id, new pulsekit.Engine.Style(item.id, item.name, item.bpm, pulsekit.Engine.rowsFromCells(item.cells)));
            this.loadStyle(item.id, false);
            if (item.tsNum > 0) {
                this.tsNum = pulsekit.Engine.clampTsNum(item.tsNum);
                this.tsDen = pulsekit.Engine.clampTsDen(item.tsDen > 0 ? item.tsDen : 4);
                if (this.tsNumField != null) this.tsNumField.setText(java.lang.Integer.toString(this.tsNum));
                if (this.tsDenField != null) this.tsDenField.setText(java.lang.Integer.toString(this.tsDen));
            }
            int steps = item.steps > 0 ? item.steps : pulsekit.Engine.patternLenFromCells(item.cells, this.tsNum, this.tsDen);
            pulsekit.Engine.defaultAccents(this.accents, steps, pulsekit.Engine.stepsPerBeat(this.tsDen));
            this.applySteps(steps, false);
            this.show("pattern");
            this.refreshGrid();
            this.setNow(item.name);
            return;
        }
    }

    public void pkLoadFileFill(java.lang.String id) {
        if (id == null) return;
        this.applyFill("l:" + id);
        this.show("fills");
        this.refreshGrid();
    }

    public void pkLoadFileFillern(java.lang.String id) {
        if (id == null) return;
        this.pkLoadFilePattern(id);
        this.show("combo");
        this.refreshGrid();
        this.setNow("Fillern \u00b7 " + this.patternName(id));
    }

    private void showFileSetInfo(java.lang.String label, java.util.List parts, pulsekit.Engine.FileSet set) {
        if (this.infoRows == null) return;
        this.infoRows.removeAllViews();
        if (this.infoTitle != null) this.infoTitle.setText(label == null || label.length() == 0 ? "File set" : label);
        int nFill = set == null ? 0 : set.fills.size();
        int nPair = set == null ? 0 : set.fillerns.size();
        int nPart = parts == null ? 0 : parts.size();
        if (this.infoStatus != null) {
            java.lang.String style = "";
            if (parts != null && !parts.isEmpty()) {
                pulsekit.Engine.FileSetPart first = (pulsekit.Engine.FileSetPart) parts.get(0);
                if (first.styleLabel != null && first.styleLabel.length() > 0) style = first.styleLabel + " · ";
            }
            this.infoStatus.setText(style + (nPart > 0 ? ((pulsekit.Engine.FileSetPart) parts.get(0)).bpm + " BPM · " : "") + nPart + " parts · " + pulsekit.Engine.fileSetLengthLine(parts, set == null ? 0f : set.durationSec) + " · " + nFill + " fills · " + nPair + " Fillerns");
        }
        if (parts != null) {
            for (int i = 0; i < parts.size(); i++) {
                pulsekit.Engine.FileSetPart p = (pulsekit.Engine.FileSetPart) parts.get(i);
                android.widget.LinearLayout card = this.col();
                card.setBackground(this.round(ELEV, 10));
                card.setPadding(this.dp(10), this.dp(8), this.dp(10), this.dp(8));
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, this.dp(8));
                card.setLayoutParams(lp);
                android.widget.LinearLayout head = this.row();
                head.addView(this.text(p.name, 14, true), this.flex(1));
                android.widget.TextView kind = this.text(p.kind, 11, true);
                kind.setTextColor(SUBTLE);
                head.addView(kind);
                card.addView(head);
                card.addView(this.text("BPM " + p.bpm + " · TS " + p.tsNum + "/" + p.tsDen + " · " + (p.styleLabel == null ? "" : p.styleLabel), 12, false));
                android.widget.TextView more = this.text(p.bars + " bars · " + this.fmtAnalyzeTime(p.startSec) + "–" + this.fmtAnalyzeTime(p.endSec) + " · " + p.hits + " hits · swing " + p.swing, 11, false);
                more.setTextColor(SUBTLE);
                card.addView(more);
                this.infoRows.addView(card);
            }
        }
        this.infoRows.requestLayout();
        this.setNow(nPart + " parts");
    }

    private void openFileSetInfoCore(java.lang.String key, java.lang.String label) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        this.ensureFsetInfoMap();
        java.util.List parts = (java.util.List) this.fsetInfoMap.get(src);
        pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, label, this.learned, this.learnedFills, this.fillernPairs);
        if (parts == null || parts.isEmpty()) {
            if (set != null && set.parts != null && !set.parts.isEmpty()) parts = set.parts;
            else parts = pulsekit.Engine.partsForDisplay(set);
        }
        if (set != null && "compose".equals(set.origin) && parts != null && !parts.isEmpty()) {
            float fileSec = set.durationSec;
            pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
            if (au != null && au.sourceWav != null && au.sourceWav.length >= 44) {
                try {
                    pulsekit.AudioIo.Pcm guitar = pulsekit.AudioIo.parseWav(au.sourceWav);
                    if (guitar.sr > 0 && guitar.samples.length > 0) fileSec = guitar.samples.length / (float) guitar.sr;
                } catch (Exception ignored) {}
            }
            if (fileSec > 0.05f) {
                pulsekit.Engine.fitPartsExact(parts, fileSec);
                set.durationSec = fileSec;
            }
        }
        pulsekit.Engine.unifyFileSetParts(parts, set);
        if (parts != null && !parts.isEmpty()) this.storeFsetParts(src, parts);
        if (!"fsetinfo".equals(this.view)) this.pkInfoBack = this.view;
        java.lang.String shown = pulsekit.Engine.fileSetMarked(label == null || label.length() == 0 ? "File set" : label, pulsekit.Engine.fileSetOriginOf(src));
        this.showFileSetInfo(shown, parts, set);
        java.lang.String word = pulsekit.Engine.fileSetOriginTitle(pulsekit.Engine.fileSetOriginOf(src));
        if (word.length() > 0 && this.infoStatus != null) {
            java.lang.String cur = this.infoStatus.getText().toString();
            if (!cur.startsWith(word)) this.infoStatus.setText(word + " · " + cur);
        }
        this.show("fsetinfo");
    }

    private void wireInfoPane() {
        this.infoPane = this.col();
        this.infoPane.setVisibility(8);
        this.infoPane.setBackgroundColor(BG);
        this.infoPane.setClickable(true);
        this.infoPane.setPadding(this.dp(16), this.dp(12), this.dp(16), this.dp(12));
        this.infoTitle = this.text("File set", 18, true);
        this.infoPane.addView(this.infoTitle);
        this.infoStatus = this.text("Hold a file set and choose Info.", 14, false);
        this.infoStatus.setTextColor(MUTED);
        this.infoStatus.setPadding(0, this.dp(8), 0, this.dp(8));
        this.infoPane.addView(this.infoStatus);
        android.widget.LinearLayout.LayoutParams closeLp = new android.widget.LinearLayout.LayoutParams(-1, this.dp(48));
        closeLp.setMargins(0, 0, 0, this.dp(8));
        android.widget.TextView close = this.action("Close", SURFACE, FG, pulsekit.AnalyzeClicks.closeInfo(this));
        this.infoPane.addView(close, closeLp);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        this.infoRows = this.col();
        scroll.addView(this.infoRows);
        android.widget.LinearLayout.LayoutParams lp = this.flexFill();
        lp.setMargins(0, this.dp(8), 0, 0);
        this.infoPane.addView(scroll, lp);
        android.view.ViewGroup host = null;
        if (this.analyzePane != null && this.analyzePane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.analyzePane.getParent();
        } else if (this.isolatePane != null && this.isolatePane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.isolatePane.getParent();
        } else if (this.importPane != null && this.importPane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) this.importPane.getParent();
        }
        if (host != null) {
            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(-1, -1);
            host.addView((android.view.View) this.infoPane, (android.view.ViewGroup.LayoutParams) flp);
        }
    }

    private boolean packIsolationFileSet(pulsekit.AudioIo.Analysis a, java.lang.String file, boolean anyPad) {
        try {
            if (a == null || a.pcm == null || a.pcm.length < pulsekit.AudioIo.SR * 2) return false;
            pulsekit.AudioIo.PartAnalysis parts = pulsekit.AudioIo.analyzeParts(a.pcm, pulsekit.AudioIo.SR);
            java.lang.String stem = pulsekit.Engine.uniqueImportSource(pulsekit.Engine.stemNameFromMidi(file), this.learned, this.learnedFills);
            pulsekit.Engine.FileSet set = pulsekit.AudioIo.fileSetFromParts(parts, stem, "isolate");
            if (set.patterns.isEmpty() && set.fills.isEmpty()) return false;
            this.storeFsetParts(stem, set.parts);
            byte[] packed = pulsekit.Engine.encodeFset(set);
            this.loadFset(packed, pulsekit.Engine.fsetFilename(set.name));
            if (anyPad) this.show("pads");
            return true;
        } catch (java.lang.Exception ex) {
            java.lang.String m = ex.getMessage();
            this.setNow(m != null ? m : "Could not build a file set");
            return false;
        }
    }

    private boolean tryPackIsolation() {
        if (this.isolateAnalysis == null || this.isolateAnalysis.pcm == null) return false;
        pulsekit.AudioIo.Analysis a = this.isolateAnalysis;
        java.lang.String file = this.isolateFile != null ? this.isolateFile : "song";
        this.rememberIsoPads();
        boolean anyPad = false;
        java.util.Iterator it = a.pads.iterator();
        while (it.hasNext()) {
            pulsekit.AudioIo.PadIso p = (pulsekit.AudioIo.PadIso) it.next();
            if (p.found && p.sample != null) anyPad = true;
        }
        if (!anyPad && (a.kickSample != null || a.snareSample != null)) anyPad = true;
        if (this.isoPadMode == ISO_PADS_SKIP) anyPad = false;
        if (anyPad) this.addIsolatedSet(pulsekit.Engine.stemNameFromMidi(file), a.pads);
        boolean packed = this.packIsolationFileSet(a, file, anyPad);
        this.isolateAnalysis = null;
        this.isolateFile = null;
        if (packed) return true;
        if (anyPad) this.show("pads");
        else this.applyAnalysis(a, file);
        return true;
    }

    public void closeFileSetInfo() {
        java.lang.String back = this.pkInfoBack;
        if (back == null || back.length() == 0 || "fsetinfo".equals(back)) back = "import";
        this.show(back);
    }

    public void makeFileSetSong(java.lang.String key, java.lang.String label) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, label, this.learned, this.learnedFills, this.fillernPairs);
        if (set == null) {
            this.setNow("That file set is empty");
            return;
        }
        this.ensureFsetInfoMap();
        java.util.List stored = (java.util.List) this.fsetInfoMap.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        java.util.List song = pulsekit.Engine.songFromFileSet(set);
        if (song == null || song.isEmpty()) {
            this.setNow("Could not make a song from that file set");
            return;
        }
        java.lang.String name = label;
        if (name == null || name.length() == 0) name = src.length() == 0 ? "Import" : src;
        this.addImportedArrangement(name, song);
    }

    public java.lang.String[] styleDbNames() {
        java.util.List rows = pulsekit.StyleDb.rows();
        java.lang.String[] names = new java.lang.String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            pulsekit.StyleDb.Row row = (pulsekit.StyleDb.Row) rows.get(i);
            names[i] = row.name + "  \u00b7  " + row.bpm;
        }
        return names;
    }

    public int currentStyleDbIndex(java.lang.String key) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        this.ensureFsetInfoMap();
        java.util.List stored = (java.util.List) this.fsetInfoMap.get(src);
        if (stored == null || stored.isEmpty()) {
            pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, "", this.learned, this.learnedFills, this.fillernPairs);
            if (set != null) stored = set.parts;
        }
        if (stored == null) return -1;
        for (int i = 0; i < stored.size(); i++) {
            pulsekit.Engine.FileSetPart p = (pulsekit.Engine.FileSetPart) stored.get(i);
            if (p != null && p.styleLabel != null && p.styleLabel.length() > 0) return pulsekit.StyleDb.indexOf(p.styleLabel);
        }
        return -1;
    }

    public void applyFileSetStyle(java.lang.String key, java.lang.String label, java.lang.String kit, java.lang.String styleName, int hats, float four, float dkick, int styleBpm, float styleBack) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        if (!pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src))) {
            this.setNow("Using the source file");
            return;
        }
        java.lang.String shown = label;
        if (shown == null || shown.length() == 0) shown = src.length() == 0 ? "Import" : src;
        pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, shown, this.learned, this.learnedFills, this.fillernPairs);
        if (set == null) {
            this.setNow("That file set is empty");
            return;
        }
        this.ensureFsetInfoMap();
        java.util.List stored = (java.util.List) this.fsetInfoMap.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        set.origin = pulsekit.Engine.fileSetOriginOf(src);
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if (au != null) set.sourceWav = au.sourceWav;
        java.lang.String back = this.view;
        pulsekit.Engine.FileSet next = pulsekit.AudioIo.restyleFileSet(set, kit, styleName, hats, four, dkick, styleBpm, styleBack);
        pulsekit.Engine.replaceFileSetLearned(src, next, this.learned, this.learnedFills, this.fillernPairs);
        if (next.parts != null && !next.parts.isEmpty()) this.storeFsetParts(src, next.parts);
        if (pulsekit.Engine.isFileSetOrigin(set.origin)) pulsekit.Engine.rememberFileSetOrigin(src, set.origin);
        this.persistLearned();
        pulsekit.Engine.ImportedSong made = pulsekit.Engine.fileSetSongMade(shown, this.importedSongs);
        if (made != null) this.importedSongs.remove(made);
        this.makeFileSetSong(key, shown);
        this.rebuildImported();
        if (back != null) this.show(back);
        this.setNow("Style \u00b7 " + styleName);
    }

    public void combineFileSetTracks(java.lang.String key, java.lang.String label) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        if (!"compose".equals(pulsekit.Engine.fileSetOriginOf(src))) {
            this.setNow("Combine tracks is for a C file set");
            return;
        }
        java.lang.String songLabel = label;
        if (songLabel == null || songLabel.length() == 0) songLabel = src.length() == 0 ? "Import" : src;
        pulsekit.Engine.ImportedSong made = pulsekit.Engine.fileSetSongMade(songLabel, this.importedSongs);
        boolean existed = made != null && made.parts != null && !made.parts.isEmpty();
        if (!existed) this.makeFileSetSong(key, label);
        made = pulsekit.Engine.fileSetSongMade(songLabel, this.importedSongs);
        if (made == null || made.parts == null || made.parts.isEmpty()) {
            this.setNow("Could not make a song from that file set");
            return;
        }
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if (au == null || au.sourceWav == null || au.sourceWav.length < 44) {
            this.setNow("Original guitar track is missing");
            return;
        }
        try {
            this.ensureAudio();
            pulsekit.AudioIo.Pcm guitar = pulsekit.AudioIo.parseWav(au.sourceWav);
            float fileSec = guitar.sr > 0 ? guitar.samples.length / (float) guitar.sr : 0f;
            this.ensureFsetInfoMap();
            java.util.List stored = (java.util.List) this.fsetInfoMap.get(src);
            if (stored != null && !stored.isEmpty() && fileSec > 0.05f) {
                pulsekit.Engine.fitPartsExact(stored, fileSec);
                this.storeFsetParts(src, stored);
            }
            pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, label, this.learned, this.learnedFills, this.fillernPairs);
            pulsekit.AudioIo.PartAnalysis fresh = pulsekit.AudioIo.analyzeParts(guitar.samples, guitar.sr);
            pulsekit.Engine.FileSet sourceSet = pulsekit.AudioIo.fileSetFromParts(fresh, songLabel, "compose");
            if (sourceSet != null) pulsekit.AudioIo.stampGuitarIfEmpty(sourceSet, guitar.samples, guitar.sr);
            if (sourceSet != null && sourceSet.patterns != null && !sourceSet.patterns.isEmpty()) {
                sourceSet.origin = "compose";
                if (sourceSet.parts != null && fileSec > 0.05f) pulsekit.Engine.fitPartsExact(sourceSet.parts, fileSec);
                pulsekit.Engine.replaceFileSetLearned(src, sourceSet, this.learned, this.learnedFills, this.fillernPairs);
                if (sourceSet.parts != null && !sourceSet.parts.isEmpty()) {
                    this.storeFsetParts(src, sourceSet.parts);
                    stored = sourceSet.parts;
                }
                set = sourceSet;
            }
            boolean fitStyle = false;
            pulsekit.AudioIo.GuitarFit fit = pulsekit.AudioIo.fitGuitar(guitar.samples, guitar.sr, stored);
            float[] heard = guitar.samples;
            if (fitStyle && set != null && fit.cells != null && fit.cells.length > 0 && fit.cells[0] != null) {
                for (int pi = 0; pi < set.patterns.size(); pi++) {
                    pulsekit.Engine.Learned learned = (pulsekit.Engine.Learned) set.patterns.get(pi);
                    if (learned == null) continue;
                    int[][] cells = new int[fit.cells.length][];
                    for (int t = 0; t < fit.cells.length; t++) {
                        int[] row = fit.cells[t];
                        if (row == null) {
                            cells[t] = null;
                        } else {
                            int[] copy = new int[row.length];
                            for (int k = 0; k < row.length; k++) copy[k] = row[k];
                            cells[t] = copy;
                        }
                    }
                    learned.cells = cells;
                    learned.bpm = fit.bpm;
                    learned.tsNum = fit.tsNum;
                    learned.tsDen = fit.tsDen;
                    learned.swing = fit.swing;
                    learned.human = 0;
                    learned.closest = "folk";
                    learned.steps = fit.cells[0].length;
                }
                java.util.List rows = stored != null && !stored.isEmpty() ? stored : set.parts;
                for (int ri = 0; rows != null && ri < rows.size(); ri++) {
                    pulsekit.Engine.FileSetPart row = (pulsekit.Engine.FileSetPart) rows.get(ri);
                    if (row == null) continue;
                    row.styleLabel = "Folk";
                    row.bpm = fit.bpm;
                    row.tsNum = fit.tsNum;
                    row.tsDen = fit.tsDen;
                    row.swing = fit.swing;
                }
                if (stored != null) this.storeFsetParts(src, stored);
            }
            float[] wallS = null;
            float[] wallE = null;
            if (stored != null && !stored.isEmpty()) {
                wallS = new float[stored.size()];
                wallE = new float[stored.size()];
                for (int i = 0; i < stored.size(); i++) {
                    pulsekit.Engine.FileSetPart wall = (pulsekit.Engine.FileSetPart) stored.get(i);
                    if (wall == null) continue;
                    wallS[i] = wall.startSec;
                    wallE[i] = wall.endSec;
                }
            }
            if (set != null) pulsekit.AudioIo.stampGuitarFeel(set, stored != null && !stored.isEmpty() ? stored : set.parts, guitar.samples, guitar.sr);
            java.util.ArrayList secs = new java.util.ArrayList();
            java.util.List aligned = made.parts;
            if (set != null) {
                if (stored != null && stored != set.parts) {
                    set.parts.clear();
                    set.parts.addAll(stored);
                }
                set.durationSec = fileSec;
                set.origin = "compose";
                aligned = pulsekit.Engine.songFromFileSet(set, secs);
            }
            if (stored != null && wallS != null) {
                for (int i = 0; i < stored.size() && i < wallS.length; i++) {
                    pulsekit.Engine.FileSetPart wall = (pulsekit.Engine.FileSetPart) stored.get(i);
                    if (wall == null) continue;
                    wall.startSec = wallS[i];
                    wall.endSec = wallE[i];
                }
            }
            if (set != null) pulsekit.AudioIo.stampGuitarFeel(set, stored != null && !stored.isEmpty() ? stored : set.parts, guitar.samples, guitar.sr);
            if (set != null && aligned != null && aligned != made.parts && !aligned.isEmpty()) {
                made.parts.clear();
                made.parts.addAll(aligned);
                this.persistLearned();
            }
            java.util.List clockParts = stored != null && !stored.isEmpty() ? stored : (set == null ? null : set.parts);
            short[] drums;
            if (set == null) drums = pulsekit.AudioIo.mixSongOnFile(aligned, secs, null, this.mixVoices(), 22050, fileSec);
            else if (fitStyle) drums = pulsekit.AudioIo.renderGuitarFit(fit, this.mixVoices());
            else drums = pulsekit.AudioIo.mixFileSetOnGuitar(set, clockParts, heard, guitar.sr, this.mixVoices());
            short[] mix;
            pulsekit.AudioIo.MixOut mixed = pulsekit.AudioIo.combineMix(drums, 22050, heard, guitar.sr, fit.beats, this.pkDrumLevel / 100f, this.pkGuitarLevel / 100f);
            mix = mixed.mix;
            java.lang.String name = pulsekit.Engine.combinedAudioName(made.name != null ? made.name : songLabel);
            pulsekit.Engine.rememberFileSetAudio(src, au.sourceWav, pulsekit.AudioIo.encodeWav(mix, 22050), name);
            pulsekit.Engine.rememberMixStems(src, pulsekit.AudioIo.encodeWav(mixed.drums, 22050), pulsekit.AudioIo.encodeWav(mixed.guitar, 22050));
            this.pkDrumPcm = mixed.drums;
            this.pkGuitarPcm = mixed.guitar;
            this.pkStemSrc = src;
            pulsekit.Engine.storeFileSetAudioDir(new java.io.File(this.getFilesDir(), "fset-audio"));
            if (key != null) this.openPacks.put(key, java.lang.Boolean.TRUE);
            this.rebuildImported();
            this.rebuildImportedFills();
            java.lang.String extra = "";
            this.setNow("Combined · " + name + extra + " · v122 · source" + (existed ? "" : " · song saved"));
        } catch (java.lang.Throwable ex) {
            java.lang.String m = ex instanceof java.lang.OutOfMemoryError
                ? "That take is too long to mix on this phone"
                : ex.getMessage();
            this.setNow(m != null ? m : "Could not combine those tracks");
        }
    }

    public void applyStylePick(java.lang.String key, java.lang.String label, int index) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        if (!pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src))) {
            this.setNow("Using the source file");
            return;
        }
        java.util.List rows = pulsekit.StyleDb.rows();
        if (index < 0 || index >= rows.size()) return;
        pulsekit.StyleDb.Row row = (pulsekit.StyleDb.Row) rows.get(index);
        this.applyFileSetStyle(key, label, row.kit, row.name, row.hats, row.four, row.dkick, row.bpm, row.back);
        if ("compose".equals(pulsekit.Engine.fileSetOriginOf(src))) {
            this.pkSkipGuitarFit = true;
            this.combineFileSetTracks(key, label);
            this.pkSkipGuitarFit = false;
        }
        this.setNow("Style \u00b7 " + row.name);
    }

    public boolean fileSetIsCompose(java.lang.String key) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        return "compose".equals(pulsekit.Engine.fileSetOriginOf(src));
    }

    public boolean fileSetStyleOn(java.lang.String key) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        return pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src));
    }

    public void addImportedMidiSong(java.lang.String name, java.util.List parts) {
        this.addImportedArrangement(name, parts);
    }

    private boolean tryPackMidiFileSet(java.lang.String filename, pulsekit.Engine.MidiBars bars, boolean force) {
        if (bars == null || bars.bars == null || bars.bars.isEmpty()) return false;
        java.util.List segs = pulsekit.Engine.segmentMidiBars(bars.bars);
        if (segs == null || segs.isEmpty()) return false;
        java.util.LinkedHashSet uniqueG = new java.util.LinkedHashSet();
        java.util.LinkedHashSet uniqueF = new java.util.LinkedHashSet();
        int nFillern = 0;
        for (int i = 0; i < segs.size(); i++) {
            pulsekit.Engine.MidiSeg s = (pulsekit.Engine.MidiSeg) segs.get(i);
            uniqueG.add(pulsekit.Engine.patternSignature(s.groove));
            if (s.fill != null) uniqueF.add(pulsekit.Engine.patternSignature(s.fill));
            if ("fillern".equals(s.kind)) nFillern++;
        }
        boolean multi = bars.bars.size() >= 3 && (segs.size() > 1 || uniqueG.size() > 1 || uniqueF.size() > 1 || (nFillern > 0 && bars.bars.size() >= 4));
        if (!force && !multi) return false;
        try {
            java.lang.String stem = pulsekit.Engine.uniqueImportSource(pulsekit.Engine.stemNameFromMidi(filename), this.learned, this.learnedFills);
            pulsekit.Engine.FileSet set = pulsekit.AudioIo.fileSetFromMidi(bars, stem);
            pulsekit.Engine.attachStagedMidi(set);
            if (set.patterns.isEmpty() && set.fills.isEmpty()) return false;
            this.storeFsetParts(stem, set.parts);
            byte[] packed = pulsekit.Engine.encodeFset(set);
            this.loadFset(packed, pulsekit.Engine.fsetFilename(set.name));
            java.util.ArrayList song = new java.util.ArrayList();
            for (int i = 0; i < segs.size(); i++) {
                if (song.size() >= pulsekit.Engine.MAX_SONG) break;
                pulsekit.Engine.MidiSeg s = (pulsekit.Engine.MidiSeg) segs.get(i);
                song.add(pulsekit.Engine.groove(stem, bars.bpm, s.groove, s.grooveRepeats));
                if (s.fill != null && song.size() < pulsekit.Engine.MAX_SONG) {
                    song.add(pulsekit.Engine.fill("fill", bars.bpm, s.fill, 1));
                }
            }
            if (!song.isEmpty()) {
                new android.app.AlertDialog.Builder(this)
                    .setTitle((java.lang.CharSequence) "Make a song?")
                    .setMessage((java.lang.CharSequence) (set.patterns.size() + " patterns · " + set.fills.size() + " fills. It goes under Imported. Your original song stays."))
                    .setPositiveButton((java.lang.CharSequence) "Make song", pulsekit.AnalyzeClicks.makeSong(this, stem, song))
                    .setNegativeButton((java.lang.CharSequence) "Not now", null)
                    .show();
            }
            return true;
        } catch (java.lang.Exception ex) {
            java.lang.String m = ex.getMessage();
            this.setNow(m != null ? m : "Could not build a file set");
            return false;
        }
    }

    private void afterRefreshSong() {
        if (!"song".equals(this.view)) return;
        java.util.List parts = this.activeSong();
        if ("imported".equals(this.songLane) && this.importedSongs.isEmpty()) {
            this.setNow("No imported song yet");
            return;
        }
        int idx = this.songPlay ? this.songIndex : -1;
        int loop = this.songPlay ? this.songLoop : 0;
        int step = this.playhead < 0 ? 0 : this.playhead;
        int global = pulsekit.Engine.songGlobalStep(parts, idx, loop, step);
        this.setNow(pulsekit.Engine.songNowLine(parts, this.songPlay, idx, global));
    }

    private void afterPartCard(android.widget.LinearLayout card, boolean now) {
        if (!now || card == null) return;
        android.widget.TextView lab = this.text("Now", 10, true);
        lab.setTextColor(HIT);
        int at = card.getChildCount() > 2 ? 2 : card.getChildCount();
        card.addView((android.view.View) lab, at);
    }

    private void pkPaintRun(android.widget.TextView button, String mode) {
        if (button == null) return;
        boolean on = mode.equals(this.pkPromptRun);
        button.setBackgroundColor(on ? HIT : ELEV);
        button.setTextColor(on ? BG : FG);
    }

    public void pkSetPromptRun(String mode) {
        String m = mode == null ? "" : pulsekit.PromptRun.normalizeType(mode);
        if (m.length() == 0 && mode != null && "ai".equals(mode.toLowerCase())) m = "ai";
        if (m.length() == 0) m = "bash";
        this.pkPromptRun = m;
        this.pkPaintRun(this.pkRunBash, "bash");
        this.pkPaintRun(this.pkRunCmd, "cmd");
        this.pkPaintRun(this.pkRunAi, "ai");
        if (this.pyName == null || !this.pyName.toLowerCase().endsWith(".prompt")) return;
        String report = pulsekit.PromptRun.report(this.pkPromptCategory, m, pulsekit.Subsystem.grok(this), pulsekit.Subsystem.sogni(this), pulsekit.Subsystem.claude(this));
        this.pkPromptReport = report;
        if (this.pkPyHint != null) this.pkPyHint.setText(report);
        if (this.pkPyLog != null) this.pkPyLog.setText(report);
        this.setNow(report.replace('\n', ' '));
    }

    private void pkShowPromptModes() {
        if (this.pkPromptModes == null) return;
        boolean prompt = this.pyName != null && this.pyName.toLowerCase().endsWith(".prompt");
        this.pkPromptModes.setVisibility(prompt ? 0 : 8);
    }

    public void pkLoadRefs(String full) {
        try {
            String text = full == null ? this.pkPromptSource : full;
            if (text == null) text = "";
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
            String title = sheet != null && sheet.name != null ? sheet.name : "";
            if (title.length() == 0 && this.pyName != null) {
                title = this.pyName;
                if (title.toLowerCase().endsWith(".prompt")) title = title.substring(0, title.length() - 7);
            }
            String category = sheet != null && sheet.category != null && sheet.category.length() > 0 ? sheet.category : this.pkPromptCategory;
            String n1 = sheet != null && sheet.ref1 != null ? sheet.ref1 : "";
            String n2 = sheet != null && sheet.ref2 != null ? sheet.ref2 : "";
            pulsekit.PromptFiles.Saved saved = pulsekit.PromptFiles.write(this, title, category, n1, n2);
            this.pkRef1Path = saved.ref1Path;
            this.pkRef2Path = saved.ref2Path;
            this.pkPromptDescription = saved.description == null ? "" : saved.description;
            this.pkDefinedResult = saved.resultName == null ? "" : saved.resultName;
            if (this.pkPromptDescription.length() == 0 && sheet != null && sheet.description != null) this.pkPromptDescription = sheet.description;
            if (this.pyEditor != null) {
                String now = this.pyEditor.getText().toString();
                String next = pulsekit.PromptRun.withoutDescription(this.pkPromptDescription, now);
                if (!now.equals(next)) this.pyEditor.setText(next);
            }
            if (saved.ref1Path != null && saved.ref1Path.length() > 0 && (this.pkPyInputPath == null || this.pkPyInputPath.length() == 0)) this.pkPyInputPath = saved.ref1Path;
            String note = saved.note == null ? "" : saved.note;
            String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
            int cut = report.indexOf("\nReference file 1:");
            if (cut >= 0) report = report.substring(0, cut);
            String shown = report.length() == 0 ? note : report + "\n" + note;
            this.pkPromptReport = shown;
            if (this.pkPyLog != null) this.pkPyLog.setText(shown);
            if (this.pkPyHint != null) this.pkPyHint.setText(shown);
        } catch (Exception ex) {
            if (this.pkPyLog != null) this.pkPyLog.setText("Could not read reference files.");
        }
    }

    private void pkApplyHint(String hint) {
        if (hint == null) hint = "";
        if (this.pkPyHint != null) this.pkPyHint.setText(hint);
        if (this.pkPyLog != null) this.pkPyLog.setText(hint);
        this.pkPyInputToken = pulsekit.PyJavHints.firstInput(hint);
        java.io.File dir = new java.io.File(this.getCacheDir(), "pyjav-in");
        if (!dir.isDirectory()) dir.mkdirs();
        String out = pulsekit.PyJavHints.outputFile(this.pkPyInputPath, hint, dir.getAbsolutePath());
        if (out.length() > 0) {
            this.pkPyOutputPath = out;
            String filled = pulsekit.PyJavHints.fillArgs("", hint, this.pkPyInputToken, this.pkPyInputPath, dir.getAbsolutePath());
            if (this.pkPyArgs != null) this.pkPyArgs.setText(filled);
            String note = hint + "\nOutput file: " + out + "\nExtra args: " + filled;
            if (this.pkPyLog != null) this.pkPyLog.setText(note);
        }
        if (this.pkPyInput == null) return;
        if (this.pkPyInputToken == null) {
            this.pkPyInput.setVisibility(8);
        } else {
            this.pkPyInput.setVisibility(0);
            this.pkPyInput.setText("Browse " + this.pkPyInputToken);
        }
    }

    private void pkRefreshRecent(int select) {
        if (this.pkPyRecent == null) return;
        this.pkPyRecentItems = pulsekit.PyJavRecent.load(this.getFilesDir());
        pulsekit.PyJavUi.fillRecent(this, this.pkPyRecent, this.pkPyRecentItems);
    }

    public void pkApplyRecent(int index) {
        if (this.pkPyRecentMute || this.pkPyRecentItems == null || index <= 0 || index > this.pkPyRecentItems.size()) return;
        pulsekit.PyJavRecent.Item item = (pulsekit.PyJavRecent.Item) this.pkPyRecentItems.get(index - 1);
        this.pyName = item.name;
        this.pkPyInputPath = null;
        boolean binary = item.bytes != null && item.bytes.length > 0;
        if (binary) this.pkPyBytes = item.bytes;
        else this.pkPyBytes = null;
        if (this.pkPyArgs != null) this.pkPyArgs.setText(item.extra == null ? "" : item.extra);
        if (this.pyEditor != null) {
            if (binary) this.pyEditor.setText("// " + item.name + "\n// Binary. Run uses this file.\n");
            else {
                String text = item.source == null ? "" : item.source;
                if (item.name != null && item.name.toLowerCase().endsWith(".prompt")) {
                    pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
                    this.pkPromptSource = text;
                    this.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
                    this.pkSetPromptRun(pulsekit.PromptRun.runMode(text, sheet));
                    this.pkLoadRefs(this.pkPromptSource);
                    if (sheet != null && sheet.body != null) text = sheet.body;
                }
                this.pyEditor.setText(text);
            }
        }
        this.pkShowPromptModes();
        this.setNow("PyJav · " + item.label());
        String hint = pulsekit.PyJavHints.status(item.name, item.source, item.bytes);
        this.pkApplyHint(hint);
    }

    private void pkWirePyJavCore() {
        if (this.tabs != null) {
            for (int i = 0; i < this.tabs.size(); i++) {
                android.widget.TextView tv = (android.widget.TextView) this.tabs.get(i);
                if ("py".equals(tv.getTag())) tv.setText("PyJav");
            }
        }
        if (this.pyPane == null) return;
        if (this.pyPane.getChildCount() > 0 && this.pyPane.getChildAt(0) instanceof android.widget.TextView) {
            ((android.widget.TextView) this.pyPane.getChildAt(0)).setText("PyJav");
        }
        if (this.pyPane.getChildCount() > 1 && this.pyPane.getChildAt(1) instanceof android.widget.TextView) {
            android.widget.TextView hint = (android.widget.TextView) this.pyPane.getChildAt(1);
            hint.setText("Python, Java, or a .prompt file. Java is compiled in the app and run on ART.");
        }
        if (this.pkPyWired) return;
        this.pkPyWired = true;
        int slot = 1;
        if (slot > this.pyPane.getChildCount()) slot = this.pyPane.getChildCount();
        this.pkPyRecent = new android.widget.Spinner(this, 1);
        this.pkPyRecent.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        this.pkPyRecent.setMinimumHeight(this.dp(40));
        this.pyPane.addView(this.pkPyRecent, slot);
        slot = slot + 1;
        this.pkPyRecent.setOnItemSelectedListener(pulsekit.PyJavUi.recent(this));
        this.pkRefreshRecent(0);
        this.pkPyHint = this.text("Possible extra args appear after you browse a file.", 12, false);
        this.pkPyHint.setTextColor(FG);
        this.pyPane.addView(this.pkPyHint, slot);
        slot = slot + 1;
        this.pkPyArgs = new android.widget.EditText(this);
        this.pkPyArgs.setHint("Extra args");
        this.pkPyArgs.setSingleLine(false);
        this.pkPyArgs.setMinLines(2);
        this.pkPyArgs.setTextColor(FG);
        this.pkPyArgs.setHintTextColor(MUTED);
        this.pkPyArgs.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        this.pyPane.addView(this.pkPyArgs, slot);
        slot = slot + 1;
        this.pkPyInput = this.action("Browse input", ELEV, FG, pulsekit.PyJavUi.browseInput(this));
        this.pkPyInput.setVisibility(8);
        this.pyPane.addView(this.pkPyInput, slot);
        slot = slot + 1;
        this.pkPromptModes = new android.widget.LinearLayout(this);
        this.pkPromptModes.setOrientation(0);
        this.pkRunBash = this.action("bash", ELEV, FG, pulsekit.PyJavUi.mode(this, "bash"));
        this.pkRunCmd = this.action("cmd", ELEV, FG, pulsekit.PyJavUi.mode(this, "cmd"));
        this.pkRunAi = this.action("AI", ELEV, FG, pulsekit.PyJavUi.mode(this, "ai"));
        this.pkPromptModes.addView(this.pkRunBash, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        this.pkPromptModes.addView(this.pkRunCmd, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        this.pkPromptModes.addView(this.pkRunAi, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        this.pyPane.addView(this.pkPromptModes, slot);
        this.pkPromptModes.setVisibility(8);
        this.pkSetPromptRun(this.pkPromptRun == null ? "bash" : this.pkPromptRun);
        slot = slot + 1;
        this.pyPane.addView(this.action("Browse file", ELEV, FG, pulsekit.PyJavUi.browse(this)), slot);
        slot = slot + 1;
        this.pyPane.addView(this.action("Params", FG, BG, pulsekit.PyJavUi.params(this)), slot);
        slot = slot + 1;
        this.pyPane.addView(this.action("Run", FG, BG, pulsekit.PyJavUi.click(this)), slot);
        slot = slot + 1;
        this.pkPyLog = this.text("Output appears here.", 12, false);
        this.pkPyLog.setTextColor(FG);
        this.pkPyLog.setMinLines(4);
        this.pyPane.addView(this.pkPyLog, slot);
    }

    public void pkPrepareOutput() {
        pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(this.pkPromptSource);
        String defined = this.pkDefinedResult == null ? "" : this.pkDefinedResult;
        if (defined.length() == 0 && sheet != null && sheet.result != null) defined = sheet.result;
        String title = sheet != null && sheet.name != null && sheet.name.length() > 0 ? sheet.name : this.pyName;
        String category = sheet != null && sheet.category != null && sheet.category.length() > 0 ? sheet.category : this.pkPromptCategory;
        String model = sheet != null && sheet.model != null ? sheet.model : "";
        String body = this.pyEditor != null ? this.pyEditor.getText().toString() : "";
        String fileName = pulsekit.PromptRun.chooseOutputName(title, category, this.pkPromptRun, model, body, defined);
        this.pkOutputInvented = defined == null || defined.trim().length() == 0;
        this.pkOutputSaved = !this.pkOutputInvented;
        this.pkOutputName = fileName;
        java.io.File dir = new java.io.File(this.getCacheDir(), "pyjav-in");
        if (!dir.isDirectory()) dir.mkdirs();
        this.pkPyOutputPath = new java.io.File(dir, fileName).getAbsolutePath();
        String note = "Output file: " + fileName;
        String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
        int cut = report.indexOf("\nOutput file:");
        if (cut >= 0) report = report.substring(0, cut);
        this.pkPromptReport = report.length() == 0 ? note : report + "\n" + note;
    }

    public void pkSaveInventedOutput(String text) {
        if (!this.pkOutputInvented || this.pkOutputSaved || this.pkPyOutputPath == null || this.pkOutputName == null) return;
        try {
            java.io.File file = new java.io.File(this.pkPyOutputPath);
            byte[] data = null;
            if (file.isFile() && file.length() > 0) {
                java.io.FileInputStream in = new java.io.FileInputStream(file);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                data = bos.toByteArray();
            } else if (pulsekit.PromptRun.logOutput(this.pkOutputName)) {
                String body = text == null ? "" : text;
                data = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                java.io.FileOutputStream out = new java.io.FileOutputStream(file);
                out.write(data);
                out.close();
            }
            if (data == null) return;
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(this.pkPromptSource);
            String title = sheet != null && sheet.name != null ? sheet.name : "";
            String category = sheet != null && sheet.category != null ? sheet.category : this.pkPromptCategory;
            String n1 = sheet != null && sheet.ref1 != null ? sheet.ref1 : "";
            String n2 = sheet != null && sheet.ref2 != null ? sheet.ref2 : "";
            pulsekit.PromptFiles.storeResult(this, title, category, n1, n2, this.pkOutputName, data);
            this.pkOutputSaved = true;
            this.pkDefinedResult = this.pkOutputName;
        } catch (Exception ignored) {}
    }

    public void pkShowAiResult(String line) {
        if (line == null) line = "";
        String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
        String shown = report.length() == 0 ? line : report + "\n" + line;
        if (this.pkPyLog != null) this.pkPyLog.setText(shown);
        if (this.pkPyHint != null) this.pkPyHint.setText(shown);
        this.setNow(line);
        this.pkSaveInventedOutput(shown);
    }

    public void pkRunPyJav() {
        if (this.pyName != null && this.pyName.toLowerCase().endsWith(".prompt")) {
            if (!this.pkOutputResume) {
                this.pkLoadRefs(this.pkPromptSource);
                this.pkPrepareOutput();
                if (this.pkOutputInvented && pulsekit.PromptRun.logOutput(this.pkOutputName)) {
                    pulsekit.PyJavUi.askOutput(this);
                    return;
                }
            }
            this.pkOutputResume = false;
        }
        if (this.pyName != null && this.pyName.toLowerCase().endsWith(".prompt") && "ai".equals(this.pkPromptRun)) {
            String body = this.pyEditor != null ? this.pyEditor.getText().toString() : "";
            body = pulsekit.PromptRun.withoutDescription(this.pkPromptDescription, body);
            pulsekit.PyJavRecent.remember(this.getFilesDir(), this.pyName, "", this.pkPromptSource == null ? body : this.pkPromptSource, null);
            this.pkRefreshRecent(0);
            pulsekit.PyJavUi.chooseAi(this, body, this.pkRef1Path, this.pkRef2Path);
            return;
        }
        String extra = this.pkPyArgs != null ? this.pkPyArgs.getText().toString() : "";
        String hint = this.pkPyHint != null ? this.pkPyHint.getText().toString() : "";
        if (this.pkPyLog != null) this.pkPyLog.setText(this.pkPromptReport != null && this.pkPromptReport.length() > 0 ? this.pkPromptReport + "\nRunning…" : "Running…");
        if (this.pkPyHint != null) this.pkPyHint.setText(this.pkPromptReport != null && this.pkPromptReport.length() > 0 ? this.pkPromptReport : "Running…");
        this.setNow("Running…");
        String name = this.pyName == null ? "script.py" : this.pyName;
        extra = pulsekit.PyJavParams.merge(extra, pulsekit.PyJavParams.load(this, name));
        String src = this.pyEditor != null ? this.pyEditor.getText().toString() : "";
        src = pulsekit.PromptRun.withoutDescription(this.pkPromptDescription, src);
        java.io.File dir = new java.io.File(this.getCacheDir(), "pyjav-in");
        if (!dir.isDirectory()) dir.mkdirs();
        String filled = pulsekit.PyJavHints.fillArgs(extra, hint, this.pkPyInputToken, this.pkPyInputPath, dir.getAbsolutePath());
        String out = pulsekit.PyJavHints.outputFile(this.pkPyInputPath, hint + "\n" + extra, dir.getAbsolutePath());
        if (out.length() > 0 && !this.pkOutputInvented) this.pkPyOutputPath = out;
        if (this.pkPyArgs != null && filled.length() > 0) this.pkPyArgs.setText(filled);
        pulsekit.PyJavRecent.remember(this.getFilesDir(), name, filled, src, this.pkPyBytes);
        this.pkRefreshRecent(0);
        java.util.List argv;
        if ((this.pkPyInputPath != null && this.pkPyInputPath.length() > 0) || out.length() > 0) {
            argv = pulsekit.PyJavHints.programArgs(filled, this.pkPyInputToken, this.pkPyInputPath, hint, dir.getAbsolutePath());
            if (this.pkPyHint != null) this.pkPyHint.setText(pulsekit.PyJavHints.outputNotice(filled, hint, this.pkPyInputPath, dir.getAbsolutePath()));
        } else {
            int swing = this.swingBar != null ? this.swingBar.getVal() : 0;
            argv = pulsekit.JavaRun.argvFor(src, this.bpm(), this.style, this.bars, swing, extra);
        }
        if (name.toLowerCase().endsWith(".prompt")) {
            argv.add("--pk-run");
            argv.add(this.pkPromptRun == null ? "bash" : this.pkPromptRun);
        }
        if (this.pkRef1Path != null && this.pkRef1Path.length() > 0 && !argv.contains(this.pkRef1Path)) argv.add(this.pkRef1Path);
        if (this.pkRef2Path != null && this.pkRef2Path.length() > 0 && !argv.contains(this.pkRef2Path)) argv.add(this.pkRef2Path);
        if (this.pkOutputInvented && this.pkPyOutputPath != null && this.pkPyOutputPath.length() > 0 && !argv.contains(this.pkPyOutputPath)) argv.add(this.pkPyOutputPath);
        pulsekit.JavaRun.start(name, src, this.pkPyBytes, argv, pulsekit.PyJavUi.listener(this));
    }

    public void pkAcceptOutput(String answer) {
        String given = answer == null ? "" : answer.trim();
        if (given.length() > 0) {
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(this.pkPromptSource);
            String title = sheet != null && sheet.name != null && sheet.name.length() > 0 ? sheet.name : this.pyName;
            String category = sheet != null && sheet.category != null && sheet.category.length() > 0 ? sheet.category : this.pkPromptCategory;
            String model = sheet != null && sheet.model != null ? sheet.model : "";
            String body = this.pyEditor != null ? this.pyEditor.getText().toString() : "";
            String fileName = pulsekit.PromptRun.chooseOutputName(title, category, this.pkPromptRun, model, body, given);
            this.pkOutputName = fileName;
            this.pkOutputInvented = true;
            this.pkOutputSaved = false;
            java.io.File dir = new java.io.File(this.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            this.pkPyOutputPath = new java.io.File(dir, fileName).getAbsolutePath();
            String note = "Output file: " + fileName;
            String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
            int cut = report.indexOf("\nOutput file:");
            if (cut >= 0) report = report.substring(0, cut);
            this.pkPromptReport = report.length() == 0 ? note : report + "\n" + note;
        }
        this.pkOutputResume = true;
        this.pkRunPyJav();
    }

    public void pkCancelOutput() {
        this.pkOutputInvented = false;
        this.pkOutputResume = false;
        this.pkShowAiResult("Cancelled.");
    }

    public void pkSetPyArgs(java.lang.String args) {
        java.lang.String extra = this.pkPyArgs != null ? this.pkPyArgs.getText().toString() : "";
        java.lang.String merged = pulsekit.PyJavParams.merge(extra, args);
        if (this.pkPyArgs != null) this.pkPyArgs.setText(merged);
        this.setNow("Params saved");
    }

    public void pkOpenParams() {
        java.lang.String name = this.pyName == null ? "DrumMidi" : this.pyName;
        java.lang.String extra = this.pkPyArgs != null ? this.pkPyArgs.getText().toString() : "";
        pulsekit.PyJavParams.open(this, name, extra);
    }

    public void pkSyncTransport() {
        try {
            if (this.playBtn == null || !(this.playBtn.getParent() instanceof android.view.View)) return;
            android.view.View row = (android.view.View) this.playBtn.getParent();
            row.setVisibility("py".equals(this.view) ? 8 : 0);
        } catch (Throwable ignored) {}
    }

    public String pkImportProgramMidi(byte[] data, String name) {
        try {
            if (data == null || name == null) return "Import failed: no MIDI file";
            pulsekit.Engine.stageSourceMidi(data, name);
            String stem = pulsekit.Engine.stemNameFromMidi(name);
            pulsekit.Engine.MidiBars bars = pulsekit.Engine.parseMidiBars(data);
            boolean ok = bars != null && this.learnFromSongImport(name, bars, true);
            String source = stem;
            if (ok) {
                pulsekit.Engine.rememberFileSetMidi(source, data, name);
                for (int i = 0; i < this.learned.size(); i++) {
                    pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) this.learned.get(i);
                    if (item == null) continue;
                    java.lang.String s = pulsekit.Engine.sourceOf(item);
                    if (s == null || s.length() == 0) continue;
                    if (s.equals(source) || s.startsWith(source + " ")) pulsekit.Engine.rememberFileSetMidi(s, data, name);
                }
                pulsekit.Engine.rememberFileSetOrigin(source, "program");
                pulsekit.Engine.storeFileSetAudioDir(new java.io.File(this.getFilesDir(), "fset-audio"));
                this.persistFsetInfo();
                this.rebuildImported();
                this.rebuildImportedFills();
                this.setNow("Succeeded: " + source);
                android.widget.Toast.makeText(this, "Succeeded: " + source, 1).show();
                return "Succeeded: " + source;
            }
            int[][] cells = pulsekit.Engine.parseMidi(data);
            if (cells == null || pulsekit.Engine.hitCount(cells) < 1) return "Failed: no drum notes in " + name;
            int bpm = pulsekit.Engine.parseMidiBpm(data, this.bpm());
            this.learnFromImport(name, cells, bpm);
            source = pulsekit.Engine.importSource(name);
            if (source == null || source.length() == 0) source = stem;
            boolean found = false;
            for (int i = 0; i < this.learned.size(); i++) {
                pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) this.learned.get(i);
                if (item != null && source.equals(item.source)) found = true;
            }
            if (!found) return "Failed: " + name + " did not become a file set";
            pulsekit.Engine.rememberFileSetMidi(source, data, name);
            pulsekit.Engine.rememberFileSetOrigin(source, "program");
            this.persistFsetInfo();
            this.rebuildImported();
            this.setNow("Succeeded: " + source);
            android.widget.Toast.makeText(this, "Succeeded: " + source, 1).show();
            return "Succeeded: " + source;
        } catch (Throwable ex) {
            String m = ex.getMessage();
            return "Failed: " + (m == null ? ex.toString() : m);
        }
    }

    public String pkVerdict(String log, int code, int midis, String status) {
        String found = null;
        if (log != null) {
            int i = 0;
            while (i < log.length()) {
                int nl = log.indexOf(10, i);
                String line = (nl < 0 ? log.substring(i) : log.substring(i, nl)).trim();
                if (line.startsWith("Succeeded:") || line.startsWith("Failed:")) found = line;
                if (nl < 0) break;
                i = nl + 1;
            }
        }
        if (found != null) return found;
        if (status != null && (status.startsWith("Succeeded:") || status.startsWith("Failed:"))) return status;
        String why = status == null ? "" : status;
        if (why.startsWith("Import failed: ")) why = why.substring(15);
        if (why.startsWith("Import succeeded: ")) return "Succeeded: " + why.substring(18);
        if (log != null) {
            int cut = log.indexOf("ERROR");
            if (cut < 0) cut = log.indexOf("Exception");
            if (cut < 0) cut = log.indexOf("Timed out");
            if (cut >= 0) {
                int end = log.indexOf(10, cut);
                why = (end < 0 ? log.substring(cut) : log.substring(cut, end)).trim();
            }
        }
        if (midis > 0 && code == 0) return "Succeeded: " + (why.length() == 0 ? "MIDI" : why);
        if (why.length() == 0) why = "no MIDI file was written";
        return "Failed: " + why;
    }

    public void pkShowPyResult(pulsekit.JavaRun.Result result) {
        String status = "Import failed: no result";
        String log = "";
        try {
        log = result == null || result.log == null || result.log.length() == 0 ? "(no output)" : result.log;
        if (this.pkPromptReport != null && this.pkPromptReport.length() > 0 && this.pyName != null && this.pyName.toLowerCase().endsWith(".prompt") && log.indexOf("Category:") != 0) log = this.pkPromptReport + "\n" + log;
        int midis = 0;
        if (result != null && result.files != null) {
            for (int i = 0; i < result.files.size(); i++) {
                pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) result.files.get(i);
                log = log + "\nfile " + f.name + " (" + f.bytes.length + " bytes)";
                String low = f.name.toLowerCase();
                if ((low.endsWith(".mid") || low.endsWith(".midi")) && f.bytes.length >= 4 && f.bytes[0] == 'M' && f.bytes[1] == 'T' && f.bytes[2] == 'h' && f.bytes[3] == 'd') {
                    status = this.pkImportProgramMidi(f.bytes, f.name);
                    midis = midis + 1;
                }
            }
        }
        boolean prompt = this.pyName != null && this.pyName.toLowerCase().endsWith(".prompt");
        if (midis == 0 && !prompt && this.pkPyOutputPath != null && this.pkPyOutputPath.length() > 0 && (result == null || result.log == null || !result.log.startsWith("Executed"))) {
            try {
                java.io.File out = new java.io.File(this.pkPyOutputPath);
                if (out.isFile()) {
                    java.io.FileInputStream in = new java.io.FileInputStream(out);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    in.close();
                    log = log + "\nfile " + out.getName() + " (" + bos.size() + " bytes)";
                    status = this.pkImportProgramMidi(bos.toByteArray(), out.getName());
                    midis = midis + 1;
                }
            } catch (Throwable ex) {
                String m = ex.getMessage();
                status = "Import failed: " + (m == null ? ex.toString() : m);
            }
        }
        if (midis == 0 && (prompt || (result != null && result.log != null && (result.log.startsWith("Executed") || result.log.startsWith("AI prompt") || result.log.startsWith("$ ") || result.log.startsWith("The prompt is empty") || result.log.startsWith("bash is not") || result.log.startsWith("cmd is not"))))) {
            String raw = result == null || result.log == null ? "" : result.log;
            int nl = raw.indexOf(10);
            status = nl < 0 ? raw : raw.substring(0, nl);
            if (status.length() == 0) status = result != null && result.code != 0 ? "Prompt failed" : "Prompt finished";
        } else if (midis == 0) {
            String why = "Import failed: no MIDI file was written";
            int cut = log.indexOf("No output file:");
            if (cut < 0) cut = log.indexOf("could not read");
            if (cut < 0) cut = log.indexOf("OutOfMemoryError");
            if (cut < 0) cut = log.indexOf("Error processing audio");
            if (cut >= 0) {
                int end = log.indexOf('\n', cut);
                why = "Import failed: " + (end < 0 ? log.substring(cut) : log.substring(cut, end)).trim();
            }
            status = why;
        }
        status = this.pkVerdict(log, result == null ? 1 : result.code, midis, status);
        log = status + "\n" + log;
        if (this.pkPyHint != null) this.pkPyHint.setText(status);
        this.setNow(status);
        android.widget.Toast.makeText(this, status, 1).show();
        this.pkSaveInventedOutput(log);
        if (this.pkPyLog != null) this.pkPyLog.setText(log);
        } catch (Throwable ex) {
            String m = ex.getMessage();
            status = "Failed: " + (m == null ? ex.toString() : m);
            if (this.pkPyHint != null) this.pkPyHint.setText(status);
            this.setNow(status);
            android.widget.Toast.makeText(this, status, 1).show();
            if (this.pkPyLog != null) this.pkPyLog.setText(status);
        }
    }

    private boolean pkTakeProgramCore(byte[] data, String name) {
        if (data == null || name == null) return false;
        String low = name.toLowerCase();
        boolean jar = low.endsWith(".jar");
        boolean cls = low.endsWith(".class");
        boolean javaSrc = low.endsWith(".java");
        if (!jar && !cls && !javaSrc) return false;
        if (jar) {
            try {
                java.util.Map files = pulsekit.Engine.unzip(data);
                if (files != null && files.containsKey("pattern.json")) return false;
            } catch (Exception ignored) {}
        }
        int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        this.pyName = base;
        this.pkPyInputPath = null;
        if (jar || cls) this.pkPyBytes = data;
        else this.pkPyBytes = null;
        if (this.pyEditor != null) {
            if (jar || cls) this.pyEditor.setText("// " + base + "\n// Binary. Run uses this file.\n");
            else this.pyEditor.setText(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
        this.show("py");
        this.setNow("PyJav · " + base);
        String src = (jar || cls) ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
        String hint = pulsekit.PyJavHints.status(base, src, data);
        this.pkApplyHint(hint);
        byte[] kept = null;
        if (jar || cls) kept = data;
        pulsekit.PyJavRecent.remember(this.getFilesDir(), base, "", src, kept);
        this.pkRefreshRecent(0);
        this.pkShowPromptModes();
        return true;
    }

    public void pkOpenPromptText(String name, String full) {
        if (name == null || name.length() == 0) name = "prompt.prompt";
        if (!name.toLowerCase().endsWith(".prompt")) name = name + ".prompt";
        this.pyName = name;
        this.pkPyBytes = null;
        this.pkPyInputPath = null;
        String text = full == null ? "" : full;
        pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
        this.pkPromptSource = text;
        this.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
        String body = sheet != null && sheet.body != null ? sheet.body : text;
        if (this.pyEditor != null) this.pyEditor.setText(body);
        this.pkSetPromptRun(pulsekit.PromptRun.runMode(text, sheet));
        this.pkShowPromptModes();
        this.show("py");
        String hint = this.pkPromptReport == null ? "" : this.pkPromptReport;
        this.setNow(hint.replace('\n', ' '));
        this.pkApplyHint(hint);
        this.pkLoadRefs(text);
        pulsekit.PyJavRecent.remember(this.getFilesDir(), name, "", text, null);
        this.pkRefreshRecent(0);
        android.widget.Toast.makeText(this, "Opened " + name, 0).show();
    }

    public void pkBrowsePyJav() {
        android.content.Intent intent = new android.content.Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        intent.putExtra("android.intent.extra.MIME_TYPES", new String[]{"*/*", "text/plain", "text/*", "application/octet-stream"});
        this.startActivityForResult(intent, 25);
    }

    private void pkTakePickedProgramCore(android.net.Uri uri) {
        try {
            byte[] data = this.readUri(uri);
            String name = null;
            android.database.Cursor cursor = this.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex("_display_name");
                        if (col >= 0) name = cursor.getString(col);
                    }
                } finally { cursor.close(); }
            }
            if (name == null) name = uri.getLastPathSegment();
            if (name == null) name = "program.py";
            int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
            if (slash >= 0) name = name.substring(slash + 1);
            String low = name.toLowerCase();
            boolean py = low.endsWith(".py");
            boolean jar = low.endsWith(".jar");
            boolean cls = low.endsWith(".class");
            boolean javaSrc = low.endsWith(".java");
            boolean prompt = low.endsWith(".prompt") || low.endsWith(".prompt.txt") || low.contains(".prompt");
            if (!prompt && data != null && data.length >= 9) {
                String head = new String(data, 0, Math.min(data.length, 12), java.nio.charset.StandardCharsets.UTF_8);
                if (head.startsWith("PKPROMPT1")) {
                    prompt = true;
                    if (!low.endsWith(".prompt")) name = (low.endsWith(".txt") ? name.substring(0, name.length() - 4) : name) + ".prompt";
                }
            }
            if (!py && !prompt && !jar && !cls && !javaSrc) {
                String why = "Pick a .py, .java, .class, .jar, or .prompt file.";
                if (this.pkPyLog != null) this.pkPyLog.setText(why);
                android.widget.Toast.makeText(this, why, 1).show();
                return;
            }
            if (jar) {
                try {
                    java.util.Map files = pulsekit.Engine.unzip(data);
                    if (files != null && files.containsKey("pattern.json")) {
                        if (this.pkPyLog != null) this.pkPyLog.setText("That JAR is a kit snapshot, not a program.");
                        return;
                    }
                } catch (Exception ignored) {}
            }
            this.pyName = name;
            this.pkPyInputPath = null;
            if (jar || cls) this.pkPyBytes = data;
            else this.pkPyBytes = null;
            if (this.pyEditor != null) {
                if (jar || cls) this.pyEditor.setText("// " + name + "\n// Binary. Run uses this file.\n");
                else {
                    String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    if (prompt) {
                        pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
                        this.pkPromptSource = text;
                        this.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
                        if (sheet != null && sheet.body != null) text = sheet.body;
                    }
                    this.pyEditor.setText(text);
                    if (prompt) this.pkSetPromptRun(pulsekit.PromptRun.runMode(new String(data, java.nio.charset.StandardCharsets.UTF_8), null));
                }
            }
            this.pkShowPromptModes();
            this.show("py");
            this.setNow("PyJav · " + name);
            String src = (jar || cls) ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
            if (prompt) {
                pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(src);
                if (sheet != null && sheet.body != null) src = sheet.body;
            }
            String hint = prompt
                ? (this.pkPromptReport == null ? "" : this.pkPromptReport)
                : pulsekit.PyJavHints.status(name, src, data);
            this.pkApplyHint(hint);
            if (prompt) this.pkLoadRefs(this.pkPromptSource);
            byte[] kept = null;
            if (jar || cls) kept = data;
            pulsekit.PyJavRecent.remember(this.getFilesDir(), name, "", src, kept);
            this.pkRefreshRecent(0);
            android.widget.Toast.makeText(this, "Opened " + name, 0).show();
        } catch (Exception ex) {
            String m = ex.getMessage();
            if (m == null || m.length() == 0) m = "Could not open that file";
            if (this.pkPyLog != null) this.pkPyLog.setText(m);
            android.widget.Toast.makeText(this, m, 1).show();
        }
    }

    public void pkBrowseInput() {
        android.content.Intent intent = new android.content.Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        this.startActivityForResult(intent, 26);
    }

    public void pkTakeInputFile(android.net.Uri uri) {
        try {
            String name = null;
            android.database.Cursor cursor = this.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex("_display_name");
                        if (col >= 0) name = cursor.getString(col);
                    }
                } finally { cursor.close(); }
            }
            if (name == null || name.length() == 0) name = "input.wav";
            int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
            if (slash >= 0) name = name.substring(slash + 1);
            name = name.replace(' ', '_');
            java.io.File dir = new java.io.File(this.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, name);
            java.io.InputStream in = this.getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("Could not open " + name);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            } finally { fos.close(); in.close(); }
            String path = out.getAbsolutePath();
            this.pkPyInputPath = path;
            String hint = this.pkPyHint != null ? this.pkPyHint.getText().toString() : "";
            java.io.File folder = new java.io.File(this.getCacheDir(), "pyjav-in");
            String note = pulsekit.PyJavHints.outputNotice("", hint, path, folder.getAbsolutePath());
            String next = pulsekit.PyJavHints.fillArgs("", hint, this.pkPyInputToken, path, folder.getAbsolutePath());
            String output = pulsekit.PyJavHints.outputFile(path, hint, folder.getAbsolutePath());
            this.pkPyOutputPath = output;
            if (this.pkPyArgs != null) this.pkPyArgs.setText(next);
            if (this.pkPyLog != null) this.pkPyLog.setText(note);
        } catch (Exception ex) {
            String m = ex.getMessage();
            if (this.pkPyLog != null) this.pkPyLog.setText(m != null ? m : "Could not open that input file");
        }
    }

    private android.widget.LinearLayout pkMidiRow(java.lang.String src) {
        if (src == null) src = "";
        src = pulsekit.Engine.fileSetMidiKeyForLabel(src);
        java.lang.String name = pulsekit.Engine.fileSetMidiName(src);
        if (name.length() == 0) return null;
        android.widget.LinearLayout line = this.row();
        line.setTag("sourcemidi");
        line.setPadding(this.dp(8), this.dp(4), 0, this.dp(4));
        android.widget.TextView label = this.text(name, 12, false);
        label.setTextColor(FG);
        line.addView(label, this.flex(1));
        android.widget.TextView clock = this.text("00:00 / 00:00", 11, false);
        clock.setTag("midiclock");
        clock.setTextColor(MUTED);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.addView(clock);
        line.addView(this.outline("Play", false, pulsekit.AnalyzeClicks.filePlayMidi(this, src)));
        line.addView(this.outline("Pause", false, pulsekit.AnalyzeClicks.filePauseMidi(this)));
        line.addView(this.outline("Stop", false, pulsekit.AnalyzeClicks.fileStopMidi(this)));
        return line;
    }

    private void appendMidiFileRows() {
        if (this.importedFileList == null) return;
        for (int i = 0; i < this.importedFileList.getChildCount(); i++) {
            android.view.View child = this.importedFileList.getChildAt(i);
            if (!(child instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout row = (android.widget.LinearLayout) child;
            android.widget.TextView packHead = null;
            boolean header = false;
            for (int j = 0; j < row.getChildCount(); j++) {
                android.view.View v = row.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                if ("Export".equals(s)) header = true;
                if (s.startsWith("\u25be ") || s.startsWith("\u25b8 ")) packHead = (android.widget.TextView) v;
            }
            if (!header || packHead == null) continue;
            android.view.View next = i + 1 < this.importedFileList.getChildCount() ? this.importedFileList.getChildAt(i + 1) : null;
            android.widget.LinearLayout kids = next instanceof android.widget.LinearLayout ? (android.widget.LinearLayout) next : null;
            boolean nextHeader = true;
            boolean already = false;
            if (kids != null) {
                nextHeader = false;
                for (int k = 0; k < kids.getChildCount(); k++) {
                    android.view.View v = kids.getChildAt(k);
                    if ("sourcemidi".equals(v.getTag())) already = true;
                    if (v instanceof android.widget.TextView) {
                        java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                        if ("Export".equals(s)) nextHeader = true;
                    }
                }
            }
            if (already) continue;
            java.lang.String head = packHead.getText() == null ? "" : packHead.getText().toString();
            if (head.startsWith("\u25be ") || head.startsWith("\u25b8 ")) head = head.substring(2);
            int cut = head.lastIndexOf(" \u00b7 ");
            java.lang.String label = cut > 0 ? head.substring(0, cut) : head;
            if (label.startsWith("EP ")) label = label.substring(3);
            else if (label.startsWith("A ") || label.startsWith("M ") || label.startsWith("I ") || label.startsWith("C ")) label = label.substring(2);
            java.lang.String src = "Other".equals(label) ? "" : label;
            android.widget.LinearLayout line = this.pkMidiRow(src);
            if (line == null) continue;
            if (nextHeader) this.importedFileList.addView(line, i + 1);
            else kids.addView(line, 0);
        }
    }

    private void pkShowInfoMidi(java.lang.String src) {
        if (this.infoRows == null) return;
        android.widget.LinearLayout line = this.pkMidiRow(src);
        if (line != null) this.infoRows.addView(line, 0);
    }

    public void pkRefreshNodeUi() {
        if (this.pyPane == null) return;
        String next = "Python, Java, JavaScript, or TypeScript. On Android, Node.js runs in Termux: pkg install nodejs. npm installs @sogni-ai/sogni-client. Java runs in the app.";
        for (int i = 0; i < this.pyPane.getChildCount(); i++) {
            android.view.View child = this.pyPane.getChildAt(i);
            if (!(child instanceof android.widget.TextView)) continue;
            android.widget.TextView label = (android.widget.TextView) child;
            String s = label.getText() == null ? "" : label.getText().toString();
            if (s.indexOf("Python") >= 0 && s.indexOf("Java") >= 0 && s.indexOf("Termux") < 0) label.setText(next);
        }
        if (this.pyPane.findViewWithTag("sogni-client") != null) return;
        android.widget.TextView btn = this.text("Sogni client", 13, true);
        btn.setTag("sogni-client");
        btn.setGravity(17);
        btn.setBackground(this.round(ELEV, 8));
        btn.setOnClickListener(new pulsekit.SogniClientClick(this));
        int at = this.pyPane.getChildCount() > 2 ? 2 : this.pyPane.getChildCount();
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, this.dp(40));
        lp.bottomMargin = this.dp(8);
        this.pyPane.addView(btn, at, lp);
    }

    /** JavaScript and TypeScript files run with Node.js (Termux on Android). */
    private static boolean isNodeScript(String name) {
        String low = name.toLowerCase();
        return low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".cjs") || low.endsWith(".jsx") || low.endsWith(".ts") || low.endsWith(".mts") || low.endsWith(".tsx");
    }

    public void pkLoadSogniClient(View v) {
        this.pyName = "sogni-client.mjs";
        this.pkPyBytes = null;
        this.pkPyInputPath = null;
        String src = "import * as sogni from \"@sogni-ai/sogni-client\";\n\n"
            + "const names = Object.keys(sogni).sort();\n"
            + "console.log(\"Sogni client loaded\");\n"
            + "console.log(names.length ? names.join(\", \") : \"(no named exports)\");\n"
            + "if (!process.env.SOGNI_APP_ID) {\n"
            + "  console.log(\"Set SOGNI_APP_ID, SOGNI_USERNAME, and SOGNI_PASSWORD to sign in.\");\n"
            + "}\n";
        if (this.pyEditor != null) {
            this.pyEditor.setText(src);
        }
        this.setNow("sogni-client.mjs");
    }
}
