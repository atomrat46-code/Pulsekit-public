package pulsekit;

import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * General settings > Storage (Storage): what Pulsekit's folders take, Clear temporary files, and
 * how old a temporary file gets before Pulsekit deletes it when it starts.
 */
final class StorageScreen {
    private StorageScreen() {}

    /** What the last clearing said, and the question asked last, for the tests. */
    static volatile String lastSaid;
    static AlertDialog lastAsk;
    /** The tests: the sizes counted last. */
    static volatile String lastSizes;

    static List<Storage.Place> places(MainActivity app) {
        File files = app.getFilesDir(), cache = app.getCacheDir();
        File logs = Logs.dir();
        List<Storage.Place> out = new ArrayList<Storage.Place>();
        out.add(new Storage.Place("Prompt library (all DBs)", files, false).dbOnly());
        out.add(new Storage.Place("Settings, songs, patterns and playlists", files, false).noDb().skip(logs.getName()));
        out.add(new Storage.Place("Logs", logs, false));
        out.add(new Storage.Place("Pulsekit's tools (Java compiler and runtime)", app.getDir("art", 0).getParentFile(), false).prefix("app_"));
        out.add(new Storage.Place("Program inputs and outputs", new File(cache, "pyjav-in"), true));
        out.add(new Storage.Place("Playlist cache", cache, true).prefix("playlist-"));
        out.add(new Storage.Place("Other temporary files", cache, true).skip("pyjav-in", "playlist-cache", "playlist-merge"));
        return out;
    }

    /** The Storage section of General settings. */
    static void addTo(final MainActivity app, LinearLayout col, List<android.view.View> controls) {
        col.addView(GeneralSettings.headline(app, Storage.TITLE));
        final TextView sizes = app.text("Counting…", 13, false);
        sizes.setTag("storage-sizes");
        col.addView(sizes);
        count(app, sizes);
        TextView clear = app.pill(Storage.CLEAR, false, v -> askClear(app, sizes));
        clear.setTag("storage-clear");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = app.dp(6);
        col.addView(clear, lp);
        col.addView(app.text(Storage.AUTO, 13, false));
        final Spinner days = new Spinner(app);
        days.setTag("storage-days");
        String[] labels = new String[Storage.DAYS.length];
        int at = 0;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Storage.daysLabel(Storage.DAYS[i]);
            if (Storage.DAYS[i] == AppSettings.tempDays()) at = i;
        }
        days.setAdapter(new ArrayAdapter<String>(app, android.R.layout.simple_spinner_dropdown_item, labels));
        days.setSelection(at);
        days.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                if (Storage.DAYS[pos] == AppSettings.tempDays()) return;
                AppSettings.setTempDays(Storage.DAYS[pos]);
                app.setNow(Storage.DAYS[pos] == 0 ? "Temporary files are kept until Clear temporary files"
                    : "Temporary files older than " + Storage.daysLabel(Storage.DAYS[pos]) + " are deleted when Pulsekit starts");
            }

            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        col.addView(days);
        controls.add(clear);
        controls.add(days);
        TextView note = app.text(Storage.NOTE, 12, false);
        note.setPadding(0, app.dp(4), 0, 0);
        col.addView(note);
    }

    /** The sizes counted off the screen's thread (a big input folder takes a moment). */
    static void count(final MainActivity app, final TextView into) {
        new Thread(() -> {
            final String text = String.join("\n", Storage.lines(places(app)));
            app.handler.post(() -> {
                lastSizes = text;
                into.setText(text);
            });
        }, "pulsekit-storage").start();
    }

    static void askClear(final MainActivity app, final TextView sizes) {
        if (PyJav.KEEPING.get() > 0) {
            said(app, "A program or a DB task is running: Clear temporary files when it is done");
            return;
        }
        lastAsk = new AlertDialog.Builder(app).setTitle(Storage.CLEAR).setMessage(Storage.ASK)
            .setPositiveButton("Delete", (d, w) -> {
                sizes.setText("Deleting…");
                new Thread(() -> {
                    final String text = Storage.said(Storage.clear(places(app), Long.MAX_VALUE), null);
                    app.handler.post(() -> {
                        said(app, text);
                        count(app, sizes);
                    });
                }, "pulsekit-storage-clear").start();
            })
            .setNegativeButton("Cancel", null).show();
    }

    /** When Pulsekit starts: the temporary files older than the days set deleted (off the screen's thread). */
    static void startup(final MainActivity app) {
        final int days = AppSettings.tempDays();
        if (days <= 0) return;
        new Thread(() -> {
            String text = Storage.clearOld(places(app), days);
            if (text == null) return;
            Logs.write(Logs.STARTS, text, text);
            app.handler.post(() -> app.setNow(text));
        }, "pulsekit-storage-startup").start();
    }

    static void said(MainActivity app, String text) {
        lastSaid = text;
        app.setNow(text);
        android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_LONG).show();
    }
}
