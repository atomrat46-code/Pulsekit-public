package pulsekit;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * After a run that made pictures (SogniChat's tool results), a dialog shows them, each under its
 * file name, scaled to the screen. The files are already kept (Downloads or the program files
 * folder); this only shows them.
 */
final class PictureOffer {
    private PictureOffer() {}

    static boolean isPicture(String name) {
        return name != null && name.toLowerCase().matches(".+\\.(png|jpe?g|webp|gif)");
    }

    /** Shows the run's pictures; false when it made none that decode. */
    static boolean offer(MainActivity app, JavaRun.Result result) {
        if (result == null || result.files == null) return false;
        List<JavaRun.FileOut> pictures = new ArrayList<JavaRun.FileOut>();
        for (JavaRun.FileOut f : result.files) if (isPicture(f.name) && f.bytes != null && f.bytes.length > 0) pictures.add(f);
        if (pictures.isEmpty()) return false;
        int width = app.getResources().getDisplayMetrics().widthPixels;
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        int shown = 0;
        for (JavaRun.FileOut f : pictures) {
            Bitmap b = decode(f.bytes, width);
            TextView name = app.text(f.name + (b == null ? " (no preview)" : ""), 13, false);
            name.setPadding(0, app.dp(shown == 0 ? 0 : 12), 0, app.dp(4));
            col.addView(name);
            if (b == null) continue;
            ImageView view = new ImageView(app);
            view.setImageBitmap(b);
            view.setAdjustViewBounds(true);
            view.setTag("picture-offer:" + f.name);
            col.addView(view, new LinearLayout.LayoutParams(-1, -2));
            shown++;
        }
        if (shown == 0) return false;
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        new AlertDialog.Builder(app)
            .setTitle(pictures.size() == 1 ? "Picture ready" : pictures.size() + " pictures ready")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show();
        return true;
    }

    /** The picture at about `max` pixels wide at most, read at a smaller size first when it is big. */
    static Bitmap decode(byte[] data, int max) {
        try {
            BitmapFactory.Options size = new BitmapFactory.Options();
            size.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, size);
            BitmapFactory.Options read = new BitmapFactory.Options();
            read.inSampleSize = 1;
            while (size.outWidth / (read.inSampleSize * 2) >= Math.max(1, max)) read.inSampleSize *= 2;
            return BitmapFactory.decodeByteArray(data, 0, data.length, read);
        } catch (Throwable ex) {
            return null;
        }
    }
}
