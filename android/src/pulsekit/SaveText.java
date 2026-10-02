package pulsekit;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

/** Long press on a results text: save it as a .txt file, such as CompareHits_test_results.txt. */
final class SaveText {
    static final int SAVE = 31;

    interface Name {
        String get();
    }

    /** Text waiting for the save dialog to return a place. */
    private static String pending;

    private SaveText() {}

    static void attach(final MainActivity app, final TextView view, final Name name) {
        view.setLongClickable(true);
        view.setOnLongClickListener(v -> {
            final String text = view.getText() == null ? "" : view.getText().toString();
            if (text.trim().length() == 0) return false;
            final String file = name.get();
            new AlertDialog.Builder(app)
                .setItems(new String[] {"Save as " + file}, (d, which) -> save(app, file, text))
                .show();
            return true;
        });
    }

    /** Lines of a results table: the header and the Kick/Snare/Cymbals/Toms rows. */
    private static final java.util.regex.Pattern TABLE =
        java.util.regex.Pattern.compile("^(\\s+ref\\s|Kick\\s|Snare\\s|Cymbals\\s|Toms\\s).*");

    /**
     * Picks a text size (11 down to 8 sp) at which the widest table line fits in `widthPx`, so the
     * tables read across without scrolling; other lines wrap.
     */
    static void fitTables(TextView view, String text, int widthPx) {
        String widest = "";
        for (String line : (text == null ? "" : text).split("\n")) {
            if (TABLE.matcher(line).matches() && line.length() > widest.length()) widest = line;
        }
        float density = view.getResources().getDisplayMetrics().scaledDensity;
        float sp = 11f;
        android.graphics.Paint paint = new android.graphics.Paint(view.getPaint());
        while (sp > 8f) {
            paint.setTextSize(sp * density);
            if (paint.measureText(widest) <= widthPx) break;
            sp -= 0.5f;
        }
        view.setTextSize(sp);
        view.setHorizontallyScrolling(false);
    }

    static void save(MainActivity app, String file, String text) {
        pending = text;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, file);
        app.startActivityForResult(intent, SAVE);
    }

    /** The place the save dialog returned. */
    static void write(MainActivity app, Uri uri) {
        String text = pending;
        pending = null;
        if (text == null) return;
        try {
            java.io.OutputStream out = app.getContentResolver().openOutputStream(uri);
            if (out == null) throw new java.io.IOException("no output");
            try {
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                out.close();
            }
            app.setNow("Saved results");
        } catch (Exception ex) {
            app.setNow("Could not save the results");
        }
    }
}
