package pulsekit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The "Pads" screen: drum-set chip strip, Live/Silent/Match-Original controls,
 * and a 3x4 grid of drum pads. Extracted from MainActivity.buildUi()'s inline
 * padsPane construction.
 *
 * Talks back to the Activity (MainActivity) for shared state (pattern cells,
 * drum sets, playhead, live-pads mode) and shared helpers (view factories,
 * previewing a sound, flashing a pad, refreshing the grid). Those members were
 * widened from private to package-private on MainActivity so this class, in
 * the same package, can reach them directly.
 */
final class PadsPane extends LinearLayout {

    PadsPane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        addView((View) app.text("Drum sets", 11, true));

        HorizontalScrollView drumSetScroll = new HorizontalScrollView((Context) app);
        drumSetScroll.setHorizontalScrollBarEnabled(false);
        app.drumSetBar = new FlowLayout((Context) app, app.dp(6), app.dp(6));
        app.drumSetBar.setSingleLine(true);
        drumSetScroll.addView((View) app.drumSetBar);
        addView((View) drumSetScroll);

        LinearLayout controls = app.row();
        TextView live = app.outline("Live", false, view -> {
            app.livePads = !app.livePads;
            app.paintOutline((TextView) view, app.livePads);
            app.setNow(app.livePads ? "Pads write the pattern" : "Hold a pad");
        });
        controls.addView((View) live);
        controls.addView((View) app.outline("Silent", false, view -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < 16; ++j) {
                    app.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(app.lens);
            app.syncBuiltinFill();
            app.refreshGrid();
        }));
        app.padMatchAll = app.outline("Match Original", true, view -> app.matchWholeOriginal());
        app.padMatchAll.setVisibility(GONE);
        controls.addView((View) app.padMatchAll);
        TextView matchHint = app.text("  Tap Match Original for the whole kit", 11, false);
        matchHint.setTextColor(MainActivity.SUBTLE);
        controls.addView((View) matchHint);
        addView((View) controls);

        ScrollView padsScroll = new ScrollView((Context) app);
        LinearLayout rows = app.col();
        for (int i = 0; i < 3; ++i) {
            LinearLayout row = app.row();
            for (int j = 0; j < 4; ++j) {
                int n7 = i * 4 + j;
                if (n7 >= Engine.TRACK_ID.length) {
                    row.addView(new View((Context) app), (ViewGroup.LayoutParams) new LinearLayout.LayoutParams(0, app.dp(96), 1.0f));
                    continue;
                }
                int n8 = n7;
                TextView padBtn = app.pad(Engine.TRACK_SHORT[n7], Engine.TRACK_LABEL[n7], view -> {
                    app.bang(n8, 110);
                    app.flashPad(n8);
                    if (app.livePads) {
                        int n2 = app.playhead >= 0 ? app.playhead : 0;
                        app.cells[n8][n2] = app.cells[n8][n2] > 0 ? 0 : 100;
                        if (app.cells[n8][n2] <= 0) {
                            app.lens[n8][n2] = 0;
                        }
                        app.syncBuiltinFill();
                        app.refreshGrid();
                    }
                });
                app.padBtns[n7] = padBtn;
                padBtn.setOnLongClickListener(view -> {
                    app.padTarget = n8;
                    app.showPadMenu(n8);
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.dp(96), 1.0f);
                lp.setMargins(app.dp(4), app.dp(4), app.dp(4), app.dp(4));
                padBtn.setLayoutParams((ViewGroup.LayoutParams) lp);
                row.addView((View) padBtn);
            }
            rows.addView((View) row);
        }
        padsScroll.addView((View) rows);
        addView((View) padsScroll, (ViewGroup.LayoutParams) app.flexFill());

        app.refreshDrumSets();
    }
}
