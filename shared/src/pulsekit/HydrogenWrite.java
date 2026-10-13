package pulsekit;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Export > Hydrogen: MIDI drums as a Hydrogen pattern (.h2pattern) or song (.h2song), for the
 * GMRockKit that comes with Hydrogen (Hydrogen imports patterns, not MIDI). A song is one pattern
 * a bar (the same bar played again is the same pattern), in order, at the MIDI's tempo. A note the
 * kit has no instrument for goes to the nearest one (a GM tom to a tom, a china to the crash).
 * Hydrogen (the class) reads them back.
 */
public final class HydrogenWrite {
  /** Hydrogen's ticks a quarter note. */
  public static final int RES = 48;

  /** GMRockKit's instruments: name|MIDI note|volume|pan L|pan R|layers (file:min:max;...), as Hydrogen ships it. */
  static final String[] KIT = {
    "Kick|36|1|1|1|Kick-Softest.wav:0:0.202899;Kick-Soft.wav:0.202899:0.376812;Kick-Med.wav:0.369565:0.731884;Kick-Hard.wav:0.731884:0.865942;Kick-Hardest.wav:0.855072:1",
    "Stick|37|0.99569|1|0.7|SideStick-Softest.wav:0:0.181159;SideStick-Soft.wav:0.188406:0.398551;SideStick-Med.wav:0.402174:0.597826;SideStick-Hard.wav:0.597826:0.782609;SideStick-Hardest.wav:0.768116:1",
    "Snare|38|1.02155|1|0.7|Snare-Softest.wav:0:0.206522;Snare-Soft.wav:0.202899:0.380435;Snare-Med.wav:0.376812:0.572464;Snare-Hard.wav:0.568841:0.782609;Snare-Hardest.wav:0.782609:1",
    "Hand Clap|39|1.02155|0.7|1|HandClap.wav:0:1",
    "Snare Rimshot|38|1|1|0.7|SnareRimshot-Softest.wav:0:0.192029;SnareRimshot-Soft.wav:0.188406:0.387681;SnareRimshot-Med.wav:0.380435:0.597826;SnareRimshot-Hard.wav:0.594203:0.731884;SnareRimshot-Hardest.wav:0.728261:1",
    "Floor Tom|41|1.04741|0.44|1|TomFloor-Softest.wav:0:0.199275;TomFloor-Soft.wav:0.199275:0.391304;TomFloor-Med.wav:0.394928:0.608696;TomFloor-Hard.wav:0.608696:0.786232;TomFloor-Hardest.wav:0.786232:1",
    "Hat Closed|42|0.685345|1|0.48|HatClosed-Softest.wav:0:0.173913;HatClosed-Soft.wav:0.173913:0.376812;HatClosed-Med.wav:0.373188:0.572464;HatClosed-Hard.wav:0.576087:0.786232;HatClosed-Hardest.wav:0.786232:1",
    "Tom 2|43|1|0.76|1|Tom2-Softest.wav:0:0.177536;Tom2-Soft.wav:0.177536:0.376812;Tom2-Med.wav:0.373188:0.572464;Tom2-Hard.wav:0.576087:0.786232;Tom2-Hardest.wav:0.782609:1",
    "Hat Pedal|44|0.685345|1|0.48|HatPedal-Softest.wav:0:0.206522;HatPedal-Soft.wav:0.210145:0.391304;HatPedal-Med.wav:0.384058:0.59058;HatPedal-Hard.wav:0.594203:0.793478;HatPedal-Hardest.wav:0.797101:1",
    "Tom 1|45|1|1|0.8|Tom1-Softest.wav:0:0.202899;Tom1-Soft.wav:0.202899:0.398551;Tom1-Med.wav:0.394928:0.601449;Tom1-Hard.wav:0.605072:0.789855;Tom1-Hardest.wav:0.786232:1",
    "Hat Open|46|0.698276|1|0.48|HatOpen-Softest.wav:0:0.202899;HatOpen-Soft.wav:0.202899:0.394928;HatOpen-Med.wav:0.398551:0.601449;HatOpen-Hard.wav:0.605072:0.793478;HatOpen-Hardest.wav:0.797101:1",
    "Cowbell|56|0.568965|1|0.32|Cowbell-Softest.wav:0:0.199275;Cowbell-Soft.wav:0.184783:0.384058;Cowbell-Med.wav:0.384058:0.57971;Cowbell-Hard.wav:0.576087:0.778986;Cowbell-Hardest.wav:0.782609:1",
    "Ride|51|0.633621|0.56|1|Ride-Softest.wav:0:0.195652;Ride-Soft.wav:0.195652:0.373188;Ride-Med.wav:0.376812:0.576087;Ride-Hard.wav:0.572464:0.775362;Ride-Hardest.wav:0.782609:1",
    "Crash|49|0.517241|1|0.74|Crash-Softest.wav:0:0.195652;Crash-Soft.wav:0.199275:0.380435;Crash-Med.wav:0.384058:0.586957;Crash-Hard.wav:0.59058:0.782609;Crash-Hardest.wav:0.789855:1",
    "Ride 2|59|1|0.5|1|24Ride-5.wav:0.8:1;24Ride-4.wav:0.6:0.8;24Ride-3.wav:0.4:0.6;24Ride-2.wav:0.2:0.4;24Ride-1.wav:0:0.2",
    "Splash|57|0.543103|0.98|1|Splash-Softest.wav:0:0.195652;Splash-Soft.wav:0.195652:0.362319;Splash-Med.wav:0.362319:0.565217;Splash-Hard.wav:0.568841:0.753623;Splash-Hardest.wav:0.75:1",
    "Hat Semi-Open|82|0.69|1|0.48|HatSemiOpen-Softest.wav:0:0.192029;HatSemiOpen-Soft.wav:0.188406:0.380435;HatSemiOpen-Med.wav:0.380435:0.583333;HatSemiOpen-Hard.wav:0.57971:0.789855;HatSemiOpen-Hardest.wav:0.782609:1",
    "Bell|81|0.534828|0.56|1|Bell-Softest.wav:0:0.217391;Bell-Soft.wav:0.210145:0.402174;Bell-Med.wav:0.405797:0.605072;Bell-Hard.wav:0.605072:0.782609;Bell-Hardest.wav:0.786232:1",
  };

  private HydrogenWrite() {}

  /** The kit instrument for a GM drum note, or -1 for none. */
  public static int instrument(int note) {
    int[] direct = {36, 37, 38, 39, -1, 41, 42, 43, 44, 45, 46, 56, 51, 49, 59, 57, 82, 81};
    for (int i = 0; i < direct.length; i++) if (direct[i] == note) return i;
    switch (note) {
      case 35: return 0;
      case 40: return 2;
      case 47: case 48: case 50: return 9;
      case 52: case 55: return 13;
      case 53: return 17;
      case 54: case 58: case 69: case 70: return 11;
      default: return -1;
    }
  }

  /** A MIDI file's drum notes {tick, key, velocity}, its resolution, length, time signature and tempo. */
  static final class Notes {
    int ppq = 480;
    long last;
    int num = 4, den = 4;
    double bpm = 120;
    boolean sig, tempo;
    final List<int[]> notes = new ArrayList<int[]>();
  }

  static Notes read(byte[] d) {
    if (d == null || d.length < 14 || d[0] != 'M' || d[1] != 'T' || d[2] != 'h' || d[3] != 'd') throw new IllegalArgumentException("not a MIDI file");
    Notes m = new Notes();
    int div = ((d[12] & 0xff) << 8) | (d[13] & 0xff);
    if ((div & 0x8000) != 0) throw new IllegalArgumentException("SMPTE-timed MIDI is not supported");
    m.ppq = div == 0 ? 480 : div;
    List<int[]> all = new ArrayList<int[]>();
    boolean ch10 = false;
    int i = 8 + be32(d, 4);
    while (i + 8 <= d.length) {
      int len = be32(d, i + 4);
      boolean track = d[i] == 'M' && d[i + 1] == 'T' && d[i + 2] == 'r' && d[i + 3] == 'k';
      int p = i + 8, end = Math.min(d.length, p + Math.max(0, len));
      i = end;
      if (!track) continue;
      long tick = 0;
      int status = 0;
      while (p < end) {
        long delta = 0;
        int b;
        do {
          b = d[p++] & 0xff;
          delta = (delta << 7) | (b & 0x7f);
        } while ((b & 0x80) != 0 && p < end);
        tick += delta;
        if (p >= end) break;
        int s = d[p] & 0xff;
        if (s == 0xff || s == 0xf0 || s == 0xf7) {
          int type = s == 0xff && p + 1 < end ? d[p + 1] & 0xff : -1;
          p += s == 0xff ? 2 : 1;
          long l = 0;
          do {
            b = p < end ? d[p++] & 0xff : 0;
            l = (l << 7) | (b & 0x7f);
          } while ((b & 0x80) != 0 && p < end);
          if (type == 0x58 && l >= 2 && p + 1 < end && !m.sig) {
            m.num = Math.max(1, d[p] & 0xff);
            m.den = 1 << Math.min(5, d[p + 1] & 0xff);
            m.sig = true;
          }
          if (type == 0x51 && l >= 3 && p + 2 < end && !m.tempo) {
            int mpq = ((d[p] & 0xff) << 16) | ((d[p + 1] & 0xff) << 8) | (d[p + 2] & 0xff);
            if (mpq > 0) m.bpm = 60000000.0 / mpq;
            m.tempo = true;
          }
          p += (int) l;
          continue;
        }
        if ((s & 0x80) != 0) {
          status = s;
          p++;
        }
        if (status == 0) break;
        int kind = status & 0xf0;
        int n = kind == 0xc0 || kind == 0xd0 ? 1 : 2;
        if (p + n > end) break;
        if (kind == 0x90 || kind == 0x80) {
          m.last = Math.max(m.last, tick);
          int vel = d[p + 1] & 0x7f;
          if (kind == 0x90 && vel > 0) {
            all.add(new int[] {(int) tick, status & 0x0f, d[p] & 0x7f, vel});
            if ((status & 0x0f) == 9) ch10 = true;
          }
        }
        p += n;
      }
    }
    // Channel 10 only, when the file has it (the drums of a band's MIDI).
    for (int[] n : all) if (!ch10 || n[1] == 9) m.notes.add(new int[] {n[0], n[2], n[3]});
    java.util.Collections.sort(m.notes, new java.util.Comparator<int[]>() {
      public int compare(int[] a, int[] b) {
        return a[0] != b[0] ? (a[0] < b[0] ? -1 : 1) : a[1] - b[1];
      }
    });
    return m;
  }

  /** The ticks of a bar of num/den. */
  static int barTicks(int num, int den) {
    return Math.max(1, num * RES * 4 / Math.max(1, den));
  }

  /** How many bars the notes take (at least one). */
  private static int bars(Notes m, int bar) {
    long lastNote = 0;
    for (int[] n : m.notes) lastNote = Math.max(lastNote, Math.round(n[0] * (double) RES / m.ppq));
    long end = Math.max(lastNote + 1, Math.round(m.last * (double) RES / m.ppq));
    return (int) Math.max(1, (end + bar - 1) / bar);
  }

  /** The MIDI's drums from `from` to `to` (Hydrogen ticks): {position, instrument, velocity}; notes the kit cannot play are left out. */
  private static List<int[]> hydrogenNotes(Notes m, long from, long to) {
    List<int[]> out = new ArrayList<int[]>();
    for (int[] n : m.notes) {
      long pos = Math.round(n[0] * (double) RES / m.ppq);
      if (pos < from || pos >= to) continue;
      int inst = instrument(n[1]);
      if (inst < 0) continue;
      out.add(new int[] {(int) (pos - from), inst, n[2]});
    }
    return out;
  }

  private static String noteList(List<int[]> notes, String indent) {
    StringBuilder sb = new StringBuilder();
    for (int[] n : notes) {
      sb.append(indent).append("<note>\n");
      sb.append(indent).append(" <position>").append(n[0]).append("</position>\n");
      sb.append(indent).append(" <leadlag>0</leadlag>\n");
      sb.append(indent).append(" <velocity>").append(fmt(n[2] / 127.0)).append("</velocity>\n");
      sb.append(indent).append(" <pan_L>0.5</pan_L>\n");
      sb.append(indent).append(" <pan_R>0.5</pan_R>\n");
      sb.append(indent).append(" <pitch>0</pitch>\n");
      sb.append(indent).append(" <probability>1</probability>\n");
      sb.append(indent).append(" <key>C0</key>\n");
      sb.append(indent).append(" <length>-1</length>\n");
      sb.append(indent).append(" <instrument>").append(n[1]).append("</instrument>\n");
      sb.append(indent).append(" <note_off>false</note_off>\n");
      sb.append(indent).append("</note>\n");
    }
    return sb.toString();
  }

  /** The MIDI as one pattern (whole bars), named `name`, in `category`. */
  public static byte[] pattern(byte[] midi, String name, String category) {
    Notes m = read(midi);
    if (m.notes.isEmpty()) throw new IllegalArgumentException("no drum notes");
    int bar = barTicks(m.num, m.den);
    long size = (long) bars(m, bar) * bar;
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<drumkit_pattern>\n");
    sb.append(" <pattern_for_drumkit>GMRockKit</pattern_for_drumkit>\n");
    sb.append(" <pattern>\n");
    sb.append("  <pattern_name>").append(xml(name)).append("</pattern_name>\n");
    sb.append("  <category>").append(xml(category == null || category.length() == 0 ? "Pulsekit" : category)).append("</category>\n");
    sb.append("  <size>").append(size).append("</size>\n");
    sb.append("  <denominator>").append(m.den).append("</denominator>\n");
    sb.append("  <noteList>\n");
    sb.append(noteList(hydrogenNotes(m, 0, size), "   "));
    sb.append("  </noteList>\n");
    sb.append(" </pattern>\n");
    sb.append("</drumkit_pattern>\n");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** The MIDI as a song: one pattern a bar (a bar played again is the same pattern), in order, at its tempo. */
  public static byte[] song(byte[] midi, String name) {
    Notes m = read(midi);
    if (m.notes.isEmpty()) throw new IllegalArgumentException("no drum notes");
    int bar = barTicks(m.num, m.den);
    int bars = bars(m, bar);
    Map<String, String> byNotes = new LinkedHashMap<String, String>();
    Map<String, List<int[]>> patterns = new LinkedHashMap<String, List<int[]>>();
    List<String> sequence = new ArrayList<String>();
    for (int b = 0; b < bars; b++) {
      List<int[]> notes = hydrogenNotes(m, (long) b * bar, (long) (b + 1) * bar);
      StringBuilder key = new StringBuilder();
      for (int[] n : notes) key.append(n[0]).append(',').append(n[1]).append(',').append(n[2]).append(';');
      String id = byNotes.get(key.toString());
      if (id == null) {
        id = "Bar " + (b + 1);
        byNotes.put(key.toString(), id);
        patterns.put(id, notes);
      }
      sequence.add(id);
    }
    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<song>\n");
    sb.append(" <version>1.2.0</version>\n");
    sb.append(" <bpm>").append(fmt(Math.max(10, Math.min(400, m.bpm)))).append("</bpm>\n");
    sb.append(" <volume>0.73</volume>\n");
    sb.append(" <metronomeVolume>0.5</metronomeVolume>\n");
    sb.append(" <name>").append(xml(name)).append("</name>\n");
    sb.append(" <author>Pulsekit</author>\n");
    sb.append(" <notes>Exported from Pulsekit.</notes>\n");
    sb.append(" <license>Unknown license</license>\n");
    sb.append(" <loopEnabled>false</loopEnabled>\n");
    sb.append(" <patternModeMode>true</patternModeMode>\n");
    sb.append(" <playbackTrackFilename></playbackTrackFilename>\n");
    sb.append(" <playbackTrackEnabled>false</playbackTrackEnabled>\n");
    sb.append(" <playbackTrackVolume>0</playbackTrackVolume>\n");
    sb.append(" <action_mode>0</action_mode>\n");
    sb.append(" <mode>song</mode>\n");
    sb.append(" <pan_law_type>RATIO_STRAIGHT_POLYGONAL</pan_law_type>\n");
    sb.append(" <pan_law_k_norm>1.33333</pan_law_k_norm>\n");
    sb.append(" <humanize_time>0</humanize_time>\n");
    sb.append(" <humanize_velocity>0</humanize_velocity>\n");
    sb.append(" <swing_factor>0</swing_factor>\n");
    sb.append(" <componentList>\n  <drumkitComponent>\n   <id>0</id>\n   <name>Main</name>\n   <volume>1</volume>\n  </drumkitComponent>\n </componentList>\n");
    sb.append(" <instrumentList>\n");
    for (int i = 0; i < KIT.length; i++) sb.append(instrumentXml(i, KIT[i]));
    sb.append(" </instrumentList>\n");
    sb.append(" <patternList>\n");
    for (Map.Entry<String, List<int[]>> e : patterns.entrySet()) {
      sb.append("  <pattern>\n");
      sb.append("   <name>").append(xml(e.getKey())).append("</name>\n");
      sb.append("   <category>Pulsekit</category>\n");
      sb.append("   <size>").append(bar).append("</size>\n");
      sb.append("   <denominator>").append(m.den).append("</denominator>\n");
      sb.append("   <info></info>\n");
      sb.append("   <noteList>\n");
      sb.append(noteList(e.getValue(), "    "));
      sb.append("   </noteList>\n");
      sb.append("  </pattern>\n");
    }
    sb.append(" </patternList>\n");
    sb.append(" <virtualPatternList></virtualPatternList>\n");
    sb.append(" <patternSequence>\n");
    for (String id : sequence) sb.append("  <group>\n   <patternID>").append(xml(id)).append("</patternID>\n  </group>\n");
    sb.append(" </patternSequence>\n");
    sb.append(" <ladspa></ladspa>\n");
    sb.append(" <BPMTimeLine></BPMTimeLine>\n");
    sb.append(" <timeLineTag></timeLineTag>\n");
    sb.append(" <automationPaths></automationPaths>\n");
    sb.append("</song>\n");
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static String instrumentXml(int id, String row) {
    String[] f = row.split("\\|", -1);
    StringBuilder sb = new StringBuilder();
    sb.append("  <instrument>\n");
    sb.append("   <id>").append(id).append("</id>\n");
    sb.append("   <name>").append(xml(f[0])).append("</name>\n");
    sb.append("   <drumkit>GMRockKit</drumkit>\n");
    sb.append("   <drumkitLookup>2</drumkitLookup>\n");
    sb.append("   <volume>").append(f[2]).append("</volume>\n");
    sb.append("   <isMuted>false</isMuted>\n   <isSoloed>false</isSoloed>\n");
    sb.append("   <pan_L>").append(f[3]).append("</pan_L>\n   <pan_R>").append(f[4]).append("</pan_R>\n");
    sb.append("   <gain>1</gain>\n   <applyVelocity>false</applyVelocity>\n   <filterActive>false</filterActive>\n");
    sb.append("   <filterCutoff>1</filterCutoff>\n   <filterResonance>0</filterResonance>\n");
    sb.append("   <FX1Level>0</FX1Level>\n   <FX2Level>0</FX2Level>\n   <FX3Level>0</FX3Level>\n   <FX4Level>0</FX4Level>\n");
    sb.append("   <Attack>0</Attack>\n   <Decay>0</Decay>\n   <Sustain>1</Sustain>\n   <Release>1000</Release>\n");
    sb.append("   <pitchOffset>0</pitchOffset>\n   <randomPitchFactor>0</randomPitchFactor>\n");
    sb.append("   <muteGroup>-1</muteGroup>\n   <isStopNote>false</isStopNote>\n   <sampleSelectionAlgo>VELOCITY</sampleSelectionAlgo>\n");
    sb.append("   <midiOutChannel>-1</midiOutChannel>\n   <midiOutNote>").append(f[1]).append("</midiOutNote>\n");
    sb.append("   <isHihat>-1</isHihat>\n   <lower_cc>0</lower_cc>\n   <higher_cc>127</higher_cc>\n");
    sb.append("   <instrumentComponent>\n    <component_id>0</component_id>\n    <gain>1</gain>\n");
    for (String layer : f[5].split(";")) {
      String[] l = layer.split(":");
      sb.append("    <layer>\n     <filename>").append(xml(l[0])).append("</filename>\n");
      sb.append("     <ismodified>false</ismodified>\n     <smode>forward</smode>\n     <startframe>0</startframe>\n");
      sb.append("     <loopframe>0</loopframe>\n     <loops>0</loops>\n     <endframe>0</endframe>\n");
      sb.append("     <userubber>0</userubber>\n     <rubberdivider>1</rubberdivider>\n     <rubberCsettings>4</rubberCsettings>\n");
      sb.append("     <rubberPitch>1</rubberPitch>\n     <min>").append(l[1]).append("</min>\n     <max>").append(l[2]).append("</max>\n");
      sb.append("     <gain>1</gain>\n     <pitch>0</pitch>\n    </layer>\n");
    }
    sb.append("   </instrumentComponent>\n");
    sb.append("  </instrument>\n");
    return sb.toString();
  }

  static String fmt(double v) {
    if (v == Math.rint(v)) return Long.toString((long) v);
    String s = String.format(Locale.ROOT, "%.4f", v);
    while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
    return s;
  }

  static String xml(String s) {
    return (s == null ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static int be32(byte[] d, int i) {
    if (i + 4 > d.length) return 0;
    return ((d[i] & 0xff) << 24) | ((d[i + 1] & 0xff) << 16) | ((d[i + 2] & 0xff) << 8) | (d[i + 3] & 0xff);
  }
}
