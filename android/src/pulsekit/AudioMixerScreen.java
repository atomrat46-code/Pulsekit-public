package pulsekit;

import android.app.AlertDialog;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.io.File;

/**
 * AudioMixer (File > AudioMixer, or PyJav's Java list): the tracks, each with its volume (0 to 150%),
 * gain (− and + by 1 dB), Mute, Solo, ▶ (it alone) and ✕ (taken out), and its input with ● Record,
 * ❚❚ Pause and ■ Stop (TrackRecorder); + New empty track; Import new track… (the Media
 * browser: a WAV, an MP3, or a drum MIDI played with the kit), ▶ Play mix, ■ Stop, Mixdown (the mix
 * as a WAV in Download, and in the prompt library with Drum Midi Settings' "Write AudioMixer output
 * tracks into db") and New project. The tracks stay in the app's audiomixer folder until cleared.
 */
final class AudioMixerScreen {
    private AudioMixerScreen() {}

    /** The dialog shown last, where the mixdown went last, and what was said (for the tests). */
    static AlertDialog last;
    static volatile String lastMixdown;
    static volatile String lastSaid;
    static AudioTrack playing;

    static File folder(MainActivity app) {
        return new File(app.getFilesDir(), "audiomixer");
    }

    static void show(final MainActivity app) {
        AudioMix.open(folder(app));
        LinearLayout col = app.col();
        col.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
        final LinearLayout rows = app.col();
        rows.setTag("audiomixer-tracks");
        final TextView status = app.text(" ", 13, false);
        status.setTag("audiomixer-status");
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            rows.removeAllViews();
            if (AudioMix.tracks.isEmpty()) {
                TextView none = app.text("No tracks yet: " + AudioMix.IMPORT, 14, false);
                none.setTag("audiomixer-empty");
                rows.addView(none);
            }
            for (AudioMix.Track t : new java.util.ArrayList<AudioMix.Track>(AudioMix.tracks)) rows.addView(row(app, t, status, fill));
        };
        LinearLayout buttons = app.row();
        TextView imp = app.pill(AudioMix.IMPORT, false, v -> MediaBrowser.choose(app, (name, file) -> add(app, name, file, status, fill[0]), null));
        imp.setTag("audiomixer-import");
        TextView play = app.pill("▶ Play mix", false, v -> playMix(app, status));
        play.setTag("audiomixer-play");
        TextView stop = app.pill("■", false, v -> stop());
        stop.setTag("audiomixer-stop");
        stop.setContentDescription("Stop");
        TextView empty = app.pill("+ " + AudioMix.NEW_EMPTY, false, v -> newEmpty(status, fill[0]));
        empty.setTag("audiomixer-new-empty");
        buttons.addView(imp);
        buttons.addView(empty, margin(app));
        LinearLayout transport = app.row();
        transport.addView(play);
        transport.addView(stop, margin(app));
        LinearLayout more = app.row();
        TextView mix = app.pill(AudioMix.MIXDOWN, true, v -> mixdown(app, status));
        mix.setTag("audiomixer-mixdown");
        TextView clear = app.pill("New project", false, v -> {
            stop();
            TrackRecorder.stop();
            if (AudioMix.tracks.isEmpty()) return;
            new AlertDialog.Builder(app).setTitle(AudioMix.CLEAR).setMessage("Take every track out of the AudioMixer?")
                .setPositiveButton("Clear", (d, w) -> {
                    AudioMix.clear();
                    fill[0].run();
                }).setNegativeButton("Cancel", null).show();
        });
        clear.setTag("audiomixer-clear");
        more.addView(mix);
        more.addView(clear, margin(app));
        col.addView(buttons);
        col.addView(transport);
        col.addView(more);
        col.addView(status);
        col.addView(rows);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        fill[0].run();
        if (last != null && last.isShowing()) last.dismiss();
        last = new AlertDialog.Builder(app).setTitle(AudioMix.TITLE).setView(scroll).setPositiveButton("Close", null).create();
        last.setOnDismissListener(d -> {
            stop();
            TrackRecorder.stop();
        });
        last.show();
    }

    static LinearLayout.LayoutParams margin(MainActivity app) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = app.dp(6);
        return lp;
    }

    /** One track: its line, volume, gain − / +, Mute, Solo, ▶ and ✕. */
    static LinearLayout row(final MainActivity app, final AudioMix.Track t, final TextView status, final Runnable[] fill) {
        LinearLayout box = app.col();
        box.setTag("audiomixer-track:" + t.name);
        box.setPadding(0, app.dp(8), 0, app.dp(4));
        final TextView name = app.text(AudioMix.line(t), 14, true);
        box.addView(name);
        SeekBar vol = new SeekBar(app);
        vol.setTag("audiomixer-volume:" + t.name);
        vol.setMax(AudioMix.MAX_VOLUME);
        vol.setProgress(t.volume);
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                t.volume = v;
                name.setText(AudioMix.line(t));
            }

            public void onStartTrackingTouch(SeekBar s) {}

            public void onStopTrackingTouch(SeekBar s) {
                AudioMix.save();
            }
        });
        box.addView(vol);
        LinearLayout r = app.row();
        TextView down = app.pill("− dB", false, v -> {
            t.gain = Math.max(AudioMix.MIN_GAIN, t.gain - 1);
            name.setText(AudioMix.line(t));
            AudioMix.save();
        });
        down.setTag("audiomixer-gain-down:" + t.name);
        TextView up = app.pill("+ dB", false, v -> {
            t.gain = Math.min(AudioMix.MAX_GAIN, t.gain + 1);
            name.setText(AudioMix.line(t));
            AudioMix.save();
        });
        up.setTag("audiomixer-gain-up:" + t.name);
        final CheckBox mute = new CheckBox(app);
        mute.setText("Mute");
        mute.setTag("audiomixer-mute:" + t.name);
        mute.setChecked(t.mute);
        mute.setOnCheckedChangeListener((b, on) -> {
            t.mute = on;
            name.setText(AudioMix.line(t));
            AudioMix.save();
        });
        final CheckBox solo = new CheckBox(app);
        solo.setText("Solo");
        solo.setTag("audiomixer-solo:" + t.name);
        solo.setChecked(t.solo);
        solo.setOnCheckedChangeListener((b, on) -> {
            t.solo = on;
            name.setText(AudioMix.line(t));
            AudioMix.save();
        });
        TextView one = app.pill("▶", false, v -> {
            try {
                AudioIo.StereoPcm p = AudioMix.parseStereo(AudioMix.read(new File(AudioMix.dir, t.file)));
                float g = (float) (t.volume / 100.0 * Math.pow(10, t.gain / 20.0));
                float[] l = p.left.clone(), rr = p.right.clone();
                for (int i = 0; i < l.length; i++) {
                    l[i] = Math.max(-1, Math.min(1, l[i] * g));
                    rr[i] = Math.max(-1, Math.min(1, rr[i] * g));
                }
                play(l, rr, status, t.name);
            } catch (Exception ex) {
                say(status, "Could not play " + t.name);
            }
        });
        one.setTag("audiomixer-play:" + t.name);
        TextView out = app.pill("✕", false, v -> {
            stop();
            if (TrackRecorder.recording() == t) TrackRecorder.stop();
            AudioMix.remove(t);
            fill[0].run();
        });
        out.setTag("audiomixer-remove:" + t.name);
        r.addView(down);
        r.addView(up, margin(app));
        r.addView(mute, margin(app));
        r.addView(solo, margin(app));
        r.addView(one, margin(app));
        r.addView(out, margin(app));
        box.addView(r);
        box.addView(recordRow(app, t, name, status));
        return box;
    }

    /** The track's input and ● Record, ❚❚ Pause, ■ Stop, with what the input hears. */
    static LinearLayout recordRow(final MainActivity app, final AudioMix.Track t, final TextView name, final TextView status) {
        LinearLayout col = app.col();
        LinearLayout r = app.row();
        final String[][] in = TrackRecorder.choices();
        final android.widget.Spinner pick = new android.widget.Spinner(app);
        pick.setTag("audiomixer-input:" + t.name);
        android.widget.ArrayAdapter<String> names = new android.widget.ArrayAdapter<String>(app, android.R.layout.simple_spinner_item, in[0]);
        names.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pick.setAdapter(names);
        pick.setSelection(Math.max(0, java.util.Arrays.asList(in[1]).indexOf(t.input)));
        pick.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, android.view.View v, int i, long id) {
                if (in[1][i].equals(t.input)) return;
                t.input = in[1][i];
                AudioMix.save();
            }

            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        final TextView heard = app.text(" ", 12, false);
        heard.setTag("audiomixer-heard:" + t.name);
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        TextView rec = app.pill(TrackRecorder.RECORD, false, v -> {
            if (android.os.Build.VERSION.SDK_INT >= 23 && app.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                say(status, ASK_MIC);
                app.requestPermissions(new String[] {android.Manifest.permission.RECORD_AUDIO}, InputTestScreen.MIC_PERMISSION);
                return;
            }
            stop();
            say(status, TrackRecorder.record(t, text -> main.post(() -> {
                heard.setText(text);
                name.setText(AudioMix.line(t));
            })));
        });
        rec.setTag("audiomixer-record:" + t.name);
        TextView pause = app.pill(TrackRecorder.PAUSE, false, v -> {
            if (TrackRecorder.recording() != t) return;
            say(status, TrackRecorder.pause());
            name.setText(AudioMix.line(t));
        });
        pause.setTag("audiomixer-pause:" + t.name);
        TextView stopRec = app.pill(TrackRecorder.STOP, false, v -> {
            stop();
            if (TrackRecorder.recording() != t) return;
            say(status, TrackRecorder.stop());
            heard.setText(" ");
            name.setText(AudioMix.line(t));
        });
        stopRec.setTag("audiomixer-record-stop:" + t.name);
        r.addView(rec);
        r.addView(pause, margin(app));
        r.addView(stopRec, margin(app));
        col.addView(pick);
        col.addView(r);
        col.addView(heard);
        return col;
    }

    static final String ASK_MIC = "Recording needs the microphone: allow it, then tap " + TrackRecorder.RECORD + " again";

    /** New empty track: a track with no sound, for ● Record. */
    static void newEmpty(TextView status, Runnable fill) {
        try {
            AudioMix.Track t = AudioMix.addEmpty();
            say(status, "Added " + t.name + ": pick its input, then " + TrackRecorder.RECORD);
        } catch (Exception ex) {
            say(status, "Could not add a track: " + ex.getMessage());
        }
        if (fill != null) fill.run();
    }

    /** The phone's audio inputs for TrackRecorder (AudioRecord at 44.1 kHz mono, the picked device preferred). */
    static TrackRecorder.Inputs inputs(final MainActivity app) {
        return new TrackRecorder.Inputs() {
            public java.util.List<SoundDevices.Device> list() {
                return SoundDevices.of(SoundOut.list(app), SoundDevices.IN);
            }

            public TrackRecorder.Input open(String key) throws Exception {
                int min = android.media.AudioRecord.getMinBufferSize(AudioMix.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                final android.media.AudioRecord rec = new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, AudioMix.RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192) * 2);
                if (rec.getState() != android.media.AudioRecord.STATE_INITIALIZED) {
                    rec.release();
                    throw new IllegalStateException("the microphone could not be opened");
                }
                if (key != null && !key.isEmpty()) {
                    for (SoundDevices.Device d : list()) {
                        if (!d.key().equals(key)) continue;
                        android.media.AudioDeviceInfo info = InputTestScreen.input(app, d);
                        if (info != null) rec.setPreferredDevice(info);
                    }
                }
                rec.startRecording();
                return new TrackRecorder.Input() {
                    public int read(short[] buf) {
                        return rec.read(buf, 0, buf.length);
                    }

                    public void close() {
                        try {
                            rec.stop();
                        } catch (Throwable ignored) {
                            // not started
                        }
                        rec.release();
                    }
                };
            }
        };
    }

    /** `file` (named `name`) added as a track, off the main thread; the rows made again. */
    static void add(final MainActivity app, final String name, final File file, final TextView status, final Runnable fill) {
        if (!AudioMix.importable(name)) {
            say(status, name + " is not a WAV, an MP3 or a MIDI file");
            return;
        }
        say(status, "Adding " + name + "…");
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            String said;
            try {
                short[][] kit = app.playback == null ? null : app.playback.mixVoices();
                AudioMix.Track t = AudioMix.add(name, AudioMix.read(file), kit);
                said = "Added " + AudioMix.line(t);
            } catch (Exception ex) {
                said = "Could not add " + name + ": " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            final String shown = said;
            main.post(() -> {
                say(status, shown);
                if (fill != null) fill.run();
            });
        }, "pulsekit-mixer-add").start();
    }

    /** Mixdown: the mix as a WAV in Download (and in the library when the setting says so). */
    static void mixdown(final MainActivity app, final TextView status) {
        if (AudioMix.heard().isEmpty()) {
            say(status, "Nothing to mix: add a track (or unmute one)");
            return;
        }
        say(status, "Mixing down…");
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            String said;
            String where = null;
            try {
                StringBuilder scaled = new StringBuilder();
                byte[] wav = AudioMix.mixdown(scaled);
                String name = AudioMix.mixdownName();
                where = ArtJava.publish(app, name, wav);
                if (where == null) throw new java.io.IOException("Download took no file");
                said = "Mixdown: " + where + (scaled.length() > 0 ? " (" + scaled + ")" : "");
                if (AudioMix.toDb) said += "; " + MediaDir.addToDb(app.getFilesDir(), name, wav, true);
            } catch (Exception ex) {
                said = "Could not mix down: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            }
            lastMixdown = where;
            final String shown = said;
            main.post(() -> {
                say(status, shown);
                app.setNow(shown);
            });
        }, "pulsekit-mixdown").start();
    }

    static void playMix(final MainActivity app, final TextView status) {
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            try {
                float[][] m = AudioMix.mix(null);
                main.post(() -> play(m[0], m[1], status, "the mix"));
            } catch (Exception ex) {
                main.post(() -> say(status, "Could not mix: " + ex.getMessage()));
            }
        }, "pulsekit-mixer-play").start();
    }

    static void play(float[] l, float[] r, TextView status, String what) {
        stop();
        if (l.length == 0) {
            say(status, "Nothing to play");
            return;
        }
        try {
            short[] pcm = new short[l.length * 2];
            for (int i = 0; i < l.length; i++) {
                pcm[2 * i] = (short) Math.round(Math.max(-1, Math.min(1, l[i])) * 32767);
                pcm[2 * i + 1] = (short) Math.round(Math.max(-1, Math.min(1, r[i])) * 32767);
            }
            AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(AudioMix.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(pcm.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
            t.write(pcm, 0, pcm.length);
            t.play();
            playing = t;
            say(status, "Playing " + what);
        } catch (Exception ex) {
            say(status, "No audio output for " + what);
        }
    }

    static void stop() {
        AudioTrack t = playing;
        playing = null;
        if (t != null) {
            try {
                t.stop();
            } catch (Exception ignored) {
                // stopped
            }
            t.release();
        }
    }

    /** An MP3 read by the phone (MediaExtractor), mono on both sides at its own rate. */
    static AudioIo.StereoPcm decodeMp3(byte[] mp3) throws Exception {
        File tmp = File.createTempFile("pulsekit-mixer", ".mp3");
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            try {
                out.write(mp3);
            } finally {
                out.close();
            }
            java.io.FileInputStream in = new java.io.FileInputStream(tmp);
            ProjectIo.Decoded d;
            try {
                d = ProjectIo.decodeNative(in.getFD());
            } finally {
                in.close();
            }
            float[] f = new float[d.mono.length];
            for (int i = 0; i < f.length; i++) f[i] = d.mono[i] / 32768f;
            return new AudioIo.StereoPcm(f, f.clone(), d.rate);
        } finally {
            tmp.delete();
        }
    }

    static void say(TextView status, String s) {
        lastSaid = s;
        if (status != null) status.setText(s);
    }
}
