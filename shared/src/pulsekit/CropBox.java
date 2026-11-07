package pulsekit;

/**
 * Crop image (a picture's long press, in the picture viewers: the Media browser's and the Prompts
 * page's on the phone, the Preview window on the desktop): a coloured frame over the picture, its
 * borders and corners dragged to size it and its inside dragged to move it; while it is dragged
 * only the part kept shows. Kept as fractions of the picture (0..1), so it holds at any zoom and is
 * cut from the picture at its full size. A long press then has Save as… (<name>-crop.png) and
 * Upscale (ImageUpscaler with the crop).
 */
public final class CropBox {
  public static final String CROP = "Crop image";
  public static final String SAVE = "Save as\u2026";
  public static final String UPSCALE = "Upscale";
  /** Picture ready's crop: into another program as its Reference file 1 or 2. */
  public static final String PROCESS = "Process as a reference";
  public static final String CANCEL = "Cancel crop";
  /** Sogni Params' Crop (beside Preview on a picture row): the crop fills the row (asked about upscaling first when it needs it). */
  public static final String USE_IN_ROW = "Use the crop in this row";
  public static final String CROP_ROW = "Crop";
  public static final String HINT = "Drag the frame's borders or corners (or inside it, to move it). A long press saves or upscales the crop.";
  /** The frame's colour (ARGB). */
  public static final int COLOR = 0xFFFFA726;
  /** The smallest box, as a fraction of the picture's side. */
  static final double MIN = 0.05;

  /** Left, top, right, bottom: fractions of the picture's width and height. */
  public double left = 0.1, top = 0.1, right = 0.9, bottom = 0.9;

  /**
   * What a press holds: a corner (0 top left, 1 top right, 2 bottom right, 3 bottom left), 4 the box,
   * a border (5 left, 6 top, 7 right, 8 bottom), -1 nothing.
   */
  public int held = -1;
  private double lastX, lastY;

  /**
   * A press at (x, y) (fractions of the picture), `reach` the fraction a corner is caught from: the
   * corner nearest it, else the box when inside it. What it holds.
   */
  public int press(double x, double y, double reach) {
    double[][] c = {{left, top}, {right, top}, {right, bottom}, {left, bottom}};
    int best = -1;
    double near = reach;
    for (int i = 0; i < 4; i++) {
      double d = Math.hypot(x - c[i][0], y - c[i][1]);
      if (d <= near) {
        near = d;
        best = i;
      }
    }
    // A border: the nearest one within reach, along its length.
    if (best < 0) {
      double[] d = {Math.abs(x - left), Math.abs(y - top), Math.abs(x - right), Math.abs(y - bottom)};
      boolean alongY = y >= top - reach && y <= bottom + reach, alongX = x >= left - reach && x <= right + reach;
      double near2 = reach;
      for (int i = 0; i < 4; i++) {
        boolean along = i % 2 == 0 ? alongY : alongX;
        if (along && d[i] <= near2) {
          near2 = d[i];
          best = 5 + i;
        }
      }
    }
    if (best < 0 && x >= left && x <= right && y >= top && y <= bottom) best = 4;
    held = best;
    lastX = x;
    lastY = y;
    return held;
  }

  /** The finger or mouse moved to (x, y) (fractions): the corner held follows it, or the box moves; kept in the picture. */
  public void drag(double x, double y) {
    x = clamp(x);
    y = clamp(y);
    if (held == 4) {
      double dx = x - lastX, dy = y - lastY;
      dx = Math.max(-left, Math.min(1 - right, dx));
      dy = Math.max(-top, Math.min(1 - bottom, dy));
      left += dx;
      right += dx;
      top += dy;
      bottom += dy;
    } else if (held >= 0) {
      if (held == 0 || held == 3 || held == 5) left = Math.min(x, right - MIN);
      if (held == 1 || held == 2 || held == 7) right = Math.max(x, left + MIN);
      if (held == 0 || held == 1 || held == 6) top = Math.min(y, bottom - MIN);
      if (held == 2 || held == 3 || held == 8) bottom = Math.max(y, top + MIN);
    }
    lastX = x;
    lastY = y;
  }

  public void release() {
    held = -1;
  }

  static double clamp(double v) {
    return Math.max(0, Math.min(1, v));
  }

  /** The box in a picture `w` x `h` pixels: x, y, width, height (at least 1 x 1, inside it). */
  public int[] pixels(int w, int h) {
    int x0 = (int) Math.round(left * w), y0 = (int) Math.round(top * h);
    int x1 = (int) Math.round(right * w), y1 = (int) Math.round(bottom * h);
    x0 = Math.max(0, Math.min(w - 1, x0));
    y0 = Math.max(0, Math.min(h - 1, y0));
    x1 = Math.max(x0 + 1, Math.min(w, x1));
    y1 = Math.max(y0 + 1, Math.min(h, y1));
    return new int[] {x0, y0, x1 - x0, y1 - y0};
  }

  /** The crop's file name: "beach.jpg" -> "beach-crop.png". */
  public static String name(String picture) {
    String n = picture == null || picture.isEmpty() ? "picture" : picture;
    int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
    if (slash >= 0) n = n.substring(slash + 1);
    int dot = n.lastIndexOf('.');
    if (dot > 0) n = n.substring(0, dot);
    return n + "-crop.png";
  }
}
