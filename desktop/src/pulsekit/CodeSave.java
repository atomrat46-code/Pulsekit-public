package pulsekit;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;

/**
 * PyJav's code editor: Save and Save as on right click. Save writes back to the file the program
 * was opened from, or to the last Save as place; a bundled program has neither, so Save asks for
 * a place. Binary programs and prompt sheets (whose editor shows only the body) are not saved here.
 */
final class CodeSave {
    final Pulsekit app;
    /** Where Save writes, and the editor file it belongs to. */
    File target;
    String targetFor;
    /** A Code menu file shown in the editor over the program pyName. */
    String codeName;
    String codeFor;

    CodeSave(Pulsekit app) {
        this.app = app;
    }

    void attach(final JTextArea editor) {
        editor.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) show(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) show(e);
            }

            void show(MouseEvent e) {
                JPopupMenu menu = CodeSave.this.menu();
                if (menu != null) menu.show(editor, e.getX(), e.getY());
            }
        });
    }

    /** The right-click menu, or null when the editor holds nothing to save. */
    JPopupMenu menu() {
        if (!this.canSave()) return null;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem save = new JMenuItem("Save");
        save.addActionListener(a -> this.save());
        JMenuItem saveAs = new JMenuItem("Save as");
        saveAs.addActionListener(a -> this.saveAs());
        menu.add(save);
        menu.add(saveAs);
        return menu;
    }

    boolean canSave() {
        if (app.pyBytes != null || app.pyEditor == null || !app.pyEditor.isEditable()) return false;
        String n = this.name().toLowerCase();
        return !(n.endsWith(".prompt") || n.endsWith(".class") || n.endsWith(".jar"));
    }

    /** The editor's file name: a Code menu file, else the program. */
    String name() {
        String program = app.pyName == null || app.pyName.length() == 0 ? "script.py" : app.pyName;
        if (this.codeName != null && program.equals(this.codeFor) && !app.programMenus.editorShowsListed) return this.codeName;
        return program;
    }

    String text() {
        return app.pyEditor == null || app.pyEditor.getText() == null ? "" : app.pyEditor.getText();
    }

    /** The program was read from this file; Save writes back to it. */
    void opened(File file) {
        this.codeName = null;
        this.target = file;
        this.targetFor = this.name();
    }

    /** A Code menu file is now in the editor. */
    void openedCode(String name) {
        this.codeName = name;
        this.codeFor = app.pyName == null || app.pyName.length() == 0 ? "script.py" : app.pyName;
    }

    void save() {
        if (this.target != null && this.name().equals(this.targetFor) && this.write(this.target)) return;
        this.saveAs();
    }

    void saveAs() {
        JFileChooser chooser = new JFileChooser();
        File dir = this.target != null && this.target.getParentFile() != null ? this.target.getParentFile() : chooser.getCurrentDirectory();
        chooser.setSelectedFile(new File(dir, this.name()));
        if (chooser.showSaveDialog(app) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return;
        this.saveTo(chooser.getSelectedFile());
    }

    /** Save as this file; Save writes there from now on. */
    void saveTo(File file) {
        String forName = this.name();
        if (this.write(file)) {
            this.target = file;
            this.targetFor = forName;
        }
    }

    boolean write(File file) {
        try {
            Files.write(file.toPath(), this.text().getBytes(StandardCharsets.UTF_8));
            app.setNow("Saved " + file.getName());
            return true;
        } catch (Exception ex) {
            app.setNow("Could not save " + file.getName());
            return false;
        }
    }
}
