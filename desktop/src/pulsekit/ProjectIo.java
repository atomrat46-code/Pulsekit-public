package pulsekit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import pulsekit.AudioIo;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** Opening and saving files: projects, plugins, songs, MIDI, audio and the Import/Export pages. */
final class ProjectIo {
    final Pulsekit app;

    ProjectIo(Pulsekit app) {
        this.app = app;
    }

    JPanel pluginList;

    JPanel buildImportPage() {
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel();
        jPanel2.setOpaque(false);
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        JLabel jLabel = new JLabel("Import");
        jLabel.setFont(new Font("SansSerif", 1, 20));
        jLabel.setForeground(FG);
        JLabel jLabel2 = new JLabel("<html><body style='width:420px;color:#8A8B86'>PRJ \u00b7 full project<br>PKP \u00b7 plugin pack<br>FSET \u00b7 patterns, Fillerns, and fills from one imported file<br>MIDI \u00b7 pattern, Fillern or fill (named in the file)<br>SNG \u00b7 song<br>WAV / MP3 \u00b7 input file for a PyJav program such as MidiDrumGen.java<br>SF2 \u00b7 drum samples onto pads<br>PY / JAVA / JAR / CLASS \u00b7 program for PyJav</body></html>");
        jPanel2.add(jLabel);
        jPanel2.add(Box.createVerticalStrut(8));
        jPanel2.add(jLabel2);
        jPanel2.add(Box.createVerticalStrut(16));
        // A file picked here can also go into the prompt library, as a reference file.
        ImportDb.on = this.loadImportToDb();
        javax.swing.JCheckBox toDb = new javax.swing.JCheckBox("Import to DB also", ImportDb.on);
        toDb.setName("import-to-db");
        toDb.setOpaque(false);
        toDb.setForeground(FG);
        toDb.setAlignmentX(0.0f);
        toDb.addActionListener(e -> {
            ImportDb.on = toDb.isSelected();
            this.saveImportToDb();
        });
        jPanel2.add(toDb);
        JLabel toDbNote = new JLabel("A file chosen with Choose file is also added to the prompt library, as a reference file.");
        toDbNote.setForeground(MUTED);
        toDbNote.setAlignmentX(0.0f);
        toDbNote.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 24, 8, 0));
        jPanel2.add(toDbNote);
        // Choose file, then Browse DB: a file from the prompt library, imported as if it were chosen.
        JPanel chooseRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chooseRow.setOpaque(false);
        chooseRow.setAlignmentX(0.0f);
        JButton jButton = app.action("Choose file", FG, BG);
        jButton.addActionListener(actionEvent -> this.openFile());
        JButton browseDb = app.action("Browse DB", ELEV, FG);
        browseDb.setName("import-browse-db");
        browseDb.addActionListener(e -> this.browseDb());
        chooseRow.add(jButton);
        chooseRow.add(Box.createHorizontalStrut(8));
        chooseRow.add(browseDb);
        jPanel2.add(chooseRow);
        jPanel2.add(Box.createVerticalStrut(16));
        JLabel filesLab = new JLabel("Imported files");
        filesLab.setFont(new Font("SansSerif", 1, 14));
        filesLab.setForeground(FG);
        filesLab.setAlignmentX(0.0f);
        jPanel2.add(filesLab);
        JLabel filesHint = new JLabel("<html><body style='width:420px;color:#8A8B86'>Tap a name to open its patterns and fills. Delete drops the whole set.</body></html>");
        filesHint.setAlignmentX(0.0f);
        jPanel2.add(filesHint);
        jPanel2.add(Box.createVerticalStrut(8));
        app.importedFileList = new JPanel();
        app.importedFileList.setOpaque(false);
        app.importedFileList.setLayout(new BoxLayout(app.importedFileList, BoxLayout.Y_AXIS));
        app.importedFileList.setAlignmentX(0.0f);
        jPanel2.add(app.importedFileList);
        jPanel2.add(Box.createVerticalStrut(16));
        JLabel plugLab = new JLabel("Plugins");
        plugLab.setFont(new Font("SansSerif", 1, 14));
        plugLab.setForeground(FG);
        plugLab.setAlignmentX(0.0f);
        jPanel2.add(plugLab);
        JLabel plugHint = new JLabel("<html><body style='width:420px;color:#8A8B86'>Packs add styles, fills, scripts, and processors. Later packs can use the same format.</body></html>");
        plugHint.setAlignmentX(0.0f);
        jPanel2.add(plugHint);
        jPanel2.add(Box.createVerticalStrut(8));
        this.pluginList = new JPanel();
        this.pluginList.setOpaque(false);
        this.pluginList.setLayout(new BoxLayout(this.pluginList, 1));
        this.pluginList.setAlignmentX(0.0f);
        jPanel2.add(this.pluginList);
        jPanel2.add(Box.createVerticalStrut(8));
        JButton dub = app.action("Try Dub pack", ELEV, FG, () -> this.tryDub());
        dub.setAlignmentX(0.0f);
        jPanel2.add(dub);
        JScrollPane scroll = new JScrollPane(jPanel2);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        jPanel.add((Component)scroll, "Center");
        this.refreshPluginUi();
        return jPanel;
    }

    JPanel buildExportPage() {
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel();
        jPanel2.setOpaque(false);
        jPanel2.setLayout(new BoxLayout(jPanel2, 1));
        JLabel jLabel = new JLabel("Export");
        jLabel.setFont(new Font("SansSerif", 1, 20));
        jLabel.setForeground(FG);
        jPanel2.add(jLabel);
        // MIDI, WAV and MP3 exports can also go into the prompt library, as reference files.
        ExportDb.on = this.loadExportToDb();
        javax.swing.JCheckBox toDb = new javax.swing.JCheckBox("Export supported media files to DB also", ExportDb.on);
        toDb.setName("export-to-db");
        toDb.setOpaque(false);
        toDb.setForeground(FG);
        toDb.setAlignmentX(0.0f);
        toDb.addActionListener(e -> {
            ExportDb.on = toDb.isSelected();
            this.saveExportToDb();
        });
        jPanel2.add(Box.createVerticalStrut(8));
        jPanel2.add(toDb);
        JLabel toDbNote = new JLabel("MIDI, WAV and MP3 exports are added to the prompt library too, as reference files.");
        toDbNote.setForeground(MUTED);
        toDbNote.setAlignmentX(0.0f);
        toDbNote.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 24, 0, 0));
        jPanel2.add(toDbNote);
        jPanel2.add(this.exportSection("Project", new JComponent[] {
            app.action("PRJ", HIT, BG, () -> this.savePrj()),
            app.action("PKP", ELEV, FG, () -> this.savePkp()),
            app.action("FSET", ELEV, FG, () -> app.importLibrary.saveFset(null))
        }));
        jPanel2.add(this.exportSection("MIDI", new JComponent[] {
            app.action("Pattern", ELEV, FG, () -> this.saveMidi("pattern")),
            app.action("Fillern", ELEV, FG, () -> this.saveMidi("fillern")),
            app.action("Fill", ELEV, FG, () -> this.saveMidi("fill"))
        }));
        jPanel2.add(this.exportSection("This beat", new JComponent[] {
            app.action("WAV", ELEV, FG, () -> this.saveWav()),
            app.action("MP3", ELEV, FG, () -> this.saveMp3()),
            app.action("SF2", ELEV, FG, () -> this.saveSf2()),
            app.action("JAR", ELEV, FG, () -> this.saveJar())
        }));
        jPanel2.add(this.exportSection("Song", new JComponent[] {
            app.action("SNG", ELEV, FG, () -> this.saveSng()),
            app.action("Song MIDI", ELEV, FG, () -> this.saveSongMidi()),
            app.action("Song WAV", ELEV, FG, () -> this.saveSongAudio("wav")),
            app.action("Song MP3", ELEV, FG, () -> this.saveSongAudio("mp3"))
        }));
        jPanel2.add(this.exportSection("PyJav", new JComponent[] {
            app.action("Save", ELEV, FG, () -> this.savePy())
        }));
        jPanel.add((Component)jPanel2, "North");
        return jPanel;
    }

    File exportSettingsFile() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "export-settings.txt");
    }

    boolean loadExportToDb() {
        try {
            File f = this.exportSettingsFile();
            return f.isFile() && new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).contains("toDb=1");
        } catch (Exception ex) {
            return false;
        }
    }

    void saveExportToDb() {
        try {
            File f = this.exportSettingsFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), ("toDb=" + (ExportDb.on ? 1 : 0) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // the setting stays for this session
        }
    }

    /**
     * After an export of kind `ext` (mid, wav, mp3...): a MIDI, WAV or MP3 also into the prompt
     * library when the Export screen says so, under the name it was saved as (with its extension
     * added when the name was typed without one).
     */
    void exportToDb(File saved, byte[] bytes, String ext) {
        if (!ExportDb.supported("export." + ext)) return;
        String name = saved.getName();
        if (!ExportDb.supported(name)) name = name + "." + ext;
        String toDb = ExportDb.store(PromptDb.dir(), name, bytes);
        if (!toDb.isEmpty()) app.setNow("Saved " + saved.getName() + ", " + toDb);
    }

    JPanel exportSection(String title, JComponent[] buttons) {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, 1));
        wrap.setAlignmentX(0.0f);
        JLabel lab = new JLabel(title);
        lab.setForeground(MUTED);
        lab.setAlignmentX(0.0f);
        wrap.add(Box.createVerticalStrut(10));
        wrap.add(lab);
        wrap.add(Box.createVerticalStrut(6));
        JPanel row = new JPanel(new GridLayout(0, 3, 8, 8));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        row.setMaximumSize(new Dimension(520, 80 * ((buttons.length + 2) / 3)));
        for (JComponent b : buttons) row.add(b);
        wrap.add(row);
        return wrap;
    }

    void applyEnabledProcessors(Random random) {
        for (Engine.Plugin p : app.plugins) {
            if (!p.enabled) continue;
            Engine.applyOps(app.cells, p.ops, random, app.steps);
        }
    }

    void installPlugin(Engine.Plugin incoming, boolean apply) {
        if (incoming == null) return;
        app.plugins.removeIf(p -> incoming.id.equals(p.id));
        app.plugins.add(0, incoming);
        while (app.plugins.size() > Engine.MAX_PLUGINS) app.plugins.remove(app.plugins.size() - 1);
        this.refreshPluginChips();
        this.refreshPluginUi();
        if (incoming.script != null && app.pyEditor != null) {
            app.pyBytes = null;
            app.pyEditor.setEditable(true);
            app.pyEditor.setText(incoming.script);
            if (incoming.scriptName != null) app.pyName = incoming.scriptName;
        }
        if (!apply || !incoming.enabled) return;
        if (!incoming.styles.isEmpty()) {
            Engine.Style st = incoming.styles.get(0);
            app.styles.put(st.id, st);
            app.styleLibrary.loadStyle(st.id, false);
        }
        if (!incoming.fills.isEmpty()) {
            Engine.PlugFill f = incoming.fills.get(0);
            app.fillId = "p:" + incoming.id + "/" + f.id;
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                System.arraycopy(f.cells[t], 0, app.fillPat[t], 0, Math.min(Engine.MAX_STEPS, f.cells[t].length));
            }
            app.styleLibrary.refreshFills();
        }
        app.showView("pattern");
        app.setNow("Plugin \u00b7 " + incoming.name);
    }

    void setPluginEnabled(String id, boolean on) {
        for (Engine.Plugin p : app.plugins) {
            if (!p.id.equals(id)) continue;
            p.enabled = on;
            break;
        }
        this.refreshPluginChips();
        this.refreshPluginUi();
        app.styleLibrary.refreshStyles();
        app.styleLibrary.refreshFills();
    }

    void uninstallPlugin(String id) {
        app.plugins.removeIf(p -> id.equals(p.id));
        this.refreshPluginChips();
        this.refreshPluginUi();
        app.styleLibrary.refreshStyles();
        app.styleLibrary.refreshFills();
        app.setNow("Plugin removed");
    }

    void refreshPluginUi() {
        if (this.pluginList == null) return;
        this.pluginList.removeAll();
        if (app.plugins.isEmpty()) {
            JLabel empty = new JLabel("No packs installed yet.");
            empty.setForeground(SUBTLE);
            empty.setAlignmentX(0.0f);
            this.pluginList.add(empty);
        } else {
            for (Engine.Plugin p : app.plugins) {
                final String pid = p.id;
                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setOpaque(false);
                row.setAlignmentX(0.0f);
                row.setMaximumSize(new Dimension(520, 52));
                JPanel text = new JPanel();
                text.setOpaque(false);
                text.setLayout(new BoxLayout(text, 1));
                JLabel name = new JLabel(p.name);
                name.setForeground(FG);
                name.setFont(new Font("SansSerif", 1, 13));
                JLabel sum = new JLabel(p.summary() + (p.enabled ? "" : " \u00b7 off"));
                sum.setForeground(MUTED);
                sum.setFont(new Font("SansSerif", 0, 11));
                text.add(name);
                text.add(sum);
                row.add(text, "Center");
                JPanel btns = new JPanel(new FlowLayout(2, 6, 0));
                btns.setOpaque(false);
                JButton tog = app.action(p.enabled ? "On" : "Off", p.enabled ? HIT : ELEV, p.enabled ? BG : FG, () -> this.setPluginEnabled(pid, !this.pluginEnabled(pid)));
                JButton rm = app.action("Remove", ELEV, FG, () -> this.uninstallPlugin(pid));
                btns.add(tog);
                btns.add(rm);
                row.add(btns, "East");
                this.pluginList.add(row);
                this.pluginList.add(Box.createVerticalStrut(6));
            }
        }
        this.pluginList.revalidate();
        this.pluginList.repaint();
    }

    boolean pluginEnabled(String id) {
        for (Engine.Plugin p : app.plugins) if (p.id.equals(id)) return p.enabled;
        return false;
    }

    void refreshPluginChips() {
        app.styleLibrary.refreshLearnedChips();
    }

    void saveMidi(String role) {
        JFileChooser jFileChooser = new JFileChooser();
        String label = app.styles.containsKey(app.style) ? app.styles.get(app.style).label : app.style;
        String name = Engine.midiFileName(label, app.bpm(), role, app.styleLibrary.fillLabel(app.fillId));
        jFileChooser.setSelectedFile(new File(name));
        jFileChooser.setFileFilter(new FileNameExtensionFilter("MIDI", "mid", "midi"));
        if (jFileChooser.showSaveDialog(app) != 0) {
            return;
        }
        try {
            int[][] cells = Engine.cellsForMidiRole(app.cells, app.fillPat, role);
            int[][] lens = Engine.cellsForMidiRole(app.lens, app.fillLens, role);
            int steps = "fill".equals(role) ? Engine.barSteps(app.tsNum, app.tsDen) : app.steps;
            byte[] byArray = Engine.encodeMidi(cells, lens, app.bpm(), steps, app.tsNum, app.tsDen);
            Files.write(jFileChooser.getSelectedFile().toPath(), byArray, new OpenOption[0]);
            app.setNow("Saved " + name);
            this.exportToDb(jFileChooser.getSelectedFile(), byArray, "mid");
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not save MIDI: " + exception.getMessage());
        }
    }

    void saveSng() {
        List<Engine.Part> parts = app.songEditor.activeSong();
        if (parts.isEmpty()) {
            String name = this.exportName("mid").replace(".mid", "");
            parts = Collections.singletonList(Engine.groove(name, app.bpm(), app.cells, 1));
        }
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setSelectedFile(new File(Engine.songFilename(parts, app.songEditor.songFileSet(parts))));
        jFileChooser.setFileFilter(new FileNameExtensionFilter("Pulsekit song", "sng"));
        if (jFileChooser.showSaveDialog(app) != 0) {
            return;
        }
        try {
            String songSet = app.songEditor.songFileSet(parts);
            Files.write(jFileChooser.getSelectedFile().toPath(), Engine.encodeSng(parts, songSet != null ? songSet : parts.get(0).name), new OpenOption[0]);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not save .sng: " + exception.getMessage());
        }
    }

    void savePy() {
        byte[] data = app.pyBytes != null
            ? app.pyBytes
            : (app.pyEditor != null ? app.pyEditor.getText() : "").getBytes(StandardCharsets.UTF_8);
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setSelectedFile(new File(app.pyName));
        jFileChooser.setFileFilter(new FileNameExtensionFilter("Python, Java, JavaScript, or prompt", "py", "java", "jar", "class", "js", "mjs", "ts", "tsx", "prompt"));
        if (jFileChooser.showSaveDialog(app) != 0) {
            return;
        }
        try {
            Files.write(jFileChooser.getSelectedFile().toPath(), data, new OpenOption[0]);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not save program: " + exception.getMessage());
        }
    }

    void savePrj() {
        try {
            this.saveBytes(this.exportName("prj"), "Pulsekit project", "prj", this.encodePrjBytes());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(app, "Could not save .prj: " + ex.getMessage());
        }
    }

    byte[] encodePrjBytes() throws Exception {
            LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
            String py = app.pyEditor != null ? app.pyEditor.getText() : "";
            String safeName = (app.pyName == null || app.pyName.isEmpty() ? "script.py" : app.pyName).replaceAll("[\\\\/]", "_");
            String scriptFile = "scripts/" + safeName;
            byte[] scriptBytes = app.pyBytes != null ? app.pyBytes : py.getBytes(StandardCharsets.UTF_8);
            files.put(scriptFile, scriptBytes);
            app.playback.fillMissingVoices();
            String kitName = app.styles.containsKey(app.style) ? app.styles.get(app.style).label : "Pulsekit";
            byte[] sf2 = AudioIo.encodeSf2(app.voices, kitName);
            files.put("kit.sf2", sf2);
            StringBuilder pads = new StringBuilder();
            pads.append('[');
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                if (t > 0) pads.append(',');
                String file = "pads/" + t + "-" + Engine.TRACK_ID[t] + ".wav";
                files.put(file, AudioIo.encodeWav(app.voices[t], 22050));
                pads.append("{\"id\":").append(Engine.quote(Engine.TRACK_ID[t]));
                pads.append(",\"note\":").append(Engine.NOTES[t]);
                pads.append(",\"name\":").append(Engine.quote(Engine.TRACK_LABEL[t]));
                pads.append(",\"file\":").append(Engine.quote(file)).append('}');
            }
            pads.append(']');
            StringBuilder song = new StringBuilder();
            song.append('[');
            for (int i = 0; i < app.song.size(); i++) {
                if (i > 0) song.append(',');
                Engine.Part p = app.song.get(i);
                song.append("{\"kind\":").append(Engine.quote(p.kind));
                song.append(",\"name\":").append(Engine.quote(p.name));
                song.append(",\"repeats\":").append(p.repeats);
                song.append(",\"bpm\":").append(p.bpm);
                song.append(",\"steps\":").append(p.steps);
                song.append(",\"tsNum\":").append(p.tsNum);
                song.append(",\"tsDen\":").append(p.tsDen);
                song.append(",\"pattern\":").append(Engine.cellsJson(p.cells)).append('}');
            }
            song.append(']');
            StringBuilder mutes = new StringBuilder();
            mutes.append('{');
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                if (t > 0) mutes.append(',');
                mutes.append(Engine.quote(Engine.TRACK_ID[t])).append(':').append(app.mutes[t] ? "true" : "false");
            }
            mutes.append('}');
            String json = "{\"format\":\"pulsekit-prj\",\"v\":1,\"name\":" + Engine.quote(kitName)
                + ",\"kit\":{\"pattern\":" + Engine.cellsJson(app.cells)
                + ",\"bpm\":" + app.bpm()
                + ",\"swing\":" + app.swingBar.getVal()
                + ",\"humanize\":" + app.humanBar.getVal() / 100.0
                + ",\"density\":" + app.densBar.getVal()
                + ",\"steps\":" + app.steps
                + ",\"tsNum\":" + app.tsNum
                + ",\"tsDen\":" + app.tsDen
                + ",\"style\":" + Engine.quote(app.style)
                + ",\"bars\":" + app.bars
                + ",\"mutes\":" + mutes
                + ",\"fillLastBar\":" + app.fillLast
                + ",\"fillVariated\":" + app.fillVariated
                + ",\"accents\":" + Engine.boolJson(app.accents)
                + ",\"lengths\":" + Engine.cellsJson(app.lens)
                + "},\"fillPattern\":" + Engine.cellsJson(app.fillPat)
                + ",\"fillLengths\":" + Engine.cellsJson(app.fillLens)
                + ",\"fillId\":" + Engine.quote(app.fillId)
                + ",\"learned\":" + app.persistence.learnedJson()
                + ",\"learnedFills\":" + app.persistence.learnedFillsJson()
                + ",\"hiddenStyles\":" + app.persistence.stringListJson(app.hiddenStyles)
                + ",\"hiddenFills\":" + app.persistence.stringListJson(app.hiddenFills)
                + ",\"song\":" + song
                + ",\"live\":" + app.livePads
                + ",\"songMode\":" + Engine.quote(app.songMode)
                + ",\"scriptName\":" + Engine.quote(app.pyName)
                + ",\"scripts\":[{\"name\":" + Engine.quote(app.pyName) + ",\"file\":" + Engine.quote(scriptFile) + "}]"
                + ",\"plugins\":" + this.pluginsJson()
                + ",\"sf2\":\"kit.sf2\",\"sf2Name\":" + Engine.quote(kitName)
                + ",\"pads\":" + pads + "}";
            files.put("project.json", json.getBytes(StandardCharsets.UTF_8));
            for (Engine.Plugin p : app.plugins) {
                String safe = p.id.replaceAll("[^a-zA-Z0-9._-]", "_");
                files.put("plugins/" + safe + ".json", p.rawJson.getBytes(StandardCharsets.UTF_8));
            }
            return Engine.zipStored(files);
    }

    String pluginsJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < app.plugins.size(); i++) {
            if (i > 0) sb.append(',');
            Engine.Plugin p = app.plugins.get(i);
            String safe = p.id.replaceAll("[^a-zA-Z0-9._-]", "_");
            sb.append("{\"id\":").append(Engine.quote(p.id));
            sb.append(",\"file\":").append(Engine.quote("plugins/" + safe + ".json"));
            sb.append(",\"enabled\":").append(p.enabled);
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    void loadPrj(byte[] data, String filename) throws Exception {
        Map<String, byte[]> files = Engine.unzip(data);
        byte[] jsonBytes = files.get("project.json");
        if (jsonBytes == null) throw new IllegalArgumentException("Could not read that .prj project");
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        if (!json.contains("pulsekit-prj")) throw new IllegalArgumentException("Could not read that .prj project");
        int bpm = Engine.clampBpm(extractJsonInt(json, "\"bpm\"", app.bpm()));
        app.tempoBar.setVal(bpm);
        app.bpmField.setText(Integer.toString(bpm));
        int swing = extractJsonInt(json, "\"swing\"", app.swingBar.getVal());
        app.swingBar.setVal(Engine.clamp(swing, 0, 75));
        int dens = extractJsonInt(json, "\"density\"", app.densBar.getVal());
        app.densBar.setVal(Engine.clamp(dens, 1, 10));
        int human = (int) Math.round(extractJsonDouble(json, "\"humanize\"", app.humanBar.getVal() / 100.0) * 100);
        app.humanBar.setVal(Engine.clamp(human, 0, 100));
        app.bars = Engine.clamp(extractJsonInt(json, "\"bars\"", app.bars), 1, 16);
        String st = extractJsonString(json, "\"style\"");
        if (st != null && app.styles.containsKey(st)) app.style = st;
        String fid = extractJsonString(json, "\"fillId\"");
        if (fid != null) app.fillId = fid;
        app.fillLast = json.contains("\"fillLastBar\":true");
        app.fillVariated = json.contains("\"fillVariated\":true");
        app.livePads = json.contains("\"live\":true");
        copyPatternFromJson(json, "\"pattern\"", app.cells);
        copyPatternFromJson(json, "\"fillPattern\"", app.fillPat);
        copyPatternFromJson(json, "\"lengths\"", app.lens);
        copyPatternFromJson(json, "\"fillLengths\"", app.fillLens);
        app.learned.clear();
        app.learnedFills.clear();
        app.hiddenStyles.clear();
        app.hiddenFills.clear();
        for (String obj : Engine.jsonObjects(json, "learned")) {
            Engine.Learned item = new Engine.Learned();
            item.id = Engine.jsonStr(obj, "\"id\"");
            item.name = Engine.jsonStr(obj, "\"name\"");
            if (item.id == null) item.id = Engine.newLearnedId();
            if (item.name == null) item.name = "Import";
            item.bpm = Engine.clampBpm(Engine.jsonInt(obj, "\"bpm\"", app.bpm()));
            item.tsNum = Engine.clampTsNum(Engine.jsonInt(obj, "\"tsNum\"", 4));
            item.tsDen = Engine.clampTsDen(Engine.jsonInt(obj, "\"tsDen\"", 4));
            item.steps = Engine.clampSteps(Engine.jsonInt(obj, "\"steps\"", Engine.STEPS));
            item.closest = Engine.jsonStr(obj, "\"closest\"");
            item.source = Engine.jsonStr(obj, "\"source\"");
            if (item.source != null && item.source.isEmpty()) item.source = null;
            item.cells = Engine.emptyCells();
            Engine.patternFromJson(obj, item.cells);
            app.learned.add(item);
            app.styles.put(item.id, new Engine.Style(item.id, item.name, item.bpm, Engine.rowsFromCells(item.cells)));
        }
        for (String obj : Engine.jsonObjects(json, "learnedFills")) {
            Engine.LearnedFill item = new Engine.LearnedFill();
            item.id = Engine.jsonStr(obj, "\"id\"");
            item.name = Engine.jsonStr(obj, "\"name\"");
            item.kind = Engine.jsonStr(obj, "\"kind\"");
            if (item.id == null) item.id = Engine.newLearnedId();
            if (item.name == null) item.name = "Fill";
            if (item.kind == null) item.kind = "toms";
            item.source = Engine.jsonStr(obj, "\"source\"");
            if (item.source != null && item.source.isEmpty()) item.source = null;
            item.cells = Engine.emptyCells();
            Engine.patternFromJson(obj, item.cells);
            app.learnedFills.add(item);
        }
        app.hiddenStyles.addAll(app.persistence.parseStringArray(json, "hiddenStyles"));
        app.hiddenFills.addAll(app.persistence.parseStringArray(json, "hiddenFills"));
        app.tsNum = Engine.clampTsNum(extractJsonInt(json, "\"tsNum\"", 4));
        app.tsDen = Engine.clampTsDen(extractJsonInt(json, "\"tsDen\"", 4));
        app.tsNumField.setText(Integer.toString(app.tsNum));
        app.tsDenField.setText(Integer.toString(app.tsDen));
        app.gridEditor.applySteps(Engine.clampSteps(extractJsonInt(json, "\"steps\"", app.steps)), false);
        List<Engine.Part> parts = Engine.decodeSng(json.getBytes(StandardCharsets.UTF_8));
        app.song.clear();
        app.song.addAll(parts);
        app.songLane = "original";
        byte[] sf2 = files.get("kit.sf2");
        if (sf2 != null) {
            short[][] loaded = AudioIo.parseSf2(sf2);
            for (int t = 0; t < app.voices.length && t < loaded.length; t++) {
                if (loaded[t] != null) app.voices[t] = loaded[t];
            }
        }
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (!e.getKey().startsWith("pads/") || e.getValue() == null) continue;
            try {
                AudioIo.Pcm pcm = AudioIo.parseWav(e.getValue());
                short[] s = new short[pcm.samples.length];
                for (int i = 0; i < s.length; i++) s[i] = (short) Math.max(-32768, Math.min(32767, pcm.samples[i] * 32768f));
                String id = e.getKey();
                for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                    if (id.contains(Engine.TRACK_ID[t])) {
                        app.voices[t] = s;
                        break;
                    }
                }
            } catch (Exception ignored) { /* raw pad bytes from web */ }
        }
        byte[] prog = null;
        String progName = null;
        byte[] legacy = files.get("scripts/0-script.py");
        if (legacy != null) {
            prog = legacy;
            progName = "script.py";
        }
        for (String k : files.keySet()) {
            if (k == null || !k.startsWith("scripts/")) continue;
            String low = k.toLowerCase();
            if (!(low.endsWith(".py") || low.endsWith(".java") || low.endsWith(".jar") || low.endsWith(".class") || low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".ts") || low.endsWith(".tsx") || low.endsWith(".prompt"))) continue;
            prog = files.get(k);
            progName = k.substring(k.lastIndexOf('/') + 1);
            break;
        }
        if (prog != null && progName != null) {
            boolean binary = progName.toLowerCase().endsWith(".jar") || progName.toLowerCase().endsWith(".class");
            app.pyJav.rememberProgram(progName, prog, binary, false);
        }
        app.plugins.clear();
        for (String k : files.keySet()) {
            if (!k.startsWith("plugins/") || !k.endsWith(".json")) continue;
            try {
                this.installPlugin(Engine.decodePlugin(files.get(k)), false);
            } catch (Exception ignored) { /* skip bad pack */ }
        }
        for (String obj : Engine.jsonObjects(json, "plugins")) {
            String pid = Engine.jsonStr(obj, "\"id\"");
            if (pid == null) continue;
            boolean on = !obj.contains("\"enabled\":false");
            this.setPluginEnabled(pid, on);
        }
        this.refreshPluginChips();
        app.styleLibrary.refreshLearnedChips();
        if (st != null && app.styles.containsKey(st)) app.style = st;
        app.styleLibrary.refreshStyles();
        app.gridEditor.refreshGrid();
        app.styleLibrary.refreshFills();
        app.songEditor.refreshSong();
        app.showView(app.song.isEmpty() ? (app.fillLast ? "combo" : "pattern") : "song");
        app.setNow("Project \u00b7 " + filename.replaceAll("^.*[/\\\\]", ""));
    }

    void loadPlugin(byte[] data, String filename) throws Exception {
        Engine.Plugin p = Engine.decodePlugin(data);
        this.installPlugin(p, true);
    }

    int extractJsonInt(String json, String key, int fallback) {
        int at = json.indexOf(key);
        if (at < 0) return fallback;
        int c = json.indexOf(':', at);
        if (c < 0) return fallback;
        int i = c + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n')) i++;
        int j = i;
        while (j < json.length() && "-0123456789".indexOf(json.charAt(j)) >= 0) j++;
        try {
            return Integer.parseInt(json.substring(i, j));
        } catch (Exception ex) {
            return fallback;
        }
    }

    double extractJsonDouble(String json, String key, double fallback) {
        int at = json.indexOf(key);
        if (at < 0) return fallback;
        int c = json.indexOf(':', at);
        if (c < 0) return fallback;
        int i = c + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n')) i++;
        int j = i;
        while (j < json.length() && "-0123456789.eE".indexOf(json.charAt(j)) >= 0) j++;
        try {
            return Double.parseDouble(json.substring(i, j));
        } catch (Exception ex) {
            return fallback;
        }
    }

    String extractJsonString(String json, String key) {
        int at = json.indexOf(key);
        if (at < 0) return null;
        int c = json.indexOf(':', at);
        if (c < 0) return null;
        int q = json.indexOf('"', c + 1);
        if (q < 0) return null;
        int q2 = json.indexOf('"', q + 1);
        if (q2 < 0) return null;
        return json.substring(q + 1, q2);
    }

    void copyPatternFromJson(String json, String key, int[][] dest) {
        int at = json.indexOf(key);
        if (at < 0) return;
        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
            String k = "\"" + Engine.TRACK_ID[t] + "\"";
            int p = json.indexOf(k, at);
            if (p < 0) continue;
            int br = json.indexOf('[', p);
            int cl = json.indexOf(']', br);
            if (br < 0 || cl < 0) continue;
            String[] nums = json.substring(br + 1, cl).split(",");
            for (int s = 0; s < Engine.MAX_STEPS && s < nums.length; s++) {
                try {
                    dest[t][s] = Integer.parseInt(nums[s].trim());
                } catch (Exception ignored) { /* */ }
            }
        }
    }

    void saveWav() {
        this.saveBytes(this.exportName("wav"), "WAV", "wav", AudioIo.encodeWav(app.playback.mixPcm(), 22050));
    }

    void saveMp3() {
        this.saveBytes(this.exportName("mp3"), "MP3", "mp3", AudioIo.encodeMp3(app.playback.mixPcm(), 22050));
    }

    void saveSf2() {
        app.playback.fillMissingVoices();
        String string = app.styles.containsKey(app.style) ? app.styles.get((Object)app.style).label : "Pulsekit";
        this.saveBytes(this.exportName("sf2"), "SoundFont", "sf2", AudioIo.encodeSf2(app.voices, string));
    }

    void saveBytes(String string, String string2, String string3, byte[] byArray) {
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setSelectedFile(new File(string));
        jFileChooser.setFileFilter(new FileNameExtensionFilter(string2, string3));
        if (jFileChooser.showSaveDialog(app) != 0) {
            return;
        }
        try {
            Files.write(jFileChooser.getSelectedFile().toPath(), byArray, new OpenOption[0]);
            this.exportToDb(jFileChooser.getSelectedFile(), byArray, string3);
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not save " + string2 + ": " + exception.getMessage());
        }
    }

    String exportName(String string) {
        String string2 = app.styles.containsKey(app.style) ? app.styles.get((Object)app.style).label : app.style;
        boolean bl = true;
        for (int i = 0; i < Engine.TRACK_ID.length && bl; ++i) {
            for (int j = 0; j < app.steps; ++j) {
                if (app.cells[i][j] <= 0) continue;
                bl = false;
            }
        }
        return AudioIo.fileName(bl ? "silent" : string2, app.bpm(), string, app.fillVariated);
    }

    File importSettingsFile() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "import-settings.txt");
    }

    boolean loadImportToDb() {
        try {
            File f = this.importSettingsFile();
            return f.isFile() && new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).contains("toDb=1");
        } catch (Exception ex) {
            return false;
        }
    }

    void saveImportToDb() {
        try {
            File f = this.importSettingsFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), ("toDb=" + (ImportDb.on ? 1 : 0) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // the setting stays for this session
        }
    }

    /** Import screen's Browse DB: a file from the prompt library, imported as Choose file would (not stored again). */
    void browseDb() {
        if (PromptDb.allFiles(null).isEmpty()) {
            app.setNow("The prompt library has no files yet");
            return;
        }
        app.promptDb.browse(null, (name, file) -> {
            try {
                this.ingest(Files.readAllBytes(file.toPath()), name);
            } catch (Exception exception) {
                JOptionPane.showMessageDialog(app, "Could not import " + name + ": " + exception.getMessage());
            }
        });
    }

    void openFile() {
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setFileFilter(new FileNameExtensionFilter("MIDI, song, WAV, MP3, SoundFont, Python, Java, JavaScript, prompt, project, plugin, file set", "sng", "mid", "midi", "wav", "wave", "mp3", "sf2", "py", "java", "class", "jar", "js", "mjs", "ts", "tsx", "prompt", "prj", "pkp", "fset"));
        if (jFileChooser.showOpenDialog(app) != 0) {
            return;
        }
        File file = jFileChooser.getSelectedFile();
        try {
            byte[] picked = Files.readAllBytes(file.toPath());
            this.ingest(picked, file.getName());
            // Import screen: "Import to DB also" keeps the picked file in the prompt library too.
            String toDb = ImportDb.store(PromptDb.dir(), file.getName(), picked);
            if (!toDb.isEmpty()) {
                // After what the import itself said, not over it.
                String now = app.nowPlaying.getText() == null ? "" : app.nowPlaying.getText().trim();
                app.setNow(now.isEmpty() ? toDb : now + " \u00b7 " + toDb);
            }
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not read that file: " + exception.getMessage());
        }
    }

    void ingest(byte[] byArray, String string) throws Exception {
        int n;
        String string2 = string.toLowerCase();
        String string3 = AudioIo.sniff(byArray);
        if ("unknown".equals(string3)) {
            if (string2.endsWith(".sng")) {
                string3 = "sng";
            } else if (string2.endsWith(".sf2")) {
                string3 = "sf2";
            } else if (string2.endsWith(".wav") || string2.endsWith(".wave")) {
                string3 = "wav";
            } else if (string2.endsWith(".mp3")) {
                string3 = "mp3";
            } else if (string2.endsWith(".mid") || string2.endsWith(".midi")) {
                string3 = "midi";
            } else if (string2.endsWith(".py")) {
                string3 = "py";
            } else if (string2.endsWith(".prompt")) {
                string3 = "prompt";
            } else if (string2.endsWith(".java")) {
                string3 = "java";
            } else if (string2.endsWith(".class")) {
                string3 = "class";
            } else if (string2.endsWith(".jar")) {
                string3 = "jar";
            } else if (string2.endsWith(".js") || string2.endsWith(".mjs") || string2.endsWith(".cjs")) {
                string3 = "js";
            } else if (string2.endsWith(".ts") || string2.endsWith(".mts") || string2.endsWith(".tsx")) {
                string3 = "ts";
            } else if (string2.endsWith(".prj") || Engine.isPrj(byArray)) {
                string3 = "prj";
            } else if (string2.endsWith(".pkp") || Engine.isPlugin(byArray)) {
                string3 = "pkp";
            } else if (string2.endsWith(".fset") || Engine.isFset(byArray)) {
                string3 = "fset";
            }
        }
        if ("prj".equals(string3) || Engine.isPrj(byArray)) {
            this.loadPrj(byArray, string);
            return;
        }
        if ("pkp".equals(string3) || Engine.isPlugin(byArray)) {
            this.loadPlugin(byArray, string);
            return;
        }
        if ("fset".equals(string3) || Engine.isFset(byArray)) {
            app.importLibrary.loadFset(byArray, string);
            return;
        }
        if ("py".equals(string3) || "java".equals(string3) || "class".equals(string3) || "jar".equals(string3) || "prompt".equals(string3) || "js".equals(string3) || "ts".equals(string3)) {
            if ("jar".equals(string3) && JavaRun.zipEntry(byArray, "pattern.json") != null) {
                byte[] mid = JavaRun.zipEntry(byArray, "beat.mid");
                if (mid != null) {
                    app.importLibrary.applyMidiBytes(mid, string);
                    return;
                }
            }
            String base = string.replaceAll("^.*[/\\\\]", "");
            boolean binary = "jar".equals(string3) || "class".equals(string3);
            app.pyJav.loadProgram(base, byArray, binary);
            return;
        }
        if ("sng".equals(string3)) {
            List<Engine.Part> list = Engine.decodeSng(byArray);
            if (list.isEmpty()) {
                throw new IllegalArgumentException("Could not read that .sng song");
            }
            app.song.clear();
            app.song.addAll(list);
            app.songLane = "original";
            app.showView("song");
            app.songEditor.refreshSong();
            Engine.Part part = list.get(0);
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                System.arraycopy(part.cells[i], 0, app.cells[i], 0, 16);
            }
            app.tempoBar.setVal(Engine.clampBpm(part.bpm));
            app.bpmField.setText(Integer.toString(part.bpm));
            app.gridEditor.refreshGrid();
            app.setNow(list.size() + " parts loaded");
            return;
        }
        if ("wav".equals(string3) || "mp3".equals(string3)) {
            File dir = new File(app.persistence.pulsekitDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            File copy = new File(dir, string.replace(' ', '_'));
            Files.write(copy.toPath(), byArray);
            app.pyJav.useAudioInput(copy);
            return;
        }
        if ("sf2".equals(string3)) {
            int n2;
            short[][] sArray = AudioIo.parseSf2(byArray);
            for (n2 = 0; n2 < app.voices.length; ++n2) {
                if (sArray[n2] == null) continue;
                app.voices[n2] = sArray[n2];
            }
            app.playback.fillMissingVoices();
            app.playback.reloadDrumBank();
            app.persistence.persistSf2(byArray);
            n2 = 0;
            for (short[] sArray2 : sArray) {
                if (sArray2 == null) continue;
                ++n2;
            }
            app.setNow("SoundFont \u00b7 " + n2 + " drums");
            return;
        }
        app.importLibrary.applyMidiBytes(byArray, string);
    }

    void tryDub() {
        this.installPlugin(Engine.examplePlugin(), true);
    }

    void savePkp() {
        try {
            String name = app.styles.containsKey(app.style) ? app.styles.get(app.style).label : "Pulsekit";
            String py = app.pyEditor != null ? app.pyEditor.getText() : "";
            byte[] pkp = Engine.encodePkp(
                "pulsekit." + app.style,
                name,
                app.bpm(),
                app.style,
                app.cells,
                app.fillPat,
                app.pyName,
                py);
            this.saveBytes(this.exportName("pkp"), "Pulsekit plugin", "pkp", pkp);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(app, "Could not save .pkp: " + ex.getMessage());
        }
    }

    /** The song's drum hits as audio, with the current drum set: "<song>_song_export.wav" / ".mp3". */
    void saveSongAudio(String ext) {
        List<Engine.Part> parts = app.songEditor.activeSong().isEmpty()
            ? Collections.singletonList(Engine.groove(this.exportName("mid").replace(".mid", ""), app.bpm(), app.cells, 1))
            : app.songEditor.activeSong();
        short[] pcm = app.playback.songPcm(parts);
        byte[] bytes = "mp3".equals(ext) ? AudioIo.encodeMp3(pcm, 22050) : AudioIo.encodeWav(pcm, 22050);
        this.saveBytes(Engine.songExportFilename(parts, app.songEditor.songFileSet(parts), ext), "mp3".equals(ext) ? "Song MP3" : "Song WAV", ext, bytes);
    }

    void saveSongMidi() {
        List<Engine.Part> parts = app.songEditor.activeSong().isEmpty()
            ? Collections.singletonList(Engine.groove(this.exportName("mid").replace(".mid", ""), app.bpm(), app.cells, 1))
            : app.songEditor.activeSong();
        this.saveBytes(Engine.songFilename(parts, app.songEditor.songFileSet(parts)).replace(".sng", ".mid"), "Song MIDI", "mid", Engine.encodeSongMidi(parts));
    }

    void saveJar() {
        try {
            this.saveBytes(this.exportName("jar"), "Kit snapshot", "jar",
                Engine.encodeKitJar(Engine.encodeMidi(app.cells, app.bpm(), app.steps, app.tsNum, app.tsDen), app.cells, app.bpm(), app.style));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(app, "Could not save JAR: " + ex.getMessage());
        }
    }
}
