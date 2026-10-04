import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SogniMusic: music from a text description, made on Sogni's GPU network (generate_music).
 *
 * The API key comes from SOGNI_API_KEY, from --key_file (a text file with SOGNI_API_KEY=... or the
 * key alone), or from ~/.config/sogni/credentials. On the phone, choose the key file in Params:
 * PyJav copies it into its own folder. Each run spends Sogni credit (Spark) on your account.
 *
 * The default model is turbo (ACE-Step): quick, cheap, and it keeps the exact BPM, key and time
 * signature, which suits drum work. music3 (MiniMax Music 3) sings better but costs about 20x.
 *
 * --genre names the style (any name; PyJav's Params lists Pulsekit's style database, and PyJav
 * passes the app's current style when none is given). It leads the prompt.
 *
 * --saveprompt also writes the final prompt as sogni-<genre>.prompt: a Pulsekit prompt sheet
 * (category Music, type AI) that opens in PyJav and the Prompts page. It is written before the key
 * is checked, so a prompt can be exported without one.
 *
 * --drums_only and --instruments write the instrumentation into the prompt ("drums only, no bass,
 * no melody..."). Sogni has no stem or negative-prompt control, so this steers the model rather
 * than guaranteeing it; DrumMidi_CRT can still pull the drum hits out of a fuller mix.
 */
public final class SogniMusic {
  public static void main(String[] args) throws Exception {
    int code = run(args);
    // Inside Pulsekit (PyJav on the phone runs programs in the app's own process, and sets
    // pulsekit.work) System.exit would close the app, so only a separate run exits with the code.
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  /** The program; returns its exit code (0 ok, 1 failed, 2 bad arguments). */
  static int run(String[] args) throws Exception {
    String out = null;
    String prompt = null;
    String keyFile = null;
    String keyscale = null;
    String model = "turbo";
    String lyrics = null;
    String apiBase = null;
    double bpm = 0;
    double duration = 30;
    int timesig = 0;
    double maxCost = 0;
    boolean confirm = false;
    boolean drumsOnly = false;
    String instruments = null;
    String genre = null;
    boolean savePrompt = false;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--prompt") && i + 1 < args.length) prompt = args[++i];
      else if (a.equals("--bpm") && i + 1 < args.length) bpm = Double.parseDouble(args[++i]);
      else if (a.equals("--duration") && i + 1 < args.length) duration = Double.parseDouble(args[++i]);
      else if (a.equals("--keyscale") && i + 1 < args.length) keyscale = args[++i];
      else if (a.equals("--timesig") && i + 1 < args.length) timesig = Integer.parseInt(args[++i]);
      else if (a.equals("--model") && i + 1 < args.length) model = args[++i];
      else if (a.equals("--lyrics") && i + 1 < args.length) lyrics = args[++i];
      else if (a.equals("--key_file") && i + 1 < args.length) keyFile = args[++i];
      else if (a.equals("--max_cost") && i + 1 < args.length) maxCost = Double.parseDouble(args[++i]);
      else if (a.equals("--api_base") && i + 1 < args.length) apiBase = args[++i];
      else if (a.equals("--confirm_cost")) confirm = true;
      else if (a.equals("--drums_only")) drumsOnly = true;
      else if (a.equals("--instruments") && i + 1 < args.length) instruments = args[++i];
      else if (a.equals("--genre") && i + 1 < args.length) genre = args[++i];
      else if (a.equals("--saveprompt")) savePrompt = true;
      else if (a.equals("-h") || a.equals("--help")) {
        usage();
        return 0;
      } else if (!a.startsWith("--") && out == null) out = a;
      else {
        System.out.println("Unknown argument: " + a);
        usage();
        return 2;
      }
    }
    if (drumsOnly && instruments != null && instruments.trim().length() > 0) {
      System.out.println("Failed: use --drums_only or --instruments, not both (for drums with other instruments: --instruments \"drums, bass\")");
      return 2;
    }
    if (drumsOnly && lyrics != null && lyrics.trim().length() > 0) {
      System.out.println("Failed: --drums_only makes a track without vocals, so leave out --lyrics");
      return 2;
    }
    boolean steered = drumsOnly || (instruments != null && instruments.trim().length() > 0) || (genre != null && genre.trim().length() > 0);
    if ((prompt == null || prompt.trim().length() == 0) && !steered) {
      System.out.println("Failed: give --prompt, --genre, --drums_only or --instruments, for example --prompt \"funk groove, slap bass, tight drums\"");
      usage();
      return 2;
    }
    prompt = musicPrompt(prompt, genre, drumsOnly, instruments, lyrics != null && lyrics.trim().length() > 0);
    if (savePrompt) {
      File sheet = savePrompt(promptName(genre), "Sogni " + model, prompt);
      System.out.println(sheet == null ? "Could not save the prompt" : "Saved prompt " + sheet.getPath());
    }
    if (!"turbo".equals(model) && !"sft".equals(model) && !"music3".equals(model)) {
      System.out.println("Failed: --model is turbo, sft or music3");
      return 2;
    }
    if (duration < 10 || duration > 600) {
      System.out.println("Failed: --duration is 10 to 600 seconds");
      return 2;
    }
    String key = SogniApi.findKey(keyFile);
    if (key == null) {
      System.out.println("Failed: no Sogni API key. Set SOGNI_API_KEY, or give --key_file with a text file holding SOGNI_API_KEY=<your key>."
          + " Get the key at https://dashboard.sogni.ai (account menu).");
      return 1;
    }
    SogniApi api = new SogniApi(apiBase, key);
    String input = SogniApi.musicInput("Pulsekit music", prompt, duration, bpm, keyscale, timesig, model, lyrics);
    System.out.println("Music: " + prompt);
    System.out.println("Model " + model + ", " + SogniApi.number(duration) + " s"
        + (bpm > 0 ? ", " + SogniApi.number(bpm) + " BPM" : "") + (keyscale != null ? ", " + keyscale : "")
        + (timesig > 0 ? ", " + timesig + "/4" : ""));
    try {
      String id = api.start(input, confirm, maxCost);
      System.out.println("Workflow: " + id);
      Map<String, Object> wf = api.waitFor(id, 15 * 60 * 1000L, new SogniApi.Log() {
        public void line(String s) {
          System.out.println(s);
        }
      });
      String status = SogniApi.str(wf.get("status"));
      List<Map<String, Object>> audio = SogniApi.audioArtifacts(wf);
      if (audio.isEmpty()) {
        System.out.println("Failed: " + SogniApi.problem(wf));
        return 1;
      }
      String url = SogniApi.str(audio.get(0).get("url"));
      String mime = SogniApi.str(audio.get(0).get("mimeType"));
      String ext = SogniApi.extension(url, SogniApi.extension(mime, ".mp3"));
      File file = new File(out != null ? out : "sogni_music" + ext);
      // The file is named for what Sogni sent (an .mp3 is not written as .wav), and nothing is overwritten.
      String given = SogniApi.extension(file.getName(), null);
      if (given != null && !given.equals(ext)) file = new File(file.getPath().substring(0, file.getPath().length() - given.length()) + ext);
      String stem = file.getPath().substring(0, file.getPath().length() - (SogniApi.extension(file.getName(), null) == null ? 0 : ext.length()));
      for (int n = 1; file.exists(); n++) file = new File(stem + "(" + n + ")" + ext);
      byte[] data = api.download(url);
      FileOutputStream fos = new FileOutputStream(file);
      try {
        fos.write(data);
      } finally {
        fos.close();
      }
      System.out.println("Wrote " + file.getPath() + " (" + (data.length / 1024) + " KB)");
      if (!"completed".equals(status)) System.out.println("Note: workflow " + status);
      System.out.println("Succeeded: " + file.getPath());
    } catch (SogniApi.ApiException ex) {
      System.out.println("Failed: " + ex.getMessage());
      return 1;
    } catch (IOException ex) {
      System.out.println("Failed: " + ex.getMessage());
      return 1;
    }
    return 0;
  }

  static void usage() {
    System.out.println("Usage: java SogniMusic [output.mp3] [--prompt text] [--genre style] [--saveprompt] [--drums_only] [--instruments list] [--bpm N] [--duration seconds] "
        + "[--keyscale key] [--timesig N] [--model turbo|sft|music3] [--lyrics text] [--key_file credentials.txt] [--confirm_cost] [--max_cost N]");
  }

  /** "sogni-" and the genre, spaces and symbols as hyphens ("Deep House" → sogni-Deep-House). */
  static String promptName(String genre) {
    String g = genre == null ? "" : genre.trim().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
    return "sogni-" + (g.length() == 0 ? "music" : g);
  }

  /**
   * Writes `prompt` as a Pulsekit prompt sheet (PKPROMPT1, as PromptRun.encode writes it):
   * name, category Music, the model, type AI, then the prompt. Never over an existing file.
   */
  static File savePrompt(String name, String model, String prompt) {
    StringBuilder sb = new StringBuilder();
    sb.append("PKPROMPT1\n").append(name).append("\n\n\n\n\n");
    sb.append("Category: Music\n");
    sb.append("Model: ").append(model).append('\n');
    sb.append("Reference file 1: \n");
    sb.append("Reference file 2: \n");
    sb.append("Type: ai\n");
    sb.append("---\n");
    sb.append(prompt);
    File file = new File(name + ".prompt");
    for (int n = 1; file.exists(); n++) file = new File(name + "(" + n + ").prompt");
    try {
      FileOutputStream fos = new FileOutputStream(file);
      try {
        fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
      } finally {
        fos.close();
      }
      return file;
    } catch (IOException ex) {
      return null;
    }
  }

  /**
   * The prompt sent to Sogni: the genre and style description, then the instrumentation.
   * --drums_only asks for a solo drum kit; --instruments "bass, rhodes" for those instruments
   * alone. Without lyrics the track is asked to be instrumental.
   */
  static String musicPrompt(String prompt, String genre, boolean drumsOnly, String instruments, boolean lyrics) {
    StringBuilder sb = new StringBuilder();
    String g = genre == null ? "" : genre.trim();
    String style = prompt == null ? "" : prompt.trim();
    if (drumsOnly) {
      sb.append("Solo drum kit, drums only");
      if (g.length() > 0) sb.append(", ").append(g).append(" drum pattern");
      if (style.length() > 0) sb.append(", ").append(style);
      else if (g.length() == 0) sb.append(", tight groove");
      sb.append(". Kick, snare, hi-hat and cymbals only: no bass, no guitar, no keys, no synths, no melody, no chords, no vocals. Dry, clear drum recording.");
      return sb.toString();
    }
    if (g.length() > 0) style = style.length() > 0 ? g + ", " + style : g;
    List<String> list = new ArrayList<String>();
    if (instruments != null) {
      for (String part : instruments.split("[,;/]")) {
        String t = part.trim();
        if (t.length() > 0) list.add(t);
      }
    }
    if (list.isEmpty()) return style;
    if (style.length() > 0) sb.append(style).append(". ");
    sb.append(lyrics ? "Only these instruments: " : "Instrumental, only these instruments: ");
    for (int i = 0; i < list.size(); i++) {
      if (i > 0) sb.append(i == list.size() - 1 ? " and " : ", ");
      sb.append(list.get(i));
    }
    sb.append(". No other instruments");
    if (!lyrics) sb.append(", no vocals");
    sb.append('.');
    return sb.toString();
  }

  /** Sogni's hosted API: a copy of shared/src/pulsekit/SogniApi.java (android/build.sh checks they match). */
  static final class SogniApi {
    // --- SogniApi begin ---
    public static final String BASE = "https://api.sogni.ai";
    public static final String APP_SOURCE = "pulsekit";
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

    /**
     * The API key: SOGNI_API_KEY in the environment, else SOGNI_API_KEY=... in keyFile (when
     * given), else in ~/.config/sogni/credentials. Null when none is found.
     */
    public static String findKey(String keyFile) {
      String env = System.getenv("SOGNI_API_KEY");
      if (env != null && env.trim().length() > 0) return env.trim();
      List<File> files = new ArrayList<File>();
      if (keyFile != null && keyFile.length() > 0) files.add(new File(keyFile));
      String home = System.getProperty("user.home");
      if (home != null) files.add(new File(home, ".config/sogni/credentials"));
      for (File f : files) {
        if (!f.isFile()) continue;
        try {
          String text = new String(readAll(new java.io.FileInputStream(f)), StandardCharsets.UTF_8);
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
        } catch (IOException ignored) {
          // Try the next place.
        }
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

    /** Starts a workflow from input JSON ({"steps": [...]}) and returns its id. */
    public String start(String inputJson, boolean confirmCost, double maxCost) throws IOException {
      StringBuilder body = new StringBuilder();
      body.append("{\"input\":").append(inputJson);
      body.append(",\"token_type\":\"spark\",\"app_source\":").append(quote(APP_SOURCE));
      if (confirmCost) body.append(",\"confirm_cost\":true");
      if (maxCost > 0) body.append(",\"max_estimated_capacity_units\":").append(number(maxCost));
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
        return "Sogni is waiting for input: " + (why == null ? "unknown reason" : why);
      }
      Object err = wf.get("error");
      if (err instanceof Map) {
        String msg = str(((Map<?, ?>) err).get("message"));
        if (msg != null) return msg;
      }
      if (err != null) return String.valueOf(err);
      return status == null ? "no status" : "workflow " + status;
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
    // --- SogniApi end ---
  }
}
