import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * VideoFrames: pictures from a video (MP4, WebM, MOV...): its first frame, its last frame, frames
 * at given times, or one every so many seconds, as PNG files named after the video
 * (clip-first.png, clip-last.png, clip-0m12s345.png).
 *
 * Usage: java VideoFrames <video.mp4> [--first] [--last] [--at 0:12.345,1:05] [--every N] [--dir <folder>] [--recursive] [--max N] [--outdir <folder>] [--upscale]
 *
 *   --first, --last   the first and the last frame (with no frame switch: the first).
 *   --at T,T...       frames at these times: seconds (12.5), m:ss.mmm (0:12.345) or h:mm:ss.
 *   --every N         a frame every N seconds, from the start.
 *   --dir <folder>    every video in the folder instead of one (--recursive: its subfolders too).
 *   --max N           the largest side in pixels (default: the video's own size).
 *   --outdir <folder> where the pictures go (default: beside PyJav's other output).
 *   --upscale         PyJav then opens ImageUpscaler with the first picture made.
 *
 * Inside Pulsekit the frames come from the app (Android's own decoder on the phone, VLC on the
 * desktop); elsewhere from ffmpeg, when it is installed.
 */
public final class VideoFrames {
  private VideoFrames() {}

  static final String USAGE = "Usage: java VideoFrames <video.mp4> [--first] [--last] [--at 0:12.345,1:05] [--every N] [--dir <folder>] [--recursive] [--max N] [--outdir <folder>] [--upscale]";

  public static void main(String[] args) throws Exception {
    int code = run(args);
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  static int run(String[] args) throws Exception {
    String input = null, dir = null, outdir = null;
    boolean first = false, last = false, recursive = false;
    List<Long> at = new ArrayList<Long>();
    double every = 0;
    int max = 0;
    try {
      for (int i = 0; i < args.length; i++) {
        String a = args[i];
        if (a.equals("--first")) first = true;
        else if (a.equals("--last")) last = true;
        else if (a.equals("--at") && i + 1 < args.length) {
          for (String t : args[++i].split("[,;]")) {
            if (t.trim().length() == 0) continue;
            long ms = time(t.trim());
            if (ms < 0) {
              System.out.println("Failed: --at takes times such as 12.5, 0:12.345 or 1:02:03, not " + t.trim());
              return 2;
            }
            at.add(ms);
          }
        } else if (a.equals("--every") && i + 1 < args.length) every = Double.parseDouble(args[++i].trim());
        else if (a.equals("--dir") && i + 1 < args.length) dir = args[++i];
        else if (a.equals("--recursive")) recursive = true;
        else if (a.equals("--max") && i + 1 < args.length) max = Integer.parseInt(args[++i].trim());
        else if (a.equals("--outdir") && i + 1 < args.length) outdir = args[++i];
        else if (a.equals("--upscale")) continue;
        else if (a.equals("-h") || a.equals("--help")) {
          System.out.println(USAGE);
          return 0;
        } else if (a.startsWith("--")) {
          System.out.println("Failed: unknown argument " + a);
          System.out.println(USAGE);
          return 2;
        } else if (input == null) input = a;
      }
    } catch (NumberFormatException ex) {
      System.out.println("Failed: " + ex.getMessage());
      return 2;
    }
    if (!first && !last && at.isEmpty() && every <= 0) first = true;
    List<File> videos = new ArrayList<File>();
    if (dir != null && dir.trim().length() > 0) {
      File d = new File(dir.trim());
      if (!d.isDirectory()) {
        System.out.println("Failed: " + dir + " is not a folder");
        return 1;
      }
      collect(d, recursive, videos);
      if (videos.isEmpty()) {
        System.out.println("Failed: no videos in " + d.getName());
        return 1;
      }
    } else if (input != null) {
      File f = new File(input);
      if (!f.isFile()) {
        System.out.println("Failed: " + input + " is not a file");
        return 1;
      }
      videos.add(f);
    } else {
      System.out.println("Failed: name a video (or a folder with --dir)");
      System.out.println(USAGE);
      return 2;
    }
    String work = System.getProperty("pulsekit.work");
    File out = outdir != null && outdir.trim().length() > 0 ? new File(outdir.trim()) : work != null && work.length() > 0 ? new File(work) : new File(".");
    if (!out.isDirectory() && !out.mkdirs()) {
      System.out.println("Failed: could not make " + out.getPath());
      return 1;
    }
    Source src = source();
    if (src == null) {
      System.out.println("Failed: nothing here reads video frames: run it in Pulsekit's PyJav, or install ffmpeg");
      return 1;
    }
    System.out.println("VideoFrames: " + videos.size() + " video" + (videos.size() == 1 ? "" : "s") + ", frames read by " + src.name());
    int made = 0, failed = 0;
    for (File v : videos) {
      List<Long> times = new ArrayList<Long>();
      List<String> names = new ArrayList<String>();
      String stem = v.getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
      if (first) {
        times.add(0L);
        names.add(stem + "-first.png");
      }
      for (long t : at) {
        times.add(t);
        names.add(stem + "-" + clock(t) + ".png");
      }
      if (every > 0) {
        long len = src.length(v.getAbsolutePath());
        if (len <= 0) System.out.println("Note: the length of " + v.getName() + " is not known, so --every is left out");
        for (long t = 0; len > 0 && t < len; t += Math.max(1, Math.round(every * 1000))) {
          if (times.contains(t)) continue;
          times.add(t);
          names.add(stem + "-" + clock(t) + ".png");
        }
      }
      if (last) {
        times.add(-1L);
        names.add(stem + "-last.png");
      }
      for (int k = 0; k < times.size(); k++) {
        byte[] png = src.frame(v.getAbsolutePath(), times.get(k), max);
        if (png == null || png.length == 0) {
          System.out.println("Could not read " + (times.get(k) < 0 ? "the last frame" : "the frame at " + shown(times.get(k))) + " of " + v.getName());
          failed++;
          continue;
        }
        File f = free(new File(out, names.get(k)));
        FileOutputStream fos = new FileOutputStream(f);
        try {
          fos.write(png);
        } finally {
          fos.close();
        }
        int[] wh = pngSize(png);
        System.out.println("Wrote " + f.getName() + (wh == null ? "" : " (" + wh[0] + "x" + wh[1] + ")"));
        made++;
      }
    }
    if (made == 0) {
      System.out.println("Failed: no frames could be read");
      return 1;
    }
    System.out.println("Succeeded: " + made + " frame" + (made == 1 ? "" : "s") + " from " + videos.size() + " video" + (videos.size() == 1 ? "" : "s")
        + (failed > 0 ? " (" + failed + " could not be read)" : ""));
    return 0;
  }

  /** Where the frames come from: Pulsekit's own reader, or ffmpeg. */
  interface Source {
    String name();

    long length(String path);

    byte[] frame(String path, long ms, int max);
  }

  static Source source() {
    // Inside Pulsekit: its class of frames (the phone's decoder, or VLC on the desktop).
    try {
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      if (cl == null) cl = VideoFrames.class.getClassLoader();
      final Class<?> app = Class.forName("pulsekit.VideoFrames", true, cl);
      final java.lang.reflect.Method frame = app.getMethod("frame", String.class, long.class, int.class);
      final java.lang.reflect.Method length = app.getMethod("length", String.class);
      return new Source() {
        public String name() {
          return "Pulsekit";
        }

        public long length(String path) {
          try {
            return ((Long) length.invoke(null, path)).longValue();
          } catch (Exception ex) {
            return 0;
          }
        }

        public byte[] frame(String path, long ms, int max) {
          try {
            return (byte[]) frame.invoke(null, path, Long.valueOf(ms), Integer.valueOf(max));
          } catch (Exception ex) {
            return null;
          }
        }
      };
    } catch (Throwable notInApp) {
      // ffmpeg below
    }
    if (!works("ffmpeg", "-version")) return null;
    return new Source() {
      public String name() {
        return "ffmpeg";
      }

      public long length(String path) {
        String said = output(new String[] {"ffmpeg", "-hide_banner", "-i", path});
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Duration: (\\d+):(\\d+):(\\d+)\\.(\\d+)").matcher(said);
        if (!m.find()) return 0;
        return (Long.parseLong(m.group(1)) * 3600 + Long.parseLong(m.group(2)) * 60 + Long.parseLong(m.group(3))) * 1000
            + Long.parseLong((m.group(4) + "000").substring(0, 3));
      }

      public byte[] frame(String path, long ms, int max) {
        try {
          File tmp = File.createTempFile("frame", ".png");
          tmp.delete();
          List<String> cmd = new ArrayList<String>();
          cmd.add("ffmpeg");
          cmd.add("-v");
          cmd.add("error");
          if (ms < 0) {
            cmd.add("-sseof");
            cmd.add("-0.25");
          } else {
            cmd.add("-ss");
            cmd.add(String.format(Locale.ROOT, "%.3f", ms / 1000.0));
          }
          cmd.add("-i");
          cmd.add(path);
          if (ms < 0) {
            // The last of the frames in the final quarter second.
            cmd.add("-update");
            cmd.add("1");
          } else {
            cmd.add("-frames:v");
            cmd.add("1");
          }
          if (max > 0) {
            cmd.add("-vf");
            cmd.add("scale='min(" + max + ",iw)':'min(" + max + ",ih)':force_original_aspect_ratio=decrease");
          }
          cmd.add(tmp.getAbsolutePath());
          Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
          output(p);
          if (!tmp.isFile()) return null;
          byte[] b = java.nio.file.Files.readAllBytes(tmp.toPath());
          tmp.delete();
          return b;
        } catch (Exception ex) {
          return null;
        }
      }
    };
  }

  static void collect(File dir, boolean recursive, List<File> into) {
    File[] kids = dir.listFiles();
    if (kids == null) return;
    java.util.Arrays.sort(kids);
    for (File k : kids) {
      if (k.isDirectory()) {
        if (recursive) collect(k, true, into);
      } else if (k.getName().toLowerCase(Locale.ROOT).matches(".+\\.(mp4|m4v|webm|mkv|3gp|mov)")) into.add(k);
    }
  }

  /** "12.5", "0:12.345", "1:02:03" as milliseconds; -1 when it is none of those. */
  static long time(String t) {
    try {
      String[] p = t.split(":");
      double s = 0;
      for (String part : p) s = s * 60 + Double.parseDouble(part.trim());
      return s < 0 ? -1 : Math.round(s * 1000);
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  /** "0m12s345", as the Media browser's captures are named. */
  static String clock(long ms) {
    return (ms / 60000) + "m" + String.format(Locale.ROOT, "%02d", (ms / 1000) % 60) + "s" + String.format(Locale.ROOT, "%03d", ms % 1000);
  }

  static String shown(long ms) {
    return (ms / 60000) + ":" + String.format(Locale.ROOT, "%02d.%03d", (ms / 1000) % 60, ms % 1000);
  }

  /** `f`, or "name (1).png"... when it is taken. */
  static File free(File f) {
    String name = f.getName();
    String stem = name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    String ext = name.substring(stem.length());
    File out = f;
    for (int n = 1; out.exists(); n++) out = new File(f.getParentFile(), stem + " (" + n + ")" + ext);
    return out;
  }

  /** A PNG's width and height from its header; null when it is not one. */
  static int[] pngSize(byte[] b) {
    if (b == null || b.length < 24 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') return null;
    int w = ((b[16] & 0xff) << 24) | ((b[17] & 0xff) << 16) | ((b[18] & 0xff) << 8) | (b[19] & 0xff);
    int h = ((b[20] & 0xff) << 24) | ((b[21] & 0xff) << 16) | ((b[22] & 0xff) << 8) | (b[23] & 0xff);
    return new int[] {w, h};
  }

  static boolean works(String... cmd) {
    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      output(p);
      return p.waitFor() == 0;
    } catch (Exception ex) {
      return false;
    }
  }

  static String output(String[] cmd) {
    try {
      return output(new ProcessBuilder(cmd).redirectErrorStream(true).start());
    } catch (Exception ex) {
      return "";
    }
  }

  static String output(Process p) throws java.io.IOException {
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    java.io.InputStream in = p.getInputStream();
    byte[] buf = new byte[8192];
    for (int n; (n = in.read(buf)) > 0; ) bos.write(buf, 0, n);
    try {
      p.waitFor();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    return bos.toString("UTF-8");
  }
}
