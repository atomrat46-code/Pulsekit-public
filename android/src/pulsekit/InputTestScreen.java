package pulsekit;

import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * File > Sound devices > an input > Test (InputLevel): a level meter of what the input hears while
 * the window is open, and Record, Stop, Play and Save (a WAV into Download). The recording level
 * set on Sound devices is applied to what is heard and recorded.
 */
final class InputTestScreen {
    private InputTestScreen() {}

    static final int MIC_PERMISSION = 93;
    static final String ASK_MIC = "Testing an input needs the microphone: allow it, then tap Test again";

    /** For the tests: the window shown last, what it said last, the samples recorded. */
    static AlertDialog last;
    static volatile String lastSaid = "";
    static volatile int recordedSamples;

    private static volatile boolean open;
    private static volatile boolean recording;
    private static volatile boolean playing;
    private static short[] recorded = new short[0];
    private static int length;

    static void show(final MainActivity app, final SoundDevices.Device d) {
        if (android.os.Build.VERSION.SDK_INT >= 23 && app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            said(app, ASK_MIC);
            app.requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            return;
        }
        LinearLayout col = app.col();
        col.setPadding(app.dp(20), app.dp(8), app.dp(20), app.dp(8));
        final TextView level = app.text("Listening…", 13, false);
        level.setTag("input-level");
        final ProgressBar meter = new ProgressBar(app, null, android.R.attr.progressBarStyleHorizontal);
        meter.setMax(1000);
        meter.setTag("input-meter");
        final ProgressBar peak = new ProgressBar(app, null, android.R.attr.progressBarStyleHorizontal);
        peak.setMax(1000);
        peak.setTag("input-peak");
        final TextView state = app.text("Recording level " + AppSettings.recordLevel() + "%. Nothing recorded yet.", 13, false);
        state.setTag("input-state");
        col.addView(level);
        col.addView(meter, new LinearLayout.LayoutParams(-1, -2));
        col.addView(peak, new LinearLayout.LayoutParams(-1, -2));
        col.addView(state);
        LinearLayout buttons = app.row();
        final TextView rec = app.outline(InputLevel.RECORD, false, null);
        final TextView stop = app.outline(InputLevel.STOP, false, null);
        final TextView play = app.outline(InputLevel.PLAY, false, null);
        final TextView save = app.outline(InputLevel.SAVE, false, null);
        rec.setTag("input-record");
        stop.setTag("input-stop");
        play.setTag("input-play");
        save.setTag("input-save");
        for (TextView b : new TextView[] {rec, stop, play, save}) buttons.addView(b);
        col.addView(app.chipStrip(buttons));
        rec.setOnClickListener(v -> {
            synchronized (InputTestScreen.class) {
                length = 0;
                recorded = new short[InputLevel.RATE * 10];
            }
            playing = false;
            recording = true;
            state.setText("Recording… (Stop ends it)");
        });
        stop.setOnClickListener(v -> {
            boolean was = recording;
            recording = false;
            playing = false;
            recordedSamples = length;
            state.setText(was ? "Recorded " + InputLevel.length(length) + ": Play listens to it, Save keeps it" : "Stopped");
        });
        play.setOnClickListener(v -> {
            if (length == 0) {
                state.setText("Record something first");
                return;
            }
            recording = false;
            play(app, state);
        });
        save.setOnClickListener(v -> {
            if (length == 0) {
                state.setText("Record something first");
                return;
            }
            final short[] pcm = java.util.Arrays.copyOf(recorded, length);
            final String name = InputLevel.fileName();
            new Thread(() -> {
                String where = ArtJava.publish(app, name, AudioIo.encodeWav(pcm, InputLevel.RATE));
                app.handler.post(() -> said(app, where == null ? "Could not save " + name : "Saved " + where));
            }, "pulsekit-input-save").start();
        });
        open = true;
        recording = false;
        length = 0;
        recordedSamples = 0;
        last = new AlertDialog.Builder(app).setTitle(InputLevel.TITLE + ": " + d.name).setView(col).setPositiveButton("Close", null).create();
        last.setOnDismissListener(x -> {
            open = false;
            recording = false;
            playing = false;
        });
        last.show();
        listen(app, d, level, meter, peak);
    }

    /** What the input hears, read on a thread of its own while the window is open; the meter updated about ten times a second. */
    static void listen(final MainActivity app, final SoundDevices.Device d, final TextView level, final ProgressBar meter, final ProgressBar peak) {
        new Thread(() -> {
            AudioRecord rec = null;
            try {
                int min = AudioRecord.getMinBufferSize(InputLevel.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                rec = new AudioRecord(MediaRecorder.AudioSource.MIC, InputLevel.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192) * 2);
                AudioDeviceInfo info = input(app, d);
                if (info != null) rec.setPreferredDevice(info);
                rec.startRecording();
                short[] buf = new short[InputLevel.RATE / 10];
                while (open) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n < 0) throw new IllegalStateException("the input gave error " + n);
                    if (n == 0) {
                        Thread.sleep(20);
                        continue;
                    }
                    InputLevel.gain(buf, n, AppSettings.recordLevel());
                    if (recording) keep(buf, n);
                    final double[] lv = InputLevel.levels(buf, n);
                    final String text = InputLevel.text(lv) + (recording ? " · recording " + InputLevel.length(length) : "");
                    app.handler.post(() -> {
                        lastSaid = text;
                        level.setText(text);
                        meter.setProgress((int) Math.round(InputLevel.meter(lv[0]) * 1000));
                        peak.setProgress((int) Math.round(InputLevel.meter(lv[1]) * 1000));
                    });
                }
            } catch (Throwable ex) {
                final String text = "Could not listen to " + d.name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
                app.handler.post(() -> {
                    lastSaid = text;
                    level.setText(text);
                });
                SoundDevices.log("Input test: " + d.name, text);
            } finally {
                if (rec != null) {
                    try {
                        rec.stop();
                    } catch (Throwable ignored) {
                        // not started
                    }
                    rec.release();
                }
            }
        }, "pulsekit-input-test").start();
    }

    private static synchronized void keep(short[] buf, int n) {
        int max = InputLevel.RATE * InputLevel.MAX_SECONDS;
        if (length + n > max) {
            recording = false;
            n = max - length;
        }
        if (length + n > recorded.length) recorded = java.util.Arrays.copyOf(recorded, Math.min(max, Math.max(length + n, recorded.length * 2)));
        System.arraycopy(buf, 0, recorded, length, n);
        length += n;
        recordedSamples = length;
    }

    /** The recording played through Pulsekit's output, at its playback volume. */
    static void play(final MainActivity app, final TextView state) {
        final short[] pcm;
        synchronized (InputTestScreen.class) {
            pcm = java.util.Arrays.copyOf(recorded, length);
        }
        playing = true;
        state.setText("Playing " + InputLevel.length(pcm.length) + "…");
        new Thread(() -> {
            AudioTrack t = null;
            try {
                int min = AudioTrack.getMinBufferSize(InputLevel.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                t = new AudioTrack(AudioManager.STREAM_MUSIC, InputLevel.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192), AudioTrack.MODE_STREAM);
                SoundOut.prefer(t);
                t.play();
                for (int at = 0; at < pcm.length && playing; at += 4096) t.write(pcm, at, Math.min(4096, pcm.length - at));
                t.stop();
            } catch (Throwable ex) {
                app.handler.post(() -> state.setText("Could not play: " + ex.getMessage()));
            } finally {
                if (t != null) t.release();
                boolean done = playing;
                playing = false;
                if (done) app.handler.post(() -> state.setText("Played " + InputLevel.length(pcm.length)));
            }
        }, "pulsekit-input-play").start();
    }

    /** The phone's input of this device (the first of its name and kind). */
    static AudioDeviceInfo input(Context c, SoundDevices.Device d) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return null;
        for (AudioDeviceInfo i : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) if (SoundOut.device(SoundDevices.IN, i).key().equals(d.key())) return i;
        return null;
    }

    static void said(MainActivity app, String text) {
        lastSaid = text;
        app.setNow(text);
        android.widget.Toast.makeText(app, text, android.widget.Toast.LENGTH_LONG).show();
    }
}
