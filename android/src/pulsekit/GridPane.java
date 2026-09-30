package pulsekit;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The step grid used by the Pattern, Combo and Fills views. Extracted from
 * MainActivity.buildUi()'s inline gridPane construction. This is the Android
 * counterpart of the desktop PatternGridPanel.
 *
 * Note: the surrounding ScrollView (MainActivity.gridScroll) stays built in
 * MainActivity.buildUi(), since it's just scaffolding that show(String) also
 * toggles visibility on directly — this class is only the grid content itself
 * (what used to be MainActivity.gridPane).
 */
final class GridPane extends LinearLayout {

    GridPane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setClipChildren(true);

        LinearLayout accRow = app.row();
        accRow.setClipChildren(true);
        accRow.setPadding(0, 0, 0, app.dp(2));
        if (Build.VERSION.SDK_INT >= 21) {
            accRow.setElevation((float) app.dp(6));
        }

        app.accLab = app.labelCell("ACC", MainActivity.FG);
        app.accLab.setClickable(true);
        app.bindAccCell(app.accLab);
        app.accLab.setOnClickListener(view -> {
            int n;
            boolean any = false;
            for (n = 0; n < app.steps; ++n) {
                if (!app.accents[n]) continue;
                any = true;
                break;
            }
            if (any) {
                for (n = 0; n < 32; ++n) {
                    app.accents[n] = false;
                }
            } else {
                Engine.defaultAccents(app.accents, app.steps, Engine.stepsPerBeat(app.tsDen));
            }
            view.performHapticFeedback(3);
            app.refreshGrid();
        });
        accRow.addView((View) app.accLab);

        for (int n2 = 0; n2 < 32; ++n2) {
            int n4 = n2;
            TextView accCell = app.cell(n2 % 4 == 0 ? Integer.toString(n2 / 4 + 1) : "\u00b7", view -> {
                app.accents[n4] = !app.accents[n4];
                view.performHapticFeedback(3);
                app.refreshGrid();
            });
            accCell.setTag("acc-" + n2);
            app.bindAccCell(accCell);
            app.accCells[n2] = accCell;
            accRow.addView((View) accCell, (ViewGroup.LayoutParams) app.accLp());
        }
        addView((View) accRow);

        for (int n2 = 0; n2 < Engine.TRACK_ID.length; ++n2) {
            LinearLayout trackRow = app.row();
            trackRow.setClipChildren(true);
            trackRow.setMinimumHeight(app.dp(40));
            int n5 = n2;
            TextView label = app.labelCell(Engine.TRACK_SHORT[n2], MainActivity.MUTED);
            label.setOnClickListener(view -> {
                if (app.mutes[n5]) {
                    app.mutes[n5] = false;
                    app.refreshGrid();
                    return;
                }
                app.bang(n5, 110);
            });
            label.setOnLongClickListener(view -> {
                app.mutes[n5] = !app.mutes[n5];
                app.refreshGrid();
                return true;
            });
            trackRow.addView((View) label);

            for (int i = 0; i < 32; ++i) {
                int n6 = i;
                TextView cellView = app.cell("", view -> {
                    if (app.paintLen > 0) {
                        app.setCellLen(n5, n6, app.paintLen);
                        return;
                    }
                    int[][] vel = app.editCells();
                    int[][] lensArr = app.editLens();
                    int n3 = vel[n5][n6];
                    vel[n5][n6] = n3 <= 0 ? 100 : (n3 < 90 ? 0 : (n3 < 120 ? 127 : 64));
                    if (vel[n5][n6] <= 0) {
                        lensArr[n5][n6] = 0;
                    }
                    app.refreshGrid();
                    if (!"fills".equals(app.view)) {
                        app.syncBuiltinFill();
                    }
                });
                app.bindCellLength(cellView, n5, n6);
                app.grid[n2][i] = cellView;
                trackRow.addView((View) cellView, (ViewGroup.LayoutParams) app.cellLp());
            }
            addView((View) trackRow);
        }
    }
}
