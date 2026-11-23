package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A drum set from a song's own drums (DrumMidi --make-set): its folder's set.txt (a line a drum: its note,
 * WAV file, hits, clear hits, length) read into the pads' sounds, each drum on the pad of its note (the first
 * pad with that note: 46 the open hat, 49 the crash, 51 the ride), mono at the pads' rate.
 */
public final class SongDrumSet {
  private SongDrumSet() {}

  /** DrumMidi's line naming the folder (SetMaker.SET_SAID). */
  public static final String SET_SAID = "Drum set from the song: ";
  public static final String INDEX = "set.txt";

  /** The set's folder named in a run's log, or null. */
  public static File folderIn(String log) {
    if (log == null) return null;
    File out = null;
    for (String line : log.split("\n")) {
      int at = line.indexOf(SET_SAID);
      if (at >= 0) out = new File(line.substring(at + SET_SAID.length()).trim());
    }
    return out != null && new File(out, INDEX).isFile() ? out : null;
  }

  /** The song's name (set.txt's first line says it), else the folder's. */
  public static String song(File dir) {
    try {
      String first = new String(Files.readAllBytes(new File(dir, INDEX).toPath()), StandardCharsets.UTF_8).split("\n")[0];
      int a = first.indexOf(" from "), b = first.lastIndexOf(" (DrumMidi");
      if (first.startsWith("#") && a >= 0 && b > a) return first.substring(a + 6, b).trim();
    } catch (Exception ignored) {
      // the folder's name
    }
    return dir.getName().replaceAll("(?i)(-dm)?-set$", "");
  }

  /** The pads' sounds (Engine.TRACK_ID order; null where the song gave none) at `rate`: each drum's first (best) hit. */
  public static short[][] voices(File dir, int rate) throws Exception {
    short[][][] all = layers(dir, rate);
    short[][] out = new short[all.length][];
    for (int t = 0; t < all.length; t++) out[t] = all[t] == null ? null : all[t][0];
    return out;
  }

  /** Whether some drum has more than one hit (DrumMidi --set-hits). */
  public static boolean hasLayers(short[][][] layers) {
    for (short[][] l : layers) if (l != null && l.length > 1) return true;
    return false;
  }

  /** Every hit of each pad's drum (the best first), at `rate`; null where the song gave none. */
  public static short[][][] layers(File dir, int rate) throws Exception {
    java.util.List<java.util.List<short[]>> got = new java.util.ArrayList<java.util.List<short[]>>();
    for (int t = 0; t < Engine.TRACK_ID.length; t++) got.add(new java.util.ArrayList<short[]>());
    for (String line : new String(Files.readAllBytes(new File(dir, INDEX).toPath()), StandardCharsets.UTF_8).split("\n")) {
      if (line.startsWith("#") || line.trim().isEmpty()) continue;
      String[] w = line.split("\t");
      if (w.length < 2) continue;
      int note;
      try {
        note = Integer.parseInt(w[0].trim());
      } catch (NumberFormatException e) {
        continue;
      }
      int track = -1;
      for (int t = 0; t < Engine.NOTES.length && track < 0; t++) if (Engine.NOTES[t] == note && !Engine.isChoke(t)) track = t;
      File wav = new File(dir, w[1].trim());
      if (track < 0 || !wav.isFile()) continue;
      AudioIo.Pcm pcm = AudioIo.parseWav(Files.readAllBytes(wav.toPath()));
      if (pcm == null || pcm.samples.length == 0) continue;
      got.get(track).add(toShorts(resample(pcm.samples, pcm.sr, rate)));
    }
    short[][][] out = new short[Engine.TRACK_ID.length][][];
    for (int t = 0; t < out.length; t++) if (!got.get(t).isEmpty()) out[t] = got.get(t).toArray(new short[0][]);
    return out;
  }

  /**
   * The drum sets in a zip (folders with a set.txt, as DrumMidi --make-set and --combine-sets write them,
   * the folder itself zipped or several of them) unpacked under `to`, a folder each: a combined set first,
   * then the others by name. Only the sets' own files (no paths out of `to`). None found: empty.
   */
  public static java.util.List<File> fromZip(File zip, File to) throws java.io.IOException {
    java.util.Set<String> dirs = new java.util.TreeSet<String>();
    java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip);
    try {
      for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
        String n = en.nextElement().getName().replace('\\', '/');
        if (n.equals(INDEX) || n.endsWith("/" + INDEX)) dirs.add(n.substring(0, n.length() - INDEX.length()));
      }
      java.util.List<File> out = new java.util.ArrayList<File>();
      int k = 0;
      for (String d : dirs) {
        String name = d.isEmpty() ? zip.getName().replaceAll("(?i)\\.zip$", "") : d.substring(0, d.length() - 1).replaceAll(".*/", "");
        File dir = new File(to, (k++) + "-" + name.replaceAll("[\\\\/:*?\"<>|]", "_"));
        dir.mkdirs();
        for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
          java.util.zip.ZipEntry e = en.nextElement();
          String n = e.getName().replace('\\', '/');
          if (e.isDirectory() || !n.startsWith(d) || n.substring(d.length()).contains("/")) continue;
          java.io.InputStream in = z.getInputStream(e);
          try {
            Files.copy(in, new File(dir, n.substring(d.length())).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } finally {
            in.close();
          }
        }
        out.add(dir);
      }
      // A combined set (--combine-sets) first: the best of the others.
      java.util.List<File> sorted = new java.util.ArrayList<File>();
      for (File f : out) if (isCombined(f)) sorted.add(f);
      for (File f : out) if (!isCombined(f)) sorted.add(f);
      return sorted;
    } finally {
      z.close();
    }
  }

  /** A combined set's name from its zip's ("Doom drum sets.zip" is "Doom"): the songs it came from. */
  public static String zipName(String zip) {
    String n = (zip == null ? "" : zip).replaceAll("(?i)\\.zip$", "").replaceAll("(?i)[ _-]*(drum[ _-]*sets?|sets?|combined)$", "").trim();
    return n.isEmpty() ? "Song" : n;
  }

  /** Whether the set was made by --combine-sets (its set.txt says so). */
  public static boolean isCombined(File dir) {
    try {
      return new String(Files.readAllBytes(new File(dir, INDEX).toPath()), StandardCharsets.UTF_8).split("\n")[0].contains("--combine-sets");
    } catch (Exception ex) {
      return false;
    }
  }

  /** How many pads have a sound. */
  public static int count(short[][] voices) {
    int n = 0;
    for (short[] v : voices) if (v != null) n++;
    return n;
  }

  /** Linear interpolation, a short average first when it goes down (no harsh aliasing from cymbals). */
  static float[] resample(float[] x, int from, int to) {
    if (from <= 0 || from == to) return x;
    float[] src = x;
    if (to < from) {
      int k = Math.max(1, (int) Math.round(from / (double) to));
      src = new float[x.length];
      for (int i = 0; i < x.length; i++) {
        float s = 0;
        int n = 0;
        for (int j = Math.max(0, i - k / 2); j <= Math.min(x.length - 1, i + k / 2); j++, n++) s += x[j];
        src[i] = s / n;
      }
    }
    int n = Math.max(1, (int) ((long) src.length * to / from));
    float[] y = new float[n];
    double step = from / (double) to;
    for (int i = 0; i < n; i++) {
      double p = i * step;
      int a = (int) p;
      double f = p - a;
      float u = src[Math.min(a, src.length - 1)], v = src[Math.min(a + 1, src.length - 1)];
      y[i] = (float) (u + (v - u) * f);
    }
    return y;
  }

  static short[] toShorts(float[] x) {
    short[] y = new short[x.length];
    for (int i = 0; i < x.length; i++) y[i] = (short) Math.max(-32768, Math.min(32767, Math.round(x[i] * 32767f)));
    return y;
  }
}
