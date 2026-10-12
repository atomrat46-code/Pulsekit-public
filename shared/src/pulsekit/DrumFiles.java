package pulsekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drum programs' files that Pulsekit reads as MIDI: Hydrogen songs and patterns (.h2song,
 * .h2pattern) and Reaper projects (.rpp). Each is told by its content, whatever it is called.
 */
public final class DrumFiles {
  private DrumFiles() {}

  /** A Hydrogen file or a Reaper project. */
  public static boolean isDrumFile(byte[] data) {
    return Hydrogen.isHydrogen(data) || Reaper.isReaper(data);
  }

  /** The file as MIDI, with what was read. */
  public static final class Converted {
    public byte[] midi;
    public String summary = "";
    public int notes;
    public String midiName = "";
  }

  public static Converted toMidi(byte[] data, String name) throws Exception {
    Converted c = new Converted();
    if (Reaper.isReaper(data)) {
      Reaper.Result r = Reaper.toMidi(data);
      c.midi = r.midi;
      c.summary = Reaper.summary(r);
      c.notes = r.notes;
      c.midiName = Reaper.midiName(name);
    } else {
      Hydrogen.Result r = Hydrogen.toMidi(data);
      c.midi = r.midi;
      c.summary = Hydrogen.summary(r);
      c.notes = r.notes;
      c.midiName = Hydrogen.midiName(name);
    }
    return c;
  }

  /**
   * Program arguments with every Hydrogen or Reaper file swapped for its MIDI, written to dir
   * (CompareHits, Midi2Wav and the other programs read MIDI only).
   */
  public static List<String> midiArgs(List<String> argv, java.io.File dir) {
    if (argv == null) return argv;
    List<String> out = new ArrayList<String>(argv);
    for (int i = 0; i < out.size(); i++) {
      String a = out.get(i);
      String low = a.toLowerCase(Locale.ROOT);
      if (!low.endsWith(".h2song") && !low.endsWith(".h2pattern") && !low.endsWith(".xml") && !low.endsWith(".rpp")) continue;
      java.io.File f = new java.io.File(a);
      if (!f.isFile()) continue;
      try {
        byte[] data = readAll(f);
        if (!isDrumFile(data)) continue;
        Converted c = toMidi(data, f.getName());
        dir.mkdirs();
        java.io.File mid = new java.io.File(dir, c.midiName);
        java.io.FileOutputStream fos = new java.io.FileOutputStream(mid);
        try {
          fos.write(c.midi);
        } finally {
          fos.close();
        }
        out.set(i, mid.getAbsolutePath());
      } catch (Exception ex) {
        // Left as it is: the program says what it could not read.
      }
    }
    return out;
  }

  private static byte[] readAll(java.io.File f) throws java.io.IOException {
    java.io.FileInputStream in = new java.io.FileInputStream(f);
    try {
      java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) > 0) o.write(b, 0, n);
      return o.toByteArray();
    } finally {
      in.close();
    }
  }
}
