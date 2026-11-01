package pulsekit;

import java.util.Locale;

/**
 * How far a long piece of DB work is (Send db to zip, Import db zip): its steps by name, the bytes
 * of the step done so far, the whole as a percent and the time left. The work's thread holds it
 * (NOW); the steps say where they are (at), the zip code counts its bytes (count); the screen
 * reads it (text, fraction) while the work runs.
 */
public final class WorkProgress {
  /** The work of this thread (null: none watched). */
  public static final ThreadLocal<WorkProgress> NOW = new ThreadLocal<WorkProgress>();

  private final int steps;
  private final long started = System.currentTimeMillis();
  private volatile int step;
  private volatile String name = "Starting";
  private volatile long done;
  private volatile long total;

  /** Work of about `steps` steps. */
  public WorkProgress(int steps) {
    this.steps = Math.max(1, steps);
  }

  /** The next step begins: its name and its bytes (0: not counted). */
  public static void at(String name, long total) {
    WorkProgress p = NOW.get();
    if (p == null) return;
    synchronized (p) {
      if (p.step < p.steps) p.step++;
      p.name = name;
      p.done = 0;
      p.total = Math.max(0, total);
    }
  }

  /** Bytes of the step done. */
  public static void count(long n) {
    WorkProgress p = NOW.get();
    if (p == null || n <= 0) return;
    synchronized (p) {
      p.done += n;
    }
  }

  /** The whole done, 0..1: the steps finished and the counted part of this one. */
  public synchronized double fraction() {
    if (this.step == 0) return 0;
    double in = this.total > 0 ? Math.min(1.0, this.done / (double) this.total) : 0;
    return Math.min(1.0, (this.step - 1 + in) / this.steps);
  }

  /** "Step 2 of 3: Zipping with the password · 3.1 MB of 7.7 MB". */
  public synchronized String stepText() {
    String s = "Step " + Math.max(1, this.step) + " of " + this.steps + ": " + this.name;
    if (this.total > 0) s += " · " + Playlist.sizeText(Math.min(this.done, this.total)) + " of " + Playlist.sizeText(this.total);
    return s;
  }

  /** "45% done, about 12 s left" (the time once it can be told). */
  public String percentText() {
    double f = this.fraction();
    long elapsed = System.currentTimeMillis() - this.started;
    String s = (int) Math.floor(f * 100) + "% done, " + (int) Math.ceil((1 - f) * 100) + "% left";
    if (f >= 0.05 && elapsed >= 1000 && f < 1) s += ", about " + seconds((long) (elapsed * (1 - f) / f)) + " to go";
    return s;
  }

  static String seconds(long ms) {
    long s = Math.max(1, (ms + 999) / 1000);
    return s < 60 ? s + " s" : String.format(Locale.ROOT, "%d min %02d s", s / 60, s % 60);
  }

  /** Counts what is read through it into the work of the thread reading. */
  public static java.io.InputStream counted(java.io.InputStream in) {
    if (NOW.get() == null) return in;
    return new java.io.FilterInputStream(in) {
      @Override
      public int read() throws java.io.IOException {
        int b = super.read();
        if (b >= 0) count(1);
        return b;
      }

      @Override
      public int read(byte[] b, int off, int len) throws java.io.IOException {
        int n = super.read(b, off, len);
        count(n);
        return n;
      }
    };
  }
}
