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
    /**
     * A large file (BLOB_MIN or more) is kept in its own encrypted file and read only when asked
     * for (PromptVault.fileBytes): its name and size here, and its byte[] field above is empty.
     */
    String ref1Blob, ref2Blob, resultBlob;
    int ref1Size, ref2Size, resultSize;
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
  private static volatile PromptVault shared;
  /** The file's time when this copy last read or wrote it; 0 before. */
  private long stamp;

  private PromptVault(File file) {
    this.file = file;
  }

  /**
   * The library in `dir` when it is already in memory and up to date, else null: open() would have
   * to read and decrypt the file, slow for a large library (videos kept in it). Never waits.
   */
  public static PromptVault ready(File dir) {
    PromptVault v = shared;
    File at = new File(dir, "prompts.vault");
    if (v == null || !v.file.getAbsolutePath().equals(at.getAbsolutePath())) return null;
    if (at.isFile() ? at.lastModified() != v.stamp : v.stamp != 0) return null;
    return v;
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
    begin("Opening the encrypted database");
    try {
      if (file.isFile()) read();
      if (categories.isEmpty()) seed();
      ensure("Image");
      ensure("video");
    } finally {
      end();
    }
  }

  private static final java.util.concurrent.atomic.AtomicInteger BUSY = new java.util.concurrent.atomic.AtomicInteger();
  private static volatile long busySince;
  private static volatile String busyWhat = "";

  private static void begin(String what) {
    if (BUSY.getAndIncrement() == 0) {
      busyWhat = what;
      busySince = System.currentTimeMillis();
    }
  }

  private static void end() {
    if (BUSY.decrementAndGet() <= 0) {
      BUSY.set(0);
      busySince = 0;
    }
  }

  /**
   * What the library is doing for longer than `ms` milliseconds ("Saving the encrypted database"),
   * or null: the apps show "Processing DB, please wait" while it lasts. Never waits.
   */
  public static String busy(long ms) {
    long since = busySince;
    if (since == 0 || System.currentTimeMillis() - since < ms) return null;
    return busyWhat;
  }

  /** Reads the file again into this same library (it changed on disk). */
  private synchronized void reload() throws Exception {
    lastBlob = null;
    lastBlobBytes = null;
    categories.clear();
    prompts.clear();
    versions.clear();
    library.clear();
    nextId = 1;
    stamp = 0;
    load();
  }

  public synchronized List<Category> categories() {
    return new ArrayList<Category>(categories);
  }

  public synchronized List<Category> mains() {
    List<Category> out = new ArrayList<Category>();
    for (int i = 0; i < categories.size(); i++) {
      if (categories.get(i).parentId == 0) out.add(categories.get(i));
    }
    return out;
  }

  public synchronized List<Category> children(long parentId) {
    List<Category> out = new ArrayList<Category>();
    for (int i = 0; i < categories.size(); i++) {
      if (categories.get(i).parentId == parentId) out.add(categories.get(i));
    }
    return out;
  }

  public synchronized long mainOf(long id) {
    Category category = category(id);
    if (category == null) return 0;
    if (category.parentId == 0) return category.id;
    Category parent = category(category.parentId);
    return parent == null ? category.id : parent.id;
  }

  public synchronized List<Prompt> prompts(long categoryId) {
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

  public synchronized List<StoredFile> referenceFiles() {
    List<StoredFile> out = new ArrayList<StoredFile>();
    for (int i = library.size() - 1; i >= 0; i--) if (library.get(i).which != 3) addLibrary(out, library.get(i));
    for (int i = versions.size() - 1; i >= 0; i--) {
      Version version = versions.get(i);
      addStored(out, version, 1);
      addStored(out, version, 2);
    }
    return out;
  }

  public synchronized List<StoredFile> resultFiles() {
    List<StoredFile> out = new ArrayList<StoredFile>();
    // Files imported as result files (File > Import as Result file) first, newest first.
    for (int i = library.size() - 1; i >= 0; i--) if (library.get(i).which == 3) addLibrary(out, library.get(i));
    for (int i = versions.size() - 1; i >= 0; i--) addStored(out, versions.get(i), 3);
    return out;
  }

  public synchronized byte[] fileBytes(long versionId, int which) {
    Version version = version(versionId);
    if (version != null) return bytesOf(version, which);
    LibraryFile image = libraryFile(versionId);
    if (image == null) return new byte[0];
    if (image.blob != null) return blobBytes(image.blob);
    return copy(image.bytes);
  }

  /** The version's file (1 reference file 1, 2 reference file 2, 3 result), a copy; empty when it has none. */
  public synchronized byte[] bytesOf(Version version, int which) {
    if (version == null) return new byte[0];
    String blob = which == 1 ? version.ref1Blob : which == 2 ? version.ref2Blob : version.resultBlob;
    if (blob != null) return blobBytes(blob);
    return copy(which == 1 ? version.ref1 : which == 2 ? version.ref2 : version.result);
  }

  /** The size of the version's file in bytes, without reading it (0 when it has none). */
  public synchronized int sizeOf(Version version, int which) {
    if (version == null) return 0;
    String blob = which == 1 ? version.ref1Blob : which == 2 ? version.ref2Blob : version.resultBlob;
    if (blob != null) return which == 1 ? version.ref1Size : which == 2 ? version.ref2Size : version.resultSize;
    byte[] bytes = which == 1 ? version.ref1 : which == 2 ? version.ref2 : version.result;
    return bytes == null ? 0 : bytes.length;
  }

  private void addStored(List<StoredFile> out, Version version, int which) {
    int size = sizeOf(version, which);
    if (size == 0) return;
    String name = which == 1 ? version.ref1Name : which == 2 ? version.ref2Name : version.resultName;
    if (name == null || name.length() == 0) name = "file";
    String title = titleOf(version.promptId);
    for (int i = 0; i < out.size(); i++) {
      StoredFile have = out.get(i);
      if (have.which == which && have.size == size && name.equals(have.name) && title.equals(have.promptTitle)) return;
    }
    StoredFile row = new StoredFile();
    row.versionId = version.id;
    row.which = which;
    row.name = name;
    row.promptTitle = title;
    row.size = size;
    out.add(row);
  }

  /** A still image kept in the encrypted database so it can be picked as a reference file. */
  public synchronized long addReferenceImage(String name, byte[] bytes, String note, int which) throws Exception {
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
        have.blob = null;
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
  public synchronized long addLibraryFile(String name, byte[] bytes, String note, int which) throws Exception {
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
        have.blob = null;
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
    if (image == null || image.size() == 0) return;
    String name = image.name == null || image.name.length() == 0 ? "frame.jpg" : image.name;
    String title = image.note == null || image.note.length() == 0 ? "Frame" : image.note;
    StoredFile row = new StoredFile();
    row.versionId = image.id;
    row.which = image.which == 2 || image.which == 3 ? image.which : 1;
    row.name = name;
    row.promptTitle = title;
    row.size = image.size();
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
    /** Kept in its own file (see Version.ref1Blob): its name and size, and bytes is empty. */
    String blob;
    int blobSize;

    int size() {
      return blob != null ? blobSize : bytes == null ? 0 : bytes.length;
    }
  }

  private String titleOf(long promptId) {
    Prompt prompt = prompt(promptId);
    if (prompt == null || prompt.title == null || prompt.title.length() == 0) return "Prompt";
    return prompt.title;
  }

  public synchronized Prompt prompt(long id) {
    for (int i = 0; i < prompts.size(); i++) {
      if (prompts.get(i).id == id) return prompts.get(i);
    }
    return null;
  }

  public synchronized Category category(long id) {
    for (int i = 0; i < categories.size(); i++) {
      if (categories.get(i).id == id) return categories.get(i);
    }
    return null;
  }

  public synchronized long addCategory(String name) throws Exception {
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

  public synchronized long addSubcategory(long parentId, String name) throws Exception {
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

  public synchronized long addPrompt(long categoryId, String title) throws Exception {
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

  public synchronized void setCategory(long promptId, long categoryId) throws Exception {
    Prompt prompt = prompt(promptId);
    if (prompt == null || category(categoryId) == null) return;
    prompt.categoryId = categoryId;
    save();
  }

  public synchronized List<Version> versions(long promptId) {
    List<Version> out = new ArrayList<Version>();
    for (int i = versions.size() - 1; i >= 0; i--) {
      if (versions.get(i).promptId == promptId) out.add(versions.get(i));
    }
    return out;
  }

  public synchronized Version version(long id) {
    for (int i = 0; i < versions.size(); i++) {
      if (versions.get(i).id == id) return versions.get(i);
    }
    return null;
  }

  public synchronized Version finalVersion(long promptId) {
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
  public synchronized Version selectedRefs(String title, String category, String ref1Name, String ref2Name) {
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
        boolean has = sizeOf(version, 1) > 0 || sizeOf(version, 2) > 0;
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

  public synchronized long addVersion(long promptId, String title, String description, String body, String model, String ref1Name, byte[] ref1, String ref2Name, byte[] ref2, String resultName, byte[] result, String codeType, String resultText) throws Exception {
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

  public synchronized void putCodeType(long versionId, String codeType) throws Exception {
    Version version = version(versionId);
    if (version == null) return;
    version.codeType = PromptRun.normalizeType(codeType);
    save();
  }

  /** Stores pasted result text on the open version without starting another one. */
  public synchronized void putResultText(long versionId, String text) throws Exception {
    Version version = version(versionId);
    if (version == null) throw new IllegalArgumentException("Open a prompt first");
    String body = text == null ? "" : text;
    if (body.length() > 1000000) throw new IllegalArgumentException("Result text is too large");
    version.resultText = body;
    save();
  }

  /** Write one attached file into the encrypted database without starting another version. */
  public synchronized void putFile(long versionId, int which, String name, byte[] bytes) throws Exception {
    Version version = version(versionId);
    if (version == null) throw new IllegalArgumentException("Open a prompt first");
    if (bytes != null && bytes.length > MAX_BYTES) throw new IllegalArgumentException("File is too large (max 16 MB)");
    byte[] stored = copy(bytes);
    String clean = name == null ? "" : name;
    if (which == 1) {
      version.ref1Name = clean;
      version.ref1 = stored;
      version.ref1Blob = null;
    } else if (which == 2) {
      version.ref2Name = clean;
      version.ref2 = stored;
      version.ref2Blob = null;
    } else {
      version.resultName = clean;
      version.result = stored;
      version.resultBlob = null;
    }
    save();
  }

  private static byte[] copy(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return new byte[0];
    byte[] out = new byte[bytes.length];
    System.arraycopy(bytes, 0, out, 0, bytes.length);
    return out;
  }

  public synchronized void renameCategory(long id, String name) throws Exception {
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

  public synchronized void deleteCategory(long id) throws Exception {
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

  public synchronized void renamePrompt(long id, String title) throws Exception {
    Prompt prompt = prompt(id);
    if (prompt == null) throw new IllegalArgumentException("That prompt is gone");
    String clean = clean(title);
    if (clean.length() == 0) throw new IllegalArgumentException("Name the prompt");
    prompt.title = clean;
    save();
  }

  public synchronized void deletePrompt(long id) throws Exception {
    for (int i = prompts.size() - 1; i >= 0; i--) {
      if (prompts.get(i).id == id) prompts.remove(i);
    }
    for (int i = versions.size() - 1; i >= 0; i--) {
      if (versions.get(i).promptId == id) versions.remove(i);
    }
    save();
  }

  public synchronized String renameFile(long versionId, int which, String name) throws Exception {
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

  public synchronized void deleteFile(long versionId, int which) throws Exception {
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
      version.ref1Blob = null;
    } else if (which == 2) {
      version.ref2Name = "";
      version.ref2 = new byte[0];
      version.ref2Blob = null;
    } else {
      version.resultName = "";
      version.result = new byte[0];
      version.resultBlob = null;
    }
    save();
  }

  public static String fileTitle(String name) {
    String clean = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ').trim();
    int slash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
    if (slash >= 0) clean = clean.substring(slash + 1).trim();
    return clean;
  }

  public synchronized void markFinal(long versionId) throws Exception {
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

  private synchronized void save() throws Exception {
    begin("Saving the encrypted database");
    try {
      write();
    } finally {
      end();
    }
  }

  private void write() throws Exception {
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(plain);
    out.writeInt(MAGIC);
    out.writeInt(7);
    blobsUsed.clear();
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
      if (version.ref1Blob == null && version.ref1 != null && version.ref1.length >= BLOB_MIN) {
        version.ref1Size = version.ref1.length;
        version.ref1Blob = keepBlob(version.ref1);
        version.ref1 = new byte[0];
      }
      writeBytes(out, version.ref1, version.ref1Blob);
      writeUtf(out, version.ref2Name);
      if (version.ref2Blob == null && version.ref2 != null && version.ref2.length >= BLOB_MIN) {
        version.ref2Size = version.ref2.length;
        version.ref2Blob = keepBlob(version.ref2);
        version.ref2 = new byte[0];
      }
      writeBytes(out, version.ref2, version.ref2Blob);
      out.writeLong(version.created);
      out.writeBoolean(version.finalVersion);
      writeUtf(out, version.model);
      writeUtf(out, version.resultName);
      if (version.resultBlob == null && version.result != null && version.result.length >= BLOB_MIN) {
        version.resultSize = version.result.length;
        version.resultBlob = keepBlob(version.result);
        version.result = new byte[0];
      }
      writeBytes(out, version.result, version.resultBlob);
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
        if (image.blob == null && image.bytes != null && image.bytes.length >= BLOB_MIN) {
          image.blobSize = image.bytes.length;
          image.blob = keepBlob(image.bytes);
          image.bytes = new byte[0];
        }
        writeBytes(out, image.bytes, image.blob);
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
    // Kept files no version or library file uses any more (deleted, replaced) go too.
    File[] kids = file.getParentFile() == null ? null : file.getParentFile().listFiles();
    if (kids != null) {
      for (File k : kids) {
        String n = k.getName();
        if (n.startsWith(BLOB_PREFIX) && n.endsWith(".dat") && !blobsUsed.contains(n)) k.delete();
      }
    }
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
    if (version < 1 || version > 7) throw new IllegalStateException("Prompt database is from a newer app");
    fileVersion = version;
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
      if (readBlob != null) {
        row.ref1Blob = readBlob;
        row.ref1Size = readSize;
      }
      row.ref2Name = readUtf(data);
      row.ref2 = readBytes(data);
      if (readBlob != null) {
        row.ref2Blob = readBlob;
        row.ref2Size = readSize;
      }
      row.created = data.readLong();
      row.finalVersion = data.readBoolean();
      row.model = version >= 2 ? readUtf(data) : "";
      if (version >= 3) {
        row.resultName = readUtf(data);
        row.result = readBytes(data);
        if (readBlob != null) {
          row.resultBlob = readBlob;
          row.resultSize = readSize;
        }
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
        if (readBlob != null) {
          image.blob = readBlob;
          image.blobSize = readSize;
        }
        if (image.size() > 0) library.add(image);
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

  /**
   * A file of BLOB_MIN bytes or more (a picture, a video) is kept in its own encrypted file beside
   * the library (prompts-blob-<hash>.dat), named by its contents, and the library holds only its
   * name: a save then encrypts the small index and any new file, not every stored video again
   * (which took half a minute with a large library), and opening reads only the index: a kept file
   * is read and decrypted when it is asked for, so the library does not hold every video in memory
   * (which ran the phone out of memory). Smaller files stay inside.
   */
  private void writeBytes(DataOutputStream out, byte[] bytes, String blob) throws Exception {
    if (blob != null) {
      blobsUsed.add(blob);
      out.writeInt(-1);
      writeUtf(out, blob);
      return;
    }
    if (bytes == null) bytes = new byte[0];
    out.writeInt(bytes.length);
    out.write(bytes);
  }

  /** Writes `bytes` to its kept file (unless one with the same contents is there) and returns its name. */
  private String keepBlob(byte[] bytes) throws Exception {
    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
    byte[] d = md.digest(bytes);
    StringBuilder sb = new StringBuilder(BLOB_PREFIX);
    for (int i = 0; i < 12; i++) sb.append(String.format("%02x", d[i] & 0xff));
    String name = sb.append(".dat").toString();
    File blob = new File(file.getParentFile(), name);
    if (!blob.isFile()) {
      File tmp = new File(file.getParentFile(), name + ".tmp");
      FileOutputStream fos = new FileOutputStream(tmp);
      try {
        fos.write(encrypt(bytes));
      } finally {
        fos.close();
      }
      if (!tmp.renameTo(blob)) throw new IllegalStateException("Could not store " + name);
    }
    return name;
  }

  /** Set by readBytes: the kept file's name and size when the entry is one (bytes then empty), else null. */
  private String readBlob;
  private int readSize;

  private byte[] readBytes(DataInputStream in) throws Exception {
    readBlob = null;
    readSize = 0;
    int n = in.readInt();
    if (n == -1) {
      String name = readUtf(in);
      if (!name.startsWith(BLOB_PREFIX) || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) throw new IllegalStateException("Prompt database is damaged");
      File blob = new File(file.getParentFile(), name);
      if (!blob.isFile()) return new byte[0];
      // The kept file is the magic, the 12-byte IV, the file and the 16-byte tag.
      long size = blob.length() - FILE_MAGIC.length - 12 - 16;
      if (size <= 0) return new byte[0];
      readBlob = name;
      readSize = (int) Math.min(Integer.MAX_VALUE, size);
      return new byte[0];
    }
    if (n < 0 || n > MAX_BYTES) throw new IllegalStateException("Prompt database is damaged");
    byte[] bytes = new byte[n];
    in.readFully(bytes);
    return bytes;
  }

  /** The kept file read and decrypted (empty when it is gone or cannot be read). The last one read is kept for the next ask. */
  private byte[] blobBytes(String name) {
    if (name.equals(lastBlob) && lastBlobBytes != null) return copy(lastBlobBytes);
    File blob = new File(file.getParentFile(), name);
    if (!blob.isFile()) return new byte[0];
    begin("Reading a file from the encrypted database");
    try {
      byte[] raw = new byte[(int) blob.length()];
      DataInputStream fin = new DataInputStream(new FileInputStream(blob));
      try {
        fin.readFully(raw);
      } finally {
        fin.close();
      }
      byte[] bytes = decrypt(raw);
      lastBlob = name;
      lastBlobBytes = bytes;
      return copy(bytes);
    } catch (Exception ex) {
      return new byte[0];
    } finally {
      end();
    }
  }

  private String lastBlob;
  private byte[] lastBlobBytes;

  /** The version the library file was written in (7 keeps large files on their own). */
  private int fileVersion = 7;

  /**
   * A library written by an older app keeps its large files inside: saved once in the new form
   * (slow, as every save was before), so later saves are quick. The app calls it off the main
   * thread, after opening the library at start. True when it saved.
   */
  public synchronized boolean upgrade() throws Exception {
    if (fileVersion >= 7 || !file.isFile()) return false;
    save();
    fileVersion = 7;
    return true;
  }

  /** Files this large or larger are kept in their own encrypted files (see writeBytes). */
  static final int BLOB_MIN = 64 * 1024;
  static final String BLOB_PREFIX = "prompts-blob-";
  /** The files the last save named. */
  private final java.util.HashSet<String> blobsUsed = new java.util.HashSet<String>();

  /** The library's size on disk in `dir`, its kept files included (bytes). */
  public static long storedSize(File dir) {
    long total = 0;
    File[] kids = dir == null ? null : dir.listFiles();
    if (kids == null) return 0;
    for (File k : kids) {
      String n = k.getName();
      if (n.equals("prompts.vault") || (n.startsWith(BLOB_PREFIX) && n.endsWith(".dat"))) total += k.length();
    }
    return total;
  }

  private static String clean(String text) {
    if (text == null) return "";
    return text.replace('\n', ' ').replace('\r', ' ').trim();
  }
}
