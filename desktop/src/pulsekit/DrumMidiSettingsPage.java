package pulsekit;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** File > Drum Midi Settings: how a MIDI drum track becomes a file set on import. */
final class DrumMidiSettingsPage {
    final Pulsekit app;

    DrumMidiSettingsPage(Pulsekit app) {
        this.app = app;
    }

    /** Save MidiDrumGen output file into DB: its own setting, which Reset to defaults leaves alone. */
    static boolean genToDb;
    /** Save SogniMusic output file into DB: its own setting too. */
    static boolean musicToDb;

    /** The two Save-into-DB settings, kept apart from the import settings. */
    File dbSettingsFile() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "db-settings.txt");
    }

    void loadDbSettings() {
        genToDb = false;
        musicToDb = false;
        try {
            File f = this.dbSettingsFile();
            if (!f.isFile()) return;
            for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                if (line.trim().equals("midiDrumGenToDb=1")) genToDb = true;
                if (line.trim().equals("sogniMusicToDb=1")) musicToDb = true;
            }
        } catch (Exception ignored) {
            // both off
        }
    }

    void saveDbSettings() {
        try {
            File f = this.dbSettingsFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), ("midiDrumGenToDb=" + (genToDb ? 1 : 0) + "\nsogniMusicToDb=" + (musicToDb ? 1 : 0) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // the settings stay for this session
        }
    }

    File drumMidiFile() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "drum-midi-settings.txt");
    }

    void saveDrumMidi() {
        try {
            File f = this.drumMidiFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), MidiImportSettings.encode().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // settings stay for this session
        }
    }

    JCheckBox drumMidiCheck(JPanel col, String label, String note, boolean on, java.util.function.Consumer<Boolean> set) {
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

    JLabel keyStatus;

    /**
     * Sogni API key file: chosen once, kept in ~/.pulsekit (owner only), and passed as --key_file to
     * programs that take one (SogniMusic). Reset to defaults leaves it alone.
     */
    void addKeyGroup(JPanel col) {
        col.add(Box.createVerticalStrut(18));
        JLabel head = new JLabel("Sogni API key file");
        head.setFont(new Font("SansSerif", Font.BOLD, 15));
        head.setForeground(FG);
        head.setAlignmentX(0.0f);
        col.add(head);
        JLabel note = new JLabel("<html><body style='width:480px'>A text file with SOGNI_API_KEY=&lt;your key&gt; (or the key alone). "
            + "Pulsekit keeps a private copy and gives it to every program that takes --key_file, such as SogniMusic.</body></html>");
        note.setForeground(MUTED);
        note.setAlignmentX(0.0f);
        col.add(note);
        this.keyStatus = new JLabel(ApiKeys.status());
        this.keyStatus.setName("sogni-key-status");
        this.keyStatus.setForeground(FG);
        this.keyStatus.setAlignmentX(0.0f);
        col.add(this.keyStatus);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        JButton choose = app.outline("Choose file", false);
        choose.setName("sogni-key-choose");
        choose.addActionListener(e -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            if (chooser.showOpenDialog(app) != javax.swing.JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
            this.takeKey(chooser.getSelectedFile());
        });
        JButton clear = app.outline("Clear", false);
        clear.setName("sogni-key-clear");
        clear.addActionListener(e -> {
            ApiKeys.clear();
            this.keyStatus.setText(ApiKeys.status());
            app.setNow("Sogni key cleared");
        });
        row.add(choose);
        row.add(clear);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        col.add(row);
    }

    JLabel folderStatus;

    /**
     * Program files folder: where PyJav keeps what programs make (SogniChat's replies and results,
     * SogniMusic's tracks). ~/Downloads unless a folder is chosen.
     */
    void addFolderGroup(JPanel col) {
        col.add(Box.createVerticalStrut(18));
        JLabel head = new JLabel("Program files folder");
        head.setFont(new Font("SansSerif", Font.BOLD, 15));
        head.setForeground(FG);
        head.setAlignmentX(0.0f);
        col.add(head);
        JLabel note = new JLabel("<html><body style='width:480px'>Where PyJav keeps the files programs make: SogniChat's replies, "
            + "pictures, audio and video, SogniMusic's tracks, CutWav's cuts. Downloads unless you choose a folder.</body></html>");
        note.setForeground(MUTED);
        note.setAlignmentX(0.0f);
        col.add(note);
        this.folderStatus = new JLabel(ProgramFolder.label());
        this.folderStatus.setName("program-folder-status");
        this.folderStatus.setForeground(FG);
        this.folderStatus.setAlignmentX(0.0f);
        col.add(this.folderStatus);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        JButton choose = app.outline("Choose folder", false);
        choose.setName("program-folder-choose");
        choose.addActionListener(e -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(ProgramFolder.get());
            chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(app) != javax.swing.JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
            this.takeFolder(chooser.getSelectedFile());
        });
        JButton downloads = app.outline("Use Downloads", false);
        downloads.setName("program-folder-clear");
        downloads.addActionListener(e -> {
            ProgramFolder.clear();
            this.folderStatus.setText(ProgramFolder.label());
            app.setNow("Program files go to Downloads");
        });
        row.add(choose);
        row.add(downloads);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        col.add(row);
    }

    /** The chosen folder: program files go there from now on. */
    void takeFolder(File dir) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("it is not a folder");
            ProgramFolder.set(dir.getAbsolutePath());
            app.setNow("Program files go to " + ProgramFolder.label());
        } catch (Exception ex) {
            app.setNow("Could not use that folder" + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
        if (this.folderStatus != null) this.folderStatus.setText(ProgramFolder.label());
    }

    /** The chosen file: its key is kept, or the status says why not. */
    void takeKey(File file) {
        try {
            String masked = ApiKeys.store(Files.readAllBytes(file.toPath()));
            app.setNow("Sogni key set (ends " + masked + ")");
        } catch (Exception ex) {
            app.setNow(ex.getMessage() == null ? "Could not read that file" : ex.getMessage());
        }
        if (this.keyStatus != null) this.keyStatus.setText(ApiKeys.status());
    }

    /** File > Drum Midi Settings: how a MIDI drum track becomes a file set on import. */
    JPanel buildDrumMidiPage() {
        try {
            File f = this.drumMidiFile();
            MidiImportSettings.decode(f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null);
        } catch (Exception ex) {
            MidiImportSettings.reset();
        }
        ApiKeys.init(new File(System.getProperty("user.home", "."), ".pulsekit"));
        ProgramFolder.init(new File(System.getProperty("user.home", "."), ".pulsekit"));
        PictureCopies.shrinker = new PictureShrink();
        SogniHistory.init(new File(System.getProperty("user.home", "."), ".pulsekit"));
        this.loadDbSettings();
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
        JButton minus = app.chip("\u2212", false);
        JButton plus = app.chip("+", false);
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
                if ("combo".equals(app.view)) app.styleLibrary.refreshLearnedChips();
            });
            fdGroup.add(r);
            fdRadios[i] = r;
            col.add(r);
        }
        this.addKeyGroup(col);
        this.addFolderGroup(col);
        JCheckBox gen = this.drumMidiCheck(col, "Save MidiDrumGen output file into DB",
            "When MidiDrumGen finishes, its MIDI goes into the prompt library (Music, prompt MidiDrumGen) as a result file, "
                + "with the arguments it ran with. Result files on the Prompts page plays it.",
            genToDb, on -> {
                genToDb = on;
                this.saveDbSettings();
            });
        gen.setName("midi-drum-gen-db");
        JCheckBox music = this.drumMidiCheck(col, "Save SogniMusic output file into DB",
            "When SogniMusic finishes, its track goes into the prompt library (Music, prompt SogniMusic) as a result file, "
                + "with the arguments it ran with and its workflow id. Result files on the Prompts page plays it.",
            musicToDb, on -> {
                musicToDb = on;
                this.saveDbSettings();
            });
        music.setName("sogni-music-db");
        col.add(Box.createVerticalStrut(16));
        JButton reset = app.outline("Reset to defaults", false);
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
}
