package pulsekit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** The Song page: parts, the song-pick menus, imported songs, Fillerns in songs, Save to set. */
final class SongEditor {
    final Pulsekit app;

    SongEditor(Pulsekit app) {
        this.app = app;
    }

    final JPanel timeline = new JPanel(new FlowLayout(0, 2, 0));

    JScrollPane timelineScroll;

    final JPanel songAdds = new JPanel(new FlowLayout(0, 6, 4));

    final DefaultListModel<String> songModel = new DefaultListModel();

    JList<String> songList;

    JPanel songLaneBar;

    JPanel buildSongPage() {
        JPanel jPanel = new JPanel(new BorderLayout(8, 8));
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel();
        jPanel2.setOpaque(false);
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        JPanel jPanel3 = new JPanel(new FlowLayout(0, 6, 0));
        jPanel3.setOpaque(false);
        JButton jButton = app.chip("Edit", true);
        JButton jButton2 = app.chip("Play", false);
        jButton.addActionListener(actionEvent -> {
            app.songMode = "edit";
            this.songAdds.setVisible(true);
            app.chrome.setVisible(true);
            app.styleHost.setVisible(false);
            app.fillBar.setVisible(false);
            app.paintChip(jButton, true);
            app.paintChip(jButton2, false);
            this.refreshSong();
        });
        jButton2.addActionListener(actionEvent -> {
            app.songMode = "play";
            this.songAdds.setVisible(false);
            app.chrome.setVisible(false);
            app.paintChip(jButton, false);
            app.paintChip(jButton2, true);
            this.refreshSong();
        });
        jPanel3.add(jButton);
        jPanel3.add(jButton2);
        this.songLaneBar = new JPanel(new FlowLayout(0, 6, 0));
        this.songLaneBar.setOpaque(false);
        JButton origLane = app.chip("Original", true);
        JButton impLane = app.chip("Imported", false);
        origLane.addActionListener(e -> {
            app.songLane = "original";
            app.paintChip(origLane, true);
            app.paintChip(impLane, false);
            this.refreshSong();
        });
        impLane.addActionListener(e -> {
            app.songLane = "imported";
            if (app.importedSongId == null && !app.importedSongs.isEmpty()) app.importedSongId = app.importedSongs.get(0).id;
            app.paintChip(origLane, false);
            app.paintChip(impLane, true);
            this.refreshSong();
        });
        origLane.putClientProperty("lane", "original");
        impLane.putClientProperty("lane", "imported");
        this.songLaneBar.add(origLane);
        this.songLaneBar.add(impLane);
        this.songAdds.setOpaque(false);
        JButton jButton3 = app.chip("Pattern \u00d74", false);
        jButton3.addActionListener(actionEvent -> {
            Engine.Part p = Engine.groove(app.styles.get((Object)app.style).label, app.bpm(), app.cells, 4);
            p.lens = Engine.copyCells(app.lens);
            this.addPart(p);
        });
        app.onChipMenu(jButton3, () -> this.songPickMenu(null, "pattern"));
        JButton jButtonFillern = app.chip("Fillern", false);
        jButtonFillern.addActionListener(actionEvent -> this.addCurrentFillern());
        app.onChipMenu(jButtonFillern, () -> this.songPickMenu(null, "fillern"));
        JButton jButton4 = app.chip("Fill", false);
        jButton4.addActionListener(actionEvent -> {
            Engine.Part p = Engine.fill(app.styleLibrary.fillLabel(app.fillId), app.bpm(), app.fillPat, 1);
            p.lens = Engine.copyCells(app.fillLens);
            this.addPart(p);
        });
        app.onChipMenu(jButton4, () -> this.songPickMenu(null, "fill"));
        JButton jButton5 = app.chip("Silent", false);
        jButton5.addActionListener(actionEvent -> this.addPart(Engine.rest(app.bpm(), 2)));
        JButton jButton6 = app.chip("Up", false);
        JButton jButton7 = app.chip("Down", false);
        JButton jButton8 = app.chip("Remove", false);
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
                if ("imported".equals(app.songLane)) app.persistence.persistLearned();
                this.refreshSong();
            }
        });
        jList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!(e.isPopupTrigger() || e.getButton() == 3)) return;
                if ("imported".equals(app.songLane) && "edit".equals(app.songMode)) {
                    int at = jList.locationToIndex(e.getPoint());
                    if (at < 0) return;
                    jList.setSelectedIndex(at);
                    SongEditor.this.importedPartMenu(at).show(jList, e.getX(), e.getY());
                    return;
                }
                if (!"original".equals(app.songLane) || !"edit".equals(app.songMode)) return;
                int idx = jList.locationToIndex(e.getPoint());
                if (idx < 0) return;
                jList.setSelectedIndex(idx);
                JPopupMenu menu = SongEditor.this.songPickMenu(idx);
                if (menu.getComponentCount() > 0) menu.show(jList, e.getX(), e.getY());
            }
        });
        this.songAdds.add(jButton3);
        this.songAdds.add(jButtonFillern);
        this.songAdds.add(jButton4);
        this.songAdds.add(jButton5);
        JButton saveToSet = app.chip("Save to set", false);
        saveToSet.addActionListener(actionEvent -> this.saveSongToFileSet(saveToSet));
        this.songAdds.add(saveToSet);
        this.songAdds.add(jButton6);
        this.songAdds.add(jButton7);
        this.songAdds.add(jButton8);
        JButton jButtonClear = app.chip("Clear", false);
        jButtonClear.addActionListener(actionEvent -> {
            if ("imported".equals(app.songLane) && app.importedSongId != null) {
                String id = app.importedSongId;
                app.importedSongs.removeIf(s -> id.equals(s.id));
                app.importedSongId = app.importedSongs.isEmpty() ? null : app.importedSongs.get(0).id;
                app.persistence.persistLearned();
            } else {
                app.song.clear();
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

    String songFileLabel(String key, boolean fill) {
        if (key == null) return "Other";
        if (key.startsWith("p:")) {
            String rest = key.substring(2);
            int slash = rest.indexOf('/');
            String pid = slash > 0 ? rest.substring(0, slash) : rest;
            for (Engine.Plugin p : app.plugins) if (p.id.equals(pid)) return p.name;
            return "Pack";
        }
        if (key.startsWith("l:")) {
            String id = key.substring(2);
            if (fill) {
                for (Engine.LearnedFill x : app.learnedFills) {
                    if (id.equals(x.id)) {
                        String s = Engine.sourceOf(x);
                        return s.isEmpty() ? "Other" : s;
                    }
                }
            } else {
                for (Engine.Learned x : app.learned) {
                    if (id.equals(x.id)) {
                        String s = Engine.sourceOf(x);
                        return s.isEmpty() ? "Other" : s;
                    }
                }
            }
        }
        return "Other";
    }

    void addImportedSongRows(JPopupMenu m, java.util.List<String[]> imported, String kind, Integer replaceAt) {
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
            app.addMenuHeading(m, e.getKey());
            for (String[] row : e.getValue()) {
                final String key = row[1];
                JMenuItem it = new JMenuItem("  " + row[2]);
                it.addActionListener(ev -> this.applySongPick(kind, key, replaceAt));
                m.add(it);
            }
        }
    }

    List<Engine.Part> activeSong() {
        if ("imported".equals(app.songLane)) {
            Engine.ImportedSong item = this.importedSong();
            if (item != null) return item.parts;
            return new ArrayList<Engine.Part>();
        }
        return app.song;
    }

    Engine.ImportedSong importedSong() {
        if (app.importedSongId != null) {
            for (Engine.ImportedSong s : app.importedSongs) {
                if (app.importedSongId.equals(s.id)) return s;
            }
        }
        return app.importedSongs.isEmpty() ? null : app.importedSongs.get(0);
    }

    /** A song saved in a file set, listed under Imported songs and built from the set's patterns. */
    Engine.ImportedSong addFileSetSong(String source, Engine.FileSetSong song) {
        Engine.ImportedSong item = new Engine.ImportedSong();
        item.id = Engine.newLearnedId();
        item.name = Engine.uniqueImportedName(song.name, app.importedSongs);
        item.fileSet = source;
        item.fileSetSong = song.name;
        item.parts.addAll(Engine.resolveSong(song, Engine.learnedFrom(app.learned, source), Engine.fillsFrom(app.learnedFills, source)));
        app.importedSongs.add(0, item);
        while (app.importedSongs.size() > Engine.MAX_IMPORTED_SONGS) app.importedSongs.remove(app.importedSongs.size() - 1);
        if (app.importedSongId == null) app.importedSongId = item.id;
        app.persistence.persistLearned();
        return item;
    }

    /** Opening a song saved in a file set builds it again from the set's patterns, so their edits show. */
    void refreshFromFileSet(Engine.ImportedSong item) {
        if (item == null || item.fileSet == null || item.fileSetSong == null) return;
        List<Engine.FileSetSong> songs = Engine.fileSetSongs.get(item.fileSet);
        if (songs == null) return;
        for (Engine.FileSetSong song : songs) {
            if (!song.name.equals(item.fileSetSong)) continue;
            item.parts.clear();
            item.parts.addAll(Engine.resolveSong(song, Engine.learnedFrom(app.learned, item.fileSet), Engine.fillsFrom(app.learnedFills, item.fileSet)));
            return;
        }
    }

    /** Save to set: the song goes into a file set, as references to the set's patterns and fills. */
    void saveSongToFileSet(JComponent anchor) {
        List<Engine.Part> song = this.activeSong();
        if (song.isEmpty()) {
            app.setNow("The song is empty");
            return;
        }
        List<String> sources = Engine.fileSetSources(app.learned, app.learnedFills);
        if (sources.isEmpty()) {
            app.setNow("Import a MIDI to make a file set first");
            return;
        }
        Engine.ImportedSong imported = "imported".equals(app.songLane) ? this.importedSong() : null;
        String best = imported != null && imported.fileSet != null && sources.contains(imported.fileSet) ? imported.fileSet : this.bestFileSetFor(song, sources);
        List<String> order = new ArrayList<String>();
        if (best != null) order.add(best);
        for (String s : sources) if (!order.contains(s)) order.add(s);
        String songName = imported != null ? (imported.fileSetSong != null ? imported.fileSetSong : imported.name) : "Song";
        JPopupMenu m = new JPopupMenu();
        app.addMenuHeading(m, "Save \"" + songName + "\" to file set");
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
    String songFileSet(List<Engine.Part> song) {
        List<String> sources = Engine.fileSetSources(app.learned, app.learnedFills);
        if ("imported".equals(app.songLane)) {
            Engine.ImportedSong imported = this.importedSong();
            if (imported != null && imported.fileSet != null && sources.contains(imported.fileSet)) return imported.fileSet;
            if (imported != null && sources.contains(imported.name)) return imported.name;
        }
        String best = this.bestFileSetFor(song, sources);
        return best == null || best.isEmpty() ? null : best;
    }

    /** The file set whose pattern and fill names the song uses most. */
    String bestFileSetFor(List<Engine.Part> song, List<String> sources) {
        String best = null;
        int bestHits = 0;
        for (String src : sources) {
            int hits = 0;
            List<Engine.Learned> pats = Engine.learnedFrom(app.learned, src);
            List<Engine.LearnedFill> fills = Engine.fillsFrom(app.learnedFills, src);
            for (Engine.Part p : song) {
                for (Engine.Learned x : pats) if (x.name.equals(p.name)) { hits++; break; }
                for (Engine.LearnedFill x : fills) if (x.name.equals(p.name)) { hits++; break; }
            }
            if (hits > bestHits) { bestHits = hits; best = src; }
        }
        return best;
    }

    void saveSongInto(String source, String songName, Engine.ImportedSong imported) {
        List<Engine.Part> song = this.activeSong();
        Engine.FileSetSong saved = Engine.songForFileSet(songName, song, Engine.learnedFrom(app.learned, source), Engine.fillsFrom(app.learnedFills, source));
        List<Integer> outside = Engine.partsFromOutside(saved);
        if (!outside.isEmpty()) {
            String set = source.isEmpty() ? "Other" : source;
            int ans = JOptionPane.showOptionDialog(app,
                outside.size() + (outside.size() == 1 ? " part uses a pattern or fill" : " parts use patterns or fills") + " that are not in " + set
                    + ".\nCopy them into it? Otherwise their notes are kept in the song.",
                "Parts from outside the file set", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[] { "Copy into set", "Keep in song" }, "Copy into set");
            if (ans == 0) {
                this.copyPartsIntoSet(source, song, outside);
                saved = Engine.songForFileSet(songName, song, Engine.learnedFrom(app.learned, source), Engine.fillsFrom(app.learnedFills, source));
            }
        }
        Engine.putFileSetSong(source, saved);
        if (imported != null) {
            imported.fileSet = source;
            imported.fileSetSong = saved.name;
        } else {
            Engine.ImportedSong added = this.addFileSetSong(source, saved);
            app.importedSongId = added.id;
        }
        app.fileSets.persistFileSetInfo();
        app.persistence.persistLearned();
        this.refreshSong();
        int refs = 0;
        for (Engine.SongRef r : saved.parts) if (r.usePattern != null || r.useFill != null) refs++;
        app.setNow("Saved \u00b7 " + saved.name + " in " + (source.isEmpty() ? "Other" : source) + " \u00b7 " + refs + " of " + saved.parts.size() + " parts follow the set");
    }

    /** Copies the patterns and fills of outside parts into the file set, and names the parts after the copies. */
    void copyPartsIntoSet(String source, List<Engine.Part> song, List<Integer> outside) {
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
                    fill.name = Engine.uniqueFillName(p.name, Engine.fillsFrom(app.learnedFills, source));
                    fill.cells = Engine.copyCells(p.cells);
                    fill.source = source;
                    app.learnedFills.add(0, fill);
                    name = fill.name;
                } else {
                    Engine.Learned pat = new Engine.Learned();
                    pat.id = Engine.newLearnedId();
                    pat.name = Engine.uniqueLearnedName(p.name, Engine.learnedFrom(app.learned, source));
                    pat.bpm = p.bpm;
                    pat.closest = "";
                    pat.cells = Engine.copyCells(p.cells);
                    pat.tsNum = p.tsNum;
                    pat.tsDen = p.tsDen;
                    pat.source = source;
                    app.learned.add(0, pat);
                    app.styles.put(pat.id, new Engine.Style(pat.id, pat.name, pat.bpm, Engine.rowsFromCells(pat.cells)));
                    name = pat.name;
                }
                copied.put(key, name);
            }
            p.name = name;
        }
        app.styleLibrary.refreshLearnedChips();
    }

    void addImportedSong(String name, List<Engine.Part> parts) {
        Engine.ImportedSong item = new Engine.ImportedSong();
        item.id = Engine.newLearnedId();
        item.name = Engine.uniqueImportedName(name, app.importedSongs);
        item.parts.addAll(parts);
        app.importedSongs.add(0, item);
        while (app.importedSongs.size() > Engine.MAX_IMPORTED_SONGS) {
            app.importedSongs.remove(app.importedSongs.size() - 1);
        }
        app.importedSongId = item.id;
        app.songLane = "imported";
        app.persistence.persistLearned();
        app.showView("song");
        this.refreshSong();
        app.setNow("Imported song \u00b7 " + item.name);
    }

    JPopupMenu songPickMenu(Integer replaceAt) {
        return this.songPickMenu(replaceAt, null);
    }

    JPopupMenu songPickMenu(Integer replaceAt, String only) {
        JPopupMenu m = new JPopupMenu();
        if (!"original".equals(app.songLane)) return m;
        boolean all = only == null || only.isEmpty();
        if (all || "pattern".equals(only)) {
            if (all) {
                app.addMenuHeading(m, "Pattern");
            } else {
                app.addMenuHeading(m, "Built-in");
            }
            for (String id : Engine.styles().keySet()) {
                if (app.hiddenStyles.contains(id)) continue;
                Engine.Style st = app.styles.get(id);
                if (st == null) continue;
                final String key = "s:" + id;
                JMenuItem it = new JMenuItem(st.label);
                it.addActionListener(e -> this.applySongPick("pattern", key, replaceAt));
                m.add(it);
            }
            if (!app.variatedPatterns.isEmpty()) {
                if (!all) app.addMenuHeading(m, "Variated");
                for (Engine.Learned item : app.variatedPatterns) {
                    JMenuItem it = new JMenuItem(item.name);
                    it.addActionListener(e -> this.applySongPick("pattern", "v:" + item.id, replaceAt));
                    m.add(it);
                }
            }
            if (!app.plugins.isEmpty() || !app.learned.isEmpty()) {
                boolean anyImp = false;
                for (Engine.Plugin p : app.plugins) if (p.enabled && !p.styles.isEmpty()) anyImp = true;
                if (!app.learned.isEmpty()) anyImp = true;
                if (anyImp && !all) app.addMenuHeading(m, "Imported");
                for (Engine.Plugin p : app.plugins) {
                    if (!p.enabled || p.styles.isEmpty()) continue;
                    app.addMenuHeading(m, p.name);
                    for (Engine.Style st : p.styles) {
                        final String key = "p:" + p.id + "/" + st.id;
                        JMenuItem it = new JMenuItem("  " + st.label);
                        it.addActionListener(ev -> this.applySongPick("pattern", key, replaceAt));
                        m.add(it);
                    }
                }
                java.util.LinkedHashMap<String, java.util.ArrayList<Engine.Learned>> learnedPacks = new java.util.LinkedHashMap<>();
                for (Engine.Learned item : app.learned) {
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
                    app.addMenuHeading(m, e.getKey());
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
            keys.add(app.styleLibrary.currentPatternKey());
            keys.addAll(app.fillernPairs.keySet());
            java.util.ArrayList<String[]> rows = new java.util.ArrayList<String[]>();
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<String>();
            for (String pk : keys) {
                if (pk == null || !seen.add(pk)) continue;
                if (!app.styleLibrary.fillernUnderlined(pk)) continue;
                String fk = app.styleLibrary.fillernFillKeyOf(pk);
                rows.add(new String[] { app.styleLibrary.songPickSection(pk), pk, this.patternName(pk) + " \u00b7 " + app.styleLibrary.fillLabel(fk) });
            }
            if (rows.isEmpty()) {
                if ("fillern".equals(only)) {
                    JMenuItem empty = new JMenuItem("No underlined Fillerns yet");
                    empty.setEnabled(false);
                    m.add(empty);
                }
            } else if (all) {
                if (m.getComponentCount() > 0) m.addSeparator();
                app.addMenuHeading(m, "Fillern");
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
                            app.addMenuHeading(m, sec);
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
                    app.addMenuHeading(m, "Imported");
                    this.addImportedSongRows(m, imported, "fillern", replaceAt);
                }
            }
        }
        if (all || "fill".equals(only)) {
            java.util.ArrayList<String[]> fillRows = new java.util.ArrayList<String[]>();
            for (int i = 0; i < Engine.FILL_ID.length; i++) {
                if (app.hiddenFills.contains(Engine.FILL_ID[i])) continue;
                fillRows.add(new String[] { "Built-in", Engine.FILL_ID[i], Engine.FILL_LABEL[i] });
            }
            for (Engine.LearnedFill item : app.variatedFills) {
                fillRows.add(new String[] { "Variated", "v:" + item.id, item.name });
            }
            for (Engine.Plugin p : app.plugins) {
                if (!p.enabled) continue;
                for (Engine.PlugFill f : p.fills) {
                    fillRows.add(new String[] { "Imported", "p:" + p.id + "/" + f.id, f.name });
                }
            }
            for (Engine.LearnedFill item : app.learnedFills) {
                fillRows.add(new String[] { "Imported", "l:" + item.id, item.name });
            }
            if (all) {
                if (m.getComponentCount() > 0) m.addSeparator();
                app.addMenuHeading(m, "Fill");
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
                            app.addMenuHeading(m, sec);
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
                    app.addMenuHeading(m, "Imported");
                    this.addImportedSongRows(m, imported, "fill", replaceAt);
                }
            }
        }
        return m;
    }

    void addFillernPick(java.util.List<JMenuItem> items, String patternKey, Integer replaceAt, java.util.Set<String> seen) {
        if (patternKey == null || !seen.add(patternKey)) return;
        if (!app.styleLibrary.fillernUnderlined(patternKey)) return;
        String fk = app.styleLibrary.fillernFillKeyOf(patternKey);
        JMenuItem it = new JMenuItem(this.patternName(patternKey) + " \u00b7 " + app.styleLibrary.fillLabel(fk));
        final String key = patternKey;
        it.addActionListener(e -> this.applySongPick("fillern", key, replaceAt));
        items.add(it);
    }

    String patternName(String key) {
        if (key == null) return "Pattern";
        if (key.startsWith("s:")) {
            Engine.Style st = app.styles.get(key.substring(2));
            return st != null ? st.label : key.substring(2);
        }
        if (key.startsWith("l:")) {
            for (Engine.Learned x : app.learned) if (x.id.equals(key.substring(2))) return x.name;
        }
        if (key.startsWith("v:")) {
            for (Engine.Learned x : app.variatedPatterns) if (x.id.equals(key.substring(2))) return x.name;
        }
        if (key.startsWith("p:")) {
            int slash = key.indexOf('/');
            if (slash > 2) {
                String pid = key.substring(2, slash);
                String sid = key.substring(slash + 1);
                for (Engine.Plugin p : app.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.Style st : p.styles) if (sid.equals(st.id)) return st.label;
                }
            }
        }
        Engine.Style st = app.styles.get(app.style);
        return st != null ? st.label : "Pattern";
    }

    int patternBpm(String key) {
        if (key != null && key.startsWith("s:")) {
            Engine.Style st = app.styles.get(key.substring(2));
            if (st != null) return st.bpm;
        }
        if (key != null && key.startsWith("l:")) {
            for (Engine.Learned x : app.learned) if (x.id.equals(key.substring(2))) return x.bpm;
        }
        if (key != null && key.startsWith("v:")) {
            for (Engine.Learned x : app.variatedPatterns) if (x.id.equals(key.substring(2))) return x.bpm;
        }
        if (key != null && key.startsWith("p:")) {
            int slash = key.indexOf('/');
            if (slash > 2) {
                String pid = key.substring(2, slash);
                String sid = key.substring(slash + 1);
                for (Engine.Plugin p : app.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.Style st : p.styles) if (sid.equals(st.id)) return st.bpm;
                }
            }
        }
        return app.bpm();
    }

    int[][] patternCellsFor(String key) {
        if (key != null && key.startsWith("s:")) {
            Engine.Style st = app.styles.get(key.substring(2));
            if (st != null) return Engine.styleCells(st);
        }
        if (key != null && key.startsWith("l:")) {
            for (Engine.Learned x : app.learned) if (x.id.equals(key.substring(2))) return Engine.copyCells(x.cells);
        }
        if (key != null && key.startsWith("v:")) {
            for (Engine.Learned x : app.variatedPatterns) if (x.id.equals(key.substring(2))) return Engine.copyCells(x.cells);
        }
        if (key != null && key.startsWith("p:")) {
            int slash = key.indexOf('/');
            if (slash > 2) {
                String pid = key.substring(2, slash);
                String sid = key.substring(slash + 1);
                for (Engine.Plugin p : app.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.Style st : p.styles) if (sid.equals(st.id)) return Engine.styleCells(st);
                }
            }
        }
        return Engine.copyCells(app.cells);
    }

    int[][] songFillCells(String id, int[][] groove) {
        if (id != null && (id.startsWith("l:") || id.startsWith("v:") || id.startsWith("p:"))) {
            return app.styleLibrary.fillCellsFor(id);
        }
        return Engine.buildFill(id, groove != null ? groove : app.cells, app.style);
    }

    /** Right click on a part of an imported song: replace it with a Fillern. */
    JPopupMenu importedPartMenu(int n) {
        JPopupMenu m = new JPopupMenu();
        javax.swing.JMenu sub = new javax.swing.JMenu("Replace with fillern");
        List<String> keys = new ArrayList<String>();
        for (Engine.Learned item : app.learned) {
            String key = "l:" + item.id;
            if (app.styleLibrary.selectedFillFor(key) != null) keys.add(key);
        }
        for (String id : app.styles.keySet()) {
            String key = app.styleLibrary.patternKeyFor(id);
            if (!keys.contains(key) && app.styleLibrary.selectedFillFor(key) != null) keys.add(key);
        }
        for (String key : keys) {
            String label = this.patternName(key) + " \u00b7 " + app.styleLibrary.fillLabel(app.styleLibrary.selectedFillFor(key)) + Engine.fillernModeNote(app.styleLibrary.fillernModeOf(key));
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
    void replaceWithFillern(int n, String key) {
        List<Engine.Part> cur = this.activeSong();
        if (n < 0 || n >= cur.size()) return;
        Engine.Part old = cur.get(n);
        String fillKey = app.styleLibrary.selectedFillFor(key);
        if (fillKey == null) return;
        int[][] cells = this.patternCellsFor(key);
        int bar = Engine.barSteps(old.tsNum > 0 ? old.tsNum : app.tsNum, old.tsDen > 0 ? old.tsDen : app.tsDen);
        List<Engine.Part> parts = Engine.fillernParts(this.patternName(key), old.bpm, cells, Engine.usedSteps(cells), Math.max(1, old.repeats),
            app.styleLibrary.fillLabel(fillKey), this.songFillCells(fillKey, cells), bar, app.styleLibrary.fillernModeOf(key));
        for (Engine.Part p : parts) {
            p.tsNum = old.tsNum;
            p.tsDen = old.tsDen;
            if ("fill".equals(p.kind)) p.steps = bar;
        }
        cur.remove(n);
        cur.addAll(n, parts);
        if ("imported".equals(app.songLane)) app.persistence.persistLearned();
        this.refreshSong();
        app.setNow(old.name + " \u2192 " + this.patternName(key) + " Fillern");
    }

    void addCurrentFillern() {
        String mode = app.styleLibrary.fillernModeOf(app.styleLibrary.currentPatternKey());
        if (!Engine.FILLERN_AFTER.equals(mode)) {
            // The fill goes into the pattern's last or first bar: the part keeps its 4 bars.
            for (Engine.Part p : Engine.fillernParts(app.styles.get(app.style).label, app.bpm(), app.cells, app.steps, 4,
                    app.styleLibrary.fillLabel(app.fillId), app.fillPat, Engine.barSteps(app.tsNum, app.tsDen), mode)) {
                this.addPart(p);
            }
            return;
        }
        Engine.Part g = Engine.groove(app.styles.get(app.style).label, app.bpm(), app.cells, 4);
        g.lens = Engine.copyCells(app.lens);
        Engine.Part f = Engine.fill(app.styleLibrary.fillLabel(app.fillId), app.bpm(), app.fillPat, 1);
        f.lens = Engine.copyCells(app.fillLens);
        this.addPart(g);
        this.addPart(f);
    }

    void applySongPick(String kind, String key, Integer replaceAt) {
        if (!"original".equals(app.songLane)) return;
        List<Engine.Part> cur = this.activeSong();
        int idx = replaceAt == null ? -1 : replaceAt.intValue();
        if (idx < 0 || idx >= cur.size()) idx = -1;
        if ("pattern".equals(kind)) {
            int reps = idx >= 0 && "groove".equals(cur.get(idx).kind) ? cur.get(idx).repeats : 4;
            Engine.Part p = Engine.groove(this.patternName(key), this.patternBpm(key), this.patternCellsFor(key), reps);
            if (idx >= 0) cur.set(idx, p);
            else this.addPart(p);
        } else if ("fill".equals(kind)) {
            int[][] groove = idx >= 0 ? cur.get(idx).cells : app.cells;
            int reps = idx >= 0 && "fill".equals(cur.get(idx).kind) ? cur.get(idx).repeats : 1;
            int bpm = idx >= 0 ? cur.get(idx).bpm : app.bpm();
            Engine.Part p = Engine.fill(app.styleLibrary.fillLabel(key), bpm, this.songFillCells(key, groove), reps);
            if (idx >= 0) cur.set(idx, p);
            else this.addPart(p);
        } else {
            int[][] gcells = this.patternCellsFor(key);
            String fk = app.fillernPairs.get(key);
            if (fk == null) fk = key.equals(app.styleLibrary.currentPatternKey()) ? app.fillId : "toms";
            int greps = idx >= 0 && "groove".equals(cur.get(idx).kind) ? cur.get(idx).repeats : 4;
            String mode = app.styleLibrary.fillernModeOf(key);
            if (!Engine.FILLERN_AFTER.equals(mode)) {
                // The fill replaces the end or start of the pattern: the song part keeps its length.
                List<Engine.Part> parts = Engine.fillernParts(this.patternName(key), this.patternBpm(key), gcells, Engine.usedSteps(gcells), greps,
                    app.styleLibrary.fillLabel(fk), this.songFillCells(fk, gcells), Engine.barSteps(app.tsNum, app.tsDen), mode);
                if (idx >= 0) {
                    cur.set(idx, parts.get(0));
                    for (int i = 1; i < parts.size() && cur.size() < Engine.MAX_SONG; i++) cur.add(idx + i, parts.get(i));
                } else {
                    for (Engine.Part p : parts) this.addPart(p);
                }
                this.refreshSong();
                app.setNow(this.patternName(key) + " Fillern");
                return;
            }
            Engine.Part g = Engine.groove(this.patternName(key), this.patternBpm(key), gcells, greps);
            Engine.Part f = Engine.fill(app.styleLibrary.fillLabel(fk), this.patternBpm(key), this.songFillCells(fk, gcells), 1);
            if (idx >= 0) {
                cur.set(idx, g);
                if (cur.size() < Engine.MAX_SONG) cur.add(idx + 1, f);
            } else {
                this.addPart(g);
                this.addPart(f);
            }
        }
        this.refreshSong();
        app.setNow("pattern".equals(kind) || "fill".equals(kind) ? this.patternName(key) : this.patternName(key) + " Fillern");
    }

    void addPart(Engine.Part part) {
        List<Engine.Part> cur = this.activeSong();
        if ("imported".equals(app.songLane) && this.importedSong() == null) {
            app.setNow("No imported song yet");
            return;
        }
        if (cur.size() >= Engine.MAX_SONG) {
            JOptionPane.showMessageDialog(app, "Song is full");
            return;
        }
        cur.add(part);
        part.tsNum = app.tsNum;
        part.tsDen = app.tsDen;
        if ("groove".equals(part.kind)) part.steps = app.steps;
        else part.steps = Engine.barSteps(app.tsNum, app.tsDen);
        if ("imported".equals(app.songLane)) app.persistence.persistLearned();
        this.refreshSong();
    }

    void movePart(int n, int n2) {
        List<Engine.Part> cur = this.activeSong();
        int n3 = n + n2;
        if (n < 0 || n3 < 0 || n3 >= cur.size()) {
            return;
        }
        Engine.Part part = cur.get(n);
        cur.set(n, cur.get(n3));
        cur.set(n3, part);
        if ("imported".equals(app.songLane)) app.persistence.persistLearned();
        this.refreshSong();
    }

    void refreshSong() {
        this.songModel.clear();
        this.timeline.removeAll();
        if (this.songAdds != null) {
            this.songAdds.setVisible("edit".equals(app.songMode)
                && !("imported".equals(app.songLane) && app.importedSongs.isEmpty()));
        }
        if (this.songLaneBar != null) {
            Component[] cs = this.songLaneBar.getComponents();
            for (int i = cs.length - 1; i >= 0; i--) {
                Object lane = cs[i] instanceof JComponent ? ((JComponent) cs[i]).getClientProperty("lane") : null;
                if (lane == null) this.songLaneBar.remove(i);
                else app.paintChip((JButton) cs[i], String.valueOf(lane).equals(app.songLane));
            }
            if ("imported".equals(app.songLane)) {
                for (Engine.ImportedSong s : app.importedSongs) {
                    final Engine.ImportedSong item = s;
                    JButton b = app.chip(item.name, item.id.equals(app.importedSongId));
                    b.addActionListener(e -> {
                        this.refreshFromFileSet(item);
                        app.importedSongId = item.id;
                        app.songLane = "imported";
                        this.refreshSong();
                    });
                    b.setName("song-chip:" + item.name);
                    b.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override
                        public void mousePressed(java.awt.event.MouseEvent e) {
                            if (e.isPopupTrigger()) SongEditor.this.songMenu(item, b, e.getX(), e.getY());
                        }

                        @Override
                        public void mouseReleased(java.awt.event.MouseEvent e) {
                            if (e.isPopupTrigger()) SongEditor.this.songMenu(item, b, e.getX(), e.getY());
                        }
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
            boolean bl = app.songPlay && i == app.songPart;
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
        if (app.songPlay && app.songPart >= 0 && app.songPart < this.timeline.getComponentCount()) {
            final Component now = this.timeline.getComponent(app.songPart);
            SwingUtilities.invokeLater(() -> {
                java.awt.Rectangle r = now.getBounds();
                r.x = Math.max(0, r.x - 120);
                r.width += 240;
                this.timeline.scrollRectToVisible(r);
            });
        }
        if (this.songList != null && app.songPlay && app.songPart >= 0 && app.songPart < this.songModel.getSize()) {
            this.songList.setSelectedIndex(app.songPart);
            this.songList.ensureIndexIsVisible(app.songPart);
        }
        this.paintSongNow();
    }

    void paintSongNow() {
        if (!"song".equals(app.view)) return;
        List<Engine.Part> parts = this.activeSong();
        if ("imported".equals(app.songLane) && app.importedSongs.isEmpty()) {
            app.setNow("No imported song yet");
            return;
        }
        int idx = app.songPlay ? app.songPart : -1;
        int global = 0;
        if (app.songPlay && app.sequencer != null) {
            global = (int) (app.sequencer.getTickPosition() / 120L);
        } else if (app.songPlay && idx >= 0) {
            global = Engine.songGlobalStep(parts, idx, 0, Math.max(0, app.playhead));
        }
        app.setNow(Engine.songNowLine(parts, app.songPlay, idx, global));
    }

    /** Right click on a song: Duplicate song, or Delete song after asking. */
    void songMenu(final Engine.ImportedSong song, java.awt.Component at, int x, int y) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem dup = new javax.swing.JMenuItem("Duplicate song");
        dup.addActionListener(e -> this.duplicateSong(song));
        javax.swing.JMenuItem del = new javax.swing.JMenuItem("Delete song");
        del.addActionListener(e -> this.confirmDeleteSong(song));
        menu.add(dup);
        menu.add(del);
        menu.show(at, x, y);
    }

    void duplicateSong(Engine.ImportedSong song) {
        if (app.importedSongs.size() >= Engine.MAX_IMPORTED_SONGS) {
            app.setNow("Imported songs are full (" + Engine.MAX_IMPORTED_SONGS + "). Delete one first.");
            return;
        }
        Engine.ImportedSong copy = Engine.duplicateSong(song, app.importedSongs);
        int idx = app.importedSongs.indexOf(song);
        app.importedSongs.add(idx < 0 ? 0 : idx + 1, copy);
        app.importedSongId = copy.id;
        app.persistence.persistLearned();
        this.refreshSong();
        app.setNow("Duplicated \u00b7 " + copy.name);
    }

    void confirmDeleteSong(Engine.ImportedSong song) {
        String kept = song.fileSet != null && !song.fileSet.isEmpty()
            ? " The copy saved in " + song.fileSet + " stays there." : "";
        int ans = javax.swing.JOptionPane.showConfirmDialog(app,
            "Delete " + song.name + " from Imported songs? This cannot be undone." + kept,
            "Delete song?", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (ans == javax.swing.JOptionPane.OK_OPTION) this.deleteSong(song);
    }

    void deleteSong(Engine.ImportedSong song) {
        app.importedSongs.remove(song);
        if (song.id.equals(app.importedSongId)) {
            app.importedSongId = app.importedSongs.isEmpty() ? null : app.importedSongs.get(0).id;
        }
        app.persistence.persistLearned();
        this.refreshSong();
        app.setNow("Deleted \u00b7 " + song.name);
    }
}
