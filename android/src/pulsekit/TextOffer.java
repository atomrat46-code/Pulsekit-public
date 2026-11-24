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
        if (result == null || !TextReady.wanted(program)) return false;
        JavaRun.FileOut f = TextReady.textOf(result.files);
        if (f == null) return false;
        show(app, program, f.name, TextReady.text(f.bytes));
        return true;
    }

    static void show(final MainActivity app, final String program, String name, final String whole) {
        final java.io.File dir = app.getFilesDir();
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
        final TextView body = app.text("", 15, false);
        body.setTag("text-ready:text");
        body.setTextIsSelectable(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = app.dp(8);
        col.addView(body, bp);
        TextView hint = app.text(TextReady.HINT, 12, false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = app.dp(10);
        col.addView(hint, hp);
        final Runnable cut = () -> {
            String m = TextReady.MODES[Math.max(0, mode.getSelectedItemPosition())];
            String d = delim.getText().toString();
            body.setText(TextReady.cut(whole, d, m));
            note.setText(TextReady.note(whole, d, m));
            TextReady.remember(dir, program, m, d);
        };
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
            .setNeutralButton(TextReady.COPY, null)
            .setNegativeButton(TextReady.SEND, null)
            .setPositiveButton(TextReady.CLOSE, null)
            .create();
        dialog.setOnShowListener(d -> {
            android.widget.Button copy = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            copy.setTag("text-ready:copy");
            copy.setOnClickListener(v -> {
                String t = body.getText().toString();
                android.content.ClipboardManager clip = (android.content.ClipboardManager) app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (t.trim().length() > 0) clip.setPrimaryClip(android.content.ClipData.newPlainText("text", t));
                say(app, TextReady.copied(t));
            });
            android.widget.Button send = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            send.setTag("text-ready:send");
            send.setOnClickListener(v -> send(app, body.getText().toString()));
        });
        dialog.show();
        last = dialog;
    }

    /** Send as prompt: the text into a Sogni program's Params as its prompt (the program picked, then asked). */
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
