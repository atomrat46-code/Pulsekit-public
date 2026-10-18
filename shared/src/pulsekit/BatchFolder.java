package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * A batch folder shared by several sources (MidiDrumGen --count --outdir, MidiBatch, SogniMusic's
 * --batchdir): a MIDI file added with its row in the folder's batch.txt (made with MidiDrumGen's
 * columns when there is none; else in the columns it has), so one vst_render.py --dir --test run
 * renders them all and CompareHits --batch sums them.
 */
public final class BatchFolder {
  private BatchFolder() {}

  static final String HEADER = "file\tstyle\tbase\ttempo\ttimesig\tbars\tswing\tintensity\thats\thumanize\tfills\tcrashes\thalftime";

  /** The MIDI added as `name` (a free name there) with its columns (column name to value); the file name used. */
  public static synchronized String add(File dir, String name, byte[] midi, Map<String, String> cols) throws java.io.IOException {
    if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("could not make the folder " + dir.getPath());
    File out = MediaDir.freeName(dir, name);
    java.nio.file.Files.write(out.toPath(), midi);
    File list = new File(dir, "batch.txt");
    String[] header = null;
    if (list.isFile()) {
      for (String line : new String(java.nio.file.Files.readAllBytes(list.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String t = line.replace("\r", "");
        if (t.trim().length() == 0 || t.startsWith("#")) continue;
        header = t.split("\t", -1);
        break;
      }
    }
    StringBuilder add = new StringBuilder();
    if (header == null) {
      header = HEADER.split("\t");
      add.append(HEADER).append('\n');
    }
    for (int h = 0; h < header.length; h++) {
      String v = header[h].equals("file") ? out.getName() : cols.get(header[h]);
      add.append(h == 0 ? "" : "\t").append(v == null ? "" : v.replace('\t', ' ').replace('\n', ' '));
    }
    add.append('\n');
    java.io.FileOutputStream fos = new java.io.FileOutputStream(list, true);
    try {
      fos.write(add.toString().getBytes(StandardCharsets.UTF_8));
    } finally {
      fos.close();
    }
    return out.getName();
  }

  /** How many files batch.txt in `dir` lists. */
  public static int listed(File dir) {
    File list = new File(dir, "batch.txt");
    int rows = -1;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(list.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String t = line.replace("\r", "");
        if (t.trim().length() > 0 && !t.startsWith("#")) rows++;
      }
    } catch (Exception ex) {
      return 0;
    }
    return Math.max(0, rows);
  }
}
