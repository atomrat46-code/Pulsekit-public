package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import pulsekit.AudioIo;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** Saving and restoring learned patterns, Fillerns, session state and autosave. */
final class Persistence {
    final Pulsekit app;

    Persistence(Pulsekit app) {
        this.app = app;
    }

    Timer autosaveTimer;

    boolean autosaveBusy;

    String learnedJson() {
        return Engine.learnedJson(app.learned);
    }

    String learnedFillsJson() {
        return Engine.learnedFillsJson(app.learnedFills);
    }

    String stringListJson(List<String> list) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(Engine.quote(list.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    List<String> fillernPairList() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : app.fillernPairs.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }
        return out;
    }

    List<String> fillernModeList() {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : app.fillernModes.entrySet()) out.add(e.getKey() + "=" + e.getValue());
        return out;
    }

    void loadFillernPairs(String json) {
        app.fillernModes.clear();
        for (String row : this.parseStringArray(json, "fillernModes")) {
            int eq = row.indexOf('=');
            if (eq > 0) app.fillernModes.put(row.substring(0, eq), Engine.fillernMode(row.substring(eq + 1)));
        }
        app.fillernPicked.clear();
        app.fillernPicked.addAll(this.parseStringArray(json, "fillernPicked"));
        app.fillernPairs.clear();
        for (String row : this.parseStringArray(json, "fillernPairs")) {
            int eq = row.indexOf('=');
            if (eq > 0) app.fillernPairs.put(row.substring(0, eq), row.substring(eq + 1));
        }
    }

    void restoreSessionFiles() {
        try {
            File lf = new File(this.pulsekitDir(), "learned.json");
            if (lf.isFile() && lf.length() > 8) {
                String json = new String(Files.readAllBytes(lf.toPath()), StandardCharsets.UTF_8);
                List<Engine.Learned> got = Engine.parseLearnedJson(json);
                if (!got.isEmpty()) {
                    app.learned.clear();
                    app.learned.addAll(got);
                    for (Engine.Learned item : app.learned) {
                        app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
                    }
                }
                List<Engine.LearnedFill> fills = Engine.parseLearnedFillsJson(json);
                if (!fills.isEmpty()) {
                    app.learnedFills.clear();
                    app.learnedFills.addAll(fills);
                }
                List<Engine.LearnedFill> vars = Engine.parseVariatedFillsJson(json);
                if (!vars.isEmpty()) {
                    app.variatedFills.clear();
                    app.variatedFills.addAll(vars);
                }
                List<Engine.Learned> vpat = Engine.parseVariatedPatternsJson(json);
                if (!vpat.isEmpty()) {
                    app.variatedPatterns.clear();
                    app.variatedPatterns.addAll(vpat);
                    for (Engine.Learned item : app.variatedPatterns) {
                        app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
                    }
                }
                app.styleLibrary.refreshLearnedChips();
                this.loadFillernPairs(json);
                app.fileSets.restoreFileSetInfo();
                List<Engine.ImportedSong> imported = Engine.decodeImportedSongs(json);
                if (!imported.isEmpty()) {
                    app.importedSongs.clear();
                    app.importedSongs.addAll(imported);
                    app.importedSongId = imported.get(0).id;
                }
            }
        } catch (Exception ignored) { /* optional */ }
        try {
            File sf = new File(this.pulsekitDir(), "kit.sf2");
            if (sf.isFile() && sf.length() > 64) {
                short[][] loaded = AudioIo.parseSf2(Files.readAllBytes(sf.toPath()));
                for (int t = 0; t < app.voices.length && t < loaded.length; t++) {
                    if (loaded[t] != null) app.voices[t] = loaded[t];
                }
                app.playback.fillMissingVoices();
                app.playback.reloadDrumBank();
            }
        } catch (Exception ignored) { /* optional */ }
    }

    void persistLearned() {
        try {
            String json = "{\"learned\":" + Engine.learnedJson(app.learned)
                + ",\"learnedFills\":" + Engine.learnedFillsJson(app.learnedFills)
                + ",\"variatedFills\":" + Engine.learnedFillsJson(app.variatedFills)
                + ",\"variatedPatterns\":" + Engine.learnedJson(app.variatedPatterns)
                + ",\"fillernPairs\":" + this.stringListJson(this.fillernPairList())
                + ",\"fillernPicked\":" + this.stringListJson(new ArrayList<String>(app.fillernPicked))
                + ",\"fillernModes\":" + this.stringListJson(this.fillernModeList())
                + ",\"importedSongs\":" + Engine.importedSongsJson(app.importedSongs) + "}";
            Files.write(new File(this.pulsekitDir(), "learned.json").toPath(), json.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (Exception ignored) { /* optional */ }
    }

    void persistSf2(byte[] bytes) {
        if (bytes == null || bytes.length < 16) return;
        try {
            Files.write(new File(this.pulsekitDir(), "kit.sf2").toPath(), bytes, new OpenOption[0]);
        } catch (Exception ignored) { /* optional */ }
    }

    File pulsekitDir() {
        File d = new File(System.getProperty("user.home"), ".pulsekit");
        d.mkdirs();
        return d;
    }

    File autosaveFile() {
        return new File(this.pulsekitDir(), "autosave.prj");
    }

    File autosavePref() {
        return new File(this.pulsekitDir(), "autosave.on");
    }

    void restoreAutosave() {
        boolean on = false;
        try {
            File pref = this.autosavePref();
            if (pref.isFile()) {
                String t = new String(Files.readAllBytes(pref.toPath()), StandardCharsets.UTF_8).trim();
                on = "1".equals(t) || "true".equalsIgnoreCase(t) || "on".equalsIgnoreCase(t);
            }
        } catch (Exception ignored) { /* first run */ }
        if (!on) return;
        if (app.autosaveBox != null) app.autosaveBox.setSelected(true);
        this.setAutosave(true, false);
        File prj = this.autosaveFile();
        if (!prj.isFile() || prj.length() < 16) return;
        try {
            app.projectIo.loadPrj(Files.readAllBytes(prj.toPath()), "autosave.prj");
            app.setNow("Restored autosave");
        } catch (Throwable ex) {
            app.style = "house";
            app.fillId = "toms";
            app.setNow("Autosave skipped");
        }
    }

    void setAutosave(boolean on, boolean announce) {
        app.autosaveOn = on;
        if (app.autosaveBox != null) {
            app.autosaveBox.setSelected(on);
            app.autosaveBox.setForeground(on ? FG : MUTED);
        }
        try {
            Files.write(this.autosavePref().toPath(), (on ? "1" : "0").getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
        } catch (Exception ignored) { /* prefs optional */ }
        if (this.autosaveTimer != null) {
            this.autosaveTimer.stop();
            this.autosaveTimer = null;
        }
        if (on) {
            this.writeAutosave(false);
            this.autosaveTimer = new Timer(5000, e -> this.writeAutosave(false));
            this.autosaveTimer.setRepeats(true);
            this.autosaveTimer.start();
            if (announce) app.setNow("Autosave on \u00b7 " + this.autosaveFile().getAbsolutePath());
        } else if (announce) {
            app.setNow("Autosave off");
        }
    }

    void writeAutosave(boolean announce) {
        if (!app.autosaveOn || this.autosaveBusy) return;
        this.autosaveBusy = true;
        new Thread(() -> {
            try {
                byte[] data = app.projectIo.encodePrjBytes();
                File tmp = new File(this.pulsekitDir(), "autosave.tmp");
                Files.write(tmp.toPath(), data, new OpenOption[0]);
                File dest = this.autosaveFile();
                if (dest.exists() && !dest.delete()) {
                    Files.write(dest.toPath(), data, new OpenOption[0]);
                    tmp.delete();
                } else if (!tmp.renameTo(dest)) {
                    Files.write(dest.toPath(), data, new OpenOption[0]);
                    tmp.delete();
                }
                if (announce) {
                    SwingUtilities.invokeLater(() -> app.setNow("Autosaved"));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> app.setNow("Autosave failed"));
            } finally {
                this.autosaveBusy = false;
            }
        }, "pulsekit-autosave").start();
    }

    List<String> parseStringArray(String json, String key) {
        List<String> out = new ArrayList<String>();
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) return out;
        int br = json.indexOf('[', at);
        int cl = json.indexOf(']', br);
        if (br < 0 || cl < 0) return out;
        String inner = json.substring(br + 1, cl).trim();
        if (inner.isEmpty()) return out;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(inner);
        while (m.find()) out.add(m.group(1).replace("\\\"", "\""));
        return out;
    }
}
