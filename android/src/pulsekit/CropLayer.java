package pulsekit;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ImageView;

/**
 * Crop image over a picture viewer's picture (CropBox): laid over the picture, hidden until Crop
 * image (the picture's long press) starts it. Then the picture has a coloured frame, its borders
 * and corners dragged (inside it moves it); while a finger drags, only the part kept shows. A long
 * press on it: Save as… (a name, then the viewer saves the crop), Upscale (ImageUpscaler with it),
 * Cancel crop.
 */
final class CropLayer extends View {
    /** What the viewer does with the crop. */
    interface Actions {
        /** The crop cut from the picture at its full size (off the screen's thread). */
        Bitmap cut(CropBox box) throws Exception;

        /** Saves it as `name` (off the screen's thread); what was done, for the status. */
        String save(String name, byte[] png) throws Exception;

        /** ImageUpscaler with it, as `name` (on the screen's thread). */
        void upscale(String name, byte[] png) throws Exception;
    }

    /** A viewer whose crop can also go into another program as a reference (Picture ready's). */
    interface Processes {
        /** Process as a reference with it, as `name` (on the screen's thread). */
        void process(String name, byte[] png) throws Exception;
    }

    /** A Sogni Params picture row's crop: Use the crop in this row (asked about upscaling first when it needs it). */
    interface Fills {
        /** The row filled with it, as `name` (on the screen's thread). */
        void fill(String name, byte[] png) throws Exception;
    }

    final Activity activity;
    final ImageView image;
    /** The picture's name (the viewer sets it as it goes to another picture). */
    String name;
    final Actions actions;
    CropBox box;
    boolean dragging;
    /** The menu shown last (for the tests). */
    static AlertDialog lastMenu;
    /** The last one started (for the tests). */
    static CropLayer last;
    /** What was said last (for the tests). */
    static String lastSaid;

    private final Paint dim = new Paint();
    private final Paint line = new Paint();
    private float downX, downY;
    private boolean moved;
    private final Runnable hold = () -> {
        if (!this.moved && this.box != null) {
            this.box.release();
            this.dragging = false;
            this.invalidate();
            this.menu();
        }
    };

    CropLayer(Activity activity, ImageView image, String name, Actions actions) {
        super(activity);
        this.activity = activity;
        this.image = image;
        this.name = name;
        this.actions = actions;
        this.setTag("crop-layer");
        this.setVisibility(View.GONE);
    }

    /** Over `image` in its parent (a FrameLayout the picture fills). */
    static CropLayer over(Activity activity, android.widget.FrameLayout parent, ImageView image, String name, Actions actions) {
        CropLayer c = new CropLayer(activity, image, name, actions);
        parent.addView(c, new android.widget.FrameLayout.LayoutParams(-1, -1));
        return c;
    }

    boolean cropping() {
        return this.box != null;
    }

    /** Crop image: the frame over the picture. */
    void start() {
        if (this.image.getDrawable() == null) return;
        this.box = new CropBox();
        last = this;
        this.setVisibility(View.VISIBLE);
        this.invalidate();
        say(CropBox.HINT);
    }

    void stop() {
        this.box = null;
        this.dragging = false;
        this.setVisibility(View.GONE);
    }

    /** Where the picture is drawn, in this layer's coordinates: x, y, width, height. */
    float[] rect() {
        android.graphics.drawable.Drawable dr = this.image.getDrawable();
        if (dr == null || dr.getIntrinsicWidth() <= 0 || dr.getIntrinsicHeight() <= 0 || this.image.getWidth() <= 0 || this.image.getHeight() <= 0) return null;
        float ox = this.image.getLeft() - this.getLeft(), oy = this.image.getTop() - this.getTop();
        if (this.image.getScaleType() == ImageView.ScaleType.FIT_XY) return new float[] {ox, oy, this.image.getWidth(), this.image.getHeight()};
        float s = Math.min(this.image.getWidth() / (float) dr.getIntrinsicWidth(), this.image.getHeight() / (float) dr.getIntrinsicHeight());
        float w = dr.getIntrinsicWidth() * s, h = dr.getIntrinsicHeight() * s;
        return new float[] {ox + (this.image.getWidth() - w) / 2f, oy + (this.image.getHeight() - h) / 2f, w, h};
    }

    @Override
    protected void onDraw(Canvas c) {
        float[] r = this.rect();
        if (this.box == null || r == null) return;
        float l = r[0] + (float) this.box.left * r[2], t = r[1] + (float) this.box.top * r[3];
        float rr = r[0] + (float) this.box.right * r[2], b = r[1] + (float) this.box.bottom * r[3];
        // While dragged, only the part kept shows.
        this.dim.setColor(this.dragging ? 0xF0000000 : 0x99000000);
        c.drawRect(r[0], r[1], r[0] + r[2], t, this.dim);
        c.drawRect(r[0], b, r[0] + r[2], r[1] + r[3], this.dim);
        c.drawRect(r[0], t, l, b, this.dim);
        c.drawRect(rr, t, r[0] + r[2], b, this.dim);
        float d = this.getResources().getDisplayMetrics().density;
        this.line.setColor(CropBox.COLOR);
        this.line.setStyle(Paint.Style.STROKE);
        this.line.setStrokeWidth(3 * d);
        c.drawRect(l, t, rr, b, this.line);
        // Handles: the corners and the middle of each border.
        this.line.setStyle(Paint.Style.FILL);
        float h = 7 * d;
        float mx = (l + rr) / 2, my = (t + b) / 2;
        for (float[] k : new float[][] {{l, t}, {rr, t}, {rr, b}, {l, b}, {mx, t}, {mx, b}, {l, my}, {rr, my}}) c.drawRect(k[0] - h, k[1] - h, k[0] + h, k[1] + h, this.line);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float[] r = this.rect();
        if (this.box == null || r == null) return false;
        double x = (ev.getX() - r[0]) / r[2], y = (ev.getY() - r[1]) / r[3];
        float d = this.getResources().getDisplayMetrics().density;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // The frame takes the finger: the page and the picture's pan do not scroll meanwhile.
                for (ViewParent p = this.getParent(); p != null; p = p.getParent()) p.requestDisallowInterceptTouchEvent(true);
                this.downX = ev.getX();
                this.downY = ev.getY();
                this.moved = false;
                this.box.press(x, y, 24 * d / Math.min(r[2], r[3]));
                this.postDelayed(this.hold, 550);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!this.moved && Math.hypot(ev.getX() - this.downX, ev.getY() - this.downY) > 8 * d) {
                    this.moved = true;
                    this.removeCallbacks(this.hold);
                }
                if (this.moved && this.box.held >= 0) {
                    this.dragging = true;
                    this.box.drag(x, y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                this.removeCallbacks(this.hold);
                if (this.box != null) this.box.release();
                this.dragging = false;
                break;
            default:
                break;
        }
        this.invalidate();
        return true;
    }

    /** The crop's long press: (a Params row's) Use the crop in this row, Save as…, (Picture ready's) Process as a reference, Upscale, Cancel crop. */
    void menu() {
        final boolean processes = this.actions instanceof Processes;
        final String[] items = this.actions instanceof Fills ? new String[] {CropBox.USE_IN_ROW, CropBox.SAVE, CropBox.UPSCALE, CropBox.CANCEL}
            : processes ? new String[] {CropBox.SAVE, CropBox.PROCESS, CropBox.UPSCALE, CropBox.CANCEL} : new String[] {CropBox.SAVE, CropBox.UPSCALE, CropBox.CANCEL};
        lastMenu = new AlertDialog.Builder(this.activity).setTitle(CropBox.name(this.name))
            .setItems(items, (dlg, w) -> {
                String it = items[w];
                if (it.equals(CropBox.SAVE)) this.saveAs();
                else if (it.equals(CropBox.USE_IN_ROW)) this.made(png -> ((Fills) this.actions).fill(CropBox.name(this.name), png));
                else if (it.equals(CropBox.PROCESS)) this.made(png -> ((Processes) this.actions).process(CropBox.name(this.name), png));
                else if (it.equals(CropBox.UPSCALE)) this.made(png -> this.actions.upscale(CropBox.name(this.name), png));
                else this.stop();
            }).setNegativeButton("Close", null).show();
    }

    /** The asker shown last (for the tests). */
    static AlertDialog lastAsk;

    void saveAs() {
        final EditText field = new EditText(this.activity);
        field.setSingleLine(true);
        field.setText(CropBox.name(this.name));
        field.setTag("crop-name");
        lastAsk = new AlertDialog.Builder(this.activity).setTitle(CropBox.SAVE).setView(field)
            .setPositiveButton("Save", (dlg, w) -> {
                String n = field.getText().toString().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
                if (n.isEmpty()) n = CropBox.name(this.name);
                if (!n.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) n = n + ".png";
                final String named = n;
                this.made(png -> {
                    final byte[] bytes = png;
                    new Thread(() -> {
                        String said;
                        try {
                            said = this.actions.save(named, bytes);
                        } catch (Throwable ex) {
                            said = "Could not save the crop: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                        }
                        final String shown = said;
                        this.activity.runOnUiThread(() -> say(shown));
                    }, "pulsekit-crop-save").start();
                });
            }).setNegativeButton("Cancel", null).show();
    }

    interface Png {
        void use(byte[] png) throws Exception;
    }

    /** The crop cut and made a PNG (off the screen's thread), then `then` with it on the screen's thread. */
    void made(final Png then) {
        final CropBox at = new CropBox();
        at.left = this.box.left;
        at.top = this.box.top;
        at.right = this.box.right;
        at.bottom = this.box.bottom;
        say("Cropping " + this.name + "…");
        new Thread(() -> {
            byte[] png = null;
            String problem = null;
            try {
                Bitmap cut = this.actions.cut(at);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                cut.compress(Bitmap.CompressFormat.PNG, 100, bos);
                png = bos.toByteArray();
            } catch (Throwable ex) {
                problem = "Could not crop " + this.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final byte[] got = png;
            final String why = problem;
            this.activity.runOnUiThread(() -> {
                if (got == null) {
                    say(why);
                    return;
                }
                try {
                    then.use(got);
                } catch (Exception ex) {
                    say("Could not use the crop: " + ex.getMessage());
                }
            });
        }, "pulsekit-crop").start();
    }

    void say(String s) {
        lastSaid = s;
        if (this.activity instanceof MainActivity) ((MainActivity) this.activity).setNow(s);
        android.widget.Toast.makeText(this.activity, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** A picture's bytes cut to the box at their full size (at most 8192 pixels a side). */
    static Bitmap cutBytes(byte[] bytes, CropBox box) throws Exception {
        try {
            @SuppressWarnings("deprecation")
            android.graphics.BitmapRegionDecoder dec = android.graphics.BitmapRegionDecoder.newInstance(bytes, 0, bytes.length, false);
            int[] p = box.pixels(dec.getWidth(), dec.getHeight());
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = 1;
            while (Math.max(p[2], p[3]) / o.inSampleSize > 8192) o.inSampleSize *= 2;
            Bitmap got = dec.decodeRegion(new android.graphics.Rect(p[0], p[1], p[0] + p[2], p[1] + p[3]), o);
            dec.recycle();
            if (got != null) return got;
        } catch (Throwable ex) {
            // read whole below
        }
        Bitmap whole = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (whole == null) throw new java.io.IOException("the picture could not be read");
        int[] p = box.pixels(whole.getWidth(), whole.getHeight());
        return Bitmap.createBitmap(whole, p[0], p[1], p[2], p[3]);
    }
}
