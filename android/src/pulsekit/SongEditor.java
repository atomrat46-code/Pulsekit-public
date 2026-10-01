package pulsekit;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static pulsekit.MainActivity.*;

/** The song lanes: parts, the song-pick menu and imported songs. */
final class SongEditor {
    final MainActivity app;

    SongEditor(MainActivity app) {
        this.app = app;
    }

    /** Builds the Song page: Edit/Play, Original/Imported lanes, add buttons, timeline and part cards. */
    void buildSongPane(FrameLayout frameLayout) {
        TextView textView;
        app.songPane = app.col();
        app.songPane.setVisibility(8);
        LinearLayout linearLayout14 = app.row();
        TextView editPill = app.pill("Edit", true, view -> {
            app.songMode = "edit";
            app.chrome.setVisibility(0);
            app.styleWrap.setVisibility(8);
            app.fillWrap.setVisibility(8);
            app.knobsRow.setVisibility(8);
            this.refreshSong();
        });
        editPill.setTag((Object)"edit");
        TextView textView16 = app.pill("Play", false, view -> {
            app.songMode = "play";
            app.chrome.setVisibility(8);
            this.refreshSong();
        });
        textView16.setTag((Object)"play");
        linearLayout14.addView((View)editPill);
        linearLayout14.addView((View)textView16);
        app.songPane.addView((View)linearLayout14);
        LinearLayout linearLayout15 = app.row();
        TextView textView17 = app.pill("Original", true, view -> {
            app.songLane = "original";
            this.refreshSong();
        });
        textView17.setTag((Object)"original");
        textView = app.pill("Imported", false, view -> {
            app.songLane = "imported";
            if (app.importedSongId == null && !app.importedSongs.isEmpty()) {
                app.importedSongId = app.importedSongs.get((int)0).id;
            }
            this.refreshSong();
        });
        textView.setTag((Object)"imported");
        linearLayout15.addView((View)textView17);
        linearLayout15.addView((View)textView);
        app.songPane.addView((View)linearLayout15);
        app.songAdds = app.row();
        TextView patternPill = app.pill("Pattern \u00d74", false, view -> {
            Engine.Part part = Engine.groove(app.styles.get((Object)app.style).label, app.bpm(), app.cells, 4);
            part.lens = Engine.copyCells(app.lens);
            this.add(part);
        });
        this.attachSongPick(patternPill, -1, "pattern");
        TextView textView18 = app.pill("Fillern", false, view -> this.addCurrentFillern());
        this.attachSongPick(textView18, -1, "fillern");
        TextView textView19 = app.pill("Fill", false, view -> {
            Engine.Part part = Engine.fill(app.styleLibrary.fillLabel(app.fillId), app.bpm(), app.fillPat, 1);
            part.lens = Engine.copyCells(app.fillLens);
            this.add(part);
        });
        this.attachSongPick(textView19, -1, "fill");
        app.songAdds.addView((View)patternPill);
        app.songAdds.addView((View)textView18);
        app.songAdds.addView((View)textView19);
        app.songAdds.addView((View)app.pill("Silent", false, view -> this.add(Engine.rest(app.bpm(), 2))));
        TextView saveToSet = app.pill("Save to set", false, view -> this.saveSongToFileSet());
        saveToSet.setTag((Object)"song-save-set");
        app.songAdds.addView((View)saveToSet);
        app.songAdds.addView((View)app.pill("Clear", false, view -> {
            if ("imported".equals(app.songLane) && app.importedSongId != null) {
                String string = app.importedSongId;
                app.importedSongs.removeIf(importedSong -> string.equals(importedSong.id));
                app.importedSongId = app.importedSongs.isEmpty() ? null : app.importedSongs.get((int)0).id;
                app.persistence.persistLearned();
            } else {
                app.song.clear();
            }
            this.refreshSong();
        }));
        HorizontalScrollView horizontalScrollView4 = new HorizontalScrollView((Context)app);
        horizontalScrollView4.setHorizontalScrollBarEnabled(false);
        horizontalScrollView4.addView((View)app.songAdds);
        app.songPane.addView((View)horizontalScrollView4);
        HorizontalScrollView horizontalScrollView5 = new HorizontalScrollView((Context)app);
        horizontalScrollView5.setHorizontalScrollBarEnabled(false);
        app.timeline = app.row();
        horizontalScrollView5.addView((View)app.timeline);
        app.songPane.addView((View)horizontalScrollView5);
        ScrollView scrollView2 = new ScrollView((Context)app);
        app.songCards = app.col();
        scrollView2.addView((View)app.songCards);
        app.songPane.addView((View)scrollView2, (ViewGroup.LayoutParams)app.flexFill());
        frameLayout.addView((View)app.songPane);
    }

    List<Engine.Part> activeSong() {
        if ("imported".equals(app.songLane)) {
            Engine.ImportedSong importedSong = this.importedSong();
            if (importedSong != null) {
                return importedSong.parts;
            }
            return new ArrayList<Engine.Part>();
        }
        return app.song;
    }

    Engine.ImportedSong importedSong() {
        if (app.importedSongId != null) {
            for (Engine.ImportedSong importedSong : app.importedSongs) {
                if (!app.importedSongId.equals(importedSong.id)) continue;
                return importedSong;
            }
        }
        return app.importedSongs.isEmpty() ? null : app.importedSongs.get(0);
    }

    /** A song saved in a file set, listed under Imported songs and built from the set's patterns. */
    Engine.ImportedSong addFileSetSong(String source, Engine.FileSetSong song) {
        Engine.ImportedSong importedSong = new Engine.ImportedSong();
        importedSong.id = Engine.newLearnedId();
        importedSong.name = Engine.uniqueImportedName(song.name, app.importedSongs);
        importedSong.fileSet = source;
        importedSong.fileSetSong = song.name;
        importedSong.parts.addAll(Engine.resolveSong(song, Engine.learnedFrom(app.learned, source), Engine.fillsFrom(app.learnedFills, source)));
        app.importedSongs.add(0, importedSong);
        while (app.importedSongs.size() > Engine.MAX_IMPORTED_SONGS) {
            app.importedSongs.remove(app.importedSongs.size() - 1);
        }
        if (app.importedSongId == null) app.importedSongId = importedSong.id;
        app.persistence.persistLearned();
        return importedSong;
    }

    /** Opening a song saved in a file set builds it again from the set's patterns, so their edits show. */
    void refreshFromFileSet(Engine.ImportedSong importedSong) {
        if (importedSong == null || importedSong.fileSet == null || importedSong.fileSetSong == null) return;
        List<Engine.FileSetSong> songs = Engine.fileSetSongs.get(importedSong.fileSet);
        if (songs == null) return;
        for (Engine.FileSetSong song : songs) {
            if (!song.name.equals(importedSong.fileSetSong)) continue;
            importedSong.parts.clear();
            importedSong.parts.addAll(Engine.resolveSong(song, Engine.learnedFrom(app.learned, importedSong.fileSet), Engine.fillsFrom(app.learnedFills, importedSong.fileSet)));
            return;
        }
    }

    /** Save to set: the song goes into a file set, as references to the set's patterns and fills. */
    void saveSongToFileSet() {
        List<Engine.Part> song = this.activeSong();
        if (song.isEmpty()) {
            Toast.makeText((Context)app, (CharSequence)"The song is empty", (int)0).show();
            return;
        }
        List<String> sources = Engine.fileSetSources(app.learned, app.learnedFills);
        if (sources.isEmpty()) {
            Toast.makeText((Context)app, (CharSequence)"Import a MIDI to make a file set first", (int)1).show();
            return;
        }
        Engine.ImportedSong imported = "imported".equals(app.songLane) ? this.importedSong() : null;
        String best = imported != null && imported.fileSet != null && sources.contains(imported.fileSet) ? imported.fileSet : this.bestFileSetFor(song, sources);
        ArrayList<String> order = new ArrayList<String>();
        if (best != null) order.add(best);
        for (String s : sources) if (!order.contains(s)) order.add(s);
        String[] names = new String[order.size()];
        for (int i = 0; i < names.length; i++) names[i] = order.get(i).isEmpty() ? "Other" : order.get(i);
        String songName = imported != null ? (imported.fileSetSong != null ? imported.fileSetSong : imported.name) : "Song";
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)("Save \"" + songName + "\" to file set")).setItems((CharSequence[])names, (d, which) -> {
            if (which >= 0 && which < order.size()) this.saveSongInto(order.get(which), songName, imported);
        }).setNegativeButton((CharSequence)"Cancel", null).show();
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
        if (outside.isEmpty()) {
            this.storeFileSetSong(source, saved, imported);
            return;
        }
        String set = source.isEmpty() ? "Other" : source;
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)"Parts from outside the file set")
            .setMessage((CharSequence)(outside.size() + (outside.size() == 1 ? " part uses a pattern or fill" : " parts use patterns or fills") + " that are not in " + set + ". Copy them into it? Otherwise their notes are kept in the song."))
            .setPositiveButton((CharSequence)"Copy into set", (d, w) -> {
                this.copyPartsIntoSet(source, song, outside);
                this.storeFileSetSong(source, Engine.songForFileSet(songName, song, Engine.learnedFrom(app.learned, source), Engine.fillsFrom(app.learnedFills, source)), imported);
            })
            .setNegativeButton((CharSequence)"Keep in song", (d, w) -> this.storeFileSetSong(source, saved, imported))
            .show();
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
        app.importLibrary.rebuildImported();
        app.importLibrary.rebuildImportedFills();
    }

    void storeFileSetSong(String source, Engine.FileSetSong saved, Engine.ImportedSong imported) {
        Engine.putFileSetSong(source, saved);
        if (imported != null) {
            imported.fileSet = source;
            imported.fileSetSong = saved.name;
        } else {
            Engine.ImportedSong added = this.addFileSetSong(source, saved);
            app.importedSongId = added.id;
        }
        app.fileSets.persistFsetInfo();
        app.persistence.persistLearned();
        this.refreshSong();
        int refs = 0;
        for (Engine.SongRef r : saved.parts) if (r.usePattern != null || r.useFill != null) refs++;
        app.setNow("Saved \u00b7 " + saved.name + " in " + (source.isEmpty() ? "Other" : source) + " \u00b7 " + refs + " of " + saved.parts.size() + " parts follow the set");
    }

    void addImportedArrangement(String string, List<Engine.Part> list) {
        Engine.ImportedSong importedSong = new Engine.ImportedSong();
        importedSong.id = Engine.newLearnedId();
        importedSong.name = Engine.uniqueImportedName(string, app.importedSongs);
        importedSong.parts.addAll(list);
        app.importedSongs.add(0, importedSong);
        while (app.importedSongs.size() > 8) {
            app.importedSongs.remove(app.importedSongs.size() - 1);
        }
        app.importedSongId = importedSong.id;
        app.songLane = "imported";
        app.persistence.persistLearned();
        app.show("song");
        if (!importedSong.parts.isEmpty()) {
            app.playback.applyPart(importedSong.parts.get(0));
        }
        this.refreshSong();
        app.setNow("Imported song \u00b7 " + importedSong.name);
    }

    void attachSongPick(TextView textView, int n) {
        this.attachSongPick(textView, n, null);
    }

    void attachSongPick(TextView textView, int n, String string) {
        textView.setOnLongClickListener(view -> {
            if (!"original".equals(app.songLane)) {
                return true;
            }
            this.showSongPick(n, string);
            return true;
        });
    }

    void addCurrentFillern() {
        String mode = app.styleLibrary.fillernModeOf(app.styleLibrary.currentPatternKey());
        if (!Engine.FILLERN_AFTER.equals(mode)) {
            // The fill goes into the pattern's last or first bar: the part keeps its 4 bars.
            for (Engine.Part p : Engine.fillernParts(app.styles.get((Object)app.style).label, app.bpm(), app.cells, app.steps, 4,
                    app.styleLibrary.fillLabel(app.fillId), app.fillPat, Engine.barSteps(app.tsNum, app.tsDen), mode)) {
                this.add(p);
            }
            return;
        }
        Engine.Part part = Engine.groove(app.styles.get((Object)app.style).label, app.bpm(), app.cells, 4);
        part.lens = Engine.copyCells(app.lens);
        Engine.Part part2 = Engine.fill(app.styleLibrary.fillLabel(app.fillId), app.bpm(), app.fillPat, 1);
        part2.lens = Engine.copyCells(app.fillLens);
        this.add(part);
        this.add(part2);
    }

    void showSongPick(int n) {
        this.showSongPick(n, null);
    }

    /** Long press on a part of an imported song: replace it with a Fillern. */
    void showImportedPartMenu(int n) {
        List<Engine.Part> list = this.activeSong();
        if (n < 0 || n >= list.size()) return;
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)list.get(n).name).setItems(new CharSequence[]{"Replace with fillern"}, (d, which) -> this.pickFillernFor(n)).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    /** Fillerns to choose from: patterns with a fill chosen for them, file sets first, then built-in. */
    void pickFillernFor(int n) {
        ArrayList<String> keys = new ArrayList<String>();
        ArrayList<CharSequence> names = new ArrayList<CharSequence>();
        for (Engine.Learned learned : app.learned) {
            String key = "l:" + learned.id;
            String fill = app.styleLibrary.selectedFillFor(key);
            if (fill == null) continue;
            keys.add(key);
            names.add(learned.name + " \u00b7 " + app.styleLibrary.fillLabel(fill) + Engine.fillernModeNote(app.styleLibrary.fillernModeOf(key)));
        }
        for (String id : app.styles.keySet()) {
            String key = app.styleLibrary.patternKeyFor(id);
            if (keys.contains(key)) continue;
            String fill = app.styleLibrary.selectedFillFor(key);
            if (fill == null) continue;
            keys.add(key);
            names.add(this.patternName(key) + " \u00b7 " + app.styleLibrary.fillLabel(fill) + Engine.fillernModeNote(app.styleLibrary.fillernModeOf(key)));
        }
        if (keys.isEmpty()) {
            Toast.makeText((Context)app, (CharSequence)"No Fillerns yet: make one on the Fillern tab", (int)1).show();
            return;
        }
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)"Replace with fillern").setItems(names.toArray(new CharSequence[0]), (d, which) -> {
            if (which >= 0 && which < keys.size()) this.replaceWithFillern(n, keys.get(which));
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    /**
     * Puts a Fillern where part n was. Its type decides: "replaces end/start" keep the part's
     * bars; "add after" adds the fill bar.
     */
    void replaceWithFillern(int n, String key) {
        List<Engine.Part> list = this.activeSong();
        if (n < 0 || n >= list.size()) return;
        Engine.Part old = list.get(n);
        String fillKey = app.styleLibrary.selectedFillFor(key);
        if (fillKey == null) return;
        int[][] cells = this.patternCellsFor(key);
        int steps = Engine.usedSteps(cells);
        int bar = Engine.barSteps(old.tsNum > 0 ? old.tsNum : app.tsNum, old.tsDen > 0 ? old.tsDen : app.tsDen);
        List<Engine.Part> parts = Engine.fillernParts(this.patternName(key), old.bpm, cells, steps, Math.max(1, old.repeats),
            app.styleLibrary.fillLabel(fillKey), this.songFillCells(fillKey, cells), bar, app.styleLibrary.fillernModeOf(key));
        for (Engine.Part p : parts) {
            p.tsNum = old.tsNum;
            p.tsDen = old.tsDen;
            if ("fill".equals(p.kind)) p.steps = bar;
        }
        list.remove(n);
        list.addAll(n, parts);
        if ("imported".equals(app.songLane)) app.persistence.persistLearned();
        this.refreshSong();
        app.setNow(old.name + " \u2192 " + this.patternName(key) + " Fillern");
    }

    void showSongPick(int n2, String string) {
        if (!"original".equals(app.songLane)) {
            return;
        }
        boolean bl = string == null || string.isEmpty();
        ArrayList<String> arrayList = new ArrayList<String>();
        ArrayList<Runnable> arrayList2 = new ArrayList<Runnable>();
        if (bl || "pattern".equals(string)) {
            if (bl) {
                arrayList.add("— Pattern —");
                arrayList2.add(null);
            } else {
                arrayList.add("— Built-in —");
                arrayList2.add(null);
            }
            boolean bl2 = bl;
            boolean bl3 = bl;
            for (String string2 : Engine.styles().keySet()) {
                Engine.Style style = app.styles.get(string2);
                if (style == null) continue;
                String key = "s:" + string2;
                arrayList.add(style.label);
                arrayList2.add(() -> this.applySongPick("pattern", key, n2));
            }
            for (Engine.Learned learned : app.variatedPatterns) {
                if (!bl2 && !bl) {
                    arrayList.add("— Variated —");
                    arrayList2.add(null);
                    bl2 = true;
                }
                arrayList.add(learned.name);
                arrayList2.add(() -> this.applySongPick("pattern", "v:" + learned.id, n2));
            }
            if (!app.learned.isEmpty()) {
                if (!bl3 && !bl) {
                    arrayList.add("— Imported —");
                    arrayList2.add(null);
                    bl3 = true;
                }
                LinkedHashMap<String, ArrayList<Engine.Learned>> linkedHashMap = new LinkedHashMap<String, ArrayList<Engine.Learned>>();
                for (Engine.Learned learned : app.learned) {
                    String src = Engine.sourceOf(learned);
                    if (src.isEmpty()) {
                        src = "Other";
                    }
                    ArrayList<Engine.Learned> group = linkedHashMap.get(src);
                    if (group == null) {
                        group = new ArrayList<Engine.Learned>();
                        linkedHashMap.put(src, group);
                    }
                    group.add(learned);
                }
                for (Map.Entry<String, ArrayList<Engine.Learned>> entry : linkedHashMap.entrySet()) {
                    arrayList.add("— " + entry.getKey() + " —");
                    arrayList2.add(null);
                    for (Engine.Learned learned : entry.getValue()) {
                        arrayList.add("  " + learned.name);
                        arrayList2.add(() -> this.applySongPick("pattern", "l:" + learned.id, n2));
                    }
                }
            }
        }
        if (bl || "fillern".equals(string)) {
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
            linkedHashSet.add(app.styleLibrary.currentPatternKey());
            linkedHashSet.addAll(app.fillernPairs.keySet());
            ArrayList<String[]> arrayList3 = new ArrayList<String[]>();
            LinkedHashSet<String> linkedHashSet2 = new LinkedHashSet<String>();
            for (String string3 : linkedHashSet) {
                if (string3 == null || !linkedHashSet2.add(string3) || !app.styleLibrary.fillernUnderlined(string3)) continue;
                String fillKey = app.styleLibrary.fillernFillKeyOf(string3);
                arrayList3.add(new String[]{app.styleLibrary.songPickSection(string3), string3, this.patternName(string3) + " · " + app.styleLibrary.fillLabel(fillKey)});
            }
            if (arrayList3.isEmpty()) {
                if ("fillern".equals(string)) {
                    Toast.makeText((Context)app, (CharSequence)"No underlined Fillerns yet", (int)0).show();
                    return;
                }
            } else if (bl) {
                arrayList.add("— Fillern —");
                arrayList2.add(null);
                ArrayList<String[]> arrayList5 = new ArrayList<String[]>();
                for (String[] row : arrayList3) {
                    if ("Imported".equals(row[0])) {
                        arrayList5.add(row);
                        continue;
                    }
                    String string4 = row[1];
                    arrayList.add(row[2]);
                    arrayList2.add(() -> this.applySongPick("fillern", string4, n2));
                }
                this.addImportedSongRows(arrayList, arrayList2, arrayList5, "fillern", n2);
            } else {
                for (String string5 : new String[]{"Built-in", "Variated"}) {
                    boolean bl2 = false;
                    for (String[] stringArray : arrayList3) {
                        if (!string5.equals(stringArray[0])) continue;
                        if (!bl2) {
                            arrayList.add("— " + string5 + " —");
                            arrayList2.add(null);
                            bl2 = true;
                        }
                        String string6 = stringArray[1];
                        arrayList.add(stringArray[2]);
                        arrayList2.add(() -> this.applySongPick("fillern", string6, n2));
                    }
                }
                ArrayList<String[]> arrayList6 = new ArrayList<String[]>();
                for (String[] stringArray : arrayList3) {
                    if (!"Imported".equals(stringArray[0])) continue;
                    arrayList6.add(stringArray);
                }
                if (!arrayList6.isEmpty()) {
                    arrayList.add("— Imported —");
                    arrayList2.add(null);
                    this.addImportedSongRows(arrayList, arrayList2, arrayList6, "fillern", n2);
                }
            }
        }
        if (bl || "fill".equals(string)) {
            ArrayList<String[]> arrayList4 = new ArrayList<String[]>();
            for (int i = 0; i < Engine.FILL_ID.length; ++i) {
                arrayList4.add(new String[]{"Built-in", Engine.FILL_ID[i], Engine.FILL_LABEL[i]});
            }
            for (Engine.LearnedFill learnedFill : app.variatedFills) {
                arrayList4.add(new String[]{"Variated", "v:" + learnedFill.id, learnedFill.name});
            }
            for (Engine.LearnedFill learnedFill : app.learnedFills) {
                arrayList4.add(new String[]{"Imported", "l:" + learnedFill.id, learnedFill.name});
            }
            if (arrayList4.isEmpty()) {
                if ("fill".equals(string)) {
                    Toast.makeText((Context)app, (CharSequence)"No fills on the Fills tab", (int)0).show();
                    return;
                }
            } else if (bl) {
                arrayList.add("— Fill —");
                arrayList2.add(null);
                ArrayList<String[]> imported = new ArrayList<String[]>();
                for (String[] stringArray : arrayList4) {
                    if ("Imported".equals(stringArray[0])) {
                        imported.add(stringArray);
                        continue;
                    }
                    String string7 = stringArray[1];
                    arrayList.add(stringArray[2]);
                    arrayList2.add(() -> this.applySongPick("fill", string7, n2));
                }
                this.addImportedSongRows(arrayList, arrayList2, imported, "fill", n2);
            } else {
                for (String string8 : new String[]{"Built-in", "Variated"}) {
                    boolean bl4 = false;
                    for (String[] stringArray : arrayList4) {
                        if (!string8.equals(stringArray[0])) continue;
                        if (!bl4) {
                            arrayList.add("— " + string8 + " —");
                            arrayList2.add(null);
                            bl4 = true;
                        }
                        String string9 = stringArray[1];
                        arrayList.add(stringArray[2]);
                        arrayList2.add(() -> this.applySongPick("fill", string9, n2));
                    }
                }
                ArrayList<String[]> imported = new ArrayList<String[]>();
                for (String[] stringArray : arrayList4) {
                    if (!"Imported".equals(stringArray[0])) continue;
                    imported.add(stringArray);
                }
                if (!imported.isEmpty()) {
                    arrayList.add("— Imported —");
                    arrayList2.add(null);
                    this.addImportedSongRows(arrayList, arrayList2, imported, "fill", n2);
                }
            }
        }
        if (arrayList.isEmpty()) {
            return;
        }
        String string4 = n2 >= 0 ? "Replace part" : ("fillern".equals(string) ? "Fillern" : ("fill".equals(string) ? "Fill" : ("pattern".equals(string) ? "Pattern" : "Add to song")));
        new AlertDialog.Builder((Context)app).setTitle((CharSequence)string4).setItems(arrayList.toArray(new CharSequence[0]), (dialogInterface, n) -> {
            if (n < 0 || n >= arrayList2.size()) {
                return;
            }
            Runnable runnable = (Runnable)arrayList2.get(n);
            if (runnable != null) {
                runnable.run();
            }
        }).setNegativeButton((CharSequence)"Cancel", null).show();
    }

    String songFileLabel(String string, boolean bl) {
        block5: {
            if (string == null) {
                return "Other";
            }
            if (!string.startsWith("l:")) break block5;
            String string2 = string.substring(2);
            if (bl) {
                for (Engine.LearnedFill learnedFill : app.learnedFills) {
                    if (!string2.equals(learnedFill.id)) continue;
                    String string3 = Engine.sourceOf(learnedFill);
                    return string3.isEmpty() ? "Other" : string3;
                }
            } else {
                for (Engine.Learned learned : app.learned) {
                    if (!string2.equals(learned.id)) continue;
                    String string4 = Engine.sourceOf(learned);
                    return string4.isEmpty() ? "Other" : string4;
                }
            }
        }
        return "Other";
    }

    void addImportedSongRows(List<String> list, List<Runnable> list2, List<String[]> list3, String string, int n) {
        LinkedHashMap<String, ArrayList<String[]>> linkedHashMap = new LinkedHashMap<String, ArrayList<String[]>>();
        for (String[] object : list3) {
            String string2 = this.songFileLabel(object[1], "fill".equals(string));
            ArrayList<String[]> group = linkedHashMap.get(string2);
            if (group == null) {
                group = new ArrayList<String[]>();
                linkedHashMap.put(string2, group);
            }
            group.add(object);
        }
        for (Map.Entry<String, ArrayList<String[]>> entry : linkedHashMap.entrySet()) {
            list.add("— " + entry.getKey() + " —");
            list2.add(null);
            for (String[] stringArray : entry.getValue()) {
                String string3 = stringArray[1];
                list.add("  " + stringArray[2]);
                list2.add(() -> this.applySongPick(string, string3, n));
            }
        }
    }

    String patternName(String string) {
        if (string == null) {
            return "Pattern";
        }
        if (string.startsWith("s:")) {
            Engine.Style style = app.styles.get(string.substring(2));
            return style != null ? style.label : string.substring(2);
        }
        if (string.startsWith("l:")) {
            for (Engine.Learned learned : app.learned) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.name;
            }
        }
        if (string.startsWith("v:")) {
            for (Engine.Learned learned : app.variatedPatterns) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.name;
            }
        }
        Engine.Style style = app.styles.get(app.style);
        return style != null ? style.label : "Pattern";
    }

    int patternBpm(String string) {
        Engine.Style iterator;
        if (string != null && string.startsWith("s:") && (iterator = app.styles.get(string.substring(2))) != null) {
            return iterator.bpm;
        }
        if (string != null && string.startsWith("l:")) {
            for (Engine.Learned learned : app.learned) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.bpm;
            }
        }
        if (string != null && string.startsWith("v:")) {
            for (Engine.Learned learned : app.variatedPatterns) {
                if (!learned.id.equals(string.substring(2))) continue;
                return learned.bpm;
            }
        }
        return app.bpm();
    }

    int[][] patternCellsFor(String string) {
        Engine.Style iterator;
        if (string != null && string.startsWith("s:") && (iterator = app.styles.get(string.substring(2))) != null) {
            return Engine.styleCells(iterator);
        }
        if (string != null && string.startsWith("l:")) {
            for (Engine.Learned learned : app.learned) {
                if (!learned.id.equals(string.substring(2))) continue;
                return Engine.copyCells(learned.cells);
            }
        }
        if (string != null && string.startsWith("v:")) {
            for (Engine.Learned learned : app.variatedPatterns) {
                if (!learned.id.equals(string.substring(2))) continue;
                return Engine.copyCells(learned.cells);
            }
        }
        return Engine.copyCells(app.cells);
    }

    int[][] songFillCells(String string, int[][] nArray) {
        if (string != null && (string.startsWith("l:") || string.startsWith("v:"))) {
            return app.styleLibrary.fillCellsFor(string);
        }
        return Engine.buildFill(string, nArray != null ? nArray : app.cells, app.style);
    }

    void applySongPick(String string, String string2, int n) {
        if (!"original".equals(app.songLane)) {
            return;
        }
        List<Engine.Part> list = this.activeSong();
        int n2 = n;
        if (n2 < 0 || n2 >= list.size()) {
            n2 = -1;
        }
        if ("pattern".equals(string)) {
            int n3 = n2 >= 0 && "groove".equals(list.get((int)n2).kind) ? list.get((int)n2).repeats : 4;
            Engine.Part part = Engine.groove(this.patternName(string2), this.patternBpm(string2), this.patternCellsFor(string2), n3);
            if (n2 >= 0) {
                list.set(n2, part);
            } else {
                this.add(part);
            }
        } else if ("fill".equals(string)) {
            int[][] nArray = n2 >= 0 ? list.get((int)n2).cells : app.cells;
            int n4 = n2 >= 0 && "fill".equals(list.get((int)n2).kind) ? list.get((int)n2).repeats : 1;
            int n5 = n2 >= 0 ? list.get((int)n2).bpm : app.bpm();
            Engine.Part part = Engine.fill(app.styleLibrary.fillLabel(string2), n5, this.songFillCells(string2, nArray), n4);
            if (n2 >= 0) {
                list.set(n2, part);
            } else {
                this.add(part);
            }
        } else {
            int[][] nArray = this.patternCellsFor(string2);
            String string3 = app.fillernPairs.get(string2);
            if (string3 == null) {
                string3 = string2.equals(app.styleLibrary.currentPatternKey()) ? app.fillId : "toms";
            }
            int n6 = n2 >= 0 && "groove".equals(list.get((int)n2).kind) ? list.get((int)n2).repeats : 4;
            String mode = app.styleLibrary.fillernModeOf(string2);
            if (!Engine.FILLERN_AFTER.equals(mode)) {
                // The fill replaces the end or start of the pattern: the song part keeps its length.
                int steps = Engine.usedSteps(nArray);
                List<Engine.Part> parts = Engine.fillernParts(this.patternName(string2), this.patternBpm(string2), nArray, steps, n6,
                    app.styleLibrary.fillLabel(string3), this.songFillCells(string3, nArray), Engine.barSteps(app.tsNum, app.tsDen), mode);
                if (n2 >= 0) {
                    list.set(n2, parts.get(0));
                    for (int i = 1; i < parts.size() && list.size() < Engine.MAX_SONG; ++i) list.add(n2 + i, parts.get(i));
                } else {
                    for (Engine.Part p : parts) this.add(p);
                }
                this.refreshSong();
                app.setNow(this.patternName(string2) + " Fillern");
                return;
            }
            Engine.Part part = Engine.groove(this.patternName(string2), this.patternBpm(string2), nArray, n6);
            Engine.Part part2 = Engine.fill(app.styleLibrary.fillLabel(string3), this.patternBpm(string2), this.songFillCells(string3, nArray), 1);
            if (n2 >= 0) {
                list.set(n2, part);
                if (list.size() < Engine.MAX_SONG) {
                    list.add(n2 + 1, part2);
                }
            } else {
                this.add(part);
                this.add(part2);
            }
        }
        this.refreshSong();
        app.setNow("fillern".equals(string) ? this.patternName(string2) + " Fillern" : ("fill".equals(string) ? app.styleLibrary.fillLabel(string2) : this.patternName(string2)));
    }

    void add(Engine.Part part) {
        List<Engine.Part> list = this.activeSong();
        if ("imported".equals(app.songLane) && this.importedSong() == null) {
            Toast.makeText((Context)app, (CharSequence)"No imported song yet", (int)0).show();
            return;
        }
        if (list.size() >= Engine.MAX_SONG) {
            Toast.makeText((Context)app, (CharSequence)"Song is full", (int)0).show();
            return;
        }
        list.add(part);
        part.tsNum = app.tsNum;
        part.tsDen = app.tsDen;
        part.steps = "groove".equals(part.kind) ? app.steps : Engine.barSteps(app.tsNum, app.tsDen);
        if ("imported".equals(app.songLane)) {
            app.persistence.persistLearned();
        }
        this.refreshSong();
    }

    void refreshSongBase() {
        List<Engine.Part> list;
        if (app.songAdds != null) {
            boolean bl = "edit".equals(app.songMode) && (!"imported".equals(app.songLane) || !app.importedSongs.isEmpty());
            app.songAdds.setVisibility(bl ? 0 : 8);
        }
        if (app.songPane != null) {
            LinearLayout linearLayout = (LinearLayout)app.songPane.getChildAt(0);
            for (int i = 0; i < linearLayout.getChildCount(); ++i) {
                View view = linearLayout.getChildAt(i);
                if (!(view instanceof TextView) || view.getTag() == null) continue;
                app.paintChip((TextView)view, app.songMode.equals(view.getTag()));
            }
            if (app.songPane.getChildCount() > 1 && app.songPane.getChildAt(1) instanceof LinearLayout) {
                LinearLayout linearLayout2 = (LinearLayout)app.songPane.getChildAt(1);
                for (int i = 0; i < linearLayout2.getChildCount(); ++i) {
                    View object2 = linearLayout2.getChildAt(i);
                    if (!(object2 instanceof TextView) || object2.getTag() == null) continue;
                    app.paintChip((TextView)object2, app.songLane.equals(object2.getTag()));
                }
            }
        }
        app.timeline.removeAllViews();
        app.songCards.removeAllViews();
        if ("imported".equals(app.songLane) && !app.importedSongs.isEmpty()) {
            LinearLayout linearLayout = app.row();
            for (Engine.ImportedSong importedSong : app.importedSongs) {
                TextView object = app.pill(importedSong.name, importedSong.id.equals(app.importedSongId), arg_0 -> this.refreshSongAction75(importedSong, arg_0));
                linearLayout.addView((View)object);
            }
            app.songCards.addView((View)linearLayout);
        }
        if ((list = this.activeSong()).isEmpty()) {
            TextView textView = app.text("imported".equals(app.songLane) ? "No imported song yet. After a song MIDI, choose Make song." : "Empty song \u2014 add the current pattern, a fill, or silence.", 13, false);
            textView.setTextColor(MUTED);
            textView.setPadding(0, app.dp(8), 0, 0);
            app.songCards.addView((View)textView);
            app.setNow("Song");
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        for (int i = 0; i < list.size(); ++i) {
            Engine.Part object2 = list.get(i);
            String object = object2.kind + ":" + object2.name;
            if (!arrayList.contains(object)) {
                arrayList.add(object);
            }
            int n = arrayList.indexOf(object) + 1;
            boolean bl = app.songPlay && i == app.songIndex;
            LinearLayout linearLayout = app.col();
            TextView textView = app.text(Integer.toString(n), 15, true);
            textView.setGravity(17);
            textView.setMinWidth(app.dp(44));
            textView.setPadding(app.dp(10), app.dp(8), app.dp(10), app.dp(8));
            textView.setBackground((Drawable)app.round(bl ? HIT : (Engine.partHasFill(object2) ? Engine.FILL_CELL_COLOR : ELEV), 0));
            textView.setTextColor(bl ? BG : FG);
            TextView textView2 = app.text(((Engine.Part)object2).name, 10, false);
            textView2.setGravity(17);
            textView2.setPadding(app.dp(4), app.dp(6), app.dp(4), app.dp(6));
            textView2.setTextColor(bl ? FG : MUTED);
            textView2.setBackground((Drawable)app.round(bl ? Color.parseColor((String)"#2A322C") : 0, 0));
            linearLayout.addView((View)textView);
            linearLayout.addView((View)textView2);
            LinearLayout.LayoutParams layoutParams = app.wrap();
            layoutParams.setMargins(app.dp(1), 0, app.dp(1), 0);
            if ("imported".equals(app.songLane) && "edit".equals(app.songMode)) {
                final int at = i;
                linearLayout.setOnLongClickListener(view -> {
                    this.showImportedPartMenu(at);
                    return true;
                });
            }
            app.timeline.addView((View)linearLayout, (ViewGroup.LayoutParams)layoutParams);
            app.songCards.addView((View)this.partCard(i, n, (Engine.Part)object2, bl));
        }
        app.setNow("play".equals(app.songMode) ? (app.songPlay && app.songIndex < list.size() ? list.get((int)app.songIndex).name : "Play mode") : list.size() + " parts");
    }

    LinearLayout partCardBase(int n, int n2, Engine.Part part, boolean bl) {
        LinearLayout linearLayout = app.row();
        linearLayout.setBackground((Drawable)app.round(bl ? Color.parseColor((String)"#2A322C") : (Engine.partHasFill(part) ? Engine.FILL_CELL_COLOR : ELEV), 12));
        linearLayout.setPadding(app.dp(10), app.dp(10), app.dp(10), app.dp(10));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = app.dp(6);
        linearLayout.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
        TextView textView = app.text(Integer.toString(n2), 16, true);
        textView.setTextColor(bl ? HIT : FG);
        textView.setWidth(app.dp(28));
        LinearLayout linearLayout2 = app.col();
        linearLayout2.addView((View)app.text(part.name, 14, true));
        TextView textView2 = app.text("\u00d7" + part.repeats + "  \u00b7  " + part.bpm + " BPM", 11, false);
        textView2.setTextColor(MUTED);
        linearLayout2.addView((View)textView2);
        linearLayout.addView((View)textView);
        linearLayout.addView((View)linearLayout2, (ViewGroup.LayoutParams)app.flex(1));
        if ("original".equals(app.songLane) && "edit".equals(app.songMode)) {
            linearLayout.setOnLongClickListener(view -> {
                this.showSongPick(n);
                return true;
            });
        } else if ("imported".equals(app.songLane) && "edit".equals(app.songMode)) {
            linearLayout.setOnLongClickListener(view -> {
                this.showImportedPartMenu(n);
                return true;
            });
        }
        if ("edit".equals(app.songMode)) {
            TextView textView3 = app.outline("\u2212", false, view -> {
                part.repeats = Engine.clamp(part.repeats - 1, 1, 32);
                if ("imported".equals(app.songLane)) {
                    app.persistence.persistLearned();
                }
                this.refreshSong();
            });
            TextView textView4 = app.outline("+", false, view -> {
                part.repeats = Engine.clamp(part.repeats + 1, 1, 32);
                if ("imported".equals(app.songLane)) {
                    app.persistence.persistLearned();
                }
                this.refreshSong();
            });
            TextView textView5 = app.outline("\u2715", false, view -> {
                List<Engine.Part> list = this.activeSong();
                if (n >= 0 && n < list.size()) {
                    list.remove(n);
                }
                if ("imported".equals(app.songLane)) {
                    app.persistence.persistLearned();
                }
                this.refreshSong();
            });
            linearLayout.addView((View)textView3);
            linearLayout.addView((View)textView4);
            linearLayout.addView((View)textView5);
        }
        return linearLayout;
    }

    List<Engine.Part> songPartsForExport() {
        List<Engine.Part> list = this.activeSong();
        if (!list.isEmpty()) {
            return list;
        }
        String string = app.projectIo.exportName("mid").replace(".mid", "");
        return Collections.singletonList(Engine.groove(string, app.bpm(), app.cells, 1));
    }

    private /* synthetic */ void refreshSongAction75(Engine.ImportedSong importedSong, View view) {
        this.refreshFromFileSet(importedSong);
        app.importedSongId = importedSong.id;
        app.songLane = "imported";
        this.refreshSong();
    }

    private /* synthetic */ void showSongPickAction65(String string, int n) {
        this.applySongPick("pattern", string, n);
    }

    boolean pkPartNow;

    void refreshSong() {
        this.refreshSongBase();
        this.afterRefreshSong();
    }

    LinearLayout partCard(int n, int n2, Engine.Part part, boolean bl) {
        this.pkPartNow = bl;
        LinearLayout card = this.partCardBase(n, n2, part, bl);
        card.setTag("part-card-" + n);
        this.afterPartCard(card, this.pkPartNow);
        return card;
    }

    public void addImportedMidiSong(java.lang.String name, java.util.List parts) {
        this.addImportedArrangement(name, parts);
    }

    void afterRefreshSong() {
        if (!"song".equals(app.view)) return;
        java.util.List parts = this.activeSong();
        if ("imported".equals(app.songLane) && app.importedSongs.isEmpty()) {
            app.setNow("No imported song yet");
            return;
        }
        int idx = app.songPlay ? app.songIndex : -1;
        if (idx >= 0) this.followPlayingPart(idx);
        int loop = app.songPlay ? app.songLoop : 0;
        int step = app.playhead < 0 ? 0 : app.playhead;
        int global = pulsekit.Engine.songGlobalStep(parts, idx, loop, step);
        app.setNow(pulsekit.Engine.songNowLine(parts, app.songPlay, idx, global));
    }

    /** Keeps the playing part in view: the cell strip and the part list scroll with the song. */
    void followPlayingPart(int idx) {
        if (app.timeline != null && app.timeline.getParent() instanceof android.widget.HorizontalScrollView && idx < app.timeline.getChildCount()) {
            android.widget.HorizontalScrollView strip = (android.widget.HorizontalScrollView) app.timeline.getParent();
            android.view.View cell = app.timeline.getChildAt(idx);
            this.afterLayout(strip, () -> strip.smoothScrollTo(Math.max(0, cell.getLeft() - strip.getWidth() / 3), 0));
        }
        if (app.songCards != null && app.songCards.getParent() instanceof android.widget.ScrollView) {
            android.widget.ScrollView list = (android.widget.ScrollView) app.songCards.getParent();
            android.view.View card = app.songCards.findViewWithTag("part-card-" + idx);
            if (card != null) this.afterLayout(list, () -> list.smoothScrollTo(0, Math.max(0, card.getTop() - list.getHeight() / 3)));
        }
    }

    /** Runs r once the view has been laid out again: the song views are rebuilt on every part change. */
    void afterLayout(final android.view.View v, final Runnable r) {
        v.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                v.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                r.run();
            }
        });
        v.requestLayout();
    }

    void afterPartCard(android.widget.LinearLayout card, boolean now) {
        if (!now || card == null) return;
        android.widget.TextView lab = app.text("Now", 10, true);
        lab.setTextColor(HIT);
        int at = card.getChildCount() > 2 ? 2 : card.getChildCount();
        card.addView((android.view.View) lab, at);
    }
}
