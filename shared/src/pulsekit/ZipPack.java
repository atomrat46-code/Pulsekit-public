package pulsekit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Send to zip and File > Import's zip packages: a zip written with its files deflated, and with a
 * password (recommended) each file encrypted as WinZip's AES-256 (AE-2), which 7-Zip, WinZip and
 * the systems' own zip tools open. Read back: stored, deflated, AES (128/192/256) and the old
 * ZipCrypto. Nothing is read whole into memory; a name with ".." or a leading / is made safe.
 */
public final class ZipPack {
  private ZipPack() {}

  public static final String SEND = "Send to zip";
  public static final String PASSWORD_TITLE = "Password for the zip";
  public static final String PASSWORD_NOTE = "Set a password for the archive (recommended): its files are encrypted (AES-256). "
      + "Leave it empty for a zip without a password.";
  public static final String WITH_PASSWORD = "Zip with password";
  public static final String NO_PASSWORD = "No password";
  public static final String ASK_PASSWORD = "This zip is protected with a password: type it to open it.";
  public static final String WRONG_PASSWORD = "Wrong password for the zip";

  /** A file to put in the zip: its path inside it ("Pulse/beach.png") and where its bytes come from. */
  public static final class Source {
    public final String path;
    final File file;
    final byte[] data;
    final Opener opener;
    final long time;

    public Source(String path, File file) {
      this.path = path;
      this.file = file;
      this.data = null;
      this.opener = null;
      this.time = file.lastModified();
    }

    public Source(String path, byte[] data) {
      this.path = path;
      this.file = null;
      this.data = data;
      this.opener = null;
      this.time = System.currentTimeMillis();
    }

    public Source(String path, Opener opener, long time) {
      this.path = path;
      this.file = null;
      this.data = null;
      this.opener = opener;
      this.time = time <= 0 ? System.currentTimeMillis() : time;
    }

    InputStream open() throws IOException {
      // Its bytes counted as read, when the work is watched (WorkProgress).
      if (this.file != null) return WorkProgress.counted(new FileInputStream(this.file));
      if (this.data != null) return WorkProgress.counted(new java.io.ByteArrayInputStream(this.data));
      return WorkProgress.counted(this.opener.open());
    }
  }

  /** Where a file's bytes come from when it is neither a File nor in memory (a content:// address). */
  public interface Opener {
    InputStream open() throws IOException;
  }

  /** A folder's files, and its folders' (hidden ones left out), under the folder's own name. */
  public static List<Source> folder(File dir) {
    List<Source> out = new ArrayList<Source>();
    add(out, dir, dir.getName(), 0);
    return out;
  }

  private static void add(List<Source> out, File dir, String prefix, int depth) {
    File[] kids = dir.listFiles();
    if (kids == null || depth > 16) return;
    java.util.Arrays.sort(kids);
    for (File f : kids) {
      if (f.getName().startsWith(".")) continue;
      if (f.isDirectory()) add(out, f, prefix + "/" + f.getName(), depth + 1);
      else if (f.isFile()) out.add(new Source(prefix + "/" + f.getName(), f));
    }
  }

  /** "beach.zip" for beach.png or the folder Beach ("Beach.zip"). */
  public static String zipName(String name, boolean folder) {
    String n = name == null || name.trim().length() == 0 ? "files" : name.trim();
    if (!folder) n = n.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    return n + ".zip";
  }

  // ------------------------------------------------------------------ Writing

  /** The zip of `sources` written to `out`; with a password (not empty) each file is AES-256 encrypted. */
  public static void write(File out, List<Source> sources, char[] password) throws IOException {
    boolean aes = password != null && password.length > 0;
    byte[] pw = aes ? new String(password).getBytes(StandardCharsets.UTF_8) : null;
    SecureRandom random = new SecureRandom();
    List<long[]> sizes = new ArrayList<long[]>();
    List<byte[]> names = new ArrayList<byte[]>();
    List<Integer> times = new ArrayList<Integer>();
    CountingOut os = new CountingOut(new java.io.BufferedOutputStream(new FileOutputStream(out)));
    try {
      List<String> used = new ArrayList<String>();
      for (Source s : sources) {
        String path = safePath(s.path);
        if (path.length() == 0) continue;
        // Two files of one name: the second "name (2).ext".
        String unique = path;
        for (int n = 2; used.contains(unique.toLowerCase(Locale.ROOT)); n++) {
          String stem = path.replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
          unique = stem + " (" + n + ")" + path.substring(stem.length());
        }
        used.add(unique.toLowerCase(Locale.ROOT));
        byte[] name = unique.getBytes(StandardCharsets.UTF_8);
        int dos = dosTime(s.time);
        long offset = os.count;
        // Local header; sizes and CRC follow the data (a data descriptor).
        writeInt(os, 0x04034b50);
        writeShort(os, aes ? 51 : 20);
        writeShort(os, (aes ? 1 : 0) | 8 | 0x800);
        writeShort(os, aes ? 99 : 8);
        writeInt(os, dos);
        writeInt(os, 0);
        writeInt(os, 0);
        writeInt(os, 0);
        writeShort(os, name.length);
        writeShort(os, aes ? 11 : 0);
        os.write(name);
        if (aes) aesExtra(os);
        long start = os.count;
        long[] crcAndSize = aes ? writeAes(os, s, pw, random) : writeDeflated(os, s);
        long compressed = os.count - start;
        // Data descriptor.
        writeInt(os, 0x08074b50);
        writeInt(os, (int) crcAndSize[0]);
        writeInt(os, (int) compressed);
        writeInt(os, (int) crcAndSize[1]);
        sizes.add(new long[] {crcAndSize[0], compressed, crcAndSize[1], offset});
        names.add(name);
        times.add(dos);
        if (os.count > 0xFFFFFFFFL - 1024) throw new IOException("the zip would be 4 GB or more");
      }
      long central = os.count;
      for (int i = 0; i < names.size(); i++) {
        long[] z = sizes.get(i);
        writeInt(os, 0x02014b50);
        writeShort(os, (3 << 8) | 51);
        writeShort(os, aes ? 51 : 20);
        writeShort(os, (aes ? 1 : 0) | 8 | 0x800);
        writeShort(os, aes ? 99 : 8);
        writeInt(os, times.get(i));
        writeInt(os, (int) z[0]);
        writeInt(os, (int) z[1]);
        writeInt(os, (int) z[2]);
        writeShort(os, names.get(i).length);
        writeShort(os, aes ? 11 : 0);
        writeShort(os, 0);
        writeShort(os, 0);
        writeShort(os, 0);
        writeInt(os, 0644 << 16);
        writeInt(os, (int) z[3]);
        os.write(names.get(i));
        if (aes) aesExtra(os);
      }
      long centralSize = os.count - central;
      writeInt(os, 0x06054b50);
      writeShort(os, 0);
      writeShort(os, 0);
      writeShort(os, names.size());
      writeShort(os, names.size());
      writeInt(os, (int) centralSize);
      writeInt(os, (int) central);
      writeShort(os, 0);
    } finally {
      os.close();
    }
  }

  /** The AES extra field: AE-2, "AE", 256 bits, deflated. */
  private static void aesExtra(OutputStream os) throws IOException {
    writeShort(os, 0x9901);
    writeShort(os, 7);
    writeShort(os, 2);
    os.write('A');
    os.write('E');
    os.write(3);
    writeShort(os, 8);
  }

  /** The file deflated into `os`: {crc, size}. */
  private static long[] writeDeflated(OutputStream os, Source s) throws IOException {
    CRC32 crc = new CRC32();
    long size = 0;
    Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    byte[] buf = new byte[65536];
    byte[] outBuf = new byte[65536];
    InputStream in = s.open();
    try {
      int n;
      while ((n = in.read(buf)) > 0) {
        crc.update(buf, 0, n);
        size += n;
        def.setInput(buf, 0, n);
        while (!def.needsInput()) {
          int m = def.deflate(outBuf);
          if (m > 0) os.write(outBuf, 0, m);
        }
      }
      def.finish();
      while (!def.finished()) {
        int m = def.deflate(outBuf);
        if (m > 0) os.write(outBuf, 0, m);
      }
    } finally {
      in.close();
      def.end();
    }
    return new long[] {crc.getValue(), size};
  }

  /** The file deflated and encrypted (salt, password check, data, authentication code) into `os`: {0 (AE-2 has no CRC), size}. */
  private static long[] writeAes(OutputStream os, Source s, byte[] pw, SecureRandom random) throws IOException {
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    byte[] keys = pbkdf2(pw, salt, 1000, 66);
    Aes aes = new Aes(keys, 32);
    os.write(salt);
    os.write(keys, 64, 2);
    long size = 0;
    Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    byte[] buf = new byte[65536];
    byte[] outBuf = new byte[65536];
    InputStream in = s.open();
    try {
      int n;
      while ((n = in.read(buf)) > 0) {
        size += n;
        def.setInput(buf, 0, n);
        while (!def.needsInput()) {
          int m = def.deflate(outBuf);
          if (m > 0) os.write(aes.encrypt(outBuf, m), 0, m);
        }
      }
      def.finish();
      while (!def.finished()) {
        int m = def.deflate(outBuf);
        if (m > 0) os.write(aes.encrypt(outBuf, m), 0, m);
      }
    } finally {
      in.close();
      def.end();
    }
    os.write(aes.mac(), 0, 10);
    return new long[] {0, size};
  }

  /** AES in WinZip's counter mode (a little-endian counter from 1), with HMAC-SHA1 over what it encrypted. */
  private static final class Aes {
    final Cipher cipher;
    final Mac mac;
    final byte[] counter = new byte[16];
    final byte[] stream = new byte[16];
    int used = 16;

    Aes(byte[] keys, int keyLength) throws IOException {
      try {
        this.cipher = Cipher.getInstance("AES/ECB/NoPadding");
        this.cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys, 0, keyLength, "AES"));
        this.mac = Mac.getInstance("HmacSHA1");
        this.mac.init(new SecretKeySpec(keys, keyLength, keyLength, "HmacSHA1"));
      } catch (Exception ex) {
        throw new IOException("AES is not available: " + ex.getMessage());
      }
    }

    private void crypt(byte[] b, int n) throws IOException {
      for (int i = 0; i < n; i++) {
        if (this.used == 16) {
          for (int k = 0; k < 16; k++) if (++this.counter[k] != 0) break;
          try {
            this.cipher.doFinal(this.counter, 0, 16, this.stream, 0);
          } catch (Exception ex) {
            throw new IOException(ex.getMessage());
          }
          this.used = 0;
        }
        b[i] ^= this.stream[this.used++];
      }
    }

    /** `n` bytes of `b` encrypted in place (and counted for the authentication code); `b`. */
    byte[] encrypt(byte[] b, int n) throws IOException {
      this.crypt(b, n);
      this.mac.update(b, 0, n);
      return b;
    }

    byte[] decrypt(byte[] b, int n) throws IOException {
      this.mac.update(b, 0, n);
      this.crypt(b, n);
      return b;
    }

    byte[] mac() {
      return this.mac.doFinal();
    }
  }

  /** PBKDF2 with HMAC-SHA1 over the password's UTF-8 bytes (as WinZip and 7-Zip have it). */
  static byte[] pbkdf2(byte[] pw, byte[] salt, int rounds, int length) throws IOException {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(pw.length == 0 ? new byte[1] : pw, "HmacSHA1"));
      byte[] out = new byte[length];
      int at = 0;
      for (int block = 1; at < length; block++) {
        mac.update(salt);
        mac.update(new byte[] {(byte) (block >>> 24), (byte) (block >>> 16), (byte) (block >>> 8), (byte) block});
        byte[] u = mac.doFinal();
        byte[] t = u.clone();
        for (int r = 1; r < rounds; r++) {
          u = mac.doFinal(u);
          for (int k = 0; k < t.length; k++) t[k] ^= u[k];
        }
        int take = Math.min(t.length, length - at);
        System.arraycopy(t, 0, out, at, take);
        at += take;
      }
      return out;
    } catch (Exception ex) {
      throw new IOException("HMAC-SHA1 is not available: " + ex.getMessage());
    }
  }

  // ------------------------------------------------------------------ Reading

  /** A file in a zip, from its central directory. */
  public static final class Entry {
    public String name = "";
    public long size;
    public long compressed;
    public int method;
    public int flags;
    public long crc;
    public int time;
    long offset;
    /** AES: 1, 2, 3 for 128, 192, 256 bits (0 not AES), and the method under it. */
    int aesStrength;
    int aesMethod;

    public boolean folder() {
      return this.name.endsWith("/");
    }

    public boolean encrypted() {
      return (this.flags & 1) != 0;
    }

    /** Its name with no folders. */
    public String fileName() {
      String n = this.folder() ? this.name.substring(0, this.name.length() - 1) : this.name;
      return n.substring(n.lastIndexOf('/') + 1);
    }
  }

  public static final class WrongPassword extends IOException {
    WrongPassword() {
      super(WRONG_PASSWORD);
    }
  }

  /** Whether a file is a zip by its first bytes ("PK\3\4", or an empty zip's "PK\5\6"). */
  public static boolean isZip(File f) {
    try (InputStream in = new FileInputStream(f)) {
      byte[] b = new byte[4];
      return in.read(b) == 4 && b[0] == 'P' && b[1] == 'K' && ((b[2] == 3 && b[3] == 4) || (b[2] == 5 && b[3] == 6));
    } catch (IOException ex) {
      return false;
    }
  }

  /** The files (and folders) a zip has, from its central directory. */
  public static List<Entry> list(File zip) throws IOException {
    List<Entry> out = new ArrayList<Entry>();
    try (RandomAccessFile raf = new RandomAccessFile(zip, "r")) {
      long len = raf.length();
      int back = (int) Math.min(len, 65557);
      byte[] tail = new byte[back];
      raf.seek(len - back);
      raf.readFully(tail);
      int eocd = -1;
      for (int i = back - 22; i >= 0; i--) {
        if (tail[i] == 'P' && tail[i + 1] == 'K' && tail[i + 2] == 5 && tail[i + 3] == 6) {
          eocd = i;
          break;
        }
      }
      if (eocd < 0) throw new IOException("not a zip file");
      int count = u16(tail, eocd + 10);
      long cdSize = u32(tail, eocd + 12);
      long cdAt = u32(tail, eocd + 16);
      if (count == 0xFFFF || cdAt == 0xFFFFFFFFL) throw new IOException("a zip of 4 GB or more (ZIP64) is not read");
      byte[] cd = new byte[(int) cdSize];
      raf.seek(cdAt);
      raf.readFully(cd);
      int p = 0;
      for (int i = 0; i < count && p + 46 <= cd.length; i++) {
        if (u32(cd, p) != 0x02014b50L) break;
        Entry e = new Entry();
        e.flags = u16(cd, p + 8);
        e.method = u16(cd, p + 10);
        e.time = (int) u32(cd, p + 12);
        e.crc = u32(cd, p + 16);
        e.compressed = u32(cd, p + 20);
        e.size = u32(cd, p + 24);
        int nameLen = u16(cd, p + 28);
        int extraLen = u16(cd, p + 30);
        int commentLen = u16(cd, p + 32);
        e.offset = u32(cd, p + 42);
        e.name = new String(cd, p + 46, nameLen, (e.flags & 0x800) != 0 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1).replace('\\', '/');
        int x = p + 46 + nameLen;
        int end = x + extraLen;
        while (x + 4 <= end) {
          int id = u16(cd, x);
          int sz = u16(cd, x + 2);
          if (id == 0x9901 && sz >= 7) {
            e.aesStrength = cd[x + 8] & 0xff;
            e.aesMethod = u16(cd, x + 9);
          }
          x += 4 + sz;
        }
        out.add(e);
        p += 46 + nameLen + extraLen + commentLen;
      }
    }
    return out;
  }

  /** Whether any file in it is encrypted (a password is asked). */
  public static boolean encrypted(List<Entry> entries) {
    for (Entry e : entries) if (e.encrypted()) return true;
    return false;
  }

  /** The files only (no folders), each with its path made safe. */
  public static List<Entry> files(List<Entry> entries) {
    List<Entry> out = new ArrayList<Entry>();
    for (Entry e : entries) if (!e.folder() && safePath(e.name).length() > 0) out.add(e);
    return out;
  }

  /** Whether the password opens the zip (its first encrypted file is checked). */
  public static boolean passwordOk(File zip, List<Entry> entries, char[] password) {
    for (Entry e : entries) {
      if (!e.encrypted() || e.folder()) continue;
      try {
        extract(zip, e, password, new OutputStream() {
          @Override
          public void write(int b) {}

          @Override
          public void write(byte[] b, int off, int len) {}
        });
        return true;
      } catch (IOException ex) {
        return false;
      }
    }
    return true;
  }

  /** One file's bytes written to `out` (decrypted with `password` when it is encrypted). */
  public static void extract(File zip, Entry e, char[] password, OutputStream out) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(zip, "r")) {
      raf.seek(e.offset);
      byte[] head = new byte[30];
      raf.readFully(head);
      if (u32(head, 0) != 0x04034b50L) throw new IOException("a damaged zip (" + e.name + ")");
      raf.seek(e.offset + 30 + u16(head, 26) + u16(head, 28));
      InputStream raw = new Limited(raf, e.compressed);
      int method = e.method;
      if (e.encrypted()) {
        if (password == null || password.length == 0) throw new WrongPassword();
        byte[] pw = new String(password).getBytes(StandardCharsets.UTF_8);
        if (method == 99) {
          int keyLength = e.aesStrength == 1 ? 16 : e.aesStrength == 2 ? 24 : 32;
          byte[] salt = new byte[keyLength / 2];
          readFully(raw, salt);
          byte[] check = new byte[2];
          readFully(raw, check);
          byte[] keys = pbkdf2(pw, salt, 1000, 2 * keyLength + 2);
          if (keys[2 * keyLength] != check[0] || keys[2 * keyLength + 1] != check[1]) throw new WrongPassword();
          long data = e.compressed - salt.length - 2 - 10;
          if (data < 0) throw new IOException("a damaged zip (" + e.name + ")");
          ByteArrayOutputStream plain = null;
          Aes aes = new Aes(keys, keyLength);
          // Decrypted as it is read, inflated after; the authentication code checked at the end.
          InputStream dec = new AesIn(new Limited(raw, data), aes);
          copyOut(e.aesMethod == 8 ? new java.util.zip.InflaterInputStream(dec, new Inflater(true), 65536) : dec, out);
          byte[] code = new byte[10];
          readFully(raw, code);
          byte[] mac = aes.mac();
          for (int i = 0; i < 10; i++) if (mac[i] != code[i]) throw new IOException("the zip's " + e.name + " is damaged (its check does not match)");
          return;
        }
        // The old ZipCrypto.
        raw = new ZipCryptoIn(raw, pw, e);
        method = e.method;
      }
      if (method == 0) copyOut(raw, out);
      else if (method == 8) copyOut(new java.util.zip.InflaterInputStream(raw, new Inflater(true), 65536), out);
      else throw new IOException(e.name + " is packed in a way that is not read (method " + method + ")");
    }
  }

  /** One file extracted to `to` (its folders made). */
  public static void extract(File zip, Entry e, char[] password, File to) throws IOException {
    if (to.getParentFile() != null) to.getParentFile().mkdirs();
    try (OutputStream os = new java.io.BufferedOutputStream(new FileOutputStream(to))) {
      extract(zip, e, password, os);
    }
  }

  /** A name inside a zip as a safe relative path: no leading /, no "..", no drive, no \\. */
  public static String safePath(String name) {
    if (name == null) return "";
    StringBuilder sb = new StringBuilder();
    for (String part : name.replace('\\', '/').split("/")) {
      String p = part.trim();
      if (p.length() == 0 || p.equals(".") || p.equals("..") || p.endsWith(":")) continue;
      p = p.replaceAll("[\\t\\r\\n:*?\"<>|]", "_");
      if (sb.length() > 0) sb.append('/');
      sb.append(p);
    }
    return sb.toString();
  }

  /** "4 files, 12 MB". */
  public static String summary(List<Entry> entries) {
    long total = 0;
    int n = 0;
    for (Entry e : entries) {
      if (e.folder()) continue;
      n++;
      total += e.size;
    }
    return n + (n == 1 ? " file, " : " files, ") + Playlist.sizeText(total);
  }

  // ------------------------------------------------------------------ Streams

  private static final class CountingOut extends java.io.FilterOutputStream {
    long count;

    CountingOut(OutputStream os) {
      super(os);
    }

    @Override
    public void write(int b) throws IOException {
      this.out.write(b);
      this.count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      this.out.write(b, off, len);
      this.count += len;
    }
  }

  /** At most `left` bytes of a RandomAccessFile or a stream. */
  private static final class Limited extends InputStream {
    final RandomAccessFile raf;
    final InputStream in;
    long left;

    Limited(RandomAccessFile raf, long left) {
      this.raf = raf;
      this.in = null;
      this.left = left;
    }

    Limited(InputStream in, long left) {
      this.raf = null;
      this.in = in;
      this.left = left;
    }

    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      return this.read(b, 0, 1) <= 0 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (this.left <= 0) return -1;
      int n = this.raf != null ? this.raf.read(b, off, (int) Math.min(len, this.left)) : this.in.read(b, off, (int) Math.min(len, this.left));
      if (n > 0) this.left -= n;
      return n;
    }
  }

  private static final class AesIn extends InputStream {
    final InputStream in;
    final Aes aes;

    AesIn(InputStream in, Aes aes) {
      this.in = in;
      this.aes = aes;
    }

    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      return this.read(b, 0, 1) <= 0 ? -1 : b[0] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int n = this.in.read(b, off, len);
      if (n <= 0) return n;
      byte[] part = off == 0 ? b : new byte[n];
      if (off != 0) System.arraycopy(b, off, part, 0, n);
      this.aes.decrypt(part, n);
      if (off != 0) System.arraycopy(part, 0, b, off, n);
      return n;
    }
  }

  /** The old ZipCrypto's stream: its 12-byte header checked (a wrong password said so), then the data. */
  private static final class ZipCryptoIn extends InputStream {
    final InputStream in;
    int k0 = 305419896, k1 = 591751049, k2 = 878082192;

    ZipCryptoIn(InputStream in, byte[] pw, Entry e) throws IOException {
      this.in = in;
      for (byte b : pw) this.update(b);
      byte[] head = new byte[12];
      readFully(in, head);
      for (int i = 0; i < 12; i++) head[i] = this.decrypt(head[i]);
      int check = (e.flags & 8) != 0 ? (e.time >>> 8) & 0xff : (int) ((e.crc >>> 24) & 0xff);
      if ((head[11] & 0xff) != check) throw new WrongPassword();
    }

    private static final int[] CRC_TABLE = new int[256];

    static {
      for (int n = 0; n < 256; n++) {
        int c = n;
        for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
        CRC_TABLE[n] = c;
      }
    }

    private static int crc(int crc, byte b) {
      return (crc >>> 8) ^ CRC_TABLE[(crc ^ b) & 0xff];
    }

    private void update(byte b) {
      this.k0 = crc(this.k0, b);
      this.k1 = this.k1 + (this.k0 & 0xff);
      this.k1 = this.k1 * 134775813 + 1;
      this.k2 = crc(this.k2, (byte) (this.k1 >>> 24));
    }

    private byte decrypt(byte c) {
      int t = (this.k2 & 0xffff) | 2;
      byte p = (byte) (c ^ ((t * (t ^ 1)) >>> 8));
      this.update(p);
      return p;
    }

    @Override
    public int read() throws IOException {
      int c = this.in.read();
      return c < 0 ? -1 : this.decrypt((byte) c) & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int n = this.in.read(b, off, len);
      for (int i = 0; i < n; i++) b[off + i] = this.decrypt(b[off + i]);
      return n;
    }
  }

  private static void copyOut(InputStream in, OutputStream out) throws IOException {
    byte[] buf = new byte[65536];
    int n;
    while ((n = in.read(buf)) > 0) {
      out.write(buf, 0, n);
      WorkProgress.count(n);
    }
  }

  private static void readFully(InputStream in, byte[] b) throws IOException {
    int at = 0;
    while (at < b.length) {
      int n = in.read(b, at, b.length - at);
      if (n <= 0) throw new IOException("a damaged zip (it ends too soon)");
      at += n;
    }
  }

  private static int dosTime(long ms) {
    java.util.Calendar c = java.util.Calendar.getInstance();
    c.setTimeInMillis(ms <= 0 ? System.currentTimeMillis() : ms);
    int year = Math.max(1980, c.get(java.util.Calendar.YEAR));
    return ((year - 1980) << 25) | ((c.get(java.util.Calendar.MONTH) + 1) << 21) | (c.get(java.util.Calendar.DAY_OF_MONTH) << 16)
        | (c.get(java.util.Calendar.HOUR_OF_DAY) << 11) | (c.get(java.util.Calendar.MINUTE) << 5) | (c.get(java.util.Calendar.SECOND) / 2);
  }

  private static void writeShort(OutputStream os, int v) throws IOException {
    os.write(v & 0xff);
    os.write((v >>> 8) & 0xff);
  }

  private static void writeInt(OutputStream os, int v) throws IOException {
    writeShort(os, v & 0xffff);
    writeShort(os, (v >>> 16) & 0xffff);
  }

  private static int u16(byte[] b, int at) {
    return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8);
  }

  private static long u32(byte[] b, int at) {
    return (u16(b, at) | ((long) u16(b, at + 2) << 16)) & 0xFFFFFFFFL;
  }
}
