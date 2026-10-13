package pulsekit;

/** A picture's or a video's size for its thumbnail: "1920×1080 · 16:9". */
public final class MediaSize {
  private MediaSize() {}

  /** The ratios named as people know them; anything else is shown as its decimal ratio ("1.85:1"). */
  private static final int[][] KNOWN = {{1, 1}, {4, 3}, {3, 4}, {3, 2}, {2, 3}, {16, 9}, {9, 16}, {16, 10}, {10, 16}, {21, 9}, {9, 21}, {5, 4}, {4, 5}, {2, 1}, {1, 2}};

  /** "16:9" for 1920×1080 (and for 1918×1080: within 1 %), "1.85:1" when no common ratio is that close. */
  public static String aspect(int w, int h) {
    if (w <= 0 || h <= 0) return "";
    double r = w / (double) h;
    for (int[] k : KNOWN) {
      double want = k[0] / (double) k[1];
      if (Math.abs(r - want) / want < 0.01) return k[0] + ":" + k[1];
    }
    return r >= 1 ? String.format(java.util.Locale.ROOT, "%.2f:1", r) : String.format(java.util.Locale.ROOT, "1:%.2f", 1 / r);
  }

  /** "1920×1080 · 16:9", or "" when the size is not known. */
  public static String label(int w, int h) {
    if (w <= 0 || h <= 0) return "";
    return w + "×" + h + " · " + aspect(w, h);
  }
}
