package pulsekit;

import android.view.View;

/** Opens the built-in Sogni client script in PyJav. */
public final class SogniClientClick implements View.OnClickListener {
  private final MainActivity host;

  public SogniClientClick(MainActivity host) {
    this.host = host;
  }

  @Override
  public void onClick(View v) {
    try {
      host.pyJav.pkLoadSogniClient(v);
    } catch (Exception ignored) {}
  }
}
