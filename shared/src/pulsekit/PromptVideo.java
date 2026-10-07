package pulsekit;

/**
 * The Prompts page's video player as it was last left: Loop video, the volume and the zoom, so a
 * video opened full size (a preview, on the desktop) starts as the last one was left. Kept apart
 * from the Media browser's own (MediaDir): the phone keeps it in its preferences, the desktop in
 * ~/.pulsekit/prompt-video.txt.
 */
public final class PromptVideo {
  public static volatile boolean loop;
  /** 0..100. */
  public static volatile int volume = 80;
  /** The zoom the player last had (the phone's preview 0.5..4, the desktop's 1..4). */
  public static volatile double zoom = 1;

  private PromptVideo() {}

  public static String encode() {
    return "loop=" + (loop ? 1 : 0) + "\nvolume=" + volume + "\nzoom=" + zoom + "\n";
  }

  /** Reads encode()'s text; a missing or unreadable line keeps its default (Loop off, 80%, 100%). */
  public static void decode(String text) {
    loop = false;
    volume = 80;
    zoom = 1;
    if (text == null) return;
    for (String line : text.split("\n")) {
      int eq = line.indexOf('=');
      if (eq < 0) continue;
      String k = line.substring(0, eq).trim();
      String v = line.substring(eq + 1).trim();
      try {
        if (k.equals("loop")) loop = v.equals("1");
        else if (k.equals("volume")) volume = Math.max(0, Math.min(100, Integer.parseInt(v)));
        else if (k.equals("zoom")) zoom = Math.max(0.5, Math.min(4, Double.parseDouble(v)));
      } catch (NumberFormatException ignored) {
        // that one keeps its default
      }
    }
  }
}
