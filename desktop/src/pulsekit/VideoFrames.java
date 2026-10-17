package pulsekit;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Video frames for the VideoFrames program (PyJav's Java menu), which finds this class by name: a
 * program sees only rt.jar, but PyJav puts Pulsekit's own JAR on its class path. VLC (the one the
 * video player uses) reads the frame from the time asked for. The phone has a class of the same name.
 */
public final class VideoFrames {
  private VideoFrames() {}

  /** The video's length in milliseconds (0 when VLC is missing or cannot read it). */
  public static long length(String path) {
    if (!VlcPlayer.available()) return 0;
    VlcPlayer.Probe p = VlcPlayer.probe(new File(path), 64);
    return p == null ? 0 : p.lengthMs;
  }

  /**
   * The frame at `ms` (a negative `ms` counts from the end: -1 is the last frame) as a PNG, at most
   * `maxSide` pixels across (0: up to 4096); null when VLC is missing or gives none.
   */
  public static byte[] frame(String path, long ms, int maxSide) {
    if (!VlcPlayer.available()) return null;
    File video = new File(path);
    long at = ms;
    boolean toEnd = ms < 0;
    if (toEnd) {
      // The last frame: played from a little before the end, the frame shown when it ends.
      long len = length(path);
      if (len <= 0) return null;
      at = Math.max(0, len - 1500);
    }
    VlcPlayer p = null;
    try {
      p = new VlcPlayer(video, true, maxSide > 0 ? maxSide : 4096, String.format(java.util.Locale.ROOT, ":start-time=%.3f", at / 1000.0));
      p.play();
      // The first frame shown from the start time (VLC decodes from the key frame before it up to it).
      BufferedImage frame = null;
      for (int i = 0; i < 400; i++) {
        int state = p.state();
        if (p.screen.frames > 0) {
          frame = p.screen.frame;
          if (!toEnd && (p.timeMs() + 60 >= at || state == VlcPlayer.ENDED)) break;
          if (toEnd && state == VlcPlayer.ENDED) break;
        }
        if (state == VlcPlayer.ERROR || state == VlcPlayer.ENDED && (p.screen.frames == 0 && i > 20 || toEnd)) break;
        Thread.sleep(20);
      }
      if (frame == null) return null;
      p.refreshSize();
      int vw = p.screen.visibleW > 0 ? Math.min(p.screen.visibleW, frame.getWidth()) : frame.getWidth();
      int vh = p.screen.visibleH > 0 ? Math.min(p.screen.visibleH, frame.getHeight()) : frame.getHeight();
      BufferedImage out = frame.getSubimage(0, 0, vw, vh);
      java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
      javax.imageio.ImageIO.write(out, "png", png);
      return png.size() == 0 ? null : png.toByteArray();
    } catch (Exception ex) {
      return null;
    } finally {
      if (p != null) p.release();
    }
  }
}
