package pulsekit;

/** Applies a finished vocal-removal job on the UI thread. */
public final class ComposeDone implements Runnable {
  private final Object host;

  public ComposeDone(Object host) {
    this.host = host;
  }

  public void run() {
    try {
      this.host.getClass().getMethod("finishComposeJob").invoke(this.host);
    } catch (Throwable ignored) {
      /* activity already gone */
    }
  }
}
