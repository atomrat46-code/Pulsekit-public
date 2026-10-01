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

    /** File > Drum Midi Settings: how a MIDI drum track becomes a file set on import. */
    JPanel buildDrumMidiPage() {
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
