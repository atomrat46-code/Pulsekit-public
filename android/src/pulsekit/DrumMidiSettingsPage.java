package pulsekit;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static pulsekit.MainActivity.*;

/** File > Drum Midi Settings: how a MIDI drum track becomes a file set on import. */
final class DrumMidiSettingsPage {
    static final String PREFS = "pulsekit-drum-midi";
    static final String KEY = "settings";
    static final int PICK_KEY = 33;
    static final int PICK_FOLDER = 34;

    final MainActivity app;
    TextView hitsLabel;
    TextView keyStatus;
    TextView folderStatus;

    DrumMidiSettingsPage(MainActivity app) {
        this.app = app;
    }

    /** Loads the stored settings, builds the page, and adds it on top of the other pages (hidden). */
    void wire() {
        try {
            MidiImportSettings.decode(app.getSharedPreferences(PREFS, 0).getString(KEY, null));
        } catch (Throwable ignored) {
            MidiImportSettings.reset();
        }
        ApiKeys.init(new java.io.File(app.getFilesDir(), "sogni"));
        ProgramFolder.init(new java.io.File(app.getFilesDir(), "sogni"));
        PictureCopies.shrinker = new PictureShrink();
        SogniHistory.init(new java.io.File(app.getFilesDir(), "sogni"));
        LinearLayout pane = app.col();
        pane.setVisibility(View.GONE);
        pane.setBackgroundColor(BG);
        pane.setClickable(true);
        pane.addView(app.text("Drum Midi Settings", 18, true));
        TextView lead = app.text("How a MIDI drum track, such as DrumMidi output, becomes patterns, fills and a song "
            + "when it is imported (changes apply to the next import), and how Fillerns play.", 13, false);
        lead.setTextColor(MUTED);
        lead.setPadding(0, app.dp(6), 0, app.dp(8));
        pane.addView(lead);
        LinearLayout body = app.col();
        body.addView(this.check("Keep the notes as written",
            "No style, swing, humanize or generated fills. Off: Pulsekit guesses a style and adds its feel and fills.",
            MidiImportSettings.asWritten, on -> MidiImportSettings.asWritten = on));
        body.addView(this.check("Merge hits",
            "Bars that differ by only a few hits become one pattern. The source MIDI still plays as recorded.",
            MidiImportSettings.mergeBars, on -> MidiImportSettings.mergeBars = on));
        body.addView(this.hitsRow());
        body.addView(this.check("Treat a one-off bar after a repeated groove as a fill",
            "Off: every bar is a pattern and the import makes no fills or Fillerns.",
            MidiImportSettings.oneOffFills, on -> MidiImportSettings.oneOffFills = on));
        body.addView(this.check("Reuse a pattern when the same bar comes back",
            "Off: each section gets its own pattern, even when the notes repeat.",
            MidiImportSettings.reuseBars, on -> MidiImportSettings.reuseBars = on));
        body.addView(this.check("Keep silent bars as rests",
            "Off: silent bars are dropped and the song closes up around them.",
            MidiImportSettings.keepSilent, on -> MidiImportSettings.keepSilent = on));
        body.addView(this.fillernDefaultGroup());
        body.addView(this.keyGroup());
        body.addView(this.folderGroup());
        TextView reset = app.action("Reset to defaults", ELEV, FG, v -> {
            MidiImportSettings.reset();
            this.save();
            this.rebuild();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, app.dp(40));
        lp.setMargins(0, app.dp(16), 0, 0);
        body.addView(reset, lp);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(body);
        pane.addView(scroll, app.flexFill());
        app.drumMidiPane = pane;
        if (app.importPane != null && app.importPane.getParent() instanceof ViewGroup) {
            ((ViewGroup) app.importPane.getParent()).addView(pane, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    interface Toggle {
        void set(boolean on);
    }

    View check(String label, String note, boolean on, final Toggle toggle) {
        LinearLayout item = app.col();
        item.setPadding(0, app.dp(10), 0, app.dp(4));
        CheckBox box = new CheckBox(app);
        box.setText(label);
        box.setTextColor(FG);
        box.setTextSize(15);
        box.setChecked(on);
        box.setButtonTintList(ColorStateList.valueOf(HIT));
        box.setOnCheckedChangeListener((b, checked) -> {
            toggle.set(checked);
            this.save();
        });
        item.addView(box);
        TextView sub = app.text(note, 12, false);
        sub.setTextColor(MUTED);
        sub.setPadding(app.dp(32), 0, 0, 0);
        item.addView(sub);
        return item;
    }

    /** Fillern type default: used by every Fillern that has no type of its own. */
    View fillernDefaultGroup() {
        LinearLayout item = app.col();
        item.setPadding(0, app.dp(18), 0, app.dp(4));
        item.addView(app.text("Fillern type default", 15, true));
        TextView sub = app.text("For Fillerns without a type of their own. A Fillern's type is set in its fill list.", 12, false);
        sub.setTextColor(MUTED);
        item.addView(sub);
        android.widget.RadioGroup group = new android.widget.RadioGroup(app);
        group.setTag("fillern-default");
        for (int i = 0; i < Engine.FILLERN_MODES.length; i++) {
            final String mode = Engine.FILLERN_MODES[i];
            android.widget.RadioButton radio = new android.widget.RadioButton(app);
            radio.setId(View.generateViewId());
            radio.setText(Engine.FILLERN_MODE_LABELS[i]);
            radio.setTextColor(FG);
            radio.setTextSize(15);
            radio.setButtonTintList(ColorStateList.valueOf(HIT));
            group.addView(radio);
            if (mode.equals(MidiImportSettings.fillernDefault)) radio.setChecked(true);
            radio.setOnCheckedChangeListener((b, checked) -> {
                if (!checked) return;
                MidiImportSettings.fillernDefault = mode;
                this.save();
                if ("combo".equals(app.view)) app.importLibrary.rebuildImported();
            });
        }
        item.addView(group);
        return item;
    }

    /**
     * Sogni API key file: chosen once, kept in Pulsekit's private folder, and passed as --key_file to
     * programs that take one (SogniMusic). Reset to defaults leaves it alone.
     */
    View keyGroup() {
        LinearLayout item = app.col();
        item.setPadding(0, app.dp(18), 0, app.dp(4));
        item.addView(app.text("Sogni API key file", 15, true));
        TextView sub = app.text("A text file with SOGNI_API_KEY=<your key> (or the key alone). Pulsekit keeps a private copy and "
            + "gives it to every program that takes --key_file, such as SogniMusic.", 12, false);
        sub.setTextColor(MUTED);
        item.addView(sub);
        this.keyStatus = app.text(ApiKeys.status(), 14, false);
        this.keyStatus.setTextColor(FG);
        this.keyStatus.setTag("sogni-key-status");
        this.keyStatus.setPadding(0, app.dp(6), 0, app.dp(6));
        item.addView(this.keyStatus);
        LinearLayout row = app.row();
        TextView choose = app.action("Choose file", ELEV, FG, v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            app.startActivityForResult(intent, PICK_KEY);
        });
        choose.setTag("sogni-key-choose");
        TextView clear = app.action("Clear", ELEV, FG, v -> {
            ApiKeys.clear();
            this.keyStatus.setText(ApiKeys.status());
            app.setNow("Sogni key cleared");
        });
        clear.setTag("sogni-key-clear");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, app.dp(40));
        lp.setMargins(0, 0, app.dp(8), 0);
        row.addView(choose, lp);
        row.addView(clear, new LinearLayout.LayoutParams(-2, app.dp(40)));
        item.addView(row);
        return item;
    }

    /**
     * Program files folder: where PyJav keeps what programs make (SogniChat's replies and results,
     * SogniMusic's tracks). Downloads unless a folder is chosen with the system picker, which lets
     * Pulsekit write there from then on.
     */
    View folderGroup() {
        LinearLayout item = app.col();
        item.setPadding(0, app.dp(18), 0, app.dp(4));
        item.addView(app.text("Program files folder", 15, true));
        TextView sub = app.text("Where PyJav keeps the files programs make: SogniChat's replies, pictures, audio and video, "
            + "SogniMusic's tracks, CutWav's cuts. Downloads unless you choose a folder.", 12, false);
        sub.setTextColor(MUTED);
        item.addView(sub);
        this.folderStatus = app.text(ProgramFolder.label(), 14, false);
        this.folderStatus.setTextColor(FG);
        this.folderStatus.setTag("program-folder-status");
        this.folderStatus.setPadding(0, app.dp(6), 0, app.dp(6));
        item.addView(this.folderStatus);
        LinearLayout row = app.row();
        TextView choose = app.action("Choose folder", ELEV, FG, v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            app.startActivityForResult(intent, PICK_FOLDER);
        });
        choose.setTag("program-folder-choose");
        TextView downloads = app.action("Use Downloads", ELEV, FG, v -> {
            ProgramFolder.clear();
            this.folderStatus.setText(ProgramFolder.label());
            app.setNow("Program files go to Downloads");
        });
        downloads.setTag("program-folder-clear");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, app.dp(40));
        lp.setMargins(0, 0, app.dp(8), 0);
        row.addView(choose, lp);
        row.addView(downloads, new LinearLayout.LayoutParams(-2, app.dp(40)));
        item.addView(row);
        return item;
    }

    /** The picker's folder: Pulsekit keeps permission to write there and uses it from now on. */
    void takeFolder(android.net.Uri tree) {
        try {
            app.getContentResolver().takePersistableUriPermission(tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            ProgramFolder.set(tree.toString());
            app.setNow("Program files go to " + ProgramFolder.label());
        } catch (Exception ex) {
            app.setNow("Could not use that folder" + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
        if (this.folderStatus != null) this.folderStatus.setText(ProgramFolder.label());
    }

    /** The picker's file: its key is kept, or the page says why not. */
    void takeKey(android.net.Uri uri) {
        try {
            String masked = ApiKeys.store(app.projectIo.readUri(uri));
            app.setNow("Sogni key set (ends " + masked + ")");
        } catch (Exception ex) {
            app.setNow(ex.getMessage() == null ? "Could not read that file" : ex.getMessage());
        }
        if (this.keyStatus != null) this.keyStatus.setText(ApiKeys.status());
    }

    /** Merge up to N hits, with − and + buttons. */
    View hitsRow() {
        LinearLayout row = app.row();
        row.setPadding(app.dp(32), app.dp(6), 0, app.dp(4));
        TextView minus = app.action("−", ELEV, FG, v -> this.stepHits(-1));
        TextView plus = app.action("+", ELEV, FG, v -> this.stepHits(1));
        this.hitsLabel = app.text("", 14, false);
        this.hitsLabel.setTextColor(FG);
        this.hitsLabel.setPadding(app.dp(12), 0, app.dp(12), 0);
        this.paintHits();
        row.addView(minus, new LinearLayout.LayoutParams(app.dp(44), app.dp(40)));
        row.addView(this.hitsLabel);
        row.addView(plus, new LinearLayout.LayoutParams(app.dp(44), app.dp(40)));
        return row;
    }

    void stepHits(int d) {
        MidiImportSettings.mergeHits = MidiImportSettings.clampHits(MidiImportSettings.mergeHits + d);
        this.paintHits();
        this.save();
    }

    void paintHits() {
        int n = MidiImportSettings.mergeHits;
        if (this.hitsLabel != null) this.hitsLabel.setText("Merge up to " + n + (n == 1 ? " hit" : " hits"));
    }

    void save() {
        try {
            app.getSharedPreferences(PREFS, 0).edit().putString(KEY, MidiImportSettings.encode()).apply();
        } catch (Throwable ignored) {
            // settings stay for this session
        }
    }

    /** Rebuilds the page so the boxes show the current settings. */
    void rebuild() {
        LinearLayout old = app.drumMidiPane;
        boolean shown = old != null && old.getVisibility() == View.VISIBLE;
        if (old != null && old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        this.wire();
        if (shown && app.drumMidiPane != null) {
            app.drumMidiPane.setVisibility(View.VISIBLE);
            app.drumMidiPane.bringToFront();
        }
    }
}
