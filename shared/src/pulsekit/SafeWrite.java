package pulsekit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * A list file written whole or not at all: into "<name>.tmp" first, then moved over the file. Written
 * in place, a file cut off when Pulsekit is closed mid-write (Android closing it, a crash) lost what
 * it kept: the Sogni runs' workflow ids (Params' --workflow list), the Downloads list.
 */
public final class SafeWrite {
  private SafeWrite() {}

  public static void write(File f, byte[] data) throws IOException {
    File dir = f.getAbsoluteFile().getParentFile();
    if (dir != null && !dir.isDirectory()) dir.mkdirs();
    File tmp = new File(dir, f.getName() + ".tmp");
    FileOutputStream out = new FileOutputStream(tmp);
    try {
      out.write(data);
      out.getFD().sync();
    } finally {
      out.close();
    }
    try {
      Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
