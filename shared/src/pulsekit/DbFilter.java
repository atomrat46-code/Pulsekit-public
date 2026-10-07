package pulsekit;

/**
 * Browse DB's A / I / V / S / T buttons: all files, images, videos, sound files (MIDI too) or text
 * files, by the name's ending. The one picked last is kept while the program runs, so the window
 * opens on it again (and switching Ref files / Result files keeps it).
 */
public final class DbFilter {
  public static final String[] LETTERS = {"A", "I", "V", "S", "T"};
  public static final String[] NAMES = {"All files", "Images", "Videos", "Sound files", "Text files"};

  /** The letter picked last ("A" at first). */
  public static volatile String current = "A";

  private DbFilter() {}

  /** Whether a file of this name is shown under `letter` (A, or an unknown letter, shows all). */
  public static boolean fits(String name, String letter) {
    if (letter == null || letter.equals("A")) return true;
    int kind = MediaDir.kind(name);
    if (letter.equals("I")) return kind == MediaDir.PICTURE;
    if (letter.equals("V")) return kind == MediaDir.VIDEO;
    if (letter.equals("S")) return kind == MediaDir.SOUND;
    if (letter.equals("T")) return isText(name);
    return true;
  }

  public static boolean isText(String name) {
    String low = name == null ? "" : name.toLowerCase();
    return low.matches(".+\\.(txt|text|md|json|csv|tsv|srt|vtt|lrc|xml|html?|log|prompt|py|java|js|ts|sh|ya?ml|ini|cfg|conf|rtf)");
  }

  /** What the letter shows ("All files" for A), for the button's description. */
  public static String label(String letter) {
    for (int i = 0; i < LETTERS.length; i++) if (LETTERS[i].equals(letter)) return NAMES[i];
    return letter;
  }
}
