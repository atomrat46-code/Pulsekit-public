package pulsekit;

import java.io.File;
import java.nio.file.Files;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * The prompt library's key on the desktop: 32 random bytes in ~/.pulsekit/prompts.key, readable and
 * writable only by the user, made on first use. The library file can't be read without it (a
 * copy, a backup); anyone logged in as the user can, as with most desktop apps.
 */
final class DesktopVaultKey implements PromptVault.Keys {
  private final File file;
  private SecretKey cached;

  DesktopVaultKey(File dir) {
    this.file = new File(dir, "prompts.key");
  }

  @Override
  public synchronized SecretKey key() throws Exception {
    if (this.cached != null) return this.cached;
    byte[] raw;
    if (this.file.isFile()) {
      raw = Files.readAllBytes(this.file.toPath());
      if (raw.length != 32) throw new IllegalStateException("The prompt library key " + this.file + " is damaged");
    } else {
      raw = new byte[32];
      new java.security.SecureRandom().nextBytes(raw);
      File dir = this.file.getParentFile();
      if (dir != null && !dir.isDirectory()) dir.mkdirs();
      File tmp = new File(dir, this.file.getName() + ".tmp");
      Files.write(tmp.toPath(), raw);
      // Only the user may read or write it.
      tmp.setReadable(false, false);
      tmp.setWritable(false, false);
      tmp.setExecutable(false, false);
      tmp.setReadable(true, true);
      tmp.setWritable(true, true);
      if (!tmp.renameTo(this.file)) throw new IllegalStateException("Could not store the prompt library key");
    }
    this.cached = new SecretKeySpec(raw, "AES");
    return this.cached;
  }
}
