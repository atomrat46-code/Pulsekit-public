package pulsekit;

import android.content.Context;
import android.view.MotionEvent;

/**
 * A swipe across a full screen viewer, as its arrows: the finger drawn right to left is the next
 * file (+1), left to right the previous (-1). One finger, mostly sideways, at least 72 dp, within a
 * second; a pinch or a long press is none.
 */
final class Swipe {
    private float x, y;
    private long at;
    private boolean many;
    private final float min;

    Swipe(Context c) {
        this.min = 72 * c.getResources().getDisplayMetrics().density;
    }

    /** Fed each touch; at the finger's lift, -1, 1 for a swipe, else 0. */
    int feed(MotionEvent ev) {
        int act = ev.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            this.x = ev.getRawX();
            this.y = ev.getRawY();
            this.at = ev.getEventTime();
            this.many = false;
            return 0;
        }
        if (act == MotionEvent.ACTION_POINTER_DOWN) this.many = true;
        if (act != MotionEvent.ACTION_UP || this.many) return 0;
        return of(ev.getRawX() - this.x, ev.getRawY() - this.y, ev.getEventTime() - this.at, this.min);
    }

    /** The step a move of (dx, dy) in `ms` makes: -1, 1, or 0 when it is no swipe. */
    static int of(float dx, float dy, long ms, float min) {
        if (ms > 1000 || Math.abs(dx) < min || Math.abs(dx) < 2 * Math.abs(dy)) return 0;
        return dx < 0 ? 1 : -1;
    }
}
