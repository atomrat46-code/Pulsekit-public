package pulsekit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Document ready (TextReady): after a SogniChat run, the text it wrote, cut at a delimiter (Cut after,
 * Cut before or No cut; remembered for the program), with Copy and Send as prompt (into a Sogni
 * program's Params, asked first) and Close.
 */
final class TextOffer {
    /** The dialog shown last, for the tests. */
    static AlertDialog last;
    /** The tests: Send's program (else a list) and answer (else asked); what was said last. */
    static String sendTo;
    static Boolean sendYes;
    static volatile String said = "";

    private TextOffer() {}

    static boolean offer(final MainActivity app, String program, JavaRun.Result result) {
        return offer(app, program, result, null);
    }

    /** As above; `outputDir` (--output_dir): the answer is saved there when Document ready closes (as shown, %g filled in). */
    static boolean offer(final MainActivity app, String program, JavaRun.Result result, String outputDir) {
        return offer(app, program, result, outputDir, null);
    }

    /** As above; `preset` (Send to program and group): a button sends the text there with one click. */
    static boolean offer(final MainActivity app, String program, JavaRun.Result result, String outputDir, String[] preset) {
        return offer(app, program, result, outputDir, preset, null);
    }

    /** As above; `autoTarget` (Auto render): the text as shown rendered by that program once Document ready closes. */
    static boolean offer(final MainActivity app, String program, JavaRun.Result result, String outputDir, String[] preset, String autoTarget) {
        if (result == null || !TextReady.wanted(program)) return false;
        JavaRun.FileOut f = TextReady.textOf(result.files);
        if (f == null) return false;
        show(app, program, f.name, TextReady.text(f.bytes), outputDir, preset, autoTarget);
        return true;
    }

    /** The answer saved into the output folder (`given`, its codes filled in, %g the group sent to) under its chat's name. */
    static String saveInto(MainActivity app, String given, String fileName, String text, String group) {
        String sub = ArtJava.subOf(TextReady.outDir(given, group));
        String got = ArtJava.publish(app, ChatFolder.cutName(fileName, true, group), text.getBytes(java.nio.charset.StandardCharsets.UTF_8), sub);
        return got == null ? "Could not save the answer into " + given : "Answer saved: " + got;
    }

    /** The tests: Send to group's program and group (else lists); the group it used last (%g). */
    static String groupProgram, groupName;
    static volatile String sentGroup;

    /**
     * SogniChat --input_dir / --output_dir: each answer it wrote cut at the delimiter Document ready
     * used last for it, saved into --output_dir (under Download, or a picked folder; % codes filled in)
     * with the chat's name, else beside the chats as <name>-trimmed.txt; `log` gets a line saying so.
     */
    static int saveAnswers(MainActivity app, String program, java.util.List<String> argv, JavaRun.Result result, java.util.function.Consumer<String> log) {
        java.util.List<JavaRun.FileOut> answers = ChatFolder.answers(result == null ? null : result.files);
        if (answers.isEmpty()) return 0;
        String[] set = TextReady.setting(app.getFilesDir(), program);
        String given = ChatFolder.arg(argv, "--output_dir");
        // Send to program and group: each answer also a section there; the group is %g.
        String[] preset = TextReady.preset(argv);
        try {
            preset = TextReady.resolved(PromptVault.open(app.getFilesDir()), preset);
        } catch (Exception ignored) {
            // as given
        }
        String group = preset == null ? null : preset[1];
        String sub = given == null ? null : ArtJava.subOf(TextReady.outDir(given, group));
        int n = 0, sent = 0;
        String where = null;
        for (JavaRun.FileOut f : answers) {
            String text = TextReady.result(TextReady.text(f.bytes), set);
            if (preset != null) {
                try {
                    if (TextReady.sendToGroup(PromptVault.open(app.getFilesDir()), preset[0], f.name, text, preset[1]).startsWith("Sent")) sent++;
                } catch (Exception ignored) {
                    // not sent; the answer is saved all the same
                }
            }
            String got = ArtJava.publish(app, ChatFolder.cutName(f.name, given != null, group), text.getBytes(java.nio.charset.StandardCharsets.UTF_8), sub);
            if (got == null) continue;
            n++;
            where = got.contains("/") ? got.substring(0, got.lastIndexOf('/')) : "Download";
        }
        String line = (n == 0 ? "Could not save the answers" + (given == null ? "" : " into " + given) : ChatFolder.saved(n, where, set)) + ChatFolder.sentNote(sent, preset);
        said = line;
        if (log != null) log.accept(line);
        return n;
    }

    static void show(final MainActivity app, final String program, String name, final String whole) {
        show(app, program, name, whole, null);
    }

    static void show(final MainActivity app, final String program, String name, final String whole, final String outputDir) {
        show(app, program, name, whole, outputDir, null);
    }

    static void show(final MainActivity app, final String program, String name, final String whole, final String outputDir, final String[] preset) {
        show(app, program, name, whole, outputDir, preset, null);
    }

    static void show(final MainActivity app, final String program, String name, final String whole, final String outputDir, String[] given, final String autoTarget) {
        // The preset group's % codes filled in once (Uudet%n: the next number not used yet).
        String[] at = given;
        try {
            at = TextReady.resolved(PromptVault.open(app.getFilesDir()), given);
        } catch (Exception ignored) {
            // as given
        }
        final String[] preset = at;
        final java.io.File dir = app.getFilesDir();
        // The group Send to group used (%g in the saved answer's names).
        final String[] group = {null};
        String[] set = TextReady.setting(dir, program);
        LinearLayout col = new LinearLayout(app);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        col.addView(app.text(name, 14, true));
        LinearLayout row = new LinearLayout(app);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final Spinner mode = new Spinner(app);
        mode.setTag("text-ready:mode");
        android.widget.ArrayAdapter<String> modes = new android.widget.ArrayAdapter<String>(app, android.R.layout.simple_spinner_dropdown_item, TextReady.MODES);
        mode.setAdapter(modes);
        mode.setSelection(Math.max(0, java.util.Arrays.asList(TextReady.MODES).indexOf(set[0])));
        row.addView(mode, new LinearLayout.LayoutParams(-2, -2));
        final EditText delim = new EditText(app);
        delim.setTag("text-ready:delimiter");
        delim.setSingleLine(true);
        delim.setHint(TextReady.DELIMITER);
        delim.setText(set[1]);
        row.addView(delim, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(row);
        final TextView note = app.text("", 12, false);
        note.setTag("text-ready:note");
        col.addView(note);
        // Select file and Browse sections: their text goes after the answer (remembered: next time too); Clear takes it away.
        LinearLayout arow = new LinearLayout(app);
        arow.setOrientation(LinearLayout.HORIZONTAL);
        arow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        // Add: checked, the text after is added to the answer; unchecked, it is kept aside (remembered).
        final android.widget.CheckBox addAfter = new android.widget.CheckBox(app);
        addAfter.setTag("text-ready:add-after");
        addAfter.setText(TextReady.ADD_AFTER);
        addAfter.setChecked(!TextReady.OFF.equals(set[4]));
        // Light on the dark dialog (the theme's own colours can be dark there), its own room kept.
        addAfter.setTextColor(0xFFEDEDE8);
        addAfter.setTextSize(15);
        addAfter.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFB9C9B4));
        addAfter.setMinWidth(app.dp(72));
        addAfter.setPadding(0, 0, app.dp(4), 0);
        arow.addView(addAfter, new LinearLayout.LayoutParams(-2, -2, 0f));
        final android.widget.Button selectFile = action(app, arow, TextReady.SELECT_FILE, "text-ready:select-file");
        final android.widget.Button browse = action(app, arow, TextReady.BROWSE_SECTIONS, "text-ready:browse-sections");
        final android.widget.Button clear = action(app, arow, TextReady.CLEAR_TEXT, "text-ready:clear");
        // (The buttons share what is left: no minimum width pushing the checkbox out.)
        for (android.widget.Button b : new android.widget.Button[] {selectFile, browse, clear}) {
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setPadding(app.dp(4), b.getPaddingTop(), app.dp(4), b.getPaddingBottom());
        }
        col.addView(arow);
        final EditText after = new EditText(app);
        after.setTag("text-ready:after");
        after.setHint("Added after the answer (Select file, Browse sections)");
        after.setText(set[2]);
        // Up to 4 lines, scrolled inside when longer.
        typed(after, 4);
        col.addView(after, new LinearLayout.LayoutParams(-1, -2));
        // Copy, Save and Send as prompt (Close is the dialog's button).
        LinearLayout acts = new LinearLayout(app);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.Button copy = action(app, acts, TextReady.COPY, "text-ready:copy");
        final android.widget.Button save = action(app, acts, TextReady.SAVE, "text-ready:save");
        final android.widget.Button send = action(app, acts, TextReady.SEND, "text-ready:send");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = app.dp(6);
        col.addView(acts, ap);
        // Send to group: the text as a section in a Sogni program's group (that group is %g in the saved answer's names).
        LinearLayout acts2 = new LinearLayout(app);
        acts2.setOrientation(LinearLayout.HORIZONTAL);
        final android.widget.Button toGroup = action(app, acts2, TextReady.SEND_GROUP, "text-ready:send-group");
        // Send to program and group set in Params: there with one click.
        final android.widget.Button toPreset = preset == null ? null : action(app, acts2, TextReady.presetLabel(preset), "text-ready:send-preset");
        col.addView(acts2);
        // The actions under one File menu (the buttons above kept, hidden, as the menu's doers).
        acts.setVisibility(android.view.View.GONE);
        acts2.setVisibility(android.view.View.GONE);
        final java.util.List<android.widget.Button> doers = new java.util.ArrayList<android.widget.Button>(java.util.Arrays.asList(copy, save, send, toGroup));
        if (toPreset != null) doers.add(toPreset);
        // A menu bar's File: plain text at the top, no button.
        final TextView file = app.text(TextReady.FILE_MENU, 16, true);
        file.setTag("text-ready:file");
        file.setPadding(app.dp(4), app.dp(6), app.dp(16), app.dp(6));
        file.setClickable(true);
        android.util.TypedValue tv = new android.util.TypedValue();
        if (app.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) file.setBackgroundResource(tv.resourceId);
        file.setOnClickListener(v -> {
            android.widget.PopupMenu m = new android.widget.PopupMenu(app, file);
            for (int i = 0; i < doers.size(); i++) m.getMenu().add(0, i, i, doers.get(i).getText());
            m.setOnMenuItemClickListener(it -> {
                doers.get(it.getItemId()).performClick();
                return true;
            });
            lastMenu = m;
            m.show();
        });
        col.addView(file, 0, new LinearLayout.LayoutParams(-2, -2));
        // The text as it will be copied, saved or sent: editable (a new cut, delimiter or text after makes it again).
        final EditText body = new EditText(app);
        body.setTextSize(15);
        body.setTag("text-ready:text");
        body.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        body.setMinLines(3);
        // Editable: a cursor and the keyboard; scrolled inside when longer than 14 lines.
        typed(body, 14);
        // Right under the line above (an EditText's own top padding is large).
        body.setPadding(body.getPaddingLeft(), app.dp(2), body.getPaddingRight(), body.getPaddingBottom());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = 0;
        col.addView(body, bp);
        TextView hint = app.text(TextReady.HINT, 12, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = app.dp(10);
        col.addView(hint, hp);
        final Runnable cut = () -> {
            String m = TextReady.MODES[Math.max(0, mode.getSelectedItemPosition())];
            String d = delim.getText().toString();
            String[] now = {m, d, after.getText().toString(), set[3], addAfter.isChecked() ? TextReady.ON : TextReady.OFF};
            body.setText(TextReady.result(whole, now));
            note.setText(TextReady.note(whole, d, m));
            note.setVisibility(TextReady.noteShown(whole, d, m) ? android.view.View.VISIBLE : android.view.View.GONE);
            TextReady.remember(dir, program, now[0], now[1], now[2], now[3], now[4]);
        };
        after.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            public void afterTextChanged(android.text.Editable s) {
                cut.run();
            }
        });
        clear.setOnClickListener(v -> after.setText(""));
        addAfter.setOnCheckedChangeListener((b, on) -> cut.run());
        selectFile.setOnClickListener(v -> PyJavParams.pickPromptFile(app, after));
        browse.setOnClickListener(v -> pickSection(app, after));
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                cut.run();
            }

            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        delim.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            public void afterTextChanged(android.text.Editable s) {
                cut.run();
            }
        });
        cut.run();
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        scroll.addView(col);
        final AlertDialog dialog = new AlertDialog.Builder(app)
            .setTitle(TextReady.TITLE)
            .setView(scroll)
            .setPositiveButton(TextReady.CLOSE, null)
            .create();
        copy.setOnClickListener(v -> {
            String t = body.getText().toString();
            android.content.ClipboardManager clip = (android.content.ClipboardManager) app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (t.trim().length() > 0) clip.setPrimaryClip(android.content.ClipData.newPlainText("text", t));
            say(app, TextReady.copied(t));
        });
        final String fileName = name;
        save.setOnClickListener(v -> save(app, TextReady.savedName(fileName), body.getText().toString()));
        send.setOnClickListener(v -> send(app, body.getText().toString()));
        toGroup.setOnClickListener(v -> sendToGroup(app, fileName, body.getText().toString(), g -> group[0] = g));
        if (toPreset != null) toPreset.setOnClickListener(v -> {
            try {
                String r = TextReady.sendToGroup(PromptVault.open(app.getFilesDir()), preset[0], fileName, body.getText().toString(), preset[1]);
                if (r.startsWith("Sent")) {
                    group[0] = preset[1];
                    sentGroup = preset[1];
                }
                say(app, r);
            } catch (Exception ex) {
                say(app, "Could not send the text: " + ex.getMessage());
            }
        });
        // --output_dir: the answer as shown saved there when Document ready closes (%g: the group sent to).
        dialog.setOnDismissListener(d -> {
            if (outputDir != null) say(app, saveInto(app, outputDir, fileName, body.getText().toString(), group[0]));
            // Auto render: the text as shown rendered by the Send to program.
            if (autoTarget != null && app.pyJav != null) {
                java.util.List<String[]> one = new java.util.ArrayList<String[]>();
                one.add(new String[] {fileName, body.getText().toString()});
                app.pyJav.pkStartAutoRender(autoTarget, one);
            }
        });
        dialog.show();
        // Its text boxes take the keyboard (a dialog's window can be set not to).
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        last = dialog;
    }

    /**
     * A text box typed in: several lines with a cursor and the keyboard, at most `lines` tall, its text
     * scrolled inside it when longer (the dialog's own scrolling left alone while a finger moves in it).
     */
    static void typed(final EditText box, int lines) {
        box.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setSingleLine(false);
        box.setHorizontallyScrolling(false);
        box.setFocusable(true);
        box.setFocusableInTouchMode(true);
        box.setCursorVisible(true);
        box.setMaxLines(lines);
        box.setVerticalScrollBarEnabled(true);
        box.setScrollBarStyle(android.view.View.SCROLLBARS_INSIDE_INSET);
        box.setOnTouchListener((v, e) -> {
            if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                v.getParent().requestDisallowInterceptTouchEvent(e.getActionMasked() != android.view.MotionEvent.ACTION_UP
                    && e.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL);
            }
            return false;
        });
    }

    /** Send to group: a Sogni program, then one of its groups ("(no group)" first); the text added there as a section. */
    static void sendToGroup(final MainActivity app, final String fileName, final String text, final java.util.function.Consumer<String> used) {
        if (text.trim().isEmpty()) {
            say(app, "No text to send");
            return;
        }
        final java.util.List<String> apps = new java.util.ArrayList<String>();
        for (String s : TextReady.targets(p -> PyJavParams.programTextOf(app, p))) apps.add(SogniSections.app(s));
        if (apps.isEmpty()) {
            say(app, "No Sogni program takes a prompt");
            return;
        }
        final java.util.function.Consumer<String> toApp = a -> {
            final java.util.function.Consumer<String> toGroup = g -> {
                try {
                    String r = TextReady.sendToGroup(PromptVault.open(app.getFilesDir()), a, fileName, text, g);
                    if (r.startsWith("Sent")) {
                        sentGroup = g;
                        used.accept(g);
                    }
                    say(app, r);
                } catch (Exception ex) {
                    say(app, "Could not send the text: " + ex.getMessage());
                }
            };
            if (groupName != null) {
                toGroup.accept(groupName);
                return;
            }
            try {
                final java.util.List<String> picks = SectionCollection.groupPicks(PromptVault.open(app.getFilesDir()), a);
                new AlertDialog.Builder(app).setTitle(a + ": " + SectionCollection.GROUP).setItems(picks.toArray(new String[0]), (d, w) -> toGroup.accept(picks.get(w)))
                    .setNegativeButton("Cancel", null).show();
            } catch (Exception ex) {
                say(app, "Could not read the groups: " + ex.getMessage());
            }
        };
        if (groupProgram != null) {
            toApp.accept(groupProgram);
            return;
        }
        new AlertDialog.Builder(app).setTitle(TextReady.SEND_GROUP_PROGRAM).setItems(apps.toArray(new String[0]), (d, w) -> toApp.accept(apps.get(w)))
            .setNegativeButton("Cancel", null).show();
    }

    /** The tests: From sections' pick (its index; else a list is shown), and the list shown last. */
    static Integer sectionPick;
    static AlertDialog lastSections;

    /** From sections: the Sogni programs' saved sections listed; the one picked puts its prompt in `after`. */
    static void pickSection(MainActivity app, final EditText after) {
        final java.util.List<String> texts = new java.util.ArrayList<String>();
        java.util.List<String> labels;
        try {
            labels = TextReady.sectionChoices(PromptVault.open(app.getFilesDir()), texts);
        } catch (Exception ex) {
            say(app, "Could not read the sections: " + ex.getMessage());
            return;
        }
        if (labels.isEmpty()) {
            say(app, "No sections saved yet (Sogni Params' Save)");
            return;
        }
        if (sectionPick != null) {
            if (sectionPick.intValue() >= 0 && sectionPick.intValue() < texts.size()) after.setText(texts.get(sectionPick.intValue()));
            return;
        }
        lastSections = new AlertDialog.Builder(app).setTitle(TextReady.FROM_SECTIONS)
            .setItems(labels.toArray(new String[0]), (d, w) -> after.setText(texts.get(w)))
            .setNegativeButton("Cancel", null).show();
    }

    /** The tests: the File menu shown last. */
    static android.widget.PopupMenu lastMenu;

    static android.widget.Button action(MainActivity app, LinearLayout row, String label, String tag) {
        android.widget.Button b = new android.widget.Button(app, null, android.R.attr.buttonStyleSmall);
        b.setText(label);
        b.setAllCaps(false);
        b.setTag(tag);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        return b;
    }

    /** The tests: Save's file (else the system's save dialog). */
    static java.io.File saveTo;

    /** Save: the text as shown (trimmed) into a file picked, its name suggested (answer-trimmed.txt). */
    static void save(MainActivity app, String name, String text) {
        if (text.trim().isEmpty()) {
            say(app, "No text to save");
            return;
        }
        if (saveTo != null) {
            try {
                java.nio.file.Files.write(saveTo.toPath(), text.getBytes("UTF-8"));
                say(app, "Saved the text into " + saveTo.getName());
            } catch (Exception ex) {
                say(app, "Could not save the text: " + ex.getMessage());
            }
            return;
        }
        SaveText.save(app, name, text);
    }

    /**
     * Send as prompt: the text into a Sogni program's Params as its prompt (the program picked, then asked);
     * then Document ready closes and that program opens with its Params.
     */
    static void send(final MainActivity app, final String text) {
        if (text.trim().isEmpty()) {
            say(app, "No text to send");
            return;
        }
        final java.util.List<String> targets = TextReady.targets(s -> PyJavParams.programTextOf(app, s));
        if (targets.isEmpty()) {
            say(app, "No Sogni program takes a prompt");
            return;
        }
        final java.util.function.Consumer<String> go = target -> {
            final Runnable put = () -> {
                java.util.List<ProgramParams.Param> theirs = ProgramParams.parse(PyJavParams.programTextOf(app, target));
                PyJavParams.store(app, target, ParamsClip.put(theirs, PyJavParams.load(app, target), "--prompt", text));
                say(app, ParamsClip.sent("--prompt", target));
                if (last != null) last.dismiss();
                app.openKitView("py");
                app.programMenus.selectProgram("Java", target);
                if (target.equals(app.pyName)) app.pyJav.pkOpenParams();
            };
            if (sendYes != null) {
                if (sendYes.booleanValue()) put.run();
                return;
            }
            new AlertDialog.Builder(app).setTitle(TextReady.SEND_TITLE + " " + SogniSections.app(target)).setMessage(ParamsClip.sendAsk("--prompt", target, text))
                .setPositiveButton("Send", (d, w) -> put.run()).setNegativeButton("Cancel", null).show();
        };
        if (sendTo != null) {
            if (targets.contains(sendTo)) go.accept(sendTo);
            else say(app, sendTo + " takes no prompt");
            return;
        }
        String[] rows = new String[targets.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = SogniSections.app(targets.get(i));
        new AlertDialog.Builder(app).setTitle(TextReady.SEND_TITLE).setItems(rows, (d, w) -> go.accept(targets.get(w)))
            .setNegativeButton("Cancel", null).show();
    }

    static void say(MainActivity app, String text) {
        said = text;
        app.setNow(text);
    }
}
