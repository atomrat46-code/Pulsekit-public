import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Usage: java BatchRun [--program SogniVideo] [--prompt_file <promptfile>] [--prompts <folder>] [--dir <folder>] [--list <listfile>] [--slot image] [--args text] [--join] [--max N] [--plan]
 *
 * Plans a batch of runs of another program (SogniVideo, SogniPedit, ImageUpscaler...), and
 * Pulsekit's PyJav then runs them one after another, each as an ordinary run of that program (its
 * log, File > Downloads, a reconnect when one times out). On its own it only prints the plan.
 *
 *   --program      the program run: SogniVideo (default), SogniPedit, SogniPadd, ImageUpscaler, or
 *                  another program of PyJav's Java menu (VideoFrames, Wav2Mp3...).
 *   --prompt_file  a prompt sheet (.prompt) whose prompt every run uses (the settings line a Sogni
 *                  sheet keeps under its prompt is left out).
 *   --prompts      a folder of prompt sheets instead: one run for each (with each picture, when
 *                  pictures are given too).
 *   --dir          the pictures in this folder (for another program, every file): one run for each,
 *                  the file given with --slot.
 *   --list         a text file listing the files instead (one a line; # starts a comment).
 *   --slot         the argument each file goes in: image (--image, the default), image2, end_image,
 *                  or input: first, as the program's input file (ImageUpscaler's picture).
 *   --args         the rest of each run's arguments, as in PyJav's Arguments box (for example the
 *                  Params' line: --duration 6 --end_image last.png). A --prompt here is used when no
 *                  prompt sheet is given.
 *   --join         SogniVideo: when every run is over, JoinVideo joins the clips in order.
 *   --max N        at most N runs (each costs Sogni credit).
 *   --plan         only print the plan; nothing is run.
 *
 * Each run is printed as a "Batch run:" line (its arguments quoted), which PyJav reads.
 *
 * Plain Java (no lambdas, java.* only), so PyJav can compile it on a phone.
 */
public class BatchRun {
    private BatchRun() {}

    static final String USAGE = "Usage: java BatchRun [--program SogniVideo] [--prompt_file <promptfile>] [--prompts <folder>] [--dir <folder>] [--list <listfile>] "
        + "[--slot image] [--args text] [--join] [--max N] [--plan]";
    static final String[] PROGRAMS = {"SogniVideo", "SogniPedit", "SogniPadd", "ImageUpscaler"};

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (Throwable ex) {
            String m = ex.getMessage();
            System.out.println("Failed: " + (m == null || m.length() == 0 ? ex.toString() : m));
            code = 1;
        }
        // In Pulsekit on the phone the program runs inside the app: never exit there.
        if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
    }

    static int run(String[] args) throws Exception {
        String program = "SogniVideo";
        String promptFile = null;
        String prompts = null;
        String dir = null;
        String list = null;
        String slot = "image";
        String rest = "";
        boolean join = false;
        boolean plan = false;
        int max = 0;
        for (int i = 0; i < args.length; i++) {
            String a = args[i] == null ? "" : args[i].trim();
            boolean more = i + 1 < args.length;
            if (a.length() == 0) continue;
            if (a.equals("--program") && more) program = args[++i].trim().replaceAll("(?i)\\.java$", "");
            else if (a.equals("--prompt_file") && more) promptFile = args[++i].trim();
            else if (a.equals("--prompts") && more) prompts = args[++i].trim();
            else if (a.equals("--dir") && more) dir = args[++i].trim();
            else if (a.equals("--list") && more) list = args[++i].trim();
            else if (a.equals("--slot") && more) slot = args[++i].trim();
            else if (a.equals("--args") && more) rest = args[++i];
            else if (a.equals("--join")) join = true;
            else if (a.equals("--plan")) plan = true;
            else if (a.equals("--max") && more) {
                try {
                    max = Integer.parseInt(args[++i].trim());
                } catch (NumberFormatException ex) {
                    System.out.println("Failed: --max takes a number");
                    return 2;
                }
            } else if (a.equals("-h") || a.equals("--help")) {
                System.out.println(USAGE);
                return 0;
            } else {
                System.out.println("Failed: unknown argument " + a);
                System.out.println(USAGE);
                return 2;
            }
        }
        if (!program.matches("[A-Za-z][A-Za-z0-9_]*") || program.equals("BatchRun")) {
            System.out.println("Failed: --program names a program of PyJav's Java menu, such as " + Arrays.toString(PROGRAMS));
            return 2;
        }
        boolean sogni = program.startsWith("Sogni");
        boolean pictureProgram = sogni || program.equals("ImageUpscaler");
        if (program.equals("ImageUpscaler") && slot.equals("image")) slot = "input";
        boolean input = slot.equals("input") || slot.equals("--input");
        if (!slot.startsWith("--")) slot = "--" + slot;
        if (join && !program.equals("SogniVideo")) {
            System.out.println("Failed: --join joins SogniVideo's clips");
            return 2;
        }
        List<String> base = split(rest);
        // The prompts: a sheet's, each sheet's in a folder, else the one in --args.
        List<String[]> texts = new ArrayList<String[]>();
        if (promptFile != null && promptFile.length() > 0) {
            File f = new File(promptFile);
            String p = promptOf(f);
            if (p == null) {
                System.out.println("Failed: " + f.getName() + " is not a prompt sheet with a prompt in it");
                return 1;
            }
            texts.add(new String[] {stem(f.getName()), p});
        }
        if (prompts != null && prompts.length() > 0) {
            File[] in = new File(prompts).listFiles();
            if (in == null) {
                System.out.println("Failed: no folder " + prompts);
                return 1;
            }
            Arrays.sort(in);
            for (File f : in) {
                if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".prompt")) continue;
                String p = promptOf(f);
                if (p == null) System.out.println("Left out " + f.getName() + ": no prompt in it");
                else texts.add(new String[] {stem(f.getName()), p});
            }
        }
        int at = base.indexOf("--prompt");
        if (texts.isEmpty() && at >= 0 && at + 1 < base.size()) texts.add(new String[] {"--prompt", base.get(at + 1)});
        if (at >= 0 && at + 1 < base.size()) {
            base.remove(at + 1);
            base.remove(at);
        }
        // The pictures, one run each.
        List<File> pictures = new ArrayList<File>();
        if (dir != null && dir.length() > 0) {
            File d = new File(dir);
            File[] in = d.listFiles();
            if (in == null) {
                System.out.println("Failed: no folder " + dir);
                return 1;
            }
            Arrays.sort(in);
            for (File f : in) if (f.isFile() && !f.getName().startsWith(".") && (!pictureProgram || isPicture(f.getName()))) pictures.add(f.getAbsoluteFile());
        }
        if (list != null && list.length() > 0) pictures.addAll(readList(new File(list)));
        if (texts.isEmpty() && pictures.isEmpty()) {
            System.out.println("Failed: nothing to run: give a prompt sheet (--prompt_file), a folder of them (--prompts), or pictures (--dir, --list)");
            System.out.println(USAGE);
            return 2;
        }
        if (texts.isEmpty() && sogni) {
            System.out.println("Failed: " + program + " needs a prompt: --prompt_file, --prompts, or --prompt in --args");
            return 2;
        }
        // The runs: every prompt with every picture (or each alone when there is only one kind).
        List<List<String>> runs = new ArrayList<List<String>>();
        List<String> names = new ArrayList<String>();
        List<String[]> promptsUsed = texts.isEmpty() ? Collections.singletonList((String[]) null) : texts;
        List<File> picsUsed = pictures.isEmpty() ? Collections.singletonList((File) null) : pictures;
        for (String[] t : promptsUsed) {
            for (File pic : picsUsed) {
                List<String> one = new ArrayList<String>(base);
                if (pic != null) {
                    int s = one.indexOf(slot);
                    if (s >= 0 && s + 1 < one.size()) {
                        one.remove(s + 1);
                        one.remove(s);
                    }
                    if (input) one.add(0, pic.getPath());
                    else {
                        one.add(slot);
                        one.add(pic.getPath());
                    }
                }
                if (t != null) {
                    one.add("--prompt");
                    one.add(t[1]);
                }
                runs.add(one);
                names.add((t == null || t[0].equals("--prompt") ? "" : t[0]) + (t != null && pic != null && !t[0].equals("--prompt") ? " + " : "") + (pic == null ? "" : pic.getName()));
            }
        }
        if (max > 0 && runs.size() > max) {
            System.out.println("Only the first " + max + " of " + runs.size() + " runs (--max)");
            runs = new ArrayList<List<String>>(runs.subList(0, max));
        }
        System.out.println("Batch program: " + program + ".java");
        if (join) System.out.println("Batch join: the clips, in order, with JoinVideo");
        for (int i = 0; i < runs.size(); i++) {
            StringBuilder line = new StringBuilder();
            for (String a : runs.get(i)) line.append(line.length() == 0 ? "" : " ").append(quote(a));
            System.out.println("Run " + (i + 1) + "/" + runs.size() + ": " + names.get(i));
            System.out.println("Batch run: " + line);
        }
        if (plan) {
            System.out.println("Succeeded: planned " + runs.size() + " run" + (runs.size() == 1 ? "" : "s") + " of " + program + " (--plan: none run)");
            return 0;
        }
        System.out.println("Succeeded: " + runs.size() + " run" + (runs.size() == 1 ? "" : "s") + " of " + program + " to go");
        return 0;
    }

    static boolean isPicture(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".+\\.(png|jpe?g|webp)");
    }

    static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * A prompt sheet's prompt (PKPROMPT1: the text after its "---" line), without the settings line
     * a Sogni program keeps under it ("Duration: ...", "LoRAs: ..."); null when it has none. A plain
     * text file is its prompt as it is.
     */
    static String promptOf(File f) throws java.io.IOException {
        if (!f.isFile()) return null;
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
        String body = text;
        if (text.startsWith("PKPROMPT1")) {
            int dash = text.indexOf("\n---\n");
            if (dash < 0) return null;
            body = text.substring(dash + 5);
        }
        int cut = body.lastIndexOf("\n\n");
        if (cut >= 0) {
            String last = body.substring(cut + 2).trim();
            if (last.matches("(?s)(Duration|LoRAs|Resolution|Picture|First frame): .*")) body = body.substring(0, cut);
        }
        // One line: a run's arguments are one line of the log.
        body = body.replaceAll("\\s*\n\\s*", " ").trim();
        return body.length() == 0 ? null : body;
    }

    static List<File> readList(File list) throws java.io.IOException {
        List<File> out = new ArrayList<File>();
        if (!list.isFile()) throw new java.io.IOException("no list " + list.getPath());
        File beside = list.getAbsoluteFile().getParentFile();
        for (String line : new String(Files.readAllBytes(list.toPath()), StandardCharsets.UTF_8).split("\n")) {
            String t = line.replace("\r", "").trim();
            if (t.length() == 0 || t.startsWith("#")) continue;
            File f = new File(t);
            if (!f.isAbsolute()) f = new File(beside, t);
            if (f.isFile()) out.add(f.getAbsoluteFile());
            else System.out.println("Not found: " + t);
        }
        return out;
    }

    /** An argument as PyJav's Arguments box takes it: quoted when it has a space or a quote. */
    static String quote(String a) {
        if (a.length() > 0 && !a.matches(".*[\\s\"'\\\\].*")) return a;
        return "\"" + a.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** The words of an argument line: spaces split them, except inside quotes ("..." or '...'). */
    static List<String> split(String line) {
        List<String> out = new ArrayList<String>();
        if (line == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean any = false;
        char q = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q != 0) {
                if (c == '\\' && q == '"' && i + 1 < line.length() && (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\\')) cur.append(line.charAt(++i));
                else if (c == q) q = 0;
                else cur.append(c);
            } else if (c == '"' || c == '\'') {
                q = c;
                any = true;
            } else if (Character.isWhitespace(c)) {
                if (any || cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                any = false;
            } else cur.append(c);
        }
        if (any || cur.length() > 0) out.add(cur.toString());
        return out;
    }
}
