package pulsekit;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;

/**
 * PyJav's Open in editor window: the Output's or the Code's text in a window as big as the screen,
 * with Save, Save as, Undo and Redo, Find, word wrap, and (opened from Code) Back to Code, which
 * puts the edited text in the Code area. Ctrl+S, Ctrl+Shift+S, Ctrl+Z, Ctrl+Y, Ctrl+F; closing
 * with unsaved changes asks first.
 */
final class TextEditorWindow {
    final Pulsekit app;
    final JDialog window;
    final JTextArea text = new JTextArea();
    final UndoManager undo = new UndoManager();
    final JLabel state = new JLabel(" ");
    /** Back to Code: the edited text into the Code area (null: opened from Output). */
    final Consumer<String> backTo;
    /** The name Save as offers, and the file Save writes once there is one. */
    String name;
    File file;
    boolean changed;

    /** The window opened last (for the tests). */
    static TextEditorWindow last;
    /** The tests' Save as place (null: the file dialog asks). */
    static File saveAsFor;

    private TextEditorWindow(Pulsekit app, String title, String name, String content, Consumer<String> backTo) {
        this.app = app;
        this.name = name == null || name.trim().length() == 0 ? "text.txt" : name;
        this.backTo = backTo;
        this.window = new JDialog(app, title, false);
        this.window.setName("text-editor");
        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(UiKit.BG);
        root.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);
        bar.add(this.button("Save", "editor-save", this::save));
        bar.add(this.button("Save as…", "editor-save-as", this::saveAs));
        bar.add(this.button("Undo", "editor-undo", this::undo));
        bar.add(this.button("Redo", "editor-redo", this::redo));
        bar.add(this.button("Find…", "editor-find", this::find));
        JButton wrap = this.button("Wrap", "editor-wrap", null);
        wrap.addActionListener(e -> {
            this.text.setLineWrap(!this.text.getLineWrap());
            this.text.setWrapStyleWord(true);
            app.paintOutline(wrap, this.text.getLineWrap());
        });
        app.paintOutline(wrap, false);
        bar.add(wrap);
        if (backTo != null) bar.add(this.button("Back to Code", "editor-back", this::back));
        bar.add(this.button("Close", "editor-close", this::close));
        this.state.setForeground(UiKit.MUTED);
        bar.add(this.state);
        root.add(bar, BorderLayout.NORTH);
        this.text.setText(content == null ? "" : content);
        this.text.setCaretPosition(0);
        this.text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        this.text.setBackground(UiKit.ELEV);
        this.text.setForeground(UiKit.FG);
        this.text.setCaretColor(UiKit.FG);
        this.text.setTabSize(4);
        this.text.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        this.text.setName("editor-text");
        TextMenu.attach(this.text);
        this.text.getDocument().addUndoableEditListener(this.undo);
        this.text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                TextEditorWindow.this.mark(true);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                TextEditorWindow.this.mark(true);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attributes only
            }
        });
        JScrollPane scroll = new JScrollPane(this.text);
        scroll.setBorder(BorderFactory.createLineBorder(UiKit.BORDER));
        root.add(scroll, BorderLayout.CENTER);
        this.window.setContentPane(root);
        this.keys(root);
        this.window.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        this.window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                TextEditorWindow.this.close();
            }
        });
        // As big as the screen's usable part (not under the taskbar).
        Rectangle usable = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        this.window.setBounds(usable);
        this.mark(false);
    }

    /** The text in the editor window; `backTo` (or null) takes it back to where it came from. */
    static TextEditorWindow open(Pulsekit app, String title, String name, String content, Consumer<String> backTo) {
        TextEditorWindow w = new TextEditorWindow(app, title, name, content, backTo);
        last = w;
        w.window.setVisible(true);
        w.text.requestFocusInWindow();
        return w;
    }

    private JButton button(String label, String id, Runnable click) {
        JButton b = new JButton(label);
        this.app.flatten(b);
        this.app.paintOutline(b, false);
        b.setName(id);
        if (click != null) b.addActionListener(e -> click.run());
        return b;
    }

    private void keys(JComponent root) {
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "save", this::save);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "save-as", this::saveAs);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "undo", this::undo);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "redo", this::redo);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "find", this::find);
        bind(root, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close", this::close);
    }

    private static void bind(JComponent root, KeyStroke key, String name, Runnable run) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        });
    }

    /** The title shows the file and a star while there are unsaved changes. */
    void mark(boolean changed) {
        this.changed = changed;
        String where = this.file != null ? this.file.getName() : this.name;
        this.window.setTitle((changed ? "* " : "") + where + " — Pulsekit editor");
        this.state.setText(changed ? "Not saved" : (this.file != null ? "Saved: " + this.file.getAbsolutePath() : " "));
    }

    /** Save: to the file saved to before, else as Save as. Whether it was saved. */
    boolean save() {
        if (this.file == null) return this.saveAs();
        return this.write(this.file);
    }

    boolean saveAs() {
        File to = saveAsFor;
        if (to == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), this.file != null ? this.file.getName() : this.name));
            if (chooser.showSaveDialog(this.window) != JFileChooser.APPROVE_OPTION) return false;
            to = chooser.getSelectedFile();
        }
        return this.write(to);
    }

    private boolean write(File to) {
        try {
            if (to.getParentFile() != null) to.getParentFile().mkdirs();
            Files.write(to.toPath(), this.text.getText().getBytes(StandardCharsets.UTF_8));
            this.file = to;
            this.mark(false);
            this.app.setNow("Saved " + to.getName());
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this.window, "Could not save " + to.getName() + ": " + ex.getMessage(), "Save", JOptionPane.WARNING_MESSAGE);
            return false;
        }
    }

    void undo() {
        if (this.undo.canUndo()) this.undo.undo();
    }

    void redo() {
        if (this.undo.canRedo()) this.undo.redo();
    }

    /** Find: the next place the words are, from the caret (and from the top again). */
    void find() {
        String what = JOptionPane.showInputDialog(this.window, "Find:", "Find", JOptionPane.QUESTION_MESSAGE);
        if (what == null || what.length() == 0) return;
        this.findNext(what);
    }

    boolean findNext(String what) {
        String all = this.text.getText();
        int from = Math.max(this.text.getSelectionEnd(), 0);
        int at = all.toLowerCase().indexOf(what.toLowerCase(), from);
        if (at < 0) at = all.toLowerCase().indexOf(what.toLowerCase());
        if (at < 0) {
            this.state.setText("Not found: " + what);
            return false;
        }
        this.text.requestFocusInWindow();
        this.text.select(at, at + what.length());
        return true;
    }

    /** Back to Code: the text into the Code area; the window stays open. */
    void back() {
        if (this.backTo == null) return;
        this.backTo.accept(this.text.getText());
        this.app.setNow("The edited text is in the Code area");
    }

    /** Close: unsaved changes are asked about first (Save, Don't save, Cancel). */
    void close() {
        if (this.changed) {
            Object[] options = {"Save", "Don't save", "Cancel"};
            int a = JOptionPane.showOptionDialog(this.window, "Save the changes to " + (this.file != null ? this.file.getName() : this.name) + "?", "Close the editor",
                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (a == 2 || a < 0) return;
            if (a == 0 && !this.save()) return;
        }
        this.window.dispose();
    }
}
