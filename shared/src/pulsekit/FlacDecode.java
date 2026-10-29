package pulsekit;

/**
 * A FLAC decoder (the whole format: fixed and LPC subframes, Rice residuals with escapes, wasted
 * bits, stereo decorrelation, 8 to 32 bits), for SFZ kits whose samples are .flac. The channels
 * are mixed to one, as Pulsekit's pads play them; maxSeconds stops early (a drum hit is short).
 */
public final class FlacDecode {
  private FlacDecode() {}

  public static boolean isFlac(byte[] d) {
    return d != null && d.length >= 4 && ((d[0] == 'f' && d[1] == 'L' && d[2] == 'a' && d[3] == 'C') || (d[0] == 'I' && d[1] == 'D' && d[2] == '3'));
  }

  /** The samples (mono, -1..1) and the rate; at most maxSeconds of them (0: all). */
  public static AudioIo.Pcm decode(byte[] d, double maxSeconds) {
    int at = 0;
    if (d.length >= 10 && d[0] == 'I' && d[1] == 'D' && d[2] == '3') {
      // An ID3v2 tag before the stream.
      at = 10 + (((d[6] & 0x7f) << 21) | ((d[7] & 0x7f) << 14) | ((d[8] & 0x7f) << 7) | (d[9] & 0x7f));
    }
    if (at + 4 > d.length || d[at] != 'f' || d[at + 1] != 'L' || d[at + 2] != 'a' || d[at + 3] != 'C') throw new IllegalArgumentException("Not a FLAC file");
    at += 4;
    int rate = 0, channels = 0, bps = 0;
    long total = 0;
    boolean last = false;
    while (!last && at + 4 <= d.length) {
      last = (d[at] & 0x80) != 0;
      int type = d[at] & 0x7f;
      int len = ((d[at + 1] & 0xff) << 16) | ((d[at + 2] & 0xff) << 8) | (d[at + 3] & 0xff);
      int body = at + 4;
      if (type == 0 && body + 18 <= d.length) {
        rate = ((d[body + 10] & 0xff) << 12) | ((d[body + 11] & 0xff) << 4) | ((d[body + 12] & 0xff) >> 4);
        channels = ((d[body + 12] >> 1) & 7) + 1;
        bps = (((d[body + 12] & 1) << 4) | ((d[body + 13] & 0xff) >> 4)) + 1;
        total = ((long) (d[body + 13] & 0x0f) << 32) | ((long) (d[body + 14] & 0xff) << 24) | ((d[body + 15] & 0xff) << 16) | ((d[body + 16] & 0xff) << 8) | (d[body + 17] & 0xff);
      }
      at = body + len;
    }
    if (rate <= 0 || channels <= 0) throw new IllegalArgumentException("FLAC has no stream info");
    long want = total > 0 ? total : Long.MAX_VALUE;
    if (maxSeconds > 0) want = Math.min(want, (long) (maxSeconds * rate));
    int cap = (int) Math.min(want == Long.MAX_VALUE ? rate * 60L * 10 : want, Integer.MAX_VALUE - 8);
    float[] out = new float[Math.min(cap, 1 << 20)];
    int got = 0;
    Bits b = new Bits(d, at);
    try {
      while (got < cap && b.pos + 2 <= d.length) {
        if (!b.sync()) break;
        int[][] block = frame(b, channels, bps);
        if (block == null) break;
        int n = block[0].length;
        int frameBits = b.lastBps;
        float scale = 1f / (1L << (frameBits - 1)) / block.length;
        if (got + n > out.length) out = java.util.Arrays.copyOf(out, Math.max(got + n, Math.min(cap, out.length * 2)));
        for (int i = 0; i < n && got < cap; i++) {
          long acc = 0;
          for (int[] ch : block) acc += ch[i];
          out[got++] = acc * scale;
        }
      }
    } catch (RuntimeException ended) {
      // A cut or damaged file: what was decoded so far.
      if (got == 0) throw new IllegalArgumentException("FLAC could not be decoded: " + ended.getMessage());
    }
    return new AudioIo.Pcm(got == out.length ? out : java.util.Arrays.copyOf(out, got), rate);
  }

  private static final int[] BLOCK = {0, 192, 576, 1152, 2304, 4608, -1, -2, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768};
  private static final int[] SIZE = {0, 8, 12, 0, 16, 20, 24, 32};

  /** One frame: its channels' samples (decorrelated). */
  private static int[][] frame(Bits b, int streamChannels, int streamBps) {
    b.read(14);
    b.read(1);
    b.read(1);
    int bsCode = b.read(4);
    int srCode = b.read(4);
    int chCode = b.read(4);
    int ssCode = b.read(3);
    b.read(1);
    // The frame or sample number, UTF-8 coded.
    int first = b.read(8);
    int more = 0;
    for (int m = 0x80; (first & m) != 0 && m > 1; m >>= 1) more++;
    for (int i = 0; i < Math.max(0, more - 1); i++) b.read(8);
    int size = BLOCK[bsCode];
    if (size == -1) size = b.read(8) + 1;
    else if (size == -2) size = b.read(16) + 1;
    if (srCode == 12) b.read(8);
    else if (srCode == 13 || srCode == 14) b.read(16);
    b.read(8);
    if (size <= 0) return null;
    int bps = ssCode == 0 ? streamBps : SIZE[ssCode];
    if (bps == 0) bps = streamBps;
    b.lastBps = bps;
    int channels = chCode < 8 ? chCode + 1 : 2;
    int[][] ch = new int[channels][];
    for (int c = 0; c < channels; c++) {
      boolean side = (chCode == 8 && c == 1) || (chCode == 9 && c == 0) || (chCode == 10 && c == 1);
      ch[c] = subframe(b, size, bps + (side ? 1 : 0));
    }
    b.align();
    b.read(16);
    if (chCode == 8) {
      for (int i = 0; i < size; i++) ch[1][i] = ch[0][i] - ch[1][i];
    } else if (chCode == 9) {
      for (int i = 0; i < size; i++) ch[0][i] = ch[0][i] + ch[1][i];
    } else if (chCode == 10) {
      for (int i = 0; i < size; i++) {
        long mid = ((long) ch[0][i] << 1) | (ch[1][i] & 1);
        long sideV = ch[1][i];
        ch[0][i] = (int) ((mid + sideV) >> 1);
        ch[1][i] = (int) ((mid - sideV) >> 1);
      }
    }
    return ch;
  }

  private static int[] subframe(Bits b, int n, int bps) {
    b.read(1);
    int type = b.read(6);
    int wasted = 0;
    if (b.read(1) == 1) wasted = b.unary() + 1;
    bps -= wasted;
    int[] s = new int[n];
    if (type == 0) {
      int v = b.signed(bps);
      java.util.Arrays.fill(s, v);
    } else if (type == 1) {
      for (int i = 0; i < n; i++) s[i] = b.signed(bps);
    } else if (type >= 8 && type <= 12) {
      int order = type - 8;
      for (int i = 0; i < order; i++) s[i] = b.signed(bps);
      residual(b, s, order);
      for (int i = order; i < n; i++) {
        long p;
        switch (order) {
          case 0: p = 0; break;
          case 1: p = s[i - 1]; break;
          case 2: p = 2L * s[i - 1] - s[i - 2]; break;
          case 3: p = 3L * s[i - 1] - 3L * s[i - 2] + s[i - 3]; break;
          default: p = 4L * s[i - 1] - 6L * s[i - 2] + 4L * s[i - 3] - s[i - 4]; break;
        }
        s[i] = (int) (s[i] + p);
      }
    } else if (type >= 32) {
      int order = type - 31;
      for (int i = 0; i < order; i++) s[i] = b.signed(bps);
      int precision = b.read(4) + 1;
      int shift = b.signed(5);
      int[] coef = new int[order];
      for (int i = 0; i < order; i++) coef[i] = b.signed(precision);
      residual(b, s, order);
      for (int i = order; i < n; i++) {
        long sum = 0;
        for (int j = 0; j < order; j++) sum += (long) coef[j] * s[i - 1 - j];
        s[i] = (int) (s[i] + (shift >= 0 ? sum >> shift : sum << -shift));
      }
    } else {
      throw new IllegalArgumentException("reserved subframe type " + type);
    }
    if (wasted > 0) for (int i = 0; i < n; i++) s[i] <<= wasted;
    return s;
  }

  /** The residual after the warm-up samples, into s (the prediction is added after). */
  private static void residual(Bits b, int[] s, int order) {
    int method = b.read(2);
    int paramBits = method == 0 ? 4 : 5;
    int escape = method == 0 ? 15 : 31;
    int partOrder = b.read(4);
    int parts = 1 << partOrder;
    int per = s.length >> partOrder;
    int i = order;
    for (int p = 0; p < parts; p++) {
      int count = p == 0 ? per - order : per;
      int k = b.read(paramBits);
      if (k == escape) {
        int raw = b.read(5);
        for (int j = 0; j < count; j++) s[i++] = raw == 0 ? 0 : b.signed(raw);
      } else {
        for (int j = 0; j < count; j++) {
          long q = b.unary();
          long v = (q << k) | (k == 0 ? 0 : b.read(k));
          s[i++] = (int) ((v >>> 1) ^ -(v & 1));
        }
      }
    }
  }

  /** Big-endian bits over the bytes. */
  private static final class Bits {
    final byte[] d;
    int pos;
    int bit;
    int lastBps;

    Bits(byte[] d, int pos) {
      this.d = d;
      this.pos = pos;
    }

    int read(int n) {
      int v = 0;
      for (int i = 0; i < n; i++) {
        if (this.pos >= this.d.length) throw new IllegalStateException("end of data");
        v = (v << 1) | ((this.d[this.pos] >> (7 - this.bit)) & 1);
        if (++this.bit == 8) {
          this.bit = 0;
          this.pos++;
        }
      }
      return v;
    }

    int signed(int n) {
      if (n <= 0) return 0;
      if (n > 31) {
        long v = ((long) this.read(n - 31) << 31) | (this.read(31) & 0x7fffffffL);
        return (int) ((v << (64 - n)) >> (64 - n));
      }
      int v = this.read(n);
      return (v << (32 - n)) >> (32 - n);
    }

    /** Zeros up to a one: their count. */
    int unary() {
      int n = 0;
      while (true) {
        if (this.pos >= this.d.length) throw new IllegalStateException("end of data");
        if (this.bit == 0 && this.d[this.pos] == 0) {
          n += 8;
          this.pos++;
          continue;
        }
        if (this.read(1) == 1) return n;
        n++;
      }
    }

    void align() {
      if (this.bit != 0) {
        this.bit = 0;
        this.pos++;
      }
    }

    /** At the next frame's sync code (0xFFF8 or 0xFFF9). */
    boolean sync() {
      this.align();
      while (this.pos + 1 < this.d.length) {
        if ((this.d[this.pos] & 0xff) == 0xff && ((this.d[this.pos + 1] & 0xff) >> 1) == 0x7c) return true;
        this.pos++;
      }
      return false;
    }
  }
}
