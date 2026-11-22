package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * vst_render's sessions (Params: Save session, the sessions list): a name, a description and the date
 * saved, with the session's rows (TOKENS: the Input tab's folders, the Output tab's, the plugins and kit
 * names, Test and the benches' switches and lists, the benches' folders). Loading one puts its rows into
 * the Params; the others stay as they are. Kept in ~/.pulsekit/vst-sessions.txt, a line each:
 * name, description, date and the rows' arguments, tab-separated.
 *
 * Also the folder rows' lists (FOLDER_KEYS): every folder picked or used, the last first, no duplicates.
 */
final class VstSessions {
    private VstSessions() {}

    /** The rows a session keeps. */
    static final Set<String> TOKENS = new LinkedHashSet<String>(Arrays.asList(
        "--dir", "--dir2", "--recursive", "--outdir", "--zip_outdir",
        "--plugin", "--plugin2", "--plugin3", "--plugin4", "--kit_name", "--kit_name2", "--kit_name3", "--kit_name4",
        "--padset", "--parts", "--state", "--effect", "--effect_state",
        "--test", "--cymbal_bench", "--cymbal_bench_when", "--hit_bench", "--hit_bench_when",
        "--bench_dir", "--bench_unzip", "--bench_work", "--hit_dir", "--hit_unzip", "--hit_zip_outdir", "--hit_model_file",
        // Each session's own choices for its folders, plugins and runs: the kits (or runs) left out, the maps,
        // the model comparison and leave-one-out's kits, the kits counted as new.
        "--bench_skip", "--map", "--map2", "--map3", "--map4", "--note_map",
        "--model_compare", "--model_a", "--model_a_kits", "--model_b", "--model_b_kits", "--leave_one_out", "--loo_kits", "--loo_names",
        "--new_kits", "--new_runs", "--new_names", "--hit_loo"));

    /** The folder rows' lists: the two input folders share one, each other kind its own. */
    static final Map<String, String> FOLDER_KEYS = new LinkedHashMap<String, String>();
    static {
        FOLDER_KEYS.put("--dir", "vstInFolders");
        FOLDER_KEYS.put("--dir2", "vstInFolders");
        FOLDER_KEYS.put("--outdir", "vstOutFolders");
        FOLDER_KEYS.put("--zip_outdir", "vstZipFolders");
        FOLDER_KEYS.put("--hit_zip_outdir", "vstZipFolders");
        FOLDER_KEYS.put("--bench_dir", "vstBenchFolders");
        FOLDER_KEYS.put("--hit_dir", "vstBenchFolders");
        FOLDER_KEYS.put("--bench_unzip", "vstUnzipFolders");
        FOLDER_KEYS.put("--hit_unzip", "vstUnzipFolders");
        FOLDER_KEYS.put("--bench_work", "vstWorkFolders");
    }
    /** At most this many folders in each list. */
    static final int KEEP_FOLDERS = 30;
    static final String CURRENT_KEY = "vstSession";

    static final class Session {
        String name, description, date, args;

        Session(String name, String description, String date, String args) {
            this.name = clean(name);
            this.description = clean(description);
            this.date = clean(date);
            this.args = clean(args);
        }

        /** Its line in the sessions list: "name (date)". */
        String label() {
            return this.name + (this.date.isEmpty() ? "" : "  (" + this.date + ")");
        }
    }

    static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    static File file() {
        return new File(new File(System.getProperty("user.home", "."), ".pulsekit"), "vst-sessions.txt");
    }

    /** The saved sessions, the last saved first. */
    static List<Session> load() {
        List<Session> out = new ArrayList<Session>();
        File f = file();
        if (!f.isFile()) return out;
        try {
            for (String line : new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] w = line.split("\t", -1);
                if (w.length < 4 || w[0].trim().isEmpty()) continue;
                out.add(new Session(w[0], w[1], w[2], w[3]));
            }
        } catch (Exception ignored) {
            // none to list
        }
        return out;
    }

    static void save(List<Session> all) throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        for (Session s : all) b.append(s.name).append('\t').append(s.description).append('\t').append(s.date).append('\t').append(s.args).append('\n');
        File f = file();
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Session find(String name) {
        for (Session s : load()) if (s.name.equalsIgnoreCase(clean(name))) return s;
        return null;
    }

    /** Saves the session first in the list (one of the same name replaced). */
    static void put(Session s) throws java.io.IOException {
        List<Session> all = load();
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).name.equalsIgnoreCase(s.name)) all.remove(i);
        all.add(0, s);
        save(all);
        setCurrent(s.name);
    }

    static void remove(String name) throws java.io.IOException {
        List<Session> all = load();
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).name.equalsIgnoreCase(clean(name))) all.remove(i);
        save(all);
        if (clean(name).equalsIgnoreCase(current())) setCurrent("");
    }

    /** The session loaded or saved last ("" for none). */
    static String current() {
        return java.util.prefs.Preferences.userNodeForPackage(PyJav.class).get(CURRENT_KEY, "");
    }

    static void setCurrent(String name) {
        java.util.prefs.Preferences.userNodeForPackage(PyJav.class).put(CURRENT_KEY, clean(name));
    }

    static String now() {
        return new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm").format(new java.util.Date());
    }

    /**
     * A session's rows from its arguments: each token with its value ("1" for a switch); a row the session
     * keeps but the arguments do not name is "" (empty, or off).
     */
    static Map<String, String> rows(String args, Set<String> switches) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (String t : TOKENS) out.put(t, "");
        List<String> w = JavaRun.split(args == null ? "" : args);
        for (int i = 0; i < w.size(); i++) {
            String t = w.get(i);
            if (!TOKENS.contains(t)) continue;
            if (switches.contains(t)) out.put(t, "1");
            else if (i + 1 < w.size()) out.put(t, w.get(++i));
        }
        return out;
    }

    /** A folder row's list key, or null (not a folder row of vst_render's). */
    static String folderKey(String token) {
        return FOLDER_KEYS.get(token);
    }

    static List<String> folders(String token) {
        String key = folderKey(token);
        return key == null ? new ArrayList<String>() : VstLists.kept(key);
    }

    static void rememberFolder(String token, String path) {
        String key = folderKey(token);
        if (key != null && path != null && !path.trim().isEmpty()) VstLists.remember(key, path.trim(), KEEP_FOLDERS);
    }
}
