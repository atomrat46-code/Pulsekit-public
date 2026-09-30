package pulsekit;

/** Applies a finished Analyze job on the UI thread. */
public final class AnalyzeDone implements Runnable {
  private final Object host;

  public AnalyzeDone(Object host) {
    this.host = host;
  }

  public void run() {
    try {
      this.host.getClass().getMethod("finishAnalyzeJob").invoke(this.host);
    } catch (Throwable ignored) {
      /* activity already gone */
    }
  }
}
