package pulsekit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The drum sound library: links to drum sounds (files, folders' files, the prompt library's sound
 * files), each in its drum part's container (kick, snare ... floor tom, other), measured by DrumMidi
 * (--sounds) and marked ready then. No sound is copied: an entry is its link, its part and its
 * measures. A pad's long-press menu adds sounds to its part (a file whose name says another drum goes
 * to that one); File > Drum sound library lists them. The measures are DrumMidi's own (its kit
 * profile's spectrum and cymbal look), for profiles made from many sounds later on.
 */
public final class DrumLibrary {
  private DrumLibrary() {}

  public static final String TITLE = "Drum sound library";
  public static final String ADD = "Add sounds to the library…";
  public static final String ADD_FILE = "A sound file";
  public static final String ADD_FOLDER = "A folder of sounds";
  public static final String ADD_TREE = "A folder with its subfolders";
  public static final String ADD_DB = "From the DB";
  public static final String ANALYZE = "Analyze waiting";
  public static final String FILE = "drum-library.txt";
  /** DrumMidi's profile made from the ready sounds (--library), beside the list. */
  public static final String PROFILE = "drum-library.kit";
  /** A link to a prompt library's sound file: "db:" and its name. */
  public static final String DB = "db:";

  public static final String[] PARTS = {"kick", "snare", "rim", "clap", "closed hat", "pedal hat", "open hat", "ride", "ride bell", "crash", "china", "splash",
      "high tom", "mid tom", "floor tom", "other"};
  public static final String[] PART_LABELS = {"Kick", "Snare", "Side stick / rim", "Clap", "Closed hat", "Pedal hat", "Open hat", "Ride", "Ride bell", "Crash",
      "China", "Splash", "High tom", "Mid tom", "Floor tom", "Other"};
  /** Kinds of sound file a link can be. */
  public static final String SOUND_FILES = ".+\\.(wav|wave|mp3)";

  public static final String WAITING = "waiting", READY = "ready", FAILED = "failed", MISSING = "missing";

  /** One sound: its link (a path, a content:// address, or db:<name>), name, part, how it came, state and measures. */
  public static final class Entry {
    public String link = "";
    public String name = "";
    public String part = "other";
    /** "file", "folder" or "db". */
    public String source = "file";
    /** Filed by its name (it said another drum than the pad's). */
    public boolean byName;
    public String state = WAITING;
    /** Why it failed, when it did. */
    public String said = "";
    /** Length (ms), peak (dBFS), attack (ms), decay to -20 and -40 dB (ms), pitch (Hz), brightness (Hz). */
    public double[] measures;
    /** DrumMidi's kit profile spectrum (16 bands, 40-800 Hz) and cymbal look (12 bands, fade, level). */
    public double[] spectrum, look;
    /**
     * How a test beat's hit was played (vst_render --test, DrumMidi --cut-hits): "kick" or "snare" (a
     * cymbal struck with one), "groove" (in eighths); empty for a sound heard alone (a one-shot).
     */
    public String context = "";

    public boolean ready() {
      return READY.equals(state);
    }
  }

  public static File dir;
  /** Where the library is kept, set when the app starts (the list itself is read when first opened). */
  public static volatile File home;
  public static final List<Entry> entries = new ArrayList<Entry>();

  /** The pad's part: Engine.TRACK_ID's order (kick, double kick, snare, clap, rim, closed hat, open hat, crash, ride, floor, mid, rack tom). */
  public static String partOfPad(int pad) {
    String[] byPad = {"kick", "kick", "snare", "clap", "rim", "closed hat", "open hat", "crash", "ride", "floor tom", "mid tom", "high tom"};
    return pad >= 0 && pad < byPad.length ? byPad[pad] : "other";
  }

  public static String label(String part) {
    for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(part)) return PART_LABELS[i];
    return part;
  }

  /**
   * The part a file's name says, or null: "Ride Bell 02.wav" ride bell, "OH_crash.wav" crash, "BD
   * 808.wav" kick, "HH closed.wav" closed hat. The more particular name wins (ride bell over ride,
   * open hat over hat, floor tom over tom).
   */
  public static String guess(String fileName) {
    String n = " " + fileName.toLowerCase(Locale.ROOT).replaceAll("\\.[a-z0-9]{1,5}$", "").replaceAll("[^a-z0-9]+", " ") + " ";
    if (n.matches(".*\\b(ride ?bell|bell ride|ride b)\\b.*")) return "ride bell";
    if (n.matches(".*\\b(china|chinese)\\b.*")) return "china";
    if (n.matches(".*\\b(splash|spl)\\b.*")) return "splash";
    if (n.matches(".*\\b(crash|crsh)\\b.*")) return "crash";
    if (n.matches(".*\\b(ride)\\b.*")) return "ride";
    if (n.matches(".*\\b(open ?hat|open ?hh|ohh|oh|hh ?open|hat ?open|hho|opened)\\b.*")) return "open hat";
    if (n.matches(".*\\b(pedal|foot ?hat|phh|hh ?pedal|hat ?pedal|hhp|chick)\\b.*")) return "pedal hat";
    if (n.matches(".*\\b(closed ?hat|closed ?hh|chh|hh ?closed|hat ?closed|hhc|hh|hihat|hi hat|hat)\\b.*")) return "closed hat";
    if (n.matches(".*\\b(floor ?tom|ftom|ft|low ?tom|tom ?low|lo ?tom|tom ?3|tom ?4)\\b.*")) return "floor tom";
    if (n.matches(".*\\b(mid ?tom|tom ?mid|mtom|tom ?2)\\b.*")) return "mid tom";
    if (n.matches(".*\\b(high ?tom|hi ?tom|rack ?tom|tom ?hi|tom ?high|htom|rtom|tom ?1|tom)\\b.*")) return "high tom";
    if (n.matches(".*\\b(side ?stick|sidestick|cross ?stick|rim|rimshot|stick)\\b.*")) return "rim";
    if (n.matches(".*\\b(clap|claps|cp)\\b.*")) return "clap";
    if (n.matches(".*\\b(snare|snr|sd|sn)\\b.*")) return "snare";
    if (n.matches(".*\\b(kick|kik|kck|bd|bass ?drum|bassdrum)\\b.*")) return "kick";
    return null;
  }

  /** The part a file goes in, added from `padPart`: its name's part when it says another drum. */
  public static String partFor(String fileName, String padPart) {
    String g = guess(fileName);
    return g != null && !g.equals(padPart) ? g : padPart;
  }

  // ---- The list, kept in the app's folder.

  public static synchronized void open(File folder) {
    if (folder != null && folder.equals(dir) && !entries.isEmpty()) return;
    dir = folder;
    if (folder != null) home = folder;
    entries.clear();
    if (dir == null) return;
    File f = new File(dir, FILE);
    if (!f.isFile()) return;
    try {
      for (String line : new String(AudioMix.read(f), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.split("\t", -1);
        if (p.length < 6) continue;
        Entry e = new Entry();
        e.link = p[0];
        e.name = p[1];
        e.part = p[2];
        e.source = p[3];
        e.byName = "1".equals(p[4]);
        e.state = p[5];
        e.said = p.length > 6 ? p[6] : "";
        e.measures = p.length > 7 ? nums(p[7]) : null;
        e.spectrum = p.length > 8 ? nums(p[8]) : null;
        e.look = p.length > 9 ? nums(p[9]) : null;
        e.context = p.length > 10 ? p[10] : "";
        entries.add(e);
      }
    } catch (Exception ignored) {
      // an unreadable list starts empty
    }
  }

  public static synchronized void save() {
    if (dir == null) return;
    StringBuilder sb = new StringBuilder();
    for (Entry e : entries) {
      sb.append(AudioMix.clean(e.link)).append('\t').append(AudioMix.clean(e.name)).append('\t').append(e.part).append('\t').append(e.source).append('\t')
          .append(e.byName ? 1 : 0).append('\t').append(e.state).append('\t').append(AudioMix.clean(e.said)).append('\t').append(text(e.measures)).append('\t')
          .append(text(e.spectrum)).append('\t').append(text(e.look)).append('\t').append(e.context).append('\n');
    }
    try {
      if (!dir.isDirectory()) dir.mkdirs();
      AudioMix.write(new File(dir, FILE), sb.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // kept for this session
    }
    writeProfile();
  }

  // ---- DrumMidi's profile from many sounds (--library).

  /** The parts a profile has, and their notes: drums by spectrum, cymbals ("c" and the note) by look. */
  static final String[] PROFILE_PARTS = {"kick", "floor tom", "mid tom", "high tom", "closed hat", "pedal hat", "open hat", "crash", "china", "ride", "ride bell", "splash"};
  static final String[] PROFILE_NOTES = {"36", "41", "47", "50", "c42", "c44", "c46", "c49", "c52", "c51", "c53", "c55"};

  /**
   * The profile's text: per part, the mean of its ready sounds (each made unit length first, so a loud
   * one counts no more), as DrumMidi's kit profile lines; null without a ready kick and tom.
   */
  public static synchronized String profile() {
    StringBuilder body = new StringBuilder(), from = new StringBuilder();
    boolean kick = false, tom = false;
    for (int i = 0; i < PROFILE_PARTS.length; i++) {
      boolean cym = PROFILE_NOTES[i].startsWith("c");
      int n = cym ? 12 : 16;
      double[] sum = new double[n + 1];
      int count = 0;
      for (Entry e : entries) {
        // Sounds heard alone only: a cymbal heard with a kick or snare, or in eighths, looks otherwise.
        if (!e.ready() || !e.part.equals(PROFILE_PARTS[i]) || !e.context.isEmpty()) continue;
        double[] v = cym ? e.look : e.spectrum;
        if (v == null || v.length < (cym ? n + 1 : n)) continue;
        double norm = 0;
        for (int k = 0; k < n; k++) norm += v[k] * v[k];
        if (norm <= 0) continue;
        norm = Math.sqrt(norm);
        for (int k = 0; k < n; k++) sum[k] += v[k] / norm;
        if (cym) sum[n] += v[n];
        count++;
      }
      if (count == 0) continue;
      double norm = 0;
      for (int k = 0; k < n; k++) norm += sum[k] * sum[k];
      norm = Math.sqrt(norm);
      if (norm <= 0) continue;
      body.append(PROFILE_NOTES[i]);
      for (int k = 0; k < n; k++) body.append(' ').append(String.format(Locale.ROOT, "%.4f", sum[k] / norm));
      if (cym) body.append(' ').append(String.format(Locale.ROOT, "%.4f", sum[n] / count));
      body.append('\n');
      from.append(from.length() == 0 ? "" : ", ").append(count).append(' ').append(PROFILE_PARTS[i]).append(count == 1 ? "" : PROFILE_PARTS[i].endsWith("sh") ? "es" : "s");
      if (i == 0) kick = true;
      else if (!cym) tom = true;
    }
    if (!kick || !tom) return null;
    return "# DrumMidi kit profile from the drum sound library (--library): note, then its 16 band spectrum 40-800 Hz; cymbals c<note>, 12 bands 2500-16000 Hz and fade\n"
        + "# from " + from + "\n" + body;
  }

  /** The profile written beside the list, or taken away when there is none to make. */
  static void writeProfile() {
    if (dir == null) return;
    File f = new File(dir, PROFILE);
    String text = profile();
    try {
      if (text == null) f.delete();
      else AudioMix.write(f, text.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // none this time
    }
  }

  /** The profile's file when there is one. */
  public static File profileFile() {
    File h = dir != null ? dir : home;
    File f = h == null ? null : new File(h, PROFILE);
    return f != null && f.isFile() ? f : null;
  }

  /** What the profile is, for the library's screen. */
  public static String profileLine() {
    String text = profile();
    if (text == null) return "DrumMidi profile: needs a ready kick and a ready tom";
    String from = text.split("\n")[1].substring(7);
    return "DrumMidi profile (" + PROFILE + "): " + from + ". DrumMidi's runs tell kicks and toms apart by it when no kit profile is given";
  }

  /**
   * DrumMidi's arguments with the library's profile (--library) added: on a run that finds drums in
   * audio, when there is a profile and no --library given. "--library none" leaves it out.
   */
  public static List<String> withProfile(List<String> argv) {
    if (argv == null || argv.isEmpty()) return argv;
    String first = argv.get(0);
    if ("--sounds".equals(first) || "--learn-kit".equals(first)) return argv;
    int at = argv.indexOf("--library");
    if (at >= 0) {
      if (at + 1 < argv.size() && "none".equalsIgnoreCase(argv.get(at + 1).trim())) {
        List<String> out = new ArrayList<String>(argv);
        out.remove(at + 1);
        out.remove(at);
        return out;
      }
      return argv;
    }
    File f = profileFile();
    if (f == null || argv.size() < 2 && !argv.contains("--dir")) return argv;
    List<String> out = new ArrayList<String>(argv);
    out.add("--library");
    out.add(f.getAbsolutePath());
    return out;
  }

  static double[] nums(String s) {
    if (s == null || s.trim().isEmpty()) return null;
    String[] p = s.split(",");
    double[] out = new double[p.length];
    for (int i = 0; i < p.length; i++) out[i] = AudioMix.parseDouble(p[i]);
    return out;
  }

  static String text(double[] v) {
    if (v == null) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < v.length; i++) sb.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.4g", v[i]));
    return sb.toString();
  }

  public static synchronized Entry find(String link) {
    for (Entry e : entries) if (e.link.equals(link)) return e;
    return null;
  }

  /**
   * A sound added (`link`, named `name`) under `part` (its name's part when it says another drum than
   * `part`); one already in the library is left where it is. The entry, or null when it was there.
   */
  public static synchronized Entry add(String link, String name, String part, String source) {
    if (find(link) != null) return null;
    Entry e = new Entry();
    e.link = link;
    e.name = name;
    e.part = partFor(name, part);
    e.byName = !e.part.equals(part);
    e.source = source;
    // A test beat's hit added as a plain file (on the phone, a vst-hits folder): its part and how it was
    // played from its name ("closed hat groove 03.wav").
    java.util.regex.Matcher m = TEST_HIT.matcher(name);
    if (m.matches() && java.util.Arrays.asList(PARTS).contains(m.group(1))) {
      e.part = m.group(1);
      e.byName = !e.part.equals(part);
      e.context = "alone".equals(m.group(2)) ? "" : m.group(2);
    }
    entries.add(e);
    return e;
  }

  /** A test beat hit's file name: its part, how it was played and its number. */
  static final java.util.regex.Pattern TEST_HIT = java.util.regex.Pattern.compile("([a-z ]+?) (alone|kick|snare|groove) \\d+\\.wav");

  public static synchronized void remove(Entry e) {
    entries.remove(e);
    save();
  }

  /** An entry put in another part (no longer "by name"). */
  public static synchronized void move(Entry e, String part) {
    e.part = part;
    e.byName = false;
    save();
  }

  /** The entries of a part (null: all), sorted by `sort` (SORTS). */
  public static synchronized List<Entry> of(String part, int sort) {
    List<Entry> out = new ArrayList<Entry>();
    for (Entry e : entries) if (part == null || part.equals(e.part)) out.add(e);
    final int by = sort;
    Collections.sort(out, new Comparator<Entry>() {
      public int compare(Entry a, Entry b) {
        if (by <= 0) return a.name.compareToIgnoreCase(b.name);
        double x = a.measures == null ? Double.NaN : a.measures[by - 1], y = b.measures == null ? Double.NaN : b.measures[by - 1];
        if (Double.isNaN(x) || Double.isNaN(y)) return Double.isNaN(x) ? (Double.isNaN(y) ? 0 : 1) : -1;
        return Double.compare(x, y);
      }
    });
    return out;
  }

  public static final String[] SORTS = {"Name", "Length", "Peak", "Attack", "Decay -20 dB", "Decay -40 dB", "Pitch", "Brightness"};

  /** {all, ready, waiting} of a part (null: all). */
  public static synchronized int[] counts(String part) {
    int[] c = new int[3];
    for (Entry e : entries) {
      if (part != null && !part.equals(e.part)) continue;
      c[0]++;
      if (e.ready()) c[1]++;
      if (WAITING.equals(e.state)) c[2]++;
    }
    return c;
  }

  /** "Kick (12, 10 ready)". */
  public static String partLine(String part) {
    int[] c = counts(part);
    return (part == null ? "All parts" : label(part)) + " (" + c[0] + (c[0] == 0 ? "" : ", " + c[1] + " ready") + ")";
  }

  /** An entry's line: "Kick 01 · 0.61 s · -3 dB · attack 10 ms · decay 261/516 ms · 55 Hz · bright 80 Hz". */
  public static String line(Entry e) {
    String head = e.name + (e.byName ? " (by name: " + label(e.part) + ")" : "") + (e.context.isEmpty() ? "" : " (" + contextLabel(e.context) + ")");
    if (!e.ready()) return head + " · " + (FAILED.equals(e.state) ? "failed: " + e.said : MISSING.equals(e.state) ? "missing: " + e.said : "waiting to be analyzed");
    double[] m = e.measures;
    return String.format(Locale.ROOT, "%s · %.2f s · %.0f dB · attack %.0f ms · decay %.0f/%.0f ms · %s · bright %.0f Hz", head, m[0] / 1000, m[1], m[2], m[3], m[4],
        m[5] > 0 && (e.part.endsWith("tom") || e.part.equals("kick")) ? String.format(Locale.ROOT, "%.0f Hz", m[5]) : "no pitch", m[6]);
  }

  // ---- Analysis: DrumMidi --sounds on the waiting ones.

  /** What the app gives: a readable file for an entry (the file, or a copy of a document or DB file), and DrumMidi's jar. */
  public interface Access {
    /** A file DrumMidi can read for the entry; throws when the link no longer leads to a sound. */
    File local(Entry e, File work) throws Exception;

    byte[] jar();
  }

  /** The entries still to measure. */
  public static synchronized List<Entry> waiting() {
    List<Entry> out = new ArrayList<Entry>();
    for (Entry e : entries) if (WAITING.equals(e.state)) out.add(e);
    return out;
  }

  /**
   * `todo` measured by DrumMidi (--sounds), in batches of BATCH; each made ready, failed or missing,
   * the list kept after each batch (`progress` told how many are done). What it says.
   */
  public static String analyze(List<Entry> todo, Access a, File work, java.util.function.IntConsumer progress) {
    int ready = 0, failed = 0, missing = 0, done = 0;
    if (!work.isDirectory()) work.mkdirs();
    byte[] jar = a.jar();
    for (int from = 0; from < todo.size(); from += BATCH) {
      List<Entry> batch = todo.subList(from, Math.min(todo.size(), from + BATCH));
      java.util.Map<String, Entry> byPath = new java.util.LinkedHashMap<String, Entry>();
      List<File> copies = new ArrayList<File>();
      for (Entry e : batch) {
        try {
          File f = a.local(e, work);
          // A copy made for it (a document's or the DB's) is in `work`: deleted after.
          if (work.getAbsoluteFile().equals(f.getAbsoluteFile().getParentFile())) copies.add(f);
          byPath.put(f.getAbsolutePath(), e);
        } catch (Exception ex) {
          synchronized (DrumLibrary.class) {
            e.state = MISSING;
            e.said = ex.getMessage() == null ? ex.toString() : ex.getMessage();
          }
          missing++;
        }
      }
      if (!byPath.isEmpty()) {
        try {
          File list = new File(work, "sounds.txt"), out = new File(work, "measures.txt");
          StringBuilder sb = new StringBuilder();
          for (String p : byPath.keySet()) sb.append(p).append('\n');
          AudioMix.write(list, sb.toString().getBytes(StandardCharsets.UTF_8));
          out.delete();
          List<String> argv = java.util.Arrays.asList("--sounds", list.getAbsolutePath(), out.getAbsolutePath());
          java.util.function.Function<List<String>, JavaRun.Result> run = runner;
          JavaRun.Result r = run != null ? run.apply(argv) : JavaRun.run("DrumMidi_CRT.jar", null, jar, argv);
          if (!out.isFile()) throw new java.io.IOException("DrumMidi measured nothing: " + r.log.trim());
          for (String line : new String(AudioMix.read(out), StandardCharsets.UTF_8).split("\n")) {
            String[] p = line.split("\t", -1);
            Entry e = p.length > 1 ? byPath.remove(p[0]) : null;
            if (e == null) continue;
            synchronized (DrumLibrary.class) {
              if ("ok".equals(p[1]) && p.length >= 11) {
                e.measures = new double[7];
                for (int i = 0; i < 7; i++) e.measures[i] = AudioMix.parseDouble(p[2 + i]);
                e.spectrum = nums(p[9]);
                e.look = nums(p[10]);
                e.state = READY;
                e.said = "";
                ready++;
              } else {
                e.state = FAILED;
                e.said = p.length > 2 ? p[2] : "not measured";
                failed++;
              }
            }
          }
          for (Entry e : byPath.values()) {
            e.state = FAILED;
            e.said = "not measured";
            failed++;
          }
        } catch (Exception ex) {
          for (Entry e : byPath.values()) {
            e.state = FAILED;
            e.said = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            failed++;
          }
        }
      }
      for (File c : copies) c.delete();
      done += batch.size();
      save();
      if (progress != null) progress.accept(done);
    }
    return "Analyzed " + todo.size() + " sound" + (todo.size() == 1 ? "" : "s") + ": " + ready + " ready" + (failed > 0 ? ", " + failed + " failed" : "")
        + (missing > 0 ? ", " + missing + " missing" : "");
  }

  /** Runs DrumMidi with these arguments in place of JavaRun (the tests'); null: JavaRun. */
  public static volatile java.util.function.Function<List<String>, JavaRun.Result> runner;

  /** Sounds measured in one DrumMidi run. */
  public static final int BATCH = 40;

  /** The sound files of a folder (`tree`: its subfolders too), by name. */
  public static List<File> soundFiles(File folder, boolean tree) {
    List<File> out = new ArrayList<File>();
    File[] in = folder.listFiles();
    if (in == null) return out;
    java.util.Arrays.sort(in);
    for (File f : in) {
      if (f.isDirectory()) {
        if (tree && !f.getName().startsWith(".")) out.addAll(soundFiles(f, true));
      } else if (f.getName().toLowerCase(Locale.ROOT).matches(SOUND_FILES)) out.add(f);
    }
    return out;
  }

  /** How a test beat's hit was played, said. */
  public static String contextLabel(String context) {
    return "kick".equals(context) ? "with a kick" : "snare".equals(context) ? "with a snare" : "groove".equals(context) ? "in eighths" : "alone";
  }

  // ---- vst_render's test beat hits (DrumMidi --cut-hits): a folder with its index.

  /** The index of a folder of test beat hits. */
  public static final String INDEX = "hits.txt";
  /** vst_render's line naming the folder of a test's hits. */
  public static final String HITS_SAID = "Hits for the drum sound library: ";

  /**
   * A folder of test beat hits added by its index: each hit in its part, with how it was played and
   * DrumMidi's measures (ready at once: read in the whole render, as in a song). A hit already there is
   * measured anew. Named "<file> (<kit>)": the kit is the folder's name after "vst-hits-".
   */
  public static synchronized String addIndex(File folder) {
    File f = new File(folder, INDEX);
    String kit = folder.getName().startsWith("vst-hits-") ? folder.getName().substring(9) : folder.getName();
    int added = 0, again = 0;
    try {
      for (String line : new String(AudioMix.read(f), StandardCharsets.UTF_8).split("\n")) {
        String[] p = line.split("\t", -1);
        if (line.startsWith("#") || p.length < 12) continue;
        File sound = new File(folder, p[0]);
        if (!sound.isFile()) continue;
        Entry e = find(sound.getAbsolutePath());
        if (e == null) {
          e = new Entry();
          e.link = sound.getAbsolutePath();
          e.source = "test";
          entries.add(e);
          added++;
        } else {
          again++;
        }
        e.name = p[0] + " (" + kit + ")";
        e.part = p[1];
        e.byName = false;
        e.context = "alone".equals(p[2]) ? "" : p[2];
        e.measures = new double[7];
        for (int i = 0; i < 7; i++) e.measures[i] = AudioMix.parseDouble(p[3 + i]);
        e.spectrum = nums(p[10]);
        e.look = nums(p[11]);
        e.state = READY;
        e.said = "";
      }
    } catch (Exception ex) {
      return "Could not read " + INDEX + " in " + folder.getName() + ": " + ex.getMessage();
    }
    save();
    return "Added " + added + " test beat hit" + (added == 1 ? "" : "s") + " of " + kit + (again > 0 ? " (" + again + " already there, measured anew)" : "");
  }

  /**
   * A folder's sounds added: a folder of test beat hits (its index) by its index; with `tree` its
   * subfolders too, each such one by its index and the other sounds under `part` or their names' parts.
   */
  public static String addFolder(File folder, boolean tree, String part, String source) {
    if (new File(folder, INDEX).isFile()) return addIndex(folder);
    if (!tree) {
      List<File> files = soundFiles(folder, false);
      return files.isEmpty() ? "No WAV or MP3 in " + folder.getName() : addFiles(files, part, source);
    }
    List<String> said = new ArrayList<String>();
    List<File> loose = new ArrayList<File>();
    collect(folder, loose, said);
    if (!loose.isEmpty()) said.add(0, addFiles(loose, part, source));
    return said.isEmpty() ? "No WAV or MP3 in " + folder.getName() : String.join("; ", said);
  }

  private static void collect(File folder, List<File> loose, List<String> said) {
    if (new File(folder, INDEX).isFile()) {
      said.add(addIndex(folder));
      return;
    }
    File[] in = folder.listFiles();
    if (in == null) return;
    java.util.Arrays.sort(in);
    for (File f : in) {
      if (f.isDirectory()) {
        if (!f.getName().startsWith(".")) collect(f, loose, said);
      } else if (f.getName().toLowerCase(Locale.ROOT).matches(SOUND_FILES)) loose.add(f);
    }
  }

  /** A vst_render run's test beat hits (its HITS_SAID line) added; what it says, or null when it cut none. */
  public static String fromLog(String log) {
    if (log == null) return null;
    for (String line : log.split("\n")) {
      int at = line.indexOf(HITS_SAID);
      if (at < 0) continue;
      String rest = line.substring(at + HITS_SAID.length()).trim();
      int cut = rest.lastIndexOf(" (Cut ");
      File folder = new File(cut > 0 ? rest.substring(0, cut) : rest);
      if (new File(folder, INDEX).isFile()) return addIndex(folder);
    }
    return null;
  }

  /** Files added (from a folder or one), under `part` or their names' parts; what it says. */
  public static String addFiles(List<File> files, String part, String source) {
    int added = 0, byName = 0;
    for (File f : files) {
      Entry e = add(f.getAbsolutePath(), f.getName(), part, source);
      if (e == null) continue;
      added++;
      if (e.byName) byName++;
    }
    save();
    return added == 0 ? "Already in the library: " + files.size() + " sound" + (files.size() == 1 ? "" : "s")
        : "Added " + added + " sound" + (added == 1 ? "" : "s") + " to " + label(part) + (byName > 0 ? " (" + byName + " to other parts by their names)" : "")
            + (files.size() > added ? "; " + (files.size() - added) + " already there" : "");
  }
}
