package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small private files kept beside the prompt library (the Media browser's playlists and its
 * remembered folder), sealed as the library is: AES-GCM with the library's key (the Android
 * keystore on the phone, ~/.pulsekit's key file on the desktop). A sealed file starts with "PKS1",
 * then the 12-byte IV, then the ciphertext. Names that must not tell a folder's path are a hash of
 * it with a random salt that is itself sealed (sealed.salt), as the phone's key cannot be read out.
 */
public final class Sealed {
  private static final byte[] MAGIC = {'P', 'K', 'S', '1'};
  private static final Map<String, byte[]> SALTS = new HashMap<String, byte[]>();

  private Sealed() {}

  public static boolean isSealed(byte[] raw) {
    if (raw == null || raw.length < MAGIC.length + 12 + 16) return false;
    for (int i = 0; i < MAGIC.length; i++) if (raw[i] != MAGIC[i]) return false;
    return true;
  }

  /** The bytes sealed with the library's key; throws when there is no key. */
  public static byte[] seal(byte[] plain) throws Exception {
    PromptVault.Keys k = PromptVault.keys;
    if (k == null) throw new IllegalStateException("No key for the prompt library");
    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, k.key(), new GCMParameterSpec(128, iv));
    byte[] body = cipher.doFinal(plain);
    byte[] out = new byte[MAGIC.length + 12 + body.length];
    System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
    System.arraycopy(iv, 0, out, MAGIC.length, 12);
    System.arraycopy(body, 0, out, MAGIC.length + 12, body.length);
    return out;
  }

  /** seal()'s bytes opened; throws when they are not sealed or not with this key. */
  public static byte[] open(byte[] raw) throws Exception {
    if (!isSealed(raw)) throw new IllegalStateException("Not a sealed file");
    PromptVault.Keys k = PromptVault.keys;
    if (k == null) throw new IllegalStateException("No key for the prompt library");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, k.key(), new GCMParameterSpec(128, raw, MAGIC.length, 12));
    return cipher.doFinal(raw, MAGIC.length + 12, raw.length - MAGIC.length - 12);
  }

  /** Text sealed, as hex (for a settings line). */
  public static String sealText(String text) throws Exception {
    byte[] b = seal(text.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) sb.append(String.format("%02x", x & 0xff));
    return sb.toString();
  }

  /** sealText()'s hex opened. */
  public static String openText(String hex) throws Exception {
    if (hex == null || hex.length() % 2 != 0) throw new IllegalStateException("Not sealed text");
    byte[] b = new byte[hex.length() / 2];
    for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
    return new String(open(b), StandardCharsets.UTF_8);
  }

  /** A name for `text` that does not tell it: 16 hex digits of SHA-256 over the folder's salt and the text. */
  public static String name(File dir, String text) throws Exception {
    byte[] salt = salt(dir);
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update(salt);
    byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i] & 0xff));
    return sb.toString();
  }

  /** The random salt for names in `dir`, made (and sealed) the first time. */
  static synchronized byte[] salt(File dir) throws Exception {
    String at = dir.getAbsolutePath();
    File f = new File(dir, "sealed.salt");
    byte[] have = SALTS.get(at);
    if (have != null && f.isFile()) return have;
    if (f.isFile()) {
      have = open(Files.readAllBytes(f.toPath()));
    } else {
      have = new byte[16];
      new SecureRandom().nextBytes(have);
      if (!dir.isDirectory()) dir.mkdirs();
      Files.write(f.toPath(), seal(have));
    }
    SALTS.put(at, have);
    return have;
  }

  /** Forgets the salts read (the tests start over with a new folder). */
  static synchronized void forget() {
    SALTS.clear();
  }
}
