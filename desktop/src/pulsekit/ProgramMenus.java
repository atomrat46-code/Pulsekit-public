package pulsekit;

import java.awt.FlowLayout;
import java.nio.charset.StandardCharsets;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import static pulsekit.Pulsekit.*;

/** The Java, Python and Code menus on the PyJav page (the bundled Programs folder). */
final class ProgramMenus {
    // Java and Python pick the program Run executes and show its source in the editor; edits there run.
    // Code opens a file in the editor for editing and never changes what Run executes.
    // Scripts lists the repo's Prompts folder (.prompt files); one opens like any .prompt file.
    static final String[] PROGRAM_KINDS = {"Java", "Python", "Code", "Scripts"};

    final Pulsekit app;

    ProgramMenus(Pulsekit app) {
        this.app = app;
    }

    final JButton[] programButtons = new JButton[PROGRAM_KINDS.length];

    String listedName;

    String listedKind;

    /** A listed .jar or .class: the bytes Run executes (null for source). */
    byte[] listedBytes;

    /** The Scripts file open in PyJav, while it is still the current program. */
    String scriptName;

    /** True while the editor shows the listed program (a Code file was not opened over it). */
    boolean editorShowsListed;

    JPanel buildProgramMenus() {
        JPanel row = new JPanel(new FlowLayout(0, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(0.0f);
        for (int i = 0; i < PROGRAM_KINDS.length; i++) {
            final String kind = PROGRAM_KINDS[i];
            final JButton[] self = new JButton[1];
            self[0] = app.action(kind + " \u25be", ELEV, FG, () -> this.showProgramMenu(kind, self[0]));
            this.programButtons[i] = self[0];
            row.add(self[0]);
        }
        return row;
    }

    void showProgramMenu(String kind, JButton anchor) {
        String[] names = ProgramFiles.list(kind);
        JPopupMenu menu = new JPopupMenu();
        if (names.length == 0) {
            JMenuItem none = new JMenuItem("No programs in Programs/" + kind);
            none.setEnabled(false);
            menu.add(none);
        }
        for (String name : names) {
            JMenuItem item = new JMenuItem(name);
            item.addActionListener(e -> {
                if ("Code".equals(kind)) this.openCodeFile(name);
                else if ("Scripts".equals(kind)) this.openScript(name);
                else this.selectListedProgram(kind, name);
            });
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    /** Java or Python: this file becomes the program Run executes. */
    void selectListedProgram(String kind, String name) {
        // Another program: Return (back to the Media browser after ImageUpscaler) goes.
        app.pyJav.setReturn(null);
        try {
            byte[] data = ProgramFiles.read(kind, name);
            String low = name.toLowerCase();
            boolean binary = low.endsWith(".jar") || low.endsWith(".class");
            // A .jar or .class runs as it is; the editor only names it.
            String src = binary ? "" : new String(data, StandardCharsets.UTF_8);
            app.pyName = name;
            app.pyBytes = binary ? data : null;
            app.pyInputPath = null;
            this.listedName = name;
            app.listedSource = src;
            this.listedKind = kind;
            this.listedBytes = app.pyBytes;
            if (app.pyEditor != null) {
                app.pyEditor.setEditable(!binary);
                app.pyEditor.setText(binary ? "// " + name + "\n// Binary program. Run uses this file.\n// Extra args are passed to java.\n" : src);
                app.pyEditor.setCaretPosition(0);
            }
            this.editorShowsListed = app.pyEditor != null && !binary;
            app.pyJav.showPromptModes();
            app.pyJav.showPyHint(PyJavHints.status(name, src, data));
            this.paintProgramMenus();
            app.setNow("Run \u00b7 " + name);
        } catch (Exception ex) {
            if (app.pyLog != null) app.pyLog.setText("Could not open " + name + ": " + ex.getMessage());
        }
    }

    /** Scripts: open a .prompt from the Prompts folder, as a picked .prompt file opens. */
    void openScript(String name) {
        try {
            byte[] data = ProgramFiles.read("Scripts", name);
            this.scriptName = name;
            app.pyJav.loadProgram(name, data, false);
            app.pyInputPath = null;
            PyJavRecent.remember(app.pyJav.pyRecentDir(), name, "", new String(data, StandardCharsets.UTF_8), null);
            app.pyJav.reloadPyRecent(0);
            app.pyJav.showPyHint("Run as bash, cmd, or AI.");
            app.setNow("Script \u00b7 " + name);
        } catch (Exception ex) {
            if (app.pyLog != null) app.pyLog.setText("Could not open " + name + ": " + ex.getMessage());
        }
    }

    /** Code: show the file in the editor for editing. What Run executes does not change. */
    void openCodeFile(String name) {
        try {
            String text = new String(ProgramFiles.read("Code", name), StandardCharsets.UTF_8);
            if (app.pyEditor != null) {
                app.pyEditor.setEditable(true);
                app.pyEditor.setText(text);
                app.pyEditor.setCaretPosition(0);
            }
            this.editorShowsListed = false;
            app.codeSave.openedCode(name);
            if (this.programButtons[2] != null) this.programButtons[2].setText("Code \u00b7 " + name);
            app.setNow("Editing \u00b7 " + name);
        } catch (Exception ex) {
            if (app.pyLog != null) app.pyLog.setText("Could not open " + name + ": " + ex.getMessage());
        }
    }

    /** Source Run should execute: the listed program while it is still current (as edited in the editor), else null. */
    String listedSourceToRun() {
        if (!this.listedProgramCurrent()) return null;
        if (this.editorShowsListed && app.pyEditor != null) return app.pyEditor.getText();
        return app.listedSource;
    }

    /** True while the listed program is still the current program (nothing else was opened since). */
    boolean listedProgramCurrent() {
        return this.listedName != null && this.listedName.equals(app.pyName) && app.pyBytes == this.listedBytes;
    }

    void paintProgramMenus() {
        if (this.listedName != null && !this.listedProgramCurrent()) {
            this.listedName = null;
            app.listedSource = null;
            this.listedBytes = null;
            this.listedKind = null;
        }
        for (int i = 0; i < 2; i++) {
            if (this.programButtons[i] == null) continue;
            boolean on = PROGRAM_KINDS[i].equals(this.listedKind);
            this.programButtons[i].setText(on ? PROGRAM_KINDS[i] + " \u00b7 " + this.listedName : PROGRAM_KINDS[i] + " \u25be");
            this.programButtons[i].setBackground(on ? HIT : ELEV);
            this.programButtons[i].setForeground(on ? BG : FG);
        }
        boolean script = this.scriptName != null && this.scriptName.equals(app.pyName);
        if (!script) this.scriptName = null;
        JButton s = this.programButtons[3];
        if (s != null) {
            s.setText(script ? "Scripts \u00b7 " + this.scriptName : "Scripts \u25be");
            s.setBackground(script ? HIT : ELEV);
            s.setForeground(script ? BG : FG);
        }
    }
}
