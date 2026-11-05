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
            // General settings > Show images off: the picture is not shown (its name and items still are).
            Bitmap b = AppSettings.shows("I") ? decode(f.bytes, width) : null;
            TextView name = app.text(f.name + (b == null ? (AppSettings.shows("I") ? " (no preview)" : " (not shown: Show images is off)") : ""), 13, false);
            name.setPadding(0, app.dp(shown == 0 ? 0 : 12), 0, app.dp(4));
            col.addView(name);
            // Reference, Result, Save to file: under the name, above the picture (in view without scrolling).
            col.addView(ReadySave.row(app, f.name, f.bytes, null));
            boolean hidden = !AppSettings.shows("I");
            if (b == null && !hidden) continue;
            final android.view.View view;
            // The picture into another program, as its Reference file 1 or 2.
            final JavaRun.FileOut pic = f;
            // Crop image (its long press): a frame over the picture; the crop's long press saves it,
            // takes it on as a reference, or upscales it.
            final CropLayer[] crop = new CropLayer[1];
            if (b != null) {
                ImageView image = new ImageView(app);
                image.setImageBitmap(b);
                image.setAdjustViewBounds(true);
                // At most 45% of the screen high: Process as a reference shows under it without scrolling.
                image.setMaxHeight(app.getResources().getDisplayMetrics().heightPixels * 45 / 100);
                view = image;
                android.widget.FrameLayout holder = new android.widget.FrameLayout(app);
                holder.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -2));
                crop[0] = CropLayer.over(app, holder, image, pic.name, new Cropped(app, pic.bytes));
                view.setTag("picture-offer:" + f.name);
                col.addView(holder, new LinearLayout.LayoutParams(-1, -2));
            } else {
                // Not shown: held, it still has the picture's items.
                view = app.pill("Hold for its items", false, null);
                view.setTag("picture-offer:" + f.name);
                col.addView(view, new LinearLayout.LayoutParams(-1, -2));
            }
            // Long press: Add to favourites, Add to group.
            view.setOnLongClickListener(v -> {
                java.util.List<String> base = new ArrayList<String>(java.util.Arrays.asList(isFavourite(app, pic.name, pic.bytes.length) ? FileFavourites.REMOVE_SHORT : FileFavourites.ADD_SHORT, FileGroups.ADD,
                    MediaTags.ADD_TAG, MediaTags.ADD_CATEGORY, Playlist.ADD));
                if (crop[0] != null) base.add(CropBox.CROP);
                final String[] picItems = TagDialogs.withUseLast(app, base.toArray(new String[0]), ReadyRecent.IMAGE);
                menu = new AlertDialog.Builder(app)
                    .setTitle(pic.name)
                    .setItems(picItems, (d, w) -> {
                        String it = picItems[w];
                        if (it.equals(CropBox.CROP) && crop[0] != null) crop[0].start();
                        else if (it.equals(Playlist.ADD)) PlaylistScreen.addReady(app, pic.name, pic.bytes, null);
                        else if (w == 0) toggleFavourite(app, pic.name, pic.bytes.length);
                        else if (it.equals(FileGroups.ADD)) GroupDialogs.addTo(app, groupKeys(app, pic.name, pic.bytes.length), pic.name, null, ReadyRecent.IMAGE);
                        else if (it.equals(MediaTags.ADD_TAG)) TagDialogs.tags(app, pic.name, pic.bytes.length, null, ReadyRecent.IMAGE);
                        else if (it.startsWith(ReadyRecent.USE_LAST)) TagDialogs.useLast(app, pic.name, pic.bytes.length, ReadyRecent.IMAGE);
                        else if (it.equals(ReadyRecent.USE_ALL)) TagDialogs.useAll(app, pic.name, pic.bytes.length, ReadyRecent.IMAGE, pic.bytes, null);
                        else TagDialogs.category(app, pic.name, pic.bytes.length, null, ReadyRecent.IMAGE);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });
            TextView process = app.outline("Process as a reference", false, v -> {
                if (last != null) last.dismiss();
                ProcessRefDialog.open(app, pic.name, pic.bytes);
            });
            process.setTag("picture-ref:" + f.name);
            col.addView(process);
            shown++;
        }
        if (shown == 0) return false;
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        last = new AlertDialog.Builder(app)
            .setTitle(pictures.size() == 1 ? "Picture ready" : pictures.size() + " pictures ready")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .show();
        return true;
    }

    /**
     * Picture ready's crop: Save as… into Download (or the program files folder), Process as a
     * reference (Picture ready closes; the crop into another program), Upscale (ImageUpscaler).
     */
    static final class Cropped implements CropLayer.Actions, CropLayer.Processes {
        final MainActivity app;
        final byte[] bytes;

        Cropped(MainActivity app, byte[] bytes) {
            this.app = app;
            this.bytes = bytes;
        }

        public Bitmap cut(CropBox at) throws Exception {
            return CropLayer.cutBytes(this.bytes, at);
        }

        public String save(String name, byte[] png) throws Exception {
            String where = ArtJava.publish(this.app, name, png);
            if (where == null) throw new java.io.IOException("Download takes no file named " + name);
            return "Saved the crop as " + where;
        }

        public void process(String name, byte[] png) {
            if (last != null) last.dismiss();
            ProcessRefDialog.open(this.app, name, png);
        }

        public void upscale(String name, byte[] png) throws Exception {
            if (last != null) last.dismiss();
            java.io.File dir = new java.io.File(this.app.getCacheDir(), "pyjav-in");
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File in = new java.io.File(dir, name.replaceAll("[\\\\/:*?\"<>|]", "_"));
            java.io.FileOutputStream out = new java.io.FileOutputStream(in);
            try {
                out.write(png);
            } finally {
                out.close();
            }
            this.app.openKitView("py");
            this.app.programMenus.selectProgram("Java", "ImageUpscaler.java");
            this.app.pyJav.pkUseInputPath(in.getAbsolutePath());
            this.app.pyJav.pkOpenParams();
        }
    }

    /** The dialog shown last (closed when a picture goes on to Process as a reference), and a picture's long press menu. */
    static AlertDialog last;
    static AlertDialog menu;

    private static PromptVault vault(MainActivity app) {
        try {
            return PromptVault.open(app.getFilesDir());
        } catch (Exception ex) {
            return null;
        }
    }

    /** A run's file among the favourites (by its name and size). */
    static boolean isFavourite(MainActivity app, String name, long size) {
        FileFavourites.load(app.getFilesDir());
        return MarkFile.isFavourite(name, size);
    }

    static void toggleFavourite(MainActivity app, String name, long size) {
        FileFavourites.load(app.getFilesDir());
        boolean now = MarkFile.toggleFavourite(vault(app), name, size);
        app.setNow(name + (now ? " added to favourites" : " taken out of favourites"));
    }

    static String[] groupKeys(MainActivity app, String name, long size) {
        FileGroups.load(app.getFilesDir());
        return MarkFile.groupKeys(vault(app), name, size);
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
