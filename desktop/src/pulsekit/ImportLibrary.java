package pulsekit;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import pulsekit.AudioIo;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** Learning patterns and fills from imported MIDI, file sets and the imported-file lists. */
final class ImportLibrary {
    final Pulsekit app;

    ImportLibrary(Pulsekit app) {
        this.app = app;
    }

    void removeImportSource(String source) {
        String want = source == null ? "" : source;
        app.learned.removeIf(x -> want.equals(Engine.sourceOf(x)));
        app.learnedFills.removeIf(x -> want.equals(Engine.sourceOf(x)));
        app.fileSetParts.remove(want);
        Engine.fileSetSongs.remove(want);
        Engine.forgetFileSetOrigin(want);
        Engine.forgetFileSetAudio(want);
        app.fileSets.storeAudioDir();
        app.fileSets.persistFileSetInfo();
        app.fillernPairs.entrySet().removeIf(e -> {
            String k = e.getKey();
            String v = e.getValue();
            boolean dropK = k != null && k.startsWith("l:") && app.learned.stream().noneMatch(x -> k.substring(2).equals(x.id));
            boolean dropV = v != null && v.startsWith("l:") && app.learnedFills.stream().noneMatch(x -> v.substring(2).equals(x.id));
            return dropK || dropV;
        });
        app.styleLibrary.refreshLearnedChips();
        app.persistence.persistLearned();
        app.setNow("Removed \u00b7 " + (want.isEmpty() ? "Other" : want));
    }

    void refreshImportedFiles() {
        if (app.importedFileList == null) return;
        app.importedFileList.removeAll();
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<String>();
        keys.addAll(app.styleLibrary.learnedSources());
        keys.addAll(app.styleLibrary.fillSources());
        if (keys.isEmpty()) {
            JLabel empty = new JLabel("No imported files yet.");
            empty.setForeground(MUTED);
            empty.setAlignmentX(0f);
            app.importedFileList.add(empty);
        } else {
            for (String src : keys) {
                String label = src.isEmpty() ? "Other" : src;
                String shown = Engine.fileSetMarked(label, Engine.fileSetOriginOf(src));
                String key = src.isEmpty() ? "o:other" : "f:" + src;
                int nPat = 0, nFill = 0;
                for (Engine.Learned x : app.learned) if (src.equals(Engine.sourceOf(x))) nPat++;
                for (Engine.LearnedFill x : app.learnedFills) if (src.equals(Engine.sourceOf(x))) nFill++;
                boolean open = app.styleLibrary.packOpen(key, false);
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setAlignmentX(0f);
                JButton head = app.chip((open ? "\u25BE " : "\u25B8 ") + shown + " \u00b7 " + (nPat + nFill), false);
                head.addActionListener(e -> {
                    app.openPacks.put(key, !app.styleLibrary.packOpen(key, false));
                    this.refreshImportedFiles();
                });
                JButton info = app.action("Info", ELEV, FG, () -> app.fileSets.openFileSetInfo(key, label));
                JButton make = app.action("Make song", ELEV, FG, () -> app.fileSets.makeFileSetSong(key, label));
                JButton style = Engine.fileSetStyleOn(Engine.fileSetOriginOf(src))
                    ? app.action("Change style", ELEV, FG, () -> app.fileSets.promptChangeStyle(key, label))
                    : null;
                JButton exp = app.action("Export", ELEV, FG, () -> this.saveFset(src));
                JButton del = app.action("Delete", ELEV, FG, () -> this.removeImportSource(src));
                JPanel east = new JPanel();
                east.setOpaque(false);
                east.add(info);
                east.add(make);
                if (style != null) east.add(style);
                east.add(exp);
                east.add(del);
                row.add(head, BorderLayout.CENTER);
                row.add(east, BorderLayout.EAST);
                app.importedFileList.add(row);
                if (open) {
                    JPanel kids = new JPanel();
                    kids.setOpaque(false);
                    kids.setLayout(new BoxLayout(kids, BoxLayout.Y_AXIS));
                    kids.setAlignmentX(0f);
                    for (Engine.Learned x : app.learned) {
                        if (!src.equals(Engine.sourceOf(x))) continue;
                        final Engine.Learned it = x;
                        JButton b = app.chip("Pattern \u00b7 " + it.name, false);
                        b.addActionListener(e -> { app.styleLibrary.loadLearned(it.id); app.showView("pattern"); });
                        kids.add(b);
                    }
                    for (Engine.LearnedFill x : app.learnedFills) {
                        if (!src.equals(Engine.sourceOf(x))) continue;
                        final Engine.LearnedFill it = x;
                        JButton b = app.chip("Fill \u00b7 " + it.name, false);
                        b.addActionListener(e -> { app.styleLibrary.loadLearnedFill(it.id); app.showView("fills"); });
                        kids.add(b);
                    }
                    for (Engine.Learned x : app.learned) {
                        if (!src.equals(Engine.sourceOf(x))) continue;
                        final String pair = app.fillernPairs.get("l:" + x.id);
                        if (pair == null || pair.isEmpty()) continue;
                        final Engine.Learned it = x;
                        JButton b = app.chip("Fillern \u00b7 " + it.name, false);
                        b.addActionListener(e -> {
                            app.styleLibrary.loadLearned(it.id);
                            app.showView("combo");
                        });
                        kids.add(b);
                    }
                    app.importedFileList.add(kids);
                }
                app.importedFileList.add(Box.createVerticalStrut(6));
            }
        }
        app.importedFileList.revalidate();
        app.importedFileList.repaint();
    }

    void learnFromImport(String filename, int[][] cells, int bpm) {
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
            for (Engine.LearnedFill x : app.learnedFills) {
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
                app.learnedFills.add(0, fillItem);
                while (app.learnedFills.size() > Engine.MAX_LEARNED) app.learnedFills.remove(app.learnedFills.size() - 1);
            }
            app.styleLibrary.applyFill("l:" + fillItem.id);
            app.showView("fills");
            app.styleLibrary.refreshLearnedChips();
            app.persistence.persistLearned();
            app.setNow(Engine.fillLabel(fillItem.kind) + " \u00b7 " + fillItem.name);
            return;
        }
        int[][] groove = fillern ? Engine.firstBar(cells) : cells;
        Engine.Learned styleItem = null;
        if (Engine.hitCount(groove) >= 1) {
            String sig = Engine.patternSignature(groove);
            Engine.Learned existing = null;
            for (Engine.Learned x : app.learned) {
                if (Engine.patternSignature(x.cells).equals(sig)) { existing = x; break; }
            }
            if (existing != null) {
                app.styleLibrary.loadLearned(existing.id);
                styleItem = existing;
                msg = "Style \u00b7 " + existing.name;
            } else {
                Engine.Learned item = new Engine.Learned();
                item.id = Engine.newLearnedId();
                item.name = stem;
                item.bpm = bpm;
                item.closest = Engine.matchStyle(groove);
                item.cells = Engine.copyCells(groove);
                item.swing = app.swingBar.getVal();
                item.density = app.densBar.getVal();
                item.human = app.humanBar.getVal();
                item.source = Engine.importSource(filename);
                item.tsNum = app.tsNum;
                item.tsDen = app.tsDen;
                item.steps = app.steps;
                app.learned.add(0, item);
                while (app.learned.size() > Engine.MAX_LEARNED) app.learned.remove(app.learned.size() - 1);
                app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
                app.styleLibrary.loadLearned(item.id);
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
            for (Engine.LearnedFill x : app.learnedFills) {
                if (Engine.patternSignature(x.cells).equals(sig)) { existing = x; break; }
            }
            if (existing == null) {
                Engine.LearnedFill item = new Engine.LearnedFill();
                item.id = Engine.newLearnedId();
                item.name = Engine.fillNameFromFile(filename, kind);
                item.kind = kind;
                item.cells = bar;
                item.source = Engine.importSource(filename);
                app.learnedFills.add(0, item);
                while (app.learnedFills.size() > Engine.MAX_LEARNED) app.learnedFills.remove(app.learnedFills.size() - 1);
                fillItem = item;
                msg = (msg == null ? "" : msg + " \u00b7 ") + Engine.fillLabel(kind) + " \u00b7 " + item.name;
            } else {
                fillItem = existing;
                msg = (msg == null ? "" : msg + " \u00b7 ") + existing.name;
            }
        }
        if (fillern && styleItem != null && fillItem != null) {
            app.gridEditor.applySteps(Engine.barSteps(app.tsNum, app.tsDen), false);
            app.styleLibrary.applyFill("l:" + fillItem.id);
            app.styleLibrary.rememberFillern(app.styleLibrary.patternKeyFor(styleItem.id), "l:" + fillItem.id);
            app.showView("combo");
            msg = "Fillern \u00b7 " + styleItem.name + " \u00b7 " + Engine.fillLabel(fillItem.kind);
        } else if (patternOnly) {
            app.showView("pattern");
        }
        app.styleLibrary.refreshLearnedChips();
        app.persistence.persistLearned();
        if (msg != null) app.setNow(msg);
    }

    String applyProgramMidi(byte[] data, String name) {
        try {
            Engine.stageSourceMidi(data, name);
            Engine.MidiBars bars = Engine.parseMidiBars(data);
            boolean made = bars != null && this.learnFromSongImport(name, bars, true, "program");
            Engine.clearStagedMidi();
            if (made) {
                String set = this.newestProgramSet();
                app.setNow("Import succeeded: EP " + set);
                return "Import succeeded: EP " + set;
            }
            this.applyMidiBytes(data, name);
            String source = Engine.importSource(name);
            if (source == null || source.length() == 0) source = Engine.stemNameFromMidi(name);
            boolean found = false;
            for (Engine.Learned item : app.learned) {
                if (item != null && source.equals(item.source)) found = true;
            }
            if (!found) return "Import failed: " + name + " has no drum notes Pulsekit can use";
            Engine.rememberFileSetOrigin(source, "program");
            app.fileSets.persistFileSetInfo();
            app.setNow("Import succeeded: EP " + source);
            return "Import succeeded: EP " + source;
        } catch (Throwable ex) {
            String m = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            return "Import failed: " + m;
        }
    }

    String newestProgramSet() {
        String name = "";
        for (java.util.Map.Entry<String, String> e : Engine.fileSetOrigins.entrySet()) {
            if ("program".equals(e.getValue())) name = e.getKey();
        }
        return name.length() == 0 ? "program" : name;
    }

    void applyMidiBytes(byte[] data, String name) {
        String role = Engine.midiRoleFromFile(name);
        if (role == null) {
            Engine.MidiBars bars = Engine.parseMidiBars(data);
            if (bars != null && this.learnFromSongImport(name, bars)) return;
        }
        int[][] parsed = Engine.parseMidi(data);
        if (Engine.hitCount(parsed) < 1) {
            app.setNow("No drum track in that MIDI");
            return;
        }
        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
            System.arraycopy(parsed[t], 0, app.cells[t], 0, Engine.MAX_STEPS);
        }
        int bpm = Engine.parseMidiBpm(data, app.bpm());
        app.tempoBar.setVal(Engine.clampBpm(bpm));
        app.bpmField.setText(Integer.toString(app.bpm()));
        int tn = 4;
        int td = 4;
        Engine.MidiBars meta = Engine.parseMidiBars(data);
        if (meta != null) {
            tn = meta.tsNum;
            td = meta.tsDen;
        }
        app.tsNum = Engine.clampTsNum(tn);
        app.tsDen = Engine.clampTsDen(td);
        app.tsNumField.setText(Integer.toString(app.tsNum));
        app.tsDenField.setText(Integer.toString(app.tsDen));
        app.gridEditor.applySteps(Engine.patternLenFromCells(parsed, app.tsNum, app.tsDen), false);
        Engine.defaultAccents(app.accents, app.steps, Engine.stepsPerBeat(app.tsDen));
        app.showView("pattern");
        app.gridEditor.refreshGrid();
        this.learnFromImport(name, parsed, bpm);
        if (app.nowPlaying.getText() == null || app.nowPlaying.getText().startsWith("MIDI")
            || app.nowPlaying.getText().startsWith("No drum")) {
            app.setNow("MIDI \u00b7 " + name);
        }
    }

    boolean learnFromSongImport(String filename, Engine.MidiBars bars) {
        return this.learnFromSongImport(filename, bars, true);
    }

    boolean learnFromSongImport(String filename, Engine.MidiBars bars, boolean force) {
        return this.learnFromSongImport(filename, bars, force, "midi");
    }

    boolean learnFromSongImport(String filename, Engine.MidiBars bars, boolean force, String origin) {
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

        String stem = Engine.uniqueImportSource(Engine.stemNameFromMidi(filename), app.learned, app.learnedFills);
        Engine.FileSet madeSet = null;
        try {
            if (!Engine.isFileSetOrigin(origin)) origin = "midi";
            Engine.FileSet set = AudioIo.fileSetFromMidi(bars, stem, origin);
            Engine.attachStagedMidi(set);
            if (set.patterns.isEmpty() && set.fills.isEmpty()) return false;
            madeSet = set;
            app.fileSets.rememberFileSetParts(stem, set.parts);
            this.loadFset(Engine.encodeFset(set), Engine.fsetFilename(set.name));
        } catch (Exception ex) {
            app.setNow(ex.getMessage() != null ? ex.getMessage() : "Could not build a file set");
            return false;
        }

        // The song from the file set: parts are named after its patterns and fills (Pattern 1, Fill 1, ...).
        List<Engine.Part> parts = new ArrayList<>(Engine.songFromFileSet(madeSet));
        if (!parts.isEmpty()) {
            int ans = JOptionPane.showConfirmDialog(
                app,
                app.nowPlaying.getText() + "\n\nMake a song from these? It goes under Imported. Your original song stays.",
                "Make a song?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (ans == JOptionPane.YES_OPTION) app.songEditor.addImportedSong(stem, parts);
        }
        return true;
    }

    String selectedFileSource() {
        for (Engine.Learned x : app.learned) {
            if (x.id.equals(app.style) || x.id.equals(app.styleLibrary.learnedId())) return Engine.sourceOf(x);
        }
        if (app.fillId != null && app.fillId.startsWith("l:")) {
            String id = app.fillId.substring(2);
            for (Engine.LearnedFill x : app.learnedFills) if (id.equals(x.id)) return Engine.sourceOf(x);
        }
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<String>();
        keys.addAll(app.styleLibrary.learnedSources());
        keys.addAll(app.styleLibrary.fillSources());
        if (keys.isEmpty()) return null;
        return keys.iterator().next();
    }

    void saveFset(String source) {
        String src = source;
        if (src == null) src = this.selectedFileSource();
        if (src == null) {
            app.setNow("Pick an imported file set first");
            return;
        }
        String label = src.isEmpty() ? "Other" : src;
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
        try {
            app.projectIo.saveBytes(Engine.fsetFilename(set.name), "Pulsekit file set", "fset", Engine.encodeFset(set));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(app, "Could not save .fset: " + ex.getMessage());
        }
    }

    void loadFset(byte[] data, String filename) throws Exception {
        this.loadFset(data, filename, false);
    }

    void loadFset(byte[] data, String filename, boolean keepView) throws Exception {
        Engine.FileSet set = Engine.decodeFset(data);
        String source = Engine.importSource(filename);
        if (source == null || source.isEmpty() || "Import".equals(source)) source = set.name;
        source = Engine.uniqueImportSource(source, app.learned, app.learnedFills);
        java.util.LinkedHashMap<String, String> patternIds = new java.util.LinkedHashMap<String, String>();
        java.util.ArrayList<Engine.LearnedFill> importedFills = new java.util.ArrayList<Engine.LearnedFill>();
        String firstId = null;
        int at = 0;
        for (Engine.Learned p : set.patterns) {
            Engine.Learned item = new Engine.Learned();
            item.id = Engine.newLearnedId();
            item.name = Engine.uniqueLearnedName(p.name, Engine.learnedFrom(app.learned, source));
            item.bpm = p.bpm;
            item.closest = p.closest;
            item.cells = Engine.copyCells(p.cells);
            item.source = source;
            app.learned.add(at++, item);  // keep the file set's order: first part first
            while (app.learned.size() > Engine.MAX_LEARNED) app.learned.remove(app.learned.size() - 1);
            app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
            patternIds.put(p.name, item.id);
            if (firstId == null) firstId = item.id;
        }
        int fat = 0;
        for (Engine.LearnedFill f : set.fills) {
            Engine.LearnedFill item = new Engine.LearnedFill();
            item.id = Engine.newLearnedId();
            item.name = Engine.uniqueFillName(f.name == null || f.name.isEmpty() ? "fill" : f.name, Engine.fillsFrom(app.learnedFills, source));
            item.kind = f.kind == null ? "toms" : f.kind;
            item.cells = Engine.copyCells(f.cells);
            item.source = source;
            app.learnedFills.add(fat++, item);
            while (app.learnedFills.size() > Engine.MAX_LEARNED) app.learnedFills.remove(app.learnedFills.size() - 1);
            importedFills.add(item);
        }
        int nPair = 0;
        for (String[] row : set.fillerns) {
            String pid = patternIds.get(row[0]);
            if (pid == null) continue;
            String fk = Engine.resolveFsetFillKey(row[1], importedFills);
            app.fillernPairs.put("l:" + pid, fk);
            nPair++;
        }
        // Songs saved in the file set: kept with it, and listed under Imported songs.
        for (Engine.FileSetSong song : set.songs) {
            Engine.putFileSetSong(source, song);
            app.songEditor.addFileSetSong(source, song);
        }
        if (!set.songs.isEmpty()) app.fileSets.persistFileSetInfo();
        if (firstId != null) app.styleLibrary.loadLearned(firstId);
        else if (!importedFills.isEmpty()) app.styleLibrary.loadLearnedFill(importedFills.get(0).id);
        if (Engine.isFileSetOrigin(set.origin)) Engine.rememberFileSetOrigin(source, set.origin);
        if (set.sourceWav != null || set.combinedWav != null) {
            Engine.rememberFileSetAudio(source, set.sourceWav, set.combinedWav, set.combinedName);
            app.fileSets.storeAudioDir();
        }
        app.styleLibrary.refreshLearnedChips();
        app.persistence.persistLearned();
        java.util.List<Engine.FileSetPart> info = set.parts;
        if (info == null || info.isEmpty()) info = Engine.partsForDisplay(set);
        app.fileSets.rememberFileSetParts(source, info);
        if (!keepView) {
            app.showView(nPair > 0 ? "combo" : firstId != null ? "pattern" : "fills");
        }
        app.setNow("File set \u00b7 " + Engine.fileSetMarked(source, set.origin) + " \u00b7 " + set.patterns.size() + " patterns \u00b7 " + nPair + " Fillerns \u00b7 " + set.fills.size() + " fills");
    }
}
