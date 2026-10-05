package pulsekit;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static pulsekit.MainActivity.*;

/** The PyJav page: programs, prompts, run modes, input/output files and results. */
final class PyJav {
    final MainActivity app;

    PyJav(MainActivity app) {
        this.app = app;
    }

    /** Builds the PyJav page and its editor. pkWirePyJav adds the run controls later. */
    void buildPyPane(FrameLayout frameLayout) {
        // The page scrolls, so a long argument line or output never pushes Params and Run off screen.
        // The pane shows and hides its scroll view with it.
        final android.widget.ScrollView scroll = new android.widget.ScrollView((Context)app);
        scroll.setFillViewport(true);
        scroll.setTag("py-scroll");
        app.pyPane = new android.widget.LinearLayout((Context)app) {
            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                scroll.setVisibility(visibility);
            }
        };
        app.pyPane.setOrientation(1);
        app.pyPane.setVisibility(8);
        app.pyPane.addView((View)app.text("PyJav", 18, true));
        app.pyEditor = new EditText((Context)app);
        app.pyEditor.setText((CharSequence)"#!/usr/bin/env python3\n\"\"\"Pulsekit drum script.\"\"\"\nprint(\"edit me\")\n");
        app.pyEditor.setTypeface(Typeface.MONOSPACE);
        app.pyEditor.setTextColor(FG);
        app.pyEditor.setTextSize(2, 12.0f);
        app.pyEditor.setBackground((Drawable)app.round(ELEV, 10));
        app.pyEditor.setPadding(app.dp(10), app.dp(10), app.dp(10), app.dp(10));
        app.pyEditor.setGravity(0x800033);
        app.pyEditor.setMinLines(8);
        // A fixed height that scrolls inside, so a long program does not make the page long.
        app.pyEditor.setVerticalScrollBarEnabled(true);
        app.pyEditor.setOnTouchListener((v, ev) -> {
            if (v.canScrollVertically(1) || v.canScrollVertically(-1)) v.getParent().requestDisallowInterceptTouchEvent(true);
            if (ev.getActionMasked() == android.view.MotionEvent.ACTION_UP || ev.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        app.pyPane.addView((View)app.pyEditor, new android.widget.LinearLayout.LayoutParams(-1, app.dp(360)));
        scroll.addView((View)app.pyPane, new android.widget.FrameLayout.LayoutParams(-1, -2));
        frameLayout.addView((View)scroll);
    }

    boolean pkPyWired;

    boolean pkPyRecentMute;

    android.widget.TextView pkPyLog;
    /** What the last run wrote below Run; only that text can be saved as results. */
    String pkRunLog;

    android.widget.TextView pkPyHint;

    android.widget.EditText pkPyArgs;

    android.widget.TextView pkPyInput;

    String pkPyInputToken;

    String pkPyInputPath;

    String pkPyOutputPath;

    android.widget.Spinner pkPyRecent;

    java.util.List pkPyRecentItems;

    String pkPromptRun;

    String pkPromptReport;

    String pkPromptDescription;

    String pkRef1Path;

    String pkRef2Path;

    String pkDefinedResult;

    String pkOutputName;

    boolean pkOutputInvented;

    boolean pkOutputSaved;

    boolean pkOutputResume;

    android.widget.LinearLayout pkPromptModes;

    android.widget.TextView pkRunBash;

    android.widget.TextView pkRunCmd;

    android.widget.TextView pkRunAi;



    String displayName(Uri uri, String fallback) {
        String name = null;
        android.database.Cursor cursor = app.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int col = cursor.getColumnIndex("_display_name");
                    if (col >= 0) {
                        name = cursor.getString(col);
                    }
                }
            }
            finally {
                cursor.close();
            }
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        if (name == null) {
            name = fallback;
        }
        int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    void pkWirePyJav() {
        this.pkWirePyJavCore();
        if (this.pkPyRecent != null && app.pyPane != null && this.pkPyRecent.getParent() == app.pyPane) {
            app.pyPane.removeView(this.pkPyRecent);
            int at = app.pyPane.getChildCount() > 0 ? 1 : 0;
            app.pyPane.addView(this.pkPyRecent, at);
            this.pkPyRecent.setBackgroundColor(0xFF1B1D1F);
        }
        this.addProgramMenus();
        app.programMenus.paint();
    }

    public void openPrompts() { app.show("prompts"); }

    public void takePromptRef(android.net.Uri uri) { pulsekit.PromptSheet.take(app, uri); }

    void wirePrompts() {
        app.promptsPane = pulsekit.PromptSheet.create(app);
        app.promptsPane.setVisibility(8);
        android.view.ViewGroup host = null;
        if (app.importPane != null && app.importPane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) app.importPane.getParent();
        }
        if (host != null) {
            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(-1, -1);
            host.addView((android.view.View) app.promptsPane, (android.view.ViewGroup.LayoutParams) flp);
        }
        android.widget.TextView tab = app.text("Prompts", 13, true);
        tab.setGravity(17);
        tab.setTag("prompts");
        tab.setOnClickListener(pulsekit.FileSetClicks.prompts(app));
        tab.setPadding(0, app.dp(8), 0, app.dp(8));
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, app.dp(36), 1.0f);
        lp.setMargins(app.dp(2), 0, app.dp(2), 0);
        tab.setLayoutParams(lp);
        android.view.View py = null;
        java.util.Iterator it = app.tabs.iterator();
        while (it.hasNext()) {
            android.widget.TextView t = (android.widget.TextView) it.next();
            if ("py".equals(t.getTag())) { py = t; break; }
        }
        if (py != null && py.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup row = (android.view.ViewGroup) py.getParent();
            row.addView(tab, row.indexOfChild(py) + 1);
        }
        app.tabs.add(tab);
    }

    public void takePromptExport(android.net.Uri uri) { pulsekit.PromptSheet.writeExport(app, uri); }

    void pkPaintRun(android.widget.TextView button, String mode) {
        if (button == null) return;
        boolean on = mode.equals(this.pkPromptRun);
        button.setBackgroundColor(on ? HIT : ELEV);
        button.setTextColor(on ? BG : FG);
    }

    public void pkSetPromptRun(String mode) {
        String m = mode == null ? "" : pulsekit.PromptRun.normalizeType(mode);
        if (m.length() == 0 && mode != null && "ai".equals(mode.toLowerCase())) m = "ai";
        if (m.length() == 0) m = "bash";
        this.pkPromptRun = m;
        this.pkPaintRun(this.pkRunBash, "bash");
        this.pkPaintRun(this.pkRunCmd, "cmd");
        this.pkPaintRun(this.pkRunAi, "ai");
        if (app.pyName == null || !app.pyName.toLowerCase().endsWith(".prompt")) return;
        String report = pulsekit.PromptRun.report(app.pkPromptCategory, m, pulsekit.Subsystem.grok(app), pulsekit.Subsystem.sogni(app), pulsekit.Subsystem.claude(app));
        this.pkPromptReport = report;
        if (this.pkPyHint != null) this.pkPyHint.setText(report);
        if (this.pkPyLog != null) this.pkPyLog.setText(report);
        app.setNow(report.replace('\n', ' '));
    }

    void pkShowPromptModes() {
        if (this.pkPromptModes == null) return;
        boolean prompt = app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt");
        this.pkPromptModes.setVisibility(prompt ? 0 : 8);
    }

    public void pkLoadRefs(String full) {
        try {
            String text = full == null ? app.pkPromptSource : full;
            if (text == null) text = "";
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
            String title = sheet != null && sheet.name != null ? sheet.name : "";
            if (title.length() == 0 && app.pyName != null) {
                title = app.pyName;
                if (title.toLowerCase().endsWith(".prompt")) title = title.substring(0, title.length() - 7);
            }
            String category = sheet != null && sheet.category != null && sheet.category.length() > 0 ? sheet.category : app.pkPromptCategory;
            String n1 = sheet != null && sheet.ref1 != null ? sheet.ref1 : "";
            String n2 = sheet != null && sheet.ref2 != null ? sheet.ref2 : "";
            pulsekit.PromptFiles.Saved saved = pulsekit.PromptFiles.write(app, title, category, n1, n2);
            this.pkRef1Path = saved.ref1Path;
            this.pkRef2Path = saved.ref2Path;
            this.pkPromptDescription = saved.description == null ? "" : saved.description;
            this.pkDefinedResult = saved.resultName == null ? "" : saved.resultName;
            if (this.pkPromptDescription.length() == 0 && sheet != null && sheet.description != null) this.pkPromptDescription = sheet.description;
            if (app.pyEditor != null) {
                String now = app.pyEditor.getText().toString();
                String next = pulsekit.PromptRun.withoutDescription(this.pkPromptDescription, now);
                if (!now.equals(next)) app.pyEditor.setText(next);
            }
            if (saved.ref1Path != null && saved.ref1Path.length() > 0 && (this.pkPyInputPath == null || this.pkPyInputPath.length() == 0)) this.pkPyInputPath = saved.ref1Path;
            String note = saved.note == null ? "" : saved.note;
            String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
            int cut = report.indexOf("\nReference file 1:");
            if (cut >= 0) report = report.substring(0, cut);
            String shown = report.length() == 0 ? note : report + "\n" + note;
            this.pkPromptReport = shown;
            if (this.pkPyLog != null) this.pkPyLog.setText(shown);
            if (this.pkPyHint != null) this.pkPyHint.setText(shown);
        } catch (Exception ex) {
            if (this.pkPyLog != null) this.pkPyLog.setText("Could not read reference files.");
        }
    }

    void pkApplyHint(String hint) {
        if (hint == null) hint = "";
        if (this.pkPyInputPath == null && this.pkAudioInputPath != null && new java.io.File(this.pkAudioInputPath).isFile()) {
            this.pkPyInputPath = this.pkAudioInputPath;
        }
        if (this.pkPyHint != null) this.pkPyHint.setText(hint);
        // The hint shows above; below Run is only for what a run writes.
        if (this.pkPyLog != null) this.pkPyLog.setText("Output appears here.");
        this.pkPyInputToken = pulsekit.PyJavHints.firstInput(hint);
        java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
        if (!dir.isDirectory()) dir.mkdirs();
        String out = pulsekit.PyJavHints.outputFile(this.pkPyInputPath, hint, dir.getAbsolutePath());
        // A new program starts from its own usage, not the last program's args (DrumMidi's output.mid).
        String filled = pulsekit.PyJavHints.fillArgs("", hint, this.pkPyInputToken, this.pkPyInputPath, dir.getAbsolutePath());
        if (this.pkPyArgs != null) this.pkPyArgs.setText(filled);
        if (out.length() > 0) {
            this.pkPyOutputPath = out;
            String note = hint + "\nOutput file: " + out + "\nExtra args: " + filled;
            if (this.pkPyHint != null) this.pkPyHint.setText(note);
        }
        if (this.pkPyInput == null) return;
        if (this.pkPyInputToken == null) {
            this.pkPyInput.setVisibility(8);
        } else {
            this.pkPyInput.setVisibility(0);
            this.pkPyInput.setText("Browse " + this.pkPyInputToken);
        }
    }

    void pkRefreshRecent(int select) {
        if (this.pkPyRecent == null) return;
        this.pkPyRecentItems = pulsekit.PyJavRecent.load(app.getFilesDir());
        pulsekit.PyJavUi.fillRecent(app, this.pkPyRecent, this.pkPyRecentItems);
    }

    public void pkApplyRecent(int index) {
        if (this.pkPyRecentMute || this.pkPyRecentItems == null || index <= 0 || index > this.pkPyRecentItems.size()) return;
        pulsekit.PyJavRecent.Item item = (pulsekit.PyJavRecent.Item) this.pkPyRecentItems.get(index - 1);
        // A bundled program (Programs/Java or Python) runs as the app ships it now, not the copy
        // saved when it last ran, so fixes reach programs picked from Recent.
        String bundled = item.bytes == null || item.bytes.length == 0 ? app.programMenus.bundledSource(item.name) : null;
        String source = bundled != null ? bundled : item.source;
        app.pyName = item.name;
        this.pkPyInputPath = null;
        boolean binary = item.bytes != null && item.bytes.length > 0;
        if (binary) app.pkPyBytes = item.bytes;
        else app.pkPyBytes = null;
        if (this.pkPyArgs != null) this.pkPyArgs.setText(item.extra == null ? "" : item.extra);
        if (app.pyEditor != null) {
            if (binary) app.pyEditor.setText("// " + item.name + "\n// Binary. Run uses this file.\n");
            else {
                String text = source == null ? "" : source;
                if (item.name != null && item.name.toLowerCase().endsWith(".prompt")) {
                    pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
                    app.pkPromptSource = text;
                    app.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
                    this.pkSetPromptRun(pulsekit.PromptRun.runMode(text, sheet));
                    this.pkLoadRefs(app.pkPromptSource);
                    if (sheet != null && sheet.body != null) text = sheet.body;
                }
                app.pyEditor.setText(text);
            }
        }
        this.pkShowPromptModes();
        app.setNow("PyJav · " + item.label());
        String hint = pulsekit.PyJavHints.status(item.name, source, item.bytes);
        this.pkApplyHint(hint);
        // The hint fills args from the program's usage; the recent item's own args win.
        if (this.pkPyArgs != null) this.pkPyArgs.setText(item.extra == null ? "" : item.extra);
    }

    void pkWirePyJavCore() {
        if (app.tabs != null) {
            for (int i = 0; i < app.tabs.size(); i++) {
                android.widget.TextView tv = (android.widget.TextView) app.tabs.get(i);
                if ("py".equals(tv.getTag())) tv.setText("PyJav");
            }
        }
        if (app.pyPane == null) return;
        if (app.pyPane.getChildCount() > 0 && app.pyPane.getChildAt(0) instanceof android.widget.TextView) {
            ((android.widget.TextView) app.pyPane.getChildAt(0)).setText("PyJav");
        }
        if (this.pkPyWired) return;
        this.pkPyWired = true;
        int slot = 1;
        if (slot > app.pyPane.getChildCount()) slot = app.pyPane.getChildCount();
        this.pkPyRecent = new android.widget.Spinner(app, 1);
        this.pkPyRecent.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        this.pkPyRecent.setMinimumHeight(app.dp(40));
        app.pyPane.addView(this.pkPyRecent, slot);
        slot = slot + 1;
        this.pkPyRecent.setOnItemSelectedListener(pulsekit.PyJavUi.recent(app));
        this.pkRefreshRecent(0);
        this.pkPyHint = app.text("Possible extra args appear after you browse a file.", 12, false);
        this.pkPyHint.setTextColor(FG);
        app.pyPane.addView(this.pkPyHint, slot);
        slot = slot + 1;
        this.pkPyArgs = new android.widget.EditText(app);
        this.pkPyArgs.setHint("Extra args");
        this.pkPyArgs.setSingleLine(false);
        this.pkPyArgs.setMinLines(2);
        this.pkPyArgs.setTextColor(FG);
        this.pkPyArgs.setHintTextColor(MUTED);
        this.pkPyArgs.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        app.pyPane.addView(this.pkPyArgs, slot);
        slot = slot + 1;
        this.pkPyInput = app.action("Browse input", ELEV, FG, pulsekit.PyJavUi.browseInput(app));
        this.pkPyInput.setVisibility(8);
        app.pyPane.addView(this.pkPyInput, slot);
        slot = slot + 1;
        this.pkPromptModes = new android.widget.LinearLayout(app);
        this.pkPromptModes.setOrientation(0);
        this.pkRunBash = app.action("bash", ELEV, FG, pulsekit.PyJavUi.mode(app, "bash"));
        this.pkRunCmd = app.action("cmd", ELEV, FG, pulsekit.PyJavUi.mode(app, "cmd"));
        this.pkRunAi = app.action("AI", ELEV, FG, pulsekit.PyJavUi.mode(app, "ai"));
        this.pkPromptModes.addView(this.pkRunBash, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        this.pkPromptModes.addView(this.pkRunCmd, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        this.pkPromptModes.addView(this.pkRunAi, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
        app.pyPane.addView(this.pkPromptModes, slot);
        this.pkPromptModes.setVisibility(8);
        this.pkSetPromptRun(this.pkPromptRun == null ? "bash" : this.pkPromptRun);
        slot = slot + 1;
        app.pyPane.addView(app.action("Browse file", ELEV, FG, pulsekit.PyJavUi.browse(app)), slot);
        slot = slot + 1;
        app.pyPane.addView(app.action("Params", FG, BG, pulsekit.PyJavUi.params(app)), slot);
        slot = slot + 1;
        app.pyPane.addView(app.action("Run", FG, BG, pulsekit.PyJavUi.click(app)), slot);
        slot = slot + 1;
        this.pkPyLog = app.text("Output appears here.", 12, false);
        this.pkPyLog.setTextColor(FG);
        this.pkPyLog.setMinLines(4);
        SaveText.attach(app, this.pkPyLog, () -> this.pkLogIsRun() ? pulsekit.PyJavHints.resultsFileName(app.pyName) : null);
        app.pyPane.addView(this.pkPyLog, slot);
    }

    public void pkPrepareOutput() {
        pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(app.pkPromptSource);
        String defined = this.pkDefinedResult == null ? "" : this.pkDefinedResult;
        if (defined.length() == 0 && sheet != null && sheet.result != null) defined = sheet.result;
        String title = sheet != null && sheet.name != null && sheet.name.length() > 0 ? sheet.name : app.pyName;
        String category = sheet != null && sheet.category != null && sheet.category.length() > 0 ? sheet.category : app.pkPromptCategory;
        String model = sheet != null && sheet.model != null ? sheet.model : "";
        String body = app.pyEditor != null ? app.pyEditor.getText().toString() : "";
        String fileName = pulsekit.PromptRun.chooseOutputName(title, category, this.pkPromptRun, model, body, defined);
        this.pkOutputInvented = defined == null || defined.trim().length() == 0;
        this.pkOutputSaved = !this.pkOutputInvented;
        this.pkOutputName = fileName;
        java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
        if (!dir.isDirectory()) dir.mkdirs();
        this.pkPyOutputPath = new java.io.File(dir, fileName).getAbsolutePath();
        String note = "Output file: " + fileName;
        String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
        int cut = report.indexOf("\nOutput file:");
        if (cut >= 0) report = report.substring(0, cut);
        this.pkPromptReport = report.length() == 0 ? note : report + "\n" + note;
    }

    public void pkSaveInventedOutput(String text) {
        if (!this.pkOutputInvented || this.pkOutputSaved || this.pkPyOutputPath == null || this.pkOutputName == null) return;
        try {
            java.io.File file = new java.io.File(this.pkPyOutputPath);
            byte[] data = null;
            if (file.isFile() && file.length() > 0) {
                java.io.FileInputStream in = new java.io.FileInputStream(file);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                data = bos.toByteArray();
            } else if (pulsekit.PromptRun.logOutput(this.pkOutputName)) {
                String body = text == null ? "" : text;
                data = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                java.io.FileOutputStream out = new java.io.FileOutputStream(file);
                out.write(data);
                out.close();
            }
            if (data == null) return;
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(app.pkPromptSource);
            String title = sheet != null && sheet.name != null ? sheet.name : "";
            String category = sheet != null && sheet.category != null ? sheet.category : app.pkPromptCategory;
            String n1 = sheet != null && sheet.ref1 != null ? sheet.ref1 : "";
            String n2 = sheet != null && sheet.ref2 != null ? sheet.ref2 : "";
            pulsekit.PromptFiles.storeResult(app, title, category, n1, n2, this.pkOutputName, data);
            this.pkOutputSaved = true;
            this.pkDefinedResult = this.pkOutputName;
        } catch (Exception ignored) {}
    }

    public void pkShowAiResult(String line) {
        if (line == null) line = "";
        String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
        String shown = report.length() == 0 ? line : report + "\n" + line;
        if (this.pkPyLog != null) this.pkPyLog.setText(shown);
        this.pkRunLog = shown;
        if (this.pkPyHint != null) this.pkPyHint.setText(shown);
        app.setNow(line);
        this.pkSaveInventedOutput(shown);
    }

    public void pkRunPyJav() {
        if (app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt")) {
            if (!this.pkOutputResume) {
                this.pkLoadRefs(app.pkPromptSource);
                this.pkPrepareOutput();
                if (this.pkOutputInvented && pulsekit.PromptRun.logOutput(this.pkOutputName)) {
                    pulsekit.PyJavUi.askOutput(app);
                    return;
                }
            }
            this.pkOutputResume = false;
        }
        if (app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt") && "ai".equals(this.pkPromptRun)) {
            String body = app.pyEditor != null ? app.pyEditor.getText().toString() : "";
            body = pulsekit.PromptRun.withoutDescription(this.pkPromptDescription, body);
            pulsekit.PyJavRecent.remember(app.getFilesDir(), app.pyName, "", app.pkPromptSource == null ? body : app.pkPromptSource, null);
            this.pkRefreshRecent(0);
            pulsekit.PyJavUi.chooseAi(app, body, this.pkRef1Path, this.pkRef2Path);
            return;
        }
        String extra = this.pkPyArgs != null ? this.pkPyArgs.getText().toString() : "";
        String hint = this.pkPyHint != null ? this.pkPyHint.getText().toString() : "";
        if (this.pkPyLog != null) this.pkPyLog.setText(this.pkPromptReport != null && this.pkPromptReport.length() > 0 ? this.pkPromptReport + "\nRunning…" : "Running…");
        if (this.pkPyHint != null) this.pkPyHint.setText(this.pkPromptReport != null && this.pkPromptReport.length() > 0 ? this.pkPromptReport : "Running…");
        app.setNow("Running…");
        String name = app.pyName == null ? "script.py" : app.pyName;
        String listed = app.programMenus.sourceToRun();
        String src = listed != null ? listed : (app.pyEditor != null ? app.pyEditor.getText().toString() : "");
        extra = pulsekit.PyJavParams.merge(pulsekit.PyJavHints.programText(name, src, app.pkPyBytes), extra, pulsekit.PyJavParams.load(app, name));
        src = pulsekit.PromptRun.withoutDescription(this.pkPromptDescription, src);
        java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
        if (!dir.isDirectory()) dir.mkdirs();
        String filled = pulsekit.PyJavHints.fillArgs(extra, hint, this.pkPyInputToken, this.pkPyInputPath, dir.getAbsolutePath());
        String out = pulsekit.PyJavHints.outputFile(this.pkPyInputPath, hint + "\n" + extra, dir.getAbsolutePath());
        if (out.length() > 0 && !this.pkOutputInvented) this.pkPyOutputPath = out;
        if (this.pkPyArgs != null && filled.length() > 0) this.pkPyArgs.setText(filled);
        pulsekit.PyJavRecent.remember(app.getFilesDir(), name, filled, src, app.pkPyBytes);
        this.pkRefreshRecent(0);
        java.util.List argv;
        if ((this.pkPyInputPath != null && this.pkPyInputPath.length() > 0) || out.length() > 0) {
            argv = pulsekit.PyJavHints.programArgs(filled, this.pkPyInputToken, this.pkPyInputPath, hint, dir.getAbsolutePath());
            if (this.pkPyHint != null) this.pkPyHint.setText(pulsekit.PyJavHints.outputNotice(filled, hint, this.pkPyInputPath, dir.getAbsolutePath()));
        } else {
            int swing = app.swingBar != null ? app.swingBar.getVal() : 0;
            argv = pulsekit.JavaRun.argvFor(src, app.bpm(), app.style, app.bars, swing, extra);
        }
        if (name.toLowerCase().endsWith(".prompt")) {
            argv.add("--pk-run");
            argv.add(this.pkPromptRun == null ? "bash" : this.pkPromptRun);
        }
        if (this.pkRef1Path != null && this.pkRef1Path.length() > 0 && !argv.contains(this.pkRef1Path)) argv.add(this.pkRef1Path);
        if (this.pkRef2Path != null && this.pkRef2Path.length() > 0 && !argv.contains(this.pkRef2Path)) argv.add(this.pkRef2Path);
        if (this.pkOutputInvented && this.pkPyOutputPath != null && this.pkPyOutputPath.length() > 0 && !argv.contains(this.pkPyOutputPath)) argv.add(this.pkPyOutputPath);
        pulsekit.JavaRun.start(name, src, app.pkPyBytes, argv, pulsekit.PyJavUi.listener(app));
    }

    public void pkAcceptOutput(String answer) {
        String given = answer == null ? "" : answer.trim();
        if (given.length() > 0) {
            pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(app.pkPromptSource);
            String title = sheet != null && sheet.name != null && sheet.name.length() > 0 ? sheet.name : app.pyName;
            String category = sheet != null && sheet.category != null && sheet.category.length() > 0 ? sheet.category : app.pkPromptCategory;
            String model = sheet != null && sheet.model != null ? sheet.model : "";
            String body = app.pyEditor != null ? app.pyEditor.getText().toString() : "";
            String fileName = pulsekit.PromptRun.chooseOutputName(title, category, this.pkPromptRun, model, body, given);
            this.pkOutputName = fileName;
            this.pkOutputInvented = true;
            this.pkOutputSaved = false;
            java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            this.pkPyOutputPath = new java.io.File(dir, fileName).getAbsolutePath();
            String note = "Output file: " + fileName;
            String report = this.pkPromptReport == null ? "" : this.pkPromptReport;
            int cut = report.indexOf("\nOutput file:");
            if (cut >= 0) report = report.substring(0, cut);
            this.pkPromptReport = report.length() == 0 ? note : report + "\n" + note;
        }
        this.pkOutputResume = true;
        this.pkRunPyJav();
    }

    public void pkCancelOutput() {
        this.pkOutputInvented = false;
        this.pkOutputResume = false;
        this.pkShowAiResult("Cancelled.");
    }

    public void pkSetPyArgs(java.lang.String args) {
        java.lang.String extra = this.pkPyArgs != null ? this.pkPyArgs.getText().toString() : "";
        java.lang.String merged = pulsekit.PyJavParams.merge(extra, args);
        if (this.pkPyArgs != null) this.pkPyArgs.setText(merged);
        app.setNow("Params saved");
    }

    public void pkOpenParams() {
        java.lang.String name = app.pyName == null ? "DrumMidi" : app.pyName;
        java.lang.String extra = this.pkPyArgs != null ? this.pkPyArgs.getText().toString() : "";
        pulsekit.PyJavParams.open(app, name, extra, this.pkProgramText());
    }

    /** The loaded program's text, for its parameters: the listed program, the editor, or a .class/.jar's strings. */
    String pkProgramText() {
        String listed = app.programMenus.sourceToRun();
        String src = listed != null ? listed : (app.pyEditor != null ? app.pyEditor.getText().toString() : "");
        return pulsekit.PyJavHints.programText(app.pyName, src, app.pkPyBytes);
    }

    /** Params' argument line; a new input file also becomes PyJav's input, so the output is named after it. */
    public void pkSetPyLine(java.lang.String line, java.lang.String input) {
        if (input != null && input.length() > 0) this.pkUseInputPath(input);
        if (this.pkPyArgs != null) this.pkPyArgs.setText(line);
        app.setNow("Params saved");
    }

    /** A file picked on the Params screen, copied into PyJav's input folder. */
    public void pkTakeParamFile(android.net.Uri uri) {
        try {
            pulsekit.PyJavParams.filePicked(this.pkCopyInputFile(uri));
        } catch (Exception ex) {
            app.setNow("Could not open that file");
        }
    }

    public void pkSyncTransport() {
        try {
            if (app.playBtn == null || !(app.playBtn.getParent() instanceof android.view.View)) return;
            android.view.View row = (android.view.View) app.playBtn.getParent();
            row.setVisibility("py".equals(app.view) ? 8 : 0);
        } catch (Throwable ignored) {}
    }

    public String pkImportProgramMidi(byte[] data, String name) {
        try {
            if (data == null || name == null) return "Import failed: no MIDI file";
            pulsekit.Engine.stageSourceMidi(data, name);
            String stem = pulsekit.Engine.stemNameFromMidi(name);
            pulsekit.Engine.MidiBars bars = pulsekit.Engine.parseMidiBars(data);
            boolean ok = bars != null && app.importLibrary.learnFromSongImport(name, bars, true);
            pulsekit.Engine.clearStagedMidi();
            String source = stem;
            if (ok) {
                pulsekit.Engine.rememberFileSetMidi(source, data, name);
                for (int i = 0; i < app.learned.size(); i++) {
                    pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) app.learned.get(i);
                    if (item == null) continue;
                    java.lang.String s = pulsekit.Engine.sourceOf(item);
                    if (s == null || s.length() == 0) continue;
                    if (s.equals(source) || s.startsWith(source + " ")) pulsekit.Engine.rememberFileSetMidi(s, data, name);
                }
                pulsekit.Engine.rememberFileSetOrigin(source, "program");
                pulsekit.Engine.storeFileSetAudioDir(new java.io.File(app.getFilesDir(), "fset-audio"));
                app.fileSets.persistFsetInfo();
                app.importLibrary.rebuildImported();
                app.importLibrary.rebuildImportedFills();
                app.setNow("Succeeded: " + source);
                android.widget.Toast.makeText(app, "Succeeded: " + source, 1).show();
                return "Succeeded: " + source;
            }
            int[][] cells = pulsekit.Engine.parseMidi(data);
            if (cells == null || pulsekit.Engine.hitCount(cells) < 1) return "Failed: no drum notes in " + name;
            int bpm = pulsekit.Engine.parseMidiBpm(data, app.bpm());
            app.importLibrary.learnFromImport(name, cells, bpm);
            source = pulsekit.Engine.importSource(name);
            if (source == null || source.length() == 0) source = stem;
            boolean found = false;
            for (int i = 0; i < app.learned.size(); i++) {
                pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) app.learned.get(i);
                if (item != null && source.equals(item.source)) found = true;
            }
            if (!found) return "Failed: " + name + " did not become a file set";
            pulsekit.Engine.rememberFileSetMidi(source, data, name);
            pulsekit.Engine.rememberFileSetOrigin(source, "program");
            app.fileSets.persistFsetInfo();
            app.importLibrary.rebuildImported();
            app.setNow("Succeeded: " + source);
            android.widget.Toast.makeText(app, "Succeeded: " + source, 1).show();
            return "Succeeded: " + source;
        } catch (Throwable ex) {
            String m = ex.getMessage();
            return "Failed: " + (m == null ? ex.toString() : m);
        }
    }

    public String pkVerdict(String log, int code, int midis, String status) {
        String found = null;
        if (log != null) {
            int i = 0;
            while (i < log.length()) {
                int nl = log.indexOf(10, i);
                String line = (nl < 0 ? log.substring(i) : log.substring(i, nl)).trim();
                if (line.startsWith("Succeeded:") || line.startsWith("Failed:")) found = line;
                if (nl < 0) break;
                i = nl + 1;
            }
        }
        if (found != null) return found;
        if (status != null && (status.startsWith("Succeeded:") || status.startsWith("Failed:"))) return status;
        String why = status == null ? "" : status;
        if (why.startsWith("Import failed: ")) why = why.substring(15);
        if (why.startsWith("Import succeeded: ")) return "Succeeded: " + why.substring(18);
        if (log != null) {
            int cut = log.indexOf("ERROR");
            if (cut < 0) cut = log.indexOf("Exception");
            if (cut < 0) cut = log.indexOf("Timed out");
            if (cut >= 0) {
                int end = log.indexOf(10, cut);
                why = (end < 0 ? log.substring(cut) : log.substring(cut, end)).trim();
            }
        }
        if (midis > 0 && code == 0) return "Succeeded: " + (why.length() == 0 ? "MIDI" : why);
        if (why.length() == 0) why = "no MIDI file was written";
        return "Failed: " + why;
    }

    public void pkShowPyResult(pulsekit.JavaRun.Result result) {
        String status = "Import failed: no result";
        String log = "";
        try {
        log = result == null || result.log == null || result.log.length() == 0 ? "(no output)" : result.log;
        if (this.pkPromptReport != null && this.pkPromptReport.length() > 0 && app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt") && log.indexOf("Category:") != 0) log = this.pkPromptReport + "\n" + log;
        int midis = 0;
        if (result != null && result.files != null) {
            for (int i = 0; i < result.files.size(); i++) {
                pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) result.files.get(i);
                log = log + "\nfile " + f.name + " (" + f.bytes.length + " bytes)";
                String low = f.name.toLowerCase();
                if ((low.endsWith(".mid") || low.endsWith(".midi")) && f.bytes.length >= 4 && f.bytes[0] == 'M' && f.bytes[1] == 'T' && f.bytes[2] == 'h' && f.bytes[3] == 'd') {
                    status = this.pkImportProgramMidi(f.bytes, f.name);
                    midis = midis + 1;
                }
            }
        }
        boolean prompt = app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt");
        if (midis == 0 && !prompt && this.pkPyOutputPath != null && this.pkPyOutputPath.length() > 0 && (result == null || result.log == null || !result.log.startsWith("Executed"))) {
            try {
                java.io.File out = new java.io.File(this.pkPyOutputPath);
                if (out.isFile()) {
                    java.io.FileInputStream in = new java.io.FileInputStream(out);
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    in.close();
                    log = log + "\nfile " + out.getName() + " (" + bos.size() + " bytes)";
                    status = this.pkImportProgramMidi(bos.toByteArray(), out.getName());
                    midis = midis + 1;
                }
            } catch (Throwable ex) {
                String m = ex.getMessage();
                status = "Import failed: " + (m == null ? ex.toString() : m);
            }
        }
        if (midis == 0 && (prompt || (result != null && result.log != null && (result.log.startsWith("Executed") || result.log.startsWith("AI prompt") || result.log.startsWith("$ ") || result.log.startsWith("The prompt is empty") || result.log.startsWith("bash is not") || result.log.startsWith("cmd is not"))))) {
            String raw = result == null || result.log == null ? "" : result.log;
            int nl = raw.indexOf(10);
            status = nl < 0 ? raw : raw.substring(0, nl);
            if (status.length() == 0) status = result != null && result.code != 0 ? "Prompt failed" : "Prompt finished";
        } else if (midis == 0) {
            String why = "Import failed: no MIDI file was written";
            int cut = log.indexOf("No output file:");
            if (cut < 0) cut = log.indexOf("could not read");
            if (cut < 0) cut = log.indexOf("OutOfMemoryError");
            if (cut < 0) cut = log.indexOf("Error processing audio");
            if (cut >= 0) {
                int end = log.indexOf('\n', cut);
                why = "Import failed: " + (end < 0 ? log.substring(cut) : log.substring(cut, end)).trim();
            }
            status = why;
        }
        status = this.pkVerdict(log, result == null ? 1 : result.code, midis, status);
        log = status + "\n" + log;
        if (this.pkPyHint != null) this.pkPyHint.setText(status);
        app.setNow(status);
        android.widget.Toast.makeText(app, status, 1).show();
        this.pkSaveInventedOutput(log);
        if (this.pkPyLog != null) this.pkPyLog.setText(log);
        this.pkRunLog = log;
        pulsekit.SogniHistory.record(log, System.currentTimeMillis());
        // A run that made an audio file (SogniMusic's track): play it, or make drum MIDI from it.
        if (status.startsWith("Succeeded")) AudioOffer.offer(app, pulsekit.PyJavHints.madeAudio(log), result);
        } catch (Throwable ex) {
            String m = ex.getMessage();
            status = "Failed: " + (m == null ? ex.toString() : m);
            if (this.pkPyHint != null) this.pkPyHint.setText(status);
            app.setNow(status);
            android.widget.Toast.makeText(app, status, 1).show();
            if (this.pkPyLog != null) this.pkPyLog.setText(status);
            this.pkRunLog = status;
        }
    }

    /** True while the text below Run is a run's output, not a hint or a note. */
    boolean pkLogIsRun() {
        return this.pkPyLog != null && this.pkRunLog != null && this.pkRunLog.equals(this.pkPyLog.getText().toString());
    }

    boolean pkTakeProgram(byte[] data, String name) {
        if (data != null && name != null && isNodeScript(name)) {
            int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
            String base = slash >= 0 ? name.substring(slash + 1) : name;
            app.pyName = base;
            app.pkPyBytes = null;
            this.pkPyInputPath = null;
            if (app.pyEditor != null) {
                app.pyEditor.setText(new String(data, StandardCharsets.UTF_8));
            }
            app.show("py");
            return true;
        }
        if (data == null || name == null) return false;
        String low = name.toLowerCase();
        boolean jar = low.endsWith(".jar");
        boolean cls = low.endsWith(".class");
        boolean javaSrc = low.endsWith(".java");
        if (!jar && !cls && !javaSrc) return false;
        if (jar) {
            try {
                java.util.Map files = pulsekit.Engine.unzip(data);
                if (files != null && files.containsKey("pattern.json")) return false;
            } catch (Exception ignored) {}
        }
        int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
        String base = slash >= 0 ? name.substring(slash + 1) : name;
        app.pyName = base;
        this.pkPyInputPath = null;
        if (jar || cls) app.pkPyBytes = data;
        else app.pkPyBytes = null;
        if (app.pyEditor != null) {
            if (jar || cls) app.pyEditor.setText("// " + base + "\n// Binary. Run uses this file.\n");
            else app.pyEditor.setText(new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
        app.show("py");
        app.setNow("PyJav · " + base);
        String src = (jar || cls) ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
        String hint = pulsekit.PyJavHints.status(base, src, data);
        this.pkApplyHint(hint);
        byte[] kept = null;
        if (jar || cls) kept = data;
        pulsekit.PyJavRecent.remember(app.getFilesDir(), base, "", src, kept);
        this.pkRefreshRecent(0);
        this.pkShowPromptModes();
        return true;
    }

    public void pkOpenPromptText(String name, String full) {
        if (name == null || name.length() == 0) name = "prompt.prompt";
        if (!name.toLowerCase().endsWith(".prompt")) name = name + ".prompt";
        app.pyName = name;
        app.pkPyBytes = null;
        this.pkPyInputPath = null;
        String text = full == null ? "" : full;
        pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
        app.pkPromptSource = text;
        app.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
        String body = sheet != null && sheet.body != null ? sheet.body : text;
        if (app.pyEditor != null) app.pyEditor.setText(body);
        this.pkSetPromptRun(pulsekit.PromptRun.runMode(text, sheet));
        this.pkShowPromptModes();
        app.show("py");
        String hint = this.pkPromptReport == null ? "" : this.pkPromptReport;
        app.setNow(hint.replace('\n', ' '));
        this.pkApplyHint(hint);
        this.pkLoadRefs(text);
        pulsekit.PyJavRecent.remember(app.getFilesDir(), name, "", text, null);
        this.pkRefreshRecent(0);
        android.widget.Toast.makeText(app, "Opened " + name, 0).show();
    }

    public void pkBrowsePyJav() {
        android.content.Intent intent = new android.content.Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        intent.putExtra("android.intent.extra.MIME_TYPES", new String[]{"*/*", "text/plain", "text/*", "application/octet-stream"});
        app.startActivityForResult(intent, 25);
    }

    public void pkTakePickedProgram(android.net.Uri uri) {
        try {
            byte[] data = app.projectIo.readUri(uri);
            String name = this.displayName(uri, "program.js");
            if (data != null && isNodeScript(name)) {
                app.pyName = name;
                app.pkPyBytes = null;
                this.pkPyInputPath = null;
                if (app.pyEditor != null) {
                    app.pyEditor.setText(new String(data, StandardCharsets.UTF_8));
                }
                app.show("py");
                return;
            }
        }
        catch (Exception exception) {
            // Fall through: the general loader reports the error.
        }
        try {
            byte[] data = app.projectIo.readUri(uri);
            String name = null;
            android.database.Cursor cursor = app.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex("_display_name");
                        if (col >= 0) name = cursor.getString(col);
                    }
                } finally { cursor.close(); }
            }
            if (name == null) name = uri.getLastPathSegment();
            if (name == null) name = "program.py";
            int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
            if (slash >= 0) name = name.substring(slash + 1);
            String low = name.toLowerCase();
            boolean py = low.endsWith(".py");
            boolean jar = low.endsWith(".jar");
            boolean cls = low.endsWith(".class");
            boolean javaSrc = low.endsWith(".java");
            boolean prompt = low.endsWith(".prompt") || low.endsWith(".prompt.txt") || low.contains(".prompt");
            if (!prompt && data != null && data.length >= 9) {
                String head = new String(data, 0, Math.min(data.length, 12), java.nio.charset.StandardCharsets.UTF_8);
                if (head.startsWith("PKPROMPT1")) {
                    prompt = true;
                    if (!low.endsWith(".prompt")) name = (low.endsWith(".txt") ? name.substring(0, name.length() - 4) : name) + ".prompt";
                }
            }
            if (!py && !prompt && !jar && !cls && !javaSrc) {
                String why = "Pick a .py, .java, .class, .jar, or .prompt file.";
                if (this.pkPyLog != null) this.pkPyLog.setText(why);
                android.widget.Toast.makeText(app, why, 1).show();
                return;
            }
            if (jar) {
                try {
                    java.util.Map files = pulsekit.Engine.unzip(data);
                    if (files != null && files.containsKey("pattern.json")) {
                        if (this.pkPyLog != null) this.pkPyLog.setText("That JAR is a kit snapshot, not a program.");
                        return;
                    }
                } catch (Exception ignored) {}
            }
            app.pyName = name;
            this.pkPyInputPath = null;
            if (jar || cls) app.pkPyBytes = data;
            else app.pkPyBytes = null;
            if (app.pyEditor != null) {
                if (jar || cls) app.pyEditor.setText("// " + name + "\n// Binary. Run uses this file.\n");
                else {
                    String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    if (prompt) {
                        pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(text);
                        app.pkPromptSource = text;
                        app.pkPromptCategory = sheet == null || sheet.category == null ? "" : sheet.category;
                        if (sheet != null && sheet.body != null) text = sheet.body;
                    }
                    app.pyEditor.setText(text);
                    if (prompt) this.pkSetPromptRun(pulsekit.PromptRun.runMode(new String(data, java.nio.charset.StandardCharsets.UTF_8), null));
                }
            }
            this.pkShowPromptModes();
            app.show("py");
            app.setNow("PyJav · " + name);
            String src = (jar || cls) ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
            if (prompt) {
                pulsekit.PromptRun.Sheet sheet = pulsekit.PromptRun.parse(src);
                if (sheet != null && sheet.body != null) src = sheet.body;
            }
            String hint = prompt
                ? (this.pkPromptReport == null ? "" : this.pkPromptReport)
                : pulsekit.PyJavHints.status(name, src, data);
            this.pkApplyHint(hint);
            if (prompt) this.pkLoadRefs(app.pkPromptSource);
            byte[] kept = null;
            if (jar || cls) kept = data;
            pulsekit.PyJavRecent.remember(app.getFilesDir(), name, "", src, kept);
            this.pkRefreshRecent(0);
            android.widget.Toast.makeText(app, "Opened " + name, 0).show();
        } catch (Exception ex) {
            String m = ex.getMessage();
            if (m == null || m.length() == 0) m = "Could not open that file";
            if (this.pkPyLog != null) this.pkPyLog.setText(m);
            android.widget.Toast.makeText(app, m, 1).show();
        }
    }

    public void pkBrowseInput() {
        android.content.Intent intent = new android.content.Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        app.startActivityForResult(intent, 26);
    }

    public void pkTakeInputFile(android.net.Uri uri) {
        try {
            this.pkUseInputPath(this.pkCopyInputFile(uri));
        } catch (Exception ex) {
            String m = ex.getMessage();
            if (this.pkPyLog != null) this.pkPyLog.setText(m != null ? m : "Could not open that input file");
        }
    }

    /** Copies a picked file into PyJav's input folder and returns its path. */
    String pkCopyInputFile(android.net.Uri uri) throws Exception {
        {
            String name = null;
            android.database.Cursor cursor = app.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int col = cursor.getColumnIndex("_display_name");
                        if (col >= 0) name = cursor.getString(col);
                    }
                } finally { cursor.close(); }
            }
            if (name == null || name.length() == 0) name = "input.wav";
            int slash = Math.max(name.lastIndexOf(47), name.lastIndexOf(58));
            if (slash >= 0) name = name.substring(slash + 1);
            name = name.replace(' ', '_');
            java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, name);
            java.io.InputStream in = app.getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("Could not open " + name);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            } finally { fos.close(); in.close(); }
            return out.getAbsolutePath();
        }
    }

    /** Makes path the PyJav input file and fills the program's args from its hint. */
    void pkUseInputPath(String path) {
        this.pkPyInputPath = path;
        String hint = this.pkPyHint != null ? this.pkPyHint.getText().toString() : "";
        java.io.File folder = new java.io.File(app.getCacheDir(), "pyjav-in");
        String note = pulsekit.PyJavHints.outputNotice("", hint, path, folder.getAbsolutePath());
        String next = pulsekit.PyJavHints.fillArgs("", hint, this.pkPyInputToken, path, folder.getAbsolutePath());
        this.pkPyOutputPath = pulsekit.PyJavHints.outputFile(path, hint, folder.getAbsolutePath());
        if (this.pkPyArgs != null) this.pkPyArgs.setText(next);
        if (this.pkPyLog != null) this.pkPyLog.setText(note);
    }

    /**
     * Imported WAV or MP3: copy it into PyJav's input folder and open PyJav, so a
     * program such as MidiDrumGen.java can turn it into MIDI.
     */
    /** Last WAV/MP3 imported as PyJav input; programs opened later still get it. */
    String pkAudioInputPath;

    boolean pkTakeAudioInput(byte[] data, String name) {
        if (data == null || data.length < 4) {
            return false;
        }
        String kind = AudioIo.sniff(data);
        String low = name == null ? "" : name.toLowerCase();
        boolean audio = "wav".equals(kind) || "mp3".equals(kind) || low.endsWith(".wav") || low.endsWith(".wave") || low.endsWith(".mp3");
        if (!audio) {
            return false;
        }
        String base = name == null || name.isEmpty() ? "input." + ("mp3".equals(kind) ? "mp3" : "wav") : name;
        int slash = Math.max(base.lastIndexOf(47), base.lastIndexOf(58));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replace(' ', '_');
        try {
            java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
            java.io.File out = new java.io.File(dir, base);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                fos.write(data);
            }
            finally {
                fos.close();
            }
            this.pkAudioInputPath = out.getAbsolutePath();
            app.show("py");
            this.pkUseInputPath(this.pkAudioInputPath);
            app.setNow("PyJav input \u00b7 " + base);
            if (base.toLowerCase().endsWith(".mp3")) {
                // Most programs (DrumMidi_CRT) read WAV only: a WAV beside the MP3 becomes the input once it is made.
                final String mp3 = out.getAbsolutePath();
                AudioOffer.toWav(app, out, (wav, error) -> {
                    if (wav == null || !mp3.equals(this.pkAudioInputPath)) return;
                    this.pkAudioInputPath = wav.getAbsolutePath();
                    if (mp3.equals(this.pkPyInputPath)) this.pkUseInputPath(this.pkAudioInputPath);
                    app.setNow("PyJav input \u00b7 " + wav.getName());
                });
            }
            Toast.makeText((Context)app, (CharSequence)(base + " is the PyJav input. Run MidiDrumGen.java to make MIDI."), (int)1).show();
        }
        catch (Exception exception) {
            String m = exception.getMessage();
            Toast.makeText((Context)app, (CharSequence)(m != null ? m : "Could not use that audio file"), (int)1).show();
        }
        return true;
    }

    public void addProgramMenus() {
        if (app.pyPane == null) return;
        if (app.pyPane.findViewWithTag("program-menus") != null) return;
        int at = app.pyPane.getChildCount() > 2 ? 2 : app.pyPane.getChildCount();
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = app.dp(8);
        app.pyPane.addView(app.programMenus.buildRow(), at, lp);
    }

    /** JavaScript and TypeScript files run with Node.js (Termux on Android). */
    static boolean isNodeScript(String name) {
        String low = name.toLowerCase();
        return low.endsWith(".js") || low.endsWith(".mjs") || low.endsWith(".cjs") || low.endsWith(".jsx") || low.endsWith(".ts") || low.endsWith(".mts") || low.endsWith(".tsx");
    }
}
