package pulsekit;

/**
 * The Prompts page's two tabs: Result & Reference entries (Ref files, Result files) and Prompts &
 * Categories (Prompt files, Import, the favourite prompts, the categories' tabs, the prompts and New
 * prompt). The tab shown last is shown again.
 */
public final class PromptsTabs {
  private PromptsTabs() {}

  public static final String ENTRIES = "Result & Reference entries";
  public static final String PROMPTS = "Prompts & Categories";
  public static final String[] TABS = {ENTRIES, PROMPTS};

  /** The tab shown (while Pulsekit runs). */
  public static String tab = PROMPTS;

  public static boolean entries() {
    return ENTRIES.equals(tab);
  }
}
