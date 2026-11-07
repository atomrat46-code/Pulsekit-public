package pulsekit;

import java.util.List;

/**
 * ReRender (a video thumbnail's item): the video made again at another size and shape. Its shape is
 * reached by cropping the picture (Crop to fill, the part kept picked: centre, top or left, bottom or
 * right) or by black bars (Fit with black bars), then it is scaled to the size asked; the sound stays
 * as it was. The numbers here are the source's pixels, even (H.264 takes even sizes).
 */
public final class VideoFit {
  private VideoFit() {}

  public static final String RERENDER = "ReRender…";
  /** The sizes offered, each "W×H (shape)"; Custom… takes one typed. */
  public static final String[] SIZES = {
    "1080×1920 (9:16)", "720×1280 (9:16)", "1920×1080 (16:9)", "1280×720 (16:9)", "1080×1080 (1:1)",
    "1080×1350 (4:5)", "1080×1890 (4:7)", "720×1260 (4:7)"
  };
  public static final String CUSTOM = "Custom…";
  public static final String CROP = "Crop to fill";
  public static final String PAD = "Fit with black bars";
  /** Crop to fill: the part of the picture kept, and where it sits (0 the top or left, 1 the bottom or right). */
  public static final String[] AREAS = {"Centre", "Top / left", "Bottom / right"};
  public static final double[] ANCHORS = {0.5, 0.0, 1.0};

  /** "1080×1920 (9:16)", "1080x1920" or "1080 1920": {1080, 1920}; null when it is no size (each 16 to 4096, made even). */
  public static int[] parseSize(String text) {
    if (text == null) return null;
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2,4})\\s*[x×X* ,]\\s*(\\d{2,4})").matcher(text);
    if (!m.find()) return null;
    int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
    if (w < 16 || h < 16 || w > 4096 || h > 4096) return null;
    return new int[] {w & ~1, h & ~1};
  }

  /**
   * How a sw×sh picture reaches the ow×oh shape, in its own pixels: {cropLeft, cropTop, cropRight,
   * cropBottom, padLeft, padTop, padRight, padBottom}, all even. Crop to fill (`pad` false) cuts the
   * long side down, keeping the part `anchor` says (0.5 the middle); Fit with black bars adds bars
   * to the short side, half each side. Already that shape: all 0.
   */
  public static int[] plan(int sw, int sh, int ow, int oh, boolean pad, double anchor) {
    int[] p = new int[8];
    if (sw <= 0 || sh <= 0 || ow <= 0 || oh <= 0) return p;
    double want = ow / (double) oh, have = sw / (double) sh;
    if (Math.abs(want - have) < 0.005) return p;
    double a = Math.max(0, Math.min(1, anchor));
    if (!pad) {
      if (have > want) {
        // Too wide: the sides go.
        int keep = even(sh * want);
        int cut = Math.max(0, sw - keep);
        int left = even(cut * a);
        p[0] = left;
        p[2] = cut - left;
      } else {
        // Too tall: the top and bottom go.
        int keep = even(sw / want);
        int cut = Math.max(0, sh - keep);
        int top = even(cut * a);
        p[1] = top;
        p[3] = cut - top;
      }
    } else {
      if (have > want) {
        // Too wide: bars above and below.
        int add = Math.max(0, even(sw / want) - sh);
        p[5] = even(add / 2.0);
        p[7] = add - p[5];
      } else {
        // Too tall: bars at the sides.
        int add = Math.max(0, even(sh * want) - sw);
        p[4] = even(add / 2.0);
        p[6] = add - p[4];
      }
    }
    return p;
  }

  static int even(double v) {
    return ((int) Math.round(v)) & ~1;
  }

  /** The re-rendered video's name: walk.mp4 at 1080×1920 is walk-1080x1920.mp4. */
  public static String name(String video, int w, int h) {
    String stem = video == null ? "video" : video.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    return stem + "-" + w + "x" + h + ".mp4";
  }

  /** The largest of the sizes {w, h} (by area; the first such), or null for none: JoinVideo's default. */
  public static int[] largest(List<int[]> sizes) {
    int[] best = null;
    if (sizes != null) for (int[] s : sizes) if (s != null && (best == null || (long) s[0] * s[1] > (long) best[0] * best[1])) best = s;
    return best;
  }

  /** What the plan does, in words ("crops 40 px off the sides", "adds bars above and below"). */
  public static String said(int[] p) {
    if (p[0] + p[2] > 0) return "crops " + (p[0] + p[2]) + " px off the sides";
    if (p[1] + p[3] > 0) return "crops " + (p[1] + p[3]) + " px off the top and bottom";
    if (p[5] + p[7] > 0) return "adds black bars above and below";
    if (p[4] + p[6] > 0) return "adds black bars at the sides";
    return "keeps its shape";
  }
}
