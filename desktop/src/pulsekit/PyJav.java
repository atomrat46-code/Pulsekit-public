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
    /** What the last run wrote in the output box; only that text can be saved as results. */
    String runLog;

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
        SaveText.attach(app, app.pyLog, () -> this.runLog != null && this.runLog.equals(app.pyLog.getText()) ? PyJavHints.resultsFileName(app.pyName) : null);
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
            app.codeSave.opened(file);
            app.pyInputPath = null;
            String src = binary ? "" : new String(data, StandardCharsets.UTF_8);
            String status = low.endsWith(".prompt")
                ? "Run as bash, cmd, or AI."
                : PyJavHints.status(name, src, data);
            PyJavRecent.remember(this.pyRecentDir(), name, "", src, binary ? data : null);
            this.reloadPyRecent(0);
            this.showPyHint(status);
            if (low.endsWith(".prompt")) this.loadPromptRefs(src);
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
            this.runLog = shown;
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
                this.runLog = shown;
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
        final String listed = app.programMenus.listedSourceToRun();
        final String src = listed != null ? listed : app.pyEditor.getText();
        final String name = app.pyName == null || app.pyName.isEmpty() ? "drum_midi.py" : app.pyName;
        if (this.mustWait(name, true)) return;
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
        // Saved Params for this program replace its own switches; file paths stay.
        final String extra = ProgramParams.merge(ProgramParams.parse(PyJavHints.programText(name, src, bytes)),
            this.pyExtra != null ? this.pyExtra.getText() : "", this.loadParams(app.pyName));
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
            argv = PythonRun.kitArgv(src, app.bpm(), app.style, app.bars, app.swingBar.getVal(), extra, app.tsNum, app.tsDen);
        }
        if (app.pyLog != null) app.pyLog.setText("Running…");
        if (this.pyRun != null) this.pyRun.setEnabled(false);
        // The run is pending (PendingOps) until what it stores in the prompt library is stored.
        PromptDb.dir();
        final String pending = PendingOps.guarded(name, false) ? PendingOps.add(name, PendingOps.files(argv)) : null;
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
                // The library work below runs on this same thread, before anything else can start.
                PendingOps.done(pending);
                String shown = result.log == null || result.log.isEmpty() ? "(no output)" : result.log;
                if (promptProg && this.promptOutputName.length() > 0) shown = shown + "\nOutput file: " + this.promptOutputName;
                this.writePromptOutput(shown);
                // What the prompt made is kept as its result file in the prompt library, when it has none yet.
                if (promptProg && this.pyOutputPath != null && this.promptOutputName.length() > 0) {
                    try {
                        File made = new File(this.pyOutputPath);
                        if (made.isFile()) PromptDb.storeResult(this.promptSource, this.promptOutputName, Files.readAllBytes(made.toPath()));
                    } catch (Exception ignored) {
                        // kept on disk only
                    }
                }
                if (app.pyLog != null) app.pyLog.setText(shown);
                boolean loaded = false;
                int midis = 0;
                int audios = 0;
                int texts = 0;
                java.util.Map<String, File> savedAudio = new java.util.HashMap<String, File>();
                java.util.List<File> savedPictures = new java.util.ArrayList<File>();
                java.util.List<File> savedVideos = new java.util.ArrayList<File>();
                StringBuilder status = new StringBuilder();
                PythonRun.FileOut madeMidi = null;
                for (PythonRun.FileOut f : result.files) {
                    String lower = f.name.toLowerCase();
                    boolean midi = f.bytes != null && f.bytes.length >= 4
                        && f.bytes[0] == 'M' && f.bytes[1] == 'T' && f.bytes[2] == 'h' && f.bytes[3] == 'd';
                    if ((lower.endsWith(".mid") || lower.endsWith(".midi")) && midi) {
                        status.append(app.importLibrary.applyProgramMidi(f.bytes, f.name)).append('\n');
                        midis++;
                        loaded = true;
                        madeMidi = f;
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
                    } else if (lower.matches(".*\\.(mp3|wav|flac|m4a|ogg|aac|prompt|png|jpe?g|webp|gif|mp4|webm|mov|glb)$")) {
                        // Audio, a prompt sheet, a picture or a video a program wrote in its work folder (SogniMusic's
                        // track and --saveprompt, SogniChat's tool results): kept in Downloads, as on Android.
                        File saved = this.saveProgramFile(f.name, f.bytes);
                        status.append(saved == null ? "Could not save " + f.name : "Saved " + saved.getPath()).append('\n');
                        if (saved != null) {
                            audios++;
                            savedAudio.put(f.name, saved);
                            if (lower.matches(".*\\.(png|jpe?g|webp|gif)$")) savedPictures.add(saved);
                            if (lower.matches(".*\\.(mp4|m4v|webm|mov)$")) savedVideos.add(saved);
                        }
                    } else if (lower.endsWith(".txt")) {
                        // A text file a program wrote (SogniChat's reply): kept in Downloads, as on Android.
                        File saved = this.saveProgramFile(f.name, f.bytes);
                        status.append(saved == null ? "Could not save " + f.name : "Saved " + saved.getPath()).append('\n');
                        if (saved != null) texts++;
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
                } else if (midis == 0 && audios == 0 && texts == 0) {
                    // A program that says how it went (SogniChat --models, a bad argument) is believed, as on Android.
                    String verdict = null;
                    for (String line : (result.log == null ? "" : result.log).split("\n")) {
                        String t = line.trim();
                        if (t.startsWith("Succeeded") || t.startsWith("Failed:")) verdict = t;
                    }
                    status.append(verdict != null ? verdict : "Import failed: no MIDI file was written").append('\n');
                }
                if (this.pyHint != null && status.length() > 0) this.pyHint.setText(hintHtml(status.toString().trim()));
                if (status.length() > 0) app.setNow(status.toString().trim().split("\n")[0]);
                if (app.pyLog != null && status.length() > 0) {
                    app.pyLog.setText(status.toString() + (app.pyLog.getText() == null ? "" : "\n" + app.pyLog.getText()));
                }
                if (app.pyLog != null) this.runLog = app.pyLog.getText();
                SogniHistory.record(result.log, System.currentTimeMillis());
                // A prompt sheet the run saved (--saveprompt) goes into the prompt library, with its pictures and result;
                // MidiDrumGen's MIDI and SogniMusic's track too, when Drum Midi Settings says so.
                String kept = this.keepInLibrary(name, result, argv);
                if (kept.length() > 0 && app.pyLog != null) {
                    app.pyLog.append("\n" + kept);
                    this.runLog = app.pyLog.getText();
                }
                if (loaded) app.setNow("Script MIDI · " + name);
                // A run that made an audio file (SogniMusic's track): play it, or make drum MIDI from it.
                String made = PyJavHints.madeAudio(result.log);
                if (made != null && savedAudio.containsKey(made)) this.offerAudio(savedAudio.get(made));
                // Pictures it made (SogniChat's tool results) are shown.
                if (!savedPictures.isEmpty() && result.code == 0) this.offerPictures(savedPictures);
                // A video it made (SogniVideo's clip, a SogniChat tool result) is offered for playing.
                if (!savedVideos.isEmpty() && result.code == 0) {
                    // A joined clip (SogniVideo's Join with this video) is the one to watch.
                    File watch = savedVideos.get(0);
                    for (File v : savedVideos) if (v.getName().toLowerCase().matches(".+-merged(\\(\\d+\\))?\\.mp4")) watch = v;
                    this.offerVideo(watch, savedVideos.size());
                }
                // MidiDrumGen's groove is played with the kit's sounds, with Play / Stop.
                if (madeMidi != null && result.code == 0 && "MidiDrumGen.java".equals(name)) this.offerMidi(madeMidi.name, madeMidi.bytes);
                // MediaBrowser: its folder in the Media browser.
                String browse = result.code == 0 && "MediaBrowser.java".equals(name) ? MediaDir.opened(result.log) : null;
                if (browse != null) app.mediaBrowser.open(new File(browse));
            });
        }, "pulsekit-pyjav").start();
    }

    /**
     * After a run that made pictures: each is shown under its name, scaled to fit, with Open (the
     * system viewer, for the first) and Close. A picture Java cannot read (WebP) is named only.
     */
    void offerPictures(java.util.List<File> pictures) {
        JPanel col = new JPanel();
        col.setLayout(new javax.swing.BoxLayout(col, javax.swing.BoxLayout.Y_AXIS));
        for (File f : pictures) {
            java.awt.image.BufferedImage img = null;
            try {
                img = javax.imageio.ImageIO.read(f);
            } catch (Exception ignored) {
                img = null;
            }
            JLabel name = new JLabel(f.getName() + (img == null ? " (no preview)" : ""));
            name.setAlignmentX(0.0f);
            col.add(name);
            if (img == null) continue;
            double scale = Math.min(1.0, 480.0 / Math.max(img.getWidth(), img.getHeight()));
            java.awt.Image shown = img.getScaledInstance(Math.max(1, (int) (img.getWidth() * scale)), Math.max(1, (int) (img.getHeight() * scale)), java.awt.Image.SCALE_SMOOTH);
            JLabel view = new JLabel(new javax.swing.ImageIcon(shown));
            view.setName("picture-offer:" + f.getName());
            view.setAlignmentX(0.0f);
            col.add(view);
            col.add(javax.swing.Box.createVerticalStrut(8));
        }
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(col);
        scroll.setPreferredSize(new java.awt.Dimension(520, Math.min(560, col.getPreferredSize().height + 8)));
        Object[] options = new Object[] {"Open", "Close"};
        int ans = JOptionPane.showOptionDialog(app, scroll, pictures.size() == 1 ? "Picture ready" : pictures.size() + " pictures ready",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[1]);
        if (ans == 0) {
            try {
                java.awt.Desktop.getDesktop().open(pictures.get(0));
            } catch (Exception ex) {
                app.setNow("Could not open a viewer for " + pictures.get(0).getName());
            }
        }
    }

    /** A text file's contents into a prompt field (a prompt sheet gives its prompt). */
    void promptFrom(javax.swing.JTextArea area, File file) {
        try {
            String text = ProgramParams.promptText(java.nio.file.Files.readAllBytes(file.toPath()));
            if (text.length() == 0) {
                app.setNow(file.getName() + " has no text");
                return;
            }
            area.setText(text);
            area.setCaretPosition(0);
        } catch (Exception ex) {
            app.setNow("Could not read " + file.getName());
        }
    }

    static void collectButtons(java.awt.Container c, List<JButton> out) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof JButton) out.add((JButton) k);
            else if (k instanceof java.awt.Container) collectButtons((java.awt.Container) k, out);
        }
    }

    /** The run's prompt sheets, and its MIDI or track when Drum Midi Settings says so, into the prompt library; a line for the log. */
    String keepInLibrary(String name, PythonRun.Result result, java.util.List<String> argv) {
        java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
        for (PythonRun.FileOut f : result.files) files.add(new JavaRun.FileOut(f.name, f.bytes));
        JavaRun.Result r = new JavaRun.Result(result.log, files, result.code);
        File dir = PromptDb.dir();
        String kept = PromptKeep.keep(dir, r, argv);
        if (DrumMidiSettingsPage.genToDb && "MidiDrumGen.java".equals(name)) {
            String gen = PromptKeep.keepMidiDrumGen(dir, r, argv);
            if (gen.length() > 0) kept = kept.length() > 0 ? kept + "\n" + gen : gen;
        }
        // JoinVideo with --addtodb: the joined video goes in as a result file.
        if ("JoinVideo.java".equals(name)) {
            String joined = PromptKeep.keepJoined(dir, r, argv);
            if (joined.length() > 0) kept = kept.length() > 0 ? kept + "\n" + joined : joined;
        }
        // SogniVideo: a joined clip (Join with this video) goes in as a result file, and Join is unticked
        // for the next run.
        if ("SogniVideo.java".equals(name)) {
            String merged = PromptKeep.keepMerged(dir, r);
            if (merged.length() > 0) kept = kept.length() > 0 ? kept + "\n" + merged : merged;
            if (this.pyExtra != null && this.pyExtra.getText().indexOf("--join") >= 0) this.pyExtra.setText(ProgramParams.drop(this.pyExtra.getText(), "--join"));
            String saved = this.loadParams(name);
            if (saved != null && saved.indexOf("--join") >= 0) this.saveParams(name, ProgramParams.drop(saved, "--join"));
        }
        if (DrumMidiSettingsPage.musicToDb && "SogniMusic.java".equals(name)) {
            String music = PromptKeep.keepSogniMusic(dir, r, argv);
            if (music.length() > 0) kept = kept.length() > 0 ? kept + "\n" + music : music;
        }
        return kept;
    }

    /**
     * After a MidiDrumGen run: the groove played with Pulsekit's kit sounds (the imported SoundFont,
     * through the active drum set), as a file set plays its source MIDI. Play / Stop and Close; the
     * MIDI is already imported as a file set. Nothing is shown when it has no drums for the kit.
     */
    void offerMidi(String midiName, byte[] midi) {
        short[] pcm;
        try {
            pcm = AudioIo.renderMidiDrums(midi, app.playback.mixVoices(), 22050);
        } catch (Exception ex) {
            pcm = null;
        }
        if (pcm == null || pcm.length == 0) return;
        byte[] pcmBytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            pcmBytes[2 * i] = (byte) pcm[i];
            pcmBytes[2 * i + 1] = (byte) (pcm[i] >> 8);
        }
        int seconds = Math.round(pcm.length / 22050f);
        String length = (seconds / 60) + ":" + String.format(java.util.Locale.US, "%02d", seconds % 60);
        javax.sound.sampled.Clip clip = null;
        try {
            while (true) {
                boolean playing = clip != null && clip.isRunning();
                Object[] options = new Object[] {playing ? "Stop" : "Play", "Close"};
                int ans = JOptionPane.showOptionDialog(app, midiName + " (" + length + ") is imported as a file set.\n\n"
                    + "Play sounds it with Pulsekit's kit (your imported SoundFont, if any).", "MIDI ready",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                if (ans != 0) break;
                if (playing) {
                    clip.stop();
                    continue;
                }
                try {
                    if (clip == null) {
                        clip = javax.sound.sampled.AudioSystem.getClip();
                        clip.open(new javax.sound.sampled.AudioFormat(22050f, 16, 1, true, false), pcmBytes, 0, pcmBytes.length);
                    }
                    clip.setFramePosition(0);
                    clip.start();
                } catch (Exception ex) {
                    app.setNow("No audio output for the MIDI");
                    break;
                }
            }
        } finally {
            if (clip != null) {
                clip.stop();
                clip.close();
            }
        }
    }

    /**
     * After a run that made a video: Play opens a player page in the browser (Java has no video
     * decoder) with the same controls as on the phone: Play/Pause, Stop, Mute and a volume slider.
     * Open uses the system player; Close leaves it saved.
     */
    void offerVideo(File video, int count) {
        Object[] options = new Object[] {"Play", "Open", "Close"};
        String more = count > 1 ? "\n" + (count - 1) + " more " + (count == 2 ? "video is" : "videos are") + " saved beside it." : "";
        int ans = JOptionPane.showOptionDialog(app, video.getName() + " (" + (video.length() / 1024) + " KB) is saved in " + video.getParent() + "." + more
            + "\n\nPlay opens it in the browser with Play/Pause, Stop, Mute and volume.", "Video ready",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        try {
            if (ans == 0) java.awt.Desktop.getDesktop().browse(this.videoPage(video).toURI());
            else if (ans == 1) java.awt.Desktop.getDesktop().open(video);
        } catch (Exception ex) {
            app.setNow("Could not open a player for " + video.getName());
        }
    }

    /** A player page for `video` in the temp folder: the clip with Play/Pause, Stop, Mute and a volume slider. */
    File videoPage(File video) throws java.io.IOException {
        return this.videoPage(video, false);
    }

    /** As above, with Loop (ticked when `loop` says so), Zoom −, Zoom +, Fit (a drag moves a zoomed video) and Speed. */
    File videoPage(File video, boolean loop) throws java.io.IOException {
        return this.videoPage(video, loop, 100, 1, 1);
    }

    /** As above, starting at `volume` (0..100), `zoom` (1..4) and `speed` (the Media browser's last ones). */
    File videoPage(File video, boolean loop, int volume, double zoom, double speed) throws java.io.IOException {
        StringBuilder speeds = new StringBuilder();
        for (double v : MediaDir.SPEEDS) {
            speeds.append("<option value=\"").append(v).append('"').append(v == MediaDir.speed(speed) ? " selected" : "").append('>').append(MediaDir.speedLabel(v)).append("</option>");
        }
        File dir = new File(System.getProperty("java.io.tmpdir", "."), "pulsekit-player");
        if (!dir.isDirectory()) dir.mkdirs();
        String name = video.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        String src = video.toURI().toString().replace("\"", "%22");
        String html = "<!doctype html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "<title>" + name + "</title>\n<style>\n"
            + "body{margin:0;background:#111;color:#eee;font:15px sans-serif;display:flex;flex-direction:column;align-items:center;padding:16px}\n"
            + "#box{max-width:100%;max-height:70vh;overflow:hidden;border-radius:8px;background:#000;cursor:grab}\n"
            + "video{display:block;max-width:100%;max-height:70vh;background:#000;transform-origin:center}\n"
            + ".bar{display:flex;gap:8px;align-items:center;margin-top:12px;flex-wrap:wrap;justify-content:center}\n"
            + "button{background:#2a2a2a;color:#eee;border:1px solid #444;border-radius:16px;padding:7px 16px;font:inherit;cursor:pointer}\n"
            + "button.on{background:#c7f04b;color:#111;border-color:#c7f04b}\ninput[type=range]{width:200px}\n</style></head>\n<body>\n"
            + "<div>" + name + "</div>\n"
            + "<div id=\"box\"><video id=\"v\" src=\"" + src + "\" preload=\"auto\" playsinline" + (loop ? " loop" : "") + "></video></div>\n"
            + "<div class=\"bar\"><button id=\"play\" class=\"on\">Play</button><button id=\"stop\">Stop</button><button id=\"mute\">Mute</button>"
            + "<label><input id=\"loop\" type=\"checkbox\"" + (loop ? " checked" : "") + "> Loop</label>"
            + " <span id=\"time\">0:00 / 0:00</span></div>\n"
            + "<div class=\"bar\"><button id=\"zout\">Zoom &minus;</button><button id=\"zin\">Zoom +</button><button id=\"fit\">Fit</button> <span id=\"zoom\">100%</span>"
            + " &nbsp;Speed <select id=\"speed\">" + speeds + "</select></div>\n"
            + "<div class=\"bar\">Volume <input id=\"volume\" type=\"range\" min=\"0\" max=\"100\" value=\"" + volume + "\"> <span id=\"level\">" + volume + "%</span></div>\n"
            + "<script>\n"
            + "var v=document.getElementById('v'),play=document.getElementById('play'),mute=document.getElementById('mute'),vol=document.getElementById('volume'),level=document.getElementById('level');\n"
            + "function show(){play.textContent=v.paused?'Play':'Pause';mute.textContent=v.muted?'Unmute':'Mute';mute.className=v.muted?'on':'';level.textContent=v.muted?'muted':vol.value+'%';}\n"
            + "play.onclick=function(){if(v.paused)v.play();else v.pause();};\n"
            + "document.getElementById('stop').onclick=function(){v.pause();v.currentTime=0;};\n"
            + "mute.onclick=function(){v.muted=!v.muted;show();};\n"
            + "vol.oninput=function(){v.volume=vol.value/100;show();};\n"
            + "document.getElementById('loop').onchange=function(){v.loop=this.checked;};\n"
            + "var sp=document.getElementById('speed');sp.onchange=function(){v.playbackRate=v.defaultPlaybackRate=parseFloat(sp.value);};\n"
            + "v.volume=vol.value/100;v.playbackRate=v.defaultPlaybackRate=parseFloat(sp.value);\n"
            + "var steps=[1,1.25,1.5,2,2.5,3,4],z=" + MediaDir.clampZoom(zoom) + ",px=0,py=0,box=document.getElementById('box');\n"
            + "function paint(){if(z==1){px=0;py=0;}var mx=v.clientWidth*(z-1)/2,my=v.clientHeight*(z-1)/2;px=Math.max(-mx,Math.min(mx,px));py=Math.max(-my,Math.min(my,py));"
            + "v.style.transform='translate('+px+'px,'+py+'px) scale('+z+')';document.getElementById('zoom').textContent=Math.round(z*100)+'%';}\n"
            + "function step(up){var i;if(up){for(i=0;i<steps.length;i++)if(steps[i]>z+0.001){z=steps[i];break;}}else{var n=1;for(i=0;i<steps.length;i++)if(steps[i]<z-0.001)n=steps[i];z=n;}paint();}\n"
            + "document.getElementById('zin').onclick=function(){step(true);};document.getElementById('zout').onclick=function(){step(false);};\n"
            + "document.getElementById('fit').onclick=function(){z=1;paint();};\n"
            + "box.onwheel=function(e){e.preventDefault();step(e.deltaY<0);};\n"
            + "var dx=null,dy=0;box.onmousedown=function(e){dx=e.clientX-px;dy=e.clientY-py;};window.onmouseup=function(){dx=null;};\n"
            + "window.onmousemove=function(e){if(dx!==null&&z>1){px=e.clientX-dx;py=e.clientY-dy;paint();}};\n"
            + "function clock(t){t=Math.max(0,Math.floor(t||0));var s=t%60;return Math.floor(t/60)+':'+(s<10?'0':'')+s;}\n"
            + "function tick(){document.getElementById('time').textContent=clock(v.currentTime)+' / '+clock(isFinite(v.duration)?v.duration:0);}\n"
            + "v.ontimeupdate=v.ondurationchange=tick;\n"
            + "v.onloadedmetadata=function(){paint();tick();};\n"
            + "v.onplay=v.onpause=v.onended=show;\nshow();paint();\n</script>\n</body></html>\n";
        File page = new File(dir, video.getName().replaceAll("[^A-Za-z0-9._-]", "_") + ".html");
        Files.write(page.toPath(), html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return page;
    }

    /**
     * After a run that made an audio file: Make drum MIDI (the file becomes the audio input and
     * DrumMidi_CRT.jar runs on it, importing the drums as a file set), Play (the system player), or Close.
     */
    void offerAudio(File audio) {
        Object[] options = new Object[] {"Make drum MIDI", "Play", "Close"};
        int ans = JOptionPane.showOptionDialog(app, audio.getName() + " is saved in " + audio.getParent() + ".\n\n"
            + "Make drum MIDI runs DrumMidi_CRT on it and imports the drums as a file set.", "Audio ready",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (ans == 0) {
            // DrumMidi_CRT.jar carries an MP3 decoder, so the audio is given as it is.
            this.audioInputPath = audio.getAbsolutePath();
            app.programMenus.selectListedProgram("Java", "DrumMidi_CRT.jar");
            this.runPython();
        } else if (ans == 1) {
            try {
                java.awt.Desktop.getDesktop().open(audio);
            } catch (Exception ex) {
                app.setNow("Could not open a player for " + audio.getName());
            }
        }
    }

    /** An MP3 as a mono 16-bit WAV beside it (song.mp3 → song.wav); other files, or a failed decode, as they are. */
    File asWav(File audio) {
        if (audio == null || !audio.getName().toLowerCase().endsWith(".mp3")) return audio;
        try {
            AudioIo.Pcm pcm = Mp3Decode.parse(Files.readAllBytes(audio.toPath()));
            String name = audio.getName();
            File wav = new File(audio.getParentFile(), name.substring(0, name.length() - 4) + ".wav");
            Files.write(wav.toPath(), AudioIo.encodeWav(AudioIo.floatsToShorts(pcm.samples), pcm.sr));
            return wav;
        } catch (Exception ex) {
            app.setNow("Could not make a WAV from " + audio.getName() + "; using the MP3");
            return audio;
        }
    }

    /** Writes a program's file to the program files folder (else ~/Downloads, or ~/.pulsekit), never over an existing file. */
    File saveProgramFile(String name, byte[] data) {
        try {
            File home = new File(System.getProperty("user.home", "."));
            // The program files folder from Drum Midi Settings, else ~/Downloads (or ~/.pulsekit).
            File dir = ProgramFolder.get() != null ? new File(ProgramFolder.get()) : new File(home, "Downloads");
            if (ProgramFolder.get() == null && !dir.isDirectory()) dir = new File(home, ".pulsekit");
            if (!dir.isDirectory()) dir.mkdirs();
            String safe = name.replace('\\', '_').replace('/', '_');
            int dot = safe.lastIndexOf('.');
            String stem = dot > 0 ? safe.substring(0, dot) : safe;
            String ext = dot > 0 ? safe.substring(dot) : "";
            File out = new File(dir, safe);
            for (int n = 1; out.exists(); n++) out = new File(dir, stem + "(" + n + ")" + ext);
            Files.write(out.toPath(), data);
            return out;
        } catch (Exception ex) {
            return null;
        }
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
        this.pyRecent.addItem("Select program");
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
        // A bundled program (Programs/Java or Python) runs as the app ships it now, not the copy
        // saved when it last ran, so fixes reach programs picked from Recent.
        String bundled = binary ? null : ProgramFiles.bundledSource(item.name);
        String source = bundled != null ? bundled : item.source;
        byte[] data = binary ? item.bytes : (source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8));
        this.rememberProgram(item.name, data, binary, false);
        app.pyInputPath = null;
        this.showPyHint(PyJavHints.status(item.name, source, item.bytes));
        // The hint fills args from the program's usage; the recent item's own args win.
        if (this.pyExtra != null) this.pyExtra.setText(item.extra);
    }

    void showPyHint(String status) {
        this.pyHintPlain = status == null ? "" : status;
        if (app.pyInputPath == null && this.audioInputPath != null && new File(this.audioInputPath).isFile()) {
            app.pyInputPath = this.audioInputPath;
        }
        // The hint shows above; the output box is only for what a run writes.
        if (app.pyLog != null) app.pyLog.setText("Output appears here.");
        if (this.pyHint != null) this.pyHint.setText(hintHtml(this.pyHintPlain));
        this.pyInputToken = PyJavHints.firstInput(this.pyHintPlain);
        File outDir = new File(System.getProperty("user.home", "."), ".pulsekit");
        if (!outDir.isDirectory()) outDir.mkdirs();
        String out = PyJavHints.outputFile(app.pyInputPath, this.pyHintPlain, outDir.getAbsolutePath());
        // A new program starts from its own usage, not the last program's args (DrumMidi's output.mid).
        String filled = PyJavHints.fillArgs("", this.pyHintPlain, this.pyInputToken, app.pyInputPath, outDir.getAbsolutePath());
        if (this.pyExtra != null) this.pyExtra.setText(filled);
        if (out.length() > 0) {
            this.pyOutputPath = out;
            String note = this.pyHintPlain + "\nOutput file: " + out + "\nExtra args: " + filled;
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
        // Most programs (DrumMidi_CRT) read WAV only: an MP3 is given as a WAV made beside it.
        file = this.asWav(file);
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

    /** The loaded program's text, for its parameters: the listed program, the editor, or a .class/.jar's strings. */
    String programText() {
        String listed = app.programMenus.listedSourceToRun();
        String src = listed != null ? listed : (app.pyEditor != null ? app.pyEditor.getText() : "");
        return PyJavHints.programText(app.pyName, src, app.pyBytes);
    }

    /**
     * Params: the loaded program's parameters, read from its Usage line or argparse calls. A .wav,
     * .mp3 or .mid parameter gets a file button, anything else a text field. Switches are saved per
     * program; with Reset to defaults, and Reset to suggested values when the program has them.
     */
    /** True (after saying so) when a Sogni program must wait for the database operations still pending. */
    boolean mustWait(String program, boolean running) {
        int n = PendingOps.count();
        if (n == 0 || !PendingOps.guarded(program, running)) return false;
        app.setNow(PendingOps.waitText(n));
        if (app.pyLog != null) app.pyLog.setText(PendingOps.waitText(n));
        return true;
    }

    void openParams() {
        final String program = app.pyName == null || app.pyName.isEmpty() ? "program" : app.pyName;
        if (this.mustWait(program, false)) return;
        final java.util.List<ProgramParams.Param> ps = ProgramParams.parse(this.programText());
        if (ps.isEmpty()) {
            JOptionPane.showMessageDialog(app, "No parameters found in " + program + ". A line such as\n"
                + "Usage: java Prog <input.wav> [song.mid] [--level N]\nin the program lists them here.",
                "Parameters \u00b7 " + program, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        final String extra = this.pyExtra != null ? this.pyExtra.getText() : "";
        final String[] values = ProgramParams.values(ps, this.loadParams(program), extra);
        for (int i = 0; i < ps.size(); i++) {
            ProgramParams.Param p = ps.get(i);
            if (p.flag || p.output) continue;
            String input = app.pyInputPath;
            if (values[i].length() == 0 && input != null && ProgramParams.isAudio(p)
                && input.toLowerCase().matches(".*\\.(wav|wave|mp3)$")) values[i] = input;
            break;
        }
        final String[] before = values.clone();
        final JTextField[] fields = new JTextField[ps.size()];
        // A prompt or system text: a wrapping area of a few lines that scrolls.
        final javax.swing.JTextArea[] areas = new javax.swing.JTextArea[ps.size()];
        // On/off switches (DrumMidi's --no-hpss and --no-cymbals) are checkboxes.
        final javax.swing.JCheckBox[] checks = new javax.swing.JCheckBox[ps.size()];
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        for (int i = 0; i < ps.size(); i++) {
            final ProgramParams.Param p = ps.get(i);
            final int index = i;
            form.add(new JLabel(p.flag ? p.label + "  " + p.token : p.label + (p.optional ? "  (optional)" : "")));
            if (p.output && !p.optional) {
                form.add(new JLabel("Named by PyJav from the input file"));
                continue;
            }
            if (p.flag && !p.takesValue) {
                javax.swing.JCheckBox check = new javax.swing.JCheckBox("On", DrumMidiArgs.on(values[i]));
                check.setName("params-check:" + p.token);
                checks[i] = check;
                form.add(check);
                continue;
            }
            if (p.dir) {
                // A folder (MediaBrowser's directory): Browse picks it and opens the Media browser on it.
                JPanel row = new JPanel(new BorderLayout(6, 0));
                // Empty: the folder the Media browser last opened.
                if (values[i].length() == 0) {
                    MediaBrowser.loadLoop();
                    values[i] = MediaDir.lastRoot;
                }
                final JLabel chosen = new JLabel(MediaDir.label(values[i]));
                chosen.setName("params-chosen:" + p.token);
                JButton browse = new JButton("Browse");
                browse.setName("params-dir:" + p.token);
                browse.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser(values[index].length() > 0 ? new File(values[index]) : null);
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle("Directory");
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
                    File picked = chooser.getSelectedFile();
                    values[index] = picked.getAbsolutePath();
                    chosen.setText(MediaDir.label(values[index]));
                    app.mediaBrowser.open(picked);
                });
                row.add(browse, BorderLayout.WEST);
                row.add(chosen, BorderLayout.CENTER);
                form.add(row);
                continue;
            }
            if (p.isFile()) {
                JPanel row = new JPanel(new BorderLayout(6, 0));
                final JLabel chosen = new JLabel(values[i].length() == 0 ? "None" : new File(values[i]).getName());
                JButton pick = new JButton("any".equals(p.ext) ? "Choose file" : "Choose ." + p.ext);
                pick.setName("params-file:" + p.token);
                pick.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser(values[index].length() > 0 ? new File(values[index]).getParentFile() : null);
                    String[] exts = "mid".equals(p.ext) ? new String[] {"mid", "midi"} : "wav".equals(p.ext) ? new String[] {"wav", "wave"} : new String[] {p.ext};
                    if (!"any".equals(p.ext)) chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("." + p.ext + " files", exts));
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
                    values[index] = chooser.getSelectedFile().getAbsolutePath();
                    chosen.setText(chooser.getSelectedFile().getName());
                });
                if (p.newChat) {
                    // SogniChat: New chat leaves the saved chat out, so the next run starts afresh.
                    JButton fresh = new JButton("New chat");
                    fresh.setName("params-newchat:" + p.token);
                    fresh.addActionListener(e -> {
                        values[index] = "";
                        chosen.setText("None");
                    });
                    JPanel picks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                    picks.add(pick);
                    picks.add(fresh);
                    row.add(picks, BorderLayout.WEST);
                } else if (p.refs) {
                    // A file can also come from the prompt library (its reference or result files); greyed when it has none.
                    // An audio row lists only sound files (DrumMidi's input also MIDI files, played with the kit to a WAV),
                    // a MIDI row only MIDI files.
                    final boolean midiAsAudio = p.midiAsAudio;
                    final String only = midiAsAudio ? PromptDb.SOUNDS_OR_MIDIS
                        : p.join || "mp4".equals(p.ext) ? PromptDb.VIDEOS
                        : ProgramParams.isAudio(p) ? PromptDb.SOUNDS : "mid".equals(p.ext) ? PromptDb.MIDIS : null;
                    JButton db = new JButton("Browse DB");
                    db.setName("params-db:" + p.token);
                    db.setEnabled(!PromptDb.allFiles(only).isEmpty());
                    db.addActionListener(e -> app.promptDb.browse(only, (picked, file) -> {
                        if (midiAsAudio) {
                            app.promptDb.useAsAudio(picked, file, values, index, chosen);
                        } else {
                            values[index] = file.getAbsolutePath();
                            chosen.setText(picked + " \u00b7 from DB");
                        }
                    }));
                    JPanel picks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                    picks.add(pick);
                    picks.add(db);
                    row.add(picks, BorderLayout.WEST);
                } else {
                    row.add(pick, BorderLayout.WEST);
                }
                row.add(chosen, BorderLayout.CENTER);
                if ("mid".equals(p.ext)) {
                    // DrumMidi's MIDI is kept with the file set it made (source.mid), not as a file to browse to.
                    JButton fromSet = new JButton("From file set");
                    fromSet.setName("params-fileset:" + p.token);
                    fromSet.addActionListener(e -> {
                        String path = this.pickFileSetMidi();
                        if (path == null) return;
                        values[index] = path;
                        chosen.setText(new File(path).getName());
                    });
                    row.add(fromSet, BorderLayout.EAST);
                }
                if (p.join) {
                    // SogniVideo's Join with this video: the file buttons work only when it is ticked;
                    // unticked leaves the video out. PyJav unticks it after every run.
                    final javax.swing.JCheckBox join = new javax.swing.JCheckBox("Join with this video", values[i].length() > 0);
                    join.setName("params-join:" + p.token);
                    final List<JButton> buttons = new java.util.ArrayList<JButton>();
                    collectButtons(row, buttons);
                    final boolean[] usable = new boolean[buttons.size()];
                    for (int b = 0; b < buttons.size(); b++) usable[b] = buttons.get(b).isEnabled();
                    final Runnable paint = () -> {
                        for (int b = 0; b < buttons.size(); b++) buttons.get(b).setEnabled(join.isSelected() && usable[b]);
                    };
                    join.addActionListener(e -> {
                        if (!join.isSelected()) {
                            values[index] = "";
                            chosen.setText("None");
                        }
                        paint.run();
                    });
                    paint.run();
                    // The checkbox takes the place of the row's label.
                    form.remove(form.getComponentCount() - 1);
                    form.add(join);
                    JPanel holder = new JPanel(new BorderLayout());
                    holder.add(row, BorderLayout.CENTER);
                    form.add(holder);
                    continue;
                }
                form.add(row);
                continue;
            }
            if (ProgramParams.longText(p)) {
                javax.swing.JTextArea area = new javax.swing.JTextArea(values[i], 3, 24);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                area.setName("params-field:" + p.token);
                if (p.hint.length() > 0) area.setToolTipText(p.hint);
                // Right click or a long press: Select all, Cut, Copy, Paste (as on the phone).
                TextMenu.attach(area);
                areas[i] = area;
                if (ProgramParams.promptFromFile(p)) {
                    // The prompt from a text file (an Answer Prompt 1.txt made by Extract prompt): Select file or Browse DB.
                    JPanel holder = new JPanel(new BorderLayout(0, 4));
                    holder.add(new javax.swing.JScrollPane(area), BorderLayout.CENTER);
                    JPanel picks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                    JButton select = new JButton("Select file");
                    select.setName("params-prompt-file:" + p.token);
                    select.addActionListener(e -> {
                        JFileChooser chooser = new JFileChooser();
                        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt", "text", "md", "prompt"));
                        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
                        this.promptFrom(area, chooser.getSelectedFile());
                    });
                    JButton db = new JButton("Browse DB");
                    db.setName("params-prompt-db:" + p.token);
                    db.setEnabled(!PromptDb.allFiles(PromptDb.TEXTS).isEmpty());
                    db.addActionListener(e -> {
                        // A prompt is a text file: Browse DB starts on T.
                        DbFilter.current = "T";
                        app.promptDb.browse(PromptDb.TEXTS, (picked, file) -> this.promptFrom(area, file));
                    });
                    picks.add(select);
                    picks.add(db);
                    holder.add(picks, BorderLayout.SOUTH);
                    form.add(holder);
                    continue;
                }
                form.add(new javax.swing.JScrollPane(area));
                continue;
            }
            JTextField field = new JTextField(values[i], 10);
            field.setName("params-field:" + p.token);
            if (p.hint.length() > 0) field.setToolTipText("Default: " + p.hint);
            TextMenu.attach(field);
            fields[i] = field;
            if (p.choices != null && p.choices.length > 0) {
                // A list to pick from (SogniMusic's --genre: Pulsekit's style database); typing still works.
                JPanel row = new JPanel(new BorderLayout(6, 0));
                JButton choose = new JButton("Choose");
                choose.setName("params-choose:" + p.token);
                choose.addActionListener(e -> {
                    int now = java.util.Arrays.asList(p.choiceValues != null ? p.choiceValues : p.choices).indexOf(field.getText().trim());
                    int picked = SearchList.choose(app, "Choose \u00b7 " + p.label, p.choices, now, now, "Choose");
                    if (picked >= 0) field.setText(p.choiceValue(picked));
                });
                row.add(field, BorderLayout.CENTER);
                row.add(choose, BorderLayout.EAST);
                form.add(row);
                continue;
            }
            form.add(field);
        }
        JPanel resets = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton defaults = new JButton("Reset to defaults");
        defaults.addActionListener(e -> {
            for (JTextField f : fields) if (f != null) f.setText("");
            for (javax.swing.JTextArea a : areas) if (a != null) a.setText("");
            for (javax.swing.JCheckBox c : checks) if (c != null) c.setSelected(false);
        });
        resets.add(defaults);
        if (ProgramParams.hasSuggested(ps)) {
            JButton suggested = new JButton("Reset to suggested values");
            suggested.addActionListener(e -> {
                for (int i = 0; i < fields.length; i++) if (fields[i] != null) fields[i].setText(ps.get(i).suggested);
                for (int i = 0; i < areas.length; i++) if (areas[i] != null) areas[i].setText(ps.get(i).suggested);
                for (int i = 0; i < checks.length; i++) if (checks[i] != null) checks[i].setSelected(DrumMidiArgs.on(ps.get(i).suggested));
            });
            resets.add(suggested);
        }
        JPanel box = new JPanel(new BorderLayout(0, 8));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        resets.setAlignmentX(0.0f);
        top.add(resets);
        JLabel note = new JLabel("<html><body style='width:380px'>" + ProgramParams.note(ps) + " Empty fields use the default shown when you point at them.</body></html>");
        note.setAlignmentX(0.0f);
        top.add(note);
        box.add(top, BorderLayout.NORTH);
        box.add(form, BorderLayout.CENTER);
        int ans = JOptionPane.showConfirmDialog(app, box, "Parameters \u00b7 " + program, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ans != JOptionPane.OK_OPTION) return;
        for (int i = 0; i < fields.length; i++) if (fields[i] != null) values[i] = fields[i].getText();
        for (int i = 0; i < areas.length; i++) if (areas[i] != null) values[i] = ProgramParams.oneLine(areas[i].getText());
        for (int i = 0; i < checks.length; i++) if (checks[i] != null) values[i] = checks[i].isSelected() ? "1" : "";
        this.saveParams(program, ProgramParams.build(ps, values));
        String input = null;
        boolean newInput = false;
        for (int i = 0; i < ps.size(); i++) {
            ProgramParams.Param p = ps.get(i);
            if (p.flag || p.output) continue;
            input = values[i];
            newInput = !values[i].equals(before[i]);
            break;
        }
        String line = ProgramParams.line(ps, values, newInput ? "" : extra);
        if (newInput && input != null && input.length() > 0 && new File(input).isFile()) this.setInputFile(new File(input));
        if (this.pyExtra != null) this.pyExtra.setText(line);
        app.setNow("Params saved");
    }

    /** A file set's source MIDI, copied into ~/.pulsekit/pyjav-in for a program; null when none is picked. */
    String pickFileSetMidi() {
        java.util.LinkedHashMap<String, String> sets = HitCompare.fileSetsWithMidi(Engine.fileSetSources(app.learned, app.learnedFills));
        if (sets.isEmpty()) {
            JOptionPane.showMessageDialog(app, "No file set keeps a source MIDI yet. Run DrumMidi_CRT in PyJav: its MIDI is kept with the file set it makes.",
                "From file set", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        String[] labels = sets.keySet().toArray(new String[0]);
        Object pick = JOptionPane.showInputDialog(app, "Source MIDI from file set", "From file set",
            JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (pick == null) return null;
        try {
            File dir = new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            File out = new File(dir, ProgramParams.fileSetMidiFile(pick.toString()));
            Files.write(out.toPath(), HitCompare.fileSetMidi(sets.get(pick.toString())));
            return out.getAbsolutePath();
        } catch (Exception ex) {
            app.setNow("Could not copy that file set's MIDI");
            return null;
        }
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

    /** The opened prompt sheet as it was read (its name, category and reference file names), for the prompt library. */
    String promptSource = "";

    /**
     * An opened .prompt's reference files out of the prompt library (its final version, or the one
     * with its file names), into PyJav's input folder; the first is the run's input. Says which.
     */
    void loadPromptRefs(String text) {
        PromptDb.Refs refs = PromptDb.refsFor(text, app.pyName);
        if (refs.description.length() > 0) this.promptDescription = refs.description;
        if (refs.resultName.length() > 0 && (this.promptResult == null || this.promptResult.isEmpty())) this.promptResult = refs.resultName;
        if (refs.ref1Path.length() > 0 && (app.pyInputPath == null || app.pyInputPath.isEmpty())) app.pyInputPath = refs.ref1Path;
        if (app.pyEditor != null) {
            String now = app.pyEditor.getText();
            String next = PromptRun.withoutDescription(this.promptDescription, now);
            if (!now.equals(next)) app.pyEditor.setText(next);
        }
        if (app.pyLog != null) app.pyLog.setText(refs.note);
        if (this.pyHint != null) this.pyHint.setText(hintHtml(this.pyHintPlain + "\n" + refs.note));
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
                    this.promptSource = text;
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
