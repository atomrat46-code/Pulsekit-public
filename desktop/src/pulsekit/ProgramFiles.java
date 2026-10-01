package pulsekit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The bundled Programs folder (Java, Python, Code) for PyJav's menus.
 * In the JAR it is /Programs with an index.txt written by desktop/build.sh,
 * because a JAR cannot list a folder. Outside a JAR, a Programs/ folder on disk is used.
 */
final class ProgramFiles {
    private ProgramFiles() {}

    static String[] list(String kind) {
        List<String> out = new ArrayList<>();
        String index = resourceText("/Programs/index.txt");
        if (index != null) {
            for (String line : index.split("\n")) {
                String path = line.trim();
                if (path.startsWith(kind + "/") && path.indexOf('/', kind.length() + 1) < 0) {
                    out.add(path.substring(kind.length() + 1));
                }
            }
        } else {
            File dir = folder(kind);
            String[] names = dir == null ? null : dir.list();
            if (names != null) {
                for (String n : names) {
                    if (new File(dir, n).isFile()) out.add(n);
                }
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out.toArray(new String[0]);
    }

    static byte[] read(String kind, String name) throws Exception {
        try (InputStream in = ProgramFiles.class.getResourceAsStream("/Programs/" + kind + "/" + name)) {
            if (in != null) return readAll(in);
        }
        File dir = folder(kind);
        if (dir == null) throw new java.io.FileNotFoundException("Programs/" + kind + "/" + name);
        return Files.readAllBytes(new File(dir, name).toPath());
    }

    /** Programs/KIND next to the working directory or one level up (running from desktop/). */
    private static File folder(String kind) {
        String cwd = System.getProperty("user.dir", ".");
        for (File base : new File[] {new File(cwd, "Programs"), new File(cwd, "../Programs")}) {
            File dir = new File(base, kind);
            if (dir.isDirectory()) return dir;
        }
        return null;
    }

    private static String resourceText(String path) {
        try (InputStream in = ProgramFiles.class.getResourceAsStream(path)) {
            return in == null ? null : new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
