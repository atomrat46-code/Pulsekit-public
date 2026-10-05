package pulsekit;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Smaller copies of big JPEG pictures for SogniChat. Sogni shows the chat model pictures of up to
 * 1024 px on the longest side, and SogniChat (plain Java, no picture decoder on the phone) can only
 * shrink a PNG itself. Before a SogniChat run the app makes a 1024 px copy of each bigger JPEG given
 * with --file, with the platform's own decoder, and adds --seen_copy <copy> after it: the model sees
 * the copy, Sogni's tools still get the whole file. The copies are deleted after the run.
 */
public final class PictureCopies {
  /** Shrinks a JPEG to at most `side` pixels on its longest side, turned upright by its EXIF orientation (1-8). */
  public interface Shrinker {
    byte[] smaller(byte[] jpeg, int side, int orientation) throws Exception;
  }

  /** Set by the app at start: BitmapFactory on the phone, ImageIO on the desktop. */
  public static volatile Shrinker shrinker;

  /** The longest side Sogni shows the chat model. */
  public static final int SIDE = 1024;

  /** Pictures bigger than this are left to SogniChat (Sogni takes uploads of up to 100 MB). */
  static final long READ_MAX = 100L * 1024 * 1024;

  private PictureCopies() {}

  /** True for SogniChat, the program that sends pictures to a chat model. */
  public static boolean wants(String name) {
    return name != null && name.toLowerCase().startsWith("sognichat");
  }

  /**
   * argv with --seen_copy <copy> after each --file naming a JPEG bigger than SIDE; the copies made
   * are added to `made` (for deleting after the run). argv itself when there is nothing to copy.
   */
  public static List<String> withCopies(List<String> argv, List<File> made) {
    Shrinker s = shrinker;
    if (s == null || argv == null || !argv.contains("--file")) return argv;
    List<String> out = new ArrayList<String>();
    File dir = null;
    for (int i = 0; i < argv.size(); i++) {
      String a = argv.get(i);
      out.add(a);
      if (!"--file".equals(a) || i + 1 >= argv.size()) continue;
      String path = argv.get(++i);
      out.add(path);
      if (i + 1 < argv.size() && "--seen_copy".equals(argv.get(i + 1))) continue;
      try {
        File f = new File(path.trim());
        if (!f.isFile() || f.length() > READ_MAX) continue;
        byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
        int[] px = jpegSize(data);
        if (px == null || Math.max(px[0], px[1]) <= SIDE) continue;
        byte[] small = s.smaller(data, SIDE, exifOrientation(data));
        int[] got = small == null ? null : jpegSize(small);
        if (got == null || Math.max(got[0], got[1]) > SIDE) continue;
        if (dir == null) {
          dir = File.createTempFile("pulsekit-seen-", "");
          dir.delete();
          if (!dir.mkdirs()) return argv;
          made.add(dir);
        }
        File copy = new File(dir, f.getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-" + SIDE + ".jpg");
        FileOutputStream w = new FileOutputStream(copy);
        try {
          w.write(small);
        } finally {
          w.close();
        }
        made.add(0, copy);
        out.add("--seen_copy");
        out.add(copy.getAbsolutePath());
      } catch (Throwable ignored) {
        // No copy: SogniChat says what it can do with the picture.
      }
    }
    return out;
  }

  /** Deletes the copies (and their folder) made by withCopies. */
  public static void clean(List<File> made) {
    for (File f : made) f.delete();
  }

  /** {width, height} of a JPEG from its SOF marker, or null for anything else. */
  public static int[] jpegSize(byte[] d) {
    if (d == null || d.length < 4 || (d[0] & 0xff) != 0xff || (d[1] & 0xff) != 0xd8) return null;
    int i = 2;
    while (i + 9 < d.length) {
      if ((d[i] & 0xff) != 0xff) return null;
      int m = d[i + 1] & 0xff;
      if (m == 0xff) {
        i++;
        continue;
      }
      if (m == 0x01 || (m >= 0xd0 && m <= 0xd8)) {
        i += 2;
        continue;
      }
      if (m >= 0xc0 && m <= 0xcf && m != 0xc4 && m != 0xc8 && m != 0xcc) {
        return new int[] {((d[i + 7] & 0xff) << 8) | (d[i + 8] & 0xff), ((d[i + 5] & 0xff) << 8) | (d[i + 6] & 0xff)};
      }
      i += 2 + (((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff));
    }
    return null;
  }

  /** The EXIF orientation (1-8) a camera stored in the JPEG, 1 (upright) when there is none. */
  public static int exifOrientation(byte[] d) {
    try {
      int i = 2;
      while (i + 4 < d.length && (d[i] & 0xff) == 0xff) {
        int m = d[i + 1] & 0xff;
        int len = ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
        if (m == 0xda || m == 0xd9) return 1;
        if (m == 0xe1 && len >= 16 && d[i + 4] == 'E' && d[i + 5] == 'x' && d[i + 6] == 'i' && d[i + 7] == 'f') {
          int t = i + 10;
          boolean little = d[t] == 'I';
          int ifd = t + read(d, t + 4, 4, little);
          int n = read(d, ifd, 2, little);
          for (int e = 0; e < n; e++) {
            int at = ifd + 2 + e * 12;
            if (read(d, at, 2, little) == 0x0112) {
              int v = read(d, at + 8, 2, little);
              return v >= 1 && v <= 8 ? v : 1;
            }
          }
          return 1;
        }
        i += 2 + len;
      }
    } catch (RuntimeException ignored) {
      // A damaged EXIF block: take the picture as stored.
    }
    return 1;
  }

  private static int read(byte[] d, int at, int n, boolean little) {
    int v = 0;
    for (int k = 0; k < n; k++) v |= (d[at + k] & 0xff) << (8 * (little ? k : n - 1 - k));
    return v;
  }
}
