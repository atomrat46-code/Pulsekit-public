package pulsekit;

/**
 * Browse DB's A / I / V / S / T buttons: all files, images, videos, sound files (MIDI too) or text
 * files, by the name's ending. The one picked last is kept while the program runs, so the window
 * opens on it again (and switching Ref files / Result files keeps it).
 */
public final class DbFilter {
  public static final String[] LETTERS = {"A", "I", "V", "S", "T"};
  public static final String[] NAMES = {"All files", "Images", "Videos", "Sound files", "Text files"};

  /** The thumbnail screens' letters: A I V S T, then F (favourites) and G (a group). */
  public static final String[] WITH_FAV_GROUP = {"A", "I", "V", "S", "T", FileFavourites.LETTER, FileGroups.LETTER};

  /** Whether a library file shows under `letter`: F its favourites, G the group G shows, else by its name. */
  public static boolean fits(PromptVault.StoredFile f, String letter) {
    if (FileFavourites.LETTER.equals(letter)) return FileFavourites.is(f);
    if (FileGroups.LETTER.equals(letter)) return FileGroups.fits(f);
    return fits(f.name, letter);
  }

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

  /** After an import: the gallery opens on its letter, S for a sound or MIDI file, V a video, I a picture, T a text; null to leave it. */
  public static String afterImport(String name) {
    int kind = MediaDir.kind(name);
    if (kind == MediaDir.SOUND) return "S";
    if (kind == MediaDir.VIDEO) return "V";
    if (kind == MediaDir.PICTURE) return "I";
    return isText(name) ? "T" : null;
  }

  /** A file of this size goes into separate storage (PromptVault.LINK_MIN or more): what the import asks. */
  public static String linkQuestion(String name, long size) {
    long mb = Math.max(1, size / (1024 * 1024));
    return name + " is " + (size > 0 ? mb + " MB" : "20 MB or more") + ". A file of " + (PromptVault.LINK_MIN / (1024 * 1024))
        + " MB or more goes into separate storage: it is kept as it is (not encrypted) beside the library and linked to it, so it is never read whole into memory."
        + " Its thumbnail, information and menus work as for any other file (In separate storage under it).\n\nContinue the import?";
  }

  /** What the letter shows ("All files" for A), for the button's description. */
  public static String label(String letter) {
    if (FileFavourites.LETTER.equals(letter)) return "Favourites";
    if (FileGroups.LETTER.equals(letter)) return "A group";
    for (int i = 0; i < LETTERS.length; i++) if (LETTERS[i].equals(letter)) return NAMES[i];
    return letter;
  }
}
