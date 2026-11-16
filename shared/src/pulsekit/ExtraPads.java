package pulsekit;

/**
 * The Pads page's second row: sounds the 12 drum tracks have not got, played by hand (not written into
 * patterns or songs): the pedal hat (44), a semi-open hat (the open hat let ring a little), crash 2 (57),
 * china (52), splash (55) and the ride bell (53), and the crash's and the ride's chokes (a ringing cymbal
 * stopped). A SoundFont kit's own samples for them when it has them; else made from the pads' own sounds.
 */
public final class ExtraPads {
  private ExtraPads() {}

  public static final String[] ID = {"ped", "semi", "cr2", "chn", "spl", "bel", "chk", "rchk"};
  public static final String[] SHORT = {"PED", "SEMI", "CR2", "CHN", "SPL", "BEL", "CHK", "RCHK"};
  public static final String[] LABEL = {"Pedal hat", "Semi-open hat", "Crash 2", "China", "Splash", "Ride bell", "Crash choke", "Ride choke"};
  /** The MIDI note each plays (a MIDI output or the synth gets it); -1 for a choke. The semi-open hat is the open hat's. */
  public static final int[] NOTES = {44, 46, 57, 52, 55, 53, -1, -1};
  /** How long the semi-open hat rings before it is closed (seconds). */
  public static final double SEMI_OPEN = 0.35;
  /** The notes a choke stops: the crash's (crash, crash 2, china, splash) and the ride's (ride, ride bell, ride 2). */
  public static final int[] CRASH_NOTES = {49, 57, 52, 55};
  public static final int[] RIDE_NOTES = {51, 53, 59};

  public static boolean isChoke(int i) {
    return NOTES[i] < 0;
  }

  /** Which pad's choke: the crash's (true) or the ride's. */
  public static boolean crashChoke(int i) {
    return "chk".equals(ID[i]);
  }

  /** The extra pads a choke stops (crash 2, china, splash; or the ride bell). */
  public static int[] chokedExtras(int i) {
    return crashChoke(i) ? new int[] {2, 3, 4} : new int[] {5};
  }

  /**
   * The extra pads' sounds at AudioIo.SR: the SoundFont's own for their notes (`sf2`, null for none), else
   * made from the pads' 12 sounds (`main`, Engine's track order): the pedal hat a soft, short closed hat,
   * crash 2 and china the crash, the splash a short crash, the bell a short ride. The semi-open hat is the
   * open hat cut at SEMI_OPEN with a fade. Chokes have none.
   */
  public static short[][] voices(byte[] sf2, short[][] main) {
    int sr = AudioIo.SR;
    int[] caps = {sr / 3, sr, sr * 2, sr * 2, sr, sr * 2, 0, 0};
    short[][] out = sf2 == null ? new short[ID.length][] : AudioIo.sf2Notes(sf2, NOTES, caps);
    short[] chh = track(main, "chh"), ohh = track(main, "ohh"), crash = track(main, "crash"), ride = track(main, "ride");
    if (out[0] == null) out[0] = shaped(chh, sr / 6, 0.7f);
    // The semi-open hat: the open hat closed after SEMI_OPEN (a soundfont's own 46 first).
    short[] open = sf2 != null ? AudioIo.sf2Notes(sf2, new int[] {46}, new int[] {sr * 2})[0] : null;
    out[1] = shaped(open != null ? open : ohh, (int) (sr * SEMI_OPEN), 1f);
    if (out[2] == null) out[2] = shaped(crash, sr * 2, 0.9f);
    if (out[3] == null) out[3] = shaped(crash, sr * 2, 1f);
    if (out[4] == null) out[4] = shaped(crash, sr * 3 / 5, 0.85f);
    if (out[5] == null) out[5] = shaped(ride, sr / 2, 1f);
    out[6] = null;
    out[7] = null;
    return out;
  }

  static short[] track(short[][] main, String id) {
    int t = Engine.track(id);
    return main == null || t < 0 || t >= main.length ? null : main[t];
  }

  /** A sound cut at `max` frames (faded over its last 40 ms when cut) at `gain`. */
  static short[] shaped(short[] src, int max, float gain) {
    if (src == null) return null;
    int len = Math.min(src.length, max);
    int fade = src.length > max ? Math.min(len / 3, AudioIo.SR / 25) : 0;
    short[] out = new short[len];
    for (int i = 0; i < len; i++) {
      float g = gain * (fade > 0 && i >= len - fade ? (len - i) / (float) fade : 1f);
      out[i] = (short) Math.max(-32767, Math.min(32767, Math.round(src[i] * g)));
    }
    return out;
  }
}
