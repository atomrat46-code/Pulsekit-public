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
    return files(dir, false);
  }

  /** As above; `deep`: its subfolders' files too (by name). */
  public static List<File> files(File dir, boolean deep) {
    List<File> out = new ArrayList<File>();
    collect(dir, deep, out, 0);
    java.util.Collections.sort(out, (a, b) -> {
      int c = a.getName().compareToIgnoreCase(b.getName());
      return c != 0 ? c : a.getName().compareTo(b.getName());
    });
    return out;
  }

  static void collect(File dir, boolean deep, List<File> out, int depth) {
    File[] all = dir == null ? null : dir.listFiles();
    if (all == null) return;
    for (File f : all) {
      if (f.isFile() && kind(f.getName())) out.add(f);
      else if (deep && f.isDirectory() && !f.getName().startsWith(".") && depth < 8) collect(f, true, out, depth + 1);
    }
  }

  /** Silent auto mode: Document ready skipped (only with Auto render and a Send to program). */
  public static final String SILENT = "--silent_auto";

  /** Silent auto mode in effect: ticked, with Auto render and its Send to program (else not used). */
  public static boolean silent(String program, List<String> argv) {
    return "SogniChat.java".equals(program) && argv != null && argv.contains(SILENT) && AutoRender.target(argv) != null;
  }

  /** A run whose answers are saved, sent and rendered by themselves (no Document ready): an input folder's, or silent. */
  public static boolean autoSaved(String program, List<String> argv) {
    return folderRun(program, argv) || silent(program, argv);
  }

  /** Auto render ticked with no Send to program: asked before the run. */
  public static boolean autoWithoutProgram(String program, List<String> argv) {
    return "SogniChat.java".equals(program) && argv != null && argv.contains("--auto_render") && arg(argv, "--send_program") == null;
  }

  public static final String AUTO_NO_PROGRAM = "Auto render is ticked, but Send to program is empty: there is no program to render the answer with.";
  public static final String RUN_WITHOUT_AUTO = "Run without Auto render", CHOOSE_PROGRAM = "Choose the program", RUN_ANYWAY = "Run anyway";
  /** The programs Auto render can render with (Send to program's list). */
  public static final String[] RENDER_PROGRAMS = {"SogniPadd", "SogniPedit", "SogniVideo", "SogniTextVideo", "SogniMusic"};

  /** Auto render and Silent auto mode taken off a run's arguments (Run without Auto render). */
  public static void withoutAuto(List<String> argv) {
    while (argv.remove("--auto_render")) {}
    while (argv.remove(SILENT)) {}
  }

  /** Silent auto mode's fields left empty (Output name, Output folder, Send to group): asked about before the run. */
  public static List<String> silentEmpty(String program, List<ProgramParams.Param> ps, List<String> argv) {
    List<String> out = new ArrayList<String>();
    if (!silent(program, argv)) return out;
    if (outputName(ps, argv) == null) out.add("Output name");
    if (arg(argv, "--output_dir") == null) out.add("Output folder");
    if (arg(argv, "--send_group") == null) out.add("Send to group");
    return out;
  }

  /** The question when Silent auto mode would run with fields empty. */
  public static String silentEmptyAsk(List<String> empty) {
    StringBuilder sb = new StringBuilder("Silent auto mode would run with these left empty:");
    if (empty.contains("Output name")) sb.append("\n\u2022 Output name: the answer is named after the chat");
    if (empty.contains("Output folder")) sb.append("\n\u2022 Output folder: the answer is saved in Download");
    if (empty.contains("Send to group")) sb.append("\n\u2022 Send to group: the answer is not kept as a section");
    return sb.append("\n\nRun it like this?").toString();
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
    // Include subfolders: their files too (on the phone a picked folder's are copied side by side already).
    List<File> all = files(d, argv.contains("--subfolders"));
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

  /**
   * An input folder run with no file left to give (all processed before with Skip already processed, or none
   * the program takes): what to say instead of running; null when there are files.
   */
  public static String nothingLeft(List<String> argv, List<String> expanded) {
    String dir = arg(argv, "--input_dir");
    if (dir == null || expanded == null || argv.contains("--dir_file") || expanded.contains("--dir_file")) return null;
    File d = new File(dir);
    if (!d.isDirectory()) return null;
    String name = MediaDir.label(key(argv) == null ? dir : key(argv));
    boolean skip = argv.contains("--skip_done");
    return "Nothing left to process in " + name + (skip ? ": every file there was processed before (Skip already processed is on: untick it to go over them again)"
        : ": it has no files SogniChat takes (pictures, text, PDF...)");
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
   * Whether a run's raw answer (the chat as SogniChat wrote it) is written to disk: not when Output name is
   * given (the processed copy is saved under that name: into the Output folder, else Download; the raw one is
   * only read for it). `programText` the program's source (its Params: which argument is Output name).
   */
  public static boolean rawKept(String program, String programText, List<String> argv) {
    if (!"SogniChat.java".equals(program) || argv == null) return true;
    return outputName(programText == null ? java.util.Collections.<ProgramParams.Param>emptyList() : ProgramParams.parse(programText), argv) == null;
  }

  /** The run's Output name (its first argument that is not a switch or a switch's value), or null. */
  public static String outputName(List<ProgramParams.Param> ps, List<String> argv) {
    for (int i = 0; argv != null && i < argv.size(); i++) {
      String w = argv.get(i) == null ? "" : argv.get(i).trim();
      if (w.startsWith("-")) {
        ProgramParams.Param p = null;
        for (ProgramParams.Param q : ps) if (q.flag && q.token.equals(w)) p = q;
        if ((p == null || p.takesValue) && i + 1 < argv.size() && argv.get(i + 1) != null && !argv.get(i + 1).startsWith("-")) i++;
        continue;
      }
      if (!w.isEmpty()) return w;
    }
    return null;
  }

  /** Where the processed answer goes: --output_dir, else "" (Download) when the raw one is not kept, else null (not saved by itself). */
  public static String answerDir(List<String> argv, boolean rawKept) {
    String given = arg(argv, "--output_dir");
    return given != null ? given : rawKept ? null : "";
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

  /** The log's words for answers left out because nothing was left of them once processed ("" when none). */
  public static String emptyNote(int n) {
    return n == 0 ? "" : "; " + n + " empty after processing, left out (not saved, sent or rendered)";
  }

  /** The log's line after the answers are saved. */
  public static String saved(int n, String where, String[] setting) {
    String how = TextReady.NO_CUT.equals(setting[0]) || setting[1].isEmpty() ? "whole" : setting[0].toLowerCase(Locale.ROOT) + " “" + setting[1] + "”";
    return "Answers saved (" + how + "): " + n + " file" + (n == 1 ? "" : "s") + " in " + where;
  }
}
