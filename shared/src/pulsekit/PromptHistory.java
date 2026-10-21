package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Params' prompt field: the six prompts used last with each program (prompt-history.txt), newest
 * first, picked again from Recent prompts. A prompt used again moves to the top.
 */
public final class PromptHistory {
  private PromptHistory() {}

  static final String FILE = "prompt-history.txt";
  public static final int KEEP = 6;
  public static final String RECENT = "Recent prompts";

  private static List<String[]> read(File dir) {
    List<String[]> out = new ArrayList<String[]>();
    File f = new File(dir, FILE);
    if (!f.isFile()) return out;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int tab = line.indexOf('\t');
        if (tab > 0) out.add(new String[] {line.substring(0, tab), line.substring(tab + 1).replace("\\n", "\n").replace("\\\\", "\\")});
      }
    } catch (Exception ignored) {
      // none kept
    }
    return out;
  }

  /** The program's prompts used last, newest first (at most six). */
  public static synchronized List<String> recent(File dir, String program) {
    List<String> out = new ArrayList<String>();
    for (String[] e : read(dir)) if (e[0].equals(program)) out.add(e[1]);
    return out;
  }

  /** A prompt the program was run with (Params' OK): first in its list, the oldest past six dropped. */
  public static synchronized void add(File dir, String program, String prompt) {
    if (dir == null || program == null || prompt == null || prompt.trim().length() == 0) return;
    String p = prompt.trim();
    List<String[]> all = read(dir);
    List<String[]> out = new ArrayList<String[]>();
    out.add(new String[] {program, p});
    int mine = 1;
    for (String[] e : all) {
      if (e[0].equals(program)) {
        if (e[1].equals(p) || mine >= KEEP) continue;
        mine++;
      }
      out.add(e);
    }
    StringBuilder sb = new StringBuilder();
    for (String[] e : out) sb.append(e[0].replace("\t", " ")).append('\t').append(e[1].replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n")).append('\n');
    try {
      Files.write(new File(dir, FILE).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session only
    }
  }

  /** A list row: the prompt's start on one line ("A walk on the beach at dusk, …"). */
  public static String label(String prompt) {
    String one = prompt.replace('\n', ' ').trim();
    return one.length() > 90 ? one.substring(0, 90) + "\u2026" : one;
  }
}
