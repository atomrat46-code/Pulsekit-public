package pulsekit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static pulsekit.MainActivity.*;

/** Learning patterns and fills from imported MIDI, and the imported-file lists. */
final class ImportLibrary {
    final MainActivity app;

    ImportLibrary(MainActivity app) {
        this.app = app;
    }

    final Map<String, Boolean> openPacks = new LinkedHashMap<String, Boolean>();

    /*
     * WARNING - void declaration
     */
    void learnFromImport(String string, int[][] nArray, int n) {
        Engine.LearnedFill learnedFill = null;
        boolean bl;
        if (Engine.hitCount(nArray) < 1) {
            return;
        }
        String string2 = Engine.midiRoleFromFile(string);
        boolean bl2 = "pattern".equals(string2);
        boolean bl3 = "fill".equals(string2);
        boolean bl4 = "fillern".equals(string2) || !bl2 && !bl3 && Engine.importedFillern(nArray);
        boolean bl5 = bl = bl3 || bl4 || !bl2 && (Engine.shouldImportFill(nArray) || Engine.hitCount(Engine.lastBar(nArray)) >= 2);
        if (bl3) {
            Engine.LearnedFill learnedFill2 = this.addImportedFill(string, nArray);
            if (learnedFill2 != null) {
                app.styleLibrary.applyFill("l:" + learnedFill2.id);
                app.show("fills");
                app.persistence.persistLearned();
                app.setNow(app.styleLibrary.fillLabel(learnedFill2.kind) + " \u00b7 " + learnedFill2.name);
            }
            return;
        }
        int[][] nArray2 = bl4 ? Engine.firstBar(nArray) : nArray;
        String string3 = Engine.patternSignature(nArray2);
        Engine.Learned object3 = null;
        for (Engine.Learned object22 : app.learned) {
            if (!Engine.patternSignature(object22.cells).equals(string3)) continue;
            object3 = object22;
            app.styleLibrary.loadStyle(object22.id, false);
            break;
        }
        if (object3 == null) {
            Engine.Learned object = new Engine.Learned();
            object.id = Engine.newLearnedId();
            object.name = Engine.stemNameFromMidi(string);
            object.bpm = n;
            object.closest = Engine.matchStyle(nArray2);
            object.cells = Engine.copyCells(nArray2);
            object.swing = app.swing();
            object.density = app.density();
            object.human = app.human();
            object.source = Engine.importSource(string);
            app.learned.add(0, object);
            while (app.learned.size() > Engine.MAX_LEARNED) {
                app.learned.remove(app.learned.size() - 1);
            }
            app.styles.put(object.id, new Engine.Style(object.id, object.name, object.bpm, Engine.rowsFromCells(object.cells)));
            this.addLearnedChip(object);
            object3 = object;
            app.styleLibrary.loadStyle(object.id, false);
        }
        String object = "Style \u00b7 " + object3.name;
        if (bl && (learnedFill = this.addImportedFill(string, nArray)) != null) {
            object = object + " \u00b7 fill \u00b7 " + learnedFill.name;
        }
        if (bl4 && learnedFill != null) {
            app.styleLibrary.applyFill("l:" + learnedFill.id);
            app.styleLibrary.rememberFillern(app.styleLibrary.patternKeyFor(object3.id), "l:" + learnedFill.id);
            app.show("combo");
            object = "Fillern \u00b7 " + object3.name + " \u00b7 " + app.styleLibrary.fillLabel(learnedFill.kind);
        } else if (bl2) {
            app.show("pattern");
        }
        app.persistence.persistLearned();
        app.setNow(object);
    }

    boolean learnFromSongImport(String string, Engine.MidiBars midiBars) {
        if (this.tryPackMidiFileSet(string, midiBars, true)) {
            return true;
        }
        return this.learnFromSongImport(string, midiBars, false);
    }

    /*
     * WARNING - void declaration
     */
    boolean learnFromSongImport(String string, Engine.MidiBars midiBars, boolean bl) {
        if (this.tryPackMidiFileSet(string, midiBars, bl)) {
            return true;
        }
        boolean bl2;
        List<Engine.MidiSeg> list = Engine.segmentMidiBars(midiBars.bars);
        if (list.isEmpty()) {
            return false;
        }
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        LinkedHashSet<String> linkedHashSet2 = new LinkedHashSet<String>();
        int n = 0;
        for (Engine.MidiSeg object52 : list) {
            linkedHashSet.add(Engine.patternSignature(object52.groove));
            if (object52.fill != null) {
                linkedHashSet2.add(Engine.patternSignature(object52.fill));
            }
            if (!"fillern".equals(object52.kind)) continue;
            ++n;
        }
        boolean bl3 = bl2 = midiBars.bars.size() >= 3 && (list.size() > 1 || linkedHashSet.size() > 1 || linkedHashSet2.size() > 1 || n > 0 && midiBars.bars.size() >= 4);
        if (!bl && !bl2) {
            return false;
        }
        String string2 = Engine.stemNameFromMidi(string);
        int[][] nArray = Engine.copyCells(list.get((int)0).groove);
        for (int linkedHashMap = 0; linkedHashMap < Engine.TRACK_ID.length; ++linkedHashMap) {
            System.arraycopy(nArray[linkedHashMap], 0, app.cells[linkedHashMap], 0, 32);
        }
        if (app.bpmBar != null) {
            app.bpmBar.setVal(Engine.clampBpm(midiBars.bpm));
        }
        app.bpmLabel.setText((CharSequence)Integer.toString(app.bpm()));
        app.gridEditor.applyTimeSig(midiBars.tsNum, midiBars.tsDen);
        app.gridEditor.refreshGrid();
        LinkedHashMap<String, Engine.Learned> linkedHashMap = new LinkedHashMap<String, Engine.Learned>();
        LinkedHashMap<String, Engine.LearnedFill> linkedHashMap2 = new LinkedHashMap<String, Engine.LearnedFill>();
        String string3 = null;
        String string4 = null;
        for (Engine.MidiSeg midiSeg : list) {
            String string5 = Engine.patternSignature(midiSeg.groove);
            Engine.Learned object2 = linkedHashMap.get(string5);
            if (object2 == null) {
                for (Engine.Learned learned : app.learned) {
                    if (!Engine.patternSignature(learned.cells).equals(string5)) continue;
                    object2 = learned;
                    break;
                }
                if (object2 == null && Engine.hitCount(midiSeg.groove) >= 1) {
                    Engine.Learned object = new Engine.Learned();
                    object.id = Engine.newLearnedId();
                    object.name = Engine.uniqueLearnedName(string2, app.learned);
                    object.bpm = midiBars.bpm;
                    object.closest = Engine.matchStyle(midiSeg.groove);
                    object.cells = Engine.copyCells(midiSeg.groove);
                    object.swing = app.swing();
                    object.density = app.density();
                    object.human = app.human();
                    object.source = string2;
                    app.learned.add(0, object);
                    while (app.learned.size() > Engine.MAX_LEARNED) {
                        app.learned.remove(app.learned.size() - 1);
                    }
                    app.styles.put(object.id, new Engine.Style(object.id, object.name, object.bpm, Engine.rowsFromCells(object.cells)));
                    this.addLearnedChip(object);
                    object2 = object;
                }
                if (object2 != null) {
                    linkedHashMap.put(string5, object2);
                }
            }
            if (object2 != null && string3 == null) {
                string3 = object2.id;
            }
            if (midiSeg.fill == null || object2 == null) continue;
            String fillSig = Engine.patternSignature(midiSeg.fill);
            Engine.LearnedFill learnedFill2 = linkedHashMap2.get(fillSig);
            if (learnedFill2 == null && (learnedFill2 = this.addImportedFillBar(string, midiSeg.fill, string2 + " " + Engine.fillLabel(Engine.classifyFill(midiSeg.fill)))) != null) {
                linkedHashMap2.put(fillSig, learnedFill2);
            }
            if (learnedFill2 == null) continue;
            app.styleLibrary.rememberFillern(app.styleLibrary.patternKeyFor(object2.id), "l:" + learnedFill2.id);
            if (string4 != null) continue;
            string4 = object2.id;
        }
        ArrayList<Engine.Part> arrayList = new ArrayList<Engine.Part>();
        for (Engine.MidiSeg midiSeg : list) {
            if (arrayList.size() >= Engine.MAX_SONG) break;
            Engine.Learned object2 = linkedHashMap.get(Engine.patternSignature(midiSeg.groove));
            arrayList.add(Engine.groove(object2 != null ? object2.name : string2, midiBars.bpm, midiSeg.groove, midiSeg.grooveRepeats));
            if (midiSeg.fill == null || arrayList.size() >= Engine.MAX_SONG) continue;
            Engine.LearnedFill object = linkedHashMap2.get(Engine.patternSignature(midiSeg.fill));
            arrayList.add(Engine.fill(object != null ? object.name : "fill", midiBars.bpm, midiSeg.fill, 1));
        }
        if (string4 != null) {
            app.styleLibrary.loadStyle(string4, false);
            app.show("combo");
            app.styleLibrary.applyStoredFillern(app.styleLibrary.patternKeyFor(string4));
        } else {
            if (string3 != null) {
                app.styleLibrary.loadStyle(string3, false);
            }
            app.show("pattern");
        }
        app.persistence.persistLearned();
        StringBuilder stringBuilder = new StringBuilder("Imported \u00b7 ").append(linkedHashMap.size()).append(linkedHashMap.size() == 1 ? " pattern" : " patterns");
        if (!linkedHashMap2.isEmpty()) {
            stringBuilder.append(" \u00b7 ").append(linkedHashMap2.size()).append(linkedHashMap2.size() == 1 ? " fill" : " fills");
        }
        if (n > 0) {
            stringBuilder.append(" \u00b7 ").append(n).append(n == 1 ? " Fillern" : " Fillerns");
        }
        app.setNow(stringBuilder.toString());
        Toast.makeText((Context)app, (CharSequence)stringBuilder.toString(), (int)1).show();
        if (!arrayList.isEmpty()) {
            String string6 = string2;
            ArrayList<Engine.Part> object2 = new ArrayList<Engine.Part>(arrayList);
            new AlertDialog.Builder((Context)app).setTitle((CharSequence)"Make a song?").setMessage((CharSequence)(stringBuilder + "\n\nIt goes under Imported. Your original song stays.")).setPositiveButton((CharSequence)"Make song", (arg_0, arg_1) -> this.learnFromSongImportAction100(string6, object2, arg_0, arg_1)).setNegativeButton((CharSequence)"Not now", null).show();
        }
        return true;
    }

    Engine.LearnedFill addImportedFillBar(String string, int[][] nArray, String string2) {
        if (Engine.hitCount(nArray) < 1 && !"rest".equals(Engine.classifyFill(nArray))) {
            return null;
        }
        String string3 = Engine.patternSignature(nArray);
        for (Engine.LearnedFill learnedFill : app.learnedFills) {
            if (!Engine.patternSignature(learnedFill.cells).equals(string3)) continue;
            return learnedFill;
        }
        Engine.LearnedFill learnedFill = new Engine.LearnedFill();
        learnedFill.id = Engine.newLearnedId();
        learnedFill.kind = Engine.classifyFill(nArray);
        learnedFill.name = Engine.uniqueFillName(string2 != null ? string2 : Engine.fillNameFromFile(string, learnedFill.kind), app.learnedFills);
        learnedFill.cells = Engine.copyCells(nArray);
        learnedFill.source = Engine.importSource(string);
        app.learnedFills.add(0, learnedFill);
        while (app.learnedFills.size() > Engine.MAX_LEARNED) {
            app.learnedFills.remove(app.learnedFills.size() - 1);
        }
        this.addLearnedFillChip(learnedFill);
        return learnedFill;
    }

    Engine.LearnedFill addImportedFill(String string, int[][] nArray) {
        return this.addImportedFillBar(string, Engine.lastBar(nArray), null);
    }

    void addLearnedChip(Engine.Learned learned) {
        this.rebuildImported();
    }

    void addLearnedFillChip(Engine.LearnedFill learnedFill) {
        this.rebuildImportedFills();
    }

    boolean packOpen(String string, boolean bl) {
        Boolean bl2 = this.openPacks.get(string);
        return bl2 != null ? bl2 : bl;
    }

    void addImportPackBase(LinearLayout linearLayout, String string, String string2, int n, boolean bl, Runnable runnable, Runnable runnable2, Consumer<FlowLayout> consumer) {
        boolean bl2 = this.packOpen(string, bl);
        TextView textView = app.pill((bl2 ? "\u25be " : "\u25b8 ") + string2 + " \u00b7 " + n, bl, view -> {
            this.openPacks.put(string, !this.packOpen(string, bl));
            this.rebuildImported();
            this.rebuildImportedFills();
            this.refreshImportedFiles();
        });
        textView.setTag((Object)"pack");
        textView.setOnLongClickListener(view -> {
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
            if (runnable2 != null) {
                arrayList.add("Export .fset");
                arrayList2.add(runnable2);
            }
            arrayList.add("Delete file set");
            arrayList2.add(runnable);
            new AlertDialog.Builder((Context)app).setTitle((CharSequence)string2).setItems(arrayList.toArray(new CharSequence[0]), (dialogInterface, which) -> {
                if (which >= 0 && which < arrayList2.size()) {
                    ((Runnable)arrayList2.get(which)).run();
                }
            }).show();
            return true;
        });
        linearLayout.addView((View)textView);
        if (bl2) {
            FlowLayout flowLayout = new FlowLayout((Context)app, app.dp(6), app.dp(6));
            flowLayout.setSingleLine(true);
            consumer.accept(flowLayout);
            linearLayout.addView((View)app.chipStrip((View)flowLayout));
        }
    }

    void rebuildImported() {
        if (app.importedHost == null) {
            return;
        }
        app.importedHost.removeAllViews();
        app.importedFor = "combo".equals(app.view) ? "combo" : "pattern";
        if (app.learned.isEmpty()) {
            TextView textView = app.text("MIDI, song or plugin pack", 12, false);
            textView.setTextColor(MUTED);
            textView.setTag((Object)"imported-empty");
            app.importedHost.addView((View)textView);
            this.refreshImportedFiles();
            return;
        }
        LinkedHashMap<String, ArrayList<Engine.Learned>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.Learned>>();
        for (Engine.Learned object : app.learned) {
            String string = Engine.sourceOf(object);
            ArrayList<Engine.Learned> arrayList = linkedHashMap.get(string);
            if (arrayList == null) {
                arrayList = new ArrayList<Engine.Learned>();
                linkedHashMap.put(string, arrayList);
            }
            arrayList.add(object);
        }
        for (Map.Entry<String, ArrayList<Engine.Learned>> entry : linkedHashMap.entrySet()) {
            String string = entry.getKey();
            ArrayList<Engine.Learned> arrayList = entry.getValue();
            boolean bl = false;
            for (Engine.Learned object2 : arrayList) {
                if (!app.style.equals(object2.id)) continue;
                bl = true;
            }
            String string2 = string.isEmpty() ? "Other" : string;
            String packKey = string.isEmpty() ? "o:other" : "f:" + string;
            // On the Fillern tab a file set lists its Fillerns: patterns with a fill chosen for them.
            boolean fillerns = "combo".equals(app.view);
            int shown = fillerns ? this.fillernsOf(arrayList).size() : arrayList.size();
            this.addImportPack(app.importedHost, packKey, string2, shown, bl, () -> this.removeImportSource(string), () -> this.saveFset(string), flowLayout -> {
                if (fillerns) {
                    this.addFillernChips(flowLayout, arrayList);
                    return;
                }
                for (Engine.Learned learned : arrayList) {
                    TextView textView = app.pill(learned.name, false, view -> app.styleLibrary.loadStyle(learned.id, false));
                    textView.setTag((Object)learned.id);
                    app.styleLibrary.attachLearnedStyleMenu(textView, learned);
                    flowLayout.addView((View)textView);
                }
            });
        }
        app.styleLibrary.refreshStyles();
        this.refreshImportedFiles();
    }

    /** Patterns of a file set that have a fill chosen for them. */
    List<Engine.Learned> fillernsOf(List<Engine.Learned> patterns) {
        ArrayList<Engine.Learned> out = new ArrayList<Engine.Learned>();
        for (Engine.Learned learned : patterns) {
            if (app.styleLibrary.selectedFillFor(app.styleLibrary.patternKeyFor(learned.id)) != null) out.add(learned);
        }
        return out;
    }

    /** Fillern chips ("pattern · fill"), or a note when there are none, and a way to make one. */
    void addFillernChips(ViewGroup host, List<Engine.Learned> patterns) {
        List<Engine.Learned> made = this.fillernsOf(patterns);
        for (Engine.Learned learned : made) {
            String fill = app.styleLibrary.selectedFillFor(app.styleLibrary.patternKeyFor(learned.id));
            String key = app.styleLibrary.patternKeyFor(learned.id);
            String note = Engine.fillernModeNote(app.styleLibrary.fillernModeOf(key));
            TextView textView = app.pill(learned.name + " \u00b7 " + app.styleLibrary.fillLabel(fill) + note, false, view -> app.styleLibrary.loadStyle(learned.id, false));
            textView.setTag((Object)learned.id);
            app.styleLibrary.attachLearnedStyleMenu(textView, learned);
            host.addView((View)textView);
        }
        if (made.isEmpty()) {
            TextView none = app.text("No fillerns yet, create one", 13, false);
            none.setTextColor(MUTED);
            none.setTag((Object)"fillern-none");
            none.setPadding(app.dp(4), app.dp(10), app.dp(10), app.dp(10));
            none.setOnClickListener(view -> this.createFillern(patterns));
            host.addView((View)none);
        }
        TextView add = app.pill("+ Fillern", false, view -> this.createFillern(patterns));
        add.setTag((Object)"fillern-add");
        host.addView((View)add);
    }

    /** Pick a pattern of the file set, then a fill for it. */
    void createFillern(List<Engine.Learned> patterns) {
        if (patterns.isEmpty()) {
            app.setNow("This file set has no patterns");
            return;
        }
        String[] names = new String[patterns.size()];
        for (int i = 0; i < names.length; i++) names[i] = patterns.get(i).name;
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)"Fillern: choose a pattern").setItems((CharSequence[])names, (dialogInterface, which) -> {
            if (which < 0 || which >= patterns.size()) return;
            Engine.Learned learned = patterns.get(which);
            ArrayList<CharSequence> rows = new ArrayList<CharSequence>();
            ArrayList<Runnable> acts = new ArrayList<Runnable>();
            app.styleLibrary.addFillernFillRows(rows, acts, app.styleLibrary.patternKeyFor(learned.id), () -> app.styleLibrary.loadStyle(learned.id, false));
            new AlertDialog.Builder((Context)app).setTitle((CharSequence)("Fill for " + learned.name)).setItems(rows.toArray(new CharSequence[0]), (d, n) -> {
                if (n < 0 || n >= acts.size() || acts.get(n) == null) return;
                acts.get(n).run();
            }).setNegativeButton((CharSequence)"Cancel", null).show();
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    void rebuildImportedFills() {
        if (app.importedFillHost == null) {
            return;
        }
        app.importedFillHost.removeAllViews();
        if (app.learnedFills.isEmpty() && app.learned.isEmpty()) {
            TextView textView = app.text("Last bar of an imported MIDI", 12, false);
            textView.setTextColor(MUTED);
            textView.setTag((Object)"imported-empty");
            app.importedFillHost.addView((View)textView);
            this.refreshImportedFiles();
            return;
        }
        // Every file set, also one without fills yet.
        LinkedHashMap<String, ArrayList<Engine.LearnedFill>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.LearnedFill>>();
        for (String src : Engine.fileSetSources(app.learned, app.learnedFills)) linkedHashMap.put(src, new ArrayList<Engine.LearnedFill>());
        for (Engine.LearnedFill object : app.learnedFills) {
            String string = Engine.sourceOf(object);
            ArrayList<Engine.LearnedFill> arrayList = linkedHashMap.get(string);
            if (arrayList == null) {
                arrayList = new ArrayList<Engine.LearnedFill>();
                linkedHashMap.put(string, arrayList);
            }
            arrayList.add(object);
        }
        for (Map.Entry<String, ArrayList<Engine.LearnedFill>> entry : linkedHashMap.entrySet()) {
            String string = entry.getKey();
            ArrayList<Engine.LearnedFill> arrayList = entry.getValue();
            boolean bl = false;
            for (Engine.LearnedFill object2 : arrayList) {
                if (!("l:" + object2.id).equals(app.fillId)) continue;
                bl = true;
            }
            String string2 = string.isEmpty() ? "Other" : string;
            String packKey = string.isEmpty() ? "o:other" : "f:" + string;
            this.addImportPack(app.importedFillHost, packKey, string2, arrayList.size(), bl, () -> this.removeImportSource(string), () -> this.saveFset(string), flowLayout -> {
                for (Engine.LearnedFill learnedFill : arrayList) {
                    String key = "l:" + learnedFill.id;
                    TextView textView = app.pill(learnedFill.name, false, view -> app.styleLibrary.applyFill(key));
                    textView.setTag((Object)key);
                    app.styleLibrary.attachLearnedFillMenu(textView, learnedFill);
                    flowLayout.addView((View)textView);
                }
                if (arrayList.isEmpty()) {
                    TextView none = app.text("No fills yet, add one", 13, false);
                    none.setTextColor(MUTED);
                    none.setTag((Object)"fills-none");
                    none.setPadding(app.dp(4), app.dp(10), app.dp(10), app.dp(10));
                    none.setOnClickListener(view -> app.styleLibrary.pickBuiltinFillFor(string));
                    flowLayout.addView((View)none);
                }
                TextView add = app.pill("+ Fill", false, view -> app.styleLibrary.pickBuiltinFillFor(string));
                add.setTag((Object)"fills-add");
                flowLayout.addView((View)add);
            });
        }
        app.styleLibrary.refreshFills();
        this.refreshImportedFiles();
    }

    void removeImportSourceBase(String string) {
        String string2 = string == null ? "" : string;
        app.learned.removeIf(learned -> string2.equals(Engine.sourceOf(learned)));
        app.learnedFills.removeIf(learnedFill -> string2.equals(Engine.sourceOf(learnedFill)));
        if (Engine.fileSetSongs.remove(string2) != null) app.fileSets.persistFsetInfo();
        app.fillernPairs.entrySet().removeIf(entry -> {
            String id;
            String key = (String)entry.getKey();
            String string3 = (String)entry.getValue();
            boolean bl = false;
            boolean bl2 = false;
            if (key != null && key.startsWith("l:")) {
                id = key.substring(2);
                bl = true;
                for (Engine.Learned object : app.learned) {
                    if (!id.equals(object.id)) continue;
                    bl = false;
                }
            }
            if (string3 != null && string3.startsWith("l:")) {
                id = string3.substring(2);
                bl2 = true;
                for (Engine.LearnedFill learnedFill : app.learnedFills) {
                    if (!id.equals(learnedFill.id)) continue;
                    bl2 = false;
                }
            }
            return bl || bl2;
        });
        app.persistence.persistLearned();
        this.rebuildImported();
        this.rebuildImportedFills();
        app.setNow("Removed \u00b7 " + (string2.isEmpty() ? "Other" : string2));
    }

    String selectedFileSource() {
        Object object;
        for (Engine.Learned iterator : app.learned) {
            if (!iterator.id.equals(app.style)) continue;
            return Engine.sourceOf(iterator);
        }
        if (app.fillId != null && app.fillId.startsWith("l:")) {
            object = app.fillId.substring(2);
            for (Engine.LearnedFill learnedFill : app.learnedFills) {
                if (!((String)object).equals(learnedFill.id)) continue;
                return Engine.sourceOf(learnedFill);
            }
        }
        object = new LinkedHashMap();
        for (Engine.Learned learned : app.learned) {
            ((HashMap)object).put(Engine.sourceOf(learned), Boolean.TRUE);
        }
        for (Engine.LearnedFill learnedFill : app.learnedFills) {
            ((HashMap)object).put(Engine.sourceOf(learnedFill), Boolean.TRUE);
        }
        if (((HashMap)object).isEmpty()) {
            return null;
        }
        return (String)((LinkedHashMap)object).keySet().iterator().next();
    }

    void saveFset(String string) {
        String string2 = string;
        if (string2 == null) {
            string2 = this.selectedFileSource();
        }
        if (string2 == null) {
            Toast.makeText((Context)app, (CharSequence)"Pick an imported file set first", (int)0).show();
            return;
        }
        String string3 = string2.isEmpty() ? "Other" : string2;
        Engine.FileSet fileSet = Engine.collectFset(string2, string3, app.learned, app.learnedFills, app.fillernPairs);
        if (fileSet == null) {
            Toast.makeText((Context)app, (CharSequence)"That file set is empty", (int)0).show();
            return;
        }
        app.fsetSaveSource = string2;
        app.projectIo.saveKind(19);
    }

    void loadFset(byte[] byArray, String string) throws Exception {
        this.pkFsetBytes = byArray;
        java.lang.String src = pulsekit.Engine.importSource(string);
        if (src == null || src.length() == 0 || "Import".equals(src)) src = string;
        java.lang.String unique = pulsekit.Engine.uniqueImportSource(src, app.learned, app.learnedFills);
        if (unique != null && src != null && !unique.equals(src)) string = unique + ".fset";
        this.pkFsetName = string;
        Engine.FileSet fileSet = Engine.decodeFset(byArray);
        String string2 = Engine.importSource(string);
        if (string2 == null || string2.isEmpty() || "Import".equals(string2)) {
            string2 = fileSet.name;
        }
        string2 = Engine.uniqueImportSource(string2, app.learned, app.learnedFills);
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
        ArrayList<Engine.LearnedFill> arrayList = new ArrayList<Engine.LearnedFill>();
        String string3 = null;
        int at = 0;
        for (Engine.Learned object2 : fileSet.patterns) {
            Engine.Learned copy = new Engine.Learned();
            copy.id = Engine.newLearnedId();
            copy.name = Engine.uniqueLearnedName(object2.name, Engine.learnedFrom(app.learned, string2));
            copy.bpm = object2.bpm;
            copy.closest = object2.closest;
            copy.cells = Engine.copyCells(object2.cells);
            copy.source = string2;
            app.learned.add(at++, copy);  // keep the file set's order: first part first
            while (app.learned.size() > Engine.MAX_LEARNED) {
                app.learned.remove(app.learned.size() - 1);
            }
            app.styles.put(copy.id, new Engine.Style(copy.id, copy.name, copy.bpm, Engine.rowsFromCells(copy.cells)));
            linkedHashMap.put(object2.name, copy.id);
            if (string3 != null) continue;
            string3 = copy.id;
        }
        int fat = 0;
        for (Engine.LearnedFill learnedFill : fileSet.fills) {
            Engine.LearnedFill copy = new Engine.LearnedFill();
            copy.id = Engine.newLearnedId();
            copy.name = Engine.uniqueFillName(learnedFill.name == null || learnedFill.name.isEmpty() ? "fill" : learnedFill.name, Engine.fillsFrom(app.learnedFills, string2));
            copy.kind = learnedFill.kind == null || learnedFill.kind.isEmpty() ? "toms" : learnedFill.kind;
            copy.cells = Engine.copyCells(learnedFill.cells);
            copy.source = string2;
            app.learnedFills.add(fat++, copy);
            while (app.learnedFills.size() > Engine.MAX_LEARNED) {
                app.learnedFills.remove(app.learnedFills.size() - 1);
            }
            arrayList.add(copy);
        }
        int n = 0;
        for (String[] stringArray2 : fileSet.fillerns) {
            String string4 = linkedHashMap.get(stringArray2[0]);
            if (string4 == null) continue;
            String string5 = Engine.resolveFsetFillKey(stringArray2[1], arrayList);
            app.fillernPairs.put("l:" + string4, string5);
            ++n;
        }
        // Songs saved in the file set: kept with it, and listed under Imported songs.
        for (Engine.FileSetSong song : fileSet.songs) {
            Engine.putFileSetSong(string2, song);
            app.songEditor.addFileSetSong(string2, song);
        }
        if (!fileSet.songs.isEmpty()) app.fileSets.persistFsetInfo();
        if (string3 != null) {
            app.styleLibrary.loadStyle(string3, false);
        } else if (!arrayList.isEmpty()) {
            app.styleLibrary.applyFill("l:" + arrayList.get(0).id);
        }
        app.persistence.persistLearned();
        this.rebuildImported();
        this.rebuildImportedFills();
        app.show(n > 0 ? "combo" : (string3 != null ? "pattern" : "fills"));
        String string6 = "File set · " + string2 + " · " + fileSet.patterns.size() + " patterns · " + n + " Fillerns · " + fileSet.fills.size() + " fills";
        app.setNow(string6);
        Toast.makeText((Context)app, (CharSequence)string6, (int)0).show();
        this.afterLoadFset();
    }

    void refreshImportedFilesBase() {
        if (app.importedFileList == null) {
            return;
        }
        app.importedFileList.removeAllViews();
        app.importedFileList.addView((View)app.sectionLabel("Imported files"));
        LinkedHashMap<String, int[]> linkedHashMap = new LinkedHashMap<String, int[]>();
        for (Engine.Learned object2 : app.learned) {
            String string = Engine.sourceOf(object2);
            int[] counts = linkedHashMap.get(string);
            if (counts == null) {
                counts = new int[]{0, 0};
                linkedHashMap.put(string, counts);
            }
            counts[0] = counts[0] + 1;
        }
        for (Engine.LearnedFill learnedFill : app.learnedFills) {
            String string = Engine.sourceOf(learnedFill);
            int[] counts = linkedHashMap.get(string);
            if (counts == null) {
                counts = new int[]{0, 0};
                linkedHashMap.put(string, counts);
            }
            counts[1] = counts[1] + 1;
        }
        if (linkedHashMap.isEmpty()) {
            TextView empty = app.text("No imported files yet.", 12, false);
            empty.setTextColor(MUTED);
            app.importedFileList.addView((View)empty);
            return;
        }
        for (Map.Entry<String, int[]> entry : linkedHashMap.entrySet()) {
            String string = entry.getKey();
            String label = string.isEmpty() ? "Other" : string;
            String string2 = string.isEmpty() ? "o:other" : "f:" + string;
            int n = entry.getValue()[0] + entry.getValue()[1];
            boolean bl = this.packOpen(string2, false);
            LinearLayout linearLayout = app.row();
            TextView textView2 = app.pill((bl ? "▾ " : "▸ ") + label + " · " + n, false, view -> {
                this.openPacks.put(string2, !this.packOpen(string2, false));
                this.refreshImportedFiles();
            });
            linearLayout.addView((View)textView2, (ViewGroup.LayoutParams)app.flex(1));
            TextView textView3 = app.outline("Export", false, view -> this.saveFset(string));
            linearLayout.addView((View)textView3);
            TextView textView4 = app.outline("Delete", false, view -> this.removeImportSource(string));
            linearLayout.addView((View)textView4);
            app.importedFileList.addView((View)linearLayout);
            if (!bl) continue;
            LinearLayout linearLayout2 = app.col();
            linearLayout2.setPadding(app.dp(8), 0, 0, app.dp(6));
            for (Engine.Learned learned : app.learned) {
                if (!string.equals(Engine.sourceOf(learned))) continue;
                TextView textView = app.text("Pattern · " + learned.name, 13, false);
                textView.setTextColor(MUTED);
                textView.setPadding(0, app.dp(4), 0, app.dp(4));
                textView.setOnClickListener(arg_0 -> this.refreshImportedFilesAction118(learned, arg_0));
                linearLayout2.addView((View)textView);
            }
            for (Engine.LearnedFill learnedFill : app.learnedFills) {
                if (!string.equals(Engine.sourceOf(learnedFill))) continue;
                TextView textView = app.text("Fill · " + learnedFill.name, 13, false);
                textView.setTextColor(MUTED);
                textView.setPadding(0, app.dp(4), 0, app.dp(4));
                textView.setOnClickListener(arg_0 -> this.refreshImportedFilesAction119(learnedFill, arg_0));
                linearLayout2.addView((View)textView);
            }
            app.importedFileList.addView((View)linearLayout2);
        }
    }

    private /* synthetic */ void refreshImportedFilesAction119(Engine.LearnedFill learnedFill, View view) {
        app.styleLibrary.applyFill("l:" + learnedFill.id);
        app.show("fills");
    }

    private /* synthetic */ void refreshImportedFilesAction118(Engine.Learned learned, View view) {
        app.styleLibrary.loadStyle(learned.id, false);
        app.show("pattern");
    }

    private /* synthetic */ void learnFromSongImportAction100(String string, List list, DialogInterface dialogInterface, int n) {
        app.songEditor.addImportedArrangement(string, list);
    }

    android.widget.LinearLayout pkPackHost;

    java.lang.String pkPackKey;

    java.lang.String pkPackLabel;

    java.lang.Runnable pkPackDel;

    java.lang.Runnable pkPackExp;

    byte[] pkFsetBytes;

    java.lang.String pkFsetName;

    java.lang.String pkRemovedSource;


    void afterLoadFset() {
        try {
            if (this.pkFsetBytes == null) return;
            pulsekit.Engine.FileSet set = pulsekit.Engine.decodeFset(this.pkFsetBytes);
            java.lang.String src = pulsekit.Engine.importSource(this.pkFsetName);
            if (src == null || src.length() == 0 || "Import".equals(src)) src = set.name;
            java.util.List parts = set.parts;
            if (parts == null || parts.isEmpty()) parts = pulsekit.Engine.partsForDisplay(set);
            if (pulsekit.Engine.isFileSetOrigin(set.origin)) pulsekit.Engine.rememberFileSetOrigin(src, set.origin);
            if (set.sourceWav != null || set.combinedWav != null) {
                pulsekit.Engine.rememberFileSetAudio(src, set.sourceWav, set.combinedWav, set.combinedName);
                pulsekit.Engine.storeFileSetAudioDir(new java.io.File(app.getFilesDir(), "fset-audio"));
            }
            if (set.sourceMidi != null && set.sourceMidi.length >= 14) {
                pulsekit.Engine.rememberFileSetMidi(src, set.sourceMidi, set.sourceMidiName);
                if (set.name != null && !set.name.equals(src)) pulsekit.Engine.rememberFileSetMidi(set.name, set.sourceMidi, set.sourceMidiName);
                for (int i = 0; i < app.learned.size() && i < 12; i++) {
                    pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) app.learned.get(i);
                    if (item == null) continue;
                    java.lang.String s = pulsekit.Engine.sourceOf(item);
                    if (s == null || s.length() == 0) continue;
                    if (s.equals(src) || (set.name != null && (s.equals(set.name) || s.startsWith(set.name + " ")))) pulsekit.Engine.rememberFileSetMidi(s, set.sourceMidi, set.sourceMidiName);
                }
            }
            app.fileSets.storeFsetParts(src, parts);
            this.rebuildImported();
            this.rebuildImportedFills();
        } catch (java.lang.Exception ignored) {}
    }



    void removeImportSource(String string) {
        this.pkRemovedSource = string;
        this.removeImportSourceBase(string);
        app.fileSets.ensureFsetInfoMap();
        if (this.pkRemovedSource != null) {
            app.fsetInfoMap.remove(this.pkRemovedSource);
            pulsekit.Engine.forgetFileSetOrigin(this.pkRemovedSource);
            pulsekit.Engine.forgetFileSetAudio(this.pkRemovedSource);
            pulsekit.Engine.storeFileSetAudioDir(new java.io.File(app.getFilesDir(), "fset-audio"));
        }
        app.fileSets.persistFsetInfo();
    }

    void addImportPack(LinearLayout linearLayout, String string, String string2, int n, boolean bl, Runnable runnable, Runnable runnable2, Consumer<FlowLayout> consumer) {
        this.pkPackHost = linearLayout;
        this.pkPackKey = string;
        this.pkPackLabel = string2;
        this.pkPackDel = runnable;
        this.pkPackExp = runnable2;
        this.addImportPackBase(linearLayout, string, string2, n, bl, runnable, runnable2, consumer);
        app.fileSets.attachFileSetInfoMenu(this.pkPackHost, this.pkPackKey, this.pkPackLabel, this.pkPackDel, this.pkPackExp);
    }

    void refreshImportedFiles() {
        this.refreshImportedFilesBase();
        app.fileSets.wireFileSetListActions();
    }

    boolean tryPackMidiFileSet(java.lang.String filename, pulsekit.Engine.MidiBars bars, boolean force) {
        if (bars == null || bars.bars == null || bars.bars.isEmpty()) return false;
        java.util.List segs = pulsekit.Engine.segmentMidiBars(bars.bars);
        if (segs == null || segs.isEmpty()) return false;
        java.util.LinkedHashSet uniqueG = new java.util.LinkedHashSet();
        java.util.LinkedHashSet uniqueF = new java.util.LinkedHashSet();
        int nFillern = 0;
        for (int i = 0; i < segs.size(); i++) {
            pulsekit.Engine.MidiSeg s = (pulsekit.Engine.MidiSeg) segs.get(i);
            uniqueG.add(pulsekit.Engine.patternSignature(s.groove));
            if (s.fill != null) uniqueF.add(pulsekit.Engine.patternSignature(s.fill));
            if ("fillern".equals(s.kind)) nFillern++;
        }
        boolean multi = bars.bars.size() >= 3 && (segs.size() > 1 || uniqueG.size() > 1 || uniqueF.size() > 1 || (nFillern > 0 && bars.bars.size() >= 4));
        if (!force && !multi) return false;
        try {
            java.lang.String stem = pulsekit.Engine.uniqueImportSource(pulsekit.Engine.stemNameFromMidi(filename), app.learned, app.learnedFills);
            pulsekit.Engine.FileSet set = pulsekit.AudioIo.fileSetFromMidi(bars, stem);
            pulsekit.Engine.attachStagedMidi(set);
            if (set.patterns.isEmpty() && set.fills.isEmpty()) return false;
            app.fileSets.storeFsetParts(stem, set.parts);
            byte[] packed = pulsekit.Engine.encodeFset(set);
            this.loadFset(packed, pulsekit.Engine.fsetFilename(set.name));
            // The song from the file set: parts are named after its patterns and fills (Pattern 1, Fill 1, ...).
            java.util.ArrayList song = new java.util.ArrayList(pulsekit.Engine.songFromFileSet(set));
            if (!song.isEmpty()) {
                new android.app.AlertDialog.Builder(app)
                    .setTitle((java.lang.CharSequence) "Make a song?")
                    .setMessage((java.lang.CharSequence) (set.patterns.size() + " patterns · " + set.fills.size() + " fills. It goes under Imported. Your original song stays."))
                    .setPositiveButton((java.lang.CharSequence) "Make song", pulsekit.FileSetClicks.makeSong(app, stem, song))
                    .setNegativeButton((java.lang.CharSequence) "Not now", null)
                    .show();
            }
            return true;
        } catch (java.lang.Exception ex) {
            java.lang.String m = ex.getMessage();
            app.setNow(m != null ? m : "Could not build a file set");
            return false;
        }
    }
}
