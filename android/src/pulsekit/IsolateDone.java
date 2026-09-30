package pulsekit;

/** Applies a finished Isolation job on the UI thread. */
public final class IsolateDone implements Runnable {
  private final Object host;

  public IsolateDone(Object host) {
    this.host = host;
  }

  public void run() {
    try {
      this.host.getClass().getMethod("finishIsolateJob").invoke(this.host);
    } catch (Throwable ignored) {
      /* activity already gone */
    }
  }
}
