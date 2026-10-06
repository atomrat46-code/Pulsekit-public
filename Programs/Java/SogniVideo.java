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
 * SogniVideo: a video clip made with MiniMax H3 FastH3 on Sogni's GPU network, with native sound.
 *
 * --image animates a picture (PNG, JPEG or WebP): it is the first frame, and --prompt says what
 * happens next (the motion, the camera, the sound), not what the picture already shows. With
 * --end_image as well, the clip moves from the first picture to the second. Without --image the
 * clip is made from the prompt alone. The pictures are uploaded to Sogni's media storage at full
 * size first.
 *
 * --duration is 5 to 15 seconds (H3 makes 5.17 to 15.08 s on its own frame grid, so a length is
 * rounded to the nearest one it makes). --resolution 768 (the default) is FastH3's own canvas;
 * 720, 1080 or 1440 (2K) use the two-stage engine, which renders a canvas and delivers it at twice
 * the size, for a higher price. --aspect (16:9, 9:16, 1:1...) changes the shape; leave it out to
 * keep the picture's own. --no_audio makes a silent clip. --exact_prompt sends the prompt as
 * written; otherwise Sogni shapes it for the model first.
 *
 * The API key is found as SogniMusic finds it: SOGNI_API_KEY, --key_file, the key file in File >
 * Drum Midi Settings, or ~/.config/sogni/credentials. A run spends Sogni credit (Spark): about 4
 * Spark a second at 768p and 720, 10 at 1080, 16 at 2K. --max_cost caps it in capacity units and
 * --confirm_cost confirms the charge. --unlimited is for a Sogni Unlimited Plan: the subscription
 * pays, and only Sogni's daily and monthly fair use limits apply.
 *
 * --saveprompt also writes the prompt as sogni-video-<first words>.prompt: a Pulsekit prompt sheet
 * (category video, type AI) that opens in PyJav and the Prompts page, with the pictures' names as
 * its reference files (1 the first frame, 2 the last). A line of the settings
 * (duration, resolution, shape, sound, the pictures) follows the prompt. It is written before the
 * key is checked, so a prompt can be kept without one.
 *
 * The clip is saved as sogni-video-<first words>.mp4 (or the output name given), which lands in
 * Downloads on the phone. --workflow <id> downloads the clip of a run that already finished (the id
 * is printed as "Workflow: ..."), without starting or paying for a new one.
 *
 * --join video.mp4 joins the clip with another MP4 when the run succeeds: the clip first, then the
 * other video, saved beside it as <clip name>-merged.mp4 (no re-encoding: the two pictures must be
 * in the same format, as two Sogni clips from the same model are). The clip is kept as it is.
 */
public final class SogniVideo {
  public static void main(String[] args) throws Exception {
    int code = run(args);
    // Inside Pulsekit (PyJav on the phone runs programs in the app's own process, and sets
    // pulsekit.work) System.exit would close the app, so only a separate run exits with the code.
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  /** Printed first, so a run's log shows which SogniVideo ran. */
  static final String VERSION = "SogniVideo 2026-10-06";

  /** The largest picture uploaded (Sogni's own limit: 100 MB). */
  static final int UPLOAD_MAX = 100 * 1024 * 1024;

  /**
   * The work folder this run writes in, read when the run starts. On the phone PyJav runs programs
   * inside the app and gives each run its own folder through pulsekit.work, one setting for the
   * whole app: a run still waiting when the next one starts would otherwise save into that one's
   * folder, and its file would be reported by the wrong run.
   */
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

  /**
   * Prints a line of the log. Errors and warnings (Failed, Note, Could not...) are also kept for the
   * saved prompt sheet's Result text.
   */
  static void say(String line) {
    System.out.println(line);
    String t = line == null ? "" : line.trim();
    if (said != null && (t.startsWith("Failed") || t.startsWith("Note") || t.startsWith("Could not") || t.startsWith("Sogni's fair use")
        || t.startsWith("Unknown argument"))) {
      said.append(t).append('\n');
    }
  }

  /**
   * Ends the sheet --saveprompt wrote with what the run made: "Result file: <the track or clip>",
   * and "Result text:" with the errors and warnings, or the run's last words when it made no file.
   * PromptRun.parse reads them back off the prompt.
   */
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
    String image = null;
    String endImage = null;
    String keyFile = null;
    String apiBase = null;
    String workflowId = null;
    String aspect = null;
    String resolutionText = null;
    String joinPath = null;
    double duration = 5;
    double maxCost = 0;
    boolean confirm = false;
    boolean unlimited = false;
    boolean silent = false;
    boolean exact = false;
    boolean savePrompt = false;
    // Off only when asked, on every run.
    SogniApi.noFilter = false;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--prompt") && i + 1 < args.length) prompt = args[++i];
      else if (a.equals("--image") && i + 1 < args.length) image = args[++i].trim();
      else if (a.equals("--end_image") && i + 1 < args.length) endImage = args[++i].trim();
      else if (a.equals("--duration") && i + 1 < args.length) duration = number(a, args[++i]);
      else if (a.equals("--resolution") && i + 1 < args.length) resolutionText = args[++i];
      else if (a.equals("--aspect") && i + 1 < args.length) aspect = args[++i].trim();
      else if (a.equals("--no_audio")) silent = true;
      else if (a.equals("--exact_prompt")) exact = true;
      else if (a.equals("--saveprompt")) savePrompt = true;
      else if (a.equals("--unlimited")) unlimited = true;
      else if (a.equals("--no_filter")) SogniApi.noFilter = true;
      else if (a.equals("--join") && i + 1 < args.length) joinPath = args[++i].trim();
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
    if (Double.isNaN(duration) || Double.isNaN(maxCost)) return 2;
    if (image != null && image.length() == 0) image = null;
    if (endImage != null && endImage.length() == 0) endImage = null;
    if (aspect != null && aspect.length() == 0) aspect = null;
    if (workflowId != null && workflowId.length() == 0) workflowId = null;
    if ((prompt == null || prompt.trim().length() == 0) && workflowId == null) {
      say("Failed: give --prompt, what happens in the clip, for example --prompt \"She walks slowly through the garden, "
          + "the camera follows at waist height, birdsong and footsteps on gravel\"");
      usage();
      return 2;
    }
    if (endImage != null && image == null) {
      say("Failed: --end_image is the last frame; give the first one with --image");
      return 2;
    }
    // --join: checked before the run, so a missing file costs nothing.
    File joinFile = null;
    if (joinPath != null && joinPath.length() > 0) {
      joinFile = new File(joinPath);
      if (!joinFile.isFile()) {
        say("Failed: --join names no file: " + joinPath);
        return 2;
      }
      if (!joinFile.getName().toLowerCase().matches(".+\\.(mp4|m4v|mov)")) {
        say("Failed: --join takes an MP4 video (" + joinFile.getName() + " is not one)");
        return 2;
      }
    }
    int resolution = resolution(resolutionText);
    if (resolution < 0) {
      say("Failed: --resolution is 768 (FastH3's own size), or 720, 1080 or 1440 (2K) for the two-stage engine");
      return 2;
    }
    if (duration < 5 || duration > 15.1) {
      say("Failed: --duration is 5 to 15 seconds (MiniMax H3 makes 5.17 to 15.08 s)");
      return 2;
    }
    if (aspect != null && !aspect.matches("\\d{1,2}:\\d{1,2}|\\d{3,4}x\\d{3,4}")) {
      say("Failed: --aspect is a shape such as 16:9, 9:16, 1:1 or 4:5 (or pixels, such as 1280x720)");
      return 2;
    }
    if (unlimited && maxCost > 0) say("Note: --max_cost is not used with the Unlimited Plan");
    List<byte[]> pictures = new ArrayList<byte[]>();
    List<String> pictureTypes = new ArrayList<String>();
    List<String> pictureNames = new ArrayList<String>();
    if (workflowId == null) {
      for (String path : new String[] {image, endImage}) {
        if (path == null) continue;
        File f = new File(path);
        byte[] data = readPicture(f);
        if (data == null) return 2;
        pictures.add(data);
        pictureTypes.add(pictureType(data));
        pictureNames.add(f.getName());
      }
    }
    if (savePrompt && workflowId == null) {
      // After the checks, so the sheet holds the settings as sent (1440 for "2K").
      String sheetName = out != null ? out.trim().replaceAll("\\.[A-Za-z0-9]{1,5}$", "") : clipName(prompt, "");
      File sheet = savePrompt(sheetName, "Sogni " + SogniApi.videoModel(pictures.size(), resolution),
          prompt.trim() + settingsLine(duration, resolution, aspect, silent, pictureNames), pictureNames);
      say(sheet == null ? "Could not save the prompt" : "Saved prompt " + sheet.getName());
    }
    String key = SogniApi.findKey(keyFile);
    if (key == null) {
      say("Failed: no Sogni API key. Choose a key file in File > Drum Midi Settings (Sogni API key file), give --key_file "
          + "with a text file holding SOGNI_API_KEY=<your key>, or set SOGNI_API_KEY. Get the key at https://dashboard.sogni.ai (account menu).");
      return 1;
    }
    SogniApi api = new SogniApi(apiBase, key);
    String model = SogniApi.videoModel(pictures.size(), resolution);
    if (workflowId != null) {
      say("Fetching the clip of workflow " + workflowId);
    } else {
      say("Video: " + prompt.trim());
      say("Model " + model + ", " + SogniApi.number(duration) + " s, "
          + (resolution == 1440 ? "2K" : resolution + "p") + (aspect != null ? ", " + aspect : "") + (silent ? ", silent" : ", with sound")
          + (exact ? ", prompt as written" : ""));
      say(pictures.isEmpty() ? "From the prompt alone (no --image)"
          : pictures.size() == 1 ? "First frame: " + pictureNames.get(0) : "First frame: " + pictureNames.get(0) + ", last frame: " + pictureNames.get(1));
      if (unlimited) say("Unlimited Plan: the subscription pays; Sogni's daily and monthly fair use limits apply");
      if (SogniApi.noFilter) say("Content filter: off (Sogni's Safe Content Filter does not check this run)");
    }
    try {
      String id = workflowId;
      if (id == null) {
        List<Map<String, Object>> media = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < pictures.size(); i++) {
          Map<String, Object> ref = api.uploadMedia("image", pictureTypes.get(i), pictures.get(i), i + 1, pictureNames.get(i));
          media.add(ref);
          say("Uploaded " + pictureNames.get(i) + " (" + size(pictures.get(i).length) + ") as " + SogniApi.str(ref.get("id")));
        }
        String input = SogniApi.videoInput("Pulsekit video", prompt.trim(), pictures.size(), duration, resolution, !silent, exact, aspect);
        id = api.start(input, confirm || unlimited, unlimited ? 0 : maxCost, media, unlimited ? "subscription" : null);
      }
      say("Workflow: " + id);
      Map<String, Object> wf = api.waitFor(id, 18 * 60 * 1000L, new SogniApi.Log() {
        public void line(String s) {
          say(s);
        }
      });
      String status = SogniApi.str(wf.get("status"));
      List<Map<String, Object>> clips = SogniApi.videoArtifacts(wf);
      if (clips.isEmpty()) {
        say("Failed: " + why(api, id, wf));
        return 1;
      }
      String url = SogniApi.str(clips.get(0).get("url"));
      String ext = SogniApi.mediaExtension(url, SogniApi.mimeOf(clips.get(0)), ".mp4");
      String name = out != null ? out.trim().replaceAll("\\.[A-Za-z0-9]{1,5}$", "") : clipName(prompt, id);
      byte[] data = api.download(url);
      File file = saveData(name + ext, data);
      if (file == null) {
        say("Failed: could not save " + name + ext);
        return 1;
      }
      say("Wrote " + file.getName() + " (" + size(data.length) + ")");
      resultFile = file.getName();
      if (joinFile != null) joinWith(file, joinFile, name, ext);
      if (!"completed".equals(status)) say("Note: workflow " + status);
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

  /**
   * --join: the clip, then `other`, as <name>-merged.mp4 beside it (Mp4Join: no re-encoding). The
   * clip is kept as it is either way; a join that cannot be made says why.
   */
  static void joinWith(File clip, File other, String name, String ext) {
    if (!".mp4".equalsIgnoreCase(ext) && !".m4v".equalsIgnoreCase(ext) && !".mov".equalsIgnoreCase(ext)) {
      say("Could not join with " + other.getName() + ": Sogni sent a " + ext + " clip, not an MP4");
      return;
    }
    File merged = inWork(name + "-merged.mp4");
    for (int n = 1; merged.exists(); n++) merged = inWork(name + "-merged(" + n + ").mp4");
    try {
      String note = Mp4Join.join(clip, other, merged);
      say("Joined " + clip.getName() + " and " + other.getName() + ": wrote " + merged.getName() + " (" + size(merged.length()) + ")");
      if (note.length() > 0) say("Note: " + note);
    } catch (IOException ex) {
      merged.delete();
      say("Could not join with " + other.getName() + ": " + ex.getMessage() + " (the clip is saved as it is)");
    }
  }

  static void usage() {
    say("Usage: java SogniVideo [output.mp4] [--prompt text] [--image picture.png] [--end_image picture.png] [--duration seconds] "
        + "[--resolution 768|720|1080|1440] [--aspect 16:9|9:16|1:1] [--no_audio] [--exact_prompt] [--saveprompt] [--unlimited] [--no_filter] [--join video.mp4] [--key_file credentials.txt] "
        + "[--confirm_cost] [--max_cost N] [--workflow id]");
  }

  /** 768 (also empty), 720, 1080 or 1440 ("1080p", "2K" read the same way); -1 for anything else. */
  static int resolution(String value) {
    String v = value == null ? "" : value.trim().toLowerCase().replaceAll("p$", "");
    if (v.length() == 0 || v.equals("768")) return 768;
    if (v.equals("2k")) return 1440;
    if (v.equals("720") || v.equals("1080") || v.equals("1440")) return Integer.parseInt(v);
    return -1;
  }

  /** A picture's bytes, or null after saying why it cannot be used. */
  static byte[] readPicture(File f) {
    if (!f.isFile()) {
      say("Failed: no picture " + f.getPath());
      return null;
    }
    if (f.length() > UPLOAD_MAX) {
      say("Failed: " + f.getName() + " is " + size(f.length()) + "; Sogni takes pictures of up to " + size(UPLOAD_MAX));
      return null;
    }
    byte[] data;
    try {
      data = java.nio.file.Files.readAllBytes(f.toPath());
    } catch (IOException ex) {
      say("Failed: could not read " + f.getName() + ": " + ex.getMessage());
      return null;
    }
    if (pictureType(data) == null) {
      say("Failed: " + f.getName() + " is not a PNG, JPEG or WebP picture");
      return null;
    }
    return data;
  }

  /** "image/png", "image/jpeg" or "image/webp" from the file's first bytes, else null. */
  static String pictureType(byte[] d) {
    if (d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
    if (d.length >= 3 && (d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8 && (d[2] & 0xff) == 0xff) return "image/jpeg";
    if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
    return null;
  }

  /**
   * The clip's settings under the prompt in a saved sheet: "\n\nDuration: 5 s. Resolution: 768p.
   * Shape: 16:9. Sound: none. First frame: garden.png." Defaults (with sound, the picture's own
   * shape) are left out.
   */
  static String settingsLine(double duration, int resolution, String aspect, boolean silent, List<String> pictures) {
    StringBuilder sb = new StringBuilder();
    sb.append("Duration: ").append(SogniApi.number(duration)).append(" s. ");
    sb.append("Resolution: ").append(resolution == 1440 ? "2K" : resolution + "p").append(resolution == 768 ? "" : " (two-stage)").append(". ");
    if (aspect != null) sb.append("Shape: ").append(aspect).append(". ");
    if (silent) sb.append("Sound: none. ");
    if (pictures.size() > 0) sb.append("First frame: ").append(pictures.get(0)).append(". ");
    if (pictures.size() > 1) sb.append("Last frame: ").append(pictures.get(1)).append(". ");
    return "\n\n" + sb.toString().trim();
  }

  /**
   * Writes `prompt` as a Pulsekit prompt sheet (PKPROMPT1, as PromptRun.encode writes it):
   * name, category video, the model, the pictures as reference files (1 the first frame, 2 the
   * last), type AI, then the prompt. Never over an existing file.
   */
  static File savePrompt(String name, String model, String prompt, List<String> pictures) {
    String ref1 = pictures.size() > 0 ? pictures.get(0) : "";
    String ref2 = pictures.size() > 1 ? pictures.get(1) : "";
    StringBuilder sb = new StringBuilder();
    sb.append("PKPROMPT1\n").append(name).append("\n\n\n").append(ref1).append('\n').append(ref2).append('\n');
    sb.append("Category: video\n");
    sb.append("Model: ").append(model).append('\n');
    sb.append("Reference file 1: ").append(ref1).append('\n');
    sb.append("Reference file 2: ").append(ref2).append('\n');
    sb.append("Type: ai\n");
    sb.append("---\n");
    sb.append(prompt);
    File file = saveData(name + ".prompt", sb.toString().getBytes(StandardCharsets.UTF_8));
    promptSheet = file;
    promptText = sb.toString();
    return file;
  }

  /** sogni-video-<first words of the prompt>, or sogni-video-<run> when there is no prompt. */
  static String clipName(String prompt, String id) {
    String words = prompt == null ? "" : prompt.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    StringBuilder sb = new StringBuilder();
    for (String w : words.split(" ")) {
      if (w.length() == 0 || sb.length() + w.length() > 24) break;
      sb.append(sb.length() > 0 ? "-" : "").append(w);
    }
    if (sb.length() == 0) sb.append(id.replaceAll("[^A-Za-z0-9]", "").substring(0, Math.min(8, id.replaceAll("[^A-Za-z0-9]", "").length())));
    return "sogni-video-" + sb;
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
   * Why a run made no clip: the failed step's own words, from the record or the event list. When
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
      Map<String, Object> args = new LinkedHashMap<String, Object>();
      args.put("prompt", prompt);
      args.put("videoModel", videoModel(pictures, resolution));
      if (duration > 0) args.put("duration", Double.valueOf(duration));
      args.put("targetResolution", Integer.valueOf(resolution == 720 || resolution == 1080 || resolution == 1440 ? resolution : 768));
      if (!audio) args.put("generateAudio", Boolean.FALSE);
      if (exact) args.put("skipPromptProcessing", Boolean.TRUE);
      if (aspect != null && aspect.length() > 0) args.put("aspectRatio", aspect);
      args.put("numberOfVariations", Integer.valueOf(1));
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
    // --- SogniApi end ---
  }

  /**
   * --join: the clip SogniVideo made, then another video, as one MP4, without re-encoding. Both
   * must be plain MP4s (H.264 or another codec, not fragmented) whose pictures have the same
   * format (codec settings and size): two Sogni clips from the same model do. The sample tables
   * are rebuilt and the samples copied in order, the first video's then the second's. Sound is
   * joined when both have it in the same format; otherwise the sound there is is kept where it was
   * (a note says so). Java 8 without lambdas, for the phone's compiler.
   */
  static final class Mp4Join {
    private Mp4Join() {}

    /** What went wrong, in words for the log. */
    static final class JoinException extends IOException {
      JoinException(String why) {
        super(why);
      }
    }

    /** One sample: where its bytes are, how big, how long (in its track's timescale). */
    static final class Sample {
      int part;
      long offset;
      int size;
      long duration;
      long ctts;
      boolean sync;
    }

    /** A track of one file. */
    static final class Track {
      String handler;
      long timescale;
      byte[] tkhd;
      byte[] mdhd;
      byte[] hdlr;
      byte[] mediaHeader;
      byte[] dinf;
      byte[] stsd;
      long editMediaTime;
      boolean hasCtts;
      boolean hasStss;
      List<Sample> samples = new ArrayList<Sample>();

      long mediaDuration() {
        long d = 0;
        for (Sample s : samples) d += s.duration;
        return d;
      }
    }

    /** One file: its brand box, movie header and tracks. */
    static final class Movie {
      byte[] ftyp;
      byte[] mvhd;
      long timescale;
      Track video;
      Track audio;
    }

    /** Writes `first` then `second` into `dest`; returns a note (sound left out...) or "". */
    static String join(File first, File second, File dest) throws IOException {
      Movie a = read(first, 0);
      Movie b = read(second, 1);
      if (a.video == null) throw new JoinException(first.getName() + " has no picture");
      if (b.video == null) throw new JoinException(second.getName() + " has no picture");
      if (!sameEntry(a.video.stsd, b.video.stsd)) {
        throw new JoinException("the pictures are in different formats (" + describe(a.video.stsd) + " and " + describe(b.video.stsd)
            + "), so they cannot be joined without re-encoding");
      }
      String note = "";
      Track video = combine(a.video, b.video);
      Track audio = null;
      long leadA = 0;
      if (a.audio != null && b.audio != null && sameEntry(a.audio.stsd, b.audio.stsd)) {
        audio = combine(a.audio, b.audio);
      } else if (a.audio != null) {
        audio = a.audio;
        note = b.audio == null ? second.getName() + " has no sound: the joined part is silent"
            : second.getName() + "'s sound is in another format, so it is left out";
      } else if (b.audio != null) {
        // The sound starts where the second video does.
        audio = b.audio;
        leadA = a.video.mediaDuration() * a.timescale / Math.max(1, a.video.timescale);
        note = first.getName() + " has no sound: the sound starts with " + second.getName();
      }
      List<Track> tracks = new ArrayList<Track>();
      tracks.add(video);
      if (audio != null) tracks.add(audio);
      long[] leads = new long[] {0, leadA};
      write(dest, a, tracks, leads, new File[] {first, second});
      return note;
    }

    /** The second track's samples after the first's, in the first's timescale. */
    static Track combine(Track x, Track y) {
      Track t = new Track();
      t.handler = x.handler;
      t.timescale = x.timescale;
      t.tkhd = x.tkhd;
      t.mdhd = x.mdhd;
      t.hdlr = x.hdlr;
      t.mediaHeader = x.mediaHeader;
      t.dinf = x.dinf;
      t.stsd = x.stsd;
      t.editMediaTime = x.editMediaTime;
      t.hasCtts = x.hasCtts || y.hasCtts;
      t.hasStss = x.hasStss || y.hasStss;
      t.samples.addAll(x.samples);
      // Durations rescaled with the rounding carried along, so the total stays exact.
      long cum = 0;
      long done = 0;
      for (Sample s : y.samples) {
        Sample c = new Sample();
        c.part = s.part;
        c.offset = s.offset;
        c.size = s.size;
        c.sync = s.sync;
        cum += s.duration;
        long end = Math.round(cum * (double) x.timescale / y.timescale);
        c.duration = end - done;
        done = end;
        c.ctts = Math.round(s.ctts * (double) x.timescale / y.timescale);
        t.samples.add(c);
      }
      return t;
    }

    /**
     * Whether samples described by `q` decode with description `p`: the same codec, picture size or
     * sound settings, and codec setup (avcC, hvcC..., or the AAC config in esds). Bitrate notes (btrt,
     * the esds bitrates) and the encoder's name may differ.
     */
    static boolean sameEntry(byte[] p, byte[] q) {
      if (Arrays.equals(p, q)) return true;
      if (p == null || q == null || p.length < 24 || q.length < 24) return false;
      if (!new String(p, 20, 4, StandardCharsets.ISO_8859_1).equals(new String(q, 20, 4, StandardCharsets.ISO_8859_1))) return false;
      boolean video = describe(p).indexOf('x') > 0 && !new String(p, 20, 4, StandardCharsets.ISO_8859_1).equals("mp4a");
      // The fixed part of the entry after its size and type: 78 bytes for a picture, 28 for sound.
      int fixed = video ? 78 : 28;
      int body = 24 + fixed;
      if (p.length < body || q.length < body) return false;
      if (video) {
        for (int i = 48; i < 52; i++) if (p[i] != q[i]) return false;
      } else {
        for (int i = 24; i < body; i++) if (p[i] != q[i]) return false;
      }
      Map<String, byte[]> a = entryBoxes(p, body);
      Map<String, byte[]> b = entryBoxes(q, body);
      if (a == null || b == null) return false;
      String[] setup = {"avcC", "hvcC", "av1C", "vpcC", "dOps", "dac3", "dec3", "alac", "dfLa"};
      for (String k : setup) if (!Arrays.equals(a.get(k), b.get(k))) return false;
      if (a.containsKey("esds") || b.containsKey("esds")) {
        if (!Arrays.equals(esdsConfig(a.get("esds")), esdsConfig(b.get("esds")))) return false;
      }
      return true;
    }

    /** The boxes inside the first sample entry, by type, from `at` to the entry's end; null when damaged. */
    static Map<String, byte[]> entryBoxes(byte[] stsd, int at) {
      Map<String, byte[]> out = new LinkedHashMap<String, byte[]>();
      int end = Math.min(stsd.length, 16 + (int) u32(stsd, 16));
      try {
        for (int[] c : children(stsd, at, end)) out.put(new String(stsd, c[0] + 4, 4, StandardCharsets.ISO_8859_1), Arrays.copyOfRange(stsd, c[0], c[1]));
      } catch (JoinException ex) {
        return null;
      }
      return out;
    }

    /** From an esds box: the codec kind and its decoder setup (DecoderSpecificInfo), without the bitrates. */
    static byte[] esdsConfig(byte[] esds) {
      if (esds == null) return null;
      try {
        int[] pos = {12};
        if (esds[pos[0]++] != 0x03) return esds;
        descriptorLength(esds, pos);
        int flags = esds[pos[0] + 2] & 0xff;
        pos[0] += 3;
        if ((flags & 0x80) != 0) pos[0] += 2;
        if ((flags & 0x40) != 0) pos[0] += 1 + (esds[pos[0]] & 0xff);
        if ((flags & 0x20) != 0) pos[0] += 2;
        if (esds[pos[0]++] != 0x04) return esds;
        descriptorLength(esds, pos);
        byte kind = esds[pos[0]];
        pos[0] += 13;
        if (esds[pos[0]++] != 0x05) return new byte[] {kind};
        int len = descriptorLength(esds, pos);
        byte[] out = new byte[1 + len];
        out[0] = kind;
        System.arraycopy(esds, pos[0], out, 1, len);
        return out;
      } catch (ArrayIndexOutOfBoundsException ex) {
        return esds;
      }
    }

    /** An MPEG-4 descriptor's length: up to four bytes of seven bits each. */
    static int descriptorLength(byte[] d, int[] pos) {
      int len = 0;
      for (int i = 0; i < 4; i++) {
        int b = d[pos[0]++] & 0xff;
        len = (len << 7) | (b & 0x7f);
        if ((b & 0x80) == 0) break;
      }
      return len;
    }

    /** "avc1 768x1152" from a sample description (stsd payload). */
    static String describe(byte[] stsd) {
      // The box header (8), version and entry count (8), then the first entry: size, type, and for
      // a picture its width and height 24 bytes further on.
      if (stsd == null || stsd.length < 24) return "unknown";
      String type = new String(stsd, 20, 4, StandardCharsets.ISO_8859_1);
      if (stsd.length >= 52) {
        int w = ((stsd[48] & 0xff) << 8) | (stsd[49] & 0xff);
        int h = ((stsd[50] & 0xff) << 8) | (stsd[51] & 0xff);
        if (w > 0 && h > 0) return type + " " + w + "x" + h;
      }
      return type;
    }

    // ---- reading

    static Movie read(File f, int part) throws IOException {
      RandomAccessFile in = new RandomAccessFile(f, "r");
      try {
        Movie m = new Movie();
        long pos = 0;
        long len = in.length();
        byte[] moov = null;
        while (pos + 8 <= len) {
          in.seek(pos);
          long size = in.readInt() & 0xffffffffL;
          String type = fourcc(in.readInt());
          long head = 8;
          if (size == 1) {
            size = in.readLong();
            head = 16;
          } else if (size == 0) {
            size = len - pos;
          }
          if (size < head || pos + size > len) throw new JoinException(f.getName() + " is not a complete MP4 file");
          if (type.equals("ftyp")) m.ftyp = readBox(in, pos, size);
          else if (type.equals("moov")) moov = readBox(in, pos, size);
          else if (type.equals("moof")) throw new JoinException(f.getName() + " is a fragmented MP4, which cannot be joined here");
          pos += size;
        }
        if (moov == null) throw new JoinException(f.getName() + " is not an MP4 video");
        for (int[] box : children(moov, 8, moov.length)) {
          String type = new String(moov, box[0] + 4, 4, StandardCharsets.ISO_8859_1);
          if (type.equals("mvhd")) {
            m.mvhd = Arrays.copyOfRange(moov, box[0], box[1]);
            m.timescale = u32(m.mvhd, m.mvhd[8] == 1 ? 8 + 4 + 16 : 8 + 4 + 8);
          } else if (type.equals("trak")) {
            Track t = track(moov, box[0], box[1], part, f.getName());
            if (t == null) continue;
            if ("vide".equals(t.handler) && m.video == null) m.video = t;
            else if ("soun".equals(t.handler) && m.audio == null) m.audio = t;
          }
        }
        if (m.mvhd == null) throw new JoinException(f.getName() + " has no movie header");
        if (m.ftyp == null) throw new JoinException(f.getName() + " is not an MP4 video (no file type)");
        return m;
      } finally {
        in.close();
      }
    }

    static byte[] readBox(RandomAccessFile in, long pos, long size) throws IOException {
      if (size > 64L * 1024 * 1024) throw new JoinException("an MP4 header is too large");
      byte[] b = new byte[(int) size];
      in.seek(pos);
      in.readFully(b);
      return b;
    }

    /** The boxes inside [from, to): {start, end} each. */
    static List<int[]> children(byte[] d, int from, int to) throws JoinException {
      List<int[]> out = new ArrayList<int[]>();
      int pos = from;
      while (pos + 8 <= to) {
        long size = u32(d, pos);
        if (size == 1) size = u64(d, pos + 8);
        else if (size == 0) size = to - pos;
        if (size < 8 || pos + size > to) throw new JoinException("an MP4 header is damaged");
        out.add(new int[] {pos, (int) (pos + size)});
        pos += (int) size;
      }
      return out;
    }

    static int[] child(byte[] d, int from, int to, String type) throws JoinException {
      for (int[] c : children(d, from, to)) if (new String(d, c[0] + 4, 4, StandardCharsets.ISO_8859_1).equals(type)) return c;
      return null;
    }

    /** A track's tables, its samples in order; null for a track that is neither picture nor sound. */
    static Track track(byte[] d, int start, int end, int part, String name) throws JoinException {
      Track t = new Track();
      int[] tkhd = child(d, start + 8, end, "tkhd");
      int[] mdia = child(d, start + 8, end, "mdia");
      if (tkhd == null || mdia == null) return null;
      t.tkhd = Arrays.copyOfRange(d, tkhd[0], tkhd[1]);
      int[] edts = child(d, start + 8, end, "edts");
      if (edts != null) {
        int[] elst = child(d, edts[0] + 8, edts[1], "elst");
        if (elst != null) {
          boolean v1 = d[elst[0] + 8] == 1;
          long count = u32(d, elst[0] + 12);
          // The first edit that shows media: where the track's media starts (skips encoder delay).
          for (int i = 0, at = elst[0] + 16; i < count && at < elst[1]; i++) {
            long mediaTime = v1 ? u64(d, at + 8) : (long) (int) u32(d, at + 4);
            at += v1 ? 20 : 12;
            if (mediaTime >= 0) {
              t.editMediaTime = mediaTime;
              break;
            }
          }
        }
      }
      int[] mdhd = child(d, mdia[0] + 8, mdia[1], "mdhd");
      int[] hdlr = child(d, mdia[0] + 8, mdia[1], "hdlr");
      int[] minf = child(d, mdia[0] + 8, mdia[1], "minf");
      if (mdhd == null || hdlr == null || minf == null) return null;
      t.mdhd = Arrays.copyOfRange(d, mdhd[0], mdhd[1]);
      t.hdlr = Arrays.copyOfRange(d, hdlr[0], hdlr[1]);
      t.handler = new String(d, hdlr[0] + 16, 4, StandardCharsets.ISO_8859_1);
      if (!t.handler.equals("vide") && !t.handler.equals("soun")) return null;
      t.timescale = u32(t.mdhd, t.mdhd[8] == 1 ? 8 + 4 + 16 : 8 + 4 + 8);
      int[] head = child(d, minf[0] + 8, minf[1], t.handler.equals("vide") ? "vmhd" : "smhd");
      int[] dinf = child(d, minf[0] + 8, minf[1], "dinf");
      int[] stbl = child(d, minf[0] + 8, minf[1], "stbl");
      if (head == null || dinf == null || stbl == null) throw new JoinException(name + " has an incomplete track");
      t.mediaHeader = Arrays.copyOfRange(d, head[0], head[1]);
      t.dinf = Arrays.copyOfRange(d, dinf[0], dinf[1]);
      int[] stsd = child(d, stbl[0] + 8, stbl[1], "stsd");
      int[] stts = child(d, stbl[0] + 8, stbl[1], "stts");
      int[] ctts = child(d, stbl[0] + 8, stbl[1], "ctts");
      int[] stss = child(d, stbl[0] + 8, stbl[1], "stss");
      int[] stsc = child(d, stbl[0] + 8, stbl[1], "stsc");
      int[] stsz = child(d, stbl[0] + 8, stbl[1], "stsz");
      int[] stco = child(d, stbl[0] + 8, stbl[1], "stco");
      int[] co64 = child(d, stbl[0] + 8, stbl[1], "co64");
      if (stsd == null || stts == null || stsc == null || stsz == null || (stco == null && co64 == null)) {
        throw new JoinException(name + " has an incomplete sample table");
      }
      t.stsd = Arrays.copyOfRange(d, stsd[0], stsd[1]);
      if (u32(d, stsd[0] + 12) != 1) throw new JoinException(name + " has more than one picture or sound format in a track");
      // Sizes.
      long uniform = u32(d, stsz[0] + 12);
      int n = (int) u32(d, stsz[0] + 16);
      for (int i = 0; i < n; i++) {
        Sample s = new Sample();
        s.part = part;
        s.size = (int) (uniform != 0 ? uniform : u32(d, stsz[0] + 20 + 4 * i));
        s.sync = true;
        t.samples.add(s);
      }
      // Durations.
      int k = 0;
      long entries = u32(d, stts[0] + 12);
      for (int e = 0; e < entries; e++) {
        long count = u32(d, stts[0] + 16 + 8 * e);
        long delta = u32(d, stts[0] + 20 + 8 * e);
        for (long c = 0; c < count && k < n; c++) t.samples.get(k++).duration = delta;
      }
      // Composition offsets.
      if (ctts != null) {
        t.hasCtts = true;
        k = 0;
        entries = u32(d, ctts[0] + 12);
        for (int e = 0; e < entries; e++) {
          long count = u32(d, ctts[0] + 16 + 8 * e);
          long off = (int) u32(d, ctts[0] + 20 + 8 * e);
          for (long c = 0; c < count && k < n; c++) t.samples.get(k++).ctts = off;
        }
      }
      // Key frames (all are, without stss).
      if (stss != null) {
        t.hasStss = true;
        for (Sample s : t.samples) s.sync = false;
        entries = u32(d, stss[0] + 12);
        for (int e = 0; e < entries; e++) {
          long number = u32(d, stss[0] + 16 + 4 * e);
          if (number >= 1 && number <= n) t.samples.get((int) number - 1).sync = true;
        }
      }
      // Where each sample is: chunk offsets, samples per chunk.
      boolean wide = stco == null;
      int[] co = wide ? co64 : stco;
      long chunks = u32(d, co[0] + 12);
      long scEntries = u32(d, stsc[0] + 12);
      k = 0;
      for (int e = 0; e < scEntries; e++) {
        long firstChunk = u32(d, stsc[0] + 16 + 12 * e);
        long perChunk = u32(d, stsc[0] + 20 + 12 * e);
        long desc = u32(d, stsc[0] + 24 + 12 * e);
        if (desc != 1) throw new JoinException(name + " has more than one sample format");
        long lastChunk = e + 1 < scEntries ? u32(d, stsc[0] + 16 + 12 * (e + 1)) - 1 : chunks;
        for (long c = firstChunk; c <= lastChunk; c++) {
          long off = wide ? u64(d, co[0] + 16 + 8 * (int) (c - 1)) : u32(d, co[0] + 16 + 4 * (int) (c - 1));
          for (long i = 0; i < perChunk && k < n; i++) {
            Sample s = t.samples.get(k++);
            s.offset = off;
            off += s.size;
          }
        }
      }
      if (k < n) throw new JoinException(name + "'s sample table is incomplete");
      return t;
    }

    // ---- writing

    /** A run of samples of one track, laid out together in the media data. */
    static final class Chunk {
      Track track;
      int from;
      int to;
      double start;
      long offset;
    }

    static void write(File dest, Movie a, List<Track> tracks, long[] leads, File[] parts) throws IOException {
      // Chunks of about half a second, interleaved by time.
      List<Chunk> chunks = new ArrayList<Chunk>();
      for (int ti = 0; ti < tracks.size(); ti++) {
        Track t = tracks.get(ti);
        double lead = ti == 0 ? 0 : leads[1] / (double) Math.max(1, a.timescale);
        long time = 0;
        int i = 0;
        while (i < t.samples.size()) {
          Chunk c = new Chunk();
          c.track = t;
          c.from = i;
          c.start = lead + time / (double) t.timescale;
          long limit = time + t.timescale / 2;
          int part = t.samples.get(i).part;
          while (i < t.samples.size() && (i == c.from || time < limit) && t.samples.get(i).part == part) {
            time += t.samples.get(i).duration;
            i++;
          }
          c.to = i;
          chunks.add(c);
        }
      }
      Collections.sort(chunks, new java.util.Comparator<Chunk>() {
        public int compare(Chunk x, Chunk y) {
          int c = Double.compare(x.start, y.start);
          return c != 0 ? c : (x.track.handler.equals("vide") ? -1 : 1) - (y.track.handler.equals("vide") ? -1 : 1);
        }
      });
      long mdatSize = 0;
      for (Chunk c : chunks) for (int i = c.from; i < c.to; i++) mdatSize += c.track.samples.get(i).size;
      boolean big = mdatSize > 0xffffffffL - 16;
      // Twice: the movie box's size sets where the media data starts, which sets the chunk offsets.
      byte[] moov = moov(a, tracks, leads, chunks, big);
      long dataStart = a.ftyp.length + moov.length + (big ? 16 : 8);
      long at = dataStart;
      for (Chunk c : chunks) {
        c.offset = at;
        for (int i = c.from; i < c.to; i++) at += c.track.samples.get(i).size;
      }
      moov = moov(a, tracks, leads, chunks, big);
      if (a.ftyp.length + moov.length + (big ? 16 : 8) != dataStart) throw new JoinException("could not lay out the joined file");
      RandomAccessFile[] in = new RandomAccessFile[parts.length];
      DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(dest), 1 << 16));
      boolean done = false;
      try {
        for (int i = 0; i < parts.length; i++) in[i] = new RandomAccessFile(parts[i], "r");
        out.write(a.ftyp);
        out.write(moov);
        if (big) {
          out.writeInt(1);
          out.writeBytes("mdat");
          out.writeLong(mdatSize + 16);
        } else {
          out.writeInt((int) (mdatSize + 8));
          out.writeBytes("mdat");
        }
        byte[] buf = new byte[1 << 16];
        for (Chunk c : chunks) {
          for (int i = c.from; i < c.to; i++) {
            Sample s = c.track.samples.get(i);
            RandomAccessFile src = in[s.part];
            src.seek(s.offset);
            int left = s.size;
            while (left > 0) {
              int n = src.read(buf, 0, Math.min(buf.length, left));
              if (n <= 0) throw new JoinException("a video ends before its last frame");
              out.write(buf, 0, n);
              left -= n;
            }
          }
        }
        out.flush();
        done = true;
      } finally {
        for (RandomAccessFile r : in) if (r != null) r.close();
        out.close();
        if (!done) dest.delete();
      }
    }

    static byte[] moov(Movie a, List<Track> tracks, long[] leads, List<Chunk> chunks, boolean big) throws IOException {
      java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
      long movieDuration = 0;
      List<byte[]> traks = new ArrayList<byte[]>();
      for (int ti = 0; ti < tracks.size(); ti++) {
        Track t = tracks.get(ti);
        long lead = ti == 0 ? 0 : leads[1];
        long media = t.mediaDuration();
        long shown = Math.max(0, media - t.editMediaTime);
        long inMovie = Math.round(shown * (double) a.timescale / t.timescale);
        movieDuration = Math.max(movieDuration, lead + inMovie);
        traks.add(trak(t, ti + 1, lead, inMovie, a.timescale, chunks, big));
      }
      body.write(withDuration(a.mvhd, movieDuration, true, tracks.size() + 1));
      for (byte[] b : traks) body.write(b);
      return box("moov", body.toByteArray());
    }

    static byte[] trak(Track t, int id, long lead, long inMovie, long movieScale, List<Chunk> chunks, boolean big) throws IOException {
      java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
      byte[] tkhd = t.tkhd.clone();
      boolean v1 = tkhd[8] == 1;
      put32(tkhd, v1 ? 8 + 4 + 16 : 8 + 4 + 8, id);
      if (v1) put64(tkhd, 8 + 4 + 16 + 8, lead + inMovie);
      else put32(tkhd, 8 + 4 + 8 + 8, lead + inMovie);
      b.write(tkhd);
      // The edit list: an empty edit first when the track starts later, then the media from where it shows.
      java.io.ByteArrayOutputStream el = new java.io.ByteArrayOutputStream();
      DataOutputStream e = new DataOutputStream(el);
      e.writeInt(0x01000000);
      e.writeInt(lead > 0 ? 2 : 1);
      if (lead > 0) {
        e.writeLong(lead);
        e.writeLong(-1);
        e.writeInt(0x00010000);
      }
      e.writeLong(inMovie);
      e.writeLong(t.editMediaTime);
      e.writeInt(0x00010000);
      b.write(box("edts", box("elst", el.toByteArray())));
      java.io.ByteArrayOutputStream mdia = new java.io.ByteArrayOutputStream();
      mdia.write(withDuration(t.mdhd, t.mediaDuration(), false, 0));
      mdia.write(t.hdlr);
      java.io.ByteArrayOutputStream minf = new java.io.ByteArrayOutputStream();
      minf.write(t.mediaHeader);
      minf.write(t.dinf);
      minf.write(stbl(t, chunks, big));
      mdia.write(box("minf", minf.toByteArray()));
      b.write(box("mdia", mdia.toByteArray()));
      return box("trak", b.toByteArray());
    }

    static byte[] stbl(Track t, List<Chunk> chunks, boolean big) throws IOException {
      java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
      b.write(t.stsd);
      List<Sample> s = t.samples;
      // Durations, run-length.
      java.io.ByteArrayOutputStream x = new java.io.ByteArrayOutputStream();
      DataOutputStream d = new DataOutputStream(x);
      List<long[]> runs = new ArrayList<long[]>();
      for (Sample one : s) {
        if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == one.duration) runs.get(runs.size() - 1)[0]++;
        else runs.add(new long[] {1, one.duration});
      }
      d.writeInt(0);
      d.writeInt(runs.size());
      for (long[] r : runs) {
        d.writeInt((int) r[0]);
        d.writeInt((int) r[1]);
      }
      b.write(box("stts", x.toByteArray()));
      if (t.hasCtts) {
        x = new java.io.ByteArrayOutputStream();
        d = new DataOutputStream(x);
        runs = new ArrayList<long[]>();
        boolean negative = false;
        for (Sample one : s) {
          if (one.ctts < 0) negative = true;
          if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == one.ctts) runs.get(runs.size() - 1)[0]++;
          else runs.add(new long[] {1, one.ctts});
        }
        d.writeInt(negative ? 0x01000000 : 0);
        d.writeInt(runs.size());
        for (long[] r : runs) {
          d.writeInt((int) r[0]);
          d.writeInt((int) r[1]);
        }
        b.write(box("ctts", x.toByteArray()));
      }
      if (t.hasStss) {
        x = new java.io.ByteArrayOutputStream();
        d = new DataOutputStream(x);
        List<Integer> keys = new ArrayList<Integer>();
        for (int i = 0; i < s.size(); i++) if (s.get(i).sync) keys.add(Integer.valueOf(i + 1));
        d.writeInt(0);
        d.writeInt(keys.size());
        for (Integer k : keys) d.writeInt(k.intValue());
        b.write(box("stss", x.toByteArray()));
      }
      // Chunks of this track, in file order.
      List<Chunk> mine = new ArrayList<Chunk>();
      for (Chunk c : chunks) if (c.track == t) mine.add(c);
      x = new java.io.ByteArrayOutputStream();
      d = new DataOutputStream(x);
      List<long[]> sc = new ArrayList<long[]>();
      for (int i = 0; i < mine.size(); i++) {
        long per = mine.get(i).to - mine.get(i).from;
        if (sc.isEmpty() || sc.get(sc.size() - 1)[1] != per) sc.add(new long[] {i + 1, per});
      }
      d.writeInt(0);
      d.writeInt(sc.size());
      for (long[] r : sc) {
        d.writeInt((int) r[0]);
        d.writeInt((int) r[1]);
        d.writeInt(1);
      }
      b.write(box("stsc", x.toByteArray()));
      x = new java.io.ByteArrayOutputStream();
      d = new DataOutputStream(x);
      d.writeInt(0);
      d.writeInt(0);
      d.writeInt(s.size());
      for (Sample one : s) d.writeInt(one.size);
      b.write(box("stsz", x.toByteArray()));
      x = new java.io.ByteArrayOutputStream();
      d = new DataOutputStream(x);
      d.writeInt(0);
      d.writeInt(mine.size());
      for (Chunk c : mine) {
        if (big) d.writeLong(c.offset);
        else d.writeInt((int) c.offset);
      }
      b.write(box(big ? "co64" : "stco", x.toByteArray()));
      return box("stbl", b.toByteArray());
    }

    /** A copy of a movie or media header with its duration (and, for the movie, the next track id). */
    static byte[] withDuration(byte[] header, long duration, boolean movie, int nextTrack) {
      byte[] h = header.clone();
      boolean v1 = h[8] == 1;
      int at = v1 ? 8 + 4 + 16 + 4 : 8 + 4 + 8 + 4;
      if (v1) put64(h, at, duration);
      else put32(h, at, Math.min(duration, 0xffffffffL));
      if (movie) put32(h, h.length - 4, nextTrack);
      return h;
    }

    static byte[] box(String type, byte[] payload) {
      byte[] b = new byte[8 + payload.length];
      put32(b, 0, b.length);
      for (int i = 0; i < 4; i++) b[4 + i] = (byte) type.charAt(i);
      System.arraycopy(payload, 0, b, 8, payload.length);
      return b;
    }

    static long u32(byte[] d, int at) {
      return ((d[at] & 0xffL) << 24) | ((d[at + 1] & 0xffL) << 16) | ((d[at + 2] & 0xffL) << 8) | (d[at + 3] & 0xffL);
    }

    static long u64(byte[] d, int at) {
      return (u32(d, at) << 32) | u32(d, at + 4);
    }

    static void put32(byte[] d, int at, long v) {
      d[at] = (byte) (v >>> 24);
      d[at + 1] = (byte) (v >>> 16);
      d[at + 2] = (byte) (v >>> 8);
      d[at + 3] = (byte) v;
    }

    static void put64(byte[] d, int at, long v) {
      put32(d, at, v >>> 32);
      put32(d, at + 4, v);
    }

    static String fourcc(int v) {
      return new String(new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v}, StandardCharsets.ISO_8859_1);
    }
  }
}
