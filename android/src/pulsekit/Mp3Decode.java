package pulsekit;

import java.io.ByteArrayInputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/** MP3 decode via JLayer. Android loads the decoder tables from assets. */
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
    left = fit(left, frames, frames);
    if (right != null) right = fit(right, frames, frames);
    return new AudioIo.StereoPcm(left, right, sr);
  }

  private static float[] fit(float[] src, int n, int keep) {
    if (src != null && src.length == n) return src;
    float[] o = new float[Math.max(0, n)];
    if (src != null && keep > 0) System.arraycopy(src, 0, o, 0, Math.min(keep, Math.min(src.length, o.length)));
    return o;
  }
}
