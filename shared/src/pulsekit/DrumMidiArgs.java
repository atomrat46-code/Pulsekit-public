package pulsekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DrumMidi switches for the Params screen (Android and desktop): what each one is, its
 * default and suggested value, and how a saved set merges into a program's arguments.
 */
public final class DrumMidiArgs {
  private DrumMidiArgs() {}

  public static final String[] FLAGS = {
    "--sens", "--hat", "--tom", "--ride", "--crash", "--bpm", "--quantize", "--no-hpss", "--no-cymbals"
  };
  public static final String[] LABELS = {
    "Sensitivity", "Hi-hat", "Toms", "Ride", "Crash", "BPM", "Quantize", "No HPSS", "Kick, snare and toms only"
  };
  /** The program's own values, shown as hints in empty fields. */
  public static final String[] HINTS = {
    "1.0", "1.0", "1.0", "1.0", "1.0", "auto", "off", "1 to turn off", "1 for no hats, rides or crashes"
  };
  /**
   * Suggested values, tested on a drum and bass guitar mix (Passing Ships): lower sensitivity
   * keeps bass notes and cymbal wash out. Empty means the program's own default.
   */
  public static final String[] SUGGESTED = {
    "0.4", "0.4", "0.4", "0.4", "0.4", "", "", "", ""
  };
  public static final String NOTE = "Defaults: the program's own values (empty fields). Suggested: tested on a drum and bass guitar mix.";

  /** The switches from field values, in FLAGS order. Empty fields are left out. */
  public static String build(String[] values) {
    StringBuilder built = new StringBuilder();
    for (int i = 0; i < FLAGS.length && i < values.length; i++) {
      String value = values[i] == null ? "" : values[i].trim();
      if (value.length() == 0) continue;
      if (onOff(FLAGS[i])) {
        if (on(value)) {
          if (built.length() > 0) built.append(' ');
          built.append(FLAGS[i]);
        }
        continue;
      }
      if (built.length() > 0) built.append(' ');
      built.append(FLAGS[i]).append(' ').append(value);
    }
    return built.toString();
  }

  /** File paths stay. A saved string replaces DrumMidi flags. Null means nothing was saved. */
  public static String merge(String extra, String saved) {
    if (saved == null) return extra == null ? "" : extra.trim();
    String kept = strip(extra);
    if (saved.trim().length() == 0) return kept;
    if (kept.length() == 0) return saved.trim();
    return kept + " " + saved.trim();
  }

  /** The arguments without DrumMidi switches. */
  public static String strip(String extra) {
    if (extra == null || extra.trim().length() == 0) return "";
    String[] parts = extra.trim().split("\\s+");
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      int flag = index(parts[i]);
      if (flag >= 0) {
        if (!onOff(FLAGS[flag]) && i + 1 < parts.length && !parts[i + 1].startsWith("-")) i++;
        continue;
      }
      if (out.length() > 0) out.append(' ');
      out.append(parts[i]);
    }
    return out.toString();
  }

  /** Switch → value from arguments ("1" for an on/off switch such as --no-hpss). */
  public static Map<String, String> read(String extra) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    if (extra == null || extra.trim().length() == 0) return out;
    String[] parts = extra.trim().split("\\s+");
    for (int i = 0; i < parts.length; i++) {
      int flag = index(parts[i]);
      if (flag < 0) continue;
      if (onOff(FLAGS[flag])) {
        out.put(FLAGS[flag], "1");
        continue;
      }
      if (i + 1 < parts.length && !parts[i + 1].startsWith("-")) out.put(FLAGS[flag], parts[++i]);
    }
    return out;
  }

  /** A switch with no value: --no-hpss, --no-cymbals. */
  static boolean onOff(String flag) {
    return "--no-hpss".equals(flag) || "--no-cymbals".equals(flag);
  }

  public static int index(String token) {
    for (int i = 0; i < FLAGS.length; i++) if (FLAGS[i].equals(token)) return i;
    return -1;
  }

  public static boolean on(String value) {
    return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value);
  }
}
