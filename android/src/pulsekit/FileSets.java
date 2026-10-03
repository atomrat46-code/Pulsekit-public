package pulsekit;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

import static pulsekit.MainActivity.*;

/** File sets: the Info page, style changes, songs from sets, and source-MIDI playback. */
final class FileSets {
    final MainActivity app;

    FileSets(MainActivity app) {
        this.app = app;
    }

    // ------------------------------------------------------------------
    // Features that used to be injected with Javassist (android/patch).
    // Analyze, Compose, Prompts, file-set Info, stem/MIDI playback, PyJav.
    // ------------------------------------------------------------------

    private android.media.MediaPlayer pkMidiPlayer;

    java.lang.String pkMidiSrc;

    boolean pkMidiPaused;

    android.widget.LinearLayout infoRows;

    android.widget.TextView infoTitle;

    java.lang.String pkInfoBack;

    void wireFileSetListActions() {
        this.wireFileSetListActionsCore();
        this.pkRebindFileSetClicks();
    }


    java.lang.String fmtAnalyzeTime(float sec) {
        int s = Math.max(0, Math.round(sec));
        return (s / 60) + ":" + java.lang.String.format("%02d", new java.lang.Object[] { java.lang.Integer.valueOf(s % 60) });
    }

    void ensureFsetInfoMap() {
        if (app.fsetInfoMap == null) app.fsetInfoMap = new java.util.LinkedHashMap();
    }

    void persistFsetInfo() {
        try {
            this.ensureFsetInfoMap();
            java.io.File f = new java.io.File(app.getFilesDir(), "fset-info.json");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            out.write(pulsekit.Engine.encodeFsetInfo(app.fsetInfoMap).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.close();
        } catch (java.lang.Exception ignored) {}
    }

    void loadPersistedFsetInfo() {
        try {
            this.ensureFsetInfoMap();
            java.io.File f = new java.io.File(app.getFilesDir(), "fset-info.json");
            if (!f.isFile() || f.length() < 8) return;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            in.close();
            if (n <= 0) return;
            java.lang.String json = new java.lang.String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
            app.fsetInfoMap.clear();
            app.fsetInfoMap.putAll(pulsekit.Engine.decodeFsetInfo(json));
            pulsekit.Engine.fileSetOrigins.clear();
            pulsekit.Engine.fileSetOrigins.putAll(pulsekit.Engine.decodeFsetOrigins(json));
            pulsekit.Engine.loadFileSetSongs(json);
            pulsekit.Engine.loadFileSetAudioDir(new java.io.File(app.getFilesDir(), "fset-audio"));
        } catch (java.lang.Exception ignored) {}
    }

    void storeFsetParts(java.lang.String source, java.util.List parts) {
        this.ensureFsetInfoMap();
        if (source == null) return;
        if (parts == null || parts.isEmpty()) app.fsetInfoMap.remove(source);
        else app.fsetInfoMap.put(source, new java.util.ArrayList(parts));
        this.persistFsetInfo();
    }

    static java.lang.String mixTime(int ms) {
        if (ms < 0) ms = 0;
        int s = ms / 1000;
        int m = s / 60;
        s = s % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    void writePcmFile(java.io.File f, short[] pcm) throws java.lang.Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        out.write(pulsekit.AudioIo.encodeWav(pcm, 22050));
        out.close();
    }

    void releasePlayer(android.media.MediaPlayer p) {
        if (p == null) return;
        try { p.stop(); } catch (java.lang.Exception ignored) {}
        try { p.release(); } catch (java.lang.Exception ignored) {}
    }

    void paintMidiClockOn(android.view.View v, java.lang.String label) {
        if (v instanceof android.widget.TextView && "midiclock".equals(v.getTag())) {
            ((android.widget.TextView) v).setText(label);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) this.paintMidiClockOn(g.getChildAt(i), label);
        }
    }

    void paintMidiClock(java.lang.String label) {
        android.view.View root = app.getWindow() == null ? null : app.getWindow().getDecorView();
        if (root != null) this.paintMidiClockOn(root, label);
    }

    public void tickMidiClock() {
        android.media.MediaPlayer mp = this.pkMidiPlayer;
        if (mp == null) return;
        int pos = 0;
        int dur = 0;
        boolean going = false;
        try {
            dur = mp.getDuration();
            pos = mp.getCurrentPosition();
            going = mp.isPlaying();
        } catch (java.lang.Exception ex) { return; }
        this.paintMidiClock(mixTime(pos) + " / " + mixTime(dur));
        if (going && this.pkMidiClock != null) app.handler.postDelayed(this.pkMidiClock, 200L);
    }

    /** Repaints the source-MIDI clock every 200 ms while it plays. */
    Runnable pkMidiClock;

    void startMidiClock() {
        this.stopMidiClock();
        this.pkMidiClock = this::tickMidiClock;
        app.handler.post(this.pkMidiClock);
    }

    void stopMidiClock() {
        if (this.pkMidiClock != null) app.handler.removeCallbacks(this.pkMidiClock);
        this.pkMidiClock = null;
    }

    public void stopSourceMidi() {
        this.stopMidiClock();
        android.media.MediaPlayer mp = this.pkMidiPlayer;
        this.pkMidiPlayer = null;
        this.pkMidiPaused = false;
        this.pkMidiSrc = null;
        if (mp != null) this.releasePlayer(mp);
        this.paintMidiClock("00:00 / 00:00");
        app.setNow("Stopped");
    }

    public void pauseSourceMidi() {
        android.media.MediaPlayer mp = this.pkMidiPlayer;
        if (mp == null) return;
        try {
            if (mp.isPlaying()) mp.pause();
            this.pkMidiPaused = true;
            this.paintMidiClock(mixTime(mp.getCurrentPosition()) + " / " + mixTime(mp.getDuration()));
            app.setNow("Paused");
        } catch (java.lang.Exception ex) {
            java.lang.String m = ex.getMessage();
            app.setNow(m != null ? m : "Could not pause");
        }
    }

    public void playSourceMidi(java.lang.String src) {
        if (src != null && src.equals(this.pkMidiSrc) && this.pkMidiPlayer != null && this.pkMidiPaused) {
            try {
                this.pkMidiPlayer.start();
                this.pkMidiPaused = false;
                this.startMidiClock();
                app.setNow("Play");
                return;
            } catch (java.lang.Exception ignored) {}
        }
        this.stopSourceMidi();
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if (au == null || au.sourceMidi == null || au.sourceMidi.length < 14) {
            app.setNow("That MIDI file is missing");
            return;
        }
        try {
            short[] pcm = pulsekit.AudioIo.renderMidiDrums(au.sourceMidi, app.playback.mixVoices(), 22050);
            if (pcm == null || pcm.length < 8) {
                app.setNow("No drum notes in that MIDI");
                return;
            }
            java.io.File f = new java.io.File(app.getCacheDir(), "pk-midi.wav");
            this.writePcmFile(f, pcm);
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            mp.start();
            this.pkMidiPlayer = mp;
            this.pkMidiSrc = src;
            this.pkMidiPaused = false;
            this.startMidiClock();
            app.setNow("Play \u00b7 " + pulsekit.Engine.fileSetMidiName(src));
        } catch (java.lang.Throwable ex) {
            java.lang.String m = ex.getMessage();
            app.setNow(m != null ? m : "Could not play that MIDI");
        }
    }

    void attachMidiChip(android.widget.LinearLayout host, java.lang.String key) {
        if (host == null || key == null || !key.startsWith("f:")) return;
        java.lang.String src = key.substring(2);
        java.lang.String name = pulsekit.Engine.fileSetMidiName(src);
        if (name.length() == 0) return;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            android.view.View v = host.getChildAt(i);
            if ("sourcemidi".equals(v.getTag())) return;
            if (v instanceof android.widget.TextView && "pack".equals(v.getTag())) break;
        }
        android.widget.LinearLayout line = app.row();
        line.setTag("sourcemidi");
        line.setPadding(app.dp(8), 0, 0, app.dp(4));
        android.widget.TextView label = app.text(name, 12, false);
        label.setTextColor(MUTED);
        line.addView(label, app.flex(1));
        android.widget.TextView clock = app.text("00:00 / 00:00", 11, false);
        clock.setTag("midiclock");
        clock.setTextColor(MUTED);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.addView(clock);
        line.addView(app.outline("Play", false, pulsekit.FileSetClicks.filePlayMidi(app, src)));
        line.addView(app.outline("Pause", false, pulsekit.FileSetClicks.filePauseMidi(app)));
        line.addView(app.outline("Stop", false, pulsekit.FileSetClicks.fileStopMidi(app)));
        host.addView(line);
        android.widget.LinearLayout results = this.pkResultsRow(src);
        if (results != null) host.addView(results);
    }

    /** True when the file set keeps a source MIDI, so Compare hits has something to compare. */
    boolean hasSourceMidi(java.lang.String key) {
        java.lang.String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        return pulsekit.Engine.fileSetMidiName(pulsekit.Engine.fileSetMidiKeyForLabel(src)).length() > 0;
    }

    /**
     * File set menu > Compare hits: the set's source MIDI against its song and the original WAV
     * (kept with the set, or the last WAV given to PyJav), as CompareHits --log writes it. The
     * results are kept with the file set as CompareHits_test_results.txt.
     */
    void compareFileSetHits(java.lang.String key, final java.lang.String label) {
        java.lang.String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        final java.lang.String midiKey = pulsekit.Engine.fileSetMidiKeyForLabel(src);
        final byte[] midi = pulsekit.HitCompare.fileSetMidi(midiKey);
        if (midi == null || midi.length < 14) {
            app.setNow("This file set keeps no source MIDI");
            return;
        }
        final java.lang.String midiName = pulsekit.Engine.fileSetMidiName(midiKey);
        pulsekit.Engine.ImportedSong found = pulsekit.HitCompare.songFor(src, app.importedSongs);
        if (found == null && !midiKey.equals(src)) found = pulsekit.HitCompare.songFor(midiKey, app.importedSongs);
        final pulsekit.Engine.ImportedSong song = found;
        final byte[] kept = pulsekit.HitCompare.fileSetWav(midiKey);
        final java.io.File last = kept == null ? app.compareHits.lastPyJavWav() : null;
        final java.lang.String shown = label == null || label.length() == 0 ? (src.length() == 0 ? "Other" : src) : label;
        app.setNow("Comparing hits \u00b7 " + shown);
        new java.lang.Thread(() -> {
            java.lang.String text;
            boolean ok = true;
            try {
                byte[] w = kept != null ? kept : (last != null ? pulsekit.CompareHitsPage.readFile(last) : null);
                java.lang.String wavName = kept != null ? "source.wav (kept with the file set)" : (last != null ? last.getName() : "");
                text = pulsekit.HitCompare.fileSetLog(shown, midi, midiName, song, w == null ? null : pulsekit.AudioIo.parseWav(w), wavName);
            } catch (java.lang.Throwable ex) {
                ok = false;
                text = "Could not compare: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            final java.lang.String result = text;
            final boolean saved = ok;
            app.runOnUiThread(() -> {
                if (saved) {
                    pulsekit.Engine.rememberFileSetResults(midiKey, result);
                    pulsekit.Engine.storeFileSetAudioDir(new java.io.File(app.getFilesDir(), "fset-audio"));
                    app.importLibrary.rebuildImported();
                }
                this.showResults(shown, result);
                app.setNow(saved ? "Compare hits \u00b7 " + shown + " \u00b7 " + pulsekit.HitCompare.RESULTS_FILE : "Could not compare hits");
            });
        }).start();
    }

    /**
     * The results text in a dialog as wide as the screen. The tables fit across (the text size
     * shrinks to fit), longer sentences wrap, and the text scrolls inside a box of at most 60% of
     * the screen height, so the Save and Close buttons below it always show.
     */
    void showResults(java.lang.String title, final java.lang.String text) {
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        int width = dm.widthPixels;
        android.widget.TextView body = app.text(text, 11, false);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextColor(FG);
        body.setTag("fileset-results-text");
        body.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        pulsekit.SaveText.fitTables(body, text, width - app.dp(24 + 24));
        android.widget.ScrollView tall = new android.widget.ScrollView(app);
        tall.setVerticalScrollBarEnabled(true);
        tall.setScrollbarFadingEnabled(false);
        tall.addView(body);
        // Height: the text's own, up to 60% of the screen.
        body.measure(android.view.View.MeasureSpec.makeMeasureSpec(width - app.dp(48), android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        int boxHeight = Math.min(body.getMeasuredHeight(), (int) (dm.heightPixels * 0.6f));
        android.widget.LinearLayout content = app.col();
        content.addView(tall, new android.widget.LinearLayout.LayoutParams(-1, Math.max(app.dp(120), boxHeight)));
        android.widget.LinearLayout buttons = app.row();
        buttons.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(12));
        final android.app.AlertDialog[] holder = new android.app.AlertDialog[1];
        android.widget.TextView save = app.action("Save as " + pulsekit.HitCompare.RESULTS_FILE, ELEV, FG, v -> {
            if (holder[0] != null) holder[0].dismiss();
            pulsekit.SaveText.save(app, pulsekit.HitCompare.RESULTS_FILE, text);
        });
        save.setTag("results-save");
        android.widget.TextView close = app.action("Close", HIT, BG, v -> {
            if (holder[0] != null) holder[0].dismiss();
        });
        close.setTag("results-close");
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(0, app.dp(44), 1f);
        lp.setMargins(0, 0, app.dp(8), 0);
        buttons.addView(save, lp);
        buttons.addView(close, new android.widget.LinearLayout.LayoutParams(app.dp(96), app.dp(44)));
        content.addView(buttons);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(app)
            .setTitle((java.lang.CharSequence) ("Compare hits \u00b7 " + title))
            .setView(content)
            .show();
        holder[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(width - app.dp(16), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** CompareHits_test_results.txt under the file set, with Open and Save. Null when there are none. */
    android.widget.LinearLayout pkResultsRow(java.lang.String src) {
        final java.lang.String key = pulsekit.Engine.fileSetMidiKeyForLabel(src == null ? "" : src);
        final java.lang.String text = pulsekit.Engine.fileSetResults(key);
        if (text == null) return null;
        android.widget.LinearLayout line = app.row();
        line.setTag("fileset-results");
        line.setPadding(app.dp(8), 0, 0, app.dp(4));
        android.widget.TextView label = app.text(pulsekit.HitCompare.RESULTS_FILE, 12, false);
        label.setTextColor(MUTED);
        line.addView(label, app.flex(1));
        line.addView(app.outline("Open", false, v -> this.showResults(key.length() == 0 ? "Other" : key, text)));
        line.addView(app.outline("Save", false, v -> pulsekit.SaveText.save(app, pulsekit.HitCompare.RESULTS_FILE, text)));
        return line;
    }

    void stampPackMark(android.widget.TextView pack, java.lang.String source) {
        if (pack == null || source == null) return;
        java.lang.String mark = pulsekit.Engine.fileSetOriginMark(pulsekit.Engine.fileSetOriginOf(source));
        if (mark.length() == 0) return;
        java.lang.CharSequence cs = pack.getText();
        java.lang.String raw = cs == null ? "" : cs.toString();
        boolean arrow = raw.startsWith("\u25be ") || raw.startsWith("\u25b8 ");
        java.lang.String rest = arrow ? raw.substring(2) : raw;
        if (rest.startsWith(mark + " ")) return;
        pack.setText((arrow ? raw.substring(0, 2) : "") + mark + " " + rest);
    }

    void attachFileSetInfoMenu(android.widget.LinearLayout host, java.lang.String key, java.lang.String label, java.lang.Runnable onDelete, java.lang.Runnable onExport) {
        if (host == null) return;
        android.widget.TextView pack = null;
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            android.view.View v = host.getChildAt(i);
            if (v instanceof android.widget.TextView && "pack".equals(v.getTag())) {
                pack = (android.widget.TextView) v;
                break;
            }
        }
        if (pack == null) return;
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        this.stampPackMark(pack, src);
        pack.setOnLongClickListener(pulsekit.FileSetClicks.packMenu(app, key, label, onDelete, onExport));
        this.attachMidiChip(host, key);
    }

    void wireFileSetListActionsCore() {
        if (app.importedFileList == null) return;
        for (int i = 0; i < app.importedFileList.getChildCount(); i++) {
            android.view.View child = app.importedFileList.getChildAt(i);
            if (!(child instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout row = (android.widget.LinearLayout) child;
            android.widget.TextView exportBtn = null;
            android.widget.TextView packHead = null;
            boolean hasMake = false;
            for (int j = 0; j < row.getChildCount(); j++) {
                android.view.View v = row.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                android.widget.TextView t = (android.widget.TextView) v;
                java.lang.CharSequence cs = t.getText();
                java.lang.String s = cs == null ? "" : cs.toString();
                if ("Export".equals(s)) exportBtn = t;
                else if ("Make song".equals(s)) hasMake = true;
                else if (s.indexOf(" \u00b7 ") >= 0) packHead = t;
            }
            if (exportBtn == null || packHead == null || hasMake) continue;
            java.lang.String head = packHead.getText().toString();
            if (head.startsWith("\u25be ") || head.startsWith("\u25b8 ")) head = head.substring(2);
            int cut = head.lastIndexOf(" \u00b7 ");
            java.lang.String label = cut > 0 ? head.substring(0, cut) : head;
            java.lang.String src = "Other".equals(label) ? "" : label;
            java.lang.String key = src.length() == 0 ? "o:other" : "f:" + src;
            this.stampPackMark(packHead, src);
            android.widget.TextView make = app.outline("Make song", false, pulsekit.FileSetClicks.fileMakeSong(app, key, label));
            row.addView(make, row.indexOfChild(exportBtn));
            if (pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src))) {
                android.widget.TextView style = app.outline("Change style", false, pulsekit.FileSetClicks.fileChangeStyle(app, key, label));
                row.addView(style, row.indexOfChild(exportBtn));
            }
        }
        this.appendMidiFileRows();
    }

    void pkRebindFileSetClicks() {
        if (app.importedFileList == null) return;
        android.view.View.OnClickListener click = pulsekit.FileSetClicks.fileEntry(app);
        java.lang.String src = "";
        for (int i = 0; i < app.importedFileList.getChildCount(); i++) {
            android.view.View child = app.importedFileList.getChildAt(i);
            if (!(child instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout box = (android.widget.LinearLayout) child;
            boolean header = false;
            android.widget.TextView packHead = null;
            for (int j = 0; j < box.getChildCount(); j++) {
                android.view.View v = box.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                if ("Export".equals(s)) header = true;
                if (s.startsWith("\u25be ") || s.startsWith("\u25b8 ")) packHead = (android.widget.TextView) v;
            }
            if (header) {
                src = "";
                if (packHead != null && packHead.getText() != null) {
                    java.lang.String head = packHead.getText().toString();
                    if (head.startsWith("\u25be ") || head.startsWith("\u25b8 ")) head = head.substring(2);
                    int cut = head.lastIndexOf(" \u00b7 ");
                    java.lang.String label = cut > 0 ? head.substring(0, cut) : head;
                    if (label.startsWith("EP ")) label = label.substring(3);
                    else if (label.startsWith("A ") || label.startsWith("M ") || label.startsWith("I ") || label.startsWith("C ")) label = label.substring(2);
                    src = "Other".equals(label) ? "" : label;
                }
                continue;
            }
            int pi = 0;
            int fi = 0;
            for (int j = 0; j < box.getChildCount(); j++) {
                android.view.View v = box.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                if (s.startsWith("Pattern")) {
                    while (pi < app.learned.size()) {
                        pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) app.learned.get(pi);
                        pi = pi + 1;
                        if (item != null && src.equals(pulsekit.Engine.sourceOf(item))) {
                            v.setTag("fp:" + item.id);
                            v.setOnClickListener(click);
                            v.setClickable(true);
                            break;
                        }
                    }
                } else if (s.startsWith("Fill ") || s.startsWith("Fill\u00b7")) {
                    while (fi < app.learnedFills.size()) {
                        pulsekit.Engine.LearnedFill item = (pulsekit.Engine.LearnedFill) app.learnedFills.get(fi);
                        fi = fi + 1;
                        if (item != null && src.equals(pulsekit.Engine.sourceOf(item))) {
                            v.setTag("ff:" + item.id);
                            v.setOnClickListener(click);
                            v.setClickable(true);
                            break;
                        }
                    }
                }
            }
            for (int n = 0; n < app.learned.size(); n++) {
                pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) app.learned.get(n);
                if (item == null || !src.equals(pulsekit.Engine.sourceOf(item))) continue;
                java.lang.String pair = (java.lang.String) app.fillernPairs.get("l:" + item.id);
                if (pair == null || pair.length() == 0) continue;
                android.widget.TextView t = app.text("Fillern \u00b7 " + item.name, 13, false);
                t.setTextColor(MUTED);
                t.setPadding(0, app.dp(4), 0, app.dp(4));
                t.setTag("fr:" + item.id);
                t.setOnClickListener(click);
                t.setClickable(true);
                box.addView(t);
            }
        }
    }

    public void pkLoadFilePattern(java.lang.String id) {
        if (id == null) return;
        for (int i = 0; i < app.learned.size(); i++) {
            pulsekit.Engine.Learned item = (pulsekit.Engine.Learned) app.learned.get(i);
            if (item == null || !id.equals(item.id)) continue;
            app.styles.put(item.id, new pulsekit.Engine.Style(item.id, item.name, item.bpm, pulsekit.Engine.rowsFromCells(item.cells)));
            app.styleLibrary.loadStyle(item.id, false);
            if (item.tsNum > 0) {
                app.tsNum = pulsekit.Engine.clampTsNum(item.tsNum);
                app.tsDen = pulsekit.Engine.clampTsDen(item.tsDen > 0 ? item.tsDen : 4);
                if (app.tsNumField != null) app.tsNumField.setText(java.lang.Integer.toString(app.tsNum));
                if (app.tsDenField != null) app.tsDenField.setText(java.lang.Integer.toString(app.tsDen));
            }
            int steps = item.steps > 0 ? item.steps : pulsekit.Engine.patternLenFromCells(item.cells, app.tsNum, app.tsDen);
            pulsekit.Engine.defaultAccents(app.accents, steps, pulsekit.Engine.stepsPerBeat(app.tsDen));
            app.gridEditor.applySteps(steps, false);
            app.show("pattern");
            app.gridEditor.refreshGrid();
            app.setNow(item.name);
            return;
        }
    }

    public void pkLoadFileFill(java.lang.String id) {
        if (id == null) return;
        app.styleLibrary.applyFill("l:" + id);
        app.show("fills");
        app.gridEditor.refreshGrid();
    }

    public void pkLoadFileFillern(java.lang.String id) {
        if (id == null) return;
        this.pkLoadFilePattern(id);
        app.show("combo");
        app.gridEditor.refreshGrid();
        app.setNow("Fillern \u00b7 " + app.songEditor.patternName(id));
    }

    void showFileSetInfo(java.lang.String label, java.util.List parts, pulsekit.Engine.FileSet set) {
        if (this.infoRows == null) return;
        this.infoRows.removeAllViews();
        if (this.infoTitle != null) this.infoTitle.setText(label == null || label.length() == 0 ? "File set" : label);
        int nFill = set == null ? 0 : set.fills.size();
        int nPair = set == null ? 0 : set.fillerns.size();
        int nPart = parts == null ? 0 : parts.size();
        if (app.infoStatus != null) {
            java.lang.String style = "";
            if (parts != null && !parts.isEmpty()) {
                pulsekit.Engine.FileSetPart first = (pulsekit.Engine.FileSetPart) parts.get(0);
                if (first.styleLabel != null && first.styleLabel.length() > 0) style = first.styleLabel + " · ";
            }
            app.infoStatus.setText(style + (nPart > 0 ? ((pulsekit.Engine.FileSetPart) parts.get(0)).bpm + " BPM · " : "") + nPart + " parts · " + pulsekit.Engine.fileSetLengthLine(parts, set == null ? 0f : set.durationSec) + " · " + nFill + " fills · " + nPair + " Fillerns");
        }
        if (parts != null) {
            for (int i = 0; i < parts.size(); i++) {
                pulsekit.Engine.FileSetPart p = (pulsekit.Engine.FileSetPart) parts.get(i);
                android.widget.LinearLayout card = app.col();
                card.setBackground(app.round(ELEV, 10));
                card.setPadding(app.dp(10), app.dp(8), app.dp(10), app.dp(8));
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, app.dp(8));
                card.setLayoutParams(lp);
                android.widget.LinearLayout head = app.row();
                head.addView(app.text(p.name, 14, true), app.flex(1));
                android.widget.TextView kind = app.text(p.kind, 11, true);
                kind.setTextColor(SUBTLE);
                head.addView(kind);
                card.addView(head);
                card.addView(app.text("BPM " + p.bpm + " · TS " + p.tsNum + "/" + p.tsDen + " · " + (p.styleLabel == null ? "" : p.styleLabel), 12, false));
                android.widget.TextView more = app.text(p.bars + " bars · " + this.fmtAnalyzeTime(p.startSec) + "–" + this.fmtAnalyzeTime(p.endSec) + " · " + p.hits + " hits · swing " + p.swing, 11, false);
                more.setTextColor(SUBTLE);
                card.addView(more);
                this.infoRows.addView(card);
            }
        }
        this.infoRows.requestLayout();
        app.setNow(nPart + " parts");
    }

    public void openFileSetInfo(java.lang.String key, java.lang.String label) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        this.ensureFsetInfoMap();
        java.util.List parts = (java.util.List) app.fsetInfoMap.get(src);
        pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, label, app.learned, app.learnedFills, app.fillernPairs);
        if (parts == null || parts.isEmpty()) {
            if (set != null && set.parts != null && !set.parts.isEmpty()) parts = set.parts;
            else parts = pulsekit.Engine.partsForDisplay(set);
        }
        pulsekit.Engine.unifyFileSetParts(parts, set);
        if (parts != null && !parts.isEmpty()) this.storeFsetParts(src, parts);
        if (!"fsetinfo".equals(app.view)) this.pkInfoBack = app.view;
        java.lang.String shown = pulsekit.Engine.fileSetMarked(label == null || label.length() == 0 ? "File set" : label, pulsekit.Engine.fileSetOriginOf(src));
        this.showFileSetInfo(shown, parts, set);
        java.lang.String word = pulsekit.Engine.fileSetOriginTitle(pulsekit.Engine.fileSetOriginOf(src));
        if (word.length() > 0 && app.infoStatus != null) {
            java.lang.String cur = app.infoStatus.getText().toString();
            if (!cur.startsWith(word)) app.infoStatus.setText(word + " · " + cur);
        }
        app.show("fsetinfo");
        this.pkShowInfoMidi(key != null && key.startsWith("f:") ? key.substring(2) : "");
        this.pkShowInfoSongs(key, label);
    }

    /** Info: the songs connected to the file set, each opening in the Song view; or Make song. */
    void pkShowInfoSongs(final java.lang.String key, final java.lang.String label) {
        if (this.infoRows == null) return;
        java.lang.String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        java.util.List<pulsekit.Engine.ImportedSong> songs = pulsekit.Engine.songsOfFileSet(src, label, app.importedSongs);
        android.widget.LinearLayout box = app.col();
        box.setTag("info-songs");
        box.setPadding(0, 0, 0, app.dp(8));
        box.addView(app.text(songs.size() == 1 ? "Song" : "Songs", 15, true));
        if (songs.isEmpty()) {
            android.widget.LinearLayout line = app.row();
            android.widget.TextView none = app.text("No song from this file set yet", 13, false);
            none.setTextColor(MUTED);
            line.addView(none, app.flex(1));
            line.addView(app.outline("Make song", false, v -> this.makeFileSetSongNow(key, label)));
            box.addView(line);
        }
        for (final pulsekit.Engine.ImportedSong song : songs) {
            android.widget.LinearLayout line = app.row();
            line.setTag("info-song:" + song.name);
            line.setPadding(0, app.dp(2), 0, app.dp(2));
            float sec = pulsekit.Engine.songDurationSec(song.parts);
            android.widget.TextView name = app.text(song.name + "  \u00b7  " + song.parts.size() + " parts \u00b7 "
                + pulsekit.Engine.fmtClock(sec) + (song.fileSetSong != null ? " \u00b7 saved in the set" : ""), 13, false);
            name.setTextColor(FG);
            line.addView(name, app.flex(1));
            line.addView(app.outline("Open", false, v -> this.openSong(song)));
            box.addView(line);
        }
        this.infoRows.addView(box, 0);
    }

    void wireInfoPane() {
        app.infoPane = app.col();
        app.infoPane.setVisibility(8);
        app.infoPane.setBackgroundColor(BG);
        app.infoPane.setClickable(true);
        app.infoPane.setPadding(app.dp(16), app.dp(12), app.dp(16), app.dp(12));
        this.infoTitle = app.text("File set", 18, true);
        app.infoPane.addView(this.infoTitle);
        app.infoStatus = app.text("Hold a file set and choose Info.", 14, false);
        app.infoStatus.setTextColor(MUTED);
        app.infoStatus.setPadding(0, app.dp(8), 0, app.dp(8));
        app.infoPane.addView(app.infoStatus);
        android.widget.LinearLayout.LayoutParams closeLp = new android.widget.LinearLayout.LayoutParams(-1, app.dp(48));
        closeLp.setMargins(0, 0, 0, app.dp(8));
        android.widget.TextView close = app.action("Close", SURFACE, FG, pulsekit.FileSetClicks.closeInfo(app));
        app.infoPane.addView(close, closeLp);
        android.widget.ScrollView scroll = new android.widget.ScrollView(app);
        this.infoRows = app.col();
        scroll.addView(this.infoRows);
        android.widget.LinearLayout.LayoutParams lp = app.flexFill();
        lp.setMargins(0, app.dp(8), 0, 0);
        app.infoPane.addView(scroll, lp);
        android.view.ViewGroup host = null;
        if (app.importPane != null && app.importPane.getParent() instanceof android.view.ViewGroup) {
            host = (android.view.ViewGroup) app.importPane.getParent();
        }
        if (host != null) {
            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(-1, -1);
            host.addView((android.view.View) app.infoPane, (android.view.ViewGroup.LayoutParams) flp);
        }
    }

    public void closeFileSetInfo() {
        java.lang.String back = this.pkInfoBack;
        if (back == null || back.length() == 0 || "fsetinfo".equals(back)) back = "import";
        app.show(back);
    }

    /**
     * Make song (file set menu or info): when the set already has a song, asks whether to replace
     * it, make another, or open it.
     */
    public void makeFileSetSong(final java.lang.String key, final java.lang.String label) {
        java.lang.String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        java.util.List<pulsekit.Engine.ImportedSong> have = pulsekit.Engine.songsOfFileSet(src, label, app.importedSongs);
        if (have.isEmpty()) {
            this.makeFileSetSongNow(key, label);
            return;
        }
        final pulsekit.Engine.ImportedSong first = have.get(0);
        java.lang.String names = first.name + (have.size() > 1 ? " and " + (have.size() - 1) + " more" : "");
        new android.app.AlertDialog.Builder(app)
            .setTitle((java.lang.CharSequence) "This file set already has a song")
            .setMessage((java.lang.CharSequence) (names + ".\n\nReplace it with a new song from the file set, make another song, or open it?"))
            .setPositiveButton((java.lang.CharSequence) "Replace", (d, w) -> this.replaceFileSetSong(first, key, label))
            .setNeutralButton((java.lang.CharSequence) "Make another", (d, w) -> this.makeFileSetSongNow(key, label))
            .setNegativeButton((java.lang.CharSequence) "Open it", (d, w) -> this.openSong(first))
            .show();
    }

    /** The song built from the file set, as Make song makes it; null (with a message) when it cannot be. */
    java.util.List buildFileSetSong(java.lang.String key, java.lang.String label) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, label, app.learned, app.learnedFills, app.fillernPairs);
        if (set == null) {
            app.setNow("That file set is empty");
            return null;
        }
        this.ensureFsetInfoMap();
        java.util.List stored = (java.util.List) app.fsetInfoMap.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        java.util.List song = pulsekit.Engine.songFromFileSet(set);
        if (song == null || song.isEmpty()) {
            app.setNow("Could not make a song from that file set");
            return null;
        }
        return song;
    }

    /** Replace: the song's parts are rebuilt from the file set; its name stays. */
    void replaceFileSetSong(pulsekit.Engine.ImportedSong old, java.lang.String key, java.lang.String label) {
        java.util.List song = this.buildFileSetSong(key, label);
        if (song == null) return;
        java.lang.String src = key != null && key.startsWith("f:") ? key.substring(2) : "";
        old.parts.clear();
        old.parts.addAll(song);
        if (src.length() > 0) old.fileSet = src;
        old.fileSetSong = null;
        app.persistence.persistLearned();
        this.openSong(old);
        app.setNow("Replaced \u00b7 " + old.name);
    }

    /** Shows the song in the Song view (Imported lane). */
    void openSong(pulsekit.Engine.ImportedSong song) {
        app.importedSongId = song.id;
        app.songLane = "imported";
        app.show("song");
        app.songEditor.refreshSong();
    }

    /** Make another: a new song from the file set, without asking. */
    public void makeFileSetSongNow(java.lang.String key, java.lang.String label) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        java.util.List song = this.buildFileSetSong(key, label);
        if (song == null) return;
        java.lang.String name = label;
        if (name == null || name.length() == 0) name = src.length() == 0 ? "Import" : src;
        app.songEditor.addImportedArrangement(name, song);
        // The song belongs to this file set: export names and Compare Hits follow it.
        pulsekit.Engine.ImportedSong made = app.songEditor.importedSong();
        if (made != null && src.length() > 0) {
            made.fileSet = src;
            app.persistence.persistLearned();
        }
    }

    public java.lang.String[] styleDbNames() {
        java.util.List rows = pulsekit.StyleDb.rows();
        java.lang.String[] names = new java.lang.String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            pulsekit.StyleDb.Row row = (pulsekit.StyleDb.Row) rows.get(i);
            names[i] = row.name + "  \u00b7  " + row.bpm;
        }
        return names;
    }

    public int currentStyleDbIndex(java.lang.String key) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        this.ensureFsetInfoMap();
        java.util.List stored = (java.util.List) app.fsetInfoMap.get(src);
        if (stored == null || stored.isEmpty()) {
            pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, "", app.learned, app.learnedFills, app.fillernPairs);
            if (set != null) stored = set.parts;
        }
        if (stored == null) return -1;
        for (int i = 0; i < stored.size(); i++) {
            pulsekit.Engine.FileSetPart p = (pulsekit.Engine.FileSetPart) stored.get(i);
            if (p != null && p.styleLabel != null && p.styleLabel.length() > 0) return pulsekit.StyleDb.indexOf(p.styleLabel);
        }
        return -1;
    }

    public void applyFileSetStyle(java.lang.String key, java.lang.String label, java.lang.String kit, java.lang.String styleName, int hats, float four, float dkick, int styleBpm, float styleBack) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        if (!pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src))) {
            app.setNow("Using the source file");
            return;
        }
        java.lang.String shown = label;
        if (shown == null || shown.length() == 0) shown = src.length() == 0 ? "Import" : src;
        pulsekit.Engine.FileSet set = pulsekit.Engine.collectFset(src, shown, app.learned, app.learnedFills, app.fillernPairs);
        if (set == null) {
            app.setNow("That file set is empty");
            return;
        }
        this.ensureFsetInfoMap();
        java.util.List stored = (java.util.List) app.fsetInfoMap.get(src);
        if (stored != null && !stored.isEmpty()) {
            set.parts.clear();
            set.parts.addAll(stored);
        }
        set.origin = pulsekit.Engine.fileSetOriginOf(src);
        pulsekit.Engine.FileSetAudio au = pulsekit.Engine.fileSetAudioOf(src);
        if (au != null) set.sourceWav = au.sourceWav;
        java.lang.String back = app.view;
        pulsekit.Engine.FileSet next = pulsekit.AudioIo.restyleFileSet(set, kit, styleName, hats, four, dkick, styleBpm, styleBack);
        pulsekit.Engine.replaceFileSetLearned(src, next, app.learned, app.learnedFills, app.fillernPairs);
        if (next.parts != null && !next.parts.isEmpty()) this.storeFsetParts(src, next.parts);
        if (pulsekit.Engine.isFileSetOrigin(set.origin)) pulsekit.Engine.rememberFileSetOrigin(src, set.origin);
        app.persistence.persistLearned();
        pulsekit.Engine.ImportedSong made = pulsekit.Engine.fileSetSongMade(shown, app.importedSongs);
        if (made != null) app.importedSongs.remove(made);
        this.makeFileSetSongNow(key, shown);
        app.importLibrary.rebuildImported();
        if (back != null) app.show(back);
        app.setNow("Style \u00b7 " + styleName);
    }

    public void applyStylePick(java.lang.String key, java.lang.String label, int index) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        if (!pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src))) {
            app.setNow("Using the source file");
            return;
        }
        java.util.List rows = pulsekit.StyleDb.rows();
        if (index < 0 || index >= rows.size()) return;
        pulsekit.StyleDb.Row row = (pulsekit.StyleDb.Row) rows.get(index);
        this.applyFileSetStyle(key, label, row.kit, row.name, row.hats, row.four, row.dkick, row.bpm, row.back);
        app.setNow("Style \u00b7 " + row.name);
    }

    public boolean fileSetStyleOn(java.lang.String key) {
        java.lang.String src = "";
        if (key != null && key.startsWith("f:")) src = key.substring(2);
        return pulsekit.Engine.fileSetStyleOn(pulsekit.Engine.fileSetOriginOf(src));
    }

    android.widget.LinearLayout pkMidiRow(java.lang.String src) {
        if (src == null) src = "";
        src = pulsekit.Engine.fileSetMidiKeyForLabel(src);
        java.lang.String name = pulsekit.Engine.fileSetMidiName(src);
        if (name.length() == 0) return null;
        android.widget.LinearLayout line = app.row();
        line.setTag("sourcemidi");
        line.setPadding(app.dp(8), app.dp(4), 0, app.dp(4));
        android.widget.TextView label = app.text(name, 12, false);
        label.setTextColor(FG);
        line.addView(label, app.flex(1));
        android.widget.TextView clock = app.text("00:00 / 00:00", 11, false);
        clock.setTag("midiclock");
        clock.setTextColor(MUTED);
        clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        line.addView(clock);
        line.addView(app.outline("Play", false, pulsekit.FileSetClicks.filePlayMidi(app, src)));
        line.addView(app.outline("Pause", false, pulsekit.FileSetClicks.filePauseMidi(app)));
        line.addView(app.outline("Stop", false, pulsekit.FileSetClicks.fileStopMidi(app)));
        android.widget.LinearLayout results = this.pkResultsRow(src);
        if (results == null) return line;
        // The MIDI row and the Compare hits results row go in together.
        line.setTag(null);
        android.widget.LinearLayout both = app.col();
        both.setTag("sourcemidi");
        both.addView(line);
        both.addView(results);
        return both;
    }

    void appendMidiFileRows() {
        if (app.importedFileList == null) return;
        for (int i = 0; i < app.importedFileList.getChildCount(); i++) {
            android.view.View child = app.importedFileList.getChildAt(i);
            if (!(child instanceof android.widget.LinearLayout)) continue;
            android.widget.LinearLayout row = (android.widget.LinearLayout) child;
            android.widget.TextView packHead = null;
            boolean header = false;
            for (int j = 0; j < row.getChildCount(); j++) {
                android.view.View v = row.getChildAt(j);
                if (!(v instanceof android.widget.TextView)) continue;
                java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                if ("Export".equals(s)) header = true;
                if (s.startsWith("\u25be ") || s.startsWith("\u25b8 ")) packHead = (android.widget.TextView) v;
            }
            if (!header || packHead == null) continue;
            android.view.View next = i + 1 < app.importedFileList.getChildCount() ? app.importedFileList.getChildAt(i + 1) : null;
            android.widget.LinearLayout kids = next instanceof android.widget.LinearLayout ? (android.widget.LinearLayout) next : null;
            boolean nextHeader = true;
            boolean already = false;
            if (kids != null) {
                nextHeader = false;
                for (int k = 0; k < kids.getChildCount(); k++) {
                    android.view.View v = kids.getChildAt(k);
                    if ("sourcemidi".equals(v.getTag())) already = true;
                    if (v instanceof android.widget.TextView) {
                        java.lang.String s = ((android.widget.TextView) v).getText() == null ? "" : ((android.widget.TextView) v).getText().toString();
                        if ("Export".equals(s)) nextHeader = true;
                    }
                }
            }
            if (already) continue;
            java.lang.String head = packHead.getText() == null ? "" : packHead.getText().toString();
            if (head.startsWith("\u25be ") || head.startsWith("\u25b8 ")) head = head.substring(2);
            int cut = head.lastIndexOf(" \u00b7 ");
            java.lang.String label = cut > 0 ? head.substring(0, cut) : head;
            if (label.startsWith("EP ")) label = label.substring(3);
            else if (label.startsWith("A ") || label.startsWith("M ") || label.startsWith("I ") || label.startsWith("C ")) label = label.substring(2);
            java.lang.String src = "Other".equals(label) ? "" : label;
            android.widget.LinearLayout line = this.pkMidiRow(src);
            if (line == null) continue;
            if (nextHeader) app.importedFileList.addView(line, i + 1);
            else kids.addView(line, 0);
        }
    }

    void pkShowInfoMidi(java.lang.String src) {
        if (this.infoRows == null) return;
        android.widget.LinearLayout line = this.pkMidiRow(src);
        if (line != null) this.infoRows.addView(line, 0);
    }
}
