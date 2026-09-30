package pulsekit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioTrack;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static pulsekit.MainActivity.*;

/** Transport, the audio mixer thread, drum voices, drum sets and pads. */
final class Playback {
    final MainActivity app;

    Playback(MainActivity app) {
        this.app = app;
    }

    /** Builds the Pads page: drum sets, Live/Silent/Match Original, and the pad grid. */
    void buildPadsPane(FrameLayout frameLayout) {
        LinearLayout.LayoutParams layoutParams;
        TextView textView;
        LinearLayout linearLayout;
        TextView textView3;
        app.padsPane = app.col();
        app.padsPane.setVisibility(8);
        app.padsPane.addView((View)app.text("Drum sets", 11, true));
        HorizontalScrollView horizontalScrollView3 = new HorizontalScrollView((Context)app);
        horizontalScrollView3.setHorizontalScrollBarEnabled(false);
        app.drumSetBar = new FlowLayout((Context)app, app.dp(6), app.dp(6));
        app.drumSetBar.setSingleLine(true);
        horizontalScrollView3.addView((View)app.drumSetBar);
        app.padsPane.addView((View)horizontalScrollView3);
        LinearLayout linearLayout13 = app.row();
        textView3 = app.outline("Live", false, view -> {
            app.livePads = !app.livePads;
            app.paintOutline((TextView)view, app.livePads);
            app.setNow(app.livePads ? "Pads write the pattern" : "Hold a pad");
        });
        linearLayout13.addView((View)textView3);
        linearLayout13.addView((View)app.outline("Silent", false, view -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < 16; ++j) {
                    app.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(app.lens);
            app.styleLibrary.syncBuiltinFill();
            app.gridEditor.refreshGrid();
        }));
        app.padMatchAll = app.outline("Match Original", true, view -> this.matchWholeOriginal());
        app.padMatchAll.setVisibility(8);
        linearLayout13.addView((View)app.padMatchAll);
        TextView textView15 = app.text("  Tap Match Original for the whole kit", 11, false);
        textView15.setTextColor(SUBTLE);
        linearLayout13.addView((View)textView15);
        app.padsPane.addView((View)linearLayout13);
        ScrollView scrollView = new ScrollView((Context)app);
        LinearLayout padGrid = app.col();
        for (int i = 0; i < 3; ++i) {
            linearLayout = app.row();
            for (int j = 0; j < 4; ++j) {
                int n7 = i * 4 + j;
                if (n7 >= Engine.TRACK_ID.length) {
                    linearLayout.addView(new View((Context)app), (ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, app.dp(96), 1.0f));
                    continue;
                }
                int n8 = n7;
                app.padBtns[n7] = textView = app.pad(Engine.TRACK_SHORT[n7], Engine.TRACK_LABEL[n7], view -> {
                    this.bang(n8, 110);
                    this.flashPad(n8);
                    if (app.livePads) {
                        int step = app.playhead >= 0 ? app.playhead : 0;
                        app.cells[n8][step] = app.cells[n8][step] > 0 ? 0 : 100;
                        if (app.cells[n8][step] <= 0) {
                            app.lens[n8][step] = 0;
                        }
                        app.styleLibrary.syncBuiltinFill();
                        app.gridEditor.refreshGrid();
                    }
                });
                textView.setOnLongClickListener(view -> {
                    app.padTarget = n8;
                    this.showPadMenu(n8);
                    return true;
                });
                layoutParams = new LinearLayout.LayoutParams(0, app.dp(96), 1.0f);
                layoutParams.setMargins(app.dp(4), app.dp(4), app.dp(4), app.dp(4));
                textView.setLayoutParams((ViewGroup.LayoutParams)layoutParams);
                linearLayout.addView((View)textView);
            }
            padGrid.addView((View)linearLayout);
        }
        scrollView.addView((View)padGrid);
        app.padsPane.addView((View)scrollView, (ViewGroup.LayoutParams)app.flexFill());
        frameLayout.addView((View)app.padsPane);
        this.refreshDrumSets();
    }

    int drumSetIndex = 0;

    short[] oneShot;

    int oneShotPos = -1;

    float oneShotGain;

    int step;

    static final int MIX_CHUNK = 256;

    final int[] mixGain = new int[Engine.NOTES.length];

    Runnable pendingDouble;

    volatile boolean mixNeedHit;

    volatile boolean mixReset;

    int mixAcc;

    final Random mixRng = new Random();

    void toggle() {
        if (app.playing) {
            this.stop();
            return;
        }
        this.ensureAudio();
        app.songPlay = "song".equals(app.view) && !app.songEditor.activeSong().isEmpty();
        this.step = 0;
        app.songIndex = 0;
        app.songLoop = 0;
        app.barLoop = 0;
        app.playhead = 0;
        app.shownHead = -1;
        if (app.songPlay) {
            this.applyPart(app.songEditor.activeSong().get(0));
        }
        app.playBtn.setText((CharSequence)"Pause");
        this.mixReset = true;
        this.mixNeedHit = true;
        app.playing = true;
        app.gridEditor.refreshGrid();
    }

    void stop() {
        app.playing = false;
        this.mixNeedHit = false;
        app.playhead = -1;
        app.songPlay = false;
        if (app.playBtn != null) {
            app.playBtn.setText((CharSequence)"Play");
        }
        app.gridEditor.refreshGrid();
        app.songEditor.refreshSong();
    }

    int stepFrames(int n, int n2) {
        int n3 = (int)Math.round(1323000.0 / (double)Math.max(40, n2) / 4.0);
        if ((n & 1) == 1) {
            n3 = (int)((long)n3 + Math.round((double)(n3 * app.swing()) / 100.0));
        }
        return Math.max(48, n3);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void audioTick() {
        int n;
        List<Engine.Part> list;
        boolean bl;
        if (!app.playing) {
            return;
        }
        int n2 = this.step;
        int[][] nArray = app.cells;
        boolean bl2 = bl = !app.songPlay && app.fillLast && app.barLoop == app.bars - 1;
        if (bl) {
            nArray = app.fillPat;
        }
        Engine.Part part = null;
        List<Engine.Part> list2 = list = app.songPlay ? app.songEditor.activeSong() : null;
        if (app.songPlay && list != null && app.songIndex < list.size()) {
            part = list.get(app.songIndex);
            nArray = part.cells;
        }
        int n3 = app.human();
        Object object = app.mixLock;
        synchronized (object) {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                if (app.muted || app.mutes[i]) continue;
                int n4 = n = n2 < nArray[i].length ? nArray[i][n2] : 0;
                if (n <= 0) continue;
                n = Engine.accentVel(n, n2 < app.accents.length && app.accents[n2]);
                if (n3 > 0) {
                    n = Engine.clamp(n + this.mixRng.nextInt(n3 + 1) - n3 / 2, 1, 127);
                }
                app.mixPos[i] = 0;
                this.mixGain[i] = n;
            }
        }
        int n5 = n2;
        Engine.Part part2 = part;
        this.step = n2 + 1;
        if (this.step >= app.gridEditor.currentSteps()) {
            this.step = 0;
            if (app.songPlay) {
                Engine.Part part3;
                List<Engine.Part> list3 = app.songEditor.activeSong();
                Engine.Part part4 = part3 = app.songIndex < list3.size() ? list3.get(app.songIndex) : null;
                if (part3 == null) {
                    app.playing = false;
                    app.handler.post(this::stop);
                    return;
                }
                ++app.songLoop;
                if (app.songLoop >= Math.max(1, part3.repeats)) {
                    app.songLoop = 0;
                    ++app.songIndex;
                    if (app.songIndex >= list3.size()) {
                        app.playing = false;
                        app.handler.post(this::stop);
                        return;
                    }
                    n = 1;
                } else {
                    n = 0;
                }
            } else {
                app.barLoop = (app.barLoop + 1) % Math.max(1, app.bars);
                n = 0;
            }
        } else {
            n = 0;
        }
        boolean advanced = n != 0;
        app.handler.post(() -> this.audioTickAction94(n5, part2, advanced));
    }

    void applyPart(Engine.Part part) {
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            System.arraycopy(part.cells[i], 0, app.cells[i], 0, 32);
        }
        app.tsNum = Engine.clampTsNum(part.tsNum);
        app.tsDen = Engine.clampTsDen(part.tsDen);
        if (app.tsNumField != null) {
            app.tsNumField.setText((CharSequence)Integer.toString(app.tsNum));
        }
        if (app.tsDenField != null) {
            app.tsDenField.setText((CharSequence)Integer.toString(app.tsDen));
        }
        app.gridEditor.applySteps(Engine.clampSteps(part.steps), false);
        if (app.bpmBar != null) {
            app.bpmBar.setVal(Engine.clampBpm(part.bpm));
        }
        app.bpmLabel.setText((CharSequence)Integer.toString(part.bpm));
        app.gridEditor.refreshGrid();
    }

    void generate() {
        app.styleLibrary.loadStyle(app.style, true);
        Random random = new Random();
        double d = 0.04 + (double)app.density() * 0.02;
        for (int i = 0; i < 6; ++i) {
            for (int j = 0; j < app.steps; ++j) {
                if (!(random.nextDouble() < d)) continue;
                if (app.cells[i][j] > 0 && random.nextBoolean()) {
                    app.cells[i][j] = 0;
                    continue;
                }
                if (app.cells[i][j] != 0) continue;
                app.cells[i][j] = random.nextBoolean() ? 64 : 100;
            }
        }
        if (app.pluginGhostHats) {
            Engine.ghostHats(app.cells, random);
        }
        app.styleLibrary.syncBuiltinFill();
        app.gridEditor.refreshGrid();
    }

    void ensureAudio() {
        if (app.track != null && app.mixRun) {
            return;
        }
        this.buildVoices();
        this.startMixer();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void buildVoices() {
        int n;
        short[][] sArray = AudioIo.buildVoices(22050);
        Object object = app.mixLock;
        synchronized (object) {
            for (n = 0; n < app.voices.length; ++n) {
                if (app.voices[n] != null) continue;
                app.voices[n] = sArray[n];
            }
        }
        if (app.track == null) {
            int n2 = AudioTrack.getMinBufferSize((int)22050, (int)4, (int)2);
            n = Math.max(n2, 512);
            app.track = new AudioTrack(3, 22050, 4, 2, n, 1);
            app.track.play();
        }
    }

    void startMixer() {
        if (app.mixThread != null && app.mixThread.isAlive()) {
            return;
        }
        app.mixRun = true;
        app.mixThread = new Thread(this::mixLoop, "pulsekit-mix");
        app.mixThread.start();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void mixLoop() {
        Process.setThreadPriority((int)-16);
        short[] sArray = new short[256];
        while (app.mixRun) {
            int n;
            block29: {
                if (this.mixReset) {
                    this.mixReset = false;
                    this.mixAcc = 0;
                    this.mixNeedHit = true;
                }
                if (app.playing && this.mixNeedHit) {
                    this.mixNeedHit = false;
                    this.audioTick();
                }
                Arrays.fill(sArray, (short)0);
                Object object = app.mixLock;
                synchronized (object) {
                    int n2;
                    for (n = 0; n < app.voices.length; ++n) {
                        int n3 = app.mixPos[n];
                        if (n3 < 0) continue;
                        short[] sArray2 = this.voiceAtLocked(n);
                        if (sArray2 == null || n3 >= sArray2.length) {
                            app.mixPos[n] = -1;
                            continue;
                        }
                        float f = (float)this.mixGain[n] / 127.0f;
                        if (n >= 8) {
                            f *= 1.4f;
                        }
                        n2 = Math.min(256, sArray2.length - n3);
                        for (int i = 0; i < n2; ++i) {
                            int n4 = sArray[i] + (int)((float)sArray2[n3 + i] * f);
                            if (n4 > Short.MAX_VALUE) {
                                n4 = Short.MAX_VALUE;
                            } else if (n4 < Short.MIN_VALUE) {
                                n4 = Short.MIN_VALUE;
                            }
                            sArray[i] = (short)n4;
                        }
                        app.mixPos[n] = (n3 += n2) >= sArray2.length ? -1 : n3;
                    }
                    if (this.oneShot != null && this.oneShotPos >= 0) {
                        n = this.oneShotPos;
                        if (n >= this.oneShot.length) {
                            this.oneShotPos = -1;
                        } else {
                            float f = this.oneShotGain;
                            int n5 = Math.min(256, this.oneShot.length - n);
                            for (int i = 0; i < n5; ++i) {
                                n2 = sArray[i] + (int)((float)this.oneShot[n + i] * f);
                                if (n2 > Short.MAX_VALUE) {
                                    n2 = Short.MAX_VALUE;
                                } else if (n2 < Short.MIN_VALUE) {
                                    n2 = Short.MIN_VALUE;
                                }
                                sArray[i] = (short)n2;
                            }
                            this.oneShotPos = (n += n5) >= this.oneShot.length ? -1 : n;
                        }
                    }
                }
                AudioTrack audioTrack = app.track;
                if (audioTrack == null) {
                    try {
                        Thread.sleep(10L);
                        continue;
                    }
                    catch (InterruptedException interruptedException) {
                        break;
                    }
                }
                try {
                    n = audioTrack.write(sArray, 0, 256);
                    if (n > 0) break block29;
                    Thread.sleep(4L);
                }
                catch (Exception exception) {
                    break;
                }
            }
            if (app.playing) {
                this.mixAcc += 256;
                n = app.bpm();
                List<Engine.Part> list = app.songEditor.activeSong();
                if (app.songPlay && app.songIndex < list.size()) {
                    n = list.get((int)app.songIndex).bpm;
                }
                int n6 = this.stepFrames(Math.max(0, this.step == 0 ? app.gridEditor.currentSteps() - 1 : this.step - 1), n);
                while (app.playing && this.mixAcc >= n6) {
                    this.mixAcc -= n6;
                    this.audioTick();
                    n = app.bpm();
                    list = app.songEditor.activeSong();
                    if (app.songPlay && app.songIndex < list.size()) {
                        n = list.get((int)app.songIndex).bpm;
                    }
                    n6 = this.stepFrames(Math.max(0, this.step == 0 ? 15 : this.step - 1), n);
                }
                continue;
            }
            this.mixAcc = 0;
        }
    }

    void bang(int n, int n2) {
        if (app.muted || app.mutes[n] || n < 0 || n >= app.mixPos.length) {
            return;
        }
        this.ensureAudio();
        int n3 = Engine.track("dkick");
        int n4 = Engine.track("kick");
        if (n == n3) {
            this.triggerMix(n3, n2);
            if (this.pendingDouble != null) {
                app.handler.removeCallbacks(this.pendingDouble);
            }
            int n5 = n2;
            this.pendingDouble = () -> {
                if (app.muted || app.mutes[n3]) {
                    return;
                }
                this.triggerMix(n4, n5);
            };
            app.handler.postDelayed(this.pendingDouble, (long)Engine.doubleKickDelayMs(app.bpm(), app.tsDen));
            return;
        }
        this.triggerMix(n, n2);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void triggerMix(int n, int n2) {
        Object object = app.mixLock;
        synchronized (object) {
            app.mixPos[n] = 0;
            this.mixGain[n] = Engine.clamp(n2, 1, 127);
        }
    }

    void flashPad(int n) {
        if (app.padBtns[n] == null) {
            return;
        }
        app.padBtns[n].setBackground((Drawable)app.round(HIT, 12));
        app.handler.postDelayed(() -> {
            if (app.padBtns[n] != null) {
                app.padBtns[n].setBackground((Drawable)app.round(ELEV, 12));
            }
        }, 90L);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void applySf2(short[][] sArray) {
        this.ensureAudio();
        int n = 0;
        Object object = app.mixLock;
        synchronized (object) {
            for (int i = 0; i < app.voices.length; ++i) {
                if (sArray[i] == null) continue;
                app.voices[i] = sArray[i];
                app.mixPos[i] = -1;
                ++n;
            }
        }
        this.fillMissingVoices();
        app.setNow("SoundFont \u00b7 " + n + " drums");
        Toast.makeText((Context)app, (CharSequence)("SoundFont \u00b7 " + n + " drums"), (int)0).show();
    }

    Engine.DrumSet activeDrumSet() {
        if (app.drumSets.isEmpty()) {
            app.drumSets.add(Engine.DrumSet.originalSet());
        }
        if (this.drumSetIndex < 0 || this.drumSetIndex >= app.drumSets.size()) {
            this.drumSetIndex = 0;
        }
        return app.drumSets.get(this.drumSetIndex);
    }

    short[] voiceAtLocked(int n) {
        int n2 = Engine.soundTrack(n);
        Engine.DrumSet drumSet = this.activeDrumSet();
        if (drumSet.original || drumSet.matchOrig[n2]) {
            return app.voices[n2];
        }
        return n2 >= 0 && n2 < drumSet.samples.length ? drumSet.samples[n2] : null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    short[][] mixVoices() {
        this.fillMissingVoices();
        Engine.DrumSet drumSet = this.activeDrumSet();
        short[][] sArrayArray = new short[app.voices.length][];
        Object object = app.mixLock;
        synchronized (object) {
            for (int i = 0; i < Engine.TRACK_ID.length && i < sArrayArray.length; ++i) {
                int n = Engine.soundTrack(i);
                sArrayArray[i] = drumSet.original || drumSet.matchOrig[n] ? app.voices[n] : drumSet.samples[n];
            }
        }
        return sArrayArray;
    }

    void refreshDrumSets() {
        if (app.drumSetBar == null) {
            return;
        }
        app.drumSetBar.removeAllViews();
        for (int i = 0; i < app.drumSets.size(); ++i) {
            int n = i;
            Engine.DrumSet drumSet = app.drumSets.get(i);
            TextView textView = app.pill(drumSet.name, n == this.drumSetIndex, view -> {
                this.drumSetIndex = n;
                this.refreshDrumSets();
                this.refreshPadLabels();
                app.setNow(drumSet.original ? "Original kit" : drumSet.name + " \u00b7 " + drumSet.foundCount() + " pads");
            });
            if (!drumSet.original) {
                textView.setOnLongClickListener(view -> {
                    new AlertDialog.Builder((Context)app).setItems(new CharSequence[]{"Match Original", "Match empty from Original", "Delete set"}, (dialogInterface, n2) -> {
                        if (n2 == 0) {
                            this.drumSetIndex = n;
                            this.matchWholeOriginal();
                        } else if (n2 == 1) {
                            this.drumSetIndex = n;
                            for (int t = 0; t < Engine.TRACK_ID.length; ++t) {
                                if (drumSet.samples[t] != null) continue;
                                drumSet.matchOrig[t] = true;
                            }
                            this.refreshDrumSets();
                            this.refreshPadLabels();
                            Toast.makeText((Context)app, (CharSequence)"Empty pads match Original", (int)0).show();
                        } else if (n > 0 && n < app.drumSets.size()) {
                            app.drumSets.remove(n);
                            if (this.drumSetIndex >= app.drumSets.size()) {
                                this.drumSetIndex = 0;
                            }
                            this.refreshDrumSets();
                            this.refreshPadLabels();
                            Toast.makeText((Context)app, (CharSequence)"Drum set removed", (int)0).show();
                        }
                    }).show();
                    return true;
                });
            }
            app.drumSetBar.addView((View)textView);
        }
        app.drumSetBar.requestLayout();
        this.refreshPadLabels();
        Engine.DrumSet drumSet = this.activeDrumSet();
        if (app.padMatchAll != null) {
            int n = !drumSet.original ? 1 : 0;
            app.padMatchAll.setVisibility(n != 0 ? 0 : 8);
            if (n != 0) {
                boolean bl = true;
                for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                    if (drumSet.matchOrig[i]) continue;
                    bl = false;
                    break;
                }
                app.padMatchAll.setText((CharSequence)(bl ? "This set" : "Match Original"));
                app.paintOutline(app.padMatchAll, !bl);
            }
        }
    }

    void matchWholeOriginal() {
        int n;
        Engine.DrumSet drumSet = this.activeDrumSet();
        if (drumSet.original) {
            return;
        }
        boolean bl = true;
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            if (drumSet.matchOrig[n]) continue;
            bl = false;
            break;
        }
        for (n = 0; n < Engine.TRACK_ID.length; ++n) {
            drumSet.matchOrig[n] = !bl;
        }
        this.refreshDrumSets();
        this.refreshPadLabels();
        Toast.makeText((Context)app, (CharSequence)(!bl ? "Whole set matches Original" : "Using this set's sounds"), (int)0).show();
    }

    void refreshPadLabels() {
        Engine.DrumSet drumSet = this.activeDrumSet();
        for (int i = 0; i < app.padBtns.length; ++i) {
            if (app.padBtns[i] == null) continue;
            String string = Engine.TRACK_LABEL[i];
            if (!drumSet.original) {
                string = drumSet.matchOrig[i] ? "Original" : (drumSet.samples[i] != null ? "From song" : "Empty");
            }
            app.padBtns[i].setText((CharSequence)(Engine.TRACK_SHORT[i] + "\n" + string));
        }
    }

    void showPadMenu(int n) {
        Engine.DrumSet drumSet = this.activeDrumSet();
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("Load WAV / MP3");
        if (!drumSet.original) {
            arrayList.add(drumSet.matchOrig[n] ? "Use this set's sound" : "Match Original");
            if (drumSet.samples[n] != null) {
                arrayList.add("Remove sample");
            }
        }
        CharSequence[] charSequenceArray = arrayList.toArray(new String[0]);
        new AlertDialog.Builder((Context)app).setItems(charSequenceArray, (arg_0, arg_1) -> this.showPadMenuAction147((String[])charSequenceArray, n, drumSet, arg_0, arg_1)).show();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    byte[] encodeWav() {
        return AudioIo.encodeWav(this.mixPcm(), 22050);
    }

    short[] mixPcm() {
        this.ensureAudio();
        this.fillMissingVoices();
        return AudioIo.mix(app.cells, app.fillPat, app.fillLast, app.mutes, this.mixVoices(), app.bpm(), app.bars, 22050);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void fillMissingVoices() {
        short[][] sArray = AudioIo.buildVoices(22050);
        Object object = app.mixLock;
        synchronized (object) {
            for (int i = 0; i < app.voices.length; ++i) {
                if (app.voices[i] != null) continue;
                app.voices[i] = sArray[i];
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private /* synthetic */ void showPadMenuAction147(String[] stringArray, int n, Engine.DrumSet drumSet, DialogInterface dialogInterface, int n2) {
        String string = stringArray[n2];
        if ("Load WAV / MP3".equals(string)) {
            app.padTarget = n;
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("audio/*");
            app.startActivityForResult(intent, 13);
        } else if ("Match Original".equals(string)) {
            drumSet.matchOrig[n] = true;
            this.refreshPadLabels();
            Toast.makeText((Context)app, (CharSequence)(Engine.TRACK_LABEL[n] + " \u00b7 Original"), (int)0).show();
        } else if ("Use this set's sound".equals(string)) {
            drumSet.matchOrig[n] = false;
            this.refreshPadLabels();
            Toast.makeText((Context)app, (CharSequence)(Engine.TRACK_LABEL[n] + " \u00b7 this set"), (int)0).show();
        } else if ("Remove sample".equals(string)) {
            Object object = app.mixLock;
            synchronized (object) {
                drumSet.samples[n] = null;
            }
            this.refreshPadLabels();
        }
    }

    private /* synthetic */ void audioTickAction94Base(int n, Engine.Part part, boolean bl) {
        if (!app.playing) {
            return;
        }
        app.playhead = n;
        app.gridEditor.refreshPlayhead();
        if (part != null && bl) {
            Engine.Part part2;
            List<Engine.Part> list = app.songEditor.activeSong();
            Engine.Part part3 = part2 = list.isEmpty() ? null : list.get(Math.min(app.songIndex, list.size() - 1));
            if (part2 != null) {
                this.applyPart(part2);
                app.setNow(part2.name + "  " + part2.bpm + " BPM");
            }
            app.songEditor.refreshSong();
        } else if (part != null && n == 0) {
            app.setNow(part.name + "  " + part.bpm + " BPM");
        }
    }

    void audioTickAction94(int n, Engine.Part part, boolean bl) {
        this.audioTickAction94Base(n, part, bl);
        app.songEditor.afterRefreshSong();
    }
}
