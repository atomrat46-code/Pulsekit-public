package pulsekit;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

import static pulsekit.MainActivity.*;

/** The pattern grid: cell taps and note lengths, the accent row, steps and time signature. */
final class GridEditor {
    final MainActivity app;

    GridEditor(MainActivity app) {
        this.app = app;
    }

    /** Builds the pattern grid (accent row, one row per track) inside a scroll view. */
    void buildGridPane(FrameLayout frameLayout) {
        TextView textView2;
        TextView textView3;
        int n2;
        app.gridPane = app.col();
        app.gridPane.setClipChildren(true);
        LinearLayout linearLayout11 = app.row();
        linearLayout11.setClipChildren(true);
        linearLayout11.setPadding(0, 0, 0, app.dp(2));
        if (Build.VERSION.SDK_INT >= 21) {
            linearLayout11.setElevation((float)app.dp(6));
        }
        app.accLab = app.labelCell("ACC", FG);
        app.accLab.setClickable(true);
        this.bindAccCell(app.accLab);
        app.accLab.setOnClickListener(view -> {
            int n;
            boolean bl = false;
            for (n = 0; n < app.steps; ++n) {
                if (!app.accents[n]) continue;
                bl = true;
                break;
            }
            if (bl) {
                for (n = 0; n < 32; ++n) {
                    app.accents[n] = false;
                }
            } else {
                Engine.defaultAccents(app.accents, app.steps, Engine.stepsPerBeat(app.tsDen));
            }
            view.performHapticFeedback(3);
            this.refreshGrid();
        });
        linearLayout11.addView((View)app.accLab);
        for (n2 = 0; n2 < 32; ++n2) {
            int n4 = n2;
            TextView textView14 = app.cell(n2 % 4 == 0 ? Integer.toString(n2 / 4 + 1) : "\u00b7", view -> {
                app.accents[n4] = !app.accents[n4];
                view.performHapticFeedback(3);
                this.refreshGrid();
            });
            textView14.setTag((Object)("acc-" + n2));
            this.bindAccCell(textView14);
            app.accCells[n2] = textView14;
            linearLayout11.addView((View)textView14, (ViewGroup.LayoutParams)app.accLp());
        }
        app.gridPane.addView((View)linearLayout11);
        for (n2 = 0; n2 < Engine.TRACK_ID.length; ++n2) {
            LinearLayout linearLayout12 = app.row();
            linearLayout12.setClipChildren(true);
            linearLayout12.setMinimumHeight(app.dp(40));
            int n5 = n2;
            textView3 = app.labelCell(Engine.TRACK_SHORT[n2], MUTED);
            textView3.setOnClickListener(view -> {
                if (app.mutes[n5]) {
                    app.mutes[n5] = false;
                    this.refreshGrid();
                    return;
                }
                app.playback.bang(n5, 110);
            });
            textView3.setOnLongClickListener(view -> {
                app.mutes[n5] = !app.mutes[n5];
                this.refreshGrid();
                return true;
            });
            linearLayout12.addView((View)textView3);
            for (int i = 0; i < 32; ++i) {
                int n6 = i;
                textView2 = app.cell("", view -> {
                    if (app.paintLen > 0) {
                        this.setCellLen(n5, n6, app.paintLen);
                        return;
                    }
                    int[][] nArray = this.editCells();
                    int[][] nArray2 = this.editLens();
                    int v = nArray[n5][n6];
                    nArray[n5][n6] = v <= 0 ? 100 : (v < 90 ? 0 : (v < 120 ? 127 : 64));
                    if (nArray[n5][n6] <= 0) {
                        nArray2[n5][n6] = 0;
                    }
                    this.refreshGrid();
                    if (!"fills".equals(app.view)) {
                        app.styleLibrary.syncBuiltinFill();
                    }
                });
                this.bindCellLength(textView2, n5, n6);
                app.grid[n2][i] = textView2;
                linearLayout12.addView((View)textView2, (ViewGroup.LayoutParams)app.cellLp());
            }
            app.gridPane.addView((View)linearLayout12);
        }
        HorizontalScrollView horizontalScrollView2 = new HorizontalScrollView((Context)app);
        horizontalScrollView2.setHorizontalScrollBarEnabled(false);
        horizontalScrollView2.setFillViewport(true);
        horizontalScrollView2.setOverScrollMode(2);
        horizontalScrollView2.addView((View)app.gridPane);
        app.gridScroll = new ScrollView((Context)app);
        app.gridScroll.setFillViewport(true);
        app.gridScroll.setOverScrollMode(2);
        app.gridScroll.setPadding(0, app.dp(4), 0, app.dp(8));
        app.gridScroll.setClipToPadding(false);
        app.gridScroll.addView((View)horizontalScrollView2);
        frameLayout.addView((View)app.gridScroll);
    }

    /** Builds the note-length (LEN) chip bar under the grid. */
    void buildLenBar(LinearLayout linearLayout23) {
        app.lenBar = app.row();
        app.lenBar.setPadding(0, 0, 0, app.dp(8));
        app.lenBar.setGravity(16);
        TextView textView23 = app.text("LEN", 10, true);
        textView23.setTextColor(SUBTLE);
        textView23.setPadding(0, 0, app.dp(6), 0);
        app.lenBar.addView((View)textView23);
        for (int i = 0; i < Engine.LEN_STEPS.length; ++i) {
            int n9 = Engine.LEN_STEPS[i];
            TextView textView24 = app.text(Engine.LEN_LABEL[i], 11, true);
            textView24.setGravity(17);
            textView24.setTextColor(BG);
            textView24.setPadding(app.dp(4), app.dp(12), app.dp(4), app.dp(12));
            LinearLayout.LayoutParams layoutParams5 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams5.setMargins(app.dp(3), 0, app.dp(3), 0);
            textView24.setOnClickListener(view -> {
                app.paintLen = app.paintLen == n9 ? 0 : n9;
                this.paintLenChips();
            });
            app.lenChips[i] = textView24;
            app.lenBar.addView((View)textView24, (ViewGroup.LayoutParams)layoutParams5);
        }
        linearLayout23.addView((View)app.lenBar);
        this.paintLenChips();
    }

    long cellTapAt;

    int cellTapT = -1;

    int cellTapS = -1;

    float cellTapX;

    float cellTapY;

    View cellTapView;

    Runnable cellTapClick;

    Runnable cellTapUnlock;

    PopupWindow lenPop;

    boolean lenPopReady;

    void refreshGrid() {
        app.shownHead = app.playhead;
        this.applyGridWidth();
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            if (app.gridPane != null && app.gridPane.getChildCount() > i + 1) {
                LinearLayout linearLayout = (LinearLayout)app.gridPane.getChildAt(i + 1);
                TextView textView = (TextView)linearLayout.getChildAt(0);
                textView.setTextColor(app.mutes[i] ? SUBTLE : MUTED);
            }
            for (int j = 0; j < 32; ++j) {
                boolean bl = Engine.lengthCoveredAt(this.editLens()[i], this.editCells()[i], j);
                int n = this.editCells()[i][j];
                int n2 = n > 0 ? Engine.lenAt(this.editLens(), i, j) : Engine.coverLen(this.editLens(), this.editCells(), i, j);
                this.paintCell(app.grid[i][j], n, n2, j == app.playhead, app.accents[j], bl);
            }
        }
        this.paintAccentRow();
    }

    void refreshPlayheadBase() {
        int n = app.shownHead;
        int n2 = app.playhead;
        if (n == n2) {
            return;
        }
        if (n >= 0 && n < 32) {
            this.paintColumn(n, false);
        }
        if (n2 >= 0 && n2 < 32) {
            this.paintColumn(n2, true);
        }
        app.shownHead = n2;
    }

    void paintColumn(int n, boolean bl) {
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            boolean bl2 = Engine.lengthCoveredAt(this.editLens()[i], this.editCells()[i], n);
            int n2 = this.editCells()[i][n];
            int n3 = n2 > 0 ? Engine.lenAt(this.editLens(), i, n) : Engine.coverLen(this.editLens(), this.editCells(), i, n);
            this.paintCell(app.grid[i][n], n2, n3, bl, app.accents[n], bl2);
        }
        if (app.gridPane == null || app.gridPane.getChildCount() == 0) {
            return;
        }
        LinearLayout linearLayout = (LinearLayout)app.gridPane.getChildAt(0);
        View view = linearLayout.getChildAt(n + 1);
        if (view instanceof TextView) {
            TextView textView = (TextView)view;
            app.fillRound(textView, app.accents[n] ? ACCENT : (bl ? FG : ELEV), 6);
            textView.setText((CharSequence)(n % 4 == 0 ? Integer.toString(n / 4 + 1) : (app.accents[n] ? "\u25be" : "\u00b7")));
            textView.setTextColor(app.accents[n] || bl ? BG : FG);
        }
    }

    void paintAccentRow() {
        if (app.gridPane == null || app.gridPane.getChildCount() == 0) {
            return;
        }
        boolean bl = false;
        for (int i = 0; i < app.steps; ++i) {
            if (!app.accents[i]) continue;
            bl = true;
            break;
        }
        if (app.accLab != null) {
            app.accLab.setTextColor(bl ? ACCENT : FG);
        }
        LinearLayout linearLayout = (LinearLayout)app.gridPane.getChildAt(0);
        for (int i = 0; i < 32; ++i) {
            View view = linearLayout.getChildAt(i + 1);
            if (!(view instanceof TextView)) continue;
            TextView textView = (TextView)view;
            app.fillRound(textView, app.accents[i] ? ACCENT : (i == app.playhead ? FG : ELEV), 6);
            textView.setText((CharSequence)(i % 4 == 0 ? Integer.toString(i / 4 + 1) : (app.accents[i] ? "\u25be" : "\u00b7")));
            textView.setTextColor(app.accents[i] || i == app.playhead ? BG : FG);
        }
    }

    int[][] editCells() {
        return "fills".equals(app.view) ? app.fillPat : app.cells;
    }

    int[][] editLens() {
        return "fills".equals(app.view) ? app.fillLens : app.lens;
    }

    void setCellLen(int n, int n2, int n3) {
        int[][] nArray = this.editCells();
        int[][] nArray2 = this.editLens();
        int n4 = Engine.clampLen(n3);
        if (nArray[n][n2] <= 0) {
            nArray[n][n2] = 100;
        }
        nArray2[n][n2] = n4 <= 1 ? 0 : n4;
        this.refreshGrid();
        if (!"fills".equals(app.view)) {
            app.styleLibrary.syncBuiltinFill();
        }
    }

    void bindAccCell(TextView textView) {
        textView.setClickable(true);
        textView.setLongClickable(false);
        textView.setTextIsSelectable(false);
        textView.setOnTouchListener((view, motionEvent) -> {
            int n = motionEvent.getActionMasked();
            if (n == 0) {
                view.setPressed(true);
                this.disallowScroll(view, true);
                return true;
            }
            if (n == 2) {
                return true;
            }
            if (n == 1 || n == 3) {
                view.setPressed(false);
                this.disallowScroll(view, false);
                if (n == 1) {
                    view.performClick();
                }
                return true;
            }
            return true;
        });
    }

    void bindCellLength(final TextView textView, final int n, final int n2) {
        textView.setClickable(true);
        textView.setLongClickable(false);
        textView.setTextIsSelectable(false);
        textView.setHapticFeedbackEnabled(true);
        textView.setOnLongClickListener(null);
        textView.setOnTouchListener(new View.OnTouchListener(){
            float downX;
            float downY;
            long downAt;
            boolean held;
            boolean moved;
            final Runnable fire = () -> {
                if (this.held) {
                    return;
                }
                this.held = true;
                GridEditor.this.cancelPendingCellTap();
                textView.performHapticFeedback(0);
                GridEditor.this.showLenMenu(n, n2);
            };

            public boolean onTouch(View view, MotionEvent motionEvent) {
                int n3 = motionEvent.getActionMasked();
                if (n3 == 0) {
                    boolean bl;
                    long l = SystemClock.uptimeMillis();
                    this.held = false;
                    this.moved = false;
                    this.downX = motionEvent.getRawX();
                    this.downY = motionEvent.getRawY();
                    this.downAt = l;
                    app.handler.removeCallbacks(this.fire);
                    view.setPressed(true);
                    GridEditor.this.disallowScroll(view, true);
                    float f = app.dp(22);
                    boolean bl2 = bl = l - GridEditor.this.cellTapAt < 400L && GridEditor.this.cellTapT == n && GridEditor.this.cellTapS == n2 && Math.abs(this.downX - GridEditor.this.cellTapX) <= f && Math.abs(this.downY - GridEditor.this.cellTapY) <= f;
                    if (bl) {
                        GridEditor.this.cancelPendingCellTap();
                        this.fire.run();
                        return true;
                    }
                    app.handler.postDelayed(this.fire, 400L);
                    return true;
                }
                if (n3 == 2) {
                    if (!this.moved && (Math.abs(motionEvent.getRawX() - this.downX) > (float)app.dp(28) || Math.abs(motionEvent.getRawY() - this.downY) > (float)app.dp(28))) {
                        this.moved = true;
                        app.handler.removeCallbacks(this.fire);
                        view.setPressed(false);
                        GridEditor.this.disallowScroll(view, false);
                    }
                    return true;
                }
                if (n3 == 1 || n3 == 3) {
                    long l = SystemClock.uptimeMillis() - this.downAt;
                    app.handler.removeCallbacks(this.fire);
                    view.setPressed(false);
                    if (this.moved || this.held) {
                        GridEditor.this.disallowScroll(view, false);
                        return true;
                    }
                    if (n3 == 3) {
                        GridEditor.this.disallowScroll(view, false);
                        return true;
                    }
                    if (l >= 280L) {
                        this.fire.run();
                        return true;
                    }
                    if (app.paintLen > 0) {
                        textView.performClick();
                        return true;
                    }
                    GridEditor.this.cellTapT = n;
                    GridEditor.this.cellTapS = n2;
                    GridEditor.this.cellTapAt = SystemClock.uptimeMillis();
                    GridEditor.this.cellTapX = this.downX;
                    GridEditor.this.cellTapY = this.downY;
                    GridEditor.this.cellTapView = view;
                    if (GridEditor.this.cellTapUnlock != null) {
                        app.handler.removeCallbacks(GridEditor.this.cellTapUnlock);
                    }
                    GridEditor.this.cellTapUnlock = () -> {
                        GridEditor.this.disallowScroll(view, false);
                        GridEditor.this.cellTapUnlock = null;
                    };
                    app.handler.postDelayed(GridEditor.this.cellTapUnlock, 120L);
                    if (GridEditor.this.cellTapClick != null) {
                        app.handler.removeCallbacks(GridEditor.this.cellTapClick);
                        GridEditor.this.cellTapClick = null;
                    }
                    textView.performClick();
                    return true;
                }
                return true;
            }
        });
    }

    void cancelPendingCellTap() {
        this.cellTapT = -1;
        this.cellTapAt = 0L;
        if (this.cellTapClick != null) {
            app.handler.removeCallbacks(this.cellTapClick);
            this.cellTapClick = null;
        }
        if (this.cellTapUnlock != null) {
            app.handler.removeCallbacks(this.cellTapUnlock);
            this.cellTapUnlock = null;
        }
        if (this.cellTapView != null) {
            this.disallowScroll(this.cellTapView, false);
        }
        this.cellTapView = null;
    }

    void paintLenChips() {
        if (app.lenChips[0] == null) {
            return;
        }
        for (int i = 0; i < Engine.LEN_STEPS.length; ++i) {
            int n = Engine.LEN_STEPS[i];
            GradientDrawable gradientDrawable = app.round(Engine.lenHue(n) | 0xFF000000, 8);
            if (app.paintLen == n) {
                gradientDrawable.setStroke(app.dp(3), FG);
            }
            app.lenChips[i].setBackground((Drawable)gradientDrawable);
            app.lenChips[i].setAlpha(app.paintLen == 0 || app.paintLen == n ? 1.0f : 0.45f);
        }
    }

    void disallowScroll(View view, boolean bl) {
        for (ViewParent viewParent = view.getParent(); viewParent != null; viewParent = viewParent.getParent()) {
            viewParent.requestDisallowInterceptTouchEvent(bl);
        }
    }

    void showLenMenu(int n, int n2) {
        int n3;
        this.hideLenMenu();
        int n4 = Engine.lenAt(this.editLens(), n, n2);
        LinearLayout linearLayout = app.col();
        GradientDrawable gradientDrawable = app.round(SURFACE, 0);
        gradientDrawable.setStroke(app.dp(1), BORDER);
        linearLayout.setBackground((Drawable)gradientDrawable);
        linearLayout.setPadding(app.dp(12), app.dp(10), app.dp(12), app.dp(16));
        LinearLayout linearLayout2 = app.row();
        linearLayout2.setGravity(16);
        LinearLayout linearLayout3 = app.col();
        TextView textView = app.text("NOTE LENGTH", 10, true);
        textView.setTextColor(SUBTLE);
        linearLayout3.addView((View)textView);
        TextView textView2 = app.text(Engine.TRACK_LABEL[n] + " \u00b7 step " + (n2 + 1) + " \u00b7 " + Engine.noteLengthLabel(n4), 13, true);
        textView2.setTextColor(FG);
        linearLayout3.addView((View)textView2);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        linearLayout2.addView((View)linearLayout3, (ViewGroup.LayoutParams)layoutParams);
        TextView textView3 = app.text("Done", 13, true);
        textView3.setTextColor(MUTED);
        textView3.setPadding(app.dp(12), app.dp(8), app.dp(4), app.dp(8));
        textView3.setOnClickListener(view -> this.hideLenMenu());
        linearLayout2.addView((View)textView3);
        linearLayout.addView((View)linearLayout2);
        LinearLayout linearLayout4 = app.row();
        linearLayout4.setPadding(0, app.dp(8), 0, 0);
        for (n3 = 0; n3 < Engine.LEN_STEPS.length; ++n3) {
            int n5 = Engine.LEN_STEPS[n3];
            TextView textView4 = app.text(Engine.LEN_LABEL[n3], 12, n5 == n4);
            textView4.setGravity(17);
            textView4.setTextColor(BG);
            GradientDrawable gradientDrawable2 = app.round(Engine.lenHue(n5) | 0xFF000000, 8);
            if (n5 == n4) {
                gradientDrawable2.setStroke(app.dp(2), FG);
            }
            textView4.setBackground((Drawable)gradientDrawable2);
            textView4.setPadding(app.dp(4), app.dp(12), app.dp(4), app.dp(12));
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams2.setMargins(app.dp(3), 0, app.dp(3), 0);
            textView4.setOnClickListener(view -> {
                if (!this.lenPopReady) {
                    return;
                }
                this.setCellLen(n, n2, n5);
                this.hideLenMenu();
            });
            linearLayout4.addView((View)textView4, (ViewGroup.LayoutParams)layoutParams2);
        }
        linearLayout.addView((View)linearLayout4);
        linearLayout.measure(View.MeasureSpec.makeMeasureSpec((int)app.getResources().getDisplayMetrics().widthPixels, (int)0x40000000), 0);
        n3 = linearLayout.getMeasuredHeight();
        this.lenPop = new PopupWindow((View)linearLayout, -1, n3, false);
        this.lenPop.setBackgroundDrawable((Drawable)new GradientDrawable());
        this.lenPop.setOutsideTouchable(false);
        this.lenPop.setElevation((float)app.dp(12));
        View linearLayout5 = app.gridPane != null ? (View)app.gridPane : app.getWindow().getDecorView();
        try {
            this.lenPop.showAtLocation((View)linearLayout5, 80, 0, 0);
        }
        catch (Exception exception) {
            Toast.makeText((Context)app, (CharSequence)(Engine.TRACK_LABEL[n] + " \u00b7 " + Engine.noteLengthLabel(n4)), (int)0).show();
            return;
        }
        this.lenPopReady = false;
        app.handler.postDelayed(() -> {
            this.lenPopReady = true;
        }, 280L);
    }

    void hideLenMenu() {
        this.lenPopReady = false;
        if (this.lenPop == null) {
            return;
        }
        try {
            if (this.lenPop.isShowing()) {
                this.lenPop.dismiss();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        this.lenPop = null;
    }

    void paintCell(TextView textView, int n, int n2, boolean bl, boolean bl2, boolean bl3) {
        String string;
        if (textView == null) {
            return;
        }
        int n3 = bl ? HIT : (n > 0 ? Engine.lenColor(n2, n) : (bl3 ? Engine.lenColor(n2, 0) : (bl2 ? ACC_CELL : ELEV)));
        app.fillRound(textView, n3, 6);
        Drawable drawable = textView.getBackground();
        if (drawable instanceof GradientDrawable) {
            GradientDrawable gradientDrawable = (GradientDrawable)drawable;
            if (bl2 && !bl) {
                gradientDrawable.setStroke(Math.max(2, app.dp(2)), ACCENT);
            } else {
                gradientDrawable.setStroke(0, 0);
            }
        }
        string = "";
        if (n > 0) {
            string = Engine.lenMark(n2);
            if (string.isEmpty()) {
                string = "16";
            }
        } else if (bl3) {
            string = "\u2014";
        }
        textView.setText((CharSequence)string);
        textView.setTextColor(n >= 90 ? BG : (n > 0 || bl3 ? FG : SUBTLE));
        textView.setTextSize(2, 8.0f);
    }

    EditText numField(String string, int n, int n2) {
        EditText editText = new EditText((Context)app);
        editText.setText((CharSequence)string);
        editText.setTextColor(FG);
        editText.setTextSize(2, (float)n);
        editText.setTypeface(Typeface.MONOSPACE, 1);
        editText.setBackgroundColor(0);
        editText.setInputType(2);
        editText.setGravity(0x800005);
        editText.setPadding(0, 0, 0, 0);
        editText.setMinWidth(app.dp(n2));
        editText.setSingleLine(true);
        editText.setImeOptions(6);
        editText.setIncludeFontPadding(false);
        return editText;
    }

    void bindNumField(EditText editText, int n2, int n3, IntFn intFn) {
        Runnable runnable = () -> {
            block6: {
                try {
                    String string;
                    int v = Integer.parseInt(editText.getText().toString().trim().replaceAll("[^0-9-]", ""));
                    v = Engine.clamp(v, n2, n3);
                    if (editText == app.tsDenField) {
                        v = Engine.clampTsDen(v);
                    }
                    if (!(string = Integer.toString(v)).contentEquals((CharSequence)editText.getText())) {
                        editText.setText((CharSequence)string);
                    }
                    intFn.apply(v);
                }
                catch (Exception exception) {
                    if (editText == app.bpmLabel) {
                        editText.setText((CharSequence)Integer.toString(app.bpm()));
                    }
                    if (editText == app.tsNumField) {
                        editText.setText((CharSequence)Integer.toString(app.tsNum));
                    }
                    if (editText != app.tsDenField) break block6;
                    editText.setText((CharSequence)Integer.toString(app.tsDen));
                }
            }
        };
        editText.setOnEditorActionListener((textView, n, keyEvent) -> {
            if (n == 6 || n == 2) {
                runnable.run();
                return true;
            }
            return false;
        });
        editText.setOnFocusChangeListener((view, bl) -> {
            if (bl) {
                editText.post(() -> ((EditText)editText).selectAll());
            } else {
                runnable.run();
            }
        });
    }

    void applyTimeSig(int n, int n2) {
        boolean bl = Engine.isDoubled(app.steps, app.tsNum, app.tsDen);
        app.tsNum = Engine.clampTsNum(n);
        app.tsDen = Engine.clampTsDen(n2);
        if (app.tsNumField != null) {
            app.tsNumField.setText((CharSequence)Integer.toString(app.tsNum));
        }
        if (app.tsDenField != null) {
            app.tsDenField.setText((CharSequence)Integer.toString(app.tsDen));
        }
        int n3 = Engine.patternSteps(app.tsNum, app.tsDen, bl);
        Engine.defaultAccents(app.accents, n3, Engine.stepsPerBeat(app.tsDen));
        this.applySteps(n3, true);
    }

    void toggleSteps() {
        int n = Engine.barSteps(app.tsNum, app.tsDen);
        if (n * 2 <= 32 && app.steps == n) {
            this.applySteps(n * 2, true);
        } else {
            this.applySteps(n, false);
        }
    }

    void applySteps(int n, boolean bl) {
        int n2 = Engine.barSteps(app.tsNum, app.tsDen);
        int n3 = Engine.clampSteps(n);
        if (n3 != n2 && (n3 != n2 * 2 || n2 * 2 > 32)) {
            n3 = n2;
        }
        if (n3 > app.steps && bl) {
            int n4 = Math.max(1, Math.min(app.steps, n2));
            Engine.tileSteps(app.cells, n4, n3);
            Engine.tileSteps(app.lens, n4, n3);
            for (int i = n4; i < n3; ++i) {
                app.accents[i] = app.accents[i % n4];
            }
        }
        app.steps = n3;
        if (app.stepsBtn != null) {
            app.stepsBtn.setText((CharSequence)(app.steps + " steps"));
            app.paintOutline(app.stepsBtn, app.steps > n2);
        }
        this.applyGridWidth();
        app.styleLibrary.syncBuiltinFill();
        this.refreshGrid();
    }

    void applyGridWidth() {
        for (int i = 0; i < 32; ++i) {
            int n;
            int n2 = n = i < app.steps ? 0 : 8;
            if (app.accCells[i] != null) {
                app.accCells[i].setVisibility(n);
            }
            for (int j = 0; j < Engine.TRACK_ID.length; ++j) {
                if (app.grid[j][i] == null) continue;
                app.grid[j][i].setVisibility(n);
            }
        }
    }

    int currentSteps() {
        if (app.songPlay) {
            List<Engine.Part> list = app.songEditor.activeSong();
            if (app.songIndex >= 0 && app.songIndex < list.size()) {
                return Engine.clampSteps(list.get((int)app.songIndex).steps);
            }
        }
        return app.steps;
    }

    void refreshPlayhead() {
        this.refreshPlayheadBase();
        app.songEditor.afterRefreshSong();
    }
}
