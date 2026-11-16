package pulsekit;

/**
 * The drum tracks added after the first 12 (Engine.BASE_TRACKS): the pedal hat (44), the semi-open hat
 * (the open hat's 46 with the pedal half down), crash 2 (57), china (52), splash (55), the ride bell (53),
 * and the crash's and the ride's chokes (a ringing cymbal grabbed: aftertouch on 49 or 51, no sound).
 * Their sounds: a kit's own when it has them, else made from its hats and cymbals here.
 */
public final class ExtraPads {
  private ExtraPads() {}

  /** How long the semi-open hat rings before it is closed (seconds). */
  public static final double SEMI_OPEN = 0.35;

  /**
   * Fills the new tracks' sounds a kit has not got (`v` in Engine's track order, at AudioIo.SR): the pedal
   * hat a soft, short closed hat, crash 2 and china the crash, the splash a short crash, the bell a short
   * ride. The semi-open hat is the open hat cut at SEMI_OPEN with a fade. The chokes have none.
   */
  public static void complete(short[][] v) {
    if (v == null || v.length < Engine.TRACK_ID.length) return;
    int sr = AudioIo.SR;
    short[] chh = get(v, "chh"), ohh = get(v, "ohh"), crash = get(v, "crash"), ride = get(v, "ride");
    fill(v, "ped", shaped(chh, sr / 6, 0.7f));
    fill(v, "cr2", shaped(crash, sr * 2, 0.9f));
    fill(v, "chn", shaped(crash, sr * 2, 1f));
    fill(v, "spl", shaped(crash, sr * 3 / 5, 0.85f));
    fill(v, "bel", shaped(ride, sr / 2, 1f));
    int semi = Engine.track("semi");
    short[] open = v[semi] != null ? v[semi] : ohh;
    if (open != null && open.length > (int) (sr * SEMI_OPEN)) v[semi] = shaped(open, (int) (sr * SEMI_OPEN), 1f);
    else if (v[semi] == null) v[semi] = open;
    v[Engine.track("chk")] = null;
    v[Engine.track("rchk")] = null;
  }

  static short[] get(short[][] v, String id) {
    int t = Engine.track(id);
    return v == null || t < 0 || t >= v.length ? null : v[t];
  }

  static void fill(short[][] v, String id, short[] s) {
    int t = Engine.track(id);
    if (v[t] == null) v[t] = s;
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
