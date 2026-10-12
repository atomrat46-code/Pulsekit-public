package pulsekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a SogniMusic run asked Sogni for, read from its arguments before its test runs: the
 * instruments (--drums_only, --instruments, else the ones its prompt and genre name; vocals with
 * --lyrics), the tempo, length and time signature. The test sets DrumMidi_CRT's run from it (the
 * tempo asked; with instruments besides drums the switches suggested for a mix), and its report
 * says how to read CompareHits' numbers for that kind of track and checks the WAV and DrumMidi's
 * MIDI against it.
 */
public final class MusicExpect {
  public String prompt = "";
  public String genre = "";
  public boolean drumsOnly;
  /** The instruments asked for or named, in order ("drums", "bass", "vocals"...). */
  public final List<String> instruments = new ArrayList<String>();
  /** True when the list came from --instruments or --drums_only, false when read from the prompt. */
  public boolean listed;
  public boolean lyrics;
  public double bpm;
  public double duration;
  public String timesig = "";

  /** Words that name an instrument in a prompt, and the instrument they stand for. */
  private static final String[][] WORDS = {
    {"drum", "drums"}, {"kick", "drums"}, {"snare", "drums"}, {"hi-hat", "drums"}, {"hihat", "drums"}, {"percussion", "percussion"},
    {"bass", "bass"}, {"808", "bass"}, {"guitar", "guitar"}, {"piano", "piano"}, {"keys", "keys"}, {"rhodes", "keys"}, {"organ", "organ"},
    {"synth", "synth"}, {"pad", "synth"}, {"strings", "strings"}, {"violin", "strings"}, {"cello", "strings"}, {"brass", "brass"},
    {"horn", "brass"}, {"trumpet", "brass"}, {"sax", "sax"}, {"flute", "flute"}, {"vocal", "vocals"}, {"voice", "vocals"},
    {"choir", "vocals"}, {"sing", "vocals"}, {"rap", "vocals"},
  };

  /** From SogniMusic's arguments. */
  public static MusicExpect of(List<String> argv) {
    MusicExpect e = new MusicExpect();
    String instr = null;
    if (argv != null) {
      for (int i = 0; i < argv.size(); i++) {
        String a = argv.get(i);
        String v = i + 1 < argv.size() ? argv.get(i + 1) : "";
        if (a.equals("--prompt")) e.prompt = v;
        else if (a.equals("--genre")) e.genre = v;
        else if (a.equals("--drums_only")) e.drumsOnly = true;
        else if (a.equals("--instruments")) instr = v;
        else if (a.equals("--lyrics")) e.lyrics = v.trim().length() > 0;
        else if (a.equals("--bpm")) e.bpm = num(v);
        else if (a.equals("--duration")) e.duration = num(v);
        else if (a.equals("--timesig")) e.timesig = v.trim();
      }
    }
    if (e.drumsOnly) {
      e.instruments.add("drums");
      e.listed = true;
    } else if (instr != null && instr.trim().length() > 0) {
      for (String part : instr.split("[,;/]")) {
        String t = part.trim().toLowerCase(Locale.ROOT);
        if (t.length() > 0 && !e.instruments.contains(t)) e.instruments.add(t);
      }
      e.listed = true;
    } else {
      String text = (e.genre + " " + e.prompt).toLowerCase(Locale.ROOT);
      for (String[] w : WORDS) if (text.contains(w[0]) && !e.instruments.contains(w[1])) e.instruments.add(w[1]);
    }
    if (e.lyrics && !e.instruments.contains("vocals")) e.instruments.add("vocals");
    return e;
  }

  private static double num(String v) {
    try {
      return Double.parseDouble(v.trim());
    } catch (Exception ex) {
      return 0;
    }
  }

  /** Drums asked for: yes (listed, or named), or likely (nothing says otherwise; most genres have drums). */
  public boolean drums() {
    for (String i : instruments) if (i.contains("drum") || i.equals("percussion")) return true;
    return !listed;
  }

  /** Instruments besides drums (and percussion). */
  public List<String> others() {
    List<String> out = new ArrayList<String>();
    for (String i : instruments) if (!i.contains("drum") && !i.equals("percussion")) out.add(i);
    return out;
  }

  /**
   * DrumMidi_CRT's arguments for the test: the WAV and its MIDI, the tempo asked (DrumMidi does not
   * have to find it), and the user's saved DrumMidi switches; without any saved, the switches
   * suggested for a mix when instruments besides drums are expected.
   */
  public List<String> drumMidiArgs(String wav, String midiName, String saved) {
    List<String> out = new ArrayList<String>();
    out.add(wav);
    out.add(midiName);
    List<String> mine = JavaRun.split(saved == null ? "" : saved);
    boolean any = false;
    for (int i = 0; i < mine.size(); i++) {
      String a = mine.get(i);
      if (!a.startsWith("--")) continue;
      // The tempo is the one asked for.
      if (a.equals("--bpm")) {
        i++;
        continue;
      }
      out.add(a);
      any = true;
      if (i + 1 < mine.size() && !mine.get(i + 1).startsWith("--") && !DrumMidiArgs.onOff(a)) out.add(mine.get(++i));
    }
    if (!any && !others().isEmpty()) {
      for (int i = 0; i < DrumMidiArgs.FLAGS.length; i++) {
        if (DrumMidiArgs.SUGGESTED[i].length() == 0) continue;
        out.add(DrumMidiArgs.FLAGS[i]);
        out.add(DrumMidiArgs.SUGGESTED[i]);
      }
    }
    if (bpm > 0) {
      out.add("--bpm");
      out.add(SogniTime.num(bpm));
    }
    return out;
  }

  /** Why DrumMidi runs with those switches, for the report. */
  public String drumMidiWhy(String saved) {
    boolean mine = saved != null && saved.trim().length() > 0;
    StringBuilder sb = new StringBuilder();
    if (mine) sb.append("DrumMidi runs with your saved switches (Params)");
    else if (!others().isEmpty()) sb.append("DrumMidi runs with the switches suggested for a mix (").append(DrumMidiArgs.build(DrumMidiArgs.SUGGESTED))
        .append("), because ").append(join(others())).append(others().size() == 1 ? " is" : " are").append(" expected besides drums");
    else sb.append("DrumMidi runs with its own defaults (drums only)");
    if (bpm > 0) sb.append(", at the tempo asked (--bpm ").append(SogniTime.num(bpm)).append(")");
    return sb.append('.').toString();
  }

  /** The report's first part: what was asked for, and how to read the comparison for it. */
  public String asked() {
    StringBuilder sb = new StringBuilder("== What SogniMusic was asked for\n");
    if (genre.length() > 0) sb.append("Genre: ").append(genre).append('\n');
    if (prompt.length() > 0) sb.append("Prompt: ").append(prompt).append('\n');
    sb.append("Instruments: ").append(instruments.isEmpty() ? "none named (most genres have drums)" : join(instruments))
        .append(listed ? "" : instruments.isEmpty() ? "" : " (named in the prompt)").append('\n');
    if (bpm > 0) sb.append("Tempo: ").append(SogniTime.num(bpm)).append(" BPM\n");
    if (duration > 0) sb.append("Length: ").append(SogniTime.num(duration)).append(" s\n");
    if (timesig.length() > 0) sb.append("Time signature: ").append(timesig).append('\n');
    sb.append('\n');
    if (!drums()) {
      sb.append("No drums were asked for: the hits DrumMidi finds are other instruments, so its numbers say little.\n");
    } else if (others().isEmpty()) {
      sb.append("Drums only: the WAV should hold nothing else, so DrumMidi's hits should line up with the WAV's onsets (precision near 100%). "
          + "Low precision means Sogni added other instruments, or DrumMidi heard a drum's ring as more hits.\n");
    } else {
      boolean named = false;
      for (String i : instruments) if (i.contains("drum") || i.equals("percussion")) named = true;
      if (!named) sb.append("Drums are not named (most tracks have them; if this one has none, DrumMidi's hits are the other instruments). ");
      sb.append(named ? "Drums with " : "With ").append(join(others())).append(": ");
      List<String> notes = new ArrayList<String>();
      for (String o : others()) {
        if (o.contains("bass") || o.equals("808")) add(notes, "the kick band also hears the bass");
        else if (o.contains("guitar") || o.contains("piano") || o.contains("keys") || o.contains("organ") || o.contains("synth")) add(notes, "the snare band also hears " + o);
        else if (o.contains("vocal")) add(notes, "the snare and hat bands also hear the vocals");
        else if (o.contains("string") || o.contains("brass") || o.contains("sax") || o.contains("flute")) add(notes, "sustained " + o + " add to the cymbal band");
      }
      sb.append(notes.isEmpty() ? "the other instruments add onsets of their own" : join(notes))
          .append(". Recall against the WAV is a hint only, and extra hits in DrumMidi's MIDI may be those instruments.\n");
    }
    return sb.toString();
  }

  /** The checks once DrumMidi has run: its tempo, the WAV's length and the drum hits, against what was asked. */
  public String checks(byte[] drumsMidi, double wavSeconds) {
    StringBuilder sb = new StringBuilder("== Checks against what was asked\n");
    double found = HitCompare.midiBpm(drumsMidi);
    if (bpm > 0) {
      double r = found / bpm;
      String how = Math.abs(r - 1) < 0.02 ? "matches" : Math.abs(r - 2) < 0.04 ? "is double (DrumMidi counted eighths as beats)"
          : Math.abs(r - 0.5) < 0.02 ? "is half (DrumMidi counted half notes as beats)" : "differs";
      sb.append(String.format(Locale.ROOT, "Tempo: DrumMidi's MIDI is at %s BPM, asked %s: %s.\n", SogniTime.num(found), SogniTime.num(bpm), how));
    } else {
      sb.append(String.format(Locale.ROOT, "Tempo: DrumMidi's MIDI is at %s BPM (no tempo was asked for).\n", SogniTime.num(found)));
    }
    if (duration > 0 && wavSeconds > 0) {
      double d = wavSeconds - duration;
      sb.append(String.format(Locale.ROOT, "Length: the WAV is %.1f s, asked %s s%s.\n", wavSeconds, SogniTime.num(duration),
          Math.abs(d) <= Math.max(1.5, duration * 0.05) ? "" : d < 0 ? ": Sogni made it shorter" : ": Sogni made it longer"));
    }
    double[][] hits = HitCompare.midiHits(drumsMidi);
    int total = 0;
    for (double[] f : hits) total += f.length;
    double bars = Math.max(1, (wavSeconds > 0 ? wavSeconds : 1) / (240.0 / Math.max(30, found > 0 ? found : 120)));
    sb.append(String.format(Locale.ROOT, "Drum hits: %d in DrumMidi's MIDI (%.1f a bar): %d kicks, %d snares, %d cymbals, %d toms.\n",
        total, total / bars, hits[0].length, hits[1].length, hits[2].length, hits.length > 3 ? hits[3].length : 0));
    if (drums() && total / bars < 2) sb.append("Few drum hits for a track with drums: Sogni may have made little drumming, or DrumMidi's --sens is low.\n");
    if (drumsOnly && hits[0].length == 0 && hits[1].length == 0) sb.append("No kicks or snares in a drums-only track: DrumMidi did not hear the drums.\n");
    return sb.toString();
  }

  private static void add(List<String> l, String s) {
    if (!l.contains(s)) l.add(s);
  }

  static String join(List<String> l) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < l.size(); i++) sb.append(i == 0 ? "" : i == l.size() - 1 ? " and " : ", ").append(l.get(i));
    return sb.toString();
  }

  /** Numbers as SogniMusic says them: 120, 92.5. */
  static final class SogniTime {
    static String num(double v) {
      return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
  }
}
