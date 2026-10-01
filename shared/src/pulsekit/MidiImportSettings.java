package pulsekit;

/**
 * Drum Midi Settings (File menu): how a MIDI drum track, such as DrumMidi output,
 * becomes a file set on import. Android and desktop store these as text from encode().
 */
public final class MidiImportSettings {
  private MidiImportSettings() {}

  public static final int MIN_MERGE_HITS = 1;
  public static final int MAX_MERGE_HITS = 8;

  /** Keep the notes as written: no style, swing, humanize, or generated fills. */
  public static boolean asWritten = true;
  /** Merge bars that differ by up to mergeHits hits into one. */
  public static boolean mergeBars = true;
  public static int mergeHits = 2;
  /** A bar that comes back is the same pattern or fill, not a new one. */
  public static boolean reuseBars = true;
  /** Treat a one-off bar after a repeated groove as a fill. */
  public static boolean oneOffFills = true;
  /** Silent bars stay in the song as rests. */
  public static boolean keepSilent = true;

  public static void reset() {
    asWritten = true;
    mergeBars = true;
    mergeHits = 2;
    reuseBars = true;
    oneOffFills = true;
    keepSilent = true;
  }

  /** Bars within this many hits merge on import; 0 when merging is off. */
  public static int mergeLimit() {
    return mergeBars ? mergeHits : 0;
  }

  public static String encode() {
    return "asWritten=" + asWritten + "\nmergeBars=" + mergeBars + "\nmergeHits=" + mergeHits
        + "\nreuseBars=" + reuseBars + "\noneOffFills=" + oneOffFills + "\nkeepSilent=" + keepSilent + "\n";
  }

  /** Reads text from encode(). Missing or unreadable lines keep their defaults. */
  public static void decode(String text) {
    reset();
    if (text == null) return;
    for (String line : text.split("\n")) {
      int eq = line.indexOf('=');
      if (eq <= 0) continue;
      String key = line.substring(0, eq).trim();
      String v = line.substring(eq + 1).trim();
      if ("asWritten".equals(key)) asWritten = Boolean.parseBoolean(v);
      else if ("mergeBars".equals(key)) mergeBars = Boolean.parseBoolean(v);
      else if ("reuseBars".equals(key)) reuseBars = Boolean.parseBoolean(v);
      else if ("oneOffFills".equals(key)) oneOffFills = Boolean.parseBoolean(v);
      else if ("keepSilent".equals(key)) keepSilent = Boolean.parseBoolean(v);
      else if ("mergeHits".equals(key)) {
        try {
          mergeHits = clampHits(Integer.parseInt(v));
        } catch (NumberFormatException ignored) {
          // keep the default
        }
      }
    }
  }

  public static int clampHits(int n) {
    return Math.max(MIN_MERGE_HITS, Math.min(MAX_MERGE_HITS, n));
  }
}
