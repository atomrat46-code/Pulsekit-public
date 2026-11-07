package pulsekit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Add the result to the AudioMixer" (--to_mixer) for SogniMusic, MidiDrumGen and DrumMidi_CRT: after a
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
    return p.equals("SogniMusic.java") || p.equals("MidiDrumGen.java") || p.equals("DrumMidi_CRT.java") || p.equals("DrumMidi_CRT.jar");
  }

  /** The switch for Params. */
  public static ProgramParams.Param param() {
    ProgramParams.Param p = new ProgramParams.Param();
    p.token = FLAG;
    p.flag = true;
    p.takesValue = false;
    p.optional = true;
    p.label = LABEL;
    p.hint = "one file at a time only (not with --zip)";
    return p;
  }

  /** JavaRun's step: the run without --to_mixer, then its sounds added to the mixer (said in its log). */
  static JavaRun.Result around(String name, List<String> argv, java.util.function.Function<List<String>, JavaRun.Result> run) {
    if (!wants(name, argv)) return run.apply(argv);
    List<String> args = new ArrayList<String>(argv);
    while (args.remove(FLAG)) {
      // all of them
    }
    JavaRun.Result r = run.apply(args);
    if (r.code != 0) return r;
    String said;
    if (args.contains("--zip") || args.contains("--zip_mb")) said = "Not added to the AudioMixer: the run made a zip (" + FLAG + " is for one file at a time)";
    else said = add(r.files);
    return new JavaRun.Result(r.log + (r.log.endsWith("\n") || r.log.isEmpty() ? "" : "\n") + said + "\n", r.files, r.code);
  }

  /** The sounds of `files` added as tracks; what it says. */
  public static String add(List<JavaRun.FileOut> files) {
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
    if (sounds.isEmpty()) return "Not added to the AudioMixer: the run made no WAV, MP3 or MIDI";
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
