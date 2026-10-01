package pulsekit;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static pulsekit.MainActivity.*;

/**
 * File > Compare Hits: how closely a file set's drum hits line up. The song against the file
 * set's source MIDI (what the import changed), and both against the original WAV (what DrumMidi
 * heard). The WAV is the one kept with the file set, the last WAV given to PyJav, or one picked here.
 */
final class CompareHitsPage {
    static final int PICK_WAV = 29;

    final MainActivity app;
    /** Chosen file set label, and the source key that holds its MIDI. */
    String source;
    String midiKey;
    byte[] pickedWav;
    String pickedWavName;
    LinearLayout setsBox;
    TextView wavLabel;
    TextView result;
    TextView compareBtn;
    boolean running;

    CompareHitsPage(MainActivity app) {
        this.app = app;
    }

    void wire() {
        LinearLayout pane = app.col();
        pane.setVisibility(View.GONE);
        pane.setBackgroundColor(BG);
        pane.setClickable(true);
        pane.addView(app.text("Compare Hits", 18, true));
        TextView lead = app.text("How closely the drum hits line up, per drum family. The song against the file set's source MIDI "
            + "shows what the import changed; the MIDI against the original WAV shows what DrumMidi heard. "
            + "Hit times are compared, not sound.", 13, false);
        lead.setTextColor(MUTED);
        lead.setPadding(0, app.dp(6), 0, app.dp(8));
        pane.addView(lead);
        LinearLayout body = app.col();
        body.addView(this.heading("File set"));
        this.setsBox = app.col();
        body.addView(this.setsBox);
        body.addView(this.heading("Original WAV"));
        this.wavLabel = app.text("", 13, false);
        this.wavLabel.setTextColor(MUTED);
        body.addView(this.wavLabel);
        LinearLayout buttons = app.row();
        buttons.setPadding(0, app.dp(8), 0, app.dp(8));
        TextView pick = app.action("Pick WAV", ELEV, FG, v -> this.pickWav());
        pick.setTag("compare-pick-wav");
        this.compareBtn = app.action("Compare", HIT, BG, v -> this.compare());
        this.compareBtn.setTag("compare-run");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, app.dp(40));
        lp.setMargins(0, 0, app.dp(8), 0);
        buttons.addView(pick, lp);
        buttons.addView(this.compareBtn, new LinearLayout.LayoutParams(-2, app.dp(40)));
        body.addView(buttons);
        this.result = app.text("", 12, false);
        this.result.setTypeface(Typeface.MONOSPACE);
        this.result.setTextColor(FG);
        this.result.setTag("compare-result");
        HorizontalScrollView wide = new HorizontalScrollView(app);
        wide.addView(this.result);
        body.addView(wide);
        TextView legend = app.text(HitCompare.LEGEND, 12, false);
        legend.setTextColor(MUTED);
        legend.setPadding(0, app.dp(10), 0, app.dp(16));
        body.addView(legend);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(body);
        pane.addView(scroll, app.flexFill());
        app.compareHitsPane = pane;
        if (app.importPane != null && app.importPane.getParent() instanceof ViewGroup) {
            ((ViewGroup) app.importPane.getParent()).addView(pane, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    TextView heading(String s) {
        TextView t = app.text(s, 15, true);
        t.setPadding(0, app.dp(12), 0, app.dp(4));
        return t;
    }

    /** Called when the page is shown: lists the file sets and finds a WAV. */
    void refresh() {
        if (this.setsBox == null) return;
        java.util.LinkedHashMap<String, String> sets = HitCompare.fileSetsWithMidi(Engine.fileSetSources(app.learned, app.learnedFills));
        if (this.source == null || !sets.containsKey(this.source)) {
            this.source = sets.isEmpty() ? null : sets.keySet().iterator().next();
            this.pickedWav = null;
            this.pickedWavName = null;
        }
        this.midiKey = this.source == null ? null : sets.get(this.source);
        this.setsBox.removeAllViews();
        if (sets.isEmpty()) {
            TextView none = app.text("No file set with a source MIDI yet. Run DrumMidi_CRT in PyJav: its MIDI is kept with the file set it makes.", 13, false);
            none.setTextColor(MUTED);
            this.setsBox.addView(none);
        }
        for (final String label : sets.keySet()) {
            boolean on = label.equals(this.source);
            TextView chip = app.action(label, on ? HIT : ELEV, on ? BG : FG, v -> {
                if (!label.equals(this.source)) {
                    this.source = label;
                    this.pickedWav = null;
                    this.pickedWavName = null;
                    this.result.setText("");
                }
                this.refresh();
            });
            chip.setTag("compare-set:" + label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, app.dp(38));
            lp.setMargins(0, app.dp(3), 0, app.dp(3));
            this.setsBox.addView(chip, lp);
        }
        this.paintWav();
    }

    void paintWav() {
        if (this.wavLabel == null) return;
        String kept = this.midiKey == null ? null : (HitCompare.fileSetWav(this.midiKey) != null ? "Kept with the file set" : null);
        java.io.File last = this.lastPyJavWav();
        if (this.pickedWav != null) this.wavLabel.setText("Picked: " + this.pickedWavName);
        else if (kept != null) this.wavLabel.setText(kept);
        else if (last != null) this.wavLabel.setText("Last WAV given to PyJav: " + last.getName());
        else this.wavLabel.setText("None. Pick the WAV you gave DrumMidi to compare against it too.");
    }

    /** The WAV last given to PyJav, such as DrumMidi_CRT's input, if it is still in PyJav's input folder. */
    java.io.File lastPyJavWav() {
        String[] paths = {app.pyJav.pkPyInputPath, app.pyJav.pkAudioInputPath};
        for (String p : paths) {
            if (p == null) continue;
            java.io.File f = new java.io.File(p);
            if (f.isFile() && p.toLowerCase().endsWith(".wav")) return f;
        }
        java.io.File[] all = new java.io.File(app.getCacheDir(), "pyjav-in").listFiles();
        java.io.File best = null;
        if (all != null) {
            for (java.io.File f : all) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".wav") && (best == null || f.lastModified() > best.lastModified())) best = f;
            }
        }
        return best;
    }

    void pickWav() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        app.startActivityForResult(intent, PICK_WAV);
    }

    void takeWav(Uri uri) {
        try {
            this.pickedWav = app.projectIo.readUri(uri);
            String name = uri.getLastPathSegment();
            android.database.Cursor c = app.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int col = c.getColumnIndex("_display_name");
                    if (c.moveToFirst() && col >= 0) name = c.getString(col);
                } finally {
                    c.close();
                }
            }
            this.pickedWavName = name == null ? "WAV" : name;
            this.paintWav();
        } catch (Exception ex) {
            app.setNow("Could not read that WAV");
        }
    }

    void compare() {
        if (this.running) return;
        if (this.midiKey == null) {
            app.setNow("Pick a file set first");
            return;
        }
        final byte[] midi = HitCompare.fileSetMidi(this.midiKey);
        final Engine.ImportedSong song = HitCompare.songFor(this.source, app.importedSongs);
        byte[] wav = this.pickedWav != null ? this.pickedWav : HitCompare.fileSetWav(this.midiKey);
        final java.io.File last = wav == null ? this.lastPyJavWav() : null;
        final byte[] wavBytes = wav;
        this.running = true;
        this.result.setText("Comparing…");
        app.setNow("Comparing hits");
        new Thread(() -> {
            String text;
            try {
                byte[] w = wavBytes != null ? wavBytes : (last != null ? readFile(last) : null);
                text = HitCompare.report(midi, song, w == null ? null : AudioIo.parseWav(w));
            } catch (Throwable ex) {
                text = "Could not compare: " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            }
            final String shown = text;
            app.runOnUiThread(() -> {
                this.running = false;
                this.result.setText(shown);
                app.setNow("Compare Hits");
            });
        }).start();
    }

    static byte[] readFile(java.io.File f) throws java.io.IOException {
        java.io.InputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream((int) Math.min(Integer.MAX_VALUE - 8, f.length()));
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }
}
