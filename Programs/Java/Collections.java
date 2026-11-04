/**
 * Collections: opens Pulsekit's Collections viewer (File > Collections), where each collection is a
 * tree of containers holding links to files, DB files (Ref and Result files), playlists and file
 * sets. The collections are kept in Pulsekit's encrypted prompt library, so this program only
 * says which one to open; Pulsekit shows it after the run.
 *
 *   java Collections [name]
 *
 * name   The collection to open (as File > Collections lists it). None: all of them.
 *
 * Add to Collection, on the Media browser's thumbnails, the Ref / Result / Prompt files, a playlist
 * (the Playlist window) and a file set, puts a link into a collection's container.
 *
 * Plain Java 8, so PyJav can compile it on a phone.
 */
public final class Collections {
  private Collections() {}

  public static void main(String[] args) {
    int code = run(args);
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  static void usage() {
    System.out.println("Usage: java Collections [name]");
  }

  static int run(String[] args) {
    StringBuilder name = new StringBuilder();
    for (String raw : args == null ? new String[0] : args) {
      String a = raw == null ? "" : raw.trim();
      if (a.equals("-h") || a.equals("--help")) {
        usage();
        return 0;
      }
      // A switch PyJav adds (an input file) or a placeholder is no name.
      if (a.length() == 0 || a.startsWith("--") || a.startsWith("<")) continue;
      if (name.length() > 0) name.append(' ');
      name.append(a);
    }
    System.out.println("Succeeded: Pulsekit opens the Collections viewer");
    System.out.println("Collections: " + name);
    return 0;
  }
}
