package pulsekit;

/** Runs vocal removal off the UI thread. Javassist cannot compile an anonymous class. */
public final class ComposeJob implements Runnable {
  private final Object host;
  private final boolean strip;

  public ComposeJob(Object host, boolean strip) {
    this.host = host;
    this.strip = strip;
  }

  public void run() {
    try {
      this.host.getClass().getMethod("runComposeJob", boolean.class).invoke(this.host, Boolean.valueOf(this.strip));
    } catch (Throwable ignored) {
      try {
        this.host.getClass().getMethod("finishComposeJob").invoke(this.host);
      } catch (Throwable ignored2) {
        /* activity already gone */
      }
    }
  }
}
