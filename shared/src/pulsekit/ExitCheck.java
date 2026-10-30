package pulsekit;

import java.util.List;

/**
 * File > Exit: what is not saved yet (the code editor, the playlist editor, the MIDI editor, the
 * prompt editor) is listed and Pulsekit asks before it closes. Songs, patterns and playlists' own
 * lists are kept as they change; these are the edits that wait for a Save.
 */
public final class ExitCheck {
  private ExitCheck() {}

  public static final String EXIT = "Exit";
  public static final String TITLE = "Exit Pulsekit";
  public static final String SAVE = "Save and exit";
  public static final String DISCARD = "Exit without saving";

  /** The question for the unsaved edits ("Code editor: SogniMusic.java"...). */
  public static String question(List<String> unsaved) {
    StringBuilder sb = new StringBuilder("Not saved yet:\n");
    for (String u : unsaved) sb.append("\u2022 ").append(u).append('\n');
    return sb.append("\nDo you want to save the changes?").toString();
  }
}
