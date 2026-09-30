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
            Engine.Part part = Engine.groove(this.patternName(string2), this.patternBpm(string2), nArray, n6);
            Engine.Part part2 = Engine.fill(app.styleLibrary.fillLabel(string3), this.patternBpm(string2), this.songFillCells(string3, nArray), 1);
            if (n2 >= 0) {
                list.set(n2, part);
                if (list.size() < 24) {
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
        if (list.size() >= 24) {
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
            textView.setBackground((Drawable)app.round(bl ? HIT : ELEV, 0));
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
            app.timeline.addView((View)linearLayout, (ViewGroup.LayoutParams)layoutParams);
            app.songCards.addView((View)this.partCard(i, n, (Engine.Part)object2, bl));
        }
        app.setNow("play".equals(app.songMode) ? (app.songPlay && app.songIndex < list.size() ? list.get((int)app.songIndex).name : "Play mode") : list.size() + " parts");
    }

    LinearLayout partCardBase(int n, int n2, Engine.Part part, boolean bl) {
        LinearLayout linearLayout = app.row();
        linearLayout.setBackground((Drawable)app.round(bl ? Color.parseColor((String)"#2A322C") : ELEV, 12));
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
        int loop = app.songPlay ? app.songLoop : 0;
        int step = app.playhead < 0 ? 0 : app.playhead;
        int global = pulsekit.Engine.songGlobalStep(parts, idx, loop, step);
        app.setNow(pulsekit.Engine.songNowLine(parts, app.songPlay, idx, global));
    }

    void afterPartCard(android.widget.LinearLayout card, boolean now) {
        if (!now || card == null) return;
        android.widget.TextView lab = app.text("Now", 10, true);
        lab.setTextColor(HIT);
        int at = card.getChildCount() > 2 ? 2 : card.getChildCount();
        card.addView((android.view.View) lab, at);
    }
}
