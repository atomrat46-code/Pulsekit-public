package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A prompt sheet a run saved (--saveprompt), after the run: one that is the same as a version in
 * the prompt library already (the same prompt and settings, model and reference files) is neither
 * stored nor written to disk again. Otherwise the app asks where it goes (main category and
 * subcategory, browsed or new, the ones used recently offered), its name (the one chosen last for
 * a sheet of that name offered) and description; it is
 * stored as the next version of the prompt of that name there, and the sheet written to disk gets
 * that version number and the date.
 */
public final class PromptSave {
  private PromptSave() {}

  /** Where a sheet goes, as the dialog filled it. */
  public static final class Choice {
    public String main = "";
    public String sub = "";
    public String name = "";
    public String description = "";
  }

  /** A sheet to save: its file, as the run wrote it, and the sheet read from it. */
  public static final class Pending {
    public JavaRun.FileOut file;
    public PromptRun.Sheet sheet;
  }

  /** What a save did: a line for the log, and the sheet to write to disk (null: none). */
  public static final class Saved {
    public String note = "";
    public String fileName;
    public byte[] bytes;
  }

  /** The library folder's settings: Ask where to save (default on) and the recent places. */
  private static File settingsDir;
  private static boolean ask = true;
  /** Recent places, the last first: "sheet category\tmain\tsub". */
  private static final List<String> RECENT = new ArrayList<String>();
  private static final int MAX_RECENT = 8;
  /** The names chosen for a program's sheets, the last first: "sheet's name\tname chosen". */
  private static final List<String> NAMES = new ArrayList<String>();
  private static final int MAX_NAMES = 30;

  /** Reads the settings kept beside the library in `dir` (prompt-save.txt). */
  public static synchronized void load(File dir) {
    settingsDir = dir;
    ask = true;
    RECENT.clear();
    NAMES.clear();
    if (dir == null) return;
    File f = new File(dir, "prompt-save.txt");
    if (!f.isFile()) return;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
        if (line.startsWith("ask=")) ask = !line.substring(4).trim().equals("0");
        else if (line.startsWith("recent=")) {
          try {
            String place = Sealed.openText(line.substring(7).trim());
            if (place.split("\t", -1).length == 3 && !RECENT.contains(place)) RECENT.add(place);
          } catch (Exception ex) {
            // unreadable: left out
          }
        } else if (line.startsWith("name=")) {
          try {
            String pair = Sealed.openText(line.substring(5).trim());
            if (pair.split("\t", -1).length == 2 && !NAMES.contains(pair)) NAMES.add(pair);
          } catch (Exception ex) {
            // unreadable: left out
          }
        }
      }
    } catch (Exception ignored) {
      // defaults
    }
  }

  private static synchronized void save() {
    if (settingsDir == null) return;
    StringBuilder sb = new StringBuilder("ask=").append(ask ? 1 : 0).append('\n');
    for (String place : RECENT) {
      try {
        // Category names are sealed with the library's key, as the library is; without a key they are not kept.
        sb.append("recent=").append(Sealed.sealText(place)).append('\n');
      } catch (Exception ex) {
        // not kept
      }
    }
    for (String pair : NAMES) {
      try {
        sb.append("name=").append(Sealed.sealText(pair)).append('\n');
      } catch (Exception ex) {
        // not kept
      }
    }
    try {
      if (!settingsDir.isDirectory()) settingsDir.mkdirs();
      java.nio.file.Files.write(new File(settingsDir, "prompt-save.txt").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
  }

  /** General settings: Ask where to save a prompt after a run. Off: it goes where the sheet says, as before. */
  public static synchronized boolean ask() {
    return ask;
  }

  public static synchronized void setAsk(boolean on) {
    ask = on;
    save();
  }

  /** The places used recently, the last first, as {main, sub}. */
  public static synchronized List<String[]> recent() {
    List<String[]> out = new ArrayList<String[]>();
    List<String> seen = new ArrayList<String>();
    for (String place : RECENT) {
      String[] p = place.split("\t", -1);
      String key = (p[1] + "\t" + p[2]).toLowerCase(java.util.Locale.ROOT);
      if (seen.contains(key)) continue;
      seen.add(key);
      out.add(new String[] {p[1], p[2]});
    }
    return out;
  }

  /** "Image › Portraits", or "Image" without a subcategory. */
  public static String placeLabel(String main, String sub) {
    String m = main == null ? "" : main.trim();
    String s = sub == null ? "" : sub.trim();
    return s.length() == 0 ? m : m + " › " + s;
  }

  private static synchronized void used(String sheetCategory, String main, String sub, String sheetName, String name) {
    String place = one(sheetCategory) + "\t" + one(main) + "\t" + one(sub);
    RECENT.remove(place);
    RECENT.add(0, place);
    while (RECENT.size() > MAX_RECENT) RECENT.remove(RECENT.size() - 1);
    // The program names its sheet the same way next time (sogni-pedit-same-person): the name chosen then is offered.
    for (int i = NAMES.size() - 1; i >= 0; i--) if (NAMES.get(i).startsWith(one(sheetName) + "\t")) NAMES.remove(i);
    if (!one(sheetName).equals(one(name))) NAMES.add(0, one(sheetName) + "\t" + one(name));
    while (NAMES.size() > MAX_NAMES) NAMES.remove(NAMES.size() - 1);
    save();
  }

  /**
   * The dialog's first values: the place used last for a sheet of this kind (its Category line:
   * image, music...), else the library's category of that name (else the sheet's), no subcategory;
   * the sheet's name and description.
   */
  public static Choice suggest(PromptVault vault, PromptRun.Sheet sheet) {
    Choice c = new Choice();
    String kind = sheet.category == null ? "" : sheet.category.trim();
    synchronized (PromptSave.class) {
      for (String place : RECENT) {
        String[] p = place.split("\t", -1);
        if (p[0].equalsIgnoreCase(kind)) {
          c.main = p[1];
          c.sub = p[2];
          break;
        }
      }
    }
    if (c.main.length() == 0) {
      c.main = kind.length() == 0 ? "General" : kind;
      if (vault != null) for (PromptVault.Category m : vault.mains()) if (m.name != null && m.name.trim().equalsIgnoreCase(c.main)) c.main = m.name.trim();
    }
    c.name = sheet.name == null ? "" : sheet.name.trim();
    synchronized (PromptSave.class) {
      for (String pair : NAMES) {
        if (pair.startsWith(c.name + "\t")) {
          c.name = pair.substring(c.name.length() + 1);
          break;
        }
      }
    }
    c.description = sheet.description == null ? "" : sheet.description.trim();
    return c;
  }

  /** The sheets among a run's files (.prompt files that read as sheets with a name). */
  public static List<Pending> sheets(JavaRun.Result result) {
    List<Pending> out = new ArrayList<Pending>();
    if (result == null || result.files == null) return out;
    for (JavaRun.FileOut f : result.files) {
      if (!isSheetFile(f)) continue;
      PromptRun.Sheet sheet = PromptRun.parse(new String(f.bytes, StandardCharsets.UTF_8));
      if (sheet == null || sheet.name.trim().length() == 0) continue;
      Pending p = new Pending();
      p.file = f;
      p.sheet = sheet;
      out.add(p);
    }
    return out;
  }

  /** A .prompt file a run wrote: the app writes it to disk itself, after asking (or not, when unchanged). */
  public static boolean isSheetFile(JavaRun.FileOut f) {
    return f != null && f.name != null && f.bytes != null && f.name.toLowerCase(java.util.Locale.ROOT).endsWith(".prompt")
        && PromptRun.parse(new String(f.bytes, StandardCharsets.UTF_8)) != null;
  }

  /**
   * The library's version the same as the sheet (the same prompt and settings, model and reference
   * files), as "Image › Portraits: Smile v2"; null when it has none (something changed).
   */
  public static String unchanged(PromptVault vault, PromptRun.Sheet sheet) {
    if (vault == null || sheet == null) return null;
    String body = sheet.body == null ? "" : sheet.body.trim();
    for (PromptVault.Category c : vault.categories()) {
      for (PromptVault.Prompt p : vault.prompts(c.id)) {
        List<PromptVault.Version> rows = vault.versions(p.id);
        for (int i = 0; i < rows.size(); i++) {
          PromptVault.Version v = rows.get(i);
          String stored = PromptRun.withoutDescription(v.description, v.body == null ? "" : v.body).trim();
          if (!body.equals(stored) || !same(sheet.model, v.model) || !same(sheet.ref1, v.ref1Name) || !same(sheet.ref2, v.ref2Name)) continue;
          long main = vault.mainOf(c.id);
          PromptVault.Category m = vault.category(main);
          String place = main == c.id || m == null ? c.name : placeLabel(m.name, c.name);
          return place + ": " + p.title + " v" + (rows.size() - i);
        }
      }
    }
    return null;
  }

  /** The line for a sheet that was not saved again. */
  public static String unchangedNote(PromptRun.Sheet sheet, String same) {
    return "Prompt library: " + sheet.name.trim() + " unchanged (the same as " + same + "), not saved again";
  }

  /** The line for a sheet the dialog was cancelled for. */
  public static String cancelledNote(PromptRun.Sheet sheet) {
    return "Prompt library: " + sheet.name.trim() + " not saved (cancelled)";
  }

  /** The next version number of the prompt `name` in that place (1 when there is none yet). */
  public static int nextVersion(PromptVault vault, String main, String sub, String name) {
    long cat = find(vault, main, sub);
    if (cat == 0) return 1;
    for (PromptVault.Prompt p : vault.prompts(cat)) {
      if (p.title != null && p.title.trim().equalsIgnoreCase(name.trim())) return vault.versions(p.id).size() + 1;
    }
    return 1;
  }

  /** The category for main / sub, 0 when it is not there. */
  static long find(PromptVault vault, String main, String sub) {
    long mainId = 0;
    for (PromptVault.Category c : vault.mains()) if (c.name != null && c.name.trim().equalsIgnoreCase(one(main))) mainId = c.id;
    if (mainId == 0 || one(sub).length() == 0) return mainId;
    for (PromptVault.Category c : vault.children(mainId)) if (c.name != null && c.name.trim().equalsIgnoreCase(one(sub))) return c.id;
    return 0;
  }

  /** The category for main / sub, made when it is not there. */
  static long place(PromptVault vault, String main, String sub) throws Exception {
    String m = one(main).length() == 0 ? "General" : one(main);
    long mainId = find(vault, m, "");
    if (mainId == 0) mainId = vault.addCategory(m);
    if (one(sub).length() == 0) return mainId;
    long subId = find(vault, m, sub);
    return subId != 0 ? subId : vault.addSubcategory(mainId, one(sub));
  }

  /** "2026-10-12". */
  public static String today() {
    return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date());
  }

  /**
   * Stores the sheet where `c` says, as the next version of its prompt there, and gives the sheet
   * to write to disk: named and placed as chosen, with its version number and the date.
   */
  public static Saved store(File dir, Pending pending, JavaRun.Result result, List<String> argv, Choice c) {
    Saved out = new Saved();
    PromptRun.Sheet sheet = pending.sheet;
    String given = sheet.name.trim();
    String name = one(c.name).length() == 0 ? given : one(c.name);
    try {
      PromptVault vault = PromptVault.open(dir);
      int version = nextVersion(vault, c.main, c.sub, name);
      String date = today();
      long cat = place(vault, c.main, c.sub);
      sheet.name = name;
      sheet.description = one(c.description);
      out.note = PromptKeep.store(vault, sheet, result, argv, cat, placeLabel(c.main.length() == 0 ? "General" : c.main, c.sub)) + ", v" + version + ", " + date;
      used(pending.sheet.category, c.main.length() == 0 ? "General" : c.main, c.sub, given, name);
      out.bytes = rewrite(new String(pending.file.bytes, StandardCharsets.UTF_8), name, c, version, date).getBytes(StandardCharsets.UTF_8);
      out.fileName = fileName(pending.file.name, sheet, name);
    } catch (Exception ex) {
      out.note = "Prompt library: could not store " + pending.file.name + (ex.getMessage() == null ? "" : " (" + ex.getMessage() + ")");
      out.fileName = pending.file.name;
      out.bytes = pending.file.bytes;
    }
    return out;
  }

  /**
   * Stores the sheet where it says (Ask where to save off), with its version number and the date,
   * as the dialog's first values would.
   */
  public static Saved storeAsIs(File dir, Pending pending, JavaRun.Result result, List<String> argv) {
    PromptVault vault = null;
    try {
      vault = PromptVault.open(dir);
    } catch (Exception ignored) {
      // store says why
    }
    Choice c = new Choice();
    c.main = pending.sheet.category.trim().length() == 0 ? "General" : pending.sheet.category.trim();
    if (vault != null) for (PromptVault.Category m : vault.mains()) if (m.name != null && m.name.trim().equalsIgnoreCase(c.main)) c.main = m.name.trim();
    c.name = pending.sheet.name.trim();
    c.description = pending.sheet.description == null ? "" : pending.sheet.description.trim();
    return store(dir, pending, result, argv, c);
  }

  /** The file's name: the run's, or the chosen name's when it was renamed. */
  private static String fileName(String given, PromptRun.Sheet sheet, String name) {
    String was = given == null ? "prompt.prompt" : given;
    String stem = was.replaceAll("(?i)\\.prompt$", "");
    if (name.equals(stem) || name.length() == 0) return was;
    String safe = name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]+", "_").trim();
    return (safe.length() == 0 ? stem : safe) + ".prompt";
  }

  /** The sheet text with the chosen name, place, description, version and date. */
  static String rewrite(String text, String name, Choice c, int version, String date) {
    String norm = text.replace("\r\n", "\n").replace("\r", "\n");
    String[] lines = norm.split("\n", -1);
    int dash = -1;
    for (int i = 1; i < lines.length; i++) {
      if ("---".equals(lines[i])) {
        dash = i;
        break;
      }
    }
    if (dash < 0 || lines.length < 2) return text;
    StringBuilder sb = new StringBuilder();
    sb.append(lines[0]).append('\n').append(one(name)).append('\n');
    boolean placed = false;
    for (int i = 2; i < dash; i++) {
      String l = lines[i];
      // The first five lines are fields (outputs, references); labels follow.
      if (i < 6) {
        sb.append(l).append('\n');
        continue;
      }
      if (l.startsWith("Category: ")) {
        sb.append("Category: ").append(one(c.main)).append('\n');
        if (one(c.sub).length() > 0) sb.append("Subcategory: ").append(one(c.sub)).append('\n');
        placed = true;
        continue;
      }
      if (l.startsWith("Subcategory: ") || l.startsWith("Version: ") || l.startsWith("Date: ") || l.startsWith("Description: ")) continue;
      sb.append(l).append('\n');
    }
    if (!placed) {
      sb.append("Category: ").append(one(c.main)).append('\n');
      if (one(c.sub).length() > 0) sb.append("Subcategory: ").append(one(c.sub)).append('\n');
    }
    sb.append("Version: ").append(version).append('\n');
    sb.append("Date: ").append(date).append('\n');
    if (one(c.description).length() > 0) sb.append("Description: ").append(one(c.description)).append('\n');
    for (int i = dash; i < lines.length; i++) sb.append(lines[i]).append(i < lines.length - 1 ? "\n" : "");
    return sb.toString();
  }

  private static boolean same(String a, String b) {
    return (a == null ? "" : a.trim()).equals(b == null ? "" : b.trim());
  }

  private static String one(String s) {
    return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
  }
}
