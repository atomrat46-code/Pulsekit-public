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
        if (song != null && !song.isEmpty()) app.songEditor.addImportedSong(shown, song);
        if (back != null) app.showView(back);
        app.setNow("Style · " + row.name);
    }

    void makeFileSetSong(String key, String label) {
        String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        Engine.FileSet set = Engine.collectFset(src, label, app.learned, app.learnedFills, app.fillernPairs);
        if (set == null) {
            app.setNow("That file set is empty");
            return;
        }
        java.util.List<Engine.FileSetPart> stored = app.fileSetParts.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        java.util.List<Engine.Part> song = Engine.songFromFileSet(set);
        if (song == null || song.isEmpty()) {
            app.setNow("Could not make a song from that file set");
            return;
        }
        String name = label == null || label.isEmpty() ? (src.isEmpty() ? "Import" : src) : label;
        app.songEditor.addImportedSong(name, song);
        // The song belongs to this file set: export names and Compare Hits follow it.
        Engine.ImportedSong made = app.songEditor.importedSong();
        if (made != null && !src.isEmpty()) {
            made.fileSet = src;
            app.persistence.persistLearned();
        }
    }

    void storeAudioDir() {
        Engine.storeFileSetAudioDir(new File(app.persistence.pulsekitDir(), "fset-audio"));
    }

    void openFileSetInfo(String key, String label) {
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
