import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Usage: java ImageUpscaler <input.png> [output.png] [--method 1|2] [--scale N] [--width N] [--height N] [--aspect W:H] [--fit crop|pad|stretch] [--quality N]
 *
 * Resizes / upscales a picture (PNG, JPEG; on the desktop also BMP and GIF, on the phone WebP).
 *
 *   --method 1  bicubic resize, built in (default; 2x unless --scale, --width or --height say
 *               otherwise). Works on the phone and on the desktop.
 *   --method 2  Real-ESRGAN AI upscaling at 4x; needs the realesrgan-ncnn-vulkan program on PATH
 *               (or its path in REALESRGAN_BIN). Desktop only: the phone cannot start it.
 *   --scale N   how many times larger (method 1: 2 by default, 0.1 to 8; method 2: 4, and another
 *               N resizes the AI's 4x picture to N times).
 *   --width N / --height N   the size wanted, in pixels: one of them keeps the shape (or --aspect);
 *               both give that exact size. They win over --scale.
 *   --aspect W:H  the shape wanted, such as 1:1, 4:3, 16:9 or 9:16 (or 1.5).
 *   --fit       when the shape changes: crop (cut the edges, default), pad (add black bars, or
 *               transparent ones in a PNG) or stretch.
 *   --quality N JPEG / WebP quality, 1 to 100 (92).
 *
 * The output is named <input>-upscaled.png when it is not given. The old form
 * "java ImageUpscaler input.png output.png 2" still works (the method last).
 */
public class ImageUpscaler {
    private static final int AI_SCALE = 4;
    /** The largest picture made: about 50 megapixels (200 MB in memory). */
    private static final long MAX_PIXELS = 50000000L;

    public static void main(String[] args) {
        int code = run(args);
        // In Pulsekit on the phone the program runs inside the app: never exit there.
        if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
    }

    static int run(String[] args) {
        String input = null;
        String output = null;
        int method = 1;
        double scale = 0;
        int width = 0;
        int height = 0;
        double aspect = 0;
        String fit = "crop";
        int quality = 92;
        try {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a.equals("--method") && i + 1 < args.length) method = Integer.parseInt(args[++i].trim().substring(0, 1));
                else if (a.equals("--scale") && i + 1 < args.length) scale = number(args[++i]);
                else if (a.equals("--width") && i + 1 < args.length) width = (int) Math.round(number(args[++i]));
                else if (a.equals("--height") && i + 1 < args.length) height = (int) Math.round(number(args[++i]));
                else if (a.equals("--aspect") && i + 1 < args.length) aspect = aspect(args[++i]);
                else if (a.equals("--fit") && i + 1 < args.length) fit = args[++i].trim().toLowerCase(Locale.ROOT);
                else if (a.equals("--quality") && i + 1 < args.length) quality = (int) Math.round(number(args[++i]));
                else if (a.equals("-h") || a.equals("--help")) {
                    usage();
                    return 0;
                } else if (a.startsWith("--")) {
                    System.out.println("Failed: unknown argument " + a);
                    usage();
                    return 2;
                } else if (input == null) input = a;
                else if (output == null) output = a;
                else if (a.trim().matches("[12]")) method = Integer.parseInt(a.trim());
                else {
                    System.out.println("Failed: one input and one output picture, then the method (1 or 2)");
                    usage();
                    return 2;
                }
            }
        } catch (NumberFormatException ex) {
            System.out.println("Failed: " + ex.getMessage());
            usage();
            return 2;
        }
        // The old form: input, output and the method given in order ("in.png 2" names no output).
        if (output != null && output.trim().matches("[12]") && args.length == 2) {
            method = Integer.parseInt(output.trim());
            output = null;
        }
        if (input == null || input.trim().length() == 0) {
            System.out.println("Failed: give the picture to upscale");
            usage();
            return 2;
        }
        if (method != 1 && method != 2) {
            System.out.println("Failed: the method is 1 (bicubic, built in) or 2 (Real-ESRGAN AI 4x)");
            return 2;
        }
        if (!fit.equals("crop") && !fit.equals("pad") && !fit.equals("stretch")) {
            System.out.println("Failed: --fit is crop, pad or stretch");
            return 2;
        }
        if (scale != 0 && (scale < 0.1 || scale > 8)) {
            System.out.println("Failed: --scale is 0.1 to 8");
            return 2;
        }
        if (width < 0 || height < 0) {
            System.out.println("Failed: --width and --height are pixels, more than 0");
            return 2;
        }
        quality = Math.max(1, Math.min(100, quality));
        File in = new File(input.trim());
        if (!in.isFile()) {
            System.out.println("Failed: no picture " + input);
            return 1;
        }
        if (output == null || output.trim().length() == 0) {
            String stem = in.getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
            String ext = in.getName().toLowerCase(Locale.ROOT).matches(".*\\.jpe?g") ? ".jpg" : ".png";
            output = stem + "-upscaled" + ext;
        }
        File out = inWork(output.trim());
        try {
            String format = format(out.getName());
            boolean phone = Droid.here();
            if (method == 2 && phone) {
                System.out.println("Failed: method 2 (Real-ESRGAN AI) runs the realesrgan-ncnn-vulkan program, which the phone cannot start "
                    + "from inside the app. Use method 1 here, or run method 2 in Pulsekit on a desktop computer.");
                return 1;
            }
            if (phone && (format.equals("bmp") || format.equals("gif"))) throw new IOException("the phone writes PNG, JPEG or WebP, not " + format);
            if (!phone && format.equals("webp")) throw new IOException("the desktop writes PNG, JPEG, BMP or GIF, not WebP");
            Picture src = phone ? Droid.read(in) : Awt.read(in);
            src = upright(src, orientation(in));
            System.out.println("Read " + in.getName() + " (" + src.w + " x " + src.h + ")");
            // The size made: from --width / --height, else the picture (in the --aspect shape) times the scale.
            double shape = aspect > 0 ? aspect : (width > 0 && height > 0 ? (double) width / height : (double) src.w / src.h);
            int tw;
            int th;
            if (width > 0 && height > 0) {
                tw = width;
                th = height;
                if (aspect > 0) System.out.println("Note: --width and --height give the shape; --aspect is not used");
            } else if (width > 0) {
                tw = width;
                th = Math.max(1, (int) Math.round(width / shape));
            } else if (height > 0) {
                th = height;
                tw = Math.max(1, (int) Math.round(height * shape));
            } else {
                double[] base = shaped(src.w, src.h, shape, fit);
                double times = scale > 0 ? scale : method == 2 ? AI_SCALE : 2;
                tw = Math.max(1, (int) Math.round(base[0] * times));
                th = Math.max(1, (int) Math.round(base[1] * times));
            }
            if ((long) tw * th > MAX_PIXELS) {
                throw new IOException(tw + " x " + th + " is too large (at most about " + (MAX_PIXELS / 1000000) + " megapixels)");
            }
            Picture pic = src;
            // Crop: the edges go first, so the AI works on what is kept.
            if (fit.equals("crop")) pic = crop(pic, (double) tw / th);
            if (method == 2) pic = ai(pic);
            boolean alpha = src.alpha && (format.equals("png") || format.equals("webp") || format.equals("gif"));
            Picture made = fit.equals("pad") ? pad(pic, tw, th, alpha) : bicubic(pic, tw, th);
            made.alpha = alpha;
            if (phone) Droid.write(made, out, format, quality);
            else Awt.write(made, out, format, quality);
            System.out.println("Method " + method + (method == 1 ? " (bicubic)" : " (Real-ESRGAN AI 4x, then bicubic to the size)")
                + ", fit " + fit + ": " + src.w + " x " + src.h + " -> " + made.w + " x " + made.h);
            System.out.println("Wrote " + out.getName() + " (" + size(out.length()) + ")");
            System.out.println("Succeeded: " + out.getName());
            return 0;
        } catch (OutOfMemoryError ex) {
            System.out.println("Failed: not enough memory for a picture this large; try a smaller --scale or size");
            return 1;
        } catch (Exception ex) {
            System.out.println("Failed: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            return 1;
        }
    }

    /** A picture as ARGB pixels, row by row. */
    static final class Picture {
        int w;
        int h;
        int[] px;
        boolean alpha;

        Picture(int w, int h) {
            this.w = w;
            this.h = h;
            this.px = new int[w * h];
        }
    }

    /** {width, height} of the picture in the shape `shape` (width / height): cropped, padded or stretched. */
    static double[] shaped(int w, int h, double shape, String fit) {
        double now = (double) w / h;
        if (Math.abs(now - shape) < 1e-6) return new double[] {w, h};
        if (fit.equals("stretch")) return new double[] {w, w / shape};
        boolean wider = now > shape;
        if (fit.equals("crop")) return wider ? new double[] {h * shape, h} : new double[] {w, w / shape};
        return wider ? new double[] {w, w / shape} : new double[] {h * shape, h};
    }

    /** The middle of the picture in the shape `shape` (width / height). */
    static Picture crop(Picture p, double shape) {
        double now = (double) p.w / p.h;
        if (Math.abs(now - shape) < 1e-3) return p;
        int cw = p.w;
        int ch = p.h;
        if (now > shape) cw = Math.max(1, (int) Math.round(p.h * shape));
        else ch = Math.max(1, (int) Math.round(p.w / shape));
        int x0 = (p.w - cw) / 2;
        int y0 = (p.h - ch) / 2;
        Picture out = new Picture(cw, ch);
        out.alpha = p.alpha;
        for (int y = 0; y < ch; y++) System.arraycopy(p.px, (y0 + y) * p.w + x0, out.px, y * cw, cw);
        return out;
    }

    /** The picture fitted inside tw x th, centred on black (or transparent) bars. */
    static Picture pad(Picture p, int tw, int th, boolean alpha) {
        double k = Math.min((double) tw / p.w, (double) th / p.h);
        int iw = Math.max(1, Math.min(tw, (int) Math.round(p.w * k)));
        int ih = Math.max(1, Math.min(th, (int) Math.round(p.h * k)));
        Picture inner = bicubic(p, iw, ih);
        Picture out = new Picture(tw, th);
        java.util.Arrays.fill(out.px, alpha ? 0x00000000 : 0xff000000);
        int x0 = (tw - iw) / 2;
        int y0 = (th - ih) / 2;
        for (int y = 0; y < ih; y++) System.arraycopy(inner.px, y * iw, out.px, (y0 + y) * tw + x0, iw);
        return out;
    }

    /**
     * Bicubic resize (Keys, a = -0.5), in two passes: rows, then columns. When making a picture
     * smaller the kernel is widened, so it is not grainy.
     */
    static Picture bicubic(Picture p, int tw, int th) {
        if (tw == p.w && th == p.h) return p;
        Picture mid = new Picture(tw, p.h);
        resample(p.px, p.w, p.h, mid.px, tw, true);
        Picture out = new Picture(tw, th);
        resample(mid.px, tw, p.h, out.px, th, false);
        out.alpha = p.alpha;
        return out;
    }

    /** One pass: along rows (`across`) from sw to `to` pixels, or down columns from sh to `to`. */
    private static void resample(int[] src, int sw, int sh, int[] dst, int to, boolean across) {
        int from = across ? sw : sh;
        int lines = across ? sh : sw;
        double ratio = (double) from / to;
        double support = ratio > 1 ? 2 * ratio : 2;
        double stretch = ratio > 1 ? ratio : 1;
        int taps = (int) Math.ceil(support * 2) + 1;
        int[] first = new int[to];
        float[] weights = new float[to * taps];
        for (int o = 0; o < to; o++) {
            double centre = (o + 0.5) * ratio - 0.5;
            int lo = (int) Math.floor(centre - support) + 1;
            first[o] = lo;
            double sum = 0;
            for (int k = 0; k < taps; k++) {
                double wgt = cubic((lo + k - centre) / stretch);
                weights[o * taps + k] = (float) wgt;
                sum += wgt;
            }
            if (sum != 0) for (int k = 0; k < taps; k++) weights[o * taps + k] /= (float) sum;
        }
        for (int line = 0; line < lines; line++) {
            for (int o = 0; o < to; o++) {
                float a = 0, r = 0, g = 0, b = 0;
                int lo = first[o];
                for (int k = 0; k < taps; k++) {
                    float wgt = weights[o * taps + k];
                    if (wgt == 0) continue;
                    int at = Math.max(0, Math.min(from - 1, lo + k));
                    int c = across ? src[line * sw + at] : src[at * sw + line];
                    a += wgt * (c >>> 24);
                    r += wgt * ((c >> 16) & 0xff);
                    g += wgt * ((c >> 8) & 0xff);
                    b += wgt * (c & 0xff);
                }
                int c = (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
                if (across) dst[line * to + o] = c;
                else dst[o * sw + line] = c;
            }
        }
    }

    private static double cubic(double x) {
        x = Math.abs(x);
        if (x < 1) return (1.5 * x - 2.5) * x * x + 1;
        if (x < 2) return ((-0.5 * x + 2.5) * x - 4) * x + 2;
        return 0;
    }

    private static int clamp(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : i > 255 ? 255 : i;
    }

    /** Real-ESRGAN at 4x (desktop): the picture through realesrgan-ncnn-vulkan, by way of PNG files. */
    static Picture ai(Picture p) throws Exception {
        String executable = System.getenv("REALESRGAN_BIN");
        if (executable == null || executable.trim().length() == 0) executable = "realesrgan-ncnn-vulkan";
        File dir = new File(System.getProperty("java.io.tmpdir", "."));
        File in = File.createTempFile("upscale-in-", ".png", dir);
        File out = File.createTempFile("upscale-out-", ".png", dir);
        try {
            Awt.write(p, in, "png", 100);
            ProcessBuilder pb = new ProcessBuilder(executable, "-i", in.getAbsolutePath(), "-o", out.getAbsolutePath(), "-s", Integer.toString(AI_SCALE));
            pb.redirectErrorStream(true);
            System.out.println("Starting Real-ESRGAN AI upscaling (4x)...");
            Process run;
            try {
                run = pb.start();
            } catch (IOException ex) {
                throw new IOException("could not start " + executable + ": install realesrgan-ncnn-vulkan and put it on PATH, "
                    + "or set REALESRGAN_BIN to its path (or use method 1)");
            }
            InputStream log = run.getInputStream();
            byte[] buf = new byte[4096];
            StringBuilder last = new StringBuilder();
            for (int n; (n = log.read(buf)) > 0; ) {
                last.append(new String(buf, 0, n, "UTF-8"));
                if (last.length() > 2000) last.delete(0, last.length() - 2000);
            }
            int exit = run.waitFor();
            if (exit != 0 || !out.isFile() || out.length() == 0) {
                throw new IOException("Real-ESRGAN exited with code " + exit + ". " + last.toString().trim());
            }
            Picture got = Awt.read(out);
            got.alpha = p.alpha;
            return got;
        } finally {
            in.delete();
            out.delete();
        }
    }

    /** The picture turned upright by its EXIF orientation (1-8): a phone photo is often stored on its side. */
    static Picture upright(Picture p, int o) {
        if (o <= 1 || o > 8) return p;
        boolean swap = o >= 5;
        Picture out = new Picture(swap ? p.h : p.w, swap ? p.w : p.h);
        out.alpha = p.alpha;
        for (int y = 0; y < p.h; y++) {
            for (int x = 0; x < p.w; x++) {
                int nx, ny;
                switch (o) {
                    case 2: nx = p.w - 1 - x; ny = y; break;
                    case 3: nx = p.w - 1 - x; ny = p.h - 1 - y; break;
                    case 4: nx = x; ny = p.h - 1 - y; break;
                    case 5: nx = y; ny = x; break;
                    case 6: nx = p.h - 1 - y; ny = x; break;
                    case 7: nx = p.h - 1 - y; ny = p.w - 1 - x; break;
                    default: nx = y; ny = p.w - 1 - x; break;
                }
                out.px[ny * out.w + nx] = p.px[y * p.w + x];
            }
        }
        return out;
    }

    /** A JPEG's EXIF orientation (1-8); 1 for anything else. */
    static int orientation(File f) {
        if (!f.getName().toLowerCase(Locale.ROOT).matches(".*\\.jpe?g")) return 1;
        try {
            byte[] d = new byte[(int) Math.min(f.length(), 256 * 1024)];
            InputStream in = new java.io.FileInputStream(f);
            try {
                int got = 0;
                for (int n; got < d.length && (n = in.read(d, got, d.length - got)) > 0; ) got += n;
            } finally {
                in.close();
            }
            if ((d[0] & 0xff) != 0xff || (d[1] & 0xff) != 0xd8) return 1;
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
        } catch (Exception ignored) {
            // read as stored
        }
        return 1;
    }

    private static int read(byte[] d, int at, int n, boolean little) {
        int v = 0;
        for (int k = 0; k < n; k++) v |= (d[at + k] & 0xff) << (8 * (little ? k : n - 1 - k));
        return v;
    }

    /**
     * Reading and writing pictures on the desktop: Java's ImageIO, by reflection, so the program also
     * compiles on the phone (whose Java has no java.awt or javax.imageio) and runs there with Droid.
     */
    static final class Awt {
        static Picture read(File f) throws Exception {
            Class<?> io = Class.forName("javax.imageio.ImageIO");
            Object img = io.getMethod("read", File.class).invoke(null, f);
            if (img == null) throw new IOException("unsupported or unreadable picture: " + f.getName());
            Class<?> bi = img.getClass();
            int w = ((Integer) bi.getMethod("getWidth").invoke(img)).intValue();
            int h = ((Integer) bi.getMethod("getHeight").invoke(img)).intValue();
            Picture p = new Picture(w, h);
            bi.getMethod("getRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class)
                .invoke(img, Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(w), Integer.valueOf(h), p.px, Integer.valueOf(0), Integer.valueOf(w));
            Object model = bi.getMethod("getColorModel").invoke(img);
            p.alpha = ((Boolean) model.getClass().getMethod("hasAlpha").invoke(model)).booleanValue();
            return p;
        }

        static void write(Picture p, File f, String format, int quality) throws Exception {
            boolean keepAlpha = p.alpha && !format.equals("jpg") && !format.equals("jpeg") && !format.equals("bmp");
            Class<?> bi = Class.forName("java.awt.image.BufferedImage");
            // TYPE_INT_ARGB 2, TYPE_INT_RGB 1.
            Object img = bi.getConstructor(int.class, int.class, int.class).newInstance(Integer.valueOf(p.w), Integer.valueOf(p.h), Integer.valueOf(keepAlpha ? 2 : 1));
            bi.getMethod("setRGB", int.class, int.class, int.class, int.class, int[].class, int.class, int.class)
                .invoke(img, Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(p.w), Integer.valueOf(p.h), p.px, Integer.valueOf(0), Integer.valueOf(p.w));
            Class<?> io = Class.forName("javax.imageio.ImageIO");
            Class<?> rendered = Class.forName("java.awt.image.RenderedImage");
            String kind = format.equals("jpeg") ? "jpg" : format;
            if (kind.equals("jpg")) {
                java.util.Iterator<?> it = (java.util.Iterator<?>) io.getMethod("getImageWritersByFormatName", String.class).invoke(null, "jpg");
                if (!it.hasNext()) throw new IOException("no JPEG writer");
                Object w = it.next();
                Class<?> writer = Class.forName("javax.imageio.ImageWriter");
                Class<?> paramClass = Class.forName("javax.imageio.ImageWriteParam");
                Object param = writer.getMethod("getDefaultWriteParam").invoke(w);
                // MODE_EXPLICIT 2.
                paramClass.getMethod("setCompressionMode", int.class).invoke(param, Integer.valueOf(2));
                paramClass.getMethod("setCompressionQuality", float.class).invoke(param, Float.valueOf(quality / 100f));
                Object out = io.getMethod("createImageOutputStream", Object.class).invoke(null, f);
                try {
                    writer.getMethod("setOutput", Object.class).invoke(w, out);
                    Class<?> iio = Class.forName("javax.imageio.IIOImage");
                    Class<?> meta = Class.forName("javax.imageio.metadata.IIOMetadata");
                    Object whole = iio.getConstructor(rendered, java.util.List.class, meta).newInstance(img, null, null);
                    writer.getMethod("write", meta, iio, paramClass).invoke(w, null, whole, param);
                } finally {
                    out.getClass().getMethod("close").invoke(out);
                    writer.getMethod("dispose").invoke(w);
                }
                return;
            }
            Object ok = io.getMethod("write", rendered, String.class, File.class).invoke(null, img, kind, f);
            if (!Boolean.TRUE.equals(ok)) throw new IOException("no writer for " + format);
        }
    }

    /** Reading and writing pictures on the phone: Android's BitmapFactory and Bitmap, by reflection (the program compiles as plain Java). */
    static final class Droid {
        static boolean here() {
            try {
                Class.forName("android.graphics.Bitmap");
                return true;
            } catch (Throwable ex) {
                return false;
            }
        }

        static Picture read(File f) throws Exception {
            Class<?> factory = Class.forName("android.graphics.BitmapFactory");
            Object bmp = factory.getMethod("decodeFile", String.class).invoke(null, f.getAbsolutePath());
            if (bmp == null) throw new IOException("unsupported or unreadable picture: " + f.getName());
            Class<?> bc = bmp.getClass();
            int w = ((Integer) bc.getMethod("getWidth").invoke(bmp)).intValue();
            int h = ((Integer) bc.getMethod("getHeight").invoke(bmp)).intValue();
            Picture p = new Picture(w, h);
            bc.getMethod("getPixels", int[].class, int.class, int.class, int.class, int.class, int.class, int.class)
                .invoke(bmp, p.px, 0, w, 0, 0, w, h);
            p.alpha = ((Boolean) bc.getMethod("hasAlpha").invoke(bmp)).booleanValue();
            bc.getMethod("recycle").invoke(bmp);
            return p;
        }

        static void write(Picture p, File f, String format, int quality) throws Exception {
            Class<?> bc = Class.forName("android.graphics.Bitmap");
            Class<?> config = Class.forName("android.graphics.Bitmap$Config");
            Object argb = config.getMethod("valueOf", String.class).invoke(null, "ARGB_8888");
            if (!p.alpha) for (int i = 0; i < p.px.length; i++) p.px[i] |= 0xff000000;
            Object bmp = bc.getMethod("createBitmap", int[].class, int.class, int.class, config).invoke(null, p.px, p.w, p.h, argb);
            Class<?> cf = Class.forName("android.graphics.Bitmap$CompressFormat");
            String name = format.equals("png") ? "PNG" : format.equals("webp") ? "WEBP" : "JPEG";
            Object kind = cf.getMethod("valueOf", String.class).invoke(null, name);
            OutputStream out = new FileOutputStream(f);
            try {
                Object ok = bc.getMethod("compress", cf, int.class, OutputStream.class).invoke(bmp, kind, Integer.valueOf(quality), out);
                if (!Boolean.TRUE.equals(ok)) throw new IOException("could not write " + f.getName());
            } finally {
                out.close();
                bc.getMethod("recycle").invoke(bmp);
            }
        }
    }

    /** The output in the work folder: on the phone PyJav's (pulsekit.work), where Pulsekit keeps it; an absolute path stays. */
    static File inWork(String name) {
        File f = new File(name);
        if (f.isAbsolute()) return f;
        String work = System.getProperty("pulsekit.work");
        String dir = work != null && work.length() > 0 ? work : System.getProperty("user.dir", ".");
        return new File(dir, name);
    }

    static String format(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) throw new IllegalArgumentException("the output name needs an extension, e.g. .png or .jpg");
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("png|jpg|jpeg|bmp|gif|webp")) throw new IllegalArgumentException("unsupported output format: " + ext);
        return ext;
    }

    static double number(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new NumberFormatException("not a number: " + s);
        }
    }

    /** "16:9", "16x9", "16/9" or "1.78" as width / height. */
    static double aspect(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.length() == 0 || t.equals("original")) return 0;
        String[] part = t.split("[:x/]");
        double v = part.length == 2 ? number(part[0]) / number(part[1]) : number(t);
        if (!(v > 0.05 && v < 20)) throw new NumberFormatException("--aspect is a shape such as 16:9 or 1:1, not " + s);
        return v;
    }

    static String size(long n) {
        if (n >= 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", n / (1024.0 * 1024));
        return Math.max(1, n / 1024) + " KB";
    }

    private static void usage() {
        System.out.println("Usage: java ImageUpscaler <input.png> [output.png] [--method 1|2] [--scale N] [--width N] [--height N] [--aspect W:H] [--fit crop|pad|stretch] [--quality N]");
        System.out.println("  --method 1 = bicubic resize, 2x by default (built in; phone and desktop)");
        System.out.println("  --method 2 = Real-ESRGAN AI upscaling at 4x (desktop; needs realesrgan-ncnn-vulkan on PATH or REALESRGAN_BIN)");
        System.out.println("Example: java ImageUpscaler photo.jpg photo-big.jpg --method 1 --width 2048 --aspect 4:3");
    }
}
