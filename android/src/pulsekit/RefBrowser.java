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
 * Browse DB on PyJav's Params page: the prompt library's reference files as a grid of previews,
 * as the Prompts page's Ref files shows them. Picking one copies it into PyJav's input folder for
 * the file row (SogniVideo's first or last frame picture). A file kept in several prompt versions
 * is listed once.
 */
final class RefBrowser {
    private RefBrowser() {}

    /** The library's reference files, each name and size once; empty when there are none or the library cannot be read. */
    static List<PromptVault.StoredFile> files(Activity activity) {
        List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
        try {
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            for (PromptVault.StoredFile f : PromptVault.open(activity).referenceFiles()) {
                if (seen.add(f.name + "\n" + f.size)) out.add(f);
            }
        } catch (Exception ex) {
            // No library (or the keystore is gone): nothing to browse.
        }
        return out;
    }

    static void browse(final Activity activity, final String[] values, final int index, final TextView label) {
        final List<PromptVault.StoredFile> files = files(activity);
        if (files.isEmpty()) return;
        final PromptVault vault;
        try {
            vault = PromptVault.open(activity);
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
                if (use(activity, vault, file, values, index, label) && dialog[0] != null) dialog[0].dismiss();
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
            .setTitle("Reference files")
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

    /** Copies the stored file into PyJav's input folder for the row; false when it cannot. */
    static boolean use(Activity activity, PromptVault vault, PromptVault.StoredFile file, String[] values, int index, TextView label) {
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
            values[index] = out.getAbsolutePath();
            label.setText(file.name + " · from DB");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
