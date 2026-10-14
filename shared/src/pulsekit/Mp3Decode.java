package pulsekit;

import java.io.ByteArrayInputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * MP3 decode via JLayer. Android loads the decoder tables from assets. A gapless tag (LAME's,
 * as Wav2Mp3 and ffmpeg write it) trims the encoder's delay and padding, so the audio starts and
 * ends where the WAV did.
 */
public final class Mp3Decode {
  private Mp3Decode() {}

  public static AudioIo.Pcm parse(byte[] d) throws Exception {
    AudioIo.StereoPcm st = parseStereo(d);
    if (st.right == null || st.left == null) {
      return new AudioIo.Pcm(st.left == null ? new float[0] : st.left, st.sr);
    }
    int n = Math.min(st.left.length, st.right.length);
    for (int i = 0; i < n; i++) st.left[i] = (st.left[i] + st.right[i]) * 0.5f;
    if (n == st.left.length) return new AudioIo.Pcm(st.left, st.sr);
    float[] m = new float[n];
    System.arraycopy(st.left, 0, m, 0, n);
    return new AudioIo.Pcm(m, st.sr);
  }

  public static AudioIo.StereoPcm parseStereo(byte[] d) throws Exception {
    if (d == null || d.length < 16) throw new IllegalArgumentException("Not an MP3 file");
    Bitstream bs = new Bitstream(new ByteArrayInputStream(d));
    Decoder dec = new Decoder();
    int sr = 44100;
    float[] left = new float[4096];
    float[] right = null;
    int frames = 0;
    try {
      Header h;
      int decoded = 0;
      while ((h = bs.readFrame()) != null && decoded < 24000) {
        sr = h.frequency();
        int limit = Math.max(1, sr) * 60 * 15;
        SampleBuffer buf = (SampleBuffer) dec.decodeFrame(h, bs);
        int nch = Math.max(1, buf.getChannelCount());
        short[] s = buf.getBuffer();
        int n = buf.getBufferLength();
        int got = n / nch;
        if (frames + got > limit) got = limit - frames;
        if (got > 0) {
          if (right == null && nch >= 2) right = new float[left.length];
          int need = frames + got;
          if (need > left.length) {
            int cap = Math.min(limit, Math.max(need, left.length * 2));
            left = fit(left, cap, frames);
            if (right != null) right = fit(right, cap, frames);
          }
          for (int i = 0; i < got; i++) {
            left[frames + i] = s[i * nch] / 32768f;
            if (right != null && nch >= 2) right[frames + i] = s[i * nch + 1] / 32768f;
          }
          frames += got;
        }
        bs.closeFrame();
        decoded++;
        if (frames >= limit) break;
      }
    } finally {
      try {
        bs.close();
      } catch (Exception ignored) {
      }
    }
    if (frames < sr / 2) throw new IllegalArgumentException("Could not decode that MP3");
    // Gapless (a LAME / Lavc tag in an Info or Xing frame): the tag frame's silence, the encoder's
    // delay and the decoder's 529 samples left out at the start, and the padding at the end.
    int[] gap = gapless(d);
    if (gap != null) {
      int start = gap[0] + gap[1] + 529;
      int length = frames - gap[0] - gap[1] - gap[2];
      if (start < frames && length > 0) {
        int end = Math.min(frames, start + length);
        left = slice(left, start, end);
        if (right != null) right = slice(right, start, end);
        return new AudioIo.StereoPcm(left, right, sr);
      }
    }
    left = fit(left, frames, frames);
    if (right != null) right = fit(right, frames, frames);
    return new AudioIo.StereoPcm(left, right, sr);
  }

  private static float[] slice(float[] src, int from, int to) {
    float[] o = new float[Math.max(0, to - from)];
    System.arraycopy(src, from, o, 0, o.length);
    return o;
  }

  /**
   * The first frame's gapless numbers when it is an Info / Xing frame with a LAME (or Lavc) tag:
   * {samples in that frame, encoder delay, padding}; null otherwise.
   */
  static int[] gapless(byte[] d) {
    int at = 0;
    // An ID3v2 tag first: its size is syncsafe.
    if (d.length > 10 && d[0] == 'I' && d[1] == 'D' && d[2] == '3') {
      at = 10 + ((d[6] & 0x7f) << 21 | (d[7] & 0x7f) << 14 | (d[8] & 0x7f) << 7 | (d[9] & 0x7f));
      if ((d[5] & 0x10) != 0) at += 10;
    }
    for (int limit = Math.min(d.length - 4, at + 8192); at < limit; at++) {
      if ((d[at] & 0xff) == 0xff && (d[at + 1] & 0xe0) == 0xe0) break;
    }
    if (at + 4 > d.length || (d[at] & 0xff) != 0xff) return null;
    int version = (d[at + 1] >> 3) & 3; // 3 MPEG-1, 2 MPEG-2, 0 MPEG-2.5
    int layer = (d[at + 1] >> 1) & 3; // 1 layer III
    if (layer != 1) return null;
    boolean mono = ((d[at + 3] >> 6) & 3) == 3;
    int side = version == 3 ? (mono ? 17 : 32) : (mono ? 9 : 17);
    int x = at + 4 + side;
    if (x + 8 > d.length) return null;
    String id = new String(d, x, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
    if (!id.equals("Info") && !id.equals("Xing")) return null;
    int flags = (d[x + 7] & 0xff) | (d[x + 6] & 0xff) << 8;
    int lame = x + 8 + ((flags & 1) != 0 ? 4 : 0) + ((flags & 2) != 0 ? 4 : 0) + ((flags & 4) != 0 ? 100 : 0) + ((flags & 8) != 0 ? 4 : 0);
    if (lame + 24 > d.length) return null;
    String enc = new String(d, lame, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
    if (!enc.equals("LAME") && !enc.equals("Lavc") && !enc.equals("Lavf")) return null;
    int delay = (d[lame + 21] & 0xff) << 4 | (d[lame + 22] & 0xff) >> 4;
    int padding = (d[lame + 22] & 0x0f) << 8 | (d[lame + 23] & 0xff);
    return new int[] {version == 3 ? 1152 : 576, delay, padding};
  }

  private static float[] fit(float[] src, int n, int keep) {
    if (src != null && src.length == n) return src;
    float[] o = new float[Math.max(0, n)];
    if (src != null && keep > 0) System.arraycopy(src, 0, o, 0, Math.min(keep, Math.min(src.length, o.length)));
    return o;
  }
}
