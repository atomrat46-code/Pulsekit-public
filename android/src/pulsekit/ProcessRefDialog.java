package pulsekit;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.List;

import static pulsekit.MainActivity.*;

/**
 * Picture ready > Process as a reference: which program (one run recently that takes pictures),
 * which of its recent prompts, and Reference file 1 or 2; Open loads it in PyJav with the picture
 * in that slot, ready to Run. The choices are kept and picked again next time (ProcessAsRef).
 */
final class ProcessRefDialog {
    final MainActivity app;
    final File picture;
    final List<PyJavRecent.Item> recent;
    final List<String> programs;
    final ProcessAsRef.Choice choice;
    String program;
    List<PyJavRecent.Item> runs;
    int run;
    int slot;
    TextView programBtn;
    TextView promptBtn;
    TextView ref1;
    TextView ref2;
    AlertDialog dialog;
    /** The dialog shown last (for the tests). */
    static ProcessRefDialog last;
    /** Opened from the Media browser (C on a video, or a file's menu): it and its player close on Open, and Return opens it again. */
    MediaBrowser from;
    android.app.Dialog player;

    @SuppressWarnings("unchecked")
    private ProcessRefDialog(MainActivity app, File picture) {
        this.app = app;
        this.picture = picture;
        this.recent = PyJavRecent.load(app.getFilesDir());
        // File > Downloads' runs: their prompts are listed too.
        SogniDownloads.init(app.getFilesDir());
        this.programs = ProcessAsRef.programs(this.recent, (name, items) -> this.source(name));
        this.choice = ProcessAsRef.load(app.getFilesDir());
    }

    String source(String name) {
        String bundled = this.app.programMenus.bundledSource(name);
        return bundled != null ? bundled : ProcessAsRef.keptSource(name, this.recent);
    }

    /** Picture ready's button: the picture (written to a file when it is not one yet). */
    static void open(MainActivity app, String name, byte[] bytes) {
        open(app, name, bytes, null, null);
    }

    /** As above, from the Media browser `from` (and its video `player`, or null). */
    static void open(MainActivity app, String name, byte[] bytes, MediaBrowser from, android.app.Dialog player) {
        File dir = new File(app.getFilesDir(), "process-ref");
        dir.mkdirs();
        File f = new File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        try {
            java.nio.file.Files.write(f.toPath(), bytes);
        } catch (Exception ex) {
            app.setNow("Could not keep " + name + " for the next program");
            return;
        }
        open(app, f, from, player);
    }

    static void open(MainActivity app, File picture) {
        open(app, picture, null, null);
    }

    static void open(MainActivity app, File picture, MediaBrowser from, android.app.Dialog player) {
        ProcessRefDialog d = new ProcessRefDialog(app, picture);
        d.from = from;
        d.player = player;
        if (d.programs.isEmpty()) {
            new AlertDialog.Builder(app).setTitle("Process as a reference")
                .setMessage("No program that takes pictures has been run yet. Run SogniPedit or SogniVideo once (PyJav), and it is listed here.")
                .setPositiveButton("OK", null).show();
            return;
        }
        last = d;
        d.show();
    }

    void show() {
        LinearLayout col = this.app.col();
        col.setPadding(this.app.dp(16), this.app.dp(8), this.app.dp(16), this.app.dp(4));
        TextView pic = this.app.text(this.picture.getName(), 13, false);
        pic.setTextColor(MUTED);
        col.addView(pic);
        col.addView(this.label("Program"));
        this.programBtn = this.app.outline("", false, v -> this.pickProgram());
        this.programBtn.setTag("process-ref-program");
        col.addView(this.programBtn);
        col.addView(this.label("Prompt (its recent runs)"));
        this.promptBtn = this.app.outline("", false, v -> this.pickPrompt());
        this.promptBtn.setTag("process-ref-prompt");
        col.addView(this.promptBtn);
        col.addView(this.label("The picture is"));
        LinearLayout slots = this.app.row();
        this.ref1 = this.app.pill("Reference file 1", false, v -> this.setSlot(1));
        this.ref1.setTag("process-ref-1");
        this.ref2 = this.app.pill("Reference file 2", false, v -> this.setSlot(2));
        this.ref2.setTag("process-ref-2");
        slots.addView(this.ref1);
        slots.addView(this.ref2);
        col.addView(slots);
        this.setProgram(this.programs.get(ProcessAsRef.programIndex(this.programs, this.choice)), true);
        this.dialog = new AlertDialog.Builder(this.app).setTitle("Process as a reference").setView(col)
            .setPositiveButton("Open", (d, w) -> this.go())
            .setNegativeButton("Cancel", null).show();
    }

    TextView label(String s) {
        TextView t = this.app.text(s, 12, true);
        t.setTextColor(MUTED);
        t.setPadding(0, this.app.dp(10), 0, this.app.dp(2));
        return t;
    }

    void setProgram(String name, boolean kept) {
        this.program = name;
        // Its prompts, the newest first (the one used last is the first: picked at once).
        this.runs = ProcessAsRef.promptRuns(this.recent, name);
        this.run = 0;
        this.programBtn.setText(name);
        this.showPrompt();
        List<String> s = ProcessAsRef.slots(this.source(name));
        this.ref1.setText("Reference file 1 (" + s.get(0) + ")");
        this.ref2.setVisibility(s.size() > 1 ? 0 : 8);
        if (s.size() > 1) this.ref2.setText("Reference file 2 (" + s.get(1) + ")");
        this.setSlot(kept && name.equals(this.choice.program) ? this.choice.slot : 1);
    }

    void showPrompt() {
        this.promptBtn.setText(this.runs.isEmpty() ? "(no runs)" : ProcessAsRef.promptShort(this.runs.get(this.run), 120));
    }

    void setSlot(int n) {
        List<String> s = ProcessAsRef.slots(this.source(this.program));
        this.slot = n == 2 && s.size() > 1 ? 2 : 1;
        this.app.paintChip(this.ref1, this.slot == 1);
        this.app.paintChip(this.ref2, this.slot == 2);
    }

    void pickProgram() {
        new AlertDialog.Builder(this.app).setTitle("Program")
            .setItems(this.programs.toArray(new CharSequence[0]), (d, w) -> this.setProgram(this.programs.get(w), false)).setNegativeButton("Cancel", null).show();
    }

    /** The prompts list and a prompt's whole text, shown last (for the tests). */
    static AlertDialog lastPrompts, lastPromptText;

    /** The prompts, each by its first sentences; a tap shows the whole prompt, Use picks it. */
    void pickPrompt() {
        if (this.runs.isEmpty()) return;
        CharSequence[] labels = new CharSequence[this.runs.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = ProcessAsRef.promptShort(this.runs.get(i), 160);
        lastPrompts = new AlertDialog.Builder(this.app).setTitle("Prompt (" + this.runs.size() + ")")
            .setItems(labels, (d, w) -> this.showWhole(w)).setNegativeButton("Cancel", null).show();
    }

    /** A prompt's whole text: Use picks it, Back goes to the list. */
    void showWhole(final int w) {
        String whole = ProcessAsRef.prompt(this.runs.get(w));
        TextView text = this.app.text(whole == null ? ProcessAsRef.promptLabel(this.runs.get(w), 2000) : whole, 14, false);
        text.setTextIsSelectable(true);
        text.setTag("process-ref-prompt-text");
        text.setPadding(this.app.dp(20), this.app.dp(8), this.app.dp(20), 0);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this.app);
        scroll.addView(text);
        lastPromptText = new AlertDialog.Builder(this.app).setTitle("Prompt " + (w + 1) + " of " + this.runs.size()).setView(scroll)
            .setPositiveButton("Use", (d, x) -> {
                this.run = w;
                this.showPrompt();
            })
            .setNegativeButton("Back", (d, x) -> this.pickPrompt()).show();
    }

    /** Open: PyJav with the program, the run's settings and the picture in its slot. */
    void go() {
        PyJavRecent.Item item = this.runs.isEmpty() ? null : this.runs.get(this.run);
        List<String> s = ProcessAsRef.slots(this.source(this.program));
        String token = s.get(Math.min(this.slot, s.size()) - 1);
        // The run's on/off switches the saved Params no longer have stay off (--saveprompt unticked since).
        String runArgs = ProcessAsRef.withoutOffSwitches(item == null ? "" : item.extra, PyJavParams.load(this.app, this.program), ProgramParams.parse(this.source(this.program)));
        String extra = ProcessAsRef.withPicture(runArgs, token, this.picture.getAbsolutePath());
        this.choice.program = this.program;
        this.choice.extra = item == null ? "" : item.extra;
        this.choice.slot = this.slot;
        ProcessAsRef.save(this.app.getFilesDir(), this.choice);
        // Among the recent reference pictures (Params' Recent).
        RecentRefs.add(this.app.getFilesDir(), this.picture);
        // The program's saved Params win over the run's arguments: the picture goes in there too.
        String saved = ProcessAsRef.savedWithPicture(PyJavParams.load(this.app, this.program), token, this.picture.getAbsolutePath());
        if (saved != null) PyJavParams.store(this.app, this.program, saved);
        // The slot's row left out of the run (its Use box unticked): ticked again, so this picture is used there.
        String off = ProcessAsRef.offWithout(PyJavParams.load(this.app, this.program + ProgramParams.OFF_KEY), token);
        if (off != null) PyJavParams.store(this.app, this.program + ProgramParams.OFF_KEY, off);
        PyJav pj = this.app.pyJav;
        pj.pkPyRecentItems = this.recent;
        int index = item == null ? -1 : this.recent.indexOf(item);
        // A prompt from File > Downloads (not in PyJav's recent list): the program loaded, its arguments set below.
        if (index < 0) index = this.recent.indexOf(PyJavRecent.tapped(this.recent, this.program));
        // From the Media browser: it (and its video) closes, so the program and its Params are on top.
        Runnable back = this.from == null ? null : this.from.returner();
        if (this.player != null && this.player.isShowing()) this.player.dismiss();
        if (this.from != null && this.from.dialog != null) this.from.dialog.dismiss();
        this.app.show("py");
        if (index >= 0) pj.pkApplyRecent(index + 1);
        if (pj.pkPyArgs != null) pj.pkPyArgs.setText(extra);
        if (back != null) pj.pkSetReturn(back);
        this.app.setNow(this.program + " · " + this.picture.getName() + " as Reference file " + this.slot + ": Run starts it");
        // Its Params, with the picture in its slot.
        pj.pkOpenParams();
    }
}
