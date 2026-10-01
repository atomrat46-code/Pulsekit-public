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

    final MainActivity app;
    TextView hitsLabel;

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
