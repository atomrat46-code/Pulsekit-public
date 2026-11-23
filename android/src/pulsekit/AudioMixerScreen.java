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
 * ❚❚ Pause and ■ Stop (TrackRecorder), or a DrumPad track's Drumpad button (DrumPadScreen); + New
 * empty track; + New DrumPad track; Import new track… (the Media
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
    /** The open screen's rows made again (tracks added from elsewhere). */
    static Runnable refill;

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
            WAVES.clear();
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
        TextView newPads = app.pill("+ " + DrumPads.NEW, false, v -> newPads(status, fill[0]));
        newPads.setTag("audiomixer-new-pads");
        LinearLayout transport = app.row();
        transport.addView(play);
        transport.addView(stop, margin(app));
        LinearLayout padsRow = app.row();
        padsRow.addView(newPads);
        LinearLayout more = app.row();
        TextView mix = app.pill(AudioMix.MIXDOWN, true, v -> mixdown(app, status));
        mix.setTag("audiomixer-mixdown");
        TextView clear = app.pill("New project", false, v -> {
            stop();
            TrackRecorder.stop();
            if (DrumPadScreen.last != null && DrumPadScreen.last.isShowing()) DrumPadScreen.last.dismiss();
            if (DrumGridScreen.last != null && DrumGridScreen.last.isShowing()) DrumGridScreen.last.dismiss();
            if (AudioMix.tracks.isEmpty()) return;
            new AlertDialog.Builder(app).setTitle(AudioMix.CLEAR).setMessage("Take every track out of the AudioMixer?"
                    + (AudioMix.project.isEmpty() ? "" : "\nThe project " + AudioMix.project + " keeps what was saved in it."))
                .setPositiveButton("Clear", (d, w) -> {
                    AudioMix.clear();
                    fill[0].run();
                    titled();
                }).setNegativeButton("Cancel", null).show();
        });
        clear.setTag("audiomixer-clear");
        more.addView(mix);
        more.addView(clear, margin(app));
        // Projects: the tracks and their settings saved under a name, and opened again.
        LinearLayout projectRow = app.row();
        TextView saveP = app.pill(AudioMix.SAVE_PROJECT, false, v -> saveProject(app, status));
        saveP.setTag("audiomixer-save-project");
        TextView openP = app.pill(AudioMix.OPEN_PROJECT, false, v -> openProject(app, status, fill[0]));
        openP.setTag("audiomixer-open-project");
        projectRow.addView(saveP);
        projectRow.addView(openP, margin(app));
        col.addView(buttons);
        col.addView(padsRow);
        col.addView(transport);
        col.addView(more);
        col.addView(projectRow);
        col.addView(status);
        col.addView(rows);
        ScrollView scroll = new ScrollView(app);
        scroll.addView(col);
        fill[0].run();
        refill = fill[0];
        // The mixer shown before goes (one of this app's: an earlier app's window is gone with it).
        if (last != null && last.isShowing() && last.getOwnerActivity() == app) {
            try {
                last.dismiss();
            } catch (RuntimeException ignored) {
                // gone already
            }
        }
        last = new AlertDialog.Builder(app).setTitle(title()).setView(scroll).setPositiveButton("Close", null).create();
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
        // Its waveform, with where the playing has got to (▶, Play mix).
        WaveView wave = new WaveView(app, t);
        box.addView(wave, new LinearLayout.LayoutParams(-1, app.dp(44)));
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
                play(l, rr, status, t.name, java.util.Collections.singletonList(t));
            } catch (Exception ex) {
                say(status, "Could not play " + t.name);
            }
        });
        one.setTag("audiomixer-play:" + t.name);
        TextView out = app.pill("✕", false, v -> {
            stop();
            if (TrackRecorder.recording() == t) TrackRecorder.stop();
            if (DrumPads.current != null && DrumPads.current.track == t && DrumPadScreen.last != null) DrumPadScreen.last.dismiss();
            if (DrumPads.current != null && DrumPads.current.track == t && DrumGridScreen.last != null) DrumGridScreen.last.dismiss();
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
        if (t.pads) {
            TextView pads = app.pill(DrumPads.BUTTON, true, v -> {
                stop();
                TrackRecorder.stop();
                DrumPadScreen.show(app, t, () -> name.setText(AudioMix.line(t)));
            });
            pads.setTag("audiomixer-drumpad:" + t.name);
            TextView editor = app.pill(DrumGrid.BUTTON, false, v -> {
                stop();
                TrackRecorder.stop();
                DrumGridScreen.show(app, t, () -> name.setText(AudioMix.line(t)));
            });
            editor.setTag("audiomixer-editor:" + t.name);
            LinearLayout pr = app.row();
            pr.addView(pads);
            pr.addView(editor, margin(app));
            box.addView(pr);
        } else {
            box.addView(recordRow(app, t, name, status));
        }
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

    /** New DrumPad track: a track for the Drumpad window's hits. */
    static void newPads(TextView status, Runnable fill) {
        try {
            AudioMix.Track t = AudioMix.addPads();
            say(status, "Added " + t.name + ": open its " + DrumPads.BUTTON);
        } catch (Exception ex) {
            say(status, "Could not add a track: " + ex.getMessage());
        }
        if (fill != null) fill.run();
    }

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
            say(status, name + " is not a WAV, an MP3, an M4A or a MIDI file");
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

    /** The window's title: AudioMixer · its project's name, when it has one. */
    static String title() {
        return AudioMix.TITLE + (AudioMix.project.isEmpty() ? "" : " \u00b7 " + AudioMix.project);
    }

    static void titled() {
        if (last != null && last.isShowing()) last.setTitle(title());
    }

    /** The tests' name for Save project and project for Open project (instead of asking). */
    static String projectAnswer;

    /** Save project: a name asked (the project's own, or Project N), a project of that name replaced when Yes. */
    static void saveProject(final MainActivity app, final TextView status) {
        if (AudioMix.tracks.isEmpty()) {
            say(status, "No tracks to save");
            return;
        }
        String suggest = AudioMix.project;
        if (suggest.isEmpty()) {
            int n = 1;
            while (AudioMix.projectExists("Project " + n)) n++;
            suggest = "Project " + n;
        }
        if (projectAnswer != null) {
            saveAs(app, status, projectAnswer);
            return;
        }
        final android.widget.EditText name = new android.widget.EditText(app);
        name.setSingleLine(true);
        name.setText(suggest);
        name.setSelectAllOnFocus(true);
        name.setTag("audiomixer-project-name");
        new AlertDialog.Builder(app).setTitle(AudioMix.SAVE_PROJECT).setMessage("The tracks and their settings (volume, gain, Mute, Solo) under this name:")
            .setView(name).setPositiveButton("Save", (d, w) -> {
                final String n = name.getText().toString().trim();
                if (n.isEmpty()) return;
                if (AudioMix.projectExists(n) && !AudioMix.projectFolderName(n).equals(AudioMix.project)) {
                    new AlertDialog.Builder(app).setTitle(AudioMix.SAVE_PROJECT).setMessage("A project " + n + " is saved already. Replace it?")
                        .setPositiveButton("Replace", (d2, w2) -> saveAs(app, status, n)).setNegativeButton("Cancel", null).show();
                } else saveAs(app, status, n);
            }).setNegativeButton("Cancel", null).show();
    }

    static void saveAs(MainActivity app, TextView status, String name) {
        try {
            AudioMix.saveProject(name);
            say(status, "Saved the project " + AudioMix.project + " (" + AudioMix.tracks.size() + (AudioMix.tracks.size() == 1 ? " track)" : " tracks)"));
            titled();
        } catch (Exception ex) {
            say(status, "Could not save the project: " + ex.getMessage());
        }
    }

    /** Open project: the saved ones listed (newest first); the mixer's tracks replaced by the one picked. */
    static void openProject(final MainActivity app, final TextView status, final Runnable fill) {
        final java.util.List<String> names = AudioMix.projects();
        if (names.isEmpty()) {
            say(status, "No projects saved yet: " + AudioMix.SAVE_PROJECT + " keeps the tracks under a name");
            return;
        }
        if (projectAnswer != null) {
            openNamed(status, fill, projectAnswer);
            return;
        }
        new AlertDialog.Builder(app).setTitle(AudioMix.OPEN_PROJECT).setItems(names.toArray(new String[0]), (d, w) -> {
            final String n = names.get(w);
            if (AudioMix.tracks.isEmpty()) {
                openNamed(status, fill, n);
                return;
            }
            new AlertDialog.Builder(app).setTitle(AudioMix.OPEN_PROJECT)
                .setMessage("Open " + n + "? It takes the place of the AudioMixer's tracks now: what is not saved in a project is lost.")
                .setPositiveButton("Open", (d2, w2) -> openNamed(status, fill, n)).setNegativeButton("Cancel", null).show();
        }).setNegativeButton("Cancel", null).show();
    }

    static void openNamed(TextView status, Runnable fill, String name) {
        stop();
        TrackRecorder.stop();
        try {
            AudioMix.openProject(name);
            fill.run();
            titled();
            say(status, "Opened the project " + AudioMix.project + " (" + AudioMix.tracks.size() + (AudioMix.tracks.size() == 1 ? " track)" : " tracks)"));
        } catch (Exception ex) {
            say(status, "Could not open the project: " + ex.getMessage());
        }
    }

    /** The last run's question (for the tests). */
    static AlertDialog lastOffer;

    /**
     * A run's sounds held for the mixer (--to_mixer): one into an empty mixer goes in at once; else asked
     * which go in (all ticked), and whether they replace the mixer's tracks or join them. Then the mixer.
     */
    static void offer(final MainActivity app) {
        final java.util.List<JavaRun.FileOut> waiting = MixerOut.takeWaiting();
        if (waiting.isEmpty()) return;
        if (!MixerOut.needsAsking(waiting.size())) {
            addRun(app, waiting, false);
            return;
        }
        int has = AudioMix.tracks.size();
        LinearLayout box = app.col();
        box.setPadding(app.dp(20), app.dp(8), app.dp(20), 0);
        box.addView(app.text(MixerOut.askRun(waiting.size(), has), 14, false));
        final java.util.List<android.widget.CheckBox> ticks = new java.util.ArrayList<android.widget.CheckBox>();
        for (JavaRun.FileOut f : waiting) {
            android.widget.CheckBox c = new android.widget.CheckBox(app);
            c.setText(new File(f.name).getName());
            c.setChecked(true);
            c.setTag("audiomixer-offer-track");
            ticks.add(c);
            box.addView(c);
        }
        ScrollView scroll = new ScrollView(app);
        scroll.addView(box);
        final java.util.function.Function<Boolean, Runnable> go = replace -> () -> {
            java.util.List<JavaRun.FileOut> chosen = new java.util.ArrayList<JavaRun.FileOut>();
            for (int i = 0; i < waiting.size(); i++) if (ticks.get(i).isChecked()) chosen.add(waiting.get(i));
            addRun(app, chosen, replace.booleanValue());
        };
        AlertDialog.Builder b = new AlertDialog.Builder(app).setTitle(MixerOut.ASK_RUN_TITLE).setView(scroll)
            .setNegativeButton(MixerOut.SKIP, (d, w) -> app.setNow("Not added to the AudioMixer"));
        if (has > 0) {
            b.setPositiveButton(MixerOut.REPLACE, (d, w) -> go.apply(Boolean.TRUE).run());
            b.setNeutralButton(MixerOut.ADD_TO, (d, w) -> go.apply(Boolean.FALSE).run());
        } else b.setPositiveButton(MixerOut.ADD_THESE, (d, w) -> go.apply(Boolean.FALSE).run());
        lastOffer = b.show();
    }

    /** The chosen sounds into the mixer (in the background), then the mixer shown. */
    static void addRun(final MainActivity app, final java.util.List<JavaRun.FileOut> chosen, final boolean replace) {
        if (chosen.isEmpty()) {
            app.setNow("Nothing added to the AudioMixer");
            return;
        }
        new Thread(() -> {
            final String said = MixerOut.addChosen(chosen, replace);
            app.handler.post(() -> {
                app.setNow(said.split("\n")[0] + (said.contains("\n") ? " …" : ""));
                show(app);
            });
        }, "pulsekit-mixer-run").start();
    }

    /** The tracks playing now (▶: its own; Play mix: those heard), the frames played, the rows' waveforms. */
    static final java.util.Set<AudioMix.Track> SOUNDING = new java.util.HashSet<AudioMix.Track>();
    static int playFrames;
    static final java.util.List<WaveView> WAVES = new java.util.ArrayList<WaveView>();
    static android.os.Handler tick;

    /** The main thread's handler for the playheads (made again when the main looper is a new one). */
    static android.os.Handler tick() {
        android.os.Looper main = android.os.Looper.getMainLooper();
        if (tick == null || tick.getLooper() != main) tick = new android.os.Handler(main);
        return tick;
    }

    /** Seconds played of what plays now, or -1. */
    static double playedSeconds() {
        AudioTrack t = playing;
        if (t == null) return -1;
        try {
            return (t.getPlaybackHeadPosition() & 0xffffffffL) / (double) AudioMix.RATE;
        } catch (Exception ex) {
            return -1;
        }
    }

    /** The playheads moved about 25 times a second while something plays; at its end they go. */
    static final Runnable TICKER = new Runnable() {
        public void run() {
            double at = playedSeconds();
            if (at < 0 || at * AudioMix.RATE >= playFrames) SOUNDING.clear();
            for (WaveView w : WAVES) w.invalidate();
            if (!SOUNDING.isEmpty()) tick().postDelayed(this, 40);
        }
    };

    /** A track's waveform (WAV or MP3 alike: the mixer keeps each as a WAV) and the playhead on it. */
    static final class WaveView extends android.view.View {
        final AudioMix.Track t;
        float[] peaks;
        int loading = -1;
        final android.graphics.Paint wave = new android.graphics.Paint(), head = new android.graphics.Paint(), base = new android.graphics.Paint();

        WaveView(MainActivity app, AudioMix.Track t) {
            super(app);
            this.t = t;
            setTag("audiomixer-wave:" + t.name);
            wave.setColor(0xFF8FA89A);
            head.setColor(0xFFF2D06B);
            head.setStrokeWidth(app.dp(2));
            base.setColor(0x33FFFFFF);
            WAVES.add(this);
        }

        /** The playhead's place (0 to 1) on this track now, or -1 when it is not playing. */
        double playhead() {
            if (!SOUNDING.contains(t) || t.seconds <= 0) return -1;
            double at = playedSeconds();
            return at < 0 || at > t.seconds ? -1 : at / t.seconds;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            int w = getWidth(), h = getHeight();
            int cols = Math.max(1, w / 3);
            if ((peaks == null || peaks.length != cols) && loading != cols) {
                // Read in the background: a long WAV would hold up the screen.
                loading = cols;
                final int want = cols;
                new Thread(() -> {
                    final float[] p = AudioMix.peaks(t, want);
                    // Through the view: a window closed meanwhile takes nothing more.
                    post(() -> {
                        peaks = p;
                        invalidate();
                    });
                }, "pulsekit-mixer-wave").start();
            }
            float mid = h / 2f;
            c.drawRect(0, mid - 0.5f, w, mid + 0.5f, base);
            if (peaks != null) {
                float cw = w / (float) peaks.length;
                for (int i = 0; i < peaks.length; i++) {
                    float a = Math.max(0.5f, peaks[i] * (mid - 2));
                    c.drawRect(i * cw, mid - a, i * cw + Math.max(1f, cw - 1), mid + a, wave);
                }
            }
            double at = playhead();
            if (at >= 0) c.drawLine((float) (at * w), 0, (float) (at * w), h, head);
        }
    }

    /** The open screen shows the tracks again (a run added some). */
    static void refresh() {
        if (last != null && last.isShowing() && refill != null) refill.run();
    }

    static void playMix(final MainActivity app, final TextView status) {
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        new Thread(() -> {
            try {
                float[][] m = AudioMix.mix(null);
                final java.util.List<AudioMix.Track> heard = AudioMix.heard();
                main.post(() -> play(m[0], m[1], status, "the mix", heard));
            } catch (Exception ex) {
                main.post(() -> say(status, "Could not mix: " + ex.getMessage()));
            }
        }, "pulsekit-mixer-play").start();
    }

    /** Plays a sound that is no mixer track's (a pad, a kit's sound): no playhead. */
    static void play(float[] l, float[] r, TextView status, String what) {
        play(l, r, status, what, java.util.Collections.<AudioMix.Track>emptyList());
    }

    static void play(float[] l, float[] r, TextView status, String what, java.util.Collection<AudioMix.Track> which) {
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
            SOUNDING.addAll(which);
            playFrames = l.length;
            tick().removeCallbacks(TICKER);
            tick().post(TICKER);
            say(status, "Playing " + what);
        } catch (Exception ex) {
            say(status, "No audio output for " + what);
        }
    }

    static void stop() {
        AudioTrack t = playing;
        playing = null;
        if (tick != null) tick.removeCallbacks(TICKER);
        if (!SOUNDING.isEmpty()) {
            SOUNDING.clear();
            for (WaveView w : WAVES) w.invalidate();
        }
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
        // In the mixer's own folder: java.io.tmpdir can name a program run's folder that is gone by now.
        File at = AudioMix.dir;
        if (at != null && !at.isDirectory()) at.mkdirs();
        File tmp = File.createTempFile("pulsekit-mixer", ".mp3", at != null && at.isDirectory() ? at : null);
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
