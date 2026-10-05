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
    /** An output file: PyJav names it, so the screen leaves it alone. */
    public boolean output;

    public boolean isFile() {
      return ext != null && !output;
    }
  }

  private static final Pattern FILE_EXT = Pattern.compile("(?i)\\.(wav|wave|mp3|midi?|txt)\\b");

  /** The parameters in `text` (a program's source or readable strings), in the order the program lists them. */
  public static List<Param> parse(String text) {
    List<Param> out = fromUsage(text);
    if (out.isEmpty()) out = fromArgparse(text);
    boolean drumMidi = has(out, "--sens") && has(out, "--no-hpss");
    // SogniChat: --prompt is a question and --system the role and way to answer, not a music description.
    boolean chat = has(out, "--prompt") && has(out, "--system");
    for (Param p : out) known(p, drumMidi, chat);
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
    if (p.flag && p.takesValue && p.token.equals("--continue") && chat) {
      p.label = "Continue from saved chat";
      p.hint = "a sogni-chat .txt from an earlier run";
    }
    if (p.flag && p.takesValue && p.token.equals("--system") && chat) {
      p.label = "System role/answer";
      p.hint = "who answers and how, e.g. You are a drum teacher. Answer briefly.";
    }
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

  /** "mid", "txt" or "audio" (wav and mp3 stand in for each other). */
  static String kind(String ext) {
    return "mid".equals(ext) || "txt".equals(ext) ? ext : "audio";
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
        out[i] = v == null ? "" : v;
      } else {
        String v = k < inOrder.size() ? inOrder.get(k) : "";
        k++;
        // A file of the wrong kind (another program's output.mid in a .wav slot) is not this one's.
        String has = ext(v);
        if (p.ext != null && has != null && !kind(has).equals(kind(p.ext))) v = "";
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
