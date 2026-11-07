package pulsekit;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;

/**
 * DB/Prompts' Full screen preview and Full screen playback: a picture or a video on the whole black
 * screen (turned sideways, a wide one fills it). A tap plays or pauses the video; a long press (or
 * Back) closes it, the video's place handed back. A swipe (when it has a Step) shows the previous or
 * next picture or video full screen in its place.
 */
final class FullScreen {
    private FullScreen() {}

    /** Where a video was left when its full screen closed (ms). */
    interface Left {
        void at(int ms);
    }

    /** What a swipe does: the previous (-1) or next (1) one, full screen in this one's place. */
    interface Step {
        void go(int dir);
    }

    /** The full screen shown last (for the tests). */
    static Dialog last;
    static VideoView lastVideo;
    static ImageView lastImage;

    static void video(MainActivity app, String path, int ms, Left left) {
        video(app, path, ms, left, null);
    }

    /** The video at `path` from `ms`; `left` (may be null) gets where it was when it closes; `step` (may be null): what a swipe does. */
    static void video(final MainActivity app, String path, final int ms, final Left left, final Step step) {
        final boolean[] stepped = new boolean[1];
        final Swipe swipe = new Swipe(app);
        final Dialog d = new Dialog(app, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final VideoView big = new VideoView(app);
        big.setTag("full-screen-video");
        FrameLayout frame = new FrameLayout(app);
        frame.setBackgroundColor(android.graphics.Color.BLACK);
        frame.addView(big, new FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER));
        d.setContentView(frame);
        big.setOnPreparedListener(mp -> {
            big.seekTo(ms);
            big.start();
        });
        final GestureDetector gestures = new GestureDetector(app, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (big.isPlaying()) big.pause();
                else big.start();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                d.dismiss();
            }
        });
        View.OnTouchListener touch = (v, ev) -> {
            int dir = step == null ? 0 : swipe.feed(ev);
            if (dir != 0) {
                stepped[0] = true;
                d.dismiss();
                step.go(dir);
                return true;
            }
            return gestures.onTouchEvent(ev);
        };
        big.setOnTouchListener(touch);
        frame.setOnTouchListener(touch);
        d.setOnDismissListener(x -> {
            int at = 0;
            try {
                at = big.getCurrentPosition();
            } catch (Exception ignored) {
                // not started
            }
            big.stopPlayback();
            MediaBrowser.portraitAgain(app);
            MediaBrowser.screenOn(d.getWindow(), false);
            // Swiped on: the next one shows instead (this place is not handed back).
            if (left != null && !stepped[0]) left.at(at);
        });
        big.setVideoPath(path);
        last = d;
        lastVideo = big;
        lastImage = null;
        MediaBrowser.mayTurn(app);
        d.show();
        MediaBrowser.screenOn(d.getWindow(), true);
    }

    static void picture(MainActivity app, byte[] bytes) {
        picture(app, bytes, null);
    }

    /** The picture (its bytes) fitted to the whole screen; a tap or a long press closes it; `step` (may be null): what a swipe does. */
    static void picture(final MainActivity app, byte[] bytes, final Step step) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        // At most about 4096 pixels a side (a large picture ran the phone out of memory).
        while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize == 0 ? 1 : opts.inSampleSize) > 4096) opts.inSampleSize = opts.inSampleSize == 0 ? 2 : opts.inSampleSize * 2;
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        if (b == null) {
            app.setNow("The picture could not be read");
            return;
        }
        final Dialog d = new Dialog(app, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView image = new ImageView(app);
        image.setTag("full-screen-image");
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(android.graphics.Color.BLACK);
        image.setImageBitmap(b);
        image.setOnClickListener(v -> d.dismiss());
        image.setOnLongClickListener(v -> {
            d.dismiss();
            return true;
        });
        if (step != null) {
            // A swipe: the previous or the next one; a tap or a long press still closes it.
            final Swipe swipe = new Swipe(app);
            final GestureDetector gestures = new GestureDetector(app, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    d.dismiss();
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    d.dismiss();
                }
            });
            image.setOnTouchListener((v, ev) -> {
                int dir = swipe.feed(ev);
                if (dir != 0) {
                    d.dismiss();
                    step.go(dir);
                    return true;
                }
                return gestures.onTouchEvent(ev);
            });
        }
        d.setContentView(image);
        d.setOnDismissListener(x -> MediaBrowser.portraitAgain(app));
        last = d;
        lastImage = image;
        lastVideo = null;
        MediaBrowser.mayTurn(app);
        d.show();
    }
}
