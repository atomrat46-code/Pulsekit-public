package pulsekit;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static pulsekit.MainActivity.*;

/** The Help page (File > Help-Android). Each section is a caption and its text. */
final class HelpPage {
    /** Caption, then text. */
    static final String[][] SECTIONS = {
        {"PyJav", "Python, Java, JavaScript, or TypeScript. On Android, Node.js runs in Termux: "
            + "pkg install nodejs. npm installs @sogni-ai/sogni-client. Java runs in the app."},
    };

    final MainActivity app;

    HelpPage(MainActivity app) {
        this.app = app;
    }

    /** Builds the page and adds it on top of the other pages (hidden until shown). */
    void wire() {
        LinearLayout pane = app.col();
        pane.setVisibility(View.GONE);
        pane.setBackgroundColor(BG);
        pane.setClickable(true);
        pane.addView(app.text("Help", 18, true));
        LinearLayout body = app.col();
        for (String[] section : SECTIONS) {
            TextView caption = app.text(section[0], 15, true);
            caption.setPadding(0, app.dp(16), 0, app.dp(6));
            body.addView(caption);
            TextView text = app.text(section[1], 14, false);
            text.setTextColor(MUTED);
            text.setLineSpacing(0f, 1.15f);
            body.addView(text);
        }
        ScrollView scroll = new ScrollView(app);
        scroll.addView(body);
        pane.addView(scroll, app.flexFill());
        app.helpPane = pane;
        if (app.importPane != null && app.importPane.getParent() instanceof ViewGroup) {
            ((ViewGroup) app.importPane.getParent()).addView(pane, new FrameLayout.LayoutParams(-1, -1));
        }
    }
}
