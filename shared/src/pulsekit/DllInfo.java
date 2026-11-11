package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Windows plugin .dll's header, read without loading it: 32 or 64 bits, and the names it exports
 * (a VST2 exports VSTPluginMain or main, a VST3 GetPluginFactory). vst_render (64-bit Python) loads
 * only 64-bit plugins; a 32-bit VST2 plays in Carla's rack through Carla's 32-bit bridge (as
 * vst_render's dll_problem and bridged_state do); a .dll without those exports is not a plugin.
 */
public final class DllInfo {
  /** 32 or 64; 0 when the file is not a Windows program file. */
  public final int bits;
  public final Set<String> exports;

  private DllInfo(int bits, Set<String> exports) {
    this.bits = bits;
    this.exports = exports;
  }

  public static DllInfo read(File f) {
    Set<String> names = new LinkedHashSet<String>();
    byte[] d;
    try {
      long n = f.length();
      if (n < 0x40 || n > 256L * 1024 * 1024) return new DllInfo(0, names);
      d = java.nio.file.Files.readAllBytes(f.toPath());
    } catch (Exception ex) {
      return new DllInfo(0, names);
    }
    if (d[0] != 'M' || d[1] != 'Z') return new DllInfo(0, names);
    int pe = i32(d, 0x3C);
    if (pe < 0 || pe + 24 > d.length || d[pe] != 'P' || d[pe + 1] != 'E' || d[pe + 2] != 0 || d[pe + 3] != 0) return new DllInfo(0, names);
    int machine = u16(d, pe + 4), sections = u16(d, pe + 6), optSize = u16(d, pe + 20), opt = pe + 24;
    int magic = u16(d, opt);
    int bits = magic == 0x20B ? 64 : magic == 0x10B ? 32 : 0;
    if (machine == 0x8664 || machine == 0xAA64) bits = 64;
    else if (machine == 0x14C) bits = 32;
    try {
      int expRva = i32(d, opt + (magic == 0x20B ? 112 : 96));
      int table = opt + optSize;
      int at = expRva == 0 ? -1 : offset(d, table, sections, expRva);
      if (at >= 0) {
        int count = i32(d, at + 24), first = offset(d, table, sections, i32(d, at + 32));
        for (int i = 0; first >= 0 && i < Math.min(count, 5000); i++) {
          int o = offset(d, table, sections, i32(d, first + 4 * i));
          if (o < 0) continue;
          int end = o;
          while (end < d.length && d[end] != 0) end++;
          names.add(new String(d, o, end - o, java.nio.charset.StandardCharsets.ISO_8859_1));
        }
      }
    } catch (RuntimeException ignored) {
      // a header cut short: its bits are known still
    }
    return new DllInfo(bits, names);
  }

  private static int offset(byte[] d, int table, int sections, int rva) {
    for (int i = 0; i < sections; i++) {
      int sec = table + 40 * i;
      int vsize = i32(d, sec + 8), vaddr = i32(d, sec + 12), rsize = i32(d, sec + 16), raw = i32(d, sec + 20);
      if (rva >= vaddr && rva < vaddr + Math.max(vsize, rsize)) return rva - vaddr + raw;
    }
    return -1;
  }

  private static int u16(byte[] d, int at) {
    return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8;
  }

  private static int i32(byte[] d, int at) {
    return (d[at] & 0xFF) | (d[at + 1] & 0xFF) << 8 | (d[at + 2] & 0xFF) << 16 | (d[at + 3] & 0xFF) << 24;
  }

  public boolean plugin() {
    return exports.isEmpty() || exports.contains("VSTPluginMain") || exports.contains("main") || exports.contains("GetPluginFactory");
  }

  /** What is in the way of a .dll playing: null when it plays as it is (not a .dll, or a 64-bit plugin). */
  public static String problem(File f) {
    if (f == null) return null;
    String low = f.getName().toLowerCase(java.util.Locale.ROOT);
    // A SoundFont (x42 AVL Drumkits' kits too): vst_render plays it with Midi2Wav, no plugin needed.
    if (isSoundFont(f)) return null;
    // An LV2 plugin's library (avldrums.lv2's avldrums.dll): vst_render plays it in Carla's rack.
    if (isLv2(f)) return null;
    if (!low.endsWith(".dll") || !f.isFile()) return null;
    DllInfo i = read(f);
    if (i.bits == 0) return f.getName() + " is not a Windows plugin file";
    if (!i.plugin()) return f.getName() + " is not a VST plugin: it has no VSTPluginMain (a VST2's start), so it is some other program's library";
    if (i.bits == 32) return BRIDGED;
    return null;
  }

  /** A SoundFont (.sf2, .sfz): vst_render plays it as its plugin with Midi2Wav (no DawDreamer or Carla). */
  public static boolean isSoundFont(File f) {
    String low = f.getName().toLowerCase(java.util.Locale.ROOT);
    return low.endsWith(".sf2") || low.endsWith(".sfz");
  }

  public static final String SOUNDFONT = "A SoundFont: vst_render plays it with Midi2Wav (its drum kit: bank 128, else its first preset); no Carla or Edit settings";

  /** An LV2 plugin: a folder ending .lv2, or a file in one (vst_render plays it in Carla's rack). */
  public static boolean isLv2(File f) {
    File a = f.getAbsoluteFile();
    if (a.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lv2")) return true;
    File up = a.getParentFile();
    return up != null && up.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".lv2") && !isSoundFont(a);
  }

  /** problem's word for a 32-bit VST2 (it plays through Carla's bridge). */
  public static final String BRIDGED = "32";

  /** A 32-bit plugin's notice: how it plays, and whether Carla's rack is there for it. */
  public static String bridgedNote(File plugin, File rack) {
    return plugin.getName() + " is a 32-bit VST2 plugin. vst_render runs 64-bit, so it plays it in Carla's rack, through Carla's 32-bit bridge"
        + (rack != null ? " (Carla-Rack: " + rack.getPath() + "). Edit settings opens it in Carla's own program: double-click the plugin there for its own window, load the kit, save the project (Ctrl+S) and close Carla."
            : ".\nCarla-Rack was not found: install Carla (free at kx.studio/carla, with its 32-bit bridge), or add the folder Carla is in to Plugin folders.");
  }

  /** Carla's Carla-Rack plugin in `folders` (and Carla's usual places), looked through DEPTH deep; null when there is none. */
  public static File carlaRack(List<String> folders) {
    List<String> all = new ArrayList<String>(folders);
    for (String d : new String[] {"C:\\Program Files\\Carla", "C:\\Program Files (x86)\\Carla", "/usr/lib/carla", "/usr/local/lib/carla", "/Applications/Carla.app/Contents/MacOS"}) all.add(d);
    for (String d : all) {
      File hit = find(new File(d), 0);
      if (hit != null) return hit;
    }
    return null;
  }

  /**
   * Carla's rack plugin by its name: CarlaRack.dll, Carla-Rack.vst3, or on Windows (Carla 2.4 and newer)
   * CarlaVstShell.dll, the VST2 holding Carla-Rack and Carla's others; not the effect ones (CarlaRackFX, CarlaVstFxShell).
   */
  public static boolean isRack(File f) {
    String low = f.getName().toLowerCase(java.util.Locale.ROOT);
    int dot = low.lastIndexOf('.');
    if (dot < 0) return false;
    String ext = low.substring(dot);
    String stem = low.substring(0, dot).replaceAll("[^a-z]", "");
    return (ext.equals(".dll") || ext.equals(".vst3") || ext.equals(".so") || ext.equals(".vst")) && (stem.equals("carlarack") || stem.equals("carlavstshell"));
  }

  /** The rack found, kept for vst_render (~/.pulsekit/carla-rack.txt: it tries that first). */
  public static void keepRack(File home, File rack) {
    if (home == null || rack == null) return;
    try {
      home.mkdirs();
      java.nio.file.Files.write(new File(home, "carla-rack.txt").toPath(), rack.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // vst_render looks itself
    }
  }

  private static File find(File dir, int depth) {
    if (isRack(dir) && dir.exists()) return dir;
    File[] in = dir.listFiles();
    if (in == null) return null;
    java.util.Arrays.sort(in);
    for (File f : in) if (isRack(f)) return f;
    if (depth >= PluginLibrary.DEPTH) return null;
    for (File f : in) {
      if (!f.isDirectory() || f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".vst3")) continue;
      File hit = find(f, depth + 1);
      if (hit != null) return hit;
    }
    return null;
  }
}
