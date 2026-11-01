package pulsekit;

/**
 * A song's long-press Save song / Save song as: the song as files to keep and share, a Pulsekit
 * song (.sng, opened again with its parts) and a MIDI of it (.mid, for other apps and a DAW).
 */
public final class SongFiles {
  private SongFiles() {}

  public static final String SAVE = "Save song";
  public static final String SAVE_AS = "Save song as";

  /** A song's name as a file name ("Verse / Chorus" → "Verse - Chorus"). */
  public static String fileName(String name) {
    String n = name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|]+", " - ").replaceAll("\\s+", " ").trim();
    return n.length() == 0 ? "song" : n;
  }

  /** The song's files: {name.sng, its bytes}, {name.mid, its bytes}. */
  public static Object[][] files(Engine.ImportedSong song, String name) {
    String n = fileName(name == null ? song.name : name);
    return new Object[][] {
      {n + ".sng", Engine.encodeSng(song.parts, n)},
      {n + ".mid", Engine.encodeSongMidi(song.parts)},
    };
  }
}
