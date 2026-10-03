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
    static final String[] PROGRAM_KINDS = {"Java", "Python", "Code"};

    final Pulsekit app;

    ProgramMenus(Pulsekit app) {
        this.app = app;
    }

    final JButton[] programButtons = new JButton[PROGRAM_KINDS.length];

    String listedName;

    String listedKind;

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
                else this.selectListedProgram(kind, name);
            });
            menu.add(item);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    /** Java or Python: this file becomes the program Run executes. */
    void selectListedProgram(String kind, String name) {
        try {
            byte[] data = ProgramFiles.read(kind, name);
            String src = new String(data, StandardCharsets.UTF_8);
            app.pyName = name;
            app.pyBytes = null;
            app.pyInputPath = null;
            this.listedName = name;
            app.listedSource = src;
            this.listedKind = kind;
            if (app.pyEditor != null) {
                app.pyEditor.setEditable(true);
                app.pyEditor.setText(src);
                app.pyEditor.setCaretPosition(0);
            }
            this.editorShowsListed = app.pyEditor != null;
            app.pyJav.showPromptModes();
            app.pyJav.showPyHint(PyJavHints.status(name, src, data));
            this.paintProgramMenus();
            app.setNow("Run \u00b7 " + name);
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
        return this.listedName != null && this.listedName.equals(app.pyName) && app.pyBytes == null;
    }

    void paintProgramMenus() {
        if (this.listedName != null && !this.listedProgramCurrent()) {
            this.listedName = null;
            app.listedSource = null;
            this.listedKind = null;
        }
        for (int i = 0; i < 2; i++) {
            if (this.programButtons[i] == null) continue;
            boolean on = PROGRAM_KINDS[i].equals(this.listedKind);
            this.programButtons[i].setText(on ? PROGRAM_KINDS[i] + " \u00b7 " + this.listedName : PROGRAM_KINDS[i] + " \u25be");
            this.programButtons[i].setBackground(on ? HIT : ELEV);
            this.programButtons[i].setForeground(on ? BG : FG);
        }
    }
}
