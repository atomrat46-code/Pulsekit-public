package pulsekit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import java.io.ByteArrayOutputStream;

/** PictureCopies' shrinker on the phone: BitmapFactory reads the JPEG, a smaller upright copy is saved as JPEG. */
final class PictureShrink implements PictureCopies.Shrinker {
    @Override
    public byte[] smaller(byte[] jpeg, int side, int orientation) {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, size);
        int longest = Math.max(size.outWidth, size.outHeight);
        if (longest <= 0) return null;
        BitmapFactory.Options read = new BitmapFactory.Options();
        read.inSampleSize = 1;
        while (longest / (read.inSampleSize * 2) >= side) read.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, read);
        if (b == null) return null;
        float k = Math.min(1f, side / (float) Math.max(b.getWidth(), b.getHeight()));
        Matrix m = new Matrix();
        switch (orientation) {
            case 2: m.setScale(-1, 1); break;
            case 3: m.setRotate(180); break;
            case 4: m.setRotate(180); m.postScale(-1, 1); break;
            case 5: m.setRotate(90); m.postScale(-1, 1); break;
            case 6: m.setRotate(90); break;
            case 7: m.setRotate(-90); m.postScale(-1, 1); break;
            case 8: m.setRotate(-90); break;
            default: break;
        }
        m.postScale(k, k);
        Bitmap upright = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        upright.compress(Bitmap.CompressFormat.JPEG, 90, out);
        if (upright != b) upright.recycle();
        b.recycle();
        return out.toByteArray();
    }
}
