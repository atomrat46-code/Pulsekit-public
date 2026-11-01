package pulsekit;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Long DB work's progress (WorkProgress) in a window of its own while it runs: the step being done,
 * a bar, the percent done and left, and about how long to go.
 */
final class WorkProgressDialog {
    /** The one shown last, for the tests. */
    static WorkProgressDialog last;

    final WorkProgress progress;
    final AlertDialog dialog;
    final TextView step;
    final TextView percent;
    final ProgressBar bar;
    private boolean over;
    private final MainActivity app;

    private WorkProgressDialog(MainActivity app, String title, int steps) {
        this.app = app;
        this.progress = new WorkProgress(steps);
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), app.dp(8));
        this.step = app.text("Starting…", 14, false);
        this.step.setTag("work-step");
        this.bar = new ProgressBar(app, null, android.R.attr.progressBarStyleHorizontal);
        this.bar.setMax(1000);
        this.bar.setTag("work-bar");
        this.percent = app.text("0% done", 13, false);
        this.percent.setTag("work-percent");
        col.addView(this.step);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = app.dp(10);
        col.addView(this.bar, lp);
        col.addView(this.percent);
        this.dialog = new AlertDialog.Builder(app).setTitle(title).setView(col).setCancelable(false).show();
        this.tick();
    }

    /** Shown on the screen's thread; the work's thread then sets WorkProgress.NOW to its progress. */
    static WorkProgressDialog show(MainActivity app, String title, int steps) {
        last = new WorkProgressDialog(app, title, steps);
        return last;
    }

    private void tick() {
        if (this.over) return;
        this.step.setText(this.progress.stepText());
        this.bar.setProgress((int) Math.round(this.progress.fraction() * 1000));
        this.percent.setText(this.progress.percentText());
        this.app.handler.postDelayed(this::tick, 250);
    }

    /** The work is over (on the screen's thread). */
    void close() {
        this.over = true;
        if (this.dialog.isShowing()) this.dialog.dismiss();
    }
}
