package pulsekit;

import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;

/** Drum and guitar level. Calls MainActivity directly so a drag is heard immediately. */
public final class MixLevels implements SeekBar.OnSeekBarChangeListener, View.OnTouchListener {
  private final MainActivity host;
  private final int kind;
  private final String src;

  public MixLevels(Object host, int kind, String src) {
    this.host = (MainActivity) host;
    this.kind = kind;
    this.src = src == null ? "" : src;
  }

  public boolean onTouch(View v, MotionEvent event) {
    if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
    return false;
  }

  public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
    if (!fromUser) return;
    this.host.setMixLevel(this.kind, progress);
  }

  public void onStartTrackingTouch(SeekBar bar) {}

  public void onStopTrackingTouch(SeekBar bar) {
    this.host.applyMixLevels(this.src);
  }
}
