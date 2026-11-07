package pulsekit;

import android.app.AlertDialog;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * A DrumPad track's MIDI editor (DrumGrid) on the phone: a bar at a time, its timeline of 16th-note
 * steps over a grid of the 12 pads, and the pads under them. Tap a step on the timeline, then touch a
 * pad: it plays in that step, or comes out of it when it was there. A cell of the grid does the same.
 * ◀ ▶ go bar to bar; + Bar, Quantize, BPM − / +, time signature, ▶ Play, Export MIDI (into Download).
 * Each change makes the track's sound again.
 */
final class DrumGridScreen {
    private DrumGridScreen() {}

    /** The window shown last, where the MIDI went last (for the tests). */
    static AlertDialog last;
    static volatile String lastExport;
    static final int ON = 0xFFE06C3C, BEAT = 0xFF3A3F4A, OFF = 0xFF2A2E36, PICKED = 0xFF4C8BF5;

    static void show(final MainActivity app, final AudioMix.Track t, final Runnable changed) {
        if (last != null && last.isShowing()) last.dismiss();
        if (DrumPadScreen.last != null && DrumPadScreen.last.isShowing()) DrumPadScreen.last.dismiss();
        app.playback.fillMissingVoices();
        final DrumPads.Session s = DrumPads.open(t, null);
        Engine.DrumSet set = app.drumSets.get(Math.max(0, Math.min(app.drumSets.size() - 1, app.playback.drumSetIndex)));
        for (Engine.DrumSet k : app.drumSets) if (k.name.equals(s.take.kit)) set = k;
        s.setVoices(DrumPads.atRate(DrumPads.voices(set, app.voices)), set.name);
        final int[] where = {0, 0};
        LinearLayout col = app.col();
        col.setPadding(app.dp(10), app.dp(8), app.dp(10), app.dp(8));
        final TextView state = app.text(" ", 13, false);
        state.setTag("editor-state");
        final LinearLayout grid = app.col();
        grid.setTag("editor-grid");
        final Runnable[] fill = new Runnable[1];
        final Runnable edited = () -> {
            try {
                s.write();
            } catch (Exception ex) {
                state.setText("Could not write " + t.name + ": " + ex.getMessage());
            }
            fill[0].run();
            if (changed != null) changed.run();
        };
        fill[0] = () -> {
            int per = DrumGrid.stepsPerBar(s.take);
            where[0] = Math.max(0, Math.min(DrumGrid.bars(s.take) - 1, where[0]));
            where[1] = Math.max(0, Math.min(per - 1, where[1]));
            int beat = Math.max(1, 16 / s.take.unit());
            grid.removeAllViews();
            LinearLayout steps = app.row();
            steps.addView(label(app, ""));
            for (int i = 0; i < per; i++) {
                final int at = i;
                TextView c = cell(app, String.valueOf(i + 1), i == where[1] ? PICKED : i % beat == 0 ? BEAT : OFF);
                c.setTag("editor-step:" + i);
                c.setOnClickListener(v -> {
                    where[1] = at;
                    fill[0].run();
                });
                steps.addView(c);
            }
            grid.addView(steps);
            for (int p = 0; p < Engine.TRACK_ID.length; p++) {
                final int pad = p;
                LinearLayout r = app.row();
                r.addView(label(app, Engine.TRACK_SHORT[p]));
                for (int i = 0; i < per; i++) {
                    final int at = i;
                    final int step = where[0] * per + i;
                    boolean on = DrumGrid.has(s.take, p, step);
                    TextView c = cell(app, on ? "●" : "", on ? ON : i == where[1] ? 0xFF33507F : i % beat == 0 ? BEAT : OFF);
                    c.setTag("editor-cell:" + Engine.TRACK_ID[p] + ":" + i);
                    c.setOnClickListener(v -> {
                        where[1] = at;
                        DrumGrid.toggle(s.take, pad, step);
                        edited.run();
                    });
                    r.addView(c);
                }
                grid.addView(r);
            }
            state.setText(DrumGrid.state(s.take, where[0], where[1]));
        };
        // The bar, + Bar, Quantize.
        LinearLayout bars = app.row();
        TextView prev = app.pill("◀", false, v -> {
            where[0] = Math.max(0, where[0] - 1);
            fill[0].run();
        });
        prev.setTag("editor-bar-prev");
        TextView next = app.pill("▶", false, v -> {
            where[0] = Math.min(DrumGrid.bars(s.take) - 1, where[0] + 1);
            fill[0].run();
        });
        next.setTag("editor-bar-next");
        TextView add = app.pill(DrumGrid.ADD_BAR, false, v -> {
            DrumGrid.addBar(s.take);
            where[0] = DrumGrid.bars(s.take) - 1;
            edited.run();
        });
        add.setTag("editor-add-bar");
        TextView quantize = app.pill(DrumGrid.QUANTIZE, false, v -> {
            int moved = DrumGrid.quantize(s.take);
            edited.run();
            state.setText(DrumGrid.state(s.take, where[0], where[1]) + " · " + moved + " moved onto the steps");
        });
        quantize.setTag("editor-quantize");
        bars.addView(prev);
        bars.addView(next, AudioMixerScreen.margin(app));
        bars.addView(add, AudioMixerScreen.margin(app));
        bars.addView(quantize, AudioMixerScreen.margin(app));
        col.addView(bars);
        // BPM − / +, time signature, ▶ Play, Export MIDI.
        LinearLayout tempo = app.row();
        final TextView bpm = app.text(s.take.bpm + " BPM", 14, true);
        bpm.setTag("editor-bpm");
        TextView slower = app.pill("−", false, v -> {
            s.take.bpm = Math.max(Engine.MIN_BPM, s.take.bpm - 1);
            bpm.setText(s.take.bpm + " BPM");
            DrumPadScreen.keep(s);
        });
        slower.setTag("editor-bpm-down");
        TextView faster = app.pill("+", false, v -> {
            s.take.bpm = Math.min(Engine.MAX_BPM, s.take.bpm + 1);
            bpm.setText(s.take.bpm + " BPM");
            DrumPadScreen.keep(s);
        });
        faster.setTag("editor-bpm-up");
        final android.widget.Spinner ts = DrumPadScreen.spinner(app, DrumPads.SIGNATURES);
        ts.setTag("editor-ts");
        ts.setSelection(Math.max(0, java.util.Arrays.asList(DrumPads.SIGNATURES).indexOf(s.take.ts)));
        ts.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) {
                if (DrumPads.SIGNATURES[i].equals(s.take.ts)) return;
                s.take.ts = DrumPads.SIGNATURES[i];
                DrumPadScreen.keep(s);
                fill[0].run();
            }

            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        tempo.addView(slower);
        tempo.addView(bpm, AudioMixerScreen.margin(app));
        tempo.addView(faster, AudioMixerScreen.margin(app));
        tempo.addView(ts, AudioMixerScreen.margin(app));
        col.addView(tempo);
        LinearLayout more = app.row();
        TextView play = app.pill("▶ Play", false, v -> {
            float[][] m = DrumPads.render(s.take, s.voices);
            float g = (float) t.factor();
            for (int i = 0; i < m[0].length; i++) {
                m[0][i] *= g;
                m[1][i] *= g;
            }
            AudioMixerScreen.play(m[0], m[1], state, t.name);
        });
        play.setTag("editor-play");
        TextView export = app.pill(DrumGrid.EXPORT, false, v -> {
            String w = ArtJava.publish(app, DrumGrid.midiName(t), DrumGrid.toMidi(s.take));
            lastExport = w;
            state.setText(w == null ? "Could not save " + DrumGrid.midiName(t) : "Exported " + w);
            if (w != null) app.setNow(state.getText().toString());
        });
        export.setTag("editor-export");
        more.addView(play);
        more.addView(export, AudioMixerScreen.margin(app));
        col.addView(more);
        col.addView(state);
        HorizontalScrollView across = new HorizontalScrollView(app);
        across.addView(grid);
        col.addView(across);
        // The pads: each puts itself in the step selected (and sounds).
        for (int row = 0; row < 2; row++) {
            LinearLayout r = app.row();
            for (int c = 0; c < 6; c++) {
                final int pad = row * 6 + c;
                TextView p = app.pad(Engine.TRACK_SHORT[pad], Engine.TRACK_LABEL[pad], null);
                p.setTag("editor-pad:" + Engine.TRACK_ID[pad]);
                p.setTextSize(10);
                p.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        s.hit(pad, 1f);
                        DrumGrid.toggle(s.take, pad, where[0] * DrumGrid.stepsPerBar(s.take) + where[1]);
                        v.setAlpha(0.6f);
                        edited.run();
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        v.setAlpha(1f);
                    }
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.dp(56), 1f);
                lp.setMargins(app.dp(2), app.dp(2), app.dp(2), app.dp(2));
                r.addView(p, lp);
            }
            col.addView(r);
        }
        fill[0].run();
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        last = new AlertDialog.Builder(app).setTitle(DrumGrid.TITLE + ": " + t.name).setView(scroll).setPositiveButton("Close", null).create();
        last.setOnDismissListener(d -> {
            s.close();
            if (changed != null) changed.run();
        });
        last.show();
        s.play(DrumPadScreen.out());
    }

    static TextView label(MainActivity app, String text) {
        TextView l = app.text(text, 10, true);
        l.setLayoutParams(new LinearLayout.LayoutParams(app.dp(34), app.dp(24)));
        return l;
    }

    static TextView cell(MainActivity app, String text, int color) {
        TextView c = app.text(text, 9, false);
        c.setGravity(android.view.Gravity.CENTER);
        c.setBackgroundColor(color);
        c.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(app.dp(22), app.dp(24));
        lp.setMargins(1, 1, 1, 1);
        c.setLayoutParams(lp);
        return c;
    }
}
