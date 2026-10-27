package pulsekit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PyJav's Open in editor window on the phone: the Output's or the Code's text on a screen of its
 * own, with Save, Save as, Undo and Redo, Find and (opened from Code) Back to Code, which puts the
 * edited text in the Code area. Back (or Close) with unsaved changes asks first.
 */
final class TextEditorScreen {
    static final int SAVE_AS = 61;

    /** Back to Code takes the edited text (null: opened from Output). */
    interface Back {
        void take(String text);
    }

    final MainActivity app;
    final Dialog dialog;
    final EditText text;
    final TextView state;
    final Back back;
    String name;
    /** The document Save writes to once Save as has chosen one. */
    Uri file;
    boolean changed;
    /** Undo's steps: the text as it was, a step about every half second of typing. */
    final List<String> steps = new ArrayList<String>();
    int at;
    private boolean applying;
    private final Handler later = new Handler(Looper.getMainLooper());
    private final Runnable keep = this::keepStep;

    /** The screen open now (the tests, and Save as's answer). */
    static TextEditorScreen last;

    private TextEditorScreen(final MainActivity app, String title, String name, String content, Back back) {
        this.app = app;
        this.name = name == null || name.trim().length() == 0 ? "text.txt" : name;
        this.back = back;
        this.dialog = new Dialog(app, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout root = app.col();
        root.setBackgroundColor(UiKit.BG);
        root.setPadding(app.dp(10), app.dp(10), app.dp(10), app.dp(10));
        TextView head = app.text(title, 16, true);
        head.setTag("editor-title");
        root.addView(head);
        LinearLayout bar = app.row();
        bar.setPadding(0, app.dp(6), 0, app.dp(6));
        bar.addView(this.pill("Save", "editor-save", v -> this.save()));
        bar.addView(this.pill("Save as", "editor-save-as", v -> this.saveAs()));
        bar.addView(this.pill("Undo", "editor-undo", v -> this.undo()));
        bar.addView(this.pill("Redo", "editor-redo", v -> this.redo()));
        bar.addView(this.pill("Find", "editor-find", v -> this.find()));
        if (back != null) bar.addView(this.pill("Back to Code", "editor-back", v -> this.back()));
        bar.addView(this.pill("Close", "editor-close", v -> this.close()));
        HorizontalScrollView bars = new HorizontalScrollView(app);
        bars.setHorizontalScrollBarEnabled(false);
        bars.addView(bar);
        root.addView(bars);
        this.state = app.text(" ", 11, false);
        this.state.setTextColor(UiKit.MUTED);
        this.state.setTag("editor-state");
        root.addView(this.state);
        this.text = new EditText(app);
        this.text.setTag("editor-text");
        this.text.setText(content == null ? "" : content);
        this.text.setTypeface(Typeface.MONOSPACE);
        this.text.setTextSize(2, 13f);
        this.text.setTextColor(UiKit.FG);
        this.text.setGravity(Gravity.TOP | Gravity.START);
        this.text.setBackgroundColor(UiKit.ELEV);
        this.text.setPadding(app.dp(10), app.dp(10), app.dp(10), app.dp(10));
        this.text.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams fill = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        fill.topMargin = app.dp(6);
        root.addView(this.text, fill);
        this.steps.add(this.text.getText().toString());
        this.text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (TextEditorScreen.this.applying) return;
                TextEditorScreen.this.mark(true);
                TextEditorScreen.this.later.removeCallbacks(TextEditorScreen.this.keep);
                TextEditorScreen.this.later.postDelayed(TextEditorScreen.this.keep, 500);
            }
        });
        this.dialog.setContentView(root);
        this.dialog.setOnKeyListener((d, code, e) -> {
            if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                this.close();
                return true;
            }
            return code == KeyEvent.KEYCODE_BACK;
        });
        this.mark(false);
    }

    /** The text on the editor screen; `back` (or null) takes it back to the Code area. */
    static TextEditorScreen open(MainActivity app, String title, String name, String content, Back back) {
        TextEditorScreen s = new TextEditorScreen(app, title, name, content, back);
        last = s;
        s.dialog.show();
        return s;
    }

    private TextView pill(String label, String tag, View.OnClickListener click) {
        TextView p = this.app.pill(label, false, click);
        p.setTag(tag);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, this.app.dp(6), 0);
        p.setLayoutParams(lp);
        return p;
    }

    void mark(boolean changed) {
        this.changed = changed;
        this.state.setText(changed ? "Not saved" : (this.file != null ? "Saved" : this.name));
    }

    /** An Undo step: the text now, when it differs from the last step (Redo's steps after it go). */
    void keepStep() {
        String now = this.text.getText().toString();
        if (this.at < this.steps.size() && now.equals(this.steps.get(this.at))) return;
        while (this.steps.size() > this.at + 1) this.steps.remove(this.steps.size() - 1);
        this.steps.add(now);
        this.at = this.steps.size() - 1;
        while (this.steps.size() > 200) {
            this.steps.remove(0);
            this.at--;
        }
    }

    void undo() {
        this.later.removeCallbacks(this.keep);
        this.keepStep();
        if (this.at <= 0) return;
        this.at--;
        this.apply(this.steps.get(this.at));
    }

    void redo() {
        if (this.at >= this.steps.size() - 1) return;
        this.at++;
        this.apply(this.steps.get(this.at));
    }

    private void apply(String s) {
        this.applying = true;
        this.text.setText(s);
        this.text.setSelection(Math.min(s.length(), this.text.getText().length()));
        this.applying = false;
        this.mark(true);
    }

    /** Save: to the document saved to before, else as Save as. */
    void save() {
        if (this.file != null && this.write(this.file)) return;
        this.saveAs();
    }

    void saveAs() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Not text/plain: the picker would add .txt to vst_render.py.
        intent.setType(this.name.toLowerCase().endsWith(".txt") ? "text/plain" : "application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, this.name);
        this.app.startActivityForResult(intent, SAVE_AS);
    }

    /** The place Save as's dialog returned: written there, and Save writes there from now on. */
    static void savedAs(MainActivity app, Uri uri) {
        TextEditorScreen s = last;
        if (s == null || uri == null) return;
        if (s.write(uri)) s.file = uri;
    }

    boolean write(Uri uri) {
        try (OutputStream out = this.app.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new java.io.IOException("no place to write");
            out.write(this.text.getText().toString().getBytes(StandardCharsets.UTF_8));
            this.file = uri;
            this.mark(false);
            this.app.setNow("Saved " + this.name);
            return true;
        } catch (Exception ex) {
            this.state.setText("Could not save: " + ex.getMessage());
            return false;
        }
    }

    /** Find: the next place the words are, from the cursor (and from the top again). */
    void find() {
        final EditText what = new EditText(this.app);
        what.setTag("editor-find-text");
        what.setSingleLine(true);
        new AlertDialog.Builder(this.app).setTitle("Find").setView(what)
            .setPositiveButton("Find", (d, w) -> this.findNext(what.getText().toString()))
            .setNegativeButton("Cancel", null).show();
    }

    boolean findNext(String what) {
        if (what == null || what.length() == 0) return false;
        String all = this.text.getText().toString().toLowerCase();
        String w = what.toLowerCase();
        int at = all.indexOf(w, Math.max(0, this.text.getSelectionEnd()));
        if (at < 0) at = all.indexOf(w);
        if (at < 0) {
            this.state.setText("Not found: " + what);
            return false;
        }
        this.text.requestFocus();
        this.text.setSelection(at, at + what.length());
        return true;
    }

    void back() {
        if (this.back == null) return;
        this.back.take(this.text.getText().toString());
        this.app.setNow("The edited text is in the Code area");
    }

    /** Close: unsaved changes are asked about first. */
    void close() {
        if (!this.changed) {
            this.dialog.dismiss();
            return;
        }
        new AlertDialog.Builder(this.app).setTitle("Close the editor").setMessage("Save the changes to " + this.name + "?")
            .setPositiveButton("Save", (d, w) -> {
                this.save();
                if (!this.changed) this.dialog.dismiss();
            })
            .setNegativeButton("Don't save", (d, w) -> this.dialog.dismiss())
            .setNeutralButton("Cancel", null).show();
    }
}
