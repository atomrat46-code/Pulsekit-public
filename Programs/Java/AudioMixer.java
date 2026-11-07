/**
 * AudioMixer: Pulsekit's own mixer (File > AudioMixer). Picking it in PyJav's Java list opens it: tracks
 * (a WAV, an MP3, or a drum MIDI played with the kit), each with its volume, gain, Mute and Solo, mixed
 * into one WAV (Mixdown). This file is only its place in the list; run alone, it says so.
 */
public final class AudioMixer {
  public static void main(String[] args) {
    System.out.println("AudioMixer is Pulsekit's own window: File > AudioMixer (or pick AudioMixer in PyJav's Java list).");
  }
}
