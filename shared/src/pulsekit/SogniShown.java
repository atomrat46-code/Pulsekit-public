package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * General settings' Sogni boxes beyond PyJav's list: with "Show Sogni tools" unticked, what the
 * Sogni programs made is left out too (their prompts, the files they saved into the library, their
 * Downloads, playlist items); with "Show only Sogni music tools and chat", only SogniMusic's and
 * SogniChat's are shown. Which program made a prompt version is kept when its run stores it
 * (prompt-programs.txt beside the library); a file is known by its name too ("sogni-video-...").
 */
public final class SogniShown {
  private SogniShown() {}

  static final String FILE = "prompt-programs.txt";
  private static final Map<Long, String> PROGRAMS = new HashMap<Long, String>();
  private static File at;

  /** The programs kept in `dir` (read again only when it is another folder). */
  public static synchronized void load(File dir) {
    File f = dir == null ? null : new File(dir, FILE);
    if (f != null && f.equals(at)) return;
    at = f;
    PROGRAMS.clear();
    if (f == null || !f.isFile()) return;
    try {
      for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        int tab = line.indexOf('\t');
        if (tab <= 0) continue;
        try {
          PROGRAMS.put(Long.parseLong(line.substring(0, tab).trim()), line.substring(tab + 1).trim());
        } catch (NumberFormatException ignored) {
          // a damaged line
        }
      }
    } catch (Exception ignored) {
      // none known
    }
  }

  /** Everything as it is at first (the tests start again). */
  public static synchronized void reset() {
    PROGRAMS.clear();
    at = null;
  }

  /** A run of `program` stored its prompt as `versionId`. */
  public static synchronized void record(long versionId, String program) {
    if (versionId == 0 || program == null || program.trim().length() == 0) return;
    PROGRAMS.put(versionId, program.trim());
    if (at == null) return;
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<Long, String> e : PROGRAMS.entrySet()) sb.append(e.getKey()).append('\t').append(e.getValue().replace('\n', ' ')).append('\n');
    try {
      if (at.getParentFile() != null) at.getParentFile().mkdirs();
      Files.write(at.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // known while the program runs
    }
  }

  /** The program that stored the version ("SogniVideo.java"), or "" when no run did (typed in, imported). */
  public static synchronized String programOf(long versionId) {
    String p = PROGRAMS.get(versionId);
    if (p != null) return p;
    String run = SogniDownloads.programOfVersion(versionId);
    return run == null ? "" : run;
  }

  /** Whether the Sogni settings show what this program made (any program not Sogni's is shown). */
  public static boolean programShown(String program) {
    return program == null || program.length() == 0 || AppSettings.listsProgram(program);
  }

  /** The Sogni program a file's name says made it ("sogni-video-beach.mp4": SogniVideo), "Sogni" for another sogni- name, or "". */
  public static String programOfName(String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
    if (slash >= 0) n = n.substring(slash + 1);
    if (!n.startsWith("sogni")) return "";
    if (n.startsWith("sogni-video-")) return "SogniVideo";
    if (n.startsWith("sogni-textvideo-")) return "SogniTextVideo";
    if (n.startsWith("sogni-pedit-")) return "SogniPedit";
    if (n.startsWith("sogni-padd-")) return "SogniPadd";
    if (n.startsWith("sogni-chat")) return "SogniChat";
    // SogniMusic names its tracks after the genre ("sogni-Deep-House-1a2b.mp3").
    if (MediaDir.kind(n) == MediaDir.SOUND || n.endsWith(".mid") || n.endsWith(".midi")) return "SogniMusic";
    return "Sogni";
  }

  /** Whether a file of this name is shown by the Sogni settings. */
  public static boolean nameShown(String name) {
    String p = programOfName(name);
    if (p.length() == 0) return true;
    if (!AppSettings.showSogni()) return false;
    if (!AppSettings.musicOnly()) return true;
    // Music tools and chat only: their files, and any sound file.
    return p.equals("SogniMusic") || p.equals("SogniChat") || MediaDir.kind(name) == MediaDir.SOUND;
  }

  /** Whether a library file is shown: by its name, and a result or prompt file by the program that stored its prompt. */
  public static boolean fileShown(PromptVault.StoredFile f) {
    if (f == null) return true;
    if (!nameShown(f.name)) return false;
    // A reference file is the user's own (a photo): it stays, as other prompts may use it.
    if (f.which == 1 || f.which == 2) return true;
    return programShown(programOf(f.versionId));
  }

  /** Whether a prompt is shown: its newest version's program (one typed in or imported always is). */
  public static boolean promptShown(PromptVault vault, PromptVault.Prompt p) {
    if (vault == null || p == null || (AppSettings.showSogni() && !AppSettings.musicOnly())) return true;
    List<PromptVault.Version> versions = vault.versions(p.id);
    long newest = 0;
    long when = Long.MIN_VALUE;
    for (PromptVault.Version v : versions) {
      if (v.created >= when) {
        when = v.created;
        newest = v.id;
      }
    }
    return newest == 0 || programShown(programOf(newest));
  }

  /** The prompts a list shows: those the Sogni settings leave out are not in it. */
  public static List<PromptVault.Prompt> prompts(PromptVault vault, List<PromptVault.Prompt> all) {
    if (all == null || (AppSettings.showSogni() && !AppSettings.musicOnly())) return all;
    List<PromptVault.Prompt> out = new ArrayList<PromptVault.Prompt>();
    for (PromptVault.Prompt p : all) if (promptShown(vault, p)) out.add(p);
    return out;
  }
}
