package pulsekit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-GCM prompt database, the same on the phone and the desktop. The file on disk is ciphertext;
 * the key comes from the platform (keys): the Android keystore on the phone, a key file readable
 * only by the user on the desktop.
 */
public final class PromptVault {
  /** Where the library's AES key comes from: set by the app at start. */
  public interface Keys {
    SecretKey key() throws Exception;
  }

  public static volatile Keys keys;

  public static final class Category {
    public long id;
    public long parentId;
    public String name;
  }

  public static final class Prompt {
    public long id;
    public long categoryId;
    public String title;
    public long created;
  }

  public static final class Version {
    public long id;
    public long promptId;
    public String description;
    public String body;
    public String ref1Name;
    public byte[] ref1;
    public String ref2Name;
    public byte[] ref2;
    public String resultName;
    public byte[] result;
    public String model;
    public String codeType;
    public String resultText;
    public long created;
    public boolean finalVersion;
  }

  private static final int MAGIC = 0x504B4442;
  private static final byte[] FILE_MAGIC = new byte[] {'P', 'K', 'V', '1'};
  private static final int MAX_BYTES = 16 * 1024 * 1024;

  private final File file;
  private final List<Category> categories = new ArrayList<Category>();
  private final List<Prompt> prompts = new ArrayList<Prompt>();
  private final List<Version> versions = new ArrayList<Version>();
  private final List<LibraryFile> library = new ArrayList<LibraryFile>();
  private long nextId = 1;

  /**
   * The one library in memory: the Prompts page, PyJav (saved sheets, MidiDrumGen output), Browse
   * DB and File > Import all use it, so a save from one never writes back an older copy over what
   * another stored. Read again only when the file changed on disk since this copy last wrote or
   * read it.
   */
  private static PromptVault shared;
  /** The file's time when this copy last read or wrote it; 0 before. */
  private long stamp;

  private PromptVault(File file) {
    this.file = file;
  }

  /** The library kept in `dir` (the app's files folder on the phone, ~/.pulsekit on the desktop). */
  public static synchronized PromptVault open(File dir) throws Exception {
    File at = new File(dir, "prompts.vault");
    if (shared != null && shared.file.getAbsolutePath().equals(at.getAbsolutePath())) {
      if (at.isFile() ? at.lastModified() != shared.stamp : shared.stamp != 0) shared.reload();
      return shared;
    }
    PromptVault vault = new PromptVault(at);
    vault.load();
    shared = vault;
    return vault;
  }

  private void load() throws Exception {
    if (file.isFile()) read();
    if (categories.isEmpty()) seed();
    ensure("Image");
    ensure("video");
  }

  /** Reads the file again into this same library (it changed on disk). */
  private void reload() throws Exception {
    categories.clear();
    prompts.clear();
    versions.clear();
    library.clear();
    nextId = 1;
    stamp = 0;
    load();
  }

  public List<Category> categories() {
    return new ArrayList<Category>(categories);
  }

  public List<Category> mains() {
    List<Category> out = new ArrayList<Category>();
    for (int i = 0; i < categories.size(); i++) {
      if (categories.get(i).parentId == 0) out.add(categories.get(i));
    }
    return out;
  }

  public List<Category> children(long parentId) {
    List<Category> out = new ArrayList<Category>();
    for (int i = 0; i < categories.size(); i++) {
      if (categories.get(i).parentId == parentId) out.add(categories.get(i));
    }
    return out;
  }

  public long mainOf(long id) {
    Category category = category(id);
    if (category == null) return 0;
    if (category.parentId == 0) return category.id;
    Category parent = category(category.parentId);
    return parent == null ? category.id : parent.id;
  }

  public List<Prompt> prompts(long categoryId) {
    List<Prompt> out = new ArrayList<Prompt>();
    for (int i = 0; i < prompts.size(); i++) {
      Prompt prompt = prompts.get(i);
      if (prompt.categoryId == categoryId) out.add(prompt);
    }
    return out;
  }

  public static final class StoredFile {
    public long versionId;
    public int which;
    public String name;
    public String promptTitle;
    public int size;
  }

  public List<StoredFile> referenceFiles() {
    List<StoredFile> out = new ArrayList<StoredFile>();
    for (int i = library.size() - 1; i >= 0; i--) if (library.get(i).which != 3) addLibrary(out, library.get(i));
    for (int i = versions.size() - 1; i >= 0; i--) {
      Version version = versions.get(i);
      addStored(out, version, 1);
      addStored(out, version, 2);
    }
    return out;
  }

  public List<StoredFile> resultFiles() {
    List<StoredFile> out = new ArrayList<StoredFile>();
    // Files imported as result files (File > Import as Result file) first, newest first.
    for (int i = library.size() - 1; i >= 0; i--) if (library.get(i).which == 3) addLibrary(out, library.get(i));
    for (int i = versions.size() - 1; i >= 0; i--) addStored(out, versions.get(i), 3);
    return out;
  }

  public byte[] fileBytes(long versionId, int which) {
    Version version = version(versionId);
    if (version != null) {
      byte[] bytes = which == 1 ? version.ref1 : which == 2 ? version.ref2 : version.result;
      return copy(bytes);
    }
    LibraryFile image = libraryFile(versionId);
    if (image == null) return new byte[0];
    return copy(image.bytes);
  }

  private void addStored(List<StoredFile> out, Version version, int which) {
    byte[] bytes = which == 1 ? version.ref1 : which == 2 ? version.ref2 : version.result;
    if (bytes == null || bytes.length == 0) return;
    String name = which == 1 ? version.ref1Name : which == 2 ? version.ref2Name : version.resultName;
    if (name == null || name.length() == 0) name = "file";
    String title = titleOf(version.promptId);
    for (int i = 0; i < out.size(); i++) {
      StoredFile have = out.get(i);
      if (have.which == which && have.size == bytes.length && name.equals(have.name) && title.equals(have.promptTitle)) return;
    }
    StoredFile row = new StoredFile();
    row.versionId = version.id;
    row.which = which;
    row.name = name;
    row.promptTitle = title;
    row.size = bytes.length;
    out.add(row);
  }

  /** A still image kept in the encrypted database so it can be picked as a reference file. */
  public long addReferenceImage(String name, byte[] bytes, String note, int which) throws Exception {
    if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("That frame is empty");
    if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Frame is too large (max 16 MB)");
    String clean = fileTitle(name);
    if (clean.length() == 0) clean = "frame.jpg";
    String lower = clean.toLowerCase(java.util.Locale.US);
    if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png")) clean = clean + ".jpg";
    String label = note == null ? "" : note.replace('\n', ' ').replace('\r', ' ').trim();
    if (label.length() == 0) label = "Frame";
    for (int i = 0; i < library.size(); i++) {
      LibraryFile have = library.get(i);
      if (have.name != null && have.name.equalsIgnoreCase(clean)) {
        have.bytes = copy(bytes);
        have.note = label;
        have.which = which == 2 ? 2 : 1;
        save();
        return have.id;
      }
    }
    LibraryFile image = new LibraryFile();
    image.id = nextId++;
    image.name = clean;
    image.bytes = copy(bytes);
    image.note = label;
    image.which = which == 2 ? 2 : 1;
    library.add(image);
    save();
    return image.id;
  }

  /**
   * A file kept on its own (File > Import as Ref file / Import as Result file), `which` 1 for a
   * reference file, 3 for a result file. Its name is kept as it is; a file of the same name and
   * kind is replaced. `note` shows where a prompt's title would.
   */
  public long addLibraryFile(String name, byte[] bytes, String note, int which) throws Exception {
    if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("That file is empty");
    if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("File is too large (max 16 MB)");
    String clean = fileTitle(name);
    if (clean.length() == 0) clean = "file";
    int kind = which == 3 ? 3 : 1;
    String label = note == null ? "" : note.replace('\n', ' ').replace('\r', ' ').trim();
    for (int i = 0; i < library.size(); i++) {
      LibraryFile have = library.get(i);
      if (have.which == kind && have.name != null && have.name.equalsIgnoreCase(clean)) {
        have.bytes = copy(bytes);
        have.note = label;
        save();
        return have.id;
      }
    }
    LibraryFile file = new LibraryFile();
    file.id = nextId++;
    file.name = clean;
    file.bytes = copy(bytes);
    file.note = label;
    file.which = kind;
    library.add(file);
    save();
    return file.id;
  }

  private void addLibrary(List<StoredFile> out, LibraryFile image) {
    if (image == null || image.bytes == null || image.bytes.length == 0) return;
    String name = image.name == null || image.name.length() == 0 ? "frame.jpg" : image.name;
    String title = image.note == null || image.note.length() == 0 ? "Frame" : image.note;
    StoredFile row = new StoredFile();
    row.versionId = image.id;
    row.which = image.which == 2 || image.which == 3 ? image.which : 1;
    row.name = name;
    row.promptTitle = title;
    row.size = image.bytes.length;
    out.add(row);
  }

  private LibraryFile libraryFile(long id) {
    for (int i = 0; i < library.size(); i++) {
      if (library.get(i).id == id) return library.get(i);
    }
    return null;
  }

  private static final class LibraryFile {
    long id;
    String name;
    byte[] bytes;
    String note;
    int which;
  }

  private String titleOf(long promptId) {
    Prompt prompt = prompt(promptId);
    if (prompt == null || prompt.title == null || prompt.title.length() == 0) return "Prompt";
    return prompt.title;
  }

  public Prompt prompt(long id) {
    for (int i = 0; i < prompts.size(); i++) {
      if (prompts.get(i).id == id) return prompts.get(i);
    }
    return null;
  }

  public Category category(long id) {
    for (int i = 0; i < categories.size(); i++) {
      if (categories.get(i).id == id) return categories.get(i);
    }
    return null;
  }

  public long addCategory(String name) throws Exception {
    String clean = clean(name);
    if (clean.length() == 0) throw new IllegalArgumentException("Name the category");
    Category category = new Category();
    category.id = nextId++;
    category.name = clean;
    category.parentId = 0;
    categories.add(category);
    save();
    return category.id;
  }

  public long addSubcategory(long parentId, String name) throws Exception {
    long main = mainOf(parentId);
    if (main == 0) throw new IllegalArgumentException("Pick a category");
    String clean = clean(name);
    if (clean.length() == 0) throw new IllegalArgumentException("Name the subcategory");
    List<Category> kids = children(main);
    for (int i = 0; i < kids.size(); i++) {
      if (clean.equalsIgnoreCase(kids.get(i).name)) throw new IllegalArgumentException("That subcategory already exists");
    }
    Category category = new Category();
    category.id = nextId++;
    category.parentId = main;
    category.name = clean;
    categories.add(category);
    save();
    return category.id;
  }

  public long addPrompt(long categoryId, String title) throws Exception {
    if (category(categoryId) == null) throw new IllegalArgumentException("Pick a category");
    String clean = clean(title);
    if (clean.length() == 0) throw new IllegalArgumentException("Name the prompt");
    Prompt prompt = new Prompt();
    prompt.id = nextId++;
    prompt.categoryId = categoryId;
    prompt.title = clean;
    prompt.created = System.currentTimeMillis();
    prompts.add(prompt);
    save();
    return prompt.id;
  }

  public void setCategory(long promptId, long categoryId) throws Exception {
    Prompt prompt = prompt(promptId);
    if (prompt == null || category(categoryId) == null) return;
    prompt.categoryId = categoryId;
    save();
  }

  public List<Version> versions(long promptId) {
    List<Version> out = new ArrayList<Version>();
    for (int i = versions.size() - 1; i >= 0; i--) {
      if (versions.get(i).promptId == promptId) out.add(versions.get(i));
    }
    return out;
  }

  public Version version(long id) {
    for (int i = 0; i < versions.size(); i++) {
      if (versions.get(i).id == id) return versions.get(i);
    }
    return null;
  }

  public Version finalVersion(long promptId) {
    Version latest = null;
    for (int i = 0; i < versions.size(); i++) {
      Version version = versions.get(i);
      if (version.promptId != promptId) continue;
      if (version.finalVersion) return version;
      latest = version;
    }
    return latest;
  }

  /** Final version of the named prompt, or the version whose reference names match. */
  public Version selectedRefs(String title, String category, String ref1Name, String ref2Name) {
    String wantTitle = title == null ? "" : title.trim();
    String wantCat = category == null ? "" : category.trim();
    String n1 = ref1Name == null ? "" : ref1Name.trim();
    String n2 = ref2Name == null ? "" : ref2Name.trim();
    Prompt match = null;
    for (int i = 0; i < prompts.size(); i++) {
      Prompt prompt = prompts.get(i);
      String have = prompt.title == null ? "" : prompt.title.trim();
      if (wantTitle.length() > 0 && !wantTitle.equalsIgnoreCase(have)) continue;
      if (wantCat.length() > 0 && !categoryMatches(prompt, wantCat)) continue;
      match = prompt;
      break;
    }
    if (match == null && (n1.length() > 0 || n2.length() > 0)) {
      for (int i = versions.size() - 1; i >= 0; i--) {
        Version version = versions.get(i);
        if (n1.length() > 0 && n1.equals(version.ref1Name)) return version;
        if (n2.length() > 0 && n2.equals(version.ref2Name)) return version;
      }
    }
    if (match == null) return null;
    if (n1.length() > 0 || n2.length() > 0) {
      List<Version> rows = versions(match.id);
      for (int i = 0; i < rows.size(); i++) {
        Version version = rows.get(i);
        boolean ok1 = n1.length() == 0 || n1.equals(version.ref1Name);
        boolean ok2 = n2.length() == 0 || n2.equals(version.ref2Name);
        boolean has = version.ref1 != null && version.ref1.length > 0 || version.ref2 != null && version.ref2.length > 0;
        if (ok1 && ok2 && has) return version;
      }
    }
    return finalVersion(match.id);
  }

  private boolean categoryMatches(Prompt prompt, String want) {
    Category cat = category(prompt.categoryId);
    if (cat == null || cat.name == null) return false;
    if (want.equalsIgnoreCase(cat.name.trim())) return true;
    Category parent = category(cat.parentId);
    return parent != null && parent.name != null && want.equalsIgnoreCase(parent.name.trim());
  }

  public long addVersion(long promptId, String title, String description, String body, String model, String ref1Name, byte[] ref1, String ref2Name, byte[] ref2, String resultName, byte[] result, String codeType, String resultText) throws Exception {
    Prompt prompt = prompt(promptId);
    if (prompt == null) throw new IllegalArgumentException("That prompt is gone");
    String clean = clean(title);
    if (clean.length() == 0) throw new IllegalArgumentException("Name the prompt");
    if (ref1 != null && ref1.length > MAX_BYTES) throw new IllegalArgumentException("Reference file 1 is too large (max 16 MB)");
    if (ref2 != null && ref2.length > MAX_BYTES) throw new IllegalArgumentException("Reference file 2 is too large (max 16 MB)");
    if (result != null && result.length > MAX_BYTES) throw new IllegalArgumentException("Result file is too large (max 16 MB)");
    prompt.title = clean;
    Version version = new Version();
    version.id = nextId++;
    version.promptId = promptId;
    version.description = description == null ? "" : description;
    version.body = body == null ? "" : body;
    version.ref1Name = ref1Name == null ? "" : ref1Name;
    version.ref1 = copy(ref1);
    version.ref2Name = ref2Name == null ? "" : ref2Name;
    version.ref2 = copy(ref2);
    version.resultName = resultName == null ? "" : resultName;
    version.result = copy(result);
    version.model = model == null ? "" : model.replace('\n', ' ').replace('\r', ' ').trim();
    version.codeType = PromptRun.normalizeType(codeType);
    version.resultText = resultText == null ? "" : resultText;
    version.created = System.currentTimeMillis();
    version.finalVersion = versions(promptId).isEmpty();
    versions.add(version);
    save();
    return version.id;
  }

  public void putCodeType(long versionId, String codeType) throws Exception {
    Version version = version(versionId);
    if (version == null) return;
    version.codeType = PromptRun.normalizeType(codeType);
    save();
  }

  /** Stores pasted result text on the open version without starting another one. */
  public void putResultText(long versionId, String text) throws Exception {
    Version version = version(versionId);
    if (version == null) throw new IllegalArgumentException("Open a prompt first");
    String body = text == null ? "" : text;
    if (body.length() > 1000000) throw new IllegalArgumentException("Result text is too large");
    version.resultText = body;
    save();
  }

  /** Write one attached file into the encrypted database without starting another version. */
  public void putFile(long versionId, int which, String name, byte[] bytes) throws Exception {
    Version version = version(versionId);
    if (version == null) throw new IllegalArgumentException("Open a prompt first");
    if (bytes != null && bytes.length > MAX_BYTES) throw new IllegalArgumentException("File is too large (max 16 MB)");
    byte[] stored = copy(bytes);
    String clean = name == null ? "" : name;
    if (which == 1) {
      version.ref1Name = clean;
      version.ref1 = stored;
    } else if (which == 2) {
      version.ref2Name = clean;
      version.ref2 = stored;
    } else {
      version.resultName = clean;
      version.result = stored;
    }
    save();
  }

  private static byte[] copy(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return new byte[0];
    byte[] out = new byte[bytes.length];
    System.arraycopy(bytes, 0, out, 0, bytes.length);
    return out;
  }

  public void renameCategory(long id, String name) throws Exception {
    Category category = category(id);
    if (category == null) throw new IllegalArgumentException("That category is gone");
    String clean = clean(name);
    if (clean.length() == 0) throw new IllegalArgumentException(category.parentId == 0 ? "Name the category" : "Name the subcategory");
    List<Category> peers = category.parentId == 0 ? mains() : children(category.parentId);
    for (int i = 0; i < peers.size(); i++) {
      Category peer = peers.get(i);
      if (peer.id != id && peer.name != null && clean.equalsIgnoreCase(peer.name)) {
        throw new IllegalArgumentException(category.parentId == 0 ? "That category already exists" : "That subcategory already exists");
      }
    }
    category.name = clean;
    save();
  }

  public void deleteCategory(long id) throws Exception {
    Category category = category(id);
    if (category == null) return;
    if (category.parentId == 0 && mains().size() <= 1) throw new IllegalArgumentException("Keep at least one category");
    List<Long> drop = new ArrayList<Long>();
    drop.add(Long.valueOf(id));
    if (category.parentId == 0) {
      List<Category> kids = children(id);
      for (int i = 0; i < kids.size(); i++) drop.add(Long.valueOf(kids.get(i).id));
    }
    for (int i = categories.size() - 1; i >= 0; i--) {
      if (drop.contains(Long.valueOf(categories.get(i).id))) categories.remove(i);
    }
    List<Long> gone = new ArrayList<Long>();
    for (int i = prompts.size() - 1; i >= 0; i--) {
      if (drop.contains(Long.valueOf(prompts.get(i).categoryId))) {
        gone.add(Long.valueOf(prompts.get(i).id));
        prompts.remove(i);
      }
    }
    for (int i = versions.size() - 1; i >= 0; i--) {
      if (gone.contains(Long.valueOf(versions.get(i).promptId))) versions.remove(i);
    }
    save();
  }

  public void renamePrompt(long id, String title) throws Exception {
    Prompt prompt = prompt(id);
    if (prompt == null) throw new IllegalArgumentException("That prompt is gone");
    String clean = clean(title);
    if (clean.length() == 0) throw new IllegalArgumentException("Name the prompt");
    prompt.title = clean;
    save();
  }

  public void deletePrompt(long id) throws Exception {
    for (int i = prompts.size() - 1; i >= 0; i--) {
      if (prompts.get(i).id == id) prompts.remove(i);
    }
    for (int i = versions.size() - 1; i >= 0; i--) {
      if (versions.get(i).promptId == id) versions.remove(i);
    }
    save();
  }

  public String renameFile(long versionId, int which, String name) throws Exception {
    String clean = fileTitle(name);
    if (clean.length() == 0) throw new IllegalArgumentException("Name the file");
    Version version = version(versionId);
    if (version == null) {
      LibraryFile image = libraryFile(versionId);
      if (image == null) throw new IllegalArgumentException("That file is gone");
      image.name = clean;
      save();
      return clean;
    }
    if (which == 1) version.ref1Name = clean;
    else if (which == 2) version.ref2Name = clean;
    else version.resultName = clean;
    save();
    return clean;
  }

  public void deleteFile(long versionId, int which) throws Exception {
    Version version = version(versionId);
    if (version == null) {
      for (int i = library.size() - 1; i >= 0; i--) {
        if (library.get(i).id == versionId) library.remove(i);
      }
      save();
      return;
    }
    if (which == 1) {
      version.ref1Name = "";
      version.ref1 = new byte[0];
    } else if (which == 2) {
      version.ref2Name = "";
      version.ref2 = new byte[0];
    } else {
      version.resultName = "";
      version.result = new byte[0];
    }
    save();
  }

  public static String fileTitle(String name) {
    String clean = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ').trim();
    int slash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
    if (slash >= 0) clean = clean.substring(slash + 1).trim();
    return clean;
  }

  public void markFinal(long versionId) throws Exception {
    Version chosen = version(versionId);
    if (chosen == null) return;
    for (int i = 0; i < versions.size(); i++) {
      Version version = versions.get(i);
      if (version.promptId == chosen.promptId) version.finalVersion = version.id == versionId;
    }
    save();
  }

  private Version blank(long promptId) {
    Version version = new Version();
    version.id = nextId++;
    version.promptId = promptId;
    version.description = "";
    version.body = "";
    version.ref1Name = "";
    version.ref1 = new byte[0];
    version.ref2Name = "";
    version.ref2 = new byte[0];
    version.resultName = "";
    version.result = new byte[0];
    version.model = "";
    version.codeType = "";
    version.resultText = "";
    version.created = System.currentTimeMillis();
    return version;
  }

  private void seed() throws Exception {
    String[] names = {"General", "Writing", "Code", "Music", "Image", "video"};
    for (int i = 0; i < names.length; i++) {
      Category category = new Category();
      category.id = nextId++;
      category.name = names[i];
      categories.add(category);
    }
    save();
  }

  private void ensure(String name) throws Exception {
    for (int i = 0; i < categories.size(); i++) {
      String current = categories.get(i).name;
      if (current != null && current.equalsIgnoreCase(name)) return;
    }
    Category category = new Category();
    category.id = nextId++;
    category.name = name;
    categories.add(category);
    save();
  }

  private void save() throws Exception {
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(plain);
    out.writeInt(MAGIC);
    out.writeInt(6);
    out.writeInt(categories.size());
    for (int i = 0; i < categories.size(); i++) {
      Category category = categories.get(i);
      out.writeLong(category.id);
      writeUtf(out, category.name);
      out.writeLong(category.parentId);
    }
    out.writeInt(prompts.size());
    for (int i = 0; i < prompts.size(); i++) {
      Prompt prompt = prompts.get(i);
      out.writeLong(prompt.id);
      out.writeLong(prompt.categoryId);
      writeUtf(out, prompt.title);
      out.writeLong(prompt.created);
    }
    out.writeInt(versions.size());
    for (int i = 0; i < versions.size(); i++) {
      Version version = versions.get(i);
      out.writeLong(version.id);
      out.writeLong(version.promptId);
      writeUtf(out, version.description);
      writeUtf(out, version.body);
      writeUtf(out, version.ref1Name);
      writeBytes(out, version.ref1);
      writeUtf(out, version.ref2Name);
      writeBytes(out, version.ref2);
      out.writeLong(version.created);
      out.writeBoolean(version.finalVersion);
      writeUtf(out, version.model);
      writeUtf(out, version.resultName);
      writeBytes(out, version.result);
      writeUtf(out, version.codeType);
      writeUtf(out, version.resultText);
    }
    out.writeLong(nextId);
    if (!library.isEmpty()) {
      out.writeInt(library.size());
      for (int i = 0; i < library.size(); i++) {
        LibraryFile image = library.get(i);
        out.writeLong(image.id);
        writeUtf(out, image.name);
        writeUtf(out, image.note);
        out.writeInt(image.which);
        writeBytes(out, image.bytes);
      }
    }
    out.flush();
    byte[] cipher = encrypt(plain.toByteArray());
    File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
    FileOutputStream fos = new FileOutputStream(tmp);
    try {
      fos.write(cipher);
    } finally {
      fos.close();
    }
    if (file.exists() && !file.delete()) throw new IllegalStateException("Could not replace the prompt database");
    if (!tmp.renameTo(file)) throw new IllegalStateException("Could not store the prompt database");
    stamp = file.lastModified();
  }

  private void read() throws Exception {
    FileInputStream in = new FileInputStream(file);
    byte[] raw;
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[65536];
      int n;
      while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
      raw = bos.toByteArray();
    } finally {
      in.close();
    }
    stamp = file.lastModified();
    byte[] plain = decrypt(raw);
    DataInputStream data = new DataInputStream(new ByteArrayInputStream(plain));
    if (data.readInt() != MAGIC) throw new IllegalStateException("Prompt database is damaged");
    int version = data.readInt();
    if (version != 1 && version != 2 && version != 3 && version != 4 && version != 5 && version != 6) throw new IllegalStateException("Prompt database is from a newer app");
    int ncat = data.readInt();
    for (int i = 0; i < ncat; i++) {
      Category category = new Category();
      category.id = data.readLong();
      category.name = readUtf(data);
      category.parentId = version >= 4 ? data.readLong() : 0;
      categories.add(category);
    }
    int np = data.readInt();
    for (int i = 0; i < np; i++) {
      Prompt prompt = new Prompt();
      prompt.id = data.readLong();
      prompt.categoryId = data.readLong();
      prompt.title = readUtf(data);
      prompt.created = data.readLong();
      prompts.add(prompt);
    }
    int nv = data.readInt();
    for (int i = 0; i < nv; i++) {
      Version row = new Version();
      row.id = data.readLong();
      row.promptId = data.readLong();
      row.description = readUtf(data);
      row.body = readUtf(data);
      row.ref1Name = readUtf(data);
      row.ref1 = readBytes(data);
      row.ref2Name = readUtf(data);
      row.ref2 = readBytes(data);
      row.created = data.readLong();
      row.finalVersion = data.readBoolean();
      row.model = version >= 2 ? readUtf(data) : "";
      if (version >= 3) {
        row.resultName = readUtf(data);
        row.result = readBytes(data);
      } else {
        row.resultName = "";
        row.result = new byte[0];
      }
      row.codeType = version >= 5 ? PromptRun.normalizeType(readUtf(data)) : "";
      row.resultText = version >= 6 ? readUtf(data) : "";
      versions.add(row);
    }
    nextId = data.readLong();
    if (nextId < 1) nextId = 1;
    if (data.available() > 0) {
      int nlib = data.readInt();
      for (int i = 0; i < nlib; i++) {
        LibraryFile image = new LibraryFile();
        image.id = data.readLong();
        image.name = readUtf(data);
        image.note = readUtf(data);
        image.which = data.readInt();
        image.bytes = readBytes(data);
        if (image.bytes != null && image.bytes.length > 0) library.add(image);
      }
    }
  }

  private static byte[] encrypt(byte[] plain) throws Exception {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    byte[] iv = cipher.getIV();
    byte[] body = cipher.doFinal(plain);
    ByteArrayOutputStream out = new ByteArrayOutputStream(FILE_MAGIC.length + iv.length + body.length);
    out.write(FILE_MAGIC);
    out.write(iv);
    out.write(body);
    return out.toByteArray();
  }

  private static byte[] decrypt(byte[] raw) throws Exception {
    if (raw.length < FILE_MAGIC.length + 12 + 16) throw new IllegalStateException("Prompt database is damaged");
    for (int i = 0; i < FILE_MAGIC.length; i++) {
      if (raw[i] != FILE_MAGIC[i]) throw new IllegalStateException("Prompt database is damaged");
    }
    byte[] iv = new byte[12];
    System.arraycopy(raw, FILE_MAGIC.length, iv, 0, 12);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
    return cipher.doFinal(raw, FILE_MAGIC.length + 12, raw.length - FILE_MAGIC.length - 12);
  }

  private static SecretKey key() throws Exception {
    Keys k = keys;
    if (k == null) throw new IllegalStateException("The prompt library has no key");
    return k.key();
  }

  private static void writeUtf(DataOutputStream out, String text) throws Exception {
    byte[] bytes = (text == null ? "" : text).getBytes("UTF-8");
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  private static String readUtf(DataInputStream in) throws Exception {
    int n = in.readInt();
    if (n < 0 || n > 8 * 1024 * 1024) throw new IllegalStateException("Prompt database is damaged");
    byte[] bytes = new byte[n];
    in.readFully(bytes);
    return new String(bytes, "UTF-8");
  }

  private static void writeBytes(DataOutputStream out, byte[] bytes) throws Exception {
    if (bytes == null) bytes = new byte[0];
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  private static byte[] readBytes(DataInputStream in) throws Exception {
    int n = in.readInt();
    if (n < 0 || n > MAX_BYTES) throw new IllegalStateException("Prompt database is damaged");
    byte[] bytes = new byte[n];
    in.readFully(bytes);
    return bytes;
  }

  private static String clean(String text) {
    if (text == null) return "";
    return text.replace('\n', ' ').replace('\r', ' ').trim();
  }
}
