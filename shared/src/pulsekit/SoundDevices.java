package pulsekit;

import java.util.ArrayList;
import java.util.List;

/**
 * File > Sound devices: what each app found (audio outputs and inputs, MIDI outputs and inputs,
 * drivers, USB devices), the devices Pulsekit uses (General settings keep them: its sound output,
 * the drum MIDI output; none: the system's default and Pulsekit's own synth), and the report that
 * Copy report copies and the logs keep (Logs' Sound devices).
 */
public final class SoundDevices {
  private SoundDevices() {}

  public static final String OUT = "Audio outputs";
  public static final String IN = "Audio inputs";
  public static final String MIDI_OUT = "MIDI outputs";
  public static final String MIDI_IN = "MIDI inputs";
  public static final String PORTS = "Volume ports";
  public static final String DRIVERS = "Drivers";
  public static final String USB = "USB devices";
  /** The groups in the order they are shown. */
  public static final String[] KINDS = {OUT, IN, MIDI_OUT, MIDI_IN, PORTS, DRIVERS, USB};
  public static final String DEFAULT_OUT = "System default output";
  public static final String OWN_SYNTH = "Pulsekit's own synth";
  /** The driver of a MIDI output that is the computer's software synth (Pulsekit's own). */
  public static final String SYNTH = "software synth";

  /** One device: its group, name, driver (or kind of connection) and what else is known of it. */
  public static final class Device {
    public final String kind;
    public final String name;
    public final String driver;
    public final String details;

    public Device(String kind, String name, String driver, String details) {
      this.kind = kind;
      this.name = name == null ? "" : name;
      this.driver = driver == null ? "" : driver;
      this.details = details == null ? "" : details;
    }

    /** The list's line: the name, its driver, and whether Pulsekit uses it. */
    public String label() {
      return this.name + (this.driver.isEmpty() ? "" : "  (" + this.driver + ")") + (used(this) ? "  \u2714 used by Pulsekit" : "");
    }

    /** The key the settings keep for it: its name, and for Android its kind of connection (ids change). */
    public String key() {
      return this.name + (this.driver.isEmpty() ? "" : " | " + this.driver);
    }
  }

  /** Whether Pulsekit uses the device (its sound output, or the drum MIDI output). */
  public static boolean used(Device d) {
    if (OUT.equals(d.kind)) return d.key().equals(AppSettings.soundOut());
    if (MIDI_OUT.equals(d.kind)) return AppSettings.midiOut().isEmpty() ? SYNTH.equals(d.driver) : d.key().equals(AppSettings.midiOut());
    return false;
  }

  /** Whether the device can be picked for Pulsekit (an output: audio or MIDI). */
  public static boolean usable(Device d) {
    return OUT.equals(d.kind) || MIDI_OUT.equals(d.kind);
  }

  /** The kept choice's name for the summary: "System default output" when none. */
  public static String outNow() {
    String v = AppSettings.soundOut();
    return v.isEmpty() ? DEFAULT_OUT : v;
  }

  public static String midiNow() {
    String v = AppSettings.midiOut();
    return v.isEmpty() ? OWN_SYNTH : v;
  }

  /** The devices of a group, in the order found. */
  public static List<Device> of(List<Device> all, String kind) {
    List<Device> out = new ArrayList<Device>();
    for (Device d : all) if (d.kind.equals(kind)) out.add(d);
    return out;
  }

  /** The kept choice, when it is not among the devices found now (unplugged): Pulsekit uses the default then. */
  public static String missing(List<Device> all) {
    StringBuilder sb = new StringBuilder();
    if (!AppSettings.soundOut().isEmpty() && !has(all, OUT, AppSettings.soundOut())) sb.append("Sound output ").append(AppSettings.soundOut()).append(" is not connected: the system default is used.\n");
    if (!AppSettings.midiOut().isEmpty() && !has(all, MIDI_OUT, AppSettings.midiOut())) sb.append("MIDI output ").append(AppSettings.midiOut()).append(" is not connected: Pulsekit's own synth plays.\n");
    return sb.toString();
  }

  private static boolean has(List<Device> all, String kind, String key) {
    for (Device d : of(all, kind)) if (d.key().equals(key)) return true;
    return false;
  }

  /** Everything found, as text (Copy report, the log). */
  public static String report(String header, List<Device> all) {
    StringBuilder sb = new StringBuilder(header == null ? "" : header.trim() + "\n");
    sb.append("Pulsekit's sound output: ").append(outNow()).append("\nDrum MIDI output: ").append(midiNow()).append('\n');
    sb.append(missing(all));
    for (String kind : KINDS) {
      List<Device> some = of(all, kind);
      sb.append("\n== ").append(kind).append(" (").append(some.size()).append(")\n");
      for (Device d : some) {
        sb.append("* ").append(d.name).append(d.driver.isEmpty() ? "" : "  [" + d.driver + "]").append(used(d) ? "  (used by Pulsekit)" : "").append('\n');
        for (String line : d.details.split("\n")) if (line.trim().length() > 0) sb.append("    ").append(line).append('\n');
      }
    }
    return sb.toString();
  }

  /** Into the logs (Logging on): what was found or done. */
  public static void log(String what, String text) {
    Logs.write(Logs.SOUND, what, text);
  }
}
