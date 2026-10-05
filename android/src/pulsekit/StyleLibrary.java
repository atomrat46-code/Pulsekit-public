package pulsekit;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static pulsekit.MainActivity.*;

/** Patterns, fills and Fillerns: built-in, variated and imported chips and their menus. */
final class StyleLibrary {
    final MainActivity app;

    StyleLibrary(MainActivity app) {
        this.app = app;
    }

    /**
     * The imported file sets scroll inside at most a third of the screen, so a long list
     * does not push the knobs and the grid off the screen.
     */
    View cappedScroll(View content) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(app) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int cap = (int) (getResources().getDisplayMetrics().heightPixels * 0.33f);
                int size = View.MeasureSpec.getSize(heightSpec);
                if (View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED || size > cap) size = cap;
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setTag("imported-scroll");
        scroll.setFillViewport(false);
        scroll.addView(content);
        return scroll;
    }

    /** Builds the Built-in / Variated / Imported chip strips for patterns and for fills. */
    void buildStyleStrips() {
        TextView textView4;
        app.styleWrap = app.col();
        app.styleWrap.addView((View)app.sectionLabel("Built-in"));
        app.styleBar = new FlowLayout((Context)app, app.dp(6), app.dp(6));
        app.styleBar.setSingleLine(true);
        for (Engine.Style style2 : app.styles.values()) {
            TextView textView10 = app.pill(style2.label, false, view -> this.loadStyle(style2.id, false));
            textView10.setTag((Object)style2.id);
            this.attachBuiltinStyleMenu(textView10, style2.id);
            app.styleBar.addView((View)textView10);
        }
        app.styleWrap.addView((View)app.chipStrip((View)app.styleBar));
        app.styleWrap.addView((View)app.sectionLabel("Variated"));
        app.variatedPatternBar = new FlowLayout((Context)app, app.dp(6), app.dp(6));
        app.variatedPatternBar.setSingleLine(true);
        TextView textView11 = app.text("Variate a groove or add a fill", 12, false);
        textView11.setTextColor(MUTED);
        textView11.setTag((Object)"imported-empty");
        app.variatedPatternBar.addView((View)textView11);
        app.styleWrap.addView((View)app.chipStrip((View)app.variatedPatternBar));
        app.styleWrap.addView((View)app.sectionLabel("Imported"));
        app.importedHost = app.col();
        TextView emptyStyles = app.text("MIDI, song or plugin pack", 12, false);
        emptyStyles.setTextColor(MUTED);
        emptyStyles.setTag("imported-empty");
        app.importedHost.addView((View)emptyStyles);
        app.styleWrap.addView((View)this.cappedScroll(app.importedHost));
        app.chrome.addView((View)app.styleWrap);
        app.fillWrap = app.col();
        app.fillWrap.addView((View)app.sectionLabel("Built-in"));
        app.fillBar = new FlowLayout((Context)app, app.dp(6), app.dp(6));
        app.fillBar.setSingleLine(true);
        for (int i = 0; i < Engine.FILL_ID.length; ++i) {
            String string = Engine.FILL_ID[i];
            textView4 = app.pill(Engine.FILL_LABEL[i], false, view -> this.applyFill(string));
            textView4.setTag((Object)string);
            this.attachBuiltinFillMenu(textView4, string);
            app.fillBar.addView((View)textView4);
        }
        app.fillBar.addView((View)app.pill("Variate", false, view -> this.variateFill()));
        app.fillBar.addView((View)app.pill("Apply", false, view -> app.show("combo")));
        app.fillWrap.addView((View)app.chipStrip((View)app.fillBar));
        app.fillWrap.addView((View)app.sectionLabel("Variated"));
        app.variatedFillBar = new FlowLayout((Context)app, app.dp(6), app.dp(6));
        app.variatedFillBar.setSingleLine(true);
        TextView textView12 = app.text("Variate to keep a copy here", 12, false);
        textView12.setTextColor(MUTED);
        textView12.setTag((Object)"imported-empty");
        app.variatedFillBar.addView((View)textView12);
        app.fillWrap.addView((View)app.chipStrip((View)app.variatedFillBar));
        app.fillWrap.addView((View)app.sectionLabel("Imported"));
        app.importedFillHost = app.col();
        TextView emptyFills = app.text("Last bar of an imported MIDI", 12, false);
        emptyFills.setTextColor(MUTED);
        emptyFills.setTag((Object)"imported-empty");
        app.importedFillHost.addView((View)emptyFills);
        app.fillWrap.addView((View)this.cappedScroll(app.importedFillHost));
        app.fillWrap.setVisibility(8);
        app.chrome.addView((View)app.fillWrap);
    }

    final List<String> hiddenStyles = new ArrayList<String>();

    final List<String> hiddenFills = new ArrayList<String>();

    String variatedPatternId;

    void applyFill(String string) {
        app.fillId = string;
        int[][] nArray = this.fillCellsFor(string);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, app.fillPat[i], 0, 16);
        }
        Engine.zeroCells(app.fillLens);
        boolean bl = app.fillVariated = string != null && string.startsWith("v:");
        if (app.playing && !app.songPlay) {
            app.fillLast = true;
            app.barLoop = Math.max(0, app.bars - 1);
        }
        this.refreshFills();
        app.setNow(this.fillLabel(string));
    }

    boolean customFill() {
        return app.fillId != null && (app.fillId.startsWith("l:") || app.fillId.startsWith("v:") || app.fillId.startsWith("p:"));
    }

    void syncBuiltinFill() {
        if (this.customFill()) {
            return;
        }
        int[][] nArray = Engine.buildFill(app.fillId, app.cells, app.style);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, app.fillPat[i], 0, 16);
        }
    }

    int[][] fillCellsFor(String string) {
        String string2;
        if (string != null && string.startsWith("l:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : app.learnedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return Engine.copyCells(learnedFill.cells);
            }
        }
        if (string != null && string.startsWith("v:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : app.variatedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return Engine.copyCells(learnedFill.cells);
            }
        }
        return Engine.buildFill(string, app.cells, app.style);
    }

    String fillLabel(String string) {
        String string2;
        if (string != null && string.startsWith("l:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : app.learnedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return learnedFill.name;
            }
        }
        if (string != null && string.startsWith("v:")) {
            string2 = string.substring(2);
            for (Engine.LearnedFill learnedFill : app.variatedFills) {
                if (!string2.equals(learnedFill.id)) continue;
                return learnedFill.name;
            }
        }
        return Engine.fillLabel(string);
    }

    /** Long press on a built-in fill: copy it, or a variation of it, into a file set. */
    void attachBuiltinFillMenu(TextView textView, String fillId) {
        textView.setOnLongClickListener(view -> {
            new AlertDialog.Builder((Context)app).setTitle((CharSequence)this.fillLabel(fillId)).setItems(new CharSequence[]{"Copy fill to file set", "Variated fill into file set"}, (dialogInterface, n) -> this.chooseFileSetFor(fillId, n == 1)).setNegativeButton((CharSequence)"Cancel", null).show();
            return true;
        });
    }

    void chooseFileSetFor(String fillId, boolean variate) {
        List<String> sources = Engine.fileSetSources(app.learned, app.learnedFills);
        if (sources.isEmpty()) {
            app.setNow("Import a MIDI to make a file set first");
            return;
        }
        String[] names = new String[sources.size()];
        for (int i = 0; i < names.length; i++) names[i] = sources.get(i).isEmpty() ? "Other" : sources.get(i);
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)(variate ? "Variated fill into file set" : "Copy fill to file set")).setItems((CharSequence[])names, (dialogInterface, n) -> {
            if (n >= 0 && n < sources.size()) this.copyFillToFileSet(fillId, sources.get(n), variate);
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    /** "No fills yet, add one" and "+ Fill": choose a built-in fill to copy into this file set. */
    void pickBuiltinFillFor(String source) {
        ArrayList<String> ids = new ArrayList<String>();
        ArrayList<CharSequence> names = new ArrayList<CharSequence>();
        for (int i = 0; i < Engine.FILL_ID.length; ++i) {
            if (this.hiddenFills.contains(Engine.FILL_ID[i])) continue;
            ids.add(Engine.FILL_ID[i]);
            names.add(Engine.FILL_LABEL[i]);
        }
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)("Add a fill to " + (source.isEmpty() ? "Other" : source))).setItems(names.toArray(new CharSequence[0]), (dialogInterface, n) -> {
            if (n >= 0 && n < ids.size()) this.copyFillToFileSet(ids.get(n), source, false);
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    /** Adds a built-in fill, as it sounds with the current pattern, to a file set; with variate, a variation of it. */
    Engine.LearnedFill copyFillToFileSet(String fillId, String source, boolean variate) {
        int[][] cells = Engine.copyCells(this.fillCellsFor(fillId));
        if (variate) cells = Engine.variateFillCells(cells, new Random());
        Engine.LearnedFill fill = new Engine.LearnedFill();
        fill.id = Engine.newLearnedId();
        fill.kind = Engine.isFillId(fillId) ? fillId : "toms";
        fill.name = Engine.uniqueFillName(this.fillLabel(fillId) + (variate ? " var" : ""), Engine.fillsFrom(app.learnedFills, source));
        fill.cells = cells;
        fill.source = source;
        app.learnedFills.add(0, fill);
        while (app.learnedFills.size() > Engine.MAX_LEARNED) {
            app.learnedFills.remove(app.learnedFills.size() - 1);
        }
        app.persistence.persistLearned();
        app.importLibrary.rebuildImportedFills();
        this.applyFill("l:" + fill.id);
        app.setNow(fill.name + " \u00b7 " + (source.isEmpty() ? "Other" : source));
        return fill;
    }

    void variateFill() {
        Random random = new Random();
        for (int i = 8; i < Engine.TRACK_ID.length; ++i) {
            for (int j = 8; j < 16; ++j) {
                if (!(random.nextDouble() < 0.22)) continue;
                app.fillPat[i][j] = app.fillPat[i][j] > 0 ? 0 : (random.nextBoolean() ? 100 : 127);
            }
        }
        Engine.LearnedFill learnedFill = new Engine.LearnedFill();
        learnedFill.id = Engine.newLearnedId();
        learnedFill.kind = app.fillId.startsWith("l:") || app.fillId.startsWith("v:") ? "toms" : app.fillId;
        learnedFill.name = Engine.uniqueFillName(Engine.fillLabel(learnedFill.kind) + " var", app.variatedFills);
        learnedFill.cells = Engine.copyCells(app.fillPat);
        app.variatedFills.add(0, learnedFill);
        while (app.variatedFills.size() > 8) {
            app.variatedFills.remove(app.variatedFills.size() - 1);
        }
        this.addVariatedFillChip(learnedFill);
        app.persistence.persistLearned();
        app.fillId = "v:" + learnedFill.id;
        app.fillVariated = true;
        this.refreshFills();
        app.setNow(learnedFill.name);
    }

    void refreshFills() {
        this.paintFillBar(app.fillBar);
        this.paintFillBar(app.variatedFillBar);
        if (app.importedFillHost != null) {
            this.paintFillGroup((ViewGroup)app.importedFillHost);
        }
    }

    void paintFillGroup(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); ++i) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof TextView && view.getTag() != null && !"imported-empty".equals(view.getTag()) && !"pack".equals(view.getTag())) {
                app.paintChip((TextView)view, app.fillId.equals(view.getTag()));
                continue;
            }
            if (!(view instanceof ViewGroup)) continue;
            this.paintFillGroup((ViewGroup)view);
        }
    }

    void paintFillBar(FlowLayout flowLayout) {
        if (flowLayout == null) {
            return;
        }
        for (int i = 0; i < flowLayout.getChildCount(); ++i) {
            View view = flowLayout.getChildAt(i);
            if (!(view instanceof TextView) || view.getTag() == null || "imported-empty".equals(view.getTag())) continue;
            app.paintChip((TextView)view, app.fillId.equals(view.getTag()));
        }
    }

    String currentPatternKey() {
        return this.patternKeyFor(app.style);
    }

    void loadStyle(String string, boolean bl) {
        Engine.Style style = app.styles.get(string);
        if (style == null) {
            return;
        }
        app.style = string;
        app.styleChosen = true;
        // A style in another meter (Ballad 6/8, Slow Blues 12/8) sets the time signature; a 4/4 style
        // after it sets 4/4 back, but a time signature set by hand stays.
        int[] meter = Engine.styleMeter(string);
        boolean other = meter[0] != 4 || meter[1] != 4;
        if (other && (app.tsNum != meter[0] || app.tsDen != meter[1])) {
            app.gridEditor.applyTimeSig(meter[0], meter[1]);
            app.tsFromStyle = true;
        } else if (!other && app.tsFromStyle) {
            app.gridEditor.applyTimeSig(4, 4);
            app.tsFromStyle = false;
        } else if (other) {
            app.tsFromStyle = true;
        }
        int[][] nArray = Engine.rowsToCells(style.rows);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, app.cells[i], 0, 32);
        }
        Engine.zeroCells(app.lens);
        if (!bl && app.bpmBar != null) {
            app.bpmBar.setVal(Engine.clampBpm(style.bpm));
            app.bpmLabel.setText((CharSequence)Integer.toString(style.bpm));
            this.applyFeel(string, null);
        }
        this.variatedPatternId = null;
        for (Engine.Learned learned : app.variatedPatterns) {
            if (!learned.id.equals(string)) continue;
            this.variatedPatternId = string;
            break;
        }
        this.syncBuiltinFill();
        this.refreshStyles();
        app.gridEditor.refreshGrid();
        if ("combo".equals(app.view)) {
            this.applyStoredFillern(this.patternKeyFor(string));
        }
    }

    void variatePattern(String string) {
        Object object;
        int[][] nArray = Engine.copyCells(app.cells);
        Random random = new Random();
        String string2 = "var";
        if ("fill".equals(string)) {
            Engine.stampFillLastBar(nArray, app.fillPat, app.steps, Engine.barSteps(app.tsNum, app.tsDen));
            string2 = this.fillLabel(app.fillId).toLowerCase();
            app.fillLast = true;
        } else if ("random-fill".equals(string)) {
            object = Engine.randomFillId(random);
            Engine.stampFillLastBar(nArray, Engine.buildFill((String)object, app.cells, app.style), app.steps, Engine.barSteps(app.tsNum, app.tsDen));
            string2 = Engine.fillLabel((String)object).toLowerCase();
            app.fillLast = true;
        } else {
            Engine.nudgePattern(nArray, random, app.density(), 16);
            if (app.pluginGhostHats) {
                Engine.ghostHats(nArray, random);
            }
        }
        object = app.styles.get(app.style);
        String string3 = object != null ? ((Engine.Style)object).label : "Groove";
        Engine.Learned learned = new Engine.Learned();
        learned.id = Engine.newLearnedId();
        learned.name = Engine.uniqueLearnedName(string3 + " " + string2, app.variatedPatterns);
        learned.bpm = app.bpm();
        learned.closest = app.style;
        learned.cells = nArray;
        learned.swing = app.swing();
        learned.density = app.density();
        learned.human = app.human();
        app.variatedPatterns.add(0, learned);
        while (app.variatedPatterns.size() > 8) {
            app.variatedPatterns.remove(app.variatedPatterns.size() - 1);
        }
        app.styles.put(learned.id, new Engine.Style(learned.id, learned.name, learned.bpm, Engine.rowsFromCells(learned.cells)));
        this.addVariatedPatternChip(learned);
        app.persistence.persistLearned();
        this.loadStyle(learned.id, false);
        if (app.fillLast && "pattern".equals(app.view)) {
            app.show("combo");
        }
        app.setNow("Var \u00b7 " + learned.name);
    }

    void addVariatedPatternChip(Engine.Learned learned) {
        if (app.variatedPatternBar == null) {
            return;
        }
        for (int i = 0; i < app.variatedPatternBar.getChildCount(); ++i) {
            if (!learned.id.equals(app.variatedPatternBar.getChildAt(i).getTag())) continue;
            return;
        }
        TextView textView = app.pill(learned.name, false, view -> this.loadStyle(learned.id, false));
        textView.setTag((Object)learned.id);
        textView.setOnLongClickListener(view -> {
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            arrayList.add("Duplicate");
            arrayList2.add(() -> this.replicateStyle(learned.id));
            arrayList.add("Rename");
            arrayList2.add(() -> this.promptRename(learned.name, string -> {
                learned.name = string;
                textView.setText((CharSequence)string);
                app.persistence.persistLearned();
            }));
            arrayList.add("Delete");
            arrayList2.add(() -> {
                app.variatedPatterns.remove(learned);
                app.variatedPatternBar.removeView((View)textView);
                app.persistence.persistLearned();
                this.syncVariatedPatternEmpty();
                if (learned.id.equals(this.variatedPatternId)) {
                    this.variatedPatternId = null;
                }
                app.setNow("Variation deleted");
            });
            this.showEntryMenu(learned.name, this.patternKeyFor(learned.id), () -> this.loadStyle(learned.id, false), arrayList, arrayList2);
            return true;
        });
        app.variatedPatternBar.addView((View)textView);
        this.syncVariatedPatternEmpty();
        this.refreshStyles();
    }

    void syncVariatedPatternEmpty() {
        if (app.variatedPatternBar == null) {
            return;
        }
        boolean bl = false;
        View view = null;
        for (int i = 0; i < app.variatedPatternBar.getChildCount(); ++i) {
            View view2 = app.variatedPatternBar.getChildAt(i);
            if ("imported-empty".equals(view2.getTag())) {
                view = view2;
                continue;
            }
            bl = true;
        }
        if (view != null) {
            view.setVisibility(bl ? 8 : 0);
        }
    }

    void applyFeel(String string, Engine.Learned learned) {
        int n;
        if (learned == null) {
            for (Engine.Learned learned2 : app.learned) {
                if (!learned2.id.equals(string)) continue;
                learned = learned2;
                break;
            }
        }
        String string2 = learned != null && learned.closest != null ? learned.closest : string;
        int n2 = learned != null && learned.swing >= 0 ? learned.swing : Engine.styleSwing(string2);
        int n3 = learned != null && learned.density >= 0 ? learned.density : Engine.styleDensity(string2);
        int n4 = n = learned != null && learned.human >= 0 ? learned.human : Engine.styleHuman(string2);
        if (app.swingBar != null) {
            app.swingBar.setVal(Engine.clamp(n2, 0, 75));
        }
        if (app.densBar != null) {
            app.densBar.setVal(Engine.clamp(n3, 1, 10));
        }
        if (app.humanBar != null) {
            app.humanBar.setVal(Engine.clamp(n, 0, 100));
        }
    }

    void replicateStyle(String string) {
        Engine.Learned learned3 = null;
        for (Engine.Learned learned : app.learned) {
            if (!learned.id.equals(string)) continue;
            learned3 = learned;
            break;
        }
        if (learned3 == null) {
            for (Engine.Learned learned : app.variatedPatterns) {
                if (!learned.id.equals(string)) continue;
                learned3 = learned;
                break;
            }
        }
        Engine.Style style = app.styles.get(string);
        Engine.Learned learned22 = new Engine.Learned();
        learned22.id = Engine.newLearnedId();
        if (learned3 != null) {
            learned22.name = Engine.uniqueLearnedName(learned3.name + " copy", app.learned);
            learned22.bpm = learned3.bpm;
            learned22.cells = Engine.copyCells(learned3.cells);
            learned22.closest = learned3.closest;
            learned22.swing = learned3.swing >= 0 ? learned3.swing : app.swing();
            learned22.density = learned3.density >= 0 ? learned3.density : app.density();
            learned22.human = learned3.human >= 0 ? learned3.human : app.human();
            learned22.source = learned3.source;
        } else if (style != null) {
            learned22.name = Engine.uniqueLearnedName(style.label + " copy", app.learned);
            learned22.bpm = style.bpm;
            learned22.cells = Engine.rowsToCells(style.rows);
            learned22.closest = string;
            learned22.swing = Engine.styleSwing(string);
            learned22.density = Engine.styleDensity(string);
            learned22.human = Engine.styleHuman(string);
        } else {
            return;
        }
        app.learned.add(0, learned22);
        while (app.learned.size() > Engine.MAX_LEARNED) {
            app.learned.remove(app.learned.size() - 1);
        }
        app.styles.put(learned22.id, new Engine.Style(learned22.id, learned22.name, learned22.bpm, Engine.rowsFromCells(learned22.cells)));
        String string2 = null;
        for (Engine.Learned learned : app.learned) {
            if (!learned.id.equals(string)) continue;
            string2 = "l:" + string;
            break;
        }
        if (string2 == null) {
            for (Engine.Learned learned : app.variatedPatterns) {
                if (!learned.id.equals(string)) continue;
                string2 = "v:" + string;
                break;
            }
        }
        if (string2 == null) {
            string2 = this.patternKeyFor(string);
        }
        String object = app.fillernPairs.get(string2);
        if (object != null) {
            app.fillernPairs.put("l:" + learned22.id, object);
            if (app.fillernPicked.contains(string2)) app.fillernPicked.add("l:" + learned22.id);
        }
        app.importLibrary.addLearnedChip(learned22);
        app.persistence.persistLearned();
        this.loadStyle(learned22.id, false);
        app.setNow("Copy \u00b7 " + learned22.name);
    }

    void refreshStyles() {
        View view;
        int n;
        if (app.styleBar != null) {
            for (n = 0; n < app.styleBar.getChildCount(); ++n) {
                view = app.styleBar.getChildAt(n);
                if (!(view instanceof TextView)) continue;
                app.paintChip((TextView)view, app.styleChosen && app.style.equals(view.getTag()), this.fillernUnder(String.valueOf(view.getTag())));
            }
        }
        if (app.importedHost != null) {
            this.paintStyleGroup((ViewGroup)app.importedHost);
        }
        if (app.variatedPatternBar != null) {
            for (n = 0; n < app.variatedPatternBar.getChildCount(); ++n) {
                view = app.variatedPatternBar.getChildAt(n);
                if (!(view instanceof TextView) || view.getTag() == null || "imported-empty".equals(view.getTag())) continue;
                app.paintChip((TextView)view, app.styleChosen && app.style.equals(view.getTag()), this.fillernUnder(String.valueOf(view.getTag())));
            }
        }
    }

    void paintStyleGroup(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); ++i) {
            View view = viewGroup.getChildAt(i);
            if (view instanceof TextView && view.getTag() != null && !"imported-empty".equals(view.getTag()) && !"pack".equals(view.getTag())) {
                app.paintChip((TextView)view, app.styleChosen && app.style.equals(view.getTag()), this.fillernUnder(String.valueOf(view.getTag())));
                continue;
            }
            if (!(view instanceof ViewGroup)) continue;
            this.paintStyleGroup((ViewGroup)view);
        }
    }

    void addPluginStyle(Engine.Style style) {
        app.styles.put(style.id, style);
        app.importLibrary.rebuildImported();
    }

    void addVariatedFillChip(Engine.LearnedFill learnedFill) {
        if (app.variatedFillBar == null) {
            return;
        }
        String string = "v:" + learnedFill.id;
        for (int i = 0; i < app.variatedFillBar.getChildCount(); ++i) {
            if (!string.equals(app.variatedFillBar.getChildAt(i).getTag())) continue;
            return;
        }
        TextView textView = app.pill(learnedFill.name, false, view -> this.applyFill(string));
        textView.setTag((Object)string);
        textView.setOnLongClickListener(view -> {
            new AlertDialog.Builder((Context)app).setTitle((CharSequence)learnedFill.name).setItems(new CharSequence[]{"Delete"}, (dialogInterface, n) -> {
                app.variatedFills.remove(learnedFill);
                app.variatedFillBar.removeView((View)textView);
                app.persistence.persistLearned();
                this.syncVariatedEmpty();
                app.setNow("Variation deleted");
            }).show();
            return true;
        });
        app.variatedFillBar.addView((View)textView);
        this.syncVariatedEmpty();
        this.refreshFills();
    }

    void syncVariatedEmpty() {
        if (app.variatedFillBar == null) {
            return;
        }
        boolean bl = false;
        View view = null;
        for (int i = 0; i < app.variatedFillBar.getChildCount(); ++i) {
            View view2 = app.variatedFillBar.getChildAt(i);
            if ("imported-empty".equals(view2.getTag())) {
                view = view2;
                continue;
            }
            bl = true;
        }
        if (view != null) {
            view.setVisibility(bl ? 8 : 0);
        }
    }

    String patternKeyFor(String string) {
        if (string == null) {
            return "s:house";
        }
        for (Engine.Learned learned : app.variatedPatterns) {
            if (!learned.id.equals(string)) continue;
            return "v:" + string;
        }
        for (Engine.Learned learned : app.learned) {
            if (!learned.id.equals(string)) continue;
            return "l:" + string;
        }
        return "s:" + string;
    }

    boolean fillernUnder(String string) {
        if (!"combo".equals(app.view) || string == null || "imported-empty".equals(string) || "null".equals(string)) {
            return false;
        }
        return this.fillernUnderlined(this.patternKeyFor(string));
    }

    /** Underlined only when a fill was chosen for this pattern from the list, and that fill still exists. */
    boolean fillernUnderlined(String string) {
        return this.selectedFillFor(string) != null;
    }

    String fillernFillKeyOf(String string) {
        String string2 = app.fillernPairs.get(string);
        if (string2 != null && !string2.isEmpty()) {
            return string2;
        }
        if (string.equals(this.currentPatternKey())) {
            return app.fillId;
        }
        return "toms";
    }

    String songPickSection(String string) {
        if (string != null && string.startsWith("v:")) {
            return "Variated";
        }
        if (string != null && (string.startsWith("l:") || string.startsWith("p:"))) {
            return "Imported";
        }
        return "Built-in";
    }

    /** The fill chosen from the list for this pattern, or null. Fills an import paired do not count. */
    String selectedFillFor(String string) {
        if (string == null || !app.fillernPicked.contains(string)) {
            return null;
        }
        String string2 = app.fillernPairs.get(string);
        if (string2 == null || string2.isEmpty()) {
            return null;
        }
        return Engine.fillKeyExists(string2, app.variatedFills, app.learnedFills) ? string2 : null;
    }

    /** A fill chosen from the list: remembered and underlined. */
    void pickFillern(String string, String string2) {
        if (string == null || string2 == null) {
            return;
        }
        app.fillernPicked.add(string);
        this.rememberFillern(string, string2);
        if ("combo".equals(app.view)) app.importLibrary.rebuildImported();
    }

    void rememberFillern(String string, String string2) {
        if (string == null || string2 == null) {
            return;
        }
        app.fillernPairs.remove(string);
        app.fillernPairs.put(string, string2);
        app.persistence.persistLearned();
        this.refreshStyles();
    }

    void applyStoredFillern(String string) {
        if (!"combo".equals(app.view) || string == null) {
            return;
        }
        String string2 = app.fillernPairs.get(string);
        if (string2 != null) {
            this.applyFill(string2);
        }
    }

    void hideStyle(String string) {
        if (string == null) {
            return;
        }
        if (!this.hiddenStyles.contains(string)) {
            this.hiddenStyles.add(string);
        }
        if (app.styleBar != null) {
            for (int i = 0; i < app.styleBar.getChildCount(); ++i) {
                View view = app.styleBar.getChildAt(i);
                if (!string.equals(String.valueOf(view.getTag()))) continue;
                view.setVisibility(8);
            }
        }
        app.setNow("Hidden style");
    }

    CharSequence fillernItem(String string, String string2, String string3) {
        String string4 = "  " + string2;
        if (string.equals(this.selectedFillFor(string3))) {
            String string5 = string4.replace("&", "&amp;").replace("<", "&lt;");
            return Html.fromHtml((String)("<u>" + string5 + "</u>"), (int)0);
        }
        return string4;
    }

    String fillernModeOf(String patternKey) {
        return Engine.fillernModeOr(patternKey == null ? null : app.fillernModes.get(patternKey));
    }

    void setFillernMode(String patternKey, String mode) {
        if (patternKey == null) return;
        String m = Engine.fillernMode(mode);
        app.fillernModes.put(patternKey, m);  // its own type, whatever the default is later
        app.persistence.persistLearned();
        if ("combo".equals(app.view)) app.importLibrary.rebuildImported();
        app.setNow(Engine.FILLERN_MODE_LABELS[java.util.Arrays.asList(Engine.FILLERN_MODES).indexOf(m)]);
    }

    void addFillernFillRows(List<CharSequence> list, List<Runnable> list2, String string, Runnable runnable) {
        if (!"combo".equals(app.view)) {
            return;
        }
        list.add("— Fillern type —");
        list2.add(null);
        String mode = this.fillernModeOf(string);
        for (int i = 0; i < Engine.FILLERN_MODES.length; ++i) {
            String m = Engine.FILLERN_MODES[i];
            String row = "  " + Engine.FILLERN_MODE_LABELS[i];
            list.add(m.equals(mode) ? Html.fromHtml((String)("<u>" + row.replace("&", "&amp;").replace("<", "&lt;") + "</u>"), (int)0) : row);
            list2.add(() -> this.setFillernMode(string, m));
        }
        list.add("— Last-bar fill —");
        list2.add(null);
        list.add("— Built-in —");
        list2.add(null);
        for (int i = 0; i < Engine.FILL_ID.length; ++i) {
            String object3 = Engine.FILL_ID[i];
            if (this.hiddenFills.contains(object3)) continue;
            list.add(this.fillernItem(object3, Engine.FILL_LABEL[i], string));
            list2.add(() -> {
                runnable.run();
                this.applyFill(object3);
                this.pickFillern(string, object3);
                app.show("combo");
            });
        }
        if (!app.variatedFills.isEmpty()) {
            list.add("— Variated —");
            list2.add(null);
            for (Engine.LearnedFill object4 : app.variatedFills) {
                String object2 = "v:" + object4.id;
                list.add(this.fillernItem(object2, object4.name, string));
                list2.add(() -> this.addFillernFillRowsAction124(runnable, object2, string));
            }
        }
        LinkedHashMap<String, ArrayList<Engine.LearnedFill>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.LearnedFill>>();
        for (Engine.LearnedFill learnedFill : app.learnedFills) {
            String src = Engine.sourceOf(learnedFill);
            if (src.isEmpty()) {
                src = "Other";
            }
            ArrayList<Engine.LearnedFill> group = linkedHashMap.get(src);
            if (group == null) {
                group = new ArrayList<Engine.LearnedFill>();
                linkedHashMap.put(src, group);
            }
            group.add(learnedFill);
        }
        for (Map.Entry<String, ArrayList<Engine.LearnedFill>> entry : linkedHashMap.entrySet()) {
            list.add("— " + entry.getKey() + " —");
            list2.add(null);
            for (Engine.LearnedFill object4 : entry.getValue()) {
                String string2 = "l:" + object4.id;
                list.add(this.fillernItem(string2, object4.name, string));
                list2.add(() -> {
                    runnable.run();
                    this.applyFill(string2);
                    this.pickFillern(string, string2);
                    app.show("combo");
                });
            }
        }
    }

    void showEntryMenu(String string, String string2, Runnable runnable, List<String> list, List<Runnable> list2) {
        ArrayList<CharSequence> arrayList = new ArrayList<CharSequence>(list);
        ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>(list2);
        this.addFillernFillRows(arrayList, arrayList2, string2, runnable);
        if (arrayList.isEmpty()) {
            return;
        }
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)string).setItems(arrayList.toArray(new CharSequence[0]), (dialogInterface, n) -> {
            if (n < 0 || n >= arrayList2.size()) {
                return;
            }
            Runnable picked = (Runnable)arrayList2.get(n);
            if (picked != null) {
                picked.run();
            }
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    void attachLearnedStyleMenu(TextView textView, Engine.Learned learned) {
        textView.setOnLongClickListener(view -> {
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            arrayList.add("Duplicate");
            arrayList2.add(() -> this.replicateStyle(learned.id));
            arrayList.add("Rename");
            arrayList2.add(() -> this.promptRename(learned.name, string -> {
                learned.name = string;
                textView.setText((CharSequence)string);
                app.styles.put(learned.id, new Engine.Style(learned.id, string, learned.bpm, Engine.rowsFromCells(learned.cells)));
                app.persistence.persistLearned();
            }));
            arrayList.add("Delete");
            arrayList2.add(() -> {
                app.learned.remove(learned);
                app.persistence.persistLearned();
                app.importLibrary.rebuildImported();
                app.setNow("Style deleted");
            });
            this.showEntryMenu(learned.name, this.patternKeyFor(learned.id), () -> this.loadStyle(learned.id, false), arrayList, arrayList2);
            return true;
        });
    }

    void attachBuiltinStyleMenu(TextView textView, String string) {
        textView.setOnLongClickListener(view -> {
            Engine.Style style = app.styles.get(string);
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            arrayList.add("Duplicate");
            arrayList2.add(() -> this.replicateStyle(string));
            arrayList.add("Hide");
            arrayList2.add(() -> this.hideStyle(string));
            this.showEntryMenu(style != null ? style.label : string, this.patternKeyFor(string), () -> this.loadStyle(string, false), arrayList, arrayList2);
            return true;
        });
    }

    void attachLearnedFillMenu(TextView textView, Engine.LearnedFill learnedFill) {
        textView.setOnLongClickListener(view -> {
            new AlertDialog.Builder((Context)app).setTitle((CharSequence)learnedFill.name).setItems(new CharSequence[]{"Rename", "Delete"}, (dialogInterface, n) -> {
                if (n == 0) {
                    this.promptRename(learnedFill.name, string -> {
                        learnedFill.name = string;
                        textView.setText((CharSequence)string);
                        app.persistence.persistLearned();
                    });
                } else {
                    app.learnedFills.remove(learnedFill);
                    app.persistence.persistLearned();
                    app.importLibrary.rebuildImportedFills();
                    app.setNow("Fill deleted");
                }
            }).show();
            return true;
        });
    }

    void promptRename(String string, NameFn nameFn) {
        EditText editText = new EditText((Context)app);
        editText.setText((CharSequence)string);
        editText.setSingleLine();
        editText.setSelectAllOnFocus(true);
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)"Rename").setView((View)editText).setPositiveButton((CharSequence)"Save", (dialogInterface, n) -> {
            String name = editText.getText().toString().trim();
            if (name.isEmpty()) {
                return;
            }
            if (name.length() > 28) {
                name = name.substring(0, 28);
            }
            nameFn.apply(name);
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    private /* synthetic */ void addFillernFillRowsAction124(Runnable runnable, String string, String string2) {
        runnable.run();
        this.applyFill(string);
        this.pickFillern(string2, string);
        app.show("combo");
    }
}
