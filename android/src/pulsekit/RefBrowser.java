package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * Browse DB on PyJav's Params page (SogniVideo's pictures, SogniChat's --file; DrumMidi's and CompareHits' audio input:
 * sound files only; CompareHits' drums and song: MIDI files only) and the Compare Hits page: the prompt library's reference files, or its result files
 * (a switch at the top), as a grid of previews, as the Prompts page's Ref files and Result files
 * show them. Picking one copies it into PyJav's input folder for the file row (SogniVideo's first
 * or last frame picture: a picture made by a SogniChat tool and kept as a result, say). A file kept
 * in several prompt versions is listed once.
 */
final class RefBrowser {
    private RefBrowser() {}

    /** The library's reference files and result files together: Browse DB is greyed when there are none. */
    static List<PromptVault.StoredFile> files(Activity activity) {
        return allFiles(activity, null);
    }

    /** Only sound files (an audio input: DrumMidi, CompareHits). */
    static final String SOUNDS = "sounds";
    /** Only MIDI files (CompareHits' drums and song). */
    static final String MIDIS = "midis";
    /** Sound files and MIDI files (DrumMidi's input: a MIDI is played with the kit to a WAV). */
    static final String SOUNDS_OR_MIDIS = "sounds-or-midis";

    /** As above; `only` (SOUNDS or MIDIS) keeps only that kind of file, null keeps all. */
    static List<PromptVault.StoredFile> files(Activity activity, boolean results, String only) {
        List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
        for (PromptVault.StoredFile f : files(activity, results)) if (fits(f.name, only)) out.add(f);
        return out;
    }

    /** Reference and result files together, only the `only` kind when given. */
    static List<PromptVault.StoredFile> allFiles(Activity activity, String only) {
        List<PromptVault.StoredFile> out = files(activity, false, only);
        out.addAll(files(activity, true, only));
        return out;
    }

    static boolean fits(String name, String only) {
        if (only == null) return true;
        String low = name == null ? "" : name.toLowerCase();
        if (SOUNDS.equals(only)) return low.matches(".+\\.(wav|wave|mp3|flac|m4a|ogg|aac)");
        if (MIDIS.equals(only)) return low.matches(".+\\.(mid|midi)");
        if (SOUNDS_OR_MIDIS.equals(only)) return fits(name, SOUNDS) || fits(name, MIDIS);
        return true;
    }

    /**
     * For an audio row a MIDI can stand in for (DrumMidi's input): a sound file fills the row as it
     * is; a MIDI file's drums are played with the kit's current sounds (an imported SoundFont, the
     * active drum set) to <name>-kit.wav beside it, which fills the row. A MIDI with no drums for
     * the kit leaves the row as it was and says so.
     */
    static void useAsAudio(Activity activity, String name, java.io.File file, String[] values, int index, TextView label) {
        if (!fits(name, MIDIS)) {
            values[index] = file.getAbsolutePath();
            label.setText(name + " \u00b7 from DB");
            return;
        }
        java.io.File wav = kitWav(activity, name, file);
        if (wav == null) return;
        values[index] = wav.getAbsolutePath();
        label.setText(name + " \u2192 " + wav.getName() + " (kit sounds)");
    }

    /**
     * A MIDI file's drums played with the kit's current sounds, written as <name>-kit.wav beside
     * it; null after saying why when there are no drums for the kit (or no kit).
     */
    static java.io.File kitWav(Activity activity, String name, java.io.File file) {
        try {
            short[] pcm = null;
            if (activity instanceof MainActivity && ((MainActivity) activity).playback != null) {
                java.io.FileInputStream in = new java.io.FileInputStream(file);
                java.io.ByteArrayOutputStream midi = new java.io.ByteArrayOutputStream();
                try {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) midi.write(buf, 0, n);
                } finally {
                    in.close();
                }
                pcm = AudioIo.renderMidiDrums(midi.toByteArray(), ((MainActivity) activity).playback.mixVoices(), 22050);
            }
            if (pcm == null || pcm.length == 0) {
                say(activity, name + " has no drums for the kit to play: pick a WAV, or a MIDI with drums");
                return null;
            }
            String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
            java.io.File wav = new java.io.File(file.getParentFile(), stem.replace(' ', '_') + "-kit.wav");
            java.io.FileOutputStream out = new java.io.FileOutputStream(wav);
            try {
                out.write(AudioIo.encodeWav(pcm, 22050));
            } finally {
                out.close();
            }
            return wav;
        } catch (Exception ex) {
            say(activity, "Could not play " + name + " with the kit");
            return null;
        }
    }

    private static void say(Activity activity, String text) {
        if (activity instanceof MainActivity) ((MainActivity) activity).setNow(text);
        android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_LONG).show();
    }

    /** The library's reference (or result) files, each name and size once; empty when there are none or the library cannot be read. */
    static List<PromptVault.StoredFile> files(Activity activity, boolean results) {
        List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
        try {
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            PromptVault vault = PromptVault.open(activity.getFilesDir());
            for (PromptVault.StoredFile f : results ? vault.resultFiles() : vault.referenceFiles()) {
                if (seen.add(f.name + "\n" + f.size)) out.add(f);
            }
        } catch (Exception ex) {
            // No library (or the keystore is gone): nothing to browse.
        }
        return out;
    }

    /** What to do with the picked file, copied into PyJav's input folder (its name, and the copy). */
    interface Picked {
        void picked(String name, java.io.File file);
    }

    static void browse(final Activity activity, final String[] values, final int index, final TextView label) {
        browse(activity, values, index, label, null);
    }

    /** For a Params file row: the pick fills it. `only`: SOUNDS or MIDIS lists only that kind (an audio or MIDI input). */
    static void browse(final Activity activity, final String[] values, final int index, final TextView label, String only) {
        browse(activity, only, (name, file) -> {
            values[index] = file.getAbsolutePath();
            label.setText(name + " \u00b7 from DB");
        });
    }

    /** Reference files first, as before; result files when there are no reference files. */
    static void browse(final Activity activity, String only, Picked picked) {
        browse(activity, files(activity, false, only).isEmpty(), only, picked);
    }

    static void browse(final Activity activity, final boolean results, final String only, final Picked picked) {
        final List<PromptVault.StoredFile> files = files(activity, results, only);
        final PromptVault vault;
        try {
            vault = PromptVault.open(activity.getFilesDir());
        } catch (Exception ex) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int gap = (int) (8 * density);
        int width = activity.getResources().getDisplayMetrics().widthPixels - (int) (80 * density);
        final int cell = Math.max((int) (100 * density), (width - gap) / 2);
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(gap * 2, gap, gap * 2, gap);
        final AlertDialog[] dialog = new AlertDialog[1];
        // Ref files / Result files: the list shown is lit; the other opens in its place.
        LinearLayout kinds = new LinearLayout(activity);
        kinds.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < 2; k++) {
            final boolean showResults = k == 1;
            android.widget.Button kind = new android.widget.Button(activity);
            kind.setText((showResults ? "Result files" : "Ref files") + " (" + files(activity, showResults, only).size() + ")");
            kind.setTag(showResults ? "refs-kind:results" : "refs-kind:refs");
            kind.setEnabled(showResults != results);
            kind.setOnClickListener(v -> {
                if (dialog[0] != null) dialog[0].dismiss();
                browse(activity, showResults, only, picked);
            });
            kinds.addView(kind, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        col.addView(kinds);
        if (files.isEmpty()) {
            TextView none = new TextView(activity);
            none.setText("none");
            none.setPadding(0, gap, 0, gap);
            col.addView(none);
        }
        final ImageView[] thumbs = new ImageView[files.size()];
        final TextView[] marks = new TextView[files.size()];
        LinearLayout row = null;
        for (int i = 0; i < files.size(); i++) {
            final PromptVault.StoredFile file = files.get(i);
            if (i % 2 == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
                rowLp.bottomMargin = gap;
                col.addView(row, rowLp);
            }
            FrameLayout frame = new FrameLayout(activity);
            frame.setBackgroundColor(Color.parseColor("#131416"));
            ImageView thumb = new ImageView(activity);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            String name = file.name == null ? "file" : file.name;
            int dot = name.lastIndexOf('.');
            TextView mark = new TextView(activity);
            mark.setText(dot >= 0 ? name.substring(dot + 1).toUpperCase() : "FILE");
            mark.setTextColor(Color.parseColor("#8A8B86"));
            mark.setGravity(Gravity.CENTER);
            frame.addView(thumb, new FrameLayout.LayoutParams(-1, cell));
            frame.addView(mark, new FrameLayout.LayoutParams(-1, cell));
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setTag("refs-pick:" + name);
            card.addView(frame);
            TextView caption = new TextView(activity);
            caption.setText(name);
            caption.setMaxLines(2);
            caption.setPadding(0, gap / 2, 0, 0);
            card.addView(caption);
            TextView sub = new TextView(activity);
            sub.setText((file.promptTitle == null ? "" : file.promptTitle) + " · " + (file.size / 1024) + " KB");
            sub.setTextSize(11);
            sub.setMaxLines(1);
            card.addView(sub);
            card.setOnClickListener(v -> {
                if (use(activity, vault, file, picked) && dialog[0] != null) dialog[0].dismiss();
            });
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cell, -2);
            if (i % 2 == 1) cardLp.leftMargin = gap;
            row.addView(card, cardLp);
            thumbs[i] = thumb;
            marks[i] = mark;
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(col);
        dialog[0] = new AlertDialog.Builder(activity)
            .setTitle(results ? "Result files" : "Reference files")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .show();
        // Previews come in the background, as on the Prompts page.
        new Thread(() -> {
            for (int i = 0; i < files.size(); i++) {
                if (dialog[0] == null || !dialog[0].isShowing()) return;
                PromptVault.StoredFile f = files.get(i);
                final Bitmap bitmap = PromptSheet.refThumb(activity, f.name, vault.fileBytes(f.versionId, f.which), cell);
                if (bitmap == null) continue;
                final int at = i;
                activity.runOnUiThread(() -> {
                    thumbs[at].setImageBitmap(bitmap);
                    marks[at].setVisibility(android.view.View.GONE);
                });
            }
        }, "pulsekit-ref-thumbs").start();
    }

    /** Copies the stored file into PyJav's input folder and hands it on; false when it cannot. */
    static boolean use(Activity activity, PromptVault vault, PromptVault.StoredFile file, Picked picked) {
        try {
            byte[] bytes = vault.fileBytes(file.versionId, file.which);
            if (bytes == null || bytes.length == 0) return false;
            java.io.File dir = new java.io.File(activity.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, (file.name == null ? "reference" : file.name).replace('/', '_').replace(' ', '_'));
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                fos.write(bytes);
            } finally {
                fos.close();
            }
            picked.picked(file.name, out);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
