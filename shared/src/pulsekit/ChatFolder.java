package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * SogniChat --input_dir: the folder's files asked about one after another (the same question for
 * each), at most --max_files of them (by name). Before the run the app lists them and gives each as
 * --dir_file (a big JPEG then gets its smaller copy, as a --file does); SogniChat lists the folder
 * itself only when run on its own. After the run each answer is cut at Document ready's delimiter
 * (TextReady, the one used last) and saved, into --output_dir when it is given; no Document ready.
 */
public final class ChatFolder {
  private ChatFolder() {}

  /** --max_files when not given. */
  public static final int MAX_DEFAULT = 20;

  /** The files SogniChat reads (text, MIDI, pictures, audio, video, prompt sheets). */
  public static final List<String> KINDS = Arrays.asList("txt", "md", "csv", "json", "log", "prompt", "mid", "midi", "png", "jpg", "jpeg", "webp", "gif",
      "mp3", "wav", "flac", "m4a", "mp4", "mov", "webm");

  public static boolean kind(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 && !name.startsWith(".") && KINDS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
  }

  /** The value after `flag` in argv, or null (none, empty, or another switch). */
  public static String arg(List<String> argv, String flag) {
    if (argv == null) return null;
    int at = argv.indexOf(flag);
    if (at < 0 || at + 1 >= argv.size()) return null;
    String v = argv.get(at + 1) == null ? "" : argv.get(at + 1).trim();
    return v.isEmpty() || v.startsWith("--") ? null : v;
  }

  /** --max_files, else MAX_DEFAULT (at least 1). */
  public static int max(List<String> argv) {
    try {
      String v = arg(argv, "--max_files");
      return v == null ? MAX_DEFAULT : Math.max(1, Integer.parseInt(v));
    } catch (NumberFormatException ex) {
      return MAX_DEFAULT;
    }
  }

  /** The folder's files SogniChat reads, by name (not its subfolders'); all of them, the cap left to the caller. */
  public static List<File> files(File dir) {
    List<File> out = new ArrayList<File>();
    File[] all = dir == null ? null : dir.listFiles();
    if (all == null) return out;
    for (File f : all) if (f.isFile() && kind(f.getName())) out.add(f);
    java.util.Collections.sort(out, (a, b) -> {
      int c = a.getName().compareToIgnoreCase(b.getName());
      return c != 0 ? c : a.getName().compareTo(b.getName());
    });
    return out;
  }

  /** A SogniChat run on a folder (--input_dir given). */
  public static boolean folderRun(String program, List<String> argv) {
    return PictureCopies.wants(program) && arg(argv, "--input_dir") != null;
  }

  /**
   * argv with the folder's files (at most --max_files) given as --dir_file each, after a note of how
   * many there are; argv itself without --input_dir or when the folder cannot be read. `notes` gets
   * what was left out.
   */
  public static List<String> expand(List<String> argv, List<String> notes) {
    String dir = arg(argv, "--input_dir");
    if (dir == null || argv.contains("--dir_file")) return argv;
    File d = new File(dir);
    if (!d.isDirectory()) return argv;
    List<File> all = files(d);
    // Skip already processed: the files answered in earlier runs on this folder left out (before Max files).
    int skipped = 0;
    if (argv.contains("--skip_done")) {
      java.util.Set<String> done = done(key(argv));
      List<File> fresh = new ArrayList<File>();
      for (File f : all) if (!done.contains(f.getName())) fresh.add(f);
      skipped = all.size() - fresh.size();
      all = fresh;
    }
    int max = max(argv);
    List<String> out = new ArrayList<String>(argv);
    // (Listed here: SogniChat does not list the folder again, also when nothing is left.)
    out.add("--listed");
    for (int i = 0; i < all.size() && i < max; i++) {
      out.add("--dir_file");
      out.add(all.get(i).getAbsolutePath());
    }
    if (skipped > 0 && notes != null) notes.add("Note: " + skipped + " file" + (skipped == 1 ? "" : "s") + " in " + d.getName() + " answered before, left out (Skip already processed)");
    if (all.size() > max && notes != null) notes.add("Note: " + all.size() + " files in " + d.getName() + "; the first " + max + " by name are asked about (Max files)");
    return out;
  }

  /** Where the list of answered files is kept (the app's own folder, set at start). */
  public static volatile File doneDir;
  static final String DONE_FILE = "chat-folder-done.txt";

  /** The input folder as given (a picked folder's address on the phone: --done_key, its copy's path not). */
  public static String key(List<String> argv) {
    String k = arg(argv, "--done_key");
    return k != null ? k : arg(argv, "--input_dir");
  }

  /** The names of the files of the folder `key` answered in earlier runs. */
  public static java.util.Set<String> done(String key) {
    java.util.Set<String> out = new java.util.HashSet<String>();
    File f = doneDir == null ? null : new File(doneDir, DONE_FILE);
    if (key == null || f == null || !f.isFile()) return out;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        int t = line.indexOf('\t');
        if (t > 0 && line.substring(0, t).equals(key)) out.add(line.substring(t + 1));
      }
    } catch (Exception ignored) {
      // none known
    }
    return out;
  }

  /** The files a folder run answered (its log's "=== File k of n: name ===" blocks with a "Succeeded:" line). */
  public static List<String> answered(String log) {
    List<String> out = new ArrayList<String>();
    String cur = null;
    boolean ok = false;
    for (String line : (log == null ? "" : log).split("\n")) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("^=== File \\d+ of \\d+: (.+) ===$").matcher(line.trim());
      if (m.matches() || line.trim().startsWith("Folder done:")) {
        if (cur != null && ok) out.add(cur);
        cur = m.matches() ? m.group(1) : null;
        ok = false;
      } else if (cur != null && line.trim().startsWith("Succeeded")) {
        ok = true;
      }
    }
    if (cur != null && ok) out.add(cur);
    return out;
  }

  /** The files answered added to the folder's list (kept also when Skip already processed is off). */
  public static void markDone(String key, List<String> names) {
    if (key == null || names.isEmpty() || doneDir == null) return;
    java.util.Set<String> have = done(key);
    StringBuilder sb = new StringBuilder();
    for (String n : names) if (have.add(n)) sb.append(key).append('\t').append(n).append('\n');
    if (sb.length() == 0) return;
    try {
      doneDir.mkdirs();
      java.nio.file.Files.write(new File(doneDir, DONE_FILE).toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
          java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    } catch (Exception ignored) {
      // only not kept
    }
  }

  /**
   * Whether a run's raw answer (answer.txt as SogniChat wrote it) is written to disk: not when an Output folder
   * is given (the processed copy goes there; the raw one is only read for it). No Output folder: into Download as before.
   */
  public static boolean rawKept(String program, List<String> argv) {
    if (!"SogniChat.java".equals(program) || argv == null) return true;
    return arg(argv, "--output_dir") == null;
  }

  /** The text files the run wrote (its answers), in order. */
  public static List<JavaRun.FileOut> answers(List<JavaRun.FileOut> files) {
    List<JavaRun.FileOut> out = new ArrayList<JavaRun.FileOut>();
    if (files != null) for (JavaRun.FileOut f : files) if (f != null && f.name != null && f.bytes != null && f.name.toLowerCase(Locale.ROOT).endsWith(".txt")) out.add(f);
    return out;
  }

  /** An answer's file name once cut: the same name in --output_dir, else <name>-trimmed.txt beside the chat. */
  public static String cutName(String name, boolean ownFolder) {
    return ownFolder ? name : TextReady.savedName(name);
  }

  /** As above, %g (Send to group's group; "" none) and the other choice codes filled in. */
  public static String cutName(String name, boolean ownFolder, String group) {
    return cutName(TextReady.outName(name, group), ownFolder);
  }

  /** The log's words for the answers sent to the preset group (Send to program and group), "" when none. */
  public static String sentNote(int n, String[] preset) {
    return preset == null || n == 0 ? "" : "; " + n + " sent to " + preset[0] + "'s group " + preset[1] + " as sections";
  }

  /** The log's line after the answers are saved. */
  public static String saved(int n, String where, String[] setting) {
    String how = TextReady.NO_CUT.equals(setting[0]) || setting[1].isEmpty() ? "whole" : setting[0].toLowerCase(Locale.ROOT) + " “" + setting[1] + "”";
    return "Answers saved (" + how + "): " + n + " file" + (n == 1 ? "" : "s") + " in " + where;
  }
}
