package pulsekit;

/** Repeats the combined-mix clock. Javassist cannot compile an anonymous class. */
public final class MixClock implements Runnable {
  private final Object host;

  public MixClock(Object host) {
    this.host = host;
  }

  public void run() {
    try {
      this.host.getClass().getMethod("tickMixClock").invoke(this.host);
    } catch (Throwable ignored) {
      /* player already gone */
    }
  }
}
