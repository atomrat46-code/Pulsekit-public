package pulsekit;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extract prompt, in the menu of a text file in the prompt library that has one (a SogniChat answer.txt): the
 * text after the last "**Prompt:**" in it, kept as a new file of the same kind (Answer Prompt 1.txt).
 * The text runs to the end of that part: a "---" line, the next "**Heading:**" or the chat's next
 * turn ("=== You ==="). Quote marks ("> ") at the start of its lines are dropped.
 */
public final class PromptExtract {
  private static final Pattern MARK = Pattern.compile("\\*\\*\\s*Prompt\\s*:?\\s*\\*\\*\\s*:?", Pattern.CASE_INSENSITIVE);
  private static final Pattern HEADING = Pattern.compile("^\\*\\*[^*]+:\\s*\\*\\*.*|^\\*\\*[^*]+\\*\\*\\s*:.*");

  private PromptExtract() {}

  /** Whether the menu offers Extract prompt for this stored file: a text file with a prompt after a **Prompt:**. */
  public static boolean offered(PromptVault vault, long versionId, int which, String name) {
    if (!DbFilter.isText(name)) return false;
    try {
      byte[] bytes = vault.fileBytes(versionId, which);
      return bytes != null && extract(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)) != null;
    } catch (Exception ex) {
      return false;
    }
  }

  /** The prompt text after the last "**Prompt:**", or null when the text has none (or nothing after it). */
  public static String extract(String text) {
    if (text == null) return null;
    Matcher m = MARK.matcher(text);
    int at = -1;
    while (m.find()) at = m.end();
    if (at < 0) return null;
    String[] lines = text.substring(at).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String t = line.trim();
      if (i > 0 && (t.matches("-{3,}|\\*{3,}|_{3,}") || t.startsWith("=== ") || t.startsWith("#") || HEADING.matcher(t).matches())) break;
      while (t.startsWith(">")) t = t.substring(1).trim();
      sb.append(i > 0 ? "\n" : "").append(t);
    }
    String out = sb.toString().trim();
    return out.length() == 0 ? null : out;
  }

  /** "<File> Prompt <n>.txt" for answer.txt (Answer Prompt 1.txt), n the first not among `taken` (any case). */
  public static String name(String fileName, Collection<String> taken) {
    String stem = PromptVault.fileTitle(fileName).replaceAll("\\.[A-Za-z0-9]{1,5}$", "").trim();
    if (stem.length() == 0) stem = "Answer";
    stem = Character.toUpperCase(stem.charAt(0)) + stem.substring(1);
    for (int n = 1; ; n++) {
      String name = stem + " Prompt " + n + ".txt";
      boolean used = false;
      if (taken != null) for (String t : taken) if (t != null && t.equalsIgnoreCase(name)) used = true;
      if (!used) return name;
    }
  }

  /**
   * Extracts the prompt from the stored file and keeps it in `vault` as a new file of the same kind
   * (reference or result); returns its name. Throws with a message to show when the file has no
   * "**Prompt:**" or cannot be read.
   */
  public static String keep(PromptVault vault, long versionId, int which, String fileName, String promptTitle) throws Exception {
    byte[] bytes = vault.fileBytes(versionId, which);
    if (bytes == null) throw new IllegalStateException("Could not read " + fileName);
    String prompt = extract(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    if (prompt == null) throw new IllegalStateException("No **Prompt:** in " + fileName);
    java.util.List<String> taken = new java.util.ArrayList<String>();
    for (PromptVault.StoredFile f : which == 3 ? vault.resultFiles() : vault.referenceFiles()) taken.add(f.name);
    String name = name(fileName, taken);
    vault.addLibraryFile(name, (prompt + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8), promptTitle == null ? "" : promptTitle, which == 3 ? 3 : 1);
    return name;
  }
}
