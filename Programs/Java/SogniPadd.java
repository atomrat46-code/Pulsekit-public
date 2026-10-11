import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SogniPadd: a new picture from a prompt alone, made with Dark Beast Krea 2 (Sogni's uncensored
 * Krea 2 community model, dark-beast-krea2) on Sogni's GPU network. It takes no input picture and
 * changes no existing one. Sogni's Safe Content Filter is off for every run: the model's mature
 * output needs it off.
 *
 * The LoRAs are Krea 2 LoRAs applied in order, as id:strength pairs separated by commas, spaces or new lines, or by the names Sogni's app shows (Warm Light 0.6, separated by commas). Without
 * --loras the set is Mystic X 1, Realism Engine v3 0.8, Chest Size 0.5, Weight -1 and
 * Krea2FilterBypass 2vector 1 (krea2-mystic-x:1,krea2-realism-engine:0.8,krea2-breast:0.5,
 * krea2-weight:-1,krea2-filter-bypass-2:1); --loras none leaves them all out. Eight at most.
 * Sogni refuses Age (krea2-age) below 0 with Mystic X or Realism Engine, and so does SogniPadd.
 *
 * --aspect (16:9, 9:16, 4:5, 1:1...) or --width and --height (256 to 2560) set the size; Sogni's
 * default is 1024 square. --seed repeats a picture. Steps, sampler and VAE are Sogni's own for the
 * model: its hosted image tool takes none of them.
 *
 * The API key is found as SogniVideo finds it: SOGNI_API_KEY, --key_file, the key file in File >
 * Drum Midi Settings, or ~/.config/sogni/credentials. --unlimited is for a Sogni Unlimited Plan (the
 * subscription pays; fair use limits apply); otherwise the run spends Spark, capped by --max_cost,
 * with --confirm_cost to confirm the charge. --saveprompt also writes the prompt as
 * sogni-padd-<first words>.prompt (category image, type AI), with the settings under it and the
 * run's picture added when it ends. The picture is saved as sogni-padd-<first words>.png (or the
 * output name given), in Downloads on the phone. --workflow <id> downloads the picture of a run
 * that already finished.
 */
public final class SogniPadd {
  public static void main(String[] args) throws Exception {
    int code = run(args);
    // Inside Pulsekit (PyJav on the phone runs programs in the app's own process) System.exit would close the app.
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  /** Printed first, so a run's log shows which SogniPadd ran. */
  static final String VERSION = "SogniPadd 2026-10-07";

  /** The model: Dark Beast Krea 2, as Sogni's hosted tools name it. */
  static final String MODEL = "dark-beast-krea2";

  /** The LoRAs without --loras. */
  static final String DEFAULT_LORAS = "krea2-mystic-x:1,krea2-realism-engine:0.8,krea2-breast:0.5,krea2-weight:-1,krea2-filter-bypass-2:1";

  /** The most LoRAs one render takes. */
  static final int MAX_LORAS = 8;

  /** The work folder this run writes in (pulsekit.work on the phone). */
  static String work;

  /** The prompt sheet --saveprompt wrote this run and its text, the file the run made, and its errors and warnings. */
  static File promptSheet;
  static String promptText;
  static String resultFile;
  static StringBuilder said;

  /** The program; returns its exit code (0 ok, 1 failed, 2 bad arguments). */
  static int run(String[] typed) throws Exception {
    work = System.getProperty("pulsekit.work");
    promptSheet = null;
    promptText = null;
    resultFile = null;
    said = new StringBuilder();
    try {
      return steps(typed);
    } finally {
      finishPrompt();
    }
  }

  /** Prints a line of the log; errors and warnings are also kept for the saved sheet's Result text. */
  static void say(String line) {
    System.out.println(line);
    String t = line == null ? "" : line.trim();
    if (said != null && (t.startsWith("Failed") || t.startsWith("Note") || t.startsWith("Could not") || t.startsWith("Sogni's fair use")
        || t.startsWith("Unknown argument"))) {
      said.append(t).append('\n');
    }
  }

  /** Ends the sheet --saveprompt wrote with "Result file: <the picture>" and "Result text:" (errors, warnings). */
  static void finishPrompt() {
    if (promptSheet == null || promptText == null) return;
    String text = said == null ? "" : said.toString().trim();
    if (resultFile == null && text.length() == 0) text = "No result file was made";
    StringBuilder sb = new StringBuilder(promptText);
    sb.append("\n\n");
    if (resultFile != null) sb.append("Result file: ").append(resultFile).append('\n');
    if (text.length() > 0) sb.append("Result text:\n").append(text).append('\n');
    try {
      FileOutputStream fos = new FileOutputStream(promptSheet);
      try {
        fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
      } finally {
        fos.close();
      }
    } catch (IOException ex) {
      System.out.println("Could not add the result to " + promptSheet.getName());
    }
  }

  /** The run itself. */
  static int steps(String[] typed) throws Exception {
    say(VERSION);
    String[] args = tidy(typed);
    String out = null;
    String prompt = null;
    String loraText = null;
    String aspect = null;
    String keyFile = null;
    String apiBase = null;
    String workflowId = null;
    double width = 0;
    double height = 0;
    double seed = -1;
    double maxCost = 0;
    boolean confirm = false;
    boolean unlimited = false;
    boolean savePrompt = false;
    boolean deleteFromSogni = false;
    // The model's mature output needs Sogni's Safe Content Filter off: off on every SogniPadd run.
    SogniApi.noFilter = true;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--prompt") && i + 1 < args.length) prompt = args[++i];
      else if (a.equals("--loras") && i + 1 < args.length) loraText = args[++i].trim();
      else if (a.equals("--aspect") && i + 1 < args.length) aspect = args[++i].trim();
      else if (a.equals("--width") && i + 1 < args.length) width = number(a, args[++i]);
      else if (a.equals("--height") && i + 1 < args.length) height = number(a, args[++i]);
      else if (a.equals("--seed") && i + 1 < args.length) seed = number(a, args[++i]);
      else if (a.equals("--saveprompt")) savePrompt = true;
      else if (a.equals("--delete_from_sogni")) deleteFromSogni = true;
      else if (a.equals("--unlimited")) unlimited = true;
      else if (a.equals("--confirm_cost")) confirm = true;
      else if (a.equals("--max_cost") && i + 1 < args.length) maxCost = number(a, args[++i]);
      else if (a.equals("--workflow") && i + 1 < args.length) workflowId = args[++i].trim();
      else if (a.equals("--key_file") && i + 1 < args.length) keyFile = args[++i];
      else if (a.equals("--api_base") && i + 1 < args.length) apiBase = args[++i];
      else if (a.equals("-h") || a.equals("--help")) {
        usage();
        return 0;
      } else if (!a.startsWith("--") && out == null) out = a;
      else {
        say("Unknown argument: " + a);
        usage();
        return 2;
      }
    }
    if (Double.isNaN(maxCost) || Double.isNaN(width) || Double.isNaN(height) || Double.isNaN(seed)) return 2;
    if (aspect != null && aspect.length() == 0) aspect = null;
    if (workflowId != null && workflowId.length() == 0) workflowId = null;
    if (workflowId == null && (prompt == null || prompt.trim().length() == 0)) {
      say("Failed: give --prompt, the picture to make");
      usage();
      return 2;
    }
    if ((width != 0 && (width < 256 || width > 2560)) || (height != 0 && (height < 256 || height > 2560))) {
      say("Failed: --width and --height are 256 to 2560 pixels (Dark Beast Krea 2's sizes)");
      return 2;
    }
    if ((width > 0) != (height > 0)) {
      say("Failed: give both --width and --height, or neither (Sogni's 1024 square)");
      return 2;
    }
    if (aspect != null && width > 0) {
      say("Failed: give --aspect or --width and --height, not both");
      return 2;
    }
    if (aspect != null && !aspect.matches("\\d{1,2}:\\d{1,2}")) {
      say("Failed: --aspect is a shape such as 16:9, 9:16, 4:5 or 1:1");
      return 2;
    }
    // The LoRAs, in order: the default set, or --loras (none for no LoRAs).
    List<String> loras = new ArrayList<String>();
    List<Double> strengths = new ArrayList<Double>();
    String given = loraText == null || loraText.length() == 0 ? DEFAULT_LORAS : loraText;
    if (!given.equalsIgnoreCase("none")) {
      // Ids with strengths (krea2-warm-light:0.6) or the names Sogni's app shows (Warm Light 0.6).
      for (String[] pair : SogniApi.loraList(given, SogniApi.KREA2_LORAS)) {
        String id = pair[0];
        double strength = 1;
        if (pair[1] != null) {
          strength = number("--loras (" + id + ")", pair[1]);
          if (Double.isNaN(strength)) return 2;
        }
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
          say("Failed: --loras takes LoRA ids such as krea2-mystic-x:0.5 or their names such as Warm Light 0.5, not \"" + id + "\". Known: " + SogniApi.loraNames(SogniApi.KREA2_LORAS));
          return 2;
        }
        loras.add(id);
        strengths.add(Double.valueOf(strength));
      }
    }
    if (loras.size() > MAX_LORAS) {
      say("Failed: " + loras.size() + " LoRAs; one render takes " + MAX_LORAS + " at most");
      return 2;
    }
    // Sogni's own rule: no younger-looking Age with the sexual fine-tunes.
    int age = loras.indexOf("krea2-age");
    if (age >= 0 && strengths.get(age).doubleValue() < 0 && (loras.contains("krea2-mystic-x") || loras.contains("krea2-realism-engine"))) {
      say("Failed: krea2-age below 0 cannot be used with krea2-mystic-x or krea2-realism-engine (Sogni refuses it); keep Age at 0 or above");
      return 2;
    }
    if (unlimited && maxCost > 0) say("Note: --max_cost is not used with the Unlimited Plan");
    String size = width > 0 ? (int) width + "x" + (int) height : aspect != null ? aspect : "1024 square (Sogni's default)";
    if (savePrompt && workflowId == null) {
      String sheetName = out != null ? out.trim().replaceAll("\\.[A-Za-z0-9]{1,5}$", "") : paddName(prompt, "");
      File sheet = savePrompt(sheetName, "Sogni Dark Beast Krea 2", prompt.trim() + "\n\nLoRAs: " + (loras.isEmpty() ? "none" : loraLine(loras, strengths))
          + ". Size: " + size + "." + (seed >= 0 ? " Seed: " + (long) seed + "." : ""));
      say(sheet == null ? "Could not save the prompt" : "Saved prompt " + sheet.getName());
    }
    String key = SogniApi.findKey(keyFile);
    if (key == null) {
      say("Failed: no Sogni API key. Choose a key file in File > Drum Midi Settings (Sogni API key file), give --key_file "
          + "with a text file holding SOGNI_API_KEY=<your key>, or set SOGNI_API_KEY. Get the key at https://dashboard.sogni.ai (account menu).");
      return 1;
    }
    SogniApi api = new SogniApi(apiBase, key);
    if (workflowId != null) {
      say("Fetching the picture of workflow " + workflowId);
    } else {
      say("Picture: " + prompt.trim());
      say("Model Dark Beast Krea 2 (" + MODEL + "), 1 picture, " + size + (seed >= 0 ? ", seed " + (long) seed : ""));
      say(loras.isEmpty() ? "LoRAs: none" : "LoRAs: " + loraLine(loras, strengths));
      say("Content filter: off (Sogni's Safe Content Filter does not check SogniPadd runs)");
      if (unlimited) say("Unlimited Plan: the subscription pays; Sogni's daily and monthly fair use limits apply");
    }
    try {
      String id = workflowId;
      if (id == null) {
        String input = SogniApi.imageInput("Pulsekit picture", prompt.trim(), MODEL, (int) width, (int) height, aspect, (long) seed, loras, strengths);
        id = api.start(input, confirm || unlimited, unlimited ? 0 : maxCost, null, unlimited ? "subscription" : null);
      }
      say("Workflow: " + id);
      Map<String, Object> wf = api.waitFor(id, 10 * 60 * 1000L, new SogniApi.Log() {
        public void line(String s) {
          say(s);
        }
      });
      String status = SogniApi.str(wf.get("status"));
      List<Map<String, Object>> made = SogniApi.imageArtifacts(wf);
      if (made.isEmpty()) {
        say("Failed: " + why(api, id, wf));
        return 1;
      }
      String url = SogniApi.str(made.get(0).get("url"));
      String ext = SogniApi.mediaExtension(url, SogniApi.mimeOf(made.get(0)), ".png");
      String name = out != null ? out.trim().replaceAll("\\.[A-Za-z0-9]{1,5}$", "") : paddName(prompt, id);
      byte[] data = api.download(url);
      File file = saveData(name + ext, data);
      if (file == null) {
        say("Failed: could not save " + name + ext);
        return 1;
      }
      say("Wrote " + file.getName() + " (" + size(data.length) + ")");
      resultFile = file.getName();
      if (!"completed".equals(status)) say("Note: workflow " + status);
      // --delete_from_sogni: the render deleted on Sogni now that it is saved here.
      if (deleteFromSogni) for (String line : api.deleteProjects(wf, inWork("sogni_workflow_record.txt"))) say(line);
      say("Succeeded: " + file.getName());
    } catch (SogniApi.ApiException ex) {
      say("Failed: " + ex.getMessage());
      if (unlimited && (ex.status == 429 || String.valueOf(ex.getMessage()).toLowerCase().contains("fair use"))) {
        say("Sogni's fair use limit is reached, so it does not run the task now. Try again when the daily limit renews"
            + (ex.retryAfter > 0 ? " (Sogni says in about " + wait(ex.retryAfter) + ")" : "") + ".");
      }
      return 1;
    } catch (IOException ex) {
      say("Failed: " + ex.getMessage());
      return 1;
    }
    return 0;
  }

  static void usage() {
    say("Usage: java SogniPadd [output.png] [--prompt text] [--loras id:strength,...] [--aspect 16:9|9:16|4:5|1:1] [--width N] [--height N] "
        + "[--seed N] [--saveprompt] [--unlimited] [--key_file credentials.txt] [--confirm_cost] [--max_cost N] [--workflow id] [--delete_from_sogni]");
  }

  /** "krea2-mystic-x 1, krea2-weight -1". */
  static String loraLine(List<String> loras, List<Double> strengths) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < loras.size(); i++) sb.append(i == 0 ? "" : ", ").append(loras.get(i)).append(' ').append(SogniApi.number(strengths.get(i).doubleValue()));
    return sb.toString();
  }

  /** Writes `prompt` as a Pulsekit prompt sheet (PKPROMPT1): name, category image, the model, no reference files, type AI. Never over an existing file. */
  static File savePrompt(String name, String model, String prompt) {
    StringBuilder sb = new StringBuilder();
    sb.append("PKPROMPT1\n").append(name).append("\n\n\n\n\n");
    sb.append("Category: image\n");
    sb.append("Model: ").append(model).append('\n');
    sb.append("Reference file 1: \n");
    sb.append("Reference file 2: \n");
    sb.append("Type: ai\n");
    sb.append("---\n");
    sb.append(prompt);
    File file = saveData(name + ".prompt", sb.toString().getBytes(StandardCharsets.UTF_8));
    promptSheet = file;
    promptText = sb.toString();
    return file;
  }

  /** sogni-padd-<first words of the prompt>, or sogni-padd-<run> when there is no prompt. */
  static String paddName(String prompt, String id) {
    String words = prompt == null ? "" : prompt.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    StringBuilder sb = new StringBuilder();
    for (String w : words.split(" ")) {
      if (w.length() == 0 || sb.length() + w.length() > 24) break;
      sb.append(sb.length() > 0 ? "-" : "").append(w);
    }
    String plain = id.replaceAll("[^A-Za-z0-9]", "");
    if (sb.length() == 0) sb.append(plain.substring(0, Math.min(8, plain.length())));
    return "sogni-padd-" + sb;
  }

  /** "800 KB" or "1.2 MB". */
  static String size(long bytes) {
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }

  /** "3 h 20 min", "12 min" or "45 s" for a wait in seconds. */
  static String wait(int seconds) {
    if (seconds >= 3600) return (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min";
    if (seconds >= 60) return (seconds / 60) + " min";
    return seconds + " s";
  }

  /**
   * A file in the work folder. On the phone PyJav runs programs inside the app, where a bare name
   * would land in the process's own folder ("/", read-only), so names are placed under PyJav's
   * work folder (pulsekit.work, else user.dir); an absolute path stays as given.
   */
  static File inWork(String name) {
    File f = new File(name);
    if (f.isAbsolute()) return f;
    // Android ignores setting user.dir (it stays "/", read-only), so PyJav's own work folder comes first.
    String dir = work != null && work.length() > 0 ? work : System.getProperty("user.dir", ".");
    return new File(dir, name);
  }

  /** Writes `data` as `name` in the work folder, never over an existing file (name(1).mp4...). Null if it could not. */
  static File saveData(String name, byte[] data) {
    File file = inWork(name);
    String path = file.getPath();
    int dot = path.lastIndexOf('.');
    String stem = dot > path.lastIndexOf(File.separatorChar) ? path.substring(0, dot) : path;
    String ext = dot > path.lastIndexOf(File.separatorChar) ? path.substring(dot) : "";
    for (int n = 1; file.exists(); n++) file = new File(stem + "(" + n + ")" + ext);
    try {
      FileOutputStream fos = new FileOutputStream(file);
      try {
        fos.write(data);
      } finally {
        fos.close();
      }
      return file;
    } catch (IOException ex) {
      return null;
    }
  }

  /**
   * Why a run made no picture: the failed step's own words, from the record or the event list. When
   * Sogni gives none, the record is saved as sogni_workflow_failed.json to look at.
   */
  static String why(SogniApi api, String id, Map<String, Object> wf) {
    String why = SogniApi.problem(wf);
    if (!why.startsWith("workflow ")) return why;
    try {
      List<String> found = new ArrayList<String>();
      SogniApi.reasons(api.events(id), found);
      if (!found.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < found.size() && i < 3; i++) sb.append(i == 0 ? "" : "; ").append(found.get(i));
        return sb.toString();
      }
    } catch (IOException ignored) {
      // Fall back to the saved record.
    }
    File raw = saveData("sogni_workflow_failed.json", SogniApi.toJson(wf).getBytes(StandardCharsets.UTF_8));
    return raw == null ? why : why + " (Sogni gave no reason; the workflow record is saved as " + raw.getName() + ")";
  }

  /**
   * Arguments as typed by hand: "--workflow wf_1" given as one argument becomes the switch and its
   * value, and a switch given twice in a row (--workflow --workflow wf_1) counts once.
   */
  static String[] tidy(String[] args) {
    List<String> out = new ArrayList<String>();
    for (String a : args) {
      String t = a == null ? "" : a.trim();
      int sp = t.startsWith("--") ? t.indexOf(' ') : -1;
      List<String> parts = new ArrayList<String>();
      if (sp > 0) {
        parts.add(t.substring(0, sp));
        parts.add(t.substring(sp + 1).trim());
      } else {
        parts.add(a);
      }
      for (String p : parts) {
        if (p.startsWith("--") && !out.isEmpty() && p.equals(out.get(out.size() - 1))) continue;
        out.add(p);
      }
    }
    return out.toArray(new String[0]);
  }

  /** A number for `flag`, or NaN after saying what is wrong. */
  static double number(String flag, String value) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException ex) {
      say("Failed: " + flag + " needs a number, not \"" + value + "\"");
      return Double.NaN;
    }
  }

  /** shared/src/pulsekit/SogniApi.java, copied here (programs see only the Java runtime); android/build.sh checks they match. */
  static final class SogniApi {
    // --- SogniApi begin ---
    public static final String BASE = "https://api.sogni.ai";
    public static final String APP_SOURCE = "pulsekit";
    /**
     * --no_filter: Sogni's Safe Content Filter off for what this run starts (a workflow, a chat
     * and the tools it runs), as Sogni's own tools' --no-filter. On by default: Sogni can then pause
     * a run for a safety review (waiting_for_user, safety_review_required). Set by each program on
     * every run (PyJav runs programs in the app, where this stays between runs).
     */
    public static boolean noFilter;
    public static final String[] DONE = {"completed", "partial_failure", "failed", "cancelled", "waiting_for_user"};

    /** Progress lines (status changes, waits). */
    public interface Log {
      void line(String s);
    }

    /** A request Sogni refused, with its HTTP status and, for 429, the seconds to wait. */
    public static final class ApiException extends IOException {
      public final int status;
      public final int retryAfter;

      public ApiException(String message, int status, int retryAfter) {
        super(message);
        this.status = status;
        this.retryAfter = retryAfter;
      }
    }

    public final String base;
    public final String apiKey;
    public int timeoutMs = 60000;

    public SogniApi(String base, String apiKey) {
      String b = base == null || base.length() == 0 ? BASE : base;
      while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
      this.base = b;
      this.apiKey = apiKey;
    }

    /** The key file set in Pulsekit's settings (File > Drum Midi Settings), or null. Set by ApiKeys. */
    public static String keyFileSetting;

    /**
     * The API key: SOGNI_API_KEY in the environment, else SOGNI_API_KEY=... (or the key alone) in
     * keyFile when given, in the settings' key file, or in ~/.config/sogni/credentials. Null when
     * none is found.
     */
    public static String findKey(String keyFile) {
      String env = System.getenv("SOGNI_API_KEY");
      if (env != null && env.trim().length() > 0) return env.trim();
      List<File> files = new ArrayList<File>();
      if (keyFile != null && keyFile.length() > 0) files.add(new File(keyFile));
      if (keyFileSetting != null && keyFileSetting.length() > 0) files.add(new File(keyFileSetting));
      String home = System.getProperty("user.home");
      if (home != null) files.add(new File(home, ".config/sogni/credentials"));
      for (File f : files) {
        String k = keyIn(f);
        if (k != null) return k;
      }
      return null;
    }

    /** The key in a file: a SOGNI_API_KEY=... line, or a line holding only the key. Null if none. */
    public static String keyIn(File f) {
      if (f == null || !f.isFile()) return null;
      try {
        return keyInText(new String(readAll(new java.io.FileInputStream(f)), StandardCharsets.UTF_8));
      } catch (IOException ex) {
        return null;
      }
    }

    public static String keyInText(String text) {
      if (text == null) return null;
      for (String line : text.split("\r?\n")) {
        String t = line.trim();
        if (t.startsWith("export ")) t = t.substring(7).trim();
        if (t.startsWith("SOGNI_API_KEY=")) {
          String v = t.substring(14).trim();
          if (v.length() > 1 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) v = v.substring(1, v.length() - 1);
          if (v.length() > 0) return v;
        }
        // A file holding only the key.
        if (t.length() >= 16 && t.indexOf('=') < 0 && t.indexOf(' ') < 0 && !t.startsWith("#")) return t;
      }
      return null;
    }

    /**
     * A one-step generate_music workflow. Zero or empty values are left to Sogni's defaults
     * (30 s, 120 BPM, C major, 4/4, turbo).
     */
    public static String musicInput(String title, String prompt, double duration, double bpm, String keyscale, int timesig, String model, String lyrics) {
      Map<String, Object> args = new LinkedHashMap<String, Object>();
      args.put("prompt", prompt);
      if (duration > 0) args.put("duration", Double.valueOf(duration));
      if (bpm > 0) args.put("bpm", Double.valueOf(bpm));
      if (keyscale != null && keyscale.length() > 0) args.put("keyscale", keyscale);
      if (timesig > 0) args.put("timesig", Integer.valueOf(timesig));
      if (model != null && model.length() > 0) args.put("model", model);
      if (lyrics != null && lyrics.length() > 0) args.put("lyrics", lyrics);
      args.put("numberOfVariations", Integer.valueOf(1));
      Map<String, Object> step = new LinkedHashMap<String, Object>();
      step.put("id", "music");
      step.put("toolName", "generate_music");
      step.put("arguments", args);
      List<Object> steps = new ArrayList<Object>();
      steps.add(step);
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      if (title != null && title.length() > 0) input.put("title", title);
      input.put("steps", steps);
      return toJson(input);
    }

    /**
     * A one-step MiniMax H3 FastH3 video workflow. With no picture it is text-to-video
     * (generate_video); one picture (the first upload) is the start frame (animate_photo); two are
     * the first and last frames. `resolution` 768 or 0 is FastH3's own 768p canvas; 720, 1080 or 1440
     * pick the two-stage engine, which renders a canvas and delivers it at twice the size. Zero
     * duration is Sogni's default (5 s; H3 makes 5.17 to 15.08 s). `exact` sends the prompt as
     * written; `audio` false asks for a silent clip; `aspect` ("16:9", "9:16"...) only when given.
     */
    public static String videoInput(String title, String prompt, int pictures, double duration, int resolution, boolean audio, boolean exact, String aspect) {
      return videoInput(title, prompt, pictures, duration, resolution, audio, exact, aspect, null, null);
    }

    /**
     * H3's structured prompt ("integrated_multimodal_description: ... overall_soundscape: ...
     * non_diegetic_music: ...") with each field starting a paragraph again: Params joins a prompt's
     * lines into one, and Sogni reads the fields only at line starts ("received none"). A left-out
     * non_diegetic_music is added as N/A (no score). Any other prompt is sent as it is.
     */
    public static String h3Fields(String prompt) {
      if (prompt == null || prompt.indexOf("integrated_multimodal_description") < 0) return prompt;
      String out = prompt.replaceAll("\\s*(?<![A-Za-z0-9_])(integrated_multimodal_description|overall_soundscape|non_diegetic_music)\\s*:\\s*", "\n\n$1: ").trim();
      if (out.indexOf("non_diegetic_music:") < 0) out = out + "\n\nnon_diegetic_music: N/A";
      return out;
    }

    /** As above, with H3 video LoRAs in order (`strengths` positional; positive only, 0 off). */
    public static String videoInput(String title, String prompt, int pictures, double duration, int resolution, boolean audio, boolean exact, String aspect,
        List<String> loras, List<Double> strengths) {
      Map<String, Object> args = new LinkedHashMap<String, Object>();
      args.put("prompt", h3Fields(prompt));
      args.put("videoModel", videoModel(pictures, resolution));
      if (duration > 0) args.put("duration", Double.valueOf(duration));
      args.put("targetResolution", Integer.valueOf(resolution == 720 || resolution == 1080 || resolution == 1440 ? resolution : 768));
      if (!audio) args.put("generateAudio", Boolean.FALSE);
      if (exact) args.put("skipPromptProcessing", Boolean.TRUE);
      if (aspect != null && aspect.length() > 0) args.put("aspectRatio", aspect);
      args.put("numberOfVariations", Integer.valueOf(1));
      if (loras != null && !loras.isEmpty()) {
        args.put("loras", new ArrayList<Object>(loras));
        List<Object> s = new ArrayList<Object>();
        for (Double d : strengths) s.add(d);
        args.put("loraStrengths", s);
      }
      Map<String, Object> step = new LinkedHashMap<String, Object>();
      step.put("id", "video");
      step.put("toolName", pictures > 0 ? "animate_photo" : "generate_video");
      step.put("arguments", args);
      if (pictures > 0) {
        // The uploaded pictures (media_references, in order) are the frames: -1 the first upload, -2 the second.
        args.put("sourceImageIndex", Integer.valueOf(-1));
        args.put("frameRole", pictures >= 2 ? "both" : "start");
        if (pictures >= 2) args.put("endImageIndex", Integer.valueOf(-2));
        List<Object> deps = new ArrayList<Object>();
        for (int i = 0; i < Math.min(pictures, 2); i++) {
          Map<String, Object> d = new LinkedHashMap<String, Object>();
          d.put("sourceStepId", "$input_media");
          d.put("targetArgument", i == 0 ? "sourceImageIndex" : "endImageIndex");
          d.put("transform", "image_index");
          d.put("sourceArtifactIndex", Integer.valueOf(i));
          d.put("mediaType", "image");
          d.put("required", Boolean.TRUE);
          deps.add(d);
        }
        step.put("dependsOn", deps);
      }
      List<Object> steps = new ArrayList<Object>();
      steps.add(step);
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      if (title != null && title.length() > 0) input.put("title", title);
      input.put("steps", steps);
      return toJson(input);
    }

    /**
     * A one-step text-to-image workflow (generate_image) with `model` (a hosted model key such as
     * dark-beast-krea2), one picture out. Zero width and height leave Sogni's size (1024 square);
     * `aspect` ("16:9", "4:5"...) only when given; a negative seed is random. `loras` and `strengths`
     * are applied in order (Krea 2 based models only). Steps and sampler are left to the model: the
     * hosted generate_image tool takes none of them.
     */
    public static String imageInput(String title, String prompt, String model, int width, int height, String aspect, long seed, List<String> loras, List<Double> strengths) {
      Map<String, Object> args = new LinkedHashMap<String, Object>();
      args.put("prompt", prompt);
      if (model != null && model.length() > 0) args.put("model", model);
      if (width > 0) args.put("width", Integer.valueOf(width));
      if (height > 0) args.put("height", Integer.valueOf(height));
      if (aspect != null && aspect.length() > 0) args.put("aspectRatio", aspect);
      if (seed >= 0) args.put("seed", Long.valueOf(seed));
      args.put("numberOfVariations", Integer.valueOf(1));
      if (loras != null && !loras.isEmpty()) {
        args.put("loras", new ArrayList<Object>(loras));
        List<Object> s = new ArrayList<Object>();
        for (Double d : strengths) s.add(d);
        args.put("loraStrengths", s);
      }
      Map<String, Object> step = new LinkedHashMap<String, Object>();
      step.put("id", "image");
      step.put("toolName", "generate_image");
      step.put("arguments", args);
      List<Object> steps = new ArrayList<Object>();
      steps.add(step);
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      if (title != null && title.length() > 0) input.put("title", title);
      input.put("steps", steps);
      return toJson(input);
    }

    /**
     * A one-step Krea 2 Identity Edit workflow (edit_image, model krea-identity-edit): the uploaded
     * pictures are the references (the first is the one edited, a second one guides it), one picture
     * out. `loras` and `strengths` are Krea 2 LoRA ids and their strengths, in order (strengths
     * positional; each LoRA needs one). Steps, guidance and sampler are left to the model: the hosted
     * edit_image tool takes none of them.
     */
    public static String imageEditInput(String title, String prompt, int pictures, List<String> loras, List<Double> strengths) {
      Map<String, Object> args = new LinkedHashMap<String, Object>();
      args.put("prompt", prompt);
      args.put("model", "krea-identity-edit");
      args.put("sourceImageIndex", Integer.valueOf(-1));
      args.put("numberOfVariations", Integer.valueOf(1));
      if (loras != null && !loras.isEmpty()) {
        args.put("loras", new ArrayList<Object>(loras));
        List<Object> s = new ArrayList<Object>();
        for (Double d : strengths) s.add(d);
        args.put("loraStrengths", s);
      }
      Map<String, Object> step = new LinkedHashMap<String, Object>();
      step.put("id", "edit");
      step.put("toolName", "edit_image");
      step.put("arguments", args);
      if (pictures > 0) {
        // The first upload is the picture edited; a second upload goes along as a context image.
        List<Object> deps = new ArrayList<Object>();
        Map<String, Object> d = new LinkedHashMap<String, Object>();
        d.put("sourceStepId", "$input_media");
        d.put("targetArgument", "sourceImageIndex");
        d.put("transform", "image_index");
        d.put("sourceArtifactIndex", Integer.valueOf(0));
        d.put("mediaType", "image");
        d.put("required", Boolean.TRUE);
        deps.add(d);
        step.put("dependsOn", deps);
      }
      List<Object> steps = new ArrayList<Object>();
      steps.add(step);
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      if (title != null && title.length() > 0) input.put("title", title);
      input.put("steps", steps);
      return toJson(input);
    }

    /**
     * A promptless picture upscale (upscale_image: NVIDIA RTX VSR) of the first uploaded picture:
     * `scale` 2, 3 or 4, or `targetLongestEdge` pixels (512-15360) when it is more than 0. Sogni keeps
     * the shape, aligns both edges to 8 px and returns a JPG above 7680 px.
     */
    public static String upscaleInput(String title, int scale, int targetLongestEdge) {
      Map<String, Object> args = new LinkedHashMap<String, Object>();
      args.put("sourceImageIndex", Integer.valueOf(-1));
      if (targetLongestEdge > 0) args.put("targetLongestEdge", Integer.valueOf(targetLongestEdge));
      else args.put("scale", Integer.valueOf(scale < 2 ? 2 : scale > 4 ? 4 : scale));
      Map<String, Object> step = new LinkedHashMap<String, Object>();
      step.put("id", "upscale");
      step.put("toolName", "upscale_image");
      step.put("arguments", args);
      List<Object> deps = new ArrayList<Object>();
      Map<String, Object> d = new LinkedHashMap<String, Object>();
      d.put("sourceStepId", "$input_media");
      d.put("targetArgument", "sourceImageIndex");
      d.put("transform", "image_index");
      d.put("sourceArtifactIndex", Integer.valueOf(0));
      d.put("mediaType", "image");
      d.put("required", Boolean.TRUE);
      deps.add(d);
      step.put("dependsOn", deps);
      List<Object> steps = new ArrayList<Object>();
      steps.add(step);
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      if (title != null && title.length() > 0) input.put("title", title);
      input.put("steps", steps);
      return toJson(input);
    }

    private static final java.util.regex.Pattern PROJECT_URL = java.util.regex.Pattern.compile(
      "/projects?/([0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})");

    /**
     * The Sogni projects (renders) a workflow record names: projectId / project_id / projectIds
     * values anywhere in it, and project ids in its result links (.../projects/<id>/...). The ones
     * Sogni's app deletes with DELETE /v1/projects/<id>. In the order found, each once.
     */
    public static List<String> projectIds(Object record) {
      List<String> out = new ArrayList<String>();
      collectProjects(record, null, out);
      return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectProjects(Object v, String key, List<String> out) {
      String k = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT).replace("_", "");
      boolean named = k.equals("projectid") || k.equals("projectids") || k.equals("sogniprojectid");
      if (v instanceof Map) {
        for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) collectProjects(e.getValue(), e.getKey(), out);
      } else if (v instanceof List) {
        for (Object o : (List<Object>) v) collectProjects(o, named ? key : null, out);
      } else if (v instanceof String) {
        String t = ((String) v).trim();
        if (named && t.matches("[A-Za-z0-9_-]{6,80}")) {
          if (!out.contains(t)) out.add(t);
        } else {
          java.util.regex.Matcher m = PROJECT_URL.matcher(t);
          while (m.find()) if (!out.contains(m.group(1))) out.add(m.group(1));
        }
      }
    }

    /** Deletes a Sogni project (a render and its files), as Sogni's app does: DELETE /v1/projects/<id>. */
    public void deleteProject(String id) throws IOException {
      HttpURLConnection c = this.open("DELETE", "/v1/projects/" + enc(id));
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      c.disconnect();
    }

    /**
     * Delete from Sogni after download: each project the workflow record names deleted; a line for
     * the log each. When it names none, `record` (when given) gets the record, so what Sogni sends
     * can be read, and a note says nothing was deleted.
     */
    public List<String> deleteProjects(Map<String, Object> wf, File record) {
      List<String> lines = new ArrayList<String>();
      List<String> ids = projectIds(wf);
      if (ids.isEmpty()) {
        String saved = "";
        if (record != null) {
          try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(record);
            try {
              fos.write(toJson(wf).getBytes(StandardCharsets.UTF_8));
            } finally {
              fos.close();
            }
            saved = "; Sogni's record of the run is saved as " + record.getName();
          } catch (IOException ex) {
            // the note says enough
          }
        }
        lines.add("Note: Sogni's record of this run names no project, so nothing was deleted on Sogni" + saved);
        return lines;
      }
      for (String id : ids) {
        try {
          this.deleteProject(id);
          lines.add("Deleted from Sogni: project " + id);
        } catch (IOException ex) {
          lines.add("Note: could not delete Sogni project " + id + " (" + ex.getMessage() + ")");
        }
      }
      return lines;
    }

    /** The picture results in a workflow record (its artifacts first, so an uploaded input is not taken for one). */
    public static List<Map<String, Object>> imageArtifacts(Map<String, Object> record) {
      List<Map<String, Object>> found = new ArrayList<Map<String, Object>>();
      collectMedia(record.get("artifacts"), found);
      if (found.isEmpty()) collectMedia(record, found);
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      for (Map<String, Object> m : found) {
        String ext = mediaExtension(str(m.get("url")), mimeOf(m), "");
        if (ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".webp")) out.add(m);
      }
      return out;
    }

    /** The FastH3 selector: t2v, i2v (a start frame) or flf2v (first and last frames); -2stage for 720, 1080 or 1440. */
    public static String videoModel(int pictures, int resolution) {
      String mode = pictures >= 2 ? "flf2v" : pictures == 1 ? "i2v" : "t2v";
      return "minimax-h3-fasth3-" + mode + "-turbo" + (resolution == 720 || resolution == 1080 || resolution == 1440 ? "-2stage" : "");
    }

    /** Starts a workflow from input JSON ({"steps": [...]}) and returns its id. */
    public String start(String inputJson, boolean confirmCost, double maxCost) throws IOException {
      return this.start(inputJson, confirmCost, maxCost, null, null);
    }

    /**
     * As above, with uploaded files (uploadMedia) as media_references for the steps, and a billing
     * mode ("subscription" for an Unlimited Plan; null for Sogni's default).
     */
    public String start(String inputJson, boolean confirmCost, double maxCost, List<Map<String, Object>> media, String billingMode) throws IOException {
      StringBuilder body = new StringBuilder();
      body.append("{\"input\":").append(inputJson);
      body.append(",\"token_type\":\"spark\",\"app_source\":").append(quote(APP_SOURCE));
      if (confirmCost) body.append(",\"confirm_cost\":true");
      if (maxCost > 0) body.append(",\"max_estimated_capacity_units\":").append(number(maxCost));
      if (media != null && !media.isEmpty()) body.append(",\"media_references\":").append(toJson(media));
      if (billingMode != null && billingMode.length() > 0) body.append(",\"billing_mode\":").append(quote(billingMode));
      if (noFilter) body.append(",\"safe_content_filter\":false");
      body.append('}');
      Map<String, Object> wf = workflowOf(this.request("POST", "/v1/creative-agent/workflows", body.toString()));
      String id = str(wf.get("workflowId"));
      if (id == null) id = str(wf.get("id"));
      if (id == null) throw new IOException("Sogni did not return a workflow id");
      return id;
    }

    /** The workflow record. */
    public Map<String, Object> workflow(String id) throws IOException {
      return workflowOf(this.request("GET", "/v1/creative-agent/workflows/" + enc(id), null));
    }

    /** The workflow's event list (what happened, step by step), as Sogni returns it. */
    public Object events(String id) throws IOException {
      return this.request("GET", "/v1/creative-agent/workflows/" + enc(id) + "/events", null);
    }

    // ---- Chat (Sogni Intelligence, OpenAI-style /v1/chat/completions) ----

    /** The hosted chat model Sogni's own tools use by default. */
    public static final String CHAT_MODEL = "qwen3.6-35b-a3b-gguf-iq4xs";

    /**
     * A plain text chat request: no Sogni tools, so the reply is text only. `turns` are
     * {role, text} pairs ("user" or "assistant") after the optional system text; a user turn can add
     * pictures after its text, as data: URIs (PNG or JPEG), which a vision model sees. maxTokens 0
     * leaves Sogni's default. `thinking` lets the model reason before it answers (slower, more tokens).
     */
    public static String chatInput(String model, String system, List<String[]> turns, int maxTokens, boolean thinking) {
      return chatInput(model, system, turns, maxTokens, thinking, null);
    }

    /**
     * As above, with Sogni's tool surface offered to the model ("creative-tools"), or null for none.
     * The tools are never run by the chat (sogni_tool_execution false): the model only proposes tool
     * calls (chatToolCalls), which the caller may run as a workflow (toolsInput, start) under a cost limit.
     */
    public static String chatInput(String model, String system, List<String[]> turns, int maxTokens, boolean thinking, String tools) {
      return chatInput(model, system, turns, maxTokens, thinking, tools, false);
    }

    /**
     * As above; `execute` lets Sogni run the tools inside the chat (sogni_tool_execution), with no cost
     * check here: for an Unlimited Plan, where only Sogni's fair use limits apply. The reply then names
     * the workflows it started (chatWorkflows).
     */
    public static String chatInput(String model, String system, List<String[]> turns, int maxTokens, boolean thinking, String tools, boolean execute) {
      return chatInput(model, system, turns, maxTokens, thinking, tools, execute, null);
    }

    /** As above, with uploaded files (uploadMedia) as media_references, for Sogni's tools to work on. */
    public static String chatInput(String model, String system, List<String[]> turns, int maxTokens, boolean thinking, String tools, boolean execute,
        List<Map<String, Object>> media) {
      Map<String, Object> body = new LinkedHashMap<String, Object>();
      body.put("model", model == null || model.length() == 0 ? CHAT_MODEL : model);
      body.put("messages", chatMessages(system, turns));
      if (maxTokens > 0) body.put("max_tokens", Integer.valueOf(maxTokens));
      body.put("token_type", "spark");
      body.put("app_source", APP_SOURCE);
      body.put("sogni_tools", tools == null || tools.length() == 0 ? (Object) Boolean.FALSE : tools);
      body.put("sogni_tool_execution", Boolean.valueOf(execute && tools != null && tools.length() > 0));
      Map<String, Object> kwargs = new LinkedHashMap<String, Object>();
      kwargs.put("enable_thinking", Boolean.valueOf(thinking));
      body.put("chat_template_kwargs", kwargs);
      if (media != null && !media.isEmpty()) body.put("media_references", media);
      // The filter for the chat's own check and for the tools Sogni runs in it.
      if (noFilter) body.put("safe_content_filter", Boolean.FALSE);
      return toJson(body);
    }

    /**
     * A durable chat run (POST /v1/chat/runs), for an Unlimited Plan: Sogni runs the model and its
     * tools on its side and the run goes on when the connection drops, so a long job (a video) does
     * not time out one long request (Cloudflare ends a request after about 100 s: error 524).
     * Pictures in `turns` are uploaded URLs (a run takes no data: URIs); `media` are the uploads
     * (uploadMedia), named as media references and as the run's starting media.
     */
    public static String chatRunInput(String model, String system, List<String[]> turns, int maxTokens, boolean thinking, List<Map<String, Object>> media) {
      Map<String, Object> body = new LinkedHashMap<String, Object>();
      body.put("model", model == null || model.length() == 0 ? CHAT_MODEL : model);
      body.put("messages", chatMessages(system, turns));
      Map<String, Object> sampling = new LinkedHashMap<String, Object>();
      if (maxTokens > 0) sampling.put("max_tokens", Integer.valueOf(maxTokens));
      sampling.put("think", Boolean.valueOf(thinking));
      body.put("sampling", sampling);
      body.put("token_type", "spark");
      body.put("app_source", APP_SOURCE);
      if (noFilter) {
        Map<String, Object> runtime = new LinkedHashMap<String, Object>();
        runtime.put("safeContentFilter", Boolean.FALSE);
        body.put("runtime_config", runtime);
      }
      if (media != null && !media.isEmpty()) {
        body.put("media_references", media);
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        List<Object> images = new ArrayList<Object>(), videos = new ArrayList<Object>(), audio = new ArrayList<Object>();
        for (Map<String, Object> m : media) {
          String kind = str(m.get("kind"));
          ("video".equals(kind) ? videos : "audio".equals(kind) ? audio : images).add(m.get("url"));
        }
        context.put("images", new ArrayList<Object>());
        context.put("videos", new ArrayList<Object>());
        context.put("audio", new ArrayList<Object>());
        context.put("uploadedImages", images);
        context.put("uploadedVideos", videos);
        context.put("uploadedAudio", audio);
        body.put("media_context", context);
      }
      return toJson(body);
    }

    /** OpenAI-style messages: the system text, then each turn; a turn's entries after its text are picture URLs. */
    static List<Object> chatMessages(String system, List<String[]> turns) {
      List<Object> messages = new ArrayList<Object>();
      if (system != null && system.trim().length() > 0) messages.add(message("system", system));
      for (String[] t : turns) {
        if (t.length <= 2) {
          messages.add(message(t[0], t[1]));
          continue;
        }
        // Text and pictures in one message, as OpenAI-style vision input.
        List<Object> parts = new ArrayList<Object>();
        Map<String, Object> text = new LinkedHashMap<String, Object>();
        text.put("type", "text");
        text.put("text", t[1]);
        parts.add(text);
        for (int i = 2; i < t.length; i++) {
          Map<String, Object> url = new LinkedHashMap<String, Object>();
          url.put("url", t[i]);
          Map<String, Object> image = new LinkedHashMap<String, Object>();
          image.put("type", "image_url");
          image.put("image_url", url);
          parts.add(image);
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("role", t[0]);
        m.put("content", parts);
        messages.add(m);
      }
      return messages;
    }

    static Map<String, Object> message(String role, String text) {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("role", role);
      m.put("content", text);
      return m;
    }

    /** Sends a chat request (chatInput) and returns Sogni's reply as it came. */
    public Object chat(String inputJson) throws IOException {
      return this.request("POST", "/v1/chat/completions", inputJson);
    }

    /** The reply's text (choices[0].message.content, also inside "data"), without a <think> part; null if none. */
    @SuppressWarnings("unchecked")
    public static String chatReply(Object payload) {
      if (!(payload instanceof Map)) return null;
      Map<String, Object> p = (Map<String, Object>) payload;
      Object data = p.get("data");
      if (!(p.get("choices") instanceof List) && data instanceof Map) p = (Map<String, Object>) data;
      Object choices = p.get("choices");
      if (!(choices instanceof List) || ((List<Object>) choices).isEmpty()) return null;
      Object first = ((List<Object>) choices).get(0);
      if (!(first instanceof Map)) return null;
      Object m = ((Map<String, Object>) first).get("message");
      if (!(m instanceof Map)) m = ((Map<String, Object>) first).get("delta");
      if (!(m instanceof Map)) return null;
      String text = str(((Map<String, Object>) m).get("content"));
      if (text == null) return null;
      int close = text.lastIndexOf("</think>");
      if (close >= 0) text = text.substring(close + 8);
      return text.trim();
    }

    /** "120 in, 340 out" from the reply's token counts, or null. */
    @SuppressWarnings("unchecked")
    public static String chatUsage(Object payload) {
      if (!(payload instanceof Map)) return null;
      Object u = ((Map<String, Object>) payload).get("usage");
      if (!(u instanceof Map) && ((Map<String, Object>) payload).get("data") instanceof Map) u = ((Map<String, Object>) ((Map<String, Object>) payload).get("data")).get("usage");
      if (!(u instanceof Map)) return null;
      Object inN = ((Map<String, Object>) u).get("prompt_tokens");
      Object outN = ((Map<String, Object>) u).get("completion_tokens");
      String in = inN instanceof Number ? number(((Number) inN).doubleValue()) : str(inN);
      String out = outN instanceof Number ? number(((Number) outN).doubleValue()) : str(outN);
      if (in == null && out == null) return null;
      return (in == null ? "?" : in) + " in, " + (out == null ? "?" : out) + " out";
    }

    /** The tool calls in a chat reply, as {name, arguments JSON}; empty when the model proposed none. */
    @SuppressWarnings("unchecked")
    public static List<String[]> chatToolCalls(Object payload) {
      List<String[]> out = new ArrayList<String[]>();
      if (!(payload instanceof Map)) return out;
      Map<String, Object> p = (Map<String, Object>) payload;
      if (!(p.get("choices") instanceof List) && p.get("data") instanceof Map) p = (Map<String, Object>) p.get("data");
      Object choices = p.get("choices");
      if (!(choices instanceof List) || ((List<Object>) choices).isEmpty() || !(((List<Object>) choices).get(0) instanceof Map)) return out;
      Map<String, Object> first = (Map<String, Object>) ((List<Object>) choices).get(0);
      Object m = first.get("message") instanceof Map ? first.get("message") : first.get("delta");
      if (!(m instanceof Map)) return out;
      Object calls = ((Map<String, Object>) m).get("tool_calls");
      if (calls == null) calls = ((Map<String, Object>) m).get("toolCalls");
      if (!(calls instanceof List)) return out;
      for (Object c : (List<Object>) calls) {
        if (!(c instanceof Map)) continue;
        Object fn = ((Map<String, Object>) c).get("function");
        Map<String, Object> f = fn instanceof Map ? (Map<String, Object>) fn : (Map<String, Object>) c;
        String name = str(f.get("name"));
        if (name == null) continue;
        Object args = f.get("arguments");
        out.add(new String[] {name, args == null ? "{}" : args instanceof String ? (String) args : toJson(args)});
      }
      return out;
    }

    /** The workflow ids a chat reply says Sogni started (creative_workflows), in order; empty when none. */
    @SuppressWarnings("unchecked")
    public static List<String> chatWorkflows(Object payload) {
      List<String> out = new ArrayList<String>();
      if (!(payload instanceof Map)) return out;
      Map<String, Object> p = (Map<String, Object>) payload;
      Object list = p.get("creative_workflows");
      if (list == null) list = p.get("creativeWorkflows");
      if (list == null && p.get("data") instanceof Map) {
        Map<String, Object> d = (Map<String, Object>) p.get("data");
        list = d.get("creative_workflows") != null ? d.get("creative_workflows") : d.get("creativeWorkflows");
      }
      if (!(list instanceof List)) return out;
      for (Object o : (List<Object>) list) {
        String id = o instanceof Map ? str(((Map<String, Object>) o).get("workflowId")) : str(o);
        if (id == null && o instanceof Map) id = str(((Map<String, Object>) o).get("id"));
        if (id != null && !out.contains(id)) out.add(id);
      }
      return out;
    }

    /** A workflow input that runs these tool calls ({name, arguments JSON}), one step each. */
    public static String toolsInput(String title, List<String[]> calls) {
      List<Object> steps = new ArrayList<Object>();
      for (int i = 0; i < calls.size(); i++) {
        Object args;
        try {
          args = parseJson(calls.get(i)[1]);
        } catch (RuntimeException ex) {
          args = null;
        }
        Map<String, Object> step = new LinkedHashMap<String, Object>();
        step.put("id", "step" + (i + 1));
        step.put("toolName", calls.get(i)[0]);
        step.put("arguments", args instanceof Map ? args : new LinkedHashMap<String, Object>());
        steps.add(step);
      }
      Map<String, Object> input = new LinkedHashMap<String, Object>();
      if (title != null && title.length() > 0) input.put("title", title);
      input.put("steps", steps);
      return toJson(input);
    }

    /** The chat model ids Sogni offers (/v1/models). */
    @SuppressWarnings("unchecked")
    public List<String> chatModels() throws IOException {
      Object payload = this.request("GET", "/v1/models", null);
      List<String> out = new ArrayList<String>();
      Object list = payload instanceof Map ? ((Map<String, Object>) payload).get("data") : payload;
      if (list instanceof Map) list = ((Map<String, Object>) list).get("data");
      if (list instanceof List) {
        for (Object o : (List<Object>) list) {
          String id = o instanceof Map ? str(((Map<String, Object>) o).get("id")) : str(o);
          if (id != null) out.add(id);
        }
      }
      return out;
    }

    /**
     * Follows the workflow until it stops (completed, failed, cancelled, or waiting for the user)
     * and returns its record. Reads the event stream, one long request; if that breaks, looks again
     * every 20 seconds, waiting longer when Sogni asks (429).
     */
    public Map<String, Object> waitFor(String id, long timeoutMs, Log log) throws IOException {
      long end = System.currentTimeMillis() + timeoutMs;
      String last = null;
      try {
        last = this.stream(id, end, log);
      } catch (IOException ex) {
        if (log != null) log.line("Event stream stopped (" + ex.getMessage() + "); checking every 20 s");
      }
      while (true) {
        Map<String, Object> wf;
        try {
          wf = this.workflow(id);
        } catch (ApiException ex) {
          if (ex.status != 429) throw ex;
          int wait = Math.max(ex.retryAfter, 20);
          if (log != null) log.line("Sogni asks to wait " + wait + " s");
          sleep(wait * 1000L);
          continue;
        }
        String status = str(wf.get("status"));
        if (status != null && !status.equals(last) && log != null) log.line("Status: " + status);
        last = status;
        if (isDone(status)) return wf;
        if (System.currentTimeMillis() > end) throw new IOException("Timed out; the workflow " + id + " is still " + status + " on Sogni");
        sleep(20000L);
      }
    }

    /** Starts a durable chat run (chatRunInput); returns its record (runId, status). */
    public Map<String, Object> startChatRun(String inputJson) throws IOException {
      return runOf(this.request("POST", "/v1/chat/runs", inputJson));
    }

    /** A chat run's record: status, finalResponse, artifacts, childWorkflowIds, failureReason, waiting. */
    public Map<String, Object> chatRun(String id) throws IOException {
      return runOf(this.request("GET", "/v1/chat/runs/" + enc(id), null));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> runOf(Object payload) throws IOException {
      if (!(payload instanceof Map)) throw new IOException("Unexpected reply from Sogni");
      Map<String, Object> p = (Map<String, Object>) payload;
      Object data = p.get("data");
      if (data instanceof Map && ((Map<String, Object>) data).get("run") instanceof Map) return (Map<String, Object>) ((Map<String, Object>) data).get("run");
      if (p.get("run") instanceof Map) return (Map<String, Object>) p.get("run");
      if (data instanceof Map) return (Map<String, Object>) data;
      return p;
    }

    /**
     * Follows a chat run until it stops (completed, partial_failure, failed, cancelled, or waiting
     * for the user) and returns its record. Reads the run's event stream, telling what the tools do;
     * if that breaks, looks again every 20 seconds.
     */
    public Map<String, Object> waitForRun(String id, long timeoutMs, Log log) throws IOException {
      long end = System.currentTimeMillis() + timeoutMs;
      String last = null;
      try {
        this.runStream(id, end, log);
      } catch (IOException ex) {
        if (log != null) log.line("Event stream stopped (" + ex.getMessage() + "); checking every 20 s");
      }
      while (true) {
        Map<String, Object> run;
        try {
          run = this.chatRun(id);
        } catch (ApiException ex) {
          if (ex.status != 429 && ex.status < 500) throw ex;
          int wait = Math.max(ex.retryAfter, 20);
          if (log != null) log.line("Sogni asks to wait (" + ex.getMessage() + "); again in " + wait + " s");
          sleep(wait * 1000L);
          continue;
        }
        String status = str(run.get("status"));
        if (status != null && !status.equals(last) && !isDone(status) && log != null) log.line("Status: " + status);
        last = status;
        if (isDone(status)) return run;
        if (System.currentTimeMillis() > end) throw new IOException("Timed out; the chat run " + id + " is still " + status + " on Sogni");
        sleep(20000L);
      }
    }

    /** Reads a chat run's events until it stops or the stream ends, telling each tool call and its progress. */
    @SuppressWarnings("unchecked")
    void runStream(String id, long end, Log log) throws IOException {
      HttpURLConnection c = this.open("GET", "/v1/chat/runs/" + enc(id) + "/events/stream");
      c.setRequestProperty("Accept", "text/event-stream");
      c.setReadTimeout((int) Math.max(1000L, Math.min(Integer.MAX_VALUE, end - System.currentTimeMillis())));
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
      try {
        StringBuilder data = new StringBuilder();
        String event = null;
        long shownTens = -1;
        String shownStep = null;
        String line;
        while ((line = in.readLine()) != null) {
          if (line.startsWith("event:")) {
            event = line.substring(6).trim();
            continue;
          }
          if (line.startsWith("data:")) {
            data.append(data.length() > 0 ? "\n" : "").append(line.substring(5).trim());
            continue;
          }
          if (line.length() > 0 || data.length() == 0) continue;
          Map<String, Object> ev = null;
          try {
            Object o = parseJson(data.toString());
            if (o instanceof Map) ev = (Map<String, Object>) o;
          } catch (RuntimeException ignored) {
            // Not JSON: a keep-alive or a note.
          }
          data.setLength(0);
          String name = event;
          event = null;
          if (ev == null) continue;
          String type = str(ev.get("type"));
          if (type == null) type = name;
          Map<String, Object> pay = ev.get("payload") instanceof Map ? (Map<String, Object>) ev.get("payload") : ev;
          if ("run_status".equals(type) || "run_status".equals(name)) {
            if (isDone(str(pay.get("status")))) break;
            continue;
          }
          String tool = str(pay.get("toolName"));
          if (tool == null) tool = str(pay.get("name"));
          if ("tool_call_dispatched".equals(type)) {
            if (log != null) log.line("Tool: " + (tool == null ? "started" : tool));
            shownTens = -1;
            shownStep = null;
          } else if ("tool_call_progress".equals(type)) {
            String step = str(pay.get("stepLabel"));
            if (step == null) step = str(pay.get("jobLabel"));
            if (step != null && !step.equals(shownStep)) {
              if (log != null) log.line("  " + step);
              shownStep = step;
            }
            if (pay.get("progress") instanceof Number) {
              double p = ((Number) pay.get("progress")).doubleValue();
              long pct = Math.round(p <= 1 ? p * 100 : p);
              if (pct / 10 != shownTens) {
                shownTens = pct / 10;
                Object eta = pay.get("etaSeconds");
                if (log != null) log.line("  " + pct + "%" + (eta instanceof Number ? ", about " + Math.round(((Number) eta).doubleValue()) + " s left" : ""));
              }
            }
          } else if ("tool_call_resolved".equals(type)) {
            String st = str(pay.get("status"));
            if (log != null) log.line("Tool finished" + (tool == null ? "" : ": " + tool) + (st == null ? "" : " (" + st + ")"));
          } else if ("run_waiting_for_user".equals(type) || "run_completed".equals(type) || "run_failed".equals(type)
              || "run_partial_failure".equals(type) || "run_cancelled".equals(type)) {
            break;
          }
        }
      } finally {
        in.close();
        c.disconnect();
      }
    }

    /** The run's answer: finalResponse.content, else its last assistant message; without a <think> part. Null if none. */
    @SuppressWarnings("unchecked")
    public static String runReply(Map<String, Object> run) {
      String text = null;
      if (run.get("finalResponse") instanceof Map) text = str(((Map<String, Object>) run.get("finalResponse")).get("content"));
      if ((text == null || text.trim().length() == 0) && run.get("messages") instanceof List) {
        for (Object o : (List<Object>) run.get("messages")) {
          if (!(o instanceof Map) || !"assistant".equals(str(((Map<String, Object>) o).get("role")))) continue;
          Object c = ((Map<String, Object>) o).get("content");
          if (c instanceof String && ((String) c).trim().length() > 0) text = (String) c;
        }
      }
      if (text == null) return null;
      return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    /** The question a chat run answers: its last user message's text (request.messages, else messages); null if none. */
    @SuppressWarnings("unchecked")
    public static String runQuestion(Map<String, Object> run) {
      Object list = run.get("request") instanceof Map ? ((Map<String, Object>) run.get("request")).get("messages") : null;
      if (!(list instanceof List) || ((List<Object>) list).isEmpty()) list = run.get("messages");
      if (!(list instanceof List)) return null;
      String text = null;
      for (Object o : (List<Object>) list) {
        if (!(o instanceof Map) || !"user".equals(str(((Map<String, Object>) o).get("role")))) continue;
        Object c = ((Map<String, Object>) o).get("content");
        if (c instanceof String) text = (String) c;
        if (c instanceof List) {
          for (Object part : (List<Object>) c) {
            if (part instanceof Map && "text".equals(str(((Map<String, Object>) part).get("type")))) text = str(((Map<String, Object>) part).get("text"));
          }
        }
      }
      return text == null || text.trim().length() == 0 ? null : text.trim();
    }

    /** The chat model a run used (request.model, else model); null if it does not say. */
    @SuppressWarnings("unchecked")
    public static String runModel(Map<String, Object> run) {
      String m = run.get("request") instanceof Map ? str(((Map<String, Object>) run.get("request")).get("model")) : null;
      return m != null ? m : str(run.get("model"));
    }

    /** The pictures, audio and video a chat run made (its artifacts), once each. */
    public static List<Map<String, Object>> runArtifacts(Map<String, Object> run) {
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      collectMedia(run.get("artifacts"), out);
      return out;
    }

    /** The workflow ids a chat run started (childWorkflowIds); empty when none. */
    @SuppressWarnings("unchecked")
    public static List<String> runWorkflows(Map<String, Object> run) {
      List<String> out = new ArrayList<String>();
      if (run.get("childWorkflowIds") instanceof List) {
        for (Object o : (List<Object>) run.get("childWorkflowIds")) if (str(o) != null) out.add(str(o));
      }
      return out;
    }

    /** Why a chat run stopped short: its failure reason, or what it waits for. */
    @SuppressWarnings("unchecked")
    public static String runProblem(Map<String, Object> run) {
      String why = str(run.get("failureReason"));
      if (why == null) why = str(run.get("cancellationReason"));
      if (why == null && run.get("waiting") instanceof Map) {
        Map<String, Object> w = (Map<String, Object>) run.get("waiting");
        String reason = str(w.get("reason"));
        String message = str(w.get("message"));
        why = "cost_approval_required".equals(reason) ? "it waits for a cost approval" : message != null ? message : reason != null ? "it waits: " + reason.replace('_', ' ') : null;
      }
      return why;
    }

    /** Reads the workflow's event stream until a final status or the stream ends; returns the last status seen. */
    String stream(String id, long end, Log log) throws IOException {
      HttpURLConnection c = this.open("GET", "/v1/creative-agent/workflows/" + enc(id) + "/events/stream");
      c.setRequestProperty("Accept", "text/event-stream");
      c.setReadTimeout((int) Math.max(1000L, Math.min(Integer.MAX_VALUE, end - System.currentTimeMillis())));
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      String last = null;
      java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
      try {
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
          if (line.startsWith("data:")) {
            data.append(line.substring(5).trim());
            continue;
          }
          if (line.length() > 0) continue;
          if (data.length() == 0) continue;
          String status = null;
          try {
            Object ev = parseJson(data.toString());
            if (ev instanceof Map) {
              Map<?, ?> m = (Map<?, ?>) ev;
              status = str(m.get("status"));
              if (status == null && m.get("data") instanceof Map) status = str(((Map<?, ?>) m.get("data")).get("status"));
              if (status == null && m.get("workflow") instanceof Map) status = str(((Map<?, ?>) m.get("workflow")).get("status"));
            }
          } catch (RuntimeException ignored) {
            // Not JSON: a keep-alive or a note.
          }
          data.setLength(0);
          if (status != null && !status.equals(last)) {
            if (log != null) log.line("Status: " + status);
            last = status;
          }
          if (isDone(status)) break;
        }
      } finally {
        in.close();
        c.disconnect();
      }
      return last;
    }

    public static boolean isDone(String status) {
      if (status == null) return false;
      for (String d : DONE) if (d.equals(status)) return true;
      return false;
    }

    /** Audio results anywhere in a workflow record: objects with a URL that is audio by type or extension. */
    public static List<Map<String, Object>> audioArtifacts(Object record) {
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      collectAudio(record, out);
      return out;
    }

    /** The video results in a workflow record (its artifacts first, so an uploaded input is not taken for one). */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> videoArtifacts(Map<String, Object> record) {
      List<Map<String, Object>> found = new ArrayList<Map<String, Object>>();
      collectMedia(record.get("artifacts"), found);
      if (found.isEmpty()) collectMedia(record, found);
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      for (Map<String, Object> m : found) {
        String ext = mediaExtension(str(m.get("url")), mimeOf(m), "");
        if (ext.equals(".mp4") || ext.equals(".webm") || ext.equals(".mov")) out.add(m);
      }
      return out;
    }

    /** Every picture, audio and video result in a workflow record (url plus its details), once each. */
    public static List<Map<String, Object>> mediaArtifacts(Object record) {
      List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
      collectMedia(record, out);
      return out;
    }

    @SuppressWarnings("unchecked")
    static void collectMedia(Object o, List<Map<String, Object>> out) {
      if (o instanceof Map) {
        Map<String, Object> m = (Map<String, Object>) o;
        String url = str(m.get("url"));
        if (url != null && url.startsWith("http") && mediaExtension(url, mimeOf(m), null) != null) {
          for (Map<String, Object> seen : out) if (url.equals(seen.get("url"))) return;
          out.add(m);
          return;
        }
        for (Object v : m.values()) collectMedia(v, out);
      } else if (o instanceof List) {
        for (Object v : (List<Object>) o) collectMedia(v, out);
      }
    }

    /** The MIME type a result names (mimeType, mediaType, contentType or type), or null. */
    public static String mimeOf(Map<String, Object> m) {
      for (String k : new String[] {"mimeType", "mediaType", "contentType", "type"}) {
        String v = str(m.get(k));
        if (v != null && v.indexOf('/') > 0) return v;
      }
      // A chat run's artifacts name only the kind.
      String kind = str(m.get("mediaType"));
      if ("video".equals(kind)) return "video/mp4";
      if ("image".equals(kind)) return "image/png";
      if ("audio".equals(kind)) return "audio/mpeg";
      return null;
    }

    /** A picture's, video's or audio file's extension from its URL path, else its MIME type; fallback when neither says. */
    public static String mediaExtension(String url, String mime, String fallback) {
      String path = url == null ? "" : url.toLowerCase();
      int q = path.indexOf('?');
      if (q >= 0) path = path.substring(0, q);
      for (String e : new String[] {".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".webm", ".mov", ".glb"}) if (path.endsWith(e)) return e;
      String audio = extension(url, null);
      if (audio != null) return audio;
      String t = mime == null ? "" : mime.toLowerCase();
      if (t.startsWith("image/png")) return ".png";
      if (t.startsWith("image/jpeg") || t.startsWith("image/jpg")) return ".jpg";
      if (t.startsWith("image/webp")) return ".webp";
      if (t.startsWith("image/gif")) return ".gif";
      if (t.startsWith("video/mp4")) return ".mp4";
      if (t.startsWith("video/webm")) return ".webm";
      if (t.startsWith("video/quicktime")) return ".mov";
      if (t.startsWith("audio/")) return extension(t, ".mp3");
      return fallback;
    }

    @SuppressWarnings("unchecked")
    static void collectAudio(Object o, List<Map<String, Object>> out) {
      if (o instanceof Map) {
        Map<String, Object> m = (Map<String, Object>) o;
        String url = str(m.get("url"));
        if (url != null && url.startsWith("http") && isAudio(m, url)) {
          for (Map<String, Object> seen : out) if (url.equals(seen.get("url"))) return;
          out.add(m);
          return;
        }
        for (Object v : m.values()) collectAudio(v, out);
      } else if (o instanceof List) {
        for (Object v : (List<Object>) o) collectAudio(v, out);
      }
    }

    static boolean isAudio(Map<String, Object> m, String url) {
      for (String k : new String[] {"mediaType", "mimeType", "type", "contentType"}) {
        String v = str(m.get(k));
        if (v != null && v.toLowerCase().startsWith("audio")) return true;
      }
      return extension(url, null) != null;
    }

    /** ".mp3", ".wav", ".flac", ".m4a", ".ogg" or ".aac" from the URL path or a MIME type; fallback when neither says. */
    public static String extension(String url, String fallback) {
      String path = url == null ? "" : url.toLowerCase();
      int q = path.indexOf('?');
      if (q >= 0) path = path.substring(0, q);
      for (String e : new String[] {".mp3", ".wav", ".flac", ".m4a", ".ogg", ".aac"}) if (path.endsWith(e)) return e;
      if (path.startsWith("audio/")) {
        if (path.contains("mpeg") || path.contains("mp3")) return ".mp3";
        if (path.contains("wav")) return ".wav";
        if (path.contains("flac")) return ".flac";
        if (path.contains("mp4") || path.contains("m4a")) return ".m4a";
        if (path.contains("ogg")) return ".ogg";
      }
      return fallback;
    }

    /**
     * Uploads a file to Sogni's media storage, as Sogni's own CLI does, and returns it as a
     * media_references entry: {id media_ref_<n>, kind, mime_type, url, filename, ...}. `kind` is
     * "image", "audio" or "video"; `n` counts the request's files from 1. The file is stored for the
     * hosted tools (edit_image, animate_photo, sound_to_video, video_to_video...) to read.
     */
    public Map<String, Object> uploadMedia(String kind, String mime, byte[] data, int n, String filename) throws IOException {
      String id = "media_ref_" + n;
      String jobId = "pulsekit-" + System.currentTimeMillis() + "-" + n + "-" + Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xffffffffL);
      String type = "audio".equals(kind) ? "referenceAudio" : "video".equals(kind) ? "referenceVideo" : "contextImage" + Math.min(n, 16);
      String query = "?type=" + enc(type) + "&jobId=" + enc(jobId) + "&contentType=" + enc(mime) + ("image".equals(kind) ? "&imageId=" : "&id=") + enc(id);
      String endpoint = "image".equals(kind) ? "/v1/image/" : "/v1/media/";
      String uploadUrl = storedUrl(this.request("GET", endpoint + "uploadUrl" + query, null), "uploadUrl");
      this.put(uploadUrl, mime, data);
      String url = storedUrl(this.request("GET", endpoint + "downloadUrl" + query, null), "downloadUrl");
      Map<String, Object> ref = new LinkedHashMap<String, Object>();
      ref.put("id", id);
      ref.put("source", APP_SOURCE);
      ref.put("flag", "audio".equals(kind) ? "--ref-audio" : "video".equals(kind) ? "--ref-video" : "-c/--context");
      ref.put("kind", kind);
      ref.put("mime_type", mime);
      ref.put("url", url);
      ref.put("filename", filename);
      ref.put("byte_length", Integer.valueOf(data.length));
      ref.put("prompt_label", filename);
      Map<String, Object> storage = new LinkedHashMap<String, Object>();
      storage.put("jobId", jobId);
      storage.put("type", type);
      ref.put("storage", storage);
      return ref;
    }

    /** The uploadUrl or downloadUrl in Sogni's reply (also inside "data"). */
    @SuppressWarnings("unchecked")
    static String storedUrl(Object payload, String key) throws IOException {
      if (payload instanceof Map) {
        Map<String, Object> p = (Map<String, Object>) payload;
        String v = str(p.get(key));
        if (v == null && p.get("data") instanceof Map) v = str(((Map<String, Object>) p.get("data")).get(key));
        if (v != null && v.length() > 0) return v;
      }
      throw new IOException("Sogni did not return " + key + " for the upload");
    }

    /** Sends a file to a signed upload URL (no key: the URL carries its own permission). */
    void put(String url, String mime, byte[] data) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
      c.setRequestMethod("PUT");
      c.setConnectTimeout(this.timeoutMs);
      c.setReadTimeout(Math.max(this.timeoutMs, 300000));
      c.setDoOutput(true);
      c.setFixedLengthStreamingMode(data.length);
      c.setRequestProperty("Content-Type", mime);
      if (url.startsWith(this.base + "/")) this.authorize(c);
      OutputStream out = c.getOutputStream();
      try {
        out.write(data);
      } finally {
        out.close();
      }
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      c.disconnect();
    }

    /** Downloads a result URL (signed; no key is sent to hosts other than the API). */
    public byte[] download(String url) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
      c.setConnectTimeout(this.timeoutMs);
      c.setReadTimeout(this.timeoutMs);
      if (url.startsWith(this.base + "/")) this.authorize(c);
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      try {
        return readAll(c.getInputStream());
      } finally {
        c.disconnect();
      }
    }

    /** The error a stopped workflow reports, or its waiting reason. */
    public static String problem(Map<String, Object> wf) {
      String status = str(wf.get("status"));
      if ("waiting_for_user".equals(status)) {
        String why = str(wf.get("waitingReason"));
        if (Boolean.TRUE.equals(wf.get("awaitingCostApproval")) || "cost_approval_required".equals(why)) {
          return "Sogni wants the cost approved. Run again with --confirm_cost.";
        }
        if ("safety_review_required".equals(why)) {
          return "Sogni's safety check paused this run (safety_review_required)" + (noFilter ? "" : ": try another picture or prompt, or run again with --no_filter (Content filter off)");
        }
        return "Sogni is waiting for input: " + (why == null ? "unknown reason" : why);
      }
      List<String> why = new ArrayList<String>();
      reasons(wf, why);
      if (!why.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < why.size() && i < 3; i++) sb.append(i == 0 ? "" : "; ").append(why.get(i));
        return sb.toString();
      }
      return status == null ? "no status" : "workflow " + status;
    }

    /**
     * Error text anywhere in a record or event list: values of error, lastError, failureReason,
     * errorMessage and reason, or the message beside them. The workflow's own "workflow failed"
     * status says nothing, so the failed step's words are what count.
     */
    @SuppressWarnings("unchecked")
    public static void reasons(Object o, List<String> out) {
      if (o instanceof Map) {
        Map<String, Object> m = (Map<String, Object>) o;
        for (Map.Entry<String, Object> e : m.entrySet()) {
          String k = e.getKey().toLowerCase();
          Object v = e.getValue();
          boolean errorKey = k.equals("error") || k.equals("lasterror") || k.equals("failurereason") || k.equals("errormessage")
              || k.equals("reason") || k.equals("failure");
          if (errorKey && v instanceof String) addReason(out, (String) v);
          else if (errorKey && v instanceof Map) {
            Map<String, Object> em = (Map<String, Object>) v;
            String msg = str(em.get("message"));
            String code = str(em.get("code"));
            if (msg == null) msg = str(em.get("errorMessage"));
            if (msg != null) addReason(out, code != null && !msg.contains(code) ? msg + " (" + code + ")" : msg);
          }
        }
        String status = str(m.get("status"));
        if (status != null && (status.contains("fail") || status.contains("error")) && m.get("message") instanceof String) addReason(out, (String) m.get("message"));
        for (Object v : m.values()) if (v instanceof Map || v instanceof List) reasons(v, out);
      } else if (o instanceof List) {
        for (Object v : (List<Object>) o) reasons(v, out);
      }
    }

    static void addReason(List<String> out, String s) {
      String t = s == null ? "" : s.trim();
      if (t.length() == 0 || t.equalsIgnoreCase("workflow failed") || out.contains(t)) return;
      out.add(t);
    }

    // ---- HTTP ----

    Object request(String method, String path, String body) throws IOException {
      HttpURLConnection c = this.open(method, path);
      if (body != null) {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream out = c.getOutputStream();
        try {
          out.write(body.getBytes(StandardCharsets.UTF_8));
        } finally {
          out.close();
        }
      }
      int code = c.getResponseCode();
      if (code / 100 != 2) throw failure(c, code);
      try {
        String text = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
        return text.trim().length() == 0 ? new LinkedHashMap<String, Object>() : parseJson(text);
      } finally {
        c.disconnect();
      }
    }

    HttpURLConnection open(String method, String path) throws IOException {
      HttpURLConnection c = (HttpURLConnection) new URL(this.base + path).openConnection();
      c.setRequestMethod(method);
      c.setConnectTimeout(this.timeoutMs);
      c.setReadTimeout(this.timeoutMs);
      c.setRequestProperty("Accept", "application/json");
      this.authorize(c);
      return c;
    }

    void authorize(HttpURLConnection c) {
      c.setRequestProperty("Authorization", "Bearer " + this.apiKey);
      c.setRequestProperty("api-key", this.apiKey);
    }

    static ApiException failure(HttpURLConnection c, int code) {
      String text = "";
      try {
        InputStream err = c.getErrorStream();
        if (err != null) text = new String(readAll(err), StandardCharsets.UTF_8);
      } catch (IOException ignored) {
        // The status alone still says what went wrong.
      }
      String message = null;
      try {
        Object p = parseJson(text);
        if (p instanceof Map) {
          message = str(((Map<?, ?>) p).get("message"));
          Object e = ((Map<?, ?>) p).get("error");
          if (message == null && e instanceof Map) message = str(((Map<?, ?>) e).get("message"));
        }
      } catch (RuntimeException ignored) {
        if (text.trim().length() > 0 && text.length() < 300) message = text.trim();
      }
      int retry = 0;
      try {
        String ra = c.getHeaderField("Retry-After");
        if (ra != null) retry = Integer.parseInt(ra.trim());
      } catch (NumberFormatException ignored) {
        retry = 60;
      }
      String what = code == 401 ? "Sogni refused the API key (401)" : code == 429 ? "Sogni rate limit (429)" : "Sogni API error " + code;
      c.disconnect();
      return new ApiException(message == null ? what : what + ": " + message, code, retry);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> workflowOf(Object payload) throws IOException {
      if (!(payload instanceof Map)) throw new IOException("Unexpected reply from Sogni");
      Map<String, Object> p = (Map<String, Object>) payload;
      Object data = p.get("data");
      if (data instanceof Map && ((Map<String, Object>) data).get("workflow") instanceof Map) return (Map<String, Object>) ((Map<String, Object>) data).get("workflow");
      if (p.get("workflow") instanceof Map) return (Map<String, Object>) p.get("workflow");
      if (data instanceof Map) return (Map<String, Object>) data;
      return p;
    }

    static String enc(String s) {
      try {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
      } catch (java.io.UnsupportedEncodingException e) {
        return s;
      }
    }

    static byte[] readAll(InputStream in) throws IOException {
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
      } finally {
        in.close();
      }
    }

    static void sleep(long ms) throws IOException {
      try {
        Thread.sleep(ms);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted");
      }
    }

    static String str(Object o) {
      return o == null ? null : String.valueOf(o);
    }

    // ---- JSON: just what these calls need ----

    public static String quote(String s) {
      StringBuilder sb = new StringBuilder("\"");
      for (int i = 0; i < s.length(); i++) {
        char ch = s.charAt(i);
        if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
        else if (ch == '\n') sb.append("\\n");
        else if (ch == '\r') sb.append("\\r");
        else if (ch == '\t') sb.append("\\t");
        else if (ch < 0x20) sb.append(String.format("\\u%04x", Integer.valueOf(ch)));
        else sb.append(ch);
      }
      return sb.append('"').toString();
    }

    static String number(double d) {
      return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf((long) d) : String.valueOf(d);
    }

    @SuppressWarnings("unchecked")
    public static String toJson(Object o) {
      if (o == null) return "null";
      if (o instanceof String) return quote((String) o);
      if (o instanceof Double || o instanceof Float) return number(((Number) o).doubleValue());
      if (o instanceof Number || o instanceof Boolean) return String.valueOf(o);
      StringBuilder sb = new StringBuilder();
      if (o instanceof Map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
          if (!first) sb.append(',');
          first = false;
          sb.append(quote(e.getKey())).append(':').append(toJson(e.getValue()));
        }
        return sb.append('}').toString();
      }
      if (o instanceof List) {
        sb.append('[');
        boolean first = true;
        for (Object v : (List<Object>) o) {
          if (!first) sb.append(',');
          first = false;
          sb.append(toJson(v));
        }
        return sb.append(']').toString();
      }
      return quote(String.valueOf(o));
    }

    /** Maps, lists, strings, doubles, booleans and nulls. Throws IllegalArgumentException on bad JSON. */
    public static Object parseJson(String text) {
      int[] at = {0};
      Object v = value(text, at);
      skip(text, at);
      if (at[0] != text.length()) throw new IllegalArgumentException("Trailing text in JSON at " + at[0]);
      return v;
    }

    static Object value(String s, int[] at) {
      skip(s, at);
      if (at[0] >= s.length()) throw new IllegalArgumentException("JSON ended early");
      char ch = s.charAt(at[0]);
      if (ch == '{') {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        at[0]++;
        skip(s, at);
        if (s.charAt(at[0]) == '}') {
          at[0]++;
          return m;
        }
        while (true) {
          skip(s, at);
          String k = string(s, at);
          skip(s, at);
          expect(s, at, ':');
          m.put(k, value(s, at));
          skip(s, at);
          if (s.charAt(at[0]) == ',') {
            at[0]++;
            continue;
          }
          expect(s, at, '}');
          return m;
        }
      }
      if (ch == '[') {
        List<Object> l = new ArrayList<Object>();
        at[0]++;
        skip(s, at);
        if (s.charAt(at[0]) == ']') {
          at[0]++;
          return l;
        }
        while (true) {
          l.add(value(s, at));
          skip(s, at);
          if (s.charAt(at[0]) == ',') {
            at[0]++;
            continue;
          }
          expect(s, at, ']');
          return l;
        }
      }
      if (ch == '"') return string(s, at);
      if (s.startsWith("true", at[0])) {
        at[0] += 4;
        return Boolean.TRUE;
      }
      if (s.startsWith("false", at[0])) {
        at[0] += 5;
        return Boolean.FALSE;
      }
      if (s.startsWith("null", at[0])) {
        at[0] += 4;
        return null;
      }
      int start = at[0];
      while (at[0] < s.length() && "+-0123456789.eE".indexOf(s.charAt(at[0])) >= 0) at[0]++;
      if (start == at[0]) throw new IllegalArgumentException("Bad JSON at " + start);
      return Double.valueOf(s.substring(start, at[0]));
    }

    static String string(String s, int[] at) {
      expect(s, at, '"');
      StringBuilder sb = new StringBuilder();
      while (at[0] < s.length()) {
        char ch = s.charAt(at[0]++);
        if (ch == '"') return sb.toString();
        if (ch != '\\') {
          sb.append(ch);
          continue;
        }
        char e = s.charAt(at[0]++);
        if (e == 'n') sb.append('\n');
        else if (e == 't') sb.append('\t');
        else if (e == 'r') sb.append('\r');
        else if (e == 'b') sb.append('\b');
        else if (e == 'f') sb.append('\f');
        else if (e == 'u') {
          sb.append((char) Integer.parseInt(s.substring(at[0], at[0] + 4), 16));
          at[0] += 4;
        } else sb.append(e);
      }
      throw new IllegalArgumentException("Unterminated JSON string");
    }

    static void skip(String s, int[] at) {
      while (at[0] < s.length() && Character.isWhitespace(s.charAt(at[0]))) at[0]++;
    }

    static void expect(String s, int[] at, char ch) {
      if (at[0] >= s.length() || s.charAt(at[0]) != ch) throw new IllegalArgumentException("Expected " + ch + " in JSON at " + at[0]);
      at[0]++;
    }

    /** MiniMax H3 video LoRAs Sogni offers (October 2026): id, then the name its app shows. */
    public static final String[][] H3_LORAS = {
      {"h3-mystic-xxx-v4", "Mystic X v4"},
      {"h3-vbvr-video-reasoning", "VBVR Video Reasoning"},
      {"h3-better-motion", "Better Motion"},
      {"h3-natural-face-speech", "Natural Face & Speech"},
      {"h3-combat-base-v2", "Combat Base V2"},
    };

    /** Krea 2 LoRAs Sogni offers (October 2026): id, then the name its app shows. */
    public static final String[][] KREA2_LORAS = {
      {"krea2-mystic-x", "Mystic X"}, {"krea2-realism-engine", "Realism Engine v3"}, {"krea2-skin-detail", "Skin Detail"},
      {"krea2-breast", "Chest Size"}, {"krea2-weight", "Weight"}, {"krea2-height", "Height"}, {"krea2-age", "Age"},
      {"krea2-hourglass-figure", "Figure"}, {"krea2-chest-firmness", "Natural Sag → Firm"}, {"krea2-filter-bypass-2", "Krea2FilterBypass 2vector"},
      {"krea2-filter-bypass-3", "Krea2FilterBypass 3vector"}, {"krea2-detail-enhancer", "Detail Enhancer"}, {"krea2-amateur", "Professional ↔ Amateur"},
      {"krea2-candid", "Editorial ↔ Candid"}, {"krea2-realism", "Illustrated ↔ Realistic"}, {"krea2-bloomgirls", "BloomGirls UltraRealism"},
      {"krea2-aberrant", "Aberrant"}, {"krea2-afterlight", "Afterlight"}, {"krea2-purple-grainy", "Purple Grainy"},
      {"krea2-scene-complexity", "Scene Complexity"}, {"krea2-skin-tone", "Skin Tone"}, {"krea2-warm-light", "Warm Light"},
      {"krea2-wetness", "Wetness"}, {"krea2-zoom", "Zoom"}, {"krea2-nipple-projection", "Nipple Flat → Protruding"},
    };

    /**
     * A LoRA list as typed in Params: entries separated by commas, semicolons or new lines, each an
     * id with its strength (h3-better-motion:0.6) or the name Sogni's app shows (Better Motion 0.6,
     * or Better Motion: 0.6). Several id:strength pairs may also share an entry, separated by spaces.
     * Each result is {id, strength}: a name in `known` (any case, symbols ignored) becomes its id,
     * anything else is kept as typed for the program to check; the strength is null when not given.
     */
    public static List<String[]> loraList(String text, String[][] known) {
      List<String[]> out = new ArrayList<String[]>();
      if (text == null) return out;
      for (String entry : text.split("[,;\\r\\n]+")) {
        String e = entry.trim();
        if (e.length() == 0) continue;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.*?)(?:\\s*:\\s*|\\s+)(-?\\d*\\.?\\d+)$").matcher(e);
        String name = m.matches() ? m.group(1).trim() : e;
        String strength = m.matches() ? m.group(2) : null;
        String id = loraId(name, known);
        if (id != null) {
          out.add(new String[] {id, strength});
          continue;
        }
        if (e.matches("[A-Za-z0-9][A-Za-z0-9._]*-[A-Za-z0-9._-]*(:-?\\d*\\.?\\d+)?(\\s+[A-Za-z0-9][A-Za-z0-9._]*-[A-Za-z0-9._-]*(:-?\\d*\\.?\\d+)?)+")) {
          // id:strength pairs separated by spaces (ids have hyphens; an unknown name is kept whole for the program to refuse).
          for (String token : e.split("\\s+")) {
            int colon = token.lastIndexOf(':');
            String tid = colon > 0 ? token.substring(0, colon) : token;
            String found = loraId(tid, known);
            out.add(new String[] {found != null ? found : tid, colon > 0 ? token.substring(colon + 1) : null});
          }
          continue;
        }
        out.add(new String[] {name, strength});
      }
      return out;
    }

    /** The id for a LoRA's id or shown name in `known` (any case, symbols and spaces ignored), or null. */
    public static String loraId(String name, String[][] known) {
      String key = loraKey(name);
      if (key.length() == 0 || known == null) return null;
      for (String[] k : known) {
        if (loraKey(k[0]).equals(key) || loraKey(k[1]).equals(key)) return k[0];
      }
      return null;
    }

    static String loraKey(String s) {
      return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    /** "Better Motion (h3-better-motion), ..." for a message: the names `known` gives. */
    public static String loraNames(String[][] known) {
      StringBuilder sb = new StringBuilder();
      for (String[] k : known) sb.append(sb.length() > 0 ? ", " : "").append(k[1]).append(" (").append(k[0]).append(')');
      return sb.toString();
    }
    // --- SogniApi end ---
  }
}
