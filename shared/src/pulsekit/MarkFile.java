package pulsekit;

/**
 * Add to favourites and Add to group for a file a run made (Picture ready, Video ready): the file
 * is marked as the Media browser knows it (its name and size). A file in the prompt library of that
 * name and size is one of them too (FileFavourites.is, FileGroups.fits), so F and G find it in
 * Browse DB and the galleries as well, even when the library keeps it after it was marked.
 */
public final class MarkFile {
  private MarkFile() {}

  /** The Media browser's key of the file. */
  public static String mediaKey(String name, long size) {
    return FileGroups.fileKey(MediaTags.key(name, size));
  }

  public static boolean isFavourite(String name, long size) {
    return FileFavourites.isKey(mediaKey(name, size));
  }

  /** Added to the favourites (or taken out); true when it is one now. Its copies in the library follow (they are known by the same name and size). */
  public static boolean toggleFavourite(PromptVault vault, String name, long size) {
    return FileFavourites.toggleKey(mediaKey(name, size));
  }

  /** The key Add to group puts in a group: the file's (its copies in the library are known by it too). */
  public static String[] groupKeys(PromptVault vault, String name, long size) {
    return new String[] {mediaKey(name, size)};
  }
}
