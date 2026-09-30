package pulsekit;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The "Song" screen: Edit/Play mode, Original/Imported lane, the add-part pill
 * row, timeline, and the song card list. Extracted from MainActivity.buildUi()'s
 * inline songPane construction.
 *
 * The actual song-editing logic (add, refreshSong, persistence) stays on
 * MainActivity, since other screens also feed into the song. This class only
 * builds the screen and wires its controls to those shared methods.
 */
final class SongPane extends LinearLayout {

    SongPane(MainActivity app) {
        super((Context) app);
        setOrientation(VERTICAL);
        setVisibility(GONE);

        LinearLayout modeRow = app.row();
        TextView editPill = app.pill("Edit", true, view -> {
            app.songMode = "edit";
            app.chrome.setVisibility(VISIBLE);
            app.styleWrap.setVisibility(GONE);
            app.fillWrap.setVisibility(GONE);
            app.knobsRow.setVisibility(GONE);
            app.refreshSong();
        });
        editPill.setTag("edit");
        TextView playPill = app.pill("Play", false, view -> {
            app.songMode = "play";
            app.chrome.setVisibility(GONE);
            app.refreshSong();
        });
        playPill.setTag("play");
        modeRow.addView((View) editPill);
        modeRow.addView((View) playPill);
        addView((View) modeRow);

        LinearLayout laneRow = app.row();
        TextView origLane = app.pill("Original", true, view -> {
            app.songLane = "original";
            app.refreshSong();
        });
        origLane.setTag("original");
        TextView impLane = app.pill("Imported", false, view -> {
            app.songLane = "imported";
            if (app.importedSongId == null && !app.importedSongs.isEmpty()) {
                app.importedSongId = app.importedSongs.get(0).id;
            }
            app.refreshSong();
        });
        impLane.setTag("imported");
        laneRow.addView((View) origLane);
        laneRow.addView((View) impLane);
        addView((View) laneRow);

        app.songAdds = app.row();
        TextView addPattern = app.pill("Pattern \u00d74", false, view -> {
            Engine.Part part = Engine.groove(app.styles.get(app.style).label, app.bpm(), app.cells, 4);
            part.lens = Engine.copyCells(app.lens);
            app.add(part);
        });
        app.attachSongPick(addPattern, -1, "pattern");
        TextView addFillern = app.pill("Fillern", false, view -> app.addCurrentFillern());
        app.attachSongPick(addFillern, -1, "fillern");
        TextView addFill = app.pill("Fill", false, view -> {
            Engine.Part part = Engine.fill(app.fillLabel(app.fillId), app.bpm(), app.fillPat, 1);
            part.lens = Engine.copyCells(app.fillLens);
            app.add(part);
        });
        app.attachSongPick(addFill, -1, "fill");
        app.songAdds.addView((View) addPattern);
        app.songAdds.addView((View) addFillern);
        app.songAdds.addView((View) addFill);
        app.songAdds.addView((View) app.pill("Silent", false, view -> app.add(Engine.rest(app.bpm(), 2))));
        app.songAdds.addView((View) app.pill("Clear", false, view -> {
            if ("imported".equals(app.songLane) && app.importedSongId != null) {
                String id = app.importedSongId;
                app.importedSongs.removeIf(importedSong -> id.equals(importedSong.id));
                app.importedSongId = app.importedSongs.isEmpty() ? null : app.importedSongs.get(0).id;
                app.persistLearned();
            } else {
                app.song.clear();
            }
            app.refreshSong();
        }));
        HorizontalScrollView addsScroll = new HorizontalScrollView((Context) app);
        addsScroll.setHorizontalScrollBarEnabled(false);
        addsScroll.addView((View) app.songAdds);
        addView((View) addsScroll);

        HorizontalScrollView timelineScroll = new HorizontalScrollView((Context) app);
        timelineScroll.setHorizontalScrollBarEnabled(false);
        app.timeline = app.row();
        timelineScroll.addView((View) app.timeline);
        addView((View) timelineScroll);

        ScrollView cardsScroll = new ScrollView((Context) app);
        app.songCards = app.col();
        cardsScroll.addView((View) app.songCards);
        addView((View) cardsScroll, (ViewGroup.LayoutParams) app.flexFill());
    }
}
