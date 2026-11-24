package pulsekit;

/** Params' rows of several folders, separated by ";" (the benches' input folders). */
public final class ParamsFolders {
  private ParamsFolders() {}

  /** `now`'s folders with `picked` added after them (not again when it is there). */
  public static String add(String now, String picked) {
    String t = now == null ? "" : now.trim();
    if (picked == null || picked.trim().isEmpty()) return t;
    if (t.isEmpty()) return picked.trim();
    for (String f : t.split("\\s*;\\s*")) if (f.equalsIgnoreCase(picked.trim())) return t;
    return t + "; " + picked.trim();
  }
}
