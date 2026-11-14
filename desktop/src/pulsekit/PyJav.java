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
        // JoinVideo --size, SogniVideo --join_size: VLC makes the videos one size first.
        JoinSizes.sizer = VideoRender.sizer();
        AudioMix.mp3 = Mp3Decode::parseStereo;
        AudioMix.aac = AudioMixerWindow::decodeAac;
        TrackRecorder.inputs = AudioMixerWindow.inputs();
        // SogniMusic, MidiDrumGen, DrumMidi_CRT --to_mixer: their results become mixer tracks.
        MixerOut.folder = AudioMixerWindow.folder();
        MixerOut.kit = () -> {
            if (app.playback == null) return null;
            app.playback.fillMissingVoices();
            return app.playback.mixVoices();
        };
        MixerOut.changed = () -> javax.swing.SwingUtilities.invokeLater(AudioMixerWindow::refresh);
    }

    /** The arguments: one line for the program, wrapped over a few on screen (many VST paths do not fit one). */
    javax.swing.JTextArea pyExtra;

    JButton pyRecent;

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

    JLabel pyEditorCaption;

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
        // Recent: each program once; a click loads it, a right click lists its runs (Run starts it).
        this.pyRecent = new JButton("Select program \u25be");
        this.pyRecent.setName("pyjav-recent");
        this.pyRecent.setBackground(ELEV);
        this.pyRecent.setForeground(FG);
        this.pyRecent.setHorizontalAlignment(JButton.LEFT);
        this.pyRecent.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        this.pyRecent.setAlignmentX(0.0f);
        this.reloadPyRecent(0);
        this.pyRecent.addActionListener(ev -> this.recentMenu().show(this.pyRecent, 0, this.pyRecent.getHeight()));
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
        // Params and Run apart: a click meant for Params no longer starts a run.
        controls.add(javax.swing.Box.createHorizontalStrut(28));
        this.pyRun = app.action("Run", HIT, BG, () -> this.runPython());
        controls.add(this.pyRun);
        // Return: back to what opened this program (the Media browser, for Upscale/resize image).
        this.pyReturn = app.action("Return to Media browser", ELEV, FG, () -> this.doReturn());
        this.pyReturn.setName("pyjav-return");
        this.pyReturn.setVisible(this.returnTo != null);
        controls.add(this.pyReturn);
        // Stop batch: BatchRun's runs end after the one in progress.
        this.pyBatchStop = app.action("Stop batch", ELEV, FG, () -> this.stopBatch());
        this.pyBatchStop.setName("pyjav-batch-stop");
        this.pyBatchStop.setVisible(BatchQueue.current() != null);
        controls.add(this.pyBatchStop);
        // Stop script: a Python script running now (vst_render.py ends after the file in progress, then tests and zips).
        this.pyScriptStop = app.action("Stop script", ELEV, FG, () -> this.stopScript());
        this.pyScriptStop.setName("pyjav-script-stop");
        this.pyScriptStop.setVisible(PythonRun.current != null);
        controls.add(this.pyScriptStop);
        // Arguments: a click shows or hides the args field. Hidden at first, as Params sets the args.
        final JButton extraLab = new JButton("Arguments \u25b8");
        extraLab.setName("pyjav-args-head");
        extraLab.setForeground(MUTED);
        extraLab.setContentAreaFilled(false);
        extraLab.setBorderPainted(false);
        extraLab.setFocusPainted(false);
        extraLab.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        // The arguments get a row of their own, as wide as the page.
        final JPanel argsRow = new JPanel(new BorderLayout());
        argsRow.setOpaque(false);
        argsRow.setAlignmentX(0.0f);
        argsRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        argsRow.setVisible(false);
        extraLab.addActionListener(e -> {
            boolean open = !this.pyExtra.isVisible();
            this.pyExtra.setVisible(open);
            argsRow.setVisible(open);
            extraLab.setText(open ? "Arguments \u25be" : "Arguments \u25b8");
            north.revalidate();
            north.repaint();
        });
        controls.add(extraLab);
        this.pyExtra = new javax.swing.JTextArea(1, 22);
        this.pyExtra.setLineWrap(true);
        this.pyExtra.setWrapStyleWord(true);
        this.pyExtra.setFont(javax.swing.UIManager.getFont("TextField.font"));
        // Still one line for the program: Enter adds no line, a pasted one becomes a space.
        this.pyExtra.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "none");
        ((javax.swing.text.AbstractDocument) this.pyExtra.getDocument()).setDocumentFilter(new javax.swing.text.DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, javax.swing.text.AttributeSet attr) throws javax.swing.text.BadLocationException {
                super.insertString(fb, offset, text == null ? null : text.replaceAll("[\\r\\n]+", " "), attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs) throws javax.swing.text.BadLocationException {
                super.replace(fb, offset, length, text == null ? null : text.replaceAll("[\\r\\n]+", " "), attrs);
            }
        });
        this.pyExtra.setVisible(false);
        this.pyExtra.setBackground(ELEV);
        this.pyExtra.setForeground(FG);
        this.pyExtra.setCaretColor(FG);
        this.pyExtra.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        this.pyExtra.setToolTipText("Optional extra command-line flags, e.g. --out groove.mid");
        // Three lines shown; more scroll.
        JScrollPane extraScroll = new JScrollPane(this.pyExtra, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        extraScroll.setName("pyjav-args-scroll");
        extraScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        int lineHeight = this.pyExtra.getFontMetrics(this.pyExtra.getFont()).getHeight();
        extraScroll.setPreferredSize(new Dimension(200, ARGS_LINES * lineHeight + 14));
        argsRow.add(extraScroll, BorderLayout.CENTER);
        north.add(controls);
        argsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, extraScroll.getPreferredSize().height + 6));
        north.add(argsRow);
        app.pyEditor = new JTextArea(PythonRun.defaultScript());
        app.codeSave.watch(app.pyEditor);
        app.pyEditor.setFont(new Font(Font.MONOSPACED, 0, 12));
        app.pyEditor.setBackground(ELEV);
        app.pyEditor.setForeground(FG);
        app.pyEditor.setCaretColor(FG);
        app.pyEditor.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        // Code's menu: Select all, Cut, Copy, Paste, Open in editor window, Save text as.
        TextMenu.attach(app.pyEditor, menu -> {
            menu.add(this.menuItem("Open in editor window", "code-editor", () -> this.openInEditor(true)));
            menu.add(this.menuItem("Save text as\u2026", "code-save-as", () -> SaveText.save(app, this.codeName(), app.pyEditor.getText())));
        });
        JScrollPane scroll = new JScrollPane(app.pyEditor);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        app.pyLog = new JTextArea(6, 40);
        // Output's menu: Select all and Copy, Open in editor window, Save text as (and a run's results file).
        TextMenu.attach(app.pyLog, menu -> {
            menu.add(this.menuItem("Open in editor window", "output-editor", () -> this.openInEditor(false)));
            String results = this.runLog != null && this.runLog.equals(app.pyLog.getText()) ? PyJavHints.resultsFileName(app.pyName) : null;
            if (results != null) menu.add(this.menuItem("Save as " + results, "output-save-results", () -> SaveText.save(app, results, app.pyLog.getText())));
            menu.add(this.menuItem("Save text as\u2026", "output-save-as", () -> SaveText.save(app, this.outputName(), app.pyLog.getText())));
        });
        app.pyLog.setEditable(false);
        // Output is read, not edited (its newest lines last): Home and End go to its start and its end.
        app.pyLog.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_HOME, 0), javax.swing.text.DefaultEditorKit.beginAction);
        app.pyLog.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_END, 0), javax.swing.text.DefaultEditorKit.endAction);
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
        // The editor and the output each under a headline (Code or Prompt, Output), with room between.
        this.pyEditorCaption = new JLabel("Code");
        this.pyEditorCaption.setForeground(FG);
        this.pyEditorCaption.setFont(this.pyEditorCaption.getFont().deriveFont(Font.BOLD));
        this.pyEditorCaption.setBorder(BorderFactory.createEmptyBorder(10, 2, 4, 0));
        this.pyEditorCaption.setName("pyjav-editor-caption");
        JPanel editorBox = new JPanel(new BorderLayout());
        editorBox.setOpaque(false);
        editorBox.add((Component)this.pyEditorCaption, "North");
        editorBox.add((Component)scroll, "Center");
        JLabel outputHead = new JLabel("Output");
        outputHead.setForeground(FG);
        outputHead.setFont(outputHead.getFont().deriveFont(Font.BOLD));
        outputHead.setBorder(BorderFactory.createEmptyBorder(14, 2, 4, 0));
        outputHead.setName("pyjav-output-head");
        JPanel outputBox = new JPanel(new BorderLayout());
        outputBox.setOpaque(false);
        // A Sogni run's workflow id, copied with one click as soon as the run prints it.
        this.copyWorkflow = new JButton("Copy workflow id");
        this.copyWorkflow.setName("pyjav-copy-workflow");
        this.copyWorkflow.setVisible(false);
        this.copyWorkflow.addActionListener(e -> this.copyWorkflowId());
        JPanel outTop = new JPanel(new BorderLayout());
        outTop.setOpaque(false);
        outTop.add(outputHead, BorderLayout.WEST);
        // The run's state beside the headline: "Running… 10:05 elapsed", then how it ended.
        this.pyStatus = new JLabel(" ");
        this.pyStatus.setName("pyjav-status");
        this.pyStatus.setForeground(MUTED);
        this.pyStatus.setBorder(BorderFactory.createEmptyBorder(14, 12, 4, 0));
        outTop.add(this.pyStatus, BorderLayout.CENTER);
        outTop.add(this.copyWorkflow, BorderLayout.EAST);
        outputBox.add((Component)outTop, "North");
        outputBox.add((Component)logScroll, "Center");
        jPanel.add((Component)north, "North");
        // Code and Output share the rest; the line between them is dragged to give either more room.
        this.pySplit = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, editorBox, outputBox);
        this.pySplit.setName("pyjav-split");
        this.pySplit.setOpaque(false);
        this.pySplit.setBorder(BorderFactory.createEmptyBorder());
        this.pySplit.setContinuousLayout(true);
        this.pySplit.setResizeWeight(0.65);
        // Home and End are the text's keys, never the divider's (a split pane moves it with them).
        for (int key : new int[] {java.awt.event.KeyEvent.VK_HOME, java.awt.event.KeyEvent.VK_END})
            this.pySplit.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke(key, 0), "none");
        this.pySplit.setDividerSize(8);
        editorBox.setMinimumSize(new Dimension(50, 60));
        outputBox.setMinimumSize(new Dimension(50, 60));
        jPanel.add((Component)this.pySplit, "Center");
        // The program run last (Recent's first) is open when PyJav starts, once the window is built.
        SwingUtilities.invokeLater(this::openLastProgram);
        return jPanel;
    }

    /** The program shown when PyJav starts: the one run last, with its parameters (not run). */
    String lastOpened;

    /**
     * PyJav starts with the program run last (Recent's first) and its parameters, not run, unless a
     * program was opened already (a file given to the app).
     */
    void openLastProgram() {
        if (!"drum_midi.py".equals(app.pyName)) return;
        this.reloadPyRecent(0);
        if (this.pyRecentItems == null || this.pyRecentItems.isEmpty()) return;
        PyJavRecent.Item last = this.pyRecentItems.get(0);
        this.applyPyRecent(last);
        this.lastOpened = last.name;
    }

    /** Code above, Output below: the divider between them is dragged. */
    javax.swing.JSplitPane pySplit;

    /** Recent prompts' menu shown last (for the tests). */
    static javax.swing.JPopupMenu lastRecent;

    /** Copy workflow id: shown once the run going on (or the last one) has printed "Workflow: <id>". */
    JButton copyWorkflow;
    String workflowId;

    /** The workflow id the run printed, on the button (null: hidden). */
    void showWorkflow(String id) {
        this.workflowId = id;
        if (this.copyWorkflow == null) return;
        this.copyWorkflow.setVisible(id != null);
        if (id != null) this.copyWorkflow.setText("Copy workflow id (" + (id.length() > 14 ? id.substring(0, 14) + "\u2026" : id) + ")");
    }

    /** The workflow id onto the clipboard. */
    void copyWorkflowId() {
        if (this.workflowId == null) return;
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(this.workflowId), null);
            // In Params' --workflow list too (when it is not there yet).
            SogniHistory.started(this.workflowId, app.pyName, System.currentTimeMillis());
            app.setNow("Copied workflow id " + this.workflowId);
        } catch (Exception ex) {
            app.setNow("Workflow id: " + this.workflowId);
        }
    }

    void browsePyFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Python, Java, or prompt");
        chooser.setFileFilter(new FileNameExtensionFilter("Python, Java, JavaScript, prompt, or playlist", "py", "java", "class", "jar", "js", "mjs", "cjs", "ts", "tsx", "prompt", "pls"));
        if (chooser.showOpenDialog(app) != 0) return;
        File file = chooser.getSelectedFile();
        if (file == null) return;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String name = file.getName();
            String low = name.toLowerCase();
            boolean binary = low.endsWith(".jar") || low.endsWith(".class");
            if (!(low.endsWith(".py") || low.endsWith(".java") || low.endsWith(".prompt") || low.endsWith(".pls") || low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".cjs") || low.endsWith(".ts") || low.endsWith(".tsx") || binary)) {
                if (app.pyLog != null) app.pyLog.setText("Pick a .py, .java, .js, .ts, .class, .jar, .prompt, or .pls file.");
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
                : low.endsWith(".pls") ? PLS_HINT
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
        if (this.pyEditorCaption != null) this.pyEditorCaption.setText(prompt ? "Prompt" : "Code");
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

    /** A playlist opened into the code area: Run plays it. */
    static final String PLS_HINT = "A playlist: Run plays it with PlayList.java (the Playlist window, Editor and Viewer).";

    /** The last playlist Run handed to PlayList.java, for the tests. */
    JavaRun.Result lastPlsRun;

    /**
     * A .pls in the code area run: the text (as edited) handed to PlayList.java; what it wrote kept
     * with the playlists and opened in the Playlist window.
     */
    void runPls(final String name, final String text) {
        if (app.pyLog != null) app.pyLog.setText("Running\u2026");
        app.setNow("PlayList.java: Running\u2026");
        new Thread(() -> {
            JavaRun.Result r;
            try {
                File in = new File(new File(PromptDb.dir(), "pyjav-in"), new File(name).getName());
                in.getParentFile().mkdirs();
                Files.write(in.toPath(), text.getBytes(StandardCharsets.UTF_8));
                java.util.List<String> argv = new java.util.ArrayList<String>();
                argv.add(in.getAbsolutePath());
                r = JavaRun.run("PlayList.java", new String(ProgramFiles.read("Java", "PlayList.java"), StandardCharsets.UTF_8), null, argv);
            } catch (Exception ex) {
                r = new JavaRun.Result("Failed: " + ex.getMessage(), new java.util.ArrayList<JavaRun.FileOut>(), 1);
            }
            final JavaRun.Result done = r;
            SwingUtilities.invokeLater(() -> {
                this.lastPlsRun = done;
                if (app.pyLog != null) app.pyLog.setText(done.log);
                String list = PlaylistWindow.kept(app, done);
                if (list != null) PlaylistWindow.open(app, list);
            });
        }, "pulsekit-pls").start();
    }

    private javax.swing.JMenuItem menuItem(String label, String name, Runnable click) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        item.setName(name);
        item.addActionListener(e -> click.run());
        return item;
    }

    /** The Code area's file name (the program's, or script.py). */
    String codeName() {
        return app.pyName == null || app.pyName.isEmpty() ? "script.py" : app.pyName;
    }

    /** The Output's file name to offer: "SogniVideo-output.txt". */
    String outputName() {
        return this.codeName().replaceAll("\\.[A-Za-z0-9]+$", "") + "-output.txt";
    }

    /** Open in editor window: the Output's text, or the Code's (which Back to Code puts back). */
    void openInEditor(boolean code) {
        if (code) {
            TextEditorWindow.open(app, this.codeName() + " \u2014 Pulsekit editor", this.codeName(), app.pyEditor.getText(), edited -> app.pyEditor.setText(edited));
        } else {
            TextEditorWindow.open(app, this.outputName() + " \u2014 Pulsekit editor", this.outputName(), app.pyLog.getText(), null);
        }
    }

    /** The Install question shown last (for the tests). */
    static String lastPipQuestion;

    /**
     * Install for a script that stopped for a library that is not installed: the Python Pulsekit
     * runs is named (pip in a command window may install into another), and Install runs
     * python -m pip install for that Python, then the script again.
     */
    /** The Pythons offerPip looks through (the tests give their own); null: those on this computer. */
    static java.util.function.Supplier<java.util.List<PythonRun.Found>> pythonsFound;

    void offerPip(final java.util.List<String> packages) {
        new Thread(() -> {
            final String python = PythonRun.whichPython();
            // Another Python on the computer may have them already (pip in a command window installs into its own).
            PythonRun.Found other = null;
            java.util.List<PythonRun.Found> all = pythonsFound != null ? pythonsFound.get() : PythonRun.found();
            for (PythonRun.Found f : all) {
                if (f.path.equalsIgnoreCase(python)) continue;
                if (PythonRun.hasPackages(f.path, packages)) {
                    other = f;
                    break;
                }
            }
            String version = "";
            for (PythonRun.Found f : all) if (f.path.equalsIgnoreCase(python)) version = f.version;
            final PythonRun.Found useIt = other;
            final String pythonVersion = version;
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (useIt != null) {
                    lastPipQuestion = PipMissing.otherHasIt(packages, python, useIt.label());
                    Object[] options = {"Use that Python", "Install here", "No"};
                    int answer = javax.swing.JOptionPane.showOptionDialog(app, lastPipQuestion, "Python for scripts",
                        javax.swing.JOptionPane.YES_NO_CANCEL_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                    if (answer == 0) {
                        AppSettings.setPythonPath(useIt.path);
                        app.setNow("Scripts run with " + useIt.label() + ": running again");
                        this.runPython();
                        return;
                    }
                    if (answer != 1) {
                        app.setNow("Not installed: " + String.join(", ", packages) + " (General settings > " + AppSettings.PYTHON + " picks another Python)");
                        return;
                    }
                    this.pipInstall(packages, python, pythonVersion);
                    return;
                }
                lastPipQuestion = PipMissing.question(packages, python);
                Object[] options = {"Install", "No"};
                int answer = javax.swing.JOptionPane.showOptionDialog(app, lastPipQuestion, "Install " + String.join(", ", packages) + "?",
                    javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                if (answer != 0) {
                    app.setNow("Not installed: " + String.join(", ", packages) + " (python -m pip install " + String.join(" ", packages) + " with " + python + ")");
                    return;
                }
                this.pipInstall(packages, python, pythonVersion);
            });
        }, "python-which").start();
    }

    /** python -m pip install for the Python Pulsekit runs, then the script again; a package with none for this Python is said so. */
    void pipInstall(final java.util.List<String> packages, final String python, final String version) {
        if (this.pyRun != null) this.pyRun.setEnabled(false);
        app.setNow("Installing " + String.join(", ", packages) + " for " + python + "\u2026");
        if (app.pyLog != null) app.pyLog.append("\n\nInstalling " + String.join(", ", packages) + " for " + python + "\u2026\n");
        final StringBuilder log = new StringBuilder();
        new Thread(() -> {
            boolean ok = PythonRun.pipInstall(packages, line -> {
                synchronized (log) {
                    log.append(line).append('\n');
                }
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (app.pyLog != null) app.pyLog.append(line + "\n");
                });
            });
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (this.pyRun != null) this.pyRun.setEnabled(true);
                if (!ok) {
                    String said;
                    synchronized (log) {
                        said = log.toString();
                    }
                    if (PipMissing.noPackageFor(said)) {
                        String note = PipMissing.noPackageNote(packages, python, version);
                        app.setNow(note);
                        if (app.pyLog != null) app.pyLog.append("\n" + note + "\n");
                        javax.swing.JOptionPane.showMessageDialog(app, note, "No package for this Python", javax.swing.JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    app.setNow("pip could not install " + String.join(", ", packages) + ": see its output below");
                    return;
                }
                app.setNow("Installed " + String.join(", ", packages) + ": running again");
                this.runPython();
            });
        }, "pip-install").start();
    }

    void runPython() {
        if (app.pyEditor == null) return;
        final String listed = app.programMenus.listedSourceToRun();
        final String src = listed != null ? listed : app.pyEditor.getText();
        final String name = app.pyName == null || app.pyName.isEmpty() ? "drum_midi.py" : app.pyName;
        // A playlist: PlayList.java plays it.
        if (name.toLowerCase().endsWith(".pls")) {
            this.runPls(name, src);
            return;
        }
        // A batch's next run goes on without asking (the run before may still be storing).
        boolean batchStep = this.batchStep;
        this.batchStep = false;
        if (!batchStep && this.mustWait(name, true)) return;
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
        final java.util.List<ProgramParams.Param> runParams = ProgramParams.parse(PyJavHints.programText(name, src, bytes));
        final String field = this.pyExtra != null ? this.pyExtra.getText() : "";
        final String savedNow = this.loadParams(app.pyName);
        final String extra = ProgramParams.merge(runParams, field, savedNow);
        if ("vst_render.py".equals(name)) {
            VstLists.rememberKits(extra);
            VstLists.rememberPlugins(extra);
            // Pulsekit's map files where vst_render finds them (~/.pulsekit/maps): --test uses drum-kits.txt when --map is empty.
            VstLists.unpackMaps();
        }
        // Switches typed in the field (a --plugin by hand) go into the Params too: they stay for the next run.
        if (savedNow != null && ProgramParams.typed(runParams, field).length() > 0)
            this.saveParams(app.pyName, ProgramParams.build(runParams, ProgramParams.values(runParams, ProgramParams.typed(runParams, extra), "")));
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
        java.util.List<String> runArgs;
        if ((app.pyInputPath != null && app.pyInputPath.length() > 0) || outPath.length() > 0) {
            runArgs = PyJavHints.programArgs(filled, this.pyInputToken, app.pyInputPath, this.pyHintPlain, outDir.getAbsolutePath());
        } else {
            runArgs = PythonRun.kitArgv(src, app.bpm(), app.style, app.bars, app.swingBar.getVal(), extra, app.tsNum, app.tsDen);
        }
        // A step of Midi2Wav's test (CompareHits on the test's files): exactly these arguments.
        if (this.forcedArgs != null) {
            runArgs = new java.util.ArrayList<String>(this.forcedArgs);
            this.forcedArgs = null;
        }
        // The key file from Drum Midi Settings for every program that takes one (ImageUpscaler's runs have an input file).
        runArgs = JavaRun.withKeyFile(this.programText(), new java.util.ArrayList<String>(runArgs));
        // Midi2Wav without a SoundFont: Pulsekit's kit (the active drum set) as one.
        if (JavaRun.needsKit(name, runArgs)) {
            try {
                File kitDir = new File(PromptDb.dir(), "pyjav-in");
                kitDir.mkdirs();
                File kit = new File(kitDir, "Pulsekit-kit.sf2");
                Files.write(kit.toPath(), AudioIo.encodeSf2(app.playback.mixVoices(), "Pulsekit"));
                runArgs = JavaRun.withKit(runArgs, kit.getAbsolutePath());
            } catch (Exception ex) {
                app.setNow("Could not write Pulsekit's kit as a SoundFont: " + ex.getMessage());
            }
        }
        // Hydrogen songs and patterns, and Reaper projects, given to a program: their MIDI.
        runArgs = DrumFiles.midiArgs(runArgs, new File(PromptDb.dir(), "pyjav-in"));
        // MediaBrowser without a folder: the one the Media browser opened last.
        if ("MediaBrowser.java".equals(name)) {
            MediaBrowser.loadLoop();
            runArgs = MediaDir.withLastRoot(runArgs);
        }
        // A batch (MidiDrumGen --count, vst_render.py --dir, CompareHits --batch): a plain folder name is
        // in the program files folder, not the run's temporary one.
        runArgs = ProgramFolder.batchFolders(name, runArgs, this.programFolder());
        // vst_render.py --test, --mp3 (Wav2Mp3 being in it) and the cymbal model bench: Pulsekit's DrumMidi_CRT.jar and this Java for it.
        if ("vst_render.py".equals(name) && JavaRun.needsDrumMidi(runArgs)) {
            try {
                runArgs = JavaRun.withDrumMidi(name, runArgs, new File(new File(PromptDb.dir(), "pyjav-in"), "DrumMidi_CRT.jar"), ProgramFiles.read("Java", "DrumMidi_CRT.jar"));
            } catch (Exception ex) {
                app.setNow("Could not hand DrumMidi_CRT.jar to the test: " + ex.getMessage());
            }
        }
        // Params' Save this run into DB: taken off (the program never sees it), stored after the run.
        boolean saving = false;
        while (runArgs.remove(PromptKeep.SAVE_RUN)) saving = true;
        this.saveRun = saving;
        // Params' Destination subfolder: taken off too, kept with the run's download (Move puts its files there).
        final String subPattern = DownloadMove.takeSub(runArgs);
        final boolean savingRun = saving;
        // PlayList --nogui without a list writes the Default playlist: its file given.
        final java.util.List<String> argv = Playlist.withDefault(name, runArgs, PromptDb.dir());
        // SogniMusic's test without drums asked for: said first (DrumMidi would find nothing to check).
        String drums = Midi2WavTest.drumsWarning(name, argv);
        if (drums != null) {
            Object[] options = {"Run anyway", "Cancel"};
            int ans = JOptionPane.showOptionDialog(app, drums, "SogniMusic test", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (ans != 0) {
                app.setNow("Not run: SogniMusic's test needs drums");
                return;
            }
        }
        // A reconnect (the run timed out with its workflow still going on Sogni) carries on this run.
        final boolean reconnect = this.reconnectDownload != 0 || this.reconnectNote != null;
        if (!reconnect) this.reconnects = 0;
        final String note = this.reconnectNote;
        this.reconnectNote = null;
        if (app.pyLog != null) app.pyLog.setText(note == null ? "Running…" : note + "\nRunning…");
        if (this.pyRun != null) this.pyRun.setEnabled(false);
        // The run is pending (PendingOps) until what it stores in the prompt library is stored.
        PromptDb.dir();
        final String pending = PendingOps.guarded(name, false) ? PendingOps.add(name, PendingOps.files(argv), saving || PendingOps.storesInDb(argv)) : null;
        // File > Downloads: the Sogni run is in progress until its result is in.
        SogniDownloads.init(PromptDb.dir());
        final long download = this.reconnectDownload != 0 ? this.reconnectDownload
            : (pending != null || SogniDownloads.other(name)) && SogniDownloads.listed(name) ? SogniDownloads.started(name, System.currentTimeMillis()) : 0;
        this.reconnectDownload = 0;
        // Create again (File > Downloads): the arguments this run had.
        if (download != 0) SogniDownloads.args(download, filled);
        // The "Running…" line: the time it has run, and its latest news (queued, reconnecting, progress).
        final JavaRun.Live live = new JavaRun.Live();
        // Its workflow id and exact arguments kept with its download: Resume download if the app is closed.
        if (download != 0) {
            SogniDownloads.argv(download, argv);
            if (subPattern.length() > 0) SogniDownloads.subfolder(download, subPattern);
            live.download(download);
        }
        if (note != null) live.add(note + "\n");
        final String head = note == null ? "" : note + "\n";
        final javax.swing.Timer tick = new javax.swing.Timer(1000, null);
        final String[] lastLine = new String[] {app.pyLog == null ? null : app.pyLog.getText()};
        app.setNow(name + ": Running\u2026");
        final String[] lastTop = new String[] {name + ": Running\u2026"};
        // Kept by the program: shown again when it is picked after another (SogniPedit's Params looked at meanwhile).
        final Shown runShown = new Shown(live, head, lastLine, lastTop);
        RUNNING.put(name, runShown);
        this.showWorkflow(null);
        tick.addActionListener(ev -> {
            // Its workflow id, once printed: Copy workflow id.
            String wf = SogniDownloads.workflowIn(live.text());
            if (wf != null && !wf.equals(this.workflowId)) this.showWorkflow(wf);
            // DrumMidi without a hit for a while: asked whether to abort.
            String noHits = JavaRun.noHitsQuestion(name, live, System.currentTimeMillis());
            if (noHits != null) {
                live.askedAt = System.currentTimeMillis();
                tick.stop();
                Object[] options = {"Abort", "Keep waiting"};
                int ans = JOptionPane.showOptionDialog(app, noHits, name, JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
                if (ans == 0) {
                    live.abort();
                    app.setNow("Aborting " + name + "\u2026");
                    return;
                }
                live.askedAt = System.currentTimeMillis();
                tick.start();
            }
            // The top line shows the same time and news (while nothing else was put there).
            if (lastTop[0] != null && lastTop[0].equals(app.nowPlaying.getText())) {
                lastTop[0] = JavaRun.runningTop(name, live, System.currentTimeMillis());
                app.setNow(lastTop[0]);
            }
            if (this.pyStatus != null && name.equals(app.pyName)) this.pyStatus.setText(name + ": " + JavaRun.runningStatus(live, System.currentTimeMillis()));
            if (app.pyLog == null || lastLine[0] == null || !lastLine[0].equals(app.pyLog.getText())) return;
            // The lines printed so far, each with its time, the newest at the end (scrolled to).
            String tail = JavaRun.runningTail(live);
            String line = tail.length() == 0 ? "Running\u2026" : tail;
            if (!line.equals(app.pyLog.getText())) {
                app.pyLog.setText(line);
                app.pyLog.setCaretPosition(app.pyLog.getDocument().getLength());
            }
            lastLine[0] = line;
        });
        tick.start();
        new Thread(() -> {
            JavaRun.track(live);
            final boolean javaProg = !promptProg && isJavaName(name);
            java.util.List<String> runArgv = argv;
            if (promptProg) {
                runArgv = new java.util.ArrayList<String>(argv);
                runArgv.add("--pk-run");
                runArgv.add(this.promptRunMode);
                if (this.promptOutputInvented && outPath.length() > 0 && !runArgv.contains(outPath)) runArgv.add(outPath);
            }
            if (!promptProg && !javaProg && this.pyScriptStop != null) SwingUtilities.invokeLater(() -> this.pyScriptStop.setVisible(true));
            final long runStarted = System.currentTimeMillis();
            // SogniMusic --outdir: the run's tracks, WAVs and zip kept in that folder (% codes filled in, made when missing).
            String outdirArg = ProgramFolder.outdirArg(name, argv);
            this.runOutDir = outdirArg == null ? null : ProgramFolder.outdir(outdirArg, this.programFolder());
            if (this.runOutDir != null && !this.runOutDir.isDirectory()) this.runOutDir.mkdirs();
            // Where the run's files are kept: an output name's %n counts the files there too.
            System.setProperty("pulsekit.outdir", (this.runOutDir != null ? this.runOutDir : this.programFolder()).getAbsolutePath());
            final PythonRun.Result result = promptProg
                ? asPython(JavaRun.run(name, runSrc, bytes, runArgv))
                : javaProg
                    ? asPython(JavaRun.run(name, src, bytes, argv))
                    : PythonRun.run(src, name, argv);
            // File > Logs (Logging on): the run's arguments, exit code and output.
            Logs.run(name, promptProg ? runArgv : argv, result.code, result.log, runStarted);
            // vst_render's test: its test beat's hits into the drum sound library.
            if ("vst_render.py".equals(name)) DrumLibraryWindow.fromRun(app, result.log);
            javax.swing.SwingUtilities.invokeLater(() -> {
                tick.stop();
                RUNNING.remove(name, runShown);
                if (this.pyScriptStop != null) this.pyScriptStop.setVisible(false);
                if (this.pyRun != null) this.pyRun.setEnabled(true);
                // The library work below runs on this same thread, before anything else can start.
                PendingOps.done(pending);
                // Timed out (or the network went away) while the workflow goes on on Sogni: joined again.
                java.util.List<String> again = javaProg && this.reconnects < JavaRun.RECONNECTS ? JavaRun.reconnectArgs(name, src, result.log, argv) : null;
                if (again != null) {
                    this.reconnects++;
                    if (savingRun) again.add(PromptKeep.SAVE_RUN);
                    DownloadMove.giveSub(again, subPattern);
                    String why = reconnectWhy(result.log);
                    this.reconnectNote = "Reconnecting to workflow " + JavaRun.workflowOf(again) + " (" + why + "; try " + this.reconnects + " of " + JavaRun.RECONNECTS + ")\u2026";
                    if (download != 0) SogniDownloads.reconnecting(download, this.reconnectNote);
                    this.reconnectDownload = download;
                    this.forcedArgs = again;
                    app.setNow(this.reconnectNote);
                    this.runPython();
                    return;
                }
                long ended = System.currentTimeMillis();
                if (this.pyStatus != null && name.equals(app.pyName)) this.pyStatus.setText(name + ": " + JavaRun.endedStatus(live, result.code, ended));
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
                // On screen the lines have the times they came (the output file above has the text as it was).
                if (app.pyLog != null) app.pyLog.setText(JavaRun.stampedLog(live, shown, ended));
                // A library the script needs is not installed for the Python Pulsekit runs: install it for that one.
                if (!javaProg && !promptProg && result.code != 0) {
                    java.util.List<String> packages = PipMissing.missing(result.log);
                    if (!packages.isEmpty()) this.offerPip(packages);
                }
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
                    } else if (lower.endsWith(".prompt") && PromptSave.isSheetFile(new JavaRun.FileOut(f.name, f.bytes))) {
                        // A prompt sheet (--saveprompt) is saved after the run, once it is known where it goes
                        // (and not at all when it is the same as one in the library already): keepInLibrary.
                        continue;
                    } else if (lower.matches(".*\\.(mp3|wav|flac|m4a|ogg|aac|prompt|png|jpe?g|webp|gif|mp4|webm|mov|glb)$")) {
                        // Audio, a prompt sheet, a picture or a video a program wrote in its work folder (SogniMusic's
                        // track and --saveprompt, SogniChat's tool results): kept in Downloads, as on Android
                        // (SogniMusic --outdir: in its folder).
                        File saved = this.saveProgramFile(f.name, f.bytes, this.runOutDir);
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
                if (SogniDownloads.workflowIn(result.log) != null) {
                    this.showWorkflow(SogniDownloads.workflowIn(result.log));
                    // Params' --workflow list: completed when it downloaded, failed else.
                    SogniHistory.finished(SogniDownloads.workflowIn(result.log), result.log.indexOf("Succeeded") >= 0);
                    // The Sogni job ids its files carry, kept with the workflow (a file's menu: Sogni job id).
                    SogniHistory.jobs(result.log, SogniDownloads.workflowIn(result.log));
                }
                // A prompt sheet the run saved (--saveprompt) goes into the prompt library, with its pictures and result;
                // MidiDrumGen's MIDI and SogniMusic's track too, when Drum Midi Settings says so.
                // Pending (File > Downloads) while what the run stores is stored.
                String verdict = "";
                for (String line : (result.log == null ? "" : result.log).split("\n")) {
                    String t = line.trim();
                    if (t.startsWith("Succeeded") || t.startsWith("Failed")) verdict = t;
                }
                if (download != 0) SogniDownloads.result(download, verdict, status.toString(), null);
                // The top line's "Running…" ends with the run: its verdict, as the log under Run ends.
                if (lastTop[0] != null && lastTop[0].equals(app.nowPlaying.getText())) app.setNow(verdict.length() > 0 ? name + ": " + verdict : name + ": done");
                this.lastSheetVersion = 0;
                final boolean[] moveAfterReady = {false};
                String kept = this.keepInLibrary(name, result, argv);
                if (kept.length() > 0 && app.pyLog != null) {
                    app.pyLog.append("\n" + kept);
                    this.runLog = app.pyLog.getText();
                }
                if (download != 0) {
                    SogniDownloads.saved(download, kept, null);
                    SogniDownloads.stored(download, this.lastSheetVersion);
                    SogniDownloads.done(download);
                    // Another program's run that saved no file: off the list. Move downloaded media files to, automatically
                    // (a run whose Destination subfolder or file names have group, category or tag codes: after its ready dialogs).
                    if (!SogniDownloads.dropIfEmpty(download)) {
                        if (SogniDownloads.waitsForChoices(download)) moveAfterReady[0] = true;
                        else DownloadsScreen.finished(app);
                    }
                }
                if (loaded) app.setNow("Script MIDI · " + name);
                // A run of a batch (BatchRun): what it made stays on disk, not shown; the next run follows.
                final BatchQueue batch = BatchQueue.current();
                final boolean inBatch = batch != null && batch.takes(name) && !batch.joining;
                // A run that made an audio file (SogniMusic's track): play it, or make drum MIDI from it.
                String made = PyJavHints.madeAudio(result.log);
                // SogniMusic's track is also kept as a WAV (unless --no_wav), beside the MP3.
                File musicWav = null;
                if (result.code == 0 && made != null && savedAudio.get(made) != null && made.toLowerCase().endsWith(".mp3") && Midi2WavTest.musicToWav(name, argv)) {
                    File mp3 = savedAudio.get(made);
                    File w = this.asWav(mp3);
                    if (w != null && !w.equals(mp3)) {
                        musicWav = w;
                        this.testNote("Saved " + w.getPath());
                    }
                }
                // SogniMusic --zip: what the run made (and its WAV) gathered for one zip, written when its last step is over.
                if (result.code == 0 && RunZip.wanted(name, argv)) {
                    RunZip z = RunZip.start(result.log, argv);
                    z.dir = this.runOutDir;
                    this.zipAdd(z, name, "", result);
                    // The tracks saved in Downloads are the zip's: --zip_delete deletes them too.
                    for (File copy : savedAudio.values()) z.addCopy(copy.getPath());
                    if (musicWav != null) z.add(musicWav.getName(), musicWav);
                    if (!MusicCompare.wanted(name, argv)) this.zipFinish();
                }
                // Midi2Wav's and SogniMusic's tests go on (DrumMidi, CompareHits) instead of offering the audio.
                boolean testing = this.testStep(name, argv, verdict, madeMidi, musicWav != null ? musicWav : made == null ? null : savedAudio.get(made), result.log);
                // A comparison's DrumMidi run ended: the next track's, or the chain stops on a failure.
                if (MusicCompare.chainStep(name)) {
                    MusicCompare.Chain chain = MusicCompare.chain();
                    if (RunZip.current() != null) this.zipAdd(RunZip.current(), name, chain == null ? "" : chain.setName(chain.model()), result);
                    if (result.code == 0 && verdict.startsWith("Succeeded")) this.chainNext();
                    else {
                        MusicCompare.stopChain();
                        this.importHeld(chain);
                        this.testNote("File sets stopped: DrumMidi_CRT did not succeed");
                        this.zipFinish();
                    }
                }
                // SogniMusic --compare_models: its tracks measured and ranked, under the run's log.
                if (result.code == 0 && MusicCompare.wanted(name, argv)) this.compareModels(argv, savedAudio, result.log);
                if (!testing && !inBatch && made != null && savedAudio.containsKey(made)) this.offerAudio(savedAudio.get(made));
                // VideoFrames --upscale: the first frame goes on to ImageUpscaler (its Params open) instead of being shown.
                File frame = result.code == 0 && "VideoFrames.java".equals(name) && argv.contains("--upscale") ? this.firstFrame(savedPictures, argv, result.log) : null;
                if (frame != null) {
                    app.programMenus.selectListedProgram("Java", "ImageUpscaler.java");
                    this.setInputFile(frame);
                    this.openParams();
                }
                // Pictures it made (SogniChat's tool results) are shown.
                else if (!inBatch && !savedPictures.isEmpty() && result.code == 0) this.offerPictures(savedPictures);
                // A video it made (SogniVideo's clip, a SogniChat tool result) is offered for playing.
                if (!inBatch && !savedVideos.isEmpty() && result.code == 0) {
                    // A joined clip (SogniVideo's Join with this video) is the one to watch.
                    File watch = savedVideos.get(0);
                    // The run's own clip (its "Succeeded:" line): the first of its variations.
                    String named = null;
                    for (String line : String.valueOf(result.log).split("\n")) if (line.trim().startsWith("Succeeded: ")) named = line.trim().substring(11).trim();
                    for (File v : savedVideos) if (named != null && v.getName().equals(named)) watch = v;
                    for (File v : savedVideos) if (v.getName().toLowerCase().matches(".+-merged(\\(\\d+\\))?\\.mp4")) watch = v;
                    this.offerVideo(watch, savedVideos.size());
                }
                // The ready dialogs closed (the group, category and tags picked): the files named with their codes
                // renamed, and the move held for them made.
                if (download != 0 && moveAfterReady[0]) this.afterReady(download);
                // MidiDrumGen's groove is played with the kit's sounds, with Play / Stop.
                // With --towav or --test it goes on to Midi2Wav (and its test) instead.
                if (madeMidi != null && result.code == 0 && Midi2WavTest.fromGen(name, argv)) this.genToWav(madeMidi, argv);
                else if (madeMidi != null && result.code == 0 && "MidiDrumGen.java".equals(name) && !argv.contains("--count")) this.offerMidi(madeMidi.name, madeMidi.bytes);
                // WAV ready's Convert hits into MIDI: MIDI ready on what DrumMidi_CRT made.
                else if (madeMidi != null && result.code == 0 && "DrumMidi_CRT.jar".equals(name) && this.hitsAsked) this.offerMidi(madeMidi.name, madeMidi.bytes);
                this.hitsAsked = false;
                // MediaBrowser: its folder in the Media browser.
                String browse = result.code == 0 && "MediaBrowser.java".equals(name) ? MediaDir.opened(result.log) : null;
                if (browse != null) app.mediaBrowser.open(new File(browse));
                // PlayList: the playlist it wrote (or merged) kept with the playlists, and opened in the Playlist window.
                String list = "PlayList.java".equals(name) ? PlaylistWindow.kept(app, result) : null;
                if (list != null) PlaylistWindow.open(app, list);
                // Collections: the Collections viewer (on the collection it names).
                if (result.code == 0 && "Collections.java".equals(name)) {
                    final String coll = MediaCollections.opened(result.log);
                    SwingUtilities.invokeLater(() -> CollectionsWindow.show(app, coll));
                }
                // Any other failure with its workflow known: asked whether to resume it (no new run is paid for).
                java.util.List<String> resume = javaProg && !inBatch && result.log != null && result.log.indexOf("Failed") >= 0 ? JavaRun.resumeArgs(name, src, result.log, argv) : null;
                if (resume != null) {
                    if (savingRun) resume.add(PromptKeep.SAVE_RUN);
                    DownloadMove.giveSub(resume, subPattern);
                    Object[] options = {"Resume", "No"};
                    if (JOptionPane.showOptionDialog(app, JavaRun.resumeQuestion(result.log, resume), "Resume " + name + "?", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, options, options[0]) == 0) {
                        this.reconnectNote = "Resuming workflow " + JavaRun.workflowOf(resume) + "\u2026";
                        this.forcedArgs = resume;
                        app.setNow(this.reconnectNote);
                        this.runPython();
                        return;
                    }
                }
                // BatchRun's plan starts the batch; each run of it (and JoinVideo at the end) leads to the next step.
                if (batch != null && batch.takes(name)) {
                    java.util.List<String> paths = new java.util.ArrayList<String>();
                    for (File f : savedVideos) paths.add(f.getAbsolutePath());
                    for (File f : savedPictures) paths.add(f.getAbsolutePath());
                    batch.done(verdict, paths);
                    this.batchNext(batch);
                } else if (BatchQueue.start(name, argv, verdict, result.log) != null) {
                    this.batchNext(BatchQueue.current());
                }
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
            // General settings > Show images off: the picture is not shown (its name and right-click items still are).
            boolean hidden = !AppSettings.shows("I");
            try {
                img = hidden ? null : javax.imageio.ImageIO.read(f);
            } catch (Exception ignored) {
                img = null;
            }
            JLabel name = new JLabel(f.getName() + (img == null ? (hidden ? " (not shown: Show images is off; a right click has its items)" : " (no preview)") : ""));
            name.setAlignmentX(0.0f);
            col.add(name);
            // Reference, Result, Save to file: under the name, above the picture.
            JPanel saves = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            saves.setAlignmentX(0.0f);
            final File saveFrom = f;
            for (int k = 0; k < MediaDir.READY_SAVES.length; k++) {
                final String item = MediaDir.READY_SAVES[k];
                JButton b = new JButton(MediaDir.READY_SAVES_SHORT[k]);
                b.setName("ready-save:" + item + ":" + f.getName());
                b.setToolTipText(item);
                b.addActionListener(e -> this.saveReady(saveFrom, item));
                saves.add(b);
            }
            col.add(saves);
            if (hidden) {
                final File pic = f;
                name.setName("picture-offer:" + f.getName());
                GroupMenus.onRightClick(name, () -> GroupMenus.fileMenu(app, pic).show(name, 20, 20));
            }
            if (img == null) continue;
            double scale = Math.min(1.0, 480.0 / Math.max(img.getWidth(), img.getHeight()));
            java.awt.Image shown = img.getScaledInstance(Math.max(1, (int) (img.getWidth() * scale)), Math.max(1, (int) (img.getHeight() * scale)), java.awt.Image.SCALE_SMOOTH);
            JLabel view = new JLabel(new javax.swing.ImageIcon(shown));
            view.setName("picture-offer:" + f.getName());
            view.setToolTipText("Right click: Add to favourites, Add to group, Crop image");
            // Right click: Add to favourites, Add to group, Crop image.
            final File pic = f;
            GroupMenus.onRightClick(view, () -> {
                javax.swing.JPopupMenu m = GroupMenus.fileMenu(app, pic);
                javax.swing.JMenuItem crop = new javax.swing.JMenuItem(CropBox.CROP);
                crop.setName("picture-crop:" + pic.getName());
                crop.addActionListener(e -> this.cropReady(pic, view));
                m.add(crop);
                this.lastReadyMenu = m;
                m.show(view, 20, 20);
            });
            view.setAlignmentX(0.0f);
            col.add(view);
            col.add(javax.swing.Box.createVerticalStrut(8));
        }
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(col);
        scroll.setPreferredSize(new java.awt.Dimension(520, Math.min(560, col.getPreferredSize().height + 8)));
        // Process as a reference: the picture (the first, or the one picked) into another program as its Reference file 1 or 2.
        Object[] options = new Object[] {"Process as a reference", "Open", "Close"};
        int ans = JOptionPane.showOptionDialog(app, scroll, pictures.size() == 1 ? "Picture ready" : pictures.size() + " pictures ready",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[2]);
        if (ans == 0) {
            File pick = pictures.get(0);
            if (pictures.size() > 1) {
                String[] names = new String[pictures.size()];
                for (int i = 0; i < names.length; i++) names[i] = pictures.get(i).getName();
                Object chosen = JOptionPane.showInputDialog(app, "Which picture?", "Process as a reference", JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
                if (chosen == null) return;
                for (File f : pictures) if (f.getName().equals(chosen)) pick = f;
            }
            ProcessRefDialog.open(app, pick);
        } else if (ans == 1) {
            try {
                java.awt.Desktop.getDesktop().open(pictures.get(0));
            } catch (Exception ex) {
                app.setNow("Could not open a viewer for " + pictures.get(0).getName());
            }
        }
    }

    /**
     * After a run's ready dialogs (its files' group, category and tags picked there): its files named
     * with choice codes (an output name such as Img-%g) renamed with them filled in, their tags and groups
     * going along; then Move automatically, held for them, moves the run (its Destination subfolder's
     * codes filled in the same way).
     */
    void afterReady(long download) {
        FileGroups.load(PromptDb.dir());
        MediaTags.load(PromptDb.dir());
        for (SogniDownloads.Item item : SogniDownloads.namedWithChoices(download)) {
            File f = new File(item.where);
            if (!f.isFile()) continue;
            long size = f.length();
            String name = DownloadMove.nameFor(f.getName(), DownloadMove.choicesOf(f.getName(), size));
            String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", ""), ext = name.substring(stem.length());
            File to = new File(f.getParentFile(), name);
            for (int n = 1; to.exists(); n++) to = new File(f.getParentFile(), stem + " (" + n + ")" + ext);
            if (!f.renameTo(to)) continue;
            DownloadMove.carryChoices(f.getName(), to.getName(), size);
            SogniDownloads.moved(item, to.getName(), to.getAbsolutePath(), to.getAbsolutePath());
            Playlist.repoint(PromptDb.dir(), java.util.Collections.singleton(f.getAbsolutePath()), to.getAbsolutePath());
        }
        DownloadsScreen.finished(app);
    }

    /** Picture ready's right click menu shown last (for the tests). */
    javax.swing.JPopupMenu lastReadyMenu;

    /**
     * Picture ready's Crop image: the picture in the preview with the crop frame on; the crop's menu
     * has Save as… (beside the picture), Process as a reference and Upscale (both close Picture ready).
     */
    void cropReady(File pic, java.awt.Component from) {
        PromptDb db = app.promptDb;
        final Runnable leave = () -> {
            java.awt.Window w = SwingUtilities.getWindowAncestor(from);
            if (w != null) w.dispose();
        };
        db.previewFolder = pic.getAbsoluteFile().getParentFile();
        db.previewCropNow = true;
        db.previewLeave = leave;
        db.previewProcess = made -> {
            leave.run();
            SwingUtilities.invokeLater(() -> ProcessRefDialog.open(app, made));
        };
        try {
            db.preview(pic.getName(), java.nio.file.Files.readAllBytes(pic.toPath()));
        } catch (Exception ex) {
            app.setNow("Could not read " + pic.getName());
        } finally {
            db.previewFolder = null;
            db.previewCropNow = false;
            db.previewLeave = null;
            db.previewProcess = null;
        }
    }

    /**
     * Crop on a Sogni Params picture row: the picture in the Preview window with the crop frame on;
     * its menu has Use the crop in this row (one that needs upscaling is asked first), Save as…, Upscale.
     */
    void cropPicture(String path, java.util.function.Consumer<File> row) {
        File pic = new File(path);
        PromptDb db = app.promptDb;
        db.previewFolder = pic.getAbsoluteFile().getParentFile();
        db.previewCropNow = true;
        db.previewRow = row;
        try {
            db.preview(pic.getName(), java.nio.file.Files.readAllBytes(pic.toPath()));
        } catch (Exception ex) {
            app.setNow("Could not read " + pic.getName());
        } finally {
            db.previewFolder = null;
            db.previewCropNow = false;
            db.previewRow = null;
        }
    }

    static final String SELECT_DIR = "select-file-dir";

    /** Select file's chooser, open in the folder the last file was picked from (a subfolder too). */
    static JFileChooser selectChooser() {
        String last = java.util.prefs.Preferences.userNodeForPackage(PyJav.class).get(SELECT_DIR, "");
        File dir = last.length() > 0 ? new File(last) : null;
        return dir != null && dir.isDirectory() ? new JFileChooser(dir) : new JFileChooser();
    }

    /** A file Select file picked: its folder kept for the next one. */
    static void selected(File f) {
        File dir = f == null ? null : f.getAbsoluteFile().getParentFile();
        if (dir != null) java.util.prefs.Preferences.userNodeForPackage(PyJav.class).put(SELECT_DIR, dir.getAbsolutePath());
    }

    /** What Save or Browse sections said last; the tests' answers (a section's index to load; 0 save first, 1 load, 2 cancel). */
    static volatile String sectionSaid = "";
    static Integer pickSection;
    static Integer askSave;
    /** The tests: what is done with the section picked (0 load, 1 Edit section, 2 Remove section), Edit's text, Remove's answer (true: Remove). */
    static Integer sectionAction;
    static String sectionEditText;
    static Boolean sectionRemove;

    private void sectionSay(String text) {
        sectionSaid = text;
        app.setNow(text);
    }

    /** A row's value now (its text area, else its field). */
    static java.util.Map<String, String> sectionFields(java.util.List<ProgramParams.Param> ps, JTextField[] fields, javax.swing.JTextArea[] areas) {
        String[] now = new String[ps.size()];
        for (int i = 0; i < now.length; i++) now[i] = areas[i] != null ? areas[i].getText() : fields[i] != null ? fields[i].getText() : "";
        return SogniSections.fields(ps, now);
    }

    /** The tests: Export's file (else a file picker), Paste's answer (true: replace), Send's program and answer. */
    static File exportTo;
    /** The tests: Export's answer when the program has saved sections (true: all of them), else asked. */
    static Boolean exportAll;
    static Boolean pasteReplace;
    static String sendTo;
    static Boolean sendYes;
    static volatile String clipSaid = "";

    private void clipSay(String text) {
        clipSaid = text;
        app.setNow(text);
    }

    /** Export: the rows Save keeps, written into a file picked ("Prompt: ...", "Loras: ..."). */
    void exportSection(java.awt.Component at, String program, java.util.List<ProgramParams.Param> ps, JTextField[] fields, javax.swing.JTextArea[] areas) {
        java.util.Map<String, String> now = sectionFields(ps, fields, areas);
        // Sections saved for the program: this entry, or all of them (asked).
        java.util.List<PromptVault.SogniSection> saved = java.util.Collections.emptyList();
        try {
            saved = PromptDb.vault().sogniSections(program);
        } catch (Exception ignored) {
            // none to offer
        }
        if (!saved.isEmpty()) {
            boolean all;
            if (exportAll != null) {
                all = exportAll.booleanValue();
            } else {
                Object[] options = {ParamsClip.EXPORT_THIS, ParamsClip.exportAllLabel(program, saved.size()), "Cancel"};
                int ans = JOptionPane.showOptionDialog(at, ParamsClip.EXPORT_ASK, ParamsClip.EXPORT, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                if (ans != 0 && ans != 1) return;
                all = ans == 1;
            }
            if (all) {
                this.exportText(at, program, ParamsClip.exportAllName(program), ParamsClip.exportAllText(program, saved), "the " + saved.size() + " " + program + " sections");
                return;
            }
        }
        if (now.isEmpty()) {
            this.clipSay("Nothing to export: the prompt and the other rows are empty");
            return;
        }
        this.exportText(at, program, ParamsClip.exportName(program, now), ParamsClip.exportText(now), "the " + program + " section");
    }

    /** Export's file written: the one the tests give, else one picked (its name suggested). */
    private void exportText(java.awt.Component at, String program, String name, String text, String what) {
        File to = exportTo;
        if (to == null) {
            JFileChooser chooser = selectChooser();
            chooser.setDialogTitle(ParamsClip.EXPORT + " " + what);
            chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), name));
            if (chooser.showSaveDialog(at) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
            to = chooser.getSelectedFile();
            if (to.isFile() && JOptionPane.showConfirmDialog(at, to.getName() + " is there already. Replace it?", ParamsClip.EXPORT, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            selected(to);
        }
        try {
            Files.write(to.toPath(), text.getBytes(StandardCharsets.UTF_8));
            this.clipSay("Exported " + what + " into " + to.getName());
        } catch (Exception ex) {
            this.clipSay("Could not export " + what + ": " + ex.getMessage());
        }
    }

    /** The prompt's or LoRAs' Copy, Paste and Send buttons; `box` gives the row's text box. */
    java.util.List<JButton> clipButtons(final String program, final String token, final java.util.function.Supplier<javax.swing.text.JTextComponent> box) {
        JButton copy = new JButton(ParamsClip.COPY), paste = new JButton(ParamsClip.PASTE), send = new JButton(ParamsClip.SEND);
        copy.setName("params-copy:" + token);
        paste.setName("params-paste:" + token);
        send.setName("params-send:" + token);
        copy.setToolTipText(ParamsClip.COPY_TIP);
        paste.setToolTipText(ParamsClip.PASTE_TIP);
        send.setToolTipText(ParamsClip.SEND_TIP);
        for (JButton b : new JButton[] {copy, paste, send}) {
            b.setMargin(new java.awt.Insets(0, 4, 0, 4));
            b.setFocusable(false);
        }
        copy.addActionListener(e -> {
            String text = box.get().getText();
            if (text.trim().length() > 0) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null);
            this.clipSay(ParamsClip.copied(token, text));
        });
        paste.addActionListener(e -> {
            String clip = null;
            try {
                clip = (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            } catch (Exception ignored) {
                // no text there
            }
            if (clip == null || clip.trim().isEmpty()) {
                this.clipSay("The clipboard has no text");
                return;
            }
            javax.swing.text.JTextComponent t = box.get();
            if (t.getText().trim().length() > 0) {
                boolean yes = pasteReplace != null ? pasteReplace.booleanValue()
                    : JOptionPane.showConfirmDialog(paste, ParamsClip.replaceAsk(token), "Paste", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
                if (!yes) return;
            }
            t.setText(clip.trim());
            t.setCaretPosition(0);
            this.clipSay("Pasted into the " + ParamsClip.what(token));
        });
        send.addActionListener(e -> {
            final String text = box.get().getText();
            if (text.trim().isEmpty()) {
                this.clipSay("No " + ParamsClip.what(token) + " to send");
                return;
            }
            java.util.List<String> targets = ParamsClip.targets(program, token, PyJav::programTextOf);
            if (targets.isEmpty()) {
                this.clipSay("No other Sogni program takes a " + ParamsClip.what(token));
                return;
            }
            java.util.function.Consumer<String> go = target -> {
                boolean yes = sendYes != null ? sendYes.booleanValue()
                    : JOptionPane.showConfirmDialog(send, ParamsClip.sendAsk(token, target, text), ParamsClip.SEND_TITLE, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
                if (!yes) return;
                java.util.List<ProgramParams.Param> theirs = ProgramParams.parse(programTextOf(target));
                this.saveParams(target, ParamsClip.put(theirs, this.loadParams(target), token, text));
                this.clipSay(ParamsClip.sent(token, target));
            };
            if (sendTo != null) {
                if (targets.contains(sendTo)) go.accept(sendTo);
                else this.clipSay(sendTo + " takes no " + ParamsClip.what(token));
                return;
            }
            javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
            for (final String target : targets) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(SogniSections.app(target));
                item.setName("params-send-to:" + target);
                item.addActionListener(ev -> go.accept(target));
                m.add(item);
            }
            MenuCancel.add(m);
            m.show(send, 0, send.getHeight());
        });
        return java.util.Arrays.asList(copy, paste, send);
    }

    /** A program's text for its Params (its bundled source); null when it is not there. */
    static String programTextOf(String program) {
        try {
            return PyJavHints.programText(program, new String(ProgramFiles.read("Java", program), StandardCharsets.UTF_8), null);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Save: the rows kept as the program's section, unless the same one is saved already. True when they are saved now. */
    boolean saveSection(java.awt.Component at, String program, java.util.List<ProgramParams.Param> ps, JTextField[] fields, javax.swing.JTextArea[] areas) {
        java.util.Map<String, String> now = sectionFields(ps, fields, areas);
        if (now.isEmpty()) {
            this.sectionSay("Nothing to save: the prompt and the other rows are empty");
            return false;
        }
        try {
            String text = SogniSections.text(now);
            PromptVault.SogniSection same = PromptDb.vault().findSogniSection(program, text);
            if (same != null) {
                this.sectionSay("This section is saved already: " + SogniSections.label(same));
                if (pickSection == null) JOptionPane.showMessageDialog(at, sectionSaid, SogniSections.SAVE, JOptionPane.PLAIN_MESSAGE);
                return true;
            }
            PromptVault.SogniSection sec = PromptDb.vault().addSogniSection(program, text);
            this.sectionSay("Saved a " + program + " section: " + SogniSections.label(sec));
            return true;
        } catch (Exception ex) {
            this.sectionSay("Could not save the section: " + ex.getMessage());
            return false;
        }
    }

    /** Browse sections' list shown last, and the full view's answer for the tests (0 Load, 1 Edit, 2 Remove, -1 Close; null: asked). */
    javax.swing.JList<String> lastSectionsList;
    static Integer sectionShownAnswer;
    String lastSectionShown;

    /** A section shown fully (its entries, a line each): what was picked, 0 Load, 1 Edit, 2 Remove, -1 Close. */
    int showSection(java.awt.Component at, PromptVault.SogniSection sec) {
        this.lastSectionShown = SogniSections.full(sec);
        if (sectionShownAnswer != null) return sectionShownAnswer.intValue();
        javax.swing.JTextArea text = new javax.swing.JTextArea(this.lastSectionShown, 10, 60);
        text.setName("section-full-text");
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        Object[] options = {SogniSections.LOAD, SogniSections.EDIT, SogniSections.REMOVE, "Close"};
        int ans = JOptionPane.showOptionDialog(at, new javax.swing.JScrollPane(text), SogniSections.label(sec), JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        return ans >= 0 && ans <= 2 ? ans : -1;
    }

    /** Browse sections: the program's sections; one picked fills the rows (the entries now saved first, when asked); Delete removes one. */
    void browseSections(java.awt.Component at, String program, java.util.List<ProgramParams.Param> ps, JTextField[] fields, javax.swing.JTextArea[] areas) {
        java.util.List<PromptVault.SogniSection> all;
        try {
            all = PromptDb.vault().sogniSections(program);
        } catch (Exception ex) {
            this.sectionSay("Could not read the sections: " + ex.getMessage());
            return;
        }
        if (all.isEmpty()) {
            this.sectionSay("No " + program + " sections yet: Save keeps the entries as one");
            if (pickSection == null) JOptionPane.showMessageDialog(at, sectionSaid, SogniSections.BROWSE, JOptionPane.PLAIN_MESSAGE);
            return;
        }
        int chosen;
        boolean delete = false, edit = false;
        if (pickSection != null) {
            chosen = pickSection;
            if (sectionAction != null && sectionAction == 1) edit = true;
            if (sectionAction != null && sectionAction == 2) delete = true;
        } else {
            // Grouped by the prompt's first sentences, A to Z: a group's right click (or hold) lists its
            // sections under it; a double click shows a section fully (Load, Edit, Remove there too).
            final java.util.List<SogniSections.Group> groups = SogniSections.groups(all);
            final java.util.Set<SogniSections.Group> open = new java.util.HashSet<SogniSections.Group>();
            final java.util.List<Object> rows = new java.util.ArrayList<Object>();
            final javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<String>();
            final Runnable fill = () -> {
                rows.clear();
                model.clear();
                for (SogniSections.Group g : groups) {
                    rows.add(g);
                    model.addElement(open.contains(g) ? "\u25be " + g.lead + " (" + g.sections.size() + ")" : SogniSections.groupLabel(g));
                    if (!open.contains(g)) continue;
                    for (PromptVault.SogniSection sec : g.sections) {
                        rows.add(sec);
                        model.addElement("      " + SogniSections.label(sec));
                    }
                }
            };
            fill.run();
            final javax.swing.JList<String> list = new javax.swing.JList<String>(model);
            list.setName("sections-list");
            list.setSelectedIndex(0);
            list.setVisibleRowCount(Math.min(12, Math.max(4, rows.size())));
            this.lastSectionsList = list;
            final java.util.function.IntConsumer toggle = i -> {
                if (i < 0 || i >= rows.size() || !(rows.get(i) instanceof SogniSections.Group)) return;
                SogniSections.Group g = (SogniSections.Group) rows.get(i);
                if (g.sections.size() < 2) return;
                if (!open.remove(g)) open.add(g);
                fill.run();
                list.setSelectedIndex(rows.indexOf(g));
            };
            final java.util.function.IntFunction<PromptVault.SogniSection> sectionAt = i -> {
                if (i < 0 || i >= rows.size()) return null;
                Object r = rows.get(i);
                if (r instanceof PromptVault.SogniSection) return (PromptVault.SogniSection) r;
                SogniSections.Group g = (SogniSections.Group) r;
                return g.sections.size() == 1 ? g.sections.get(0) : null;
            };
            // A section shown fully and an action picked there (0 Load, 1 Edit, 2 Remove).
            final PromptVault.SogniSection[] fullSec = new PromptVault.SogniSection[1];
            final int[] fullAct = {-1};
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                javax.swing.Timer hold;

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    final int i = list.locationToIndex(e.getPoint());
                    if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                        toggle.accept(i);
                        return;
                    }
                    this.hold = new javax.swing.Timer(600, ev -> toggle.accept(i));
                    this.hold.setRepeats(false);
                    this.hold.start();
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (this.hold != null) this.hold.stop();
                }

                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() < 2) return;
                    int i = list.locationToIndex(e.getPoint());
                    PromptVault.SogniSection sec = sectionAt.apply(i);
                    if (sec == null) {
                        toggle.accept(i);
                        return;
                    }
                    int act = PyJav.this.showSection(list, sec);
                    if (act < 0) return;
                    fullSec[0] = sec;
                    fullAct[0] = act;
                    java.awt.Window w = SwingUtilities.getWindowAncestor(list);
                    if (w != null) w.dispose();
                }
            });
            Object[] options = {SogniSections.LOAD, SogniSections.EDIT, SogniSections.REMOVE, "Cancel"};
            PromptVault.SogniSection sec;
            int ans;
            while (true) {
                ans = JOptionPane.showOptionDialog(at, new javax.swing.JScrollPane(list), SogniSections.BROWSE + " \u00b7 " + program, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                if (fullSec[0] != null) {
                    sec = fullSec[0];
                    ans = fullAct[0];
                    break;
                }
                if (ans < 0 || ans > 2) return;
                int i = list.getSelectedIndex();
                sec = sectionAt.apply(i);
                if (sec != null) break;
                // A group of several picked: its sections open under it, to pick one.
                if (i < 0) return;
                toggle.accept(i);
            }
            chosen = all.indexOf(sec);
            if (ans == 1) edit = true;
            else if (ans == 2) delete = true;
        }
        if (chosen < 0 || chosen >= all.size()) return;
        PromptVault.SogniSection sec = all.get(chosen);
        if (delete) {
            // Remove section: asked first.
            boolean sure = sectionRemove != null ? sectionRemove.booleanValue()
                : JOptionPane.showConfirmDialog(at, SogniSections.removeAsk(sec), SogniSections.REMOVE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
            if (!sure) return;
            try {
                PromptDb.vault().deleteSogniSection(sec.id);
                this.sectionSay("Removed: " + SogniSections.label(sec));
            } catch (Exception ex) {
                this.sectionSay("Could not remove it: " + ex.getMessage());
            }
            return;
        }
        if (edit) {
            // Edit section: its entries, a line each ("Prompt: ...", "Loras: ...").
            String now;
            if (sectionEditText != null) now = sectionEditText;
            else {
                javax.swing.JTextArea text = new javax.swing.JTextArea(sec.text.trim(), 8, 50);
                text.setName("section-edit-text");
                text.setLineWrap(true);
                text.setWrapStyleWord(true);
                if (JOptionPane.showConfirmDialog(at, new javax.swing.JScrollPane(text), SogniSections.EDIT, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
                now = text.getText();
            }
            now = now.trim() + "\n";
            String problem = SogniSections.editProblem(now);
            if (problem != null) {
                this.sectionSay("Not saved: " + problem);
                return;
            }
            try {
                PromptDb.vault().updateSogniSection(sec.id, now);
                sec.text = now;
                this.sectionSay("Saved: " + SogniSections.label(sec));
            } catch (Exception ex) {
                this.sectionSay("Could not save the section: " + ex.getMessage());
            }
            return;
        }
        java.util.Map<String, String> now = sectionFields(ps, fields, areas);
        boolean saved;
        try {
            saved = now.isEmpty() || PromptDb.vault().findSogniSection(program, SogniSections.text(now)) != null;
        } catch (Exception ex) {
            saved = false;
        }
        if (!saved) {
            Object[] options = {SogniSections.SAVE_FIRST, SogniSections.DONT_SAVE, "Cancel"};
            int ans = askSave != null ? askSave : JOptionPane.showOptionDialog(at, SogniSections.ASK, SogniSections.BROWSE, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (ans == 0 && !this.saveSection(at, program, ps, fields, areas)) return;
            if (ans != 0 && ans != 1) return;
        }
        java.util.Map<String, String> f = SogniSections.read(sec.text);
        for (int i = 0; i < ps.size(); i++) {
            if (!SogniSections.kept(ps.get(i))) continue;
            String v = f.get(ps.get(i).token);
            if (areas[i] != null) areas[i].setText(v == null ? "" : v);
            else if (fields[i] != null) fields[i].setText(v == null ? "" : v);
        }
        this.sectionSay("Loaded: " + SogniSections.label(sec));
    }

    /** The Params shown last: their switches and text rows (a definitions file fills several). */
    java.util.List<ProgramParams.Param> shownParams;
    JTextField[] shownFields;
    javax.swing.JTextArea[] shownAreas;
    /** The tests: the definition picked when a file has several (null: ask). */
    static Integer pickDef;

    /** A text file's styles into SogniMusic's --styles row ("Funk|Hard Rock|Metal"). */
    void stylesFrom(JTextField field, File file) {
        try {
            String list = ProgramParams.styleList(ProgramParams.promptText(java.nio.file.Files.readAllBytes(file.toPath())));
            field.setText(list);
            int n = list.isEmpty() ? 0 : list.split("\\|").length;
            app.setNow(n + (n == 1 ? " style" : " styles") + " from " + file.getName());
        } catch (Exception ex) {
            app.setNow("Could not read " + file.getName());
        }
    }

    /** A list file's names into a row (Import: vst_render's Kits left out). */
    void importList(JTextField field, File file) {
        try {
            String list = ProgramParams.importedList(new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            field.setText(list);
            int n = list.isEmpty() ? 0 : list.split(",").length;
            app.setNow(n + (n == 1 ? " name" : " names") + " from " + file.getName());
        } catch (Exception ex) {
            app.setNow("Could not read " + file.getName());
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
            // A definitions file (App:, Prompt:, Loras:, ---- between them): its fields fill their rows.
            if (ParamDefs.looksLike(text) && this.defsFrom(area, file.getName(), text)) return;
            area.setText(text);
            area.setCaretPosition(0);
        } catch (Exception ex) {
            app.setNow("Could not read " + file.getName());
        }
    }

    /** A definitions file for the Params shown: the one for this program fills its rows (several: a choice). False: no definitions. */
    boolean defsFrom(javax.swing.JTextArea prompt, String fileName, String text) {
        java.util.Set<String> tokens = new java.util.HashSet<String>();
        if (this.shownParams != null) for (ProgramParams.Param p : this.shownParams) tokens.add(p.token);
        java.util.List<ParamDefs.Def> all = ParamDefs.parse(text, tokens);
        if (all.isEmpty()) return false;
        String program = app.pyName;
        java.util.List<ParamDefs.Def> mine = ParamDefs.forProgram(all, program);
        if (mine.isEmpty()) {
            app.setNow(fileName + " is for " + ParamDefs.apps(all) + ", not " + program);
            return true;
        }
        ParamDefs.Def d = mine.get(0);
        if (mine.size() > 1) {
            String[] labels = new String[mine.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = mine.get(i).label();
            int at = pickDef != null ? pickDef : java.util.Arrays.asList(labels).indexOf(JOptionPane.showInputDialog(app, fileName + ": " + mine.size() + " definitions",
                "Definitions", JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]));
            if (at < 0) return true;
            d = mine.get(at);
        }
        java.util.List<String> filled = new java.util.ArrayList<String>(), missing = new java.util.ArrayList<String>();
        for (java.util.Map.Entry<String, String> e : d.fields.entrySet()) {
            javax.swing.text.JTextComponent target = null;
            if (this.shownParams != null) {
                for (int i = 0; i < this.shownParams.size(); i++) {
                    if (!this.shownParams.get(i).token.equals(e.getKey())) continue;
                    if (this.shownAreas != null && i < this.shownAreas.length && this.shownAreas[i] != null) target = this.shownAreas[i];
                    else if (this.shownFields != null && i < this.shownFields.length && this.shownFields[i] != null) target = this.shownFields[i];
                }
            }
            if (target == null && e.getKey().equals("--prompt")) target = prompt;
            if (target == null) {
                missing.add(e.getKey());
                continue;
            }
            target.setText(e.getValue());
            target.setCaretPosition(0);
            filled.add(e.getKey());
        }
        app.setNow("Filled " + String.join(", ", filled) + (missing.isEmpty() ? "" : "; " + program + " has no " + String.join(", ", missing)));
        return true;
    }

    /** The first of the computer's usual VST3 and VST2 folders that exists (vst_render.py's plugin rows), else null. */
    /** Where a plugin was last picked from (kept between sessions), or the usual VST folder. */
    static File lastVstFolder() {
        String kept = java.util.prefs.Preferences.userNodeForPackage(PyJav.class).get(LAST_VST_KEY, "");
        if (kept.length() > 0 && new File(kept).isDirectory()) return new File(kept);
        return vstFolder();
    }

    static void rememberVstFolder(File dir) {
        if (dir == null) return;
        java.util.prefs.Preferences node = java.util.prefs.Preferences.userNodeForPackage(PyJav.class);
        node.put(LAST_VST_KEY, dir.getAbsolutePath());
        try {
            node.flush();
        } catch (java.util.prefs.BackingStoreException ignored) {
            // kept for this session
        }
    }

    static final String LAST_VST_KEY = "lastVstFolder";

    static File vstFolder() {
        List<File> dirs = vstFolders();
        return dirs.isEmpty() ? null : dirs.get(0);
    }

    /** The usual VST folders of this computer that exist (VST3 first). */
    static List<File> vstFolders() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String home = System.getProperty("user.home", "");
        String[] dirs;
        if (os.contains("win")) {
            String pf = System.getenv("ProgramFiles") == null ? "C:\\Program Files" : System.getenv("ProgramFiles");
            dirs = new String[] {pf + "\\Common Files\\VST3", pf + "\\Common Files\\VST2", pf + "\\VSTPlugins", pf + "\\Steinberg\\VSTPlugins"};
        } else if (os.contains("mac")) {
            dirs = new String[] {"/Library/Audio/Plug-Ins/VST3", "/Library/Audio/Plug-Ins/VST", home + "/Library/Audio/Plug-Ins/VST3"};
        } else {
            dirs = new String[] {home + "/.vst3", "/usr/lib/vst3", home + "/.vst", "/usr/lib/vst", "/usr/local/lib/vst3"};
        }
        List<File> found = new java.util.ArrayList<File>();
        for (String d : dirs) if (new File(d).isDirectory()) found.add(new File(d));
        return found;
    }

    static void collectButtons(java.awt.Container c, List<JButton> out) {
        for (java.awt.Component k : c.getComponents()) {
            if (k instanceof JButton) out.add((JButton) k);
            else if (k instanceof java.awt.Container) collectButtons((java.awt.Container) k, out);
        }
    }

    /**
     * The run's prompt sheets (--saveprompt): one the same as a version in the library already is not
     * saved again; the others go where Save prompt says (or where the sheet says, when General
     * settings does not ask), as the prompt's next version, and are written to disk with that version
     * number and the date. Lines for the log.
     */
    /** The next run's arguments as they are (a step of Midi2Wav's test), or null. */
    java.util.List<String> forcedArgs;
    /** A reconnect's File > Downloads run (it carries on that run), its note, and how many were made for this run. */
    long reconnectDownload;
    String reconnectNote;
    int reconnects;

    /** Why a run is joined again: "timed out after 1200 s", or its failure. */
    static String reconnectWhy(String log) {
        String why = "timed out";
        for (String line : (log == null ? "" : log).split("\n")) {
            String t = line.trim();
            if (t.startsWith("Timed out (")) why = "timed out after " + t.substring(11).replace(").", "").replace(")", "");
            else if (t.startsWith("Failed: ")) why = t.substring(8);
        }
        return why.length() > 100 ? why.substring(0, 100) + "\u2026" : why;
    }

    /**
     * Midi2Wav's test (--test), one step a run: after Midi2Wav, DrumMidi_CRT on its WAV; after
     * DrumMidi, CompareHits on the WAV, DrumMidi's MIDI and the song; then CompareHits on the MIDI
     * played against DrumMidi's; then the report. True when the test took this run (no audio offer).
     */
    boolean testStep(String name, java.util.List<String> argv, String verdict, PythonRun.FileOut madeMidi, File wav, String log) {
        Midi2WavTest t = Midi2WavTest.current();
        if (Midi2WavTest.musicSteps(name, argv, true)) {
            // A comparison is measured and ranked instead (MusicCompare): the one-track test does not apply.
            if (!verdict.startsWith("Succeeded") || MusicCompare.wanted(name, argv)) return false;
            t = Midi2WavTest.startMusic(argv, wav != null && wav.getName().toLowerCase().endsWith(".wav") ? wav : null, this.loadParams("DrumMidi_CRT.jar"));
            if (t == null) {
                this.testNote("Test stopped: no WAV was made of the track");
                return false;
            }
            final java.util.List<String> drumArgs = t.drumMidiArgs();
            this.testNote("Test: " + t.expect.drumMidiWhy(t.drumSaved));
            this.testNote((t.testing ? "Test: " : t.vst ? "VST: " : "Batch folder: ") + "DrumMidi_CRT on " + wav.getName() + ", then "
                + (t.testing ? "CompareHits" : t.vst ? "vst_render.py" : "its MIDI into " + new File(t.batchDir).getName()));
            SwingUtilities.invokeLater(() -> {
                app.pyInputPath = null;
                app.programMenus.selectListedProgram("Java", "DrumMidi_CRT.jar");
                this.forcedArgs = drumArgs;
                this.runPython();
            });
            // The track is offered as usual unless the test (or the VST render) takes the run.
            return t.testing || t.vst;
        }
        // Midi2Wav --dir --test: DrumMidi_CRT --dir on the folder's WAVs, then CompareHits --batch.
        if (Midi2WavTest.batchWanted(name, argv)) {
            t = Midi2WavTest.startBatch(verdict, argv);
            if (t == null) return false;
            final java.util.List<String> drumArgs = t.batchDrumArgs();
            this.testNote("Test: DrumMidi_CRT on every WAV in " + new File(t.batch).getName() + ", then CompareHits --batch");
            SwingUtilities.invokeLater(() -> {
                app.pyInputPath = null;
                app.programMenus.selectListedProgram("Java", "DrumMidi_CRT.jar");
                this.forcedArgs = drumArgs;
                this.runPython();
            });
            return true;
        }
        if (Midi2WavTest.wanted(name, argv)) {
            t = Midi2WavTest.start(verdict, argv, wav);
            if (t == null) return false;
            this.testNote("Test: DrumMidi_CRT on " + wav.getName() + ", then CompareHits");
            SwingUtilities.invokeLater(() -> {
                this.audioInputPath = wav.getAbsolutePath();
                app.programMenus.selectListedProgram("Java", "DrumMidi_CRT.jar");
                this.runPython();
            });
            return true;
        }
        if (t == null) return false;
        final Midi2WavTest test = t;
        try {
            if (t.stage == Midi2WavTest.BATCH_DRUMS && "DrumMidi_CRT.jar".equals(name)) {
                if (!t.batchDrumsDone(verdict)) {
                    this.testNote("Test stopped: DrumMidi_CRT made no MIDI (" + (verdict.length() == 0 ? "no result" : verdict) + ")");
                    return true;
                }
                this.testNote("Test: CompareHits --batch " + new File(t.batch).getName());
                SwingUtilities.invokeLater(() -> this.runTestCompare(test.batchCompareArgs()));
                return true;
            }
            if (t.stage == Midi2WavTest.BATCH_COMPARE && "CompareHits.java".equals(name)) {
                t.batchCompareDone(log);
                SwingUtilities.invokeLater(() -> this.showTestReport(test));
                return true;
            }
            if (t.stage == Midi2WavTest.DRUM_MIDI && "DrumMidi_CRT.jar".equals(name)) {
                byte[] drums = madeMidi != null ? madeMidi.bytes : null;
                String drumsName = madeMidi != null ? madeMidi.name : null;
                if (drums == null && this.pyOutputPath != null && new File(this.pyOutputPath).isFile()) {
                    drums = Files.readAllBytes(new File(this.pyOutputPath).toPath());
                    drumsName = new File(this.pyOutputPath).getName();
                }
                if (!t.drumMidiDone(drums, drumsName, app.learned, app.importedSongs)) {
                    this.testNote("Test stopped: DrumMidi_CRT made no MIDI");
                    return true;
                }
                if (t.batchNote.length() > 0) this.testNote(t.batchNote);
                // SogniMusic's --vst (or --batchdir) without --test: DrumMidi's MIDI goes straight on to vst_render.py, or that was all.
                if (!t.testing) {
                    if (t.vst) SwingUtilities.invokeLater(() -> this.toVst(test.drums, false));
                    return true;
                }
                this.testNote("Test: CompareHits on " + new File(t.wav).getName() + ", " + new File(t.drums).getName() + (t.song.length() > 0 ? ", " + new File(t.song).getName() : " (no song was made)"));
                SwingUtilities.invokeLater(() -> this.runTestCompare(test.compareArgs()));
                return true;
            }
            if ((t.stage == Midi2WavTest.COMPARE || t.stage == Midi2WavTest.AGAINST_PLAYED) && "CompareHits.java".equals(name) && !verdict.startsWith("Succeeded")) {
                Midi2WavTest.stop();
                this.testNote("Test stopped: CompareHits did not run (" + (verdict.length() == 0 ? "no result" : verdict) + ")");
                return true;
            }
            if (t.stage == Midi2WavTest.COMPARE && "CompareHits.java".equals(name)) {
                if (t.compareDone(log)) {
                    this.testNote("Test: CompareHits --truth: " + new File(t.drums).getName() + (t.song.length() > 0 ? " and the song" : "") + " against the drums played (" + new File(t.played).getName() + ")");
                    SwingUtilities.invokeLater(() -> this.runTestCompare(test.againstPlayedArgs()));
                } else {
                    SwingUtilities.invokeLater(() -> this.showTestReport(test));
                }
                return true;
            }
            if (t.stage == Midi2WavTest.AGAINST_PLAYED && "CompareHits.java".equals(name)) {
                t.againstPlayedDone(log);
                SwingUtilities.invokeLater(() -> this.showTestReport(test));
                return true;
            }
        } catch (Exception ex) {
            this.testNote("Test stopped: " + ex.getMessage());
        }
        // Another program ran in between: the test is over.
        Midi2WavTest.stop();
        return false;
    }

    /** SogniMusic --compare_models: each model's track (as saved) measured in the background, then the ranking under the log. */
    void compareModels(final java.util.List<String> argv, final java.util.Map<String, File> saved, final String log) {
        this.testNote("Measuring the models' tracks…");
        final java.util.Map<String, File> files = new java.util.HashMap<String, File>(saved);
        new Thread(() -> {
            String report;
            try {
                report = MusicCompare.of(argv, log, name -> {
                    File f = files.get(name);
                    try {
                        byte[] data = f == null ? null : Files.readAllBytes(f.toPath());
                        return data == null ? null : "wav".equals(AudioIo.sniff(data)) ? AudioIo.parseWav(data) : Mp3Decode.parse(data);
                    } catch (Exception ex) {
                        return null;
                    }
                });
            } catch (Throwable ex) {
                report = "Could not compare the models' tracks: " + ex;
            }
            final String shown = report == null ? "Fewer than two tracks to compare" : report;
            // Kept beside the tracks (Downloads): the log under the run is gone after the next run.
            final File kept = report == null ? null : this.saveProgramFile(MusicCompare.reportName(log), report.getBytes(StandardCharsets.UTF_8));
            // --compare_to_file_sets: each track as a WAV, then DrumMidi_CRT on each into a file set of its own.
            final java.util.List<String[]> wavs = new java.util.ArrayList<String[]>();
            if (argv.contains(MusicCompare.TO_FILE_SETS)) {
                for (String[] t : MusicCompare.tracks(log)) {
                    File mp3 = files.get(t[1]);
                    File wav = mp3 == null ? null : this.asWav(mp3);
                    if (wav != null && wav.getName().toLowerCase().endsWith(".wav")) {
                        wavs.add(new String[] {t[0], wav.getAbsolutePath()});
                        if (RunZip.current() != null) RunZip.current().add(wav.getName(), wav);
                    }
                }
            }
            SwingUtilities.invokeLater(() -> {
                for (String line : shown.split("\n")) this.testNote(line);
                if (kept != null) this.testNote("Saved " + kept.getPath());
                if (kept != null && RunZip.current() != null) RunZip.current().add(kept.getName(), kept);
                if (!argv.contains(MusicCompare.TO_FILE_SETS)) {
                    this.zipFinish();
                    return;
                }
                MusicCompare.Chain c = MusicCompare.startChain(argv, Engine.fileSetSources(app.learned, app.learnedFills), wavs, this.loadParams("DrumMidi_CRT.jar"), log);
                if (c == null) {
                    this.testNote("No WAVs for DrumMidi: no file sets made");
                    this.zipFinish();
                } else {
                    c.report = shown;
                    this.testNote("File sets: DrumMidi_CRT on each track, into " + c.run + " <model>");
                    this.chainNext();
                }
            });
        }, "pulsekit-compare-models").start();
    }

    /** --zip: a run's files and its log into the zip being gathered (`about`: what the run made, for its log's name). */
    void zipAdd(RunZip z, String program, String about, PythonRun.Result result) {
        if (z == null || result == null) return;
        for (PythonRun.FileOut f : result.files) z.add(f.name, f.bytes);
        z.addLog(program, about, result.log);
    }

    /** --zip: the files gathered written into one zip in the program files folder (Downloads), off the screen's thread. */
    void zipFinish() {
        final RunZip z = RunZip.finish();
        if (z == null) return;
        this.testNote("Zipping " + z.size() + " files into " + z.name + "…");
        DownloadMove.zipping.incrementAndGet();
        new Thread(() -> {
            StringBuilder text = new StringBuilder();
            try {
                File tmp = File.createTempFile("run-zip", ".zip");
                try {
                    // Parts of at most 25 MB each (sending often stops at 30 MB), each a zip of its own.
                    java.util.List<java.util.List<ZipPack.Source>> parts = z.parts(RunZip.PART);
                    for (int i = 0; i < parts.size(); i++) {
                        ZipPack.write(tmp, parts.get(i), null);
                        String name = z.partName(i, parts.size());
                        File saved = this.saveProgramFile(name, Files.readAllBytes(tmp.toPath()), z.dir);
                        text.append(text.length() == 0 ? "" : "\n").append(saved == null ? "Could not save " + name
                            : "Zipped " + parts.get(i).size() + " files (" + (tmp.length() / (1024 * 1024)) + " MB): saved " + saved.getPath());
                    }
                } finally {
                    tmp.delete();
                }
                // --zip_delete: only when every part is saved.
                if (z.deleteAfter && !text.toString().contains("Could not save")) text.append("\n").append(z.deleteZipped());
            } catch (Throwable ex) {
                text.append(text.length() == 0 ? "" : "\n").append("Could not zip the run's files: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            }
            final String shown = text.toString();
            SwingUtilities.invokeLater(() -> {
                for (String line : shown.split("\n")) this.testNote(line);
            });
            // Move automatically, held up until the zip was saved.
            DownloadMove.zipping.decrementAndGet();
            DownloadsScreen.zipSaved(app);
        }, "pulsekit-run-zip").start();
    }

    /** The comparison's next DrumMidi_CRT run (its MIDI named for the file set it makes), or the chain's end. */
    void chainNext() {
        MusicCompare.Chain c = MusicCompare.chain();
        if (c == null) return;
        final java.util.List<String> drumArgs = c.next();
        if (drumArgs == null) {
            this.importHeld(c);
            this.testNote("File sets made: " + (c.made.isEmpty() ? "none" : String.join(", ", c.made)));
            for (String line : c.report.split("\n")) if (line.length() > 0) this.testNote(line);
            this.zipFinish();
            // One message for the whole chain: what it made (after the zip has started, so it does not wait).
            final String summary = c.summary();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(app, summary, "Succeeded", JOptionPane.INFORMATION_MESSAGE));
            return;
        }
        Double asked = c.askedBpm();
        this.testNote("DrumMidi_CRT " + c.index() + " of " + c.size() + ": " + c.model() + " into the file set " + c.setFor(c.model())
            + (asked == null ? "" : " at the tempo asked, " + (asked.doubleValue() == Math.rint(asked.doubleValue()) ? Long.toString(asked.longValue()) : asked.toString()) + " BPM"));
        SwingUtilities.invokeLater(() -> {
            app.pyInputPath = null;
            app.programMenus.selectListedProgram("Java", "DrumMidi_CRT.jar");
            this.forcedArgs = drumArgs;
            this.runPython();
        });
    }

    /** A --random run's tracks: one file set a model ("SogniMusic run 4 sft"), all its tracks in it. */
    void importHeld(MusicCompare.Chain c) {
        if (c == null) return;
        MusicCompare.heldImport = true;
        try {
            for (Object[] h : c.heldSets()) {
                @SuppressWarnings("unchecked") java.util.List<Engine.MidiBars> tracks = (java.util.List<Engine.MidiBars>) h[2];
                @SuppressWarnings("unchecked") java.util.List<String> names = (java.util.List<String>) h[3];
                @SuppressWarnings("unchecked") java.util.List<byte[]> midis = (java.util.List<byte[]>) h[4];
                AudioIo.stageTracks(tracks, names, midis);
                String r = app.importLibrary.applyProgramMidi((byte[]) h[1], (String) h[0]);
                AudioIo.stageTracks(null, null, null);
                this.testNote(r + " (" + tracks.size() + (tracks.size() == 1 ? " track)" : " tracks)"));
            }
            // The tracks' MIDI files kept with the sets (fset-audio), the list showing them.
            if (!c.heldSets().isEmpty()) {
                app.fileSets.storeAudioDir();
                app.styleLibrary.refreshLearnedChips();
            }
        } finally {
            MusicCompare.heldImport = false;
        }
    }

    private void testNote(String line) {
        app.setNow(line);
        if (app.pyLog != null) {
            app.pyLog.append("\n" + line);
            this.runLog = app.pyLog.getText();
        }
    }

    /** VideoFrames' first picture: the first saved, else the first "Wrote" one in its --outdir; null when none. */
    File firstFrame(java.util.List<File> saved, java.util.List<String> argv, String log) {
        for (File f : saved) if (f.getName().toLowerCase().endsWith(".png")) return f;
        int at = argv.indexOf("--outdir");
        if (at < 0 || at + 1 >= argv.size() || log == null) return null;
        for (String line : log.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("Wrote ")) continue;
            String n = t.substring(6).replaceAll(" \\(\\d+x\\d+\\)$", "").trim();
            File f = new File(argv.get(at + 1), n);
            if (f.isFile()) return f;
        }
        return null;
    }

    /** MidiDrumGen --towav / --test: its MIDI played into a WAV by Midi2Wav (and tested); with --vst by vst_render.py. */
    void genToWav(PythonRun.FileOut midi, java.util.List<String> genArgv) {
        try {
            File dir = new File(PromptDb.dir(), "pyjav-in");
            dir.mkdirs();
            File f = new File(dir, midi.name);
            Files.write(f.toPath(), midi.bytes);
            if (Midi2WavTest.genToVst("MidiDrumGen.java", genArgv)) {
                this.toVst(f.getAbsolutePath(), genArgv.contains(Midi2WavTest.FLAG));
                return;
            }
            String source = ProgramFiles.bundledSource("Midi2Wav.java");
            final java.util.List<String> args = Midi2WavTest.genToWavArgs(f.getAbsolutePath(), genArgv, source, this.loadParams("Midi2Wav.java"));
            this.testNote("Next: Midi2Wav on " + midi.name + (args.contains(Midi2WavTest.FLAG) ? ", then its test" : ""));
            SwingUtilities.invokeLater(() -> {
                app.pyInputPath = null;
                app.programMenus.selectListedProgram("Java", "Midi2Wav.java");
                this.forcedArgs = args;
                this.runPython();
            });
        } catch (Exception ex) {
            this.testNote("Could not hand " + midi.name + " to Midi2Wav: " + ex.getMessage());
        }
    }

    /**
     * --vst (SogniMusic, MidiDrumGen): the MIDI played through a VST by vst_render.py, with the
     * plugin and settings saved in its Params, and its own --test when `test`.
     */
    void toVst(String midiPath, boolean test) {
        final java.util.List<String> args = Midi2WavTest.vstArgs(midiPath, ProgramFiles.bundledSource("vst_render.py"), this.loadParams("vst_render.py"), test);
        if (args == null) {
            this.testNote("No VST render: pick the plugin (and Edit its kit), a Carla project or a DrumGizmo kit, in vst_render.py's Params and save them; --vst uses those");
            return;
        }
        this.testNote("Next: vst_render.py on " + new File(midiPath).getName() + (test ? ", then its test" : ""));
        SwingUtilities.invokeLater(() -> {
            app.pyInputPath = null;
            app.programMenus.selectListedProgram("Python", "vst_render.py");
            this.forcedArgs = args;
            this.runPython();
        });
    }

    /** Set for the batch's own runs: Run goes on without asking. */
    boolean batchStep;
    javax.swing.JComponent pyBatchStop;

    /**
     * The batch's next step: its next run, else JoinVideo on its clips (--join), else the summary.
     * Each run is the program's own, with the arguments BatchRun planned.
     */
    void batchNext(BatchQueue q) {
        if (q == null) return;
        if (this.pyBatchStop != null) this.pyBatchStop.setVisible(true);
        final String program;
        java.util.List<String> args;
        if (q.hasNext()) {
            program = q.program;
            args = q.nextArgs();
            this.testNote(q.progress());
        } else if (q.joinNext()) {
            program = "JoinVideo.java";
            try {
                args = q.joinArgs(new File(PromptDb.dir(), "pyjav-in"));
            } catch (Exception ex) {
                this.testNote("Batch: could not list the clips for JoinVideo (" + ex.getMessage() + ")");
                this.batchOver(q);
                return;
            }
            this.testNote("Batch: JoinVideo on the " + q.clips().size() + " clips, in order");
        } else {
            this.batchOver(q);
            return;
        }
        final java.util.List<String> run = args;
        SwingUtilities.invokeLater(() -> {
            app.pyInputPath = null;
            app.programMenus.selectListedProgram("Java", program);
            this.forcedArgs = run;
            this.batchStep = true;
            this.runPython();
        });
    }

    void batchOver(BatchQueue q) {
        BatchQueue.stop();
        if (this.pyBatchStop != null) this.pyBatchStop.setVisible(false);
        this.testNote(q.summary());
    }

    javax.swing.JComponent pyScriptStop;

    /** Stop script: asks the script to stop; pressed again, it is ended at once. */
    void stopScript() {
        if (PythonRun.stop()) {
            this.testNote("Stopping: the step in progress ends after its file (vst_render.py then tests and zips what it made; Stop script again skips the next step too; pressed twice quickly, it ends the script now).");
            app.setNow("Stopping the script…");
        } else {
            this.testNote("The script was ended.");
            if (this.pyScriptStop != null) this.pyScriptStop.setVisible(false);
        }
    }

    /** Stop batch: no run after the one in progress (JoinVideo is left out too). */
    void stopBatch() {
        BatchQueue q = BatchQueue.current();
        if (q == null) {
            if (this.pyBatchStop != null) this.pyBatchStop.setVisible(false);
            return;
        }
        q.stopping = true;
        this.testNote("Batch: stopping after the run in progress");
    }

    void runTestCompare(java.util.List<String> args) {
        app.pyInputPath = null;
        app.programMenus.selectListedProgram("Java", "CompareHits.java");
        this.forcedArgs = args;
        this.runPython();
    }

    /** The test's report: both comparisons, in a window (also kept as <wav>-test.txt beside the WAV). */
    void showTestReport(Midi2WavTest t) {
        String text = t.text();
        File saved = null;
        try {
            File f = new File(new File(t.wav).getParentFile(), new File(t.wav).getName().replaceAll("(?i)\\.wav$", "") + "-test.txt");
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
            saved = f;
        } catch (Exception ignored) {
            // shown only
        }
        this.testNote("Test done" + (saved == null ? "" : ": report saved as " + saved.getPath()));
        javax.swing.JTextArea area = new javax.swing.JTextArea(text, 24, 80);
        area.setName("midi2wav-test-report");
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        JOptionPane.showMessageDialog(app, new JScrollPane(area), t.expect != null ? "SogniMusic test" : "Midi2Wav test", JOptionPane.PLAIN_MESSAGE);
        // SogniMusic's --vst: DrumMidi's MIDI of the track then plays through the VST (and vst_render's own test).
        if (t.vst && t.drums.length() > 0) this.toVst(t.drums, true);
    }

    /** The prompt library version the last run's sheet was stored as (File > Downloads: Delete with it). */
    long lastSheetVersion;

    String saveSheets(File dir, JavaRun.Result r, java.util.List<String> argv) {
        java.util.List<PromptSave.Pending> sheets = PromptSave.sheets(r);
        // A failed run's sheet is neither asked about nor saved: the log says so.
        String failed = PromptSave.failedNotes(r);
        if (sheets.isEmpty()) return failed;
        PromptSave.load(dir);
        StringBuilder out = new StringBuilder(failed);
        PromptVault vault = null;
        try {
            vault = PromptVault.open(dir);
        } catch (Exception ignored) {
            // store says why
        }
        for (PromptSave.Pending p : sheets) {
            String same = PromptSave.unchanged(vault, p.sheet, r, argv);
            String line;
            if (same != null) {
                line = PromptSave.unchangedNote(p.sheet, same);
            } else {
                PromptSave.Choice c = PromptSave.ask() ? SavePromptDialog.ask(app, vault, p.sheet, PromptSave.mediaOf(r, p.sheet)) : null;
                if (PromptSave.ask() && c == null) {
                    line = PromptSave.cancelledNote(p.sheet);
                } else {
                    PromptSave.Saved saved = c == null ? PromptSave.storeAsIs(dir, p, r, argv) : PromptSave.store(dir, p, r, argv, c);
                    line = saved.note;
                    if (saved.versionId != 0) this.lastSheetVersion = saved.versionId;
                    if (saved.bytes != null) {
                        File at = this.saveProgramFile(saved.fileName, saved.bytes);
                        line = line + "\n" + (at == null ? "Could not save " + saved.fileName : "Saved " + at.getPath());
                    }
                }
            }
            out.append(out.length() > 0 ? "\n" : "").append(line);
        }
        return out.toString();
    }

    /** The run's prompt sheets, and its MIDI or track when Drum Midi Settings says so, into the prompt library; a line for the log. */
    String keepInLibrary(String name, PythonRun.Result result, java.util.List<String> argv) {
        java.util.List<JavaRun.FileOut> files = new java.util.ArrayList<JavaRun.FileOut>();
        for (PythonRun.FileOut f : result.files) files.add(new JavaRun.FileOut(f.name, f.bytes));
        JavaRun.Result r = new JavaRun.Result(result.log, files, result.code);
        File dir = PromptDb.dir();
        String kept = this.saveSheets(dir, r, argv);
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
        // ImageUpscaler with --addtodb: the picture as a Reference file; --output_dir original: a copy beside the input picture.
        if ("ImageUpscaler.java".equals(name)) {
            // Its size: Add to DB asks about smaller pictures.
            if (r.code == 0) UpscaleCheck.remember(dir, r);
            String up = PromptKeep.keepUpscaled(dir, r, argv);
            if (up.length() > 0) kept = kept.length() > 0 ? kept + "\n" + up : up;
            String copy = this.copyToOriginal(r, argv);
            if (copy.length() > 0) kept = kept.length() > 0 ? kept + "\n" + copy : copy;
        }
        // Midi2Wav with --addtodb: the MIDI and the SoundFont as reference files, the WAV as result file
        // (once, when Save this run into DB is ticked too).
        if ("Midi2Wav.java".equals(name) && argv.contains("--addtodb") && !this.saveRun) {
            String run = PromptKeep.keepRun(dir, r, argv, name);
            if (run.length() > 0) kept = kept.length() > 0 ? kept + "\n" + run : run;
        }
        if (this.saveRun) {
            this.saveRun = false;
            String run = PromptKeep.keepRun(dir, r, argv, name);
            if (run.length() > 0) kept = kept.length() > 0 ? kept + "\n" + run : run;
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
        // The MIDI as a file for its items (the MIDI editor, Midi2Wav, vst_render, the default SoundFont).
        File file = new File(new File(PromptDb.dir(), "pyjav-in"), midiName.replace('/', '_').replace('\\', '_'));
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), midi);
        } catch (Exception ex) {
            file = null;
        }
        javax.sound.sampled.Clip clip = null;
        try {
            while (true) {
                boolean playing = clip != null && clip.isRunning();
                // Add to composer stack: the file set's patterns and fills, as blocks to compose with.
                // Then favourites, group, tags, category, playlist and the MIDI's own items.
                FileFavourites.load(PromptDb.dir());
                java.util.List<Object> opts = new java.util.ArrayList<Object>();
                opts.add(playing ? "Stop" : "Play");
                opts.add("Add to composer stack");
                java.util.List<String> more = file == null ? new java.util.ArrayList<String>() : TagDialogs.withUseLast(midiItems(MarkFile.isFavourite(file.getName(), file.length())), ReadyRecent.MIDI);
                opts.addAll(more);
                opts.add("Close");
                Object[] options = opts.toArray();
                int ans = JOptionPane.showOptionDialog(app, midiName + " (" + length + ") is imported as a file set.\n\n"
                    + "Play sounds it with Pulsekit's kit (your imported SoundFont, if any).", "MIDI ready",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
                if (ans == 1) {
                    String set = app.importLibrary.lastPackedSet;
                    if (set == null) {
                        app.setNow("No file set of " + midiName + " to put on the stack");
                        break;
                    }
                    Composer c = app.composerPage.composer();
                    int added = c.addFileSet(set, app.learned, app.learnedFills);
                    c.save(PromptDb.dir());
                    app.setNow(Composer.stackedNote(midiName, added));
                    break;
                }
                if (ans >= 2 && ans < 2 + more.size()) {
                    if (clip != null && clip.isRunning()) clip.stop();
                    if (this.midiItem(file, midiName, more.get(ans - 2))) break;
                    continue;
                }
                if (ans != 0) break;
                if (playing) {
                    clip.stop();
                    continue;
                }
                try {
                    if (clip == null) {
                        clip = javax.sound.sampled.AudioSystem.getClip();
                        clip.open(new javax.sound.sampled.AudioFormat(22050f, 16, 1, true, false), pcmBytes, 0, pcmBytes.length);
                        SoundOut.volume(clip);
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

    /** MIDI ready's items after Play and Add to composer stack: favourites, group, tags, category, playlist, the MIDI's own. */
    static java.util.List<String> midiItems(boolean favourite) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add(favourite ? FileFavourites.REMOVE_SHORT : FileFavourites.ADD_SHORT);
        out.add(FileGroups.ADD);
        out.add(MediaTags.ADD_TAG);
        out.add(MediaTags.ADD_CATEGORY);
        out.add(Playlist.ADD);
        for (String k : MediaDir.kindItems("x.mid", true)) if (!MediaDir.TO_COMPOSER.equals(k)) out.add(k);
        return out;
    }

    /** One of MIDI ready's items; true when it leaves MIDI ready (a program opened, the editor). */
    boolean midiItem(File file, String name, String item) {
        String kind = ReadyRecent.MIDI;
        if (item.equals(FileFavourites.ADD_SHORT) || item.equals(FileFavourites.REMOVE_SHORT)) GroupMenus.toggleFavourite(app, file);
        else if (item.equals(FileGroups.ADD)) GroupMenus.ask(app, GroupMenus.keys(file), name, kind);
        else if (item.equals(MediaTags.ADD_TAG)) TagDialogs.tags(app, app, file.getName(), file.length(), null, kind);
        else if (item.startsWith(ReadyRecent.USE_LAST)) TagDialogs.useLast(app, file.getName(), file.length(), kind);
        else if (item.equals(ReadyRecent.USE_ALL)) TagDialogs.useAll(app, file, kind);
        else if (item.equals(MediaTags.ADD_CATEGORY)) TagDialogs.category(app, app, file.getName(), file.length(), null, kind);
        else if (item.equals(Playlist.ADD)) PlaylistWindow.askAdd(app, file.getAbsolutePath(), name);
        else if (item.equals(MediaDir.PLAY_SF)) MediaBrowser.playDefault(app, file, name);
        else if (item.equals(MediaDir.TO_FILE_SET)) MediaBrowser.toFileSet(app, file, name);
        else if (item.equals(MediaDir.MIDI_EDIT)) {
            // Save keeps the edited MIDI in the program files folder as <name>-edited.mid.
            final String edited = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-edited.mid";
            try {
                MidiEditScreen.openFile(app, name, Files.readAllBytes(file.toPath()), m -> {
                    File saved = this.saveProgramFile(edited, m);
                    return saved == null ? edited : saved.getPath();
                });
            } catch (Exception ex) {
                app.setNow("Could not read " + name);
            }
            return true;
        } else if (item.equals(MediaDir.RENDER_SF2) || item.equals(MediaDir.RENDER_PLUGIN)) {
            app.showView("py");
            if (item.equals(MediaDir.RENDER_PLUGIN)) app.programMenus.selectListedProgram("Python", "vst_render.py");
            else app.programMenus.selectListedProgram("Java", "Midi2Wav.java");
            this.setInputFile(file);
            SwingUtilities.invokeLater(this::openParams);
            return true;
        }
        return false;
    }

    /**
     * After a run that made a video: Play opens a player page in the browser (Java has no video
     * decoder) with the same controls as on the phone: Play/Pause, Stop, Mute and a volume slider.
     * Open uses the system player; Close leaves it saved.
     */
    void offerVideo(File video, int count) {
        String more = count > 1 ? "\n" + (count - 1) + " more " + (count == 2 ? "video is" : "videos are") + " saved beside it." : "";
        // With VLC the video plays here (right click: Extract frame); without it, in the browser.
        boolean vlc = VlcPlayer.available();
        int ans;
        // Add to favourites, Add to group, the tags, the category and Add to playlist come back to the question.
        while (true) {
            FileFavourites.load(PromptDb.dir());
            java.util.List<String> videoItems = TagDialogs.withUseLast(java.util.Arrays.asList("Play", "Open", MarkFile.isFavourite(video.getName(), video.length()) ? FileFavourites.REMOVE_SHORT : FileFavourites.ADD_SHORT, FileGroups.ADD,
                MediaTags.ADD_TAG, MediaTags.ADD_CATEGORY, Playlist.ADD, MediaDir.SAVE_REF, MediaDir.SAVE_RESULT, MediaDir.SAVE_DISK, "Close"), ReadyRecent.VIDEO);
            Object[] options = videoItems.toArray();
            ans = JOptionPane.showOptionDialog(app, video.getName() + " (" + (video.length() / 1024) + " KB) is saved in " + video.getParent() + "." + more
                + (vlc ? "\n\nPlay plays it here; a right click on the picture pauses it and has Extract frame."
                    : "\n\nPlay opens it in the browser with Play/Pause, Stop, Mute and volume."), "Video ready",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            String it = ans < 0 ? "Close" : videoItems.get(ans);
            if (ans == 2) GroupMenus.toggleFavourite(app, video);
            else if (it.equals(FileGroups.ADD)) GroupMenus.ask(app, GroupMenus.keys(video), video.getName(), ReadyRecent.VIDEO);
            else if (it.equals(MediaTags.ADD_TAG)) TagDialogs.tags(app, app, video.getName(), video.length(), null, ReadyRecent.VIDEO);
            else if (it.startsWith(ReadyRecent.USE_LAST)) TagDialogs.useLast(app, video.getName(), video.length(), ReadyRecent.VIDEO);
            else if (it.equals(ReadyRecent.USE_ALL)) TagDialogs.useAll(app, video, ReadyRecent.VIDEO);
            else if (it.equals(MediaTags.ADD_CATEGORY)) TagDialogs.category(app, app, video.getName(), video.length(), null, ReadyRecent.VIDEO);
            else if (java.util.Arrays.asList(MediaDir.READY_SAVES).contains(it)) this.saveReady(video, it);
            else if (it.equals(Playlist.ADD)) PlaylistWindow.askAdd(app, video.getAbsolutePath(), video.getName());
            else break;
        }
        try {
            if (ans == 0 && vlc) {
                try {
                    app.promptDb.vlcPreview(video.getName(), video);
                    return;
                } catch (Exception ex) {
                    app.setNow(ex.getMessage() != null ? ex.getMessage() : "VLC could not play " + video.getName());
                }
            }
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
            + " <span id=\"time\">00:00 / 00:00</span></div>\n"
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
            + "function clock(t){t=Math.max(0,Math.floor(t||0));var s=t%60,m=Math.floor(t/60);return (m<10?'0':'')+m+':'+(s<10?'0':'')+s;}\n"
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
        // Favourites, group, tags, category, playlist and Convert into WAV come back to the question.
        // A WAV (Convert into WAV's) has Convert hits into MIDI instead: DrumMidi_CRT on it, then MIDI ready.
        String name = audio.getName();
        String kind = ReadyRecent.kind(name);
        boolean mp3 = name.toLowerCase().endsWith(".mp3");
        boolean wav = name.toLowerCase().matches(".+\\.(wav|wave)");
        String it;
        File madeWav = null;
        while (true) {
            FileFavourites.load(PromptDb.dir());
            java.util.List<String> items = TagDialogs.withUseLast(java.util.Arrays.asList("Make drum MIDI", "Play", MarkFile.isFavourite(name, audio.length()) ? FileFavourites.REMOVE_SHORT : FileFavourites.ADD_SHORT,
                FileGroups.ADD, MediaDir.SAVE_REF, MediaDir.SAVE_RESULT, MediaDir.SAVE_DISK, MediaTags.ADD_TAG, MediaTags.ADD_CATEGORY, Playlist.ADD, wav ? HITS_TO_MIDI : TO_WAV, "Close"), kind);
            Object[] options = items.toArray();
            int ans = JOptionPane.showOptionDialog(app, name + " is saved in " + audio.getParent() + ".\n\n"
                + (wav ? "Convert hits into MIDI runs DrumMidi_CRT on it, then shows the MIDI it made."
                    : "Make drum MIDI runs DrumMidi_CRT on it and imports the drums as a file set."), mp3 ? "MP3 ready" : wav ? "WAV ready" : "Audio ready",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            it = ans < 0 ? "Close" : items.get(ans);
            if (ans == 2) GroupMenus.toggleFavourite(app, audio);
            else if (it.equals(FileGroups.ADD)) GroupMenus.ask(app, GroupMenus.keys(audio), name, kind);
            else if (java.util.Arrays.asList(MediaDir.READY_SAVES).contains(it)) this.saveReady(audio, it);
            else if (it.equals(MediaTags.ADD_TAG)) TagDialogs.tags(app, app, name, audio.length(), null, kind);
            else if (it.startsWith(ReadyRecent.USE_LAST)) TagDialogs.useLast(app, name, audio.length(), kind);
            else if (it.equals(ReadyRecent.USE_ALL)) TagDialogs.useAll(app, audio, kind);
            else if (it.equals(MediaTags.ADD_CATEGORY)) TagDialogs.category(app, app, name, audio.length(), null, kind);
            else if (it.equals(Playlist.ADD)) PlaylistWindow.askAdd(app, audio.getAbsolutePath(), name);
            else if (it.equals(TO_WAV)) {
                if (!mp3) app.setNow(name + " is a WAV already");
                else {
                    File w = this.asWav(audio);
                    if (w != audio) {
                        app.setNow("Saved " + w.getName() + " beside " + name);
                        // WAV ready follows on it.
                        madeWav = w;
                        break;
                    }
                }
            } else break;
        }
        if (madeWav != null) {
            this.offerAudio(madeWav);
        } else if (it.equals("Make drum MIDI") || it.equals(HITS_TO_MIDI)) {
            // DrumMidi_CRT.jar carries an MP3 decoder, so the audio is given as it is.
            this.audioInputPath = audio.getAbsolutePath();
            app.programMenus.selectListedProgram("Java", "DrumMidi_CRT.jar");
            if (it.equals(HITS_TO_MIDI)) {
                // This WAV, whatever input the program had before; MIDI ready when it is done.
                this.setInputFile(audio);
                this.hitsAsked = true;
            }
            this.runPython();
        } else if (it.equals("Play")) {
            if (SoundClip.plays(audio)) SoundClip.window(app, audio);
            else try {
                java.awt.Desktop.getDesktop().open(audio);
            } catch (Exception ex) {
                app.setNow("Could not open a player for " + audio.getName());
            }
        }
    }

    /** The last Add to DB's line (for the tests). */
    String lastDbAdd;

    String lastDbAddText() {
        return this.lastDbAdd;
    }

    /** Where Save into disk… opens: the folder saved into last. */
    static final String READY_SAVE_DIR = "readySaveDir";
    /** The tests' answer for Save into disk… (the file to save as); null asks. */
    static java.util.function.Function<String, File> saveAsFor;

    /**
     * The ready screens' saves (Picture, Video, MP3 and WAV ready): into the prompt library as a
     * reference or result file (then which group it goes in), or into a folder on disk (the folder
     * saved into last offered first).
     */
    void saveReady(File f, String item) {
        if (MediaDir.SAVE_DISK.equals(item)) {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(PyJav.class);
            File to;
            if (saveAsFor != null) to = saveAsFor.apply(f.getName());
            else {
                String last = prefs.get(READY_SAVE_DIR, "");
                JFileChooser chooser = new JFileChooser(last.length() > 0 && new File(last).isDirectory() ? new File(last) : null);
                chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), f.getName()));
                to = chooser.showSaveDialog(app) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
            }
            if (to == null) return;
            try {
                Files.copy(f.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (to.getAbsoluteFile().getParentFile() != null) prefs.put(READY_SAVE_DIR, to.getAbsoluteFile().getParentFile().getAbsolutePath());
                this.lastDbAdd = "Saved " + to.getName() + " in " + to.getAbsoluteFile().getParent();
            } catch (Exception ex) {
                this.lastDbAdd = "Could not save " + f.getName() + ": " + ex.getMessage();
            }
            app.setNow(this.lastDbAdd);
            return;
        }
        byte[] bytes = null;
        try {
            if (!MediaDir.tooBig(f.length())) bytes = Files.readAllBytes(f.toPath());
        } catch (Exception ignored) {
            // said below
        }
        String said = bytes == null && MediaDir.tooBig(f.length()) ? f.getName() + " is over the library's 128 MB, so it is not in the DB"
            : MediaDir.addToDb(PromptDb.dir(), f.getName(), bytes, MediaDir.SAVE_RESULT.equals(item));
        this.lastDbAdd = said;
        app.setNow(said);
        // Its group asked for, unless it is in one already (Add to group on the ready screen).
        FileGroups.load(PromptDb.dir());
        if (said.contains(" is in the DB as a ") && FileGroups.groupsOf(GroupMenus.keys(f)).isEmpty()) GroupMenus.afterAdd(app, f.getName(), f.length());
    }

    /** WAV / MP3 ready's Add to DB: as a result or a reference file in the prompt library, then which group it goes in. */
    void addAudioToDb(File audio) {
        Object[] options = {MediaDir.DB_KINDS[0], MediaDir.DB_KINDS[1], "Cancel"};
        int ans = JOptionPane.showOptionDialog(app, "Add " + audio.getName() + " to the prompt library as a result file (what a prompt or run made) or a reference file?",
            MediaDir.ADD_TO_DB, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (ans != 0 && ans != 1) return;
        byte[] bytes = null;
        try {
            if (!MediaDir.tooBig(audio.length())) bytes = Files.readAllBytes(audio.toPath());
        } catch (Exception ignored) {
            // said below
        }
        String said = bytes == null && MediaDir.tooBig(audio.length()) ? audio.getName() + " is over the library's 128 MB, so it is not in the DB"
            : MediaDir.addToDb(PromptDb.dir(), audio.getName(), bytes, ans == 0);
        this.lastDbAdd = said;
        app.setNow(said);
        if (said.contains(" is in the DB as a ")) GroupMenus.afterAdd(app, audio.getName(), audio.length());
    }

    /** WAV ready's: DrumMidi_CRT on the WAV, then MIDI ready on the MIDI it made. */
    static final String HITS_TO_MIDI = "Convert hits into MIDI";
    /** Convert hits into MIDI asked: MIDI ready follows the DrumMidi_CRT run. */
    boolean hitsAsked;

    static final String TO_WAV = "Convert into WAV";

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
    /** The program files folder from Drum Midi Settings, else ~/Downloads (or ~/.pulsekit). */
    File programFolder() {
        File home = new File(System.getProperty("user.home", "."));
        File dir = ProgramFolder.get() != null ? new File(ProgramFolder.get()) : new File(home, "Downloads");
        if (ProgramFolder.get() == null && !dir.isDirectory()) dir = new File(home, ".pulsekit");
        return dir;
    }

    /** SogniMusic --outdir: the folder the run going on (or ended last) keeps its files in; null: the program files folder. */
    volatile File runOutDir;

    File saveProgramFile(String name, byte[] data) {
        return this.saveProgramFile(name, data, null);
    }

    /** As above, into `into` (null: the program files folder). */
    File saveProgramFile(String name, byte[] data, File into) {
        try {
            File dir = into != null ? into : this.programFolder();
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
    }

    /**
     * Recent's menu: each program once, the one run last first. A click runs it (without parameters
     * when such a run is remembered, else with its last ones); a right click lists its runs.
     */
    javax.swing.JPopupMenu recentMenu() {
        this.reloadPyRecent(0);
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setName("pyjav-recent-menu");
        java.util.List<String> programs = PyJavRecent.programs(this.pyRecentItems);
        if (programs.isEmpty()) {
            javax.swing.JMenuItem none = new javax.swing.JMenuItem("No recent programs");
            none.setEnabled(false);
            menu.add(none);
        }
        ProgramStars.load(PromptDb.dir());
        for (final String name : programs) {
            final javax.swing.JMenuItem item = new javax.swing.JMenuItem(ProgramStars.label(name));
            item.setName("pyjav-recent:" + name);
            item.setToolTipText("A right click lists its recent parameters");
            item.addActionListener(e -> this.loadRecent(PyJavRecent.tapped(this.pyRecentItems, name)));
            item.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (!SwingUtilities.isRightMouseButton(e)) return;
                    menu.setVisible(false);
                    runsMenu(name).show(pyRecent, 0, pyRecent.getHeight());
                }
            });
            menu.add(item);
        }
        MenuCancel.add(menu);
        return menu;
    }

    /** A program's runs (the last first); a click loads it with those parameters. */
    javax.swing.JPopupMenu runsMenu(String name) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setName("pyjav-recent-runs");
        java.util.List<PyJavRecent.Item> runs = PyJavRecent.runsOf(this.pyRecentItems, name);
        for (int i = 0; i < runs.size(); i++) {
            final PyJavRecent.Item run = runs.get(i);
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(PyJavRecent.argsLabel(run));
            item.setName("pyjav-recent-run:" + i);
            item.addActionListener(e -> this.loadRecent(run));
            menu.add(item);
        }
        // The star, last: bright when the program is starred; a click stars it or takes the star off.
        final boolean starred = ProgramStars.is(name);
        javax.swing.JMenuItem star = new javax.swing.JMenuItem(starred ? "\u2605 Starred" : "\u2606 Star " + name);
        star.setName("pyjav-recent-star");
        star.addActionListener(e -> app.setNow(name + (ProgramStars.toggle(name) ? " is starred" : " is no longer starred")));
        menu.addSeparator();
        menu.add(star);
        MenuCancel.add(menu);
        return menu;
    }

    /** Recent: the run picked is loaded and run. */
    /** Select program: the program and its parameters are loaded; Run starts it. */
    void loadRecent(PyJavRecent.Item item) {
        if (item == null) return;
        this.applyPyRecent(item);
    }

    void applyPyRecent(PyJavRecent.Item item) {
        if (item == null) return;
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
        // Its run still going (another program looked at meanwhile): shown again.
        this.showRunning(item.name);
    }

    void showPyHint(String status) {
        this.pyHintPlain = status == null ? "" : status;
        // Another program loaded: Return (to the Media browser or the Prompts page) was for the one before.
        if (this.returnTo != null && !String.valueOf(app.pyName).equals(this.returnFor)) this.setReturn(null);
        // The audio input carries over to a program that takes a sound (not to SogniPedit, which
        // would name its picture after it).
        if (app.pyInputPath == null && this.audioInputPath != null && new File(this.audioInputPath).isFile() && PyJavHints.takesAudio(app.pyName, this.pyHintPlain)) {
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
    /** This run's "Save this run into DB" (Params). */
    boolean saveRun;

    /** What Return opens (the Media browser that started ImageUpscaler), or null: the button is hidden. */
    Runnable returnTo;
    javax.swing.JComponent pyReturn;
    String returnFor;

    void setReturn(Runnable back) {
        this.setReturn("Return to Media browser", back);
    }

    /** Return: back to what opened this program, the button saying where ("Return to Prompts"). */
    void setReturn(String label, Runnable back) {
        this.returnTo = back;
        // It belongs to the program loaded now: another one loaded (from Recent, a script) drops it.
        this.returnFor = back == null ? null : app.pyName;
        if (this.pyReturn != null) {
            this.pyReturn.setVisible(back != null);
            if (label != null && this.pyReturn instanceof javax.swing.AbstractButton) ((javax.swing.AbstractButton) this.pyReturn).setText(label);
        }
    }

    void doReturn() {
        Runnable back = this.returnTo;
        this.setReturn(null);
        if (back != null) back.run();
    }

    /** ImageUpscaler with --output_dir original: the picture it made, copied into the input picture's folder; a line for the log, or "". */
    String copyToOriginal(JavaRun.Result r, java.util.List<String> argv) {
        int at = argv == null ? -1 : argv.indexOf("--output_dir");
        if (at < 0 || at + 1 >= argv.size() || !"original".equalsIgnoreCase(argv.get(at + 1).trim())) return "";
        JavaRun.FileOut made = PromptKeep.made(r);
        if (made == null) return "";
        File in = app.pyInputPath == null || app.pyInputPath.isEmpty() ? null : new File(app.pyInputPath);
        File folder = in == null ? null : in.getAbsoluteFile().getParentFile();
        if (folder == null || !folder.isDirectory()) return "Output folder: the input picture's folder is not known; " + made.name + " is in the program files folder";
        String stem = made.name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        String ext = made.name.substring(stem.length());
        File f = new File(folder, made.name);
        for (int n = 1; f.exists(); n++) f = new File(folder, stem + " (" + n + ")" + ext);
        try {
            Files.write(f.toPath(), made.bytes);
            return "Output folder: copy saved as " + f.getPath();
        } catch (Exception ex) {
            return "Output folder: could not save a copy in " + folder.getPath() + (ex.getMessage() == null ? "" : " (" + ex.getMessage() + ")");
        }
    }

    /** True when Run must not go on: a run stores in the prompt library and the answer to "Are you sure…" was No. */
    boolean mustWait(String program, boolean running) {
        // Params always open; Run asks first only while a run stores in the prompt library.
        if (!running || !PendingOps.mustAsk(program)) return false;
        String text = PendingOps.askText(PendingOps.storingCount());
        int ans = JOptionPane.showConfirmDialog(app, text, "Run again?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ans == JOptionPane.YES_OPTION) return false;
        app.setNow("Not run: the current task is not complete");
        return true;
    }

    /** ImageUpscaler's input picture (the first argument that is a picture file): its --aspect suggestion, or null. */
    String[] aspectTip() {
        if (this.pyExtra == null) return null;
        for (String a : JavaRun.split(this.pyExtra.getText())) {
            if (a.startsWith("--")) continue;
            File f = new File(a);
            if (!f.isFile() || MediaDir.kind(f.getName()) != MediaDir.PICTURE) continue;
            int[] wh = UpscaleCheck.size(MediaDir.head(f));
            return wh == null ? null : UpscaleCheck.suggestAspect(wh[0], wh[1]);
        }
        return null;
    }

    void openParams() {
        final String program = app.pyName == null || app.pyName.isEmpty() ? "program" : app.pyName;
        if (this.mustWait(program, false)) return;
        final java.util.List<ProgramParams.Param> ps = ProgramParams.parse(this.programText());
        // vst_render: its map files and the kit names given before, to pick from.
        for (ProgramParams.Param p : ps) {
            if (p.token.equals("--map") || p.token.equals("--map2") || p.token.equals("--map3") || p.token.equals("--map4")) {
                String[][] maps = VstLists.mapChoices();
                if (maps[0].length > 0) {
                    p.choices = maps[0];
                    p.choiceValues = maps[1];
                }
            } else if (p.token.startsWith("--kit_name")) {
                String[] kits = VstLists.kitNames();
                if (kits.length > 0) p.choices = kits;
            } else if (p.token.equals("--aspect") && "ImageUpscaler.java".equals(program)) {
                // ImageUpscaler: the shape suggested for the input picture, first in the list.
                String[] tip = this.aspectTip();
                if (tip != null && p.choices != null) {
                    java.util.List<String> labels = new java.util.ArrayList<String>(java.util.Arrays.asList(p.choices));
                    java.util.List<String> values = new java.util.ArrayList<String>(java.util.Arrays.asList(p.choiceValues != null ? p.choiceValues : p.choices));
                    int at = values.indexOf(tip[0]);
                    if (at >= 0) {
                        labels.remove(at);
                        values.remove(at);
                    }
                    labels.add(0, tip[0] + " (suggested)");
                    values.add(0, tip[0]);
                    p.choices = labels.toArray(new String[0]);
                    p.choiceValues = values.toArray(new String[0]);
                    p.hint = "suggested " + tip[0] + ": " + tip[1];
                }
            }
        }
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
        // Rows left out of the run (their Use box unticked) keep their files beside the Params.
        final java.util.Set<String> off = new java.util.LinkedHashSet<String>();
        java.util.Map<String, String> offValues = ProgramParams.offValues(ps, this.loadParams(program + ProgramParams.OFF_KEY));
        for (int i = 0; i < ps.size(); i++) {
            String kept = offValues.get(ps.get(i).token);
            if (kept == null || !ProgramParams.canTurnOff(ps.get(i))) continue;
            values[i] = kept;
            off.add(ps.get(i).token);
        }
        final javax.swing.JCheckBox[] uses = new javax.swing.JCheckBox[ps.size()];
        final String[] before = values.clone();
        // The file rows' labels: a video picked for a picture row fills it (and an empty second picture row) with its frames.
        final JLabel[] fileLabels = new JLabel[ps.size()];
        final JTextField[] fields = new JTextField[ps.size()];
        // A prompt or system text: a wrapping area of a few lines that scrolls.
        final javax.swing.JTextArea[] areas = new javax.swing.JTextArea[ps.size()];
        // A definitions file (ParamDefs) fills several rows: the Params' rows kept for it.
        this.shownParams = ps;
        this.shownFields = fields;
        this.shownAreas = areas;
        // On/off switches (DrumMidi's --no-hpss and --no-cymbals) are checkboxes.
        final javax.swing.JCheckBox[] checks = new javax.swing.JCheckBox[ps.size()];
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        // Where each row's label and field start in the form (vst_render's rows then go onto its tabs).
        final int[] rowStarts = new int[ps.size()];
        for (int i = 0; i < ps.size(); i++) {
            final ProgramParams.Param p = ps.get(i);
            final int index = i;
            rowStarts[i] = form.getComponentCount();
            JLabel rowLabel = new JLabel(p.flag ? p.label + "  " + p.token : p.label + (p.optional ? "  (optional)" : ""));
            if (ParamsClip.has(program, p)) {
                // The prompt's and the LoRAs' Copy, Paste and Send to another Sogni program, after the label.
                JPanel withClip = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                withClip.add(rowLabel);
                for (JButton b : this.clipButtons(program, p.token, () -> areas[index] != null ? areas[index] : fields[index])) withClip.add(b);
                form.add(withClip);
            } else {
                form.add(rowLabel);
            }
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
            if (p.dropdown && p.choices != null && p.choices.length > 0) {
                form.add(this.dropdown(p, values[i], fields, i));
                continue;
            }
            if (p.dir && p.typed) {
                // A folder as a path to write on (vst_render's --zip_outdir): Browse puts the picked
                // folder's full path in the field; % codes and the like can then be added to it.
                JPanel row = new JPanel(new BorderLayout(6, 0));
                final javax.swing.JTextField path = new javax.swing.JTextField(values[i], 30);
                path.setName("params-dir-text:" + p.token);
                path.setToolTipText(p.hint);
                path.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    void keep() {
                        values[index] = path.getText().trim();
                    }

                    public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        keep();
                    }

                    public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        keep();
                    }

                    public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        keep();
                    }
                });
                JButton browse = new JButton("Browse");
                browse.setName("params-dir:" + p.token);
                browse.addActionListener(e -> {
                    String now = path.getText().trim();
                    File start = now.length() > 0 && !now.contains("%") ? new File(now) : null;
                    JFileChooser chooser = new JFileChooser(start);
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setDialogTitle("Directory");
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
                    path.setText(chooser.getSelectedFile().getAbsolutePath());
                });
                row.add(browse, BorderLayout.WEST);
                row.add(path, BorderLayout.CENTER);
                JButton clear = new JButton("Clear");
                clear.setName("params-clear:" + p.token);
                clear.addActionListener(e -> path.setText(""));
                row.add(clear, BorderLayout.EAST);
                form.add(this.withUse(p, i, row, path, uses, off));
                continue;
            }
            if (p.dir) {
                // A folder (MediaBrowser's directory): Browse picks it and opens the Media browser on it.
                JPanel row = new JPanel(new BorderLayout(6, 0));
                // Empty: the folder the Media browser last opened.
                // (A folder switch, as Midi2HydroPattern's --dir, stays empty until one is picked.)
                if (values[i].length() == 0 && !p.flag) {
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
                    if (!p.flag) app.mediaBrowser.open(picked);
                });
                if (p.pls) {
                    // A playlist (.pls) in place of the folder: one of Pulsekit's playlists.
                    JPanel picks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                    picks.add(browse);
                    picks.add(playlistButton(p, values, index, chosen));
                    row.add(picks, BorderLayout.WEST);
                } else row.add(browse, BorderLayout.WEST);
                row.add(chosen, BorderLayout.CENTER);
                if (p.flag) {
                    // Clear: a folder switch (CutWav's and SplitWav's --dir) goes back to unset, so one file is processed.
                    JButton clear = new JButton("Clear");
                    clear.setName("params-clear:" + p.token);
                    clear.addActionListener(e -> {
                        values[index] = "";
                        chosen.setText(MediaDir.label(values[index]));
                    });
                    row.add(clear, BorderLayout.EAST);
                }
                form.add(this.withUse(p, i, row, chosen, uses, off));
                continue;
            }
            if (p.isFile()) {
                JPanel row = new JPanel(new BorderLayout(6, 0));
                final JLabel chosen = new JLabel(values[i].length() == 0 ? "None" : new File(values[i]).getName());
                chosen.setName("params-chosen:" + p.token);
                fileLabels[i] = chosen;
                final JButton[] rowEdit = new JButton[1];
                JButton pick = new JButton(p.plugin ? "Choose plugin" : "any".equals(p.ext) ? "Choose file" : "Choose ." + p.ext);
                pick.setName("params-file:" + p.token);
                // A VST plugin: the system's chooser in the VST folders; a .vst3 bundle folder can be picked as it is.
                if (p.plugin) pick.addActionListener(e -> {
                    // Its own plugin's folder, else the folder a plugin was last picked from, else the usual VST folder.
                    File start = values[index].length() > 0 ? new File(values[index]).getParentFile() : lastVstFolder();
                    JFileChooser chooser = new JFileChooser(start);
                    chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                    chooser.setDialogTitle("Plugin (.dll, .vst3, .clap, .lv2) or SoundFont (.sf2, .sfz)");
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
                    values[index] = chooser.getSelectedFile().getAbsolutePath();
                    chosen.setText(chooser.getSelectedFile().getName());
                    rememberVstFolder(chooser.getSelectedFile().getParentFile());
                    this.editRow = rowEdit[0];
                    this.dllNotice(pick, chooser.getSelectedFile());
                    this.editRow = null;
                });
                // Choose file: the Media browser (Recent MB folder, Download, favourites); Other… is the system's chooser.
                // Each row's Choose file opens where its own last file was (Reference image 1 and 2 apart).
                else pick.addActionListener(e -> app.mediaBrowser.chooseFor(p.token, f -> this.assignFile(ps, values, index, fileLabels, f.getAbsolutePath(), null), () -> {
                    JFileChooser chooser = new JFileChooser(values[index].length() > 0 ? new File(values[index]).getParentFile() : null);
                    String[] exts = "mid".equals(p.ext) ? new String[] {"mid", "midi", "h2song", "h2pattern", "rpp"} : "wav".equals(p.ext) ? new String[] {"wav", "wave"}
                        : "sf2".equals(p.ext) ? new String[] {"sf2", "sfz", "zip", "gz", "tgz", "tar"}
                        : p.pls ? new String[] {p.ext, "pls"} : new String[] {p.ext};
                    if (!"any".equals(p.ext)) chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("." + p.ext + " files", exts));
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
                    this.assignFile(ps, values, index, fileLabels, chooser.getSelectedFile().getAbsolutePath(), null);
                }, ProgramParams.previewsPicture(p)));
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
                            this.assignFile(ps, values, index, fileLabels, file.getAbsolutePath(), picked + " \u00b7 from DB");
                        }
                    }));
                    JPanel picks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                    picks.add(pick);
                    picks.add(db);
                    // A picture row: Recent, the reference pictures used last as small thumbnails.
                    if (ProgramParams.previewsPicture(p)) {
                        JButton recent = new JButton(RecentRefs.RECENT);
                        recent.setName("params-recent:" + p.token);
                        recent.addActionListener(e -> this.recentRefs(recent, ps, values, index, fileLabels));
                        picks.add(recent);
                    }
                    picks.add(this.clearButton(p, values, index, chosen));
                    row.add(picks, BorderLayout.WEST);
                } else {
                    JPanel picks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                    picks.add(pick);
                    if (p.pls) picks.add(playlistButton(p, values, index, chosen));
                    if (p.plugin) {
                        // Plugins list: the installed ones, favourites first to hand; test one, or load its kit.
                        JButton plugins = new JButton("Plugins list");
                        plugins.setName("params-plugins:" + p.token);
                        plugins.addActionListener(e -> {
                            File picked = PluginsList.show(this, SwingUtilities.getWindowAncestor(plugins), values[index]);
                            if (picked == null) return;
                            values[index] = picked.getAbsolutePath();
                            chosen.setText(picked.getName());
                            rememberVstFolder(picked.getParentFile());
                            this.editRow = rowEdit[0];
                            this.dllNotice(plugins, picked);
                            this.editRow = null;
                        });
                        picks.add(plugins);
                        // Edit settings: the chosen plugin's own window (load a kit or patch, close it): kept for its runs,
                        // as the Plugins list's Edit settings does, without going there.
                        JButton editSettings = new JButton("Edit settings");
                        rowEdit[0] = editSettings;
                        editSettings.setName("params-plugin-edit:" + p.token);
                        editSettings.setToolTipText("Opens the plugin's window: its kit or patch is kept for vst_render's runs");
                        // Stop timer: pressed when the kit has loaded (its load time is how long a test waits for it).
                        final JButton stopTimer = PluginsList.stopTimerButton("params-plugin-timer:" + p.token);
                        editSettings.addActionListener(e -> {
                            if (values[index].length() == 0 || !new File(values[index]).exists()) {
                                app.setNow("Choose a plugin first (" + p.token + ")");
                                return;
                            }
                            File plug = new File(values[index]);
                            if (DllInfo.BRIDGED.equals(DllInfo.problem(plug)) || DllInfo.isLv2(plug)) carlaRack();
                            editSettings.setEnabled(false);
                            stopTimer.setEnabled(true);
                            stopTimer.setText("Stop timer");
                            app.setNow(PluginsList.testing(plug, true));
                            final javax.swing.Timer tick = PluginsList.ticker(pr -> app.setNow("Edit settings \u00b7 " + plug.getName() + ": " + pr));
                            PluginsList.test(this, plug, true, log -> {
                                tick.stop();
                                editSettings.setEnabled(true);
                                stopTimer.setEnabled(false);
                                app.setNow(PluginsList.summary(log));
                                // What it came to, always in a window: why it did not open (a 32-bit plugin without Carla,
                                // not a plugin...), else what was saved (and where Carla's project is, for a 32-bit one).
                                String failed = PluginsList.failed(log);
                                pluginTestSaid = failed == null ? "" : failed;
                                if (!pluginTestQuiet)
                                    JOptionPane.showMessageDialog(editSettings, PluginsList.result(plug, true, log), "Edit settings \u00b7 " + plug.getName(),
                                        failed != null ? JOptionPane.WARNING_MESSAGE : JOptionPane.PLAIN_MESSAGE);
                            });
                        });
                        picks.add(editSettings);
                        picks.add(stopTimer);
                        // Test: the plugin plays the test beat (its own window not opened); says whether it plays drums.
                        JButton test = new JButton("Test");
                        test.setName("params-plugin-test:" + p.token);
                        test.setToolTipText("Plays the test beat through this plugin and says whether its drums are heard");
                        test.addActionListener(e -> this.testPluginRow(test, ps, values, p.token, values[index]));
                        picks.add(test);
                    }
                    picks.add(this.clearButton(p, values, index, chosen));
                    row.add(picks, BorderLayout.WEST);
                }
                row.add(chosen, BorderLayout.CENTER);
                // Plugin 1 to 4: a plugin dragged from Favourites or Recently used lands here.
                if (p.plugin && p.token.matches("--plugin\\d?")) {
                    javax.swing.TransferHandler drop = this.pluginDrop(ps, values, fileLabels, uses, p.token);
                    row.setTransferHandler(drop);
                    chosen.setTransferHandler(drop);
                }
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
                if (ProgramParams.previewsPicture(p)) {
                    // Preview: a thumbnail of the picked picture pops up; greyed while no picture is picked.
                    final JButton preview = new JButton("Preview");
                    preview.setName("params-preview:" + p.token);
                    preview.setEnabled(ProgramParams.isPicture(values[i]));
                    chosen.addPropertyChangeListener("text", e -> preview.setEnabled(ProgramParams.isPicture(values[index])));
                    preview.addActionListener(e -> this.previewPicture(values[index]));
                    // Crop: the picture with the crop frame on; its menu has Use the crop in this row (upscale asked when needed).
                    final JButton crop = new JButton(CropBox.CROP_ROW);
                    crop.setName("params-crop:" + p.token);
                    crop.setEnabled(ProgramParams.isPicture(values[i]));
                    chosen.addPropertyChangeListener("text", e -> crop.setEnabled(ProgramParams.isPicture(values[index])));
                    crop.addActionListener(e -> this.cropPicture(values[index], f -> this.assignFile(ps, values, index, fileLabels, f.getAbsolutePath(), null)));
                    JPanel looks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
                    looks.add(preview);
                    looks.add(crop);
                    row.add(looks, BorderLayout.EAST);
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
                form.add(this.withUse(p, i, row, chosen, uses, off));
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
                        JFileChooser chooser = selectChooser();
                        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt", "text", "md", "prompt"));
                        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
                        selected(chooser.getSelectedFile());
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
                    // Browse prompts: a prompt from the prompt library, by category.
                    JButton prompts = new JButton("Browse prompts");
                    prompts.setName("params-prompt-browse:" + p.token);
                    prompts.addActionListener(e -> PromptBrowser.show(app, (title, text) -> {
                        area.setText(text);
                        area.setCaretPosition(0);
                    }));
                    // Recent prompts: the six used last with this program, one picked into the field.
                    JButton recent = new JButton(PromptHistory.RECENT + " \u25be");
                    recent.setName("params-prompt-recent:" + p.token);
                    recent.addActionListener(e -> {
                        java.util.List<String> got = PromptHistory.recent(PromptDb.dir(), program);
                        if (got.isEmpty()) {
                            app.setNow("No prompts run with " + program + " yet");
                            return;
                        }
                        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
                        for (final String one : got) {
                            javax.swing.JMenuItem item = new javax.swing.JMenuItem(PromptHistory.label(one));
                            item.addActionListener(ev -> {
                                area.setText(one);
                                area.setCaretPosition(0);
                            });
                            m.add(item);
                        }
                        MenuCancel.add(m);
                        lastRecent = m;
                        m.show(recent, 0, recent.getHeight());
                    });
                    picks.add(select);
                    picks.add(db);
                    picks.add(prompts);
                    picks.add(recent);
                    if (SogniSections.shown(program)) {
                        // Browse sections: the program's saved Params (Save at the bottom), one picked into the rows.
                        JButton sections = new JButton(SogniSections.BROWSE);
                        sections.setName("params-sections:" + p.token);
                        sections.addActionListener(e -> this.browseSections(sections, SogniSections.app(program), ps, fields, areas));
                        picks.add(sections);
                    }
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
            if (p.importList) {
                // A list row with Import (vst_render's Kits left out): a text file's lines into it.
                JPanel row = new JPanel(new BorderLayout(6, 0));
                JButton load = new JButton("Import\u2026");
                load.setName("params-import:" + p.token);
                load.addActionListener(e -> {
                    JFileChooser chooser = selectChooser();
                    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt", "text"));
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
                    selected(chooser.getSelectedFile());
                    this.importList(field, chooser.getSelectedFile());
                });
                row.add(field, BorderLayout.CENTER);
                row.add(load, BorderLayout.EAST);
                form.add(row);
                continue;
            }
            if (ProgramParams.stylesFromFile(p)) {
                // SogniMusic's styles list from a text file: Select file or Browse DB (one style a line, or with | or , between).
                JPanel row = new JPanel(new BorderLayout(6, 0));
                JPanel picks = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
                JButton select = new JButton("Select file");
                select.setName("params-styles-file:" + p.token);
                select.addActionListener(e -> {
                    JFileChooser chooser = selectChooser();
                    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files", "txt", "text", "md"));
                    if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
                    selected(chooser.getSelectedFile());
                    this.stylesFrom(field, chooser.getSelectedFile());
                });
                JButton db = new JButton("Browse DB");
                db.setName("params-styles-db:" + p.token);
                db.setEnabled(!PromptDb.allFiles(PromptDb.TEXTS).isEmpty());
                db.addActionListener(e -> {
                    DbFilter.current = "T";
                    app.promptDb.browse(PromptDb.TEXTS, (picked, file) -> this.stylesFrom(field, file));
                });
                picks.add(select);
                picks.add(db);
                row.add(field, BorderLayout.CENTER);
                row.add(picks, BorderLayout.SOUTH);
                form.add(row);
                continue;
            }
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
                if (p.token.startsWith("--map")) {
                    // vst_render's map: one of its files above, or another text file.
                    JButton browse = new JButton("Browse");
                    browse.setName("params-map-browse:" + p.token);
                    browse.addActionListener(e -> {
                        JFileChooser chooser = new JFileChooser(VstLists.mapsDir());
                        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Map files", "txt"));
                        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
                        String path = chooser.getSelectedFile().getAbsolutePath();
                        VstLists.rememberMap(path);
                        field.setText(path);
                    });
                    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                    buttons.add(choose);
                    buttons.add(browse);
                    buttons.add(choiceClear(p, field));
                    row.add(buttons, BorderLayout.EAST);
                } else {
                    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                    buttons.add(choose);
                    buttons.add(choiceClear(p, field));
                    row.add(buttons, BorderLayout.EAST);
                }
                form.add(row);
                if (p.modelNote) {
                    // SogniChat: image analyze mode (a DeepSeek vision model) or tools mode, as the model is picked.
                    final JLabel note = new JLabel(ProgramParams.chatModelNote(field.getText()));
                    note.setName("params-note:" + p.token);
                    field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                        @Override
                        public void insertUpdate(javax.swing.event.DocumentEvent e) {
                            note.setText(ProgramParams.chatModelNote(field.getText()));
                        }

                        @Override
                        public void removeUpdate(javax.swing.event.DocumentEvent e) {
                            note.setText(ProgramParams.chatModelNote(field.getText()));
                        }

                        @Override
                        public void changedUpdate(javax.swing.event.DocumentEvent e) {
                            note.setText(ProgramParams.chatModelNote(field.getText()));
                        }
                    });
                    // The form is two columns (label, field): the note sits under the field.
                    form.add(new JLabel(""));
                    form.add(note);
                }
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
        // A long form (vst_render's) scrolls: the dialog stays within the screen, its buttons on it.
        // (and as wide: long labels make it wider than a small screen).
        // vst_render's rows on tabs (Input, Output, Plugins, Settings): one page is not all of it at once.
        this.showRows(form, rowStarts, ps, fields, checks);
        java.awt.Component formView = this.onTabs(form, rowStarts, ps, values, fileLabels, uses);
        javax.swing.JScrollPane formScroll = new javax.swing.JScrollPane(formView, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        formScroll.setName("params-scroll");
        formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        formScroll.getHorizontalScrollBar().setUnitIncrement(16);
        java.awt.Dimension formSize = formView.getPreferredSize();
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int room = screen.height - top.getPreferredSize().height - PARAMS_FRAME;
        formScroll.setPreferredSize(new java.awt.Dimension(Math.min(formSize.width + formScroll.getVerticalScrollBar().getPreferredSize().width, screen.width - PARAMS_SIDES),
            Math.min(formSize.height, Math.max(PARAMS_MIN_FORM, room))));
        box.add(formScroll, BorderLayout.CENTER);
        if (SogniSections.shown(program)) {
            // Save (bottom left): the text rows kept as one of the program's sections; the Params stay open.
            JButton saveSection = new JButton(SogniSections.SAVE);
            saveSection.setName("params-save-section");
            saveSection.addActionListener(e -> this.saveSection(saveSection, SogniSections.app(program), ps, fields, areas));
            // Export: the same rows written into a file picked, not the prompt library.
            JButton exportSection = new JButton(ParamsClip.EXPORT);
            exportSection.setName("params-export-section");
            exportSection.addActionListener(e -> this.exportSection(exportSection, SogniSections.app(program), ps, fields, areas));
            JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
            south.add(saveSection);
            south.add(exportSection);
            box.add(south, BorderLayout.SOUTH);
        }
        // Instrument sessions: ticking one unticks the others, unless Multi-instrument session is ticked.
        for (int i = 0; i < checks.length; i++) {
            if (checks[i] == null || ps.get(i).clears == null) continue;
            final int at = i;
            checks[i].addActionListener(e -> {
                if (!checks[at].isSelected()) return;
                boolean[] now = new boolean[checks.length];
                for (int k = 0; k < checks.length; k++) now[k] = checks[k] != null && checks[k].isSelected();
                for (int k : ProgramParams.toClear(ps, at, now)) checks[k].setSelected(false);
            });
        }
        int ans = JOptionPane.showConfirmDialog(app, box, "Parameters \u00b7 " + program, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        // Closed with the window's X: what was picked is kept, as with OK; Cancel leaves it all as it was.
        if (ans != JOptionPane.OK_OPTION && ans != JOptionPane.CLOSED_OPTION) return;
        for (int i = 0; i < fields.length; i++) if (fields[i] != null) values[i] = fields[i].getText();
        for (int i = 0; i < areas.length; i++) if (areas[i] != null) values[i] = ProgramParams.oneLine(areas[i].getText());
        for (int i = 0; i < checks.length; i++) if (checks[i] != null) values[i] = checks[i].isSelected() ? "1" : "";
        // The rows left out: kept beside the Params, not in them (a run takes the Params' switches).
        off.clear();
        for (int i = 0; i < uses.length; i++) if (uses[i] != null && !uses[i].isSelected()) off.add(ps.get(i).token);
        this.saveParams(program + ProgramParams.OFF_KEY, ProgramParams.build(ps, ProgramParams.onlyOff(ps, values, off)));
        System.arraycopy(ProgramParams.withoutOff(ps, values, off), 0, values, 0, values.length);
        this.saveParams(program, ProgramParams.build(ps, values));
        // The prompt kept among the program's six last (Recent prompts).
        for (int i = 0; i < ps.size(); i++) if (ProgramParams.promptFromFile(ps.get(i))) PromptHistory.add(PromptDb.dir(), program, values[i]);
        // Another program loaded meanwhile (ImageUpscaler from the Media browser Choose file opened):
        // its argument line is not this program's (it was given SogniPedit's --prompt).
        if (!program.equals(app.pyName)) {
            app.setNow("Params of " + program + " saved");
            return;
        }
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

    /** The Params dialog's title bar, OK/Cancel row and margins, and the least of the form shown. */
    static final int PARAMS_FRAME = 160, PARAMS_MIN_FORM = 200, PARAMS_SIDES = 80;

    /**
     * A file or folder switch's row with its Use box in front (ticked: the file is in the run;
     * unticked: it stays chosen, greyed, and the run leaves it out). Other rows as they are.
     */
    java.awt.Component withUse(ProgramParams.Param p, int i, JPanel row, javax.swing.JComponent chosen, javax.swing.JCheckBox[] uses, java.util.Set<String> off) {
        if (!ProgramParams.canTurnOff(p)) return row;
        final javax.swing.JCheckBox use = new javax.swing.JCheckBox("Use", !off.contains(p.token));
        use.setName("params-use:" + p.token);
        use.setToolTipText("Unticked: the file or folder stays chosen, and the run leaves it out");
        uses[i] = use;
        Runnable paint = () -> chosen.setEnabled(use.isSelected());
        use.addActionListener(e -> paint.run());
        paint.run();
        JPanel wrap = new JPanel(new BorderLayout(4, 0));
        wrap.add(use, BorderLayout.WEST);
        wrap.add(row, BorderLayout.CENTER);
        return wrap;
    }

    /** What the last video picked for a picture row gave (for the tests). */
    String lastFramesNote;

    /**
     * A file picked for Params row `index`: its path, shown (`shown`: "x.png · from DB"); a video
     * for a picture row gives its first frame instead, and its last frame to the next picture row
     * still empty (read with VLC off the Swing thread; a video it cannot read stays as it is).
     */
    /** Recent's menu shown last (for the tests). */
    static javax.swing.JPopupMenu lastRecentRefs;

    /** Recent: the reference pictures used last, each a small thumbnail with its name; a click puts it in the row. */
    void recentRefs(java.awt.Component at, java.util.List<ProgramParams.Param> ps, String[] values, int index, JLabel[] labels) {
        java.util.List<File> pics = RecentRefs.list(PromptDb.dir());
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        if (pics.isEmpty()) {
            javax.swing.JMenuItem none = new javax.swing.JMenuItem("No recent reference pictures yet");
            none.setEnabled(false);
            m.add(none);
        }
        for (final File f : pics) {
            javax.swing.JMenuItem it = new javax.swing.JMenuItem(f.getName());
            it.setName("params-recent-ref:" + f.getName());
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
                if (img != null) {
                    int side = 48;
                    double k = Math.min(side / (double) img.getWidth(), side / (double) img.getHeight());
                    it.setIcon(new javax.swing.ImageIcon(img.getScaledInstance(Math.max(1, (int) (img.getWidth() * k)), Math.max(1, (int) (img.getHeight() * k)), java.awt.Image.SCALE_SMOOTH)));
                }
            } catch (Exception ignored) {
                // its name only
            }
            it.addActionListener(e -> this.assignFile(ps, values, index, labels, f.getAbsolutePath(), f.getName() + " \u00b7 recent"));
            m.add(it);
        }
        MenuCancel.add(m);
        lastRecentRefs = m;
        if (at.isShowing()) m.show(at, 0, at.getHeight());
    }

    void assignFile(java.util.List<ProgramParams.Param> ps, String[] values, int index, JLabel[] labels, String path, String shown) {
        final int[][] frames = ProgramParams.framesFor(ps, values, index, path);
        if (frames.length == 0) {
            values[index] = path;
            if (labels[index] != null) labels[index].setText(shown != null ? shown : new File(path).getName());
            // A picture: among the recent references (Recent beside a picture row).
            if (path != null && MediaDir.kind(path) == MediaDir.PICTURE) RecentRefs.add(PromptDb.dir(), new File(path));
            return;
        }
        final String video = new File(path).getName();
        if (labels[index] != null) labels[index].setText("Reading the frames of " + video + "\u2026");
        new Thread(() -> {
            final String[] made = new String[frames.length];
            for (int k = 0; k < frames.length; k++) {
                byte[] png = VideoFrames.frame(path, frames[k][1], 0);
                if (png == null) continue;
                try {
                    File dir = new File(PromptDb.dir(), "pyjav-in");
                    dir.mkdirs();
                    File out = new File(dir, video.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + (frames[k][1] < 0 ? "-last.png" : "-first.png"));
                    Files.write(out.toPath(), png);
                    made[k] = out.getAbsolutePath();
                } catch (Exception ex) {
                    made[k] = null;
                }
            }
            SwingUtilities.invokeLater(() -> {
                if (made[0] == null) {
                    // No frame from it (no VLC): the video itself, as picked.
                    values[index] = path;
                    if (labels[index] != null) labels[index].setText(video + " (no frame could be read)");
                    this.lastFramesNote = "none";
                    return;
                }
                for (int k = 0; k < frames.length; k++) {
                    if (made[k] == null) continue;
                    int row = frames[k][0];
                    values[row] = made[k];
                    if (labels[row] != null) labels[row].setText(new File(made[k]).getName() + " \u00b7 " + (frames[k][1] < 0 ? "last" : "first") + " frame of " + video);
                }
                this.lastFramesNote = frames.length > 1 && made[1] != null ? "first and last frame" : "first frame";
            });
        }, "pulsekit-params-frames").start();
    }

    /** A file set's source MIDI, copied into ~/.pulsekit/pyjav-in for a program; null when none is picked. */
    /** The Preview shown last, for the tests. */
    javax.swing.JDialog lastParamsPreview;

    /** A thumbnail of the picture at path, in a small dialog (the file's name as its title). */
    void previewPicture(String path) {
        java.awt.image.BufferedImage image = null;
        try {
            image = javax.imageio.ImageIO.read(new File(path));
        } catch (Exception ignored) {
        }
        JOptionPane pane;
        if (image == null) {
            pane = new JOptionPane("The picture could not be read (Java shows PNG, JPEG, GIF and BMP).", JOptionPane.INFORMATION_MESSAGE);
        } else {
            int max = 320;
            double scale = Math.min(1.0, Math.min(max / (double) image.getWidth(), max / (double) image.getHeight()));
            int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
            JLabel thumb = new JLabel(new javax.swing.ImageIcon(image.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH)));
            thumb.setName("params-preview-image");
            JLabel size = new JLabel(image.getWidth() + " \u00d7 " + image.getHeight());
            JPanel box = new JPanel(new BorderLayout(0, 6));
            box.add(thumb, BorderLayout.CENTER);
            // Upscale: ImageUpscaler with this picture, its Params open; Return opens this program's Params again.
            JButton upscale = new JButton(UPSCALE_PREVIEW);
            upscale.setName("params-preview-upscale");
            upscale.addActionListener(e -> this.upscalePreview(path, upscale));
            JPanel foot = new JPanel(new BorderLayout(8, 0));
            foot.add(size, BorderLayout.WEST);
            foot.add(upscale, BorderLayout.EAST);
            box.add(foot, BorderLayout.SOUTH);
            pane = new JOptionPane(box, JOptionPane.PLAIN_MESSAGE);
        }
        javax.swing.JDialog dialog = pane.createDialog(app, new File(path).getName());
        dialog.setModal(false);
        this.lastParamsPreview = dialog;
        dialog.setVisible(true);
    }

    static final String UPSCALE_PREVIEW = "Upscale";

    /**
     * Preview's Upscale: the Preview and the Params closed (what was picked kept, as with the window's X),
     * then ImageUpscaler with the picture as its input, its Params open; Return to the program opens its Params.
     */
    void upscalePreview(String path, java.awt.Component from) {
        final String program = app.pyName;
        java.awt.Window preview = SwingUtilities.getWindowAncestor(from);
        if (preview != null) preview.dispose();
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w instanceof javax.swing.JDialog && w.isShowing() && String.valueOf(((javax.swing.JDialog) w).getTitle()).startsWith("Parameters \u00b7 ")) w.dispose();
        }
        final Runnable back = program == null ? null : () -> {
            app.showView("py");
            app.programMenus.selectListedProgram("Java", program);
            SwingUtilities.invokeLater(() -> app.pyJav.openParams());
        };
        SwingUtilities.invokeLater(() -> {
            RefImages.upscale(app, new File(path), null, null);
            if (back != null) this.setReturn("Return to " + program.replaceAll("\\.java$", ""), back);
        });
    }

    /** Clear: a file picked by mistake leaves the row (a second reference image, say). */
    /** A run going on: its live news and the "Running…" line and top line it set last. */
    static final class Shown {
        final JavaRun.Live live;
        final String head;
        final String[] lastLine, lastTop;

        Shown(JavaRun.Live live, String head, String[] lastLine, String[] lastTop) {
            this.live = live;
            this.head = head;
            this.lastLine = lastLine;
            this.lastTop = lastTop;
        }
    }

    /** Beside Output's headline: the run's state and time ("vst_render.py: Running… 10:05 elapsed"). */
    JLabel pyStatus;

    /** How many lines of the arguments show at once. */
    static final int ARGS_LINES = 3;

    /** The runs going on, by program. */
    static final java.util.Map<String, Shown> RUNNING = new java.util.concurrent.ConcurrentHashMap<String, Shown>();

    /** The program picked again while its run goes on: its "Running…" line (time and news) shown again and kept up. */
    boolean showRunning(String name) {
        Shown r = name == null ? null : RUNNING.get(name);
        if (r == null || r.live.done || r.live.aborted()) return false;
        long now = System.currentTimeMillis();
        String tail = JavaRun.runningTail(r.live);
        String line = tail.length() == 0 ? "Running\u2026" : tail;
        if (app.pyLog != null) {
            app.pyLog.setText(line);
            app.pyLog.setCaretPosition(app.pyLog.getDocument().getLength());
        }
        if (this.pyStatus != null) this.pyStatus.setText(name + ": " + JavaRun.runningStatus(r.live, now));
        r.lastLine[0] = line;
        r.lastTop[0] = JavaRun.runningTop(name, r.live, now);
        app.setNow(r.lastTop[0]);
        return true;
    }

    /** The menu Playlist showed last (for the tests). */
    static javax.swing.JPopupMenu lastPlaylistMenu;

    /** Playlist: one of Pulsekit's playlists (.pls) for a batch program's folder or list. */
    static JButton playlistButton(ProgramParams.Param p, final String[] values, final int index, final JLabel chosen) {
        final JButton b = new JButton("Playlist");
        b.setName("params-pls:" + p.token);
        b.setToolTipText("One of Pulsekit's playlists: its files are the ones used");
        b.addActionListener(e -> {
            javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
            java.util.List<String> names = Playlist.withFiles(PromptDb.dir());
            if (names.isEmpty()) {
                javax.swing.JMenuItem none = new javax.swing.JMenuItem("No playlists yet");
                none.setEnabled(false);
                m.add(none);
            }
            for (final String n : names) {
                javax.swing.JMenuItem it = new javax.swing.JMenuItem(n);
                it.setName("params-pls-pick:" + n);
                it.addActionListener(ev -> {
                    values[index] = Playlist.file(PromptDb.dir(), n).getAbsolutePath();
                    chosen.setText(n + Playlist.EXT);
                });
                m.add(it);
            }
            MenuCancel.add(m);
            lastPlaylistMenu = m;
            if (b.isShowing()) m.show(b, 0, b.getHeight());
        });
        return b;
    }

    /**
     * A drop-down row (vst_render's Model A, Model B...): its list picks the row's value, kept in a field
     * that is not shown (fields[i], as the typed rows' are); the field set (Suggested, Defaults) moves the list.
     */
    javax.swing.JComboBox<String> dropdown(final ProgramParams.Param p, String value, JTextField[] fields, int i) {
        final JTextField field = new JTextField(value == null ? "" : value.trim(), 10);
        field.setName("params-field:" + p.token);
        fields[i] = field;
        final javax.swing.JComboBox<String> list = new javax.swing.JComboBox<String>(p.choices);
        list.setName("params-dropdown:" + p.token);
        final java.util.List<String> vals = java.util.Arrays.asList(p.choiceValues != null ? p.choiceValues : p.choices);
        final boolean[] syncing = {false};
        list.setSelectedIndex(Math.max(0, vals.indexOf(field.getText().trim())));
        list.addActionListener(e -> {
            if (syncing[0]) return;
            syncing[0] = true;
            field.setText(p.choiceValue(Math.max(0, list.getSelectedIndex())));
            syncing[0] = false;
        });
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void follow() {
                if (syncing[0]) return;
                syncing[0] = true;
                list.setSelectedIndex(Math.max(0, vals.indexOf(field.getText().trim())));
                syncing[0] = false;
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                follow();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                follow();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                follow();
            }
        });
        return list;
    }

    /**
     * Rows shown only when other rows are so (ProgramParams.shown: Model A's number of kits when Model A is
     * "the first N kits"): hidden or shown again as the switches are ticked and the values picked.
     */
    void showRows(JPanel form, int[] rowStarts, final List<ProgramParams.Param> ps, final JTextField[] fields, final javax.swing.JCheckBox[] checks) {
        final java.awt.Component[] all = form.getComponents();
        final java.util.List<Runnable> rows = new java.util.ArrayList<Runnable>();
        final java.util.function.IntFunction<String> valueOf = j -> checks[j] != null ? (checks[j].isSelected() ? "on" : "") : fields[j] != null ? fields[j].getText() : "";
        for (int i = 0; i < ps.size(); i++) {
            final ProgramParams.Param p = ps.get(i);
            if (p.showWhen == null) continue;
            int end = i + 1 < ps.size() ? rowStarts[i + 1] : all.length;
            final java.awt.Component[] mine = java.util.Arrays.copyOfRange(all, rowStarts[i], end);
            rows.add(() -> {
                boolean show = ProgramParams.shown(p, ps, valueOf);
                for (java.awt.Component c : mine) {
                    if (c.isVisible() == show) continue;
                    c.setVisible(show);
                    if (c.getParent() != null) {
                        c.getParent().revalidate();
                        c.getParent().repaint();
                    }
                }
            });
        }
        if (rows.isEmpty()) return;
        final Runnable update = () -> {
            for (Runnable r : rows) r.run();
        };
        for (javax.swing.JCheckBox c : checks) if (c != null) c.addItemListener(e -> update.run());
        for (JTextField f : fields) {
            if (f == null) continue;
            f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    update.run();
                }

                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    update.run();
                }

                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    update.run();
                }
            });
        }
        update.run();
    }

    /** The tab the Params page showed last (vst_render's), opened again next time. */
    static String paramsTab;

    /**
     * The form's rows moved onto their tabs (ProgramParams.tabs), the Plugins tab with the favourite,
     * recently used and folder lists under its rows; the form itself when the rows have no tabs.
     */
    java.awt.Component onTabs(JPanel form, int[] rowStarts, final List<ProgramParams.Param> ps, final String[] values, final JLabel[] fileLabels, final javax.swing.JCheckBox[] uses) {
        List<String> names = ProgramParams.tabs(ps);
        if (names.isEmpty()) return form;
        java.awt.Component[] all = form.getComponents();
        java.util.LinkedHashMap<String, JPanel> pages = new java.util.LinkedHashMap<String, JPanel>();
        // Each page's rows: the label only as wide as the page's labels, the rest for the row's buttons and names.
        for (String t : names) pages.put(t, new JPanel(new java.awt.GridBagLayout()));
        java.util.Map<String, int[]> rowsIn = new java.util.HashMap<String, int[]>();
        String last = names.get(names.size() - 1);
        for (int i = 0; i < ps.size(); i++) {
            int end = i + 1 < ps.size() ? rowStarts[i + 1] : all.length;
            String tab = ps.get(i).tab != null ? ps.get(i).tab : last;
            JPanel page = pages.get(tab);
            int[] at = rowsIn.computeIfAbsent(tab, x -> new int[] {0, 0});
            for (int k = rowStarts[i]; k < end; k++) {
                java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
                c.gridx = at[1] % 2;
                c.gridy = at[1] / 2;
                c.weightx = c.gridx == 0 ? 0 : 1;
                c.fill = java.awt.GridBagConstraints.HORIZONTAL;
                c.anchor = java.awt.GridBagConstraints.WEST;
                c.insets = new java.awt.Insets(3, 0, 3, c.gridx == 0 ? 12 : 0);
                // A long label wraps (two lines) instead of widening the label column.
                if (c.gridx == 0 && all[k] instanceof JLabel) {
                    JLabel l = (JLabel) all[k];
                    String text = l.getText();
                    if (text != null && !text.startsWith("<html>") && l.getPreferredSize().width > TAB_LABEL_WIDTH) {
                        l.setToolTipText(text);
                        l.setText("<html><body style='width:" + TAB_LABEL_WIDTH + "px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
                    }
                }
                page.add(all[k], c);
                at[1]++;
            }
        }
        final javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.setName("params-tabs");
        for (java.util.Map.Entry<String, JPanel> e : pages.entrySet()) {
            JPanel holder = new JPanel(new BorderLayout(0, 10));
            holder.add(e.getValue(), BorderLayout.NORTH);
            if (ProgramParams.PLUGINS.equals(e.getKey())) holder.add(this.pluginPicks(ps, values, fileLabels, uses), BorderLayout.CENTER);
            if (ProgramParams.FILES.equals(e.getKey())) holder.add(this.filesPanel(), BorderLayout.CENTER);
            holder.setName("params-tab:" + e.getKey());
            tabs.addTab(e.getKey(), holder);
        }
        // The tab open last, also after Pulsekit is started again (~/.pulsekit/params-tab.txt).
        final File home = new File(System.getProperty("user.home", "."));
        if (paramsTab == null && PluginFiles.kept(PluginFiles.paramsTabFile(home)).length() > 0) paramsTab = PluginFiles.kept(PluginFiles.paramsTabFile(home));
        int at = paramsTab == null ? -1 : names.indexOf(paramsTab);
        if (at >= 0) tabs.setSelectedIndex(at);
        tabs.addChangeListener(e -> {
            paramsTab = tabs.getTitleAt(tabs.getSelectedIndex());
            PluginFiles.keep(PluginFiles.paramsTabFile(home), paramsTab);
        });
        return tabs;
    }

    /** A .dll picked for a plugin row: said at once when it is a 32-bit plugin (Carla's bridge plays it) or no plugin. */
    void dllNotice(java.awt.Component at, File f) {
        String problem = DllInfo.problem(f);
        if (problem == null && f != null && DllInfo.isSoundFont(f)) app.setNow(DllInfo.SOUNDFONT);
        if (problem == null) return;
        File rack = DllInfo.BRIDGED.equals(problem) ? carlaRack() : null;
        String text = DllInfo.BRIDGED.equals(problem) ? DllInfo.bridgedNote(f, rack) : "\u2718 " + problem;
        pluginTestSaid = text;
        app.setNow(text.split("\n")[0]);
        if (pluginTestQuiet) return;
        if (rack != null && editRow != null) {
            // A 32-bit plugin and Carla's rack found: its window (the rack, the plugin in it) opened now, when asked.
            int ans = JOptionPane.showOptionDialog(at, text + "\n\nOpen it in Carla's rack now to load its kit (Edit settings)?", "Plugin \u00b7 " + f.getName(),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, new Object[] {"Open in Carla", "Not now"}, "Open in Carla");
            if (ans == 0) editRow.doClick();
        } else {
            JOptionPane.showMessageDialog(at, text, "Plugin \u00b7 " + f.getName(), DllInfo.BRIDGED.equals(problem) ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        }
    }

    /** The row's Edit settings while its plugin is being chosen (Open in Carla presses it). */
    private JButton editRow;

    /** Carla's rack in the plugin folders (and Carla's usual places), kept for vst_render when found. */
    static File carlaRack() {
        File rack = DllInfo.carlaRack(pluginFolders());
        DllInfo.keepRack(PromptDb.dir(), rack);
        return rack;
    }

    /** The VST folders Pulsekit looks in (Plugin folders). */
    static List<String> pluginFolders() {
        PluginLibrary.load(PromptDb.dir());
        String home = System.getProperty("user.home", ".");
        List<String> out = new ArrayList<String>(PluginLibrary.folders(PluginLibrary.VST3, false, home));
        for (String f : PluginLibrary.folders(PluginLibrary.VST2, false, home)) if (!out.contains(f)) out.add(f);
        return out;
    }

    /** The tests: the Params' plugin Test said last (the verdict), and whether to show it in a window. */
    static volatile String pluginTestSaid = "";
    static boolean pluginTestQuiet;

    /** A plugin row's Test: vst_render --check with the row's map and DrumMidi's jar (the kit's drums heard or not). */
    void testPluginRow(final JButton test, List<ProgramParams.Param> ps, String[] values, String token, String path) {
        if (path == null || path.length() == 0 || !new File(path).exists()) {
            app.setNow("Choose a plugin first (" + token + ")");
            return;
        }
        final File plug = new File(path);
        String problem = DllInfo.problem(plug);
        if (DllInfo.BRIDGED.equals(problem) || DllInfo.isLv2(plug)) carlaRack();
        if (problem != null && !DllInfo.BRIDGED.equals(problem)) {
            pluginTestSaid = "\u2718 " + problem;
            app.setNow(pluginTestSaid);
            if (!pluginTestQuiet) JOptionPane.showMessageDialog(test, pluginTestSaid, "Test \u00b7 " + plug.getName(), JOptionPane.PLAIN_MESSAGE);
            return;
        }
        List<String> extra = new ArrayList<String>();
        String mapToken = token.equals("--plugin") ? "--map" : token.replace("--plugin", "--map");
        for (int i = 0; i < ps.size(); i++) if (ps.get(i).token.equals(mapToken) && values[i] != null && values[i].trim().length() > 0) {
            extra.add("--map");
            extra.add(values[i].trim());
        }
        VstLists.unpackMaps();
        try {
            File jar = new File(new File(PromptDb.dir(), "pyjav-in"), "DrumMidi_CRT.jar");
            List<String> with = JavaRun.withDrumMidi("vst_render.py", new ArrayList<String>(java.util.Arrays.asList("--test")), jar, ProgramFiles.read("Java", "DrumMidi_CRT.jar"));
            with.remove("--test");
            extra.addAll(with);
        } catch (Exception ex) {
            // without the jar: only whether it plays
        }
        test.setEnabled(false);
        app.setNow(PluginsList.testing(plug, false));
        // While it runs: the time taken on the button, what it is doing in the status line.
        final javax.swing.Timer tick = PluginsList.ticker(p -> {
            test.setText(p.split(" ")[0]);
            app.setNow("Testing " + plug.getName() + ": " + p);
        });
        PluginsList.test(pluginTestQuiet ? null : this, plug, false, extra, log -> {
            tick.stop();
            test.setText("Test");
            test.setEnabled(true);
            String v = PluginsList.verdict(plug.getName(), log);
            pluginTestSaid = v;
            app.setNow(v.split("\n")[0]);
            // A plugin whose pads are on other notes: Test's note map from their sounds, put in map.txt when asked.
            lastSuggestion = PluginFiles.suggestion(log);
            if (pluginTestQuiet) return;
            if (lastSuggestion == null) {
                JOptionPane.showMessageDialog(test, v, "Test \u00b7 " + plug.getName(), JOptionPane.PLAIN_MESSAGE);
                return;
            }
            this.offerSuggestion(test, plug, v, lastSuggestion);
        });
    }

    /** Test's suggested note map shown last (for the tests). */
    static PluginFiles.Suggestion lastSuggestion;

    /**
     * Test's verdict with its suggested note map: Add to map.txt (the plugin's section replaced or added),
     * Edit first (map.txt's editor with the lines in it), or Not now.
     */
    void offerSuggestion(java.awt.Component at, File plug, String verdict, PluginFiles.Suggestion s) {
        File home = new File(System.getProperty("user.home", "."));
        javax.swing.JTextArea text = new javax.swing.JTextArea(verdict + "\n\nSome of its drums are on other notes. A note map from their sounds (each pad played alone), for map.txt:\n\n"
            + PluginFiles.suggestionText(s), 22, 80);
        text.setEditable(false);
        text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        int ans = JOptionPane.showOptionDialog(at, new JScrollPane(text), "Test \u00b7 " + plug.getName(), JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
            new Object[] {"Add to map.txt", "Edit first", "Not now"}, "Add to map.txt");
        File map = PluginFiles.noteMap(home);
        if (ans == 0) {
            boolean ok = PluginFiles.addSuggestion(home, s);
            app.setNow(ok ? "Added [" + s.section + "] to " + map.getPath() + ": Test again to hear it" : "Could not write " + map.getPath());
        } else if (ans == 1) {
            String before = PluginFiles.mapText(map);
            PluginFiles.write(map, PluginFiles.withSection(before, s.section, s.lines));
            if (!this.editNoteMap(at, map)) PluginFiles.write(map, before);
        }
    }

    /** The widest a tab page's label is before it wraps (vst_render's Params: room for the rows' buttons and names). */
    static final int TAB_LABEL_WIDTH = 300;

    /** A plugin's file name for a list: "Riot Drums.vst3". */
    static String plugName(String path) {
        return new File(path).getName();
    }

    /**
     * The Plugins tab's lists: Favourites (the Plugins list's and Plugin Browser's), Recently used (vst_render's
     * runs) and Plugin folders (where VST3 and VST2 plugins are looked for; Folders… changes them). A plugin
     * clicked: Use as plugin 1 to 4 fills that row (and ticks its Use).
     */
    static final String PICKS_TIP = "Favourites and Recently used: click a plugin to use it as plugin 1 to 4, or drag it onto a plugin row";

    JPanel pluginPicks(final List<ProgramParams.Param> ps, final String[] values, final JLabel[] fileLabels, final javax.swing.JCheckBox[] uses) {
        PluginLibrary.load(PromptDb.dir());
        final String home = System.getProperty("user.home", ".");
        final javax.swing.DefaultListModel<String> favModel = new javax.swing.DefaultListModel<String>(), recentModel = new javax.swing.DefaultListModel<String>(),
            folderModel = new javax.swing.DefaultListModel<String>();
        final Runnable fill = () -> {
            favModel.clear();
            for (String f : PluginLibrary.favouritePaths()) {
                PluginLibrary.Type t = PluginLibrary.typeOf(new File(f));
                if (t != null && (PluginLibrary.VST3.equals(t.name) || PluginLibrary.VST2.equals(t.name))) favModel.addElement(f);
            }
            recentModel.clear();
            for (String f : PluginLibrary.recent()) recentModel.addElement(f);
            folderModel.clear();
            for (String type : new String[] {PluginLibrary.VST3, PluginLibrary.VST2})
                for (String f : PluginLibrary.folders(type, false, home)) if (!folderModel.contains(f)) folderModel.addElement(f);
        };
        fill.run();
        JPanel lists = new JPanel(new GridLayout(1, 3, 8, 0));
        // One line above the lists says what a click does (under each list, the form's last pixels cut it off).
        JPanel out = new JPanel(new BorderLayout(0, 4));
        out.setName("params-plugin-picks");
        JLabel tip = new JLabel(PICKS_TIP);
        tip.setName("params-plugin-picks-tip");
        tip.setFont(tip.getFont().deriveFont(11f));
        out.add(tip, BorderLayout.NORTH);
        out.add(lists, BorderLayout.CENTER);
        lists.add(this.pickList("Favourites", "params-favourites", favModel, ps, values, fileLabels, uses, "No favourites yet (Plugins list: Add to favourites)"));
        lists.add(this.pickList("Recently used", "params-recent", recentModel, ps, values, fileLabels, uses, "None yet: the plugins of vst_render's runs"));
        JPanel folders = new JPanel(new BorderLayout(0, 4));
        javax.swing.JList<String> folderList = new javax.swing.JList<String>(folderModel);
        folderList.setName("params-plugin-folders");
        folderList.setVisibleRowCount(5);
        folders.add(new JScrollPane(folderList), BorderLayout.CENTER);
        JButton change = new JButton("Folders\u2026");
        change.setName("params-plugin-folders-change");
        change.setToolTipText("Where VST3 and VST2 plugins are looked for (the Plugin Browser's folders)");
        change.addActionListener(e -> {
            if (PluginBrowser.foldersDialog(SwingUtilities.getWindowAncestor(change))) fill.run();
        });
        // (Beside the title, not under the list: there the row's height hid it.)
        JPanel head = new JPanel(new BorderLayout(6, 0));
        head.add(new JLabel("Plugin folders"), BorderLayout.WEST);
        change.setMargin(new java.awt.Insets(0, 6, 0, 6));
        head.add(change, BorderLayout.EAST);
        folders.add(head, BorderLayout.NORTH);
        lists.add(folders);
        // A plugin made a favourite (or a run made recent) while the Params are open, in the Plugin Browser
        // or the Plugins list: the lists again each time the Params window comes back to the front.
        final Runnable refresh = () -> SwingUtilities.invokeLater(() -> refreshPicks(favModel, recentModel, fill));
        PluginLibrary.changed.add(refresh);
        out.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & (java.awt.event.HierarchyEvent.SHOWING_CHANGED | java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED)) == 0) return;
            if (out.isShowing()) {
                if (!PluginLibrary.changed.contains(refresh)) PluginLibrary.changed.add(refresh);
                java.awt.Window w = SwingUtilities.getWindowAncestor(out);
                if (w != null && !Boolean.TRUE.equals(out.getClientProperty("picks-refresh"))) {
                    out.putClientProperty("picks-refresh", Boolean.TRUE);
                    // Back to the front (from the Plugin Browser, a run): Recently used may have changed too.
                    w.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override
                        public void windowActivated(java.awt.event.WindowEvent we) {
                            refreshPicks(favModel, recentModel, fill);
                        }

                        @Override
                        public void windowClosed(java.awt.event.WindowEvent we) {
                            PluginLibrary.changed.remove(refresh);
                        }
                    });
                }
            } else if (!out.isDisplayable()) {
                PluginLibrary.changed.remove(refresh);
            }
        });
        return out;
    }

    /** The Plugins tab's lists again, when what they show changed (the list's selection kept otherwise). */
    static void refreshPicks(javax.swing.DefaultListModel<String> favModel, javax.swing.DefaultListModel<String> recentModel, Runnable fill) {
        java.util.List<String> fav = java.util.Collections.list(favModel.elements()), recent = java.util.Collections.list(recentModel.elements());
        javax.swing.DefaultListModel<String> f2 = new javax.swing.DefaultListModel<String>();
        for (String f : PluginLibrary.favouritePaths()) {
            PluginLibrary.Type t = PluginLibrary.typeOf(new File(f));
            if (t != null && (PluginLibrary.VST3.equals(t.name) || PluginLibrary.VST2.equals(t.name))) f2.addElement(f);
        }
        if (fav.equals(java.util.Collections.list(f2.elements())) && recent.equals(PluginLibrary.recent())) return;
        fill.run();
    }

    /**
     * The Files &amp; locations tab: Carla's program and rack plugin (Browse keeps them in ~/.pulsekit, where
     * vst_render looks first) and the note map, map.txt (Edit: its text, with the pads' notes at hand).
     */
    JPanel filesPanel() {
        final File home = new File(System.getProperty("user.home", "."));
        JPanel out = new JPanel(new java.awt.GridBagLayout());
        out.setName("params-files");
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.insets = new java.awt.Insets(4, 0, 4, 8);
        c.gridy = 0;
        this.locationRow(out, c, "carla-app", PluginFiles.CARLA_APP, PluginFiles.carlaAppFile(home), () -> null, true);
        this.locationRow(out, c, "carla-rack", PluginFiles.CARLA_RACK, PluginFiles.carlaRackFile(home), () -> carlaRack(), false);
        // Note names: as the plugin names them (DigiDrum's C-4 is note 48); the pads, Test and map.txt follow it.
        c.gridx = 0;
        c.weightx = 0;
        out.add(new JLabel("<html><body style='width:" + TAB_LABEL_WIDTH + "px'>" + PluginFiles.NOTE_NAMES + "</body></html>"), c);
        PluginFiles.loadNoteNames(home);
        final javax.swing.JComboBox<String> naming = new javax.swing.JComboBox<String>(Engine.NOTE_NAMING_LABELS);
        naming.setName("params-files-note-names");
        for (int k = 0; k < Engine.NOTE_NAMINGS.length; k++) if (Engine.NOTE_NAMINGS[k] == Engine.noteOctaveAt0) naming.setSelectedIndex(k);
        naming.addActionListener(e -> {
            PluginFiles.saveNoteNames(home, Engine.NOTE_NAMINGS[naming.getSelectedIndex()]);
            app.playback.refreshPadLabels();
            app.setNow("Note names: " + PluginFiles.label() + " (Kick 36 is " + Engine.noteName(36) + ")");
        });
        JPanel namingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        namingRow.add(naming);
        c.gridx = 1;
        c.weightx = 1;
        out.add(namingRow, c);
        c.gridy++;
        c.gridx = 0;
        c.weightx = 0;
        out.add(new JLabel("<html><body style='width:" + TAB_LABEL_WIDTH + "px'>" + PluginFiles.NOTE_MAP + "</body></html>"), c);
        JPanel mapRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        final File map = PluginFiles.noteMap(home);
        final JLabel mapAt = new JLabel(map.isFile() ? map.getPath() : map.getPath() + " (not made yet)");
        mapAt.setName("params-files-map-path");
        JButton edit = new JButton("Edit");
        edit.setName("params-files-map-edit");
        edit.setToolTipText("Opens map.txt: which notes go to which of the plugin's pads (Pads & notes shows Pulsekit's)");
        edit.addActionListener(e -> {
            if (this.editNoteMap(edit, map)) mapAt.setText(map.getPath());
        });
        JButton notes = new JButton(PluginFiles.PAD_NOTES);
        notes.setName("params-files-pad-notes");
        notes.addActionListener(e -> padNotesWindow(notes));
        mapRow.add(edit);
        mapRow.add(notes);
        mapRow.add(mapAt);
        c.gridx = 1;
        c.weightx = 1;
        out.add(mapRow, c);
        return out;
    }

    /** A location row: its label, the path kept (or found), Browse… and Clear. */
    private void locationRow(JPanel out, java.awt.GridBagConstraints c, String name, String label, final File kept, final java.util.function.Supplier<File> found,
        final boolean isApp) {
        c.gridx = 0;
        c.weightx = 0;
        out.add(new JLabel("<html><body style='width:" + TAB_LABEL_WIDTH + "px'>" + label + "</body></html>"), c);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        final JLabel at = new JLabel();
        at.setName("params-files-path:" + name);
        final Runnable show = () -> at.setText(PluginFiles.shown(PluginFiles.kept(kept), PluginFiles.kept(kept).length() > 0 ? null : found.get()));
        show.run();
        JButton browse = new JButton("Browse\u2026");
        browse.setName("params-files-browse:" + name);
        browse.addActionListener(e -> {
            String now = PluginFiles.kept(kept);
            JFileChooser chooser = new JFileChooser(now.length() > 0 ? new File(now).getParentFile() : lastVstFolder());
            chooser.setDialogTitle(isApp ? "Carla's program (Carla.exe)" : "Carla's rack plugin (CarlaVstShell.dll, Carla-Rack)");
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (chooser.showOpenDialog(browse) != JFileChooser.APPROVE_OPTION) return;
            this.keepLocation(browse, kept, chooser.getSelectedFile(), isApp);
            show.run();
        });
        JButton clear = new JButton("Clear");
        clear.setName("params-files-clear:" + name);
        clear.addActionListener(e -> {
            PluginFiles.keep(kept, "");
            show.run();
        });
        row.add(browse);
        row.add(clear);
        row.add(at);
        c.gridx = 1;
        c.weightx = 1;
        out.add(row, c);
        c.gridy++;
    }

    /** A Carla location picked: kept (and said when it is not what the row is for). */
    void keepLocation(java.awt.Component at, File kept, File picked, boolean isApp) {
        boolean right = isApp ? PluginFiles.isCarlaApp(picked) : DllInfo.isRack(picked);
        PluginFiles.keep(kept, picked.getAbsolutePath());
        String said = (right ? "Kept: " : "Kept, though it is not named as Carla's " + (isApp ? "program (Carla.exe)" : "rack (CarlaVstShell.dll, Carla-Rack)") + ": ") + picked.getPath();
        app.setNow(said);
        locationSaid = said;
    }

    /** What a Carla location's Browse said last (for the tests). */
    static String locationSaid = "";

    /**
     * The note map's editor: its text (the template when new); Pads &amp; notes, Check (the map's clear
     * errors, as vst_render says them at a run), Save copy as…; Find, Replace and Replace all; a right-click
     * menu (Cut, Copy, Paste, Select all, and the section clicked: Copy, Cut, Paste after it, Delete…).
     * Save checks the map first and asks when it finds errors. True when saved.
     */
    boolean editNoteMap(java.awt.Component at, File map) {
        final javax.swing.JTextArea text = new javax.swing.JTextArea(PluginFiles.mapText(map), 22, 70);
        JPanel box = this.mapEditor(text, map);
        while (true) {
            int ans = JOptionPane.showOptionDialog(at, box, "Note map \u00b7 map.txt", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new Object[] {"Save", "Cancel"}, "Save");
            if (ans != 0) return false;
            List<String> problems = PluginFiles.mapProblems(text.getText());
            if (!problems.isEmpty()) {
                int go = JOptionPane.showOptionDialog(at, "The check found " + problems.size() + " problem" + (problems.size() == 1 ? "" : "s") + ":\n\n" + String.join("\n", problems),
                    "Note map \u00b7 check", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new Object[] {"Back to editing", "Save anyway"}, "Back to editing");
                if (go != 1) continue;
            }
            boolean ok = PluginFiles.write(map, text.getText());
            app.setNow(ok ? "Saved " + map.getPath() : "Could not save " + map.getPath());
            return ok;
        }
    }

    /** The note map editor's panel around its text (see editNoteMap). */
    JPanel mapEditor(final javax.swing.JTextArea text, final File map) {
        text.setName("params-files-map-text");
        text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        JPanel box = new JPanel(new BorderLayout(0, 6));
        box.setName("params-files-map-editor");
        box.add(new JScrollPane(text), BorderLayout.CENTER);
        JButton notes = new JButton(PluginFiles.PAD_NOTES);
        notes.setName("params-files-map-pad-notes");
        notes.addActionListener(e -> padNotesWindow(notes));
        JButton check = new JButton("Check");
        check.setName("params-files-map-check");
        check.setToolTipText("Clear errors: a line that is no note=note, the kick or snare left out, a tom sent to the kick's or snare's notes, a note or section twice");
        check.addActionListener(e -> {
            List<String> problems = PluginFiles.mapProblems(text.getText());
            mapCheckSaid = problems.isEmpty() ? "No clear errors found" : String.join("\n", problems);
            JOptionPane.showMessageDialog(check, problems.isEmpty() ? "No clear errors found." : "The check found " + problems.size() + " problem" + (problems.size() == 1 ? "" : "s")
                + ":\n\n" + mapCheckSaid, "Note map \u00b7 check", problems.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        });
        JButton copyAs = new JButton("Save copy as\u2026");
        copyAs.setName("params-files-map-save-copy");
        copyAs.setToolTipText("The text as it is now into another file; map.txt stays as it was");
        copyAs.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(map.getParentFile());
            chooser.setSelectedFile(new File(map.getParentFile(), "map-copy.txt"));
            if (chooser.showSaveDialog(copyAs) != JFileChooser.APPROVE_OPTION) return;
            File to = chooser.getSelectedFile();
            app.setNow(PluginFiles.write(to, text.getText()) ? "Saved a copy: " + to.getPath() : "Could not save " + to.getPath());
        });
        JButton importMap = new JButton("Import\u2026");
        importMap.setName("params-files-map-import");
        importMap.setToolTipText("Another map file in place of map.txt (asks first; map.txt is kept as map-backup-<date>.txt)");
        importMap.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(map.getParentFile());
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Map files", "txt"));
            if (chooser.showOpenDialog(importMap) != JFileChooser.APPROVE_OPTION) return;
            this.importNoteMap(importMap, text, chooser.getSelectedFile());
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(new JLabel(map.getPath()));
        top.add(notes);
        top.add(check);
        top.add(importMap);
        top.add(copyAs);
        box.add(top, BorderLayout.NORTH);
        // Find and replace (case aside).
        final javax.swing.JTextField find = new javax.swing.JTextField(12), with = new javax.swing.JTextField(12);
        find.setName("params-files-map-find");
        with.setName("params-files-map-replace-with");
        final JLabel said = new JLabel(" ");
        said.setName("params-files-map-find-said");
        JButton next = new JButton("Find next");
        next.setName("params-files-map-find-next");
        final Runnable findNext = () -> {
            int from = text.getSelectionEnd();
            int found = PluginFiles.findNext(text.getText(), find.getText(), from);
            if (found < 0) {
                said.setText(find.getText().length() == 0 ? " " : "Not found");
                return;
            }
            text.requestFocusInWindow();
            text.select(found, found + find.getText().length());
            said.setText(found < from ? "From the start again" : " ");
        };
        next.addActionListener(e -> findNext.run());
        find.addActionListener(e -> findNext.run());
        JButton replace = new JButton("Replace");
        replace.setName("params-files-map-replace");
        replace.addActionListener(e -> {
            String sel = text.getSelectedText();
            if (sel != null && sel.equalsIgnoreCase(find.getText()) && find.getText().length() > 0) text.replaceSelection(with.getText());
            findNext.run();
        });
        JButton all = new JButton("Replace all");
        all.setName("params-files-map-replace-all");
        all.addActionListener(e -> {
            int[] n = new int[1];
            String now = PluginFiles.replaceAll(text.getText(), find.getText(), with.getText(), n);
            if (n[0] > 0) text.setText(now);
            said.setText(n[0] == 0 ? "Not found" : "Replaced " + n[0]);
        });
        JPanel findRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        findRow.add(new JLabel("Find"));
        findRow.add(find);
        findRow.add(next);
        findRow.add(new JLabel("Replace with"));
        findRow.add(with);
        findRow.add(replace);
        findRow.add(all);
        findRow.add(said);
        box.add(findRow, BorderLayout.SOUTH);
        text.setComponentPopupMenu(null);
        text.addMouseListener(new java.awt.event.MouseAdapter() {
            void maybe(java.awt.event.MouseEvent ev) {
                if (!ev.isPopupTrigger()) return;
                int offset = text.viewToModel(ev.getPoint());
                if (text.getSelectionStart() == text.getSelectionEnd() || offset < text.getSelectionStart() || offset > text.getSelectionEnd()) text.setCaretPosition(Math.max(0, offset));
                mapMenu(text, offset).show(text, ev.getX(), ev.getY());
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent ev) {
                maybe(ev);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent ev) {
                maybe(ev);
            }
        });
        lastMapText = text;
        lastMapEditor = box;
        return box;
    }

    /**
     * Import: a map file in place of map.txt, when it is one (note=note lines) and the user says so; map.txt
     * is kept as map-backup-<date>.txt first, and the editor shows the map imported. What it said.
     */
    String importNoteMap(java.awt.Component at, javax.swing.JTextArea text, File from) {
        final File home = new File(System.getProperty("user.home", "."));
        String t = PluginFiles.read(from);
        String why = PluginFiles.notAMap(t);
        if (why != null) {
            mapImportSaid = from.getName() + " is not a note map: " + why;
            if (!pluginTestQuiet) JOptionPane.showMessageDialog(at, mapImportSaid, "Note map \u00b7 import", JOptionPane.WARNING_MESSAGE);
            return mapImportSaid;
        }
        List<String> problems = PluginFiles.mapProblems(t);
        String ask = "Replace your note map (map.txt) with " + from.getName() + "?\nYour map now is kept as a backup beside it (map-backup-<date>.txt)."
            + (problems.isEmpty() ? "" : "\n\nThe check finds in it:\n" + String.join("\n", problems));
        if (!pluginTestQuiet && JOptionPane.showConfirmDialog(at, ask, "Note map \u00b7 import", JOptionPane.YES_NO_OPTION,
            problems.isEmpty() ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            mapImportSaid = "Not imported";
            return mapImportSaid;
        }
        File backup = PluginFiles.importMap(home, t);
        text.setText(t);
        text.setCaretPosition(0);
        mapImportSaid = "Imported " + from.getName() + " as map.txt" + (backup != null ? "; the map before is kept as " + backup.getName() : "");
        app.setNow(mapImportSaid);
        return mapImportSaid;
    }

    static String mapImportSaid = "";

    /** The note map editor's right-click menu at `offset`: Cut, Copy, Paste, Select all; the section there. */
    javax.swing.JPopupMenu mapMenu(final javax.swing.JTextArea text, int offset) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.setName("params-files-map-menu");
        boolean selected = text.getSelectionStart() != text.getSelectionEnd();
        javax.swing.JMenuItem cut = new javax.swing.JMenuItem("Cut"), copy = new javax.swing.JMenuItem("Copy"), paste = new javax.swing.JMenuItem("Paste"),
            selectAll = new javax.swing.JMenuItem("Select all");
        cut.setEnabled(selected);
        copy.setEnabled(selected);
        cut.addActionListener(e -> text.cut());
        copy.addActionListener(e -> text.copy());
        paste.addActionListener(e -> text.paste());
        selectAll.addActionListener(e -> text.selectAll());
        menu.add(cut);
        menu.add(copy);
        menu.add(paste);
        menu.add(selectAll);
        menu.addSeparator();
        final int[] at = PluginFiles.sectionAt(text.getText(), offset);
        final String name = at == null ? null : PluginFiles.sectionName(text.getText(), at);
        String clip = clipboardText();
        final boolean isSection = clip != null && clip.trim().startsWith("[") && clip.trim().split("\n", 2)[0].trim().endsWith("]");
        javax.swing.JMenuItem copySec = new javax.swing.JMenuItem(name == null ? "Copy section" : "Copy section [" + name + "]");
        javax.swing.JMenuItem cutSec = new javax.swing.JMenuItem(name == null ? "Cut section" : "Cut section [" + name + "]");
        javax.swing.JMenuItem pasteSec = new javax.swing.JMenuItem(name == null ? "Paste section at the end" : "Paste section after [" + name + "]");
        javax.swing.JMenuItem deleteSec = new javax.swing.JMenuItem(name == null ? "Delete section\u2026" : "Delete section [" + name + "]\u2026");
        copySec.setEnabled(at != null);
        cutSec.setEnabled(at != null);
        deleteSec.setEnabled(at != null);
        pasteSec.setEnabled(isSection);
        copySec.addActionListener(e -> copyText(text.getText().substring(at[0], at[1])));
        cutSec.addActionListener(e -> {
            copyText(text.getText().substring(at[0], at[1]));
            text.setText(PluginFiles.withoutSection(text.getText(), at));
            text.setCaretPosition(Math.min(at[0], text.getText().length()));
        });
        pasteSec.addActionListener(e -> {
            String c = clipboardText();
            if (c != null) text.setText(PluginFiles.pasteSection(text.getText(), at, c));
        });
        deleteSec.addActionListener(e -> {
            int lines = text.getText().substring(at[0], at[1]).trim().split("\n").length;
            if (JOptionPane.showConfirmDialog(text, "Delete the section [" + name + "] (" + lines + " line" + (lines == 1 ? "" : "s") + ")?", "Note map \u00b7 delete section",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            text.setText(PluginFiles.withoutSection(text.getText(), at));
            text.setCaretPosition(Math.min(at[0], text.getText().length()));
        });
        menu.add(copySec);
        menu.add(cutSec);
        menu.add(pasteSec);
        menu.add(deleteSec);
        lastMapMenu = menu;
        return menu;
    }

    static String clipboardText() {
        try {
            return (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
        } catch (Exception ex) {
            return null;
        }
    }

    static void copyText(String t) {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(t), null);
        } catch (Exception ex) {
            // (no clipboard: nothing copied)
        }
    }

    /** The map editor's panel and right-click menu shown last, and what Check said (for the tests). */
    static JPanel lastMapEditor;
    static javax.swing.JPopupMenu lastMapMenu;
    static String mapCheckSaid = "";

    /** The note map editor's text shown last (for the tests). */
    static javax.swing.JTextArea lastMapText;

    /** Pulsekit's pads with their MIDI notes, beside the map's editor (not modal). */
    static void padNotesWindow(java.awt.Component at) {
        javax.swing.JTextArea t = new javax.swing.JTextArea(PluginFiles.padNotes());
        t.setEditable(false);
        t.setName("params-pad-notes-text");
        t.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
        javax.swing.JDialog d = new javax.swing.JDialog(SwingUtilities.getWindowAncestor(at), PluginFiles.PAD_NOTES, java.awt.Dialog.ModalityType.MODELESS);
        d.setName("params-pad-notes");
        d.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        d.add(new JScrollPane(t));
        d.pack();
        d.setLocationRelativeTo(at);
        d.setVisible(true);
        lastPadNotes = d;
    }

    static javax.swing.JDialog lastPadNotes;

    /** One of the Plugins tab's plugin lists: a click shows Use as plugin 1 to 4. */
    JPanel pickList(String title, String name, javax.swing.DefaultListModel<String> model, final List<ProgramParams.Param> ps, final String[] values,
        final JLabel[] fileLabels, final javax.swing.JCheckBox[] uses, String empty) {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.add(new JLabel(title), BorderLayout.NORTH);
        final javax.swing.JList<String> list = new javax.swing.JList<String>(model);
        list.setName(name);
        list.setVisibleRowCount(5);
        list.setToolTipText(empty);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean sel, boolean focus) {
                java.awt.Component c = super.getListCellRendererComponent(l, plugName(String.valueOf(v)), i, sel, focus);
                ((javax.swing.JComponent) c).setToolTipText(String.valueOf(v));
                return c;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent ev) {
                int at = list.locationToIndex(ev.getPoint());
                if (at < 0) return;
                list.setSelectedIndex(at);
                useMenu(ps, values, fileLabels, uses, list.getModel().getElementAt(at)).show(list, ev.getX(), ev.getY());
            }
        });
        // Dragged onto a plugin row: that row's plugin.
        list.setDragEnabled(true);
        list.setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public int getSourceActions(javax.swing.JComponent c) {
                return COPY;
            }

            @Override
            protected java.awt.datatransfer.Transferable createTransferable(javax.swing.JComponent c) {
                String v = list.getSelectedValue();
                return v == null ? null : new java.awt.datatransfer.StringSelection(v);
            }
        });
        box.add(new JScrollPane(list), BorderLayout.CENTER);
        return box;
    }

    /** A plugin row taking a plugin dragged from Favourites or Recently used (or a plugin file from the desktop). */
    javax.swing.TransferHandler pluginDrop(final List<ProgramParams.Param> ps, final String[] values, final JLabel[] fileLabels, final javax.swing.JCheckBox[] uses,
        final String token) {
        return new javax.swing.TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor) || s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport s) {
                try {
                    String path;
                    if (s.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        List<?> files = (List<?>) s.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                        if (files.isEmpty()) return false;
                        path = ((File) files.get(0)).getAbsolutePath();
                    } else {
                        path = String.valueOf(s.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor)).trim();
                    }
                    if (path.length() == 0 || !new File(path).exists()) return false;
                    usePlugin(ps, values, fileLabels, uses, token, path);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        };
    }

    /** The menu a listed plugin shows: Use as plugin 1 to 4 (the page's plugin rows). */
    javax.swing.JPopupMenu useMenu(final List<ProgramParams.Param> ps, final String[] values, final JLabel[] fileLabels, final javax.swing.JCheckBox[] uses, final String path) {
        javax.swing.JPopupMenu m = new javax.swing.JPopupMenu();
        m.setName("params-use-menu");
        for (final String token : ProgramParams.pluginRows(ps)) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(ProgramParams.useAs(token));
            item.setName("params-use-as:" + token);
            item.addActionListener(e -> this.usePlugin(ps, values, fileLabels, uses, token, path));
            m.add(item);
        }
        MenuCancel.add(m);
        lastUseMenu = m;
        return m;
    }

    /** The Use as plugin menu shown last (for the tests). */
    static javax.swing.JPopupMenu lastUseMenu;

    /** A plugin put on a row (and its Use ticked). */
    void usePlugin(List<ProgramParams.Param> ps, String[] values, JLabel[] fileLabels, javax.swing.JCheckBox[] uses, String token, String path) {
        for (int i = 0; i < ps.size(); i++) {
            if (!ps.get(i).token.equals(token)) continue;
            values[i] = path;
            if (fileLabels[i] != null) fileLabels[i].setText(plugName(path));
            if (uses[i] != null) uses[i].setSelected(true);
            app.setNow(plugName(path) + " is " + ProgramParams.useAs(token).substring(7));
        }
    }

    /** Clear beside a list's Choose: shown while something is chosen or typed (SogniMusic's --workflow back to a new run). */
    private JButton choiceClear(ProgramParams.Param p, final javax.swing.text.JTextComponent field) {
        final JButton clear = new JButton("Clear");
        clear.setName("params-clear:" + p.token);
        clear.addActionListener(e -> field.setText(""));
        clear.setVisible(field.getText().trim().length() > 0);
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            void now() {
                boolean show = field.getText().trim().length() > 0;
                if (clear.isVisible() != show) {
                    clear.setVisible(show);
                    clear.getParent().revalidate();
                }
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { now(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { now(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { now(); }
        });
        return clear;
    }

    private JButton clearButton(ProgramParams.Param p, final String[] values, final int index, final JLabel chosen) {
        JButton clear = new JButton("Clear");
        clear.setName("params-clear:" + p.token);
        clear.addActionListener(e -> {
            values[index] = "";
            chosen.setText("None");
        });
        return clear;
    }

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
