package pulsekit;

/** Runs Analyze off the UI thread. Javassist cannot compile an anonymous class. */
public final class AnalyzeJob implements Runnable {
  private final Object host;

  public AnalyzeJob(Object host) {
    this.host = host;
  }

  public void run() {
    try {
      this.host.getClass().getMethod("runAnalyzeJob").invoke(this.host);
    } catch (Throwable ignored) {
      try {
        this.host.getClass().getMethod("finishAnalyzeJob").invoke(this.host);
      } catch (Throwable ignored2) {
        /* activity already gone */
      }
    }
  }
}
