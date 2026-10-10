package pulsekit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PyJav's Params screen (Android and desktop): the parameters a loaded program takes, read from
 * its "Usage:" line (Java strings split over lines are joined) or its argparse add_argument calls.
 * A parameter named like a .wav, .mp3, .mid or .txt file gets a file picker; anything else a text field.
 * Output files are left to PyJav, which names them.
 */
public final class ProgramParams {
  private ProgramParams() {}

  public static final class Param {
    /** "--sens" for a switch, or the name of a file or value given in order, such as "drums.mid". */
    public String token = "";
    public String label = "";
    /** The program's own value, shown in an empty field. */
    public String hint = "";
    public String suggested = "";
    /** A switch starting with "-". Otherwise a value given in order. */
    public boolean flag;
    /** The switch is followed by a value. False for on/off switches such as --no-hpss. */
    public boolean takesValue = true;
    public boolean optional;
    /** "wav", "mp3", "mid" or "txt" when the value is a file of that kind, else null. */
    public String ext;
    /** Values to pick from (SogniMusic's --genre: Pulsekit's style database), or null. */
    public String[] choices;
    /** What each choice puts in the field, when it differs from its label (--workflow: the id); else null. */
    public String[] choiceValues;

    /** The value a picked choice puts in the field. */
    public String choiceValue(int i) {
      return this.choiceValues != null && i < this.choiceValues.length ? this.choiceValues[i] : this.choices[i];
    }
    /** A file that can also come from the prompt library's reference files (Browse DB, on the phone). */
    public boolean refs;
    /**
     * An audio input that a MIDI file can stand in for (DrumMidi's): a MIDI picked with Browse DB is
     * rendered with the kit's sounds to a WAV, which the program gets.
     */
    public boolean midiAsAudio;
    /** An output file: PyJav names it, so the screen leaves it alone. */
    public boolean output;
    /** SogniChat's saved chat to continue: the row has New chat, which clears it (the next run starts a new chat). */
    public boolean newChat;
    /** An on/off switch that starts ticked in Params until the program's Params are saved (SogniVideo's --saveprompt and --no_filter). */
    public boolean defaultOn;
    /**
     * SogniVideo's --join: a file row behind a "Join with this video" checkbox (its buttons work
     * only when it is ticked); PyJav unticks it after every run (drop).
     */
    public boolean join;
    /**
     * A folder (MediaBrowser's &lt;directory&gt;): the row has Browse, which picks a folder and opens
     * the Media browser on it. On the phone the value is the folder's content:// address.
     */
    public boolean dir;

    public boolean isFile() {
      return ext != null && !output;
    }
  }

  private static final Pattern FILE_EXT = Pattern.compile("(?i)\\.(wav|wave|mp3|midi?|txt|mp4)\\b");

  /** The parameters in `text` (a program's source or readable strings), in the order the program lists them. */
  public static List<Param> parse(String text) {
    List<Param> out = fromUsage(text);
    if (out.isEmpty()) out = fromArgparse(text);
    boolean drumMidi = has(out, "--sens") && has(out, "--no-hpss");
    // SogniChat: --prompt is a question and --system the role and way to answer, not a music description.
    boolean chat = has(out, "--prompt") && has(out, "--system");
    // MidiDrumGen: --style takes Pulsekit's style names.
    boolean drumGen = has(out, "--style") && has(out, "--intensity");
    // SplitWav: one WAV split in two (--output_file1, --output_file2).
    boolean splitWav = has(out, "--output_file1") && has(out, "--output_file2");
    // CutWav: one WAV cut at a time or size (--split_time).
    boolean cutWav = has(out, "--split_time");
    // CompareHits: a WAV and two MIDI files (the source and the song).
    boolean compareHits = has(out, "input.wav") && has(out, "drums.mid") && has(out, "song.mid");
    // SogniVideo: pictures to animate, and MiniMax H3's sizes.
    boolean video = has(out, "--image") && has(out, "--end_image");
    // SogniPedit: Krea 2 Identity Edit of a picture, with Skin Detail and other Krea 2 LoRAs.
    boolean pic = has(out, "--image2") && has(out, "--skin_detail");
    // SogniPadd: a picture from a prompt alone, with Krea 2 LoRAs.
    boolean padd = has(out, "--loras") && has(out, "--seed") && !has(out, "--image2");
    // SogniTextVideo: a clip from a prompt alone, with H3 LoRAs.
    boolean textVideo = has(out, "--loras") && has(out, "--duration") && !has(out, "--image");
    // ImageUpscaler: a picture made larger (bicubic, or Real-ESRGAN on the desktop), in a size and shape.
    boolean upscaler = has(out, "--aspect") && has(out, "--fit");
    // JoinVideo: two MP4s as one.
    boolean joinVideo = has(out, "--b_first") && has(out, "--addtodb");
    for (Param p : out) {
      known(p, drumMidi, chat);
      // DrumMidi's, CompareHits', SplitWav's and CutWav's audio input can also come from the prompt library (Browse DB, sound files only).
      if ((drumMidi || compareHits || splitWav || cutWav) && !p.flag && p.isFile() && isAudio(p)) p.refs = true;
      // DrumMidi's and CompareHits' audio input can be a MIDI from the library too, played with the kit to a WAV.
      if ((drumMidi || compareHits) && !p.flag && p.isFile() && isAudio(p)) p.midiAsAudio = true;
      // CompareHits' drums and song MIDI too (MIDI files only), beside From file set.
      if (compareHits && !p.flag && p.isFile() && "mid".equals(p.ext)) p.refs = true;
      if (video) knownVideo(p);
      if (joinVideo) knownJoinVideo(p);
      if (upscaler) knownUpscaler(p);
      if (pic) knownPedit(p);
      if (padd) knownPadd(p);
      if (textVideo) knownTextVideo(p);
      // A folder given in order (MediaBrowser's <directory>): Browse picks it.
      if (!p.flag && p.ext == null && p.token.matches("(?i)dir(ectory)?|folder")) {
        p.dir = true;
        p.label = "Directory";
        p.hint = "a folder of pictures, videos and sounds";
      }
      // SogniVideo, SogniMusic and SogniChat: Sogni's Safe Content Filter off for the run.
      if (p.flag && !p.takesValue && p.token.equals("--no_filter")) p.label = "Content filter off (Sogni's Safe Content Filter)";
      if (drumGen && p.flag && p.takesValue && p.token.equals("--style")) {
        p.hint = "the app's style, or one from the list";
        p.choices = StyleDb.names();
      }
    }
    return out;
  }

  static List<Param> fromUsage(String text) {
    List<Param> out = new ArrayList<Param>();
    if (text == null) return out;
    int at = 0;
    while ((at = text.indexOf("Usage:", at)) >= 0) {
      String line = usageLine(text, at + 6);
      at += 6;
      List<Param> found = usageParams(line);
      if (!found.isEmpty()) return found;
    }
    return out;
  }

  /** The usage text after "Usage:", with Java "..." + "..." joins removed, up to the end of the string or line. */
  static String usageLine(String text, int from) {
    String tail = text.substring(from, Math.min(text.length(), from + 800));
    tail = tail.replaceAll("\"\\s*\\+\\s*\"", "");
    int end = tail.length();
    int q = tail.indexOf('"');
    if (q >= 0) end = Math.min(end, q);
    int nl = tail.indexOf('\n');
    if (nl >= 0) end = Math.min(end, nl);
    return tail.substring(0, end).trim();
  }

  static List<Param> usageParams(String line) {
    List<Param> out = new ArrayList<Param>();
    List<String> words = new ArrayList<String>();
    // One level of brackets inside brackets is part of the same parameter ([--time mm:ss[.ms]]).
    Matcher m = Pattern.compile("\\[(?:[^\\[\\]]|\\[[^\\]]*\\])*\\]|<[^>]*>|\\S+").matcher(line);
    while (m.find()) words.add(m.group());
    int i = 0;
    // Skip "java", "python", "python3" and the program's name.
    while (i < words.size()) {
      String w = words.get(i);
      if (w.startsWith("[") || w.startsWith("<") || w.startsWith("-")) break;
      i++;
    }
    for (; i < words.size(); i++) {
      String w = words.get(i);
      // A shortened Usage line (PyJav's hint keeps 220 characters) can end inside a bracket: stop there.
      if (w.startsWith("[") && !w.endsWith("]") || w.startsWith("<") && !w.endsWith(">")) break;
      boolean optional = w.startsWith("[");
      String inner = optional ? w.substring(1, w.length() - 1).trim() : w;
      if (inner.startsWith("-")) {
        String[] parts = inner.split("\\s+");
        Param p = new Param();
        p.flag = true;
        p.optional = optional;
        p.token = parts[0].replaceAll("[,|].*$", "");
        String value = parts.length > 1 ? parts[1] : null;
        if (!optional && value == null && i + 1 < words.size() && isValueWord(words.get(i + 1))) value = words.get(++i);
        p.takesValue = value != null;
        if (value != null) p.ext = ext(value);
        // The value's word as a hint ("mm:ss.ms", "MB"), unless it is only N or a <name>.
        if (value != null && !value.equals("N") && !value.startsWith("<") && p.ext == null) p.hint = value;
        p.output = p.token.toLowerCase(Locale.ROOT).startsWith("--out") || p.token.equals("-o");
        if (p.token.length() > 1 && !has(out, p.token)) out.add(p);
      } else {
        String name = inner.replaceAll("^<|>$", "").trim();
        if (name.length() == 0 || name.equals("...")) continue;
        Param p = new Param();
        p.token = name;
        p.optional = optional;
        p.ext = ext(name);
        p.output = name.toLowerCase(Locale.ROOT).startsWith("out");
        out.add(p);
      }
    }
    return out;
  }

  private static boolean isValueWord(String w) {
    if (w.startsWith("<")) return true;
    return w.matches("[A-Z][A-Z0-9_]*");
  }

  static List<Param> fromArgparse(String text) {
    List<Param> out = new ArrayList<Param>();
    if (text == null) return out;
    Matcher m = Pattern.compile("add_argument\\(([^)]*)\\)").matcher(text);
    while (m.find()) {
      String args = m.group(1);
      Matcher names = Pattern.compile("^\\s*([\"'][^\"']+[\"'](?:\\s*,\\s*[\"'][^\"']+[\"'])*)").matcher(args);
      if (!names.find()) continue;
      String token = null;
      for (String n : names.group(1).split(",")) {
        String v = n.trim().replaceAll("^[\"']|[\"']$", "");
        if (token == null || v.startsWith("--")) token = v;
      }
      if (token == null) continue;
      Param p = new Param();
      p.flag = token.startsWith("-");
      p.token = token;
      p.optional = p.flag || args.contains("nargs=\"?\"") || args.contains("nargs='?'");
      p.takesValue = !args.matches("(?s).*action\\s*=\\s*[\"']store_(true|false)[\"'].*");
      Matcher def = Pattern.compile("default\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^,\\s)]+)").matcher(args);
      if (def.find()) p.hint = def.group(1).replaceAll("^[\"']|[\"']$", "");
      Matcher help = Pattern.compile("help\\s*=\\s*(\"[^\"]*\"|'[^']*')").matcher(args);
      if (help.find()) p.label = help.group(1).substring(1, help.group(1).length() - 1);
      String nameForExt = p.flag ? p.hint + " " + token : token;
      p.ext = ext(nameForExt);
      p.output = token.toLowerCase(Locale.ROOT).replaceAll("^-+", "").startsWith("out");
      if (!has(out, p.token)) out.add(p);
    }
    return out;
  }

  /** Labels, hints and suggested values for DrumMidi's switches; a readable label for anything else. */
  /** The chat models Sogni offered (SogniChat --models, October 2026), the default first. */
  static final String[] CHAT_MODELS = {
    "qwen3.6-35b-a3b-gguf-iq4xs", "deepseek-v4-flash-vision-exp-dspark-1m", "qwen3.5-35b-a3b-abliterated-gguf-q4km"
  };
  static final String[] CHAT_MODEL_LABELS = {
    "qwen3.6-35b-a3b-gguf-iq4xs (default)", "deepseek-v4-flash-vision-exp-dspark-1m", "qwen3.5-35b-a3b-abliterated-gguf-q4km"
  };

  private static void known(Param p, boolean drumMidi, boolean chat) {
    int i = drumMidi && p.flag ? DrumMidiArgs.index(p.token) : -1;
    if (i >= 0) {
      p.label = DrumMidiArgs.LABELS[i];
      p.hint = DrumMidiArgs.HINTS[i];
      p.suggested = DrumMidiArgs.SUGGESTED[i];
    }
    if (p.label == null || p.label.length() == 0) {
      String s = p.token.replaceAll("^-+", "").replace('_', ' ').replace('-', ' ');
      if (!p.flag && p.ext != null) p.label = p.token;
      else p.label = s.length() == 0 ? p.token : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
    if (p.output && p.optional && p.hint.length() == 0) p.hint = "optional; the program picks a name";
    if (p.flag && p.takesValue && p.token.equals("--prompt")) p.hint = chat ? "your question" : "genre, mood, instruments";
    if (p.flag && p.takesValue && p.token.equals("--model") && chat) {
      p.label = "Chat model";
      p.hint = "empty for the default, or one from the list";
      p.choices = CHAT_MODEL_LABELS;
      p.choiceValues = CHAT_MODELS;
    }
    if (p.flag && p.takesValue && p.token.equals("--file") && chat) {
      // SogniChat reads text, MIDI, pictures, audio and video: any file can be picked, also from the prompt library (Browse DB).
      p.ext = "any";
      p.refs = true;
      p.label = "File (text, MIDI, picture, audio or video)";
    }
    if (p.flag && chat && p.token.equals("--tools")) p.label = "Offer Sogni tools (show proposed calls)";
    if (p.flag && chat && p.token.equals("--run_tools")) p.label = "Run proposed tool calls (paid)";
    if (p.flag && chat && p.token.equals("--confirm_cost")) p.label = "Confirm the charge";
    if (p.flag && chat && p.token.equals("--unlimited")) p.label = "Unlimited Plan (Sogni runs tools in the chat; fair use limits apply)";
    // SogniChat runs with the filter off; this switch turns it on.
    if (p.flag && chat && p.token.equals("--filter_on")) p.label = "Content filter on (Sogni's Safe Content Filter; off by default)";
    if (p.flag && p.takesValue && chat && p.token.equals("--max_cost")) {
      p.label = "Max cost (capacity units)";
      p.hint = "e.g. 10; empty for no limit";
    }
    if (!p.flag && p.output && chat) {
      // SogniChat's output is a base name for everything it saves, not one file.
      p.label = "Output name";
      p.hint = "e.g. kit-ideas: kit-ideas.txt, results kit-ideas-1.png...";
    }
    if (p.flag && p.takesValue && p.token.equals("--run") && chat) {
      p.label = "Follow chat run (id)";
      p.hint = "a run id from an earlier log, e.g. after a time-out";
    }
    if (p.flag && p.takesValue && p.token.equals("--continue") && chat) {
      p.label = "Continue from saved chat";
      p.hint = "a sogni-chat .txt from an earlier run";
      p.newChat = true;
    }
    if (p.flag && p.takesValue && p.token.equals("--system") && chat) {
      p.label = "System role/answer";
      p.hint = "who answers and how, e.g. You are a drum teacher. Answer briefly.";
    }
    if (p.flag && !p.takesValue && p.token.equals("--saveprompt")) p.label = "Save the prompt as a prompt sheet";
    // SogniChat: the sheet goes into the prompt library with the saved chat (.txt) as its result file.
    if (p.flag && !p.takesValue && p.token.equals("--saveprompt") && chat) p.label = "Save the prompt, and the answer .txt as its result file in DB";
    if (p.flag && p.takesValue && p.token.equals("--instruments")) p.hint = "e.g. bass, rhodes piano";
    if (p.flag && p.takesValue && p.token.equals("--keyscale") && p.hint.equals("key")) p.hint = "e.g. C major, A minor (or C, Am)";
    if (p.flag && p.takesValue && p.token.equals("--timesig") && p.hint.indexOf('|') >= 0) p.hint = "2, 3, 4 or 6 (4 = 4/4, 6 = 6/8)";
    if (p.flag && p.takesValue && p.token.equals("--genre")) {
      p.hint = "the app's style, or one from the list";
      p.choices = StyleDb.names();
    }
    if (p.flag && p.takesValue && p.token.equals("--workflow")) {
      // Runs PyJav saw start, newest first: picking one fills in its id.
      String[][] runs = SogniHistory.choices();
      p.hint = runs == null ? "a Workflow: id from a run's log" : "a past run, from the list";
      if (runs != null) {
        p.choices = runs[0];
        p.choiceValues = runs[1];
      }
    }
    if (!p.flag && p.hint.length() == 0 && p.optional) p.hint = "optional";
    if (p.flag && !p.takesValue && p.hint.length() == 0) p.hint = "1 to turn on";
    if (p.flag && p.takesValue && p.hint.length() == 0 && p.token.matches("--?log(file)?")) p.hint = "a file name, such as results.txt";
  }

  /** ImageUpscaler's labels: the picture (also from the prompt library), the method, the size and the shape. */
  private static void knownUpscaler(Param p) {
    if (!p.flag && p.output) {
      p.label = "Output name";
      p.hint = "optional; <picture>-upscaled.png (.png, .jpg; phone also .webp)";
      return;
    }
    if (!p.flag) {
      p.ext = "any";
      p.refs = true;
      p.label = "Picture to upscale";
      p.hint = "a PNG or JPEG";
      return;
    }
    if (p.token.equals("--method")) {
      p.label = "Upscale method";
      p.hint = "1 (default), 2 or 3";
      p.choices = new String[] {"1 = standard bicubic resize, 2x (built in; phone and desktop)",
        "2 = Real-ESRGAN AI upscale, 4x (desktop only; needs realesrgan-ncnn-vulkan)",
        "3 = Sogni AI upscale (RTX VSR, online; phone and desktop; paid)"};
      p.choiceValues = new String[] {"1", "2", "3"};
    } else if (p.token.equals("--scale")) {
      p.label = "Scale (times larger)";
      p.hint = "2 for method 1, 4 for method 2; 0.1 to 8";
    } else if (p.token.equals("--width")) {
      p.label = "Preferred width (pixels)";
      p.hint = "empty: from the scale; width alone keeps the shape";
    } else if (p.token.equals("--height")) {
      p.label = "Preferred height (pixels)";
      p.hint = "empty: from the scale; both width and height give that exact size";
    } else if (p.token.equals("--aspect")) {
      p.label = "Aspect ratio (width:height)";
      p.hint = "empty: the picture's own; e.g. 16:9";
      p.choices = new String[] {"1:1", "4:3", "3:4", "3:2", "2:3", "16:9", "9:16", "4:5", "5:4", "4:7", "21:9"};
    } else if (p.token.equals("--fit")) {
      p.label = "When the shape changes";
      p.hint = "crop (default), pad or stretch";
      p.choices = new String[] {"crop (cut the edges)", "pad (add bars)", "stretch"};
      p.choiceValues = new String[] {"crop", "pad", "stretch"};
    } else if (p.token.equals("--quality")) {
      p.label = "JPEG / WebP quality (1-100)";
      p.hint = "92";
    } else if (p.token.equals("--key_file")) {
      p.label = "Sogni API key file (method 3)";
      p.hint = "empty: the one in Drum Midi Settings";
    } else if (p.token.equals("--max_cost")) {
      p.label = "Max cost (capacity units, method 3)";
      p.hint = "e.g. 5; empty for no limit";
    } else if (p.token.equals("--confirm_cost")) {
      p.label = "Confirm the charge (method 3)";
    } else if (p.token.equals("--unlimited")) {
      p.label = "Unlimited Plan (method 3; fair use limits apply)";    } else if (p.token.equals("--output_dir")) {
      p.label = "Output directory";
      p.hint = "download (default) or original: also beside the input picture";
      p.choices = new String[] {"download (Download / program files folder)", "original (also in the input picture's folder)"};
      p.choiceValues = new String[] {"download", "original"};
    } else if (p.token.equals("--addtodb")) {
      p.label = "Add the picture to DB as a Reference file";
    }
  }

  /** JoinVideo's labels: the two videos (also from the prompt library), the order, and the library. */
  private static void knownJoinVideo(Param p) {
    if (!p.flag && p.output) {
      p.label = "Output name";
      p.hint = "optional; <video a>-merged.mp4";
    } else if (!p.flag && p.isFile()) {
      p.refs = true;
      p.label = p.token.contains("_b") ? "Video b" : "Video a";
      p.hint = "an MP4; the two must have the same picture format";
    }
    if (p.flag && p.takesValue && p.token.matches("--video_[c-f]")) {
      // More videos after a and b, each a file row with Browse DB too.
      p.ext = "mp4";
      p.refs = true;
      p.label = "Video " + p.token.charAt(8) + " (optional)";
      p.hint = "after the videos before it";
    }
    if (p.flag && p.token.equals("--b_first")) p.label = "Video b first (then video a; c to f follow)";
    if (p.flag && p.token.equals("--addtodb")) p.label = "Add the joined video to the prompt library (DB)";
  }

  /** SogniPedit's labels: the pictures are files (also from the prompt library), the LoRAs a list; Unlimited Plan and Save the prompt start ticked. */
  private static void knownPedit(Param p) {
    if (!p.flag && p.output) {
      p.label = "Output name";
      p.hint = "optional; sogni-pedit-<first words>.png";
    }
    if (!p.flag || !p.takesValue) {
      if (p.token.equals("--saveprompt")) p.label = "Save the prompt as a prompt sheet";
      if (p.token.equals("--unlimited")) p.label = "Unlimited Plan (the subscription pays; fair use limits apply)";
      if (p.token.equals("--confirm_cost")) p.label = "Confirm the charge";
      if (p.token.equals("--saveprompt") || p.token.equals("--unlimited")) p.defaultOn = true;
      return;
    }
    if (p.token.equals("--prompt")) p.hint = "what changes: clothing, hair, pose, background, light or style (1-4 sentences)";
    if (p.token.equals("--image")) {
      p.ext = "any";
      p.refs = true;
      p.label = "Picture to edit";
      p.hint = "the person or character keeps their likeness";
    }
    if (p.token.equals("--image2")) {
      p.ext = "any";
      p.refs = true;
      p.label = "Second reference picture (optional)";
      p.hint = "an outfit, a pose, a style or another detail to use";
    }
    if (p.token.equals("--skin_detail")) {
      p.label = "Skin detail (Krea 2 Skin Detail LoRA)";
      p.hint = "1.1 (default); -0.5 smoother to 3 more detail; 0 off";
    }
    if (p.token.equals("--loras")) {
      p.label = "More Krea 2 LoRAs (id:strength or name strength, comma separated)";
      p.hint = "e.g. krea2-warm-light:0.6,krea2-film-grain:1";
    }
    if (p.token.equals("--max_cost")) {
      p.label = "Max cost (capacity units)";
      p.hint = "e.g. 50; empty for no limit";
    }
  }

  /** SogniPadd's labels: the LoRAs a list (its default set when empty), the size; Unlimited Plan and Save the prompt start ticked. */
  private static void knownPadd(Param p) {
    if (!p.flag && p.output) {
      p.label = "Output name";
      p.hint = "optional; sogni-padd-<first words>.png";
    }
    if (!p.flag || !p.takesValue) {
      if (p.token.equals("--saveprompt")) p.label = "Save the prompt as a prompt sheet";
      if (p.token.equals("--unlimited")) p.label = "Unlimited Plan (the subscription pays; fair use limits apply)";
      if (p.token.equals("--confirm_cost")) p.label = "Confirm the charge";
      if (p.token.equals("--saveprompt") || p.token.equals("--unlimited")) p.defaultOn = true;
      return;
    }
    if (p.token.equals("--prompt")) p.hint = "the picture to make";
    if (p.token.equals("--loras")) {
      p.label = "Krea 2 LoRAs (id:strength or name strength, comma separated)";
      p.hint = "empty: Mystic X 1, Realism Engine 0.8, Chest Size 0.5, Weight -1, Filter Bypass 2vector 1; none for no LoRAs";
    }
    if (p.token.equals("--aspect")) p.hint = "e.g. 9:16 or 4:5; empty for 1024 square";
    if (p.token.equals("--width") || p.token.equals("--height")) p.hint = "256 to 2560; give both, or use the shape";
    if (p.token.equals("--seed")) p.hint = "empty for a new picture each run";
    if (p.token.equals("--max_cost")) {
      p.label = "Max cost (capacity units)";
      p.hint = "e.g. 50; empty for no limit";
    }
  }

  /** SogniTextVideo's labels: the H3 sizes, the LoRA list (its default set when empty); Unlimited Plan and Save the prompt start ticked. */
  private static void knownTextVideo(Param p) {
    if (!p.flag && p.output) {
      p.label = "Output name";
      p.hint = "optional; sogni-textvideo-<first words>.mp4";
    }
    if (!p.flag || !p.takesValue) {
      if (p.token.equals("--no_audio")) p.label = "No sound (silent clip)";
      if (p.token.equals("--exact_prompt")) p.label = "Send the prompt as written";
      if (p.token.equals("--saveprompt")) p.label = "Save the prompt as a prompt sheet";
      if (p.token.equals("--unlimited")) p.label = "Unlimited Plan (the subscription pays; fair use limits apply)";
      if (p.token.equals("--confirm_cost")) p.label = "Confirm the charge";
      if (p.token.equals("--saveprompt") || p.token.equals("--unlimited")) p.defaultOn = true;
      return;
    }
    if (p.token.equals("--prompt")) p.hint = "what happens: the scene, the motion, the camera, the sound";
    if (p.token.equals("--duration")) p.hint = "5 to 15 seconds (default 5)";
    if (p.token.equals("--resolution")) {
      p.hint = "768 (default), or a two-stage size";
      p.choices = new String[] {"768 (FastH3, about 4 Spark/s)", "720 two-stage (about 4 Spark/s)", "1080 two-stage (about 10 Spark/s)", "1440 two-stage, 2K (about 16 Spark/s)"};
      p.choiceValues = new String[] {"768", "720", "1080", "1440"};
    }
    if (p.token.equals("--aspect")) p.hint = "e.g. 16:9 or 9:16; empty for the model's own";
    if (p.token.equals("--loras")) {
      p.label = "H3 LoRAs (id:strength or name strength, comma separated)";
      p.hint = "empty: Mystic X v4 0.5, VBVR Video Reasoning 1; none for no LoRAs";
    }
    if (p.token.equals("--max_cost")) {
      p.label = "Max cost (capacity units)";
      p.hint = "e.g. 100; empty for no limit";
    }
  }

  /** SogniVideo's labels: pictures are picked as files, the resolution from MiniMax H3's sizes. */
  private static void knownVideo(Param p) {
    if (!p.flag && p.output) {
      p.label = "Output name";
      p.hint = "optional; sogni-video-<first words>.mp4";
    }
    if (!p.flag || !p.takesValue) {
      if (p.token.equals("--no_audio")) p.label = "No sound (silent clip)";
      if (p.token.equals("--exact_prompt")) p.label = "Send the prompt as written";
      if (p.token.equals("--saveprompt")) p.label = "Save the prompt as a prompt sheet";
      // Ticked in Params from the start: the sheet keeps the prompt, and the filter can pause a run for a safety review.
      if (p.token.equals("--saveprompt") || p.token.equals("--no_filter")) p.defaultOn = true;
      if (p.token.equals("--unlimited")) p.label = "Unlimited Plan (the subscription pays; fair use limits apply)";
      if (p.token.equals("--confirm_cost")) p.label = "Confirm the charge";
      if (p.token.equals("--join_first")) p.label = "Joined video first (then the new clip)";
      return;
    }
    if (p.token.equals("--prompt")) p.hint = "what happens: the motion, the camera, the sound";
    if (p.token.equals("--image")) {
      p.ext = "any";
      p.refs = true;
      p.label = "Picture to animate (first frame)";
      p.hint = "optional; without one the clip comes from the prompt alone";
    }
    if (p.token.equals("--end_image")) {
      p.ext = "any";
      p.refs = true;
      p.label = "Last frame picture";
      p.hint = "optional; the clip moves from the first picture to this one";
    }
    if (p.token.equals("--join")) {
      p.ext = "any";
      p.refs = true;
      p.join = true;
      p.label = "Join with this video";
      p.hint = "the clip first, then this MP4, saved as <clip name>-merged.mp4";
    }
    if (p.token.equals("--duration")) p.hint = "5 to 15 seconds (default 5)";
    if (p.token.equals("--resolution")) {
      p.hint = "768 (default), or a two-stage size";
      p.choices = new String[] {"768 (FastH3, about 4 Spark/s)", "720 two-stage (about 4 Spark/s)", "1080 two-stage (about 10 Spark/s)", "1440 two-stage, 2K (about 16 Spark/s)"};
      p.choiceValues = new String[] {"768", "720", "1080", "1440"};
    }
    if (p.token.equals("--aspect")) p.hint = "e.g. 16:9 or 9:16; empty keeps the picture's shape";
    if (p.token.equals("--loras")) {
      p.label = "H3 LoRAs (id:strength or name strength, comma separated)";
      p.hint = "empty: h3-vbvr-video-reasoning:1 with the filter off; none for no LoRAs; e.g. h3-better-motion:0.6";
    }
    if (p.token.equals("--max_cost")) {
      p.label = "Max cost (capacity units)";
      p.hint = "e.g. 100; empty for no limit";
    }
  }

  static String ext(String name) {
    if (name == null) return null;
    Matcher m = FILE_EXT.matcher(name);
    if (!m.find()) return null;
    String e = m.group(1).toLowerCase(Locale.ROOT);
    if (e.startsWith("mid")) return "mid";
    return e.equals("wave") ? "wav" : e;
  }

  /** A value typed with its switch in front ("--workflow wf_1" in the --workflow field) without it. */
  static String withoutOwnSwitch(Param p, String v) {
    String t = v.trim();
    while (t.equals(p.token) || t.startsWith(p.token + " ") || t.startsWith(p.token + "=")) t = t.substring(p.token.length()).replaceFirst("^[\\s=]+", "");
    return t;
  }

  /** "mid", "txt", "any" (SogniChat's --file) or "audio" (wav and mp3 stand in for each other). */
  static String kind(String ext) {
    return "mid".equals(ext) || "txt".equals(ext) || "any".equals(ext) ? ext : "audio";
  }

  /** A switch that takes a sentence or more (a prompt, a system text, lyrics), shown as a field of several lines. */
  public static boolean longText(Param p) {
    if (p == null || !p.flag || !p.takesValue || p.choices != null) return false;
    String t = p.token;
    // A LoRA list (SogniVideo, SogniPedit, SogniPadd) too: wrapped so the whole list shows.
    return t.equals("--prompt") || t.equals("--system") || t.equals("--lyrics") || t.equals("--instruments") || t.equals("--loras");
  }

  /** Whether the field is a program's prompt (--prompt): Params offers Select file and Browse DB for a text file to fill it. */
  public static boolean promptFromFile(Param p) {
    return p != null && p.flag && p.takesValue && !p.isFile() && p.token.equals("--prompt");
  }

  /**
   * A picked text file's contents for a prompt field (an Answer Prompt 1.txt made by Extract
   * prompt, say): UTF-8 without a byte order mark, trimmed; a Pulsekit prompt sheet gives its prompt.
   */
  public static String promptText(byte[] bytes) {
    if (bytes == null) return "";
    String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    if (text.startsWith("\uFEFF")) text = text.substring(1);
    PromptRun.Sheet sheet = PromptRun.parse(text);
    if (sheet != null) text = sheet.body;
    return text.replace("\r\n", "\n").replace('\r', '\n').trim();
  }

  /** A several-line field's text as one argument: lines joined with spaces. */
  public static String oneLine(String text) {
    return text == null ? "" : text.trim().replaceAll("\\s*\\n\\s*", " ");
  }

  /** A .wav or .mp3 parameter. */
  public static boolean isAudio(Param p) {
    return p.ext != null && "audio".equals(kind(p.ext));
  }

  private static boolean has(List<Param> ps, String token) {
    for (Param p : ps) if (p.token.equals(token)) return true;
    return false;
  }

  /** DrumMidi's note for its suggested values, or a general one. */
  public static String note(List<Param> ps) {
    if (hasSuggested(ps)) return DrumMidiArgs.NOTE;
    return "Read from the program itself.";
  }

  /** File name for a file set's source MIDI copied in for a program, such as "Passing_Ships_source.mid". */
  public static String fileSetMidiFile(String label) {
    String n = label == null ? "" : label.trim().replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
    if (n.length() == 0) n = "fileset";
    if (n.length() > 40) n = n.substring(0, 40);
    return n + "_source.mid";
  }

  public static boolean hasSuggested(List<Param> ps) {
    for (Param p : ps) if (p.suggested != null && p.suggested.length() > 0) return true;
    return false;
  }

  /** Switches only, from field values (one per parameter). Empty fields are left out. */
  /** The arguments without `token` and its value (SogniVideo's --join after a run). */
  public static String drop(String extra, String token) {
    List<String> words = JavaRun.split(extra == null ? "" : extra);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      String w = words.get(i);
      if (w.equals(token)) {
        if (i + 1 < words.size() && !words.get(i + 1).startsWith("--")) i++;
        continue;
      }
      append(sb, quote(w));
    }
    return sb.toString();
  }

  public static String build(List<Param> ps, String[] values) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      Param p = ps.get(i);
      String v = values[i] == null ? "" : values[i].trim();
      if (!p.flag || v.length() == 0) continue;
      if (!p.takesValue) {
        if (DrumMidiArgs.on(v)) append(sb, p.token);
        continue;
      }
      v = withoutOwnSwitch(p, v);
      if (v.length() == 0) continue;
      append(sb, p.token);
      append(sb, quote(v));
    }
    return sb.toString();
  }

  /**
   * The arguments given in order (files and values), from field values; empty optional ones end
   * the list. An output keeps what the arguments had, or its &lt;name&gt; so PyJav fills it in.
   */
  public static String ordered(List<Param> ps, String[] values, String extra) {
    List<String> before = orderedValues(ps, extra);
    StringBuilder sb = new StringBuilder();
    int k = 0;
    for (int i = 0; i < ps.size() && i < values.length; i++) {
      Param p = ps.get(i);
      if (p.flag) continue;
      String v = values[i] == null ? "" : values[i].trim();
      // An output PyJav names keeps what the arguments had, or its <name>. An optional one the
      // program names itself (CutWav's [output.wav]) is left out unless one was given.
      if (p.output && !p.optional) v = k < before.size() ? before.get(k) : "<" + p.token + ">";
      k++;
      if (v.length() == 0) {
        if (p.optional) break;
        v = "<" + p.token + ">";
      }
      append(sb, quote(v));
    }
    return sb.toString();
  }

  /** The new argument line: values in order, then switches. */
  public static String line(List<Param> ps, String[] values, String extra) {
    String a = ordered(ps, values, extra);
    String b = build(ps, values);
    if (a.length() == 0) return b;
    if (b.length() == 0) return a;
    return a + " " + b;
  }

  /** Field values from saved switches and the current arguments (saved switches win). */
  public static String[] values(List<Param> ps, String saved, String extra) {
    Map<String, String> fromSaved = read(ps, saved);
    Map<String, String> fromArgs = read(ps, extra);
    List<String> inOrder = orderedValues(ps, extra);
    String[] out = new String[ps.size()];
    int k = 0;
    for (int i = 0; i < ps.size(); i++) {
      Param p = ps.get(i);
      if (p.flag) {
        String v = saved != null && fromSaved.containsKey(p.token) ? fromSaved.get(p.token) : fromArgs.get(p.token);
        // A default-on switch starts ticked until the program's Params are saved (then the saved choice wins).
        if (v == null && saved == null && p.defaultOn && !p.takesValue) v = "1";
        out[i] = v == null ? "" : v;
      } else {
        String v = k < inOrder.size() ? inOrder.get(k) : "";
        k++;
        // A file of the wrong kind (another program's output.mid in a .wav slot) is not this one's.
        String has = ext(v);
        if (p.ext != null && !"any".equals(p.ext) && has != null && !kind(has).equals(kind(p.ext))) v = "";
        out[i] = v.startsWith("<") ? "" : v;
      }
    }
    return out;
  }

  /** Switch → value in the arguments ("1" for an on/off switch). */
  public static Map<String, String> read(List<Param> ps, String extra) {
    Map<String, String> out = new LinkedHashMap<String, String>();
    List<String> words = JavaRun.split(extra);
    for (int i = 0; i < words.size(); i++) {
      Param p = find(ps, words.get(i));
      if (p == null) continue;
      if (!p.takesValue) out.put(p.token, "1");
      else if (i + 1 < words.size() && !words.get(i + 1).startsWith("-")) out.put(p.token, words.get(++i));
    }
    return out;
  }

  /** The arguments that are not switches or their values, in order. */
  static List<String> orderedValues(List<Param> ps, String extra) {
    List<String> out = new ArrayList<String>();
    List<String> words = JavaRun.split(extra);
    for (int i = 0; i < words.size(); i++) {
      String w = words.get(i);
      if (w.startsWith("-")) {
        Param p = find(ps, w);
        if ((p == null || p.takesValue) && i + 1 < words.size() && !words.get(i + 1).startsWith("-")) i++;
        continue;
      }
      out.add(w);
    }
    return out;
  }

  /** The arguments without this program's switches. */
  public static String strip(List<Param> ps, String extra) {
    List<String> words = JavaRun.split(extra);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      Param p = find(ps, words.get(i));
      if (p != null && p.flag) {
        if (p.takesValue && i + 1 < words.size() && !words.get(i + 1).startsWith("-")) i++;
        continue;
      }
      append(sb, quote(words.get(i)));
    }
    return sb.toString();
  }

  /** As DrumMidiArgs.merge, for any program: saved switches replace the program's switches in the arguments. */
  public static String merge(List<Param> ps, String extra, String saved) {
    if (ps == null || ps.isEmpty()) return DrumMidiArgs.merge(extra, saved);
    if (saved == null) return extra == null ? "" : extra.trim();
    String kept = strip(ps, extra);
    if (saved.trim().length() == 0) return kept;
    if (kept.length() == 0) return saved.trim();
    return kept + " " + saved.trim();
  }

  private static Param find(List<Param> ps, String token) {
    for (Param p : ps) if (p.flag && p.token.equals(token)) return p;
    return null;
  }

  private static String quote(String v) {
    return v.indexOf(' ') >= 0 ? "\"" + v + "\"" : v;
  }

  private static void append(StringBuilder sb, String s) {
    if (s == null || s.length() == 0) return;
    if (sb.length() > 0) sb.append(' ');
    sb.append(s);
  }
}
