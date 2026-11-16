package pulsekit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The drum sets made on the Pads page, kept between runs: sets.txt in their folder (a line a set: its id,
 * name, the kit loaded into it, which pads match Original and which have a sound of their own), and each
 * set's sounds as <id>.sf2. The Original set's line keeps only the kit last loaded into it (its sounds are
 * Pulsekit's kit.sf2).
 */
public final class DrumSetStore {
  private DrumSetStore() {}

  public static final String INDEX = "sets.txt";

  /** The app's drum sets (Original first), for vst_render's Pads set list; null: none known. */
  public static volatile List<Engine.DrumSet> live;
  /** A set's sounds as the pads play it (Original's for the pads matching it), by its name; set by the app. */
  public static volatile java.util.function.Function<String, short[][]> voicesOf;

  /** The drum sets' names, Original first. */
  public static List<String> names() {
    List<String> out = new ArrayList<String>();
    List<Engine.DrumSet> sets = live;
    if (sets != null) for (Engine.DrumSet s : new ArrayList<Engine.DrumSet>(sets)) out.add(s.name);
    if (out.isEmpty()) out.add("Original");
    return out;
  }

  public static void save(File dir, List<Engine.DrumSet> sets) throws IOException {
    dir.mkdirs();
    StringBuilder b = new StringBuilder();
    List<String> keep = new ArrayList<String>();
    for (Engine.DrumSet s : sets) {
      StringBuilder match = new StringBuilder(), has = new StringBuilder();
      for (int t = 0; t < Engine.TRACK_ID.length; t++) {
        match.append(s.matchOrig[t] ? '1' : '0');
        has.append(s.samples[t] != null ? '1' : '0');
      }
      b.append(s.original ? "original" : s.id).append('\t').append(s.name.replace('\t', ' ')).append('\t').append(s.kit == null ? "" : s.kit.replace('\t', ' '))
          .append('\t').append(match).append('\t').append(has).append('\n');
      if (s.original) continue;
      keep.add(s.id + ".sf2");
      File f = new File(dir, s.id + ".sf2");
      short[][] v = new short[Engine.TRACK_ID.length][];
      for (int t = 0; t < v.length; t++) v[t] = s.samples[t];
      Files.write(f.toPath(), AudioIo.encodeSf2(v, s.name));
    }
    Files.write(new File(dir, INDEX).toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
    // Sets deleted: their sounds go too.
    File[] all = dir.listFiles();
    if (all != null) for (File f : all) if (f.getName().endsWith(".sf2") && !keep.contains(f.getName())) f.delete();
  }

  /**
   * The sets kept (Original first, with the kit last loaded into it); just Original when there are none.
   */
  public static List<Engine.DrumSet> load(File dir) {
    List<Engine.DrumSet> out = new ArrayList<Engine.DrumSet>();
    Engine.DrumSet original = Engine.DrumSet.originalSet();
    out.add(original);
    File f = new File(dir, INDEX);
    if (!f.isFile()) return out;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.split("\t", -1);
        if (p.length < 3 || p[0].trim().isEmpty()) continue;
        if (p[0].equals("original")) {
          original.kit = p[2];
          continue;
        }
        Engine.DrumSet s = Engine.DrumSet.isolated(p[0], p[1]);
        s.kit = p[2];
        String match = p.length > 3 ? p[3] : "", has = p.length > 4 ? p[4] : "";
        short[][] v = null;
        File sf = new File(dir, s.id + ".sf2");
        try {
          if (sf.isFile()) v = AudioIo.parseSf2(Files.readAllBytes(sf.toPath()));
        } catch (Exception ex) {
          v = null;
        }
        for (int t = 0; t < Engine.TRACK_ID.length; t++) {
          s.matchOrig[t] = t < match.length() ? match.charAt(t) == '1' : true;
          boolean own = t < has.length() && has.charAt(t) == '1';
          s.samples[t] = own && v != null && t < v.length ? v[t] : null;
          if (own && s.samples[t] == null && !s.matchOrig[t]) s.matchOrig[t] = true;
        }
        out.add(s);
      }
    } catch (IOException ignored) {
      // Original only
    }
    return out;
  }

  /** Puts a kit's sounds into a set (not Original): its pads with a sound take it, the others match Original. */
  public static void fill(Engine.DrumSet s, short[][] voices, String kit) {
    for (int t = 0; t < Engine.TRACK_ID.length; t++) {
      short[] v = voices != null && t < voices.length ? voices[t] : null;
      s.samples[t] = v;
      s.matchOrig[t] = v == null && !Engine.isChoke(t);
    }
    s.kit = kit == null ? "" : kit;
  }
}
