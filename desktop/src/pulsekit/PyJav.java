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
            argv = PythonRun.kitArgv(src, app.bpm(), app.style, app.bars, app.swingBar.getVal(), extra);
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
                int audios = 0;
                int texts = 0;
                java.util.Map<String, File> savedAudio = new java.util.HashMap<String, File>();
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
                    } else if (lower.matches(".*\\.(mp3|wav|flac|m4a|ogg|aac|prompt)$")) {
                        // Audio or a prompt sheet a program wrote in its work folder (SogniMusic's track and
                        // --saveprompt): kept in Downloads, as on Android.
                        File saved = this.saveProgramFile(f.name, f.bytes);
                        status.append(saved == null ? "Could not save " + f.name : "Saved " + saved.getPath()).append('\n');
                        if (saved != null) {
                            audios++;
                            savedAudio.put(f.name, saved);
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
                if (loaded) app.setNow("Script MIDI · " + name);
                // A run that made an audio file (SogniMusic's track): play it, or make drum MIDI from it.
                String made = PyJavHints.madeAudio(result.log);
                if (made != null && savedAudio.containsKey(made)) this.offerAudio(savedAudio.get(made));
            });
        }, "pulsekit-pyjav").start();
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

    /** Writes a program's file to ~/Downloads (or ~/.pulsekit), never over an existing file. */
    File saveProgramFile(String name, byte[] data) {
        try {
            File home = new File(System.getProperty("user.home", "."));
            File dir = new File(home, "Downloads");
            if (!dir.isDirectory()) dir = new File(home, ".pulsekit");
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
    void openParams() {
        final String program = app.pyName == null || app.pyName.isEmpty() ? "program" : app.pyName;
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
            if (p.isFile()) {
                JPanel row = new JPanel(new BorderLayout(6, 0));
                final JLabel chosen = new JLabel(values[i].length() == 0 ? "None" : new File(values[i]).getName());
                JButton pick = new JButton("Choose ." + p.ext);
                pick.setName("params-file:" + p.token);
                pick.addActionListener(e -> {
                    JFileChooser chooser = new JFileChooser(values[index].length() > 0 ? new File(values[index]).getParentFile() : null);
                    String[] exts = "mid".equals(p.ext) ? new String[] {"mid", "midi"} : "wav".equals(p.ext) ? new String[] {"wav", "wave"} : new String[] {p.ext};
                    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("." + p.ext + " files", exts));
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
                    values[index] = chooser.getSelectedFile().getAbsolutePath();
                    chosen.setText(chooser.getSelectedFile().getName());
                });
                row.add(pick, BorderLayout.WEST);
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
                form.add(row);
                continue;
            }
            JTextField field = new JTextField(values[i], 10);
            field.setName("params-field:" + p.token);
            if (p.hint.length() > 0) field.setToolTipText("Default: " + p.hint);
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
            for (javax.swing.JCheckBox c : checks) if (c != null) c.setSelected(false);
        });
        resets.add(defaults);
        if (ProgramParams.hasSuggested(ps)) {
            JButton suggested = new JButton("Reset to suggested values");
            suggested.addActionListener(e -> {
                for (int i = 0; i < fields.length; i++) if (fields[i] != null) fields[i].setText(ps.get(i).suggested);
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
