package pulsekit;

import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** Patterns, fills and Fillerns: built-in, variated and imported chips and their menus. */
final class StyleLibrary {
    final Pulsekit app;

    StyleLibrary(Pulsekit app) {
        this.app = app;
    }

    void loadStyle(String string, boolean bl) {
        Engine.Style style = app.styles.get(string);
        if (style == null) {
            return;
        }
        app.style = string;
        app.styleChosen = true;
        int[][] nArray = Engine.rowsToCells(style.rows);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, app.cells[i], 0, Engine.MAX_STEPS);
        }
        Engine.zeroCells(app.lens);
        if (app.steps == Engine.MAX_STEPS) {
            Engine.tileSteps(app.cells, Engine.STEPS, Engine.MAX_STEPS);
            Engine.tileSteps(app.lens, Engine.STEPS, Engine.MAX_STEPS);
        }
        if (!bl) {
            app.tempoBar.setVal(Engine.clampBpm(style.bpm));
            app.bpmField.setText(Integer.toString(app.bpm()));
            this.applyFeel(string, null);
        }
        app.gridEditor.refreshGrid();
        this.refreshStyles();
        this.syncBuiltinFill();
        if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) {
            app.playback.rebuildAndPlay(true);
        }
        if ("combo".equals(app.view)) this.applyStoredFillern(this.patternKeyFor(string));
    }

    void applyFeel(String styleId, Engine.Learned learned) {
        if (learned == null) {
            for (Engine.Learned x : app.learned) {
                if (x.id.equals(styleId)) { learned = x; break; }
            }
        }
        String feelId = learned != null && learned.closest != null ? learned.closest : styleId;
        int swing = learned != null && learned.swing >= 0 ? learned.swing : Engine.styleSwing(feelId);
        int dens = learned != null && learned.density >= 0 ? learned.density : Engine.styleDensity(feelId);
        int human = learned != null && learned.human >= 0 ? learned.human : Engine.styleHuman(feelId);
        if (app.swingBar != null) app.swingBar.setVal(Engine.clamp(swing, 0, 75));
        if (app.densBar != null) app.densBar.setVal(Engine.clamp(dens, 1, 10));
        if (app.humanBar != null) app.humanBar.setVal(Engine.clamp(human, 0, 100));
    }

    void replicateStyle(String id) {
        Engine.Learned src = null;
        for (Engine.Learned x : app.learned) if (x.id.equals(id)) { src = x; break; }
        if (src == null) {
            for (Engine.Learned x : app.variatedPatterns) if (x.id.equals(id)) { src = x; break; }
        }
        Engine.Style st = app.styles.get(id);
        Engine.Learned item = new Engine.Learned();
        item.id = Engine.newLearnedId();
        if (src != null) {
            item.name = Engine.uniqueLearnedName(src.name + " copy", app.learned);
            item.bpm = src.bpm;
            item.cells = Engine.copyCells(src.cells);
            item.closest = src.closest;
            item.swing = src.swing >= 0 ? src.swing : app.swingBar.getVal();
            item.density = src.density >= 0 ? src.density : app.densBar.getVal();
            item.human = src.human >= 0 ? src.human : app.humanBar.getVal();
            item.source = src.source;
            item.tsNum = src.tsNum;
            item.tsDen = src.tsDen;
            item.steps = src.steps;
        } else if (st != null) {
            item.name = Engine.uniqueLearnedName(st.label + " copy", app.learned);
            item.bpm = st.bpm;
            item.cells = Engine.rowsToCells(st.rows);
            item.closest = id;
            item.swing = Engine.styleSwing(id);
            item.density = Engine.styleDensity(id);
            item.human = Engine.styleHuman(id);
        } else {
            return;
        }
        app.learned.add(0, item);
        while (app.learned.size() > Engine.MAX_LEARNED) app.learned.remove(app.learned.size() - 1);
        app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        String oldKey = null;
        for (Engine.Learned x : app.learned) if (x.id.equals(id)) { oldKey = "l:" + id; break; }
        if (oldKey == null) {
            for (Engine.Learned x : app.variatedPatterns) if (x.id.equals(id)) { oldKey = "v:" + id; break; }
        }
        if (oldKey == null && app.styles.containsKey(id) && !id.equals(item.id)) oldKey = this.patternKeyFor(id);
        String fk = oldKey == null ? null : app.fillernPairs.get(oldKey);
        if (fk != null) app.fillernPairs.put("l:" + item.id, fk);
        if (fk != null && app.fillernPicked.contains(oldKey)) app.fillernPicked.add("l:" + item.id);
        app.persistence.persistLearned();
        this.refreshLearnedChips();
        this.loadLearned(item.id);
        app.setNow("Copy \u00b7 " + item.name);
    }

    int[][] fillCellsFor(String id) {
        if (id != null && id.startsWith("l:")) {
            String lid = id.substring(2);
            for (Engine.LearnedFill f : app.learnedFills) {
                if (lid.equals(f.id)) return Engine.copyCells(f.cells);
            }
        }
        if (id != null && id.startsWith("v:")) {
            String vid = id.substring(2);
            for (Engine.LearnedFill f : app.variatedFills) {
                if (vid.equals(f.id)) return Engine.copyCells(f.cells);
            }
        }
        if (id != null && id.startsWith("p:")) {
            int slash = id.indexOf('/');
            if (slash > 2) {
                String pid = id.substring(2, slash);
                String fid = id.substring(slash + 1);
                for (Engine.Plugin p : app.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.PlugFill f : p.fills) {
                        if (fid.equals(f.id)) return Engine.copyCells(f.cells);
                    }
                }
            }
        }
        return Engine.buildFill(id, app.cells, app.style);
    }

    String fillLabel(String id) {
        if (id != null && id.startsWith("l:")) {
            String lid = id.substring(2);
            for (Engine.LearnedFill f : app.learnedFills) {
                if (lid.equals(f.id)) return f.name;
            }
        }
        if (id != null && id.startsWith("v:")) {
            String vid = id.substring(2);
            for (Engine.LearnedFill f : app.variatedFills) {
                if (vid.equals(f.id)) return f.name;
            }
        }
        if (id != null && id.startsWith("p:")) {
            int slash = id.indexOf('/');
            if (slash > 2) {
                String pid = id.substring(2, slash);
                String fid = id.substring(slash + 1);
                for (Engine.Plugin p : app.plugins) {
                    if (!p.id.equals(pid)) continue;
                    for (Engine.PlugFill f : p.fills) {
                        if (fid.equals(f.id)) return f.name;
                    }
                }
            }
        }
        return Engine.fillLabel(id);
    }

    JPopupMenu variatePatternMenu() {
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

    void variatePattern(String mode) {
        int[][] next = Engine.copyCells(app.cells);
        Random rng = new Random();
        String tag = "var";
        if ("fill".equals(mode)) {
            Engine.stampFillLastBar(next, app.fillPat, app.steps, Engine.barSteps(app.tsNum, app.tsDen));
            tag = this.fillLabel(app.fillId).toLowerCase();
            app.fillLast = true;
        } else if ("random-fill".equals(mode)) {
            String fid = Engine.randomFillId(rng);
            Engine.stampFillLastBar(next, Engine.buildFill(fid, app.cells, app.style), app.steps, Engine.barSteps(app.tsNum, app.tsDen));
            tag = Engine.fillLabel(fid).toLowerCase();
            app.fillLast = true;
        } else {
            Engine.nudgePattern(next, rng, app.densBar.getVal(), app.steps);
        }
        Engine.Style st = app.styles.get(app.style);
        String base = st != null ? st.label : "Groove";
        Engine.Learned item = new Engine.Learned();
        item.id = Engine.newLearnedId();
        item.name = Engine.uniqueLearnedName(base + " " + tag, app.variatedPatterns);
        item.bpm = app.bpm();
        item.tsNum = app.tsNum;
        item.tsDen = app.tsDen;
        item.steps = app.steps;
        item.closest = app.style;
        item.cells = next;
        item.swing = app.swingBar.getVal();
        item.density = app.densBar.getVal();
        item.human = app.humanBar.getVal();
        app.variatedPatterns.add(0, item);
        while (app.variatedPatterns.size() > Engine.MAX_VARIATED) app.variatedPatterns.remove(app.variatedPatterns.size() - 1);
        app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        app.persistence.persistLearned();
        this.refreshLearnedChips();
        this.loadStyle(item.id, false);
        if (app.fillLast && "pattern".equals(app.view)) app.showView("combo");
        app.setNow("Var \u00b7 " + item.name);
        if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) app.playback.rebuildAndPlay(true);
    }

    String patternKeyFor(String id) {
        for (Engine.Learned x : app.variatedPatterns) if (x.id.equals(id)) return "v:" + id;
        for (Engine.Learned x : app.learned) if (x.id.equals(id)) return "l:" + id;
        return "s:" + id;
    }

    String currentPatternKey() {
        return this.patternKeyFor(app.style);
    }

    /** Underlined only when a fill was chosen for this pattern from the list, and that fill still exists. */
    boolean fillernUnderlined(String patternKey) {
        return this.selectedFillFor(patternKey) != null;
    }

    String fillernFillKeyOf(String patternKey) {
        String stored = app.fillernPairs.get(patternKey);
        if (stored != null && !stored.isEmpty()) return stored;
        if (patternKey.equals(this.currentPatternKey())) return app.fillId;
        return "toms";
    }

    String songPickSection(String key) {
        if (key != null && key.startsWith("v:")) return "Variated";
        if (key != null && (key.startsWith("l:") || key.startsWith("p:"))) return "Imported";
        return "Built-in";
    }

    /** The fill chosen from the list for this pattern, or null. Fills an import paired do not count. */
    String selectedFillFor(String patternKey) {
        if (patternKey == null || !app.fillernPicked.contains(patternKey)) return null;
        String stored = app.fillernPairs.get(patternKey);
        if (stored == null || stored.isEmpty()) return null;
        return Engine.fillKeyExists(stored, app.variatedFills, app.learnedFills) ? stored : null;
    }

    /** A fill chosen from the list: remembered and underlined. */
    void pickFillern(String patternKey, String fillKey) {
        if (patternKey == null || fillKey == null) return;
        app.fillernPicked.add(patternKey);
        this.rememberFillern(patternKey, fillKey);
        if ("combo".equals(app.view)) this.refreshLearnedChips();
    }

    /** Make a Fillern: a menu of the file set's patterns, each opening the fills to pair with it. */
    void createFillern(JComponent anchor, List<Engine.Learned> patterns) {
        if (patterns.isEmpty()) {
            app.setNow("This file set has no patterns");
            return;
        }
        JPopupMenu m = new JPopupMenu();
        app.addMenuHeading(m, "Fillern: choose a pattern");
        for (Engine.Learned item : patterns) {
            final Engine.Learned it = item;
            javax.swing.JMenu sub = new javax.swing.JMenu(it.name);
            this.addFillernItems(sub.getPopupMenu(), () -> this.loadLearned(it.id), this.patternKeyFor(it.id));
            m.add(sub);
        }
        m.show(anchor, 0, anchor.getHeight());
    }

    void rememberFillern(String patternKey, String fillKey) {
        if (patternKey == null || fillKey == null) return;
        app.fillernPairs.remove(patternKey);
        app.fillernPairs.put(patternKey, fillKey);
        app.persistence.persistLearned();
        this.refreshStyles();
    }

    void applyStoredFillern(String patternKey) {
        if (!"combo".equals(app.view) || patternKey == null) return;
        String fk = app.fillernPairs.get(patternKey);
        if (fk != null) this.applyFill(fk);
    }

    String fillernMenuLabel(String text, boolean on) {
        if (!on) return text;
        String safe = text.replace("&", "&").replace("<", "<");
        return "<html><u>" + safe + "</u></html>";
    }

    void addFillernItems(JPopupMenu m, Runnable loadPattern, String patternKey) {
        if (!"combo".equals(app.view)) return;
        String selected = this.selectedFillFor(patternKey);
        app.addMenuHeading(m, "Fillern type");
        String mode = this.fillernModeOf(patternKey);
        for (int i = 0; i < Engine.FILLERN_MODES.length; i++) {
            final String fm = Engine.FILLERN_MODES[i];
            JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + Engine.FILLERN_MODE_LABELS[i], fm.equals(mode)));
            it.addActionListener(e -> this.setFillernMode(patternKey, fm));
            m.add(it);
        }
        app.addMenuHeading(m, "Last-bar fill");
        app.addMenuHeading(m, "Built-in");
        for (int i = 0; i < Engine.FILL_ID.length; i++) {
            final String fid = Engine.FILL_ID[i];
            if (app.hiddenFills.contains(fid)) continue;
            JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + Engine.FILL_LABEL[i], fid.equals(selected)));
            it.addActionListener(e -> {
                loadPattern.run();
                this.applyFill(fid);
                this.pickFillern(patternKey, fid);
                if (!"combo".equals(app.view)) app.showView("combo");
            });
            m.add(it);
        }
        if (!app.variatedFills.isEmpty()) {
            app.addMenuHeading(m, "Variated");
            for (Engine.LearnedFill f : app.variatedFills) {
                final Engine.LearnedFill fill = f;
                final String fid = "v:" + fill.id;
                JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + fill.name, fid.equals(selected)));
                it.addActionListener(e -> {
                    loadPattern.run();
                    this.applyFill(fid);
                    this.pickFillern(patternKey, fid);
                    if (!"combo".equals(app.view)) app.showView("combo");
                });
                m.add(it);
            }
        }
        for (Engine.Plugin p : app.plugins) {
            if (!p.enabled || p.fills.isEmpty()) continue;
            app.addMenuHeading(m, p.name);
            for (Engine.PlugFill f : p.fills) {
                final String fid = "p:" + p.id + "/" + f.id;
                final String name = f.name;
                JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + name, fid.equals(selected)));
                it.addActionListener(e -> {
                    loadPattern.run();
                    this.applyFill(fid);
                    this.pickFillern(patternKey, fid);
                    if (!"combo".equals(app.view)) app.showView("combo");
                });
                m.add(it);
            }
        }
        java.util.LinkedHashMap<String, java.util.ArrayList<Engine.LearnedFill>> packs = new java.util.LinkedHashMap<>();
        for (Engine.LearnedFill f : app.learnedFills) {
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
            app.addMenuHeading(m, e.getKey());
            for (Engine.LearnedFill f : e.getValue()) {
                final Engine.LearnedFill fill = f;
                final String fid = "l:" + fill.id;
                JMenuItem it = new JMenuItem(this.fillernMenuLabel("  " + fill.name, fid.equals(selected)));
                it.addActionListener(ev -> {
                    loadPattern.run();
                    this.applyFill(fid);
                    this.pickFillern(patternKey, fid);
                    if (!"combo".equals(app.view)) app.showView("combo");
                });
                m.add(it);
            }
        }
    }

    void addFillernAfterEdit(JPopupMenu m, Runnable loadPattern, String patternKey) {
        if (!"combo".equals(app.view)) return;
        m.addSeparator();
        this.addFillernItems(m, loadPattern, patternKey);
    }

    JPopupMenu builtinStyleMenu(final String sid) {
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

    JPopupMenu learnedStyleMenu(final Engine.Learned item) {
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

    JPopupMenu learnedFillMenu(final Engine.LearnedFill item) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem ren = new JMenuItem("Rename");
        ren.addActionListener(e -> this.renameLearnedFill(item.id));
        JMenuItem del = new JMenuItem("Delete");
        del.addActionListener(e -> this.removeLearnedFill(item.id));
        m.add(ren);
        m.add(del);
        return m;
    }

    void renameLearned(String id) {
        Engine.Learned item = null;
        for (Engine.Learned x : app.learned) if (x.id.equals(id)) { item = x; break; }
        if (item == null) {
            for (Engine.Learned x : app.variatedPatterns) if (x.id.equals(id)) { item = x; break; }
        }
        if (item == null) return;
        String n = JOptionPane.showInputDialog(app, "Name", item.name);
        if (n == null) return;
        n = n.trim();
        if (n.isEmpty()) return;
        if (n.length() > 28) n = n.substring(0, 28);
        item.name = n;
        app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        app.persistence.persistLearned();
        this.refreshLearnedChips();
        app.setNow("Renamed \u00b7 " + n);
    }

    void renameLearnedFill(String id) {
        for (Engine.LearnedFill item : app.learnedFills) {
            if (!item.id.equals(id)) continue;
            String n = JOptionPane.showInputDialog(app, "Name", item.name);
            if (n == null) return;
            n = n.trim();
            if (n.isEmpty()) return;
            if (n.length() > 28) n = n.substring(0, 28);
            item.name = n;
            app.persistence.persistLearned();
            this.refreshLearnedChips();
            app.setNow("Renamed \u00b7 " + n);
            return;
        }
    }

    void hideStyle(String id) {
        if (!app.hiddenStyles.contains(id)) app.hiddenStyles.add(id);
        this.refreshLearnedChips();
        app.setNow("Hidden style");
    }

    void hideFill(String id) {
        if (!app.hiddenFills.contains(id)) app.hiddenFills.add(id);
        this.refreshLearnedChips();
        app.setNow("Hidden fill");
    }

    void showAllStyles() {
        app.hiddenStyles.clear();
        this.refreshLearnedChips();
        app.setNow("Styles restored");
    }

    void showAllFills() {
        app.hiddenFills.clear();
        this.refreshLearnedChips();
        app.setNow("Fills restored");
    }

    void refreshLearnedChips() {
        app.importedBar.removeAll();
        app.variatedPatternBar.removeAll();
        for (Component c : app.styleBar.getComponents()) {
            if (c instanceof JButton && "hidden-styles".equals(((JButton) c).getClientProperty("role"))) app.styleBar.remove(c);
        }
        app.importedFillBar.removeAll();
        app.variatedFillBar.removeAll();
        for (Component c : app.fillBar.getComponents()) {
            if (c instanceof JButton && ((JButton) c).getClientProperty("learned") != null) app.fillBar.remove(c);
            if (c instanceof JButton && "hidden-fills".equals(((JButton) c).getClientProperty("role"))) app.fillBar.remove(c);
        }
        for (Component c : app.styleBar.getComponents()) {
            if (!(c instanceof JButton)) continue;
            JButton b = (JButton) c;
            Object sid = b.getClientProperty("style");
            if (sid instanceof String && b.getClientProperty("plugin") == null && b.getClientProperty("learned") == null) {
                b.setVisible(!app.hiddenStyles.contains(sid));
            }
        }
        for (Component c : app.fillBar.getComponents()) {
            if (!(c instanceof JButton)) continue;
            JButton b = (JButton) c;
            Object fid = b.getClientProperty("fill");
            if (fid instanceof String && b.getClientProperty("plugin") == null && b.getClientProperty("learned") == null
                && !((String) fid).startsWith("p:")) {
                b.setVisible(!app.hiddenFills.contains(fid));
            }
        }
        for (Engine.Learned item : app.variatedPatterns) {
            final Engine.Learned it = item;
            JButton b = app.chip(it.name, false);
            b.putClientProperty("style", it.id);
            b.putClientProperty("variated", it.id);
            b.addActionListener(e -> this.loadStyle(it.id, false));
            app.onChipMenu(b, () -> {
                JPopupMenu m = new JPopupMenu();
                JMenuItem dup = new JMenuItem("Duplicate");
                dup.addActionListener(ev -> this.replicateStyle(it.id));
                JMenuItem ren = new JMenuItem("Rename");
                ren.addActionListener(ev -> this.renameLearned(it.id));
                JMenuItem del = new JMenuItem("Delete");
                del.addActionListener(ev -> {
                    app.variatedPatterns.removeIf(x -> it.id.equals(x.id));
                    app.persistence.persistLearned();
                    this.refreshLearnedChips();
                    app.setNow("Variation deleted");
                });
                m.add(dup);
                m.add(ren);
                m.add(del);
                this.addFillernAfterEdit(m, () -> this.loadStyle(it.id, false), this.patternKeyFor(it.id));
                return m;
            });
            app.variatedPatternBar.add(b);
        }
        for (Engine.Plugin p : app.plugins) {
            if (!p.enabled || p.styles.isEmpty()) continue;
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            for (Engine.Style st : p.styles) {
                final Engine.Style st0 = st;
                app.styles.put(st0.id, st0);
                JButton b = app.chip(st0.label, false);
                b.putClientProperty("style", st0.id);
                b.putClientProperty("plugin", p.id);
                b.addActionListener(e -> this.loadStyle(st0.id, false));
                kids.add(b);
                if (st0.id.equals(app.style)) selected = true;
            }
            final String pid = p.id;
            app.importedBar.add(this.importPack("p:" + pid, p.name, p.styles.size(), selected, "Remove pack",
                () -> { app.projectIo.uninstallPlugin(pid); app.setNow("Removed \u00b7 " + p.name); }, kids));
        }
        // The Pattern tab lists a file set's patterns, the Fillern tab its Fillerns.
        boolean fillerns = "combo".equals(app.view);
        app.importedFor = fillerns ? "combo" : "pattern";
        for (String src : this.learnedSources()) {
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            int n = 0;
            List<Engine.Learned> setPatterns = new ArrayList<Engine.Learned>();
            for (Engine.Learned item : app.learned) if (src.equals(Engine.sourceOf(item))) setPatterns.add(item);
            for (Engine.Learned item : app.learned) {
                if (!src.equals(Engine.sourceOf(item))) continue;
                if (fillerns && this.selectedFillFor(this.patternKeyFor(item.id)) == null) continue;
                final Engine.Learned it = item;
                String chipName = fillerns
                    ? it.name + " \u00b7 " + this.fillLabel(this.selectedFillFor(this.patternKeyFor(it.id))) + Engine.fillernModeNote(this.fillernModeOf(this.patternKeyFor(it.id)))
                    : it.name;
                JButton b = app.chip(chipName, false);
                b.putClientProperty("style", it.id);
                b.putClientProperty("learned", it.id);
                b.addActionListener(e -> this.loadLearned(it.id));
                app.onChipMenu(b, () -> this.learnedStyleMenu(it));
                kids.add(b);
                n++;
                if (it.id.equals(app.style) || it.id.equals(this.learnedId())) selected = true;
            }
            if (fillerns) {
                if (n == 0) {
                    JButton none = new JButton("No fillerns yet, create one");
                    app.flatten(none);
                    none.setBackground(BG);
                    none.setForeground(MUTED);
                    none.putClientProperty("role", "fillern-none");
                    none.addActionListener(e -> this.createFillern(none, setPatterns));
                    kids.add(none);
                }
                JButton add = app.chip("+ Fillern", false);
                add.putClientProperty("role", "fillern-add");
                add.addActionListener(e -> this.createFillern(add, setPatterns));
                kids.add(add);
            }
            String label = src.isEmpty() ? "Other" : src;
            app.importedBar.add(this.importPack(src.isEmpty() ? "o:other" : "f:" + src, label, n, selected, "Delete file set",
                () -> app.importLibrary.removeImportSource(src), () -> app.importLibrary.saveFset(src), kids));
        }
        if (!app.hiddenStyles.isEmpty()) {
            JButton b = app.chip("Hidden " + app.hiddenStyles.size(), false);
            b.putClientProperty("role", "hidden-styles");
            b.addActionListener(e -> this.showAllStyles());
            app.styleBar.add(b);
        }
        for (Engine.LearnedFill item : app.variatedFills) {
            final Engine.LearnedFill it = item;
            JButton b = app.chip(it.name, false);
            b.putClientProperty("fill", "v:" + it.id);
            b.putClientProperty("variated", it.id);
            b.addActionListener(e -> this.applyFill("v:" + it.id));
            app.onRightClick(b, () -> {
                app.variatedFills.removeIf(x -> it.id.equals(x.id));
                app.persistence.persistLearned();
                this.refreshLearnedChips();
                app.setNow("Variation deleted");
            });
            app.variatedFillBar.add(b);
        }
        for (Engine.Plugin p : app.plugins) {
            if (!p.enabled || p.fills.isEmpty()) continue;
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            for (Engine.PlugFill f : p.fills) {
                final Engine.PlugFill f0 = f;
                final String fid = "p:" + p.id + "/" + f0.id;
                JButton b = app.chip(f0.name, false);
                b.putClientProperty("fill", fid);
                b.putClientProperty("plugin", p.id);
                b.addActionListener(e -> this.applyFill(fid));
                kids.add(b);
                if (fid.equals(app.fillId)) selected = true;
            }
            final String pid = p.id;
            app.importedFillBar.add(this.importPack("p:" + pid, p.name, p.fills.size(), selected, "Remove pack",
                () -> { app.projectIo.uninstallPlugin(pid); app.setNow("Removed \u00b7 " + p.name); }, kids));
        }
        // Every file set, also one without fills yet.
        for (String src : Engine.fileSetSources(app.learned, app.learnedFills)) {
            ChipStrip kids = new ChipStrip();
            boolean selected = false;
            int n = 0;
            for (Engine.LearnedFill item : app.learnedFills) {
                if (!src.equals(Engine.sourceOf(item))) continue;
                final Engine.LearnedFill it = item;
                JButton b = app.chip(it.name, false);
                b.putClientProperty("fill", "l:" + it.id);
                b.putClientProperty("learned", it.id);
                b.addActionListener(e -> this.loadLearnedFill(it.id));
                app.onChipMenu(b, () -> this.learnedFillMenu(it));
                kids.add(b);
                n++;
                if (("l:" + it.id).equals(app.fillId)) selected = true;
            }
            if (n == 0) {
                JButton none = new JButton("No fills yet, add one");
                app.flatten(none);
                none.setBackground(BG);
                none.setForeground(MUTED);
                none.putClientProperty("role", "fills-none");
                none.addActionListener(e -> this.pickBuiltinFillFor(none, src));
                kids.add(none);
            }
            JButton addFill = app.chip("+ Fill", false);
            addFill.putClientProperty("role", "fills-add");
            addFill.addActionListener(e -> this.pickBuiltinFillFor(addFill, src));
            kids.add(addFill);
            String label = src.isEmpty() ? "Other" : src;
            app.importedFillBar.add(this.importPack(src.isEmpty() ? "o:other" : "f:" + src, label, n, selected, "Delete file set",
                () -> app.importLibrary.removeImportSource(src), () -> app.importLibrary.saveFset(src), kids));
        }
        if (!app.hiddenFills.isEmpty()) {
            JButton b = app.chip("Hidden " + app.hiddenFills.size(), false);
            b.putClientProperty("role", "hidden-fills");
            b.addActionListener(e -> this.showAllFills());
            app.fillBar.add(b);
        }
        app.styleBar.revalidate();
        app.styleBar.repaint();
        app.importedBar.revalidate();
        app.importedBar.repaint();
        app.variatedPatternBar.revalidate();
        app.variatedPatternBar.repaint();
        app.fillBar.revalidate();
        app.fillBar.repaint();
        app.variatedFillBar.revalidate();
        app.variatedFillBar.repaint();
        app.importedFillBar.revalidate();
        app.importedFillBar.repaint();
        app.importLibrary.refreshImportedFiles();
        this.refreshStyles();
        this.refreshFills();
    }

    String learnedId() {
        return app.style;
    }

    java.util.LinkedHashSet<String> learnedSources() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        for (Engine.Learned x : app.learned) out.add(Engine.sourceOf(x));
        return out;
    }

    java.util.LinkedHashSet<String> fillSources() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        for (Engine.LearnedFill x : app.learnedFills) out.add(Engine.sourceOf(x));
        return out;
    }

    boolean packOpen(String key, boolean fallback) {
        Boolean v = app.openPacks.get(key);
        return v != null ? v : fallback;
    }

    JPanel importPack(String key, String label, int count, boolean selected, String deleteLabel, Runnable onDelete, JPanel kids) {
        return this.importPack(key, label, count, selected, deleteLabel, onDelete, null, kids);
    }

    JPanel importPack(String key, String label, int count, boolean selected, String deleteLabel, Runnable onDelete, Runnable onExport, JPanel kids) {
        boolean open = this.packOpen(key, selected);
        String shown = label;
        if (key != null && key.startsWith("f:")) shown = Engine.fileSetMarked(label, Engine.fileSetOriginOf(key.substring(2)));
        JPanel pack = new JPanel();
        pack.setOpaque(false);
        pack.setLayout(new BoxLayout(pack, BoxLayout.Y_AXIS));
        pack.setAlignmentX(0f);
        JButton head = app.chip((open ? "\u25BE " : "\u25B8 ") + shown + " \u00b7 " + count, selected);
        head.putClientProperty("pack", key);
        head.addActionListener(e -> {
            app.openPacks.put(key, !this.packOpen(key, selected));
            this.refreshLearnedChips();
        });
        app.onChipMenu(head, () -> {
            JPopupMenu m = new JPopupMenu();
            if (onExport != null) {
                JMenuItem info = new JMenuItem("Info");
                info.addActionListener(ev -> app.fileSets.openFileSetInfo(key, label));
                m.add(info);
                JMenuItem make = new JMenuItem("Make song");
                make.addActionListener(ev -> app.fileSets.makeFileSetSong(key, label));
                m.add(make);
                String origin = key != null && key.startsWith("f:") ? Engine.fileSetOriginOf(key.substring(2)) : "";
                if (Engine.fileSetStyleOn(origin)) {
                    JMenuItem style = new JMenuItem("Change style");
                    style.addActionListener(ev -> app.fileSets.promptChangeStyle(key, label));
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

    void loadLearned(String id) {
        for (Engine.Learned item : app.learned) {
            if (!item.id.equals(id)) continue;
            this.loadStyle(item.id, false);
            app.tsNum = Engine.clampTsNum(item.tsNum);
            app.tsDen = Engine.clampTsDen(item.tsDen);
            app.tsNumField.setText(Integer.toString(app.tsNum));
            app.tsDenField.setText(Integer.toString(app.tsDen));
            app.gridEditor.applySteps(Engine.clampSteps(item.steps), false);
            app.setNow("Learned \u00b7 " + item.name + (item.closest != null ? " \u00b7 like " + item.closest : ""));
            return;
        }
    }

    void loadLearnedFill(String id) {
        for (Engine.LearnedFill item : app.learnedFills) {
            if (!item.id.equals(id)) continue;
            this.applyFill("l:" + item.id);
            return;
        }
    }

    void removeLearned(String id) {
        app.learned.removeIf(x -> id.equals(x.id));
        this.refreshLearnedChips();
        app.persistence.persistLearned();
        app.setNow("Style deleted");
    }

    void removeLearnedFill(String id) {
        app.learnedFills.removeIf(x -> id.equals(x.id));
        this.refreshLearnedChips();
        app.persistence.persistLearned();
        app.setNow("Fill deleted");
    }

    void applyFill(String string) {
        app.fillId = string;
        int[][] nArray = this.fillCellsFor(string);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, app.fillPat[i], 0, 16);
        }
        Engine.zeroCells(app.fillLens);
        this.refreshFills();
        app.fillVariated = string != null && string.startsWith("v:");
        app.setNow(this.fillLabel(string));
        if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) {
            app.fillLast = true;
            app.playback.rebuildAndPlay(true);
        }
    }

    boolean customFill() {
        return app.fillId != null && (app.fillId.startsWith("l:") || app.fillId.startsWith("v:") || app.fillId.startsWith("p:"));
    }

    void syncBuiltinFill() {
        if (this.customFill()) return;
        int[][] src = Engine.buildFill(app.fillId, app.cells, app.style);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(src[i], 0, app.fillPat[i], 0, 16);
        }
    }

    /** Right click on a built-in fill: copy it, or a variation of it, into a file set; or hide it. */
    void builtinFillMenu(JComponent anchor, String fillId) {
        JPopupMenu m = new JPopupMenu();
        javax.swing.JMenu copy = new javax.swing.JMenu("Copy fill to file set");
        javax.swing.JMenu variated = new javax.swing.JMenu("Variated fill into file set");
        List<String> sources = Engine.fileSetSources(app.learned, app.learnedFills);
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
    void pickBuiltinFillFor(JComponent anchor, String source) {
        JPopupMenu m = new JPopupMenu();
        app.addMenuHeading(m, "Add a fill to " + (source.isEmpty() ? "Other" : source));
        for (int i = 0; i < Engine.FILL_ID.length; i++) {
            final String fid = Engine.FILL_ID[i];
            if (app.hiddenFills.contains(fid)) continue;
            JMenuItem it = new JMenuItem(Engine.FILL_LABEL[i]);
            it.addActionListener(e -> this.copyFillToFileSet(fid, source, false));
            m.add(it);
        }
        m.show(anchor, 0, anchor.getHeight());
    }

    /** Adds a built-in fill, as it sounds with the current pattern, to a file set; with variate, a variation of it. */
    void copyFillToFileSet(String fillId, String source, boolean variate) {
        int[][] cells = Engine.copyCells(this.fillCellsFor(fillId));
        if (variate) cells = Engine.variateFillCells(cells, new Random());
        Engine.LearnedFill fill = new Engine.LearnedFill();
        fill.id = Engine.newLearnedId();
        fill.kind = Engine.isFillId(fillId) ? fillId : "toms";
        fill.name = Engine.uniqueFillName(this.fillLabel(fillId) + (variate ? " var" : ""), Engine.fillsFrom(app.learnedFills, source));
        fill.cells = cells;
        fill.source = source;
        app.learnedFills.add(0, fill);
        while (app.learnedFills.size() > Engine.MAX_LEARNED) app.learnedFills.remove(app.learnedFills.size() - 1);
        app.persistence.persistLearned();
        this.refreshLearnedChips();
        this.applyFill("l:" + fill.id);
        app.setNow(fill.name + " \u00b7 " + (source.isEmpty() ? "Other" : source));
    }

    void variateFill() {
        Random random = new Random();
        for (int i = Engine.track("ltom"); i < Engine.TRACK_ID.length; ++i) {
            for (int j = 8; j < 16; ++j) {
                if (!(random.nextDouble() < 0.22)) continue;
                app.fillPat[i][j] = app.fillPat[i][j] > 0 ? 0 : (random.nextBoolean() ? 100 : 127);
            }
        }
        Engine.LearnedFill item = new Engine.LearnedFill();
        item.id = Engine.newLearnedId();
        item.kind = app.fillId != null && app.fillId.length() < 12 ? app.fillId : "toms";
        if (item.kind.startsWith("l:") || item.kind.startsWith("v:") || item.kind.startsWith("p:")) item.kind = "toms";
        item.name = Engine.uniqueFillName(Engine.fillLabel(item.kind) + " var", app.variatedFills);
        item.cells = Engine.copyCells(app.fillPat);
        app.variatedFills.add(0, item);
        while (app.variatedFills.size() > Engine.MAX_VARIATED) app.variatedFills.remove(app.variatedFills.size() - 1);
        app.fillId = "v:" + item.id;
        app.fillVariated = true;
        app.persistence.persistLearned();
        this.refreshLearnedChips();
        this.refreshFills();
        app.setNow(item.name);
        if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) {
            app.fillLast = true;
            app.playback.rebuildAndPlay(true);
        }
    }

    void refreshStyles() {
        for (JPanel bar : new JPanel[] { app.styleBar, app.variatedPatternBar, app.importedBar }) {
            app.walkChips(bar, jButton -> {
                Object sid = jButton.getClientProperty("style");
                boolean on = app.styleChosen && app.style.equals(sid);
                app.paintChip(jButton, on);
                boolean under = false;
                if ("combo".equals(app.view) && sid instanceof String) {
                    String pk = this.patternKeyFor((String) sid);
                    under = this.fillernUnderlined(pk);
                }
                app.underlineChip(jButton, under);
            });
        }
    }

    void refreshFills() {
        for (JPanel bar : new JPanel[] { app.fillBar, app.variatedFillBar, app.importedFillBar }) {
            app.walkChips(bar, jButton -> {
                if (jButton.getClientProperty("fill") == null) return;
                app.paintChip(jButton, app.fillId.equals(jButton.getClientProperty("fill")));
            });
        }
    }

    String fillernModeOf(String patternKey) {
        return Engine.fillernModeOr(patternKey == null ? null : app.fillernModes.get(patternKey));
    }

    void setFillernMode(String patternKey, String mode) {
        if (patternKey == null) return;
        String m = Engine.fillernMode(mode);
        app.fillernModes.put(patternKey, m);  // its own type, whatever the default is later
        app.persistence.persistLearned();
        if ("combo".equals(app.view)) this.refreshLearnedChips();
        app.setNow(Engine.FILLERN_MODE_LABELS[java.util.Arrays.asList(Engine.FILLERN_MODES).indexOf(m)]);
    }

    void addPluginStyle(Engine.Style st) {
        app.styles.put(st.id, st);
        this.refreshLearnedChips();
    }
}
