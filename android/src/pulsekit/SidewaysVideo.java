package pulsekit;

import android.app.Activity;
import android.app.Dialog;
import android.widget.FrameLayout;
import android.widget.VideoView;

/**
 * The phone turned sideways while a smaller video player shows (Video ready, the Prompts page's
 * preview): the video goes on full screen from where it is; upright again, that full screen closes
 * and the player is where it was left. While such a player shows, the screen may turn (as the
 * phone's auto-rotate says); the full screen players (the Media browser's, the playlist viewer)
 * let it turn themselves.
 */
final class SidewaysVideo {
    private SidewaysVideo() {}

    /** A smaller player: whether it shows now, and its video full screen (the dialog shown). */
    interface Small {
        boolean shown();

        Dialog wide();
    }

    private static final java.util.LinkedHashSet<Small> SMALL = new java.util.LinkedHashSet<Small>();
    private static java.lang.ref.WeakReference<Activity> where;
    /** The full screen opened by turning sideways (closed by turning upright), for the tests too. */
    static Dialog full;

    /** A smaller player shows: the screen may turn while it does. */
    static void opened(Activity a, Small s) {
        if (s == null || !SMALL.add(s)) return;
        where = new java.lang.ref.WeakReference<Activity>(a);
        MediaBrowser.mayTurn(a);
    }

    /** It is gone: upright again once no player lets the screen turn. */
    static void closed(Small s) {
        if (s == null || !SMALL.remove(s)) return;
        Activity a = where == null ? null : where.get();
        if (a != null) MediaBrowser.portraitAgain(a);
    }

    /** The screen turned (MainActivity's configuration changed). */
    static void turned(boolean sideways) {
        if (sideways) {
            if (full != null && full.isShowing()) return;
            Small pick = null;
            java.util.List<Small> gone = new java.util.ArrayList<Small>();
            for (Small s : SMALL) {
                if (s.shown()) pick = s;
                else gone.add(s);
            }
            // A player no longer shown (its page left): it no longer lets the screen turn.
            for (Small s : gone) closed(s);
            if (pick != null) full = pick.wide();
        } else if (full != null) {
            Dialog f = full;
            full = null;
            if (f.isShowing()) f.dismiss();
        }
    }

    /** The full screen player shown last by play (for the tests). */
    static VideoView lastPlayed;

    /**
     * A video file on the whole screen from `ms`, at `gain`: a tap plays or pauses it, a long press
     * (or Back) closes it; the small player `back` is then where this one was.
     */
    static Dialog play(Activity a, String path, int ms, float gain, final VideoView back) {
        final Dialog d = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final VideoView big = new VideoView(a);
        big.setTag("sideways-video");
        FrameLayout frame = new FrameLayout(a);
        frame.setBackgroundColor(0xff000000);
        frame.addView(big, new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER));
        big.setOnPreparedListener(mp -> {
            try {
                mp.setVolume(gain, gain);
            } catch (IllegalStateException ignored) {
                // closing
            }
            big.seekTo(ms);
            big.start();
        });
        final android.view.GestureDetector gestures = new android.view.GestureDetector(a, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(android.view.MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(android.view.MotionEvent e) {
                if (big.isPlaying()) big.pause();
                else big.start();
                return true;
            }

            @Override
            public void onLongPress(android.view.MotionEvent e) {
                d.dismiss();
            }
        });
        android.view.View.OnTouchListener touch = (v, ev) -> gestures.onTouchEvent(ev);
        big.setOnTouchListener(touch);
        frame.setOnTouchListener(touch);
        d.setContentView(frame);
        d.setOnDismissListener(x -> {
            int at = 0;
            try {
                at = big.getCurrentPosition();
            } catch (Exception ignored) {
                // not started
            }
            big.stopPlayback();
            MediaBrowser.screenOn(d.getWindow(), false);
            if (back != null && at > 0) {
                try {
                    back.seekTo(at);
                } catch (Exception ignored) {
                    // gone
                }
            }
        });
        big.setVideoPath(path);
        lastPlayed = big;
        d.show();
        MediaBrowser.screenOn(d.getWindow(), true);
        return d;
    }
}
