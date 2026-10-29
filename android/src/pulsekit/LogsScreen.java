package pulsekit;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/**
 * File > Logs: the log entries (Logs) of the type picked (chips), the newest first. A tap opens the
 * entry: its text can be selected and copied (a long press selects; Copy takes the whole entry),
 * saved (Export) or deleted; Export all saves every entry of the list into one text file.
 */
final class LogsScreen {
    private LogsScreen() {}

    /** The type shown last. */
    static String type = Logs.ALL;
    static AlertDialog last;
    static AlertDialog entry;

    static void show(final MainActivity app) {
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app).setTitle("Logs").setView(scroll).setPositiveButton("Close", null).create();
        fill(app, col);
        last.show();
    }

    static void fill(final MainActivity app, final LinearLayout col) {
        col.removeAllViews();
        LinearLayout chips = app.row();
        String[] types = new String[Logs.TYPES.length + 1];
        types[0] = Logs.ALL;
        System.arraycopy(Logs.TYPES, 0, types, 1, Logs.TYPES.length);
        for (final String t : types) {
            TextView chip = app.pill(t, t.equals(type), v -> {
                type = t;
                fill(app, col);
            });
            chip.setTag("logs-type:" + t);
            chips.addView(chip);
        }
        col.addView(app.chipStrip(chips));
        final List<Logs.Entry> entries = Logs.entries(type);
        TextView state = app.text(entries.size() + " entries" + (Logs.on() ? "" : " \u2014 Logging is off (General settings > Logging on): nothing new is written"), 12, false);
        state.setTag("logs-state");
        state.setPadding(0, app.dp(6), 0, app.dp(6));
        col.addView(state);
        LinearLayout buttons = app.row();
        TextView all = app.outline("Export all", false, v -> {
            if (entries.isEmpty()) app.setNow("No log entries to export");
            else SaveText.save(app, Logs.exportName(type), Logs.joined(entries));
        });
        all.setTag("logs-export-all");
        buttons.addView(all);
        TextView refresh = app.outline("Refresh", false, v -> fill(app, col));
        refresh.setTag("logs-refresh");
        buttons.addView(refresh);
        col.addView(buttons);
        if (entries.isEmpty()) col.addView(app.hint(Logs.EMPTY));
        for (final Logs.Entry e : entries) {
            TextView line = app.text(e.label(), 13, false);
            line.setTag("logs-entry:" + e.file.getName());
            line.setPadding(0, app.dp(8), 0, app.dp(8));
            line.setOnClickListener(v -> open(app, e, () -> fill(app, col)));
            col.addView(line);
        }
    }

    /** One entry: its text (selectable), Copy, Export, Delete. */
    static void open(final MainActivity app, final Logs.Entry e, final Runnable deleted) {
        final String text = Logs.text(e);
        TextView body = app.text(text, 11, false);
        body.setTag("logs-text");
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(app.dp(14), app.dp(8), app.dp(14), app.dp(8));
        ScrollView scroll = new ScrollView(app);
        scroll.addView(body);
        if (entry != null && entry.isShowing()) entry.dismiss();
        entry = new AlertDialog.Builder(app).setTitle(e.type + ": " + e.title).setView(scroll)
            .setPositiveButton("Copy", (d, w) -> {
                android.content.ClipboardManager clips = (android.content.ClipboardManager) app.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                clips.setPrimaryClip(android.content.ClipData.newPlainText("Pulsekit log", text));
                app.setNow("Copied the log entry (" + text.length() + " characters)");
            })
            .setNeutralButton("Export", (d, w) -> SaveText.save(app, Logs.exportName(e.type), text))
            .setNegativeButton("Delete", (d, w) -> {
                app.setNow("Deleted " + Logs.delete(java.util.Collections.singletonList(e)) + " log entry");
                deleted.run();
            })
            .create();
        entry.show();
    }
}
