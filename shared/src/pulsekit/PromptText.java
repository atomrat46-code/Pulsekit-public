package pulsekit;

/**
 * A prompt's text and the settings a Sogni program keeps under it ("Duration: 10 s. Resolution:
 * 768p. Shape: 4:7. First frame: x.png. LoRAs: ..."): stored together, as the runs and the sheets
 * have them (the last paragraph), shown apart in the prompt editors (Prompt, Settings).
 */
public final class PromptText {
  private PromptText() {}

  /** What a settings paragraph starts with. */
  static final String KEYS = "Tempo|Duration|Resolution|Shape|Sound|LoRAs|Picture|First frame|Last frame|Second reference|Time signature|Key";

  /** {prompt, settings}: the settings "" when the text has none. Joined again with join, it is the same text. */
  public static String[] split(String body) {
    String text = body == null ? "" : body;
    int cut = text.lastIndexOf("\n\n");
    if (cut >= 0) {
      String last = text.substring(cut + 2);
      if (last.trim().matches("(?s)(" + KEYS + "): .*") && text.substring(0, cut).trim().length() > 0) return new String[] {text.substring(0, cut), last};
    }
    return new String[] {text, ""};
  }

  /** The prompt, and the settings (when any) under it as its last paragraph. */
  public static String join(String prompt, String settings) {
    String p = prompt == null ? "" : prompt;
    String s = settings == null ? "" : settings;
    if (s.trim().length() == 0) return p;
    return p + "\n\n" + s;
  }
}
