package pulsekit;

/** Runs Isolation off the UI thread. Javassist cannot compile an anonymous class. */
public final class IsolateJob implements Runnable {
  private final Object host;

  public IsolateJob(Object host) {
    this.host = host;
  }

  public void run() {
    try {
      this.host.getClass().getMethod("runIsolateJob").invoke(this.host);
    } catch (Throwable ignored) {
      try {
        this.host.getClass().getMethod("finishIsolateJob").invoke(this.host);
      } catch (Throwable ignored2) {
        /* activity already gone */
      }
    }
  }
}
