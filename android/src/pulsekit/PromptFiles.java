package pulsekit;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;

/** Writes a prompt's reference files out of the encrypted database. */
public final class PromptFiles {
  public static final class Saved {
    public String ref1Name = "";
    public String ref1Path = "";
    public String ref2Name = "";
    public String ref2Path = "";
    public String description = "";
    public String resultName = "";
    public String note = "";
  }

  private PromptFiles() {}

  public static Saved write(Context context, String title, String category, String ref1Name, String ref2Name) {
    Saved saved = new Saved();
    try {
      PromptVault vault = PromptVault.open(context.getFilesDir());
      PromptVault.Version version = vault.selectedRefs(title, category, ref1Name, ref2Name);
      if (version == null) {
        saved.note = "Reference file 1: none\nReference file 2: none";
        return saved;
      }
      File dir = new File(context.getCacheDir(), "pyjav-in");
      if (!dir.isDirectory() && !dir.mkdirs()) {
        saved.note = "Could not store the reference files.";
        return saved;
      }
      saved.ref1Name = version.ref1Name == null ? "" : version.ref1Name;
      saved.ref2Name = version.ref2Name == null ? "" : version.ref2Name;
      saved.description = version.description == null ? "" : version.description;
      saved.resultName = version.result != null && version.result.length > 0 && version.resultName != null ? version.resultName : "";
      if (version.ref1 != null && version.ref1.length > 0) saved.ref1Path = store(dir, "ref1-", saved.ref1Name, version.ref1);
      if (version.ref2 != null && version.ref2.length > 0) saved.ref2Path = store(dir, "ref2-", saved.ref2Name, version.ref2);
      saved.note = line("Reference file 1", saved.ref1Name, saved.ref1Path) + "\n" + line("Reference file 2", saved.ref2Name, saved.ref2Path);
      String kind = version.codeType == null ? "" : PromptRun.normalizeType(version.codeType);
      String raw = version.body == null ? "" : version.body;
      if (kind.length() == 0) kind = PromptRun.defaultMode(raw);
      if ("ai".equals(kind)) {
        Subsystem.copyPrompt(context, PromptRun.withoutDescription(version.description, raw));
        saved.note = saved.note + "\nPrompt copied.";
      }
    } catch (Exception ex) {
      saved.note = "Could not read the encrypted database.";
    }
    return saved;
  }

  private static String line(String label, String name, String path) {
    if (path == null || path.length() == 0) return label + ": none";
    String shown = name == null || name.length() == 0 ? "file" : name;
    return label + ": " + shown + "\n" + path;
  }

  private static String store(File dir, String prefix, String name, byte[] bytes) throws Exception {
    String fileName = prefix + safe(name);
    File file = new File(dir, fileName);
    FileOutputStream out = new FileOutputStream(file);
    try {
      out.write(bytes);
    } finally {
      out.close();
    }
    return file.getAbsolutePath();
  }

  /** Stores a run's output as the prompt result when the user had not chosen one. */
  public static String storeResult(Context context, String title, String category, String ref1Name, String ref2Name, String fileName, byte[] bytes) {
    if (fileName == null || fileName.length() == 0 || bytes == null) return "";
    try {
      PromptVault vault = PromptVault.open(context.getFilesDir());
      PromptVault.Version version = vault.selectedRefs(title, category, ref1Name, ref2Name);
      if (version == null) return fileName;
      if (version.result != null && version.result.length > 0 && version.resultName != null && version.resultName.length() > 0) return version.resultName;
      vault.putFile(version.id, 3, fileName, bytes);
      return fileName;
    } catch (Exception ex) {
      return fileName;
    }
  }

  private static String safe(String name) {
    String raw = name == null || name.trim().length() == 0 ? "file" : name.trim();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length() && sb.length() < 80; i++) {
      char c = raw.charAt(i);
      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == '_') sb.append(c);
      else sb.append('_');
    }
    return sb.length() == 0 ? "file" : sb.toString();
  }
}
