package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Starred programs: a long press (a right click on the desktop) on a program under Select program
 * stars it, and its name then shows "★ " in the program lists. Kept beside the library
 * (program-stars.txt, one name a line), the same on the phone and the desktop.
 */
public final class ProgramStars {
  private static File file;
  private static final List<String> NAMES = new ArrayList<String>();

  private ProgramStars() {}

  public static synchronized void load(File dir) {
    file = dir == null ? null : new File(dir, "program-stars.txt");
    NAMES.clear();
    if (file == null || !file.isFile()) return;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
        String n = line.trim();
        if (n.length() > 0 && !NAMES.contains(n)) NAMES.add(n);
      }
    } catch (Exception ignored) {
      // none starred
    }
  }

  public static synchronized boolean is(String program) {
    return program != null && NAMES.contains(program);
  }

  /** Stars the program, or takes its star off; true when it is starred now. */
  public static synchronized boolean toggle(String program) {
    if (program == null || program.length() == 0) return false;
    boolean now = !NAMES.remove(program);
    if (now) NAMES.add(program);
    if (file != null) {
      StringBuilder sb = new StringBuilder();
      for (String n : NAMES) sb.append(n).append('\n');
      try {
        if (!file.getParentFile().isDirectory()) file.getParentFile().mkdirs();
        java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
      } catch (Exception ignored) {
        // kept for this session
      }
    }
    return now;
  }

  /** "★ SogniPedit.java" for a starred program, else its name. */
  public static synchronized String label(String program) {
    return is(program) ? "★ " + program : program;
  }
}
