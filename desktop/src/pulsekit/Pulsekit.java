package pulsekit;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import pulsekit.AudioIo;
import pulsekit.Engine;

public final class Pulsekit extends UiKit {
    final Map<String, Engine.Style> styles = Engine.styles();

    final int[][] cells = Engine.emptyCells();

    final int[][] fillPat = Engine.fillCells("toms");

    final int[][] lens = Engine.emptyCells();

    final int[][] fillLens = Engine.emptyCells();

    final boolean[] accents = new boolean[Engine.MAX_STEPS];

    final boolean[] mutes = new boolean[Engine.TRACK_ID.length];

    final List<JButton> tabs = new ArrayList<JButton>();

    final JPanel styleHost = new JPanel();

    final JPanel styleBar = new ChipStrip();

    final JPanel importedBar = new JPanel();

    final JPanel variatedPatternBar = new ChipStrip();

    final JPanel fillHost = new JPanel();

    final JPanel fillBar = new ChipStrip();

    final JPanel variatedFillBar = new ChipStrip();

    final JPanel importedFillBar = new JPanel();

    final JPanel toolsRow = new JPanel(new FlowLayout(0, 6, 4));

    final JPanel knobsRow = new JPanel(new GridLayout(1, 4, 12, 0));

    final JTextField bpmField = new JTextField("124", 3);

    final JTextField tsNumField = new JTextField("4", 2);

    final JTextField tsDenField = new JTextField("4", 2);

    final JLabel nowPlaying = new JLabel(" ");

    final RangeBar tempoBar = new RangeBar(40, 240, 124, n -> {
        if (this.bpmField != null && !Integer.toString(n).equals(this.bpmField.getText())) {
            this.bpmField.setText(Integer.toString(n));
        }
        this.gridEditor.onTempo(n);
    });

    final RangeBar swingBar = new RangeBar(0, 75, 12, n -> {});

    final RangeBar densBar = new RangeBar(1, 10, 5, n -> {});

    final RangeBar humanBar = new RangeBar(0, 100, 18, n -> {});

    final List<Engine.Part> song = new ArrayList<Engine.Part>();

    final List<Engine.ImportedSong> importedSongs = new ArrayList<Engine.ImportedSong>();

    String songLane = "original";

    String importedSongId;

    final CardLayout pages = new CardLayout();

    final JPanel pageHost = new JPanel(this.pages);

    JPanel chrome;

    JButton playBtn;

    JButton muteBtn;

    JButton fillLastBtn;

    JButton barsBtn;

    JButton stepsBtn;

    JPanel gridHost;

    JLabel infoStatus;

    final java.util.LinkedHashMap<String, java.util.List<Engine.FileSetPart>> fileSetParts = new java.util.LinkedHashMap<String, java.util.List<Engine.FileSetPart>>();

    final List<Engine.DrumSet> drumSets = new ArrayList<Engine.DrumSet>();

    String style = "house";

    /** False until a pattern is chosen: the startup House pattern is not shown as selected. */
    boolean styleChosen;
    /** The time signature was set by picking a style in another meter (Ballad 6/8), so a 4/4 style sets 4/4 back. */
    boolean tsFromStyle;

    String view = "pattern";

    String fillId = "toms";

    String songMode = "edit";

    String pyName = "drum_midi.py";

    byte[] pyBytes;

    JTextArea pyEditor;

    JTextArea pyLog;

    String pyInputPath;

    Sequencer sequencer;

    int playhead = -1;

    int songPart = -1;

    int bars = 4;

    int steps = 16;

    int tsNum = 4;

    int tsDen = 4;

    boolean songPlay;

    boolean muted;

    boolean livePads;

    boolean fillLast;

    boolean fillVariated;

    boolean pluginGhostHats;

    final List<Engine.Plugin> plugins = new ArrayList<Engine.Plugin>();

    final List<Engine.Learned> learned = new ArrayList<Engine.Learned>();

    final List<Engine.Learned> variatedPatterns = new ArrayList<Engine.Learned>();

    final List<Engine.LearnedFill> learnedFills = new ArrayList<Engine.LearnedFill>();

    final List<Engine.LearnedFill> variatedFills = new ArrayList<Engine.LearnedFill>();

    final List<String> hiddenStyles = new ArrayList<String>();

    final List<String> hiddenFills = new ArrayList<String>();

    final Map<String, String> fillernPairs = new LinkedHashMap<String, String>();

    /** Patterns whose Fillern fill was chosen from the list. Only these are underlined. */
    final java.util.Set<String> fillernPicked = new java.util.LinkedHashSet<String>();

    /** Fillern type per pattern: Engine.FILLERN_AFTER (default), FILLERN_END or FILLERN_START. */
    final Map<String, String> fillernModes = new LinkedHashMap<String, String>();

    /** The groove tab the imported chips were last built for. */
    String importedFor;

    final Map<String, Boolean> openPacks = new LinkedHashMap<String, Boolean>();

    JPanel importedFileList;

    JCheckBox autosaveBox;

    boolean autosaveOn;

    final short[][] voices = AudioIo.buildVoices(22050);

    Pulsekit() {
        super("Pulsekit");
        this.drumSets.add(Engine.DrumSet.originalSet());
        for (int i = 0; i < Engine.MAX_STEPS; ++i) {
            this.accents[i] = i % 4 == 0;
        }
        this.styleLibrary.loadStyle("house", false);
        this.buildUi();
        this.playback.openMidi();
        this.persistence.restoreAutosave();
        this.persistence.restoreSessionFiles();
        this.styleChosen = false;
        this.styleLibrary.refreshStyles();
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (Pulsekit.this.autosaveOn) Pulsekit.this.persistence.writeAutosave(false);
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

    int bpm() {
        return this.tempoBar.getVal();
    }

    void buildUi() {
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
        this.gridEditor.styleNumField(this.bpmField, 18);
        this.bpmField.setHorizontalAlignment(4);
        this.gridEditor.bindNumField(this.bpmField, 40, 240, n -> {
            this.tempoBar.setVal(n);
            this.gridEditor.onTempo(this.bpm());
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
        this.gridEditor.styleNumField(this.tsNumField, 14);
        this.gridEditor.styleNumField(this.tsDenField, 14);
        this.tsNumField.setHorizontalAlignment(4);
        this.tsDenField.setHorizontalAlignment(2);
        this.gridEditor.bindNumField(this.tsNumField, 1, 16, n -> {
            if (n != this.tsNum) this.tsFromStyle = false;
            this.gridEditor.applyTimeSig(n, this.tsDen);
        });
        this.gridEditor.bindNumField(this.tsDenField, 2, 16, n -> {
            if (n != this.tsDen) this.tsFromStyle = false;
            this.gridEditor.applyTimeSig(this.tsNum, n);
        });
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
            this.playback.applyMute();
        });
        jPanel4.add(this.muteBtn);
        this.autosaveBox = new JCheckBox("Autosave");
        this.autosaveBox.setOpaque(false);
        this.autosaveBox.setForeground(MUTED);
        this.autosaveBox.setFocusPainted(false);
        this.autosaveBox.setFont(new Font("SansSerif", 1, 12));
        this.autosaveBox.setToolTipText("Save the full project every few seconds to " + this.persistence.autosaveFile().getAbsolutePath());
        this.autosaveBox.addActionListener(e -> this.persistence.setAutosave(this.autosaveBox.isSelected(), true));
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
            jButton.addActionListener(actionEvent -> this.styleLibrary.loadStyle(sid, false));
            jButton.putClientProperty("style", sid);
            this.onChipMenu(jButton, () -> this.styleLibrary.builtinStyleMenu(sid));
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
            jButton.addActionListener(actionEvent -> this.styleLibrary.applyFill(string));
            final JButton fillChip = jButton;
            this.onRightClick(jButton, () -> this.styleLibrary.builtinFillMenu(fillChip, string));
            this.fillBar.add(jButton);
        }
        JButton jButton2 = this.chip("Variate", false);
        jButton2.addActionListener(actionEvent -> this.styleLibrary.variateFill());
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
        this.stepsBtn.addActionListener(actionEvent -> this.gridEditor.toggleSteps());
        this.barsBtn = this.outline("4 bars out", false);
        this.barsBtn.addActionListener(actionEvent -> {
            this.bars = this.bars == 4 ? 8 : (this.bars == 8 ? 2 : 4);
            this.barsBtn.setText(this.bars + " bars out");
        });
        this.toolsRow.add(this.barsBtn);
        JButton varyBtn = this.outline("Variate", false);
        varyBtn.addActionListener(actionEvent -> this.styleLibrary.variatePattern("random"));
        this.onChipMenu(varyBtn, () -> this.styleLibrary.variatePatternMenu());
        this.toolsRow.add(varyBtn);
        jButton = this.outline("Silent", false);
        jButton.addActionListener(actionEvent -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < this.steps; ++j) {
                    this.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(this.lens);
            this.styleLibrary.syncBuiltinFill();
            this.gridEditor.refreshGrid();
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
        this.gridHost.add((Component)this.gridEditor.buildGrid(), "Center");
        this.pageHost.add((Component)this.gridHost, "grid");
        this.pageHost.add((Component)this.playback.buildPads(), "pads");
        this.pageHost.add((Component)this.songEditor.buildSongPage(), "song");
        this.pageHost.add((Component)this.pyJav.buildPyPage(), "py");
        this.pageHost.add((Component)this.projectIo.buildImportPage(), "import");
        this.pageHost.add((Component)this.projectIo.buildExportPage(), "export");
        this.pageHost.add((Component)this.promptsPage.buildPromptsPage(), "prompts");
        this.pageHost.add((Component)this.fileSets.buildInfoPage(), "fsetinfo");
        this.pageHost.add((Component)this.helpPage.buildHelpPage(), "help");
        this.pageHost.add((Component)this.drumMidiSettings.buildDrumMidiPage(), "midisettings");
        this.pageHost.add((Component)this.compareHits.buildComparePage(), "comparehits");
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
        jButton4.addActionListener(actionEvent -> this.playback.stop());
        this.playBtn = this.action("Play", FG, BG);
        this.playBtn.setPreferredSize(new Dimension(240, 48));
        this.playBtn.addActionListener(actionEvent -> this.playback.togglePlay());
        JButton jButton5 = this.action("Gen", HIT, BG);
        jButton5.setPreferredSize(new Dimension(72, 48));
        jButton5.addActionListener(actionEvent -> this.playback.generate());
        jPanel11.add(jButton4);
        jPanel11.add(Box.createHorizontalStrut(8));
        jPanel11.add(this.playBtn);
        jPanel11.add(Box.createHorizontalStrut(8));
        jPanel11.add(jButton5);
        jPanel11.add(Box.createHorizontalGlue());
        jPanel10.add(jPanel11);
        jPanel.add((Component)jPanel10, "South");
        this.setContentPane(jPanel);
        this.styleLibrary.refreshStyles();
        this.styleLibrary.refreshFills();
        this.refreshTabs();
    }

    JPanel knobBox(String string, RangeBar rangeBar, Supplier<String> supplier) {
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

    void addTab(JPanel jPanel, String string, String string2) {
        JButton jButton = new JButton(string2);
        this.flatten(jButton);
        jButton.setFont(new Font("SansSerif", 1, 13));
        jButton.putClientProperty("tab", string);
        jButton.addActionListener(actionEvent -> this.showView(string));
        jPanel.add(jButton);
        this.tabs.add(jButton);
    }

    void addFileTab(JPanel jPanel) {
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
            // PyJav's code editor is saved from here, not from a right click in it.
            boolean codeSavable = this.codeSave.canSave();
            JMenuItem saveCode = new JMenuItem("Save code");
            saveCode.setEnabled(codeSavable);
            saveCode.addActionListener(e -> this.codeSave.save());
            JMenuItem saveCodeAs = new JMenuItem("Save code as");
            saveCodeAs.setEnabled(codeSavable);
            saveCodeAs.addActionListener(e -> this.codeSave.saveAs());
            // A file into the prompt library on its own: a reference file (Ref files, Browse DB) or a result file.
            JMenuItem importRef = new JMenuItem("Import as Ref file");
            importRef.addActionListener(e -> this.promptDb.importFile(false));
            JMenuItem importResult = new JMenuItem("Import as Result file");
            importResult.addActionListener(e -> this.promptDb.importFile(true));
            JMenuItem midi = new JMenuItem("Drum Midi Settings");
            midi.addActionListener(e -> this.showView("midisettings"));
            JMenuItem compare = new JMenuItem("Compare Hits");
            compare.addActionListener(e -> this.showView("comparehits"));
            JMenuItem help = new JMenuItem("Help-Desktop");
            help.addActionListener(e -> this.showView("help"));
            menu.add(imp);
            menu.add(exp);
            menu.add(importRef);
            menu.add(importResult);
            menu.add(saveCode);
            menu.add(saveCodeAs);
            menu.add(midi);
            menu.add(compare);
            menu.add(help);
            menu.show(jButton, 0, jButton.getHeight());
        });
        jPanel.add(jButton);
        this.tabs.add(jButton);
    }

    static byte[] headOf(File file) {
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

    static String hintHtml(String status) {
        String safe = status == null ? "" : status.replace("&", "&" + "amp;").replace("<", "&" + "lt;").replace(">", "&" + "gt;");
        return "<html><body style='width:520px;color:#F4F1EA'>" + safe.replace("\n", "<br>") + "</body></html>";
    }

    static boolean isJavaName(String name) {
        String n = name == null ? "" : name.toLowerCase();
        return n.endsWith(".java") || n.endsWith(".jar") || n.endsWith(".class");
    }

    static PythonRun.Result asPython(JavaRun.Result r) {
        java.util.ArrayList<PythonRun.FileOut> files = new java.util.ArrayList<PythonRun.FileOut>();
        if (r != null && r.files != null) {
            for (JavaRun.FileOut f : r.files) files.add(new PythonRun.FileOut(f.name, f.bytes));
        }
        return new PythonRun.Result(r == null ? "" : r.log, files, r == null ? 1 : r.code);
    }


    String listedSource;

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
    final PromptsPage promptsPage = new PromptsPage(this);

    final PromptDb promptDb = new PromptDb(this);


    void showView(String string) {
        this.view = string;
        if (("pattern".equals(string) || "combo".equals(string)) && !string.equals(this.importedFor)) this.styleLibrary.refreshLearnedChips();
        boolean bl = "fills".equals(string);
        boolean combo = "combo".equals(string);
        boolean pattern = "pattern".equals(string);
        boolean groove = pattern || combo;
        boolean bl2 = "song".equals(string);
        boolean bl3 = "py".equals(string);
        boolean bl4 = "import".equals(string) || "export".equals(string) || "fsetinfo".equals(string) || "help".equals(string) || "midisettings".equals(string) || "comparehits".equals(string);
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
            this.songEditor.refreshSong();
        }
        if (pattern) {
            this.fillLast = false;
            this.setNow(null);
            this.styleLibrary.refreshStyles();
        }
        if (combo) {
            this.fillLast = true;
            String fk = this.fillernPairs.get(this.styleLibrary.currentPatternKey());
            this.styleLibrary.applyFill(fk != null ? fk : this.fillId);
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
            this.setNow("Last bar \u00b7 " + this.styleLibrary.fillLabel(this.fillId));
            this.styleLibrary.refreshStyles();
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
            this.setNow(this.styleLibrary.fillLabel(this.fillId));
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
        if ("comparehits".equals(string)) {
            this.compareHits.refresh();
            this.setNow("Compare Hits");
        }
        if ("import".equals(string)) {
            this.setNow("Choose a MIDI, song, WAV or SoundFont");
        }
        if ("fsetinfo".equals(string)) {
            this.setNow(this.infoStatus != null ? this.infoStatus.getText() : "File set");
        }
        if ("export".equals(string)) {
            this.setNow(this.projectIo.exportName("mid").replace(".mid", ""));
        }
        this.refreshTabs();
        this.styleLibrary.refreshFills();
        if (pattern || combo || bl) this.gridEditor.refreshGrid();
    }

    void setNow(String string) {
        boolean bl = string != null && !string.trim().isEmpty();
        this.nowPlaying.setText(bl ? string : " ");
        this.nowPlaying.setVisible(bl);
    }

    void refreshTabs() {
        Iterator<JButton> iterator = this.tabs.iterator();
        while (iterator.hasNext()) {
            JButton jButton = iterator.next();
            String tab = String.valueOf(jButton.getClientProperty("tab"));
            boolean bl = this.view.equals(tab)
                || ("file".equals(tab) && ("import".equals(this.view) || "export".equals(this.view) || "help".equals(this.view) || "midisettings".equals(this.view) || "comparehits".equals(this.view) || "fsetinfo".equals(this.view)));
            jButton.setBackground(bl ? ELEV : BG);
            jButton.setForeground(bl ? FG : MUTED);
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
    static final class ChipStrip extends JPanel {
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
