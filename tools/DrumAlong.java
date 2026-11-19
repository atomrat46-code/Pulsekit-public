import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * DrumAlong: a drum beat played along with an instrument track (a guitar, a bass, keys: no drums), as a
 * MIDI file in time with the track. It listens for the track's tempo and beats (following a player's
 * small drifts), where its bars start (the bass's notes, the strongest chords), and how it plays each
 * sixteenth (or triplet) of every bar: its bass notes, its strums and their accents, swing, how busy it
 * is, and where its sections change. Then a drummer plays to it: the kick with the bass (on a guitar
 * alone, with its strongest strums), the snare on the backbeat (half time when the track feels so), hats
 * or ride in the track's own subdivision, ghost notes when it is busy, a fill at the end of each phrase
 * and a crash where a section starts.
 *
 * The style is picked from the track (rock, punk, metal, funk, shuffle, ballad, waltz, 6/8, four on the
 * floor, hip-hop, reggae, country) unless --style names one; Pulsekit's style names work too (Hard Rock,
 * Deep House, Boom Bap...). --busy 1-10 sets how much the drummer plays (default: as busy as the track).
 * A full song (with drums) goes in too, but its own drums are heard as the band's: give its other
 * instruments alone (Demucs's no_drums.wav, or DrumMidi --separate's computer setup).
 *
 * Built into Programs/Java/DrumAlong.jar with DrumMidi_CRT's audio reading (WAV, MP3) and beat finding
 * by tools/make_drummidi_jar.py; its source is here (a program of the Java menu is one file).
 */
public final class DrumAlong {
    static final String USAGE = "Usage: java DrumAlong <input.wav> [output.mid] [--style name] [--busy 1-10] [--bpm N] [--timesig 4/4|3/4|6/8] "
            + "[--half-time] [--shift] [--separate] [--no-fills] [--no-crashes] [--no-ghosts] [--grid]";

    static final int KICK = 36, SNARE = 38, SIDESTICK = 37, CLOSED_HH = 42, PEDAL_HH = 44, OPEN_HH = 46;
    static final int CRASH = 49, RIDE = 51, FLOOR_TOM = 41, LOW_TOM = 45, MID_TOM = 47, HIGH_TOM = 50;
    static final int PPQ = 480;

    /** One drum note: seconds from the track's start, the note, its velocity. */
    static final class Hit {
        final double time;
        final int note, vel;

        Hit(double time, int note, int vel) {
            this.time = time;
            this.note = note;
            this.vel = Math.max(1, Math.min(127, vel));
        }
    }

    /** What a drummer plays in a style: its kick and snare steps (in sixteenths of a 4/4 bar), its hats. */
    static final class Style {
        final String name;
        /** Kick steps it always plays (0-15), snare steps (backbeat), and whether the kick follows the bass. */
        final int[] kick, snare;
        final boolean follow;
        /** "8" eighths, "16" sixteenths, "4" quarters, "off" offbeat eighths (open), "ride" ride eighths, "auto" the track's. */
        final String hats;
        final boolean halfTime, fourFloor, shuffle;

        Style(String name, int[] kick, int[] snare, boolean follow, String hats, boolean halfTime, boolean fourFloor, boolean shuffle) {
            this.name = name;
            this.kick = kick;
            this.snare = snare;
            this.follow = follow;
            this.hats = hats;
            this.halfTime = halfTime;
            this.fourFloor = fourFloor;
            this.shuffle = shuffle;
        }
    }

    static Style style(String family) {
        switch (family) {
            case "punk": return new Style("punk", new int[] {0, 8}, new int[] {4, 12}, true, "8", false, false, false);
            case "metal": return new Style("metal", new int[] {0, 8}, new int[] {4, 12}, true, "8", false, false, false);
            case "funk": return new Style("funk", new int[] {0}, new int[] {4, 12}, true, "16", false, false, false);
            case "shuffle": return new Style("shuffle", new int[] {0, 8}, new int[] {4, 12}, true, "ride", false, false, true);
            case "ballad": return new Style("ballad", new int[] {0}, new int[] {8}, true, "8", true, false, false);
            case "four": return new Style("four on the floor", new int[] {0, 4, 8, 12}, new int[] {4, 12}, false, "off", false, true, false);
            case "hiphop": return new Style("hip-hop", new int[] {0}, new int[] {4, 12}, true, "8", false, false, false);
            case "reggae": return new Style("reggae one drop", new int[] {8}, new int[] {8}, false, "8", false, false, false);
            case "country": return new Style("country train beat", new int[] {0, 8}, new int[] {4, 12}, true, "16", false, false, false);
            default: return new Style("rock", new int[] {0, 8}, new int[] {4, 12}, true, "auto", false, false, false);
        }
    }

    /** A style name (--style: a family, or one of Pulsekit's style names) as a family. */
    static String familyOf(String s) {
        String n = s.toLowerCase(Locale.ROOT);
        String[][] words = {
            {"punk", "punk"}, {"hardcore", "punk"}, {"thrash", "metal"}, {"metal", "metal"}, {"djent", "metal"}, {"doom", "ballad"},
            {"funk", "funk"}, {"disco", "four"}, {"house", "four"}, {"techno", "four"}, {"trance", "four"}, {"edm", "four"},
            {"dance", "four"}, {"shuffle", "shuffle"}, {"blues", "shuffle"}, {"swing", "shuffle"}, {"jazz", "shuffle"},
            {"boogie", "shuffle"}, {"ballad", "ballad"}, {"slow", "ballad"}, {"hip", "hiphop"}, {"boom", "hiphop"}, {"rap", "hiphop"},
            {"trap", "hiphop"}, {"r&b", "hiphop"}, {"rnb", "hiphop"}, {"lo-fi", "hiphop"}, {"reggae", "reggae"}, {"dub", "reggae"},
            {"ska", "reggae"}, {"country", "country"}, {"bluegrass", "country"}, {"rockabilly", "country"}, {"rock", "rock"},
            {"pop", "rock"}, {"grunge", "rock"}, {"indie", "rock"}, {"alternative", "rock"},
        };
        for (String[] w : words) if (n.contains(w[0])) return w[1];
        return null;
    }

    static String input, output, styleName;
    static int busy = 0, tsNum = 0, tsDen = 4;
    static double bpmGiven = 0;
    static boolean halfTimeGiven, fills = true, crashes = true, ghosts = true, onGrid, shift, separate;
    /** --separate's parts (Demucs): the bass alone and the other instruments (guitars, keys) alone; null without. */
    static double[] bassPart, otherPart;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable ex) {
            String m = ex.getMessage();
            System.out.println("Failed: " + (m == null || m.length() == 0 ? ex.toString() : m));
        }
    }

    static void run(String[] args) throws Exception {
        input = output = styleName = null;
        busy = 0;
        tsNum = 0;
        tsDen = 4;
        bpmGiven = 0;
        halfTimeGiven = false;
        fills = crashes = ghosts = true;
        onGrid = false;
        shift = false;
        separate = false;
        bassPart = otherPart = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--style") && i + 1 < args.length) styleName = args[++i];
            else if (a.equals("--busy") && i + 1 < args.length) busy = Math.max(1, Math.min(10, Integer.parseInt(args[++i].trim())));
            else if ((a.equals("--bpm") || a.equals("--tempo")) && i + 1 < args.length) bpmGiven = Double.parseDouble(args[++i].trim());
            else if (a.equals("--timesig") && i + 1 < args.length) {
                String[] p = args[++i].trim().split("/");
                tsNum = Integer.parseInt(p[0]);
                tsDen = p.length > 1 ? Integer.parseInt(p[1]) : 4;
            } else if (a.equals("--half-time")) halfTimeGiven = true;
            else if (a.equals("--no-fills")) fills = false;
            else if (a.equals("--no-crashes")) crashes = false;
            else if (a.equals("--no-ghosts")) ghosts = false;
            else if (a.equals("--grid")) onGrid = true;
            // The bar a beat later: the snare on the other beats (where the bar starts is the hardest to hear
            // from a guitar or bass alone: right on some 60% of SogniMusic's band tracks).
            else if (a.equals("--shift")) shift = true;
            // A whole song (with drums, vocals): Demucs's bass and other parts heard instead (on a computer).
            else if (a.equals("--separate")) separate = true;
            else if (a.startsWith("--")) {
                // Pulsekit passes its own (--swing, --saveprompt...): not DrumAlong's.
                if (i + 1 < args.length && !args[i + 1].startsWith("--") && !args[i + 1].contains(".")) i++;
            } else if (input == null) input = a;
            else if (output == null) output = a;
        }
        if (input == null) {
            System.out.println(USAGE);
            System.out.println("Failed: give the instrument track (a WAV or MP3: guitar, bass or keys without drums)");
            return;
        }
        File in = new File(input);
        if (!in.isFile()) {
            System.out.println("Failed: no file " + input);
            return;
        }
        if (output == null || output.startsWith("<")) output = in.getName().replaceAll("\\.[^.]+$", "") + "-drums.mid";
        File out = new File(output);
        if (!out.isAbsolute() && in.getAbsoluteFile().getParentFile() != null && new File(output).getParentFile() == null) {
            // Beside the input when only a name is given and the current folder is not writable (the phone's).
            File here = new File(System.getProperty("user.dir", "."));
            if (!here.canWrite()) out = new File(in.getAbsoluteFile().getParentFile(), output);
        }
        System.out.println("DrumAlong | input " + in.getName() + " | output " + out.getName());
        DrumMidi_CRT.Audio audio = DrumMidi_CRT.readAudio(in);
        double sr = audio.sampleRate;
        double[] x = audio.samples;
        if (separate) {
            java.util.Map<String, File> st = DrumMidi_CRT.Demucs.stems(in, false, "Separate the song", "the song's drums, bass, other instruments and vocals");
            if (st != null) {
                try {
                    double[][] part = new double[4][];
                    String[] names = {"drums", "bass", "other", "vocals"};
                    double[] level = new double[4];
                    double total = 0;
                    for (int k = 0; k < 4; k++) {
                        DrumMidi_CRT.Audio a = DrumMidi_CRT.readAudio(st.get(names[k]));
                        part[k] = a.samples;
                        double sum = 0;
                        for (int i = 0; i < a.samples.length; i += 16) sum += a.samples[i] * a.samples[i];
                        level[k] = sum;
                        total += sum;
                        if (k == 1) sr = a.sampleRate;
                    }
                    bassPart = part[1];
                    otherPart = part[2];
                    int n = Math.min(bassPart.length, otherPart.length);
                    x = new double[n];
                    for (int i = 0; i < n; i++) x[i] = bassPart[i] + otherPart[i];
                    System.out.println(String.format(Locale.ROOT, "Separated with Demucs: drums %.0f%%, bass %.0f%%, guitars and keys %.0f%%, vocals %.0f%% of the song's energy; the beat follows the bass and the guitars and keys",
                            100 * level[0] / total, 100 * level[1] / total, 100 * level[2] / total, 100 * level[3] / total));
                } finally {
                    for (File f : st.values()) f.delete();
                }
            }
        }
        System.out.println(String.format(Locale.ROOT, "Loaded %.1f s at %.0f Hz", x.length / sr, sr));
        if (x.length < sr * 4) {
            System.out.println("Failed: the track is too short (4 s at least)");
            return;
        }
        System.out.println("Progress: 10%");
        Analysis an = analyse(x, sr);
        System.out.println("Progress: 70%");
        List<Hit> hits = play(an);
        System.out.println("Progress: 90%");
        byte[] mid = midi(hits, an);
        File parent = out.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(mid);
        }
        int k = 0, s = 0, h = 0, c = 0, t = 0;
        for (Hit hit : hits) {
            if (hit.note == KICK) k++;
            else if (hit.note == SNARE || hit.note == SIDESTICK) s++;
            else if (hit.note == CLOSED_HH || hit.note == OPEN_HH || hit.note == PEDAL_HH || hit.note == RIDE) h++;
            else if (hit.note == CRASH) c++;
            else t++;
        }
        System.out.println(String.format(Locale.ROOT, "Wrote %s: %d bars at %.1f BPM, %d kicks, %d snares, %d hats/ride, %d crashes, %d toms",
                out.getName(), an.bars, an.bpm, k, s, h, c, t));
        System.out.println("Succeeded: " + out.getName());
    }

    // ------------------------------------------------------------------ listening

    /** What was heard: the beats' times, the meter, and how the track plays each step of each bar. */
    static final class Analysis {
        double bpm;
        /** Every beat's time (seconds), the first being a bar's first beat. */
        double[] beats;
        int beatsPerBar, stepsPerBeat, bars;
        /** Per bar and step: the bass's note starts (40-250 Hz) and the chords' (250-4000 Hz), 0..1. */
        double[][] low, mid;
        /** Per bar: how loud the track is (its level against the song's), 0..1+. */
        double[] level;
        /** Per bar: a section starts there (the track gets much louder or busier). */
        boolean[] section;
        boolean swing, hasBass, halfTime;
        /** How much happens between the beats: 0 (only on the beats) to 1 (every sixteenth). */
        double density;
        Style style;
        int busy;
        int firstBar, lastBar;
    }

    static Analysis analyse(double[] x, double sr) {
        Analysis an = new Analysis();
        float[][] mag = DrumMidi_CRT.spectrogram(x, sr);
        int maxBin = mag[0].length - 1;
        double fps = sr / DrumMidi_CRT.HOP;
        // Separated (--separate): the bass's notes from the bass alone, the strums from the guitars and keys alone.
        float[][] magLow = bassPart != null ? DrumMidi_CRT.spectrogram(bassPart, sr) : mag;
        float[][] magMid = otherPart != null ? DrumMidi_CRT.spectrogram(otherPart, sr) : mag;
        double[] low = DrumMidi_CRT.flux(magLow, DrumMidi_CRT.bin(40, sr, maxBin), DrumMidi_CRT.bin(250, sr, maxBin));
        double[] mid = DrumMidi_CRT.flux(magMid, DrumMidi_CRT.bin(250, sr, maxBin), DrumMidi_CRT.bin(4000, sr, maxBin));
        double[] high = DrumMidi_CRT.flux(magMid, DrumMidi_CRT.bin(2000, sr, maxBin), DrumMidi_CRT.bin(8000, sr, maxBin));
        double[] lowLevel = DrumMidi_CRT.bandMean(magLow, DrumMidi_CRT.bin(40, sr, maxBin), DrumMidi_CRT.bin(250, sr, maxBin));
        double[] midLevel = DrumMidi_CRT.bandMean(magMid, DrumMidi_CRT.bin(250, sr, maxBin), DrumMidi_CRT.bin(4000, sr, maxBin));
        // Is there a bass at all (a guitar alone has little below 250 Hz against its chords)?
        double lowSum = 0, midSum = 0;
        for (int t = 0; t < mag.length; t++) {
            lowSum += lowLevel[t];
            midSum += midLevel[t];
        }
        an.hasBass = lowSum > 0.35 * midSum;
        // Note starts against their surroundings (a held chord's flux is no start), each band to its own scale.
        int mw = Math.max(4, (int) Math.round(0.25 * fps));
        low = overMedian(low, mw);
        mid = overMedian(mid, mw);
        high = overMedian(high, mw);
        DrumMidi_CRT.normalize(low);
        DrumMidi_CRT.normalize(mid);
        DrumMidi_CRT.normalize(high);
        double[] env = new double[low.length];
        for (int t = 0; t < env.length; t++) env[t] = (an.hasBass ? low[t] : 0.5 * low[t]) + mid[t] + 0.5 * high[t];

        // Tempo: the beat's lag, kept in 70-170 BPM (strummed eighths read as a beat twice as fast).
        double bpm;
        if (bpmGiven > 0) bpm = bpmGiven;
        else {
            double coarse = tempo(env, fps);
            double[] g = DrumMidi_CRT.fitBeatGrid(env, fps, coarse * 0.97, coarse * 1.03);
            bpm = g[0];
        }
        double[] grid = DrumMidi_CRT.fitBeatGrid(env, fps, bpm, bpm);
        an.bpm = bpm;
        double period = 60.0 / bpm, phase = grid[1];
        // On the beat or between: strummed chords on the "and" pull the grid there; the bass plays on the beat.
        double onB = 0, offB = 0;
        for (double t = phase; t < x.length / sr; t += period) {
            onB += (an.hasBass ? 2 * peak(low, t, fps, 0.03) : 0) + peak(mid, t, fps, 0.03);
            offB += (an.hasBass ? 2 * peak(low, t + period / 2, fps, 0.03) : 0) + peak(mid, t + period / 2, fps, 0.03);
        }
        if (offB > 1.1 * onB) phase += period / 2;
        double dur = x.length / sr;
        // The beats, each pulled a little toward the strongest onset near where it is due (a player drifts).
        double[] smooth = new double[env.length];
        for (int t = 0; t < env.length; t++) {
            double s = 0;
            for (int k = -1; k <= 1; k++) s += env[Math.max(0, Math.min(env.length - 1, t + k))];
            smooth[t] = s / 3;
        }
        List<Double> beats = new ArrayList<Double>();
        double tBeat = phase;
        while (tBeat - period > 0) tBeat -= period;
        double step = period;
        while (tBeat < dur + period) {
            double best = tBeat;
            int c = (int) Math.round(tBeat * fps), w = (int) Math.round(0.07 * period * fps);
            double bestV = 0;
            for (int f = c - w; f <= c + w; f++) {
                if (f < 0 || f >= smooth.length) continue;
                double v = smooth[f] * (1 - 0.5 * Math.abs(f - c) / Math.max(1, w));
                if (v > bestV) {
                    bestV = v;
                    best = f / fps;
                }
            }
            double t = bestV > 0.6 ? 0.6 * tBeat + 0.4 * best : tBeat;
            if (!beats.isEmpty()) step = 0.9 * step + 0.1 * (t - beats.get(beats.size() - 1));
            beats.add(t);
            tBeat = t + Math.max(0.8 * period, Math.min(1.2 * period, step));
        }

        // Meter: 3 or 4 beats a bar from how the bass's accents repeat (or as --timesig says); 6/8 as two
        // dotted-quarter beats of three eighths.
        double[] beatLow = new double[beats.size()];
        for (int b = 0; b < beats.size(); b++) beatLow[b] = peak(an.hasBass ? low : mid, beats.get(b), fps, 0.04);
        int bpb;
        boolean compound = false;
        if (tsNum > 0) {
            compound = tsDen == 8 && tsNum % 3 == 0;
            bpb = compound ? tsNum / 3 : tsNum;
        } else {
            // 3/4 only on clear evidence over enough bars (most tracks are in 4).
            double c3 = lagCorr(beatLow, 3), c4 = lagCorr(beatLow, 4);
            bpb = beatLow.length >= 24 && c3 > 0 && c3 > 1.5 * Math.max(0, c4) ? 3 : 4;
        }
        // The bar's first beat: where the harmony changes most (chords change on the bar), and the bass starts.
        // On SogniMusic's band tracks (the drummer's snares telling where beat 1 is) each of these points to
        // it on 63-66% of tracks: the chords' and the bass's notes changing over a beat, the bass held after it.
        double[] chord = new double[beats.size()], bassNote = new double[beats.size()], bassHeld = new double[beats.size()];
        int c1 = DrumMidi_CRT.bin(120, sr, maxBin), c2 = DrumMidi_CRT.bin(1500, sr, maxBin);
        int b1 = DrumMidi_CRT.bin(40, sr, maxBin), b2 = DrumMidi_CRT.bin(300, sr, maxBin), b3 = DrumMidi_CRT.bin(250, sr, maxBin);
        for (int b = 0; b < beats.size(); b++) {
            chord[b] = spectrumChange(mag, beats.get(b), period, fps, c1, c2);
            bassNote[b] = spectrumChange(mag, beats.get(b), period, fps, b1, b2);
            bassHeld[b] = levelAfter(mag, beats.get(b), period / 2, fps, b1, b3);
        }
        double[] zc = zscore(chord), zb = zscore(bassNote), zh = zscore(bassHeld);
        int best = 0;
        double bestS = -Double.MAX_VALUE;
        for (int o = 0; o < bpb; o++) {
            double s = 0;
            int n = 0;
            for (int b = o; b < beatLow.length; b += bpb) {
                s += zc[b] + zb[b] + zh[b];
                n++;
            }
            s /= Math.max(1, n);
            if (s > bestS) {
                bestS = s;
                best = o;
            }
        }
        if (shift) best = (best + 1) % bpb;
        List<Double> barBeats = new ArrayList<Double>();
        // Beats before the first downbeat: a pickup bar from the beat before (filled out below).
        int first = best;
        while (first - bpb >= 0) first -= bpb;
        for (int b = first; b < beats.size(); b++) barBeats.add(beats.get(b));
        an.beats = new double[barBeats.size()];
        for (int i = 0; i < an.beats.length; i++) an.beats[i] = barBeats.get(i);
        an.beatsPerBar = bpb;

        // Swing (or compound time): the offbeat eighth heard on the triplet's third more than half way.
        double straight = 0, triplet = 0;
        for (int b = 0; b + 1 < an.beats.length; b++) {
            double p = an.beats[b + 1] - an.beats[b];
            straight += peak(mid, an.beats[b] + p / 2, fps, 0.02);
            triplet += peak(mid, an.beats[b] + p * 2 / 3, fps, 0.02);
        }
        an.swing = compound || triplet > 1.3 * straight;
        an.stepsPerBeat = compound || an.swing ? 3 : 4;
        int steps = bpb * an.stepsPerBeat;
        an.bars = Math.max(1, (an.beats.length - 1) / bpb);
        an.low = new double[an.bars][steps];
        an.mid = new double[an.bars][steps];
        an.level = new double[an.bars];
        double[] rms = new double[an.bars];
        for (int bar = 0; bar < an.bars; bar++) {
            for (int s = 0; s < steps; s++) {
                double t = stepTime(an, bar, s);
                an.low[bar][s] = peak(low, t, fps, 0.03);
                an.mid[bar][s] = peak(mid, t, fps, 0.03);
            }
            double a = an.beats[bar * bpb], z = an.beats[Math.min(an.beats.length - 1, (bar + 1) * bpb)];
            double sum = 0;
            int n = 0;
            for (int i = (int) (a * sr); i < Math.min(x.length, (int) (z * sr)); i += 64) {
                sum += x[i] * x[i];
                n++;
            }
            rms[bar] = Math.sqrt(sum / Math.max(1, n));
        }
        double[] sorted = rms.clone();
        Arrays.sort(sorted);
        double ref = Math.max(1e-6, sorted[(int) (0.8 * (sorted.length - 1))]);
        an.firstBar = -1;
        an.lastBar = -1;
        for (int bar = 0; bar < an.bars; bar++) {
            an.level[bar] = rms[bar] / ref;
            if (an.level[bar] > 0.15) {
                if (an.firstBar < 0) an.firstBar = bar;
                an.lastBar = bar;
            }
        }
        if (an.firstBar < 0) {
            an.firstBar = 0;
            an.lastBar = an.bars - 1;
        }
        // Sections: a bar much louder than the two before it (a chorus coming in), on a phrase line where possible.
        an.section = new boolean[an.bars];
        for (int bar = an.firstBar + 2; bar <= an.lastBar; bar++) {
            double before = (an.level[bar - 1] + an.level[bar - 2]) / 2;
            if (an.level[bar] > 1.45 * before && an.level[bar] > 0.5) an.section[bar] = true;
        }
        // How busy the track is between its beats.
        int on = 0, all = 0;
        for (int bar = an.firstBar; bar <= an.lastBar; bar++) {
            for (int s = 0; s < steps; s++) {
                if (s % an.stepsPerBeat == 0) continue;
                all++;
                if (Math.max(an.mid[bar][s], an.hasBass ? an.low[bar][s] : 0) > 0.35) on++;
            }
        }
        an.density = all == 0 ? 0 : on / (double) all;
        an.busy = busy > 0 ? busy : (int) Math.round(Math.max(2, Math.min(9, 2 + an.density * 10)));
        // The style: given, else from the track's feel.
        String fam = styleName == null ? null : familyOf(styleName);
        if (styleName != null && fam == null) System.out.println("Style " + styleName + " not known: picked from the track");
        if (fam == null) {
            if (an.swing && !compound) fam = "shuffle";
            else if (bpm >= 165 && an.density < 0.6) fam = "punk";
            else if (an.density >= 0.8 && bpm >= 80 && bpm <= 125) fam = "funk";
            else if (an.density < 0.2 && bpm < 95) fam = "ballad";
            else fam = "rock";
        }
        an.style = style(fam);
        an.halfTime = halfTimeGiven || an.style.halfTime;
        System.out.println(String.format(Locale.ROOT, "Tempo: %.2f BPM, first bar at %.3f s, %d bars of %s%s", bpm, an.beats[0], an.bars,
                compound ? (bpb * 3) + "/8" : bpb + "/4", an.swing && !compound ? " (swung)" : ""));
        System.out.println(String.format(Locale.ROOT, "Heard: %s, busy %.0f%% between the beats, plays from bar %d to %d%s",
                an.hasBass ? "a bass line" : "no bass (chords only)", 100 * an.density, an.firstBar + 1, an.lastBar + 1,
                countSections(an) > 0 ? ", " + countSections(an) + " section changes" : ""));
        System.out.println("Style: " + an.style.name + (styleName != null && familyOf(styleName) != null ? " (" + styleName + ")" : " (from the track)")
                + ", busy " + an.busy + (an.halfTime ? ", half time" : ""));
        return an;
    }

    /**
     * The tempo from how the onsets repeat: each beat's lag scored with its double and half (a bar's
     * eighths and its half notes repeat too), under a mild preference for 75-150 BPM.
     */
    static double tempo(double[] env, double fps) {
        int n = env.length;
        double mean = 0;
        for (double v : env) mean += v;
        mean /= n;
        int maxLag = (int) Math.ceil(fps * 60 / 30.0);
        double[] ac = new double[Math.min(n, maxLag + 2)];
        for (int lag = 1; lag < ac.length; lag++) {
            double s = 0;
            for (int i = 0; i + lag < n; i++) s += (env[i] - mean) * (env[i + lag] - mean);
            ac[lag] = s / (n - lag);
        }
        double best = 120, bestS = -Double.MAX_VALUE;
        for (double bpm = 60; bpm <= 180; bpm += 0.25) {
            double lag = 60 * fps / bpm;
            double s = acAt(ac, lag) + 0.5 * acAt(ac, 2 * lag) + 0.5 * acAt(ac, lag / 2) + 0.25 * acAt(ac, 4 * lag);
            double oct = Math.log(bpm / 108.0) / Math.log(2);
            s *= Math.exp(-0.5 * oct * oct / (0.7 * 0.7));
            if (s > bestS) {
                bestS = s;
                best = bpm;
            }
        }
        return best;
    }

    static double acAt(double[] ac, double lag) {
        int i = (int) lag;
        if (i < 1 || i + 1 >= ac.length) return 0;
        double f = lag - i;
        return ac[i] * (1 - f) + ac[i + 1] * f;
    }

    /**
     * How unlike the spectrum (lo-hi bins, log) is in the `span` before t and the `span` after: 1 minus
     * their correlation (a chord or bass note changing there).
     */
    static double spectrumChange(float[][] mag, double t, double span, double fps, int lo, int hi) {
        int c = (int) Math.round(t * fps), w = Math.max(2, (int) Math.round(span * fps));
        if (c - w < 0 || c + w >= mag.length || hi <= lo) return 0;
        int n = hi - lo + 1;
        double[] a = new double[n], b = new double[n];
        for (int k = 0; k < n; k++) {
            for (int f = c - w; f < c - 1; f++) a[k] += Math.log1p(100 * mag[f][lo + k]);
            for (int f = c + 1; f < c + w; f++) b[k] += Math.log1p(100 * mag[f][lo + k]);
        }
        double ma = 0, mb = 0;
        for (int k = 0; k < n; k++) {
            ma += a[k] / n;
            mb += b[k] / n;
        }
        double ab = 0, aa = 0, bb = 0;
        for (int k = 0; k < n; k++) {
            ab += (a[k] - ma) * (b[k] - mb);
            aa += (a[k] - ma) * (a[k] - ma);
            bb += (b[k] - mb) * (b[k] - mb);
        }
        return 1 - ab / (Math.sqrt(aa * bb) + 1e-12);
    }

    /** The mean log level of bins lo-hi in the `span` after t. */
    static double levelAfter(float[][] mag, double t, double span, double fps, int lo, int hi) {
        int c = (int) Math.round(t * fps), w = Math.max(1, (int) Math.round(span * fps));
        double s = 0;
        int n = 0;
        for (int f = c; f < Math.min(mag.length, c + w); f++) {
            for (int k = lo; k <= hi; k++) s += Math.log1p(100 * mag[f][k]);
            n += hi - lo + 1;
        }
        return n == 0 ? 0 : s / n;
    }

    /** How much the spectrum (lo-hi bins, log) differs between the half beat before t and the half beat after. */
    static double harmonyChange(float[][] mag, double t, double half, double fps, int lo, int hi) {
        int c = (int) Math.round(t * fps), w = Math.max(2, (int) Math.round(half * fps));
        if (c - w < 0 || c + w >= mag.length) return 0;
        double d = 0;
        for (int k = lo; k <= hi; k++) {
            double a = 0, b = 0;
            for (int f = c - w; f < c - 1; f++) a += mag[f][k];
            for (int f = c + 1; f < c + w; f++) b += mag[f][k];
            d += Math.abs(Math.log1p(a) - Math.log1p(b));
        }
        return d / (hi - lo + 1);
    }

    static double[] zscore(double[] v) {
        double m = 0, s = 0;
        for (double x : v) m += x;
        m /= Math.max(1, v.length);
        for (double x : v) s += (x - m) * (x - m);
        s = Math.sqrt(s / Math.max(1, v.length));
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = s < 1e-12 ? 0 : (v[i] - m) / s;
        return out;
    }

    /** How far each frame is over the median of the frames around it (0 when under). */
    static double[] overMedian(double[] f, int w) {
        double[] out = new double[f.length];
        double[] win = new double[2 * w + 1];
        for (int t = 0; t < f.length; t++) {
            int n = 0;
            for (int k = t - w; k <= t + w; k++) if (k >= 0 && k < f.length) win[n++] = f[k];
            Arrays.sort(win, 0, n);
            out[t] = Math.max(0, f[t] - win[n / 2]);
        }
        return out;
    }

    static int countSections(Analysis an) {
        int n = 0;
        for (boolean b : an.section) if (b) n++;
        return n;
    }

    /** A step's time: its beat's, and the way to the next beat (straight or in triplets). */
    static double stepTime(Analysis an, int bar, int s) {
        int b = bar * an.beatsPerBar + s / an.stepsPerBeat;
        int frac = s % an.stepsPerBeat;
        double a = an.beats[Math.min(b, an.beats.length - 1)];
        double z = b + 1 < an.beats.length ? an.beats[b + 1] : a + 60.0 / an.bpm;
        return onGrid ? an.beats[0] + (bar * an.beatsPerBar * an.stepsPerBeat + s) * 60.0 / an.bpm / an.stepsPerBeat
                : a + (z - a) * frac / an.stepsPerBeat;
    }

    /** The strongest onset within `win` seconds of t. */
    static double peak(double[] env, double t, double fps, double win) {
        int c = (int) Math.round(t * fps), w = Math.max(1, (int) Math.round(win * fps));
        double v = 0;
        for (int f = c - w; f <= c + w; f++) if (f >= 0 && f < env.length) v = Math.max(v, env[f]);
        return Math.min(1.5, v);
    }

    static double lagCorr(double[] v, int lag) {
        double m = 0;
        for (double d : v) m += d;
        m /= Math.max(1, v.length);
        double s = 0;
        int n = 0;
        for (int i = 0; i + lag < v.length; i++) {
            s += (v[i] - m) * (v[i + lag] - m);
            n++;
        }
        return n == 0 ? 0 : s / n;
    }

    // ------------------------------------------------------------------ playing

    static List<Hit> play(Analysis an) {
        List<Hit> out = new ArrayList<Hit>();
        int spb = an.stepsPerBeat, bpb = an.beatsPerBar, steps = spb * bpb;
        Style st = an.style;
        boolean triplet = spb == 3;
        int phrase = an.busy >= 7 ? 4 : 8;
        java.util.Random rnd = new java.util.Random(7);
        for (int bar = an.firstBar; bar <= an.lastBar; bar++) {
            double lvl = Math.max(0.4, Math.min(1.2, an.level[bar]));
            int rel = bar - an.firstBar;
            boolean fillBar = fills && bar < an.lastBar && ((rel + 1) % phrase == 0 || bar + 1 < an.bars && an.section[bar + 1]);
            boolean quiet = an.level[bar] < 0.35;
            int fillFrom = fillBar ? (an.busy >= 6 ? steps / 2 : steps - spb) : steps;
            // Crash: a section's or a phrase's first beat (after a fill), and the first bar.
            if (crashes && (bar == an.firstBar || an.section[bar] || rel > 0 && rel % phrase == 0 && bar > an.firstBar)) {
                out.add(new Hit(stepTime(an, bar, 0), CRASH, (int) (105 * lvl)));
            }
            boolean[] kick = new boolean[steps], snare = new boolean[steps];
            // The style's kick and snare, in this meter.
            if (bpb == 4 && !triplet) {
                for (int k : st.kick) kick[k] = true;
                for (int s : st.snare) snare[s] = true;
                if (an.halfTime && !st.fourFloor) {
                    Arrays.fill(snare, false);
                    snare[8] = true;
                    kick[8] = false;
                }
            } else if (bpb == 4) {
                // Swung 4/4: beats on steps 0, 3, 6, 9.
                kick[0] = true;
                if (!an.halfTime) kick[6] = true;
                if (an.halfTime) snare[6] = true;
                else {
                    snare[3] = true;
                    snare[9] = true;
                }
                if (st.fourFloor) for (int b = 0; b < 4; b++) kick[b * 3] = true;
            } else if (bpb == 3) {
                // A waltz: the kick on one, the snare (light) on two and three; in 9/8 the same in dotted beats.
                kick[0] = true;
                snare[spb] = true;
                snare[2 * spb] = true;
            } else if (bpb == 2) {
                // 6/8: the kick on one, the snare on the second dotted beat.
                kick[0] = true;
                snare[spb] = true;
            } else {
                kick[0] = true;
                for (int b = 1; b < bpb; b += 2) snare[b * spb] = true;
            }
            // The kick with the bass: its strong note starts (on a guitar alone, its strongest strums).
            if (st.follow && !quiet) {
                double[] src = an.hasBass ? an.low[bar] : an.mid[bar];
                double thr = an.hasBass ? 0.55 : 0.8;
                int added = 0, max = 1 + an.busy / 3;
                Integer[] order = new Integer[steps];
                for (int s = 0; s < steps; s++) order[s] = s;
                final double[] fs = src;
                Arrays.sort(order, (p, q) -> Double.compare(fs[q], fs[p]));
                for (int s : order) {
                    if (added >= max || src[s] < thr) break;
                    if (snare[s] || kick[s]) continue;
                    // Not the sixteenth right before or after the snare (a drummer's foot rarely plays there) unless busy.
                    if (an.busy < 7 && (s > 0 && snare[s - 1] || s + 1 < steps && snare[s + 1])) continue;
                    if (s >= fillFrom) continue;
                    kick[s] = true;
                    added++;
                }
                // The style's second kick (on three) only where the bass is there too, when the kick follows it.
                if (an.hasBass && bpb == 4 && !triplet && !st.fourFloor && kick[8] && an.low[bar][8] < 0.3 && an.low[bar][9] < 0.3 && an.low[bar][10] < 0.3) kick[8] = false;
            }
            for (int s = 0; s < Math.min(steps, fillFrom); s++) {
                double t = stepTime(an, bar, s);
                if (kick[s]) out.add(new Hit(t, KICK, (int) ((s == 0 ? 112 : 100) * lvl)));
                if (snare[s]) out.add(new Hit(t, bpb == 3 ? (s == spb ? SNARE : SIDESTICK) : SNARE, (int) ((bpb == 3 && s != spb ? 70 : 112) * lvl)));
            }
            // Ghost notes: a busy track's sixteenths where its chords play and no hit is.
            if (ghosts && an.busy >= 7 && !triplet && !quiet) {
                for (int s = 1; s < Math.min(steps, fillFrom); s++) {
                    if (s % 2 == 0 || kick[s] || snare[s] || snare[s - 1] || s + 1 < steps && snare[s + 1]) continue;
                    if (an.mid[bar][s] > 0.5 && rnd.nextDouble() < 0.6) out.add(new Hit(stepTime(an, bar, s), SNARE, 34 + rnd.nextInt(10)));
                }
            }
            // Hats or ride: the style's, or the track's own subdivision.
            String hats = st.hats;
            if (hats.equals("auto")) hats = an.busy >= 7 ? "16" : an.busy <= 3 ? (quiet ? "4" : "8") : "8";
            if (quiet && !hats.equals("4")) hats = "8";
            for (int s = 0; s < Math.min(steps, fillFrom); s++) {
                boolean onBeat = s % spb == 0;
                int note = -1, vel = 0;
                if (hats.equals("ride")) {
                    // Swung ride: the beat and the triplet's third (spang-a-lang).
                    if (onBeat || triplet && s % spb == 2) {
                        note = RIDE;
                        vel = onBeat ? 92 : 70;
                    }
                } else if (triplet) {
                    if (onBeat || s % spb == 2) {
                        note = CLOSED_HH;
                        vel = onBeat ? 90 : 66;
                    }
                } else if (hats.equals("16")) {
                    note = CLOSED_HH;
                    vel = onBeat ? 92 : s % 2 == 0 ? 76 : 58;
                } else if (hats.equals("off")) {
                    if (s % spb == spb / 2) {
                        note = OPEN_HH;
                        vel = 84;
                    } else if (onBeat) {
                        note = PEDAL_HH;
                        vel = 60;
                    }
                } else if (hats.equals("4")) {
                    if (onBeat) {
                        note = CLOSED_HH;
                        vel = 80;
                    }
                } else if (s % 2 == 0) {
                    note = CLOSED_HH;
                    vel = onBeat ? 92 : 70;
                }
                // A crash's beat has no hat.
                if (note >= 0 && !(s == 0 && crashes && (bar == an.firstBar || an.section[bar] || rel > 0 && rel % phrase == 0))) {
                    out.add(new Hit(stepTime(an, bar, s), note, (int) (vel * lvl)));
                }
            }
            if (fillBar) fill(out, an, bar, fillFrom, lvl, rnd);
        }
        // The end: a last kick and crash on the bar after the track's last bar.
        if (crashes && an.lastBar + 1 < an.bars) {
            double t = stepTime(an, an.lastBar + 1, 0);
            out.add(new Hit(t, CRASH, 110));
            out.add(new Hit(t, KICK, 110));
        }
        out.sort((p, q) -> Double.compare(p.time, q.time));
        return out;
    }

    /** A fill from step `from` to the bar's end: snare then toms down the kit, louder toward the end. */
    static void fill(List<Hit> out, Analysis an, int bar, int from, double lvl, java.util.Random rnd) {
        int spb = an.stepsPerBeat, steps = spb * an.beatsPerBar;
        int stride = an.busy >= 5 && spb == 4 ? 1 : spb == 4 ? 2 : 1;
        int[] voices = {SNARE, SNARE, HIGH_TOM, MID_TOM, LOW_TOM, FLOOR_TOM};
        int n = 0, total = Math.max(1, (steps - from + stride - 1) / stride);
        for (int s = from; s < steps; s += stride) {
            int v = voices[Math.min(voices.length - 1, n * voices.length / total)];
            int vel = (int) ((84 + 30.0 * n / total) * lvl);
            out.add(new Hit(stepTime(an, bar, s), v, vel));
            if (s == from) out.add(new Hit(stepTime(an, bar, s), KICK, (int) (96 * lvl)));
            n++;
        }
    }

    // ------------------------------------------------------------------ MIDI

    /** A type-0 MIDI file: the tempo and meter, then the hits (channel 10) at their times. */
    static byte[] midi(List<Hit> hits, Analysis an) {
        ByteArrayOutputStream tr = new ByteArrayOutputStream();
        int usPerQ = (int) Math.round(60000000.0 / an.bpm);
        meta(tr, 0, 0x51, new byte[] {(byte) (usPerQ >> 16), (byte) (usPerQ >> 8), (byte) usPerQ});
        int num = an.stepsPerBeat == 3 && tsNum > 0 && tsDen == 8 ? an.beatsPerBar * 3 : an.beatsPerBar;
        int den = an.stepsPerBeat == 3 && tsNum > 0 && tsDen == 8 ? 3 : 2;
        meta(tr, 0, 0x58, new byte[] {(byte) num, (byte) den, 24, 8});
        meta(tr, 0, 0x03, "DrumAlong".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        List<long[]> ev = new ArrayList<long[]>();
        double ticksPerSec = an.bpm / 60.0 * PPQ;
        for (Hit h : hits) {
            long t = Math.max(0, Math.round(h.time * ticksPerSec));
            ev.add(new long[] {t, 1, h.note, h.vel});
            ev.add(new long[] {t + PPQ / 8, 0, h.note, 0});
        }
        ev.sort((p, q) -> p[0] != q[0] ? Long.compare(p[0], q[0]) : Long.compare(p[1], q[1]));
        long last = 0;
        for (long[] e : ev) {
            varLen(tr, e[0] - last);
            last = e[0];
            tr.write(e[1] == 1 ? 0x99 : 0x89);
            tr.write((int) e[2]);
            tr.write((int) e[3]);
        }
        meta(tr, 0, 0x2F, new byte[0]);
        byte[] body = tr.toByteArray();
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write('M');
        f.write('T');
        f.write('h');
        f.write('d');
        int32(f, 6);
        f.write(0);
        f.write(0);
        f.write(0);
        f.write(1);
        f.write(PPQ >> 8);
        f.write(PPQ & 0xFF);
        f.write('M');
        f.write('T');
        f.write('r');
        f.write('k');
        int32(f, body.length);
        f.write(body, 0, body.length);
        return f.toByteArray();
    }

    static void meta(ByteArrayOutputStream o, long delta, int type, byte[] data) {
        varLen(o, delta);
        o.write(0xFF);
        o.write(type);
        varLen(o, data.length);
        o.write(data, 0, data.length);
    }

    static void varLen(ByteArrayOutputStream o, long v) {
        long buf = v & 0x7F;
        while ((v >>= 7) > 0) {
            buf <<= 8;
            buf |= ((v & 0x7F) | 0x80);
        }
        while (true) {
            o.write((int) (buf & 0xFF));
            if ((buf & 0x80) != 0) buf >>= 8;
            else break;
        }
    }

    static void int32(ByteArrayOutputStream o, int v) {
        o.write(v >>> 24);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write(v & 0xFF);
    }
}
