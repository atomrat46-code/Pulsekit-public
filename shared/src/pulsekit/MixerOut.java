package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Add the result to the AudioMixer" (--to_mixer) for SogniMusic, MidiDrumGen, DrumMidi_CRT and DrumAlong (its
 * instrument track too, so the beat plays along with it): after a
 * run that made its files one by one (not into a zip), each sound it made (a WAV or an MP3, or a MIDI
 * played with the kit when no WAV of it was made) becomes a track of the AudioMixer. Pulsekit does it
 * (the switch is taken out before the program runs).
 */
public final class MixerOut {
  private MixerOut() {}

  public static final String FLAG = "--to_mixer";
  public static final String LABEL = "Add the result to the AudioMixer (as a track)";
  /** Extract audio's question, after the sound was taken out of a video. */
  public static final String ASK_TITLE = "Add to the AudioMixer?";
  public static final String ADD = "Add to AudioMixer";
  public static final String NOT_NOW = "Not now";

  public static String ask(String file) {
    return "Add " + file + " to the AudioMixer as a track?";
  }

  /** One file (Extract audio's) added as a track; what it says. */
  public static String addOne(String name, byte[] bytes) {
    List<JavaRun.FileOut> one = new ArrayList<JavaRun.FileOut>();
    one.add(new JavaRun.FileOut(name, bytes));
    return add(one);
  }

  /** At most this many tracks from one run. */
  public static final int MAX = 8;

  /** Set by the app as it starts: the mixer's folder, the kit for a MIDI, and what shows the mixer again (or null). */
  public static volatile File folder;
  public static volatile java.util.function.Supplier<short[][]> kit;
  public static volatile Runnable changed;

  /** One of the programs, run with --to_mixer. */
  public static boolean wants(String program, List<String> argv) {
    return argv != null && argv.contains(FLAG) && takes(program);
  }

  public static boolean takes(String program) {
    String p = program == null ? "" : program;
    return p.equals("SogniMusic.java") || p.equals("MidiDrumGen.java") || p.equals("DrumMidi_CRT.java") || p.equals("DrumMidi_CRT.jar")
        || p.equals("DrumAlong.jar");
  }

  /** The switch for Params. */
  public static ProgramParams.Param param() {
    ProgramParams.Param p = new ProgramParams.Param();
    p.token = FLAG;
    p.flag = true;
    p.takesValue = false;
    p.optional = true;
    p.label = LABEL;
    p.hint = "one file at a time only (not with --zip or a folder)";
    // A folder's run (DrumMidi's --dir, its zips) or a zipped one makes many: not both.
    p.clears = new String[] {"--dir", "--zip", "--zip_mb"};
    return p;
  }

  /** JavaRun's step: the run without --to_mixer, then its sounds added to the mixer (said in its log). */
  static JavaRun.Result around(String name, List<String> argv, java.util.function.Function<List<String>, JavaRun.Result> run) {
    if (!wants(name, argv)) return run.apply(argv);
    List<String> args = new ArrayList<String>(argv);
    while (args.remove(FLAG)) {
      // all of them
    }
    // DrumMidi run by a SogniMusic file sets chain: SogniMusic's own switch decides what goes into the mixer
    // (its tracks); DrumMidi's switch is for DrumMidi run by itself.
    MusicCompare.Chain chain = MusicCompare.chainStep(name) ? MusicCompare.chain() : null;
    if (chain != null && chain.sogniMixer) return run.apply(args);
    JavaRun.Result r = run.apply(args);
    if (r.code != 0) return r;
    String said;
    if (args.contains("--dir")) said = "Not added to the AudioMixer: the run was on a folder (" + FLAG + " is for one file at a time)";
    else if (args.contains("--zip") || args.contains("--zip_mb")) said = "Not added to the AudioMixer: the run made a zip (" + FLAG + " is for one file at a time)";
    else if ("DrumAlong.jar".equals(name)) {
      // The instrument track first, then the beat: the two play together in the mixer.
      List<JavaRun.FileOut> both = new ArrayList<JavaRun.FileOut>();
      for (String a : args) {
        File f = new File(a);
        if (a.startsWith("-") || !f.isFile() || !a.toLowerCase(Locale.ROOT).matches(".+\\.(wav|wave|mp3)")) continue;
        try {
          both.add(new JavaRun.FileOut(f.getName(), java.nio.file.Files.readAllBytes(f.toPath())));
        } catch (Exception ignored) {
          // the beat alone
        }
        break;
      }
      both.addAll(r.files);
      said = ask ? hold(both) : add(both);
    } else said = ask ? hold(namedForChain(name, r.files)) : add(namedForChain(name, r.files));
    note(said);
    return new JavaRun.Result(r.log + (r.log.endsWith("\n") || r.log.isEmpty() ? "" : "\n") + said + "\n", r.files, r.code);
  }

  /** What the runs' mixer steps said, oldest first (a file sets chain shows its own at its end). */
  static final List<String> said = new ArrayList<String>();

  static void note(String lines) {
    synchronized (said) {
      for (String l : lines.split("\n")) if (l.trim().length() > 0) said.add(l.trim());
      while (said.size() > 200) said.remove(0);
    }
  }

  /** How many lines were said so far. */
  public static int saidCount() {
    synchronized (said) {
      return said.size();
    }
  }

  /** The lines said since `from` (saidCount() then). */
  public static List<String> saidSince(int from) {
    synchronized (said) {
      return new ArrayList<String>(said.subList(Math.max(0, Math.min(from, said.size())), said.size()));
    }
  }

  /** Whether a run's log says a track went into the mixer, or waits for the question. */
  public static boolean addedIn(String log) {
    return log != null && (log.contains("Added to the AudioMixer") || log.contains(HELD));
  }

  /**
   * Set by the apps: a run's sounds are held, not added, and the app asks when the run (or its file sets
   * chain) is done which go in and whether they replace the mixer's tracks. Unset: added at once.
   */
  public static volatile boolean ask;
  static final String HELD = "For the AudioMixer (asked when the run is done): ";
  static final List<JavaRun.FileOut> pending = new ArrayList<JavaRun.FileOut>();

  /**
   * A file's kind for the question's ticks: "midi" (a drum MIDI), "stem-drums" ... "stem-other" (Demucs's,
   * DrumMidi --keep-stems), or "audio" (a song's track, a WAV or an MP3).
   */
  public static String kindOf(String name) {
    String low = new File(name == null ? "" : name).getName().toLowerCase(Locale.ROOT);
    if (low.endsWith(".mid") || low.endsWith(".midi")) return "midi";
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(".*-(drums|bass|vocals|other)\\.wav$").matcher(low);
    if (m.matches()) return "stem-" + m.group(1);
    return "audio";
  }

  static final String TICKS = "audiomixer-offer-ticks.txt";

  /** The ticks' file: beside the mixer's folder, not in it (a project saved or exported keeps only its tracks). */
  static File ticksFile() {
    return folder == null ? null : new File(folder.getAbsoluteFile().getParentFile(), TICKS);
  }

  /** Whether the question ticks a file of this kind first: as it was ticked last time (ticked when never asked). */
  public static boolean ticked(String kind) {
    File f = ticksFile();
    if (f == null || !f.isFile()) return true;
    try {
      for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        if (line.startsWith(kind + "=")) return !line.substring(kind.length() + 1).trim().equals("0");
      }
    } catch (Exception ignored) {
      // ticked
    }
    return true;
  }

  /** The question's ticks kept by kind (a kind ticked when any of its files was), for the next question. */
  public static void rememberTicks(List<JavaRun.FileOut> files, boolean[] on) {
    if (folder == null || files == null || on == null) return;
    java.util.Map<String, Boolean> kinds = new java.util.LinkedHashMap<String, Boolean>();
    File f = ticksFile();
    try {
      if (f.isFile()) for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        int eq = line.indexOf('=');
        if (eq > 0) kinds.put(line.substring(0, eq), Boolean.valueOf(!line.substring(eq + 1).trim().equals("0")));
      }
      java.util.Map<String, Boolean> now = new java.util.HashMap<String, Boolean>();
      for (int i = 0; i < files.size() && i < on.length; i++) {
        String k = kindOf(files.get(i).name);
        now.put(k, Boolean.valueOf(on[i] || (now.containsKey(k) && now.get(k).booleanValue())));
      }
      kinds.putAll(now);
      StringBuilder sb = new StringBuilder();
      for (java.util.Map.Entry<String, Boolean> e : kinds.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue().booleanValue() ? 1 : 0).append('\n');
      f.getParentFile().mkdirs();
      java.nio.file.Files.write(f.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception ignored) {
      // asked as ticked next time
    }
  }

  /**
   * A file sets chain's drum MIDI named after its Sogni track ("sogni-Rock-1b198091-turbo drums.mid"),
   * not the file set's run number; other files as they are.
   */
  static List<JavaRun.FileOut> namedForChain(String program, List<JavaRun.FileOut> files) {
    if (!MusicCompare.chainStep(program)) return files;
    MusicCompare.Chain c = MusicCompare.chain();
    String track;
    try {
      track = c == null ? null : c.tracks.get(c.at)[1];
    } catch (Exception ex) {
      track = null;
    }
    if (track == null || track.isEmpty()) return files;
    String stem = new File(track).getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
    List<JavaRun.FileOut> out = new ArrayList<JavaRun.FileOut>();
    for (JavaRun.FileOut f : files) {
      String low = f.name.toLowerCase(Locale.ROOT);
      out.add(low.endsWith(".mid") || low.endsWith(".midi") ? new JavaRun.FileOut(stem + " drums.mid", f.bytes) : f);
    }
    return out;
  }

  /** The sounds kept for the question; what it says. */
  static String hold(List<JavaRun.FileOut> files) {
    List<JavaRun.FileOut> sounds = sounds(files);
    if (sounds.isEmpty()) return "Not added to the AudioMixer: the run made no WAV, MP3 or MIDI";
    StringBuilder names = new StringBuilder();
    synchronized (pending) {
      for (JavaRun.FileOut f : sounds) {
        pending.add(f);
        names.append(names.length() == 0 ? "" : ", ").append(new File(f.name).getName());
      }
    }
    return HELD + names;
  }

  /** Whether sounds wait for the question. */
  public static boolean waiting() {
    synchronized (pending) {
      return !pending.isEmpty();
    }
  }

  /** The sounds waiting (the question's list), taken: the next run starts afresh. */
  public static List<JavaRun.FileOut> takeWaiting() {
    synchronized (pending) {
      List<JavaRun.FileOut> out = new ArrayList<JavaRun.FileOut>(pending);
      pending.clear();
      return out;
    }
  }

  /** The question's title and text: how many wait, and what the mixer has now. */
  public static final String ASK_RUN_TITLE = "Add to the AudioMixer";
  public static final String REPLACE = "Replace its tracks", ADD_TO = "Add to them", ADD_THESE = "Add", SKIP = "Don't add";

  public static String askRun(int waiting, int has) {
    return "The run made " + waiting + (waiting == 1 ? " track" : " tracks") + " for the AudioMixer: tick the ones to add."
        + (has > 0 ? "\nThe AudioMixer has " + has + (has == 1 ? " track" : " tracks") + " already (earlier runs): replace them, or add to them?" : "");
  }

  /** Whether the question is needed: more than one track, or the mixer has some already. */
  public static boolean needsAsking(int waiting) {
    synchronized (AudioMix.class) {
      try {
        if (AudioMix.dir == null || (folder != null && !folder.equals(AudioMix.dir))) AudioMix.open(folder);
      } catch (Exception ignored) {
        // asked
      }
      return waiting > 1 || !AudioMix.tracks.isEmpty();
    }
  }

  /** The question answered: the ticked sounds in (`replace`: the mixer's tracks taken out first); what it says. */
  public static String addChosen(List<JavaRun.FileOut> chosen, boolean replace) {
    if (chosen == null || chosen.isEmpty()) return "Nothing added to the AudioMixer";
    if (replace) {
      synchronized (AudioMix.class) {
        try {
          if (AudioMix.dir == null || (folder != null && !folder.equals(AudioMix.dir))) AudioMix.open(folder);
          AudioMix.clear();
        } catch (Exception ex) {
          return "Not added to the AudioMixer: " + ex.getMessage();
        }
      }
    }
    return add(chosen);
  }

  /**
   * Whether the AudioMixer opens after this run: it added tracks, and nothing follows it (a file sets
   * chain opens it at its end, with its message; the chain's DrumMidi runs do not).
   */
  public static boolean opensAfter(String program, List<String> argv, int code, String log) {
    return code == 0 && addedIn(log) && !MusicCompare.chainStep(program)
        && !(argv != null && argv.contains(MusicCompare.TO_FILE_SETS) && MusicCompare.wanted(program, argv));
  }

  /** The sounds of `files` added as tracks; what it says. */
  public static String add(List<JavaRun.FileOut> files) {
    List<JavaRun.FileOut> sounds = sounds(files);
    if (sounds.isEmpty()) return "Not added to the AudioMixer: the run made no WAV, MP3 or MIDI";
    return addSounds(sounds);
  }

  /** The files a mixer takes: WAVs, MP3s, and MIDIs that have no WAV or MP3 of their own. */
  static List<JavaRun.FileOut> sounds(List<JavaRun.FileOut> files) {
    List<JavaRun.FileOut> sounds = new ArrayList<JavaRun.FileOut>();
    for (JavaRun.FileOut f : files) {
      String low = f.name.toLowerCase(Locale.ROOT);
      if (!AudioMix.importable(f.name) || f.bytes == null || f.bytes.length == 0) continue;
      if (low.endsWith(".mid") || low.endsWith(".midi")) {
        String stem = stem(f.name);
        boolean wav = false;
        for (JavaRun.FileOut o : files) wav |= o != f && stem(o.name).equals(stem) && o.name.toLowerCase(Locale.ROOT).matches(".+\\.(wav|wave|mp3)");
        if (wav) continue;
      }
      sounds.add(f);
    }
    return sounds;
  }

  static String addSounds(List<JavaRun.FileOut> sounds) {
    StringBuilder sb = new StringBuilder();
    try {
      synchronized (AudioMix.class) {
        if (AudioMix.dir == null || (folder != null && !folder.equals(AudioMix.dir))) AudioMix.open(folder);
      }
    } catch (Exception ex) {
      return "Not added to the AudioMixer: " + ex.getMessage();
    }
    int n = 0;
    for (JavaRun.FileOut f : sounds) {
      if (n == MAX) {
        sb.append("Not added to the AudioMixer: ").append(sounds.size() - MAX).append(" more (").append(MAX).append(" a run at most)\n");
        break;
      }
      try {
        java.util.function.Supplier<short[][]> k = kit;
        AudioMix.Track t = AudioMix.add(new File(f.name).getName(), f.bytes, k == null ? null : k.get());
        sb.append("Added to the AudioMixer: ").append(AudioMix.line(t)).append('\n');
        n++;
      } catch (Exception ex) {
        sb.append("Could not add ").append(f.name).append(" to the AudioMixer: ").append(ex.getMessage() == null ? ex.toString() : ex.getMessage()).append('\n');
      }
    }
    Runnable c = changed;
    if (c != null && n > 0) {
      try {
        c.run();
      } catch (Throwable ignored) {
        // shown when it is opened
      }
    }
    String s = sb.toString();
    return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
  }

  static String stem(String name) {
    return new File(name).getName().replaceAll("\\.[A-Za-z0-9]{1,5}$", "").toLowerCase(Locale.ROOT);
  }
}
