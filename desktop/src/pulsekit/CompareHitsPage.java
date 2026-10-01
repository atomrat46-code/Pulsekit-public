package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import static pulsekit.Pulsekit.*;

/**
 * File > Compare Hits: how closely a file set's drum hits line up. The song against the file
 * set's source MIDI (what the import changed), and both against the original WAV (what DrumMidi
 * heard). The WAV is the one kept with the file set, the last WAV given to PyJav, or one picked here.
 */
final class CompareHitsPage {
    final Pulsekit app;
    /** Chosen file set label, and the source key that holds its MIDI. */
    String source;
    String midiKey;
    File pickedWav;
    JPanel setsBox;
    JLabel wavLabel;
    JTextArea result;
    boolean running;

    CompareHitsPage(Pulsekit app) {
        this.app = app;
    }

    JPanel buildComparePage() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(4, 4, 16, 4));
        JLabel title = new JLabel("Compare Hits");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(FG);
        title.setAlignmentX(0.0f);
        col.add(title);
        col.add(Box.createVerticalStrut(6));
        col.add(this.note("How closely the drum hits line up, per drum family. The song against the file set's source MIDI "
            + "shows what the import changed; the MIDI against the original WAV shows what DrumMidi heard. "
            + "Hit times are compared, not sound."));
        col.add(this.heading("File set"));
        this.setsBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        this.setsBox.setOpaque(false);
        this.setsBox.setAlignmentX(0.0f);
        col.add(this.setsBox);
        col.add(this.heading("Original WAV"));
        this.wavLabel = new JLabel();
        this.wavLabel.setForeground(MUTED);
        this.wavLabel.setAlignmentX(0.0f);
        col.add(this.wavLabel);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        buttons.setOpaque(false);
        buttons.setAlignmentX(0.0f);
        JButton pick = app.outline("Pick WAV", false);
        pick.addActionListener(e -> this.pickWav());
        JButton run = app.outline("Compare", true);
        run.addActionListener(e -> this.compare());
        buttons.add(pick);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(run);
        col.add(buttons);
        this.result = new JTextArea(14, 64);
        this.result.setEditable(false);
        this.result.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        this.result.setForeground(FG);
        this.result.setBackground(SURFACE);
        this.result.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.result.setAlignmentX(0.0f);
        col.add(this.result);
        col.add(Box.createVerticalStrut(10));
        col.add(this.note(HitCompare.LEGEND));
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JScrollPane scroll = new JScrollPane(col);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    JLabel heading(String s) {
        JLabel h = new JLabel(s);
        h.setFont(new Font("SansSerif", Font.BOLD, 15));
        h.setForeground(FG);
        h.setBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0));
        h.setAlignmentX(0.0f);
        return h;
    }

    JLabel note(String s) {
        JLabel n = new JLabel("<html><body style='width:520px'>" + s + "</body></html>");
        n.setForeground(MUTED);
        n.setAlignmentX(0.0f);
        return n;
    }

    /** Called when the page is shown: lists the file sets and finds a WAV. */
    void refresh() {
        if (this.setsBox == null) return;
        java.util.LinkedHashMap<String, String> sets = HitCompare.fileSetsWithMidi(Engine.fileSetSources(app.learned, app.learnedFills));
        if (this.source == null || !sets.containsKey(this.source)) {
            this.source = sets.isEmpty() ? null : sets.keySet().iterator().next();
            this.pickedWav = null;
        }
        this.midiKey = this.source == null ? null : sets.get(this.source);
        this.setsBox.removeAll();
        if (sets.isEmpty()) {
            JLabel none = new JLabel("No file set with a source MIDI yet. Run DrumMidi_CRT in PyJav: its MIDI is kept with the file set it makes.");
            none.setForeground(MUTED);
            this.setsBox.add(none);
        }
        for (final String label : sets.keySet()) {
            JButton chip = app.chip(label, label.equals(this.source));
            chip.setName("compare-set:" + label);
            chip.addActionListener(e -> {
                if (!label.equals(this.source)) {
                    this.source = label;
                    this.pickedWav = null;
                    this.result.setText("");
                }
                this.refresh();
            });
            this.setsBox.add(chip);
        }
        this.setsBox.revalidate();
        this.setsBox.repaint();
        this.paintWav();
    }

    void paintWav() {
        File last = this.lastPyJavWav();
        if (this.pickedWav != null) this.wavLabel.setText("Picked: " + this.pickedWav.getName());
        else if (this.midiKey != null && HitCompare.fileSetWav(this.midiKey) != null) this.wavLabel.setText("Kept with the file set");
        else if (last != null) this.wavLabel.setText("Last WAV given to PyJav: " + last.getName());
        else this.wavLabel.setText("None. Pick the WAV you gave DrumMidi to compare against it too.");
    }

    File lastPyJavWav() {
        String p = app.pyInputPath;
        if (p == null || !p.toLowerCase().endsWith(".wav")) return null;
        File f = new File(p);
        return f.isFile() ? f : null;
    }

    void pickWav() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("WAV audio", "wav", "wave"));
        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
        this.pickedWav = chooser.getSelectedFile();
        this.paintWav();
    }

    void compare() {
        if (this.running) return;
        if (this.midiKey == null) {
            app.setNow("Pick a file set first");
            return;
        }
        final byte[] midi = HitCompare.fileSetMidi(this.midiKey);
        final Engine.ImportedSong song = HitCompare.songFor(this.source, app.importedSongs);
        final byte[] kept = HitCompare.fileSetWav(this.midiKey);
        final File file = this.pickedWav != null ? this.pickedWav : (kept == null ? this.lastPyJavWav() : null);
        final byte[] keptWav = this.pickedWav != null ? null : kept;
        this.running = true;
        this.result.setText("Comparing…");
        app.setNow("Comparing hits");
        new Thread(() -> {
            String text;
            try {
                byte[] w = file != null ? Files.readAllBytes(file.toPath()) : keptWav;
                text = HitCompare.report(midi, song, w == null ? null : AudioIo.parseWav(w));
            } catch (Throwable ex) {
                text = "Could not compare: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            final String shown = text;
            SwingUtilities.invokeLater(() -> {
                this.running = false;
                this.result.setText(shown);
                app.setNow("Compare Hits");
            });
        }, "compare-hits").start();
    }
}
