import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
 * SogniChat: a question to Sogni's hosted chat model (Sogni Intelligence), answered in text.
 *
 * Plain text chat: Sogni's creative tools are off, so a reply never starts a paid image or music
 * job; the chat itself spends a little Sogni credit (Spark). The key is found as SogniMusic finds
 * it: SOGNI_API_KEY, --key_file, the key file in File > Drum Midi Settings, or
 * ~/.config/sogni/credentials.
 *
 * --prompt is the question. --file adds a file after it, and can be given more than once:
 *   - text (a CompareHits or DrumMidi log, a lyric sheet, notes); a Pulsekit .prompt sheet gives
 *     its prompt text;
 *   - MIDI, turned into text the model can read: tempo, time signature, then each track bar by bar
 *     with its notes (drum names on channel 10);
 *   - a PNG or JPEG picture, sent as an image for a model that sees pictures;
 *   - with Sogni's tools on (--tools, --run_tools, --unlimited), also pictures (PNG, JPEG, WebP,
 *     GIF), audio (MP3, WAV, FLAC, M4A) and video (MP4, MOV, WebM) up to 100 MB: each is uploaded to
 *     Sogni's media storage, as Sogni's CLI does, and named in the request (media_ref_1...) for the
 *     tools to work on: edit or animate a picture, a video to a song, a video restyled.
 *   A picture bigger than the 1024 px on its longest side that Sogni shows the chat model is sent
 *   as a smaller copy: SogniChat shrinks a PNG itself; for a JPEG, --seen_copy <smaller picture>
 *   after its --file gives the copy (Pulsekit makes one before the run). The tools get the whole file.
 *   The saved conversation names these files; --continue does not send them again. --system says how to
 * answer ("You are a drum teacher. Answer briefly."). The reply is printed and saved as a .txt
 * (sogni-chat-<first words>.txt), which lands in Downloads on the phone. An output name given
 * first (kit-ideas) names everything a run saves: kit-ideas.txt, and any tool results
 * kit-ideas-1.png, kit-ideas-2.mp3...; the extensions come from what Sogni sends.
 *
 * --saveprompt also writes the question as a Pulsekit prompt sheet named as the saved chat
 * (kit-ideas.prompt: category Writing, type AI, the --file names as its reference files, the system
 * text under the question), with the saved chat as its result file: Pulsekit keeps it in the prompt
 * library, the chat .txt as a result file.
 *
 * --models lists the chat models Sogni offers; --model picks one.
 *
 * --tools offers the model Sogni's creative tools (generate_image, generate_music, edit_image...).
 * The chat never runs them itself: the model proposes tool calls, which are shown and kept in the
 * saved conversation. --run_tools runs the proposed calls as a Sogni workflow (paid: --max_cost
 * caps it in capacity units, --confirm_cost confirms the charge), follows it, and saves what it
 * made beside the conversation (<name>-1.png, <name>-2.mp3...).
 *
 * --unlimited is for a Sogni Unlimited Plan, where cost does not matter and only Sogni's daily and
 * monthly fair use limits apply: the question goes to Sogni as a durable chat run, where Sogni runs
 * the model and its tools; SogniChat follows the run (the tools' progress) and saves its reply and
 * results the same way. The run goes on at Sogni when the connection drops or SogniChat stops
 * waiting (30 minutes): --run <run id> (the id is in the log) follows it again and keeps what it made. Past a fair use limit Sogni refuses
 * the task until the limit renews, and SogniChat says so.
 *
 * Sogni's Safe Content Filter is off for SogniChat's runs (the chat and the tools it runs), so a
 * run is not paused for a safety review; --filter_on turns it on.
 *
 * The saved file is the whole conversation (model, system text, each question and reply), so
 * --continue <that file> goes on from it: the earlier turns are sent again with the new --prompt,
 * and the longer conversation is saved as a new file (sogni-chat-<words>-2.txt, -3...). Its model
 * and system text carry on unless --model or --system are given. A file holding only a reply
 * (from SogniChat before this) continues as that one reply.
 */
public final class SogniChat {
  public static void main(String[] args) throws Exception {
    int code = run(args);
    // Inside Pulsekit (PyJav on the phone runs programs in the app's own process, and sets
    // pulsekit.work) System.exit would close the app, so only a separate run exits with the code.
    if (code != 0 && System.getProperty("pulsekit.work") == null) System.exit(code);
  }

  /** Printed first, so a run's log shows which SogniChat ran. */
  static final String VERSION = "SogniChat 2026-10-10";

  /** The first line of a saved conversation, and the lines that start each turn in it. */
  static final String HEAD = "SogniChat conversation";
  static final String YOU = "=== You ===";
  static final String SOGNI = "=== Sogni ===";

  /** The most of a --file that is sent: chat models read a limited amount of text. */
  static final int FILE_MAX = 60000;

  /** The largest picture sent (7 MB; a data: URI is a third bigger). */
  static final int IMAGE_MAX = 7 * 1024 * 1024;

  /** The longest side, in pixels, of a picture shown to the chat model (Sogni's limit). */
  static final int INLINE_SIDE = 1024;

  /** The largest file uploaded for Sogni's tools (Sogni's own limit: 100 MB). */
  static final int UPLOAD_MAX = 100 * 1024 * 1024;

  /**
   * The work folder this run writes in, read when the run starts. On the phone PyJav runs programs
   * inside the app and gives each run its own folder through pulsekit.work, one setting for the
   * whole app: a run still waiting when the next one starts would otherwise save into that one's
   * folder, and its file would be reported by the wrong run.
   */
  static String work;

  /** The program; returns its exit code (0 ok, 1 failed, 2 bad arguments). */
  static int run(String[] typed) throws Exception {
    work = System.getProperty("pulsekit.work");
    System.out.println(VERSION);
    String[] args = tidy(typed);
    String out = null;
    String prompt = null;
    String system = null;
    List<String> files = new ArrayList<String>();
    List<String> copies = new ArrayList<String>();
    String earlier = null;
    String model = null;
    String keyFile = null;
    String apiBase = null;
    double maxTokens = 0;
    boolean thinking = false;
    boolean models = false;
    boolean tools = false;
    boolean runTools = false;
    boolean unlimited = false;
    boolean confirm = false;
    boolean savePrompt = false;
    double maxCost = 0;
    String rejoin = null;
    // SogniChat runs with Sogni's Safe Content Filter off unless --filter_on asks for it, on every run.
    SogniApi.noFilter = true;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if (a.equals("--prompt") && i + 1 < args.length) prompt = args[++i];
      else if (a.equals("--system") && i + 1 < args.length) system = args[++i];
      else if (a.equals("--file") && i + 1 < args.length) {
        String f = args[++i].trim();
        if (f.length() > 0) {
          files.add(f);
          copies.add(null);
        }
      }
      else if (a.equals("--seen_copy") && i + 1 < args.length) {
        String c = args[++i].trim();
        if (!copies.isEmpty() && c.length() > 0) copies.set(copies.size() - 1, c);
      }
      else if (a.equals("--continue") && i + 1 < args.length) earlier = args[++i];
      else if (a.equals("--model") && i + 1 < args.length) model = args[++i].trim();
      else if (a.equals("--max_tokens") && i + 1 < args.length) maxTokens = number(a, args[++i]);
      else if (a.equals("--key_file") && i + 1 < args.length) keyFile = args[++i];
      else if (a.equals("--api_base") && i + 1 < args.length) apiBase = args[++i];
      else if (a.equals("--thinking")) thinking = true;
      else if (a.equals("--models")) models = true;
      else if (a.equals("--tools")) tools = true;
      else if (a.equals("--run_tools")) runTools = true;
      else if (a.equals("--unlimited")) unlimited = true;
      // --no_filter is the default now; kept so saved arguments that have it still work.
      else if (a.equals("--no_filter")) continue;
      else if (a.equals("--filter_on")) SogniApi.noFilter = false;
      else if (a.equals("--confirm_cost")) confirm = true;
      else if (a.equals("--saveprompt")) savePrompt = true;
      else if (a.equals("--max_cost") && i + 1 < args.length) maxCost = number(a, args[++i]);
      else if (a.equals("--run") && i + 1 < args.length) {
        String id = args[++i].trim();
        if (id.length() > 0) rejoin = id;
      }
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
    if (Double.isNaN(maxTokens) || Double.isNaN(maxCost)) return 2;
    if (maxTokens < 0 || maxTokens > 32000) {
      System.out.println("Failed: --max_tokens is 1 to 32000 (leave it out for Sogni's default)");
      return 2;
    }
    if (rejoin != null) {
      // --run follows a chat run started earlier: its question is already at Sogni.
      if ((prompt != null && prompt.trim().length() > 0) || !files.isEmpty()) {
        System.out.println("Note: --run follows chat run " + rejoin + "; --prompt and --file are not sent");
      }
      prompt = null;
      files.clear();
      copies.clear();
      unlimited = true;
    }
    // With Sogni's tools on, pictures, audio and video are uploaded for the tools to work on.
    boolean toolsOn = tools || runTools || unlimited;
    List<Attachment> attached = new ArrayList<Attachment>();
    for (int k = 0; k < files.size(); k++) {
      Attachment a = attachment(new File(files.get(k)), toolsOn, copies.get(k));
      if (a == null) return 2;
      attached.add(a);
    }
    Conversation before = null;
    if (earlier != null && earlier.trim().length() > 0) {
      before = conversation(new File(earlier.trim()));
      if (before == null) return 2;
    }
    boolean asked = (prompt != null && prompt.trim().length() > 0) || !attached.isEmpty();
    if (!asked && !models && rejoin == null) {
      System.out.println(before != null
          ? "Failed: give --prompt with the next question to continue " + new File(earlier.trim()).getName()
          : "Failed: give --prompt (the question), --file (a text, MIDI or picture file to send), or both, "
              + "for example --prompt \"Suggest a fill for a rock groove at 120 BPM\"");
      usage();
      return 2;
    }
    // A continued conversation keeps its model and system text unless new ones are given.
    if (before != null && (model == null || model.length() == 0)) model = before.model;
    if (before != null && (system == null || system.trim().length() == 0)) system = before.system;
    String key = SogniApi.findKey(keyFile);
    if (key == null) {
      System.out.println("Failed: no Sogni API key. Choose a key file in File > Drum Midi Settings (Sogni API key file), give --key_file "
          + "with a text file holding SOGNI_API_KEY=<your key>, or set SOGNI_API_KEY. Get the key at https://dashboard.sogni.ai (account menu).");
      return 1;
    }
    SogniApi api = new SogniApi(apiBase, key);
    // A long answer takes a while to write; the reply comes in one piece.
    api.timeoutMs = 180000;
    try {
      if (models) {
        List<String> ids = api.chatModels();
        System.out.println("Chat models (" + ids.size() + "):");
        for (String id : ids) System.out.println("  " + id + (id.equals(SogniApi.CHAT_MODEL) ? "  (default)" : ""));
        if (!asked) {
          System.out.println("Succeeded: listed " + ids.size() + " models");
          return 0;
        }
      }
      // Files for the tools go to Sogni's media storage first; the request then names them.
      List<Map<String, Object>> media = new ArrayList<Map<String, Object>>();
      for (Attachment a : attached) {
        if (a.data == null) continue;
        Map<String, Object> ref = api.uploadMedia(a.kind, a.mime, a.data, media.size() + 1, a.name);
        a.ref = SogniApi.str(ref.get("id"));
        a.url = SogniApi.str(ref.get("url"));
        media.add(ref);
        System.out.println("Uploaded " + a.name + " (" + size(a.data.length) + ") as " + a.ref);
        a.data = null;
      }
      // The model sees pictures as uploaded URLs, as Sogni's own apps send them (a data: URI in the
      // request was not always shown to a vision model): the smaller copy of a big one. A chat run
      // (Unlimited Plan) takes only URLs; otherwise a picture that cannot be uploaded goes inline.
      int views = 0;
      for (Attachment a : attached) {
        if (a.seen == null) continue;
        if (!a.shrunk && a.url != null) {
          a.seenUrl = a.url;
          continue;
        }
        String copy = a.name.replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + "-seen" + ("image/png".equals(a.seenMime) ? ".png" : ".jpg");
        try {
          a.seenUrl = SogniApi.str(api.uploadMedia("image", a.seenMime, a.seen, 90 + (++views), copy).get("url"));
        } catch (IOException ex) {
          if (unlimited) throw ex;
          System.out.println("Note: could not upload " + a.name + " for the model to see (" + ex.getMessage() + "); it is sent inside the request");
        }
        if (a.seenUrl != null && a.seenUrl.length() == 0) a.seenUrl = null;
      }
      String question = question(prompt, attached);
      List<String[]> turns = new ArrayList<String[]>();
      if (before != null) {
        turns.addAll(before.turns);
        System.out.println("Continuing " + new File(earlier.trim()).getName() + ": " + exchanges(before.turns) + " earlier "
            + (exchanges(before.turns) == 1 ? "exchange" : "exchanges"));
      }
      List<String> sent = new ArrayList<String>();
      sent.add("user");
      sent.add(question);
      for (Attachment a : attached) if (a.image != null) sent.add(a.seenUrl != null ? a.seenUrl : a.image);
      turns.add(sent.toArray(new String[0]));
      List<String[]> runTurns = new ArrayList<String[]>(turns);
      if (unlimited) {
        List<String> linked = new ArrayList<String>(sent.subList(0, 2));
        for (Attachment a : attached) if (a.seenUrl != null) linked.add(a.seenUrl);
        runTurns.set(runTurns.size() - 1, linked.toArray(new String[0]));
      }
      String chosen = model == null || model.length() == 0 ? SogniApi.CHAT_MODEL : model;
      if (rejoin == null) {
        System.out.println("Model " + chosen + (thinking ? ", thinking" : ""));
        if (SogniApi.noFilter) System.out.println("Content filter: off (Sogni's Safe Content Filter does not check this run)");
        if (system != null && system.trim().length() > 0) System.out.println("System: " + system.trim());
        StringBuilder shown = new StringBuilder("Prompt: ").append(prompt == null ? "" : prompt.trim());
        for (Attachment a : attached) shown.append(shown.length() > 8 ? " " : "").append("[+ ").append(a.what).append(']');
        System.out.println(shown);
      }
      // --run_tools offers the tools too; the model only proposes calls, which run below as a workflow.
      // --unlimited (an Unlimited Plan: no cost, only fair use limits) lets Sogni run them in the chat.
      boolean offered = toolsOn;
      if (rejoin != null) {
        System.out.println("Sogni tools: Unlimited Plan, the chat run " + rejoin + " started earlier");
      } else if (unlimited) {
        System.out.println("Sogni tools: Unlimited Plan, run in the chat (no cost limit; Sogni's daily and monthly fair use limits apply)");
        if (maxCost > 0) System.out.println("Note: --max_cost is not used with the Unlimited Plan");
      } else if (offered) {
        System.out.println("Sogni tools: offered" + (runTools ? "; proposed calls run as a workflow (paid"
            + (maxCost > 0 ? ", at most " + SogniApi.number(maxCost) + " capacity units" : "") + ")" : "; proposed calls are shown, not run"));
      }
      if (runTools && !unlimited && !media.isEmpty()) {
        System.out.println("Note: uploaded files reach Sogni's tools inside the chat (Unlimited Plan); a proposed call run by --run_tools does not get them");
      }
      Object payload = null;
      String reply;
      List<String[]> calls = new ArrayList<String[]>();
      List<String> started = new ArrayList<String>();
      List<Map<String, Object>> runMedia = new ArrayList<Map<String, Object>>();
      String stopped = null;
      if (unlimited) {
        // A durable chat run: Sogni runs the model and the tools on its side and the run goes on
        // if the connection drops, so a video that takes minutes does not time out the request.
        String runId = rejoin;
        if (runId == null) {
          Map<String, Object> begun = api.startChatRun(SogniApi.chatRunInput(chosen, system, runTurns, (int) maxTokens, thinking, media));
          runId = SogniApi.str(begun.get("runId"));
          if (runId == null) runId = SogniApi.str(begun.get("id"));
          if (runId == null) {
            System.out.println("Failed: Sogni did not say which chat run it started");
            return 1;
          }
        }
        System.out.println("Chat run " + runId + (rejoin == null ? " started" : "") + "; following it (if this stops, --run " + runId + " follows it again)");
        Map<String, Object> run;
        try {
          run = api.waitForRun(runId, 30 * 60 * 1000L, new SogniApi.Log() {
            public void line(String s) {
              System.out.println(s);
            }
          });
        } catch (SogniApi.ApiException ex) {
          throw ex;
        } catch (IOException ex) {
          System.out.println("Failed: " + ex.getMessage() + ". The run goes on at Sogni: --run " + runId + " follows it again and keeps what it made.");
          return 1;
        }
        if (rejoin != null) {
          // The conversation saved is the run's own question and its answer.
          String q = SogniApi.runQuestion(run);
          if (q != null) {
            question = q;
            prompt = q.split("\n\n")[0];
          }
          String m = SogniApi.runModel(run);
          if (m != null) chosen = m;
          System.out.println("Model " + chosen);
          if (SogniApi.noFilter) System.out.println("Content filter: off (Sogni's Safe Content Filter does not check this run)");
          System.out.println("Prompt: " + (prompt == null ? "(not given by Sogni)" : prompt));
        }
        String status = SogniApi.str(run.get("status"));
        reply = SogniApi.runReply(run);
        runMedia = SogniApi.runArtifacts(run);
        if (runMedia.isEmpty()) started = SogniApi.runWorkflows(run);
        if (!"completed".equals(status)) {
          String why = SogniApi.runProblem(run);
          stopped = "the chat run " + runId + " " + ("waiting_for_user".equals(status) ? "is waiting" : status == null ? "stopped" : status.replace('_', ' '))
              + (why != null ? ": " + why : "");
          if (runMedia.isEmpty() && started.isEmpty() && (reply == null || reply.length() == 0)) {
            System.out.println("Failed: " + stopped);
            return 1;
          }
        }
      } else {
        payload = api.chat(SogniApi.chatInput(chosen, system, turns, (int) maxTokens, thinking, offered ? "creative-tools" : null, false, media));
        reply = SogniApi.chatReply(payload);
        calls = SogniApi.chatToolCalls(payload);
        started = SogniApi.chatWorkflows(payload);
      }
      if ((reply == null || reply.length() == 0) && calls.isEmpty() && started.isEmpty() && runMedia.isEmpty()) {
        System.out.println("Failed: Sogni sent no reply text" + (thinking ? " (with --thinking the answer can run out of tokens: raise --max_tokens)" : ""));
        return 1;
      }
      if (reply == null) reply = "";
      if (!calls.isEmpty()) {
        // The saved conversation keeps the proposal, so a --continue knows what was suggested.
        StringBuilder proposed = new StringBuilder(reply.length() > 0 ? reply + "\n\n" : "").append(unlimited ? "[Sogni tool calls]" : "[Sogni tool calls proposed]");
        for (String[] c : calls) proposed.append("\n- ").append(c[0]).append(' ').append(c[1]);
        reply = proposed.toString();
      }
      // The saved conversation keeps the question's text; a picture is named in it, not stored.
      turns.set(turns.size() - 1, new String[] {"user", question});
      System.out.println();
      System.out.println(reply);
      System.out.println();
      if (stopped != null) System.out.println("Note: " + stopped);
      String usage = payload == null ? null : SogniApi.chatUsage(payload);
      if (usage != null) System.out.println("Tokens: " + usage);
      turns.add(new String[] {"assistant", reply});
      // An output name is a base: the chat is <name>.txt and results <name>-1.png...; an extension given is dropped.
      String name = out != null ? out.trim().replaceAll("\\.[A-Za-z0-9]{1,5}$", "") + ".txt" : before != null ? continuedName(new File(earlier.trim()).getName(), exchanges(turns)) : replyName(prompt, files.isEmpty() ? null : files.get(0));
      File saved = save(name, transcript(chosen, system, turns));
      if (saved == null) System.out.println("Could not save the reply (it is in the log above)");
      else System.out.println("Wrote " + saved.getName());
      if (savePrompt && saved != null) {
        List<String> names = new ArrayList<String>();
        for (String f : files) names.add(new File(f.trim()).getName());
        File sheet = savePrompt(saved.getName().replaceAll("\\.[^.]*$", ""), chosen, prompt, system, names, saved.getName(), stopped);
        if (sheet == null) System.out.println("Could not save the prompt sheet");
        else System.out.println("Wrote " + sheet.getName() + " (the prompt, with " + saved.getName() + " as its result file)");
      }
      List<String> made = new ArrayList<String>();
      if (saved != null) made.add(saved.getName());
      if (unlimited && !runMedia.isEmpty()) {
        // The chat run made them: keep them beside the conversation.
        List<String> kept = saveMedia(api, runMedia, name.replaceAll("\\.[^.]*$", ""));
        made.addAll(kept);
      } else if (unlimited && !started.isEmpty()) {
        // Sogni ran the tools in the chat: follow what it started and keep the results.
        List<String> results = follow(api, started, name.replaceAll("\\.[^.]*$", ""));
        if (results == null) return 1;
        made.addAll(results);
      } else if (unlimited && !calls.isEmpty()) {
        // The model proposed calls but Sogni started nothing: run them, with no cost limit.
        List<String> results = runCalls(api, calls, name.replaceAll("\\.[^.]*$", ""), true, 0);
        if (results == null) return 1;
        made.addAll(results);
      } else if (!calls.isEmpty() && !runTools) {
        System.out.println("Not run: tick Run tools (--run_tools) to run the proposed " + (calls.size() == 1 ? "call" : "calls")
            + " (paid; --max_cost limits the spend). --continue with this conversation keeps the proposal.");
      } else if (!calls.isEmpty()) {
        String stem = name.replaceAll("\\.[^.]*$", "");
        List<String> results = runCalls(api, calls, stem, confirm, maxCost);
        if (results == null) return 1;
        made.addAll(results);
      } else if (offered) {
        System.out.println("No tool calls: the model answered in text");
      }
      System.out.println(made.isEmpty() ? "Succeeded" : "Succeeded: " + join(made));
    } catch (SogniApi.ApiException ex) {
      boolean picture = false;
      for (Attachment a : attached) if (a.image != null) picture = true;
      String said = String.valueOf(ex.getMessage()).toLowerCase();
      System.out.println("Failed: " + ex.getMessage() + (picture && ex.status == 400 && !said.contains("dimension") && !said.contains("exceeds")
          ? " (a picture needs a model that sees pictures: try --model deepseek-v4-flash-vision-exp-dspark-1m)" : ""));
      if (unlimited && fairUse(ex)) {
        System.out.println("Sogni's fair use limit is reached, so it does not run the task now. Try again when the daily limit renews"
            + (ex.retryAfter > 0 ? " (Sogni says in about " + wait(ex.retryAfter) + ")" : "") + ".");
      }
      return 1;
    } catch (IOException ex) {
      System.out.println("Failed: " + ex.getMessage());
      return 1;
    }
    return 0;
  }

  static void usage() {
    System.out.println("Usage: java SogniChat [output_name] [--prompt text] [--file notes.txt|song.mid|picture.jpg] [--continue chat.txt] [--system text] [--model id] "
        + "[--max_tokens N] [--thinking] [--models] [--tools] [--run_tools] [--unlimited] [--filter_on] [--run run_id] [--max_cost N] [--confirm_cost] [--saveprompt] [--key_file credentials.txt]");
  }

  /**
   * Runs the model's tool calls as one Sogni workflow (a step each), follows it, and saves what it
   * made as <stem>-1.png, <stem>-2.mp3...: the file names, or null after saying why it failed.
   * Steps run side by side; a call that needs another's result is not linked to it.
   */
  static List<String> runCalls(SogniApi api, List<String[]> calls, String stem, boolean confirm, double maxCost) throws IOException {
    String id = api.start(SogniApi.toolsInput("Pulsekit chat", calls), confirm, maxCost);
    List<String> ids = new ArrayList<String>();
    ids.add(id);
    return follow(api, ids, stem);
  }

  /**
   * Follows these workflows to their end and saves every picture, audio and video they made as
   * <stem>-1.png, <stem>-2.mp3...: the file names, or null after saying why nothing was made.
   */
  static List<String> follow(SogniApi api, List<String> ids, String stem) throws IOException {
    List<Map<String, Object>> media = new ArrayList<Map<String, Object>>();
    List<String> notes = new ArrayList<String>();
    for (String id : ids) {
      System.out.println("Workflow: " + id);
      Map<String, Object> wf = api.waitFor(id, 20 * 60 * 1000L, new SogniApi.Log() {
        public void line(String s) {
          System.out.println(s);
        }
      });
      List<Map<String, Object>> found = SogniApi.mediaArtifacts(wf);
      media.addAll(found);
      String status = SogniApi.str(wf.get("status"));
      if (found.isEmpty()) {
        String why = SogniApi.problem(wf);
        notes.add(id + ": " + (why != null ? why : "status " + status));
      } else if (!"completed".equals(status)) {
        System.out.println("Note: workflow " + id + " " + status);
      }
    }
    if (media.isEmpty()) {
      System.out.println("Failed: no picture, audio or video was made (" + join(notes) + ")");
      return null;
    }
    for (String n : notes) System.out.println("Note: " + n);
    return saveMedia(api, media, stem);
  }

  /** Downloads each result and saves it as <stem>-1.png, <stem>-2.mp4...; returns the names saved. */
  static List<String> saveMedia(SogniApi api, List<Map<String, Object>> media, String stem) throws IOException {
    List<String> names = new ArrayList<String>();
    for (int i = 0; i < media.size(); i++) {
      String url = SogniApi.str(media.get(i).get("url"));
      String ext = SogniApi.mediaExtension(url, SogniApi.mimeOf(media.get(i)), ".bin");
      byte[] data = api.download(url);
      File file = saveData(stem + "-" + (i + 1) + ext, data);
      if (file == null) {
        System.out.println("Could not save " + stem + "-" + (i + 1) + ext);
        continue;
      }
      System.out.println("Wrote " + file.getName() + " (" + (data.length / 1024) + " KB)");
      names.add(file.getName());
    }
    return names;
  }

  /** A refusal for Sogni's fair use limits: a rate limit (429), or a message about a daily or monthly limit. */
  static boolean fairUse(SogniApi.ApiException ex) {
    String m = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
    return ex.status == 429 || m.contains("fair use") || m.contains("daily limit") || m.contains("monthly limit") || m.contains("quota");
  }

  /** "3 h 20 min", "12 min" or "45 s" for a wait in seconds. */
  static String wait(int seconds) {
    if (seconds >= 3600) return (seconds / 3600) + " h " + ((seconds % 3600) / 60) + " min";
    if (seconds >= 60) return (seconds / 60) + " min";
    return seconds + " s";
  }

  /** "a, b, c". */
  static String join(List<String> names) {
    StringBuilder sb = new StringBuilder();
    for (String n : names) sb.append(sb.length() > 0 ? ", " : "").append(n);
    return sb.toString();
  }

  /** A --file as it is sent: text (a text file, or a MIDI file read out), a picture, audio or video. */
  static final class Attachment {
    String name;
    /** "text", "image", "audio" or "video". */
    String kind = "text";
    /** The text sent after the prompt, or null for a picture, audio or video. */
    String text;
    /** A picture as a data: URI the model sees, or null. */
    String image;
    /** The file's MIME type, and its bytes until it is uploaded for Sogni's tools (null when it is not). */
    String mime;
    byte[] data;
    /** Its media reference once uploaded (media_ref_1...), and the uploaded file's URL; null when not uploaded. */
    String ref;
    String url;
    /**
     * The picture the model sees (the file, or a smaller copy: shrunk), for a chat run, which takes
     * pictures as uploaded URLs; seenUrl once uploaded.
     */
    byte[] seen;
    String seenMime;
    boolean shrunk;
    String seenUrl;
    /** For the log: "notes.txt, 34 characters". */
    String what;
  }

  /**
   * What is sent as text: the prompt, then each file under its name. A picture, audio or video is
   * named (the picture also goes with the message); uploaded ones are listed by their media
   * reference, as Sogni's own CLI lists them, so the tools can be pointed at them.
   */
  static String question(String prompt, List<Attachment> attached) {
    StringBuilder sb = new StringBuilder(prompt == null ? "" : prompt.trim());
    StringBuilder refs = new StringBuilder();
    for (Attachment a : attached) {
      if (sb.length() > 0) sb.append("\n\n");
      if ("text".equals(a.kind)) {
        sb.append("File ").append(a.name).append(":\n").append(a.text);
        continue;
      }
      String what = "image".equals(a.kind) ? "Picture" : "audio".equals(a.kind) ? "Audio" : "Video";
      sb.append(what).append(' ').append(a.name).append(" is attached").append(a.ref != null ? " as " + a.ref : "").append('.');
      if (a.ref != null) {
        String flag = "audio".equals(a.kind) ? "--ref-audio" : "video".equals(a.kind) ? "--ref-video" : "-c/--context";
        refs.append("\n- ").append(a.ref).append(' ').append(a.kind).append(" (").append(flag).append("): ").append(a.name);
      }
    }
    if (refs.length() > 0) sb.append("\n\nAPI media references:").append(refs);
    return sb.toString();
  }

  /** "820 KB" or "3.4 MB". */
  static String size(long bytes) {
    if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
    return beats(bytes / (1024.0 * 1024.0)) + " MB";
  }

  /** A --file read by its contents: MIDI (MThd), a PNG or JPEG picture, or text. Null after saying why. */
  static Attachment attachment(File f, boolean tools, String copy) {
    byte[] data = readBytes(f, "--file");
    if (data == null) return null;
    Attachment a = new Attachment();
    a.name = f.getName();
    if (data.length >= 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd') {
      a.text = midiText(f.getName(), data);
      if (a.text == null) return null;
      if (a.text.length() > FILE_MAX) {
        int cut = a.text.lastIndexOf('\n', FILE_MAX);
        a.text = a.text.substring(0, cut > 0 ? cut : FILE_MAX) + "\n(the rest of the song is left out: too long to send)";
        System.out.println("Note: " + f.getName() + " is long; its first part is sent");
      }
      a.what = f.getName() + " as text, " + a.text.length() + " characters";
      return a;
    }
    String[] media = mediaType(data);
    if (media != null) {
      a.kind = media[0];
      a.mime = media[1];
      boolean seen = "image/png".equals(a.mime) || "image/jpeg".equals(a.mime);
      byte[] inline = seen && data.length <= IMAGE_MAX ? data : null;
      String inlineMime = a.mime;
      int[] px = seen ? pixelSize(data) : null;
      byte[] given = copy == null ? null : readBytes(new File(copy), "--seen_copy");
      int[] givenPx = given == null ? null : pixelSize(given);
      if (seen && givenPx != null && Math.max(givenPx[0], givenPx[1]) <= INLINE_SIDE && given.length <= IMAGE_MAX) {
        // A smaller copy made before the run (Pulsekit makes one of a big JPEG): the model sees it.
        inline = given;
        inlineMime = pictureType(given);
        if (px != null) {
          System.out.println("Note: " + f.getName() + " is " + px[0] + "x" + px[1] + " pixels; the chat model sees a " + givenPx[0] + "x" + givenPx[1]
              + " copy (Sogni shows it pictures of up to " + INLINE_SIDE + " px on the longest side)" + (tools ? "; the tools get the full picture" : ""));
        }
      } else if (inline != null && px != null && Math.max(px[0], px[1]) > INLINE_SIDE) {
        // Sogni shows the chat model pictures of up to 1024 px on the longest side: a PNG is sent as
        // a smaller copy; the tools still get the whole file.
        inline = "image/png".equals(a.mime) ? smallerPng(data, INLINE_SIDE) : null;
        int[] small = inline == null ? null : pixelSize(inline);
        String big = f.getName() + " is " + px[0] + "x" + px[1] + " pixels";
        if (small != null) {
          System.out.println("Note: " + big + "; the chat model sees a " + small[0] + "x" + small[1] + " copy (Sogni shows it pictures of up to "
              + INLINE_SIDE + " px on the longest side)" + (tools ? "; the tools get the full picture" : ""));
        } else if (tools) {
          System.out.println("Note: " + big + ", more than the " + INLINE_SIDE + " px on the longest side Sogni shows the chat model,"
              + " so only the tools get it (uploaded at full size)");
        } else {
          System.out.println("Failed: " + big + "; Sogni shows the chat model pictures of up to " + INLINE_SIDE + " px on the longest side."
              + " Save it smaller or as a PNG (SogniChat sends a smaller copy of a PNG), or tick Offer Sogni tools, Run proposed tool calls"
              + " or Unlimited Plan, and the file is uploaded for the tools at full size.");
          return null;
        }
      }
      if (!tools && inline == null) {
        System.out.println("Failed: " + f.getName() + " is " + ("image".equals(a.kind) ? "a picture the chat model cannot see (send a PNG or JPEG of up to "
            + (IMAGE_MAX / (1024 * 1024)) + " MB)" : a.kind + ", which the chat model cannot " + ("audio".equals(a.kind) ? "hear" : "watch"))
            + ". Sogni's tools can use it: tick Offer Sogni tools, Run proposed tool calls or Unlimited Plan, and the file is uploaded for them.");
        return null;
      }
      if (data.length > UPLOAD_MAX) {
        System.out.println("Failed: " + f.getName() + " is " + size(data.length) + "; Sogni takes files of up to " + size(UPLOAD_MAX));
        return null;
      }
      // The model sees a PNG or JPEG picture; with the tools on, every file is also uploaded for them.
      if (inline != null) {
        a.image = "data:" + inlineMime + ";base64," + base64(inline);
        a.seen = inline;
        a.seenMime = inlineMime;
        a.shrunk = inline != data;
      }
      if (tools) a.data = data;
      a.what = ("image".equals(a.kind) ? "picture " : a.kind + " ") + f.getName() + ", " + size(data.length);
      return a;
    }
    a.text = fileText(f);
    if (a.text == null) return null;
    a.what = f.getName() + ", " + a.text.length() + " characters";
    return a;
  }

  /**
   * {kind, MIME type} from a file's first bytes, for what Sogni's tools take: PNG, JPEG, WebP or GIF
   * pictures, MP3, WAV, FLAC or M4A audio, MP4, MOV or WebM video. Null for anything else.
   */
  static String[] mediaType(byte[] d) {
    String picture = pictureType(d);
    if (picture != null) return new String[] {"image", picture};
    if (starts(d, 0, "RIFF") && starts(d, 8, "WEBP")) return new String[] {"image", "image/webp"};
    if (starts(d, 0, "GIF8")) return new String[] {"image", "image/gif"};
    if (starts(d, 0, "RIFF") && starts(d, 8, "WAVE")) return new String[] {"audio", "audio/wav"};
    if (starts(d, 0, "fLaC")) return new String[] {"audio", "audio/flac"};
    if (starts(d, 0, "ID3") || (d.length >= 2 && (d[0] & 0xff) == 0xff && (d[1] & 0xe0) == 0xe0)) return new String[] {"audio", "audio/mpeg"};
    if (starts(d, 4, "ftyp")) {
      String brand = d.length >= 12 ? new String(d, 8, 4, StandardCharsets.ISO_8859_1) : "";
      if (brand.startsWith("M4A") || brand.startsWith("M4B")) return new String[] {"audio", "audio/mp4"};
      if (brand.startsWith("qt")) return new String[] {"video", "video/quicktime"};
      return new String[] {"video", "video/mp4"};
    }
    if (d.length >= 4 && (d[0] & 0xff) == 0x1a && (d[1] & 0xff) == 0x45 && (d[2] & 0xff) == 0xdf && (d[3] & 0xff) == 0xa3) return new String[] {"video", "video/webm"};
    return null;
  }

  static boolean starts(byte[] d, int at, String text) {
    if (d.length < at + text.length()) return false;
    for (int i = 0; i < text.length(); i++) if (d[at + i] != (byte) text.charAt(i)) return false;
    return true;
  }

  /** "image/png" or "image/jpeg" from the file's first bytes, else null. */
  static String pictureType(byte[] d) {
    if (d.length >= 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
    if (d.length >= 3 && (d[0] & 0xff) == 0xff && (d[1] & 0xff) == 0xd8 && (d[2] & 0xff) == 0xff) return "image/jpeg";
    return null;
  }

  /** {width, height} of a PNG (its IHDR) or a JPEG (its SOF marker), or null when unread. */
  static int[] pixelSize(byte[] d) {
    String type = pictureType(d);
    if ("image/png".equals(type)) return d.length >= 24 && starts(d, 12, "IHDR") ? new int[] {bigInt(d, 16), bigInt(d, 20)} : null;
    if (!"image/jpeg".equals(type)) return null;
    int i = 2;
    while (i + 9 < d.length) {
      if ((d[i] & 0xff) != 0xff) return null;
      int m = d[i + 1] & 0xff;
      if (m == 0xff) {
        i++;
        continue;
      }
      if (m == 0x01 || (m >= 0xd0 && m <= 0xd8)) {
        i += 2;
        continue;
      }
      if (m >= 0xc0 && m <= 0xcf && m != 0xc4 && m != 0xc8 && m != 0xcc) {
        return new int[] {((d[i + 7] & 0xff) << 8) | (d[i + 8] & 0xff), ((d[i + 5] & 0xff) << 8) | (d[i + 6] & 0xff)};
      }
      i += 2 + (((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff));
    }
    return null;
  }

  static int bigInt(byte[] d, int at) {
    return ((d[at] & 0xff) << 24) | ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
  }

  /**
   * A PNG at most `side` pixels on its longest side, made by averaging the pixels (no java.awt or
   * javax.imageio on Android). Reads 8- and 16-bit grey, RGB, palette, grey+alpha and RGBA PNGs
   * that are not interlaced; null for others or a damaged file.
   */
  static byte[] smallerPng(byte[] d, int side) {
    try {
      int w = 0, h = 0, depth = 0, type = -1;
      byte[] palette = null, alpha = null;
      ByteArrayOutputStream idat = new ByteArrayOutputStream();
      for (int i = 8; i + 8 <= d.length; ) {
        int len = bigInt(d, i), at = i + 8;
        if (len < 0 || at + len > d.length) return null;
        String kind = new String(d, i + 4, 4, StandardCharsets.ISO_8859_1);
        if (kind.equals("IHDR")) {
          w = bigInt(d, at);
          h = bigInt(d, at + 4);
          depth = d[at + 8] & 0xff;
          type = d[at + 9] & 0xff;
          if (d[at + 12] != 0) return null;
        } else if (kind.equals("PLTE")) {
          palette = java.util.Arrays.copyOfRange(d, at, at + len);
        } else if (kind.equals("tRNS")) {
          alpha = java.util.Arrays.copyOfRange(d, at, at + len);
        } else if (kind.equals("IDAT")) {
          idat.write(d, at, len);
        } else if (kind.equals("IEND")) {
          break;
        }
        i = at + len + 4;
      }
      int channels = type == 0 || type == 3 ? 1 : type == 2 ? 3 : type == 4 ? 2 : type == 6 ? 4 : 0;
      if (w <= 0 || h <= 0 || channels == 0 || (depth != 8 && depth != 16) || (type == 3 && (palette == null || depth != 8))) return null;
      int longest = Math.max(w, h);
      if (longest <= side) return d;
      int nw = Math.max(1, (int) ((long) w * side / longest)), nh = Math.max(1, (int) ((long) h * side / longest));
      boolean keepAlpha = type == 4 || type == 6 || (type == 3 && alpha != null);
      int step = depth / 8, bpp = channels * step;
      InputStream in = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(idat.toByteArray()));
      byte[] row = new byte[w * bpp], prev = new byte[w * bpp];
      long[] sum = new long[nw * 4];
      int[] count = new int[nw];
      ByteArrayOutputStream raw = new ByteArrayOutputStream();
      int done = 0;
      for (int y = 0; y < h; y++) {
        int filter = in.read();
        if (filter < 0) return null;
        for (int got = 0; got < row.length; ) {
          int n = in.read(row, got, row.length - got);
          if (n < 0) return null;
          got += n;
        }
        unfilter(filter, row, prev, bpp);
        int oy = (int) ((long) y * nh / h);
        for (; done < oy; done++) shrunkRow(raw, sum, count, keepAlpha);
        for (int x = 0; x < w; x++) {
          int p = x * bpp, r, g, b, a = 255;
          if (type == 3) {
            int idx = row[p] & 0xff;
            if (idx * 3 + 2 >= palette.length) return null;
            r = palette[idx * 3] & 0xff;
            g = palette[idx * 3 + 1] & 0xff;
            b = palette[idx * 3 + 2] & 0xff;
            if (alpha != null && idx < alpha.length) a = alpha[idx] & 0xff;
          } else if (type == 0 || type == 4) {
            r = g = b = row[p] & 0xff;
            if (type == 4) a = row[p + step] & 0xff;
          } else {
            r = row[p] & 0xff;
            g = row[p + step] & 0xff;
            b = row[p + 2 * step] & 0xff;
            if (type == 6) a = row[p + 3 * step] & 0xff;
          }
          int ox = (int) ((long) x * nw / w);
          sum[ox * 4] += r;
          sum[ox * 4 + 1] += g;
          sum[ox * 4 + 2] += b;
          sum[ox * 4 + 3] += a;
          count[ox]++;
        }
        byte[] t = prev;
        prev = row;
        row = t;
      }
      for (; done < nh; done++) shrunkRow(raw, sum, count, keepAlpha);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
      byte[] head = new byte[13];
      putInt(head, 0, nw);
      putInt(head, 4, nh);
      head[8] = 8;
      head[9] = (byte) (keepAlpha ? 6 : 2);
      pngChunk(png, "IHDR", head);
      ByteArrayOutputStream packed = new ByteArrayOutputStream();
      java.util.zip.Deflater deflater = new java.util.zip.Deflater(9);
      java.util.zip.DeflaterOutputStream z = new java.util.zip.DeflaterOutputStream(packed, deflater);
      z.write(raw.toByteArray());
      z.close();
      deflater.end();
      pngChunk(png, "IDAT", packed.toByteArray());
      pngChunk(png, "IEND", new byte[0]);
      return png.toByteArray();
    } catch (Exception ex) {
      return null;
    }
  }

  /** Undoes a PNG row's filter in place; `prev` is the row above, already undone. */
  static void unfilter(int filter, byte[] row, byte[] prev, int bpp) throws IOException {
    if (filter == 0) return;
    for (int i = 0; i < row.length; i++) {
      int a = i >= bpp ? row[i - bpp] & 0xff : 0, b = prev[i] & 0xff, c = i >= bpp ? prev[i - bpp] & 0xff : 0, add;
      if (filter == 1) add = a;
      else if (filter == 2) add = b;
      else if (filter == 3) add = (a + b) / 2;
      else if (filter == 4) {
        int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        add = pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      } else throw new IOException("bad PNG filter " + filter);
      row[i] = (byte) (row[i] + add);
    }
  }

  /** Writes one row of the smaller picture (the averages so far) and starts the next. */
  static void shrunkRow(ByteArrayOutputStream raw, long[] sum, int[] count, boolean keepAlpha) {
    raw.write(0);
    for (int x = 0; x < count.length; x++) {
      int n = Math.max(1, count[x]);
      for (int c = 0; c < (keepAlpha ? 4 : 3); c++) raw.write((int) (sum[x * 4 + c] / n));
      count[x] = 0;
      for (int c = 0; c < 4; c++) sum[x * 4 + c] = 0;
    }
  }

  static void putInt(byte[] d, int at, int v) {
    d[at] = (byte) (v >>> 24);
    d[at + 1] = (byte) (v >>> 16);
    d[at + 2] = (byte) (v >>> 8);
    d[at + 3] = (byte) v;
  }

  static void pngChunk(ByteArrayOutputStream png, String kind, byte[] data) {
    byte[] len = new byte[4];
    putInt(len, 0, data.length);
    byte[] name = kind.getBytes(StandardCharsets.ISO_8859_1);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(name);
    crc.update(data);
    byte[] check = new byte[4];
    putInt(check, 0, (int) crc.getValue());
    png.write(len, 0, 4);
    png.write(name, 0, 4);
    png.write(data, 0, data.length);
    png.write(check, 0, 4);
  }

  /** Base64 without java.util.Base64 (Android before API 26 has none). */
  static String base64(byte[] d) {
    String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
    for (int i = 0; i < d.length; i += 3) {
      int b0 = d[i] & 0xff;
      int b1 = i + 1 < d.length ? d[i + 1] & 0xff : 0;
      int b2 = i + 2 < d.length ? d[i + 2] & 0xff : 0;
      int v = (b0 << 16) | (b1 << 8) | b2;
      sb.append(abc.charAt((v >> 18) & 63)).append(abc.charAt((v >> 12) & 63));
      sb.append(i + 1 < d.length ? abc.charAt((v >> 6) & 63) : '=');
      sb.append(i + 2 < d.length ? abc.charAt(v & 63) : '=');
    }
    return sb.toString();
  }

  /**
   * A text file's contents, cut to FILE_MAX characters; a Pulsekit .prompt sheet (PKPROMPT1) gives
   * the prompt after its "---" line. Null, after saying why, when it cannot be read.
   */
  static String fileText(File f) {
    String text = readText(f, "--file");
    if (text == null) return null;
    if (text.startsWith("PKPROMPT1")) {
      int at = text.indexOf("\n---\n");
      if (at >= 0) text = text.substring(at + 5);
    }
    text = text.trim();
    if (text.length() == 0) {
      System.out.println("Failed: " + f.getName() + " is empty");
      return null;
    }
    if (text.length() > FILE_MAX) {
      System.out.println("Note: " + f.getName() + " is long; its first " + FILE_MAX + " characters are sent");
      text = text.substring(0, FILE_MAX);
    }
    return text;
  }

  /** A text file's contents, or null after saying why (`flag` names the switch it came from). */
  static String readText(File f, String flag) {
    byte[] data = readBytes(f, flag);
    if (data == null) return null;
    String text = new String(data, StandardCharsets.UTF_8);
    if (text.indexOf('\0') >= 0) {
      System.out.println("Failed: " + f.getName() + " is not a text file"
          + ("--file".equals(flag) ? ", a MIDI file, a picture, audio or video" : " (pick a sogni-chat .txt)"));
      return null;
    }
    return text.replace("\r\n", "\n");
  }

  /** A file's bytes, or null after saying why (`flag` names the switch it came from). */
  static byte[] readBytes(File f, String flag) {
    if (!f.isFile()) {
      System.out.println("Failed: " + flag + " " + f.getName() + " was not found");
      return null;
    }
    try {
      InputStream in = new FileInputStream(f);
      try {
        return SogniApi.readAll(in);
      } finally {
        in.close();
      }
    } catch (IOException ex) {
      System.out.println("Failed: could not read " + f.getName() + ": " + ex.getMessage());
      return null;
    }
  }

  // ---- MIDI as text ----

  /** General MIDI drum names (channel 10), by note number. */
  static final String[] DRUMS = {
    "Acoustic kick", "Kick", "Side stick", "Snare", "Clap", "Electric snare", "Low floor tom", "Closed hat",
    "High floor tom", "Pedal hat", "Low tom", "Open hat", "Low-mid tom", "High-mid tom", "Crash", "High tom",
    "Ride", "China", "Ride bell", "Tambourine", "Splash", "Cowbell", "Crash 2", "Vibraslap", "Ride 2",
    "High bongo", "Low bongo", "Muted high conga", "Open high conga", "Low conga", "High timbale",
    "Low timbale", "High agogo", "Low agogo", "Cabasa", "Maracas", "Short whistle", "Long whistle",
    "Short guiro", "Long guiro", "Claves", "High wood block", "Low wood block", "Muted cuica", "Open cuica",
    "Muted triangle", "Open triangle"
  };
  static final String[] NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

  /**
   * A MIDI file as text a chat model can read: the tempo and time signature, then each track (and
   * channel) bar by bar, "Bar 3 | 1: Kick, Closed hat | 1.5: Closed hat | 2: Snare". Beats count
   * quarter notes from 1; a pitched note shows its length in beats, C4 (0.5). Null after saying why.
   */
  static String midiText(String name, byte[] b) {
    try {
      int division = u16(b, 12);
      if ((division & 0x8000) != 0 || division == 0) {
        System.out.println("Failed: " + name + " counts time in SMPTE frames, which SogniChat does not read");
        return null;
      }
      int format = u16(b, 8);
      int tracks = u16(b, 10);
      // {track, channel, tick, key, velocity, length in ticks (-1 until its note-off)}
      List<long[]> notes = new ArrayList<long[]>();
      Map<Integer, String> names = new LinkedHashMap<Integer, String>();
      double bpm = 0;
      int num = 0;
      int den = 4;
      int tempos = 0;
      int sigs = 0;
      long last = 0;
      int pos = 8 + (int) u32(b, 4);
      for (int t = 0; t < tracks && pos + 8 <= b.length; t++) {
        int len = (int) u32(b, pos + 4);
        boolean track = b[pos] == 'M' && b[pos + 1] == 'T' && b[pos + 2] == 'r' && b[pos + 3] == 'k';
        int end = Math.min(b.length, pos + 8 + len);
        int p = pos + 8;
        pos = pos + 8 + len;
        if (!track) continue;
        long tick = 0;
        int status = 0;
        Map<Integer, long[]> open = new LinkedHashMap<Integer, long[]>();
        while (p < end) {
          long[] delta = varLen(b, p);
          tick += delta[0];
          p = (int) delta[1];
          int c = b[p] & 0xff;
          if (c == 0xff) {
            int type = b[p + 1] & 0xff;
            long[] l = varLen(b, p + 2);
            int at = (int) l[1];
            int n = (int) l[0];
            if (type == 0x51 && n >= 3) {
              double next = 60000000.0 / (((b[at] & 0xff) << 16) | ((b[at + 1] & 0xff) << 8) | (b[at + 2] & 0xff));
              if (bpm == 0) bpm = next;
              else if (Math.abs(next - bpm) > 0.01) tempos++;
            } else if (type == 0x58 && n >= 2) {
              if (num == 0) {
                num = b[at] & 0xff;
                den = 1 << (b[at + 1] & 0xff);
              } else {
                sigs++;
              }
            } else if (type == 0x03 && n > 0 && !names.containsKey(Integer.valueOf(t))) {
              names.put(Integer.valueOf(t), new String(b, at, n, StandardCharsets.UTF_8).trim());
            }
            p = at + n;
          } else if (c == 0xf0 || c == 0xf7) {
            long[] l = varLen(b, p + 1);
            p = (int) (l[1] + l[0]);
          } else {
            if (c >= 0x80) {
              status = c;
              p++;
            }
            int kind = status & 0xf0;
            int ch = status & 0x0f;
            if (kind == 0xc0 || kind == 0xd0) {
              p += 1;
              continue;
            }
            int key = b[p] & 0x7f;
            int vel = b[p + 1] & 0x7f;
            p += 2;
            Integer slot = Integer.valueOf(ch * 128 + key);
            if (kind == 0x90 && vel > 0) {
              long[] note = {t, ch, tick, key, vel, -1};
              notes.add(note);
              open.put(slot, note);
              last = Math.max(last, tick);
            } else if (kind == 0x80 || kind == 0x90) {
              long[] note = open.remove(slot);
              if (note != null) note[5] = tick - note[2];
            }
          }
        }
      }
      if (num == 0) num = 4;
      if (bpm == 0) bpm = 120;
      double beatsPerBar = num * 4.0 / den;
      long barTicks = Math.max(1, Math.round(division * beatsPerBar));
      StringBuilder sb = new StringBuilder();
      sb.append("MIDI ").append(name).append(": format ").append(format).append(", ").append(tracks).append(tracks == 1 ? " track" : " tracks")
          .append(", ").append(notes.size()).append(" notes\n");
      sb.append("Tempo ").append(beats(bpm)).append(" BPM").append(tempos > 0 ? " (then " + tempos + " tempo changes)" : "")
          .append(", time signature ").append(num).append('/').append(den).append(sigs > 0 ? " (then " + sigs + " changes)" : "")
          .append(", ").append(last / barTicks + 1).append(" bars. Beats count quarter notes from 1.\n");
      if (notes.isEmpty()) return sb.append("(no notes)\n").toString();
      // One section per track and channel, in the order they first play.
      List<String> order = new ArrayList<String>();
      Map<String, List<long[]>> sections = new LinkedHashMap<String, List<long[]>>();
      for (long[] n : notes) {
        String k = n[0] + ":" + n[1];
        if (!sections.containsKey(k)) {
          sections.put(k, new ArrayList<long[]>());
          order.add(k);
        }
        sections.get(k).add(n);
      }
      for (String k : order) {
        List<long[]> list = sections.get(k);
        int t = (int) list.get(0)[0];
        int ch = (int) list.get(0)[1];
        boolean drums = ch == 9;
        String trackName = names.get(Integer.valueOf(t));
        sb.append("\nTrack ").append(t + 1).append(trackName != null && trackName.length() > 0 ? " \"" + trackName + "\"" : "")
            .append(", channel ").append(ch + 1).append(drums ? " (drums)" : "").append(": ").append(list.size()).append(" notes\n");
        java.util.Collections.sort(list, new java.util.Comparator<long[]>() {
          public int compare(long[] x, long[] y) {
            return x[2] != y[2] ? (x[2] < y[2] ? -1 : 1) : (int) (x[3] - y[3]);
          }
        });
        long bar = -1;
        long at = -1;
        StringBuilder line = null;
        for (long[] n : list) {
          long nb = n[2] / barTicks;
          if (nb != bar) {
            if (line != null) sb.append(line).append('\n');
            bar = nb;
            at = -1;
            line = new StringBuilder("Bar ").append(nb + 1);
          }
          if (n[2] != at) {
            at = n[2];
            line.append(" | ").append(beats(1 + (n[2] - nb * barTicks) / (double) division)).append(": ");
          } else {
            line.append(", ");
          }
          int key = (int) n[3];
          if (drums) line.append(key >= 35 && key - 35 < DRUMS.length ? DRUMS[key - 35] : "Drum " + key);
          else line.append(NOTES[key % 12]).append(key / 12 - 1).append(n[5] > 0 ? " (" + beats(n[5] / (double) division) + ")" : "");
        }
        if (line != null) sb.append(line).append('\n');
      }
      return sb.toString();
    } catch (RuntimeException ex) {
      System.out.println("Failed: " + name + " could not be read as MIDI (" + ex + ")");
      return null;
    }
  }

  /** 1, 1.5, 2.25: a beat position or length, to two decimals. */
  static String beats(double v) {
    String t = String.format(java.util.Locale.ROOT, "%.2f", v);
    while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
    if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
    return t;
  }

  static int u16(byte[] b, int at) {
    return ((b[at] & 0xff) << 8) | (b[at + 1] & 0xff);
  }

  static long u32(byte[] b, int at) {
    return ((long) (b[at] & 0xff) << 24) | ((b[at + 1] & 0xff) << 16) | ((b[at + 2] & 0xff) << 8) | (b[at + 3] & 0xff);
  }

  /** A MIDI variable-length number at `at`: {value, index after it}. */
  static long[] varLen(byte[] b, int at) {
    long v = 0;
    int p = at;
    for (int i = 0; i < 4; i++) {
      int c = b[p++] & 0xff;
      v = (v << 7) | (c & 0x7f);
      if ((c & 0x80) == 0) break;
    }
    return new long[] {v, p};
  }

  /** A saved conversation: its model and system text (null when it had none) and its turns as {role, text}. */
  static final class Conversation {
    String model;
    String system;
    final List<String[]> turns = new ArrayList<String[]>();
  }

  /**
   * The conversation saved in `f` (transcript writes it). A file without the SogniChat heading is
   * an earlier reply on its own, and continues as that reply. Null, after saying why, if unreadable.
   */
  static Conversation conversation(File f) {
    String text = readText(f, "--continue");
    if (text == null) return null;
    Conversation c = new Conversation();
    if (!text.startsWith(HEAD)) {
      if (text.trim().length() == 0) {
        System.out.println("Failed: " + f.getName() + " is empty");
        return null;
      }
      c.turns.add(new String[] {"assistant", text.trim()});
      return c;
    }
    String role = null;
    StringBuilder body = new StringBuilder();
    for (String line : text.split("\n", -1)) {
      if (line.equals(YOU) || line.equals(SOGNI)) {
        if (role != null) c.turns.add(new String[] {role, body.toString().trim()});
        role = line.equals(YOU) ? "user" : "assistant";
        body.setLength(0);
      } else if (role != null) {
        body.append(line).append('\n');
      } else if (line.startsWith("Model: ")) {
        c.model = line.substring(7).trim();
      } else if (line.startsWith("System: ")) {
        c.system = line.substring(8).trim();
      }
    }
    if (role != null) c.turns.add(new String[] {role, body.toString().trim()});
    if (c.turns.isEmpty()) {
      System.out.println("Failed: " + f.getName() + " holds no questions or replies to continue");
      return null;
    }
    return c;
  }

  /** The conversation as it is saved: a heading, the model and system text, then each turn under its marker. */
  static String transcript(String model, String system, List<String[]> turns) {
    StringBuilder sb = new StringBuilder(HEAD).append('\n');
    sb.append("Model: ").append(model).append('\n');
    if (system != null && system.trim().length() > 0) sb.append("System: ").append(system.trim().replaceAll("\\s*\n\\s*", " ")).append('\n');
    for (String[] t : turns) sb.append('\n').append("user".equals(t[0]) ? YOU : SOGNI).append('\n').append(t[1].trim()).append('\n');
    return sb.toString();
  }

  /** Questions answered: the replies in `turns`. */
  static int exchanges(List<String[]> turns) {
    int n = 0;
    for (String[] t : turns) if ("assistant".equals(t[0])) n++;
    return n;
  }

  /** sogni-chat-suggest-a-fill-3.txt for the third exchange of sogni-chat-suggest-a-fill(1).txt or -2.txt. */
  static String continuedName(String from, int exchanges) {
    String stem = from.replaceAll("\\.[^.]*$", "");
    stem = stem.replaceAll("\\s*\\(\\d+\\)$", "").replaceAll("-\\d+$", "");
    if (stem.length() == 0) stem = "sogni-chat";
    return stem + "-" + exchanges + ".txt";
  }

  /** sogni-chat-suggest-a-fill-for.txt: the first words of the prompt (or the file's name). */
  static String replyName(String prompt, String file) {
    String from = prompt != null && prompt.trim().length() > 0 ? prompt : (file == null ? "" : new File(file.trim()).getName().replaceAll("\\.[^.]*$", ""));
    StringBuilder sb = new StringBuilder();
    int words = 0;
    for (String w : from.toLowerCase().split("[^a-z0-9]+")) {
      if (w.length() == 0) continue;
      if (sb.length() > 0) sb.append('-');
      sb.append(w);
      if (++words == 4 || sb.length() > 30) break;
    }
    return "sogni-chat" + (sb.length() > 0 ? "-" + sb : "") + ".txt";
  }

  /**
   * --saveprompt: a Pulsekit prompt sheet (PKPROMPT1, as PromptRun.encode writes it) named as the
   * saved chat: category Writing, the chat model, the first two --file names as its reference files,
   * type AI, the question (and the system text under it), and the saved chat as its result file.
   * Pulsekit stores it in the prompt library after the run, the chat .txt as a result file. Never
   * over an existing file; null if it could not be written.
   */
  static File savePrompt(String name, String model, String prompt, String system, List<String> files, String result, String note) {
    String ref1 = files.size() > 0 ? files.get(0) : "";
    String ref2 = files.size() > 1 ? files.get(1) : "";
    StringBuilder sb = new StringBuilder();
    sb.append("PKPROMPT1\n").append(name).append("\n\n\n").append(ref1).append('\n').append(ref2).append('\n');
    sb.append("Category: Writing\n");
    sb.append("Model: Sogni chat ").append(model == null ? SogniApi.CHAT_MODEL : model).append('\n');
    sb.append("Reference file 1: ").append(ref1).append('\n');
    sb.append("Reference file 2: ").append(ref2).append('\n');
    sb.append("Type: ai\n");
    sb.append("---\n");
    sb.append(prompt == null ? "" : prompt.trim());
    if (system != null && system.trim().length() > 0) sb.append("\n\nSystem: ").append(system.trim());
    sb.append("\n\nResult file: ").append(result).append('\n');
    if (note != null && note.length() > 0) sb.append("Result text:\nNote: ").append(note).append('\n');
    return saveData(name + ".prompt", sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** Writes a result beside the program's other files, never over an existing file. Null if it could not. */
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

  /** Writes the reply beside the program's other files, never over an existing file. Null if it could not. */
  static File save(String name, String reply) {
    File file = inWork(name);
    String path = file.getPath();
    int dot = path.lastIndexOf('.');
    String stem = dot > path.lastIndexOf(File.separatorChar) ? path.substring(0, dot) : path;
    String ext = dot > path.lastIndexOf(File.separatorChar) ? path.substring(dot) : "";
    for (int n = 1; file.exists(); n++) file = new File(stem + "(" + n + ")" + ext);
    try {
      FileOutputStream fos = new FileOutputStream(file);
      try {
        fos.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
      } finally {
        fos.close();
      }
      return file;
    } catch (IOException ex) {
      return null;
    }
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

  /**
   * Arguments as typed by hand: "--prompt hello" given as one argument becomes the switch and its
   * value, and a switch given twice in a row (--prompt --prompt hello) counts once.
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
      System.out.println("Failed: " + flag + " needs a number, not \"" + value + "\"");
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
