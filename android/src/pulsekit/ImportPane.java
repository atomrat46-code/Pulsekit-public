package pulsekit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The "Import" screen: file-type legend, a Choose file button, the imported
 * file list, and the Dub pack shortcut. Extracted from MainActivity.buildUi()'s
 * inline importPane construction. File parsing and plugin logic stay on
 * MainActivity.
 */
final class ImportPane extends LinearLayout {

    ImportPane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        addView((View) app.text("Import", 18, true));

        TextView legend = app.text("PRJ \u00b7 full project\nPKP \u00b7 plugin pack\nFSET \u00b7 patterns, Fillerns, and fills from one imported file\nMIDI \u00b7 pattern, Fillern or fill (named in the file)\nSNG \u00b7 song\nWAV / MP3 \u00b7 isolate kick, snare, toms, hats, ride, crash\nSF2 \u00b7 drum samples onto pads\nPY \u00b7 Python script to edit", 14, false);
        legend.setTextColor(MainActivity.MUTED);
        legend.setPadding(0, app.dp(8), 0, app.dp(16));
        addView((View) legend);

        addView((View) app.action("Choose file", MainActivity.FG, MainActivity.BG, view -> app.openFile()));

        app.importedFileList = app.col();
        app.importedFileList.setPadding(0, app.dp(12), 0, 0);
        addView((View) app.importedFileList);

        TextView dubPack = app.action("Try Dub pack", MainActivity.ELEV, MainActivity.FG, view -> app.tryDub());
        LinearLayout.LayoutParams lp = app.wrap();
        lp.setMargins(0, app.dp(8), 0, 0);
        dubPack.setLayoutParams((ViewGroup.LayoutParams) lp);
        addView((View) dubPack);
    }
}
