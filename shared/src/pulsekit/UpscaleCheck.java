package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Add to DB (Reference or Result files) for a picture: smaller than the picture ImageUpscaler made
 * last, or not 4:7 (the phone's preferred shape, upright or lying), it is asked whether to upscale
 * it first. The last upscaled size is kept in upscaled-last.txt; sizes are read from the file's
 * header (PNG, JPEG, WebP), so nothing is decoded.
 */
public final class UpscaleCheck {
  private UpscaleCheck() {}

  static final String FILE = "upscaled-last.txt";
  /** How far from 4:7 (or 7:4) a picture may be and still count as that shape. */
  static final double SLACK = 0.02;

  /** The picture ImageUpscaler made (its first PNG, JPEG or WebP), kept as the size to compare with. */
  public static void remember(File dir, JavaRun.Result r) {
    if (r == null || r.files == null) return;
    for (JavaRun.FileOut f : r.files) {
      int[] wh = size(f.bytes);
      if (wh == null) continue;
      try {
        Files.write(new File(dir, FILE).toPath(), (wh[0] + "x" + wh[1] + "\n").getBytes(StandardCharsets.UTF_8));
      } catch (Exception ignored) {
        // not kept: the 4:7 check still asks
      }
      return;
    }
  }

  /** The last upscaled picture's {width, height}, or null. */
  public static int[] last(File dir) {
    try {
      File f = new File(dir, FILE);
      if (!f.isFile()) return null;
      String[] p = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().split("x");
      return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    } catch (Exception ex) {
      return null;
    }
  }

  /** Why to upscale `name` first ("… is 640×480, smaller than the last upscaled 1536×2688 · not 4:7"), or null when it needs not be asked. */
  public static String question(File dir, String name, byte[] bytes) {
    if (MediaDir.kind(name) != MediaDir.PICTURE) return null;
    int[] wh = size(bytes);
    if (wh == null || wh[0] <= 0 || wh[1] <= 0) return null;
    StringBuilder why = new StringBuilder();
    int[] last = last(dir);
    if (last != null && (long) wh[0] * wh[1] < (long) last[0] * last[1]) why.append("smaller than the last upscaled picture (").append(last[0]).append("×").append(last[1]).append(")");
    double r = wh[0] / (double) wh[1];
    if (Math.abs(r - 4 / 7.0) > SLACK && Math.abs(r - 7 / 4.0) > SLACK) {
      if (why.length() > 0) why.append(", and ");
      why.append("not 4:7 (it is ").append(MediaSize.aspect(wh[0], wh[1])).append(")");
    }
    if (why.length() == 0) return null;
    return name + " is " + wh[0] + "×" + wh[1] + ": " + why + ".\n\nUpscale it first (ImageUpscaler), or add it as it is?";
  }

  /** The shapes ImageUpscaler's --aspect is offered, upright and lying. */
  static final int[][] SHAPES = {{4, 7}, {7, 4}, {1, 1}, {4, 3}, {3, 4}, {3, 2}, {2, 3}, {16, 9}, {9, 16}, {4, 5}, {5, 4}, {21, 9}};
  /** Up to this much cut off, Pulsekit's own 4:7 (or 7:4) is suggested before the shape that cuts least. */
  static final double OWN_SHAPE_CUT = 0.15;

  /** How much of a w×h picture cropping to a:b cuts off (0 to 1). */
  public static double cut(int w, int h, int a, int b) {
    double r = w / (double) h, t = a / (double) b;
    return 1 - Math.min(r, t) / Math.max(r, t);
  }

  /**
   * ImageUpscaler's --aspect for a w×h picture: {value, why}. Pulsekit's 4:7 (7:4 for a lying
   * picture) when cropping to it cuts at most 15%; else the shape that cuts least (its own when it
   * is one of them). Null for an unknown size.
   */
  public static String[] suggestAspect(int w, int h) {
    if (w <= 0 || h <= 0) return null;
    int[] own = w > h ? new int[] {7, 4} : new int[] {4, 7};
    double ownCut = cut(w, h, own[0], own[1]);
    int[] best = own;
    double bestCut = ownCut;
    if (ownCut > OWN_SHAPE_CUT) {
      for (int[] s : SHAPES) {
        double c = cut(w, h, s[0], s[1]);
        if (c < bestCut - 1e-9) {
          best = s;
          bestCut = c;
        }
      }
    }
    String value = best[0] + ":" + best[1];
    String why = "the picture is " + w + "\u00d7" + h + " (" + MediaSize.aspect(w, h) + ")";
    if (best == own) why += bestCut < 0.005 ? ", already Pulsekit's " + value : "; " + value + " (Pulsekit's shape for Sogni) cuts " + percent(bestCut);
    else why += "; " + value + " cuts least (" + percent(bestCut) + "), Pulsekit's " + own[0] + ":" + own[1] + " would cut " + percent(ownCut);
    return new String[] {value, why};
  }

  private static String percent(double f) {
    return Math.round(f * 100) + "%";
  }

  /** A PNG's, JPEG's or WebP's {width, height} from its header; null for anything else. */
  public static int[] size(byte[] d) {
    if (d == null || d.length < 30) return null;
    // PNG: the IHDR chunk.
    if ((d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return new int[] {be32(d, 16), be32(d, 20)};
    if ((d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8) return PictureCopies.jpegSize(d);
    if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
      if (d[12] == 'V' && d[13] == 'P' && d[14] == '8' && d[15] == ' ') return new int[] {le16(d, 26) & 0x3fff, le16(d, 28) & 0x3fff};
      if (d[12] == 'V' && d[13] == 'P' && d[14] == '8' && d[15] == 'L') {
        int b0 = d[21] & 0xff, b1 = d[22] & 0xff, b2 = d[23] & 0xff, b3 = d[24] & 0xff;
        return new int[] {1 + (((b1 & 0x3f) << 8) | b0), 1 + (((b3 & 0xf) << 10) | (b2 << 2) | ((b1 & 0xc0) >> 6))};
      }
      if (d[12] == 'V' && d[13] == 'P' && d[14] == '8' && d[15] == 'X') return new int[] {1 + le24(d, 24), 1 + le24(d, 27)};
    }
    return null;
  }

  private static int be32(byte[] d, int at) {
    return ((d[at] & 0xff) << 24) | ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
  }

  private static int le16(byte[] d, int at) {
    return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8);
  }

  private static int le24(byte[] d, int at) {
    return (d[at] & 0xff) | ((d[at + 1] & 0xff) << 8) | ((d[at + 2] & 0xff) << 16);
  }
}
