package pulsekit;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * The "Analyze" screen: pick a WAV/MP3, split it into parts, and preview the
 * results. Extracted from MainActivity.buildUi()'s inline analyzePane
 * construction. The actual analysis (runAnalyze, populating analyzeRows)
 * stays on MainActivity.
 */
final class AnalyzePane extends LinearLayout {

    AnalyzePane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        addView((View) app.text("Analyze", 18, true));

        app.analyzeStatus = app.text("Choose a WAV or MP3, then tap Process.", 14, false);
        app.analyzeStatus.setTextColor(MainActivity.MUTED);
        app.analyzeStatus.setPadding(0, app.dp(8), 0, app.dp(8));
        addView((View) app.analyzeStatus);

        app.analyzeFileLab = app.text("No file selected", 13, false);
        app.analyzeFileLab.setTextColor(MainActivity.FG);
        app.analyzeFileLab.setBackground((Drawable) app.round(MainActivity.SURFACE, 8));
        app.analyzeFileLab.setPadding(app.dp(12), app.dp(12), app.dp(12), app.dp(12));
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(-1, -2);
        pathLp.setMargins(0, app.dp(4), 0, app.dp(8));
        addView((View) app.analyzeFileLab, (ViewGroup.LayoutParams) pathLp);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, app.dp(48));
        btnLp.setMargins(0, 0, 0, app.dp(8));
        addView((View) app.action("Choose file", MainActivity.SURFACE, MainActivity.FG, view -> {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("audio/*");
            app.startActivityForResult(intent, MainActivity.OPEN_ANALYZE);
        }), (ViewGroup.LayoutParams) btnLp);
        addView((View) app.action("Process", MainActivity.FG, MainActivity.BG, view -> app.runAnalyze()), (ViewGroup.LayoutParams) btnLp);

        ScrollView scroll = new ScrollView((Context) app);
        app.analyzeRows = app.col();
        scroll.addView((View) app.analyzeRows);
        LinearLayout.LayoutParams scrollLp = app.flexFill();
        scrollLp.setMargins(0, app.dp(8), 0, 0);
        addView((View) scroll, (ViewGroup.LayoutParams) scrollLp);
    }
}
