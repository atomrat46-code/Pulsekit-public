package pulsekit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import pulsekit.AudioIo;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** File sets: the Info page, style changes, songs from sets, and saved part timelines. */
final class FileSets {
    final Pulsekit app;

    FileSets(Pulsekit app) {
        this.app = app;
    }

    JLabel infoTitle;

    JPanel infoRows;

    String infoBack = "import";

    JPanel buildInfoPage() {
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
        JButton close = app.action("Close", ELEV, FG);
        close.addActionListener(e -> this.closeFileSetInfo());
        JPanel closeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeWrap.setOpaque(false);
        closeWrap.add(close);
        head.add(closeWrap, BorderLayout.EAST);
        app.infoStatus = new JLabel("Hold a file set and choose Info.");
        app.infoStatus.setForeground(MUTED);
        app.infoStatus.setAlignmentX(0.0f);
        col.add(head);
        col.add(Box.createVerticalStrut(8));
        col.add(app.infoStatus);
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

    void closeFileSetInfo() {
        String back = this.infoBack;
        if (back == null || back.isEmpty() || "fsetinfo".equals(back)) back = "import";
        app.showView(back);
    }

    String fileSetStyleLabel(String key) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        java.util.List<Engine.FileSetPart> stored = app.fileSetParts.get(src);
        if (stored == null || stored.isEmpty()) {
            Engine.FileSet set = Engine.collectFset(src, "", app.learned, app.learnedFills, app.fillernPairs);
            if (set != null) stored = set.parts;
        }
        if (stored == null) return "";
        for (Engine.FileSetPart p : stored) {
            if (p != null && p.styleLabel != null && !p.styleLabel.trim().isEmpty()) return p.styleLabel.trim();
        }
        return "";
    }

    void promptChangeStyle(String key, String label) {
        java.util.List<StyleDb.Row> rows = StyleDb.rows();
        if (rows.isEmpty()) {
            app.setNow("The style database is empty");
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
            app, scroll, "Style database",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (ans != 0) return;
        int pick = list.getSelectedIndex();
        if (pick < 0) pick = initial;
        this.applyFileSetStyle(key, label, rows.get(pick));
    }

    void applyFileSetStyle(String key, String label, StyleDb.Row row) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        if (!Engine.fileSetStyleOn(Engine.fileSetOriginOf(src))) {
            app.setNow("Using the source file");
            return;
        }
        String shown = label == null || label.isEmpty() ? (src.isEmpty() ? "Import" : src) : label;
        Engine.FileSet set = Engine.collectFset(src, shown, app.learned, app.learnedFills, app.fillernPairs);
        if (set == null) {
            app.setNow("That file set is empty");
            return;
        }
        java.util.List<Engine.FileSetPart> stored = app.fileSetParts.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        set.origin = Engine.fileSetOriginOf(src);
        Engine.FileSetAudio au = Engine.fileSetAudioOf(src);
        if (au != null) set.sourceWav = au.sourceWav;
        String back = app.view;
        Engine.FileSet next = AudioIo.restyleFileSet(set, row.kit, row.name, row.hats, row.four, row.dkick, row.bpm, row.back);
        String first = Engine.replaceFileSetLearned(src, next, app.learned, app.learnedFills, app.fillernPairs);
        if (next.parts != null && !next.parts.isEmpty()) this.rememberFileSetParts(src, next.parts);
        if (Engine.isFileSetOrigin(set.origin)) Engine.rememberFileSetOrigin(src, set.origin);
        app.persistence.persistLearned();
        app.styleLibrary.refreshLearnedChips();
        if (first != null) app.styleLibrary.loadLearned(first);
        Engine.ImportedSong made = Engine.fileSetSongMade(shown, app.importedSongs);
        if (made != null) app.importedSongs.remove(made);
        java.util.List<Engine.Part> song = Engine.songFromFileSet(next);
        if (song != null && !song.isEmpty()) {
            app.songEditor.addImportedSong(shown, song);
            Engine.ImportedSong styled = app.songEditor.importedSong();
            if (styled != null && !src.isEmpty()) styled.fileSet = src;
            app.persistence.persistLearned();
        }
        if (back != null) app.showView(back);
        app.setNow("Style · " + row.name);
    }

    /**
     * Make song (file set menu or info): when the set already has a song, asks whether to replace
     * it, make another, or open it.
     */
    void makeFileSetSong(String key, String label) {
        String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        java.util.List<Engine.ImportedSong> have = Engine.songsOfFileSet(src, label, app.importedSongs);
        if (have.isEmpty()) {
            this.makeFileSetSongNow(key, label);
            return;
        }
        Engine.ImportedSong first = have.get(0);
        String names = first.name + (have.size() > 1 ? " and " + (have.size() - 1) + " more" : "");
        Object[] options = {"Replace", "Make another", "Open it", "Cancel"};
        int ans = javax.swing.JOptionPane.showOptionDialog(app,
            names + ".\n\nReplace it with a new song from the file set, make another song, or open it?",
            "This file set already has a song", javax.swing.JOptionPane.DEFAULT_OPTION,
            javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (ans == 0) this.replaceFileSetSong(first, key, label);
        else if (ans == 1) this.makeFileSetSongNow(key, label);
        else if (ans == 2) this.openSong(first);
    }

    /** The song built from the file set, as Make song makes it; null (with a message) when it cannot be. */
    java.util.List<Engine.Part> buildFileSetSong(String key, String label) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        Engine.FileSet set = Engine.collectFset(src, label, app.learned, app.learnedFills, app.fillernPairs);
        if (set == null) {
            app.setNow("That file set is empty");
            return null;
        }
        java.util.List<Engine.FileSetPart> stored = app.fileSetParts.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        java.util.List<Engine.Part> song = Engine.songFromFileSet(set);
        if (song == null || song.isEmpty()) {
            app.setNow("Could not make a song from that file set");
            return null;
        }
        return song;
    }

    /** Replace: the song's parts are rebuilt from the file set; its name stays. */
    void replaceFileSetSong(Engine.ImportedSong old, String key, String label) {
        java.util.List<Engine.Part> song = this.buildFileSetSong(key, label);
        if (song == null) return;
        String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        old.parts.clear();
        old.parts.addAll(song);
        if (!src.isEmpty()) old.fileSet = src;
        old.fileSetSong = null;
        app.persistence.persistLearned();
        this.openSong(old);
        app.setNow("Replaced \u00b7 " + old.name);
    }

    /** Shows the song in the Song view (Imported lane). */
    void openSong(Engine.ImportedSong song) {
        app.importedSongId = song.id;
        app.songLane = "imported";
        app.showView("song");
        app.songEditor.refreshSong();
    }

    /** Make another: a new song from the file set, without asking. */
    void makeFileSetSongNow(String key, String label) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        java.util.List<Engine.Part> song = this.buildFileSetSong(key, label);
        if (song == null) return;
        String name = label == null || label.isEmpty() ? (src.isEmpty() ? "Import" : src) : label;
        app.songEditor.addImportedSong(name, song);
        // The song belongs to this file set: export names and Compare Hits follow it.
        Engine.ImportedSong made = app.songEditor.importedSong();
        if (made != null && !src.isEmpty()) {
            made.fileSet = src;
            app.persistence.persistLearned();
        }
    }

    /** True when the file set keeps a source MIDI, so Compare hits has something to compare. */
    boolean hasSourceMidi(String key) {
        String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        return Engine.fileSetMidiName(Engine.fileSetMidiKeyForLabel(src)).length() > 0;
    }

    /**
     * File set menu > Compare hits: the set's source MIDI against its song and the original WAV
     * (kept with the set, or the last WAV given to PyJav), as CompareHits --log writes it. The
     * results are kept with the file set as CompareHits_test_results.txt.
     */
    void compareFileSetHits(String key, String label) {
        String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        final String midiKey = Engine.fileSetMidiKeyForLabel(src);
        final byte[] midi = HitCompare.fileSetMidi(midiKey);
        if (midi == null || midi.length < 14) {
            app.setNow("This file set keeps no source MIDI");
            return;
        }
        final String midiName = Engine.fileSetMidiName(midiKey);
        Engine.ImportedSong found = HitCompare.songFor(src, app.importedSongs);
        if (found == null && !midiKey.equals(src)) found = HitCompare.songFor(midiKey, app.importedSongs);
        final Engine.ImportedSong song = found;
        final byte[] kept = HitCompare.fileSetWav(midiKey);
        final File last = kept == null ? app.compareHits.lastPyJavWav() : null;
        final String shown = label == null || label.isEmpty() ? (src.isEmpty() ? "Other" : src) : label;
        app.setNow("Comparing hits \u00b7 " + shown);
        new Thread(() -> {
            String text;
            boolean ok = true;
            try {
                byte[] w = kept != null ? kept : (last != null ? java.nio.file.Files.readAllBytes(last.toPath()) : null);
                String wavName = kept != null ? "source.wav (kept with the file set)" : (last != null ? last.getName() : "");
                text = HitCompare.fileSetLog(shown, midi, midiName, song, w == null ? null : AudioIo.parseWav(w), wavName);
            } catch (Throwable ex) {
                ok = false;
                text = "Could not compare: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            final String result = text;
            final boolean saved = ok;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (saved) {
                    Engine.rememberFileSetResults(midiKey, result);
                    this.storeAudioDir();
                    app.styleLibrary.refreshLearnedChips();
                }
                app.setNow(saved ? "Compare hits \u00b7 " + shown + " \u00b7 " + HitCompare.RESULTS_FILE : "Could not compare hits");
                this.showResults(shown, result);
            });
        }, "compare-hits").start();
    }

    /** The results text in a dialog, with Save as. */
    void showResults(String title, String text) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(text, 28, 84);
        area.setEditable(false);
        // Tables fit in 84 columns; longer sentences wrap at word boundaries.
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        area.setName("fileset-results-text");
        Object[] options = {"Save as " + HitCompare.RESULTS_FILE, "Close"};
        int ans = javax.swing.JOptionPane.showOptionDialog(app, new javax.swing.JScrollPane(area), "Compare hits \u00b7 " + title,
            javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE, null, options, options[1]);
        if (ans == 0) SaveText.save(app, HitCompare.RESULTS_FILE, text);
    }

    /** CompareHits_test_results.txt under the file set, with Open and Save. Null when there are none. */
    javax.swing.JPanel resultsRow(String src, String label) {
        final String key = Engine.fileSetMidiKeyForLabel(src == null ? "" : src);
        final String text = Engine.fileSetResults(key);
        if (text == null) return null;
        javax.swing.JPanel row = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        row.setName("fileset-results");
        javax.swing.JLabel name = new javax.swing.JLabel(HitCompare.RESULTS_FILE);
        name.setForeground(MUTED);
        row.add(name);
        javax.swing.JButton open = app.chip("Open", false);
        open.addActionListener(e -> this.showResults(label == null || label.isEmpty() ? "Other" : label, text));
        javax.swing.JButton save = app.chip("Save", false);
        save.addActionListener(e -> SaveText.save(app, HitCompare.RESULTS_FILE, text));
        row.add(open);
        row.add(save);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** Info: the songs connected to the file set, each opening in the Song view; or Make song. */
    JPanel infoSongs(final String key, final String label) {
        String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        java.util.List<Engine.ImportedSong> songs = Engine.songsOfFileSet(src, label, app.importedSongs);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(0.0f);
        box.setName("info-songs");
        box.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        JLabel head = new JLabel(songs.size() == 1 ? "Song" : "Songs");
        head.setForeground(FG);
        head.setFont(head.getFont().deriveFont(java.awt.Font.BOLD));
        head.setAlignmentX(0.0f);
        box.add(head);
        if (songs.isEmpty()) {
            JPanel line = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
            line.setOpaque(false);
            line.setAlignmentX(0.0f);
            JLabel none = new JLabel("No song from this file set yet");
            none.setForeground(MUTED);
            line.add(none);
            javax.swing.JButton make = app.chip("Make song", false);
            make.addActionListener(e -> this.makeFileSetSongNow(key, label));
            line.add(make);
            box.add(line);
        }
        for (final Engine.ImportedSong song : songs) {
            JPanel line = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 2));
            line.setOpaque(false);
            line.setAlignmentX(0.0f);
            line.setName("info-song:" + song.name);
            JLabel name = new JLabel(song.name + "  \u00b7  " + song.parts.size() + " parts \u00b7 "
                + Engine.fmtClock(Engine.songDurationSec(song.parts)) + (song.fileSetSong != null ? " \u00b7 saved in the set" : ""));
            name.setForeground(FG);
            line.add(name);
            javax.swing.JButton open = app.chip("Open", false);
            open.addActionListener(e -> this.openSong(song));
            line.add(open);
            javax.swing.JButton del = app.chip("Delete", false);
            del.setName("info-delete:" + song.name);
            del.addActionListener(e -> app.songEditor.confirmDeleteSong(song));
            line.add(del);
            box.add(line);
        }
        return box;
    }

    void storeAudioDir() {
        Engine.storeFileSetAudioDir(new File(app.persistence.pulsekitDir(), "fset-audio"));
    }

    /** The file set Info last shown, so it can be shown again after a change. */
    String infoKey;
    String infoLabel;

    void reopenInfo() {
        if (this.infoKey != null) this.openFileSetInfo(this.infoKey, this.infoLabel);
    }

    void openFileSetInfo(String key, String label) {
        this.infoKey = key;
        this.infoLabel = label;
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        java.util.List<Engine.FileSetPart> parts = app.fileSetParts.get(src);
        Engine.FileSet set = Engine.collectFset(src, label, app.learned, app.learnedFills, app.fillernPairs);
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
        if (this.infoRows != null) this.infoRows.add(this.infoSongs(key, label));
        int nFill = set == null ? 0 : set.fills.size();
        int nPair = set == null ? 0 : set.fillerns.size();
        if (app.infoStatus != null) {
            String style = "";
            if (parts != null && !parts.isEmpty() && parts.get(0).styleLabel != null && !parts.get(0).styleLabel.isEmpty()) {
                style = parts.get(0).styleLabel + " \u00b7 ";
            }
            String bpm = parts == null || parts.isEmpty() ? "" : parts.get(0).bpm + " BPM \u00b7 ";
            String word = Engine.fileSetOriginTitle(Engine.fileSetOriginOf(src));
            String lead = word.isEmpty() ? "" : word + " \u00b7 ";
            String len = Engine.fileSetLengthLine(parts, set == null ? 0 : set.durationSec);
            app.infoStatus.setText(lead + style + bpm + (parts == null ? 0 : parts.size()) + " parts \u00b7 " + len + " \u00b7 " + nFill + " fills \u00b7 " + nPair + " Fillerns");
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
        if (!"fsetinfo".equals(app.view)) this.infoBack = app.view;
        app.showView("fsetinfo");
    }

    void rememberFileSetParts(String source, java.util.List<Engine.FileSetPart> parts) {
        if (source == null) return;
        if (parts == null || parts.isEmpty()) app.fileSetParts.remove(source);
        else app.fileSetParts.put(source, new java.util.ArrayList<Engine.FileSetPart>(parts));
        this.persistFileSetInfo();
    }

    void persistFileSetInfo() {
        try {
            Files.write(new File(app.persistence.pulsekitDir(), "fset-info.json").toPath(),
                Engine.encodeFsetInfo(app.fileSetParts).getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (Exception ignored) { /* optional */ }
    }

    void restoreFileSetInfo() {
        try {
            File f = new File(app.persistence.pulsekitDir(), "fset-info.json");
            if (!f.isFile() || f.length() < 8) return;
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            app.fileSetParts.clear();
            app.fileSetParts.putAll(Engine.decodeFsetInfo(json));
            Engine.fileSetOrigins.clear();
            Engine.fileSetOrigins.putAll(Engine.decodeFsetOrigins(json));
            Engine.loadFileSetSongs(json);
            Engine.loadFileSetAudioDir(new File(app.persistence.pulsekitDir(), "fset-audio"));
        } catch (Exception ignored) { /* optional */ }
    }

    String fmtTime(float sec) {
        int s = Math.max(0, Math.round(sec));
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
