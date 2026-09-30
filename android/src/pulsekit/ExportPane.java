package pulsekit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * The "Export" screen: grouped export buttons for the project, MIDI, the
 * current beat, the song, and the Python script. Extracted from
 * MainActivity.buildUi()'s inline exportPane construction. The save*()
 * methods it calls do the actual work and stay on MainActivity.
 */
final class ExportPane extends LinearLayout {

    ExportPane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        addView((View) app.text("Export", 18, true));

        addView((View) app.hint("Project"));
        LinearLayout projectRow = app.row();
        projectRow.addView((View) app.action("PRJ", MainActivity.HIT, MainActivity.BG, view -> app.saveKind(15)), (ViewGroup.LayoutParams) app.flexBtn());
        projectRow.addView((View) app.action("PKP", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(16)), (ViewGroup.LayoutParams) app.flexBtn());
        projectRow.addView((View) app.action("FSET", MainActivity.ELEV, MainActivity.FG, view -> app.saveFset(null)), (ViewGroup.LayoutParams) app.flexBtn());
        addView((View) projectRow);

        addView((View) app.hint("MIDI"));
        LinearLayout midiRow = app.row();
        midiRow.addView((View) app.action("Pattern", MainActivity.ELEV, MainActivity.FG, view -> app.saveMidi("pattern")), (ViewGroup.LayoutParams) app.flexBtn());
        midiRow.addView((View) app.action("Fillern", MainActivity.ELEV, MainActivity.FG, view -> app.saveMidi("fillern")), (ViewGroup.LayoutParams) app.flexBtn());
        midiRow.addView((View) app.action("Fill", MainActivity.ELEV, MainActivity.FG, view -> app.saveMidi("fill")), (ViewGroup.LayoutParams) app.flexBtn());
        addView((View) midiRow);

        addView((View) app.hint("This beat"));
        LinearLayout beatRow1 = app.row();
        beatRow1.addView((View) app.action("WAV", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(10)), (ViewGroup.LayoutParams) app.flexBtn());
        beatRow1.addView((View) app.action("MP3", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(11)), (ViewGroup.LayoutParams) app.flexBtn());
        addView((View) beatRow1);

        LinearLayout beatRow2 = app.row();
        beatRow2.setPadding(0, app.dp(8), 0, 0);
        beatRow2.addView((View) app.action("SF2", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(12)), (ViewGroup.LayoutParams) app.flexBtn());
        beatRow2.addView((View) app.action("JAR", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(18)), (ViewGroup.LayoutParams) app.flexBtn());
        addView((View) beatRow2);

        addView((View) app.hint("Song"));
        LinearLayout songRow = app.row();
        songRow.addView((View) app.action("SNG", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(9)), (ViewGroup.LayoutParams) app.flexBtn());
        songRow.addView((View) app.action("Song MIDI", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(17)), (ViewGroup.LayoutParams) app.flexBtn());
        addView((View) songRow);

        addView((View) app.hint("Python"));
        LinearLayout pyRow = app.row();
        pyRow.addView((View) app.action("PY", MainActivity.ELEV, MainActivity.FG, view -> app.saveKind(14)), (ViewGroup.LayoutParams) app.flexBtn());
        addView((View) pyRow);
    }
}
