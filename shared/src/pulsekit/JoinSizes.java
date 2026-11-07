package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Joining videos of other sizes or shapes: Pulsekit re-renders them first (the programs cannot, they
 * have no video encoder). JoinVideo --size WxH|largest [--fit crop|pad] [--area centre|top|bottom]:
 * every video is made again at that size before the run (the same encoder and settings, so they join
 * without more re-encoding); largest (the default) is the largest video's size. SogniVideo
 * --join_size WxH|largest [--join_fit] [--join_area]: after the run, the new clip and the video to join
 * are made at that size and joined, <clip>-merged.mp4. Each app gives the Sizer (VLC on a computer,
 * the phone's own encoder).
 */
public final class JoinSizes {
  private JoinSizes() {}

  /** What the app does: a video's size, a video made again at a size (with its sound), a file kept. */
  public interface Sizer {
    /** The video's picture size {w, h}; null when it cannot be read. */
    int[] size(File video);

    /** `src` made again as `dst` at w×h (crop or bars, the part kept `anchor`), its sound with it. */
    void render(File src, File dst, int w, int h, boolean pad, double anchor) throws Exception;

    /** A file the run made, kept where the app keeps them (the phone's Download); where, or null when the app keeps it itself. */
    String keep(String name, byte[] data);
  }

  /** Set by the app as it starts; null: no re-rendering (the programs join as they are). */
  public static volatile Sizer sizer;

  public static final String SIZE = "--size", FIT = "--fit", AREA = "--area";
  public static final String JOIN_SIZE = "--join_size", JOIN_FIT = "--join_fit", JOIN_AREA = "--join_area";
  public static final String LARGEST = "largest";

  /** JoinVideo with --size. */
  public static boolean before(String program, List<String> argv) {
    return "JoinVideo.java".equals(program) && argv != null && argv.contains(SIZE);
  }

  /** SogniVideo with --join and --join_size. */
  public static boolean after(String program, List<String> argv) {
    return "SogniVideo.java".equals(program) && argv != null && argv.contains("--join") && argv.contains(JOIN_SIZE);
  }

  static String value(List<String> argv, String key) {
    int at = argv.indexOf(key);
    if (at < 0 || at + 1 >= argv.size()) return null;
    String v = argv.get(at + 1) == null ? "" : argv.get(at + 1).trim();
    return v.startsWith("--") ? null : v;
  }

  /** The size asked ("1080x1920"), or the largest of the videos' (largest, or nothing given); null when none can be read. */
  static int[] target(String spec, List<File> videos, Sizer s) {
    int[] given = spec == null || LARGEST.equalsIgnoreCase(spec) ? null : VideoFit.parseSize(spec);
    if (given != null) return given;
    List<int[]> sizes = new ArrayList<int[]>();
    for (File v : videos) sizes.add(s.size(v));
    int[] big = VideoFit.largest(sizes);
    return big == null ? null : new int[] {big[0] & ~1, big[1] & ~1};
  }

  static double anchor(String area) {
    if (area == null) return 0.5;
    String a = area.toLowerCase(java.util.Locale.ROOT);
    return a.startsWith("top") || a.startsWith("left") ? 0 : a.startsWith("bottom") || a.startsWith("right") ? 1 : 0.5;
  }

  /** The arguments without `keys` and their values. */
  static List<String> without(List<String> argv, String... keys) {
    List<String> out = new ArrayList<String>();
    for (int i = 0; i < argv.size(); i++) {
      String a = argv.get(i);
      boolean drop = false;
      for (String k : keys) if (k.equals(a)) drop = true;
      if (drop) {
        if (i + 1 < argv.size() && !String.valueOf(argv.get(i + 1)).startsWith("--")) i++;
        continue;
      }
      out.add(a);
    }
    return out;
  }

  static boolean isVideo(String a) {
    return a != null && a.toLowerCase(java.util.Locale.ROOT).matches(".+\\.(mp4|m4v|mov)") && new File(a).isFile();
  }

  /**
   * JoinVideo --size: each video (given, --video_c..f, or in --list) made again at the size into `tmp`
   * (its own name kept, so the joined video's name is the same); the arguments with them in their
   * place, --size, --fit and --area left out. What was done goes into `log`.
   */
  public static List<String> prepare(List<String> argv, File tmp, Sizer s, StringBuilder log) throws Exception {
    List<String> args = new ArrayList<String>(argv);
    String spec = value(args, SIZE);
    boolean pad = "pad".equalsIgnoreCase(String.valueOf(value(args, FIT)));
    double anchor = anchor(value(args, AREA));
    args = without(args, SIZE, FIT, AREA);
    // The videos, and where each is in the arguments (-1: a --list line).
    List<File> videos = new ArrayList<File>();
    List<Integer> where = new ArrayList<Integer>();
    for (int i = 0; i < args.size(); i++) {
      String a = args.get(i);
      if (a != null && a.matches("--video_[c-f]") && i + 1 < args.size()) {
        if (isVideo(args.get(i + 1))) {
          videos.add(new File(args.get(i + 1)));
          where.add(i + 1);
        }
        i++;
      } else if ("--list".equals(a) && i + 1 < args.size()) {
        i++;
      } else if (isVideo(a)) {
        videos.add(new File(a));
        where.add(i);
      }
    }
    int listAt = args.indexOf("--list");
    List<String> listed = null;
    if (listAt >= 0 && listAt + 1 < args.size()) {
      listed = new ArrayList<String>();
      for (String line : new String(read(new File(args.get(listAt + 1))), StandardCharsets.UTF_8).split("\n")) {
        String t = line.trim();
        if (isVideo(t)) {
          listed.add(t);
          videos.add(new File(t));
          where.add(-1);
        }
      }
    }
    if (videos.isEmpty()) return args;
    int[] wh = target(spec, videos, s);
    if (wh == null) throw new java.io.IOException("the videos' sizes could not be read");
    log.append("Making the videos ").append(wh[0]).append('×').append(wh[1]).append(pad ? " (black bars)" : " (cropped)").append(" before joining\n");
    if (!tmp.isDirectory()) tmp.mkdirs();
    List<String> newList = new ArrayList<String>();
    for (int k = 0; k < videos.size(); k++) {
      File v = videos.get(k);
      File d = new File(new File(tmp, String.valueOf(k)), v.getName());
      d.getParentFile().mkdirs();
      s.render(v, d, wh[0], wh[1], pad, anchor);
      log.append("Made ").append(v.getName()).append(" at ").append(wh[0]).append('×').append(wh[1]).append('\n');
      if (where.get(k) >= 0) args.set(where.get(k), d.getAbsolutePath());
      else newList.add(d.getAbsolutePath());
    }
    if (listed != null) {
      File list = new File(tmp, "list.txt");
      StringBuilder sb = new StringBuilder();
      for (String l : newList) sb.append(l).append('\n');
      write(list, sb.toString().getBytes(StandardCharsets.UTF_8));
      args.set(listAt + 1, list.getAbsolutePath());
    }
    return args;
  }

  /**
   * SogniVideo --join_size, after the run: its new clip (`clip`, named `clipName`) and --join's video
   * made at the size and joined (the other first with --join_first) into `tmp`; the joined file.
   */
  public static File joinAfter(List<String> argv, File clip, String clipName, File tmp, Sizer s, StringBuilder log) throws Exception {
    File other = new File(value(argv, "--join"));
    if (!other.isFile()) throw new java.io.IOException("no video to join: " + other.getName());
    String spec = value(argv, JOIN_SIZE);
    boolean pad = "pad".equalsIgnoreCase(String.valueOf(value(argv, JOIN_FIT)));
    double anchor = anchor(value(argv, JOIN_AREA));
    List<File> both = new ArrayList<File>();
    both.add(clip);
    both.add(other);
    int[] wh = target(spec, both, s);
    if (wh == null) throw new java.io.IOException("the videos' sizes could not be read");
    if (!tmp.isDirectory()) tmp.mkdirs();
    File a = new File(tmp, "clip.mp4"), b = new File(tmp, "other.mp4");
    s.render(clip, a, wh[0], wh[1], pad, anchor);
    s.render(other, b, wh[0], wh[1], pad, anchor);
    boolean otherFirst = argv.contains("--join_first");
    File merged = new File(tmp, clipName.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-merged.mp4");
    List<File> order = new ArrayList<File>();
    order.add(otherFirst ? b : a);
    order.add(otherFirst ? a : b);
    String note = Mp4Media.join(order, merged);
    log.append("Joined ").append(otherFirst ? other.getName() : clipName).append(" and then ").append(otherFirst ? clipName : other.getName())
        .append(" at ").append(wh[0]).append('×').append(wh[1]).append(": wrote ").append(merged.getName()).append('\n');
    if (note.length() > 0) log.append("Note: ").append(note).append('\n');
    return merged;
  }

  static byte[] read(File f) throws java.io.IOException {
    java.io.FileInputStream in = new java.io.FileInputStream(f);
    try {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[1 << 16];
      for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
      return out.toByteArray();
    } finally {
      in.close();
    }
  }

  static void write(File f, byte[] data) throws java.io.IOException {
    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  static void clean(File dir) {
    File[] in = dir.listFiles();
    if (in != null) for (File f : in) {
      if (f.isDirectory()) clean(f);
      f.delete();
    }
    dir.delete();
  }

  /** JavaRun's step: JoinVideo's videos made at the size first, or SogniVideo's join made after; the run's result. */
  static JavaRun.Result around(String name, List<String> argv, java.util.function.Function<List<String>, JavaRun.Result> run) {
    Sizer s = sizer;
    if (s == null || !(before(name, argv) || after(name, argv))) return run.apply(argv);
    File tmp = new File(new File(System.getProperty("java.io.tmpdir", ".")), "pulsekit-join-" + System.nanoTime());
    StringBuilder log = new StringBuilder();
    try {
      if (before(name, argv)) {
        List<String> args;
        try {
          args = prepare(argv, tmp, s, log);
        } catch (Exception ex) {
          return new JavaRun.Result(log + "Failed: could not make the videos one size: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()), new ArrayList<JavaRun.FileOut>(), 1);
        }
        JavaRun.Result r = run.apply(args);
        return new JavaRun.Result(log + r.log, r.files, r.code);
      }
      JavaRun.Result r = run.apply(argv);
      if (r.code != 0) return r;
      JavaRun.FileOut clip = null;
      for (JavaRun.FileOut f : r.files) if (clip == null && f.name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4") && !f.name.contains("-merged")) clip = f;
      if (clip == null) return r;
      try {
        tmp.mkdirs();
        File c = new File(tmp, clip.name);
        write(c, clip.bytes);
        File merged = joinAfter(argv, c, clip.name, new File(tmp, "join"), s, log);
        byte[] data = read(merged);
        List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>(r.files);
        files.add(new JavaRun.FileOut(merged.getName(), data));
        String where = s.keep(merged.getName(), data);
        if (where != null) log.append("saved ").append(where).append('\n');
        return new JavaRun.Result(r.log + "\n" + log, files, r.code);
      } catch (Exception ex) {
        return new JavaRun.Result(r.log + "\n" + log + "Could not join with the other video at one size: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage())
            + " (the clip is saved as it is)", r.files, r.code);
      }
    } finally {
      clean(tmp);
    }
  }
}
