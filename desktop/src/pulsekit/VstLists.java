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

    /**
     * The kit names in the run's arguments (--kit_name, --kit_name2 to --kit_name4), kept for the list; and
     * each with its plugin (--plugin with --kit_name, --plugin2 with --kit_name2...), for kitsFor.
     */
    static void rememberKits(String args) {
        List<String> words = JavaRun.split(args == null ? "" : args);
        java.util.Map<String, String> plugins = new java.util.HashMap<String, String>(), kits = new java.util.HashMap<String, String>();
        for (int i = 0; i + 1 < words.size(); i++) {
            String w = words.get(i);
            if (w.equals("--kit_name") || w.equals("--kit_name2") || w.equals("--kit_name3") || w.equals("--kit_name4")) {
                remember(KITS_KEY, words.get(i + 1));
                kits.put(w.substring("--kit_name".length()), words.get(i + 1));
            }
            if (w.matches("--plugin\\d?")) plugins.put(w.substring("--plugin".length()), words.get(i + 1));
        }
        for (java.util.Map.Entry<String, String> e : kits.entrySet()) {
            String plugin = plugins.get(e.getKey());
            if (plugin != null && pluginKey(plugin).length() > 0) remember(PLUGIN_KITS_KEY, pluginKey(plugin) + "\t" + e.getValue().trim(), PLUGIN_KITS_KEEP);
        }
    }

    static final String PLUGIN_KITS_KEY = "vstPluginKits";
    /** At most this many plugin and kit pairs are kept, the last used first. */
    static final int PLUGIN_KITS_KEEP = 100;
    /** The names vst_render gives a kit not named (not kits of their own to pick). */
    static final java.util.Set<String> NOT_KITS = new java.util.HashSet<String>(java.util.Arrays.asList("saved kit", "default kit", "parts"));

    /** A plugin's key: its file's name without the extension, letters and digits only, lower case ("ugritonedrums"). */
    static String pluginKey(String path) {
        String n = new File(path == null ? "" : path.replace('\\', '/')).getName();
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * The kits named for a plugin before, the last used first: the runs' (--kit_name with its plugin), then
     * vst_render's own list (~/.pulsekit/kit-names.txt: each kit name and the plugin it was last given for).
     * "saved kit" and "default kit" left out (vst_render names a kit so when none is given).
     */
    static List<String> kitsFor(String plugin) {
        String key = pluginKey(plugin);
        List<String> out = new ArrayList<String>();
        if (key.length() == 0) return out;
        for (String line : kept(PLUGIN_KITS_KEY)) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || !line.substring(0, tab).equals(key)) continue;
            String kit = line.substring(tab + 1).trim();
            if (kit.length() > 0 && !NOT_KITS.contains(kit.toLowerCase(java.util.Locale.ROOT)) && !out.contains(kit)) out.add(kit);
        }
        File names = new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "kit-names.txt");
        if (names.isFile()) {
            try {
                for (String line : new String(Files.readAllBytes(names.toPath()), "UTF-8").split("\n")) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0 || !pluginKey(line.substring(tab + 1).trim()).equals(key)) continue;
                    String kit = line.substring(0, tab).trim();
                    if (!NOT_KITS.contains(kit.toLowerCase(java.util.Locale.ROOT)) && !out.contains(kit)) out.add(kit);
                }
            } catch (Exception ignored) {
                // the runs' list only
            }
        }
        return out;
    }

    /** The run's plugins (--plugin to --plugin4, --effect): first among the recently used (Params' Plugins tab). */
    static void rememberPlugins(String args) {
        PluginLibrary.load(PromptDb.dir());
        PluginLibrary.used(JavaRun.split(args == null ? "" : args));
    }

    static List<String> kept(String key) {
        String all = java.util.prefs.Preferences.userNodeForPackage(PyJav.class).get(key, "");
        List<String> out = new ArrayList<String>();
        for (String s : all.split("\n")) if (s.trim().length() > 0) out.add(s.trim());
        return out;
    }

    static void remember(String key, String value) {
        remember(key, value, KEEP);
    }

    static void remember(String key, String value, int keep) {
        if (value == null || value.trim().isEmpty()) return;
        List<String> now = kept(key);
        now.remove(value.trim());
        now.add(0, value.trim());
        while (now.size() > keep) now.remove(now.size() - 1);
        java.util.prefs.Preferences.userNodeForPackage(PyJav.class).put(key, String.join("\n", now));
    }

    private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
