package pulsekit;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * vst_render's lists in Params: its map files (Pulsekit's own, put in ~/.pulsekit/maps where
 * vst_render looks for a map's bare name, and the ones picked with Browse) and the kit names
 * given before (--kit_name: plugins do not say which kit is loaded).
 */
final class VstLists {
    private VstLists() {}

    static final String MAPS_KEY = "vstMapFiles", KITS_KEY = "vstKitNames";
    /** At most this many kit names and browsed maps are kept, the last used first. */
    static final int KEEP = 20;

    static File mapsDir() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "maps");
    }

    /** Pulsekit's map files (Programs/Python/maps in the JAR), written to mapsDir when missing or changed. */
    static void unpackMaps() {
        File dir = mapsDir();
        for (String name : bundledMaps()) {
            try {
                byte[] data = ProgramFiles.read("Python", "maps/" + name);
                File f = new File(dir, name);
                if (f.isFile() && java.util.Arrays.equals(Files.readAllBytes(f.toPath()), data)) continue;
                dir.mkdirs();
                Files.write(f.toPath(), data);
            } catch (Exception ignored) {
                // A map that cannot be written is left out of the list.
            }
        }
    }

    static List<String> bundledMaps() {
        List<String> out = new ArrayList<String>();
        try (java.io.InputStream in = VstLists.class.getResourceAsStream("/Programs/index.txt")) {
            if (in != null) {
                for (String line : new String(readAll(in), "UTF-8").split("\n")) {
                    String p = line.trim();
                    if (p.startsWith("Python/maps/") && p.toLowerCase().endsWith(".txt")) out.add(p.substring("Python/maps/".length()));
                }
                return out;
            }
        } catch (Exception ignored) {
            // Read from the folder below instead.
        }
        File dir = new File(new File(System.getProperty("user.dir", "."), "../Programs/Python"), "maps");
        if (!dir.isDirectory()) dir = new File(new File(System.getProperty("user.dir", "."), "Programs/Python"), "maps");
        String[] names = dir.list();
        if (names != null) for (String n : names) if (n.toLowerCase().endsWith(".txt")) out.add(n);
        return out;
    }

    /**
     * The --map choices: {labels, values}. The maps in mapsDir by their bare name (vst_render finds
     * them there), then the files picked with Browse by their path.
     */
    static String[][] mapChoices() {
        unpackMaps();
        List<String> labels = new ArrayList<String>(), values = new ArrayList<String>();
        Set<String> bundled = new LinkedHashSet<String>(bundledMaps());
        String[] here = mapsDir().list();
        if (here != null) {
            java.util.Arrays.sort(here, String.CASE_INSENSITIVE_ORDER);
            for (String n : here) {
                if (!n.toLowerCase().endsWith(".txt")) continue;
                labels.add(n + (bundled.contains(n) ? " (Pulsekit's)" : ""));
                values.add(n);
            }
        }
        for (String path : kept(MAPS_KEY)) {
            if (!new File(path).isFile()) continue;
            labels.add(new File(path).getName() + " — " + path);
            values.add(path);
        }
        return new String[][] {labels.toArray(new String[0]), values.toArray(new String[0])};
    }

    static void rememberMap(String path) {
        remember(MAPS_KEY, path);
    }

    static String[] kitNames() {
        return kept(KITS_KEY).toArray(new String[0]);
    }

    /** The kit names in the run's arguments (--kit_name, --kit_name2, --kit_name3), kept for the list. */
    static void rememberKits(String args) {
        List<String> words = JavaRun.split(args == null ? "" : args);
        for (int i = 0; i + 1 < words.size(); i++) {
            String w = words.get(i);
            if (w.equals("--kit_name") || w.equals("--kit_name2") || w.equals("--kit_name3")) remember(KITS_KEY, words.get(i + 1));
        }
    }

    static List<String> kept(String key) {
        String all = java.util.prefs.Preferences.userNodeForPackage(PyJav.class).get(key, "");
        List<String> out = new ArrayList<String>();
        for (String s : all.split("\n")) if (s.trim().length() > 0) out.add(s.trim());
        return out;
    }

    static void remember(String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        List<String> now = kept(key);
        now.remove(value.trim());
        now.add(0, value.trim());
        while (now.size() > KEEP) now.remove(now.size() - 1);
        java.util.prefs.Preferences.userNodeForPackage(PyJav.class).put(key, String.join("\n", now));
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
