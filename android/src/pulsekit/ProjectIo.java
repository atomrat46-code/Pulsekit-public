package pulsekit;

import android.content.Context;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static pulsekit.MainActivity.*;

/** Opening and saving files: projects, plugins, songs, MIDI and audio decoding. */
final class ProjectIo {
    final MainActivity app;

    ProjectIo(MainActivity app) {
        this.app = app;
    }

    /** Builds the Import page and its imported-files list. */
    void buildImportPane(FrameLayout frameLayout) {
        app.importPane = app.col();
        app.importPane.setVisibility(8);
        app.importPane.addView((View)app.text("Import", 18, true));
        TextView textView21 = app.text("PRJ \u00b7 full project\nPKP \u00b7 plugin pack\nFSET \u00b7 patterns, Fillerns, and fills from one imported file\nMIDI \u00b7 pattern, Fillern or fill (named in the file)\nSNG \u00b7 song\nWAV / MP3 \u00b7 input file for a PyJav program such as MidiDrumGen.java\nSF2 \u00b7 drum samples onto pads\nPY \u00b7 Python script to edit", 14, false);
        textView21.setTextColor(MUTED);
        textView21.setPadding(0, app.dp(8), 0, app.dp(16));
        app.importPane.addView((View)textView21);
        app.importPane.addView((View)app.action("Choose file", FG, BG, view -> this.openFile()));
        app.importedFileList = app.col();
        app.importedFileList.setPadding(0, app.dp(12), 0, 0);
        app.importPane.addView((View)app.importedFileList);
        TextView textView22 = app.action("Try Dub pack", ELEV, FG, view -> this.tryDub());
        LinearLayout.LayoutParams layoutParams4 = app.wrap();
        layoutParams4.setMargins(0, app.dp(8), 0, 0);
        textView22.setLayoutParams((ViewGroup.LayoutParams)layoutParams4);
        app.importPane.addView((View)textView22);
        frameLayout.addView((View)app.importPane);
    }

    /** Builds the Export page. */
    void buildExportPane(FrameLayout frameLayout) {
        app.exportPane = app.col();
        app.exportPane.setVisibility(8);
        app.exportPane.addView((View)app.text("Export", 18, true));
        app.exportPane.addView((View)app.hint("Project"));
        LinearLayout linearLayout17 = app.row();
        linearLayout17.addView((View)app.action("PRJ", HIT, BG, view -> this.saveKind(15)), (ViewGroup.LayoutParams)app.flexBtn());
        linearLayout17.addView((View)app.action("PKP", ELEV, FG, view -> this.saveKind(16)), (ViewGroup.LayoutParams)app.flexBtn());
        linearLayout17.addView((View)app.action("FSET", ELEV, FG, view -> app.importLibrary.saveFset(null)), (ViewGroup.LayoutParams)app.flexBtn());
        app.exportPane.addView((View)linearLayout17);
        app.exportPane.addView((View)app.hint("MIDI"));
        LinearLayout linearLayout18 = app.row();
        linearLayout18.addView((View)app.action("Pattern", ELEV, FG, view -> this.saveMidi("pattern")), (ViewGroup.LayoutParams)app.flexBtn());
        linearLayout18.addView((View)app.action("Fillern", ELEV, FG, view -> this.saveMidi("fillern")), (ViewGroup.LayoutParams)app.flexBtn());
        linearLayout18.addView((View)app.action("Fill", ELEV, FG, view -> this.saveMidi("fill")), (ViewGroup.LayoutParams)app.flexBtn());
        app.exportPane.addView((View)linearLayout18);
        app.exportPane.addView((View)app.hint("This beat"));
        LinearLayout linearLayout19 = app.row();
        linearLayout19.addView((View)app.action("WAV", ELEV, FG, view -> this.saveKind(10)), (ViewGroup.LayoutParams)app.flexBtn());
        linearLayout19.addView((View)app.action("MP3", ELEV, FG, view -> this.saveKind(11)), (ViewGroup.LayoutParams)app.flexBtn());
        app.exportPane.addView((View)linearLayout19);
        LinearLayout linearLayout20 = app.row();
        linearLayout20.setPadding(0, app.dp(8), 0, 0);
        linearLayout20.addView((View)app.action("SF2", ELEV, FG, view -> this.saveKind(12)), (ViewGroup.LayoutParams)app.flexBtn());
        linearLayout20.addView((View)app.action("JAR", ELEV, FG, view -> this.saveKind(18)), (ViewGroup.LayoutParams)app.flexBtn());
        app.exportPane.addView((View)linearLayout20);
        app.exportPane.addView((View)app.hint("Song"));
        LinearLayout linearLayout21 = app.row();
        linearLayout21.addView((View)app.action("SNG", ELEV, FG, view -> this.saveKind(9)), (ViewGroup.LayoutParams)app.flexBtn());
        linearLayout21.addView((View)app.action("Song MIDI", ELEV, FG, view -> this.saveKind(17)), (ViewGroup.LayoutParams)app.flexBtn());
        app.exportPane.addView((View)linearLayout21);
        // The song's drum hits as audio, with the current drum set.
        LinearLayout songAudio = app.row();
        songAudio.setPadding(0, app.dp(8), 0, 0);
        songAudio.addView((View)app.action("Song WAV", ELEV, FG, view -> this.saveKind(20)), (ViewGroup.LayoutParams)app.flexBtn());
        songAudio.addView((View)app.action("Song MP3", ELEV, FG, view -> this.saveKind(21)), (ViewGroup.LayoutParams)app.flexBtn());
        app.exportPane.addView((View)songAudio);
        app.exportPane.addView((View)app.hint("Python"));
        LinearLayout linearLayout22 = app.row();
        linearLayout22.addView((View)app.action("PY", ELEV, FG, view -> this.saveKind(14)), (ViewGroup.LayoutParams)app.flexBtn());
        app.exportPane.addView((View)linearLayout22);
        frameLayout.addView((View)app.exportPane);
    }

    void saveKind(int n) {
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        if (n == 9) {
            intent.setType("application/octet-stream");
            List<Engine.Part> list = app.songEditor.songPartsForExport();
            intent.putExtra("android.intent.extra.TITLE", Engine.songFilename(list, app.songEditor.songFileSet(list)));
        } else if (n == 14) {
            intent.setType("text/x-python");
            intent.putExtra("android.intent.extra.TITLE", app.pyName);
        } else if (n == 15) {
            intent.setType("application/octet-stream");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("prj"));
        } else if (n == 16) {
            intent.setType("application/octet-stream");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("pkp"));
        } else if (n == 19) {
            intent.setType("application/octet-stream");
            String string = app.fsetSaveSource != null ? app.fsetSaveSource : "";
            intent.putExtra("android.intent.extra.TITLE", Engine.fsetFilename(string.isEmpty() ? "Other" : string));
        } else if (n == 18) {
            intent.setType("application/java-archive");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("jar"));
        } else if (n == 17) {
            intent.setType("audio/midi");
            List<Engine.Part> songParts = app.songEditor.songPartsForExport();
            intent.putExtra("android.intent.extra.TITLE", Engine.songFilename(songParts, app.songEditor.songFileSet(songParts)).replace(".sng", ".mid"));
        } else if (n == 20 || n == 21) {
            intent.setType(n == 20 ? "audio/x-wav" : "audio/mpeg");
            List<Engine.Part> songParts = app.songEditor.songPartsForExport();
            intent.putExtra("android.intent.extra.TITLE", Engine.songExportFilename(songParts, app.songEditor.songFileSet(songParts), n == 20 ? "wav" : "mp3"));
        } else if (n == 10) {
            intent.setType("audio/x-wav");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("wav"));
        } else if (n == 11) {
            intent.setType("audio/mpeg");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("mp3"));
        } else if (n == 12) {
            intent.setType("application/octet-stream");
            intent.putExtra("android.intent.extra.TITLE", this.exportName("sf2"));
        } else {
            intent.setType("audio/midi");
            String string = app.styles.containsKey(app.style) ? app.styles.get((Object)app.style).label : app.style;
            intent.putExtra("android.intent.extra.TITLE", Engine.midiFileName(string, app.bpm(), app.midiSaveRole, app.styleLibrary.fillLabel(app.fillId)));
        }
        app.startActivityForResult(intent, n);
    }

    byte[] encodePrj() throws Exception {
        LinkedHashMap<String, byte[]> linkedHashMap = new LinkedHashMap<String, byte[]>();
        String string = app.pyEditor != null ? app.pyEditor.getText().toString() : "";
        linkedHashMap.put("scripts/0-script.py", string.getBytes(StandardCharsets.UTF_8));
        app.playback.fillMissingVoices();
        String string2 = app.styles.containsKey(app.style) ? app.styles.get((Object)app.style).label : "Pulsekit";
        linkedHashMap.put("kit.sf2", AudioIo.encodeSf2(app.voices, string2));
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('[');
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            if (i > 0) {
                stringBuilder.append(',');
            }
            String string3 = "pads/" + i + "-" + Engine.TRACK_ID[i] + ".wav";
            linkedHashMap.put(string3, AudioIo.encodeWav(app.voices[i], 22050));
            stringBuilder.append("{\"id\":").append(Engine.quote(Engine.TRACK_ID[i]));
            stringBuilder.append(",\"note\":").append(Engine.NOTES[i]);
            stringBuilder.append(",\"name\":").append(Engine.quote(Engine.TRACK_LABEL[i]));
            stringBuilder.append(",\"file\":").append(Engine.quote(string3)).append('}');
        }
        stringBuilder.append(']');
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append('[');
        for (int i = 0; i < app.song.size(); ++i) {
            if (i > 0) {
                stringBuilder2.append(',');
            }
            Engine.Part part = app.song.get(i);
            stringBuilder2.append("{\"kind\":").append(Engine.quote(part.kind));
            stringBuilder2.append(",\"name\":").append(Engine.quote(part.name));
            stringBuilder2.append(",\"repeats\":").append(part.repeats);
            stringBuilder2.append(",\"bpm\":").append(part.bpm);
            stringBuilder2.append(",\"steps\":").append(part.steps);
            stringBuilder2.append(",\"tsNum\":").append(part.tsNum);
            stringBuilder2.append(",\"tsDen\":").append(part.tsDen);
            stringBuilder2.append(",\"pattern\":").append(Engine.cellsJson(part.cells)).append('}');
        }
        stringBuilder2.append(']');
        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append('{');
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            if (i > 0) {
                stringBuilder3.append(',');
            }
            stringBuilder3.append(Engine.quote(Engine.TRACK_ID[i])).append(':').append(app.mutes[i] ? "true" : "false");
        }
        stringBuilder3.append('}');
        String string4 = "{\"format\":\"pulsekit-prj\",\"v\":1,\"name\":" + Engine.quote(string2) + ",\"kit\":{\"pattern\":" + Engine.cellsJson(app.cells) + ",\"bpm\":" + app.bpm() + ",\"swing\":" + (app.swingBar != null ? app.swingBar.getVal() : 12) + ",\"humanize\":" + (app.humanBar != null ? (double)app.humanBar.getVal() / 100.0 : 0.18) + ",\"density\":" + (app.densBar != null ? app.densBar.getVal() : 5) + ",\"steps\":" + app.steps + ",\"tsNum\":" + app.tsNum + ",\"tsDen\":" + app.tsDen + ",\"style\":" + Engine.quote(app.style) + ",\"bars\":" + app.bars + ",\"mutes\":" + stringBuilder3 + ",\"fillLastBar\":" + app.fillLast + ",\"fillVariated\":" + app.fillVariated + ",\"accents\":" + Engine.boolJson(app.accents) + ",\"lengths\":" + Engine.cellsJson(app.lens) + "},\"fillPattern\":" + Engine.cellsJson(app.fillPat) + ",\"fillLengths\":" + Engine.cellsJson(app.fillLens) + ",\"fillId\":" + Engine.quote(app.fillId) + ",\"learned\":[],\"learnedFills\":[],\"hiddenStyles\":[],\"hiddenFills\":[],\"song\":" + stringBuilder2 + ",\"live\":" + app.livePads + ",\"songMode\":" + Engine.quote(app.songMode) + ",\"scriptName\":" + Engine.quote(app.pyName) + ",\"scripts\":[{\"name\":" + Engine.quote(app.pyName) + ",\"file\":\"scripts/0-script.py\"}],\"sf2\":\"kit.sf2\",\"sf2Name\":" + Engine.quote(string2) + ",\"pads\":" + stringBuilder + "}";
        linkedHashMap.put("project.json", string4.getBytes(StandardCharsets.UTF_8));
        return Engine.zipStored(linkedHashMap);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void loadPrj(byte[] byArray) throws Exception {
        Map<String, byte[]> map = Engine.unzip(byArray);
        byte[] byArray2 = map.get("project.json");
        if (byArray2 == null) {
            throw new IllegalArgumentException("Could not read that .prj");
        }
        String string = new String(byArray2, StandardCharsets.UTF_8);
        int n = Engine.clampBpm(app.persistence.jsonInt(string, "\"bpm\"", app.bpm()));
        if (app.bpmBar != null) {
            app.bpmBar.setVal(n);
        }
        app.bpmLabel.setText((CharSequence)Integer.toString(n));
        if (app.swingBar != null) {
            app.swingBar.setVal(Engine.clamp(app.persistence.jsonInt(string, "\"swing\"", app.swingBar.getVal()), 0, 75));
        }
        if (app.densBar != null) {
            app.densBar.setVal(Engine.clamp(app.persistence.jsonInt(string, "\"density\"", app.densBar.getVal()), 1, 10));
        }
        if (app.humanBar != null) {
            int n2 = (int)Math.round(app.persistence.jsonDouble(string, "\"humanize\"", (double)app.humanBar.getVal() / 100.0) * 100.0);
            app.humanBar.setVal(Engine.clamp(n2, 0, 100));
        }
        app.persistence.copyPattern(string, "\"pattern\"", app.cells);
        app.persistence.copyPattern(string, "\"fillPattern\"", app.fillPat);
        app.persistence.copyPattern(string, "\"lengths\"", app.lens);
        app.persistence.copyPattern(string, "\"fillLengths\"", app.fillLens);
        app.tsNum = Engine.clampTsNum(app.persistence.jsonInt(string, "\"tsNum\"", 4));
        app.tsDen = Engine.clampTsDen(app.persistence.jsonInt(string, "\"tsDen\"", 4));
        if (app.tsNumField != null) {
            app.tsNumField.setText((CharSequence)Integer.toString(app.tsNum));
        }
        if (app.tsDenField != null) {
            app.tsDenField.setText((CharSequence)Integer.toString(app.tsDen));
        }
        app.gridEditor.applySteps(Engine.clampSteps(app.persistence.jsonInt(string, "\"steps\"", app.steps)), false);
        app.song.clear();
        app.song.addAll(Engine.decodeSng(byArray2));
        app.songLane = "original";
        byte[] byArray3 = map.get("kit.sf2");
        if (byArray3 != null) {
            short[][] object = AudioIo.parseSf2(byArray3);
            Object object2 = app.mixLock;
            synchronized (object2) {
                for (int i = 0; i < app.voices.length && i < object.length; ++i) {
                    if (object[i] == null) continue;
                    app.voices[i] = object[i];
                    app.mixPos[i] = -1;
                }
            }
        }
        byte[] object = null;
        for (String string2 : map.keySet()) {
            if (!string2.startsWith("scripts/") || !string2.endsWith(".py")) continue;
            object = map.get(string2);
            app.pyName = string2.substring(string2.lastIndexOf(47) + 1);
            break;
        }
        if (object != null && app.pyEditor != null) {
            app.pyEditor.setText((CharSequence)new String((byte[])object, StandardCharsets.UTF_8));
        }
        app.fillLast = string.contains("\"fillLastBar\":true");
        app.show(app.song.isEmpty() ? (app.fillLast ? "combo" : "pattern") : "song");
        app.gridEditor.refreshGrid();
        app.styleLibrary.refreshFills();
        app.songEditor.refreshSong();
        Toast.makeText((Context)app, (CharSequence)"Project loaded", (int)0).show();
    }

    void loadPlugin(byte[] byArray) throws Exception {
        String string;
        Map<String, byte[]> map;
        try {
            map = Engine.unzip(byArray);
        }
        catch (Exception exception) {
            map = new LinkedHashMap<String, byte[]>();
        }
        byte[] byArray2 = map.get("plugin.json");
        if (byArray2 == null) {
            byArray2 = byArray;
        }
        if (!(string = new String(byArray2, StandardCharsets.UTF_8)).contains("pulsekit-plugin")) {
            throw new IllegalArgumentException("Could not read that plugin");
        }
        app.persistence.copyPattern(string, "\"pattern\"", app.cells);
        int n = Engine.clampBpm(app.persistence.jsonInt(string, "\"bpm\"", app.bpm()));
        if (app.bpmBar != null) {
            app.bpmBar.setVal(n);
        }
        app.bpmLabel.setText((CharSequence)Integer.toString(n));
        String string2 = app.persistence.jsonStr(string, "\"name\"");
        String string3 = app.persistence.jsonStr(string, "\"id\"");
        if (string3 == null || string3.contains(".")) {
            string3 = "plug";
        }
        app.styleLibrary.addPluginStyle(new Engine.Style(string3, string2 != null ? string2 : "Plugin", n, Engine.rowsFromCells(app.cells)));
        app.styleLibrary.loadStyle(string3, false);
        app.pluginGhostHats = string.contains("ghostHats");
        byte[] byArray3 = null;
        for (String string4 : map.keySet()) {
            if (!string4.startsWith("scripts/") || !string4.endsWith(".py")) continue;
            byArray3 = map.get(string4);
            app.pyName = string4.substring(string4.lastIndexOf(47) + 1);
            break;
        }
        if (byArray3 != null && app.pyEditor != null) {
            app.pyEditor.setText((CharSequence)new String(byArray3, StandardCharsets.UTF_8));
        }
        app.show("pattern");
        app.gridEditor.refreshGrid();
        app.styleLibrary.refreshFills();
        Toast.makeText((Context)app, (CharSequence)("Plugin \u00b7 " + (string2 != null ? string2 : "pack")), (int)0).show();
    }

    void tryDub() {
        Engine.Style style = Engine.dubStyle();
        app.styleLibrary.addPluginStyle(style);
        int[][] nArray = Engine.dubFill();
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(nArray[i], 0, app.fillPat[i], 0, Math.min(Engine.MAX_STEPS, nArray[i].length));
        }
        app.pluginGhostHats = true;
        app.styleLibrary.loadStyle("dub", false);
        app.show("pattern");
        Toast.makeText((Context)app, (CharSequence)"Plugin \u00b7 Dub pack", (int)0).show();
    }

    void saveMidi(String string) {
        app.midiSaveRole = string == null ? "pattern" : string;
        this.saveKind(8);
    }

    String exportName(String string) {
        String string2 = app.styles.containsKey(app.style) ? app.styles.get((Object)app.style).label : app.style;
        boolean bl = true;
        for (int i = 0; i < Engine.TRACK_ID.length && bl; ++i) {
            for (int j = 0; j < 16; ++j) {
                if (app.cells[i][j] <= 0) continue;
                bl = false;
            }
        }
        return AudioIo.fileName(bl ? "silent" : string2, app.bpm(), string, app.fillVariated);
    }

    void openFile() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        app.startActivityForResult(intent, 7);
    }

    byte[] readUri(Uri uri) throws Exception {
        int n;
        InputStream inputStream = app.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] byArray = new byte[4096];
        while ((n = inputStream.read(byArray)) > 0) {
            byteArrayOutputStream.write(byArray, 0, n);
        }
        inputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    void ingest(byte[] byArray, String string, Uri uri) throws Exception {
        // WAV and MP3 become the input file of the PyJav program (e.g. MidiDrumGen.java).
        if (app.pyJav.pkTakeAudioInput(byArray, string)) {
            return;
        }
        // Programs (.java, .class, .jar, .js, .ts) open in PyJav.
        if (app.pyJav.pkTakeProgram(byArray, string)) {
            if (uri != null) app.codeSave.opened(uri);
            return;
        }
        // .prompt sheets open in PyJav with their run mode and references.
        if (string != null && (string.toLowerCase().endsWith(".prompt") || (byArray != null && byArray.length >= 9 && new String(byArray, 0, Math.min(byArray.length, 12), java.nio.charset.StandardCharsets.UTF_8).startsWith("PKPROMPT1")))) {
            String base = string;
            int slash = Math.max(base.lastIndexOf(47), base.lastIndexOf(58));
            if (slash >= 0) base = base.substring(slash + 1);
            if (!base.toLowerCase().endsWith(".prompt")) base = base + ".prompt";
            app.pyName = base;
            app.pkPyBytes = null;
            String text = new String(byArray, java.nio.charset.StandardCharsets.UTF_8);
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
            app.pkPromptSource = text;
            app.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
            if (sheet != null && sheet.body != null) text = sheet.body;
            if (app.pyEditor != null) app.pyEditor.setText(text);
            app.pyJav.pkSetPromptRun(pulsekit.PromptRun.runMode(new String(byArray, java.nio.charset.StandardCharsets.UTF_8), null));
            app.pyJav.pkLoadRefs(app.pkPromptSource);
            app.pyJav.pkShowPromptModes();
            app.show("py");
            app.setNow("PyJav · " + base);
            return;
        }
        int n;
        int[][] object;
        String string2 = AudioIo.sniff(byArray);
        String string3 = string.toLowerCase();
        if ("unknown".equals(string2)) {
            if (string3.contains(".sng")) {
                string2 = "sng";
            } else if (string3.contains(".sf2")) {
                string2 = "sf2";
            } else if (string3.contains(".wav")) {
                string2 = "wav";
            } else if (string3.contains(".mp3")) {
                string2 = "mp3";
            } else if (string3.contains(".mid")) {
                string2 = "midi";
            } else if (string3.contains(".py")) {
                string2 = "py";
            } else if (string3.contains(".prj") || Engine.isPrj(byArray)) {
                string2 = "prj";
            } else if (string3.contains(".pkp") || Engine.isPlugin(byArray)) {
                string2 = "pkp";
            } else if (string3.contains(".fset") || Engine.isFset(byArray)) {
                string2 = "fset";
            }
        }
        if ("prj".equals(string2) || Engine.isPrj(byArray)) {
            this.loadPrj(byArray);
            return;
        }
        if ("pkp".equals(string2) || Engine.isPlugin(byArray)) {
            this.loadPlugin(byArray);
            return;
        }
        if ("fset".equals(string2) || Engine.isFset(byArray)) {
            app.importLibrary.loadFset(byArray, string);
            return;
        }
        if ("py".equals(string2)) {
            String string4 = string;
            int n2 = Math.max(string4.lastIndexOf(47), string4.lastIndexOf(58));
            String string5 = app.pyName = n2 >= 0 ? string4.substring(n2 + 1) : string4;
            if (!app.pyName.toLowerCase().endsWith(".py")) {
                app.pyName = "script.py";
            }
            if (app.pyEditor != null) {
                app.pyEditor.setText((CharSequence)new String(byArray, StandardCharsets.UTF_8));
            }
            app.show("py");
            Toast.makeText((Context)app, (CharSequence)("Python \u00b7 " + app.pyName), (int)0).show();
            return;
        }
        if ("sng".equals(string2)) {
            List<Engine.Part> list = Engine.decodeSng(byArray);
            if (list.isEmpty()) {
                Toast.makeText((Context)app, (CharSequence)"Could not read that .sng", (int)0).show();
                return;
            }
            app.song.clear();
            app.song.addAll(list);
            app.songLane = "original";
            app.show("song");
            app.playback.applyPart(list.get(0));
            Toast.makeText((Context)app, (CharSequence)(list.size() + " parts loaded"), (int)0).show();
            return;
        }
        if ("sf2".equals(string2)) {
            if ((long)byArray.length > 0x3000000L) {
                Toast.makeText((Context)app, (CharSequence)"SoundFont is too large", (int)0).show();
                return;
            }
            app.setNow("Loading SoundFont\u2026");
            Toast.makeText((Context)app, (CharSequence)"Loading SoundFont\u2026", (int)0).show();
            new Thread(() -> {
                try {
                    short[][] sArray = AudioIo.parseSf2(byArray);
                    app.handler.post(() -> {
                        app.playback.applySf2(sArray);
                        app.persistence.persistSf2(byArray);
                    });
                }
                catch (Exception exception) {
                    app.handler.post(() -> {
                        app.setNow(null);
                        String msg = exception.getMessage() != null ? exception.getMessage() : "Could not read SoundFont";
                        Toast.makeText((Context)app, (CharSequence)msg, (int)1).show();
                    });
                }
            }, "pulsekit-sf2").start();
            return;
        }
        String string6 = Engine.midiRoleFromFile(string);
        Engine.MidiBars songBars;
        if (string6 == null && (songBars = Engine.parseMidiBars(byArray)) != null && app.importLibrary.learnFromSongImport(string, songBars)) {
            return;
        }
        object = Engine.parseMidi(byArray);
        if (Engine.hitCount((int[][])object) < 1) {
            Toast.makeText((Context)app, (CharSequence)"No drum track in that MIDI", (int)0).show();
            return;
        }
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            System.arraycopy(object[n], 0, app.cells[n], 0, 32);
        }
        n = Engine.parseMidiBpm(byArray, app.bpm());
        if (app.bpmBar != null) {
            app.bpmBar.setVal(Engine.clampBpm(n));
        }
        app.bpmLabel.setText((CharSequence)Integer.toString(app.bpm()));
        Engine.MidiBars midiBars = Engine.parseMidiBars(byArray);
        int n3 = midiBars != null ? midiBars.tsNum : 4;
        int n4 = midiBars != null ? midiBars.tsDen : 4;
        app.tsNum = Engine.clampTsNum(n3);
        app.tsDen = Engine.clampTsDen(n4);
        if (app.tsNumField != null) {
            app.tsNumField.setText((CharSequence)Integer.toString(app.tsNum));
        }
        if (app.tsDenField != null) {
            app.tsDenField.setText((CharSequence)Integer.toString(app.tsDen));
        }
        app.gridEditor.applySteps(Engine.patternLenFromCells((int[][])object, app.tsNum, app.tsDen), false);
        Engine.defaultAccents(app.accents, app.steps, Engine.stepsPerBeat(app.tsDen));
        app.show("pattern");
        app.gridEditor.refreshGrid();
        app.importLibrary.learnFromImport(string, (int[][])object, n);
        CharSequence charSequence = app.now != null ? app.now.getText() : null;
        Toast.makeText((Context)app, (CharSequence)(charSequence != null && charSequence.length() > 0 ? charSequence : "MIDI loaded"), (int)0).show();
    }

    short[] decodeToShorts(Uri uri, byte[] byArray) throws Exception {
        if (uri != null) {
            try {
                return this.decodeWithExtractor(uri);
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (AudioIo.sniff(byArray).equals("wav")) {
            return AudioIo.floatsToShorts(AudioIo.parseWav((byte[])byArray).samples);
        }
        throw new IllegalArgumentException("Could not decode that audio file");
    }

    short[] decodeWithExtractor(Uri uri) throws Exception {
        int n;
        ParcelFileDescriptor parcelFileDescriptor = app.getContentResolver().openFileDescriptor(uri, "r");
        Decoded decoded;
        try {
            decoded = decodeNative(parcelFileDescriptor.getFileDescriptor());
        } finally {
            parcelFileDescriptor.close();
        }
        short[] sArray = decoded.mono;
        int n3 = decoded.rate;
        if (n3 != 22050) {
            double d = (double)n3 / 22050.0;
            n = Math.max(1, (int)Math.floor((double)sArray.length / d));
            short[] sArray2 = new short[n];
            for (int i = 0; i < n; ++i) {
                sArray2[i] = sArray[Math.min(sArray.length - 1, (int)Math.floor((double)i * d))];
            }
            return sArray2;
        }
        return sArray;
    }

    /** Decoded audio: mono 16-bit samples at the file's own rate. */
    static final class Decoded {
        final short[] mono;
        final int rate;

        Decoded(short[] mono, int rate) {
            this.mono = mono;
            this.rate = rate;
        }
    }

    /** Decodes an MP3 (or any audio Android reads) to mono at its own sample rate. */
    static Decoded decodeNative(java.io.FileDescriptor fd) throws Exception {
        int n;
        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(fd);
        int n2 = 0;
        MediaFormat mediaFormat = null;
        for (int i = 0; i < mediaExtractor.getTrackCount(); ++i) {
            MediaFormat mediaFormat2 = mediaExtractor.getTrackFormat(i);
            String string = mediaFormat2.getString("mime");
            if (string == null || !string.startsWith("audio/")) continue;
            n2 = i;
            mediaFormat = mediaFormat2;
            break;
        }
        if (mediaFormat == null) {
            mediaExtractor.release();
            throw new IllegalArgumentException("No audio track");
        }
        mediaExtractor.selectTrack(n2);
        String string = mediaFormat.getString("mime");
        int n3 = mediaFormat.containsKey("sample-rate") ? mediaFormat.getInteger("sample-rate") : 44100;
        int n4 = mediaFormat.containsKey("channel-count") ? mediaFormat.getInteger("channel-count") : 1;
        MediaCodec mediaCodec = MediaCodec.createDecoderByType((String)string);
        mediaCodec.configure(mediaFormat, null, null, 0);
        mediaCodec.start();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        boolean bl = false;
        boolean bl2 = false;
        int spins = 0;
        while (!bl2) {
            ByteBuffer byteBuffer;
            int n5;
            if (!bl && (n5 = mediaCodec.dequeueInputBuffer(10000L)) >= 0) {
                byteBuffer = mediaCodec.getInputBuffer(n5);
                int n6 = byteBuffer == null ? -1 : mediaExtractor.readSampleData(byteBuffer, 0);
                if (n6 < 0) {
                    mediaCodec.queueInputBuffer(n5, 0, 0, 0L, 4);
                    bl = true;
                } else {
                    mediaCodec.queueInputBuffer(n5, 0, n6, mediaExtractor.getSampleTime(), 0);
                    mediaExtractor.advance();
                }
            }
            if ((n5 = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000L)) < 0) {
                if (bl && ++spins > 80) break;
                continue;
            }
            spins = 0;
            byteBuffer = mediaCodec.getOutputBuffer(n5);
            int size = bufferInfo.size;
            int off = bufferInfo.offset;
            if (byteBuffer != null && size > 0 && off >= 0 && off + size <= byteBuffer.capacity()) {
                byteBuffer.position(off);
                byteBuffer.limit(off + size);
                byte[] byArray = new byte[size];
                byteBuffer.get(byArray);
                byteArrayOutputStream.write(byArray);
            }
            mediaCodec.releaseOutputBuffer(n5, false);
            if ((bufferInfo.flags & 4) == 0) continue;
            bl2 = true;
        }
        mediaCodec.stop();
        mediaCodec.release();
        mediaExtractor.release();
        byte[] byArray = byteArrayOutputStream.toByteArray();
        int n7 = byArray.length / 2 / Math.max(1, n4);
        short[] sArray = new short[n7];
        for (int i = 0; i < n7; ++i) {
            int n8 = 0;
            for (n = 0; n < n4; ++n) {
                int n9 = (i * n4 + n) * 2;
                n8 += (short)(byArray[n9] & 0xFF | byArray[n9 + 1] << 8);
            }
            sArray[i] = (short)(n8 / n4);
        }
        return new Decoded(sArray, n3);
    }


}
