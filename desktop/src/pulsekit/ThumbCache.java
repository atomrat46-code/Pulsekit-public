package pulsekit;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Video previews for the desktop galleries, made once by the browser (Make video previews) and
 * kept in ~/.pulsekit/thumbs: one file per video, named by a hash of the video's bytes and
 * encrypted with the prompt library's key (AES-GCM), so a preview is as private as the library.
 */
final class ThumbCache {
    private ThumbCache() {}

    static File dir() {
        return new File(PromptDb.dir(), "thumbs");
    }

    /** The video's name in the cache: SHA-256 of its bytes. */
    static String key(byte[] video) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(video);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** The preview (a JPEG) of this video, or null when there is none yet or it cannot be read. */
    static byte[] get(byte[] video) {
        if (video == null || video.length == 0) return null;
        File f = new File(dir(), key(video) + ".thumb");
        if (!f.isFile()) return null;
        try {
            byte[] raw = Files.readAllBytes(f.toPath());
            if (raw.length < 13) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, PromptVault.keys.key(), new GCMParameterSpec(128, raw, 0, 12));
            return cipher.doFinal(raw, 12, raw.length - 12);
        } catch (Exception ex) {
            return null;
        }
    }

    static boolean has(byte[] video) {
        return video != null && video.length > 0 && new File(dir(), key(video) + ".thumb").isFile();
    }

    static void put(byte[] video, byte[] jpeg) throws Exception {
        File d = dir();
        if (!d.isDirectory()) d.mkdirs();
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, PromptVault.keys.key(), new GCMParameterSpec(128, iv));
        byte[] sealed = cipher.doFinal(jpeg);
        byte[] out = new byte[12 + sealed.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(sealed, 0, out, 12, sealed.length);
        File f = new File(d, key(video) + ".thumb");
        File tmp = new File(d, f.getName() + ".tmp");
        Files.write(tmp.toPath(), out);
        if (!tmp.renameTo(f)) {
            Files.write(f.toPath(), out);
            tmp.delete();
        }
    }
}
