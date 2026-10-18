package pulsekit;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.ArrayList;
import java.util.List;

/**
 * After a run that made a video (SogniVideo's clip, a SogniChat tool result): a dialog plays it,
 * with Play/Pause, Stop (back to the start), Mute and a volume slider. The file is already kept
 * (Downloads or the program files folder); the dialog plays a copy in the app's cache, which is
 * replaced by the next video.
 */
final class VideoOffer {
    /** The player's state: the last dialog's, for the tests too. */
    static final class Player {
        VideoView view;
        MediaPlayer media;
        boolean playing;
        boolean muted;
        int volume = 100;
        TextView play;
        TextView mute;
        TextView level;
        /** "00:12 / 00:30": where the clip is, while the dialog shows. */
        TextView clock;
        boolean open;

        void tick() {
            if (!open || clock == null || view == null) return;
            int at = 0, all = 0;
            try {
                at = view.getCurrentPosition();
                all = view.getDuration();
            } catch (Exception ignored) {
                // not ready yet
            }
            clock.setText(FileSets.mixTime(at) + " / " + FileSets.mixTime(Math.max(0, all)));
            clock.postDelayed(this::tick, 250);
        }

        /** The dialog's window and the app's: the screen is kept on while it plays. */
        android.view.Window window;
        android.view.Window appWindow;

        void awake() {
            MediaBrowser.screenOn(window, playing);
            MediaBrowser.screenOn(appWindow, playing);
        }

        /** The volume MediaPlayer is given: none when muted, else the slider's share. */
        float gain() {
            return muted ? 0f : volume / 100f;
        }

        void applyVolume() {
            if (media != null) {
                try {
                    media.setVolume(gain(), gain());
                } catch (IllegalStateException ignored) {
                    // Released: the dialog is closing.
                }
            }
            if (level != null) level.setText(muted ? "muted" : volume + "%");
        }

        void playPause() {
            if (playing) {
                view.pause();
                playing = false;
            } else {
                view.start();
                playing = true;
            }
            play.setText(playing ? "Pause" : "Play");
            awake();
        }

        void stop() {
            view.pause();
            view.seekTo(0);
            playing = false;
            play.setText("Play");
            awake();
        }

        /** The long press's menu (for the tests). */
        AlertDialog menu;
        /** Where the last extracted frame was saved (for the tests). */
        String extracted;

        /** Long press: paused, then Extract frame. */
        void frameMenu(final MainActivity app, final String path, final String clipName) {
            if (playing) playPause();
            final int ms = Math.max(0, view.getCurrentPosition());
            menu = new AlertDialog.Builder(app)
                .setTitle("Frame at " + FileSets.mixTime(ms))
                .setItems(new CharSequence[] {"Extract frame"}, (d, w) -> extractFrame(app, path, clipName, ms))
                .setNegativeButton("Cancel", null)
                .show();
        }

        /**
         * The frame at `ms` as a PNG in Downloads' SC folder (clip-0m12s345.png), then shown in
         * Picture ready (Process as a reference...). Read and written off the main thread.
         */
        void extractFrame(final MainActivity app, final String path, final String clipName, final int ms) {
            app.setNow("Extracting the frame at " + FileSets.mixTime(ms) + "\u2026");
            final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            new Thread(() -> {
                String said;
                byte[] png = null;
                String name = MediaDir.frameName(clipName, ms);
                android.media.MediaMetadataRetriever media = new android.media.MediaMetadataRetriever();
                try {
                    media.setDataSource(path);
                    android.graphics.Bitmap frame = media.getFrameAtTime(ms * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame == null) frame = media.getFrameAtTime(ms * 1000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame == null) throw new java.io.IOException("the phone gave no picture at " + FileSets.mixTime(ms));
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
                    png = out.toByteArray();
                    String where = MediaBrowser.saveInto(app, new String[] {"", MediaBrowser.DOWNLOADS}, MediaDir.FRAMES_DIR, name, "image/png", png);
                    extracted = where;
                    said = "Saved the frame as " + where;
                } catch (Throwable ex) {
                    said = "Could not extract the frame: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                } finally {
                    try {
                        media.release();
                    } catch (Exception ignored) {}
                }
                final String shown = said;
                final byte[] made = png;
                main.post(() -> {
                    app.setNow(shown);
                    android.widget.Toast.makeText(app, shown, android.widget.Toast.LENGTH_SHORT).show();
                    if (made == null) return;
                    List<JavaRun.FileOut> files = new ArrayList<JavaRun.FileOut>();
                    files.add(new JavaRun.FileOut(name, made));
                    PictureOffer.offer(app, new JavaRun.Result("Succeeded: " + name, files, 0));
                });
            }, "pulsekit-video-frame").start();
        }

        void toggleMute(MainActivity app) {
            muted = !muted;
            mute.setText(muted ? "Unmute" : "Mute");
            app.paintChip(mute, muted);
            applyVolume();
        }
    }

    static Player last;

    private VideoOffer() {}

    static boolean isVideo(String name) {
        return name != null && name.toLowerCase().matches(".+\\.(mp4|m4v|webm|mov)");
    }

    /** Shows the run's first video; false when it made none. */
    static boolean offer(final MainActivity app, JavaRun.Result result) {
        if (result == null || result.files == null) return false;
        List<JavaRun.FileOut> videos = new ArrayList<JavaRun.FileOut>();
        for (JavaRun.FileOut f : result.files) if (isVideo(f.name) && f.bytes != null && f.bytes.length > 0) videos.add(f);
        if (videos.isEmpty()) return false;
        JavaRun.FileOut clip = videos.get(0);
        // A joined clip (SogniVideo's Join with this video) is the one to watch.
        for (JavaRun.FileOut f : videos) if (f.name.toLowerCase().matches(".+-merged(\\(\\d+\\))?\\.mp4")) clip = f;
        final String path = cacheCopy(app, clip.name, clip.bytes);
        if (path == null) return false;
        final Player p = new Player();
        last = p;
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        String others = videos.size() > 1 ? " (+" + (videos.size() - 1) + " more in Downloads)" : "";
        TextView name = app.text(clip.name + ", " + (clip.bytes.length / 1024) + " KB" + others, 13, false);
        name.setPadding(0, 0, 0, app.dp(6));
        col.addView(name);
        p.view = new VideoView(app);
        p.view.setTag("video-offer:view");
        final int width = app.getResources().getDisplayMetrics().widthPixels - app.dp(72);
        final LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, app.dp(240));
        col.addView(p.view, size);
        p.view.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                p.media = mp;
                p.applyVolume();
                // The clip's own shape: as wide as the dialog, at most 60% of the screen high.
                int w = mp.getVideoWidth(), h = mp.getVideoHeight();
                if (w > 0 && h > 0) {
                    int max = app.getResources().getDisplayMetrics().heightPixels * 6 / 10;
                    size.height = Math.min(max, width * h / w);
                    p.view.setLayoutParams(size);
                }
                // The first frame, until Play.
                if (!p.playing) p.view.seekTo(1);
            }
        });
        p.view.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                p.playing = false;
                p.play.setText("Play");
                p.awake();
            }
        });
        p.view.setVideoPath(path);
        // Long press: the clip pauses, and the frame shown can be extracted.
        final String clipName = clip.name;
        p.view.setOnLongClickListener(v -> {
            p.frameMenu(app, path, clipName);
            return true;
        });
        LinearLayout buttons = app.row();
        buttons.setPadding(0, app.dp(10), 0, app.dp(4));
        p.play = app.pill("Play", true, v -> p.playPause());
        p.play.setTag("video-offer:play");
        TextView stop = app.pill("Stop", false, v -> p.stop());
        stop.setTag("video-offer:stop");
        p.mute = app.pill("Mute", false, v -> p.toggleMute(app));
        p.mute.setTag("video-offer:mute");
        for (TextView b : new TextView[] {p.play, stop, p.mute}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(app.dp(3), 0, app.dp(3), 0);
            buttons.addView(b, lp);
        }
        col.addView(buttons);
        // Where the clip is: mm:ss / mm:ss, ticking while it plays.
        p.clock = app.text("00:00 / 00:00", 13, false);
        p.clock.setTag("video-offer:clock");
        p.clock.setTypeface(android.graphics.Typeface.MONOSPACE);
        p.clock.setGravity(android.view.Gravity.CENTER);
        p.clock.setPadding(0, app.dp(4), 0, 0);
        col.addView(p.clock, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout volumeRow = app.row();
        volumeRow.setPadding(0, app.dp(6), 0, 0);
        volumeRow.addView(app.text("Volume", 13, false));
        SeekBar volume = new SeekBar(app);
        volume.setMax(100);
        volume.setProgress(p.volume);
        volume.setTag("video-offer:volume");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                p.volume = value;
                p.applyVolume();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });
        volumeRow.addView(volume, new LinearLayout.LayoutParams(0, -2, 1f));
        p.level = app.text("100%", 13, false);
        p.level.setTag("video-offer:level");
        p.level.setMinWidth(app.dp(52));
        volumeRow.addView(p.level);
        col.addView(volumeRow);
        AlertDialog dialog = new AlertDialog.Builder(app)
            .setTitle("Video ready")
            .setView(col)
            .setPositiveButton("Close", null)
            .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                p.open = false;
                p.playing = false;
                p.awake();
                p.media = null;
                p.view.stopPlayback();
            }
        });
        p.window = dialog.getWindow();
        p.appWindow = app.getWindow();
        dialog.show();
        p.open = true;
        p.tick();
        return true;
    }

    /** The clip as a file the player can open (the kept copy is in Downloads, which has no path here); null if it cannot be written. */
    static String cacheCopy(MainActivity app, String name, byte[] data) {
        try {
            java.io.File dir = new java.io.File(app.getCacheDir(), "video-offer");
            java.io.File[] old = dir.listFiles();
            if (old != null) for (java.io.File f : old) f.delete();
            if (!dir.isDirectory()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, name.replace(' ', '_').replace('/', '_'));
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            try {
                fos.write(data);
            } finally {
                fos.close();
            }
            return out.getAbsolutePath();
        } catch (Exception ex) {
            return null;
        }
    }
}
