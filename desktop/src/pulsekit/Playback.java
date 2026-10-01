package pulsekit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.font.TextAttribute;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import pulsekit.AudioIo;
import pulsekit.Engine;

import static pulsekit.Pulsekit.*;

/** Transport and the MIDI sequencer, preview, drum sets, pads and pad samples. */
final class Playback {
    final Pulsekit app;

    Playback(Pulsekit app) {
        this.app = app;
    }

    final JButton[] padBtns = new JButton[Engine.TRACK_ID.length];

    int drumSetIndex = 0;

    JPanel drumSetBar;

    JButton padMatchAll;

    Synthesizer synth;

    Timer doubleKickTimer;

    Timer playheadTimer;

    JPanel buildPads() {
        JPanel jPanel = new JPanel(new BorderLayout(8, 8));
        jPanel.setOpaque(false);
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        this.drumSetBar = new ChipStrip();
        this.drumSetBar.setAlignmentX(0.0f);
        north.add(this.drumSetBar);
        JPanel jPanel2 = new JPanel(new FlowLayout(0, 6, 4));
        jPanel2.setOpaque(false);
        jPanel2.setAlignmentX(0.0f);
        JButton jButton = app.outline("Live", false);
        jButton.addActionListener(actionEvent -> {
            app.livePads = !app.livePads;
            app.paintOutline(jButton, app.livePads);
            app.setNow(app.livePads ? "Pads write the pattern" : "Tap a pad");
        });
        JButton jButton2 = app.outline("Silent", false);
        jButton2.addActionListener(actionEvent -> {
            for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
                for (int j = 0; j < app.steps; ++j) {
                    app.cells[i][j] = 0;
                }
            }
            Engine.zeroCells(app.lens);
            app.styleLibrary.syncBuiltinFill();
            app.gridEditor.refreshGrid();
            app.setNow("Silent pattern");
        });
        jPanel2.add(jButton);
        jPanel2.add(jButton2);
        this.padMatchAll = app.outline("Match Original", true);
        this.padMatchAll.setVisible(false);
        this.padMatchAll.addActionListener(e -> this.matchWholeOriginal());
        jPanel2.add(this.padMatchAll);
        JLabel jLabel = new JLabel("  Tap Match Original for the whole kit");
        jLabel.setForeground(SUBTLE);
        jPanel2.add(jLabel);
        north.add(jPanel2);
        jPanel.add((Component)north, "North");
        JPanel jPanel3 = new JPanel(new GridLayout(3, 4, 10, 10));
        jPanel3.setOpaque(false);
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
            final int n = i;
            JButton jButton3 = app.action(Engine.TRACK_SHORT[i] + "  " + Engine.TRACK_LABEL[i], ELEV, FG);
            jButton3.setPreferredSize(new Dimension(120, 88));
            jButton3.addActionListener(actionEvent -> {
                this.preview(n);
                this.flashPad(n);
                if (app.livePads) {
                    int n2 = app.playhead >= 0 ? app.playhead : 0;
                    app.cells[n][n2] = app.cells[n][n2] > 0 ? 0 : 100;
                    if (app.cells[n][n2] <= 0) app.lens[n][n2] = 0;
                    app.styleLibrary.syncBuiltinFill();
                    app.gridEditor.refreshGrid();
                }
            });
            app.onChipMenu(jButton3, () -> this.padMenu(n));
            this.padBtns[i] = jButton3;
            jPanel3.add(jButton3);
        }
        jPanel.add((Component)jPanel3, "Center");
        this.refreshDrumSets();
        return jPanel;
    }

    void applyMute() {
        if (this.synth == null) {
            return;
        }
        try {
            MidiChannel midiChannel = this.synth.getChannels()[9];
            if (midiChannel != null) {
                midiChannel.setMute(app.muted);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    void openMidi() {
        try {
            this.synth = MidiSystem.getSynthesizer();
            this.synth.open();
            app.sequencer = MidiSystem.getSequencer(false);
            app.sequencer.getTransmitter().setReceiver(this.synth.getReceiver());
            app.sequencer.open();
            app.sequencer.setTempoInBPM(app.bpm());
            this.applyMute();
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not open the computer MIDI synth.\n" + exception.getMessage());
        }
    }

    int accentVel(int n, int n2) {
        int n3;
        if (n <= 0) {
            return 0;
        }
        if (app.accents[n2] && n < 127) {
            n = Math.min(127, n + 18);
        }
        if ((n3 = app.humanBar.getVal()) > 0) {
            n = Engine.clamp(n + (int)(Math.random() * (double)(n3 + 1)) - n3 / 2, 1, 127);
        }
        return n;
    }

    Sequence sequenceFromCells(int[][] nArray, int n) throws Exception {
        Sequence sequence = new Sequence(0.0f, 480);
        Track track = sequence.createTrack();
        int n2 = 120;
        int n3 = (int)((double)n2 * ((double)app.swingBar.getVal() / 100.0));
        int n4 = Math.max(1, app.bars);
        int steps = app.steps;
        for (int i = 0; i < n4; ++i) {
            // The Fillern's fill plays in the last bar, or in the first when it replaces the pattern's start.
            int fillBar = Engine.FILLERN_START.equals(app.styleLibrary.fillernModeOf(app.styleLibrary.currentPatternKey())) ? 0 : n4 - 1;
            boolean last = app.fillLast && i == fillBar;
            int n5 = i * steps * n2;
            for (int j = 0; j < Engine.TRACK_ID.length; ++j) {
                if (app.mutes[j]) continue;
                for (int k = 0; k < steps; ++k) {
                    int[][] src = nArray;
                    int[][] srcLens = app.lens;
                    int idx = k;
                    if (last) {
                        int bar = Engine.barSteps(app.tsNum, app.tsDen);
                        if (steps <= bar) {
                            src = app.fillPat;
                            srcLens = app.fillLens;
                        } else if (k >= steps - bar) {
                            src = app.fillPat;
                            srcLens = app.fillLens;
                            idx = k - (steps - bar);
                        }
                    }
                    int n6 = this.accentVel(src[j][idx], k);
                    if (n6 <= 0) continue;
                    int n7 = n5 + k * n2;
                    if (k % 2 == 1) {
                        n7 += n3;
                    }
                    int gate = Engine.gateTicks(Engine.lenAt(srcLens, j, idx));
                    track.add(new MidiEvent(new ShortMessage(144, 9, Engine.NOTES[j], n6), n7));
                    track.add(new MidiEvent(new ShortMessage(128, 9, Engine.NOTES[j], 0), n7 + gate));
                }
            }
        }
        return sequence;
    }

    Sequence sequenceFromSong() throws Exception {
        Sequence sequence = new Sequence(0.0f, 480);
        Track track = sequence.createTrack();
        int n = 120;
        int n2 = 0;
        for (Engine.Part part : app.songEditor.activeSong()) {
            int n3 = (int)Math.round(6.0E7 / (double)Math.max(Engine.MIN_BPM, part.bpm));
            MetaMessage metaMessage = new MetaMessage();
            metaMessage.setMessage(81, new byte[]{(byte)(n3 >> 16 & 0xFF), (byte)(n3 >> 8 & 0xFF), (byte)(n3 & 0xFF)}, 3);
            track.add(new MidiEvent(metaMessage, n2));
            int n4 = Math.max(1, part.repeats);
            int partSteps = Engine.clampSteps(part.steps);
            for (int i = 0; i < n4; ++i) {
                for (int j = 0; j < Engine.TRACK_ID.length; ++j) {
                    if (app.mutes[j]) continue;
                    for (int k = 0; k < partSteps; ++k) {
                        int n5 = this.accentVel(part.cells[j][k], k);
                        if (n5 <= 0) continue;
                        int n6 = n2 + k * n;
                        int gate = Engine.gateTicks(Engine.lenAt(part.lens, j, k));
                        track.add(new MidiEvent(new ShortMessage(144, 9, Engine.NOTES[j], n5), n6));
                        track.add(new MidiEvent(new ShortMessage(128, 9, Engine.NOTES[j], 0), n6 + gate));
                    }
                }
                n2 += partSteps * n;
            }
        }
        return sequence;
    }

    void togglePlay() {
        if (app.sequencer == null) {
            this.openMidi();
            if (app.sequencer == null) {
                return;
            }
        }
        if (app.sequencer.isRunning()) {
            this.stop();
            return;
        }
        app.songPlay = "song".equals(app.view) && !app.songEditor.activeSong().isEmpty();
        if (app.songPlay && app.songPart < 0) app.songPart = 0;
        app.playBtn.setText("Pause");
        this.rebuildAndPlay(false);
    }

    void rebuildAndPlay(boolean bl) {
        try {
            long l = bl && app.sequencer != null ? app.sequencer.getTickPosition() : 0L;
            Sequence sequence = app.songPlay ? this.sequenceFromSong() : this.sequenceFromCells(app.cells, app.bpm());
            app.sequencer.stop();
            app.sequencer.setSequence(sequence);
            app.sequencer.setLoopCount(app.songPlay ? 0 : -1);
            app.sequencer.setLoopStartPoint(0L);
            app.sequencer.setLoopEndPoint(Math.max(1L, sequence.getTickLength()));
            if (!app.songPlay) {
                app.sequencer.setTempoInBPM(app.bpm());
            }
            app.sequencer.setTickPosition(l % Math.max(1L, sequence.getTickLength()));
            app.sequencer.start();
            if (app.songPlay) app.songEditor.paintSongNow();
            if (this.playheadTimer == null) {
                this.playheadTimer = new Timer(40, actionEvent -> {
                    if (app.sequencer == null || !app.sequencer.isRunning()) {
                        return;
                    }
                    long tick = app.sequencer.getTickPosition();
                    List<Engine.Part> playing = app.songEditor.activeSong();
                    int span = app.songPlay && app.songPart >= 0 && app.songPart < playing.size()
                        ? (playing.get(app.songPart).steps == Engine.MAX_STEPS ? Engine.MAX_STEPS : Engine.STEPS)
                        : app.steps;
                    int n = (int)(tick / 120L % (long) Math.max(1, span));
                    if (app.songPlay) {
                        int n2;
                        int n3 = (int)(tick / 120L);
                        int n4 = 0;
                        int n5 = 0;
                        int n6 = 0;
                        while (n6 < playing.size()) {
                            Engine.Part sp = playing.get(n6);
                            n2 = Math.max(1, sp.repeats) * (sp.steps == Engine.MAX_STEPS ? Engine.MAX_STEPS : Engine.STEPS);
                            if (n3 < n4 + n2) {
                                n5 = n6;
                                break;
                            }
                            n4 += n2;
                            n5 = n6++;
                        }
                        if (n5 != app.songPart && n5 < playing.size()) {
                            app.songPart = n5;
                            Engine.Part part = playing.get(n5);
                            for (n2 = 0; n2 < Engine.TRACK_ID.length; ++n2) {
                                System.arraycopy(part.cells[n2], 0, app.cells[n2], 0, Engine.MAX_STEPS);
                            }
                            app.songEditor.refreshSong();
                        } else {
                            app.songEditor.paintSongNow();
                        }
                    }
                    if (n != app.playhead) {
                        app.playhead = n;
                        app.gridEditor.refreshGrid();
                    }
                });
                this.playheadTimer.start();
            }
            if (!bl) {
                app.sequencer.setTickPosition(0L);
            }
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not play: " + exception.getMessage());
        }
    }

    void stop() {
        if (app.sequencer != null && app.sequencer.isRunning()) {
            app.sequencer.stop();
        }
        if (app.sequencer != null) {
            app.sequencer.setTickPosition(0L);
        }
        app.playhead = -1;
        app.songPart = -1;
        app.songPlay = false;
        if (app.playBtn != null) {
            app.playBtn.setText("Play");
        }
        app.gridEditor.refreshGrid();
        app.songEditor.refreshSong();
    }

    void generate() {
        Random random = new Random();
        if (!app.styles.containsKey(app.style)) app.style = "house";
        app.styleLibrary.loadStyle(app.style, true);
        double d = 0.04 + (double)app.densBar.getVal() * 0.02;
        for (int i = 0; i < Engine.TRACK_ID.length; ++i) {
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
        app.projectIo.applyEnabledProcessors(random);
        app.styleLibrary.syncBuiltinFill();
        app.gridEditor.refreshGrid();
        if (app.sequencer != null && app.sequencer.isRunning() && !app.songPlay) {
            this.rebuildAndPlay(true);
        }
    }

    void preview(int n) {
        if (this.synth == null || app.muted || app.mutes[n]) {
            return;
        }
        try {
            MidiChannel midiChannel = this.synth.getChannels()[9];
            if (midiChannel == null) {
                return;
            }
            int kick = Engine.track("kick");
            if (n == Engine.track("dkick")) {
                midiChannel.noteOn(Engine.NOTES[kick], 110);
                if (this.doubleKickTimer != null) {
                    this.doubleKickTimer.stop();
                }
                int delay = Engine.doubleKickDelayMs(app.bpm(), app.tsDen);
                this.doubleKickTimer = new Timer(delay, actionEvent -> {
                    try {
                        if (this.synth == null || app.muted || app.mutes[n]) return;
                        MidiChannel ch = this.synth.getChannels()[9];
                        if (ch != null) ch.noteOn(Engine.NOTES[kick], 110);
                    } catch (Exception ignored) {
                    }
                });
                this.doubleKickTimer.setRepeats(false);
                this.doubleKickTimer.start();
                return;
            }
            midiChannel.noteOn(Engine.NOTES[n], 110);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    void flashPad(int n) {
        if (this.padBtns[n] == null) {
            return;
        }
        this.padBtns[n].setBackground(HIT);
        Timer timer = new Timer(90, actionEvent -> this.padBtns[n].setBackground(ELEV));
        timer.setRepeats(false);
        timer.start();
    }

    Engine.DrumSet activeDrumSet() {
        if (app.drumSets.isEmpty()) app.drumSets.add(Engine.DrumSet.originalSet());
        if (this.drumSetIndex < 0 || this.drumSetIndex >= app.drumSets.size()) this.drumSetIndex = 0;
        return app.drumSets.get(this.drumSetIndex);
    }

    short[] voiceAt(int t) {
        int src = Engine.soundTrack(t);
        Engine.DrumSet s = this.activeDrumSet();
        if (s.original || s.matchOrig[src]) return app.voices[src];
        return s.samples[src];
    }

    short[][] mixVoices() {
        this.fillMissingVoices();
        Engine.DrumSet s = this.activeDrumSet();
        short[][] out = new short[app.voices.length][];
        for (int t = 0; t < Engine.TRACK_ID.length && t < out.length; t++) {
            int src = Engine.soundTrack(t);
            out[t] = s.original || s.matchOrig[src] ? app.voices[src] : s.samples[src];
        }
        return out;
    }

    void refreshDrumSets() {
        if (this.drumSetBar == null) return;
        this.drumSetBar.removeAll();
        JLabel lab = new JLabel("Drum sets  ");
        lab.setForeground(SUBTLE);
        this.drumSetBar.add(lab);
        for (int i = 0; i < app.drumSets.size(); i++) {
            final int idx = i;
            Engine.DrumSet s = app.drumSets.get(i);
            JButton b = app.chip(s.name, idx == this.drumSetIndex);
            if (!s.original) {
                java.util.Map<TextAttribute, Object> attrs = new java.util.HashMap<TextAttribute, Object>();
                attrs.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
                b.setFont(b.getFont().deriveFont(attrs));
            }
            b.addActionListener(e -> {
                this.drumSetIndex = idx;
                this.reloadDrumBank();
                this.refreshDrumSets();
                this.refreshPadLabels();
                app.setNow(s.original ? "Original kit" : s.name + " \u00b7 " + s.foundCount() + " pads");
            });
            if (!s.original) app.onChipMenu(b, () -> this.drumSetMenu(idx));
            this.drumSetBar.add(b);
        }
        this.drumSetBar.revalidate();
        this.drumSetBar.repaint();
        this.refreshPadLabels();
        Engine.DrumSet cur = this.activeDrumSet();
        if (this.padMatchAll != null) {
            boolean iso = cur != null && !cur.original;
            this.padMatchAll.setVisible(iso);
            if (iso) {
                boolean all = true;
                for (int t = 0; t < Engine.TRACK_ID.length; t++) if (!cur.matchOrig[t]) { all = false; break; }
                this.padMatchAll.setText(all ? "This set" : "Match Original");
                app.paintOutline(this.padMatchAll, !all);
            }
        }
    }

    void matchWholeOriginal() {
        Engine.DrumSet s = this.activeDrumSet();
        if (s == null || s.original) return;
        boolean all = true;
        for (int t = 0; t < Engine.TRACK_ID.length; t++) if (!s.matchOrig[t]) { all = false; break; }
        for (int t = 0; t < Engine.TRACK_ID.length; t++) s.matchOrig[t] = !all;
        this.reloadDrumBank();
        this.refreshDrumSets();
        app.setNow(!all ? "Whole set matches Original" : "Using this set's sounds");
    }

    JPopupMenu drumSetMenu(int idx) {
        JPopupMenu m = new JPopupMenu();
        if (idx <= 0 || idx >= app.drumSets.size()) return m;
        JMenuItem matchAll = new JMenuItem("Match Original");
        matchAll.addActionListener(e -> {
            this.drumSetIndex = idx;
            this.matchWholeOriginal();
        });
        JMenuItem match = new JMenuItem("Match empty from Original");
        match.addActionListener(e -> {
            this.drumSetIndex = idx;
            Engine.DrumSet s = app.drumSets.get(idx);
            for (int t = 0; t < Engine.TRACK_ID.length; t++) {
                if (s.samples[t] == null) s.matchOrig[t] = true;
            }
            this.reloadDrumBank();
            this.refreshDrumSets();
            app.setNow("Empty pads match Original");
        });
        JMenuItem del = new JMenuItem("Delete set");
        del.addActionListener(e -> {
            if (idx <= 0 || idx >= app.drumSets.size()) return;
            app.drumSets.remove(idx);
            if (this.drumSetIndex >= app.drumSets.size()) this.drumSetIndex = 0;
            this.reloadDrumBank();
            this.refreshDrumSets();
            app.setNow("Drum set removed");
        });
        m.add(matchAll);
        m.add(match);
        m.add(del);
        return m;
    }

    JPopupMenu padMenu(int t) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem load = new JMenuItem("Load WAV / MP3");
        load.addActionListener(e -> this.loadPadSample(t));
        m.add(load);
        Engine.DrumSet s = this.activeDrumSet();
        if (!s.original) {
            if (s.matchOrig[t]) {
                JMenuItem use = new JMenuItem("Use this set's sound");
                use.addActionListener(e -> {
                    s.matchOrig[t] = false;
                    this.reloadDrumBank();
                    this.refreshPadLabels();
                    app.setNow(Engine.TRACK_LABEL[t] + " \u00b7 this set");
                });
                m.add(use);
            } else {
                JMenuItem match = new JMenuItem("Match Original");
                match.addActionListener(e -> {
                    s.matchOrig[t] = true;
                    this.reloadDrumBank();
                    this.refreshPadLabels();
                    app.setNow(Engine.TRACK_LABEL[t] + " \u00b7 Original");
                });
                m.add(match);
            }
            if (s.samples[t] != null) {
                JMenuItem clear = new JMenuItem("Remove sample");
                clear.addActionListener(e -> {
                    s.samples[t] = null;
                    this.reloadDrumBank();
                    this.refreshPadLabels();
                    app.setNow(Engine.TRACK_LABEL[t] + " \u00b7 empty");
                });
                m.add(clear);
            }
        }
        return m;
    }

    void refreshPadLabels() {
        Engine.DrumSet s = this.activeDrumSet();
        for (int t = 0; t < this.padBtns.length; t++) {
            if (this.padBtns[t] == null) continue;
            String extra = Engine.TRACK_LABEL[t];
            if (!s.original) {
                if (s.matchOrig[t]) extra = "Original";
                else if (s.samples[t] != null) extra = "From song";
                else extra = "Empty";
            }
            this.padBtns[t].setText(Engine.TRACK_SHORT[t] + "  " + extra);
        }
    }

    /** A song's drum hits as audio, with the current drum set (Song WAV / Song MP3). */
    short[] songPcm(List<Engine.Part> parts) {
        this.fillMissingVoices();
        return AudioIo.renderSong(parts, app.mutes, this.mixVoices(), 22050);
    }

    short[] mixPcm() {
        return AudioIo.mix(app.cells, app.fillPat, app.fillLast, app.mutes, this.mixVoices(), app.bpm(), app.bars, 22050, app.steps);
    }

    void fillMissingVoices() {
        short[][] sArray = AudioIo.buildVoices(22050);
        for (int i = 0; i < app.voices.length; ++i) {
            if (app.voices[i] != null) continue;
            app.voices[i] = sArray[i];
        }
    }

    void loadPadSample(int n) {
        JFileChooser jFileChooser = new JFileChooser();
        jFileChooser.setFileFilter(new FileNameExtensionFilter("WAV or MP3", "wav", "wave", "mp3"));
        if (jFileChooser.showOpenDialog(app) != 0) {
            return;
        }
        try {
            byte[] byArray = Files.readAllBytes(jFileChooser.getSelectedFile().toPath());
            this.applyPadBytes(n, byArray, jFileChooser.getSelectedFile().getName());
        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(app, "Could not load pad sample: " + exception.getMessage());
        }
    }

    void applyPadBytes(int n, byte[] byArray, String string) throws Exception {
        String string2 = AudioIo.sniff(byArray);
        AudioIo.Pcm pcm;
        if ("mp3".equals(string2) || string.toLowerCase().endsWith(".mp3")) {
            pcm = Mp3Decode.parse(byArray);
        } else if ("wav".equals(string2) || string.toLowerCase().endsWith(".wav") || string.toLowerCase().endsWith(".wave")) {
            pcm = AudioIo.parseWav(byArray);
        } else {
            throw new IllegalArgumentException("Use a WAV or MP3 sample");
        }
        short[] sArray = AudioIo.floatsToShorts(pcm.samples);
        int n2 = 88200;
        if (sArray.length > n2) {
            short[] sArray2 = new short[n2];
            System.arraycopy(sArray, 0, sArray2, 0, n2);
            sArray = sArray2;
        }
        Engine.DrumSet set = this.activeDrumSet();
        if (set.original) {
            app.voices[n] = sArray;
        } else {
            set.samples[n] = sArray;
            set.matchOrig[n] = false;
        }
        this.reloadDrumBank();
        this.refreshPadLabels();
        app.setNow(Engine.TRACK_LABEL[n] + " \u00b7 " + string);
    }

    void reloadDrumBank() {
        if (this.synth == null) {
            return;
        }
        try {
            this.fillMissingVoices();
            byte[] byArray = AudioIo.encodeSf2(this.mixVoices(), "Pulsekit");
            Soundbank soundbank = MidiSystem.getSoundbank(new ByteArrayInputStream(byArray));
            this.synth.unloadAllInstruments(this.synth.getDefaultSoundbank());
            this.synth.loadAllInstruments(soundbank);
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}
