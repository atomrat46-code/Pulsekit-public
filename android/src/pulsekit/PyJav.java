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
        // The editor's caption: Code, or Prompt for a prompt sheet (pkShowPromptModes).
        this.pkEditorCaption = app.text("Code", 13, true);
        this.pkEditorCaption.setTag("pyjav-editor-caption");
        this.pkEditorCaption.setPadding(0, app.dp(14), 0, app.dp(4));
        app.pyEditor = new EditText((Context)app);
        app.pyEditor.setText((CharSequence)"#!/usr/bin/env python3\n\"\"\"Pulsekit drum script.\"\"\"\nprint(\"edit me\")\n");
        app.pyEditor.setTypeface(Typeface.MONOSPACE);
        app.pyEditor.setTextColor(FG);
        app.pyEditor.setTextSize(2, 12.0f);
        // An outline sets the editor apart from the rows around it.
        android.graphics.drawable.GradientDrawable frame = new android.graphics.drawable.GradientDrawable();
        frame.setColor(ELEV);
        frame.setCornerRadius(app.dp(10));
        frame.setStroke(app.dp(1), MUTED);
        app.pyEditor.setBackground((Drawable)frame);
        app.pyEditor.setPadding(app.dp(10), app.dp(10), app.dp(10), app.dp(10));
        app.pyEditor.setGravity(0x800033);
        app.pyEditor.setMinLines(8);
        // A fixed height that scrolls inside, so a long program does not make the page long.
        app.pyEditor.setVerticalScrollBarEnabled(true);
        // A drag in the editor scrolls it while it can go that way; at its top or end the page takes
        // the drag, so the page above stays reachable with a finger.
        final float[] lastY = new float[1];
        app.pyEditor.setOnTouchListener((v, ev) -> {
            int action = ev.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                lastY[0] = ev.getY();
                v.getParent().requestDisallowInterceptTouchEvent(v.canScrollVertically(1) || v.canScrollVertically(-1));
            } else if (action == android.view.MotionEvent.ACTION_MOVE) {
                float dy = ev.getY() - lastY[0];
                lastY[0] = ev.getY();
                if (dy != 0) v.getParent().requestDisallowInterceptTouchEvent(v.canScrollVertically(dy > 0 ? -1 : 1));
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        app.pyPane.addView((View)this.pkEditorCaption);
        app.pyPane.addView((View)app.pyEditor, new android.widget.LinearLayout.LayoutParams(-1, app.dp(360)));
        // With the keyboard up the page is short: the editor is made lower so the rows above it
        // stay on screen to tap and to drag the page by.
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int want = Math.min(app.dp(360), Math.max(app.dp(140), (b - t) - app.dp(120)));
            android.view.ViewGroup.LayoutParams lp = app.pyEditor.getLayoutParams();
            if (b - t > 0 && lp != null && lp.height != want) {
                lp.height = want;
                app.pyEditor.post(() -> app.pyEditor.setLayoutParams(lp));
            }
        });
        scroll.addView((View)app.pyPane, new android.widget.FrameLayout.LayoutParams(-1, -2));
        frameLayout.addView((View)scroll);
    }

    boolean pkPyWired;

    /** "Code" above the editor ("Prompt" while a prompt sheet is loaded). */
    android.widget.TextView pkEditorCaption;

    boolean pkPyRecentMute;

    android.widget.TextView pkPyLog;
    /** What the last run wrote below Run; only that text can be saved as results. */
    String pkRunLog;

    android.widget.TextView pkPyHint;

    /** The hint's full text (the shown one leaves out its "Extra args:" line). */
    String pkHintFull = "";

    android.widget.EditText pkPyArgs;

    android.widget.TextView pkPyInput;

    String pkPyInputToken;

    String pkPyInputPath;

    String pkPyOutputPath;

    android.widget.Spinner pkPyRecent;

    java.util.List pkPyRecentItems;
    /** The arguments of the last run started: its input files, for the prompt library (PromptKeep). */
    java.util.List pkLastArgv;

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
        android.widget.TextView tab = app.text("DB/Prompts", 13, true);
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
        boolean prompt = app.pyName != null && app.pyName.toLowerCase().endsWith(".prompt");
        // Another program loaded: Return (to the Media browser or the Prompts page) was for the one before.
        if (this.pkReturn != null && !String.valueOf(app.pyName).equals(this.pkReturnFor)) this.pkSetReturn(null);
        // Another program: the prompt sheet's report, reference files and invented output name go.
        if (!prompt) {
            this.pkPromptReport = null;
            this.pkRef1Path = null;
            this.pkRef2Path = null;
            this.pkOutputInvented = false;
            this.pkPromptDescription = "";
        }
        if (this.pkPromptModes == null) return;
        this.pkPromptModes.setVisibility(prompt ? 0 : 8);
        if (this.pkEditorCaption != null) this.pkEditorCaption.setText(prompt ? "Prompt" : "Code");
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
        // The audio input carries over to a program that takes a sound (not to SogniPedit, which
        // would name its picture after it).
        if (this.pkPyInputPath == null && this.pkAudioInputPath != null && new java.io.File(this.pkAudioInputPath).isFile() && pulsekit.PyJavHints.takesAudio(app.pyName, hint)) {
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
        // Its run still going (another program looked at meanwhile): shown again.
        this.pkShowRunning(item.name);
    }

    /** Recent: the run picked (a tap on a program, or one of its runs) is loaded and run. */
    /** Select program: the program and its parameters are loaded; Run starts it. */
    public void pkLoadRecent(int index) {
        if (this.pkPyRecentItems == null || index <= 0 || index > this.pkPyRecentItems.size()) return;
        this.pkApplyRecent(index);
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
        // The hint keeps its full text for filling in a program's args (pkHintText); it shows it
        // without the "Extra args:" line, which the Arguments box below shows itself.
        this.pkPyHint = new android.widget.TextView(app) {
            @Override
            public void setText(CharSequence text, android.widget.TextView.BufferType type) {
                pkHintFull = text == null ? "" : text.toString();
                super.setText(pulsekit.PyJavHints.shown(pkHintFull), type);
            }
        };
        this.pkPyHint.setTextSize(2, 12.0f);
        this.pkPyHint.setTypeface(Typeface.SANS_SERIF, 0);
        this.pkPyHint.setIncludeFontPadding(false);
        this.pkPyHint.setText("Possible extra args appear after you browse a file.");
        this.pkPyHint.setTextColor(FG);
        app.pyPane.addView(this.pkPyHint, slot);
        slot = slot + 1;
        // Arguments: a tap shows or hides the args box. Hidden at first, as Params sets the args.
        final android.widget.TextView argsHead = app.text("Arguments \u25b8", 13, true);
        argsHead.setTag("pyjav-args-head");
        argsHead.setPadding(0, app.dp(10), 0, app.dp(10));
        argsHead.setClickable(true);
        argsHead.setOnClickListener(v -> {
            boolean open = this.pkPyArgs.getVisibility() != 0;
            this.pkPyArgs.setVisibility(open ? 0 : 8);
            argsHead.setText(open ? "Arguments \u25be" : "Arguments \u25b8");
        });
        app.pyPane.addView(argsHead, slot);
        slot = slot + 1;
        this.pkPyArgs = new android.widget.EditText(app);
        this.pkPyArgs.setHint("Extra args");
        this.pkPyArgs.setSingleLine(false);
        this.pkPyArgs.setMinLines(2);
        this.pkPyArgs.setTextColor(FG);
        this.pkPyArgs.setHintTextColor(MUTED);
        this.pkPyArgs.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-1, -2));
        this.pkPyArgs.setVisibility(8);
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
        // Params and Run apart, and in different colours: a tap meant for Params no longer starts a run.
        android.widget.TextView paramsBtn = app.action("Params", ELEV, FG, pulsekit.PyJavUi.params(app));
        paramsBtn.setTag("pyjav-params");
        app.pyPane.addView(paramsBtn, slot);
        slot = slot + 1;
        android.widget.TextView runBtn = app.action("Run", FG, BG, pulsekit.PyJavUi.click(app));
        runBtn.setTag("pyjav-run");
        android.widget.LinearLayout.LayoutParams runAt = new android.widget.LinearLayout.LayoutParams(-1, -2);
        runAt.topMargin = app.dp(28);
        app.pyPane.addView(runBtn, slot, runAt);
        slot = slot + 1;
        // Return: back to what opened this program (the Media browser, for Upscale/resize image).
        this.pkReturnBtn = app.action("Return to Media browser", ELEV, FG, v -> this.pkDoReturn());
        this.pkReturnBtn.setTag("pyjav-return");
        this.pkReturnBtn.setVisibility(this.pkReturn == null ? 8 : 0);
        app.pyPane.addView(this.pkReturnBtn, slot);
        slot = slot + 1;
        // Stop batch: BatchRun's runs end after the one in progress.
        this.pkBatchStopBtn = app.action("Stop batch", ELEV, FG, v -> this.pkStopBatch());
        this.pkBatchStopBtn.setTag("pyjav-batch-stop");
        this.pkBatchStopBtn.setVisibility(pulsekit.BatchQueue.current() == null ? 8 : 0);
        app.pyPane.addView(this.pkBatchStopBtn, slot);
        slot = slot + 1;
        // The run's output, set apart under its own headline.
        android.widget.TextView outHead = app.text("Output", 13, true);
        outHead.setTag("pyjav-output-head");
        android.widget.LinearLayout.LayoutParams outAt = new android.widget.LinearLayout.LayoutParams(-1, -2);
        outAt.topMargin = app.dp(18);
        app.pyPane.addView(outHead, slot, outAt);
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

    /** This run's "Save this run into DB" (Params). */
    boolean pkSaveRun;

    /** What Return opens (the Media browser that started ImageUpscaler), or null: the button is hidden. */
    Runnable pkReturn;
    android.view.View pkReturnBtn;
    String pkReturnFor;

    public void pkSetReturn(Runnable back) {
        this.pkSetReturn("Return to Media browser", back);
    }

    /** Return: back to what opened this program, the button saying where ("Return to Prompts"). */
    public void pkSetReturn(String label, Runnable back) {
        this.pkReturn = back;
        // It belongs to the program loaded now: another one loaded (from Recent, a script) drops it.
        this.pkReturnFor = back == null ? null : app.pyName;
        if (this.pkReturnBtn != null) {
            this.pkReturnBtn.setVisibility(back == null ? 8 : 0);
            if (label != null && this.pkReturnBtn instanceof android.widget.TextView) ((android.widget.TextView) this.pkReturnBtn).setText(label);
        }
    }

    void pkDoReturn() {
        Runnable back = this.pkReturn;
        this.pkSetReturn(null);
        if (back != null) back.run();
    }

    /**
     * ImageUpscaler with --output_dir original: a copy of the picture it made in the input picture's
     * own folder (the Media browser folder it was picked from, or the folder of a file given by
     * path), written off the main thread; the line it adds to the log says where.
     */
    void pkCopyToOriginal(final pulsekit.JavaRun.Result result, final java.util.List argv) {
        if (!"ImageUpscaler.java".equals(app.pyName) || argv == null) return;
        int at = argv.indexOf("--output_dir");
        if (at < 0 || at + 1 >= argv.size() || !"original".equals(String.valueOf(argv.get(at + 1)).trim().toLowerCase())) return;
        final pulsekit.JavaRun.FileOut made = pulsekit.PromptKeep.made(result);
        if (made == null) return;
        String input = this.pkPyInputPath;
        String[] origin = input == null ? null : pulsekit.MediaBrowser.ORIGIN.get(input);
        if (origin == null && input != null) {
            java.io.File in = new java.io.File(input);
            // A file the app copied in (Browse DB, the system picker): its own folder is not known.
            if (in.getParentFile() != null && !in.getAbsolutePath().startsWith(app.getCacheDir().getAbsolutePath())) origin = new String[] {"", in.getParentFile().getAbsolutePath()};
        }
        if (origin == null) {
            this.pkAddLogLine("Output folder: the input picture's own folder is not known (it came from the DB or the system picker); " + made.name + " is in Download");
            return;
        }
        final String[] where = origin;
        final String mime = made.name.toLowerCase().endsWith(".png") ? "image/png" : made.name.toLowerCase().endsWith(".webp") ? "image/webp" : "image/jpeg";
        new Thread(() -> {
            String line;
            try {
                line = "Output folder: copy saved as " + pulsekit.MediaBrowser.saveInto(app, where, null, made.name, mime, made.bytes);
            } catch (Throwable ex) {
                line = "Output folder: could not save a copy beside the picture (" + (ex.getMessage() == null ? ex.toString() : ex.getMessage()) + ")";
            }
            final String shown = line;
            app.runOnUiThread(() -> this.pkAddLogLine(shown));
        }, "pulsekit-output-dir").start();
    }

    /** A line under the run's log (kept when the log is still this run's). */
    void pkAddLogLine(String line) {
        String now = this.pkRunLog == null ? "" : this.pkRunLog;
        String next = now.length() == 0 ? line : now + "\n" + line;
        if (this.pkPyLog != null && (this.pkRunLog == null || this.pkRunLog.equals(this.pkPyLog.getText().toString()))) this.pkPyLog.setText(next);
        this.pkRunLog = next;
        app.setNow(line);
    }

    /** This run's entry in the pending database operations (PendingOps), until they are over. */
    String pkPending;

    /** SogniMusic's test without drums: Run anyway was pressed (the next run goes on), and the question, for the tests. */
    boolean pkDrumsWarned;
    android.app.AlertDialog pkAskDrums;

    /** Run was answered Yes ("run a new session while the current task is incomplete"): the next check passes. */
    boolean pkRunConfirmed;
    /** The question asked last, for the tests. */
    android.app.AlertDialog pkAskRerun;

    /**
     * True when Run must not go on now: a run stores in the prompt library, so Run asks first
     * ("Are you sure…"; Yes runs). Params always open, and a run that stores nothing does not wait.
     */
    boolean pkMustWait(String program, boolean running) {
        boolean confirmed = this.pkRunConfirmed;
        this.pkRunConfirmed = false;
        if (!running || !pulsekit.PendingOps.mustAsk(program)) return false;
        if (confirmed) return false;
        // Nothing is going any more (no run, no storing, no Save prompt open): what is listed was
        // left behind by a run that is over, so it leaves the list and Run goes on.
        if (RUNS.get() == 0 && KEEPING.get() == 0 && ASKING.get() == 0) {
            pulsekit.PendingOps.clear();
            for (Long download : DOWNLOAD_OF.values()) pulsekit.SogniDownloads.done(download.longValue());
            DOWNLOAD_OF.clear();
            return false;
        }
        String text = pulsekit.PendingOps.askText(pulsekit.PendingOps.storingCount());
        app.setNow("Asking: the current task is not complete");
        this.pkAskRerun = new android.app.AlertDialog.Builder(app).setTitle("Run again?").setMessage(text)
            .setPositiveButton("Yes", (d, w) -> {
                this.pkRunConfirmed = true;
                this.pkRunPyJav();
            })
            .setNegativeButton("No", (d, w) -> app.setNow("Not run: the current task is not complete")).show();
        return true;
    }

    /** A reconnect (the run timed out while its workflow goes on on Sogni): its File > Downloads run, its note, and how many so far. */
    Long pkReconnectDownload;
    String pkReconnectNote;
    int pkReconnects;
    /** The run shown on the "Running…" line (its time and news), and that line as set last. */
    pulsekit.JavaRun.Live pkLive, pkTicking;
    String pkLiveShown;
    /** The top line and the hint as the "Running…" tick set them last (left alone once something else is shown). */
    String pkTopShown, pkHintShown;
    /** DrumMidi's "no hits yet, abort?" question shown last, for the tests. */
    android.app.AlertDialog pkAskAbort;

    /** Every second while the run goes: "Running… 2:05 · Status: processing", while the text below Run is still that line. */
    void pkTickLive(final pulsekit.JavaRun.Live live, final String head) {
        // One tick a run (shown again after another program: the one going on carries on).
        if (this.pkPyLog == null || this.pkTicking == live) return;
        this.pkTicking = live;
        this.pkPyLog.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (pkLive != live || pkPyLog == null || live.done || live.aborted()) {
                    if (pkTicking == live) pkTicking = null;
                    return;
                }
                // DrumMidi without a hit for a while: asked whether to abort.
                String noHits = pulsekit.JavaRun.noHitsQuestion(app.pyName, live, System.currentTimeMillis());
                if (noHits != null) {
                    live.askedAt = System.currentTimeMillis();
                    pkAskAbort = new android.app.AlertDialog.Builder(app).setTitle(app.pyName).setMessage(noHits)
                        .setPositiveButton("Abort", (d, w) -> {
                            live.abort();
                            app.setNow("Aborting " + app.pyName + "\u2026");
                        })
                        .setNegativeButton("Keep waiting", (d, w) -> live.askedAt = System.currentTimeMillis()).show();
                }
                if (pkLiveShown != null && pkLiveShown.equals(pkPyLog.getText().toString())) {
                    String line = head + pulsekit.JavaRun.runningLine(live, System.currentTimeMillis());
                    pkPyLog.setText(line);
                    pkLiveShown = line;
                }
                // The top line and the hint show the same time and news (while nothing else was put there).
                String top = pulsekit.JavaRun.runningTop(app.pyName, live, System.currentTimeMillis());
                if (app.now != null && pkTopShown != null && pkTopShown.equals(app.now.getText().toString())) {
                    app.setNow(top);
                    pkTopShown = top;
                }
                if (pkPyHint != null && pkHintShown != null && pkHintShown.equals(pkPyHint.getText().toString())) {
                    pkPyHint.setText(top);
                    pkHintShown = top;
                }
                pkPyLog.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /** The runs going on, by program: {its Live, the head of its "Running…" line}. */
    static final java.util.Map<String, Object[]> PK_RUNNING = new java.util.concurrent.ConcurrentHashMap<String, Object[]>();

    /** The program picked again while its run goes on: its "Running…" line (time and news) shown again and kept up. */
    public boolean pkShowRunning(String name) {
        Object[] r = name == null ? null : PK_RUNNING.get(name);
        if (r == null) return false;
        pulsekit.JavaRun.Live live = (pulsekit.JavaRun.Live) r[0];
        if (live.done || live.aborted()) {
            PK_RUNNING.remove(name, r);
            return false;
        }
        String head = (String) r[1];
        long now = System.currentTimeMillis();
        this.pkLive = live;
        if (this.pkPyLog != null) {
            this.pkPyLog.setText(head + pulsekit.JavaRun.runningLine(live, now));
            this.pkLiveShown = this.pkPyLog.getText().toString();
        }
        String top = pulsekit.JavaRun.runningTop(name, live, now);
        app.setNow(top);
        this.pkTopShown = app.now == null ? null : app.now.getText().toString();
        if (this.pkPyHint != null) {
            this.pkPyHint.setText(top);
            this.pkHintShown = top;
        }
        this.pkTickLive(live, head);
        return true;
    }

    /** Why a run is joined again: "timed out after 1200 s", or its failure. */
    static String pkReconnectWhy(String log) {
        String why = "timed out";
        for (String line : (log == null ? "" : log).split("\n")) {
            String t = line.trim();
            if (t.startsWith("Timed out (")) why = "timed out after " + t.substring(11).replace(").", "").replace(")", "");
            else if (t.startsWith("Failed: ")) why = t.substring(8);
        }
        return why.length() > 100 ? why.substring(0, 100) + "\u2026" : why;
    }

    /** A playlist opened into the code area: Run plays it. */
    static final String PLS_HINT = "A playlist: Run plays it with PlayList.java (the Playlist window, Editor and Viewer).";

    /**
     * A .pls in the code area run: the text (as edited) handed to PlayList.java; what it wrote kept
     * with the playlists and opened in the Playlist window.
     */
    void pkRunPls(final String name, final String text) {
        if (this.pkPyLog != null) this.pkPyLog.setText("Running\u2026");
        app.setNow("PlayList.java: Running\u2026");
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            pulsekit.JavaRun.Result r;
            try {
                java.io.File in = new java.io.File(new java.io.File(app.getCacheDir(), "pyjav-pls"), new java.io.File(name).getName());
                in.getParentFile().mkdirs();
                java.nio.file.Files.write(in.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.util.List<String> argv = new java.util.ArrayList<String>();
                argv.add(in.getAbsolutePath());
                String src = app.programMenus.bundledSource("PlayList.java");
                if (src == null) throw new IllegalStateException("PlayList.java is not among the programs");
                r = pulsekit.JavaRun.run("PlayList.java", src, null, argv);
            } catch (Exception ex) {
                r = new pulsekit.JavaRun.Result("Failed: " + ex.getMessage(), new java.util.ArrayList<pulsekit.JavaRun.FileOut>(), 1);
            }
            final pulsekit.JavaRun.Result done = r;
            main.post(() -> {
                lastPlsRun = done;
                if (this.pkPyLog != null) this.pkPyLog.setText(done.log);
                if (!PlaylistScreen.afterRun(app, done)) app.setNow("PlayList.java made no playlist");
            });
        }, "pulsekit-pls").start();
    }

    /** The last playlist Run handed to PlayList.java, for the tests. */
    static pulsekit.JavaRun.Result lastPlsRun;

    public void pkRunPyJav() {
        if (this.pkMustWait(app.pyName, true)) return;
        // A playlist: PlayList.java plays it.
        if (app.pyName != null && app.pyName.toLowerCase().endsWith(".pls")) {
            this.pkRunPls(app.pyName, app.pyEditor != null ? app.pyEditor.getText().toString() : "");
            return;
        }
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
        String hint = this.pkPyHint != null ? this.pkHintFull : "";
        if (this.pkPyLog != null) this.pkPyLog.setText(this.pkPromptReport != null && this.pkPromptReport.length() > 0 ? this.pkPromptReport + "\nRunning…" : "Running…");
        boolean report = this.pkPromptReport != null && this.pkPromptReport.length() > 0;
        String top = (app.pyName == null ? "script.py" : app.pyName) + ": Running\u2026";
        if (this.pkPyHint != null) this.pkPyHint.setText(report ? this.pkPromptReport : top);
        this.pkHintShown = report ? null : top;
        app.setNow(top);
        this.pkTopShown = top;
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
            argv = pulsekit.JavaRun.argvFor(src, app.bpm(), app.style, app.bars, swing, extra, app.tsNum, app.tsDen);
        }
        // A step of Midi2Wav's test (CompareHits on the test's files): exactly these arguments.
        if (this.pkForcedArgs != null) {
            argv = new java.util.ArrayList<String>(this.pkForcedArgs);
            this.pkForcedArgs = null;
        }
        // The key file from Drum Midi Settings for every program that takes one (ImageUpscaler's runs have an input file).
        argv = pulsekit.JavaRun.withKeyFile(this.pkProgramText(), new java.util.ArrayList<String>(argv));
        // Midi2Wav without a SoundFont: Pulsekit's kit (the active drum set) as one.
        if (pulsekit.JavaRun.needsKit(name, argv)) {
            try {
                java.io.File kit = new java.io.File(dir, "Pulsekit-kit.sf2");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(kit);
                try {
                    fos.write(pulsekit.AudioIo.encodeSf2(app.playback.mixVoices(), "Pulsekit"));
                } finally {
                    fos.close();
                }
                argv = pulsekit.JavaRun.withKit(argv, kit.getAbsolutePath());
            } catch (Exception ex) {
                app.setNow("Could not write Pulsekit's kit as a SoundFont: " + ex.getMessage());
            }
        }
        // Hydrogen songs and patterns, and Reaper projects, given to a program: their MIDI.
        argv = pulsekit.DrumFiles.midiArgs(argv, dir);
        // A folder picked for Midi2HydroPattern (--dir): its MIDI files copied into the work folder.
        StringBuilder staged = new StringBuilder();
        argv = MidiFolder.forProgram(app, name, argv, dir, staged);
        if (staged.length() > 0) app.setNow(name.replace(".java", "") + " \u00b7 " + staged);
        // MediaBrowser without a folder: the one the Media browser opened last.
        if ("MediaBrowser.java".equals(name)) {
            pulsekit.MediaBrowser.load(app);
            argv = pulsekit.MediaDir.withLastRoot(argv);
        }
        // PlayList --nogui without a list writes the Default playlist: its file given.
        argv = pulsekit.Playlist.withDefault(name, argv, app.getFilesDir());
        // Params' Save this run into DB: taken off (the program never sees it), stored after the run.
        this.pkSaveRun = false;
        while (argv.remove(pulsekit.PromptKeep.SAVE_RUN)) this.pkSaveRun = true;
        // A prompt sheet's run: its mode, reference files and output name (as on the desktop, only for a
        // .prompt: ImageUpscaler opened after one was given the sheet's files too).
        if (name.toLowerCase().endsWith(".prompt")) {
            argv.add("--pk-run");
            argv.add(this.pkPromptRun == null ? "bash" : this.pkPromptRun);
            if (this.pkRef1Path != null && this.pkRef1Path.length() > 0 && !argv.contains(this.pkRef1Path)) argv.add(this.pkRef1Path);
            if (this.pkRef2Path != null && this.pkRef2Path.length() > 0 && !argv.contains(this.pkRef2Path)) argv.add(this.pkRef2Path);
            if (this.pkOutputInvented && this.pkPyOutputPath != null && this.pkPyOutputPath.length() > 0 && !argv.contains(this.pkPyOutputPath)) argv.add(this.pkPyOutputPath);
        }
        // SogniMusic's test without drums asked for: said first (DrumMidi would find nothing to check).
        String drums = this.pkDrumsWarned ? null : pulsekit.Midi2WavTest.drumsWarning(name, argv);
        this.pkDrumsWarned = false;
        if (drums != null) {
            if (this.pkPyLog != null) this.pkPyLog.setText(drums);
            app.setNow("SogniMusic's test needs drums");
            this.pkAskDrums = new android.app.AlertDialog.Builder(app).setTitle("SogniMusic test").setMessage(drums)
                .setPositiveButton("Run anyway", (d, w) -> {
                    this.pkDrumsWarned = true;
                    this.pkRunPyJav();
                })
                .setNegativeButton("Cancel", (d, w) -> app.setNow("Not run: SogniMusic's test needs drums")).show();
            return;
        }
        this.pkLastArgv = argv;
        // Each run keeps its own pending entry and File > Downloads run (PyJavUi.Run): one started
        // while another goes no longer takes over the other's (which then stayed In progress).
        this.pkPending = pulsekit.PendingOps.guarded(name, false) ? pulsekit.PendingOps.add(name, pulsekit.PendingOps.files(argv), this.pkSaveRun || pulsekit.PendingOps.storesInDb(argv)) : null;
        // File > Downloads: the Sogni run is in progress until its result is in (a reconnect carries on its run).
        pulsekit.SogniDownloads.init(app.getFilesDir());
        Long reuse = this.pkReconnectDownload;
        this.pkReconnectDownload = null;
        String note = this.pkReconnectNote;
        this.pkReconnectNote = null;
        if (note == null) this.pkReconnects = 0;
        if (this.pkPending != null && pulsekit.SogniDownloads.listed(name)) DOWNLOAD_OF.put(this.pkPending, reuse != null ? reuse : Long.valueOf(pulsekit.SogniDownloads.started(name, System.currentTimeMillis())));
        // Create again (File > Downloads): the arguments this run had.
        if (this.pkPending != null && DOWNLOAD_OF.containsKey(this.pkPending)) pulsekit.SogniDownloads.args(DOWNLOAD_OF.get(this.pkPending).longValue(), this.pkPyArgs != null ? this.pkPyArgs.getText().toString() : "");
        RUNS.incrementAndGet();
        // Kept running (a notification) until the run is over: switching apps does not lose it.
        pulsekit.RunKeeper.start(app, name);
        pulsekit.JavaRun.Live live = pulsekit.JavaRun.start(name, src, app.pkPyBytes, argv, pulsekit.PyJavUi.listener(app, this.pkPending, argv, this.pkSaveRun, src));
        // Its workflow id and exact arguments kept with its download: Resume download if the app is closed.
        Long downloadRun = this.pkPending == null ? null : DOWNLOAD_OF.get(this.pkPending);
        if (downloadRun != null) {
            pulsekit.SogniDownloads.argv(downloadRun.longValue(), argv);
            live.download(downloadRun.longValue());
        }
        if (note != null) live.add(note + "\n");
        String head = note == null ? (this.pkPromptReport != null && this.pkPromptReport.length() > 0 ? this.pkPromptReport + "\n" : "") : note + "\n";
        this.pkLive = live;
        // Kept by the program: shown again when it is picked after another (SogniPedit's Params looked at meanwhile).
        PK_RUNNING.put(name, new Object[] {live, head});
        if (this.pkPyLog != null) {
            this.pkPyLog.setText(head + "Running\u2026");
            this.pkLiveShown = this.pkPyLog.getText().toString();
        }
        this.pkTickLive(live, head);
    }

    /** The run's own pending entry, arguments and Save this run into DB, before its result is shown (PyJavUi.Run). */
    public void pkShowPyResult(pulsekit.JavaRun.Result result, String pending, java.util.List<String> argv, boolean saveRun, String src) {
        this.pkPending = pending;
        this.pkLastArgv = argv;
        this.pkSaveRun = saveRun;
        // Timed out (or the network went away) while the workflow goes on on Sogni: joined again.
        String log = result == null ? null : result.log;
        java.util.List<String> again = this.pkReconnects < pulsekit.JavaRun.RECONNECTS ? pulsekit.JavaRun.reconnectArgs(app.pyName, src, log, argv) : null;
        if (again != null && result != null && result.files != null && !result.files.isEmpty()) {
            boolean media = false;
            for (int i = 0; i < result.files.size(); i++) if (((pulsekit.JavaRun.FileOut) result.files.get(i)).name.toLowerCase().matches(".*\\.(mp4|webm|mov|mp3|wav|png|jpe?g|webp)$")) media = true;
            if (media) again = null;
        }
        if (again != null) {
            this.pkReconnects++;
            if (saveRun) again.add(pulsekit.PromptKeep.SAVE_RUN);
            String note = "Reconnecting to workflow " + pulsekit.JavaRun.workflowOf(again) + " (" + pkReconnectWhy(log) + "; try " + this.pkReconnects + " of " + pulsekit.JavaRun.RECONNECTS + ")\u2026";
            pulsekit.PendingOps.done(pending);
            Long download = pending == null ? null : DOWNLOAD_OF.remove(pending);
            if (download != null) pulsekit.SogniDownloads.reconnecting(download.longValue(), note);
            this.pkReconnectDownload = download;
            this.pkReconnectNote = note;
            this.pkPending = null;
            this.pkForcedArgs = again;
            this.pkRunConfirmed = true;
            this.pkDrumsWarned = true;
            app.setNow(note);
            this.pkRunPyJav();
            return;
        }
        this.pkShowPyResult(result);
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
        if (this.pkMustWait(name, false)) return;
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
        // The run's entry (PendingOps), for a failure below after the field is cleared; left alone
        // once its storing has started (that work ends it).
        final String runPending = this.pkPending;
        boolean storingStarted = false;
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
        // The prompt library work runs in the background: storing a large result (a 10 s video)
        // re-encrypts the whole library, which on the main thread froze the app ("not responding").
        final pulsekit.JavaRun.Result kept0 = result;
        final java.util.List<String> argv = this.pkLastArgv;
        final String program = app.pyName;
        final boolean genToDb = DrumMidiSettingsPage.genToDb;
        final boolean musicToDb = DrumMidiSettingsPage.musicToDb;
        final String pending = this.pkPending;
        this.pkPending = null;
        final java.io.File filesDir = app.getFilesDir();
        if (program != null && program.equals("SogniVideo.java")) {
            // Join is unticked for the next run.
            if (this.pkPyArgs != null) {
                String now = this.pkPyArgs.getText().toString();
                if (now.indexOf("--join") >= 0) this.pkPyArgs.setText(ProgramParams.drop(now, "--join"));
            }
            PyJavParams.dropJoin(app, program);
        }
        final String shown = log;
        final boolean saveRun = this.pkSaveRun && status.startsWith("Succeeded");
        this.pkSaveRun = false;
        final boolean any = PromptKeep.hasWork(kept0, argv, program, genToDb, musicToDb) || saveRun;
        // The run's prompt sheets (--saveprompt): not saved again when unchanged; else PyJav asks where they go.
        final java.util.List<PromptSave.Pending> sheets = PromptSave.sheets(kept0);
        PromptSave.load(filesDir);
        // File > Downloads: what the run saved; pending until what it stores is stored.
        if (pending != null && DOWNLOAD_OF.containsKey(pending)) pulsekit.SogniDownloads.result(DOWNLOAD_OF.get(pending).longValue(), status, log, ArtJava.SAVED);
        if (!any) this.pkDone(pending);
        if (any) {
            storingStarted = true;
            log = log + "\nPrompt library: storing\u2026";
            if (this.pkPyLog != null) this.pkPyLog.setText(log);
            this.pkRunLog = log;
            this.pkKeeping = true;
            KEEPING.incrementAndGet();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String stored;
                    final java.util.List<PromptSave.Pending> asking = new java.util.ArrayList<PromptSave.Pending>();
                    try {
                        stored = PromptKeep.keepAll(filesDir, kept0, argv, program, genToDb, musicToDb, sheets.isEmpty());
                        if (saveRun) {
                            String run = PromptKeep.keepRun(filesDir, kept0, argv, program);
                            stored = stored.length() == 0 ? run : run.length() == 0 ? stored : stored + "\n" + run;
                        }
                        if (!sheets.isEmpty()) {
                            PromptVault vault = PromptVault.open(filesDir);
                            for (PromptSave.Pending p : sheets) {
                                String same = PromptSave.unchanged(vault, p.sheet, kept0, argv);
                                String line;
                                if (same != null) {
                                    line = PromptSave.unchangedNote(p.sheet, same);
                                } else if (PromptSave.ask()) {
                                    asking.add(p);
                                    continue;
                                } else {
                                    line = pkWriteSheet(PromptSave.storeAsIs(filesDir, p, kept0, argv), pending);
                                }
                                stored = stored.length() == 0 ? line : stored + "\n" + line;
                            }
                        }
                    } catch (Throwable ex) {
                        // Never left at "storing…": what went wrong is said instead.
                        String m = ex.getMessage();
                        stored = "Prompt library: could not store (" + ex.getClass().getSimpleName() + (m == null || m.length() == 0 ? "" : ": " + m) + ")";
                    }
                    final String kept = stored;
                    app.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pkKeeping = false;
                            KEEPING.decrementAndGet();
                            // Sheets to ask about: the run stays pending until each is saved or cancelled.
                            if (asking.isEmpty()) pkDone(pending);
                            // The "storing" line becomes what was stored; notes added since stay.
                            String now = pkRunLog == null ? shown + "\nPrompt library: storing\u2026" : pkRunLog;
                            String done = now.replace("\nPrompt library: storing\u2026", kept.length() > 0 ? "\n" + kept : "");
                            // Only when the text below Run is still this run's (another run may have started).
                            if (pkPyLog != null && pkRunLog != null && pkRunLog.equals(pkPyLog.getText().toString())) pkPyLog.setText(done);
                            pkRunLog = done;
                            if (!asking.isEmpty()) {
                                ASKING.incrementAndGet();
                                pkAskSheets(asking, 0, kept0, argv, pending);
                            }
                        }
                    });
                }
            }, "pulsekit-prompt-keep").start();
        }
        // --output_dir original: a copy beside the input picture (ImageUpscaler).
        if (status.startsWith("Succeeded")) this.pkCopyToOriginal(result, argv);
        // What the run made is offered (played, shown, opened). A problem in one of these is a note
        // under the run's log: it never turns a run that succeeded into "Failed".
        final boolean ok = status.startsWith("Succeeded");
        final pulsekit.JavaRun.Result made = result;
        final String madeLog = log;
        // Midi2Wav's test goes on (DrumMidi, CompareHits) instead of offering the WAV.
        final boolean testing = this.pkTestStep(program, argv, status, made, madeLog);
        // A run of a batch (BatchRun): what it made is saved, not shown; the next run follows.
        final pulsekit.BatchQueue batch = pulsekit.BatchQueue.current();
        final boolean inBatch = batch != null && batch.takes(program) && !batch.joining;
        this.pkOffer("the sound", ok && !testing && !inBatch, () -> AudioOffer.offer(app, pulsekit.PyJavHints.madeAudio(madeLog), made));
        // VideoFrames --upscale: the first frame goes on to ImageUpscaler (its Params open) instead of being shown.
        final boolean upscaling = ok && "VideoFrames.java".equals(program) && argv != null && argv.contains("--upscale") && this.pkFrameToUpscaler(made);
        // Pictures it made (SogniChat's tool results) are shown.
        this.pkOffer("the pictures", ok && !upscaling && !inBatch, () -> PictureOffer.offer(app, made));
        // A video it made (SogniVideo's clip, a SogniChat tool result) is played, with its controls.
        this.pkOffer("the video", ok && !inBatch, () -> VideoOffer.offer(app, made));
        // MidiDrumGen's groove is played with the kit's sounds, with Play / Stop.
        // With --towav or --test it goes on to Midi2Wav (and its test) instead.
        final boolean toWav = ok && pulsekit.Midi2WavTest.fromGen(program, argv) && this.pkGenToWav(made, argv);
        // (Not after a batch, --count: its grooves go to the batch folder.)
        this.pkOffer("the groove", ok && !toWav && "MidiDrumGen.java".equals(app.pyName) && !(argv != null && argv.contains("--count")), () -> MidiOffer.offer(app, made));
        // MediaBrowser: its folder in the Media browser.
        this.pkOffer("the Media browser", ok && "MediaBrowser.java".equals(app.pyName), () -> {
            String browse = MediaDir.opened(madeLog);
            if (browse != null) MediaBrowser.open(app, browse);
        });
        // PlayList: the playlist it wrote (or merged) kept with the playlists, and opened in the Playlist window.
        this.pkOffer("the playlist", ok && "PlayList.java".equals(app.pyName), () -> PlaylistScreen.afterRun(app, made));
        // BatchRun's plan starts the batch; each run of it (and JoinVideo at the end) leads to the next step.
        if (batch != null && batch.takes(program)) {
            batch.done(status, this.pkBatchCopies(made));
            this.pkBatchNext(batch);
        } else if (pulsekit.BatchQueue.start(program, argv, status, madeLog) != null) {
            this.pkBatchNext(pulsekit.BatchQueue.current());
        }
        } catch (Throwable ex) {
            String m = ex.getMessage();
            status = "Failed: " + (m == null || m.trim().length() == 0 ? ex.toString() : m);
            // File > Downloads says so too (it was left In progress before).
            Long download = this.pkPending == null ? null : DOWNLOAD_OF.get(this.pkPending);
            if (download != null) pulsekit.SogniDownloads.result(download.longValue(), status, log, ArtJava.SAVED);
            this.pkDone(this.pkPending);
            if (!storingStarted) this.pkDone(runPending);
            this.pkPending = null;
            if (this.pkPyHint != null) this.pkPyHint.setText(status);
            app.setNow(status);
            android.widget.Toast.makeText(app, status, 1).show();
            if (this.pkPyLog != null) this.pkPyLog.setText(status);
            this.pkRunLog = status;
        }
    }

    /** The next run's arguments as they are (a step of Midi2Wav's test), or null. */
    java.util.List<String> pkForcedArgs;
    /** The test's report shown last, for the tests. */
    static android.app.AlertDialog pkTestReport;

    /**
     * Midi2Wav's test (--test), one step a run: after Midi2Wav, DrumMidi_CRT on its WAV; after
     * DrumMidi, CompareHits on the WAV, DrumMidi's MIDI and the song; then CompareHits --truth on
     * DrumMidi's MIDI against the MIDI played; then the report. True when the test took this run.
     */
    boolean pkTestStep(String program, java.util.List<String> argv, String status, pulsekit.JavaRun.Result result, String log) {
        pulsekit.Midi2WavTest t = pulsekit.Midi2WavTest.current();
        // SogniMusic: the track is also kept as a WAV (unless --no_wav); with --test, the test follows.
        if (pulsekit.Midi2WavTest.musicToWav(program, argv) && status != null && status.startsWith("Succeeded")) {
            String mp3Name = pulsekit.PyJavHints.madeAudio(log);
            byte[] mp3 = null;
            if (mp3Name != null && mp3Name.toLowerCase().endsWith(".mp3") && result != null && result.files != null) {
                for (int i = 0; i < result.files.size(); i++) {
                    pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) result.files.get(i);
                    if (mp3Name.equals(f.name)) mp3 = f.bytes;
                }
            }
            String path = mp3 == null ? null : AudioOffer.useAsInput(app, mp3Name, mp3);
            if (path == null) return false;
            // --test, or --batchdir (DrumMidi's MIDI of the track into the shared batch folder): DrumMidi follows.
            final boolean test = pulsekit.Midi2WavTest.musicSteps(program, argv, false);
            final java.util.List<String> musicArgv = argv;
            if (argv.contains(pulsekit.Midi2WavTest.VST)) this.pkAddLogLine(VST_HERE);
            this.pkAddLogLine("Making a WAV of " + mp3Name + "…");
            AudioOffer.toWav(app, new java.io.File(path), (wav, why) -> {
                if (wav == null) {
                    this.pkAddLogLine("Could not make a WAV of " + mp3Name + (why == null ? "" : ": " + why));
                    return;
                }
                try {
                    String at = ArtJava.publish(app, wav.getName(), java.nio.file.Files.readAllBytes(wav.toPath()));
                    this.pkAddLogLine(at == null ? "Could not save " + wav.getName() : "saved " + at);
                } catch (Exception ex) {
                    this.pkAddLogLine("Could not save " + wav.getName());
                }
                if (test) this.pkStartMusicTest(musicArgv, wav);
            });
            // The track is offered as usual unless the test takes the run.
            return pulsekit.Midi2WavTest.musicWanted(program, argv);
        }
        // Midi2Wav --dir --test: DrumMidi_CRT --dir on the folder's WAVs, then CompareHits --batch.
        if (pulsekit.Midi2WavTest.batchWanted(program, argv)) {
            t = pulsekit.Midi2WavTest.startBatch(status, argv);
            if (t == null) return false;
            final java.util.List<String> drumArgs = t.batchDrumArgs();
            this.pkAddLogLine("Test: DrumMidi_CRT on every WAV in " + new java.io.File(t.batch).getName() + ", then CompareHits --batch");
            app.handler.post(() -> {
                this.pkPyInputPath = null;
                app.programMenus.selectProgram("Java", "DrumMidi_CRT.jar");
                this.pkForcedArgs = drumArgs;
                this.pkRunPyJav();
            });
            return true;
        }
        if (pulsekit.Midi2WavTest.wanted(program, argv)) {
            String wavName = pulsekit.PyJavHints.madeAudio(log);
            byte[] wav = null;
            if (wavName != null && result != null && result.files != null) {
                for (int i = 0; i < result.files.size(); i++) {
                    pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) result.files.get(i);
                    if (wavName.equals(f.name)) wav = f.bytes;
                }
            }
            String path = wav == null ? null : AudioOffer.useAsInput(app, wavName, wav);
            t = pulsekit.Midi2WavTest.start(status, argv, path == null ? null : new java.io.File(path));
            if (t == null) return false;
            this.pkAddLogLine("Test: DrumMidi_CRT on " + wavName + ", then CompareHits");
            app.handler.post(() -> AudioOffer.makeDrumMidi(app, true));
            return true;
        }
        if (t == null) return false;
        final pulsekit.Midi2WavTest test = t;
        try {
            if (t.stage == pulsekit.Midi2WavTest.BATCH_DRUMS && "DrumMidi_CRT.jar".equals(program)) {
                if (!t.batchDrumsDone(status)) {
                    this.pkAddLogLine("Test stopped: DrumMidi_CRT made no MIDI (" + (status == null ? "no result" : status) + ")");
                    return true;
                }
                this.pkAddLogLine("Test: CompareHits --batch " + new java.io.File(t.batch).getName());
                app.handler.post(() -> this.pkRunTestCompare(test.batchCompareArgs()));
                return true;
            }
            if (t.stage == pulsekit.Midi2WavTest.BATCH_COMPARE && "CompareHits.java".equals(program)) {
                t.batchCompareDone(log);
                app.handler.post(() -> this.pkShowTestReport(test));
                return true;
            }
            if (t.stage == pulsekit.Midi2WavTest.DRUM_MIDI && "DrumMidi_CRT.jar".equals(program)) {
                byte[] drums = null;
                String drumsName = null;
                if (result != null && result.files != null) {
                    for (int i = 0; i < result.files.size() && drums == null; i++) {
                        pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) result.files.get(i);
                        String low = f.name.toLowerCase();
                        if ((low.endsWith(".mid") || low.endsWith(".midi")) && f.bytes.length >= 4 && f.bytes[0] == 'M' && f.bytes[1] == 'T') {
                            drums = f.bytes;
                            drumsName = f.name;
                        }
                    }
                }
                if (drums == null && this.pkPyOutputPath != null && new java.io.File(this.pkPyOutputPath).isFile()) {
                    drums = java.nio.file.Files.readAllBytes(new java.io.File(this.pkPyOutputPath).toPath());
                    drumsName = new java.io.File(this.pkPyOutputPath).getName();
                }
                if (!t.drumMidiDone(drums, drumsName, app.learned, app.importedSongs)) {
                    this.pkAddLogLine("Test stopped: DrumMidi_CRT made no MIDI");
                    return true;
                }
                if (t.batchNote.length() > 0) this.pkAddLogLine(t.batchNote);
                // --batchdir without --test: putting the MIDI in the batch folder was all.
                if (!t.testing) return true;
                this.pkAddLogLine("Test: CompareHits on " + new java.io.File(t.wav).getName() + ", " + new java.io.File(t.drums).getName()
                    + (t.song.length() > 0 ? ", " + new java.io.File(t.song).getName() : " (no song was made)"));
                app.handler.post(() -> this.pkRunTestCompare(test.compareArgs()));
                return true;
            }
            if ((t.stage == pulsekit.Midi2WavTest.COMPARE || t.stage == pulsekit.Midi2WavTest.AGAINST_PLAYED) && "CompareHits.java".equals(program)
                    && (status == null || !status.startsWith("Succeeded"))) {
                pulsekit.Midi2WavTest.stop();
                this.pkAddLogLine("Test stopped: CompareHits did not run (" + (status == null ? "no result" : status) + ")");
                return true;
            }
            if (t.stage == pulsekit.Midi2WavTest.COMPARE && "CompareHits.java".equals(program)) {
                if (t.compareDone(log)) {
                    this.pkAddLogLine("Test: CompareHits --truth: " + new java.io.File(t.drums).getName() + (t.song.length() > 0 ? " and the song" : "") + " against the drums played (" + new java.io.File(t.played).getName() + ")");
                    app.handler.post(() -> this.pkRunTestCompare(test.againstPlayedArgs()));
                } else {
                    app.handler.post(() -> this.pkShowTestReport(test));
                }
                return true;
            }
            if (t.stage == pulsekit.Midi2WavTest.AGAINST_PLAYED && "CompareHits.java".equals(program)) {
                t.againstPlayedDone(log);
                app.handler.post(() -> this.pkShowTestReport(test));
                return true;
            }
        } catch (Exception ex) {
            this.pkAddLogLine("Test stopped: " + ex.getMessage());
        }
        // Another program ran in between: the test is over.
        pulsekit.Midi2WavTest.stop();
        return false;
    }

    /** VideoFrames --upscale: ImageUpscaler with the first picture the run made as its input, its Params open; false when it made none. */
    boolean pkFrameToUpscaler(pulsekit.JavaRun.Result made) {
        if (made == null || made.files == null) return false;
        for (Object o : made.files) {
            pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) o;
            if (f.name == null || !f.name.toLowerCase().endsWith(".png") || f.bytes == null) continue;
            try {
                java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
                if (!dir.isDirectory()) dir.mkdirs();
                final java.io.File in = new java.io.File(dir, f.name);
                java.nio.file.Files.write(in.toPath(), f.bytes);
                app.runOnUiThread(() -> {
                    app.programMenus.selectProgram("Java", "ImageUpscaler.java");
                    this.pkUseInputPath(in.getAbsolutePath());
                    this.pkOpenParams();
                });
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }

    /** --vst on the phone: VST plugins run on a computer (vst_render.py in Pulsekit's desktop app). */
    static final String VST_HERE = "--vst: VST plugins play on a computer (vst_render.py in Pulsekit's desktop app)";

    /** MidiDrumGen --towav / --test: its MIDI played into a WAV by Midi2Wav (and tested). False when it made no MIDI. */
    boolean pkGenToWav(pulsekit.JavaRun.Result result, java.util.List<String> genArgv) {
        pulsekit.JavaRun.FileOut midi = null;
        if (result != null && result.files != null) {
            for (int i = 0; i < result.files.size() && midi == null; i++) {
                pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) result.files.get(i);
                String low = f.name.toLowerCase();
                if ((low.endsWith(".mid") || low.endsWith(".midi")) && f.bytes.length >= 4 && f.bytes[0] == 'M' && f.bytes[1] == 'T') midi = f;
            }
        }
        if (midi == null) return false;
        try {
            java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, midi.name);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            try {
                fos.write(midi.bytes);
            } finally {
                fos.close();
            }
            final java.util.List<String> args = pulsekit.Midi2WavTest.genToWavArgs(f.getAbsolutePath(), genArgv,
                app.programMenus.bundledSource("Midi2Wav.java"), pulsekit.PyJavParams.load(app, "Midi2Wav.java"));
            if (pulsekit.Midi2WavTest.genToVst("MidiDrumGen.java", genArgv)) this.pkAddLogLine(VST_HERE + "; Midi2Wav plays it here");
            this.pkAddLogLine("Next: Midi2Wav on " + midi.name + (args.contains(pulsekit.Midi2WavTest.FLAG) ? ", then its test" : ""));
            app.handler.post(() -> {
                this.pkPyInputPath = null;
                app.programMenus.selectProgram("Java", "Midi2Wav.java");
                this.pkForcedArgs = args;
                this.pkRunPyJav();
            });
            return true;
        } catch (Exception ex) {
            this.pkAddLogLine("Could not hand " + midi.name + " to Midi2Wav: " + ex.getMessage());
            return false;
        }
    }

    /** SogniMusic's test: DrumMidi_CRT on the track's WAV, set from what the track was asked to be. */
    void pkStartMusicTest(java.util.List<String> argv, java.io.File wav) {
        pulsekit.Midi2WavTest t = pulsekit.Midi2WavTest.startMusic(argv, wav, pulsekit.PyJavParams.load(app, "DrumMidi_CRT.jar"));
        if (t == null) {
            this.pkAddLogLine("Test stopped: no WAV was made of the track");
            return;
        }
        this.pkAddLogLine("Test: " + t.expect.drumMidiWhy(t.drumSaved));
        this.pkAddLogLine((t.testing ? "Test: " : "Batch folder: ") + "DrumMidi_CRT on " + wav.getName() + ", then "
            + (t.testing ? "CompareHits" : "its MIDI into " + new java.io.File(t.batchDir).getName()));
        final java.util.List<String> drumArgs = t.drumMidiArgs();
        app.handler.post(() -> {
            this.pkPyInputPath = null;
            app.programMenus.selectProgram("Java", "DrumMidi_CRT.jar");
            this.pkForcedArgs = drumArgs;
            this.pkRunPyJav();
        });
    }

    /** The batch's Stop batch button (shown while a batch runs). */
    android.view.View pkBatchStopBtn;

    /** A batch run's clips and pictures, kept in the cache for JoinVideo (paths, in order). */
    java.util.List<String> pkBatchCopies(pulsekit.JavaRun.Result made) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (made == null || made.files == null) return out;
        java.io.File dir = new java.io.File(app.getCacheDir(), "pyjav-batch");
        if (!dir.isDirectory()) dir.mkdirs();
        for (int i = 0; i < made.files.size(); i++) {
            pulsekit.JavaRun.FileOut f = (pulsekit.JavaRun.FileOut) made.files.get(i);
            if (f.name == null || f.bytes == null || !f.name.toLowerCase().matches(".+\\.(mp4|m4v|mov|png|jpe?g|webp)")) continue;
            try {
                java.io.File copy = new java.io.File(dir, f.name);
                java.nio.file.Files.write(copy.toPath(), f.bytes);
                out.add(copy.getAbsolutePath());
            } catch (Exception ignored) {
                // left out of the join
            }
        }
        return out;
    }

    /**
     * The batch's next step: its next run, else JoinVideo on its clips (--join), else the summary.
     * Each run is the program's own, with the arguments BatchRun planned.
     */
    void pkBatchNext(pulsekit.BatchQueue q) {
        if (q == null) return;
        if (this.pkBatchStopBtn != null) this.pkBatchStopBtn.setVisibility(0);
        final String program;
        java.util.List<String> args;
        if (q.hasNext()) {
            program = q.program;
            args = q.nextArgs();
            this.pkAddLogLine(q.progress());
        } else if (q.joinNext()) {
            program = "JoinVideo.java";
            try {
                args = q.joinArgs(new java.io.File(app.getCacheDir(), "pyjav-in"));
            } catch (Exception ex) {
                this.pkAddLogLine("Batch: could not list the clips for JoinVideo (" + ex.getMessage() + ")");
                this.pkBatchOver(q);
                return;
            }
            this.pkAddLogLine("Batch: JoinVideo on the " + q.clips().size() + " clips, in order");
        } else {
            this.pkBatchOver(q);
            return;
        }
        final java.util.List<String> run = args;
        app.handler.post(() -> {
            this.pkPyInputPath = null;
            app.programMenus.selectProgram("Java", program);
            this.pkForcedArgs = run;
            // A batch's next run goes on without asking (the run before may still be storing).
            this.pkRunConfirmed = true;
            this.pkRunPyJav();
        });
    }

    void pkBatchOver(pulsekit.BatchQueue q) {
        pulsekit.BatchQueue.stop();
        if (this.pkBatchStopBtn != null) this.pkBatchStopBtn.setVisibility(8);
        this.pkAddLogLine(q.summary());
    }

    /** Stop batch: no run after the one in progress (JoinVideo is left out too). */
    void pkStopBatch() {
        pulsekit.BatchQueue q = pulsekit.BatchQueue.current();
        if (this.pkBatchStopBtn != null && q == null) this.pkBatchStopBtn.setVisibility(8);
        if (q == null) return;
        q.stopping = true;
        this.pkAddLogLine("Batch: stopping after the run in progress");
    }

    void pkRunTestCompare(java.util.List<String> args) {
        this.pkPyInputPath = null;
        app.programMenus.selectProgram("Java", "CompareHits.java");
        this.pkForcedArgs = args;
        this.pkRunPyJav();
    }

    /** The test's report: both comparisons, in a dialog; also saved as <wav>-test.txt (Download). */
    void pkShowTestReport(pulsekit.Midi2WavTest t) {
        String text = t.text();
        String name = new java.io.File(t.wav).getName().replaceAll("(?i)\\.wav$", "") + "-test.txt";
        String at = ArtJava.publish(app, name, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.pkAddLogLine("Test done" + (at == null ? "" : ": report saved as " + at));
        android.widget.TextView body = new android.widget.TextView(app);
        body.setText(text);
        body.setTag("midi2wav-test-report");
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(11);
        body.setTextIsSelectable(true);
        body.setPadding(app.dp(14), app.dp(8), app.dp(14), app.dp(8));
        android.widget.HorizontalScrollView wide = new android.widget.HorizontalScrollView(app);
        wide.addView(body);
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(wide);
        pkTestReport = new android.app.AlertDialog.Builder(app).setTitle(t.expect != null ? "SogniMusic test" : "Midi2Wav test").setView(scroll).setPositiveButton("Close", null).show();
    }

    /** A saved sheet: kept in the DB's Prompt files, or written to disk (Download) when that failed; its log lines. */
    String pkWriteSheet(PromptSave.Saved saved, String pending) {
        String lines = saved.note;
        if (saved.bytes != null) {
            String at = ArtJava.publish(app, saved.fileName, saved.bytes);
            lines = lines + (at == null ? "\nCould not save " + saved.fileName : "\nsaved " + at);
        }
        // File > Downloads: the run's sheet and its prompt library version, for Delete.
        Long download = pending == null ? null : DOWNLOAD_OF.get(pending);
        if (download != null) {
            pulsekit.SogniDownloads.saved(download.longValue(), lines, ArtJava.SAVED);
            pulsekit.SogniDownloads.stored(download.longValue(), saved.versionId);
        }
        return lines;
    }

    /** The Sogni run (File > Downloads) of each pending entry (PendingOps) still open. */
    static final java.util.Map<String, Long> DOWNLOAD_OF = new java.util.concurrent.ConcurrentHashMap<String, Long>();

    /** The run's database operations are over (PendingOps), and its downloads finished or failed. */
    void pkDone(String pending) {
        pulsekit.PendingOps.done(pending);
        Long download = pending == null ? null : DOWNLOAD_OF.remove(pending);
        if (download != null) pulsekit.SogniDownloads.done(download.longValue());
    }

    /**
     * The run's changed prompt sheets, one by one: where each goes (Save prompt), then stored in the
     * background and written to disk; the run's database operations are over after the last.
     */
    void pkAskSheets(final java.util.List<PromptSave.Pending> sheets, final int i, final pulsekit.JavaRun.Result result,
            final java.util.List<String> argv, final String pending) {
        if (i >= sheets.size()) {
            ASKING.updateAndGet(n -> n > 0 ? n - 1 : 0);
            this.pkDone(pending);
            return;
        }
        final PromptSave.Pending p = sheets.get(i);
        final java.io.File filesDir = app.getFilesDir();
        PromptVault vault = null;
        try {
            vault = PromptVault.open(filesDir);
        } catch (Exception ignored) {
            // the dialog offers no categories then
        }
        SavePromptDialog.show(app, vault, p.sheet, choice -> {
            if (choice == null) {
                this.pkAddLogLine(PromptSave.cancelledNote(p.sheet));
                this.pkAskSheets(sheets, i + 1, result, argv, pending);
                return;
            }
            KEEPING.incrementAndGet();
            new Thread(() -> {
                String line;
                try {
                    line = this.pkWriteSheet(PromptSave.store(filesDir, p, result, argv, choice), pending);
                } catch (Throwable ex) {
                    line = "Prompt library: could not store " + p.file.name;
                }
                final String note = line;
                app.runOnUiThread(() -> {
                    KEEPING.decrementAndGet();
                    for (String l : note.split("\n")) this.pkAddLogLine(l);
                    this.pkAskSheets(sheets, i + 1, result, argv, pending);
                });
            }, "pulsekit-prompt-save").start();
        });
    }

    /**
     * One offer after a run (`when` it applies): a problem in it is added to the run's log as a
     * note, with the error's name, instead of failing the run.
     */
    void pkOffer(String what, boolean when, Runnable offer) {
        if (!when) return;
        try {
            offer.run();
        } catch (Throwable ex) {
            String m = ex.getMessage();
            String note = "Note: could not show " + what + " (" + ex.getClass().getSimpleName() + (m == null || m.trim().length() == 0 ? "" : ": " + m) + ")";
            if (this.pkPyLog != null) {
                boolean ours = this.pkLogIsRun();
                String now = this.pkPyLog.getText().toString() + "\n" + note;
                this.pkPyLog.setText(now);
                if (ours) this.pkRunLog = now;
            }
            app.setNow(note);
        }
    }

    /** True while a run's result is being stored in the prompt library (in the background). */
    volatile boolean pkKeeping;
    /** Runs whose results are still being stored, all PyJavs together (the tests wait for 0). */
    static final java.util.concurrent.atomic.AtomicInteger KEEPING = new java.util.concurrent.atomic.AtomicInteger();
    /** Runs started and not yet over, and runs whose prompt sheets are being asked about (Save prompt). */
    static final java.util.concurrent.atomic.AtomicInteger RUNS = new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger ASKING = new java.util.concurrent.atomic.AtomicInteger();

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
            // A playlist: its text in the code area; Run plays it with PlayList.java.
            boolean pls = low.endsWith(".pls");
            boolean prompt = low.endsWith(".prompt") || low.endsWith(".prompt.txt") || low.contains(".prompt");
            if (!prompt && data != null && data.length >= 9) {
                String head = new String(data, 0, Math.min(data.length, 12), java.nio.charset.StandardCharsets.UTF_8);
                if (head.startsWith("PKPROMPT1")) {
                    prompt = true;
                    if (!low.endsWith(".prompt")) name = (low.endsWith(".txt") ? name.substring(0, name.length() - 4) : name) + ".prompt";
                }
            }
            if (!py && !prompt && !jar && !cls && !javaSrc && !pls) {
                String why = "Pick a .py, .java, .class, .jar, .prompt, or .pls file.";
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
                : pls ? PLS_HINT
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
        String hint = this.pkPyHint != null ? this.pkHintFull : "";
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
