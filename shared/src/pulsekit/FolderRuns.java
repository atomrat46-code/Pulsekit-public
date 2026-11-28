package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SogniPedit's and SogniPadd's Input folder: the program run once for each of the folder's files (by
 * name, at most Max files), one after another as a batch (Stop batch ends it): SogniPedit edits each
 * picture (--image), SogniPadd renders each .txt or .prompt file's prompt (a definitions file's
 * LoRAs and other rows too). Its Params are the run's for every file; Section (--section, a saved
 * section's name) puts that section's entries in them first. Skip already processed leaves out the
 * files done in earlier runs on the folder (as SogniChat's).
 */
public final class FolderRuns {
  private FolderRuns() {}

  /** The Params rows of a folder run: not given to the program's runs. */
  public static final String[] OWN = {"--input_dir", "--max_files", "--skip_done", "--section", "--input_as", "--subfolders"};

  public static boolean wanted(String program) {
    return pictures(program) || "SogniPadd.java".equals(program) || "SogniTextVideo.java".equals(program);
  }

  /** The programs whose input folder is pictures (SogniPedit edits them, SogniVideo makes videos from them); the others' is text files. */
  public static boolean pictures(String program) {
    return "SogniPedit.java".equals(program) || "SogniVideo.java".equals(program);
  }

  /** Where a folder's picture goes: --image, or with Input as 2 SogniPedit's --image2 (reference 2), SogniVideo's --end_image. */
  public static String pictureRow(String program, String inputAs) {
    if (!"2".equals(inputAs == null ? "" : inputAs.trim())) return "--image";
    return "SogniVideo.java".equals(program) ? "--end_image" : "--image2";
  }

  /** A run of the program on a folder (Input folder given). */
  public static boolean folderRun(String program, List<String> argv) {
    return wanted(program) && ChatFolder.arg(argv, "--input_dir") != null;
  }

  /** The files the program takes: SogniPedit pictures, SogniPadd text and prompt files. */
  public static boolean kind(String program, String name) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (n.startsWith(".")) return false;
    if (pictures(program)) return n.matches(".+\\.(png|jpe?g|webp)$");
    return n.endsWith(".txt") || n.endsWith(".prompt");
  }

  /** The folder's files the program takes, by name. */
  public static List<File> files(String program, File dir) {
    return files(program, dir, false);
  }

  /** As above; `deep` (Include subfolders): its subfolders' files too, by their path in the folder. */
  public static List<File> files(String program, File dir, boolean deep) {
    final List<File> out = new ArrayList<File>();
    collect(program, dir, deep, out, 0);
    final File root = dir;
    java.util.Collections.sort(out, (a, b) -> {
      String x = rel(root, a), y = rel(root, b);
      int c = x.compareToIgnoreCase(y);
      return c != 0 ? c : x.compareTo(y);
    });
    return out;
  }

  static void collect(String program, File dir, boolean deep, List<File> out, int depth) {
    File[] all = dir == null ? null : dir.listFiles();
    if (all == null) return;
    for (File f : all) {
      if (f.isFile() && kind(program, f.getName())) out.add(f);
      else if (deep && f.isDirectory() && !f.getName().startsWith(".") && depth < 8) collect(program, f, true, out, depth + 1);
    }
  }

  /** The input folder of `files` (the shortest folder they are in: Include subfolders' files are under it). */
  static File commonRoot(List<File> files) {
    File best = null;
    for (File f : files) {
      File d = f.getAbsoluteFile().getParentFile();
      if (best == null || (d != null && d.getAbsolutePath().length() < best.getAbsolutePath().length())) best = d;
    }
    return best;
  }

  /** A file's name in the input folder: its path under it ("sub/a.txt"), or its own name. */
  public static String rel(File root, File f) {
    if (root == null || f == null) return f == null ? "" : f.getName();
    String r = root.getAbsolutePath(), p = f.getAbsolutePath();
    return p.startsWith(r + File.separator) ? p.substring(r.length() + 1).replace(File.separatorChar, '/') : f.getName();
  }

  /** Skip already processed's list: kept for the program and the folder as given (a picked folder's address). */
  public static String doneKey(String program, String folder) {
    return SogniSections.app(program) + ":" + folder;
  }

  /** The section named in Section (--section): its name or its label, without case; null when none is. */
  public static PromptVault.SogniSection section(PromptVault vault, String program, String name) {
    if (vault == null || name == null || name.trim().isEmpty()) return null;
    String want = name.trim();
    for (PromptVault.SogniSection s : vault.sogniSections(SogniSections.app(program))) {
      if (s.name != null && s.name.trim().equalsIgnoreCase(want)) return s;
    }
    for (PromptVault.SogniSection s : vault.sogniSections(SogniSections.app(program))) {
      if (SogniSections.label(s).toLowerCase(Locale.ROOT).contains(want.toLowerCase(Locale.ROOT))) return s;
    }
    return null;
  }

  /**
   * One file's Params: `base` (the Params, or with the section's entries) without the folder run's own rows,
   * the file in: SogniPedit's picture (--image), SogniPadd's prompt from the file (a definitions file's rows
   * too). (The output name is the argument line's: %f in it is the file's name, BatchQueue.withFile.)
   */
  public static String paramsFor(String program, List<ProgramParams.Param> ps, String base, File f, byte[] bytes) {
    String[] values = ProgramParams.values(ps, base == null ? "" : base, "");
    java.util.Map<String, String> defs = null;
    String text = null;
    // Input as (its value before the folder run's own rows are cleared): where the picture goes.
    String inputAs = "";
    for (int i = 0; i < ps.size() && i < values.length; i++) if (ps.get(i).token.equals("--input_as")) inputAs = values[i];
    String picRow = pictures(program) ? pictureRow(program, inputAs) : null;
    if (!pictures(program)) {
      String raw = bytes == null ? "" : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
      if (raw.startsWith("﻿")) raw = raw.substring(1);
      if (ParamDefs.looksLike(raw)) {
        java.util.Set<String> tokens = new java.util.HashSet<String>();
        for (ProgramParams.Param p : ps) if (p.flag) tokens.add(p.token);
        List<ParamDefs.Def> found = ParamDefs.forProgram(ParamDefs.parse(raw, tokens), program);
        if (!found.isEmpty()) defs = found.get(0).fields;
      }
      if (defs == null) text = ProgramParams.promptText(bytes);
    }
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      ProgramParams.Param p = ps.get(i);
      if (java.util.Arrays.asList(OWN).contains(p.token)) values[i] = "";
      else if (picRow != null && p.token.equals(picRow)) values[i] = f.getAbsolutePath();
      else if (defs != null && p.flag && defs.containsKey(p.token)) values[i] = defs.get(p.token);
      else if (text != null && p.token.equals("--prompt")) values[i] = text;
    }
    // The program's Batch prompt after the file's prompt.
    return Chain.addBatch(ps, ProgramParams.build(ps, values));
  }

  /**
   * The batch: the folder's files the program takes (Skip already processed: not those done before; at
   * most Max files), each run with its own Params. `files` are local (a picked folder's copied first);
   * `folder` as given, for Skip already processed's list. Null when there is none to run; `notes` say why.
   */
  public static BatchQueue start(String program, List<ProgramParams.Param> ps, String saved, List<String> argv, List<File> files, String folder,
      PromptVault vault, List<String> notes) throws Exception {
    return start(program, ps, saved, argv, files, folder, vault, notes, commonRoot(files));
  }

  /** As above; `root`: the folder the files are in (Include subfolders: each one's name is its path under it). */
  public static BatchQueue start(String program, List<ProgramParams.Param> ps, String saved, List<String> argv, List<File> files, String folder,
      PromptVault vault, List<String> notes, File root) throws Exception {
    String key = doneKey(program, folder);
    List<File> todo = new ArrayList<File>(files);
    // (The files' names in the folder, a subfolder's its path "sub/a.txt": what Skip already processed keeps.)
    if (argv.contains("--skip_done")) {
      java.util.Set<String> done = ChatFolder.done(key);
      List<File> fresh = new ArrayList<File>();
      for (File f : todo) if (!done.contains(rel(root, f))) fresh.add(f);
      int skipped = todo.size() - fresh.size();
      if (skipped > 0) notes.add(skipped + " file" + (skipped == 1 ? "" : "s") + " done before, left out (Skip already processed)");
      todo = fresh;
    }
    int max = ChatFolder.max(argv);
    if (todo.isEmpty()) {
      notes.add(files.isEmpty() ? "No " + (pictures(program) ? "pictures (PNG, JPEG, WebP)" : ".txt or .prompt files") + " in the folder"
          : "Nothing new: every file in the folder was done before (Skip already processed)");
      return null;
    }
    String base = saved;
    String sectionName = ChatFolder.arg(argv, "--section");
    if (sectionName != null) {
      PromptVault.SogniSection s = section(vault, program, sectionName);
      if (s == null) notes.add("No " + SogniSections.app(program) + " section named " + sectionName + ": the Params as they are");
      else {
        base = BatchQueue.paramsFor(ps, saved, s);
        notes.add("Section " + SogniSections.label(s) + " with each file");
      }
    }
    List<String> params = new ArrayList<String>(), labels = new ArrayList<String>(), names = new ArrayList<String>();
    // Max files counts only the files that make a run: a text file with no prompt in it is passed over, not counted.
    int empty = 0, more = 0;
    for (File f : todo) {
      byte[] bytes = pictures(program) ? null : java.nio.file.Files.readAllBytes(f.toPath());
      if (bytes != null && ProgramParams.promptText(bytes).trim().isEmpty()) {
        empty++;
        continue;
      }
      if (params.size() >= max) {
        more++;
        continue;
      }
      params.add(paramsFor(program, ps, base, f, bytes));
      labels.add(rel(root, f));
      names.add(rel(root, f));
    }
    if (empty > 0) notes.add(empty + " file" + (empty == 1 ? "" : "s") + " with no prompt in it, passed over (not counted in Max files)");
    if (more > 0) notes.add((params.size() + more) + " files to run; the first " + max + " by name are run (Max files)");
    if (params.isEmpty()) {
      notes.add("No file with a prompt in it left to run");
      return null;
    }
    BatchQueue q = BatchQueue.files(program, ps, params, labels, names, key);
    if (q != null) {
      q.notes.addAll(notes);
      q.folder = MediaDir.label(folder);
    }
    return q;
  }
}
