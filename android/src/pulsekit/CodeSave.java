package pulsekit;

import android.content.Intent;
import android.net.Uri;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

/**
 * PyJav's code editor: Save and Save as in the long-press text menu, beside Cut, Copy and Paste.
 * Save writes back to the file the program was opened from, or to the last Save as place; a
 * bundled program has neither, so Save asks for a place. Binary programs and prompt sheets
 * (whose editor shows only the body) are not saved from here.
 */
final class CodeSave {
    static final int SAVE_AS = 32;
    static final int ID_SAVE = 0x5a01;
    static final int ID_SAVE_AS = 0x5a02;

    final MainActivity app;
    /** Where Save writes, and the editor file it belongs to. */
    Uri target;
    String targetFor;
    /** A Code menu file shown in the editor over the program pyName. */
    String codeName;
    String codeFor;
    /** Text waiting for the Save as dialog to return a place. */
    String pending;
    String pendingFor;

    CodeSave(MainActivity app) {
        this.app = app;
    }

    void attach(EditText editor) {
        ActionMode.Callback menu = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu m) {
                if (CodeSave.this.canSave()) {
                    m.add(Menu.NONE, ID_SAVE, 200, "Save");
                    m.add(Menu.NONE, ID_SAVE_AS, 201, "Save as");
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu m) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == ID_SAVE) CodeSave.this.save();
                else if (item.getItemId() == ID_SAVE_AS) CodeSave.this.saveAs();
                else return false;
                if (mode != null) mode.finish();
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {}
        };
        editor.setCustomSelectionActionModeCallback(menu);
        editor.setCustomInsertionActionModeCallback(menu);
    }

    boolean canSave() {
        if (app.pkPyBytes != null) return false;
        String n = this.name().toLowerCase();
        return !(n.endsWith(".prompt") || n.endsWith(".class") || n.endsWith(".jar"));
    }

    /** The editor's file name: a Code menu file, else the program. */
    String name() {
        String program = app.pyName == null || app.pyName.length() == 0 ? "script.py" : app.pyName;
        if (this.codeName != null && program.equals(this.codeFor) && !app.programMenus.editorShowsRun) return this.codeName;
        return program;
    }

    String text() {
        return app.pyEditor == null || app.pyEditor.getText() == null ? "" : app.pyEditor.getText().toString();
    }

    /** The program was read from this document; Save writes back to it. */
    void opened(Uri uri) {
        this.codeName = null;
        this.target = uri;
        this.targetFor = this.name();
    }

    /** A Code menu file is now in the editor. */
    void openedCode(String name) {
        this.codeName = name;
        this.codeFor = app.pyName == null || app.pyName.length() == 0 ? "script.py" : app.pyName;
    }

    void save() {
        if (this.target != null && this.name().equals(this.targetFor) && this.write(this.target, this.text())) return;
        this.saveAs();
    }

    void saveAs() {
        this.pending = this.text();
        this.pendingFor = this.name();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Not text/plain: the picker would add .txt to CutWav.java.
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, this.pendingFor);
        app.startActivityForResult(intent, SAVE_AS);
    }

    /** The place the Save as dialog returned; Save writes there from now on. */
    void savedAs(Uri uri) {
        String text = this.pending;
        String forName = this.pendingFor;
        this.pending = null;
        this.pendingFor = null;
        if (text == null || uri == null) return;
        if (this.write(uri, text)) {
            this.target = uri;
            this.targetFor = forName;
        }
    }

    boolean write(Uri uri, String text) {
        try {
            java.io.OutputStream out = app.getContentResolver().openOutputStream(uri, "wt");
            if (out == null) throw new java.io.IOException("no output");
            try {
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            app.setNow("Saved " + this.name());
            return true;
        } catch (Exception ex) {
            app.setNow("Could not save " + this.name());
            return false;
        }
    }
}
