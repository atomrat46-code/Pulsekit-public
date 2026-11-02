package pulsekit;

/**
 * Crop in the picture viewers (the Media browser's on the phone, the Preview window on the
 * desktop): a box over the picture, its corners dragged to size it and its inside dragged to move
 * it. Kept as fractions of the picture (0..1), so it holds at any zoom and is cut from the picture
 * at its full size. Save crop writes <name>-crop.png beside the picture; held, it also opens
 * ImageUpscaler with the crop.
 */
public final class CropBox {
  public static final String CROP = "Crop";
  public static final String SAVE = "Save crop";
  public static final String HINT = "Drag the corners, or the box to move it. Save crop saves it; hold it to upscale it too.";
  /** The smallest box, as a fraction of the picture's side. */
  static final double MIN = 0.05;

  /** Left, top, right, bottom: fractions of the picture's width and height. */
  public double left = 0.1, top = 0.1, right = 0.9, bottom = 0.9;

  /** What a press holds: a corner (0 top left, 1 top right, 2 bottom right, 3 bottom left), 4 the box, -1 nothing. */
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
      if (held == 0 || held == 3) left = Math.min(x, right - MIN);
      if (held == 1 || held == 2) right = Math.max(x, left + MIN);
      if (held == 0 || held == 1) top = Math.min(y, bottom - MIN);
      if (held == 2 || held == 3) bottom = Math.max(y, top + MIN);
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
