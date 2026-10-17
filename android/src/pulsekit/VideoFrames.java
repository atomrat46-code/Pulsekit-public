package pulsekit;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;

/**
 * Video frames for the VideoFrames program (PyJav's Java menu), which finds this class by name: a
 * program sees only rt.jar, but on the phone it runs inside the app. Android's own decoder reads
 * the frame closest to the time asked for. The desktop has a class of the same name (VLC).
 */
public final class VideoFrames {
  private VideoFrames() {}

  /** The video's length in milliseconds (0 when it cannot be read). */
  public static long length(String path) {
    MediaMetadataRetriever media = new MediaMetadataRetriever();
    try {
      media.setDataSource(path);
      String d = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      return d == null ? 0 : Long.parseLong(d.trim());
    } catch (Exception ex) {
      return 0;
    } finally {
      try {
        media.release();
      } catch (Exception ignored) {
        // released
      }
    }
  }

  /**
   * The frame at `ms` (a negative `ms` counts from the end: -1 is the last frame) as a PNG, at most
   * `maxSide` pixels across (0: its own size); null when the video gives none.
   */
  public static byte[] frame(String path, long ms, int maxSide) {
    MediaMetadataRetriever media = new MediaMetadataRetriever();
    try {
      media.setDataSource(path);
      long at = ms;
      if (ms < 0) {
        String d = media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long len = d == null ? 0 : Long.parseLong(d.trim());
        // The last frame: just before the end (at the very end some phones give none).
        at = Math.max(0, len + ms - 40);
      }
      Bitmap b = media.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
      if (b == null) b = media.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC);
      if (b == null) return null;
      if (maxSide > 0 && Math.max(b.getWidth(), b.getHeight()) > maxSide) {
        double s = maxSide / (double) Math.max(b.getWidth(), b.getHeight());
        Bitmap small = Bitmap.createScaledBitmap(b, Math.max(1, (int) Math.round(b.getWidth() * s)), Math.max(1, (int) Math.round(b.getHeight() * s)), true);
        if (small != b) b.recycle();
        b = small;
      }
      java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
      b.compress(Bitmap.CompressFormat.PNG, 100, png);
      b.recycle();
      return png.toByteArray();
    } catch (Exception ex) {
      return null;
    } finally {
      try {
        media.release();
      } catch (Exception ignored) {
        // released
      }
    }
  }
}
