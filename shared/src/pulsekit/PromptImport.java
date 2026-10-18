package pulsekit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Prompts page > Import: a .prompt sheet (as Export or a program's --saveprompt writes it) into
 * the prompt library, as a new prompt or a new version of the one of that name in its category,
 * with its reference and result files. A file of the same name already in the library is used as
 * it is (linked, not added again); otherwise the file picked with the sheet is added. A sheet the
 * library already holds (same text, model and file names) is not added again.
 */
public final class PromptImport {
  private static final int MAX_BYTES = 128 * 1024 * 1024;

  private PromptImport() {}

  /**
   * Imports one sheet; `files` are the other files picked with it, by name. Returns a line for the
   * status ("Imported Smile (Image, new version): reference face.png (already in DB, linked)").
   * Throws with a message when it is not a prompt sheet.
   */
  public static String importSheet(PromptVault vault, String fileName, byte[] sheetBytes, Map<String, byte[]> files) throws Exception {
    PromptRun.Sheet sheet = PromptRun.parse(new String(sheetBytes, StandardCharsets.UTF_8));
    if (sheet == null) throw new IllegalArgumentException(fileName + " is not a prompt sheet");
    String name = sheet.name.trim().length() > 0 ? sheet.name.trim() : PromptVault.fileTitle(fileName).replaceAll("\\.prompt$", "");
    String catName = sheet.category.trim().length() == 0 ? "General" : sheet.category.trim();
    long catId = 0;
    if (sheet.subcategory.trim().length() > 0) {
      // A sheet saved into a subcategory (Subcategory: line) goes back there.
      catId = PromptSave.place(vault, catName, sheet.subcategory);
      catName = PromptSave.placeLabel(catName, sheet.subcategory);
    }
    for (PromptVault.Category c : vault.categories()) {
      if (catId != 0) break;
      if (c.name != null && c.name.trim().equalsIgnoreCase(catName)) {
        catId = c.id;
        break;
      }
    }
    if (catId == 0) catId = vault.addCategory(catName);
    long promptId = 0;
    for (PromptVault.Prompt p : vault.prompts(catId)) {
      if (p.title != null && p.title.trim().equalsIgnoreCase(name)) promptId = p.id;
    }
    String body = sheet.body == null ? "" : sheet.body;
    // The same sheet imported again: nothing new.
    if (promptId != 0) {
      List<PromptVault.Version> rows = vault.versions(promptId);
      for (int i = 0; i < rows.size(); i++) {
        PromptVault.Version v = rows.get(i);
        if (body.equals(v.body) && same(sheet.model, v.model) && same(sheet.ref1, v.ref1Name) && same(sheet.ref2, v.ref2Name) && same(sheet.result, v.resultName)) {
          return name + " (" + catName + ") is in the library already, as v" + (rows.size() - i);
        }
      }
    }
    boolean added = promptId == 0;
    if (added) promptId = vault.addPrompt(catId, name);
    List<String> notes = new ArrayList<String>();
    byte[] ref1 = file(vault, sheet.ref1, files, false, "reference", notes);
    byte[] ref2 = file(vault, sheet.ref2, files, false, "reference", notes);
    byte[] result = file(vault, sheet.result, files, true, "result", notes);
    long id = vault.addVersion(promptId, name, sheet.description, body, sheet.model, sheet.ref1, ref1, sheet.ref2, ref2, sheet.result, result, sheet.type, sheet.resultText);
    StringBuilder sb = new StringBuilder("Imported ").append(name).append(" (").append(catName).append(added ? ", new" : ", new version");
    if (sheet.version.trim().length() > 0) sb.append("; the sheet's version: ").append(sheet.version.trim());
    sb.append(')');
    for (int i = 0; i < notes.size(); i++) sb.append(i == 0 ? ": " : ", ").append(notes.get(i));
    if (id == 0) sb.append(" (not stored)");
    return sb.toString();
  }

  private static boolean same(String a, String b) {
    return (a == null ? "" : a.trim()).equals(b == null ? "" : b.trim());
  }

  /**
   * The file a sheet names: the library's file of that name when it has one (linked; the picked
   * one, when it differs, is added instead), else the file picked with the sheet; null when neither.
   */
  private static byte[] file(PromptVault vault, String name, Map<String, byte[]> files, boolean result, String kind, List<String> notes) {
    if (name == null || name.trim().length() == 0) return null;
    String n = name.trim();
    byte[] picked = files == null ? null : files.get(n);
    byte[] stored = null;
    List<PromptVault.StoredFile> all = new ArrayList<PromptVault.StoredFile>(result ? vault.resultFiles() : vault.referenceFiles());
    all.addAll(result ? vault.referenceFiles() : vault.resultFiles());
    for (PromptVault.StoredFile f : all) {
      if (!n.equals(f.name)) continue;
      byte[] b = vault.fileBytes(f.versionId, f.which);
      if (b == null || b.length == 0) continue;
      if (picked == null || Arrays.equals(b, picked)) {
        stored = b;
        break;
      }
    }
    if (stored != null) {
      notes.add(kind + " " + n + " (already in DB, linked)");
      return stored;
    }
    if (picked != null && picked.length > MAX_BYTES) {
      notes.add(kind + " " + n + " (over 128 MB, by name only)");
      return null;
    }
    if (picked != null) {
      notes.add(kind + " " + n + " (added)");
      return picked;
    }
    notes.add(kind + " " + n + " (not found: pick it with the sheet)");
    return null;
  }

  /** The sheets among picked files: those ending .prompt, or holding a sheet. */
  public static boolean isSheet(String name, byte[] bytes) {
    if (name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".prompt")) return true;
    return bytes != null && bytes.length > 9 && new String(bytes, 0, 9, StandardCharsets.UTF_8).equals("PKPROMPT1");
  }

  /** Imports every sheet among `picked` (name -> bytes), the rest as their files; a line each. */
  public static String importAll(PromptVault vault, Map<String, byte[]> picked) {
    StringBuilder out = new StringBuilder();
    int sheets = 0;
    for (Map.Entry<String, byte[]> e : picked.entrySet()) {
      if (!isSheet(e.getKey(), e.getValue())) continue;
      sheets++;
      String line;
      try {
        line = importSheet(vault, e.getKey(), e.getValue(), picked);
      } catch (Exception ex) {
        line = "Could not import " + e.getKey() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
      }
      out.append(out.length() > 0 ? "\n" : "").append(line);
    }
    if (sheets == 0) return "No prompt sheet (.prompt) among the files picked";
    return out.toString();
  }
}
