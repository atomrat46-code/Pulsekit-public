package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * Add to favourites and Add to group for a file a run made (Picture ready, Video ready): the file
 * as the Media browser knows it (its name and size), and the same file in the prompt library
 * (Reference, Result or Prompt files of that name and size) when it is kept there too, so F and G
 * find it in the Media browser, Browse DB and the galleries alike.
 */
public final class MarkFile {
  private MarkFile() {}

  /** The library's copies of the file: the stored files of this name and size. */
  public static List<PromptVault.StoredFile> inLibrary(PromptVault vault, String name, long size) {
    List<PromptVault.StoredFile> out = new ArrayList<PromptVault.StoredFile>();
    if (vault == null || name == null) return out;
    List<PromptVault.StoredFile> all = new ArrayList<PromptVault.StoredFile>();
    all.addAll(vault.referenceFiles());
    all.addAll(vault.resultFiles());
    all.addAll(vault.promptFiles());
    for (PromptVault.StoredFile f : all) if (name.equals(f.name) && f.size == size) out.add(f);
    return out;
  }

  /** The Media browser's key of the file. */
  public static String mediaKey(String name, long size) {
    return FileGroups.fileKey(MediaTags.key(name, size));
  }

  public static boolean isFavourite(String name, long size) {
    return FileFavourites.isKey(mediaKey(name, size));
  }

  /** Added to the favourites (or taken out), its library copies with it; true when it is one now. */
  public static boolean toggleFavourite(PromptVault vault, String name, long size) {
    boolean now = FileFavourites.toggleKey(mediaKey(name, size));
    for (PromptVault.StoredFile f : inLibrary(vault, name, size)) if (FileFavourites.is(f) != now) FileFavourites.toggle(f);
    return now;
  }

  /** The keys Add to group puts in a group together: the file's, then its library copies'. */
  public static String[] groupKeys(PromptVault vault, String name, long size) {
    List<String> out = new ArrayList<String>();
    out.add(mediaKey(name, size));
    for (PromptVault.StoredFile f : inLibrary(vault, name, size)) out.add(FileGroups.key(f));
    return out.toArray(new String[0]);
  }
}
