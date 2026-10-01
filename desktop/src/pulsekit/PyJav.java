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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** The PyJav page: programs, prompts, run modes, input/output files, results and Params. */
final class PyJav {
    final Pulsekit app;

    PyJav(Pulsekit app) {
        this.app = app;
    }

    JTextField pyExtra;

    JComboBox<String> pyRecent;

    JLabel pyHint;

    JButton pyInputBtn;

    String pyInputToken;

    String pyOutputPath;

    String pyHintPlain = "";

    java.util.List<PyJavRecent.Item> pyRecentItems = new java.util.ArrayList<PyJavRecent.Item>();

    boolean pyRecentMute;

    JButton pyRun;

    String promptRunMode = "bash";

    JButton pyRunBash;

    JButton pyRunCmd;

    JButton pyRunAi;

    JPanel pyPromptModes;

    String promptDescription = "";

    String promptCategory = "";

    String promptModel = "";

    String promptResult = "";

    String promptOutputName = "";

    boolean promptOutputInvented;

    JPanel buildPyPage() {
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setOpaque(false);
        JLabel jLabel = new JLabel("PyJav");
        jLabel.setFont(new Font("SansSerif", 1, 20));
        jLabel.setForeground(FG);
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, 1));
        north.add(jLabel);
        north.add(Box.createVerticalStrut(8));
        this.pyRecent = new JComboBox<String>();
        this.pyRecent.setBackground(ELEV);
        this.pyRecent.setForeground(FG);
        this.pyRecent.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        this.pyRecent.setAlignmentX(0.0f);
        this.reloadPyRecent(0);
        this.pyRecent.addActionListener(ev -> this.applyPyRecent());
        north.add(this.pyRecent);
        north.add(Box.createVerticalStrut(6));
        north.add(app.programMenus.buildProgramMenus());
        this.pyHint = new JLabel("Possible extra args appear here after you browse a file.");
        this.pyHint.setForeground(FG);
        this.pyHint.setAlignmentX(0.0f);
        north.add(Box.createVerticalStrut(6));
        north.add(this.pyHint);
        north.add(Box.createVerticalStrut(8));
        JPanel modes = new JPanel(new FlowLayout(0, 8, 0));
        modes.setOpaque(false);
        modes.setAlignmentX(0.0f);
        this.pyRunBash = app.action("bash", ELEV, FG, () -> this.selectPromptRun("bash"));
        this.pyRunCmd = app.action("cmd", ELEV, FG, () -> this.selectPromptRun("cmd"));
        this.pyRunAi = app.action("AI", ELEV, FG, () -> this.selectPromptRun("ai"));
        modes.add(this.pyRunBash);
        modes.add(this.pyRunCmd);
        modes.add(this.pyRunAi);
        this.pyPromptModes = modes;
        this.pyPromptModes.setVisible(false);
        north.add(modes);
        this.paintPromptModes();
        JPanel controls = new JPanel(new FlowLayout(0, 8, 0));
        controls.setOpaque(false);
        controls.setAlignmentX(0.0f);
        controls.add(app.action("Browse file", ELEV, FG, () -> this.browsePyFile()));
        this.pyInputBtn = app.action("Browse input", ELEV, FG, () -> this.browseInputFile());
        this.pyInputBtn.setVisible(false);
        controls.add(this.pyInputBtn);
        controls.add(app.action("Params", ELEV, FG, () -> this.openParams()));
        this.pyRun = app.action("Run", HIT, BG, () -> this.runPython());
        controls.add(this.pyRun);
        JLabel extraLab = new JLabel("Extra args");
        extraLab.setForeground(MUTED);
        controls.add(extraLab);
        this.pyExtra = new JTextField(22);
        this.pyExtra.setBackground(ELEV);
        this.pyExtra.setForeground(FG);
        this.pyExtra.setCaretColor(FG);
        this.pyExtra.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        this.pyExtra.setToolTipText("Optional extra command-line flags, e.g. --out groove.mid");
        controls.add(this.pyExtra);
        north.add(controls);
        app.pyEditor = new JTextArea(PythonRun.defaultScript());
        app.pyEditor.setFont(new Font(Font.MONOSPACED, 0, 12));
        app.pyEditor.setBackground(ELEV);
        app.pyEditor.setForeground(FG);
        app.pyEditor.setCaretColor(FG);
        app.pyEditor.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(app.pyEditor);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        app.pyLog = new JTextArea(6, 40);
        app.pyLog.setEditable(false);
        app.pyLog.setLineWrap(true);
        app.pyLog.setWrapStyleWord(true);
        app.pyLog.setFont(new Font(Font.MONOSPACED, 0, 11));
        app.pyLog.setBackground(ELEV);
        app.pyLog.setForeground(MUTED);
        app.pyLog.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        app.pyLog.setText("Output appears here.");
        JScrollPane logScroll = new JScrollPane(app.pyLog);
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        logScroll.setPreferredSize(new Dimension(100, 120));
        jPanel.add((Component)north, "North");
        jPanel.add((Component)scroll, "Center");
        jPanel.add((Component)logScroll, "South");
        return jPanel;
    }

    void browsePyFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Python, Java, or prompt");
        chooser.setFileFilter(new FileNameExtensionFilter("Python, Java, JavaScript, or prompt", "py", "java", "class", "jar", "js", "mjs", "cjs", "ts", "tsx", "prompt"));
        if (chooser.showOpenDialog(app) != 0) return;
        File file = chooser.getSelectedFile();
        if (file == null) return;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String name = file.getName();
            String low = name.toLowerCase();
            boolean binary = low.endsWith(".jar") || low.endsWith(".class");
            if (!(low.endsWith(".py") || low.endsWith(".java") || low.endsWith(".prompt") || low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".cjs") || low.endsWith(".ts") || low.endsWith(".tsx") || binary)) {
                if (app.pyLog != null) app.pyLog.setText("Pick a .py, .java, .js, .ts, .class, .jar, or .prompt file.");
                return;
            }
            if (low.endsWith(".jar") && JavaRun.zipEntry(data, "pattern.json") != null) {
                if (app.pyLog != null) app.pyLog.setText("That JAR is a kit snapshot, not a program.");
                return;
            }
            this.loadProgram(name, data, binary);
            app.pyInputPath = null;
            String src = binary ? "" : new String(data, StandardCharsets.UTF_8);
            String status = low.endsWith(".prompt")
                ? "Run as bash, cmd, or AI."
                : PyJavHints.status(name, src, data);
            PyJavRecent.remember(this.pyRecentDir(), name, "", src, binary ? data : null);
            this.reloadPyRecent(0);
            this.showPyHint(status);
        } catch (Exception ex) {
            if (app.pyLog != null) app.pyLog.setText("Could not open that file: " + ex.getMessage());
        }
    }

    void showPromptModes() {
        boolean prompt = app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt");
        if (this.pyPromptModes != null) this.pyPromptModes.setVisible(prompt);
    }

    void selectPromptRun(String mode) {
        if (mode == null) mode = "bash";
        String chosen = PromptRun.normalizeType(mode);
        if (chosen.length() == 0 && "ai".equals(mode.toLowerCase())) chosen = "ai";
        if (chosen.length() == 0) chosen = "bash";
        this.promptRunMode = chosen;
        this.paintPromptModes();
        if (app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt")) this.showPromptReport();
    }

    void paintPromptModes() {
        this.paintPromptMode(this.pyRunBash, "bash");
        this.paintPromptMode(this.pyRunCmd, "cmd");
        this.paintPromptMode(this.pyRunAi, "ai");
    }

    void showPromptReport() {
        DesktopAi.Found found = DesktopAi.present();
        String report = PromptRun.report(this.promptCategory, this.promptRunMode, found.grok, found.sogni, found.claude);
        if (this.promptOutputName != null && this.promptOutputName.length() > 0) report = report + "\nOutput file: " + this.promptOutputName;
        if (app.pyLog != null) app.pyLog.setText(report);
        if (this.pyHint != null) this.pyHint.setText(report);
    }

    /** Picks an output name. Asks when the format is unclear. Blank keeps a log. False means cancel. */
    boolean preparePromptOutput() {
        String defined = this.promptResult == null ? "" : this.promptResult.trim();
        String body = app.pyEditor == null ? "" : app.pyEditor.getText();
        String fileName = PromptRun.chooseOutputName(app.pyName, this.promptCategory, this.promptRunMode, this.promptModel, body, defined);
        this.promptOutputInvented = defined.length() == 0;
        if (this.promptOutputInvented && PromptRun.logOutput(fileName)) {
            String answer = JOptionPane.showInputDialog(app, "The output format is unclear. Enter a name and extension, or leave blank and PyJav will write a log.", "Output file name", JOptionPane.QUESTION_MESSAGE);
            if (answer == null) return false;
            answer = answer.trim();
            if (answer.length() > 0) fileName = PromptRun.chooseOutputName(app.pyName, this.promptCategory, this.promptRunMode, this.promptModel, body, answer);
        }
        this.promptOutputName = fileName;
        File dir = new File(System.getProperty("user.home", "."), ".pulsekit/pyjav-out");
        if (!dir.isDirectory()) dir.mkdirs();
        this.pyOutputPath = new File(dir, fileName).getAbsolutePath();
        return true;
    }

    void runDesktopAi(String body) {
        DesktopAi.Found found = DesktopAi.present();
        if (found.choices.isEmpty()) {
            String report = PromptRun.report(this.promptCategory, "ai", false, false, false);
            if (app.pyLog != null) app.pyLog.setText(report);
            return;
        }
        DesktopAi.Choice chosen = found.choices.get(0);
        if (found.choices.size() > 1) {
            String[] labels = new String[found.choices.size()];
            for (int i = 0; i < found.choices.size(); i++) labels[i] = found.choices.get(i).name;
            Object pick = JOptionPane.showInputDialog(app, "Which system should run this prompt?", "PyJav", JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
            if (pick == null) {
                if (app.pyLog != null) app.pyLog.setText("Cancelled.");
                return;
            }
            for (int i = 0; i < found.choices.size(); i++) {
                if (found.choices.get(i).name.equals(pick)) chosen = found.choices.get(i);
            }
        }
        final DesktopAi.Choice choice = chosen;
        final String prompt = body == null ? "" : body;
        if (!"api".equals(choice.kind)) {
            String line = DesktopAi.launch(choice, prompt);
            String shown = PromptRun.report(this.promptCategory, "ai", found.grok, found.sogni, found.claude) + "\n" + line + "\nOutput file: " + this.promptOutputName;
            this.writePromptOutput(shown);
            if (app.pyLog != null) app.pyLog.setText(shown);
            app.setNow(line);
            return;
        }
        if (app.pyLog != null) app.pyLog.setText("Running…");
        if (this.pyRun != null) this.pyRun.setEnabled(false);
        new Thread(() -> {
            final JavaRun.Result result = PromptRun.run(prompt, "ai");
            SwingUtilities.invokeLater(() -> {
                if (this.pyRun != null) this.pyRun.setEnabled(true);
                String shown = result == null || result.log == null || result.log.length() == 0 ? "(no output)" : result.log;
                shown = shown + "\nOutput file: " + this.promptOutputName;
                this.writePromptOutput(shown);
                if (app.pyLog != null) app.pyLog.setText(shown);
                app.setNow("Grok");
            });
        }, "pulsekit-ai").start();
    }

    void writePromptOutput(String text) {
        if (!this.promptOutputInvented || this.promptOutputName == null || this.pyOutputPath == null) return;
        try {
            File file = new File(this.pyOutputPath);
            if (file.isFile() && file.length() > 0) return;
            if (!PromptRun.logOutput(this.promptOutputName)) return;
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            Files.write(file.toPath(), (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    void paintPromptMode(JButton button, String mode) {
        if (button == null) return;
        boolean on = mode.equals(this.promptRunMode);
        button.setBackground(on ? HIT : ELEV);
        button.setForeground(on ? BG : FG);
    }

    void runPython() {
        if (app.pyEditor == null) return;
        final String listed = app.programMenus.listedProgramCurrent() ? app.listedSource : null;
        final String src = listed != null ? listed : app.pyEditor.getText();
        final String name = app.pyName == null || app.pyName.isEmpty() ? "drum_midi.py" : app.pyName;
        final byte[] bytes = app.pyBytes;
        final boolean promptProg = name.toLowerCase().endsWith(".prompt");
        final String runSrc = promptProg ? PromptRun.withoutDescription(this.promptDescription, src) : src;
        if (promptProg && !this.preparePromptOutput()) {
            if (app.pyLog != null) app.pyLog.setText("Cancelled.");
            return;
        }
        if (promptProg && "ai".equals(this.promptRunMode)) {
            this.runDesktopAi(runSrc);
            return;
        }
        if (!promptProg) {
            this.promptOutputInvented = false;
            this.promptOutputName = "";
        }
        // Saved Params for this program replace its DrumMidi switches; file paths stay.
        final String extra = DrumMidiArgs.merge(this.pyExtra != null ? this.pyExtra.getText() : "", this.loadParams(app.pyName));
        File outDir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (app.pyInputPath != null && app.pyInputPath.length() > 0) {
            File parent = new File(app.pyInputPath).getParentFile();
            if (parent != null) outDir = parent;
        }
        if (!outDir.isDirectory()) outDir.mkdirs();
        final String filled = PyJavHints.fillArgs(extra, this.pyHintPlain, this.pyInputToken, app.pyInputPath, outDir.getAbsolutePath());
        String hintedOut = PyJavHints.outputFile(app.pyInputPath, (this.pyHintPlain == null ? "" : this.pyHintPlain) + "\n" + extra, outDir.getAbsolutePath());
        if (!(promptProg && this.promptOutputInvented && this.pyOutputPath != null && this.pyOutputPath.length() > 0)) this.pyOutputPath = hintedOut;
        final String outPath = this.pyOutputPath == null ? "" : this.pyOutputPath;
        if (this.pyExtra != null && filled.length() > 0) this.pyExtra.setText(filled);
        PyJavRecent.remember(this.pyRecentDir(), name, filled, src, bytes);
        this.reloadPyRecent(0);
        final java.util.List<String> argv;
        if ((app.pyInputPath != null && app.pyInputPath.length() > 0) || outPath.length() > 0) {
            argv = PyJavHints.programArgs(filled, this.pyInputToken, app.pyInputPath, this.pyHintPlain, outDir.getAbsolutePath());
        } else {
            argv = PythonRun.kitArgv(src, app.bpm(), app.style, app.bars, app.swingBar.getVal());
            argv.addAll(PythonRun.splitArgv(extra));
        }
        if (app.pyLog != null) app.pyLog.setText("Running…");
        if (this.pyRun != null) this.pyRun.setEnabled(false);
        new Thread(() -> {
            final boolean javaProg = !promptProg && isJavaName(name);
            java.util.List<String> runArgv = argv;
            if (promptProg) {
                runArgv = new java.util.ArrayList<String>(argv);
                runArgv.add("--pk-run");
                runArgv.add(this.promptRunMode);
                if (this.promptOutputInvented && outPath.length() > 0 && !runArgv.contains(outPath)) runArgv.add(outPath);
            }
            final PythonRun.Result result = promptProg
                ? asPython(JavaRun.run(name, runSrc, bytes, runArgv))
                : javaProg
                    ? asPython(JavaRun.run(name, src, bytes, argv))
                    : PythonRun.run(src, name, argv);
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (this.pyRun != null) this.pyRun.setEnabled(true);
                String shown = result.log == null || result.log.isEmpty() ? "(no output)" : result.log;
                if (promptProg && this.promptOutputName.length() > 0) shown = shown + "\nOutput file: " + this.promptOutputName;
                this.writePromptOutput(shown);
                if (app.pyLog != null) app.pyLog.setText(shown);
                boolean loaded = false;
                int midis = 0;
                StringBuilder status = new StringBuilder();
                for (PythonRun.FileOut f : result.files) {
                    String lower = f.name.toLowerCase();
                    boolean midi = f.bytes != null && f.bytes.length >= 4
                        && f.bytes[0] == 'M' && f.bytes[1] == 'T' && f.bytes[2] == 'h' && f.bytes[3] == 'd';
                    if ((lower.endsWith(".mid") || lower.endsWith(".midi")) && midi) {
                        status.append(app.importLibrary.applyProgramMidi(f.bytes, f.name)).append('\n');
                        midis++;
                        loaded = true;
                    } else if (lower.endsWith(".sng")) {
                        try {
                            java.util.List<Engine.Part> parts = Engine.decodeSng(f.bytes);
                            if (parts != null && !parts.isEmpty()) {
                                app.song.clear();
                                app.song.addAll(parts);
                                app.songLane = "original";
                                app.songEditor.refreshSong();
                                app.showView("song");
                                app.setNow(parts.size() + " parts from script");
                            }
                        } catch (Exception ex) {
                            if (app.pyLog != null) app.pyLog.append("\nCould not read " + f.name);
                        }
                    }
                }
                if (!promptProg && midis == 0 && this.pyOutputPath != null && this.pyOutputPath.length() > 0) {
                    java.io.File out = new java.io.File(this.pyOutputPath);
                    if (out.isFile()) {
                        try {
                            status.append(app.importLibrary.applyProgramMidi(java.nio.file.Files.readAllBytes(out.toPath()), out.getName())).append('\n');
                            midis++;
                            loaded = true;
                        } catch (Exception ex) {
                            status.append("Import failed: ").append(ex.getMessage() == null ? "could not read the output file" : ex.getMessage()).append('\n');
                        }
                    }
                }
                if (midis == 0 && promptProg) {
                    String line = result.log == null ? "" : result.log;
                    int nl = line.indexOf('\n');
                    String first = (nl < 0 ? line : line.substring(0, nl)).trim();
                    if (first.isEmpty()) first = result.code == 0 ? "Prompt finished" : "Prompt failed";
                    status.append(first).append('\n');
                } else if (midis == 0) status.append("Import failed: no MIDI file was written\n");
                if (this.pyHint != null && status.length() > 0) this.pyHint.setText(hintHtml(status.toString().trim()));
                if (status.length() > 0) app.setNow(status.toString().trim().split("\n")[0]);
                if (app.pyLog != null && status.length() > 0) {
                    app.pyLog.setText(status.toString() + (app.pyLog.getText() == null ? "" : "\n" + app.pyLog.getText()));
                }
                if (loaded) app.setNow("Script MIDI · " + name);
            });
        }, "pulsekit-pyjav").start();
    }

    File pyRecentDir() {
        File dir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    void reloadPyRecent(int select) {
        this.pyRecentItems = PyJavRecent.load(this.pyRecentDir());
        if (this.pyRecent == null) return;
        this.pyRecentMute = true;
        this.pyRecent.removeAllItems();
        this.pyRecent.addItem("Recent");
        for (PyJavRecent.Item item : this.pyRecentItems) this.pyRecent.addItem(item.label());
        int index = select > 0 && select <= this.pyRecentItems.size() ? select : 0;
        this.pyRecent.setSelectedIndex(index);
        this.pyRecentMute = false;
    }

    void applyPyRecent() {
        if (this.pyRecentMute || this.pyRecent == null) return;
        int index = this.pyRecent.getSelectedIndex();
        if (index <= 0 || this.pyRecentItems == null || index > this.pyRecentItems.size()) return;
        PyJavRecent.Item item = this.pyRecentItems.get(index - 1);
        app.pyInputPath = null;
        boolean binary = item.bytes != null && item.bytes.length > 0;
        byte[] data = binary ? item.bytes : (item.source == null ? new byte[0] : item.source.getBytes(StandardCharsets.UTF_8));
        this.rememberProgram(item.name, data, binary, false);
        app.pyInputPath = null;
        this.showPyHint(PyJavHints.status(item.name, item.source, item.bytes));
        // The hint fills args from the program's usage; the recent item's own args win.
        if (this.pyExtra != null) this.pyExtra.setText(item.extra);
    }

    void showPyHint(String status) {
        this.pyHintPlain = status == null ? "" : status;
        if (app.pyInputPath == null && this.audioInputPath != null && new File(this.audioInputPath).isFile()) {
            app.pyInputPath = this.audioInputPath;
        }
        if (app.pyLog != null) app.pyLog.setText(this.pyHintPlain);
        if (this.pyHint != null) this.pyHint.setText(hintHtml(this.pyHintPlain));
        this.pyInputToken = PyJavHints.firstInput(this.pyHintPlain);
        File outDir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (!outDir.isDirectory()) outDir.mkdirs();
        String out = PyJavHints.outputFile(app.pyInputPath, this.pyHintPlain, outDir.getAbsolutePath());
        if (out.length() > 0) {
            this.pyOutputPath = out;
            String filled = PyJavHints.fillArgs("", this.pyHintPlain, this.pyInputToken, app.pyInputPath, outDir.getAbsolutePath());
            if (this.pyExtra != null) this.pyExtra.setText(filled);
            String note = this.pyHintPlain + "\nOutput file: " + out + "\nExtra args: " + filled;
            if (app.pyLog != null) app.pyLog.setText(note);
            if (this.pyHint != null) this.pyHint.setText(hintHtml(note));
        }
        if (this.pyInputBtn != null) {
            this.pyInputBtn.setVisible(this.pyInputToken != null);
            if (this.pyInputToken != null) this.pyInputBtn.setText("Browse " + this.pyInputToken);
        }
    }

    /** Last WAV/MP3 imported as PyJav input; programs opened later still get it. */
    String audioInputPath;

    /** Imported WAV or MP3 becomes the input of the PyJav program, e.g. MidiDrumGen.java. */
    void useAudioInput(File file) {
        this.audioInputPath = file.getAbsolutePath();
        app.showView("py");
        this.setInputFile(file);
        app.setNow("PyJav input \u00b7 " + file.getName() + " \u00b7 run MidiDrumGen.java to make MIDI");
    }

    void browseInputFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(this.pyInputToken == null ? "Input file" : this.pyInputToken);
        chooser.setAcceptAllFileFilterUsed(true);
        if (chooser.showOpenDialog(app) != 0) return;
        File file = chooser.getSelectedFile();
        if (file == null) return;
        this.setInputFile(file);
    }

    File paramsFile() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "pyjav-params.properties");
    }

    /** Saved DrumMidi switches for a program, or null when none were saved. */
    String loadParams(String program) {
        if (program == null) return null;
        java.util.Properties props = new java.util.Properties();
        File f = this.paramsFile();
        if (!f.isFile()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            props.load(in);
        } catch (Exception ignored) {
            return null;
        }
        return props.getProperty(program);
    }

    void saveParams(String program, String args) {
        java.util.Properties props = new java.util.Properties();
        File f = this.paramsFile();
        try {
            if (f.isFile()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                    props.load(in);
                }
            }
            props.setProperty(program, args);
            f.getParentFile().mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                props.store(out, "PyJav Params per program");
            }
        } catch (Exception ignored) {
            // kept in the args field for this session
        }
    }

    /** Params: DrumMidi switches, one field each, saved per program; with Reset to defaults / suggested values. */
    void openParams() {
        final String program = app.pyName == null || app.pyName.isEmpty() ? "program" : app.pyName;
        java.util.Map<String, String> saved = DrumMidiArgs.read(this.loadParams(program));
        java.util.Map<String, String> current = DrumMidiArgs.read(this.pyExtra != null ? this.pyExtra.getText() : "");
        final JTextField[] fields = new JTextField[DrumMidiArgs.FLAGS.length];
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        for (int i = 0; i < DrumMidiArgs.FLAGS.length; i++) {
            String flag = DrumMidiArgs.FLAGS[i];
            form.add(new JLabel(DrumMidiArgs.LABELS[i] + "  " + flag));
            String value = saved.containsKey(flag) ? saved.get(flag) : current.get(flag);
            JTextField field = new JTextField(value == null ? "" : value, 10);
            field.setToolTipText("Default: " + DrumMidiArgs.HINTS[i]);
            fields[i] = field;
            form.add(field);
        }
        JPanel resets = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton defaults = new JButton("Reset to defaults");
        defaults.addActionListener(e -> { for (JTextField f : fields) f.setText(""); });
        JButton suggested = new JButton("Reset to suggested values");
        suggested.addActionListener(e -> { for (int i = 0; i < fields.length; i++) fields[i].setText(DrumMidiArgs.SUGGESTED[i]); });
        resets.add(defaults);
        resets.add(suggested);
        JPanel box = new JPanel(new BorderLayout(0, 8));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        resets.setAlignmentX(0.0f);
        top.add(resets);
        JLabel note = new JLabel("<html><body style='width:380px'>" + DrumMidiArgs.NOTE + " Empty fields use the default shown when you point at them.</body></html>");
        note.setAlignmentX(0.0f);
        top.add(note);
        box.add(top, BorderLayout.NORTH);
        box.add(form, BorderLayout.CENTER);
        int ans = JOptionPane.showConfirmDialog(app, box, "DrumMidi parameters \u00b7 " + program, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ans != JOptionPane.OK_OPTION) return;
        String[] values = new String[fields.length];
        for (int i = 0; i < fields.length; i++) values[i] = fields[i].getText();
        String args = DrumMidiArgs.build(values);
        this.saveParams(program, args);
        if (this.pyExtra != null) this.pyExtra.setText(DrumMidiArgs.merge(this.pyExtra.getText(), args));
        app.setNow("Params saved");
    }

    void setInputFile(File file) {
        if (this.pyExtra == null) return;
        app.pyInputPath = file.getAbsolutePath();
        File folder = file.getParentFile() == null ? new File(System.getProperty("user.home", "."), ".pulsekit") : file.getParentFile();
        String note = PyJavHints.outputNotice(this.pyExtra.getText(), this.pyHintPlain, app.pyInputPath, folder.getAbsolutePath());
        String args = PyJavHints.fillArgs(this.pyExtra.getText(), this.pyHintPlain, this.pyInputToken, app.pyInputPath, folder.getAbsolutePath());
        this.pyOutputPath = PyJavHints.outputFile(app.pyInputPath, this.pyHintPlain, folder.getAbsolutePath());
        this.pyExtra.setText(args);
        if (app.pyLog != null) app.pyLog.setText(note);
        if (this.pyHint != null) this.pyHint.setText(hintHtml(note));
    }

    void loadProgram(String name, byte[] data, boolean binary) {
        this.rememberProgram(name, data, binary, true);
    }

    void rememberProgram(String name, byte[] data, boolean binary, boolean open) {
        app.pyName = name == null || name.isEmpty() ? "script.py" : name;
        app.pyBytes = binary ? data : null;
        if (app.pyEditor != null) {
            app.pyEditor.setEditable(!binary);
            if (binary) {
                app.pyEditor.setText("// " + app.pyName + "\n// Binary program. Run uses this file.\n// Extra args are passed to java.\n");
            } else {
                String text = new String(data, StandardCharsets.UTF_8);
                if (app.pyName.toLowerCase().endsWith(".prompt")) {
                    PromptRun.Sheet sheet = PromptRun.parse(text);
                    this.promptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
                    this.promptModel = sheet == null || sheet.model == null ? "" : sheet.model;
                    this.promptResult = sheet == null || sheet.result == null ? "" : sheet.result;
                    this.promptDescription = sheet == null || sheet.description == null ? "" : sheet.description;
                    this.promptOutputName = "";
                    this.selectPromptRun(PromptRun.runMode(text, sheet));
                    if (sheet != null && sheet.body != null) text = PromptRun.withoutDescription(this.promptDescription, sheet.body);
                }
                app.pyEditor.setText(text);
            }
        }
        this.showPromptModes();
        app.programMenus.paintProgramMenus();
        if (!open) return;
        app.showView("py");
        app.setNow("PyJav · " + app.pyName);
    }
}
