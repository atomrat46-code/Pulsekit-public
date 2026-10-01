package pulsekit;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import pulsekit.AudioIo;
import pulsekit.Engine;

public final class Pulsekit
extends JFrame {
    private static final Color BG = new Color(658188);
    private static final Color SURFACE = new Color(1250326);
    private static final Color FG = new Color(15526886);
    private static final Color MUTED = new Color(9079686);
    private static final Color SUBTLE = new Color(6053209);
    private static final Color ELEV = new Color(1776927);
    private static final Color BORDER = new Color(2763564);
    private static final Color HIT = new Color(10136476);
    private final Map<String, Engine.Style> styles = Engine.styles();
    private final int[][] cells = Engine.emptyCells();
    private final int[][] fillPat = Engine.fillCells("toms");
    private final int[][] lens = Engine.emptyCells();
    private final int[][] fillLens = Engine.emptyCells();
    private boolean lenHeld;
    private final boolean[] accents = new boolean[Engine.MAX_STEPS];
    private final boolean[] mutes = new boolean[Engine.TRACK_ID.length];
    private final JButton[][] buttons = new JButton[Engine.TRACK_ID.length][Engine.MAX_STEPS];
    private final JButton[] accentBtns = new JButton[Engine.MAX_STEPS];
    private final JLabel[] trackLabs = new JLabel[Engine.TRACK_ID.length];
    private final JButton[] padBtns = new JButton[Engine.TRACK_ID.length];
    private final List<JButton> tabs = new ArrayList<JButton>();
    private final JPanel styleHost = new JPanel();
    private final JPanel styleBar = new ChipStrip();
    private final JPanel importedBar = new JPanel();
    private final JPanel variatedPatternBar = new ChipStrip();
    private final JPanel fillHost = new JPanel();
    private final JPanel fillBar = new ChipStrip();
    private final JPanel variatedFillBar = new ChipStrip();
    private final JPanel importedFillBar = new JPanel();
    private final JPanel toolsRow = new JPanel(new FlowLayout(0, 6, 4));
    private final JPanel knobsRow = new JPanel(new GridLayout(1, 4, 12, 0));
    private final JPanel timeline = new JPanel(new FlowLayout(0, 2, 0));
    private JScrollPane timelineScroll;
    private final JPanel songAdds = new JPanel(new FlowLayout(0, 6, 4));
    private final JTextField bpmField = new JTextField("124", 3);
    private final JTextField tsNumField = new JTextField("4", 2);
    private final JTextField tsDenField = new JTextField("4", 2);
    private final JLabel nowPlaying = new JLabel(" ");
    private final RangeBar tempoBar = new RangeBar(40, 240, 124, n -> {
        if (this.bpmField != null && !Integer.toString(n).equals(this.bpmField.getText())) {
            this.bpmField.setText(Integer.toString(n));
        }
        this.onTempo(n);
    });
    private final RangeBar swingBar = new RangeBar(0, 75, 12, n -> {});
    private final RangeBar densBar = new RangeBar(1, 10, 5, n -> {});
    private final RangeBar humanBar = new RangeBar(0, 100, 18, n -> {});
    private final DefaultListModel<String> songModel = new DefaultListModel();
    private JList<String> songList;
    private final List<Engine.Part> song = new ArrayList<Engine.Part>();
    private final List<Engine.ImportedSong> importedSongs = new ArrayList<Engine.ImportedSong>();
    private String songLane = "original";
    private String importedSongId;
    private JPanel songLaneBar;
    private final CardLayout pages = new CardLayout();
    private final JPanel pageHost = new JPanel(this.pages);
    private JPanel chrome;
    private JButton playBtn;
    private JButton muteBtn;
    private JButton fillLastBtn;
    private JButton barsBtn;
    private JButton stepsBtn;
    private JPanel gridHost;
    private JTextField promptTitle;
    private JTextArea promptBody;
    private JTextField promptOut1;
    private JTextField promptOut2;
    private JLabel promptRef1;
    private JLabel promptRef2;
    private File promptRef1File;
    private File promptRef2File;
    private JLabel infoTitle;
    private JLabel infoStatus;
    private JPanel infoRows;
    private String infoBack = "import";
    private final java.util.LinkedHashMap<String, java.util.List<Engine.FileSetPart>> fileSetParts = new java.util.LinkedHashMap<String, java.util.List<Engine.FileSetPart>>();
    private final List<Engine.DrumSet> drumSets = new ArrayList<Engine.DrumSet>();
    private int drumSetIndex = 0;
    private JPanel drumSetBar;
    private JButton padMatchAll;
    private String style = "house";
    /** False until a pattern is chosen: the startup House pattern is not shown as selected. */
    private boolean styleChosen;
    private String view = "pattern";
    private String fillId = "toms";
    private String songMode = "edit";
    private String pyName = "drum_midi.py";
    private byte[] pyBytes;
    private JTextArea pyEditor;
    private JTextArea pyLog;
    private JTextField pyExtra;
    private JComboBox<String> pyRecent;
    private JLabel pyHint;
    private JButton pyInputBtn;
    private String pyInputToken;
    private String pyInputPath;
    private String pyOutputPath;
    private String pyHintPlain = "";
    private java.util.List<PyJavRecent.Item> pyRecentItems = new java.util.ArrayList<PyJavRecent.Item>();
    private boolean pyRecentMute;
    private JButton pyRun;
    private String promptRunMode = "bash";
    private JButton pyRunBash;
    private JButton pyRunCmd;
    private JButton pyRunAi;
    private JPanel pyPromptModes;
    private String promptDescription = "";
    private String promptCategory = "";
    private String promptModel = "";
    private String promptResult = "";
    private String promptOutputName = "";
    private boolean promptOutputInvented;
    private Sequencer sequencer;
    private Synthesizer synth;
    private Timer doubleKickTimer;
    private Timer playheadTimer;
    private int playhead = -1;
    private int songPart = -1;
    private int bars = 4;
    private int steps = 16;
    private int tsNum = 4;
    private int tsDen = 4;
    private boolean songPlay;
    private boolean muted;
    private boolean livePads;
    private boolean fillLast;
    private boolean fillVariated;
    private boolean pluginGhostHats;
    private final List<Engine.Plugin> plugins = new ArrayList<Engine.Plugin>();
    private final List<Engine.Learned> learned = new ArrayList<Engine.Learned>();
    private final List<Engine.Learned> variatedPatterns = new ArrayList<Engine.Learned>();
    private final List<Engine.LearnedFill> learnedFills = new ArrayList<Engine.LearnedFill>();
    private final List<Engine.LearnedFill> variatedFills = new ArrayList<Engine.LearnedFill>();
    private final List<String> hiddenStyles = new ArrayList<String>();
    private final List<String> hiddenFills = new ArrayList<String>();
    private final Map<String, String> fillernPairs = new LinkedHashMap<String, String>();
    /** Patterns whose Fillern fill was chosen from the list. Only these are underlined. */
    private final java.util.Set<String> fillernPicked = new java.util.LinkedHashSet<String>();
    /** Fillern type per pattern: Engine.FILLERN_AFTER (default), FILLERN_END or FILLERN_START. */
    private final Map<String, String> fillernModes = new LinkedHashMap<String, String>();
    /** The groove tab the imported chips were last built for. */
    private String importedFor;
    private final Map<String, Boolean> openPacks = new LinkedHashMap<String, Boolean>();
    private JPanel pluginList;
    private JPanel importedFileList;
    private JCheckBox autosaveBox;
    private Timer autosaveTimer;
    private boolean autosaveOn;
    private boolean autosaveBusy;
    private final short[][] voices = AudioIo.buildVoices(22050);

    private Pulsekit() {
        super("Pulsekit");
        this.drumSets.add(Engine.DrumSet.originalSet());
        for (int i = 0; i < Engine.MAX_STEPS; ++i) {
            this.accents[i] = i % 4 == 0;
        }
        this.loadStyle("house", false);
        this.buildUi();
        this.openMidi();
        this.restoreAutosave();
        this.restoreSessionFiles();
        this.styleChosen = false;
        this.refreshStyles();
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (Pulsekit.this.autosaveOn) Pulsekit.this.writeAutosave(false);
            }
        });
        this.setDefaultCloseOperation(3);
        this.setMinimumSize(new Dimension(1100, 740));
        this.setSize(1280, 820);
        this.setLocationRelativeTo(null);
        this.getContentPane().setBackground(BG);
    }

    public static void main(String[] stringArray) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            }
            catch (Exception exception) {
                // empty catch block
            }
            new Pulsekit().setVisible(true);
        });
    }

    private int bpm() {
        return this.tempoBar.getVal();
    }

    private void styleNumField(JTextField field, int size) {
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setBackground(BG);
        field.setFont(new Font("Monospaced", 1, size));
        field.setColumns(Math.max(2, field.getColumns()));
    }

    private void bindNumField(JTextField field, int min, int max, IntConsumer on) {
        Runnable commit = () -> {
            try {
                int n = Integer.parseInt(field.getText().trim().replaceAll("[^0-9-]", ""));
                n = Engine.clamp(n, min, max);
                if (field == this.tsDenField) n = Engine.clampTsDen(n);
                field.setText(Integer.toString(n));
                on.accept(n);
            } catch (Exception e) {
                if (field == this.bpmField) field.setText(Integer.toString(this.bpm()));
                else if (field == this.tsNumField) field.setText(Integer.toString(this.tsNum));
                else if (field == this.tsDenField) field.setText(Integer.toString(this.tsDen));
            }
        };
        field.addActionListener(e -> commit.run());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.selectAll();
            }
            @Override
            public void focusLost(FocusEvent e) {
                commit.run();
            }
        });
    }

    private void applyTimeSig(int num, int den) {
        boolean two = Engine.isDoubled(this.steps, this.tsNum, this.tsDen);
        this.tsNum = Engine.clampTsNum(num);
        this.tsDen = Engine.clampTsDen(den);
        this.tsNumField.setText(Integer.toString(this.tsNum));
        this.tsDenField.setText(Integer.toString(this.tsDen));
        int next = Engine.patternSteps(this.tsNum, this.tsDen, two);
        Engine.defaultAccents(this.accents, next, Engine.stepsPerBeat(this.tsDen));
        this.applySteps(next, true);
    }

    private void setBpmUi(int n) {
        int bpm = Engine.clampBpm(n);
        this.tempoBar.setVal(bpm);
        this.bpmField.setText(Integer.toString(bpm));
    }

    private void onTempo(int n) {
        if (this.sequencer != null && !this.songPlay) {
            this.sequencer.setTempoInBPM(n);
        }
    }

    private void loadStyle(String string, boolean bl) {
        Engine.Style style = this.styles.get(string);
        if (style == null) {
            return;
        }
        this.style = string;
        this.styleChosen = true;
        int[][] nArray = Engine.rowsToCells(style.rows);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, this.cells[i], 0, Engine.MAX_STEPS);
        }
        Engine.zeroCells(this.lens);
        if (this.steps == Engine.MAX_STEPS) {
            Engine.tileSteps(this.cells, Engine.STEPS, Engine.MAX_STEPS);
            Engine.tileSteps(this.lens, Engine.STEPS, Engine.MAX_STEPS);
        }
        if (!bl) {
            this.tempoBar.setVal(Engine.clampBpm(style.bpm));
            this.bpmField.setText(Integer.toString(this.bpm()));
            this.applyFeel(string, null);
        }
        this.refreshGrid();
        this.refreshStyles();
        this.syncBuiltinFill();
        if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
            this.rebuildAndPlay(true);
        }
        if ("combo".equals(this.view)) this.applyStoredFillern(this.patternKeyFor(string));
    }

    private void applyFeel(String styleId, Engine.Learned learned) {
        if (learned == null) {
            for (Engine.Learned x : this.learned) {
                if (x.id.equals(styleId)) { learned = x; break; }
            }
        }
        String feelId = learned != null && learned.closest != null ? learned.closest : styleId;
        int swing = learned != null && learned.swing >= 0 ? learned.swing : Engine.styleSwing(feelId);
        int dens = learned != null && learned.density >= 0 ? learned.density : Engine.styleDensity(feelId);
        int human = learned != null && learned.human >= 0 ? learned.human : Engine.styleHuman(feelId);
        if (this.swingBar != null) this.swingBar.setVal(Engine.clamp(swing, 0, 75));
        if (this.densBar != null) this.densBar.setVal(Engine.clamp(dens, 1, 10));
        if (this.humanBar != null) this.humanBar.setVal(Engine.clamp(human, 0, 100));
    }

    private void replicateStyle(String id) {
        Engine.Learned src = null;
        for (Engine.Learned x : this.learned) if (x.id.equals(id)) { src = x; break; }
        if (src == null) {
            for (Engine.Learned x : this.variatedPatterns) if (x.id.equals(id)) { src = x; break; }
        }
        Engine.Style st = this.styles.get(id);
        Engine.Learned item = new Engine.Learned();
        item.id = Engine.newLearnedId();
        if (src != null) {
            item.name = Engine.uniqueLearnedName(src.name + " copy", this.learned);
            item.bpm = src.bpm;
            item.cells = Engine.copyCells(src.cells);
            item.closest = src.closest;
            item.swing = src.swing >= 0 ? src.swing : this.swingBar.getVal();
            item.density = src.density >= 0 ? src.density : this.densBar.getVal();
            item.human = src.human >= 0 ? src.human : this.humanBar.getVal();
            item.source = src.source;
            item.tsNum = src.tsNum;
            item.tsDen = src.tsDen;
            item.steps = src.steps;
        } else if (st != null) {
            item.name = Engine.uniqueLearnedName(st.label + " copy", this.learned);
            item.bpm = st.bpm;
            item.cells = Engine.rowsToCells(st.rows);
            item.closest = id;
            item.swing = Engine.styleSwing(id);
            item.density = Engine.styleDensity(id);
            item.human = Engine.styleHuman(id);
        } else {
            return;
        }
        this.learned.add(0, item);
        while (this.learned.size() > Engine.MAX_LEARNED) this.learned.remove(this.learned.size() - 1);
        this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        String oldKey = null;
        for (Engine.Learned x : this.learned) if (x.id.equals(id)) { oldKey = "l:" + id; break; }
        if (oldKey == null) {
            for (Engine.Learned x : this.variatedPatterns) if (x.id.equals(id)) { oldKey = "v:" + id; break; }
        }
        if (oldKey == null && this.styles.containsKey(id) && !id.equals(item.id)) oldKey = this.patternKeyFor(id);
        String fk = oldKey == null ? null : this.fillernPairs.get(oldKey);
        if (fk != null) this.fillernPairs.put("l:" + item.id, fk);
        if (fk != null && this.fillernPicked.contains(oldKey)) this.fillernPicked.add("l:" + item.id);
        this.persistLearned();
        this.refreshLearnedChips();
        this.loadLearned(item.id);
        this.setNow("Copy \u00b7 " + item.name);
    }

    private void buildUi() {
        JButton jButton;
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setBackground(BG);
        jPanel.setBorder(BorderFactory.createEmptyBorder(6, 14, 0, 14));
        JPanel jPanel2 = new JPanel(new BorderLayout());
        jPanel2.setOpaque(false);
        JPanel jPanel3 = new JPanel(new GridLayout(2, 1, 0, 0));
        jPanel3.setOpaque(false);
        JLabel jLabel = new JLabel("DRUM MIDI");
        jLabel.setFont(new Font("SansSerif", 1, 8));
        jLabel.setForeground(SUBTLE);
        JLabel jLabel2 = new JLabel("PULSEKIT");
        jLabel2.setFont(new Font("SansSerif", 1, 16));
        jLabel2.setForeground(FG);
        jPanel3.add(jLabel);
        jPanel3.add(jLabel2);
        jPanel2.add((Component)jPanel3, "West");
        JPanel jPanel4 = new JPanel(new FlowLayout(2, 8, 0));
        jPanel4.setOpaque(false);
        JPanel jPanel5 = new JPanel(new GridLayout(2, 1, 0, 0));
        jPanel5.setOpaque(false);
        this.styleNumField(this.bpmField, 18);
        this.bpmField.setHorizontalAlignment(4);
        this.bindNumField(this.bpmField, 40, 240, n -> {
            this.tempoBar.setVal(n);
            this.onTempo(this.bpm());
        });
        JLabel jLabel3 = new JLabel("BPM", 4);
        jLabel3.setForeground(SUBTLE);
        jLabel3.setFont(new Font("SansSerif", 1, 11));
        jPanel5.add(this.bpmField);
        jPanel5.add(jLabel3);
        jPanel4.add(jPanel5);
        JPanel tsBox = new JPanel(new GridLayout(2, 1));
        tsBox.setOpaque(false);
        JPanel tsRow = new JPanel(new FlowLayout(2, 2, 0));
        tsRow.setOpaque(false);
        this.styleNumField(this.tsNumField, 14);
        this.styleNumField(this.tsDenField, 14);
        this.tsNumField.setHorizontalAlignment(4);
        this.tsDenField.setHorizontalAlignment(2);
        this.bindNumField(this.tsNumField, 1, 16, n -> this.applyTimeSig(n, this.tsDen));
        this.bindNumField(this.tsDenField, 2, 16, n -> this.applyTimeSig(this.tsNum, n));
        JLabel slash = new JLabel("/");
        slash.setForeground(MUTED);
        slash.setFont(new Font("Monospaced", 1, 18));
        tsRow.add(this.tsNumField);
        tsRow.add(slash);
        tsRow.add(this.tsDenField);
        JLabel tsCap = new JLabel("TS", 4);
        tsCap.setForeground(SUBTLE);
        tsCap.setFont(new Font("SansSerif", 1, 11));
        tsBox.add(tsRow);
        tsBox.add(tsCap);
        jPanel4.add(tsBox);
        this.muteBtn = this.outline("Mute", false);
        this.muteBtn.addActionListener(actionEvent -> {
            this.muted = !this.muted;
            this.muteBtn.setText(this.muted ? "Unmute" : "Mute");
            this.paintOutline(this.muteBtn, this.muted);
            this.applyMute();
        });
        jPanel4.add(this.muteBtn);
        this.autosaveBox = new JCheckBox("Autosave");
        this.autosaveBox.setOpaque(false);
        this.autosaveBox.setForeground(MUTED);
        this.autosaveBox.setFocusPainted(false);
        this.autosaveBox.setFont(new Font("SansSerif", 1, 12));
        this.autosaveBox.setToolTipText("Save the full project every few seconds to " + this.autosaveFile().getAbsolutePath());
        this.autosaveBox.addActionListener(e -> this.setAutosave(this.autosaveBox.isSelected(), true));
        jPanel4.add(this.autosaveBox);
        jPanel2.add((Component)jPanel4, "East");
        jPanel.add((Component)jPanel2, "North");
        JPanel jPanel6 = new JPanel(new BorderLayout(0, 8));
        jPanel6.setOpaque(false);
        JPanel jPanel7 = new JPanel(new GridLayout(2, 4, 6, 4));
        jPanel7.setOpaque(false);
        this.addTab(jPanel7, "pattern", "Pattern");
        this.addTab(jPanel7, "combo", "Fillern");
        this.addTab(jPanel7, "fills", "Fills");
        this.addTab(jPanel7, "pads", "Pads");
        this.addTab(jPanel7, "song", "Song");
        this.addTab(jPanel7, "py", "PyJav");
        this.addTab(jPanel7, "prompts", "Prompts");
        this.addFileTab(jPanel7);
        JPanel jPanel8 = new JPanel(new BorderLayout());
        jPanel8.setOpaque(false);
        jPanel8.add((Component)jPanel7, "North");
        this.nowPlaying.setForeground(HIT);
        this.nowPlaying.setFont(new Font("SansSerif", 1, 13));
        this.nowPlaying.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        this.nowPlaying.setVisible(false);
        jPanel8.add((Component)this.nowPlaying, "South");
        jPanel6.add((Component)jPanel8, "North");
        this.chrome = new JPanel();
        this.chrome.setOpaque(false);
        this.chrome.setLayout(new BoxLayout(this.chrome, 1));
        this.styleHost.setOpaque(false);
        this.styleHost.setLayout(new BoxLayout(this.styleHost, BoxLayout.Y_AXIS));
        this.styleHost.setAlignmentX(0.0f);
        this.styleHost.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        this.styleBar.setOpaque(false);
        this.styleBar.setAlignmentX(0.0f);
        for (String object2 : this.styles.keySet()) {
            final String sid = object2;
            jButton = this.chip(this.styles.get((Object)object2).label, false);
            jButton.addActionListener(actionEvent -> this.loadStyle(sid, false));
            jButton.putClientProperty("style", sid);
            this.onChipMenu(jButton, () -> this.builtinStyleMenu(sid));
            this.styleBar.add(jButton);
        }
        this.importedBar.setOpaque(false);
        this.importedBar.setAlignmentX(0.0f);
        this.importedBar.setLayout(new BoxLayout(this.importedBar, BoxLayout.Y_AXIS));
        this.importedFillBar.setOpaque(false);
        this.importedFillBar.setAlignmentX(0.0f);
        this.importedFillBar.setLayout(new BoxLayout(this.importedFillBar, BoxLayout.Y_AXIS));
        this.variatedPatternBar.setOpaque(false);
        this.variatedPatternBar.setAlignmentX(0.0f);
        this.styleHost.add(this.sectionLab("Built-in"));
        this.styleHost.add(this.styleBar);
        this.styleHost.add(this.sectionLab("Variated"));
        this.styleHost.add(this.variatedPatternBar);
        this.styleHost.add(this.sectionLab("Imported"));
        this.styleHost.add(this.cappedScroll(this.importedBar, 260));
        this.fillHost.setOpaque(false);
        this.fillHost.setLayout(new BoxLayout(this.fillHost, BoxLayout.Y_AXIS));
        this.fillHost.setAlignmentX(0.0f);
        this.fillHost.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
        this.fillBar.setOpaque(false);
        this.fillBar.setAlignmentX(0.0f);
        this.fillBar.setVisible(true);
        this.variatedFillBar.setOpaque(false);
        this.variatedFillBar.setAlignmentX(0.0f);
        this.importedFillBar.setOpaque(false);
        this.importedFillBar.setAlignmentX(0.0f);
        for (int i = 0; i < Engine.FILL_ID.length; ++i) {
            final String string = Engine.FILL_ID[i];
            jButton = this.chip(Engine.FILL_LABEL[i], false);
            jButton.putClientProperty("fill", string);
            jButton.addActionListener(actionEvent -> this.applyFill(string));
            final JButton fillChip = jButton;
            this.onRightClick(jButton, () -> this.builtinFillMenu(fillChip, string));
            this.fillBar.add(jButton);
        }
        JButton jButton2 = this.chip("Variate", false);
        jButton2.addActionListener(actionEvent -> this.variateFill());
        this.fillBar.add(jButton2);
        JButton jButton3 = this.chip("Apply", false);
        jButton3.addActionListener(actionEvent -> this.showView("combo"));
        this.fillBar.add(jButton3);
        this.knobsRow.setOpaque(false);
        this.knobsRow.setAlignmentX(0.0f);
        this.knobsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        this.knobsRow.add(this.knobBox("TEMPO", this.tempoBar, () -> Integer.toString(this.tempoBar.getVal())));
        this.knobsRow.add(this.knobBox("SWING", this.swingBar, () -> this.swingBar.getVal() + "%"));
        this.knobsRow.add(this.knobBox("DENSITY", this.densBar, () -> Integer.toString(this.densBar.getVal())));
        this.knobsRow.add(this.knobBox("HUMAN", this.humanBar, () -> this.humanBar.getVal() + "%"));
        this.toolsRow.setOpaque(false);
        this.toolsRow.setAlignmentX(0.0f);
        this.toolsRow.add(this.stepsBtn = this.outline("16 steps", false));
        this.stepsBtn.addActionListener(actionEvent -> this.toggleSteps());
        this.barsBtn = this.outline("4 bars out", false);
        this.barsBtn.addActionListener(actionEvent -> {
            this.bars = this.bars == 4 ? 8 : (this.bars == 8 ? 2 : 4);
            this.barsBtn.setText(this.bars + " bars out");
        });
        this.toolsRow.add(this.barsBtn);
        JButton varyBtn = this.outline("Variate", false);
        varyBtn.addActionListener(actionEvent -> this.variatePattern("random"));
        this.onChipMenu(varyBtn, () -> this.variatePatternMenu());
        this.toolsRow.add(varyBtn);
        jButton = this.outline("Silent", false);
        jButton.addActionListener(actionEvent -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < this.steps; ++j) {
                    this.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(this.lens);
            this.syncBuiltinFill();
            this.refreshGrid();
            this.setNow("Silent pattern");
        });
        this.toolsRow.add(jButton);
        this.fillHost.add(this.sectionLab("Built-in"));
        this.fillHost.add(this.fillBar);
        this.fillHost.add(this.sectionLab("Variated"));
        this.fillHost.add(this.variatedFillBar);
        this.fillHost.add(this.sectionLab("Imported"));
        this.fillHost.add(this.cappedScroll(this.importedFillBar, 160));
        this.fillHost.setVisible(false);
        this.chrome.add(this.styleHost);
        this.chrome.add(this.fillHost);
        this.chrome.add(Box.createVerticalStrut(8));
        this.chrome.add(this.knobsRow);
        this.chrome.add(this.toolsRow);
        JPanel jPanel9 = new JPanel(new BorderLayout(0, 6));
        jPanel9.setOpaque(false);
        jPanel9.add((Component)this.chrome, "North");
        this.pageHost.setOpaque(false);
        this.gridHost = new JPanel(new BorderLayout());
        this.gridHost.setOpaque(false);
        this.gridHost.add((Component)this.buildGrid(), "Center");
        this.pageHost.add((Component)this.gridHost, "grid");
        this.pageHost.add((Component)this.buildPads(), "pads");
        this.pageHost.add((Component)this.buildSongPage(), "song");
        this.pageHost.add((Component)this.buildPyPage(), "py");
        this.pageHost.add((Component)this.buildImportPage(), "import");
        this.pageHost.add((Component)this.buildExportPage(), "export");
        this.pageHost.add((Component)this.buildPromptsPage(), "prompts");
        this.pageHost.add((Component)this.buildInfoPage(), "fsetinfo");
        this.pageHost.add((Component)this.buildHelpPage(), "help");
        this.pageHost.add((Component)this.buildDrumMidiPage(), "midisettings");
        jPanel9.add((Component)this.pageHost, "Center");
        jPanel6.add((Component)jPanel9, "Center");
        jPanel.add((Component)jPanel6, "Center");
        JPanel jPanel10 = new JPanel();
        jPanel10.setLayout(new BoxLayout(jPanel10, 1));
        jPanel10.setBackground(SURFACE);
        jPanel10.setBorder(BorderFactory.createEmptyBorder(12, 18, 16, 18));
        JPanel jPanel11 = new JPanel();
        jPanel11.setOpaque(false);
        jPanel11.setLayout(new BoxLayout(jPanel11, 0));
        JButton jButton4 = this.action("\u25a0", ELEV, FG);
        jButton4.setPreferredSize(new Dimension(48, 48));
        jButton4.addActionListener(actionEvent -> this.stop());
        this.playBtn = this.action("Play", FG, BG);
        this.playBtn.setPreferredSize(new Dimension(240, 48));
        this.playBtn.addActionListener(actionEvent -> this.togglePlay());
        JButton jButton5 = this.action("Gen", HIT, BG);
        jButton5.setPreferredSize(new Dimension(72, 48));
        jButton5.addActionListener(actionEvent -> this.generate());
        jPanel11.add(jButton4);
        jPanel11.add(Box.createHorizontalStrut(8));
        jPanel11.add(this.playBtn);
        jPanel11.add(Box.createHorizontalStrut(8));
        jPanel11.add(jButton5);
        jPanel11.add(Box.createHorizontalGlue());
        jPanel10.add(jPanel11);
        jPanel.add((Component)jPanel10, "South");
        this.setContentPane(jPanel);
        this.refreshStyles();
        this.refreshFills();
        this.refreshTabs();
    }

    private JPanel knobBox(String string, RangeBar rangeBar, Supplier<String> supplier) {
        JPanel jPanel = new JPanel(new BorderLayout(0, 2));
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel(new BorderLayout());
        jPanel2.setOpaque(false);
        JLabel jLabel = new JLabel(string);
        jLabel.setForeground(SUBTLE);
        jLabel.setFont(new Font("SansSerif", 1, 10));
        JLabel jLabel2 = new JLabel(supplier.get(), 4);
        jLabel2.setForeground(MUTED);
        jLabel2.setFont(new Font("Monospaced", 0, 11));
        jPanel2.add((Component)jLabel, "West");
        jPanel2.add((Component)jLabel2, "East");
        rangeBar.onExtra = () -> jLabel2.setText((String)supplier.get());
        jPanel.add((Component)jPanel2, "North");
        jPanel.add((Component)rangeBar, "Center");
        return jPanel;
    }

    private void addTab(JPanel jPanel, String string, String string2) {
        JButton jButton = new JButton(string2);
        this.flatten(jButton);
        jButton.setFont(new Font("SansSerif", 1, 13));
        jButton.putClientProperty("tab", string);
        jButton.addActionListener(actionEvent -> this.showView(string));
        jPanel.add(jButton);
        this.tabs.add(jButton);
    }

    private void addFileTab(JPanel jPanel) {
        JButton jButton = new JButton("File");
        this.flatten(jButton);
        jButton.setFont(new Font("SansSerif", 1, 13));
        jButton.putClientProperty("tab", "file");
        jButton.addActionListener(actionEvent -> {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem imp = new JMenuItem("Import");
            imp.addActionListener(e -> this.showView("import"));
            JMenuItem exp = new JMenuItem("Export");
            exp.addActionListener(e -> this.showView("export"));
            JMenuItem midi = new JMenuItem("Drum Midi Settings");
            midi.addActionListener(e -> this.showView("midisettings"));
            JMenuItem help = new JMenuItem("Help-Desktop");
            help.addActionListener(e -> this.showView("help"));
            menu.add(imp);
            menu.add(exp);
            menu.add(midi);
            menu.add(help);
            menu.show(jButton, 0, jButton.getHeight());
        });
        jPanel.add(jButton);
        this.tabs.add(jButton);
    }

    private JPanel buildGrid() {
        int n;
        int cols = this.steps;
        JPanel jPanel = new JPanel(new GridLayout(Engine.TRACK_ID.length + 1, cols + 1, 3, 3));
        jPanel.setOpaque(false);
        Font font = new Font("SansSerif", 1, 11);
        JLabel jLabel = new JLabel("ACC");
        jLabel.setForeground(SUBTLE);
        jLabel.setFont(font);
        jPanel.add(jLabel);
        int pulse = Engine.stepsPerBeat(this.tsDen);
        int cellW = cols > 16 ? 22 : 28;
        for (n = 0; n < cols; ++n) {
            int n2 = n;
            JButton jButton = this.cellBtn(n % pulse == 0 ? Integer.toString(n / pulse + 1) : "\u00b7", cellW);
            jButton.addActionListener(actionEvent -> {
                this.accents[n2] = !this.accents[n2];
                this.refreshGrid();
            });
            this.accentBtns[n] = jButton;
            jPanel.add(jButton);
        }
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            JLabel jLabel2 = new JLabel(Engine.TRACK_SHORT[n]);
            jLabel2.setForeground(MUTED);
            jLabel2.setFont(font);
            final int n3 = n;
            jLabel2.addMouseListener(new MouseAdapter(){

                @Override
                public void mousePressed(MouseEvent mouseEvent) {
                    if (mouseEvent.getButton() == 3) {
                        Pulsekit.this.mutes[n3] = !Pulsekit.this.mutes[n3];
                        Pulsekit.this.refreshGrid();
                    } else {
                        Pulsekit.this.preview(n3);
                    }
                }
            });
            this.trackLabs[n] = jLabel2;
            jPanel.add(jLabel2);
            for (int i = 0; i < cols; ++i) {
                JButton jButton = this.cellBtn("", cellW);
                int n4 = i;
                jButton.addActionListener(actionEvent -> {
                    if (this.lenHeld) {
                        this.lenHeld = false;
                        return;
                    }
                    int[][] vel = this.editCells();
                    int[][] row = this.editLens();
                    int cur = vel[n3][n4];
                    vel[n3][n4] = cur <= 0 ? 100 : (cur < 90 ? 0 : (cur < 120 ? 127 : 64));
                    if (vel[n3][n4] <= 0) row[n3][n4] = 0;
                    this.refreshGrid();
                    if (!"fills".equals(this.view)) this.syncBuiltinFill();
                    if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
                        this.rebuildAndPlay(true);
                    }
                });
                jButton.addMouseListener(new MouseAdapter() {
                    private Timer hold;

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.getButton() == 3) {
                            Pulsekit.this.showLenMenu(jButton, n3, n4);
                            return;
                        }
                        if (e.getButton() != 1) return;
                        Pulsekit.this.lenHeld = false;
                        if (this.hold != null) this.hold.stop();
                        this.hold = new Timer(420, ev -> {
                            Pulsekit.this.lenHeld = true;
                            Pulsekit.this.showLenMenu(jButton, n3, n4);
                        });
                        this.hold.setRepeats(false);
                        this.hold.start();
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (this.hold != null) this.hold.stop();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        /* keep the hold; leaving the tiny cell is common */
                    }
                });
                this.buttons[n][i] = jButton;
                jPanel.add(jButton);
            }
        }
        this.refreshGrid();
        return jPanel;
    }

    private JPanel buildPads() {
        JPanel jPanel = new JPanel(new BorderLayout(8, 8));
        jPanel.setOpaque(false);
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        this.drumSetBar = new ChipStrip();
        this.drumSetBar.setAlignmentX(0.0f);
        north.add(this.drumSetBar);
        JPanel jPanel2 = new JPanel(new FlowLayout(0, 6, 4));
        jPanel2.setOpaque(false);
        jPanel2.setAlignmentX(0.0f);
        JButton jButton = this.outline("Live", false);
        jButton.addActionListener(actionEvent -> {
            this.livePads = !this.livePads;
            this.paintOutline(jButton, this.livePads);
            this.setNow(this.livePads ? "Pads write the pattern" : "Tap a pad");
        });
        JButton jButton2 = this.outline("Silent", false);
        jButton2.addActionListener(actionEvent -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < this.steps; ++j) {
                    this.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(this.lens);
            this.syncBuiltinFill();
            this.refreshGrid();
            this.setNow("Silent pattern");
        });
        jPanel2.add(jButton);
        jPanel2.add(jButton2);
        this.padMatchAll = this.outline("Match Original", true);
        this.padMatchAll.setVisible(false);
        this.padMatchAll.addActionListener(e -> this.matchWholeOriginal());
        jPanel2.add(this.padMatchAll);
        JLabel jLabel = new JLabel("  Tap Match Original for the whole kit");
        jLabel.setForeground(SUBTLE);
        jPanel2.add(jLabel);
        north.add(jPanel2);
        jPanel.add((Component)north, "North");
        JPanel jPanel3 = new JPanel(new GridLayout(3, 4, 10, 10));
        jPanel3.setOpaque(false);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            final int n = i;
            JButton jButton3 = this.action(Engine.TRACK_SHORT[i] + "  " + Engine.TRACK_LABEL[i], ELEV, FG);
            jButton3.setPreferredSize(new Dimension(120, 88));
            jButton3.addActionListener(actionEvent -> {
                this.preview(n);
                this.flashPad(n);
                if (this.livePads) {
                    int n2 = this.playhead >= 0 ? this.playhead : 0;
                    this.cells[n][n2] = this.cells[n][n2] > 0 ? 0 : 100;
                    if (this.cells[n][n2] <= 0) this.lens[n][n2] = 0;
                    this.syncBuiltinFill();
                    this.refreshGrid();
                }
            });
            this.onChipMenu(jButton3, () -> this.padMenu(n));
            this.padBtns[i] = jButton3;
            jPanel3.add(jButton3);
        }
        jPanel.add((Component)jPanel3, "Center");
        this.refreshDrumSets();
        return jPanel;
    }

    private JPanel buildSongPage() {
        JPanel jPanel = new JPanel(new BorderLayout(8, 8));
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel();
        jPanel2.setOpaque(false);
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        JPanel jPanel3 = new JPanel(new FlowLayout(0, 6, 0));
        jPanel3.setOpaque(false);
        JButton jButton = this.chip("Edit", true);
        JButton jButton2 = this.chip("Play", false);
        jButton.addActionListener(actionEvent -> {
            this.songMode = "edit";
            this.songAdds.setVisible(true);
            this.chrome.setVisible(true);
            this.styleHost.setVisible(false);
            this.fillBar.setVisible(false);
            this.paintChip(jButton, true);
            this.paintChip(jButton2, false);
            this.refreshSong();
        });
        jButton2.addActionListener(actionEvent -> {
            this.songMode = "play";
            this.songAdds.setVisible(false);
            this.chrome.setVisible(false);
            this.paintChip(jButton, false);
            this.paintChip(jButton2, true);
            this.refreshSong();
        });
        jPanel3.add(jButton);
        jPanel3.add(jButton2);
        this.songLaneBar = new JPanel(new FlowLayout(0, 6, 0));
        this.songLaneBar.setOpaque(false);
        JButton origLane = this.chip("Original", true);
        JButton impLane = this.chip("Imported", false);
        origLane.addActionListener(e -> {
            this.songLane = "original";
            this.paintChip(origLane, true);
            this.paintChip(impLane, false);
            this.refreshSong();
        });
        impLane.addActionListener(e -> {
            this.songLane = "imported";
            if (this.importedSongId == null && !this.importedSongs.isEmpty()) this.importedSongId = this.importedSongs.get(0).id;
            this.paintChip(origLane, false);
            this.paintChip(impLane, true);
            this.refreshSong();
        });
        origLane.putClientProperty("lane", "original");
        impLane.putClientProperty("lane", "imported");
        this.songLaneBar.add(origLane);
        this.songLaneBar.add(impLane);
        this.songAdds.setOpaque(false);
        JButton jButton3 = this.chip("Pattern \u00d74", false);
        jButton3.addActionListener(actionEvent -> {
            Engine.Part p = Engine.groove(this.styles.get((Object)this.style).label, this.bpm(), this.cells, 4);
            p.lens = Engine.copyCells(this.lens);
            this.addPart(p);
        });
        this.onChipMenu(jButton3, () -> this.songPickMenu(null, "pattern"));
        JButton jButtonFillern = this.chip("Fillern", false);
        jButtonFillern.addActionListener(actionEvent -> this.addCurrentFillern());
        this.onChipMenu(jButtonFillern, () -> this.songPickMenu(null, "fillern"));
        JButton jButton4 = this.chip("Fill", false);
        jButton4.addActionListener(actionEvent -> {
            Engine.Part p = Engine.fill(this.fillLabel(this.fillId), this.bpm(), this.fillPat, 1);
            p.lens = Engine.copyCells(this.fillLens);
            this.addPart(p);
        });
        this.onChipMenu(jButton4, () -> this.songPickMenu(null, "fill"));
        JButton jButton5 = this.chip("Silent", false);
        jButton5.addActionListener(actionEvent -> this.addPart(Engine.rest(this.bpm(), 2)));
        JButton jButton6 = this.chip("Up", false);
        JButton jButton7 = this.chip("Down", false);
        JButton jButton8 = this.chip("Remove", false);
        JList<String> jList = new JList<String>(this.songModel);
        this.songList = jList;
        jList.setBackground(ELEV);
        jList.setForeground(FG);
        jList.setSelectionBackground(HIT);
        jList.setSelectionForeground(BG);
        jList.setFont(new Font("SansSerif", 0, 14));
        jButton6.addActionListener(actionEvent -> this.movePart(jList.getSelectedIndex(), -1));
        jButton7.addActionListener(actionEvent -> this.movePart(jList.getSelectedIndex(), 1));
        jButton8.addActionListener(actionEvent -> {
            int n = jList.getSelectedIndex();
            List<Engine.Part> cur = this.activeSong();
            if (n >= 0 && n < cur.size()) {
                cur.remove(n);
                if ("imported".equals(this.songLane)) this.persistLearned();
                this.refreshSong();
            }
        });
        jList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!(e.isPopupTrigger() || e.getButton() == 3)) return;
                if ("imported".equals(Pulsekit.this.songLane) && "edit".equals(Pulsekit.this.songMode)) {
                    int at = jList.locationToIndex(e.getPoint());
                    if (at < 0) return;
                    jList.setSelectedIndex(at);
                    Pulsekit.this.importedPartMenu(at).show(jList, e.getX(), e.getY());
                    return;
                }
                if (!"original".equals(Pulsekit.this.songLane) || !"edit".equals(Pulsekit.this.songMode)) return;
                int idx = jList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                jList.setSelectedIndex(idx);
                JPopupMenu menu = Pulsekit.this.songPickMenu(idx);
                if (menu.getComponentCount() > 0) menu.show(jList, e.getX(), e.getY());
            }
        });
        this.songAdds.add(jButton3);
        this.songAdds.add(jButtonFillern);
        this.songAdds.add(jButton4);
        this.songAdds.add(jButton5);
        JButton saveToSet = this.chip("Save to set", false);
        saveToSet.addActionListener(actionEvent -> this.saveSongToFileSet(saveToSet));
        this.songAdds.add(saveToSet);
        this.songAdds.add(jButton6);
        this.songAdds.add(jButton7);
        this.songAdds.add(jButton8);
        JButton jButtonClear = this.chip("Clear", false);
        jButtonClear.addActionListener(actionEvent -> {
            if ("imported".equals(this.songLane) && this.importedSongId != null) {
                String id = this.importedSongId;
                this.importedSongs.removeIf(s -> id.equals(s.id));
                this.importedSongId = this.importedSongs.isEmpty() ? null : this.importedSongs.get(0).id;
                this.persistLearned();
            } else {
                this.song.clear();
            }
            this.refreshSong();
        });
        this.songAdds.add(jButtonClear);
        this.timeline.setOpaque(false);
        jPanel2.add(jPanel3);
        jPanel2.add(this.songLaneBar);
        jPanel2.add(this.songAdds);
        // The cell strip scrolls sideways and follows the playing part.
        this.timelineScroll = new JScrollPane(this.timeline, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, this.getPreferredSize().height);
            }
        };
        this.timelineScroll.setOpaque(false);
        this.timelineScroll.getViewport().setOpaque(false);
        this.timelineScroll.setBorder(BorderFactory.createEmptyBorder());
        this.timelineScroll.setAlignmentX(0.0f);
        this.timelineScroll.getHorizontalScrollBar().setUnitIncrement(24);
        jPanel2.add(this.timelineScroll);
        jPanel.add((Component)jPanel2, "North");
        JScrollPane jScrollPane = new JScrollPane(jList);
        jScrollPane.getViewport().setBackground(ELEV);
        jScrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        jPanel.add((Component)jScrollPane, "Center");
        JLabel jLabel = new JLabel("Numbers are add-order. Same pattern keeps the same number. Play uses per-part tempo.");
        jLabel.setForeground(MUTED);
        jPanel.add((Component)jLabel, "South");
        return jPanel;
    }

    private JPanel buildPyPage() {
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setOpaque(false);
        JLabel jLabel = new JLabel("PyJav");
        jLabel.setFont(new Font("SansSerif", 1, 20));
        jLabel.setForeground(FG);
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, 1));
        north.add(jLabel);
        north.add(Box.createVerticalStrut(8));
        this.pyRecent = new JComboBox<String>();
        this.pyRecent.setBackground(ELEV);
        this.pyRecent.setForeground(FG);
        this.pyRecent.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        this.pyRecent.setAlignmentX(0.0f);
        this.reloadPyRecent(0);
        this.pyRecent.addActionListener(ev -> this.applyPyRecent());
        north.add(this.pyRecent);
        north.add(Box.createVerticalStrut(6));
        north.add(this.buildProgramMenus());
        this.pyHint = new JLabel("Possible extra args appear here after you browse a file.");
        this.pyHint.setForeground(FG);
        this.pyHint.setAlignmentX(0.0f);
        north.add(Box.createVerticalStrut(6));
        north.add(this.pyHint);
        north.add(Box.createVerticalStrut(8));
        JPanel modes = new JPanel(new FlowLayout(0, 8, 0));
        modes.setOpaque(false);
        modes.setAlignmentX(0.0f);
        this.pyRunBash = this.action("bash", ELEV, FG, () -> this.selectPromptRun("bash"));
        this.pyRunCmd = this.action("cmd", ELEV, FG, () -> this.selectPromptRun("cmd"));
        this.pyRunAi = this.action("AI", ELEV, FG, () -> this.selectPromptRun("ai"));
        modes.add(this.pyRunBash);
        modes.add(this.pyRunCmd);
        modes.add(this.pyRunAi);
        this.pyPromptModes = modes;
        this.pyPromptModes.setVisible(false);
        north.add(modes);
        this.paintPromptModes();
        JPanel controls = new JPanel(new FlowLayout(0, 8, 0));
        controls.setOpaque(false);
        controls.setAlignmentX(0.0f);
        controls.add(this.action("Browse file", ELEV, FG, () -> this.browsePyFile()));
        this.pyInputBtn = this.action("Browse input", ELEV, FG, () -> this.browseInputFile());
        this.pyInputBtn.setVisible(false);
        controls.add(this.pyInputBtn);
        this.pyRun = this.action("Run", HIT, BG, () -> this.runPython());
        controls.add(this.pyRun);
        JLabel extraLab = new JLabel("Extra args");
        extraLab.setForeground(MUTED);
        controls.add(extraLab);
        this.pyExtra = new JTextField(22);
        this.pyExtra.setBackground(ELEV);
        this.pyExtra.setForeground(FG);
        this.pyExtra.setCaretColor(FG);
        this.pyExtra.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        this.pyExtra.setToolTipText("Optional extra command-line flags, e.g. --out groove.mid");
        controls.add(this.pyExtra);
        north.add(controls);
        this.pyEditor = new JTextArea(PythonRun.defaultScript());
        this.pyEditor.setFont(new Font(Font.MONOSPACED, 0, 12));
        this.pyEditor.setBackground(ELEV);
        this.pyEditor.setForeground(FG);
        this.pyEditor.setCaretColor(FG);
        this.pyEditor.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(this.pyEditor);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        this.pyLog = new JTextArea(6, 40);
        this.pyLog.setEditable(false);
        this.pyLog.setLineWrap(true);
        this.pyLog.setWrapStyleWord(true);
        this.pyLog.setFont(new Font(Font.MONOSPACED, 0, 11));
        this.pyLog.setBackground(ELEV);
        this.pyLog.setForeground(MUTED);
        this.pyLog.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.pyLog.setText("Output appears here.");
        JScrollPane logScroll = new JScrollPane(this.pyLog);
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        logScroll.setPreferredSize(new Dimension(100, 120));
        jPanel.add((Component)north, "North");
        jPanel.add((Component)scroll, "Center");
        jPanel.add((Component)logScroll, "South");
        return jPanel;
    }

    private void browsePyFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Python, Java, or prompt");
        chooser.setFileFilter(new FileNameExtensionFilter("Python, Java, JavaScript, or prompt", "py", "java", "class", "jar", "js", "mjs", "cjs", "ts", "tsx", "prompt"));
        if (chooser.showOpenDialog(this) != 0) return;
        File file = chooser.getSelectedFile();
        if (file == null) return;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String name = file.getName();
            String low = name.toLowerCase();
            boolean binary = low.endsWith(".jar") || low.endsWith(".class");
            if (!(low.endsWith(".py") || low.endsWith(".java") || low.endsWith(".prompt") || low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".cjs") || low.endsWith(".ts") || low.endsWith(".tsx") || binary)) {
                if (this.pyLog != null) this.pyLog.setText("Pick a .py, .java, .js, .ts, .class, .jar, or .prompt file.");
                return;
            }
            if (low.endsWith(".jar") && JavaRun.zipEntry(data, "pattern.json") != null) {
                if (this.pyLog != null) this.pyLog.setText("That JAR is a kit snapshot, not a program.");
                return;
            }
            this.loadProgram(name, data, binary);
            this.pyInputPath = null;
            String src = binary ? "" : new String(data, StandardCharsets.UTF_8);
            String status = low.endsWith(".prompt")
                ? "Run as bash, cmd, or AI."
                : PyJavHints.status(name, src, data);
            PyJavRecent.remember(this.pyRecentDir(), name, "", src, binary ? data : null);
            this.reloadPyRecent(0);
            this.showPyHint(status);
        } catch (Exception ex) {
            if (this.pyLog != null) this.pyLog.setText("Could not open that file: " + ex.getMessage());
        }
    }

    private void showPromptModes() {
        boolean prompt = this.pyName != null && this.pyName.toLowerCase().endsWith(".prompt");
        if (this.pyPromptModes != null) this.pyPromptModes.setVisible(prompt);
    }

    private void selectPromptRun(String mode) {
        if (mode == null) mode = "bash";
        String chosen = PromptRun.normalizeType(mode);
        if (chosen.length() == 0 && "ai".equals(mode.toLowerCase())) chosen = "ai";
        if (chosen.length() == 0) chosen = "bash";
        this.promptRunMode = chosen;
        this.paintPromptModes();
        if (this.pyName != null && this.pyName.toLowerCase().endsWith(".prompt")) this.showPromptReport();
    }

    private void paintPromptModes() {
        this.paintPromptMode(this.pyRunBash, "bash");
        this.paintPromptMode(this.pyRunCmd, "cmd");
        this.paintPromptMode(this.pyRunAi, "ai");
    }

    private void showPromptReport() {
        DesktopAi.Found found = DesktopAi.present();
        String report = PromptRun.report(this.promptCategory, this.promptRunMode, found.grok, found.sogni, found.claude);
        if (this.promptOutputName != null && this.promptOutputName.length() > 0) report = report + "\nOutput file: " + this.promptOutputName;
        if (this.pyLog != null) this.pyLog.setText(report);
        if (this.pyHint != null) this.pyHint.setText(report);
    }

    /** Picks an output name. Asks when the format is unclear. Blank keeps a log. False means cancel. */
    private boolean preparePromptOutput() {
        String defined = this.promptResult == null ? "" : this.promptResult.trim();
        String body = this.pyEditor == null ? "" : this.pyEditor.getText();
        String fileName = PromptRun.chooseOutputName(this.pyName, this.promptCategory, this.promptRunMode, this.promptModel, body, defined);
        this.promptOutputInvented = defined.length() == 0;
        if (this.promptOutputInvented && PromptRun.logOutput(fileName)) {
            String answer = JOptionPane.showInputDialog(this, "The output format is unclear. Enter a name and extension, or leave blank and PyJav will write a log.", "Output file name", JOptionPane.QUESTION_MESSAGE);
            if (answer == null) return false;
            answer = answer.trim();
            if (answer.length() > 0) fileName = PromptRun.chooseOutputName(this.pyName, this.promptCategory, this.promptRunMode, this.promptModel, body, answer);
        }
        this.promptOutputName = fileName;
        File dir = new File(System.getProperty("user.home", "."), ".pulsekit/pyjav-out");
        if (!dir.isDirectory()) dir.mkdirs();
        this.pyOutputPath = new File(dir, fileName).getAbsolutePath();
        return true;
    }

    private void runDesktopAi(String body) {
        DesktopAi.Found found = DesktopAi.present();
        if (found.choices.isEmpty()) {
            String report = PromptRun.report(this.promptCategory, "ai", false, false, false);
            if (this.pyLog != null) this.pyLog.setText(report);
            return;
        }
        DesktopAi.Choice chosen = found.choices.get(0);
        if (found.choices.size() > 1) {
            String[] labels = new String[found.choices.size()];
            for (int i = 0; i < found.choices.size(); i++) labels[i] = found.choices.get(i).name;
            Object pick = JOptionPane.showInputDialog(this, "Which system should run this prompt?", "PyJav", JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
            if (pick == null) {
                if (this.pyLog != null) this.pyLog.setText("Cancelled.");
                return;
            }
            for (int i = 0; i < found.choices.size(); i++) {
                if (found.choices.get(i).name.equals(pick)) chosen = found.choices.get(i);
            }
        }
        final DesktopAi.Choice choice = chosen;
        final String prompt = body == null ? "" : body;
        if (!"api".equals(choice.kind)) {
            String line = DesktopAi.launch(choice, prompt);
            String shown = PromptRun.report(this.promptCategory, "ai", found.grok, found.sogni, found.claude) + "\n" + line + "\nOutput file: " + this.promptOutputName;
            this.writePromptOutput(shown);
            if (this.pyLog != null) this.pyLog.setText(shown);
            this.setNow(line);
            return;
        }
        if (this.pyLog != null) this.pyLog.setText("Running…");
        if (this.pyRun != null) this.pyRun.setEnabled(false);
        new Thread(() -> {
            final JavaRun.Result result = PromptRun.run(prompt, "ai");
            SwingUtilities.invokeLater(() -> {
                if (this.pyRun != null) this.pyRun.setEnabled(true);
                String shown = result == null || result.log == null || result.log.length() == 0 ? "(no output)" : result.log;
                shown = shown + "\nOutput file: " + this.promptOutputName;
                this.writePromptOutput(shown);
                if (this.pyLog != null) this.pyLog.setText(shown);
                this.setNow("Grok");
            });
        }, "pulsekit-ai").start();
    }

    private void writePromptOutput(String text) {
        if (!this.promptOutputInvented || this.promptOutputName == null || this.pyOutputPath == null) return;
        try {
            File file = new File(this.pyOutputPath);
            if (file.isFile() && file.length() > 0) return;
            if (!PromptRun.logOutput(this.promptOutputName)) return;
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            Files.write(file.toPath(), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private void paintPromptMode(JButton button, String mode) {
        if (button == null) return;
        boolean on = mode.equals(this.promptRunMode);
        button.setBackground(on ? HIT : ELEV);
        button.setForeground(on ? BG : FG);
    }

    private void runPython() {
        if (this.pyEditor == null) return;
        final String listed = this.listedProgramCurrent() ? this.listedSource : null;
        final String src = listed != null ? listed : this.pyEditor.getText();
        final String name = this.pyName == null || this.pyName.isEmpty() ? "drum_midi.py" : this.pyName;
        final byte[] bytes = this.pyBytes;
        final boolean promptProg = name.toLowerCase().endsWith(".prompt");
        final String runSrc = promptProg ? PromptRun.withoutDescription(this.promptDescription, src) : src;
        if (promptProg && !this.preparePromptOutput()) {
            if (this.pyLog != null) this.pyLog.setText("Cancelled.");
            return;
        }
        if (promptProg && "ai".equals(this.promptRunMode)) {
            this.runDesktopAi(runSrc);
            return;
        }
        if (!promptProg) {
            this.promptOutputInvented = false;
            this.promptOutputName = "";
        }
        final String extra = this.pyExtra != null ? this.pyExtra.getText() : "";
        File outDir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (this.pyInputPath != null && this.pyInputPath.length() > 0) {
            File parent = new File(this.pyInputPath).getParentFile();
            if (parent != null) outDir = parent;
        }
        if (!outDir.isDirectory()) outDir.mkdirs();
        final String filled = PyJavHints.fillArgs(extra, this.pyHintPlain, this.pyInputToken, this.pyInputPath, outDir.getAbsolutePath());
        String hintedOut = PyJavHints.outputFile(this.pyInputPath, (this.pyHintPlain == null ? "" : this.pyHintPlain) + "\n" + extra, outDir.getAbsolutePath());
        if (!(promptProg && this.promptOutputInvented && this.pyOutputPath != null && this.pyOutputPath.length() > 0)) this.pyOutputPath = hintedOut;
        final String outPath = this.pyOutputPath == null ? "" : this.pyOutputPath;
        if (this.pyExtra != null && filled.length() > 0) this.pyExtra.setText(filled);
        PyJavRecent.remember(this.pyRecentDir(), name, filled, src, bytes);
        this.reloadPyRecent(0);
        final java.util.List<String> argv;
        if ((this.pyInputPath != null && this.pyInputPath.length() > 0) || outPath.length() > 0) {
            argv = PyJavHints.programArgs(filled, this.pyInputToken, this.pyInputPath, this.pyHintPlain, outDir.getAbsolutePath());
        } else {
            argv = PythonRun.kitArgv(src, this.bpm(), this.style, this.bars, this.swingBar.getVal());
            argv.addAll(PythonRun.splitArgv(extra));
        }
        if (this.pyLog != null) this.pyLog.setText("Running…");
        if (this.pyRun != null) this.pyRun.setEnabled(false);
        new Thread(() -> {
            final boolean javaProg = !promptProg && isJavaName(name);
            java.util.List<String> runArgv = argv;
            if (promptProg) {
                runArgv = new java.util.ArrayList<String>(argv);
                runArgv.add("--pk-run");
                runArgv.add(this.promptRunMode);
                if (this.promptOutputInvented && outPath.length() > 0 && !runArgv.contains(outPath)) runArgv.add(outPath);
            }
            final PythonRun.Result result = promptProg
                ? asPython(JavaRun.run(name, runSrc, bytes, runArgv))
                : javaProg
                    ? asPython(JavaRun.run(name, src, bytes, argv))
                    : PythonRun.run(src, name, argv);
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (this.pyRun != null) this.pyRun.setEnabled(true);
                String shown = result.log == null || result.log.isEmpty() ? "(no output)" : result.log;
                if (promptProg && this.promptOutputName.length() > 0) shown = shown + "\nOutput file: " + this.promptOutputName;
                this.writePromptOutput(shown);
                if (this.pyLog != null) this.pyLog.setText(shown);
                boolean loaded = false;
                int midis = 0;
                StringBuilder status = new StringBuilder();
                for (PythonRun.FileOut f : result.files) {
                    String lower = f.name.toLowerCase();
                    boolean midi = f.bytes != null && f.bytes.length >= 4
                        && f.bytes[0] == 'M' && f.bytes[1] == 'T' && f.bytes[2] == 'h' && f.bytes[3] == 'd';
                    if ((lower.endsWith(".mid") || lower.endsWith(".midi")) && midi) {
                        status.append(this.applyProgramMidi(f.bytes, f.name)).append('\n');
                        midis++;
                        loaded = true;
                    } else if (lower.endsWith(".sng")) {
                        try {
                            java.util.List<Engine.Part> parts = Engine.decodeSng(f.bytes);
                            if (parts != null && !parts.isEmpty()) {
                                this.song.clear();
                                this.song.addAll(parts);
                                this.songLane = "original";
                                this.refreshSong();
                                this.showView("song");
                                this.setNow(parts.size() + " parts from script");
                            }
                        } catch (Exception ex) {
                            if (this.pyLog != null) this.pyLog.append("\nCould not read " + f.name);
                        }
                    }
                }
                if (!promptProg && midis == 0 && this.pyOutputPath != null && this.pyOutputPath.length() > 0) {
                    java.io.File out = new java.io.File(this.pyOutputPath);
                    if (out.isFile()) {
                        try {
                            status.append(this.applyProgramMidi(java.nio.file.Files.readAllBytes(out.toPath()), out.getName())).append('\n');
                            midis++;
                            loaded = true;
                        } catch (Exception ex) {
                            status.append("Import failed: ").append(ex.getMessage() == null ? "could not read the output file" : ex.getMessage()).append('\n');
                        }
                    }
                }
                if (midis == 0 && promptProg) {
                    String line = result.log == null ? "" : result.log;
                    int nl = line.indexOf('\n');
                    String first = (nl < 0 ? line : line.substring(0, nl)).trim();
                    if (first.isEmpty()) first = result.code == 0 ? "Prompt finished" : "Prompt failed";
                    status.append(first).append('\n');
                } else if (midis == 0) status.append("Import failed: no MIDI file was written\n");
                if (this.pyHint != null && status.length() > 0) this.pyHint.setText(hintHtml(status.toString().trim()));
                if (status.length() > 0) this.setNow(status.toString().trim().split("\n")[0]);
                if (this.pyLog != null && status.length() > 0) {
                    this.pyLog.setText(status.toString() + (this.pyLog.getText() == null ? "" : "\n" + this.pyLog.getText()));
                }
                if (loaded) this.setNow("Script MIDI · " + name);
            });
        }, "pulsekit-pyjav").start();
    }

    private File pyRecentDir() {
        File dir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    private void reloadPyRecent(int select) {
        this.pyRecentItems = PyJavRecent.load(this.pyRecentDir());
        if (this.pyRecent == null) return;
        this.pyRecentMute = true;
        this.pyRecent.removeAllItems();
        this.pyRecent.addItem("Recent");
        for (PyJavRecent.Item item : this.pyRecentItems) this.pyRecent.addItem(item.label());
        int index = select > 0 && select <= this.pyRecentItems.size() ? select : 0;
        this.pyRecent.setSelectedIndex(index);
        this.pyRecentMute = false;
    }

    private void applyPyRecent() {
        if (this.pyRecentMute || this.pyRecent == null) return;
        int index = this.pyRecent.getSelectedIndex();
        if (index <= 0 || this.pyRecentItems == null || index > this.pyRecentItems.size()) return;
        PyJavRecent.Item item = this.pyRecentItems.get(index - 1);
        this.pyInputPath = null;
        boolean binary = item.bytes != null && item.bytes.length > 0;
        byte[] data = binary ? item.bytes : (item.source == null ? new byte[0] : item.source.getBytes(StandardCharsets.UTF_8));
        this.rememberProgram(item.name, data, binary, false);
        this.pyInputPath = null;
        this.showPyHint(PyJavHints.status(item.name, item.source, item.bytes));
        // The hint fills args from the program's usage; the recent item's own args win.
        if (this.pyExtra != null) this.pyExtra.setText(item.extra);
    }

    private void showPyHint(String status) {
        this.pyHintPlain = status == null ? "" : status;
        if (this.pyInputPath == null && this.audioInputPath != null && new File(this.audioInputPath).isFile()) {
            this.pyInputPath = this.audioInputPath;
        }
        if (this.pyLog != null) this.pyLog.setText(this.pyHintPlain);
        if (this.pyHint != null) this.pyHint.setText(hintHtml(this.pyHintPlain));
        this.pyInputToken = PyJavHints.firstInput(this.pyHintPlain);
        File outDir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (!outDir.isDirectory()) outDir.mkdirs();
        String out = PyJavHints.outputFile(this.pyInputPath, this.pyHintPlain, outDir.getAbsolutePath());
        if (out.length() > 0) {
            this.pyOutputPath = out;
            String filled = PyJavHints.fillArgs("", this.pyHintPlain, this.pyInputToken, this.pyInputPath, outDir.getAbsolutePath());
            if (this.pyExtra != null) this.pyExtra.setText(filled);
            String note = this.pyHintPlain + "\nOutput file: " + out + "\nExtra args: " + filled;
            if (this.pyLog != null) this.pyLog.setText(note);
            if (this.pyHint != null) this.pyHint.setText(hintHtml(note));
        }
        if (this.pyInputBtn != null) {
            this.pyInputBtn.setVisible(this.pyInputToken != null);
            if (this.pyInputToken != null) this.pyInputBtn.setText("Browse " + this.pyInputToken);
        }
    }

    /** Last WAV/MP3 imported as PyJav input; programs opened later still get it. */
    private String audioInputPath;

    /** Imported WAV or MP3 becomes the input of the PyJav program, e.g. MidiDrumGen.java. */
    private void useAudioInput(File file) {
        this.audioInputPath = file.getAbsolutePath();
        this.showView("py");
        this.setInputFile(file);
        this.setNow("PyJav input \u00b7 " + file.getName() + " \u00b7 run MidiDrumGen.java to make MIDI");
    }

    private void browseInputFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(this.pyInputToken == null ? "Input file" : this.pyInputToken);
        chooser.setAcceptAllFileFilterUsed(true);
        if (chooser.showOpenDialog(this) != 0) return;
        File file = chooser.getSelectedFile();
        if (file == null) return;
        this.setInputFile(file);
    }

    private void setInputFile(File file) {
        if (this.pyExtra == null) return;
        this.pyInputPath = file.getAbsolutePath();
        File folder = file.getParentFile() == null ? new File(System.getProperty("user.home", "."), ".pulsekit") : file.getParentFile();
        String note = PyJavHints.outputNotice(this.pyExtra.getText(), this.pyHintPlain, this.pyInputPath, folder.getAbsolutePath());
        String args = PyJavHints.fillArgs(this.pyExtra.getText(), this.pyHintPlain, this.pyInputToken, this.pyInputPath, folder.getAbsolutePath());
        this.pyOutputPath = PyJavHints.outputFile(this.pyInputPath, this.pyHintPlain, folder.getAbsolutePath());
        this.pyExtra.setText(args);
        if (this.pyLog != null) this.pyLog.setText(note);
        if (this.pyHint != null) this.pyHint.setText(hintHtml(note));
    }

    private static byte[] headOf(File file) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                byte[] buf = new byte[16];
                int n = in.read(buf);
                if (n <= 0) return new byte[0];
                if (n == buf.length) return buf;
                byte[] cut = new byte[n];
                System.arraycopy(buf, 0, cut, 0, n);
                return cut;
            } finally {
                in.close();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static String hintHtml(String status) {
        String safe = status == null ? "" : status.replace("&", "&" + "amp;").replace("<", "&" + "lt;").replace(">", "&" + "gt;");
        return "<html><body style='width:520px;color:#F4F1EA'>" + safe.replace("\n", "<br>") + "</body></html>";
    }

    private static boolean isJavaName(String name) {
        String n = name == null ? "" : name.toLowerCase();
        return n.endsWith(".java") || n.endsWith(".jar") || n.endsWith(".class");
    }

    private static PythonRun.Result asPython(JavaRun.Result r) {
        java.util.ArrayList<PythonRun.FileOut> files = new java.util.ArrayList<PythonRun.FileOut>();
        if (r != null && r.files != null) {
            for (JavaRun.FileOut f : r.files) files.add(new PythonRun.FileOut(f.name, f.bytes));
        }
        return new PythonRun.Result(r == null ? "" : r.log, files, r == null ? 1 : r.code);
    }

    private void loadProgram(String name, byte[] data, boolean binary) {
        this.rememberProgram(name, data, binary, true);
    }

    private void rememberProgram(String name, byte[] data, boolean binary, boolean open) {
        this.pyName = name == null || name.isEmpty() ? "script.py" : name;
        this.pyBytes = binary ? data : null;
        if (this.pyEditor != null) {
            this.pyEditor.setEditable(!binary);
            if (binary) {
                this.pyEditor.setText("// " + this.pyName + "\n// Binary program. Run uses this file.\n// Extra args are passed to java.\n");
            } else {
                String text = new String(data, StandardCharsets.UTF_8);
                if (this.pyName.toLowerCase().endsWith(".prompt")) {
                    PromptRun.Sheet sheet = PromptRun.parse(text);
                    this.promptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
                    this.promptModel = sheet == null || sheet.model == null ? "" : sheet.model;
                    this.promptResult = sheet == null || sheet.result == null ? "" : sheet.result;
                    this.promptDescription = sheet == null || sheet.description == null ? "" : sheet.description;
                    this.promptOutputName = "";
                    this.selectPromptRun(PromptRun.runMode(text, sheet));
                    if (sheet != null && sheet.body != null) text = PromptRun.withoutDescription(this.promptDescription, sheet.body);
                }
                this.pyEditor.setText(text);
            }
        }
        this.showPromptModes();
        this.paintProgramMenus();
        if (!open) return;
        this.showView("py");
        this.setNow("PyJav · " + this.pyName);
    }

    // ---- Java / Python / Code menus (Programs folder) -----------------------------
    // Java and Python pick the program Run executes; the editor is left alone.
    // Code opens a file in the editor for editing and never changes what Run executes.

    private static final String[] PROGRAM_KINDS = {"Java", "Python", "Code"};
    private final JButton[] programButtons = new JButton[PROGRAM_KINDS.length];
    private String listedName;
    private String listedSource;
    private String listedKind;

    private JPanel buildProgramMenus() {
        JPanel row = new JPanel(new FlowLayout(0, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        for (int i = 0; i < PROGRAM_KINDS.length; i++) {
            final String kind = PROGRAM_KINDS[i];
            final JButton[] self = new JButton[1];
            self[0] = this.action(kind + " \u25be", ELEV, FG, () -> this.showProgramMenu(kind, self[0]));
            this.programButtons[i] = self[0];
            row.add(self[0]);
        }
        return row;
    }

    private void showProgramMenu(String kind, JButton anchor) {
        String[] names = ProgramFiles.list(kind);
        JPopupMenu menu = new JPopupMenu();
        if (names.length == 0) {
            JMenuItem none = new JMenuItem("No programs in Programs/" + kind);
            none.setEnabled(false);
            menu.add(none);
        }
        for (String name : names) {
            JMenuItem item = new JMenuItem(name);
            item.addActionListener(e -> {
                if ("Code".equals(kind)) this.openCodeFile(name);
                else this.selectListedProgram(kind, name);
            });
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    /** Java or Python: this file becomes the program Run executes. */
    private void selectListedProgram(String kind, String name) {
        try {
            byte[] data = ProgramFiles.read(kind, name);
            String src = new String(data, StandardCharsets.UTF_8);
            this.pyName = name;
            this.pyBytes = null;
            this.pyInputPath = null;
            this.listedName = name;
            this.listedSource = src;
            this.listedKind = kind;
            this.showPromptModes();
            this.showPyHint(PyJavHints.status(name, src, data));
            this.paintProgramMenus();
            this.setNow("Run \u00b7 " + name);
        } catch (Exception ex) {
            if (this.pyLog != null) this.pyLog.setText("Could not open " + name + ": " + ex.getMessage());
        }
    }

    /** Code: show the file in the editor for editing. What Run executes does not change. */
    private void openCodeFile(String name) {
        try {
            String text = new String(ProgramFiles.read("Code", name), StandardCharsets.UTF_8);
            if (this.pyEditor != null) {
                this.pyEditor.setEditable(true);
                this.pyEditor.setText(text);
                this.pyEditor.setCaretPosition(0);
            }
            if (this.programButtons[2] != null) this.programButtons[2].setText("Code \u00b7 " + name);
            this.setNow("Editing \u00b7 " + name);
        } catch (Exception ex) {
            if (this.pyLog != null) this.pyLog.setText("Could not open " + name + ": " + ex.getMessage());
        }
    }

    /** True while the listed program is still the current program (nothing else was opened since). */
    private boolean listedProgramCurrent() {
        return this.listedName != null && this.listedName.equals(this.pyName) && this.pyBytes == null;
    }

    private void paintProgramMenus() {
        if (this.listedName != null && !this.listedProgramCurrent()) {
            this.listedName = null;
            this.listedSource = null;
            this.listedKind = null;
        }
        for (int i = 0; i < 2; i++) {
            if (this.programButtons[i] == null) continue;
            boolean on = PROGRAM_KINDS[i].equals(this.listedKind);
            this.programButtons[i].setText(on ? PROGRAM_KINDS[i] + " \u00b7 " + this.listedName : PROGRAM_KINDS[i] + " \u25be");
            this.programButtons[i].setBackground(on ? HIT : ELEV);
            this.programButtons[i].setForeground(on ? BG : FG);
        }
    }

    private JPanel buildImportPage() {
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel();
        jPanel2.setOpaque(false);
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        JLabel jLabel = new JLabel("Import");
        jLabel.setFont(new Font("SansSerif", 1, 20));
        jLabel.setForeground(FG);
        JLabel jLabel2 = new JLabel("<html><body style='width:420px;color:#8A8B86'>PRJ \u00b7 full project<br>PKP \u00b7 plugin pack<br>FSET \u00b7 patterns, Fillerns, and fills from one imported file<br>MIDI \u00b7 pattern, Fillern or fill (named in the file)<br>SNG \u00b7 song<br>WAV / MP3 \u00b7 input file for a PyJav program such as MidiDrumGen.java<br>SF2 \u00b7 drum samples onto pads<br>PY / JAVA / JAR / CLASS \u00b7 program for PyJav</body></html>");
        jPanel2.add(jLabel);
        jPanel2.add(Box.createVerticalStrut(8));
        jPanel2.add(jLabel2);
        jPanel2.add(Box.createVerticalStrut(16));
        JButton jButton = this.action("Choose file", FG, BG);
        jButton.addActionListener(actionEvent -> this.openFile());
        jPanel2.add(jButton);
        jPanel2.add(Box.createVerticalStrut(16));
        JLabel filesLab = new JLabel("Imported files");
        filesLab.setFont(new Font("SansSerif", 1, 14));
        filesLab.setForeground(FG);
        filesLab.setAlignmentX(0.0f);
        jPanel2.add(filesLab);
        JLabel filesHint = new JLabel("<html><body style='width:420px;color:#8A8B86'>Tap a name to open its patterns and fills. Delete drops the whole set.</body></html>");
        filesHint.setAlignmentX(0.0f);
        jPanel2.add(filesHint);
        jPanel2.add(Box.createVerticalStrut(8));
        this.importedFileList = new JPanel();
        this.importedFileList.setOpaque(false);
        this.importedFileList.setLayout(new BoxLayout(this.importedFileList, BoxLayout.Y_AXIS));
        this.importedFileList.setAlignmentX(0.0f);
        jPanel2.add(this.importedFileList);
        jPanel2.add(Box.createVerticalStrut(16));
        JLabel plugLab = new JLabel("Plugins");
        plugLab.setFont(new Font("SansSerif", 1, 14));
        plugLab.setForeground(FG);
        plugLab.setAlignmentX(0.0f);
        jPanel2.add(plugLab);
        JLabel plugHint = new JLabel("<html><body style='width:420px;color:#8A8B86'>Packs add styles, fills, scripts, and processors. Later packs can use the same format.</body></html>");
        plugHint.setAlignmentX(0.0f);
        jPanel2.add(plugHint);
        jPanel2.add(Box.createVerticalStrut(8));
        this.pluginList = new JPanel();
        this.pluginList.setOpaque(false);
        this.pluginList.setLayout(new BoxLayout(this.pluginList, 1));
        this.pluginList.setAlignmentX(0.0f);
        jPanel2.add(this.pluginList);
        jPanel2.add(Box.createVerticalStrut(8));
        JButton dub = this.action("Try Dub pack", ELEV, FG, () -> this.tryDub());
        dub.setAlignmentX(0.0f);
        jPanel2.add(dub);
        JScrollPane scroll = new JScrollPane(jPanel2);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        jPanel.add((Component)scroll, "Center");
        this.refreshPluginUi();
        return jPanel;
    }

    /** Help page sections (File > Help-Desktop): caption, then text. */
    private static final String[][] HELP_SECTIONS = {
        {"PyJav", "Python, Java, JavaScript, or TypeScript. On Windows, Node.js runs in Termux for Windows: "
            + "pkg install nodejs. npm installs @sogni-ai/sogni-client. A .prompt file runs as bash, cmd, or AI. "
            + "Java runs with the JDK installed on this computer. Python needs Python 3."},
    };

    private JPanel buildHelpPage() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(4, 4, 16, 4));
        JLabel title = new JLabel("Help");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(FG);
        title.setAlignmentX(0.0f);
        col.add(title);
        for (String[] section : HELP_SECTIONS) {
            col.add(Box.createVerticalStrut(16));
            JLabel caption = new JLabel(section[0]);
            caption.setFont(new Font("SansSerif", Font.BOLD, 15));
            caption.setForeground(FG);
            caption.setAlignmentX(0.0f);
            col.add(caption);
            col.add(Box.createVerticalStrut(6));
            JLabel text = new JLabel("<html><body style='width:520px'>" + section[1] + "</body></html>");
            text.setForeground(MUTED);
            text.setAlignmentX(0.0f);
            col.add(text);
        }
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JScrollPane scroll = new JScrollPane(col);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private File drumMidiFile() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "drum-midi-settings.txt");
    }

    private void saveDrumMidi() {
        try {
            File f = this.drumMidiFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), MidiImportSettings.encode().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // settings stay for this session
        }
    }

    private JCheckBox drumMidiCheck(JPanel col, String label, String note, boolean on, java.util.function.Consumer<Boolean> set) {
        col.add(Box.createVerticalStrut(12));
        JCheckBox box = new JCheckBox(label, on);
        box.setOpaque(false);
        box.setForeground(FG);
        box.setFont(new Font("SansSerif", Font.PLAIN, 14));
        box.setAlignmentX(0.0f);
        box.addActionListener(e -> {
            set.accept(box.isSelected());
            this.saveDrumMidi();
        });
        col.add(box);
        JLabel sub = new JLabel("<html><body style='width:480px'>" + note + "</body></html>");
        sub.setForeground(MUTED);
        sub.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));
        sub.setAlignmentX(0.0f);
        col.add(sub);
        return box;
    }

    /** File > Drum Midi Settings: how a MIDI drum track becomes a file set on import. */
    private JPanel buildDrumMidiPage() {
        try {
            File f = this.drumMidiFile();
            MidiImportSettings.decode(f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null);
        } catch (Exception ex) {
            MidiImportSettings.reset();
        }
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(4, 4, 16, 4));
        JLabel title = new JLabel("Drum Midi Settings");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(FG);
        title.setAlignmentX(0.0f);
        col.add(title);
        JLabel lead = new JLabel("<html><body style='width:520px'>How a MIDI drum track, such as DrumMidi output, becomes patterns, "
            + "fills and a song when it is imported (changes apply to the next import), and how Fillerns play.</body></html>");
        lead.setForeground(MUTED);
        lead.setAlignmentX(0.0f);
        col.add(Box.createVerticalStrut(6));
        col.add(lead);
        JCheckBox written = this.drumMidiCheck(col, "Keep the notes as written",
            "No style, swing, humanize or generated fills. Off: Pulsekit guesses a style and adds its feel and fills.",
            MidiImportSettings.asWritten, on -> MidiImportSettings.asWritten = on);
        JCheckBox merge = this.drumMidiCheck(col, "Merge hits",
            "Bars that differ by only a few hits become one pattern. The source MIDI still plays as recorded.",
            MidiImportSettings.mergeBars, on -> MidiImportSettings.mergeBars = on);
        JPanel hitsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        hitsRow.setOpaque(false);
        hitsRow.setAlignmentX(0.0f);
        hitsRow.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        JButton minus = this.chip("\u2212", false);
        JButton plus = this.chip("+", false);
        JLabel hits = new JLabel();
        hits.setForeground(FG);
        Runnable paintHits = () -> hits.setText("Merge up to " + MidiImportSettings.mergeHits + (MidiImportSettings.mergeHits == 1 ? " hit" : " hits"));
        paintHits.run();
        minus.addActionListener(e -> {
            MidiImportSettings.mergeHits = MidiImportSettings.clampHits(MidiImportSettings.mergeHits - 1);
            paintHits.run();
            this.saveDrumMidi();
        });
        plus.addActionListener(e -> {
            MidiImportSettings.mergeHits = MidiImportSettings.clampHits(MidiImportSettings.mergeHits + 1);
            paintHits.run();
            this.saveDrumMidi();
        });
        hitsRow.add(minus);
        hitsRow.add(hits);
        hitsRow.add(plus);
        hitsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        col.add(hitsRow);
        JCheckBox fills = this.drumMidiCheck(col, "Treat a one-off bar after a repeated groove as a fill",
            "Off: every bar is a pattern and the import makes no fills or Fillerns.",
            MidiImportSettings.oneOffFills, on -> MidiImportSettings.oneOffFills = on);
        JCheckBox reuse = this.drumMidiCheck(col, "Reuse a pattern when the same bar comes back",
            "Off: each section gets its own pattern, even when the notes repeat.",
            MidiImportSettings.reuseBars, on -> MidiImportSettings.reuseBars = on);
        JCheckBox silent = this.drumMidiCheck(col, "Keep silent bars as rests",
            "Off: silent bars are dropped and the song closes up around them.",
            MidiImportSettings.keepSilent, on -> MidiImportSettings.keepSilent = on);
        col.add(Box.createVerticalStrut(18));
        JLabel fd = new JLabel("Fillern type default");
        fd.setFont(new Font("SansSerif", Font.BOLD, 15));
        fd.setForeground(FG);
        fd.setAlignmentX(0.0f);
        col.add(fd);
        JLabel fdNote = new JLabel("<html><body style='width:480px'>For Fillerns without a type of their own. A Fillern's type is set in its fill list.</body></html>");
        fdNote.setForeground(MUTED);
        fdNote.setAlignmentX(0.0f);
        col.add(fdNote);
        javax.swing.ButtonGroup fdGroup = new javax.swing.ButtonGroup();
        javax.swing.JRadioButton[] fdRadios = new javax.swing.JRadioButton[Engine.FILLERN_MODES.length];
        for (int i = 0; i < Engine.FILLERN_MODES.length; i++) {
            final String mode = Engine.FILLERN_MODES[i];
            javax.swing.JRadioButton r = new javax.swing.JRadioButton(Engine.FILLERN_MODE_LABELS[i], mode.equals(MidiImportSettings.fillernDefault));
            r.setOpaque(false);
            r.setForeground(FG);
            r.setFont(new Font("SansSerif", Font.PLAIN, 14));
            r.setAlignmentX(0.0f);
            r.addActionListener(e -> {
                MidiImportSettings.fillernDefault = mode;
                this.saveDrumMidi();
                if ("combo".equals(this.view)) this.refreshLearnedChips();
            });
            fdGroup.add(r);
            fdRadios[i] = r;
            col.add(r);
        }
        col.add(Box.createVerticalStrut(16));
        JButton reset = this.outline("Reset to defaults", false);
        reset.setAlignmentX(0.0f);
        reset.addActionListener(e -> {
            MidiImportSettings.reset();
            this.saveDrumMidi();
            written.setSelected(MidiImportSettings.asWritten);
            merge.setSelected(MidiImportSettings.mergeBars);
            fills.setSelected(MidiImportSettings.oneOffFills);
            reuse.setSelected(MidiImportSettings.reuseBars);
            silent.setSelected(MidiImportSettings.keepSilent);
            for (int i = 0; i < fdRadios.length; i++) fdRadios[i].setSelected(Engine.FILLERN_MODES[i].equals(MidiImportSettings.fillernDefault));
            paintHits.run();
        });
        col.add(reset);
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JScrollPane scroll = new JScrollPane(col);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildPromptsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(4, 4, 16, 4));
        JLabel title = new JLabel("Prompts");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(FG);
        title.setAlignmentX(0.0f);
        JLabel lead = new JLabel("<html><body style='width:460px'>Name the prompt, then Save exports that name as a .prompt file. Open it on PyJav and Run.</body></html>");
        lead.setForeground(MUTED);
        lead.setAlignmentX(0.0f);
        col.add(title);
        col.add(Box.createVerticalStrut(6));
        col.add(lead);
        col.add(Box.createVerticalStrut(14));
        this.promptTitle = this.promptNameField();
        col.add(this.promptNameRow("PROMPT NAME", this.promptTitle));
        col.add(Box.createVerticalStrut(10));
        this.promptRef1 = new JLabel("No file selected");
        this.promptRef2 = new JLabel("No file selected");
        col.add(this.promptFileRow("Reference file 1", this.promptRef1, 1));
        col.add(Box.createVerticalStrut(10));
        col.add(this.promptFileRow("Reference file 2", this.promptRef2, 2));
        col.add(Box.createVerticalStrut(12));
        JLabel promptLab = new JLabel("PROMPT");
        promptLab.setForeground(SUBTLE);
        promptLab.setFont(new Font("SansSerif", Font.BOLD, 11));
        promptLab.setAlignmentX(0.0f);
        this.promptBody = new JTextArea(12, 40);
        this.promptBody.setLineWrap(true);
        this.promptBody.setWrapStyleWord(true);
        this.promptBody.setFont(new Font("Monospaced", Font.PLAIN, 13));
        this.promptBody.setBackground(SURFACE);
        this.promptBody.setForeground(FG);
        this.promptBody.setCaretColor(FG);
        this.promptBody.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane promptScroll = new JScrollPane(this.promptBody);
        promptScroll.setAlignmentX(0.0f);
        promptScroll.setPreferredSize(new Dimension(480, 220));
        promptScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));
        promptScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        col.add(promptLab);
        col.add(Box.createVerticalStrut(4));
        col.add(promptScroll);
        col.add(Box.createVerticalStrut(12));
        this.promptOut1 = this.promptNameField();
        this.promptOut2 = this.promptNameField();
        col.add(this.promptNameRow("OUTPUT FILE 1", this.promptOut1));
        col.add(Box.createVerticalStrut(8));
        col.add(this.promptNameRow("OUTPUT FILE 2", this.promptOut2));
        col.add(Box.createVerticalStrut(14));
        JButton save = this.action("Save", FG, BG);
        save.setAlignmentX(0.0f);
        save.addActionListener(e -> this.savePrompts());
        col.add(save);
        this.loadPrompts();
        JScrollPane scroll = new JScrollPane(col);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JPanel promptFileRow(String label, JLabel name, int which) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(0.0f);
        JLabel lab = new JLabel(label.toUpperCase());
        lab.setForeground(SUBTLE);
        lab.setFont(new Font("SansSerif", Font.BOLD, 11));
        lab.setAlignmentX(0.0f);
        name.setForeground(FG);
        name.setFont(new Font("Monospaced", Font.PLAIN, 13));
        name.setAlignmentX(0.0f);
        JButton pick = this.action("Choose file", ELEV, FG);
        pick.setAlignmentX(0.0f);
        pick.addActionListener(e -> this.pickPromptFile(which));
        box.add(lab);
        box.add(Box.createVerticalStrut(4));
        box.add(name);
        box.add(Box.createVerticalStrut(6));
        box.add(pick);
        return box;
    }

    private JTextField promptNameField() {
        JTextField field = new JTextField();
        field.setBackground(SURFACE);
        field.setForeground(FG);
        field.setCaretColor(FG);
        field.setFont(new Font("Monospaced", Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setAlignmentX(0.0f);
        return field;
    }

    private JPanel promptNameRow(String label, JTextField field) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(0.0f);
        JLabel lab = new JLabel(label);
        lab.setForeground(SUBTLE);
        lab.setFont(new Font("SansSerif", Font.BOLD, 11));
        lab.setAlignmentX(0.0f);
        box.add(lab);
        box.add(Box.createVerticalStrut(4));
        box.add(field);
        return box;
    }

    private void pickPromptFile(int which) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != 0) return;
        File file = chooser.getSelectedFile();
        if (file == null) return;
        if (file.length() > 16L * 1024L * 1024L) {
            this.setNow((which == 1 ? "Reference file 1" : "Reference file 2") + " is too large (max 16 MB)");
            return;
        }
        if (which == 1) {
            this.promptRef1File = file;
            this.promptRef1.setText(file.getName());
        } else {
            this.promptRef2File = file;
            this.promptRef2.setText(file.getName());
        }
    }

    private File promptsFile() {
        File dir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, "prompts.txt");
    }

    private void loadPrompts() {
        File file = this.promptsFile();
        if (!file.isFile() || this.promptBody == null) return;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            PromptRun.Sheet sheet = PromptRun.parse(text);
            if (sheet == null) return;
            if (this.promptTitle != null) this.promptTitle.setText(sheet.name);
            this.promptOut1.setText(sheet.out1);
            this.promptOut2.setText(sheet.out2);
            if (sheet.ref1.length() > 0) this.promptRef1.setText(sheet.ref1);
            if (sheet.ref2.length() > 0) this.promptRef2.setText(sheet.ref2);
            this.promptBody.setText(sheet.body);
        } catch (Exception ignored) {
            /* keep the empty sheet */
        }
    }

    private void savePrompts() {
        try {
            String name = this.promptTitle == null ? "" : this.promptTitle.getText().replace('\n', ' ').replace('\r', ' ').trim();
            String fileName = PromptRun.fileName(name);
            if (fileName.isEmpty()) {
                this.setNow("Name the prompt");
                return;
            }
            String out1 = this.promptOut1.getText().replace('\n', ' ').replace('\r', ' ');
            String out2 = this.promptOut2.getText().replace('\n', ' ').replace('\r', ' ');
            String ref1 = this.promptRef1.getText();
            String ref2 = this.promptRef2.getText();
            if ("No file selected".equals(ref1)) ref1 = "";
            if ("No file selected".equals(ref2)) ref2 = "";
            String body = this.promptBody.getText() == null ? "" : this.promptBody.getText();
            String text = PromptRun.encode(name, out1, out2, ref1, ref2, body);
            Files.write(this.promptsFile().toPath(), text.getBytes(StandardCharsets.UTF_8));
            this.copyPromptRef(this.promptRef1File, "prompt-ref-1");
            this.copyPromptRef(this.promptRef2File, "prompt-ref-2");
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export prompt");
            chooser.setSelectedFile(new File(fileName));
            if (chooser.showSaveDialog(this) != 0) {
                this.setNow("Not exported");
                return;
            }
            File dest = chooser.getSelectedFile();
            if (dest == null) {
                this.setNow("Not exported");
                return;
            }
            if (!dest.getName().toLowerCase().endsWith(".prompt")) {
                File parent = dest.getParentFile();
                dest = new File(parent == null ? new File(".") : parent, dest.getName() + ".prompt");
            }
            Files.write(dest.toPath(), text.getBytes(StandardCharsets.UTF_8));
            this.setNow("Saved · " + dest.getName());
        } catch (Exception ex) {
            this.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not save");
        }
    }

    private void copyPromptRef(File src, String stem) throws Exception {
        if (src == null || !src.isFile()) return;
        String name = src.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : "";
        File dest = new File(this.promptsFile().getParentFile(), stem + ext);
        Files.write(dest.toPath(), Files.readAllBytes(src.toPath()));
    }

    private JPanel buildInfoPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setAlignmentX(0.0f);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        this.infoTitle = new JLabel("File set");
        this.infoTitle.setFont(new Font("SansSerif", Font.BOLD, 20));
        this.infoTitle.setForeground(FG);
        this.infoTitle.setAlignmentX(0.0f);
        head.add(this.infoTitle, BorderLayout.WEST);
        JButton close = this.action("Close", ELEV, FG);
        close.addActionListener(e -> this.closeFileSetInfo());
        JPanel closeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrap.setOpaque(false);
        closeWrap.add(close);
        head.add(closeWrap, BorderLayout.EAST);
        this.infoStatus = new JLabel("Hold a file set and choose Info.");
        this.infoStatus.setForeground(MUTED);
        this.infoStatus.setAlignmentX(0.0f);
        col.add(head);
        col.add(Box.createVerticalStrut(8));
        col.add(this.infoStatus);
        col.add(Box.createVerticalStrut(12));
        this.infoRows = new JPanel();
        this.infoRows.setOpaque(false);
        this.infoRows.setLayout(new BoxLayout(this.infoRows, BoxLayout.Y_AXIS));
        this.infoRows.setAlignmentX(0.0f);
        col.add(this.infoRows);
        JScrollPane scroll = new JScrollPane(col);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private void closeFileSetInfo() {
        String back = this.infoBack;
        if (back == null || back.isEmpty() || "fsetinfo".equals(back)) back = "import";
        this.showView(back);
    }

    private String fileSetStyleLabel(String key) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        java.util.List<Engine.FileSetPart> stored = this.fileSetParts.get(src);
        if (stored == null || stored.isEmpty()) {
            Engine.FileSet set = Engine.collectFset(src, "", this.learned, this.learnedFills, this.fillernPairs);
            if (set != null) stored = set.parts;
        }
        if (stored == null) return "";
        for (Engine.FileSetPart p : stored) {
            if (p != null && p.styleLabel != null && !p.styleLabel.trim().isEmpty()) return p.styleLabel.trim();
        }
        return "";
    }

    private void promptChangeStyle(String key, String label) {
        java.util.List<StyleDb.Row> rows = StyleDb.rows();
        if (rows.isEmpty()) {
            this.setNow("The style database is empty");
            return;
        }
        String[] names = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            StyleDb.Row row = rows.get(i);
            names[i] = row.name + "  ·  " + row.bpm;
        }
        final int current = StyleDb.indexOf(this.fileSetStyleLabel(key));
        final int initial = current >= 0 ? current : 0;
        JList<String> list = new JList<String>(names);
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(initial);
        list.setFixedCellHeight(24);
        list.setVisibleRowCount(16);
        final Font listFont = UIManager.getFont("List.font");
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (c instanceof JLabel) {
                    JLabel lab = (JLabel) c;
                    Font base = listFont != null ? listFont : lab.getFont();
                    if (index == current) {
                        Map<TextAttribute, Object> attrs = new HashMap<TextAttribute, Object>();
                        attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                        lab.setFont(base.deriveFont(attrs));
                    } else {
                        lab.setFont(base);
                    }
                }
                return c;
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(380, 320));
        scroll.getViewport().setViewPosition(new java.awt.Point(0, Math.max(0, (initial - 4) * 24)));
        list.ensureIndexIsVisible(initial);
        Object[] options = new Object[] { "Change style", "Cancel" };
        int ans = JOptionPane.showOptionDialog(
            this, scroll, "Style database",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (ans != 0) return;
        int pick = list.getSelectedIndex();
        if (pick < 0) pick = initial;
        this.applyFileSetStyle(key, label, rows.get(pick));
    }

    private void applyFileSetStyle(String key, String label, StyleDb.Row row) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        if (!Engine.fileSetStyleOn(Engine.fileSetOriginOf(src))) {
            this.setNow("Using the source file");
            return;
        }
        String shown = label == null || label.isEmpty() ? (src.isEmpty() ? "Import" : src) : label;
        Engine.FileSet set = Engine.collectFset(src, shown, this.learned, this.learnedFills, this.fillernPairs);
        if (set == null) {
            this.setNow("That file set is empty");
            return;
        }
        java.util.List<Engine.FileSetPart> stored = this.fileSetParts.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        set.origin = Engine.fileSetOriginOf(src);
        Engine.FileSetAudio au = Engine.fileSetAudioOf(src);
        if (au != null) set.sourceWav = au.sourceWav;
        String back = this.view;
        Engine.FileSet next = AudioIo.restyleFileSet(set, row.kit, row.name, row.hats, row.four, row.dkick, row.bpm, row.back);
        String first = Engine.replaceFileSetLearned(src, next, this.learned, this.learnedFills, this.fillernPairs);
        if (next.parts != null && !next.parts.isEmpty()) this.rememberFileSetParts(src, next.parts);
        if (Engine.isFileSetOrigin(set.origin)) Engine.rememberFileSetOrigin(src, set.origin);
        this.persistLearned();
        this.refreshLearnedChips();
        if (first != null) this.loadLearned(first);
        Engine.ImportedSong made = Engine.fileSetSongMade(shown, this.importedSongs);
        if (made != null) this.importedSongs.remove(made);
        java.util.List<Engine.Part> song = Engine.songFromFileSet(next);
        if (song != null && !song.isEmpty()) this.addImportedSong(shown, song);
        if (back != null) this.showView(back);
        this.setNow("Style · " + row.name);
    }

    private void makeFileSetSong(String key, String label) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        Engine.FileSet set = Engine.collectFset(src, label, this.learned, this.learnedFills, this.fillernPairs);
        if (set == null) {
            this.setNow("That file set is empty");
            return;
        }
        java.util.List<Engine.FileSetPart> stored = this.fileSetParts.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        java.util.List<Engine.Part> song = Engine.songFromFileSet(set);
        if (song == null || song.isEmpty()) {
            this.setNow("Could not make a song from that file set");
            return;
        }
        String name = label == null || label.isEmpty() ? (src.isEmpty() ? "Import" : src) : label;
        this.addImportedSong(name, song);
    }

    private void storeAudioDir() {
        Engine.storeFileSetAudioDir(new File(this.pulsekitDir(), "fset-audio"));
    }

    private void openFileSetInfo(String key, String label) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        java.util.List<Engine.FileSetPart> parts = this.fileSetParts.get(src);
        Engine.FileSet set = Engine.collectFset(src, label, this.learned, this.learnedFills, this.fillernPairs);
        if (parts == null || parts.isEmpty()) {
            if (set != null && set.parts != null && !set.parts.isEmpty()) parts = set.parts;
            else parts = Engine.partsForDisplay(set);
        }
        Engine.unifyFileSetParts(parts, set);
        if (parts != null && !parts.isEmpty()) this.rememberFileSetParts(src, parts);
        if (this.infoTitle != null) {
            String shown = label == null || label.isEmpty() ? "File set" : label;
            this.infoTitle.setText(Engine.fileSetMarked(shown, Engine.fileSetOriginOf(src)));
        }
        if (this.infoRows != null) this.infoRows.removeAll();
        int nFill = set == null ? 0 : set.fills.size();
        int nPair = set == null ? 0 : set.fillerns.size();
        if (this.infoStatus != null) {
            String style = "";
            if (parts != null && !parts.isEmpty() && parts.get(0).styleLabel != null && !parts.get(0).styleLabel.isEmpty()) {
                style = parts.get(0).styleLabel + " \u00b7 ";
            }
            String bpm = parts == null || parts.isEmpty() ? "" : parts.get(0).bpm + " BPM \u00b7 ";
            String word = Engine.fileSetOriginTitle(Engine.fileSetOriginOf(src));
            String lead = word.isEmpty() ? "" : word + " \u00b7 ";
            String len = Engine.fileSetLengthLine(parts, set == null ? 0 : set.durationSec);
            this.infoStatus.setText(lead + style + bpm + (parts == null ? 0 : parts.size()) + " parts \u00b7 " + len + " \u00b7 " + nFill + " fills \u00b7 " + nPair + " Fillerns");
        }
        if (parts != null) {
            for (Engine.FileSetPart p : parts) {
                JPanel card = new JPanel();
                card.setOpaque(false);
                card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
                card.setAlignmentX(0.0f);
                card.setBorder(BorderFactory.createEmptyBorder(6, 0, 10, 0));
                JLabel nameLab = new JLabel(p.name + "  \u00b7  " + p.kind);
                nameLab.setForeground(FG);
                nameLab.setAlignmentX(0.0f);
                JLabel meta = new JLabel("BPM " + p.bpm + "  \u00b7  TS " + p.tsNum + "/" + p.tsDen + "  \u00b7  " + (p.styleLabel == null ? "" : p.styleLabel));
                meta.setForeground(MUTED);
                meta.setAlignmentX(0.0f);
                JLabel more = new JLabel(p.bars + " bars  \u00b7  " + this.fmtTime(p.startSec) + "\u2013" + this.fmtTime(p.endSec) + "  \u00b7  " + p.hits + " hits  \u00b7  swing " + p.swing + "  \u00b7  " + Math.round(Math.min(1f, p.density) * 100) + "%");
                more.setForeground(SUBTLE);
                more.setAlignmentX(0.0f);
                card.add(nameLab);
                card.add(meta);
                card.add(more);
                if (this.infoRows != null) this.infoRows.add(card);
            }
        }
        if (set != null && !set.fills.isEmpty()) {
            JLabel fillsLab = new JLabel("Fills");
            fillsLab.setForeground(FG);
            fillsLab.setAlignmentX(0.0f);
            fillsLab.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
            if (this.infoRows != null) this.infoRows.add(fillsLab);
            for (Engine.LearnedFill f : set.fills) {
                JLabel row = new JLabel(f.name + (f.kind != null ? "  \u00b7  " + f.kind : ""));
                row.setForeground(MUTED);
                row.setAlignmentX(0.0f);
                if (this.infoRows != null) this.infoRows.add(row);
            }
        }
        if (set != null && !set.fillerns.isEmpty()) {
            JLabel pairLab = new JLabel("Fillerns");
            pairLab.setForeground(FG);
            pairLab.setAlignmentX(0.0f);
            pairLab.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
            if (this.infoRows != null) this.infoRows.add(pairLab);
            for (String[] row : set.fillerns) {
                JLabel lab = new JLabel(row[0] + " + " + row[1]);
                lab.setForeground(MUTED);
                lab.setAlignmentX(0.0f);
                if (this.infoRows != null) this.infoRows.add(lab);
            }
        }
        if (this.infoRows != null) {
            this.infoRows.revalidate();
            this.infoRows.repaint();
        }
        if (!"fsetinfo".equals(this.view)) this.infoBack = this.view;
        this.showView("fsetinfo");
    }

    private void rememberFileSetParts(String source, java.util.List<Engine.FileSetPart> parts) {
        if (source == null) return;
        if (parts == null || parts.isEmpty()) this.fileSetParts.remove(source);
        else this.fileSetParts.put(source, new java.util.ArrayList<Engine.FileSetPart>(parts));
        this.persistFileSetInfo();
    }

    private void persistFileSetInfo() {
        try {
            Files.write(new File(this.pulsekitDir(), "fset-info.json").toPath(),
                Engine.encodeFsetInfo(this.fileSetParts).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (Exception ignored) { /* optional */ }
    }

    private void restoreFileSetInfo() {
        try {
            File f = new File(this.pulsekitDir(), "fset-info.json");
            if (!f.isFile() || f.length() < 8) return;
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            this.fileSetParts.clear();
            this.fileSetParts.putAll(Engine.decodeFsetInfo(json));
            Engine.fileSetOrigins.clear();
            Engine.fileSetOrigins.putAll(Engine.decodeFsetOrigins(json));
            Engine.loadFileSetSongs(json);
            Engine.loadFileSetAudioDir(new File(this.pulsekitDir(), "fset-audio"));
        } catch (Exception ignored) { /* optional */ }
    }

    private String fmtTime(float sec) {
        int s = Math.max(0, Math.round(sec));
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private JPanel buildExportPage() {
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel();
        jPanel2.setOpaque(false);
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        JLabel jLabel = new JLabel("Export");
        jLabel.setFont(new Font("SansSerif", 1, 20));
        jLabel.setForeground(FG);
        jPanel2.add(jLabel);
        jPanel2.add(this.exportSection("Project", new JComponent[] {
            this.action("PRJ", HIT, BG, () -> this.savePrj()),
            this.action("PKP", ELEV, FG, () -> this.savePkp()),
            this.action("FSET", ELEV, FG, () -> this.saveFset(null))
        }));
        jPanel2.add(this.exportSection("MIDI", new JComponent[] {
            this.action("Pattern", ELEV, FG, () -> this.saveMidi("pattern")),
            this.action("Fillern", ELEV, FG, () -> this.saveMidi("fillern")),
            this.action("Fill", ELEV, FG, () -> this.saveMidi("fill"))
        }));
        jPanel2.add(this.exportSection("This beat", new JComponent[] {
            this.action("WAV", ELEV, FG, () -> this.saveWav()),
            this.action("MP3", ELEV, FG, () -> this.saveMp3()),
            this.action("SF2", ELEV, FG, () -> this.saveSf2()),
            this.action("JAR", ELEV, FG, () -> this.saveJar())
        }));
        jPanel2.add(this.exportSection("Song", new JComponent[] {
            this.action("SNG", ELEV, FG, () -> this.saveSng()),
            this.action("Song MIDI", ELEV, FG, () -> this.saveSongMidi())
        }));
        jPanel2.add(this.exportSection("PyJav", new JComponent[] {
            this.action("Save", ELEV, FG, () -> this.savePy())
        }));
        jPanel.add((Component)jPanel2, "North");
        return jPanel;
    }

    private JPanel exportSection(String title, JComponent[] buttons) {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, 1));
        wrap.setAlignmentX(0.0f);
        JLabel lab = new JLabel(title);
        lab.setForeground(MUTED);
        lab.setAlignmentX(0.0f);
        wrap.add(Box.createVerticalStrut(10));
        wrap.add(lab);
        wrap.add(Box.createVerticalStrut(6));
        JPanel row = new JPanel(new GridLayout(0, 3, 8, 8));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        row.setMaximumSize(new Dimension(520, 80 * ((buttons.length + 2) / 3)));
        for (JComponent b : buttons) row.add(b);
        wrap.add(row);
        return wrap;
    }

    private JButton action(String string, Color color, Color color2, Runnable runnable) {
        JButton jButton = this.action(string, color, color2);
        jButton.addActionListener(actionEvent -> runnable.run());
        return jButton;
    }

    private int[][] fillCellsFor(String id) {
        if (id != null && id.startsWith("l:")) {
            String lid = id.substring(2);
            for (Engine.LearnedFill f : this.learnedFills) {
                if (lid.equals(f.id)) return Engine.copyCells(f.cells);
            }
        }
        if (id != null && id.startsWith("v:")) {
            String vid = id.substring(2);
            for (Engine.LearnedFill f : this.variatedFills) {
                if (vid.equals(f.id)) return Engine.copyCells(f.cells);
            }
        }
        if (id != null && id.startsWith("p:")) {
            int slash = id.indexOf('/');
            if (slash > 2) {
                String pid = id.substring(2, slash);
                String fid = id.substring(slash + 1);
                for (Engine.Plugin p : this.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.PlugFill f : p.fills) {
                        if (fid.equals(f.id)) return Engine.copyCells(f.cells);
                    }
                }
            }
        }
        return Engine.buildFill(id, this.cells, this.style);
    }

    private String fillLabel(String id) {
        if (id != null && id.startsWith("l:")) {
            String lid = id.substring(2);
            for (Engine.LearnedFill f : this.learnedFills) {
                if (lid.equals(f.id)) return f.name;
            }
        }
        if (id != null && id.startsWith("v:")) {
            String vid = id.substring(2);
            for (Engine.LearnedFill f : this.variatedFills) {
                if (vid.equals(f.id)) return f.name;
            }
        }
        if (id != null && id.startsWith("p:")) {
            int slash = id.indexOf('/');
            if (slash > 2) {
                String pid = id.substring(2, slash);
                String fid = id.substring(slash + 1);
                for (Engine.Plugin p : this.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.PlugFill f : p.fills) {
                        if (fid.equals(f.id)) return f.name;
                    }
                }
            }
        }
        return Engine.fillLabel(id);
    }

    private void applyEnabledProcessors(Random random) {
        for (Engine.Plugin p : this.plugins) {
            if (!p.enabled) continue;
            Engine.applyOps(this.cells, p.ops, random, this.steps);
        }
    }

    private void installPlugin(Engine.Plugin incoming, boolean apply) {
        if (incoming == null) return;
        this.plugins.removeIf(p -> incoming.id.equals(p.id));
        this.plugins.add(0, incoming);
        while (this.plugins.size() > Engine.MAX_PLUGINS) this.plugins.remove(this.plugins.size() - 1);
        this.refreshPluginChips();
        this.refreshPluginUi();
        if (incoming.script != null && this.pyEditor != null) {
            this.pyBytes = null;
            this.pyEditor.setEditable(true);
            this.pyEditor.setText(incoming.script);
            if (incoming.scriptName != null) this.pyName = incoming.scriptName;
        }
        if (!apply || !incoming.enabled) return;
        if (!incoming.styles.isEmpty()) {
            Engine.Style st = incoming.styles.get(0);
            this.styles.put(st.id, st);
            this.loadStyle(st.id, false);
        }
        if (!incoming.fills.isEmpty()) {
            Engine.PlugFill f = incoming.fills.get(0);
            this.fillId = "p:" + incoming.id + "/" + f.id;
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                System.arraycopy(f.cells[t], 0, this.fillPat[t], 0, Math.min(16, f.cells[t].length));
            }
            this.refreshFills();
        }
        this.showView("pattern");
        this.setNow("Plugin \u00b7 " + incoming.name);
    }

    private void setPluginEnabled(String id, boolean on) {
        for (Engine.Plugin p : this.plugins) {
            if (!p.id.equals(id)) continue;
            p.enabled = on;
            break;
        }
        this.refreshPluginChips();
        this.refreshPluginUi();
        this.refreshStyles();
        this.refreshFills();
    }

    private void uninstallPlugin(String id) {
        this.plugins.removeIf(p -> id.equals(p.id));
        this.refreshPluginChips();
        this.refreshPluginUi();
        this.refreshStyles();
        this.refreshFills();
        this.setNow("Plugin removed");
    }

    private void refreshPluginUi() {
        if (this.pluginList == null) return;
        this.pluginList.removeAll();
        if (this.plugins.isEmpty()) {
            JLabel empty = new JLabel("No packs installed yet.");
            empty.setForeground(SUBTLE);
            empty.setAlignmentX(0.0f);
            this.pluginList.add(empty);
        } else {
            for (Engine.Plugin p : this.plugins) {
                final String pid = p.id;
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setAlignmentX(0.0f);
                row.setMaximumSize(new Dimension(520, 52));
                JPanel text = new JPanel();
                text.setOpaque(false);
                text.setLayout(new BoxLayout(text, 1));
                JLabel name = new JLabel(p.name);
                name.setForeground(FG);
                name.setFont(new Font("SansSerif", 1, 13));
                JLabel sum = new JLabel(p.summary() + (p.enabled ? "" : " \u00b7 off"));
                sum.setForeground(MUTED);
                sum.setFont(new Font("SansSerif", 0, 11));
                text.add(name);
                text.add(sum);
                row.add(text, "Center");
                JPanel btns = new JPanel(new FlowLayout(2, 6, 0));
                btns.setOpaque(false);
                JButton tog = this.action(p.enabled ? "On" : "Off", p.enabled ? HIT : ELEV, p.enabled ? BG : FG, () -> this.setPluginEnabled(pid, !this.pluginEnabled(pid)));
                JButton rm = this.action("Remove", ELEV, FG, () -> this.uninstallPlugin(pid));
                btns.add(tog);
                btns.add(rm);
                row.add(btns, "East");
                this.pluginList.add(row);
                this.pluginList.add(Box.createVerticalStrut(6));
            }
        }
        this.pluginList.revalidate();
        this.pluginList.repaint();
    }

    private boolean pluginEnabled(String id) {
        for (Engine.Plugin p : this.plugins) if (p.id.equals(id)) return p.enabled;
        return false;
    }

    private void refreshPluginChips() {
        this.refreshLearnedChips();
    }

    private void onChipMenu(JButton btn, Supplier<JPopupMenu> menus) {
        btn.addMouseListener(new MouseAdapter() {
            Timer hold;
            void showAt(MouseEvent e) {
                JPopupMenu menu = menus.get();
                if (menu == null || menu.getComponentCount() == 0) return;
                menu.show(btn, e != null ? e.getX() : 8, e != null ? e.getY() : btn.getHeight());
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == 3) {
                    showAt(e);
                    return;
                }
                hold = new Timer(450, ev -> {
                    JPopupMenu menu = menus.get();
                    if (menu != null && menu.getComponentCount() > 0) menu.show(btn, 8, btn.getHeight());
                });
                hold.setRepeats(false);
                hold.start();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (hold != null) hold.stop();
                if (e.isPopupTrigger()) showAt(e);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (hold != null) hold.stop();
            }
        });
    }

    private JPopupMenu variatePatternMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem a = new JMenuItem("Random groove");
        a.addActionListener(e -> this.variatePattern("random"));
        JMenuItem b = new JMenuItem("With this fill");
        b.addActionListener(e -> this.variatePattern("fill"));
        JMenuItem c = new JMenuItem("With random fill");
        c.addActionListener(e -> this.variatePattern("random-fill"));
        m.add(a);
        m.add(b);
        m.add(c);
        return m;
    }

    private void variatePattern(String mode) {
        int[][] next = Engine.copyCells(this.cells);
        Random rng = new Random();
        String tag = "var";
        if ("fill".equals(mode)) {
            Engine.stampFillLastBar(next, this.fillPat, this.steps, Engine.barSteps(this.tsNum, this.tsDen));
            tag = this.fillLabel(this.fillId).toLowerCase();
            this.fillLast = true;
        } else if ("random-fill".equals(mode)) {
            String fid = Engine.randomFillId(rng);
            Engine.stampFillLastBar(next, Engine.buildFill(fid, this.cells, this.style), this.steps, Engine.barSteps(this.tsNum, this.tsDen));
            tag = Engine.fillLabel(fid).toLowerCase();
            this.fillLast = true;
        } else {
            Engine.nudgePattern(next, rng, this.densBar.getVal(), this.steps);
        }
        Engine.Style st = this.styles.get(this.style);
        String base = st != null ? st.label : "Groove";
        Engine.Learned item = new Engine.Learned();
        item.id = Engine.newLearnedId();
        item.name = Engine.uniqueLearnedName(base + " " + tag, this.variatedPatterns);
        item.bpm = this.bpm();
        item.tsNum = this.tsNum;
        item.tsDen = this.tsDen;
        item.steps = this.steps;
        item.closest = this.style;
        item.cells = next;
        item.swing = this.swingBar.getVal();
        item.density = this.densBar.getVal();
        item.human = this.humanBar.getVal();
        this.variatedPatterns.add(0, item);
        while (this.variatedPatterns.size() > Engine.MAX_VARIATED) this.variatedPatterns.remove(this.variatedPatterns.size() - 1);
        this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        this.persistLearned();
        this.refreshLearnedChips();
        this.loadStyle(item.id, false);
        if (this.fillLast && "pattern".equals(this.view)) this.showView("combo");
        this.setNow("Var \u00b7 " + item.name);
        if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) this.rebuildAndPlay(true);
    }

    private String patternKeyFor(String id) {
        for (Engine.Learned x : this.variatedPatterns) if (x.id.equals(id)) return "v:" + id;
        for (Engine.Learned x : this.learned) if (x.id.equals(id)) return "l:" + id;
        return "s:" + id;
    }

    private String currentPatternKey() {
        return this.patternKeyFor(this.style);
    }

    /** Underlined only when a fill was chosen for this pattern from the list, and that fill still exists. */
    private boolean fillernUnderlined(String patternKey) {
        return this.selectedFillFor(patternKey) != null;
    }

    private String fillernFillKeyOf(String patternKey) {
        String stored = this.fillernPairs.get(patternKey);
        if (stored != null && !stored.isEmpty()) return stored;
        if (patternKey.equals(this.currentPatternKey())) return this.fillId;
        return "toms";
    }

    private String songPickSection(String key) {
        if (key != null && key.startsWith("v:")) return "Variated";
        if (key != null && (key.startsWith("l:") || key.startsWith("p:"))) return "Imported";
        return "Built-in";
    }

    private void addMenuHeading(JPopupMenu m, String title) {
        JMenuItem h = new JMenuItem(title);
        h.setEnabled(false);
        m.add(h);
    }

    private String songFileLabel(String key, boolean fill) {
        if (key == null) return "Other";
        if (key.startsWith("p:")) {
            String rest = key.substring(2);
            int slash = rest.indexOf('/');
            String pid = slash > 0 ? rest.substring(0, slash) : rest;
            for (Engine.Plugin p : this.plugins) if (p.id.equals(pid)) return p.name;
            return "Pack";
        }
        if (key.startsWith("l:")) {
            String id = key.substring(2);
            if (fill) {
                for (Engine.LearnedFill x : this.learnedFills) {
                    if (id.equals(x.id)) {
                        String s = Engine.sourceOf(x);
                        return s.isEmpty() ? "Other" : s;
                    }
                }
            } else {
                for (Engine.Learned x : this.learned) {
                    if (id.equals(x.id)) {
                        String s = Engine.sourceOf(x);
                        return s.isEmpty() ? "Other" : s;
                    }
                }
            }
        }
        return "Other";
    }

    private void addImportedSongRows(JPopupMenu m, java.util.List<String[]> imported, String kind, Integer replaceAt) {
        java.util.LinkedHashMap<String, java.util.ArrayList<String[]>> packs = new java.util.LinkedHashMap<>();
        for (String[] row : imported) {
            String pack = this.songFileLabel(row[1], "fill".equals(kind));
            java.util.ArrayList<String[]> list = packs.get(pack);
            if (list == null) {
                list = new java.util.ArrayList<>();
                packs.put(pack, list);
            }
            list.add(row);
        }
        for (java.util.Map.Entry<String, java.util.ArrayList<String[]>> e : packs.entrySet()) {
            this.addMenuHeading(m, e.getKey());
            for (String[] row : e.getValue()) {
                final String key = row[1];
                JMenuItem it = new JMenuItem("  " + row[2]);
                it.addActionListener(ev -> this.applySongPick(kind, key, replaceAt));
                m.add(it);
            }
        }
    }

    /** The fill chosen from the list for this pattern, or null. Fills an import paired do not count. */
    private String selectedFillFor(String patternKey) {
        if (patternKey == null || !this.fillernPicked.contains(patternKey)) return null;
        String stored = this.fillernPairs.get(patternKey);
        if (stored == null || stored.isEmpty()) return null;
        return Engine.fillKeyExists(stored, this.variatedFills, this.learnedFills) ? stored : null;
    }

    /** A fill chosen from the list: remembered and underlined. */
    private void pickFillern(String patternKey, String fillKey) {
        if (patternKey == null || fillKey == null) return;
        this.fillernPicked.add(patternKey);
        this.rememberFillern(patternKey, fillKey);
        if ("combo".equals(this.view)) this.refreshLearnedChips();
    }

    /** Make a Fillern: a menu of the file set's patterns, each opening the fills to pair with it. */
    private void createFillern(JComponent anchor, List<Engine.Learned> patterns) {
        if (patterns.isEmpty()) {
            this.setNow("This file set has no patterns");
            return;
        }
        JPopupMenu m = new JPopupMenu();
        this.addMenuHeading(m, "Fillern: choose a pattern");
        for (Engine.Learned item : patterns) {
            final Engine.Learned it = item;
            javax.swing.JMenu sub = new javax.swing.JMenu(it.name);
            this.addFillernItems(sub.getPopupMenu(), () -> this.loadLearned(it.id), this.patternKeyFor(it.id));
            m.add(sub);
        }
        m.show(anchor, 0, anchor.getHeight());
    }

    private void rememberFillern(String patternKey, String fillKey) {
        if (patternKey == null || fillKey == null) return;
        this.fillernPairs.remove(patternKey);
        this.fillernPairs.put(patternKey, fillKey);
        this.persistLearned();
        this.refreshStyles();
    }

    private void applyStoredFillern(String patternKey) {
        if (!"combo".equals(this.view) || patternKey == null) return;
        String fk = this.fillernPairs.get(patternKey);
        if (fk != null) this.applyFill(fk);
    }

    private String fillernMenuLabel(String text, boolean on) {
        if (!on) return text;
        String safe = text.replace("&", "&").replace("<", "<");
        return "<html><u>" + safe + "</u></html>";
    }

    private void addFillernItems(JPopupMenu m, Runnable loadPattern, String patternKey) {
        if (!"combo".equals(this.view)) return;
        String selected = this.selectedFillFor(patternKey);
        this.addMenuHeading(m, "Fillern type");
        String mode = this.fillernModeOf(patternKey);
        for (int i = 0; i < Engine.FILLERN_MODES.length; i++) {
            final String fm = Engine.FILLERN_MODES[i];
            JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + Engine.FILLERN_MODE_LABELS[i], fm.equals(mode)));
            it.addActionListener(e -> this.setFillernMode(patternKey, fm));
            m.add(it);
        }
        this.addMenuHeading(m, "Last-bar fill");
        this.addMenuHeading(m, "Built-in");
        for (int i = 0; i < Engine.FILL_ID.length; i++) {
            final String fid = Engine.FILL_ID[i];
            if (this.hiddenFills.contains(fid)) continue;
            JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + Engine.FILL_LABEL[i], fid.equals(selected)));
            it.addActionListener(e -> {
                loadPattern.run();
                this.applyFill(fid);
                this.pickFillern(patternKey, fid);
                if (!"combo".equals(this.view)) this.showView("combo");
            });
            m.add(it);
        }
        if (!this.variatedFills.isEmpty()) {
            this.addMenuHeading(m, "Variated");
            for (Engine.LearnedFill f : this.variatedFills) {
                final Engine.LearnedFill fill = f;
                final String fid = "v:" + fill.id;
                JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + fill.name, fid.equals(selected)));
                it.addActionListener(e -> {
                    loadPattern.run();
                    this.applyFill(fid);
                    this.pickFillern(patternKey, fid);
                    if (!"combo".equals(this.view)) this.showView("combo");
                });
                m.add(it);
            }
        }
        for (Engine.Plugin p : this.plugins) {
            if (!p.enabled || p.fills.isEmpty()) continue;
            this.addMenuHeading(m, p.name);
            for (Engine.PlugFill f : p.fills) {
                final String fid = "p:" + p.id + "/" + f.id;
                final String name = f.name;
                JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + name, fid.equals(selected)));
                it.addActionListener(e -> {
                    loadPattern.run();
                    this.applyFill(fid);
                    this.pickFillern(patternKey, fid);
                    if (!"combo".equals(this.view)) this.showView("combo");
                });
                m.add(it);
            }
        }
        java.util.LinkedHashMap<String, java.util.ArrayList<Engine.LearnedFill>> packs = new java.util.LinkedHashMap<>();
        for (Engine.LearnedFill f : this.learnedFills) {
            String src = Engine.sourceOf(f);
            if (src.isEmpty()) src = "Other";
            java.util.ArrayList<Engine.LearnedFill> list = packs.get(src);
            if (list == null) {
                list = new java.util.ArrayList<>();
                packs.put(src, list);
            }
            list.add(f);
        }
        for (java.util.Map.Entry<String, java.util.ArrayList<Engine.LearnedFill>> e : packs.entrySet()) {
            this.addMenuHeading(m, e.getKey());
            for (Engine.LearnedFill f : e.getValue()) {
                final Engine.LearnedFill fill = f;
                final String fid = "l:" + fill.id;
                JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + fill.name, fid.equals(selected)));
                it.addActionListener(ev -> {
                    loadPattern.run();
                    this.applyFill(fid);
                    this.pickFillern(patternKey, fid);
                    if (!"combo".equals(this.view)) this.showView("combo");
                });
                m.add(it);
            }
        }
    }

    private void addFillernAfterEdit(JPopupMenu m, Runnable loadPattern, String patternKey) {
        if (!"combo".equals(this.view)) return;
        m.addSeparator();
        this.addFillernItems(m, loadPattern, patternKey);
    }

    private JPopupMenu builtinStyleMenu(final String sid) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem dup = new JMenuItem("Duplicate");
        dup.addActionListener(e -> this.replicateStyle(sid));
        JMenuItem hide = new JMenuItem("Hide");
        hide.addActionListener(e -> this.hideStyle(sid));
        m.add(dup);
        m.add(hide);
        this.addFillernAfterEdit(m, () -> this.loadStyle(sid, false), this.patternKeyFor(sid));
        return m;
    }

    private JPopupMenu learnedStyleMenu(final Engine.Learned item) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem dup = new JMenuItem("Duplicate");
        dup.addActionListener(e -> this.replicateStyle(item.id));
        JMenuItem ren = new JMenuItem("Rename");
        ren.addActionListener(e -> this.renameLearned(item.id));
        JMenuItem del = new JMenuItem("Delete");
        del.addActionListener(e -> this.removeLearned(item.id));
        m.add(dup);
        m.add(ren);
        m.add(del);
        this.addFillernAfterEdit(m, () -> this.loadLearned(item.id), this.patternKeyFor(item.id));
        return m;
    }

    private JPopupMenu learnedFillMenu(final Engine.LearnedFill item) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem ren = new JMenuItem("Rename");
        ren.addActionListener(e -> this.renameLearnedFill(item.id));
        JMenuItem del = new JMenuItem("Delete");
        del.addActionListener(e -> this.removeLearnedFill(item.id));
        m.add(ren);
        m.add(del);
        return m;
    }

    private void renameLearned(String id) {
        Engine.Learned item = null;
        for (Engine.Learned x : this.learned) if (x.id.equals(id)) { item = x; break; }
        if (item == null) {
            for (Engine.Learned x : this.variatedPatterns) if (x.id.equals(id)) { item = x; break; }
        }
        if (item == null) return;
        String n = JOptionPane.showInputDialog(this, "Name", item.name);
        if (n == null) return;
        n = n.trim();
        if (n.isEmpty()) return;
        if (n.length() > 28) n = n.substring(0, 28);
        item.name = n;
        this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        this.persistLearned();
        this.refreshLearnedChips();
        this.setNow("Renamed \u00b7 " + n);
    }

    private void renameLearnedFill(String id) {
        for (Engine.LearnedFill item : this.learnedFills) {
            if (!item.id.equals(id)) continue;
            String n = JOptionPane.showInputDialog(this, "Name", item.name);
            if (n == null) return;
            n = n.trim();
            if (n.isEmpty()) return;
            if (n.length() > 28) n = n.substring(0, 28);
            item.name = n;
            this.persistLearned();
            this.refreshLearnedChips();
            this.setNow("Renamed \u00b7 " + n);
            return;
        }
    }

    private void onRightClick(JButton btn, Runnable action) {
        btn.addMouseListener(new MouseAdapter() {
            Timer hold;
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || e.getButton() == 3) {
                    action.run();
                    return;
                }
                hold = new Timer(450, ev -> action.run());
                hold.setRepeats(false);
                hold.start();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (hold != null) hold.stop();
                if (e.isPopupTrigger()) action.run();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (hold != null) hold.stop();
            }
        });
    }

    private void hideStyle(String id) {
        if (!this.hiddenStyles.contains(id)) this.hiddenStyles.add(id);
        this.refreshLearnedChips();
        this.setNow("Hidden style");
    }

    private void hideFill(String id) {
        if (!this.hiddenFills.contains(id)) this.hiddenFills.add(id);
        this.refreshLearnedChips();
        this.setNow("Hidden fill");
    }

    private void showAllStyles() {
        this.hiddenStyles.clear();
        this.refreshLearnedChips();
        this.setNow("Styles restored");
    }

    private void showAllFills() {
        this.hiddenFills.clear();
        this.refreshLearnedChips();
        this.setNow("Fills restored");
    }

    private void refreshLearnedChips() {
        this.importedBar.removeAll();
        this.variatedPatternBar.removeAll();
        for (Component c : this.styleBar.getComponents()) {
            if (c instanceof JButton && "hidden-styles".equals(((JButton) c).getClientProperty("role"))) this.styleBar.remove(c);
        }
        this.importedFillBar.removeAll();
        this.variatedFillBar.removeAll();
        for (Component c : this.fillBar.getComponents()) {
            if (c instanceof JButton && ((JButton) c).getClientProperty("learned") != null) this.fillBar.remove(c);
            if (c instanceof JButton && "hidden-fills".equals(((JButton) c).getClientProperty("role"))) this.fillBar.remove(c);
        }
        for (Component c : this.styleBar.getComponents()) {
            if (!(c instanceof JButton)) continue;
            JButton b = (JButton) c;
            Object sid = b.getClientProperty("style");
            if (sid instanceof String && b.getClientProperty("plugin") == null && b.getClientProperty("learned") == null) {
                b.setVisible(!this.hiddenStyles.contains(sid));
            }
        }
        for (Component c : this.fillBar.getComponents()) {
            if (!(c instanceof JButton)) continue;
            JButton b = (JButton) c;
            Object fid = b.getClientProperty("fill");
            if (fid instanceof String && b.getClientProperty("plugin") == null && b.getClientProperty("learned") == null
                && !((String) fid).startsWith("p:")) {
                b.setVisible(!this.hiddenFills.contains(fid));
            }
        }
        for (Engine.Learned item : this.variatedPatterns) {
            final Engine.Learned it = item;
            JButton b = this.chip(it.name, false);
            b.putClientProperty("style", it.id);
            b.putClientProperty("variated", it.id);
            b.addActionListener(e -> this.loadStyle(it.id, false));
            this.onChipMenu(b, () -> {
                JPopupMenu m = new JPopupMenu();
                JMenuItem dup = new JMenuItem("Duplicate");
                dup.addActionListener(ev -> this.replicateStyle(it.id));
                JMenuItem ren = new JMenuItem("Rename");
                ren.addActionListener(ev -> this.renameLearned(it.id));
                JMenuItem del = new JMenuItem("Delete");
                del.addActionListener(ev -> {
                    this.variatedPatterns.removeIf(x -> it.id.equals(x.id));
                    this.persistLearned();
                    this.refreshLearnedChips();
                    this.setNow("Variation deleted");
                });
                m.add(dup);
                m.add(ren);
                m.add(del);
                this.addFillernAfterEdit(m, () -> this.loadStyle(it.id, false), this.patternKeyFor(it.id));
                return m;
            });
            this.variatedPatternBar.add(b);
        }
        for (Engine.Plugin p : this.plugins) {
            if (!p.enabled || p.styles.isEmpty()) continue;
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            for (Engine.Style st : p.styles) {
                final Engine.Style st0 = st;
                this.styles.put(st0.id, st0);
                JButton b = this.chip(st0.label, false);
                b.putClientProperty("style", st0.id);
                b.putClientProperty("plugin", p.id);
                b.addActionListener(e -> this.loadStyle(st0.id, false));
                kids.add(b);
                if (st0.id.equals(this.style)) selected = true;
            }
            final String pid = p.id;
            this.importedBar.add(this.importPack("p:" + pid, p.name, p.styles.size(), selected, "Remove pack",
                () -> { this.uninstallPlugin(pid); this.setNow("Removed \u00b7 " + p.name); }, kids));
        }
        // The Pattern tab lists a file set's patterns, the Fillern tab its Fillerns.
        boolean fillerns = "combo".equals(this.view);
        this.importedFor = fillerns ? "combo" : "pattern";
        for (String src : this.learnedSources()) {
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            int n = 0;
            List<Engine.Learned> setPatterns = new ArrayList<Engine.Learned>();
            for (Engine.Learned item : this.learned) if (src.equals(Engine.sourceOf(item))) setPatterns.add(item);
            for (Engine.Learned item : this.learned) {
                if (!src.equals(Engine.sourceOf(item))) continue;
                if (fillerns && this.selectedFillFor(this.patternKeyFor(item.id)) == null) continue;
                final Engine.Learned it = item;
                String chipName = fillerns
                    ? it.name + " \u00b7 " + this.fillLabel(this.selectedFillFor(this.patternKeyFor(it.id))) + Engine.fillernModeNote(this.fillernModeOf(this.patternKeyFor(it.id)))
                    : it.name;
                JButton b = this.chip(chipName, false);
                b.putClientProperty("style", it.id);
                b.putClientProperty("learned", it.id);
                b.addActionListener(e -> this.loadLearned(it.id));
                this.onChipMenu(b, () -> this.learnedStyleMenu(it));
                kids.add(b);
                n++;
                if (it.id.equals(this.style) || it.id.equals(this.learnedId())) selected = true;
            }
            if (fillerns) {
                if (n == 0) {
                    JButton none = new JButton("No fillerns yet, create one");
                    this.flatten(none);
                    none.setBackground(BG);
                    none.setForeground(MUTED);
                    none.putClientProperty("role", "fillern-none");
                    none.addActionListener(e -> this.createFillern(none, setPatterns));
                    kids.add(none);
                }
                JButton add = this.chip("+ Fillern", false);
                add.putClientProperty("role", "fillern-add");
                add.addActionListener(e -> this.createFillern(add, setPatterns));
                kids.add(add);
            }
            String label = src.isEmpty() ? "Other" : src;
            this.importedBar.add(this.importPack(src.isEmpty() ? "o:other" : "f:" + src, label, n, selected, "Delete file set",
                () -> this.removeImportSource(src), () -> this.saveFset(src), kids));
        }
        if (!this.hiddenStyles.isEmpty()) {
            JButton b = this.chip("Hidden " + this.hiddenStyles.size(), false);
            b.putClientProperty("role", "hidden-styles");
            b.addActionListener(e -> this.showAllStyles());
            this.styleBar.add(b);
        }
        for (Engine.LearnedFill item : this.variatedFills) {
            final Engine.LearnedFill it = item;
            JButton b = this.chip(it.name, false);
            b.putClientProperty("fill", "v:" + it.id);
            b.putClientProperty("variated", it.id);
            b.addActionListener(e -> this.applyFill("v:" + it.id));
            this.onRightClick(b, () -> {
                this.variatedFills.removeIf(x -> it.id.equals(x.id));
                this.persistLearned();
                this.refreshLearnedChips();
                this.setNow("Variation deleted");
            });
            this.variatedFillBar.add(b);
        }
        for (Engine.Plugin p : this.plugins) {
            if (!p.enabled || p.fills.isEmpty()) continue;
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            for (Engine.PlugFill f : p.fills) {
                final Engine.PlugFill f0 = f;
                final String fid = "p:" + p.id + "/" + f0.id;
                JButton b = this.chip(f0.name, false);
                b.putClientProperty("fill", fid);
                b.putClientProperty("plugin", p.id);
                b.addActionListener(e -> this.applyFill(fid));
                kids.add(b);
                if (fid.equals(this.fillId)) selected = true;
            }
            final String pid = p.id;
            this.importedFillBar.add(this.importPack("p:" + pid, p.name, p.fills.size(), selected, "Remove pack",
                () -> { this.uninstallPlugin(pid); this.setNow("Removed \u00b7 " + p.name); }, kids));
        }
        // Every file set, also one without fills yet.
        for (String src : Engine.fileSetSources(this.learned, this.learnedFills)) {
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            int n = 0;
            for (Engine.LearnedFill item : this.learnedFills) {
                if (!src.equals(Engine.sourceOf(item))) continue;
                final Engine.LearnedFill it = item;
                JButton b = this.chip(it.name, false);
                b.putClientProperty("fill", "l:" + it.id);
                b.putClientProperty("learned", it.id);
                b.addActionListener(e -> this.loadLearnedFill(it.id));
                this.onChipMenu(b, () -> this.learnedFillMenu(it));
                kids.add(b);
                n++;
                if (("l:" + it.id).equals(this.fillId)) selected = true;
            }
            if (n == 0) {
                JButton none = new JButton("No fills yet, add one");
                this.flatten(none);
                none.setBackground(BG);
                none.setForeground(MUTED);
                none.putClientProperty("role", "fills-none");
                none.addActionListener(e -> this.pickBuiltinFillFor(none, src));
                kids.add(none);
            }
            JButton addFill = this.chip("+ Fill", false);
            addFill.putClientProperty("role", "fills-add");
            addFill.addActionListener(e -> this.pickBuiltinFillFor(addFill, src));
            kids.add(addFill);
            String label = src.isEmpty() ? "Other" : src;
            this.importedFillBar.add(this.importPack(src.isEmpty() ? "o:other" : "f:" + src, label, n, selected, "Delete file set",
                () -> this.removeImportSource(src), () -> this.saveFset(src), kids));
        }
        if (!this.hiddenFills.isEmpty()) {
            JButton b = this.chip("Hidden " + this.hiddenFills.size(), false);
            b.putClientProperty("role", "hidden-fills");
            b.addActionListener(e -> this.showAllFills());
            this.fillBar.add(b);
        }
        this.styleBar.revalidate();
        this.styleBar.repaint();
        this.importedBar.revalidate();
        this.importedBar.repaint();
        this.variatedPatternBar.revalidate();
        this.variatedPatternBar.repaint();
        this.fillBar.revalidate();
        this.fillBar.repaint();
        this.variatedFillBar.revalidate();
        this.variatedFillBar.repaint();
        this.importedFillBar.revalidate();
        this.importedFillBar.repaint();
        this.refreshImportedFiles();
        this.refreshStyles();
        this.refreshFills();
    }

    private String learnedId() {
        return this.style;
    }

    private java.util.LinkedHashSet<String> learnedSources() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        for (Engine.Learned x : this.learned) out.add(Engine.sourceOf(x));
        return out;
    }

    private java.util.LinkedHashSet<String> fillSources() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        for (Engine.LearnedFill x : this.learnedFills) out.add(Engine.sourceOf(x));
        return out;
    }

    private boolean packOpen(String key, boolean fallback) {
        Boolean v = this.openPacks.get(key);
        return v != null ? v : fallback;
    }

    private JPanel importPack(String key, String label, int count, boolean selected, String deleteLabel, Runnable onDelete, JPanel kids) {
        return this.importPack(key, label, count, selected, deleteLabel, onDelete, null, kids);
    }

    private JPanel importPack(String key, String label, int count, boolean selected, String deleteLabel, Runnable onDelete, Runnable onExport, JPanel kids) {
        boolean open = this.packOpen(key, selected);
        String shown = label;
        if (key != null && key.startsWith("f:")) shown = Engine.fileSetMarked(label, Engine.fileSetOriginOf(key.substring(2)));
        JPanel pack = new JPanel();
        pack.setOpaque(false);
        pack.setLayout(new BoxLayout(pack, BoxLayout.Y_AXIS));
        pack.setAlignmentX(0f);
        JButton head = this.chip((open ? "\u25BE " : "\u25B8 ") + shown + " \u00b7 " + count, selected);
        head.putClientProperty("pack", key);
        head.addActionListener(e -> {
            this.openPacks.put(key, !this.packOpen(key, selected));
            this.refreshLearnedChips();
        });
        this.onChipMenu(head, () -> {
            JPopupMenu m = new JPopupMenu();
            if (onExport != null) {
                JMenuItem info = new JMenuItem("Info");
                info.addActionListener(ev -> this.openFileSetInfo(key, label));
                m.add(info);
                JMenuItem make = new JMenuItem("Make song");
                make.addActionListener(ev -> this.makeFileSetSong(key, label));
                m.add(make);
                String origin = key != null && key.startsWith("f:") ? Engine.fileSetOriginOf(key.substring(2)) : "";
                if (Engine.fileSetStyleOn(origin)) {
                    JMenuItem style = new JMenuItem("Change style");
                    style.addActionListener(ev -> this.promptChangeStyle(key, label));
                    m.add(style);
                }
                JMenuItem exp = new JMenuItem("Export .fset");
                exp.addActionListener(ev -> onExport.run());
                m.add(exp);
            }
            JMenuItem del = new JMenuItem(deleteLabel);
            del.addActionListener(ev -> onDelete.run());
            m.add(del);
            return m;
        });
        pack.add(head);
        kids.setVisible(open);
        kids.setAlignmentX(0f);
        pack.add(kids);
        return pack;
    }

    private void removeImportSource(String source) {
        String want = source == null ? "" : source;
        this.learned.removeIf(x -> want.equals(Engine.sourceOf(x)));
        this.learnedFills.removeIf(x -> want.equals(Engine.sourceOf(x)));
        this.fileSetParts.remove(want);
        Engine.fileSetSongs.remove(want);
        Engine.forgetFileSetOrigin(want);
        Engine.forgetFileSetAudio(want);
        this.storeAudioDir();
        this.persistFileSetInfo();
        this.fillernPairs.entrySet().removeIf(e -> {
            String k = e.getKey();
            String v = e.getValue();
            boolean dropK = k != null && k.startsWith("l:") && this.learned.stream().noneMatch(x -> k.substring(2).equals(x.id));
            boolean dropV = v != null && v.startsWith("l:") && this.learnedFills.stream().noneMatch(x -> v.substring(2).equals(x.id));
            return dropK || dropV;
        });
        this.refreshLearnedChips();
        this.persistLearned();
        this.setNow("Removed \u00b7 " + (want.isEmpty() ? "Other" : want));
    }

    private void refreshImportedFiles() {
        if (this.importedFileList == null) return;
        this.importedFileList.removeAll();
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<String>();
        keys.addAll(this.learnedSources());
        keys.addAll(this.fillSources());
        if (keys.isEmpty()) {
            JLabel empty = new JLabel("No imported files yet.");
            empty.setForeground(MUTED);
            empty.setAlignmentX(0f);
            this.importedFileList.add(empty);
        } else {
            for (String src : keys) {
                String label = src.isEmpty() ? "Other" : src;
                String shown = Engine.fileSetMarked(label, Engine.fileSetOriginOf(src));
                String key = src.isEmpty() ? "o:other" : "f:" + src;
                int nPat = 0, nFill = 0;
                for (Engine.Learned x : this.learned) if (src.equals(Engine.sourceOf(x))) nPat++;
                for (Engine.LearnedFill x : this.learnedFills) if (src.equals(Engine.sourceOf(x))) nFill++;
                boolean open = this.packOpen(key, false);
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setAlignmentX(0f);
                JButton head = this.chip((open ? "\u25BE " : "\u25B8 ") + shown + " \u00b7 " + (nPat + nFill), false);
                head.addActionListener(e -> {
                    this.openPacks.put(key, !this.packOpen(key, false));
                    this.refreshImportedFiles();
                });
                JButton info = this.action("Info", ELEV, FG, () -> this.openFileSetInfo(key, label));
                JButton make = this.action("Make song", ELEV, FG, () -> this.makeFileSetSong(key, label));
                JButton style = Engine.fileSetStyleOn(Engine.fileSetOriginOf(src))
                    ? this.action("Change style", ELEV, FG, () -> this.promptChangeStyle(key, label))
                    : null;
                JButton exp = this.action("Export", ELEV, FG, () -> this.saveFset(src));
                JButton del = this.action("Delete", ELEV, FG, () -> this.removeImportSource(src));
                JPanel east = new JPanel();
                east.setOpaque(false);
                east.add(info);
                east.add(make);
                if (style != null) east.add(style);
                east.add(exp);
                east.add(del);
                row.add(head, BorderLayout.CENTER);
                row.add(east, BorderLayout.EAST);
                this.importedFileList.add(row);
                if (open) {
                    JPanel kids = new JPanel();
                    kids.setOpaque(false);
                    kids.setLayout(new BoxLayout(kids, BoxLayout.Y_AXIS));
                    kids.setAlignmentX(0f);
                    for (Engine.Learned x : this.learned) {
                        if (!src.equals(Engine.sourceOf(x))) continue;
                        final Engine.Learned it = x;
                        JButton b = this.chip("Pattern \u00b7 " + it.name, false);
                        b.addActionListener(e -> { this.loadLearned(it.id); this.showView("pattern"); });
                        kids.add(b);
                    }
                    for (Engine.LearnedFill x : this.learnedFills) {
                        if (!src.equals(Engine.sourceOf(x))) continue;
                        final Engine.LearnedFill it = x;
                        JButton b = this.chip("Fill \u00b7 " + it.name, false);
                        b.addActionListener(e -> { this.loadLearnedFill(it.id); this.showView("fills"); });
                        kids.add(b);
                    }
                    for (Engine.Learned x : this.learned) {
                        if (!src.equals(Engine.sourceOf(x))) continue;
                        final String pair = this.fillernPairs.get("l:" + x.id);
                        if (pair == null || pair.isEmpty()) continue;
                        final Engine.Learned it = x;
                        JButton b = this.chip("Fillern \u00b7 " + it.name, false);
                        b.addActionListener(e -> {
                            this.loadLearned(it.id);
                            this.showView("combo");
                        });
                        kids.add(b);
                    }
                    this.importedFileList.add(kids);
                }
                this.importedFileList.add(Box.createVerticalStrut(6));
            }
        }
        this.importedFileList.revalidate();
        this.importedFileList.repaint();
    }

    private void loadLearned(String id) {
        for (Engine.Learned item : this.learned) {
            if (!item.id.equals(id)) continue;
            this.loadStyle(item.id, false);
            this.tsNum = Engine.clampTsNum(item.tsNum);
            this.tsDen = Engine.clampTsDen(item.tsDen);
            this.tsNumField.setText(Integer.toString(this.tsNum));
            this.tsDenField.setText(Integer.toString(this.tsDen));
            this.applySteps(Engine.clampSteps(item.steps), false);
            this.setNow("Learned \u00b7 " + item.name + (item.closest != null ? " \u00b7 like " + item.closest : ""));
            return;
        }
    }

    private void loadLearnedFill(String id) {
        for (Engine.LearnedFill item : this.learnedFills) {
            if (!item.id.equals(id)) continue;
            this.applyFill("l:" + item.id);
            return;
        }
    }

    private void removeLearned(String id) {
        this.learned.removeIf(x -> id.equals(x.id));
        this.refreshLearnedChips();
        this.persistLearned();
        this.setNow("Style deleted");
    }

    private void removeLearnedFill(String id) {
        this.learnedFills.removeIf(x -> id.equals(x.id));
        this.refreshLearnedChips();
        this.persistLearned();
        this.setNow("Fill deleted");
    }

    private void learnFromImport(String filename, int[][] cells, int bpm) {
        String role = Engine.midiRoleFromFile(filename);
        String stem = Engine.stemNameFromMidi(filename);
        String msg = null;
        boolean patternOnly = "pattern".equals(role);
        boolean fillOnly = "fill".equals(role);
        boolean fillern = "fillern".equals(role) || (!patternOnly && !fillOnly && Engine.importedFillern(cells));
        boolean hasFill = fillOnly || fillern || (!patternOnly && (Engine.shouldImportFill(cells) || Engine.hitCount(Engine.lastBar(cells)) >= 2));
        if (fillOnly) {
            int[][] bar = Engine.lastBar(cells);
            String kind = Engine.classifyFill(bar);
            String sig = Engine.patternSignature(bar);
            Engine.LearnedFill existing = null;
            for (Engine.LearnedFill x : this.learnedFills) {
                if (Engine.patternSignature(x.cells).equals(sig)) { existing = x; break; }
            }
            Engine.LearnedFill fillItem = existing;
            if (fillItem == null) {
                fillItem = new Engine.LearnedFill();
                fillItem.id = Engine.newLearnedId();
                fillItem.name = Engine.fillNameFromFile(filename, kind);
                fillItem.kind = kind;
                fillItem.cells = bar;
                fillItem.source = Engine.importSource(filename);
                this.learnedFills.add(0, fillItem);
                while (this.learnedFills.size() > Engine.MAX_LEARNED) this.learnedFills.remove(this.learnedFills.size() - 1);
            }
            this.applyFill("l:" + fillItem.id);
            this.showView("fills");
            this.refreshLearnedChips();
            this.persistLearned();
            this.setNow(Engine.fillLabel(fillItem.kind) + " \u00b7 " + fillItem.name);
            return;
        }
        int[][] groove = fillern ? Engine.firstBar(cells) : cells;
        Engine.Learned styleItem = null;
        if (Engine.hitCount(groove) >= 1) {
            String sig = Engine.patternSignature(groove);
            Engine.Learned existing = null;
            for (Engine.Learned x : this.learned) {
                if (Engine.patternSignature(x.cells).equals(sig)) { existing = x; break; }
            }
            if (existing != null) {
                this.loadLearned(existing.id);
                styleItem = existing;
                msg = "Style \u00b7 " + existing.name;
            } else {
                Engine.Learned item = new Engine.Learned();
                item.id = Engine.newLearnedId();
                item.name = stem;
                item.bpm = bpm;
                item.closest = Engine.matchStyle(groove);
                item.cells = Engine.copyCells(groove);
                item.swing = this.swingBar.getVal();
                item.density = this.densBar.getVal();
                item.human = this.humanBar.getVal();
                item.source = Engine.importSource(filename);
                item.tsNum = this.tsNum;
                item.tsDen = this.tsDen;
                item.steps = this.steps;
                this.learned.add(0, item);
                while (this.learned.size() > Engine.MAX_LEARNED) this.learned.remove(this.learned.size() - 1);
                this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
                this.loadLearned(item.id);
                styleItem = item;
                msg = "Style \u00b7 " + item.name + (item.closest != null ? " \u00b7 like " + item.closest : "");
            }
        }
        Engine.LearnedFill fillItem = null;
        if (hasFill) {
            int[][] bar = Engine.lastBar(cells);
            String kind = Engine.classifyFill(bar);
            String sig = Engine.patternSignature(bar);
            Engine.LearnedFill existing = null;
            for (Engine.LearnedFill x : this.learnedFills) {
                if (Engine.patternSignature(x.cells).equals(sig)) { existing = x; break; }
            }
            if (existing == null) {
                Engine.LearnedFill item = new Engine.LearnedFill();
                item.id = Engine.newLearnedId();
                item.name = Engine.fillNameFromFile(filename, kind);
                item.kind = kind;
                item.cells = bar;
                item.source = Engine.importSource(filename);
                this.learnedFills.add(0, item);
                while (this.learnedFills.size() > Engine.MAX_LEARNED) this.learnedFills.remove(this.learnedFills.size() - 1);
                fillItem = item;
                msg = (msg == null ? "" : msg + " \u00b7 ") + Engine.fillLabel(kind) + " \u00b7 " + item.name;
            } else {
                fillItem = existing;
                msg = (msg == null ? "" : msg + " \u00b7 ") + existing.name;
            }
        }
        if (fillern && styleItem != null && fillItem != null) {
            this.applySteps(Engine.barSteps(this.tsNum, this.tsDen), false);
            this.applyFill("l:" + fillItem.id);
            this.rememberFillern(this.patternKeyFor(styleItem.id), "l:" + fillItem.id);
            this.showView("combo");
            msg = "Fillern \u00b7 " + styleItem.name + " \u00b7 " + Engine.fillLabel(fillItem.kind);
        } else if (patternOnly) {
            this.showView("pattern");
        }
        this.refreshLearnedChips();
        this.persistLearned();
        if (msg != null) this.setNow(msg);
    }

    private void applyFill(String string) {
        this.fillId = string;
        int[][] nArray = this.fillCellsFor(string);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, this.fillPat[i], 0, 16);
        }
        Engine.zeroCells(this.fillLens);
        this.refreshFills();
        this.fillVariated = string != null && string.startsWith("v:");
        this.setNow(this.fillLabel(string));
        if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
            this.fillLast = true;
            this.rebuildAndPlay(true);
        }
    }

    private boolean customFill() {
        return this.fillId != null && (this.fillId.startsWith("l:") || this.fillId.startsWith("v:") || this.fillId.startsWith("p:"));
    }

    private void syncBuiltinFill() {
        if (this.customFill()) return;
        int[][] src = Engine.buildFill(this.fillId, this.cells, this.style);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(src[i], 0, this.fillPat[i], 0, 16);
        }
    }

    /** Right click on a built-in fill: copy it, or a variation of it, into a file set; or hide it. */
    private void builtinFillMenu(JComponent anchor, String fillId) {
        JPopupMenu m = new JPopupMenu();
        javax.swing.JMenu copy = new javax.swing.JMenu("Copy fill to file set");
        javax.swing.JMenu variated = new javax.swing.JMenu("Variated fill into file set");
        List<String> sources = Engine.fileSetSources(this.learned, this.learnedFills);
        for (String src : sources) {
            String label = src.isEmpty() ? "Other" : src;
            JMenuItem c = new JMenuItem(label);
            c.addActionListener(e -> this.copyFillToFileSet(fillId, src, false));
            copy.add(c);
            JMenuItem v = new JMenuItem(label);
            v.addActionListener(e -> this.copyFillToFileSet(fillId, src, true));
            variated.add(v);
        }
        copy.setEnabled(!sources.isEmpty());
        variated.setEnabled(!sources.isEmpty());
        m.add(copy);
        m.add(variated);
        m.addSeparator();
        JMenuItem hide = new JMenuItem("Hide");
        hide.addActionListener(e -> this.hideFill(fillId));
        m.add(hide);
        m.show(anchor, 0, anchor.getHeight());
    }

    /** "No fills yet, add one" and "+ Fill": choose a built-in fill to copy into this file set. */
    private void pickBuiltinFillFor(JComponent anchor, String source) {
        JPopupMenu m = new JPopupMenu();
        this.addMenuHeading(m, "Add a fill to " + (source.isEmpty() ? "Other" : source));
        for (int i = 0; i < Engine.FILL_ID.length; i++) {
            final String fid = Engine.FILL_ID[i];
            if (this.hiddenFills.contains(fid)) continue;
            JMenuItem it = new JMenuItem(Engine.FILL_LABEL[i]);
            it.addActionListener(e -> this.copyFillToFileSet(fid, source, false));
            m.add(it);
        }
        m.show(anchor, 0, anchor.getHeight());
    }

    /** Adds a built-in fill, as it sounds with the current pattern, to a file set; with variate, a variation of it. */
    private void copyFillToFileSet(String fillId, String source, boolean variate) {
        int[][] cells = Engine.copyCells(this.fillCellsFor(fillId));
        if (variate) cells = Engine.variateFillCells(cells, new Random());
        Engine.LearnedFill fill = new Engine.LearnedFill();
        fill.id = Engine.newLearnedId();
        fill.kind = Engine.isFillId(fillId) ? fillId : "toms";
        fill.name = Engine.uniqueFillName(this.fillLabel(fillId) + (variate ? " var" : ""), Engine.fillsFrom(this.learnedFills, source));
        fill.cells = cells;
        fill.source = source;
        this.learnedFills.add(0, fill);
        while (this.learnedFills.size() > Engine.MAX_LEARNED) this.learnedFills.remove(this.learnedFills.size() - 1);
        this.persistLearned();
        this.refreshLearnedChips();
        this.applyFill("l:" + fill.id);
        this.setNow(fill.name + " \u00b7 " + (source.isEmpty() ? "Other" : source));
    }

    private void variateFill() {
        Random random = new Random();
        for (int i = Engine.track("ltom"); i < Engine.TRACK_ID.length; ++i) {
            for (int j = 8; j < 16; ++j) {
                if (!(random.nextDouble() < 0.22)) continue;
                this.fillPat[i][j] = this.fillPat[i][j] > 0 ? 0 : (random.nextBoolean() ? 100 : 127);
            }
        }
        Engine.LearnedFill item = new Engine.LearnedFill();
        item.id = Engine.newLearnedId();
        item.kind = this.fillId != null && this.fillId.length() < 12 ? this.fillId : "toms";
        if (item.kind.startsWith("l:") || item.kind.startsWith("v:") || item.kind.startsWith("p:")) item.kind = "toms";
        item.name = Engine.uniqueFillName(Engine.fillLabel(item.kind) + " var", this.variatedFills);
        item.cells = Engine.copyCells(this.fillPat);
        this.variatedFills.add(0, item);
        while (this.variatedFills.size() > Engine.MAX_VARIATED) this.variatedFills.remove(this.variatedFills.size() - 1);
        this.fillId = "v:" + item.id;
        this.fillVariated = true;
        this.persistLearned();
        this.refreshLearnedChips();
        this.refreshFills();
        this.setNow(item.name);
        if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
            this.fillLast = true;
            this.rebuildAndPlay(true);
        }
    }

    private List<Engine.Part> activeSong() {
        if ("imported".equals(this.songLane)) {
            Engine.ImportedSong item = this.importedSong();
            if (item != null) return item.parts;
            return new ArrayList<Engine.Part>();
        }
        return this.song;
    }

    private Engine.ImportedSong importedSong() {
        if (this.importedSongId != null) {
            for (Engine.ImportedSong s : this.importedSongs) {
                if (this.importedSongId.equals(s.id)) return s;
            }
        }
        return this.importedSongs.isEmpty() ? null : this.importedSongs.get(0);
    }

    /** A song saved in a file set, listed under Imported songs and built from the set's patterns. */
    private Engine.ImportedSong addFileSetSong(String source, Engine.FileSetSong song) {
        Engine.ImportedSong item = new Engine.ImportedSong();
        item.id = Engine.newLearnedId();
        item.name = Engine.uniqueImportedName(song.name, this.importedSongs);
        item.fileSet = source;
        item.fileSetSong = song.name;
        item.parts.addAll(Engine.resolveSong(song, Engine.learnedFrom(this.learned, source), Engine.fillsFrom(this.learnedFills, source)));
        this.importedSongs.add(0, item);
        while (this.importedSongs.size() > Engine.MAX_IMPORTED_SONGS) this.importedSongs.remove(this.importedSongs.size() - 1);
        if (this.importedSongId == null) this.importedSongId = item.id;
        this.persistLearned();
        return item;
    }

    /** Opening a song saved in a file set builds it again from the set's patterns, so their edits show. */
    private void refreshFromFileSet(Engine.ImportedSong item) {
        if (item == null || item.fileSet == null || item.fileSetSong == null) return;
        List<Engine.FileSetSong> songs = Engine.fileSetSongs.get(item.fileSet);
        if (songs == null) return;
        for (Engine.FileSetSong song : songs) {
            if (!song.name.equals(item.fileSetSong)) continue;
            item.parts.clear();
            item.parts.addAll(Engine.resolveSong(song, Engine.learnedFrom(this.learned, item.fileSet), Engine.fillsFrom(this.learnedFills, item.fileSet)));
            return;
        }
    }

    /** Save to set: the song goes into a file set, as references to the set's patterns and fills. */
    private void saveSongToFileSet(JComponent anchor) {
        List<Engine.Part> song = this.activeSong();
        if (song.isEmpty()) {
            this.setNow("The song is empty");
            return;
        }
        List<String> sources = Engine.fileSetSources(this.learned, this.learnedFills);
        if (sources.isEmpty()) {
            this.setNow("Import a MIDI to make a file set first");
            return;
        }
        Engine.ImportedSong imported = "imported".equals(this.songLane) ? this.importedSong() : null;
        String best = imported != null && imported.fileSet != null && sources.contains(imported.fileSet) ? imported.fileSet : this.bestFileSetFor(song, sources);
        List<String> order = new ArrayList<String>();
        if (best != null) order.add(best);
        for (String s : sources) if (!order.contains(s)) order.add(s);
        String songName = imported != null ? (imported.fileSetSong != null ? imported.fileSetSong : imported.name) : "Song";
        JPopupMenu m = new JPopupMenu();
        this.addMenuHeading(m, "Save \"" + songName + "\" to file set");
        for (String src : order) {
            JMenuItem it = new JMenuItem(src.isEmpty() ? "Other" : src);
            it.addActionListener(e -> this.saveSongInto(src, songName, imported));
            m.add(it);
        }
        m.show(anchor, 0, anchor.getHeight());
    }

    /**
     * The file set a song comes from, for naming its exports: the set it is saved in, the set an
     * imported song was made from, or the set whose names it uses most. Null when none.
     */
    private String songFileSet(List<Engine.Part> song) {
        List<String> sources = Engine.fileSetSources(this.learned, this.learnedFills);
        if ("imported".equals(this.songLane)) {
            Engine.ImportedSong imported = this.importedSong();
            if (imported != null && imported.fileSet != null && sources.contains(imported.fileSet)) return imported.fileSet;
            if (imported != null && sources.contains(imported.name)) return imported.name;
        }
        String best = this.bestFileSetFor(song, sources);
        return best == null || best.isEmpty() ? null : best;
    }

    /** The file set whose pattern and fill names the song uses most. */
    private String bestFileSetFor(List<Engine.Part> song, List<String> sources) {
        String best = null;
        int bestHits = 0;
        for (String src : sources) {
            int hits = 0;
            List<Engine.Learned> pats = Engine.learnedFrom(this.learned, src);
            List<Engine.LearnedFill> fills = Engine.fillsFrom(this.learnedFills, src);
            for (Engine.Part p : song) {
                for (Engine.Learned x : pats) if (x.name.equals(p.name)) { hits++; break; }
                for (Engine.LearnedFill x : fills) if (x.name.equals(p.name)) { hits++; break; }
            }
            if (hits > bestHits) { bestHits = hits; best = src; }
        }
        return best;
    }

    private void saveSongInto(String source, String songName, Engine.ImportedSong imported) {
        List<Engine.Part> song = this.activeSong();
        Engine.FileSetSong saved = Engine.songForFileSet(songName, song, Engine.learnedFrom(this.learned, source), Engine.fillsFrom(this.learnedFills, source));
        List<Integer> outside = Engine.partsFromOutside(saved);
        if (!outside.isEmpty()) {
            String set = source.isEmpty() ? "Other" : source;
            int ans = JOptionPane.showOptionDialog(this,
                outside.size() + (outside.size() == 1 ? " part uses a pattern or fill" : " parts use patterns or fills") + " that are not in " + set
                    + ".\nCopy them into it? Otherwise their notes are kept in the song.",
                "Parts from outside the file set", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[] { "Copy into set", "Keep in song" }, "Copy into set");
            if (ans == 0) {
                this.copyPartsIntoSet(source, song, outside);
                saved = Engine.songForFileSet(songName, song, Engine.learnedFrom(this.learned, source), Engine.fillsFrom(this.learnedFills, source));
            }
        }
        Engine.putFileSetSong(source, saved);
        if (imported != null) {
            imported.fileSet = source;
            imported.fileSetSong = saved.name;
        } else {
            Engine.ImportedSong added = this.addFileSetSong(source, saved);
            this.importedSongId = added.id;
        }
        this.persistFileSetInfo();
        this.persistLearned();
        this.refreshSong();
        int refs = 0;
        for (Engine.SongRef r : saved.parts) if (r.usePattern != null || r.useFill != null) refs++;
        this.setNow("Saved \u00b7 " + saved.name + " in " + (source.isEmpty() ? "Other" : source) + " \u00b7 " + refs + " of " + saved.parts.size() + " parts follow the set");
    }

    /** Copies the patterns and fills of outside parts into the file set, and names the parts after the copies. */
    private void copyPartsIntoSet(String source, List<Engine.Part> song, List<Integer> outside) {
        java.util.HashMap<String, String> copied = new java.util.HashMap<String, String>();
        for (Integer at : outside) {
            Engine.Part p = song.get(at.intValue());
            String key = p.kind + ":" + Engine.patternSignature(p.cells);
            String name = copied.get(key);
            if (name == null) {
                if ("fill".equals(p.kind)) {
                    Engine.LearnedFill fill = new Engine.LearnedFill();
                    fill.id = Engine.newLearnedId();
                    fill.kind = "toms";
                    fill.name = Engine.uniqueFillName(p.name, Engine.fillsFrom(this.learnedFills, source));
                    fill.cells = Engine.copyCells(p.cells);
                    fill.source = source;
                    this.learnedFills.add(0, fill);
                    name = fill.name;
                } else {
                    Engine.Learned pat = new Engine.Learned();
                    pat.id = Engine.newLearnedId();
                    pat.name = Engine.uniqueLearnedName(p.name, Engine.learnedFrom(this.learned, source));
                    pat.bpm = p.bpm;
                    pat.closest = "";
                    pat.cells = Engine.copyCells(p.cells);
                    pat.tsNum = p.tsNum;
                    pat.tsDen = p.tsDen;
                    pat.source = source;
                    this.learned.add(0, pat);
                    this.styles.put(pat.id, new Engine.Style(pat.id, pat.name, pat.bpm, Engine.rowsFromCells(pat.cells)));
                    name = pat.name;
                }
                copied.put(key, name);
            }
            p.name = name;
        }
        this.refreshLearnedChips();
    }

    private void addImportedSong(String name, List<Engine.Part> parts) {
        Engine.ImportedSong item = new Engine.ImportedSong();
        item.id = Engine.newLearnedId();
        item.name = Engine.uniqueImportedName(name, this.importedSongs);
        item.parts.addAll(parts);
        this.importedSongs.add(0, item);
        while (this.importedSongs.size() > Engine.MAX_IMPORTED_SONGS) {
            this.importedSongs.remove(this.importedSongs.size() - 1);
        }
        this.importedSongId = item.id;
        this.songLane = "imported";
        this.persistLearned();
        this.showView("song");
        this.refreshSong();
        this.setNow("Imported song \u00b7 " + item.name);
    }

    private JPopupMenu songPickMenu(Integer replaceAt) {
        return this.songPickMenu(replaceAt, null);
    }

    private JPopupMenu songPickMenu(Integer replaceAt, String only) {
        JPopupMenu m = new JPopupMenu();
        if (!"original".equals(this.songLane)) return m;
        boolean all = only == null || only.isEmpty();
        if (all || "pattern".equals(only)) {
            if (all) {
                this.addMenuHeading(m, "Pattern");
            } else {
                this.addMenuHeading(m, "Built-in");
            }
            for (String id : Engine.styles().keySet()) {
                if (this.hiddenStyles.contains(id)) continue;
                Engine.Style st = this.styles.get(id);
                if (st == null) continue;
                final String key = "s:" + id;
                JMenuItem it = new JMenuItem(st.label);
                it.addActionListener(e -> this.applySongPick("pattern", key, replaceAt));
                m.add(it);
            }
            if (!this.variatedPatterns.isEmpty()) {
                if (!all) this.addMenuHeading(m, "Variated");
                for (Engine.Learned item : this.variatedPatterns) {
                    JMenuItem it = new JMenuItem(item.name);
                    it.addActionListener(e -> this.applySongPick("pattern", "v:" + item.id, replaceAt));
                    m.add(it);
                }
            }
            if (!this.plugins.isEmpty() || !this.learned.isEmpty()) {
                boolean anyImp = false;
                for (Engine.Plugin p : this.plugins) if (p.enabled && !p.styles.isEmpty()) anyImp = true;
                if (!this.learned.isEmpty()) anyImp = true;
                if (anyImp && !all) this.addMenuHeading(m, "Imported");
                for (Engine.Plugin p : this.plugins) {
                    if (!p.enabled || p.styles.isEmpty()) continue;
                    this.addMenuHeading(m, p.name);
                    for (Engine.Style st : p.styles) {
                        final String key = "p:" + p.id + "/" + st.id;
                        JMenuItem it = new JMenuItem("  " + st.label);
                        it.addActionListener(ev -> this.applySongPick("pattern", key, replaceAt));
                        m.add(it);
                    }
                }
                java.util.LinkedHashMap<String, java.util.ArrayList<Engine.Learned>> learnedPacks = new java.util.LinkedHashMap<>();
                for (Engine.Learned item : this.learned) {
                    String src = Engine.sourceOf(item);
                    if (src.isEmpty()) src = "Other";
                    java.util.ArrayList<Engine.Learned> list = learnedPacks.get(src);
                    if (list == null) {
                        list = new java.util.ArrayList<>();
                        learnedPacks.put(src, list);
                    }
                    list.add(item);
                }
                for (java.util.Map.Entry<String, java.util.ArrayList<Engine.Learned>> e : learnedPacks.entrySet()) {
                    this.addMenuHeading(m, e.getKey());
                    for (Engine.Learned item : e.getValue()) {
                        JMenuItem it = new JMenuItem("  " + item.name);
                        it.addActionListener(ev -> this.applySongPick("pattern", "l:" + item.id, replaceAt));
                        m.add(it);
                    }
                }
            }
        }
        if (all || "fillern".equals(only)) {
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<String>();
            keys.add(this.currentPatternKey());
            keys.addAll(this.fillernPairs.keySet());
            java.util.ArrayList<String[]> rows = new java.util.ArrayList<String[]>();
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<String>();
            for (String pk : keys) {
                if (pk == null || !seen.add(pk)) continue;
                if (!this.fillernUnderlined(pk)) continue;
                String fk = this.fillernFillKeyOf(pk);
                rows.add(new String[] { this.songPickSection(pk), pk, this.patternName(pk) + " \u00b7 " + this.fillLabel(fk) });
            }
            if (rows.isEmpty()) {
                if ("fillern".equals(only)) {
                    JMenuItem empty = new JMenuItem("No underlined Fillerns yet");
                    empty.setEnabled(false);
                    m.add(empty);
                }
            } else if (all) {
                if (m.getComponentCount() > 0) m.addSeparator();
                this.addMenuHeading(m, "Fillern");
                java.util.ArrayList<String[]> imported = new java.util.ArrayList<>();
                for (String[] row : rows) {
                    if ("Imported".equals(row[0])) imported.add(row);
                    else {
                        final String key = row[1];
                        JMenuItem it = new JMenuItem(row[2]);
                        it.addActionListener(e -> this.applySongPick("fillern", key, replaceAt));
                        m.add(it);
                    }
                }
                this.addImportedSongRows(m, imported, "fillern", replaceAt);
            } else {
                for (String sec : new String[] { "Built-in", "Variated" }) {
                    boolean headed = false;
                    for (String[] row : rows) {
                        if (!sec.equals(row[0])) continue;
                        if (!headed) {
                            this.addMenuHeading(m, sec);
                            headed = true;
                        }
                        final String key = row[1];
                        JMenuItem it = new JMenuItem(row[2]);
                        it.addActionListener(e -> this.applySongPick("fillern", key, replaceAt));
                        m.add(it);
                    }
                }
                java.util.ArrayList<String[]> imported = new java.util.ArrayList<>();
                for (String[] row : rows) if ("Imported".equals(row[0])) imported.add(row);
                if (!imported.isEmpty()) {
                    this.addMenuHeading(m, "Imported");
                    this.addImportedSongRows(m, imported, "fillern", replaceAt);
                }
            }
        }
        if (all || "fill".equals(only)) {
            java.util.ArrayList<String[]> fillRows = new java.util.ArrayList<String[]>();
            for (int i = 0; i < Engine.FILL_ID.length; i++) {
                if (this.hiddenFills.contains(Engine.FILL_ID[i])) continue;
                fillRows.add(new String[] { "Built-in", Engine.FILL_ID[i], Engine.FILL_LABEL[i] });
            }
            for (Engine.LearnedFill item : this.variatedFills) {
                fillRows.add(new String[] { "Variated", "v:" + item.id, item.name });
            }
            for (Engine.Plugin p : this.plugins) {
                if (!p.enabled) continue;
                for (Engine.PlugFill f : p.fills) {
                    fillRows.add(new String[] { "Imported", "p:" + p.id + "/" + f.id, f.name });
                }
            }
            for (Engine.LearnedFill item : this.learnedFills) {
                fillRows.add(new String[] { "Imported", "l:" + item.id, item.name });
            }
            if (all) {
                if (m.getComponentCount() > 0) m.addSeparator();
                this.addMenuHeading(m, "Fill");
                java.util.ArrayList<String[]> imported = new java.util.ArrayList<>();
                for (String[] row : fillRows) {
                    if ("Imported".equals(row[0])) imported.add(row);
                    else {
                        final String key = row[1];
                        JMenuItem it = new JMenuItem(row[2]);
                        it.addActionListener(e -> this.applySongPick("fill", key, replaceAt));
                        m.add(it);
                    }
                }
                this.addImportedSongRows(m, imported, "fill", replaceAt);
            } else {
                for (String sec : new String[] { "Built-in", "Variated" }) {
                    boolean headed = false;
                    for (String[] row : fillRows) {
                        if (!sec.equals(row[0])) continue;
                        if (!headed) {
                            this.addMenuHeading(m, sec);
                            headed = true;
                        }
                        final String key = row[1];
                        JMenuItem it = new JMenuItem(row[2]);
                        it.addActionListener(e -> this.applySongPick("fill", key, replaceAt));
                        m.add(it);
                    }
                }
                java.util.ArrayList<String[]> imported = new java.util.ArrayList<>();
                for (String[] row : fillRows) if ("Imported".equals(row[0])) imported.add(row);
                if (!imported.isEmpty()) {
                    this.addMenuHeading(m, "Imported");
                    this.addImportedSongRows(m, imported, "fill", replaceAt);
                }
            }
        }
        return m;
    }

    private void addFillernPick(java.util.List<JMenuItem> items, String patternKey, Integer replaceAt, java.util.Set<String> seen) {
        if (patternKey == null || !seen.add(patternKey)) return;
        if (!this.fillernUnderlined(patternKey)) return;
        String fk = this.fillernFillKeyOf(patternKey);
        JMenuItem it = new JMenuItem(this.patternName(patternKey) + " \u00b7 " + this.fillLabel(fk));
        final String key = patternKey;
        it.addActionListener(e -> this.applySongPick("fillern", key, replaceAt));
        items.add(it);
    }

    private String patternName(String key) {
        if (key == null) return "Pattern";
        if (key.startsWith("s:")) {
            Engine.Style st = this.styles.get(key.substring(2));
            return st != null ? st.label : key.substring(2);
        }
        if (key.startsWith("l:")) {
            for (Engine.Learned x : this.learned) if (x.id.equals(key.substring(2))) return x.name;
        }
        if (key.startsWith("v:")) {
            for (Engine.Learned x : this.variatedPatterns) if (x.id.equals(key.substring(2))) return x.name;
        }
        if (key.startsWith("p:")) {
            int slash = key.indexOf('/');
            if (slash > 2) {
                String pid = key.substring(2, slash);
                String sid = key.substring(slash + 1);
                for (Engine.Plugin p : this.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.Style st : p.styles) if (sid.equals(st.id)) return st.label;
                }
            }
        }
        Engine.Style st = this.styles.get(this.style);
        return st != null ? st.label : "Pattern";
    }

    private int patternBpm(String key) {
        if (key != null && key.startsWith("s:")) {
            Engine.Style st = this.styles.get(key.substring(2));
            if (st != null) return st.bpm;
        }
        if (key != null && key.startsWith("l:")) {
            for (Engine.Learned x : this.learned) if (x.id.equals(key.substring(2))) return x.bpm;
        }
        if (key != null && key.startsWith("v:")) {
            for (Engine.Learned x : this.variatedPatterns) if (x.id.equals(key.substring(2))) return x.bpm;
        }
        if (key != null && key.startsWith("p:")) {
            int slash = key.indexOf('/');
            if (slash > 2) {
                String pid = key.substring(2, slash);
                String sid = key.substring(slash + 1);
                for (Engine.Plugin p : this.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.Style st : p.styles) if (sid.equals(st.id)) return st.bpm;
                }
            }
        }
        return this.bpm();
    }

    private int[][] patternCellsFor(String key) {
        if (key != null && key.startsWith("s:")) {
            Engine.Style st = this.styles.get(key.substring(2));
            if (st != null) return Engine.styleCells(st);
        }
        if (key != null && key.startsWith("l:")) {
            for (Engine.Learned x : this.learned) if (x.id.equals(key.substring(2))) return Engine.copyCells(x.cells);
        }
        if (key != null && key.startsWith("v:")) {
            for (Engine.Learned x : this.variatedPatterns) if (x.id.equals(key.substring(2))) return Engine.copyCells(x.cells);
        }
        if (key != null && key.startsWith("p:")) {
            int slash = key.indexOf('/');
            if (slash > 2) {
                String pid = key.substring(2, slash);
                String sid = key.substring(slash + 1);
                for (Engine.Plugin p : this.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.Style st : p.styles) if (sid.equals(st.id)) return Engine.styleCells(st);
                }
            }
        }
        return Engine.copyCells(this.cells);
    }

    private int[][] songFillCells(String id, int[][] groove) {
        if (id != null && (id.startsWith("l:") || id.startsWith("v:") || id.startsWith("p:"))) {
            return this.fillCellsFor(id);
        }
        return Engine.buildFill(id, groove != null ? groove : this.cells, this.style);
    }

    /** Right click on a part of an imported song: replace it with a Fillern. */
    private JPopupMenu importedPartMenu(int n) {
        JPopupMenu m = new JPopupMenu();
        javax.swing.JMenu sub = new javax.swing.JMenu("Replace with fillern");
        List<String> keys = new ArrayList<String>();
        for (Engine.Learned item : this.learned) {
            String key = "l:" + item.id;
            if (this.selectedFillFor(key) != null) keys.add(key);
        }
        for (String id : this.styles.keySet()) {
            String key = this.patternKeyFor(id);
            if (!keys.contains(key) && this.selectedFillFor(key) != null) keys.add(key);
        }
        for (String key : keys) {
            String label = this.patternName(key) + " \u00b7 " + this.fillLabel(this.selectedFillFor(key)) + Engine.fillernModeNote(this.fillernModeOf(key));
            JMenuItem it = new JMenuItem(label);
            it.addActionListener(e -> this.replaceWithFillern(n, key));
            sub.add(it);
        }
        if (keys.isEmpty()) {
            JMenuItem none = new JMenuItem("No Fillerns yet: make one on the Fillern tab");
            none.setEnabled(false);
            sub.add(none);
        }
        m.add(sub);
        return m;
    }

    /** Puts a Fillern where part n was. Its type decides: "replaces end/start" keep the part's bars; "add after" adds the fill bar. */
    private void replaceWithFillern(int n, String key) {
        List<Engine.Part> cur = this.activeSong();
        if (n < 0 || n >= cur.size()) return;
        Engine.Part old = cur.get(n);
        String fillKey = this.selectedFillFor(key);
        if (fillKey == null) return;
        int[][] cells = this.patternCellsFor(key);
        int bar = Engine.barSteps(old.tsNum > 0 ? old.tsNum : this.tsNum, old.tsDen > 0 ? old.tsDen : this.tsDen);
        List<Engine.Part> parts = Engine.fillernParts(this.patternName(key), old.bpm, cells, Engine.usedSteps(cells), Math.max(1, old.repeats),
            this.fillLabel(fillKey), this.songFillCells(fillKey, cells), bar, this.fillernModeOf(key));
        for (Engine.Part p : parts) {
            p.tsNum = old.tsNum;
            p.tsDen = old.tsDen;
            if ("fill".equals(p.kind)) p.steps = bar;
        }
        cur.remove(n);
        cur.addAll(n, parts);
        if ("imported".equals(this.songLane)) this.persistLearned();
        this.refreshSong();
        this.setNow(old.name + " \u2192 " + this.patternName(key) + " Fillern");
    }

    private void addCurrentFillern() {
        String mode = this.fillernModeOf(this.currentPatternKey());
        if (!Engine.FILLERN_AFTER.equals(mode)) {
            // The fill goes into the pattern's last or first bar: the part keeps its 4 bars.
            for (Engine.Part p : Engine.fillernParts(this.styles.get(this.style).label, this.bpm(), this.cells, this.steps, 4,
                    this.fillLabel(this.fillId), this.fillPat, Engine.barSteps(this.tsNum, this.tsDen), mode)) {
                this.addPart(p);
            }
            return;
        }
        Engine.Part g = Engine.groove(this.styles.get(this.style).label, this.bpm(), this.cells, 4);
        g.lens = Engine.copyCells(this.lens);
        Engine.Part f = Engine.fill(this.fillLabel(this.fillId), this.bpm(), this.fillPat, 1);
        f.lens = Engine.copyCells(this.fillLens);
        this.addPart(g);
        this.addPart(f);
    }

    private void applySongPick(String kind, String key, Integer replaceAt) {
        if (!"original".equals(this.songLane)) return;
        List<Engine.Part> cur = this.activeSong();
        int idx = replaceAt == null ? -1 : replaceAt.intValue();
        if (idx < 0 || idx >= cur.size()) idx = -1;
        if ("pattern".equals(kind)) {
            int reps = idx >= 0 && "groove".equals(cur.get(idx).kind) ? cur.get(idx).repeats : 4;
            Engine.Part p = Engine.groove(this.patternName(key), this.patternBpm(key), this.patternCellsFor(key), reps);
            if (idx >= 0) cur.set(idx, p);
            else this.addPart(p);
        } else if ("fill".equals(kind)) {
            int[][] groove = idx >= 0 ? cur.get(idx).cells : this.cells;
            int reps = idx >= 0 && "fill".equals(cur.get(idx).kind) ? cur.get(idx).repeats : 1;
            int bpm = idx >= 0 ? cur.get(idx).bpm : this.bpm();
            Engine.Part p = Engine.fill(this.fillLabel(key), bpm, this.songFillCells(key, groove), reps);
            if (idx >= 0) cur.set(idx, p);
            else this.addPart(p);
        } else {
            int[][] gcells = this.patternCellsFor(key);
            String fk = this.fillernPairs.get(key);
            if (fk == null) fk = key.equals(this.currentPatternKey()) ? this.fillId : "toms";
            int greps = idx >= 0 && "groove".equals(cur.get(idx).kind) ? cur.get(idx).repeats : 4;
            String mode = this.fillernModeOf(key);
            if (!Engine.FILLERN_AFTER.equals(mode)) {
                // The fill replaces the end or start of the pattern: the song part keeps its length.
                List<Engine.Part> parts = Engine.fillernParts(this.patternName(key), this.patternBpm(key), gcells, Engine.usedSteps(gcells), greps,
                    this.fillLabel(fk), this.songFillCells(fk, gcells), Engine.barSteps(this.tsNum, this.tsDen), mode);
                if (idx >= 0) {
                    cur.set(idx, parts.get(0));
                    for (int i = 1; i < parts.size() && cur.size() < 24; i++) cur.add(idx + i, parts.get(i));
                } else {
                    for (Engine.Part p : parts) this.addPart(p);
                }
                this.refreshSong();
                this.setNow(this.patternName(key) + " Fillern");
                return;
            }
            Engine.Part g = Engine.groove(this.patternName(key), this.patternBpm(key), gcells, greps);
            Engine.Part f = Engine.fill(this.fillLabel(fk), this.patternBpm(key), this.songFillCells(fk, gcells), 1);
            if (idx >= 0) {
                cur.set(idx, g);
                if (cur.size() < 24) cur.add(idx + 1, f);
            } else {
                this.addPart(g);
                this.addPart(f);
            }
        }
        this.refreshSong();
        this.setNow("pattern".equals(kind) || "fill".equals(kind) ? this.patternName(key) : this.patternName(key) + " Fillern");
    }

    private void addPart(Engine.Part part) {
        List<Engine.Part> cur = this.activeSong();
        if ("imported".equals(this.songLane) && this.importedSong() == null) {
            this.setNow("No imported song yet");
            return;
        }
        if (cur.size() >= 24) {
            JOptionPane.showMessageDialog(this, "Song is full");
            return;
        }
        cur.add(part);
        part.tsNum = this.tsNum;
        part.tsDen = this.tsDen;
        if ("groove".equals(part.kind)) part.steps = this.steps;
        else part.steps = Engine.barSteps(this.tsNum, this.tsDen);
        if ("imported".equals(this.songLane)) this.persistLearned();
        this.refreshSong();
    }

    private void movePart(int n, int n2) {
        List<Engine.Part> cur = this.activeSong();
        int n3 = n + n2;
        if (n < 0 || n3 < 0 || n3 >= cur.size()) {
            return;
        }
        Engine.Part part = cur.get(n);
        cur.set(n, cur.get(n3));
        cur.set(n3, part);
        if ("imported".equals(this.songLane)) this.persistLearned();
        this.refreshSong();
    }

    private void refreshSong() {
        this.songModel.clear();
        this.timeline.removeAll();
        if (this.songAdds != null) {
            this.songAdds.setVisible("edit".equals(this.songMode)
                && !("imported".equals(this.songLane) && this.importedSongs.isEmpty()));
        }
        if (this.songLaneBar != null) {
            Component[] cs = this.songLaneBar.getComponents();
            for (int i = cs.length - 1; i >= 0; i--) {
                Object lane = cs[i] instanceof JComponent ? ((JComponent) cs[i]).getClientProperty("lane") : null;
                if (lane == null) this.songLaneBar.remove(i);
                else this.paintChip((JButton) cs[i], String.valueOf(lane).equals(this.songLane));
            }
            if ("imported".equals(this.songLane)) {
                for (Engine.ImportedSong s : this.importedSongs) {
                    final Engine.ImportedSong item = s;
                    JButton b = this.chip(item.name, item.id.equals(this.importedSongId));
                    b.addActionListener(e -> {
                        this.refreshFromFileSet(item);
                        this.importedSongId = item.id;
                        this.songLane = "imported";
                        this.refreshSong();
                    });
                    this.songLaneBar.add(b);
                }
            }
            this.songLaneBar.revalidate();
            this.songLaneBar.repaint();
        }
        List<Engine.Part> cur = this.activeSong();
        ArrayList<String> arrayList = new ArrayList<String>();
        for (int i = 0; i < cur.size(); ++i) {
            Engine.Part part = cur.get(i);
            String string = part.kind + ":" + part.name;
            if (!arrayList.contains(string)) {
                arrayList.add(string);
            }
            int n = arrayList.indexOf(string) + 1;
            boolean bl = this.songPlay && i == this.songPart;
            JPanel jPanel = new JPanel();
            jPanel.setLayout(new BoxLayout(jPanel, 1));
            jPanel.setOpaque(false);
            JLabel jLabel = new JLabel(Integer.toString(n), 0);
            jLabel.setOpaque(true);
            jLabel.setBackground(bl ? HIT : (Engine.partHasFill(part) ? new Color(Engine.FILL_CELL_COLOR, true) : ELEV));
            jLabel.setForeground(bl ? BG : FG);
            jLabel.setFont(new Font("SansSerif", 1, 15));
            jLabel.setAlignmentX(0.5f);
            jLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            JLabel jLabel2 = new JLabel(part.name, 0);
            jLabel2.setForeground(bl ? FG : MUTED);
            jLabel2.setFont(new Font("SansSerif", 0, 10));
            jLabel2.setAlignmentX(0.5f);
            jLabel2.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
            jPanel.add(jLabel);
            jPanel.add(jLabel2);
            this.timeline.add(jPanel);
            this.songModel.addElement(n + "  " + part.name + (bl ? "  Now" : "") + "  \u00d7" + part.repeats + "  " + part.bpm + " BPM");
        }
        this.timeline.revalidate();
        this.timeline.repaint();
        if (this.songPlay && this.songPart >= 0 && this.songPart < this.timeline.getComponentCount()) {
            final Component now = this.timeline.getComponent(this.songPart);
            SwingUtilities.invokeLater(() -> {
                java.awt.Rectangle r = now.getBounds();
                r.x = Math.max(0, r.x - 120);
                r.width += 240;
                this.timeline.scrollRectToVisible(r);
            });
        }
        if (this.songList != null && this.songPlay && this.songPart >= 0 && this.songPart < this.songModel.getSize()) {
            this.songList.setSelectedIndex(this.songPart);
            this.songList.ensureIndexIsVisible(this.songPart);
        }
        this.paintSongNow();
    }

    private void paintSongNow() {
        if (!"song".equals(this.view)) return;
        List<Engine.Part> parts = this.activeSong();
        if ("imported".equals(this.songLane) && this.importedSongs.isEmpty()) {
            this.setNow("No imported song yet");
            return;
        }
        int idx = this.songPlay ? this.songPart : -1;
        int global = 0;
        if (this.songPlay && this.sequencer != null) {
            global = (int) (this.sequencer.getTickPosition() / 120L);
        } else if (this.songPlay && idx >= 0) {
            global = Engine.songGlobalStep(parts, idx, 0, Math.max(0, this.playhead));
        }
        this.setNow(Engine.songNowLine(parts, this.songPlay, idx, global));
    }

    private void showView(String string) {
        this.view = string;
        if (("pattern".equals(string) || "combo".equals(string)) && !string.equals(this.importedFor)) this.refreshLearnedChips();
        boolean bl = "fills".equals(string);
        boolean combo = "combo".equals(string);
        boolean pattern = "pattern".equals(string);
        boolean groove = pattern || combo;
        boolean bl2 = "song".equals(string);
        boolean bl3 = "py".equals(string);
        boolean bl4 = "import".equals(string) || "export".equals(string) || "fsetinfo".equals(string) || "help".equals(string) || "midisettings".equals(string);
        boolean prompts = "prompts".equals(string);
        this.chrome.setVisible(!bl3 && !bl4 && !prompts && (!bl2 || !"play".equals(this.songMode)));
        this.styleHost.setVisible(groove);
        this.fillHost.setVisible(bl);
        this.knobsRow.setVisible(!bl2 && !bl3 && !bl4 && !prompts);
        this.toolsRow.setVisible(groove);
        if (pattern || combo || bl) {
            this.pages.show(this.pageHost, "grid");
        } else {
            this.pages.show(this.pageHost, string);
        }
        if (bl2) {
            this.refreshSong();
        }
        if (pattern) {
            this.fillLast = false;
            this.setNow(null);
            this.refreshStyles();
        }
        if (combo) {
            this.fillLast = true;
            String fk = this.fillernPairs.get(this.currentPatternKey());
            this.applyFill(fk != null ? fk : this.fillId);
            if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
                try {
                    long ticksPerBar = (long) this.steps * 120L;
                    long last = (Math.max(1, this.bars) - 1L) * ticksPerBar;
                    Sequence seq = this.sequencer.getSequence();
                    if (seq != null && seq.getTickLength() > 0) {
                        this.sequencer.setTickPosition(Math.min(last, seq.getTickLength() - 1));
                    }
                } catch (Exception ignored) { /* keep playing */ }
            }
            this.setNow("Last bar \u00b7 " + this.fillLabel(this.fillId));
            this.refreshStyles();
        }
        if (bl) {
            if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
                this.fillLast = true;
                try {
                    long ticksPerBar = (long) this.steps * 120L;
                    long last = (Math.max(1, this.bars) - 1L) * ticksPerBar;
                    Sequence seq = this.sequencer.getSequence();
                    if (seq != null && seq.getTickLength() > 0) {
                        this.sequencer.setTickPosition(Math.min(last, seq.getTickLength() - 1));
                    }
                } catch (Exception ignored) { /* keep playing */ }
            }
            this.setNow(this.fillLabel(this.fillId));
        }
        if ("pads".equals(string)) {
            this.setNow(this.livePads ? "Pads write the pattern" : "Tap a pad");
        }
        if (bl3) {
            this.setNow("Scripts on web");
        }
        if (prompts) {
            this.setNow("Prompts");
        }
        if ("help".equals(string)) {
            this.setNow("Help");
        }
        if ("midisettings".equals(string)) {
            this.setNow("Drum Midi Settings");
        }
        if ("import".equals(string)) {
            this.setNow("Choose a MIDI, song, WAV or SoundFont");
        }
        if ("fsetinfo".equals(string)) {
            this.setNow(this.infoStatus != null ? this.infoStatus.getText() : "File set");
        }
        if ("export".equals(string)) {
            this.setNow(this.exportName("mid").replace(".mid", ""));
        }
        this.refreshTabs();
        this.refreshFills();
        if (pattern || combo || bl) this.refreshGrid();
    }

    private void setNow(String string) {
        boolean bl = string != null && !string.trim().isEmpty();
        this.nowPlaying.setText(bl ? string : " ");
        this.nowPlaying.setVisible(bl);
    }

    private void refreshTabs() {
        Iterator<JButton> iterator = this.tabs.iterator();
        while (iterator.hasNext()) {
            JButton jButton = iterator.next();
            String tab = String.valueOf(jButton.getClientProperty("tab"));
            boolean bl = this.view.equals(tab)
                || ("file".equals(tab) && ("import".equals(this.view) || "export".equals(this.view) || "help".equals(this.view) || "midisettings".equals(this.view) || "fsetinfo".equals(this.view)));
            jButton.setBackground(bl ? ELEV : BG);
            jButton.setForeground(bl ? FG : MUTED);
        }
    }

    private void toggleSteps() {
        int bar = Engine.barSteps(this.tsNum, this.tsDen);
        if (bar * 2 <= Engine.MAX_STEPS && this.steps == bar) this.applySteps(bar * 2, true);
        else this.applySteps(bar, false);
    }

    private void applySteps(int n, boolean tile) {
        int bar = Engine.barSteps(this.tsNum, this.tsDen);
        int next = Engine.clampSteps(n);
        if (next != bar && !(next == bar * 2 && bar * 2 <= Engine.MAX_STEPS)) next = bar;
        if (next == this.steps && this.gridHost != null && this.gridHost.getComponentCount() > 0) {
            if (this.stepsBtn != null) {
                this.stepsBtn.setText(this.steps + " steps");
                this.paintOutline(this.stepsBtn, this.steps > bar);
            }
            return;
        }
        if (next > this.steps && tile) {
            int from = Math.max(1, Math.min(this.steps, bar));
            Engine.tileSteps(this.cells, from, next);
            Engine.tileSteps(this.lens, from, next);
            for (int s = from; s < next; s++) {
                this.accents[s] = this.accents[s % from];
            }
        }
        this.steps = next;
        if (this.stepsBtn != null) {
            this.stepsBtn.setText(this.steps + " steps");
            this.paintOutline(this.stepsBtn, this.steps > bar);
        }
        this.rebuildGrid();
        this.syncBuiltinFill();
        if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
            this.rebuildAndPlay(true);
        }
    }

    private void rebuildGrid() {
        if (this.gridHost == null) return;
        this.gridHost.removeAll();
        this.gridHost.add((Component)this.buildGrid(), "Center");
        this.gridHost.revalidate();
        this.gridHost.repaint();
    }

    private void refreshGrid() {
        int n;
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            if (this.trackLabs[n] != null) {
                this.trackLabs[n].setForeground(this.mutes[n] ? SUBTLE : MUTED);
            }
            for (int i = 0; i < this.steps; ++i) {
                if (this.buttons[n][i] == null) continue;
                boolean covered = Engine.lengthCoveredAt(this.editLens()[n], this.editCells()[n], i);
                int vel = this.editCells()[n][i];
                int len = vel > 0 ? Engine.lenAt(this.editLens(), n, i) : Engine.coverLen(this.editLens(), this.editCells(), n, i);
                this.paintCell(this.buttons[n][i], vel, len, i == this.playhead, this.accents[i], covered);
            }
        }
        for (n = 0; n < this.steps; ++n) {
            JButton jButton = this.accentBtns[n];
            if (jButton == null) continue;
            boolean bl = this.accents[n];
            jButton.setBackground(bl ? HIT : (n == this.playhead ? FG : ELEV));
            jButton.setForeground(bl || n == this.playhead ? BG : SUBTLE);
        }
    }

    private int[][] editCells() {
        return "fills".equals(this.view) ? this.fillPat : this.cells;
    }

    private int[][] editLens() {
        return "fills".equals(this.view) ? this.fillLens : this.lens;
    }

    private void setCellLen(int t, int s, int n) {
        int[][] vel = this.editCells();
        int[][] row = this.editLens();
        int len = Engine.clampLen(n);
        if (vel[t][s] <= 0) vel[t][s] = 100;
        row[t][s] = len <= 1 ? 0 : len;
        this.refreshGrid();
        if (!"fills".equals(this.view)) this.syncBuiltinFill();
        if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
            this.rebuildAndPlay(true);
        }
    }

    private void showLenMenu(JButton btn, int t, int s) {
        JPopupMenu menu = new JPopupMenu();
        int cur = Engine.lenAt(this.editLens(), t, s);
        JLabel head = new JLabel("Length \u00b7 " + Engine.TRACK_SHORT[t] + " \u00b7 " + (s + 1) + " \u00b7 " + Engine.noteLengthLabel(cur));
        head.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        head.setForeground(SUBTLE);
        menu.add(head);
        for (int i = 0; i < Engine.LEN_STEPS.length; i++) {
            final int n = Engine.LEN_STEPS[i];
            JMenuItem it = new JMenuItem(Engine.LEN_LABEL[i]);
            if (n == cur) it.setFont(it.getFont().deriveFont(Font.BOLD));
            it.addActionListener(e -> this.setCellLen(t, s, n));
            menu.add(it);
        }
        JMenuItem custom = new JMenuItem("Steps 1\u201316\u2026");
        custom.addActionListener(e -> {
            String raw = JOptionPane.showInputDialog(this, "Length in 16th notes (1\u201316)", Integer.toString(cur));
            if (raw == null) return;
            try {
                this.setCellLen(t, s, Integer.parseInt(raw.trim()));
            } catch (Exception ignored) { /* */ }
        });
        menu.add(custom);
        menu.show(btn, 0, btn.getHeight());
    }

    private void paintCell(JButton jButton, int n, int len, boolean bl, boolean bl2, boolean covered) {
        if (jButton == null) {
            return;
        }
        Color color = bl
            ? HIT
            : (n > 0 || covered
                ? new Color(Engine.lenColor(len, n), true)
                : (bl2 ? new Color(0x222422) : ELEV));
        jButton.setBackground(color);
        String mark = n > 0 ? (Engine.lenMark(len).isEmpty() ? "16" : Engine.lenMark(len)) : (covered ? "—" : "");
        jButton.setText(mark);
        jButton.setForeground(n >= 90 ? BG : (n > 0 || covered ? FG : SUBTLE));
    }

    private void refreshStyles() {
        for (JPanel bar : new JPanel[] { this.styleBar, this.variatedPatternBar, this.importedBar }) {
            this.walkChips(bar, jButton -> {
                Object sid = jButton.getClientProperty("style");
                boolean on = this.styleChosen && this.style.equals(sid);
                this.paintChip(jButton, on);
                boolean under = false;
                if ("combo".equals(this.view) && sid instanceof String) {
                    String pk = this.patternKeyFor((String) sid);
                    under = this.fillernUnderlined(pk);
                }
                this.underlineChip(jButton, under);
            });
        }
    }

    private void walkChips(JComponent host, java.util.function.Consumer<JButton> fn) {
        for (Component c : host.getComponents()) {
            if (c instanceof JButton) fn.accept((JButton) c);
            else if (c instanceof JComponent) this.walkChips((JComponent) c, fn);
        }
    }

    private JLabel sectionLab(String s) {
        JLabel l = new JLabel(s.toUpperCase());
        l.setForeground(SUBTLE);
        l.setFont(new Font("SansSerif", Font.BOLD, 10));
        l.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        l.setAlignmentX(0.0f);
        return l;
    }

    private void refreshFills() {
        for (JPanel bar : new JPanel[] { this.fillBar, this.variatedFillBar, this.importedFillBar }) {
            this.walkChips(bar, jButton -> {
                if (jButton.getClientProperty("fill") == null) return;
                this.paintChip(jButton, this.fillId.equals(jButton.getClientProperty("fill")));
            });
        }
    }

    /** The imported file sets scroll inside at most maxHeight pixels, so a long list leaves room for the grid. */
    private JScrollPane cappedScroll(JComponent content, int maxHeight) {
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = content.getPreferredSize();
                return new Dimension(d.width, Math.min(maxHeight, d.height + 4));
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, this.getPreferredSize().height);
            }
        };
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setAlignmentX(0.0f);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void flatten(JButton jButton) {
        jButton.setFocusPainted(false);
        jButton.setBorderPainted(false);
        jButton.setOpaque(true);
        jButton.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
    }

    private JButton chip(String string, boolean bl) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        this.paintChip(jButton, bl);
        return jButton;
    }

    private JButton outline(String string, boolean bl) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        this.paintOutline(jButton, bl);
        return jButton;
    }

    private void paintChip(JButton jButton, boolean bl) {
        jButton.setBackground(bl ? FG : ELEV);
        jButton.setForeground(bl ? BG : FG);
        jButton.setBorderPainted(false);
    }

    private void underlineChip(JButton b, boolean on) {
        Font f = b.getFont();
        Map<TextAttribute, Object> attrs = new HashMap<TextAttribute, Object>(f.getAttributes());
        attrs.put(TextAttribute.UNDERLINE, on ? TextAttribute.UNDERLINE_ON : Integer.valueOf(-1));
        b.setFont(f.deriveFont(attrs));
    }

    private void paintOutline(JButton jButton, boolean bl) {
        jButton.setOpaque(true);
        jButton.setBackground(bl ? new Color(2765356) : BG);
        jButton.setForeground(bl ? FG : MUTED);
        jButton.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(bl ? HIT : BORDER), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
    }

    private JButton cellBtn(String string) {
        return this.cellBtn(string, 28);
    }

    private JButton cellBtn(String string, int w) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        jButton.setFont(new Font("SansSerif", 1, 9));
        jButton.setPreferredSize(new Dimension(w, 22));
        jButton.setMinimumSize(new Dimension(w, 22));
        jButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        jButton.setBackground(ELEV);
        jButton.setForeground(SUBTLE);
        return jButton;
    }

    private JButton action(String string, Color color, Color color2) {
        JButton jButton = new JButton(string);
        this.flatten(jButton);
        jButton.setBackground(color);
        jButton.setForeground(color2);
        jButton.setFont(new Font("SansSerif", 1, 14));
        jButton.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        return jButton;
    }

    private void applyMute() {
        if (this.synth == null) {
            return;
        }
        try {
            MidiChannel midiChannel = this.synth.getChannels()[9];
            if (midiChannel != null) {
                midiChannel.setMute(this.muted);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void openMidi() {
        try {
            this.synth = MidiSystem.getSynthesizer();
            this.synth.open();
            this.sequencer = MidiSystem.getSequencer(false);
            this.sequencer.getTransmitter().setReceiver(this.synth.getReceiver());
            this.sequencer.open();
            this.sequencer.setTempoInBPM(this.bpm());
            this.applyMute();
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not open the computer MIDI synth.\n" + exception.getMessage());
        }
    }

    private int accentVel(int n, int n2) {
        int n3;
        if (n <= 0) {
            return 0;
        }
        if (this.accents[n2] && n < 127) {
            n = Math.min(127, n + 18);
        }
        if ((n3 = this.humanBar.getVal()) > 0) {
            n = Engine.clamp(n + (int)(Math.random() * (double)(n3 + 1)) - n3 / 2, 1, 127);
        }
        return n;
    }

    private Sequence sequenceFromCells(int[][] nArray, int n) throws Exception {
        Sequence sequence = new Sequence(0.0f, 480);
        Track track = sequence.createTrack();
        int n2 = 120;
        int n3 = (int)((double)n2 * ((double)this.swingBar.getVal() / 100.0));
        int n4 = Math.max(1, this.bars);
        int steps = this.steps;
        for (int i = 0; i < n4; ++i) {
            // The Fillern's fill plays in the last bar, or in the first when it replaces the pattern's start.
            int fillBar = Engine.FILLERN_START.equals(this.fillernModeOf(this.currentPatternKey())) ? 0 : n4 - 1;
            boolean last = this.fillLast && i == fillBar;
            int n5 = i * steps * n2;
            for (int j = 0; j < Engine.TRACK_ID.length; ++j) {
                if (this.mutes[j]) continue;
                for (int k = 0; k < steps; ++k) {
                    int[][] src = nArray;
                    int[][] srcLens = this.lens;
                    int idx = k;
                    if (last) {
                        int bar = Engine.barSteps(this.tsNum, this.tsDen);
                        if (steps <= bar) {
                            src = this.fillPat;
                            srcLens = this.fillLens;
                        } else if (k >= steps - bar) {
                            src = this.fillPat;
                            srcLens = this.fillLens;
                            idx = k - (steps - bar);
                        }
                    }
                    int n6 = this.accentVel(src[j][idx], k);
                    if (n6 <= 0) continue;
                    int n7 = n5 + k * n2;
                    if (k % 2 == 1) {
                        n7 += n3;
                    }
                    int gate = Engine.gateTicks(Engine.lenAt(srcLens, j, idx));
                    track.add(new MidiEvent(new ShortMessage(144, 9, Engine.NOTES[j], n6), n7));
                    track.add(new MidiEvent(new ShortMessage(128, 9, Engine.NOTES[j], 0), n7 + gate));
                }
            }
        }
        return sequence;
    }

    private Sequence sequenceFromSong() throws Exception {
        Sequence sequence = new Sequence(0.0f, 480);
        Track track = sequence.createTrack();
        int n = 120;
        int n2 = 0;
        for (Engine.Part part : this.activeSong()) {
            int n3 = (int)Math.round(6.0E7 / (double)Math.max(Engine.MIN_BPM, part.bpm));
            MetaMessage metaMessage = new MetaMessage();
            metaMessage.setMessage(81, new byte[]{(byte)(n3 >> 16 & 0xFF), (byte)(n3 >> 8 & 0xFF), (byte)(n3 & 0xFF)}, 3);
            track.add(new MidiEvent(metaMessage, n2));
            int n4 = Math.max(1, part.repeats);
            int partSteps = Engine.clampSteps(part.steps);
            for (int i = 0; i < n4; ++i) {
                for (int j = 0; j < Engine.TRACK_ID.length; ++j) {
                    if (this.mutes[j]) continue;
                    for (int k = 0; k < partSteps; ++k) {
                        int n5 = this.accentVel(part.cells[j][k], k);
                        if (n5 <= 0) continue;
                        int n6 = n2 + k * n;
                        int gate = Engine.gateTicks(Engine.lenAt(part.lens, j, k));
                        track.add(new MidiEvent(new ShortMessage(144, 9, Engine.NOTES[j], n5), n6));
                        track.add(new MidiEvent(new ShortMessage(128, 9, Engine.NOTES[j], 0), n6 + gate));
                    }
                }
                n2 += partSteps * n;
            }
        }
        return sequence;
    }

    private void togglePlay() {
        if (this.sequencer == null) {
            this.openMidi();
            if (this.sequencer == null) {
                return;
            }
        }
        if (this.sequencer.isRunning()) {
            this.stop();
            return;
        }
        this.songPlay = "song".equals(this.view) && !this.activeSong().isEmpty();
        if (this.songPlay && this.songPart < 0) this.songPart = 0;
        this.playBtn.setText("Pause");
        this.rebuildAndPlay(false);
    }

    private void rebuildAndPlay(boolean bl) {
        try {
            long l = bl && this.sequencer != null ? this.sequencer.getTickPosition() : 0L;
            Sequence sequence = this.songPlay ? this.sequenceFromSong() : this.sequenceFromCells(this.cells, this.bpm());
            this.sequencer.stop();
            this.sequencer.setSequence(sequence);
            this.sequencer.setLoopCount(this.songPlay ? 0 : -1);
            this.sequencer.setLoopStartPoint(0L);
            this.sequencer.setLoopEndPoint(Math.max(1L, sequence.getTickLength()));
            if (!this.songPlay) {
                this.sequencer.setTempoInBPM(this.bpm());
            }
            this.sequencer.setTickPosition(l % Math.max(1L, sequence.getTickLength()));
            this.sequencer.start();
            if (this.songPlay) this.paintSongNow();
            if (this.playheadTimer == null) {
                this.playheadTimer = new Timer(40, actionEvent -> {
                    if (this.sequencer == null || !this.sequencer.isRunning()) {
                        return;
                    }
                    long tick = this.sequencer.getTickPosition();
                    List<Engine.Part> playing = this.activeSong();
                    int span = this.songPlay && this.songPart >= 0 && this.songPart < playing.size()
                        ? (playing.get(this.songPart).steps == Engine.MAX_STEPS ? Engine.MAX_STEPS : Engine.STEPS)
                        : this.steps;
                    int n = (int)(tick / 120L % (long) Math.max(1, span));
                    if (this.songPlay) {
                        int n2;
                        int n3 = (int)(tick / 120L);
                        int n4 = 0;
                        int n5 = 0;
                        int n6 = 0;
                        while (n6 < playing.size()) {
                            Engine.Part sp = playing.get(n6);
                            n2 = Math.max(1, sp.repeats) * (sp.steps == Engine.MAX_STEPS ? Engine.MAX_STEPS : Engine.STEPS);
                            if (n3 < n4 + n2) {
                                n5 = n6;
                                break;
                            }
                            n4 += n2;
                            n5 = n6++;
                        }
                        if (n5 != this.songPart && n5 < playing.size()) {
                            this.songPart = n5;
                            Engine.Part part = playing.get(n5);
                            for (n2 = 0; n2 < Engine.TRACK_ID.length; ++n2) {
                                System.arraycopy(part.cells[n2], 0, this.cells[n2], 0, Engine.MAX_STEPS);
                            }
                            this.refreshSong();
                        } else {
                            this.paintSongNow();
                        }
                    }
                    if (n != this.playhead) {
                        this.playhead = n;
                        this.refreshGrid();
                    }
                });
                this.playheadTimer.start();
            }
            if (!bl) {
                this.sequencer.setTickPosition(0L);
            }
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not play: " + exception.getMessage());
        }
    }

    private void stop() {
        if (this.sequencer != null && this.sequencer.isRunning()) {
            this.sequencer.stop();
        }
        if (this.sequencer != null) {
            this.sequencer.setTickPosition(0L);
        }
        this.playhead = -1;
        this.songPart = -1;
        this.songPlay = false;
        if (this.playBtn != null) {
            this.playBtn.setText("Play");
        }
        this.refreshGrid();
        this.refreshSong();
    }

    private void generate() {
        Random random = new Random();
        if (!this.styles.containsKey(this.style)) this.style = "house";
        this.loadStyle(this.style, true);
        double d = 0.04 + (double)this.densBar.getVal() * 0.02;
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
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
        this.applyEnabledProcessors(random);
        this.syncBuiltinFill();
        this.refreshGrid();
        if (this.sequencer != null && this.sequencer.isRunning() && !this.songPlay) {
            this.rebuildAndPlay(true);
        }
    }

    private void preview(int n) {
        if (this.synth == null || this.muted || this.mutes[n]) {
            return;
        }
        try {
            MidiChannel midiChannel = this.synth.getChannels()[9];
            if (midiChannel == null) {
                return;
            }
            int kick = Engine.track("kick");
            if (n == Engine.track("dkick")) {
                midiChannel.noteOn(Engine.NOTES[kick], 110);
                if (this.doubleKickTimer != null) {
                    this.doubleKickTimer.stop();
                }
                int delay = Engine.doubleKickDelayMs(this.bpm(), this.tsDen);
                this.doubleKickTimer = new Timer(delay, actionEvent -> {
                    try {
                        if (this.synth == null || this.muted || this.mutes[n]) return;
                        MidiChannel ch = this.synth.getChannels()[9];
                        if (ch != null) ch.noteOn(Engine.NOTES[kick], 110);
                    } catch (Exception ignored) {
                    }
                });
                this.doubleKickTimer.setRepeats(false);
                this.doubleKickTimer.start();
                return;
            }
            midiChannel.noteOn(Engine.NOTES[n], 110);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void flashPad(int n) {
        if (this.padBtns[n] == null) {
            return;
        }
        this.padBtns[n].setBackground(HIT);
        Timer timer = new Timer(90, actionEvent -> this.padBtns[n].setBackground(ELEV));
        timer.setRepeats(false);
        timer.start();
    }

    private void saveMidi(String role) {
        JFileChooser jFileChooser = new JFileChooser();
        String label = this.styles.containsKey(this.style) ? this.styles.get(this.style).label : this.style;
        String name = Engine.midiFileName(label, this.bpm(), role, this.fillLabel(this.fillId));
        jFileChooser.setSelectedFile(new File(name));
        jFileChooser.setFileFilter(new FileNameExtensionFilter("MIDI", "mid", "midi"));
        if (jFileChooser.showSaveDialog(this) != 0) {
            return;
        }
        try {
            int[][] cells = Engine.cellsForMidiRole(this.cells, this.fillPat, role);
            int[][] lens = Engine.cellsForMidiRole(this.lens, this.fillLens, role);
            int steps = "fill".equals(role) ? Engine.barSteps(this.tsNum, this.tsDen) : this.steps;
            byte[] byArray = Engine.encodeMidi(cells, lens, this.bpm(), steps, this.tsNum, this.tsDen);
            Files.write(jFileChooser.getSelectedFile().toPath(), byArray, new OpenOption[0]);
            this.setNow("Saved " + name);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not save MIDI: " + exception.getMessage());
        }
    }

    private void saveSng() {
        List<Engine.Part> parts = this.activeSong();
        if (parts.isEmpty()) {
            String name = this.exportName("mid").replace(".mid", "");
            parts = Collections.singletonList(Engine.groove(name, this.bpm(), this.cells, 1));
        }
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setSelectedFile(new File(Engine.songFilename(parts, this.songFileSet(parts))));
        jFileChooser.setFileFilter(new FileNameExtensionFilter("Pulsekit song", "sng"));
        if (jFileChooser.showSaveDialog(this) != 0) {
            return;
        }
        try {
            String songSet = this.songFileSet(parts);
            Files.write(jFileChooser.getSelectedFile().toPath(), Engine.encodeSng(parts, songSet != null ? songSet : parts.get(0).name), new OpenOption[0]);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not save .sng: " + exception.getMessage());
        }
    }

    private void savePy() {
        byte[] data = this.pyBytes != null
            ? this.pyBytes
            : (this.pyEditor != null ? this.pyEditor.getText() : "").getBytes(StandardCharsets.UTF_8);
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setSelectedFile(new File(this.pyName));
        jFileChooser.setFileFilter(new FileNameExtensionFilter("Python, Java, JavaScript, or prompt", "py", "java", "jar", "class", "js", "mjs", "ts", "tsx", "prompt"));
        if (jFileChooser.showSaveDialog(this) != 0) {
            return;
        }
        try {
            Files.write(jFileChooser.getSelectedFile().toPath(), data, new OpenOption[0]);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not save program: " + exception.getMessage());
        }
    }

    private void savePrj() {
        try {
            this.saveBytes(this.exportName("prj"), "Pulsekit project", "prj", this.encodePrjBytes());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save .prj: " + ex.getMessage());
        }
    }

    private byte[] encodePrjBytes() throws Exception {
            LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
            String py = this.pyEditor != null ? this.pyEditor.getText() : "";
            String safeName = (this.pyName == null || this.pyName.isEmpty() ? "script.py" : this.pyName).replaceAll("[\\\\/]", "_");
            String scriptFile = "scripts/" + safeName;
            byte[] scriptBytes = this.pyBytes != null ? this.pyBytes : py.getBytes(StandardCharsets.UTF_8);
            files.put(scriptFile, scriptBytes);
            this.fillMissingVoices();
            String kitName = this.styles.containsKey(this.style) ? this.styles.get(this.style).label : "Pulsekit";
            byte[] sf2 = AudioIo.encodeSf2(this.voices, kitName);
            files.put("kit.sf2", sf2);
            StringBuilder pads = new StringBuilder();
            pads.append('[');
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                if (t > 0) pads.append(',');
                String file = "pads/" + t + "-" + Engine.TRACK_ID[t] + ".wav";
                files.put(file, AudioIo.encodeWav(this.voices[t], 22050));
                pads.append("{\"id\":").append(Engine.quote(Engine.TRACK_ID[t]));
                pads.append(",\"note\":").append(Engine.NOTES[t]);
                pads.append(",\"name\":").append(Engine.quote(Engine.TRACK_LABEL[t]));
                pads.append(",\"file\":").append(Engine.quote(file)).append('}');
            }
            pads.append(']');
            StringBuilder song = new StringBuilder();
            song.append('[');
            for (int i = 0; i < this.song.size(); i++) {
                if (i > 0) song.append(',');
                Engine.Part p = this.song.get(i);
                song.append("{\"kind\":").append(Engine.quote(p.kind));
                song.append(",\"name\":").append(Engine.quote(p.name));
                song.append(",\"repeats\":").append(p.repeats);
                song.append(",\"bpm\":").append(p.bpm);
                song.append(",\"steps\":").append(p.steps);
                song.append(",\"tsNum\":").append(p.tsNum);
                song.append(",\"tsDen\":").append(p.tsDen);
                song.append(",\"pattern\":").append(Engine.cellsJson(p.cells)).append('}');
            }
            song.append(']');
            StringBuilder mutes = new StringBuilder();
            mutes.append('{');
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                if (t > 0) mutes.append(',');
                mutes.append(Engine.quote(Engine.TRACK_ID[t])).append(':').append(this.mutes[t] ? "true" : "false");
            }
            mutes.append('}');
            String json = "{\"format\":\"pulsekit-prj\",\"v\":1,\"name\":" + Engine.quote(kitName)
                + ",\"kit\":{\"pattern\":" + Engine.cellsJson(this.cells)
                + ",\"bpm\":" + this.bpm()
                + ",\"swing\":" + this.swingBar.getVal()
                + ",\"humanize\":" + this.humanBar.getVal() / 100.0
                + ",\"density\":" + this.densBar.getVal()
                + ",\"steps\":" + this.steps
                + ",\"tsNum\":" + this.tsNum
                + ",\"tsDen\":" + this.tsDen
                + ",\"style\":" + Engine.quote(this.style)
                + ",\"bars\":" + this.bars
                + ",\"mutes\":" + mutes
                + ",\"fillLastBar\":" + this.fillLast
                + ",\"fillVariated\":" + this.fillVariated
                + ",\"accents\":" + Engine.boolJson(this.accents)
                + ",\"lengths\":" + Engine.cellsJson(this.lens)
                + "},\"fillPattern\":" + Engine.cellsJson(this.fillPat)
                + ",\"fillLengths\":" + Engine.cellsJson(this.fillLens)
                + ",\"fillId\":" + Engine.quote(this.fillId)
                + ",\"learned\":" + this.learnedJson()
                + ",\"learnedFills\":" + this.learnedFillsJson()
                + ",\"hiddenStyles\":" + this.stringListJson(this.hiddenStyles)
                + ",\"hiddenFills\":" + this.stringListJson(this.hiddenFills)
                + ",\"song\":" + song
                + ",\"live\":" + this.livePads
                + ",\"songMode\":" + Engine.quote(this.songMode)
                + ",\"scriptName\":" + Engine.quote(this.pyName)
                + ",\"scripts\":[{\"name\":" + Engine.quote(this.pyName) + ",\"file\":" + Engine.quote(scriptFile) + "}]"
                + ",\"plugins\":" + this.pluginsJson()
                + ",\"sf2\":\"kit.sf2\",\"sf2Name\":" + Engine.quote(kitName)
                + ",\"pads\":" + pads + "}";
            files.put("project.json", json.getBytes(StandardCharsets.UTF_8));
            for (Engine.Plugin p : this.plugins) {
                String safe = p.id.replaceAll("[^a-zA-Z0-9._-]", "_");
                files.put("plugins/" + safe + ".json", p.rawJson.getBytes(StandardCharsets.UTF_8));
            }
            return Engine.zipStored(files);
    }

    private String pluginsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < this.plugins.size(); i++) {
            if (i > 0) sb.append(',');
            Engine.Plugin p = this.plugins.get(i);
            String safe = p.id.replaceAll("[^a-zA-Z0-9._-]", "_");
            sb.append("{\"id\":").append(Engine.quote(p.id));
            sb.append(",\"file\":").append(Engine.quote("plugins/" + safe + ".json"));
            sb.append(",\"enabled\":").append(p.enabled);
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private String learnedJson() {
        return Engine.learnedJson(this.learned);
    }

    private String learnedFillsJson() {
        return Engine.learnedFillsJson(this.learnedFills);
    }

    private String stringListJson(List<String> list) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(Engine.quote(list.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    private List<String> fillernPairList() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : this.fillernPairs.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        return out;
    }

    private List<String> fillernModeList() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : this.fillernModes.entrySet()) out.add(e.getKey() + "=" + e.getValue());
        return out;
    }

    private String fillernModeOf(String patternKey) {
        return Engine.fillernModeOr(patternKey == null ? null : this.fillernModes.get(patternKey));
    }

    private void setFillernMode(String patternKey, String mode) {
        if (patternKey == null) return;
        String m = Engine.fillernMode(mode);
        this.fillernModes.put(patternKey, m);  // its own type, whatever the default is later
        this.persistLearned();
        if ("combo".equals(this.view)) this.refreshLearnedChips();
        this.setNow(Engine.FILLERN_MODE_LABELS[java.util.Arrays.asList(Engine.FILLERN_MODES).indexOf(m)]);
    }

    private void loadFillernPairs(String json) {
        this.fillernModes.clear();
        for (String row : this.parseStringArray(json, "fillernModes")) {
            int eq = row.indexOf('=');
            if (eq > 0) this.fillernModes.put(row.substring(0, eq), Engine.fillernMode(row.substring(eq + 1)));
        }
        this.fillernPicked.clear();
        this.fillernPicked.addAll(this.parseStringArray(json, "fillernPicked"));
        this.fillernPairs.clear();
        for (String row : this.parseStringArray(json, "fillernPairs")) {
            int eq = row.indexOf('=');
            if (eq > 0) this.fillernPairs.put(row.substring(0, eq), row.substring(eq + 1));
        }
    }

    private void restoreSessionFiles() {
        try {
            File lf = new File(this.pulsekitDir(), "learned.json");
            if (lf.isFile() && lf.length() > 8) {
                String json = new String(Files.readAllBytes(lf.toPath()), StandardCharsets.UTF_8);
                List<Engine.Learned> got = Engine.parseLearnedJson(json);
                if (!got.isEmpty()) {
                    this.learned.clear();
                    this.learned.addAll(got);
                    for (Engine.Learned item : this.learned) {
                        this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
                    }
                }
                List<Engine.LearnedFill> fills = Engine.parseLearnedFillsJson(json);
                if (!fills.isEmpty()) {
                    this.learnedFills.clear();
                    this.learnedFills.addAll(fills);
                }
                List<Engine.LearnedFill> vars = Engine.parseVariatedFillsJson(json);
                if (!vars.isEmpty()) {
                    this.variatedFills.clear();
                    this.variatedFills.addAll(vars);
                }
                List<Engine.Learned> vpat = Engine.parseVariatedPatternsJson(json);
                if (!vpat.isEmpty()) {
                    this.variatedPatterns.clear();
                    this.variatedPatterns.addAll(vpat);
                    for (Engine.Learned item : this.variatedPatterns) {
                        this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
                    }
                }
                this.refreshLearnedChips();
                this.loadFillernPairs(json);
                this.restoreFileSetInfo();
                List<Engine.ImportedSong> imported = Engine.decodeImportedSongs(json);
                if (!imported.isEmpty()) {
                    this.importedSongs.clear();
                    this.importedSongs.addAll(imported);
                    this.importedSongId = imported.get(0).id;
                }
            }
        } catch (Exception ignored) { /* optional */ }
        try {
            File sf = new File(this.pulsekitDir(), "kit.sf2");
            if (sf.isFile() && sf.length() > 64) {
                short[][] loaded = AudioIo.parseSf2(Files.readAllBytes(sf.toPath()));
                for (int t = 0; t < this.voices.length && t < loaded.length; t++) {
                    if (loaded[t] != null) this.voices[t] = loaded[t];
                }
                this.fillMissingVoices();
                this.reloadDrumBank();
            }
        } catch (Exception ignored) { /* optional */ }
    }

    private void persistLearned() {
        try {
            String json = "{\"learned\":" + Engine.learnedJson(this.learned)
                + ",\"learnedFills\":" + Engine.learnedFillsJson(this.learnedFills)
                + ",\"variatedFills\":" + Engine.learnedFillsJson(this.variatedFills)
                + ",\"variatedPatterns\":" + Engine.learnedJson(this.variatedPatterns)
                + ",\"fillernPairs\":" + this.stringListJson(this.fillernPairList())
                + ",\"fillernPicked\":" + this.stringListJson(new ArrayList<String>(this.fillernPicked))
                + ",\"fillernModes\":" + this.stringListJson(this.fillernModeList())
                + ",\"importedSongs\":" + Engine.importedSongsJson(this.importedSongs) + "}";
            Files.write(new File(this.pulsekitDir(), "learned.json").toPath(), json.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (Exception ignored) { /* optional */ }
    }

    private void persistSf2(byte[] bytes) {
        if (bytes == null || bytes.length < 16) return;
        try {
            Files.write(new File(this.pulsekitDir(), "kit.sf2").toPath(), bytes, new OpenOption[0]);
        } catch (Exception ignored) { /* optional */ }
    }

    private File pulsekitDir() {
        File d = new File(System.getProperty("user.home"), ".pulsekit");
        d.mkdirs();
        return d;
    }

    private File autosaveFile() {
        return new File(this.pulsekitDir(), "autosave.prj");
    }

    private File autosavePref() {
        return new File(this.pulsekitDir(), "autosave.on");
    }

    private void restoreAutosave() {
        boolean on = false;
        try {
            File pref = this.autosavePref();
            if (pref.isFile()) {
                String t = new String(Files.readAllBytes(pref.toPath()), StandardCharsets.UTF_8).trim();
                on = "1".equals(t) || "true".equalsIgnoreCase(t) || "on".equalsIgnoreCase(t);
            }
        } catch (Exception ignored) { /* first run */ }
        if (!on) return;
        if (this.autosaveBox != null) this.autosaveBox.setSelected(true);
        this.setAutosave(true, false);
        File prj = this.autosaveFile();
        if (!prj.isFile() || prj.length() < 16) return;
        try {
            this.loadPrj(Files.readAllBytes(prj.toPath()), "autosave.prj");
            this.setNow("Restored autosave");
        } catch (Throwable ex) {
            this.style = "house";
            this.fillId = "toms";
            this.setNow("Autosave skipped");
        }
    }

    private void setAutosave(boolean on, boolean announce) {
        this.autosaveOn = on;
        if (this.autosaveBox != null) {
            this.autosaveBox.setSelected(on);
            this.autosaveBox.setForeground(on ? FG : MUTED);
        }
        try {
            Files.write(this.autosavePref().toPath(), (on ? "1" : "0").getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (Exception ignored) { /* prefs optional */ }
        if (this.autosaveTimer != null) {
            this.autosaveTimer.stop();
            this.autosaveTimer = null;
        }
        if (on) {
            this.writeAutosave(false);
            this.autosaveTimer = new Timer(5000, e -> this.writeAutosave(false));
            this.autosaveTimer.setRepeats(true);
            this.autosaveTimer.start();
            if (announce) this.setNow("Autosave on \u00b7 " + this.autosaveFile().getAbsolutePath());
        } else if (announce) {
            this.setNow("Autosave off");
        }
    }

    private void writeAutosave(boolean announce) {
        if (!this.autosaveOn || this.autosaveBusy) return;
        this.autosaveBusy = true;
        new Thread(() -> {
            try {
                byte[] data = this.encodePrjBytes();
                File tmp = new File(this.pulsekitDir(), "autosave.tmp");
                Files.write(tmp.toPath(), data, new OpenOption[0]);
                File dest = this.autosaveFile();
                if (dest.exists() && !dest.delete()) {
                    Files.write(dest.toPath(), data, new OpenOption[0]);
                    tmp.delete();
                } else if (!tmp.renameTo(dest)) {
                    Files.write(dest.toPath(), data, new OpenOption[0]);
                    tmp.delete();
                }
                if (announce) {
                    SwingUtilities.invokeLater(() -> this.setNow("Autosaved"));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> this.setNow("Autosave failed"));
            } finally {
                this.autosaveBusy = false;
            }
        }, "pulsekit-autosave").start();
    }

    private void loadPrj(byte[] data, String filename) throws Exception {
        Map<String, byte[]> files = Engine.unzip(data);
        byte[] jsonBytes = files.get("project.json");
        if (jsonBytes == null) throw new IllegalArgumentException("Could not read that .prj project");
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        if (!json.contains("pulsekit-prj")) throw new IllegalArgumentException("Could not read that .prj project");
        int bpm = Engine.clampBpm(extractJsonInt(json, "\"bpm\"", this.bpm()));
        this.tempoBar.setVal(bpm);
        this.bpmField.setText(Integer.toString(bpm));
        int swing = extractJsonInt(json, "\"swing\"", this.swingBar.getVal());
        this.swingBar.setVal(Engine.clamp(swing, 0, 75));
        int dens = extractJsonInt(json, "\"density\"", this.densBar.getVal());
        this.densBar.setVal(Engine.clamp(dens, 1, 10));
        int human = (int) Math.round(extractJsonDouble(json, "\"humanize\"", this.humanBar.getVal() / 100.0) * 100);
        this.humanBar.setVal(Engine.clamp(human, 0, 100));
        this.bars = Engine.clamp(extractJsonInt(json, "\"bars\"", this.bars), 1, 16);
        String st = extractJsonString(json, "\"style\"");
        if (st != null && this.styles.containsKey(st)) this.style = st;
        String fid = extractJsonString(json, "\"fillId\"");
        if (fid != null) this.fillId = fid;
        this.fillLast = json.contains("\"fillLastBar\":true");
        this.fillVariated = json.contains("\"fillVariated\":true");
        this.livePads = json.contains("\"live\":true");
        copyPatternFromJson(json, "\"pattern\"", this.cells);
        copyPatternFromJson(json, "\"fillPattern\"", this.fillPat);
        copyPatternFromJson(json, "\"lengths\"", this.lens);
        copyPatternFromJson(json, "\"fillLengths\"", this.fillLens);
        this.learned.clear();
        this.learnedFills.clear();
        this.hiddenStyles.clear();
        this.hiddenFills.clear();
        for (String obj : Engine.jsonObjects(json, "learned")) {
            Engine.Learned item = new Engine.Learned();
            item.id = Engine.jsonStr(obj, "\"id\"");
            item.name = Engine.jsonStr(obj, "\"name\"");
            if (item.id == null) item.id = Engine.newLearnedId();
            if (item.name == null) item.name = "Import";
            item.bpm = Engine.clampBpm(Engine.jsonInt(obj, "\"bpm\"", this.bpm()));
            item.tsNum = Engine.clampTsNum(Engine.jsonInt(obj, "\"tsNum\"", 4));
            item.tsDen = Engine.clampTsDen(Engine.jsonInt(obj, "\"tsDen\"", 4));
            item.steps = Engine.clampSteps(Engine.jsonInt(obj, "\"steps\"", Engine.STEPS));
            item.closest = Engine.jsonStr(obj, "\"closest\"");
            item.source = Engine.jsonStr(obj, "\"source\"");
            if (item.source != null && item.source.isEmpty()) item.source = null;
            item.cells = Engine.emptyCells();
            Engine.patternFromJson(obj, item.cells);
            this.learned.add(item);
            this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        }
        for (String obj : Engine.jsonObjects(json, "learnedFills")) {
            Engine.LearnedFill item = new Engine.LearnedFill();
            item.id = Engine.jsonStr(obj, "\"id\"");
            item.name = Engine.jsonStr(obj, "\"name\"");
            item.kind = Engine.jsonStr(obj, "\"kind\"");
            if (item.id == null) item.id = Engine.newLearnedId();
            if (item.name == null) item.name = "Fill";
            if (item.kind == null) item.kind = "toms";
            item.source = Engine.jsonStr(obj, "\"source\"");
            if (item.source != null && item.source.isEmpty()) item.source = null;
            item.cells = Engine.emptyCells();
            Engine.patternFromJson(obj, item.cells);
            this.learnedFills.add(item);
        }
        this.hiddenStyles.addAll(this.parseStringArray(json, "hiddenStyles"));
        this.hiddenFills.addAll(this.parseStringArray(json, "hiddenFills"));
        this.tsNum = Engine.clampTsNum(extractJsonInt(json, "\"tsNum\"", 4));
        this.tsDen = Engine.clampTsDen(extractJsonInt(json, "\"tsDen\"", 4));
        this.tsNumField.setText(Integer.toString(this.tsNum));
        this.tsDenField.setText(Integer.toString(this.tsDen));
        this.applySteps(Engine.clampSteps(extractJsonInt(json, "\"steps\"", this.steps)), false);
        List<Engine.Part> parts = Engine.decodeSng(json.getBytes(StandardCharsets.UTF_8));
        this.song.clear();
        this.song.addAll(parts);
        this.songLane = "original";
        byte[] sf2 = files.get("kit.sf2");
        if (sf2 != null) {
            short[][] loaded = AudioIo.parseSf2(sf2);
            for (int t = 0; t < this.voices.length && t < loaded.length; t++) {
                if (loaded[t] != null) this.voices[t] = loaded[t];
            }
        }
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (!e.getKey().startsWith("pads/") || e.getValue() == null) continue;
            try {
                AudioIo.Pcm pcm = AudioIo.parseWav(e.getValue());
                short[] s = new short[pcm.samples.length];
                for (int i = 0; i < s.length; i++) s[i] = (short) Math.max(-32768, Math.min(32767, pcm.samples[i] * 32768f));
                String id = e.getKey();
                for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                    if (id.contains(Engine.TRACK_ID[t])) {
                        this.voices[t] = s;
                        break;
                    }
                }
            } catch (Exception ignored) { /* raw pad bytes from web */ }
        }
        byte[] prog = null;
        String progName = null;
        byte[] legacy = files.get("scripts/0-script.py");
        if (legacy != null) {
            prog = legacy;
            progName = "script.py";
        }
        for (String k : files.keySet()) {
            if (k == null || !k.startsWith("scripts/")) continue;
            String low = k.toLowerCase();
            if (!(low.endsWith(".py") || low.endsWith(".java") || low.endsWith(".jar") || low.endsWith(".class") || low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".ts") || low.endsWith(".tsx") || low.endsWith(".prompt"))) continue;
            prog = files.get(k);
            progName = k.substring(k.lastIndexOf('/') + 1);
            break;
        }
        if (prog != null && progName != null) {
            boolean binary = progName.toLowerCase().endsWith(".jar") || progName.toLowerCase().endsWith(".class");
            this.rememberProgram(progName, prog, binary, false);
        }
        this.plugins.clear();
        for (String k : files.keySet()) {
            if (!k.startsWith("plugins/") || !k.endsWith(".json")) continue;
            try {
                this.installPlugin(Engine.decodePlugin(files.get(k)), false);
            } catch (Exception ignored) { /* skip bad pack */ }
        }
        for (String obj : Engine.jsonObjects(json, "plugins")) {
            String pid = Engine.jsonStr(obj, "\"id\"");
            if (pid == null) continue;
            boolean on = !obj.contains("\"enabled\":false");
            this.setPluginEnabled(pid, on);
        }
        this.refreshPluginChips();
        this.refreshLearnedChips();
        if (st != null && this.styles.containsKey(st)) this.style = st;
        this.refreshStyles();
        this.refreshGrid();
        this.refreshFills();
        this.refreshSong();
        this.showView(this.song.isEmpty() ? (this.fillLast ? "combo" : "pattern") : "song");
        this.setNow("Project \u00b7 " + filename.replaceAll("^.*[/\\\\]", ""));
    }

    private List<String> parseStringArray(String json, String key) {
        List<String> out = new ArrayList<String>();
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) return out;
        int br = json.indexOf('[', at);
        int cl = json.indexOf(']', br);
        if (br < 0 || cl < 0) return out;
        String inner = json.substring(br + 1, cl).trim();
        if (inner.isEmpty()) return out;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(inner);
        while (m.find()) out.add(m.group(1).replace("\\\"", "\""));
        return out;
    }

    private void loadPlugin(byte[] data, String filename) throws Exception {
        Engine.Plugin p = Engine.decodePlugin(data);
        this.installPlugin(p, true);
    }

    private int extractJsonInt(String json, String key, int fallback) {
        int at = json.indexOf(key);
        if (at < 0) return fallback;
        int c = json.indexOf(':', at);
        if (c < 0) return fallback;
        int i = c + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n')) i++;
        int j = i;
        while (j < json.length() && "-0123456789".indexOf(json.charAt(j)) >= 0) j++;
        try {
            return Integer.parseInt(json.substring(i, j));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double extractJsonDouble(String json, String key, double fallback) {
        int at = json.indexOf(key);
        if (at < 0) return fallback;
        int c = json.indexOf(':', at);
        if (c < 0) return fallback;
        int i = c + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n')) i++;
        int j = i;
        while (j < json.length() && "-0123456789.eE".indexOf(json.charAt(j)) >= 0) j++;
        try {
            return Double.parseDouble(json.substring(i, j));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String extractJsonString(String json, String key) {
        int at = json.indexOf(key);
        if (at < 0) return null;
        int c = json.indexOf(':', at);
        if (c < 0) return null;
        int q = json.indexOf('"', c + 1);
        if (q < 0) return null;
        int q2 = json.indexOf('"', q + 1);
        if (q2 < 0) return null;
        return json.substring(q + 1, q2);
    }

    private void copyPatternFromJson(String json, String key, int[][] dest) {
        int at = json.indexOf(key);
        if (at < 0) return;
        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
            String k = "\"" + Engine.TRACK_ID[t] + "\"";
            int p = json.indexOf(k, at);
            if (p < 0) continue;
            int br = json.indexOf('[', p);
            int cl = json.indexOf(']', br);
            if (br < 0 || cl < 0) continue;
            String[] nums = json.substring(br + 1, cl).split(",");
            for (int s = 0; s < Engine.MAX_STEPS && s < nums.length; s++) {
                try {
                    dest[t][s] = Integer.parseInt(nums[s].trim());
                } catch (Exception ignored) { /* */ }
            }
        }
    }

    private void saveWav() {
        this.saveBytes(this.exportName("wav"), "WAV", "wav", AudioIo.encodeWav(this.mixPcm(), 22050));
    }

    private void saveMp3() {
        this.saveBytes(this.exportName("mp3"), "MP3", "mp3", AudioIo.encodeMp3(this.mixPcm(), 22050));
    }

    private void saveSf2() {
        this.fillMissingVoices();
        String string = this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : "Pulsekit";
        this.saveBytes(this.exportName("sf2"), "SoundFont", "sf2", AudioIo.encodeSf2(this.voices, string));
    }

    private void saveBytes(String string, String string2, String string3, byte[] byArray) {
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setSelectedFile(new File(string));
        jFileChooser.setFileFilter(new FileNameExtensionFilter(string2, string3));
        if (jFileChooser.showSaveDialog(this) != 0) {
            return;
        }
        try {
            Files.write(jFileChooser.getSelectedFile().toPath(), byArray, new OpenOption[0]);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not save " + string2 + ": " + exception.getMessage());
        }
    }

    private String exportName(String string) {
        String string2 = this.styles.containsKey(this.style) ? this.styles.get((Object)this.style).label : this.style;
        boolean bl = true;
        for (int i = 0; i < Engine.TRACK_ID.length && bl; ++i) {
            for (int j = 0; j < this.steps; ++j) {
                if (this.cells[i][j] <= 0) continue;
                bl = false;
            }
        }
        return AudioIo.fileName(bl ? "silent" : string2, this.bpm(), string, this.fillVariated);
    }

    private Engine.DrumSet activeDrumSet() {
        if (this.drumSets.isEmpty()) this.drumSets.add(Engine.DrumSet.originalSet());
        if (this.drumSetIndex < 0 || this.drumSetIndex >= this.drumSets.size()) this.drumSetIndex = 0;
        return this.drumSets.get(this.drumSetIndex);
    }

    private short[] voiceAt(int t) {
        int src = Engine.soundTrack(t);
        Engine.DrumSet s = this.activeDrumSet();
        if (s.original || s.matchOrig[src]) return this.voices[src];
        return s.samples[src];
    }

    private short[][] mixVoices() {
        this.fillMissingVoices();
        Engine.DrumSet s = this.activeDrumSet();
        short[][] out = new short[this.voices.length][];
        for (int t = 0; t < Engine.TRACK_ID.length && t < out.length; t++) {
            int src = Engine.soundTrack(t);
            out[t] = s.original || s.matchOrig[src] ? this.voices[src] : s.samples[src];
        }
        return out;
    }

    private void refreshDrumSets() {
        if (this.drumSetBar == null) return;
        this.drumSetBar.removeAll();
        JLabel lab = new JLabel("Drum sets  ");
        lab.setForeground(SUBTLE);
        this.drumSetBar.add(lab);
        for (int i = 0; i < this.drumSets.size(); i++) {
            final int idx = i;
            Engine.DrumSet s = this.drumSets.get(i);
            JButton b = this.chip(s.name, idx == this.drumSetIndex);
            if (!s.original) {
                java.util.Map<TextAttribute, Object> attrs = new java.util.HashMap<TextAttribute, Object>();
                attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                b.setFont(b.getFont().deriveFont(attrs));
            }
            b.addActionListener(e -> {
                this.drumSetIndex = idx;
                this.reloadDrumBank();
                this.refreshDrumSets();
                this.refreshPadLabels();
                this.setNow(s.original ? "Original kit" : s.name + " \u00b7 " + s.foundCount() + " pads");
            });
            if (!s.original) this.onChipMenu(b, () -> this.drumSetMenu(idx));
            this.drumSetBar.add(b);
        }
        this.drumSetBar.revalidate();
        this.drumSetBar.repaint();
        this.refreshPadLabels();
        Engine.DrumSet cur = this.activeDrumSet();
        if (this.padMatchAll != null) {
            boolean iso = cur != null && !cur.original;
            this.padMatchAll.setVisible(iso);
            if (iso) {
                boolean all = true;
                for (int t = 0; t < Engine.TRACK_ID.length; t++) if (!cur.matchOrig[t]) { all = false; break; }
                this.padMatchAll.setText(all ? "This set" : "Match Original");
                this.paintOutline(this.padMatchAll, !all);
            }
        }
    }

    private void matchWholeOriginal() {
        Engine.DrumSet s = this.activeDrumSet();
        if (s == null || s.original) return;
        boolean all = true;
        for (int t = 0; t < Engine.TRACK_ID.length; t++) if (!s.matchOrig[t]) { all = false; break; }
        for (int t = 0; t < Engine.TRACK_ID.length; t++) s.matchOrig[t] = !all;
        this.reloadDrumBank();
        this.refreshDrumSets();
        this.setNow(!all ? "Whole set matches Original" : "Using this set's sounds");
    }

    private JPopupMenu drumSetMenu(int idx) {
        JPopupMenu m = new JPopupMenu();
        if (idx <= 0 || idx >= this.drumSets.size()) return m;
        JMenuItem matchAll = new JMenuItem("Match Original");
        matchAll.addActionListener(e -> {
            this.drumSetIndex = idx;
            this.matchWholeOriginal();
        });
        JMenuItem match = new JMenuItem("Match empty from Original");
        match.addActionListener(e -> {
            this.drumSetIndex = idx;
            Engine.DrumSet s = this.drumSets.get(idx);
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                if (s.samples[t] == null) s.matchOrig[t] = true;
            }
            this.reloadDrumBank();
            this.refreshDrumSets();
            this.setNow("Empty pads match Original");
        });
        JMenuItem del = new JMenuItem("Delete set");
        del.addActionListener(e -> {
            if (idx <= 0 || idx >= this.drumSets.size()) return;
            this.drumSets.remove(idx);
            if (this.drumSetIndex >= this.drumSets.size()) this.drumSetIndex = 0;
            this.reloadDrumBank();
            this.refreshDrumSets();
            this.setNow("Drum set removed");
        });
        m.add(matchAll);
        m.add(match);
        m.add(del);
        return m;
    }

    private JPopupMenu padMenu(int t) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem load = new JMenuItem("Load WAV / MP3");
        load.addActionListener(e -> this.loadPadSample(t));
        m.add(load);
        Engine.DrumSet s = this.activeDrumSet();
        if (!s.original) {
            if (s.matchOrig[t]) {
                JMenuItem use = new JMenuItem("Use this set's sound");
                use.addActionListener(e -> {
                    s.matchOrig[t] = false;
                    this.reloadDrumBank();
                    this.refreshPadLabels();
                    this.setNow(Engine.TRACK_LABEL[t] + " \u00b7 this set");
                });
                m.add(use);
            } else {
                JMenuItem match = new JMenuItem("Match Original");
                match.addActionListener(e -> {
                    s.matchOrig[t] = true;
                    this.reloadDrumBank();
                    this.refreshPadLabels();
                    this.setNow(Engine.TRACK_LABEL[t] + " \u00b7 Original");
                });
                m.add(match);
            }
            if (s.samples[t] != null) {
                JMenuItem clear = new JMenuItem("Remove sample");
                clear.addActionListener(e -> {
                    s.samples[t] = null;
                    this.reloadDrumBank();
                    this.refreshPadLabels();
                    this.setNow(Engine.TRACK_LABEL[t] + " \u00b7 empty");
                });
                m.add(clear);
            }
        }
        return m;
    }

    private void refreshPadLabels() {
        Engine.DrumSet s = this.activeDrumSet();
        for (int t = 0; t < this.padBtns.length; t++) {
            if (this.padBtns[t] == null) continue;
            String extra = Engine.TRACK_LABEL[t];
            if (!s.original) {
                if (s.matchOrig[t]) extra = "Original";
                else if (s.samples[t] != null) extra = "From song";
                else extra = "Empty";
            }
            this.padBtns[t].setText(Engine.TRACK_SHORT[t] + "  " + extra);
        }
    }

    private short[] mixPcm() {
        return AudioIo.mix(this.cells, this.fillPat, this.fillLast, this.mutes, this.mixVoices(), this.bpm(), this.bars, 22050, this.steps);
    }

    private void fillMissingVoices() {
        short[][] sArray = AudioIo.buildVoices(22050);
        for (int i = 0; i < this.voices.length; ++i) {
            if (this.voices[i] != null) continue;
            this.voices[i] = sArray[i];
        }
    }

    private void loadPadSample(int n) {
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setFileFilter(new FileNameExtensionFilter("WAV or MP3", "wav", "wave", "mp3"));
        if (jFileChooser.showOpenDialog(this) != 0) {
            return;
        }
        try {
            byte[] byArray = Files.readAllBytes(jFileChooser.getSelectedFile().toPath());
            this.applyPadBytes(n, byArray, jFileChooser.getSelectedFile().getName());
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not load pad sample: " + exception.getMessage());
        }
    }

    private void applyPadBytes(int n, byte[] byArray, String string) throws Exception {
        String string2 = AudioIo.sniff(byArray);
        AudioIo.Pcm pcm;
        if ("mp3".equals(string2) || string.toLowerCase().endsWith(".mp3")) {
            pcm = Mp3Decode.parse(byArray);
        } else if ("wav".equals(string2) || string.toLowerCase().endsWith(".wav") || string.toLowerCase().endsWith(".wave")) {
            pcm = AudioIo.parseWav(byArray);
        } else {
            throw new IllegalArgumentException("Use a WAV or MP3 sample");
        }
        short[] sArray = AudioIo.floatsToShorts(pcm.samples);
        int n2 = 88200;
        if (sArray.length > n2) {
            short[] sArray2 = new short[n2];
            System.arraycopy(sArray, 0, sArray2, 0, n2);
            sArray = sArray2;
        }
        Engine.DrumSet set = this.activeDrumSet();
        if (set.original) {
            this.voices[n] = sArray;
        } else {
            set.samples[n] = sArray;
            set.matchOrig[n] = false;
        }
        this.reloadDrumBank();
        this.refreshPadLabels();
        this.setNow(Engine.TRACK_LABEL[n] + " \u00b7 " + string);
    }

    private void reloadDrumBank() {
        if (this.synth == null) {
            return;
        }
        try {
            this.fillMissingVoices();
            byte[] byArray = AudioIo.encodeSf2(this.mixVoices(), "Pulsekit");
            Soundbank soundbank = MidiSystem.getSoundbank(new ByteArrayInputStream(byArray));
            this.synth.unloadAllInstruments(this.synth.getDefaultSoundbank());
            this.synth.loadAllInstruments(soundbank);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private void openFile() {
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setFileFilter(new FileNameExtensionFilter("MIDI, song, WAV, MP3, SoundFont, Python, Java, JavaScript, prompt, project, plugin, file set", "sng", "mid", "midi", "wav", "wave", "mp3", "sf2", "py", "java", "class", "jar", "js", "mjs", "ts", "tsx", "prompt", "prj", "pkp", "fset"));
        if (jFileChooser.showOpenDialog(this) != 0) {
            return;
        }
        File file = jFileChooser.getSelectedFile();
        try {
            this.ingest(Files.readAllBytes(file.toPath()), file.getName());
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Could not read that file: " + exception.getMessage());
        }
    }

    private void ingest(byte[] byArray, String string) throws Exception {
        int n;
        String string2 = string.toLowerCase();
        String string3 = AudioIo.sniff(byArray);
        if ("unknown".equals(string3)) {
            if (string2.endsWith(".sng")) {
                string3 = "sng";
            } else if (string2.endsWith(".sf2")) {
                string3 = "sf2";
            } else if (string2.endsWith(".wav") || string2.endsWith(".wave")) {
                string3 = "wav";
            } else if (string2.endsWith(".mp3")) {
                string3 = "mp3";
            } else if (string2.endsWith(".mid") || string2.endsWith(".midi")) {
                string3 = "midi";
            } else if (string2.endsWith(".py")) {
                string3 = "py";
            } else if (string2.endsWith(".prompt")) {
                string3 = "prompt";
            } else if (string2.endsWith(".java")) {
                string3 = "java";
            } else if (string2.endsWith(".class")) {
                string3 = "class";
            } else if (string2.endsWith(".jar")) {
                string3 = "jar";
            } else if (string2.endsWith(".js") || string2.endsWith(".mjs") || string2.endsWith(".cjs")) {
                string3 = "js";
            } else if (string2.endsWith(".ts") || string2.endsWith(".mts") || string2.endsWith(".tsx")) {
                string3 = "ts";
            } else if (string2.endsWith(".prj") || Engine.isPrj(byArray)) {
                string3 = "prj";
            } else if (string2.endsWith(".pkp") || Engine.isPlugin(byArray)) {
                string3 = "pkp";
            } else if (string2.endsWith(".fset") || Engine.isFset(byArray)) {
                string3 = "fset";
            }
        }
        if ("prj".equals(string3) || Engine.isPrj(byArray)) {
            this.loadPrj(byArray, string);
            return;
        }
        if ("pkp".equals(string3) || Engine.isPlugin(byArray)) {
            this.loadPlugin(byArray, string);
            return;
        }
        if ("fset".equals(string3) || Engine.isFset(byArray)) {
            this.loadFset(byArray, string);
            return;
        }
        if ("py".equals(string3) || "java".equals(string3) || "class".equals(string3) || "jar".equals(string3) || "prompt".equals(string3) || "js".equals(string3) || "ts".equals(string3)) {
            if ("jar".equals(string3) && JavaRun.zipEntry(byArray, "pattern.json") != null) {
                byte[] mid = JavaRun.zipEntry(byArray, "beat.mid");
                if (mid != null) {
                    this.applyMidiBytes(mid, string);
                    return;
                }
            }
            String base = string.replaceAll("^.*[/\\\\]", "");
            boolean binary = "jar".equals(string3) || "class".equals(string3);
            this.loadProgram(base, byArray, binary);
            return;
        }
        if ("sng".equals(string3)) {
            List<Engine.Part> list = Engine.decodeSng(byArray);
            if (list.isEmpty()) {
                throw new IllegalArgumentException("Could not read that .sng song");
            }
            this.song.clear();
            this.song.addAll(list);
            this.songLane = "original";
            this.showView("song");
            this.refreshSong();
            Engine.Part part = list.get(0);
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                System.arraycopy(part.cells[i], 0, this.cells[i], 0, 16);
            }
            this.tempoBar.setVal(Engine.clampBpm(part.bpm));
            this.bpmField.setText(Integer.toString(part.bpm));
            this.refreshGrid();
            this.setNow(list.size() + " parts loaded");
            return;
        }
        if ("wav".equals(string3) || "mp3".equals(string3)) {
            File dir = new File(this.pulsekitDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            File copy = new File(dir, string.replace(' ', '_'));
            Files.write(copy.toPath(), byArray);
            this.useAudioInput(copy);
            return;
        }
        if ("sf2".equals(string3)) {
            int n2;
            short[][] sArray = AudioIo.parseSf2(byArray);
            for (n2 = 0; n2 < this.voices.length; ++n2) {
                if (sArray[n2] == null) continue;
                this.voices[n2] = sArray[n2];
            }
            this.fillMissingVoices();
            this.reloadDrumBank();
            this.persistSf2(byArray);
            n2 = 0;
            for (short[] sArray2 : sArray) {
                if (sArray2 == null) continue;
                ++n2;
            }
            this.setNow("SoundFont \u00b7 " + n2 + " drums");
            return;
        }
        this.applyMidiBytes(byArray, string);
    }

    private String applyProgramMidi(byte[] data, String name) {
        try {
            Engine.stageSourceMidi(data, name);
            Engine.MidiBars bars = Engine.parseMidiBars(data);
            boolean made = bars != null && this.learnFromSongImport(name, bars, true, "program");
            Engine.clearStagedMidi();
            if (made) {
                String set = this.newestProgramSet();
                this.setNow("Import succeeded: EP " + set);
                return "Import succeeded: EP " + set;
            }
            this.applyMidiBytes(data, name);
            String source = Engine.importSource(name);
            if (source == null || source.length() == 0) source = Engine.stemNameFromMidi(name);
            boolean found = false;
            for (Engine.Learned item : this.learned) {
                if (item != null && source.equals(item.source)) found = true;
            }
            if (!found) return "Import failed: " + name + " has no drum notes Pulsekit can use";
            Engine.rememberFileSetOrigin(source, "program");
            this.persistFileSetInfo();
            this.setNow("Import succeeded: EP " + source);
            return "Import succeeded: EP " + source;
        } catch (Throwable ex) {
            String m = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            return "Import failed: " + m;
        }
    }

    private String newestProgramSet() {
        String name = "";
        for (java.util.Map.Entry<String, String> e : Engine.fileSetOrigins.entrySet()) {
            if ("program".equals(e.getValue())) name = e.getKey();
        }
        return name.length() == 0 ? "program" : name;
    }

    private void applyMidiBytes(byte[] data, String name) {
        String role = Engine.midiRoleFromFile(name);
        if (role == null) {
            Engine.MidiBars bars = Engine.parseMidiBars(data);
            if (bars != null && this.learnFromSongImport(name, bars)) return;
        }
        int[][] parsed = Engine.parseMidi(data);
        if (Engine.hitCount(parsed) < 1) {
            this.setNow("No drum track in that MIDI");
            return;
        }
        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
            System.arraycopy(parsed[t], 0, this.cells[t], 0, Engine.MAX_STEPS);
        }
        int bpm = Engine.parseMidiBpm(data, this.bpm());
        this.tempoBar.setVal(Engine.clampBpm(bpm));
        this.bpmField.setText(Integer.toString(this.bpm()));
        int tn = 4;
        int td = 4;
        Engine.MidiBars meta = Engine.parseMidiBars(data);
        if (meta != null) {
            tn = meta.tsNum;
            td = meta.tsDen;
        }
        this.tsNum = Engine.clampTsNum(tn);
        this.tsDen = Engine.clampTsDen(td);
        this.tsNumField.setText(Integer.toString(this.tsNum));
        this.tsDenField.setText(Integer.toString(this.tsDen));
        this.applySteps(Engine.patternLenFromCells(parsed, this.tsNum, this.tsDen), false);
        Engine.defaultAccents(this.accents, this.steps, Engine.stepsPerBeat(this.tsDen));
        this.showView("pattern");
        this.refreshGrid();
        this.learnFromImport(name, parsed, bpm);
        if (this.nowPlaying.getText() == null || this.nowPlaying.getText().startsWith("MIDI")
            || this.nowPlaying.getText().startsWith("No drum")) {
            this.setNow("MIDI \u00b7 " + name);
        }
    }

    private boolean learnFromSongImport(String filename, Engine.MidiBars bars) {
        return this.learnFromSongImport(filename, bars, true);
    }

    private boolean learnFromSongImport(String filename, Engine.MidiBars bars, boolean force) {
        return this.learnFromSongImport(filename, bars, force, "midi");
    }

    private boolean learnFromSongImport(String filename, Engine.MidiBars bars, boolean force, String origin) {
        List<Engine.MidiSeg> segs = Engine.segmentMidiBars(bars.bars);
        if (segs.isEmpty()) return false;
        java.util.LinkedHashSet<String> uniqueG = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> uniqueF = new java.util.LinkedHashSet<>();
        int nFillern = 0;
        for (Engine.MidiSeg s : segs) {
            uniqueG.add(Engine.patternSignature(s.groove));
            if (s.fill != null) uniqueF.add(Engine.patternSignature(s.fill));
            if ("fillern".equals(s.kind)) nFillern++;
        }
        boolean multi = bars.bars.size() >= 3 && (segs.size() > 1 || uniqueG.size() > 1 || uniqueF.size() > 1
            || (nFillern > 0 && bars.bars.size() >= 4));
        if (!force && !multi) return false;

        String stem = Engine.uniqueImportSource(Engine.stemNameFromMidi(filename), this.learned, this.learnedFills);
        Engine.FileSet madeSet = null;
        try {
            if (!Engine.isFileSetOrigin(origin)) origin = "midi";
            Engine.FileSet set = AudioIo.fileSetFromMidi(bars, stem, origin);
            Engine.attachStagedMidi(set);
            if (set.patterns.isEmpty() && set.fills.isEmpty()) return false;
            madeSet = set;
            this.rememberFileSetParts(stem, set.parts);
            this.loadFset(Engine.encodeFset(set), Engine.fsetFilename(set.name));
        } catch (Exception ex) {
            this.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not build a file set");
            return false;
        }

        // The song from the file set: parts are named after its patterns and fills (Pattern 1, Fill 1, ...).
        List<Engine.Part> parts = new ArrayList<>(Engine.songFromFileSet(madeSet));
        if (!parts.isEmpty()) {
            int ans = JOptionPane.showConfirmDialog(
                this,
                this.nowPlaying.getText() + "\n\nMake a song from these? It goes under Imported. Your original song stays.",
                "Make a song?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (ans == JOptionPane.YES_OPTION) this.addImportedSong(stem, parts);
        }
        return true;
    }

    private void tryDub() {
        this.installPlugin(Engine.examplePlugin(), true);
    }

    private void addPluginStyle(Engine.Style st) {
        this.styles.put(st.id, st);
        this.refreshLearnedChips();
    }

    private String selectedFileSource() {
        for (Engine.Learned x : this.learned) {
            if (x.id.equals(this.style) || x.id.equals(this.learnedId())) return Engine.sourceOf(x);
        }
        if (this.fillId != null && this.fillId.startsWith("l:")) {
            String id = this.fillId.substring(2);
            for (Engine.LearnedFill x : this.learnedFills) if (id.equals(x.id)) return Engine.sourceOf(x);
        }
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<String>();
        keys.addAll(this.learnedSources());
        keys.addAll(this.fillSources());
        if (keys.isEmpty()) return null;
        return keys.iterator().next();
    }

    private void saveFset(String source) {
        String src = source;
        if (src == null) src = this.selectedFileSource();
        if (src == null) {
            this.setNow("Pick an imported file set first");
            return;
        }
        String label = src.isEmpty() ? "Other" : src;
        Engine.FileSet set = Engine.collectFset(src, label, this.learned, this.learnedFills, this.fillernPairs);
        if (set == null) {
            this.setNow("That file set is empty");
            return;
        }
        java.util.List<Engine.FileSetPart> stored = this.fileSetParts.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        try {
            this.saveBytes(Engine.fsetFilename(set.name), "Pulsekit file set", "fset", Engine.encodeFset(set));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save .fset: " + ex.getMessage());
        }
    }

    private void loadFset(byte[] data, String filename) throws Exception {
        this.loadFset(data, filename, false);
    }

    private void loadFset(byte[] data, String filename, boolean keepView) throws Exception {
        Engine.FileSet set = Engine.decodeFset(data);
        String source = Engine.importSource(filename);
        if (source == null || source.isEmpty() || "Import".equals(source)) source = set.name;
        source = Engine.uniqueImportSource(source, this.learned, this.learnedFills);
        java.util.LinkedHashMap<String, String> patternIds = new java.util.LinkedHashMap<String, String>();
        java.util.ArrayList<Engine.LearnedFill> importedFills = new java.util.ArrayList<Engine.LearnedFill>();
        String firstId = null;
        int at = 0;
        for (Engine.Learned p : set.patterns) {
            Engine.Learned item = new Engine.Learned();
            item.id = Engine.newLearnedId();
            item.name = Engine.uniqueLearnedName(p.name, Engine.learnedFrom(this.learned, source));
            item.bpm = p.bpm;
            item.closest = p.closest;
            item.cells = Engine.copyCells(p.cells);
            item.source = source;
            this.learned.add(at++, item);  // keep the file set's order: first part first
            while (this.learned.size() > Engine.MAX_LEARNED) this.learned.remove(this.learned.size() - 1);
            this.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
            patternIds.put(p.name, item.id);
            if (firstId == null) firstId = item.id;
        }
        int fat = 0;
        for (Engine.LearnedFill f : set.fills) {
            Engine.LearnedFill item = new Engine.LearnedFill();
            item.id = Engine.newLearnedId();
            item.name = Engine.uniqueFillName(f.name == null || f.name.isEmpty() ? "fill" : f.name, Engine.fillsFrom(this.learnedFills, source));
            item.kind = f.kind == null ? "toms" : f.kind;
            item.cells = Engine.copyCells(f.cells);
            item.source = source;
            this.learnedFills.add(fat++, item);
            while (this.learnedFills.size() > Engine.MAX_LEARNED) this.learnedFills.remove(this.learnedFills.size() - 1);
            importedFills.add(item);
        }
        int nPair = 0;
        for (String[] row : set.fillerns) {
            String pid = patternIds.get(row[0]);
            if (pid == null) continue;
            String fk = Engine.resolveFsetFillKey(row[1], importedFills);
            this.fillernPairs.put("l:" + pid, fk);
            nPair++;
        }
        // Songs saved in the file set: kept with it, and listed under Imported songs.
        for (Engine.FileSetSong song : set.songs) {
            Engine.putFileSetSong(source, song);
            this.addFileSetSong(source, song);
        }
        if (!set.songs.isEmpty()) this.persistFileSetInfo();
        if (firstId != null) this.loadLearned(firstId);
        else if (!importedFills.isEmpty()) this.loadLearnedFill(importedFills.get(0).id);
        if (Engine.isFileSetOrigin(set.origin)) Engine.rememberFileSetOrigin(source, set.origin);
        if (set.sourceWav != null || set.combinedWav != null) {
            Engine.rememberFileSetAudio(source, set.sourceWav, set.combinedWav, set.combinedName);
            this.storeAudioDir();
        }
        this.refreshLearnedChips();
        this.persistLearned();
        java.util.List<Engine.FileSetPart> info = set.parts;
        if (info == null || info.isEmpty()) info = Engine.partsForDisplay(set);
        this.rememberFileSetParts(source, info);
        if (!keepView) {
            this.showView(nPair > 0 ? "combo" : firstId != null ? "pattern" : "fills");
        }
        this.setNow("File set \u00b7 " + Engine.fileSetMarked(source, set.origin) + " \u00b7 " + set.patterns.size() + " patterns \u00b7 " + nPair + " Fillerns \u00b7 " + set.fills.size() + " fills");
    }

    private void savePkp() {
        try {
            String name = this.styles.containsKey(this.style) ? this.styles.get(this.style).label : "Pulsekit";
            String py = this.pyEditor != null ? this.pyEditor.getText() : "";
            byte[] pkp = Engine.encodePkp(
                "pulsekit." + this.style,
                name,
                this.bpm(),
                this.style,
                this.cells,
                this.fillPat,
                this.pyName,
                py);
            this.saveBytes(this.exportName("pkp"), "Pulsekit plugin", "pkp", pkp);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save .pkp: " + ex.getMessage());
        }
    }

    private void saveSongMidi() {
        List<Engine.Part> parts = this.activeSong().isEmpty()
            ? Collections.singletonList(Engine.groove(this.exportName("mid").replace(".mid", ""), this.bpm(), this.cells, 1))
            : this.activeSong();
        this.saveBytes(Engine.songFilename(parts, this.songFileSet(parts)).replace(".sng", ".mid"), "Song MIDI", "mid", Engine.encodeSongMidi(parts));
    }

    private void saveJar() {
        try {
            this.saveBytes(this.exportName("jar"), "Kit snapshot", "jar",
                Engine.encodeKitJar(Engine.encodeMidi(this.cells, this.bpm(), this.steps, this.tsNum, this.tsDen), this.cells, this.bpm(), this.style));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save JAR: " + ex.getMessage());
        }
    }

    static final class RangeBar
    extends JComponent {
        private final int min;
        private final int max;
        private int value;
        private final IntConsumer on;
        Runnable onExtra;

        RangeBar(int n, int n2, int n3, IntConsumer intConsumer) {
            this.min = n;
            this.max = n2;
            this.value = n3;
            this.on = intConsumer;
            this.setOpaque(false);
            this.setPreferredSize(new Dimension(120, 28));
            this.addMouseListener(new MouseAdapter(){

                @Override
                public void mousePressed(MouseEvent mouseEvent) {
                    RangeBar.this.setFrom(mouseEvent.getX());
                }
            });
            this.addMouseMotionListener(new MouseAdapter(){

                @Override
                public void mouseDragged(MouseEvent mouseEvent) {
                    RangeBar.this.setFrom(mouseEvent.getX());
                }
            });
        }

        int getVal() {
            return this.value;
        }

        void setVal(int n) {
            this.value = Engine.clamp(n, this.min, this.max);
            if (this.onExtra != null) this.onExtra.run();
            this.repaint();
        }

        private void setFrom(int n) {
            int n2 = 8;
            int n3 = Math.max(n2 + 1, this.getWidth() - 8);
            float f = (float)(n - n2) / (float)(n3 - n2);
            int n4 = Math.round((float)this.min + (f = Math.max(0.0f, Math.min(1.0f, f))) * (float)(this.max - this.min));
            if (n4 != this.value) {
                this.value = n4;
                this.on.accept(n4);
                if (this.onExtra != null) {
                    this.onExtra.run();
                }
                this.repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D)graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float f = 8.0f;
            float f2 = this.getWidth() - 8;
            float f3 = (float)this.getHeight() / 2.0f;
            float f4 = this.max == this.min ? 0.0f : (float)(this.value - this.min) / (float)(this.max - this.min);
            float f5 = f + f4 * (f2 - f);
            graphics2D.setColor(BORDER);
            graphics2D.setStroke(new BasicStroke(3.0f, 1, 1));
            graphics2D.drawLine((int)f, (int)f3, (int)f2, (int)f3);
            graphics2D.setColor(HIT);
            graphics2D.drawLine((int)f, (int)f3, (int)f5, (int)f3);
            graphics2D.setColor(FG);
            graphics2D.fill(new Ellipse2D.Float(f5 - 8.0f, f3 - 8.0f, 16.0f, 16.0f));
            graphics2D.dispose();
        }
    }

    /** Wrapping chip row that reports a stable height so BoxLayout cannot loop. */
    private static final class ChipStrip extends JPanel {
        ChipStrip() {
            super(new FlowLayout(FlowLayout.LEFT, 6, 6));
            setOpaque(false);
            setAlignmentX(0f);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = 720;
            Container p = getParent();
            if (p != null && p.getWidth() > 0) w = p.getWidth();
            else if (getWidth() > 0) w = getWidth();
            int inner = Math.max(64, w);
            int x = 0;
            int y = 0;
            int rowH = 0;
            int n = 0;
            for (Component c : getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                if (x > 0 && x + d.width > inner) {
                    y += rowH + 6;
                    x = 0;
                    rowH = 0;
                }
                x += d.width + 6;
                rowH = Math.max(rowH, d.height);
                n++;
            }
            if (n == 0) return new Dimension(w, 12);
            return new Dimension(w, y + rowH + 8);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension d = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, d.height);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(80, 28);
        }
    }
}
