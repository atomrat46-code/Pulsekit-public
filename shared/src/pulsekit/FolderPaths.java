package pulsekit;

/**
 * A folder picked with Android's folder picker as a path Pulsekit reads its files from: the
 * picker's document id "primary:Music/sfz" is /storage/emulated/0/Music/sfz, a memory card's
 * "1A2B-3C4D:kits" is /storage/1A2B-3C4D/kits.
 */
public final class FolderPaths {
  private FolderPaths() {}

  public static final String ADD = "Add folder…";
  public static final String CHANGE = "Change…";
  public static final String DELETE = "Delete";
  public static final String BROWSE = "Browse…";
  public static final String NONE = "(no folders)";

  /** The path of a picked folder's document id, or null when it is not on the phone's storage. */
  public static String fromDocId(String docId) {
    if (docId == null) return null;
    int colon = docId.indexOf(':');
    if (colon <= 0) return docId.startsWith("/") ? docId : null;
    String volume = docId.substring(0, colon), rest = docId.substring(colon + 1);
    // "raw:/storage/..." (the Downloads provider) is the path itself.
    if (volume.equals("raw")) return rest;
    String root = volume.equalsIgnoreCase("primary") ? "/storage/emulated/0" : volume.equalsIgnoreCase("home") ? "/storage/emulated/0/Documents" : "/storage/" + volume;
    while (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
    return rest.isEmpty() ? root : root + "/" + rest;
  }
}
