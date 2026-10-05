package pulsekit;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** PictureCopies' shrinker on the desktop: ImageIO reads the JPEG, a smaller upright copy is saved as JPEG. */
final class PictureShrink implements PictureCopies.Shrinker {
  @Override
  public byte[] smaller(byte[] jpeg, int side, int orientation) throws Exception {
    BufferedImage src = ImageIO.read(new ByteArrayInputStream(jpeg));
    if (src == null) return null;
    int w = src.getWidth(), h = src.getHeight();
    double k = Math.min(1.0, side / (double) Math.max(w, h));
    int sw = Math.max(1, (int) Math.round(w * k)), sh = Math.max(1, (int) Math.round(h * k));
    BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = small.createGraphics();
    g.drawImage(src.getScaledInstance(sw, sh, Image.SCALE_AREA_AVERAGING), 0, 0, null);
    g.dispose();
    // Turned upright by the EXIF orientation: each pixel of the result from where it is stored.
    boolean turned = orientation >= 5 && orientation <= 8;
    int ow = turned ? sh : sw, oh = turned ? sw : sh;
    BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < oh; y++) {
      for (int x = 0; x < ow; x++) {
        int sx, sy;
        switch (orientation) {
          case 2: sx = sw - 1 - x; sy = y; break;
          case 3: sx = sw - 1 - x; sy = sh - 1 - y; break;
          case 4: sx = x; sy = sh - 1 - y; break;
          case 5: sx = y; sy = x; break;
          case 6: sx = y; sy = sh - 1 - x; break;
          case 7: sx = sw - 1 - y; sy = sh - 1 - x; break;
          case 8: sx = sw - 1 - y; sy = x; break;
          default: sx = x; sy = y; break;
        }
        out.setRGB(x, y, small.getRGB(sx, sy));
      }
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    if (!ImageIO.write(out, "jpg", bytes)) return null;
    return bytes.toByteArray();
  }
}
