package pulsekit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Pads page's kits: the SoundFonts (.sf2) and SFZ kits added with Add SF2, listed in its drop-down
 * list; one picked there loads into the drum set selected. Each is kept in the shelf's folder (a copy:
 * the picked file may be a temporary one), or, for a lone .sfz with its sample folders beside it (a
 * computer), where it is. kits.txt lists them: a name, the file, and the .sfz in a package (a tab between).
 */
public final class KitShelf {
  private KitShelf() {}

  public static final String INDEX = "kits.txt";
  /** The largest SoundFont kept (the pads' kit needs its drums only: a whole General MIDI bank is not). */
  public static final long MAX_BYTES = 0x3000000L;

  /** A kit on the shelf: its name in the list, its file (in the shelf's folder, or a path), the .sfz in a package. */
  public static final class Kit {
    public String name = "";
    public String file = "";
    public String sfz = "";

    public String line() {
      return name.replace('\t', ' ') + "\t" + file + "\t" + sfz;
    }
  }

  public static boolean takes(String fileName) {
    String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    return n.endsWith(".sf2") || Sfz.isKitName(n);
  }

  public static List<Kit> list(File dir) {
    List<Kit> out = new ArrayList<Kit>();
    File f = new File(dir, INDEX);
    if (!f.isFile()) return out;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        if (line.trim().isEmpty()) continue;
        String[] p = line.split("\t", -1);
        Kit k = new Kit();
        k.name = p[0].trim();
        k.file = p.length > 1 ? p[1].trim() : "";
        k.sfz = p.length > 2 ? p[2].trim() : "";
        if (k.name.length() > 0 && k.file.length() > 0) out.add(k);
      }
    } catch (IOException ignored) {
      // none
    }
    return out;
  }

  public static void save(File dir, List<Kit> kits) throws IOException {
    dir.mkdirs();
    StringBuilder b = new StringBuilder();
    for (Kit k : kits) b.append(k.line()).append('\n');
    Files.write(new File(dir, INDEX).toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** The kit's file. */
  public static File file(File dir, Kit k) {
    File f = new File(k.file);
    return f.isAbsolute() ? f : new File(dir, k.file);
  }

  /**
   * Adds `src` (named `name`, as the picker said): a SoundFont, or each kit (.sfz) in an SFZ package, as
   * entries of their own. Returns the entries added (an entry of the same name is replaced). A package is
   * copied into the shelf; a lone .sfz stays where it is (its samples beside it).
   */
  public static List<Kit> add(File dir, File src, String name) throws IOException {
    if (name == null || name.isEmpty()) name = src.getName();
    String low = name.toLowerCase(Locale.ROOT);
    if (!takes(low)) throw new IOException(name + " is not a SoundFont (.sf2) or an SFZ kit (.sfz, or a .zip / .tar.gz with one)");
    if (src.length() > MAX_BYTES && low.endsWith(".sf2")) throw new IOException(name + " is too large (" + (src.length() >> 20) + " MB; at most " + (MAX_BYTES >> 20) + " MB)");
    dir.mkdirs();
    List<Kit> added = new ArrayList<Kit>();
    String base = name.replaceAll("(?i)\\.(sf2|sfz|zip|tgz|tar\\.gz|tar)$", "");
    if (low.endsWith(".sf2")) {
      // It must read: a SoundFont with no drums is refused here, not when picked.
      AudioIo.parseSf2(Files.readAllBytes(src.toPath()));
      Kit k = new Kit();
      k.name = base;
      k.file = copyIn(dir, src, name);
      added.add(k);
    } else if (low.endsWith(".sfz")) {
      Sfz.load(src, null);
      Kit k = new Kit();
      k.name = base;
      k.file = src.getAbsolutePath();
      added.add(k);
    } else {
      List<String> kits = Sfz.open(src).kits();
      if (kits.isEmpty()) throw new IOException("No .sfz kit in " + name);
      String file = copyIn(dir, src, name);
      for (String sfz : kits) {
        Kit k = new Kit();
        String kit = sfz.replaceAll(".*/", "").replaceAll("(?i)\\.sfz$", "");
        k.name = kits.size() == 1 ? base : base + ": " + kit;
        k.file = file;
        k.sfz = sfz;
        added.add(k);
      }
    }
    List<Kit> all = list(dir);
    for (Kit k : added) {
      for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).name.equalsIgnoreCase(k.name)) all.remove(i);
      all.add(k);
    }
    save(dir, all);
    return added;
  }

  /** Copies `src` into the shelf under a name no other kit has; returns that name. */
  static String copyIn(File dir, File src, String name) throws IOException {
    String clean = name.replaceAll("[\\\\/:*?\"<>|\t]", "_");
    File to = new File(dir, clean);
    for (int n = 2; to.exists() && !sameFile(to, src); n++) {
      int dot = clean.toLowerCase(Locale.ROOT).endsWith(".tar.gz") ? clean.length() - 7 : clean.lastIndexOf('.');
      to = new File(dir, dot > 0 ? clean.substring(0, dot) + " " + n + clean.substring(dot) : clean + " " + n);
    }
    if (!sameFile(to, src)) Files.copy(src.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
    return to.getName();
  }

  static boolean sameFile(File a, File b) {
    try {
      return a.getCanonicalPath().equals(b.getCanonicalPath());
    } catch (IOException ex) {
      return false;
    }
  }

  /** Takes the kit off the shelf (its copy too, when no other entry uses it). */
  public static void remove(File dir, Kit kit) throws IOException {
    List<Kit> all = list(dir);
    boolean used = false;
    for (int i = all.size() - 1; i >= 0; i--) {
      Kit k = all.get(i);
      if (k.name.equals(kit.name) && k.file.equals(kit.file) && k.sfz.equals(kit.sfz)) all.remove(i);
      else if (k.file.equals(kit.file)) used = true;
    }
    save(dir, all);
    if (!used && !new File(kit.file).isAbsolute()) new File(dir, kit.file).delete();
  }

  /** The kit's sounds (Engine's track order, at AudioIo.SR); null for a pad it has none for. */
  public static short[][] voices(File dir, Kit k) throws IOException {
    File f = file(dir, k);
    if (!f.isFile()) throw new IOException(k.name + ": its file is gone (" + f.getName() + ")");
    if (f.getName().toLowerCase(Locale.ROOT).endsWith(".sf2")) return AudioIo.parseSf2(Files.readAllBytes(f.toPath()));
    return Sfz.load(f, k.sfz.isEmpty() ? null : k.sfz).voices;
  }

  /** The list's first entry: Pulsekit's own sounds (no file). */
  public static final String DEFAULT = "Default kit";

  /** A SoundFont's own name (its INAM), or null. */
  public static String sf2Name(byte[] d) {
    if (d == null) return null;
    for (int i = 12; i + 8 < d.length && i < 4096; i++) {
      if (d[i] == 'I' && d[i + 1] == 'N' && d[i + 2] == 'A' && d[i + 3] == 'M') {
        int n = (d[i + 4] & 0xff) | ((d[i + 5] & 0xff) << 8);
        if (n <= 0 || i + 8 + n > d.length) return null;
        String name = new String(d, i + 8, n, StandardCharsets.ISO_8859_1).replace("\0", "").trim();
        return name.isEmpty() ? null : name;
      }
    }
    return null;
  }

  /**
   * The pads' own kit (kit.sf2: a SoundFont or SFZ kit loaded from File, or before the list was there)
   * onto the list under `name` (the file's name; else the SoundFont's own, else "Imported kit"), unless
   * it is there already; returns the name it has there, or null when there is no kit.sf2.
   */
  public static String adopt(File dir, File kitSf2, String name) {
    if (kitSf2 == null || !kitSf2.isFile() || kitSf2.length() < 64) return null;
    try {
      byte[] d = Files.readAllBytes(kitSf2.toPath());
      String base = name == null ? null : name.replaceAll("(?i)\\.(sf2|sfz|zip|tgz|tar\\.gz|tar)$", "").trim();
      if (base == null || base.isEmpty()) base = sf2Name(d);
      if (base == null || base.isEmpty() || base.equals("Pulsekit")) base = "Imported kit";
      for (Kit k : list(dir)) {
        File f = file(dir, k);
        if (f.isFile() && f.length() == d.length && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), d)) return k.name;
      }
      return add(dir, kitSf2, base + ".sf2").get(0).name;
    } catch (Exception ex) {
      return null;
    }
  }

  public static Kit named(File dir, String name) {
    for (Kit k : list(dir)) if (k.name.equals(name)) return k;
    return null;
  }
}
