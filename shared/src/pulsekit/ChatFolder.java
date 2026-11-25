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
    int max = max(argv);
    List<String> out = new ArrayList<String>(argv);
    for (int i = 0; i < all.size() && i < max; i++) {
      out.add("--dir_file");
      out.add(all.get(i).getAbsolutePath());
    }
    if (all.size() > max && notes != null) notes.add("Note: " + all.size() + " files in " + d.getName() + "; the first " + max + " by name are asked about (Max files)");
    return out;
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

  /** The log's line after the answers are saved. */
  public static String saved(int n, String where, String[] setting) {
    String how = TextReady.NO_CUT.equals(setting[0]) || setting[1].isEmpty() ? "whole" : setting[0].toLowerCase(Locale.ROOT) + " “" + setting[1] + "”";
    return "Answers saved (" + how + "): " + n + " file" + (n == 1 ? "" : "s") + " in " + where;
  }
}
