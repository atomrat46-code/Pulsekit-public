package pulsekit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The "Isolation" screen, reached after importing a WAV to split drums onto
 * pads. Extracted from MainActivity.buildUi()'s inline isolatePane
 * construction. The actual isolation logic (finishIsolation, populating
 * isolateRows) stays on MainActivity.
 */
final class IsolatePane extends LinearLayout {

    IsolatePane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        addView((View) app.text("Isolation", 18, true));

        app.isolateStatus = app.text("Import a WAV or MP3 to isolate pads.", 14, false);
        app.isolateStatus.setTextColor(MainActivity.MUTED);
        app.isolateStatus.setPadding(0, app.dp(8), 0, app.dp(12));
        addView((View) app.isolateStatus);

        ScrollView scroll = new ScrollView((Context) app);
        app.isolateRows = app.col();
        scroll.addView((View) app.isolateRows);
        addView((View) scroll, (ViewGroup.LayoutParams) app.flexFill());

        LinearLayout acts = app.row();
        acts.setPadding(0, app.dp(8), 0, 0);
        app.isolateUse = app.action("Use pads", MainActivity.FG, MainActivity.BG, view -> app.finishIsolation());
        app.isolateSkip = app.action("Skip", MainActivity.ELEV, MainActivity.FG, view -> app.finishIsolation());
        acts.addView((View) app.isolateUse, (ViewGroup.LayoutParams) app.flexBtn());
        acts.addView((View) app.isolateSkip, (ViewGroup.LayoutParams) app.flexBtn());
        addView((View) acts);
    }
}
