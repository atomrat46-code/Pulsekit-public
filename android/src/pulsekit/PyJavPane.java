package pulsekit;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The "PyJav" screen: a plain script editor (Python or Java source, .jar, or
 * .class). Extracted from MainActivity.buildUi()'s inline pyPane construction.
 * This Android build is simpler than the desktop PyJav screen — no run-mode
 * picker or recent-files list here.
 */
final class PyJavPane extends LinearLayout {

    PyJavPane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        addView((View) app.text("PyJav", 18, true));

        TextView blurb = app.text("Python, or Java .java / .jar / .class. Run uses java or python3 on this device when it is installed.", 14, false);
        blurb.setTextColor(MainActivity.MUTED);
        blurb.setPadding(0, app.dp(8), 0, app.dp(8));
        addView((View) blurb);

        app.pyEditor = new EditText((Context) app);
        app.pyEditor.setText("#!/usr/bin/env python3\n\"\"\"Pulsekit drum script.\"\"\"\nprint(\"edit me\")\n");
        app.pyEditor.setTypeface(Typeface.MONOSPACE);
        app.pyEditor.setTextColor(MainActivity.FG);
        app.pyEditor.setTextSize(2, 12.0f);
        app.pyEditor.setBackground((Drawable) app.round(MainActivity.ELEV, 10));
        app.pyEditor.setPadding(app.dp(10), app.dp(10), app.dp(10), app.dp(10));
        app.pyEditor.setGravity(0x800033);
        app.pyEditor.setMinLines(8);
        addView((View) app.pyEditor, (ViewGroup.LayoutParams) app.flexFill());
    }
}
