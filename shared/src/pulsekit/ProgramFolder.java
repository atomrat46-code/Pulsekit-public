package pulsekit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Where PyJav keeps the files a program makes (SogniChat's replies and results, SogniMusic's
 * tracks, CutWav's cuts), chosen in File > Drum Midi Settings. Empty means Downloads. On Android it
 * is a folder picked with the system picker (a document tree the app may write to); on the desktop
 * a folder path. Kept in a small file beside the Sogni key copy.
 */
public final class ProgramFolder {
  private static File stored;
  private static String value;

  private ProgramFolder() {}

  /** Reads the setting kept in `dir`. */
  public static void init(File dir) {
    stored = dir == null ? null : new File(dir, "program_folder");
    value = null;
    try {
      if (stored != null && stored.isFile()) {
        String v = new String(java.nio.file.Files.readAllBytes(stored.toPath()), StandardCharsets.UTF_8).trim();
        value = v.length() == 0 ? null : v;
      }
    } catch (IOException ex) {
      value = null;
    }
  }

  /** The chosen folder (an Android tree URI or a desktop path), or null for Downloads. */
  public static String get() {
    return value;
  }

  public static void set(String folder) throws IOException {
    String v = folder == null ? "" : folder.trim();
    if (stored != null) {
      File dir = stored.getParentFile();
      if (dir != null && !dir.isDirectory()) dir.mkdirs();
      FileOutputStream out = new FileOutputStream(stored);
      try {
        out.write(v.getBytes(StandardCharsets.UTF_8));
      } finally {
        out.close();
      }
    }
    value = v.length() == 0 ? null : v;
  }

  public static void clear() {
    if (stored != null && stored.isFile()) stored.delete();
    value = null;
  }

  /**
   * The folder as people read it: "Music/Sogni" for an Android tree URI whose document id is
   * "primary:Music/Sogni", the path on the desktop, or "Downloads" when none is chosen.
   */
  public static String label() {
    if (value == null) return "Downloads";
    if (!value.startsWith("content:")) return value;
    try {
      String v = java.net.URLDecoder.decode(value, "UTF-8");
      int tree = v.indexOf("/tree/");
      String id = tree >= 0 ? v.substring(tree + 6) : v;
      int slash = id.indexOf("/document/");
      if (slash >= 0) id = id.substring(0, slash);
      int colon = id.indexOf(':');
      String path = colon >= 0 ? id.substring(colon + 1) : id;
      return path.length() == 0 ? "the chosen folder (top level)" : path;
    } catch (Exception ex) {
      return "the chosen folder";
    }
  }

  /** DrumMidi_CRT (its jar or its source). */
  public static boolean isDrumMidi(String program) {
    return "DrumMidi_CRT.jar".equals(program) || "DrumMidi_CRT.java".equals(program);
  }

  /** SogniMusic's --outdir: where Pulsekit keeps the run's tracks, WAVs and zip, as typed (% codes in it); null when not given. */
  public static String outdirArg(String program, java.util.List<String> argv) {
    if (!"SogniMusic.java".equals(program) || argv == null) return null;
    int at = argv.indexOf("--outdir");
    if (at < 0 || at + 1 >= argv.size()) return null;
    String v = argv.get(at + 1) == null ? "" : argv.get(at + 1).trim();
    return v.length() == 0 || v.startsWith("--") ? null : v;
  }

  /**
   * --outdir's folder with its % codes filled in for today (as Downloads' subfolders: %ddmmyy,
   * %n the first number whose folder is not there yet), under `base` unless it is a whole path.
   */
  public static File outdir(String pattern, final File base) {
    String p = pattern.trim().replace('\\', '/');
    if (new File(p).isAbsolute()) {
      // A whole path: the part before the first % code is the folder the rest goes under.
      int pct = p.indexOf('%');
      if (pct < 0) return new File(p);
      int slash = p.lastIndexOf('/', pct);
      final File root = new File(slash <= 0 ? "/" : p.substring(0, slash));
      return new File(root, DownloadMove.expand(p.substring(slash + 1), java.util.Calendar.getInstance(), head -> new File(root, head).exists()));
    }
    return new File(base, subdir(pattern, base));
  }

  /** --outdir's folder under `base` (Download on the phone), its % codes filled in: a relative path ("" for none). */
  public static String subdir(String pattern, final File base) {
    return DownloadMove.expand(pattern.trim().replace('\\', '/').replaceAll("^/+", ""), java.util.Calendar.getInstance(), head -> base != null && new File(base, head).exists());
  }

  /**
   * A batch's folders made whole on the desktop, where a program runs in a temporary folder that
   * goes when it ends: MidiDrumGen's --outdir (with --count and no --outdir, midi-batch-<seed> with
   * a --seed added), MidiBatch's --dir, --list and --outdir (midi-mix-<seed> when not given),
   * Midi2Wav's --dir and --kits, DrumMidi_CRT's --zip_outdir (and with --zip on one WAV, the program files folder),
   * vst_render.py's --dir and --outdir, and CompareHits' --batch, when given as a
   * plain name, are under `base` (the program files folder).
   */
  public static java.util.List<String> batchFolders(String program, java.util.List<String> argv, File base) {
    if (argv == null || base == null || program == null) return argv;
    java.util.List<String> out = new java.util.ArrayList<String>(argv);
    String[] keys = "MidiDrumGen.java".equals(program) ? new String[] {"--outdir"}
        : "vst_render.py".equals(program) ? new String[] {"--dir", "--outdir"}
        : "CompareHits.java".equals(program) ? new String[] {"--batch"}
        : "VideoFrames.java".equals(program) ? new String[] {"--dir", "--outdir"}
        : "MidiBatch.java".equals(program) ? new String[] {"--dir", "--list", "--outdir"}
        : "Midi2Wav.java".equals(program) ? new String[] {"--dir", "--kits"}
        : "SogniMusic.java".equals(program) ? new String[] {"--batchdir"}
        : isDrumMidi(program) ? new String[] {"--zip_outdir"} : new String[0];
    if (keys.length == 0) return argv;
    for (int i = 0; i + 1 < out.size(); i++) {
      String a = out.get(i) == null ? "" : out.get(i).trim();
      for (String k : keys) {
        if (!k.equals(a)) continue;
        String v = out.get(i + 1) == null ? "" : out.get(i + 1).trim();
        if (v.length() > 0 && !v.startsWith("-") && !new File(v).isAbsolute()) out.set(i + 1, new File(base, v).getAbsolutePath());
      }
    }
    // DrumMidi_CRT --zip (one WAV): the zip kept in the program files folder (the run's own folder goes when it ends).
    if (isDrumMidi(program) && (out.contains("--zip") || out.contains("--zip_mb")) && !out.contains("--zip_outdir") && !out.contains("--dir")) {
      out.add("--zip_outdir");
      out.add(base.getAbsolutePath());
    }
    boolean mix = "MidiBatch.java".equals(program);
    if ((mix || "MidiDrumGen.java".equals(program) && out.contains("--count")) && !out.contains("--outdir")) {
      String seed = null;
      int at = out.indexOf("--seed");
      if (at >= 0 && at + 1 < out.size()) seed = out.get(at + 1).trim();
      if (seed == null || !seed.matches("\\d+")) {
        seed = String.valueOf(System.currentTimeMillis() % 1000000L);
        if (at >= 0 && at + 1 < out.size()) out.set(at + 1, seed);
        else {
          out.add("--seed");
          out.add(seed);
        }
      }
      out.add("--outdir");
      out.add(new File(base, (mix ? "midi-mix-" : "midi-batch-") + seed).getAbsolutePath());
    }
    return out;
  }
}
