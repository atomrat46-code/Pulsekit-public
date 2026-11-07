package pulsekit;

import android.app.AlertDialog;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * A DrumPad track's Drumpad window (DrumPads): the kit (any drum set), BPM − / +, time signature and
 * Metronome, the track's volume and gain, the 12 pads (they sound as a finger touches them), and
 * ● Record, ❚❚ Pause, ■ Stop and ▶ Play. The pads sound through Pulsekit's output.
 */
final class DrumPadScreen {
    private DrumPadScreen() {}

    /** The window shown last (for the tests). */
    static AlertDialog last;

    static void show(final MainActivity app, final AudioMix.Track t, final Runnable changed) {
        if (last != null && last.isShowing()) last.dismiss();
        app.playback.fillMissingVoices();
        final DrumPads.Session s = DrumPads.open(t, null);
        int kitAt = app.playback.drumSetIndex;
        for (int i = 0; i < app.drumSets.size(); i++) if (app.drumSets.get(i).name.equals(s.take.kit)) kitAt = i;
        kitAt = Math.max(0, Math.min(app.drumSets.size() - 1, kitAt));
        Engine.DrumSet set = app.drumSets.get(kitAt);
        s.setVoices(DrumPads.atRate(DrumPads.voices(set, app.voices)), set.name);
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        final TextView status = app.text(s.state(), 13, false);
        status.setTag("drumpad-status");
        // The kit.
        String[] kits = new String[app.drumSets.size()];
        for (int i = 0; i < kits.length; i++) kits[i] = app.drumSets.get(i).name;
        final android.widget.Spinner kit = spinner(app, kits);
        kit.setTag("drumpad-kit");
        kit.setSelection(kitAt);
        kit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) {
                Engine.DrumSet k = app.drumSets.get(i);
                if (k.name.equals(s.take.kit)) return;
                s.setVoices(DrumPads.atRate(DrumPads.voices(k, app.voices)), k.name);
                try {
                    s.write();
                    status.setText(s.state());
                } catch (Exception ex) {
                    status.setText("Could not write " + t.name + ": " + ex.getMessage());
                }
                if (changed != null) changed.run();
            }

            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        col.addView(kit);
        // BPM, time signature, Metronome.
        LinearLayout tempo = app.row();
        final TextView bpm = app.text(s.take.bpm + " BPM", 14, true);
        bpm.setTag("drumpad-bpm");
        TextView slower = app.pill("−", false, v -> {
            s.take.bpm = Math.max(Engine.MIN_BPM, s.take.bpm - 1);
            bpm.setText(s.take.bpm + " BPM");
            keep(s);
        });
        slower.setTag("drumpad-bpm-down");
        TextView faster = app.pill("+", false, v -> {
            s.take.bpm = Math.min(Engine.MAX_BPM, s.take.bpm + 1);
            bpm.setText(s.take.bpm + " BPM");
            keep(s);
        });
        faster.setTag("drumpad-bpm-up");
        final android.widget.Spinner ts = spinner(app, DrumPads.SIGNATURES);
        ts.setTag("drumpad-ts");
        ts.setSelection(Math.max(0, java.util.Arrays.asList(DrumPads.SIGNATURES).indexOf(s.take.ts)));
        ts.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) {
                if (DrumPads.SIGNATURES[i].equals(s.take.ts)) return;
                s.take.ts = DrumPads.SIGNATURES[i];
                keep(s);
            }

            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        final TextView[] metro = new TextView[1];
        metro[0] = app.pill(DrumPads.METRONOME, false, v -> {
            s.metronome(!s.metronomeOn());
            metro[0].setText((s.metronomeOn() ? "✔ " : "") + DrumPads.METRONOME);
        });
        metro[0].setTag("drumpad-metronome");
        tempo.addView(slower);
        tempo.addView(bpm, AudioMixerScreen.margin(app));
        tempo.addView(faster, AudioMixerScreen.margin(app));
        tempo.addView(ts, AudioMixerScreen.margin(app));
        col.addView(tempo);
        col.addView(metro[0]);
        // The track's volume and gain.
        final TextView level = app.text(AudioMix.line(t), 13, false);
        SeekBar vol = new SeekBar(app);
        vol.setTag("drumpad-volume");
        vol.setMax(AudioMix.MAX_VOLUME);
        vol.setProgress(t.volume);
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int v, boolean user) {
                t.volume = v;
                level.setText(AudioMix.line(t));
            }

            public void onStartTrackingTouch(SeekBar b) {}

            public void onStopTrackingTouch(SeekBar b) {
                AudioMix.save();
                if (changed != null) changed.run();
            }
        });
        LinearLayout gains = app.row();
        TextView down = app.pill("− dB", false, v -> {
            t.gain = Math.max(AudioMix.MIN_GAIN, t.gain - 1);
            level.setText(AudioMix.line(t));
            AudioMix.save();
            if (changed != null) changed.run();
        });
        down.setTag("drumpad-gain-down");
        TextView up = app.pill("+ dB", false, v -> {
            t.gain = Math.min(AudioMix.MAX_GAIN, t.gain + 1);
            level.setText(AudioMix.line(t));
            AudioMix.save();
            if (changed != null) changed.run();
        });
        up.setTag("drumpad-gain-up");
        gains.addView(down);
        gains.addView(up, AudioMixerScreen.margin(app));
        col.addView(level);
        col.addView(vol);
        col.addView(gains);
        // The pads: 3 rows of 4, sounding as a finger touches them.
        for (int row = 0; row < 3; row++) {
            LinearLayout r = app.row();
            for (int c = 0; c < 4; c++) {
                final int pad = row * 4 + c;
                final TextView p = app.pad(Engine.TRACK_SHORT[pad], Engine.TRACK_LABEL[pad], null);
                p.setTag("drumpad-pad:" + Engine.TRACK_ID[pad]);
                p.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                        s.hit(pad, 1f);
                        v.setAlpha(0.6f);
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        v.setAlpha(1f);
                    }
                    return true;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, app.dp(72), 1f);
                lp.setMargins(app.dp(3), app.dp(3), app.dp(3), app.dp(3));
                r.addView(p, lp);
            }
            col.addView(r);
        }
        // ● Record, ❚❚ Pause, ■ Stop, ▶ Play.
        LinearLayout transport = app.row();
        TextView rec = app.pill(TrackRecorder.RECORD, true, v -> {
            AudioMixerScreen.stop();
            status.setText(s.record());
        });
        rec.setTag("drumpad-record");
        TextView pause = app.pill(TrackRecorder.PAUSE, false, v -> {
            try {
                status.setText(s.pause());
            } catch (Exception ex) {
                status.setText("Could not write " + t.name + ": " + ex.getMessage());
            }
            if (changed != null) changed.run();
        });
        pause.setTag("drumpad-pause");
        TextView stop = app.pill(TrackRecorder.STOP, false, v -> {
            AudioMixerScreen.stop();
            if (!s.recording()) return;
            try {
                status.setText(s.stop());
            } catch (Exception ex) {
                status.setText("Could not write " + t.name + ": " + ex.getMessage());
            }
            if (changed != null) changed.run();
        });
        stop.setTag("drumpad-stop");
        TextView play = app.pill("▶", false, v -> {
            float[][] m = DrumPads.render(s.take, s.voices);
            float g = (float) t.factor();
            for (int i = 0; i < m[0].length; i++) {
                m[0][i] *= g;
                m[1][i] *= g;
            }
            AudioMixerScreen.play(m[0], m[1], status, t.name);
        });
        play.setTag("drumpad-play");
        transport.addView(rec);
        transport.addView(pause, AudioMixerScreen.margin(app));
        transport.addView(stop, AudioMixerScreen.margin(app));
        transport.addView(play, AudioMixerScreen.margin(app));
        col.addView(transport);
        col.addView(status);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (last == null || !last.isShowing() || DrumPads.current != s) return;
            if (s.recording()) status.setText(s.state());
            main.postDelayed(tick[0], 200);
        };
        last = new AlertDialog.Builder(app).setTitle(DrumPads.TITLE + ": " + t.name).setView(scroll).setPositiveButton("Close", null).create();
        last.setOnDismissListener(d -> {
            s.close();
            if (changed != null) changed.run();
        });
        last.show();
        main.postDelayed(tick[0], 200);
        s.play(out());
    }

    static android.widget.Spinner spinner(MainActivity app, String[] items) {
        android.widget.Spinner sp = new android.widget.Spinner(app);
        android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<String>(app, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);
        return sp;
    }

    static void keep(DrumPads.Session s) {
        try {
            DrumPads.save(s.track, s.take);
        } catch (Exception ignored) {
            // kept for this session
        }
    }

    /** Pulsekit's output for the pads (low latency where the phone has it); null when there is none. */
    static DrumPads.Out out() {
        try {
            int min = AudioTrack.getMinBufferSize(AudioMix.RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack.Builder b = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(AudioMix.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(Math.max(min, DrumPads.BLOCK * 4 * 2)).setTransferMode(AudioTrack.MODE_STREAM);
            if (android.os.Build.VERSION.SDK_INT >= 26) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            final AudioTrack track = b.build();
            SoundOut.prefer(track);
            track.play();
            return new DrumPads.Out() {
                public void write(short[] lr, int frames) {
                    if (track.write(lr, 0, frames * 2) < 0) throw new IllegalStateException("the output stopped");
                }

                public void close() {
                    try {
                        track.stop();
                    } catch (Throwable ignored) {
                        // not playing
                    }
                    track.release();
                }
            };
        } catch (Throwable ex) {
            return null;
        }
    }
}
