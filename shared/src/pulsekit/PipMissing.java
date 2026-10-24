package pulsekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Python run that stopped for a library that is not installed ("numpy is not installed ...
 * Install it with: pip install numpy", or Python's own "No module named 'numpy'"): the packages
 * to install. pip in a command window may install them for another Python than the one Pulsekit
 * runs, so Pulsekit offers to install them for its own (python -m pip install ...).
 */
public final class PipMissing {
  private PipMissing() {}

  private static final Pattern PIP = Pattern.compile("pip3? install ([A-Za-z0-9_.\\-\\[\\]]+(?: +[A-Za-z0-9_.\\-\\[\\]]+)*)");
  private static final Pattern NO_MODULE = Pattern.compile("No module named '?([A-Za-z0-9_]+)");

  /** Import names whose package is called something else. */
  private static final String[][] PACKAGES = {
    {"cv2", "opencv-python"}, {"PIL", "pillow"}, {"sklearn", "scikit-learn"}, {"yaml", "pyyaml"}, {"skimage", "scikit-image"},
  };

  /** The packages the run's log says are missing, in the order it names them; none when it names none. */
  public static List<String> missing(String log) {
    List<String> out = new ArrayList<String>();
    if (log == null) return out;
    for (String line : log.split("\n")) {
      String low = line.toLowerCase(Locale.ROOT);
      if (low.contains("not installed")) {
        Matcher m = PIP.matcher(line);
        if (m.find()) for (String p : m.group(1).trim().split(" +")) add(out, p);
      }
      Matcher m = NO_MODULE.matcher(line);
      if (m.find()) add(out, packageOf(m.group(1)));
    }
    return out;
  }

  private static void add(List<String> out, String p) {
    // A trailing full stop ("pip install numpy.") is the sentence's.
    String name = p.replaceAll("[.,;:]+$", "");
    if (name.length() == 0 || name.startsWith("-")) return;
    for (String have : out) if (have.equalsIgnoreCase(name)) return;
    out.add(name);
  }

  /** The package an import name comes in ("cv2": opencv-python). */
  public static String packageOf(String module) {
    for (String[] p : PACKAGES) if (p[0].equals(module)) return p[1];
    return module;
  }

  /** Install's question: which Python Pulsekit runs (where pip in a command window may not install). */
  public static String question(List<String> packages, String python) {
    String what = String.join(", ", packages);
    return what + (packages.size() == 1 ? " is" : " are") + " not installed for the Python Pulsekit runs:\n" + python
        + "\n\n(pip in a command window can install into another Python on this computer.)\n\nInstall " + what + " for this Python now ("
        + "python -m pip install " + String.join(" ", packages) + "), then run again?";
  }

  /** Whether pip's log says the install needs --user (no right to write into Python's own folder). */
  public static boolean needsUser(String log) {
    String low = log == null ? "" : log.toLowerCase(Locale.ROOT);
    return low.contains("permission denied") || low.contains("access is denied") || low.contains("--user") || low.contains("errno 13");
  }
}
